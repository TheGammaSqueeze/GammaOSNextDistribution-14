# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import test, telemetry_check


class telemetry_Check(test.test):
    """Run telemetry_check."""
    version = 1

    def run_once(self, count, run_cryptohome, run_incognito, run_screenlock):
        telemetry_check.TelemetryCheck(count=count,
                                       run_cryptohome=run_cryptohome,
                                       run_incognito=run_incognito,
                                       run_screenlock=run_screenlock).Run()
