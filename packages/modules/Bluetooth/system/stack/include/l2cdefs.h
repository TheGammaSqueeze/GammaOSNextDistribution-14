/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

#ifndef L2CDEFS_H
#define L2CDEFS_H

#include <cstdint>

#include "internal_include/bt_target.h"  // L2CAP_EXTFEA_SUPPORTED_MASK
#include "macros.h"

/* L2CAP command codes
*/
#define L2CAP_CMD_REJECT 0x01
#define L2CAP_CMD_CONN_REQ 0x02
#define L2CAP_CMD_CONN_RSP 0x03
#define L2CAP_CMD_CONFIG_REQ 0x04
#define L2CAP_CMD_CONFIG_RSP 0x05
#define L2CAP_CMD_DISC_REQ 0x06
#define L2CAP_CMD_DISC_RSP 0x07
#define L2CAP_CMD_ECHO_REQ 0x08
#define L2CAP_CMD_ECHO_RSP 0x09
#define L2CAP_CMD_INFO_REQ 0x0A
#define L2CAP_CMD_INFO_RSP 0x0B
#define L2CAP_CMD_AMP_CONN_REQ 0x0C
#define L2CAP_CMD_AMP_MOVE_REQ 0x0E
#define L2CAP_CMD_BLE_UPDATE_REQ 0x12
#define L2CAP_CMD_BLE_UPDATE_RSP 0x13
#define L2CAP_CMD_BLE_CREDIT_BASED_CONN_REQ 0x14
#define L2CAP_CMD_BLE_CREDIT_BASED_CONN_RES 0x15
#define L2CAP_CMD_BLE_FLOW_CTRL_CREDIT 0x16
/* Enhanced CoC */
#define L2CAP_CMD_CREDIT_BASED_CONN_REQ 0x17
#define L2CAP_CMD_CREDIT_BASED_CONN_RES 0x18
#define L2CAP_CMD_CREDIT_BASED_RECONFIG_REQ 0x19
#define L2CAP_CMD_CREDIT_BASED_RECONFIG_RES 0x1A

/* Define some packet and header lengths
*/
/* Length and CID                       */
#define L2CAP_PKT_OVERHEAD 4
/* Cmd code, Id and length              */
#define L2CAP_CMD_OVERHEAD 4
/* Reason (data is optional)            */
#define L2CAP_CMD_REJECT_LEN 2
/* PSM and source CID                   */
#define L2CAP_CONN_REQ_LEN 4
/* Dest CID, source CID, reason, status */
#define L2CAP_CONN_RSP_LEN 8
/* Dest CID, flags (data is optional)   */
#define L2CAP_CONFIG_REQ_LEN 4
/* Dest CID, flags, result,data optional*/
#define L2CAP_CONFIG_RSP_LEN 6
/* Dest CID, source CID                 */
#define L2CAP_DISC_REQ_LEN 4
/* Dest CID, source CID                 */
#define L2CAP_DISC_RSP_LEN 4
/* Data is optional                     */
#define L2CAP_ECHO_REQ_LEN 0
/* Data is optional                     */
#define L2CAP_ECHO_RSP_LEN 0
/* Info type, result (data is optional) */
#define L2CAP_INFO_RSP_LEN 4

/* Min and max interval, latency, tout  */
#define L2CAP_CMD_BLE_UPD_REQ_LEN 8
/* Result                               */
#define L2CAP_CMD_BLE_UPD_RSP_LEN 2

/* LE_PSM, SCID, MTU, MPS, Init Credit */
#define L2CAP_CMD_BLE_CREDIT_BASED_CONN_REQ_LEN 10
/* DCID, MTU, MPS, Init credit, Result */
#define L2CAP_CMD_BLE_CREDIT_BASED_CONN_RES_LEN 10
/* CID, Credit */
#define L2CAP_CMD_BLE_FLOW_CTRL_CREDIT_LEN 4

/* LE PSM, MTU, MPS, Initial Credits, SCIDS[] */
#define L2CAP_CMD_CREDIT_BASED_CONN_REQ_MIN_LEN 8
/* MTU, MPS, Initial Credits, Result, DCIDS[] */
#define L2CAP_CMD_CREDIT_BASED_CONN_RES_MIN_LEN 8

