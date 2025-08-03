# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Expects to be run in an environment with sudo and no interactive password
# prompt, such as within the Chromium OS development chroot.


"""This file provides core logic for servo verify/repair process."""


from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import os
import re
import tarfile
import threading
import json
import tempfile
import time
import six
import six.moves.xmlrpc_client

try:
    import docker
    from autotest_lib.site_utils.docker import utils as docker_utils
except ImportError:
    logging.info("Docker API is not installed in this environment")

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.server import crashcollect
from autotest_lib.server.cros.servo import servo
from autotest_lib.server.hosts import servo_repair
from autotest_lib.server.hosts import base_servohost
from autotest_lib.server.hosts import servo_constants
from autotest_lib.server.cros.faft.utils import config
from autotest_lib.client.common_lib import global_config
from autotest_lib.server.cros.servo.topology import servo_topology

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock

_CONFIG = global_config.global_config

SERVOD_CONTAINER_IMAGE_PATH = "us-docker.pkg.dev/chromeos-partner-moblab/common-core"

# Create the flag on drone or inside the ssp container to enable servod debug mode.
# The servod container will not be deleted after being stopped.
SERVOD_DEBUG_FLAG = '/servod_debug'

DOCKER_SERVOD_DEBUG_MODE = os.environ.get('DOCKER_SERVOD_DEBUG_MODE', '0')


