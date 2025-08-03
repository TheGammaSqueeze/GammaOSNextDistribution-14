# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Client class to access the Floss manager interface."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import math
import random

from autotest_lib.client.cros.bluetooth.floss.observer_base import ObserverBase
from autotest_lib.client.cros.bluetooth.floss.utils import glib_call, glib_callback


class ManagerCallbacks:
    """Callbacks for the Manager Interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_callback.
    """
    def on_hci_device_changed(self, hci, present):
        """Hci device presence is updated.

        @param hci: Hci interface number.
        @param present: Whether this hci interface is appearing or disappearing.
        """
        pass

    def on_hci_enabled_changed(self, hci, enabled):
        """Hci device is being enabled or disabled.

        @param hci: Hci interface number.
        @param enabled: Whether this hci interface is being enabled or disabled.
        """
        pass


class FlossManagerClient(ManagerCallbacks):
    """ Handles method calls to and callbacks from the Manager interface."""

    MGR_SERVICE = 'org.chromium.bluetooth.Manager'
    MGR_INTERFACE = 'org.chromium.bluetooth.Manager'
    MGR_OBJECT = '/org/chromium/bluetooth/Manager'

    # Exported callback interface and objects
    CB_EXPORTED_INTF = 'org.chromium.bluetooth.ManagerCallbacks'
    CB_EXPORTED_OBJ = '/org/chromium/bluetooth/test_manager_client{}'

    class AdaptersNotParseable(Exception):
        """An entry in the result of GetAvailableAdapters was not parseable."""
        pass

    class ExportedManagerCallbacks(ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.ManagerCallbacks">
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
            """Construct exported callbacks object.
            """
            ObserverBase.__init__(self)

        def OnHciDeviceChanged(self, hci, present):
            """Handle device presence callbacks."""
            for observer in self.observers.values():
                observer.on_hci_device_changed(hci, present)

        def OnHciEnabledChanged(self, hci, enabled):
            """Handle device enabled callbacks."""
            for observer in self.observers.values():
                observer.on_hci_enabled_changed(hci, enabled)

    def __init__(self, bus):
        """ Construct the client.

        @param bus: DBus bus over which we'll establish connections.
        """
        self.bus = bus

        # We don't register callbacks by default. The client owner must call
        # register_callbacks to do so.
        self.callbacks = None

        # Initialize hci devices and their power states
        self.adapters = {}

    def __del__(self):
        """Destructor"""
        del self.callbacks

    @glib_call(False)
    def has_proxy(self):
        """Checks whether manager proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to manager interface for method calls."""
        return self.bus.get(self.MGR_SERVICE,
                            self.MGR_OBJECT)[self.MGR_INTERFACE]

    @glib_call(False)
    def register_callbacks(self):
        """Registers manager callbacks for this client if one doesn't already exist.
        """
        # Callbacks already registered
        if self.callbacks:
            return True

        # Generate a random number between 1-1000
        rnumber = math.floor(random.random() * 1000 + 1)

        # Create and publish callbacks
        self.callbacks = self.ExportedManagerCallbacks()
        self.callbacks.add_observer('manager_client', self)
        objpath = self.CB_EXPORTED_OBJ.format(rnumber)
        self.bus.register_object(objpath, self.callbacks, None)

        # Register published callbacks with manager daemon
        self.proxy().RegisterCallback(objpath)

        return True

    @glib_callback()
    def on_hci_device_changed(self, hci, present):
        """Handle device presence change."""
        if present:
            self.adapters[hci] = self.adapters.get(hci, False)
        elif hci in self.adapters:
            del self.adapters[hci]

    @glib_callback()
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

    @glib_call()
    def start(self, hci):
        """Start a specific adapter."""
        self.proxy().Start(hci)

    @glib_call()
    def stop(self, hci):
        """Stop a specific adapter."""
        self.proxy().Stop(hci)

    @glib_call(False)
    def get_adapter_enabled(self, hci):
        """Checks whether a specific adapter is enabled (i.e. started)."""
        return bool(self.proxy().GetAdapterEnabled(hci))

    @glib_call(False)
    def get_floss_enabled(self):
        """Gets whether Floss is enabled."""
        return bool(self.proxy().GetFlossEnabled())

    @glib_call()
    def set_floss_enabled(self, enabled):
        self.proxy().SetFlossEnabled(enabled)

    @glib_call([])
    def get_available_adapters(self):
        """Gets a list of currently available adapters and if they are enabled.
        """
        all_adapters = []
        dbus_result = self.proxy().GetAvailableAdapters()

        for d in dbus_result:
            if 'hci_interface' in d and 'enabled' in d:
                all_adapters.append(
                        (int(d['hci_interface']), bool(d['enabled'])))
            else:
                raise FlossManagerClient.AdaptersNotParseable(
                        'Could not parse: {}', str(d))

        # This function call overwrites any existing cached values of
        # self.adapters that we may have gotten from observers.
        self.adapters = {}
        for (hci, enabled) in all_adapters:
            self.adapters[hci] = enabled

        return all_adapters
