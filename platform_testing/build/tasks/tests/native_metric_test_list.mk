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

native_metric_tests := \
    binderAddInts \
    binderRpcBenchmark \
    bionic-benchmarks \
    binder_thread_stats \
    hwuimacro \
    hwuimicro \
    inputflinger_benchmarks \
    libandroidfw_benchmarks \
    libhwbinder_benchmark \
    libjavacore-benchmarks \
    libpowermanager_benchmarks \
    libvibratorservice_benchmarks \
    minikin_perftests \
    mmapPerf \
    netd_benchmark \
    VibratorHalIntegrationBenchmark \
    librenderengine_bench

ifneq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
native_metric_tests += perf-setup
endif
