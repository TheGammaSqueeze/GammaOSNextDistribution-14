#!/bin/bash -u

if [[ -z "${ANDROID_BUILD_TOP:-}" ]] ; then
  echo >&2 "*** ERROR: $(basename $0) requires envar ANDROID_BUILD_TOP to be set"
  exit 1
fi

DRYRUN=""
MODE="update"
while [[ $# -ne 0 ]] ; do
  case "${1}" in
    --dryrun)
      DRYRUN="echo"
      shift
      ;;
    --mode=*)
      MODE=${1#"--mode="}
      shift
      ;;
    *)
      echo >&2 "*** USAGE: $(basename $0) [--dryrun] [--mode={update|hook}]"
      exit 1
      ;;
  esac
done

TOOL=$(dirname $0)/generate_api.py
SPECFILE=$(dirname $0)/types.spec
HALDIR=${ANDROID_BUILD_TOP}/hardware/interfaces/neuralnetworks
NDKDIR=${ANDROID_BUILD_TOP}/packages/modules/NeuralNetworks/runtime/include
CANONICALDIR=${ANDROID_BUILD_TOP}/packages/modules/NeuralNetworks/common/types/include/nnapi
AIDLDIR=${ANDROID_BUILD_TOP}/hardware/interfaces/neuralnetworks/aidl/android/hardware/neuralnetworks

RET=0
function doit {
  typeset -r kind="$1" in="$2" out="$3"
  echo "=== $kind"
  ${DRYRUN} ${TOOL} --kind ${kind} --specification ${SPECFILE} --template ${in} --out ${out}
  if [[ $? -ne 0 ]] ; then RET=1 ; fi
}

function check {
  typeset -r kind="$1" in="$2" out="$3"
  TEMPFILE=$(mktemp)
  doit ${kind} ${in} ${TEMPFILE}
  if [[ ${RET} -eq 0 ]] ; then
    ${DRYRUN} cmp -s ${out} ${TEMPFILE} || {
      RET=1
      echo >&2 "Error: $(basename ${out}) is out of sync with $(basename ${in}) or types.spec. Please run generate_api.sh before uploading."
    }
  fi
}

case "${MODE}" in
  update)
    doit canonical $(dirname $0)/Types.t ${CANONICALDIR}/Types.h
    doit canonical $(dirname $0)/OperandTypes.t ${CANONICALDIR}/OperandTypes.h
    doit canonical $(dirname $0)/OperationTypes.t ${CANONICALDIR}/OperationTypes.h
    doit ndk $(dirname $0)/NeuralNetworksTypes.t ${NDKDIR}/NeuralNetworksTypes.h
    doit hal_1.0 ${HALDIR}/1.0/types.t ${HALDIR}/1.0/types.hal
    doit hal_1.1 ${HALDIR}/1.1/types.t ${HALDIR}/1.1/types.hal
    doit hal_1.2 ${HALDIR}/1.2/types.t ${HALDIR}/1.2/types.hal
    doit hal_1.3 ${HALDIR}/1.3/types.t ${HALDIR}/1.3/types.hal
    doit aidl $(dirname $0)/OperandTypeAidl.t ${AIDLDIR}/OperandType.aidl
    doit aidl $(dirname $0)/OperationTypeAidl.t ${AIDLDIR}/OperationType.aidl
    ;;
  hook)
    check canonical $(dirname $0)/Types.t ${CANONICALDIR}/Types.h
    check canonical $(dirname $0)/OperandTypes.t ${CANONICALDIR}/OperandTypes.h
    check canonical $(dirname $0)/OperationTypes.t ${CANONICALDIR}/OperationTypes.h
    check ndk $(dirname $0)/NeuralNetworksTypes.t ${NDKDIR}/NeuralNetworksTypes.h
    # Avoids checking HAL/AIDL because of cross-repo dependencies.
    ;;
  *)
    echo >&2 "*** Unknown mode: ${MODE}"
    exit 1
    ;;
esac

if [[ ${RET} -ne 0 ]] ; then
  echo >&2 "*** FAILED"
fi
exit ${RET}
