# Experimental QEMU8 build system

This is a build of QEMU8 from scratch on Linux, using AOSP-specific compiler
toolchain and sysroot (based on an old glibc-2.17 to ensure the generated
binaries run on a vast number of distributions).

## Prerequisite:

Ensure podman is installed with `sudo apt-get install podman`

## Reproducing a build in AOSP

This secion is about rebuilding QEMU with AOSP toolchain (with the `repo` tool).
Information about how to iterate on this build can be found in the next section.

If you have an AOSP checkout, run:

```sh
$ANDROID_BUILD_TOP/device/google/cuttlefish_vmm/qemu/scripts/rebuild_in_container.sh
```

This will create a clean source checkout of QEMU and all relevant
dependencies, and starts a build isolated in a podman container.

If you don't have an AOSP checkout, run:

```sh
mkdir cuttlefish_vmm
cd cuttlefish_vmm
repo init --manifest-url https://android.googlesource.com/device/google/cuttlefish_vmm \
  --manifest-name=qemu/manifest.xml
repo sync -j 12
qemu/scripts/rebuild_in_container.sh --from_existing_sources
```

`--from_existing_sources` also makes possible to reuse an existing QEMU source checkout
to iterate faster.

The result is a portable QEMU archive that can be
found in `/tmp/qemu-build-output/qemu-portable.tar.gz`

## Development process

QEMU assembles many source trees that use git submodules. Hence it is more convenient
to iterate on a checkout based on `git submodules` for development, and to capture the
state in a `repo` manifest before submitting the changes to AOSP.


```sh
git clone sso://experimental-qemu-build-internal.googlesource.com/qemu-build qemu
cd qemu
git submodule update --init --depth 1 --recursive --jobs 4
```

You can build without isolation with:

```sh
qemu/scripts/rebuild.sh --build-dir ~/qemu-build.out
```

You can also build in a container with:

```sh
qemu/scripts/rebuild_in_container.sh --from_existing_sources
```

After sucessfull build the ASOP binaries can be updated with:

```sh
tar -xvf /tmp/qemu-build-output/qemu-portable.tar.gz \
  -C $ANDROID_BUILD_TOP/device/google/cuttlefish_vmm/qemu/x86_64-linux-gnu
```

This makes possible to upvert a dependency such as `qemu` and
regenerate the repo manifest from the submodule tree.

```sh
python3 qemu/scripts/genrepo.py . --repo_manifest qemu/manifest.xml
```

## Check your code before submit

The following script run pytype and pyformat.

```sh
scripts/check.sh
```

## Regenerate Cargo crates list

Cargo crates are checked-in as part of the source tree and enumerated by
`qemu/third_party/.cargo/config.toml`. This file hase be regenerated when
`qemu/third_party/rust/crate` changes with the following command line:

```sh
ls qemu/third_party/rust/crates | awk '
  BEGIN {print "[patch.crates-io]"}
  {print $1 " = { path = \"rust/crates/" $1 "\" }"}' > qemu/third_party/.cargo/config.toml
```