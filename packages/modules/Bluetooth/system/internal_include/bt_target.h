/******************************************************************************
 *
 *  Copyright (c) 2014 The Android Open Source Project
 *  Copyright 1999-2016 Broadcom Corporation
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

#ifndef BT_TARGET_H
#define BT_TARGET_H

#ifndef FALSE
#define FALSE false
#endif

#ifndef TRUE
#define TRUE true
#endif

//------------------Added from bdroid_buildcfg.h---------------------
#ifndef L2CAP_EXTFEA_SUPPORTED_MASK
#define L2CAP_EXTFEA_SUPPORTED_MASK                                            \
  (L2CAP_EXTFEA_ENH_RETRANS | L2CAP_EXTFEA_STREAM_MODE | L2CAP_EXTFEA_NO_CRC | \
   L2CAP_EXTFEA_FIXED_CHNLS)
#endif

#ifndef BTUI_OPS_FORMATS
#define BTUI_OPS_FORMATS (BTA_OP_VCARD21_MASK | BTA_OP_ANY_MASK)
#endif

#ifndef BTA_RFC_MTU_SIZE
#define BTA_RFC_MTU_SIZE \
  (L2CAP_MTU_SIZE - L2CAP_MIN_OFFSET - RFCOMM_DATA_OVERHEAD)
#endif

#ifndef BTA_PAN_INCLUDED
#define BTA_PAN_INCLUDED TRUE
#endif

#ifndef BTA_HD_INCLUDED
#define BTA_HD_INCLUDED TRUE
#endif

#ifndef BTA_HH_INCLUDED
#define BTA_HH_INCLUDED TRUE
#endif

#ifndef BTA_HH_ROLE
#define BTA_HH_ROLE BTA_CENTRAL_ROLE_PREF
#endif

#ifndef AVDT_VERSION
#define AVDT_VERSION 0x0103
#endif

#ifndef BTA_AG_AT_MAX_LEN
#define BTA_AG_AT_MAX_LEN 512
#endif

#ifndef BTA_AV_RET_TOUT
#define BTA_AV_RET_TOUT 15
#endif

#ifndef BTA_DM_SDP_DB_SIZE
#define BTA_DM_SDP_DB_SIZE 20000
#endif

#ifndef AG_VOICE_SETTINGS
#define AG_VOICE_SETTINGS HCI_DEFAULT_VOICE_SETTINGS
#endif

// How long to wait before activating sniff mode after entering the
// idle state for server FT/RFCOMM, OPS connections
#ifndef BTA_FTS_OPS_IDLE_TO_SNIFF_DELAY_MS
#define BTA_FTS_OPS_IDLE_TO_SNIFF_DELAY_MS 7000
#endif

// How long to wait before activating sniff mode after entering the
// idle state for client FT/RFCOMM connections
#ifndef BTA_FTC_IDLE_TO_SNIFF_DELAY_MS
#define BTA_FTC_IDLE_TO_SNIFF_DELAY_MS 5000
#endif

//------------------End added from bdroid_buildcfg.h---------------------

/******************************************************************************
 *
 * Buffer sizes
 *
 *****************************************************************************/

#ifndef BT_DEFAULT_BUFFER_SIZE
#define BT_DEFAULT_BUFFER_SIZE (4096 + 16)
#endif

#ifndef BT_SMALL_BUFFER_SIZE
#define BT_SMALL_BUFFER_SIZE 660
#endif

/* Receives HCI events from the lower-layer. */
#ifndef HCI_CMD_BUF_SIZE
#define HCI_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

/* Sends SDP data packets. */
#ifndef SDP_DATA_BUF_SIZE
#define SDP_DATA_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/* Sends RFCOMM command packets. */
#ifndef RFCOMM_CMD_BUF_SIZE
#define RFCOMM_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

/* Sends RFCOMM data packets. */
#ifndef RFCOMM_DATA_BUF_SIZE
#define RFCOMM_DATA_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/* Sends L2CAP packets to the peer and HCI messages to the controller. */
#ifndef L2CAP_CMD_BUF_SIZE
#define L2CAP_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

