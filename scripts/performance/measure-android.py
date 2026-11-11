#!/usr/bin/env python3
"""Local process-cold starts. Does not erase app data or contact a backend.
Only accepts the isolated performance package; never acts on the installed client app.
"""
import argparse, json, os, re, statistics, subprocess, time
from pathlib import Path
p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--output', required=True)
p.add_argument('--runs', type=int, default=7)
a = p.parse_args()
assert 3 <= a.runs <= 30
adb = os.environ.get('ADB', str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
package = 'org.example.project.performance'
def run(*args):
    return subprocess.check_output([adb, '-s', a.serial, *args], text=True, timeout=40)
info = {key: run('shell', 'getprop', key).strip() for key in ['ro.build.version.sdk', 'ro.product.model', 'ro.product.cpu.abi']}
info['meminfo_device'] = run('shell', 'cat', '/proc/meminfo')
starts = []
for i in range(a.runs):
    run('shell', 'am', 'force-stop', package)
    run('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    time.sleep(1)
    result = run('shell', 'am', 'start', '-W', '-n', package + '/org.example.project.MainActivity')
    assert 'Status: ok' in result, result
    launch_state = re.search(r'^LaunchState: (.+)', result, re.M)
    assert launch_state and launch_state[1] == 'COLD', result
    times = {key: int(value) for key, value in re.findall(r'^(ThisTime|TotalTime|WaitTime): (\d+)', result, re.M)}
    assert times.get('TotalTime', 0) > 0, result
    time.sleep(4)  # Same idle window before/after, includes the old artificial splash delay.
    starts.append({'run': i + 1, **times, 'am_start_output': result,
                   'launch_state': launch_state[1] if launch_state else None,
                   'memory': run('shell', 'dumpsys', 'meminfo', package),
                   'frames': run('shell', 'dumpsys', 'gfxinfo', package)})
values = sorted(x['TotalTime'] for x in starts)
output = {'conditions': info, 'method': 'am start -W; force-stop (not storage-cold); idle onboarding only',
          'runs': starts, 'summary_ms': {'median': statistics.median(values), 'min': min(values),
           'max': max(values), 'p95_nearest_rank': values[min(len(values)-1, int(len(values)*0.95))]}}
Path(a.output).write_text(json.dumps(output, indent=2))
print(json.dumps(output['summary_ms']))
