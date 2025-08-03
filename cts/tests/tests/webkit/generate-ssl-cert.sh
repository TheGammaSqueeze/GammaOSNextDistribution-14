#!/bin/bash

# This script generates two self-signed test certificates to be used by
# CtsTestServer in the CtsWebkitTestCases APK. This script is not invoked as
# part of the build; the certificates and keys are checked in. The certificates
# are valid for 10 years, and this script can be used to regenerate them before
# they expire.

for name in trusted untrusted; do
  tempkey="${name}key.tmp"
  openssl req -x509 -newkey rsa:4096 -sha256 -days 3650 -nodes \
    -keyout "${tempkey}" -out "res/raw/${name}cert.crt" -subj "/CN=CtsWebkitTestCases-${name}" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" &&
  openssl pkcs8 -topk8 -outform DER -in "${tempkey}" -out "res/raw/${name}key.der" -nocrypt &&
  rm "${tempkey}"
done
