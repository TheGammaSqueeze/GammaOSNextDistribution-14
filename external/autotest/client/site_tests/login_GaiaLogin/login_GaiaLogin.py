# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.bin import test, utils
from autotest_lib.client.cros import cryptohome
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import chrome


class login_GaiaLogin(test.test):
    """Sign into production gaia using Telemetry."""
    version = 1


    def run_once(self, username, password):
        """Test body."""
        if not username:
            raise error.TestFail('User not set.')
        if not password:
            raise error.TestFail('Password not set.')

        with chrome.Chrome(gaia_login=True,
                           username=username,
                           password=password) as cr:
            if not cryptohome.is_vault_mounted(
                    user=chrome.NormalizeEmail(username)):
                raise error.TestFail('Expected to find a mounted vault for %s'
                                     % username)

            tab = cr.browser.tabs.New()
            tab.Navigate('https://google.com')
            tab.WaitForDocumentReadyStateToBeComplete()

            def _userLoggedIn(tab, username):
                # TODO(achuith): Use a better signal of being logged in, instead
                # of parsing google.com.
                res = tab.EvaluateJavaScript('''
                      var res = '',
                          divs = document.getElementsByTagName('div');
                      for (var i = 0; i < divs.length; i++) {
                          res = divs[i].textContent;
                          if (res.search('%s') > 1) {
                              break;
                          }
                      }
                      res;
              ''' % username)
                return res

            utils.poll_for_condition(lambda: _userLoggedIn(tab, username),
                                     timeout=20)
