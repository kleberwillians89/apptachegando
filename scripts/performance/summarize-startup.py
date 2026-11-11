#!/usr/bin/env python3
import argparse, json, re, statistics
from pathlib import Path
p = argparse.ArgumentParser(); p.add_argument('input'); p.add_argument('--output', required=True); a = p.parse_args()
x = json.loads(Path(a.input).read_text())
rows = []
for r in x['runs']:
    pss = re.search(r'TOTAL PSS:\s*(\d+)', r['memory'])
    frames = re.search(r'Total frames rendered:\s*(\d+)', r['frames'])
    jank = re.search(r'Janky frames:\s*(\d+)', r['frames'])
    rows.append({'run':r['run'], 'ttid_ms':r['TotalTime'], 'launch_state':r.get('launch_state'), 'wait_ms':r['WaitTime'],
                 'pss_kib': int(pss[1]) if pss else None,
                 'frames': int(frames[1]) if frames else None,
                 'janky_frames': int(jank[1]) if jank else None})
valid = [r for r in rows if r['frames'] is not None and r['frames'] > 0]
output = {'method':x['method'], 'device':{k:v for k,v in x['conditions'].items() if k != 'meminfo_device'},
    'device_memtotal_kib':int(re.search(r'MemTotal:\s*(\d+)',x['conditions']['meminfo_device'])[1]),
    'startup_ms':x['summary_ms'], 'samples':rows,
    'median_pss_kib':statistics.median([r['pss_kib'] for r in rows if r['pss_kib'] is not None]),
    'total_frames':sum(r['frames'] for r in valid), 'janky_frames':sum(r['janky_frames'] for r in valid)}
Path(a.output).write_text(json.dumps(output, indent=2) + '\n')
print(json.dumps(output))
