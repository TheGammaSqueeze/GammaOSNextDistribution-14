# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import json
import logging
import time

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome
from autotest_lib.client.cros import cryptohome
from autotest_lib.client.cros.input_playback import input_playback


class desktopui_CheckRlzPingSent(test.test):
    """Tests creating a new user, doing a google search, checking RLZ Data."""
    version = 1

    _RLZ_DATA_FILE = "/home/chronos/user/RLZ Data"

    def _verify_rlz_data(self, expect_caf_ping=True, guest=False):
        """
        Checks the RLZ data file for CAI and CAF ping events.

        @param expect_caf_ping: True if expecting the CAF event to be in the
                                RLZ data file, False if not expecting it.
        @param guest: True if checking in guest mode. The guest mode user
                      mount may not be in the root mount namespace, so the RLZ
                      data file path must be adjusted accordingly.

        """
        rlz_data_cmd = []
        if guest:
            mounter_pid = utils.run(
                ['pgrep', '-f', '/usr/sbin/cryptohome-namespace-mount']).stdout
            if mounter_pid is not None:
                ns_path = '/proc/%s/ns/mnt' % mounter_pid.rstrip()
                rlz_data_cmd.extend(['nsenter', '--mount=%s' % ns_path])

        rlz_data_cmd.extend(['cat', self._RLZ_DATA_FILE])

        def rlz_data_exists():
            """Check rlz data exists."""
            rlz_data = json.loads(utils.run(rlz_data_cmd).stdout)
            logging.debug('rlz data: %s', rlz_data)
            if 'stateful_events' in rlz_data:
                cai_present = 'CAI' in rlz_data['stateful_events']['C']['_']
                caf_present = 'CAF' in rlz_data['stateful_events']['C']['_']
                return cai_present and (caf_present == expect_caf_ping)
            return False

        utils.poll_for_condition(rlz_data_exists, timeout=120)


    def _check_url_for_rlz(self, cr):
        """
        Does a Google search and ensures there is an rlz parameter.

        @param cr: Chrome instance.

        """
        timeout_minutes = 2
        timeout = time.time() + 60 * timeout_minutes

        # Setup a keyboard emulator to open new tabs and type a search.
        with input_playback.InputPlayback() as player:
            player.emulate(input_type='keyboard')
            player.find_connected_inputs()

            while True:
                # Open a new tab, search in the omnibox.
                player.blocking_playback_of_default_file(
                    input_type='keyboard', filename='keyboard_ctrl+t')
                player.blocking_playback_of_default_file(
                    input_type='keyboard', filename='keyboard_b+a+d+enter')
                logging.info(cr.browser.tabs[-1].url)
                if 'rlz=' in cr.browser.tabs[-1].url:
                    break
                else:
                    if time.time() > timeout:
                        raise error.TestFail('RLZ ping did not send in %d '
                                             'minutes.' % timeout_minutes)
                    time.sleep(10)


    def _wait_for_rlz_lock(self):
        """Waits for the DUT to get into locked state after login."""
        def get_install_lockbox_finalized_status():
            status = cryptohome.get_install_attribute_status()
            return status == 'VALID'

        try:
            utils.poll_for_condition(
                lambda: get_install_lockbox_finalized_status(),
                exception=utils.TimeoutError(),
                timeout=120)
        except utils.TimeoutError:
            raise error.TestFail('Timed out trying to lock the device')


    def run_once(self, ping_timeout=30, expect_caf_ping=True, username=None,
                 pre_login=None, pre_login_username=None):
        """
        Tests whether or not the RLZ install event (CAI) and first-use event
        (CAF) pings are sent. After the first user login, the CAI ping will
        be sent after a certain delay. This delay is 24 hours by default, but
        can be overridden by specifying the rlz-ping-delay flag in
        /etc/chrome_dev.conf, or by using the --rlz-ping-delay argument to
        Chrome. Then, if two RW_VPD settings have the correct values
        (should_send_rlz_ping == 1, rlz_embargo_end_date has passed OR not
        specified), the CAF ping will be sent as well. Ping status is checked
        in the /home/chronos/user/'RLZ Data' file, which will contain entries
        for CAI and CAF pings in the 'stateful_events' section.

        @param ping_timeout: Delay time (seconds) before any RLZ pings are
                             sent.
        @param expect_caf_ping: True if expecting the first-use event (CAF)
                                ping to be sent, False if not expecting it.
                                The ping would not be expected if the relevant
                                RW_VPD settings do not have the right
                                combination of values.
        @param username: Username to log in with during the main RLZ check.
                         None to sign in with the default test user account.
                         Specifying a username will log in with a profile
                         distinct from the test user.
        @param pre_login: Whether or not to login before the main RLZ ping
                          test, and for how long. Should be one of
                          ['lock', 'no_lock', None]. 'lock' is meant for guest
                          mode testing, where a non-guest user must login to
                          'lock' the device for RLZ before the ping can be
                          sent in guest mode. 'no_lock' is to log and log out
                          immediately to ensure no ping is sent. Used to
                          verify that the ping can be sent from subsequent
                          user logins if it has not already been sent.
        @param pre_login_username: The username to sign in with for the
                                   pre-login step. None to use the default
                                   test user account.

        """
        # Browser arg to make DUT send rlz ping after a short delay.
        browser_args = ['--rlz-ping-delay=%d' % ping_timeout]

        # TODO(crbug/1103298): keyboard input doesn't work in guest mode
        # without disabling this flag. Remove when bug is fixed.
        if pre_login == 'lock':
            browser_args.append('--disable-features=ImeInputLogicFst')

        # If we are testing the ping is sent in guest mode (pre_login='lock'),
        # we need to first do a real login and wait for the DUT to become
        # 'locked' for rlz. Then logout and enter guest mode.
        # If we are testing the ping can be sent by the second user to use the
        # device, we will login and immediately logout (pre_login='no_lock').
        if pre_login is not None:
            logging.debug("Logging in before main RLZ test with username "
                          "flag: %s", pre_login_username)
            with chrome.Chrome(logged_in=True, username=pre_login_username,
                               extra_browser_args=browser_args):
                if pre_login is 'lock':
                    logging.debug("Waiting for device to be 'locked' for RLZ")
                    self._wait_for_rlz_lock()

        logging.debug("Starting RLZ check with username flag: %s", username)
        # Pass clear_enterprise_policy=False in guest mode to avoid deleting
        # /home/chronos/'Local State' between logins. Deleting it will cause
        # the guest mode test to fail on boards that do not have rlz_brand_code
        # in the VPD (mainly unibuild models). This file is normally not
        # deleted between logins anyways.
        with chrome.Chrome(
                logged_in=pre_login is not 'lock',
                clear_enterprise_policy=pre_login is not 'lock',
                extra_browser_args=browser_args,
                username=username,
                dont_override_profile=True) as cr:
            self._check_url_for_rlz(cr)
            self._verify_rlz_data(expect_caf_ping=expect_caf_ping,
                                  guest=pre_login is 'lock')
