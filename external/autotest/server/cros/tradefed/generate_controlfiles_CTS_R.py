#!/usr/bin/env python3
# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections

CONFIG = {}

CONFIG['TEST_NAME'] = 'cheets_CTS_R'
CONFIG['DOC_TITLE'] = 'Android Compatibility Test Suite (CTS)'
CONFIG['MOBLAB_SUITE_NAME'] = 'suite:cts'
CONFIG['MOBLAB_HARDWARE_SUITE_NAME'] = 'suite:cts-hardware'
CONFIG['COPYRIGHT_YEAR'] = 2020
CONFIG['AUTHKEY'] = ''

# Both arm, x86 tests results normally is below 200MB.
# 1000MB should be sufficient for CTS tests and dump logs for android-cts.
CONFIG['LARGE_MAX_RESULT_SIZE'] = 1000 * 1024

# Individual module normal produces less results than all modules, which is
# ranging from 4MB to 50MB. 500MB should be sufficient to handle all the cases.
CONFIG['NORMAL_MAX_RESULT_SIZE'] = 500 * 1024

CONFIG['TRADEFED_CTS_COMMAND'] = 'cts'
CONFIG['TRADEFED_RETRY_COMMAND'] = 'retry'
CONFIG['TRADEFED_DISABLE_REBOOT'] = False
CONFIG['TRADEFED_DISABLE_REBOOT_ON_COLLECTION'] = True
CONFIG['TRADEFED_MAY_SKIP_DEVICE_INFO'] = False
CONFIG['TRADEFED_EXECUTABLE_PATH'] = 'android-cts/tools/cts-tradefed'
CONFIG['TRADEFED_IGNORE_BUSINESS_LOGIC_FAILURE'] = False

# On moblab everything runs in the same suite.
CONFIG['INTERNAL_SUITE_NAMES'] = [
        'suite:arc-cts-r', 'suite:arc-cts', 'suite:arc-cts-unibuild'
]
CONFIG['QUAL_SUITE_NAMES'] = ['suite:arc-cts-qual']
CONFIG['HARDWARE_SUITE_NAME'] = 'suite:arc-cts-hardware'

CONFIG['CONTROLFILE_TEST_FUNCTION_NAME'] = 'run_TS'
CONFIG['CONTROLFILE_WRITE_SIMPLE_QUAL_AND_REGRESS'] = False
CONFIG['CONTROLFILE_WRITE_CAMERA'] = True
CONFIG['CONTROLFILE_WRITE_EXTRA'] = True

# The dashboard suppresses upload to APFE for GS directories (based on autotest
# tag) that contain 'tradefed-run-collect-tests'. b/119640440
# Do not change the name/tag without adjusting the dashboard.
_COLLECT = 'tradefed-run-collect-tests-only-internal'
_PUBLIC_COLLECT = 'tradefed-run-collect-tests-only'

CONFIG['LAB_DEPENDENCY'] = {'x86': ['cts_abi_x86']}

CONFIG['CTS_JOB_RETRIES_IN_PUBLIC'] = 1
CONFIG['CTS_QUAL_RETRIES'] = 9
CONFIG['CTS_MAX_RETRIES'] = {
        # TODO(b/183196062): Remove once the flakiness is fixed.
        'CtsHardwareTestCases': 30,
        # TODO(b/168262403): Remove once the flakiness is fixed.
        'CtsIncidentHostTestCases': 10,
        # TODO(b/181543065): Remove once the flakiness is fixed.
        'CtsWindowManagerDeviceTestCases': 10,
}