/* Number of ACL buffers to assign to LE */
/*
 * TODO: Do we need this?
 * It was used when the HCI buffers were shared with BR/EDR.
 */
#ifndef L2C_DEF_NUM_BLE_BUF_SHARED
#define L2C_DEF_NUM_BLE_BUF_SHARED 1
#endif

/* Used by BTM when it sends HCI commands to the controller. */
#ifndef BTM_CMD_BUF_SIZE
#define BTM_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

/* BNEP data and protocol messages. */
#ifndef BNEP_BUF_SIZE
#define BNEP_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/* AVDTP buffer size for protocol messages */
#ifndef AVDT_CMD_BUF_SIZE
#define AVDT_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

#ifndef PAN_BUF_SIZE
#define PAN_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/* Maximum number of buffers to allocate for PAN */
#ifndef PAN_BUF_MAX
#define PAN_BUF_MAX 100
#endif

/* AVCTP buffer size for protocol messages */
#ifndef AVCT_CMD_BUF_SIZE
#define AVCT_CMD_BUF_SIZE 288
#endif

/* AVRCP buffer size for protocol messages */
#ifndef AVRC_CMD_BUF_SIZE
#define AVRC_CMD_BUF_SIZE 288
#endif

/* AVRCP Metadata buffer size for protocol messages */
#ifndef AVRC_META_CMD_BUF_SIZE
#define AVRC_META_CMD_BUF_SIZE BT_SMALL_BUFFER_SIZE
#endif

/* GATT Data sending buffer size */
#ifndef GATT_DATA_BUF_SIZE
#define GATT_DATA_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

/******************************************************************************
 *
 * BTM
 *
 *****************************************************************************/

/* Cancel Inquiry on incoming SSP */
#ifndef BTM_NO_SSP_ON_INQUIRY
#define BTM_NO_SSP_ON_INQUIRY FALSE
#endif

#ifndef DISABLE_WBS
#define DISABLE_WBS FALSE
#endif

/**************************
 * Initial SCO TX credit
 ************************/
/* The size of buffer used for TX SCO data packets. The size should be divisible
 * by BTM_MSBC_CODE_SIZE(240) and BTM_LC3_CODE_SIZE(480). */
#ifndef BTM_SCO_DATA_SIZE_MAX
#define BTM_SCO_DATA_SIZE_MAX 480
#endif

/* The size in bytes of the BTM inquiry database. */
#ifndef BTM_INQ_DB_SIZE
#define BTM_INQ_DB_SIZE 80
#endif

/* Sets the Page_Scan_Window:  the length of time that the device is performing
 * a page scan. */
#ifndef BTM_DEFAULT_CONN_WINDOW
#define BTM_DEFAULT_CONN_WINDOW 0x0012
#endif

/* Sets the Page_Scan_Activity:  the interval between the start of two
 * consecutive page scans. */
#ifndef BTM_DEFAULT_CONN_INTERVAL
#define BTM_DEFAULT_CONN_INTERVAL 0x0400
#endif

/* When automatic inquiry scan is enabled, this sets the inquiry scan window. */
#ifndef BTM_DEFAULT_DISC_WINDOW
#define BTM_DEFAULT_DISC_WINDOW 0x0012
#endif

/* When automatic inquiry scan is enabled, this sets the inquiry scan interval.
 */
#ifndef BTM_DEFAULT_DISC_INTERVAL
#define BTM_DEFAULT_DISC_INTERVAL 0x0800
#endif

/* The number of SCO links. */
#ifndef BTM_MAX_SCO_LINKS
#define BTM_MAX_SCO_LINKS 6
#endif

/* The number of security records for peer devices. */
#ifndef BTM_SEC_MAX_DEVICE_RECORDS
#define BTM_SEC_MAX_DEVICE_RECORDS 100
#endif

/* The number of security records for services. */
#ifndef BTM_SEC_MAX_SERVICE_RECORDS
#define BTM_SEC_MAX_SERVICE_RECORDS 32
#endif

