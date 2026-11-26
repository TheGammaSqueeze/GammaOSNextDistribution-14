# GammaOS Next

The source tree of GammaOS Next: an Android 14 generic system image (GSI) for handheld
consoles, built on LineageOS 21 and the TrebleDroid device tree. This file explains how the
tree is built.

## What is in here

| Path | What it holds |
| --- | --- |
| the Android source folders (`frameworks/`, `system/`, `packages/`, ...) | LineageOS 21 with the GammaOS changes |
| `device/phh/treble/` | the product makefiles of the images |
| `vendor/lineage/build/core/main_version.mk` | the GammaOS version (`GAMMAOS_VERSION`) and the edition of an image |
| `gammaos/` | GammaOS's own additions: first-run setup, bundled apps, the module tool (`gammaos/modules`), the script that builds the nano programs |
| `lineage_build_unified/` | the build script the wrappers below call |
| `build.sh`, `buildfull.sh`, `buildtv.sh`, `buildtv_cc.sh` | one command per image; nothing else has to be set or run |
| `gammaos/build-image.sh` | what those four run |
| `gen_ota_package.sh` | makes a system update package from partition images |

## The editions

| Edition | What it is | Product | Command |
| --- | --- | --- | --- |
| Core | the lightest one, on the Android TV base; for kernels 5.7 and newer | `lineage_tv_arm64_bvN` | `bash buildtv.sh` |
| Core, old kernels | the same for kernels up to 4.14 (no userfaultfd) | `lineage_tv_arm64_bvN` | `bash buildtv_cc.sh` |
| Lite | Android without Google apps | `lineage_arm64_bvN` | `bash build.sh` |
| Full | Android with Google apps | `lineage_arm64_bgN` | `bash buildfull.sh` |

## GammaOS Nano is optional

GammaOS Nano is the native home GammaOS boots into, with its own emulators. Each of its
programs lives in a repository of its own, linked here as a git submodule:

| Submodule | Program |
| --- | --- |
| `frameworks/base/cmds/gammaos-nano` | the nano home |
| `gammaos/emulators/drastic-nano` | Nintendo DS (DraStic) |
| `gammaos/emulators/melonds-nano` | Nintendo DS (melonDS) |
| `gammaos/emulators/azahar-nano` | Nintendo 3DS (Azahar) |
| `gammaos/emulators/retroarch-nano` | libretro cores (RetroArch) |

**The tree builds without them.** If you cannot reach those repositories, leave the
submodules alone and build as described below. Whatever is not checked out is left out of
the image, and an image without the nano home boots as plain Android, with everything else
GammaOS adds. Nothing has to be edited for that: the product makefile
(`device/phh/treble/gammaos-nano.mk`) only adds a program whose folder is there, and the
build script skips a folder that is empty with a note such as

    GammaOS Nano: gammaos/emulators/melonds-nano is not checked out, building the image without it

The rules: an emulator is only added together with the home, and no emulator needs another.

## What you need

