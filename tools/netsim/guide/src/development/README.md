# Network Simulator Development

This section walks you through building netsim from source.

Netsim can be built as part of emulator or cuttlefish and best practice is to
setup both and switch between repo directories to test each build environment.

* To build with emulator, follow the [netsim with emulator](#netsim_with_emulator)
section to build netsim by `cmake` in `emu-master-dev` manifest branch.

* To build with cuttlefish, follow the [netsim with
cuttlefish](#netsim_with_cuttlefish) to build netsim by `soong` in `aosp-master`
manifest branch.

## Emulator and cuttlefish build branches

The *netsim* network simulator is built as a component of
[emulator](https://source.android.com/docs/setup/create/avd)
and
[cuttlefish](https://source.android.com/docs/setup/create/cuttlefish)
virtual devices.

*Emulator* allows you to run emulations of Android devices on Windows, macOS or
Linux machines. Emulator runs the Android operating system in a virtual machine
called an Android Virtual Device (AVD).
The emulator is typically used from
[Android Studio](https://developer.android.com/studio).

*Cuttlefish* is a configurable virtual Android device that can be run on Linux
x86 machines both remotely (using third-party cloud offerings such as Google
Cloud Engine) and locally. Cuttlefish runs the Android operating system in a
virtual machine called a Cuttlefish Virtual Device (CVD).
Cuttlefish is typically used by developers working with AOSP code to [launch
AOSP builds](https://source.android.com/docs/setup/create/cuttlefish-use).

The table below summarizes the two virtual device environments:

|                 |      emulator         | cuttlefish         |
|:----------------|:---------------------:|:----------------:  |
| AOSP branch     | `emu-master-dev`      | `aosp-master`      |
| launcher        | `emulator` app and<br>Android Studio | `launch_cvd` and<br>`cvd` app |
| best for        | App developer         | Platform developer |
| Supported OS    | Linux, MacOS, Windows | Linux              |
| Build system    | &nbsp;&nbsp; CMake (CMakeLists.txt) &nbsp;&nbsp; | &nbsp;&nbsp; Soong (Android.bp) &nbsp;&nbsp; |
| Virtual device  | AVD                   | CVD                |

Netsim is the default networking backplane for AVD and CVD emulated Android
devices.

## <a name="netsim_with_emulator"></a>Build netsim with emulator

For developing netsim alongside emulator, start with the OS specific build
instructions:
* [Android emulator Windows Development](
https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/docs/WINDOWS-DEV.md
)
* [Android emulator MacOS Development](
https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/docs/DARWIN-DEV.md
)
* [Android emulator Linux Development](
https://android.googlesource.com/platform/external/qemu/+/refs/heads/emu-master-dev/android/docs/LINUX-DEV.md
)

In general changes should be built and tested on all three operating systems.

Follow the instructions above links for workstation setup. Linux setup and build
is summarized below:

### Linux workstation set up

Install cmake and ninja:

```
sudo apt-get install -y cmake ninja-build
```

### Initialize and sync the code

Download the emu-master-dev branch:

```
mkdir /repo/emu-master-dev; cd /repo/emu-master-dev
repo init -u https://android.googlesource.com/platform/manifest -b emu-master-dev
```
Sync the source code:

```
repo sync -j8
```

### Emulator full build

Use Android emulator toolchain script to run the build:
```
cd /repo/emu-master-dev/external/qemu
sh android/rebuild.sh
```

The output can be found in:
```
/repo/emu-master-dev/external/qemu/objs/distribution/emulator
```

### Emulator incremental netsim build

The `emulator` rebuild script does a complete clean build of all emulator components.
For incrmental builds of the `netsimd` component, you can use the `cmake_setup` script:
```
cd /repo/emu-master-dev/tools/netsim
sh scripts/cmake_setup.sh
```

Then use `ninja` for a partial netsim build:
```
ninja -C objs netsimd
```

If the build fails with rust errors it may be necessary to issue this command:

```
rm rust/Cargo.lock
```

Copy Web UI assets into `objs/netsim-ui`.
```
sh scripts/build_ui.sh
```
If you wish to change the source code of the ui and rebuild, use the `-b` flag.

The output can be found in

```
/repo/emu-master-dev/tools/netsim/objs
```

## <a name="netsim_with_cuttlefish"></a>Build netsim with cuttlefish

The [Android Developer Codelab](https://source.android.com/docs/setup/start)
provides instructions for building and running cuttlefish AVDs.

Follow the instructions in the codelab for workstation setup.

### Initialize and sync the code

Initialize the repo:
```
mkdir /repo/aosp-master; cd /repo/aosp-master
repo init -u https://android.googlesource.com/platform/manifest -b aosp-master
```

Sync the source code:
```
repo sync -j8
```

### Build cuttlefish

Set up build environment:
```
source build/envsetup.sh
```

Set the target device type:
```
lunch aosp_cf_x86_64_phone
```

Start the build:
```
m -j64
```

The netsim executable can be found in:
```
/repo/aosp-master/out/host/linux-x86/bin
```

### Cuttlefish incremental netsim build


Start the build with netsimd target:
```
m netsimd -j64
```

## Unit Testing

Unit tests can be run from the `aosp-master` branch using the `atest` command:
```
atest --host-unit-test-only --test-filter netsim
```

Rust tests can also be run for individual Rust modules using the `cargo test` command:
```
cd tools/netsim/rust/netsim-cxx/
cargo test transport
```

## Build Tips

### Building across repository directories

You will need to verify that any changes in `tools/netsim` can be built from
both manifest branches. To temporarily copy changes between repositories we often
use:

```
git diff HEAD^ > /tmp/git.diff
cd /repo/emu-master-dev
git apply /tmp/git.diff
```

### Repo workflow

The repo workflow for creating and uploading a change request:
```
repo start new-branch
git add <files>
git commit
repo upload --branch=new-branch
```

Subsequent commits:
```
git add <files>
git commit --amend --no-edit
repo upload --branch=new-branch
```

## Documentation

The developer and user documentation for netsim is stored in the `guide`
directory in `mdbook` format.  Refer to
[install](https://rust-lang.github.io/mdBook/guide/installation.html)
for instructions on how to install `mdbook`.

Use this command to start a local web server with the netsim guide:
```
mdbook serve guide
```

