# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_test

class autoupdate_BadMetadata(update_engine_test.UpdateEngineTest):
    """Tests updates fail when the metadata in the omaha response is invalid."""
    version = 1

    _SHA256_ERROR = 'Updating payload state for error code: 10 (' \
                    'ErrorCode::kPayloadHashMismatchError)'
    _METADATA_SIZE_ERROR = 'Updating payload state for error code: 32 (' \
                           'ErrorCode::kDownloadInvalidMetadataSize)'


    def run_once(self, payload_url, bad_metadata_size=False, bad_sha256=False):
        """
        Tests update_engine can deal with invalid data in the omaha response.

        @param payload_url: The payload url.
        @param bad_metadata_size: True if we want to test bad metadata size.
        @param bad_sha256: True if we want to test bad sha256.

        """
        props_to_override = {}
        error_string = None
        if bad_sha256:
            props_to_override[nebraska_wrapper.KEY_SHA256] = 'blahblah'
            error_string = self._SHA256_ERROR
        if bad_metadata_size:
            props_to_override[nebraska_wrapper.KEY_METADATA_SIZE] = 123
            props_to_override[
                nebraska_wrapper.KEY_PUBLIC_KEY] = self._IMAGE_PUBLIC_KEY
            error_string = self._METADATA_SIZE_ERROR

        with nebraska_wrapper.NebraskaWrapper(
            log_dir=self.resultsdir, payload_url=payload_url,
            **props_to_override) as nebraska:

            try:
                self._check_for_update(
                    nebraska.get_update_url(critical_update=True),
                    wait_for_completion=True)
                raise error.TestFail('Update completed when it should have '
                                     'failed. Check the update_engine log.')
            except error.CmdError as e:
                logging.error(e)
                self._check_update_engine_log_for_entry(error_string,
                                                        raise_error=True)
