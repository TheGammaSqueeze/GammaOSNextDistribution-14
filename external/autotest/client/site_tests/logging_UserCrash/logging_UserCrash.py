# Copyright (c) 2010 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import cros_ui, upstart
from autotest_lib.client.cros.crash import user_crash_test


_CRASH_REPORTER_ENABLED_PATH = '/var/lib/crash_reporter/crash-handling-enabled'


class logging_UserCrash(user_crash_test.UserCrashTest):
    """Verifies crash reporting for user processes."""
    version = 1


    def _get_uptime(self):
        with open('/proc/uptime', 'r') as f:
            uptime_seconds = float(f.readline().split()[0])

        return uptime_seconds


    # This test has a tast counterpart, but the tast version only performs a
    # slightly different function. Specifically, the tast variant does not
    # verify that crash reporter state is valid before any tests run and
    # re-initialize crash reporter.
    # TODO(https://crbug.com/1085194): Write a tast test to verify that crash
    # reporter's state is good on a "clean" system.
    def _test_reporter_startup(self):
        """Test that the core_pattern is set up by crash reporter."""
        # Turn off crash filtering so we see the original setting.
        self.disable_crash_filtering()
        output = utils.read_file(self._CORE_PATTERN).rstrip()
        expected_core_pattern = ('|%s --user=%%P:%%s:%%u:%%g:%%f' %
                                 self._CRASH_REPORTER_PATH)
        if output != expected_core_pattern:
            raise error.TestFail('core pattern should have been %s, not %s' %
                                 (expected_core_pattern, output))


    # This test has a critical tast counterpart, but we leave it here because
    # it verifies that the in_progress_integration_test variable will be set in
    # autotests.
    def _test_chronos_crasher(self):
        """Test a user space crash when running as chronos is handled."""
        self._check_crashing_process(
                'chronos',
                extra_meta_contents='upload_var_in_progress_integration_test='
                'logging_UserCrash')


    def initialize(self):
        user_crash_test.UserCrashTest.initialize(self)

        # If the device has a GUI, return the device to the sign-in screen, as
        # some tests will fail inside a user session.
        if upstart.has_service('ui'):
            cros_ui.restart()


    def run_once(self):
        """ Run all tests once """
        self._prepare_crasher()
        self._populate_symbols()

        # Run the test once without re-initializing
        # to catch problems with the default crash reporting setup
        self.run_crash_tests(['reporter_startup'],
                              initialize_crash_reporter=False,
                              must_run_all=False)

        self.run_crash_tests(['reporter_startup', 'chronos_crasher'],
                             initialize_crash_reporter=True)