# Timeout in hours.
CONFIG['CTS_TIMEOUT_DEFAULT'] = 1.0
CONFIG['CTS_TIMEOUT'] = {
        'CtsAppSecurityHostTestCases': 2.0,
        'CtsAutoFillServiceTestCases': 2.5,  # TODO(b/134662826)
        'CtsCameraTestCases': 1.5,
        'CtsDeqpTestCases': 30.0,
        'CtsDeqpTestCases.dEQP-EGL': 2.0,
        'CtsDeqpTestCases.dEQP-GLES2': 2.0,
        'CtsDeqpTestCases.dEQP-GLES3': 6.0,
        'CtsDeqpTestCases.dEQP-GLES31': 6.0,
        'CtsDeqpTestCases.dEQP-VK': 15.0,
        'CtsFileSystemTestCases': 3.0,
        'CtsHardwareTestCases': 2.0,
        'CtsIcuTestCases': 2.0,
        'CtsKeystoreTestCases': 2.0,
        'CtsLibcoreOjTestCases': 2.0,
        'CtsMediaStressTestCases': 5.0,
        'CtsMediaTestCases': 10.0,
        'CtsMediaTestCases.video': 10.0,
        'CtsNNAPIBenchmarkTestCases': 2.0,
        'CtsPrintTestCases': 1.5,
        'CtsSecurityTestCases': 20.0,
        'CtsSecurityTestCases[instant]': 20.0,
        'CtsSensorTestCases': 2.0,
        'CtsStatsdHostTestCases': 2.0,
        'CtsVideoTestCases': 1.5,
        'CtsViewTestCases': 2.5,
        'CtsWidgetTestCases': 2.0,
        _COLLECT: 2.5,
        _PUBLIC_COLLECT: 2.5,
}

# Any test that runs as part as blocking BVT needs to be stable and fast. For
# this reason we enforce a tight timeout on these modules/jobs.
# Timeout in hours. (0.1h = 6 minutes)
CONFIG['BVT_TIMEOUT'] = 0.1
# We allow a very long runtime for qualification (2 days).
CONFIG['QUAL_TIMEOUT'] = 48

CONFIG['QUAL_BOOKMARKS'] = sorted([
        'A',  # A bookend to simplify partition algorithm.
        'CtsAccessibilityServiceTestCases',  # TODO(ihf) remove when b/121291711 fixed. This module causes problems. Put it into its own control file.
        'CtsAccessibilityServiceTestCasesz',
        'CtsCameraTestCases',  # Flaky
        'CtsCameraTestCasesz',
        'CtsDeqpTestCases',
        'CtsDeqpTestCasesz',  # Put Deqp in one control file. Long enough, fairly stable.
        'CtsFileSystemTestCases',  # Runs long enough. (3h)
        'CtsFileSystemTestCasesz',
        'CtsMediaStressTestCases',  # Put heavy  Media module in its own control file. Long enough.
        'CtsMediaTestCases',
        'CtsMediaTestCasesz',
        'CtsJvmti',
        'CtsProvider',  # TODO(b/184680306): Remove once the USB stick issue is resolved.
        'CtsSecurityHostTestCases',  # TODO(ihf): remove when passing cleanly.
        'CtsSecurityHostTestCasesz',
        'CtsSensorTestCases',  # TODO(ihf): Remove when not needing 30 retries.
        'CtsSensorTestCasesz',
        'CtsSystem',  # TODO(b/183170604): Remove when flakiness is fixed.
        'CtsViewTestCases',  # TODO(b/126741318): Fix performance regression and remove this.
        'CtsViewTestCasesz',
        'zzzzz'  # A bookend to simplify algorithm.
])

CONFIG['SMOKE'] = []

CONFIG['BVT_ARC'] = []

CONFIG['BVT_PERBUILD'] = [
        'CtsAccelerationTestCases',
        'CtsMidiTestCases',
]

CONFIG['NEEDS_POWER_CYCLE'] = [
        'CtsAppTestCases',
        'CtsSensorTestCases',
]

CONFIG['HARDWARE_DEPENDENT_MODULES'] = [
        'CtsSensorTestCases',
        'CtsCameraTestCases',
        'CtsBluetoothTestCases',
]

# The suite is divided based on the run-time hint in the *.config file.
CONFIG['VMTEST_INFO_SUITES'] = collections.OrderedDict()

# Modules that are known to download and/or push media file assets.
CONFIG['MEDIA_MODULES'] = [
        'CtsMediaTestCases',
        'CtsMediaStressTestCases',
        'CtsMediaBitstreamsTestCases',
]

