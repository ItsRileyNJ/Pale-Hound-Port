"""Build only the separate reference maquette; never reads/writes original Hound assets."""
from pathlib import Path
import json, math, random
from PIL import Image
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/pale_hound'
bones = [{'name':'root','pivot':[0,0,0]}]
def bone(name, parent='root'):
    b={'name':name,'parent':parent,'pivot':[0,0,0],'cubes':[]}; bones.append(b); return b
palette=[(86,75,69),(132,109,98),(42,39,37),(35,24,24),(225,215,177),(174,249,255),(31,30,29),(71,48,45)]
def cube(b, origin, size, material=0, rotation=None, pivot=None):
    u=material*16
    faces={face:{'uv':[u,0],'uv_size':[16,16]} for face in ['north','south','east','west','up','down']}
    c={'origin':origin,'size':size,'uv':faces}
    if rotation: c.update(rotation=rotation,pivot=list(pivot or origin))
    b['cubes'].append(c)
def segment(b,a,c,width,depth,mat=0):
    dx,dy,dz=[c[i]-a[i] for i in range(3)]; length=math.sqrt(dx*dx+dy*dy+dz*dz)
    cube(b,[a[0]-width/2,a[1],a[2]-depth/2],[width,length,depth],mat,
         [-math.degrees(math.atan2(dz,dy)),0,math.degrees(math.atan2(dx,math.hypot(dy,dz)))],a)
body=bone('torso')
cube(body,[-8,25,-7],[16,12,19]); cube(body,[-6,24,9],[12,9,12]);cube(body,[-9,29,-9],[18,10,9])
cube(body,[-5,24,-8],[10,6,9],2)
# Neck joins the chest to the skull continuously.
neck=bone('neck','torso');segment(neck,[0,33,-5],[0,34,-18],7.5,8)
head=bone('head','neck');cube(head,[-6.5,29,-22],[13,10,10]);cube(head,[-5,29,-29],[10,5,9]);cube(head,[-3,30,-31],[6,3,3],6)
# Cheek framing, brow ridges, inset eyes and ear plates.
for s in [-1,1]:
    cube(head,[s*6-1.5,28,-22],[3,8,6],0)
    cube(head,[s*4-1.5,35,-23],[3,1.8,1],5)
    cube(head,[s*4-2.4,37,-23.5],[4.8,2,3],0,[0,0,s*14],[s*4,37,-22])
    ear=bone(('left' if s==1 else 'right')+'_ear','head')
    cube(ear,[s*6-2,37,-17],[4,8,3],0,[0,0,-s*18],[s*6,37,-16])
    cube(ear,[s*6-1.2,38,-17.2],[2.4,5.4,.6],7,[0,0,-s*18],[s*6,37,-16])
    cube(head,[s*5-1,25,-21],[2,6,6],2)
# Open mouth with separate jaw, tongue and staggered teeth.
cube(head,[-4.5,27,-28.5],[9,2,8],3)
jaw=bone('jaw','head');segment(jaw,[0,28,-16],[0,20,-24],8,3,0)
cube(jaw,[-4,19,-28],[8,2.5,6]);cube(jaw,[-3.2,21,-27],[6.4,.7,5],3)
segment(jaw,[0,21,-26],[0,26,-20],3.5,1,7)
for x in [-3.5,-1.3,1.3,3.5]:
    cube(head,[x-.65,25 if abs(x)>3 else 27,-28],[1.3,4 if abs(x)>3 else 2,1.4],4)
    cube(jaw,[x-.6,21,-27.7],[1.2,1.6,1.4],4)
for z in [-25,-22]:
    for x in [-3.6,3.6]: cube(jaw,[x-.5,21,z],[1,1.7,1.2],4)
# Long splayed forearms and folded, digitigrade rear legs.
for s in [-1,1]:
    side='left' if s==1 else 'right'
    upper=bone(side+'_upper_arm','torso');segment(upper,[s*8,40,-5],[s*13,24,-5],4.8,5.6)
    fore=bone(side+'_forearm',upper['name']);segment(fore,[s*13,24,-5],[s*15,3,-15],3.4,3.7)
    segment(fore,[s*13.4,21,-6],[s*15.2,5,-14],2.2,2.8,1)
    paw=bone(side+'_hand',fore['name']);cube(paw,[s*15-3,0,-19],[6,3,7])
    for i in range(4):
        x=s*15-3+i*1.6;cube(paw,[x,0,-21+(abs(i-1.5)*.5)],[1.4,2,4],1)
        cube(paw,[x+.25,.2,-22+(abs(i-1.5)*.5)],[.9,1,1.8],6)
    thigh=bone(side+'_thigh','torso');segment(thigh,[s*6,34,15],[s*10,20,7],4.8,5.6)
    shin=bone(side+'_shin',thigh['name']);segment(shin,[s*10,20,7],[s*9,10,20],3.2,3.5)
    foot=bone(side+'_hind_paw',shin['name']);segment(foot,[s*9,10,20],[s*9,2,16],2.6,2.6)
    cube(foot,[s*9-2.8,0,12],[5.6,2.5,7])
    for i in range(3): cube(foot,[s*9-2.7+i*1.8,0,10.5],[1.6,2,4],1)
