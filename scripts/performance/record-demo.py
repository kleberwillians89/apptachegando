#!/usr/bin/env python3
"""Record the existing consumer session on an emulator. No signup, password change or business-data writes.
Requires an already authenticated performance app. The final UI action logs that session out.
Credentials must be entered before running this script; no credentials are accepted here.
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

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', required=True)
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'This demonstration targets an emulator only.'
adb_path = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
pkg = 'org.example.project.performance'
folder = Path(args.output).resolve()
folder.mkdir(parents=True, exist_ok=True)
video = folder / 'TaChegando-demonstracao-android.mp4'
raw = folder / 'TaChegando-demonstracao-original-screenrecord.mp4'
assert not video.exists() and not raw.exists(), 'Preserve the existing recording; choose a new output directory.'
assert shutil.which('ffmpeg'), 'Install/configure ffmpeg before recording.'
remote = '/sdcard/tachegando-demo-20261103.mp4'

def adb(*command):
    result = subprocess.run([adb_path, '-s', args.serial, *command], capture_output=True, timeout=40)
    if result.returncode:
        raise RuntimeError('ADB operation failed: ' + ' '.join(command[:3]))
    return result.stdout

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/tachegando-demo-window.xml')
    return list(ET.fromstring(adb('shell', 'cat', '/sdcard/tachegando-demo-window.xml')).iter('node'))

def tap(label):
    matches = [n for n in nodes() if n.get('text') == label or n.get('content-desc') == label]
    assert matches, 'UI target absent: ' + label
    bounds = list(map(int, re.findall(r'\d+', matches[0].get('bounds'))))
    adb('shell', 'input', 'tap', str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
    time.sleep(1)

trace = []
started = None

def observe(name, expected, hold=3):
    ui = nodes()
    for n in ui:
        text = n.get('text', '')
        assert '@' not in text and 'access_token' not in text and 'refresh_token' not in text, 'Sensitive text detected; stop recording.'
        if n.get('class') == 'android.widget.EditText':
            assert text in ('', 'Email', 'Senha', 'Buscar profissional ou produto...'), 'Filled form detected; stop recording.'
    texts = [n.get('text') or n.get('content-desc') for n in ui if n.get('text') or n.get('content-desc')]
    assert expected in texts, 'Expected screen absent: ' + name
    trace.append({'screen': name, 'elapsedSeconds': round(time.monotonic() - started, 2), 'texts': texts})
    (folder / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    print('Verified:', name, flush=True)
    time.sleep(hold)

# Return to the home before recording; no credentials are recorded.
current = [n.get('text') for n in nodes()]
if 'Voltar' in [n.get('content-desc') for n in nodes()]:
    tap('Voltar')
tap('Mapa')
assert 'Profissionais perto' in [n.get('text') for n in nodes()], 'Authenticate consumerA before recording.'
existing = subprocess.run([adb_path, '-s', args.serial, 'shell', 'pidof', 'screenrecord'], capture_output=True).stdout.strip()
assert not existing, 'Another recording is active; leave it untouched.'
record = subprocess.Popen([adb_path, '-s', args.serial, 'shell', 'screenrecord', '--size', '720x1616', '--bit-rate', '4000000', '--time-limit', '180', remote], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
started = time.monotonic()
pid = ''
try:
    time.sleep(2)
    pid = adb('shell', 'pidof', 'screenrecord').decode().strip()
    assert re.fullmatch(r'\d+', pid), 'Recorder PID unavailable.'
    observe('01-home-autenticada', 'Profissionais perto')
    tap('Esconder lista'); observe('02-lista-recolhida', 'Tá Chegando')
    tap('Buscar'); observe('03-categorias', 'Comida')
    tap('Comida'); observe('04-catalogo-comida-vazio', '0 vendedores nesta página')
    tap('Voltar'); tap('Hortifruti'); observe('05-catalogo-hortifruti-vazio', '0 vendedores nesta página')
    tap('Voltar'); tap('Avisos'); observe('06-avisos', 'Avisos')
    tap('Perfil'); observe('07-perfil', 'Meu Perfil')
    tap('Favoritos'); observe('08-favoritos-vazios', 'Nenhum vendedor favorito ainda.')
    tap('Voltar'); tap('Mapa'); observe('09-retorno-home', 'Tá Chegando')
    tap('Perfil'); observe('10-perfil-antes-logout', 'Sair da Conta', hold=1)
    tap('Sair da Conta'); time.sleep(3)
    observe('11-login-apos-logout', 'Encontre vendedores próximos a você', hold=4)
finally:
    if pid:
        adb('shell', 'kill', '-2', pid)
    try:
        record.communicate(timeout=20)
    except subprocess.TimeoutExpired:
        record.terminate()
        record.communicate(timeout=10)
    adb('pull', remote, str(raw))
    (folder / 'navigation-trace.json').write_text(json.dumps({'applicationId': pkg, 'recordingMethod': 'Android screenrecord; actual UI, no fabricated screens', 'credentialEntryRecorded': False, 'trace': trace}, ensure_ascii=False, indent=2) + '\n')
# Preserve the native variable-frame-rate recording and export a broadly playable MP4.
subprocess.run(['ffmpeg', '-v', 'error', '-i', str(raw), '-map', '0:v:0', '-an', '-vf', 'fps=30', '-c:v', 'libx264', '-preset', 'fast', '-crf', '20', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(video)], check=True)
print('Video:', video)
