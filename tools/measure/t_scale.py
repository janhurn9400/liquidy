import numpy as np, cv2, glob, os, sys
D='./work/fr/'
files=sorted(glob.glob(D+'f_*.png')); ts=np.array([int(os.path.basename(f)[2:8])/60 for f in files])
a,b,tref=float(sys.argv[1]),float(sys.argv[2]),float(sys.argv[3])
x0,y0,x1,y1=map(int,sys.argv[4:8])  # template rect in ref frame (pt)
ref=cv2.imread(files[int(np.argmin(abs(ts-tref)))],0)
T=ref[y0:y1,x0:x1].astype(np.float32)
tcx,tcy=(x0+x1)/2,(y0+y1)/2
prev_s=None
for i in np.where((ts>=a)&(ts<=b))[0]:
    img=cv2.imread(files[i],0).astype(np.float32)
    best=(-2,0,0,0)
    ss=np.arange(0.10,1.12,0.02) if prev_s is None else np.arange(max(0.08,prev_s-0.25),min(1.15,prev_s+0.25),0.01)
    for s in ss:
        t=cv2.resize(T,None,fx=s,fy=s,interpolation=cv2.INTER_AREA)
        if t.shape[0]<8 or t.shape[1]<8: continue
        r=cv2.matchTemplate(img,t,cv2.TM_CCOEFF_NORMED)
        _,mv,_,ml=cv2.minMaxLoc(r)
        if mv>best[0]: best=(mv,s,ml[0]+t.shape[1]/2,ml[1]+t.shape[0]/2)
    mv,s,cx,cy=best
    print(f'{ts[i]:.3f} scale={s:.2f} cx={cx:6.1f} cy={cy:6.1f} score={mv:.2f}')
