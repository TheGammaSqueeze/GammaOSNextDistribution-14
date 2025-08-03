#!/usr/bin/env python
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import copy
import json
import time
import logging
import shutil

import common

from autotest_lib.server.cros.device_health_profile.profile_constants import *


class DeviceHealthProfileError(Exception):
    """
    Generic Exception for failures from DeviceHealthProfile object.
    """


class InvalidDeviceHealthProfileKeyError(DeviceHealthProfileError):
    """
    Exception to throw when trying to get an invalid health profile key.
    """


class DeviceHealthProfile(object):
    """This class provide interfaces to access device health profile
    that cached on profile host(usually labstation).
    """

    def __init__(self, hostname, host_info=None, result_dir=None):
        """Initialize the class.

        @param hostname:    The device hostaname or identification.
        @param host_info:   A HostInfo object of the device of the profile.
        @param result_dir:  A result directory where we can keep local copy of
                            device profile.
        """
        self._hostname = hostname
        # Cache host-info data
        self._device_board = host_info.board if host_info else ''
        self._device_model = host_info.model if host_info else ''
        # the profile is located on servo-host as temporally location.
        # The servo-host will be provided later
        self._profile_host = None
        # The flag will be set when we set _profile_host.
        # For servod container setups we keep device prfile on drone instead
        # the servo-host.
        self._is_containerized_servod = False
        self._health_profile = None

        # Construct remote and local file path.
        self._filename = self._hostname + '.profile'
        self._remote_path = os.path.join(PROFILE_FILE_DIR, self._filename)
        result_dir = result_dir or '/tmp'
        self._local_path = os.path.join(result_dir, self._filename)

    def init_profile(self, profile_host):
        """Initialize device health profile data.

        If the cached file exists on profile host the method will download
        file to a local path and read data, otherwise create a profile data
        from template.

        @param profile_host: An ServoHost object, where is the location
                             we store device health for device.
        """
        if not profile_host:
            raise DeviceHealthProfileError('The profile host is not provided.')
        self._profile_host = profile_host
        # When we work with containeried servod we do not have access to the
        # remote host and keep profiles in local volume on the drone.
        if self._profile_host.is_containerized_servod():
            self._is_containerized_servod = True
            # Set path to volume on the drone where we keep all profiles.
            self._remote_path = os.path.join(PROFILE_DIR_CONTAINER,
                                             self._filename)
        else:
            # Do a lightweighted check to make sure the machine is up
            # (by ping), as we don't waste time on unreachable DUT.
            if not self._profile_host.check_cached_up_status():
                raise DeviceHealthProfileError(
                        'The profile host %s is not reachable via ping.' %
                        self._profile_host.hostname)

            # We also want try to check if the DUT is available for ssh.
            if not self._profile_host.is_up():
                raise DeviceHealthProfileError(
                        'The profile host %s is pingable but not sshable.' %
                        self._profile_host.hostname)

        if not self._sync_existing_profile():
            self._create_profile_from_template()

    def is_loaded(self):
        """Check if device profile was loaded on not."""
        return self._health_profile is not None

    def _sync_existing_profile(self):
        """Sync health profile from remote profile host(servohost) and
        validate profile data is not corrupted or outdated.

        @returns True if sync and validate succeed otherwise False.
        """
        if self._is_containerized_servod:
            self._copy_from_local()
        else:
            if not self._profile_host.is_file_exists(self._remote_path):
                logging.debug('%s not exists on %s.', self._remote_path,
                              self._profile_host.hostname)
                return False
            self._download_profile()

        self._read_profile()
        return self._validate_profile_data(self._health_profile)

    def _download_profile(self):
        """Copy profile file from remote profile host to local path.
        """
        logging.debug('Downloading profile file from %s:%s to local path: %s',
                      self._profile_host.hostname,
                      self._remote_path,
                      self._local_path)
        self._profile_host.get_file(source=self._remote_path,
                                    dest=self._local_path)

    def _upload_profile(self):
        """Copy profile file from local path to remote profile host.
        """
        # Make sure the device health profile directory exists on profile host.
        self._profile_host.run('mkdir -p %s' % PROFILE_FILE_DIR,
                               ignore_status=True)

        logging.debug('Uploading profile from local path: %s to remote %s:%s',
                      self._local_path,
                      self._profile_host.hostname,
                      self._remote_path)
        self._profile_host.send_file(source=self._local_path,
                                     dest=self._remote_path)

    def _copy_from_local(self):
        """Copy profile from local volume to result directory.

        For Satlab all device profiles saved in special volume on the drone.
        """
        if os.path.exists(self._remote_path):
            logging.info('Copying profile file from %s to local path: %s',
                         self._remote_path, self._local_path)
            shutil.copyfile(self._remote_path, self._local_path)
        else:
            logging.info(
                    'Skipping copy from remote path %s as file is not exist.',
                    self._remote_path)

    def _copy_to_local(self):
        """Copy profile file from result directory to local volume.

        For Satlab all device profiles saved in special volume on the drone.
        """
        logging.info('Copying profile file from %s to remote path: %s',
                     self._local_path, self._remote_path)
        shutil.copyfile(self._local_path, self._remote_path)

    def _read_profile(self):
        """Read profile data from local path and convert it into json format.
        """
        if not os.path.exists(self._local_path):
            logging.info('Skipping reading as local file: %s is not exist.',
                         self._local_path)
            return
        logging.debug('Reading device health profile from: %s',
                      self._local_path)
        with open(self._local_path, 'r') as f:
            try:
                self._health_profile = json.load(f)
            except Exception as e:
                logging.warning('Could not decode %s to json format, the file'
                                ' may be corrupted; %s',
                                self._local_path, str(e))

    def _dump_profile(self):
        """Dump profile data into local file.
        """
        logging.debug('Dumping device health profile to: %s', self._local_path)
        with open(self._local_path, 'w') as f:
            json.dump(self._health_profile, f)

    def _create_profile_from_template(self):
        """Create a new health profile dict from template.
        """
        logging.info('Creating new health profile from template for %s.',
                     self._hostname)
        self._health_profile = copy.deepcopy(DEVICE_HEALTH_PROFILE_TEMPLATE)
        if self._device_board or self._device_model:
            self._set_board(self._device_board)
            self._set_model(self._device_model)
        self.refresh_update_time()

    def _validate_profile_data(self, data):
        """Validate the given profile data is in good state.
        """
        logging.debug('Validating health profile data.')
        if not isinstance(data, dict):
            logging.debug('Non-dict type detected, the profile data'
                          ' may be corrupted.')
            return False

        # Validate that cached health profile version is not outdated.
        input_version = data.get(PROFILE_VERSION_KEY)
        if input_version != PROFILE_VERSION:
            logging.info('The input profile version: %s is outdated,'
                         ' expected version: %s', input_version,
                         PROFILE_VERSION)
            return False

        # Validate that cached board/model is match with device, in case
        # there is was decom/redeploy.
        cached_board = data.get(BOARD_KEY)
        cached_model = data.get(MODEL_KEY)
        if (self._device_board and cached_board
                    and (self._device_board != cached_board)):
            logging.info(
                    'The board: %s from host_info does not match board: %s'
                    ' from cached profile, the device hardware probably has'
                    ' been changed.', self._device_board, cached_board)
            return False
        if (self._device_model and cached_model
                    and (self._device_model != cached_model)):
            logging.info(
                    'The model: %s from host_info does not match model: %s'
                    ' from cached profile, the device hardware probably has'
                    ' been changed.', self._device_model, cached_model)
            return False
        return True

    def _is_validate_profile_key(self, key):
        return key in DEVICE_HEALTH_PROFILE_TEMPLATE

    def _update_profile(self, key, value):
        if not self._is_validate_profile_key(key):
            logging.info('%s is an invalid health profile key.', key)
            return
        logging.debug('Updating health profile key %s to %s', key, value)
        self._health_profile[key] = value

    def _get_value(self, key):
        """The basic interface to get a value from health profile dictionary.

        @raises InvalidDeviceHealthProfileKeyError if the input key is
                not a valid device health profile key.
        """
        if not self._is_validate_profile_key(key):
            raise InvalidDeviceHealthProfileKeyError(
                '%s is not a valid device health profile key' % key)
        return self._health_profile.get(key)

    def _set_board(self, board):
        # pylint: disable=missing-docstring
        self._update_profile(BOARD_KEY, board)

    def _set_model(self, model):
        # pylint: disable=missing-docstring
        self._update_profile(MODEL_KEY, model)

    @property
    def health_profile(self):
        # pylint: disable=missing-docstring
        return self._health_profile

    def get_board(self):
        """Get device board from cached device health profile.
        """
        return self._get_value(BOARD_KEY)

    def get_model(self):
        """Get device model from cached device health profile.
        """
        return self._get_value(MODEL_KEY)

    def get_profile_version(self):
        """Get the version of cached device health profile.
        """
        return self._get_value(PROFILE_VERSION_KEY)

    def get_dut_state(self):
        """Get most recent dut state from device health profile.
        """
        return self._get_value(DUT_STATE_KEY)

    def get_servo_state(self):
        """Get most recent servo state from device health profile.
        """
        return self._get_value(SERVO_STATE_KEY)

    def get_cros_stable_version(self):
        """Get the most recent used cros image during repair.
        """
        return self._get_value(CROS_STABLE_VERSION_KEY)

    def get_firmware_stable_version(self):
        """Get the most recent used firmware image during repair, we only
        expect to see this on non-faft pool device.
        """
        return self._get_value(FIRMWARE_STABLE_VERSION_KEY)

    def get_last_update_time(self):
        """Get the timestamp of when device health profile file received
        the most recent updates. Example "2020-01-01 15:05:05"
        """
        return self._get_value(LAST_UPDATE_TIME_KEY)

    def get_last_update_time_epoch(self):
        """Get the unix time in int of when device health profile file
        received the most recent updates.
        """
        return int(time.mktime(time.strptime(
            self.get_last_update_time(), TIME_PATTERN)))

    def get_enter_current_state_time(self):
        """Get the timestamp of when DUT enter current state.
        Example "2020-01-01 15:05:05"
        """
        return self._get_value(TIME_ENTER_CURRENT_STATE_KEY)

    def get_enter_current_state_time_epoch(self):
        """Get the unix time in int of when DUT enter current state.
        """
        return int(time.mktime(time.strptime(
            self.get_enter_current_state_time(), TIME_PATTERN)))

    def get_repair_fail_count(self):
        """Get repair fail count since enter current state.
        """
        return self._get_value(REPAIR_FAIL_COUNT_KEY)

    def get_provision_fail_count(self):
        """Get provision fail count since enter current state.
        """
        return self._get_value(PROVISION_FAIL_COUNT_KEY)

    def get_failed_verifiers(self):
        """Get all failed verifiers.

        @returns a dict represents all failed verifiers and
                 their fail count.
        """
        return self._get_value(FAILED_VERIFIERS_KEY)

    def get_failed_verifier(self, tag):
        """Get fail count of a specific verifier.

        @param tag: the short identifier of the verifier.

        @returns the fail count of the specified verifier.
        """
        return self.get_failed_verifiers().get(tag, 0)

    def get_succeed_repair_actions(self):
        """Get all repair actions that has been applied and succeed.

        @returns a dict represents all succeed repair actions
                 and their success count.
        """
        return self._get_value(SUCCEED_REPAIR_ACTIONS_KEY)

    def get_succeed_repair_action(self, tag):
        """Get success count of a specific repair action.

        @param tag: the short identifier of the repair action.

        @returns the success count of the specified repair action.
        """
        return self.get_succeed_repair_actions().get(tag, 0)

    def get_failed_repair_actions(self):
        """Get all repair actions that has been applied and failed.

        @returns a dict represents all failed repair actions
                 and their fail count.
        """
        return self._get_value(FAILED_REPAIR_ACTIONS_KEY)

    def get_failed_repair_action(self, tag):
        """Get fail count of a specific repair action.

        @param tag: the short identifier of the repair action.

        @returns the failed count of the specified repair action.
        """
        return self.get_failed_repair_actions().get(tag, 0)

    def get_badblocks_ro_run_time(self):
        """Get the timestamp of when run last read-only badblocks check
        on the device. Example "2020-01-01 15:05:05"
        """
        last_time = self._get_value(LAST_BADBLOCKS_RO_RUN_TIME_KEY)
        return last_time or DEFAULT_TIMESTAMP

    def get_badblocks_ro_run_time_epoch(self):
        """Get the unix time of when run last read-only badblocks check
        on the device."
        """
        last_time = self.get_badblocks_ro_run_time()
        return int(time.mktime(time.strptime(last_time, TIME_PATTERN)))

    def get_badblocks_rw_run_time(self):
        """Get the timestamp of when run last read-write badblocks check
        on the device. Example "2020-01-01 15:05:05"
        """
        last_time = self._get_value(LAST_BADBLOCKS_RW_RUN_TIME_KEY)
        return last_time or DEFAULT_TIMESTAMP

    def get_badblocks_rw_run_time_epoch(self):
        """Get the unix time of when run last read-write badblocks check
        on the device."
        """
        last_time = self.get_badblocks_rw_run_time()
        return int(time.mktime(time.strptime(last_time, TIME_PATTERN)))

    def get_servo_micro_fw_update_time(self):
        """Get the timestamp of when run last fw update for servo_micro.
        Example "2020-01-01 15:05:05"
        """
        last_time = self._get_value(LAST_SERVO_MICRO_FW_UPDATE_RUN_TIME_KEY)
        return last_time or DEFAULT_TIMESTAMP

    def get_servo_micro_fw_update_time_epoch(self):
        """Get the unix time of when run last fw update for servo_micro.
        """
        last_time = self.get_servo_micro_fw_update_time()
        return int(time.mktime(time.strptime(last_time, TIME_PATTERN)))

    def set_cros_stable_version(self, build):
        """Set the most recent used cros image during repair.
        """
        self._update_profile(CROS_STABLE_VERSION_KEY, build)

    def set_firmware_stable_version(self, build):
        """Set the most recent used firmware image during repair, we only
        expect to see this on non-faft pool device.
        """
        self._update_profile(FIRMWARE_STABLE_VERSION_KEY, build)

    def refresh_badblocks_ro_run_time(self):
        """Get the timestamp of when run last read-only badblocks check
        on the device.
        """
        return self._update_profile(
                LAST_BADBLOCKS_RO_RUN_TIME_KEY,
                time.strftime(TIME_PATTERN, time.localtime()))

    def refresh_badblocks_rw_run_time(self):
        """Get the timestamp of when run last read-write badblocks check
        on the device.
        """
        return self._update_profile(
                LAST_BADBLOCKS_RW_RUN_TIME_KEY,
                time.strftime(TIME_PATTERN, time.localtime()))

    def refresh_servo_miro_fw_update_run_time(self):
        """Get the timestamp of when run last fw update for servo_micro.
        """
        return self._update_profile(
                LAST_SERVO_MICRO_FW_UPDATE_RUN_TIME_KEY,
                time.strftime(TIME_PATTERN, time.localtime()))

    def refresh_update_time(self):
        """Update last_update_time to current timestamp in UTC.
        """
        self._update_profile(LAST_UPDATE_TIME_KEY,
                             time.strftime(TIME_PATTERN, time.localtime()))

    def increase_repair_fail_count(self):
        # pylint: disable=missing-docstring
        self._update_profile(REPAIR_FAIL_COUNT_KEY,
                             self.get_repair_fail_count() + 1)

    def increase_provision_fail_count(self):
        # pylint: disable=missing-docstring
        self._update_profile(PROVISION_FAIL_COUNT_KEY,
                             self.get_provision_fail_count() + 1)

    def insert_failed_verifier(self, tag):
        """Increase fail count for a specific verifier by 1.
        """
        verifiers = self.get_failed_verifiers()
        if tag not in verifiers:
            verifiers[tag] = 0
        verifiers[tag] += 1
        self._update_profile(FAILED_VERIFIERS_KEY, verifiers)

    def insert_succeed_repair_action(self, tag):
        """Increase succeed count for a specific repair action by 1.
        """
        actions = self.get_succeed_repair_actions()
        if tag not in actions:
            actions[tag] = 0
        actions[tag] += 1
        self._update_profile(SUCCEED_REPAIR_ACTIONS_KEY, actions)

    def insert_failed_repair_action(self, tag):
        """Increase fail count for a specific repair action by 1.
        """
        actions = self.get_failed_repair_actions()
        if tag not in actions:
            actions[tag] = 0
        actions[tag] += 1
        self._update_profile(FAILED_REPAIR_ACTIONS_KEY, actions)

    def update_dut_state(self, state, reset_counters=False):
        """Update state of the device, this will also reset all fail counts.

        @param state: the new dut state to update.
        @param reset_counts: a boolean to indicate whether we want to reset
                             all counters.
        """
        if state == self.get_dut_state():
            logging.debug('The host is already in %s state.', state)
            if state == DUT_STATE_REPAIR_FAILED:
                self.increase_repair_fail_count()
            return
        # Reset some records when dut state changes.
        if reset_counters:
            self._update_profile(REPAIR_FAIL_COUNT_KEY, 0)
            self._update_profile(PROVISION_FAIL_COUNT_KEY, 0)
            self._update_profile(FAILED_VERIFIERS_KEY, {})
            self._update_profile(SUCCEED_REPAIR_ACTIONS_KEY, {})
            self._update_profile(FAILED_REPAIR_ACTIONS_KEY, {})
        self._update_profile(TIME_ENTER_CURRENT_STATE_KEY,
                             time.strftime(TIME_PATTERN, time.localtime()))
        self._update_profile(DUT_STATE_KEY, state)

    def update_servo_state(self, state):
        # pylint: disable=missing-docstring
        if state == self.get_servo_state():
            logging.debug('The servo is already in %s state.', state)
            return
        self._update_profile(SERVO_STATE_KEY, state)

    def close(self):
        # pylint: disable=missing-docstring
        self.refresh_update_time()
        self._dump_profile()
        if self._is_containerized_servod:
            self._copy_to_local()
        else:
            self._upload_profile()
