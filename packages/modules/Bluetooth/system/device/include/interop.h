/******************************************************************************
 *
 *  Copyright 2015 Google, Inc.
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

#include <hardware/bluetooth.h>
#include <stdbool.h>

#include "include/hardware/bluetooth.h"
#include "osi/include/list.h"
#include "raw_address.h"

static const char INTEROP_MODULE[] = "interop_module";

// NOTE:
// Only add values at the end of this enum and before END_OF_INTEROP_LIST
// do NOT delete values as they may be used in dynamic device configuration.
typedef enum {

  BEGINNING_OF_INTEROP_LIST = 0,
  // Disable secure connections
  // This is for pre BT 4.1/2 devices that do not handle secure mode
  // very well.
  INTEROP_DISABLE_LE_SECURE_CONNECTIONS = BEGINNING_OF_INTEROP_LIST,

  // Some devices have proven problematic during the pairing process, often
  // requiring multiple retries to complete pairing. To avoid degrading the user
  // experience for those devices, automatically re-try pairing if page
  // timeouts are received during pairing.
  INTEROP_AUTO_RETRY_PAIRING,

  // Devices requiring this workaround do not handle Bluetooth Absolute Volume
  // control correctly, leading to undesirable (potentially harmful) volume
  // levels or general lack of controlability.
  INTEROP_DISABLE_ABSOLUTE_VOLUME,

  // Disable automatic pairing with headsets/car-kits
  // Some car kits do not react kindly to a failed pairing attempt and
  // do not allow immediate re-pairing. Rejectlist these so that the initial
  // pairing attempt makes it to the user instead.
  INTEROP_DISABLE_AUTO_PAIRING,

  // Use a fixed pin for specific keyboards
  // Keyboards should use a variable pin at all times. However, some keyboards
  // require a fixed pin of all 0000. This workaround enables auto pairing for
  // those keyboards.
  INTEROP_KEYBOARD_REQUIRES_FIXED_PIN,

  // Some headsets have audio jitter issues because of increased
  // re-transmissions as the 3 Mbps packets have a lower link margin, and are
  // more prone to interference. We can disable 3DH packets (use only 2DH
  // packets) for the ACL link to improve sensitivity when streaming A2DP audio
  // to the headset. Air sniffer logs show reduced re-transmissions after
  // switching to 2DH packets.

  // Disable 3Mbps packets and use only 2Mbps packets for ACL links when
  // streaming audio.
  INTEROP_2MBPS_LINK_ONLY,

  // Some HID devices have proven problematic behaviour if SDP is initiated
  // again while HID connection is in progress or if more than 1 SDP connection
  // is  created with those HID devices rsulting in issues of connection
  // failure with such devices. To avoid degrading the user experience with
  // those devices, SDP is not attempted as part of pairing process.
  INTEROP_DISABLE_SDP_AFTER_PAIRING,

  // HID Keyboards that claim support for multitouch functionality have issue
  // with  normal functioning of keyboard because of issues in USB HID kernel
  // driver. To avoid degrading the user experience with those devices,
  // digitizer record is removed from the report descriptor.
  INTEROP_REMOVE_HID_DIG_DESCRIPTOR,

  // Some HID devices have problematic behaviour where when hid link is in Sniff
  // and DUT is in peripheral role for SCO link (not eSCO) any solution cannot
  // maintain the link as SCO scheduling over a short period will overlap with
  // Sniff link due to peripheral drift. To avoid degrading the user experience
  // with those devices, sniff is disabled from link policy when sco is active,
  // and enabled when sco is disabled.
  INTEROP_DISABLE_SNIFF_DURING_SCO,

  // Do not use supervision timeout value received from preferred connection
  // parameters, use 3s instead. Use with HID only.
  INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S,

  // Do not send service changed indications (GATT client).
  // This should be removed after the characteristic is implmeented b/62088395.
  INTEROP_GATTC_NO_SERVICE_CHANGED_IND,

  // Few carkits take long time to start sending AT commands
  // Increase AG_CONN TIMEOUT so that AG connection go through
  INTEROP_INCREASE_AG_CONN_TIMEOUT,

  // Some HOGP devices do not respond well when we switch from default LE conn
  // parameters to preferred conn params immediately post connection. Disable
  // automatic switching to preferred conn params for such devices and allow
  // them to explicitly ask for it.
  INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS,

  // certain remote A2DP sinks have issue playing back Music in AAC format.
  // disable AAC for those headsets so that it switch to SBC
  INTEROP_DISABLE_AAC_CODEC,

  // certain remote A2DP sinks have issue playing back Music in AAC VBR format.
  // disable AAC for those headsets so that it switch to AAC CBR
  INTEROP_DISABLE_AAC_VBR_CODEC,

  // Enable AAC only for allowlist of devices
  INTEROP_ENABLE_AAC_CODEC,

  // Disable role switch for headsets/car-kits
  // Some car kits initiate a role switch but won't initiate encryption
  // after role switch complete
  INTEROP_DISABLE_ROLE_SWITCH_POLICY,

  INTEROP_HFP_1_7_DENYLIST,

  INTEROP_HFP_1_8_DENYLIST,

  // Devices requiring this workaround do not handle Bluetooth PBAP 1.2 version
  // correctly, leading them to go in bad state. So for better interoperability
  // respond with PBAP 1.1 as supported version.
  INTEROP_ADV_PBAP_VER_1_1,

  // Devices requiring this workaround do not handle SSR max latency values as
  // mentioned, in their SDP HID Record properly and lead to connection timeout
  // or lags. To prevent such scenarios, device requiring this workaorund need
  // to use specific ssr max latency values.
  INTEROP_UPDATE_HID_SSR_MAX_LAT,

  // Some HID pointing devices have proven problematic behaviour if pairing is
  // initiated with them, resulting in no response for authentication request
  // and ultimately resulting in connection failure. To avoid degrading the user
  // experience with those devices, authentication request is not requested
  // explicitly.
  INTEROP_DISABLE_AUTH_FOR_HID_POINTING,

  // Do not use AVDTP RECONFIGURE when reconfiguring A2DP streams.
  // Some A2DP Sink devices report SUCCESS to the AVDTP RECONFIGURE command,
  // but fail to play the reconfigured audio stream.
  INTEROP_DISABLE_AVDTP_RECONFIGURE,

  // Create dynamic rejectlist to disable role switch.
  // Some car kits indicate that role switch is supported, but then reject
  // role switch attempts. After rejecting several role switch attempts,
  // such car kits will go into bad state.
  INTEROP_DYNAMIC_ROLE_SWITCH,

  // Few carkit hfp version is hfp1.5 but it support hfp indicator, violate spec
  // remove hfp indicator for such device
  INTEROP_DISABLE_HF_INDICATOR,

  // Disable role switch for headsets/car-kits.
  // Some car kits allow role switch but when the Phone initiates role switch,
  // the Remote device will go into bad state that will lead to LMP time out.
  INTEROP_DISABLE_ROLE_SWITCH,

  // Some remotes are very strict in receiving the call active
  // indicator and SCO connection request order for MT call.
  // If CIEV1,1 and SCO connection request are sent back to back
  // to SOC, it may send SCO connection request first then CIEV1,1
  // which may lead to remotes not rendering SCO audio.
  INTEROP_DELAY_SCO_FOR_MT_CALL,

  // Some remotes are taking too long to respond for codec negotiation.
  // Disable codec negotiation for such remotes and directly initiate
  // SCO Connection.
  INTEROP_DISABLE_CODEC_NEGOTIATION,

  // Some Carkits being AVRCP v1.3 upon receiving Play Application Setting
  // Command Response and notification, doesn't send Passthrough commands back
  // to DUT in Streaming State
  INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS,

  // Disable profile connection for headsets/car-kits
  // Some car kits going bad state when DUT initiate profile connection in
  // collision scenario Hence don't initaite profile level connections and wait
  // for incoming connetcion
  INTEROP_DISABLE_CONNECTION_AFTER_COLLISION,

  // Some LE devices have proven problematic behaviour if LE connection update
  // is initiated with them, resulting in no response after initiating LE
  // connection update and ultimately resulting in connection timeout. To avoid
  // degrading the user experience with those devices, LE connection update is
  // not requested explicitly for those devices.
  INTEROP_DISABLE_LE_CONN_UPDATES,

  // Devices requiring this workaround do not handle Bluetooth PBAP 1.1 version
  // correctly, leading them to go in bad state. So for better interoperability
  // respond with PBAP 1.2 as supported version.
  INTEROP_ADV_PBAP_VER_1_2,

  // Disable profile PCE SDP request for headsets/car-kits
  // Some car kits going bad state when DUT initiate PCE SDP request during
  // BONDING time Hence don't send PCE SDP request for to prevent this abnormal
  // behaviour.
  INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING,

  // Few remote sends avrcp browsing request just after host sends avrcp
  // browsing request leading collision which results in browsing channel open
  // failure. For such devices, collision timer is used avoid collision.
  INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION,

  // Some remote devices don't support sniff mode when the SCO is connected.
  // For such devices, disable sniff mode after SCO is connected and make
  // the link as active.
  INTEROP_DISABLE_SNIFF_LINK_DURING_SCO,

  // For some remote devicea, disable sniff mode during the call
  INTEROP_DISABLE_SNIFF_DURING_CALL,

  // Set a very low initial sniff subrating for HID devices that do not
  // set their own sniff interval.
  INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL,

  // Disable refresh_accept_signalling_timer
  INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER,

  // list of allowlisted media players
  INTEROP_BROWSE_PLAYER_ALLOW_LIST,

  // skip sending incoming state to denylisted devices
  INTEROP_SKIP_INCOMING_STATE,

  // Don't update avrcp paused status to some certain remote devices when a2dp
  // is still playing.
  INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE,

  // Some certain devices reject DUT initiated connection
  // when DUT tries to connect other auto-connectable profiles 6s after
  // peer connects the first one. To avoid such race condition, raised
  // connect other profiles timeout to 10s to make sure there is no
  // connection rejection from remote because of connection collision.
  INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES,
  // Disable remote name requst for some devices.
  // The public address of these devices are same as the Random address in ADV.
  // Then will get name by LE_Create_connection, actually fails,
  // but will block pairing.
  INTEROP_DISABLE_NAME_REQUEST,

  // Respond AVRCP profile version only 1.4 for some device.
  INTEROP_AVRCP_1_4_ONLY,

  // Disable sniff mode for headsets/car-kits
  // Some car kits supports sniff mode but when DUT initiates sniff req
  // Remote will go to bad state and its leads to LMP time out.
  INTEROP_DISABLE_SNIFF,

  // Do not send AVDTP SUSPEND while the playback is paused.
  // Some older A2DP Sink devices might not support to pause the streaming.
  INTEROP_DISABLE_AVDTP_SUSPEND,

  // Some car kits do not send the AT+BIND command while establishing the SLC
  // which causes an HFP profile connection failure
  INTEROP_SLC_SKIP_BIND_COMMAND,

  // Few remote devices do not understand AVRCP version greater than 1.3. For
  // these devices, we would like to denylist them and advertise AVRCP version
  // as 1.3
  INTEROP_AVRCP_1_3_ONLY,

  // As per the implementation for the incoming connection scenario if one
  // profile got connected and other profile didn't get connected, DUT starts
  // connect other profiles after 6sec timeout. For some certain device,
  // this timeout has been reduced to 2sec for triggering auto connect to
  // otherprofiles from DUT. So that Audio will get routed to BT device with
  // reduced delay.
  INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES,

  // Some remote devices are misbehaving when there is an active
  // call and MT call is in progress. We send call indicators for
  // active call and MT Call in progess. When active call is
  // terminated, we send call end indicator only to remote. This
  // is confusing remote and remotes are going into bad state.
  // Denylist remote devices to disconnect SCO when active call is
  // ended, fake MT call indicator again with some delay.
  INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR,

  // Some certain devices not rendering VOIP call audio if call indicators
  // are sent with a delay. Denylist the devices to send call
  // indicators back to back.
  INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK,

  // Some remotes need SCO immediately after SLC if there is an ongoing
  // call on the phone
  INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL,

  // Some LE mouses want to use preferred connection parameters
  INTEROP_ENABLE_PREFERRED_CONN_PARAMETER,

  // Some remotes drop SCO connection immediately after accept it,
  // Retry setup SCO once for those remote devices.
  INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO,

  // some remoted need a delay after outgoing call indicators
  // are sent before sco is opened
  INTEROP_DELAY_SCO_FOR_MO_CALL,

  // Some remote hid devices cannot work properly as they laod special hid usb
  // driver in kernel, so modify their vid/pid so that generic hid driver are
  // loaded.
  INTEROP_CHANGE_HID_VID_PID,

  // Some remote always set DUT in peripheral role during connection which
  // restricts setting supervision timeout to required value. This interop will
  // allow local device to be only central as role switch would be restricted
  // during connection.
  INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION,

  // Some remote devices have LMP version in[5.0, 5.2] but do not support robust
  // caching or correctly response with an error. We disable the database hash
  // lookup for such devices.
  INTEROP_DISABLE_ROBUST_CACHING,

  INTEROP_HFP_1_7_ALLOWLIST,

  INTEROP_HFP_1_9_ALLOWLIST,

  // Some device may have problem in reconnect flow,
  // need we initiate connection after signalling timeout
  INTEROP_IGNORE_DISC_BEFORE_SIGNALLING_TIMEOUT,

  // Certain devices have issue where they cannot handle ATT traffic during SMP
  // key exchange. For those devices, queued ATT writes are delayed until after
  // both encryption complete and SMP key exchange completed.
  INTEROP_SUSPEND_ATT_TRAFFIC_DURING_PAIRING,

  // This triggers a +CIEV command to set the call status for HFP devices.
  // It is required for some devices to provide sound.
  INTEROP_INSERT_CALL_WHEN_SCO_START,

  // Some device need the host to delay authentication process to avoid
  // collision.
  INTEROP_DELAY_AUTH,

  END_OF_INTEROP_LIST
} interop_feature_t;

// Check if a given |addr| matches a known interoperability workaround as
// identified by the |interop_feature_t| enum. This API is used for simple
// address based lookups where more information is not available. No
// look-ups or random address resolution are performed on |addr|.
bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr);

// Check if a given remote device |name| matches a known workaround.
// Name comparisons are case sensitive and do not allow for partial matches.
// If |name| is "TEST" and a workaround exists for "TESTING", then this function
// will return false. But, if |name| is "TESTING" and a workaround exists for
// "TEST", this function will return true.
// |name| cannot be null and must be null terminated.
bool interop_match_name(const interop_feature_t feature, const char* name);

// This api will lookup remote name with |addr| by btif_storage api internally.
// Then if either interop_match_addr or interop_match_name is matched, this
// function will return true.
bool interop_match_addr_or_name(const interop_feature_t feature,
                                const RawAddress* addr,
                                bt_status_t (*get_remote_device_property)(
                                    const RawAddress*, bt_property_t*));

// Check if a given |manufacturer| matches a known interoperability workaround
// as identified by the |interop_feature_t| enum. This API is used for
// manufacturer based lookups where more information is not available.
bool interop_match_manufacturer(const interop_feature_t feature,
                                uint16_t manufacturer);

// Check if a given |vendor_id, |product_id| matches a known
// interoperability workaround as identified by the |interop_feature_t|
// enum. This API is used for simple name based lookups where more information
// is not available.
bool interop_match_vendor_product_ids(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id);

// Add a dynamic interop database entry for a device matching the first |length|
// bytes of |addr|, implementing the workaround identified by |feature|.
// |addr| may not be null.
// |length| must be greater than 0 and less than RawAddress::kLength.
// As |interop_feature_t| is not exposed in the public API, feature must be a
// valid integer representing an option in the enum.
void interop_database_add(const uint16_t feature, const RawAddress* addr,
                          size_t length);

// Clear the dynamic portion of the interoperability workaround database.
void interop_database_clear(void);

// check if device version is matching with the interop database
bool interop_database_match_version(const interop_feature_t feature,
                                    uint16_t version);
// Check if a given |addr| matches a known interoperability workaround as
// identified by the |interop_feature_t| enum. This API is used for simple
// address based lookups where more information is not available. No look-ups or
// random address resolution are performed on |addr|. If address is matched, max
// latency for SSR stored for particular remote device is returned.
bool interop_match_addr_get_max_lat(const interop_feature_t feature,
                                    const RawAddress* addr, uint16_t* max_lat);

// This API is used for name based lookups for allowlisted media players.
// If allowlisted media players list found it will assign the media players list
// pointer to the argument passed and  return true else return false.
bool interop_get_allowlisted_media_players_list(list_t* p_bl_devices);

// Return feature's enum value according to feature'name.
int interop_feature_name_to_feature_id(const char* feature_name);
