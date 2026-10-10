import cv2, numpy as np, glob, os
D='./work/hr/'
ref=cv2.imread('./docs/reference/iphone/IMG_3123.PNG',0).astype(np.float32)
icons={'FaceTime':(90,872),'Camera':(923,872),'Mail':(90,1173),'Reminders':(646,1173)}
files=sorted(glob.glob(D+'h_*.png'))
angles=np.arange(-4,4.01,0.1)
res={k:[] for k in icons}
for f in files:
    k=int(os.path.basename(f)[2:8]); t=19.2+k/60
    img=cv2.imread(f,0).astype(np.float32)
    for name,(x,y) in icons.items():
        cx,cy=x+96,y+96
        crop=img[cy-110:cy+110, cx-110:cx+110]
        best=(-2,0,0,0)
        for a in angles:
            M=cv2.getRotationMatrix2D((cx,cy),a,1.0)
            rot=cv2.warpAffine(ref,M,(ref.shape[1],ref.shape[0]),flags=cv2.INTER_LINEAR)
            tpl=rot[cy-80:cy+80, cx-80:cx+80]
            r=cv2.matchTemplate(crop,tpl,cv2.TM_CCOEFF_NORMED)
            _,mv,_,ml=cv2.minMaxLoc(r)
            if mv>best[0]:
                # subpixel via parabola
                px,py=ml
                def sp(v0,v1,v2): d=(v0-2*v1+v2); return 0 if d==0 else 0.5*(v0-v2)/d
                ox=sp(r[py,px-1],r[py,px],r[py,px+1]) if 0<px<r.shape[1]-1 else 0
                oy=sp(r[py-1,px],r[py,px],r[py+1,px]) if 0<py<r.shape[0]-1 else 0
                best=(mv,a,(px+ox-30)/3,(py+oy-30)/3)
        res[name].append((t,)+best)
import json
json.dump({k:[[float(x) for x in r] for r in v] for k,v in res.items()},open('./work/wiggle.json','w'))
for name in icons:
    print(name)
    for t,s,a,dx,dy in res[name][::2]: print(f'  {t:.3f} ang={a:+.1f} dx={dx:+.2f}pt dy={dy:+.2f}pt score={s:.2f}')
