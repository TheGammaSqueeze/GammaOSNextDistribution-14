# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Helper class to manage bluetooth logs"""

from datetime import datetime
import logging
import os
import re
import subprocess
import time


SYSLOG_PATH = '/var/log/messages'


class LogManager(object):
    """The LogManager class helps to collect logs without a listening thread"""

    DEFAULT_ENCODING = 'utf-8'

    class LoggingException(Exception):
        """A stub exception class for LogManager class."""
        pass

    def __init__(self, log_path=SYSLOG_PATH, raise_missing=False):
        """Initialize log manager object

        @param log_path: string path to log file to manage
        @param raise_missing: raise an exception if the log file is missing

        @raises: LogManager.LoggingException on non-existent log file
        """
        if not os.path.isfile(log_path):
            msg = 'Requested log file {} does not exist'.format(log_path)
            if raise_missing:
                raise LogManager.LoggingException(msg)
            else:
                self._LogErrorToSyslog(msg)

        self.log_path = log_path

        self.ResetLogMarker()
        self._bin_log_contents = []

    def _LogErrorToSyslog(self, message):
        """Create a new syslog file and add a message to syslog."""
        subprocess.call(['reload', 'syslog'])
        subprocess.call(['logger', message])

    def _GetSize(self):
        """Get the size of the log"""
        try:
            return os.path.getsize(self.log_path)
        except Exception as e:
            logging.error('Failed to get log size: {}'.format(e))
            return 0

    def ResetLogMarker(self, now_size=None):
        """Reset the start-of-log marker for later comparison"""
        if now_size is None:
            now_size = self._GetSize()
        self.initial_log_size = now_size

    def StartRecording(self):
        """Mark initial log size for later comparison"""

        self._bin_log_contents = []

    def StopRecording(self):
        """Gather the logs since StartRecording was called

        @raises: LogManager.LoggingException if:
                - Log file disappeared since StartRecording was called
                - Log file is smaller than when logging began
                - StartRecording was never called
        """
        initial_size = self.initial_log_size
        now_size = self._GetSize()

        if not os.path.isfile(self.log_path):
            msg = 'File {} disappeared unexpectedly'.format(self.log_path)
            raise LogManager.LoggingException(msg)

        if now_size < initial_size:
            msg = 'Log became smaller unexpectedly'
            raise LogManager.LoggingException(msg)

        with open(self.log_path, 'rb') as mf:
            # Skip to the point where we started recording
            mf.seek(self.initial_log_size)

            readsize = now_size - self.initial_log_size
            self._bin_log_contents = mf.read(readsize).split(b'\n')

        # Re-set start of log marker
        self.ResetLogMarker(now_size)

    def LogContains(self, search_str):
        """Performs simple string checking on each line from the collected log

        @param search_str: string to be located within log contents. This arg
                is expected to not span between lines in the logs

        @returns: True if search_str was located in the collected log contents,
                False otherwise
        """
        pattern = re.compile(search_str.encode(self.DEFAULT_ENCODING))
        for line in self._bin_log_contents:
            if pattern.search(line):
                return True

        return False

    def FilterOut(self, rm_reg_exp):
        """Remove lines with specified pattern from the log file

        @param rm_reg_exp: regular expression of the lines to be removed
        """
        # If log_path doesn't exist, there's nothing to do
        if not os.path.isfile(self.log_path):
            return

        rm_line_cnt = 0
        initial_size = self._GetSize()
        rm_pattern = re.compile(rm_reg_exp.encode(self.DEFAULT_ENCODING))

        with open(self.log_path, 'rb+') as mf:
            lines = mf.readlines()
            mf.seek(0)
            for line in lines:
                if rm_pattern.search(line):
                    rm_line_cnt += 1
                else:
                    mf.write(line)
            mf.truncate()

        # Some tracebacks point out here causing /var/log/messages missing but
        # we don't have many clues. Adding a check and logs here.
        if not os.path.isfile(self.log_path):
            msg = '{} does not exist after FilterOut'.format(self.log_path)
            logging.warning(msg)
            self._LogErrorToSyslog(msg)

        new_size = self._GetSize()
        rm_byte = initial_size - new_size
        logging.info('Removed number of line: %d, Reduced log size: %d byte',
                     rm_line_cnt, rm_byte)

        # Note the new size of the log
        self.ResetLogMarker(new_size)


