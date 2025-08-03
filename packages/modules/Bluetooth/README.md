# Fluoride Bluetooth stack

## Building and running on AOSP
Just build AOSP - Fluoride is there by default.

## Building and running on Linux

Instructions for a Debian based distribution:
* Debian Bullseye or newer
* Ubuntu 20.10 or newer
* Clang-11 or Clang-12
* Flex 2.6.x
* Bison 3.x.x (tested with 3.0.x, 3.2.x and 3.7.x)

You'll want to download some pre-requisite packages as well. If you're currently
configured for AOSP development, you should have most required packages.
Otherwise, you can use the following apt-get list or use the `--run-bootstrap`
option on `build.py` (see below) to get a list of packages missing on your
system:

```sh
sudo apt-get install repo git-core gnupg flex bison gperf build-essential \
  zip curl zlib1g-dev gcc-multilib g++-multilib \
  x11proto-core-dev libx11-dev libncurses5 \
  libgl1-mesa-dev libxml2-utils xsltproc unzip liblz4-tool libssl-dev \
  libc++-dev libevent-dev \
  flatbuffers-compiler libflatbuffers1 openssl \
  libflatbuffers-dev libfmt-dev libtinyxml2-dev \
  libglib2.0-dev libevent-dev libnss3-dev libdbus-1-dev \
  libprotobuf-dev ninja-build generate-ninja protobuf-compiler \
  libre2-9 debmake \
  llvm libc++abi-dev \
  libre2-dev libdouble-conversion-dev \
  libgtest-dev libgmock-dev libabsl-dev
```

You will also need a recent-ish version of Rust and Cargo. Please follow the
instructions on [Rustup](https://rustup.rs/) to install a recent version.

### Download source

```sh
mkdir ~/fluoride
cd ~/fluoride
git clone https://android.googlesource.com/platform/packages/modules/Bluetooth
```

### Using --run-bootstrap on build.py

`build.py` is the helper script used to build Fluoride for Linux (i.e. Floss).
It accepts a `--run-bootstrap` option that will set up your build staging
directory and also make sure you have all required system packages to build
(should work on Debian and Ubuntu). You will still need to build some unpackaged
dependencies (like libchrome, modp_b64, googletest, etc).

To use it:
```sh
./build.py --run-bootstrap
```

This will install your bootstrapped build environment to `~/.floss`. If you want
to change this, just pass in `--bootstrap-dir` to the script.

### Build dependencies

The following third-party dependencies are necessary but currently unavailable
via a package manager. You may have to build these from source and install them
to your local environment.

* libchrome
* modp_b64

We provide a script to produce debian packages for those components. Please
see the instructions in build/dpkg/README.txt for more details.

```sh
cd system/build/dpkg
mkdir -p outdir/{modp_b64,libchrome}

# Build and install modp_b64
pushd modp_b64
./gen-src-pkg.sh $(readlink -f ../outdir/modp_b64)
popd
sudo dpkg -i outdir/modp_b64/*.deb

# Build and install libchrome
pushd libchrome
./gen-src-pkg.sh $(readlink -f ../outdir/libchrome)
popd
sudo dpkg -i outdir/libchrome/*.deb
```

### Rust dependencies

**Note**: Handled by `--run-bootstrap` option.

Run the following to install Rust dependencies:
```
cargo install cxxbridge-cmd
```

### Stage your build environment

**Note**: Handled by `--run-bootstrap` option.

For host build, we depend on a few other repositories:
* [Platform2](https://chromium.googlesource.com/chromiumos/platform2/)
* [Rust crates](https://chromium.googlesource.com/chromiumos/third_party/rust_crates/)
* [Proto logging](https://android.googlesource.com/platform/frameworks/proto_logging/)

Clone these all somewhere and create your staging environment.
```sh
export STAGING_DIR=path/to/your/staging/dir
mkdir ${STAGING_DIR}
mkdir -p ${STAGING_DIR}/external
ln -s $(readlink -f ${PLATFORM2_DIR}/common-mk) ${STAGING_DIR}/common-mk
ln -s $(readlink -f ${PLATFORM2_DIR}/.gn) ${STAGING_DIR}/.gn
ln -s $(readlink -f ${RUST_CRATE_DIR}) ${STAGING_DIR}/external/rust
ln -s $(readlink -f ${PROTO_LOG_DIR}) ${STAGING_DIR}/external/proto_logging
```

### Build

We provide a build script to automate building assuming you've staged your build
environment already as above. At this point, make sure you have all the
pre-requisites installed (i.e. bootstrap option and other dependencies above) or
you will see failures. In addition, you may need to set a `--libdir=` if your
libraries are not stored in `/usr/lib` by default.


```sh
./build.py
```

This will build all targets to the output directory at `--bootstrap-dir` (which
defaults to `~/.floss`). You can also build each stage separately (if you want
to iterate on something specific):

* prepare - Generate the GN rules
* tools - Generate host tools
* rust - Build the rust portion of the build
* main - Build all the C/C++ code
* test - Build all targets and run the tests
* clean - Clean the output directory

You can choose to run only a specific stage by passing an arg via `--target`.

Currently, Rust builds are a separate stage that uses Cargo to build. See
[gd/rust/README.md](gd/rust/README.md) for more information. If you are
iterating on Rust code and want to add new crates, you may also want to use the
`--no-vendored-rust` option (which will let you use crates.io instead of using
a pre-populated vendored crates repo).

### Run

By default on Linux, we statically link libbluetooth so you can just run the
binary directly. By default, it will try to run on hci0 but you can pass it
--hci=N, where N corresponds to /sys/class/bluetooth/hciN.

```sh
$OUTPUT_DIR/debug/btadapterd --hci=$HCI INIT_gd_hci=true
```
