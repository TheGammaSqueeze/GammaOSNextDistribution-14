# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import re

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server.cros.dynamic_suite import tools
from autotest_lib.server.cros.update_engine import update_engine_test
from autotest_lib.utils.frozen_chromite.lib import retry_util

class autoupdate_P2P(update_engine_test.UpdateEngineTest):
    """Tests a peer to peer (P2P) autoupdate."""

    version = 1

    _CURRENT_RESPONSE_SIGNATURE_PREF = 'current-response-signature'
    _CURRENT_URL_INDEX_PREF = 'current-url-index'
    _P2P_FIRST_ATTEMPT_TIMESTAMP_PREF = 'p2p-first-attempt-timestamp'
    _P2P_NUM_ATTEMPTS_PREF = 'p2p-num-attempts'

    _CLIENT_TEST_WITH_DLC = 'autoupdate_InstallAndUpdateDLC'
    _CLIENT_TEST = 'autoupdate_CannedOmahaUpdate'

    def cleanup(self):
        logging.info('Disabling p2p_update on hosts.')
        for host in self._hosts:
            try:
                cmd = [self._UPDATE_ENGINE_CLIENT_CMD, '--p2p_update=no']
                retry_util.RetryException(error.AutoservRunError, 2, host.run,
                                          cmd)
            except Exception:
                logging.info('Failed to disable P2P in cleanup.')
        super(autoupdate_P2P, self).cleanup()


    def _cleanup_dlcs(self):
        """Remove all DLCs on the DUT before starting the test. """
        installed = self._dlc_util.list().keys()
        for dlc_id in installed:
            self._dlc_util.purge(dlc_id)
        # DLCs may be present but not mounted, so they won't be purged above.
        self._dlc_util.purge(self._dlc_util._SAMPLE_DLC_ID, ignore_status=True)

    def _enable_p2p_update_on_hosts(self):
        """Turn on the option to enable p2p updating on both DUTs."""
        logging.info('Enabling p2p_update on hosts.')
        for host in self._hosts:
            try:
                cmd = [self._UPDATE_ENGINE_CLIENT_CMD, '--p2p_update=yes']
                retry_util.RetryException(error.AutoservRunError, 2, host.run,
                                          cmd)
            except Exception:
                raise error.TestFail('Failed to enable p2p on %s' % host)


    def _setup_second_hosts_prefs(self):
        """The second DUT needs to be setup for the test."""
        num_attempts = os.path.join(self._UPDATE_ENGINE_PREFS_DIR,
                                    self._P2P_NUM_ATTEMPTS_PREF)
        if self._too_many_attempts:
            self._hosts[1].run('echo 11 > %s' % num_attempts)
        else:
            self._hosts[1].run('rm %s' % num_attempts, ignore_status=True)

        first_attempt = os.path.join(self._UPDATE_ENGINE_PREFS_DIR,
                                     self._P2P_FIRST_ATTEMPT_TIMESTAMP_PREF)
        if self._deadline_expired:
            self._hosts[1].run('echo 1 > %s' % first_attempt)
        else:
            self._hosts[1].run('rm %s' % first_attempt, ignore_status=True)


    def _copy_payload_signature_between_hosts(self):
        """
        Copies the current-payload-signature between hosts.

        We copy the pref file from host one (that updated normally) to host two
        (that will be updating via p2p). We do this because otherwise host two
        would have to actually update and fail in order to get itself into
        the error states (deadline expired and too many attempts).

        """
        pref_file = os.path.join(self._UPDATE_ENGINE_PREFS_DIR,
                                 self._CURRENT_RESPONSE_SIGNATURE_PREF)
        self._hosts[0].get_file(pref_file, self.resultsdir)
        result_pref_file = os.path.join(self.resultsdir,
                                        self._CURRENT_RESPONSE_SIGNATURE_PREF)
        self._hosts[1].send_file(result_pref_file,
                                 self._UPDATE_ENGINE_PREFS_DIR)


    def _reset_current_url_index(self):
        """
        Reset current-url-index pref to 0.

        Since we are copying the state from one DUT to the other we also need to
        reset the current url index or UE will reset all of its state.

        """
        current_url_index = os.path.join(self._UPDATE_ENGINE_PREFS_DIR,
                                         self._CURRENT_URL_INDEX_PREF)

        self._hosts[1].run('echo 0 > %s' % current_url_index)


    def _update_dut(self, host, tag, interactive=True):
        """
        Update the first DUT normally and save the update engine logs.

        @param host: the host object for the first DUT.
        @param interactive: Whether the update should be interactive.

        """
        self._host = host
        self._set_active_p2p_host(host)
        self._dlc_util.set_run(self._host.run)
        if self._with_dlc:
            self._cleanup_dlcs()
        self._host.reboot()
        # Sometimes update request is lost if checking right after reboot so
        # make sure update_engine is ready.
        utils.poll_for_condition(condition=self._is_update_engine_idle,
                                 desc='Waiting for update engine idle')

        logging.info('Updating %s (%s).', host, tag)
        if self._with_dlc:
            self._run_client_test_and_check_result(
                    self._CLIENT_TEST_WITH_DLC,
                    payload_urls=self._payload_urls,
                    tag=tag,
                    interactive=interactive)
        else:
            self._run_client_test_and_check_result(
                    self._CLIENT_TEST,
                    payload_url=self._payload_urls[0],
                    tag=tag,
                    interactive=interactive)
        update_engine_log = self._get_update_engine_log()
        host.reboot()
        return update_engine_log
        # TODO(ahassani): There is probably a race condition here. We should
        # wait after the reboot to make sure p2p is up before kicking off the
        # second host's update check. Otherwise, the second one is not going to
        # use p2p updates. Another solution would be to not reboot here at all
        # since p2p server is still running.


    def _check_p2p_still_enabled(self, host):
        """
        Check that updating has not affected P2P status.

        @param host: The host that we just updated.

        """
        logging.info('Checking that p2p is still enabled after update.')
        def _is_p2p_enabled():
            p2p = host.run([self._UPDATE_ENGINE_CLIENT_CMD,
                            '--show_p2p_update'], ignore_status=True)
            if p2p.stderr is not None and 'ENABLED' in p2p.stderr:
                return True
            else:
                return False

        err = 'P2P was disabled after the first DUT was updated. This is not ' \
              'expected. Something probably went wrong with the update.'

        utils.poll_for_condition(_is_p2p_enabled,
                                 exception=error.TestFail(err))


    def _check_for_p2p_entries_in_update_log(self, update_engine_log):
        """
        Ensure that the second DUT actually updated via P2P.

        We will check the update_engine log for entries that tell us that the
        update was done via P2P.

        @param update_engine_log: the update engine log for the p2p update.

        """
        logging.info('Making sure we have p2p entries in update engine log.')
        line1 = "Checking if payload is available via p2p, file_id=" \
                "cros_update_size_(.*)_hash_(.*)"
        line2 = "Lookup complete, p2p-client returned URL " \
                "'http://(.*)/cros_update_size_(.*)_hash_(.*).cros_au'"
        line3 = "Replacing URL (.*) with local URL " \
                "http://(.*)/cros_update_size_(.*)_hash_(.*).cros_au " \
                "since p2p is enabled."
        errline = "Forcibly disabling use of p2p for downloading because no " \
                  "suitable peer could be found."
        too_many_attempts_err_str = "Forcibly disabling use of p2p for " \
                                    "downloading because of previous " \
                                    "failures when using p2p."

        if re.compile(errline).search(update_engine_log) is not None:
            raise error.TestFail('P2P update was disabled because no suitable '
                                 'peer DUT was found.')
        if self._too_many_attempts or self._deadline_expired:
            ue = re.compile(too_many_attempts_err_str)
            if ue.search(update_engine_log) is None:
                raise error.TestFail('We expected update_engine to complain '
                                     'that there were too many p2p attempts '
                                     'but it did not. Check the logs.')
            return
        for line in [line1, line2, line3]:
            ue = re.compile(line)
            if ue.search(update_engine_log) is None:
                raise error.TestFail('We did not find p2p string "%s" in the '
                                     'update_engine log for the second host. '
                                     'Please check the update_engine logs in '
                                     'the results directory.' % line)


    def _get_build_from_job_repo_url(self, host):
        """
        Gets the build string from a hosts job_repo_url.

        @param host: Object representing host.

        """
        info = host.host_info_store.get()
        repo_url = info.attributes.get(host.job_repo_url_attribute, '')
        if not repo_url:
            raise error.TestFail('There was no job_repo_url for %s so we '
                                 'cant get a payload to use.' % host.hostname)
        return tools.get_devserver_build_from_package_url(repo_url)


    def _verify_hosts(self, job_repo_url):
        """
        Ensure that the hosts scheduled for the test are valid.

        @param job_repo_url: URL to work out the current build.

        """
        lab1 = self._hosts[0].hostname.partition('-')[0]
        lab2 = self._hosts[1].hostname.partition('-')[0]
        if lab1 != lab2:
            raise error.TestNAError('Test was given DUTs in different labs so '
                                    'P2P will not work. See crbug.com/807495.')

        logging.info('Making sure hosts can ping each other.')
        result = self._hosts[1].run('ping -c5 %s' % self._hosts[0].ip,
                                    ignore_status=True)
        logging.debug('Ping status: %s', result)
        if result.exit_status != 0:
            raise error.TestFail('Devices failed to ping each other.')
        # Get the current build. e.g samus-release/R65-10200.0.0
        if job_repo_url is None:
            logging.info('Making sure hosts have the same build.')
            _, build1 = self._get_build_from_job_repo_url(self._hosts[0])
            _, build2 = self._get_build_from_job_repo_url(self._hosts[1])
            if build1 != build2:
                raise error.TestFail('The builds on the hosts did not match. '
                                     'Host one: %s, Host two: %s' % (build1,
                                                                     build2))


    def run_once(self,
                 companions,
                 job_repo_url=None,
                 too_many_attempts=False,
                 deadline_expired=False,
                 with_dlc=False,
                 running_at_desk=False):
        """
        Testing autoupdate via P2P.

        @param companions: List of other DUTs used in the test.
        @param job_repo_url: A url linking to autotest packages.
        @param too_many_attempts: True to test what happens with too many
                                  failed update attempts.
        @param deadline_expired: True to test what happens when the deadline
                                 between peers has expired
        @param with_dlc: Whether to include sample-dlc in the test.
        @param running_at_desk: True to stage files on public bucket. Useful
                                for debugging locally.

        """
        self._hosts = [self._host, companions[0]]
        logging.info('Hosts for this test: %s', self._hosts)

        self._too_many_attempts = too_many_attempts
        self._deadline_expired = deadline_expired
        self._with_dlc = with_dlc

        self._verify_hosts(job_repo_url)
        self._enable_p2p_update_on_hosts()
        self._setup_second_hosts_prefs()

        # Get an N-to-N delta payload update url to use for the test. P2P
        # updates are very slow so we will only update with a delta payload. In
        # addition we need the full DLC payload so we can perform its install.
        self._payload_urls = [
                self.get_payload_for_nebraska(job_repo_url,
                                              full_payload=False,
                                              public_bucket=running_at_desk)
        ]
        if self._with_dlc:
            self._payload_urls += [
                    self.get_payload_for_nebraska(
                            job_repo_url=job_repo_url,
                            full_payload=True,
                            payload_type=self._PAYLOAD_TYPE.DLC,
                            public_bucket=running_at_desk),
                    self.get_payload_for_nebraska(
                            job_repo_url=job_repo_url,
                            full_payload=False,
                            payload_type=self._PAYLOAD_TYPE.DLC,
                            public_bucket=running_at_desk)
            ]

        # The first device just updates normally.
        self._update_dut(self._hosts[0], 'host1')
        self._check_p2p_still_enabled(self._hosts[0])

        if too_many_attempts or deadline_expired:
            self._copy_payload_signature_between_hosts()
            self._reset_current_url_index()

        # Update the 2nd DUT with the delta payload via P2P from the 1st DUT.
        update_engine_log = self._update_dut(self._hosts[1],
                                             'host2',
                                             interactive=False)
        self._check_for_p2p_entries_in_update_log(update_engine_log)
