TARGET_GAPPS_ARCH := arm
include device/google/atv/products/gsi_tv_arm.mk


# gsi_tv_* products enable Artifact Path Requirements enforcement for compliance GSIs.
# This build intentionally installs additional PHH/Gamma packages into system/, so disable APR enforcement.
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := false

# Suppress the compliance debug policy warning for non-compliance, custom GSIs.
PRODUCT_INSTALL_DEBUG_POLICY_TO_SYSTEM_EXT := false

$(call inherit-product, device/phh/treble/base.mk)
$(call inherit-product, device/phh/treble/base-sas.mk)
$(call inherit-product, vendor/gapps/arm/arm-vendor.mk)

$(call inherit-product, device/phh/treble/lineage.mk)

PRODUCT_NAME := lineage_tv_a64_agS
PRODUCT_DEVICE := tdgsi_a64_a
PRODUCT_BRAND := google
PRODUCT_SYSTEM_BRAND := google
PRODUCT_MODEL := GammaOS Core

PRODUCT_CHARACTERISTICS := tv

PRODUCT_PACKAGES +=  phh-su me.phh.superuser su

# GammaOS Nano: ATV builds (used by handheld emulator devices like TrimUI
# Brick) do not need the full Android apex set. Drop apex modules that
# are never exercised on a retro gaming handheld so apexd activation is
# faster at boot. com.android.runtime / art / i18n / tzdata / conscrypt /
# resolv / media / media.swcodec / adbd / permission / wifi / statsd /
# tethering / configinfrastructure / mediaprovider / scheduling /
# sdkext / extservices stay - they back framework functionality that
# RetroArch + the Nano menu actually use. The list below is the set
# that:
#   - has no hardware behind it (uwb, virt)
#   - is for use cases the device does not target (adservices,
#     appsearch, healthfitness, ondevicepersonalization, devicelock,
#     ipsec, rkpd, neuralnetworks)
#   - is a CTS test shim only (apex.cts.shim)
# btservices stays because we DO ship Bluetooth on these devices.
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

# Force CC with read barriers (concurrent-copying=true). Our GSI does not
# bundle a kernel, so without this the AOSP build probe falls back to
# "<unknown-kernel>" -> uffd CMC, which makes the host dex2oat emit boot.art
# with concurrent-copying=false. On kernels without UFFD SIGBUS the runtime
# falls back to read-barrier CC (gUseReadBarrier=true), the OatHeader
# mismatch trips zygote-side regeneration via dex2oat on every cold boot
# (16 to 20 seconds wasted). On the TV chain gsi_release.mk forces this to
# true via inherit-product so we MUST reassert it here at the top level for
# the value to win.
PRODUCT_ENABLE_UFFD_GC := false
