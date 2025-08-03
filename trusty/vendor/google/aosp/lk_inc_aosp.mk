# Copyright (C) 2015 The Android Open Source Project
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

LKROOT ?= external/lk

LKINC ?=  trusty/kernel \
          external/lk \
          external/headers \
          external/trusty/headers \
          trusty/hardware/nxp \
          trusty/user/base \
          trusty/device/arm/generic-arm64 \
          trusty/device/arm/vexpress-a15 \
          trusty/device/nxp/imx7d \
          trusty/device/x86/generic-x86_64 \
          trusty/vendor/google/aosp \
