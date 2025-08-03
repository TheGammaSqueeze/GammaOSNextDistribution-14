# GSI

This document introduces special GSI settings for facilitating xTS-on-GSI with
a single image.

### Support system_dlkm partition

```
[BoardConfigGsiCommon.mk]

BOARD_USES_SYSTEM_DLKMIMAGE := true
BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_COPY_OUT_SYSTEM_DLKM := system_dlkm

[gsi_release.mk]

PRODUCT_BUILD_SYSTEM_DLKM_IMAGE := false
```

Starting from Android 13, all devices must include a [system_dlkm partition].
GSI enables system_dlkm to support the devices with system_dlkm partition,
and be compatible with old devices without a system_dlkm partition.

With these configurations, `/system/system_dlkm` would not be created.
Instead, a `/system/lib/modules` -> `/system_dlkm/lib/modules` symlink is
created.

For device without system_dlkm partition, the symlink would be dangling.
The dangling symlink shouldn't be followed anyway because the device doesn't
use system_dlkm.

For device with system_dlkm, they can load modules via that path normally like
when they are using their original system image.

[system_dlkm partition]: https://source.android.com/docs/core/architecture/bootloader/partitions/gki-partitions

### SystemUI overlays

Some devices access the private android framework resource by `@*android:`
while overlaying their SystemUI setting `status_bar_header_height_keyguard`.
However, referencing private framework resource IDs from RRO packages in the
vendor partition crashes on these devices when GSI is used. This is because
private framework resource don't have a stable ID, and these vendor RRO
packages would be referencing to dangling resource references after GSI is
used (b/245806899).

In order to prevent SystemUI crash, GSI adds a runtime resource overlay in
the system_ext partition, which have higher overlay precedence than RROs on
vendor partition, so the problematic vendor RROs would be overridden.

Lifetime of this package:
* Starts at: Android 14.
* Deprecation plan: TBD, depends on b/254581880.
