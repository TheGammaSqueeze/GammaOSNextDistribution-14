CONFIG_PATH := hardware/qcom/media/conf_files/$(MSMSTEPPE)

# Video feature flags

# Video configuration files
PRODUCT_COPY_FILES += \
    $(CONFIG_PATH)/codec2.vendor.ext.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/codec2.vendor.ext.policy \
    $(CONFIG_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(CONFIG_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    $(CONFIG_PATH)/media_codecs_vendor.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor.xml \
    $(CONFIG_PATH)/media_codecs_vendor_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_audio.xml \
    $(CONFIG_PATH)/media_codecs_sdmmagpie_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_v1.xml \
    $(CONFIG_PATH)/media_codecs_sdmmagpie_v1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_v2.xml \
    $(CONFIG_PATH)/media_codecs_vendor_sdmmagpie_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v1.xml \
    $(CONFIG_PATH)/media_codecs_vendor_sdmmagpie_v1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_v2.xml \
    $(CONFIG_PATH)/media_codecs_performance_sdmmagpie_v0.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v1.xml \
    $(CONFIG_PATH)/media_codecs_performance_sdmmagpie_v1.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_v2.xml \
    $(CONFIG_PATH)/mediacodec-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(CONFIG_PATH)/system_properties.xml:$(TARGET_COPY_OUT_VENDOR)/etc/system_properties.xml

# Produce packages addition
PRODUCT_PACKAGES += \
    libcodec2_vndk.vendor \
    libcodec2_hidl@1.0.vendor
ifeq ($(TARGET_FWK_SUPPORTS_FULL_VALUEADDS),true)
  $(warning "Compiling with full value-added framework")
else
  $(warning "Compiling without full value-added framework - enabling GENERIC_ODM_IMAGE")
  GENERIC_ODM_IMAGE := true
endif

# Vendor property overrides
# Enable Codec2.0 HAL for pure AOSP variants.
# Enable OMX for pure AOSP AUTO variants.
ifeq ($(GENERIC_ODM_IMAGE)$(TARGET_BOARD_AUTO),truetrue)
  $(warning "Forcing OMX for Generic auto build variant")
  DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/sm6150/c2_manifest_vendor.xml
  PRODUCT_ODM_PROPERTIES += debug.media.codec2=0
  PRODUCT_ODM_PROPERTIES += debug.stagefright.ccodec=0
  PRODUCT_ODM_PROPERTIES += debug.stagefright.omx_default_rank=1000
  PRODUCT_COPY_FILES += \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml
else ifeq ($(GENERIC_ODM_IMAGE),true)
  $(warning "Forcing codec2.0 HW for generic odm build variant")
  DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/sm6150/c2_manifest_vendor.xml
  #Set default ranks and rank Codec 2.0 over OMX codecs
  PRODUCT_ODM_PROPERTIES += debug.stagefright.ccodec=4
  PRODUCT_ODM_PROPERTIES += debug.stagefright.omx_default_rank=1000
  PRODUCT_COPY_FILES += \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml
else ifneq ($(TARGET_FWK_SUPPORTS_AV_VALUEADDS),false)
  $(warning "Enabling codec2.0 non-audio SW only for non-generic odm build variant")
  DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/sm6150/c2_manifest.xml
  PRODUCT_PROPERTY_OVERRIDES += debug.stagefright.omx_default_rank=0
  PRODUCT_PROPERTY_OVERRIDES += media.settings.xml=/vendor/etc/media_profiles_vendor.xml
  PRODUCT_COPY_FILES += \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml \
      $(CONFIG_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml
else
    $(warning "Compiling without value-added features")
    DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/msmnile/c2_manifest.xml
    PRODUCT_COPY_FILES += \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml
endif
