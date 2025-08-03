#!/bin/bash

# Note: not intended to be invoked directly, see rebuild.sh.
#
# Rebuilds Crosvm and its dependencies from a clean state.

: ${TOOLS_DIR:="$(pwd)/tools"}

# Stable is usually too old for crosvm, but make sure you bump this
# up as far as you can each time this script is touched..
RUST_TOOLCHAIN_VER=1.65.0

setup_env() {
  : ${SOURCE_DIR:="$(pwd)/source"}
  : ${WORKING_DIR:="$(pwd)/working"}
  : ${CUSTOM_MANIFEST:=""}

  ARCH="$(uname -m)"
  : ${OUTPUT_DIR:="$(pwd)/${ARCH}-linux-gnu"}
  OUTPUT_BIN_DIR="${OUTPUT_DIR}/bin"
  OUTPUT_ETC_DIR="${OUTPUT_DIR}/etc"
  OUTPUT_SECCOMP_DIR="${OUTPUT_ETC_DIR}/seccomp"

  export PATH="${PATH}:${TOOLS_DIR}:${HOME}/.local/bin"
  export PKG_CONFIG_PATH="${WORKING_DIR}/usr/lib/pkgconfig"
}

set -e
set -x

fatal_echo() {
  echo "$@"
  exit 1
}

prepare_cargo() {
  echo Setting up cargo...
  cd
  rm -rf .cargo
  # Sometimes curl hangs. When it does, retry
  retry curl -L \
    --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs -o rustup-init
  chmod +x rustup-init
  ./rustup-init -y --no-modify-path --default-toolchain ${RUST_TOOLCHAIN_VER}
  source $HOME/.cargo/env
  if [[ -n "$1" ]]; then
    rustup target add "$1"
  fi
  rm -f rustup-init

  if [[ -n "$1" ]]; then
  cat >>~/.cargo/config <<EOF
[target.$1]
linker = "${1/-unknown-/-}"
EOF
  fi
}

install_packages() {
  echo Installing packages...

  sudo dpkg --add-architecture arm64
  sudo apt-get update
  sudo apt-get install -y \
      "$@" \
      autoconf \
      automake \
      build-essential \
      curl \
      doxygen \
      g++ \
      gcc \
      git \
      graphviz \
      libcap-dev \
      libegl1-mesa-dev \
      libfdt-dev \
      libgl1-mesa-dev \
      libgles2-mesa-dev \
      libpciaccess-dev \
      libssl-dev \
      libtool \
      libusb-1.0-0-dev \
      libwayland-bin \
      libwayland-dev \
      libxml2-dev \
      make \
      nasm \
      ninja-build \
      pkg-config \
      protobuf-compiler \
      python3 \
      python3-pip \
      texinfo \
      wayland-protocols \
      xmlto \
      xutils-dev # Needed to pacify autogen.sh for libepoxy
  mkdir -p "${TOOLS_DIR}"

  # Repo needs python3 but python-is-python3 package not available:
  sudo ln -s -f /usr/bin/python3 /usr/bin/python

  curl https://storage.googleapis.com/git-repo-downloads/repo > "${TOOLS_DIR}/repo"
  chmod a+x "${TOOLS_DIR}/repo"

  # Gfxstream needs a new-ish version of CMake
  mkdir -p "${TOOLS_DIR}/cmake"
  cd "${TOOLS_DIR}/cmake"
  curl -O -L https://cmake.org/files/v3.22/cmake-3.22.1-linux-$(uname -m).sh
  chmod +x cmake-3.22.1-linux-$(uname -m).sh
  sudo ./cmake-3.22.1-linux-$(uname -m).sh --skip-license --exclude-subdir --prefix=/usr/local
  cmake --version

  # Meson getting started guide mentions that the distro version is frequently
  # outdated and recommends installing via pip.
  pip3 install --no-warn-script-location meson

  # Tools for building gfxstream
  pip3 install absl-py
  pip3 install urlfetch

  case "$(uname -m)" in
    aarch64)
      prepare_cargo
      ;;
    x86_64)
      # Cross-compilation is x86_64 specific
      sudo apt install -y crossbuild-essential-arm64
      prepare_cargo aarch64-unknown-linux-gnu
      ;;
  esac
}

