# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from dbus.mainloop.glib import DBusGMainLoop

try:
    from gi.repository import GObject
except ImportError:
    import gobject as GObject

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib.cros import chrome, session_manager
from autotest_lib.client.cros import asan


class login_LoginSuccess(test.test):
    """Sign in using Telemetry and validate system state."""
    version = 1

    _SESSION_START_TIMEOUT = 10
    _SESSION_STOP_TIMEOUT = 60
    # TODO(afakhry): Remove this timeout increase for asan bots once we figure
    # out why logging out is taking so long. See crbug.com/488291
    if asan.running_on_asan():
        _SESSION_STOP_TIMEOUT *= 2


    def initialize(self):
        super(login_LoginSuccess, self).initialize()

        bus_loop = DBusGMainLoop(set_as_default=True)
        self._session_manager = session_manager.connect(bus_loop)
        self._listener = session_manager.SessionSignalListener(
                GObject.MainLoop())


    def run_once(self,
                 stress_run=False,
                 arc_mode=None,
                 username=None,
                 password=None,
                 dont_override_profile=False):
        """
        Runs the test.

        @param stress_run: True if we are doing a stress run and want to
                           double the timeout.
        @param username: Username to use instead of the default telemetry one.
        @param password: Password to use instead of the default telemetry one.
        @param arc_mode: This value is passed to Chrome and determines how
                         the ARC/Android instance should start. Possible values
                         are defined in common_lib/cros/arc_common.py.
        @dont_override_profile: Don't delete cryptohome before login.

        """
        if stress_run:
            self._SESSION_STOP_TIMEOUT *= 2
        self._listener.listen_for_session_state_change('started')
        with chrome.Chrome(arc_mode=arc_mode,
                           username=username,
                           password=password,
                           dont_override_profile=dont_override_profile):
            self._listener.wait_for_signals(desc='Session started.',
                                            timeout=self._SESSION_START_TIMEOUT)
            # To enable use as a 'helper test'.
            self.job.set_state('client_success', True)

            # Start listening to stop signal before logging out.
            self._listener.listen_for_session_state_change('stopped')

        self._listener.wait_for_signals(desc='Session stopped.',
                                        timeout=self._SESSION_STOP_TIMEOUT)
