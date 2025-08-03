#Display related packages and configuration

PRODUCT_PACKAGES += \
    android.hardware.graphics.composer@2.1-impl \
    android.hardware.graphics.composer@2.1-service \
    android.hardware.graphics.mapper@2.0-impl-qti-display \
    vendor.qti.hardware.display.allocator@1.0-service \
    android.hardware.memtrack@1.0-impl \
    android.hardware.memtrack@1.0-service \
    android.hardware.light@2.0-impl \
    android.hardware.light@2.0-service \
    modetest \
    gralloc.sdm845 \
    lights.sdm845 \
    hwcomposer.sdm845 \
    memtrack.sdm845 \
    libqdutils \
    libqdMetaData \
    libqdMetaData.system


TARGET_FORCE_HWC_FOR_VIRTUAL_DISPLAYS := true
NUM_FRAMEBUFFER_SURFACE_BUFFERS := 2
#Enable Charging Icon
TARGET_RECOVERY_PIXEL_FORMAT := RGBX_8888
TARGET_USES_GRALLOC1 := true
TARGET_USES_HWC2 := true
TARGET_USES_QCOM_DISPLAY_BSP := true
TARGET_USES_COLOR_METADATA := true
TARGET_USES_DRM_PP := true

PRODUCT_PROPERTY_OVERRIDES += \
    debug.sf.enable_hwc_vds=0 \
    persist.demo.hdmirotationlock=false \
    ro.vendor.display.cabl=2 \
    debug.sf.latch_unsignaled=1 \

PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
    ro.surface_flinger.max_virtual_display_dimension=4096

# This matrix should be in column major order, per SurfaceFlinger requirement
#  1.16868   -0.16868    0.00000
# -0.03155    1.03155    0.00000
# -0.01473   -0.05899    1.07372
PRODUCT_PROPERTY_OVERRIDES += \
    vendor.display.dataspace_saturation_matrix=1.16868,-0.03155,-0.01473,-0.16868,1.03155,-0.05899,0.00000,0.00000,1.07372

