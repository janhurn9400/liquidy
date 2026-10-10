import json,numpy as np
from scipy.optimize import curve_fit
r=json.load(open('./work/wiggle.json'))
f=lambda t,A,P,ph,c: A*np.sin(2*np.pi*t/P+ph)+c
for name,v in r.items():
    v=np.array(v); m=(v[:,0]>=19.40)&(v[:,0]<=20.60)
    t=v[m,0]; 
    out=[]
    for col,lab in [(2,'angle'),(3,'dx'),(4,'dy')]:
        p,_=curve_fit(f,t,v[m,col],p0=[1.5 if col==2 else 0.4,0.27,0,0])
        res=np.sqrt(np.mean((f(t,*p)-v[m,col])**2))
        out.append(f'{lab}: A={abs(p[0]):.2f} P={p[1]:.4f}s phase={(np.degrees(p[2]) + (180 if p[0]<0 else 0))%360:5.0f}° rms={res:.2f}')
    print(name,' | '.join(out))
