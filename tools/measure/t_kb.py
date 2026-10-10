exec(open('./work/track.py').read())
import sys
a,b=float(sys.argv[1]),float(sys.argv[2])
ref=load(int(np.argmin(abs(np.array(ts)-b))))
# template: keyboard key row strip in final frame; find vertical offset by matching row profile
prof_ref=ref[:, 20:380].mean(1)
for i in range(len(files)):
    if not (a<=ts[i]<=b): continue
    im=load(i); p=im[:,20:380].mean(1)
    # match bottom-anchored keyboard region rows 560..874 of ref against p shifted down by d
    best=(1e9,0)
    for d in range(0,330):
        seg=prof_ref[560:874-d]; cur=p[560+d:874]
        if len(seg)<30: break
        e=np.mean((seg-cur)**2)
        if e<best[0]: best=(e,d)
    # also wallpaper zoom/blur: std of a mid region as blur proxy
    print(f'{ts[i]:.3f} kb_offset={best[1]:3d} err={best[0]:.1f} midstd={im[350:500,20:380].std():.1f}')
