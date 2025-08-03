# Lint as: python2, python3
# Copyright (c) 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Expects to be run in an environment with sudo and no interactive password
# prompt, such as within the Chromium OS development chroot.
"""This is the base host class for attached devices"""

import logging
import time

import common

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.server.hosts import ssh_host


class AttachedDeviceHost(ssh_host.SSHHost):
    """Host class for all attached devices(e.g. Android)"""

    # Since we currently use labstation as phone host, the repair logic
    # of labstation checks /var/lib/servod/ path to make reboot decision.
    #TODO(b:226151633): use a separated path after adjust repair logic.
    TEMP_FILE_DIR = '/var/lib/servod/'
    LOCK_FILE_POSTFIX = "_in_use"
    REBOOT_TIMEOUT_SECONDS = 240

    def _initialize(self,
                    hostname,
                    serial_number,
                    phone_station_ssh_port=None,
                    *args,
                    **dargs):
        """Construct a AttachedDeviceHost object.

        Args:
            hostname: Hostname of the attached device host.
            serial_number: Usb serial number of the associated
                           device(e.g. Android).
            phone_station_ssh_port: port for ssh to phone station, it
                                    use default 22 if the value is None.
        """
        self.serial_number = serial_number
        if phone_station_ssh_port:
            dargs['port'] = int(phone_station_ssh_port)
        super(AttachedDeviceHost, self)._initialize(hostname=hostname,
                                                    *args,
                                                    **dargs)

        # When run local test against a remote DUT in lab, user may use
        # port forwarding to bypass corp ssh relay. So the hostname may
        # be localhost while the command intended to run on a remote DUT,
        # we can differentiate this by checking if a non-default port
        # is specified.
        self._is_localhost = (self.hostname in {'localhost', "127.0.0.1"}
                              and phone_station_ssh_port is None)
        # Commands on the the host must be run by the superuser.
        # Our account on a remote host is root, but if our target is
        # localhost then we might be running unprivileged.  If so,
        # `sudo` will have to be added to the commands.
        self._sudo_required = False
        if self._is_localhost:
            self._sudo_required = utils.system_output('id -u') != '0'

        # We need to lock the attached device host to prevent other task
        # perform any interruptive actions(e.g. reboot) since they can
        # be shared by multiple devices
        self._is_locked = False
        self._lock_file = (self.TEMP_FILE_DIR + self.serial_number +
                           self.LOCK_FILE_POSTFIX)
        if not self.wait_up(self.REBOOT_TIMEOUT_SECONDS):
            raise error.AutoservError(
                    'Attached device host %s is not reachable via ssh.' %
                    self.hostname)
        if not self._is_localhost:
            self._lock()
            self.wait_ready()

    def _lock(self):
        logging.debug('Locking host %s by touching %s file', self.hostname,
                      self._lock_file)
        self.run('mkdir -p %s' % self.TEMP_FILE_DIR)
        self.run('touch %s' % self._lock_file)
        self._is_locked = True

    def _unlock(self):
        logging.debug('Unlocking host by removing %s file', self._lock_file)
        self.run('rm %s' % self._lock_file, ignore_status=True)
        self._is_locked = False

    def make_ssh_command(self,
                         user='root',
                         port=22,
                         opts='',
                         hosts_file=None,
                         connect_timeout=None,
                         alive_interval=None,
                         alive_count_max=None,
                         connection_attempts=None):
        """Override default make_ssh_command to use tuned options.

        Tuning changes:
          - ConnectTimeout=30; maximum of 30 seconds allowed for an SSH
          connection failure. Consistency with remote_access.py.

          - ServerAliveInterval=180; which causes SSH to ping connection every
          180 seconds. In conjunction with ServerAliveCountMax ensures
          that if the connection dies, Autotest will bail out quickly.

          - ServerAliveCountMax=3; consistency with remote_access.py.

          - ConnectAttempts=4; reduce flakiness in connection errors;
          consistency with remote_access.py.

          - UserKnownHostsFile=/dev/null; we don't care about the keys.

          - SSH protocol forced to 2; needed for ServerAliveInterval.

        Args:
            user: User name to use for the ssh connection.
            port: Port on the target host to use for ssh connection.
            opts: Additional options to the ssh command.
            hosts_file: Ignored.
            connect_timeout: Ignored.
            alive_interval: Ignored.
            alive_count_max: Ignored.
            connection_attempts: Ignored.

        Returns:
            An ssh command with the requested settings.
        """
        options = ' '.join([opts, '-o Protocol=2'])
        return super(AttachedDeviceHost,
                     self).make_ssh_command(user=user,
                                            port=port,
                                            opts=options,
                                            hosts_file='/dev/null',
                                            connect_timeout=30,
                                            alive_interval=180,
                                            alive_count_max=3,
                                            connection_attempts=4)

    def _make_scp_cmd(self, sources, dest):
        """Format scp command.

        Given a list of source paths and a destination path, produces the
        appropriate scp command for encoding it. Remote paths must be
        pre-encoded. Overrides _make_scp_cmd in AbstractSSHHost
        to allow additional ssh options.

        Args:
            sources: A list of source paths to copy from.
            dest: Destination path to copy to.

        Returns:
            An scp command that copies |sources| on local machine to
            |dest| on the remote host.
        """
        command = ('scp -rq %s -o BatchMode=yes -o StrictHostKeyChecking=no '
                   '-o UserKnownHostsFile=/dev/null %s %s "%s"')
        port = self.port
        if port is None:
            logging.info('AttachedDeviceHost: defaulting to port 22.'
                         ' See b/204502754.')
            port = 22
        args = (
                self._main_ssh.ssh_option,
                ("-P %s" % port),
                sources,
                dest,
        )
        return command % args

    def run(self,
            command,
            timeout=3600,
            ignore_status=False,
            stdout_tee=utils.TEE_TO_LOGS,
            stderr_tee=utils.TEE_TO_LOGS,
            connect_timeout=30,
            ssh_failure_retry_ok=False,
            options='',
            stdin=None,
            verbose=True,
            args=()):
        """Run a command on the attached device host.

        Extends method `run` in SSHHost. If the host is a remote device,
        it will call `run` in SSHost without changing anything.
        If the host is 'localhost', it will call utils.system_output.

        Args:
            command: The command line string.
            timeout: Time limit in seconds before attempting to
                     kill the running process. The run() function
                     will take a few seconds longer than 'timeout'
                     to complete if it has to kill the process.
            ignore_status: Do not raise an exception, no matter
                           what the exit code of the command is.
            stdout_tee: Where to tee the stdout.
            stderr_tee: Where to tee the stderr.
            connect_timeout: SSH connection timeout (in seconds)
                             Ignored if host is 'localhost'.
            options: String with additional ssh command options
                     Ignored if host is 'localhost'.
            ssh_failure_retry_ok: when True and ssh connection failure is
                                  suspected, OK to retry command (but not
                                  compulsory, and likely not needed here)
            stdin: Stdin to pass (a string) to the executed command.
            verbose: Log the commands.
            args: Sequence of strings to pass as arguments to command by
                  quoting them in " and escaping their contents if
                  necessary.

        Returns:
            A utils.CmdResult object.

        Raises:
            AutoservRunError: If the command failed.
            AutoservSSHTimeout: SSH connection has timed out. Only applies
                                when the host is not 'localhost'.
        """
        run_args = {
                'command': command,
                'timeout': timeout,
                'ignore_status': ignore_status,
                'stdout_tee': stdout_tee,
                'stderr_tee': stderr_tee,
                # connect_timeout     n/a for localhost
                # options             n/a for localhost
                # ssh_failure_retry_ok n/a for localhost
                'stdin': stdin,
                'verbose': verbose,
                'args': args,
        }
        if self._is_localhost:
            if self._sudo_required:
                run_args['command'] = 'sudo -n sh -c "%s"' % utils.sh_escape(
                        command)
            try:
                return utils.run(**run_args)
            except error.CmdError as e:
                logging.error(e)
                raise error.AutoservRunError('command execution error',
                                             e.result_obj)
        else:
            run_args['connect_timeout'] = connect_timeout
            run_args['options'] = options
            run_args['ssh_failure_retry_ok'] = ssh_failure_retry_ok
            return super(AttachedDeviceHost, self).run(**run_args)

    def wait_ready(self, required_uptime=300):
        """Wait ready for the host if it has been rebooted recently.

        It may take a few minutes until the system and usb components
        re-enumerated and become ready after a attached device reboot,
        so we need to make sure the host has been up for a given a mount
        of time before trying to start any actions.

        Args:
            required_uptime: Minimum uptime in seconds that we can
                             consider an attached device host be ready.
        """
        uptime = float(self.check_uptime())
        # To prevent unexpected output from check_uptime() that causes long
        # sleep, make sure the maximum wait time <= required_uptime.
        diff = min(required_uptime - uptime, required_uptime)
        if diff > 0:
            logging.info(
                    'The attached device host was just rebooted, wait %s'
                    ' seconds for all system services ready and usb'
                    ' components re-enumerated.', diff)
            #TODO(b:226401363): Use a poll to ensure all dependencies are ready.
            time.sleep(diff)

    def close(self):
        try:
            if self._is_locked:
                self._unlock()
        except error.AutoservSSHTimeout:
            logging.error('Unlock attached device host failed due to ssh'
                          ' timeout. It may caused by the host went down'
                          ' during the task.')
        finally:
            super(AttachedDeviceHost, self).close()
