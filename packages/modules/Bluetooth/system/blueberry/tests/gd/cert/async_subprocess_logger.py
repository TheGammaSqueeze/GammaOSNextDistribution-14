#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import concurrent.futures
import logging
import re
import subprocess
from blueberry.tests.gd.cert.os_utils import TerminalColor
from contextlib import ExitStack


class AsyncSubprocessLogger:
    """
    An asynchronous logger for subprocesses.Popen object's STDOUT

    Contains threading functionality that allows asynchronous handling of lines
    from STDOUT from subprocess.Popen
    """
    WAIT_TIMEOUT_SECONDS = 10
    PROCESS_TAG_MIN_WIDTH = 24

    def __init__(self,
                 process: subprocess.Popen,
                 log_file_paths,
                 log_to_stdout=False,
                 tag=None,
                 color: TerminalColor = None):
        """
        :param process: a subprocess.Popen object with STDOUT
        :param log_file_paths: list of log files to redirect log to
        :param log_to_stdout: whether to dump logs to stdout in the format of
                              "[tag] logline"
        :param tag: tag to be used in above format
        :param color: when dumping to stdout, what color to use for tag
        """
        if not process:
            raise ValueError("process cannot be None")
        if not process.stdout:
            raise ValueError("process.stdout cannot be None")
        if log_to_stdout:
            if not tag or type(tag) is not str:
                raise ValueError("When logging to stdout, log tag must be set")
        self.log_file_paths = log_file_paths
        self.log_to_stdout = log_to_stdout
        self.tag = tag
        self.color = color
        self.process = process
        self.executor = concurrent.futures.ThreadPoolExecutor(max_workers=1)
        self.future = self.executor.submit(self.__logging_loop)

    def stop(self):
        """
        Stop this logger and this object can no longer be used after this call
        """
        try:
            result = self.future.result(timeout=self.WAIT_TIMEOUT_SECONDS)
            if result:
                logging.error("logging thread %s produced an error when executing: %s" % (self.tag, str(result)))
        except concurrent.futures.TimeoutError:
            logging.error("logging thread %s failed to finish after %d seconds" % (self.tag, self.WAIT_TIMEOUT_SECONDS))
        self.executor.shutdown(wait=False)

    def __logging_loop(self):
        if self.color:
            loggableTag = "[%s%s%s]" % (self.color, self.tag, TerminalColor.END)
        else:
            loggableTag = "[%s]" % self.tag
        tagLength = len(re.sub('[^\w\s]', '', loggableTag))
        if tagLength < self.PROCESS_TAG_MIN_WIDTH:
            loggableTag += " " * (self.PROCESS_TAG_MIN_WIDTH - tagLength)
        with ExitStack() as stack:
            log_files = [stack.enter_context(open(file_path, 'w')) for file_path in self.log_file_paths]
            for line in self.process.stdout:
                for log_file in log_files:
                    log_file.write(line)
                if self.log_to_stdout:
                    print("{}{}".format(loggableTag, line.strip()))