/* MTU, MPS, DCIDS[] */
#define L2CAP_CMD_CREDIT_BASED_RECONFIG_REQ_MIN_LEN 4
/* Result */
#define L2CAP_CMD_CREDIT_BASED_RECONFIG_RES_LEN 2

/* Define the packet boundary flags
*/
#define L2CAP_PKT_START_NON_FLUSHABLE 0
#define L2CAP_PKT_START 2
#define L2CAP_PKT_CONTINUE 1
#define L2CAP_PKT_TYPE_SHIFT 12

#define L2CAP_CONN_INTERNAL_MASK 0xF000
#define L2CAP_CONN_LE_MASK 0xFF00

/* Define the LE L2CAP Connection Response Result codes
 */
typedef enum : uint8_t {
  L2CAP_LE_RESULT_CONN_OK = 0,
  L2CAP_LE_RESULT_NO_PSM = 2,
  L2CAP_LE_RESULT_NO_RESOURCES = 4,
  L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION = 5,
  L2CAP_LE_RESULT_INSUFFICIENT_AUTHORIZATION = 6,
  L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP_KEY_SIZE = 7,
  L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP = 8,
  /* We don't like peer device response */
  L2CAP_LE_RESULT_INVALID_SOURCE_CID = 9,
  L2CAP_LE_RESULT_SOURCE_CID_ALREADY_ALLOCATED = 0x0A,
  L2CAP_LE_RESULT_UNACCEPTABLE_PARAMETERS = 0x0B,
  L2CAP_LE_RESULT_INVALID_PARAMETERS = 0x0C
} tL2CAP_LE_RESULT_CODE;

/* Define the L2CAP connection result codes */
typedef enum : uint16_t {
  L2CAP_CONN_OK = 0,
  L2CAP_CONN_PENDING = 1,
  L2CAP_CONN_NO_PSM = 2,
  L2CAP_CONN_SECURITY_BLOCK = 3,
  L2CAP_CONN_NO_RESOURCES = 4,
  L2CAP_CONN_TIMEOUT = 0xEEEE,
  /* Generic L2CAP conn failure reasons */
  L2CAP_CONN_OTHER_ERROR = L2CAP_CONN_INTERNAL_MASK,
  L2CAP_CONN_ACL_CONNECTION_FAILED = L2CAP_CONN_INTERNAL_MASK + 1,
  L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED = L2CAP_CONN_INTERNAL_MASK + 2,
  L2CAP_CONN_NO_LINK = L2CAP_CONN_INTERNAL_MASK + 3,
  L2CAP_CONN_CANCEL =
      L2CAP_CONN_INTERNAL_MASK + 4, /* L2CAP connection cancelled */
  /* For LE result codes converted to L2CAP conn failure code */
  L2CAP_CONN_INSUFFICIENT_AUTHENTICATION =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION,
  L2CAP_CONN_INSUFFICIENT_AUTHORIZATION =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INSUFFICIENT_AUTHORIZATION,
  L2CAP_CONN_INSUFFICIENT_ENCRYP_KEY_SIZE =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP_KEY_SIZE,
  L2CAP_CONN_INSUFFICIENT_ENCRYP =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP,
  L2CAP_CONN_INVALID_SOURCE_CID =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INVALID_SOURCE_CID,
  L2CAP_CONN_SOURCE_CID_ALREADY_ALLOCATED =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_SOURCE_CID_ALREADY_ALLOCATED,
  L2CAP_CONN_UNACCEPTABLE_PARAMETERS =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_UNACCEPTABLE_PARAMETERS,
  L2CAP_CONN_INVALID_PARAMETERS =
      L2CAP_CONN_LE_MASK + L2CAP_LE_RESULT_INVALID_PARAMETERS,
} tL2CAP_CONN;

