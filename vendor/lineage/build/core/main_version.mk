# Build fingerprint
ifneq ($(BUILD_FINGERPRINT),)
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.build.fingerprint=$(BUILD_FINGERPRINT)
endif

# GammaOS variant: bgN (GApps-Go) = Full, anything else (bvN, bvS) = Lite
ifneq (,$(findstring bgN,$(TARGET_PRODUCT)))
GAMMAOS_VARIANT_TAG := Full
else
GAMMAOS_VARIANT_TAG := Lite
endif

# LineageOS System Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.version=GammaOS_Next_$(GAMMAOS_VARIANT_TAG)_v1.3.0 \
    ro.lineage.releasetype=$(LINEAGE_BUILDTYPE) \
    ro.lineage.build.version=$(PRODUCT_VERSION_MAJOR).$(PRODUCT_VERSION_MINOR) \
    ro.modversion=$(LINEAGE_VERSION) \
    ro.lineagelegal.url=https://lineageos.org/legal

# LineageOS Platform Display Version
# Note: ADDITIONAL_SYSTEM_PROPERTIES is whitespace-split by Make, so the value
# cannot contain spaces or only the first token reaches build.prop. Use
# underscores to match ro.lineage.version.
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.display.version=GammaOS_Next_$(GAMMAOS_VARIANT_TAG)_v1.3.0

# LineageOS Platform SDK Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.build.version.plat.sdk=$(LINEAGE_PLATFORM_SDK_VERSION)

# LineageOS Platform Internal Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.build.version.plat.rev=$(LINEAGE_PLATFORM_REV)
