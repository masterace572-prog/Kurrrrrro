#!/usr/bin/env python3
"""
Design preview for the loader screens.

Reads the real layout XML, styles, colours, strings and vector icons and draws
them with a small flexbox that follows the Android rules the layouts actually
use (match_parent / wrap_content / weights / gravity / margins / padding).

It is a wireframe, not an emulator: no font shaping and no runtime state, but
sizes, rhythm, alignment and hierarchy are the real ones, which is what design
review needs. Real-device captures come from .github/workflows/ui-screenshots.yml.

    python3 tools/render_layouts.py      # → build/ui-preview/*.png
"""

import io
import os
import re
import xml.etree.ElementTree as ET

import resvg_py
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'loader', 'app', 'src', 'main', 'res')
OUT = os.path.join(HERE, '..', 'build', 'ui-preview')
A = '{http://schemas.android.com/apk/res/android}'
DENSITY = 2.0
FONT_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
TEXT_BASELINE = 1.22        # DejaVu ascent factor, keeps line boxes honest

# --------------------------------------------------------------------- values


def load_values():
    colors, strings, styles = {}, {}, {}
    vdir = os.path.join(RES, 'values')
    for name in os.listdir(vdir):
        for node in ET.parse(os.path.join(vdir, name)).getroot():
            if node.tag == 'color':
                colors[node.get('name')] = (node.text or '').strip()
            elif node.tag == 'string':
                strings[node.get('name')] = (node.text or '').strip()
            elif node.tag == 'style':
                styles[node.get('name')] = (
                    node.get('parent'),
                    {i.get('name'): (i.text or '').strip() for i in node},
                )
    # res/color/*.xml are state lists; a preview has no state, so the last item
    # (the default one) is what a settled screen shows.
    cdir = os.path.join(RES, 'color')
    if os.path.isdir(cdir):
        for name in os.listdir(cdir):
            root = ET.parse(os.path.join(cdir, name)).getroot()
            items = list(root)
            if items:
                # the resource name is the file name, not an attribute
                colors[os.path.splitext(name)[0]] = (
                    items[-1].get('{http://schemas.android.com/apk/res/android}color') or '')

    resolved = {}
    for name in styles:
        merged, cur = {}, name
        while cur in styles:
            parent, items = styles[cur]
            for k, v in items.items():
                merged.setdefault(k, v)
            cur = parent.split('/')[-1] if (parent or '').startswith('@style/') else None
        resolved[name] = merged
    return colors, strings, resolved


COLORS, STRINGS, STYLES = load_values()


def color(value, default=(242, 244, 246, 255)):
    if not value:
        return default
    v = value.strip()
    if v.startswith('@color/'):
        return color(COLORS.get(v[7:], '#FFFFFF'), default)
    if v.startswith('@android:color/'):
        return (0, 0, 0, 0) if v.endswith('transparent') else default
    if v.startswith('#') and len(v) in (7, 9):
        h = v[1:]
        if len(h) == 6:
            return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
        return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    return default


def size_of(value, default=0.0):
    if value is None:
        return default
    v = str(value).strip()
    m = re.match(r'^(-?[\d.]+)(dp|dip|sp|px)?$', v)
    return float(m.group(1)) if m else default


def dim(value):
    """'match' | 'wrap' | fixed px"""
    if value in (None, 'wrap_content'):
        return 'wrap'
    if value in ('match_parent', 'fill_parent'):
        return 'match'
    return int(round(size_of(value) * DENSITY))


def px(d):
    return int(round(d * DENSITY))


def text_of(value):
    if not value:
        return ''
    if value.startswith('@string/'):
        return STRINGS.get(value[8:], '')
    return value


def attrs(node):
    out = {}
    style = node.get('style')
    if style and style.startswith('@style/'):
        out.update(STYLES.get(style[7:], {}))
    for k, v in node.attrib.items():
        out[k[len(A):] if k.startswith(A) else k] = v
    for k in list(out):
        if k.startswith('android:'):
            out[k[8:]] = out.pop(k)
    for key in ('paddingStart', 'paddingEnd', 'paddingTop', 'paddingBottom',
                'layout_marginStart', 'layout_marginEnd', 'layout_marginTop', 'layout_marginBottom'):
        if key + 'Left' in out and key.endswith('Start'):
            pass
    return out


def is_bold(a):
    style = str(a.get('textStyle', ''))
    return 'bold' in style or style in ('1', '3')


