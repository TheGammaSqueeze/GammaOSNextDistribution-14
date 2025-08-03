#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
"""Constants class contains final variables using by other classes."""

LOCAL_MAC_ADDRESS_PERMISSION = 'android.permission.LOCAL_MAC_ADDRESS'
APS_PACKAGE = 'android.platform.snippets'
DIAL_A_NUMBER = 'Dial a number'
DEFAULT_WAIT_TIME_FIVE_SECS = 5
DEFAULT_WAIT_TIME_FIFTEEN_SECS = 15
WAIT_FOR_LOAD = 2
BT_DEFAULT_TIMEOUT = 15
WAIT_ONE_SEC = 1
WAIT_TWO_SECONDS = 2
WAIT_THIRTY_SECONDS = 30
SYNC_WAIT_TIME = 15 # Sometimes syncing between devices can take a while
DEVICE_CONNECT_WAIT_TIME = 20 # Waiting for device pairing to complete.
MOBILE_DEVICE_NAME = 'target'
AUTOMOTIVE_DEVICE_NAME = 'discoverer'
REBOOT = 'reboot'
DIALER_THREE_DIGIT_NUMBER = "511"
INFORMATION_THREE_DIGIT_NUMBER = "411"
EXPECTED_CONTACT_FULL_NAME = "John Smith"
EXPECTED_CONTACT_FIRST_NAME = "John"
EXPECTED_CONTACT_LAST_NAME = "Smith"
FIRST_DIGIT_OF_SEARCH_CONTACT_NUMBER = "6"
EXPECTED_PHONE_NUMBER = "611"
ROOT = "root"
DATE_CMD = "date"
DEFAULT_DIAL_PAD_ENTRY = "Dial a number"
# The word or phrase present in a device summary that is connected.
CONNECTED_SUMMARY_STATUS = "Connected"
DISCONNECTED_SUMMARY_STATUS = "Disconnected"
DECLINE_CALL_TEXT = "Decline"
ANSWER_CALL_TEXT = "Answer"
ACCEPT_CALL_TEXT = "Accept"
DISABLE_ANDROID_AUTO_POP_UP = "pm disable --user 10 com.google.android.embedded.projection"

BTSNOOP_LOG_PATH_ON_DEVICE = '/data/misc/bluetooth/logs/btsnoop_hci.log'
BTSNOOP_LAST_LOG_PATH_ON_DEVICE = (
    '/data/misc/bluetooth/logs/btsnoop_hci.log.last'
)
PHONE_CONTACTS_DESTINATION_PATH = (
    '/data/data/com.google.android.contacts/cache/contacts.vcf'
)
IMPOST_CONTACTS_SHELL_COMAND = (
        'am start-activity -W -t "text/x-vcard" -d file://'
        + PHONE_CONTACTS_DESTINATION_PATH
        + ' -a android.intent.action.VIEW com.google.android.contacts'
)

# Should be kept in sync with BluetoothProfile.java
BT_PROFILE_CONSTANTS = {
    'headset': 1,
    'a2dp': 2,
    'health': 3,
    'input_device': 4,
    'pan': 5,
    'pbap_server': 6,
    'gatt': 7,
    'gatt_server': 8,
    'map': 9,
    'sap': 10,
    'a2dp_sink': 11,
    'avrcp_controller': 12,
    'headset_client': 16,
    'pbap_client': 17,
    'map_mce': 18,
}

BLUETOOTH_PROFILE_CONNECTION_STATE_CHANGED = (
    'BluetoothProfileConnectionStateChanged'
)

BT_PROFILE_STATES = {
    'disconnected': 0,
    'connecting': 1,
    'connected': 2,
    'disconnecting': 3,
}
PATH_TO_CONTACTS_VCF_FILE = 'platform_testing/tests/automotive/mobly_tests/utilities/contacts_test.vcf'

PHONE_CONTACTS_DESTINATION_PATH = (
    '/data/data/com.google.android.contacts/cache/contacts.vcf'
)

IMPOST_CONTACTS_SHELL_COMAND = (
    'am start-activity -W -t "text/x-vcard" -d file://'
    + PHONE_CONTACTS_DESTINATION_PATH
    + ' -a android.intent.action.VIEW com.google.android.contacts'
)

ONE_SEC = 1

# KeyEvents
KEYCODE_ENTER = 'input keyevent KEYCODE_ENTER'
KEYCODE_TAB = 'input keyevent KEYCODE_TAB'
KEYCODE_MEDIA_NEXT = 'input keyevent KEYCODE_MEDIA_NEXT'
KEYCODE_MEDIA_PREVIOUS = 'input keyevent KEYCODE_MEDIA_PREVIOUS'
KEYCODE_MEDIA_PAUSE = 'input keyevent KEYCODE_MEDIA_PAUSE'
KEYCODE_MEDIA_PLAY = 'input keyevent KEYCODE_MEDIA_PLAY'
KEYCODE_MEDIA_STOP = 'input keyevent KEYCODE_MEDIA_STOP'

# YouTube Media
YOUTUBE_MUSIC_PACKAGE = 'com.google.android.apps.youtube.music'
START_YOUTUBE_MEDIA_SHELL = 'am start ' + YOUTUBE_MUSIC_PACKAGE
STOP_YOUTUBE_MEDIA_SHELL = 'am force-stop ' + YOUTUBE_MUSIC_PACKAGE
GET_DUMPSYS_METADATA = 'dumpsys media_session'
SONG_METADATA_PATTERN = r"description=.[^\n]*"
DEFAULT_YOUTUBE_MUSIC_PLAYLIST = 'am start -a android.intent.action.VIEW -d https://music.youtube.com/watch?v=sjuOaAf9C70'
BLUETOOTH_AUDIO_APP = "Bluetooth Audio"
YOUTUBE_MUSIC_APP = "YouTube Music"
BLUETOOTH_PLAYER = "Bluetooth Player"
YOUTUBE_MUSIC_DOWNLOADS = "Downloads"
RADIO_APP = "Radio"
FM_FREQUENCY_PATTERN = '^(10[1-7][13579])|(8[789][1357])|(9\\d[1357])$'
RADIO_FM_RANGE = "FM"
CONFIRM_RADIO_FREQUENCY = "Enter"
DEFAULT_FM_FREQUENCY = "1013"

# SMS
SMS_TEXT = "sms_test"
REPLY_SMS= "Okay"
SMS_REPLY_TEXT = "SMS Reply"
SMS_TEXT_DRIVE_MODE="Tap to read aloud"
TIMEZONE_DICT = {
    "PST": "Pacific Standard Time",
    "PDT": "Pacific Daylight Time",
    "EST": "Eastern Standard Time",
    "EDT": "Eastern Daylight Time"
}
CLEAR_MESSAGING_APP = 'pm clear com.google.android.apps.messaging'
DELETE_MESSAGING_DB = 'rm /data/data/com.android.providers.telephony/databases/mmssms.db'
OPEN_NOTIFICATION = 'service call statusbar 1'

# Dialer Page
NO_PHONE_MESSAGE = "To complete your call, first connect your phone to your car via Bluetooth."
DIALER_RECENTS_LABEL = "Recents"
DIALER_CONTACTS_LABEL = "Contacts"
DIALER_FAVORITES_LABEL = "Favorites"
DIALER_DIALPAD_LABEL = "Dialpad"
