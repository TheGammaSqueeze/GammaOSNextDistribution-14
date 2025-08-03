#!/usr/bin/env python3
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import enum
import sys


def _import_str_enum():
    # StrEnum is only introduced in Python 3.11
    if sys.version_info >= (3, 11):
        from enum import StrEnum
        return StrEnum
    else:
        from typing import Type, TypeVar
        _T = TypeVar("_T")

        class StrEnumInternal(str, enum.Enum):
            pass

        return StrEnumInternal


StrEnum = _import_str_enum()


# Gatt Callback error messages
class GattCallbackError(StrEnum):
    CHAR_WRITE_REQ_ERR = "Characteristic Write Request event not found. Expected {}"
    CHAR_WRITE_ERR = "Characteristic Write event not found. Expected {}"
    DESC_WRITE_REQ_ERR = "Descriptor Write Request event not found. Expected {}"
    DESC_WRITE_ERR = "Descriptor Write event not found. Expected {}"
    CHAR_READ_ERR = "Characteristic Read event not found. Expected {}"
    CHAR_READ_REQ_ERR = "Characteristic Read Request not found. Expected {}"
    DESC_READ_ERR = "Descriptor Read event not found. Expected {}"
    DESC_READ_REQ_ERR = "Descriptor Read Request event not found. Expected {}"
    RD_REMOTE_RSSI_ERR = "Read Remote RSSI event not found. Expected {}"
    GATT_SERV_DISC_ERR = "GATT Services Discovered event not found. Expected {}"
    SERV_ADDED_ERR = "Service Added event not found. Expected {}"
    MTU_CHANGED_ERR = "MTU Changed event not found. Expected {}"
    MTU_SERV_CHANGED_ERR = "MTU Server Changed event not found. Expected {}"
    GATT_CONN_CHANGE_ERR = "GATT Connection Changed event not found. Expected {}"
    CHAR_CHANGE_ERR = "GATT Characteristic Changed event not fond. Expected {}"
    PHY_READ_ERR = "Phy Read event not fond. Expected {}"
    PHY_UPDATE_ERR = "Phy Update event not fond. Expected {}"
    EXEC_WRITE_ERR = "GATT Execute Write event not found. Expected {}"


# GATT callback strings as defined in GattClientFacade.java and
# GattServerFacade.java implemented callbacks.
class GattCallbackString(StrEnum):
    CHAR_WRITE_REQ = "GattServer{}onCharacteristicWriteRequest"
    EXEC_WRITE = "GattServer{}onExecuteWrite"
    CHAR_WRITE = "GattConnect{}onCharacteristicWrite"
    DESC_WRITE_REQ = "GattServer{}onDescriptorWriteRequest"
    DESC_WRITE = "GattConnect{}onDescriptorWrite"
    CHAR_READ = "GattConnect{}onCharacteristicRead"
    CHAR_READ_REQ = "GattServer{}onCharacteristicReadRequest"
    DESC_READ = "GattConnect{}onDescriptorRead"
    DESC_READ_REQ = "GattServer{}onDescriptorReadRequest"
    RD_REMOTE_RSSI = "GattConnect{}onReadRemoteRssi"
    GATT_SERV_DISC = "GattConnect{}onServicesDiscovered"
    SERV_ADDED = "GattServer{}onServiceAdded"
    MTU_CHANGED = "GattConnect{}onMtuChanged"
    MTU_SERV_CHANGED = "GattServer{}onMtuChanged"
    GATT_CONN_CHANGE = "GattConnect{}onConnectionStateChange"
    CHAR_CHANGE = "GattConnect{}onCharacteristicChanged"
    PHY_READ = "GattConnect{}onPhyRead"
    PHY_UPDATE = "GattConnect{}onPhyUpdate"
    SERV_PHY_READ = "GattServer{}onPhyRead"
    SERV_PHY_UPDATE = "GattServer{}onPhyUpdate"


