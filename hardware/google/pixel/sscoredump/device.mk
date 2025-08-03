# Subsystem coredump
PRODUCT_PACKAGES += sscoredump
PRODUCT_PROPERTY_OVERRIDES += vendor.debug.ssrdump.type=sscoredump
PRODUCT_SOONG_NAMESPACES += vendor/google/tools/subsystem-coredump
BOARD_SEPOLICY_DIRS += hardware/google/pixel-sepolicy/sscoredump
