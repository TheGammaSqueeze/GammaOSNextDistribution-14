#!/bin/bash

set -x

# Setup paths and import util functions
. $(dirname $(readlink -f "$0"))/util.sh

TESTS=""
BACKENDS=""
NUM_THREADS=${NUM_THREADS:-$(expr $(expr $(nproc) / 8) + 1)}

USE_HOST_GLES=0
TEST_APP="deqp"

parse_input()
{
   while  [ -n "$1" ]; do
      case $1 in

      -a|--all-backends)
         BACKENDS=""
         BACKENDS="$BACKENDS vtest-softpipe"
         BACKENDS="$BACKENDS vtest-llvmpipe"
         BACKENDS="$BACKENDS vtest-gpu"
         BACKENDS="$BACKENDS softpipe"
         BACKENDS="$BACKENDS llvmpipe"
         BACKENDS="$BACKENDS gpu"
         ;;

   	-v|--vtest)
         BACKENDS=""
         BACKENDS="$BACKENDS vtest-softpipe"
         BACKENDS="$BACKENDS vtest-llvmpipe"
         BACKENDS="$BACKENDS vtest-gpu"
         ;;

      --host-gles)
         USE_HOST_GLES=1
         ;;

      --host-gl)
         USE_HOST_GLES=0
         ;;

      -b|--backend)
         NEW_BACKEND="$2"
         shift
         BACKENDS="$BACKENDS $NEW_BACKEND"
         ;;

      -j|--threads)
         NUM_THREADS=$2
         shift
         ;;

   	--gles2)
         TESTS="$TESTS gles2"
   	   ;;

   	--gles3)
         TESTS="$TESTS gles3"
   	   ;;

   	--gles31)
         TESTS="$TESTS gles31"
   	   ;;

      --gl30)
         TESTS="$TESTS gl30"
         ;;

      --gl31)
         TESTS="$TESTS gl31"
         ;;

      --gl32)
         TESTS="$TESTS gl32"
         ;;

      -d|--deqp)
         TEST_APP="deqp"
         ;;

      -p|--piglit)
         TEST_APP="piglit"
         ;;

   	*)
   	   echo "Unknown flag $1"
   	   exit 1
      esac
      shift
   done

   if [[ -z $BACKENDS ]]; then
      BACKENDS="gpu"
   fi
}

compare_previous()
{
   if [ ! -f $PREVIOUS_RESULTS_DIR/results.txt ]; then
      return 2
   fi

   # Piglit tests use @ as separator for path/to/test
   IGNORE_TESTS=$(sed "s/\@/\//g" $IGNORE_TESTS_FILE 2>/dev/null)

   # Avoid introducing changes while doing this comparison
   TMP_RESULTS=$(mktemp /tmp/virgl_ci_results.XXXXXX)
   TMP_PREV_RESULTS=$(mktemp /tmp/virgl_ci_previous_results.XXXXXX)
   cp $RESULTS_DIR/results.txt $TMP_RESULTS
   cp $PREVIOUS_RESULTS_DIR/results.txt $TMP_PREV_RESULTS

   for TEST in $IGNORE_TESTS; do
      sed -i "\:$TEST:d" $TMP_RESULTS $TMP_PREV_RESULTS
   done

   if [ "$TEST_APP" = "piglit" ]; then
      # This distinction adds too much variability
      sed -i "s/crash/fail/g" $TMP_RESULTS $TMP_PREV_RESULTS
   elif [ "$TEST_APP" = "deqp" ]; then
      # This distinction adds too much variability
      sed -i "s/QualityWarning/Pass/g" $TMP_RESULTS $TMP_PREV_RESULTS
      sed -i "s/CompatibilityWarning/Pass/g" $TMP_RESULTS $TMP_PREV_RESULTS
   fi

   # Sort results files
   sort -V $TMP_RESULTS -o $TMP_RESULTS
   sort -V $TMP_PREV_RESULTS -o $TMP_PREV_RESULTS

   diff -u $TMP_PREV_RESULTS $TMP_RESULTS 2>&1 > $RESULTS_DIR/regression_diff.txt
   if [ $? -ne 0 ]; then
      touch $VIRGL_PATH/results/regressions_detected
      return 1
   else
      rm -rf $RESULTS_DIR/regression_diff.txt
      return 0
   fi
}

