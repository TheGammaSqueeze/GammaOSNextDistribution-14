# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

load("//metadata/test_common.star", "test_common")

DOC = """
This is a helper test that will succeed. Used to verify various
autotest scheduling features, including pass results, dependencies, etc.
"""

TESTS = [
    test_common.define_client_test(
        test_name = "stub_Pass",
        purpose = "Demonstrate success methods of autotests.",
        doc = DOC,
        owner_emails = ["email_addr@chromium.org"],
        owner_groups = ["team-mdb-group"],
        suites = ["stub", "stubclientretries", "push_to_prod",
            "skylab_staging_test", "something_else"],
        #TODO: max_result_size_kb = 5000
    ),

    test_common.define_client_test(
        test_name = "stub_Pass.actionable",
        purpose = "Demonstrate success methods of autotests",
        doc = DOC,
        owner_emails = ["email_addr@chromium.org"],
        suites = ["stub", "stubclientretries", "push_to_prod",
            "skylab_staging_test", "something_else"],
        #TODO: common_deps = ["cleanup-reboot"],
        named_args = {"tag": "actionable"},
    ),

    test_common.define_client_test(
        test_name = "stub_Pass.bluetooth",
        purpose = "Demonstrate DEPENDENCIES in autotests.",
        doc = DOC,
        owner_emails = ["email_addr@chromium.org"],
        suites = ["stub", "push_to_prod", "skylab_staging_test"],
        common_deps = ["bluetooth"],
        named_args = {"tag": "bluetooth"},
    ),

    test_common.define_client_test(
        test_name = "stub_Pass.experimental",
        purpose = "Demonstrate success methods of autotests.",
        doc = DOC,
        owner_emails = ["email_addr@chromium.org"],
        suites = ["stub", "stubclientretries", "something_else"],
        named_args = {"tag": "experimental"},
    ),
]
