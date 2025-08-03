platform_tests += \
    ActivityManagerPerfTests \
    ActivityManagerPerfTestsStubApp1 \
    ActivityManagerPerfTestsStubApp2 \
    ActivityManagerPerfTestsStubApp3 \
    ActivityManagerPerfTestsTestApp \
    AdServicesScenarioTests \
    AndroidTVJankTests \
    AndroidXComposeStartupApp \
    ApiDemos \
    AppCompatibilityTest \
    AppLaunch \
    AppTransitionTests \
    AutoLocTestApp \
    AutoLocVersionedTestApp_v1 \
    AutoLocVersionedTestApp_v2 \
    BackgroundDexOptServiceIntegrationTests \
    BandwidthEnforcementTest \
    BandwidthTests \
    BootHelperApp \
    BusinessCard \
    CalculatorFunctionalTests \
    CalendarTests \
    camera_client_test \
    camera_metadata_tests \
    CellBroadcastReceiverTests \
    ConnectivityManagerTest \
    ContactsTests \
    CtsCameraTestCases \
    CtsHardwareTestCases \
    DataIdleTest \
    Development \
    DeviceHealthChecks \
    DynamicCodeLoggerIntegrationTests \
    DialerJankTests \
    DownloadManagerTestApp \
    StubIME \
    ExternalLocAllPermsTestApp \
    ExternalLocTestApp \
    ExternalLocVersionedTestApp_v1 \
    ExternalLocVersionedTestApp_v2 \
    ExternalSharedPermsBTTestApp \
    ExternalSharedPermsDiffKeyTestApp \
    ExternalSharedPermsFLTestApp \
    ExternalSharedPermsTestApp \
    flatland \
    FrameworkPerf \
    FrameworkPermissionTests \
    FrameworksCoreTests \
    FrameworksMockingCoreTests \
    FrameworksPrivacyLibraryTests \
    FrameworksUtilTests \
    InternalLocTestApp \
    JankMicroBenchmarkTests \
    LauncherIconsApp \
    long_trace_binder_config.textproto \
    long_trace_config.textproto \
    MemoryUsage \
    MultiDexLegacyTestApp \
    MultiDexLegacyTestApp2 \
    MultiDexLegacyTestServices \
    MultiDexLegacyTestServicesTests \
    MultiDexLegacyVersionedTestApp_v1 \
    MultiDexLegacyVersionedTestApp_v2 \
    MultiDexLegacyVersionedTestApp_v3 \
    NoLocTestApp \
    NoLocVersionedTestApp_v1 \
    NoLocVersionedTestApp_v2 \
    OverviewFunctionalTests \
    perfetto_trace_processor_shell \
    PerformanceAppTest \
    PerformanceLaunch \
    PermissionFunctionalTests \
    PermissionTestAppMV1 \
    PermissionUtils \
    PlatformCommonScenarioTests \
    PowerPerfTest \
    SdkSandboxPerfScenarioTests \
    SettingsUITests \
    SimpleServiceTestApp1 \
    SimpleServiceTestApp2 \
    SimpleServiceTestApp3 \
    SimpleTestApp \
    sl4a \
    SmokeTest \
    SmokeTestApp \
    SysAppJankTestsWear \
    TouchLatencyJankTestWear \
    trace_config.textproto \
    trace_config_detailed.textproto \
    trace_config_detailed_heapdump.textproto \
    trace_config_experimental.textproto \
    trace_config_multi_user_cuj_tests.textproto \
    UbSystemUiJankTests \
    UbWebViewJankTests \
    UiBench \
    UiBenchJankTests \
    UiBenchJankTestsWear \
    UiBenchMicrobenchmark \
    UpdateExternalLocTestApp_v1_ext \
    UpdateExternalLocTestApp_v2_none \
    UpdateExtToIntLocTestApp_v1_ext \
    UpdateExtToIntLocTestApp_v2_int \
    uwb_snippet \
    VersatileTestApp_Auto \
    VersatileTestApp_External \
    VersatileTestApp_Internal \
    VersatileTestApp_None \
    VoiceInteraction \
    WifiStrengthScannerUtil \

ifneq ($(strip $(BOARD_PERFSETUP_SCRIPT)),)
platform_tests += perf-setup
endif

ifneq ($(filter vsoc_arm vsoc_arm64 vsoc_x86 vsoc_x86_64, $(TARGET_BOARD_PLATFORM)),)
  platform_tests += \
    CuttlefishRilTests \
    CuttlefishWifiTests
endif

ifeq ($(HOST_OS),linux)
platform_tests += root-canal
endif
