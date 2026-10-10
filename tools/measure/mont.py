import sys,os
from PIL import Image, ImageDraw
D='./work/fr/'
a,b,step,out=float(sys.argv[1]),float(sys.argv[2]),int(sys.argv[3]),sys.argv[4]
cols=int(sys.argv[5]) if len(sys.argv)>5 else 12
fs=[D+f'f_{k:06d}.png' for k in range(int(a*60),int(b*60)+1,step) if os.path.exists(D+f'f_{k:06d}.png')]
ims=[Image.open(f).resize((134,291)) for f in fs]
rows=(len(ims)+cols-1)//cols
W=Image.new('RGB',(cols*136,rows*305),'white'); d=ImageDraw.Draw(W)
for i,(im,f) in enumerate(zip(ims,fs)):
    x=(i%cols)*136; y=(i//cols)*305; W.paste(im,(x,y)); d.text((x+2,y+292),f"{int(os.path.basename(f)[2:8])/60:.3f}",fill='black')
W.save(out)
