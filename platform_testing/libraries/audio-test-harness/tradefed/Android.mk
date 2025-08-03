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

# Custom Android.mk rules based on the TradeFed Contrib Android.mk
# that ensures that this library is added to the tradefed/ directory
# and thus the TradeFed classpath when it is built.

# Per b/178739059 while there is a custom rule that should handle this--
# it was rolled back and no longer functional, thus this custom
# .mk file is necessary.

LOCAL_PATH := $(call my-dir)

# makefile rules to copy jars to HOST_OUT/tradefed
# so tradefed.sh can automatically add to classpath
DEST_JAR := $(HOST_OUT)/tradefed/audiotestharness-tradefed-lib.jar
BUILT_JAR := $(call intermediates-dir-for,JAVA_LIBRARIES,audiotestharness-tradefed-lib,HOST)/javalib.jar
$(DEST_JAR): $(BUILT_JAR)
	$(copy-file-to-new-target)

# this dependency ensure the above rule will be executed if jar is built
$(HOST_OUT_JAVA_LIBRARIES)/audiotestharness-tradefed-lib.jar : $(DEST_JAR)

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))

