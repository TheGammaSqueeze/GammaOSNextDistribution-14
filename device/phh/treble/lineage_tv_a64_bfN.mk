TARGET_GAPPS_ARCH := arm
include device/google/atv/products/gsi_tv_arm.mk


# gsi_tv_* products enable Artifact Path Requirements enforcement for compliance GSIs.
# This build intentionally installs additional PHH/Gamma packages into system/, so disable APR enforcement.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

# Suppress the compliance debug policy warning for non-compliance, custom GSIs.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := false

$(call inherit-product, device/phh/treble/base.mk)

$(call inherit-product, vendor/foss/foss.mk)

# We intentionally do NOT want ATV behavior. We only use the TV base for lower overhead.
# Keep the product behaving like a normal AOSP/Lineage "device" build.
PRODUCT_IS_ATV := false

# Best-effort runtime marker (some trees use this; harmless if unused).
PRODUCT_SYSTEM_PROPERTIES += ro.product.is_atv=false

$(call inherit-product, device/phh/treble/lineage.mk)
$(call inherit-product-if-exists, device/phh/treble/lineage_tv_overrides.mk)
$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_tv_a64_bfN
PRODUCT_DEVICE := tdgsi_a64_ab
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := TrebleDroid TV with FOSS apps

# Overwrite the inherited "emulator" characteristics
PRODUCT_CHARACTERISTICS := device

PRODUCT_PACKAGES += 
