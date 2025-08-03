# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Client class to access the Floss manager interface."""

from floss.pandora.floss import observer_base
from floss.pandora.floss import utils


class ManagerCallbacks:
    """Callbacks for the Manager Interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_callback.
    """

    def on_hci_device_changed(self, hci, present):
        """Hci device presence is updated.

        Args:
            hci: Hci interface number.
            present: Whether this hci interface is appearing or disappearing.
        """
        pass

    def on_hci_enabled_changed(self, hci, enabled):
        """Hci device is being enabled or disabled.

        Args:
            hci: Hci interface number.
            enabled: Whether this hci interface is being enabled or disabled.
        """
        pass


class FlossManagerClient(ManagerCallbacks):
    """Handles method calls to and callbacks from the Manager interface."""

    MGR_SERVICE = 'org.chromium.bluetooth.Manager'
    MGR_INTERFACE = 'org.chromium.bluetooth.Manager'
    MGR_OBJECT = '/org/chromium/bluetooth/Manager'

    # Exported callback interface and objects
    CB_EXPORTED_INTF = 'org.chromium.bluetooth.ManagerCallback'
    CB_EXPORTED_OBJ_NAME = 'test_manager_client'

    class AdaptersNotParseable(Exception):
        """An entry in the result of GetAvailableAdapters was not parseable."""
        pass

    class ExportedManagerCallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.ManagerCallback">
                <method name="OnHciDeviceChanged">
                    <arg type="i" name="hci" direction="in" />
                    <arg type="b" name="present" direction="in" />
                </method>
                <method name="OnHciEnabledChanged">
                    <arg type="i" name="hci" direction="in" />
                    <arg type="b" name="enabled" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self):
            """Construct exported callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnHciDeviceChanged(self, hci, present):
            """Handle device presence callbacks."""
            for observer in self.observers.values():
                observer.on_hci_device_changed(hci, present)

        def OnHciEnabledChanged(self, hci, enabled):
            """Handle device enabled callbacks."""
            for observer in self.observers.values():
                observer.on_hci_enabled_changed(hci, enabled)

    def __init__(self, bus):
        """Construct the client.

        Args:
            bus: DBus bus over which we'll establish connections.
        """
        self.bus = bus

        # We don't register callbacks by default. The client owner must call
        # register_callbacks to do so.
        self.callbacks = None

        # Initialize hci devices and their power states
        self.adapters = {}

    def __del__(self):
        """Destructor."""
        del self.callbacks

    @utils.glib_call(False)
    def has_proxy(self):
        """Checks whether manager proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to manager interface for method calls."""
        return self.bus.get(self.MGR_SERVICE, self.MGR_OBJECT)[self.MGR_INTERFACE]

    @utils.glib_call(False)
    def register_callbacks(self):
        """Registers manager callbacks for this client if one doesn't already exist."""
        # Callbacks already registered
        if self.callbacks:
            return True

        # Create and publish callbacks
        self.callbacks = self.ExportedManagerCallbacks()
        self.callbacks.add_observer('manager_client', self)
        objpath = utils.generate_dbus_cb_objpath(self.CB_EXPORTED_OBJ_NAME)
        self.bus.register_object(objpath, self.callbacks, None)

        # Register published callbacks with manager daemon
        self.proxy().RegisterCallback(objpath)

        return True

    @utils.glib_callback()
    def on_hci_device_changed(self, hci, present):
        """Handle device presence change."""
        if present:
            self.adapters[hci] = self.adapters.get(hci, False)
        elif hci in self.adapters:
            del self.adapters[hci]

    @utils.glib_callback()
    def on_hci_enabled_changed(self, hci, enabled):
        """Handle device enabled change."""
        self.adapters[hci] = enabled

    def get_default_adapter(self):
        """Get the default adapter in use by the manager."""
        # TODO(abps): The default adapter is hci0 until we support multiple
        #             adapters.
        return 0

    def has_default_adapter(self):
        """Checks whether the default adapter exists on this system."""
        return self.get_default_adapter() in self.adapters

    @utils.glib_call()
    def start(self, hci):
        """Start a specific adapter."""
        self.proxy().Start(hci)

    @utils.glib_call()
    def stop(self, hci):
        """Stop a specific adapter."""
        self.proxy().Stop(hci)

    @utils.glib_call(False)
    def get_adapter_enabled(self, hci):
        """Checks whether a specific adapter is enabled (i.e. started)."""
        return bool(self.proxy().GetAdapterEnabled(hci))

    @utils.glib_call(False)
    def get_floss_enabled(self):
        """Gets whether Floss is enabled."""
        return bool(self.proxy().GetFlossEnabled())

    @utils.glib_call()
    def set_floss_enabled(self, enabled):
        self.proxy().SetFlossEnabled(enabled)

    @utils.glib_call([])
    def get_available_adapters(self):
        """Gets a list of currently available adapters and if they are enabled."""
        all_adapters = []
        dbus_result = self.proxy().GetAvailableAdapters()

        for d in dbus_result:
            if 'hci_interface' in d and 'enabled' in d:
                all_adapters.append((int(d['hci_interface']), bool(d['enabled'])))
            else:
                raise FlossManagerClient.AdaptersNotParseable(f'Could not parse: {d}')

        # This function call overwrites any existing cached values of
        # self.adapters that we may have gotten from observers.
        self.adapters = {}
        for (hci, enabled) in all_adapters:
            self.adapters[hci] = enabled

        return all_adapters
