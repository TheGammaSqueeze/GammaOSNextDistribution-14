#!/bin/sh

# Creates an apex stub in a subdirectory named after the input package name.

# Exit early if any subcommands fail.
set -e

usage() {
  echo "Usage $0 [options] apex_package_name"
  echo "  -v"
  echo "       Whether this is a vendor APEX"
  echo "  -k existing_apex_keyname"
  echo "       Use existing key instead of creating a new key"
  exit -1
}

is_vendor=0

while getopts "vk:" opt; do
  case $opt in
    v)
      is_vendor=1
      ;;
    k)
      APEX_KEY=${OPTARG}
      ;;
    *)
      usage
  esac
done

shift $((OPTIND-1))
APEX_NAME=$1
if [ -z ${APEX_NAME} ]
then
  echo "Missing apex package name"
  usage
fi

YEAR=$(date +%Y)
mkdir -p ${APEX_NAME}
cd ${APEX_NAME}

cat > Android.bp <<EOF
// Copyright (C) ${YEAR} The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package {
    default_applicable_licenses: ["Android-Apache-2.0"],
}

EOF

if [ -z ${APEX_KEY} ]
then
APEX_KEY=${APEX_NAME}

openssl genrsa -out ${APEX_KEY}.pem 4096
avbtool extract_public_key --key ${APEX_KEY}.pem --output ${APEX_KEY}.avbpubkey

cat > csr.conf <<EOF
[req]
default_bits = 4096
distinguished_name = dn
prompt             = no

[dn]
C="US"
ST="California"
L="Mountain View"
O="Android"
OU="Android"
emailAddress="android@android.com"
CN="${APEX_KEY}"
EOF

openssl req -x509 -config csr.conf -newkey rsa:4096 -nodes -days 999999 -keyout key.pem -out ${APEX_KEY}.x509.pem
rm csr.conf
openssl pkcs8 -topk8 -inform PEM -outform DER -in key.pem -out ${APEX_KEY}.pk8 -nocrypt
rm key.pem

cat >> Android.bp <<EOF
apex_key {
    name: "${APEX_KEY}.key",
    public_key: "${APEX_KEY}.avbpubkey",
    private_key: "${APEX_KEY}.pem",
}

android_app_certificate {
    name: "${APEX_KEY}.certificate",
    certificate: "${APEX_KEY}",
}

EOF

fi

if ((is_vendor == 0)); then

cat >> Android.bp <<EOF
apex {
    name: "${APEX_NAME}",
    manifest: "manifest.json",
    file_contexts: ":apex.test-file_contexts",  // Default, please edit, see go/android-apex-howto
    key: "${APEX_KEY}.key",
    certificate: ":${APEX_KEY}.certificate",
    updatable: false,
}
EOF

cat > manifest.json << EOF
{
    "name": "${APEX_NAME}",

    // Placeholder module version to be replaced during build.
    // Do not change!
    "version": 0
}
EOF

else

cat >> Android.bp <<EOF
apex {
    name: "${APEX_NAME}",
    manifest: "manifest.json",
    file_contexts: "file_contexts",
    key: "${APEX_KEY}.key",
    certificate: ":${APEX_KEY}.certificate",
    updatable: false,
    vendor: true,
}
EOF

cat > manifest.json << EOF
{
    "name": "${APEX_NAME}",
    "version": 1
}
EOF

cat > file_contexts << EOF
(/.*)?                                                          u:object_r:vendor_file:s0
/etc(/.*)?                                                      u:object_r:vendor_configs_file:s0
# Add more ...
# /bin/hw/foo                                                   u:object_r:hal_foo_exec:s0
EOF

fi
