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

#ifndef SMP_API_TYPES_H
#define SMP_API_TYPES_H

#include <base/strings/stringprintf.h>
#include <bluetooth/log.h>

#include <cstdint>
#include <string>

#include "macros.h"
#include "stack/include/bt_octets.h"
#include "stack/include/btm_status.h"
#include "stack/include/smp_status.h"
#include "types/ble_address_with_type.h"
#include "types/raw_address.h"

/* SMP event type */
typedef enum : uint8_t {
  SMP_EVT_NONE,           /* Default no event */
  SMP_IO_CAP_REQ_EVT,     /* IO capability request event */
  SMP_SEC_REQUEST_EVT,    /* SMP pairing request */
  SMP_PASSKEY_NOTIF_EVT,  /* passkey notification event */
  SMP_PASSKEY_REQ_EVT,    /* passkey request event */
  SMP_OOB_REQ_EVT,        /* OOB request event */
  SMP_NC_REQ_EVT,         /* Numeric Comparison request event */
  SMP_COMPLT_EVT,         /* SMP complete event */
  SMP_PEER_KEYPR_NOT_EVT, /* Peer keypress notification */

  /* SC OOB request event (both local and peer OOB data can be expected in
   * response) */
  SMP_SC_OOB_REQ_EVT,
  /* SC OOB local data set is created (as result of SMP_CrLocScOobData(...))
   */
  SMP_SC_LOC_OOB_DATA_UP_EVT,
  SMP_UNUSED11,
  SMP_BR_KEYS_REQ_EVT, /* SMP over BR keys request event */
  SMP_UNUSED13,
  SMP_CONSENT_REQ_EVT,           /* Consent request event */
  SMP_LE_ADDR_ASSOC_EVT,         /* Identity address association event */
  SMP_SIRK_VERIFICATION_REQ_EVT, /* SIRK verification request event */
} tSMP_EVT;

inline std::string smp_evt_to_text(const tSMP_EVT evt) {
  switch (evt) {
    CASE_RETURN_TEXT(SMP_EVT_NONE);
    CASE_RETURN_TEXT(SMP_IO_CAP_REQ_EVT);
    CASE_RETURN_TEXT(SMP_SEC_REQUEST_EVT);
    CASE_RETURN_TEXT(SMP_PASSKEY_NOTIF_EVT);
    CASE_RETURN_TEXT(SMP_PASSKEY_REQ_EVT);
    CASE_RETURN_TEXT(SMP_OOB_REQ_EVT);
    CASE_RETURN_TEXT(SMP_NC_REQ_EVT);
    CASE_RETURN_TEXT(SMP_COMPLT_EVT);
    CASE_RETURN_TEXT(SMP_PEER_KEYPR_NOT_EVT);
    CASE_RETURN_TEXT(SMP_SC_OOB_REQ_EVT);
    CASE_RETURN_TEXT(SMP_SC_LOC_OOB_DATA_UP_EVT);
    CASE_RETURN_TEXT(SMP_UNUSED11);
    CASE_RETURN_TEXT(SMP_BR_KEYS_REQ_EVT);
    CASE_RETURN_TEXT(SMP_UNUSED13);
    CASE_RETURN_TEXT(SMP_CONSENT_REQ_EVT);
    CASE_RETURN_TEXT(SMP_LE_ADDR_ASSOC_EVT);
    CASE_RETURN_TEXT(SMP_SIRK_VERIFICATION_REQ_EVT);
    default:
      return "UNKNOWN SMP EVENT";
  }
}

/* Device IO capability */
#define SMP_IO_CAP_IO BTM_IO_CAP_IO         /* DisplayYesNo */
#define SMP_IO_CAP_KBDISP BTM_IO_CAP_KBDISP /* Keyboard Display */
#define SMP_IO_CAP_MAX BTM_IO_CAP_MAX
typedef uint8_t tSMP_IO_CAP;

/* OOB data present or not */
enum { SMP_OOB_NONE, SMP_OOB_PRESENT, SMP_OOB_UNKNOWN };
typedef uint8_t tSMP_OOB_FLAG;

/* type of OOB data required from application */
typedef enum : uint8_t {
  SMP_OOB_INVALID_TYPE,
  SMP_OOB_PEER,
  SMP_OOB_LOCAL,
  SMP_OOB_BOTH,
} tSMP_OOB_DATA_TYPE;

enum : uint8_t {
  SMP_AUTH_NO_BOND = 0x00,
  /* no MITM, No Bonding, encryption only */
  SMP_AUTH_NB_ENC_ONLY = 0x00,  //(SMP_AUTH_MASK | BTM_AUTH_SP_NO)
  SMP_AUTH_BOND = (1u << 0),
  SMP_AUTH_UNUSED = (1u << 1),
  /* SMP Authentication requirement */
  SMP_AUTH_YN_BIT = (1u << 2),
  SMP_SC_SUPPORT_BIT = (1u << 3),
  SMP_KP_SUPPORT_BIT = (1u << 4),
  SMP_H7_SUPPORT_BIT = (1u << 5),
};

#define SMP_AUTH_MASK                                                          \
  (SMP_AUTH_BOND | SMP_AUTH_YN_BIT | SMP_SC_SUPPORT_BIT | SMP_KP_SUPPORT_BIT | \
   SMP_H7_SUPPORT_BIT)

/* Secure Connections, no MITM, no Bonding */
#define SMP_AUTH_SC_ENC_ONLY (SMP_H7_SUPPORT_BIT | SMP_SC_SUPPORT_BIT)