/* If True, force a retrieval of remote device name for each bond in case it's
 * changed */
#ifndef BTM_SEC_FORCE_RNR_FOR_DBOND
#define BTM_SEC_FORCE_RNR_FOR_DBOND FALSE
#endif

/* Maximum device name length used in btm database. */
#ifndef BTM_MAX_REM_BD_NAME_LEN
#define BTM_MAX_REM_BD_NAME_LEN 248
#endif

/* Maximum local device name length stored btm database */
#ifndef BTM_MAX_LOC_BD_NAME_LEN
#define BTM_MAX_LOC_BD_NAME_LEN 248
#endif

/* Maximum service name stored with security authorization (0 if not needed) */
#ifndef BTM_SEC_SERVICE_NAME_LEN
#define BTM_SEC_SERVICE_NAME_LEN BT_MAX_SERVICE_NAME_LEN
#endif

/* Maximum length of the service name. */
#ifndef BT_MAX_SERVICE_NAME_LEN
#define BT_MAX_SERVICE_NAME_LEN 21
#endif

/* The maximum number of clients that can register with the power manager. */
#ifndef BTM_MAX_PM_RECORDS
#define BTM_MAX_PM_RECORDS 2
#endif

/* If the user does not respond to security process requests within this many
 * seconds, a negative response would be sent automatically.
 * 30 is LMP response timeout value */
#ifndef BTM_SEC_TIMEOUT_VALUE
#define BTM_SEC_TIMEOUT_VALUE 35
#endif

/* Maximum number of callbacks that can be registered using
 * BTM_RegisterForVSEvents */
#ifndef BTM_MAX_VSE_CALLBACKS
#define BTM_MAX_VSE_CALLBACKS 3
#endif

/******************************************
 *    Lisbon Features
 ******************************************/
/* This is set to TRUE if the FEC is required for EIR packet. */
#ifndef BTM_EIR_DEFAULT_FEC_REQUIRED
#define BTM_EIR_DEFAULT_FEC_REQUIRED TRUE
#endif

/* The IO capability of the local device (for Simple Pairing) */
#ifndef BTM_LOCAL_IO_CAPS
#define BTM_LOCAL_IO_CAPS BTM_IO_CAP_IO
#endif

/*************************
 * End of Lisbon Features
 *************************/

/******************************************************************************
 *
 * L2CAP
 *
 *****************************************************************************/

/* The maximum number of simultaneous links that L2CAP can support. */
#ifndef MAX_L2CAP_LINKS
#define MAX_L2CAP_LINKS 16
#endif

/* The maximum number of simultaneous channels that L2CAP can support. */
#ifndef MAX_L2CAP_CHANNELS
#define MAX_L2CAP_CHANNELS 64
#endif

/* The maximum number of simultaneous applications that can register with L2CAP.
 */
#ifndef MAX_L2CAP_CLIENTS
#define MAX_L2CAP_CLIENTS 15
#endif

/* The number of seconds of link inactivity before a link is disconnected. */
#ifndef L2CAP_LINK_INACTIVITY_TOUT
#define L2CAP_LINK_INACTIVITY_TOUT 4
#endif

/* The number of seconds of link inactivity after bonding before a link is
 * disconnected. */
#ifndef L2CAP_BONDING_TIMEOUT
#define L2CAP_BONDING_TIMEOUT 3
#endif

/* The time from the HCI connection complete to disconnect if no channel is
 * established. */
#ifndef L2CAP_LINK_STARTUP_TOUT
#define L2CAP_LINK_STARTUP_TOUT 60
#endif

/* The L2CAP MTU; must be in accord with the HCI ACL buffer size. */
#ifndef L2CAP_MTU_SIZE
#define L2CAP_MTU_SIZE 1691
#endif

/* Minimum number of ACL credit for high priority link */
#ifndef L2CAP_HIGH_PRI_MIN_XMIT_QUOTA
#define L2CAP_HIGH_PRI_MIN_XMIT_QUOTA 5
#endif

