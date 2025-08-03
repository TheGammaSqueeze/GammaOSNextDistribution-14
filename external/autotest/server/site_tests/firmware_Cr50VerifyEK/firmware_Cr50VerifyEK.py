# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50VerifyEK(Cr50Test):
    """Verify tpm verify ek."""
    version = 1

    def run_once(self, host):
        """Run tpm verify ek."""
        host.run('tpm-manager initialize')
        host.run('tpm-manager verify_endorsement')
