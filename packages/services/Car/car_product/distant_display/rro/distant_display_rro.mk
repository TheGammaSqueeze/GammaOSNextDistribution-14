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

$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-customizations/product.mk)
$(call inherit-product, packages/services/Car/car_product/car_ui_portrait/rro/car-ui-toolbar-customizations/product.mk)
$(call inherit-product-if-exists, vendor/auto/embedded/products/coolwhip/car-ui-lib-rros/product.mk)
$(call inherit-product-if-exists, vendor/google/nexus_overlay/fonts/fonts.mk)

# All RROs to be included in car_ui_portrait builds.
PRODUCT_PACKAGES += \
    DriverUiRRO \
    CarServiceRRO \
    DistantDisplayFrameworkResRRO \

PRODUCT_PRODUCT_PROPERTIES += \
    car.ui.config=distantdisplay