CONFIG['NEEDS_PUSH_MEDIA'] = CONFIG['MEDIA_MODULES'] + [
        'CtsMediaStressTestCases.camera',
        'CtsMediaTestCases.audio',
        'CtsMediaTestCases.perf',
        'CtsMediaTestCases.video',
]

CONFIG['NEEDS_CTS_HELPERS'] = [
        'CtsPrintTestCases',
]

CONFIG['SPLIT_BY_BITS_MODULES'] = [
        'CtsDeqpTestCases',
        'CtsDeqpTestCases.dEQP-VK',
        'CtsMediaTestCases',
]

CONFIG['PUBLIC_SPLIT_BY_BITS_MODULES'] = [
        'CtsDeqpTestCases',
]

CONFIG['USE_OLD_ADB'] = [
        'CtsStatsdHostTestCases',
]

# Modules that are known to need the default apps of Chrome (eg. Files.app).
CONFIG['ENABLE_DEFAULT_APPS'] = [
        'CtsAppSecurityHostTestCases',
        'CtsContentTestCases',
]

# Run `eject` for (and only for) each device with RM=1 in lsblk output.
_EJECT_REMOVABLE_DISK_COMMAND = (
        "\'lsblk -do NAME,RM | sed -n s/1$//p | xargs -n1 eject\'")

_WIFI_CONNECT_COMMANDS = [
        # These needs to be in order.
        "'/usr/local/autotest/cros/scripts/wifi connect %s %s\' % (ssid, wifipass)",
        "'android-sh -c \\'dumpsys wifi transports -eth\\''"
]

_WIFI_CONNECT_COMMANDS_V2 = [
        # These needs to be in order.
        "'adb shell cmd wifi add-network %s %s %s' % (pipes.quote(ssid), 'open' if wifipass == '' else 'wpa', pipes.quote(wifipass))",
        "'adb shell cmd wifi connect-network %s' % pipes.quote(ssid)",
        "'adb shell dumpsys wifi transports -eth'",
]

_DISPLAY_REFRESH_COMMANDS = [
        "'sleep 20'",  # Wait for the intent helper mojo connection established
        "'android-sh -c \\'am start -a android.intent.action.VIEW -d https://webglsamples.org/electricflower/electricflower.html\\''"
]

# Preconditions applicable to public and internal tests.
CONFIG['PRECONDITION'] = {
        'CtsCameraTestCases.NativeCameraDeviceTest': _DISPLAY_REFRESH_COMMANDS,
}

CONFIG['LOGIN_PRECONDITION'] = {
        'CtsAppSecurityHostTestCases': [_EJECT_REMOVABLE_DISK_COMMAND],
        'CtsJobSchedulerTestCases': [_EJECT_REMOVABLE_DISK_COMMAND],
        'CtsMediaTestCases': [_EJECT_REMOVABLE_DISK_COMMAND],
        'CtsOsTestCases': [_EJECT_REMOVABLE_DISK_COMMAND],
        'CtsProviderTestCases': [_EJECT_REMOVABLE_DISK_COMMAND],
}

# Preconditions applicable to public tests.
CONFIG['PUBLIC_PRECONDITION'] = {
        'CtsCameraTestCases.NativeCameraDeviceTest': _DISPLAY_REFRESH_COMMANDS,
        'CtsHostsideNetworkTests': _WIFI_CONNECT_COMMANDS,
        'CtsLibcoreTestCases': _WIFI_CONNECT_COMMANDS,
        'CtsNetApi23TestCases': _WIFI_CONNECT_COMMANDS,
        'CtsNetTestCases': _WIFI_CONNECT_COMMANDS,
        'CtsJobSchedulerTestCases': _WIFI_CONNECT_COMMANDS,
        'CtsUsageStatsTestCases': _WIFI_CONNECT_COMMANDS,
        'CtsStatsdHostTestCases': _WIFI_CONNECT_COMMANDS,
        'CtsWifiTestCases': _WIFI_CONNECT_COMMANDS,
}

CONFIG['PUBLIC_DEPENDENCIES'] = {
        'CtsCameraTestCases': ['lighting'],
        'CtsMediaTestCases': ['noloopback'],
}

