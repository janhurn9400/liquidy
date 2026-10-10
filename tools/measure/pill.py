import cv2,numpy as np,os
D='./work/fr/'
def crop(k): return cv2.imread(D+f'f_{k:06d}.png',0)[700:736,155:248].astype(float)
A=crop(54)  # t=0.9 search
B=crop(int(20.0*60))  # edit dots
for k in list(range(170,300))+list(range(650,820))+list(range(990,1050)):
    if not os.path.exists(D+f'f_{k:06d}.png'): continue
    c=crop(k); ea=np.mean((c-A)**2); eb=np.mean((c-B)**2)
    # dot-ness: find bright small blobs; print width of pill via column profile edges
    print(f'{k/60:.3f} dSearch={ea:7.1f} dDots={eb:7.1f}')
