# GammaOS OTA System — Implementation Plan

## Overview

In-place OTA update system for GammaOS. Writes directly to block devices while partitions are mounted read-only — no unmount, no pivot_root, no recovery required. Proven on-device 2026-03-20.

**Critical safety requirement:** Once overwriting begins on a logical partition, the mounted filesystem's block device contains garbage. Any binary or library still being demand-paged from that partition will crash. Therefore, `gammaos-ota` and ALL its dependencies must be staged to tmpfs and executed from there BEFORE any writes begin.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  OTA Server (ota.gammaos.sh)             │
│  Serves per-device manifests + hosts .img.xz files      │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────────┐
│              GammaOS Updater App                         │
│  (modified LineageOS Updater)                            │
│  - Checks for updates via ro.gammaos.device              │
│  - Downloads OTA package to /data/gammaos_ota/           │
│  - Verifies checksums                                    │
│  - Triggers OTA mode via property                        │
└──────────────────────┬──────────────────────────────────┘
                       │ setprop sys.gammaos.ota.package <path>
                       │ setprop ctl.start gammaos-ota
┌──────────────────────▼──────────────────────────────────┐
│              gammaos-ota (native C++ binary)             │
│  Modeled after NanoMenu — SurfaceFlinger + EGL/GLES     │
│                                                          │
│  TWO MODES:                                              │
│                                                          │
│  Mode A: Auto (launched with package path)               │
│  → Shows progress, stops framework, flashes, reboots     │
│                                                          │
│  Mode B: Manual (launched without package)                │
│  → File browser for internal/external storage             │
│  → User selects .zip, copies to /data/ if external       │
│  → Verifies, then proceeds like Mode A                   │
└─────────────────────────────────────────────────────────┘
```

## Entry Points

| Trigger | How | Mode |
|---------|-----|------|
| Updater app "Install" | App downloads OTA, sets `sys.gammaos.ota.package`, starts service | Auto (A) |
| Settings → System → GammaOS Update | Starts service with no package set | Manual (B) |
| `adb shell setprop ctl.start gammaos-ota` | Direct launch for testing | Manual (B) |

## OTA Package Format

A `.zip` file containing:
```
gammaos_ota_v1.2.3.zip
├── manifest.json
├── system.img.xz
├── vendor.img.xz           (optional)
├── boot.img.xz             (optional)
├── vendor_boot.img.xz      (optional)
├── vbmeta.img.xz           (optional)
├── vbmeta_system.img.xz    (optional)
├── vbmeta_vendor.img.xz    (optional)
├── dtbo.img.xz             (optional)
├── vendor_dlkm.img.xz      (optional)
├── system_ext.img.xz       (optional)
├── product.img.xz          (optional)
└── odm.img.xz              (optional)
```

### manifest.json

```json
{
  "version": "1.2.3",
  "version_code": 12300,
  "datetime": 1710900000,
  "device": ["ayaneo_pocket_air", "retroid_pocket_5"],
  "min_battery": 50,
  "partitions": [
    {
      "name": "system",
      "type": "logical",
      "size": 3629744128,
      "file": "system.img.xz",
      "sha256": "abc123...",
      "sha256_uncompressed": "fed987..."
    },
    {
      "name": "vendor",
      "type": "logical",
      "size": 536870912,
      "file": "vendor.img.xz",
      "sha256": "def456...",
      "sha256_uncompressed": "cba654..."
    },
    {
      "name": "boot",
      "type": "physical",
      "file": "boot.img.xz",
      "sha256": "789abc...",
      "sha256_uncompressed": "321fed..."
    },
    {
      "name": "vbmeta",
      "type": "physical",
      "file": "vbmeta.img.xz",
      "sha256": "012def...",
      "sha256_uncompressed": "456abc..."
    }
  ]
}
```

- `type: "logical"` — partition inside `super`, may need resize
- `type: "physical"` — raw partition with A/B slots, written to both `_a` and `_b`
- `size` — required for logical partitions (used for resize). Not needed for physical.
- `device` — array of `ro.gammaos.device` values this OTA is compatible with
- `sha256` — checksum of the compressed `.img.xz` file (verified before flash)
- `sha256_uncompressed` — checksum of the raw image (verified after flash by reading back from block device)
- Server can include any subset of partitions per update

## OTA Server API

**Domain:** `ota.gammaos.sh`

```
GET https://ota.gammaos.sh/api/v1/{ro.gammaos.device}