/* Used for features using fixed channels; set to zero if no fixed channels
 * supported (BLE, etc.) */
/* Excluding L2CAP signaling channel and UCD */
#ifndef L2CAP_NUM_FIXED_CHNLS
#define L2CAP_NUM_FIXED_CHNLS 32
#endif

/* First fixed channel supported */
#ifndef L2CAP_FIRST_FIXED_CHNL
#define L2CAP_FIRST_FIXED_CHNL 4
#endif

#ifndef L2CAP_LAST_FIXED_CHNL
#define L2CAP_LAST_FIXED_CHNL \
  (L2CAP_FIRST_FIXED_CHNL + L2CAP_NUM_FIXED_CHNLS - 1)
#endif

/* Used for conformance testing ONLY:  When TRUE lets scriptwrapper overwrite
 * info response */
#ifndef L2CAP_CONFORMANCE_TESTING
#define L2CAP_CONFORMANCE_TESTING FALSE
#endif

/*
 * Max bytes per connection to buffer locally before dropping the
 * connection if local client does not receive it  - default is 1MB
 */
#ifndef L2CAP_MAX_RX_BUFFER
#define L2CAP_MAX_RX_BUFFER 0x100000
#endif

/******************************************************************************
 *
 * BLE
 *
 *****************************************************************************/

#ifndef LOCAL_BLE_CONTROLLER_ID
#define LOCAL_BLE_CONTROLLER_ID 1
#endif

/* The maximum number of simultaneous applications that can register with LE
 * L2CAP. */
#ifndef BLE_MAX_L2CAP_CLIENTS
#define BLE_MAX_L2CAP_CLIENTS 15
#endif

/******************************************************************************
 *
 * ATT/GATT Protocol/Profile Settings
 *
 *****************************************************************************/
#ifndef GATT_MAX_SR_PROFILES
#define GATT_MAX_SR_PROFILES 32 /* max is 32 */
#endif

#ifndef GATT_MAX_APPS
#define GATT_MAX_APPS 32 /* note: 2 apps used internally GATT and GAP */
#endif

/* connection manager doesn't generate it's own IDs. Instead, all GATT clients
 * use their gatt_if to identify against conection manager. When stack tries to
 * create l2cap connection, it will use this fixed ID. */
#define CONN_MGR_ID_L2CAP (GATT_MAX_APPS + 10)

#ifndef GATT_MAX_PHY_CHANNEL
#define GATT_MAX_PHY_CHANNEL 7
#endif

/* Used for conformance testing ONLY */
#ifndef GATT_CONFORMANCE_TESTING
#define GATT_CONFORMANCE_TESTING FALSE
#endif

/* Used only for GATT Multiple Variable Length Notifications PTS tests */
#ifndef GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_NOTIF
#define GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_NOTIF FALSE
#endif

/* Used only for GATT Multiple Variable Length READ PTS tests */
#ifndef GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_READ
#define GATT_UPPER_TESTER_MULT_VARIABLE_LENGTH_READ FALSE
#endif

/******************************************************************************
 *
 * CSIP
 *
 *****************************************************************************/

/* Used to trigger invalid behaviour of CSIP test case PTS */
#ifndef CSIP_UPPER_TESTER_FORCE_TO_SEND_LOCK
#define CSIP_UPPER_TESTER_FORCE_TO_SEND_LOCK FALSE
#endif

/******************************************************************************
 *
 * SMP
 *
 *****************************************************************************/
#ifndef SMP_DEFAULT_AUTH_REQ
#define SMP_DEFAULT_AUTH_REQ SMP_AUTH_NB_ENC_ONLY
#endif

#ifndef SMP_MAX_ENC_KEY_SIZE
#define SMP_MAX_ENC_KEY_SIZE 16
#endif

/* minimum link timeout after SMP pairing is done, leave room for key exchange
   and racing condition for the following service connection.
   Prefer greater than 0 second, and no less than default inactivity link idle
   timer(L2CAP_LINK_INACTIVITY_TOUT) in l2cap) */
