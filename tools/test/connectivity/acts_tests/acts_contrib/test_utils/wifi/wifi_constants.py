#!/usr/bin/env python3
#
#   Copyright 2016 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

# Constants for Wifi related events.
WIFI_CONNECTED = "WifiNetworkConnected"
WIFI_DISCONNECTED = "WifiNetworkDisconnected"
SUPPLICANT_CON_CHANGED = "SupplicantConnectionChanged"
WIFI_STATE_CHANGED = "WifiStateChanged"
WIFI_FORGET_NW_SUCCESS = "WifiManagerForgetNetworkOnSuccess"
WIFI_NETWORK_REQUEST_MATCH_CB_ON_MATCH = "WifiManagerNetworkRequestMatchCallbackOnMatch"
WIFI_NETWORK_REQUEST_MATCH_CB_ON_CONNECT_SUCCESS = "WifiManagerNetworkRequestMatchCallbackOnUserSelectionConnectSuccess"
WIFI_NETWORK_REQUEST_MATCH_CB_ON_CONNECT_FAILURE = "WifiManagerNetworkRequestMatchCallbackOnUserSelectionConnectFailure"
WIFI_NETWORK_CB_ON_AVAILABLE = "WifiManagerNetworkCallbackOnAvailable"
WIFI_NETWORK_CB_ON_UNAVAILABLE = "WifiManagerNetworkCallbackOnUnavailable"
WIFI_NETWORK_CB_ON_LOST = "WifiManagerNetworkCallbackOnLost"
WIFI_NETWORK_SUGGESTION_POST_CONNECTION = "WifiNetworkSuggestionPostConnection"
WIFI_SUBSYSTEM_RESTARTING = "WifiSubsystemRestarting"
WIFI_SUBSYSTEM_RESTARTED = "WifiSubsystemRestarted"

# These constants will be used by the ACTS wifi tests.
CONNECT_BY_CONFIG_SUCCESS = 'WifiManagerConnectByConfigOnSuccess'
CONNECT_BY_NETID_SUCCESS = 'WifiManagerConnectByNetIdOnSuccess'

# Softap related constants
SOFTAP_CALLBACK_EVENT = "WifiManagerSoftApCallback-"
# Callback Event for softap state change
# WifiManagerSoftApCallback-[callbackId]-OnStateChanged
SOFTAP_STATE_CHANGED = "-OnStateChanged"
SOFTAP_STATE_CHANGE_CALLBACK_KEY = "State"
WIFI_AP_DISABLING_STATE = 10
WIFI_AP_DISABLED_STATE = 11
WIFI_AP_ENABLING_STATE = 12
WIFI_AP_ENABLED_STATE = 13
WIFI_AP_FAILED_STATE = 14

SOFTAP_RANDOMIZATION_NONE = 0
SOFTAP_RANDOMIZATION_PERSISTENT = 1

# Callback Event for client number change:
# WifiManagerSoftApCallback-[callbackId]-OnNumClientsChanged
SOFTAP_NUMBER_CLIENTS_CHANGED_WITH_INFO = "-OnConnectedClientsChangedWithInfo"
SOFTAP_NUMBER_CLIENTS_CHANGED = "-OnNumClientsChanged"
SOFTAP_NUMBER_CLIENTS_CALLBACK_KEY = "NumClients"
SOFTAP_CLIENTS_MACS_CALLBACK_KEY = "MacAddresses"
# Callback Event for softap info change
SOFTAP_INFO_CHANGED = "-OnInfoChanged"
SOFTAP_INFOLIST_CHANGED = "-OnInfoListChanged"
SOFTAP_INFO_FREQUENCY_CALLBACK_KEY = "frequency"
SOFTAP_INFO_BANDWIDTH_CALLBACK_KEY = "bandwidth"
SOFTAP_INFO_WIFISTANDARD_CALLBACK_KEY = "wifiStandard"
SOFTAP_INFO_AUTO_SHUTDOWN_CALLBACK_KEY = "autoShutdownTimeoutMillis"
SOFTAP_INFO_BSSID_CALLBACK_KEY = "bssid"
# Callback Event for softap client blocking
SOFTAP_BLOCKING_CLIENT_CONNECTING = "-OnBlockedClientConnecting"
SOFTAP_BLOCKING_CLIENT_REASON_KEY = "BlockedReason"
SOFTAP_BLOCKING_CLIENT_WIFICLIENT_KEY = "WifiClient"
SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER = 0
SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS = 1

# Callback Event for softap capability
SOFTAP_CAPABILITY_CHANGED = "-OnCapabilityChanged"
SOFTAP_CAPABILITY_MAX_SUPPORTED_CLIENTS = "maxSupportedClients"
SOFTAP_CAPABILITY_24GHZ_SUPPORTED_CHANNEL_LIST = "supported2GHzChannellist"
SOFTAP_CAPABILITY_5GHZ_SUPPORTED_CHANNEL_LIST = "supported5GHzChannellist"
SOFTAP_CAPABILITY_6GHZ_SUPPORTED_CHANNEL_LIST = "supported6GHzChannellist"
SOFTAP_CAPABILITY_60GHZ_SUPPORTED_CHANNEL_LIST = "supported60GHzChannellist"
SOFTAP_CAPABILITY_FEATURE_ACS = "acsOffloadSupported"
SOFTAP_CAPABILITY_FEATURE_CLIENT_CONTROL = "clientForceDisconnectSupported"
SOFTAP_CAPABILITY_FEATURE_WPA3_SAE = "wpa3SaeSupported"
SOFTAP_CAPABILITY_FEATURE_IEEE80211AX = "ieee80211axSupported"
SOFTAP_CAPABILITY_FEATURE_24GHZ = "24gSupported"
SOFTAP_CAPABILITY_FEATURE_5GHZ = "5gSupported"
SOFTAP_CAPABILITY_FEATURE_6GHZ = "6gSupported"
SOFTAP_CAPABILITY_FEATURE_60GHZ = "60gSupported"

DEFAULT_SOFTAP_TIMEOUT_S = 600 # 10 minutes

# AP related constants
AP_MAIN = "main_AP"
AP_AUX = "aux_AP"
SSID = "SSID"

# Delay before registering the match callback.
NETWORK_REQUEST_CB_REGISTER_DELAY_SEC = 2

# Constants for JSONObject representation of CoexUnsafeChannel
COEX_BAND = "band"
COEX_BAND_24_GHZ = "24_GHZ"
COEX_BAND_5_GHZ = "5_GHZ"
COEX_CHANNEL = "channel"
COEX_POWER_CAP_DBM = "powerCapDbm"

# Constants for bundle keys for CoexCallback#onCoexUnsafeChannelsChanged
KEY_COEX_UNSAFE_CHANNELS = "KEY_COEX_UNSAFE_CHANNELS"
KEY_COEX_RESTRICTIONS = "KEY_COEX_RESTRICTIONS"

# WiFi standards
WIFI_STANDARD_11AX = 6