class ServoHost(base_servohost.BaseServoHost):
    """Host class for a servo host(e.g. beaglebone, labstation)
     that with a servo instance for a specific port.

     @type _servo: servo.Servo | None
     """

    DEFAULT_PORT = int(os.getenv('SERVOD_PORT', '9999'))

    # Timeout for initializing servo signals.
    INITIALIZE_SERVO_TIMEOUT_SECS = 60

    # Default timeout for run terminal command.
    DEFAULT_TERMINAL_TIMEOUT = 30

    # Ready test function
    SERVO_READY_METHOD = 'get_version'

    # Directory prefix on the servo host where the servod logs are stored.
    SERVOD_LOG_PREFIX = '/var/log/servod'

    # Exit code to use when symlinks for servod logs are not found.
    NO_SYMLINKS_CODE = 9

    # Directory in the job's results directory to dump the logs into.
    LOG_DIR = 'servod'

    # Prefix for joint loglevel files in the logs.
    JOINT_LOG_PREFIX = 'log'

    # Regex group to extract timestamp from logfile name.
    TS_GROUP = 'ts'

    # This regex is used to extract the timestamp from servod logs.
    # files always start with log.
    TS_RE = (r'log.'
             # The timestamp is of format %Y-%m-%d--%H-%M-%S.MS
             r'(?P<%s>\d{4}(\-\d{2}){2}\-(-\d{2}){3}.\d{3})'
             # The loglevel is optional depending on labstation version.
             r'(.(INFO|DEBUG|WARNING))?' % TS_GROUP)
    TS_EXTRACTOR = re.compile(TS_RE)

    # Regex group to extract MCU name from logline in servod logs.
    MCU_GROUP = 'mcu'

    # Regex group to extract logline from MCU logline in servod logs.
    LINE_GROUP = 'line'

    # This regex is used to extract the mcu and the line content from an
    # MCU logline in servod logs. e.g. EC or servo_v4 console logs.
    # Here is an example log-line:
    #
    # 2020-01-23 13:15:12,223 - servo_v4 - EC3PO.Console - DEBUG -
    # console.py:219:LogConsoleOutput - /dev/pts/9 - cc polarity: cc1
    #
    # Here is conceptually how they are formatted:
    #
    #  <time> - <MCU> - EC3PO.Console - <LVL> - <file:line:func> - <pts> -
    #  <output>
    #
    # The log format starts with a timestamp
    MCU_RE = (r'[\d\-]+ [\d:,]+ '
              # The mcu that is logging this is next.
              r'- (?P<%s>[\w/]+) - '
              # Next, we have more log outputs before the actual line.
              # Information about the file line, logging function etc.
              # Anchor on EC3PO Console, LogConsoleOutput and dev/pts.
              # NOTE: if the log format changes, this regex needs to be
              # adjusted.
              r'EC3PO\.Console[\s\-\w\d:.]+LogConsoleOutput - /dev/pts/\d+ - '
              # Lastly, we get the MCU's console line.
              r'(?P<%s>.+$)' % (MCU_GROUP, LINE_GROUP))
    MCU_EXTRACTOR = re.compile(MCU_RE)

    # Regex to detect timeout messages when USBC pigtail has timeout issue.
    # e.g.:  [475635.427072 PD TMOUT RX 1/1]
    USBC_PIGTAIL_TIMEOUT_RE = r'\[[\d \.]{1,20}(PD TMOUT RX 1\/1)\]'

    # Suffix to identify compressed logfiles.
    COMPRESSION_SUFFIX = '.tbz2'

    # A suffix to mark servod log directories that came from instance that
    # ran during this servo_host, but are not the last one running e.g. when
    # an instance (on purpose, or due to a bug) restarted in the middle of the
    # run.
    OLD_LOG_SUFFIX = 'old'

    def _init_attributes(self):
        self._servo_state = None
        self.servo_port = None
        self.servo_board = None
        self._ec_supported = None
        self.servo_model = None
        self.servo_serial = None
        self.servo_setup = None
        self.servo_recovery = None
        self.servo_fw_channel = None
        self.additional_servod_args = None
        self._dut_health_profile = None
        # The flag that indicate if a servo is connected to a smart usbhub.
        # TODO(xianuowang@) remove this flag once all usbhubs in the lab
        # get replaced.
        self.smart_usbhub = None
        self._servo = None
        self._topology = None
        self._tunnel_proxy = None
        self._tunnel_proxy_lock = threading.Lock()
        self._initial_instance_ts = None
        # Flag to make sure that multiple calls to close do not result in the
        # logic executing multiple times.
        self._closed = False
        # Per-thread local data
        self._local = threading.local()

    def _initialize(self,
                    servo_host='localhost',
                    servo_port=DEFAULT_PORT,
                    servo_board=None,
                    servo_model=None,
                    servo_serial=None,
                    servo_setup=None,
                    servo_recovery=None,
                    servo_fw_channel=None,
                    additional_servod_args=None,
                    is_in_lab=None,
                    *args,
                    **dargs):
        """Initialize a ServoHost instance.

        A ServoHost instance represents a host that controls a servo.

        @param servo_host: Name of the host where the servod process
                           is running.
        @param servo_port: Port the servod process is listening on. Defaults
                           to the SERVOD_PORT environment variable if set,
                           otherwise 9999.
        @param servo_board: Board that the servo is connected to.
        @param servo_model: Model that the servo is connected to.
        @param servo_serial: Serial number of the servo device.
        @param servo_setup: Type of servo setup, e.g. REGULAR or DUAL_V4.
        @param additional_servod_args: Additional args that will append to
                                       servod start command.
        @param is_in_lab: True if the servo host is in Cros Lab. Default is set
                          to None, for which utils.host_is_in_lab_zone will be
                          called to check if the servo host is in Cros lab.

        """
        super(ServoHost, self)._initialize(hostname=servo_host,
                                           is_in_lab=is_in_lab, *args, **dargs)
        self._init_attributes()
        self.servo_port = int(servo_port)
        self.servo_board = servo_board
        self.servo_model = servo_model
        self.servo_serial = servo_serial
        self.servo_setup = servo_setup
        self.servo_recovery = servo_recovery
        self.servo_fw_channel = servo_fw_channel
        self.additional_servod_args = additional_servod_args

        # The location of the log files on the servo host for this instance.
        self.remote_log_dir = '%s_%s' % (self.SERVOD_LOG_PREFIX,
                                         self.servo_port)
        # Path of the servo host lock file.
        self._lock_file = (self.TEMP_FILE_DIR + str(self.servo_port) +
                           self.LOCK_FILE_POSTFIX)
        # File path to declare a reboot request.
        self._reboot_file = (self.TEMP_FILE_DIR + str(self.servo_port) +
                             self.REBOOT_FILE_POSTFIX)

        # Lock the servo host if it's an in-lab labstation to prevent other
        # task to reboot it until current task completes. We also wait and
        # make sure the labstation is up here, in the case of the labstation is
        # in the middle of reboot.
        self._is_locked = False
        if (not self.is_containerized_servod()
                    and self.wait_up(self.REBOOT_TIMEOUT) and self.is_in_lab()
                    and self.is_labstation()):
            self._lock()
            try:
                self.wait_ready()
            except Exception as e:
                logging.info(
                        'Unexpected error while ensure labstation'
                        ' readiness; %s', str(e))

        self._repair_strategy = (
                servo_repair.create_servo_repair_strategy())

    def __str__(self):
        return "<%s '%s:%s'>" % (
                type(self).__name__, self.hostname, self.servo_port)

    def connect_servo(self):
        """ Initialize and setup servo for later use.
        """
        self.initialize_servo()
        self.initialize_dut_for_servo()

    def initialize_servo(self):
        """Establish a connection to the servod server on this host.

        Initializes `self._servo` and then verifies that all network
        connections are working.  This will create an ssh tunnel if
        it's required.
        """
        self._servo = servo.Servo(servo_host=self,
                                  servo_serial=self.servo_serial)

    def initialize_dut_for_servo(self):
        """This method will do some setup for dut control, e.g. setup
        main servo_v4 device, and also testing the connection between servo
        and DUT. As a side effect of testing the connection, all signals on
        the target servo are reset to default values, and the USB stick is
        set to the neutral (off) position.
        """
        if not self._servo:
            raise hosts.AutoservVerifyError('Servo object needs to be'
                                            ' initialized before initialize'
                                            ' DUT.')
        timeout, _ = retry.timeout(
            self._servo.initialize_dut,
            timeout_sec=self.INITIALIZE_SERVO_TIMEOUT_SECS)
        if timeout:
            raise hosts.AutoservVerifyError('Initialize dut for servo timed'
                                            ' out.')

    def disconnect_servo(self):
        """Disconnect our servo if it exists.

        If we've previously successfully connected to our servo,
        disconnect any established ssh tunnel, and set `self._servo`
        back to `None`.
        """
        if self._servo:
            # N.B. This call is safe even without a tunnel:
            # rpc_server_tracker.disconnect() silently ignores
            # unknown ports.
            self.rpc_server_tracker.disconnect(self.servo_port)
            self._servo = None

    def _to_str(self, maybe_str):
        """If the param is bytes, convert it to a str."""
        # b/217780680, Make this compatible with python3,
        if isinstance(maybe_str, bytes):
            return maybe_str.decode('utf-8', errors='replace')
        return maybe_str

    def _maybe_create_servod_ssh_tunnel_proxy(self):
        """Create a xmlrpc proxy for use with a ssh tunnel.
        A lock is used to safely create a singleton proxy.
        """
        with self._tunnel_proxy_lock:
            if self._tunnel_proxy is None:
                self._tunnel_proxy = self.rpc_server_tracker.xmlrpc_connect(
                       None,
                       self.servo_port,
                       ready_test_name=self.SERVO_READY_METHOD,
                       timeout_seconds=60,
                       request_timeout_seconds=3600,
                       server_desc=str(self))

    def get_servod_server_proxy(self):
        """Return a proxy if it exists; otherwise, create a new one.
        A proxy can either be a ssh tunnel based proxy, or a httplib
        based proxy.

        @returns: An xmlrpclib.ServerProxy that is connected to the servod
                  server on the host.
        """
        if (servo_constants.ENABLE_SSH_TUNNEL_FOR_SERVO
                    and not self.is_localhost()
                    and not self.is_containerized_servod()):
            # Check for existing ssh tunnel proxy.
            if self._tunnel_proxy is None:
                self._maybe_create_servod_ssh_tunnel_proxy()
            return self._tunnel_proxy
        else:
            # xmlrpc/httplib is not thread-safe, so each thread must have its
            # own separate proxy connection.
            if not hasattr(self._local, "_per_thread_proxy"):
                if self.is_containerized_servod():
                    ip_addr = docker_utils.get_container_ip(self.hostname)
                    remote = 'http://%s:%s' % (ip_addr, self.servo_port)
                else:
                    remote = 'http://%s:%s' % (self.hostname, self.servo_port)
                logging.debug('Servo Proxy RPC URL: %s', remote)
                self._local._per_thread_proxy = six.moves.xmlrpc_client.ServerProxy(remote)
            return self._local._per_thread_proxy

    def verify(self, silent=False):
        """Update the servo host and verify it's in a good state.

        @param silent   If true, suppress logging in `status.log`.
        """
        message = 'Beginning verify for servo host %s port %s serial %s'
        message %= (self.hostname, self.servo_port, self.servo_serial)
        self.record('INFO', None, None, message)
        try:
            self._repair_strategy.verify(self, silent)
            self._servo_state = servo_constants.SERVO_STATE_WORKING
            self.record('INFO', None, None,
                        'ServoHost verify set servo_state as WORKING')
        except Exception as e:
            if not self.is_localhost():
                self._servo_state = self.determine_servo_state()
                self.record('INFO', None, None,
                            'ServoHost verify set servo_state as %s'
                            % self._servo_state)
            if self._is_critical_error(e):
                raise

    def _get_default_usbkey_mount_path(self):
        return '/media/servo_usb/%s' % self.servo_port

    def get_image_name_from_usbkey(self, usbkey_dev):
        """Mount usb drive and check ChromeOS image name on it if there is
        one. This method assumes the image_usbkey_direction is already set
        to servo side.

        @param usbkey_dev: usbkey dev path(e.g. /dev/sdb).

        @returns: image_name on the usbkey, e.g. nami-release/R82.10138.0.0,
                  or empty string if no test image detected, or unexpected
                  error occurred.
        """
        logging.info('Checking ChromeOS image name on usbkey.')
        mount_dst = self._get_default_usbkey_mount_path()
        # Unmount if there is an existing stale mount.
        self._unmount_drive(mount_dst)
        # ChromeOS root fs is in /dev/sdx3
        mount_src = usbkey_dev + '3'
        try:
            if not self._mount_drive(mount_src, mount_dst):
                logging.debug('Unexpected error occurred on mount usb drive.')
                return ''

            release_content = self._to_str(
                    self.run('cat %s/etc/lsb-release' % mount_dst,
                             ignore_status=True).stdout.strip())

            if not re.search(r'RELEASE_TRACK=.*test', release_content):
                logging.info('The image on usbkey is not a test image')
                return ''

            return lsbrelease_utils.get_chromeos_release_builder_path(
                lsb_release_content=release_content)
        finally:
            logging.debug('Image check compeleted, unmounting the usb drive.')
            self._unmount_drive(mount_dst)

    def _extract_firmware_image_from_usbkey(self, fw_dst):
        """Extract firmware images from the usbkey on servo, this method
        assumes there is already a ChromeOS test image staged on servo.

        @param fw_dst: the path that we'll copy firmware images to.

        @returns: a json format string of firmware manifest data.
        """
        usbkey_dev = self._probe_and_validate_usb_dev()
        if not usbkey_dev:
            raise hosts.AutoservRepairError('Unexpected error occurred when'
                      ' probe usbkey dev path, please check logs for detail.')

        mount_dst = self._get_default_usbkey_mount_path()
        # Unmount if there is an existing stale mount.
        self._unmount_drive(mount_dst)
        # ChromeOS root fs is in /dev/sdx3
        mount_src = usbkey_dev + '3'
        try:
            if not self._mount_drive(mount_src, mount_dst):
                raise hosts.AutoservRepairError('Failed to extract firmware'
                          ' image; Unable to mount %s.' % usbkey_dev,
                          'unable to mount usbkey')
            updater_bin = os.path.join(mount_dst,
                                       'usr/sbin/chromeos-firmwareupdate')
            self.run('%s --unpack %s' % (updater_bin, fw_dst))
            return self._to_str(self.run('%s --manifest' % updater_bin).stdout)

        finally:
            self._unmount_drive(mount_dst)

    def prepare_repair_firmware_image(self, fw_dst=None):
        """Prepare firmware image on the servohost for auto repair process
        to consume.

        @param fw_dst: the path that we want to store firmware image on
                       the servohost.

        @returns: A tuple that containes ec firmware image path and bios
                  firmware image path on the servohost, or None if type of
                  image is not available based on manifest and dut's model.
        """
        model = self.servo_model or self._dut_host_info.model
        if not model:
            raise hosts.AutoservRepairError(
                      'Could not determine DUT\'s model.',
                      'model infomation unknown')

        if not fw_dst:
            fw_dst = '/tmp/firmware_image/%s' % self.servo_port
        # Cleanup and re-create dst path to have a fresh start.
        self.run('rm -rf %s' % fw_dst)
        self.run('mkdir -p %s' % fw_dst)

        manifest = json.loads(self._extract_firmware_image_from_usbkey(fw_dst))
        # For models that have packed $MODEL_signed variant, we want use the
        # 'signed' variant once we get DVT devices, so try to read manifest
        # from $MODEL_signed first.
        build = manifest.get('%s_signed' % model) or manifest.get(model)
        if not build:
            raise hosts.AutoservRepairError('Could not find firmware manifest'
                      ' for model:%s' % model, 'model manifest not found')
        try:
            ec_image = os.path.join(fw_dst, build['ec']['image'])
        except KeyError:
            ec_image = None
        try:
            bios_image = os.path.join(fw_dst, build['host']['image'])
        except KeyError:
            bios_image = None
        if not ec_image and not bios_image:
            raise hosts.AutoservRepairError('Could not find any firmware image'
                      ' for model:%s' % model, 'cannot find firmware image')
        return ec_image, bios_image

    def flash_ap_firmware_via_servo(self, image):
        """Flash AP firmware by use a provided image.

        This is will be a short term enhanment for infra repair use, it use
        'futility update' which will automatically determine various parameters
        needed for flashrom, and will preserve the GBB, VPD, and HWID for
        AP firmware update.
        @TODO(xianuowang@) Remove this method once b/148403277 implemented.

        @param image: the firmware image path on servohost.
        """
        cmd = 'futility update -i %s --servo_port=%s'
        self.run(cmd % (image, self.servo_port), timeout=900)

    def _probe_and_validate_usb_dev(self):
        """This method probe the usb dev path by talking to servo, and then
        validate the dev path is valid block device to servohost.
        Possible output:
        1. Encounter error during probe usb dev, returns empty string.
        2. probe usb dev completed without error but cannot find usb dev,
           raise AutoservRepairError.
        3. probe usb dev find a usb dev path, but failed validation in this
           method, raise AutoservRepairError.

        @returns: A string of usb dev path(e.g. '/dev/sdb'), or empty string
        if unexpected error occurred during probe.
        @raises: AutoservRepairError if servo couldn't probe the usb dev path
        (servo.probe_host_usb_dev() returns empty string), or the dev path is
        not valid block device to servohost.
        """
        logging.info('Validating image usbkey on servo.')
        try:
            usb_dev = self._servo.probe_host_usb_dev()
        except Exception as e:
            # We don't want any unexpected or transient servo communicating
            # failure block usb repair, so capture all errors here.
            logging.error(e, exc_info=True)
            logging.error('Unexpected error occurred on get usbkey dev path,'
                          ' skipping usbkey validation.')
            return ''

        if usb_dev:
            # probe_host_usb_dev() sometimes return stale record,
            # so we need to make sure the path exists in fdisk.
            validate_cmd = 'fdisk -l %s' % usb_dev
            try:
                resp = self.run(validate_cmd, ignore_status=True, timeout=30)
                if resp.exit_status == 0:
                    return usb_dev
                logging.error('%s is reported from "image_usbkey_dev" control'
                              ' but not detected by fdisk!', usb_dev)
            except error.AutoservRunError as e:
                if 'Timeout encountered' in str(e):
                    logging.warning('Timeout encountered during fdisk run,'
                                    ' skipping usbkey validation.')
                    return ''
                raise

        raise hosts.AutoservRepairError(
              'No usbkey detected on servo, the usbkey may be either missing'
              ' or broken. Please replace usbkey on the servo and retry.',
              'missing usbkey')

    def is_ec_supported(self):
        """Check if ec is supported on the servo_board"""
        if self.servo_board:
            if self._ec_supported is not None:
                return self._ec_supported
            try:
                frm_config = config.Config(self.servo_board, self.servo_model)
                self._ec_supported = getattr(frm_config, 'chrome_ec', False)
                return self._ec_supported
            except Exception as e:
                logging.error(
                        'Unexpected error when read from firmware'
                        ' configs; %s', e)
        else:
            logging.debug('Cannot detect if DUT has EC as board unknown.')
        return False

    def validate_image_usbkey(self):
        """This method first validate if there is a recover usbkey on servo
        that accessible to servohost, and second check if a ChromeOS image is
        already on the usb drive and return the image_name so we can avoid
        unnecessary download and flash to the recover usbkey on servo.

        Please note that, there is special error handling logic here:
        1. If unexpected error happens, we return empty string. So repair
           actions will not get blocked.
        2. If no working usbkey present on servo, but no errors, we'll raise
           AutoservRepairError here.

        @returns: image_name on the usbkey, e.g. nami-release/R82.10138.0.0,
                  or empty string if no test image detected, or unexpected
                  error occurred.
        @raises:  AutoservRepairError if the usbkey is not detected on servo.
        """
        usb_dev = self._probe_and_validate_usb_dev()
        if usb_dev:
            return self.get_image_name_from_usbkey(usb_dev)
        else:
            return ''

    def repair(self, silent=False):
        """Attempt to repair servo host.

        @param silent   If true, suppress logging in `status.log`.
        """
        message = 'Beginning repair for servo host %s port %s serial %s'
        message %= (self.hostname, self.servo_port, self.servo_serial)
        self.record('INFO', None, None, message)
        try:
            self._repair_strategy.repair(self, silent)
            self._servo_state = servo_constants.SERVO_STATE_WORKING
            self.record('INFO', None, None,
                        'ServoHost repair set servo_state as WORKING')
            # If target is a labstation then try to withdraw any existing
            # reboot request created by this servo because it passed repair.
            if self.is_labstation():
                self.withdraw_reboot_request()
        except Exception as e:
            if not self.is_localhost():
                self._servo_state = self.determine_servo_state()
                self.record('INFO', None, None,
                            'ServoHost repair set servo_state as %s'
                            % self._servo_state)
            if self._is_critical_error(e):
                self.disconnect_servo()
                self.stop_servod()
                raise

    def _is_critical_error(self, error):
        if (isinstance(error, hosts.AutoservVerifyDependencyError)
            and not error.is_critical()):
            logging.warning('Non-critical verify failure(s) detected during'
                            ' verify/repair servo, servo connection will'
                            ' still be up but may not be fully functional.'
                            ' Some repair actions and servo dependent'
                            ' tests may not run.')
            return False
        logging.info(
                'Critical verify failure(s) detected during repair/verify '
                'servo. Disconnecting servo and running `stop servod`, all'
                ' repair actions and tests that depends on servo will not '
                'run.')
        return True

    def get_servo(self):
        """Get the cached servo.Servo object.

        @return: a servo.Servo object.
        @rtype: autotest_lib.server.cros.servo.servo.Servo
        """
        return self._servo

    def request_reboot(self):
        """Request servohost to be rebooted when it's safe to by touch a file.
        """
        logging.debug('Request to reboot servohost %s has been created by '
                      'servo with port # %s', self.hostname, self.servo_port)
        self.run('touch %s' % self._reboot_file, ignore_status=True)

    def withdraw_reboot_request(self):
        """Withdraw a servohost reboot request if exists by remove the flag
        file.
        """
        logging.debug('Withdrawing request to reboot servohost %s that created'
                      ' by servo with port # %s if exists.',
                      self.hostname, self.servo_port)
        self.run('rm -f %s' % self._reboot_file, ignore_status=True)

    def start_servod(self, quick_startup=False):
        """Start the servod process on servohost.
        """
        # Skip if running on the localhost.(crbug.com/1038168)
        if self.is_localhost():
            logging.debug("Servohost is a localhost, skipping start servod.")
            return

        if self.is_containerized_servod():
            return self.start_containerized_servod()

        cmd = 'start servod'
        if self.servo_board:
            cmd += ' BOARD=%s' % self.servo_board
            if self.servo_model:
                cmd += ' MODEL=%s' % self.servo_model
        else:
            logging.warning('Board for DUT is unknown; starting servod'
                            ' assuming a pre-configured board.')

        cmd += ' PORT=%d' % self.servo_port
        if self.servo_serial:
            cmd += ' SERIAL=%s' % self.servo_serial

        # Start servod with dual_v4 based on servo_setup.
        if self.is_dual_setup():
            cmd += ' DUAL_V4=1'

        # Start servod with CONFIG=cr50.xml which required for some pools.
        if self._require_cr50_servod_config():
            cmd += ' CONFIG=cr50.xml'

        if self.servo_recovery == True:
            cmd += ' REC_MODE=1'

        # Adding customized args if any.
        if self.additional_servod_args:
            cmd += ' ' + self.additional_servod_args

        # Remove the symbolic links from the logs. This helps ensure that
        # a failed servod instantiation does not cause us to grab old logs
        # by mistake.
        self.remove_latest_log_symlinks()
        self.run(cmd, timeout=60)

        # There's a lag between when `start servod` completes and when
        # the _ServodConnectionVerifier trigger can actually succeed.
        # The call to time.sleep() below gives time to make sure that
        # the trigger won't fail after we return.

        # Normally servod on servo_v3 and labstation take ~10 seconds to ready,
        # But in the rare case all servo on a labstation are in heavy use they
        # may take ~30 seconds. So the timeout value will double these value,
        # and we'll try quick start up when first time initialize servohost,
        # and use standard start up timeout in repair.
        if quick_startup:
            timeout = servo_constants.SERVOD_QUICK_STARTUP_TIMEOUT
        else:
            timeout = servo_constants.SERVOD_STARTUP_TIMEOUT
        logging.debug('Wait %s seconds for servod process fully up.', timeout)
        time.sleep(timeout)
        # Cache the initial instance timestamp to check against servod restarts
        self._initial_instance_ts = self.get_instance_logs_ts()

    def stop_servod(self):
        """Stop the servod process on servohost.
        """
        # Skip if running on the localhost.(crbug.com/1038168)
        if self.is_localhost():
            logging.debug("Servohost is a localhost, skipping stop servod.")
            return

        if self.is_containerized_servod():
            # TODO(gregorynisbet): Remove this message in 2022Q2.
            logging.debug("ServoHost: Detected containerized servod.")
            remove_container = True
            if DOCKER_SERVOD_DEBUG_MODE == '1' or os.path.exists(
                    SERVOD_DEBUG_FLAG):
                remove_container = False
            self.stop_containerized_servod(remove_container=remove_container)
            return

        logging.debug('Stopping servod on port %s', self.servo_port)
        self.run('stop servod PORT=%d' % self.servo_port,
                 timeout=60, ignore_status=True)
        logging.debug('Wait %s seconds for servod process fully teardown.',
                      servo_constants.SERVOD_TEARDOWN_TIMEOUT)
        time.sleep(servo_constants.SERVOD_TEARDOWN_TIMEOUT)

    def wait_for_init_servod_in_container(self, container):
        """Waits for servod process to be ready to listen inside container."""
        ready_output = "Instance associated with id %s ready" % self.servo_port
        if not container:
            logging.debug("Container object is None.")
            return False
        try:
            # Executes servodtool command to wait for servod to be active.
            exit_code, output = container.exec_run(
                    cmd="servodtool instance wait-for-active -p %s" %
                    self.servo_port,
                    stdout=True)
            # b/217780680, Make this compatible with python3,
            if isinstance(output, bytes):
                output = output.decode(errors='replace')
            if exit_code != 0 or ready_output not in output:
                logging.debug(
                        'Failed to start servod process inside container,'
                        'exit_code=%s, output=%s.', exit_code, output)
                return False
        except docker.errors.APIError as e:
            logging.error('%s', e)
        return True

    def start_containerized_servod(self, with_servod=True):
        """Start the servod process on servohost."""
        logging.info("Starting servod container %s.",
                     self.servod_container_name)
        client = docker_utils.get_docker_client()
        logging.debug("Docker deamon ping %s", client.ping())
        labels = {'WITH_SERVOD': str(with_servod)}
        try:
            if self.is_up(with_servod=with_servod):
                logging.warning("Container already exists - not starting")
                return
            logging.info(
                    'Servod container either does not exist or is not running.'
            )
            self.stop_containerized_servod()
        except docker.errors.APIError:
            # Container exists but is not running
            logging.info("Cleanup of non functional container.")
            self.stop_containerized_servod()

        label = os.environ.get("SERVOD_CONTAINER_LABEL", "release")
        registry = os.environ.get("REGISTRY_URI", SERVOD_CONTAINER_IMAGE_PATH)
        image = "%s/servod:%s" % (registry, label)
        logging.info("Servod container image: %s", image)

        try:
            client.images.pull(image)
        except docker.errors.APIError:
            logging.exception("Failed to pull servod container image.")

        environment = [
                "BOARD=%s" % self.servo_board,
                "MODEL=%s" % self.servo_model,
                "SERIAL=%s" % self.servo_serial,
                "PORT=%s" % self.servo_port,
        ]
        # Start servod with dual_v4 based on servo_setup.
        if self.is_dual_setup():
            environment.append("DUAL_V4=1")
        # Start servod with CONFIG=cr50.xml which required for some pools.
        if self._require_cr50_servod_config():
            environment.append("CONFIG=cr50.xml")
        if self.servo_recovery == True:
            environment.append("REC_MODE=1")

        container_network = os.environ.get("DOCKER_DEFAULT_NETWORK", None)
        # In case the network environment is not set, fallback to default network
        # for moblab or satlab based on the TLE.
        if not container_network:
            container_network = "default_moblab"
            if 'drone' in docker_utils.get_running_containers(client=client):
                container_network = "default_satlab"

        logging.info('Servod container will use %s network', container_network)
        logging.info('Servod container environment: %s', environment)
        try:
            start_cmds = ["bash", "/start_servod.sh"]
            if not with_servod:
                # In some cases we do not need container without running servod.
                start_cmds = ["tail", "-f", "/dev/null"]
                logging.debug(
                        'Attempting to start Servod container without servod')
            container = client.containers.run(
                    image,
                    remove=False,
                    privileged=True,
                    labels=labels,
                    name=self.servod_container_name,
                    hostname=self.servod_container_name,
                    network=container_network,
                    cap_add=["NET_ADMIN"],
                    detach=True,
                    volumes=[
                            "/dev:/dev",
                            "%s_log:/var/log/servod_%s/" %
                            (self.servod_container_name, self.servo_port)
                    ],
                    environment=environment,
                    command=start_cmds,
            )
            # Probing servod ready state fails.
            if with_servod:
                current_time = 0
                while not self.wait_for_init_servod_in_container(
                        container
                ) and current_time <= servo_constants.SERVOD_STARTUP_TIMEOUT:
                    time.sleep(10)
                    current_time += 10

                if not self.wait_for_init_servod_in_container(container):
                    logging.info(
                            'Servod process is not up within the servod container after %s seconds.'
                            % servo_constants.SERVOD_STARTUP_TIMEOUT)
                else:
                    logging.info(
                            'Servod process is up within the servod container after %s seconds.'
                            % current_time)
            else:
                logging.info(
                        "Servod container %s up and running without servod process.",
                        self.servod_container_name)

        except docker.errors.ContainerError as e:
            logging.exception("Failed to start servod container. %s", e)
            raise
        except docker.errors.ImageNotFound:
            logging.exception("Servod container image %s not found.", image)

    def stop_containerized_servod(self, remove_container=True):
        """Stop the container running servod."""
        logging.info("Stopping servod container %s.",
                     self.servod_container_name)
        client = docker_utils.get_docker_client()
        try:
            cont = client.containers.get(self.servod_container_name)
        except docker.errors.NotFound:
            logging.info("Servod container %s not found no need to stop it.",
                         self.servod_container_name)
        except docker.errors.APIError:
            logging.exception(
                    "Stopping servod container %s caused a docker error.",
                    self.servod_container_name)
        else:
            if remove_container == True:
                cont.remove(force=True)
                logging.debug('Servod container instance removed')

    def restart_servod(self, quick_startup=False):
        """Restart the servod process on servohost.
        """
        self.stop_servod()
        self.start_servod(quick_startup)

    def _process_servodtool_error(self, response):
        """Helper function to handle non-zero servodtool response.
        """
        if re.search(servo_constants.ERROR_MESSAGE_USB_HUB_NOT_COMPATIBLE,
                     self._to_str(response.stdout)):
            logging.error('The servo is not plugged on a usb hub that supports'
                          ' power-cycle!')
            # change the flag so we can update this label in later process.
            self.smart_usbhub = False
            return

        if re.search(
                servo_constants.ERROR_MESSAGE_DEVICE_NOT_FOUND %
                self.servo_serial, self._to_str(response.stdout)):
            logging.error('No servo with serial %s found!', self.servo_serial)
            return

        logging.error('Unexpected error occurred from usbhub control, please'
                      ' file a bug and inform chrome-fleet-software@ team!')

    def get_main_servo_usb_path(self):
        """Helper function to collect current usb-path to main servo.

        The usb-path is path to the folder where usb-device was enumerated.
        If fail then will return an empty string ('').

        @returns: string, usb-path to the main servo device.
            e.g.: '/sys/bus/usb/devices/1-6.1.3.1'
        """
        # TODO remove try-except when fix crbug.com/1087964
        try:
            cmd = 'servodtool device -s %s usb-path' % self.servo_serial
            resp = self.run(cmd, ignore_status=True, timeout=30)
        except Exception as e:
            # Here we catch only timeout errors.
            # Other errors is filtered by ignore_status=True
            logging.debug('Attempt to get servo usb-path failed due to '
                          'timeout; %s', e)
            return ''
        if resp.exit_status != 0:
            self._process_servodtool_error(resp)
            return ''
        usb_path = self._to_str(resp.stdout.strip())

        logging.info('Usb path of servo %s is %s', self.servo_serial, usb_path)
        return usb_path

    def _get_servo_usb_devnum(self):
        """Helper function to collect current usb devnum of servo."""
        usb_path = self.get_main_servo_usb_path()
        if not usb_path:
            return ''
        resp = self.run('cat %s/devnum' % usb_path, ignore_status=True)
        if resp.exit_status != 0:
            self._process_servodtool_error(resp)
            return ''
        return self._to_str(resp.stdout.strip())

    def reboot_servo_v3_on_need(self):
        """Check and reboot servo_v3 based on below conditions.
               1. If there is an update pending on reboot.
               2. Servo_v3 has been up for more than 96 hours.
        """
        if self.get_board() != 'beaglebone_servo':
            logging.info('Servo reboot is only applicable for servo V3.')
            return

        update_pending_reboot = (self._check_update_status() ==
                                 self.UPDATE_STATE.PENDING_REBOOT)
        uptime_hours = float(self.check_uptime())/3600
        logging.info('Uptime of servo_v3: %s hour(s)', uptime_hours)
        long_up_time = uptime_hours > 96

        # Skip reboot if neither condition are met.
        if not (update_pending_reboot or long_up_time):
            return

        if update_pending_reboot:
            message = 'Starting reboot servo_v3 because an update is pending.'
            reboot_method = self._post_update_reboot
        elif long_up_time:
            message = 'Starting reboot servo_v3 because uptime > 96 hours.'
            reboot_method = self._servo_host_reboot
        self.record('INFO', None, None, message)
        logging.info(message)
        try:
            reboot_method()
            message = 'Servo_v3 reboot completed successfully.'
        except Exception as e:
            logging.debug("Fail to reboot servo_v3; %s", e)
            message = ('Servo_v3 reboot failed, please check debug log '
                       'for details.')
        logging.info(message)
        self.record('INFO', None, None, message)

    def _reset_servo(self):
        logging.info('Resetting servo through smart usbhub.')
        # TODO remove try-except when fix crbug.com/1087964
        try:
            resp = self.run('servodtool device -s %s power-cycle' %
                            self.servo_serial, ignore_status=True,
                            timeout=30)
            if resp.exit_status != 0:
                self._process_servodtool_error(resp)
                return False
        except Exception as e:
            # Here we catch only timeout errors.
            # Other errors is filtered by ignore_status=True
            logging.debug('Attempt to reset servo failed due to timeout;'
                          ' %s', e)
            return False

        logging.debug('Wait %s seconds for servo to come back from reset.',
                      servo_constants.SERVO_RESET_TIMEOUT_SECONDS)
        time.sleep(servo_constants.SERVO_RESET_TIMEOUT_SECONDS)
        # change the flag so we can update this label in later process.
        self.smart_usbhub = True
        return True

    def reset_servo(self):
        """Reset(power-cycle) the servo via smart usbhub.
        """
        if not self.is_labstation():
            logging.info('Servo reset is not applicable to servo_v3.')
            return

        pre_reset_devnum = self._get_servo_usb_devnum()
        logging.info('Servo usb devnum before reset: %s', pre_reset_devnum)
        result = self._reset_servo()
        if not result:
            message = ('Failed to reset servo with serial: %s. (Please ignore'
                       ' this error if the DUT is not connected to a smart'
                       ' usbhub).' % self.servo_serial)
            logging.warning(message)
            self.record('INFO', None, None, message)
            return

        post_reset_devnum = self._get_servo_usb_devnum()
        logging.info('Servo usb devnum after reset: %s', post_reset_devnum)
        if not (pre_reset_devnum and post_reset_devnum):
            message = ('Servo reset completed but unable to verify'
                       ' devnum change!')
        elif pre_reset_devnum != post_reset_devnum:
            message = ('Reset servo with serial %s completed successfully!'
                       % self.servo_serial)
        else:
            message = 'Servo reset completed but devnum is still not changed!'
        logging.info(message)
        self.record('INFO', None, None, message)

    def _extract_compressed_logs(self, logdir, relevant_files):
        """Decompress servod logs in |logdir|.

        @param logdir: directory containing compressed servod logs.
        @param relevant_files: list of files in |logdir| to consider.

        @returns: tuple, (tarfiles, files) where
                  tarfiles: list of the compressed filenames that have been
                            extracted and deleted
                  files:  list of the uncompressed files that were generated
        """
        # For all tar-files, first extract them to the directory, and
        # then let the common flow handle them.
        tarfiles = [cf for cf in relevant_files if
                    cf.endswith(self.COMPRESSION_SUFFIX)]
        files = []
        for f in tarfiles:
            norm_name = os.path.basename(f)[:-len(self.COMPRESSION_SUFFIX)]
            with tarfile.open(f) as tf:
                # Each tarfile has only one member, as
                # that's the compressed log.
                member = tf.members[0]
                # Manipulate so that it only extracts the basename, and not
                # the directories etc.
                member.name = norm_name
                files.append(os.path.join(logdir, member.name))
                tf.extract(member, logdir)
            # File has been extracted: remove the compressed file.
            os.remove(f)
        return tarfiles, files

    def _extract_mcu_logs(self, log_subdir):
        """Extract MCU (EC, Cr50, etc) console output from servod debug logs.

        Using the MCU_EXTRACTOR regex (above) extract and split out MCU console
        lines from the logs to generate invidiual console logs e.g. after
        this method, you can find an ec.txt and servo_v4.txt in |log_dir| if
        those MCUs had any console input/output.

        @param log_subdir: directory with log.DEBUG.txt main servod debug logs.
        """
        # Extract the MCU for each one. The MCU logs are only in the .DEBUG
        # files
        mcu_lines_file = os.path.join(log_subdir, 'log.DEBUG.txt')
        if not os.path.exists(mcu_lines_file):
            logging.info('No DEBUG logs found to extract MCU logs from.')
            return
        mcu_files = {}
        mcu_file_template = '%s.txt'
        with open(mcu_lines_file, 'r') as f:
            for line in f:
                match = self.MCU_EXTRACTOR.match(line)
                if match:
                    mcu = match.group(self.MCU_GROUP).lower()
                    # The |mcu| might contain a '/' in it. Replace it with '.'
                    # to avoid generating bad filepaths.
                    mcu = mcu.replace('/', '.')
                    line = match.group(self.LINE_GROUP)
                    if mcu not in mcu_files:
                        mcu_file = os.path.join(log_subdir,
                                                mcu_file_template % mcu)
                        mcu_files[mcu] = open(mcu_file, 'a')
                    fd = mcu_files[mcu]
                    fd.write(line + '\n')
        for f in mcu_files:
            mcu_files[f].close()

    def remove_latest_log_symlinks(self):
        """Remove the conveninence symlinks 'latest' servod logs."""
        symlink_wildcard = '%s/latest*' % self.remote_log_dir
        cmd = 'rm ' + symlink_wildcard
        self.run(cmd, stderr_tee=None, ignore_status=True)

    def probe_servod_restart(self, instance_ts, outdir):
        """Grab servod logs from previous instances if part of this session.

        If since the last time this host called start_servod() servod crashed
        and restarted, this helper finds those logs as well, and stores them
        with the |OLD_LOG_SUFFIX| to investigate if necessary.

        It also issues a panicinfo command to servo devices after the restart
        to try and collect reboot information for debugging.

        @param instance_ts: the log timestamp that the current instance uses
        @param outdir: directory to create a subdirectory into to place the
                       servod logs into.
        """
        if self._initial_instance_ts is None:
            logging.info('No log timestamp grabbed successfully on servod '
                         'startup. Cannot check device restarts. Ignoring.')
            return
        if instance_ts == self._initial_instance_ts:
            logging.debug('Servod appears to have run without restarting')
            return
        # Servod seems to have restarted (at least once). |_initial_instance_ts|
        # is the first timestamp, and instance_ts is the current timestamp. Find
        # all timestamps in between them, and grab the logs for each.
        tss = self._find_instance_timestamps_between(self._initial_instance_ts,
                                                     instance_ts)
        logging.info('Servod has restarted %d times between the start and the '
                     'end of this servo_host.', len(tss))
        logging.info('This might be an issue. Will extract all logs from each '
                     'instance.')
        logging.info('Logs that are not the currently running (about to turn '
                     'down) instance are maked with a .%s in their folder.',
                     self.OLD_LOG_SUFFIX)
        for ts in tss:
            self.get_instance_logs(ts, outdir, old=True)
        # Lastly, servod has restarted due to a potential issue. Try to get
        # panic information from servo micro and servo v4 for the current logs.
        # This can only happen if the |_servo| attribute is initialized.
        if self._servo:
            for mcu in ['servo_micro', 'servo_v4', 'servo_v4p1']:
                ctrl = '%s_uart_cmd' % mcu
                if self._servo.has_control(ctrl):
                    logging.info('Trying to retrieve %r panicinfo into logs',
                                 mcu)
                    try:
                        self._servo.set_nocheck(ctrl, 'panicinfo')
                    except error.TestFail as e:
                        logging.error('Failed to generate panicinfo for %r '
                                      'logs. %s', mcu, str(e))

    def _find_instance_timestamps_between(self, start_ts, end_ts):
        """Find all log timestamps between [start_ts, end_ts).

        @param start_ts: str, earliest log timestamp of interest
        @param end_ts: str, latest log timestamp of interest

        @returns: list, all timestamps between start_ts and end_ts, end_ts
                  exclusive, on the servo_host. An empty list on errors
        """
        # Simply get all timestamp, and then sort and remove
        cmd = 'ls %s' % self.remote_log_dir
        res = self.run(cmd, stderr_tee=None, ignore_status=True)
        if res.exit_status != 0:
            # Here we failed to find anything.
            logging.info('Failed to find remote servod logs. Ignoring.')
            return []
        logfiles = self._to_str(res.stdout.strip().split())
        timestamps = set()
        for logfile in logfiles:
            ts_match = self.TS_EXTRACTOR.match(logfile)
            if not ts_match:
                # Simply ignore files that fail the check. It might be the
                # 'latest' symlinks or random files.
                continue
            timestamps.add(ts_match.group(self.TS_GROUP))
        # At this point we have all unique timestamps.
        timestamps = sorted(timestamps)
        for ts in [start_ts, end_ts]:
            if ts not in timestamps:
                logging.error('Timestamp %r not in servod logs. Cannot query '
                              'for timestamps in between %r and %r', ts,
                              start_ts, end_ts)
                return []
        return timestamps[timestamps.index(start_ts):timestamps.index(end_ts)]

    def get_instance_logs_ts(self):
        """Retrieve the currently running servod instance's log timestamp

        @returns: str, timestamp for current instance, or None on failure
        """
        # First, extract the timestamp. This cmd gives the real filename of
        # the latest aka current log file.
        cmd = ('if [ -f %(dir)s/latest.DEBUG ];'
               'then realpath %(dir)s/latest.DEBUG;'
               'elif [ -f %(dir)s/latest ];'
               'then realpath %(dir)s/latest;'
               'else exit %(code)d;'
               'fi' % {'dir': self.remote_log_dir,
                       'code': self.NO_SYMLINKS_CODE})
        res = self.run(cmd, stderr_tee=None, ignore_status=True)
        if res.exit_status != 0:
            if res.exit_status == self.NO_SYMLINKS_CODE:
                logging.warning('servod log latest symlinks not found. '
                                'This is likely due to an error starting up '
                                'servod. Ignoring..')
            else:
                logging.warning('Failed to find servod logs on servo host.')
                logging.warning(res.stderr.strip())
            return None
        fname = os.path.basename(self._to_str(res.stdout.strip()))
        # From the fname, ought to extract the timestamp using the TS_EXTRACTOR
        if type(fname) == type(b' '):
            fname = fname.decode("utf-8")
        ts_match = self.TS_EXTRACTOR.match(fname)
        if not ts_match:
            logging.warning('Failed to extract timestamp from servod log file '
                            '%r. Skipping. The servo host is using outdated '
                            'servod logging and needs to be updated.', fname)
            return None
        return ts_match.group(self.TS_GROUP)

    def get_servohost_logs(self, outdir):
        """Get logs that can help debugging servo/servod problem from
        the servohost

        @param outdir: directory to create a subdirectory into to place the
                       servod logs into.
        """
        log_dir = os.path.join(outdir, 'servohost_%s' % self.hostname)
        if os.path.isdir(log_dir):
            # In multi-DUTs testing, each DUTs will may their own servohost
            # instance, where could cause duplicate efforts if they share a
            # same servohost, so we can just skip the collect if the log
            # dir already exists.
            logging.info(
                    'Skip dmesg and messages logs collecting as %s'
                    ' already exists.', log_dir)
            return
        logging.info('Collecting dmesg and messages from servohost %s',
                     self.hostname)
        os.mkdir(log_dir)
        logging.info('Saving servohost logs to %s.', log_dir)
        # First collect dmesg from the servohost.
        crashcollect.collect_command(self, 'dmesg -H',
                                     os.path.join(log_dir, 'dmesg'))
        # Collect messages log from the servohost.
        if not self.is_containerized_servod():
            try:
                self.get_file('/var/log/messages', log_dir, try_rsync=False)
            except error.AutoservRunError:
                logging.warning(
                        'Failed to collect messages log from servohost.')

    def get_servod_startup_log(self, outdir):
        """Get servod start_up log, this log is available even servod was
        not started successfully.

        @param outdir: directory to create a subdirectory into to place the
                       servod logs into.
        """
        if self.is_containerized_servod():
            return
        log_dir = os.path.join(outdir, 'servod_startup_%s' % self.servo_port)
        os.mkdir(log_dir)
        start_up_log = '/var/log/servod_%s.STARTUP.log' % self.servo_port
        try:
            self.get_file(start_up_log, log_dir, try_rsync=False)
        except error.AutoservRunError:
            logging.warning('Failed to collect servod start up log'
                            ' from servohost.')

    def get_instance_logs(self, instance_ts, outdir, old=False):
        """Collect all logs with |instance_ts| and dump into a dir in |outdir|

        This method first collects all logs on the servo_host side pertaining
        to this servod instance (port, instatiation). It glues them together
        into combined log.[level].txt files and extracts all available MCU
        console I/O from the logs into individual files e.g. servo_v4.txt

        All the output can be found in a directory inside |outdir| that
        this generates based on |LOG_DIR|, the servod port, and the instance
        timestamp on the servo_host side.

        @param instance_ts: log timestamp to grab logfiles for
        @param outdir: directory to create a subdirectory into to place the
                       servod logs into.
        @param old: bool, whether to append |OLD_LOG_SUFFIX| to output dir
        """
        # Create the local results log dir.
        log_dir = os.path.join(outdir, '%s_%s.%s' % (self.LOG_DIR,
                                                     str(self.servo_port),
                                                     instance_ts))
        if old:
            log_dir = '%s.%s' % (log_dir, self.OLD_LOG_SUFFIX)
        logging.info('Saving servod logs to %r.', log_dir)
        os.mkdir(log_dir)
        # Now, get all files with that timestamp.
        cmd = 'find %s -maxdepth 1 -name "log.%s*"' % (self.remote_log_dir,
                                                       instance_ts)
        res = self.run(cmd, stderr_tee=None, ignore_status=True)
        files = self._to_str(res.stdout.strip()).split()
        try:
            if self.is_containerized_servod():
                client = docker_utils.get_docker_client()
                container = client.containers.get(self.servod_container_name)
                for f in files:
                    file_stream, stat = container.get_archive(f)
                    tf = tempfile.NamedTemporaryFile(delete=False)
                    for block in file_stream:
                        tf.write(block)
                    tf.close()
                    pw_tar = tarfile.TarFile(tf.name)
                    pw_tar.extractall(log_dir)
                    os.remove(tf.name)
            else:
                self.get_file(files, log_dir, try_rsync=False)

            if not os.listdir(log_dir):
                logging.info('No servod logs retrieved. Ignoring, and removing '
                             '%r again.', log_dir)
                os.rmdir(log_dir)
                return
        except docker.errors.NotFound:
            logging.info("Servod container %s not found no need to stop it.",
                         self.hostname)
        except error.AutoservRunError as e:
            result = e.result_obj
            if result.exit_status != 0:
                stderr = result.stderr.strip()
                logging.warning("Couldn't retrieve servod logs. Ignoring: %s",
                                stderr or '\n%s' % result)
            # Remove the log_dir as nothing was added to it.
            os.rmdir(log_dir)
            return
        local_files = [os.path.join(log_dir, f) for f in os.listdir(log_dir)]
        # TODO(crrev.com/c/1793030): remove no-level case once CL is pushed
        for level_name in ('DEBUG', 'INFO', 'WARNING', ''):
            # Create the joint files for each loglevel. i.e log.DEBUG
            joint_file = self.JOINT_LOG_PREFIX
            if level_name:
                joint_file = '%s.%s' % (self.JOINT_LOG_PREFIX, level_name)
            # This helps with some online tools to avoid complaints about an
            # unknown filetype.
            joint_file = joint_file + '.txt'
            joint_path = os.path.join(log_dir, joint_file)
            files = [f for f in local_files if level_name in f]
            if not files:
                # TODO(crrev.com/c/1793030): remove no-level case once CL
                # is pushed
                continue
            # Extract compressed logs if any.
            compressed, extracted = self._extract_compressed_logs(log_dir,
                                                                  files)
            files = list(set(files) - set(compressed))
            files.extend(extracted)
            # Need to sort. As they all share the same timestamp, and
            # loglevel, the index itself is sufficient. The highest index
            # is the oldest file, therefore we need a descending sort.
            def sortkey(f, level=level_name):
                """Custom sortkey to sort based on rotation number int."""
                if f.endswith(level_name): return 0
                return int(f.split('.')[-1])

            files.sort(reverse=True, key=sortkey)
            # Just rename the first file rather than building from scratch.
            os.rename(files[0], joint_path)
            with open(joint_path, 'a') as joint_f:
                for logfile in files[1:]:
                    # Transfer the file to the joint file line by line.
                    with open(logfile, 'r') as log_f:
                        for line in log_f:
                            joint_f.write(line)
                    # File has been written over. Delete safely.
                    os.remove(logfile)
            # Need to remove all files form |local_files| so we don't
            # analyze them again.
            local_files = list(set(local_files) - set(files) - set(compressed))
        # Lastly, extract MCU logs from the joint logs.
        self._extract_mcu_logs(log_dir)

    def _lock(self):
        """lock servohost by touching a file.
        """
        logging.debug('Locking servohost %s by touching %s file',
                      self.hostname, self._lock_file)
        self.run('touch %s' % self._lock_file, ignore_status=True)
        self._is_locked = True

    def _unlock(self):
        """Unlock servohost by removing the lock file.
        """
        logging.debug('Unlocking servohost by removing %s file',
                      self._lock_file)
        self.run('rm %s' % self._lock_file, ignore_status=True)
        self._is_locked = False

    def close(self):
        """Close the associated servo and the host object."""
        # NOTE: throughout this method there are multiple attempts to catch
        # all errors. This is WAI as log grabbing should not fail tests.
        # However, the goal is to catch and handle/process all errors, thus
        # we print the traceback and ask for a bug.
        if self._closed:
            logging.debug('ServoHost is already closed.')
            return

        # Only attempt ssh related actions if servohost is sshable. We call
        # check_cached_up_status() first because it's lightweighted and return
        # much faster in the case servohost is down, however, we still want
        # to call is_up() later since check_cached_up_status() is ping based check
        # and not guarantee the servohost is sshable.
        servo_host_ready = self.check_cached_up_status() and self.is_up()

        # If the dockerized servod is in used, we can start a new servod container
        # with out the servod process for log collection.
        if (not self.is_localhost() and not servo_host_ready
                    and self.is_containerized_servod()):
            logging.info(
                    'Start servod container without servod for log collection.'
            )
            self.start_containerized_servod(with_servod=False)
            servo_host_ready = True

        # TODO(crbug.com/1011516): once enabled, remove the check against
        # localhost and instead check against log-rotiation enablement.
        should_collect_log = (servo_host_ready and self.job
                              and not self.is_localhost())
        if should_collect_log:
            instance_ts = self.get_instance_logs_ts()
        else:
            logging.info('Servohost is down, will skip servod log collecting.')
            instance_ts = None
        servod_logs_available = instance_ts is not None
        if servod_logs_available:
            # Probe whether there was a servod restart, and grab those old
            # logs as well.
            try:
                self.probe_servod_restart(instance_ts, self.job.resultdir)
            except (error.AutoservRunError, error.TestFail) as e:
                logging.info('Failed to grab servo logs due to: %s. '
                             'This error is forgiven.', str(e))
            except Exception as e:
                logging.error('Unexpected error probing for old logs. %s. '
                              'Forgiven. Please file a bug and fix or catch '
                              'in log probing function', str(e),
                              exc_info=True)
        if self._servo:
            outdir = None if not self.job else self.job.resultdir
            # In some cases when we run as lab-tools, the job object is None.
            self._servo.close(outdir)
        try:
            if should_collect_log:
                self.get_servod_startup_log(self.job.resultdir)
                self.get_servohost_logs(self.job.resultdir)
            # Grab current (not old like above) logs after the servo instance
            # was closed out.
            if servod_logs_available:
                self.get_instance_logs(instance_ts, self.job.resultdir)
        except error.AutoservRunError as e:
            logging.info(
                    'Failed to grab servo logs due to: %s. '
                    'This error is forgiven.', str(e))
        except Exception as e:
            logging.error(
                    'Unexpected error grabbing servod logs. %s. '
                    'Forgiven. Please file a bug and fix or catch '
                    'in log grabbing function',
                    str(e),
                    exc_info=True)

        if self._is_locked and servo_host_ready:
            # Remove the lock if the servohost has been locked.
            try:
                self._unlock()
            except error.AutoservSSHTimeout:
                logging.error('Unlock servohost failed due to ssh timeout.'
                              ' It may caused by servohost went down during'
                              ' the task.')
        # We want always stop servod after task to minimum the impact of bad
        # servod process interfere other servods.(see crbug.com/1028665)
        if servo_host_ready:
            try:
                self.stop_servod()
            except error.AutoservRunError as e:
                logging.info(
                        "Failed to stop servod due to:\n%s\n"
                        "This error is forgiven.", str(e))

        super(ServoHost, self).close()
        # Mark closed.
        self._closed = True

    def get_servo_state(self):
        return self._servo_state

    def _get_host_metrics_data(self):
        return {'port': self.servo_port,
                'host': self.get_dut_hostname() or self.hostname,
                'board': self.servo_board or ''}

    def is_servo_board_present_on_servo_v3(self):
        """Check if servo board is detected on servo_v3"""
        logging.debug('Started to detect servo board on servo_v3')
        vid_pids = ['18d1:5004', '0403:6014']
        not_detected = 'The servo board is not detected on servo_v3'
        try:
            cmd = 'lsusb | grep "%s"' % "\|".join(vid_pids)
            result = self.run(cmd, ignore_status=True, timeout=30)
            if result.exit_status == 0 and self._to_str(result.stdout.strip()):
                logging.debug('The servo board is detected on servo_v3')
                return True
            logging.debug('%s; %s', not_detected, result)
            return False
        except Exception as e:
            # can be triggered by timeout issue due running the script
            metrics.Counter(
                'chromeos/autotest/repair/servo_detection/timeout'
                ).increment(fields=self._get_host_metrics_data())
            logging.error('%s; %s', not_detected, str(e))
        return None

    def _require_cr50_servod_config(self):
        """Check whether we need start servod with CONFIG=cr50.xml"""
        dut_host_info = self.get_dut_host_info()
        if not dut_host_info:
            return False
        for pool in dut_host_info.pools:
            if pool.startswith(servo_constants.CR50_CONFIG_POOL_PREFIX):
                return True
        return False

    def get_verifier_state(self, tag):
        """Return the state of servo verifier.

        @returns: bool or None
        """
        return self._repair_strategy.verifier_is_good(tag)

    def get_repair_strategy_node(self, tag):
        """Return the instance of verifier/repair node for host by tag.

        @returns: _DependencyNode or None
        """
        return self._repair_strategy.node_by_tag(tag)

    def determine_servo_state(self):
        """Determine servo state based on the failed verifier.

        @returns: servo state value
        The state detecting based on first fail verifier or collecting of
        them.
        """
        ssh = self.get_verifier_state('connection')
        servo_root_present = self.get_verifier_state('servo_root_present')
        servo_root_present_node = self.get_repair_strategy_node(
                'servo_root_present')
        servo_v3_present = self.get_verifier_state('servo_v3_root_present')
        servo_fw = self.get_verifier_state('servo_fw')
        servo_fw_update = self.get_repair_strategy_node('servo_fw_update')
        servod_dut_controller_missing = self.get_repair_strategy_node(
                'servod_dut_controller_missing')
        disk_space = self.get_verifier_state('servo_disk_space')
        start_servod = self.get_verifier_state('start_servod')
        servod_started = self.get_verifier_state('servod_started')
        servod_echo = self.get_verifier_state('servod_echo')
        create_servo = self.get_verifier_state('servod_connection')
        init_servo = self.get_verifier_state('servod_control')
        cr50_low_sbu = self.get_verifier_state('servo_cr50_low_sbu')
        cr50_off = self.get_verifier_state('servo_cr50_off')
        servo_topology = self.get_verifier_state('servo_topology')
        dut_connected = self.get_verifier_state('servo_dut_connected')
        hub_connected = self.get_verifier_state('servo_hub_connected')
        pwr_button = self.get_verifier_state('servo_pwr_button')
        lid_open = self.get_verifier_state('servo_lid_open')
        ec_board = self.get_verifier_state('servo_ec_console')
        cr50_console = self.get_verifier_state('servo_cr50_console')
        ccd_testlab = self.get_verifier_state('servo_ccd_testlab')

        if not ssh:
            return servo_constants.SERVO_STATE_NO_SSH
        if start_servod == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_SERVO_HOST_ISSUE
        if servo_root_present == hosts.VERIFY_FAILED:
            if not self.servo_serial:
                return servo_constants.SERVO_STATE_WRONG_CONFIG
            if hasattr(servo_root_present_node, 'serial_mismatch'):
                return servo_constants.SERVO_STATE_SERIAL_MISMATCH
            return servo_constants.SERVO_STATE_NOT_CONNECTED
        if servo_v3_present == hosts.VERIFY_FAILED:
            # if we cannot find required board on servo_v3
            return servo_constants.SERVO_STATE_NEED_REPLACEMENT
        if servo_fw == hosts.VERIFY_FAILED:
            logging.info(servo_fw_update)
            if hasattr(servo_fw_update, 'servo_updater_issue_detected'):
                return servo_constants.SERVO_STATE_SERVO_UPDATER_ISSUE
            return servo_constants.SERVO_STATE_NEED_REPLACEMENT

        if dut_connected == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_DUT_NOT_CONNECTED
        if hub_connected == hosts.VERIFY_FAILED:
            logging.info('Servo HUB not connected')
            return servo_constants.SERVO_STATE_DUT_NOT_CONNECTED

        if cr50_low_sbu == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_SBU_LOW_VOLTAGE
        if cr50_off == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_CR50_NOT_ENUMERATED

        if servod_dut_controller_missing == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_SERVOD_DUT_CONTROLLER_MISSING
        if servo_topology == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_TOPOLOGY_ISSUE

        # TODO(otabek@): detect special cases detected by pwr_button
        if dut_connected == hosts.VERIFY_SUCCESS:
            if pwr_button == hosts.VERIFY_FAILED:
                metrics.Counter(
                        'chromeos/autotest/repair/servo_unexpected/pwr_button2'
                ).increment(fields=self._get_host_metrics_data())

        if (servod_started == hosts.VERIFY_FAILED
                    or servod_echo == hosts.VERIFY_FAILED):
            return servo_constants.SERVO_STATE_SERVOD_ISSUE

        # one of the reason why servo can not initialized
        if cr50_console == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_CR50_CONSOLE_MISSING
        if ccd_testlab == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_CCD_TESTLAB_ISSUE

        if (create_servo == hosts.VERIFY_FAILED
                    or init_servo == hosts.VERIFY_FAILED):
            return servo_constants.SERVO_STATE_SERVOD_PROXY_ISSUE

        if ec_board == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_EC_BROKEN
        if pwr_button == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_BAD_RIBBON_CABLE
        if lid_open == hosts.VERIFY_FAILED:
            return servo_constants.SERVO_STATE_LID_OPEN_FAILED

        metrics.Counter(
            'chromeos/autotest/repair/unknown_servo_state'
            ).increment(fields=self._get_host_metrics_data())
        logging.info('We do not have special state for this failure yet :)')
        return servo_constants.SERVO_STATE_BROKEN

    def is_servo_topology_supported(self):
        """Check if servo_topology is supported."""
        if self.is_containerized_servod():
            logging.info('Servod is running within a container.')
            return True
        if not self.is_up_fast():
            logging.info('Servo-Host is not reachable.')
            return False
        if not self.servo_serial:
            logging.info('Servo-topology required a servo serial.')
            return False
        if self.is_labstation():
            logging.info('Servod is running within labstation.')
            return True
        return False

    def get_topology(self):
        """Get servo topology."""
        if not self._topology:
            self._topology = servo_topology.ServoTopology(self)
        return self._topology

    def is_dual_setup(self):
        """Check is servo will run in dual setup.

        Dual setup used only for servo_v4 when used ccd_cr50 and servo_micro
        at the same time.
        """
        return self.servo_setup == servo_constants.SERVO_SETUP_VALUE_DUAL_V4

    def set_dut_health_profile(self, dut_health_profile):
        """
        @param dut_health_profile: A DeviceHealthProfile object.
        """
        logging.debug('setting dut_health_profile field to (%s)',
                      dut_health_profile)
        self._dut_health_profile = dut_health_profile

    def get_dut_health_profile(self):
        """
        @return A DeviceHealthProfile object.
        """
        return self._dut_health_profile

    def print_all_servo_of_host(self):
        """Print all servos detected on the host."""
        try:
            logging.info('\tDevices detected on the host:')
            devices = self.get_topology().get_list_available_servos()
            for device in devices:
                logging.info('\t%s', device)
        except Exception as e:
            logging.debug('(Not critical) Fail list all servos: %s', e)


