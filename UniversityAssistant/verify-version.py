#!/usr/bin/env python3
"""Fail closed on stale project/APK; record APK provenance without signing secrets."""
import hashlib, json, os, re, subprocess, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent
EXPECTED_NAME, EXPECTED_CODE = '1.2.2', '5'

def require_version(name, code):
    if (name, str(code)) != (EXPECTED_NAME, EXPECTED_CODE):
        raise ValueError(f'STALE BUILD: expected {EXPECTED_NAME} ({EXPECTED_CODE}), got {name} ({code}). Use the UniversityAssistant source from Daneshyar-Fresh-Repository.zip.')

def main():
    source = (ROOT/'app/build.gradle').read_text()
    require_version(re.search(r"versionName\s+['\"]([^'\"]+)", source)[1], re.search(r'versionCode\s+(\d+)', source)[1])
    if sys.argv[1] == 'apk':
        apk = Path(sys.argv[2])
        def manifest(field):
            return subprocess.check_output(['apkanalyzer', 'manifest', field, str(apk)], text=True).strip()
        name, code = manifest('version-name'), manifest('version-code')
        require_version(name, code)
        info = dict(versionName=name, versionCode=int(code), applicationId=manifest('application-id'), sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), runId=os.getenv('GITHUB_RUN_ID'), runNumber=os.getenv('GITHUB_RUN_NUMBER'), commit=os.getenv('GITHUB_SHA'), apk=str(apk))
        if info['applicationId'] != 'ir.daneshyar.offline':
            raise ValueError('Unexpected APK application ID')
        (ROOT/'dist').mkdir(exist_ok=True)
        (ROOT/'dist/build-info.json').write_text(json.dumps(info, indent=2)+'\n')
    print(f'VERSION VERIFIED: {EXPECTED_NAME} ({EXPECTED_CODE})')

if __name__ == '__main__':
    try: main()
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
