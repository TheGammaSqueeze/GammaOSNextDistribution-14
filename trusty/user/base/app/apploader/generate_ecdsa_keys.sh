#!/bin/bash
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# This scripts generates an ECDSA private/public key pair
# for apploader signatures.

set -e

if [ "$#" -ne 3 ]; then
    echo -e "Usage: $0 <p256|p384> <private key file> <public key file>"
    exit 1
fi

case $1 in
    p256)
        EC_CURVE_NAME=prime256v1
        ;;
    p384)
        EC_CURVE_NAME=secp384r1
        ;;
    *)
        echo "Invalid key type"
        exit 1
        ;;
esac

PRIVATE_KEY_FILE=$2
PUBLIC_KEY_FILE=$3

openssl ecparam \
    -genkey \
    -name $EC_CURVE_NAME \
    -noout \
    -outform DER \
    -out "$PRIVATE_KEY_FILE"

openssl ec \
    -inform DER \
    -in "$PRIVATE_KEY_FILE" \
    -pubout \
    -outform DER \
    -out "$PUBLIC_KEY_FILE"