# Layered mane follows the humped back, with irregular raised central tufts.
mane=bone('mane','torso')
for j,z in enumerate(range(-15,23,4)):
    y=39- max(0,z+3)*.36
    for i,x in enumerate([-8,-4,0,4,8]):
        h=(2.4 if x==0 else 1.6)-max(0,j-5)*.12
        cube(mane,[x-1.25,y-abs(x)*.25,z],[2.5,h,5],2,[-28-j*1.8,0,-x*1.4],[x,y,z])
# Shaggy chest, side coat, and tapering articulated tail.
for s in [-1,1]:
    for j in range(4): cube(mane,[s*8-1.4,27-j,-6+j*5],[1.8,3,3.5],2,[18,0,s*18],[s*8,29-j,-6+j*5])
tail=bone('tail','torso')
points=[[0,28,19],[0,24,27],[0,18,34],[0,11,40],[0,8,44]]
for i in range(4):
    segment(tail,points[i],points[i+1],5-i,5-i,2)
    for s in [-1,1]:
        a=points[i]; cube(tail,[a[0]+s*2-1.5,a[1]-3,a[2]],[3,4,8-i],2,[30,0,s*12],a)
# Raise the torso and its attached features; limb endpoints above already meet the taller shoulder/hip anchors.
for b in bones:
    if b['name'] in ['root'] or any(part in b['name'] for part in ['upper_arm','forearm','hand','thigh','shin','hind_paw']):
        continue
    b['pivot'][1] += 6
    for c in b.get('cubes', []):
        c['origin'][1] += 6
        if 'pivot' in c: c['pivot'][1] += 6
head['pivot'] = [0,40,-17]
# Bake scaling into geometry: the GeckoLib geometry format does not apply bone scale.
for c in body['cubes']:
    c['origin'][0] *= 0.85
    c['size'][0] *= 0.85
anchor = [0,40,-17]
for b in bones:
    if b['name'] not in ['head','jaw','left_ear','right_ear']: continue
    b['pivot'] = [anchor[i] + (b['pivot'][i]-anchor[i])*0.78 for i in range(3)]
    for c in b['cubes']:
        c['origin'] = [anchor[i] + (c['origin'][i]-anchor[i])*0.78 for i in range(3)]
        c['size'] = [v*0.78 for v in c['size']]
        if 'pivot' in c: c['pivot'] = [anchor[i] + (c['pivot'][i]-anchor[i])*0.78 for i in range(3)]
geo={'format_version' :'1.12.0','minecraft:geometry':[{'description':{'identifier':'geometry.reference_preview','texture_width':128,'texture_height':16,'visible_bounds_width':7,'visible_bounds_height':5,'visible_bounds_offset':[0,1.5,0]},'bones':bones}]}
(ROOT/'geo/reference_preview.geo.json').write_text(json.dumps(geo,indent=2)+'\n')
(ROOT/'animations/reference_preview.animation.json').write_text(json.dumps({'format_version':'1.8.0','animations':{}},indent=2)+'\n')
rng=random.Random(19);image=Image.new('RGBA',(128,16));glow=Image.new('RGBA',(128,16))
for m,base in enumerate(palette):
    for y in range(16):
        for x in range(16):
            tone=rng.choice([-13,-7,0,0,6,10])
            color=base
            if m==0 and ((x//3+y//4)%5==0): color=palette[1]
            pixel=tuple(max(0,min(255,c+tone)) for c in color)+(255,)
            image.putpixel((m*16+x,y),pixel)
            if m==5: glow.putpixel((m*16+x,y),pixel)
image.save(ROOT/'textures/entity/reference_preview.png');glow.save(ROOT/'textures/entity/reference_preview_glowmask.png')
print(f'Built {len(bones)} bones and {sum(len(b.get("cubes",[])) for b in bones)} cubes')
