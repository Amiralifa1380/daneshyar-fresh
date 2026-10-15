"""Regression: reject an older APK even when the source version is correct."""
import importlib.util, tempfile, subprocess, os, json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('version',root/'verify-version.py'); mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
mod.require_version('1.2.3',6)
for version,code in [('1.1',2),('1.2.1',4),('1.2.3',4)]:
    try: mod.require_version(version,code)
    except ValueError: pass
    else: raise AssertionError('stale version accepted')
with tempfile.TemporaryDirectory() as tmp:
    tmp=Path(tmp)
    (tmp/'app').mkdir(); (tmp/'app/build.gradle').write_text((root/'app/build.gradle').read_text())
    (tmp/'verify-version.py').write_text((root/'verify-version.py').read_text())
    (tmp/'test.apk').write_bytes(b'test fixture, not a real APK')
    analyzer=tmp/'apkanalyzer';analyzer.write_text('''#!/usr/bin/env python3
import os,sys
print({'version-name':os.environ['MOCK_VERSION'], 'version-code':os.environ['MOCK_CODE'], 'application-id':'ir.daneshyar.offline'}[sys.argv[2]])
''');analyzer.chmod(0o755)
    env=dict(os.environ,PATH=str(tmp)+os.pathsep+os.environ['PATH'],MOCK_VERSION='1.1',MOCK_CODE='2')
    args=['python3',str(tmp/'verify-version.py'),'apk',str(tmp/'test.apk')]
    result=subprocess.run(args,env=env,capture_output=True,text=True)
    assert result.returncode==1 and 'STALE BUILD' in result.stderr
    assert not (tmp/'dist/build-info.json').exists()
    env.update(MOCK_VERSION='1.2.3',MOCK_CODE='6')
    subprocess.run(args,env=env,check=True)
    info=json.loads((tmp/'dist/build-info.json').read_text()); assert info['versionName']=='1.2.3' and len(info['sha256'])==64
print('PASS: version mismatches rejected; mocked APK gate and provenance verified (not an APK build)')