def font(sp, bold):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, max(6, px(sp)))


_scratch = ImageDraw.Draw(Image.new('RGB', (4, 4)))


def tracking(em, sp):
    """Android letterSpacing is in em; return the extra px per gap."""
    try:
        return px(sp) * float(em)
    except Exception:
        return 0.0


def text_w(text, sp, bold, em=0.0):
    base = _scratch.textlength(text or ' ', font=font(sp, bold))
    if em and text and len(text) > 1:
        base += tracking(em, sp) * (len(text) - 1)
    return int(base)


def draw_tracked(d, xy, text, fnt, fill, em, anchor):
    if not em or not text:
        d.text(xy, text, font=fnt, fill=fill, anchor=anchor)
        return
    gap = tracking(em, fnt.size / (px(1) or 1))
    total = sum(d.textlength(ch, font=fnt) for ch in text) + gap * (len(text) - 1)
    tx, ty = xy
    if anchor in ('mm', 'lm'):
        start = tx - total / 2 if anchor == 'mm' else tx
    elif anchor == 'rm':
        start = tx - total
    else:
        start = tx
    for ch in text:
        d.text((start, ty), ch, font=fnt, fill=fill, anchor='lm')
        start += d.textlength(ch, font=fnt) + gap


def text_h(sp):
    return int(px(sp) * TEXT_BASELINE)


DEBUG_WRAP = False


def wrap(text, sp, bold, em, width):
    """Greedy word wrap, the way Android breaks a TextView."""
    if width <= 0:
        return [text]
    out, line = [], ''
    for word in text.split(' '):
        candidate = word if not line else line + ' ' + word
        if text_w(candidate, sp, bold, em) <= width or not line:
            line = candidate
        else:
            out.append(line)
            line = word
    out.append(line)
    return out or [text]


# ------------------------------------------------------------------- drawing

SHAPES = {
    'bg_card': ('@color/surface', '@color/stroke', 18),
    'bg_input': ('@color/surface_2', '@color/stroke', 14),
    'bg_chip': ('@color/surface_2', '@color/stroke', 500),
    'bg_dialog': ('@color/surface', None, 20),
    'bg_btn_primary': ('@color/action', None, 16),
    'bg_btn_stop': ('#1E1A1C', '@color/danger', 16),
    'bg_btn_quiet': (None, '@color/stroke', 14),
    'bg_dot': ('@color/success', None, 500),
}


