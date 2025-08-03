#!/bin/bash
#
# Copyright 2019 The Android Open Source Project
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

if [[ ${#@} != 1 ]]; then
  cat <<EOF
Usage
  host_bcp <image> | xargs <art-host-tool> ...
Extracts boot class path locations from <image> and outputs the appropriate
  --runtime-arg -Xbootclasspath:...
  --runtime-arg -Xbootclasspath-locations:...
arguments for many ART host tools based on the \$ANDROID_PRODUCT_OUT variable
and existing \$ANDROID_PRODUCT_OUT/apex/* paths.
EOF
  exit 1
fi

IMAGE=$1

if [[ ! -e ${IMAGE} ]]; then
  IMAGE=${ANDROID_PRODUCT_OUT}/$1
  if [[ ! -e ${IMAGE} ]]; then
    echo "Neither $1 nor ${ANDROID_PRODUCT_OUT}/$1 exists."
    exit 1
  fi
fi
BCPL=`grep -az -A1 -E '^bootclasspath$' ${IMAGE} 2>/dev/null | \
      xargs -0 echo | gawk '{print $2}'`
if [[ "x${BCPL}" == "x" ]]; then
  echo "Failed to extract boot class path locations from $1."
  exit 1
fi

APEX_INFO_LIST=${ANDROID_PRODUCT_OUT}/apex/apex-info-list.xml
if [[ ! -e ${APEX_INFO_LIST} ]]; then
  echo "Failed to locate apex info at ${APEX_INFO_LIST}."
  exit 1
fi

BCP=
OLD_IFS=${IFS}
IFS=:
APEX_PREFIX=/apex/
for COMPONENT in ${BCPL}; do
  HEAD=${ANDROID_PRODUCT_OUT}
  TAIL=${COMPONENT}
  # Apex module paths aren't symlinked on the host, so map from the symbolic
  # device path to the prebuilt (host) module path using the apex info table.
  if [[ ${COMPONENT:0:${#APEX_PREFIX}} = ${APEX_PREFIX} ]]; then
    # First extract the symbolic module name and its (internal) jar path.
    COMPONENT=${COMPONENT#${APEX_PREFIX}}
    MODULE_NAME=${COMPONENT%%/*}
    MODULE_JAR=${COMPONENT#*/}
    # Use the module name to look up the preinstalled module path..
    HOST_MODULE=`xmllint --xpath "string(//apex-info[@moduleName=\"${MODULE_NAME}\"]/@preinstalledModulePath)" ${APEX_INFO_LIST}`
    # Extract the preinstalled module name from the full path (strip prefix/suffix).
    HOST_MODULE_NAME=${HOST_MODULE#*${APEX_PREFIX}}
    HOST_MODULE_NAME=${HOST_MODULE_NAME%.*apex}
    # Rebuild the host path using the preinstalled module name.
    TAIL="${APEX_PREFIX}${HOST_MODULE_NAME}/${MODULE_JAR}"
  fi
  if [[ ! -e $HEAD$TAIL ]]; then
    echo "File does not exist: $HEAD$TAIL"
    exit 1
  fi
  BCP="${BCP}:${HEAD}${TAIL}"
done
IFS=${OLD_IFS}
BCP=${BCP:1}  # Strip leading ':'.

echo --runtime-arg -Xbootclasspath:${BCP} \
     --runtime-arg -Xbootclasspath-locations:${BCPL}
