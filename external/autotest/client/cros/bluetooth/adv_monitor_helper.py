# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Advertisement Monitor Test Application."""

import dbus
import dbus.mainloop.glib
import dbus.service
# AU tests use ToT client code, but ToT -3 client version.
try:
    from gi.repository import GObject
except ImportError:
    import gobject as GObject
import logging

from multiprocessing import Process, Pipe
from threading import Thread

DBUS_OM_IFACE = 'org.freedesktop.DBus.ObjectManager'
DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'

BLUEZ_SERVICE_NAME = 'org.bluez'

ADV_MONITOR_MANAGER_IFACE = 'org.bluez.AdvertisementMonitorManager1'
ADV_MONITOR_IFACE = 'org.bluez.AdvertisementMonitor1'
ADV_MONITOR_APP_BASE_PATH = '/org/bluez/adv_monitor_app'


class AdvMonitor(dbus.service.Object):
    """A monitor object.

    This class exposes a dbus monitor object along with its properties
    and methods.

    More information can be found at BlueZ documentation:
    doc/advertisement-monitor-api.txt

    """

    # Refer doc/advertisement-monitor-api.txt for more info about unset values.
    UNSET_RSSI = 127
    UNSET_TIMEOUT = 0
    UNSET_SAMPLING_PERIOD = 256

    # Indexes of the Monitor object parameters in a monitor data list.
    MONITOR_TYPE = 0
    RSSI_FILTER = 1
    PATTERNS = 2

    # Indexes of the RSSI filter parameters in a monitor data list.
    RSSI_H_THRESH = 0
    RSSI_H_TIMEOUT = 1
    RSSI_L_THRESH = 2
    RSSI_L_TIMEOUT = 3
    SAMPLING_PERIOD = 4

    # Indexes of the Patterns filter parameters in a monitor data list.
    PATTERN_START_POS = 0
    PATTERN_AD_TYPE = 1
    PATTERN_DATA = 2

    def __init__(self, bus, app_path, monitor_id, monitor_data):
        """Construction of a Monitor object.

        @param bus: a dbus system bus.
        @param app_path: application path.
        @param monitor_id: unique monitor id.

        """
        self.path = app_path + '/monitor' + str(monitor_id)
        self.bus = bus

        self.events = dict()
        self.events['Activate'] = 0
        self.events['Release'] = 0
        self.events['DeviceFound'] = 0
        self.events['DeviceLost'] = 0

        self.target_devices = []

        self._set_type(monitor_data[self.MONITOR_TYPE])
        self._set_rssi(monitor_data[self.RSSI_FILTER])
        self._set_patterns(monitor_data[self.PATTERNS])

        super(AdvMonitor, self).__init__(self.bus, self.path)


    def get_path(self):
        """Get the dbus object path of the monitor.

        @returns: the monitor object path.

        """
        return dbus.ObjectPath(self.path)


    def get_properties(self):
        """Get the properties dictionary of the monitor.

        @returns: the monitor properties dictionary.

        """
        properties = dict()
        properties['Type'] = dbus.String(self.monitor_type)
        if self.rssi_h_thresh != self.UNSET_RSSI:
            properties['RSSIHighThreshold'] = dbus.Int16(self.rssi_h_thresh)
        if self.rssi_h_timeout != self.UNSET_TIMEOUT:
            properties['RSSIHighTimeout'] = dbus.UInt16(self.rssi_h_timeout)
        if self.rssi_l_thresh != self.UNSET_RSSI:
            properties['RSSILowThreshold'] = dbus.Int16(self.rssi_l_thresh)
        if self.rssi_l_timeout != self.UNSET_TIMEOUT:
            properties['RSSILowTimeout'] = dbus.UInt16(self.rssi_l_timeout)
        if self.sampling_period != self.UNSET_SAMPLING_PERIOD:
            properties['RSSISamplingPeriod'] = dbus.UInt16(self.sampling_period)
        properties['Patterns'] = dbus.Array(self.patterns, signature='(yyay)')
        return {ADV_MONITOR_IFACE: properties}


    def _set_type(self, monitor_type):
        """Set the monitor type.

        @param monitor_type: the type of a monitor.

        """
        self.monitor_type = monitor_type


    def _set_rssi(self, rssi):
        """Set the RSSI filter values.

        @param rssi: the list of rssi threshold and timeout values.

        """
        self.rssi_h_thresh = rssi[self.RSSI_H_THRESH]
        self.rssi_h_timeout = rssi[self.RSSI_H_TIMEOUT]
        self.rssi_l_thresh = rssi[self.RSSI_L_THRESH]
        self.rssi_l_timeout = rssi[self.RSSI_L_TIMEOUT]
        self.sampling_period = rssi[self.SAMPLING_PERIOD]


    def _set_patterns(self, patterns):
        """Set the content filter patterns.

        @param patterns: the list of start position, ad type and patterns.

        """
        self.patterns = []
        for pattern in patterns:
            start_pos = dbus.Byte(pattern[self.PATTERN_START_POS])
            ad_type = dbus.Byte(pattern[self.PATTERN_AD_TYPE])
            ad_data = []
            for byte in pattern[self.PATTERN_DATA]:
                ad_data.append(dbus.Byte(byte))
            adv_pattern = dbus.Struct((start_pos, ad_type, ad_data),
                                      signature='yyay')
            self.patterns.append(adv_pattern)


    def remove_monitor(self):
        """Remove the monitor object.

        Invoke the dbus method to remove current monitor object from the
        connection.

        """
        self.remove_from_connection()


    def _update_event_count(self, event):
        """Update the event count.

        @param event: name of the event.

        """
        self.events[event] += 1


    def get_event_count(self, event):
        """Read the event count.

        @param event: name of the specific event or 'All' for all events.

        @returns: count of the specific event or dict of counts of all events.

        """
        if event == 'All':
            return self.events

        return self.events.get(event)


    def reset_event_count(self, event):
        """Reset the event count.

        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        if event == 'All':
            for event_key in self.events:
                self.events[event_key] = 0
            return True

        if event in self.events:
            self.events[event] = 0
            return True

        return False


    def set_target_devices(self, devices):
        """Set the target devices to the given monitor.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        @param devices: a list of devices in dbus object path

        """
        self.target_devices = devices

    @dbus.service.method(DBUS_PROP_IFACE,
                         in_signature='s',
                         out_signature='a{sv}')
    def GetAll(self, interface):
        """Get the properties dictionary of the monitor.

        @param interface: the bluetooth dbus interface.

        @returns: the monitor properties dictionary.

        """
        logging.info('%s: %s GetAll', self.path, interface)

        if interface != ADV_MONITOR_IFACE:
            logging.error('%s: GetAll: Invalid arg %s', self.path, interface)
            return {}

        return self.get_properties()[ADV_MONITOR_IFACE]


    @dbus.service.method(ADV_MONITOR_IFACE,
                         in_signature='',
                         out_signature='')
    def Activate(self):
        """The method callback at Activate."""
        logging.info('%s: Monitor Activated!', self.path)
        self._update_event_count('Activate')


    @dbus.service.method(ADV_MONITOR_IFACE,
                         in_signature='',
                         out_signature='')
    def Release(self):
        """The method callback at Release."""
        logging.info('%s: Monitor Released!', self.path)
        self._update_event_count('Release')


    @dbus.service.method(ADV_MONITOR_IFACE,
                         in_signature='o',
                         out_signature='')
    def DeviceFound(self, device):
        """The method callback at DeviceFound.

        @param device: the dbus object path of the found device.

        """
        logging.info('%s: %s Device Found!', self.path, device)
        if device in self.target_devices:
            self._update_event_count('DeviceFound')
        else:
            logging.debug('Found an uninteresting device: %s', device)


    @dbus.service.method(ADV_MONITOR_IFACE,
                         in_signature='o',
                         out_signature='')
    def DeviceLost(self, device):
        """The method callback at DeviceLost.

        @param device: the dbus object path of the lost device.

        """
        logging.info('%s: %s Device Lost!', self.path, device)
        if device in self.target_devices:
            self._update_event_count('DeviceLost')
        else:
            logging.debug('Lost an uninteresting device: %s', device)


class AdvMonitorApp(dbus.service.Object):
    """The test application.

    This class implements a test application to manage monitor objects.

    """

    def __init__(self, bus, dbus_mainloop, advmon_manager, app_id):
        """Construction of a test application object.

        @param bus: a dbus system bus.
        @param dbus_mainloop: an instance of mainloop.
        @param advmon_manager: AdvertisementMonitorManager1 interface on
                               the adapter.
        @param app_id: application id (to create application path).

        """
        self.bus = bus
        self.mainloop = dbus_mainloop
        self.advmon_mgr = advmon_manager
        self.app_path = ADV_MONITOR_APP_BASE_PATH + str(app_id)

        self.monitors = dict()

        super(AdvMonitorApp, self).__init__(self.bus, self.app_path)


    def get_app_path(self):
        """Get the dbus object path of the application.

        @returns: the application path.

        """
        return dbus.ObjectPath(self.app_path)


    def add_monitor(self, monitor_data):
        """Create a monitor object.

        @param monitor_data: the list containing monitor type, RSSI filter
                             values and patterns.

        @returns: monitor id, once the monitor is created.

        """
        monitor_id = 0
        while monitor_id in self.monitors:
            monitor_id += 1

        monitor = AdvMonitor(self.bus, self.app_path, monitor_id, monitor_data)

        # Emit the InterfacesAdded signal once the Monitor object is created.
        self.InterfacesAdded(monitor.get_path(), monitor.get_properties())

        self.monitors[monitor_id] = monitor

        return monitor_id


    def remove_monitor(self, monitor_id):
        """Remove a monitor object based on the given monitor id.

        @param monitor_id: the monitor id.

        @returns: True on success, False otherwise.

        """
        if monitor_id not in self.monitors:
            return False

        monitor = self.monitors[monitor_id]

        # Emit the InterfacesRemoved signal before removing the Monitor object.
        self.InterfacesRemoved(monitor.get_path(),
                               list(monitor.get_properties().keys()))

        monitor.remove_monitor()

        self.monitors.pop(monitor_id)

        return True


    def get_event_count(self, monitor_id, event):
        """Read the count of a particular event on the given monitor.

        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: count of the specific event or dict of counts of all events.

        """
        if monitor_id not in self.monitors:
            return None

        return self.monitors[monitor_id].get_event_count(event)


    def reset_event_count(self, monitor_id, event):
        """Reset the count of a particular event on the given monitor.

        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        if monitor_id not in self.monitors:
            return False

        return self.monitors[monitor_id].reset_event_count(event)


    def set_target_devices(self, monitor_id, devices):
        """Set the target devices to the given monitor.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        @param monitor_id: the monitor id.
        @param devices: a list of devices in dbus object path

        @returns: True on success, False otherwise.
        """
        if monitor_id not in self.monitors:
            return False

        self.monitors[monitor_id].set_target_devices(devices)
        return True

    def _mainloop_thread(self):
        """Run the dbus mainloop thread.

        Callback methods on the monitor objects get invoked only when the
        dbus mainloop is running. This thread starts when app is registered
        and stops when app is unregistered.

        """
        self.mainloop.run() # blocks until mainloop.quit() is called


    def register_app(self):
        """Register an advertisement monitor app.

        @returns: True on success, False otherwise.

        """
        if self.mainloop.is_running():
            self.mainloop.quit()

        self.register_successful = False

        def register_cb():
            """Handler when RegisterMonitor succeeded."""
            logging.info('%s: RegisterMonitor successful!', self.app_path)
            self.register_successful = True
            self.mainloop.quit()

        def register_error_cb(error):
            """Handler when RegisterMonitor failed."""
            logging.error('%s: RegisterMonitor failed: %s', self.app_path,
                                                            str(error))
            self.register_successful = False
            self.mainloop.quit()

        self.advmon_mgr.RegisterMonitor(self.get_app_path(),
                                        reply_handler=register_cb,
                                        error_handler=register_error_cb)
        self.mainloop.run() # blocks until mainloop.quit() is called

        # Start a background thread to run mainloop.run(). This is required for
        # the bluetoothd to be able to invoke methods on the monitor object.
        # Mark this thread as a daemon to make sure that the thread is killed
        # in case the parent process dies unexpectedly.
        t = Thread(target=self._mainloop_thread)
        t.daemon = True
        t.start()

        return self.register_successful


    def unregister_app(self):
        """Unregister an advertisement monitor app.

        @returns: True on success, False otherwise.

        """
        if self.mainloop.is_running():
            self.mainloop.quit()

        self.unregister_successful = False

        def unregister_cb():
            """Handler when UnregisterMonitor succeeded."""
            logging.info('%s: UnregisterMonitor successful!', self.app_path)
            self.unregister_successful = True
            self.mainloop.quit()

        def unregister_error_cb(error):
            """Handler when UnregisterMonitor failed."""
            logging.error('%s: UnregisterMonitor failed: %s', self.app_path,
                                                              str(error))
            self.unregister_successful = False
            self.mainloop.quit()

        self.advmon_mgr.UnregisterMonitor(self.get_app_path(),
                                          reply_handler=unregister_cb,
                                          error_handler=unregister_error_cb)
        self.mainloop.run() # blocks until mainloop.quit() is called

        return self.unregister_successful


    @dbus.service.method(DBUS_OM_IFACE, out_signature='a{oa{sa{sv}}}')
    def GetManagedObjects(self):
        """Get the list of managed monitor objects.

        @returns: the list of managed objects and their properties.

        """
        logging.info('%s: GetManagedObjects', self.app_path)

        objects = dict()
        for monitor_id in self.monitors:
            monitor = self.monitors[monitor_id]
            objects[monitor.get_path()] = monitor.get_properties()

        return objects


    @dbus.service.signal(DBUS_OM_IFACE, signature='oa{sa{sv}}')
    def InterfacesAdded(self, object_path, interfaces_and_properties):
        """Emit the InterfacesAdded signal for a given monitor object.

        Invoking this method emits the InterfacesAdded signal,
        nothing needs to be done here.

        @param object_path: the dbus object path of a monitor.
        @param interfaces_and_properties: the monitor properties dictionary.

        """
        return


    @dbus.service.signal(DBUS_OM_IFACE, signature='oas')
    def InterfacesRemoved(self, object_path, interfaces):
        """Emit the InterfacesRemoved signal for a given monitor object.

        Invoking this method emits the InterfacesRemoved signal,
        nothing needs to be done here.

        @param object_path: the dbus object path of a monitor.
        @param interfaces: the list of monitor interfaces.

        """
        return