# yapf: disable
# GATT event dictionary of expected callbacks and errors.
class GattEvent(dict, enum.Enum):

    def __getitem__(self, item):
        return self._value_[item]

    CHAR_WRITE_REQ = {
            "evt": GattCallbackString.CHAR_WRITE_REQ,
            "err": GattCallbackError.CHAR_WRITE_REQ_ERR
    }
    EXEC_WRITE = {
            "evt": GattCallbackString.EXEC_WRITE,
            "err": GattCallbackError.EXEC_WRITE_ERR
    }
    CHAR_WRITE = {
            "evt": GattCallbackString.CHAR_WRITE,
            "err": GattCallbackError.CHAR_WRITE_ERR
    }
    DESC_WRITE_REQ = {
            "evt": GattCallbackString.DESC_WRITE_REQ,
            "err": GattCallbackError.DESC_WRITE_REQ_ERR
    }
    DESC_WRITE = {
            "evt": GattCallbackString.DESC_WRITE,
            "err": GattCallbackError.DESC_WRITE_ERR
    }
    CHAR_READ = {
            "evt": GattCallbackString.CHAR_READ,
            "err": GattCallbackError.CHAR_READ_ERR
    }
    CHAR_READ_REQ = {
            "evt": GattCallbackString.CHAR_READ_REQ,
            "err": GattCallbackError.CHAR_READ_REQ_ERR
    }
    DESC_READ = {
            "evt": GattCallbackString.DESC_READ,
            "err": GattCallbackError.DESC_READ_ERR
    }
    DESC_READ_REQ = {
            "evt": GattCallbackString.DESC_READ_REQ,
            "err": GattCallbackError.DESC_READ_REQ_ERR
    }
    RD_REMOTE_RSSI = {
            "evt": GattCallbackString.RD_REMOTE_RSSI,
            "err": GattCallbackError.RD_REMOTE_RSSI_ERR
    }
    GATT_SERV_DISC = {
            "evt": GattCallbackString.GATT_SERV_DISC,
            "err": GattCallbackError.GATT_SERV_DISC_ERR
    }
    SERV_ADDED = {
            "evt": GattCallbackString.SERV_ADDED,
            "err": GattCallbackError.SERV_ADDED_ERR
    }
    MTU_CHANGED = {
            "evt": GattCallbackString.MTU_CHANGED,
            "err": GattCallbackError.MTU_CHANGED_ERR
    }
    GATT_CONN_CHANGE = {
            "evt": GattCallbackString.GATT_CONN_CHANGE,
            "err": GattCallbackError.GATT_CONN_CHANGE_ERR
    }
    CHAR_CHANGE = {
            "evt": GattCallbackString.CHAR_CHANGE,
            "err": GattCallbackError.CHAR_CHANGE_ERR
    }
    PHY_READ = {
            "evt": GattCallbackString.PHY_READ,
            "err": GattCallbackError.PHY_READ_ERR
    }
    PHY_UPDATE = {
            "evt": GattCallbackString.PHY_UPDATE,
            "err": GattCallbackError.PHY_UPDATE_ERR
    }
    SERV_PHY_READ = {
            "evt": GattCallbackString.SERV_PHY_READ,
            "err": GattCallbackError.PHY_READ_ERR
    }
    SERV_PHY_UPDATE = {
            "evt": GattCallbackString.SERV_PHY_UPDATE,
            "err": GattCallbackError.PHY_UPDATE_ERR
    }
# yapf: enable


# Matches constants of connection states defined in BluetoothGatt.java
class GattConnectionState(enum.IntEnum):
    STATE_DISCONNECTED = 0
    STATE_CONNECTING = 1
    STATE_CONNECTED = 2
    STATE_DISCONNECTING = 3


