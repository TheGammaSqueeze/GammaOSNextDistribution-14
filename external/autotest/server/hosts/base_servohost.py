# Lint as: python2, python3
# Copyright (c) 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Expects to be run in an environment with sudo and no interactive password
# prompt, such as within the Chromium OS development chroot.


"""This is a base host class for servohost and labstation."""


import six.moves.http_client
import logging
import socket
import six.moves.xmlrpc_client
import time
import os

try:
    import docker
    from autotest_lib.site_utils.docker import utils as docker_utils
except ImportError:
    logging.info("Docker API is not installed in this environment")

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import autotest_enum
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import kernel_utils
from autotest_lib.client.cros import constants as client_constants
from autotest_lib.server import autotest
from autotest_lib.server import site_utils as server_utils
from autotest_lib.server.cros import provisioner
from autotest_lib.server.hosts import ssh_host
from autotest_lib.site_utils.rpm_control_system import rpm_client


class BaseServoHost(ssh_host.SSHHost):
    """Base host class for a host that manage servo(s).
     E.g. beaglebone, labstation.
    """
    REBOOT_CMD = 'sleep 5; reboot & sleep 10; reboot -f'

    TEMP_FILE_DIR = '/var/lib/servod/'

    LOCK_FILE_POSTFIX = '_in_use'
    REBOOT_FILE_POSTFIX = '_reboot'

    # Time to wait a rebooting servohost, in seconds.
    REBOOT_TIMEOUT = 240

    # Timeout value to power cycle a servohost, in seconds.
    BOOT_TIMEOUT = 240

    # Constants that reflect current host update state.
    UPDATE_STATE = autotest_enum.AutotestEnum('IDLE', 'RUNNING',
                                              'PENDING_REBOOT')

    def _initialize(self,
                    hostname,
                    is_in_lab=None,
                    servo_host_ssh_port=None,
                    servod_docker=None,
                    *args,
                    **dargs):
        """Construct a BaseServoHost object.

        @param is_in_lab: True if the servo host is in Cros Lab. Default is set
                          to None, for which utils.host_is_in_lab_zone will be
                          called to check if the servo host is in Cros lab.

        """
        if servo_host_ssh_port is not None:
            dargs['port'] = int(servo_host_ssh_port)

        super(BaseServoHost, self)._initialize(hostname=hostname,
                                               *args, **dargs)

        self.servod_container_name = None
        self._is_containerized_servod = False
        if bool(servod_docker):
            self._is_containerized_servod = True
            self.servod_container_name = servod_docker
        elif self.hostname.endswith('docker_servod'):
            # For backward compatibility
            self.servod_container_name = self.hostname
            self._is_containerized_servod = True

        self._is_localhost = (self.hostname == 'localhost'
                              and servo_host_ssh_port is None)
        if self._is_localhost or self._is_containerized_servod:
            self._is_in_lab = False
        elif is_in_lab is None:
            self._is_in_lab = (utils.host_is_in_lab_zone(self.hostname)
                               or self.is_satlab())
        else:
            self._is_in_lab = is_in_lab

        # Commands on the servo host must be run by the superuser.
        # Our account on a remote host is root, but if our target is
        # localhost then we might be running unprivileged.  If so,
        # `sudo` will have to be added to the commands.
        if self._is_localhost:
            self._sudo_required = utils.system_output('id -u') != '0'
        else:
            self._sudo_required = False

        self._is_labstation = None
        self._dut_host_info = None
        self._dut_hostname = None


    def get_board(self):
        """Determine the board for this servo host. E.g. fizz-labstation

        @returns a string representing this labstation's board or None if
         target host is not using a ChromeOS image(e.g. test in chroot).
        """
        output = self.run('cat /etc/lsb-release', ignore_status=True).stdout
        return lsbrelease_utils.get_current_board(lsb_release_content=output)


    def set_dut_host_info(self, dut_host_info):
        """
        @param dut_host_info: A HostInfo object.
        """
        logging.info('setting dut_host_info field to (%s)', dut_host_info)
        self._dut_host_info = dut_host_info


    def get_dut_host_info(self):
        """
        @return A HostInfo object.
        """
        return self._dut_host_info


    def set_dut_hostname(self, dut_hostname):
        """
        @param dut_hostname: hostname of the DUT that connected to this servo.
        """
        logging.info('setting dut_hostname as (%s)', dut_hostname)
        self._dut_hostname = dut_hostname


    def get_dut_hostname(self):
        """
        @returns hostname of the DUT that connected to this servo.
        """
        return self._dut_hostname


    def is_labstation(self):
        """Determine if the host is a labstation

        @returns True if ths host is a labstation otherwise False.
        """
        if self.is_containerized_servod():
            return False

        if self._is_labstation is None:
            if 'labstation' in self.hostname:
                logging.info('Based on hostname, the servohost is'
                             ' a labstation.')
                self._is_labstation = True
            else:
                logging.info(
                        'Cannot determine if %s is a labstation from'
                        ' hostname, getting board info from the'
                        ' servohost.', self.hostname)
                board = self.get_board()
                self._is_labstation = bool(board) and 'labstation' in board

        return self._is_labstation


    def _get_lsb_release_content(self):
        """Return the content of lsb-release file of host."""
        return self.run(
            'cat "%s"' % client_constants.LSB_RELEASE).stdout.strip()


    def get_release_version(self):
        """Get the value of attribute CHROMEOS_RELEASE_VERSION from lsb-release.

        @returns The version string in lsb-release, under attribute
                 CHROMEOS_RELEASE_VERSION(e.g. 12900.0.0). None on fail.
        """
        return lsbrelease_utils.get_chromeos_release_version(
            lsb_release_content=self._get_lsb_release_content()
        )


    def get_full_release_path(self):
        """Get full release path from servohost as string.

        @returns full release path as a string
                 (e.g. fizz-labstation-release/R82.12900.0.0). None on fail.
        """
        return lsbrelease_utils.get_chromeos_release_builder_path(
            lsb_release_content=self._get_lsb_release_content()
        )


    def _check_update_status(self):
        """ Check servohost's current update state.

        @returns: one of below state of from self.UPDATE_STATE
            IDLE -- if the target host is not currently updating and not
                pending on a reboot.
            RUNNING -- if there is another updating process that running on
                target host(note: we don't expect to hit this scenario).
            PENDING_REBOOT -- if the target host had an update and pending
                on reboot.
        """
        result = self.run('pgrep -f quick-provision | grep -v $$',
                          ignore_status=True)
        # We don't expect any output unless there are another quick
        # provision process is running.
        if result.exit_status == 0:
            return self.UPDATE_STATE.RUNNING

        # Determine if we have an update that pending on reboot by check if
        # the current inactive kernel has priority for the next boot.
        try:
            inactive_kernel = kernel_utils.get_kernel_state(self)[1]
            next_kernel = kernel_utils.get_next_kernel(self)
            if inactive_kernel == next_kernel:
                return self.UPDATE_STATE.PENDING_REBOOT
        except Exception as e:
            logging.error('Unexpected error while checking kernel info; %s', e)
        return self.UPDATE_STATE.IDLE


    def is_in_lab(self):
        """Check whether the servo host is a lab device.

        @returns: True if the servo host is in Cros Lab, otherwise False.

        """
        return self._is_in_lab


    def is_localhost(self):
        """Checks whether the servo host points to localhost.

        @returns: True if it points to localhost, otherwise False.

        """
        return self._is_localhost


    def is_containerized_servod(self):
        """Checks whether the servo host is a containerized servod.

        @returns: True if using containerized servod, otherwise False.

        """
        return self._is_containerized_servod

    def is_cros_host(self):
        """Check if a servo host is running chromeos.

        @return: True if the servo host is running chromeos.
            False if it isn't, or we don't have enough information.
        """
        if self.is_containerized_servod():
            return False
        try:
            result = self.run('grep -q CHROMEOS /etc/lsb-release',
                              ignore_status=True, timeout=10)
        except (error.AutoservRunError, error.AutoservSSHTimeout):
            return False
        return result.exit_status == 0


    def prepare_for_update(self):
        """Prepares the DUT for an update.
        Subclasses may override this to perform any special actions
        required before updating.
        """
        pass


    def reboot(self, *args, **dargs):
        """Reboot using special servo host reboot command."""
        super(BaseServoHost, self).reboot(reboot_cmd=self.REBOOT_CMD,
                                          *args, **dargs)


    def update_image(self, stable_version=None):
        """Update the image on the servo host, if needed.

        This method recognizes the following cases:
          * If the Host is not running ChromeOS, do nothing.
          * If a previously triggered update is now complete, reboot
            to the new version.
          * If the host is processing an update do nothing.
          * If the host has an update that pending on reboot, do nothing.
          * If the host is running a version of ChromeOS different
            from the default for servo Hosts, start an update.

        @stable_version the target build number.(e.g. R82-12900.0.0)

        @raises dev_server.DevServerException: If all the devservers are down.
        @raises site_utils.ParseBuildNameException: If the devserver returns
            an invalid build name.
        """
        # servod could be running in a Ubuntu workstation.
        if not self.is_cros_host():
            logging.info('Not attempting an update, either %s is not running '
                         'chromeos or we cannot find enough information about '
                         'the host.', self.hostname)
            return

        if lsbrelease_utils.is_moblab():
            logging.info('Not attempting an update, %s is running moblab.',
                         self.hostname)
            return

        if not stable_version:
            logging.debug("BaseServoHost::update_image attempting to get"
                          " servo cros stable version")
            try:
                stable_version = (self.get_dut_host_info().
                                  servo_cros_stable_version)
            except AttributeError:
                logging.error("BaseServoHost::update_image failed to get"
                              " servo cros stable version.")

        target_build = "%s-release/%s" % (self.get_board(), stable_version)
        target_build_number = server_utils.ParseBuildName(
            target_build)[3]
        current_build_number = self.get_release_version()

        if current_build_number == target_build_number:
            logging.info('servo host %s does not require an update.',
                         self.hostname)
            return

        status = self._check_update_status()
        if status == self.UPDATE_STATE.RUNNING:
            logging.info('servo host %s already processing an update',
                         self.hostname)
            return
        if status == self.UPDATE_STATE.PENDING_REBOOT:
            # Labstation reboot is handled separately here as it require
            # synchronized reboot among all managed DUTs. For servo_v3, we'll
            # reboot when initialize Servohost, if there is a update pending.
            logging.info('An update has been completed and pending reboot.')
            return

        ds = dev_server.ImageServer.resolve(self.hostname,
                                            hostname=self.hostname)
        url = ds.get_update_url(target_build)
        cros_provisioner = provisioner.ChromiumOSProvisioner(update_url=url,
                                                             host=self,
                                                             is_servohost=True)
        logging.info('Using devserver url: %s to trigger update on '
                     'servo host %s, from %s to %s', url, self.hostname,
                     current_build_number, target_build_number)
        cros_provisioner.run_provision()


    def has_power(self):
        """Return whether or not the servo host is powered by PoE or RPM."""
        # TODO(fdeng): See crbug.com/302791
        # For now, assume all servo hosts in the lab have power.
        return self.is_in_lab()


    def _post_update_reboot(self):
        """ Reboot servohost after an quick provision.

        We need to do some specifal cleanup before and after reboot
        when there is an update pending.
        """
        # Regarding the 'crossystem' command below: In some cases,
        # the update flow puts the TPM into a state such that it
        # fails verification.  We don't know why.  However, this
        # call papers over the problem by clearing the TPM during
        # the reboot.
        #
        # We ignore failures from 'crossystem'.  Although failure
        # here is unexpected, and could signal a bug, the point of
        # the exercise is to paper over problems; allowing this to
        # fail would defeat the purpose.

        # Preserve critical files before reboot since post-provision
        # clobbering will wipe the stateful partition.
        # TODO(xianuowang@) Remove this logic once we have updated to
        # a image with https://crrev.com/c/2485908.
        path_to_preserve = [
                '/var/lib/servod',
                '/var/lib/device_health_profile',
        ]
        safe_location = '/mnt/stateful_partition/unencrypted/preserve/'
        for item in path_to_preserve:
            dest = os.path.join(safe_location, item.split('/')[-1])
            self.run('rm -rf %s' % dest, ignore_status=True)
            self.run('mv %s %s' % (item, safe_location), ignore_status=True)

        self.run('crossystem clear_tpm_owner_request=1', ignore_status=True)
        self._servo_host_reboot()
        logging.debug('Cleaning up autotest directories if exist.')
        try:
            installed_autodir = autotest.Autotest.get_installed_autodir(self)
            self.run('rm -rf ' + installed_autodir)
        except autotest.AutodirNotFoundError:
            logging.debug('No autotest installed directory found.')

        # Recover preserved files to original location.
        # TODO(xianuowang@) Remove this logic once we have updated to
        # a image with https://crrev.com/c/2485908.
        for item in path_to_preserve:
            src = os.path.join(safe_location, item.split('/')[-1])
            dest = '/'.join(item.split('/')[:-1])
            self.run('mv %s %s' % (src, dest), ignore_status=True)

    def power_cycle(self):
        """Cycle power to this host via PoE(servo v3) or RPM(labstation)
        if it is a lab device.

        @raises AutoservRepairError if it fails to power cycle the
                servo host.

        """
        if self.has_power():
            try:
                rpm_client.set_power(self, 'CYCLE')
            except (socket.error, six.moves.xmlrpc_client.Error,
                    six.moves.http_client.BadStatusLine,
                    rpm_client.RemotePowerException) as e:
                raise hosts.AutoservRepairError(
                    'Power cycling %s failed: %s' % (self.hostname, e),
                    'power_cycle_via_rpm_failed'
                )
        else:
            logging.info('Skipping power cycling, not a lab device.')


    def _servo_host_reboot(self):
        """Reboot this servo host because a reboot is requested."""
        try:
            # TODO(otabek) remove if found the fix for b/174514811
            # The default factory firmware remember the latest chromeboxes
            # status after power off. If box was in sleep mode before the
            # break, the box will stay at sleep mode after power on.
            # Disable power manager has make chromebox to boot always when
            # we deliver the power to the device.
            logging.info('Stoping powerd service on device')
            self.run('stop powerd', ignore_status=True, timeout=30)
        except Exception as e:
            logging.debug('(Not critical) Fail to stop powerd; %s', e)

        logging.info('Rebooting servo host %s from build %s', self.hostname,
                     self.get_release_version())
        # Tell the reboot() call not to wait for completion.
        # Otherwise, the call will log reboot failure if servo does
        # not come back.  The logged reboot failure will lead to
        # test job failure.  If the test does not require servo, we
        # don't want servo failure to fail the test with error:
        # `Host did not return from reboot` in status.log.
        self.reboot(fastsync=True, wait=False)

        # We told the reboot() call not to wait, but we need to wait
        # for the reboot before we continue.  Alas.  The code from
        # here below is basically a copy of Host.wait_for_restart(),
        # with the logging bits ripped out, so that they can't cause
        # the failure logging problem described above.
        #
        # The stain that this has left on my soul can never be
        # erased.
        old_boot_id = self.get_boot_id()
        if not self.wait_down(timeout=self.WAIT_DOWN_REBOOT_TIMEOUT,
                              warning_timer=self.WAIT_DOWN_REBOOT_WARNING,
                              old_boot_id=old_boot_id):
            raise error.AutoservHostError(
                'servo host %s failed to shut down.' %
                self.hostname)
        if self.wait_up(timeout=self.REBOOT_TIMEOUT):
            logging.info('servo host %s back from reboot, with build %s',
                         self.hostname, self.get_release_version())
        else:
            raise error.AutoservHostError(
                'servo host %s failed to come back from reboot.' %
                self.hostname)


    def make_ssh_command(self, user='root', port=22, opts='', hosts_file=None,
        connect_timeout=None, alive_interval=None, alive_count_max=None,
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

        @param user User name to use for the ssh connection.
        @param port Port on the target host to use for ssh connection.
        @param opts Additional options to the ssh command.
        @param hosts_file Ignored.
        @param connect_timeout Ignored.
        @param alive_interval Ignored.
        @param alive_count_max Ignored.
        @param connection_attempts Ignored.

        @returns: An ssh command with the requested settings.

        """
        options = ' '.join([opts, '-o Protocol=2'])
        return super(BaseServoHost, self).make_ssh_command(
            user=user, port=port, opts=options, hosts_file='/dev/null',
            connect_timeout=30, alive_interval=180, alive_count_max=3,
            connection_attempts=4)


    def _make_scp_cmd(self, sources, dest):
        """Format scp command.

        Given a list of source paths and a destination path, produces the
        appropriate scp command for encoding it. Remote paths must be
        pre-encoded. Overrides _make_scp_cmd in AbstractSSHHost
        to allow additional ssh options.

        @param sources: A list of source paths to copy from.
        @param dest: Destination path to copy to.

        @returns: An scp command that copies |sources| on local machine to
                  |dest| on the remote servo host.

        """
        command = ('scp -rq %s -o BatchMode=yes -o StrictHostKeyChecking=no '
                   '-o UserKnownHostsFile=/dev/null %s %s "%s"')
        port = self.port
        if port is None:
            logging.info('BaseServoHost: defaulting to port 22. See b/204502754.')
            port = 22
        args = (
            self._main_ssh.ssh_option,
            ("-P %s" % port),
            sources,
            dest,
        )
        return command % args


    def run(self, command, timeout=3600, ignore_status=False,
        stdout_tee=utils.TEE_TO_LOGS, stderr_tee=utils.TEE_TO_LOGS,
        connect_timeout=30, ssh_failure_retry_ok=False,
        options='', stdin=None, verbose=True, args=()):
        """Run a command on the servo host.

        Extends method `run` in SSHHost. If the servo host is a remote device,
        it will call `run` in SSHost without changing anything.
        If the servo host is 'localhost', it will call utils.system_output.

        @param command: The command line string.
        @param timeout: Time limit in seconds before attempting to
                        kill the running process. The run() function
                        will take a few seconds longer than 'timeout'
                        to complete if it has to kill the process.
        @param ignore_status: Do not raise an exception, no matter
                              what the exit code of the command is.
        @param stdout_tee/stderr_tee: Where to tee the stdout/stderr.
        @param connect_timeout: SSH connection timeout (in seconds)
                                Ignored if host is 'localhost'.
        @param options: String with additional ssh command options
                        Ignored if host is 'localhost'.
        @param ssh_failure_retry_ok: when True and ssh connection failure is
                                     suspected, OK to retry command (but not
                                     compulsory, and likely not needed here)
        @param stdin: Stdin to pass (a string) to the executed command.
        @param verbose: Log the commands.
        @param args: Sequence of strings to pass as arguments to command by
                     quoting them in " and escaping their contents if necessary.

        @returns: A utils.CmdResult object.

        @raises AutoservRunError if the command failed.
        @raises AutoservSSHTimeout SSH connection has timed out. Only applies
                when servo host is not 'localhost'.

        """
        run_args = {
            'command'             : command,
            'timeout'             : timeout,
            'ignore_status'       : ignore_status,
            'stdout_tee'          : stdout_tee,
            'stderr_tee'          : stderr_tee,
            # connect_timeout     n/a for localhost
            # options             n/a for localhost
            # ssh_failure_retry_ok n/a for localhost
            'stdin'               : stdin,
            'verbose'             : verbose,
            'args'                : args,
        }
        if self.is_containerized_servod():
            logging.debug("Trying to run the command %s", command)
            client = docker_utils.get_docker_client(timeout=timeout)
            container = client.containers.get(self.servod_container_name)
            try:
                (exit_code,
                 output) = container.exec_run("bash -c '%s'" % command)
                # b/217780680, Make this compatible with python3,
                if isinstance(output, bytes):
                    output = output.decode(errors='replace')
            except docker.errors.APIError:
                logging.exception("Failed to run command %s", command)
                for line in container.logs().split(b'\n'):
                    logging.error(line)
                return utils.CmdResult(command=command,
                                       stdout="",
                                       exit_status=-1)
            return utils.CmdResult(command=command,
                                   stdout=output,
                                   exit_status=exit_code)
        elif self.is_localhost():
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
            return super(BaseServoHost, self).run(**run_args)

    def _mount_drive(self, src_path, dst_path):
        """Mount an external drive on servohost.

        @param: src_path  the drive path to mount(e.g. /dev/sda3).
        @param: dst_path  the destination directory on servohost to mount
                          the drive.

        @returns: True if mount success otherwise False.
        """
        # Make sure the dst dir exists.
        self.run('mkdir -p %s' % dst_path)

        result = self.run('mount -o ro %s %s' % (src_path, dst_path),
                          ignore_status=True)
        return result.exit_status == 0

    def _unmount_drive(self, mount_path):
        """Unmount a drive from servohost.

        @param: mount_path  the path on servohost to unmount.

        @returns: True if unmount success otherwise False.
        """
        result = self.run('umount %s' % mount_path, ignore_status=True)
        return result.exit_status == 0

    def wait_ready(self, required_uptime=300):
        """Wait ready for a servohost if it has been rebooted recently.

        It may take a few minutes until all servos and their componments
        re-enumerated and become ready after a servohost(especially labstation
        as it supports multiple servos) reboot, so we need to make sure the
        servohost has been up for a given a mount of time before trying to
        start any actions.

        @param required_uptime: Minimum uptime in seconds that we can
                                consdier a servohost be ready.
        """
        uptime = float(self.check_uptime())
        # To prevent unexpected output from check_uptime() that causes long
        # sleep, make sure the maximum wait time <= required_uptime.
        diff = min(required_uptime - uptime, required_uptime)
        if diff > 0:
            logging.info(
                    'The servohost was just rebooted, wait %s'
                    ' seconds for it to become ready', diff)
            time.sleep(diff)

    def is_up(self,
              timeout=60,
              connect_timeout=None,
              base_cmd="true",
              with_servod=True):
        """
        Check if the remote host is up by ssh-ing and running a base command.

        @param timeout: command execution timeout in seconds.
        @param connect_timeout: ssh connection timeout in seconds.
        @param base_cmd: a base command to run with ssh. The default is 'true'.
        @returns True if the remote host is up before the timeout expires,
                 False otherwise.
        """
        if self.is_containerized_servod():
            client = docker_utils.get_docker_client(timeout=timeout)
            # Look up the container list with hostname and with/without servod process by label.
            containers = client.containers.list(
                    filters={
                            'name': self.hostname,
                            'label': ["WITH_SERVOD=%s" % str(with_servod)]
                    })
            if not containers:
                return False
            elif with_servod:
                # For container with servod process, check if servod process started.
                (exit_code, output) = containers[0].exec_run("ps")
                logging.info("Is Up output %s", output)
                if b"servod" not in output:
                    return False
            return True
        else:
            return super(BaseServoHost, self).is_up(timeout, connect_timeout,
                                                    base_cmd)
