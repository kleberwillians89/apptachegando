#!/usr/bin/env python3
"""ZIP accounting only. Uncompressed content is a proxy, not installed/download size."""
import argparse, hashlib, json, zipfile
from pathlib import Path
p = argparse.ArgumentParser(); p.add_argument('artifacts', nargs='+'); a = p.parse_args()
result = []
for name in a.artifacts:
    path = Path(name)
    with zipfile.ZipFile(path) as z:
        files = z.infolist()
        dex = [x for x in files if x.filename.endswith('.dex')]
        result.append({'artifact': path.name, 'bytes': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'uncompressed_zip_bytes': sum(x.file_size for x in files),
            'dex_count': len(dex), 'dex_uncompressed_bytes': sum(x.file_size for x in dex),
            'baseline_profile_files': [x.filename for x in files if 'baseline.prof' in x.filename],
            'largest_entries': [{'path':x.filename, 'bytes':x.file_size} for x in sorted(files, key=lambda x:x.file_size, reverse=True)[:10]]})
print(json.dumps(result, indent=2))
