#
# Copyright (C) 2016 The Android Open Source Project
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


# Rules to generate setup script for device perf tests
# Different devices may share the same script. To add a new script, define a
# new variable named <device name>_script, pointing at the script in current
# source folder.
# At execution time, scripts will be pushed onto device and run with root
# identity

LOCAL_PATH:= $(call my-dir)

# only define the target if a perf setup script is defined by the BoardConfig
# of the device we are building.
#
# To add a new script:
# 1. add a new setup script suitable for the device at:
#    platform_testing/scripts/perf-setup/
# 2. modify BoardConfig.mk of the corresponding device under:
#    device/<OEM name>/<device name/
# 3. add variable "BOARD_PERFSETUP_SCRIPT", and point it at the path to the new
#    perf setup script; the path should be relative to the build root
ifeq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
perfsetup_script := platform_testing/scripts/perf-setup/empty-setup.sh
else
perfsetup_script := $(BOARD_PERFSETUP_SCRIPT)
endif

include $(CLEAR_VARS)
LOCAL_MODULE := perf-setup
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0
LOCAL_LICENSE_CONDITIONS := notice
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/local/tmp
LOCAL_MODULE_STEM := perf-setup.sh
LOCAL_PREBUILT_MODULE_FILE := $(perfsetup_script)
LOCAL_COMPATIBILITY_SUITE := device-tests
include $(BUILD_PREBUILT)

perfsetup_script :=
