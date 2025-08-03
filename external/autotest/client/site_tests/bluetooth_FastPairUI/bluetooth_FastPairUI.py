# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import ui_utils
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros.graphics import graphics_utils
from autotest_lib.client.cros.bluetooth import bluetooth_device_xmlrpc_server


class bluetooth_FastPairUI(graphics_utils.GraphicsTest):
    """Click through the Fast Pair pairing flow UI"""

    version = 1

    # Notification IDs
    DISCOVERY_GUEST_ID = 'cros_fast_pair_discovery_guest_notification_id'
    DISCOVERY_USER_ID = 'cros_fast_pair_discovery_user_notification_id'
    PAIRING_ID = 'cros_fast_pair_pairing_notification_id'
    ERROR_ID = 'cros_fast_pair_error_notification_id'

    # Node roles
    BUTTON_ROLE = 'button'

    # Node names
    CONNECT = 'CONNECT'

    # Amount of seconds we wait for notifications to show/disappear
    NOTIFICATION_WAIT_TIMEOUT = 30

    def initialize(self):
        """Autotest initialize function"""
        self.xmlrpc_delegate = \
            bluetooth_device_xmlrpc_server.BluetoothDeviceXmlRpcDelegate()
        super(bluetooth_FastPairUI, self).initialize(raise_error_on_hang=True)

    def cleanup(self):
        """Autotest cleanup function"""
        if self._GSC:
            keyvals = self._GSC.get_memory_difference_keyvals()
            for key, val in keyvals.items():
                self.output_perf_value(description=key,
                                       value=val,
                                       units='bytes',
                                       higher_is_better=False)
            self.write_perf_keyval(keyvals)
        super(bluetooth_FastPairUI, self).cleanup()

    def find_notification(self, expected_id):
        """Returns True if notification with expected_id is found"""
        notifications = self._cr.get_visible_notifications()
        return any([n['id'] == expected_id for n in (notifications or [])])

    def wait_for_notification_to_show(self, expected_id):
        """Wait for the notification with expected_id to show"""
        logging.info('Waiting for notificaiton with id:%s to show',
                     expected_id)
        utils.poll_for_condition(
                condition=lambda: self.find_notification(expected_id),
                exception=error.TestError(
                        """Timed out waiting for {} notification
                                      to show""".format(expected_id)),
                timeout=self.NOTIFICATION_WAIT_TIMEOUT)

    def wait_for_notification_to_disappear(self, expected_id):
        """Wait for the notification with expected_id to disappear"""
        logging.info('Waiting for notificaiton with id:%s to disappear',
                     expected_id)
        utils.poll_for_condition(
                condition=lambda: not self.find_notification(expected_id),
                exception=error.TestError(
                        """Timed out waiting for {} notification
                                      to disappear""".format(expected_id)),
                timeout=self.NOTIFICATION_WAIT_TIMEOUT)

    def wait_for_discovery_notification(self):
        """Wait for an instance of the discovery notification to show"""
        logging.info('Waiting for discovery notification to show.')
        utils.poll_for_condition(
                condition=lambda: (self.find_notification(
                        self.DISCOVERY_GUEST_ID) or self.find_notification(
                                self.DISCOVERY_USER_ID)),
                exception=error.TestError("""Timed out waiting for discovery
                                      notification to show"""),
                timeout=self.NOTIFICATION_WAIT_TIMEOUT)

    def run_once(self, username, password):
        """Click through the Fast Pair pairing flow UI"""
        try:
            # (b/221155928) Remove enable_features when it is on by default.
            with chrome.Chrome(autotest_ext=True,
                               enable_features='FastPair',
                               gaia_login=True,
                               username=username,
                               password=password) as cr:
                ui = ui_utils.UI_Handler()
                ui.start_ui_root(cr)
                self._cr = cr

                # Wait for the initial discovery notification to show.
                self.wait_for_discovery_notification()

                # Click 'connect' on the discovery notification.
                ui.doDefault_on_obj(name=self.CONNECT,
                                    isRegex=False,
                                    role=self.BUTTON_ROLE)

                # Wait for the pairing notification to show and then disappear.
                self.wait_for_notification_to_show(self.PAIRING_ID)
                self.wait_for_notification_to_disappear(self.PAIRING_ID)

                # Check if the error notification is shown.
                if self.find_notification(self.ERROR_ID):
                    raise error.TestFail('Pairing failed.')
        except error.TestFail:
            raise
        except Exception as e:
            logging.error('Exception "%s" seen during test', e)
            raise error.TestFail('Exception "%s" seen during test' % e)
        finally:
            self.xmlrpc_delegate.reset_on()
