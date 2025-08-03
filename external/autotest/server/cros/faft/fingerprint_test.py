# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import time

import six

from autotest_lib.server import test
from autotest_lib.server.cros import filesystem_util
from autotest_lib.client.common_lib import error, utils


class FingerprintTest(test.test):
    """Base class that sets up helpers for fingerprint tests."""
    version = 1

    # Location of firmware from the build on the DUT
    _FINGERPRINT_BUILD_FW_DIR = '/opt/google/biod/fw'

    _DISABLE_FP_UPDATER_FILE = '.disable_fp_updater'

    _UPSTART_DIR = '/etc/init'
    _BIOD_UPSTART_JOB_FILE = 'biod.conf'
    _STATEFUL_PARTITION_DIR = '/mnt/stateful_partition'

    _GENIMAGES_SCRIPT_NAME = 'gen_test_images.sh'
    _GENIMAGES_OUTPUT_DIR_NAME = 'images'

    _TEST_IMAGE_FORMAT_MAP = {
        'TEST_IMAGE_ORIGINAL': '%s.bin',
        'TEST_IMAGE_DEV': '%s.dev',
        'TEST_IMAGE_CORRUPT_FIRST_BYTE': '%s_corrupt_first_byte.bin',
        'TEST_IMAGE_CORRUPT_LAST_BYTE': '%s_corrupt_last_byte.bin',
        'TEST_IMAGE_DEV_RB_ZERO': '%s.dev.rb0',
        'TEST_IMAGE_DEV_RB_ONE': '%s.dev.rb1',
        'TEST_IMAGE_DEV_RB_NINE': '%s.dev.rb9'
    }

    _ROLLBACK_ZERO_BLOCK_ID = '0'
    _ROLLBACK_INITIAL_BLOCK_ID = '1'
    _ROLLBACK_INITIAL_MIN_VERSION = '0'
    _ROLLBACK_INITIAL_RW_VERSION = '0'

    _SERVER_GENERATED_FW_DIR_NAME = 'generated_fw'

    _DUT_TMP_PATH_BASE = '/tmp/fp_test'

    # Name of key in "futility show" output corresponds to the signing key ID
    _FUTILITY_KEY_ID_KEY_NAME = 'ID'

    # Types of firmware
    _FIRMWARE_TYPE_RO = 'RO'
    _FIRMWARE_TYPE_RW = 'RW'

    # Types of signing keys
    _KEY_TYPE_DEV = 'dev'
    _KEY_TYPE_PRE_MP = 'premp'
    _KEY_TYPE_MP = 'mp'

    # EC board names for FPMCUs
    _FP_BOARD_NAME_BLOONCHIPPER = 'bloonchipper'
    _FP_BOARD_NAME_DARTMONKEY = 'dartmonkey'
    _FP_BOARD_NAME_NOCTURNE = 'nocturne_fp'
    _FP_BOARD_NAME_NAMI = 'nami_fp'

    # Map from signing key ID to type of signing key
    _KEY_ID_MAP_ = {
        # bloonchipper
        '61382804da86b4156d666cc9a976088f8b647d44': _KEY_TYPE_DEV,
        '07b1af57220c196e363e68d73a5966047c77011e': _KEY_TYPE_PRE_MP,
        '1c590ef36399f6a2b2ef87079c135b69ef89eb60': _KEY_TYPE_MP,

        # dartmonkey
        '257a0aa3ac9e81aa4bc3aabdb6d3d079117c5799': _KEY_TYPE_MP,

        # nocturne
        '8a8fc039a9463271995392f079b83ce33832d07d': _KEY_TYPE_DEV,
        '6f38c866182bd9bf7a4462c06ac04fa6a0074351': _KEY_TYPE_MP,
        'f6f7d96c48bd154dbae7e3fe3a3b4c6268a10934': _KEY_TYPE_PRE_MP,

        # nami
        '754aea623d69975a22998f7b97315dd53115d723': _KEY_TYPE_PRE_MP,
        '35486c0090ca390408f1fbbf2a182966084fe2f8': _KEY_TYPE_MP

    }

    # RO versions that are flashed in the factory
    # (for eternity for a given board)
    _GOLDEN_RO_FIRMWARE_VERSION_MAP = {
            _FP_BOARD_NAME_BLOONCHIPPER: {
                    'hatch': 'bloonchipper_v2.0.4277-9f652bb3',
                    'zork': 'bloonchipper_v2.0.5938-197506c1',
                    'volteer': 'bloonchipper_v2.0.5938-197506c1',
                    'brya': 'bloonchipper_v2.0.5938-197506c1',
                    'guybrush': 'bloonchipper_v2.0.5938-197506c1',
            },
            _FP_BOARD_NAME_DARTMONKEY: 'dartmonkey_v2.0.2887-311310808',
            _FP_BOARD_NAME_NOCTURNE: 'nocturne_fp_v2.2.64-58cf5974e',
            _FP_BOARD_NAME_NAMI: 'nami_fp_v2.2.144-7a08e07eb',
    }

    _FIRMWARE_VERSION_SHA256SUM = 'sha256sum'
    _FIRMWARE_VERSION_RO_VERSION = 'ro_version'
    _FIRMWARE_VERSION_RW_VERSION = 'rw_version'
    _FIRMWARE_VERSION_KEY_ID = 'key_id'

    # Map of attributes for a given board's various firmware file releases
    #
    # Two purposes:
    #   1) Documents the exact versions and keys used for a given firmware file.
    #   2) Used to verify that files that end up in the build (and therefore
    #      what we release) is exactly what we expect.
    _FIRMWARE_VERSION_MAP = {
        _FP_BOARD_NAME_BLOONCHIPPER: {
            'bloonchipper_v2.0.4277-9f652bb3-RO_v2.0.13589-727a419-RW.bin': {
                _FIRMWARE_VERSION_SHA256SUM: 'b500a08d1c4f49ac1455214f1957f178288a2f4b36b40e7cd49acad1d0896ccc',
                _FIRMWARE_VERSION_RO_VERSION: 'bloonchipper_v2.0.4277-9f652bb3',
                _FIRMWARE_VERSION_RW_VERSION: 'bloonchipper_v2.0.13589-727a419',
                _FIRMWARE_VERSION_KEY_ID: '1c590ef36399f6a2b2ef87079c135b69ef89eb60',
            },
            'bloonchipper_v2.0.5938-197506c1-RO_v2.0.13589-727a419-RW.bin': {
                _FIRMWARE_VERSION_SHA256SUM: 'dfa1a9e409893441c990edde86dbe6d0e301c03b7a9e604ec6af5fc1691ef1be',
                _FIRMWARE_VERSION_RO_VERSION: 'bloonchipper_v2.0.5938-197506c1',
                _FIRMWARE_VERSION_RW_VERSION: 'bloonchipper_v2.0.13589-727a419',
                _FIRMWARE_VERSION_KEY_ID: '1c590ef36399f6a2b2ef87079c135b69ef89eb60',
            },
        },
        _FP_BOARD_NAME_NOCTURNE: {
            'nocturne_fp_v2.2.64-58cf5974e-RO_v2.0.13584-6fcfe697-RW.bin': {
                _FIRMWARE_VERSION_SHA256SUM: '8ebc978bf18fc1629a8ab9b33ac91817d850ce5ca9c55dc69c99b0acfb540948',
                _FIRMWARE_VERSION_RO_VERSION: 'nocturne_fp_v2.2.64-58cf5974e',
                _FIRMWARE_VERSION_RW_VERSION: 'nocturne_fp_v2.0.13584-6fcfe697',
                _FIRMWARE_VERSION_KEY_ID: '6f38c866182bd9bf7a4462c06ac04fa6a0074351',
            },
        },
        _FP_BOARD_NAME_NAMI: {
            'nami_fp_v2.2.144-7a08e07eb-RO_v2.0.13584-6fcfe69780-RW.bin': {
                _FIRMWARE_VERSION_SHA256SUM: 'e198db08020ac71a11a53d641d6ada750061fb3f3faa2728aab7835266ed9e7b',
                _FIRMWARE_VERSION_RO_VERSION: 'nami_fp_v2.2.144-7a08e07eb',
                _FIRMWARE_VERSION_RW_VERSION: 'nami_fp_v2.0.13584-6fcfe69780',
                _FIRMWARE_VERSION_KEY_ID: '35486c0090ca390408f1fbbf2a182966084fe2f8',
            },
        },
        _FP_BOARD_NAME_DARTMONKEY: {
            'dartmonkey_v2.0.2887-311310808-RO_v2.0.13584-6fcfe6978-RW.bin': {
                _FIRMWARE_VERSION_SHA256SUM: '8fa168c19d886b5fe8e852bba7d3b04cd0cd2344d377d9b3d278a45d76b206a1',
                _FIRMWARE_VERSION_RO_VERSION: 'dartmonkey_v2.0.2887-311310808',
                _FIRMWARE_VERSION_RW_VERSION: 'dartmonkey_v2.0.13584-6fcfe6978',
                _FIRMWARE_VERSION_KEY_ID: '257a0aa3ac9e81aa4bc3aabdb6d3d079117c5799',
            }
        }
    }

    _BIOD_UPSTART_JOB_NAME = 'biod'
    _POWERD_UPSTART_JOB_NAME = 'powerd'
    # TODO(crbug.com/925545)
    _TIMBERSLIDE_UPSTART_JOB_NAME = \
        'timberslide LOG_PATH=/sys/kernel/debug/cros_fp/console_log'

    _INIT_ENTROPY_CMD = 'bio_wash --factory_init'

    _CROS_FP_ARG = '--name=cros_fp'
    _CROS_CONFIG_FINGERPRINT_PATH = '/fingerprint'
    _ECTOOL_RO_VERSION = 'RO version'
    _ECTOOL_RW_VERSION = 'RW version'
    _ECTOOL_FIRMWARE_COPY = 'Firmware copy'
    _ECTOOL_ROLLBACK_BLOCK_ID = 'Rollback block id'
    _ECTOOL_ROLLBACK_MIN_VERSION = 'Rollback min version'
    _ECTOOL_ROLLBACK_RW_VERSION = 'RW rollback version'

    @staticmethod
    def _parse_colon_delimited_output(ectool_output):
        """
        Converts ectool's (or any other tool with similar output) colon
        delimited output into python dict. Ignores any lines that do not have
        colons.

        Example:
        RO version:    nocturne_fp_v2.2.64-58cf5974e
        RW version:    nocturne_fp_v2.2.110-b936c0a3c

        becomes:
        {
          'RO version': 'nocturne_fp_v2.2.64-58cf5974e',
          'RW version': 'nocturne_fp_v2.2.110-b936c0a3c'
        }
        """
        ret = {}
        try:
            for line in ectool_output.strip().split('\n'):
                splits = line.split(':', 1)
                if len(splits) != 2:
                    continue
                key = splits[0].strip()
                val = splits[1].strip()
                ret[key] = val
        except:
            raise error.TestFail('Unable to parse ectool output: %s'
                                 % ectool_output)
        return ret

    def initialize(self, host):
        """Perform minimal initialization, to avoid AttributeError in cleanup"""
        self.host = host
        self.servo = host.servo

        self._validate_compatible_servo_version()

        self.servo.initialize_dut()

        self.fp_board = self.get_fp_board()
        self._build_fw_file = self.get_build_fw_file()

    def setup_test(self, test_dir, use_dev_signed_fw=False,
                   enable_hardware_write_protect=True,
                   enable_software_write_protect=True,
                   force_firmware_flashing=False, init_entropy=True):
        """Perform more complete initialization, including copying test files"""
        logging.info('HW write protect enabled: %s',
                     self.is_hardware_write_protect_enabled())

        # TODO(crbug.com/925545): stop timberslide so /var/log/cros_fp.log
        # continues to update after flashing.
        self._timberslide_running = self.host.upstart_status(
            self._TIMBERSLIDE_UPSTART_JOB_NAME)
        if self._timberslide_running:
            logging.info('Stopping %s', self._TIMBERSLIDE_UPSTART_JOB_NAME)
            self.host.upstart_stop(self._TIMBERSLIDE_UPSTART_JOB_NAME)

        self._biod_running = self.host.upstart_status(
            self._BIOD_UPSTART_JOB_NAME)
        if self._biod_running:
            logging.info('Stopping %s', self._BIOD_UPSTART_JOB_NAME)
            self.host.upstart_stop(self._BIOD_UPSTART_JOB_NAME)

        # TODO(b/183123775): Remove when bug is fixed.
        #  Disabling powerd to prevent the display from turning off, which kills
        #  USB on some platforms.
        self._powerd_running = self.host.upstart_status(
            self._POWERD_UPSTART_JOB_NAME)
        if self._powerd_running:
            logging.info('Stopping %s', self._POWERD_UPSTART_JOB_NAME)
            self.host.upstart_stop(self._POWERD_UPSTART_JOB_NAME)

        # On some platforms an AP reboot is needed after flashing firmware to
        # rebind the driver.
        self._dut_needs_reboot = self.is_uart_device()

        if filesystem_util.is_rootfs_writable(self.host):
            if self._dut_needs_reboot:
                logging.warning('rootfs is writable')
            else:
                raise error.TestFail('rootfs is writable')

        if not self.biod_upstart_job_enabled():
            raise error.TestFail(
                    'Biod upstart job is disabled at the beginning of test')
        if not self.fp_updater_is_enabled():
            raise error.TestFail(
                    'Fingerprint firmware updater is disabled at the beginning of test'
            )

        # Disable biod and updater so that they won't interfere after reboot.
        if self._dut_needs_reboot:
            self.disable_biod_upstart_job()
            self.disable_fp_updater()

        # create tmp working directory on device (automatically cleaned up)
        self._dut_working_dir = self.host.get_tmp_dir(
            parent=self._DUT_TMP_PATH_BASE)
        logging.info('Created dut_working_dir: %s', self._dut_working_dir)
        self.copy_files_to_dut(test_dir, self._dut_working_dir)

        self.validate_build_fw_file()

        gen_script = os.path.abspath(os.path.join(self.autodir,
                                                  'server', 'cros', 'faft',
                                                  self._GENIMAGES_SCRIPT_NAME))
        self._dut_firmware_test_images_dir = \
            self._generate_test_firmware_images(gen_script,
                                                self._build_fw_file,
                                                self._dut_working_dir)
        logging.info('dut_firmware_test_images_dir: %s',
                     self._dut_firmware_test_images_dir)

        self._initialize_test_firmware_image_attrs(
            self._dut_firmware_test_images_dir)

        self._initialize_running_fw_version(use_dev_signed_fw,
                                            force_firmware_flashing)

        if init_entropy:
            self._initialize_fw_entropy()

        self._initialize_hw_and_sw_write_protect(enable_hardware_write_protect,
                                                 enable_software_write_protect)

    def cleanup(self):
        """Restores original state."""
        if hasattr(self, '_need_fw_restore') and self._need_fw_restore:
            # Once the tests complete we need to make sure we're running the
            # original firmware (not dev version) and potentially reset rollback.
            self._initialize_running_fw_version(use_dev_signed_fw=False,
                                                force_firmware_flashing=False)
            self._initialize_fw_entropy()

        # Re-enable biod and updater after flashing and initializing entropy so
        # that they don't interfere if there was a reboot.
        if hasattr(self, '_dut_needs_reboot') and self._dut_needs_reboot:
            if not self.biod_upstart_job_enabled():
                self.enable_biod_upstart_job()
            if not self.fp_updater_is_enabled():
                self.enable_fp_updater()
        self._initialize_hw_and_sw_write_protect(
            enable_hardware_write_protect=True,
            enable_software_write_protect=True)
        # TODO(b/183123775)
        if hasattr(self, '_powerd_running') and self._powerd_running:
            logging.info('Restarting powerd')
            self.host.upstart_restart(self._POWERD_UPSTART_JOB_NAME)
        if hasattr(self, '_biod_running') and self._biod_running:
            logging.info('Restarting biod')
            self.host.upstart_restart(self._BIOD_UPSTART_JOB_NAME)
        # TODO(crbug.com/925545)
        if hasattr(self, '_timberslide_running') and self._timberslide_running:
            logging.info('Restarting timberslide')
            self.host.upstart_restart(self._TIMBERSLIDE_UPSTART_JOB_NAME)

        super(FingerprintTest, self).cleanup()

    def after_run_once(self):
        """Logs which iteration just ran."""
        logging.info('successfully ran iteration %d', self.iteration)

    def _validate_compatible_servo_version(self):
        """Asserts if a compatible servo version is not attached."""
        servo_version = self.servo.get_servo_version()
        logging.info('servo version: %s', servo_version)

    def _generate_test_firmware_images(self, gen_script, build_fw_file,
                                       dut_working_dir):
        """
        Copies the fingerprint firmware from the DUT to the server running
        the tests, which runs a script to generate various test versions of
        the firmware.

        @return full path to location of test images on DUT
        """
        # create subdirectory under existing tmp dir
        server_tmp_dir = os.path.join(self.tmpdir,
                                      self._SERVER_GENERATED_FW_DIR_NAME)
        os.mkdir(server_tmp_dir)
        logging.info('server_tmp_dir: %s', server_tmp_dir)

        # Copy firmware from device to server
        self.get_files_from_dut(build_fw_file, server_tmp_dir)

        # Run the test image generation script on server
        pushd = os.getcwd()
        os.chdir(server_tmp_dir)
        cmd = ' '.join([gen_script,
                        self.get_fp_board(),
                        os.path.basename(build_fw_file)])
        result = self.run_server_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Failed to run test image generation script')

        os.chdir(pushd)

        # Copy resulting files to DUT tmp dir
        server_generated_images_dir = \
            os.path.join(server_tmp_dir, self._GENIMAGES_OUTPUT_DIR_NAME)
        self.copy_files_to_dut(server_generated_images_dir, dut_working_dir)

        return os.path.join(dut_working_dir, self._GENIMAGES_OUTPUT_DIR_NAME)

    def _initialize_test_firmware_image_attrs(self, dut_fw_test_images_dir):
        """Sets attributes with full path to test images on DUT.

        Example: self.TEST_IMAGE_DEV = /some/path/images/nocturne_fp.dev
        """
        for key, val in six.iteritems(self._TEST_IMAGE_FORMAT_MAP):
            full_path = os.path.join(dut_fw_test_images_dir,
                                     val % self.get_fp_board())
            setattr(self, key, full_path)

    def _initialize_running_fw_version(self, use_dev_signed_fw,
                                       force_firmware_flashing):
        """
        Ensures that the running firmware version matches build version
        and factory rollback settings; flashes to correct version if either
        fails to match is requested to force flashing.

        RO firmware: original version released at factory
        RW firmware: firmware from current build
        """
        build_rw_firmware_version = \
            self.get_build_rw_firmware_version(use_dev_signed_fw)
        golden_ro_firmware_version = \
            self.get_golden_ro_firmware_version(use_dev_signed_fw)
        logging.info('Build RW firmware version: %s', build_rw_firmware_version)
        logging.info('Golden RO firmware version: %s',
                     golden_ro_firmware_version)

        running_rw_firmware = self.ensure_running_rw_firmware()

        fw_versions_match = self.running_fw_version_matches_given_version(
            build_rw_firmware_version, golden_ro_firmware_version)

        if not running_rw_firmware or not fw_versions_match \
            or not self.is_rollback_set_to_initial_val() \
            or force_firmware_flashing:
            fw_file = self._build_fw_file
            if use_dev_signed_fw:
                fw_file = self.TEST_IMAGE_DEV
            self.flash_rw_ro_firmware(fw_file)
            if not self.running_fw_version_matches_given_version(
                build_rw_firmware_version, golden_ro_firmware_version):
                raise error.TestFail(
                    'Running firmware version does not match expected version')

    def _initialize_fw_entropy(self):
        """Sets the entropy (key) in FPMCU flash (if not set)."""
        result = self.run_cmd(self._INIT_ENTROPY_CMD)
        if result.exit_status != 0:
            raise error.TestFail('Unable to initialize entropy')

    def _initialize_hw_and_sw_write_protect(self, enable_hardware_write_protect,
                                            enable_software_write_protect):
        """Enables/disables hardware/software write protect."""
        # sw: 0, hw: 0 => initial_hw(0) -> sw(0) -> hw(0)
        # sw: 0, hw: 1 => initial_hw(0) -> sw(0) -> hw(1)
        # sw: 1, hw: 0 => initial_hw(1) -> sw(1) -> hw(0)
        # sw: 1, hw: 1 => initial_hw(1) -> sw(1) -> hw(1)
        hardware_write_protect_initial_enabled = True
        if not enable_software_write_protect:
            hardware_write_protect_initial_enabled = False

        self.set_hardware_write_protect(hardware_write_protect_initial_enabled)

        self.set_software_write_protect(enable_software_write_protect)
        self.set_hardware_write_protect(enable_hardware_write_protect)

    def get_fp_board(self):
        """Returns name of fingerprint EC.

        nocturne and nami are special cases and have "_fp" appended. Newer
        FPMCUs have unique names.
        See go/cros-fingerprint-firmware-branching-and-signing.
        """
        # Use cros_config to get fingerprint board.
        # Due to b/160271883, we will try running the cmd via cat instead.
        result = self._run_cros_config_cmd_cat('fingerprint/board')
        if result.exit_status != 0:
            raise error.TestFail(
                'Unable to get fingerprint board with cros_config')
        return result.stdout.rstrip()

    def is_uart_device(self) -> bool:
        """Returns True if the boards transpot device is UART"""
        uart_devices = ['zork', 'guybrush']
        return self.get_host_board() in uart_devices

    def get_host_board(self):
        """Returns name of the host board."""
        return self.host.get_board().split(':')[-1]

    def get_build_fw_file(self):
        """Returns full path to build FW file on DUT."""
        ls_cmd = 'ls %s/%s*.bin' % (
            self._FINGERPRINT_BUILD_FW_DIR, self.fp_board)
        result = self.run_cmd(ls_cmd)
        if result.exit_status != 0:
            raise error.TestFail(
                'Unable to find firmware file on device:'
                ' command failed (rc=%s): %s'
                % (result.exit_status, result.stderr.strip() or ls_cmd))
        ret = result.stdout.rstrip()
        logging.info('Build firmware file: %s', ret)
        return ret

    def check_equal(self, a, b):
        """Raises exception if "a" does not equal "b"."""
        if a != b:
            raise error.TestFail('"%s" does not match expected "%s" for board '
                                 '%s' % (a, b, self.get_fp_board()))

    def validate_build_fw_file(self,
                               allowed_types=(_KEY_TYPE_PRE_MP, _KEY_TYPE_MP)):
        """
        Checks that all attributes in the given firmware file match their
        expected values.

        @param allowed_types: If key type is something else, raise TestFail.
                              Default: pre-MP or MP.
        @type allowed_types: tuple | list
        """
        build_fw_file = self._build_fw_file
        # check hash
        actual_hash = self._calculate_sha256sum(build_fw_file)
        expected_hash = self._get_expected_firmware_hash(build_fw_file)
        self.check_equal(actual_hash, expected_hash)

        # check signing key_id
        actual_key_id = self._read_firmware_key_id(build_fw_file)
        expected_key_id = self._get_expected_firmware_key_id(build_fw_file)
        self.check_equal(actual_key_id, expected_key_id)

        # check that the signing key for firmware in the build
        # is "pre mass production" (pre-mp) or "mass production" (MP)
        key_type = self._get_key_type(actual_key_id)
        if key_type not in allowed_types:
            raise error.TestFail(
                'Firmware key type must be %s for board %s; got %s (%s)' %
                (' or '.join(allowed_types), self.fp_board, key_type,
                 actual_key_id))

        # check ro_version
        actual_ro_version = self._read_firmware_ro_version(build_fw_file)
        expected_ro_version = \
            self._get_expected_firmware_ro_version(build_fw_file)
        self.check_equal(actual_ro_version, expected_ro_version)

        # check rw_version
        actual_rw_version = self._read_firmware_rw_version(build_fw_file)
        expected_rw_version = \
            self._get_expected_firmware_rw_version(build_fw_file)
        self.check_equal(actual_rw_version, expected_rw_version)

        logging.info("Validated build firmware metadata.")

    def _get_key_type(self, key_id):
        """Returns the key "type" for a given "key id"."""
        key_type = self._KEY_ID_MAP_.get(key_id)
        if key_type is None:
            raise error.TestFail('Unable to get key type for key id: %s'
                                 % key_id)
        return key_type

    def _get_expected_firmware_info(self, build_fw_file, info_type):
        """
        Returns expected firmware info for a given firmware file name.
        """
        build_fw_file_name = os.path.basename(build_fw_file)

        board = self.get_fp_board()
        board_expected_fw_info = self._FIRMWARE_VERSION_MAP.get(board)
        if board_expected_fw_info is None:
            raise error.TestFail('Unable to get firmware info for board: %s'
                                 % board)

        expected_fw_info = board_expected_fw_info.get(build_fw_file_name)
        if expected_fw_info is None:
            raise error.TestFail('Unable to get firmware info for file: %s'
                                 % build_fw_file_name)

        ret = expected_fw_info.get(info_type)
        if ret is None:
            raise error.TestFail('Unable to get firmware info type: %s'
                                 % info_type)

        return ret

    def _get_expected_firmware_hash(self, build_fw_file):
        """Returns expected hash of firmware file."""
        return self._get_expected_firmware_info(
            build_fw_file, self._FIRMWARE_VERSION_SHA256SUM)

    def _get_expected_firmware_key_id(self, build_fw_file):
        """Returns expected "key id" for firmware file."""
        return self._get_expected_firmware_info(
            build_fw_file, self._FIRMWARE_VERSION_KEY_ID)

    def _get_expected_firmware_ro_version(self, build_fw_file):
        """Returns expected RO version for firmware file."""
        return self._get_expected_firmware_info(
            build_fw_file, self._FIRMWARE_VERSION_RO_VERSION)

    def _get_expected_firmware_rw_version(self, build_fw_file):
        """Returns expected RW version for firmware file."""
        return self._get_expected_firmware_info(
            build_fw_file, self._FIRMWARE_VERSION_RW_VERSION)

    def _read_firmware_key_id(self, file_name):
        """Returns "key id" as read from the given file."""
        result = self._run_futility_show_cmd(file_name)
        parsed = self._parse_colon_delimited_output(result)
        key_id = parsed.get(self._FUTILITY_KEY_ID_KEY_NAME)
        if key_id is None:
            raise error.TestFail('Failed to get key ID for file: %s'
                                 % file_name)
        return key_id

    def _read_firmware_ro_version(self, file_name):
        """Returns RO firmware version as read from the given file."""
        return self._run_dump_fmap_cmd(file_name, 'RO_FRID')

    def _read_firmware_rw_version(self, file_name):
        """Returns RW firmware version as read from the given file."""
        return self._run_dump_fmap_cmd(file_name, 'RW_FWID')

    def _calculate_sha256sum(self, file_name):
        """Returns SHA256 hash of the given file contents."""
        result = self._run_sha256sum_cmd(file_name)
        return result.stdout.split()[0]

    def _get_running_firmware_info(self, key):
        """
        Returns requested firmware info (RW version, RO version, or firmware
        type).
        """
        result = self._run_ectool_cmd('version')
        parsed = self._parse_colon_delimited_output(result.stdout)
        if result.exit_status != 0:
            raise error.TestFail('Failed to get running firmware info')
        info = parsed.get(key)
        if info is None:
            raise error.TestFail(
                'Failed to get running firmware info: %s' % key)
        return info

    def get_running_rw_firmware_version(self):
        """Returns running RW firmware version."""
        return self._get_running_firmware_info(self._ECTOOL_RW_VERSION)

    def get_running_ro_firmware_version(self):
        """Returns running RO firmware version."""
        return self._get_running_firmware_info(self._ECTOOL_RO_VERSION)

    def get_running_firmware_type(self):
        """Returns type of firmware we are running (RW or RO)."""
        return self._get_running_firmware_info(self._ECTOOL_FIRMWARE_COPY)

    def _get_rollback_info(self, info_type):
        """Returns requested type of rollback info."""
        result = self._run_ectool_cmd('rollbackinfo')
        parsed = self._parse_colon_delimited_output(result.stdout)
        if result.exit_status != 0:
            raise error.TestFail('Failed to get rollback info')
        info = parsed.get(info_type)
        if info is None:
            raise error.TestFail('Failed to get rollback info: %s' % info_type)
        return info

    def get_rollback_id(self):
        """Returns rollback ID."""
        return self._get_rollback_info(self._ECTOOL_ROLLBACK_BLOCK_ID)

    def get_rollback_min_version(self):
        """Returns rollback min version."""
        return self._get_rollback_info(self._ECTOOL_ROLLBACK_MIN_VERSION)

    def get_rollback_rw_version(self):
        """Returns RW rollback version."""
        return self._get_rollback_info(self._ECTOOL_ROLLBACK_RW_VERSION)

    def _construct_dev_version(self, orig_version):
        """
        Given a "regular" version string from a signed build, returns the
        special "dev" version that we use when creating the test images.
        """
        fw_version = orig_version
        if len(fw_version) + len('.dev') > 31:
            fw_version = fw_version[:27]
        fw_version = fw_version + '.dev'
        return fw_version

    def get_golden_ro_firmware_version(self, use_dev_signed_fw):
        """Returns RO firmware version used in factory."""
        board = self.get_fp_board()
        golden_version = self._GOLDEN_RO_FIRMWARE_VERSION_MAP.get(board)
        if isinstance(golden_version, dict):
            golden_version = golden_version.get(self.get_host_board())
        if golden_version is None:
            raise error.TestFail('Unable to get golden RO version for board: %s'
                                 % board)
        if use_dev_signed_fw:
            golden_version = self._construct_dev_version(golden_version)
        return golden_version

    def get_build_rw_firmware_version(self, use_dev_signed_fw):
        """Returns RW firmware version from build."""
        fw_version = self._read_firmware_rw_version(self._build_fw_file)
        if use_dev_signed_fw:
            fw_version = self._construct_dev_version(fw_version)
        return fw_version

    def ensure_running_rw_firmware(self):
        """
        Check whether the device is running RW firmware. If not, try rebooting
        to RW.

        @return true if successfully verified running RW firmware, false
        otherwise.
        """
        try:
            if self.get_running_firmware_type() != self._FIRMWARE_TYPE_RW:
                self._reboot_ec()
                if self.get_running_firmware_type() != self._FIRMWARE_TYPE_RW:
                    # RW may be corrupted.
                    return False
        except:
            # We may not always be able to read the firmware version.
            # For example, if the firmware is erased due to RDP1, running any
            # commands (such as getting the version) won't work.
            return False
        return True

    def running_fw_version_matches_given_version(self, rw_version, ro_version):
        """
        Returns True if the running RO and RW firmware versions match the
        provided versions.
        """
        try:
            running_rw_firmware_version = self.get_running_rw_firmware_version()
            running_ro_firmware_version = self.get_running_ro_firmware_version()

            logging.info('RW firmware, running: %s, expected: %s',
                         running_rw_firmware_version, rw_version)
            logging.info('RO firmware, running: %s, expected: %s',
                         running_ro_firmware_version, ro_version)

            return (running_rw_firmware_version == rw_version and
                    running_ro_firmware_version == ro_version)
        except:
            # We may not always be able to read the firmware version.
            # For example, if the firmware is erased due to RDP1, running any
            # commands (such as getting the version) won't work.
            return False

    def is_rollback_set_to_initial_val(self):
        """
        Returns True if rollbackinfo matches the initial value that it
        should have coming from the factory.
        """
        return (self.get_rollback_id() ==
                self._ROLLBACK_INITIAL_BLOCK_ID
                and
                self.get_rollback_min_version() ==
                self._ROLLBACK_INITIAL_MIN_VERSION
                and
                self.get_rollback_rw_version() ==
                self._ROLLBACK_INITIAL_RW_VERSION)

    def is_rollback_unset(self):
        """
        Returns True if rollbackinfo matches the uninitialized value that it
        should have after flashing the entire flash.
        """
        return (self.get_rollback_id() == self._ROLLBACK_ZERO_BLOCK_ID
                and self.get_rollback_min_version() ==
                self._ROLLBACK_INITIAL_MIN_VERSION
                and self.get_rollback_rw_version() ==
                self._ROLLBACK_INITIAL_RW_VERSION)

    def biod_upstart_job_enabled(self):
        """Returns whether biod's upstart job file is at original location."""
        return self.host.is_file_exists(
                os.path.join(self._UPSTART_DIR, self._BIOD_UPSTART_JOB_FILE))

    def disable_biod_upstart_job(self):
        """
        Disable biod's upstart job so that biod will not run after a reboot.
        """
        logging.info('Disabling biod\'s upstart job')
        filesystem_util.make_rootfs_writable(self.host)
        cmd = 'mv %s %s' % (os.path.join(
                self._UPSTART_DIR,
                self._BIOD_UPSTART_JOB_FILE), self._STATEFUL_PARTITION_DIR)
        result = self.run_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Unable to disable biod upstart job: %s' %
                                 result.stderr.strip())

    def enable_biod_upstart_job(self):
        """
        Enable biod's upstart job so that biod will run after a reboot.
        """
        logging.info('Enabling biod\'s upstart job')
        filesystem_util.make_rootfs_writable(self.host)
        cmd = 'mv %s %s' % (os.path.join(
                self._STATEFUL_PARTITION_DIR,
                self._BIOD_UPSTART_JOB_FILE), self._UPSTART_DIR)
        result = self.run_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Unable to enable biod upstart job: %s' %
                                 result.stderr.strip())

    def fp_updater_is_enabled(self):
        """Returns whether the fingerprint firmware updater is disabled."""
        return not self.host.is_file_exists(
                os.path.join(self._FINGERPRINT_BUILD_FW_DIR,
                             self._DISABLE_FP_UPDATER_FILE))

    def disable_fp_updater(self):
        """Disable the fingerprint firmware updater."""
        filesystem_util.make_rootfs_writable(self.host)
        touch_cmd = 'touch %s' % os.path.join(self._FINGERPRINT_BUILD_FW_DIR,
                                              self._DISABLE_FP_UPDATER_FILE)
        logging.info('Disabling fp firmware updater')
        result = self.run_cmd(touch_cmd)
        if result.exit_status != 0:
            raise error.TestFail(
                    'Unable to write file to disable fp updater:'
                    ' command failed (rc=%s): %s' %
                    (result.exit_status, result.stderr.strip() or touch_cmd))
        self.run_cmd('sync')

    def enable_fp_updater(self):
        """
        Enable the fingerprint firmware updater. Must be called only after
        disable_fp_updater().
        """
        filesystem_util.make_rootfs_writable(self.host)
        rm_cmd = 'rm %s' % os.path.join(self._FINGERPRINT_BUILD_FW_DIR,
                                        self._DISABLE_FP_UPDATER_FILE)
        logging.info('Enabling fp firmware updater')
        result = self.run_cmd(rm_cmd)
        if result.exit_status != 0:
            raise error.TestFail(
                    'Unable to rm .disable_fp_updater:'
                    ' command failed (rc=%s): %s' %
                    (result.exit_status, result.stderr.strip() or rm_cmd))
        self.run_cmd('sync')

    def flash_rw_ro_firmware(self, fw_path):
        """Flashes *all* firmware (both RO and RW)."""
        # Check if FPMCU firmware needs to be re-flashed during cleanup
        self._need_fw_restore = True
        self.set_hardware_write_protect(False)
        flash_cmd = 'flash_fp_mcu' + ' --noservices ' + fw_path
        logging.info('Running flash cmd: %s', flash_cmd)
        flash_result = self.run_cmd(flash_cmd)
        self.set_hardware_write_protect(True)

        # Zork cannot rebind cros-ec-uart after flashing, so an AP reboot is
        # needed to talk to FPMCU. See b/170213489.
        # We have to do this even if flashing failed.
        if hasattr(self, '_dut_needs_reboot') and self._dut_needs_reboot:
            self.host.reboot()
            if self.fp_updater_is_enabled():
                raise error.TestFail(
                        'Fp updater was not disabled when firmware is flashed')
            # If we just re-enable fp updater, it can still update (race
            # condition), so do it later in cleanup.

        if flash_result.exit_status != 0:
            raise error.TestFail('Flashing RW/RO firmware failed')

    def is_hardware_write_protect_enabled(self):
        """Returns state of hardware write protect."""
        fw_wp_state = self.servo.get('fw_wp_state')
        return fw_wp_state == 'on' or fw_wp_state == 'force_on'

    def set_hardware_write_protect(self, enable):
        """Enables or disables hardware write protect."""
        self.servo.set('fw_wp_state', 'force_on' if enable else 'force_off')

    def set_software_write_protect(self, enable):
        """Enables or disables software write protect."""
        arg  = 'enable' if enable else 'disable'
        self._run_ectool_cmd('flashprotect ' + arg)
        # TODO(b/116396469): The flashprotect command returns an error even on
        # success.
        # if result.exit_status != 0:
        #    raise error.TestFail('Failed to modify software write protect')

        # TODO(b/116396469): "flashprotect enable" command is slow, so wait for
        # it to complete before attempting to reboot.
        time.sleep(2)
        self._reboot_ec()

    def _reboot_ec(self):
        """Reboots the fingerprint MCU (FPMCU)."""
        self._run_ectool_cmd('reboot_ec')
        # TODO(b/116396469): The reboot_ec command returns an error even on
        # success.
        # if result.exit_status != 0:
        #    raise error.TestFail('Failed to reboot ec')
        time.sleep(2)

    def get_files_from_dut(self, src, dst):
        """Copes files from DUT to server."""
        logging.info('Copying files from (%s) to (%s).', src, dst)
        self.host.get_file(src, dst, delete_dest=True)

    def copy_files_to_dut(self, src_dir, dst_dir):
        """Copies files from server to DUT."""
        logging.info('Copying files from (%s) to (%s).', src_dir, dst_dir)
        self.host.send_file(src_dir, dst_dir, delete_dest=True)
        # Sync the filesystem in case we need to reboot the AP soon.
        self.run_cmd('sync')

    def run_server_cmd(self, command, timeout=65):
        """Runs command on server; return result with output and exit code."""
        logging.info('Server execute: %s', command)
        result = utils.run(command, timeout=timeout, ignore_status=True)
        logging.info('exit_code: %d', result.exit_status)
        logging.info('stdout:\n%s', result.stdout)
        logging.info('stderr:\n%s', result.stderr)
        return result

    def run_cmd(self, command, timeout=300):
        """Runs command on the DUT; return result with output and exit code."""
        logging.debug('DUT Execute: %s', command)
        result = self.host.run(command, timeout=timeout, ignore_status=True)
        logging.info('exit_code: %d', result.exit_status)
        logging.info('stdout:\n%s', result.stdout)
        logging.info('stderr:\n%s', result.stderr)
        return result

    def _run_ectool_cmd(self, command):
        """Runs ectool on DUT; return result with output and exit code."""
        cmd = 'ectool ' + self._CROS_FP_ARG + ' ' + command
        result = self.run_cmd(cmd)
        return result

    def _run_cros_config_cmd(self, command):
        """Runs cros_config on DUT; return result with output and exit code."""
        cmd = 'cros_config ' + self._CROS_CONFIG_FINGERPRINT_PATH + ' ' \
              + command
        result = self.run_cmd(cmd)
        return result

    def _run_cros_config_cmd_cat(self, command):
        """Runs cat /run/chromeos-config/v1 on DUT; return result."""
        cmd = "cat /run/chromeos-config/v1/{}".format(command)
        return self.run_cmd(cmd)

    def _run_dump_fmap_cmd(self, fw_file, section):
        """
        Runs "dump_fmap" on DUT for given file.
        Returns value of given section.
        """
        # Write result to stderr while redirecting stderr to stdout
        # and dropping stdout. This is done because dump_map only writes the
        # value read from a section to a file (will not just print it to
        # stdout).
        cmd = 'dump_fmap -x ' + fw_file + ' ' + section +\
              ':/dev/stderr /dev/stderr >& /dev/stdout > /dev/null'
        result = self.run_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Failed to read section: %s' % section)
        return result.stdout.rstrip('\0')

    def _run_futility_show_cmd(self, fw_file):
        """
        Runs "futility show" on DUT for given file.
        Returns stdout on success.
        """
        futility_cmd = 'futility show ' + fw_file
        result = self.run_cmd(futility_cmd)
        if result.exit_status != 0:
            raise error.TestFail('Unable to run futility on device')
        return result.stdout

    def _run_sha256sum_cmd(self, file_name):
        """
        Runs "sha256sum" on DUT for given file.
        Returns stdout on success.
        """
        sha_cmd = 'sha256sum ' + file_name
        result = self.run_cmd(sha_cmd)
        if result.exit_status != 0:
            raise error.TestFail('Unable to calculate sha256sum on device')
        return result

    def run_test(self, test_name, *args):
        """Runs test on DUT."""
        logging.info('Running %s', test_name)
        # Redirecting stderr to stdout since some commands intentionally fail
        # and it's easier to read when everything ordered in the same output
        test_cmd = ' '.join([os.path.join(self._dut_working_dir, test_name)] +
                            list(args) + ['2>&1'])
        # Change the working dir so we can write files from within the test
        # (otherwise defaults to $HOME (/root), which is not usually writable)
        # Note that dut_working_dir is automatically cleaned up so tests don't
        # need to worry about files from previous invocations or other tests.
        test_cmd = '(cd ' + self._dut_working_dir + ' && ' + test_cmd + ')'
        logging.info('Test command: %s', test_cmd)
        result = self.run_cmd(test_cmd)
        if result.exit_status != 0:
            raise error.TestFail(test_name + ' failed')