Response (LineageOS Updater JSON format):
{
  "response": [{
    "datetime": 1710900000,
    "filename": "gammaos_ota_v1.2.3.zip",
    "id": "<sha256 of zip>",
    "romtype": "UNOFFICIAL",
    "size": 1234567890,
    "url": "https://cdn.gammaos.sh/ota/gammaos_ota_v1.2.3.zip",
    "version": "1.2.3"
  }]
}
```

Can be implemented as:
- Static JSON on GitHub Pages
- Cloudflare Workers
- Any CDN with a thin API layer

## gammaos-ota Binary

### Location
```
frameworks/base/cmds/gammaos-ota/
├── Android.bp
├── gammaos-ota.rc
├── main.cpp
├── OtaMenu.cpp          # UI rendering (file browser + progress)
├── OtaMenu.h
├── OtaFlasher.cpp       # Flash logic (staging, backup, flash, verify)
├── OtaFlasher.h
├── OtaManifest.cpp      # JSON manifest parser
├── OtaManifest.h
├── XzDecompressor.cpp   # XZ stream decompression to block device
└── XzDecompressor.h
```

### Init Service
```rc
service gammaos-ota /system/bin/gammaos-ota
    class core
    user root
    group root system graphics input media_rw
    disabled
    oneshot
    ioprio rt 0
    task_profiles MaxPerformance
