#!/usr/bin/env python3
"""Measure a frame dumped by the glue (XROAR_DUMP_FRAME): how much of the
surface the CoCo's green screen fills, its aspect ratio, and how sharp its
text edges are.  Prints "fill_w fill_h aspect ramp" (fractions, ratio, pixels).
Needs Pillow and NumPy."""
import sys
from PIL import Image
import numpy as np

im = np.asarray(Image.open(sys.argv[1]).convert("RGB")).astype(float)
H, W, _ = im.shape
mask = (im[:, :, 1] > im[:, :, 0] + 40) & (im[:, :, 1] > im[:, :, 2] + 40)
ys, xs = np.where(mask)
bw, bh = xs.max() - xs.min() + 1, ys.max() - ys.min() + 1

# Edge sharpness: mean length of the ramps between text and background on a
# band of rows through the text (1 = hard pixels, 3 = smeared).
g = im.mean(axis=2)
rows = g[ys.min() + 25:ys.min() + 45, xs.min() + 3:xs.max() - 3]
hi, lo = np.percentile(rows, 95), np.percentile(rows, 2)
span = max(hi - lo, 1.0)
ramps = []
for r in rows:
    n = (r - lo) / span
    i = 0
    while i < len(n) - 1:
        if abs(n[i + 1] - n[i]) > 0.05:
            j, up = i, n[i + 1] > n[i]
            while j < len(n) - 1 and ((n[j + 1] > n[j]) if up else (n[j + 1] < n[j])) and abs(n[j + 1] - n[j]) > 0.02:
                j += 1
            if abs(n[j] - n[i]) > 0.6:
                ramps.append(j - i)
            i = j + 1
        else:
            i += 1
ramp = float(np.mean(ramps)) if ramps else 99.0
print(f"{bw / W:.3f} {bh / H:.3f} {bw / bh:.3f} {ramp:.2f}")
