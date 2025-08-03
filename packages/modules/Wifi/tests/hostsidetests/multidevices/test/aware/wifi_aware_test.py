#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
"""CTS-V Wifi test reimplemented in Mobly."""
import logging
import sys

logging.basicConfig(filename="/tmp/wifi_aware_test_log.txt", level=logging.INFO)

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device

WIFI_AWARE_SNIPPET_PATH = 'wifi_aware_snippet'

WIFI_AWARE_SNIPPET_PACKAGE = 'com.google.snippet.wifi.aware'

TEST_MESSAGE = 'test message!'

MESSAGE_ID = 1234


class WifiAwareTest(base_test.BaseTestClass):

    def setup_class(self):
        # Declare that two Android devices are needed.
        self.publisher, self.subscriber = self.register_controller(
            android_device, min_number=2)

        def setup_device(device):
            device.load_snippet('wifi_aware_snippet', WIFI_AWARE_SNIPPET_PACKAGE)
            # Expect wifi_aware apk to be installed as it is configured to install
            # with the module configuration AndroidTest.xml on both devices.
            device.adb.shell([
                'pm', 'grant', WIFI_AWARE_SNIPPET_PACKAGE,
                'android.permission.ACCESS_FINE_LOCATION'
            ])

        # Sets up devices in parallel to save time.
        utils.concurrent_exec(
            setup_device, ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True)

    def on_fail(self, record):
        logging.info('Collecting bugreports...')
        android_device.take_bug_reports(
            ads=[self.publisher, self.subscriber],
            test_name=record.test_name,
            begin_time=record.begin_time,
            destination=self.current_test_info.output_path
        )

    def test_discovery_base_test_case(self):
        is_unsolicited = True
        is_ranging_required = False
        self.subscriber.wifi_aware_snippet.attach()
        self.publisher.wifi_aware_snippet.attach()

        self.publisher.wifi_aware_snippet.publish(is_unsolicited, is_ranging_required, False)
        self.subscriber.wifi_aware_snippet.subscribe(is_unsolicited, is_ranging_required, False)

        self.subscriber.wifi_aware_snippet.sendMessage(MESSAGE_ID, TEST_MESSAGE)
        received_message = self.publisher.wifi_aware_snippet.receiveMessage()
        asserts.assert_equal(
            received_message, TEST_MESSAGE,
            'Message received by publisher does not match the message sent by subscriber.'
        )

    def test_aware_pairing_accept_test_case(self):
        is_pairing_supported = self.publisher.wifi_aware_snippet.checkIfPairingSupported() and \
                               self.subscriber.wifi_aware_snippet.checkIfPairingSupported()
        asserts.skip_if(not is_pairing_supported,
                        "Aware pairing test skip as feature is not supported")
        is_unsolicited = True
        is_ranging_required = False
        self.subscriber.wifi_aware_snippet.attach()
        self.publisher.wifi_aware_snippet.attach()

        self.publisher.wifi_aware_snippet.publish(is_unsolicited, is_ranging_required, True)
        self.subscriber.wifi_aware_snippet.subscribe(is_unsolicited, is_ranging_required, True)
        self.subscriber.wifi_aware_snippet.initiatePairingSetup(True, True)
        self.subscriber.wifi_aware_snippet.respondToPairingSetup(True, True)

    def test_aware_pairing_reject_test_case(self):
        is_pairing_supported = self.publisher.wifi_aware_snippet.checkIfPairingSupported() and \
                               self.subscriber.wifi_aware_snippet.checkIfPairingSupported()
        asserts.skip_if(not is_pairing_supported,
                        "Aware pairing test skip as feature is not supported")
        is_unsolicited = True
        is_ranging_required = False
        self.subscriber.wifi_aware_snippet.attach()
        self.publisher.wifi_aware_snippet.attach()

        self.publisher.wifi_aware_snippet.publish(is_unsolicited, is_ranging_required, True)
        self.subscriber.wifi_aware_snippet.subscribe(is_unsolicited, is_ranging_required, True)
        self.subscriber.wifi_aware_snippet.initiatePairingSetup(True, False)
        self.subscriber.wifi_aware_snippet.respondToPairingSetup(True, False)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
