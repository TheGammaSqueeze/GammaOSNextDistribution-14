#!/usr/bin/env python3
#
# Copyright (C) 2016 The Android Open Source Project
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
This test script exercises GATT notify/indicate procedures.

Original location:
  tools/test/connectivity/acts_tests/tests/google/ble/gatt/GattNotifyTest.py
"""

from blueberry.tests.gd.cert.test_decorators import test_tracker_info

from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.sl4a_sl4a.lib import gatt_connected_base_test
from blueberry.utils.bt_gatt_constants import GattCharDesc
from blueberry.utils.bt_gatt_constants import GattDescriptor
from blueberry.utils.bt_gatt_constants import GattEvent
from mobly import test_runner


class GattNotifyTest(gatt_connected_base_test.GattConnectedBaseTest):

    @test_tracker_info(uuid='e0ba60af-c1f2-4516-a5d5-89e2def0c757')
    def test_notify_char(self):
        """Test notify characteristic value.

        Test GATT notify characteristic value.

        Steps:
        1. Central: write CCC - register for notifications.
        2. Peripheral: receive CCC modification.
        3. Peripheral: send characteristic notification.
        4. Central: receive notification, verify it's conent matches what was
           sent

        Expected Result:
        Verify that notification registration and delivery works.

        Returns:
          Pass if True
          Fail if False

        TAGS: LE, GATT, Characteristic
        Priority: 0
        """
        # write CCC descriptor to enable notifications
        self.central.sl4a.gattClientDescriptorSetValue(
            self.bluetooth_gatt, self.discovered_services_index, self.test_service_index, self.NOTIFIABLE_CHAR_UUID,
            GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID, GattDescriptor.ENABLE_NOTIFICATION_VALUE)

        self.central.sl4a.gattClientWriteDescriptor(self.bluetooth_gatt, self.discovered_services_index,
                                                    self.test_service_index, self.NOTIFIABLE_CHAR_UUID,
                                                    GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID)

        # enable notifications in app
        self.central.sl4a.gattClientSetCharacteristicNotification(self.bluetooth_gatt, self.discovered_services_index,
                                                                  self.test_service_index, self.NOTIFIABLE_CHAR_UUID,
                                                                  True)

        event = self._server_wait(GattEvent.DESC_WRITE_REQ)

        request_id = event['data']['requestId']
        bt_device_id = 0
        status = 0
        # confirm notification registration was successful
        self.peripheral.sl4a.gattServerSendResponse(self.gatt_server, bt_device_id, request_id, status, 0, [])
        # wait for client to get response
        event = self._client_wait(GattEvent.DESC_WRITE)

        # set notified value
        notified_value = [1, 5, 9, 7, 5, 3, 6, 4, 8, 2]
        self.peripheral.sl4a.gattServerCharacteristicSetValue(self.notifiable_char_index, notified_value)

        # send notification
        self.peripheral.sl4a.gattServerNotifyCharacteristicChanged(self.gatt_server, bt_device_id,
                                                                   self.notifiable_char_index, False)

        # wait for client to receive the notification
        event = self._client_wait(GattEvent.CHAR_CHANGE)
        assertThat(event["data"]["CharacteristicValue"]).isEqualTo(notified_value)

        return True


if __name__ == '__main__':
    test_runner.main()