inline std::string l2cap_result_code_text(const tL2CAP_CONN& result) {
  switch (result) {
    CASE_RETURN_TEXT(L2CAP_CONN_OK);
    CASE_RETURN_TEXT(L2CAP_CONN_PENDING);
    CASE_RETURN_TEXT(L2CAP_CONN_NO_PSM);
    CASE_RETURN_TEXT(L2CAP_CONN_SECURITY_BLOCK);
    CASE_RETURN_TEXT(L2CAP_CONN_NO_RESOURCES);
    CASE_RETURN_TEXT(L2CAP_CONN_TIMEOUT);
    CASE_RETURN_TEXT(L2CAP_CONN_OTHER_ERROR);
    CASE_RETURN_TEXT(L2CAP_CONN_ACL_CONNECTION_FAILED);
    CASE_RETURN_TEXT(L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED);
    CASE_RETURN_TEXT(L2CAP_CONN_NO_LINK);
    CASE_RETURN_TEXT(L2CAP_CONN_CANCEL);
    CASE_RETURN_TEXT(L2CAP_CONN_INSUFFICIENT_AUTHENTICATION);
    CASE_RETURN_TEXT(L2CAP_CONN_INSUFFICIENT_AUTHORIZATION);
    CASE_RETURN_TEXT(L2CAP_CONN_INSUFFICIENT_ENCRYP_KEY_SIZE);
    CASE_RETURN_TEXT(L2CAP_CONN_INSUFFICIENT_ENCRYP);
    CASE_RETURN_TEXT(L2CAP_CONN_INVALID_SOURCE_CID);
    CASE_RETURN_TEXT(L2CAP_CONN_SOURCE_CID_ALREADY_ALLOCATED);
    CASE_RETURN_TEXT(L2CAP_CONN_UNACCEPTABLE_PARAMETERS);
    CASE_RETURN_TEXT(L2CAP_CONN_INVALID_PARAMETERS);
    default:
      return std::string("UNKNOWN[") + std::to_string(result) +
             std::string("]");
  }
}

static inline std::string l2cap_command_code_text(uint8_t cmd) {
  switch (cmd) {
    CASE_RETURN_TEXT(L2CAP_CMD_REJECT);
    CASE_RETURN_TEXT(L2CAP_CMD_CONN_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_CONN_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_CONFIG_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_CONFIG_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_DISC_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_DISC_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_ECHO_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_ECHO_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_INFO_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_INFO_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_AMP_CONN_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_AMP_MOVE_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_BLE_UPDATE_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_BLE_UPDATE_RSP);
    CASE_RETURN_TEXT(L2CAP_CMD_BLE_CREDIT_BASED_CONN_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_BLE_CREDIT_BASED_CONN_RES);
    CASE_RETURN_TEXT(L2CAP_CMD_BLE_FLOW_CTRL_CREDIT);
    CASE_RETURN_TEXT(L2CAP_CMD_CREDIT_BASED_CONN_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_CREDIT_BASED_CONN_RES);
    CASE_RETURN_TEXT(L2CAP_CMD_CREDIT_BASED_RECONFIG_REQ);
    CASE_RETURN_TEXT(L2CAP_CMD_CREDIT_BASED_RECONFIG_RES);
    default:
      return std::string("UNKNOWN L2CAP CMD[") + std::to_string(cmd) +
             std::string("]");
  }
}

inline tL2CAP_CONN to_l2cap_result_code(uint16_t result) {
  return static_cast<tL2CAP_CONN>(result);
}

inline std::string l2cap_le_result_code_text(
    const tL2CAP_LE_RESULT_CODE& code) {
  switch (code) {
    case L2CAP_LE_RESULT_CONN_OK:
      return std::string("le connection success");
    case L2CAP_LE_RESULT_NO_PSM:
      return std::string("le no psm service");
    case L2CAP_LE_RESULT_NO_RESOURCES:
      return std::string("le no resources");
    case L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION:
      return std::string("le authentication failed");
    case L2CAP_LE_RESULT_INSUFFICIENT_AUTHORIZATION:
      return std::string("le authorization failed");
    case L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP_KEY_SIZE:
      return std::string("le encryption key size failed");
    case L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP:
      return std::string("le encryption failed");
    case L2CAP_LE_RESULT_INVALID_SOURCE_CID:
      return std::string("le invalid source channel identifier");
    case L2CAP_LE_RESULT_SOURCE_CID_ALREADY_ALLOCATED:
      return std::string("le source channel identifier busy");
    case L2CAP_LE_RESULT_UNACCEPTABLE_PARAMETERS:
      return std::string("le unacceptable parameters");
    case L2CAP_LE_RESULT_INVALID_PARAMETERS:
      return std::string("invalid parameters");
  }
}

