#!/usr/bin/env python3
"""Run each model entry point in isolation and retain auditable outputs, never invented results."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / 'src/main/java/tech/nmfin/algoquant/demo/model'
MAIN = re.compile(r'public\s+static\s+void\s+main\s*\(')


def inventory():
    result = []
    for source in sorted(SOURCES.rglob('*.java')):
        text = source.read_text()
        name = 'tech.nmfin.algoquant.demo.model.' + '.'.join(source.relative_to(SOURCES).with_suffix('').parts)
        result.append({'class': name, 'name': source.stem, 'source': source.relative_to(ROOT).as_posix(),
                       'runnable': bool(MAIN.search(text)),
                       'mode': 'synthetic --quick' if '/technical/' in source.as_posix() else 'original main defaults'})
    return result


def redact(text):
    # Redact legacy SQL literals if a downstream library happens to echo its config.
    for source in SOURCES.rglob('*.java'):
        for match in re.finditer(r'new SQLParam\(\s*((?:"(?:\\.|[^"\\])*"\s*,?\s*)+)\)', source.read_text(), re.S):
            for value in re.findall(r'"([^"\n]+)"', match.group(1)):
                text = text.replace(value, '[database configuration redacted]')
    text = re.sub(r'(?i)(password\s*[=:]\s*)\S+', r'\1[redacted]', text)
    return text


def classify(code, timed_out, text):
    if timed_out:
        return 'timeout', 'The original main exceeded the configured execution limit; partial outputs are not a completed result.'
    patterns = [
        (r'External database access is not configured', 'blocked-data', 'Requires an explicitly configured database; legacy MySQL access was blocked before authentication.'),
        (r'(?i)(license|licence).*(expired|invalid|not found|not valid|error)', 'blocked-license', 'The external license was not accepted for this calculation.'),
        (r'FileNotFoundException|NoSuchFileException|(?i:does not exist|no such file|cannot find.*file)', 'blocked-data', 'A required local dataset or path is unavailable; see the exception and configured path.'),
        (r'(?i)(HTTP response code|HTTP error|too many requests|download failed|unknownhostexception|sslhandshake|connectexception|sockettimeoutexception)', 'blocked-provider', 'The requested historical data/service could not be retrieved.'),
        (r'HeadlessException|AWTError', 'blocked-display', 'The demonstration requires a functioning desktop display.'),
        (r'OutOfMemoryError', 'resource-limit', 'The original calculation exceeded the per-demo memory allocation.'),
    ]
    for pattern, status, explanation in patterns:
        if re.search(pattern, text):
            return status, explanation
    if code == 0 and 'DEMO_RUNNER_MAIN_RETURNED' in text:
        if re.search(r'(?:^|\\n)(?:.*\\bERROR\\b.*|[\\w.$]+Exception:.*|\\s+at [\\w.$]+\\([^\\n]*\\.java:\\d+\\))', text):
            return 'completed-with-errors', 'The main returned but logged a calculation error or stack trace; this is not a verified numerical result.'
        return 'completed', 'The original main returned successfully without a detected calculation error. Read its assumptions before interpreting it.'
    return 'failed', 'The demo raised an exception or did not return normally. This is not a verified numerical result.'


def execute(item, output, seconds, heap):
    destination = output / item['name']
    destination.mkdir(parents=True, exist_ok=True)
    work = destination / 'work'
    work.mkdir(exist_ok=True)
    for name in ['test-data', 'config']:
        if (ROOT / name).exists(): (work / name).symlink_to(ROOT / name, target_is_directory=True)
    (work / 'log').mkdir(exist_ok=True)
    (work / 'log/demo').mkdir(exist_ok=True)
    cp = os.pathsep.join([str(ROOT/'target/model-runner'), str(ROOT/'target/classes'), str(ROOT/'target/lib/*')])
    command = ['xvfb-run', '-a', 'java', '-Xmx' + heap, '-Djava.awt.headless=false',
               '-Dsun.net.client.defaultConnectTimeout=20000', '-Dsun.net.client.defaultReadTimeout=30000',
               '-Dnm.license=' + os.environ.get('NM_LICENSE_FILE', str(ROOT/'license/nm.lic')),
               '-cp', cp, 'ModelDemoRunner', item['class']]
    if '/technical/' in item['source']: command.append('--quick')
    start = time.monotonic()
    timed_out = False
    with (destination/'stdout.log').open('w') as stdout, (destination/'stderr.log').open('w') as stderr:
        proc = subprocess.Popen(command, cwd=work, stdout=stdout, stderr=stderr, start_new_session=True)
        try: code = proc.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(proc.pid, signal.SIGTERM)
            try: code = proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(proc.pid, signal.SIGKILL)
                code = proc.wait()
    for file in [destination/'stdout.log', destination/'stderr.log']:
        file.write_text(redact(file.read_text(errors='replace')))
    text = (destination/'stdout.log').read_text() + '\n' + (destination/'stderr.log').read_text()
    status, explanation = classify(code, timed_out, text)
    # Preserve only run-produced report/chart files, never data caches or external inputs.
    exported = []
    for directory in [work/'charts', work/'log', work/'output', work/'results']:
        if not directory.exists(): continue
        for path in sorted(directory.rglob('*')):
            if path.is_file() and path.suffix.lower() in ['.csv', '.zip', '.png', '.pdf', '.xlsx', '.txt']:
                # Yahoo cache ZIPs are inputs, not outputs for redistribution.
                if any(part.lower() in ['yahoo', 'holidays'] for part in path.relative_to(work).parts): continue
                if path.suffix.lower() == '.zip' and not re.search(r'(?i)(report|performance|pnl|corr|execution|trade)', path.name): continue
                if 'constituent' in path.name.lower(): continue
                if path.stat().st_size > 20*1024*1024: continue
                target = destination/'files'/path.relative_to(work)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
                exported.append({'path':target.relative_to(destination).as_posix(), 'bytes':target.stat().st_size,
                                 'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    shutil.rmtree(work)
    result = dict(item, status=status, explanation=explanation, exit_code=code,
                  seconds=round(time.monotonic()-start,2), limit_seconds=seconds,
                  heap=heap, exports=exported,
                  arguments=['--quick'] if '/technical/' in item['source'] else [])
    (destination/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print('%s: %s (%.2fs)' % (item['name'], status, result['seconds']), flush=True)
    return result


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='target/model-results')
    parser.add_argument('--shard', type=int, default=0)
    parser.add_argument('--shards', type=int, default=1)
    parser.add_argument('--seconds', type=int, default=180)
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--heap', default='1200m')
    parser.add_argument('--only', help='Run one class by its simple or qualified name')
    args=parser.parse_args()
    output=(ROOT/args.output).resolve();output.mkdir(parents=True,exist_ok=True)
    items=inventory();(output/'inventory.json').write_text(json.dumps(items,indent=2)+'\n')
    runs=[x for x in items if x['runnable']][args.shard::args.shards]
    if args.only:
        runs=[x for x in runs if args.only in [x['name'],x['class']]]
        if not runs: parser.error('No matching runnable demo')
    with ThreadPoolExecutor(args.workers) as pool:
        results=list(pool.map(lambda item:execute(item, output, args.seconds, args.heap),runs))
    (output/('shard-%d.json'%args.shard)).write_text(json.dumps(results,indent=2)+'\n')
