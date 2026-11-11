#!/usr/bin/env python3
"""Record a complete single-session product/catalogue-to-route presentation. Emulator only; no remote writes.
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
   if b[2]>b[0] and b[3]>b[1] and (not scroll or b[3]<2320):break
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
def enter_name(value):
 fs=[n for n in nodes() if n.get('class')=='android.widget.EditText'];assert len(fs)==1
 b=list(map(int,re.findall(r'\d+',fs[0].get('bounds'))));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
 for character in value:
  adb('shell','input','text','%s' if character==' ' else character);time.sleep(.09)
 adb('shell','input','keyevent','KEYCODE_BACK')
clips=[];record=None;record_pid='';clip_start=0;clip_name='';completed=False
def start_clip(name):
 global record,record_pid,clip_start,clip_name
 assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip()
 clip_name=name;clip_start=time.monotonic()
 record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','240','/sdcard/tch-story-'+name+'.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 time.sleep(1);record_pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',record_pid)
def stop_clip():
 global record,record_pid
 if not record:return
 elapsed=time.monotonic()-clip_start
 adb('shell','kill','-2',record_pid)
 try:record.communicate(timeout=20)
 except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
 raw=out/(clip_name+'-original.mp4');encoded=out/(clip_name+'.mp4')
 adb('pull','/sdcard/tch-story-'+clip_name+'.mp4',str(raw));record=None;record_pid=''
 assert elapsed<225,'Chapter exceeded safe recording length; use a new folder and retake.'
 subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(encoded)],check=True)
 clips.append(encoded.name)
 print('Chapter completed:',clip_name,flush=True)
touches=adb('shell','settings','get','system','show_touches').decode().strip();assert touches in ('null','0','1')
adb('shell','settings','put','system','show_touches','1')
started=time.monotonic()
try:
 start_clip('01-cadastro-e-catalogo')
 adb('shell','am','force-stop','org.example.project.performance');adb('shell','am','start','-W','-n','org.example.project.performance/org.example.project.MainActivity');time.sleep(3)
 observe('01-abertura','Explorar demonstração',hold=2)
 tap('Explorar demonstração');tap('Cadastrar na demonstração');tap('Vendedor');enter_name('Jose Demo')
 observe('02-cadastro-vendedor','Categoria: Pães',hold=3)
 tap('Concluir cadastro demo');observe('03-cadastro-concluido','Cadastro de demonstração concluído',hold=3)
 tap('Ir para meu painel');tap('Administração');tap('Minhas categorias');tap('Adicionar categoria')
 tap('Categoria: Pães');tap('Lanches naturais');observe('04-cadastro-categoria-sem-preco','Categoria: Lanches naturais',hold=3)
 tap('Salvar categoria demo');observe('05-catalogo-salvo-localmente','Categoria salva somente nesta demonstração.',hold=3)
 observe('06-nova-categoria-com-imagem','Lanches naturais',hold=3)
 tap('Painel');tap('Jornada')
 if 'Salvar rota de exemplo' in texts(nodes()):tap('Salvar rota de exemplo')
 tap('Selecionar rota salva');observe('07-rota-habitual-selecionada','Rota salva no aparelho',hold=3)
 stop_clip()
 start_clip('02-comprador-encontra-e-manifesta-interesse')
 tap('Iniciar jornada simulada');observe('08-vendedor-trabalhando','Trabalhando · jornada simulada',hold=3)
 menu('Explorar como cliente');time.sleep(5);observe('09-mapa-do-comprador','[DEMO] Pão do José',hold=3)
 tap('Pães');tap('[DEMO] Pão do José');observe('10-catalogo-criado-aparece-para-cliente','Lanches naturais',hold=3,scroll=True)
 tap('Lanches naturais',scroll=True);observe('11-detalhe-da-categoria','Consultar disponibilidade',hold=3)
 tap('Consultar disponibilidade');observe('12-mesmo-vendedor-mesma-categoria','categoria Lanches naturais',hold=3,scroll=True)
 consumer_send();observe('13-interesse-na-lista-da-jornada','Interesse na lista do vendedor',hold=4,scroll=True)
 protocol=next(t for t in texts(nodes()) if t.startswith('Protocolo local:'))
 tap('Voltar à jornada');observe('14-fila-do-vendedor-atualizada','1 interesse à frente',hold=4)
 stop_clip()
 start_clip('03-jornada-automatica-e-encerramento')
 menu('Simular interesses');observe('15-onze-interesses-sem-aceite','11 interesses à frente',hold=3)
 menu('Avançar posição simulada');observe('16-fila-acompanha-o-percurso','7 interesses à frente',hold=3)
 observe('17-registros-ultrapassados-preservados','4 pontos ultrapassados',hold=2)
 tap('Visão consumidor');observe('18-cliente-acompanha-ponto-ultrapassado','Ponto já ultrapassado nesta rota',hold=3,scroll=True)
 assert protocol in texts(nodes())
 consumer_send('Esquina à frente (simulado)');observe('19-ajuste-ponto-mantem-protocolo','Interesse na lista do vendedor',hold=3,scroll=True)
 assert protocol in texts(nodes())
 tap('Voltar à jornada');observe('20-fila-recalculada-sem-duplicar','8 interesses à frente',hold=3)
 tap('Pausar jornada');observe('21-pausa-da-jornada','Pausado · jornada simulada',hold=2)
 tap('Visão consumidor');observe('22-cliente-ve-a-pausa','Jornada pausada; aguardando retomada',hold=3,scroll=True)
 tap('Voltar à jornada');tap('Retomar jornada');observe('23-retomada-com-mesmos-interesses','8 interesses à frente',hold=2)
 end_journey();observe('24-vendedor-encerra-jornada','Jornada encerrada · simulação',hold=3)
 tap('Visão consumidor');observe('25-comprador-ve-encerramento','Jornada encerrada',hold=4,scroll=True)
 tap('Voltar à jornada');observe('26-encerramento-com-registros-preservados','11 registros preservados',hold=5)
 adb('shell','input','swipe','60','180','60','180','110');time.sleep(2)
 stop_clip();completed=True
finally:
 if record:stop_clip()
 if touches=='null':adb('shell','settings','delete','system','show_touches')
 else:adb('shell','settings','put','system','show_touches',touches)
 (out/'UI_TRACE.json').write_text(json.dumps({'completed':completed,'singleAppSession':True,'capture':'Three consecutive actual emulator recordings from one session; chapters concatenated; no fabricated screens','businessData':'Synthetic, labelled; no remote writes, prices, checkout or stock guarantee','tests':trace,'clips':clips},ensure_ascii=False,indent=2)+'\n')
assert completed and len(clips)==3
listing=out/'CONCAT.txt';listing.write_text(''.join("file '"+name+"'\n" for name in clips))
metadata=[';FFMETADATA1','title=TáChegando — do catálogo à jornada']
start_ms=0
for name,title in zip(clips,['Vendedor: cadastro e catálogo','Comprador: mapa, categoria e interesse','Jornada: organização automática e encerramento']):
 duration=float(subprocess.run(['ffprobe','-v','error','-show_entries','format=duration','-of','csv=p=0',str(out/name)],capture_output=True,text=True,check=True).stdout.strip())
 end_ms=start_ms+round(duration*1000)
 metadata.extend(['[CHAPTER]','TIMEBASE=1/1000','START='+str(start_ms),'END='+str(end_ms),'title='+title]);start_ms=end_ms
chapter_file=out/'CHAPTERS.txt';chapter_file.write_text('\n'.join(metadata)+'\n')
final_video=out/'TaChegando-apresentacao-completa.mp4'
subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','1','-i',str(listing),'-f','ffmetadata','-i',str(chapter_file),'-map','0:v:0','-map_metadata','1','-map_chapters','1','-c','copy','-movflags','+faststart',str(final_video)],check=True)
print('Complete presentation:',final_video,flush=True)
