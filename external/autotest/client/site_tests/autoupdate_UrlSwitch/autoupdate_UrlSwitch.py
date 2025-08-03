# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test

class autoupdate_UrlSwitch(update_engine_test.UpdateEngineTest):
    """Tests that we can continue with the second url when the first fails."""
    version = 1

    def run_once(self, payload_url):
        """
        Tests to see whether the update_engine can successfully switch to a
        different URL if one fails.

        @param image_url: The URL of the update payload.

        """
        with nebraska_wrapper.NebraskaWrapper(
            log_dir=self.resultsdir, payload_url=payload_url) as nebraska:

            # Start the update that will return two Urls. This matches what test
            # and production omaha does today.
            self._check_for_update(
                nebraska.get_update_url(failures_per_url=1, num_urls=2,
                                        critical_update=True))
            self._wait_for_progress(0.2)

            # Pull the network cable so the update fails.
            self._disable_internet()

            # It will retry 21 times before giving up.
            self._wait_for_update_to_fail()

            # Check that we are moving to the next Url.
            self._enable_internet()
            self._check_update_engine_log_for_entry(
                'Reached max number of failures for Url',
                raise_error=True)

            # The next update attempt should resume and finish successfully.
            self._check_for_update(
                nebraska.get_update_url(critical_update=True))
            self._wait_for_update_to_complete()
            self._check_update_engine_log_for_entry(
                'Resuming an update that was previously started.',
                raise_error=True)
