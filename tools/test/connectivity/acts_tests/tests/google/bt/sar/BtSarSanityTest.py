#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import os
import re
import time

from acts import asserts
import acts_contrib.test_utils.bt.bt_test_utils as bt_utils
from acts_contrib.test_utils.bt.BtSarBaseTest import BtSarBaseTest


class BtSarSanityTest(BtSarBaseTest):
    """Class to run sanity checks on BT SAR mechanisms.

    This class defines sanity test cases on BT SAR. The tests include
    a software state sanity check and a software power sanity check.
    """

    def setup_class(self):
        super().setup_class()

        self.reg_domain_dict = {
            'US': 'bluetooth_power_limits_US.csv',
            'EU': 'bluetooth_power_limits_EU.csv',
            'JP': 'bluetooth_power_limits_JP.csv'
        }

        #Backup BT SAR files on the device
        for key in self.reg_domain_dict.keys():
            reg_file_path = os.path.join(
                os.path.dirname(self.power_file_paths[0]),
                self.reg_domain_dict[key])
            self.dut.adb.shell('cp {} {}.backup'.format(
                reg_file_path, reg_file_path))

        self.log.info('Regulatory files backed up')

    def setup_test(self):

        #Reset SAR test result to 0 before every test
        self.sar_test_result.metric_value = 0

        # Starting BT on the master
        self.dut.droid.bluetoothFactoryReset()
        bt_utils.enable_bluetooth(self.dut.droid, self.dut.ed)

    def teardown_class(self):
        for key in self.reg_domain_dict.keys():
            reg_file_path = os.path.join(
                os.path.dirname(self.power_file_paths[0]),
                self.reg_domain_dict[key])
            self.dut.adb.shell('mv {}.backup {}'.format(
                reg_file_path, reg_file_path))

        self.log.info('Regulatory files restored')

        self.dut.reboot()  #TODO: make this better
        super().teardown_class()

    def test_bt_sar_sanity_check_state(self):
        """ Test for BT SAR State Sanity

        BT SAR Software Sanity Test to ensure that the correct signal state
        gets propagated to the firmware. This is done by comparing expected
        device state with that read from device's logcat
        """
        #Iterating through the BT SAR scenarios
        for scenario in range(0, self.bt_sar_df.shape[0]):
            # Reading BT SAR table row into dict
            read_scenario = self.bt_sar_df.loc[scenario].to_dict()

            start_time = self.dut.adb.shell('date +%s.%m')
            time.sleep(1)

            #Setting SAR state to the read BT SAR row
            enforced_state = self.set_sar_state(self.dut, read_scenario,
                                                self.country_code)

            #Reading device state from logcat after forcing SAR State
            device_state = self.get_current_device_state(self.dut, start_time)

            #Comparing read device state to expected device state
            for key in enforced_state.keys():
                key_regex = r'{}:\s*(\d)'.format(key)
                try:
                    propagated_value = int(
                        re.findall(key_regex, device_state)[0])
                except IndexError:
                    propagated_value = 'NA'

                if enforced_state[key] == propagated_value:
                    self.sar_test_result.metric_value = 1
                    self.log.info(
                        'scenario: {}, state : {}, forced_value: {}, value:{}'.
                        format(scenario, key, enforced_state[key],
                               propagated_value))
                else:
                    self.log.error(
                        'scenario:{}, state : {}, forced_value: {}, value:{}'.
                        format(scenario, key, enforced_state[key],
                               propagated_value))

    def test_bt_sar_sanity_check_power(self):
        """ Test for BT SAR Power Cap Sanity

        BT SAR Power Cap Sanity Test to ensure that the correct SAR power
        cap corresponding to the forced SAR state gets propagated to the
        firmware. This is done by comparing expected power cap read from
        the BT SAR file to the power cap read from logcat
        """

        power_cap_error = self.sweep_power_cap()

        if power_cap_error:
            asserts.fail("Power Caps didn't match powers in the SAR table")
        else:
            self.sar_test_result.metric_value = 1
            asserts.explicit_pass('Power Caps were set according to the table')

    def test_bt_sar_sanity_country_code(self):
        """ Test for BT SAR Country Code Sanity

        BT SAR Country Code Sanity Test to ensure that the correct SAR
        regulatory domain corresponding to the forced SAR country code gets
        propagated to the firmware. This is done by comparing forced regulatory
        domain to regulatory domain read from logcat.
        """

        error_flag = 0
        for country_code_tuple in self.REG_DOMAIN_DICT.keys():
            for country_code in country_code_tuple:
                start_time = self.dut.adb.shell('date +%s.%m')

                time.sleep(1)
                #Force country code using adb command
                self.set_country_code(self.dut, country_code)

                #Read regulatory code from logcat
                set_regulatory_domain = self.get_country_code(
                    self.dut, start_time)

                if set_regulatory_domain != self.REG_DOMAIN_DICT[country_code_tuple]:
                    error_flag = 1
                    self.log.error(
                        'Country Code: {} set to regulatory domain: {}'.format(
                            country_code, set_regulatory_domain))
                else:
                    self.log.info(
                        'Country Code: {} set to regulatory domain: {}'.format(
                            country_code, set_regulatory_domain))

        if error_flag:
            asserts.fail(
                'Regulatory domains not set according to country code')
        else:
            self.sar_test_result.metric_value = 1
            asserts.explicit_pass(
                'Regulatory domains set according to country code')

    def test_bt_sar_sanity_reg_domain(self):
        """ Test for BT SAR Regulatory Domain Sanity

        BT SAR Regulatory Domain Sanity Test to ensure that the correct
        SAR regulatory domain TX powers get propagated to the firmware.
        This is done by measuring the TX power for different
        regulatory domain files
        """

        reg_domain_test_error_flag = True
        reg_file_phone_path = os.path.dirname(self.sar_file_path)

        #For different reg domain, sweep the sar table
        for cc, reg_domain in self.REG_DOMAIN_DICT.items():
            self.country_code = cc[0]
            for file in self.custom_files:
                if 'bluetooth_power_limits_{}.csv'.format(reg_domain) in file:
                    custom_reg_file = file
                    reg_file_name = os.path.join(
                        reg_file_phone_path,
                        'bluetooth_power_limits_{}.csv'.format(reg_domain))
                    break
            else:
                self.log.error(
                    'Regulatory file for {} missing'.format(reg_domain))

            self.push_table(self.dut, custom_reg_file, reg_file_name)

            start_time = self.dut.adb.shell('date +%s.%m')
            self.set_country_code(self.dut, cc[0])
            # Read regulatory code from logcat
            read_reg_domain = self.get_country_code(self.dut, start_time)
            self.log.info(
                'Regulatory domain set to {}'.format(read_reg_domain))
            self.bt_sar_df = self.read_sar_table(self.dut, custom_reg_file)

            reg_domain_error_flag = self.sweep_power_cap()

            if not reg_domain_error_flag:
                self.log.info(
                    'Regulatory Domain Sanity Test for {} passed'.format(
                        reg_domain))

            reg_domain_test_error_flag &= reg_domain_error_flag

        if reg_domain_test_error_flag:
            asserts.fail('Regulatory domain sanity tests failed')
        else:
            self.sar_test_result.metric_value = 1
            asserts.explicit_pass('Regulatory domain sanity tests passed')
