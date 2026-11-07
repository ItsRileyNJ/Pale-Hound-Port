"""Author short posture transitions and a restrained additive idle breathing loop."""
from pathlib import Path
import json,math
P=Path(__file__).resolve().parents[1]/'src/main/resources/assets/pale_hound/animations/pale_hound.animation.json'
data=json.loads(P.read_text());animations=data['animations'];prefix='animation.pale_hound.'
poses={'walk':'calm_idle','run':'run_idle','crouch':'crouch_idle','stalk':'crawl_idle','tunnel':'crawl_tunnel_idle','inspect':'crawl_low_idle'}
def first(value,default):
 if value is None:return default
 while isinstance(value,dict):value=value.get('vector',value.get('post',value.get('pre',next(iter(value.values())))))
 if not isinstance(value,list):return default
 if len(value)==1:value=value*3
 if len(value)!=3:return default
 return [float(v) if isinstance(v,(int,float)) else float(v.split('+')[0]) if v.split('+')[0].replace('.','',1).replace('-','',1).isdigit() else default[i] for i,v in enumerate(value)]
for src,sname in poses.items():
 for dst,dname in poses.items():
  if src==dst:continue
  a=animations[prefix+sname]['bones'];b=animations[prefix+dname]['bones']
  length=.3 if dst=='run' else .6 if dst in ['stalk','inspect'] else .45
  bones={}
  for bone in a.keys()|b.keys():
   bones[bone]={}
   for channel,default in [('rotation',[0,0,0]),('position',[0,0,0]),('scale',[1,1,1])]:
    left=first(a.get(bone,{}).get(channel),default);right=first(b.get(bone,{}).get(channel),default)
    frames={}
    for t in [0,.25,.7,1]:
     eased=t*t*(3-2*t)
     values=[round(left[i]+(right[i]-left[i])*eased,5) for i in range(3)]
     frames[f'{length*t:.4f}']={'vector':values,'easing':'easeInOutSine'}
    bones[bone][channel]=frames
  animations[prefix+'transition_'+src+'_to_'+dst]={'loop':False,'animation_length':length,'bones':bones}
animations[prefix+'idle_breathing']={'loop':True,'animation_length':4.0,'bones':{'upperbody':{'scale':{
 '0.0':{'vector':[1,1,1],'easing':'easeInOutSine'},
 '1.6':{'vector':[1.01,1.006,1.012],'easing':'easeInOutSine'},
 '3.5':{'vector':[1,1,1],'easing':'easeInOutSine'},
 '4.0':{'vector':[1,1,1],'easing':'easeInOutSine'}}}}}
P.write_text(json.dumps(data,indent=2)+'\n')
print('Built 30 directed posture transitions and additive idle breathing.')
