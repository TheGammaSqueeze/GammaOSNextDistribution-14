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

# Lint as: python3

import dataclasses
import enum
from typing import Optional, Union

WIFI_DIRECT_SNIPPET_PACKAGE_NAME = 'com.google.snippet.wifi.direct'

ACTION_LISTENER_ON_SUCCESS = 'onSuccess'
ACTION_LISTENER_ON_FAILURE = 'onFailure'
ACTION_LISTENER_FAILURE_REASON = 'reason'

EXTRA_WIFI_P2P_GROUP = 'p2pGroupInfo'
EXTRA_WIFI_STATE = 'wifi_p2p_state'

WIFI_P2P_CREATING_GROUP = 'CREATING_GROUP'
WIFI_P2P_CONNECTION_CHANGED_ACTION = (
    'android.net.wifi.p2p.CONNECTION_STATE_CHANGE'
)
WIFI_P2P_DISCOVERY_CHANGED_ACTION = (
    'android.net.wifi.p2p.DISCOVERY_STATE_CHANGE'
)
WIFI_P2P_PEERS_CHANGED_ACTION = 'android.net.wifi.p2p.PEERS_CHANGED'
WIFI_P2P_STATE_CHANGED_ACTION = 'android.net.wifi.p2p.STATE_CHANGED'
WIFI_P2P_THIS_DEVICE_CHANGED_ACTION = 'android.net.wifi.p2p.THIS_DEVICE_CHANGED'


@enum.unique
class ActionListenerOnFailure(enum.IntEnum):
  """Indicates the failure reason of the initiation of the action.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager.ActionListener#onFailure(int)
  """

  ERROR = 0
  P2P_UNSUPPORTED = 1
  BUSY = 2


@enum.unique
class Band(enum.IntEnum):
  """Indicates the band of the operating frequency.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig#getGroupOwnerBand()
  """

  GROUP_OWNER_BAND_AUTO = 0
  GROUP_OWNER_BAND_2GHZ = 1
  GROUP_OWNER_BAND_5GHZ = 2


@enum.unique
class IpProvisioningMode(enum.IntEnum):
  """Indicates the IP provisioning mode.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig#getGroupClientIpProvisioningMode()
  """

  GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP = 0
  GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL = 1


@enum.unique
class ExtraWifiState(enum.IntEnum):
  """Indicates whether Wi-Fi p2p is enabled or disabled.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager#EXTRA_WIFI_STATE
  """

  WIFI_P2P_STATE_UNKNOWN = 0
  WIFI_P2P_STATE_DISABLED = 1
  WIFI_P2P_STATE_ENABLED = 2


@dataclasses.dataclass(frozen=True)
class WifiP2pConfig:
  """Represents a Wi-Fi P2p configuration for setting up a connection.

  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig
  """

  persistent_mode: Optional[bool] = None
  device_address: Optional[str] = None
  group_client_ip_provisioning_mode: Optional[IpProvisioningMode] = None
  group_operating_band: Optional[Band] = None
  group_operating_frequency: Optional[int] = None
  network_name: Optional[str] = None
  passphrase: Optional[str] = None

  def to_dict(self) -> dict[str, Union[bool, int, str]]:
    """Converts this WifiP2pConfig to a dictionary."""
    return {
        k: v.value if isinstance(v, enum.Enum) else v
        for k, v in self.__dict__.items()
        if v is not None
    }

