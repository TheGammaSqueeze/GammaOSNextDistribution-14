
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'firmware/BaseECKeyboard',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Bmpblk',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv4', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CgptStress',
            suites = ['faft_normal', 'faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ChipFwUpdate',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ClearTPMOwnerAndReset',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CompareChipFwToShellBall',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ConsecutiveBoot',
            suites = ['faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ConsecutiveBootPowerButton',
            suites = ['faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ConsecutiveLidSwitch',
            suites = ['faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptBothFwBodyAB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptBothFwSigAB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptBothKernelAB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptFwBodyA',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptFwBodyB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptFwSigA',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptFwSigB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptKernelA',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptKernelB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/CorruptRecoveryCache',
            suites = ['faft_bios', 'faft_bios_ro_qual', 'faft_lv3'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50BID',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50CCDFirmwareUpdate',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50CCDServoCap',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50CCDUartStress',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50ConsoleCommands',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50DeepSleepStress',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50DeferredECReset',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50DevMode',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50DeviceState',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50ECReset',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50FactoryResetVC',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50GetName',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50InvalidateRW',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50OpenWhileAPOff',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50PartialBoardId',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50Password',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50PinWeaverServer',
            suites = ['experimental', 'faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50RMAOpen',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50RddG3',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50RejectUpdate',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50SetBoardId',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50ShortECC',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50Testlab',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50TpmManufactured',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50TpmMode',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50U2fCommands',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50U2fPowerwash',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50USB',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50Unlock',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50Update',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50UpdateScriptStress',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50VirtualNVRam',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.firmware_Cr50VirtualNVRam.firmware_Cr50VirtualNVRam',
        ),
        test_common.define_test(
            'firmware/Cr50VirtualNVRamServer',
            suites = ['faft_cr50_prepvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50WPG3',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50WilcoEcrst',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt', 'faft_wilco'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Cr50WilcoRmaFactoryMode',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt', 'faft_wilco'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/DevBootUSB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv2'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/DevDefaultBoot',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv2'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/DevMode',
            suites = ['bvt-faft', 'faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/DevModeStress',
            suites = ['faft_dev', 'faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/DevScreenTimeout',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv4'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECAdc',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECBattery',
            suites = ['bvt-faft', 'faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECBootTime',
            suites = ['bvt-faft', 'faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECCbiEeprom',
            suites = ['faft_ec', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECCharging',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECChargingState',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECHash',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECKeyboard',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECKeyboardReboot',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECLidShutdown',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECLidSwitch',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECPeci',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECPowerButton',
            suites = ['bvt-faft', 'faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECPowerG3',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECRestoreFW',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECSharedMem',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECThermal',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECUpdateId',
            suites = ['faft_ec', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECUsbPorts',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECWakeSource',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/ECWatchdog',
            suites = ['faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/EmmcWriteLoad',
            suites = ['faft_stress'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/EventLog',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv4', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FAFTSetup',
            suites = ['bvt-faft', 'faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lab', 'faft_lv2', 'faft_normal', 'faft_setup'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FMap',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FWMPDisableCCD',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FWtries',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FWupdate',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FWupdateThenSleep',
            suites = ['faft_bios_ro_qual', 'faft_bios_rw_qual'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FWupdateWP',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FingerprintSigner',
            suites = ['fingerprint'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FlashServoKeyboardMap',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FwScreenCloseLid',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv4'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/FwScreenPressPower',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv4'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/IntegratedU2F',
            suites = ['faft_cr50_experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/InvalidUSB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv4', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/LegacyRecovery',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_tot', 'faft_lv4', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/LockedME',
            suites = ['faft_bios_ro_qual', 'faft_bios_rw_qual'],
            main_package = 'autotest_lib.client.site_tests.firmware_LockedME.firmware_LockedME',
        ),
        test_common.define_test(
            'firmware/MenuModeTransition',
            suites = ['bvt-faft', 'faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/Mosys',
            suites = ['bvt-faft', 'faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDConnect',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDDataSwap',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDPowerSwap',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDProtocol',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDResetHard',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDResetSoft',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDTrySrc',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/PDVbusRequest',
            suites = ['faft_pd'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/RecoveryButton',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_tot', 'faft_lv2', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/RecoveryCacheBootKeys',
            suites = ['faft_bios', 'faft_bios_ro_qual', 'faft_lv2'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/RollbackFirmware',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/RollbackKernel',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/RomSize',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.firmware_RomSize.firmware_RomSize',
        ),
        test_common.define_test(
            'firmware/SelfSignedBoot',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv2'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/SetSerialNumber',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/SoftwareSync',
            suites = ['faft_cr50_prepvt', 'faft_cr50_pvt', 'faft_cr50_tot', 'faft_ec', 'faft_ec3po', 'faft_ec_fw_qual', 'faft_ec_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/SysfsVPD',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv2', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TPMExtend',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv2', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TPMKernelVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv3', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TPMNotCorruptedDevMode',
            suites = ['faft_bios', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_lv2'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TPMVersionCheck',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TryFwB',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TypeCCharging',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/TypeCProbeUSB3',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdateFirmwareDataKeyVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv5', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdateFirmwareVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv5', 'faft_normal', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdateKernelDataKeyVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv5'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdateKernelSubkeyVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv5'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdateKernelVersion',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_dev', 'faft_lv5', 'faft_smoke'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UpdaterModes',
            suites = ['faft_bios', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv5'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/UserRequestRecovery',
            suites = ['bvt-faft', 'faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv2', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/VbootCrypto',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/WilcoDiagnosticsMode',
            suites = ['faft_bios', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_wilco'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/WriteProtect',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv1', 'faft_normal'],
            main_package = '',
        ),
        test_common.define_test(
            'firmware/WriteProtectFunc',
            suites = ['faft_bios', 'faft_bios_ec3po', 'faft_bios_ro_qual', 'faft_bios_rw_qual', 'faft_bios_tot', 'faft_lv2', 'faft_normal'],
            main_package = '',
        )
    ]
