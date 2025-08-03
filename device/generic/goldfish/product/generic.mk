#
# Copyright (C) 2023 The Android Open Source Project
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
# This file is to configure vendor/data partitions of emulator-related products
#
$(call inherit-product-if-exists, frameworks/native/build/phone-xhdpi-2048-dalvik-heap.mk)

# Enable Scoped Storage related
$(call inherit-product, $(SRC_TARGET_DIR)/product/emulated_storage.mk)

PRODUCT_SHIPPING_API_LEVEL := 34
PRODUCT_FULL_TREBLE_OVERRIDE := true
DEVICE_MANIFEST_FILE += device/generic/goldfish/manifest.xml

PRODUCT_SOONG_NAMESPACES += \
    device/generic/goldfish \
    device/generic/goldfish-opengl

TARGET_USES_MKE2FS := true

# RKPD
PRODUCT_PRODUCT_PROPERTIES += \
    remote_provisioning.enable_rkpd=true \
    remote_provisioning.hostname=remoteprovisioning.googleapis.com

PRODUCT_VENDOR_PROPERTIES += \
    ro.control_privapp_permissions=enforce \
    ro.crypto.dm_default_key.options_format.version=2 \
    ro.crypto.volume.filenames_mode=aes-256-cts \
    ro.hardware.audio.tinyalsa.period_count=4 \
    ro.hardware.audio.tinyalsa.period_size_multiplier=2 \
    ro.hardware.audio.tinyalsa.host_latency_ms=80 \
    ro.hardware.power=ranchu \
    ro.incremental.enable=yes \
    ro.logd.size=1M \
    ro.kernel.qemu=1 \
    ro.soc.manufacturer=AOSP \
    ro.soc.model=ranchu \
    ro.surface_flinger.has_HDR_display=false \
    ro.surface_flinger.has_wide_color_display=false \
    ro.surface_flinger.protected_contents=false \
    ro.surface_flinger.supports_background_blur=1 \
    ro.surface_flinger.use_color_management=false \
    ro.zygote.disable_gl_preload=1 \
    debug.sf.vsync_reactor_ignore_present_fences=true \
    debug.stagefright.c2inputsurface=-1 \
    debug.stagefright.ccodec=4 \
    graphics.gpu.profiler.support=true \
    persist.sys.zram_enabled=1 \
    wifi.direct.interface=p2p-dev-wlan0 \
    wifi.interface=wlan0 \

# Device modules
PRODUCT_PACKAGES += \
    android.hardware.drm-service-lazy.clearkey \
    android.hardware.gatekeeper@1.0-service.software \
    android.hardware.usb-service.example \
    vulkan.ranchu \
    libandroidemu \
    libOpenglCodecCommon \
    libOpenglSystemCommon \
    qemu-export-property \
    qemu-props \
    stagefright \
    android.hardware.graphics.composer3-service.ranchu \
    toybox_vendor \
    android.hardware.wifi-service \
    android.hardware.media.c2@1.0-service-goldfish \
    libcodec2_goldfish_vp8dec \
    libcodec2_goldfish_vp9dec \
    libcodec2_goldfish_avcdec \
    libcodec2_goldfish_hevcdec \
    sh_vendor \
    local_time.default \
    SdkSetup \
    goldfish_overlay_connectivity_gsi \
    libGoldfishProfiler \
    dlkm_loader

ifneq ($(filter %_minigbm, $(TARGET_PRODUCT)),)
PRODUCT_VENDOR_PROPERTIES += ro.hardware.gralloc=minigbm
PRODUCT_PACKAGES += \
    android.hardware.graphics.allocator-service.minigbm \
    android.hardware.graphics.mapper@4.0-impl.minigbm \
    mapper.minigbm
else
PRODUCT_VENDOR_PROPERTIES += ro.hardware.gralloc=ranchu
PRODUCT_PACKAGES += \
    android.hardware.graphics.allocator@3.0-service.ranchu \
    android.hardware.graphics.mapper@3.0-impl-ranchu
endif

ifneq ($(EMULATOR_DISABLE_RADIO),true)
PRODUCT_PACKAGES += \
    libcuttlefish-ril-2 \
    libgoldfish-rild \
    EmulatorRadioConfig \
    EmulatorTetheringConfigOverlay

DEVICE_MANIFEST_FILE += device/generic/goldfish/radio/manifest.radio.xml
DISABLE_RILD_OEM_HOOK := true
endif

