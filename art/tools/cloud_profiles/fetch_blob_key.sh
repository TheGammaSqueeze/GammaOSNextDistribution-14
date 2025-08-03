#!/bin/bash
# Running this script requires "gcert" login.
#
# This script is used to fetch data from blob store based on the blob key.
# For example, `bash fetch_blob_key.sh abcdefg output.profile`
BLOB_KEY=$1
OUTPUT=$2

blob_id=$(echo "$BLOB_KEY"  | base64 --decode | gqui from rawproto:- proto blobstore.BlobRef | grep "BlobID :" | awk -F' ' '{print $3}')
blob_id=$(echo "$blob_id" | sed -e 's/^"//' -e 's/"$//')
/google/bin/releases/blobstore2/tools/bs2/bs2 read "$blob_id" > "$OUTPUT"