CONFIG['PUBLIC_OVERRIDE_TEST_PRIORITY'] = {
        _PUBLIC_COLLECT: 70,
        'CtsDeqpTestCases': 70,
        'CtsDeqpTestCases': 70,
        'CtsMediaTestCases': 70,
        'CtsMediaStressTestCases': 70,
        'CtsSecurityTestCases': 70,
        'CtsCameraTestCases': 70
}

# This information is changed based on regular analysis of the failure rate on
# partner moblabs.
CONFIG['PUBLIC_MODULE_RETRY_COUNT'] = {
        # TODO(b/183196062): Remove once the flakiness is fixed.
        'CtsHardwareTestCases': 30,
        # TODO(b/168262403): Remove once the flakiness is fixed.
        'CtsIncidentHostTestCases': 10,
        # TODO(b/181543065): Remove once the flakiness is fixed.
        'CtsWindowManagerDeviceTestCases': 10,
}

# This information is changed based on regular analysis of the job run time on
# partner moblabs.

CONFIG['OVERRIDE_TEST_LENGTH'] = {
        'CtsDeqpTestCases': 4,  # LONG
        'CtsMediaTestCases': 4,
        'CtsMediaStressTestCases': 4,
        'CtsSecurityTestCases': 4,
        'CtsCameraTestCases': 4,
        # Even though collect tests doesn't run very long, it must be the very first
        # job executed inside of the suite. Hence it is the only 'LENGTHY' test.
        _COLLECT: 5,  # LENGTHY
        _PUBLIC_COLLECT: 5,  # LENGTHY
}

# Enabling --logcat-on-failure can extend total run time significantly if
# individual tests finish in the order of 10ms or less (b/118836700). Specify
# modules here to not enable the flag.
CONFIG['DISABLE_LOGCAT_ON_FAILURE'] = set([
        'all',
        'CtsDeqpTestCases',
        'CtsDeqpTestCases.dEQP-EGL',
        'CtsDeqpTestCases.dEQP-GLES2',
        'CtsDeqpTestCases.dEQP-GLES3',
        'CtsDeqpTestCases.dEQP-GLES31',
        'CtsDeqpTestCases.dEQP-VK',
        'CtsLibcoreTestCases',
])

# This list of modules will be used for reduced set of testing for build
# variant process. Suites: cts_hardware & arc-cts-hardware.
CONFIG['HARDWARE_MODULES'] = [
        'CtsPerfettoTestCases',
        'CtsSustainedPerformanceHostTestCases',
        'CtsCameraTestCases',
        'CtsViewTestCases',
        'CtsMediaTestCases',
        'CtsNativeMediaAAudioTestCases',
        'CtsNetTestCases',
        'CtsWifiTestCases',
        'CtsUsageStatsTestCases',
        'CtsSensorTestCases',
]

SUITE_ARC_CTS_R = ['suite:arc-cts-r']
R_QUAL_SUITES = ['suite:arc-cts-r', 'suite:arc-cts-qual']
R_REGRESSION_SUITES = ['suite:arc-cts-r', 'suite:arc-cts']
R_REGRESSION_AND_QUAL_SUITES = CONFIG['QUAL_SUITE_NAMES'] + R_REGRESSION_SUITES

