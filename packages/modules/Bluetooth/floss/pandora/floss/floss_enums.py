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
"""Class to hold the Floss enums."""

import enum


class BtTransport(enum.IntEnum):
    """Bluetooth transport type."""
    AUTO = 0
    BREDR = 1
    LE = 2
    DUAL = 3


class GattWriteRequestStatus(enum.IntEnum):
    """Gatt write request status."""
    SUCCESS = 0
    FAIL = 1
    BUSY = 2


class GattWriteType(enum.IntEnum):
    """GATT write type."""
    INVALID = 0
    WRITE_NO_RSP = 1
    WRITE = 2
    WRITE_PREPARE = 3


class LePhy(enum.IntEnum):
    """Bluetooth LE physical type."""
    INVALID = 0
    PHY1M = 1
    PHY2M = 2
    PHY_CODED = 3


class GattStatus(enum.IntEnum):
    """Bluetooth GATT return status."""
    SUCCESS = 0x00
    INVALID_HANDLE = 0x01
    READ_NOT_PERMIT = 0x02
    WRITE_NOT_PERMIT = 0x03
    INVALID_PDU = 0x04
    INSUF_AUTHENTICATION = 0x05
    REQ_NOT_SUPPORTED = 0x06
    INVALID_OFFSET = 0x07
    INSUF_AUTHORIZATION = 0x08
    PREPARE_Q_FULL = 0x09
    NOT_FOUND = 0x0A
    NOT_LONG = 0x0B
    INSUF_KEY_SIZE = 0x0C
    INVALID_ATTRLEN = 0x0D
    ERR_UNLIKELY = 0x0E
    INSUF_ENCRYPTION = 0x0F
    UNSUPPORT_GRP_TYPE = 0x10
    INSUF_RESOURCE = 0x11
    DATABASE_OUT_OF_SYNC = 0x12
    VALUE_NOT_ALLOWED = 0x13
    ILLEGAL_PARAMETER = 0x87
    TOO_SHORT = 0x7F
    NO_RESOURCES = 0x80
    INTERNAL_ERROR = 0x81
    WRONG_STATE = 0x82
    DB_FULL = 0x83
    BUSY = 0x84
    ERROR = 0x85
    CMD_STARTED = 0x86
    PENDING = 0x88
    AUTH_FAIL = 0x89
    MORE = 0x8A
    INVALID_CFG = 0x8B
    SERVICE_STARTED = 0x8C
    ENCRYPTED_NO_MITM = 0x8D
    NOT_ENCRYPTED = 0x8E
    CONGESTED = 0x8F
    DUP_REG = 0x90
    ALREADY_OPEN = 0x91
    CANCEL = 0x92


class BtStatus(enum.IntEnum):
    """Bluetooth return status."""
    SUCCESS = 0
    FAIL = 1
    NOT_READY = 2
    NO_MEMORY = 3
    BUSY = 4
    DONE = 5
    UNSUPPORTED = 6
    INVALID_PARAM = 7
    UNHANDLED = 8
    AUTH_FAILURE = 9
    REMOTE_DEVICE_DOWN = 10
    AUTH_REJECTED = 11
    JNI_ENVIRONMENT_ERROR = 12
    JNI_THREAD_ATTACH_ERROR = 13
    WAKE_LOCK_ERROR = 14


class SocketType(enum.IntEnum):
    """Socket types."""
    GT_SOCK_ANY = 0
    GT_SOCK_STREAM = 1
    GT_SOCK_DGRAM = 2
    GT_SOCK_RAW = 3
    GT_SOCK_RDM = 4
    GT_SOCK_SEQPACKET = 5
    GT_SOCK_DCCP = 6
    GT_SOCK_PACKET = 10


class SuspendMode(enum.IntEnum):
    """Bluetooth suspend mode."""
    NORMAL = 0
    SUSPENDING = 1
    SUSPENDED = 2
    RESUMING = 3


class ScanType(enum.IntEnum):
    """Bluetooth scan type."""
    ACTIVE = 0
    PASSIVE = 1


class BondState(enum.IntEnum):
    """Bluetooth bonding state."""
    NOT_BONDED = 0
    BONDING = 1
    BONDED = 2