interpret_results()
{
   PASSED_TESTS="$1"
   TOTAL_TESTS="$2"
   UNRELIABLE="$3"

   # TODO: Add comparison for the unreliable tests
   if [ $UNRELIABLE -eq 0 ]; then
      compare_previous
      case $? in
         0)
         echo "Pass - matches previous results"
         return 0
         ;;

         1)
         echo "Fail - diff against previous results: $RESULTS_DIR/regression_diff.txt"
         echo -n "Changes detected: "
         grep ^+ $RESULTS_DIR/regression_diff.txt | wc -l 
         head -n20 $RESULTS_DIR/regression_diff.txt
         return 1
         ;;

         2)
         echo "Pass - no previous results, but passed $PASSED_TESTS/$TOTAL_TESTS tests"
         return 0
         ;;

         *)
         echo "BUG!"
         return 1
         ;;
      esac
   else
      if [ $PASSED_TESTS -eq $TOTAL_TESTS ] && [ $TOTAL_TESTS -ne 0 ]; then
         echo "Pass - passed $PASSED_TESTS/$TOTAL_TESTS tests"
         return 0
      else
         echo "Fail - passed $PASSED_TESTS/$TOTAL_TESTS tests: $RESULTS_DIR/results.txt"
         return 1
      fi
   fi
}

run_vtest_server()
{
   (
   if [ $USE_HOST_GLES -eq 1 ]; then
      VTEST_USE_EGL_SURFACELESS=1 \
      VTEST_USE_GLES=1 \
      virgl_test_server &>$VTEST_LOG_FILE &
   else
      VTEST_USE_EGL_SURFACELESS=1 \
      virgl_test_server &>$VTEST_LOG_FILE &
   fi
   )
}

run_test_suite()
{
   local BACKEND="$1"
   local TEST_NAME="$2"
   local UNRELIABLE="$3"
   local LOCAL_TEST_FILE="$4"
   local RES_FILE=$RESULTS_FILE
   
#   echo "run_test_suite() OUTPUT_PATH: $OUTPUT_PATH"
#   echo "run_test_suite() LOG_FILE: $LOG_FILE"
#   echo "run_test_suite() RESULTS_FILE: $RESULTS_FILE"
   
   UNRELIABLE_STRING=""
   if [ $UNRELIABLE -eq 1 ]; then
      UNRELIABLE_STRING="unreliable "
      RES_FILE="$RES_FILE.unreliable"
   fi

   if [[ $BACKEND == *"vtest"* ]]; then
      printf "Running ${UNRELIABLE_STRING}$TEST_APP-$TEST_NAME on vtest-$DRIVER_NAME: "
   else
      printf "Running ${UNRELIABLE_STRING}$TEST_APP-$TEST_NAME on $DRIVER_NAME: "
   fi

   if test $UNRELIABLE -eq 1; then
      LOCAL_TEST_FILE="$IGNORE_TESTS_FILE"
      if test ! -f $LOCAL_TEST_FILE -o $(wc -l $LOCAL_TEST_FILE | cut -f1 -d' ') -eq 0; then
         echo "Unreliable: no ignore tests."
         return 0
      fi
   fi

   case $TEST_APP in
   piglit)
      # Don't run GLX tests
      PIGLIT_TESTS=" -x glx"

      if test $UNRELIABLE -eq 1; then
         # XXX: Fold the glx exception?
         PIGLIT_TESTS_CMD="--test-list $LOCAL_TEST_FILE"
      else
         # TODO: create test_file for normal runs
         PIGLIT_TESTS_CMD="$PIGLIT_TESTS -t $TEST_NAME"

      fi

      EGL_PLATFORM=x11 \
      piglit run --platform x11_egl \
         -l verbose \
         --jobs $NUM_THREADS \
         $PIGLIT_TESTS_CMD \
         gpu \
         /tmp/  &> $LOG_FILE

      piglit summary console /tmp/ | grep -B 999999 "summary:" | grep -v "summary:" > "$RES_FILE"

      piglit summary html  $(dirname "$RES_FILE")/summary /tmp

      TOTAL_TESTS=$(cat $RES_FILE | wc -l)
      PASSED_TESTS=$(grep " pass" $RES_FILE | wc -l)
      ;;

   deqp)
      deqp  \
         --cts-build-dir $CTS_PATH/build \
         --test-names-file "$LOCAL_TEST_FILE" \
         --results-file "$RES_FILE" \
         --threads $NUM_THREADS &> $LOG_FILE

      # Remove header
      sed -i "/#/d" $RES_FILE

      # Sort results file to make diffs easier to read
      sort -V $RES_FILE -o $RES_FILE

      TOTAL_TESTS=$(cat $RES_FILE | wc -l)
      PASSED_TESTS=$(grep " Pass" $RES_FILE | wc -l)
      ;;
   esac

   interpret_results $PASSED_TESTS $TOTAL_TESTS $UNRELIABLE
   return $?
}

