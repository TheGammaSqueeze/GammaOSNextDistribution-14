# Copyright (C) 2018 The Android Open Source Project
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

# This host test is to test the implementation for mock secure storage
# interface.
HOST_TEST := mock_storage_test

HOST_SRCS += \
	trusty/user/app/storage/test/storage-unittest/main.c \

HOST_INCLUDE_DIRS += \
	lib/include \
	lib/interface/storage/include \

HOST_FLAGS := \
	-Wno-deprecated-declarations \
	-DSTORAGE_FAKE \

include trusty/user/app/storage/storage_mock/add_mock_storage.mk
include trusty/kernel/make/host_test.mk
