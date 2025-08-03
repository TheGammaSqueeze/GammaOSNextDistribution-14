#!/bin/bash

run_setup()
{
   set -x

   use_meson=$1

   if [ "x$2" = "xfuzzer" ]; then
      use_clang_fuzzer=1
   fi

   if [ "x$2" = "xtrace_stderr" ]; then
      use_trace_stderr=1
   fi

   if [ "x$2" = "xvenus" ]; then
      use_venus=1
   fi

   # Let .gitlab-ci or local ci runner set
   # desired thread count
   NUM_THREADS=${NUM_THREADS:-$(expr $(expr $(nproc) / 8) + 1)}
   export NUM_THREADS
   echo "Using $NUM_THREADS threads"

   export LD_LIBRARY_PATH=/usr/local/lib64:/usr/local/lib:/usr/local/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH

   export CCACHE_BASEDIR="$(pwd)"
   export CCACHE_DIR="$(pwd)/ccache"
   export PATH="/usr/lib/ccache:$PATH"
   mkdir -p $CCACHE_DIR
   ccache -s

   # To prevent hitting assertions such as the below:
   # sb/sb_sched.cpp:1207:schedule_alu: Assertion '!"unscheduled pending instructions"' failed.
   export R600_DEBUG=nosb

   # If render node, like /dev/dri/renderD128, has not been set
   # or exists use llvmpipe instead of HW GPU.
   if [[ ! -c $RENDER_DEVICE ]]; then
      export SOFTWARE_ONLY=1
   fi

   if [[ $LOCAL_MESA ]]; then
      pushd $LOCAL_MESA
      mkdir -p build  && \
      meson build/ && \
      meson configure build/ -Dprefix=/usr/local -Dplatforms=x11,wayland -Ddri-drivers= -Dgallium-drivers=swrast,virgl,radeonsi,iris -Dbuildtype=debugoptimized -Dllvm=true -Dglx=dri -Dgallium-vdpau=false -Dgallium-va=false -Dvulkan-drivers=[] -Dlibdir=lib && \
      ninja -C build/ install -j $NUM_THREADS
      if [ $? -ne 0 ]; then
        meson setup --wipe build/
        ninja -C build/ install -j $NUM_THREADS || exit 1
      fi
      popd
   fi

   rm -rf ./results/
   mkdir -p ./results/

   mkdir -p build
   if [ "x$use_clang_fuzzer" = "x1" ]; then
      export CC=clang
      export FUZZER=-Dfuzzer=true
   fi

   if [ "x$use_trace_stderr" = "x1" ]; then
       export TRACING=-Dtracing=stderr
   fi

   if [ "x$use_venus" = "x1" ]; then
       export VENUS=-Dvenus-experimental=true
   fi

   pwd | grep virglrenderer >/dev/null || pushd /virglrenderer && pushd $(pwd)
   meson build/ -Dprefix=/usr/local -Ddebug=true -Dtests=true --fatal-meson-warnings $FUZZER $TRACING $VENUS
   ninja -C build -j$NUM_THREADS install
   popd
}

run_make_check_meson()
{
   run_setup meson
   (
      mkdir -p ./results/make_check_meson
      VRENDTEST_USE_EGL_SURFACELESS=1 ninja -Cbuild -j$NUM_THREADS test
      RET=$?
      cp ./build/meson-logs/testlog.txt ./results/make_check_meson/
      return $RET
   )
}

run_make_check_clang_fuzzer()
{
   run_setup meson fuzzer
   (
      mkdir -p ./results/make_check_clang_fuzzer
      pushd ./build
      VRENDTEST_USE_EGL_SURFACELESS=1 ninja -j$NUM_THREADS test
      RET=$?
      cp ./meson-logs/testlog.txt ../results/make_check_clang_fuzzer/
      popd
      return $RET
   )
}

run_make_check_trace_stderr()
{
   run_setup meson trace_stderr
   (
      mkdir -p ./results/make_check_trace_stderr
      pushd ./build
      VRENDTEST_USE_EGL_SURFACELESS=1 ninja -j$NUM_THREADS test
      RET=$?
      cp ./meson-logs/testlog.txt ../results/make_check_trace_stderr/
      popd
      return $RET
   )
}