class AdvMonitorAppMgr():
    """The app manager for Advertisement Monitor Test Apps.

    This class manages instances of multiple advertisement monitor test
    applications.

    """

    # List of commands used by AdvMonitor AppMgr, AppMgr-helper process and
    # AdvMonitor Test Application for communication between each other.
    CMD_EXIT_HELPER = 0
    CMD_CREATE_APP = 1
    CMD_EXIT_APP = 2
    CMD_KILL_APP = 3
    CMD_REGISTER_APP = 4
    CMD_UNREGISTER_APP = 5
    CMD_ADD_MONITOR = 6
    CMD_REMOVE_MONITOR = 7
    CMD_GET_EVENT_COUNT = 8
    CMD_RESET_EVENT_COUNT = 9
    CMD_SET_TARGET_DEVICES = 10

    def __init__(self):
        """Construction of applications manager object."""

        # Due to a limitation of python, it is not possible to fork a new
        # process once any dbus connections are established. So, create a
        # helper process before making any dbus connections. This helper
        # process can be used to create more processes on demand.
        parent_conn, child_conn = Pipe()
        p = Process(target=self._appmgr_helper, args=(child_conn,))
        p.start()

        self._helper_proc = p
        self._helper_conn = parent_conn
        self.apps = []


    def _appmgr_helper(self, appmgr_conn):
        """AppMgr helper process.

        This process is used to create new instances of the AdvMonitor Test
        Application on demand and acts as a communication bridge between the
        AppMgr and test applications.

        @param appmgr_conn: an object of AppMgr connection pipe.

        """
        app_conns = dict()

        done = False
        while not done:
            cmd, app_id, data = appmgr_conn.recv()
            ret = None

            if cmd == self.CMD_EXIT_HELPER:
                # Terminate any outstanding test application instances before
                # exiting the helper process.
                for app_id in app_conns:
                    p, app_conn = app_conns[app_id]
                    if p.is_alive():
                        # Try to exit the app gracefully first, terminate if it
                        # doesn't work.
                        app_conn.send((self.CMD_EXIT_APP, None))
                        if not app_conn.recv() or p.is_alive():
                            p.terminate()
                            p.join() # wait for test app to terminate
                done = True
                ret = True

            elif cmd == self.CMD_CREATE_APP:
                if app_id not in app_conns:
                    parent_conn, child_conn = Pipe()
                    p = Process(target=self._testapp_main,
                                args=(child_conn, app_id,))
                    p.start()

                    app_conns[app_id] = (p, parent_conn)
                    ret = app_id

            elif cmd == self.CMD_KILL_APP:
                if app_id in app_conns:
                    p, _ = app_conns[app_id]
                    if p.is_alive():
                        p.terminate()
                        p.join() # wait for test app to terminate

                    app_conns.pop(app_id)
                    ret = not p.is_alive()

            else:
                if app_id in app_conns:
                    p, app_conn = app_conns[app_id]

                    app_conn.send((cmd, data))
                    ret = app_conn.recv()

                    if cmd == self.CMD_EXIT_APP:
                        p.join() # wait for test app to terminate

                        app_conns.pop(app_id)
                        ret = not p.is_alive()

            appmgr_conn.send(ret)


    def _testapp_main(self, helper_conn, app_id):
        """AdvMonitor Test Application Process.

        This process acts as a client application for AdvMonitor tests and used
        to host AdvMonitor dbus objects.

        @param helper_conn: an object of AppMgr-helper process connection pipe.
        @param app_id: the app id of this test app process.

        """
        # Initialize threads in GObject/dbus-glib before creating local threads.
        GObject.threads_init()
        dbus.mainloop.glib.threads_init()

        # Arrange for the GObject main loop to be the default.
        dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)

        def get_advmon_mgr(bus):
            """Finds the AdvMonitor Manager object exported by bluetoothd."""
            remote_om = dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, '/'),
                                       DBUS_OM_IFACE)
            objects = remote_om.GetManagedObjects()

            for o, props in objects.items():
                if ADV_MONITOR_MANAGER_IFACE in props:
                    return dbus.Interface(bus.get_object(BLUEZ_SERVICE_NAME, o),
                                          ADV_MONITOR_MANAGER_IFACE)
            return None

        bus = dbus.SystemBus()
        mainloop = GObject.MainLoop()
        advmon_mgr = get_advmon_mgr(bus)

        app = AdvMonitorApp(bus, mainloop, advmon_mgr, app_id)

        done = False
        while not done:
            cmd, data = helper_conn.recv()
            ret = None

            if cmd == self.CMD_EXIT_APP:
                done = True
                ret = True

            elif cmd == self.CMD_REGISTER_APP:
                ret = app.register_app()

            elif cmd == self.CMD_UNREGISTER_APP:
                ret = app.unregister_app()

            elif cmd == self.CMD_ADD_MONITOR:
                ret = app.add_monitor(data)

            elif cmd == self.CMD_REMOVE_MONITOR:
                ret = app.remove_monitor(data)

            elif cmd == self.CMD_GET_EVENT_COUNT:
                ret = app.get_event_count(*data)

            elif cmd == self.CMD_RESET_EVENT_COUNT:
                ret = app.reset_event_count(*data)

            elif cmd == self.CMD_SET_TARGET_DEVICES:
                ret = app.set_target_devices(*data)

            helper_conn.send(ret)


    def _send_to_helper(self, cmd, app_id=None, data=None):
        """Sends commands to the helper process.

        @param cmd: command number from the above set of CMD_* commands.
        @param app_id: the app id.
        @param data: the command data.

        @returns: outcome of the command returned by the helper process.

        """
        if not self._helper_proc.is_alive():
            return None

        self._helper_conn.send((cmd, app_id, data))
        return self._helper_conn.recv()


    def create_app(self):
        """Create an advertisement monitor app.

        @returns: app id, once the app is created.

        """
        app_id = 0
        while app_id in self.apps:
            app_id += 1

        self.apps.append(app_id)

        return self._send_to_helper(self.CMD_CREATE_APP, app_id)


    def exit_app(self, app_id):
        """Exit an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        self.apps.remove(app_id)

        return self._send_to_helper(self.CMD_EXIT_APP, app_id)


    def kill_app(self, app_id):
        """Kill an advertisement monitor app by sending SIGKILL.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        self.apps.remove(app_id)

        return self._send_to_helper(self.CMD_KILL_APP, app_id)


    def register_app(self, app_id):
        """Register an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        return self._send_to_helper(self.CMD_REGISTER_APP, app_id)


    def unregister_app(self, app_id):
        """Unregister an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        return self._send_to_helper(self.CMD_UNREGISTER_APP, app_id)


    def add_monitor(self, app_id, monitor_data):
        """Create a monitor object.

        @param app_id: the app id.
        @param monitor_data: the list containing monitor type, RSSI filter
                             values and patterns.

        @returns: monitor id, once the monitor is created, None otherwise.

        """
        if app_id not in self.apps:
            return None

        return self._send_to_helper(self.CMD_ADD_MONITOR, app_id, monitor_data)


    def remove_monitor(self, app_id, monitor_id):
        """Remove a monitor object based on the given monitor id.

        @param app_id: the app id.
        @param monitor_id: the monitor id.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        return self._send_to_helper(self.CMD_REMOVE_MONITOR, app_id, monitor_id)


    def get_event_count(self, app_id, monitor_id, event):
        """Read the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: count of the specific event or dict of counts of all events.

        """
        if app_id not in self.apps:
            return None

        return self._send_to_helper(self.CMD_GET_EVENT_COUNT, app_id,
                                    (monitor_id, event))


    def reset_event_count(self, app_id, monitor_id, event):
        """Reset the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        if app_id not in self.apps:
            return False

        return self._send_to_helper(self.CMD_RESET_EVENT_COUNT, app_id,
                                    (monitor_id, event))


    def set_target_devices(self, app_id, monitor_id, devices):
        """Set the target devices to the given monitor.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param devices: a list of devices in dbus object path

        @returns: True on success, False otherwise.
        """
        if app_id not in self.apps:
            return False

        self._send_to_helper(self.CMD_SET_TARGET_DEVICES, app_id,
                             (monitor_id, devices))
        return True

    def destroy(self):
        """Clean up the helper process and test app processes."""

        self._send_to_helper(self.CMD_EXIT_HELPER)

        if self._helper_proc.is_alive():
            self._helper_proc.terminate()
            self._helper_proc.join() # wait for helper process to terminate

        return not self._helper_proc.is_alive()