def draw_shape(image, box, name):
    if not name:
        return
    if name.startswith('@color/'):
        ImageDraw.Draw(image).rectangle(box, fill=color(name))
        return
    if not name.startswith('@drawable/'):
        return
    shape = SHAPES.get(name[10:])
    if not shape:
        return
    fill, stroke, radius = shape
    r = min(px(radius), box[2] // 2, box[3] // 2)
    layer = Image.new('RGBA', image.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(
        box, radius=r,
        fill=color(fill) if fill else None,
        outline=color(stroke) if stroke else None,
        width=px(1) if stroke else 0,
    )
    image.alpha_composite(layer)


_ICONS = {}


def icon(name, box_px, tint):
    key = (name, box_px, tint)
    if key in _ICONS:
        return _ICONS[key]
    path = os.path.join(RES, 'drawable', 'ic_%s.xml' % name)
    if not os.path.exists(path):
        return None
    paths = []
    for d, a in re.findall(r'android:pathData="([^"]+)"(.*?)/>', open(path).read(), re.S):
        stroke = re.search(r'android:strokeColor="#([0-9A-Fa-f]{6})"', a)
        width = re.search(r'android:strokeWidth="([\d.]+)"', a)
        fill = re.search(r'android:fillColor="#([0-9A-Fa-f]{6})"', a)
        p = 'd="%s" ' % d
        if stroke:
            p += ('fill="none" stroke="#%s" stroke-width="%s" stroke-linecap="round" '
                  'stroke-linejoin="round"' % (stroke.group(1), width.group(1) if width else '1.7'))
        else:
            p += 'fill="#%s"' % (fill.group(1) if fill else 'FFFFFF')
        paths.append('<path %s/>' % p)
    svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 24 24">'
           '%s</svg>') % (box_px, box_px, ''.join(paths))
    im = Image.open(io.BytesIO(bytes(resvg_py.svg_to_bytes(svg_string=svg, width=box_px, height=box_px)))).convert('RGBA')
    tinted = Image.new('RGBA', im.size, tint)
    tinted.putalpha(im.split()[-1])
    _ICONS[key] = tinted
    return tinted


# -------------------------------------------------------------------- layout

def is_container(node):
    return node.tag.split('.')[-1] in ('LinearLayout', 'ScrollView', 'FrameLayout', 'include')


def inflate(node):
    if node.tag.split('.')[-1] != 'include':
        return node
    layout = attrs(node).get('layout', '').split('/')[-1]
    root = ET.parse(os.path.join(RES, 'layout', layout + '.xml')).getroot()
    for key, value in node.attrib.items():
        if ':' in key and ('layout_width' in key or 'layout_height' in key or 'layout_weight' in key):
            root.set(key, value)
    return root


def kids_of(node):
    out = []
    for k in node:
        if not isinstance(k.tag, str) or k.tag.split('.')[-1] == 'requestFocus':
            continue
        if attrs(k).get('visibility') in ('gone', 'invisible'):
            continue
        out.append(k)
    return out


def margins(node):
    a = attrs(node)
    return (
        px(size_of(a.get('layout_marginStart', a.get('layout_marginLeft')), 0)),
        px(size_of(a.get('layout_marginTop'), 0)),
        px(size_of(a.get('layout_marginEnd', a.get('layout_marginRight')), 0)),
        px(size_of(a.get('layout_marginBottom'), 0)),
    )


def padding(node):
    a = attrs(node)
    return (
        px(size_of(a.get('paddingStart', a.get('padding')), 0)),
        px(size_of(a.get('paddingTop', a.get('padding')), 0)),
        px(size_of(a.get('paddingEnd', a.get('padding')), 0)),
        px(size_of(a.get('paddingBottom', a.get('padding')), 0)),
    )


def measure(node, avail):
    node = inflate(node)
    tag = node.tag.split('.')[-1]
    a = attrs(node)
    w_spec = dim(node.get(A + 'layout_width'))
    h_spec = dim(node.get(A + 'layout_height'))
    pl, pt, pr, pb = padding(node)

    if is_container(node):
        horizontal = a.get('orientation') == 'horizontal'
        inner = max(0, avail - pl - pr)
        weights = 0.0
        fixed = 0
        for k in kids_of(node):
            real = inflate(k)
            ka = attrs(real)
            weight = float(ka.get('layout_weight', 0) or 0)
            width_attr = real.get(A + 'layout_width')
            if horizontal and weight and (width_attr is None or dim(width_attr) == 'wrap'
                                          or size_of(width_attr, 0) == 0):
                weights += weight
            else:
                ml, _, mr, _ = margins(k)
                fixed += measure(k, inner)[0] + ml + mr
        rest = max(0, inner - fixed)

        if horizontal:
            width = 0
            height = 0
            for k in kids_of(node):
                real = inflate(k)
                ka = attrs(real)
                weight = float(ka.get('layout_weight', 0) or 0)
                own = dim(real.get(A + 'layout_width'))
                if weight and weights:
                    cw = int(rest * weight / weights)
                elif own == 'match':
                    cw = inner
                elif own == 'wrap':
                    cw = min(inner, measure(k, inner)[0])
                else:
                    cw = min(inner, own)
                _, ch = measure(k, cw)
                ml, mt, mr, mb = margins(k)
                width += cw + ml + mr
                height = max(height, ch + mt + mb)
            content = (width, height)
        else:
            width = 0
            height = 0
            for k in kids_of(node):
                real = inflate(k)
                ka = attrs(real)
                ml, mt, mr, mb = margins(k)
                own = dim(real.get(A + 'layout_width'))
                weight = float(ka.get('layout_weight', 0) or 0)
                if own == 'match' or (weight and own == 0):
                    cw = inner              # a weighted child takes the whole line
                elif own == 'wrap':
                    cw = min(inner, measure(k, inner)[0])
                else:
                    cw = min(inner, own)
                _, ch = measure(k, max(cw, 1))
                height += ch + mt + mb
                width = max(width, cw + ml + mr)
            content = (width, height)

        w = content[0] + pl + pr
        if w_spec in ('match', 0) or (isinstance(w_spec, int) and w_spec == 0):
            w = max(w, avail)
        h = content[1] + pt + pb
        out_w = avail if w_spec == 'match' else (w if w_spec == 'wrap' else w_spec)
        out_h = h if h_spec in ('wrap',) else (content[1] if h_spec == 'match' else h_spec)
        return out_w, max(out_h, 0)

    if tag in ('TextView', 'Button', 'AppCompatButton', 'EditText'):
        text = text_of(a.get('text')) or text_of(a.get('hint'))
        sp = size_of(a.get('textSize'), 14.0)
        lead = px(size_of(a.get('drawablePadding'), 0)) + px(16) if a.get('drawableStart') else 0
        em = size_of(a.get('letterSpacing'), 0.0)
        full = text_w(text, sp, is_bold(a), em)
        avail_text = max(1, avail - pl - pr)
        lines = wrap(text, sp, is_bold(a), em, avail_text) if text else ['']
        w = max(text_w(line, sp, is_bold(a), em) for line in lines) + pl + pr + lead
        h = text_h(sp) * len(lines) + max(pt + pb, px(6))
        if a.get('minHeight'):
            h = max(h, px(size_of(a['minHeight'])))
        if a.get('layout_height') and dim(node.get(A + 'layout_height')) not in ('wrap', 'match'):
            h = dim(node.get(A + 'layout_height'))
        resolved = avail if (w_spec == 'match' or w_spec == 0) else min(w, avail)
        return resolved, h

    if tag in ('ImageView', 'ImageButton'):
        side = dim(node.get(A + 'layout_width'))
        height = dim(node.get(A + 'layout_height'))
        if side == 'match':
            w = avail
        elif side == 'wrap':
            w = height if isinstance(height, int) else px(20)
        else:
            w = side
        if height == 'wrap':
            h = w
        elif height == 'match':
            h = w            # filled by the parent (FrameLayout passes its own height)
        else:
            h = height
        return int(w), int(h)

    if tag == 'ProgressBar':
        w = avail if w_spec == 'match' else (dim(node.get(A + 'layout_width')) if w_spec != 'wrap' else px(16))
        h = dim(node.get(A + 'layout_height')) if h_spec not in ('wrap', 'match') else px(4)
        return w, h

    if tag == 'View':
        w = avail if w_spec == 'match' else (px(1) if w_spec == 'wrap' else w_spec)
        h = px(1) if h_spec in ('wrap', 'match') else h_spec
        return w, h

    return avail, 0


def draw(node, image, x, y, avail, avail_h=None):
    node = inflate(node)
    tag = node.tag.split('.')[-1]
    a = attrs(node)
    w, h = measure(node, avail)
    if dim(node.get(A + 'layout_width')) in ('match', 0):
        w = avail                     # 0dp without a weight still fills its slot
    if avail_h is not None and dim(node.get(A + 'layout_height')) == 'match':
        h = avail_h - y        # the root fills the window, not its content
    pl, pt, pr, pb = padding(node)

    if tag == 'ScrollView':
        for k in kids_of(node):
            draw(k, image, x, y, w, avail_h=h)
        return h

    draw_shape(image, (x, y, x + w, y + h), a.get('background'))

    if tag == 'FrameLayout':
        for k in kids_of(node):
            kw, kh = measure(k, w)
            kw = w if dim(k.get(A + 'layout_width')) == 'match' else kw
            kh = h if dim(k.get(A + 'layout_height')) == 'match' else kh
            lg = str(attrs(k).get('layout_gravity', ''))
            if 'end' in lg or 'right' in lg:
                kx = x + w - kw
            elif 'start' in lg or 'left' in lg:
                kx = x
            else:
                kx = x + (w - kw) // 2
            if 'bottom' in lg:
                ky = y + h - kh
            elif 'top' in lg:
                ky = y
            else:
                ky = y + (h - kh) // 2
            draw(k, image, kx, ky, kw, avail_h=ky + kh)
        return h

    if tag == 'LinearLayout':
        horizontal = a.get('orientation') == 'horizontal'
        gravity = str(a.get('gravity', '')).replace('|', ' ')
        tokens = gravity.split()
        if 'center' in tokens:
            gravity += ' center_horizontal center_vertical'
        if 'center_horizontal' in tokens and not horizontal:
            pass
        if 'fill' in tokens:
            gravity += ' fill_horizontal'
        kids = kids_of(node)
        inner = w - pl - pr
        weights = 0.0
        fixed = 0
        for k in kids:
            real = inflate(k)
            ka = attrs(real)
            weight = float(ka.get('layout_weight', 0) or 0)
            if horizontal and weight and size_of(real.get(A + 'layout_width'), 0) == 0:
                weights += weight
            else:
                ml, _, mr, _ = margins(k)
                fixed += measure(k, inner)[0] + ml + mr
        rest = max(0, inner - fixed)

        if horizontal:
            cx = x + pl
            total_h = 0
            laid = []
            for k in kids:
                real = inflate(k)
                ka = attrs(real)
                weight = float(ka.get('layout_weight', 0) or 0)
                ml, mt, mr, mb = margins(k)
                if weight and weights:
                    cw = int(rest * weight / weights)
                elif dim(real.get(A + 'layout_width')) == 'match':
                    cw = inner
                elif dim(real.get(A + 'layout_width')) == 'wrap':
                    cw = min(inner, measure(k, inner)[0])
                else:
                    cw = min(inner, dim(real.get(A + 'layout_width')))
                laid.append((k, cx + ml, cw, mt, mb))
                cx += cw + ml + mr
                total_h = max(total_h, measure(k, cw)[1] + mt + mb)
            for k, kx, cw, mt, mb in laid:
                kh = measure(k, cw)[1]
                ky = y + pt + mt
                if 'center_vertical' in gravity.split() or 'center' in gravity.split() \
                        or 'center_vertical' in str(ka.get('layout_gravity', '')).split():
                    ky = y + pt + (h - pt - pb - kh) // 2
                draw(k, image, kx, ky, cw)
            return h

        # vertical
        cx = x + pl
        cy = y + pt
        stack = 0
        heights = []
        weight_total = 0.0
        fixed_h = 0
        for k in kids:
            real = inflate(k)
            ka = attrs(real)
            weight = float(ka.get('layout_weight', 0) or 0)
            own_h = dim(real.get(A + 'layout_height'))
            if weight and own_h == 0:
                weight_total += weight
                continue
            fixed_h += measure(k, inner)[1] + sum(margins(k)[1::2])
        spare = int(max(0, (h - pt - pb) - fixed_h))

        for k in kids:
            real = inflate(k)
            ka = attrs(real)
            own = dim(real.get(A + 'layout_width'))
            weight = float(ka.get('layout_weight', 0) or 0)
            own_h = dim(real.get(A + 'layout_height'))
            if own == 'match' or (weight and own == 0):
                cw = inner
            elif own == 'wrap':
                cw = min(inner, measure(k, inner)[0])
            else:
                cw = min(inner, own)
            if weight and own_h == 0 and weight_total:
                kh = int(spare * weight / weight_total)
                ml0, mt0, mr0, mb0 = margins(k)
            else:
                kh = measure(k, max(1, cw))[1]
            ml, mt, mr, mb = margins(k)
            heights.append((k, cw, kh, mt, mb, ka))
            stack += kh + mt + mb
        if 'center_vertical' in gravity.split() or 'center' in gravity.split():
            cy = y + pt + max(0, (h - pt - pb - stack) // 2)
        for k, cw, kh, mt, mb, ka in heights:
            own = dim(inflate(k).get(A + 'layout_width'))
            g = gravity + ' ' + str(ka.get('layout_gravity', '')).replace('|', ' ')
            if 'center' in g.split() or 'center_horizontal' in g.split():
                pass
            else:
                g = gravity
            if ('center_horizontal' in g.split() or 'center' in g.split()) and own != 'match':
                kx = x + (w - cw) // 2
            elif 'end' in g.split() or 'right' in g.split():
                kx = x + w - pr - cw
            else:
                kx = cx
            draw(k, image, kx, cy + mt, max(1, cw))
            cy += kh + mt + mb
        return h

    # ---- leaves -----------------------------------------------------------
    if tag == 'ProgressBar':
        style = str(a.get('style', ''))
        if 'progressBarStyle' in style and 'Horizontal' not in style and w > 0 and h > 0:
            # indeterminate circular spinner: a thin ring, no fill
            ring = Image.new('RGBA', (w, h), (0, 0, 0, 0))
            ImageDraw.Draw(ring).arc((2, 2, w - 2, h - 2), 0, 360,
                                     fill=color('@color/stroke'), width=max(2, w // 40))
            ImageDraw.Draw(ring).arc((2, 2, w - 2, h - 2), 210, 330,
                                     fill=color('@color/text_secondary'), width=max(2, w // 40))
            image.alpha_composite(ring, (x, y))
            return h
        bar_bg = color('#1B1F24')
        ImageDraw.Draw(image).rounded_rectangle((x, y, x + w, y + h), radius=max(1, h // 2), fill=bar_bg)
        progress = float(a.get('progress', 0) or 0)
        if progress > 0:
            ImageDraw.Draw(image).rounded_rectangle(
                (x, y, x + int(w * progress / 100), y + h), radius=max(1, h // 2), fill=color('@color/action'))
        return h

    if tag == 'View':
        if a.get('background'):
            ImageDraw.Draw(image).rectangle((x, y, x + w, y + h), fill=color(a.get('background')))
        return h

    if tag in ('ImageView', 'ImageButton'):
        side = max(8, min(w, h if h else w))
        name = (a.get('src') or '').split('/')[-1]
        tint = color(a.get('tint')) if a.get('tint') else (242, 244, 246, 255)
        if name.startswith('ic_'):
            im = icon(name[3:], side, tint)
        elif name == 'logo_mark':
            im = Image.open(os.path.join(RES, 'drawable-nodpi', 'logo_mark.png')).convert('RGBA')
            im = im.resize((side, side), Image.LANCZOS)
        else:
            im = None
        if im:
            image.alpha_composite(im, (x + (w - im.width) // 2, y + (h - im.height) // 2))
        return h

    if tag in ('TextView', 'Button', 'AppCompatButton', 'EditText'):
        text = text_of(a.get('text')) or text_of(a.get('hint'))
        sp = size_of(a.get('textSize'), 14.0)
        fill = color(a.get('textColor')) if a.get('textColor') else (242, 244, 246, 255)
        if a.get('hint') and not text_of(a.get('text')):
            fill = color(a.get('textColorHint'), (107, 116, 128, 255))
        gravity = str(a.get('gravity', ''))
        if not gravity and tag in ('Button', 'AppCompatButton'):
            gravity = 'center'
        tx = x + pl
        anchor = 'lm'
        if a.get('drawableStart'):
            im = icon(a['drawableStart'].split('/')[-1][3:], max(px(14), px(sp)),
                      color(a.get('drawableTint'), (152, 162, 174, 255)))
            if im:
                image.alpha_composite(im, (tx, y + (h - im.height) // 2))
                tx += im.width + px(size_of(a.get('drawablePadding'), 7))
        tokens = gravity.replace('|', ' ').split()
        if 'center' in tokens or 'center_horizontal' in tokens:
            tx = x + w // 2
            anchor = 'mm'
        elif 'end' in tokens or 'right' in tokens:
            tx = x + w - pr
            anchor = 'rm'
        if text:
            em = size_of(a.get('letterSpacing'), 0.0)
            lead_px = px(size_of(a.get('drawablePadding'), 0)) + px(16) if a.get('drawableStart') else 0
            if DEBUG_WRAP and text:
                print('DBG', text[:18], 'w', w, 'avail', avail, 'pl', pl, 'pr', pr)
            lines = wrap(text, sp, is_bold(a), em, max(1, w - pl - pr - lead_px))
            line_h = text_h(sp)
            top = y + pt + (max(0, (h - pt - pb) - line_h * len(lines)) // 2)
            for i, line in enumerate(lines):
                draw_tracked(ImageDraw.Draw(image), (tx, top + line_h * i + line_h // 2), line,
                             font(sp, is_bold(a)), fill, em, anchor)
        return h

    return h


def status_bar(image):
    """Draws the OS chrome in the theme's own colours (light or dark)."""
    fg = color('@color/text_primary')
    muted = color('@color/text_secondary')
    d = ImageDraw.Draw(image)
    d.text((px(20), px(13)), '9:41', font=font(13, True), fill=fg, anchor='lm')
    d.text((image.width - px(20), px(13)), '▮▮ ▮', font=font(13, True), fill=muted, anchor='rm')
    return


MASKED_KEY = 'KD9X \u2022\u2022\u2022\u2022 8F2A'

SCREENS = [
    {'name': '01-splash', 'layout': 'activity_splash.xml', 'height': 869},
    {'name': '02-login', 'layout': 'activity_login.xml', 'height': 869},
    {
        'name': '02b-verify', 'layout': 'activity_login.xml', 'height': 869,
        'attrs': {'loginError': {'visibility': 'visible'}},
        'text': {'loginError': 'That key has expired — ask for a new one'},
    },
    {
        'name': '02c-dialog', 'layout': 'dialog_verify.xml', 'height': 869,
        'dialog': True, 'backdrop': 'activity_login.xml',
        'text': {'verifyStep': 'Binding this device…', 'verifyKey': MASKED_KEY},
    },
    {
        'name': '03-main', 'layout': 'activity_main.xml', 'height': 1240,
        'text': {'keyValue': MASKED_KEY},
    },
    {
        'name': '05-license-failed', 'layout': 'activity_main.xml', 'height': 1240,
        'text': {
            'keyValue': MASKED_KEY,
            'subscriptionStatus': 'EXPIRED KEY',
            'startBgmi': 'Sign in again',
        },
    },
    {
        'name': '04b-no-data', 'layout': 'activity_main.xml', 'height': 1240,
        'attrs': {'obbCard': {'visibility': 'visible'},
                  'obbRetryButton': {'visibility': 'visible'}},
        'text': {
            'keyValue': MASKED_KEY,
            'obbTitle': 'Game data',
            'obbStatusText': 'Open the game once so it downloads its data, then retry.  Android/obb/com.pubg.imobile is empty',
            'obbRetryButton': 'Retry copy',
        },
    },
    {
        'name': '04-file-access', 'layout': 'activity_main.xml', 'height': 1240,
        'attrs': {'obbCard': {'visibility': 'visible'},
                  'obbRetryButton': {'visibility': 'visible'}},
        'text': {
            'keyValue': MASKED_KEY,
            'obbTitle': 'All files access required',
            'obbStatusText': 'The game data lives in shared \u00a0storage. Allow access and the copy starts by itself \u2014 it is a one-time step.',
            'obbRetryButton': 'Allow access',
        },
    },
]


def apply_overrides(root, attrs_map, text_map):
    """Force a view's state (visibility, text) so alt states can be previewed."""
    for node in root.iter():
        if not isinstance(node.tag, str):
            continue
        nid = str(node.get(A + 'id', '')).split('/')[-1]
        if nid in attrs_map:
            for key, value in attrs_map[nid].items():
                node.set(A + key, value)
        if nid in text_map and node.tag.split('.')[-1] in (
                'TextView', 'Button', 'AppCompatButton', 'EditText'):
            node.set(A + 'text', text_map[nid])


def main():
    os.makedirs(OUT, exist_ok=True)
    scale = 1.6  # crisp output for review
    for screen in SCREENS:
        name, layout, height_dp = screen['name'], screen['layout'], screen['height']
        w = px(411)
        h = px(height_dp)
        image = Image.new('RGBA', (w, h), color('@color/bg'))
        root = ET.parse(os.path.join(RES, 'layout', layout)).getroot()
        apply_overrides(root, screen.get('attrs', {}), screen.get('text', {}))
        draw(root, image, 0, 0, w, avail_h=h)
        canvas = Image.new('RGBA', (w, h), color('@color/bg'))
        canvas.alpha_composite(image)
        if screen.get('dialog'):
            canvas = Image.new('RGBA', (w, h), color('@color/bg'))
            backdrop_name = screen.get('backdrop')
            if backdrop_name:
                back = ET.parse(os.path.join(RES, 'layout', backdrop_name)).getroot()
                draw(back, canvas, 0, 0, w, avail_h=h)
            canvas.alpha_composite(Image.new('RGBA', (w, h), (4, 6, 8, 168)))
            dw = int(w * 0.78)
            dlg = ET.parse(os.path.join(RES, 'layout', layout)).getroot()
            apply_overrides(dlg, screen.get('attrs', {}), screen.get('text', {}))
            dh = measure(dlg, dw)[1]
            dlg_img = Image.new('RGBA', (dw, dh), (0, 0, 0, 0))
            draw(dlg, dlg_img, 0, 0, dw, avail_h=dh)
            canvas.alpha_composite(dlg_img, ((w - dw) // 2, (h - dh) // 2))
            status_bar(canvas)
            out = canvas.convert('RGB')
            out = out.resize((int(w * scale / 1.4), int(h * scale / 1.4)), Image.LANCZOS)
            out.save(os.path.join(OUT, name + '.png'))
            print('wrote', os.path.join(OUT, name + '.png'), out.size)
            continue
        status_bar(canvas)
        out = canvas.convert('RGB')
        out = out.resize((int(w * scale / 1.4), int(h * scale / 1.4)), Image.LANCZOS)
        out.save(os.path.join(OUT, name + '.png'))
        print('wrote', os.path.join(OUT, name + '.png'), out.size)


if __name__ == '__main__':
    main()