#ifndef SMP_LINK_TOUT_MIN
#if (L2CAP_LINK_INACTIVITY_TOUT > 0)
#define SMP_LINK_TOUT_MIN L2CAP_LINK_INACTIVITY_TOUT
#else
#define SMP_LINK_TOUT_MIN 2
#endif
#endif
/******************************************************************************
 *
 * SDP
 *
 *****************************************************************************/

/* The maximum number of SDP records the server can support. */
#ifndef SDP_MAX_RECORDS
#define SDP_MAX_RECORDS 30
#endif

/* The maximum number of attributes in each record. */
#ifndef SDP_MAX_REC_ATTR
#define SDP_MAX_REC_ATTR 25
#endif

#ifndef SDP_MAX_PAD_LEN
#define SDP_MAX_PAD_LEN 600
#endif

/* The maximum length, in bytes, of an attribute. */
#ifndef SDP_MAX_ATTR_LEN
#define SDP_MAX_ATTR_LEN 400
#endif

/* The maximum number of attribute filters supported by SDP databases. */
#ifndef SDP_MAX_ATTR_FILTERS
#define SDP_MAX_ATTR_FILTERS 15
#endif

/* The maximum number of UUID filters supported by SDP databases. */
#ifndef SDP_MAX_UUID_FILTERS
#define SDP_MAX_UUID_FILTERS 3
#endif

/* The maximum number of record handles retrieved in a search. */
#ifndef SDP_MAX_DISC_SERVER_RECS
#define SDP_MAX_DISC_SERVER_RECS 21
#endif

/* The size of a scratchpad buffer, in bytes, for storing the response to an
 * attribute request. */
#ifndef SDP_MAX_LIST_BYTE_COUNT
#define SDP_MAX_LIST_BYTE_COUNT 4096
#endif

/* The maximum number of parameters in an SDP protocol element. */
#ifndef SDP_MAX_PROTOCOL_PARAMS
#define SDP_MAX_PROTOCOL_PARAMS 2
#endif

/* The maximum number of simultaneous client and server connections. */
#ifndef SDP_MAX_CONNECTIONS
#define SDP_MAX_CONNECTIONS 4
#endif

/* The MTU size for the L2CAP configuration. */
#ifndef SDP_MTU_SIZE
#define SDP_MTU_SIZE 1024
#endif

/******************************************************************************
 *
 * RFCOMM
 *
 *****************************************************************************/

/* The maximum number of ports supported. */
#ifndef MAX_RFC_PORTS
#define MAX_RFC_PORTS 30
#endif

/* The maximum simultaneous links to different devices. */
#ifndef MAX_BD_CONNECTIONS
#define MAX_BD_CONNECTIONS 16
#endif

/* The port receive queue low watermark level, in bytes. */
#ifndef PORT_RX_LOW_WM
#define PORT_RX_LOW_WM (BTA_RFC_MTU_SIZE * PORT_RX_BUF_LOW_WM)
#endif

/* The port receive queue high watermark level, in bytes. */
#ifndef PORT_RX_HIGH_WM
#define PORT_RX_HIGH_WM (BTA_RFC_MTU_SIZE * PORT_RX_BUF_HIGH_WM)
#endif

/* The port receive queue critical watermark level, in bytes. */
#ifndef PORT_RX_CRITICAL_WM
#define PORT_RX_CRITICAL_WM (BTA_RFC_MTU_SIZE * PORT_RX_BUF_CRITICAL_WM)
#endif

/* The port receive queue low watermark level, in number of buffers. */
#ifndef PORT_RX_BUF_LOW_WM
#define PORT_RX_BUF_LOW_WM 4
#endif

/* The port receive queue high watermark level, in number of buffers. */
#ifndef PORT_RX_BUF_HIGH_WM
#define PORT_RX_BUF_HIGH_WM 10
#endif

/* The port receive queue critical watermark level, in number of buffers. */
#ifndef PORT_RX_BUF_CRITICAL_WM
#define PORT_RX_BUF_CRITICAL_WM 15
#endif

