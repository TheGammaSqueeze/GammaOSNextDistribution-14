
CONFIG_PATH := hardware/qcom/media/conf_files/kona
#Bring all FEATURE FLAG (Compilation) here

PRODUCT_COPY_FILES += \
    $(CONFIG_PATH)/media_codecs.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs.xml \
    $(CONFIG_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_kona_vendor.xml \
    $(CONFIG_PATH)/media_codecs_performance.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_performance_kona.xml \
    $(CONFIG_PATH)/media_codecs_vendor.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_kona_vendor.xml \
    $(CONFIG_PATH)/media_codecs_vendor.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_kona.xml \
    $(CONFIG_PATH)/media_codecs_vendor_audio.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_codecs_vendor_audio.xml \
    $(CONFIG_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_vendor.xml \
    $(CONFIG_PATH)/media_profiles.xml:$(TARGET_COPY_OUT_VENDOR)/etc/media_profiles_kona.xml \
    $(CONFIG_PATH)/mediacodec-seccomp.policy:$(TARGET_COPY_OUT_VENDOR)/etc/seccomp_policy/mediacodec.policy \
    $(CONFIG_PATH)/system_properties.xml:$(TARGET_COPY_OUT_VENDOR)/etc/system_properties.xml

# Enable CLANG/LLVM integer-overflow sanitization
TARGET_ENABLE_VIDC_INTSAN := true

# Enable DIAG mode for CLANG/LLVM integer-overflow sanitization
# TARGET_ENABLE_VIDC_INTSAN must be set to 'true' before enabling DIAG mode
# NOTE: DIAG mode should be used only for debug builds
TARGET_ENABLE_VIDC_INTSAN_DIAG := false

# Vendor property overrides
ifeq ($(GENERIC_ODM_IMAGE),true)
  $(warning "Forcing codec2.0 HW for generic odm build variant")
  #Set default ranks and rank Codec 2.0 over OMX codecs
  PRODUCT_ODM_PROPERTIES += debug.stagefright.ccodec=4
  PRODUCT_ODM_PROPERTIES += debug.stagefright.omx_default_rank=1000
  PRODUCT_COPY_FILES += \
    device/qcom/common/media/media_profiles.xml:TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml
else
  $(warning "Enabling codec2.0 non-audio SW only for non-generic odm build variant")
PRODUCT_PROPERTY_OVERRIDES += debug.stagefright.omx_default_rank=0
  PRODUCT_COPY_FILES += \
    device/qcom/common/media/media_profiles.xml:$(TARGET_COPY_OUT_ODM)/etc/media_profiles_V1_0.xml
endif

# Produce packages addition
PRODUCT_PACKAGES += \
    libcodec2_vndk.vendor \
    libcodec2_hidl@1.0.vendor
