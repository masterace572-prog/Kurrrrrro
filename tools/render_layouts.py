#!/usr/bin/env python3
"""
Design preview for the loader screens.

This is a *wireframe* renderer, not an emulator: it reads the real layout XML,
the real styles, colours, strings and vector icons, and lays them out with a
simplified flexbox so a change can be looked at without waiting for a device.
It gets sizes, spacing, alignment, colour and hierarchy right; it does not try
to be pixel exact (no font shaping, no stateful widgets).

    python3 tools/render_layouts.py            # writes build/ui-preview/*.png
"""

import io
import os
import re
import xml.etree.ElementTree as ET

import resvg_py
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'loader', 'app', 'src', 'main')
RES = os.path.join(ROOT, 'res')
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build', 'ui-preview')

ANDROID = '{http://schemas.android.com/apk/res/android}'
DENSITY = 2.0            # 1dp = 2px, i.e. a 411dp wide screen
FONT_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

# --------------------------------------------------------------------- values


def load_values():
    colors, strings, styles = {}, {}, {}
    vdir = os.path.join(RES, 'values')
    for name in os.listdir(vdir):
        tree = ET.parse(os.path.join(vdir, name))
        for node in tree.getroot():
            if node.tag == 'color':
                colors[node.get('name')] = node.text.strip()
            elif node.tag == 'string':
                strings[node.get('name')] = (node.text or '').strip()
            elif node.tag == 'style':
                items = {}
                for item in node:
                    items[item.get('name')] = (item.text or '').strip()
                styles[node.get('name')] = {'parent': node.get('parent'), 'items': items}
    # resolve style inheritance
    for name in list(styles):
        merged, cur = {}, name
        while cur and cur in styles:
            merged.update({k: v for k, v in styles[cur]['items'].items() if k not in merged})
            parent = styles[cur]['parent'] or ''
            cur = parent.split('/')[-1] if parent.startswith('@style/') else None
        styles[name] = merged
    return colors, strings, styles


COLORS, STRINGS, STYLES = load_values()


def color(value, default=(255, 255, 255, 255)):
    if not value:
        return default
    value = value.strip()
    if value.startswith('@color/'):
        value = COLORS.get(value[7:], '#FFFFFF')
    if value.startswith('@android:color/'):
        tail = value.split('/')[-1]
        if tail == 'transparent':
            return (0, 0, 0, 0)
        value = '#FFFFFF'
    if value.startswith('#'):
        h = value[1:]
        if len(h) == 6:
            return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        if len(h) == 8:  # #AARRGGBB
            return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    return default


def dp(value, default=0.0):
    if value is None:
        return default
    value = str(value).strip()
    if value in ('match_parent', 'fill_parent'):
        return 'match'
    if value == 'wrap_content':
        return 'wrap'
    m = re.match(r'^(-?[\d.]+)(dp|dip|sp|px)?$', value)
    if m:
        return float(m.group(1))
    return default


def px(value):
    if isinstance(value, str):
        return int(round(float(value) * DENSITY))
    return int(round(value * DENSITY))


def text_of(value):
    if value is None:
        return ''
    if value.startswith('@string/'):
        return STRINGS.get(value[8:], '')
    return value


# -------------------------------------------------------------------- drawing

BG_SHAPES = {
    'bg_card': dict(fill='@color/surface', stroke='@color/stroke', radius=18),
    'bg_input': dict(fill='@color/surface_2', stroke='@color/stroke', radius=14),
    'bg_chip': dict(fill='@color/surface_2', stroke='@color/stroke', radius=100),
    'bg_dialog': dict(fill='@color/surface', radius=20),
    'bg_btn_primary': dict(fill='@color/action', radius=16),
    'bg_btn_stop': dict(fill='#1E1A1C', stroke='@color/danger', radius=16),
    'bg_btn_quiet': dict(fill=None, stroke='@color/stroke', radius=14),
    'bg_dot': dict(fill='@color/success', radius=100),
}


