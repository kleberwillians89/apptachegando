#!/usr/bin/env python3
"""Keep SQL/Kotlin projection conformance vectors aligned. No database/network access."""
import argparse
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--write',action='store_true')
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
fixtures=json.loads((ROOT/'scripts/push/contract-fixtures.json').read_text())
k=['package org.example.project.core.domain.route','import kotlin.test.*','import org.example.project.core.domain.models.RouteWaypoint','import org.example.project.core.utils.Location','class PushProjectionParityTest {']
s=['-- Generated from scripts/push/contract-fixtures.json; same vectors tested by Kotlin.','begin;',"create function pg_temp.parity_assert(ok boolean,label text) returns void language plpgsql as $$begin if ok is distinct from true then raise exception 'Projection parity: %',label;end if;raise notice 'RC1 PARITY PASS: %',label;end$$;"]
for i,case in enumerate(fixtures):
    points=[dict(latitude=lat,longitude=lng,orderIndex=j) for j,(lat,lng) in enumerate(case['points'])]
    lat,lng=case['point'];amb=str(case['ambiguous']).lower()
    constructors=','.join(f"RouteWaypoint({float(p['latitude'])}, {float(p['longitude'])}, {p['orderIndex']})" for p in points)
    k += [f' @Test fun case{i}() {{',f'  val p = RouteProgress(listOf({constructors})).project(Location({float(lat)}, {float(lng)}))!!',f'  assertEquals({amb}, p.ambiguous, "{case["name"]}")']
    conditions=[f'ambiguous={amb}']
    for field,property_name,column in [('along','alongMeters','along_m'),('cross','crossTrackMeters','cross_m')]:
        if field in case:
            k += [f'  assertEquals({float(case[field])}, p.{property_name}, 0.001, "{case["name"]}")']
            conditions += [f"abs({column}-{case[field]})<0.001"]
    k += [' }']
    s += ["select pg_temp.parity_assert((select "+' and '.join(conditions)+" from private.push_projection('"+json.dumps(points,separators=(',',':'))+f"',{lat},{lng})), '{case['name']}');"]
k += ['}'];s += ['rollback;']
outputs={'KotlinProject/composeApp/src/commonTest/kotlin/org/example/project/core/domain/route/PushProjectionParityTest.kt':'\n'.join(k)+'\n','supabase/tests/push_projection_parity.sql':'\n'.join(s)+'\n'}
for relative,content in outputs.items():
    target=ROOT/relative
    if args.write: target.write_text(content)
    else: assert target.read_text()==content, 'Projection fixtures drifted: '+relative
print('PASS: shared projection fixtures match SQL and Kotlin')
