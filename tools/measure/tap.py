import cv2,numpy as np,glob,os
fs=sorted(glob.glob('./work/hl/l_*.png'))
ref=cv2.imread(fs[0]).astype(float)
x,y=646,872
R=ref[y+20:y+172,x+20:x+172]
for f in fs[:30]:
    k=int(os.path.basename(f)[2:8]); im=cv2.imread(f).astype(float)
    C=im[y+20:y+172,x+20:x+172]
    ratio=(C.mean()/R.mean())
    # also icon bbox via diff from bg near icon (scale check): measure column extents where row y+96 differs from bg
    row=im[y+96, x-80:x+272].mean(1); bg=row[:20].mean()
    nz=np.where(np.abs(row-bg)>20)[0]
    print(f'{23.4+k/60:.3f} lum_ratio={ratio:.3f} icon_x_extent={(nz[-1]-nz[0]+1)/3 if len(nz) else 0:.1f}pt')