ifneq ($(EMULATOR_VENDOR_NO_BIOMETRICS), true)
PRODUCT_PACKAGES += \
    android.hardware.biometrics.fingerprint-service.ranchu \
    android.hardware.biometrics.face-service.example \

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.fingerprint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.fingerprint.xml \
    frameworks/native/data/etc/android.hardware.biometrics.face.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.biometrics.face.xml \

endif

ifneq ($(BUILD_EMULATOR_OPENGL),false)
PRODUCT_PACKAGES += \
    libGLESv1_CM_emulation \
    lib_renderControl_enc \
    libEGL_emulation \
    libGLESv2_enc \
    libvulkan_enc \
    libGLESv2_emulation \
    libGLESv1_enc \
    libEGL_angle \
    libGLESv1_CM_angle \
    libGLESv2_angle
endif

# Enable bluetooth
PRODUCT_PACKAGES += \
    android.hardware.bluetooth-service.default \
    android.hardware.bluetooth.audio-impl \
    bt_vhci_forwarder \

# Bluetooth hardware properties.
ifeq ($(TARGET_PRODUCT_PROP),)
TARGET_PRODUCT_PROP := $(LOCAL_PATH)/bluetooth.prop
endif

PRODUCT_PACKAGES += \
    android.hardware.security.keymint-service
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.keystore.app_attest_key.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.keystore.app_attest_key.xml

PRODUCT_PACKAGES += \
    DisplayCutoutEmulationEmu01Overlay \
    EmulationPixelFoldOverlay \
    SystemUIEmulationPixelFoldOverlay \
    EmulationPixel8ProOverlay \
    SystemUIEmulationPixel8ProOverlay \
    EmulationPixel8Overlay \
    SystemUIEmulationPixel8Overlay \
    EmulationPixel7ProOverlay \
    SystemUIEmulationPixel7ProOverlay \
    EmulationPixel7Overlay \
    SystemUIEmulationPixel7Overlay \
    EmulationPixel7aOverlay \
    SystemUIEmulationPixel7aOverlay \
    EmulationPixel6ProOverlay \
    SystemUIEmulationPixel6ProOverlay \
    EmulationPixel6Overlay \
    SystemUIEmulationPixel6Overlay \
    EmulationPixel6aOverlay \
    SystemUIEmulationPixel6aOverlay \
    EmulationPixel5Overlay \
    SystemUIEmulationPixel5Overlay \
    EmulationPixel4XLOverlay \
    SystemUIEmulationPixel4XLOverlay \
    EmulationPixel4Overlay \
    SystemUIEmulationPixel4Overlay \
    EmulationPixel4aOverlay \
    SystemUIEmulationPixel4aOverlay \
    EmulationPixel3XLOverlay \
    SystemUIEmulationPixel3XLOverlay \
    EmulationPixel3Overlay \
    SystemUIEmulationPixel3Overlay \
    EmulationPixel3aOverlay \
    SystemUIEmulationPixel3aOverlay \
    EmulationPixel3aXLOverlay \
    SystemUIEmulationPixel3aXLOverlay \
    EmulationPixel2XLOverlay \
    NavigationBarMode2ButtonOverlay \

ifneq ($(EMULATOR_VENDOR_NO_GNSS),true)
PRODUCT_PACKAGES += android.hardware.gnss-service.ranchu
endif

ifneq ($(EMULATOR_VENDOR_NO_SENSORS),true)
PRODUCT_PACKAGES += \
    android.hardware.sensors-service.multihal \
    android.hardware.sensors@2.1-impl.ranchu
# TODO(rkir):
# add a soong namespace and move this into a.h.sensors@2.1-impl.ranchu
# as prebuilt_etc. For now soong_namespace causes a build break because the fw
# refers to our wifi HAL in random places.
PRODUCT_COPY_FILES += \
    device/generic/goldfish/sensors/hals.conf:$(TARGET_COPY_OUT_VENDOR)/etc/sensors/hals.conf
endif

ifneq ($(EMULATOR_VENDOR_NO_CAMERA),true)
PRODUCT_SOONG_NAMESPACES += \
    hardware/google/camera \
    hardware/google/camera/devices/EmulatedCamera \

PRODUCT_PACKAGES += \
    android.hardware.camera.provider.ranchu \
    android.hardware.camera.provider@2.7-service-google \
    libgooglecamerahwl_impl \

