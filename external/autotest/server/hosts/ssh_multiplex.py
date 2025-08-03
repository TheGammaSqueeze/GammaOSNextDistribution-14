# Lint as: python2, python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import multiprocessing
import os
import threading

from autotest_lib.client.common_lib import autotemp
from autotest_lib.server import utils
import six

# TODO b:169251326 terms below are set outside of this codebase
# and should be updated when possible. ("master" -> "main")
_MAIN_SSH_COMMAND_TEMPLATE = (
        '/usr/bin/ssh -a -x -N '
        '-o ControlMaster=yes '  # Create multiplex socket. # nocheck
        '-o ControlPath=%(socket)s '
        '-o StrictHostKeyChecking=no '
        '-o UserKnownHostsFile=/dev/null '
        '-o BatchMode=yes '
        '-o ConnectTimeout=30 '
        '-o ServerAliveInterval=30 '
        '-o ServerAliveCountMax=1 '
        '-o ConnectionAttempts=1 '
        '-o Protocol=2 '
        '-l %(user)s %(port)s %(hostname)s')


class MainSsh(object):
    """Manages multiplex ssh connection."""

    def __init__(self, hostname, user, port):
        self._hostname = hostname
        self._user = user
        self._port = port

        self._main_job = None
        self._main_tempdir = None

        self._lock = multiprocessing.Lock()

    def __del__(self):
        self.close()

    @property
    def _socket_path(self):
        return os.path.join(self._main_tempdir.name, 'socket')

    @property
    def ssh_option(self):
        """Returns the ssh option to use this multiplexed ssh.

        If background process is not running, returns an empty string.
        """
        if not self._main_tempdir:
            return ''
        return '-o ControlPath=%s' % (self._socket_path,)

    def maybe_start(self, timeout=5):
        """Starts the background process to run multiplex ssh connection.

        If there already is a background process running, this does nothing.
        If there is a stale process or a stale socket, first clean them up,
        then create a background process.

        @param timeout: timeout in seconds (default 5) to wait for main ssh
                        connection to be established. If timeout is reached, a
                        warning message is logged, but no other action is
                        taken.
        """
        # Multiple processes might try in parallel to clean up the old main
        # ssh connection and create a new one, therefore use a lock to protect
        # against race conditions.
        with self._lock:
            # If a previously started main SSH connection is not running
            # anymore, it needs to be cleaned up and then restarted.
            if (self._main_job and (not os.path.exists(self._socket_path) or
                                      self._main_job.sp.poll() is not None)):
                logging.info(
                        'Main-ssh connection to %s is down.', self._hostname)
                self._close_internal()

            # Start a new main SSH connection.
            if not self._main_job:
                # Create a shared socket in a temp location.
                self._main_tempdir = autotemp.tempdir(dir=_short_tmpdir())

                # Start the main SSH connection in the background.
                main_cmd = _MAIN_SSH_COMMAND_TEMPLATE % {
                        'hostname': self._hostname,
                        'user': self._user,
                        'port': "-p %s" % self._port if self._port else "",
                        'socket': self._socket_path,
                }
                logging.info(
                    'Starting main-ssh connection \'%s\'', main_cmd)
                self._main_job = utils.BgJob(
                    main_cmd, nickname='main-ssh',
                    stdout_tee=utils.DEVNULL, stderr_tee=utils.DEVNULL,
                    unjoinable=True)

                # To prevent a race between the main ssh connection
                # startup and its first attempted use, wait for socket file to
                # exist before returning.
                try:
                    utils.poll_for_condition(
                            condition=lambda: os.path.exists(self._socket_path),
                            timeout=timeout,
                            sleep_interval=0.2,
                            desc='main-ssh connection up')
                except utils.TimeoutError:
                    # poll_for_conditional already logs an error upon timeout
                    pass


    def close(self):
        """Releases all resources used by multiplexed ssh connection."""
        with self._lock:
            self._close_internal()

    def _close_internal(self):
        # Assume that when this is called, _lock should be acquired, already.
        if self._main_job:
            logging.debug('Nuking ssh main_job')
            utils.nuke_subprocess(self._main_job.sp)
            self._main_job = None

        if self._main_tempdir:
            logging.debug('Cleaning ssh main_tempdir')
            self._main_tempdir.clean()
            self._main_tempdir = None


class ConnectionPool(object):
    """Holds SSH multiplex connection instance."""

    def __init__(self):
        self._pool = {}
        self._lock = threading.Lock()

    def get(self, hostname, user, port):
        """Returns MainSsh instance for the given endpoint.

        If the pool holds the instance already, returns it. If not, create the
        instance, and returns it.

        Caller has the responsibility to call maybe_start() before using it.

        @param hostname: Host name of the endpoint.
        @param user: User name to log in.
        @param port: Port number sshd is listening.
        """
        key = (hostname, user, port)
        logging.debug('Get main ssh connection for %s@%s%s', user, hostname,
                      ":%s" % port if port else "")

        with self._lock:
            conn = self._pool.get(key)
            if not conn:
                conn = MainSsh(hostname, user, port)
                self._pool[key] = conn
            return conn

    def shutdown(self):
        """Closes all ssh multiplex connections."""
        for ssh in six.itervalues(self._pool):
            ssh.close()


def _short_tmpdir():
    # crbug/865171 Unix domain socket paths are limited to 108 characters.
    # crbug/945523 Swarming does not like too many top-level directories in
    # /tmp.
    # So use a shared parent directory in /tmp
    user = os.environ.get("USER", "no_USER")[:8]
    d = '/tmp/ssh-main_%s' % user
    if not os.path.exists(d):
        os.mkdir(d)
    return d
