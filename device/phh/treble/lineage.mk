# Prefer tablet/Wi-Fi if present; otherwise fall back to base w/o phone presets
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tablet_wifionly.mk)
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tablet.mk)
$(call inherit-product-if-exists, vendor/lineage/config/common_full_base.mk)
-include vendor/lineage/build/core/config.mk
-include vendor/lineage/build/core/apicheck.mk
