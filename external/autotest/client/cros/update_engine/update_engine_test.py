# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import shutil
import time
import six.moves.urllib.parse

from autotest_lib.client.bin import test, utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import dlc_util
from autotest_lib.client.cros.update_engine import update_engine_util

class UpdateEngineTest(test.test, update_engine_util.UpdateEngineUtil):
    """Base class for update engine client tests."""

    _NETWORK_INTERFACES = ['eth0', 'eth1', 'eth2']


    def initialize(self):
        """Initialize for this test."""
        self._set_util_functions(utils.run, shutil.copy)
        self._internet_was_disabled = False

        # Utilities for DLC management
        self._dlc_util = dlc_util.DLCUtil(self._run)


    def cleanup(self):
        """Cleanup for this test."""
        # Make sure to grab the update engine log for every test run.
        shutil.copy(self._UPDATE_ENGINE_LOG, self.resultsdir)

        # Ensure ethernet adapters are back on
        self._enable_internet()


    def _enable_internet(self, ping_server='google.com'):
        """
        Re-enables the internet connection.

        @param ping_server: The server to ping to check we are online.

        """
        if not self._internet_was_disabled:
            return

        self._internet_was_disabled = False
        logging.debug('Before reconnect: %s', utils.run(['ifconfig']))
        for eth in self._NETWORK_INTERFACES:
            utils.run(['ifconfig', eth, 'up'], ignore_status=True)
        utils.start_service('recover_duts', ignore_status=True)

        # Print ifconfig to help debug DUTs that stay offline.
        logging.debug('After reconnect: %s', utils.run(['ifconfig']))

        # We can't return right after reconnecting the network or the server
        # test may not receive the message. So we wait a bit longer for the
        # DUT to be reconnected.
        utils.poll_for_condition(lambda: utils.ping(ping_server,
                                                    tries=3, timeout=10) == 0,
                                 timeout=120,
                                 sleep_interval=1,
                                 exception=error.TestFail(
                                     'Ping failed after reconnecting network'))


    def _disable_internet(self, ping_server='google.com'):
        """Disable the internet connection"""
        self._internet_was_disabled = True
        try:
            logging.debug('Before disconnect: %s', utils.run(['ifconfig']))
            # DUTs in the lab have a service called recover_duts that is used to
            # check that the DUT is online and if it is not it will bring it
            # back online. We will need to stop this service for the length
            # of this test.
            utils.stop_service('recover_duts', ignore_status=True)
            for eth in self._NETWORK_INTERFACES:
                result = utils.run(['ifconfig', eth, 'down'],
                                   ignore_status=True)
                logging.debug(result)

            # Print ifconfig to help debug DUTs that stay online.
            logging.debug('After disconnect: %s', utils.run('ifconfig'))

            # Make sure we are offline
            utils.poll_for_condition(lambda: utils.ping(ping_server,
                                                        deadline=5,
                                                        timeout=5) != 0,
                                     timeout=60,
                                     sleep_interval=1,
                                     desc='Ping failure while offline.')
        except (error.CmdError, utils.TimeoutError):
            logging.exception('Failed to disconnect one or more interfaces.')
            logging.debug(utils.run(['ifconfig'], ignore_status=True))
            raise error.TestFail('Disabling the internet connection failed.')


    def _disconnect_reconnect_network_test(self,
                                           time_without_network=25,
                                           accepted_movement=0.015,
                                           ping_server='google.com'):
        """
        Disconnects the network for a period of time, verifies that the update
        pauses, reconnects the network, and ensures that the update picks up
        from where it left off. This will be used as a part of
        autoupdate_ForcedOOBEUpdate.interrupt and autoupdate_Interruptions.

        @param time_without_network: Duration of the network disconnection in
                                     seconds.
        @param accepted_movement: Acceptable movement of update_engine
                                  progress after the network is disabled.
                                  Sometimes when network is disabled
                                  update_engine progress will move a little,
                                  which can cause false positives.
        @param ping_server: The server to ping to check we are online.

        """
        logging.info('Starting network interruption check.')
        if self._is_update_finished_downloading():
            raise error.TestFail('The update has already finished before we '
                                 'can disconnect network.')
        self._disable_internet()

        # Check that we are offline.
        result = utils.ping(ping_server, deadline=5, timeout=5)
        if result != 2:
            raise error.TestFail('Ping succeeded even though we were offline.')

        # We are seeing update_engine progress move a very tiny amount
        # after disconnecting network so wait for it to stop moving.
        utils.poll_for_condition(lambda: self._has_progress_stopped,
                                 desc='Waiting for update progress to stop.')

        # Get the update progress as the network is down
        progress_before = float(self._get_update_engine_status()[
            self._PROGRESS])

        seconds = 1
        while seconds < time_without_network:
            logging.info(self._get_update_engine_status())
            time.sleep(1)
            seconds += 1

        progress_after = float(self._get_update_engine_status()[
            self._PROGRESS])

        if progress_before != progress_after:
            if progress_before < progress_after:
                if progress_after - progress_before > accepted_movement:
                    raise error.TestFail('The update continued while the '
                                         'network was supposedly disabled. '
                                         'Before: %f, After: %f' % (
                                         progress_before, progress_after))
                else:
                    logging.warning('The update progress moved slightly while '
                                    'network was off.')
            elif self._is_update_finished_downloading():
                raise error.TestFail('The update finished while the network '
                                     'was disabled. Before: %f, After: %f' %
                                     (progress_before, progress_after))
            else:
                raise error.TestFail('The update appears to have restarted. '
                                     'Before: %f, After: %f' % (progress_before,
                                                                progress_after))

        self._enable_internet()
