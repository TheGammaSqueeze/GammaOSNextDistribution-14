# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Construction of an Advertisement object from an advertisement data
dictionary.

Much of this module refers to the code of test/example-advertisement in
bluez project.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from gi.repository import GLib

# TODO(b/215715213) - Wait until ebuild runs as python3 to remove this try
try:
    import pydbus
except:
    pydbus = {}

import logging

LE_ADVERTISEMENT_IFACE = 'org.bluez.LEAdvertisement1'


def InvalidArgsException():
    return GLib.gerror_new_literal(0, 'org.freedesktop.DBus.Error.InvalidArgs',
                                   0)


class Advertisement:
    """An advertisement object."""
    def __init__(self, bus, advertisement_data):
        """Construction of an Advertisement object.

        @param bus: a dbus system bus.
        @param advertisement_data: advertisement data dictionary.
        """
        self._get_advertising_data(advertisement_data)

        # Register self on bus and hold object for unregister
        self.obj = bus.register_object(self.path, self, None)

    # D-Bus service definition (required by pydbus).
    dbus = """
    <node>
        <interface name="org.bluez.LEAdvertisement1">
            <method name="Release" />
        </interface>
        <interface name="org.freedesktop.DBus.Properties">
            <method name="Set">
                <arg type="s" name="interface" direction="in" />
                <arg type="s" name="prop" direction="in" />
                <arg type="v" name="value" direction="in" />
            </method>
            <method name="GetAll">
                <arg type="s" name="interface" direction="in" />
                <arg type="a{sv}" name="properties" direction="out" />
            </method>
        </interface>
    </node>
    """

    def unregister(self):
        """Unregister self from bus."""
        self.obj.unregister()

    def _get_advertising_data(self, advertisement_data):
        """Get advertising data from the advertisement_data dictionary.

        @param bus: a dbus system bus.

        """
        self.path = advertisement_data.get('Path')
        self.type = advertisement_data.get('Type')
        self.service_uuids = advertisement_data.get('ServiceUUIDs', [])
        self.solicit_uuids = advertisement_data.get('SolicitUUIDs', [])

        # The xmlrpclib library requires that only string keys are allowed in
        # python dictionary. Hence, we need to define the manufacturer data
        # in an advertisement dictionary like
        #    'ManufacturerData': {'0xff00': [0xa1, 0xa2, 0xa3, 0xa4, 0xa5]},
        # in order to let autotest server transmit the advertisement to
        # a client DUT for testing.
        # On the other hand, the dbus method of advertising requires that
        # the signature of the manufacturer data to be 'qv' where 'q' stands
        # for unsigned 16-bit integer. Hence, we need to convert the key
        # from a string, e.g., '0xff00', to its hex value, 0xff00.
        # For signatures of the advertising properties, refer to
        #     device_properties in src/third_party/bluez/src/device.c
        # For explanation about signature types, refer to
        #     https://dbus.freedesktop.org/doc/dbus-specification.html
        self.manufacturer_data = {}  # Signature = a{qv}
        manufacturer_data = advertisement_data.get('ManufacturerData', {})
        for key, value in manufacturer_data.items():
            self.manufacturer_data[int(key, 16)] = GLib.Variant('ay', value)

        self.service_data = {}  # Signature = a{sv}
        service_data = advertisement_data.get('ServiceData', {})
        for uuid, data in service_data.items():
            self.service_data[uuid] = GLib.Variant('ay', data)

        self.include_tx_power = advertisement_data.get('IncludeTxPower')

        self.discoverable = advertisement_data.get('Discoverable')

        self.scan_response = advertisement_data.get('ScanResponseData')

        self.min_interval = advertisement_data.get('MinInterval')
        self.max_interval = advertisement_data.get('MaxInterval')

        self.tx_power = advertisement_data.get('TxPower')

    def get_path(self):
        """Get the dbus object path of the advertisement.

        @returns: the advertisement object path.

        """
        return self.path

    def Set(self, interface, prop, value):
        """Called when bluetoothd Sets a property on our advertising object

        @param interface: String interface, i.e. org.bluez.LEAdvertisement1
        @param prop: String name of the property being set
        @param value: Value of the property being set
        """
        logging.info('Setting prop {} value to {}'.format(prop, value))

        if prop == 'TxPower':
            self.tx_power = value

    def GetAll(self, interface):
        """Get the properties dictionary of the advertisement.

        @param interface: the bluetooth dbus interface.

        @returns: the advertisement properties dictionary.

        """
        if interface != LE_ADVERTISEMENT_IFACE:
            raise InvalidArgsException()

        properties = dict()
        properties['Type'] = GLib.Variant('s', self.type)

        if self.service_uuids is not None:
            properties['ServiceUUIDs'] = GLib.Variant('as', self.service_uuids)
        if self.solicit_uuids is not None:
            properties['SolicitUUIDs'] = GLib.Variant('as', self.solicit_uuids)
        if self.manufacturer_data is not None:
            properties['ManufacturerData'] = GLib.Variant(
                    'a{qv}', self.manufacturer_data)

        if self.service_data is not None:
            properties['ServiceData'] = GLib.Variant('a{sv}',
                                                     self.service_data)
        if self.discoverable is not None:
            properties['Discoverable'] = GLib.Variant('b', self.discoverable)

        if self.include_tx_power is not None:
            properties['IncludeTxPower'] = GLib.Variant(
                    'b', self.include_tx_power)

        # Note here: Scan response data is an int (tag) -> array (value) mapping
        # but autotest's xmlrpc server can only accept string keys. For this
        # reason, the scan response key is encoded as a hex string, and then
        # re-mapped here before the advertisement is registered.
        if self.scan_response is not None:
            scan_rsp = {}
            for key, value in self.scan_response.items():
                scan_rsp[int(key, 16)] = GLib.Variant('ay', value)

            properties['ScanResponseData'] = GLib.Variant('a{yv}', scan_rsp)

        if self.min_interval is not None:
            properties['MinInterval'] = GLib.Variant('u', self.min_interval)

        if self.max_interval is not None:
            properties['MaxInterval'] = GLib.Variant('u', self.max_interval)

        if self.tx_power is not None:
            properties['TxPower'] = GLib.Variant('n', self.tx_power)

        return properties

    def Release(self):
        """The method callback at release."""
        logging.info('%s: Advertisement Release() called.', self.path)


