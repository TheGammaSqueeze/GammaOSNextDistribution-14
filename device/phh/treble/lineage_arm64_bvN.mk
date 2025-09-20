TARGET_GAPPS_ARCH := arm64
include build/make/target/product/aosp_arm64.mk
$(call inherit-product, device/phh/treble/base.mk)


$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_arm64_bvN
PRODUCT_DEVICE := tdgsi_arm64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := TrebleDroid vanilla

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += 

# ===== FINAL STRIP: Wi-Fi-only, remove telephony from system/product/system_ext =====
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

# Last-resort scrub in case something re-adds them late
PRODUCT_PACKAGES := $(filter-out \
    TeleService Stk CarrierConfig CarrierDefaultApp ImsServiceEntitlement \
    Dialer Messaging messaging ImsService Iwlan EuiccSupport EuiccSupportPixel, \
    $(PRODUCT_PACKAGES))
