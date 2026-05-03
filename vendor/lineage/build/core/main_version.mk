# Build fingerprint
ifneq ($(BUILD_FINGERPRINT),)
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.build.fingerprint=$(BUILD_FINGERPRINT)
endif

# GammaOS version. Single source of truth for the user-facing version string
# embedded in ro.lineage.version, ro.lineage.display.version, and the OTA
# manifest compatibility check (ro.gammaos.build.version).
GAMMAOS_VERSION := 1.3.2

# GammaOS variant: bgN (GApps-Go) = Full, anything else (bvN, bvS) = Lite
ifneq (,$(findstring bgN,$(TARGET_PRODUCT)))
GAMMAOS_VARIANT_TAG := Full
GAMMAOS_VARIANT := full
else
GAMMAOS_VARIANT_TAG := Lite
GAMMAOS_VARIANT := lite
endif

# GammaOS props consumed by the Updater app. ro.gammaos.variant slots into the
# {variant} URL placeholder; ro.gammaos.build.version is what the Updater
# compares the manifest's "version" field against in isCompatible()/canInstall().
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.gammaos.variant=$(GAMMAOS_VARIANT) \
    ro.gammaos.build.version=$(GAMMAOS_VERSION)

# LineageOS System Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.version=GammaOS_Next_$(GAMMAOS_VARIANT_TAG)_v$(GAMMAOS_VERSION) \
    ro.lineage.releasetype=$(LINEAGE_BUILDTYPE) \
    ro.lineage.build.version=$(PRODUCT_VERSION_MAJOR).$(PRODUCT_VERSION_MINOR) \
    ro.modversion=$(LINEAGE_VERSION) \
    ro.lineagelegal.url=https://lineageos.org/legal

# LineageOS Platform Display Version
# Note: ADDITIONAL_SYSTEM_PROPERTIES is whitespace-split by Make, so the value
# cannot contain spaces or only the first token reaches build.prop. Use
# underscores to match ro.lineage.version.
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.display.version=GammaOS_Next_$(GAMMAOS_VARIANT_TAG)_v$(GAMMAOS_VERSION)

# LineageOS Platform SDK Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.build.version.plat.sdk=$(LINEAGE_PLATFORM_SDK_VERSION)

# LineageOS Platform Internal Version
ADDITIONAL_SYSTEM_PROPERTIES += \
    ro.lineage.build.version.plat.rev=$(LINEAGE_PLATFORM_REV)
