# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from ctypes import c_size_t
import datetime
import json
import logging
import os
import re
import shutil
import time
import six
from six.moves import range
import six.moves.urllib_parse as urlparse

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.client.cros.update_engine import nebraska_wrapper
from autotest_lib.client.cros.update_engine import update_engine_event

_DEFAULT_RUN = utils.run
_DEFAULT_COPY = shutil.copy

class UpdateEngineUtil(object):
    """
    Utility code shared between client and server update_engine autotests.

    All update_engine autotests inherit from either the client or server
    version of update_engine_test:
    client/cros/update_engine/update_engine_test.py
    server/cros/update_engine/update_engine_test.py

    These update_engine_test classes inherit from test and update_engine_util.
    For update_engine_util to work seamlessly, we need the client and server
    update_engine_tests to define _run() and _get_file() functions:
    server side: host.run and host.get_file
    client side: utils.run and shutil.copy

    """

    # Update engine status lines.
    _PROGRESS = 'PROGRESS'
    _CURRENT_OP = 'CURRENT_OP'

    # Source version when we force an update.
    _FORCED_UPDATE = 'ForcedUpdate'

    # update_engine_client command
    _UPDATE_ENGINE_CLIENT_CMD = 'update_engine_client'

    # Update engine statuses.
    _UPDATE_STATUS_IDLE = 'UPDATE_STATUS_IDLE'
    _UPDATE_STATUS_CHECKING_FOR_UPDATE = 'UPDATE_STATUS_CHECKING_FOR_UPDATE'
    _UPDATE_STATUS_UPDATE_AVAILABLE = 'UPDATE_STATUS_UPDATE_AVAILABLE'
    _UPDATE_STATUS_DOWNLOADING = 'UPDATE_STATUS_DOWNLOADING'
    _UPDATE_STATUS_FINALIZING = 'UPDATE_STATUS_FINALIZING'
    _UPDATE_STATUS_UPDATED_NEED_REBOOT = 'UPDATE_STATUS_UPDATED_NEED_REBOOT'
    _UPDATE_STATUS_REPORTING_ERROR_EVENT = 'UPDATE_STATUS_REPORTING_ERROR_EVENT'

    # Files used by the tests.
    _UPDATE_ENGINE_LOG = '/var/log/update_engine.log'
    _UPDATE_ENGINE_LOG_DIR = '/var/log/update_engine/'
    _CUSTOM_LSB_RELEASE = '/mnt/stateful_partition/etc/lsb-release'
    _UPDATE_ENGINE_PREFS_DIR = '/var/lib/update_engine/prefs/'
    _STATEFUL_MOUNT_DIR = '/mnt/stateful_partition/'

    # Update engine prefs
    _UPDATE_CHECK_RESPONSE_HASH = 'update-check-response-hash'

    # Interrupt types supported in AU tests.
    _REBOOT_INTERRUPT = 'reboot'
    _SUSPEND_INTERRUPT = 'suspend'
    _NETWORK_INTERRUPT = 'network'
    _SUPPORTED_INTERRUPTS = [_REBOOT_INTERRUPT, _SUSPEND_INTERRUPT,
                             _NETWORK_INTERRUPT]

    # Public key used to force update_engine to verify omaha response data on
    # test images.
    _IMAGE_PUBLIC_KEY = 'LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUFxZE03Z25kNDNjV2ZRenlydDE2UQpESEUrVDB5eGcxOE9aTys5c2M4aldwakMxekZ0b01Gb2tFU2l1OVRMVXArS1VDMjc0ZitEeElnQWZTQ082VTVECkpGUlBYVXp2ZTF2YVhZZnFsalVCeGMrSlljR2RkNlBDVWw0QXA5ZjAyRGhrckduZi9ya0hPQ0VoRk5wbTUzZG8Kdlo5QTZRNUtCZmNnMUhlUTA4OG9wVmNlUUd0VW1MK2JPTnE1dEx2TkZMVVUwUnUwQW00QURKOFhtdzRycHZxdgptWEphRm1WdWYvR3g3K1RPbmFKdlpUZU9POUFKSzZxNlY4RTcrWlppTUljNUY0RU9zNUFYL2xaZk5PM1JWZ0cyCk83RGh6emErbk96SjNaSkdLNVI0V3daZHVobjlRUllvZ1lQQjBjNjI4NzhxWHBmMkJuM05wVVBpOENmL1JMTU0KbVFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0tCg=='

    # Screenshot names used by interrupt tests
    _BEFORE_INTERRUPT_FILENAME = 'before_interrupt.png'
    _AFTER_INTERRUPT_FILENAME = 'after_interrupt.png'

    # Test name
    _CLIENT_TEST = 'autoupdate_CannedOmahaUpdate'

    # Feature name
    _REPEATED_UPDATES_FEATURE = 'feature-repeated-updates'

    # Credentials to use for the fake login in login tests.
    _LOGIN_TEST_USERNAME = 'autotest'
    _LOGIN_TEST_PASSWORD = 'password'

    def __init__(self, run_func=_DEFAULT_RUN, get_file=_DEFAULT_COPY):
        """
        Initialize this class with _run() and _get_file() functions.

        @param run_func: the function to use to run commands on the client.
                         Defaults for use by client tests, but can be
                         overwritten to run remotely from a server test.
        @param get_file: the function to use when copying a file.  Defaults
                         for use by client tests.  Called with
                         (file, destination) syntax.

        """
        self._set_util_functions(run_func, get_file)


    def _set_util_functions(self, run_func=_DEFAULT_RUN,
                            get_file=_DEFAULT_COPY):
        """See __init__()."""
        self._run = run_func
        self._get_file = get_file


    def _wait_for_progress(self, progress):
        """
        Waits until we reach the percentage passed as the input.

        @param progress: The progress [0.0 .. 1.0] we want to wait for.
        """
        while True:
            completed = self._get_update_progress()
            logging.debug('Checking if %s is greater than %s', completed,
                          progress)
            if completed >= progress:
                break
            time.sleep(1)


    def _is_update_started(self):
        """Checks if the update has started."""
        status = self._get_update_engine_status()
        if status is None:
            return False
        return status[self._CURRENT_OP] in (
            self._UPDATE_STATUS_DOWNLOADING, self._UPDATE_STATUS_FINALIZING)


    def _has_progress_stopped(self):
        """Checks that the update_engine progress has stopped moving."""
        before = self._get_update_engine_status()[self._PROGRESS]
        for i in range(0, 10):
            if before != self._get_update_engine_status()[self._PROGRESS]:
                return False
            time.sleep(1)
        return True


    def _get_update_progress(self):
        """Returns the current payload downloaded progress."""
        while True:
            status = self._get_update_engine_status()
            if not status:
                continue
            if self._is_update_engine_idle(status):
                err_str = self._get_last_error_string()
                raise error.TestFail('Update status was idle while trying to '
                                     'get download status. Last error: %s' %
                                     err_str)
            if self._is_update_engine_reporting_error(status):
                err_str = self._get_last_error_string()
                raise error.TestFail('Update status reported error: %s' %
                                     err_str)
            # When the update has moved to the final stages, update engine
            # displays progress as 0.0  but for our needs we will return 1.0
            if self._is_update_finished_downloading(status):
                return 1.0
            # If we call this right after reboot it may not be downloading yet.
            if status[self._CURRENT_OP] != self._UPDATE_STATUS_DOWNLOADING:
                time.sleep(1)
                continue
            return float(status[self._PROGRESS])


    def _wait_for_update_to_fail(self):
        """Waits for the update to retry until failure."""
        timeout_minutes = 20
        timeout = time.time() + 60 * timeout_minutes
        while True:
            if self._check_update_engine_log_for_entry('Reached max attempts ',
                                                       raise_error=False):
                logging.debug('Found log entry for failed update.')
                if self._is_update_engine_idle():
                    break
            time.sleep(1)
            self._get_update_engine_status()
            if time.time() > timeout:
                raise error.TestFail('Update did not fail as expected. Timeout'
                                     ': %d minutes.' % timeout_minutes)


    def _wait_for_update_to_complete(self, check_kernel_after_update=True):
        """
        Wait for update status to reach NEED_REBOOT.

        @param check_kernel_after_update: True to also check kernel state after
                                          the update.

        """
        self._wait_for_update_status(self._UPDATE_STATUS_UPDATED_NEED_REBOOT)
        if check_kernel_after_update:
            kernel_utils.verify_kernel_state_after_update(
                    self._host if hasattr(self, '_host') else None)


    def _wait_for_update_to_idle(self,
                                 check_kernel_after_update=False,
                                 inactive_kernel=False):
        """
        Wait for update status to reach IDLE.

        @param check_kernel_after_update: True to also verify kernel state after
                                          the update is the expected kernel.
        @param inactive_kernel: True to indicate the expected kernel is the
                                inactive kernel.

        """
        while True:
            status = self._get_update_engine_status()
            if self._is_update_engine_idle(status):
                break
            time.sleep(1)
        if check_kernel_after_update:
            kernel_utils.verify_kernel_state_after_update(
                    self._host if hasattr(self, '_host') else None,
                    inactive_kernel)

    def _wait_for_update_status(self, status_to_wait_for):
        """
        Wait for the update to reach a certain status.

        @param status_to_wait_for: a string of the update status to wait for.

        """
        while True:
            status = self._get_update_engine_status()

            # During reboot, status will be None
            if status is not None:
                if self._is_update_engine_reporting_error(status):
                    err_str = self._get_last_error_string()
                    raise error.TestFail('Update status reported error: %s' %
                                         err_str)
                if self._is_update_engine_idle(status):
                    err_str = self._get_last_error_string()
                    raise error.TestFail('Update status was unexpectedly '
                                         'IDLE when we were waiting for the '
                                         'update to complete: %s' % err_str)
                if status[self._CURRENT_OP] == status_to_wait_for:
                    break
            time.sleep(1)


    def _get_update_engine_status(self, timeout=3600, ignore_timeout=True):
        """
        Gets a dictionary version of update_engine_client --status.

        @param timeout: How long to wait for the status to return.
        @param ignore_timeout: True to throw an exception if timeout occurs.

        @return Dictionary of values within update_engine_client --status.
        @raise: error.AutoservError if command times out

        """
        status = self._run([self._UPDATE_ENGINE_CLIENT_CMD, '--status'],
                           timeout=timeout, ignore_status=True,
                           ignore_timeout=ignore_timeout)

        if status is None:
            return None
        logging.info(status)
        if status.exit_status != 0:
            return None
        status_dict = {}
        for line in status.stdout.splitlines():
            entry = line.partition('=')
            status_dict[entry[0]] = entry[2]
        return status_dict


    def _check_update_engine_log_for_entry(self,
                                           entry,
                                           raise_error=False,
                                           err_str=None,
                                           min_count=1,
                                           update_engine_log=None):
        """
        Checks for entries in the update_engine log.

        @param entry: String or tuple of strings to search for.
        @param raise_error: Fails tests if log doesn't contain entry.
        @param err_str: The error string to raise if we cannot find entry.
        @param min_count: The minimum number of times each item should be
                          found in the log. Default one.
        @param update_engine_log: Update engine log string you want to
                                  search. If None, we will read from the
                                  current update engine log.

        @return Boolean if the update engine log contains the entry.

        """
        if isinstance(entry, str):
            # Create a tuple of strings so we can iterate over it.
            entry = (entry,)

        if not update_engine_log:
            update_engine_log = self._get_update_engine_log()

        if all(update_engine_log.count(msg) >= min_count for msg in entry):
            return True

        if not raise_error:
            return False

        error_str = ('Did not find expected string(s) in update_engine log: '
                     '%s' % entry)
        logging.debug(error_str)
        raise error.TestFail(err_str if err_str else error_str)


    def _set_feature(self, feature_name, enable=True):
        """
        Enables or disables feature from update engine client.
        @param feature_name: Name of the feature to enable or disable
        @param enable: Enables feature if true, disables if false.
                       Default True.
        """
        if not enable:
            feature_request = '--disable_feature=' + feature_name
        else:
            feature_request = '--enable_feature=' + feature_name
        self._run([self._UPDATE_ENGINE_CLIENT_CMD, feature_request])


    def _is_update_finished_downloading(self, status=None):
        """
        Checks if the update has moved to the final stages.

        @param status: Output of _get_update_engine_status(). If None that
                       function will be called here first.

        """
        if status is None:
            status = self._get_update_engine_status()
        return status[self._CURRENT_OP] in [
            self._UPDATE_STATUS_FINALIZING,
            self._UPDATE_STATUS_UPDATED_NEED_REBOOT]


    def _is_update_engine_idle(self, status=None):
        """
        Checks if the update engine is idle.

        @param status: Output of _get_update_engine_status(). If None that
                       function will be called here first.

        """
        if status is None:
            status = self._get_update_engine_status()
        return status[self._CURRENT_OP] == self._UPDATE_STATUS_IDLE


    def _is_checking_for_update(self, status=None):
        """
        Checks if the update status is still checking for an update.

        @param status: Output of _get_update_engine_status(). If None that
                       function will be called here first.

        """
        if status is None:
            status = self._get_update_engine_status()
        return status[self._CURRENT_OP] in (
            self._UPDATE_STATUS_CHECKING_FOR_UPDATE,
            self._UPDATE_STATUS_UPDATE_AVAILABLE)


    def _is_update_engine_reporting_error(self, status=None):
        """
        Checks if the update engine status reported an error.

        @param status: Output of _get_update_engine_status(). If None that
                       function will be called here first.

        """
        if status is None:
            status = self._get_update_engine_status()
        return (status[self._CURRENT_OP] ==
                self._UPDATE_STATUS_REPORTING_ERROR_EVENT)


    def _update_continued_where_it_left_off(self, progress,
                                            reboot_interrupt=False):
        """
        Checks that the update did not restart after an interruption.

        When testing a reboot interrupt we can do additional checks on the
        logs before and after reboot to see if the update resumed.

        @param progress: The progress the last time we checked.
        @param reboot_interrupt: True if we are doing a reboot interrupt test.

        @returns True if update continued. False if update restarted.

        """
        completed = self._get_update_progress()
        logging.info('New value: %f, old value: %f', completed, progress)
        if completed >= progress:
            return True

        # Sometimes update_engine will continue an update but the first reported
        # progress won't be correct. So check the logs for resume info.
        if not reboot_interrupt or not self._check_update_engine_log_for_entry(
            'Resuming an update that was previously started'):
            return False

        # Find the reported Completed and Resumed progress.
        pattern = ('(.*)/(.*) operations \((.*)%\), (.*)/(.*) bytes downloaded'
                   ' \((.*)%\), overall progress (.*)%')
        before_pattern = 'Completed %s' % pattern
        before_log = self._get_update_engine_log(r_index=1)
        before_match = re.findall(before_pattern, before_log)[-1]
        after_pattern = 'Resuming after %s' % pattern
        after_log = self._get_update_engine_log(r_index=0)
        after_match = re.findall(after_pattern, after_log)[0]
        logging.debug('Progress before interrupt: %s', before_match)
        logging.debug('Progress after interrupt: %s', after_match)

        # Check the Resuming progress is greater than Completed progress.
        for i in range(0, len(before_match)):
            logging.debug('Comparing %d and %d', int(before_match[i]),
                          int(after_match[i]))
            if int(before_match[i]) > int(after_match[i]):
                return False
        return True


    def _append_query_to_url(self, url, query_dict):
        """
        Appends the dictionary kwargs to the URL url as query string.

        This function will replace the already existing query strings in url
        with the ones in the input dictionary. I also removes keys that have
        a None value.

        @param url: The given input URL.
        @param query_dicl: A dictionary of key/values to be converted to query
                           string.
        @return: The same input URL url but with new query string items added.

        """
        # TODO(ahassani): This doesn't work (or maybe should not) for queries
        # with multiple values for a specific key.
        parsed_url = list(urlparse.urlsplit(url))
        parsed_query = urlparse.parse_qs(parsed_url[3])
        for k, v in query_dict.items():
            parsed_query[k] = [v]
        parsed_url[3] = '&'.join(
            '%s=%s' % (k, v[0]) for k, v in parsed_query.items()
            if v[0] is not None)
        return urlparse.urlunsplit(parsed_url)


    def _check_for_update(self, update_url, interactive=True,
                          wait_for_completion=False,
                          check_kernel_after_update=True, **kwargs):
        """
        Starts a background update check.

        @param update_url: The URL to get an update from.
        @param interactive: True if we are doing an interactive update.
        @param wait_for_completion: True for --update, False for
                --check_for_update.
        @param check_kernel_after_update: True to check kernel state after a
                successful update. False to skip. wait_for_completion must also
                be True.
        @param kwargs: The dictionary to be converted to a query string and
                appended to the end of the update URL. e.g:
                {'critical_update': True, 'foo': 'bar'} ->
                'http:/127.0.0.1:8080/update?critical_update=True&foo=bar' Look
                at nebraska.py or devserver.py for the list of accepted
                values. If there was already query string in update_url, it will
                append the new values and override the old ones.

        """
        update_url = self._append_query_to_url(update_url, kwargs)
        cmd = [self._UPDATE_ENGINE_CLIENT_CMD,
               '--update' if wait_for_completion else '--check_for_update',
               '--omaha_url=%s' % update_url]

        if not interactive:
            cmd.append('--interactive=false')
        self._run(cmd, ignore_status=False)
        if wait_for_completion and check_kernel_after_update:
            kernel_utils.verify_kernel_state_after_update(
                self._host if hasattr(self, '_host') else None)


    def _rollback(self, powerwash=False):
        """
        Perform a rollback of rootfs.

        @param powerwash: True to powerwash along with the rollback.

        """
        cmd = [self._UPDATE_ENGINE_CLIENT_CMD, '--rollback', '--follow']
        if not powerwash:
            cmd.append('--nopowerwash')
        logging.info('Performing rollback with cmd: %s.', cmd)
        self._run(cmd)
        kernel_utils.verify_kernel_state_after_update(self._host)


    def _restart_update_engine(self, ignore_status=False):
        """
        Restarts update-engine.

        @param ignore_status: True to not raise exception on command failure.

        """
        self._run(['restart', 'update-engine'], ignore_status=ignore_status)


    def _save_extra_update_engine_logs(self, number_of_logs):
        """
        Get the last X number of update_engine logs on the DUT.

        @param number_of_logs: The number of logs to save.

        """
        files = self._get_update_engine_logs()

        for i in range(number_of_logs if number_of_logs <= len(files) else
                       len(files)):
            file = os.path.join(self._UPDATE_ENGINE_LOG_DIR, files[i])
            self._get_file(file, self.resultsdir)


    def _get_update_engine_logs(self, timeout=3600, ignore_timeout=True):
        """
        Helper function to return the list of files in /var/log/update_engine/.

        @param timeout: How many seconds to wait for command to complete.
        @param ignore_timeout: True if we should not throw an error on timeout.

        """
        cmd = ['ls', '-t', '-1', self._UPDATE_ENGINE_LOG_DIR]
        return self._run(cmd, timeout=timeout,
                         ignore_timeout=ignore_timeout).stdout.splitlines()


    def _get_update_engine_log(self, r_index=0, timeout=3600,
                               ignore_timeout=True):
        """
        Returns the last r_index'th update_engine log.

        @param r_index: The index of the last r_index'th update_engine log
                in order they were created. For example:
                  0 -> last one.
                  1 -> second to last one.
        @param timeout: How many seconds to wait for command to complete.
        @param ignore_timeout: True if we should not throw an error on timeout.

        """
        files = self._get_update_engine_logs()
        log_file = os.path.join(self._UPDATE_ENGINE_LOG_DIR, files[r_index])
        return self._run(['cat', log_file]).stdout


    def _create_custom_lsb_release(self, update_url, build='0.0.0.0', **kwargs):
        """
        Create a custom lsb-release file.

        In order to tell OOBE to ping a different update server than the
        default we need to create our own lsb-release. We will include a
        deserver update URL.

        @param update_url: String of url to use for update check.
        @param build: String of the build number to use. Represents the
                      ChromeOS build this device thinks it is on.
        @param kwargs: A dictionary of key/values to be made into a query string
                       and appended to the update_url

        """
        update_url = self._append_query_to_url(update_url, kwargs)
        release_version = 'CHROMEOS_RELEASE_VERSION=%s' % build
        auserver = 'CHROMEOS_AUSERVER=%s' % update_url

        self._run(['mkdir', os.path.dirname(self._CUSTOM_LSB_RELEASE)],
                  ignore_status=True)
        self._run(['touch', self._CUSTOM_LSB_RELEASE])
        self._run(['echo', release_version, '>', self._CUSTOM_LSB_RELEASE])
        self._run(['echo', auserver, '>>', self._CUSTOM_LSB_RELEASE])

        # Confirm the custom lsb-release file was created successfully.
        def custom_lsb_created():
            """
            Checks if the custom lsb-release file exists and has the correct
            contents.

            @returns: True if the file exists with the expected contents
                      False otherwise
            """
            contents = self._run(['cat', self._CUSTOM_LSB_RELEASE]).stdout
            return auserver in contents and release_version in contents

        utils.poll_for_condition(condition=custom_lsb_created)


    def _clear_custom_lsb_release(self):
        """
        Delete the custom release file, if any.

        Intended to clear work done by _create_custom_lsb_release().

        """
        self._run(['rm', self._CUSTOM_LSB_RELEASE], ignore_status=True)


    def _remove_update_engine_pref(self, pref, is_dir=False):
        """
        Delete an update_engine pref file or directory.

        @param pref: The pref file to delete
        @param is_dir: True for removing a whole pref subdirectory.

        """
        pref_file = os.path.join(self._UPDATE_ENGINE_PREFS_DIR, pref)
        self._run(['rm', '-r' if is_dir else '', pref_file],
                  ignore_status=True)

    def _create_update_engine_pref(self, pref_name, pref_val="", sub_dir=None):
        """
        Create an update_engine pref file.

        @param pref_name: The name of pref file to create.
        @param pref_val: The content string in pref file.
        @param sub_dir: The sub directory for the pref.

        """
        pref_dir = self._UPDATE_ENGINE_PREFS_DIR
        if sub_dir:
            pref_dir = os.path.join(pref_dir, sub_dir)
            self._run(['mkdir', '-p', pref_dir], ignore_status=True)

        pref_file = os.path.join(pref_dir, pref_name)
        self._run(['echo', '-n', pref_val, '>', pref_file])

    def _get_update_requests(self):
        """
        Get the contents of all the update requests from the most recent log.

        @returns: a sequential list of <request> xml blocks or None if none.

        """
        update_log = self._get_update_engine_log()

        # Matches <request ... /request>.  The match can be on multiple
        # lines and the search is not greedy so it only matches one block.
        return re.findall(r'<request.*?/request>', update_log, re.DOTALL)


    def _get_time_of_last_update_request(self):
        """
        Get the time of the last update request from most recent logfile.

        @returns: seconds since epoch of when last update request happened
                  (second accuracy), or None if no such timestamp exists.

        """
        update_log = self._get_update_engine_log()

        # Matches any line with "YYYY-MM-DDTHH:MM:SS ... Request ... xml",
        # e.g.
        # "2021-01-28T10:14:33.998217Z INFO update_engine: \
        # [omaha_request_action.cc(794)] Request: <?xml"
        pattern = r'(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}).* Request:.*xml'
        LOG_TIMESTAMP_FORMAT = '%Y-%m-%dT%H:%M:%S'

        result = re.findall(pattern, update_log)

        if result:
            match = result[-1]
            log_datetime = datetime.datetime.strptime(match,
                                                      LOG_TIMESTAMP_FORMAT)
            epoch = datetime.datetime(1970, 1, 1)

            # Since log_datetime is in UTC, simply take the diff from epoch.
            return (log_datetime - epoch).total_seconds()
        else:
            # If no match for new timestamp, try old timestamp format.
            # "[0723/133526:INFO:omaha_request_action.cc(794)] Request: <?xml".
            pattern_old = r'([0-9]{4}/[0-9]{6}).*Request.*xml'
            LOG_TIMESTAMP_FORMAT_OLD = '%m%d/%H%M%S'

            result = re.findall(pattern_old, update_log)
            if not result:
                return None

            match = result[-1]

            # The old format does not include the year, so set it as this year.
            # This could cause incorrect behavior, but is unlikely to.
            current_year = datetime.datetime.now().year
            log_datetime = datetime.datetime.strptime(
                    match, LOG_TIMESTAMP_FORMAT_OLD)
            log_datetime = log_datetime.replace(year=current_year)

            return time.mktime(log_datetime.timetuple())


    def _take_screenshot(self, filename):
        """
        Take a screenshot and save in resultsdir.

        @param filename: The name of the file to save

        """
        try:
            file_location = os.path.join('/tmp', filename)
            self._run(['screenshot', file_location])
            self._get_file(file_location, self.resultsdir)
        except (error.AutoservRunError, error.CmdError):
            logging.exception('Failed to take screenshot.')


    def _remove_screenshots(self):
        """Remove screenshots taken by interrupt tests."""
        for file in [self._BEFORE_INTERRUPT_FILENAME,
                     self._AFTER_INTERRUPT_FILENAME]:
            file_location = os.path.join(self.resultsdir, file)
            if os.path.exists(file_location):
                try:
                    os.remove(file_location)
                except Exception as e:
                    logging.exception('Failed to remove %s', file_location)


    def _get_last_error_string(self):
        """
        Gets the last error message in the update engine log.

        @returns: The error message.

        """
        err_str = 'Updating payload state for error code: '
        log = self._get_update_engine_log().splitlines()
        targets = [line for line in log if err_str in line]
        logging.debug('Error lines found: %s', targets)
        if not targets:
            return None
        else:
            return targets[-1].rpartition(err_str)[2]


    def _get_latest_initial_request(self):
        """
        Return the most recent initial update request.

        AU requests occur in a chain of messages back and forth, e.g. the
        initial request for an update -> the reply with the update -> the
        report that install has started -> report that install has finished,
        etc.  This function finds the first request in the latest such chain.

        This message has no eventtype listed, or is rebooted_after_update
        type (as an artifact from a previous update since this one).
        Subsequent messages in the chain have different eventtype values.

        @returns: string of the entire update request or None.

        """
        requests = self._get_update_requests()
        if not requests:
            return None

        MATCH_STR = r'eventtype="(.*?)"'
        for i in range(len(requests) - 1, -1, -1):
            search = re.search(MATCH_STR, requests[i])
            if (not search or
                (search.group(1) ==
                 str(update_engine_event.EVENT_TYPE_REBOOTED_AFTER_UPDATE))):
                return requests[i]

        return None

    def _edit_nebraska_startup_config(self, **kwargs):
        """
        Edits an existing nebraska startup config file.

        @param kwargs: A dictionary of key/values for nebraska config options.
                       See platform/dev/nebraska/nebraska.py for more info.

        """
        conf = json.loads(
                self._run(['cat', nebraska_wrapper.NEBRASKA_CONFIG]).stdout)
        for k, v in six.iteritems(kwargs):
            conf[k] = v
        self._run([
                'echo',
                json.dumps(conf), '>', nebraska_wrapper.NEBRASKA_CONFIG
        ])

    def _clear_nebraska_dir(self):
        """
        Clears the nebraska dir on the DUT where the nebraska config and payload
        metadata files are stored.

        """
        self._run(['rm', '-rf', '/usr/local/nebraska'])

    def _get_nebraska_update_url(self):
        """
        Gets the update URL for an active nebraska server. Assumes nebraska is
        up and running.

        @returns: string of the update URL for the active nebraska.

        """
        nebraska_port = self._run(['cat', '/run/nebraska/port']).stdout
        return 'http://localhost:%s/update' % nebraska_port

    def _get_exclusion_name(self, payload_url):
        """
        Get the exclusion name of a payload url by calculating its hash in the
        same way of base::StringPieceHash in libchrome.

        @param payload_url: The payload url to be excluded.

        @returns: The payload URL hash string as the exclusion name.

        """
        result = c_size_t(0)
        for c in payload_url:
            result = c_size_t((result.value * 131) + ord(c))
        return str(result.value)
