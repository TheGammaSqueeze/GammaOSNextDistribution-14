# Prebuilts

## How to update

From your AOSP repo:

```
./device/google/cuttlefish_vmm/rebuild.sh \
  --docker \
  --docker_arch aarch64
```

If you need to make edits and iterate afterward:

./device/google/cuttlefish_vmm/rebuild.sh \
  --docker \
  --docker_arch aarch64 \
  --reuse
```

## Why do we need these?

The Android toolchain builds the Cuttlefish host tools for ARM using musl
which is not compatible with most userspace GPU drivers which are built
using glibc (see b/200592498).

The vhost-user protocol allows VMMs to run individual virtual devices in
separate host processes. By using vhost-user-gpu, the Cuttlefish host tools
can run just the Virtio GPU device in a separate subprocess using a Crosvm
binary and Gfxstream library built for the host architecture. This directory
contains prebuilts for Crosvm and Gfxstream for this purpose.