retry() {
  for i in $(seq 5); do
    "$@" && return 0
    sleep 1
  done
  return 1
}

fetch_source() {
  echo "Fetching source..."

  mkdir -p "${SOURCE_DIR}"
  cd "${SOURCE_DIR}"

  if ! git config user.name; then
    git config --global user.name "AOSP Crosvm Builder"
    git config --global user.email "nobody@android.com"
    git config --global color.ui false
  fi

  if [[ -z "${CUSTOM_MANIFEST}" ]]; then
    # Building Crosvm currently depends using Chromium's directory scheme for subproject
    # directories ('third_party' vs 'external').
    fatal_echo "CUSTOM_MANIFEST must be provided. You most likely want to provide a full path to" \
               "a copy of device/google/cuttlefish_vmm/manifest.xml."
  fi

  cp ${CUSTOM_MANIFEST} manifest.xml
  repo init --depth=1 -q -u https://android.googlesource.com/platform/manifest -m $PWD/manifest.xml
  repo sync
}

prepare_source() {
  if [ "$(ls -A $SOURCE_DIR)" ]; then
    echo "${SOURCE_DIR} is non empty. Run this from an empty directory if you wish to fetch the source." 1>&2
    exit 2
  fi
  fetch_source
}

resync_source() {
  echo "Deleting source directory..."
  rm -rf "${SOURCE_DIR}/.*"
  rm -rf "${SOURCE_DIR}/*"
  fetch_source
}

# $1 = installed library filename
debuglink() {
  objcopy --only-keep-debug "${OUTPUT_BIN_DIR}/$1" "${OUTPUT_BIN_DIR}/$1.debug"
  strip --strip-debug "${OUTPUT_BIN_DIR}/$1"
  cd "${OUTPUT_BIN_DIR}"
  objcopy --add-gnu-debuglink="$1.debug" "$1"
  cd -
}

compile_libdrm() {
  cd "${SOURCE_DIR}/external/libdrm"

  # Ensure pkg-config file supplies rpath to dependent libraries
  grep "install_rpath" meson.build || \
    sed -i "s|install : true,|install : true, install_rpath : '\$ORIGIN',|" meson.build

  meson build \
    --libdir="${WORKING_DIR}/usr/lib" \
    --prefix="${WORKING_DIR}/usr" \
    -Damdgpu=false \
    -Dfreedreno=false \
    -Dintel=false \
    -Dlibkms=false \
    -Dnouveau=false \
    -Dradeon=false \
    -Dvc4=false \
    -Dvmwgfx=false

  cd build
  ninja install

  cp -a "${WORKING_DIR}"/usr/lib/libdrm.so* "${OUTPUT_BIN_DIR}"
  debuglink libdrm.so.2.4.0
}

compile_minijail() {
  echo "Compiling Minijail..."

  cd "${SOURCE_DIR}/platform/minijail"

  if ! grep '^# cuttlefish_vmm-rebuild-mark' Makefile; then
    # Link minijail-sys rust crate dynamically to minijail
    sed -i '/BUILD_STATIC_LIBS/d' rust/minijail-sys/build.rs
    sed -i 's,static=minijail.pic,dylib=minijail,' rust/minijail-sys/build.rs

    # Use Android prebuilt C files instead of generating them
    sed -i 's,\(.*\.gen\.c: \),DISABLED_\1,' Makefile
    cat >>Makefile <<EOF
libconstants.gen.c: \$(SRC)/linux-x86/libconstants.gen.c
	@cp \$< \$@
libsyscalls.gen.c: \$(SRC)/linux-x86/libsyscalls.gen.c
	@cp \$< \$@
# cuttlefish_vmm-rebuild-mark
EOF
  fi

  make -j OUT="${WORKING_DIR}"
  cp "${WORKING_DIR}"/libminijail.so "${WORKING_DIR}"/usr/lib

  cp -a "${WORKING_DIR}"/usr/lib/libminijail.so "${OUTPUT_BIN_DIR}"
  debuglink libminijail.so
}