def make_servo_hostname(dut_hostname):
    """Given a DUT's hostname, return the hostname of its servo.

    @param dut_hostname: hostname of a DUT.

    @return hostname of the DUT's servo.

    """
    host_parts = dut_hostname.split('.')
    host_parts[0] = host_parts[0] + '-servo'
    return '.'.join(host_parts)


def _map_afe_board_to_servo_board(afe_board):
    """Map a board we get from the AFE to a servo appropriate value.

    Many boards are identical to other boards for servo's purposes.
    This function makes that mapping.

    @param afe_board string board name received from AFE.
    @return board we expect servo to have.

    """
    KNOWN_SUFFIXES = ['-freon', '_freon', '_moblab', '-cheets']
    BOARD_MAP = {'gizmo': 'panther'}
    mapped_board = afe_board
    if afe_board in BOARD_MAP:
        mapped_board = BOARD_MAP[afe_board]
    else:
        for suffix in KNOWN_SUFFIXES:
            if afe_board.endswith(suffix):
                mapped_board = afe_board[0:-len(suffix)]
                break
    if mapped_board != afe_board:
        logging.info('Mapping AFE board=%s to %s', afe_board, mapped_board)
    return mapped_board


def get_servo_args_for_host(dut_host):
    """Return servo data associated with a given DUT.

    @param dut_host   Instance of `Host` on which to find the servo
                      attributes.
    @return `servo_args` dict with host and an optional port.
    """
    info = dut_host.host_info_store.get()
    servo_args = {k: v for k, v in six.iteritems(info.attributes)
                  if k in servo_constants.SERVO_ATTR_KEYS}

    if servo_constants.SERVO_HOST_SSH_PORT_ATTR in servo_args:
        try:
            servo_args[servo_constants.SERVO_HOST_SSH_PORT_ATTR] = int(
                    servo_args[servo_constants.SERVO_HOST_SSH_PORT_ATTR])
        except ValueError:
            logging.error('servo host port is not an int: %s',
                          servo_args[servo_constants.SERVO_HOST_SSH_PORT_ATTR])
            # Reset servo_args because we don't want to use an invalid port.
            servo_args.pop(servo_constants.SERVO_HOST_SSH_PORT_ATTR, None)

    if servo_constants.SERVO_PORT_ATTR in servo_args:
        try:
            servo_args[servo_constants.SERVO_PORT_ATTR] = int(
                servo_args[servo_constants.SERVO_PORT_ATTR])
        except ValueError:
            logging.error('servo port is not an int: %s',
                          servo_args[servo_constants.SERVO_PORT_ATTR])
            # Reset servo_args because we don't want to use an invalid port.
            servo_args.pop(servo_constants.SERVO_HOST_ATTR, None)

    if info.board:
        servo_board = _map_afe_board_to_servo_board(info.board)
        servo_args[servo_constants.SERVO_BOARD_ATTR] = servo_board
    if info.model:
        servo_args[servo_constants.SERVO_MODEL_ATTR] = info.model
    return servo_args if servo_constants.SERVO_HOST_ATTR in servo_args else None


