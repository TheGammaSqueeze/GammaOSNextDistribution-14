TARGET_GAPPS_ARCH := arm64
include device/google/atv/products/gsi_tv_arm64.mk

# gsi_tv_* products enable Artifact Path Requirements enforcement for compliance GSIs.
# This build intentionally installs additional GammaOS packages into system/, so disable APR.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := false

# ===== GammaOS base (replaces device/phh/treble/base.mk) =====

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.fingerprint.xml:system/etc/permissions/android.hardware.fingerprint.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml \
    frameworks/native/data/etc/android.hardware.usb.host.xml:system/etc/permissions/android.hardware.usb.host.xml

# Bluetooth Audio (System-side HAL, sysbta)
PRODUCT_PACKAGES += \
    audio.sysbta.default \
    android.hardware.bluetooth.audio-service-system

# resetprop_phh: writes write-once ro. props directly (used by gammaos-on-boot.sh
# to flip the SurfaceFlinger primary display orientation for portrait-native mode).
PRODUCT_PACKAGES += \
    resetprop_phh

# GammaOS packages
PRODUCT_PACKAGES += \
    JoystickLedPicker \
    ShaderControl \
    GammaBrowser \
    DualStackControl \
    GammaEQ \
    GammaScreenMapper \
    DrasticSf \
    LaunchGuardControl \
    SecondaryDisplayControl \
    gammapad \
    gammapad_restore \
    gammaos-ota \
    gammaos-net

# tinyalsa command-line tools (audio bring-up / debugging on the device)
PRODUCT_PACKAGES += \
    tinymix \
    tinyplay

PRODUCT_COPY_FILES += \
    device/gammaos/bluetooth/audio/config/sysbta_audio_policy_configuration.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysbta_audio_policy_configuration.xml \
    device/gammaos/bluetooth/audio/config/sysbta_audio_policy_configuration_7_0.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysbta_audio_policy_configuration_7_0.xml

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += device/gammaos/sepolicy

PRODUCT_PACKAGE_OVERLAYS += \
    device/gammaos/overlay \
    device/gammaos/overlay-lineage

PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += \
    device/gammaos/overlay-lineage/lineage-sdk

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    ro.build.version.sdk=$(PLATFORM_SDK_VERSION) \
    ro.build.version.codename=$(PLATFORM_VERSION_CODENAME) \
    ro.build.version.all_codenames=$(PLATFORM_VERSION_ALL_CODENAMES) \
    ro.build.version.release=$(PLATFORM_VERSION) \
    ro.build.version.security_patch=$(PLATFORM_SECURITY_PATCH) \
    ro.adb.secure=0 \
    ro.surface_flinger.supports_background_blur=1 \
    ro.control_privapp_permissions=log

PRODUCT_VENDOR_PROPERTIES += \
    ro.surface_flinger.supports_background_blur=1

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    persist.sys.usb.config=adb \
    ro.cust.cdrom=/dev/null

# GammaOS init (replaces PHH vndk.rc + rw-system.sh + phh-on-boot.sh)
PRODUCT_COPY_FILES += \
    device/gammaos/gammaos.rc:system/etc/init/gammaos.rc \
    device/gammaos/gammaos-on-boot.sh:system/bin/gammaos-on-boot.sh

# USB Audio
PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/usb_audio_policy_configuration.xml:system/etc/usb_audio_policy_configuration.xml \
    device/gammaos/fake_audio_policy_volume.xml:system/etc/fake_audio_policy_volume.xml

# GammaOS Customizations
PRODUCT_COPY_FILES += \
    gammaos/utils/xz:system/bin/xz \
    gammaos/utils/dtc:system/bin/dtc \
    gammaos/utils/inotifywait:system/bin/inotifywait \
    gammaos/utils/fenix-148.0b9.multi.android-arm64-v8a.apk:system/etc/fenix-148.0b9.multi.android-arm64-v8a.apk \
    gammaos/customization.sh:system/bin/customization.sh \
    gammaos/magisk/magisk.apk:system/etc/magisk.apk \
    gammaos/magisk/magisk.tar.gz:system/etc/magisk.tar.gz \
    gammaos/magisk/magisk2.tar.gz:system/etc/magisk2.tar.gz \
    gammaos/retroarch/RetroArch_aarch64.apk:system/etc/RetroArch_aarch64.apk \
    gammaos/retroarch/retroarch.tar.xz:system/etc/retroarch.tar.xz \
    gammaos/retroarch/roms.tar.xz:system/etc/roms.tar.xz \
    gammaos/setup.sh:system/bin/setup.sh \
    gammaos/nano_cache.sh:system/bin/nano_cache.sh \
    gammaos/launcher/MiXplorer_v6.64.3-API29_B23090720.apk:system/etc/MiXplorer_v6.64.3-API29_B23090720.apk \
    gammaos/launcher/AuroraStore_4.6.2.apk:system/etc/AuroraStore_4.6.2.apk \
    gammaos/launcher/aurorastore.tar.gz:system/etc/aurorastore.tar.gz \
    gammaos/daijisho/splits/base.apk:system/etc/daijisho/base.apk \
    gammaos/daijisho/splits/split_config.en.apk:system/etc/daijisho/split_config.en.apk \
    gammaos/daijisho/splits/split_config.xxxhdpi.apk:system/etc/daijisho/split_config.xxxhdpi.apk \
    gammaos/daijisho/daijisho.tar.xz:system/etc/daijisho.tar.xz \
    gammaos/emulators/drastic.tar.gz:system/etc/drastic.tar.gz \
    gammaos/emulators/drastic_r2.6.0.4a.apk:system/etc/drastic_r2.6.0.4a.apk \
    gammaos/emulators/mupen64plusae.tar.gz:system/etc/mupen64plusae.tar.gz \
    gammaos/emulators/mupen64plusae_3.0.335.apk:system/etc/mupen64plusae_3.0.335.apk \
    gammaos/emulators/ppsspp.tar.xz:system/etc/ppsspp.tar.xz \
    gammaos/emulators/ppsspp_1.20.3.apk:system/etc/ppsspp_1.20.3.apk \
    gammaos/emulators/flycast-release.apk:system/etc/flycast-release.apk