compile_minigbm() {
  echo "Compiling Minigbm..."

  cd "${SOURCE_DIR}/platform/minigbm"

  # Minigbm's package config file has a default hard-coded path. Update here so
  # that dependent packages can find the files.
  sed -i "s|prefix=/usr\$|prefix=${WORKING_DIR}/usr|" gbm.pc

  # The gbm used by upstream linux distros is not compatible with crosvm, which must use Chrome OS's
  # minigbm.
  local cpp_flags=(-I/working/usr/include -I/working/usr/include/libdrm)
  local ld_flags=(-Wl,-soname,libgbm.so.1 -Wl,-rpath,\\\$\$ORIGIN -L/working/usr/lib)
  local make_flags=()
  local minigbm_drv=(${MINIGBM_DRV})
  for drv in "${minigbm_drv[@]}"; do
    cpp_flags+=(-D"DRV_${drv}")
    make_flags+=("DRV_${drv}"=1)
  done

  make -j install \
    "${make_flags[@]}" \
    CPPFLAGS="${cpp_flags[*]}" \
    DESTDIR="${WORKING_DIR}" \
    LDFLAGS="${ld_flags[*]}" \
    OUT="${WORKING_DIR}"

  cp -a "${WORKING_DIR}"/usr/lib/libminigbm.so* "${OUTPUT_BIN_DIR}"
  cp -a "${WORKING_DIR}"/usr/lib/libgbm.so* "${OUTPUT_BIN_DIR}"
  debuglink libminigbm.so.1.0.0
}

compile_epoxy() {
  cd "${SOURCE_DIR}/third_party/libepoxy"

  meson build \
    --libdir="${WORKING_DIR}/usr/lib" \
    --prefix="${WORKING_DIR}/usr" \
    -Dglx=no \
    -Dx11=false \
    -Degl=yes

  cd build
  ninja install

  cp -a "${WORKING_DIR}"/usr/lib/libepoxy.so* "${OUTPUT_BIN_DIR}"
  debuglink libepoxy.so.0.0.0
}

compile_virglrenderer() {
  echo "Compiling VirglRenderer..."

  # Note: depends on libepoxy
  cd "${SOURCE_DIR}/third_party/virglrenderer"

  # Meson needs to have dependency information for header lookup.
  sed -i "s|cc.has_header('epoxy/egl.h')|cc.has_header('epoxy/egl.h', dependencies: epoxy_dep)|" meson.build

  # Ensure pkg-config file supplies rpath to dependent libraries
  grep "install_rpath" src/meson.build || \
    sed -i "s|install : true|install : true, install_rpath : '\$ORIGIN'|" src/meson.build

  meson build \
    --libdir="${WORKING_DIR}/usr/lib" \
    --prefix="${WORKING_DIR}/usr" \
    -Dplatforms=egl \
    -Dminigbm_allocation=false

  cd build
  ninja install

  cp -a "${WORKING_DIR}"/usr/lib/libvirglrenderer.so* "${OUTPUT_BIN_DIR}"
  debuglink libvirglrenderer.so.1.7.7
}

compile_libffi() {
  cd "${SOURCE_DIR}/third_party/libffi"

  ./autogen.sh
  ./configure \
    --prefix="${WORKING_DIR}/usr" \
    --libdir="${WORKING_DIR}/usr/lib"
  make && make check && make install

  cp -a "${WORKING_DIR}"/usr/lib/libffi.so* "${OUTPUT_BIN_DIR}"
  debuglink libffi.so.7.1.0
}

