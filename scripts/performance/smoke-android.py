#!/usr/bin/env python3
"""Destructive to the *isolated performance process* only; emulator radios restored in finally.
Does not clear app data. Validates entry UI only, not authenticated/backend workflows.
"""
import argparse, json, os, subprocess, time
from pathlib import Path
p = argparse.ArgumentParser(); p.add_argument('--serial', required=True); p.add_argument('--output', required=True); a = p.parse_args()
assert a.serial.startswith('emulator-'), 'Emulator only; do not toggle a physical user device.'
adb = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
pkg = 'org.example.project.performance'
def call(*args):
    r = subprocess.run([adb, '-s', a.serial, *args], capture_output=True, text=True, timeout=40)
    return {'exit_code':r.returncode, 'output':r.stdout.strip(), 'error':r.stderr.strip()}
def checked(*args):
    r = call(*args)
    assert r['exit_code'] == 0, r
    return r['output']
def launch():
    return checked('shell', 'am', 'start', '-W', '-n', pkg + '/org.example.project.MainActivity')
initial = checked('shell', 'cmd', 'connectivity', 'airplane-mode').strip()
assert initial in ('enabled', 'disabled'), initial
results = {'scope':'entry UI only; no Supabase/Maps credentials, no authenticated flows', 'initial_airplane_mode':initial}
try:
    checked('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    checked('shell', 'am', 'force-stop', pkg)
    results['offline_start'] = launch()
    time.sleep(5)
    results['offline_process_alive'] = bool(checked('shell', 'pidof', pkg))
    results['trim_running_critical'] = call('shell', 'am', 'send-trim-memory', pkg, 'RUNNING_CRITICAL')
    checked('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(3)  # Wait until ActivityManager actually marks the process as background.
    results['trim_background_complete'] = call('shell', 'am', 'send-trim-memory', pkg, 'COMPLETE')
    results['resume_after_trim'] = launch()
    time.sleep(3)
    pid = checked('shell', 'pidof', pkg)
    results['process_alive_after_trim'] = bool(pid)
    logs = checked('logcat', '-d', '--pid=' + pid, '-v', 'brief')
    results['fatal_log_lines'] = sum('FATAL EXCEPTION' in x or 'ANR in ' in x for x in logs.splitlines())
    results['activity_exit_info'] = checked('shell', 'dumpsys', 'activity', 'exit-info', pkg)
finally:
    checked('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable' if initial == 'enabled' else 'disable')
    results['restored_airplane_mode'] = checked('shell', 'cmd', 'connectivity', 'airplane-mode')
    Path(a.output).write_text(json.dumps(results, indent=2) + '\n')
print(json.dumps({k:v for k,v in results.items() if k != 'activity_exit_info'}))