/* Secure Connections, MITM, Bonding */
#define SMP_AUTH_SC_MITM_GB \
  (SMP_H7_SUPPORT_BIT | SMP_SC_SUPPORT_BIT | SMP_AUTH_YN_BIT | SMP_AUTH_BOND)

typedef uint8_t tSMP_AUTH_REQ;

typedef enum : uint8_t {
  SMP_SEC_NONE = 0,
  SMP_SEC_UNAUTHENTICATE = 1,
  SMP_SEC_AUTHENTICATED = 2,
} tSMP_SEC_LEVEL;

/* Maximum Encryption Key Size range */
#define SMP_ENCR_KEY_SIZE_MIN 7
#define SMP_ENCR_KEY_SIZE_MAX 16

/* SMP key types */
enum tSMP_KEYS_BITMASK : uint8_t {
  SMP_SEC_KEY_TYPE_ENC = (1 << 0),  /* encryption key */
  SMP_SEC_KEY_TYPE_ID = (1 << 1),   /* identity key */
  SMP_SEC_KEY_TYPE_CSRK = (1 << 2), /* peripheral CSRK */
  SMP_SEC_KEY_TYPE_LK = (1 << 3),   /* BR/EDR link key */
};
typedef uint8_t tSMP_KEYS;

constexpr tSMP_KEYS SMP_BR_SEC_DEFAULT_KEY =
    (SMP_SEC_KEY_TYPE_ENC | SMP_SEC_KEY_TYPE_ID | SMP_SEC_KEY_TYPE_CSRK);

/* default security key distribution value */
constexpr tSMP_KEYS SMP_SEC_DEFAULT_KEY =
    (SMP_SEC_KEY_TYPE_ENC | SMP_SEC_KEY_TYPE_ID | SMP_SEC_KEY_TYPE_CSRK |
     SMP_SEC_KEY_TYPE_LK);

#define SMP_SC_KEY_OUT_OF_RANGE 5 /* out of range */
typedef uint8_t tSMP_SC_KEY_TYPE;

/* data type for BTM_SP_IO_REQ_EVT */
typedef struct {
  tSMP_IO_CAP io_cap;     /* local IO capabilities */
  tSMP_OOB_FLAG oob_data; /* OOB data present (locally) for the peer device */
  tSMP_AUTH_REQ auth_req; /* Authentication required (for local device) */
  uint8_t max_key_size;   /* max encryption key size */
  tSMP_KEYS init_keys;    /* initiator keys to be distributed */
  tSMP_KEYS resp_keys;    /* responder keys */
} tSMP_IO_REQ;

typedef struct {
  tSMP_STATUS reason;
  tSMP_SEC_LEVEL sec_level;
  bool is_pair_cancel;
  bool smp_over_br;
} tSMP_CMPL;

typedef struct {
  BT_OCTET32 x;
  BT_OCTET32 y;
} tSMP_PUBLIC_KEY;

/* the data associated with the info sent to the peer via OOB interface */
typedef struct {
  bool present;
  Octet16 randomizer;
  Octet16 commitment;

  tBLE_BD_ADDR addr_sent_to;
  BT_OCTET32 private_key_used; /* is used to calculate: */
  /* publ_key_used = P-256(private_key_used, curve_p256.G) - send it to the */
  /* other side */
  /* dhkey = P-256(private_key_used, publ key rcvd from the other side) */
  tSMP_PUBLIC_KEY publ_key_used; /* P-256(private_key_used, curve_p256.G) */
} tSMP_LOC_OOB_DATA;

/* the data associated with the info received from the peer via OOB interface */
typedef struct {
  bool present;
  Octet16 randomizer;
  Octet16 commitment;
  tBLE_BD_ADDR addr_rcvd_from;
} tSMP_PEER_OOB_DATA;

typedef struct {
  tSMP_LOC_OOB_DATA loc_oob_data;
  tSMP_PEER_OOB_DATA peer_oob_data;
} tSMP_SC_OOB_DATA;

typedef union {
  uint32_t passkey;
  tSMP_IO_REQ io_req; /* IO request */
  tSMP_CMPL cmplt;
  tSMP_OOB_DATA_TYPE req_oob_type;
  tSMP_LOC_OOB_DATA loc_oob_data;
  RawAddress id_addr;
} tSMP_EVT_DATA;

/* AES Encryption output */
typedef struct {
  uint8_t status;
  uint8_t param_len;
  uint16_t opcode;
  uint8_t param_buf[OCTET16_LEN];
} tSMP_ENC;

/* Security Manager events - Called by the stack when Security Manager related
 * events occur.*/
typedef tBTM_STATUS(tSMP_CALLBACK)(tSMP_EVT event, const RawAddress& bd_addr,
                                   const tSMP_EVT_DATA* p_data);
/* Security Manager SIRK verification event - Called by the stack when Security
 * Manager requires verification from CSIP.*/
typedef tBTM_STATUS(tSMP_SIRK_CALLBACK)(const RawAddress& bd_addr);

namespace fmt {
template <>
struct formatter<tSMP_OOB_DATA_TYPE> : enum_formatter<tSMP_OOB_DATA_TYPE> {};
template <>
struct formatter<tSMP_SEC_LEVEL> : enum_formatter<tSMP_SEC_LEVEL> {};
template <>
struct formatter<tSMP_EVT> : enum_formatter<tSMP_EVT> {};
}  // namespace fmt

#endif  // SMP_API_TYPES_H
