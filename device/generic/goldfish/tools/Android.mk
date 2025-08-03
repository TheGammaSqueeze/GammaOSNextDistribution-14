#
# Copyright 2019 The Android Open-Source Project
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

ifneq ($(filter emulator_% emulator64_% emu64%, $(TARGET_DEVICE)),)
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
EMU_EXTRA_FILES := \
        $(PRODUCT_OUT)/system-qemu-config.txt \
        $(PRODUCT_OUT)/ramdisk-qemu.img \
        $(PRODUCT_OUT)/misc_info.txt \
        $(PRODUCT_OUT)/vbmeta.img \
        $(PRODUCT_OUT)/VerifiedBootParams.textproto \
        $(foreach p,$(BOARD_SUPER_PARTITION_PARTITION_LIST),$(PRODUCT_OUT)/$(p).img)

ADVANCED_FEATURES_FILENAME := advancedFeatures.ini
ifneq ($(filter %_minigbm, $(TARGET_PRODUCT)),)
ADVANCED_FEATURES_FILENAME := advancedFeatures.ini.minigbm
ADVANCED_FEATURES_FILES :=

endif
ifeq ($(filter sdk_gphone_%, $(TARGET_PRODUCT)),)
ifeq ($(TARGET_BUILD_VARIANT),user)
ADVANCED_FEATURES_FILES += device/generic/goldfish/data/etc/user/$(ADVANCED_FEATURES_FILENAME)
else
ADVANCED_FEATURES_FILES += device/generic/goldfish/data/etc/$(ADVANCED_FEATURES_FILENAME)
endif
else
ifeq ($(TARGET_BUILD_VARIANT),user)
ADVANCED_FEATURES_FILES += device/generic/goldfish/data/etc/google/user/$(ADVANCED_FEATURES_FILENAME)
else
ADVANCED_FEATURES_FILES += device/generic/goldfish/data/etc/google/userdebug/$(ADVANCED_FEATURES_FILENAME)
endif
endif

EMU_EXTRA_FILES += device/generic/goldfish/data/etc/config.ini
EMU_EXTRA_FILES += device/generic/goldfish/data/etc/encryptionkey.img

name := emu-extra-linux-system-images

EMU_EXTRA_TARGET := $(PRODUCT_OUT)/$(name).zip

ifeq ($(TARGET_ARCH), arm)
# This is wrong and should be retired.
EMULATOR_KERNEL_FILE := prebuilts/qemu-kernel/arm/3.18/kernel-qemu2
EMULATOR_KERNEL_DIST_NAME := kernel-ranchu
else
ifeq ($(TARGET_ARCH), x86)
# Use 64-bit kernel even for 32-bit Android
EMULATOR_KERNEL_DIST_NAME := kernel-ranchu-64
else
# All other arches are 64-bit
EMULATOR_KERNEL_DIST_NAME := kernel-ranchu
endif # x86
endif # arm

$(EMU_EXTRA_TARGET): PRIVATE_PACKAGE_SRC := \
        $(call intermediates-dir-for, PACKAGING, emu_extra_target)

$(EMU_EXTRA_TARGET): $(EMU_EXTRA_FILES) $(ADVANCED_FEATURES_FILES) $(EMULATOR_KERNEL_FILE) $(SOONG_ZIP)
	@echo "Package: $@"
	rm -rf $@ $(PRIVATE_PACKAGE_SRC)
	mkdir -p $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)/system
	$(foreach f,$(EMU_EXTRA_FILES), cp $(f) $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)/$(notdir $(f)) &&) true
	$(foreach f,$(ADVANCED_FEATURES_FILES), cp $(f) $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)/advancedFeatures.ini &&) true
	cp $(EMULATOR_KERNEL_FILE) $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)/${EMULATOR_KERNEL_DIST_NAME}
	cp -r $(PRODUCT_OUT)/data $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)
	cp $(PRODUCT_OUT)/system/build.prop $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)/system
	$(SOONG_ZIP) -o $@ -C $(PRIVATE_PACKAGE_SRC) -D $(PRIVATE_PACKAGE_SRC)/$(TARGET_ARCH)

.PHONY: emu_extra_imgs
emu_extra_imgs: $(EMU_EXTRA_TARGET)

$(call dist-for-goals-with-filenametag, emu_extra_imgs, $(EMU_EXTRA_TARGET))

include $(call all-makefiles-under,$(LOCAL_PATH))
endif
