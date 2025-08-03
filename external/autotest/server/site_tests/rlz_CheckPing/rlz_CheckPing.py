# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import datetime

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.client.common_lib.cros import vpd_utils
from autotest_lib.server import autotest
from autotest_lib.server import test


class rlz_CheckPing(test.test):
    """
    Tests if we are sending RLZ install (CAI) and first-use (CAF) pings in a
    variety of different circumstances. Refer to the TestTracker RLZ test plan
    for more information:
    https://testtracker.googleplex.com/testplans/details/25301

    """
    version = 1

    _CLIENT_TEST = 'desktopui_CheckRlzPingSent'
    _PING_VPD = 'should_send_rlz_ping'
    _DATE_VPD = 'rlz_embargo_end_date'

    def _check_rlz_brand_code(self):
        """Checks that we have an rlz brand code."""
        try:
            self._host.run('cros_config / brand-code')
        except error.AutoservRunError as e:
            raise error.TestFail('DUT is missing brand_code: %s.' %
                                 e.result_obj.stderr)


    def _set_vpd_values(self, should_send_rlz_ping, rlz_embargo_end_date):
        """
        Sets the required vpd values for the test.

        @param should_send_rlz_ping: Value to set should_send_rlz_ping. 1 to
                                     send the ping, 0 to not send it, None to
                                     clear it from the VPD.
        @param embargo_date: Date for rlz_embargo_end_date value. This should
                             be a datetime.date object with the desired date
                             for rlz_embargo_end_date, or None to clear it and
                             proceed with rlz_embargo_end_date not set.

        """
        to_set = {}
        if should_send_rlz_ping is not None:
            to_set[self._PING_VPD] = should_send_rlz_ping
        else:
            vpd_utils.vpd_delete(host=self._host, key=self._PING_VPD,
                                 dump=True, force_dump=True)

        if rlz_embargo_end_date:
            to_set[self._DATE_VPD] = rlz_embargo_end_date.isoformat()
        else:
            vpd_utils.vpd_delete(host=self._host, key=self._DATE_VPD,
                                 dump=True, force_dump=True)

        if to_set:
            vpd_utils.vpd_set(host=self._host, vpd_dict=to_set, dump=True,
                              force_dump=True)


    def _check_rlz_vpd_settings_post_ping(self, should_send_rlz_ping,
                                          rlz_embargo_end_date):
        """
        Checks that rlz related vpd settings are correct after the test.
        In the typical case where the first-use event (CAF) ping is sent
        successfully, both should_send_rlz_ping and rlz_embargo_end_date VPD
        values should be cleared. If the CAF ping is not sent, they will
        remain unchanged.

        @param should_send_rlz_ping: Expected value for the
                                     should_send_rlz_ping VPD setting
                                     (0 or 1). If None, we expect no setting
                                     to be present.
        @param rlz_embargo_end_date: Expected value of the
                                     rlz_embargo_end_date VPD setting.
                                     This argument should be None if expecting
                                     the value to be cleared, and a
                                     datetime.date object containing the
                                     expected date otherwise.

        """
        check_should_send_ping = vpd_utils.vpd_get(host=self._host,
                                                   key=self._PING_VPD)
        if check_should_send_ping is not None:
            check_should_send_ping = int(check_should_send_ping)

        check_date = vpd_utils.vpd_get(host=self._host,
                                       key=self._DATE_VPD)

        if rlz_embargo_end_date:
            rlz_embargo_end_date = rlz_embargo_end_date.isoformat()

        if check_date != rlz_embargo_end_date:
            raise error.TestFail('Unexpected value for rlz_embargo_end_date:'
                                 ' %s' % check_date)

        if check_should_send_ping != should_send_rlz_ping:
            raise error.TestFail('Unexpected value for should_send_rlz_ping:'
                                 ' %s' % check_should_send_ping)


    def run_once(self, host, ping_timeout=30, pre_login=None,
                 rlz_embargo_end_date=None, should_send_rlz_ping=1,
                 check_ping_not_resent=False, reboot=False):
        """
        Configures the DUT to send RLZ pings. Checks for the RLZ client
        install (CAI) and first-use (CAF) pings.

        @param host: Host to run test on.
        @param ping_timeout: Delay time (seconds) before both CAI and CAF
                             pings are sent.
        @param pre_login: Whether or not to login before the main RLZ ping
                          test, and for how long. Should be one of
                          ['lock', 'no_lock', None]. 'lock' is meant for guest
                          mode testing, where a non-guest user must login to
                          'lock' the device for RLZ before the ping can be
                          sent in guest mode. 'no_lock' is to log in with a
                          different user account and log out immediately to
                          ensure no ping is sent. Used to verify that the ping
                          can be sent from subsequent user logins if it has
                          not already been sent.
        @param rlz_embargo_end_date: Date for rlz_embargo_end_date VPD
                                     setting. If None, no value will be set
                                     for rlz_embargo_end_date, and any
                                     existing value will be cleared. For a
                                     specific rlz_embargo_end_date, this
                                     argument should be a datetime.date
                                     object containing the desired date.
        @param should_send_rlz_ping: Value of the should_send_rlz_ping VPD
                                     setting. 1 to send the first-use (CAF)
                                     ping, 0 to not send it. The ping should
                                     not be sent when the VPD value is 0,
                                     which in the normal use-case occurs after
                                     the CAF ping has been sent. It is set to
                                     0 after the CAF ping to ensure it is not
                                     sent again in the device's lifetime.
                                     This argument can also be None, in which
                                     case the should_send_rlz_ping VPD setting
                                     will be cleared. No CAF ping should be
                                     sent in this case, either.
        @param check_ping_not_resent: True to perform a second RLZ check with
                                      a different user account for tests that
                                      confirm the first-use (CAF) ping is not
                                      resent. The second check assumes the CAF
                                      ping was sent successfully, so the
                                      should_send_rlz_ping and
                                      rlz_embargo_end_date parameters should
                                      be a combination that ensures it was
                                      sent.
        @param reboot: True to reboot after the first RLZ check.

        """
        self._host = host
        self._check_rlz_brand_code()

        # Set VPD settings that control if the DUT will send a first-use ping.
        self._set_vpd_values(should_send_rlz_ping=should_send_rlz_ping,
                             rlz_embargo_end_date=rlz_embargo_end_date)
        # Clear TPM owner so we have no users on DUT.
        tpm_utils.ClearTPMOwnerRequest(self._host)

        # We expect first-use (CAF) ping to be sent when:
        # 1. should_send_rlz_ping exists and is 1
        # 2. rlz_embargo_end_date is missing or in the past
        expect_caf = bool(should_send_rlz_ping)
        if rlz_embargo_end_date:
            expect_caf = (expect_caf and
                (datetime.date.today() - rlz_embargo_end_date).days > 0)

        # Login, do a Google search, verify events in RLZ Data file.
        client_at = autotest.Autotest(self._host)
        client_at.run_test(self._CLIENT_TEST, ping_timeout=ping_timeout,
                           expect_caf_ping=expect_caf, pre_login=pre_login,
                           pre_login_username='rlz_user')
        client_at._check_client_test_result(self._host, self._CLIENT_TEST)

        if expect_caf:
            self._check_rlz_vpd_settings_post_ping(
                should_send_rlz_ping=0, rlz_embargo_end_date=None)
        else:
            self._check_rlz_vpd_settings_post_ping(
                should_send_rlz_ping=should_send_rlz_ping,
                rlz_embargo_end_date=rlz_embargo_end_date)

        if reboot:
            self._host.reboot()

        # Log in with another user and verify CAF ping is not resent. This
        # portion of the test assumes a successful run above where expect_caf
        # was True.
        if check_ping_not_resent:
            client_at.run_test(self._CLIENT_TEST, ping_timeout=ping_timeout,
                               expect_caf_ping=False, username='rlz_user',
                               tag="check_ping_not_resent")

            # Confirm VPD settings are also unchanged
            self._check_rlz_vpd_settings_post_ping(should_send_rlz_ping=0,
                                                   rlz_embargo_end_date=None)