PRODUCT_COPY_FILES += \
    device/generic/goldfish/camera/media/profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_V1_0.xml \
    device/generic/goldfish/camera/media/codecs_google_video_default.xml:${TARGET_COPY_OUT_VENDOR}/etc/media_codecs_google_video.xml \
    device/generic/goldfish/camera/media/codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    device/generic/goldfish/camera/media/codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    device/generic/goldfish/camera/media/codecs_performance_c2.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_c2.xml \
    frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.flash-autofocus.xml \
    frameworks/native/data/etc/android.hardware.camera.concurrent.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.concurrent.xml \
    frameworks/native/data/etc/android.hardware.camera.front.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.front.xml \
    frameworks/native/data/etc/android.hardware.camera.full.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.full.xml \
    frameworks/native/data/etc/android.hardware.camera.raw.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.camera.raw.xml \
    hardware/google/camera/devices/EmulatedCamera/hwl/configs/emu_camera_back.json:$(TARGET_COPY_OUT_VENDOR)/etc/config/emu_camera_back.json \
    hardware/google/camera/devices/EmulatedCamera/hwl/configs/emu_camera_front.json:$(TARGET_COPY_OUT_VENDOR)/etc/config/emu_camera_front.json \
    hardware/google/camera/devices/EmulatedCamera/hwl/configs/emu_camera_depth.json:$(TARGET_COPY_OUT_VENDOR)/etc/config/emu_camera_depth.json \

endif

ifneq ($(EMULATOR_VENDOR_NO_SOUND),true)
PRODUCT_PACKAGES += \
    android.hardware.audio.service \
    android.hardware.audio@7.1-impl.ranchu \
    android.hardware.audio.effect@7.0-impl \

DEVICE_MANIFEST_FILE += device/generic/goldfish/audio/android.hardware.audio.effects@7.0.xml

PRODUCT_COPY_FILES += \
    device/generic/goldfish/audio/policy/audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_configuration.xml \
    device/generic/goldfish/audio/policy/primary_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/primary_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/bluetooth_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/bluetooth_audio_policy_configuration_7_0.xml \
    frameworks/av/services/audiopolicy/config/r_submix_audio_policy_configuration.xml:$(TARGET_COPY_OUT_VENDOR)/etc/r_submix_audio_policy_configuration.xml \
    frameworks/av/services/audiopolicy/config/audio_policy_volumes.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_policy_volumes.xml \
    frameworks/av/services/audiopolicy/config/default_volume_tables.xml:$(TARGET_COPY_OUT_VENDOR)/etc/default_volume_tables.xml \
    frameworks/av/media/libeffects/data/audio_effects.xml:$(TARGET_COPY_OUT_VENDOR)/etc/audio_effects.xml \

endif

# WiFi: vendor side
PRODUCT_PACKAGES += \
    mac80211_create_radios \
    dhcpclient \
    hostapd \
    wpa_supplicant \

# Window Extensions
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)

# "Hello, world!" HAL implementations, mostly for compliance
PRODUCT_PACKAGES += \
    android.hardware.atrace@1.0-service \
    com.android.hardware.authsecret \
    android.hardware.contexthub-service.example \
    android.hardware.dumpstate-service.example \
    android.hardware.health-service.example \
    android.hardware.health.storage-service.default \
    android.hardware.lights-service.example \
    com.android.hardware.neuralnetworks \
    android.hardware.power-service.example \
    android.hardware.power.stats-service.example \
    com.android.hardware.rebootescrow \
    android.hardware.thermal@2.0-service.mock \
    android.hardware.vibrator-service.example

# TVs don't use a hardware identity service.
ifneq ($(PRODUCT_IS_ATV_SDK),true)
    PRODUCT_PACKAGES += \
        android.hardware.identity-service.example
endif

