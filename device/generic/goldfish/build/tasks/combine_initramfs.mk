# Copyright (C) 2021 The Android Open Source Project
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

# Normally all build/tasks/*.mk files are included from
# build/make/core/Makefile. Since we don't want these
# rules to also apply to other products, check for an
# arbitrary flag that indicates we need the rules.
ifneq (,$(GOLDFISH_COMBINE_INITRAMFS))

# Normally, the bootloader is supposed to concatenate the Android initramfs
# and the initramfs for the kernel modules and let the kernel combine
# them. However, the bootloader that we're using with FVP (U-Boot) doesn't
# support concatenation, so we implement it in the build system.
$(OUT_DIR)/target/product/$(PRODUCT_DEVICE)/boot.img: $(OUT_DIR)/target/product/$(PRODUCT_DEVICE)/combined-ramdisk.img

$(OUT_DIR)/target/product/$(PRODUCT_DEVICE)/combined-ramdisk.img: $(OUT_DIR)/target/product/$(PRODUCT_DEVICE)/ramdisk.img kernel/prebuilts/common-modules/virtual-device/$(TARGET_KERNEL_USE)/arm64/initramfs.img
	cat $^ > $@

endif
