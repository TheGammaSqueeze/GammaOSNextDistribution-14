#!/bin/bash
#
# lws_config{,_private}.h are created by running cmake
# We are not allowed to use cmake to do the Android build
# These files for now have to be created offline
#
debug_mode="false"
if [[ $1 == "debug" ]] || [[ $1 == "-d" ]]; then
  debug_mode="true"
fi

function remove_trailing_slash {
  if [[ $1 == "/" ]]; then
    echo $i
  else
    echo ${1%/}
  fi
}

set -o errexit
MY_LOCATION="$( cd -P "$( dirname "$SOURCE" )" >/dev/null 2>&1 && pwd )"
MY_LOCATION=$(remove_trailing_slash $MY_LOCATION)
CMAKE_LIST_FILE="$MY_LOCATION/CMakeLists.txt"

ANDROID_ROOTDIR="$(realpath $MY_LOCATION/../..)"
ANDROID_ROOTDIR=$(remove_trailing_slash $ANDROID_ROOTDIR)

BUILD_DIR=$MY_LOCATION/build_dir

# included by CMakeLists.txt
CMAKE_ANDROID_FILE="$MY_LOCATION/CMakeAndroidLists.txt"

# used to generate CMAKE_ANDROID_FILE
CMAKE_ANDROID_FILE_TEMPLATE="$MY_LOCATION/CMakeAndroidLists.txt.template"

function find_lib_path() {
  # find the library in the out directory, and returns the directory's absolute path
  #
  # $1 -> libname
  # $2 -> optional, lib file extension
  local libext=${2:-".a"}
  local libname="$1$libext"
  local res=`find ${ANDROID_ROOTDIR}/out -type f | egrep "$libname$" | egrep "$(uname -m)" | egrep -i "$(uname -s)"`
  echo $res
}

function sed_in_cmake_android() {
  # replace in-place 'from' with 'to' in CMAKE_ANDROID_FILE
  #
  # $1: from
  # $2: to
  # $3: CMAKE_ANDROID_FILE
  #
  local cmake_android_file=${3:-"${CMAKE_ANDROID_FILE}"}
  local from="____""$1""____"
  local to=$(echo $2 | sed -r 's/\//\\\//g')
  if [[ $debug_mode == "true" ]]; then
    echo "${FUNCNAME[0]} replaces in $cmake_android_file "
    echo "     " $from
    echo "with " $to
  fi
  sed -i "s/$from/$to/g" $cmake_android_file
}

function build_prerequisites() {
  local modules=("$@")
  (
  pushd ${ANDROID_ROOTDIR}
  source ${ANDROID_ROOTDIR}/build/envsetup.sh
  for mod in "${modules[@]}"; do
    mmm $mod
  done
  popd
  )
}

function create_android_list() {
  #
  # by filling out the form in CMAKE_ANDROID_FILE_TEMPLATE,
  # create CMAKE_ANDROID_FILE that is included in CMakeLists.txt
  #
  # The CMAKE_ANDROID_FILE defines library/include directories for
  # libraries provided by somewhere in Android Root
  #
  rm -f ${CMAKE_ANDROID_FILE} || true
  cp -f ${CMAKE_ANDROID_FILE_TEMPLATE} ${CMAKE_ANDROID_FILE}
  sed_in_cmake_android "android_src_root_dir" ${ANDROID_ROOTDIR} ${CMAKE_ANDROID_FILE}
  # libraries that libwebsockets would depend on
  local libs=("libssl" "libcrypto" "libcap")
  # Android components that provide the "libs" above
  local components=("external/boringssl" "external/libcap")
  build_prerequisites "${components[@]}"
  for lib in ${libs[@]}; do
    local libfile_path=$(find_lib_path $lib)
    local libpath=$(dirname ${libfile_path})
    libpath=$(remove_trailing_slash $libpath)
    str_to_replae="build_""$lib""_dir"
    sed_in_cmake_android "$str_to_replae" $libpath ${CMAKE_ANDROID_FILE}
  done
}

# create CMakeAndroidLists.txt
create_android_list

extension=$(date | md5sum | cut -d ' ' -f 1)
cp -f $CMAKE_LIST_FILE $CMAKE_LIST_FILE.$extension

# make CMakeLists.txt buildable
sed -i 's/DO-NOT-BUILD-ME-AS-IS//g' ${CMAKE_ANDROID_FILE}

# replace CHECK_FUNCTION_EXISTS with check_function_exists
sed -i 's/CHECK_FUNCTION_EXISTS/check_function_exists/g' $CMAKE_LIST_FILE

# run cmake to configure
rm -fr $BUILD_DIR || true
LIBWEBSOCKETS_SRC_ROOT=$MY_LOCATION
mkdir -p $BUILD_DIR

if [[ $debug_mode == "true" ]]; then
  pushd $BUILD_DIR
  cmake VERBOSE=1 ..
  popd
else
  (pushd $BUILD_DIR > /dev/null 2>&1; cmake .. > /dev/null 2>&1; popd > /dev/null 2>&1)
fi

cp -f $BUILD_DIR/lws_config.h $LIBWEBSOCKETS_SRC_ROOT/include
cp -f $BUILD_DIR/lws_config_private.h $LIBWEBSOCKETS_SRC_ROOT/include

# clean up
if [[ $debug_mode != "true" ]]; then
  rm -fr $BUILD_DIR || true
  rm -f $CMAKE_ANDROID_FILE || true
  mv -f $CMAKE_LIST_FILE.$extension $CMAKE_LIST_FILE
fi
