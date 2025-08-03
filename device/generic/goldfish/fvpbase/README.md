This document describes how to build and run an Android system image targeting
the ARM Fixed Virtual Platform or QEMU.

## New to Android?

If you do not already have the ``repo`` tool, or a copy of the Android
source tree, please follow the Android instructions for [downloading the
source](https://source.android.com/setup/build/downloading).

## Building the kernel

It is not normally necessary to build the kernel manually. By
default, the ``fvp`` target will use the GKI prebuilts for kernel
version 5.10. If you need to modify the kernel, if you need to use
a different kernel branch, or if you need graphics in FVP, please
follow the instructions below.

```
mkdir android-kernel-mainline
cd android-kernel-mainline
repo init -u https://android.googlesource.com/kernel/manifest -b common-android-mainline
```

Now, update the kernel and setup for building:
```
export FVP_KERNEL_PATH=$(pwd)
# Remove any old fvp-patches cherry-pick branch.
cd common && git checkout aosp/android-mainline && \
  git branch -D fvp-patches 2> /dev/null; cd ..
repo sync -j$(nproc) -q
# One cherrypick currently required due to a bug with BoundsSan+EHCI.
repo start fvp-patches common
repo download -c kernel/common 1634850
```

To support graphics on FVP, one additional cherry pick is required. This only
applies to the ``fvp`` target, and not ``fvp_mini``, and it is also not required
for QEMU.

```
repo download -c kernel/common 1768866
```

Then, build the kernel.

```
BUILD_CONFIG=common/build.config.gki.aarch64 build/build.sh -j$(nproc)
BUILD_CONFIG=common-modules/virtual-device/build.config.virtual_device.aarch64 build/build.sh -j$(nproc)
```

## Building the firmware (ARM Trusted Firmware and U-Boot) (FVP only, not required on QEMU)

First, install ``dtc``, the device tree compiler. On Debian, this is in the
``device-tree-compiler`` package. Return to the top level directory (`cd ..`), and run:
```
mkdir platform
cd platform
export FVP_FIRMWARE_PATH=$(pwd)
repo init -u https://git.linaro.org/landing-teams/working/arm/manifest.git -m pinned-uboot.xml -b 20.01
repo sync

# The included copy of U-Boot is incompatible with this version of AOSP, switch to a recent upstream checkout.
cd u-boot
git fetch https://gitlab.denx.de/u-boot/u-boot.git/ master
git checkout 18b9c98024ec89e00a57707f07ff6ada06089d26
cd ..

mkdir -p tools/gcc
cd tools/gcc
wget https://releases.linaro.org/components/toolchain/binaries/6.2-2016.11/aarch64-linux-gnu/gcc-linaro-6.2.1-2016.11-x86_64_aarch64-linux-gnu.tar.xz
tar -xJf gcc-linaro-6.2.1-2016.11-x86_64_aarch64-linux-gnu.tar.xz
cd ../..

build-scripts/build-test-uboot.sh -p fvp all
```

## Building userspace

Follow the Android instructions to [download the
source](https://source.android.com/setup/build/downloading), and run the
following in the source directory.

```
. build/envsetup.sh
lunch fvp-eng # or fvp-userdebug, fvp_mini-eng, fvp_mini-userdebug
```

The fvp-* lunch targets will build a full Android with UI support, while
`fvp_mini-*` will build a small subset needed to boot to shell and support
command line executables.

If you are using FVP, prepopulate the build directory with the
firmware binaries. Normally, these are copied from the source tree
as part of the build process, but not for this target yet. This step
is not required when using QEMU.

```
mkdir -p $ANDROID_PRODUCT_OUT
cp $FVP_FIRMWARE_PATH/output/fvp/fvp-uboot/uboot/{bl1,fip}.bin $ANDROID_PRODUCT_OUT/
```

If you built a custom kernel, copy or symlink the newly built kernel into your
source tree. For example:
```
mkdir -p kernel/prebuilts/mykernel/arm64
ln -s $FVP_KERNEL_PATH/out/android-mainline/dist/Image kernel/prebuilts/mykernel/arm64/kernel-mykernel

mkdir -p kernel/prebuilts/common-modules/virtual-device/mykernel
ln -s $FVP_KERNEL_PATH/out/android-mainline/dist kernel/prebuilts/common-modules/virtual-device/mykernel/arm64
```
Then set the ``TARGET_KERNEL_USE`` environment variable to the name that you
gave to your kernel. For example, ``export TARGET_KERNEL_USE=mykernel``.

Set any additional build environment variables.
* To enable MTE on all platform binaries (by default it is only enabled on a
  small subset) add `export SANITIZE_TARGET=memtag_heap` for Async mode, or
  `export SANITIZE_TARGET=memtag_heap SANITIZE_TARGET_DIAG=memtag_heap` for Sync
  mode.
* To disable 32 bit support in fvp_mini-* targets use
  `export FVP_MULTILIB_BUILD=false`.

Finally, build the userspace image with `m`.

## Running

The same image can be used with either ARM Fixed Virtual Platform simulator, or
with QEMU. Slowdown from QEMU is roughly 10-20x, where ARM's FVP is 100-200x.

### Running the image in FVP

The model may be obtained from [ARM's
website](https://developer.arm.com/tools-and-software/simulation-models/fixed-virtual-platforms/arm-ecosystem-models)
(under "Armv-A Base RevC AEM FVP").

From a lunched environment, first set the value of the ``MODEL_BIN`` environment
variable to the path to the model executable (it should end with something like
`FVP_Base_RevC-2xAEMv8A/models/Linux64_GCC-6.4/FVP_Base_RevC-2xAEMv8A`). Then
run the following command to launch the model:
```
device/generic/goldfish/fvpbase/run_model
```
Additional model parameters may be passed by appending them to the
``run_model`` command. Add the following to enable MTE support in the model:
```
-C cluster0.has_arm_v8-5=1 \
-C cluster0.memory_tagging_support_level=2 \
-C bp.dram_metadata.is_enabled=1
```

To terminate the model, press ``Ctrl-] Ctrl-D`` to terminate the telnet
connection.

### Running the image in QEMU

As an alternative to using FVP, the image may also be run in QEMU.
QEMU is generally much faster than FVP, but its support for the
latest ARM architectural features is relatively new compared to FVP,
so it may have more bugs.

As of the time of writing, no released version of QEMU can successfully
boot the system to GUI due to bugs in its MTE support, so a development
version with bug fixes must be used. The instructions below check out a
commit that has been successfully tested.

Check [QEMU wiki](https://wiki.qemu.org/Hosts/Linux#Building_QEMU_for_Linux) for
the list of build dependencies. Common missing packages include `ninja-build`,
`libpixman-1-dev`, and `libgtk-3-dev` for GUI support.

```
git clone https://github.com/qemu/qemu
cd qemu
git checkout 5c6295a45b4fceac913c11abc62488c49c02b9fd
mkdir build
cd build
../configure --target-list=aarch64-softmmu
ninja
export QEMU_BIN=$(pwd)/qemu-system-aarch64
```

Then run the following command in a lunched environment to start the emulator:
```
device/generic/goldfish/fvpbase/run_qemu
```
Additional QEMU arguments may be passed by appending them to the ``run_qemu``
command. One useful argument is ``-nographic``, which disables the GUI, which
may be useful when working with ``fvp_mini`` or if the GUI is not needed.

To terminate the emulator, press ``Ctrl-A c q <Enter>`` or close the GUI
window.

### Accessing the model via adb

To connect to the model on the host:
```
adb connect localhost:5555
```
