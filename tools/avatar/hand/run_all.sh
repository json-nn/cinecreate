#!/usr/bin/env bash
# render the three variants: run_all.sh <size> <prefix>
cd "$(dirname "$0")"
B="C:/Program Files/Blender Foundation/Blender 5.2/blender.exe"
SIZE=${1:-800}
PREFIX=${2:-test}
for v in rest flat place; do
  "$B" --background --factory-startup --python build.py -- "$v" "$(pwd)/${PREFIX}_$v.png" "$SIZE" 2>&1 | grep -E "RENDERED|Error|Traceback|line [0-9]+,|Exception" | head -8
done
python -I - "$PREFIX" <<'EOF'
import sys
from PIL import Image
p = sys.argv[1]
ims = [Image.open(f'{p}_{v}.png').convert('RGBA') for v in ('rest', 'flat', 'place')]
w = ims[0].width
sheet = Image.new('RGBA', (3 * w + 40, w), (170, 190, 220, 255))
for i, im in enumerate(ims):
    sheet.paste(im, (i * (w + 20), 0), im)
sheet.resize((1220, int(1220 * w / (3 * w + 40)))).save(f'{p}_sheet.png')
EOF
