# Inherit from this product to include the "Reference Design" RROs for CarUi

# Include generated RROs
PRODUCT_PACKAGES += \
    googlecarui-com-android-car-ui-paintbooth \
    googlecarui-com-android-car-rotaryplayground \
    googlecarui-com-android-car-themeplayground \
    googlecarui-com-android-car-carlauncher \
    googlecarui-com-android-car-home \
    googlecarui-com-android-car-media \
    googlecarui-com-android-car-radio \
    googlecarui-com-android-car-calendar \
    googlecarui-com-android-car-messenger \
    googlecarui-com-android-car-systemupdater \
    googlecarui-com-android-car-dialer \
    googlecarui-com-android-car-linkviewer \
    googlecarui-com-android-car-settings \
    googlecarui-com-android-car-developeroptions \
    googlecarui-com-android-managedprovisioning \
    googlecarui-com-android-settings-intelligence \
    googlecarui-com-android-htmlviewer \

# This system property is used to enable the RROs on startup via
# the requiredSystemPropertyName/Value attributes in the manifest
PRODUCT_PRODUCT_PROPERTIES += ro.build.car_ui_rros_enabled=true