class BtPropertyType(enum.IntEnum):
    """Bluetooth's property type."""
    BdName = 0x1
    BdAddr = 0x2
    Uuids = 0x3
    ClassOfDevice = 0x4
    TypeOfDevice = 0x5
    ServiceRecord = 0x6
    AdapterScanMode = 0x7
    AdapterBondedDevices = 0x8
    AdapterDiscoverableTimeout = 0x9
    RemoteFriendlyName = 0xA
    RemoteRssi = 0xB
    RemoteVersionInfo = 0xC
    LocalLeFeatures = 0xD
    LocalIoCaps = 0xE
    LocalIoCapsBle = 0xF
    DynamicAudioBuffer = 0x10
    RemoteIsCoordinatedSetMember = 0x11
    Appearance = 0x12
    VendorProductInfo = 0x13
    # Unimplemented:
    #  BT_PROPERTY_WL_MEDIA_PLAYERS_LIST,
    #  BT_PROPERTY_REMOTE_ASHA_CAPABILITY,
    #  BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID,
    #  BT_PROPERTY_REMOTE_MODEL_NUM,
    RemoteAddrType = 0x18
    Unknown = 0xFE
    RemoteDeviceTimestamp = 0xFF


class PairingVariant(enum.IntEnum):
    """Bluetooth pairing variant type."""
    # SSP variants.
    PASSKEY_CONFIRMATION = 0
    PASSKEY_ENTRY = 1
    CONSENT = 2
    PASSKEY_NOTIFICATION = 3

    # Legacy pairing variants.
    PIN_ENTRY = 4
    PIN_16_DIGITS_ENTRY = 5
    PIN_NOTIFICATION = 6


class BleAddressType(enum.IntEnum):
    BLE_ADDR_PUBLIC = 0x00
    BLE_ADDR_RANDOM = 0x01
    BLE_ADDR_PUBLIC_ID = 0x02
    BLE_ADDR_RANDOM_ID = 0x03
    BLE_ADDR_ANONYMOUS = 0xFF


class OwnAddressType(enum.IntEnum):
    DEFAULT = -1
    PUBLIC = 0
    RANDOM = 1


class BtConnectionState(enum.IntEnum):
    NOT_CONNECTED = 0
    CONNECTED_ONLY = 1
    ENCRYPTED_BR_EDR = 3
    ENCRYPTED_LE = 5
    ENCRYPTED_BOTH = 7


class CompanyIdentifiers(enum.IntEnum):
    """Bluetooth SIG Company ID values.

    Bluetooth SIG official document: https://www.bluetooth.com/specifications/assigned-numbers/
    """
    GOOGLE = 0x00E0


class AdvertisingDataType(enum.IntEnum):
    FLAGS = 0x01
    INCOMPLETE_LIST_OF_16_BIT_SERVICE_CLASS_UUIDS = 0x02
    COMPLETE_LIST_OF_16_BIT_SERVICE_CLASS_UUIDS = 0x03
    INCOMPLETE_LIST_OF_32_BIT_SERVICE_CLASS_UUIDS = 0x04
    COMPLETE_LIST_OF_32_BIT_SERVICE_CLASS_UUIDS = 0x05
    INCOMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS = 0x06
    COMPLETE_LIST_OF_128_BIT_SERVICE_CLASS_UUIDS = 0x07
    SHORTENED_LOCAL_NAME = 0x08
    COMPLETE_LOCAL_NAME = 0x09
    TX_POWER_LEVEL = 0x0A
    CLASS_OF_DEVICE = 0x0D
    SLAVE_CONNECTION_INTERVAL_RANGE = 0x12
    LIST_OF_16_BIT_SERVICE_SOLICITATION_UUIDS = 0x14
    LIST_OF_128_BIT_SERVICE_SOLICITATION_UUIDS = 0x15
    SERVICE_DATA_16_BIT_UUID = 0x16
    PUBLIC_TARGET_ADDRESS = 0x17
    RANDOM_TARGET_ADDRESS = 0x18
    APPEARANCE = 0x19
    ADVERTISING_INTERVAL = 0x1A
    LIST_OF_32_BIT_SERVICE_SOLICITATION_UUIDS = 0x1F
    SERVICE_DATA_32_BIT_UUID = 0x20
    SERVICE_DATA_128_BIT_UUID = 0x21
    URI = 0x24
    LE_SUPPORTED_FEATURES = 0x27
    MANUFACTURER_SPECIFIC_DATA = 0xFF


class BtDiscMode(enum.IntEnum):
    """Bluetooth discoverable mode."""
    NON_DISCOVERABLE = 0
    LIMITED_DISCOVERABLE = 1
    GENERAL_DISCOVERABLE = 2


class BthhReportType(enum.IntEnum):
    """Bluetooth HID report type."""
    INPUT_REPORT = 1
    OUTPUT_REPORT = 2
    FEATURE_REPORT = 3
