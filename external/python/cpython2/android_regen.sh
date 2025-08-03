#!/bin/bash -ex
#
# Copyright 2017 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Regenerate host configuration files for the current host

cd `dirname ${BASH_SOURCE[0]}`

ANDROID_BUILD_TOP=$(cd ../../..; pwd)

if [ $(uname) == 'Darwin' ]; then
  DIR=darwin
else
  if [ $(uname -m) == 'aarch64' ]; then
      DIR=linux_arm64
  else
      DIR=linux_x86_64
  fi
fi

export CLANG_VERSION=$(cd $ANDROID_BUILD_TOP; build/soong/scripts/get_clang_version.py)

if [ $DIR == "linux_x86_64" ]; then
  export CC="$ANDROID_BUILD_TOP/prebuilts/clang/host/linux-x86/$CLANG_VERSION/bin/clang"
  export CFLAGS="--sysroot=$ANDROID_BUILD_TOP/prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8/sysroot"
  export LDFLAGS="--sysroot=$ANDROID_BUILD_TOP/prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8/sysroot -B$ANDROID_BUILD_TOP/prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8/lib/gcc/x86_64-linux/4.8.3 -L$ANDROID_BUILD_TOP/prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8/lib/gcc/x86_64-linux/4.8.3 -L$ANDROID_BUILD_TOP/prebuilts/gcc/linux-x86/host/x86_64-linux-glibc2.17-4.8/x86_64-linux/lib64"
elif [ $DIR == "linux_arm64" ]; then
  #export CC="$ANDROID_BUILD_TOP/prebuilts/clang/host/linux-x86/$CLANG_VERSION/bin/clang"
  export CC=clang
  export CFLAGS="--sysroot=$ANDROID_BUILD_TOP/prebuilts/build-tools/sysroots/aarch64-linux-musl"
  export LDFLAGS="--sysroot=$ANDROID_BUILD_TOP/prebuilts/build-tools/sysroots/aarch64-linux-musl -rtlib=compiler-rt -fuse-ld=lld --unwindlib=none"
fi

mkdir -p $DIR/pyconfig $DIR/libffi
cd $DIR

# Generate pyconfig.h
rm -rf tmp
mkdir tmp
cd tmp
../../configure
cp pyconfig.h ../pyconfig/
cd ..
rm -rf tmp

# Generate ffi.h / fficonfig.h
rm -rf tmp
mkdir tmp
cd tmp
../../Modules/_ctypes/libffi/configure
cp fficonfig.h include/ffi.h ../libffi/
cd ..
rm -rf tmp
