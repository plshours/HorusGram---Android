# Usage: python tools/horusgram/graphite_theme.py TMessagesProj/src/main/assets/horusgram.attheme TMessagesProj/src/main/assets/horusgram_graphite.attheme
# Builds horusgram_graphite.attheme from horusgram.attheme: the blue-violet
# colors of HorusGram become graphite, the light accent a warm near-white,
# other hues stay. Keys drawn on the light accent turn dark.
import colorsys, sys
src, dst = sys.argv[1], sys.argv[2]
DARK = 0x1A1A1A
POINTS = [(0, 0), (.18, .07), (.23, .094), (.27, .137), (.30, .18), (.70, .557), (1, 1)]
def gray_level(l):
    for (a, x), (b, y) in zip(POINTS, POINTS[1:]):
        if l <= b:
            return x + (y - x) * (l - a) / (b - a)
    return 1
def conv(rgb):
    r, g, b = ((rgb >> 16) & 255) / 255, ((rgb >> 8) & 255) / 255, (rgb & 255) / 255
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    family = s < 0.14 or 200 <= h * 360 <= 260
    if not family or rgb in (0xFFFFFF, 0x000000):
        return rgb
    if s >= 0.3 and l > 0.6:
        v = min(0.97, 0.80 + (l - 0.5) * 0.6)
        c = (v, v * 0.992, v * 0.97)
    else:
        v = gray_level(l)
        c = (v, v, v)
    return (round(c[0] * 255) << 16) | (round(c[1] * 255) << 8) | round(c[2] * 255)
ON_ACCENT = ['chats_actionIcon', 'chats_unreadCounterText', 'chats_verifiedCheck', 'profile_verifiedCheck',
 'featuredStickers_buttonText', 'profile_actionIcon', 'chat_goDownButtonCounter', 'checkboxCheck',
 'checkboxSquareCheck', 'dialogCheckboxSquareCheck', 'chat_attachCheckBoxCheck', 'dialogRoundCheckBoxCheck',
 'chat_messagePanelVoicePressed', 'chat_recordedVoicePlayPause', 'location_sendLocationIcon',
 'dialogFloatingIcon', 'chat_inMediaIcon', 'chat_inMediaIconSelected', 'chat_outMediaIcon',
 'chat_outMediaIconSelected', 'windowBackgroundCheckText', 'switchTrackBlueThumbChecked',
 'featuredStickers_buttonProgress']
GRAY = {'avatar_backgroundSaved': 0x5A5A5A, 'avatar_background2Saved': 0x4A4A4A,
 'avatar_backgroundInProfileBlue': 0x3A3A3A}
lines, seen = [], set()
for line in open(src, encoding='utf-8').read().replace('\r\n', '\n').split('\n'):
    if '=' not in line:
        lines.append(line); continue
    key, value = line.split('=', 1)
    try:
        c = int(value) & 0xFFFFFFFF
    except ValueError:
        lines.append(line); continue
    seen.add(key)
    if key == 'wallpaperFileOffset':
        lines.append(line); continue
    a, rgb = c & 0xFF000000, c & 0xFFFFFF
    if key in ON_ACCENT:
        rgb = DARK
    elif key in GRAY:
        rgb = GRAY[key]
    else:
        rgb = conv(rgb)
    v = a | rgb
    lines.append(f'{key}={v - (1 << 32) if v >= 1 << 31 else v}')
# Keys the HorusGram file leaves to the defaults, written out so that the
# icons on the light accent are dark here too.
extra = [k for k in ON_ACCENT if k not in seen]
wallpaper_at = next((i for i, l in enumerate(lines) if l.startswith('WPS')), len(lines))
for k in extra:
    lines.insert(wallpaper_at, f'{k}={(0xFF000000 | DARK) - (1 << 32)}')
    wallpaper_at += 1
open(dst, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines))
print('added', extra)