def draw_background(image, box, name):
    if not name or not name.startswith('@drawable/'):
        return
    shape = BG_SHAPES.get(name[10:])
    if not shape:
        return
    layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    fill = color(shape.get('fill')) if shape.get('fill') else None
    stroke = color(shape.get('stroke')) if shape.get('stroke') else None
    radius = px(shape.get('radius', 0))
    radius = min(radius, box[2] // 2, box[3] // 2)
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=stroke, width=px(1) if stroke else 0)
    image.alpha_composite(layer)


def font_for(size_sp, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, px(size_sp))


def draw_text(image, xy, text, size_sp, fill, bold=False, anchor='la'):
    if not text:
        return
    d = ImageDraw.Draw(image)
    d.text(xy, text, font=font_for(size_sp, bold), fill=fill, anchor=anchor)


ICON_CACHE = {}


def icon_image(name, size, tint):
    key = (name, size, tint)
    if key in ICON_CACHE:
        return ICON_CACHE[key]
    path = os.path.join(RES, 'drawable', 'ic_%s.xml' % name)
    if not os.path.exists(path):
        return None
    xml = open(path).read()
    paths = []
    for d, attrs in re.findall(r'android:pathData="([^"]+)"(.*?)/>', xml, re.S):
        stroke = re.search(r'android:strokeColor="#([0-9A-Fa-f]{6})"', attrs)
        width = re.search(r'android:strokeWidth="([\d.]+)"', attrs)
        fill = re.search(r'android:fillColor="#([0-9A-Fa-f]{6})"', attrs)
        a = 'd="%s" ' % d
        if stroke:
            a += 'fill="none" stroke="#%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round"' % (
                stroke.group(1), width.group(1) if width else '1.7')
        else:
            a += 'fill="#%s"' % (fill.group(1) if fill else 'FFFFFF')
        paths.append('<path %s/>' % a)
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 24 24">'
           '%s</svg>') % (size, size, ''.join(paths))
    png = bytes(resvg_py.svg_to_bytes(svg_string=svg, width=size, height=size))
    im = Image.open(io.BytesIO(png)).convert('RGBA')
    tint_im = Image.new('RGBA', im.size, tint)
    alpha = im.split()[-1]
    tint_im.putalpha(alpha)
    ICON_CACHE[key] = tint_im
    return tint_im


# ------------------------------------------------------------------- measure

def attr(node, name, fallback=None):
    value = node.get(ANDROID + name)
    return value if value is not None else (node.get(ANDROID + fallback) if fallback else None)


def resolved(node):
    """inline attributes win over the referenced style"""
    out = {}
    style = node.get('style')
    if style and style.startswith('@style/'):
        out.update({k: v for k, v in STYLES.get(style[7:], {}).items()})
    for k, v in node.attrib.items():
        if k.startswith(ANDROID):
            out[k[len(ANDROID):]] = v
        elif not k.startswith('{'):
            out[k] = v
    return out


def text_size(a):
    return dp(a.get('textSize'), 14.0)


def measure_text_width(text, size_sp, bold=False):
    if not text:
        return 0
    f = font_for(size_sp, bold)
    return int(ImageDraw.Draw(Image.new('RGB', (1, 1))).textlength(text, font=f))


def is_bold(a):
    return str(a.get('textStyle', '')).lower() in ('bold', '1') or 'bold' in str(a.get('textStyle', ''))


# ------------------------------------------------------------------- layout

class Ctx:
    def __init__(self, width_px):
        self.width = width_px


