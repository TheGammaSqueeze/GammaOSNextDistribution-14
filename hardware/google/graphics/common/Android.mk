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

ifeq (google,$(TARGET_SOC_NAME))
build_dirs :=  \
    libhwjpeg  \
    libscaler  \
    libgscaler \
    libacryl \
    libmemtrack \
    libv4l2

ifdef BOARD_HWC_VERSION
build_dirs += $(BOARD_HWC_VERSION)

ifeq ($(BOARD_HWC_VERSION), hwc3)
#hwc3 depends libexynosdisplay in libhwc2.1
build_dirs += libhwc2.1
endif

endif

include $(call all-named-subdir-makefiles,$(build_dirs))
endif
