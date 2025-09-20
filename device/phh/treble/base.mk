#Huawei devices don't declare fingerprint and telephony hardware feature
#TODO: Proper detection
PRODUCT_COPY_FILES := \
	frameworks/native/data/etc/android.hardware.fingerprint.xml:system/etc/permissions/android.hardware.fingerprint.xml \
	frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
	frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
	frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml \

# Bluetooth Audio (System-side HAL, sysbta)
PRODUCT_PACKAGES += \
    audio.sysbta.default \
    android.hardware.bluetooth.audio-service-system

PRODUCT_COPY_FILES += \
    device/phh/treble/bluetooth/audio/config/sysbta_audio_policy_configuration.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysbta_audio_policy_configuration.xml \
    device/phh/treble/bluetooth/audio/config/sysbta_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysbta_audio_policy_configuration_7_0.xml

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += device/phh/treble/sepolicy

PRODUCT_PACKAGE_OVERLAYS += \
	device/phh/treble/overlay \
	device/phh/treble/overlay-lineage

PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += \
	device/phh/treble/overlay-lineage/lineage-sdk

#$(call inherit-product, vendor/hardware_overlay/overlay.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
#$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_base_telephony.mk)

#Those overrides are here because Huawei's init read properties
#from /system/etc/prop.default, then /vendor/build.prop, then /system/build.prop
#So we need to set our props in prop.default
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
	ro.build.version.sdk=$(PLATFORM_SDK_VERSION) \
	ro.build.version.codename=$(PLATFORM_VERSION_CODENAME) \
	ro.build.version.all_codenames=$(PLATFORM_VERSION_ALL_CODENAMES) \
	ro.build.version.release=$(PLATFORM_VERSION) \
	ro.build.version.security_patch=$(PLATFORM_SECURITY_PATCH) \
	ro.adb.secure=0 \
	ro.logd.auditd=true \
	ro.logd.kernel=true \
	ro.surface_flinger.supports_background_blur=1 \

PRODUCT_VENDOR_PROPERTIES += \
       ro.surface_flinger.supports_background_blur=1

#Huawei HiSuite (also other OEM custom programs I guess) it's of no use in AOSP builds
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
	persist.sys.usb.config=adb \
	ro.cust.cdrom=/dev/null

#VNDK config files
PRODUCT_COPY_FILES += \
	device/phh/treble/vndk-detect:system/bin/vndk-detect \
	device/phh/treble/vndk.rc:system/etc/init/vndk.rc

#Charger config files
PRODUCT_COPY_FILES += \
	device/phh/treble/charger.rc:system/etc/init/charger.rc

#USB Audio
PRODUCT_COPY_FILES += \
	frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:system/etc/usb_audio_policy_configuration.xml \
	device/phh/treble/files/fake_audio_policy_volume.xml:system/etc/fake_audio_policy_volume.xml \

# NFC:
#   Provide default libnfc-nci.conf file for devices that does not have one in
#   vendor/etc
PRODUCT_COPY_FILES += \
	device/phh/treble/nfc/libnfc-nci.conf:system/phh/libnfc-nci-oreo.conf \
	device/phh/treble/nfc/libnfc-nci-huawei.conf:system/phh/libnfc-nci-huawei.conf

# LineageOS build may need this to make NFC work
PRODUCT_PACKAGES += \
        NfcNci \

PRODUCT_COPY_FILES += \
	device/phh/treble/rw-system.sh:system/bin/rw-system.sh \
	device/phh/treble/phh-on-data.sh:system/bin/phh-on-data.sh \
	device/phh/treble/phh-prop-handler.sh:system/bin/phh-prop-handler.sh \
	device/phh/treble/fixSPL/getSPL.arm:system/bin/getSPL

PRODUCT_COPY_FILES += \
	device/phh/treble/empty:system/phh/empty \
	device/phh/treble/phh-on-boot.sh:system/bin/phh-on-boot.sh

PRODUCT_PACKAGES += \
	treble-environ-rc \

PRODUCT_PACKAGES += \
	bootctl \
	vintf \


PRODUCT_COPY_FILES += \
	device/phh/treble/twrp/twrp.rc:system/etc/init/twrp.rc \
	device/phh/treble/twrp/twrp.sh:system/bin/twrp.sh \
	device/phh/treble/twrp/busybox-armv7l:system/bin/busybox_phh

