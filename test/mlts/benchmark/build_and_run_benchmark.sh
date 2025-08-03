#!/bin/bash
#
# Build benchmark app and run it, mimicking a user-initiated run
#
# Output is logged to a temporary folder and summarized in txt and JSON formats.
# parallel-inference-stress tests produce no output except for the success or failure notification,
# which is not logged.

if [[ "$OSTYPE" == "darwin"* ]]; then
  OPTS="$(getopt f:rbsm:xn: -- "$*")"
else
  OPTS="$(getopt -o f:rbsm:xn: -l filter-driver:,include-nnapi-reference,nnapi-reference-only,skip-build,use-nnapi-sl,filter-model,extract-nnapi-sl,nnapi-sl-vendor -- "$@")"
fi

if [ $? -ne 0 ]; then
  echo "Invalid arguments, accepted options are"
  if [[ "$OSTYPE" == "darwin"* ]]; then
    echo " -f <regex> : to run crash tests only on the drivers (ignoring nnapi-reference) matching the specified regular expression"
    echo " -r : to include nnapi-reference in target drivers"
    echo " -b : skip build and installation of tests"
    echo " -s : use NNAPI Support Library drivers (embedded in the benchmark APK unless -x is specified)"
    echo " -x : extract NNAPI Support Library drivers from the APK"
    echo " -n <nnapi-sl-vendor> : Optional, specify which SL driver vendor is used (qc/mtk/arm), default: qc"
    echo " -m <regex> : to filter the models used in the tests"
  else
    echo " -f <regex> | --filter-driver <regex> : to run crash tests only on the drivers (ignoring nnapi-reference) matching the specified regular expression"
    echo " -r | --include-nnapi-reference : to include nnapi-reference in target drivers"
    echo " --nnapi-reference-only : to run tests only vs nnapi-reference"
    echo " -b | --skip-build : skip build and installation of tests"
    echo " -s | --use-nnapi-sl : use NNAPI Support Library drivers (embedded in the benchmark APK unless -x is specified)"
    echo " -x | --extract-nnapi-sl : extract NNAPI Support Library drivers from the APK"
    echo " -n <nnapi-sl-vendor> : Optional, specify which SL driver vendor is used (qc/mtk/arm), default: qc"
    echo " -m <regex> : to filter the models used in the tests"
  fi
  exit
fi

eval set -- "$OPTS"

DRIVER_FILTER_OPT=""
INCLUDE_NNAPI_REF_OPT=""
BUILD_AND_INSTALL=true
NNAPI_SL_FILTER_OPT=""
MODEL_FILTER_OPT=""
while [ $# -gt 0 ] ; do
  case "$1" in
    -f|--filter-driver)
      DRIVER_FILTER_OPT="-e nnCrashtestDeviceFilter $2"
      shift 2
      ;;
    -r|--include-nnapi-reference)
      INCLUDE_NNAPI_REF_OPT="-e nnCrashtestIncludeNnapiReference true"
      shift
      ;;
    --nnapi-reference-only)
      DRIVER_FILTER_OPT="-e nnCrashtestDeviceFilter no-device"
      INCLUDE_NNAPI_REF_OPT="-e nnCrashtestIncludeNnapiReference true"
      shift
      ;;
    -m|--filter-model)
      MODEL_FILTER_OPT="-e nnBenchmarkModelFilter $2"
      shift 2
      ;;
    -b|--skip-build)
      BUILD_AND_INSTALL=false
      shift
      ;;
    -s|--use-nnapi-sl)
      NNAPI_SL_FILTER_OPT+=" -e useNnApiSupportLibrary true"
      shift
      ;;
    -x|--extract-nnapi-sl)
      NNAPI_SL_FILTER_OPT+=" -e extractNnApiSupportLibrary true"
      shift

      echo "Creating configuration file with list of libraries"
      mkdir sl_prebuilt/assets
      ls sl_prebuilt/ 2>/dev/null | grep '.so'  >sl_prebuilt/assets/sl_prebuilt_filelist.txt
      ;;
    -n|--nnapi-sl-vendor)
      NNAPI_SL_VENDOR=$2
      shift 2
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "Unsupported arg $1"
      exit 1
  esac
done

