#
# Copyright 2020 The Android Open Source Project
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

PRODUCT_SHIPPING_API_LEVEL := 29
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_FULL_TREBLE_OVERRIDE := true
PRODUCT_OTA_ENFORCE_VINTF_KERNEL_REQUIREMENTS := false

#
# All components inherited here go to system image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

#
# All components inherited here go to system_ext image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/handheld_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)

#
# All components inherited here go to product image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_product.mk)

#
# All components inherited here go to vendor image
#
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_vendor.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/emulated_storage.mk)

PRODUCT_SOONG_NAMESPACES += device/generic/goldfish

PRODUCT_PACKAGES += \
    android.hardware.audio.service \
    android.hardware.audio@6.0-impl:32 \
    android.hardware.audio.effect@6.0-impl:32 \
    audio.primary.default \
    audio.r_submix.default \
    android.hardware.drm@1.0-service \
    android.hardware.drm@1.0-impl \
    android.hardware.drm-service.clearkey \
    android.hardware.gatekeeper@1.0-service.software \
    android.hardware.graphics.allocator@2.0-service \
    android.hardware.graphics.allocator@2.0-impl \
    android.hardware.graphics.composer@2.1-service \
    android.hardware.graphics.mapper@2.0-impl \
    android.hardware.health@2.0-service \
    android.hardware.neuralnetworks@1.3-service-sample-all \
    android.hardware.neuralnetworks@1.3-service-sample-limited \
    android.hardware.keymaster@4.0-service \
    android.hardware.keymaster@4.0-impl \
    gralloc.minigbm \
    hwcomposer.drm_minigbm \
    libEGL_angle \
    libGLESv1_CM_angle \
    libGLESv2_angle \
    vulkan.pastel \

PRODUCT_HOST_PACKAGES += bind_to_localhost

PRODUCT_PACKAGE_OVERLAYS := device/generic/goldfish/fvpbase/overlay

PRODUCT_NAME := fvp
PRODUCT_DEVICE := fvpbase
PRODUCT_BRAND := Android
PRODUCT_MODEL := AOSP on FVP

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.ethernet.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.ethernet.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.usb.host.xml \
    frameworks/native/data/etc/android.software.app_widgets.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.app_widgets.xml \
    device/generic/goldfish/fvpbase/fstab.fvpbase:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.fvpbase \
    device/generic/goldfish/fvpbase/fstab.qemu:$(TARGET_COPY_OUT_VENDOR)/etc/fstab.qemu \
    device/generic/goldfish/fvpbase/fstab.initrd:$(TARGET_COPY_OUT_RAMDISK)/fstab.fvpbase \
    device/generic/goldfish/fvpbase/fstab.initrd:$(TARGET_COPY_OUT_RAMDISK)/fstab.qemu \
    device/generic/goldfish/fvpbase/init.fvpbase.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.fvpbase.rc \
    device/generic/goldfish/fvpbase/init.qemu.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.qemu.rc \
    device/generic/goldfish/fvpbase/required_images:required_images \
    device/generic/goldfish/fvpbase/ueventd.fvp.rc:$(TARGET_COPY_OUT_VENDOR)/ueventd.rc \
    frameworks/av/services/audiopolicy/config/audio_policy_configuration_generic.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/primary_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/primary_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    frameworks/av/services/audiopolicy/config/surround_sound_configuration_5_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/surround_sound_configuration_5_0.xml \

PRODUCT_BUILD_BOOT_IMAGE := true

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    qemu.hw.mainkeys=0 \
    ro.hw_timeout_multiplier=50 \
    debug.sf.nobootanimation=1 \
    ro.hardware.egl=angle \
    ro.hardware.vulkan=pastel \

PRODUCT_REQUIRES_INSECURE_EXECMEM_FOR_SWIFTSHADER := true

# It's almost always faster to dexopt on the host even in eng builds.
WITH_DEXPREOPT_BOOT_IMG_AND_SYSTEM_SERVER_ONLY := false

DEVICE_MANIFEST_FILE += device/generic/goldfish/fvpbase/manifest.xml

# Use a multilib setup (see fvpbase/BoardConfig.mk).
FVP_MULTILIB_BUILD := true

