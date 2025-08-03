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

instrumentation_metric_tests := \
    ActivityManagerPerfTests \
    ActivityManagerPerfTestsTestApp \
    ActivityManagerPerfTestsStubApp1 \
    ActivityManagerPerfTestsStubApp2 \
    ActivityManagerPerfTestsStubApp3 \
    AutofillPerfTests \
    BlobStorePerfTests \
    CorePerfTests \
    ContentCapturePerfTests \
    DocumentsUIAppPerfTests \
    MtpServicePerfTests \
    RsBlasBenchmark \
    ImageProcessingJB \
    MediaProviderClientTests \
    MultiUserPerfDummyApp \
    MultiUserPerfTests \
    NeuralNetworksApiBenchmark \
    PackageManagerPerfTests \
    QueriesAll0 \
    QueriesAll1 \
    QueriesAll2 \
    QueriesAll3 \
    QueriesAll4 \
    QueriesAll5 \
    QueriesAll6 \
    QueriesAll7 \
    QueriesAll8 \
    QueriesAll9 \
    QueriesAll10 \
    QueriesAll11 \
    QueriesAll12 \
    QueriesAll13 \
    QueriesAll14 \
    QueriesAll15 \
    QueriesAll16 \
    QueriesAll17 \
    QueriesAll18 \
    QueriesAll19 \
    QueriesAll20 \
    QueriesAll21 \
    QueriesAll22 \
    QueriesAll23 \
    QueriesAll24 \
    QueriesAll25 \
    QueriesAll26 \
    QueriesAll27 \
    QueriesAll28 \
    QueriesAll29 \
    QueriesAll30 \
    QueriesAll31 \
    QueriesAll32 \
    QueriesAll33 \
    QueriesAll34 \
    QueriesAll35 \
    QueriesAll36 \
    QueriesAll37 \
    QueriesAll38 \
    QueriesAll39 \
    QueriesAll40 \
    QueriesAll41 \
    QueriesAll42 \
    QueriesAll43 \
    QueriesAll44 \
    QueriesAll45 \
    QueriesAll46 \
    QueriesAll47 \
    QueriesAll48 \
    QueriesAll49 \
    SettingsProviderPerfTests \
    TextClassifierPerfTests \
    WmPerfTests \
    ImePerfTests \
    SurfaceFlingerPerfTests \
    trace_config_detailed.textproto \
    trace_config_multi_user.textproto \
    perfetto_trace_processor_shell \
    RubidiumPerfTests

    # TODO(b/72332760): Uncomment when fixed
    #DocumentsUIPerfTests

ifneq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
instrumentation_metric_tests += perf-setup
endif
