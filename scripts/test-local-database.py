#!/usr/bin/env python3
"""Create a disposable PostgreSQL cluster, no TCP or remote database arguments.
Requires PostgreSQL 17+ and PostGIS. Auth shim is NOT a Supabase Auth server.
"""
import os
from pathlib import Path
import subprocess
import tempfile
from concurrent.futures import ThreadPoolExecutor

ROOT = Path(__file__).resolve().parents[1]
PG_BIN = Path(os.environ.get('TCH_PG_BIN', '/opt/homebrew/opt/postgresql@17/bin'))

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, stdout=subprocess.DEVNULL)

with tempfile.TemporaryDirectory(prefix='tch-sql-') as directory:
    folder = Path(directory)
    # macOS Unix socket paths must be short, including pg_ctl's internal socket name.
    data = folder / 'data'
    socket = folder / 'sock'
    socket.mkdir()
    run(PG_BIN / 'initdb', '-D', data, '-U', 'postgres', '--auth=trust', '--no-locale', '--encoding=UTF8')
    started = False
    try:
        run(PG_BIN / 'pg_ctl', '-D', data, '-l', folder / 'postgres.log', '-o', f"-h '' -k {socket} -p 55433 -c wal_level=logical", 'start')
        started = True
        args = [PG_BIN / 'psql', '-X', '-q', '-o', str(folder/'query-results.txt'), '-v', 'ON_ERROR_STOP=1', '-h', socket, '-p', '55433', '-U', 'postgres', '-d', 'postgres']
        for file in [ROOT / 'scripts/sql/bootstrap-postgres.sql', *sorted((ROOT/'supabase/migrations').glob('*.sql')), ROOT/'supabase/seed.sql', *sorted((ROOT/'supabase/tests').glob('*.sql'))]:
            print('Applying local:', file.relative_to(ROOT), flush=True)
            run(*args, '-f', file)
        push_env = dict(os.environ, TCH_TEST_PG_SOCKET=str(socket), TCH_TEST_PG_BIN=str(PG_BIN))
        subprocess.run(['node', str(ROOT/'scripts/push/postgres-integration.mjs')], check=True, env=push_env, cwd=ROOT)
        # Confirmed web account, two independent real transactions finalize one professional identity.
        profile_owner = 'b0000000-0000-4000-8000-000000000009'
        query_args = [PG_BIN / 'psql', '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-h', socket, '-p', '55433', '-U', 'postgres', '-d', 'postgres']
        subprocess.run([str(x) for x in query_args] + ['-c', f"insert into auth.users(id,email,email_confirmed_at,raw_user_meta_data) values('{profile_owner}','profile-race@example.test',now(),'{{\"registration_contract\":\"web-v1\",\"role\":\"vendor\",\"name\":\"Concurrent web\"}}')"], check=True, stdout=subprocess.DEVNULL)
        subprocess.run([str(x) for x in query_args] + ['-c', f"insert into auth.sessions(id,user_id) values('{profile_owner}','{profile_owner}')"], check=True, stdout=subprocess.DEVNULL)
        profile_sql = f"begin;set local role authenticated;select set_config('request.jwt.claim.sub','{profile_owner}',true);select set_config('request.jwt.claims','{{\"session_id\":\"{profile_owner}\"}}',true);select public.complete_my_profile('vendor','Concurrent web','Concurrent bakery','Pães')->>'vendor_id';select pg_sleep(0.1);commit;"
        def complete_concurrently(_):
            output = subprocess.check_output([str(x) for x in query_args], input=profile_sql.encode()).decode().splitlines()
            ids = [line.strip() for line in output if len(line.strip()) == 36 and line.strip() != profile_owner]
            assert len(ids) == 1
            return ids[0]
        with ThreadPoolExecutor(max_workers=2) as pool:
            profile_ids = list(pool.map(complete_concurrently, range(2)))
        assert profile_ids[0] == profile_ids[1], 'concurrent completion duplicated vendor identity'
        profile_count = subprocess.check_output([str(x) for x in query_args] + ['-c', f"select count(*) from public.vendors where user_id='{profile_owner}'"]).decode().strip()
        assert profile_count == '1'
        print('WEB INTEGRATION PASS: two concurrent confirmed profile completions return one vendor ID and one row', flush=True)
        onboarding_sql = f"begin;set local role authenticated;select set_config('request.jwt.claim.sub','{profile_owner}',true);select set_config('request.jwt.claims','{{\"session_id\":\"{profile_owner}\"}}',true);select public.complete_vendor_onboarding('Concurrent web','Concurrent bakery','Pães',array['Pão','Bolo'])->>'product_count';commit;"
        def onboard_concurrently(_):
            return subprocess.check_output([str(x) for x in query_args], input=onboarding_sql.encode()).decode().splitlines()[-1].strip()
        with ThreadPoolExecutor(max_workers=2) as pool:
            counts = list(pool.map(onboard_concurrently, range(2)))
        assert counts == ['2','2'], 'concurrent onboarding duplicated or lost products'
        print('ONBOARDING INTEGRATION PASS: concurrent retries initialize exactly two products once', flush=True)

        # Two independent real PostgreSQL transactions contend for the same consumer.
        # The disposable cluster and synthetic seed IDs are mandatory; no remote arguments.
        concurrent_sql = """begin;
set local role authenticated;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select public.create_purchase_intent('30000000-0000-4000-8000-000000000001',0,0.005);
select pg_sleep(0.1);
commit;"""
        query_args = [PG_BIN / 'psql', '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1', '-h', socket, '-p', '55433', '-U', 'postgres', '-d', 'postgres']
        subprocess.run([str(x) for x in query_args] + ['-c', "insert into public.vendor_routes(vendor_id,name,waypoints) values ('30000000-0000-4000-8000-000000000001','Concurrent test','[{\"latitude\":0,\"longitude\":0,\"orderIndex\":0},{\"latitude\":0,\"longitude\":0.01,\"orderIndex\":1}]')"], check=True, stdout=subprocess.DEVNULL)
        def create_concurrently(_):
            output = subprocess.check_output([str(x) for x in query_args], input=concurrent_sql.encode()).decode().splitlines()
            ids = [line.strip() for line in output if len(line.strip()) == 36 and line.strip() != '10000000-0000-4000-8000-000000000001']
            assert len(ids) == 1, 'missing confirmed synthetic intent'
            return ids[0]
        with ThreadPoolExecutor(max_workers=2) as pool:
            ids = list(pool.map(create_concurrently, range(2)))
        assert ids[0] == ids[1], 'concurrent requests created duplicate IDs'
        count = subprocess.check_output([str(x) for x in query_args] + ['-c', "select count(*) from public.purchase_intents where consumer_id='10000000-0000-4000-8000-000000000001' and vendor_id='30000000-0000-4000-8000-000000000001' and status='pending'"]).decode().strip()
        assert count == '1', 'concurrent requests created duplicate rows'
        print("PASS: concurrent duplicate requests return one persistent ID and one row", flush=True)
        print("PASS: canonical migrations, synthetic seeds and security/lifecycle assertions", flush=True)
    finally:
        if started:
            run(PG_BIN / 'pg_ctl', '-D', data, '-m', 'fast', 'stop')
