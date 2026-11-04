# This file is part of HorusGram for Android, an unofficial Telegram client.
# Copyright (C) 2026 HorusGram contributors. Licensed under the GNU GPL v2 or
# later, see the LICENSE file in the root of this repository.
"""Reflection: find classes, read and write private fields."""

from java.lang import Class


def _loader():
    from org.telegram.messenger import ApplicationLoader
    return ApplicationLoader.applicationContext.getClassLoader()


def find_class(class_name):
    try:
        return Class.forName(class_name, False, _loader())
    except Exception:
        return None


def _as_class(clazz):
    if clazz is None:
        return None
    if isinstance(clazz, str):
        return find_class(clazz)
    try:
        if clazz.getClass().getName() == "java.lang.Class":
            return clazz
    except Exception:
        pass
    try:
        from java import jclass
        return jclass(clazz.__name__ if hasattr(clazz, "__name__") else str(clazz)).getClass()
    except Exception:
        return None


def _field(clazz, name):
    while clazz is not None:
        try:
            field = clazz.getDeclaredField(name)
            field.setAccessible(True)
            return field
        except Exception:
            clazz = clazz.getSuperclass()
    return None


def get_private_field(obj, field_name):
    try:
        field = _field(obj.getClass(), field_name)
        return field.get(obj) if field is not None else None
    except Exception:
        return None


def set_private_field(obj, field_name, new_value):
    try:
        field = _field(obj.getClass(), field_name)
        if field is None:
            return False
        field.set(obj, new_value)
        return True
    except Exception:
        return False


def get_static_private_field(clazz, field_name):
    try:
        field = _field(_as_class(clazz), field_name)
        return field.get(None) if field is not None else None
    except Exception:
        return None


def set_static_private_field(clazz, field_name, new_value):
    try:
        field = _field(_as_class(clazz), field_name)
        if field is None:
            return False
        field.set(None, new_value)
        return True
    except Exception:
        return False


def find_method(clazz, name, *param_types):
    try:
        method = _as_class(clazz).getDeclaredMethod(name, *param_types)
        method.setAccessible(True)
        return method
    except Exception:
        return None


def call_private_method(obj, name, *args):
    clazz = obj.getClass()
    while clazz is not None:
        for method in clazz.getDeclaredMethods():
            if method.getName() == name and len(method.getParameterTypes()) == len(args):
                method.setAccessible(True)
                return method.invoke(obj, *args)
        clazz = clazz.getSuperclass()
    raise AttributeError(name)