def _tweak_args_for_ssp_moblab(servo_args):
    if (servo_args[servo_constants.SERVO_HOST_ATTR]
            in ['localhost', '127.0.0.1']):
        servo_args[servo_constants.SERVO_HOST_ATTR] = _CONFIG.get_config_value(
                'SSP', 'host_container_ip', type=str, default=None)


def create_servo_host(dut,
                      servo_args,
                      try_lab_servo=False,
                      try_servo_repair=False,
                      try_servo_recovery=True,
                      dut_host_info=None,
                      dut_health_profile=None):
    """Create a ServoHost object for a given DUT, if appropriate.

    This function attempts to create and verify or repair a `ServoHost`
    object for a servo connected to the given `dut`, subject to various
    constraints imposed by the parameters:
      * When the `servo_args` parameter is not `None`, a servo
        host must be created, and must be checked with `repair()`.
      * Otherwise, if a servo exists in the lab and `try_lab_servo` is
        true:
          * If `try_servo_repair` is true, then create a servo host and
            check it with `repair()`.
          * Otherwise, if the servo responds to `ping` then create a
            servo host and check it with `verify()`.

    In cases where `servo_args` was not `None`, repair failure
    exceptions are passed back to the caller; otherwise, exceptions
    are logged and then discarded.  Note that this only happens in cases
    where we're called from a test (not special task) control file that
    has an explicit dependency on servo.  In that case, we require that
    repair not write to `status.log`, so as to avoid polluting test
    results.

    TODO(jrbarnette):  The special handling for servo in test control
    files is a thorn in my flesh; I dearly hope to see it cut out before
    my retirement.

    Parameters for a servo host consist of a host name, port number, and
    DUT board, and are determined from one of these sources, in order of
    priority:
      * Servo attributes from the `dut` parameter take precedence over
        all other sources of information.
      * If a DNS entry for the servo based on the DUT hostname exists in
        the CrOS lab network, that hostname is used with the default
        port and the DUT's board.
      * If no other options are found, the parameters will be taken
        from the `servo_args` dict passed in from the caller.

    @param dut:                 An instance of `Host` from which to take
                                servo parameters (if available).
    @param servo_args:          A dictionary with servo parameters to use if
                                they can't be found from `dut`.  If this
                                argument is supplied, unrepaired exceptions
                                from `verify()` will be passed back to the
                                caller.
    @param try_lab_servo:       If not true, servo host creation will be
                                skipped unless otherwise required by the
                                caller.
    @param try_servo_repair:    If true, check a servo host with
                                `repair()` instead of `verify()`.
    @param try_servo_recovery:  If true, start servod in recovery mode.
                                Default value is True.
    @param dut_host_info:       A HostInfo object of the DUT that connected
                                to this servo.
    @param dut_health_profile:  DUT repair info with history.

    @returns: A ServoHost object or None. See comments above.

    """
    # We are explicitly looking for if servo_args is None here(which means
    # servo not needed), as servo_args == {} means servo is needed and
    # we expect load servo_args from host_info_store.
    if servo_args is None:
        servo_dependency = False
        local_run = False
    else:
        servo_dependency = True
        # If servo_args pass in directly, then this is a local test run.
        local_run = servo_constants.SERVO_HOST_ATTR in servo_args

    if local_run:
        logging.warning('User input servo_args detected, will attempt'
                        ' to start servod and initialize servo conncetion'
                        ' directly. All servo/servohost verify and repair'
                        ' steps will be skipped.')

    # Loading servo args from host_info_store.
    if dut is not None and (try_lab_servo or servo_dependency):
        servo_args_override = get_servo_args_for_host(dut)
        if servo_args_override is not None:
            if utils.in_moblab_ssp():
                _tweak_args_for_ssp_moblab(servo_args_override)
            logging.debug(
                    'Overriding provided servo_args (%s) with arguments'
                    ' determined from the host (%s)',
                    servo_args,
                    servo_args_override,
            )
            servo_args = servo_args_override

    if not servo_args:
        logging.debug('No servo_args provided, and failed to find overrides.')
        if try_lab_servo or servo_dependency:
            return None, servo_constants.SERVO_STATE_MISSING_CONFIG
        else:
            # For regular test case which not required the servo
            return None, None

    servo_hostname = servo_args.get(servo_constants.SERVO_HOST_ATTR)
    servo_port = servo_args.get(servo_constants.SERVO_PORT_ATTR)
    if not local_run:
        if not _is_servo_host_information_exist(servo_hostname, servo_port):
            logging.debug(
                    'Servo connection info missed hostname: %s , port: %s',
                    servo_hostname, servo_port)
            return None, servo_constants.SERVO_STATE_MISSING_CONFIG
        if not is_servo_host_information_valid(servo_hostname, servo_port):
            logging.debug(
                    'Servo connection info is incorrect hostname: %s , port: %s',
                    servo_hostname, servo_port)
            return None, servo_constants.SERVO_STATE_WRONG_CONFIG

        if try_servo_recovery == True:
            servo_args[servo_constants.SERVO_RECOVERY_MODE] = True

    newhost = ServoHost(**servo_args)
    if local_run:
        try:
            newhost.start_servod()
        except:
            # If we failed to start servod here, we can assume the servod
            # either already started or the test is running against a
            # non-standard servohost so the user will resiponsble for ensure
            # servod is running.
            pass
        try:
            newhost.initialize_servo()
            newhost.initialize_dut_for_servo()
            newhost._servo_state = servo_constants.SERVO_STATE_WORKING
            return newhost, newhost.get_servo_state()
        except Exception as e:
            logging.error('Failed to initialize servo. %s', e)
            return None, servo_constants.SERVO_STATE_BROKEN

    if newhost.is_containerized_servod():
        # TODO(otabek@): Update for servod-manager.
        # Servod docker is not available for access.
        newhost.start_containerized_servod()
    # use_icmp is always set to true in autotest, but in labpack
    # this is determined by autoserv args. We need to make this consistent
    # across labpack and autotest b/205855910
    elif newhost.use_icmp and not newhost.is_up_fast(count=3):
        # ServoHost has internal check to wait if servo-host is in reboot
        # process. If servo-host still is not available this check will stop
        # further attempts as we do not have any option to recover servo_host.
        return None, servo_constants.SERVO_STATE_NO_SSH

    # Reset or reboot servo device only during AdminRepair tasks.
    if try_servo_repair:
        if newhost._is_locked:
            # Print available servos on the host for debugging.
            newhost.print_all_servo_of_host()
            # Reset servo if the servo is locked, as we check if the servohost
            # is up, if the servohost is labstation and if the servohost is in
            # lab inside the locking logic.
            newhost.reset_servo()
        else:
            try:
                newhost.reboot_servo_v3_on_need()
            except Exception as e:
                logging.info('[Non-critical] Unexpected error while trying to'
                              ' reboot servo_v3, skipping the reboot; %s', e)

    if dut:
        newhost.set_dut_hostname(dut.hostname)
    if dut_host_info:
        newhost.set_dut_host_info(dut_host_info)
    if (dut_health_profile and (try_lab_servo or try_servo_repair)):
        try:
            if newhost.is_localhost():
                logging.info('Servohost is a localhost, skip device'
                             ' health profile setup...')
            else:
                dut_health_profile.init_profile(newhost)
                newhost.set_dut_health_profile(dut_health_profile)
        except Exception as e:
            logging.info(
                    '[Non-critical] Unexpected error while trying to'
                    ' load device health profile; %s', e)

    # Note that the logic of repair() includes everything done
    # by verify().  It's sufficient to call one or the other;
    # we don't need both.
    if servo_dependency:
        newhost.repair(silent=True)
        return newhost, newhost.get_servo_state()

    if try_servo_repair:
        try:
            newhost.repair()
        except Exception:
            logging.exception('servo repair failed for %s', newhost.hostname)
    else:
        try:
            newhost.verify()
        except Exception:
            logging.exception('servo verify failed for %s', newhost.hostname)
    return newhost, newhost.get_servo_state()


def _is_servo_host_information_exist(hostname, port):
    if hostname is None or len(hostname.strip()) == 0:
        return False
    if port is None:
        return False
    if not type(port) is int:
        try:
            int(port)
        except ValueError:
            return False
    return True


def is_servo_host_information_valid(hostname, port):
    """Check if provided servo attributes are valid.

    @param hostname Hostname of the servohost.
    @param port     servo port number.

    @returns: A bool value to indicate if provided servo attribute valid.
    """
    if not _is_servo_host_information_exist(hostname, port):
        return False
    # checking range and correct of the port
    port_int = int(port)
    if port_int < 1 or port_int > 65000:
        return False
    # we expecting host contain only latters, digits and '-' or '_'
    if not re.match('[a-zA-Z0-9-_\.:]*$', hostname) or len(hostname) < 5:
        return False
    return True
