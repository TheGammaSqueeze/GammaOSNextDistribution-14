#
# Copyright (C) 2016 The Android Open Source Project
#
# This software is licensed under the terms of the GNU General Public
# License version 2, as published by the Free Software Foundation, and
# may be copied, distributed, and modified under those terms.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#

include $(CLEAR_VARS)

module_name := ltp_$(subst /,_,$(module_prebuilt))
module_stem := $(notdir $(module_prebuilt))
module_path := $(patsubst %/,%,$(dir $(module_prebuilt)))

LOCAL_MODULE := $(module_name)
LOCAL_LICENSE_KINDS := SPDX-license-identifier-Apache-2.0 SPDX-license-identifier-GPL-2.0
LOCAL_LICENSE_CONDITIONS := notice restricted
LOCAL_NOTICE_FILE := $(LOCAL_PATH)/../COPYING $(LOCAL_PATH)/../NOTICE
LOCAL_INSTALLED_MODULE_STEM := $(module_stem)
LOCAL_PREBUILT_MODULE_FILE := $(local_ltp_root)/$(module_src_files)
LOCAL_MODULE_RELATIVE_PATH := ltp/$(module_path)
LOCAL_MODULE_CLASS := NATIVE_TESTS
LOCAL_MULTILIB := both

include $(BUILD_PREBUILT)

module_name :=
module_stem :=
module_path :=
