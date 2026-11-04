# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Installs the pure-Python packages a plugin lists in __requirements__.

pip itself does not run inside the app, so this downloads the universal
wheel (py3-none-any) of each package from PyPI and unpacks it. Packages
with compiled code cannot be installed, which is exteraGram's rule too.
"""

import json
import os
import re
import urllib.request
import zipfile
from io import BytesIO

_NAME = re.compile(r"^\s*([A-Za-z0-9][A-Za-z0-9._-]*)\s*(.*)$")


def _normalize(name):
    return re.sub(r"[-_.]+", "-", name).lower()


def _installed(target):
    names = set()
    if os.path.isdir(target):
        for entry in os.listdir(target):
            if entry.endswith(".dist-info"):
                names.add(_normalize(entry[:-len(".dist-info")].rsplit("-", 1)[0]))
    return names


def _preinstalled(name):
    try:
        from importlib import metadata
        metadata.distribution(name)
        return True
    except Exception:
        return False


def _pick(releases, spec):
    try:
        from packaging.specifiers import SpecifierSet
        from packaging.version import Version
        specifier = SpecifierSet(spec) if spec else SpecifierSet()
        versions = sorted((Version(v) for v in releases if releases[v]), reverse=True)
        for version in versions:
            if not version.is_prerelease and version in specifier:
                return str(version)
    except Exception:
        pass
    return None


def _install_one(requirement, target, seen):
    match = _NAME.match(requirement.split(";")[0])
    if not match:
        return
    name, spec = match.group(1), match.group(2).strip().strip("()")
    if "[" in name:
        name = name.split("[")[0]
    key = _normalize(name)
    if key in seen or key in _installed(target) or _preinstalled(name):
        return
    seen.add(key)
    with urllib.request.urlopen(f"https://pypi.org/pypi/{name}/json", timeout=30) as response:
        info = json.load(response)
    version = _pick(info.get("releases", {}), spec) or info["info"]["version"]
    files = info["releases"].get(version) or info.get("urls") or []
    wheel = next((f for f in files if f["filename"].endswith("-none-any.whl") and "py3" in f["filename"]), None)
    if wheel is None:
        raise RuntimeError(f"{name} {version} has no pure-Python wheel")
    with urllib.request.urlopen(wheel["url"], timeout=60) as response:
        data = response.read()
    with zipfile.ZipFile(BytesIO(data)) as archive:
        archive.extractall(target)
        metadata_name = next((n for n in archive.namelist() if n.endswith(".dist-info/METADATA")), None)
        requires = []
        if metadata_name:
            for line in archive.read(metadata_name).decode("utf-8", "replace").splitlines():
                if line.startswith("Requires-Dist:"):
                    value = line[len("Requires-Dist:"):].strip()
                    if ";" in value and "extra ==" in value.split(";", 1)[1]:
                        continue
                    requires.append(value)
    for dependency in requires:
        _install_one(dependency, target, seen)


def install(requirements, target):
    os.makedirs(target, exist_ok=True)
    seen = set()
    for requirement in requirements:
        _install_one(str(requirement), target, seen)
    import importlib
    importlib.invalidate_caches()