MODE="${1:-scoring}"
INSTALL_NATIVE_TESTS=false
CRASH_TEST_APP="NeuralNetworksApiCrashTest"
APP="NeuralNetworksApiBenchmark"
case "$MODE" in
  scoring)
    CLASS=com.android.nn.benchmark.app.NNScoringTest
    ;;
  inference-stress)
    CLASS=com.android.nn.benchmark.app.NNInferenceStressTest
    ;;
  model-loading-stress)
    CLASS=com.android.nn.benchmark.app.NNModelLoadingStressTest
    ;;
  parallel-inference-stress)
    CLASS=com.android.nn.crashtest.app.NNParallelCrashResistantInferenceTest
    APP="$CRASH_TEST_APP"
    ;;
  parallel-inference-stress-in-process)
    CLASS=com.android.nn.crashtest.app.NNParallelInProcessInferenceTest
    APP="$CRASH_TEST_APP"
    ;;
  client-early-termination-stress)
    CLASS=com.android.nn.crashtest.app.NNClientEarlyTerminationTest
    APP="$CRASH_TEST_APP"
    ;;
  multi-process-inference-stress)
    CLASS=com.android.nn.crashtest.app.NNMultipleProcessInferenceTest
    APP="$CRASH_TEST_APP"
    INSTALL_NATIVE_TESTS=true
    ;;
  multi-process-model-load-stress)
    CLASS=com.android.nn.crashtest.app.NNMultipleProcessModelLoadTest
    APP="$CRASH_TEST_APP"
    INSTALL_NATIVE_TESTS=true
    ;;
  memory-mapped-model-load-stress)
    CLASS=com.android.nn.crashtest.app.NNMemoryMappedModelCompilationTest
    APP="$CRASH_TEST_APP"
    ;;
  model-load-random-stress)
    APP="$CRASH_TEST_APP"
    CLASS=com.android.nn.crashtest.app.NNRandomGraphLoadTest
    ;;
  inference-random-stress)
    APP="$CRASH_TEST_APP"
    CLASS=com.android.nn.crashtest.app.NNRandomGraphExecutionTest
    ;;
  performance-degradation-stress)
    APP="$CRASH_TEST_APP"
    CLASS=com.android.nn.crashtest.app.NNPerformanceDegradationTest
    ;;
  *)
    echo "Unknown execution mode: $1"
    echo "Known modes: scoring (default), inference-stress, model-loading-stress, " \
      "parallel-inference-stress, parallel-inference-stress-in-process, " \
      "client-early-termination-stress, multi-process-inference-stress, " \
      "multi-process-model-load-stress memory-mapped-model-load-stress, " \
      "model-load-random-stress, inference-random-stress, performance-degradation-stress"
    exit 1
    ;;
esac

if [[ -z "$ANDROID_BUILD_TOP" ]]; then
  echo ANDROID_BUILD_TOP not set, bailing out
  echo you must run lunch before running this script
  exit 1
fi

set -e
cd $ANDROID_BUILD_TOP

if [ "$BUILD_AND_INSTALL" = true ]; then
   if [ ! -z "$NNAPI_SL_FILTER_OPT" ]; then
    SL_PREBUILT=test/mlts/benchmark/sl_prebuilt
    if [ ! -n "$(ls -A $SL_PREBUILT/*.so 2>/dev/null)" ]; then
      echo "There is no NNAPI SL binary file under $ANDROID_BUILD_TOP/$SL_PREBUILT, cannot test using NNAPI SL"
      exit
    fi
    if [ ! -f "$SL_PREBUILT/Android.bp" ]; then
      echo "================================================================"
      echo "Enabling build of NNAPI SL libraries using template definition."
      echo  "If the definitions in $SL_PREBUILT/Android.bp don't match the libraries you copied"
      echo " please define your own version of $SL_PREBUILT/Android.bp"
      echo "================================================================"
      mv $SL_PREBUILT/Android.bp.template $SL_PREBUILT/Android.bp
    fi
    if [[ -z "$NNAPI_SL_VENDOR" ]]; then
      NNAPI_SL_VENDOR="qc"
    fi
    NNAPI_SL_FILTER_OPT+=" -e nnApiSupportLibraryVendor $NNAPI_SL_VENDOR"
  fi

  # Build and install benchmark app
  TMPFILE=$(mktemp)
  build/soong/soong_ui.bash --make-mode ${APP} 2>&1 | tee ${TMPFILE}
  TARGET_ARCH=$(cat ${TMPFILE} | grep TARGET_ARCH= | sed -e 's/TARGET_ARCH=//')
  if [ "${TARGET_ARCH}" = "aarch64" ]; then
      APK_DIR=arm64
  else
      APK_DIR=${TARGET_ARCH}
  fi

  if [ ! -z "$NNAPI_SL_FILTER_OPT" ]; then
    if [ "$(unzip -l $OUT/testcases/${APP}/${APK_DIR}/${APP}.apk | grep libnnapi_sl_driver | wc -l)" -ne 1 ]; then
      echo "NNAPI SL Libraries are not included in the APK" \
          "please check the library list is included in the LOCAL_JNI_SHARED_LIBRARIES list " \
          "for ${APP}. Please check the value of SL_LIBS in Android.mk"
      exit
    fi
  fi

  if ! adb install -r $OUT/testcases/${APP}/${APK_DIR}/${APP}.apk; then
    adb uninstall com.android.nn.benchmark.app
    adb install -r $OUT/testcases/${APP}/${APK_DIR}/${APP}.apk
  fi

  if [ "$INSTALL_NATIVE_TESTS" = true ]; then
    build/soong/soong_ui.bash --make-mode nn_stress_test
    adb push $OUT/system/bin/nn_stress_test /bin/
  fi
