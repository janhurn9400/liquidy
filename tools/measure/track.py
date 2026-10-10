import numpy as np, glob, os, sys
from PIL import Image
D='./work/fr/'
files=sorted(glob.glob(D+'f_*.png')); ts=[int(os.path.basename(f)[2:8])/60 for f in files]
def load(i): return np.asarray(Image.open(files[i]).convert('L')).astype(float)
def band(img,y0,y1): return img[y0:y1].mean(0)
def shift(a,ref,maxs=402):
    best=(1e9,0)
    for s in range(-maxs,maxs+1):
        if s>=0: x=a[s:]; r=ref[:len(ref)-s]
        else: x=a[:s]; r=ref[-s:]
        if len(x)<60: continue
        e=np.mean((x-r)**2)
        if e<best[0]: best=(e,s)
    return best