compile_wayland() {
  cd "${SOURCE_DIR}/third_party/wayland"

  # Need to figure out the right way to pass this down...
  sed -i "s|install: true\$|install: true, install_rpath : '\$ORIGIN'|" src/meson.build

  meson build \
    --libdir="${WORKING_DIR}/usr/lib" \
    --prefix="${WORKING_DIR}/usr"
  ninja -C build/ install

  cp -a "${WORKING_DIR}"/usr/lib/libwayland-client.so* "${OUTPUT_BIN_DIR}"
  debuglink libwayland-client.so.0.3.0
}

compile_gfxstream() {
  echo "Compiling gfxstream..."

  local dist_dir="${SOURCE_DIR}/hardware/google/gfxstream/build"
  [ -d "${dist_dir}" ] && rm -rf "${dist_dir}"
  mkdir "${dist_dir}"
  cd "${dist_dir}"

  cmake .. \
    -G Ninja \
    -DBUILD_GRAPHICS_DETECTOR=ON \
    -DDEPENDENCY_RESOLUTION=AOSP

  ninja

  chmod +x "${dist_dir}"/gfxstream_graphics_detector
  cp -a "${dist_dir}"/gfxstream_graphics_detector "${OUTPUT_BIN_DIR}"
  debuglink gfxstream_graphics_detector

  chmod +x "${dist_dir}"/libgfxstream_backend.so
  cp -a "${dist_dir}"/libgfxstream_backend.so "${WORKING_DIR}"/usr/lib
  cp -a "${WORKING_DIR}"/usr/lib/libgfxstream_backend.so "${OUTPUT_BIN_DIR}"
  debuglink libgfxstream_backend.so
}

compile_crosvm() {
  echo "Compiling Crosvm..."

  source "${HOME}/.cargo/env"

  # Workaround for aosp/1412815
  cd "${SOURCE_DIR}/platform/crosvm/protos/src"
  if ! grep '^mod generated {$' lib.rs; then
    cat >>lib.rs <<EOF
mod generated {
    include!(concat!(env!("OUT_DIR"), "/generated.rs"));
}
EOF
  fi
  sed -i "s/pub use cdisk_spec_proto::cdisk_spec/pub use generated::cdisk_spec/" lib.rs

  cd "${SOURCE_DIR}/platform/crosvm"

  # Workaround for minijail-sys prepending -L/usr/lib/$arch dir
  # which breaks the preferred search path for libdrm.so
  sed -i '0,/pkg_config::Config::new().probe("libdrm")?;/{/pkg_config::Config::new().probe("libdrm")?;/d;}' rutabaga_gfx/build.rs

  # Workaround rutabaga build thinking it needs at later version of virglrenderer.
  sed -i 's/atleast_version("1.0.0")/atleast_version("0.10.0")/g' rutabaga_gfx/build.rs

  local crosvm_features=audio,gdb,gpu,composite-disk,usb,virgl_renderer
  if [[ $BUILD_GFXSTREAM -eq 1 ]]; then
      crosvm_features+=,gfxstream
  fi

  CROSVM_USE_SYSTEM_MINIGBM=1 \
  CROSVM_USE_SYSTEM_VIRGLRENDERER=1 \
  GFXSTREAM_PATH="${WORKING_DIR}/usr/lib" \
  PKG_CONFIG_PATH="${WORKING_DIR}/usr/lib/pkgconfig" \
  RUSTFLAGS="-C link-arg=-Wl,-rpath,\$ORIGIN -C link-arg=${WORKING_DIR}/usr/lib/libdrm.so" \
    cargo build --features ${crosvm_features}

  # Save the outputs
  cp Cargo.lock "${OUTPUT_DIR}"
  cp target/debug/crosvm "${OUTPUT_BIN_DIR}"
  debuglink crosvm

  cargo --version --verbose > "${OUTPUT_DIR}/cargo_version.txt"
  rustup show > "${OUTPUT_DIR}/rustup_show.txt"
}