def measures(node, avail_w, ctx):
    """returns (width, height) in px"""
    tag = node.tag.split('.')[-1]
    a = resolved(node)
    lw, lh = dp(attr(node, 'layout_width'), 'wrap'), dp(attr(node, 'layout_height'), 'wrap')
    pad_l = px(dp(a.get('paddingStart', a.get('padding', 0))))
    pad_r = px(dp(a.get('paddingEnd', a.get('padding', 0))))
    pad_t = px(dp(a.get('paddingTop', a.get('padding', 0))))
    pad_b = px(dp(a.get('paddingBottom', a.get('padding', 0))))

    if tag == 'include':
        layout = a.get('layout', '')
        path = os.path.join(RES, 'layout', layout.split('/')[-1] + '.xml')
        return measures(ET.parse(path).getroot(), avail_w, ctx)

    if tag in ('LinearLayout', 'ScrollView', 'FrameLayout'):
        inner_w = avail_w - pad_l - pad_r if lw != 'match' else avail_w - pad_l - pad_r
        if lw == 'match':
            inner_w = avail_w - pad_l - pad_r
        horizontal = a.get('orientation') == 'horizontal'
        total = 0
        weight_total = 0
        fixed = 0
        kids = []
        for kid in node:
            if not isinstance(kid.tag, str):
                continue
            ka = resolved(kid)
            kw = dp(attr(kid, 'layout_width'), 'wrap')
            weight = float(ka.get('layout_weight', 0) or 0)
            if horizontal and weight and kw == 0:
                weight_total += weight
            else:
                kw_px, kh_px = measures(kid, inner_w, ctx)
                fixed += kw_px if horizontal else 0
                total = max(total, 0) if not horizontal else total
            kids.append(kid)
        avail_rest = max(0, inner_w - fixed)
        height = 0
        width = 0
        for kid in kids:
            ka = resolved(kid)
            kw = dp(attr(kid, 'layout_width'), 'wrap')
            weight = float(ka.get('layout_weight', 0) or 0)
            margin_t = px(dp(attr(kid, 'layout_marginTop'), 0))
            margin_b = px(dp(attr(kid, 'layout_marginBottom'), 0))
            margin_s = px(dp(attr(kid, 'layout_marginStart', attr(kid, 'layout_marginLeft')), 0))
            margin_e = px(dp(attr(kid, 'layout_marginEnd', attr(kid, 'layout_marginRight')), 0))
            if horizontal:
                if weight:
                    w = int(avail_rest * weight / max(weight_total, 1))
                elif kw == 'match':
                    w = inner_w
                else:
                    w = measures(kid, inner_w, ctx)[0]
                h = measures(kid, w, ctx)[1]
                width += w + margin_s + margin_e
                height = max(height, h + margin_t + margin_b)
            else:
                w = inner_w if kw == 'match' else min(inner_w, measures(kid, inner_w, ctx)[0])
                h = measures(kid, w, ctx)[1]
                height += h + margin_t + margin_b
                width = max(width, w)
        if tag == 'ScrollView':
            return avail_w, max(height + pad_t + pad_b, 0)
        return (inner_w + pad_l + pad_r if lw == 'match' else width + pad_l + pad_r), height + pad_t + pad_b

    if tag in ('TextView', 'Button', 'AppCompatButton', 'EditText'):
        size = text_size(a)
        text = text_of(a.get('text')) or text_of(a.get('hint'))
        if a.get('drawableStart'):
            text = '    ' + text
        w = measure_text_width(text or ' ', size, is_bold(a)) + px(dp(a.get('paddingStart', a.get('padding', 0)))) * 2 + 8
        h = lh if lh not in ('wrap', 'match') else int(size * DENSITY * 1.9) + pad_t + pad_b
        if lh == 'match':
            h = pad_t + pad_b + int(size * DENSITY * 1.9)
        if a.get('minHeight'):
            h = max(h, px(dp(a['minHeight'])))
        if lw == 'match':
            return avail_w, h
        return min(w, avail_w), h

    if tag == 'ImageView':
        w = lw if lw not in ('wrap', 'match') else (lh if lh not in ('wrap', 'match') else 20)
        w = px(w) if lw not in ('wrap', 'match') else (px(lh) if lh not in ('wrap', 'match') else px(20))
        h = px(lh) if lh not in ('wrap', 'match') else px(20)
        if lw == 'match':
            w = avail_w
        if lh == 'match':
            h = 0
        return w, h

    if tag == 'View':
        w = avail_w if lw == 'match' else px(lw if lw not in ('wrap', 'match') else 1)
        h = px(lh if lh not in ('wrap', 'match') else 1)
        return w, h

    if tag == 'ProgressBar':
        w = avail_w if lw == 'match' else px(lw if lw not in ('wrap', 'match') else 16)
        h = px(lh if lh not in ('wrap', 'match') else 4)
        return w, h

    return avail_w, 0


