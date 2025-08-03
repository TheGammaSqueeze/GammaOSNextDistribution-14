# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from io import StringIO
import json

import logging
import os
import re
import sys
import six
import time

import common
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import autotemp
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib import utils as common_utils
from autotest_lib.client.common_lib.cros import cros_config
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.client.cros import constants as client_constants
from autotest_lib.client.cros import cros_ui
from autotest_lib.server import afe_utils
from autotest_lib.server import utils as server_utils
from autotest_lib.server.cros import provision
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.cros.dynamic_suite import tools, frontend_wrappers
from autotest_lib.server.cros.device_health_profile import device_health_profile
from autotest_lib.server.cros.device_health_profile import profile_constants
from autotest_lib.server.cros.servo import pdtester
from autotest_lib.server.hosts import abstract_ssh
from autotest_lib.server.hosts import base_label
from autotest_lib.server.hosts import chameleon_host
from autotest_lib.server.hosts import cros_constants
from autotest_lib.server.hosts import cros_label
from autotest_lib.server.hosts import cros_repair
from autotest_lib.server.hosts import pdtester_host
from autotest_lib.server.hosts import servo_host
from autotest_lib.server.hosts import servo_constants
from autotest_lib.site_utils.rpm_control_system import rpm_client
from autotest_lib.site_utils.admin_audit import constants as audit_const
from autotest_lib.site_utils.admin_audit import verifiers as audit_verify
from six.moves import zip


# In case cros_host is being ran via SSP on an older Moblab version with an
# older chromite version.
try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock


CONFIG = global_config.global_config

class FactoryImageCheckerException(error.AutoservError):
    """Exception raised when an image is a factory image."""
    pass


