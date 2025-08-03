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

import logging
from pathlib import Path
import psutil
import re
import subprocess
from typing import Container
from collections import deque


class TerminalColor:
    RED = "\033[31;1m"
    BLUE = "\033[34;1m"
    YELLOW = "\033[33;1m"
    MAGENTA = "\033[35;1m"
    END = "\033[0m"


def is_subprocess_alive(process, timeout_seconds=1):
    """
    Check if a process is alive for at least timeout_seconds
    :param process: a Popen object that represent a subprocess
    :param timeout_seconds: process needs to be alive for at least
           timeout_seconds
    :return: True if process is alive for at least timeout_seconds
    """
    try:
        process.wait(timeout=timeout_seconds)
        return False
    except subprocess.TimeoutExpired as exp:
        return True


def get_gd_root():
    """
    Return the root of the GD test library

    GD root is the parent directory of blueberry/tests/gd/cert
    :return: root directory string of gd test library
    """
    return str(Path(__file__).absolute().parents[4])


def make_ports_available(ports: Container[int], timeout_seconds=10):
    """Make sure a list of ports are available
    kill occupying process if possible
    :param ports: list of target ports
    :param timeout_seconds: number of seconds to wait when killing processes
    :return: True on success, False on failure
    """
    if not ports:
        logging.warning("Empty ports is given to make_ports_available()")
        return True
    # Get connections whose state are in LISTEN only
    # Connections in other states won't affect binding as SO_REUSEADDR is used
    listening_conns_for_port = filter(
        lambda conn: (conn and conn.status == psutil.CONN_LISTEN and conn.laddr and conn.laddr.port in ports),
        psutil.net_connections())
    success = True
    killed_pids = set()
    for conn in listening_conns_for_port:
        logging.warning("Freeing port %d used by %s" % (conn.laddr.port, str(conn)))
        if not conn.pid:
            logging.error("Failed to kill process occupying port %d due to lack of pid" % conn.laddr.port)
            continue
        logging.warning("Killing pid %d that is using port port %d" % (conn.pid, conn.laddr.port))
        if conn.pid in killed_pids:
            logging.warning("Pid %d is already killed in previous iteration" % (conn.pid))
            continue
        try:
            process = psutil.Process(conn.pid)
            process.kill()
            process.wait(timeout=timeout_seconds)
            killed_pids.add(conn.pid)
        except psutil.NoSuchProcess:
            logging.warning("Pid %d is already dead before trying to kill it" % (conn.pid))
            killed_pids.add(conn.pid)
            continue
        except psutil.TimeoutExpired:
            logging.error("SIGKILL timeout after %d seconds for pid %d" % (timeout_seconds, conn.pid))
            success = False
            break
    return success


# e.g. 2020-05-06 16:02:04.216 bt - packages/modules/Bluetooth/system/gd/facade/facade_main.cc:79 - crash_callback: #03 pc 0000000000013520  /lib/x86_64-linux-gnu/libpthread-2.29.so
HOST_CRASH_LINE_REGEX = re.compile(r"^.* - crash_callback: (?P<line>.*)$")
HOST_ABORT_HEADER = "Process crashed, signal: Aborted"
ASAN_OUTPUT_START_REGEX = re.compile(r"^==.*AddressSanitizer.*$")


def read_crash_snippet_and_log_tail(logpath):
    """
    Get crash snippet if regex matched or last 20 lines of log
    :return: crash_snippet, log_tail_20
            1) crash snippet without timestamp in one string;
            2) last 20 lines of log in one string;
    """
    gd_root_prefix = get_gd_root() + "/"
    abort_line = None
    last_20_lines = deque(maxlen=20)
    crash_log_lines = []
    asan = False
    asan_lines = []

    try:
        with open(logpath) as f:
            for _, line in enumerate(f):
                last_20_lines.append(line)
                asan_match = ASAN_OUTPUT_START_REGEX.match(line)
                if asan or asan_match:
                    asan_lines.append(line)
                    asan = True
                    continue

                host_crash_match = HOST_CRASH_LINE_REGEX.match(line)
                if host_crash_match:
                    crash_line = host_crash_match.group("line").replace(gd_root_prefix, "")
                    if HOST_ABORT_HEADER in crash_line \
                            and len(last_20_lines) > 1:
                        abort_line = last_20_lines[-2]
                    crash_log_lines.append(crash_line)
    except EnvironmentError:
        logging.error("Cannot open backing log file at {}".format(logpath))
        return None, None

    log_tail_20 = "".join(last_20_lines)
    crash_snippet = ""
    if abort_line is not None:
        crash_snippet += "abort log line:\n\n%s\n" % abort_line
    crash_snippet += "\n".join(crash_log_lines)

    if len(asan_lines) > 0:
        return "".join(asan_lines), log_tail_20

    if len(crash_log_lines) > 0:
        return crash_snippet, log_tail_20

    return None, log_tail_20