def render(node, image, x, y, avail_w, ctx):
    """draws node at (x, y), returns the height it used"""
    tag = node.tag.split('.')[-1]
    a = resolved(node)

    if tag == 'include':
        layout = a.get('layout', '')
        path = os.path.join(RES, 'layout', layout.split('/')[-1] + '.xml')
        return render(ET.parse(path).getroot(), image, x, y, avail_w, ctx)

    x, y, avail_w = int(x), int(y), int(avail_w)
    lw, lh = dp(attr(node, 'layout_width'), 'wrap'), dp(attr(node, 'layout_height'), 'wrap')
    w, h = measures(node, avail_w, ctx)
    w, h = int(w), int(h)
    if lw == 'match':
        w = avail_w
    if lh not in ('wrap', 'match') and tag not in ('LinearLayout', 'FrameLayout', 'ScrollView'):
        h = px(lh)

    pad_l = px(dp(a.get('paddingStart', a.get('padding', 0))))
    pad_r = px(dp(a.get('paddingEnd', a.get('padding', 0))))
    pad_t = px(dp(a.get('paddingTop', a.get('padding', 0))))
    pad_b = px(dp(a.get('paddingBottom', a.get('padding', 0))))

    if tag in ('LinearLayout', 'FrameLayout', 'ScrollView'):
        draw_background(image, (x, y, x + w, y + h), a.get('background'))
        if tag == 'FrameLayout':
            for kid in node:
                if isinstance(kid.tag, str):
                    kw, kh = measures(kid, w, ctx)
                    if dp(attr(kid, 'layout_width')) == 'match':
                        kw = w
                    if dp(attr(kid, 'layout_height')) == 'match':
                        kh = h
                    render(kid, image, x + (w - kw) // 2, y + (h - kh) // 2, w, ctx)
            return h
        horizontal = a.get('orientation') == 'horizontal'
        kids = [k for k in node if isinstance(k.tag, str)]
        inner_w = w - pad_l - pad_r
        weight_total = sum(float(resolved(k).get('layout_weight', 0) or 0)
                           for k in kids if horizontal and dp(attr(k, 'layout_width'), 'wrap') == 0)
        fixed = 0
        for k in kids:
            if horizontal and dp(attr(k, 'layout_width'), 'wrap') == 0 and float(resolved(k).get('layout_weight', 0) or 0):
                continue
            fixed += measures(k, inner_w, ctx)[0]
        rest = max(0, inner_w - fixed)
        cx, cy = x + pad_l, y + pad_t
        last_h = 0
        for k in kids:
            ka = resolved(k)
            margin_t = px(dp(attr(k, 'layout_marginTop'), 0))
            margin_b = px(dp(attr(k, 'layout_marginBottom'), 0))
            margin_s = px(dp(attr(k, 'layout_marginStart', attr(k, 'layout_marginLeft')), 0))
            margin_e = px(dp(attr(k, 'layout_marginEnd', attr(k, 'layout_marginRight')), 0))
            weight = float(ka.get('layout_weight', 0) or 0)
            if horizontal:
                if weight:
                    kw = int(rest * weight / max(weight_total, 1))
                elif dp(attr(k, 'layout_width'), 'wrap') == 'match':
                    kw = inner_w
                else:
                    kw = measures(k, inner_w, ctx)[0]
                kh = render(k, image, cx + margin_s, cy + margin_t, kw, ctx)
                cx += kw + margin_s + margin_e
                last_h = max(last_h, kh + margin_t + margin_b)
            else:
                kw = inner_w if dp(attr(k, 'layout_width'), 'wrap') == 'match' else min(inner_w, measures(k, inner_w, ctx)[0])
                kh = render(k, image, x + pad_l, cy + margin_t, kw, ctx)
                cy += kh + margin_t + margin_b
                last_h = cy - y
        if tag == 'ScrollView':
            return h
        return h if lh not in ('wrap',) else (h if not horizontal else last_h) + pad_t + pad_b

    # ---- leaves -----------------------------------------------------------
    draw_background(image, (x, y, x + w, y + h), a.get('background'))
    text = text_of(a.get('text')) or text_of(a.get('hint'))
    gravity = str(a.get('gravity', ''))
    size = text_size(a)
    fill = color(a.get('textColor')) if a.get('textColor') else color('@color/text_primary')
    if a.get('hint') and not text_of(a.get('text')):
        fill = color(a.get('textColorHint'), (107, 116, 128, 255))

    cx = x + pad_l
    if tag == 'ProgressBar':
        bar_bg = color('#1B1F24')
        image_draw = ImageDraw.Draw(image)
        image_draw.rounded_rectangle((x, y, x + w, y + h), radius=h // 2, fill=bar_bg)
        progress = float(a.get('progress', 0) or 0)
        if progress > 0:
            image_draw.rounded_rectangle((x, y, x + int(w * progress / 100), y + h), radius=h // 2,
                                         fill=color('@color/action'))
        return h

    if tag == 'ImageView':
        name = (a.get('src') or '').split('/')[-1]
        tint = color(a.get('tint'), (242, 244, 246, 255)) if a.get('tint') else (242, 244, 246, 255)
        if name.startswith('ic_'):
            im = icon_image(name[3:], max(8, min(w, h)), tint)
            if im:
                image.alpha_composite(im, (x + (w - im.width) // 2, y + (h - im.height) // 2))
        elif name in ('logo_mark',):
            side = max(8, min(w, h if h else w))
            art = Image.open(os.path.join(RES, 'drawable-nodpi', 'logo_mark.png')).convert('RGBA')
            art = art.resize((side, side), Image.LANCZOS)
            image.alpha_composite(art, (x + (w - art.width) // 2, y + (h - art.height) // 2))
        return h

    if tag == 'View':
        ImageDraw.Draw(image).rectangle((x, y, x + w, y + h), fill=color(a.get('background'), (37, 42, 49, 255)))
        return h

    if tag in ('TextView', 'Button', 'AppCompatButton', 'EditText'):
        draw_icon = None
        if a.get('drawableStart'):
            draw_icon = a['drawableStart'].split('/')[-1][3:]
        text_x = cx
        anchor = 'la'
        if draw_icon:
            im = icon_image(draw_icon, px(size), color(a.get('drawableTint'), (152, 162, 174, 255)))
            if im:
                image.alpha_composite(im, (cx, y + (h - im.height) // 2))
                text_x = cx + im.width + px(7)
        if 'center' in gravity and 'horizontal' not in gravity:
            anchor = 'ma'
            text_x = x + w // 2 + (px(7) if draw_icon else 0)
        elif 'end' in gravity or 'right' in gravity:
            anchor = 'ra'
            text_x = x + w - pad_r
        draw_text(image, (text_x, y + h // 2), text, size, fill, is_bold(a), anchor=anchor if anchor != 'la' else 'lm')
        return h

    return h


def draw_status_bar(image, title=''):
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, image.width, px(28)), fill=color('@color/bg'))
    draw_text(image, (px(20), px(14)), '9:41', 13, (242, 244, 246, 255), True, anchor='lm')
    draw_text(image, (image.width - px(20), px(14)), '▮▮▮', 11, (152, 162, 174, 255), anchor='rm')


def main():
    os.makedirs(OUT, exist_ok=True)
    screens = [
        ('splash', 'activity_splash.xml', False),
        ('login', 'activity_login.xml', False),
        ('main', 'activity_main.xml', True),
    ]
    for name, layout, tall in screens:
        width_dp, height_dp = 411, (1180 if tall else 869)
        w, h = px(width_dp), px(height_dp)
        image = Image.new('RGBA', (w, h), color('@color/bg'))
        root = ET.parse(os.path.join(RES, 'layout', layout)).getroot()
        ctx = Ctx(w)
        # ScrollView takes the whole screen; its content can be taller
        content_h = render(root, image, 0, px(28), w, ctx)
        draw_status_bar(image)
        image.convert('RGB').save(os.path.join(OUT, '%s.png' % name))
        print('wrote %s (%dx%d, content %dpx)' % (os.path.join(OUT, name + '.png'), w, h, content_h))


if __name__ == '__main__':
    main()
