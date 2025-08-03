#
# Copyright 2020 Arm Ltd. All rights reserved.
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

$(call inherit-product, $(LOCAL_PATH)/minimal_system.mk)

$(call inherit-product, $(SRC_TARGET_DIR)/product/updatable_apex.mk)

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_no_zygote.mk)

PRODUCT_NAME := fvp_mini
PRODUCT_DEVICE := fvpbase
PRODUCT_BRAND := Android
PRODUCT_MODEL := AOSP on FVP
PRODUCT_SOONG_NAMESPACES += device/generic/goldfish

PRODUCT_SHIPPING_API_LEVEL := 29
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_FULL_TREBLE_OVERRIDE := true
PRODUCT_OTA_ENFORCE_VINTF_KERNEL_REQUIREMENTS := false
PRODUCT_BUILD_BOOT_IMAGE := true

# Use a multilib setup (see fvpbase/BoardConfig.mk).
FVP_MULTILIB_BUILD ?= true

# The check would fail because there are no boot jars.
SKIP_BOOT_JARS_CHECK ?= true

PRODUCT_PACKAGES += \
    com.android.runtime \
    init_vendor \
    ip \
    ping \
    selinux_policy_nonsystem \

PRODUCT_HOST_PACKAGES += \
    bind_to_localhost

PRODUCT_COPY_FILES += \
    device/generic/goldfish/fvpbase/fstab.fvpbase:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.fvpbase \
    device/generic/goldfish/fvpbase/fstab.qemu:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.qemu \
    device/generic/goldfish/fvpbase/fstab.initrd:$(TARGET_COPY_OUT_RAMDISK)/fstab.fvpbase \
    device/generic/goldfish/fvpbase/fstab.initrd:$(TARGET_COPY_OUT_RAMDISK)/fstab.qemu \
    device/generic/goldfish/fvpbase/init.fvpbase.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.fvpbase.rc \
    device/generic/goldfish/fvpbase/init.qemu.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.qemu.rc \
    device/generic/goldfish/fvpbase/mini_network.rc:system/etc/init/mini_network.rc \
    device/generic/goldfish/fvpbase/mini_network.sh:/system/bin/mini_network.sh \
    device/generic/goldfish/fvpbase/required_images:required_images \
