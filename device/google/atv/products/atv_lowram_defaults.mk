#
# Copyright (C) 2020 The Android Open Source Project
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
#

# Sets Android TV Low RAM recommended default product options.

# Set lowram options and enable traced by default
PRODUCT_VENDOR_PROPERTIES += ro.config.low_ram=true

# Minimize boot-time dex compilation to avoid OOM on 1GB devices.
# verify is the fastest filter - just checks DEX integrity, no compilation.
PRODUCT_SYSTEM_SERVER_COMPILER_FILTER := verify

# Limit dex2oat concurrency to prevent OOM during boot.
PRODUCT_SYSTEM_PROPERTIES += \
    dalvik.vm.dex2oat-threads=2 \
    dalvik.vm.boot-dex2oat-threads=2 \
    dalvik.vm.image-dex2oat-threads=2 \
    pm.dexopt.boot=verify \
    pm.dexopt.boot-after-mainline-update=verify \
    pm.dexopt.first-boot=quicken \
    pm.dexopt.install=quicken \
    ro.odsign.skip_verification=1 \
    ro.permission.grant.timeout_ms=3000 \
    ro.role.grant.timeout_ms=3000 \
    config.disable_systemtextclassifier=true \
    ro.gammaos.lean_boot=true \
    ro.gammaos.composition_timeout_ms=2000 \
    sys.read_ahead_kb=2048 \
    vm.swappiness=100 \
    ro.sys.fw.bg_apps_limit=1 \
    persist.sys.fw.bg_apps_limit=1

# Zygote arch. ZYGOTE_FORCE_64 keeps the FLEET DEFAULT 64-bit-only (saves ~105MB by
# not forking the 32-bit zygote) for any device that does not set its own ro.zygote.
# A device opts into 32-bit app support (armeabi-v7a) by shipping ro.zygote=zygote64_32
# in its vendor build.prop (e.g. the TrimUI Brick).
# GammaOS LAZY 32-bit zygote: keep ro.zygote.disable_secondary=1 so init does NOT auto-start
# the 32-bit secondary at boot (no ~105MB cost on devices that never run a 32-bit app);
# instead ZygoteProcess starts it on the first 32-bit app fork and AMS reaps it (gated by
# persist.gammaos.lazy32) ~15s after the last 32-bit app exits. disable_secondary is read
# only on the boot-enable triggers and ZygoteProcess.bootCompleted/waitForConnection, NOT on
# the fork path, so on-demand forking still works. A zygote64_32 vendor (the Brick) thus gets
# dynamic 32-bit support; zygote64 vendors have no secondary so these props are no-ops.
ZYGOTE_FORCE_64 := true
PRODUCT_SYSTEM_PROPERTIES += \
    ro.zygote.disable_secondary=1 \
    persist.gammaos.lazy32=1

# Remove packages not needed on low-RAM ATV devices.
PRODUCT_REMOVE_PACKAGES += \
    SecureElement

# Always preopt extracted APKs to prevent extracting out of the APK for gms
# modules.
PRODUCT_ALWAYS_PREOPT_EXTRACTED_APK := true

# Use a profile based boot image for this device. Note that this is currently a
# generic profile and not Android Go optimized.
PRODUCT_USE_PROFILE_FOR_BOOT_IMAGE := true
PRODUCT_DEX_PREOPT_BOOT_IMAGE_PROFILE_LOCATION := frameworks/base/config/boot-image-profile.txt

# Add Go defaults but override LMK to be less aggressive than go_defaults.
# Vendor sets downgrade_pressure=80 (conservative); go_defaults forces 60 (aggressive).
# On 1GB with zram+swap, vendor's settings are better.
TARGET_SYSTEM_PROP += \
    build/make/target/board/go_defaults_common.prop

PRODUCT_SYSTEM_PROPERTIES += \
    ro.lmk.downgrade_pressure=99 \
    ro.lmk.upgrade_pressure=10 \
    ro.lmk.swap_free_low_percentage=0 \
    ro.lmk.psi_complete_stall_ms=1000 \
    ro.lmk.psi_partial_stall_ms=200 \
    ro.lmk.thrashing_limit=80 \
    ro.lmk.thrashing_limit_decay=50 \
    ro.lmk.swap_util_max=95 \
    ro.lmk.kill_timeout_ms=100

# Ensure boot image matches runtime GC (CC with read barriers).
PRODUCT_ART_USE_READ_BARRIER := true

# Dedupe VNDK libraries with identical core variants.
TARGET_VNDK_USE_CORE_VARIANT := true

# Use the low memory allocator outside of eng builds to save RSS.
ifneq (,$(filter eng, $(TARGET_BUILD_VARIANT)))
  MALLOC_SVELTE := true
endif
