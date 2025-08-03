# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test


class autoupdate_InvalidateSuccessfulUpdate(update_engine_test.UpdateEngineTest
                                            ):
    """Tests installing an update and then invalidating it."""

    version = 1

    def _apply_update(self, update_url):
        """
        Performs the update and ensures it is successful.

        @param update_url: The URL to get an update.

        """
        try:
            self._check_for_update(update_url,
                                   critical_update=True,
                                   wait_for_completion=True)
        except error.CmdError as e:
            raise error.TestFail('Update attempt failed: %s' %
                                 self._get_last_error_string())

    def _check_invalidated_update(self, update_url):
        """
        Performs an update check and confirms that it results
        in an invalidated update.

        @param update_url: The URL to get an update.

        """
        try:
            self._check_for_update(update_url,
                                   critical_update=True,
                                   wait_for_completion=False)
            self._wait_for_update_to_idle(check_kernel_after_update=True,
                                          inactive_kernel=False)
        except error.CmdError as e:
            raise error.TestFail('Invalidate attempt failed: %s' %
                                 self._get_last_error_string())
        self._check_update_engine_log_for_entry(
                'Invalidating previous update.',
                raise_error=True,
                err_str='Failed to invalidate previous update')

    def run_once(self, payload_url):
        """
        Runs an update and then invalidates it using Nebraska.

        @param payload_url: Path to a payload on Google storage.

        """
        with nebraska_wrapper.NebraskaWrapper(
                log_dir=self.resultsdir, payload_url=payload_url) as nebraska:
            self._apply_update(nebraska.get_update_url())
            nebraska.update_config(invalidate_last_update=True)
            self._check_invalidated_update(nebraska.get_update_url())