/* The port transmit queue high watermark level, in bytes. */
#ifndef PORT_TX_HIGH_WM
#define PORT_TX_HIGH_WM (BTA_RFC_MTU_SIZE * PORT_TX_BUF_HIGH_WM)
#endif

/* The port transmit queue critical watermark level, in bytes. */
#ifndef PORT_TX_CRITICAL_WM
#define PORT_TX_CRITICAL_WM (BTA_RFC_MTU_SIZE * PORT_TX_BUF_CRITICAL_WM)
#endif

/* The port transmit queue high watermark level, in number of buffers. */
#ifndef PORT_TX_BUF_HIGH_WM
#define PORT_TX_BUF_HIGH_WM 10
#endif

/* The port transmit queue high watermark level, in number of buffers. */
#ifndef PORT_TX_BUF_CRITICAL_WM
#define PORT_TX_BUF_CRITICAL_WM 15
#endif

/* The RFCOMM multiplexer preferred flow control mechanism. */
#ifndef PORT_FC_DEFAULT
#define PORT_FC_DEFAULT PORT_FC_CREDIT
#endif

/******************************************************************************
 *
 * BNEP
 *
 *****************************************************************************/

#ifndef BNEP_INCLUDED
#define BNEP_INCLUDED TRUE
#endif

/* Maximum number of protocol filters supported. */
#ifndef BNEP_MAX_PROT_FILTERS
#define BNEP_MAX_PROT_FILTERS 5
#endif

/* Maximum number of multicast filters supported. */
#ifndef BNEP_MAX_MULTI_FILTERS
#define BNEP_MAX_MULTI_FILTERS 5
#endif

/* Preferred MTU size. */
#ifndef BNEP_MTU_SIZE
#define BNEP_MTU_SIZE L2CAP_MTU_SIZE
#endif

/* Maximum number of buffers allowed in transmit data queue. */
#ifndef BNEP_MAX_XMITQ_DEPTH
#define BNEP_MAX_XMITQ_DEPTH 20
#endif

/* Maximum number BNEP of connections supported. */
#ifndef BNEP_MAX_CONNECTIONS
#define BNEP_MAX_CONNECTIONS 7
#endif

/******************************************************************************
 *
 * AVDTP
 *
 *****************************************************************************/

/* Number of simultaneous links to different peer devices. */
#ifndef AVDT_NUM_LINKS
#define AVDT_NUM_LINKS 6
#endif

/* Number of simultaneous stream endpoints. */
#ifndef AVDT_NUM_SEPS
#define AVDT_NUM_SEPS 12
#endif

/* Number of transport channels setup by AVDT for all media streams */
#ifndef AVDT_NUM_TC_TBL
#define AVDT_NUM_TC_TBL (AVDT_NUM_SEPS + AVDT_NUM_LINKS)
#endif

/* Maximum size in bytes of the content protection information element. */
#ifndef AVDT_PROTECT_SIZE
#define AVDT_PROTECT_SIZE 90
#endif

/* Default sink delay value in ms. */
#ifndef AVDT_SINK_DELAY_MS
#define AVDT_SINK_DELAY_MS 300
#endif

/******************************************************************************
 *
 * PAN
 *
 *****************************************************************************/

#ifndef PAN_INCLUDED
#define PAN_INCLUDED TRUE
#endif

/* This will enable the PANU role */
#ifndef PAN_SUPPORTS_ROLE_PANU
#define PAN_SUPPORTS_ROLE_PANU TRUE
#endif

/* This will enable the NAP role */
#ifndef PAN_SUPPORTS_ROLE_NAP
#define PAN_SUPPORTS_ROLE_NAP TRUE
#endif

/* This is just for debugging purposes */
#ifndef PAN_SUPPORTS_DEBUG_DUMP
#define PAN_SUPPORTS_DEBUG_DUMP TRUE
#endif

/* Maximum number of PAN connections allowed */
#ifndef MAX_PAN_CONNS
#define MAX_PAN_CONNS 7
#endif

