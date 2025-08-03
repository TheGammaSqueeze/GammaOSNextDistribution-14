# Copyright (C) 2020 The Android Open Source Project
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

LOCAL_DIR := $(GET_LOCAL_DIR)

MODULE := $(LOCAL_DIR)

PROTOBUF := $(if $(wildcard external/protobuf),external/protobuf/src,prebuilts/libprotobuf/src)

MODULE_SRCS := \
	$(PROTOBUF)/google/protobuf/any_lite.cc \
	$(PROTOBUF)/google/protobuf/arena.cc \
	$(PROTOBUF)/google/protobuf/arenastring.cc \
	$(PROTOBUF)/google/protobuf/extension_set.cc \
	$(PROTOBUF)/google/protobuf/generated_enum_util.cc \
	$(PROTOBUF)/google/protobuf/generated_message_util.cc \
	$(PROTOBUF)/google/protobuf/implicit_weak_message.cc \
	$(PROTOBUF)/google/protobuf/io/coded_stream.cc \
	$(PROTOBUF)/google/protobuf/io/io_win32.cc \
	$(PROTOBUF)/google/protobuf/io/strtod.cc \
	$(PROTOBUF)/google/protobuf/io/zero_copy_stream.cc \
	$(PROTOBUF)/google/protobuf/io/zero_copy_stream_impl.cc \
	$(PROTOBUF)/google/protobuf/io/zero_copy_stream_impl_lite.cc \
	$(PROTOBUF)/google/protobuf/message_lite.cc \
	$(PROTOBUF)/google/protobuf/parse_context.cc \
	$(PROTOBUF)/google/protobuf/repeated_field.cc \
	$(PROTOBUF)/google/protobuf/repeated_ptr_field.cc \
	$(PROTOBUF)/google/protobuf/stubs/bytestream.cc \
	$(PROTOBUF)/google/protobuf/stubs/common.cc \
	$(PROTOBUF)/google/protobuf/stubs/int128.cc \
	$(PROTOBUF)/google/protobuf/stubs/status.cc \
	$(PROTOBUF)/google/protobuf/stubs/statusor.cc \
	$(PROTOBUF)/google/protobuf/stubs/stringpiece.cc \
	$(PROTOBUF)/google/protobuf/stubs/stringprintf.cc \
	$(PROTOBUF)/google/protobuf/stubs/structurally_valid.cc \
	$(PROTOBUF)/google/protobuf/stubs/strutil.cc \
	$(PROTOBUF)/google/protobuf/stubs/time.cc \
	$(PROTOBUF)/google/protobuf/wire_format_lite.cc \

MODULE_CPPFLAGS := -Wno-sign-compare

MODULE_LIBRARY_DEPS := \
	trusty/user/base/lib/libc-trusty \
	trusty/user/base/lib/libstdc++-trusty \

MODULE_INCLUDES := $(LOCAL_DIR)

MODULE_EXPORT_INCLUDES += \
	$(PROTOBUF) \

include make/library.mk
