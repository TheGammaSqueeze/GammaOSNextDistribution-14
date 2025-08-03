# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import contextlib
import dbus
import logging
import sys
import time
import traceback

import common
from autotest_lib.client.bin import local_host
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import crash_detector
from autotest_lib.client.cros import upstart
from autotest_lib.client.cros.cellular import mm
from autotest_lib.client.cros.cellular import mm1_constants
from autotest_lib.client.cros.networking import cellular_proxy
from autotest_lib.client.cros.networking import mm1_proxy
from autotest_lib.client.cros.networking import shill_context
from autotest_lib.client.cros.networking import shill_proxy


class CellularTestEnvironment(object):
    """Setup and verify cellular test environment.

    This context manager configures the following:
        - Shuts down other devices except cellular.
        - Shill and MM logging is enabled appropriately for cellular.
        - Initializes members that tests should use to access test environment
          (eg. |shill|, |modem_manager|, |modem|).
        - modemfwd is stopped to prevent the modem from rebooting underneath
          us.

    Then it verifies the following is valid:
        - The SIM is inserted and valid.
        - There is one and only one modem in the device.
        - The modem is registered to the network.
        - There is a cellular service in shill and it's not connected.

    Don't use this base class directly, use the appropriate subclass.

    Setup for over-the-air tests:
        with CellularOTATestEnvironment() as test_env:
            # Test body

    Setup for pseudomodem tests:
        with CellularPseudoMMTestEnvironment(
                pseudomm_args=({'family': '3GPP'})) as test_env:
            # Test body

    """

    def __init__(self,
                 shutdown_other_devices=True,
                 modem_pattern='',
                 skip_modem_reset=False,
                 is_esim_test=False,
                 enable_temp_containments=True):
        """
        @param shutdown_other_devices: If True, shutdown all devices except
                cellular.
        @param modem_pattern: Search string used when looking for the modem.
        @param enable_temp_containments: Enable temporary containments to avoid
                failures on tests with known problems.

        """
        # Tests should use this main loop instead of creating their own.
        self.mainloop = dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
        self.bus = dbus.SystemBus(mainloop=self.mainloop)

        self.shill = None
        self.modem_manager = None
        self.modem = None
        self.modem_path = None

        self._modem_pattern = modem_pattern
        self._skip_modem_reset = skip_modem_reset
        self._is_esim_test = is_esim_test
        self._enable_temp_containments = enable_temp_containments
        self._system_service_order = ''
        self._test_service_order = 'cellular,ethernet'

        self._nested = None
        self._context_managers = []
        self.detect_crash = crash_detector.CrashDetector(
                local_host.LocalHost())
        self.detect_crash.remove_crash_files()
        if shutdown_other_devices:
            self._context_managers.append(
                    shill_context.AllowedTechnologiesContext([
                            shill_proxy.ShillProxy.TECHNOLOGY_CELLULAR,
                            shill_proxy.ShillProxy.TECHNOLOGY_ETHERNET
                    ]))

    @contextlib.contextmanager
    def _disable_shill_autoconnect(self):
        self._enable_shill_cellular_autoconnect(False)
        yield
        self._enable_shill_cellular_autoconnect(True)

    def __enter__(self):
        try:
            # Wait for system daemons to stabilize before beginning the test.
            # Modemfwd, Chrome, Shill and Hermes might be active before the test
            # begins, and interrupting them abruptly during test setup might
            # lead to flaky tests. The modem might also appear/disappear
            # multiple times during this period. Ideally, we would wait for a
            # green signal from these daemons before performing test setup.
            with open('/proc/uptime') as uptime_file:
                uptime = float(uptime_file.readline().split()[0])
            if uptime < 60:
                logging.info(
                        "Waiting %.1f seconds to reach uptime of 1 minute before "
                        "starting test", 60 - uptime)
                time.sleep(60 - uptime)

            if upstart.has_service('modemfwd') and upstart.is_running('modemfwd'):
                # Due to b/179796133, stopping modemfwd right after it was
                # started by a previous test, can wedge the modem. In many
                # devices, a ~1 second delay solves the problem.
                time.sleep(4)
                upstart.stop_job('modemfwd')
            # Temporarily disable shill autoconnect to cellular service while
            # the test environment is setup to prevent a race condition
            # between disconnecting the modem in _verify_cellular_service()
            # and shill autoconnect.
            with self._disable_shill_autoconnect():
                try:
                    from contextlib import nested # Python 2
                except ImportError:
                    from contextlib import ExitStack, contextmanager

                    @contextmanager
                    def nested(*contexts):
                        """ Implementation of nested for python3"""
                        with ExitStack() as stack:
                            for ctx in contexts:
                                stack.enter_context(ctx)
                            yield contexts

                self._nested = nested(*self._context_managers)

                self._nested.__enter__()

                self._initialize_shill()

                # Perform SIM verification now to ensure that we can enable the
                # modem in _initialize_modem_components(). ModemManager does not
                # allow enabling a modem without a SIM.
                self._verify_sim()
                self._initialize_modem_components()

                self._setup_logging()

                if not self._is_esim_test:
                    self._wait_for_modem_registration()
                self._verify_cellular_service()

                return self
        except (error.TestError, dbus.DBusException,
                shill_proxy.ShillProxyError) as e:
            except_type, except_value, except_traceback = sys.exc_info()
            lines = traceback.format_exception(except_type, except_value,
                                               except_traceback)
            logging.error('Error during test initialization:\n%s',
                          ''.join(lines))
            self.__exit__(*sys.exc_info())
            raise error.TestError('INIT_ERROR: %s' % str(e))
        except:
            self.__exit__(*sys.exc_info())
            raise

    def __exit__(self, exception, value, traceback):
        exception_on_restore_state = None
        try:
            self._restore_state()
        except Exception as ex:
            # Exceptions thrown by _restore_state() should be ignored if a
            # previous exception exist, otherwise the root cause of the test
            # failure will be overwritten by the clean up error in
            # _restore_state, and that is not useful.
            if exception is None:
                exception_on_restore_state = ex

        # If a test fails and a crash is detected, the crash error takes
        # priority over the previous failure.
        crash_files = self.detect_crash.get_new_crash_files()
        if any(cf for cf in crash_files if any(pr in cf for pr in [
                'ModemManager', 'shill', 'qmi', 'mbim', 'hermes', 'modemfwd'
        ])):
            logging.info(
                    'A crash was encountered. '
                    'Overriding the previous error: %s', value)
            raise error.TestError(
                    'One or more daemon crashes were detected. '
                    'See crash dumps: {}'.format(crash_files))

        if exception_on_restore_state is not None:
            raise exception_on_restore_state

        if self._nested:
            return self._nested.__exit__(exception, value, traceback)
        self.shill = None
        self.modem_manager = None
        self.modem = None
        self.modem_path = None

    def _restore_state(self):
        """Try to restore the test environment to a good state.
        """
        if upstart.has_service('modemfwd'):
            upstart.restart_job('modemfwd')
        if self.shill:
            self._set_service_order(self._system_service_order)

    def _get_shill_cellular_device_object(self):
        return utils.poll_for_condition(
            lambda: self.shill.find_cellular_device_object(),
            exception=error.TestError('Cannot find cellular device in shill. '
                                      'Is the modem plugged in?'),
            timeout=shill_proxy.ShillProxy.DEVICE_ENUMERATION_TIMEOUT)

    def _get_service_order(self):
        """Get the shill service order.

        @return string service order on success, None otherwise.

        """
        return str(self.shill.manager.GetServiceOrder())

    def _set_service_order(self, order):
        """Set the shill service order.

        @param order string comma-delimited service order
        (eg. 'cellular,ethernet')
        @return bool True on success, False otherwise.

        """
        self.shill.manager.SetServiceOrder(dbus.String(order))
        return True

    def _enable_modem(self):
        modem_device = self._get_shill_cellular_device_object()
        try:
            modem_device.Enable()
        except dbus.DBusException as e:
            if (e.get_dbus_name() !=
                    shill_proxy.ShillProxy.ERROR_IN_PROGRESS):
                raise

        utils.poll_for_condition(
            lambda: modem_device.GetProperties()['Powered'],
            exception=error.TestError(
                'Failed to enable modem.'),
            timeout=shill_proxy.ShillProxy.DEVICE_ENABLE_DISABLE_TIMEOUT)

    def _enable_shill_cellular_autoconnect(self, enable):
        shill = cellular_proxy.CellularProxy.get_proxy(self.bus)
        shill.manager.SetProperty(
            shill_proxy.ShillProxy.
            MANAGER_PROPERTY_NO_AUTOCONNECT_TECHNOLOGIES,
            '' if enable else 'cellular')

    def _is_unsupported_error(self, e):
        return (e.get_dbus_name() ==
                shill_proxy.ShillProxy.ERROR_NOT_SUPPORTED or
                (e.get_dbus_name() ==
                 shill_proxy.ShillProxy.ERROR_FAILURE and
                 'operation not supported' in e.get_dbus_message()))

    def _reset_modem(self):
        modem_device = self._get_shill_cellular_device_object()
        try:
            # MBIM modems do not support being reset.
            self.shill.reset_modem(modem_device, expect_service=False)
        except dbus.DBusException as e:
            if not self._is_unsupported_error(e):
                raise

    def _initialize_shill(self):
        """Get access to shill."""
        # CellularProxy.get_proxy() checks to see if shill is running and
        # responding to DBus requests. It returns None if that's not the case.
        self.shill = cellular_proxy.CellularProxy.get_proxy(self.bus)
        if self.shill is None:
            raise error.TestError('Cannot connect to shill, is shill running?')

        self._system_service_order = self._get_service_order()
        self._set_service_order(self._test_service_order)

    def _initialize_modem_components(self):
        """Reset the modem and get access to modem components."""
        # Enable modem first so shill initializes the modemmanager proxies so
        # we can call reset on it.
        self._enable_modem()
        if not self._skip_modem_reset:
            self._reset_modem()

        # PickOneModem() makes sure there's a modem manager and that there is
        # one and only one modem.
        self.modem_manager, self.modem_path = \
            mm.PickOneModem(self._modem_pattern)
        self.modem = self.modem_manager.GetModem(self.modem_path)
        if self.modem is None:
            raise error.TestError('Cannot get modem object at %s.' %
                                  self.modem_path)

    def _setup_logging(self):
        self.shill.set_logging_for_cellular_test()
        self.modem_manager.SetDebugLogging()

    def _verify_sim(self):
        """Verify SIM is valid.

        Make sure a SIM in inserted and that it is not locked.

        @raise error.TestError if SIM does not exist or is locked.

        """
        # check modem SIM slot and properties and switch slot as needed
        modem_proxy = self._check_for_modem_with_sim()
        if modem_proxy is None:
            raise error.TestError('There is no Modem with non empty SIM path.')

        modem_device = self._get_shill_cellular_device_object()
        props = modem_device.GetProperties()

        # No SIM in CDMA modems.
        family = props[
            cellular_proxy.CellularProxy.DEVICE_PROPERTY_TECHNOLOGY_FAMILY]
        if (family ==
                cellular_proxy.CellularProxy.
                DEVICE_PROPERTY_TECHNOLOGY_FAMILY_CDMA):
            return

        # Make sure there is a SIM.
        if not props[cellular_proxy.CellularProxy.DEVICE_PROPERTY_SIM_PRESENT]:
            raise error.TestError('There is no SIM in the modem.')

        # Make sure SIM is not locked.
        lock_status = props.get(
            cellular_proxy.CellularProxy.DEVICE_PROPERTY_SIM_LOCK_STATUS,
            None)
        if lock_status is None:
            raise error.TestError('Failed to read SIM lock status.')
        locked = lock_status.get(
            cellular_proxy.CellularProxy.PROPERTY_KEY_SIM_LOCK_ENABLED,
            None)
        if locked is None:
            raise error.TestError('Failed to read SIM LockEnabled status.')
        elif locked:
            raise error.TestError(
                'SIM is locked, test requires an unlocked SIM.')

    def _check_for_modem_with_sim(self):
        """
        Make sure modem got active SIM and path is not empty

        switch slot to get non empty sim path and active sim slot for modem

        @return active modem object or None

        """
        mm_proxy = mm1_proxy.ModemManager1Proxy.get_proxy()
        if mm_proxy is None:
            raise error.TestError('Modem manager is not initialized')

        modem_proxy = mm_proxy.wait_for_modem(mm1_constants.MM_MODEM_POLL_TIME)
        if modem_proxy is None:
            raise error.TestError('Modem not initialized')

        primary_slot = modem_proxy.get_primary_sim_slot()
        # Get SIM path from modem SIM properties
        modem_props = modem_proxy.properties(mm1_constants.I_MODEM)
        sim_path = modem_props['Sim']

        logging.info('Device SIM values=> path:%s '
                'primary slot:%d', sim_path, primary_slot)

        def is_usable_sim(path):
            """Check if sim at path can be used to establish a connection"""
            if path == mm1_constants.MM_EMPTY_SLOT_PATH:
                return False
            sim_proxy = modem_proxy.get_sim_at_path(path)
            sim_props = sim_proxy.properties()
            return sim_props[
                    'EsimStatus'] != mm1_constants.MM_SIM_ESIM_STATUS_NO_PROFILES

        # Check current SIM path value and status
        if is_usable_sim(sim_path):
            return modem_proxy

        slots = modem_props['SimSlots']
        logging.info('Dut not in expected state, '
                    'current sim path:%s slots:%s', sim_path, slots)

        for idx, path in enumerate(slots):
            if not is_usable_sim(path):
                continue
            logging.info('Primary slot does not have a SIM, '
                        'switching slot to %d', idx+1)

            if (primary_slot != idx + 1):
                logging.info('setting slot:%d path:%s', idx+1, path)
                modem_proxy.set_primary_slot(idx+1)
                modem_proxy = \
                    mm_proxy.wait_for_modem(mm1_constants.MM_MODEM_POLL_TIME)
                return modem_proxy
        return None

    def _wait_for_modem_registration(self):
        """Wait for the modem to register with the network.

        @raise error.TestError if modem is not registered.

        """
        utils.poll_for_condition(
            self.modem.ModemIsRegistered,
            exception=error.TestError(
                'Modem failed to register with the network.'),
            timeout=cellular_proxy.CellularProxy.SERVICE_REGISTRATION_TIMEOUT)

    def _verify_cellular_service(self):
        """Make sure a cellular service exists.

        The cellular service should not be connected to the network.

        @raise error.TestError if cellular service does not exist or if
                there are multiple cellular services.

        """
        service = self.shill.wait_for_cellular_service_object()

        try:
            service.Disconnect()
        except dbus.DBusException as e:
            if (e.get_dbus_name() !=
                    cellular_proxy.CellularProxy.ERROR_NOT_CONNECTED):
                raise
        success, state, _ = self.shill.wait_for_property_in(
            service,
            cellular_proxy.CellularProxy.SERVICE_PROPERTY_STATE,
            ('idle',),
            cellular_proxy.CellularProxy.SERVICE_DISCONNECT_TIMEOUT)
        if not success:
            raise error.TestError(
                'Cellular service needs to start in the "idle" state. '
                'Current state is "%s". '
                'Modem disconnect may have failed.' %
                state)


