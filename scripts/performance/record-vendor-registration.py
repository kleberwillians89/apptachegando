#!/usr/bin/env python3
"""Real Android recording of the local-only vendor registration. No Auth/Supabase requests.
Requires Pixel 9 1080x2424 emulator at the normal onboarding screen and ffmpeg.
"""
import argparse,json,os,re,shutil,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',required=True);a=p.parse_args()
assert a.serial.startswith('emulator-') and shutil.which('ffmpeg')
adb_path=os.environ.get('ADB',str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
raw=out/'TaChegando-cadastro-vendedor-original.mp4';video=out/'TaChegando-cadastro-vendedor.mp4'
assert not raw.exists() and not video.exists(),'Preserve existing videos; choose a new folder.'
def adb(*args):
 r=subprocess.run([adb_path,'-s',a.serial,*args],capture_output=True,timeout=40)
 if r.returncode:raise RuntimeError('ADB failed: '+' '.join(args[:3]))
 return r.stdout
def nodes():
 adb('shell','uiautomator','dump','/sdcard/tch-presentation-window.xml');return list(ET.fromstring(adb('shell','cat','/sdcard/tch-presentation-window.xml')).iter('node'))
def tap(label):
 found=[n for n in nodes() if n.get('text')==label or n.get('content-desc')==label];assert found,'Target missing: '+label
 b=list(map(int,re.findall(r'\d+',found[0].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.7)
def enter(value):
 fields=[n for n in nodes() if n.get('class')=='android.widget.EditText'];assert len(fields)==1,'Only demo name/search fields allowed.'
 b=list(map(int,re.findall(r'\d+',fields[0].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));adb('shell','input','text',value);adb('shell','input','keyevent','KEYCODE_BACK')
trace=[];started=0
def observe(name,expected,hold=2):
 ns=nodes();texts=[n.get('text') or n.get('content-desc') for n in ns if n.get('text') or n.get('content-desc')]
 assert not any('@' in t or 'access_token' in t or 'refresh_token' in t for t in texts),'Sensitive text; stop.'
 assert expected in texts,'Expected state missing: '+name
 trace.append({'screen':name,'elapsedSeconds':round(time.monotonic()-started,2),'texts':texts});(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'));print('Verified:',name,flush=True);time.sleep(hold)
assert 'Explorar demonstração' in [n.get('text') for n in nodes()]
assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip(),'Another recording is active.'
remote='/sdcard/tch-vendor-registration-20261103.mp4'
record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','180',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();pid=''
try:
 time.sleep(1);pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',pid)
 adb('shell','am','force-stop','org.example.project.performance');adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(3)
 observe('01-abertura','Explorar demonstração',hold=2)
 tap('Explorar demonstração');observe('02-inicio-demo','Seu bairro, mais perto',hold=2)
 tap('Cadastrar na demonstração');observe('03-cadastro-perfis','Nome fictício',hold=2)
 tap('Vendedor');observe('04-perfil-vendedor','Vendedor',hold=3)
 enter('Beto%sDemo');observe('05-nome-ficticio','Concluir cadastro demo',hold=3)
 tap('Concluir cadastro demo');observe('06-cadastro-vendedor-concluido','Cadastro de demonstração concluído',hold=7)
 assert 'Perfil simulado: vendedor.' in [n.get('text') for n in nodes()], 'Wrong registration role.'

finally:
 if pid:adb('shell','kill','-2',pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 adb('pull',remote,str(raw));(out/'VIDEO_TRACE.json').write_text(json.dumps({'capture':'Actual Android screenrecord, no fabricated UI','vendorRegistration':'local simulation only; no real Auth account','credentialsRecorded':False,'trace':trace},ensure_ascii=False,indent=2)+'\n')
subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
print('Video:',video)
