#!/usr/bin/python3

#%%

import dbus
import dbus.service
import dbus.mainloop.glib
import time
import unittest

HCI_DEVICES_DIR = "/sys/class/bluetooth"

# File to store the Bluetooth daemon to use (bluez or floss)
BLUETOOTH_DAEMON_CURRENT = "/var/lib/misc/bluetooth-daemon.current"

# File to store the config for BluetoothManager
BTMANAGERD_CONF = "/var/lib/bluetooth/btmanagerd.json"

# D-BUS bus name
BUS_NAME = "org.chromium.bluetooth.Manager"

# D-Bus Bluetooth Manager object path
MANAGER_OBJECT_PATH = "/org/chromium/bluetooth/Manager"

# D-Bus Bluetooth Manager interface name
MANAGER_INTERFACE_NAME = "org.chromium.bluetooth.Manager"

# D-Bus Bluetooth Manager client (this test) object path
CLIENT_OBJECT_PATH = "/test_client"


def use_floss():
  with open(BLUETOOTH_DAEMON_CURRENT, "w") as f:
    f.write("floss")

def use_bluez():
  with open(BLUETOOTH_DAEMON_CURRENT, "w") as f:
    f.write("floss")

class BtmanagerdTest(unittest.TestCase):

    def setUp(self):
      self.bus = dbus.SystemBus()
      self.manager_object = self.bus.get_object(BUS_NAME, MANAGER_OBJECT_PATH)
      self.client_object = self.bus.get_object(BUS_NAME, CLIENT_OBJECT_PATH)
      self.manager_object.Stop(0, dbus_interface=MANAGER_INTERFACE_NAME)
      time.sleep(2.5)
      self.assertEqual(self._get_state(), 0)

    def _start_hci(self, hci=0):
      self.manager_object.Start(hci, dbus_interface=MANAGER_INTERFACE_NAME)
      time.sleep(2.5)

    def _stop_hci(self, hci=0):
      self.manager_object.Stop(hci, dbus_interface=MANAGER_INTERFACE_NAME)
      time.sleep(2.5)

    def _get_state(self):
      return self.manager_object.GetState(dbus_interface=MANAGER_INTERFACE_NAME)

    def _register_hci_device_change_observer(self):
      return self.manager_object.RegisterStateChangeObserver(self.client_object_path, dbus_interface=MANAGER_INTERFACE_NAME)

    def _unregister_hci_device_change_observer(self):
      return self.manager_object.UnregisterHciDeviceChangeObserver(self.client_object_path, dbus_interface=MANAGER_INTERFACE_NAME)

    def _get_floss_enabled(self):
      return self.manager_object.GetFlossEnabled(dbus_interface=MANAGER_INTERFACE_NAME)

    def _set_floss_enabled(self, enabled=True):
      return self.manager_object.SetFlossEnabled(enabled, dbus_interface=MANAGER_INTERFACE_NAME)

    def _list_hci_devices(self):
      return self.manager_object.ListHciDevices(dbus_interface=MANAGER_INTERFACE_NAME)

    def _register_hci_device_change_observer(self):
      return self.manager_object.RegisterHciDeviceChangeObserver(self.client_object_path, dbus_interface=MANAGER_INTERFACE_NAME)

    def _unregister_hci_device_change_observer(self):
      return self.manager_object.UnregisterHciDeviceChangeObserver(self.client_object_path, dbus_interface=MANAGER_INTERFACE_NAME)

    def test_list_hci_devices(self):
      self.assertTrue(len(self._list_hci_devices()) > 0)

    def test_floss_enabled(self):
      self.assertTrue(self._get_floss_enabled())

    def test_disable_floss(self):
      self._set_floss_enabled(False)
      self.assertFalse(self._get_floss_enabled())
      with open(BLUETOOTH_DAEMON_CURRENT, "r") as f:
        self.assertEqual(f.read(), "bluez")

      self._set_floss_enabled(True)
      self.assertTrue(self._get_floss_enabled())

    def test_start(self):
      self._start_hci()
      self.assertEqual(self._get_state(), 2)

    def test_astart_and_start(self):
      self._start_hci()
      self._start_hci()
      self.assertEqual(self._get_state(), 2)

    def test_stop(self):
      self._start_hci()
      self._stop_hci()
      self.assertEqual(self._get_state(), 0)

    def test_stop_and_stop(self):
      self._start_hci()
      self._stop_hci()
      self._stop_hci()
      self.assertEqual(self._get_state(), 0)


# %%
if __name__ == '__main__':
  dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
  use_floss()
  unittest.main()