/* Credit based reconfig results code */
#define L2CAP_RECONFIG_SUCCEED 0
#define L2CAP_RECONFIG_REDUCTION_MTU_NO_ALLOWED 1
#define L2CAP_RECONFIG_REDUCTION_MPS_NO_ALLOWED 2
#define L2CAP_RECONFIG_INVALID_DCID 3
#define L2CAP_RECONFIG_UNACCAPTED_PARAM 4

/* Define the L2CAP command reject reason codes
*/
#define L2CAP_CMD_REJ_NOT_UNDERSTOOD 0
#define L2CAP_CMD_REJ_MTU_EXCEEDED 1
#define L2CAP_CMD_REJ_INVALID_CID 2

/* L2CAP Predefined CIDs
*/
enum : uint16_t {
  L2CAP_SIGNALLING_CID = 1,
  L2CAP_CONNECTIONLESS_CID = 2,
  L2CAP_AMP_CID = 3,
  L2CAP_ATT_CID = 4,
  L2CAP_BLE_SIGNALLING_CID = 5,
  L2CAP_SMP_CID = 6,
  L2CAP_SMP_BR_CID = 7,
  L2CAP_BASE_APPL_CID = 0x0040,
};

/* Fixed Channels mask bits */

/* Signal channel supported (Mandatory) */
#define L2CAP_FIXED_CHNL_SIG_BIT (1 << L2CAP_SIGNALLING_CID)

/* Connectionless reception */
#define L2CAP_FIXED_CHNL_CNCTLESS_BIT (1 << L2CAP_CONNECTIONLESS_CID)

/* Attribute protocol supported */
#define L2CAP_FIXED_CHNL_ATT_BIT (1 << L2CAP_ATT_CID)

/* BLE Signalling supported */
#define L2CAP_FIXED_CHNL_BLE_SIG_BIT (1 << L2CAP_BLE_SIGNALLING_CID)

/* BLE Security Mgr supported */
#define L2CAP_FIXED_CHNL_SMP_BIT (1 << L2CAP_SMP_CID)

/* Security Mgr over BR supported */
#define L2CAP_FIXED_CHNL_SMP_BR_BIT (1 << L2CAP_SMP_BR_CID)

/* Define the L2CAP configuration result codes
*/
#define L2CAP_CFG_OK 0
#define L2CAP_CFG_UNACCEPTABLE_PARAMS 1
#define L2CAP_CFG_FAILED_NO_REASON 2
#define L2CAP_CFG_UNKNOWN_OPTIONS 3
#define L2CAP_CFG_PENDING 4

static_assert(L2CAP_CONN_OTHER_ERROR != L2CAP_CFG_FAILED_NO_REASON,
              "Different error code should be provided for Connect error and "
              "Config error");

/* Define the L2CAP configuration option types
*/
#define L2CAP_CFG_TYPE_MTU 0x01
#define L2CAP_CFG_TYPE_FLUSH_TOUT 0x02
#define L2CAP_CFG_TYPE_QOS 0x03
#define L2CAP_CFG_TYPE_FCR 0x04
#define L2CAP_CFG_TYPE_FCS 0x05
#define L2CAP_CFG_TYPE_EXT_FLOW 0x06

#define L2CAP_CFG_MTU_OPTION_LEN 2       /* MTU option length    */
#define L2CAP_CFG_FLUSH_OPTION_LEN 2     /* Flush option len     */
#define L2CAP_CFG_QOS_OPTION_LEN 22      /* QOS option length    */
#define L2CAP_CFG_FCR_OPTION_LEN 9       /* FCR option length    */
#define L2CAP_CFG_FCS_OPTION_LEN 1       /* FCR option length    */
#define L2CAP_CFG_EXT_FLOW_OPTION_LEN 16 /* Extended Flow Spec   */
#define L2CAP_CFG_OPTION_OVERHEAD 2      /* Type and length      */