```

Runs as `root` (writes to block devices, calls lptools).

### Rendering Stack

Same as NanoMenu:
- `SurfaceComposerClient` + `SurfaceControl` for display surface
- EGL/GLES2 for rendering
- FreeType for text
- Linux input subsystem for gamepad/keyboard/touch

### Mode B: File Browser UI

```
┌─────────────────────────────────────┐
│     GammaOS System Update           │
│                                     │
│  Select update package:             │
│                                     │
│  📁 Internal Storage                │
│    └ gammaos_ota_v1.2.3.zip   1.2GB│
│    └ gammaos_ota_v1.2.2.zip   1.1GB│
│                                     │
│  📁 USB Storage                     │
│    └ gammaos_ota_v1.2.3.zip   1.2GB│
│                                     │
│  [▲/▼] Navigate  [A] Select [B] Back│
└─────────────────────────────────────┘
```

Scans:
- `/data/gammaos_ota/*.zip` — internal (already in place)
- `/mnt/media_rw/*/*.zip` — USB/SD (copied to `/data/gammaos_ota/` before flashing)

### Pre-Flash Confirmation UI

```
┌─────────────────────────────────────┐
│     GammaOS System Update           │
│     Version 1.2.3                   │
│                                     │
│  Partitions to update:              │
│    system        3.4 GB → 3.6 GB   │
│    vendor        492 MB (no change) │
│    boot          67 MB  (both slots)│
│    vbmeta        4 KB   (both slots)│
│                                     │
│  ☐ Back up current partitions first │
│    (requires ~4.1 GB free on /data) │
│                                     │
│  [A] Install  [B] Cancel            │
└─────────────────────────────────────┘
```

### Backup Prompt

Before flashing, user is offered an **optional** backup:
- Reads current partition images from block devices
- Compresses with xz to `/data/gammaos_ota/backup/`
- Only backs up partitions that are about to be overwritten
- Requires sufficient free space on `/data`
- If user declines, flash proceeds without backup

### Flash Progress UI

```
┌─────────────────────────────────────┐
│     GammaOS System Update           │
│     Version 1.2.3                   │
│                                     │
│  ✓ boot          (both slots)       │
│  ✓ vbmeta        (both slots)       │
│  ▶ system        ████████░░  78%    │
│  ○ vendor                           │
│                                     │
│  Do not power off your device.      │
└─────────────────────────────────────┘
```

### Post-Flash: Verify + Outcome UI

After all partitions are written, checksums are verified by reading back from the block devices:

**Success:**
```
┌─────────────────────────────────────┐
│     GammaOS System Update           │
│                                     │
│  ✓ All partitions verified          │
│                                     │
│  Update complete!                   │
│  Rebooting in 5 seconds...         │
│                                     │
│  [A] Reboot now                     │
└─────────────────────────────────────┘
```

**Failure:**
```
┌─────────────────────────────────────┐
│     GammaOS System Update           │
│                                     │
│  ✗ system: checksum mismatch!       │
│  ✓ vendor: OK                       │
│                                     │
│  [A] Retry flash                    │
│  [X] Restore from backup            │
│  [B] Reboot anyway (risky)          │
└─────────────────────────────────────┘
```

- **Retry** — re-reads the .img.xz from `/data/` and flashes again
- **Restore from backup** — only shown if backup was taken; restores the backed-up partition images
- **Reboot anyway** — for advanced users; device may not boot

## Tmpfs Staging (Critical Safety Requirement)

### The Problem

`gammaos-ota` is a dynamically linked binary running from `/system/bin/`. Its shared libraries are loaded from `/system/lib64/` and `/apex/.../lib64/`. Once we start overwriting the system block device, any page fault that requires reading from the (now-corrupted) block device will crash the process.

Even though we `drop_caches` before writing, the kernel can still evict pages under memory pressure and re-fault them from disk at any time.

### The Solution

Before ANY write begins, `gammaos-ota` stages itself to tmpfs:

```
Phase 0: Stage to tmpfs (runs while framework is still up)
├── mkdir /dev/gammaos-ota-stage/
├── Copy gammaos-ota binary to /dev/gammaos-ota-stage/bin/
├── Copy ALL shared library dependencies to /dev/gammaos-ota-stage/lib64/
│   (libbase, liblp, libdm, libz, liblzma, libc, libm, libdl, libEGL, libGLESv2, etc.)
├── Copy lptools to /dev/gammaos-ota-stage/bin/
├── Copy linker64 to /dev/gammaos-ota-stage/bin/
├── Copy font files for FreeType to /dev/gammaos-ota-stage/fonts/
├── Re-exec self from tmpfs:
│   execve("/dev/gammaos-ota-stage/bin/gammaos-ota",
│          argv, {"LD_LIBRARY_PATH=/dev/gammaos-ota-stage/lib64", ...})
└── Now running entirely from RAM — safe to destroy /system
```

### How the staging works in code

```cpp
void OtaFlasher::stageToTmpfs() {
    const char* STAGE = "/dev/gammaos-ota-stage";

    // Check if we're already running from tmpfs (re-exec detection)
    if (isRunningFromTmpfs()) return;

    mkdir(STAGE, 0755);
    mkdir(STAGE "/bin", 0755);
    mkdir(STAGE "/lib64", 0755);
    mkdir(STAGE "/fonts", 0755);

    // Copy self
    copyFile("/system/bin/gammaos-ota", STAGE "/bin/gammaos-ota");

    // Copy tools
    copyFile("/system/bin/lptools", STAGE "/bin/lptools");
    copyFile("/system/bin/linker64", STAGE "/bin/linker64");

    // Copy all shared libs (enumerated at build time or via ldd)
    for (const auto& lib : REQUIRED_LIBS) {
        copyFile(lib.src, std::string(STAGE "/lib64/") + lib.name);
    }

    // Copy fonts
    copyFile("/system/fonts/Roboto-Regular.ttf", STAGE "/fonts/Roboto-Regular.ttf");

    // Re-exec from tmpfs
    setenv("LD_LIBRARY_PATH", STAGE "/lib64", 1);
    setenv("GAMMAOS_OTA_STAGED", "1", 1);
    execv(STAGE "/bin/gammaos-ota", saved_argv);
}