def example_advertisement(bus):
    """A demo example of creating an Advertisement object.

    @param bus: a dbus system bus.
    @returns: the Advertisement object.

    """
    ADVERTISEMENT_DATA = {
            'Path': '/org/bluez/test/advertisement1',

            # Could be 'central' or 'peripheral'.
            'Type': 'peripheral',

            # Refer to the specification for a list of service assigned numbers:
            # https://www.bluetooth.com/specifications/gatt/services
            # e.g., 180D represents "Heart Reate" service, and
            #       180F "Battery Service".
            'ServiceUUIDs': ['180D', '180F'],

            # Service solicitation UUIDs.
            'SolicitUUIDs': [],

            # Two bytes of manufacturer id followed by manufacturer specific data.
            'ManufacturerData': {
                    '0xff00': [0xa1, 0xa2, 0xa3, 0xa4, 0xa5]
            },

            # service UUID followed by additional service data.
            'ServiceData': {
                    '9999': [0x10, 0x20, 0x30, 0x40, 0x50]
            },

            # Does it include transmit power level?
            'IncludeTxPower': True
    }

    return Advertisement(bus, ADVERTISEMENT_DATA)


if __name__ == '__main__':
    bus = pydbus.SystemBus()
    adv = example_advertisement(bus)
    print(adv.GetAll(LE_ADVERTISEMENT_IFACE))
