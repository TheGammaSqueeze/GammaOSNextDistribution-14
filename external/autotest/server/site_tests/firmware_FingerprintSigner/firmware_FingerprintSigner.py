# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server.cros.faft.fingerprint_test import FingerprintTest


class firmware_FingerprintSigner(FingerprintTest):
    """
    This test does minimal initialization, only checking the firmware file.
    """
    version = 1

    def run_once(self, fsi=False):
        """Run the test: verify that the key is MP (or pre-MP, if not FSI)"""
        allowed_types = [self._KEY_TYPE_MP]
        if not fsi:
            allowed_types += [self._KEY_TYPE_PRE_MP]
        self.validate_build_fw_file(allowed_types=allowed_types)