compile_crosvm_seccomp() {
  echo "Processing Crosvm Seccomp..."

  cd "${SOURCE_DIR}/platform/crosvm"
  case ${ARCH} in
    x86_64) subdir="${ARCH}" ;;
    amd64) subdir="x86_64" ;;
    arm64) subdir="aarch64" ;;
    aarch64) subdir="${ARCH}" ;;
    *)
      echo "${ARCH} is not supported"
      exit 15
  esac

  inlined_policy_list="\
    jail/seccomp/$subdir/common_device.policy \
    jail/seccomp/$subdir/gpu_common.policy \
    jail/seccomp/$subdir/serial.policy \
    jail/seccomp/$subdir/net.policy \
    jail/seccomp/$subdir/block.policy \
    jail/seccomp/$subdir/vvu.policy \
    jail/seccomp/$subdir/vhost_user.policy \
    jail/seccomp/$subdir/vhost_vsock.policy"
  for policy_file in "jail/seccomp/$subdir/"*.policy; do
    [[ "$inlined_policy_list" = *"$policy_file"* ]] && continue
    jail/seccomp/policy-inliner.sh $inlined_policy_list <"$policy_file" | \
      grep -ve "^@frequency" \
      >"${OUTPUT_SECCOMP_DIR}"/$(basename "$policy_file")
  done
}

compile() {
  echo "Compiling..."
  mkdir -p \
    "${WORKING_DIR}" \
    "${OUTPUT_DIR}" \
    "${OUTPUT_BIN_DIR}" \
    "${OUTPUT_ETC_DIR}" \
    "${OUTPUT_SECCOMP_DIR}"

  if [[ $BUILD_CROSVM -eq 1 ]]; then
    compile_libdrm
    compile_minijail
    compile_minigbm
    compile_epoxy
    compile_virglrenderer
    compile_libffi # wayland depends on it
    compile_wayland
  fi

  if [[ $BUILD_GFXSTREAM -eq 1 ]]; then
    compile_gfxstream
  fi

  compile_crosvm
  compile_crosvm_seccomp

  dpkg-query -W > "${OUTPUT_DIR}/builder-packages.txt"
  echo "Results in ${OUTPUT_DIR}"
}

aarch64_retry() {
  BUILD_CROSVM=1 BUILD_GFXSTREAM=1 compile
}

aarch64_build() {
  rm -rf "${WORKING_DIR}/*"
  aarch64_retry
}

x86_64_retry() {
  MINIGBM_DRV="I915 RADEON VC4" BUILD_CROSVM=1 BUILD_GFXSTREAM=1 compile
}

x86_64_build() {
  rm -rf "${WORKING_DIR}/*"
  x86_64_retry
}

if [[ $# -lt 1 ]]; then
  echo Choosing default config
  set setup_env prepare_source x86_64_build
fi

echo Steps: "$@"

for i in "$@"; do
  echo $i
  case "$i" in
    ARCH=*) ARCH="${i/ARCH=/}" ;;
    CUSTOM_MANIFEST=*) CUSTOM_MANIFEST="${i/CUSTOM_MANIFEST=/}" ;;
    aarch64_build) $i ;;
    aarch64_retry) $i ;;
    setup_env) $i ;;
    install_packages) $i ;;
    fetch_source) $i ;;
    resync_source) $i ;;
    prepare_source) $i ;;
    x86_64_build) $i ;;
    x86_64_retry) $i ;;
    *) echo $i unknown 1>&2
      echo usage: $0 'install_packages|prepare_source|resync_source|fetch_source|$(uname -m)_build|$(uname -m)_retry' 1>&2
       exit 2
       ;;
  esac
done
