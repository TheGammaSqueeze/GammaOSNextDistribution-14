# Copyright (C) 2014-2015 The Android Open Source Project
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

CUR_DIR := $(GET_LOCAL_DIR)

MODULE_SRCS += $(CUR_DIR)/keymaster_ipc.cpp

MODULE_LIBRARY_DEPS += trusty/user/base/interface/keymaster

ifdef TRUSTY_KM_TARGET_ACCESS_POLICY
    MODULE_LIBRARY_DEPS+= $(TRUSTY_KM_TARGET_ACCESS_POLICY)
else
    MODULE_SRCS+= $(CUR_DIR)/keymaster_generic_access_policy.cpp
endif

CUR_DIR =
