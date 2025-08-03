# Board specific SELinux policy variable definitions
SEPOLICY_PATH:= device/qcom/sepolicy
LOCAL_PATH := $(call my-dir)
BOARD_SYSTEM_EXT_PREBUILT_DIR := device/qcom/sepolicy/generic
BOARD_PRODUCT_PREBUILT_DIR := device/qcom/sepolicy/generic/product
BOARD_PLAT_PUB_VERSIONED_POLICY := device/qcom/sepolicy
$(shell $(SEPOLICY_PATH)/append.sh)

SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PUBLIC_SEPOLICY_DIRS) \
    $(SEPOLICY_PATH)/generic/public

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS := \
    $(SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS) \
    $(SEPOLICY_PATH)/generic/private

#once all the services are moved to Product /ODM above lines will be removed.
# sepolicy rules for product images
PRODUCT_PUBLIC_SEPOLICY_DIRS := \
    $(PRODUCT_PUBLIC_SEPOLICY_DIRS) \
    $(SEPOLICY_PATH)/generic/product/public

PRODUCT_PRIVATE_SEPOLICY_DIRS := \
    $(PRODUCT_PRIVATE_SEPOLICY_DIRS) \
    $(SEPOLICY_PATH)/generic/product/private