# Matches constants of Bluetooth GATT Characteristic values as defined
# in BluetoothGattCharacteristic.java
class GattCharacteristic(enum.IntEnum):
    PROPERTY_BROADCAST = 0x01
    PROPERTY_READ = 0x02
    PROPERTY_WRITE_NO_RESPONSE = 0x04
    PROPERTY_WRITE = 0x08
    PROPERTY_NOTIFY = 0x10
    PROPERTY_INDICATE = 0x20
    PROPERTY_SIGNED_WRITE = 0x40
    PROPERTY_EXTENDED_PROPS = 0x80
    PERMISSION_READ = 0x01
    PERMISSION_READ_ENCRYPTED = 0x02
    PERMISSION_READ_ENCRYPTED_MITM = 0x04
    PERMISSION_WRITE = 0x10
    PERMISSION_WRITE_ENCRYPTED = 0x20
    PERMISSION_WRITE_ENCRYPTED_MITM = 0x40
    PERMISSION_WRITE_SIGNED = 0x80
    PERMISSION_WRITE_SIGNED_MITM = 0x100
    WRITE_TYPE_DEFAULT = 0x02
    WRITE_TYPE_NO_RESPONSE = 0x01
    WRITE_TYPE_SIGNED = 0x04
    FORMAT_UINT8 = 0x11
    FORMAT_UINT16 = 0x12
    FORMAT_UINT32 = 0x14
    FORMAT_SINT8 = 0x21
    FORMAT_SINT16 = 0x22
    FORMAT_SINT32 = 0x24
    FORMAT_SFLOAT = 0x32
    FORMAT_FLOAT = 0x34


# Matches constants of Bluetooth GATT Characteristic values as defined
# in BluetoothGattDescriptor.java
class GattDescriptor(list, enum.Enum):

    def __getitem__(self, item):
        return self._value_[item]

    ENABLE_NOTIFICATION_VALUE = [0x01, 0x00]
    ENABLE_INDICATION_VALUE = [0x02, 0x00]
    DISABLE_NOTIFICATION_VALUE = [0x00, 0x00]
    PERMISSION_READ = [0x01]
    PERMISSION_READ_ENCRYPTED = [0x02]
    PERMISSION_READ_ENCRYPTED_MITM = [0x04]
    PERMISSION_WRITE = [0x10]
    PERMISSION_WRITE_ENCRYPTED = [0x20]
    PERMISSION_WRITE_ENCRYPTED_MITM = [0x40]
    PERMISSION_WRITE_SIGNED = [0x80]
    PERMISSION_WRITE_SIGNED_MITM = [0x100]


# https://www.bluetooth.com/specifications/gatt/descriptors
class GattCharDesc(StrEnum):
    GATT_CHARAC_EXT_PROPER_UUID = '00002900-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_USER_DESC_UUID = '00002901-0000-1000-8000-00805f9b34fb'
    GATT_CLIENT_CHARAC_CFG_UUID = '00002902-0000-1000-8000-00805f9b34fb'
    GATT_SERVER_CHARAC_CFG_UUID = '00002903-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_FMT_UUID = '00002904-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_AGREG_FMT_UUID = '00002905-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_VALID_RANGE_UUID = '00002906-0000-1000-8000-00805f9b34fb'
    GATT_EXTERNAL_REPORT_REFERENCE = '00002907-0000-1000-8000-00805f9b34fb'
    GATT_REPORT_REFERENCE = '00002908-0000-1000-8000-00805f9b34fb'


# https://www.bluetooth.com/specifications/gatt/characteristics
class GattCharTypes(StrEnum):
    GATT_CHARAC_DEVICE_NAME = '00002a00-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_APPEARANCE = '00002a01-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_PERIPHERAL_PRIV_FLAG = '00002a02-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_RECONNECTION_ADDRESS = '00002a03-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_PERIPHERAL_PREF_CONN = '00002a04-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_SERVICE_CHANGED = '00002a05-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_SYSTEM_ID = '00002a23-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_MODEL_NUMBER_STRING = '00002a24-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_SERIAL_NUMBER_STRING = '00002a25-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_FIRMWARE_REVISION_STRING = '00002a26-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_HARDWARE_REVISION_STRING = '00002a27-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_SOFTWARE_REVISION_STRING = '00002a28-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_MANUFACTURER_NAME_STRING = '00002a29-0000-1000-8000-00805f9b34fb'
    GATT_CHARAC_PNP_ID = '00002a50-0000-1000-8000-00805f9b34fb'


