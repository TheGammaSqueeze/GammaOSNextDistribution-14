# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

load("//metadata/test_common.star", "test_common")

TESTS = [test_common.define_client_test(
    test_name = "dummy_Fail",
    purpose = "Demonstrate failure methods of autotests.",
    doc = """
This is a helper test that will fail in a number of ways.
It is used to verify the behavior of autotest.
""",
    owner_emails = ["email_addr@chromium.org"],
    owner_groups = ["team-mdb-group"],
    suites = ["dummy", "push_to_prod", "skylab_staging_test"],
    #TODO: multiple test job.run_test calls here
)]
