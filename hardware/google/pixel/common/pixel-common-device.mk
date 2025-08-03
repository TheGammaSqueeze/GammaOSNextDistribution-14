PRODUCT_COPY_FILES += \
      hardware/google/pixel/common/init.pixel.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.pixel.rc

BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/common/vendor
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/common/system_ext

# Enable atrace categories and tools for pixel devices
PRODUCT_PACKAGES += \
    atrace_categories.txt.pixel \
    dmabuf_dump

# fastbootd
PRODUCT_PACKAGES += \
    fastbootd

# Common ramdump file type.
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/ramdump/common

# Enable RKP support
PRODUCT_PRODUCT_PROPERTIES += \
    remote_provisioning.hostname=remoteprovisioning.googleapis.com

# Pixel Experience

ifneq (,$(filter eng, $(TARGET_BUILD_VARIANT)))
ifeq (,$(filter aosp_%,$(TARGET_PRODUCT)))
PRODUCT_PACKAGES_DEBUG += wifi_diagnostic
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_diagnostic
endif
endif

PRODUCT_PACKAGES_DEBUG += wifi_sniffer
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_sniffer

PRODUCT_PACKAGES_DEBUG += wifi_perf_diag
BOARD_VENDOR_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/wifi_perf_diag

# Enable whole-program R8 Java optimizations for SystemUI and system_server,
# but also allow explicit overriding for testing and development.
SYSTEM_OPTIMIZE_JAVA ?= true
SYSTEMUI_OPTIMIZE_JAVA ?= true

# Exclude features that are not available on AOSP devices.
ifneq (,$(filter aosp_%,$(TARGET_PRODUCT)))
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/aosp_excluded_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/aosp_excluded_hardware.xml
endif

# Preopt SystemUI
PRODUCT_DEXPREOPT_SPEED_APPS += SystemUIGoogle  # For internal
PRODUCT_DEXPREOPT_SPEED_APPS += SystemUI        # For AOSP

# Compile SystemUI on device with `speed`.
PRODUCT_PROPERTY_OVERRIDES += \
    dalvik.vm.systemuicompilerfilter=speed

# Virtual fingerprint HAL
ifneq (,$(filter eng, $(TARGET_BUILD_VARIANT)))
PRODUCT_PACKAGES_DEBUG += com.android.hardware.biometrics.fingerprint.virtual

# Virtual face HAL
ifeq ($(RELEASE_AIDL_USE_UNFROZEN), true)
PRODUCT_PACKAGES_DEBUG += com.android.hardware.biometrics.face.virtual
endif
endif
