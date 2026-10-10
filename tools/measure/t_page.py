exec(open('./work/track.py').read())
import sys
t0,t1,tr1,tr2=map(float,sys.argv[1:5])
idx=lambda t: int(np.argmin(np.abs(np.array(ts)-t)))
y0,y1=280,560
R1=band(load(idx(tr1)),y0,y1); R2=band(load(idx(tr2)),y0,y1)
for i in range(idx(t0),idx(t1)+1):
    b=band(load(i),y0,y1)
    e1,s1=shift(b,R1,390); e2,s2=shift(b,R2,390)
    pos = s1 if e1<e2 else s2-402
    print(f'{ts[i]:.3f} {pos:5d}  e1={e1:7.1f} e2={e2:7.1f}')
