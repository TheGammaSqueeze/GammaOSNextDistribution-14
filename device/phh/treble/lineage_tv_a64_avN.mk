TARGET_GAPPS_ARCH := arm
include device/google/atv/products/gsi_tv_arm.mk


# gsi_tv_* products enable Artifact Path Requirements enforcement for compliance GSIs.
# This build intentionally installs additional PHH/Gamma packages into system/, so disable APR enforcement.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

# Suppress the compliance debug policy warning for non-compliance, custom GSIs.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := false

$(call inherit-product, device/phh/treble/base.mk)
$(call inherit-product, device/phh/treble/base-sas.mk)


$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_tv_a64_avN
PRODUCT_DEVICE := tdgsi_a64_a
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := TrebleDroid TV vanilla

PRODUCT_CHARACTERISTICS := tv

PRODUCT_PACKAGES += 