/* Configuration Cmd/Rsp Flags mask
*/
#define L2CAP_CFG_FLAGS_MASK_CONT 0x0001 /* Flags mask: Continuation */

/* FCS Check Option values
*/
#define L2CAP_CFG_FCS_BYPASS 0 /* Bypass the FCS in streaming or ERTM modes */
#define L2CAP_CFG_FCS_USE \
  1 /* Use the FCS in streaming or ERTM modes [default] */

/* Default values for configuration
*/
#define L2CAP_NO_AUTOMATIC_FLUSH 0xFFFF

#define L2CAP_DEFAULT_MTU (672)
#define L2CAP_DEFAULT_SERV_TYPE 1
#define L2CAP_DEFAULT_TOKEN_RATE 0
#define L2CAP_DEFAULT_BUCKET_SIZE 0
#define L2CAP_DEFAULT_PEAK_BANDWIDTH 0
#define L2CAP_DEFAULT_LATENCY 0xFFFFFFFF
#define L2CAP_DEFAULT_DELAY 0xFFFFFFFF

/* Define the L2CAP disconnect result codes
*/
#define L2CAP_DISC_OK 0
#define L2CAP_DISC_TIMEOUT 0xEEEE

/* Define the L2CAP info resp result codes
*/
#define L2CAP_INFO_RESP_RESULT_SUCCESS 0
#define L2CAP_INFO_RESP_RESULT_NOT_SUPPORTED 1

/* Define the info-type fields of information request & response
*/
#define L2CAP_CONNLESS_MTU_INFO_TYPE 0x0001
/* Used in Information Req/Response */
#define L2CAP_EXTENDED_FEATURES_INFO_TYPE 0x0002
/* Used in AMP                      */
#define L2CAP_FIXED_CHANNELS_INFO_TYPE 0x0003

/* Connectionless MTU size          */
#define L2CAP_CONNLESS_MTU_INFO_SIZE 2
/* Extended features array size     */
#define L2CAP_EXTENDED_FEATURES_ARRAY_SIZE 4
/* Fixed channel array size         */
#define L2CAP_FIXED_CHNL_ARRAY_SIZE 8

/* Extended features mask bits
*/
/* Enhanced retransmission mode           */
#define L2CAP_EXTFEA_ENH_RETRANS 0x00000008
/* Streaming Mode                         */
#define L2CAP_EXTFEA_STREAM_MODE 0x00000010
/* Optional FCS (if set No FCS desired)   */
#define L2CAP_EXTFEA_NO_CRC 0x00000020
/* Extended flow spec                     */
#define L2CAP_EXTFEA_EXT_FLOW_SPEC 0x00000040
/* Fixed channels                         */
#define L2CAP_EXTFEA_FIXED_CHNLS 0x00000080
/* Extended Window Size                   */
#define L2CAP_EXTFEA_EXT_WINDOW 0x00000100
/* Unicast Connectionless Data Reception  */
#define L2CAP_EXTFEA_UCD_RECEPTION 0x00000200

/* Mask for locally supported features used in Information Response
 * (default to none) */
#ifndef L2CAP_EXTFEA_SUPPORTED_MASK
#define L2CAP_EXTFEA_SUPPORTED_MASK 0
#endif

/* Mask for LE supported features used in Information Response
 * (default to none) */
#ifndef L2CAP_BLE_EXTFEA_MASK
#define L2CAP_BLE_EXTFEA_MASK 0
#endif

/* Define a value that tells L2CAP to use the default HCI ACL buffer size */
#define L2CAP_INVALID_ERM_BUF_SIZE 0
/* Define a value that tells L2CAP to use the default MPS */
#define L2CAP_DEFAULT_ERM_MPS 0x0000

