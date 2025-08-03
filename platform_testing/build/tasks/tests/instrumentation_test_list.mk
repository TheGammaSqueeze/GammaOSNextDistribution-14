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

instrumentation_tests := \
    HelloWorldTests \
    BluetoothInstrumentationTests \
    LongevityPlatformLibTests \
    ManagedProvisioningTests \
    FrameworksCoreTests \
    FrameworksCoreGameManagerTests \
    FrameworksMockingCoreTests \
    BinderProxyCountingTestApp \
    BinderProxyCountingTestService \
    FrameworksNetTests \
    FrameworksUiServicesTests \
    BstatsTestApp \
    FrameworksServicesTests \
    FrameworksMockingServicesTests \
    WmTests \
    WmPerfTests \
    DisplayServiceTests \
    SuspendTestApp \
    FrameworksUtilTests \
    MtpServiceTests \
    MtpTests \
    DocumentsUITests \
    ShellTests \
    SystemUITests \
    TvSystemUITests \
    TestablesTests \
    FrameworksWifiApiTests \
    FrameworksWifiNonUpdatableApiTests \
    FrameworksWifiTests \
    FrameworksTelephonyTests \
    ContactsProviderTests \
    SettingsUnitTests \
    TelecomUnitTests \
    TraceurUiTests \
    AndroidVCardTests \
    PermissionFunctionalTests \
    BlockedNumberProviderTest \
    DownloadAppFunctionalTests \
    DynamicCodeLoggerIntegrationTests \
    UsbTests \
    DownloadProviderTests \
    EmergencyInfoUnitTests \
    CalendarProviderTests \
    SettingsLibTests \
    RSTest \
    PrintSpoolerOutOfProcessTests \
    CellBroadcastReceiverUnitTests \
    CellBroadcastServiceTests \
    TelephonyProviderTests \
    CarrierConfigTests \
    TeleServiceTests \
    PresencePollingTests \
    ImsCommonTests \
    SettingsProviderTest \
    FrameworksPrivacyLibraryTests \
    SettingsUITests \
    SettingsPerfTests \
    ExtServicesUnitTests-tplus \
    ExtServicesUnitTests-sminus \
    FrameworksNetSmokeTests \
    FlickerLibTest \
    FlickerTestsOther \
    FlickerTestsAppClose \
    FlickerTestsIme \
    FlickerTestsQuickswitch \
    FlickerTestsRotation \
    FlickerTestApp \
    WMShellFlickerTestsOther \
    WMShellFlickerTestsBubbles \
    WMShellFlickerTestsPip1 \
    WMShellFlickerTestsPip2 \
    WMShellFlickerTestsPip3 \
    WMShellFlickerTestsSplitScreenGroup1 \
    WMShellFlickerTestsSplitScreenGroup2 \
    WMShellUnitTests \
    trace_config_detailed.textproto \
    perfetto_trace_processor_shell \
    CarDeveloperOptionsUnitTests \
    SpaPrivilegedLibTests \
    SettingsSpaUnitTests \
    SpaLibTests \
    PermissionServiceMockingTests

ifneq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
instrumentation_tests += perf-setup
endif

# Storage Manager may not exist on device
ifneq ($(filter StorageManager, $(PRODUCT_PACKAGES)),)

instrumentation_tests += StorageManagerUnitTests

endif