CONFIG['EXTRA_MODULES'] = {
    'CtsCameraTestCases': {
        'CtsCameraTestCases.NativeCameraDeviceTest': R_REGRESSION_AND_QUAL_SUITES,
    },
    'CtsDeqpTestCases': {
        'CtsDeqpTestCases.dEQP-EGL': R_QUAL_SUITES,
        'CtsDeqpTestCases.dEQP-GLES2': R_QUAL_SUITES,
        'CtsDeqpTestCases.dEQP-GLES3': R_QUAL_SUITES,
        'CtsDeqpTestCases.dEQP-GLES31': R_QUAL_SUITES,
        'CtsDeqpTestCases.dEQP-VK': R_QUAL_SUITES,
    },
    'CtsMediaTestCases': {
        'CtsMediaTestCases.audio': R_REGRESSION_AND_QUAL_SUITES,
        'CtsMediaTestCases.perf': R_REGRESSION_AND_QUAL_SUITES,
        'CtsMediaTestCases.video': R_REGRESSION_AND_QUAL_SUITES,
    },
    'CtsWindowManagerDeviceTestCases': {
        'CtsWindowManager.A': R_REGRESSION_SUITES,
        'CtsWindowManager.C': R_REGRESSION_SUITES,
        'CtsWindowManager.D': R_REGRESSION_SUITES,
        'CtsWindowManager.F': R_REGRESSION_SUITES,
        'CtsWindowManager.L': R_REGRESSION_SUITES,
        'CtsWindowManager.M': R_REGRESSION_SUITES,
        'CtsWindowManager.Override': R_REGRESSION_SUITES,
        'CtsWindowManager.P': R_REGRESSION_SUITES,
        'CtsWindowManager.R': R_REGRESSION_SUITES,
        'CtsWindowManager.S': R_REGRESSION_SUITES,
        'CtsWindowManager.T': R_REGRESSION_SUITES,
        'CtsWindowManager.Window': R_REGRESSION_SUITES,
        'CtsWindowManager.intent': R_REGRESSION_SUITES,
        'CtsWindowManager.lifecycle': R_REGRESSION_SUITES,
    },
}

# In addition to EXTRA_MODULES, these modules do require separate control files
# for internal and moblab.
CONFIG['HARDWAREONLY_EXTRA_MODULES'] = {
        'CtsAppTestCases': {
                'CtsAppTestCases.feature': [],
        },
        'CtsCameraTestCases': {
                'CtsCameraTestCases.NativeCameraDeviceTest': [],
        },
        'CtsDeqpTestCases': {
                'CtsDeqpTestCases.dEQP-GLES3.functional.prerequisite': [],
        },
        'CtsMediaStressTestCases': {
                'CtsMediaStressTestCases.camera': [],
        },
        'CtsPermissionTestCases': {
                'CtsPermissionTestCases.camera': [],
        },
}

# Moblab optionally can reshard modules, this was originally used
# for deqp but it is no longer required for that module.  Retaining
# feature in case future slower module needs to be sharded.
CONFIG['PUBLIC_EXTRA_MODULES'] = {
        'CtsCameraTestCases': {
             'CtsCameraTestCases.NativeCameraDeviceTest':
                [CONFIG['MOBLAB_SUITE_NAME']],
        },
}

CONFIG['EXTRA_SUBMODULE_OVERRIDE'] = {
}

