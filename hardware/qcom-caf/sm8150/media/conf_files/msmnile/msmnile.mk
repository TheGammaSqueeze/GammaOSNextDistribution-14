CONFIG_PATH := hardware/qcom/media/conf_files/msmnile

# Video feature flags

# Video configuration files
PRODUCT_COPY_FILES += \
    $(CONFIG_PATH)/codec2.vendor.ext.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/codec2.vendor.ext.policy \
    $(CONFIG_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(CONFIG_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance.xml \
    $(CONFIG_PATH)/media_codecs_vendor.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor.xml \
    $(CONFIG_PATH)/media_codecs_vendor_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_audio.xml \
    $(CONFIG_PATH)/mediacodec-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(CONFIG_PATH)/system_properties.xml:$(TARGET_COPY_OUT_VENDOR)/etc/system_properties.xml

# Vendor property overrides

# Produce packages addition
PRODUCT_PACKAGES += \
    libcodec2_vndk.vendor \
    libcodec2_hidl@1.0.vendor

# Enable Codec2.0 HAL for pure AOSP variants.
ifeq ($(GENERIC_ODM_IMAGE),true)
    $(warning "Forcing codec2.0 HW for generic odm build variant")
    DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/msmnile/c2_manifest_vendor.xml
    #Set default ranks and rank Codec 2.0 over OMX codecs
    PRODUCT_ODM_PROPERTIES += debug.stagefright.ccodec=4
    PRODUCT_ODM_PROPERTIES += debug.stagefright.omx_default_rank=1000
    PRODUCT_COPY_FILES += \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml \
      device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml

else ifneq ($(TARGET_FWK_SUPPORTS_AV_VALUEADDS),false)
    $(warning "Enabling codec2.0 non-audio SW only for non-generic odm build variant")
    DEVICE_MANIFEST_FILE += hardware/qcom/media/conf_files/msmnile/c2_manifest.xml
    PRODUCT_PROPERTY_OVERRIDES += debug.stagefright.omx_default_rank=0
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