#define L2CAP_FCR_OVERHEAD 2         /* Control word                 */
#define L2CAP_FCS_LEN 2              /* FCS takes 2 bytes */
#define L2CAP_SDU_LEN_OVERHEAD 2     /* SDU length field is 2 bytes */
#define L2CAP_SDU_LEN_OFFSET 2       /* SDU length offset is 2 bytes */
#define L2CAP_EXT_CONTROL_OVERHEAD 4 /* Extended Control Field       */
/* length(2), channel(2), control(4), SDU length(2) FCS(2) */
#define L2CAP_MAX_HEADER_FCS                                                  \
  (L2CAP_PKT_OVERHEAD + L2CAP_EXT_CONTROL_OVERHEAD + L2CAP_SDU_LEN_OVERHEAD + \
   L2CAP_FCS_LEN)

/* TODO: This value can probably be optimized per transport, and per L2CAP
 * socket type, but this should not bring any big performance improvements. For
 * LE CoC, it should be biggest multiple of "PDU length" smaller than 0xffff (so
 * depend on controller buffer size), for Classic, making it multiple of PDU
 * length and also of the 3DH5 air including the l2cap headers in each packet.
 */
#define L2CAP_SDU_LENGTH_MAX (8080 + 26 - (L2CAP_MIN_OFFSET + 6))
constexpr uint16_t L2CAP_SDU_LENGTH_LE_MAX = 0xffff;

/* SAR bits in the control word
*/
/* Control word to begin with for unsegmented PDU*/
#define L2CAP_FCR_UNSEG_SDU 0x0000
/* ...for Starting PDU of a semented SDU */
#define L2CAP_FCR_START_SDU 0x4000
/* ...for ending PDU of a segmented SDU */
#define L2CAP_FCR_END_SDU 0x8000
/* ...for continuation PDU of a segmented SDU */
#define L2CAP_FCR_CONT_SDU 0xc000

/* Supervisory frame types */
/* Supervisory frame - RR                          */
#define L2CAP_FCR_SUP_RR 0x0000
/* Supervisory frame - REJ                         */
#define L2CAP_FCR_SUP_REJ 0x0001
/* Supervisory frame - RNR                         */
#define L2CAP_FCR_SUP_RNR 0x0002
/* Supervisory frame - SREJ                        */
#define L2CAP_FCR_SUP_SREJ 0x0003

/* Mask to get the SAR bits from control word */
#define L2CAP_FCR_SAR_BITS 0xC000
/* Bits to shift right to get the SAR bits from ctrl-word */
#define L2CAP_FCR_SAR_BITS_SHIFT 14

/* Mask to check if a PDU is S-frame */
#define L2CAP_FCR_S_FRAME_BIT 0x0001
/* Mask to get the req-seq from control word */
#define L2CAP_FCR_REQ_SEQ_BITS 0x3F00
/* Bits to shift right to get the req-seq from ctrl-word */
#define L2CAP_FCR_REQ_SEQ_BITS_SHIFT 8
/* Mask on get the tx-seq from control word */
#define L2CAP_FCR_TX_SEQ_BITS 0x007E
/* Bits to shift right to get the tx-seq from ctrl-word */
#define L2CAP_FCR_TX_SEQ_BITS_SHIFT 1

/* F-bit in the control word (Sup and I frames)  */
#define L2CAP_FCR_F_BIT 0x0080
/* P-bit in the control word (Sup frames only)   */
#define L2CAP_FCR_P_BIT 0x0010

#define L2CAP_FCR_F_BIT_SHIFT 7
#define L2CAP_FCR_P_BIT_SHIFT 4

/* Mask to get the segmentation bits from ctrl-word */
#define L2CAP_FCR_SEG_BITS 0xC000
/* Bits to shift right to get the S-bits from ctrl-word */
#define L2CAP_FCR_SUP_SHIFT 2
/* Mask to get the supervisory bits from ctrl-word */
#define L2CAP_FCR_SUP_BITS 0x000C

/* Initial state of the CRC register */
#define L2CAP_FCR_INIT_CRC 0
/* Mask for sequence numbers (range 0 - 63) */
#define L2CAP_FCR_SEQ_MODULO 0x3F

namespace fmt {
template <>
struct formatter<tL2CAP_CONN> : enum_formatter<tL2CAP_CONN> {};
}  // namespace fmt

#endif
