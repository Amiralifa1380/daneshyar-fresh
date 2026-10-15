#!/usr/bin/env bash
# Run ONCE only for a NEW signing identity. An existing APK needs its ORIGINAL key.
set -euo pipefail
umask 077
if [[ -f daneshyar-signing.p12 ]]; then
  echo 'Refusing to overwrite the existing signing key.' >&2
  exit 1
fi
keytool -genkeypair -storetype PKCS12 -keystore daneshyar-signing.p12 \
  -alias daneshyar -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=Amirhossein Falahati, OU=Daneshyar'
echo 'Save this private file outside your repository. Never commit it. Remember its password.'
