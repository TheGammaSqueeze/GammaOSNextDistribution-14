# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.cellular import test_environment
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test

class autoupdate_CannedOmahaUpdate(update_engine_test.UpdateEngineTest):
    """ Updates a DUT with a given image using a Nebraska instance."""

    version = 1


    def run_canned_update(self, allow_failure, update_url, interactive):
        """
        Performs the update.

        @param allow_failure: True if we dont raise an error on failure.
        @param update_url: The URL to get an update.
        @param interactive: Whether the update is interactive or not.

        """

        try:
            self._check_for_update(update_url,
                                   critical_update=True,
                                   wait_for_completion=True,
                                   interactive=interactive)
        except error.CmdError as e:
            if not allow_failure:
                raise error.TestFail('Update attempt failed: %s' %
                                     self._get_last_error_string())
            else:
                logging.info('Ignoring failed update. Failure reason: %s', e)


    def run_once(self,
                 payload_url,
                 allow_failure=False,
                 public_key=None,
                 use_cellular=False,
                 interactive=True):
        """
        Runs an update with a canned response using Nebraska.

        @param payload_url: Path to a payload on Google storage.
        @param allow_failure: If true, failing the update is expected.
        @param public_key: The public key to serve to the update client.
        @param use_cellular: True if this test uses cellular.
        @param interactive: Whether the update is interactive or not.

        """

        with nebraska_wrapper.NebraskaWrapper(
            log_dir=self.resultsdir, payload_url=payload_url,
            public_key=public_key) as nebraska:

            if not use_cellular:
                self.run_canned_update(allow_failure,
                                       nebraska.get_update_url(), interactive)
                return

            # Setup DUT so that we have ssh over ethernet but DUT uses
            # cellular as main connection.
            try:
                with test_environment.CellularOTATestEnvironment() as test_env:
                    service = test_env.shill.wait_for_cellular_service_object()
                    if not service:
                        raise error.TestError('No cellular service found.')

                    CONNECT_TIMEOUT = 120
                    test_env.shill.connect_service_synchronous(
                            service, CONNECT_TIMEOUT)
                    self.run_canned_update(allow_failure,
                                           nebraska.get_update_url(),
                                           interactive)
            except error.TestError as e:
                # Raise as test failure instead of test error so it is
                # propagated to the server test's failure message.
                logging.error('Failed setting up cellular connection.')
                raise error.TestFail(e)