PRODUCT_PACKAGES += \
    simg2img_simple \
    lptools

ifneq (,$(wildcard external/exfat))
PRODUCT_PACKAGES += \
	mkfs.exfat \
	fsck.exfat
endif

PRODUCT_PACKAGES += \
	android.hidl.manager-V1.0-java \
	vendor.huawei.hardware.biometrics.fingerprint-V2.1-java \
	vendor.huawei.hardware.tp-V1.0-java

PRODUCT_COPY_FILES += \
	device/phh/treble/interfaces.xml:system/etc/permissions/interfaces.xml

# GammaOS Customizations
PRODUCT_COPY_FILES += \
    gammaos/utils/xz:system/bin/xz \
    gammaos/utils/com.gamma.analogcalibrator.apk:system/app/GammaAnalog/com.gamma.analogcalibrator.apk \
    gammaos/utils/inotifywait:system/bin/inotifywait \
    gammaos/utils/firefox-fenix-139.0.multi.android-arm64-v8a.apk:system/etc/firefox-fenix-139.0.multi.android-arm64-v8a.apk \
    gammaos/customization.sh:system/bin/customization.sh \
    gammaos/magisk/magisk.apk:system/etc/magisk.apk \
    gammaos/magisk/magisk.tar.gz:system/etc/magisk.tar.gz \
    gammaos/magisk/magisk2.tar.gz:system/etc/magisk2.tar.gz \
    gammaos/retroarch/RetroArch_aarch64.apk:system/etc/RetroArch_aarch64.apk \
    gammaos/retroarch/retroarch.tar.xz:system/etc/retroarch.tar.xz \
    gammaos/retroarch/roms.tar.xz:system/etc/roms.tar.xz \
    gammaos/setup.sh:system/bin/setup.sh \
    gammaos/launcher/MiXplorer_v6.64.3-API29_B23090720.apk:system/etc/MiXplorer_v6.64.3-API29_B23090720.apk \
    gammaos/launcher/AuroraStore_4.6.2.apk:system/etc/AuroraStore_4.6.2.apk \
    gammaos/launcher/aurorastore.tar.gz:system/etc/aurorastore.tar.gz \
    gammaos/daijisho/daijisho412.apk.xz:system/etc/daijisho412.apk.xz \
    gammaos/daijisho/daijisho.tar.xz:system/etc/daijisho.tar.xz \
    gammaos/emulators/drastic.tar.gz:system/etc/drastic.tar.gz \
    gammaos/emulators/drastic_r2.6.0.4a.apk:system/etc/drastic_r2.6.0.4a.apk \
    gammaos/emulators/mupen64plusae.tar.gz:system/etc/mupen64plusae.tar.gz \
    gammaos/emulators/mupen64plusae_3.0.335.apk:system/etc/mupen64plusae_3.0.335.apk \
    gammaos/emulators/ppsspp.tar.xz:system/etc/ppsspp.tar.xz \
    gammaos/emulators/ppsspp_1.18.1.apk:system/etc/ppsspp_1.18.1.apk \
    gammaos/emulators/flycast-release.apk:system/etc/flycast-release.apk

SELINUX_IGNORE_NEVERALLOWS := true

# Universal NoCutoutOverlay
PRODUCT_PACKAGES += \
    NoCutoutOverlay

PRODUCT_PACKAGES += \
    lightsctl \
    lightsctl-aidl \
    uevent

PRODUCT_COPY_FILES += \
	device/phh/treble/files/adbd.rc:system/etc/init/adbd.rc

PRODUCT_PACKAGES += \
	resetprop_phh

PRODUCT_COPY_FILES += \
	device/phh/treble/files/ota.sh:system/bin/ota.sh \

PRODUCT_COPY_FILES += \
	device/phh/treble/remove-telephony.sh:system/bin/remove-telephony.sh \

PRODUCT_COPY_FILES += \
	frameworks/native/data/etc/android.software.secure_lock_screen.xml:system/etc/permissions/android.software.secure_lock_screen.xml \
	device/phh/treble/files/android.software.controls.xml:system/etc/permissions/android.software.controls.xml \

PRODUCT_COPY_FILES += \
        device/phh/treble/ld.config.26.txt:system/etc/ld.config.26.txt \