class InterleaveLogger(LogManager):
    """LogManager class that focus on interleave scan"""

    # Example bluetooth kernel log:
    # "2020-11-23T07:52:31.395941Z DEBUG kernel: [ 6469.811135] Bluetooth: "
    # "cancel_interleave_scan() hci0: cancelling interleave scan"
    KERNEL_LOG_PATTERN = ('([^ ]+) DEBUG kernel: \[.*\] Bluetooth: '
                          '{FUNCTION}\(\) hci0: {LOG_STR}')
    STATE_PATTERN = KERNEL_LOG_PATTERN.format(
            FUNCTION='hci_req_add_le_interleaved_scan',
            LOG_STR='next state: (.+)')
    CANCEL_PATTERN = KERNEL_LOG_PATTERN.format(
            FUNCTION='cancel_interleave_scan',
            LOG_STR='cancelling interleave scan')
    SYSTIME_LENGTH = len('2020-12-18T00:11:22.345678')

    def __init__(self):
        """ Initialize object
        """
        self.reset()
        self.state_pattern = re.compile(
                self.STATE_PATTERN.encode(self.DEFAULT_ENCODING))
        self.cancel_pattern = re.compile(
                self.CANCEL_PATTERN.encode(self.DEFAULT_ENCODING))
        super(InterleaveLogger, self).__init__()

    def reset(self):
        """ Clear data between each log collection attempt
        """
        self.records = []
        self.cancel_events = []

    def StartRecording(self):
        """ Reset the previous data and start recording.
        """
        self.reset()
        super(InterleaveLogger, self).ResetLogMarker()
        super(InterleaveLogger, self).StartRecording()

    def StopRecording(self):
        """ Stop recording and parse logs
            The following data will be set after this call

            - self.records: a dictionary where each item is a record of
                            interleave |state| and the |time| the state starts.
                            |state| could be {'no filter', 'allowlist'}
                            |time| is system time in sec

            - self.cancel_events: a list of |time| when a interleave cancel
                                  event log was found
                                  |time| is system time in sec

            @returns: True if StopRecording success, False otherwise

        """
        try:
            super(InterleaveLogger, self).StopRecording()
        except Exception as e:
            logging.error(e)
            return False

        success = True

        def sys_time_to_timestamp(time_str):
            """ Return timestamp of time_str """

            # This is to remove the suffix of time string, in some cases the
            # time string ends with an extra 'Z', in other cases, the string
            # ends with time zone (ex. '+08:00')
            time_str = time_str[:self.SYSTIME_LENGTH]

            try:
                dt = datetime.strptime(time_str, "%Y-%m-%dT%H:%M:%S.%f")
            except Exception as e:
                logging.error(e)
                success = False
                return 0

            return time.mktime(dt.timetuple()) + dt.microsecond * (10**-6)

        for line in self._bin_log_contents:
            line = line.strip().replace(b'\\r\\n', b'')
            state_pattern = self.state_pattern.search(line)
            cancel_pattern = self.cancel_pattern.search(line)

            if cancel_pattern:
                time_str = cancel_pattern.groups()[0].decode(
                        self.DEFAULT_ENCODING)
                time_sec = sys_time_to_timestamp(time_str)
                self.cancel_events.append(time_sec)

            if state_pattern:
                time_str, state = [
                        x.decode(self.DEFAULT_ENCODING)
                        for x in state_pattern.groups()
                ]
                time_sec = sys_time_to_timestamp(time_str)
                self.records.append({'time': time_sec, 'state': state})

        return success
