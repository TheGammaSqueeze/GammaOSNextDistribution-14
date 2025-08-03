#
# Copyright (C) 2019 The Android Open Source Project.
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

COMPATIBILITY_TESTCASES_OUT_mts := $(HOST_OUT)/mts/android-mts/testcases
COMPATIBILITY_TESTCASES_OUT_INCLUDE_MODULE_FOLDER_mts := true
COMPATIBILITY_TESTCASES_OUT_mcts := $(HOST_OUT)/mcts/android-mts/testcases
COMPATIBILITY_TESTCASES_OUT_INCLUDE_MODULE_FOLDER_mcts := true

mts_modules :=
mts_modules += \
               adbd \
               adservices \
               adservices-cts-only \
               adservices-unittest-only \
               appsearch \
               art \
               bluetooth \
               cellbroadcast \
               configinfrastructure \
               conscrypt \
               cronet \
               dnsresolver \
               documentsui \
               extservices \
               healthfitness \
               ipsec \
               mainline-infra \
               media \
               mediaprovider \
               networking \
               neuralnetworks \
               ondevicepersonalization \
               permission \
               rkpd \
               scheduling \
               sdkextensions \
               statsd \
               tethering \
               tzdata \
               uwb \
               webviewbootstrap \
               wifi

$(foreach module, $(mts_modules), \
	$(eval COMPATIBILITY_TESTCASES_OUT_mts-$(module) := $(HOST_OUT)/mts-$(module)/android-mts/testcases) \
	$(eval COMPATIBILITY_TESTCASES_OUT_INCLUDE_MODULE_FOLDER_mts-$(module) := true) \
)

$(foreach module, $(mts_modules), \
	$(eval COMPATIBILITY_TESTCASES_OUT_mcts-$(module) := $(HOST_OUT)/mcts-$(module)/android-mts/testcases) \
	$(eval COMPATIBILITY_TESTCASES_OUT_INCLUDE_MODULE_FOLDER_mcts-$(module) := true) \
)
