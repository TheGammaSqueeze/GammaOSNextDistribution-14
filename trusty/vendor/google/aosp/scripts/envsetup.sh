# Copyright (C) 2018 The Android Open Source Project
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

gettop() {
    # $BASH_SOURCE works if the shell is bash. $0 works when the shell was
    # invoked from a shell script but may fail when sourced in non-bash shell.
    SCRIPT=$(readlink -f ${BASH_SOURCE:-$0})
    TOPFILE="trusty/vendor/google/aosp/scripts/envsetup.sh"
    TOPDIR=$(dirname $SCRIPT)
    while [ \( ! -f "$TOPDIR/$TOPFILE" \) -a \( "$TOPDIR" != "/" \) ]; do
        TOPDIR=`dirname $TOPDIR`
    done
    if [ ! -f "$TOPDIR/$TOPFILE" ]; then
        echo "Error: Couldn't locate the top of the trusty tree. Try using bash?" 1>&2
        exit 1
    fi
    echo $TOPDIR
}

export TRUSTY_TOP=$(gettop)
export CLANG_BINDIR=${TRUSTY_TOP}/prebuilts/clang/host/linux-x86/clang-r475365b/bin
export CLANG_HOST_LIBDIR=${CLANG_BINDIR}/../lib
export CLANG_TOOLS_BINDIR=${TRUSTY_TOP}/prebuilts/clang-tools/linux-x86/bin
export LINUX_CLANG_BINDIR=${TRUSTY_TOP}/prebuilts/clang/host/linux-x86/clang-r475365b/bin
export RUST_BINDIR=${TRUSTY_TOP}/prebuilts/rust/linux-x86/1.70.0/bin
export RUST_HOST_LIBDIR=${RUST_BINDIR}/../lib/rustlib/x86_64-unknown-linux-gnu/lib
export ARCH_arm_TOOLCHAIN_PREFIX=${CLANG_BINDIR}/llvm-
export ARCH_arm64_TOOLCHAIN_PREFIX=${CLANG_BINDIR}/llvm-
export ARCH_x86_64_TOOLCHAIN_PREFIX=${CLANG_BINDIR}/llvm-
export ARCH_x86_TOOLCHAIN_PREFIX=${CLANG_BINDIR}/llvm-
export BUILDTOOLS_BINDIR=${TRUSTY_TOP}/prebuilts/build-tools/linux-x86/bin
export BUILDTOOLS_COMMON=${TRUSTY_TOP}/prebuilts/build-tools/common
export PY3=$BUILDTOOLS_BINDIR/py3-cmd

# Additional Python libraries:
# Most Trusty Python scripts source this file to find the hermetic
# Python interpreter ($PY3).
# The hermetic Python interpreter only exposes the Python standard library.
# Set PYTHONPATH to make select, vendor'ed libraries available to hermetic
# Python scripts.
# WARNING: Python scripts that are copied to the build output directory should *not*
# import these packages.
PYTHONPATH=${TRUSTY_TOP}/prebuilts/libprotobuf/python
PYTHONPATH=${PYTHONPATH}:${TRUSTY_TOP}/external/python/six
PYTHONPATH=${PYTHONPATH}:${TRUSTY_TOP}/external/python/markupsafe/src
PYTHONPATH=${PYTHONPATH}:${TRUSTY_TOP}/external/python/jinja/src
export PYTHONPATH

# Bindgen uses clang and libclang at runtime, so we need to tell it where to
# look for these tools.
export BINDGEN_CLANG_PATH=${TRUSTY_TOP}/prebuilts/clang/host/linux-x86/clang-r475365b/bin/clang
export BINDGEN_LIBCLANG_PATH=${TRUSTY_TOP}/prebuilts/clang/host/linux-x86/clang-r475365b/lib
