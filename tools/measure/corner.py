import numpy as np, sys
from PIL import Image
im=np.asarray(Image.open(sys.argv[1]).convert('RGB')).astype(float)
H,W,_=im.shape
def bez(p0,p1,p2,p3,n=200):
    t=np.linspace(0,1,n)[:,None]
    return (1-t)**3*p0+3*(1-t)**2*t*p1+3*(1-t)*t**2*p2+t**3*p3
def continuous_inset(r, ys):
    # top-left corner in coords (x right, y down), origin at shape corner. curve from (0,1.5287r) to (1.5287r,0)
    c=[(1.52866483,0),(1.08849323,0),(0.86840701,0),(0.63149399,0.07491100),
       (0.37282401,0.16905956),(0.16905956,0.37282401),(0.07491100,0.63149399),
       (0,0.86840701),(0,1.08849323),(0,1.52866483)]
    c=np.array(c)*r
    pts=np.vstack([bez(*c[0:4]),bez(*c[3:7]),bez(*c[6:10])])
    o=np.argsort(pts[:,1]); 
    return np.interp(ys, pts[o,1], pts[o,0], right=0)
def circle_inset(r,ys):
    ys=np.minimum(ys,r); return r-np.sqrt(np.maximum(r*r-(r-ys)**2,0))
def measure(x0,y0,bg_from='left',thr=22,maxk=None,label=''):
    # shape top-left at (x0,y0) (first interior pixel col/row). measure inset per row
    ins=[]
    for k in range(maxk):
        y=y0+k
        row=im[y,x0-6:x0+maxk]
        bg=im[y,x0-12:x0-6].mean(0) if bg_from=='left' else im[y0-8:y0-3,x0+maxk].mean(0)
        dd=np.abs(row-bg).max(1)
        idx=np.argmax(dd>thr)
        ins.append(idx-6)
    ys=np.arange(maxk)+0.5
    ins=np.array(ins,float)
    best=None
    for kind,f in [('continuous',continuous_inset),('circle',circle_inset)]:
        rs=np.arange(5,maxk,0.25)
        errs=[np.mean((f(r,ys)-ins)**2) for r in rs]
        i=int(np.argmin(errs)); print(f'{label} {kind}: r={rs[i]:.2f}px = {rs[i]/3:.2f}pt rms={np.sqrt(errs[i]):.2f}px')
    print('   inset profile', ins[:40].astype(int).tolist())
