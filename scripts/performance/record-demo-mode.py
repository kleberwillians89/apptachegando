#!/usr/bin/env python3
"""Record the labelled local demonstration on the actual Android emulator.
Requires the performance app at onboarding. Never logs in or touches Supabase fixtures.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial', required=True)
p.add_argument('--output', required=True)
a = p.parse_args()
assert a.serial.startswith('emulator-'), 'Emulator only.'
assert shutil.which('ffmpeg'), 'ffmpeg is required.'
adb_path = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
out = Path(a.output).resolve(); out.mkdir(parents=True, exist_ok=True)
raw = out / 'TaChegando-demo-original-screenrecord.mp4'
video = out / 'TaChegando-demonstracao-modo-local.mp4'
assert not raw.exists() and not video.exists(), 'Preserve previous videos; select a new output directory.'

def adb(*args):
    r = subprocess.run([adb_path, '-s', a.serial, *args], capture_output=True, timeout=40)
    if r.returncode: raise RuntimeError('ADB failed: ' + ' '.join(args[:3]))
    return r.stdout

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/tch-demo-mode-window.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/tch-demo-mode-window.xml')).iter('node'))

def tap(label):
    found = [n for n in nodes() if n.get('text') == label or n.get('content-desc') == label]
    assert found, 'Target absent: ' + label
    b = list(map(int, re.findall(r'\d+', found[0].get('bounds'))))
    adb('shell', 'input', 'tap', str((b[0]+b[2])//2), str((b[1]+b[3])//2)); time.sleep(1)

trace = []
started = 0

def observe(name, expected, hold=3):
    ui = nodes()
    texts = [n.get('text') or n.get('content-desc') for n in ui if n.get('text') or n.get('content-desc')]
    assert not any('@' in t or 'access_token' in t or 'refresh_token' in t for t in texts), 'Sensitive text detected.'
    assert expected in texts, 'Screen absent: ' + name
    trace.append({'screen': name, 'elapsedSeconds': round(time.monotonic()-started, 2), 'texts': texts})
    (out / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    print('Verified:', name, flush=True); time.sleep(hold)

assert 'Explorar demonstração' in [n.get('text') for n in nodes()], 'Open onboarding without entering credentials.'
assert not subprocess.run([adb_path, '-s', a.serial, 'shell', 'pidof', 'screenrecord'], capture_output=True).stdout.strip(), 'Another recording is active.'
remote = '/sdcard/tch-demo-mode-20261103.mp4'
record = subprocess.Popen([adb_path, '-s', a.serial, 'shell', 'screenrecord', '--size', '720x1616', '--bit-rate', '4000000', '--time-limit', '180', remote], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
pid = ''; started = time.monotonic()
try:
    time.sleep(2); pid = adb('shell', 'pidof', 'screenrecord').decode().strip(); assert re.fullmatch(r'\d+', pid)
    observe('01-entry', 'Explorar demonstração', hold=2)
    tap('Explorar demonstração'); time.sleep(3); observe('02-local-catalogue', 'MODO DEMONSTRAÇÃO')
    tap('Esconder lista'); observe('03-google-map-real-tiles', 'Google Map', hold=5)
    # Coordinates verified on the 1080x2424 Pixel 9 capture: northern Flowers marker.
    size = adb('shell', 'wm', 'size').decode(); assert '1080x2424' in size, 'Review marker coordinates for this emulator size.'
    adb('shell', 'input', 'tap', '437', '1273'); time.sleep(2)
    observe('04-map-marker-to-flowers-catalogue', 'R$ 25,00')
    tap('Simular intenção'); observe('05-local-intent-simulated', 'Simulação registrada')
    tap('Consultar intenção'); observe('06-local-intent-consulted', 'Simulação registrada')
    tap('Voltar'); tap('[DEMO] Lanches da Praça'); observe('07-lanches-catalogue', 'R$ 12,50')
    tap('Voltar'); tap('[DEMO] Feira de Frutas'); observe('08-frutas-catalogue', 'R$ 6,00')
    tap('Voltar'); tap('Esconder lista'); observe('09-return-to-google-map', 'Google Map', hold=4)
    tap('Sair da demo'); observe('10-exit-without-real-order', 'Explorar demonstração', hold=3)
finally:
    if pid: adb('shell', 'kill', '-2', pid)
    try: record.communicate(timeout=20)
    except subprocess.TimeoutExpired: record.terminate(); record.communicate(timeout=10)
    adb('pull', remote, str(raw))
    (out/'VIDEO_TRACE.json').write_text(json.dumps({'method':'Actual Android screenrecord; fictional business data labelled on-screen; Google tiles real', 'credentialsRecorded':False, 'realPurchaseIntent':False, 'trace':trace}, ensure_ascii=False, indent=2)+'\n')
subprocess.run(['ffmpeg', '-v', 'error', '-i', str(raw), '-map', '0:v:0', '-an', '-vf', 'fps=30', '-c:v', 'libx264', '-preset', 'fast', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(video)], check=True)
print('Video:', video)
