#
# Copyright (C) 2023 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

# Included makefiles change LOCAL_PATH, so make a copy to use in recipes.
BERBERIS_DIR := $(LOCAL_PATH)

# Berberis includes some components which may conflict with other packages.
# Only build it when requested explicitly.
ifeq ($(BUILD_BERBERIS),true)

include $(LOCAL_PATH)/berberis_config.mk

.PHONY: berberis_all
berberis_all: \
    $(BERBERIS_PRODUCT_PACKAGES) \
    $(BERBERIS_DEV_PRODUCT_PACKAGES)

ifeq ($(BUILD_BERBERIS_RISCV64_TO_X86_64),true)

berberis_all: \
    $(BERBERIS_PRODUCT_PACKAGES_RISCV64_TO_X86_64) \
    $(BERBERIS_DEV_PRODUCT_PACKAGES_RISCV64_TO_X86_64)

endif  # BUILD_BERBERIS_RISCV64_TO_X86_64

include $(LOCAL_PATH)/tests/run_host_tests.mk

endif  # BUILD_BERBERIS
