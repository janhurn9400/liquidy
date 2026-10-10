import numpy as np
from scipy.optimize import least_squares
def spring(t,x0,v0,w,z):
    # displacement from target, x0 at t=0
    if abs(z-1)<1e-4: z=1+1e-4
    if z<1:
        wd=w*np.sqrt(1-z*z)
        return np.exp(-z*w*t)*(x0*np.cos(wd*t)+(v0+z*w*x0)/wd*np.sin(wd*t))
    else:
        s=w*np.sqrt(z*z-1); r1=-z*w+s; r2=-z*w-s
        B=(v0-r1*x0)/(r2-r1); A=x0-B
        return A*np.exp(r1*t)+B*np.exp(r2*t)
def fit(t,x,target,label='',zfix=None):
    t=np.asarray(t)-t[0]; d=np.asarray(x,float)-target
    v0g=(d[1]-d[0])/(t[1]-t[0])
    def res(p):
        w,z,v0=p if zfix is None else (p[0],zfix,p[1])
        return spring(t,d[0],v0,w,z)-d
    p0=[15,0.9,v0g] if zfix is None else [15,v0g]
    lb=[1,0.2,-1e5] if zfix is None else [1,-1e5]; ub=[200,3,1e5] if zfix is None else [200,1e5]
    r=least_squares(res,p0,bounds=(lb,ub))
    if zfix is None: w,z,v0=r.x
    else: (w,v0),z=r.x,zfix
    rms=np.sqrt(np.mean(r.fun**2))
    print(f'{label}: response={2*np.pi/w:.3f}s  zeta={z:.3f}  stiffness={w*w:.0f}  v0={v0:.0f}pt/s  rms={rms:.2f}pt  n={len(t)}')
    return w,z,v0
