"""Trace the approved cloud-sofa silhouette from a BMP converted by sips.

Run: sips -s format bmp ../clouds/cloud-sofa.png --out /tmp/ergotube-cloud.bmp
     python3 trace_logo.py /tmp/ergotube-cloud.bmp
No bitmap changes: this extracts native vector geometry from the approved artwork.
"""
import struct
import sys
from pathlib import Path

data = Path(sys.argv[1]).read_bytes()
offset = struct.unpack_from('<I', data, 10)[0]
w, h = struct.unpack_from('<ii', data, 18)
stride = (w * 3 + 3) // 4 * 4
mask = set()
for y in range(130, 815):
    for x in range(110, 1135):
        row = y if h < 0 else h - y - 1
        b, g, r = data[offset + row * stride + x * 3:offset + row * stride + x * 3 + 3]
        if r > 170 and g > 115:
            mask.add((x, y))
edges = {}
for x, y in mask:
    for other, a, b in [((x,y-1),(x,y),(x+1,y)), ((x+1,y),(x+1,y),(x+1,y+1)), ((x,y+1),(x+1,y+1),(x,y+1)), ((x-1,y),(x,y+1),(x,y))]:
        if other not in mask:
            edges[a] = b

def simplify(p, eps=1.2):
    if len(p) < 3:
        return p
    a, b = p[0], p[-1]
    dx, dy = b[0]-a[0], b[1]-a[1]
    denom = (dx*dx+dy*dy)**0.5 or 1
    dist = [abs(dy*(q[0]-a[0])-dx*(q[1]-a[1]))/denom for q in p[1:-1]]
    largest = max(dist, default=0)
    if largest <= eps:
        return [a,b]
    i = dist.index(largest)+1
    return simplify(p[:i+1],eps)[:-1]+simplify(p[i:],eps)

loops=[]
while edges:
    start=next(iter(edges)); p=[start]; cur=edges.pop(start)
    while cur != start:
        p.append(cur); cur=edges.pop(cur)
    area=abs(sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(p,p[1:]+p[:1])))/2
    if area > 100:
        mid=len(p)//2
        loops.append(simplify(p[:mid+1])[:-1]+simplify(p[mid:]+p[:1])[:-1])
x0=min(x for x,y in mask); y0=min(y for x,y in mask)
scale=1000/(max(x for x,y in mask)+1-x0)
height=round((max(y for x,y in mask)+1-y0)*scale)
path=' '.join('M'+' L'.join(f'{(x-x0)*scale:.2f},{(y-y0)*scale:.2f}' for x,y in p)+' Z' for p in loops)
root=Path(__file__).resolve().parents[4]
res=root/'app/src/main/res/drawable'
shape=f'<path android:fillColor="#F3C39E" android:fillType="evenOdd" android:pathData="{path}" />'
header='<vector xmlns:android="http://schemas.android.com/apk/res/android" '
(res/'ic_brand.xml').write_text(header+f'android:width="48dp" android:height="{height*48/1000:.2f}dp" android:viewportWidth="1000" android:viewportHeight="{height}">\n'+shape+'\n</vector>\n')
(res/'ic_launcher_foreground.xml').write_text(header+'android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'+f'<group android:scaleX="0.064" android:scaleY="0.064" android:translateX="22" android:translateY="{(108-height*.064)/2:.3f}">\n'+shape+'\n</group>\n</vector>\n')
(res/'ic_notification.xml').write_text(header+'android:width="24dp" android:height="24dp" android:viewportWidth="1000" android:viewportHeight="1000">\n'+f'<group android:translateY="{(1000-height)/2}">\n'+shape.replace('#F3C39E','#FFFFFF')+'\n</group>\n</vector>\n')
print(f'Traced {len(loops)} contours; viewport 1000 x {height}')
