#!/bin/bash
#
# Copyright (C) 2022 The Android Open Source Project
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

# Set up prog to be the path of this script, including following
# symlinks, and set up progdir to be the fully-qualified pathname of
# its directory.  Switch the current directory to progdir for the
# remainder of the script.
set -e

if [[ -z ${ANDROID_BUILD_TOP} ]]; then
  echo ANDROID_BUILD_TOP not set
  exit 1
fi

OJLUNI_JAVA_ROOT=${ANDROID_BUILD_TOP}/libcore/ojluni/src/main/java

AWK=awk SCRIPTS=${ANDROID_BUILD_TOP}/libcore/ojluni/src/tools/scripts/ \
${ANDROID_BUILD_TOP}/libcore/ojluni/src/tools/scripts/genExceptions.sh \
  ${OJLUNI_JAVA_ROOT}/java/nio/charset/exceptions \
  ${OJLUNI_JAVA_ROOT}/java/nio/charset

MAKE_ROOT=${ANDROID_BUILD_TOP}/libcore/ojluni/src/tools/make
gmake -C ${MAKE_ROOT} all

JAVA_NIO_OUT=${MAKE_ROOT}/out/gensrc/java.base/java/nio
OJLUNI_NIO_ROOT=${OJLUNI_JAVA_ROOT}/java/nio
cp ${JAVA_NIO_OUT}/charset/CharsetEncoder.java \
  ${OJLUNI_NIO_ROOT}/charset/CharsetEncoder.java
cp ${JAVA_NIO_OUT}/charset/CharsetDecoder.java \
  ${OJLUNI_NIO_ROOT}/charset/CharsetDecoder.java

NIO_CLASSES=(
  "HeapByteBuffer"
  "HeapCharBuffer"
  "HeapDoubleBuffer"
  "HeapFloatBuffer"
  "HeapIntBuffer"
  "HeapLongBuffer"
  "HeapShortBuffer"
  "ByteBuffer"
  "CharBuffer"
  "DoubleBuffer"
  "FloatBuffer"
  "IntBuffer"
  "LongBuffer"
  "ShortBuffer"
)

for clazz in ${NIO_CLASSES[*]}; do
  cp ${JAVA_NIO_OUT}/${clazz}.java ${OJLUNI_NIO_ROOT}/${clazz}.java
done

NIO_AS_CLASSES=(
  "ByteBufferAsCharBuffer"
  "ByteBufferAsDoubleBuffer"
  "ByteBufferAsFloatBuffer"
  "ByteBufferAsIntBuffer"
  "ByteBufferAsLongBuffer"
  "ByteBufferAsShortBuffer"
)

for clazz in ${NIO_AS_CLASSES[*]}; do
  cp ${JAVA_NIO_OUT}/${clazz}L.java ${OJLUNI_NIO_ROOT}/${clazz}.java
done

pushd ${ANDROID_BUILD_TOP}/libcore/ojluni/src/test/java/nio/Buffer/
./genBasic.sh
./genCopyDirectMemory.sh
./genOrder.sh
popd