run_make_check_venus()
{
   run_setup meson venus
   (
      mkdir -p ./results/make_check_venus
      VRENDTEST_USE_EGL_SURFACELESS=1 ninja -Cbuild -j$NUM_THREADS test
      RET=$?
      cp ./build/meson-logs/testlog.txt ./results/make_check_venus/
      return $RET
   )
}

run_deqp()
{
   local retval=0
   run_setup meson
   OGL_BACKEND="$1"
   SUITE="$2"

   if [ "$SUITE" = "gl" ]; then
      TEST_SUITE="--gl30 --gl31 --gl32"
   fi

   if [ "$SUITE" = "gles" ]; then
      TEST_SUITE="--gles2 --gles3 --gles31"
   fi

   if [ "$SUITE" = "gles2" ]; then
      TEST_SUITE="--gles2"
   fi

   if [ "$SUITE" = "gles3" ]; then
      TEST_SUITE="--gles3"
   fi

   if [ "$SUITE" = "gles31" ]; then
      TEST_SUITE="--gles31"
   fi
   
   BACKENDS=""
   if [[ -z "$HARDWARE_ONLY" ]]; then
      BACKENDS="${BACKENDS} --backend vtest-llvmpipe"
   fi

   if [[ -z "$SOFTWARE_ONLY" ]]; then
      BACKENDS="${BACKENDS} --backend vtest-gpu"
   fi

   pwd | grep virglrenderer >/dev/null || pushd /virglrenderer/ci && pushd ci
   ./run_test_suite.sh --deqp ${TEST_SUITE} \
      --host-${OGL_BACKEND} \
      ${BACKENDS}
   retval=$?
   popd

   return $retval
}

run_piglit()
{
   local retval=0
   run_setup meson

   OGL_BACKEND="$1"

   BACKENDS=""
   if [[ -z "$HARDWARE_ONLY" ]]; then
      BACKENDS="${BACKENDS} --backend vtest-llvmpipe"
   fi
   
   if [[ -z "$SOFTWARE_ONLY" ]]; then
      BACKENDS="${BACKENDS} --backend vtest-gpu"
   fi

   pwd | grep virglrenderer >/dev/null || pushd /virglrenderer/ci && pushd ci
   ./run_test_suite.sh --piglit --gles2 --gles3 \
      --host-${OGL_BACKEND} \
      ${BACKENDS}
   retval=$?
   popd

   return $retval
}

parse_input()
{
   RET=0
   while  [ -n "$1" ]; do
      echo ""

      case $1 in
         --make-check-meson)
         run_make_check_meson
         ;;

         --make-check-clang-fuzzer)
         run_make_check_clang_fuzzer
         ;;

         --make-check-trace-stderr)
         run_make_check_trace_stderr
         ;;

         --make-check-venus)
         run_make_check_venus
         ;;

         --deqp-gl-gl-tests)
         run_deqp gl gl
         ;;

         --deqp-gl-gles-tests)
         run_deqp gl gles
         ;;

         --deqp-gl-gles2-tests)
         run_deqp gl gles2
         ;;

         --deqp-gl-gles3-tests)
         run_deqp gl gles3
         ;;

         --deqp-gl-gles31-tests)
         run_deqp gl gles31
         ;;
         
         --deqp-gles-gl-tests)
         run_deqp gles gl
         ;;

         --deqp-gles-gles-tests)
         run_deqp gles gles
         ;;

         --deqp-gles-gles2-tests)
         run_deqp gles gles2
         ;;

         --deqp-gles-gles3-tests)
         run_deqp gles gles3
         ;;

         --deqp-gles-gles31-tests)
         run_deqp gles gles31
         ;;
         
         --piglit-gl)
         run_piglit gl
         ;;

         --piglit-gles)
         run_piglit gles
         ;;

         *)
         echo "Unknown test option $1"
         exit 1
      esac

      if [ $? -ne 0 ]; then
         RET=1
      fi

      shift
   done

   exit $RET
}

parse_input $@
