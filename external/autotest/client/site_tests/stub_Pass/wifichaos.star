# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

load("//metadata/test_common.star", "test_common")

DOC = """
This is a placeholder test for allowing the scheduler to install tests on the
chaos_dut machines.
"""

TESTS = [
    test_common.define_client_test(
        test_name = "stub_Pass.wifichaos",
        purpose = "To re-image chaos_dut machines nightly.",
        doc = DOC,
        # TODO: find real owners for this test.
        owner_emails = ["email_addr@chromium.org"],
        suites = ["wifichaos"],
        # TODO: common_deps = ["chaos_dut"],
    ),
]
