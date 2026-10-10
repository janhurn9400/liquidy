exec(open('./work/springfit.py').read())
import re,subprocess
rows=[l.split() for l in open('./work/page.txt')]
T=np.array([float(r[0]) for r in rows]); X=np.array([float(r[1]) for r in rows])
for (a,b,tgt,lab) in [(1.15,1.55,-402,'swipe1 ->p2'),(1.75,2.15,0,'swipe2 ->p1'),(2.217,2.58,-401,'swipe3 ->p2'),(2.617,3.0,0,'swipe4 ->p1')]:
    m=(T>=a-1e-6)&(T<=b+1e-6)
    fit(T[m],X[m],tgt,lab)
    fit(T[m],X[m],tgt,lab+' (z=1)',zfix=1.0)
