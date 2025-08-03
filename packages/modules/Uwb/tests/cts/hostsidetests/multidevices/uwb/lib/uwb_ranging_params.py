"""Class for UWB ranging parameters."""

import dataclasses
from typing import Any, Dict, List, Optional


class FiraParamEnums:
  """Class for Fira parameter constants."""

  # channels
  UWB_CHANNEL_5 = 5
  UWB_CHANNEL_9 = 9

  # preamble codes
  UWB_PREAMBLE_CODE_INDEX_9 = 9
  UWB_PREAMBLE_CODE_INDEX_10 = 10
  UWB_PREAMBLE_CODE_INDEX_11 = 11
  UWB_PREAMBLE_CODE_INDEX_12 = 12

  # ranging device types
  DEVICE_TYPE_CONTROLEE = 0
  DEVICE_TYPE_CONTROLLER = 1

  # ranging device roles
  DEVICE_ROLE_RESPONDER = 0
  DEVICE_ROLE_INITIATOR = 1

  # multi node modes
  MULTI_NODE_MODE_UNICAST = 0
  MULTI_NODE_MODE_ONE_TO_MANY = 1

  # hopping modes
  HOPPING_MODE_DISABLE = 0
  HOPPING_MODE_FIRA_HOPPING_ENABLE = 1

  # ranging round usage
  RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE = 1
  RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE = 2
  RANGING_ROUND_USAGE_SS_TWR_NON_DEFERRED_MODE = 3
  RANGING_ROUND_USAGE_DS_TWR_NON_DEFERRED_MODE = 4

  # mac address mode
  MAC_ADDRESS_MODE_2_BYTES = 0
  MAC_ADDRESS_MODE_8_BYTES = 2

  # initiation time in ms
  INITIATION_TIME_MS = 0

  # slot duration rstu
  SLOT_DURATION_RSTU = 2400

  # ranging interval ms
  RANGING_INTERVAL_MS = 200

  # slots per ranging round
  SLOTS_PER_RR = 30

  # in band termination attempt count
  IN_BAND_TERMINATION_ATTEMPT_COUNT = 1

  # aoa report request
  AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT = 0
  AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS = 1

  # ranging round retries
  MAX_RANGING_ROUND_RETRIES = 0

  # block stride
  BLOCK_STRIDE_LENGTH = 0

  # list update actions
  MULTICAST_LIST_UPDATE_ACTION_ADD = 0
  MULTICAST_LIST_UPDATE_ACTION_DELETE = 1
  P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE = 2
  P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_32_BYTE = 3

  # sts config
  STS_CONFIG_STATIC = 0
  STS_CONFIG_PROVISIONED = 3
  STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY = 4


@dataclasses.dataclass
class UwbRangingReconfigureParams():
  """Class for UWB ranging reconfigure parameters.

  Attributes:
    action: Type of reconfigure action.
    address_list: new address list.
    block_stride_length: block stride length
    sub_session_id_list: provisioned sts sub session id list.
    sub_session_key_list: provisioned sts sub session key list.
  """
  action: Optional[int] = None
  address_list: Optional[List[List[int]]] = None
  block_stride_length: Optional[int] = None
  sub_session_id_list: Optional[List[int]] = None
  sub_session_key_list: Optional[List[int]] = None

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging reconfigure parameters in dictionary for sl4a.

    Returns:
      UWB ranging reconfigure parameters in dictionary.
    """
    reconfigure_params = {}
    if self.address_list is not None:
      reconfigure_params["action"] = self.action
      reconfigure_params["addressList"] = self.address_list
      if self.sub_session_id_list is not None:
        reconfigure_params["subSessionIdList"] = self.sub_session_id_list
      if self.sub_session_key_list is not None:
        reconfigure_params["subSessionKeyList"] = self.sub_session_key_list
    elif self.block_stride_length is not None:
      reconfigure_params["blockStrideLength"] = self.block_stride_length
    return reconfigure_params


@dataclasses.dataclass
class UwbRangingControleeParams():
  """Class for UWB ranging controlee parameters.

  Attributes:
    action: Type of reconfigure action.
    address_list: new address list.
    sub_session_id_list: provisioned sts sub session id list.
    sub_session_key_list: provisioned sts sub session key list.
  """
  action: Optional[int] = None
  address_list: Optional[List[List[int]]] = None
  sub_session_id_list: Optional[List[int]] = None
  sub_session_key_list: Optional[List[int]] = None

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging controlee parameters in dictionary for sl4a.

    Returns:
      UWB ranging controlee parameters in dictionary.
    """
    controlee_params = {}
    if self.action is not None:
      controlee_params["action"] = self.action
    if self.address_list is not None:
      controlee_params["addressList"] = self.address_list
    if self.sub_session_id_list is not None:
      controlee_params["subSessionIdList"] = self.sub_session_id_list
    if self.sub_session_key_list is not None:
      controlee_params["subSessionKeyList"] = self.sub_session_key_list
    return controlee_params


