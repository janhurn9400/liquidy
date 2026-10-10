exec(open('./work/springfit.py').read())
import sys
rows=[l.replace('=',' ').split() for l in open(sys.argv[1])]
T=np.array([float(r[0]) for r in rows]); s=np.array([float(r[2]) for r in rows]); cx=np.array([float(r[4]) for r in rows]); cy=np.array([float(r[6]) for r in rows])
a,b=float(sys.argv[2]),float(sys.argv[3]); m=(T>=a)&(T<=b)
ts,te=sys.argv[4:6]
fit(T[m],cx[m],float(ts),'cx'); fit(T[m],cy[m],float(te),'cy'); fit(T[m],s[m]*100,float(sys.argv[6]),'scale%')