bool OtaFlasher::isRunningFromTmpfs() {
    return getenv("GAMMAOS_OTA_STAGED") != nullptr;
}
```

After re-exec, the process is running entirely from tmpfs. All subsequent library loads, page faults, and file accesses for the binary itself come from RAM. `/data` is the only on-disk dependency remaining (for reading OTA images).

### What gets staged (~15-25MB in tmpfs)

| Component | Source | Size |
|-----------|--------|------|
| gammaos-ota | /system/bin/ | ~2MB |
| lptools | /system/bin/ | ~140KB |
| linker64 | /system/bin/ | ~2.2MB |
| libbase, liblog, liblp, libsparse, libfs_mgr, libutils, libcutils, libhidlbase, libc++, libcrypto, libext4_utils, libz, libfec, libselinux, libdm, libEGL, libGLESv2, libui, libgui, libbinder, libft2 + bionic libs | /system/lib64/ + /apex/ | ~10-18MB |
| Roboto font | /system/fonts/ | ~200KB |

Total: ~15-25MB. Well within tmpfs budget (devices have 2-8GB RAM).

## Flash Logic (verified on device)

### Overall Flow

```
Phase 0: Stage to tmpfs
├── Copy gammaos-ota + all deps to /dev/gammaos-ota-stage/
├── Re-exec from tmpfs
└── Now fully RAM-resident

Phase 1: Pre-flight checks
├── Check battery ≥ min_battery or AC power
├── Check ro.gammaos.device matches manifest
├── Verify SHA-256 of each .img.xz in the zip
├── Check lptools free ≥ total size increase for logical partitions
└── Check /data free space ≥ backup size (if backup requested)

Phase 2: Backup (optional, user-prompted)
├── For each partition to be flashed:
│   ├── dd if=/dev/block/dm-N | xz > /data/gammaos_ota/backup/{name}.img.xz
│   └── For physical: dd if=/dev/block/by-name/{name}_a | xz > backup
├── Save current manifest state to /data/gammaos_ota/backup/pre_ota_state.json
└── Update progress on screen

Phase 3: Stop Framework (keep SF alive for progress display)
├── stop zygote
├── (SurfaceFlinger KEPT ALIVE — needed for progress bar via HWC pipeline)
├── mount --bind /dev/gammaos-ota-stage/bin /system/bin
├── mount --bind /dev/gammaos-ota-stage/lib64 /system/lib64
├── sync()
└── echo 3 > /proc/sys/vm/drop_caches

Phase 4: Flash Physical Partitions (safe — not mounted)
├── For each physical partition in manifest:
│   ├── xz -d < /data/gammaos_ota/{file} > /dev/block/by-name/{name}_a
│   └── xz -d < /data/gammaos_ota/{file} > /dev/block/by-name/{name}_b
└── Update progress on screen

Phase 5: Flash Logical Partitions (point of no return)
├── For each logical partition in manifest:
│   ├── Get current dm device size via blockdev --getsize64
│   ├── If size changed (grow):
│   │   ├── lptools resize {name}_{slot} {new_size}
│   │   ├── Try lptools unmap/map
│   │   └── Fallback: dmctl replace (live-extend mounted dm device)
│   ├── XZ decompress → /data/gammaos_ota/staging/{name}.img  (staging file)
│   │   └── Progress: "Decompressing system... 47%" (cyan, updated every 250ms)
│   ├── Write staging file → /dev/block/dm-N  (block device write with progress)
│   │   └── Progress: "Writing... DO NOT POWER OFF" (yellow)
│   ├── Delete staging file
│   ├── If size changed (shrink): lptools resize metadata after write
│   └── Update progress on screen
└── (NOTE: /data stays mounted and readable throughout)

Phase 6: Post-flash verification
├── sync()
├── echo 3 > /proc/sys/vm/drop_caches
├── For each flashed partition:
│   ├── Read back from block device
│   ├── Compute SHA-256
│   └── Compare against sha256_uncompressed in manifest
├── If ALL pass → Phase 7 (success)
└── If ANY fail → show error UI with retry/restore options

