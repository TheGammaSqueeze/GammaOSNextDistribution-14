#! /bin/bash
#
# Copyright (C) 2015 The Android Open Source Project
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

set -e

. "$(dirname $0)/buildbot-utils.sh"

shopt -s failglob

if [ ! -d art ]; then
  msgerror "Script needs to be run at the root of the Android tree"
  exit 1
fi

# Logic for setting out_dir from build/make/core/envsetup.mk:
if [[ -z $OUT_DIR ]]; then
  if [[ -z $OUT_DIR_COMMON_BASE ]]; then
    out_dir=out
  else
    out_dir=${OUT_DIR_COMMON_BASE}/${PWD##*/}
  fi
else
  out_dir=${OUT_DIR}
fi

# On master-art, we need to copy ART-local riscv64 prebuilts for conscrypt and
# statsd into their own repositories, as mainline doesn't support riscv64 yet.
# Android.bp file changes are stored as patch files which need to be applied
# afterwards.
#
# TODO(b/286551985): Remove this after riscv64 support is added to mainline.
if [[ $TARGET_ARCH = "riscv64" && ! ( -d frameworks/base ) ]]; then
  msginfo "Copying prebuilt dependencies for riscv64"
  cp -u -r prebuilts/runtime/mainline/local_riscv64/prebuilts/module_sdk/conscrypt \
    prebuilts/module_sdk
  cp -u -r prebuilts/runtime/mainline/local_riscv64/prebuilts/module_sdk/StatsD \
    prebuilts/module_sdk
  for patch_file in $(find prebuilts/module_sdk -name Android.bp.patch) ; do
    bp_file=${patch_file%.patch}
    # Only apply the patches if they haven't been applied already. Assume the
    # patch files contain the bug number, and look for that.
    if grep -q b/286551985 $bp_file ; then
      msginfo "Patch for riscv64 already present in $bp_file"
    else
      patch -f $bp_file < $patch_file
    fi
  done
fi

java_libraries_dir=${out_dir}/target/common/obj/JAVA_LIBRARIES
common_targets="vogar core-tests core-ojtests apache-harmony-jdwp-tests-hostdex jsr166-tests libartpalette-system mockito-target"
# These build targets have different names on device and host.
specific_targets="libjavacoretests libwrapagentproperties libwrapagentpropertiesd"
build_host="no"
build_target="no"
installclean="no"
skip_run_tests_build="no"
j_arg="-j$(nproc)"
showcommands=
make_command=

while true; do
  if [[ "$1" == "--host" ]]; then
    build_host="yes"
    shift
  elif [[ "$1" == "--target" ]]; then
    build_target="yes"
    shift
  elif [[ "$1" == "--installclean" ]]; then
    installclean="yes"
    shift
  elif [[ "$1" == "--skip-run-tests-build" ]]; then
    skip_run_tests_build="yes"
    shift
  elif [[ "$1" == -j* ]]; then
    j_arg=$1
    shift
  elif [[ "$1" == "--showcommands" ]]; then
    showcommands="showcommands"
    shift
  elif [[ "$1" == "--dist" ]]; then
    common_targets="$common_targets dist"
    shift
  elif [[ "$1" == "" ]]; then
    break
  else
    msgerror "Unknown options: $@"
    exit 1
  fi
done

# If neither was selected, build both by default.
if [[ $build_host == "no" ]] && [[ $build_target == "no" ]]; then
  build_host="yes"
  build_target="yes"
fi

implementation_libs=(
  "heapprofd_client_api"
  "libandroid_runtime_lazy"
  "libartpalette-system"
  "libbinder"
  "libbinder_ndk"
  "libcutils"
  "libutils"
  "libvndksupport"
)

if [ -d frameworks/base ]; then
  # In full manifest branches, build the implementation libraries from source
  # instead of using prebuilts.
  common_targets="$common_targets ${implementation_libs[*]}"
else
  # Necessary to build successfully in master-art.
  extra_args="SOONG_ALLOW_MISSING_DEPENDENCIES=true"
  # Switch the build system to unbundled mode in the reduced manifest branch.
  extra_args="$extra_args TARGET_BUILD_UNBUNDLED=true"
fi

apexes=(
  "com.android.art.testing"
  "com.android.conscrypt"
  "com.android.i18n"
  "com.android.runtime"
  "com.android.tzdata"
  "com.android.os.statsd"
)

make_command="build/soong/soong_ui.bash --make-mode $j_arg $extra_args $showcommands $common_targets"
if [[ $build_host == "yes" ]]; then
  make_command+=" build-art-host-gtests"
  test $skip_run_tests_build == "yes" || make_command+=" build-art-host-run-tests"
  make_command+=" dx-tests junit-host libjdwp-host"
  for LIB in ${specific_targets} ; do
    make_command+=" $LIB-host"
  done
fi
if [[ $build_target == "yes" ]]; then
  if [[ -z "${ANDROID_PRODUCT_OUT}" ]]; then
    msgerror 'ANDROID_PRODUCT_OUT environment variable is empty; did you forget to run `lunch`?'
    exit 1
  fi
  make_command+=" build-art-target-gtests"
  test $skip_run_tests_build == "yes" || make_command+=" build-art-target-run-tests"
  make_command+=" debuggerd sh su toybox"
  # Indirect dependencies in the platform, e.g. through heapprofd_client_api.
  # These are built to go into system/lib(64) to be part of the system linker
  # namespace.
  make_command+=" libnetd_client-target libprocinfo libtombstoned_client libunwindstack"
  # Stubs for other APEX SDKs, for use by vogar. Referenced from DEVICE_JARS in
  # external/vogar/src/vogar/ModeId.java.
  # Note these go into out/target/common/obj/JAVA_LIBRARIES which isn't removed
  # by "m installclean".
  make_command+=" i18n.module.public.api.stubs conscrypt.module.public.api.stubs"
  # Targets required to generate a linker configuration for device within the
  # chroot environment. The *.libraries.txt targets are required by
  # the source linkerconfig but not included in the prebuilt one.
  make_command+=" linkerconfig conv_linker_config sanitizer.libraries.txt llndk.libraries.txt"
  # Additional targets needed for the chroot environment.
  make_command+=" event-log-tags"
  # Needed to extract prebuilt APEXes.
  make_command+=" deapexer"
  # Needed to generate the primary boot image for testing.
  make_command+=" generate-boot-image"
  # Data file needed by the `ArtExecTest.SetTaskProfiles` test.
  make_command+=" task_profiles.json"
  # Build/install the required APEXes.
  make_command+=" ${apexes[*]}"
  make_command+=" ${specific_targets}"
fi

if [[ $installclean == "yes" ]]; then
  msginfo "Perform installclean"
  ANDROID_QUIET_BUILD=true build/soong/soong_ui.bash --make-mode $extra_args installclean
  # The common java library directory is not cleaned up by installclean. Do that
  # explicitly to not overcache them in incremental builds.
  rm -rf $java_libraries_dir
else
  msgwarning "Missing --installclean argument to buildbot-build.sh"
  msgwarning "This is usually ok, but may cause rare odd failures."
  echo ""
fi

msginfo "Executing" "$make_command"
# Disable path restrictions to enable luci builds using vpython.
eval "$make_command"

if [[ $build_target == "yes" ]]; then
  if [[ -z "${ANDROID_HOST_OUT}" ]]; then
    msgwarning "ANDROID_HOST_OUT environment variable is empty; using $out_dir/host/linux-x86"
    ANDROID_HOST_OUT=$out_dir/host/linux-x86
  fi

  # Extract prebuilt APEXes.
  debugfs=$ANDROID_HOST_OUT/bin/debugfs_static
  fsckerofs=$ANDROID_HOST_OUT/bin/fsck.erofs
  for apex in ${apexes[@]}; do
    dir="$ANDROID_PRODUCT_OUT/system/apex/${apex}"
    apexbase="$ANDROID_PRODUCT_OUT/system/apex/${apex}"
    unset file
    if [ -f "${apexbase}.apex" ]; then
      file="${apexbase}.apex"
    elif [ -f "${apexbase}.capex" ]; then
      file="${apexbase}.capex"
    fi
    if [ -n "${file}" ]; then
      msginfo "Extracting APEX file:" "${file}"
      rm -rf $dir
      mkdir -p $dir
      $ANDROID_HOST_OUT/bin/deapexer --debugfs_path $debugfs --fsckerofs_path $fsckerofs \
        extract $file $dir
    fi
  done

  # Replace stub libraries with implementation libraries: because we do chroot
  # testing, we need to install an implementation of the libraries (and cannot
  # rely on the one already installed on the device, if the device is post R and
  # has it).
  if [ -d prebuilts/runtime/mainline/platform/impl -a ! -d frameworks/base ]; then
    if [[ $TARGET_ARCH = arm* ]]; then
      arch32=arm
      arch64=arm64
    elif [[ $TARGET_ARCH = riscv64 ]]; then
      arch32=none # there is no 32-bit arch for RISC-V
      arch64=riscv64
    else
      arch32=x86
      arch64=x86_64
    fi
    for so in ${implementation_libs[@]}; do
      if [ -d "$ANDROID_PRODUCT_OUT/system/lib" -a $arch32 != none ]; then
        cmd="cp -p prebuilts/runtime/mainline/platform/impl/$arch32/${so}.so $ANDROID_PRODUCT_OUT/system/lib/${so}.so"
        msginfo "Executing" "$cmd"
        eval "$cmd"
      fi
      if [ -d "$ANDROID_PRODUCT_OUT/system/lib64" -a $arch64 != none ]; then
        cmd="cp -p prebuilts/runtime/mainline/platform/impl/$arch64/${so}.so $ANDROID_PRODUCT_OUT/system/lib64/${so}.so"
        msginfo "Executing" "$cmd"
        eval "$cmd"
      fi
    done
  fi

  # Create canonical name -> file name symlink in the symbol directory for the
  # Testing ART APEX.
  #
  # This mimics the logic from `art/Android.mk`. We made the choice not to
  # implement this in `art/Android.mk`, as the Testing ART APEX is a test artifact
  # that should never ship with an actual product, and we try to keep it out of
  # standard build recipes
  #
  # TODO(b/141004137, b/129534335): Remove this, expose the Testing ART APEX in
  # the `art/Android.mk` build logic, and add absence checks (e.g. in
  # `build/make/core/main.mk`) to prevent the Testing ART APEX from ending up in a
  # system image.
  target_out_unstripped="$ANDROID_PRODUCT_OUT/symbols"
  link_name="$target_out_unstripped/apex/com.android.art"
  link_command="mkdir -p $(dirname "$link_name") && ln -sf com.android.art.testing \"$link_name\""
  msginfo "Executing" "$link_command"
  eval "$link_command"

  # Temporary fix for libjavacrypto.so dependencies in libcore and jvmti tests (b/147124225).
  conscrypt_dir="$ANDROID_PRODUCT_OUT/system/apex/com.android.conscrypt"
  conscrypt_libs="libjavacrypto.so libcrypto.so libssl.so"
  if [ ! -d "${conscrypt_dir}" ]; then
    msgerror "Missing conscrypt APEX in build output: ${conscrypt_dir}"
    exit 1
  fi
  if [ ! -f "${conscrypt_dir}/javalib/conscrypt.jar" ]; then
    msgerror "Missing conscrypt jar in build output: ${conscrypt_dir}"
    exit 1
  fi
  for l in lib lib64; do
    if [ ! -d "$ANDROID_PRODUCT_OUT/system/$l" ]; then
      continue
    fi
    for so in $conscrypt_libs; do
      src="${conscrypt_dir}/${l}/${so}"
      dst="$ANDROID_PRODUCT_OUT/system/${l}/${so}"
      if [ "${src}" -nt "${dst}" ]; then
        cmd="cp -p \"${src}\" \"${dst}\""
        msginfo "Executing" "$cmd"
        eval "$cmd"
      fi
    done
  done

  # TODO(b/159355595): Ensure there is a tzdata in system to avoid warnings on
  # stderr from Bionic.
  if [ ! -f $ANDROID_PRODUCT_OUT/system/usr/share/zoneinfo/tzdata ]; then
    mkdir -p $ANDROID_PRODUCT_OUT/system/usr/share/zoneinfo
    cp $ANDROID_PRODUCT_OUT/system/apex/com.android.tzdata/etc/tz/tzdata \
      $ANDROID_PRODUCT_OUT/system/usr/share/zoneinfo/tzdata
  fi

  # Create system symlinks for the Runtime APEX. Normally handled by
  # installSymlinkToRuntimeApex in soong/cc/binary.go, but we have to replicate
  # it here since we don't run the install rules for the Runtime APEX.
  for b in linker{,_asan}{,64}; do
    msginfo "Symlinking" "/apex/com.android.runtime/bin/$b to /system/bin"
    ln -sf /apex/com.android.runtime/bin/$b $ANDROID_PRODUCT_OUT/system/bin/$b
  done
  for d in $ANDROID_PRODUCT_OUT/system/apex/com.android.runtime/lib{,64}/bionic; do
    if [ -d $d ]; then
      for p in $d/*; do
        lib_dir=$(expr $p : '.*/\(lib[0-9]*\)/.*')
        lib_file=$(basename $p)
        src=/apex/com.android.runtime/${lib_dir}/bionic/${lib_file}
        dst=$ANDROID_PRODUCT_OUT/system/${lib_dir}/${lib_file}
        msginfo "Symlinking" "$src into /system/${lib_dir}"
        mkdir -p $(dirname $dst)
        ln -sf $src $dst
      done
    fi
  done

  # Create linker config files. We run linkerconfig on host to avoid problems
  # building it statically for device in an unbundled tree.

  # temporary root for linkerconfig
  linkerconfig_root=$ANDROID_PRODUCT_OUT/art_linkerconfig_root

  rm -rf $linkerconfig_root

  # Linkerconfig reads files from /system/etc
  mkdir -p $linkerconfig_root/system
  cp -r $ANDROID_PRODUCT_OUT/system/etc $linkerconfig_root/system

  # Use our smaller public.libraries.txt that contains only the public libraries
  # pushed to the chroot directory.
  cp $ANDROID_BUILD_TOP/art/tools/public.libraries.buildbot.txt \
    $linkerconfig_root/system/etc/public.libraries.txt

  # For linkerconfig to pick up the APEXes correctly we need to make them
  # available in $linkerconfig_root/apex.
  mkdir -p $linkerconfig_root/apex
  for apex in ${apexes[@]}; do
    src="$ANDROID_PRODUCT_OUT/system/apex/${apex}"
    if [[ $apex == com.android.art.* ]]; then
      dst="$linkerconfig_root/apex/com.android.art"
    else
      dst="$linkerconfig_root/apex/${apex}"
    fi
    msginfo "Copying APEX directory" "from $src to $dst"
    rm -rf $dst
    cp -r $src $dst
  done

  # Linkerconfig also looks at /apex/apex-info-list.xml to check for system APEXes.
  apex_xml_file=$linkerconfig_root/apex/apex-info-list.xml
  msginfo "Creating" "$apex_xml_file"
  cat <<EOF > $apex_xml_file
<?xml version="1.0" encoding="utf-8"?>
<apex-info-list>
EOF
  for apex in ${apexes[@]}; do
    [[ $apex == com.android.art.* ]] && apex=com.android.art
    cat <<EOF >> $apex_xml_file
    <apex-info moduleName="${apex}" modulePath="/system/apex/${apex}.apex" preinstalledModulePath="/system/apex/${apex}.apex" versionCode="1" versionName="" isFactory="true" isActive="true">
    </apex-info>
EOF
  done
  cat <<EOF >> $apex_xml_file
</apex-info-list>
EOF

  system_linker_config_pb=$linkerconfig_root/system/etc/linker.config.pb
  # This list needs to be synced with provideLibs in system/etc/linker.config.pb
  # in the targeted platform image.
  # TODO(b/186649223): Create a prebuilt for it in platform-mainline-sdk.
  system_provide_libs=(
    heapprofd_client_api.so
    libEGL.so
    libGLESv1_CM.so
    libGLESv2.so
    libGLESv3.so
    libOpenMAXAL.so
    libOpenSLES.so
    libRS.so
    libaaudio.so
    libadbd_auth.so
    libadbd_fs.so
    libamidi.so
    libandroid.so
    libandroid_net.so
    libartpalette-system.so
    libbinder_ndk.so
    libc.so
    libcamera2ndk.so
    libcgrouprc.so
    libclang_rt.asan-i686-android.so
    libclang_rt.asan-x86_64-android.so
    libdl.so
    libdl_android.so
    libft2.so
    libincident.so
    libjnigraphics.so
    liblog.so
    libm.so
    libmediametrics.so
    libmediandk.so
    libnativewindow.so
    libneuralnetworks_packageinfo.so
    libselinux.so
    libstdc++.so
    libsync.so
    libvndksupport.so
    libvulkan.so
    libz.so
  )

  msginfo "Encoding linker.config.json" "to $system_linker_config_pb"
  $ANDROID_HOST_OUT/bin/conv_linker_config proto -s $ANDROID_BUILD_TOP/system/core/rootdir/etc/linker.config.json -o $system_linker_config_pb
  $ANDROID_HOST_OUT/bin/conv_linker_config append -s $system_linker_config_pb -o $system_linker_config_pb --key "provideLibs" --value "${system_provide_libs[*]}"

  # To avoid warnings from linkerconfig when it checks following two partitions
  mkdir -p $linkerconfig_root/product
  mkdir -p $linkerconfig_root/system_ext

  platform_version=$(build/soong/soong_ui.bash --dumpvar-mode PLATFORM_VERSION)
  linkerconfig_out=$ANDROID_PRODUCT_OUT/linkerconfig
  msginfo "Generating linkerconfig" "in $linkerconfig_out"
  rm -rf $linkerconfig_out
  mkdir -p $linkerconfig_out
  $ANDROID_HOST_OUT/bin/linkerconfig --target $linkerconfig_out --root $linkerconfig_root
fi
