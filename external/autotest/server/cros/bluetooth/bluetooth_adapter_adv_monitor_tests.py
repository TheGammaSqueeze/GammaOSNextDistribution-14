# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Server side bluetooth tests on Advertisement Monitor API"""

import time
import logging
import array

from autotest_lib.client.bin import utils
from autotest_lib.server.cros.bluetooth import bluetooth_adapter_tests
from autotest_lib.client.common_lib import error


# List of the controllers that does not support Adv Monitor HW offloading.
ADVMON_UNSUPPORTED_CHIPSETS = [
        'BCM-43540', 'BCM-43560',
        'Intel-AC7260', 'Intel-AC7265',
        'MVL-8797', 'MVL-8887', 'MVL-8897', 'MVL-8997',
        'Realtek-RTL8822C-USB', 'Realtek-RTL8822C-UART', 'Realtek-RTL8852A-USB',
        'QCA-6174A-3-UART', 'QCA-6174A-5-USB'
]


class TestMonitor():
    """Local object hosting the test values for Advertisement Monitor object.

    This class holds the values of parameters for creating an Advertisement
    Monitor object.

    """

    # Index of the pattern data in the patterns filter.
    PATTERN_DATA_IDX = 2

    def __init__(self, app_id):
        """Construction of a local monitor object.

        @param app_id: the app id associated with the monitor.

        """
        self.type = None
        self.rssi = []
        self.sampling_period = 256  # unset Sampling Period
        self.patterns = []
        self.monitor_id = None
        self.app_id = app_id


    def _bytes(self, str_data):
        """Convert string data to byte array.

        @param str_data: the string data.

        @returns: the byte array.

        """
        return [b for b in array.array('B', str_data.encode())]


    def update_type(self, monitor_type):
        """Update the monitor type.

        @param monitor_type: type of the monitor.

        """
        self.type = monitor_type


    def update_rssi(self, monitor_rssi):
        """Update the RSSI filter values.

        @param rssi: the list of rssi threshold and timeout values.

        """
        self.rssi = monitor_rssi


    def update_sampling_period(self, monitor_sampling_period):
        """Update the sampling period value.

        @param monitor_sampling_period: sampling period value.

        """
        self.sampling_period = monitor_sampling_period


    def update_patterns(self, monitor_patterns):
        """Update the content filter patterns.

        @param patterns: the list of start position, ad type and patterns.

        """
        # Convert string patterns to byte array, if any.
        for pattern in monitor_patterns:
            if isinstance(pattern[self.PATTERN_DATA_IDX], str):
                pattern[self.PATTERN_DATA_IDX] = self._bytes(
                        pattern[self.PATTERN_DATA_IDX])

        self.patterns = monitor_patterns


    def update_monitor_id(self, monitor_id):
        """Store the monitor id returned by add_monitor().

        @param monitor_id: the monitor id.

        """
        self.monitor_id = monitor_id


    def get_monitor_data(self):
        """Return the monitor parameters.

        @returns: List containing the monitor data.

        """
        return [self.type, self.rssi + [self.sampling_period], self.patterns]


    def get_monitor_id(self):
        """Return the monitor id.

        @returns: monitor id if monitor is already added, None otherwise.

        """
        return self.monitor_id


    def get_app_id(self):
        """Return the application id.

        @returns: app id associated to the monitor object.

        """
        return self.app_id