class CrosHost(abstract_ssh.AbstractSSHHost):
    """Chromium OS specific subclass of Host."""

    VERSION_PREFIX = provision.CROS_VERSION_PREFIX

    _AFE = frontend_wrappers.RetryingAFE(timeout_min=5, delay_sec=10)

    # Timeout values (in seconds) associated with various ChromeOS
    # state changes.
    #
    # In general, a good rule of thumb is that the timeout can be up
    # to twice the typical measured value on the slowest platform.
    # The times here have not necessarily been empirically tested to
    # meet this criterion.
    #
    # SLEEP_TIMEOUT:  Time to allow for suspend to memory.
    # RESUME_TIMEOUT: Time to allow for resume after suspend, plus
    #   time to restart the netwowrk.
    # SHUTDOWN_TIMEOUT: Time to allow for shut down.
    # BOOT_TIMEOUT: Time to allow for boot from power off.  Among
    #   other things, this must account for the 30 second dev-mode
    #   screen delay, time to start the network on the DUT, and the
    #   ssh timeout of 120 seconds.
    # USB_BOOT_TIMEOUT: Time to allow for boot from a USB device,
    #   including the 30 second dev-mode delay and time to start the
    #   network.
    # INSTALL_TIMEOUT: Time to allow for chromeos-install.
    # ADMIN_INSTALL_TIMEOUT: Time to allow for chromeos-install
    #   used by admin tasks.
    # POWERWASH_BOOT_TIMEOUT: Time to allow for a reboot that
    #   includes powerwash.

    SLEEP_TIMEOUT = 2
    RESUME_TIMEOUT = 10
    SHUTDOWN_TIMEOUT = 10
    BOOT_TIMEOUT = 150
    USB_BOOT_TIMEOUT = 300
    INSTALL_TIMEOUT = 480
    ADMIN_INSTALL_TIMEOUT = 600
    POWERWASH_BOOT_TIMEOUT = 60
    DEVSERVER_DOWNLOAD_TIMEOUT = 600

    # Minimum OS version that supports server side packaging. Older builds may
    # not have server side package built or with Autotest code change to support
    # server-side packaging.
    MIN_VERSION_SUPPORT_SSP = CONFIG.get_config_value(
            'AUTOSERV', 'min_version_support_ssp', type=int)

    USE_FSFREEZE = CONFIG.get_config_value(
            'CROS', 'enable_fs_freeze', type=bool, default=False)

    # REBOOT_TIMEOUT: How long to wait for a reboot.
    #
    # We have a long timeout to ensure we don't flakily fail due to other
    # issues. Shorter timeouts are vetted in platform_RebootAfterUpdate.
    # TODO(sbasi - crbug.com/276094) Restore to 5 mins once the 'host did not
    # return from reboot' bug is solved.
    REBOOT_TIMEOUT = 480

    # _USB_POWER_TIMEOUT: Time to allow for USB to power toggle ON and OFF.
    # _POWER_CYCLE_TIMEOUT: Time to allow for manual power cycle.
    # _CHANGE_SERVO_ROLE_TIMEOUT: Time to allow DUT regain network connection
    #                             since changing servo role will reset USB state
    #                             and causes temporary ethernet drop.
    _USB_POWER_TIMEOUT = 5
    _POWER_CYCLE_TIMEOUT = 10
    _CHANGE_SERVO_ROLE_TIMEOUT = 180

    _RPM_HOSTNAME_REGEX = ('chromeos(\d+)(-row(\d+))?-rack(\d+[a-z]*)'
                           '-host(\d+)')

    # Constants used in ping_wait_up() and ping_wait_down().
    #
    # _PING_WAIT_COUNT is the approximate number of polling
    # cycles to use when waiting for a host state change.
    #
    # _PING_STATUS_DOWN and _PING_STATUS_UP are names used
    # for arguments to the internal _ping_wait_for_status()
    # method.
    _PING_WAIT_COUNT = 40
    _PING_STATUS_DOWN = False
    _PING_STATUS_UP = True

    # Allowed values for the power_method argument.

    # POWER_CONTROL_RPM: Used in power_off/on/cycle() methods, default for all
    #                    DUTs except those with servo_v4 CCD.
    # POWER_CONTROL_CCD: Used in power_off/on/cycle() methods, default for all
    #                    DUTs with servo_v4 CCD.
    # POWER_CONTROL_SERVO: Used in set_power() and power_cycle() methods.
    # POWER_CONTROL_MANUAL: Used in set_power() and power_cycle() methods.
    POWER_CONTROL_RPM = 'RPM'
    POWER_CONTROL_CCD = 'CCD'
    POWER_CONTROL_SERVO = 'servoj10'
    POWER_CONTROL_MANUAL = 'manual'

    POWER_CONTROL_VALID_ARGS = (POWER_CONTROL_RPM,
                                POWER_CONTROL_CCD,
                                POWER_CONTROL_SERVO,
                                POWER_CONTROL_MANUAL)

    _RPM_OUTLET_CHANGED = 'outlet_changed'

    # URL pattern to download firmware image.
    _FW_IMAGE_URL_PATTERN = CONFIG.get_config_value(
            'CROS', 'firmware_url_pattern', type=str)

    # Regular expression for extracting EC version string
    _EC_REGEX = '(%s_\w*[-\.]\w*[-\.]\w*[-\.]\w*)'

    # Regular expression for extracting BIOS version string
    _BIOS_REGEX = '(%s\.\w*\.\w*\.\w*)'

    # Command to update firmware located on DUT
    _FW_UPDATE_CMD = 'chromeos-firmwareupdate --mode=recovery %s'

    @staticmethod
    def check_host(host, timeout=10):
        """
        Check if the given host is a chrome-os host.

        @param host: An ssh host representing a device.
        @param timeout: The timeout for the run command.

        @return: True if the host device is chromeos.

        """
        try:
            result = host.run(
                    'grep -q CHROMEOS /etc/lsb-release && '
                    '! grep -q moblab /etc/lsb-release && '
                    '! grep -q labstation /etc/lsb-release &&'
                    ' grep CHROMEOS_RELEASE_BOARD /etc/lsb-release',
                    ignore_status=True,
                    timeout=timeout).stdout
            if result:
                return not (
                    lsbrelease_utils.is_jetstream(
                        lsb_release_content=result) or
                    lsbrelease_utils.is_gce_board(
                        lsb_release_content=result))

        except (error.AutoservRunError, error.AutoservSSHTimeout):
            return False

        return False


    @staticmethod
    def get_chameleon_arguments(args_dict):
        """Extract chameleon options from `args_dict` and return the result.

        Recommended usage:
        ~~~~~~~~
            args_dict = utils.args_to_dict(args)
            chameleon_args = hosts.CrosHost.get_chameleon_arguments(args_dict)
            host = hosts.create_host(machine, chameleon_args=chameleon_args)
        ~~~~~~~~

        @param args_dict Dictionary from which to extract the chameleon
          arguments.
        """
        chameleon_args = {key: args_dict[key]
                          for key in ('chameleon_host', 'chameleon_port')
                          if key in args_dict}
        if 'chameleon_ssh_port' in args_dict:
            chameleon_args['port'] = int(args_dict['chameleon_ssh_port'])
        return chameleon_args

    @staticmethod
    def get_btattenuator_arguments(args_dict):
        """Extract btattenuator options from `args_dict` and return the result.

        @param args_dict Dictionary from which to extract the btattenuator
          arguments.
        """
        logging.debug("args dict in croshost is  %s", args_dict)
        btattenuator_args = {
                key: args_dict[key]
                for key in ('btatten_addr', ) if key in args_dict
        }

        return btattenuator_args

    @staticmethod
    def get_btpeer_arguments(args_dict):
        """Extract btpeer options from `args_dict` and return the result.

        This is used to parse details of Bluetooth peer.
        Recommended usage:
        ~~~~~~~~
            args_dict = utils.args_to_dict(args)
            btpeer_args = hosts.CrosHost.get_btpeer_arguments(args_dict)
            host = hosts.create_host(machine, btpeer_args=btpeer_args)
        ~~~~~~~~

        If btpeer_host_list is given, it should be a comma delimited list of
            host:ssh_port/chameleon_port
            127.0.0.1:22/9992

        When using ipv6, wrap the host portion in square brackets:
            [::1]:22/9992

        Note: Only the host name is required. Both ports are optional.
              If providing the chameleon port, note that you should provide an
              unforwarded port (i.e. the port exposed on the actual dut).

        @param args_dict: Dictionary from which to extract the btpeer
          arguments.
        """
        if 'btpeer_host_list' in args_dict:
            result = []
            for btpeer in args_dict['btpeer_host_list'].split(','):
                # IPv6 addresses including a port number should be enclosed in
                # square brackets.
                delimiter = ']:' if re.search(r':.*:', btpeer) else ':'

                # Split into ip + ports
                split = btpeer.strip('[]').split(delimiter)

                # If ports are given, split into ssh + chameleon ports
                if len(split) > 1:
                    ports = split[1].split('/')
                    split = [split[0]] + ports

                result.append({
                        key: value
                        for key, value in zip(('btpeer_host',
                                               'btpeer_ssh_port',
                                               'btpeer_port'), split)
                })
            return result
        else:
            return {key: args_dict[key]
                for key in ('btpeer_host', 'btpeer_port', 'btpeer_ssh_port')
                if key in args_dict}


    @staticmethod
    def get_local_host_ip(args_dict):
        """Ip address of DUT in the local LAN.

        When using port forwarding during testing, the host ip is 127.0.0.1 and
        can't be used by any peer devices (for example to scp). A local IP
        should be given in this case so peripherals can access the DUT in the
        local LAN.

        The argument should be given with the key |local_host_ip|.

        @params args_dict: Dictionary from which to extract the local host ip.
        """
        return {
                key: args_dict[key]
                for key in ('local_host_ip', ) if key in args_dict
        }

    @staticmethod
    def get_pdtester_arguments(args_dict):
        """Extract chameleon options from `args_dict` and return the result.

        Recommended usage:
        ~~~~~~~~
            args_dict = utils.args_to_dict(args)
            pdtester_args = hosts.CrosHost.get_pdtester_arguments(args_dict)
            host = hosts.create_host(machine, pdtester_args=pdtester_args)
        ~~~~~~~~

        @param args_dict Dictionary from which to extract the pdtester
          arguments.
        """
        return {key: args_dict[key]
                for key in ('pdtester_host', 'pdtester_port')
                if key in args_dict}


    @staticmethod
    def get_servo_arguments(args_dict):
        """Extract servo options from `args_dict` and return the result.

        Recommended usage:
        ~~~~~~~~
            args_dict = utils.args_to_dict(args)
            servo_args = hosts.CrosHost.get_servo_arguments(args_dict)
            host = hosts.create_host(machine, servo_args=servo_args)
        ~~~~~~~~

        @param args_dict Dictionary from which to extract the servo
          arguments.
        """
        servo_attrs = (servo_constants.SERVO_HOST_ATTR,
                       servo_constants.SERVO_HOST_SSH_PORT_ATTR,
                       servo_constants.SERVO_PORT_ATTR,
                       servo_constants.SERVOD_DOCKER_ATTR,
                       servo_constants.SERVO_SERIAL_ATTR,
                       servo_constants.SERVO_BOARD_ATTR,
                       servo_constants.SERVO_MODEL_ATTR)
        servo_args = {key: args_dict[key]
                      for key in servo_attrs
                      if key in args_dict}
        return (
            None
            if servo_constants.SERVO_HOST_ATTR in servo_args
                and not servo_args[servo_constants.SERVO_HOST_ATTR]
            else servo_args)


    def _initialize(self,
                    hostname,
                    chameleon_args=None,
                    servo_args=None,
                    pdtester_args=None,
                    try_lab_servo=False,
                    try_servo_repair=False,
                    ssh_verbosity_flag='',
                    ssh_options='',
                    try_servo_recovery=False,
                    *args,
                    **dargs):
        """Initialize superclasses, |self.chameleon|, and |self.servo|.

        This method will attempt to create the test-assistant object
        (chameleon/servo) when it is needed by the test. Check
        the docstring of chameleon_host.create_chameleon_host and
        servo_host.create_servo_host for how this is determined.

        @param hostname: Hostname of the dut.
        @param chameleon_args: A dictionary that contains args for creating
                               a ChameleonHost. See chameleon_host for details.
        @param servo_args: A dictionary that contains args for creating
                           a ServoHost object. See servo_host for details.
        @param try_lab_servo: When true, indicates that an attempt should
                              be made to create a ServoHost for a DUT in
                              the test lab, even if not required by
                              `servo_args`. See servo_host for details.
        @param try_servo_repair: If a servo host is created, check it
                              with `repair()` rather than `verify()`.
                              See servo_host for details.
        @param ssh_verbosity_flag: String, to pass to the ssh command to control
                                   verbosity.
        @param ssh_options: String, other ssh options to pass to the ssh
                            command.
        @param try_servo_recovery:  When True, start servod in recovery mode.
                                    See servo_host for details.
        """
        super(CrosHost, self)._initialize(hostname=hostname, *args, **dargs)
        self._repair_strategy = cros_repair.create_cros_repair_strategy()
        # hold special dut_state for repair process
        self._device_repair_state = None
        self.labels = base_label.LabelRetriever(cros_label.CROS_LABELS)
        # self.env is a dictionary of environment variable settings
        # to be exported for commands run on the host.
        # LIBC_FATAL_STDERR_ can be useful for diagnosing certain
        # errors that might happen.
        self.env['LIBC_FATAL_STDERR_'] = '1'
        self._ssh_verbosity_flag = ssh_verbosity_flag
        self._ssh_options = ssh_options
        self.health_profile = None
        self._default_power_method = None
        dut_health_profile = device_health_profile.DeviceHealthProfile(
                hostname=self.hostname,
                host_info=self.host_info_store.get(),
                result_dir=self.get_result_dir())

        # TODO(otabek@): remove when b/171414073 closed
        if self.use_icmp:
            pingable_before_servo = self.is_up_fast(count=1)
            if pingable_before_servo:
                logging.info('DUT is pingable before init Servo.')
        else:
            logging.info('Skipping ping to DUT before init Servo.')
        _servo_host, servo_state = servo_host.create_servo_host(
                dut=self,
                servo_args=servo_args,
                try_lab_servo=try_lab_servo,
                try_servo_repair=try_servo_repair,
                try_servo_recovery=try_servo_recovery,
                dut_host_info=self.host_info_store.get(),
                dut_health_profile=dut_health_profile)
        if dut_health_profile.is_loaded():
            logging.info('Device health profile loaded.')
            # The device profile is located in the servo_host which make it
            # dependency. If profile is not loaded yet then we do not have it
            # TODO(otabek@) persist device provide out of servo-host.
            self.health_profile = dut_health_profile
        self.set_servo_host(_servo_host, servo_state)

        # TODO(waihong): Do the simplication on Chameleon too.
        self._chameleon_host = chameleon_host.create_chameleon_host(
            dut=self.hostname,
            chameleon_args=chameleon_args)
        if self._chameleon_host:
            self.chameleon = self._chameleon_host.create_chameleon_board()
        else:
            self.chameleon = None

        # Bluetooth peers will be populated by the test if needed
        self._btpeer_host_list = []
        self.btpeer_list = []
        self.btpeer = None

        # Add pdtester host if pdtester args were added on command line
        self._pdtester_host = pdtester_host.create_pdtester_host(
                pdtester_args, self._servo_host)

        if self._pdtester_host:
            self.pdtester_servo = self._pdtester_host.get_servo()
            logging.info('pdtester_servo: %r', self.pdtester_servo)
            # Create the pdtester object used to access the ec uart
            self.pdtester = pdtester.PDTester(self.pdtester_servo,
                    self._pdtester_host.get_servod_server_proxy())
        else:
            self.pdtester = None

    def initialize_btpeer(self, btpeer_args=[]):
        """ Initialize the Bluetooth peers

        Initialize Bluetooth peer devices given in the arguments. Bluetooth peer
        is chameleon host on Raspberry Pi.
        @param btpeer_args: A dictionary that contains args for creating
                            a ChameleonHost. See chameleon_host for details.

        """
        logging.debug('Attempting to initialize bluetooth peers if available')
        try:
            if type(btpeer_args) is list:
                btpeer_args_list = btpeer_args
            else:
                btpeer_args_list = [btpeer_args]

            self._btpeer_host_list = chameleon_host.create_btpeer_host(
                dut=self.hostname, btpeer_args_list=btpeer_args_list)
            logging.debug('Bluetooth peer hosts are  %s',
                          self._btpeer_host_list)
            self.btpeer_list = [_host.create_chameleon_board() for _host in
                                self._btpeer_host_list if _host is not None]

            if len(self.btpeer_list) > 0:
                self.btpeer = self.btpeer_list[0]

            logging.debug('After initialize_btpeer btpeer_list %s '
                          'btpeer_host_list is %s and btpeer is %s',
                          self.btpeer_list, self._btpeer_host_list,
                          self.btpeer)
        except Exception as e:
            logging.error('Exception %s in initialize_btpeer', str(e))


    def get_cros_repair_image_name(self):
        """Get latest stable cros image name from AFE.

        Use the board name from the info store. Should that fail, try to
        retrieve the board name from the host's installed image itself.

        @returns: current stable cros image name for this host.
        """
        info = self.host_info_store.get()
        if not info.board:
            logging.warning('No board label value found. Trying to infer '
                         'from the host itself.')
            try:
                info.labels.append(self.get_board())
            except (error.AutoservRunError, error.AutoservSSHTimeout) as e:
                logging.error('Also failed to get the board name from the DUT '
                              'itself. %s.', str(e))
                raise error.AutoservError('Cannot determine board of the DUT'
                                          ' while getting repair image name.')
        return afe_utils.get_stable_cros_image_name_v2(info)


    def host_version_prefix(self, image):
        """Return version label prefix.

        In case the CrOS provisioning version is something other than the
        standard CrOS version e.g. CrOS TH version, this function will
        find the prefix from provision.py.

        @param image: The image name to find its version prefix.
        @returns: A prefix string for the image type.
        """
        return provision.get_version_label_prefix(image)

    def stage_build_to_usb(self, build):
        """Stage the current ChromeOS image on the USB stick connected to the
        servo.

        @param build: The build to download and send to USB.
        """
        if not self.servo:
            raise error.TestError('Host %s does not have servo.' %
                                  self.hostname)

        _, update_url = self.stage_image_for_servo(build)

        try:
            self.servo.image_to_servo_usb(update_url)
        finally:
            # servo.image_to_servo_usb turned the DUT off, so turn it back on
            logging.debug('Turn DUT power back on.')
            self.servo.get_power_state_controller().power_on()

        logging.debug('ChromeOS image %s is staged on the USB stick.',
                      build)

    def verify_job_repo_url(self, tag=''):
        """
        Make sure job_repo_url of this host is valid.

        Eg: The job_repo_url "http://lmn.cd.ab.xyx:8080/static/\
        lumpy-release/R29-4279.0.0/autotest/packages" claims to have the
        autotest package for lumpy-release/R29-4279.0.0. If this isn't the case,
        download and extract it. If the devserver embedded in the url is
        unresponsive, update the job_repo_url of the host after staging it on
        another devserver.

        @param job_repo_url: A url pointing to the devserver where the autotest
            package for this build should be staged.
        @param tag: The tag from the server job, in the format
                    <job_id>-<user>/<hostname>, or <hostless> for a server job.

        @raises DevServerException: If we could not resolve a devserver.
        @raises AutoservError: If we're unable to save the new job_repo_url as
            a result of choosing a new devserver because the old one failed to
            respond to a health check.
        @raises urllib2.URLError: If the devserver embedded in job_repo_url
                                  doesn't respond within the timeout.
        """
        info = self.host_info_store.get()
        job_repo_url = info.attributes.get(ds_constants.JOB_REPO_URL, '')
        if not job_repo_url:
            logging.warning('No job repo url set on host %s', self.hostname)
            return

        logging.info('Verifying job repo url %s', job_repo_url)
        devserver_url, image_name = tools.get_devserver_build_from_package_url(
            job_repo_url)

        ds = dev_server.ImageServer(devserver_url)

        logging.info('Staging autotest artifacts for %s on devserver %s',
            image_name, ds.url())

        start_time = time.time()
        ds.stage_artifacts(image_name, ['autotest_packages'])
        stage_time = time.time() - start_time

        # Record how much of the verification time comes from a devserver
        # restage. If we're doing things right we should not see multiple
        # devservers for a given board/build/branch path.
        try:
            board, build_type, branch = server_utils.ParseBuildName(
                                                image_name)[:3]
        except server_utils.ParseBuildNameException:
            pass
        else:
            devserver = devserver_url[
                devserver_url.find('/') + 2:devserver_url.rfind(':')]
            stats_key = {
                'board': board,
                'build_type': build_type,
                'branch': branch,
                'devserver': devserver.replace('.', '_'),
            }

            monarch_fields = {
                'board': board,
                'build_type': build_type,
                'branch': branch,
                'dev_server': devserver,
            }
            metrics.Counter(
                    'chromeos/autotest/provision/verify_url'
                    ).increment(fields=monarch_fields)
            metrics.SecondsDistribution(
                    'chromeos/autotest/provision/verify_url_duration'
                    ).add(stage_time, fields=monarch_fields)


    def stage_server_side_package(self, image=None):
        """Stage autotest server-side package on devserver.

        @param image: Full path of an OS image to install or a build name.

        @return: A url to the autotest server-side package.

        @raise: error.AutoservError if fail to locate the build to test with, or
                fail to stage server-side package.
        """
        # If enable_drone_in_restricted_subnet is False, do not set hostname
        # in devserver.resolve call, so a devserver in non-restricted subnet
        # is picked to stage autotest server package for drone to download.
        hostname = self.hostname
        if not server_utils.ENABLE_DRONE_IN_RESTRICTED_SUBNET:
            hostname = None
        if image:
            image_name = tools.get_build_from_image(image)
            if not image_name:
                raise error.AutoservError(
                        'Failed to parse build name from %s' % image)
            ds = dev_server.ImageServer.resolve(image_name, hostname)
        else:
            info = self.host_info_store.get()
            job_repo_url = info.attributes.get(ds_constants.JOB_REPO_URL, '')
            if job_repo_url:
                devserver_url, image_name = (
                    tools.get_devserver_build_from_package_url(job_repo_url))
                # If enable_drone_in_restricted_subnet is True, use the
                # existing devserver. Otherwise, resolve a new one in
                # non-restricted subnet.
                if server_utils.ENABLE_DRONE_IN_RESTRICTED_SUBNET:
                    ds = dev_server.ImageServer(devserver_url)
                else:
                    ds = dev_server.ImageServer.resolve(image_name)
            elif info.build is not None:
                ds = dev_server.ImageServer.resolve(info.build, hostname)
                image_name = info.build
            else:
                raise error.AutoservError(
                        'Failed to stage server-side package. The host has '
                        'no job_repo_url attribute or cros-version label.')

        # Get the OS version of the build, for any build older than
        # MIN_VERSION_SUPPORT_SSP, server side packaging is not supported.
        match = re.match('.*/R\d+-(\d+)\.', image_name)
        if match and int(match.group(1)) < self.MIN_VERSION_SUPPORT_SSP:
            raise error.AutoservError(
                    'Build %s is older than %s. Server side packaging is '
                    'disabled.' % (image_name, self.MIN_VERSION_SUPPORT_SSP))

        ds.stage_artifacts(image_name, ['autotest_server_package'])
        return '%s/static/%s/%s' % (ds.url(), image_name,
                                    'autotest_server_package.tar.bz2')


    def stage_image_for_servo(self, image_name=None, artifact='test_image'):
        """Stage a build on a devserver and return the update_url.

        @param image_name: a name like lumpy-release/R27-3837.0.0
        @param artifact: a string like 'test_image'. Requests
            appropriate image to be staged.
        @returns a tuple of (image_name, URL) like
            (lumpy-release/R27-3837.0.0,
             http://172.22.50.205:8082/update/lumpy-release/R27-3837.0.0)
        """
        if not image_name:
            image_name = self.get_cros_repair_image_name()
        logging.info('Staging build for servo install: %s', image_name)
        devserver = dev_server.ImageServer.resolve(image_name, self.hostname)
        devserver.stage_artifacts(image_name, [artifact])
        if artifact == 'test_image':
            return image_name, devserver.get_test_image_url(image_name)
        elif artifact == 'recovery_image':
            return image_name, devserver.get_recovery_image_url(image_name)
        else:
            raise error.AutoservError("Bad artifact!")


    def stage_factory_image_for_servo(self, image_name):
        """Stage a build on a devserver and return the update_url.

        @param image_name: a name like <baord>/4262.204.0

        @return: An update URL, eg:
            http://<devserver>/static/canary-channel/\
            <board>/4262.204.0/factory_test/chromiumos_factory_image.bin

        @raises: ValueError if the factory artifact name is missing from
                 the config.

        """
        if not image_name:
            logging.error('Need an image_name to stage a factory image.')
            return

        factory_artifact = CONFIG.get_config_value(
                'CROS', 'factory_artifact', type=str, default='')
        if not factory_artifact:
            raise ValueError('Cannot retrieve the factory artifact name from '
                             'autotest config, and hence cannot stage factory '
                             'artifacts.')

        logging.info('Staging build for servo install: %s', image_name)
        devserver = dev_server.ImageServer.resolve(image_name, self.hostname)
        devserver.stage_artifacts(
                image_name,
                [factory_artifact],
                archive_url=None)

        return tools.factory_image_url_pattern() % (devserver.url(), image_name)


    def prepare_for_update(self):
        """Prepares the DUT for an update.

        Subclasses may override this to perform any special actions
        required before updating.
        """
        pass


    def _clear_fw_version_labels(self, rw_only):
        """Clear firmware version labels from the machine.

        @param rw_only: True to only clear fwrw_version; otherewise, clear
                        both fwro_version and fwrw_version.
        """
        info = self.host_info_store.get()
        info.clear_version_labels(provision.FW_RW_VERSION_PREFIX)
        if not rw_only:
            info.clear_version_labels(provision.FW_RO_VERSION_PREFIX)
        self.host_info_store.commit(info)


    def _add_fw_version_label(self, build, rw_only):
        """Add firmware version label to the machine.

        @param build: Build of firmware.
        @param rw_only: True to only add fwrw_version; otherwise, add both
                        fwro_version and fwrw_version.

        """
        info = self.host_info_store.get()
        info.set_version_label(provision.FW_RW_VERSION_PREFIX, build)
        if not rw_only:
            info.set_version_label(provision.FW_RO_VERSION_PREFIX, build)
        self.host_info_store.commit(info)


    def get_latest_release_version(self, platform, ref_board=None):
        """Search for the latest package release version from the image archive,
            and return it.

        @param platform: platform name, a.k.a. board or model
        @param ref_board: reference board name, a.k.a. baseboard, parent

        @return 'firmware-{platform}-{branch}-firmwarebranch/{release-version}/'
                '{platform}'
                or None if LATEST release file does not exist.
        """

        platforms = [ platform ]

        # Search the image path in reference board archive as well.
        # For example, bob has its binary image under its reference board (gru)
        # image archive.
        if ref_board:
            platforms.append(ref_board)

        for board in platforms:
            # Read 'LATEST-1.0.0' file
            branch_dir = provision.FW_BRANCH_GLOB % board
            latest_file = os.path.join(provision.CROS_IMAGE_ARCHIVE, branch_dir,
                                       'LATEST-1.0.0')

            try:
                # The result could be one or more.
                result = utils.system_output('gsutil ls -d ' +  latest_file)

                candidates = re.findall('gs://.*', result)

                # Found the directory candidates. No need to check the other
                # board name cadidates. Let's break the loop.
                break
            except error.CmdError:
                # It doesn't exist. Let's move on to the next item.
                pass
        else:
            logging.error('No LATEST release info is available.')
            return None

        for cand_dir in candidates:
            result = utils.system_output('gsutil cat ' + cand_dir)

            release_path = cand_dir.replace('LATEST-1.0.0', result)
            release_path = os.path.join(release_path, platform)
            try:
                # Check if release_path does exist.
                release = utils.system_output('gsutil ls -d ' + release_path)
                # Now 'release' has a full directory path: e.g.
                #  gs://chromeos-image-archive/firmware-octopus-11297.B-
                #  firmwarebranch/RNone-1.0.0-b4395530/octopus/

                # Remove "gs://chromeos-image-archive".
                release = release.replace(provision.CROS_IMAGE_ARCHIVE, '')

                # Remove CROS_IMAGE_ARCHIVE and any surrounding '/'s.
                return release.strip('/')
            except error.CmdError:
                # The directory might not exist. Let's try next candidate.
                pass
        else:
            raise error.AutoservError('Cannot find the latest firmware')

    @staticmethod
    def get_version_from_image(host, bios_image, ec_image):
        """Get version string from binary image using regular expression.

        @param host: An instance of hosts.Host.
        @param bios_image: Filename of AP BIOS image on the DUT/labstation.
        @param ec_image: Filename of EC image on the DUT/labstation.

        @return Tuple of bios version and ec version
        """
        if not host:
            return None
        cmd_args = ['futility', 'update', '--manifest']
        if bios_image:
            cmd_args.append('-i')
            cmd_args.append(bios_image)
        if ec_image:
            cmd_args.append('-e')
            cmd_args.append(ec_image)
        cmd = ' '.join([utils.sh_quote_word(arg) for arg in cmd_args])
        stdout = host.run(cmd).stdout
        if not isinstance(stdout, six.text_type):
            stdout = stdout.decode('utf-8')
        io = StringIO(stdout)
        data = json.load(io)
        return (
                data.get('default', {}).get('host', {}).get('versions',
                                                            {}).get('rw'),
                data.get('default', {}).get('ec', {}).get('versions',
                                                          {}).get('rw'),
        )


    def firmware_install(self,
                         build,
                         rw_only=False,
                         dest=None,
                         local_tarball=None,
                         verify_version=False,
                         try_scp=False,
                         install_ec=True,
                         install_bios=True,
                         corrupt_ec=False):
        """Install firmware to the DUT.

        Use stateful update if the DUT is already running the same build.
        Stateful update does not update kernel and tends to run much faster
        than a full reimage. If the DUT is running a different build, or it
        failed to do a stateful update, full update, including kernel update,
        will be applied to the DUT.

        Once a host enters firmware_install its fw[ro|rw]_version label will
        be removed. After the firmware is updated successfully, a new
        fw[ro|rw]_version label will be added to the host.

        @param build: The build version to which we want to provision the
                      firmware of the machine,
                      e.g. 'link-firmware/R22-2695.1.144'.
        @param rw_only: True to only install firmware to its RW portions. Keep
                        the RO portions unchanged.
        @param dest: Directory to store the firmware in.
        @param local_tarball: Path to local firmware image for installing
                              without devserver.
        @param verify_version: True to verify EC and BIOS versions after
                               programming firmware, default is False.
        @param try_scp: False to always program using servo, true to try copying
                        the firmware and programming from the DUT.
        @param install_ec: True to install EC FW, and False to skip it.
        @param install_bios: True to install BIOS, and False to skip it.
        @param corrupt_ec: True to flash EC with a false image (for test purpose).

        TODO(dshi): After bug 381718 is fixed, update here with corresponding
                    exceptions that could be raised.

        """
        if not self.servo:
            raise error.TestError('Host %s does not have servo.' %
                                  self.hostname)

        # Get the DUT board name from AFE.
        info = self.host_info_store.get()
        board = info.board
        model = info.model

        if board is None or board == '':
            board = self.servo.get_board()

        if model is None or model == '':
            try:
                model = self.get_platform()
            except Exception as e:
                logging.warning('Dut is unresponsive: %s', str(e))

        # If local firmware path not provided fetch it from the dev server
        tmpd = None
        if not local_tarball:
            logging.info('Will install firmware from build %s.', build)

            try:
                ds = dev_server.ImageServer.resolve(build, self.hostname)
                ds.stage_artifacts(build, ['firmware'])

                if not dest:
                    tmpd = autotemp.tempdir(unique_id='fwimage')
                    dest = tmpd.name

                # Download firmware image
                fwurl = self._FW_IMAGE_URL_PATTERN % (ds.url(), build)
                local_tarball = os.path.join(dest, os.path.basename(fwurl))
                logging.info('Downloading file from %s to %s.', fwurl,
                             local_tarball)
                ds.download_file(fwurl,
                                 local_tarball,
                                 timeout=self.DEVSERVER_DOWNLOAD_TIMEOUT)
                logging.info('Done downloading')
            except Exception as e:
                raise error.TestError('Failed to download firmware package: %s'
                                      % str(e))

        ec_image = None
        if install_ec:
            # Extract EC image from tarball
            logging.info('Extracting EC image.')
            ec_image = self.servo.extract_ec_image(board, model, local_tarball,
                                                   corrupt_ec)
            logging.info('Extracted: %s', ec_image)

        bios_image = None
        if install_bios:
            # Extract BIOS image from tarball
            logging.info('Extracting BIOS image.')
            bios_image = self.servo.extract_bios_image(board, model,
                                                       local_tarball)
            logging.info('Extracted: %s', bios_image)

        if not bios_image and not ec_image:
            raise error.TestError('No firmware installation was processed.')

        # Clear firmware version labels
        self._clear_fw_version_labels(rw_only)

        # Install firmware from local tarball
        try:
            image_ec_version = None
            image_bios_version = None

            # Check if copying to DUT is enabled and DUT is available
            if try_scp and self.is_up():
                # DUT is available, make temp firmware directory to store images
                logging.info('Making temp folder.')
                dest_folder = '/tmp/firmware'
                self.run('mkdir -p ' + dest_folder)
                dest_bios_path = None
                dest_ec_path = None

                fw_cmd = self._FW_UPDATE_CMD % ('--wp=1' if rw_only else '')

                if bios_image:
                    # Send BIOS firmware image to DUT
                    logging.info('Sending BIOS firmware.')
                    dest_bios_path = os.path.join(dest_folder,
                                                  os.path.basename(bios_image))
                    self.send_file(bios_image, dest_bios_path)

                    # Initialize firmware update command for BIOS image
                    fw_cmd += ' -i %s' % dest_bios_path

                # Send EC firmware image to DUT when EC image was found
                if ec_image:
                    logging.info('Sending EC firmware.')
                    dest_ec_path = os.path.join(dest_folder,
                                                os.path.basename(ec_image))
                    self.send_file(ec_image, dest_ec_path)

                    # Add EC image to firmware update command
                    fw_cmd += ' -e %s' % dest_ec_path

                # Make sure command is allowed to finish even if ssh fails.
                fw_cmd = "trap '' SIGHUP; %s" % fw_cmd

                # Update firmware on DUT
                logging.info('Updating firmware.')
                try:
                    self.run(fw_cmd, options="-o LogLevel=verbose")
                except error.AutoservRunError as e:
                    if e.result_obj.exit_status != 255:
                        raise
                    elif ec_image:
                        logging.warning("DUT network dropped during update"
                                     " (often caused by EC resetting USB)")
                    else:
                        logging.error("DUT network dropped during update"
                                      " (unexpected, since no EC image)")
                        raise
                image_bios_version, image_ec_version = self.get_version_from_image(
                        self, dest_bios_path, dest_ec_path)
            else:
                # Host is not available, program firmware using servo
                dest_bios_path = None
                dest_ec_path = None
                if ec_image:
                    dest_ec_path = self.servo.program_ec(ec_image, rw_only)
                if bios_image:
                    dest_bios_path = self.servo.program_bios(
                            bios_image, rw_only)
                if utils.host_is_in_lab_zone(self.hostname):
                    self._add_fw_version_label(build, rw_only)
                image_bios_version, image_ec_version = self.get_version_from_image(
                        self._servo_host, dest_bios_path, dest_ec_path)

            # Reboot and wait for DUT after installing firmware
            logging.info('Rebooting DUT.')
            self.servo.get_power_state_controller().reset()
            time.sleep(self.servo.BOOT_DELAY)
            self.test_wait_for_boot()

            # When enabled verify EC and BIOS firmware version after programming
            if verify_version:
                # Check programmed EC firmware when EC image was found
                if ec_image:
                    logging.info('Checking EC firmware version.')
                    if image_ec_version is None:
                        raise error.TestFail(
                                'Could not find EC version in %s' % ec_image)
                    dest_ec_version = self.get_ec_version()
                    if dest_ec_version != image_ec_version:
                        raise error.TestFail(
                            'Failed to update EC firmware, version %s '
                            '(expected %s)' % (dest_ec_version,
                                               image_ec_version))

                if bios_image:
                    # Check programmed BIOS firmware against expected version
                    logging.info('Checking BIOS firmware version.')
                    if image_bios_version is None:
                        raise error.TestFail(
                                'Could not find BIOS version in %s' %
                                bios_image)
                    dest_bios_version = self.get_firmware_version()
                    if dest_bios_version != image_bios_version:
                        raise error.TestFail(
                            'Failed to update BIOS, version %s '
                            '(expected %s)' % (dest_bios_version,
                                               image_bios_version))
        finally:
            if tmpd:
                tmpd.clean()


    def install_image_to_servo_usb(self, image_url=None):
        """Installing a test image on a USB storage device.

        Download image to USB-storage attached to the Servo board.

        @param image_url:       If specified use as the url to download to
                                USB-storage.

        @raises AutoservError if the image fails to download.

        """
        if not image_url:
            logging.debug('Skip download as image_url not provided!')
            return

        logging.info('Downloading image to USB')
        metrics_field = {'download': bool(image_url)}
        metrics.Counter(
                'chromeos/autotest/provision/servo_install/download_image'
        ).increment(fields=metrics_field)
        with metrics.SecondsTimer(
                'chromeos/autotest/servo_install/download_image_time'):
            try:
                self.servo.image_to_servo_usb(image_path=image_url,
                                              power_off_dut=False)
            except error.AutotestError as e:
                metrics.Counter('chromeos/autotest/repair/image_to_usb_error'
                                ).increment(
                                        fields={'host': self.hostname or ''})
                six.reraise(error.AutotestError, str(e), sys.exc_info()[2])

    def boot_in_recovery_mode(self,
                              usb_boot_timeout=USB_BOOT_TIMEOUT,
                              need_snk=False):
        """Booting host  in recovery mode.

        Boot device in recovery mode and verify that device booted from
        external storage as expected.

        @param usb_boot_timeout:    The usb_boot_timeout to use wait the host
                                    to boot. Factory images need a longer
                                    usb_boot_timeout than regular cros images.
        @param snk_mode:            If True, switch servo_v4 role to 'snk'
                                    mode before boot DUT into recovery mode.

        @raises AutoservError if the image fails to boot.

        """
        logging.info('Booting from USB directly. Usb boot timeout: %s',
                     usb_boot_timeout)
        with metrics.SecondsTimer(
                'chromeos/autotest/provision/servo_install/boot_duration'):
            self.servo.boot_in_recovery_mode(snk_mode=need_snk)
            if not self.wait_up(timeout=usb_boot_timeout):
                if need_snk:
                    # Attempt to restore servo_v4 role to 'src' mode.
                    self.servo.set_servo_v4_role('src')
                raise hosts.AutoservRepairError(
                        'DUT failed to boot from USB after %d seconds' %
                        usb_boot_timeout, 'failed_to_boot_pre_install')

        # Make sure the DUT is boot from an external device.
        if not self.is_boot_from_external_device():
            raise hosts.AutoservRepairError(
                    'DUT is expected to boot from an external device(e.g. '
                    'a usb stick), however it seems still boot from an'
                    ' internal storage.', 'boot_from_internal_storage')

    def run_install_image(self,
                          install_timeout=INSTALL_TIMEOUT,
                          need_snk=False,
                          is_repair=False):
        """Installing the image with chromeos-install.

        Steps included:
        1) Recover TPM on the device
        2) Run chromeos-install
        2.a) if success: power off/on the device
        2.b) if fail:
        2.b.1) Mark for replacement if fail with hardware issue
        2.b.2) Run internal storage check. (Only if is_repair=True)
        3) Wait the device to boot as verifier of success install

        Device has to booted from external storage.

        @param install_timeout:     The timeout to use when installing the
                                    chromeos image. Factory images need a
                                    longer install_timeout.
        @param snk_mode:            If True, switch servo_v4 role to 'snk'
                                    mode before boot DUT into recovery mode.
        @param is_repair:           Indicates if the method is called from a
                                    repair task.

        @raises AutoservError if the fail in process of install image.
        @raises AutoservRepairError if fail to boot after install image.

        """
        # The new chromeos-tpm-recovery has been merged since R44-7073.0.0.
        # In old CrOS images, this command fails. Skip the error.
        logging.info('Resetting the TPM status')
        try:
            self.run('chromeos-tpm-recovery')
        except error.AutoservRunError:
            logging.warning('chromeos-tpm-recovery is too old.')

        with metrics.SecondsTimer(
                'chromeos/autotest/provision/servo_install/install_duration'):
            logging.info('Installing image through chromeos-install.')
            try:
                self.run('chromeos-install --yes',timeout=install_timeout)
                self.halt()
            except Exception as e:
                storage_errors = [
                   'No space left on device',
                   'I/O error when trying to write primary GPT',
                   'Input/output error while writing out',
                   'cannot read GPT header',
                   'can not determine destination device',
                   'wrong fs type',
                   'bad superblock on',
                ]
                has_error = [msg for msg in storage_errors if(msg in str(e))]
                if has_error:
                    info = self.host_info_store.get()
                    info.set_version_label(
                        audit_const.DUT_STORAGE_STATE_PREFIX,
                        audit_const.HW_STATE_NEED_REPLACEMENT)
                    self.host_info_store.commit(info)
                    self.set_device_repair_state(
                        cros_constants.DEVICE_STATE_NEEDS_REPLACEMENT)
                    logging.debug(
                        'Fail install image from USB; Storage error; %s', e)
                    raise error.AutoservError(
                        'Failed to install image from USB due to a suspect '
                        'disk failure, DUT storage state changed to '
                        'need_replacement, please check debug log '
                        'for details.')
                else:
                    if is_repair:
                        # DUT will be marked for replacement if storage is bad.
                        audit_verify.VerifyDutStorage(self).verify()

                    logging.debug('Fail install image from USB; %s', e)
                    raise error.AutoservError(
                        'Failed to install image from USB due to unexpected '
                        'error, please check debug log for details.')
            finally:
                # We need reset the DUT no matter re-install success or not,
                # as we don't want leave the DUT in boot from usb state.
                logging.info('Power cycling DUT through servo.')
                self.servo.get_power_state_controller().power_off()
                self.servo.switch_usbkey('off')
                if need_snk:
                    # Attempt to restore servo_v4 role to 'src' mode.
                    self.servo.set_servo_v4_role('src')
                # N.B. The Servo API requires that we use power_on() here
                # for two reasons:
                #  1) After turning on a DUT in recovery mode, you must turn
                #     it off and then on with power_on() once more to
                #     disable recovery mode (this is a Parrot specific
                #     requirement).
                #  2) After power_off(), the only way to turn on is with
                #     power_on() (this is a Storm specific requirement).
                self.servo.get_power_state_controller().power_on()

        logging.info('Waiting for DUT to come back up.')
        if not self.wait_up(timeout=self.BOOT_TIMEOUT):
            raise hosts.AutoservRepairError('DUT failed to reboot installed '
                                            'test image after %d seconds' %
                                            self.BOOT_TIMEOUT,
                                            'failed_to_boot_post_install')

    def servo_install(self,
                      image_url=None,
                      usb_boot_timeout=USB_BOOT_TIMEOUT,
                      install_timeout=INSTALL_TIMEOUT,
                      is_repair=False):
        """Re-install the OS on the DUT by:

        Steps:
        1) Power off the host
        2) Installing an image on a USB-storage attached to the Servo board
        3) Booting that image in recovery mode
        4) Installing the image with chromeos-install.

        @param image_url:           If specified use as the url to install on
                                    the DUT otherwise boot the currently
                                    staged image on the USB stick.
        @param usb_boot_timeout:    The usb_boot_timeout to use during
                                    re-image. Factory images need a longer
                                    usb_boot_timeout than regular cros images.
        @param install_timeout:     The timeout to use when installing the
                                    chromeos image. Factory images need a
                                    longer install_timeout.
        @param is_repair:           Indicates if the method is called from a
                                    repair task.

        @raises AutoservError if the image fails to boot.

        """
        self.servo.get_power_state_controller().power_off()
        if image_url:
            self.install_image_to_servo_usb(image_url=image_url)
        else:
            # Give the DUT some time to power_off if we skip
            # download image to usb. (crbug.com/982993)
            time.sleep(10)

        need_snk = self.require_snk_mode_in_recovery()

        self.boot_in_recovery_mode(usb_boot_timeout=usb_boot_timeout,
                                   need_snk=need_snk)

        self.run_install_image(install_timeout=install_timeout,
                               need_snk=need_snk,
                               is_repair=is_repair)

    def set_servo_host(self, host, servo_state=None):
        """Set our servo host member, and associated servo.

        @param host  Our new `ServoHost`.
        """
        self._servo_host = host
        self.servo_pwr_supported = None
        if self._servo_host is not None:
            self.servo = self._servo_host.get_servo()
            servo_state = self._servo_host.get_servo_state()
            self._set_smart_usbhub_label(self._servo_host.smart_usbhub)
            try:
                self.servo_pwr_supported = self.servo.has_control('power_state')
            except Exception as e:
                logging.debug(
                    "Could not get servo power state due to {}".format(e))
        else:
            self.servo = None
            self.servo_pwr_supported = False
        self.set_servo_type()
        self.set_servo_state(servo_state)
        self._set_servo_topology()


    def repair_servo(self):
        """
        Confirm that servo is initialized and verified.

        If the servo object is missing, attempt to repair the servo
        host.  Repair failures are passed back to the caller.

        @raise AutoservError: If there is no servo host for this CrOS
                              host.
        """
        if self.servo:
            return
        if not self._servo_host:
            raise error.AutoservError('No servo host for %s.' %
                                      self.hostname)
        try:
            self._servo_host.repair()
        except:
            raise
        finally:
            self.set_servo_host(self._servo_host)


    def set_servo_type(self):
        """Set servo info labels to dut host_info"""
        if not self.servo:
            logging.debug('Servo is not initialized to get servo_type.')
            return
        if not self.is_servo_in_working_state():
            logging.debug('Servo is not good, skip update servo_type.')
            return
        servo_type = self.servo.get_servo_type()
        if not servo_type:
            logging.debug('Cannot collect servo_type from servo'
                ' by `dut-control servo_type`! Please file a bug'
                ' and inform infra team as we are not expected '
                ' to reach this point.')
            return
        host_info = self.host_info_store.get()
        prefix = servo_constants.SERVO_TYPE_LABEL_PREFIX
        old_type = host_info.get_label_value(prefix)
        if old_type == servo_type:
            # do not need update
            return
        host_info.set_version_label(prefix, servo_type)
        self.host_info_store.commit(host_info)
        logging.info('ServoHost: servo_type updated to %s '
                    '(previous: %s)', servo_type, old_type)


    def set_servo_state(self, servo_state):
        """Set servo info labels to dut host_info"""
        if servo_state is not None:
            host_info = self.host_info_store.get()
            servo_state_prefix = servo_constants.SERVO_STATE_LABEL_PREFIX
            old_state = host_info.get_label_value(servo_state_prefix)
            if old_state == servo_state:
                # do not need update
                return
            host_info.set_version_label(servo_state_prefix, servo_state)
            self.host_info_store.commit(host_info)
            logging.info('ServoHost: servo_state updated to %s (previous: %s)',
                         servo_state, old_state)


    def get_servo_state(self):
        host_info = self.host_info_store.get()
        servo_state_prefix = servo_constants.SERVO_STATE_LABEL_PREFIX
        return host_info.get_label_value(servo_state_prefix)

    def is_servo_in_working_state(self):
        """Validate servo is in WORKING state."""
        servo_state = self.get_servo_state()
        return servo_state == servo_constants.SERVO_STATE_WORKING

    def get_servo_usb_state(self):
        """Get the label value indicating the health of the USB drive.

        @return: The label value if defined, otherwise '' (empty string).
        @rtype: str
        """
        host_info = self.host_info_store.get()
        servo_usb_state_prefix = audit_const.SERVO_USB_STATE_PREFIX
        return host_info.get_label_value(servo_usb_state_prefix)

    def is_servo_usb_usable(self):
        """Check if the servo USB storage device is usable for FAFT.

        @return: False if the label indicates a state that will break FAFT.
                 True if state is okay, or if state is not defined.
        @rtype: bool
        """
        usb_state = self.get_servo_usb_state()
        return usb_state in ('', audit_const.HW_STATE_ACCEPTABLE,
                             audit_const.HW_STATE_NORMAL,
                             audit_const.HW_STATE_UNKNOWN)

    def _set_smart_usbhub_label(self, smart_usbhub_detected):
        if smart_usbhub_detected is None:
            # skip the label update here as this indicate we wasn't able
            # to confirm usbhub type.
            return
        host_info = self.host_info_store.get()
        if (smart_usbhub_detected ==
                (servo_constants.SMART_USBHUB_LABEL in host_info.labels)):
            # skip label update if current label match the truth.
            return
        if smart_usbhub_detected:
            logging.info('Adding %s label to host %s',
                         servo_constants.SMART_USBHUB_LABEL,
                         self.hostname)
            host_info.labels.append(servo_constants.SMART_USBHUB_LABEL)
        else:
            logging.info('Removing %s label from host %s',
                         servo_constants.SMART_USBHUB_LABEL,
                         self.hostname)
            host_info.labels.remove(servo_constants.SMART_USBHUB_LABEL)
        self.host_info_store.commit(host_info)

    def repair(self):
        """Attempt to get the DUT to pass `self.verify()`.

        This overrides the base class function for repair; it does
        not call back to the parent class, but instead relies on
        `self._repair_strategy` to coordinate the verification and
        repair steps needed to get the DUT working.
        """
        message = 'Beginning repair for host %s board %s model %s'
        info = self.host_info_store.get()
        message %= (self.hostname, info.board, info.model)
        self.record('INFO', None, None, message)
        profile_state = profile_constants.DUT_STATE_READY
        # Initialize bluetooth peers
        self.initialize_btpeer()
        try:
            self._repair_strategy.repair(self)
        except hosts.AutoservVerifyDependencyError as e:
            # TODO(otabek): remove when finish b/174191325
            self._stat_if_pingable_but_not_sshable()
            # We don't want flag a DUT as failed if only non-critical
            # verifier(s) failed during the repair.
            if e.is_critical():
                profile_state = profile_constants.DUT_STATE_REPAIR_FAILED
                self._reboot_labstation_if_needed()
                self.try_set_device_needs_manual_repair()
                raise
        finally:
            self.set_health_profile_dut_state(profile_state)

    def get_verifier_state(self, tag):
        """Return the state of host verifier by tag.

        @returns: bool or None
        """
        return self._repair_strategy.verifier_is_good(tag)

    def get_repair_strategy_node(self, tag):
        """Return the instance of verifier/repair node for host by tag.

        @returns: _DependencyNode or None
        """
        return self._repair_strategy.node_by_tag(tag)

    def close(self):
        """Close connection."""
        super(CrosHost, self).close()

        if self._chameleon_host:
            self._chameleon_host.close()

        if self.health_profile:
            try:
                self.health_profile.close()
            except Exception as e:
                logging.warning(
                    'Failed to finalize device health profile; %s', e)

        if self._servo_host:
            self._servo_host.close()

    def get_power_supply_info(self):
        """Get the output of power_supply_info.

        power_supply_info outputs the info of each power supply, e.g.,
        Device: Line Power
          online:                  no
          type:                    Mains
          voltage (V):             0
          current (A):             0
        Device: Battery
          state:                   Discharging
          percentage:              95.9276
          technology:              Li-ion

        Above output shows two devices, Line Power and Battery, with details of
        each device listed. This function parses the output into a dictionary,
        with key being the device name, and value being a dictionary of details
        of the device info.

        @return: The dictionary of power_supply_info, e.g.,
                 {'Line Power': {'online': 'yes', 'type': 'main'},
                  'Battery': {'vendor': 'xyz', 'percentage': '100'}}
        @raise error.AutoservRunError if power_supply_info tool is not found in
               the DUT. Caller should handle this error to avoid false failure
               on verification.
        """
        result = self.run('power_supply_info').stdout.strip()
        info = {}
        device_name = None
        device_info = {}
        for line in result.split('\n'):
            pair = [v.strip() for v in line.split(':')]
            if len(pair) != 2:
                continue
            if pair[0] == 'Device':
                if device_name:
                    info[device_name] = device_info
                device_name = pair[1]
                device_info = {}
            else:
                device_info[pair[0]] = pair[1]
        if device_name and not device_name in info:
            info[device_name] = device_info
        return info


    def get_battery_percentage(self):
        """Get the battery percentage.

        @return: The percentage of battery level, value range from 0-100. Return
                 None if the battery info cannot be retrieved.
        """
        try:
            info = self.get_power_supply_info()
            logging.info(info)
            return float(info['Battery']['percentage'])
        except (KeyError, ValueError, error.AutoservRunError):
            return None


    def get_battery_state(self):
        """Get the battery charging state.

        @return: A string representing the battery charging state. It can be
                 'Charging', 'Fully charged', or 'Discharging'.
        """
        try:
            info = self.get_power_supply_info()
            logging.info(info)
            return info['Battery']['state']
        except (KeyError, ValueError, error.AutoservRunError):
            return None


    def get_battery_display_percentage(self):
        """Get the battery display percentage.

        @return: The display percentage of battery level, value range from
                 0-100. Return None if the battery info cannot be retrieved.
        """
        try:
            info = self.get_power_supply_info()
            logging.info(info)
            return float(info['Battery']['display percentage'])
        except (KeyError, ValueError, error.AutoservRunError):
            return None


    def is_ac_connected(self):
        """Check if the dut has power adapter connected and charging.

        @return: True if power adapter is connected and charging.
        """
        try:
            info = self.get_power_supply_info()
            return info['Line Power']['online'] == 'yes'
        except (KeyError, error.AutoservRunError):
            return None


    def _cleanup_poweron(self):
        """Special cleanup method to make sure hosts always get power back."""
        info = self.host_info_store.get()
        if self._RPM_OUTLET_CHANGED not in info.attributes:
            return
        logging.debug('This host has recently interacted with the RPM'
                      ' Infrastructure. Ensuring power is on.')
        try:
            self.power_on()
            self._remove_rpm_changed_tag()
        except rpm_client.RemotePowerException:
            logging.error('Failed to turn Power On for this host after '
                          'cleanup through the RPM Infrastructure.')

            battery_percentage = self.get_battery_percentage()
            if (
                    battery_percentage
                    and battery_percentage < cros_constants.MIN_BATTERY_LEVEL):
                raise
            elif self.is_ac_connected():
                logging.info('The device has power adapter connected and '
                             'charging. No need to try to turn RPM on '
                             'again.')
                self._remove_rpm_changed_tag()
            logging.info('Battery level is now at %s%%. The device may '
                         'still have enough power to run test, so no '
                         'exception will be raised.', battery_percentage)


    def _remove_rpm_changed_tag(self):
        info = self.host_info_store.get()
        del info.attributes[self._RPM_OUTLET_CHANGED]
        self.host_info_store.commit(info)


    def _add_rpm_changed_tag(self):
        info = self.host_info_store.get()
        info.attributes[self._RPM_OUTLET_CHANGED] = 'true'
        self.host_info_store.commit(info)



    def _is_factory_image(self):
        """Checks if the image on the DUT is a factory image.

        @return: True if the image on the DUT is a factory image.
                 False otherwise.
        """
        result = self.run('[ -f /root/.factory_test ]', ignore_status=True)
        return result.exit_status == 0


    def _restart_ui(self):
        """Restart the Chrome UI.

        @raises: FactoryImageCheckerException for factory images, since
                 we cannot attempt to restart ui on them.
                 error.AutoservRunError for any other type of error that
                 occurs while restarting ui.
        """
        if self._is_factory_image():
            raise FactoryImageCheckerException('Cannot restart ui on factory '
                                               'images')

        # TODO(jrbarnette):  The command to stop/start the ui job
        # should live inside cros_ui, too.  However that would seem
        # to imply interface changes to the existing start()/restart()
        # functions, which is a bridge too far (for now).
        prompt = cros_ui.get_chrome_session_ident(self)
        self.run('stop ui; start ui')
        cros_ui.wait_for_chrome_ready(prompt, self)


    def _start_powerd_if_needed(self):
        """Start powerd if it isn't already running."""
        self.run('start powerd', ignore_status=True)

    def _read_arc_prop_file(self, filename):
        for path in [
                '/usr/share/arcvm/properties/', '/usr/share/arc/properties/'
        ]:
            if self.path_exists(path + filename):
                return utils.parse_cmd_output('cat ' + path + filename,
                                              run_method=self.run)
        return None

    def _get_arc_build_info(self):
        """Returns a dictionary mapping build properties to their values."""
        build_info = None
        for filename in ['build.prop', 'vendor_build.prop']:
            properties = self._read_arc_prop_file(filename)
            if properties:
                if build_info:
                    build_info.update(properties)
                else:
                    build_info = properties
            else:
                logging.error('Failed to find %s in device.', filename)
        return build_info

    def has_arc_hardware_vulkan(self):
        """Returns a boolean whether device has hardware vulkan."""
        return self._get_arc_build_info().get('ro.hardware.vulkan')

    def get_arc_build_type(self):
        """Returns the ARC build type of the host."""
        return self._get_arc_build_info().get('ro.build.type')

    def get_arc_primary_abi(self):
        """Returns the primary abi of the host."""
        return self._get_arc_build_info().get('ro.product.cpu.abi')

    def get_arc_security_patch(self):
        """Returns the security patch of the host."""
        return self._get_arc_build_info().get('ro.build.version.security_patch')

    def get_arc_first_api_level(self):
        """Returns the security patch of the host."""
        return self._get_arc_build_info().get('ro.product.first_api_level')

    def _get_lsb_release_content(self):
        """Return the content of lsb-release file of host."""
        return self.run(
                'cat "%s"' % client_constants.LSB_RELEASE).stdout.strip()


    def get_release_version(self):
        """Get the value of attribute CHROMEOS_RELEASE_VERSION from lsb-release.

        @returns The version string in lsb-release, under attribute
                 CHROMEOS_RELEASE_VERSION.
        """
        return lsbrelease_utils.get_chromeos_release_version(
                lsb_release_content=self._get_lsb_release_content())


    def get_release_builder_path(self):
        """Get the value of CHROMEOS_RELEASE_BUILDER_PATH from lsb-release.

        @returns The version string in lsb-release, under attribute
                 CHROMEOS_RELEASE_BUILDER_PATH.
        """
        return lsbrelease_utils.get_chromeos_release_builder_path(
                lsb_release_content=self._get_lsb_release_content())


    def get_chromeos_release_milestone(self):
        """Get the value of attribute CHROMEOS_RELEASE_BUILD_TYPE
        from lsb-release.

        @returns The version string in lsb-release, under attribute
                 CHROMEOS_RELEASE_BUILD_TYPE.
        """
        return lsbrelease_utils.get_chromeos_release_milestone(
                lsb_release_content=self._get_lsb_release_content())


    def verify_cros_version_label(self):
        """Verify if host's cros-version label match the actual image in dut.

        @returns True if the label match with image in dut, otherwise False
        """
        os_from_host = self.get_release_builder_path()
        info = self.host_info_store.get()
        os_from_label = info.get_label_value(self.VERSION_PREFIX)
        if not os_from_label:
            logging.debug('No existing %s label detected', self.VERSION_PREFIX)
            return True

        # known cases where the version label will not match the
        # original CHROMEOS_RELEASE_BUILDER_PATH setting:
        #  * Tests for the `arc-presubmit` append "-cheetsth" to the label.
        if os_from_label.endswith(provision.CHEETS_SUFFIX):
            logging.debug('%s label with %s suffix detected, this suffix will'
                          ' be ignored when comparing label.',
                          self.VERSION_PREFIX, provision.CHEETS_SUFFIX)
            os_from_label = os_from_label[:-len(provision.CHEETS_SUFFIX)]
        logging.debug('OS version from host: %s; OS verision cached in '
                      'label: %s', os_from_host, os_from_label)
        return os_from_label == os_from_host


    def cleanup_services(self):
        """Reinitializes the device for cleanup.

        Subclasses may override this to customize the cleanup method.

        To indicate failure of the reset, the implementation may raise
        any of:
            error.AutoservRunError
            error.AutotestRunError
            FactoryImageCheckerException

        @raises error.AutoservRunError
        @raises error.AutotestRunError
        @raises error.FactoryImageCheckerException
        """
        self._restart_ui()
        self._start_powerd_if_needed()


    def cleanup(self):
        """Cleanup state on device."""
        self.run('rm -f %s' % client_constants.CLEANUP_LOGS_PAUSED_FILE)
        try:
            self.cleanup_services()
        except (error.AutotestRunError, error.AutoservRunError,
                FactoryImageCheckerException):
            logging.warning('Unable to restart ui.')

        # cleanup routines, i.e. reboot the machine.
        super(CrosHost, self).cleanup()

        # Check if the rpm outlet was manipulated.
        if self.has_power():
            self._cleanup_poweron()


    def reboot(self, **dargs):
        """
        This function reboots the site host. The more generic
        RemoteHost.reboot() performs sync and sleeps for 5
        seconds. This is not necessary for ChromeOS devices as the
        sync should be finished in a short time during the reboot
        command.
        """
        if 'reboot_cmd' not in dargs:
            reboot_timeout = dargs.get('reboot_timeout', 10)
            dargs['reboot_cmd'] = ('sleep 1; '
                                   'reboot & sleep %d; '
                                   'reboot -f' % reboot_timeout)
        # Enable fastsync to avoid running extra sync commands before reboot.
        if 'fastsync' not in dargs:
            dargs['fastsync'] = True

        dargs['board'] = self.host_info_store.get().board
        # Record who called us
        orig = sys._getframe(1).f_code
        metric_fields = {'board' : dargs['board'],
                         'dut_host_name' : self.hostname,
                         'success' : True}
        metric_debug_fields = {'board' : dargs['board'],
                               'caller' : "%s:%s" % (orig.co_filename,
                                                     orig.co_name),
                               'success' : True,
                               'error' : ''}

        t0 = time.time()
        logging.debug('Pre reboot lsb-release {}'.format(
                self._get_lsb_release_content()))
        try:
            super(CrosHost, self).reboot(**dargs)
        except Exception as e:
            metric_fields['success'] = False
            metric_debug_fields['success'] = False
            metric_debug_fields['error'] = type(e).__name__
            raise
        finally:
            duration = int(time.time() - t0)
            logging.debug('Post reboot lsb-release {}'.format(
                    self._get_lsb_release_content()))

            metrics.Counter(
                    'chromeos/autotest/autoserv/reboot_count').increment(
                    fields=metric_fields)
            metrics.Counter(
                    'chromeos/autotest/autoserv/reboot_debug').increment(
                    fields=metric_debug_fields)
            metrics.SecondsDistribution(
                    'chromeos/autotest/autoserv/reboot_duration').add(
                    duration, fields=metric_fields)

    def _default_suspend_cmd(self, suspend_time=60, delay_seconds=0):
        """
        Return the default suspend command

        @param suspend_time: How long to suspend as integer seconds.
        @param suspend_cmd: Suspend command to execute.

        @returns formatted suspend_cmd string to execute
        """
        suspend_cmd = ' && '.join([
            'echo 0 > /sys/class/rtc/rtc0/wakealarm',
            'echo +%d > /sys/class/rtc/rtc0/wakealarm' % suspend_time,
            'powerd_dbus_suspend --delay=%d' % delay_seconds])
        return suspend_cmd

    def suspend_bg(self, suspend_time=60, delay_seconds=0,
                suspend_cmd=None):
        """
        This function suspends the site host and returns right away.

        Note: use this when you need to perform work *while* the host is
        suspended.

        @param suspend_time: How long to suspend as integer seconds.
        @param suspend_cmd: Suspend command to execute.

        @exception AutoservSuspendError: if |suspend_cmd| fails
        """
        if suspend_cmd is None:
            suspend_cmd = self._default_suspend_cmd(suspend_time, delay_seconds)
        try:
            self.run_background(suspend_cmd)
        except error.AutoservRunError:
            raise error.AutoservSuspendError("suspend command failed")

    def suspend(self, suspend_time=60, delay_seconds=0,
                suspend_cmd=None, allow_early_resume=False):
        """
        This function suspends the site host.

        @param suspend_time: How long to suspend as integer seconds.
        @param suspend_cmd: Suspend command to execute.
        @param allow_early_resume: If False and if device resumes before
                                   |suspend_time|, throw an error.

        @exception AutoservSuspendError Host resumed earlier than
                                         |suspend_time|.
        """

        if suspend_cmd is None:
            suspend_cmd = self._default_suspend_cmd(suspend_time, delay_seconds)
        super(CrosHost, self).suspend(suspend_time, suspend_cmd,
                                      allow_early_resume);


    def upstart_status(self, service_name):
        """Check the status of an upstart init script.

        @param service_name: Service to look up.

        @returns True if the service is running, False otherwise.
        """
        return 'start/running' in self.run('status %s' % service_name,
                                           ignore_status=True).stdout

    def upstart_stop(self, service_name):
        """Stops an upstart job if it's running.

        @param service_name: Service to stop

        @returns True if service has been stopped or was already stopped
                 False otherwise.
        """
        if not self.upstart_status(service_name):
            return True

        result = self.run('stop %s' % service_name, ignore_status=True)
        if result.exit_status != 0:
            return False
        return True

    def upstart_restart(self, service_name):
        """Restarts (or starts) an upstart job.

        @param service_name: Service to start/restart

        @returns True if service has been started/restarted, False otherwise.
        """
        cmd = 'start'
        if self.upstart_status(service_name):
            cmd = 'restart'
        cmd = cmd + ' %s' % service_name
        result = self.run(cmd)
        if result.exit_status != 0:
            return False
        return True

    def verify_software(self):
        """Verify working software on a ChromeOS system.

        Tests for the following conditions:
         1. All conditions tested by the parent version of this
            function.
         2. Sufficient space in /mnt/stateful_partition.
         3. Sufficient space in /mnt/stateful_partition/encrypted.
         4. update_engine answers a simple status request over DBus.

        """
        super(CrosHost, self).verify_software()
        default_kilo_inodes_required = CONFIG.get_config_value(
                'SERVER', 'kilo_inodes_required', type=int, default=100)
        board = self.get_board().replace(ds_constants.BOARD_PREFIX, '')
        kilo_inodes_required = CONFIG.get_config_value(
                'SERVER', 'kilo_inodes_required_%s' % board,
                type=int, default=default_kilo_inodes_required)
        self.check_inodes('/mnt/stateful_partition', kilo_inodes_required)
        self.check_diskspace(
            '/mnt/stateful_partition',
            CONFIG.get_config_value(
                'SERVER', 'gb_diskspace_required', type=float,
                default=20.0))
        encrypted_stateful_path = '/mnt/stateful_partition/encrypted'
        # Not all targets build with encrypted stateful support.
        if self.path_exists(encrypted_stateful_path):
            self.check_diskspace(
                encrypted_stateful_path,
                CONFIG.get_config_value(
                    'SERVER', 'gb_encrypted_diskspace_required', type=float,
                    default=0.1))

        self.wait_for_system_services()

        # Factory images don't run update engine,
        # goofy controls dbus on these DUTs.
        if not self._is_factory_image():
            self.run('update_engine_client --status')


    @retry.retry(error.AutoservError, timeout_min=5, delay_sec=10)
    def wait_for_service(self, service_name):
        """Wait for target status of an upstart init script.

        @param service_name: Service to wait for.
        """
        if not self.upstart_status(service_name):
            raise error.AutoservError('Service %s not running.' % service_name)

    def wait_for_system_services(self):
        """Waits for system-services to be running.

        Sometimes, update_engine will take a while to update firmware, so we
        should give this some time to finish. See crbug.com/765686#c38 for
        details.
        """
        self.wait_for_service('system-services')


    def verify(self):
        """Verify ChromeOS system is in good state."""
        message = 'Beginning verify for host %s board %s model %s'
        info = self.host_info_store.get()
        message %= (self.hostname, info.board, info.model)
        self.record('INFO', None, None, message)
        try:
            self._repair_strategy.verify(self)
        except hosts.AutoservVerifyDependencyError as e:
            # We don't want flag a DUT as failed if only non-critical
            # verifier(s) failed during the repair.
            if e.is_critical():
                raise


    def make_ssh_command(self,
                         user='root',
                         port=None,
                         opts='',
                         hosts_file=None,
                         connect_timeout=None,
                         alive_interval=None,
                         alive_count_max=None,
                         connection_attempts=None):
        """Override default make_ssh_command to use options tuned for ChromeOS.

        Tuning changes:
          - ConnectTimeout=30; maximum of 30 seconds allowed for an SSH
          connection failure.  Consistency with remote_access.sh.

          - ServerAliveInterval=900; which causes SSH to ping connection every
          900 seconds. In conjunction with ServerAliveCountMax ensures
          that if the connection dies, Autotest will bail out.
          Originally tried 60 secs, but saw frequent job ABORTS where
          the test completed successfully. Later increased from 180 seconds to
          900 seconds to account for tests where the DUT is suspended for
          longer periods of time.

          - ServerAliveCountMax=3; consistency with remote_access.sh.

          - ConnectAttempts=4; reduce flakiness in connection errors;
          consistency with remote_access.sh.

          - UserKnownHostsFile=/dev/null; we don't care about the keys.
          Host keys change with every new installation, don't waste
          memory/space saving them.

          - SSH protocol forced to 2; needed for ServerAliveInterval.

        @param user User name to use for the ssh connection.
        @param port Port on the target host to use for ssh connection.
        @param opts Additional options to the ssh command.
        @param hosts_file Ignored.
        @param connect_timeout Ignored.
        @param alive_interval Ignored.
        @param alive_count_max Ignored.
        @param connection_attempts Ignored.
        """
        options = ' '.join([opts, '-o Protocol=2'])
        return super(CrosHost, self).make_ssh_command(
            user=user, port=port, opts=options, hosts_file='/dev/null',
            connect_timeout=30, alive_interval=900, alive_count_max=3,
            connection_attempts=4)


    def syslog(self, message, tag='autotest'):
        """Logs a message to syslog on host.

        @param message String message to log into syslog
        @param tag String tag prefix for syslog

        """
        self.run('logger -t "%s" "%s"' % (tag, message))


    def _ping_check_status(self, status):
        """Ping the host once, and return whether it has a given status.

        @param status Check the ping status against this value.
        @return True iff `status` and the result of ping are the same
                (i.e. both True or both False).

        """
        ping_val = utils.ping(self.hostname,
                              tries=1,
                              deadline=1,
                              timeout=2,
                              ignore_timeout=True)
        return not (status ^ (ping_val == 0))

    def _ping_wait_for_status(self, status, timeout):
        """Wait for the host to have a given status (UP or DOWN).

        Status is checked by polling.  Polling will not last longer
        than the number of seconds in `timeout`.  The polling
        interval will be long enough that only approximately
        _PING_WAIT_COUNT polling cycles will be executed, subject
        to a maximum interval of about one minute.

        @param status Waiting will stop immediately if `ping` of the
                      host returns this status.
        @param timeout Poll for at most this many seconds.
        @return True iff the host status from `ping` matched the
                requested status at the time of return.

        """
        # _ping_check_status() takes about 1 second, hence the
        # "- 1" in the formula below.
        # FIXME: if the ping command errors then _ping_check_status()
        # returns instantly. If timeout is also smaller than twice
        # _PING_WAIT_COUNT then the while loop below forks many
        # thousands of ping commands (see /tmp/test_that_results_XXXXX/
        # /results-1-logging_YYY.ZZZ/debug/autoserv.DEBUG) and hogs one
        # CPU core for 60 seconds.
        poll_interval = min(int(timeout / self._PING_WAIT_COUNT), 60) - 1
        end_time = time.time() + timeout
        while time.time() <= end_time:
            if self._ping_check_status(status):
                return True
            if poll_interval > 0:
                time.sleep(poll_interval)

        # The last thing we did was sleep(poll_interval), so it may
        # have been too long since the last `ping`.  Check one more
        # time, just to be sure.
        return self._ping_check_status(status)

    def ping_wait_up(self, timeout):
        """Wait for the host to respond to `ping`.

        N.B.  This method is not a reliable substitute for
        `wait_up()`, because a host that responds to ping will not
        necessarily respond to ssh.  This method should only be used
        if the target DUT can be considered functional even if it
        can't be reached via ssh.

        @param timeout Minimum time to allow before declaring the
                       host to be non-responsive.
        @return True iff the host answered to ping before the timeout.

        """
        if self.use_icmp:
            return self._ping_wait_for_status(self._PING_STATUS_UP, timeout)
        else:
            logging.debug('Using SSH instead of ICMP for ping_wait_up.')
            return self.wait_up(timeout)

    def ping_wait_down(self, timeout):
        """Wait until the host no longer responds to `ping`.

        This function can be used as a slightly faster version of
        `wait_down()`, by avoiding potentially long ssh timeouts.

        @param timeout Minimum time to allow for the host to become
                       non-responsive.
        @return True iff the host quit answering ping before the
                timeout.

        """
        if self.use_icmp:
            return self._ping_wait_for_status(self._PING_STATUS_DOWN, timeout)
        else:
            logging.debug('Using SSH instead of ICMP for ping_wait_down.')
            return self.wait_down(timeout)

    def _is_host_port_forwarded(self):
        """Checks if the dut is connected over port forwarding.

      N.B. This method does not detect all situations where port forwarding is
      occurring. Namely, running autotest on the dut may result in a
      false-positive, and port forwarding using a different machine on the
      same network will be a false-negative.

      @return True if the dut is connected over port forwarding
              False otherwise
      """
        is_localhost = self.hostname in ['localhost', '127.0.0.1']
        is_forwarded = is_localhost and not self.is_default_port
        if is_forwarded:
            logging.info('Detected DUT connected by port forwarding')
        return is_forwarded

    def test_wait_for_sleep(self, sleep_timeout=None):
        """Wait for the client to enter low-power sleep mode.

        The test for "is asleep" can't distinguish a system that is
        powered off; to confirm that the unit was asleep, it is
        necessary to force resume, and then call
        `test_wait_for_resume()`.

        This function is expected to be called from a test as part
        of a sequence like the following:

        ~~~~~~~~
            boot_id = host.get_boot_id()
            # trigger sleep on the host
            host.test_wait_for_sleep()
            # trigger resume on the host
            host.test_wait_for_resume(boot_id)
        ~~~~~~~~

        @param sleep_timeout time limit in seconds to allow the host sleep.

        @exception TestFail The host did not go to sleep within
                            the allowed time.
        """
        if sleep_timeout is None:
            sleep_timeout = self.SLEEP_TIMEOUT

        # If the dut is accessed over SSH port-forwarding, `ping` is not useful
        # for detecting the dut is down since a ping to localhost will always
        # succeed. In this case, fall back to wait_down() which uses SSH.
        if self._is_host_port_forwarded():
            success = self.wait_down(timeout=sleep_timeout)
        else:
            success = self.ping_wait_down(timeout=sleep_timeout)

        if not success:
            raise error.TestFail(
                'client failed to sleep after %d seconds' % sleep_timeout)


    def test_wait_for_resume(self, old_boot_id, resume_timeout=None):
        """Wait for the client to resume from low-power sleep mode.

        The `old_boot_id` parameter should be the value from
        `get_boot_id()` obtained prior to entering sleep mode.  A
        `TestFail` exception is raised if the boot id changes.

        See @ref test_wait_for_sleep for more on this function's
        usage.

        @param old_boot_id A boot id value obtained before the
                               target host went to sleep.
        @param resume_timeout time limit in seconds to allow the host up.

        @exception TestFail The host did not respond within the
                            allowed time.
        @exception TestFail The host responded, but the boot id test
                            indicated a reboot rather than a sleep
                            cycle.
        """
        if resume_timeout is None:
            resume_timeout = self.RESUME_TIMEOUT

        if not self.wait_up(timeout=resume_timeout):
            raise error.TestFail(
                'client failed to resume from sleep after %d seconds' %
                    resume_timeout)
        else:
            new_boot_id = self.get_boot_id()
            if new_boot_id != old_boot_id:
                logging.error('client rebooted (old boot %s, new boot %s)',
                              old_boot_id, new_boot_id)
                raise error.TestFail(
                    'client rebooted, but sleep was expected')


    def test_wait_for_shutdown(self, shutdown_timeout=None):
        """Wait for the client to shut down.

        The test for "has shut down" can't distinguish a system that
        is merely asleep; to confirm that the unit was down, it is
        necessary to force boot, and then call test_wait_for_boot().

        This function is expected to be called from a test as part
        of a sequence like the following:

        ~~~~~~~~
            boot_id = host.get_boot_id()
            # trigger shutdown on the host
            host.test_wait_for_shutdown()
            # trigger boot on the host
            host.test_wait_for_boot(boot_id)
        ~~~~~~~~

        @param shutdown_timeout time limit in seconds to allow the host down.
        @exception TestFail The host did not shut down within the
                            allowed time.
        """
        if shutdown_timeout is None:
            shutdown_timeout = self.SHUTDOWN_TIMEOUT

        if self._is_host_port_forwarded():
            success = self.wait_down(timeout=shutdown_timeout)
        else:
            success = self.ping_wait_down(timeout=shutdown_timeout)

        if not success:
            raise error.TestFail(
                'client failed to shut down after %d seconds' %
                    shutdown_timeout)


    def test_wait_for_boot(self, old_boot_id=None):
        """Wait for the client to boot from cold power.

        The `old_boot_id` parameter should be the value from
        `get_boot_id()` obtained prior to shutting down.  A
        `TestFail` exception is raised if the boot id does not
        change.  The boot id test is omitted if `old_boot_id` is not
        specified.

        See @ref test_wait_for_shutdown for more on this function's
        usage.

        @param old_boot_id A boot id value obtained before the
                               shut down.

        @exception TestFail The host did not respond within the
                            allowed time.
        @exception TestFail The host responded, but the boot id test
                            indicated that there was no reboot.
        """
        if not self.wait_up(timeout=self.REBOOT_TIMEOUT):
            raise error.TestFail(
                'client failed to reboot after %d seconds' %
                    self.REBOOT_TIMEOUT)
        elif old_boot_id:
            if self.get_boot_id() == old_boot_id:
                logging.error('client not rebooted (boot %s)',
                              old_boot_id)
                raise error.TestFail(
                    'client is back up, but did not reboot')


    @staticmethod
    def check_for_rpm_support(hostname):
        """For a given hostname, return whether or not it is powered by an RPM.

        @param hostname: hostname to check for rpm support.

        @return None if this host does not follows the defined naming format
                for RPM powered DUT's in the lab. If it does follow the format,
                it returns a regular expression MatchObject instead.
        """
        return re.match(CrosHost._RPM_HOSTNAME_REGEX, hostname)


    def has_power(self):
        """For this host, return whether or not it is powered by an RPM.

        @return True if this host is in the CROS lab and follows the defined
                naming format.
        """
        return CrosHost.check_for_rpm_support(self.hostname)


    def _set_power(self, state, power_method):
        """Sets the power to the host via RPM, CCD, Servo or manual.

        @param state Specifies which power state to set to DUT
        @param power_method Specifies which method of power control to
                            use. By default "RPM" or "CCD" will be used based
                            on servo type. Valid values from
                            POWER_CONTROL_VALID_ARGS, or None to use default.

        """
        ACCEPTABLE_STATES = ['ON', 'OFF']

        if not power_method:
            power_method = self.get_default_power_method()

        state = state.upper()
        if state not in ACCEPTABLE_STATES:
            raise error.TestError('State must be one of: %s.'
                                   % (ACCEPTABLE_STATES,))

        if power_method == self.POWER_CONTROL_SERVO:
            logging.info('Setting servo port J10 to %s', state)
            self.servo.set('prtctl3_pwren', state.lower())
            time.sleep(self._USB_POWER_TIMEOUT)
        elif power_method == self.POWER_CONTROL_MANUAL:
            logging.info('You have %d seconds to set the AC power to %s.',
                         self._POWER_CYCLE_TIMEOUT, state)
            time.sleep(self._POWER_CYCLE_TIMEOUT)
        elif power_method == self.POWER_CONTROL_CCD:
            servo_role = 'src' if state == 'ON' else 'snk'
            logging.info('servo ccd power pass through detected,'
                         ' changing servo_role to %s.', servo_role)
            self.servo.set_servo_v4_role(servo_role)
            if not self.ping_wait_up(timeout=self._CHANGE_SERVO_ROLE_TIMEOUT):
                # Make sure we don't leave DUT with no power(servo_role=snk)
                # when DUT is not pingable, as we raise a exception here
                # that may break a power cycle in the middle.
                self.servo.set_servo_v4_role('src')
                raise error.AutoservError(
                    'DUT failed to regain network connection after %d seconds.'
                    % self._CHANGE_SERVO_ROLE_TIMEOUT)
        else:
            if not self.has_power():
                raise error.TestFail('DUT does not have RPM connected.')
            self._add_rpm_changed_tag()
            rpm_client.set_power(self, state, timeout_mins=5)


    def power_off(self, power_method=None):
        """Turn off power to this host via RPM, CCD, Servo or manual.

        @param power_method Specifies which method of power control to
                            use. By default "RPM" or "CCD" will be used based
                            on servo type. Valid values from
                            POWER_CONTROL_VALID_ARGS, or None to use default.

        """
        self._sync_if_up()
        self._set_power('OFF', power_method)

    def _check_supported(self):
        """Throw an error if dts mode control is not supported."""
        if not self.servo_pwr_supported:
            raise error.TestFail('power_state controls not supported')

    def _sync_if_up(self):
        """Run sync on the DUT and wait for completion if the DUT is up.

        Additionally, try to sync and ignore status if its not up.

        Useful prior to reboots to ensure files are written to disc.

        """
        if self.is_up_fast():
            self.run("sync")
            return
        # If it is not up, attempt to sync in the rare event the DUT is up but
        # doesn't respond to a ping. Ignore any errors.
        try:
            self.run("sync", ignore_status=True, timeout=1)
        except Exception:
            pass

    def power_off_via_servo(self):
        """Force the DUT to power off.

        The DUT is guaranteed to be off at the end of this call,
        regardless of its previous state, provided that there is
        working EC and boot firmware.  There is no requirement for
        working OS software.

        """
        self._check_supported()
        self._sync_if_up()
        self.servo.set_nocheck('power_state', 'off')

    def power_on_via_servo(self, rec_mode='on'):
        """Force the DUT to power on.

        Prior to calling this function, the DUT must be powered off,
        e.g. with a call to `power_off()`.

        At power on, recovery mode is set as specified by the
        corresponding argument.  When booting with recovery mode on, it
        is the caller's responsibility to unplug/plug in a bootable
        external storage device.

        If the DUT requires a delay after powering on but before
        processing inputs such as USB stick insertion, the delay is
        handled by this method; the caller is not responsible for such
        delays.

        @param rec_mode Setting of recovery mode to be applied at
                        power on. default: REC_OFF aka 'off'

        """
        self._check_supported()
        self.servo.set_nocheck('power_state', rec_mode)

    def reset_via_servo(self):
        """Force the DUT to reset.

        The DUT is guaranteed to be on at the end of this call,
        regardless of its previous state, provided that there is
        working OS software. This also guarantees that the EC has
        been restarted.

        """
        self._check_supported()
        self._sync_if_up()
        self.servo.set_nocheck('power_state', 'reset')


    def power_on(self, power_method=None):
        """Turn on power to this host via RPM, CCD, Servo or manual.

        @param power_method Specifies which method of power control to
                            use. By default "RPM" or "CCD" will be used based
                            on servo type. Valid values from
                            POWER_CONTROL_VALID_ARGS, or None to use default.

        """
        self._set_power('ON', power_method)


    def power_cycle(self, power_method=None):
        """Cycle power to this host by turning it OFF, then ON.

        @param power_method Specifies which method of power control to
                            use. By default "RPM" or "CCD" will be used based
                            on servo type. Valid values from
                            POWER_CONTROL_VALID_ARGS, or None to use default.

        """
        if not power_method:
            power_method = self.get_default_power_method()

        if power_method in (self.POWER_CONTROL_SERVO,
                            self.POWER_CONTROL_MANUAL,
                            self.POWER_CONTROL_CCD):
            self.power_off(power_method=power_method)
            time.sleep(self._POWER_CYCLE_TIMEOUT)
            self.power_on(power_method=power_method)
        else:
            self._add_rpm_changed_tag()
            rpm_client.set_power(self, 'CYCLE')


    def get_platform_from_fwid(self):
        """Determine the platform from the crossystem fwid.

        @returns a string representing this host's platform.
        """
        # Look at the firmware for non-unibuild cases or if cros_config fails.
        crossystem = utils.Crossystem(self)
        crossystem.init()
        # Extract fwid value and use the leading part as the platform id.
        # fwid generally follow the format of {platform}.{firmware version}
        # Example: Alex.X.YYY.Z or Google_Alex.X.YYY.Z
        platform = crossystem.fwid().split('.')[0].lower()
        # Newer platforms start with 'Google_' while the older ones do not.
        return platform.replace('google_', '')


    def get_platform(self):
        """Determine the correct platform label for this host.

        @returns a string representing this host's platform.
        """
        release_info = utils.parse_cmd_output('cat /etc/lsb-release',
                                              run_method=self.run)
        platform = ''
        if release_info.get('CHROMEOS_RELEASE_UNIBUILD') == '1':
            platform = self.get_model_from_cros_config()
        return platform if platform else self.get_platform_from_fwid()


    def get_model_from_cros_config(self):
        """Get the host model from cros_config command.

        @returns a string representing this host's model.
        """
        return cros_config.call_cros_config_get_output('/ name',
                self.run, ignore_status=True)


    def get_architecture(self):
        """Determine the correct architecture label for this host.

        @returns a string representing this host's architecture.
        """
        crossystem = utils.Crossystem(self)
        crossystem.init()
        return crossystem.arch()


    def get_chrome_version(self):
        """Gets the Chrome version number and milestone as strings.

        Invokes "chrome --version" to get the version number and milestone.

        @return A tuple (chrome_ver, milestone) where "chrome_ver" is the
            current Chrome version number as a string (in the form "W.X.Y.Z")
            and "milestone" is the first component of the version number
            (the "W" from "W.X.Y.Z").  If the version number cannot be parsed
            in the "W.X.Y.Z" format, the "chrome_ver" will be the full output
            of "chrome --version" and the milestone will be the empty string.

        """
        version_string = self.run(client_constants.CHROME_VERSION_COMMAND).stdout
        return utils.parse_chrome_version(version_string)


    def get_ec_version(self):
        """Get the ec version as strings.

        @returns a string representing this host's ec version.
        """
        command = 'mosys ec info -s fw_version'
        result = self.run(command, ignore_status=True)
        if result.exit_status != 0:
            return ''
        return result.stdout.strip()


    def get_firmware_version(self):
        """Get the firmware version as strings.

        @returns a string representing this host's firmware version.
        """
        crossystem = utils.Crossystem(self)
        crossystem.init()
        return crossystem.fwid()


    def get_hardware_id(self):
        """Get hardware id as strings.

        @returns a string representing this host's hardware id.
        """
        crossystem = utils.Crossystem(self)
        crossystem.init()
        return crossystem.hwid()

    def get_hardware_revision(self):
        """Get the hardware revision as strings.

        @returns a string representing this host's hardware revision.
        """
        command = 'mosys platform version'
        result = self.run(command, ignore_status=True)
        if result.exit_status != 0:
            return ''
        return result.stdout.strip()


    def get_kernel_version(self):
        """Get the kernel version as strings.

        @returns a string representing this host's kernel version.
        """
        return self.run('uname -r').stdout.strip()


    def get_cpu_name(self):
        """Get the cpu name as strings.

        @returns a string representing this host's cpu name.
        """

        # Try get cpu name from device tree first
        if self.path_exists('/proc/device-tree/compatible'):
            command = ' | '.join(
                    ["sed -e 's/\\x0/\\n/g' /proc/device-tree/compatible",
                     'tail -1'])
            return self.run(command).stdout.strip().replace(',', ' ')

        # Get cpu name from uname -p
        command = 'uname -p'
        ret = self.run(command).stdout.strip()

        # 'uname -p' return variant of unknown or amd64 or x86_64 or i686
        # Try get cpu name from /proc/cpuinfo instead
        if re.match("unknown|amd64|[ix][0-9]?86(_64)?", ret, re.IGNORECASE):
            command = "grep model.name /proc/cpuinfo | cut -f 2 -d: | head -1"
            self = self.run(command).stdout.strip()

        # Remove bloat from CPU name, for example
        # Intel(R) Core(TM) i5-7Y57 CPU @ 1.20GHz       -> Intel Core i5-7Y57
        # Intel(R) Xeon(R) CPU E5-2690 v4 @ 2.60GHz     -> Intel Xeon E5-2690 v4
        # AMD A10-7850K APU with Radeon(TM) R7 Graphics -> AMD A10-7850K
        # AMD GX-212JC SOC with Radeon(TM) R2E Graphics -> AMD GX-212JC
        trim_re = r' (@|processor|apu|soc|radeon).*|\(.*?\)| cpu'
        return re.sub(trim_re, '', ret, flags=re.IGNORECASE)


    def get_screen_resolution(self):
        """Get the screen(s) resolution as strings.
        In case of more than 1 monitor, return resolution for each monitor
        separate with plus sign.

        @returns a string representing this host's screen(s) resolution.
        """
        command = 'for f in /sys/class/drm/*/*/modes; do head -1 $f; done'
        ret = self.run(command, ignore_status=True)
        # We might have Chromebox without a screen
        if ret.exit_status != 0:
            return ''
        return ret.stdout.strip().replace('\n', '+')


    def get_mem_total_gb(self):
        """Get total memory available in the system in GiB (2^20).

        @returns an integer representing total memory
        """
        mem_total_kb = self.read_from_meminfo('MemTotal')
        kb_in_gb = float(2 ** 20)
        return int(round(mem_total_kb / kb_in_gb))


    def get_disk_size_gb(self):
        """Get size of disk in GB (10^9)

        @returns an integer representing  size of disk, 0 in Error Case
        """
        command = 'grep $(rootdev -s -d | cut -f3 -d/)$ /proc/partitions'
        result = self.run(command, ignore_status=True)
        if result.exit_status != 0:
            return 0
        _, _, block, _ = re.split(r' +', result.stdout.strip())
        byte_per_block = 1024.0
        disk_kb_in_gb = 1e9
        return int(int(block) * byte_per_block / disk_kb_in_gb + 0.5)


    def get_battery_size(self):
        """Get size of battery in Watt-hour via sysfs

        This method assumes that battery support voltage_min_design and
        charge_full_design sysfs.

        @returns a float representing Battery size, 0 if error.
        """
        # sysfs report data in micro scale
        battery_scale = 1e6

        command = 'cat /sys/class/power_supply/*/voltage_min_design'
        result = self.run(command, ignore_status=True)
        if result.exit_status != 0:
            return 0
        voltage = float(result.stdout.strip()) / battery_scale

        command = 'cat /sys/class/power_supply/*/charge_full_design'
        result = self.run(command, ignore_status=True)
        if result.exit_status != 0:
            return 0
        amphereHour = float(result.stdout.strip()) / battery_scale

        return voltage * amphereHour


    def get_low_battery_shutdown_percent(self):
        """Get the percent-based low-battery shutdown threshold.

        @returns a float representing low-battery shutdown percent, 0 if error.
        """
        ret = 0.0
        try:
            command = 'check_powerd_config --low_battery_shutdown_percent'
            ret = float(self.run(command).stdout)
        except error.CmdError:
            logging.debug("Can't run %s", command)
        except ValueError:
            logging.debug("Didn't get number from %s", command)

        return ret


    def has_hammer(self):
        """Check whether DUT has hammer device or not.

        @returns boolean whether device has hammer or not
        """
        command = 'grep Hammer /sys/bus/usb/devices/*/product'
        return self.run(command, ignore_status=True).exit_status == 0


    def is_chrome_switch_present(self, switch):
        """Returns True if the specified switch was provided to Chrome.

        @param switch The chrome switch to search for.
        """

        command = 'pgrep -x -f -c "/opt/google/chrome/chrome.*%s.*"' % switch
        return self.run(command, ignore_status=True).exit_status == 0


    def oobe_triggers_update(self):
        """Returns True if this host has an OOBE flow during which
        it will perform an update check and perhaps an update.
        One example of such a flow is Hands-Off Zero-Touch Enrollment.
        As more such flows are developed, code handling them needs
        to be added here.

        @return Boolean indicating whether this host's OOBE triggers an update.
        """
        return self.is_chrome_switch_present(
            '--enterprise-enable-zero-touch-enrollment=hands-off')


    # TODO(kevcheng): change this to just return the board without the
    # 'board:' prefix and fix up all the callers.  Also look into removing the
    # need for this method.
    def get_board(self):
        """Determine the correct board label for this host.

        @returns a string representing this host's board.
        """
        release_info = utils.parse_cmd_output('cat /etc/lsb-release',
                                              run_method=self.run)
        return (ds_constants.BOARD_PREFIX +
                release_info['CHROMEOS_RELEASE_BOARD'])

    def get_channel(self):
        """Determine the correct channel label for this host.

        @returns: a string represeting this host's build channel.
                  (stable, dev, beta). None on fail.
        """
        return lsbrelease_utils.get_chromeos_channel(
                lsb_release_content=self._get_lsb_release_content())

    def get_power_supply(self):
        """
        Determine what type of power supply the host has

        @returns a string representing this host's power supply.
                 'power:battery' when the device has a battery intended for
                        extended use
                 'power:AC_primary' when the device has a battery not intended
                        for extended use (for moving the machine, etc)
                 'power:AC_only' when the device has no battery at all.
        """
        psu = self.run(command='cros_config /hardware-properties psu-type',
                       ignore_status=True)
        if psu.exit_status:
            # Assume battery if unspecified in cros_config.
            return 'power:battery'

        psu_str = psu.stdout.strip()
        if psu_str == 'unknown':
            return None

        return 'power:%s' % psu_str


    def has_battery(self):
        """Determine if DUT has a battery.

        Returns:
            Boolean, False if known not to have battery, True otherwise.
        """
        return self.get_power_supply() == 'power:battery'


    def get_servo(self):
        """Determine if the host has a servo attached.

        If the host has a working servo attached, it should have a servo label.

        @return: string 'servo' if the host has servo attached. Otherwise,
                 returns None.
        """
        return 'servo' if self._servo_host else None

    def _has_display(self, internal):
        """ Determine if the device under test is equipped with a display
        @params internal: True if checking internal display else checking
                          external display.
        @return: 'internal_display' if internal is true and internal display
                 present;
                 'external_display' if internal is false and external display
                 present;
                 None otherwise.
        """
        from autotest_lib.client.cros.graphics import graphics_utils
        from autotest_lib.client.common_lib import utils as common_utils

        def __system_output(cmd):
            return self.run(cmd).stdout

        def __read_file(remote_path):
            return self.run('cat %s' % remote_path).stdout

        # Hijack the necessary client functions so that we can take advantage
        # of the client lib here.
        # FIXME: find a less hacky way than this
        original_system_output = utils.system_output
        original_read_file = common_utils.read_file
        utils.system_output = __system_output
        common_utils.read_file = __read_file
        try:
            if internal:
                return ('internal_display'
                        if graphics_utils.has_internal_display() else None)
            else:
                return ('external_display'
                        if graphics_utils.has_external_display() else None)
        finally:
            utils.system_output = original_system_output
            common_utils.read_file = original_read_file


    def has_internal_display(self):
        """Determine if the device under test is equipped with an internal
        display.

        @return: 'internal_display' if one is present; None otherwise.
        """
        return self._has_display(True)

    def has_external_display(self):
        """Determine if the device under test is equipped with an external
        display.

        @return: 'external_display' if one is present; None otherwise.
        """
        return self._has_display(False)

    def is_boot_from_usb(self):
        """Check if DUT is boot from USB.

        @return: True if DUT is boot from usb.
        """
        device = self.run('rootdev -s -d').stdout.strip()
        removable = int(self.run('cat /sys/block/%s/removable' %
                                 os.path.basename(device)).stdout.strip())
        return removable == 1

    def is_boot_from_external_device(self):
        """Check if DUT is boot from external storage.

        @return: True if DUT is boot from external storage.
        """
        boot_device = self.run('rootdev -s -d', ignore_status=True,
                               timeout=60).stdout.strip()
        if not boot_device:
            logging.debug('Boot storage not detected on the host.')
            return False
        main_storage_cmd = ('. /usr/sbin/write_gpt.sh;'
                            ' . /usr/share/misc/chromeos-common.sh;'
                            ' load_base_vars; get_fixed_dst_drive')
        main_storage = self.run(main_storage_cmd,
                                ignore_status=True,
                                timeout=60).stdout.strip()
        if not main_storage or boot_device != main_storage:
            logging.debug('Device booted from external storage storage.')
            return True
        logging.debug('Device booted from main storage.')
        return False

    def read_from_meminfo(self, key):
        """Return the memory info from /proc/meminfo

        @param key: meminfo requested

        @return the memory value as a string

        """
        meminfo = self.run('grep %s /proc/meminfo' % key).stdout.strip()
        logging.debug('%s', meminfo)
        return int(re.search(r'\d+', meminfo).group(0))


    def get_cpu_arch(self):
        """Returns CPU arch of the device.

        @return CPU architecture of the DUT.
        """
        # Add CPUs by following logic in client/bin/utils.py.
        if self.run("grep '^flags.*:.* lm .*' /proc/cpuinfo",
                ignore_status=True).stdout:
            return 'x86_64'
        if self.run("grep -Ei 'ARM|CPU implementer' /proc/cpuinfo",
                ignore_status=True).stdout:
            return 'arm'
        return 'i386'


    def get_board_type(self):
        """
        Get the DUT's device type / form factor from cros_config. It can be one
        of CHROMEBOX, CHROMEBASE, CHROMEBOOK, or CHROMEBIT.

        @return form factor value from cros_config.
        """

        device_type = self.run('cros_config /hardware-properties form-factor',
                ignore_status=True).stdout
        if device_type:
            return device_type

        # TODO: remove lsb-release fallback once cros_config works everywhere
        device_type = self.run('grep DEVICETYPE /etc/lsb-release',
                               ignore_status=True).stdout
        if device_type:
            return device_type.split('=')[-1].strip()
        return ''


    def get_arc_version(self):
        """Return ARC version installed on the DUT.

        @returns ARC version as string if the CrOS build has ARC, else None.
        """
        arc_version = self.run('grep CHROMEOS_ARC_VERSION /etc/lsb-release',
                               ignore_status=True).stdout
        if arc_version:
            return arc_version.split('=')[-1].strip()
        return None


    def get_os_type(self):
        return 'cros'


    def get_labels(self):
        """Return the detected labels on the host."""
        return self.labels.get_labels(self)


    def get_default_power_method(self):
        """
        Get the default power method for power_on/off/cycle() methods.
        @return POWER_CONTROL_RPM or POWER_CONTROL_CCD
        """
        if not self._default_power_method:
            self._default_power_method = self.POWER_CONTROL_RPM
            if self.servo and self.servo.supports_built_in_pd_control():
                self._default_power_method = self.POWER_CONTROL_CCD
            else:
                logging.debug('Either servo is unitialized or the servo '
                              'setup does not support pd controls. Falling '
                              'back to default RPM method.')
        return self._default_power_method


    def find_usb_devices(self, idVendor, idProduct):
        """
        Get usb device sysfs name for specific device.

        @param idVendor  Vendor ID to search in sysfs directory.
        @param idProduct Product ID to search in sysfs directory.

        @return Usb node names in /sys/bus/usb/drivers/usb/ that match.
        """
        # Look for matching file and cut at position 7 to get dir name.
        grep_cmd = 'grep {} /sys/bus/usb/drivers/usb/*/{} | cut -f 7 -d /'

        vendor_cmd = grep_cmd.format(idVendor, 'idVendor')
        product_cmd = grep_cmd.format(idProduct, 'idProduct')

        # Use uniq -d to print duplicate line from both command
        cmd = 'sort <({}) <({}) | uniq -d'.format(vendor_cmd, product_cmd)

        return self.run(cmd, ignore_status=True).stdout.strip().split('\n')


    def bind_usb_device(self, usb_node):
        """
        Bind usb device

        @param usb_node Node name in /sys/bus/usb/drivers/usb/
        """
        cmd = 'echo {} > /sys/bus/usb/drivers/usb/bind'.format(usb_node)
        self.run(cmd, ignore_status=True)


    def unbind_usb_device(self, usb_node):
        """
        Unbind usb device

        @param usb_node Node name in /sys/bus/usb/drivers/usb/
        """
        cmd = 'echo {} > /sys/bus/usb/drivers/usb/unbind'.format(usb_node)
        self.run(cmd, ignore_status=True)


    def get_wlan_ip(self):
        """
        Get ip address of wlan interface.

        @return ip address of wlan or empty string if wlan is not connected.
        """
        cmds = [
            'iw dev',                   # List wlan physical device
            'grep Interface',           # Grep only interface name
            'cut -f 2 -d" "',           # Cut the name part
            'xargs ifconfig',           # Feed it to ifconfig to get ip
            'grep -oE "inet [0-9.]+"',  # Grep only ipv4
            'cut -f 2 -d " "'           # Cut the ip part
        ]
        return self.run(' | '.join(cmds), ignore_status=True).stdout.strip()

    def connect_to_wifi(self, ssid, passphrase=None, security=None):
        """
        Connect to wifi network

        @param ssid       SSID of the wifi network.
        @param passphrase Passphrase of the wifi network. None if not existed.
        @param security   Security of the wifi network. Default to "psk" if
                          passphase is given without security. Possible values
                          are "none", "psk", "802_1x".

        @return True if succeed, False if not.
        """
        cmd = '/usr/local/autotest/cros/scripts/wifi connect ' + ssid
        if passphrase:
            cmd += ' ' + passphrase
            if security:
                cmd += ' ' + security
        return self.run(cmd, ignore_status=True).exit_status == 0

    def get_device_repair_state(self):
        """Get device repair state"""
        return self._device_repair_state

    def is_marked_for_replacement(self):
        """Verify if device was marked for replacemnet during admin task."""
        expected_state = cros_constants.DEVICE_STATE_NEEDS_REPLACEMENT
        return self.get_device_repair_state() == expected_state

    def set_device_repair_state(self, state, resultdir=None):
        """Set device repair state.

        The special device state will be written to the 'dut_state.repair'
        file in result directory. The file will be read by Lucifer. The
        file will not be created if result directory not specified.

        @params state:      The new state for the device.
        @params resultdir:  The path to result directory. If path not provided
                            will be attempt to get retrieve it from job
                            if present.
        """
        resultdir = resultdir or getattr(self.job, 'resultdir', '')
        if resultdir:
            target = os.path.join(resultdir, 'dut_state.repair')
            common_utils.open_write_close(target, state)
            logging.info('Set device state as %s. '
                         'Created dut_state.repair file.', state)
        else:
            logging.debug('Cannot write the device state due missing info '
                          'about result dir.')
        self._device_repair_state = state

    def set_device_needs_replacement(self, resultdir=None):
        """Set device as required replacement.

        @params resultdir:  The path to result directory. If path not provided
                            will be attempt to get retrieve it from job
                            if present.
        """
        self.set_device_repair_state(
            cros_constants.DEVICE_STATE_NEEDS_REPLACEMENT,
            resultdir=resultdir)

    def _dut_is_accessible_by_verifier(self):
        """Check if DUT accessible by SSH or PING verifier.

        @returns: bool, True - verifier marked as success.
                        False - result not reachable, verifier did not success.
        """
        if not self._repair_strategy:
            return False
        dut_ssh = self._repair_strategy.verifier_is_good('ssh')
        dut_ping = self._repair_strategy.verifier_is_good('ping')
        return dut_ssh == hosts.VERIFY_SUCCESS or dut_ssh == hosts.VERIFY_SUCCESS

    def _stat_if_pingable_but_not_sshable(self):
        """Check if DUT pingable but failed SSH verifier."""
        if not self._repair_strategy:
            return
        dut_ssh = self._repair_strategy.verifier_is_good('ssh')
        dut_ping = self._repair_strategy.verifier_is_good('ping')
        if (dut_ping == hosts.VERIFY_FAILED
                    and dut_ssh == hosts.VERIFY_FAILED):
            metrics.Counter('chromeos/autotest/dut_pingable_no_ssh').increment(
                    fields={'host': self.hostname})

    def try_set_device_needs_manual_repair(self):
        """Check if device require manual attention to be fixed.

        The state 'needs_manual_repair' can be set when auto repair cannot
        fix the device due hardware or cable issues.
        """
        # ignore the logic if state present
        # state can be set by any cros repair actions
        if self.get_device_repair_state():
            return
        if self._dut_is_accessible_by_verifier():
            # DUT is accessible and we still have many options to repair it.
            return
        needs_manual_repair = False
        dhp = self.health_profile
        if dhp and dhp.get_repair_fail_count() > 49:
            # 42 = 6 times during 7 days. (every 4 hour repair)
            # round up to 50 in case somebody will run some attempt on it.
            logging.info(
                    'DUT is not sshable and fail %s times.'
                    ' Limit to try repair is 50 times',
                    dhp.get_repair_fail_count())
            needs_manual_repair = True

        if not needs_manual_repair:
            # We cannot ssh to the DUT and we have hardware or set-up issues
            # with servo then we need request manual repair for the DUT.
            servo_state_required_manual_fix = [
                    servo_constants.SERVO_STATE_DUT_NOT_CONNECTED,
                    servo_constants.SERVO_STATE_NEED_REPLACEMENT,
            ]
            if self.get_servo_state() in servo_state_required_manual_fix:
                logging.info(
                        'DUT required manual repair because it is not sshable'
                        ' and possible have setup issue with Servo. Please'
                        ' verify all connections and present of devices.')
                needs_manual_repair = True

        if needs_manual_repair:
            self.set_device_repair_state(
                    cros_constants.DEVICE_STATE_NEEDS_MANUAL_REPAIR)

    def _reboot_labstation_if_needed(self):
        """Place request to reboot the labstation if DUT is not sshable.

        @returns: None
        """
        message_prefix = "Don't need to request servo-host reboot"
        if self._dut_is_accessible_by_verifier():
            return
        if not self._servo_host:
            logging.debug('%s as it not initialized', message_prefix)
            return
        if not self._servo_host.is_up_fast():
            logging.debug('%s as servo-host is not sshable', message_prefix)
            return
        if not self._servo_host.is_labstation():
            logging.debug('Servo_v3 is not requested to reboot for the DUT')
            return
        usb_path = self._servo_host.get_main_servo_usb_path()
        if usb_path:
            connected_port = os.path.basename(os.path.normpath(usb_path))
            # Directly connected servo to the labstation looks like '1-5.3'
            # and when connected by hub - '1-5.2.3' or '1-5.2.1.3'. Where:
            # - '1-5' - port on labstation
            # - '2' or '2.1'   - port on the hub or smart-hub
            # - '3'   - port on servo hub
            if len(connected_port.split('.')) > 2:
                logging.debug('%s as servo connected by hub', message_prefix)
                return
        self._servo_host.request_reboot()
        logging.info('Requested labstation reboot because DUT is not sshable')

    def is_file_system_writable(self, testdirs=None):
        """Check is the file systems are writable.

        The standard linux response to certain unexpected file system errors
        (including hardware errors in block devices) is to change the file
        system status to read-only. This checks that that hasn't happened.

        @param testdirs: List of directories to check. If no data provided
                         then '/mnt/stateful_partition' and '/var/tmp'
                         directories will be checked.

        @returns boolean whether file-system writable.
        """
        def _check_dir(testdir):
            # check if we can create a file
            filename = os.path.join(testdir, 'writable_my_test_file')
            command = 'touch %s && rm %s' % (filename, filename)
            rv = self.run(command=command,
                          timeout=30,
                          ignore_status=True)
            is_writable = rv.exit_status == 0
            if not is_writable:
                logging.info('Cannot create a file in "%s"!'
                             ' Probably the FS is read-only', testdir)
                logging.info("FileSystem is not writable!")
                return False
            return True

        if not testdirs or len(testdirs) == 0:
            # N.B. Order matters here:  Encrypted stateful is loop-mounted
            # from a file in unencrypted stateful, so we don't test for
            # errors in encrypted stateful if unencrypted fails.
            testdirs = ['/mnt/stateful_partition', '/var/tmp']

        for dir in testdirs:
            # loop will be stopped if any directory fill fail the check
            try:
                if not _check_dir(dir):
                    return False
            except Exception as e:
                # here expected only timeout error, all other will
                # be catch by 'ignore_status=True'
                logging.debug('Fail to check %s to write in it', dir)
                return False
        return True

    def blocking_sync(self, freeze_for_reset=False):
        """Sync root device and internal device, via script.

        The actual calls end up logged by the run() call, since they're printed
        to stdout/stderr in the script.

        @param freeze_for_reset: if True, prepare for reset by blocking writes
                                 (only if enable_fs_sync_fsfreeze=True)
        """

        if freeze_for_reset and self.USE_FSFREEZE:
            logging.info('Blocking sync and freeze')
        elif freeze_for_reset:
            logging.info('Blocking sync for reset')
        else:
            logging.info('Blocking sync')

        # client/bin is installed on the DUT as /usr/local/autotest/bin
        sync_cmd = '/usr/local/autotest/bin/fs_sync.py'
        if freeze_for_reset and self.USE_FSFREEZE:
            sync_cmd += ' --freeze'
        return self.run(sync_cmd)

    def set_health_profile_dut_state(self, state):
        if not self.health_profile:
            logging.debug('Device health profile is not initialized, skip'
                          ' set dut state.')
            return
        reset_counters = state in profile_constants.STATES_NEED_RESET_COUNTER
        self.health_profile.update_dut_state(state, reset_counters)

    def require_snk_mode_in_recovery(self):
        """Check whether we need to switch servo_v4 role to snk when
        booting into recovery mode. (See crbug.com/1129165)
        """
        has_battery = True
        # Determine if the host has battery based on host_info first.
        power_info = self.host_info_store.get().get_label_value('power')
        if power_info:
            has_battery = power_info == 'battery'
        elif self.is_up_fast():
            # when running local tests host_info is not available, so we
            # need to determine whether the host has battery by checking
            # from host side.
            logging.debug('Label `power` is not found in host_info, checking'
                          ' if the host has battery from host side.')
            has_battery = self.has_battery()

        if not has_battery:
            logging.info(
                    '%s does not has battery, snk mode is not needed'
                    ' for recovery.', self.hostname)
            return False

        if not self.servo.supports_built_in_pd_control():
            logging.info('Power delivery is not supported on this servo, snk'
                         ' mode is not needed for recovery.')
            return False
        try:
            battery_percent = self.servo.get('battery_charge_percent')
            if battery_percent < cros_constants.MIN_BATTERY_LEVEL:
                logging.info(
                        'Current battery level %s%% below %s%% threshold, we'
                        ' will attempt to boot host in recovery mode without'
                        ' changing servo to snk mode. Please note the host may'
                        ' not able to see usb drive in recovery mode later due'
                        ' to servo not in snk mode.', battery_percent,
                        cros_constants.MIN_BATTERY_LEVEL)
                return False
        except Exception as e:
            logging.info(
                    'Unexpected error occurred when getting'
                    ' battery_charge_percent from servo; %s', str(e))
            return False
        return True

    def _set_servo_topology(self):
        """Set servo-topology info to the host-info."""
        logging.debug('Try to save servo topology to host-info.')
        if not self._servo_host:
            logging.debug('Servo host is not initialized.')
            return
        if not self.is_servo_in_working_state():
            logging.debug('Is servo is not in working state then'
                          ' update topology is not allowed.')
            return
        if not self._servo_host.is_servo_topology_supported():
            logging.debug('Servo-topology is not supported.')
            return
        servo_topology = self._servo_host.get_topology()
        if not servo_topology or servo_topology.is_empty():
            logging.debug('Servo topology is empty')
            return
        servo_topology.save(self.host_info_store)
