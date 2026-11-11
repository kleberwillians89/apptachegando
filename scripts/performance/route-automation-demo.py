#!/usr/bin/env python3
"""Validate or record the local automatic-route demo. Emulator only; no remote writes.
Requires the homologation performance APK installed. Never clears app data or existing routes.
"""
import argparse,json,os,re,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as ET
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True);p.add_argument('--output',required=True);p.add_argument('--record',action='store_true')
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
record=None;record_pid='';touches=None;completed=False
raw=out/'TaChegando-atendimento-automatico-original.mp4';video=out/'TaChegando-atendimento-automatico.mp4'
try:
 if a.record:
  assert not subprocess.run([adb_path,'-s',a.serial,'shell','pidof','screenrecord'],capture_output=True).stdout.strip()
  touches=adb('shell','settings','get','system','show_touches').decode().strip();assert touches in ('null','0','1')
  adb('shell','settings','put','system','show_touches','1')
  record=subprocess.Popen([adb_path,'-s',a.serial,'shell','screenrecord','--size','720x1616','--bit-rate','5000000','--time-limit','240','/sdcard/tch-route-automatic-20261103.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  time.sleep(1);record_pid=adb('shell','pidof','screenrecord').decode().strip();assert re.fullmatch(r'\d+',record_pid)
 open_app()
 if not a.record:
  if 'Salvar rota de exemplo' in texts(nodes()):tap('Salvar rota de exemplo')
  observe('01-rota-local-salva','Selecionar rota salva')
  open_app() # Actual process restart: persistence must survive, interests must not.
  observe('02-rota-preservada-apos-reiniciar','6 pontos fictícios · armazenamento privado local')
  assert 'Salvar rota de exemplo' not in texts(nodes())
 else:observe('01-rota-habitual-previamente-salva','6 pontos fictícios · armazenamento privado local',hold=2)
 tap('Selecionar rota salva');tap('Iniciar jornada simulada')
 observe('03-jornada-iniciada','Trabalhando · jornada simulada',hold=2 if a.record else 0)
 menu('Simular interesses');observe('04-recebimento-sem-aceite','10 interesses à frente',hold=3 if a.record else 0)
 if not a.record:
  menu('Reenviar mesmos eventos');observe('05-reenvio-idempotente','10 registros preservados')
  tap('Visão consumidor');consumer_send();observe('06-consumidor-na-lista','Interesse na lista do vendedor',scroll=True)
  protocol=next(t for t in texts(nodes()) if t.startswith('Protocolo local:'))
  consumer_send('Fora do percurso (simulado)');observe('06b-mudanca-ponto-sem-duplicar','Vendedor fora de alcance do ponto',scroll=True)
  assert protocol in texts(nodes())
  consumer_send('Esquina à frente (simulado)');observe('06c-ponto-atualizado-mesmo-protocolo','Interesse na lista do vendedor',scroll=True)
  assert protocol in texts(nodes())
  tap('Cancelar interesse local',scroll=True);observe('07-consumidor-cancelado','Solicitação cancelada',scroll=True)
  consumer_send('Fora do percurso (simulado)');observe('08-consumidor-fora-alcance','Vendedor fora de alcance do ponto',scroll=True)
  tap('Cancelar interesse local',scroll=True);tap('Voltar à jornada')
 menu('Avançar posição simulada');observe('09-ordem-acompanha-percurso','7 interesses à frente',hold=3 if a.record else 0)
 observe('10-ultrapassados-preservados','3 pontos ultrapassados')
 if a.record:
  tap('Situações (10)');observe('11-situacao-ponto-ultrapassado','Ponto já ultrapassado nesta rota',hold=2);tap('Fechar')
 else:
  tap('Visão consumidor');consumer_send('Ponto atrás (simulado)');observe('11-consumidor-atras','Ponto já ultrapassado nesta rota',scroll=True)
  tap('Cancelar interesse local',scroll=True);tap('Voltar à jornada')
 tap('Pausar jornada');observe('12-jornada-pausada','Pausado · jornada simulada',hold=2 if a.record else 0)
 tap('Retomar jornada');observe('13-retomada-sem-reiniciar','7 interesses à frente')
 menu('Desconectar simulação');observe('14-perda-conexao','Sem conexão simulada',hold=2 if a.record else 0)
 if not a.record:menu('Simular interesses') # Wire holds another batch; UI must await full reconciliation.
 menu('Reconectar simulação');observe('15-reconexao-sem-duplicar','7 interesses à frente' if a.record else '14 interesses à frente',hold=2 if a.record else 0)
 if not a.record:
  menu('Perder sinal simulado');observe('16-gps-indisponivel','Localização simulada indisponível')
  menu('Recuperar sinal simulado');observe('17-gps-recuperado','14 interesses à frente')
 tap('Visão consumidor');consumer_send();observe('18-interesse-compartilhado-local','Interesse na lista do vendedor',hold=3 if a.record else 0,scroll=True)
 if a.record:
  tap('Cancelar interesse local',scroll=True);observe('19-cancelamento-visivel','Solicitação cancelada',hold=2,scroll=True)
 tap('Voltar à jornada');end_journey();observe('20-jornada-encerrada','Jornada encerrada · simulação',hold=4 if a.record else 0)
 if not a.record:
  tap('Visão consumidor');observe('21-consumidor-ve-encerramento','Jornada encerrada',scroll=True);tap('Voltar à jornada')
  tap('Selecionar rota novamente');observe('22-rota-mantida-e-historico','Histórico de jornadas da sessão (1)')
  tap('Selecionar rota salva');tap('Iniciar jornada simulada');observe('23-nova-jornada-sem-eventos-antigos','0 registros preservados')
  tap('Simulação');tap('450');tap('Simular interesses');observe('24-escala-450-interface','450 interesses à frente')
  menu('Avançar posição simulada');observe('25-450-clientes-acompanham-avanco','332 interesses à frente')
  observe('26-450-sem-descartar','450 registros preservados · 118 pontos ultrapassados')
  end_journey();observe('27-encerramento-preserva-450','450 registros preservados')
  open_app();observe('28-rota-persiste-eventos-nao','Selecionar rota salva')
  tap('Selecionar rota salva');tap('Iniciar jornada simulada');observe('29-reinicio-nao-publica-posicao-antiga','0 registros preservados')
 else:
  assert time.monotonic()-started<225,'Retake a shorter recording to avoid recorder cutoff.'
  adb('shell','input','swipe','60','180','60','180','110');time.sleep(2)
 completed=True
finally:
 if touches is not None:
  if touches=='null':adb('shell','settings','delete','system','show_touches')
  else:adb('shell','settings','put','system','show_touches',touches)
 if record_pid:adb('shell','kill','-2',record_pid)
 if record:
  try:record.communicate(timeout=20)
  except subprocess.TimeoutExpired:record.terminate();record.communicate(timeout=10)
  adb('pull','/sdcard/tch-route-automatic-20261103.mp4',str(raw))
 (out/'UI_TRACE.json').write_text(json.dumps({'completed':completed,'mode':'record' if a.record else 'verification','capture':'Real emulator UI; synthetic route/clients/GPS/events; no remote writes','tests':trace},ensure_ascii=False,indent=2)+'\n')
assert completed
if a.record:
 subprocess.run(['ffmpeg','-v','error','-i',str(raw),'-map','0:v:0','-an','-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(video)],check=True)
 print('Video:',video,flush=True)
