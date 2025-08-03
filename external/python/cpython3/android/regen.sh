#!/bin/bash -ex
#
# Copyright 2019 Google Inc. All rights reserved.
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

# TODO: Ensure all required packages are installed.

cd `dirname ${BASH_SOURCE[0]}`/..

SRC_TOP=$(pwd)
LOCAL_TOP=$SRC_TOP/android
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
mkdir -p $LOCAL_TOP/$DIR/pyconfig

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

#
# Generate pyconfig.h
#
mkdir -p $ANDROID_BUILD_TOP/out
PYTHON_BUILD=$ANDROID_BUILD_TOP/out/python
rm -rf $PYTHON_BUILD
cp -rp $SRC_TOP $PYTHON_BUILD
cd $PYTHON_BUILD
./configure

if [ $DIR == "darwin" ]; then
  # preadv and pwritev are not safe on <11, which we still target
  sed -ibak "s%#define HAVE_PREADV 1%/* #undef HAVE_PREADV */%" pyconfig.h
  sed -ibak "s%#define HAVE_PWRITEV 1%/* #undef HAVE_PWRITEV */%" pyconfig.h
  # mkfifoat and mknodat are not safe on <13, which we still target
  sed -ibak "s%#define HAVE_MKNODAT 1%/* #undef HAVE_MKNODAT */%" pyconfig.h
  sed -ibak "s%#define HAVE_MKFIFOAT 1%/* #undef HAVE_MKFIFOAT */%" pyconfig.h

  if [ $(machine) != "x86_64h" ]; then
    echo "This script expects to be run on an X86_64 machine"
    exit 1
  fi

  # Changes to support darwin_arm64
  sed -ibak 's%#define HAVE_FINITE 1%#ifdef __x86_64__\n#define HAVE_FINITE 1\n#endif%' pyconfig.h
  sed -ibak 's%#define HAVE_GAMMA 1%#ifdef __x86_64__\n#define HAVE_GAMMA 1\n#endif%' pyconfig.h
  sed -ibak 's%#define HAVE_GCC_ASM_FOR_X64 1%#ifdef __x86_64__\n#define HAVE_GCC_ASM_FOR_X64 1\n#endif%' pyconfig.h
  sed -ibak 's%#define HAVE_GCC_ASM_FOR_X87 1%#ifdef __x86_64__\n#define HAVE_GCC_ASM_FOR_X87 1\n#endif%' pyconfig.h
  sed -ibak 's%#define SIZEOF_LONG_DOUBLE .*%#ifdef __x86_64__\n#define SIZEOF_LONG_DOUBLE 16\n#else\n#define SIZEOF_LONG_DOUBLE 8\n#endif%' pyconfig.h
fi

if [ $DIR == "linux_x86_64" ]; then
  mkdir -p $LOCAL_TOP/bionic/pyconfig
  cp pyconfig.h $LOCAL_TOP/bionic/pyconfig
  # Changes to support bionic
  bionic_pyconfig=$LOCAL_TOP/bionic/pyconfig/pyconfig.h
  sed -i 's%#define HAVE_CONFSTR 1%/* #undef HAVE_CONFSTR */%' $bionic_pyconfig
  sed -i 's%#define HAVE_CRYPT_H 1%/* #undef HAVE_CRYPT_H */%' $bionic_pyconfig
  sed -i 's%#define HAVE_CRYPT_R 1%/* #undef HAVE_CRYPT_R */%' $bionic_pyconfig
  sed -i 's%#define HAVE_DECL_RTLD_DEEPBIND 1%/* #undef HAVE_DECL_RTLD_DEEPBIND */%' $bionic_pyconfig
  sed -i "s%#define HAVE_GCC_ASM_FOR_X87 1%#ifdef __i386__\n#define HAVE_GCC_ASM_FOR_X87 1\n#endif%" $bionic_pyconfig
  sed -i 's%#define HAVE_LIBINTL_H 1%/* #undef HAVE_LIBINTL_H */%' $bionic_pyconfig
  sed -i 's%#define HAVE_STROPTS_H 1%/* #undef HAVE_STROPTS_H */%' $bionic_pyconfig
  sed -i 's%#define HAVE_WAIT3 1%/* #undef HAVE_WAIT3 */%' $bionic_pyconfig

  sed -i 's%#define SIZEOF_FPOS_T .*%#define SIZEOF_FPOS_T 8%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_LONG .*%#ifdef __LP64__\n#define SIZEOF_LONG 8\n#else\n#define SIZEOF_LONG 4\n#endif%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_LONG_DOUBLE .*%#define SIZEOF_LONG_DOUBLE (SIZEOF_LONG * 2)%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_PTHREAD_T .*%#define SIZEOF_PTHREAD_T SIZEOF_LONG%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_SIZE_T .*%#define SIZEOF_SIZE_T SIZEOF_LONG%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_TIME_T .*%#define SIZEOF_TIME_T SIZEOF_LONG%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_UINTPTR_T .*%#define SIZEOF_UINTPTR_T SIZEOF_LONG%' $bionic_pyconfig
  sed -i 's%#define SIZEOF_VOID_P .*%#define SIZEOF_VOID_P SIZEOF_LONG%' $bionic_pyconfig

  # Changes to support musl
  sed -i "s%#define HAVE_DECL_RTLD_DEEPBIND 1%#ifdef __GLIBC__\n#define HAVE_DECL_RTLD_DEEPBIND 1\n#endif%" pyconfig.h
fi

cp pyconfig.h $LOCAL_TOP/$DIR/pyconfig/

#
# Generate frozen modules
#
make -j64 Python/deepfreeze/deepfreeze.c
mkdir -p $LOCAL_TOP/Python/deepfreeze
cp Python/deepfreeze/deepfreeze.c $LOCAL_TOP/Python/deepfreeze
rm -rf $LOCAL_TOP/Python/frozen_modules
cp -rp Python/frozen_modules $LOCAL_TOP/Python

function generate_srcs() {
  #
  # Generate config.c
  #
  echo >Makefile.pre
  Modules/makesetup -c Modules/config.c.in -s Modules -m Makefile.pre $LOCAL_TOP/$1/Setup.local $LOCAL_TOP/Setup.local Modules/Setup.bootstrap Modules/Setup
  cp config.c $LOCAL_TOP/$1

  #
  # Generate module file list
  #
  grep '$(CC)' Makefile | sed 's/;.*//' | sed 's/.*: //' | sed 's#$(srcdir)/##' | sed 's/$(PYTHON_HEADERS)//' | sed 's/$(MODULE_.*_DEPS)//' | sed 's#Modules/config.c##' | sed 's/ \+/\n/g' | sort -u >srcs
  (
    echo '// Generated by android/regen.sh'
    echo 'filegroup {'
    echo "    name: \"py3-c-modules-$1\","
    echo "    srcs: ["
    for src in $(cat srcs); do
      echo "        \"${src}\","
    done
    echo "    ],"
    echo '}'
  ) >$SRC_TOP/Android-$1.bp
}

generate_srcs $DIR

if [ $DIR == "linux_x86_64" ]; then
  generate_srcs bionic
fi