- A 64 bit Linux host with the usual Android build packages
  (https://source.android.com/docs/setup/start/requirements), Python 3 and git.
- A lot of free disk space: the build output alone grows past 200 GB.
- 32 GB of memory or more is comfortable. 24 GB works: the build scripts then hold Soong's
  memory use down by themselves.

## Getting the source

    git clone <this repository> gammaos
    cd gammaos

The clone is the whole tree; no `repo sync` is needed for a normal build.

With access to the nano repositories, also:

    git submodule update --init --recursive

Without access, skip that command. If you ran it and it failed part way, the build still
works with whichever submodules did come down.

## Building

From the root of the tree, one of the commands in the table above, for example:

    bash buildtv.sh

Each command does everything that image needs, in this order (`gammaos/build-image.sh`):

1. It builds the nano programs that are checked out (next section). Without them it says so
   and goes on.
2. On a host with less than 30 GB of memory it sets `GOMEMLIMIT` to two thirds of the
   machine, unless you set it yourself. A full Soong analysis (any `Android.bp` change causes
   one) otherwise outgrows the machine and the build crawls.
3. When the last image built in this `out/` folder was another product, or the Core image for
   the other kind of kernel, it removes the boot image so that it is compiled again.
   `make installclean` keeps it, and an image with the wrong one recompiles the boot
   classpath on every start of the console.
4. It selects the product, runs `make installclean` and builds `systemimage`.
5. It checks the result and stops with an error unless a new image is there. The image is
   `out/home/build-output/lineage-21.0-<date>-UNOFFICIAL-<product>.img`, a raw ext4 system
   image, and the last lines say which version and edition it is and whether GammaOS Nano
   is in it.

A first build takes hours; later ones are much quicker.

Passing `sync` as the first argument (`bash build.sh sync`) runs `repo sync` and applies the
patch sets under `lineage_patches_unified/` before building. The default, `nosync`, builds
the tree as it is, which is what you want unless you are updating the Android base.

To rebuild without `installclean` once a product has been built:

    source build/envsetup.sh
    lunch lineage_tv_arm64_bvN-ap2a-userdebug      # or lineage_arm64_bvN / lineage_arm64_bgN
    m -j8 systemimage                              # out/target/product/tdgsi_arm64_ab/system.img

### How the nano programs get into the image

The Android build compiles none of them. Each repository has a build script that works with
the Android NDK alone and writes the finished programs into its `prebuilt/` folder; its
`Android.bp` only installs what that folder holds.

**By default every image build rebuilds them**: `gammaos/build-nano-cores.sh` runs the build
scripts of the home, drastic-nano, azahar-nano and melonds-nano before the image build (they
are incremental, so an unchanged program takes moments).
retroarch-nano is never rebuilt there; the image takes the binary its repository holds. The
NDK, CMake and Ninja are downloaded once into `out/<program>/` when the host has none.

A program that comes out different from what its repository has checked in is committed
there by the build (its `prebuilt/` folder only), and so is the tree's pointer to it, so the
tree commit an image is built at names the programs that are in it. Nothing is pushed. This
only happens where the programs' signing key is; `GAMMAOS_NANO_NO_COMMIT=1` turns it off.

To skip the rebuild and ship the binaries exactly as the repositories hold them:

| Variable | Effect |
| --- | --- |
| `GAMMAOS_NANO_SKIP_CORES=1` | rebuild nothing |
| `GAMMAOS_NANO_SKIP_BUILD=1` | keep the home |
| `DRASTIC_NANO_SKIP_BUILD=1` | keep drastic-nano |
| `AZAHAR_NANO_SKIP_BUILD=1` | keep azahar-nano |
| `MELONDS_NANO_SKIP_BUILD=1` | keep melonds-nano |

`GAMMAOS_NANO_JOBS`, `DRASTIC_NANO_JOBS`, `AZAHAR_NANO_JOBS` and `MELONDS_NANO_JOBS` set the
parallel jobs of each (the default is one per CPU core).

Each repository's own README says how to build it by itself.

### The two Core images

`buildtv.sh` and `buildtv_cc.sh` build the same product with a different garbage collector
baked into the boot image, and write the same output file name. Copy the first image away
before building the second. Nothing else is needed: the build notices the switch and
compiles the boot image again.

## Checking a build

The build commands stop with an error when no new image came out, and print the image's
name, version and edition when one did. To look for the cause of a failure:

    grep -E "FAILED:|ninja: build stopped" build.log

What the image was built as:

    grep -E 'ro.gammaos.build.version|ro.gammaos.variant|ro.gammaos.nano.available' \
        out/target/product/tdgsi_arm64_ab/system/build.prop

`ro.gammaos.nano.available=true` is there only when the nano home went in.

## A slow or stuck build

- Any change to an `Android.bp` file makes Soong analyse the whole tree again, which takes
  about 20 GB of memory and several minutes before anything is compiled. That is normal.
  The memory ceiling the build sets for it on smaller hosts is described under "Building";
  `export GOMEMLIMIT=<size>` chooses another.
- Never run two builds at once: they share `out/`.
- Do not edit a source file while a build runs.

## Flashing

The image is a GSI: it goes into the `system` partition of a console that already has a
matching vendor and boot. From userspace fastboot:

    adb reboot fastboot
    fastboot flash system <image>
    fastboot reboot

Many consoles run the system image as EROFS instead of ext4, and what a kernel can mount
differs between them. Each supported console has its own installation instructions; follow
those for anything beyond a system image swap.

## Updates

- A full system update is a package made by `gen_ota_package.sh` (run it with `-h`), which a
  console installs with its updater.
- A single nano program can be updated without a system update, as a module:
  `gammaos/modules/README.md`.

## Licences

The Android and LineageOS sources keep their own licences, noted in their folders. Each nano
repository states its own.
