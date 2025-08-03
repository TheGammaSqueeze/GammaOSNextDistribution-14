# Lint as: python3
"""Constants used for bluetooth test."""

import enum

### Generic Constants Begin ###
BT_DEFAULT_TIMEOUT_SECONDS = 15
DEFAULT_RFCOMM_TIMEOUT_MS = 10000
CALL_STATE_IDLE = 0
CALL_STATE_RINGING = 1
CALL_STATE_OFFHOOK = 2
CALL_STATE_TIMEOUT_SEC = 30
NAP_CONNECTION_TIMEOUT_SECS = 20

# Call log types.
INCOMING_CALL_LOG_TYPE = '1'
OUTGOING_CALL_LOG_TYPE = '2'
MISSED_CALL_LOG_TYPE = '3'

# Passthrough Commands sent to the RPC Server.
CMD_MEDIA_PLAY = 'play'
CMD_MEDIA_PAUSE = 'pause'
CMD_MEDIA_SKIP_NEXT = 'skipNext'
CMD_MEDIA_SKIP_PREV = 'skipPrev'

# Events dispatched from the RPC Server.
EVENT_PLAY_RECEIVED = 'playReceived'
EVENT_PAUSE_RECEIVED = 'pauseReceived'
EVENT_SKIP_NEXT_RECEIVED = 'skipNextReceived'
EVENT_SKIP_PREV_RECEIVED = 'skipPrevReceived'

# A playback state indicating the media session is currently paused.
STATE_PAUSED = 2
STATE_PLAYING = 3

# File path
RAMDUMP_PATH = 'data/vendor/ssrdump'

# UiAutoHelper package name.
UIAUTO_HELPER_PACKAGE_NAME = 'com.google.android.uiautohelper'

# Test Runner for Android instrumentation test.
ANDROIDX_TEST_RUNNER = 'androidx.test.runner.AndroidJUnitRunner'

# Wifi hotspot setting
WIFI_HOTSPOT_2_4G = {'SSID': 'pqmBT', 'password': 'password', 'apBand': 0}

# Strings representing boolean of device properties.
TRUE = 'true'
FALSE = 'false'

# String representing a property of AAC VBR support for Android device.
AAC_VBR_SUPPORTED_PROPERTY = 'persist.bluetooth.a2dp_aac.vbr_supported'

# Dict containing charging control config for devices.
CHARGING_CONTROL_CONFIG_DICT = {
    # Internal codename
}


class AvrcpEvent(enum.Enum):
    """Enumeration of AVRCP event types."""
    PLAY = 'State:NOT_PLAYING->PLAYING'
    PAUSE = 'State:PLAYING->NOT_PLAYING'
    TRACK_PREVIOUS = 'sendMediaKeyEvent: keyEvent=76'
    TRACK_NEXT = 'sendMediaKeyEvent: keyEvent=75'


# Bluetooth RFCOMM UUIDs as defined by the SIG
BT_RFCOMM_UUIDS = {
    'default_uuid': '457807c0-4897-11df-9879-0800200c9a66',
    'base_uuid': '00000000-0000-1000-8000-00805F9B34FB',
    'sdp': '00000001-0000-1000-8000-00805F9B34FB',
    'udp': '00000002-0000-1000-8000-00805F9B34FB',
    'rfcomm': '00000003-0000-1000-8000-00805F9B34FB',
    'tcp': '00000004-0000-1000-8000-00805F9B34FB',
    'tcs_bin': '00000005-0000-1000-8000-00805F9B34FB',
    'tcs_at': '00000006-0000-1000-8000-00805F9B34FB',
    'att': '00000007-0000-1000-8000-00805F9B34FB',
    'obex': '00000008-0000-1000-8000-00805F9B34FB',
    'ip': '00000009-0000-1000-8000-00805F9B34FB',
    'ftp': '0000000A-0000-1000-8000-00805F9B34FB',
    'http': '0000000C-0000-1000-8000-00805F9B34FB',
    'wsp': '0000000E-0000-1000-8000-00805F9B34FB',
    'bnep': '0000000F-0000-1000-8000-00805F9B34FB',
    'upnp': '00000010-0000-1000-8000-00805F9B34FB',
    'hidp': '00000011-0000-1000-8000-00805F9B34FB',
    'hardcopy_control_channel': '00000012-0000-1000-8000-00805F9B34FB',
    'hardcopy_data_channel': '00000014-0000-1000-8000-00805F9B34FB',
    'hardcopy_notification': '00000016-0000-1000-8000-00805F9B34FB',
    'avctp': '00000017-0000-1000-8000-00805F9B34FB',
    'avdtp': '00000019-0000-1000-8000-00805F9B34FB',
    'cmtp': '0000001B-0000-1000-8000-00805F9B34FB',
    'mcap_control_channel': '0000001E-0000-1000-8000-00805F9B34FB',
    'mcap_data_channel': '0000001F-0000-1000-8000-00805F9B34FB',
    'l2cap': '00000100-0000-1000-8000-00805F9B34FB'
}


