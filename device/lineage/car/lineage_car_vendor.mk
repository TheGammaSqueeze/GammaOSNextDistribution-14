PRODUCT_PACKAGES += \
    android.hardware.automotive.vehicle@2.0-default-service \
    android.hardware.automotive.audiocontrol-service.example

PRODUCT_COPY_FILES += \
    packages/services/Car/car_product/init/init.bootstat.rc:root/init.bootstat.rc \

# Additional selinux policy
BOARD_SEPOLICY_DIRS += device/generic/car/common/sepolicy

# Whitelisted packages per user type
PRODUCT_COPY_FILES += \
    device/generic/car/common/preinstalled-packages-product-car-emulator.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/sysconfig/preinstalled-packages-product-car-emulator.xml
