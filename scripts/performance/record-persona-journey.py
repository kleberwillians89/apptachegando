#!/usr/bin/env python3
"""Real Android recording of the local-only seller or consumer journey. No Auth/Supabase requests.
Requires Pixel 9 1080x2424 emulator at the normal onboarding screen and ffmpeg.
"""
import argparse,json,os,re,shutil,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',required=True);p.add_argument('--role',choices=['vendor','consumer'],required=True);a=p.parse_args()
assert a.serial.startswith('emulator-') and shutil.which('ffmpeg')
adb_path=os.environ.get('ADB',str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
stem='TaChegando-vendedor-coxinha' if a.role=='vendor' else 'TaChegando-consumidor'
raw=out/(stem+'-original.mp4');video=out/(stem+'.mp4')
assert not raw.exists() and not video.exists(),'Preserve existing videos; choose a new folder.'
def adb(*args):
 r=subprocess.run([adb_path,'-s',a.serial,*args],capture_output=True,timeout=40)
 if r.returncode:raise RuntimeError('ADB failed: '+' '.join(args[:3]))
 return r.stdout
def nodes():
 adb('shell','uiautomator','dump','/sdcard/tch-presentation-window.xml');return list(ET.fromstring(adb('shell','cat','/sdcard/tch-presentation-window.xml')).iter('node'))
def tap(label):
 deadline=time.monotonic()+30
 while True:
  found=[n for n in nodes() if n.get('text')==label or n.get('content-desc')==label]
  if found:break
  assert time.monotonic()<deadline,'Target missing: '+label
  time.sleep(.5)
 b=list(map(int,re.findall(r'\d+',found[0].get('bounds'))));x=str((b[0]+b[2])//2);y=str((b[1]+b[3])//2);adb('shell','input','swipe',x,y,x,y,'110');time.sleep(1.0)
def type_text(value):
 for char in value.replace('%s',' '):
  adb('shell','input','text','%s' if char==' ' else char);time.sleep(.09)
def enter(value):
 fields=[n for n in nodes() if n.get('class')=='android.widget.EditText'];assert len(fields)==1,'Only demo name/search fields allowed.'
 b=list(map(int,re.findall(r'\d+',fields[0].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));type_text(value);adb('shell','input','keyevent','KEYCODE_BACK')
trace=[];started=0
def observe(name,expected,hold=2):
 deadline=time.monotonic()+30
 while True:
  ns=nodes();texts=[n.get('text') or n.get('content-desc') for n in ns if n.get('text') or n.get('content-desc')]
  assert not any('@' in t or 'access_token' in t or 'refresh_token' in t for t in texts),'Sensitive text; stop.'
  assert not any('R$' in t or 'Total simulado' in t or 'Quantidade' in t for t in texts),'Prices or checkout present in demo.'
  if expected in texts:break
  assert time.monotonic()<deadline,'Expected state missing: '+name
  time.sleep(.5)
 trace.append({'screen':name,'elapsedSeconds':round(time.monotonic()-started,2),'texts':texts});(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'));print('Verified:',name,flush=True);time.sleep(hold)
# Reset only ephemeral demonstration navigation; never clear Android app data.
if 'Sair da demo' in [n.get('text') for n in nodes()]:tap('Sair da demo')
assert 'Explorar demonstração' in [n.get('text') for n in nodes()]
assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip()
touches=adb('shell','settings','get','system','show_touches').decode().strip()
assert touches in ('null','0','1')
adb('shell','settings','put','system','show_touches','1')
remote='/sdcard/tch-persona-'+a.role+'-20261103.mp4'
record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','240',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();pid='';completed=False
try:
 time.sleep(1);pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',pid)
 adb('shell','am','force-stop','org.example.project.performance');adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(3)
 observe('01-abertura','Explorar demonstração',hold=2)
 tap('Explorar demonstração');tap('Cadastrar na demonstração')
 if a.role=='vendor':
  tap('Vendedor');enter('Carlos%sda%sCoxinha');tap('Categoria: Pães');tap('Salgados fritos')
  observe('02-cadastro-vendedor-coxinha','Categoria: Salgados fritos',hold=3)
  tap('Concluir cadastro demo');observe('03-cadastro-local-concluido','Cadastro de demonstração concluído',hold=3)
  tap('Ir para meu painel');observe('04-painel-vendedor','Olá, Carlos da Coxinha',hold=2)
  tap('Minhas categorias');observe('05-categorias-sem-precos','Editar Salgados fritos',hold=3)
  tap('Painel');tap('Ficar online');tap('Localização simulada');tap('Publicar posição simulada');time.sleep(3)
  observe('06-localizacao-no-mapa','Posição simulada publicada apenas no catálogo local.',hold=4)
  tap('Painel');tap('Intenções recebidas (0)');tap('Simular cliente')
  observe('07-consulta-recebida','DEMO-INTERESSE-0001',hold=3)
  tap('Simular contato');observe('08-contato-em-andamento','Contato em andamento (demo)',hold=3)
  tap('Concluir atendimento');observe('09-atendimento-local-concluido','Atendimento simulado concluído',hold=3)
  tap('Painel');tap('Ver como consumidor');time.sleep(5);tap('Perto de mim');tap('Salgados fritos')
  observe('10-vendedor-no-catalogo','[DEMO] Salgados da Praça',hold=3)
  tap('[DEMO] Salgados da Praça');observe('11-perfil-ilustrado','Categorias que costuma oferecer',hold=3)
  tap('Meu painel');tap('Encerrar disponibilidade');observe('12-disponibilidade-encerrada','Offline no catálogo local',hold=5)
 else:
  tap('Consumidor');enter('Ana%sDemo');observe('02-cadastro-consumidor','Concluir cadastro demo',hold=2)
  tap('Concluir cadastro demo');observe('03-cadastro-local-concluido','Cadastro de demonstração concluído',hold=3)
  tap('Explorar vendedores');time.sleep(5);tap('Perto de mim');tap('Esconder lista')
  observe('04-mapa-com-cinco-vendedores','Google Map',hold=4)
  tap('Ver profissionais perto');enter('salgados');observe('05-busca-por-vendedor','[DEMO] Salgados da Praça',hold=3)
  tap('Salgados fritos');observe('06-busca-por-categoria','Salgados fritos · Salgados assados',hold=2)
  tap('Esconder lista');observe('07-marcador-de-salgados','Google Map',hold=3)
  # Marker center observed in the real Google Maps frame, Pixel 9 1080x2424.
  adb('shell','input','swipe','775','1600','775','1600','110');time.sleep(1)
  observe('08-perfil-do-vendedor','Categorias que costuma oferecer',hold=3)
  tap('Salgados fritos');observe('09-categoria-com-imagem','Consultar disponibilidade',hold=3)
  tap('Consultar disponibilidade');observe('10-interesse-sem-pedido','DEMO-INTERESSE-0001',hold=4)
  tap('Consultar protocolo');observe('11-historico-da-sessao','DEMO-INTERESSE-0001',hold=3)
  tap('DEMO-INTERESSE-0001');observe('12-consulta-do-protocolo','Interesse registrado nesta sessão',hold=2)
  tap('Voltar à home');time.sleep(5);tap('Frutas');tap('[DEMO] Frutas do Luiz');observe('13-explorar-outro-vendedor','Temperos',hold=3)
  tap('Voltar');time.sleep(5);tap('Perto de mim');tap('Esconder lista');observe('14-retorno-ao-mapa','Google Map',hold=5)
 assert time.monotonic()-started < 225,'Journey too long for a safe continuous recording; shorten and retake.'
 # Force a real final compositor update after the reading pause; no synthetic frames.
 adb('shell','input','swipe','60','180','60','180','110');time.sleep(2)
 completed=True
finally:
 if touches=='null':adb('shell','settings','delete','system','show_touches')
 else:adb('shell','settings','put','system','show_touches',touches)
 if pid:adb('shell','kill','-2',pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 adb('pull',remote,str(raw))
 (out/'VIDEO_TRACE.json').write_text(json.dumps({'role':a.role,'completed':completed,'capture':'Continuous real Android screenrecord, no fabricated screens','interaction':'Automated visible touches, gradual typing, reading pauses; not a filmed human actor','remoteWrites':False,'pricesVisible':False,'session':'Independent in-memory session per video; no consumer/seller messages travel between recordings','trace':trace},ensure_ascii=False,indent=2)+'\n')
assert completed
subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
print('Video:',video)