class BluetoothAdapterAdvMonitorTests(
        bluetooth_adapter_tests.BluetoothAdapterTests):
    """Server side bluetooth adapter advertising Test.

    This class comprises a number of test cases to verify bluetooth
    Advertisement Monitor API.

    Refer to the test plan doc for more details: go/bt-advmon-api-test-plan

    """

    ADD_MONITOR_POLLING_TIMEOUT_SECS = 3
    ADD_MONITOR_POLLING_SLEEP_SECS = 1
    PAIR_TEST_SLEEP_SECS = 5

    # Refer doc/advertisement-monitor-api.txt for more info about unset values.
    UNSET_RSSI = 127
    UNSET_TIMEOUT = 0
    UNSET_SAMPLING_PERIOD = 256

    # Non-zero count value is used to indicate the case where multiple
    # DeviceFound/DeviceLost events are expected to occur.
    MULTIPLE_EVENTS = -1

    # Number of cycle to observe during a test
    INTERLEAVE_SCAN_TEST_CYCLE = 10
    # Acceptable extra delay of interleave scan duration, in sec
    INTERLEAVE_SCAN_DURATION_TOLERANCE = 0.1
    # Acceptable delay of cancelling interleave scan, in sec
    INTERLEAVE_SCAN_CANCEL_TOLERANCE = 2
    # Acceptable extra/missing cycles in interleave scan
    INTERLEAVE_SCAN_CYCLE_NUM_TOLERANCE = 2

    # Duration of kernel perform 'start discovery', in sec
    DISCOVERY_DURATION = 10.24

    # Acceptable difference between the first RSSI sample and following one.
    # LOW_RSSI_THRESHOLD_TOLERANCE must be larger than
    # HIGH_RSSI_THRESHOLD_TOLERANCE
    HIGH_RSSI_THRESHOLD_TOLERANCE = 20
    LOW_RSSI_THRESHOLD_TOLERANCE = 40

    test_case_log = bluetooth_adapter_tests.test_case_log
    test_retry_and_log = bluetooth_adapter_tests.test_retry_and_log


    def advmon_check_manager_interface_exist(self):
        """Check if AdvertisementMonitorManager1 interface is available.

        @returns: True if Manager interface is available, False otherwise.

        """
        return self.bluetooth_facade.advmon_check_manager_interface_exist()


    def read_supported_types(self):
        """Read the Advertisement Monitor supported monitor types.

        @returns: List of supported advertisement monitor types.

        """
        return self.bluetooth_facade.advmon_read_supported_types()


    def read_supported_features(self):
        """Read the Advertisement Monitor supported features.

        @returns: List of supported advertisement monitor features.

        """
        return self.bluetooth_facade.advmon_read_supported_features()


    def create_app(self):
        """Create an advertisement monitor app.

        @returns: app id, once the app is created.

        """
        return self.bluetooth_facade.advmon_create_app()


    def exit_app(self, app_id):
        """Exit an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_exit_app(app_id)


    def kill_app(self, app_id):
        """Kill an advertisement monitor app by sending SIGKILL.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_kill_app(app_id)


    def register_app(self, app_id):
        """Register an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_register_app(app_id)


    def unregister_app(self, app_id):
        """Unregister an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_unregister_app(app_id)


    def add_monitor(self, app_id, monitor_data):
        """Create an Advertisement Monitor object.

        @param app_id: the app id.
        @param monitor_data: the list containing monitor type, RSSI filter
                             values and patterns.

        @returns: monitor id, once the monitor is created, None otherwise.

        """
        return self.bluetooth_facade.advmon_add_monitor(app_id, monitor_data)


    def remove_monitor(self, app_id, monitor_id):
        """Remove the Advertisement Monitor object.

        @param app_id: the app id.
        @param monitor_id: the monitor id.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_remove_monitor(app_id, monitor_id)


    def get_event_count(self, app_id, monitor_id, event='All'):
        """Read the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: count of the specific event or dict of counts of all events.

        """
        return self.bluetooth_facade.advmon_get_event_count(app_id,
                                                            monitor_id,
                                                            event)


    def reset_event_count(self, app_id, monitor_id, event='All'):
        """Reset the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_reset_event_count(app_id,
                                                              monitor_id,
                                                              event)

    def set_target_devices(self, app_id, monitor_id, devices):
        """Set the target devices to the given monitor.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param devices: a list of devices in MAC address

        @returns: True on success, False otherwise.

        """
        return self.bluetooth_facade.advmon_set_target_devices(
                app_id, monitor_id, devices)

    def interleave_logger_start(self):
        """ Start interleave logger recording
        """
        self.bluetooth_facade.advmon_interleave_scan_logger_start()

    def interleave_logger_stop(self):
        """ Stop interleave logger recording

        @returns: True if logs were successfully collected,
                  False otherwise.

        """
        return self.bluetooth_facade.advmon_interleave_scan_logger_stop()

    def interleave_logger_get_records(self):
        """ Get records in previous log collections

        @returns: a list of records, where each item is a record of
                  interleave |state| and the |time| the state starts.
                  |state| could be {'no filter', 'allowlist'}
                  |time| is system time in sec

        """
        return self.bluetooth_facade.\
                                advmon_interleave_scan_logger_get_records()

    def interleave_logger_get_cancel_event(self):
        """ Get cancel event in previous log collections

        @returns: the first cancel event in the collections,
                  None if no cancel event was found

        """
        events = self.bluetooth_facade.\
                            advmon_interleave_scan_logger_get_cancel_events()
        if len(events) == 0:
            event = None
        else:
            event = events[0]
            if len(events) > 1:
                logging.warning('More than one cancel events found %s', events)
        return event

    def interleave_scan_get_durations(self):
        """Get durations of allowlist scan and no filter scan

        @returns: a dict of {'allowlist': allowlist_duration,
                             'no filter': no_filter_duration},
                  or None if something went wrong
        """
        return self.bluetooth_facade.advmon_interleave_scan_get_durations()

    @test_retry_and_log(False)
    def test_supported_types(self):
        """Test supported monitor types.

        @returns: True on success, False otherwise.

        """
        supported_types = self.read_supported_types()
        for supported_type in supported_types:
            logging.info('type: %s', supported_type)

        # TODO(b/169658213) - add check for supported types.
        return True


    @test_retry_and_log(False)
    def test_supported_features(self):
        """Test supported features.

        @returns: True on success, False otherwise.

        """
        supported_features = self.read_supported_features()
        for supported_feature in supported_features:
            logging.info('feature: %s', supported_feature)

        # TODO(b/169658213) - add check for supported features.
        return True


    def test_is_controller_offloading_supported(self):
        """Check if the controller supports HW offloading.

        @raises: TestFail if the controller is expected to support Monitor
                 Offloading but the support is missing.

        """
        chipset = self.bluetooth_facade.get_chipset_name()
        if chipset in ADVMON_UNSUPPORTED_CHIPSETS:
            logging.warning('Controller support check skipped for %s', chipset)
        else:
            supported_features = self.read_supported_features()
            if not supported_features:
                logging.error('Controller support missing on %s', chipset)
                raise error.TestFail('Controller offloading not supported')
            logging.info('Controller offloading supported on %s', chipset)


    def test_is_adv_monitoring_supported(self):
        """Check if Adv Monitor API is supported.

            If AdvMonitor API is not supported by the platform,
            AdvertisementMonitorManager1 interface won't be exposed by
            bluetoothd. In such case, skip the test and raise TestNA.

            @raises: TestNA if Adv Monitor API is not supported.

        """
        if not self.advmon_check_manager_interface_exist():
            logging.info('Advertisement Monitor API not supported')
            raise error.TestNAError('Advertisement Monitor API not supported')

        self.test_is_controller_offloading_supported()


    @test_retry_and_log(False)
    def test_exit_app(self, app_id):
        """Test exit application.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.exit_app(app_id)


    @test_retry_and_log(False)
    def test_kill_app(self, app_id):
        """Test kill application.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.kill_app(app_id)


    @test_retry_and_log(False)
    def test_register_app(self, app_id, expected=True):
        """Test register application.

        @param app_id: the app id.
        @param expected: expected result of the RegisterMonitor method.

        @returns: True on success, False otherwise.

        """
        return self.register_app(app_id) == expected


    @test_retry_and_log(False)
    def test_unregister_app(self, app_id, expected=True):
        """Test unregister application.

        @param app_id: the app id.
        @param expected: expected result of the UnregisterMonitor method.

        @returns: True on success, False otherwise.

        """
        return self.unregister_app(app_id) == expected


    @test_retry_and_log(False)
    def test_monitor_activate(self, monitor, expected):
        """Test if the Activate method on the monitor has been invoked or not.

        @param monitor: the local monitor object.
        @param expected: expected state of the Activate event.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = monitor.get_monitor_id()
        if monitor_id is None:
            return False

        def _check_activate():
            """Handler for the activate event."""
            return self.get_event_count(app_id, monitor_id, 'Activate') == 1

        activated = False
        try:
            utils.poll_for_condition(
                    condition=_check_activate,
                    timeout=self.ADD_MONITOR_POLLING_TIMEOUT_SECS,
                    sleep_interval=self.ADD_MONITOR_POLLING_SLEEP_SECS,
                    desc='Waiting for activate')
            activated = True
        except utils.TimeoutError as e:
            logging.error('activate: %s', e)
        except:
            logging.error('activate: unexpected error')

        return expected == activated


    @test_retry_and_log(False)
    def test_monitor_release(self, monitor, expected):
        """Test if the Release method on the monitor has been invoked or not.

        @param monitor: the local monitor object.
        @param expected: expected state of the Release event.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = monitor.get_monitor_id()
        if monitor_id is None:
            return False

        def _check_release():
            """Handler for the release event."""
            return self.get_event_count(app_id, monitor_id, 'Release') == 1

        released = False
        try:
            utils.poll_for_condition(
                    condition=_check_release,
                    timeout=self.ADD_MONITOR_POLLING_TIMEOUT_SECS,
                    sleep_interval=self.ADD_MONITOR_POLLING_SLEEP_SECS,
                    desc='Waiting for release')
            released = True
        except utils.TimeoutError as e:
            logging.error('release: %s', e)
        except Exception as e:
            logging.error('release: %s', e)
        except:
            logging.error('release: unexpected error')

        return expected == released


    @test_retry_and_log(True)
    def test_device_found(self, monitor, count, delay=0):
        """Test if the DeviceFound method on a monitor has been invoked or not.

        @param monitor: the local monitor object.
        @param count: expected count of the DeviceFound events.
        @param delay: wait until 'delay' seconds before reading the event count.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = monitor.get_monitor_id()
        if monitor_id is None:
            return False

        if delay:
            time.sleep(delay)

        checked_count = self.get_event_count(app_id, monitor_id, 'DeviceFound')

        if count == self.MULTIPLE_EVENTS:
            self.results = {
                    'Found events': checked_count,
                    'Expected events': 'multiple'
            }

            return checked_count > 0

        self.results = {
                'Found events': checked_count,
                'Expected events': count
        }

        return checked_count == count


    @test_retry_and_log(False)
    def test_device_lost(self, monitor, count, delay=0):
        """Test if the DeviceLost method on a monitor has been invoked or not.

        @param monitor: the local monitor object.
        @param count: expected count of the DeviceLost events.
        @param delay: wait until 'delay' seconds before reading the event count.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = monitor.get_monitor_id()
        if monitor_id is None:
            return False

        if delay:
            time.sleep(delay)

        checked_count = self.get_event_count(app_id, monitor_id, 'DeviceLost')

        if count == self.MULTIPLE_EVENTS:
            self.results = {
                    'Found events': checked_count,
                    'Expected events': 'multiple'
            }

            return checked_count > 1

        self.results = {
                'Found events': checked_count,
                'Expected events': count
        }

        return checked_count == count


    @test_retry_and_log(False)
    def test_reset_event_count(self, monitor, event='All'):
        """Test resetting count of a particular event on the given monitor.

        @param monitor: the local monitor object.
        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        return self.reset_event_count(monitor.get_app_id(),
                                      monitor.get_monitor_id(),
                                      event)


    @test_retry_and_log(False)
    def test_add_monitor(self, monitor, expected_activate=None,
                         expected_release=None):
        """Test adding a monitor.

        @param monitor: the local monitor object.
        @param expected_activate: expected state of the Activate event.
        @param expected_release: expected state of the Release event.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = self.add_monitor(app_id, monitor.get_monitor_data())
        if monitor_id is None:
            return False
        monitor.update_monitor_id(monitor_id)

        checked_activate = True
        if expected_activate is not None:
            checked_activate = self.test_monitor_activate(
                    monitor, expected_activate)

        checked_release = True
        if expected_release is not None:
            checked_release = self.test_monitor_release(
                    monitor, expected_release)

        if self.get_event_count(app_id, monitor_id, 'Release') != 0:
            self.remove_monitor(app_id, monitor_id)
            monitor.update_monitor_id(None)

        # Set the target devices so that AdvMon ignores Adv from other devices
        target_devices = []

        if hasattr(self, 'peer_mouse'):
            target_devices.append(self.peer_mouse.address)

        if hasattr(self, 'peer_keybd'):
            target_devices.append(self.peer_keybd.address)

        self.set_target_devices(app_id, monitor_id, target_devices)

        self.results = {
                'activated': checked_activate,
                'released': checked_release
        }
        return all(self.results.values())


    @test_retry_and_log(False)
    def test_remove_monitor(self, monitor):
        """Test removing a monitor.

        @param monitor: the local monitor object.

        @returns: True on success, False otherwise.

        """
        app_id = monitor.get_app_id()
        monitor_id = monitor.get_monitor_id()
        if monitor_id is None:
            return False

        ret = self.remove_monitor(app_id, monitor_id)
        monitor.update_monitor_id(None)

        if ret is None:
            return False

        return True


    @test_retry_and_log(False)
    def test_setup_peer_devices(self):
        """Test availability of the peer devices.

        @returns: True on success, False otherwise.

        """
        self.peer_keybd = None
        self.peer_mouse = None

        self.LOW_RSSI = None
        self.HIGH_RSSI = None

        for device_type, device_list in self.devices.items():
            for device in device_list:
                if device_type is 'BLE_KEYBOARD':
                    self.peer_keybd = device
                elif device_type is 'BLE_MOUSE':
                    self.peer_mouse = device

        if self.peer_keybd is None or self.peer_mouse is None:
            raise error.TestNAError('some peer device is not found')

        # Setup default RSSI threshold based on real RSSI range
        keybd_rssi = self.get_device_sample_rssi(self.peer_keybd)
        mouse_rssi = self.get_device_sample_rssi(self.peer_mouse)

        if mouse_rssi is None or keybd_rssi is None:
            raise error.TestNAError('failed to examine peer RSSI')

        min_rssi = min(mouse_rssi, keybd_rssi)

        # Make RSSI threshold tolerable.
        self.HIGH_RSSI = max(min_rssi - self.HIGH_RSSI_THRESHOLD_TOLERANCE,
                             -126)
        self.LOW_RSSI = max(min_rssi - self.LOW_RSSI_THRESHOLD_TOLERANCE, -127)

        self.test_stop_peer_device_adv(self.peer_keybd)
        self.test_stop_peer_device_adv(self.peer_mouse)

        return True


    @test_retry_and_log(False)
    def test_start_peer_device_adv(self, device, duration=0):
        """Test enabling the peer device advertisements.

        @param device: the device object.
        @param duration: the duration of the advertisement.

        @returns: True on success, False otherwise.

        """
        ret = self.test_device_set_discoverable(device, True)

        if duration:
            time.sleep(duration)

        return ret


    @test_retry_and_log(False)
    def test_stop_peer_device_adv(self, device, duration=0):
        """Test disabling the peer device advertisements.

        @param device: the device object.
        @param duration: the duration of the advertisement disable.

        @returns: True on success, False otherwise.

        """
        ret = self.test_device_set_discoverable(device, False)

        if duration:
            time.sleep(duration)

        return ret

    def check_records_interleaving(self, durations, records, expect_cycles):
        """ Check the state of records is interleaving and also the duration is
            as expected.

        @param durations: a dict of {'allowlist': allowlist_duration,
                                     'no filter': no_filter_duration}
        @param records: a list of records

        @returns: a dict of {'Interleaved': record_state_is_interleaved,
                             'Span within range': duration_is_expected}

        """

        actual_cycle = len(records) // len(list(durations.keys()))
        offset = self.INTERLEAVE_SCAN_CYCLE_NUM_TOLERANCE
        expect_cycle_lowerbound = max(1, expect_cycles - offset)
        expect_cycle_upperbound = expect_cycles + offset
        enough_cycle_num = (actual_cycle >= expect_cycle_lowerbound
                            and actual_cycle <= expect_cycle_upperbound)
        interleaved = True
        span_within_range = True
        expected_state = None

        def _next_state(state):
            if state == 'allowlist':
                return 'no filter'
            elif state == 'no filter':
                return 'allowlist'
            else:
                logging.warning('Unexpected state %s', state)
                return None

        for i, record in enumerate(records):
            state = record['state']
            nstate = _next_state(state)

            # We can't count span on single data point and expected_state
            # hasn't set
            if i != 0:
                span = (record['time'] - records[i - 1]['time'])

                if state != expected_state:
                    interleaved = False

                if span < durations[nstate] -\
                                        self.INTERLEAVE_SCAN_DURATION_TOLERANCE:
                    span_within_range = False

                if span > durations[nstate] +\
                                        self.INTERLEAVE_SCAN_DURATION_TOLERANCE:
                    span_within_range = False

            expected_state = nstate

        return {
                'Enough cycle number': enough_cycle_num,
                'Interleaved': interleaved,
                'Span within range': span_within_range
        }

    def check_records_paused(self, records, cancel_event, expect_paused_time,
                             expect_resume):
        """ Check if the interleave scan is paused

        @param records: a list of records
        @param cancel_event: the timestamp interleave was canceled
        @param expect_paused_time: minimum duration of interleave scan paused
        @param expect_resume: True if interleave scan should restart,
                              False if ***we don't care***

        @returns: a dict of {'Cancel event': (bool),
                             'Non-empty records before paused': (bool),
                             'Non-empty records after paused': (bool),
                             'Paused enough time': (bool)
                            }
                  Note: some entries might not exist if it doesn't make sense
                        in that case.

        """

        result = {}

        result.update({'Cancel event': cancel_event is not None})
        if cancel_event is None:
            return result

        canceled_time = cancel_event + self.INTERLEAVE_SCAN_CANCEL_TOLERANCE

        before_paused_rec = [r for r in records if r['time'] < canceled_time]
        after_paused_rec = [r for r in records if r['time'] >= canceled_time]

        result.update({
                'Non-empty records before paused':
                len(before_paused_rec) != 0
        })

        if expect_resume:
            result.update({
                    'Non-empty records after paused':
                    len(after_paused_rec) != 0
            })

        if len(before_paused_rec) > 0 and len(after_paused_rec) > 0:
            # Records are stored chronologically.
            last_time_before_paused = before_paused_rec[-1]['time']
            first_time_after_paused = after_paused_rec[0]['time']
            paused_time = first_time_after_paused - last_time_before_paused
            result.update(
                    {'Paused enough time': paused_time >= expect_paused_time})

        return result

    def get_interleave_scan_durations(self):
        """ Get interleave scan duration.

        @returns: a dict of {'allowlist': allowlist_duration,
                             'no filter': no_filter_duration}

        """

        durations = self.interleave_scan_get_durations()
        if durations is None:
            raise error.TestFail(
                    'Unexpected error: failed to get interleave durations')

        # Change the unit from msec to second for future convenience.
        durations = {key: value * 0.001 for key, value in durations.items()}

        return durations

    @test_retry_and_log(False)
    def test_interleaving_state(self,
                                expect_true,
                                cycles=INTERLEAVE_SCAN_TEST_CYCLE):
        """ Test for checking if kernel is doing interleave scan or not.

        @params expect_true: True if kernel should be running interleave scan
                             False if kernel shouldn't.
        @params cycles: number of cycles to collect logs

        @returns: True on success, False otherwise.

        """
        durations = self.get_interleave_scan_durations()
        interleave_period = sum(durations.values())
        log_time = interleave_period * cycles
        self.interleave_logger_start()
        time.sleep(log_time)
        self.interleave_logger_stop()
        records = self.interleave_logger_get_records()

        logging.debug(records)

        if not expect_true:
            self.results = {'No records': len(records) == 0}
        else:
            self.results = self.check_records_interleaving(
                    durations, records, cycles)

        return all(self.results.values())

    @test_retry_and_log(False)
    def test_interleaving_suspend_resume(self, expect_true):
        """ Test for checking if kernel paused interleave scan during system
            suspended.

        @returns: True on success, False otherwise.

        """
        durations = self.get_interleave_scan_durations()
        interleave_period = sum(durations.values())

        # make sure suspend time is long enough to verify there is no
        # interleaving during suspended
        expect_suspend_time = max(self.SUSPEND_TIME_SECS,
                                  2 * interleave_period)

        # make sure we'll get some records before/after system suspended
        extra_sleep_time = 2 * interleave_period

        self.interleave_logger_start()
        time.sleep(extra_sleep_time)
        self.suspend_resume(suspend_time=expect_suspend_time)
        time.sleep(extra_sleep_time)
        self.interleave_logger_stop()
        records = self.interleave_logger_get_records()
        cancel_event = self.interleave_logger_get_cancel_event()

        logging.debug(records)
        logging.debug(cancel_event)

        if not expect_true:
            self.results = {'No records': len(records) == 0}
        else:
            # Currently resume time is not very reliable. It is likely the
            # actual time in sleeping is less than expect_suspend_time.
            # Check the interleave scan paused for at least one cycle long
            # instead.
            self.results = self.check_records_paused(records, cancel_event,
                                                     interleave_period, True)
        return all(self.results.values())

    @test_retry_and_log(False)
    def test_interleaving_active_scan_cycle(self, expect_true):
        """ Test for checking if kernel paused interleave scan during active
            scan.

        @returns: True on success, False otherwise.

        """
        durations = self.get_interleave_scan_durations()
        interleave_period = sum(durations.values())

        # make sure we'll get some records before/after active scan
        extra_sleep_time = 2 * interleave_period

        self.interleave_logger_start()
        time.sleep(extra_sleep_time)
        self.test_start_discovery()
        time.sleep(extra_sleep_time + self.INTERLEAVE_SCAN_CANCEL_TOLERANCE)
        self.interleave_logger_stop()
        records = self.interleave_logger_get_records()
        cancel_event = self.interleave_logger_get_cancel_event()

        logging.debug(records)
        logging.debug(cancel_event)

        if not expect_true:
            self.results = {'No records': len(records) == 0}
        else:
            # BlueZ pauses discovery for every DISCOVERY_DURATION then restarts
            # it 5 seconds later. Interleave scan also get restarted during the
            # paused time.
            self.results = self.check_records_paused(records, cancel_event,
                                                     self.DISCOVERY_DURATION,
                                                     False)
        self.test_stop_discovery()
        return all(self.results.values())

    def advmon_test_monitor_creation(self):
        """Test case: MONITOR_CREATION

        Validate register/unregister app and create/remove monitor.

        """
        self.test_is_adv_monitoring_supported()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_rssi([-40, 5, -60, 5])
        monitor1.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
        ])

        monitor2 = TestMonitor(app1)
        monitor2.update_type('or_patterns')
        monitor2.update_rssi([-40, 10, -60, 10])
        monitor2.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])

        # Read supported types and features, should not fail.
        self.test_supported_types()
        self.test_supported_features()

        # Activate/Release should not get called.
        self.test_add_monitor(monitor1,
                              expected_activate=False,
                              expected_release=False)

        # Register the app, should not fail.
        self.test_register_app(app1)

        # Already registered app path, should fail with AlreadyExists.
        self.test_register_app(app1, expected=False)

        # Activate should get called for the monitor added before register app.
        self.test_monitor_activate(monitor1, expected=True)

        # Correct monitor parameters, activate should get called.
        self.test_add_monitor(monitor2, expected_activate=True)

        # Remove a monitor, should not fail.
        self.test_remove_monitor(monitor1)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Already unregistered app path, should fail with DoesNotExists.
        self.test_unregister_app(app1, expected=False)

        # Release should get called for a monitor not removed before unregister.
        self.test_monitor_release(monitor2, expected=True)

        # Remove another monitor, should not fail.
        self.test_remove_monitor(monitor2)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_monitor_validity(self):
        """Test case: MONITOR_VALIDITY

        Validate monitor parameters - monitor type, patterns, RSSI filter
        values.

        """
        self.test_is_adv_monitoring_supported()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('incorrect_pattern')
        monitor1.update_rssi([-40, 5, -60, 5])
        monitor1.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
        ])

        monitor2 = TestMonitor(app1)
        monitor2.update_type('or_patterns')
        monitor2.update_rssi([-40, 10, -60, 10])
        monitor2.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        # Incorrect monitor type, release should get called.
        self.test_add_monitor(monitor1, expected_release=True)

        # Incorrect rssi parameters, release should get called.
        monitor2.update_rssi([40, 10, -60, 10])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_rssi([-140, 10, -60, 10])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_rssi([-40, 10, 60, 10])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_rssi([-40, 10, -160, 10])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_rssi([-60, 10, -40, 10])
        self.test_add_monitor(monitor2, expected_release=True)

        # Correct rssi parameters, activate should get called.
        monitor2.update_rssi([self.UNSET_RSSI, 10, -60, 10])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([-40, self.UNSET_TIMEOUT, -60, 10])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([-40, 10, self.UNSET_RSSI, 10])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([-40, 10, -60, self.UNSET_TIMEOUT])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        # Incorrect sampling period, release should get called.
        monitor2.update_sampling_period(257)
        self.test_add_monitor(monitor2, expected_release=True)

        # Partial RSSI filter and sampling period, activate should get called.
        monitor2.update_rssi([-40, 10, self.UNSET_RSSI, self.UNSET_TIMEOUT])
        monitor2.update_sampling_period(self.UNSET_SAMPLING_PERIOD)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([-40, 10, self.UNSET_RSSI, self.UNSET_TIMEOUT])
        monitor2.update_sampling_period(5)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([self.UNSET_RSSI, self.UNSET_TIMEOUT, -60, 10])
        monitor2.update_sampling_period(self.UNSET_SAMPLING_PERIOD)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([self.UNSET_RSSI, self.UNSET_TIMEOUT, -60, 10])
        monitor2.update_sampling_period(10)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT,
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT
        ])
        monitor2.update_sampling_period(self.UNSET_SAMPLING_PERIOD)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        # Incorrect pattern parameters, release should get called.
        monitor2.update_patterns([
                [32, 0x09, 'MOUSE'],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_patterns([
                [0, 0x00, 'MOUSE'],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_patterns([
                [0, 0x40, 'MOUSE'],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_patterns([
                [0, 0x09, '0123456789ABCDEF0123456789ABCDEF0'],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_patterns([
                [32, 0x09, [0xc2, 0x03]],
                [0, 3, [0x12, 0x18]],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        monitor2.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
                [0, 0x00, [0x12, 0x18]],
        ])
        self.test_add_monitor(monitor2, expected_release=True)

        # Correct pattern parameters, activate should get called.
        monitor2.update_patterns([
                [0, 0x09, 'MOUSE'],
        ])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        monitor2.update_rssi([-40, 10, -60, 10])
        monitor2.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
                [0, 0x03, [0x12, 0x18]],
        ])
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_remove_monitor(monitor2)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_pattern_filter(self):
        """Test case: PATTERN_FILTER

        Verify matching of advertisements w.r.t. various pattern values and
        different AD Data Types - Local Name Service UUID and Device Type.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_rssi([self.HIGH_RSSI, 3, self.LOW_RSSI, 3])

        # Register the app, should not fail.
        self.test_register_app(app1)

        monitor1.update_patterns([
                [5, 0x09, '_REF'],
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Local name 'KEYBD_REF' should match.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)

        # Local name 'MOUSE_REF' should match.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor1, count=2)

        self.test_stop_peer_device_adv(self.peer_keybd)
        self.test_stop_peer_device_adv(self.peer_mouse)
        self.test_remove_monitor(monitor1)

        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Service UUID 0x1812 should match.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)

        # Service UUID 0x1812 should match.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor1, count=2)

        self.test_stop_peer_device_adv(self.peer_keybd)
        self.test_stop_peer_device_adv(self.peer_mouse)
        self.test_remove_monitor(monitor1)

        monitor1.update_patterns([
                [0, 0x19, [0xc1, 0x03]],
                [0, 0x09, 'MOUSE'],
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Device type 0xc103 should match.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)

        # Local name 'MOUSE_REF' should match.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor1, count=2)

        self.test_stop_peer_device_adv(self.peer_keybd)
        self.test_stop_peer_device_adv(self.peer_mouse)
        self.test_remove_monitor(monitor1)

        monitor1.update_patterns([
                [0, 0x19, [0xc1, 0x03]],
                [0, 0x19, [0xc3, 0x03]],
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Device type 0xc103 should match.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)

        # Device type 0xc203 should not match.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor1, count=1)

        self.test_stop_peer_device_adv(self.peer_keybd)
        self.test_stop_peer_device_adv(self.peer_mouse)
        self.test_remove_monitor(monitor1)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_rssi_filter_range(self):
        """Test case: RSSI_FILTER_RANGE

        Verify unset RSSI filter and filter with no matching RSSI values.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        monitor1.update_rssi([
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT,
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Unset RSSI filter, adv should match multiple times.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=self.MULTIPLE_EVENTS)

        # Unset RSSI filter, DeviceLost should not get triggered.
        self.test_stop_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_lost(monitor1, count=0)

        self.test_remove_monitor(monitor1)

        monitor1.update_rssi([-10, 5, -20, 5])
        self.test_add_monitor(monitor1, expected_activate=True)

        # Adv RSSI lower than RSSI filter, DeviceFound should not get triggered.
        self.test_start_peer_device_adv(self.peer_keybd, duration=10)
        self.test_device_found(monitor1, count=0)

        # No device was found earlier, so DeviceLost should not get triggered.
        self.test_stop_peer_device_adv(self.peer_keybd, duration=10)
        self.test_device_lost(monitor1, count=0)

        self.test_remove_monitor(monitor1)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_rssi_filter_multi_peers(self):
        """Test case: RSSI_FILTER_MULTI_PEERS

        Verify RSSI filter matching with multiple peer devices.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        monitor1.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # DeviceFound should get triggered only once per device.
        self.test_start_peer_device_adv(self.peer_keybd, duration=10)
        self.test_device_found(monitor1, count=1)

        # DeviceFound should get triggered for another device.
        self.test_start_peer_device_adv(self.peer_mouse, duration=10)
        self.test_device_found(monitor1, count=2)

        # DeviceLost should get triggered only once per device.
        self.test_stop_peer_device_adv(self.peer_keybd, duration=10)
        self.test_device_lost(monitor1, count=1)

        # DeviceLost should get triggered for another device.
        self.test_stop_peer_device_adv(self.peer_mouse, duration=10)
        self.test_device_lost(monitor1, count=2)

        self.test_remove_monitor(monitor1)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_rssi_filter_reset(self):
        """Test case: RSSI_FILTER_RESET

        Verify reset of RSSI timers based on advertisements.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        monitor1.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 10,
        ])
        self.test_add_monitor(monitor1, expected_activate=True)

        # DeviceFound should get triggered once the peer starts advertising.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)

        # DeviceLost should not get triggered before timeout.
        self.test_stop_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_lost(monitor1, count=0)

        # Timer should get reset, so DeviceLost should not get triggered.
        # DeviceFound should not get triggered as device is not lost yet.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_lost(monitor1, count=0)
        self.test_device_found(monitor1, count=1)

        # Timer should get reset, so DeviceLost should not get triggered.
        self.test_stop_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_lost(monitor1, count=0)

        # DeviceLost should get triggered once timer completes.
        self.test_device_lost(monitor1, count=1, delay=10)

        self.test_remove_monitor(monitor1)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_multi_client(self):
        """Test case: MULTI_CLIENT

        Verify working of patterns filter and RSSI filters with multiple
        clients and multiple monitors.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create two test app instances.
        app1 = self.create_app()
        app2 = self.create_app()

        # Register both apps, should not fail.
        self.test_register_app(app1)
        self.test_register_app(app2)

        # Monitors with same pattern and RSSI filter values in both apps.
        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
                [0, 0x19, [0xc1, 0x03]],
        ])
        monitor1.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        monitor2 = TestMonitor(app2)
        monitor2.update_type('or_patterns')
        monitor2.update_patterns([
                [0, 0x03, [0x12, 0x18]],
                [0, 0x19, [0xc1, 0x03]],
        ])
        monitor2.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        # Activate should get invoked.
        self.test_add_monitor(monitor1, expected_activate=True)
        self.test_add_monitor(monitor2, expected_activate=True)

        # DeviceFound should get triggered for keyboard.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=1)
        self.test_device_found(monitor2, count=1)
        self.test_stop_peer_device_adv(self.peer_keybd)

        # Remove a monitor from one app.
        self.test_remove_monitor(monitor1)

        # Monitors with same pattern but different RSSI filter values.
        monitor3 = TestMonitor(app1)
        monitor3.update_type('or_patterns')
        monitor3.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
        ])
        monitor3.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        monitor4 = TestMonitor(app2)
        monitor4.update_type('or_patterns')
        monitor4.update_patterns([
                [0, 0x19, [0xc2, 0x03]],
        ])
        monitor4.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 10,
        ])

        # Activate should get invoked.
        self.test_add_monitor(monitor3, expected_activate=True)
        self.test_add_monitor(monitor4, expected_activate=True)

        # DeviceFound should get triggered for mouse.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor2, count=2)
        self.test_device_found(monitor3, count=1)
        self.test_device_found(monitor4, count=1)
        self.test_stop_peer_device_adv(self.peer_mouse)

        # Unregister both apps, should not fail.
        self.test_unregister_app(app1)
        self.test_unregister_app(app2)

        # Terminate the both test app instances.
        self.test_exit_app(app1)
        self.test_exit_app(app2)


    def advmon_test_fg_bg_combination(self):
        """Test case: FG_BG_COMBINATION

        Verify background scanning and foreground scanning do not interfere
        working of each other.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])
        monitor1.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        # Activate should get invoked.
        self.test_add_monitor(monitor1, expected_activate=True)

        # Pair/connect LE Mouse.
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_discover_device(self.peer_mouse.address)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_pairing(self.peer_mouse.address, self.peer_mouse.pin)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_connection_by_adapter(self.peer_mouse.address)
        self.test_connection_by_device(self.peer_mouse)

        # DeviceFound should get triggered for keyboard.
        self.test_reset_event_count(monitor1)
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=self.MULTIPLE_EVENTS)
        self.test_stop_peer_device_adv(self.peer_keybd, duration=5)

        # Start foreground scanning.
        self.test_start_discovery()

        # Disconnect LE mouse.
        self.test_disconnection_by_device(self.peer_mouse)

        # Remove the monitor.
        self.test_remove_monitor(monitor1)

        # Activate should get invoked.
        self.test_add_monitor(monitor1, expected_activate=True)

        # Connect LE mouse.
        self.test_connection_by_device(self.peer_mouse)

        # DeviceFound should get triggered for keyboard.
        self.test_reset_event_count(monitor1)
        self.test_start_peer_device_adv(self.peer_keybd, duration=10)
        self.test_device_found(monitor1, count=self.MULTIPLE_EVENTS)
        self.test_stop_peer_device_adv(self.peer_keybd, duration=5)

        # Stop foreground scanning.
        self.test_stop_discovery()

        # Disconnect LE mouse.
        self.test_disconnection_by_device(self.peer_mouse)

        # DeviceFound should get triggered for keyboard.
        self.test_reset_event_count(monitor1)
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor1, count=self.MULTIPLE_EVENTS)
        self.test_stop_peer_device_adv(self.peer_keybd)

        # Remove the monitor.
        self.test_remove_monitor(monitor1)

        # Connect LE mouse.
        self.test_connection_by_device(self.peer_mouse)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)


    def advmon_test_suspend_resume(self):
        """Test case: SUSPEND_RESUME

        Verify working of background scanning with suspend/resume.

        """
        self.test_is_adv_monitoring_supported()
        self.test_setup_peer_devices()

        # Create two test app instances.
        app1 = self.create_app()
        app2 = self.create_app()

        # Register both apps, should not fail.
        self.test_register_app(app1)
        self.test_register_app(app2)

        # Add monitors in both apps.
        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([ [0, 0x03, [0x12, 0x18]], ])
        monitor1.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        monitor2 = TestMonitor(app1)
        monitor2.update_type('or_patterns')
        monitor2.update_patterns([ [0, 0x19, [0xc2, 0x03]], ])
        monitor2.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 10,
        ])

        monitor3 = TestMonitor(app2)
        monitor3.update_type('or_patterns')
        monitor3.update_patterns([ [0, 0x03, [0x12, 0x18]], ])
        monitor3.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 3,
        ])

        monitor4 = TestMonitor(app2)
        monitor4.update_type('or_patterns')
        monitor4.update_patterns([ [0, 0x19, [0xc1, 0x03]], ])
        monitor4.update_rssi([
                self.HIGH_RSSI, self.UNSET_TIMEOUT, self.LOW_RSSI, 15,
        ])

        # Activate should get invoked.
        self.test_add_monitor(monitor1, expected_activate=True)
        self.test_add_monitor(monitor2, expected_activate=True)
        self.test_add_monitor(monitor3, expected_activate=True)
        self.test_add_monitor(monitor4, expected_activate=True)

        # DeviceFound for mouse should get triggered only for matched monitors
        self.test_start_peer_device_adv(self.peer_mouse, duration=5)
        self.test_device_found(monitor1, count=1)
        self.test_device_found(monitor2, count=1)
        self.test_device_found(monitor3, count=1)
        self.test_device_found(monitor4, count=0)

        # Initiate suspend/resume.
        self.suspend_resume()

        # DeviceLost should get triggered for tracked devices on resume.
        self.test_device_lost(monitor1, count=1)
        self.test_device_lost(monitor2, count=1)
        self.test_device_lost(monitor3, count=1)
        self.test_device_lost(monitor4, count=0)

        # DeviceFound should get triggered again for matched monitors on resume.
        self.test_device_found(monitor1, count=2)
        self.test_device_found(monitor2, count=2)
        self.test_device_found(monitor3, count=2)
        self.test_device_found(monitor4, count=0)
        self.test_stop_peer_device_adv(self.peer_mouse)

        # Remove a monitor from one app, shouldn't affect working of other
        # monitors or apps.
        self.test_remove_monitor(monitor1)

        # Terminate an app, shouldn't affect working of monitors in other apps.
        self.test_exit_app(app1)

        # DeviceFound should get triggered for keyboard.
        self.test_start_peer_device_adv(self.peer_keybd, duration=5)
        self.test_device_found(monitor3, count=3)
        self.test_device_found(monitor4, count=1)
        self.test_stop_peer_device_adv(self.peer_keybd)

        # Unregister the running app, should not fail.
        self.test_unregister_app(app2)

        # Terminate the running test app instance.
        self.test_exit_app(app2)


    def advmon_test_interleaved_scan(self):
        """ Test cases for verifying interleave scan """

        self.test_is_adv_monitoring_supported()

        # cycles to collect logs for tests expect no interleave scan
        EXPECT_FALSE_TEST_CYCLE = 3

        supported_features = self.read_supported_features()

        if 'controller-patterns' in supported_features:
            # For device supporting hardware filtering, software interleave
            # scan shall not be used.
            sw_interleave_scan = False
        else:
            sw_interleave_scan = True

        # Create a test app instance.
        app1 = self.create_app()

        monitor1 = TestMonitor(app1)
        monitor1.update_type('or_patterns')
        monitor1.update_patterns([
                [0, 0x03, [0x12, 0x18]],
        ])
        monitor1.update_rssi([
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT,
                self.UNSET_RSSI,
                self.UNSET_TIMEOUT
        ])

        # Register the app, should not fail.
        self.test_register_app(app1)

        # Activate should get invoked.
        self.test_add_monitor(monitor1, expected_activate=True)

        # No device in allowlist, interleave with idle
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)

        # No device in allowlist, interleave with idle, interrupted by active
        # scan
        self.test_start_discovery()
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)
        self.test_stop_discovery()
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)

        # No device in allowlist, interleave with idle, interrupted by suspend
        # resume
        self.suspend_resume()
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)

        # Pair/connect LE Mouse.
        device = self.devices['BLE_MOUSE'][0]
        self.test_discover_device(device.address)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)
        self.test_pairing(device.address, device.pin, trusted=True)
        time.sleep(self.PAIR_TEST_SLEEP_SECS)

        # BLE_MOUSE in allowlist, interleave with allowlist passive scan
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)
        device.AdapterPowerOff()
        # Make sure the peer is disconnected
        self.test_device_is_not_connected(device.address)
        self.test_interleaving_state(sw_interleave_scan)

        # Interleaving with allowlist should get paused during active scan
        self.test_interleaving_active_scan_cycle(sw_interleave_scan)

        # Interleaving with allowlist should get resumed after stopping scan
        self.test_interleaving_state(sw_interleave_scan)

        # Interleaving with allowlist should get paused during system suspend,
        # get resumed after system awake
        self.test_interleaving_suspend_resume(sw_interleave_scan)
        self.test_interleaving_state(sw_interleave_scan)

        self.test_remove_monitor(monitor1)
        self.test_interleaving_state(False, cycles=EXPECT_FALSE_TEST_CYCLE)

        # Unregister the app, should not fail.
        self.test_unregister_app(app1)

        # Terminate the test app instance.
        self.test_exit_app(app1)

        device.AdapterPowerOn()