fi

# Should we figure out if we run on release device
if [ -z "$MLTS_RELEASE_DEVICE" ]; then
  BUILD_DESCRIPTION=`adb shell getprop ro.build.description`
  if [[ $BUILD_DESCRIPTION =~ .*release.* ]]
  then
    MLTS_RELEASE_DEVICE=True
  else
    MLTS_RELEASE_DEVICE=False
  fi
fi

# Pass --no-isolated-storage to am instrument?
BUILD_VERSION_RELEASE=`adb shell getprop ro.build.version.release`
AM_INSTRUMENT_FLAGS="$DRIVER_FILTER_OPT $INCLUDE_NNAPI_REF_OPT $NNAPI_SL_FILTER_OPT $MODEL_FILTER_OPT"
if [[ $BUILD_VERSION_RELEASE == "Q" ]]; then
  AM_INSTRUMENT_FLAGS+=" --no-isolated-storage"
fi

if [[ "$MODE" == "scoring" ]]; then
  if [[ "$MLTS_RELEASE_DEVICE" == "True" ]]; then
    TEST_EXTENRAL_STORAGE="com.android.nn.benchmark.app/com.android.nn.benchmark.util.TestExternalStorageActivity"
    while ! adb shell "am start -W $TEST_EXTENRAL_STORAGE && rm /sdcard/mlts_write_external_storage" > /dev/null 2>&1; do
       echo "************************************************************"
       echo "Grant External storage write permissions to MLTS to proceed!"
       echo "************************************************************"
       read -n 1 -r -p "Continue? (press any key)"
       echo
    done
  else
    adb root
    adb shell "pm grant com.android.nn.benchmark.app android.permission.WRITE_EXTERNAL_STORAGE"
    # Skip setup wizard and remount (read-write)
    if ! adb shell test -f /data/local.prop; then
      adb shell 'echo ro.setupwizard.mode=DISABLED > /data/local.prop'
      adb shell 'chmod 644 /data/local.prop'
      adb shell 'settings put global device_provisioned 1*'
      adb shell 'settings put secure user_setup_complete 1'
      adb disable-verity
      adb reboot
      sleep 5
      adb wait-for-usb-device root
      adb wait-for-usb-device remount
      sleep 5
    fi
    set +e
    # Enable menu key press through adb
    adb shell 'echo testing > /data/local/enable_menu_key'
    # Leave screen on (affects scheduling)
    adb shell settings put system screen_off_timeout 86400000
    # Stop background apps, seem to take ~10% CPU otherwise
    adb shell 'pm disable com.google.android.googlequicksearchbox'
    adb shell 'pm list packages -f' | sed -e 's/.*=//' | sed 's/\r//g' | grep "com.breel.wallpapers" | while read pkg; do adb shell "pm disable $pkg"; done;
    set -e
  fi
fi

adb shell setprop debug.nn.cpuonly 0
adb shell setprop debug.nn.vlog "''"

# Menukey - make sure screen is on
adb shell "input keyevent 82"
# Show homescreen
adb shell wm dismiss-keyguard

if [[ "$MODE" == "scoring" ]]; then
  LOGDIR=$(mktemp -d)/mlts-logs
  HOST_CSV=$LOGDIR/benchmark.csv
  RESULT_HTML=$LOGDIR/result.html
  DEVICE_CSV=/storage/emulated/0/mlts_benchmark.csv

  mkdir -p $LOGDIR
  echo Creating logs in $LOGDIR

  # Remove old benchmark csv data
  adb shell rm -f ${DEVICE_CSV}
fi

# Set the shell pid as a top-app and run tests
time adb shell "echo $$ > /dev/stune/top-app/tasks; am instrument ${AM_INSTRUMENT_FLAGS} -w -e class $CLASS com.android.nn.benchmark.app/androidx.test.runner.AndroidJUnitRunner"

if [[ "$MODE" == "scoring" ]]; then
  adb pull $DEVICE_CSV $HOST_CSV
  echo Benchmark data saved in $HOST_CSV

  $ANDROID_BUILD_TOP/test/mlts/benchmark/results/generate_result.py $HOST_CSV $RESULT_HTML
  echo Results stored  in $RESULT_HTML
  xdg-open $RESULT_HTML
fi
