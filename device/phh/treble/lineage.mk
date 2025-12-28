# Prefer Lineage ATV config when building Android TV products; otherwise fall back to tablet/Wi-Fi/base.
ifeq ($(PRODUCT_IS_ATV),true)
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tv.mk)
else
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tablet_wifionly.mk)
$(call inherit-product-if-exists, vendor/lineage/config/common_full_tablet.mk)
$(call inherit-product-if-exists, vendor/lineage/config/common_full_base.mk)
endif
