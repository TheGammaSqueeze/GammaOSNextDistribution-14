# Lint as: python2, python3
# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from collections import namedtuple
import os
import re
import time

class WpaMon(object):
    """wpa_supplicant event monitor."""

    WPAS_CTRL_DIR = '/var/run/wpa_supplicant/'
    LOCAL_CTRL = 'local_ctrl'
    REQUEST_PIPE = 'request_pipe'
    WPAS_EVENT_LOG = 'wpa_event.log'

    CTRL_EVENT_DO_ROAM = 'CTRL-EVENT-DO-ROAM'
    CTRL_EVENT_SKIP_ROAM = 'CTRL-EVENT-SKIP-ROAM'
    CTRL_EVENT_DISCONNECTED = 'CTRL-EVENT-DISCONNECTED'
    CTRL_EVENT_SCAN_RESULTS = 'CTRL-EVENT-SCAN-RESULTS'
    CTRL_EVENT_BSS_ADDED = 'CTRL-EVENT-BSS-ADDED'

    ROAM_MATCH = str(r' cur_bssid=([\da-fA-F:]+) cur_freq=(\d+) ' \
                 r'cur_level=([\d-]+) cur_est=(\d+) ' \
                 r'sel_bssid=([\da-fA-F:]+) sel_freq=(\d+) ' \
                 r'sel_level=([\d-]+) sel_est=(\d+)')
    DISCONNECT_MATCH = str(r' bssid=([\da-fA-F:]+) reason=(\d+)' \
                       '(?: locally_generated=(1))?')
    SCAN_RESULTS_MATCH = str(r'()')
    BSS_ADDED_MATCH = str(r' ([\d]+) ([\da-fA-F:]+)')

    Roam = namedtuple('Roam',
                      ['cur_bssid', 'cur_freq', 'cur_level', 'cur_est',
                       'sel_bssid', 'sel_freq', 'sel_level', 'sel_est'])
    Disconnect = namedtuple('Disconnect', ['bssid', 'reason',
                                           'locally_generated'])
    ScanResults = namedtuple('ScanResults', [])
    Bss = namedtuple('Bss', ['id', 'bssid'])

    MatchFields = namedtuple('MatchFields', ['match_str', 'obj'])

    EVENT_MATCH_DICT = \
        {CTRL_EVENT_DO_ROAM: MatchFields(ROAM_MATCH, Roam),
         CTRL_EVENT_SKIP_ROAM: MatchFields(ROAM_MATCH, Roam),
         CTRL_EVENT_DISCONNECTED: MatchFields(DISCONNECT_MATCH, Disconnect),
         CTRL_EVENT_SCAN_RESULTS: MatchFields(SCAN_RESULTS_MATCH, ScanResults),
         CTRL_EVENT_BSS_ADDED: MatchFields(BSS_ADDED_MATCH, Bss),
        }

    def __init__(self, host, wifi_if):
        self._host = host
        self._dest = os.path.join(self.WPAS_CTRL_DIR, wifi_if)
        self._pgid = None
        self._started = False

    def __enter__(self):
        """Connect to wpa_supplicant control interface."""
        tmp_dir = self._host.get_tmp_dir()
        tmp_dir = self._host.get_tmp_dir(parent=tmp_dir)
        # Relax permissions for self._tmp_dir so that socat (run as wpa user)
        # can create files in this directory.
        self._host.run('chmod 777 %s' % tmp_dir)
        local = os.path.join(tmp_dir, self.LOCAL_CTRL)
        self._pipe = os.path.join(tmp_dir, self.REQUEST_PIPE)
        self._log_path = os.path.join(tmp_dir, self.WPAS_EVENT_LOG)
        # Run socat as wpa user so that the socket we bind to can be written to
        # by wpa_supplicant. We use a `tail -f` on a named pipe to send requests
        # to wpa_supplicant because `tail -f` continues to read even after it
        # encounters an EOF. Using `cat` or the PIPE address type would close
        # the input stream after the first write, instructing socat to tear
        # everything else down.
        command = r"nohup sudo -u wpa -g wpa socat SYSTEM:'mkfifo %s; " \
                  r"tail -f %s'\!\!STDOUT UNIX-CONNECT:%s,type=2,bind=%s " \
                  r"</dev/null >%s 2>&1 & echo $!" % \
                  (self._pipe, self._pipe, self._dest, local, self._log_path)
        out_lines = self._host.run(command).stdout.splitlines()
        pid = int(out_lines[0])
        self._pgid = \
            int(self._host.run('ps -p %d -o pgid=' % pid).stdout.strip())
        self._capture_index = 0
        self._start()
        return self

    def __exit__(self, exception, value, traceback):
        """Disconnect from wpa_supplicant control interface."""
        self._stop()
        # socat spawns a subprocess with the SYSTEM address type, so we must
        # kill the process group in order to properly clean up.
        self._host.run('kill -- -%d' % self._pgid)
        self._pgid = None
        self._capture_index = 0

    def _start(self):
        """
        Attach to the wpa_supplicant control interface to start subscribing to
        events.

        @return False if already attached, True otherwise.
        """
        if self._started:
            return False
        self._request('ATTACH')
        self._started = True
        return True

    def _stop(self):
        """
        Detach from the wpa_supplicant control interface to no longer receive
        events.

        @return False if not currently attached, True otherwise.
        """
        if not self._started:
            return False
        self._request('DETACH')
        self._started = False
        return True

    def _request(self, cmd):
        """
        Send a request to the control interface by writing to the named pipe.

        We use the -n option because wpa_supplicant expects there to be no
        newline character after the command.

        @param cmd string: command to run
        """
        self._host.run('echo -n "%s" > %s' % (cmd, self._pipe))

    def get_log_entries(self):
        """
        Get all event log entries and command replies.

        @return string event log
        """
        return self._host.run('cat %s' % self._log_path).stdout.rstrip()

    def start_event_capture(self):
        """
        Set _capture_index to mark the point in the logs at which an event
        capture was started.
        """
        self._capture_index = len(self.get_log_entries())

    def wait_for_event(self, event, timeout=10, sleep_interval=1.0, attrs={}):
        """
        Wait for a wpa_supplicant event. start_event_capture should be called
        before this.

        @param event string: the wpa_supplicant event to wait for.
        @param timeout int: timeout in seconds.
        @param sleep_interval float: sleep interval in seconds.
        @return list of strings of all event occurrences.
        """
        start_time = time.time()
        while True:
            objs = self.get_events(event, True, attrs)
            if objs:
                return objs
            if time.time() + sleep_interval - start_time > timeout:
                return []
            time.sleep(sleep_interval)
        return []

    def get_events(self, event, captured_events=False, attrs={}):
        """
        Get all wpa_supplicant events of type |event|.

        @param event string: the wpa_supplicant event to get.
        @param captured_events boolean: True to get events starting from the
        last start_event_capture call, False to get all events.
        @return list of namedtuples corresponding to the event.
        """
        wpa_log = self.get_log_entries()
        if captured_events:
            wpa_log = wpa_log[self._capture_index:]
        match_str = event + self.EVENT_MATCH_DICT[event].match_str
        matches = re.findall(match_str, wpa_log)
        objs = []
        for match in matches:
            obj = self.EVENT_MATCH_DICT[event].obj(*match)
            does_match = True
            for attr, val in list(attrs.items()):
                if getattr(obj, attr) != val:
                    does_match = False
                    break
            if does_match:
                objs.append(obj)
        return objs
