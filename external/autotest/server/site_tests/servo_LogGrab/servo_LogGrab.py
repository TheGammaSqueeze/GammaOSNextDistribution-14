# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Test to validate servo.py log grabbing works."""

import logging
import os
import re
import tempfile
import shutil

from autotest_lib.client.common_lib import error
from autotest_lib.server import test

class servo_LogGrab(test.test):
    """Ensure all logs are grabbed and glued together."""
    version = 1

    # Regex used to extract log-record timestamp from log-line
    # An example log record timestamp is:
    #   '2020-02-05 12:39:28,394 - EC - EC3PO.Interpreter'[..]
    # This regex does a loose check on the date format as it does not
    # enforce real month numbers for example.
    # Note that the millisecond component is not part of the match group.
    # This is because it can happen that two log messages compete for the
    # file-print and one that was recorded a few milliseconds prior still
    # makes it to the file first.
    TS_RE = r'^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}),\d{3} .*$'

    TS_EXTRACTOR = re.compile(TS_RE)

    def setup_dir(self):
        """Setup a test directory to grab servo logs into and out of."""
        self.logtestdir = tempfile.mkdtemp()

    def grab_and_get_directory(self):
        """Thin wrapper around servo_host instance_logs function.

        @returns: directory name inside |self.logtestdir| containing servod logs
                  None if no directory was created (and logs grabbed)

        @raises error.TestFail: if log grabbing creates more or less than one
                                directory
        """
        ts = self.servo_host.get_instance_logs_ts()
        self.servo_host.get_instance_logs(ts, self.logtestdir)
        dirs_created = len(os.listdir(self.logtestdir))
        if dirs_created > 1:
            raise error.TestFail('Log grabbing created more than one directory')
        if dirs_created == 0:
            # None to indicate no logs were grabbed.
            return None
        return os.path.join(self.logtestdir, os.listdir(self.logtestdir)[0])

    def check_logs_ordered(self, outdir):
        """Helper to check that all logs in |outdir| are concat in order.

        Log grabbing glues together all logs for one loglevel into one
        file. These files ought to be in order.

        @param outdir: directory with logs to inspect

        @raises error.TestFail: if any log in |outdir| contains unsorted log
                                lines
        """
        for logf in os.listdir(outdir):
            if not logf.startswith(self.servo_host.JOINT_LOG_PREFIX):
                # MCU files are to be skipped.
                continue
            logpath = os.path.join(outdir, logf)
            timestamps = []
            with open(logpath, 'r') as log:
                for line in log:
                    ts_match = self.TS_EXTRACTOR.match(line)
                    if ts_match:
                        # It's safe to skip lines without timestamps, as those
                        # are just linebreaks in the log record.
                        timestamps.append(ts_match.group(1))
            if timestamps != sorted(timestamps):
                raise error.TestFail('Logfile %s contained unsorted '
                                     'timestamps.' % logf)

    def check_get_control_in_logs(self, outdir, ctrls):
        """Helper to check debug logs in |outdir|.

        This method always checks to ensure that the init logline saying that
        servod is listening on a port is printed.

        Note: if a control has been issued n times, and the goal is to ensure
        that it shows up n times in the logs, add it n times to |ctrls|.

        @param outdir: directory with logs to inspect
        @param ctrls: list of get controls that have been issued and should be
                      checked for

        @returns: False: - if any of the controls in |ctrls| are not found in
                           the logs
                         - if the log line saying that servod is listening on a
                           port is missing
                  True: otherwise
        """
        artifacts = [re.compile(r'- \(get\) %s' % ctrl) for ctrl in ctrls]
        # We also need to ensure that the initial logs don't get lost.
        init_artifact = re.compile(r'Listening on (\d{1,3}\.){3}\d{1,3} '
                                   r'port \d{1,5}')
        artifacts.append(init_artifact)
        fname = os.path.join(outdir, 'log.DEBUG.txt')
        with open(fname, 'r') as f:
            for line in f:
                # Only maintain the artifacts that have had no match yet.
                new_artifacts = []
                found = False
                for artifact in artifacts:
                    if not found and artifact.search(line):
                        # Each artifact is for one log-line. If a line is
                        # supposed to appear x times, there should be x
                        # artifacts, therefore we need to continue to avoid
                        # undercounting.
                        found = True
                        continue
                    # No match, add back to list.
                    new_artifacts.append(artifact)
                if not new_artifacts:
                    # All found, so the log file checks out.
                    return True
                artifacts = new_artifacts
        logging.error('The following artifacts were not found in logs:')
        for artifact in artifacts:
            logging.error(artifact.pattern)
        return False


    def test_singular(self):
        """Subtest to verify single-file grabbing works.

        As for any new test, the servod instance should only have one file
        upon initialization. This subtest ensures that the one file is grabbed
        properly and found in the results directory.

        @raises error.TestFail: if the log line saying that servod is listening
                                on a port is missing
        @raises error.TestFail: one of the loglevel summaries is missing
        """
        outdir = self.grab_and_get_directory()
        required = ['log.%s.txt' % l for l in ['INFO', 'DEBUG', 'WARNING']]
        logs = os.listdir(outdir)
        for log in required:
            if log not in logs:
                raise error.TestFail('Required file %s missing. '
                                     'Log files found: %s'
                                     % (log, ', '.join(logs)))
        self.check_logs_ordered(outdir)
        self.check_get_control_in_logs(outdir, [])

    def test_dual(self):
        """Subtest to verify singular file rotation works.

        Test rotates the logs once and then checks integrity.

        @raises error.TestFail: if the log line saying that servod is listening
                                on a port is missing
        @raises error.TestFail: logline showing 'config_files' was issued is
                                missing
        """
        logging.info('Running subtest to rotate logs once and validate that '
                     'logs are glued together in the results directory '
                     'correctly.')
        # First, rotate out the logs.
        self.servo.set_nocheck('rotate_servod_logs', 'yes')
        # Next issue a control that (a) requires no underlying hardware, and
        # (b) is not part of hwinit.
        ctrl = 'config_files'
        self.servo.get(ctrl)
        outdir = self.grab_and_get_directory()
        self.check_logs_ordered(outdir)
        if not self.check_get_control_in_logs(outdir, [ctrl]):
            raise error.TestFail('joint logfile log.DEBUG.txt does not contain '
                                 'log artifact that was created after the first'
                                 ' rotation')

    def test_compressed(self):
        """Subtest to verify multi file rotation and compression works.

        Test rotates the logs many times to generate compression. Validate
        that the output is properly decompressed with all expected lines.

        @raises error.TestFail: if the log line saying that servod is listening
                                on a port is missing
        @raises error.TestFail: logline showing 'config_files' was issued is
                                missing
        @raises error.TestFail: any of the expected number of loglines showing
                                'servod_pid' was issued is missing
        """
        logging.info('Running subtest to rotate logs past the compression '
                     'threshold and validate that compressed logs are '
                     'correctly decompressed upon grabbing.')
        # First, rotate out the logs.
        self.servo.set_nocheck('rotate_servod_logs', 'yes')
        ctrl = 'config_files'
        # Next issue a control that (a) requires no underlying hardware, and
        # (b) is not part of hwinit.
        self.servo.get(ctrl)
        ctrls = [ctrl]
        # Then, rotate out the logs and generate traffic for 6 additional times.
        # That should trigger file compression.
        # The command here has to be different to ensure that the first file
        # that had the config_files in it got decompressed.
        rot_ctrl = 'servod_pid'
        for _ in range(6):
            self.servo.set_nocheck('rotate_servod_logs', 'yes')
            self.servo.get(rot_ctrl)
            # Append every time to ensure it shows up the exact number of
            # times in the logs.
            ctrls.append(rot_ctrl)
        outdir = self.grab_and_get_directory()
        self.check_logs_ordered(outdir)
        if not self.check_get_control_in_logs(outdir, ctrls):
            raise error.TestFail('joint logfile log.DEBUG.txt does not contain '
                                 'log artifact that was created after the '
                                 'multiple rotation that leads to compression.')

    def test_missing(self):
        """Subtest to verify missing symlinks will grab no logs, throw no error.

        @raises error.TestFail: deleting symlinks to the latest logs causes
                                logs to still  be grabbed
        """
        logging.info('Running subtest to remove latest log symlinks and '
                     'validate this results in a grab logs noop.')
        self.servo_host.remove_latest_log_symlinks()
        outdir = self.grab_and_get_directory()
        if not outdir == None:
            raise error.TestFail('Removing symlinks to latest logs still '
                                 'caused test to find logs.')

    def test_restart(self):
        """Subtest to verify that a servod restart in the middle is caught.

        @raises error.TestFail: if the logs from the previous instances are
                                missing on a restart
        """
        restarts = 2
        # Need to cache the initial timestamp so that we can reuse it later
        initial_ts = self.servo_host.get_instance_logs_ts()
        for _ in range(restarts):
            self.servo_host.restart_servod()
        self.servo_host._initial_instance_ts = initial_ts
        # Grab the logs and probe for old, orphaned logs
        ts = self.servo_host.get_instance_logs_ts()
        self.servo_host.probe_servod_restart(ts, self.logtestdir)
        self.servo_host.get_instance_logs(ts, self.logtestdir)
        # First, validate that in total 3 directories were created: one
        # for the current instance, and two for the old ones
        dirs_created = len(os.listdir(self.logtestdir))
        expected_dirs = restarts + 1
        if dirs_created != expected_dirs:
            logging.error('Should have created %d dirs from grabbing logs '
                          'but only created %d.', expected_dirs, dirs_created)
            raise error.TestFail('Grabbing logs from restarted instance failed')
        # Check that panicinfo was called on the MCUs
        # TODO(coconutruben): refactor this into one source of truth on which
        # servos support panicinfo
        # There should only ever be one directory here that doesn't have the
        # suffix but if that logic changes in servo_host, then this needs to be
        # refactored, or an API call added to servo_host
        final_instance_dir = [d for d in os.listdir(self.logtestdir) if
                              self.servo_host.OLD_LOG_SUFFIX not in d][0]
        final_instance_dir_path = os.path.join(self.logtestdir,
                                               final_instance_dir)
        for mcu in ['servo_v4', 'servo_micro']:
            mcu_path = os.path.join(final_instance_dir_path, '%s.txt' % mcu)
            if os.path.exists(mcu_path):
                with open(mcu_path, 'r') as f:
                    if 'panicinfo' not in f.read():
                        raise error.TestFail('No panicinfo call found in %r.' %
                                             mcu_path)
    def test_restart_no_servo(self):
        """Subtest to verify that lack of |_servo| attribute yields no failure.

        @raises error.TestFail: if the lack of _servo causes an issue
        """
        self.servo_host.servo = None
        try:
            self.test_restart()
        except AttributeError as e:
            raise error.TestFail('Setting |_servo| to None caused an error. %s'
                                 % str(e))
        except error.TestFail as e:
            if 'No panicinfo call found in' in str(e):
                # This means that the lack of |_servo| worked as intended e.g.
                # the logs are grabbed, but the panicinfo could not be issued.
                pass
            # for any other issue, we need to actually raise it.
            raise e
        # Restore servo object again.
        self.servo_host.servo = self.servo

    def run_once(self, host):
        """Try 3 scenarios of log grabbing: single, multiple, compressed."""
        self.servo_host = host._servo_host
        self.servo = host.servo
        # Note that test_missing should run last as it is destructive and
        # removes logs on the servo_host side. Alternatively, restart
        # servod if another destructive test is added.
        for subtest in [self.test_singular, self.test_dual,
                        self.test_compressed, self.test_restart,
                        self.test_restart_no_servo, self.test_missing]:
            self.setup_dir()
            subtest()
            logging.info('Success')
            self.cleanup_logs()

    def cleanup_logs(self):
        """Delete the temporary directory."""
        shutil.rmtree(self.logtestdir)
