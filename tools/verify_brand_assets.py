"""Audit launcher and splash vector bounds plus local Android resource references."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'
TOKEN = re.compile(r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)')


def vector_paths(path):
    root = ET.parse(path).getroot()
    return root.findall('.//path')


def path_points(data):
    tokens = TOKEN.findall(data)
    points, i, command = [], 0, None
    arity = {'M': 2, 'L': 2, 'C': 6, 'Q': 4}
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i].upper()
            i += 1
            if command == 'Z':
                continue
            if command not in arity:
                raise ValueError(f'Unsupported path command {command}: {data}')
        count = arity[command]
        end = i + count
        if end > len(tokens) or any(token.isalpha() for token in tokens[i:end]):
            raise ValueError(f'Incomplete path command {command}: {data}')
        values = [float(token) for token in tokens[i:end]]
        points.extend(zip(values[::2], values[1::2]))
        i = end
    return points


checks = [
    ('drawable/ic_launcher_foreground.xml', False),
    ('drawable/ic_launcher_monochrome.xml', False),
    ('drawable/avd_splash_icon.xml', True),
]
for relative, animated in checks:
    paths = vector_paths(RES / relative)
    xs, ys, worst_radius = [], [], 0.0
    for path in paths:
        points = path_points(path.attrib[ANDROID + 'pathData'])
        xs.extend(x for x, _ in points)
        ys.extend(y for _, y in points)
        stroke = float(path.attrib.get(ANDROID + 'strokeWidth', '0'))
        for x, y in points:
            radius = ((x - 54) ** 2 + (y - 54) ** 2) ** 0.5 + stroke / 2
            worst_radius = max(worst_radius, radius)
            if not (21 <= x <= 87 and 21 <= y <= 87):
                raise SystemExit(f'{relative}: path coordinate out of bounds: {(x, y)}')
            if radius > 33:
                raise SystemExit(f'{relative}: point plus stroke exceeds safe circle: {(x, y, radius)}')
    print(f'{relative}: minX={min(xs):g}, maxX={max(xs):g}, minY={min(ys):g}, maxY={max(ys):g}, max radius including stroke={worst_radius:.3f}dp')

definitions = set()
for path in RES.rglob('*.xml'):
    folder = path.parent.name.split('-')[0]
    if folder in ('drawable', 'mipmap'):
        definitions.add((folder, path.stem))
    elif folder == 'values':
        for node in ET.parse(path).getroot():
            kind = node.attrib.get('type', node.tag.split('}')[-1])
            name = node.attrib.get('name')
            if name:
                definitions.add((kind, name))

changed = [
    RES / 'drawable/ic_launcher_background.xml',
    RES / 'drawable/ic_launcher_foreground.xml',
    RES / 'drawable/ic_launcher_monochrome.xml',
    RES / 'drawable/avd_splash_icon.xml',
    RES / 'mipmap-anydpi-v26/ic_launcher.xml',
    RES / 'mipmap-anydpi-v26/ic_launcher_round.xml',
    RES / 'values/themes.xml',
    RES / 'values-v24/themes.xml',
    RES / 'values-v31/themes.xml',
    ROOT / 'app/src/main/AndroidManifest.xml',
]
references = re.compile(r'@(?P<kind>color|drawable|style|mipmap)/(?P<name>[A-Za-z0-9_.]+)')
missing = set()
for path in changed:
    for match in references.finditer(path.read_text(encoding='utf-8')):
        resource = (match.group('kind'), match.group('name'))
        if resource not in definitions:
            missing.add((path.relative_to(ROOT).as_posix(), *resource))
if missing:
    raise SystemExit(f'Missing resource references: {sorted(missing)}')
print(f'Resource references: passed ({len(changed)} files checked)')