create_result_dir()
{
   if [[ -n $GALLIUM_DRIVER ]]; then
      HOST_DRIVER="_${GALLIUM_DRIVER}"
   elif [[ -n $DRIVER_NAME ]]; then
      HOST_DRIVER="_${DRIVER_NAME}"
   fi

   if [ $USE_HOST_GLES -eq 0 ]; then
      HOST_GL="gl"
   else
      HOST_GL="es"
   fi

   TEST_PATH=${HOST_GL}_host${HOST_DRIVER}/${TEST_APP}_${TEST_NAME}
   RESULTS_DIR=$VIRGL_PATH/results/${TEST_PATH}

   if [ "$HOST_DRIVER" = "_softpipe" -o "$HOST_DRIVER" = "_llvmpipe" ]; then
      PREVIOUS_RESULTS_DIR=$VIRGL_PATH/ci/previous_results/${TEST_PATH}
   else
      echo WARNING: Results are not up-to-date!
      PREVIOUS_RESULTS_DIR=$VIRGL_PATH/ci/previous_results/archived/${TEST_PATH}
   fi

   IGNORE_TESTS_FILE=$PREVIOUS_RESULTS_DIR/ignore_tests.txt

   # Remove comments from test-list
   FILTERED_TEST_FILE=$(mktemp /tmp/virgl-ci.XXXXX)
   sed '/^#/d;/^$/d' $IGNORE_TESTS_FILE 2>/dev/null > $FILTERED_TEST_FILE
   IGNORE_TESTS_FILE=$FILTERED_TEST_FILE

   mkdir -p "$RESULTS_DIR"

   export OUTPUT_PATH="${RESULTS_DIR}"
   export RESULTS_FILE="${OUTPUT_PATH}/results.txt"
   export LOG_FILE="${OUTPUT_PATH}/log.txt"
   export VTEST_LOG_FILE="${OUTPUT_PATH}/vtest_log.txt"
}

run_test_on_backends()
{
   local BACKENDS="$1"
   local TEST_NAME="$2"
   local TEST_FILE="$3"
   local RET=0

#   echo "run_test_on_backends() BACKENDS: $BACKENDS"
#   echo "run_test_on_backends() TEST_NAME: $TEST_NAME"
#   echo "run_test_on_backends() TEST_FILE: $TEST_FILE"

   for BACKEND in $BACKENDS; do
         unset DRIVER_NAME
         unset GALLIUM_DRIVER
         unset GALLIVM_PERF
         unset LIBGL_ALWAYS_SOFTWARE
         unset VTEST_USE_EGL_SURFACELESS

         case $BACKEND in
            vtest-softpipe|softpipe)
               export LIBGL_ALWAYS_SOFTWARE=1
               export GALLIUM_DRIVER=softpipe
               export DRIVER_NAME=$GALLIUM_DRIVER
               ;;

            vtest-llvmpipe|llvmpipe)
               export GALLIVM_PERF=nopt,no_filter_hacks
               export LIBGL_ALWAYS_SOFTWARE=1
               export GALLIUM_DRIVER=llvmpipe
               export DRIVER_NAME=$GALLIUM_DRIVER
               ;;

            vtest-gpu|gpu)
               DEVICE_NAME=$(basename /dev/dri/renderD128)
               export DRIVER_NAME="$(basename `readlink /sys/class/drm/${DEVICE_NAME}/device/driver`)"
               ;;
         esac

         # This case statement is broken into two parts
         # because for the second part the LOG_FILE has
         # declared, which is needed to redirect FDs
         create_result_dir

         case $BACKEND in
            vtest-*)
               run_vtest_server
               export GALLIUM_DRIVER=virpipe
               ;;

            *)
               ;;
         esac

         # Execute both mustpass and unstable tests
         # Only the former twigger an overall run fail
         run_test_suite "$BACKEND" "$TEST_NAME" 0 "$TEST_FILE"
         if [ $? -ne 0 ]; then
            RET=1
         fi

         run_test_suite "$BACKEND" "$TEST_NAME" 1 "$TEST_FILE"

         killall -q virgl_test_server
   done

   return $RET
}

run_all_tests()
{
   local BACKENDS=$1
   local TESTS=$2
   local RET=0

   if [ $USE_HOST_GLES -eq 0 ]; then
      echo "Running test(s) on GL Host"
      echo "--------------------------"
   else
      echo "Running test(s) on GLES Host"
      echo "----------------------------"
   fi
#   echo "run_all_tests() BACKENDS: $BACKENDS"
#   echo "run_all_tests() TESTS: $TESTS"


   # TODO: add similar must pass lists for piglit
   for TEST in $TESTS; do
      case $TEST in
      gles2|gles3|gles31)
         TEST_FILE="$CTS_PATH/android/cts/master/$TEST-master.txt"
         ;;
      gl30|gl31|gl32)
         TEST_FILE="$CTS_PATH/external/openglcts/data/mustpass/gl/khronos_mustpass/4.6.1.x/$TEST-master.txt"
         ;;
      esac

      run_test_on_backends "$BACKENDS" "$TEST" "$TEST_FILE"

      if [ $? -ne 0 ]; then
         RET=1
      fi
   done

   exit $RET
}

setup()
{
   Xvfb :0 -screen 0 1024x768x24 &>/dev/null &
   export DISPLAY=:0
   sleep 2
}

setup
parse_input $@
run_all_tests "$BACKENDS" "$TESTS"
