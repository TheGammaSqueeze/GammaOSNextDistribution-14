TARGET_GAPPS_ARCH := arm64
include build/make/target/product/aosp_arm64.mk
$(call inherit-product, device/phh/treble/base.mk)

$(call inherit-product, vendor/gapps/arm64/arm64-vendor.mk)
$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_arm64_bgN
PRODUCT_DEVICE := tdgsi_arm64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := GammaOS Next Full

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

# GammaOS: include the GammaOS browser (parity with the TV build's GammaBrowser). It is not
# pulled in by the shared lineage.mk, so the non-TV Full images shipped with no browser.
PRODUCT_PACKAGES += \
    GammaBrowser

# GammaOS: include the minimal Gamma secondary-display home (parity with the TV build - it was only
# added in the ATV product, so the non-TV images shipped without it). The framework secondary-home
# resolver (ActivityTaskManagerService.getSecondaryHomeIntent) launches this component on a
# secondary display that has system decorations; it stays inert on a single-screen device.
PRODUCT_PACKAGES += \
    SecondaryHome

PRODUCT_SYSTEM_DEFAULT_PROPERTIES += \
    persist.gammaos.secondary_home=com.gammaos.secondaryhome/com.gammaos.secondaryhome.SecondaryHomeActivity

# Force CC with read barriers (concurrent-copying=true). Our GSI does not bundle
# a kernel, so without this the AOSP build probe falls back to "<unknown-kernel>"
# -> uffd CMC, which makes the host dex2oat emit boot.art with
# concurrent-copying=false. On kernels without UFFD SIGBUS the runtime falls
# back to read-barrier CC (gUseReadBarrier=true), the OatHeader mismatch trips
# zygote-side regeneration via dex2oat on every cold boot (16 to 20 seconds
# wasted). Setting this here on the top-level product makefile ensures the
# value wins the inherit-product resolution chain.
PRODUCT_ENABLE_UFFD_GC := false
