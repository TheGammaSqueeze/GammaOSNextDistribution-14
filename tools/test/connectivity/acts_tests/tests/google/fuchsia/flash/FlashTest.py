#!/usr/bin/env python3
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""
Script for to flash Fuchsia devices and reports the DUT's version of Fuchsia in
the Sponge test result properties. Uses the built in flashing tool for
fuchsia_devices.
"""
from acts import asserts
from acts import signals
from acts.base_test import BaseTestClass
from acts.utils import get_device

MAX_FLASH_ATTEMPTS = 3


class FlashTest(BaseTestClass):

    def setup_class(self):
        super().setup_class()
        self.failed_to_get_version = False

    def teardown_class(self):
        # Verify that FlashTest successfully reported the DUT version. This is
        # working around a flaw in ACTS where signals.TestAbortAll does not
        # report any errors.
        #
        # TODO(http://b/253515812): This has been fixed in Mobly already. Remove
        # teardown_class and change "TestError" to "abort_all" in
        # test_flash_devices once we move to Mobly.
        if self.failed_to_get_version:
            asserts.abort_all('Failed to get DUT version')

        return super().teardown_class()

    def test_flash_devices(self):
        for device in self.fuchsia_devices:
            flash_counter = 0
            while True:
                try:
                    device.reboot(reboot_type='flash',
                                  use_ssh=True,
                                  unreachable_timeout=120,
                                  ping_timeout=120)
                    self.log.info(f'{device.orig_ip} has been flashed.')
                    break
                except Exception as err:
                    self.log.error(
                        f'Failed to flash {device.orig_ip} with error:\n{err}')

                    if not device.device_pdu_config:
                        asserts.abort_all(
                            f'Failed to flash {device.orig_ip} and no PDU available for hard reboot'
                        )

                    flash_counter = flash_counter + 1
                    if flash_counter == MAX_FLASH_ATTEMPTS:
                        asserts.abort_all(
                            f'Failed to flash {device.orig_ip} after {MAX_FLASH_ATTEMPTS} attempts'
                        )

                    self.log.info(
                        f'Hard rebooting {device.orig_ip} and retrying flash.')
                    device.reboot(reboot_type='hard',
                                  testbed_pdus=self.pdu_devices)

        # Report the new Fuchsia version
        try:
            dut = get_device(self.fuchsia_devices, 'DUT')
            version = dut.version()
            self.record_data({'sponge_properties': {
                'DUT_VERSION': version,
            }})
            self.log.info("DUT version found: {}".format(version))
        except Exception as e:
            self.failed_to_get_version = True
            raise signals.TestError(f'Failed to get DUT version: {e}') from e
