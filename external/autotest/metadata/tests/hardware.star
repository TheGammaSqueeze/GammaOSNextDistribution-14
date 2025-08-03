
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'hardware/Backlight',
            suites = ['hwqual'],
            main_package = 'autotest_lib.client.site_tests.hardware_Backlight.hardware_Backlight',
        ),
        test_common.define_test(
            'hardware/Badblocks',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Badblocks.hardware_Badblocks',
        ),
        test_common.define_test(
            'hardware/DiskFirmwareUpgrade_Client',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_DiskFirmwareUpgrade.hardware_DiskFirmwareUpgrade',
        ),
        test_common.define_test(
            'hardware/DiskFirmwareUpgrade_Server',
            suites = ['experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'hardware/DiskSize',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_DiskSize.hardware_DiskSize',
        ),
        test_common.define_test(
            'hardware/EC',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.hardware_EC.hardware_EC',
        ),
        test_common.define_test(
            'hardware/GPIOSwitches',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_GPIOSwitches.hardware_GPIOSwitches',
        ),
        test_common.define_test(
            'hardware/GPS',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_GPS.hardware_GPS',
        ),
        test_common.define_test(
            'hardware/GobiGPS',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_GobiGPS.hardware_GobiGPS',
        ),
        test_common.define_test(
            'hardware/Keyboard',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Keyboard.hardware_Keyboard',
        ),
        test_common.define_test(
            'hardware/LightSensor',
            suites = ['hwqual'],
            main_package = 'autotest_lib.client.site_tests.hardware_LightSensor.hardware_LightSensor',
        ),
        test_common.define_test(
            'hardware/MemoryLatency',
            suites = ['hwqual', 'kernel_daily_benchmarks'],
            main_package = 'autotest_lib.client.site_tests.hardware_MemoryLatency.hardware_MemoryLatency',
        ),
        test_common.define_test(
            'hardware/MemoryThroughput',
            suites = ['hwqual', 'kernel_daily_benchmarks'],
            main_package = 'autotest_lib.client.site_tests.hardware_MemoryThroughput.hardware_MemoryThroughput',
        ),
        test_common.define_test(
            'hardware/MemoryZRAMThroughput',
            suites = ['crosbolt_perf_weekly'],
            main_package = 'autotest_lib.client.site_tests.hardware_MemoryZRAMThroughput.hardware_MemoryZRAMThroughput',
        ),
        test_common.define_test(
            'hardware/Memtester',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Memtester.hardware_Memtester',
        ),
        test_common.define_test(
            'hardware/MultiReader',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_MultiReader.hardware_MultiReader',
        ),
        test_common.define_test(
            'hardware/MultiReaderPowerConsumption',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_MultiReaderPowerConsumption.hardware_MultiReaderPowerConsumption',
        ),
        test_common.define_test(
            'hardware/ProbeComponents',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_ProbeComponents.hardware_ProbeComponents',
        ),
        test_common.define_test(
            'hardware/RamFio',
            suites = ['bvt-perbuild', 'crosbolt_perf_weekly'],
            main_package = 'autotest_lib.client.site_tests.hardware_RamFio.hardware_RamFio',
        ),
        test_common.define_test(
            'hardware/RealtekCardReader',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_RealtekCardReader.hardware_RealtekCardReader',
        ),
        test_common.define_test(
            'hardware/Resolution',
            suites = ['hwqual'],
            main_package = 'autotest_lib.client.site_tests.hardware_Resolution.hardware_Resolution',
        ),
        test_common.define_test(
            'hardware/SAT',
            suites = ['kernel_per-build_regression'],
            main_package = 'autotest_lib.client.site_tests.hardware_SAT.hardware_SAT',
        ),
        test_common.define_test(
            'hardware/Smartctl',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Smartctl.hardware_Smartctl',
        ),
        test_common.define_test(
            'hardware/SsdDetection',
            suites = ['hwqual'],
            main_package = 'autotest_lib.client.site_tests.hardware_SsdDetection.hardware_SsdDetection',
        ),
        test_common.define_test(
            'hardware/StorageFio',
            suites = ['crosbolt_perf_weekly', 'kernel_daily_benchmarks'],
            main_package = 'autotest_lib.client.site_tests.hardware_StorageFio.hardware_StorageFio',
        ),
        test_common.define_test(
            'hardware/StorageFioOther.test',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_StorageFioOther.hardware_StorageFioOther',
        ),
        test_common.define_test(
            'hardware/StorageQualCheckSetup',
            suites = ['check_setup_storage_qual'],
            main_package = '',
        ),
        test_common.define_test(
            'hardware/StorageStress',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'hardware/StorageTrim',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_StorageTrim.hardware_StorageTrim',
        ),
        test_common.define_test(
            'hardware/StorageWearoutDetect',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.hardware_StorageWearoutDetect.hardware_StorageWearoutDetect',
        ),
        test_common.define_test(
            'hardware/TPMCheck',
            suites = ['kernel_per-build_regression'],
            main_package = 'autotest_lib.client.site_tests.hardware_TPMCheck.hardware_TPMCheck',
        ),
        test_common.define_test(
            'hardware/TPMLoadKey',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TPMLoadKey.hardware_TPMLoadKey',
        ),
        test_common.define_test(
            'hardware/TPMTakeOwnership',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TPMTakeOwnership.hardware_TPMTakeOwnership',
        ),
        test_common.define_test(
            'hardware/TPMtspi',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TPMtspi.hardware_TPMtspi',
        ),
        test_common.define_test(
            'hardware/TPMttci',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TPMttci.hardware_TPMttci',
        ),
        test_common.define_test(
            'hardware/Thermal',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Thermal.hardware_Thermal',
        ),
        test_common.define_test(
            'hardware/TouchScreenPowerCycles',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TouchScreenPowerCycles.hardware_TouchScreenPowerCycles',
        ),
        test_common.define_test(
            'hardware/TrimIntegrity',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_TrimIntegrity.hardware_TrimIntegrity',
        ),
        test_common.define_test(
            'hardware/UnsafeMemory',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_UnsafeMemory.hardware_UnsafeMemory',
        ),
        test_common.define_test(
            'hardware/Usb30Throughput',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_Usb30Throughput.hardware_Usb30Throughput',
        ),
        test_common.define_test(
            'hardware/UsbBasicFileOperations',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_UsbBasicFileOperations.hardware_UsbBasicFileOperations',
        ),
        test_common.define_test(
            'hardware/UsbMount',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.hardware_UsbMount.hardware_UsbMount',
        )
    ]