# Matches constants of Bluetooth GATT Characteristic values as defined
# in BluetoothGattCharacteristic.java
class CharacteristicValueFormat(enum.Enum):
    STRING = 0x1
    BYTE = 0x2
    FORMAT_SINT8 = 0x21
    FORMAT_UINT8 = 0x11
    FORMAT_SINT16 = 0x22
    FORMAT_UINT16 = 0x12
    FORMAT_SINT32 = 0x24
    FORMAT_UINT32 = 0x14


# Matches constants of Bluetooth Gatt Service types as defined in
# BluetoothGattService.java
class GattServiceType(enum.IntEnum):
    SERVICE_TYPE_PRIMARY = 0
    SERVICE_TYPE_SECONDARY = 1


# Matches constants of Bluetooth Gatt Connection Priority values as defined in
# BluetoothGatt.java
class GattConnectionPriority(enum.IntEnum):
    CONNECTION_PRIORITY_BALANCED = 0
    CONNECTION_PRIORITY_HIGH = 1
    CONNECTION_PRIORITY_LOW_POWER = 2


# Min and max MTU values
class GattMtuSize(enum.IntEnum):
    MIN = 23
    MAX = 217


# Gatt Characteristic attribute lengths
class GattCharacteristicAttrLength(enum.IntEnum):
    MTU_ATTR_1 = 1
    MTU_ATTR_2 = 3
    MTU_ATTR_3 = 15


# Matches constants of Bluetooth Gatt operations status as defined in
# BluetoothGatt.java
class BluetoothGatt(enum.IntEnum):
    GATT_SUCCESS = 0
    GATT_FAILURE = 0x101


# Matches constants of Bluetooth transport values as defined in
# BluetoothDevice.java
class GattTransport(enum.IntEnum):
    TRANSPORT_AUTO = 0x00
    TRANSPORT_BREDR = 0x01
    TRANSPORT_LE = 0x02


# Matches constants of Bluetooth physical channeling values as defined in
# BluetoothDevice.java
class GattPhy(enum.IntEnum):
    PHY_LE_1M = 1
    PHY_LE_2M = 2
    PHY_LE_CODED = 3


# Matches constants of Bluetooth physical channeling bitmask values as defined
# in BluetoothDevice.java
class GattPhyMask(enum.IntEnum):
    PHY_LE_1M_MASK = 1
    PHY_LE_2M_MASK = 2
    PHY_LE_CODED_MASK = 4


# Values as defined in the Bluetooth GATT specification
GattServerResponses = {
    "GATT_SUCCESS": 0x0,
    "GATT_FAILURE": 0x1,
    "GATT_READ_NOT_PERMITTED": 0x2,
    "GATT_WRITE_NOT_PERMITTED": 0x3,
    "GATT_INVALID_PDU": 0x4,
    "GATT_INSUFFICIENT_AUTHENTICATION": 0x5,
    "GATT_REQUEST_NOT_SUPPORTED": 0x6,
    "GATT_INVALID_OFFSET": 0x7,
    "GATT_INSUFFICIENT_AUTHORIZATION": 0x8,
    "GATT_INVALID_ATTRIBUTE_LENGTH": 0xd,
    "GATT_INSUFFICIENT_ENCRYPTION": 0xf,
    "GATT_CONNECTION_CONGESTED": 0x8f,
    "GATT_13_ERR": 0x13,
    "GATT_12_ERR": 0x12,
    "GATT_0C_ERR": 0x0C,
    "GATT_16": 0x16
}