Phase 7: Finalize
├── Display "Update complete! Rebooting..."
├── Cleanup: rm /data/gammaos_ota/backup/ (if backup was taken and flash succeeded)
├── sleep(2) or wait for user to press [A]
└── reboot
```

### Error Recovery

**On verification failure:**
- Show which partitions failed
- Offer three options:
  1. **Retry** — re-decompress and write the failed partitions again
  2. **Restore from backup** — only if backup was taken; reads backup images from `/data/gammaos_ota/backup/` and writes them back to the block devices
  3. **Reboot anyway** — advanced escape hatch, may not boot

**On mid-flash crash/power loss:**
- Physical partitions: device can still boot (old slot data intact on at least one slot... actually both were written, so this is risky for physical too if power dies mid-write)
- Logical partitions: partition is corrupted, fastboot recovery required
- If backup exists on `/data`: user can fastboot into recovery/fastbootd, mount data, and manually restore

### Backup Details

Backup is stored at `/data/gammaos_ota/backup/`:
```
/data/gammaos_ota/backup/
├── pre_ota_state.json       # records what was backed up + dm table state
├── system.img.xz            # compressed backup of system partition
├── vendor.img.xz            # compressed backup of vendor partition
├── boot.img.xz              # compressed backup of boot_a
└── ...
```

`pre_ota_state.json`:
```json
{
  "timestamp": 1710900000,
  "slot_suffix": "_a",
  "partitions": [
    {"name": "system_a", "dm_table": "linear 0 2378848 259:16 1908736 linear 2378848 4505696 259:16 4589144", "size": 3524886528},
    {"name": "vendor_a", "dm_table": "...", "size": 491520000},
    {"name": "boot_a", "block_dev": "/dev/block/by-name/boot_a", "size": 67108864}
  ]
}
```

This allows restore to also reconstruct the correct dm table and partition metadata if a resize was performed.

### Why Direct Block Device Write Works

- All logical partitions are mounted **read-only**
- Kernel allows raw writes to block devices under RO mounts
- `drop_caches` prevents stale page cache reads
- Framework is stopped and OTA binary runs from tmpfs — nothing reads from the overwritten partitions
- On reboot, everything is fresh

### Logical Partition Resize

When the new image is a different size than the current partition:

```bash
# 1. Update super partition metadata
lptools resize system_a 3629744128

# 2. Unmap the current dm device
lptools unmap system_a

# 3. Remap with new metadata (automatically uses the updated size)
lptools map system_a
```

Three simple steps. No lpdump, no dmctl, no extent parsing. The `lptools map` command reads the updated super partition metadata and creates the dm device with the correct size automatically.

### Physical Partitions — Both Slots

No slot switching is ever performed. Physical partitions are written to **both** `_a` and `_b` to ensure the device boots regardless of which slot the bootloader selects:

```
boot.img     → /dev/block/by-name/boot_a    AND boot_b
vendor_boot  → /dev/block/by-name/vendor_boot_a AND vendor_boot_b
dtbo         → /dev/block/by-name/dtbo_a    AND dtbo_b
vbmeta       → /dev/block/by-name/vbmeta_a  AND vbmeta_b
```

## Updater App Changes

Modified `packages/apps/Updater/`:

| File | Change |
|------|--------|
| `Constants.java` | Add `PROP_GAMMAOS_DEVICE`, `PROP_GAMMAOS_OTA_PACKAGE`, `PROP_GAMMAOS_OTA_AUTOINSTALL`, `GAMMAOS_OTA_DIR` |
| `Utils.java` | `getServerURL()` uses `ro.gammaos.device` for `{device}` placeholder |
| `GammaOtaInstaller.java` | **New** — extracts zip to `/data/gammaos_ota/`, launches `gammaos-ota` service |
| `UpdaterController.java` | Skip `RecoverySystem.verifyPackage()` for GammaOS OTA packages |
| `UpdateImporter.java` | Skip AOSP signature verification for GammaOS OTA zips (detected by `manifest.json`) |
| `UpdatesActivity.java` | "Install from storage" → file picker + direct install; "Local update" → file picker + import |
| `UpdatesListAdapter.java` | Persistent "GammaOS System Update" progress dialog after tapping OK (no gap) |
| `AndroidManifest.xml` | `android:usesCleartextTraffic="true"`, `android:sharedUserId="android.uid.system"` |
| `menu_toolbar.xml` | Added `menu_manual_update` item for "Install from storage" |
| `strings.xml` | Server URL template, manual update strings |

### Install Trigger (from app)

```java
// GammaOtaInstaller.java
SystemProperties.set("sys.gammaos.ota.package",
    "/data/gammaos_ota/gammaos_ota_v1.2.3.zip");
