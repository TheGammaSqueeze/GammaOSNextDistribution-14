TARGET_GAPPS_ARCH := arm
include build/make/target/product/aosp_arm.mk
$(call inherit-product, device/phh/treble/base.mk)
$(call inherit-product, device/phh/treble/base-sas.mk)
$(call inherit-product, device/phh/treble/gapps-go.mk)
$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_a64_aoN
PRODUCT_DEVICE := tdgsi_a64_a
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := GammaOS Next Go

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += 

# Force CC with read barriers (concurrent-copying=true). Our GSI does not bundle
# a kernel, so without this the AOSP build probe falls back to "<unknown-kernel>"
# -> uffd CMC, which makes the host dex2oat emit boot.art with
# concurrent-copying=false. On kernels without UFFD SIGBUS the runtime falls
# back to read-barrier CC (gUseReadBarrier=true), the OatHeader mismatch trips
# zygote-side regeneration via dex2oat on every cold boot (16 to 20 seconds
# wasted). Setting this here on the top-level product makefile ensures the
# value wins the inherit-product resolution chain.
PRODUCT_ENABLE_UFFD_GC := false
