
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'platform/AccurateTime',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_AccurateTime.platform_AccurateTime',
        ),
        test_common.define_test(
            'platform/ActivateDate',
            suites = ['regression'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/AesThroughput',
            suites = ['hwqual', 'kernel_per-build_benchmarks'],
            main_package = 'autotest_lib.client.site_tests.platform_AesThroughput.platform_AesThroughput',
        ),
        test_common.define_test(
            'platform/BootLockbox',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_BootLockbox.platform_BootLockbox',
        ),
        test_common.define_test(
            'platform/BootLockboxServer',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/BootPerf',
            suites = ['crosbolt_perf_nightly'],
            main_package = 'autotest_lib.client.site_tests.platform_BootPerf.platform_BootPerf',
        ),
        test_common.define_test(
            'platform/CheckErrorsInLog',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_CheckErrorsInLog.platform_CheckErrorsInLog',
        ),
        test_common.define_test(
            'platform/ChromeCgroups',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.platform_ChromeCgroups.platform_ChromeCgroups',
        ),
        test_common.define_test(
            'platform/CleanShutdown',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_CleanShutdown.platform_CleanShutdown',
        ),
        test_common.define_test(
            'platform/CloseOpenLid',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CompromisedStatefulPartition',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CorruptRootfs',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CrashStateful',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CrosDisksArchive',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CrosDisksArchive.platform_CrosDisksArchive',
        ),
        test_common.define_test(
            'platform/CrosDisksFilesystem',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CrosDisksFormat',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CrosDisksRename',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CrosDisksSshfs',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CrosDisksSshfs.platform_CrosDisksSshfs',
        ),
        test_common.define_test(
            'platform/Crossystem',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_Crossystem.platform_Crossystem',
        ),
        test_common.define_test(
            'platform/Crouton',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_Crouton.platform_Crouton',
        ),
        test_common.define_test(
            'platform/CryptohomeBadPerms',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeBadPerms.platform_CryptohomeBadPerms',
        ),
        test_common.define_test(
            'platform/CryptohomeChangePassword',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeChangePassword.platform_CryptohomeChangePassword',
        ),
        test_common.define_test(
            'platform/CryptohomeFio',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeFio.platform_CryptohomeFio',
        ),
        test_common.define_test(
            'platform/CryptohomeKeyEviction',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeKeyEviction.platform_CryptohomeKeyEviction',
        ),
        test_common.define_test(
            'platform/CryptohomeLECredentialManager',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeLECredentialManager.platform_CryptohomeLECredentialManager',
        ),
        test_common.define_test(
            'platform/CryptohomeLECredentialManagerServer',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.server.site_tests.platform_CryptohomeLECredentialManagerServer.platform_CryptohomeLECredentialManagerServer',
        ),
        test_common.define_test(
            'platform/CryptohomeMigrateKey',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeMigrateKey.platform_CryptohomeMigrateKey',
        ),
        test_common.define_test(
            'platform/CryptohomeMount',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeMount.platform_CryptohomeMount',
        ),
        test_common.define_test(
            'platform/CryptohomeMultiple',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeMultiple.platform_CryptohomeMultiple',
        ),
        test_common.define_test(
            'platform/CryptohomeNonDirs',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeNonDirs.platform_CryptohomeNonDirs',
        ),
        test_common.define_test(
            'platform/CryptohomeStress',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/CryptohomeTPMReOwn',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeTPMReOwn.platform_CryptohomeTPMReOwn',
        ),
        test_common.define_test(
            'platform/CryptohomeTPMReOwnServer',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.server.site_tests.platform_CryptohomeTPMReOwnServer.platform_CryptohomeTPMReOwnServer',
        ),
        test_common.define_test(
            'platform/CryptohomeTestAuth',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeTestAuth.platform_CryptohomeTestAuth',
        ),
        test_common.define_test(
            'platform/CryptohomeTpmLiveTest',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_CryptohomeTpmLiveTest.platform_CryptohomeTpmLiveTest',
        ),
        test_common.define_test(
            'platform/CryptohomeTpmLiveTestServer',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/DBusMachineIdRotation',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_DBusMachineIdRotation.platform_DBusMachineIdRotation',
        ),
        test_common.define_test(
            'platform/DaemonRespawn',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DaemonsRespawn.platform_DaemonsRespawn',
        ),
        test_common.define_test(
            'platform/DebugDaemonDumpDebugLogs',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonDumpDebugLogs.platform_DebugDaemonDumpDebugLogs',
        ),
        test_common.define_test(
            'platform/DebugDaemonGetNetworkStatus',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonGetNetworkStatus.platform_DebugDaemonGetNetworkStatus',
        ),
        test_common.define_test(
            'platform/DebugDaemonGetPerfData',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonGetPerfData.platform_DebugDaemonGetPerfData',
        ),
        test_common.define_test(
            'platform/DebugDaemonGetPerfOutputFd',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonGetPerfOutputFd.platform_DebugDaemonGetPerfOutputFd',
        ),
        test_common.define_test(
            'platform/DebugDaemonGetRoutes',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonGetRoutes.platform_DebugDaemonGetRoutes',
        ),
        test_common.define_test(
            'platform/DebugDaemonPerfDataInFeedbackLogs',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonPerfDataInFeedbackLogs.platform_DebugDaemonPerfDataInFeedbackLogs',
        ),
        test_common.define_test(
            'platform/DebugDaemonPing',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonPing.platform_DebugDaemonPing',
        ),
        test_common.define_test(
            'platform/DebugDaemonTracePath',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_DebugDaemonTracePath.platform_DebugDaemonTracePath',
        ),
        test_common.define_test(
            'platform/EncryptedStateful',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_EncryptedStateful.platform_EncryptedStateful',
        ),
        test_common.define_test(
            'platform/ExternalUSBBootStress',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/ExternalUSBStress',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/ExternalUsbPeripherals',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/FileNum',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_FileNum.platform_FileNum',
        ),
        test_common.define_test(
            'platform/FileSize',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_FileSize.platform_FileSize',
        ),
        test_common.define_test(
            'platform/Firewall',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_Firewall.platform_Firewall',
        ),
        test_common.define_test(
            'platform/FlashErasers',
            suites = ['faft_flashrom'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/Flashrom',
            suites = ['faft_flashrom'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/GesturesRegressionTest',
            suites = ['bvt-perbuild', 'touch'],
            main_package = 'autotest_lib.client.site_tests.platform_GesturesRegressionTest.platform_GesturesRegressionTest',
        ),
        test_common.define_test(
            'platform/HWwatchdog',
            suites = ['kernel_per-build_regression'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/HighResTimers',
            suites = ['bvt-perbuild', 'hwqual'],
            main_package = 'autotest_lib.client.site_tests.platform_HighResTimers.platform_HighResTimers',
        ),
        test_common.define_test(
            'platform/ImageLoader',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_ImageLoader.platform_ImageLoader',
        ),
        test_common.define_test(
            'platform/ImageLoaderServer',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/InitLoginPerf',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_InitLoginPerf.platform_InitLoginPerf',
        ),
        test_common.define_test(
            'platform/InitLoginPerfServer',
            suites = ['crosbolt_perf_weekly'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/InputBrightness',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_InputBrightness.platform_InputBrightness',
        ),
        test_common.define_test(
            'platform/InputBrowserNav',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_InputBrowserNav.platform_InputBrowserNav',
        ),
        test_common.define_test(
            'platform/InputNewTab',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_InputNewTab.platform_InputNewTab',
        ),
        test_common.define_test(
            'platform/InputScreenshot',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_InputScreenshot.platform_InputScreenshot',
        ),
        test_common.define_test(
            'platform/InputVolume',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_InputVolume.platform_InputVolume',
        ),
        test_common.define_test(
            'platform/InstallFW',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/InstallRecoveryImage',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/InstallTestImage',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/InternalDisplay',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/KernelVersion',
            suites = ['hwqual', 'regression'],
            main_package = 'autotest_lib.client.site_tests.platform_KernelVersion.platform_KernelVersion',
        ),
        test_common.define_test(
            'platform/LibCBench',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_LibCBench.platform_LibCBench',
        ),
        test_common.define_test(
            'platform/LogDupSuppression',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_LogDupSuppression.platform_LogDupSuppression',
        ),
        test_common.define_test(
            'platform/LogNonKernelKmsg',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_LogNonKernelKmsg.platform_LogNonKernelKmsg',
        ),
        test_common.define_test(
            'platform/LogoutPerf',
            suites = ['bvt-perbuild', 'crosbolt_perf_perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_LogoutPerf.platform_LogoutPerf',
        ),
        test_common.define_test(
            'platform/LongPressPower',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/MemCheck',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_MemCheck.platform_MemCheck',
        ),
        test_common.define_test(
            'platform/MetricsUploader',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_MetricsUploader.platform_MetricsUploader',
        ),
        test_common.define_test(
            'platform/MouseScrollTest',
            suites = ['crosbolt_perf_nightly'],
            main_package = 'autotest_lib.client.site_tests.platform_MouseScrollTest.platform_MouseScrollTest',
        ),
        test_common.define_test(
            'platform/Nvram',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_Nvram.platform_Nvram',
        ),
        test_common.define_test(
            'platform/OpenSSLActual',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_OpenSSLActual.platform_OpenSSLActual',
        ),
        test_common.define_test(
            'platform/Perf',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_Perf.platform_Perf',
        ),
        test_common.define_test(
            'platform/Pkcs11ChangeAuthData',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_Pkcs11ChangeAuthData.platform_Pkcs11ChangeAuthData',
        ),
        test_common.define_test(
            'platform/Pkcs11Events',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_Pkcs11Events.platform_Pkcs11Events',
        ),
        test_common.define_test(
            'platform/Pkcs11InitOnLogin',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_Pkcs11InitOnLogin.platform_Pkcs11InitOnLogin',
        ),
        test_common.define_test(
            'platform/Pkcs11InitUnderErrors',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.platform_Pkcs11InitUnderErrors.platform_Pkcs11InitUnderErrors',
        ),
        test_common.define_test(
            'platform/Pkcs11LoadPerf',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_Pkcs11LoadPerf.platform_Pkcs11LoadPerf',
        ),
        test_common.define_test(
            'platform/Powerwash',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/PrintJob',
            suites = ['audio_advanced'],
            main_package = 'autotest_lib.client.site_tests.platform_PrintJob.platform_PrintJob',
        ),
        test_common.define_test(
            'platform/Quipper',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_Quipper.platform_Quipper',
        ),
        test_common.define_test(
            'platform/Rootdev',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.platform_Rootdev.platform_Rootdev',
        ),
        test_common.define_test(
            'platform/RotationFps',
            suites = ['crosbolt_arc_perf_nightly'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/S0ixCycle',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/S3Cycle',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/ScrollTest',
            suites = ['crosbolt_perf_nightly'],
            main_package = 'autotest_lib.client.site_tests.platform_ScrollTest.platform_ScrollTest',
        ),
        test_common.define_test(
            'platform/SecureEraseFile',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_SecureEraseFile.platform_SecureEraseFile',
        ),
        test_common.define_test(
            'platform/SessionManagerBlockDevmodeSetting',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_SessionManagerBlockDevmodeSetting.platform_SessionManagerBlockDevmodeSetting',
        ),
        test_common.define_test(
            'platform/SessionManagerStateKeyGeneration',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_SessionManagerStateKeyGeneration.platform_SessionManagerStateKeyGeneration',
        ),
        test_common.define_test(
            'platform/StageAndRecover',
            suites = ['platform_test_nightly'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/SuspendResumeTiming',
            suites = ['usb_detect'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/TLSDate',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_TLSDate.platform_TLSDate',
        ),
        test_common.define_test(
            'platform/TLSDateActual',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_TLSDateActual.platform_TLSDateActual',
        ),
        test_common.define_test(
            'platform/TPMEvict',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.platform_TPMEvict.platform_TPMEvict',
        ),
        test_common.define_test(
            'platform/TabletMode',
            suites = ['tablet_mode'],
            main_package = 'autotest_lib.client.site_tests.platform_TabletMode.platform_TabletMode',
        ),
        test_common.define_test(
            'platform/TempFS',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.platform_TempFS.platform_TempFS',
        ),
        test_common.define_test(
            'platform/ToolchainTests',
            suites = ['bvt-perbuild', 'toolchain-tests'],
            main_package = 'autotest_lib.client.site_tests.platform_ToolchainTests.platform_ToolchainTests',
        ),
        test_common.define_test(
            'platform/TraceClockMonotonic',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.platform_TraceClockMonotonic.platform_TraceClockMonotonic',
        ),
        test_common.define_test(
            'platform/TrackpadStressServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/UReadAheadServer',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'platform/USBHIDWake',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'platform/UdevVars',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.platform_UdevVars.platform_UdevVars',
        ),
        test_common.define_test(
            'platform/Vpd',
            suites = ['regression'],
            main_package = '',
        )
    ]
