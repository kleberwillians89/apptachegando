#!/usr/bin/env python3
"""Real Android recording of the local-only vendor journey. No Auth/Supabase requests.
Requires Pixel 9 1080x2424 emulator at the normal onboarding screen and ffmpeg.
"""
import argparse,json,os,re,shutil,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',required=True);a=p.parse_args()
assert a.serial.startswith('emulator-') and shutil.which('ffmpeg')
adb_path=os.environ.get('ADB',str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
raw=out/'TaChegando-vendedor-jornada-original.mp4';video=out/'TaChegando-vendedor-jornada.mp4'
assert not raw.exists() and not video.exists(),'Preserve existing videos; choose a new folder.'
def adb(*args):
 r=subprocess.run([adb_path,'-s',a.serial,*args],capture_output=True,timeout=40)
 if r.returncode:raise RuntimeError('ADB failed: '+' '.join(args[:3]))
 return r.stdout
def nodes():
 adb('shell','uiautomator','dump','/sdcard/tch-presentation-window.xml');return list(ET.fromstring(adb('shell','cat','/sdcard/tch-presentation-window.xml')).iter('node'))
def tap(label):
 found=[n for n in nodes() if n.get('text')==label or n.get('content-desc')==label];assert found,'Target missing: '+label
 b=list(map(int,re.findall(r'\d+',found[0].get('bounds'))));x=str((b[0]+b[2])//2);y=str((b[1]+b[3])//2);adb('shell','input','swipe',x,y,x,y,'110');time.sleep(1.0)
def type_text(value):
 for char in value.replace('%s',' '):
  adb('shell','input','text','%s' if char==' ' else char);time.sleep(.09)
def enter(value):
 fields=[n for n in nodes() if n.get('class')=='android.widget.EditText'];assert len(fields)==1,'Only demo name/search fields allowed.'
 b=list(map(int,re.findall(r'\d+',fields[0].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));type_text(value);adb('shell','input','keyevent','KEYCODE_BACK')
def edit_field(index,value):
 fs=[n for n in nodes() if n.get('class')=='android.widget.EditText'];assert len(fs)==2
 n=fs[index];b=list(map(int,re.findall(r'\d+',n.get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));adb('shell','input','keyevent','KEYCODE_MOVE_END')
 if n.get('text'):
  for _ in n.get('text'):adb('shell','input','keyevent','KEYCODE_DEL');time.sleep(.08)
 type_text(value);adb('shell','input','keyevent','KEYCODE_BACK')
trace=[];started=0
def observe(name,expected,hold=2):
 ns=nodes();texts=[n.get('text') or n.get('content-desc') for n in ns if n.get('text') or n.get('content-desc')]
 assert not any('@' in t or 'access_token' in t or 'refresh_token' in t for t in texts),'Sensitive text; stop.'
 assert expected in texts,'Expected state missing: '+name
 trace.append({'screen':name,'elapsedSeconds':round(time.monotonic()-started,2),'texts':texts});(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'));print('Verified:',name,flush=True);time.sleep(hold)
assert 'Explorar demonstração' in [n.get('text') for n in nodes()]
assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip(),'Another recording is active.'
touches=adb('shell','settings','get','system','show_touches').decode().strip()
assert touches in ('null','0','1'), 'Unknown touch setting; leave untouched.'
adb('shell','settings','put','system','show_touches','1')
remote='/sdcard/tch-vendor-journey-20261103.mp4'
record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','0',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();pid=''
try:
 time.sleep(1);pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',pid)
 adb('shell','am','force-stop','org.example.project.performance');adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(3)
 observe('01-abertura','Explorar demonstração',hold=2)
 tap('Explorar demonstração');tap('Cadastrar na demonstração');tap('Vendedor');enter('Beto%sDemo')
 observe('02-cadastro-vendedor','Concluir cadastro demo',hold=2)
 tap('Concluir cadastro demo');observe('03-cadastro-concluido','Ir para meu painel',hold=3)
 tap('Ir para meu painel');observe('04-painel-do-vendedor','Offline no catálogo local',hold=3)
 tap('Meus produtos');tap('Editar Sanduíche artesanal');edit_field(1,'14.90')
 observe('05-editar-produto','Salvar produto demo',hold=2)
 tap('Salvar produto demo');observe('06-produto-editado','R$ 14,90',hold=3)
 tap('Adicionar produto');edit_field(0,'Combo%sDemo');edit_field(1,'18.00');observe('07-cadastrar-produto','Salvar produto demo',hold=2)
 tap('Salvar produto demo');observe('08-produto-cadastrado','Combo Demo',hold=3)
 tap('Painel');tap('Ficar online');observe('09-disponibilidade-online','Online no catálogo local',hold=2)
 tap('Localização simulada');tap('Publicar posição simulada');time.sleep(4)
 observe('10-posicao-no-mapa','Posição simulada publicada apenas no catálogo local.',hold=3)
 tap('Painel');tap('Intenções recebidas (0)');tap('Simular cliente')
 observe('11-intencao-recebida','Pendente (demo)',hold=3)
 tap('Aceitar simulação');observe('12-intencao-aceita','Aceita (demo)',hold=2)
 tap('Concluir atendimento');observe('13-atendimento-concluido','Atendimento simulado concluído',hold=3)
 tap('Painel');tap('Encerrar disponibilidade');observe('14-disponibilidade-encerrada','Offline no catálogo local',hold=5)

finally:
 if touches=='null':adb('shell','settings','delete','system','show_touches')
 else:adb('shell','settings','put','system','show_touches',touches)
 if pid:adb('shell','kill','-2',pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 adb('pull',remote,str(raw));(out/'VIDEO_TRACE.json').write_text(json.dumps({'capture':'Actual Android screenrecord, no fabricated UI','vendorJourney':'local registration, products, availability, position and incoming intents only; no remote writes','interaction':'Gradual typing, visible injected touches and reading pauses; real emulator UI, not a filmed human actor','credentialsRecorded':False,'trace':trace},ensure_ascii=False,indent=2)+'\n')
subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
print('Video:',video)
