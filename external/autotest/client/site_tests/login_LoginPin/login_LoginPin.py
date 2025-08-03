# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from dbus.mainloop.glib import DBusGMainLoop
from gi.repository import GObject

from autotest_lib.client.bin import test
from autotest_lib.client.cros import cryptohome
from autotest_lib.client.common_lib import error, lsbrelease_utils
from autotest_lib.client.common_lib.cros import chrome, session_manager

class login_LoginPin(test.test):
    """Sets up a PIN for user and then logs in using the pin."""
    version = 1

    def run_once(self,
                 username='autotest',
                 password='password',
                 pin='123456789',
                 setup_pin=True,
                 login_pin=True):
        """Test body."""
        if not cryptohome.is_low_entropy_credentials_supported():
            if lsbrelease_utils.get_current_board() == 'hatch':
                # Fail on a board where LEC must work so bisection could be run.
                raise error.TestFail(
                        'low entropy credentials must be ' +
                        'supported on hatch, are the cryptohome utils wrong?')
            raise error.TestNAError(
                    'Skip test: No hardware support for PIN login')

        username = chrome.NormalizeEmail(username)
        if setup_pin:
            with chrome.Chrome(username=username, password=password) as cr:
                if not cryptohome.is_vault_mounted(username):
                    raise error.TestFail(
                            'Expected to find a mounted vault for %s' %
                            username)

                tab = cr.browser.tabs.New()
                tab.Navigate('chrome://os-settings/osPrivacy/lockScreen')

                tab.WaitForDocumentReadyStateToBeComplete()
                setup_pin = '''
                  const getAuthToken = new Promise((resolve, reject) => {
                    chrome.quickUnlockPrivate.getAuthToken('%s', function(auth_token) { resolve(auth_token.token); })
                  });
                  function setModes(token) {
                    return new Promise((resolve, reject) => {
                      chrome.quickUnlockPrivate.setModes(token, [chrome.quickUnlockPrivate.QuickUnlockMode.PIN], ['%s'], resolve);
                    })
                  }
                  function canAuthenticatePin() {
                    return new Promise((resolve, reject) => {
                      chrome.quickUnlockPrivate.canAuthenticatePin(resolve);
                    })
                  }

                  getAuthToken.then(setModes).then(canAuthenticatePin);
                  ''' % (password, pin)
                pin_set = tab.EvaluateJavaScript(setup_pin, promise=True)
                if not pin_set:
                    raise error.TestFail('Failed to setup a pin')

        if login_pin:
            DBusGMainLoop(set_as_default=True)
            listener = session_manager.SessionSignalListener(
                    GObject.MainLoop())
            listener.listen_for_session_state_change('started')
            with chrome.Chrome(auto_login=False,
                               clear_enterprise_policy=False,
                               dont_override_profile=True,
                               extra_browser_args=[
                                       '--skip-force-online-signin-for-testing'
                               ]) as cr:
                oobe = cr.browser.oobe
                oobe.WaitForJavaScriptCondition(
                        "typeof Oobe == 'function' && "
                        "typeof OobeAPI == 'object' && "
                        "Oobe.readyForTesting",
                        timeout=20)
                oobe.ExecuteJavaScript("OobeAPI.loginWithPin('%s','%s')" %
                                       (username, pin))
                listener.wait_for_signals(desc='Session started.', timeout=20)
