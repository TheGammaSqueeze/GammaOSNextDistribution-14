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

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

ifeq (,$(wildcard $(LOCAL_PATH)/../../core))
.PHONY: tradefed-core
tradefed-core: tradefed atest_tradefed.sh
.PHONY: tradefed-all
tradefed-all: tradefed atest_tradefed.sh

tradefed_dist_host_jars := tradefed
tradefed_dist_artifacts := tradefed-tests.jar loganalysis.jar tradefed-contrib.jar tf-contrib-tests.jar tradefed-isolation.jar tradefed.sh tradefed_win.bat script_help.sh atest_tradefed.sh TradeFedUiTestApp.apk TradeFedTestApp.apk version.txt
tradefed_dist_suite_artifacts := compatibility-host-util.jar  compatibility-tradefed.jar

tradefed_dist_copy_pairs := $(foreach m, $(tradefed_dist_host_jars), $(call intermediates-dir-for,JAVA_LIBRARIES,$(m),HOST,COMMON)/javalib.jar:$(m).jar)
tradefed_dist_copy_pairs += $(foreach m, $(tradefed_dist_artifacts), $(LOCAL_PATH)/../filegroups/tradefed/$(m):$(m))
tradefed_dist_copy_pairs += $(foreach m, $(tradefed_dist_suite_artifacts), $(LOCAL_PATH)/../filegroups/suite/$(m):$(m))

tradefed_dist_artifacts :=

tradefed_dist_intermediates := $(call intermediates-dir-for,PACKAGING,tradefed_dist,HOST,COMMON)
tradefed_dist_zip := $(tradefed_dist_intermediates)/tradefed.zip
$(tradefed_dist_zip) : PRIVATE_COPY_PAIRS := $(tradefed_dist_copy_pairs)
$(tradefed_dist_zip) : $(SOONG_ZIP) $(foreach f,$(tradefed_dist_copy_pairs),$(call word-colon,1,$(f)))
	rm -rf $(dir $@)/tmp && mkdir -p $(dir $@)/tmp
	$(foreach f,$(PRIVATE_COPY_PAIRS), \
	  cp -f $(call word-colon,1,$(f)) $(dir $@)/tmp/$(call word-colon,2,$(f)) &&) true
	$(SOONG_ZIP) -o $@ -C $(dir $@)/tmp \
	  $(foreach f,$(PRIVATE_COPY_PAIRS),-f $(dir $@)/tmp/$(call word-colon,2,$(f)))

$(call declare-1p-container,$(tradefed_dist_zip),tools/tradefederation/prebuilts)
$(call declare-container-license-deps,$(tradefed_dist_zip),$(filter $(OUT_DIR)/%,$(foreach f,$(tradefed_dist_copy_pairs), $(call word-colon,1,$(f)))),$(tradefed_dist_zip):)

$(call dist-for-goals, tradefed, $(tradefed_dist_zip))

tradefed_dist_copy_pairs :=
tradefed_dist_intermediates :=
tradefed_dist_zip :=
endif

# Build all sub-directories
include $(call all-makefiles-under,$(LOCAL_PATH))
