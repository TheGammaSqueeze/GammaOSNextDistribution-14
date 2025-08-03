#!/bin/bash

set -e

sh_path=$0
mkfs=$1
output_image=$2
delta_generator=$3
compression_algo=$4

if [ -z "$compression_algo" ]
then
  compression_algo="lz4hc,9"
fi

fs_root=$(mktemp -d -t erofs-XXXXXXXXXX)

clean_up () {
    ARG=$?
    rm -rf $fs_root
    echo "> clean_up"
    exit $ARG
}
trap clean_up EXIT

if [ ! -z "${delta_generator}" ]; then
  mkdir -p ${fs_root}/dir1/dir2/dir123/nested_dir
  mkdir -p ${fs_root}/etc/
  cp ${sh_path} ${fs_root}/
  truncate -s 1M ${fs_root}/file1
  truncate -s 1M ${fs_root}/dir1/file2
  truncate -s 1M ${fs_root}/dir1/file0
  truncate -s 1M ${fs_root}/dir1/dir2/file0
  truncate -s 1M ${fs_root}/dir1/dir2/file1
  truncate -s 1M ${fs_root}/dir1/dir2/file2
  truncate -s 1M ${fs_root}/dir1/dir2/file4
  touch ${fs_root}/dir1/dir2/dir123/empty
  cp ${delta_generator} ${fs_root}/delta_generator
  truncate -s 1M ${fs_root}/delta_generator
  echo "PAYLOAD_MINOR_VERSION=1234" > ${fs_root}/etc/update_engine.conf
  truncate -s 16M ${fs_root}/dir1/dir2/dir123/chunks_of_zero
fi

${mkfs} -z $compression_algo -T 1656466080 -U BCF5939B-0930-A551-A548-E4BD99E783AC --ignore-mtime ${output_image} ${fs_root}