@dataclasses.dataclass
class UwbRangingParams():
  """Class for Uwb ranging parameters.

  Attributes:
    device_type: Type of ranging device - Controller or Controlee.
    device_role: Role of ranging device - Initiator or Responder.
    device_address: Address of the UWB device.
    destination_addresses: List of UWB peer addresses.
    channel: Channel for ranging. Possible values 5 or 9.
    preamble: Preamble for ranging.
    ranging_round_usage : Ranging Round Usage values.
    hopping_mode : Hopping modes.
    mac_address_mode : MAC address modes.
    initiation_time_ms : Initiation Time in ms.
    slot_duration_rstu : Slot duration RSTU.
    ranging_interval_ms : Ranging interval in ms.
    slots_per_ranging_round : Slots per Ranging Round.
    in_band_termination_attempt_count : In Band Termination Attempt count.
    aoa_result_request : AOA report request.
    max_ranging_round_retries : Max Ranging round retries.
    block_stride_length: Block Stride Length
    session_id: Ranging session ID.
    multi_node_mode: Ranging mode. Possible values 1 to 1 or 1 to many.
    vendor_id: Ranging device vendor ID.
    static_sts_iv: Static STS value.
    sts_config: STS config.
    session_key: Provisioned sts session key.
    sub_session_id: Ranging sub session ID.
    sub_session_key: Ranging sub session key.

  Example:
      An example of UWB ranging parameters passed to sl4a is below.

      self.initiator_params = {
        "sessionId": 10,
        "deviceType": FiraParamEnums.RANGING_DEVICE_TYPE_CONTROLLER,
        "deviceRole": FiraParamEnums.RANGING_DEVICE_ROLE_INITIATOR,
        "multiNodeMode": FiraParamEnums.MULTI_NODE_MODE_ONE_TO_MANY,
        "channel": FiraParamEnums.UWB_CHANNEL_9,
        "deviceAddress": [1, 2],
        "destinationAddresses": [[3, 4],],
        "vendorId": [5, 6],
        "staticStsIV": [5, 6, 7, 8, 9, 10],
      }

      The UwbRangingParams are passed to UwbManagerFacade#openRaningSession()
      from the open_ranging() method as a JSONObject.
      These are converted to FiraOpenSessionParams using
      UwbManagerFacade#generateFiraOpenSessionParams().
      If some of the values are skipped in the params, default values are used.
      Please see com/google/uwb/support/fira/FiraParams.java for more details
      on the default values.

      If the passed params are invalid, then open_ranging() will fail.
  """

  device_type: int
  device_role: int
  device_address: List[int]
  destination_addresses: List[List[int]]
  session_id: int = 10
  channel: int = FiraParamEnums.UWB_CHANNEL_9
  preamble: int = FiraParamEnums.UWB_PREAMBLE_CODE_INDEX_10
  multi_node_mode: int = FiraParamEnums.MULTI_NODE_MODE_ONE_TO_MANY
  ranging_round_usage: int = (
      FiraParamEnums.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE
  )
  mac_address_mode: int = FiraParamEnums.MAC_ADDRESS_MODE_2_BYTES
  initiation_time_ms: int = FiraParamEnums.INITIATION_TIME_MS
  slot_duration_rstu: int = FiraParamEnums.SLOT_DURATION_RSTU
  ranging_interval_ms: int = FiraParamEnums.RANGING_INTERVAL_MS
  slots_per_ranging_round: int = FiraParamEnums.SLOTS_PER_RR
  in_band_termination_attempt_count: int = (
      FiraParamEnums.IN_BAND_TERMINATION_ATTEMPT_COUNT
  )
  aoa_result_request: int = (
      FiraParamEnums.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS
  )
  hopping_mode: int = FiraParamEnums.HOPPING_MODE_FIRA_HOPPING_ENABLE
  max_ranging_round_retries: int = FiraParamEnums.MAX_RANGING_ROUND_RETRIES
  block_stride_length: int = FiraParamEnums.BLOCK_STRIDE_LENGTH
  vendor_id: List[int] = dataclasses.field(default_factory=lambda: [5, 6])
  static_sts_iv: List[int] = dataclasses.field(
      default_factory=lambda: [5, 6, 7, 8, 9, 10])
  sts_config: int = FiraParamEnums.STS_CONFIG_STATIC
  session_key: List[int] = dataclasses.field(
      default_factory=lambda: [1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1]
  )
  sub_session_id: Optional[int] = None
  sub_session_key: Optional[List[int]] = None

  def to_dict(self) -> Dict[str, Any]:
    """Returns UWB ranging parameters in dictionary for sl4a.

    Returns:
      UWB ranging parameters in dictionary.
    """
    dict = {
        "deviceType": self.device_type,
        "deviceRole": self.device_role,
        "deviceAddress": self.device_address,
        "destinationAddresses": self.destination_addresses,
        "channel": self.channel,
        "preamble": self.preamble,
        "rangingRoundUsage": self.ranging_round_usage,
        "macAddressMode": self.mac_address_mode,
        "initiationTimeMs": self.initiation_time_ms,
        "slotDurationRstu": self.slot_duration_rstu,
        "slotsPerRangingRound": self.slots_per_ranging_round,
        "rangingIntervalMs": self.ranging_interval_ms,
        "hoppingMode": self.hopping_mode,
        "maxRangingRoundRetries": self.max_ranging_round_retries,
        "inBandTerminationAttemptCount": self.in_band_termination_attempt_count,
        "aoaResultRequest": self.aoa_result_request,
        "blockStrideLength": self.block_stride_length,
        "sessionId": self.session_id,
        "multiNodeMode": self.multi_node_mode,
        "vendorId": self.vendor_id,
        "staticStsIV": self.static_sts_iv,
        "stsConfig": self.sts_config,
        "sessionKey": self.session_key,
    }
    if self.sub_session_id is not None:
      dict["subSessionId"] = self.sub_session_id
    if self.sub_session_key is not None:
      dict["subSessionKey"] = self.sub_session_key
    return dict

  def update(self, **kwargs: Any):
    """Updates the UWB parameters with the new values.

    Args:
      **kwargs: uwb attributes with new values.
    """
    for key, value in kwargs.items():
      if hasattr(self, key):
        setattr(self, key, value)