class BluetoothAccessLevel(enum.IntEnum):
    """Enum class for bluetooth profile access levels."""
    ACCESS_ALLOWED = 1
    ACCESS_DENIED = 2


class BluetoothProfile(enum.IntEnum):
    """Enum class for bluetooth profile types.

    Should be kept in sync with
    //frameworks/base/core/java/android/bluetooth/BluetoothProfile.java
    """
    HEADSET = 1
    A2DP = 2
    HEALTH = 3
    HID_HOST = 4
    PAN = 5
    PBAP = 6
    GATT = 7
    GATT_SERVER = 8
    MAP = 9
    SAP = 10
    A2DP_SINK = 11
    AVRCP_CONTROLLER = 12
    AVRCP = 13
    HEADSET_CLIENT = 16
    PBAP_CLIENT = 17
    MAP_MCE = 18
    HID_DEVICE = 19
    OPP = 20
    HEARING_AID = 21


class BluetoothConnectionPolicy(enum.IntEnum):
    """Enum class for bluetooth bluetooth connection policy.

    bluetooth connection policy as defined in
    //frameworks/base/core/java/android/bluetooth/BluetoothProfile.java
    """
    CONNECTION_POLICY_UNKNOWN = -1
    CONNECTION_POLICY_FORBIDDEN = 0
    CONNECTION_POLICY_ALLOWED = 100


class BluetoothConnectionStatus(enum.IntEnum):
    """Enum class for bluetooth connection status.

    Bluetooth connection status as defined in
    //frameworks/base/core/java/android/bluetooth/BluetoothProfile.java
    """
    STATE_DISCONNECTED = 0
    STATE_CONNECTING = 1
    STATE_CONNECTED = 2
    STATE_DISCONNECTING = 3


class BluetoothPriorityLevel(enum.IntEnum):
    """Enum class for bluetooth priority level.

    Priority levels as defined in
    //frameworks/base/core/java/android/bluetooth/BluetoothProfile.java
    """
    PRIORITY_AUTO_CONNECT = 1000
    PRIORITY_ON = 100
    PRIORITY_OFF = 0
    PRIORITY_UNDEFINED = -1


class LogType(enum.Enum):
    """Enumeration of device log type."""
    DEFAULT_VALUE = 'GENERIC'
    BLUETOOTH_DEVICE_SIMULATOR = 'BDS'
    ICLEVER_HB01 = 'GENERIC'


class CallState(enum.IntEnum):
    """Enum class for phone call state."""
    IDLE = 0
    RINGING = 1
    OFFHOOK = 2


class CallLogType(enum.IntEnum):
    """Enum class for phone call log type."""
    INCOMING_CALL = 1
    OUTGOING_CALL = 2
    MISSED_CALL = 3


class BluetoothA2dpCodec(enum.IntEnum):
    """Enum class for Bluetooth A2DP codec type."""
    SBC = 0
    AAC = 1
    APTX = 2
    APTX_HD = 3
    LDAC = 4
