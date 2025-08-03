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

from collections.abc import Sequence
import datetime

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import errors

from direct import constants

_DEFAULT_TIMEOUT = datetime.timedelta(seconds=30)

_WIFI_DIRECT_SNIPPET_KEY = 'wifi_direct_mobly_snippet'


def _init_wifi_p2p(
    ad: android_device.AndroidDevice,
) -> callback_handler_v2.CallbackHandlerV2:
  """Registers the application with the Wi-Fi framework."""
  state_handler = ad.wifi.p2pInitialize()
  init_event = state_handler.waitAndGet(
      event_name=constants.WIFI_P2P_STATE_CHANGED_ACTION,
      timeout=_DEFAULT_TIMEOUT.total_seconds(),
  )
  state = constants.ExtraWifiState(init_event.data[constants.EXTRA_WIFI_STATE])
  asserts.assert_equal(
      state,
      constants.ExtraWifiState.WIFI_P2P_STATE_ENABLED,
      f'Failed to initialize Wi-Fi P2P, state: {state}',
  )
  return state_handler


class WifiDirectTest(base_test.BaseTestClass):
  """Tests Wi-Fi Direct between 2 Android devices."""

  ads: Sequence[android_device.AndroidDevice]

  def _setup_device(self, ad: android_device.AndroidDevice) -> None:
    tag = 'files' if 'files' in self.user_params else 'mh_files'
    asserts.assert_in(
        _WIFI_DIRECT_SNIPPET_KEY,
        self.user_params[tag],
        'Wi-Fi direct snippet not found',
    )
    ad.adb.install(
        ['-d', '-g', '-r', self.user_params[tag][_WIFI_DIRECT_SNIPPET_KEY][0]]
    )
    ad.load_snippet('wifi', constants.WIFI_DIRECT_SNIPPET_PACKAGE_NAME)

  def setup_class(self) -> None:
    super().setup_class()
    self.ads = self.register_controller(android_device, min_number=2)
    utils.concurrent_exec(
        self._setup_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

  def test_wifi_direct_connect(self) -> None:
    group_owner, client, *_ = self.ads
    group_owner.debug_tag = 'Group Owner'
    client.debug_tag = 'Client'
    config = constants.WifiP2pConfig(
        network_name='DIRECT-BeTo', passphrase='testtest'
    )

    owner_state_handler = _init_wifi_p2p(group_owner)

    owner_action_handler = group_owner.wifi.p2pCreateGroup(config.to_dict())
    try:
      owner_action_handler.waitAndGet(
          event_name=constants.ACTION_LISTENER_ON_SUCCESS,
          timeout=_DEFAULT_TIMEOUT.total_seconds(),
      )
    except errors.CallbackHandlerTimeoutError:
      failure_event = owner_action_handler.waitAndGet(
          constants.ACTION_LISTENER_ON_FAILURE
      )
      failure_reason = constants.ActionListenerOnFailure(
          failure_event.data[constants.ACTION_LISTENER_FAILURE_REASON]
      )
      asserts.fail(f'Failed to create a group, reason: {failure_reason.name}')
    owner_connected_event = owner_state_handler.waitAndGet(
        event_name=constants.WIFI_P2P_CREATING_GROUP,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    group_owner_wifi_group = owner_connected_event.data[
        constants.EXTRA_WIFI_P2P_GROUP
    ]
    group_owner.log.info(f'Created a group: {group_owner_wifi_group}')

    client_state_handler = _init_wifi_p2p(client)

    client_action_callback = client.wifi.p2pConnect(config.to_dict())
    try:
      client_action_callback.waitAndGet(
          event_name=constants.ACTION_LISTENER_ON_SUCCESS,
          timeout=_DEFAULT_TIMEOUT.total_seconds(),
      )
    except errors.CallbackHandlerTimeoutError:
      failure_event = client_action_callback.waitAndGet(
          constants.ACTION_LISTENER_ON_FAILURE
      )
      failure_reason = constants.ActionListenerOnFailure(
          failure_event.data[constants.ACTION_LISTENER_FAILURE_REASON]
      )
      asserts.fail(
          f'Failed to connect to a group, reason: {failure_reason.name}'
      )
    client_connected_event = client_state_handler.waitAndGet(
        event_name=constants.WIFI_P2P_CREATING_GROUP,
        timeout=_DEFAULT_TIMEOUT.total_seconds(),
    )
    client_wifi_group = client_connected_event.data[
        constants.EXTRA_WIFI_P2P_GROUP
    ]
    group_owner.log.info(f'Created a group: {client_wifi_group}')

    group_owner.wifi.p2pClose()
    client.wifi.p2pClose()

  def teardown_test(self) -> None:
    utils.concurrent_exec(
        lambda d: d.services.create_output_excerpts_all(self.current_test_info),
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )


if __name__ == '__main__':
  test_runner.main()

