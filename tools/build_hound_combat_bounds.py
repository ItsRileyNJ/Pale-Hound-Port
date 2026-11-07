"""Bake conservative interaction bounds from the shipped rig; movement bounds remain separate.
Uses the same handedness and Z/Y/X rotation order as GeckoLib's model loader.
"""
import json,math,itertools
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pale_hound'
bones=json.loads((ROOT/'geo/pale_hound.geo.json').read_text())['minecraft:geometry'][0]['bones']
animations=json.loads((ROOT/'animations/pale_hound.animation.json').read_text())['animations']
def translate(v):
 m=np.eye(4);m[:3,3]=v;return m
def rotate(v):
 x,y,z=np.radians(np.array(v)*[-1,-1,1]);cx,cy,cz=np.cos([x,y,z]);sx,sy,sz=np.sin([x,y,z])
 return np.array([[cz,-sz,0,0],[sz,cz,0,0],[0,0,1,0],[0,0,0,1]])@np.array([[cy,0,sy,0],[0,1,0,0],[-sy,0,cy,0],[0,0,0,1]])@np.array([[1,0,0,0],[0,cx,-sx,0],[0,sx,cx,0],[0,0,0,1]])
def vec(v):return np.array(v)*[-1,1,1]
def value(v,t,default):
 if v is None:return np.array(default,dtype=float)
 if isinstance(v,dict):v=min(v.items(),key=lambda kv:abs(float(kv[0])-t))[1]
 return np.array([float(x) if isinstance(x,(int,float)) else default[i] for i,x in enumerate(v)])
def group(name):
 if name in ['head','neck']:return 1
 if name.startswith('left') and ('arm' in name or 'hand' in name):return 2
 if name.startswith('right') and ('arm' in name or 'hand' in name):return 3
 if 'left' in name and ('leg' in name or 'foot' in name):return 4
 if 'right' in name and ('leg' in name or 'foot' in name):return 5
 return 0
result={}
for pose,idle,move in [('standing',None,None),('crouching','crouch_idle',None),('tall','crawl_idle','crawl'),('tunnel','crawl_tunnel_idle','crawl_tunnel'),('low','crawl_low_idle','crawl_low')]:
 points=[[] for _ in range(6)]
 for animation,t in [(idle,0)]+([(move,t/20) for t in range(33)] if move else []):
  channels=animations.get('animation.pale_hound.'+str(animation),{}).get('bones',{}); transforms={}
  for b in bones:
   p=vec(b.get('pivot',[0,0,0]));c=channels.get(b['name'],{})
   rotation=np.array(b.get('rotation',[0,0,0]))+value(c.get('rotation'),t,[0,0,0])
   scale=value(c.get('scale'),t,[1,1,1]);sm=np.diag([*scale,1])
   local=translate(vec(value(c.get('position'),t,[0,0,0])))@translate(p)@rotate(rotation)@sm@translate(-p)
   transforms[b['name']]=transforms.get(b.get('parent'),np.eye(4))@local
   for cube in b.get('cubes',[]):
    origin=np.array(cube['origin']);size=np.array(cube['size'])
    # Thin decorative planes are not reliable melee surfaces.
    if min(size)<=0:continue
    cp=vec(cube.get('pivot',[0,0,0]));cm=translate(cp)@rotate(cube.get('rotation',[0,0,0]))@translate(-cp)
    for bits in itertools.product([0,1],repeat=3):
     vertex=vec(origin+size*np.array(bits));v=transforms[b['name']]@cm@np.array([*vertex,1])
     # Renderer orientation 180 degrees about Y makes model forward match +Z.
     points[group(b['name'])].append(v[:3]*[-1,1,-1]*1.3/16)
 result[pose]=[]
 for cloud in points:
  a=np.array(cloud);lo=a.min(axis=0)-.06;hi=a.max(axis=0)+.06
  lo[1]=max(0,lo[1]);result[pose].append([round(float(x),4) for x in [*lo,*hi]])
(ROOT/'combat_bounds.json').write_text(json.dumps(result,indent=2)+'\n')
for pose,boxes in result.items():print(pose,boxes[:2])
