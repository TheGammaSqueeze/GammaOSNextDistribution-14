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
#
################################################################################
#
# NanoApp C/C++ Makefile Utils
#
################################################################################

SRCS += $(NANOHUB_DIR)/app/chre/common/chre10_app.c
SRCS += $(NANOHUB_DIR)/app/chre/common/chre10_app_syscalls.c

include $(NANOHUB_DIR)/firmware_conf.mk

CFLAGS += $(COMMON_FLAGS)

# CHRE API 1.0
BIN_POSTPROCESS_ARGS := -c 0x0100
CFLAGS += -I$(NANOHUB_DIR)/../../../../system/chre/chre_api/legacy/v1_0

include $(NANOHUB_DIR)/app/app.mk