SystemProperties.set("ctl.start", "gammaos-ota");
```

## Settings Integration

Two entry points to OTA:

**A. Automatic** — GammaOS Updater app finds update → downloads → user taps "Install" → Mode A

**B. Manual** — Settings → System → GammaOS Update → launches `gammaos-ota` in Mode B (file browser)

```java
// Launch manual mode from Settings/LineageParts
SystemProperties.set("sys.gammaos.ota.package", "");
SystemProperties.set("ctl.start", "gammaos-ota");
```

## Vendor Config (per device)

Set in vendor build:
```makefile
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.gammaos.device=ayaneo_pocket_air \
    lineage.updater.uri=https://ota.gammaos.sh/api/v1/{device}
```

## Build Pipeline

```bash
# Build system image
make systemimage

# Package OTA
mkdir -p ota_staging
cp out/.../system.img ota_staging/
cp /work/GammaOSCoreVendor/out/vendor.img ota_staging/  # if vendor changed
xz -9 ota_staging/*.img

# Generate manifest (computes sha256 of compressed AND uncompressed images)
python3 tools/gen_ota_manifest.py \
    --version 1.2.3 \
    --device ayaneo_pocket_air \
    --dir ota_staging/ \
    --output ota_staging/manifest.json

# Package
cd ota_staging && zip ../gammaos_ota_v1.2.3.zip manifest.json *.img.xz

# Upload to CDN + update API JSON
```

## Safety Checks

| Check | When | Action on fail |
|-------|------|---------------|
| Battery ≥ `min_battery` or AC power | Before starting | Show error, return to menu |
| `ro.gammaos.device` matches manifest `device[]` | Before starting | Show "Wrong device" error |
| SHA-256 of each `.img.xz` (compressed) | Before flashing | Abort, show corrupt file error |
| `lptools free` ≥ total size increase | Before resize | Abort, "Not enough space in super" |
| `/data` free space ≥ backup size | Before backup | Skip backup, warn user |
| SHA-256 read-back of each partition (uncompressed) | After flashing | Offer retry / restore / reboot |
| `/data` still readable | Between partitions | Abort if `/data` died |

## Risk: No Fallback

If power is lost during logical partition write, the partition is corrupted and the device needs fastboot recovery. This is inherent to in-place writes without dual copies. Mitigated by:
- Battery/power check before starting
- Physical partitions flashed first (safe — both slots survive if completed)
- Logical partitions flashed in order of least critical first
- Optional backup before flash provides a restore path
- Post-flash checksum verification catches silent corruption

## SELinux Policy

`gammaos-ota` needs its own domain with broad block device + dm + display access. Following the pattern of existing GammaOS domains (`gammaoscustomization`, `gammapad`) and referencing `update_engine_common` for block device permissions.

### Files to create/modify

All changes must be mirrored across three directories:
- `system/sepolicy/private/` (source of truth)
- `system/sepolicy/prebuilts/api/34.0/private/` (API compat)
- `system/sepolicy/prebuilts/api/202404/private/` (API compat)

#### 1. New file: `gammaosota.te`

```te
# GammaOS OTA flasher domain
type gammaosota, domain, coredomain;
type gammaosota_exec, exec_type, system_file_type, file_type;

init_daemon_domain(gammaosota)

# ============================================================
# Block device access (modeled after update_engine_common)
# ============================================================

# Search /dev/block/ directory tree
allow gammaosota block_device:dir r_dir_perms;

# Read/write physical partition block devices (boot, dtbo, vbmeta, vendor_boot)
allow gammaosota boot_block_device:blk_file rw_file_perms;
allow gammaosota system_block_device:blk_file rw_file_perms;

# Read/write device-mapper block devices (logical partitions: system_a, vendor_a, etc.)
allow gammaosota dm_device:blk_file rw_file_perms;

# Read/write device-mapper control device (/dev/device-mapper)
# Needed by lptools unmap/map for resize
allow gammaosota dm_device:chr_file rw_file_perms;

# Read/write super partition metadata (lptools resize)
allow gammaosota super_block_device_type:blk_file rw_file_perms;
allowxperm gammaosota super_block_device_type:blk_file ioctl { BLKIOMIN BLKALIGNOFF };

# Block device ioctls (BLKGETSIZE64, BLKFLSBUF, BLKROGET, etc.)
allowxperm gammaosota dev_type:blk_file ioctl {
    BLKDISCARD
    BLKDISCARDZEROES
    BLKROGET
    BLKROSET
    BLKSECDISCARD
    BLKZEROOUT
};

# ============================================================
# Sysfs access (needed by libdm for dm path validation)
# ============================================================
r_dir_file(gammaosota, sysfs_dm)
allow gammaosota sysfs:dir r_dir_perms;

# ============================================================
# Filesystem / mount operations
# ============================================================

# Read /proc/mounts, /proc/cmdline, /proc/version
allow gammaosota proc:file r_file_perms;
allow gammaosota proc_cmdline:file r_file_perms;

# Write to /proc/sys/vm/drop_caches
allow gammaosota proc:file { write };

# Read rootfs (fstab)
allow gammaosota rootfs:dir getattr;
allow gammaosota rootfs:file r_file_perms;

# ============================================================
# Tmpfs staging (re-exec from /dev/gammaos-ota-stage/)
# ============================================================

# Create directories and files on tmpfs (/dev is tmpfs)
allow gammaosota tmpfs:dir create_dir_perms;
allow gammaosota tmpfs:file { create_file_perms execute execute_no_trans };
allow gammaosota tmpfs:filesystem { mount };

# Execute self and tools from tmpfs after staging
allow gammaosota gammaosota_exec:file { read execute execute_no_trans getattr open map };
allow gammaosota system_file:file { read open getattr execute execute_no_trans map };
allow gammaosota toolbox_exec:file { read open getattr execute execute_no_trans map };
allow gammaosota shell_exec:file { read open getattr execute execute_no_trans map };

# Allow re-exec (execve of self from tmpfs)
allow gammaosota self:process { execmem setexec };

# ============================================================
# /data access (reading OTA images, writing backups)
# ============================================================

# Read/write /data/gammaos_ota/
allow gammaosota system_data_file:dir create_dir_perms;
allow gammaosota system_data_file:file create_file_perms;

# Access media storage for manual OTA file selection (USB/SD)
allow gammaosota media_rw_data_file:dir r_dir_perms;
allow gammaosota media_rw_data_file:file r_file_perms;
allow gammaosota media_userdir_file:dir { search getattr open read };

# Traverse /mnt for external storage
allow gammaosota tmpfs:dir { search getattr open read };

# ============================================================
# Display rendering (same as bootanim/NanoMenu)
# ============================================================

# SurfaceFlinger binder access
binder_use(gammaosota)
binder_call(gammaosota, surfaceflinger)
allow gammaosota surfaceflinger_service:service_manager find;
allow gammaosota gpu_service:service_manager find;

# EGL/GLES rendering
allow gammaosota gpu_device:chr_file rw_file_perms;
allow gammaosota gpu_device:dir r_dir_perms;

# Vendor apex for GPU drivers
allow gammaosota vendor_apex_metadata_file:dir r_dir_perms;

# ============================================================
# Input handling (gamepad, keyboard, touch)
# ============================================================

allow gammaosota input_device:dir r_dir_perms;
allow gammaosota input_device:chr_file { read write open ioctl getattr };

# Hotplug via inotify on /dev/input
allow gammaosota device:dir { open read watch };

# ============================================================
# Properties
# ============================================================

# Read/write GammaOS properties (sys.gammaos.ota.*)
get_prop(gammaosota, persist_gammaos_prop)
set_prop(gammaosota, persist_gammaos_prop)

# Stop/start framework services
set_prop(gammaosota, ctl_stop_prop)
set_prop(gammaosota, ctl_start_prop)

# Read build properties
get_prop(gammaosota, build_prop)

# ============================================================
# Capabilities
# ============================================================

# sys_admin: mount, dm operations
# dac_override: read/write block devices regardless of ownership
# dac_read_search: traverse any directory
# sys_boot: reboot
# mknod: create block device nodes (if needed by lptools)
allow gammaosota self:capability { sys_admin dac_override dac_read_search sys_boot mknod fowner };

# ============================================================
# Reboot
# ============================================================

# Allow triggering reboot via sys.powerctl property
set_prop(gammaosota, powerctl_prop)

# ============================================================
# Metadata partition access (lptools reads metadata from here)
# ============================================================

allow gammaosota metadata_block_device:blk_file r_file_perms;
allow gammaosota metadata_file:dir r_dir_perms;
allow gammaosota ota_metadata_file:dir rw_dir_perms;
allow gammaosota ota_metadata_file:file create_file_perms;

# ============================================================
# Logging
# ============================================================

allow gammaosota logdr_socket:sock_file write;
allow gammaosota logd:unix_stream_socket connectto;
```

#### 2. Modify: `file_contexts`

Add:
```
/system/bin/gammaos-ota     u:object_r:gammaosota_exec:s0
```

#### 3. Modify: `property_contexts`

Add (in the `ctl.*` section):
```
ctl.gammaos-ota             u:object_r:ctl_gammaosota_prop:s0
```

And add the new property type:
```
sys.gammaos.ota.            u:object_r:gammaosota_prop:s0  prefix
```

#### 4. Modify: `property.te`

Add:
```te
# GammaOS OTA control and state properties
type gammaosota_prop, property_type;
type ctl_gammaosota_prop, property_type;
```

#### 5. Modify: `system_server.te` and/or `platform_app.te`

Allow the Updater app and Settings to start the OTA service:
```te
# Allow system_server / platform apps to start gammaos-ota
set_prop(system_server, ctl_gammaosota_prop)
set_prop(system_app, ctl_gammaosota_prop)
set_prop(platform_app, ctl_gammaosota_prop)

# Allow setting the OTA package path property
set_prop(system_app, gammaosota_prop)
set_prop(platform_app, gammaosota_prop)
set_prop(system_server, gammaosota_prop)
```

#### 6. Modify: `bootanim.te` (if NanoMenu needs to hand off to OTA)

If NanoMenu can trigger an OTA update:
```te
set_prop(bootanim, ctl_gammaosota_prop)
set_prop(bootanim, gammaosota_prop)
```

### SELinux File Summary

| File | Action | All 3 dirs |
|------|--------|-----------|
| `gammaosota.te` | Create | Yes |
| `file_contexts` | Add gammaos-ota entry | Yes |
| `property_contexts` | Add ctl.gammaos-ota + sys.gammaos.ota.* | Yes |
| `property.te` | Add gammaosota_prop + ctl_gammaosota_prop types | Yes |
| `system_server.te` | Add set_prop for ctl + ota props | Yes |
| `platform_app.te` | Add set_prop for ctl + ota props | Yes |
| `system_app.te` | Add set_prop for ctl + ota props | Yes |
| `bootanim.te` | Add set_prop (optional, if NanoMenu triggers OTA) | Yes |

### Development Approach

SELinux is enforcing from the start. Collect any denials via `adb shell dmesg | grep gammaosota` and add needed rules. The policy above is comprehensive but may need tuning based on actual audit logs.

## Build Order

1. **SELinux policy** — `gammaosota.te`, file_contexts, property_contexts, property.te (mirrored across all 3 prebuilt dirs). Enforcing from the start.
2. **`gammaos-ota` binary** — tmpfs staging, rendering, file browser, flash logic, verification
3. **`gammaos-ota.rc`** — init service definition
4. **SELinux cross-domain grants** — system_server.te, platform_app.te, system_app.te (allow starting the OTA service)
5. **`GammaOtaInstaller.java`** — Updater app bridge
6. **`gen_ota_manifest.py`** — build tool (generates manifest with compressed + uncompressed checksums)
7. **OTA server JSON** — static hosting on `ota.gammaos.sh`
8. **Settings entry point** — preference to launch manual mode
9. **Updater app changes** — use `ro.gammaos.device`, point to `ota.gammaos.sh`
10. **SELinux hardening** — collect audit denials, add any missing rules