class CellularOTATestEnvironment(CellularTestEnvironment):
    """Setup and verify cellular over-the-air (OTA) test environment. """

    def __init__(self, **kwargs):
        super(CellularOTATestEnvironment, self).__init__(**kwargs)

# pseudomodem tests disabled with b/180627893, cleaningup all pseudomodem
# related files and imports through: b/205769777
'''
class CellularPseudoMMTestEnvironment(CellularTestEnvironment):
    """Setup and verify cellular pseudomodem test environment. """

    def __init__(self, pseudomm_args=None, **kwargs):
        """
        @param pseudomm_args: Tuple of arguments passed to the pseudomodem, see
                pseudomodem_context.py for description of each argument in the
                tuple: (flags_map, block_output, bus)

        """
        kwargs["skip_modem_reset"] = True
        super(CellularPseudoMMTestEnvironment, self).__init__(**kwargs)
        self._context_managers.append(
            pseudomodem_context.PseudoModemManagerContext(
                True, bus=self.bus, *pseudomm_args))
'''

class CellularESIMTestEnvironment(CellularTestEnvironment):
    """Setup cellular eSIM test environment. """

    def __init__(self, esim_arguments=None, **kwargs):
        kwargs["skip_modem_reset"] = True
        kwargs["is_esim_test"] = True
        super(CellularESIMTestEnvironment, self).__init__(**kwargs)
