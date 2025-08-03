#
# Copyright 2017 The Android Open Source Project
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

ifneq ($(filter $(LOCAL_PATH),$(PRODUCT_SOONG_NAMESPACES)),)
  $(eval $(call declare-1p-copy-files,device/generic/goldfish/data,))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish/input,))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish/wifi,))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish/camera,))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,hals.conf))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,init.qemu-adb-keys.sh))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,init.system_ext.rc))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,.json))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,ueventd.rc))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,wpa_supplicant.conf))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,media_profiles_V1_0.xml))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,init.ranchu.rc))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,fstab.ranchu))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,display_settings.xml))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,display_settings_freeform.xml))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,device_state_configuration.xml))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,init.ranchu-core.sh))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,init.ranchu-net.sh))
  $(eval $(call declare-1p-copy-files,device/generic/goldfish,audio_policy_configuration.xml))

  ifeq ($(BUILD_QEMU_IMAGES),true)
    QEMU_CUSTOMIZATIONS := true
  endif

  ifeq ($(QEMU_CUSTOMIZATIONS),true)
    INSTALLED_EMULATOR_INFO_TXT_TARGET := $(PRODUCT_OUT)/emulator-info.txt
    emulator_info_txt := $(wildcard ${LOCAL_PATH}/emulator-info.txt)

$(INSTALLED_EMULATOR_INFO_TXT_TARGET): $(emulator_info_txt)
	$(call pretty,"Generated: ($@)")
	$(hide) grep -v '#' $< > $@

    $(call dist-for-goals, dist_files, $(INSTALLED_EMULATOR_INFO_TXT_TARGET))
    $(call dist-for-goals, sdk, $(INSTALLED_EMULATOR_INFO_TXT_TARGET))

    subdir_makefiles=$(call first-makefiles-under,$(LOCAL_PATH))
    $(foreach mk,$(subdir_makefiles),$(info including $(mk) ...)$(eval include $(mk)))

    include device/generic/goldfish/tasks/emu_img_zip.mk
  endif
endif
