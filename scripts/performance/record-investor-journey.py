#!/usr/bin/env python3
"""Real Android recording of the local-only investor journey. No Auth/Supabase requests.
Requires Pixel 9 1080x2424 emulator at the normal onboarding screen and ffmpeg.
"""
import argparse,json,os,re,shutil,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',required=True);p.add_argument('--closing-only',action='store_true',help='Append a real emulator closing clip to an existing recording after the observed ~300s recorder limit');a=p.parse_args()
assert a.serial.startswith('emulator-') and shutil.which('ffmpeg')
adb_path=os.environ.get('ADB',str(Path.home()/'Library/Android/sdk/platform-tools/adb'))
out=Path(a.output).resolve();out.mkdir(parents=True,exist_ok=True)
raw=out/'TaChegando-investidores-final-original.mp4';video=out/'TaChegando-investidores-final.mp4'
assert a.closing_only or (not raw.exists() and not video.exists()),'Preserve existing videos; choose a new folder.'
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
def capture_closing():
 # A measured ~300 s screenrecord limit truncated the initial ending. Both clips
 # below are genuine emulator recordings; no screenshots are inserted into video.
 closing_raw=out/'TaChegando-investidores-encerramento-original.mp4'
 closing_video=out/'TaChegando-investidores-encerramento.mp4'
 main_video=out/'TaChegando-investidores-principal.mp4'
 assert not closing_raw.exists() and not closing_video.exists() and not main_video.exists()
 ns=nodes()
 if 'Sair da demo' in [n.get('text') for n in ns]:tap('Sair da demo')
 assert 'Explorar demonstração' in [n.get('text') for n in nodes()]
 old_touches=adb('shell','settings','get','system','show_touches').decode().strip()
 assert old_touches in ('null','0','1')
 adb('shell','settings','put','system','show_touches','1')
 remote_closing='/sdcard/tch-investor-closing-20261103.mp4'
 rec=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','45',remote_closing],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 close_pid=''
 try:
  time.sleep(1);close_pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',close_pid)
  time.sleep(2);tap('Explorar demonstração')
  ts=[n.get('text') or n.get('content-desc') for n in nodes()]
  assert 'Seu bairro, mais perto' in ts and not any(t and ('R$' in t or '@' in t) for t in ts)
  (out/'closing-proposta-final.png').write_bytes(adb('exec-out','screencap','-p'))
  time.sleep(12)
 finally:
  if old_touches=='null':adb('shell','settings','delete','system','show_touches')
  else:adb('shell','settings','put','system','show_touches',old_touches)
  if close_pid:adb('shell','kill','-2',close_pid)
  rec.communicate(timeout=20);adb('pull',remote_closing,str(closing_raw))
 subprocess.run(['ffmpeg','-v','error','-i',str(closing_raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(closing_video)],check=True)
 video.rename(main_video)
 # Fixed filenames, no shell interpolation or untrusted ffconcat paths.
 listing=out/'VIDEO_CONCAT.txt'
 listing.write_text("file 'TaChegando-investidores-principal.mp4'\nfile 'TaChegando-investidores-encerramento.mp4'\n")
 subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','1','-i',str(listing),'-c','copy','-movflags','+faststart',str(video)],check=True)
 (out/'CLOSING_TRACE.json').write_text(json.dumps({'capture':'Second real Android screenrecord, concatenated without fabricated screens','reason':'Initial recorder stopped at approximately 300 seconds before the final product proposition','verified':'Seu bairro, mais perto','texts':ts,'touchesRestored':True,'mainClip':main_video.name,'closingClip':closing_video.name},ensure_ascii=False,indent=2)+'\n')
 print('Final real closing clip appended:',video,flush=True)

if a.closing_only:
 assert raw.exists() and video.exists()
 capture_closing()
 raise SystemExit(0)

assert 'Explorar demonstração' in [n.get('text') for n in nodes()]
assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip(),'Another recording is active.'
touches=adb('shell','settings','get','system','show_touches').decode().strip()
assert touches in ('null','0','1')
adb('shell','settings','put','system','show_touches','1')
remote='/sdcard/tch-investor-final-20261103.mp4'
record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','0',remote],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
started=time.monotonic();pid=''
try:
 time.sleep(1);pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',pid)
 adb('shell','am','force-stop','org.example.project.performance');adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(3)
 observe('01-abertura','Explorar demonstração',hold=2)
 tap('Explorar demonstração');observe('02-proposta','Seu bairro, mais perto',hold=2)
 tap('Cadastrar na demonstração');tap('Vendedor');enter('Jose%sDemo')
 tap('Categoria: Pães');tap('Pães');observe('03-cadastro-simplificado-local','Categoria: Pães',hold=2)
 tap('Concluir cadastro demo');observe('04-cadastro-demo-concluido','Cadastro de demonstração concluído',hold=3)
 tap('Ir para meu painel');observe('05-vendedor-offline-local','Offline no catálogo local',hold=2)
 tap('Ficar online');tap('Localização simulada');tap('Publicar posição simulada');time.sleep(3)
 observe('06-posicao-sintetica-local','Posição simulada publicada apenas no catálogo local.',hold=2)
 tap('Painel');tap('Ver como consumidor');time.sleep(5)
 observe('07-catalogo-local','[DEMO] Pão do José',hold=2)
 tap('Perto de mim');tap('Esconder lista');observe('08-google-maps-cinco-vendedores','Google Map',hold=4)
 tap('Ver profissionais perto');tap('Pães');observe('09-busca-paes','[DEMO] Pão do José',hold=3)
 tap('Esconder lista');observe('10-marcador-filtrado','Google Map',hold=2)
 # Pixel 9 1080x2424: coordinate observed from the real map screenshot.
 adb('shell','input','swipe','485','1330','485','1330','110');time.sleep(1)
 observe('11-catalogo-pao-do-jose','Categorias que costuma oferecer',hold=4)
 tap('Pães');observe('12-categoria-paes','Consultar disponibilidade',hold=3)
 tap('Consultar disponibilidade');observe('13-interesse-local','DEMO-INTERESSE-0001',hold=4)
 tap('Consultar protocolo');observe('14-protocolo-na-sessao','DEMO-INTERESSE-0001',hold=3)
 tap('Voltar à home');time.sleep(5);tap('Frutas');tap('[DEMO] Frutas do Luiz')
 observe('15-frutas-do-luiz','Temperos',hold=4)
 tap('Temperos');observe('16-categoria-temperos','Consultar disponibilidade',hold=3)
 tap('Voltar');tap('Voltar');time.sleep(6);tap('Salgados fritos');tap('[DEMO] Salgados da Praça')
 observe('17-salgados-da-praca','Salgados assados',hold=4)
 tap('Voltar');time.sleep(4);tap('Rotas demo');observe('18-rota-explicitamente-simulada','0 pontos sintéticos',hold=3)
 tap('Gravar percurso simulado');time.sleep(7);tap('Encerrar percurso simulado');observe('19-percurso-sintetico-gravado','6 pontos sintéticos',hold=3)
 tap('Salvar rota demo');observe('20-rota-salva-na-sessao','Reutilizar Rota da manhã (demo)',hold=3)
 tap('Reutilizar Rota da manhã (demo)');observe('21-reuso-local','Percurso recuperado da sessão. Reuso entre dias no servidor ainda não homologado.',hold=4)
 tap('Home');time.sleep(7);observe('22-retorno-a-home','[DEMO] Pão do José',hold=3)
 tap('Sair da demo');observe('23-retorno-ao-modo-real-sem-login','Explorar demonstração',hold=2)
 tap('Explorar demonstração');observe('24-proposta-final','Seu bairro, mais perto',hold=7)
finally:
 if touches=='null':adb('shell','settings','delete','system','show_touches')
 else:adb('shell','settings','put','system','show_touches',touches)
 if pid:adb('shell','kill','-2',pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 adb('pull',remote,str(raw));(out/'VIDEO_TRACE.json').write_text(json.dumps({'capture':'Actual Android screenrecord, no fabricated UI','journey':'local vendor signup/category, local online/synthetic position, real Google Maps, vendor/category search and marker, availability interest/protocol, synthetic recording and session route reuse, demo exit and product proposition','interaction':'Injected visible touches, gradual typing and reading pauses; not a filmed human actor','commercialTransactions':False,'remoteWrites':False,'credentialsRecorded':False,'trace':trace},ensure_ascii=False,indent=2)+'\n')
subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
capture_closing()
print('Video:',video)
