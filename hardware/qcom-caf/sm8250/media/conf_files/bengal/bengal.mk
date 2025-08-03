CONFIG_PATH := hardware/qcom/media/conf_files/bengal
#Bring all FEATURE FLAG (Compilation) here

PRODUCT_COPY_FILES += \
    $(CONFIG_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(CONFIG_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    $(CONFIG_PATH)/media_codecs_vendor_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_audio.xml \
    $(CONFIG_PATH)/media_codecs_performance_v1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v1.xml \
    $(CONFIG_PATH)/media_codecs_performance_scuba_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v2.xml \
    $(CONFIG_PATH)/media_codecs_performance_khaje.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v3.xml \
    $(CONFIG_PATH)/media_codecs_performance_khaje_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v4.xml \
    $(CONFIG_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml \
    $(CONFIG_PATH)/media_profiles_scuba.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_scuba.xml \
    $(CONFIG_PATH)/media_profiles_khaje.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_khaje.xml \
    $(CONFIG_PATH)/media_profiles_khaje_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_khaje_v0.xml \
    $(CONFIG_PATH)/mediacodec-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(CONFIG_PATH)/system_properties.xml:$(TARGET_COPY_OUT_VENDOR)/etc/system_properties.xml \
    device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml

ifneq ($(strip $(TARGET_BOARD_SUFFIX)),)
	PRODUCT_COPY_FILES += \
		$(CONFIG_PATH)/media_codecs_vendor_32.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor.xml \
		$(CONFIG_PATH)/media_codecs_vendor_v1_32.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v1.xml \
		$(CONFIG_PATH)/media_codecs_vendor_scuba_v0_32.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v2.xml
else
	PRODUCT_COPY_FILES += \
		$(CONFIG_PATH)/media_codecs_vendor.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor.xml \
		$(CONFIG_PATH)/media_codecs_vendor_v1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v1.xml \
		$(CONFIG_PATH)/media_codecs_vendor_scuba_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v2.xml \
		$(CONFIG_PATH)/media_codecs_vendor_khaje.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v3.xml \
		$(CONFIG_PATH)/media_codecs_vendor_khaje_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v4.xml
endif

# Vendor property overrides

ifeq ($(GENERIC_ODM_IMAGE),true)
  $(warning "Forcing codec2.0 HW for generic odm build variant")
  #Set default ranks and rank Codec 2.0 over OMX codecs
  PRODUCT_ODM_PROPERTIES += debug.stagefright.ccodec=4
  PRODUCT_ODM_PROPERTIES += debug.stagefright.omx_default_rank=1000
else
  $(warning "Enabling codec2.0 non-audio SW only for non-generic odm build variant")
  PRODUCT_PROPERTY_OVERRIDES += debug.stagefright.omx_default_rank=0
endif
