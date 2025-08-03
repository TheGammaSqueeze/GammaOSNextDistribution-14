ifeq ($(call is-board-platform-in-list, $(QCOM_BOARD_PLATFORMS)),true)

MM_CORE := libmm-omxcore
MM_CORE += libOmxCore
MM_CORE += libplatformconfig
MM_CORE += libcodec2_vndk.vendor
MM_CORE += libcodec2_hidl@1.0.vendor

PRODUCT_PACKAGES += $(MM_CORE)

#---------------------------------------------------------------------------------------------------
# Runtime Codec2.0 enablement
#---------------------------------------------------------------------------------------------------
# TODO(PC): Override ccodec selection option back to defult (4).
#           QSSI is forcing this to '1'. Must be reverted
ifeq ($(call is-board-platform-in-list, crow blair pitti bengal neo parrot taro lahaina holi \
        kona trinket qcs605), true)
    $(warning "Default Codec2.0 Enabled")
    PRODUCT_VENDOR_PROPERTIES += debug.stagefright.ccodec=4
endif

endif

