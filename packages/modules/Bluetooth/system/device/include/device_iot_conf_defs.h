/******************************************************************************
 *
 *  Copyright (C) 2018 The Linux Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#define IOT_CONF_KEY_SECTION_ADAPTER "Adapter"
#define IOT_CONF_KEY_ADDRESS "Address"

#define IOT_CONF_KEY_REMOTE_NAME "Name"
#define IOT_CONF_KEY_DEVCLASS "DevClass"
#define IOT_CONF_KEY_DEVTYPE "DevType"
#define IOT_CONF_KEY_ADDRTYPE "AddrType"
#define IOT_CONF_KEY_MANUFACTURER "Manufacturer"
#define IOT_CONF_KEY_LMPVER "LmpVer"
#define IOT_CONF_KEY_LMPSUBVER "LmpSubVer"
#define IOT_CONF_KEY_PAIRTYPE "PairType"
#define IOT_CONF_KEY_LE_PAIRTYPE "LE_PairType"
#define IOT_CONF_KEY_RT_SUPP_FEATURES "RemoteSupportedFeatures"
#define IOT_CONF_KEY_RT_EXT_FEATURES "RemoteExtendedFeatures"
#define IOT_CONF_KEY_LE_RT_FEATURES "LE_RemoteSupportedFeatures"
#define IOT_CONF_KEY_RECORDED "Recorded"

#define IOT_CONF_KEY_GAP_CONN_COUNT "ProfileGap_ConnectCount"
#define IOT_CONF_KEY_GAP_CONN_FAIL_COUNT "ProfileGap_ConnectFailCount"
#define IOT_CONF_KEY_GAP_DISC_COUNT "ProfileGap_DiscCount"
#define IOT_CONF_KEY_GAP_DISC_AUTHFAIL_COUNT "ProfileGap_DiscAuthFailCount"
#define IOT_CONF_KEY_GAP_DISC_CONNTIMEOUT_COUNT \
  "ProfileGap_DiscConnTimeoutCount"
#define IOT_CONF_KEY_A2DP_ROLE "ProfileA2dp_Role"
#define IOT_CONF_KEY_A2DP_VERSION "ProfileA2dp_Version"
#define IOT_CONF_KEY_A2DP_CODECTYPE "ProfileA2dp_CodecType"
#define IOT_CONF_KEY_A2DP_CONN_COUNT "ProfileA2dp_ConnectCount"
#define IOT_CONF_KEY_A2DP_CONN_FAIL_COUNT "ProfileA2dp_ConnectFailCount"
#define IOT_CONF_KEY_HFP_ROLE "ProfileHfp_Role"
#define IOT_CONF_KEY_HFP_VERSION "ProfileHfp_Version"
#define IOT_CONF_KEY_HFP_CODECTYPE "ProfileHfp_CodecType"
#define IOT_CONF_KEY_HFP_SLC_CONN_COUNT "ProfileHfp_SlcConnectCount"
#define IOT_CONF_KEY_HFP_SLC_CONN_FAIL_COUNT "ProfileHfp_SlcConnectFailCount"
#define IOT_CONF_KEY_HFP_SCO_CONN_COUNT "ProfileHfp_ScoConnectCount"
#define IOT_CONF_KEY_HFP_SCO_CONN_FAIL_COUNT "ProfileHfp_ScoConnectFailCount"
#define IOT_CONF_KEY_HFP_FEATURES "ProfileHfp_Features"
#define IOT_CONF_KEY_AVRCP_CTRL_VERSION "ProfileAvrcp_ControllerVersion"
#define IOT_CONF_KEY_AVRCP_TG_VERSION "ProfileAvrcp_TargetVersion"
#define IOT_CONF_KEY_AVRCP_CONN_COUNT "ProfileAvrcp_ConnectCount"
#define IOT_CONF_KEY_AVRCP_CONN_FAIL_COUNT "ProfileAvrcp_ConnectFailCount"
#define IOT_CONF_KEY_AVRCP_FEATURES "ProfileAvrcp_Features"
#define IOT_CONF_KEY_HID_ROLE "ProfileHid_Role"
#define IOT_CONF_KEY_HID_VERSION "ProfileHid_Version"
#define IOT_CONF_KEY_HID_CONN_COUNT "ProfileHid_ConnectCount"
#define IOT_CONF_KEY_HID_CONN_FAIL_COUNT "ProfileHid_ConnectFailCount"
#define IOT_CONF_KEY_PBAP_ROLE "ProfilePbap_Role"
#define IOT_CONF_KEY_PBAP_VERSION "ProfilePbap_Version"
#define IOT_CONF_KEY_PBAP_CONN_COUNT "ProfilePbap_ConnectCount"
#define IOT_CONF_KEY_PBAP_CONN_FAIL_COUNT "ProfilePbap_ConnectFailCount"
#define IOT_CONF_KEY_MAP_ROLE "ProfileMap_Role"
#define IOT_CONF_KEY_MAP_VERSION "ProfileMap_Version"
#define IOT_CONF_KEY_MAP_CONN_COUNT "ProfileMap_ConnectCount"
#define IOT_CONF_KEY_MAP_CONN_FAIL_COUNT "ProfileMap_ConnectFailCount"

#define IOT_CONF_VAL_PAIR_TYPE_LEGACY 0
#define IOT_CONF_VAL_PAIR_TYPE_SSP 1
#define IOT_CONF_VAL_LE_PAIRTYPE_LEGACY 0
#define IOT_CONF_VAL_LE_PAIRTYPE_SECURE 1
#define IOT_CONF_VAL_RECORDED_DEFAULT 0

#define IOT_CONF_VAL_A2DP_ROLE_SINK 0
#define IOT_CONF_VAL_A2DP_ROLE_SOURCE 1
#define IOT_CONF_VAL_A2DP_CODECTYPE_SBC 0x01
#define IOT_CONF_VAL_A2DP_CODECTYPE_APTX 0x02
#define IOT_CONF_VAL_A2DP_CODECTYPE_APTXHD 0x04
#define IOT_CONF_VAL_A2DP_CODECTYPE_AAC 0x08
#define IOT_CONF_VAL_A2DP_CODECTYPE_LDAC 0x10
#define IOT_CONF_VAL_A2DP_CODECTYPE_APTXADAPTIVE 0x20
#define IOT_CONF_VAL_A2DP_CODECTYPE_APTXTWS 0x40

#define IOT_CONF_VAL_HFP_ROLE_CLIENT 0
#define IOT_CONF_VAL_HFP_ROLE_AG 1
#define IOT_CONF_VAL_HFP_CODECTYPE_CVSD 0x01
#define IOT_CONF_VAL_HFP_CODECTYPE_CVSDMSBC 0x02

#define IOT_CONF_VAL_HID_ROLE_HOST 0
#define IOT_CONF_VAL_HID_ROLE_DEVICE 1

#define IOT_CONF_VAL_PBAP_ROLE_CLIENT 0
#define IOT_CONF_VAL_PBAP_ROLE_SERVER 1

#define IOT_CONF_VAL_MAP_ROLE_CLIENT 0
#define IOT_CONF_VAL_MAP_ROLE_SERVER 1

#define IOT_CONF_BYTE_NUM_1 1
#define IOT_CONF_BYTE_NUM_2 2
#define IOT_CONF_BYTE_NUM_3 3
#define IOT_CONF_BYTE_NUM_4 4