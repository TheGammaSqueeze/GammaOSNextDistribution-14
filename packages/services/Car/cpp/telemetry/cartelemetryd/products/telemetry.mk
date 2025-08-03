# Copyright (C) 2021 The Android Open Source Project

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

# Selinux public policies for cartelemetry
PRODUCT_PUBLIC_SEPOLICY_DIRS += packages/services/Car/cpp/telemetry/cartelemetryd/sepolicy/public

# cartelemetryd service
PRODUCT_PACKAGES += android.automotive.telemetryd@1.0

# Selinux private policies for cartelemetry
PRODUCT_PRIVATE_SEPOLICY_DIRS += packages/services/Car/cpp/telemetry/cartelemetryd/sepolicy/private