SELINUX_IGNORE_NEVERALLOWS := true

PRODUCT_PACKAGES += \
    NoCutoutOverlay

PRODUCT_PACKAGES += \
    lightsctl \
    lightsctl-aidl \
    uevent

PRODUCT_COPY_FILES += \
    device/gammaos/adbd.rc:system/etc/init/adbd.rc

PRODUCT_PACKAGES += \
    bootctl \
    vintf

PRODUCT_PACKAGES += \
    simg2img_simple \
    lptools

ifneq (,$(wildcard external/exfat))
PRODUCT_PACKAGES += \
    mkfs.exfat \
    fsck.exfat
endif

PRODUCT_COPY_FILES += \
    device/gammaos/interfaces.xml:system/etc/permissions/interfaces.xml

    # treble-environ-rc replaced by static init.classpath.rc

PRODUCT_PACKAGES += \
    evgrab

PRODUCT_PACKAGES += \
    slsi-booted

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    debug.fdsan=warn_once \
    persist.sys.fflag.override.settings_provider_model=false \
    ro.setupwizard.mode=OPTIONAL

PRODUCT_PRODUCT_PROPERTIES += \
    ro.setupwizard.mode=OPTIONAL \
    ro.control_privapp_permissions=log

PRODUCT_PACKAGES += \
    NavigationBarMode2ButtonOverlay

PRODUCT_COPY_FILES += \
    frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration_7_0.xml:system/etc/a2dp_audio_policy_configuration_7_0.xml \
    frameworks/av/services/audiopolicy/config/a2dp_audio_policy_configuration.xml:system/etc/a2dp_audio_policy_configuration.xml

include build/make/target/product/gsi_release.mk

PRODUCT_PACKAGES += \
    vr_hwc \
    curl \
    healthd

# Two-pane layout in Settings
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)
PRODUCT_PRODUCT_PROPERTIES += \
    persist.settings.large_screen_opt.enabled=true

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.secure_lock_screen.xml:system/etc/permissions/android.software.secure_lock_screen.xml \
    device/gammaos/android.software.controls.xml:system/etc/permissions/android.software.controls.xml

PRODUCT_COPY_FILES += \
    device/phh/treble/ld.config.26.txt:system/etc/ld.config.26.txt

# ===== Global Wi-Fi-only / No-RIL =====

PRODUCT_REMOVE_PACKAGES += \
    TeleService \
    CarrierConfig \
    MmsService \
    CellBroadcastReceiver \
    Iwlan \
    ImsService \
    EuiccSupport \
    EuiccSupportPixel

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

PRODUCT_PACKAGE_OVERLAYS += \
    device/gammaos/overlay-wifionly

PRODUCT_COPY_FILES += \
    device/gammaos/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/no_telephony.xml \
    device/gammaos/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/no_telephony.xml \
    device/gammaos/sysconfig/no_telephony.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/sysconfig/no_telephony.xml

PRODUCT_REMOVE_PACKAGES += \
    Dialer \
    Messaging \
    Stk \
    CarrierDefaultApp \
    ImsServiceEntitlement \
    Camera2 \
    com.google.android.dialer \
    com.google.android.apps.messaging \
    com.google.android.ims \
    CarrierServices

# ===== Low-RAM optimizations =====
TARGET_EXCLUDES_AUDIOFX := true

PRODUCT_REMOVE_PACKAGES += \
    DeskClock \
    PrintSpooler \
    ManagedProvisioning \
    StatementService \
    rkpdapp \
    OnDevicePersonalization \
    DeviceLockController \
    HealthConnectController

PRODUCT_EXTRA_VNDK_VERSIONS += 28 29

# ===== Lineage (minimal, ATV only) =====
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tv.mk)

# ===== Product identity =====
PRODUCT_NAME := lineage_tv_arm64_bvN
PRODUCT_DEVICE := tdgsi_arm64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := GammaOS Core

PRODUCT_CHARACTERISTICS := tv

# GammaOS Nano: drop unused APEX modules for faster apexd activation
PRODUCT_PACKAGES_REMOVE += \
    com.android.adservices \
    com.android.apex.cts.shim \
    com.android.appsearch \
    com.android.devicelock \
    com.android.healthfitness \
    com.android.ipsec \
    com.android.neuralnetworks \
    com.android.ondevicepersonalization \
    com.android.rkpd \
    com.android.uwb \
    com.android.virt

# Force CC with read barriers so dex2oat emits matching boot.art
PRODUCT_ENABLE_UFFD_GC := false