/* Default service name for NAP role */
#ifndef PAN_NAP_DEFAULT_SERVICE_NAME
#define PAN_NAP_DEFAULT_SERVICE_NAME "Network Access Point Service"
#endif

/* Default service name for PANU role */
#ifndef PAN_PANU_DEFAULT_SERVICE_NAME
#define PAN_PANU_DEFAULT_SERVICE_NAME "PAN User Service"
#endif

/* Default description for NAP role service */
#ifndef PAN_NAP_DEFAULT_DESCRIPTION
#define PAN_NAP_DEFAULT_DESCRIPTION "NAP"
#endif

/* Default description for PANU role service */
#ifndef PAN_PANU_DEFAULT_DESCRIPTION
#define PAN_PANU_DEFAULT_DESCRIPTION "PANU"
#endif

/******************************************************************************
 *
 * GAP
 *
 *****************************************************************************/

/* The maximum number of simultaneous GAP L2CAP connections. */
#ifndef GAP_MAX_CONNECTIONS
#define GAP_MAX_CONNECTIONS 30
#endif

/******************************************************************************
 *
 * HID
 *
 *****************************************************************************/

/* HID Device Role Included */
#ifndef HID_DEV_INCLUDED
#define HID_DEV_INCLUDED TRUE
#endif

#ifndef HID_CONTROL_BUF_SIZE
#define HID_CONTROL_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

#ifndef HID_INTERRUPT_BUF_SIZE
#define HID_INTERRUPT_BUF_SIZE BT_DEFAULT_BUFFER_SIZE
#endif

#ifndef HID_DEV_MTU_SIZE
#define HID_DEV_MTU_SIZE 512
#endif

/*************************************************************************
 * Definitions for Both HID-Host & Device
*/
#ifndef HID_MAX_SVC_NAME_LEN
#define HID_MAX_SVC_NAME_LEN 32
#endif

#ifndef HID_MAX_SVC_DESCR_LEN
#define HID_MAX_SVC_DESCR_LEN 32
#endif

#ifndef HID_MAX_PROV_NAME_LEN
#define HID_MAX_PROV_NAME_LEN 32
#endif

/*************************************************************************
 * Definitions for HID-Host
*/
#ifndef HID_HOST_INCLUDED
#define HID_HOST_INCLUDED TRUE
#endif

#ifndef HID_HOST_MAX_DEVICES
#define HID_HOST_MAX_DEVICES 7
#endif

#ifndef HID_HOST_MTU
#define HID_HOST_MTU 640
#endif

#ifndef HID_HOST_MAX_CONN_RETRY
#define HID_HOST_MAX_CONN_RETRY 1
#endif

#ifndef HID_HOST_REPAGE_WIN
#define HID_HOST_REPAGE_WIN 2
#endif

/******************************************************************************
 *
 * AVCTP
 *
 *****************************************************************************/

/* Number of simultaneous ACL links to different peer devices. */
#ifndef AVCT_NUM_LINKS
#define AVCT_NUM_LINKS 6
#endif

/* Number of simultaneous AVCTP connections. */
#ifndef AVCT_NUM_CONN
#define AVCT_NUM_CONN 14  // 2 * MaxDevices + 2
#endif

/******************************************************************************
 *
 * AVRCP
 *
 *****************************************************************************/

#ifndef DUMP_PCM_DATA
#define DUMP_PCM_DATA FALSE
#endif

/******************************************************************************
 *
 * BTA
 *
 *****************************************************************************/
/* BTA EIR canned UUID list (default is dynamic) */
#ifndef BTA_EIR_CANNED_UUID_LIST
#define BTA_EIR_CANNED_UUID_LIST FALSE
#endif

/* Number of supported customer UUID in EIR */
#ifndef BTA_EIR_SERVER_NUM_CUSTOM_UUID
#define BTA_EIR_SERVER_NUM_CUSTOM_UUID 8
#endif

/******************************************************************************
 *
 * Tracing:  Include trace header file here.
 *
 *****************************************************************************/

#endif /* BT_TARGET_H */
