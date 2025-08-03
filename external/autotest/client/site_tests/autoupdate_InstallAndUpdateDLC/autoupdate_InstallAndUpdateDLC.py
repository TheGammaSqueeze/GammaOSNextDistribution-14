# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test

class autoupdate_InstallAndUpdateDLC(update_engine_test.UpdateEngineTest):
    """Tests installing DLCs and updating them along with the OS. """
    version = 1

    def run_once(self, payload_urls, interactive=True):
        """
        Install DLC and perform an update, using nebraska.

        @param payload_urls: A list containing the platform payload (OS) URL
                             and DLC payload URL(s). The platform payload is
                             required, since DLCs are updated together with
                             the platform. A full DLC payload is required to
                             install the DLC. In case of a delta update, both
                             full and delta DLC payloads should be included in
                             payload_urls.
        @param interactive: Whether the update should be interactive.

        """
        with nebraska_wrapper.NebraskaWrapper(
                payload_url=payload_urls) as nebraska:
            nebraska_url = nebraska.get_update_url(critical_update=True)
            logging.debug('Installing sample-dlc')
            # Nebraska will automatically use the full payload to install DLC.
            self._dlc_util.install(self._dlc_util._SAMPLE_DLC_ID, nebraska_url)

            if not self._dlc_util.is_installed(self._dlc_util._SAMPLE_DLC_ID):
                raise error.TestFail('Test DLC was not installed.')

            logging.debug('Updating OS and DLC')
            self._check_for_update(nebraska_url,
                                   wait_for_completion=True,
                                   interactive=interactive)