CONFIG['EXTRA_COMMANDLINE'] = {
        'CtsAppTestCases.feature': [
                '--module', 'CtsAppTestCases', '--test',
                'android.app.cts.SystemFeaturesTest'
        ],
        'CtsCameraTestCases.NativeCameraDeviceTest': [
                '--include-filter',
                'CtsCameraTestCases android.hardware.camera2.cts.NativeCameraDeviceTest',
                '--include-filter',
                'CtsCameraTestCases[instant] android.hardware.camera2.cts.NativeCameraDeviceTest',
                '--include-filter',
                'CtsCameraTestCases android.hardware.camera2.cts.RecordingTest#testVideoPreviewSurfaceSharing[1]',
                '--include-filter',
                'CtsCameraTestCases[instant] android.hardware.camera2.cts.RecordingTest#testVideoPreviewSurfaceSharing[1]',
        ],
        'CtsDeqpTestCases.dEQP-EGL': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-EGL.*'
        ],
        'CtsDeqpTestCases.dEQP-GLES2': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-GLES2.*'
        ],
        'CtsDeqpTestCases.dEQP-GLES3': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-GLES3.*'
        ],
        'CtsDeqpTestCases.dEQP-GLES3.functional.prerequisite': [
                '--module', 'CtsDeqpTestCases', '--test',
                'dEQP-GLES3.functional.prerequisite#*'
        ],
        'CtsDeqpTestCases.dEQP-GLES31': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-GLES31.*'
        ],
        'CtsDeqpTestCases.dEQP-VK': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.api': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.api.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.binding_model': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.binding_model.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.clipping': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.clipping.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.compute': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.compute.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.device_group': [
                '--include-filter',
                'CtsDeqpTestCases',
                '--module',
                'CtsDeqpTestCases',
                '--test',
                'dEQP-VK.device_group*'  # Not ending on .* like most others!
        ],
        'CtsDeqpTestCases.dEQP-VK.draw': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.draw.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.dynamic_state': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.dynamic_state.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.fragment_operations': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.fragment_operations.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.geometry': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.geometry.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.glsl': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.glsl.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.image': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.image.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.info': [
                '--include-filter',
                'CtsDeqpTestCases',
                '--module',
                'CtsDeqpTestCases',
                '--test',
                'dEQP-VK.info*'  # Not ending on .* like most others!
        ],
        'CtsDeqpTestCases.dEQP-VK.memory': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.memory.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.multiview': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.multiview.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.pipeline': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.pipeline.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.protected_memory': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.protected_memory.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.query_pool': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.query_pool.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.rasterization': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.rasterization.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.renderpass': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.renderpass.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.renderpass2': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.renderpass2.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.robustness': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.robustness.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.sparse_resources': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.sparse_resources.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.spirv_assembly': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.spirv_assembly.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.ssbo': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.ssbo.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.*'
        ],
        # Splitting VK.subgroups to smaller pieces to workaround b/138622686.
        # TODO(kinaba,haddowk): remove them once the root cause is fixed, or
        # reconsider the sharding strategy.
        'CtsDeqpTestCases.dEQP-VK.subgroups.b': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.b*'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups.s': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.s*'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups.vote': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.vote#*'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups.arithmetic': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.arithmetic#*'
        ],
        # TODO(haddowk,kinaba): Hack for b/138622686. Clean up later.
        'CtsDeqpTestCases.dEQP-VK.subgroups.arithmetic.32': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.arithmetic#*',
                '--abi', 'x86'
        ],
        # TODO(haddowk,kinaba): Hack for b/138622686. Clean up later.
        'CtsDeqpTestCases.dEQP-VK.subgroups.arithmetic.64': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.arithmetic#*',
                '--abi', 'x86_64'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups.clustered': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.clustered#*'
        ],
        'CtsDeqpTestCases.dEQP-VK.subgroups.quad': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.subgroups.quad#*'
        ],
        'CtsDeqpTestCases.dEQP-VK.synchronization': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.synchronization.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.tessellation': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.tessellation.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.texture': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.texture.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.ubo': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.ubo.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.wsi': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.wsi.*'
        ],
        'CtsDeqpTestCases.dEQP-VK.ycbcr': [
                '--include-filter', 'CtsDeqpTestCases', '--module',
                'CtsDeqpTestCases', '--test', 'dEQP-VK.ycbcr.*'
        ],
        'CtsMediaStressTestCases.camera': [
                '--module',
                'CtsMediaStressTestCases',
                '--test',
                'android.mediastress.cts.MediaRecorderStressTest',
        ],
        'CtsMediaTestCases.audio': [
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioAttributesTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioEffectTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioAttributesTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioEffectTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioFocusTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioFormatTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioManagerTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioMetadataTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioNativeTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioPlayRoutingNative',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioPlaybackCaptureTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioPlaybackConfigurationTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioPreProcessingTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioPresentationTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioRecordAppOpTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioRecordRoutingNative',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioRecordTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioRecord_BufferSizeTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioRecordingConfigurationTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioSystemTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioSystemUsageTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioTrackLatencyTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioTrackOffloadTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioTrackSurroundTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioTrackTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AudioTrack_ListenerTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.SoundPoolAacTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.SoundPoolHapticTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.SoundPoolMidiTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.SoundPoolOggTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.VolumeShaperTest',
        ],
        'CtsMediaTestCases.perf': [
                '--include-filter',
                'CtsMediaTestCases android.media.cts.VideoDecoderPerfTest',
        ],
        'CtsMediaTestCases.video': [
                '--include-filter',
                'CtsMediaTestCases android.media.cts.AdaptivePlaybackTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.DecodeAccuracyTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.DecodeEditEncodeTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.DecoderConformanceTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.EncodeDecodeTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.ExtractDecodeEditEncodeMuxTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.MediaCodecPlayerTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.MediaCodecPlayerTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.MediaDrmClearkeyTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.MediaRecorderTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.MediaSynctest#testPlayVideo',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.VideoCodecTest',
                '--include-filter',
                'CtsMediaTestCases android.media.cts.VideoEncoderTest',
        ],
        'CtsPermissionTestCases.camera': [
                '--include-filter',
                'CtsPermissionTestCases android.permission.cts.CameraPermissionTest',
                '--include-filter',
                'CtsPermissionTestCases android.permission.cts.Camera2PermissionTest',
        ],
        'CtsWindowManager.A': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityManagerGetConfigTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityMetricsLoggerTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityTaskAffinityTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityTransitionTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityViewTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ActivityVisibilityTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AddWindowAsUserTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AlertWindowsAppOpsTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AlertWindowsImportanceTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AlertWindowsTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AmProfileTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AmStartOptionsTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AnrTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AppConfigurationTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AspectRatioTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.AssistantStackTests',
        ],
        'CtsWindowManager.C': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.CloseOnOutsideTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ConfigChangeTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.CrossAppDragAndDropTests',
        ],
        'CtsWindowManager.D': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DecorInsetTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DeprecatedTargetSdkTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DialogFrameTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DisplayCutoutTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DisplaySizeTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DisplayTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DragDropTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.DreamManagerServiceTests',
        ],
        'CtsWindowManager.F': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ForceRelayoutTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.FreeformWindowingModeTests',
        ],
        'CtsWindowManager.L': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.LayoutTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.LocationInWindowTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.LocationOnScreenTests',
        ],
        'CtsWindowManager.M': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ManifestLayoutTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MinimalPostProcessingTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayActivityLaunchTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayClientTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayKeyguardTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayLockedKeyguardTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayPolicyTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplayPrivateDisplayTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplaySecurityTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.MultiDisplaySystemDecorationTests',
        ],
        'CtsWindowManager.Override': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.OverrideConfigTests',
        ],
        'CtsWindowManager.P': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.PinnedStackTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.PrereleaseSdkTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.PresentationTest',
        ],
        'CtsWindowManager.R': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ReplaceWindowTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.RobustnessTests',
        ],
        'CtsWindowManager.S': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SplashscreenTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SplitScreenTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.StartActivityAsUserTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.StartActivityTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SurfaceControlTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SurfaceControlViewHostTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SurfaceViewSurfaceValidatorTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.SurfaceViewTest',
        ],
        'CtsWindowManager.T': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.ToastWindowTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.TransitionSelectionTests',
        ],
        'CtsWindowManager.Window': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowContextPolicyTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowContextTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowFocusTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInputTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsAnimationCallbackTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsAnimationControllerTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsAnimationImeTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsAnimationSynchronicityTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsAnimationTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsControllerTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsLayoutTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsPolicyTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowInsetsTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowManager_BadTokenExceptionTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowManager_LayoutParamsTest',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowMetricsTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.WindowTest',
        ],
        'CtsWindowManager.intent': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.intent.IntentGenerationTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.intent.IntentTests',
        ],
        'CtsWindowManager.lifecycle': [
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecycleFreeformTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecycleKeyguardTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecyclePipTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecycleSplitScreenTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecycleTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityLifecycleTopResumedStateTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityStarterTests',
                '--include-filter',
                'CtsWindowManagerDeviceTestCases android.server.wm.lifecycle.ActivityTests',
        ],
}

CONFIG['EXTRA_ATTRIBUTES'] = {}

CONFIG['EXTRA_ARTIFACTS'] = {}
CONFIG['PREREQUISITES'] = {}

CONFIG['USE_JDK9'] = True

from generate_controlfiles_common import main

if __name__ == '__main__':
    main(CONFIG)
