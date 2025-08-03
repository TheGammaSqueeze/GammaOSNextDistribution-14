#
# Copyright (C) 2021 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Inherit from this product to include the "Car Ui Portrait" RROs for CarUi
# Include generated RROs
PRODUCT_PACKAGES += \
    generated_caruiportrait_customization-com-android-car-ui-paintbooth \
    generated_caruiportrait_customization-com-android-car-rotaryplayground \
    generated_caruiportrait_customization-com-android-car-themeplayground \
    generated_caruiportrait_customization-com-android-car-carlauncher \
    generated_caruiportrait_customization-com-android-car-home \
    generated_caruiportrait_customization-com-android-car-media \
    generated_caruiportrait_customization-com-android-car-messenger \
    generated_caruiportrait_customization-com-android-car-radio \
    generated_caruiportrait_customization-com-android-car-calendar \
    generated_caruiportrait_customization-com-android-car-systemupdater \
    generated_caruiportrait_customization-com-android-car-dialer \
    generated_caruiportrait_customization-com-android-car-linkviewer \
    generated_caruiportrait_customization-com-android-car-settings \
    generated_caruiportrait_customization-com-android-car-voicecontrol \
    generated_caruiportrait_customization-com-android-car-faceenroll \
    generated_caruiportrait_customization-com-android-car-developeroptions \
    generated_caruiportrait_customization-com-android-managedprovisioning \
    generated_caruiportrait_customization-com-android-settings-intelligence \
    generated_caruiportrait_customization-com-android-htmlviewer \
    generated_caruiportrait_customization-com-android-permissioncontroller \
    generated_caruiportrait_customization-com-google-android-car-portraitlauncher \
    generated_caruiportrait_customization-com-chassis-car-ui-plugin \

# This system property is used to enable the RROs on startup via
# the requiredSystemPropertyName/Value attributes in the manifest
PRODUCT_PRODUCT_PROPERTIES += ro.build.car_ui_rros_enabled=true
