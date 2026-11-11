#!/usr/bin/env python3
"""Record the new local client/buyer view over automatic-route attendance. Emulator only; no remote writes.
Requires the homologation performance APK installed. Never clears app data or existing routes.
"""
import argparse,json,os,re,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True);p.add_argument('--output',required=True)
a=p.parse_args();assert a.serial.startswith('emulator-')
adb_path=os.environ.get('ADB',str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
assert not (out/'UI_TRACE.json').exists(),'Use a new output folder; preserve prior evidence.'
def adb(*args):
 r=subprocess.run([adb_path,'-s',a.serial,*args],capture_output=True,timeout=40)
 if r.returncode:raise RuntimeError('ADB failed: '+' '.join(args[:3]))
 return r.stdout
def nodes():
 adb('shell','uiautomator','dump','/sdcard/tch-auto-route-window.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/tch-auto-route-window.xml')).iter('node'))
def texts(ns):return [n.get('text') or n.get('content-desc') for n in ns if n.get('text') or n.get('content-desc')]
def tap(label,scroll=False):
 deadline=time.monotonic()+25;attempt=0
 while True:
  ns=nodes();found=[n for n in ns if n.get('text')==label or n.get('content-desc')==label]
  if found:
   b=list(map(int,re.findall(r'\d+',found[0].get('bounds'))))
   if b[2]>b[0] and b[3]>b[1]:break
  assert time.monotonic()<deadline,'Target missing: '+label
  if scroll and attempt>=1:adb('shell','input','swipe','540','1780','540','1100','350')
  attempt+=1;time.sleep(.4)
 x=str((b[0]+b[2])//2);y=str((b[1]+b[3])//2)
 adb('shell','input','swipe',x,y,x,y,'110');time.sleep(.6)
trace=[];started=time.monotonic()
def observe(name,expected,hold=0,scroll=False):
 deadline=time.monotonic()+25;attempt=0
 while True:
  ns=nodes();ts=texts(ns)
  assert not any('@' in t or 'access_token=' in t or 'refresh_token=' in t or 'R$' in t or 'Quantidade' in t for t in ts),'Sensitive text or checkout.'
  # Never accept per-customer manual acceptance in the operational panel.
  assert not any(t in ('Aceitar','Aceitar pedido','Confirmar venda') for t in ts)
  if any(expected in t for t in ts):break
  assert time.monotonic()<deadline,'Expected state missing: '+name+' / '+expected
  if scroll and attempt>=1:adb('shell','input','swipe','540','1780','540','1100','350')
  attempt+=1;time.sleep(.4)
 (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
 trace.append({'case':name,'expected':expected,'elapsedSeconds':round(time.monotonic()-started,2),'texts':ts,'passed':True})
 print('Verified:',name,flush=True);time.sleep(hold)
def open_app():
 adb('shell','am','force-stop','org.example.project.performance')
 adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(2)
 tap('Explorar demonstração');tap('Atendimento automático por rota')
def menu(action):tap('Simulação');tap(action)
def end_journey():tap('Encerrar jornada');tap('Confirmar encerramento')
def consumer_send(option=None):
 if option:
  # Previous status/cancel controls may have scrolled the modal body down.
  adb('shell','input','swipe','540','800','540','1650','350');time.sleep(.4)
  tap(option)
 tap('Confirmar ponto simulado',scroll=True);tap('Demonstrar interesse local',scroll=True)
# Prepare only this ephemeral synthetic session before the buyer-focused capture.
open_app()
if 'Salvar rota de exemplo' in texts(nodes()):tap('Salvar rota de exemplo')
tap('Selecionar rota salva');tap('Iniciar jornada simulada')
assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip()
raw=out/'TaChegando-nova-tela-cliente-comprador-original.mp4'
video=out/'TaChegando-nova-tela-cliente-comprador.mp4'
assert not raw.exists() and not video.exists()
touches=adb('shell','settings','get','system','show_touches').decode().strip();assert touches in ('null','0','1')
adb('shell','settings','put','system','show_touches','1')
remote='/sdcard/tch-new-client-route-20261103.mp4'
record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','240',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();record_pid='';completed=False
try:
 time.sleep(1);record_pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',record_pid)
 observe('01-mapa-da-jornada','Google Map',hold=3)
 tap('Visão consumidor');observe('02-nova-visao-cliente','Consumidor · visão simulada',hold=3)
 consumer_send();observe('03-interesse-na-lista','Interesse na lista do vendedor',hold=4,scroll=True)
 protocol=next(t for t in texts(nodes()) if t.startswith('Protocolo local:'))
 consumer_send('Fora do percurso (simulado)');observe('04-ponto-fora-alcance','Vendedor fora de alcance do ponto',hold=3,scroll=True)
 assert protocol in texts(nodes()),'Changing the meeting point must preserve the protocol.'
 consumer_send('Esquina à frente (simulado)');observe('05-novo-ponto-mesmo-protocolo','Interesse na lista do vendedor',hold=3,scroll=True)
 assert protocol in texts(nodes())
 tap('Cancelar interesse local',scroll=True);observe('06-interesse-cancelado','Solicitação cancelada',hold=4,scroll=True)
 consumer_send('Esquina à frente (simulado)');observe('07-nova-consulta-local','Interesse na lista do vendedor',hold=3,scroll=True)
 new_protocol=next(t for t in texts(nodes()) if t.startswith('Protocolo local:'));assert new_protocol!=protocol
 tap('Voltar à jornada');observe('08-fila-atualizada-sem-aceite','1 interesse à frente',hold=2)
 tap('Pausar jornada');tap('Visão consumidor');observe('09-cliente-ve-pausa','Jornada pausada; aguardando retomada',hold=4,scroll=True)
 tap('Voltar à jornada');tap('Retomar jornada');observe('10-retomada-preserva-interesse','1 interesse à frente',hold=3)
 end_journey();tap('Visão consumidor');observe('11-cliente-ve-encerramento','Jornada encerrada',hold=6,scroll=True)
 assert time.monotonic()-started<225
 # A real compositor update anchors the final state after the reading pause.
 adb('shell','input','swipe','210','450','210','450','110');time.sleep(2)
 completed=True
finally:
 if touches=='null':adb('shell','settings','delete','system','show_touches')
 else:adb('shell','settings','put','system','show_touches',touches)
 if record_pid:adb('shell','kill','-2',record_pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 adb('pull',remote,str(raw))
 (out/'UI_TRACE.json').write_text(json.dumps({'completed':completed,'role':'client/buyer','capture':'Continuous actual emulator screenrecord; automated visible touches and reading pauses; not a filmed human actor','data':'Explicitly synthetic route/meeting points/GPS/connection, no remote writes','sameSession':'Client status and seller queue share the same local state in this capture; the separate vendor video uses its own synthetic session','tests':trace},ensure_ascii=False,indent=2)+'\n')
assert completed
subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
print('Video:',video,flush=True)