PRODUCT_COPY_FILES += \
    device/generic/goldfish/data/empty_data_disk:data/empty_data_disk \
    device/generic/goldfish/data/etc/dtb.img:dtb.img \
    device/generic/goldfish/data/etc/encryptionkey.img:encryptionkey.img \
    device/generic/goldfish/emulator-info.txt:data/misc/emulator/version.txt \
    device/generic/goldfish/data/etc/apns-conf.xml:data/misc/apns/apns-conf.xml \
    device/generic/goldfish/radio/RadioConfig/radioconfig.xml:data/misc/emulator/config/radioconfig.xml \
    device/generic/goldfish/data/etc/iccprofile_for_sim0.xml:data/misc/modem_simulator/iccprofile_for_sim0.xml \
    device/google/cuttlefish/host/commands/modem_simulator/files/iccprofile_for_sim0_for_CtsCarrierApiTestCases.xml:data/misc/modem_simulator/iccprofile_for_carrierapitests.xml \
    device/generic/goldfish/data/etc/numeric_operator.xml:data/misc/modem_simulator/etc/modem_simulator/files/numeric_operator.xml \
    device/generic/goldfish/data/etc/local.prop:data/local.prop \
    device/generic/goldfish/init.ranchu.adb.setup.sh:$(TARGET_COPY_OUT_SYSTEM_EXT)/bin/init.ranchu.adb.setup.sh \
    device/generic/goldfish/init.ranchu-core.sh:$(TARGET_COPY_OUT_VENDOR)/bin/init.ranchu-core.sh \
    device/generic/goldfish/init.ranchu-net.sh:$(TARGET_COPY_OUT_VENDOR)/bin/init.ranchu-net.sh \
    device/generic/goldfish/init.ranchu.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/hw/init.ranchu.rc \
    device/generic/goldfish/init.system_ext.rc:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/init/init.system_ext.rc \
    device/generic/goldfish/ueventd.ranchu.rc:$(TARGET_COPY_OUT_VENDOR)/etc/ueventd.rc \
    device/generic/goldfish/input/virtio_input_rotary.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_rotary.idc \
    device/generic/goldfish/input/qwerty2.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/qwerty2.idc \
    device/generic/goldfish/input/qwerty2.kcm:$(TARGET_COPY_OUT_VENDOR)/usr/keychars/qwerty2.kcm \
    device/generic/goldfish/input/qwerty2.kl:$(TARGET_COPY_OUT_VENDOR)/usr/keylayout/qwerty2.kl \
    device/generic/goldfish/input/virtio_input_multi_touch_1.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_1.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_2.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_2.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_3.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_3.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_4.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_4.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_5.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_5.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_6.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_6.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_7.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_7.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_8.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_8.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_9.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_9.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_10.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_10.idc \
    device/generic/goldfish/input/virtio_input_multi_touch_11.idc:$(TARGET_COPY_OUT_VENDOR)/usr/idc/virtio_input_multi_touch_11.idc \
    device/generic/goldfish/display_settings_app_compat.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings_app_compat.xml \
    device/generic/goldfish/display_settings_freeform.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings_freeform.xml \
    device/generic/goldfish/display_settings.xml:$(TARGET_COPY_OUT_VENDOR)/etc/display_settings.xml \
    device/generic/goldfish/pixel_fold/device_state_configuration.xml:/data/misc/pixel_fold/devicestate/device_state_configuration.xml \
    device/generic/goldfish/pixel_fold/display_layout_configuration.xml:/data/misc/pixel_fold/displayconfig/display_layout_configuration.xml \
    device/generic/goldfish/pixel_fold/display_settings.xml:/data/misc/pixel_fold/display_settings.xml \
    device/generic/goldfish/data/etc/config.ini:config.ini \
    device/generic/goldfish/wifi/wpa_supplicant.conf:$(TARGET_COPY_OUT_VENDOR)/etc/wifi/wpa_supplicant.conf \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.wifi.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.passpoint.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.passpoint.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_audio.xml \
    frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_google_telephony.xml \
    frameworks/native/data/etc/android.hardware.touchscreen.multitouch.jazzhand.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.touchscreen.multitouch.jazzhand.xml \
    frameworks/native/data/etc/android.hardware.vulkan.level-1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.level.xml \
    frameworks/native/data/etc/android.hardware.vulkan.compute-0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.compute.xml \
    frameworks/native/data/etc/android.hardware.vulkan.version-1_3.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.hardware.vulkan.version.xml \
    frameworks/native/data/etc/android.software.vulkan.deqp.level-latest.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.vulkan.deqp.level.xml \
    frameworks/native/data/etc/android.software.opengles.deqp.level-latest.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.opengles.deqp.level.xml \
    frameworks/native/data/etc/android.software.autofill.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.autofill.xml \
    frameworks/native/data/etc/android.software.verified_boot.xml:${TARGET_COPY_OUT_PRODUCT}/etc/permissions/android.software.verified_boot.xml \
    device/generic/goldfish/data/etc/permissions/privapp-permissions-goldfish.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/permissions/privapp-permissions-goldfish.xml \

ifneq ($(EMULATOR_DISABLE_RADIO),true)
# Android TV ingests this file, but declares its own set of hardware permissions.
ifneq ($(PRODUCT_IS_ATV_SDK),true)
    PRODUCT_COPY_FILES+= \
        device/generic/goldfish/data/etc/handheld_core_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/handheld_core_hardware.xml
endif
endif

# Goldfish uses 6.X kernels.
PRODUCT_ENABLE_UFFD_GC := true
