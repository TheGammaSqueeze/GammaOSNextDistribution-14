#MSM_VIDC_TARGET_LIST := msmnile

ifneq (,$(call is-board-platform-in-list2, $(QCOM_BOARD_PLATFORMS)))

#MM_CORE
MM_CORE := libmm-omxcore
MM_CORE += libOmxCore

PRODUCT_PACKAGES += $(MM_CORE)

endif

ifneq (,$(call is-board-platform-in-list2, $(MSM_VIDC_TARGET_LIST)))

MM_VIDEO := ExoplayerDemo
MM_VIDEO += libc2dcolorconvert
MM_VIDEO += libOmxSwVdec
MM_VIDEO += libOmxSwVencMpeg4
MM_VIDEO += libOmxVdec
MM_VIDEO += libOmxVenc
MM_VIDEO += libstagefrighthw

PRODUCT_PACKAGES += $(MM_VIDEO)

ifneq (,$(call is-board-platform-in-list2, msmnile $(MSMSTEPPE)))
include hardware/qcom/sm8150/media/conf_files/$(TARGET_BOARD_PLATFORM)/$(TARGET_BOARD_PLATFORM).mk
else ifeq ($(TARGET_BOARD_PLATFORM), sdmshrike)
include hardware/qcom/media/conf_files/msmnile/msmnile.mk
endif

endif

#Vendor property to enable Codec2 for audio and OMX for Video
PRODUCT_PROPERTY_OVERRIDES += debug.stagefright.ccodec=1