# Privapp-permissions whitelist for PhhTrebleApp
PRODUCT_COPY_FILES += \
	device/phh/treble/privapp-permissions-me.phh.treble.app.xml:system/etc/permissions/privapp-permissions-me.phh.treble.app.xml

# Remote debugging
PRODUCT_COPY_FILES += \
	device/phh/treble/remote/dbclient:system/bin/dbclient \
	device/phh/treble/remote/phh-remotectl.rc:system/etc/init/phh-remotectl.rc \
	device/phh/treble/remote/phh-remotectl.sh:system/bin/phh-remotectl.sh \

PRODUCT_PACKAGES += \
	vr_hwc \
	curl \
	healthd \

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
	debug.fdsan=warn_once \
	persist.sys.fflag.override.settings_provider_model=false \
	ro.setupwizard.mode=OPTIONAL \

PRODUCT_PRODUCT_PROPERTIES += \
	ro.setupwizard.mode=OPTIONAL \

# AOSP overlays
PRODUCT_PACKAGES += \
    NavigationBarMode2ButtonOverlay

PRODUCT_COPY_FILES += \
	frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration_7_0.xml:system/etc/a2dp_audio_policy_configuration_7_0.xml \
	frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration.xml:system/etc/a2dp_audio_policy_configuration.xml \

include build/make/target/product/gsi_release.mk

# Protect deskclock from power save
PRODUCT_COPY_FILES += \
	device/phh/treble/files/com.android.deskclock_whitelist.xml:system/etc/sysconfig/com.android.deskclock_whitelist.xml

PRODUCT_PACKAGES += \
	evgrab \

PRODUCT_PACKAGES += \
	slsi-booted

# Two-pane layout in Settings
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)
PRODUCT_PRODUCT_PROPERTIES += \
    persist.settings.large_screen_opt.enabled=true

# Hide display cutout
PRODUCT_PRODUCT_PROPERTIES += \
    ro.support_hide_display_cutout=true
PRODUCT_PACKAGES += \
    AvoidAppsInCutoutOverlay \
    NoCutoutOverlay

PRODUCT_EXTRA_VNDK_VERSIONS += 28 29

# ===== Global Wi-Fi-only / No-RIL for all PHH Treble variants =====

# Robust removal list (wins even if later files add phone bits)
PRODUCT_REMOVE_PACKAGES += \
    TeleService \
    CarrierConfig \
    MmsService \
    CellBroadcastReceiver \
    Iwlan \
    ImsService \
    EuiccSupport \
    EuiccSupportPixel

# Advertise no-RIL and quiet vendor daemons
PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.radio.noril=true \
    ro.telephony.default_network=0 \
    ro.carrier=unknown \
    persist.radio.noril=1 \
    persist.vendor.radio.noril=1 \
    persist.vendor.sys.modem.disable=1 \
    persist.dbg.ims_volte_enable=0 \
    persist.dbg.vt_avail_ovr=0 \
    persist.dbg.wfc_avail_ovr=0

# Framework/UI overlays to hide telephony affordances
PRODUCT_PACKAGE_OVERLAYS += \
    device/phh/treble/overlay-wifionly

# Hard-disable telephony features at the PackageManager layer
PRODUCT_COPY_FILES += \
    device/phh/treble/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/no_telephony.xml \
    device/phh/treble/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/no_telephony.xml

PRODUCT_COPY_FILES += \
    device/phh/treble/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sysconfig/no_telephony.xml

# Also remove phone/SMS UI apps (AOSP)
PRODUCT_REMOVE_PACKAGES += \
    Dialer \
    Messaging \
    Stk \
    CarrierConfig \
    CarrierDefaultApp \
    ImsServiceEntitlement \
    Camera2

# Remove Google telephony/SMS apps if included via GApps
PRODUCT_REMOVE_PACKAGES += \
    com.google.android.dialer \
    com.google.android.apps.messaging \
    com.google.android.ims \
    CarrierServices

PRODUCT_REMOVE_PACKAGES += \
    TeleService \
    Stk \
    CarrierConfig \
    CarrierDefaultApp \
    ImsServiceEntitlement \
    Dialer \
    Messaging \
    messaging \
    ImsService \
    Iwlan \
    EuiccSupport \
    EuiccSupportPixel
