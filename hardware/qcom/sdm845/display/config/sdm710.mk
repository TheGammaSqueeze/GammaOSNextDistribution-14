#Display related packages and configuration

PRODUCT_PACKAGES += \
    android.hardware.graphics.composer@2.2-impl \
    android.hardware.graphics.composer@2.2-service \
    android.hardware.graphics.mapper@2.0-impl-qti-display \
    vendor.qti.hardware.display.allocator@1.0-service \
    android.hardware.memtrack@1.0-impl \
    android.hardware.memtrack@1.0-service \
    android.hardware.light@2.0-impl \
    android.hardware.light@2.0-service \
    modetest \
    gralloc.sdm710 \
    lights.sdm710 \
    hwcomposer.sdm710 \
    memtrack.sdm710 \
    libqdutils \
    libqdMetaData \
    libqdMetaData.system

#QDCM calibration xml file for video panel
PRODUCT_COPY_FILES += hardware/qcom/display/config/qdcm_calib_data_nt35597_cmd_mode_dsi_truly_panel_with_DSC.xml:$(TARGET_COPY_OUT_VENDOR)/etc/qdcm_calib_data_Dual_nt35597_video_mode_dsi_truly_panel_without_DSC.xml

#QDCM calibration xml file for cmd panel
PRODUCT_COPY_FILES += hardware/qcom/display/config/qdcm_calib_data_nt35597_cmd_mode_dsi_truly_panel_with_DSC.xml:$(TARGET_COPY_OUT_VENDOR)/etc/qdcm_calib_data_Dual_nt35597_cmd_mode_dsi_truly_panel_without_DSC.xml
PRODUCT_COPY_FILES += hardware/qcom/display/config/qdcm_calib_data_nt35597_cmd_mode_dsi_truly_panel_with_DSC.xml:$(TARGET_COPY_OUT_VENDOR)/etc/qdcm_calib_data_nt35597_cmd_mode_dsi_truly_panel_with_DSC.xml

TARGET_FORCE_HWC_FOR_VIRTUAL_DISPLAYS := true
MAX_VIRTUAL_DISPLAY_DIMENSION := 4096
NUM_FRAMEBUFFER_SURFACE_BUFFERS := 3
#Enable Charging Icon
TARGET_RECOVERY_PIXEL_FORMAT := RGBX_8888
TARGET_USES_GRALLOC1 := true
TARGET_USES_HWC2 := true
TARGET_USES_QCOM_DISPLAY_BSP := true
TARGET_USES_COLOR_METADATA := true
TARGET_USES_DRM_PP := true
TARGET_HAS_WIDE_COLOR_DISPLAY := true
TARGET_USES_DISPLAY_RENDER_INTENTS := true
TARGET_HAS_HDR_DISPLAY := true
ifneq (,$(filter sdm670, $(PRODUCT_PLATFORM)))
    TARGET_HAS_HDR_DISPLAY := false
endif

PRODUCT_PROPERTY_OVERRIDES += \
    persist.demo.hdmirotationlock=false \
    persist.sys.sf.color_saturation=1.0 \
    ro.vendor.display.cabl=2 \
    debug.sf.latch_unsignaled=1 \
    vendor.display.enable_default_color_mode=1 \

# This matrix should be in column major order, per SurfaceFlinger requirement
#  1.16868   -0.16868    0.00000
# -0.03155    1.03155    0.00000
# -0.01473   -0.05899    1.07372
PRODUCT_PROPERTY_OVERRIDES += \
    vendor.display.dataspace_saturation_matrix=1.16868,-0.03155,-0.01473,-0.16868,1.03155,-0.05899,0.00000,0.00000,1.07372

