/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Portions copyright (C) 2017 Broadcom Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdint.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/family.h>
#include <netlink/genl/ctrl.h>
#include <linux/rtnetlink.h>
#include <netpacket/packet.h>
#include <linux/filter.h>
#include <linux/errqueue.h>
#include <ctype.h>
#include <linux/pkt_sched.h>
#include <netlink/object-api.h>
#include <netlink/netlink.h>
#include <netlink/socket.h>

#include "nl80211_copy.h"

#include "sync.h"

#define LOG_TAG  "WifiHAL"

#include <utils/Log.h>
#include <log/log.h>
#include <hardware_legacy/wifi_hal.h>
#include "common.h"
#include "cpp_bindings.h"
#include "netinet/in.h"
#include "arpa/inet.h"
#include <openssl/sha.h>
#include <openssl/evp.h> 
#include <sys/ioctl.h>

/* Changes between incompatible Version of NAN */
#define NAN_MAJOR_REL_VERSION       1
/* Changes between Source and Binary compatible Version of NAN */
#define NAN_MINOR_REL_VERSION       2
/* Changes between perfectly compatible Version of NAN */
#define NAN_PATCH_REL_VERSION       3

#define SVC_NAME_TO_HASH            1
#define NAN_SVC_HASH_SIZE           6
#define C2S(x)  case x: return #x;
#define NAN_PUB_RECV_FLAG_MAX 15
#define NAN_SUB_RECV_FLAG_MAX 7
#define NAN_DISC_IND_MAX 7
#define NAN_MAX 255
#define NAN_MIN 0
#define INVALID 0xFF
#define NAN_MAX_PERIOD 16
#define ISGREATER(i, x) (i > x) ? 1 : 0
#define NAN_MAX_RSSI 90
#define NAN_SECURITY_SALT_SIZE	14
#define NAN_MAC_INVALID_TRANSID 0xFFFF

#define SVCHASH_ISNULL(svc_hash) ((((u8 *)(svc_hash))[0] |		\
            ((u8 *)(svc_hash))[1] |		\
            ((u8 *)(svc_hash))[2] |		\
            ((u8 *)(svc_hash))[3] |		\
            ((u8 *)(svc_hash))[4] |		\
            ((u8 *)(svc_hash))[5]) == 0)
#define ETHER_ISNULLADDR(ea) ((((u8 *)(ea))[0] |		\
            ((u8 *)(ea))[1] |		\
            ((u8 *)(ea))[2] |		\
            ((u8 *)(ea))[3] |		\
            ((u8 *)(ea))[4] |		\
            ((u8 *)(ea))[5]) == 0)

/* NAN structs versioning b/w DHD and HAL
 * TODO:add versions for each struct*/
#define NAN_HAL_VERSION_1	0x2
struct nan_dbg_cntrs {
    u32 dp_req; /* cmd */
    u32 dp_resp; /* cmd */
    u32 dp_req_evt;
    u32 dp_confirm_evt;
    u32 transmit_req; /* cmd */
    u32 transmit_txs; /* event */
    u32 transmit_recv; /* event */
};
nan_dbg_cntrs counters;

u32 current_dhd_hal_ver = 0;

/* TODO: Known bug in Android which was discovered too late and then left in for backward compatibility.
 * The issue is that the Service Name selected by the framework is invalid - it contains a space.
 * Therefore, the underlying implementation partially converts it to lower case and uses the results for PMK generation.
 * I.e. the PMK is generated based on the following service name: "Wi-Fi Aware Data Path"
 */
/* SVC Hash generated for svc name string "Wi-Fi Aware Data Path" */
u8 NAN_OOB_INTEROP_SVC_HASH[NAN_SVC_HASH_SIZE] = {0x05, 0x9e, 0xd4, 0xcf, 0x89, 0x1a};
#define NAN_OOB_INTEROP_SVC_NAME "Wi-Fi Aware Data Path"

static const char *NanStatusToString(NanStatusType status)
{
    switch (status) {
        C2S(NAN_STATUS_SUCCESS)
            C2S(NAN_STATUS_INTERNAL_FAILURE)
            C2S(NAN_STATUS_PROTOCOL_FAILURE)
            C2S(NAN_STATUS_INVALID_PUBLISH_SUBSCRIBE_ID)
            C2S(NAN_STATUS_NO_RESOURCE_AVAILABLE)
            C2S(NAN_STATUS_INVALID_PARAM)
            C2S(NAN_STATUS_INVALID_REQUESTOR_INSTANCE_ID)
            C2S(NAN_STATUS_INVALID_NDP_ID)
            C2S(NAN_STATUS_NAN_NOT_ALLOWED)
            C2S(NAN_STATUS_NO_OTA_ACK)
            C2S(NAN_STATUS_ALREADY_ENABLED)
            C2S(NAN_STATUS_FOLLOWUP_QUEUE_FULL)
            C2S(NAN_STATUS_UNSUPPORTED_CONCURRENCY_NAN_DISABLED)

        default:
            return "NAN_STATUS_INTERNAL_FAILURE";
    }
}

/* Nan Data Path Security Information */
typedef struct {
    /*
       Unique Instance Id identifying the Responder's service.
       This is same as publish_id notified on the subscribe side
       in a publish/subscribe scenario
     */
    u32 requestor_instance_id; /* Value 0 for no publish/subscribe */
    /*
       Discovery MAC addr of the publisher/peer
     */
    u8 peer_disc_mac_addr[NAN_MAC_ADDR_LEN];
    /*
       Unique token Id generated on the initiator/responder
       side used for a NDP session between two NAN devices
     */
    NanDataPathId ndp_instance_id;
} NanDataPathSecInfoRequest;
/*
 * Note: NAN_ATTRIBUTE should match with one that on driver side, wl_cfgnan.h and
 * NanAttrToString as well for enum to string.
 */
typedef enum {
    NAN_ATTRIBUTE_HEADER                            = 100,
    NAN_ATTRIBUTE_HANDLE                            = 101,
    NAN_ATTRIBUTE_TRANSAC_ID                        = 102,

    /* NAN Enable request attributes */
    NAN_ATTRIBUTE_2G_SUPPORT                        = 103,
    NAN_ATTRIBUTE_5G_SUPPORT                        = 104,
    NAN_ATTRIBUTE_CLUSTER_LOW                       = 105,
    NAN_ATTRIBUTE_CLUSTER_HIGH                      = 106,
    NAN_ATTRIBUTE_SID_BEACON                        = 107,
    NAN_ATTRIBUTE_SYNC_DISC_2G_BEACON               = 108,
    NAN_ATTRIBUTE_SYNC_DISC_5G_BEACON               = 109,
    NAN_ATTRIBUTE_SDF_2G_SUPPORT                    = 110,
    NAN_ATTRIBUTE_SDF_5G_SUPPORT                    = 111,
    NAN_ATTRIBUTE_RSSI_CLOSE                        = 112,
    NAN_ATTRIBUTE_RSSI_MIDDLE                       = 113,
    NAN_ATTRIBUTE_RSSI_PROXIMITY                    = 114,
    NAN_ATTRIBUTE_HOP_COUNT_LIMIT                   = 115,
    NAN_ATTRIBUTE_RANDOM_FACTOR                       = 116,
    NAN_ATTRIBUTE_MASTER_PREF                       = 117,
    NAN_ATTRIBUTE_PERIODIC_SCAN_INTERVAL            = 118,

    /* Nan Publish/Subscribe request attributes */
    NAN_ATTRIBUTE_PUBLISH_ID                        = 119,
    NAN_ATTRIBUTE_TTL                               = 120,
    NAN_ATTRIBUTE_PERIOD                            = 121,
    NAN_ATTRIBUTE_REPLIED_EVENT_FLAG                = 122,
    NAN_ATTRIBUTE_PUBLISH_TYPE                      = 123,
    NAN_ATTRIBUTE_TX_TYPE                           = 124,
    NAN_ATTRIBUTE_PUBLISH_COUNT                     = 125,
    NAN_ATTRIBUTE_SERVICE_NAME_LEN                  = 126,
    NAN_ATTRIBUTE_SERVICE_NAME                      = 127,
    NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN         = 128,
    NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO             = 129,
    NAN_ATTRIBUTE_RX_MATCH_FILTER_LEN               = 130,
    NAN_ATTRIBUTE_RX_MATCH_FILTER                   = 131,
    NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN               = 132,
    NAN_ATTRIBUTE_TX_MATCH_FILTER                   = 133,
    NAN_ATTRIBUTE_SUBSCRIBE_ID                      = 134,
    NAN_ATTRIBUTE_SUBSCRIBE_TYPE                    = 135,
    NAN_ATTRIBUTE_SERVICERESPONSEFILTER             = 136,
    NAN_ATTRIBUTE_SERVICERESPONSEINCLUDE            = 137,
    NAN_ATTRIBUTE_USESERVICERESPONSEFILTER          = 138,
    NAN_ATTRIBUTE_SSIREQUIREDFORMATCHINDICATION     = 139,
    NAN_ATTRIBUTE_SUBSCRIBE_MATCH                   = 140,
    NAN_ATTRIBUTE_SUBSCRIBE_COUNT                   = 141,
    NAN_ATTRIBUTE_MAC_ADDR                          = 142,
    NAN_ATTRIBUTE_MAC_ADDR_LIST                     = 143,
    NAN_ATTRIBUTE_MAC_ADDR_LIST_NUM_ENTRIES         = 144,
    NAN_ATTRIBUTE_PUBLISH_MATCH                     = 145,

    /* Nan Event attributes */
    NAN_ATTRIBUTE_ENABLE_STATUS                     = 146,
    NAN_ATTRIBUTE_JOIN_STATUS                       = 147,
    NAN_ATTRIBUTE_ROLE                              = 148,
    NAN_ATTRIBUTE_MASTER_RANK                       = 149,
    NAN_ATTRIBUTE_ANCHOR_MASTER_RANK                = 150,
    NAN_ATTRIBUTE_CNT_PEND_TXFRM                    = 151,
    NAN_ATTRIBUTE_CNT_BCN_TX                        = 152,
    NAN_ATTRIBUTE_CNT_BCN_RX                        = 153,
    NAN_ATTRIBUTE_CNT_SVC_DISC_TX                   = 154,
    NAN_ATTRIBUTE_CNT_SVC_DISC_RX                   = 155,
    NAN_ATTRIBUTE_AMBTT                             = 156,
    NAN_ATTRIBUTE_CLUSTER_ID                        = 157,
    NAN_ATTRIBUTE_INST_ID                           = 158,
    NAN_ATTRIBUTE_OUI                               = 159,
    NAN_ATTRIBUTE_STATUS                            = 160,
    NAN_ATTRIBUTE_DE_EVENT_TYPE                     = 161,
    NAN_ATTRIBUTE_MERGE                             = 162,
    NAN_ATTRIBUTE_IFACE                             = 163,
    NAN_ATTRIBUTE_CHANNEL                           = 164,
    NAN_ATTRIBUTE_PEER_ID                           = 165,
    NAN_ATTRIBUTE_NDP_ID                            = 167,
    NAN_ATTRIBUTE_SECURITY                          = 168,
    NAN_ATTRIBUTE_QOS                               = 169,
    NAN_ATTRIBUTE_RSP_CODE                          = 170,
    NAN_ATTRIBUTE_INST_COUNT                        = 171,
    NAN_ATTRIBUTE_PEER_DISC_MAC_ADDR                = 172,
    NAN_ATTRIBUTE_PEER_NDI_MAC_ADDR                 = 173,
    NAN_ATTRIBUTE_IF_ADDR                           = 174,
    NAN_ATTRIBUTE_WARMUP_TIME                       = 175,
    NAN_ATTRIBUTE_RECV_IND_CFG                      = 176,
    NAN_ATTRIBUTE_RSSI_CLOSE_5G                     = 177,
    NAN_ATTRIBUTE_RSSI_MIDDLE_5G                    = 178,
    NAN_ATTRIBUTE_RSSI_PROXIMITY_5G                 = 179,
    NAN_ATTRIBUTE_CONNMAP                           = 180,
    NAN_ATTRIBUTE_24G_CHANNEL                       = 181,
    NAN_ATTRIBUTE_5G_CHANNEL                        = 182,
    NAN_ATTRIBUTE_DWELL_TIME                        = 183,
    NAN_ATTRIBUTE_SCAN_PERIOD                       = 184,
    NAN_ATTRIBUTE_RSSI_WINDOW_SIZE                  = 185,
    NAN_ATTRIBUTE_CONF_CLUSTER_VAL                  = 186,
    NAN_ATTRIBUTE_AVAIL_BIT_MAP                     = 187,
    NAN_ATTRIBUTE_ENTRY_CONTROL                     = 188,
    NAN_ATTRIBUTE_CIPHER_SUITE_TYPE                 = 189,
    NAN_ATTRIBUTE_KEY_TYPE                          = 190,
    NAN_ATTRIBUTE_KEY_LEN                           = 191,
    NAN_ATTRIBUTE_SCID                              = 192,
    NAN_ATTRIBUTE_SCID_LEN                          = 193,
    NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP             = 194,
    NAN_ATTRIBUTE_SDE_CONTROL_SECURITY              = 195,
    NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE               = 196,
    NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT         = 197,
    NAN_ATTRIBUTE_NO_CONFIG_AVAIL                   = 198,
    NAN_ATTRIBUTE_2G_AWAKE_DW                       = 199,
    NAN_ATTRIBUTE_5G_AWAKE_DW                       = 200,
    NAN_ATTRIBUTE_RANGING_INTERVAL                  = 201,
    NAN_ATTRIBUTE_RANGING_INDICATION                = 202,
    NAN_ATTRIBUTE_RANGING_INGRESS_LIMIT             = 203,
    NAN_ATTRIBUTE_RANGING_EGRESS_LIMIT              = 204,
    NAN_ATTRIBUTE_RANGING_AUTO_ACCEPT               = 205,
    NAN_ATTRIBUTE_RANGING_RESULT                    = 206,
    NAN_ATTRIBUTE_DISC_IND_CFG                      = 207,
    NAN_ATTRIBUTE_RSSI_THRESHOLD_FLAG               = 208,
    NAN_ATTRIBUTE_KEY_DATA                          = 209,
    NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN    = 210,
    NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO        = 211,
    NAN_ATTRIBUTE_REASON                            = 212,
    NAN_ATTRIBUTE_MATCH_OCCURRED_FLAG               = 213,
    NAN_ATTRIBUTE_OUT_OF_RESOURCE_FLAG              = 214,
    NAN_ATTRIBUTE_DWELL_TIME_5G                     = 215,
    NAN_ATTRIBUTE_SCAN_PERIOD_5G                    = 216,
    NAN_ATTRIBUTE_SVC_RESPONDER_POLICY              = 217,
    NAN_ATTRIBUTE_EVENT_MASK                        = 218,
    NAN_ATTRIBUTE_SUB_SID_BEACON                    = 219,
    NAN_ATTRIBUTE_RANDOMIZATION_INTERVAL            = 220,
    NAN_ATTRIBUTE_CMD_RESP_DATA                     = 221,
    NAN_ATTRIBUTE_CMD_USE_NDPE                      = 222,
    NAN_ATTRIBUTE_ENABLE_MERGE                      = 223,
    NAN_ATTRIBUTE_DISCOVERY_BEACON_INTERVAL         = 224,
    NAN_ATTRIBUTE_NSS                               = 225,
    NAN_ATTRIBUTE_ENABLE_RANGING                    = 226,
    NAN_ATTRIBUTE_DW_EARLY_TERM                     = 227,
    NAN_ATTRIBUTE_CHANNEL_INFO                      = 228,
    NAN_ATTRIBUTE_NUM_CHANNELS                      = 229,
    NAN_ATTRIBUTE_INSTANT_MODE_ENABLE               = 230,
    NAN_ATTRIBUTE_INSTANT_COMM_CHAN                 = 231
} NAN_ATTRIBUTE;

typedef enum {
    NAN_REQUEST_ENABLE                          = 0,
    NAN_REQUEST_DISABLE                         = 1,
    NAN_REQUEST_PUBLISH                         = 2,
    NAN_REQUEST_PUBLISH_CANCEL                  = 3,
    NAN_REQUEST_TRANSMIT_FOLLOWUP               = 4,
    NAN_REQUEST_SUBSCRIBE                       = 5,
    NAN_REQUEST_SUBSCRIBE_CANCEL                = 6,
    NAN_REQUEST_STATS                           = 7,
    NAN_REQUEST_CONFIG                          = 8,
    NAN_REQUEST_TCA                             = 9,
    NAN_REQUEST_EVENT_CHECK                     = 10,
    NAN_REQUEST_GET_CAPABILITIES                = 11,
    NAN_DATA_PATH_IFACE_CREATE                  = 12,
    NAN_DATA_PATH_IFACE_DELETE                  = 13,
    NAN_DATA_PATH_INIT_REQUEST                  = 14,
    NAN_DATA_PATH_IND_RESPONSE                  = 15,
    NAN_DATA_PATH_END                           = 16,
    NAN_DATA_PATH_IFACE_UP                      = 17,
    NAN_DATA_PATH_SEC_INFO                      = 18,
    NAN_VERSION_INFO                            = 19,
    NAN_REQUEST_LAST                            = 0xFFFF
} NanRequestType;

/*
 * The enum is based on the BCME Response defs
 * used in the firmware and defined at
 * path: src/include/bcmeutils.h
 */
enum nan_response_status {
    BCME_OK                  = 0,
    BCME_ERROR               = -1,
    BCME_BADARG              = -2,
    BCME_BADRATESET          = -12,
    BCME_BADBAND             = -13,
    BCME_BUSY                = -16,
    BCME_BADCHAN             = -20,
    BCME_UNSUPPORTED         = -23,
    BCME_BADLEN              = -24,
    BCME_NOTREADY            = -25,
    BCME_NOMEM               = -27,
    BCME_NOTFOUND            = -30,
    BCME_TXFAIL              = -38,
    BCME_RXFAIL              = -39,
    BCME_SCANREJECT          = -43,
    BCME_USAGE_ERROR         = -44,
    BCME_IOCTL_ERROR         = -45
};

enum nan_de_event_type {
    NAN_EVENT_IFACE       = 0,
    NAN_EVENT_START       = 1,
    NAN_EVENT_JOIN        = 2,
    NAN_EVENT_ROLE_CHANGE = 3,
    NAN_EVENT_MERGE       = 4
};

typedef struct _nan_hal_resp {
    u16 instance_id;
    u16  subcmd;
    int32_t status;
    int32_t value;
    /* Identifier for the instance of the NDP */
    u16 ndp_instance_id;
    /* Publisher NMI */
    u8 pub_nmi[NAN_MAC_ADDR_LEN];
    /* SVC_HASH */
    u8 svc_hash[NAN_SVC_HASH_SIZE];
    char nan_reason[NAN_ERROR_STR_LEN]; /* Describe the NAN reason type */
    char pad[3];
    NanCapabilities capabilities;
} nan_hal_resp_t;

typedef int (*match_fn)(void *p1, void *data);

typedef struct _nan_hal_info {
    void *nan_handle;
    void *nan_mac_control;
    void *nan_disc_control;
    void *nan_dp_control;
} nan_hal_info_t;

u8 mNmi[NAN_MAC_ADDR_LEN];
/* Static functions */
static int is_de_event(int cmd);
static int is_dp_event(int cmd);
static int is_cmd_response(int cmd);

static int get_svc_hash(unsigned char *svc_name, u16 svc_name_len,
        u8 *svc_hash, u16 svc_hash_len);
NanResponseType get_response_type(WIFI_SUB_COMMAND nan_subcmd);
static NanStatusType nan_map_response_status(int vendor_status);

/* Function to separate the common events to NAN1.0 events */
static int is_de_event(int cmd) {
    bool is_de_evt = false;

    switch(cmd) {
        case NAN_EVENT_SUBSCRIBE_UNMATCH:
        case NAN_EVENT_SUBSCRIBE_TERMINATED:
        case NAN_EVENT_PUBLISH_TERMINATED:
        case NAN_EVENT_SUBSCRIBE_MATCH:
        case NAN_EVENT_FOLLOWUP:
        case NAN_EVENT_TRANSMIT_FOLLOWUP_IND:
        case NAN_EVENT_PUBLISH_REPLIED_IND:
        case NAN_EVENT_MATCH_EXPIRY:
            is_de_evt = true;
            break;
        default:
            /* Not used */
            break;
    }
    return is_de_evt;
}

/* Function to separate NAN2.0 events */
static int is_dp_event(int cmd) {
    bool is_dp_evt = false;

    switch(cmd) {
        case NAN_EVENT_DATA_REQUEST:
        case NAN_EVENT_DATA_CONFIRMATION:
        case NAN_EVENT_DATA_END:
            is_dp_evt = true;
            break;
        default:
            /* Not used */
            break;
    }
    return is_dp_evt;
}

static int is_cmd_response(int cmd) {
    bool is_cmd_resp = false;

    switch(cmd) {
        case NAN_ASYNC_RESPONSE_DISABLED:
            is_cmd_resp = true;
            break;
        default:
            break;
    }
    return is_cmd_resp;
}

static NanStatusType nan_map_response_status (int vendor_status) {
    NanStatusType hal_status;

    switch(vendor_status) {
        case BCME_OK:
            hal_status = NAN_STATUS_SUCCESS;
            break;
        case BCME_BUSY:
            hal_status = NAN_STATUS_NO_RESOURCE_AVAILABLE;
            break;
        case BCME_NOTREADY:
            hal_status = NAN_STATUS_NAN_NOT_ALLOWED;
            break;
        case BCME_BADLEN:
        case BCME_BADBAND:
            hal_status = NAN_STATUS_INVALID_PARAM;
            break;
        case BCME_NOMEM:
            hal_status = NAN_STATUS_NO_RESOURCE_AVAILABLE;
            break;
        case NAN_STATUS_INTERNAL_FAILURE:
        case NAN_STATUS_PROTOCOL_FAILURE:
        case NAN_STATUS_INVALID_PUBLISH_SUBSCRIBE_ID:
        case NAN_STATUS_NO_RESOURCE_AVAILABLE:
        case NAN_STATUS_INVALID_PARAM:
        case NAN_STATUS_INVALID_REQUESTOR_INSTANCE_ID:
        case NAN_STATUS_INVALID_NDP_ID:
        case NAN_STATUS_NAN_NOT_ALLOWED:
        case NAN_STATUS_NO_OTA_ACK:
        case NAN_STATUS_ALREADY_ENABLED:
        case NAN_STATUS_FOLLOWUP_QUEUE_FULL:
        case NAN_STATUS_UNSUPPORTED_CONCURRENCY_NAN_DISABLED:
            hal_status = (NanStatusType)vendor_status;
            break;
        default:
            ALOGE("%s Unknown vendor status, status = %d\n",
                    __func__, vendor_status);
            /* Generic error */
            hal_status = NAN_STATUS_INTERNAL_FAILURE;
    }
    return hal_status;
}

static void prhex(const char *msg, u8 *buf, u32 nbytes);
static const char *NanAttrToString(u16 cmd);
static const char *NanCmdToString(int cmd);
static const char *NanRspToString(int cmd);

#define NAN_DBG_ENTER() {ALOGI("Enter: %s\n", __func__);}
#define NAN_DBG_EXIT() {ALOGI("Exit: %s\n", __func__);}

static int passphrase_to_pmk(u8 *peer_mac, u32 cipher_type,
        u8 *svc_hash, NanSecurityKeyInfo *key_info, u8 *pmk_hex) {
    int result = NAN_STATUS_SUCCESS;
    u8 salt[NAN_SECURITY_SALT_SIZE];

    NAN_DBG_ENTER();
    salt[0] = 0; /* salt_version */
    salt[1] = cipher_type;
    if (svc_hash && peer_mac) {
        memcpy(&salt[2], svc_hash, NAN_SVC_HASH_SIZE);
        memcpy(&salt[2 + NAN_SVC_HASH_SIZE], peer_mac,
                ETHER_ADDR_LEN);
        prhex("Salt", salt, NAN_SECURITY_SALT_SIZE);
    } else {
        ALOGE("Mandory parameters are not present\n");
        return WIFI_ERROR_INVALID_ARGS;
    }
    if (key_info->body.passphrase_info.passphrase_len < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
            key_info->body.passphrase_info.passphrase_len > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
        ALOGE("passphrase must be between %d and %d characters long\n",
                NAN_SECURITY_MIN_PASSPHRASE_LEN,
                NAN_SECURITY_MAX_PASSPHRASE_LEN);
        return WIFI_ERROR_INVALID_ARGS;
    }

    result = PKCS5_PBKDF2_HMAC((const char *) key_info->body.passphrase_info.passphrase,
            key_info->body.passphrase_info.passphrase_len, salt, sizeof(salt),
            4096, ((cipher_type == NAN_CIPHER_SUITE_SHARED_KEY_128_MASK) ?
                (const EVP_MD *)EVP_sha256():(const EVP_MD *)EVP_sha384()), NAN_PMK_INFO_LEN,  pmk_hex);
    prhex("PMK_HEX", pmk_hex, 32);
    NAN_DBG_EXIT();
    return result;
}

typedef void *NanRequest;
nan_hal_info_t info;

#define SVC_LIST(info)                    ((info).svc_list)
#define SVC_LIST_SIZE(info)               ((info).svc_list.total_items)
#define DP_SVC_LIST(info)                 ((info).dp_svc_list)
#define DP_SVC_LIST_SIZE(info)            ((info).dp_svc_list.total_items)
#define NAN_HANDLE(info)                  ((info).nan_handle)
#define GET_NAN_HANDLE(info)              ((NanHandle *)info.nan_handle)
#define NAN_MAC_CONTROL(info)             ((info).nan_mac_control)

///////////////////////////////////////////////////////////////////////////////
class NanHandle
{
    public:
        NanCallbackHandler mHandlers;
        NanHandle(wifi_handle handle, NanCallbackHandler handlers):mHandlers(handlers)
    {}

};

void HandleExpiryEvent(nan_hal_info_t info, nlattr *vendor_data) {
    ALOGI("Received NAN_EVENT_MATCH_EXPIRY\n");
    u16 attr_type;
    NanMatchExpiredInd expired_event;
    memset(&expired_event, 0, sizeof(NanMatchExpiredInd));

    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
        attr_type = it.get_type();
        if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
            expired_event.publish_subscribe_id = it.get_u16();
            ALOGI("pub_sub id = %u\n",
            expired_event.publish_subscribe_id);
        } else if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
            expired_event.requestor_instance_id = it.get_u32();
            ALOGI("req_inst id = %u\n", expired_event.requestor_instance_id);
       }
    }

    if (expired_event.requestor_instance_id && expired_event.publish_subscribe_id) {
        GET_NAN_HANDLE(info)->mHandlers.EventMatchExpired(&expired_event);
    } else {
        ALOGE("Invalid values for notifying the expired event, dropping the event\n");
    }
}

///////////////////////////////////////////////////////////////////////////////
class NanDiscEnginePrimitive : public WifiCommand
{
    NanRequest mParams;
    NanRequestType mType;
    u16 mInstId;
    u32 mPeerId;
    u16 mTxId;

    public:
    NanDiscEnginePrimitive(wifi_interface_handle iface, int id,
            NanRequest params, NanRequestType cmdType)
        : WifiCommand("NanCommand", iface, id), mParams(params), mType(cmdType)
    {
        mInstId = 0;
        mPeerId = 0;
        setTransactionId(id);
    }

    ~NanDiscEnginePrimitive() {
        ALOGE("NanDiscEnginePrimitive destroyed\n");
    }

    void setType(NanRequestType type) {
        mType = type;
    }

    void setInstId(u16 inst_id) {
        mInstId = inst_id;
    }

    int getInstanceId() {
        return mInstId;
    }

    void setTransactionId(u16 tx_id) {
        mTxId = tx_id;
    }

    int getTransactionId() {
        return mTxId;
    }

    void setParams(NanRequest params) {
        mParams = params;
    }

    int createRequest(WifiRequest& request)
    {
        ALOGI("NAN CMD: %s\n", NanCmdToString(mType));
        if (mType == NAN_REQUEST_SUBSCRIBE) {
            return createSubscribeRequest(request,
                    (NanSubscribeRequest *)mParams);
        } else if (mType == NAN_REQUEST_SUBSCRIBE_CANCEL) {
            return createSubscribeCancelRequest(request,
                    (NanSubscribeCancelRequest *)mParams);
        } else if (mType == NAN_REQUEST_PUBLISH) {
            return createPublishRequest(request,
                    (NanPublishRequest *)mParams);
        } else if (mType == NAN_REQUEST_PUBLISH_CANCEL) {
            return createPublishCancelRequest(request,
                    (NanPublishCancelRequest *)mParams);
        } else if (mType == NAN_REQUEST_TRANSMIT_FOLLOWUP) {
            return createTransmitFollowupRequest(request,
                    (NanTransmitFollowupRequest *)mParams);
        } else if (mType == NAN_REQUEST_GET_CAPABILITIES) {
            return getCapabilitiesRequest(request);
        } else {
            ALOGE("%s Unknown Nan request\n", __func__);
        }
        return WIFI_SUCCESS;
    }

    int createPublishRequest(WifiRequest& request, NanPublishRequest *mParams)
    {
        NAN_DBG_ENTER();
        u8 pmk_hex[NAN_PMK_INFO_LEN];
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_PUBLISH);
        if (result < 0) {
            ALOGE("%s Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        /* If handle is 0xFFFF, then update instance_id in response of this request
         * otherwise, update not needed
         */
        mInstId = mParams->publish_id;
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u32(NAN_ATTRIBUTE_PUBLISH_ID, mInstId);
        if (result < 0) {
            ALOGE("%s: Failed to fill pub id, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u16(NAN_ATTRIBUTE_TTL, mParams->ttl);
        if (result < 0) {
            ALOGE("%s: Failed to fill ttl, result = %d\n", __func__, result);
            return result;
        }

        if (ISGREATER(mParams->period, NAN_MAX_PERIOD)) {
            ALOGE("%s:Invalid period value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        result = request.put_u16(NAN_ATTRIBUTE_PERIOD, mParams->period);
        if (result < 0) {
            ALOGE("%s: Failed to fill period, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_PUBLISH_TYPE, mParams->publish_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill pub type, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_TX_TYPE, mParams->tx_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill tx type, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_PUBLISH_COUNT, mParams->publish_count);
        if (result < 0) {
            ALOGE("%s: Failed to fill pub cnt, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->service_name_len) {
            u8 svc_hash[NAN_SVC_HASH_SIZE];

            result = get_svc_hash(mParams->service_name, mParams->service_name_len,
                    svc_hash, NAN_SVC_HASH_SIZE);
            if (result < 0) {
                ALOGE("%s: Failed to get hashed svc name\n", __func__);
                return result;
            }

            mParams->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(mParams->service_name, svc_hash, mParams->service_name_len);

            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_NAME_LEN, mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name len, result = %d\n", __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_NAME, (void *)mParams->service_name,
                    mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->service_specific_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info len, result = %d\n", __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->service_specific_info, mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->rx_match_filter_len) {
            result = request.put_u16(NAN_ATTRIBUTE_RX_MATCH_FILTER_LEN,
                    mParams->rx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill rx match filter len, result = %d\n",
                        __func__, result);
                return result;
            }

            prhex(NULL, mParams->rx_match_filter, mParams->rx_match_filter_len);
            result = request.put(NAN_ATTRIBUTE_RX_MATCH_FILTER,
                    (void *)mParams->rx_match_filter, mParams->rx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill rx match filter, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->tx_match_filter_len) {
            result = request.put_u16(NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN,
                    mParams->tx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill tx match filter, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->tx_match_filter, mParams->tx_match_filter_len);
            result = request.put(NAN_ATTRIBUTE_TX_MATCH_FILTER,
                    (void *)mParams->tx_match_filter, mParams->tx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill tx match filter, result = %d\n",
                        __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_PUBLISH_MATCH, mParams->publish_match_indicator);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_PUBLISH_MATCH, result = %d\n",
                    __func__, result);
            return result;
        }

        if (ISGREATER(mParams->recv_indication_cfg, NAN_PUB_RECV_FLAG_MAX)) {
            ALOGE("%s:Invalid recv_flag value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        result = request.put_u8(NAN_ATTRIBUTE_RECV_IND_CFG,
                mParams->recv_indication_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_RECV_IND_CFG, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_CIPHER_SUITE_TYPE,
                mParams->cipher_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_CIPHER_SUITE_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_KEY_TYPE,
                mParams->key_info.key_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_KEY_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
            if (mParams->key_info.body.pmk_info.pmk_len) {
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA,
                        (void *)mParams->key_info.body.pmk_info.pmk,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
            if (mParams->key_info.body.passphrase_info.passphrase_len < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                    mParams->key_info.body.passphrase_info.passphrase_len > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                ALOGE("passphrase must be between %d and %d characters long\n",
                        NAN_SECURITY_MIN_PASSPHRASE_LEN,
                        NAN_SECURITY_MAX_PASSPHRASE_LEN);
                return NAN_STATUS_INVALID_PARAM;
            } else {
                memset(pmk_hex, 0, NAN_PMK_INFO_LEN);
                result = passphrase_to_pmk(mNmi, mParams->cipher_type,
                        mParams->service_name, &mParams->key_info, pmk_hex);
                if (result < 0) {
                    ALOGE("%s: Failed to convert passphrase to key data, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA, pmk_hex, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->scid_len) {
            if ((mParams->scid_len > NAN_MAX_SCID_BUF_LEN) ||
                    (mParams->scid_len % NAN_SCID_INFO_LEN)) {
                ALOGE("%s: Invalid scid len, = %d\n", __func__, mParams->scid_len);
                return NAN_STATUS_INVALID_PARAM;
            }
            result = request.put_u32(NAN_ATTRIBUTE_SCID_LEN,
                    mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->scid, mParams->scid_len);
            result = request.put(NAN_ATTRIBUTE_SCID,
                    (void *)mParams->scid, mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP,
                mParams->sdea_params.config_nan_data_path);

        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_SECURITY,
                mParams->sdea_params.security_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_SDE_CONTROL_SECURITY, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE,
                mParams->sdea_params.ndp_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT,
                mParams->sdea_params.ranging_state);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_RSSI_THRESHOLD_FLAG,
                mParams->rssi_threshold_flag);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_RSSI_THRESHOLD_FLAG, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->sdea_service_specific_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            result = request.put(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_SVC_RESPONDER_POLICY,
                mParams->service_responder_policy);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_SVC_RESPONDER_POLICY, result = %d\n",
                    __func__, result);
            return result;
        }

        request.attr_end(data);

        ALOGI("Returning successfully\n");
        NAN_DBG_EXIT();
        return result;
    }

    int createPublishCancelRequest(WifiRequest& request, NanPublishCancelRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_PUBLISH_CANCEL);
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        NAN_DBG_ENTER();
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        if (ISGREATER(mInstId, NAN_MAX)) {
            ALOGE("%s:Invalid publish count value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        ALOGI("%s: pub id = %d, inst_id = %d\n", __func__, mParams->publish_id, mInstId);

        result = request.put_u32(NAN_ATTRIBUTE_PUBLISH_ID, mInstId);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_PUBLISH_ID, result = %d\n",
                    __func__, result);
            return result;
        }
        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int createSubscribeRequest(WifiRequest& request, NanSubscribeRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_SUBSCRIBE);
        if (result < 0) {
            ALOGE("%s Failed to create request\n", __func__);
            return result;
        }

        NAN_DBG_ENTER();

        /* If handle is 0xFFFF, then update instance_id in response of this request
         * otherwise, update not needed
         */
        mInstId = mParams->subscribe_id;
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u16(NAN_ATTRIBUTE_SUBSCRIBE_ID, mInstId);
        if (result < 0) {
            ALOGE("%s: Failed to fill sub id, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u16(NAN_ATTRIBUTE_TTL, mParams->ttl);
        if (result < 0) {
            ALOGE("%s: Failed to fill ttl, result = %d\n", __func__, result);
            return result;
        }

        if (ISGREATER(mParams->period, NAN_MAX_PERIOD)) {
            ALOGE("%s:Invalid period value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        result = request.put_u16(NAN_ATTRIBUTE_PERIOD, mParams->period);
        if (result < 0) {
            ALOGE("%s: Failed to fill period, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SUBSCRIBE_TYPE, mParams->subscribe_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill sub type, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SERVICERESPONSEFILTER,
                mParams->serviceResponseFilter);
        if (result < 0) {
            ALOGE("%s: Failed to fill svc resp filter, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SERVICERESPONSEINCLUDE,
                mParams->serviceResponseInclude);
        if (result < 0) {
            ALOGE("%s: Failed to fill svc resp include, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_USESERVICERESPONSEFILTER,
                mParams->useServiceResponseFilter);
        if (result < 0) {
            ALOGE("%s: Failed to fill use svc resp filter, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SSIREQUIREDFORMATCHINDICATION,
                mParams->ssiRequiredForMatchIndication);
        if (result < 0) {
            ALOGE("%s: Failed to fill ssi req match ind, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SUBSCRIBE_MATCH,
                mParams->subscribe_match_indicator);
        if (result < 0) {
            ALOGE("%s: Failed to fill sub match, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SUBSCRIBE_COUNT, mParams->subscribe_count);
        if (result < 0) {
            ALOGE("%s: Failed to fill sub cnt, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->service_name_len) {
            u8 svc_hash[NAN_SVC_HASH_SIZE];

            result = get_svc_hash(mParams->service_name, mParams->service_name_len,
                    svc_hash, NAN_SVC_HASH_SIZE);
            if (result < 0) {
                ALOGE("%s: Failed to get hashed svc name\n", __func__);
                return result;
            }

            mParams->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(mParams->service_name, svc_hash, mParams->service_name_len);

            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_NAME_LEN, mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc hash len, result = %d\n",
                        __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_NAME, (void *)mParams->service_name,
                    mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill hashed svc name, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->service_specific_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info len, result = %d\n", __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->service_specific_info, mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->rx_match_filter_len) {
            result = request.put_u16(NAN_ATTRIBUTE_RX_MATCH_FILTER_LEN,
                    mParams->rx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill rx match filter len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->rx_match_filter, mParams->rx_match_filter_len);
            result = request.put(NAN_ATTRIBUTE_RX_MATCH_FILTER,
                    (void *)mParams->rx_match_filter, mParams->rx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill rx match filter, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->tx_match_filter_len) {
            result = request.put_u16(NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN,
                    mParams->tx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill tx match filter len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->tx_match_filter, mParams->tx_match_filter_len);
            result = request.put(NAN_ATTRIBUTE_TX_MATCH_FILTER,
                    (void *)mParams->tx_match_filter, mParams->tx_match_filter_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill tx match filter, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->num_intf_addr_present > NAN_MAX_SUBSCRIBE_MAX_ADDRESS) {
            ALOGE("%s: Number of mac addrs: %d have crossed the threshold, fail to subscribe\n",
                    __func__, mParams->num_intf_addr_present);
            return WIFI_ERROR_NOT_SUPPORTED;
        } else if (mParams->num_intf_addr_present) {
            result = request.put_u16(NAN_ATTRIBUTE_MAC_ADDR_LIST_NUM_ENTRIES,
                    mParams->num_intf_addr_present);
            if (result < 0) {
                ALOGE("%s: Failed to fill mac addr list no, result = %d\n",
                        __func__, result);
                return result;
            }

            prhex(NULL, (u8 *)mParams->intf_addr,
                    (mParams->num_intf_addr_present * NAN_MAC_ADDR_LEN));
            result = request.put(NAN_ATTRIBUTE_MAC_ADDR_LIST, (void *)mParams->intf_addr,
                    (mParams->num_intf_addr_present * NAN_MAC_ADDR_LEN));
            if (result < 0) {
                ALOGE("%s: Failed to fill mac addr list, result = %d\n", __func__, result);
                return result;
            }
        }

        if (ISGREATER(mParams->recv_indication_cfg, NAN_SUB_RECV_FLAG_MAX)) {
            ALOGE("%s:Invalid recv_flag value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        result = request.put_u8(NAN_ATTRIBUTE_RECV_IND_CFG,
                mParams->recv_indication_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill recv_indication_cfg, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_CIPHER_SUITE_TYPE,
                mParams->cipher_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_CIPHER_SUITE_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_KEY_TYPE,
                mParams->key_info.key_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_KEY_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
            if (mParams->key_info.body.pmk_info.pmk_len) {
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA,
                        (void *)mParams->key_info.body.pmk_info.pmk,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->scid_len) {
            if (mParams->scid_len != NAN_SCID_INFO_LEN) {
                ALOGE("%s: Invalid scid len, = %d\n", __func__, mParams->scid_len);
                return NAN_STATUS_INVALID_PARAM;
            }
            result = request.put_u32(NAN_ATTRIBUTE_SCID_LEN,
                    mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid len, result = %d\n", __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SCID,
                    (void *)mParams->scid, mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP,
                mParams->sdea_params.config_nan_data_path);
        if (result < 0) {
            ALOGE("%s: Failed to fill config_nan_data_path, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_SECURITY,
                mParams->sdea_params.security_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill security_cfg, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE,
                mParams->sdea_params.ndp_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill ndp_type, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT,
                mParams->sdea_params.ranging_state);
        if (result < 0) {
            ALOGE("%s: Failed to fill ranging state, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->sdea_params.ranging_state == NAN_RANGING_ENABLE) {
            result = request.put_u32(NAN_ATTRIBUTE_RANGING_INTERVAL,
                    mParams->ranging_cfg.ranging_interval_msec);
            if (result < 0) {
                ALOGE("%s: Failed to fill ranging_interval_msec, result = %d\n", __func__, result);
                return result;
            }

            result = request.put_u32(NAN_ATTRIBUTE_RANGING_EGRESS_LIMIT,
                    mParams->ranging_cfg.distance_egress_mm);
            if (result < 0) {
                ALOGE("%s: Failed to fill distance_egress_mm, result = %d\n", __func__, result);
                return result;
            }

            result = request.put_u32(NAN_ATTRIBUTE_RANGING_INDICATION,
                    mParams->ranging_cfg.config_ranging_indications);
            if (result < 0) {
                ALOGE("%s: Failed to fill config_ranging_indications, result = %d\n", __func__, result);
                return result;
            }

            result = request.put_u32(NAN_ATTRIBUTE_RANGING_INGRESS_LIMIT,
                    mParams->ranging_cfg.distance_ingress_mm);
            if (result < 0) {
                ALOGE("%s: Failed to fill distance_ingress_mm, result = %d\n", __func__, result);
                return result;
            }
        }

        ALOGI("%s:RSSI threshold flag %d", __func__, mParams->rssi_threshold_flag);
        result = request.put_u8(NAN_ATTRIBUTE_RSSI_THRESHOLD_FLAG,
                mParams->rssi_threshold_flag);
        if (result < 0) {
            ALOGE("%s: Failed to fill rssi_threshold_flag, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->sdea_service_specific_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            result = request.put(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int createSubscribeCancelRequest(WifiRequest& request,
            NanSubscribeCancelRequest *mParams) {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_SUBSCRIBE_CANCEL);
        if (result < 0) {
            ALOGE("%s Failed to create request \n", __func__);
            return result;
        }

        NAN_DBG_ENTER();
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        if (ISGREATER(mInstId, NAN_MAX)) {
            ALOGE("%s:Invalid subscribe id value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }
        ALOGI("%s: sub id = %u\n", __func__, mInstId);

        result = request.put_u16(NAN_ATTRIBUTE_SUBSCRIBE_ID, mInstId);
        if (result < 0) {
            ALOGE("%s: Failed to fill sub id, result = %d\n", __func__, result);
            return result;
        }

        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int createTransmitFollowupRequest(WifiRequest& request,
            NanTransmitFollowupRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_TRANSMIT_FOLLOWUP);
        if (result < 0) {
            ALOGE("%s Failed to create request \n", __func__);
            return result;
        }

        NAN_DBG_ENTER();

        /* If handle is 0xFFFF, then update instance_id in response of this request
         * otherwise, update not needed
         */
        mInstId = mParams->publish_subscribe_id;
        mPeerId = mParams->requestor_instance_id;
        mTxId = getTransactionId();
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u32(NAN_ATTRIBUTE_PEER_ID, mPeerId);
        if (result < 0) {
            ALOGE("%s: Failed to fill peer id, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u16(NAN_ATTRIBUTE_INST_ID, mInstId);
        if (result < 0) {
            ALOGE("%s Failed to fill inst id = %d \n", __func__, mInstId);
            return result;
        }

        result = request.put_addr(NAN_ATTRIBUTE_MAC_ADDR, mParams->addr);
        if (result < 0) {
            ALOGE("%s: Failed to fill mac addr\n", __func__);
            return result;
        }

        if (mParams->service_specific_info_len > 0) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info len \n", __func__);
                return result;
            }

            prhex(NULL, mParams->service_specific_info, mParams->service_specific_info_len);
            result = request.put(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->service_specific_info, mParams->service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to put svc info, result = %d", __func__, result);
                return result;
            }
            mParams->service_specific_info[mParams->service_specific_info_len] = '\0';
            ALOGI("Transmit service info string is %s\n", mParams->service_specific_info);
        }

        if (ISGREATER(mParams->recv_indication_cfg, NAN_PUB_RECV_FLAG_MAX)) {
            ALOGE("%s:Invalid recv_flag value.\n", __FUNCTION__);
            return WIFI_ERROR_NOT_SUPPORTED;
        }

        result = request.put_u8(NAN_ATTRIBUTE_RECV_IND_CFG,
                mParams->recv_indication_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_RECV_IND_CFG, result = %d\n",
                    __func__, result);
            return result;
        }
        result = request.put_u16(NAN_ATTRIBUTE_TRANSAC_ID, mTxId);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_TRANSAC_ID, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->sdea_service_specific_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            result = request.put(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->sdea_service_specific_info, mParams->sdea_service_specific_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill sdea svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int getCapabilitiesRequest(WifiRequest& request) {
        int result = 0;
        NAN_DBG_ENTER();

        result = request.create(GOOGLE_OUI, NAN_SUBCMD_GET_CAPABILITIES);
        if (result < 0) {
            ALOGE("%s Failed to create request \n", __func__);
            return result;
        }
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        request.attr_end(data);

        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int start()
    {
        int result = 0;
        WifiRequest request(familyId(), ifaceId());
        result = createRequest(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to create setup request; result = %d\n", __func__, result);
            return result;
        }

        result = requestResponse(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to configure setup; result = %d\n", __func__, result);
            return result;
        }

        request.destroy();
        return WIFI_SUCCESS;
    }

    virtual bool valid_disc_response_type(int response_type) {
        bool valid = false;
        switch(response_type) {
            case NAN_RESPONSE_PUBLISH:
            case NAN_RESPONSE_SUBSCRIBE:
            case NAN_GET_CAPABILITIES:
            case NAN_RESPONSE_PUBLISH_CANCEL:
            case NAN_RESPONSE_SUBSCRIBE_CANCEL:
            case NAN_RESPONSE_TRANSMIT_FOLLOWUP:
                valid = true;
                break;
            default:
                ALOGE("NanDiscEnginePrmitive:Unknown cmd Response: %d\n", response_type);
                break;
        }
        return valid;
    }

    int handleResponse(WifiEvent& reply)
    {
        nan_hal_resp_t *rsp_vndr_data = NULL;
        NanResponseMsg rsp_data;
        u32 len;
        if (reply.get_cmd() != NL80211_CMD_VENDOR || reply.get_vendor_data() == NULL) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }
        rsp_vndr_data = (nan_hal_resp_t *)reply.get_vendor_data();
        len = reply.get_vendor_data_len();
        ALOGI("NanDiscEnginePrmitive::handle response\n");
        memset(&rsp_data, 0, sizeof(NanResponseMsg));
        rsp_data.response_type = get_response_type((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd);
        if (!valid_disc_response_type(rsp_data.response_type))
            return NL_SKIP;

        rsp_data.status = nan_map_response_status(rsp_vndr_data->status);
        ALOGE("Mapped hal status = %d\n", rsp_data.status);
        if (rsp_vndr_data->nan_reason[0] == '\0') {
            memcpy(rsp_data.nan_error, NanStatusToString(rsp_data.status),
                    strlen(NanStatusToString(rsp_data.status)));
            rsp_data.nan_error[strlen(NanStatusToString(rsp_data.status))] = '\0';
        }
        rsp_data.nan_error[NAN_ERROR_STR_LEN - 1] = '\0';
        ALOGI("\n Received nan_error string %s\n", (u8*)rsp_data.nan_error);

        if (mInstId == 0 &&
                (rsp_data.response_type == NAN_RESPONSE_PUBLISH ||
                 rsp_data.response_type == NAN_RESPONSE_SUBSCRIBE)) {
            ALOGI("Received service instance_id %d\n", rsp_vndr_data->instance_id);
            mInstId = rsp_vndr_data->instance_id;
        }

        if (rsp_data.response_type == NAN_RESPONSE_PUBLISH) {
            rsp_data.body.publish_response.publish_id = mInstId;
        } else if (rsp_data.response_type == NAN_RESPONSE_SUBSCRIBE) {
            rsp_data.body.subscribe_response.subscribe_id = mInstId;
        } else if (rsp_data.response_type == NAN_GET_CAPABILITIES) {
            memcpy((void *)&rsp_data.body.nan_capabilities, (void *)&rsp_vndr_data->capabilities,
                    min(len, sizeof(rsp_data.body.nan_capabilities)));
        }

        GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(id(), &rsp_data);
        ALOGI("NanDiscEnginePrmitive:Received response for cmd [%s], ret %d\n",
                NanRspToString(rsp_data.response_type), rsp_data.status);

        return NL_SKIP;
    }

    int handleEvent(WifiEvent& event) {
        int cmd = event.get_vendor_subcmd();
        u16 attr_type;

        ALOGI("Received NanDiscEnginePrimitive event: %d\n", event.get_cmd());
        nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);

        switch(cmd) {
            case NAN_EVENT_PUBLISH_TERMINATED:
                NanPublishTerminatedInd pub_term_event;

                memset(&pub_term_event, 0, sizeof(NanPublishTerminatedInd));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                        pub_term_event.publish_id = it.get_u32();
                        ALOGI("pub id = %u", pub_term_event.publish_id);
                    } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                        pub_term_event.reason = (NanStatusType)it.get_u8();
                        ALOGI("pub termination status %u", pub_term_event.reason);
                    } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                        u8 len = min(it.get_len(), sizeof(pub_term_event.nan_reason));
                        memcpy(pub_term_event.nan_reason, it.get_data(), len);
                        ALOGI("pub termination reason: %s, len = %d\n",
                            pub_term_event.nan_reason, len);
                    } else {
                        ALOGE("Unknown attr: %u\n", attr_type);
                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventPublishTerminated(&pub_term_event);
                break;

            case NAN_EVENT_SUBSCRIBE_MATCH:
                NanMatchInd subscribe_event;

                memset(&subscribe_event, 0, sizeof(NanMatchInd));

                /* By default FW is unable to cache this match */
                subscribe_event.out_of_resource_flag = true;

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                        ALOGI("sub id: %u", it.get_u16());
                        subscribe_event.publish_subscribe_id = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                        ALOGI("pub id: %u", it.get_u32());
                        subscribe_event.requestor_instance_id = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                        memcpy(subscribe_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                        ALOGI("Publisher mac: " MACSTR, MAC2STR(subscribe_event.addr));
                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                        ALOGI("svc length %d", it.get_u16());
                        subscribe_event.service_specific_info_len = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                        memcpy(subscribe_event.service_specific_info, it.get_data(),
                                subscribe_event.service_specific_info_len);
                        subscribe_event.service_specific_info
                            [subscribe_event.service_specific_info_len] = '\0';
                        ALOGI("service info: %s", subscribe_event.service_specific_info);
                    } else if (attr_type == NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN) {
                        ALOGI("sdf match filter length: %d", subscribe_event.sdf_match_filter_len);
                        subscribe_event.sdf_match_filter_len = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_TX_MATCH_FILTER) {
                        memcpy(subscribe_event.sdf_match_filter, it.get_data(),
                                subscribe_event.sdf_match_filter_len);
                        subscribe_event.sdf_match_filter
                            [subscribe_event.sdf_match_filter_len] = '\0';
                        ALOGI("sdf match filter: %s", subscribe_event.sdf_match_filter);
                    } else if (attr_type == NAN_ATTRIBUTE_CIPHER_SUITE_TYPE) {
                        ALOGI("Peer Cipher suite type: %u", it.get_u8());
                        subscribe_event.peer_cipher_type = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_SCID_LEN) {
                        ALOGI("scid length %d", it.get_u32());
                        subscribe_event.scid_len= it.get_u32();
                    } else if (attr_type == NAN_ATTRIBUTE_SCID) {
                        memcpy(subscribe_event.scid, it.get_data(),
                                subscribe_event.scid_len);
                        subscribe_event.scid
                            [subscribe_event.scid_len] = '\0';
                        ALOGI("scid: %s", subscribe_event.scid);
                    } else if (attr_type == NAN_ATTRIBUTE_RANGING_INDICATION) {
                        subscribe_event.range_info.ranging_event_type = it.get_u32();
                        ALOGI("ranging indication %d", it.get_u32());
                    } else if (attr_type == NAN_ATTRIBUTE_RANGING_RESULT) {
                        subscribe_event.range_info.range_measurement_mm = it.get_u32();
                        ALOGI("ranging result %d", it.get_u32());
                    } else if (attr_type == NAN_ATTRIBUTE_RSSI_PROXIMITY) {
                        subscribe_event.rssi_value = it.get_u8();
                        ALOGI("rssi value : %u", it.get_u8());
                    } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                        ALOGI("sdea svc length %d", it.get_u16());
                        subscribe_event.sdea_service_specific_info_len = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO) {
                        memcpy(subscribe_event.sdea_service_specific_info, it.get_data(),
                                subscribe_event.sdea_service_specific_info_len);
                        subscribe_event.sdea_service_specific_info
                            [subscribe_event.sdea_service_specific_info_len] = '\0';
                        ALOGI("sdea service info: %s", subscribe_event.sdea_service_specific_info);
                    } else if (attr_type == NAN_ATTRIBUTE_MATCH_OCCURRED_FLAG) {
                        ALOGI("match occurred flag: %u", it.get_u8());
                        subscribe_event.match_occured_flag = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_OUT_OF_RESOURCE_FLAG) {
                        ALOGI("Out of resource flag: %u", it.get_u8());
                        subscribe_event.out_of_resource_flag = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP) {
                        ALOGI("Peer config for data path needed: %u", it.get_u8());
                        subscribe_event.peer_sdea_params.config_nan_data_path = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE) {
                        ALOGI("Data Path type: %u", it.get_u8());
                        subscribe_event.peer_sdea_params.ndp_type = (NdpType)it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_SECURITY) {
                        ALOGI("Security configuration: %u", it.get_u8());
                        subscribe_event.peer_sdea_params.security_cfg =
                            (NanDataPathSecurityCfgStatus)it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT) {
                        ALOGI("Ranging report state: %u", it.get_u8());
                        subscribe_event.peer_sdea_params.range_report = (NanRangeReport)it.get_u8();
                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventMatch(&subscribe_event);
                break;

            case NAN_EVENT_SUBSCRIBE_UNMATCH:
                ALOGE("%s: Not applicable yet\n", __func__);
                break;

            case NAN_EVENT_SUBSCRIBE_TERMINATED:
                NanSubscribeTerminatedInd sub_term_event;
                memset(&sub_term_event, 0, sizeof(NanSubscribeTerminatedInd));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                        sub_term_event.subscribe_id = it.get_u16();
                        ALOGI("sub id = %u", sub_term_event.subscribe_id);
                    } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                        sub_term_event.reason = (NanStatusType)it.get_u16();
                        ALOGI("sub termination status %u", sub_term_event.reason);
                    } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                        u8 len = min(it.get_len(), sizeof(sub_term_event.nan_reason));
                        memcpy(sub_term_event.nan_reason, it.get_data(), len);
                        ALOGI("sub termination nan reason: %s, len = %d\n",
                            sub_term_event.nan_reason, len);
                    } else {
                        ALOGI("Unknown attr: %d\n", attr_type);
                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventSubscribeTerminated(&sub_term_event);
                break;
            case NAN_EVENT_MATCH_EXPIRY:
                HandleExpiryEvent(info, vendor_data);
                break;
            case NAN_EVENT_FOLLOWUP:
                NanFollowupInd followup_event;
                memset(&followup_event, 0, sizeof(NanFollowupInd));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                        memcpy(followup_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                    } else if (attr_type == NAN_ATTRIBUTE_PEER_ID) {
                        followup_event.publish_subscribe_id = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_INST_ID) {
                        followup_event.requestor_instance_id = it.get_u32();
                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                        followup_event.service_specific_info_len = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                        memcpy(followup_event.service_specific_info, it.get_data(),
                                followup_event.service_specific_info_len);
                    } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO) {
                        memcpy(followup_event.sdea_service_specific_info, it.get_data(),
                                followup_event.sdea_service_specific_info_len);
                    }
                }
                counters.transmit_recv++;
                GET_NAN_HANDLE(info)->mHandlers.EventFollowup(&followup_event);
                break;

            case NAN_EVENT_TRANSMIT_FOLLOWUP_IND:
                NanTransmitFollowupInd followup_ind;
                counters.transmit_txs++;
                memset(&followup_ind, 0, sizeof(NanTransmitFollowupInd));
                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();
                    if (attr_type == NAN_ATTRIBUTE_TRANSAC_ID) {
                        followup_ind.id = it.get_u16();
                    } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                        followup_ind.reason = (NanStatusType)it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                        u8 len = min(it.get_len(), sizeof(followup_ind.nan_reason));
                        memcpy(followup_ind.nan_reason, it.get_data(), len);
                        ALOGI("nan transmit followup ind: reason: %s, len = %d\n",
                            followup_ind.nan_reason, len);
                    }
                }
                GET_NAN_HANDLE(info)->mHandlers.EventTransmitFollowup(&followup_ind);
                break;
#ifdef NOT_YET
            case NAN_EVENT_PUBLISH_REPLIED_IND:
                NanPublishRepliedInd pub_reply_event;
                memset(&pub_reply_event, 0, sizeof(pub_reply_event));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                        ALOGI("sub id: %u", it.get_u16());
                        pub_reply_event.requestor_instance_id = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                        memcpy(pub_reply_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                        ALOGI("Subscriber mac: " MACSTR, MAC2STR(pub_reply_event.addr));
                    } else if (attr_type == NAN_ATTRIBUTE_RSSI_PROXIMITY) {
                        pub_reply_event.rssi_value = it.get_u8();
                        ALOGI("Received rssi value : %u", it.get_u8());
                    }
                }
                GET_NAN_HANDLE(info)->mHandlers.EventPublishReplied(&pub_reply_event);
                break;
#endif /* NOT_YET */
        } // end-of-switch-case
        return NL_SKIP;
    }
};


///////////////////////////////////////////////////////////////////////////////
class NanDataPathPrimitive : public WifiCommand
{
    NanRequest reqContext;
    u32 mNdpId;
    NanRequestType mType;
    u8 count;

    public:
    NanDataPathPrimitive(wifi_interface_handle iface, int id,
            NanRequest params, NanRequestType cmdType)
        : WifiCommand("NanCommand", iface, id), reqContext(params), mType(cmdType)
    {
        mNdpId = 0;
        count = 0;
    }
    ~NanDataPathPrimitive() {
        ALOGE("NanDataPathPrimitive destroyed\n");
    }
    u8 mSvcHash[NAN_SVC_HASH_SIZE];
    u8 mPubNmi[NAN_MAC_ADDR_LEN];

    void setType(NanRequestType type ) {
        mType = type;
    }

    int getNdpId() {
        return mNdpId;
    }

    int createRequest(WifiRequest& request)
    {
        ALOGI("NAN CMD: %s\n", NanCmdToString(mType));
        if (mType == NAN_DATA_PATH_IFACE_CREATE) {
            return createDataPathIfaceRequest(request, (char *)reqContext);
        } else if (mType == NAN_DATA_PATH_IFACE_DELETE) {
            return deleteDataPathIfaceRequest(request, (char *)reqContext);
        } else if (mType == NAN_DATA_PATH_INIT_REQUEST) {
            return createDataPathInitRequest(request,
                    (NanDataPathInitiatorRequest *)reqContext);
        } else if (mType == NAN_DATA_PATH_IND_RESPONSE) {
            return createDataPathIndResponse(request,
                    (NanDataPathIndicationResponse *)reqContext);
        } else if (mType == NAN_DATA_PATH_END) {
            return createDataPathEndRequest(request,
                    (NanDataPathEndRequest *)reqContext);
        } else if (mType == NAN_DATA_PATH_SEC_INFO) {
            return createDataPathSecInfoRequest(request,
                    (NanDataPathSecInfoRequest *)reqContext);
        } else {
            ALOGE("%s: Unknown NDP request: %d\n", __func__, mType);
        }

        return WIFI_SUCCESS;
    }

    int createDataPathIfaceRequest(WifiRequest& request, char *iface_name)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_IFACE_CREATE);
        if (result < 0) {
            ALOGE("%s Failed to create request\n", __func__);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);
        result = request.put_string(NAN_ATTRIBUTE_IFACE, (char *)iface_name);
        if (result < 0) {
            ALOGE("%s: Failed to fill iface, result = %d\n", __func__, result);
            return result;
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int deleteDataPathIfaceRequest(WifiRequest& request, char *iface_name)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_IFACE_DELETE);
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_string(NAN_ATTRIBUTE_IFACE, (char *)iface_name);
        if (result < 0) {
            ALOGE("%s: Failed to fill iface, result = %d\n", __func__, result);
            return result;
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int createDataPathSecInfoRequest(WifiRequest& request, NanDataPathSecInfoRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_SEC_INFO);
        if (result < 0) {
            ALOGE("%s Failed to create request\n", __func__);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u32(NAN_ATTRIBUTE_PUBLISH_ID, mParams->requestor_instance_id);
        if (result < 0) {
            ALOGE("%s: Failed to fill instance id = %d, result = %d\n",
                    __func__, mParams->requestor_instance_id, result);
            return result;
        }

        result = request.put_addr(NAN_ATTRIBUTE_MAC_ADDR, mParams->peer_disc_mac_addr);
        if (result < 0) {
            ALOGE("%s: Failed to fill mac addr, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u32(NAN_ATTRIBUTE_NDP_ID,  mParams->ndp_instance_id);
        if (result < 0) {
            ALOGE("%s: Failed to fill ndp_instance_id = %d, result = %d\n",
                    __func__, mParams->ndp_instance_id, result);
            return result;
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int createDataPathInitRequest(WifiRequest& request, NanDataPathInitiatorRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_REQUEST);
        u8 pmk_hex[NAN_PMK_INFO_LEN];
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        mNdpId = mParams->requestor_instance_id;
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u32(NAN_ATTRIBUTE_PUBLISH_ID, mParams->requestor_instance_id);
        if (result < 0) {
            ALOGE("%s: Failed to fill pub id = %d, result = %d\n",
                    __func__, mParams->requestor_instance_id, result);
            return result;
        }

        result = request.put_u32(NAN_ATTRIBUTE_CHANNEL, (u32)mParams->channel);
        if (result < 0) {
            ALOGE("%s: Failed to fill channel = %d, result = %d\n",
                    __func__, mParams->channel, result);
            return result;
        }

        result = request.put_addr(NAN_ATTRIBUTE_MAC_ADDR, mParams->peer_disc_mac_addr);
        if (result < 0) {
            ALOGE("%s: Failed to fill mac addr, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_string(NAN_ATTRIBUTE_IFACE, mParams->ndp_iface);
        if (result < 0) {
            ALOGE("%s: Failed to fill ndp_iface, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SECURITY,
                (NanDataPathSecurityCfgStatus)mParams->ndp_cfg.security_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill security, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_QOS,
                (NanDataPathQosCfg) mParams->ndp_cfg.qos_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill QoS, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->app_info.ndp_app_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->app_info.ndp_app_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info len = %d, result = %d\n",
                        __func__, mParams->app_info.ndp_app_info_len, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->app_info.ndp_app_info, mParams->app_info.ndp_app_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_CIPHER_SUITE_TYPE,
                mParams->cipher_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_CIPHER_SUITE_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_KEY_TYPE,
                mParams->key_info.key_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_KEY_TYPE, result = %d\n",
                    __func__, result);
            return result;
        }


        if (mParams->service_name_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_NAME_LEN, mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->service_name, mParams->service_name_len);
            result = request.put(NAN_ATTRIBUTE_SERVICE_NAME, (void *)mParams->service_name,
                    mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
            if (mParams->key_info.body.pmk_info.pmk_len) {
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA,
                        (void *)mParams->key_info.body.pmk_info.pmk,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
            if (mParams->key_info.body.passphrase_info.passphrase_len < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                    mParams->key_info.body.passphrase_info.passphrase_len > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                ALOGE("passphrase must be between %d and %d characters long\n",
                        NAN_SECURITY_MIN_PASSPHRASE_LEN,
                        NAN_SECURITY_MAX_PASSPHRASE_LEN);
                return NAN_STATUS_INVALID_PARAM;
            } else {
                memset(pmk_hex, 0, NAN_PMK_INFO_LEN);
                result = passphrase_to_pmk(mParams->peer_disc_mac_addr, mParams->cipher_type,
                        mParams->service_name, &mParams->key_info, pmk_hex);
                if (result < 0) {
                    ALOGE("%s: Failed to convert passphrase to key data, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA, pmk_hex, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase, result = %d\n", __func__, result);
                    return result;
                }
                prhex("PMK", pmk_hex, NAN_PMK_INFO_LEN);
            }
        }

        if (mParams->scid_len) {
            if (mParams->scid_len != NAN_SCID_INFO_LEN) {
                ALOGE("%s: Invalid scid len, = %d\n", __func__, mParams->scid_len);
                return NAN_STATUS_INVALID_PARAM;
            }
            result = request.put_u32(NAN_ATTRIBUTE_SCID_LEN,
                    mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid len, result = %d\n", __func__, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SCID,
                    (void *)mParams->scid, mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid, result = %d\n", __func__, result);
                return result;
            }
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int createDataPathIndResponse(WifiRequest& request,
            NanDataPathIndicationResponse *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_RESPONSE);
        u8 pmk_hex[NAN_PMK_INFO_LEN];
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u32(NAN_ATTRIBUTE_NDP_ID,  mParams->ndp_instance_id);
        if (result < 0) {
            ALOGE("%s: Failed to fill ndp_instance_id = %d, result = %d\n",
                    __func__, mParams->ndp_instance_id, result);
            return result;
        }

        result = request.put_string(NAN_ATTRIBUTE_IFACE, mParams->ndp_iface);
        if (result < 0) {
            ALOGE("%s: Failed to fill ndp_iface, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_SECURITY,
                (NanDataPathSecurityCfgStatus)mParams->ndp_cfg.security_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill security_cfg, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_QOS,
                (NanDataPathQosCfg)mParams->ndp_cfg.qos_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill qos_cfg, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->app_info.ndp_app_info_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN,
                    mParams->app_info.ndp_app_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info len = %d, result = %d\n",
                        __func__, mParams->app_info.ndp_app_info_len, result);
                return result;
            }

            result = request.put(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO,
                    (void *)mParams->app_info.ndp_app_info, mParams->app_info.ndp_app_info_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc info, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_RSP_CODE, mParams->rsp_code);
        if (result < 0) {
            ALOGE("%s: Failed to fill resp code = %d, result = %d\n",
                    __func__, mParams->rsp_code, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_CIPHER_SUITE_TYPE,
                mParams->cipher_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill cipher_type, result = %d\n",
                    __func__, result);
            return result;
        }

        result = request.put_u8(NAN_ATTRIBUTE_KEY_TYPE,
                mParams->key_info.key_type);
        if (result < 0) {
            ALOGE("%s: Failed to fill key type, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->service_name_len) {
            result = request.put_u16(NAN_ATTRIBUTE_SERVICE_NAME_LEN, mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->service_name, mParams->service_name_len);
            result = request.put(NAN_ATTRIBUTE_SERVICE_NAME, (void *)mParams->service_name,
                    mParams->service_name_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill svc name, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PMK) {
            if (mParams->key_info.body.pmk_info.pmk_len) {
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA,
                        (void *)mParams->key_info.body.pmk_info.pmk,
                        mParams->key_info.body.pmk_info.pmk_len);
                if (result < 0) {
                    ALOGE("%s: Failed to fill pmk, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
            if (mParams->key_info.body.passphrase_info.passphrase_len < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                    mParams->key_info.body.passphrase_info.passphrase_len > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                ALOGE("passphrase must be between %d and %d characters long\n",
                        NAN_SECURITY_MIN_PASSPHRASE_LEN,
                        NAN_SECURITY_MAX_PASSPHRASE_LEN);
                return NAN_STATUS_INVALID_PARAM;
            } else {
                memset(pmk_hex, 0, NAN_PMK_INFO_LEN);
                result = passphrase_to_pmk(mPubNmi, mParams->cipher_type,
                        mParams->service_name, &mParams->key_info, pmk_hex);
                if (result < 0) {
                    ALOGE("%s: Failed to convert passphrase to key data, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put_u32(NAN_ATTRIBUTE_KEY_LEN, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase len, result = %d\n", __func__, result);
                    return result;
                }
                result = request.put(NAN_ATTRIBUTE_KEY_DATA, pmk_hex, NAN_PMK_INFO_LEN);
                if (result < 0) {
                    ALOGE("%s: Failed to fill passphrase, result = %d\n", __func__, result);
                    return result;
                }
            }
        }

        if (mParams->scid_len) {
            if (mParams->scid_len != NAN_SCID_INFO_LEN) {
                ALOGE("%s: Invalid scid len, = %d\n", __func__, mParams->scid_len);
                return NAN_STATUS_INVALID_PARAM;
            }
            result = request.put_u32(NAN_ATTRIBUTE_SCID_LEN,
                    mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid len, result = %d\n", __func__, result);
                return result;
            }

            prhex(NULL, mParams->scid, mParams->scid_len);
            result = request.put(NAN_ATTRIBUTE_SCID,
                    (void *)mParams->scid, mParams->scid_len);
            if (result < 0) {
                ALOGE("%s: Failed to fill scid, result = %d\n", __func__, result);
                return result;
            }
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int createDataPathEndRequest(WifiRequest& request, NanDataPathEndRequest *mParams)
    {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DATA_PATH_END);
        if (result < 0) {
            ALOGE("%s: Failed to create request, result = %d\n", __func__, result);
            return result;
        }

        count = mParams->num_ndp_instances;
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        result = request.put_u8(NAN_ATTRIBUTE_INST_COUNT, mParams->num_ndp_instances);
        if (result < 0) {
            ALOGE("%s: Failed to fill num_ndp_instances = %d, result = %d\n",
                    __func__, mParams->num_ndp_instances, result);
            return result;
        }

        while (count) {
            result = request.put_u32(NAN_ATTRIBUTE_NDP_ID, mParams->ndp_instance_id[count-1]);
            if (result < 0) {
                ALOGE("%s: Failed to fill ndp id = %d, result = %d\n",
                        __func__, mParams->ndp_instance_id[count-1], result);
                return result;
            }
            ALOGE("%s:NDP ID = %d\n", __func__, mParams->ndp_instance_id[count-1]);
            count -= 1;
        }

        request.attr_end(data);
        return WIFI_SUCCESS;
    }

    int open()
    {
        WifiRequest request(familyId(), ifaceId());
        int result = createRequest(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: failed to create setup request; result = %d", __func__, result);
            return result;
        }

        result = requestResponse(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: failed to configure setup; result = %d", __func__, result);
            return result;
        }

        request.destroy();
        return WIFI_SUCCESS;
    }

    virtual bool valid_dp_response_type(int response_type) {
        bool valid = false;
        switch(response_type) {
            case NAN_DP_INTERFACE_CREATE:
            case NAN_DP_INTERFACE_DELETE:
            case NAN_DP_INITIATOR_RESPONSE:
            case NAN_DP_RESPONDER_RESPONSE:
            case NAN_DP_END:
                valid = true;
                break;
            default:
                ALOGE("NanDataPathPrmitive::Unknown cmd Response: %d\n", response_type);
                break;
        }
        return valid;
    }

    int handleResponse(WifiEvent& reply)
    {
        nan_hal_resp_t *rsp_vndr_data = NULL;

        if (reply.get_cmd() != NL80211_CMD_VENDOR || reply.get_vendor_data() == NULL) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }

        rsp_vndr_data = (nan_hal_resp_t *)reply.get_vendor_data();
        ALOGI("NanDataPathPrmitive::handle response\n");
        int32_t result = rsp_vndr_data->value;
        NanResponseMsg rsp_data;

        memset(&rsp_data, 0, sizeof(NanResponseMsg));
        rsp_data.response_type = get_response_type((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd);

        if ((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd == NAN_SUBCMD_DATA_PATH_SEC_INFO) {
            /* Follow through */
        } else if (!valid_dp_response_type(rsp_data.response_type)) {
            return NL_SKIP;
        }
        rsp_data.status = nan_map_response_status(rsp_vndr_data->status);
        ALOGE("Mapped hal status = %d\n", rsp_data.status);

        if (rsp_vndr_data->nan_reason[0] == '\0') {
            memcpy(rsp_data.nan_error, NanStatusToString(rsp_data.status),
                    strlen(NanStatusToString(rsp_data.status)));
            rsp_data.nan_error[strlen(NanStatusToString(rsp_data.status))] = '\0';
        }
        rsp_data.nan_error[NAN_ERROR_STR_LEN - 1] = '\0';
        ALOGI("\n Received nan_error string %s\n", (u8*)rsp_data.nan_error);

        if (rsp_data.response_type == NAN_DP_INITIATOR_RESPONSE) {
            ALOGI("received ndp instance_id %d and ret = %d\n", rsp_vndr_data->ndp_instance_id, result);
            rsp_data.body.data_request_response.ndp_instance_id = rsp_vndr_data->ndp_instance_id;
            mNdpId = rsp_vndr_data->ndp_instance_id;
        } else if ((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd == NAN_SUBCMD_DATA_PATH_SEC_INFO) {
            memcpy(mPubNmi, rsp_vndr_data->pub_nmi, NAN_MAC_ADDR_LEN);
            memcpy(mSvcHash, rsp_vndr_data->svc_hash, NAN_SVC_HASH_SIZE);
            return NL_SKIP;
        }

        ALOGI("NanDataPathPrmitive:Received response for cmd [%s], ret %d\n",
                NanRspToString(rsp_data.response_type), rsp_data.status);
        GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(id(), &rsp_data);
        return NL_SKIP;
    }

    int handleEvent(WifiEvent& event)
    {
        int cmd = event.get_vendor_subcmd();
        u16 attr_type;

        nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);

        switch(cmd) {
            case NAN_EVENT_DATA_REQUEST: {
                NanDataPathRequestInd ndp_request_event;
                memset(&ndp_request_event, 0, sizeof(NanDataPathRequestInd));
                u16 ndp_ind_app_info_len = 0;
                counters.dp_req_evt++;
                ALOGI("Received NAN_EVENT_DATA_REQUEST_INDICATION\n");

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                        ALOGI("publish_id: %u\n", it.get_u32());
                        ndp_request_event.service_instance_id = it.get_u32();

                    } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                        memcpy(ndp_request_event.peer_disc_mac_addr,
                                it.get_data(), NAN_MAC_ADDR_LEN);
                        ALOGI("Discovery MAC addr of the peer/initiator: " MACSTR "\n",
                                MAC2STR(ndp_request_event.peer_disc_mac_addr));

                    } else if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                        ALOGI("ndp id: %u\n", it.get_u32());
                        ndp_request_event.ndp_instance_id = it.get_u32();

                    } else if (attr_type == NAN_ATTRIBUTE_SECURITY) {
                        ALOGI("security: %u\n",
                                (NanDataPathSecurityCfgStatus)it.get_u8());
                        ndp_request_event.ndp_cfg.security_cfg =
                            (NanDataPathSecurityCfgStatus)it.get_u8();

                    } else if (attr_type == NAN_ATTRIBUTE_QOS) {
                        ALOGI("QoS: %u\n", (NanDataPathQosCfg)it.get_u8());
                        ndp_request_event.ndp_cfg.qos_cfg = (NanDataPathQosCfg)it.get_u8();

                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                        ndp_request_event.app_info.ndp_app_info_len = it.get_u16();
                        ndp_ind_app_info_len = ndp_request_event.app_info.ndp_app_info_len;

                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                        memcpy(ndp_request_event.app_info.ndp_app_info, it.get_data(),
                                ndp_ind_app_info_len);
                        ndp_request_event.app_info.ndp_app_info
                            [ndp_ind_app_info_len] = '\0';
                        ALOGI("service info: %s\n", ndp_request_event.app_info.ndp_app_info);

                    } else if (attr_type == NAN_ATTRIBUTE_SCID_LEN) {
                        ALOGI("scid len: %u\n", it.get_u32());
                        ndp_request_event.scid_len = it.get_u32();

                    } else if (attr_type == NAN_ATTRIBUTE_SCID) {
                        memcpy(ndp_request_event.scid, it.get_data(),
                                ndp_request_event.scid_len);
                        ndp_request_event.scid[ndp_request_event.scid_len] = '\0';
                        ALOGI("scid : %s\n", ndp_request_event.scid);

                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventDataRequest(&ndp_request_event);
                break;
            }
            case NAN_EVENT_DATA_CONFIRMATION: {
                NanDataPathConfirmInd ndp_create_confirmation_event;
                memset(&ndp_create_confirmation_event, 0, sizeof(NanDataPathConfirmInd));
                u16 ndp_conf_app_info_len = 0;
                u8 chan_idx = 0;
                counters.dp_confirm_evt++;
                ALOGI("Received NAN_EVENT_DATA_CONFIRMATION\n");

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                        ALOGI("ndp id: %u", it.get_u32());
                        ndp_create_confirmation_event.ndp_instance_id = it.get_u32();

                    } else if (attr_type == NAN_ATTRIBUTE_PEER_NDI_MAC_ADDR) {
                        memcpy(ndp_create_confirmation_event.peer_ndi_mac_addr, it.get_data(),
                                NAN_MAC_ADDR_LEN);
                        ALOGI("NDI mac address of the peer: " MACSTR "\n",
                                MAC2STR(ndp_create_confirmation_event.peer_ndi_mac_addr));

                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                        ALOGI("service info len: %d", it.get_u16());
                        ndp_create_confirmation_event.app_info.ndp_app_info_len = it.get_u16();
                        ndp_conf_app_info_len = ndp_create_confirmation_event.app_info.ndp_app_info_len;
                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                        memcpy(ndp_create_confirmation_event.app_info.ndp_app_info,
                                it.get_data(), ndp_conf_app_info_len);
                        ndp_create_confirmation_event.app_info.ndp_app_info[ndp_conf_app_info_len]
                            = '\0';
                        ALOGI("service info: %s",
                                ndp_create_confirmation_event.app_info.ndp_app_info);

                    } else if (attr_type == NAN_ATTRIBUTE_RSP_CODE) {
                        ALOGI("response code: %u", (NanDataPathResponseCode)it.get_u8());
                        ndp_create_confirmation_event.rsp_code =
                            (NanDataPathResponseCode)it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                        ALOGI("reason code %u", (NanDataPathResponseCode)it.get_u8());
                        ndp_create_confirmation_event.rsp_code =
                            (NanDataPathResponseCode)it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_NUM_CHANNELS) {
                        ALOGI("num channels %u", it.get_u32());
                        if (it.get_u32() <= NAN_MAX_CHANNEL_INFO_SUPPORTED) {
                            ndp_create_confirmation_event.num_channels = it.get_u32();
                        } else {
                            ndp_create_confirmation_event.num_channels =
                                NAN_MAX_CHANNEL_INFO_SUPPORTED;
                            ALOGE("num channels reset to max allowed %u",
                                ndp_create_confirmation_event.num_channels);
                        }
                    } else if (attr_type == NAN_ATTRIBUTE_CHANNEL_INFO) {
                        ALOGI("Channel info \n");
                        memcpy((u8 *)ndp_create_confirmation_event.channel_info, it.get_data(),
                            ndp_create_confirmation_event.num_channels * sizeof(NanChannelInfo));
                        while (chan_idx < ndp_create_confirmation_event.num_channels) {
                            ALOGI("channel: %u, Bandwidth: %u, nss: %u\n",
                                ndp_create_confirmation_event.channel_info[chan_idx].channel,
                                ndp_create_confirmation_event.channel_info[chan_idx].bandwidth,
                                ndp_create_confirmation_event.channel_info[chan_idx].nss);
                            chan_idx++;
                        }
                    }
                }
                GET_NAN_HANDLE(info)->mHandlers.EventDataConfirm(&ndp_create_confirmation_event);
                break;
            }
            case NAN_EVENT_DATA_END: {
                NanDataPathEndInd ndp_end_event;
                memset(&ndp_end_event, 0, sizeof(NanDataPathEndInd));
                u16 attr_type;
                ALOGI("Received NAN_EVENT_DATA_END\n");

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_INST_COUNT) {
                        ALOGI("ndp count: %u\n", it.get_u8());
                        ndp_end_event.num_ndp_instances = it.get_u8();
                        count = it.get_u8();
                    } else if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                        while (count) {
                            ndp_end_event.ndp_instance_id[count-1] = it.get_u32();
                            ALOGI("NDP Id from the Event = %u\n", ndp_end_event.ndp_instance_id[count-1]);
                            count -= 1;
                        }
                    } else {
                        ALOGI("Unknown attr_type: %s\n", NanAttrToString(attr_type));
                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventDataEnd(&ndp_end_event);
                break;
            }
        } // end-of-switch
        return NL_SKIP;
    }
};


///////////////////////////////////////////////////////////////////////////////
class NanMacControl : public WifiCommand
{
    NanRequest mParams;
    transaction_id mId = NAN_MAC_INVALID_TRANSID;
    wifi_interface_handle mIface;
    NanRequestType mType;
    u32 mVersion;

    public:
    NanMacControl(wifi_interface_handle iface, int id,
            NanRequest params, NanRequestType cmdType)
        : WifiCommand("NanCommand", iface, id), mParams(params), mType(cmdType)
    {
        mVersion = 0;
        setIface(iface);
        setId(id);
    }
    ~NanMacControl() {
        ALOGE("NanMacControl destroyed\n");
    }

    void setIface(wifi_interface_handle iface ) {
        mIface = iface;
    }

    void setId(transaction_id id) {
        if (id != NAN_MAC_INVALID_TRANSID) {
            mId = id;
        }
    }

    transaction_id getId() {
        return mId;
    }

    void setType(NanRequestType type) {
        mType = type;
    }
    u32 getVersion() {
        return mVersion;
    }

    void setMsg(NanRequest params) {
        mParams = params;
    }

    int createRequest(WifiRequest& request) {
        ALOGI("NAN CMD: %s\n", NanCmdToString(mType));
        if (mType == NAN_REQUEST_ENABLE) {
            return createEnableRequest(request, (NanEnableRequest *)mParams);
        } else if (mType == NAN_REQUEST_DISABLE) {
            return createDisableRequest(request);
        } else if (mType == NAN_REQUEST_CONFIG) {
            return createConfigRequest(request, (NanConfigRequest*)mParams);
        } else if (mType == NAN_REQUEST_STATS) {
            /* TODO: Not yet implemented */
        } else if (mType == NAN_REQUEST_TCA) {
            /* TODO: Not yet implemented */
        } else if (mType == NAN_VERSION_INFO) {
            return createVersionRequest(request);
        } else {
            ALOGE("Unknown Nan request\n");
        }

        return WIFI_SUCCESS;
    }

    int createVersionRequest(WifiRequest& request) {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_VERSION_INFO);
        if (result < 0) {
            ALOGE("%s: Fail to create request\n", __func__);
            return result;
        }
        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);
        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int createEnableRequest(WifiRequest& request, NanEnableRequest *mParams) {
        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_ENABLE);
        s8 rssi;
        if (result < 0) {
            ALOGE("%s: Fail to create request\n", __func__);
            return result;
        }

        NAN_DBG_ENTER();

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        if (mParams->config_2dot4g_support) {
            result = request.put_u8(NAN_ATTRIBUTE_2G_SUPPORT, mParams->support_2dot4g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 2g support, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_support_5g) {
            result = request.put_u8(NAN_ATTRIBUTE_5G_SUPPORT, mParams->support_5g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g support, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u16(NAN_ATTRIBUTE_CLUSTER_LOW, mParams->cluster_low);
        if (result < 0) {
            ALOGE("%s: Failing in cluster low, result = %d\n", __func__, result);
            return result;
        }

        result = request.put_u16(NAN_ATTRIBUTE_CLUSTER_HIGH, mParams->cluster_high);
        if (result < 0) {
            ALOGE("%s: Failing in cluster high, result = %d\n", __func__, result);
            return result;
        }

        if (mParams->config_sid_beacon) {
            result = request.put_u8(NAN_ATTRIBUTE_SID_BEACON, mParams->sid_beacon_val);
            if (result < 0) {
                ALOGE("%s: Failing in sid beacon, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_subscribe_sid_beacon) {
            result = request.put_u8(NAN_ATTRIBUTE_SUB_SID_BEACON, mParams->subscribe_sid_beacon_val);
            if (result < 0) {
                ALOGE("%s: Failing in sub sid beacon, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_2dot4g_beacons) {
            result = request.put_u8(NAN_ATTRIBUTE_SYNC_DISC_2G_BEACON, mParams->beacon_2dot4g_val);
            if (result < 0) {
                ALOGE("%s: Failing in beacon_2dot4g_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_beacons) {
            result = request.put_u8(NAN_ATTRIBUTE_SYNC_DISC_5G_BEACON, mParams->beacon_5g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g beacon, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_2dot4g_sdf) {
            result = request.put_u8(NAN_ATTRIBUTE_SDF_2G_SUPPORT, mParams->sdf_2dot4g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 2dot4g sdf, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_sdf) {
            result = request.put_u8(NAN_ATTRIBUTE_SDF_5G_SUPPORT, mParams->sdf_5g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g sdf, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_2dot4g_rssi_close) {
            if (ISGREATER(mParams->rssi_close_2dot4g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_close_2dot4g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_CLOSE, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in 2g rssi close, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_2dot4g_rssi_middle) {
            if (ISGREATER(mParams->rssi_middle_2dot4g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_middle_2dot4g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_MIDDLE, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in 2g rssi middle, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_2dot4g_rssi_proximity) {
            if (ISGREATER(mParams->rssi_proximity_2dot4g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_proximity_2dot4g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_PROXIMITY, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in 2g rssi proximity, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_rssi_close) {
            if (ISGREATER(mParams->rssi_close_5g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_close_5g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_CLOSE_5G, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in 5g rssi close, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_rssi_middle) {
            if (ISGREATER(mParams->rssi_middle_5g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_middle_5g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_MIDDLE_5G, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in 5g rssi middle, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_rssi_close_proximity) {
            if (ISGREATER(mParams->rssi_close_proximity_5g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_close_proximity_5g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_PROXIMITY_5G, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in rssi_close_proximity_5g_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_cluster_attribute_val) {
            result = request.put_u8(NAN_ATTRIBUTE_CONF_CLUSTER_VAL, mParams->config_cluster_attribute_val);
            if (result < 0) {
                ALOGE("%s: Failing in config_cluster_attribute_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_hop_count_limit) {
            result = request.put_u8(NAN_ATTRIBUTE_HOP_COUNT_LIMIT,
                    mParams->hop_count_limit_val);
            if (result < 0) {
                ALOGE("%s: Failing in hop cnt limit, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_oui) {
            ALOGI("%s: oui = 0x%04x\n", __func__, mParams->oui_val);
            result = request.put_u32(NAN_ATTRIBUTE_OUI, mParams->oui_val);
            if (result < 0) {
                ALOGE("%s: Failing in oui, result = %d\n", __func__, result);
                return result;
            }
        }

        result = request.put_u8(NAN_ATTRIBUTE_MASTER_PREF, mParams->master_pref);
        if (result < 0) {
            ALOGE("%s: Failing in master pref, result = %d\n", __func__, result);
            return result;
        }
        if (mParams->config_random_factor_force) {
            result = request.put_u8(NAN_ATTRIBUTE_RANDOM_FACTOR, mParams->random_factor_force_val);
            if (result < 0) {
                ALOGE("%s: Failing in random factor, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_24g_channel) {
            result = request.put_u32(NAN_ATTRIBUTE_24G_CHANNEL, mParams->channel_24g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 2.4g channel, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_channel) {
            result = request.put_u32(NAN_ATTRIBUTE_5G_CHANNEL, mParams->channel_5g_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g channel, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_intf_addr) {
            result = request.put_addr(NAN_ATTRIBUTE_IF_ADDR, mParams->intf_addr_val);
            if (result < 0) {
                ALOGE("%s: Failing in intf addr val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_dw.config_2dot4g_dw_band) {
            result = request.put_u32(NAN_ATTRIBUTE_2G_AWAKE_DW, mParams->config_dw.dw_2dot4g_interval_val);
            if (result < 0) {
                ALOGE("%s: Failing in 2dot4g awake dw, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_dw.config_5g_dw_band) {
            result = request.put_u32(NAN_ATTRIBUTE_5G_AWAKE_DW, mParams->config_dw.dw_5g_interval_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g awake dw, result = %d\n", __func__, result);
                return result;
            }
        }

        if (ISGREATER(mParams->discovery_indication_cfg, NAN_DISC_IND_MAX)) {
            ALOGE("%s:Invalid disc_ind_cfg value.\n", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }

        result = request.put_u8(NAN_ATTRIBUTE_DISC_IND_CFG,
                mParams->discovery_indication_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_DISC_IND_CFG, result = %d\n",
                    __func__, result);
            return result;
        }

        if (mParams->config_rssi_window_size) {
            result = request.put_u8(NAN_ATTRIBUTE_RSSI_WINDOW_SIZE,
                    mParams->rssi_window_size_val);
            if (result < 0) {
                ALOGE("%s: Failing in rssi_window_size_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_scan_params) {
            result = request.put_u8(NAN_ATTRIBUTE_DWELL_TIME,
                    mParams->scan_params_val.dwell_time[0]);
            if (result < 0) {
                ALOGE("%s: Failing in dwell time, result = %d\n", __func__, result);
                return result;
            }
            result = request.put_u8(NAN_ATTRIBUTE_DWELL_TIME_5G,
                    mParams->scan_params_val.dwell_time[1]);
            if (result < 0) {
                ALOGE("%s: Failing in 5g dwell time, result = %d\n", __func__, result);
                return result;
            }
            result = request.put_u16(NAN_ATTRIBUTE_SCAN_PERIOD,
                    mParams->scan_params_val.scan_period[0]);
            if (result < 0) {
                ALOGE("%s: Failing in scan_period, result = %d\n", __func__, result);
                return result;
            }
            result = request.put_u16(NAN_ATTRIBUTE_SCAN_PERIOD_5G,
                    mParams->scan_params_val.scan_period[1]);
            if (result < 0) {
                ALOGE("%s: Failing in 5g scan_period, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_disc_mac_addr_randomization) {
            result = request.put_u32(NAN_ATTRIBUTE_RANDOMIZATION_INTERVAL,
                    mParams->disc_mac_addr_rand_interval_sec);
            if (result < 0) {
                ALOGE("%s: Failing to fill rand mac address interval, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_discovery_beacon_int) {
            result = request.put_u32(NAN_ATTRIBUTE_DISCOVERY_BEACON_INTERVAL,
                    mParams->discovery_beacon_interval);
            if (result < 0) {
                ALOGE("%s: Failing to fill disc beacon interval, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_nss) {
            result = request.put_u32(NAN_ATTRIBUTE_NSS, mParams->nss);
            if (result < 0) {
                ALOGE("%s: Failing to fill nss, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_enable_ranging) {
            result = request.put_u32(NAN_ATTRIBUTE_ENABLE_RANGING, mParams->enable_ranging);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable ranging value, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_dw_early_termination) {
            result = request.put_u32(NAN_ATTRIBUTE_DW_EARLY_TERM, mParams->enable_dw_termination);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable dw termination value, result = %d\n",
                        __func__, result);
                return result;
            }
        }

        if (mParams->config_ndpe_attr) {
            result = request.put_u32(NAN_ATTRIBUTE_CMD_USE_NDPE,
                    mParams->use_ndpe_attr);
            if (result < 0) {
                ALOGE("%s: Failing to fill use_ndpe, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_enable_instant_mode) {
            result = request.put_u32(NAN_ATTRIBUTE_INSTANT_MODE_ENABLE,
                    mParams->enable_instant_mode);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable instant mode, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->enable_instant_mode && mParams->config_instant_mode_channel
            && mParams->instant_mode_channel) {
            result = request.put_u32(NAN_ATTRIBUTE_INSTANT_COMM_CHAN,
                    mParams->instant_mode_channel);
            if (result < 0) {
                ALOGE("%s: Failing in config instant channel, result = %d\n", __func__, result);
                return result;
            }
            ALOGI("%s: instant mode channel = %d\n", __func__, mParams->instant_mode_channel);
        }

        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int createDisableRequest(WifiRequest& request) {
        NAN_DBG_ENTER();

        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_DISABLE);
        if (result < 0) {
            ALOGE("%s: Fail to create request, result = %d\n", __func__, result);
            return result;
        }

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        request.attr_end(data);

        NAN_DBG_EXIT();
        return result;
    }

    int createConfigRequest(WifiRequest& request, NanConfigRequest *mParams) {

        int result = request.create(GOOGLE_OUI, NAN_SUBCMD_CONFIG);
        s8 rssi;
        if (result < 0) {
            ALOGE("%s: Fail to create config request\n", __func__);
            return result;
        }

        NAN_DBG_ENTER();

        nlattr *data = request.attr_start(NL80211_ATTR_VENDOR_DATA);

        if (mParams->config_sid_beacon) {
            result = request.put_u8(NAN_ATTRIBUTE_SID_BEACON, mParams->sid_beacon);
            if (result < 0) {
                ALOGE("%s: Failing in sid beacon, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_subscribe_sid_beacon) {
            result = request.put_u8(NAN_ATTRIBUTE_SUB_SID_BEACON, mParams->subscribe_sid_beacon_val);
            if (result < 0) {
                ALOGE("%s: Failing in sub sid beacon, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_rssi_proximity) {
            if (ISGREATER(mParams->rssi_proximity, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_proximity;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_PROXIMITY, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in rssi_proximity, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_master_pref) {
            ALOGI("%s: master pref = %u\n", __func__, mParams->master_pref);
            result = request.put_u8(NAN_ATTRIBUTE_MASTER_PREF, mParams->master_pref);
            if (result < 0) {
                ALOGE("%s: Failing in master pref, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_5g_rssi_close_proximity) {
            if (ISGREATER(mParams->rssi_close_proximity_5g_val, NAN_MAX_RSSI)) {
                ALOGI("%s: Invalid rssi param \n", __func__);
                return WIFI_ERROR_INVALID_ARGS;
            }
            rssi = -mParams->rssi_close_proximity_5g_val;
            result = request.put_s8(NAN_ATTRIBUTE_RSSI_PROXIMITY_5G, rssi);
            if (result < 0) {
                ALOGE("%s: Failing in rssi_close_proximity_5g_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_rssi_window_size) {
            result = request.put_u8(NAN_ATTRIBUTE_RSSI_WINDOW_SIZE,
                    mParams->rssi_window_size_val);
            if (result < 0) {
                ALOGE("%s: Failing in rssi_window_size_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_scan_params) {
            result = request.put_u8(NAN_ATTRIBUTE_DWELL_TIME,
                    mParams->scan_params_val.dwell_time[0]);
            if (result < 0) {
                ALOGE("%s: Failing in dwell time, result = %d\n", __func__, result);
                return result;
            }

            result = request.put_u8(NAN_ATTRIBUTE_DWELL_TIME_5G,
                    mParams->scan_params_val.dwell_time[1]);
            if (result < 0) {
                ALOGE("%s: Failing in 5g dwell time, result = %d\n", __func__, result);
                return result;
            }
            result = request.put_u16(NAN_ATTRIBUTE_SCAN_PERIOD,
                    mParams->scan_params_val.scan_period[0]);
            if (result < 0) {
                ALOGE("%s: Failing in scan_period, result = %d\n", __func__, result);
                return result;
            }

            result = request.put_u16(NAN_ATTRIBUTE_SCAN_PERIOD_5G,
                    mParams->scan_params_val.scan_period[1]);
            if (result < 0) {
                ALOGE("%s: Failing in 5g scan_period, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_random_factor_force) {
            result = request.put_u8(NAN_ATTRIBUTE_RANDOM_FACTOR, mParams->random_factor_force_val);
            if (result < 0) {
                ALOGE("%s: Failing in random factor, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_hop_count_force) {
            result = request.put_u8(NAN_ATTRIBUTE_HOP_COUNT_LIMIT,
                    mParams->hop_count_force_val);
            if (result < 0) {
                ALOGE("%s: Failing in hop cnt limit, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_cluster_attribute_val) {
            result = request.put_u8(NAN_ATTRIBUTE_CONF_CLUSTER_VAL, mParams->config_cluster_attribute_val);
            if (result < 0) {
                ALOGE("%s: Failing in config_cluster_attribute_val, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_fam) {
            while (mParams->fam_val.numchans) {
                result = request.put_u8(NAN_ATTRIBUTE_ENTRY_CONTROL,
                        mParams->fam_val.famchan[mParams->fam_val.numchans].entry_control);
                if (result < 0) {
                    ALOGE("%s: Failing in entry control, result = %d\n", __func__, result);
                    return result;
                }

                result = request.put_u32(NAN_ATTRIBUTE_CHANNEL,
                        (u32)mParams->fam_val.famchan[mParams->fam_val.numchans].channel);
                if (result < 0) {
                    ALOGE("%s: Failed to fill channel = %d, result = %d\n", __func__,
                            mParams->fam_val.famchan[mParams->fam_val.numchans].channel, result);
                    return result;
                }

                result = request.put_u32(NAN_ATTRIBUTE_AVAIL_BIT_MAP,
                        (u32)mParams->fam_val.famchan[mParams->fam_val.numchans].avail_interval_bitmap);
                if (result < 0) {
                    ALOGE("%s: Failed to fill avail interval bitmap = %d, result = %d\n", __func__,
                            mParams->fam_val.famchan[mParams->fam_val.numchans].avail_interval_bitmap, result);
                    return result;
                }
                mParams->fam_val.numchans -= 1;
            }

        }

        if (mParams->config_dw.config_2dot4g_dw_band) {
            result = request.put_u32(NAN_ATTRIBUTE_2G_AWAKE_DW, mParams->config_dw.dw_2dot4g_interval_val);
            if (result < 0) {
                ALOGE("%s: Failing in 2dot4g awake dw, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_dw.config_5g_dw_band) {
            result = request.put_u32(NAN_ATTRIBUTE_5G_AWAKE_DW, mParams->config_dw.dw_5g_interval_val);
            if (result < 0) {
                ALOGE("%s: Failing in 5g awake dw, result = %d\n", __func__, result);
                return result;
            }
        }
        if (ISGREATER(mParams->discovery_indication_cfg, NAN_DISC_IND_MAX)) {
            ALOGE("%s:Invalid disc_ind_cfg value.\n", __FUNCTION__);
            return WIFI_ERROR_INVALID_ARGS;
        }
        result = request.put_u8(NAN_ATTRIBUTE_DISC_IND_CFG,
                mParams->discovery_indication_cfg);
        if (result < 0) {
            ALOGE("%s: Failed to fill NAN_ATTRIBUTE_DISC_IND_CFG, result = %d\n",
                    __func__, result);
            return result;
        }
        if (mParams->config_disc_mac_addr_randomization) {
            result = request.put_u32(NAN_ATTRIBUTE_RANDOMIZATION_INTERVAL,
                    mParams->disc_mac_addr_rand_interval_sec);
            if (result < 0) {
                ALOGE("%s: Failing in 5g scan_period, result = %d\n", __func__, result);
                return result;
            }
        }
        if (mParams->config_ndpe_attr) {
            result = request.put_u32(NAN_ATTRIBUTE_CMD_USE_NDPE,
                    mParams->use_ndpe_attr);
            if (result < 0) {
                ALOGE("%s: Failing to fill use_ndpe, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_disc_mac_addr_randomization) {
            result = request.put_u32(NAN_ATTRIBUTE_RANDOMIZATION_INTERVAL,
                    mParams->disc_mac_addr_rand_interval_sec);
            if (result < 0) {
                ALOGE("%s: Failing to fill rand mac interval, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_discovery_beacon_int) {
            result = request.put_u32(NAN_ATTRIBUTE_DISCOVERY_BEACON_INTERVAL,
                    mParams->discovery_beacon_interval);
            if (result < 0) {
                ALOGE("%s: Failing to fill disc beacon interval, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_nss) {
            result = request.put_u32(NAN_ATTRIBUTE_NSS, mParams->nss);
            if (result < 0) {
                ALOGE("%s: Failing to fill nss, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_enable_ranging) {
            result = request.put_u32(NAN_ATTRIBUTE_ENABLE_RANGING, mParams->enable_ranging);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable ranging value, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->config_dw_early_termination) {
            result = request.put_u32(NAN_ATTRIBUTE_DW_EARLY_TERM, mParams->enable_dw_termination);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable dw termination value, result = %d\n",
                        __func__, result);
                return result;
            }
        }

        if (mParams->config_enable_instant_mode) {
            result = request.put_u32(NAN_ATTRIBUTE_INSTANT_MODE_ENABLE,
                    mParams->enable_instant_mode);
            if (result < 0) {
                ALOGE("%s: Failing to fill enable instant mode, result = %d\n", __func__, result);
                return result;
            }
        }

        if (mParams->enable_instant_mode && mParams->config_instant_mode_channel
            && mParams->instant_mode_channel) {
            result = request.put_u32(NAN_ATTRIBUTE_INSTANT_COMM_CHAN,
                    mParams->instant_mode_channel);
            if (result < 0) {
                ALOGE("%s: Failing in config instant channel, result = %d\n", __func__, result);
                return result;
            }
            ALOGI("%s: instant mode channel = %d\n", __func__, mParams->instant_mode_channel);
        }

        request.attr_end(data);
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int start()
    {
        NAN_DBG_ENTER();

        WifiRequest request(familyId(), ifaceId());
        int result = createRequest(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to create setup request; result = %d", __func__, result);
            return result;
        }

        result = requestResponse(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to configure setup; result = %d", __func__, result);
            return result;
        }

        request.destroy();
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int cancel()
    {
        NAN_DBG_ENTER();

        WifiRequest request(familyId(), ifaceId());
        int result = createRequest(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to create setup request; result = %d", __func__, result);
            return result;
        }

        result = requestResponse(request);
        if (result != WIFI_SUCCESS) {
            ALOGE("%s: Failed to configure setup; result = %d", __func__, result);
            return result;
        }

        request.destroy();
        NAN_DBG_EXIT();
        return WIFI_SUCCESS;
    }

    int handleResponse(WifiEvent& reply) {
        nan_hal_resp_t *rsp_vndr_data = NULL;

        if (reply.get_cmd() != NL80211_CMD_VENDOR || reply.get_vendor_data() == NULL) {
            ALOGD("Ignoring reply with cmd = %d", reply.get_cmd());
            return NL_SKIP;
        }

        rsp_vndr_data = (nan_hal_resp_t *)reply.get_vendor_data();
        ALOGI("NanMacControl::handleResponse\n");
        if (mType == NAN_VERSION_INFO) {
            mVersion = *((u32*)reply.get_vendor_data());
            ALOGI("Response not required for version cmd %d\n", mVersion);
            return NL_SKIP;
        }
        if (rsp_vndr_data->subcmd == NAN_SUBCMD_CONFIG) {
            NanResponseMsg rsp_data;
            memset(&rsp_data, 0, sizeof(NanResponseMsg));
            rsp_data.response_type = get_response_type((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd);
            rsp_data.status = nan_map_response_status(rsp_vndr_data->status);

            ALOGI("NanMacControl:Received response for cmd [%s], TxID %d ret %d\n",
                    NanRspToString(rsp_data.response_type), id(), rsp_data.status);

            GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(id(), &rsp_data);
        }
        if (rsp_vndr_data->subcmd == NAN_SUBCMD_ENABLE) {
            NanResponseMsg rsp_data;
            memset(&rsp_data, 0, sizeof(NanResponseMsg));
            rsp_data.response_type = get_response_type((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd);
            rsp_data.status = nan_map_response_status(rsp_vndr_data->status);

            ALOGI("NanMacControl:Received response for cmd [%s], TxID %d ret %d\n",
                  NanRspToString(rsp_data.response_type), mId, rsp_data.status);

            if( rsp_data.status != NAN_STATUS_SUCCESS) {
                GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(mId, &rsp_data);
            }
        }
        return NL_SKIP;
    }

    int handleAsyncResponse(nan_hal_resp_t *rsp_vndr_data) {
        NanResponseMsg rsp_data;
        ALOGI("NanMacControl::handleAsyncResponse\n");
        /* Enable response will be provided to framework in event path */
        if (rsp_vndr_data->subcmd == NAN_SUBCMD_ENABLE) {
            return NL_SKIP;
        }
        memset(&rsp_data, 0, sizeof(NanResponseMsg));
        rsp_data.response_type = get_response_type((WIFI_SUB_COMMAND)rsp_vndr_data->subcmd);
        rsp_data.status = nan_map_response_status(rsp_vndr_data->status);
        ALOGE("Mapped hal status = %d\n", rsp_data.status);

        /* populate error string if not coming from DHD */
        if (rsp_vndr_data->nan_reason[0] == '\0') {
            memcpy(rsp_data.nan_error, NanStatusToString(rsp_data.status),
                    strlen(NanStatusToString(rsp_data.status)));
            rsp_data.nan_error[strlen(NanStatusToString(rsp_data.status))] = '\0';
        }
        rsp_data.nan_error[NAN_ERROR_STR_LEN - 1] = '\0';
        ALOGI("\n Received nan_error string %s\n", (u8*)rsp_data.nan_error);
        ALOGI("Retrieved ID = %d\n", mId);

        if ((rsp_vndr_data->subcmd == NAN_SUBCMD_DISABLE) &&
                (mId != NAN_MAC_INVALID_TRANSID)) {
            GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(mId, &rsp_data);
            mId = NAN_MAC_INVALID_TRANSID;
        }
        return NL_SKIP;
    }

    int handleEvent(WifiEvent& event) {
        u32 ndp_instance_id = 0;
        int event_id = event.get_vendor_subcmd();
        nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);
        int len = event.get_vendor_data_len();
        u16 attr_type;
        nan_hal_resp_t *rsp_vndr_data = NULL;

        ALOGI("%s: Received NanMacControl event = %d (len=%d)\n",
                __func__, event.get_cmd(), len);
        if (!vendor_data || len == 0) {
            ALOGE("No event data found");
            return NL_SKIP;
        }

        for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
            attr_type = it.get_type();

            if (it.get_type() == NAN_ATTRIBUTE_HANDLE) {
            } else if (it.get_type() == NAN_ATTRIBUTE_NDP_ID) {
                ndp_instance_id = it.get_u32();
                ALOGI("handleEvent: ndp_instance_id = [%d]\n", ndp_instance_id);
            } else if (attr_type == NAN_ATTRIBUTE_CMD_RESP_DATA) {
                ALOGI("sizeof cmd response data: %ld, it.get_len() = %d\n",
                        sizeof(nan_hal_resp_t), it.get_len());
                if (it.get_len() == sizeof(nan_hal_resp_t)) {
                    rsp_vndr_data = (nan_hal_resp_t*)it.get_data();
                } else {
                    ALOGE("Wrong cmd response data received\n");
                    return NL_SKIP;
                }
            }
        }

        ALOGI("Received vendor sub cmd %d\n", event_id);
        if (is_de_event(event_id)) {

            NanDiscEnginePrimitive *de_prim =
                (NanDiscEnginePrimitive *)(info.nan_disc_control);
            if (de_prim != NULL) {
                de_prim->handleEvent(event);
            } else {
                ALOGE("%s: de_primitive is no more available\n", __func__);
            }
            return NL_SKIP;

        } else if (is_dp_event(event_id)) {

            NanDataPathPrimitive *dp_prim =
                (NanDataPathPrimitive *)(info.nan_dp_control);
            ALOGI("ndp_instance_id = [%d]\n", ndp_instance_id);
            if (dp_prim != NULL) {
                dp_prim->handleEvent(event);
            } else {
                ALOGE("%s: dp_primitive is no more available\n", __func__);
            }
            return NL_SKIP;
	} else {
		if (is_cmd_response(event_id)) {
			ALOGE("Handling cmd response asynchronously\n");
			if (rsp_vndr_data != NULL) {
				handleAsyncResponse(rsp_vndr_data);
			} else {
				ALOGE("Wrong response data, rsp_vndr_data is NULL\n");
				return NL_SKIP;
			}
		}
	}

        switch(event_id) {
            case NAN_EVENT_DE_EVENT:
                NanDiscEngEventInd de_event;
                memset(&de_event, 0, sizeof(de_event));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_CLUSTER_ID) {
                        memcpy(&de_event.data.cluster.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                        ALOGI("cluster id = " MACSTR "\n", MAC2STR(de_event.data.cluster.addr));
                    } else if (attr_type == NAN_ATTRIBUTE_ENABLE_STATUS) {
                        ALOGI("nan enable status = %u\n", it.get_u16());
                    } else if (attr_type == NAN_ATTRIBUTE_JOIN_STATUS) {
                        ALOGI("nan joined status = %u\n", it.get_u16());
                    } else if (attr_type == NAN_ATTRIBUTE_DE_EVENT_TYPE) {
                        u8 de_type = it.get_u8();
                        ALOGI("nan de event type = %u\n", de_type);
                        if (de_type == NAN_EVENT_IFACE) {
                            de_event.event_type = NAN_EVENT_ID_DISC_MAC_ADDR;
                            ALOGI("received NAN_EVENT_ID_DISC_MAC_ADDR event\n");
                        } else if (de_type == NAN_EVENT_START) {
                            de_event.event_type = NAN_EVENT_ID_STARTED_CLUSTER;
                            ALOGI("received NAN cluster started event\n");
                        } else if (de_type == NAN_EVENT_JOIN) {
                            /* To be deprecated */
                            de_event.event_type = NAN_EVENT_ID_JOINED_CLUSTER;
                            ALOGI("received join event\n");
                        } else if (de_type == NAN_EVENT_ROLE_CHANGE) {
                            ALOGI("received device role change event\n");
                        } else if (de_type == NAN_EVENT_MERGE) {
                            ALOGI("received merge event\n");
                        } else {
                            ALOGI("received unknown DE event, [%d]\n", de_type);
                        }
                    } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                        memcpy(&de_event.data.mac_addr.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                        memcpy(mNmi, it.get_data(), NAN_MAC_ADDR_LEN);
                        ALOGI("Primary discovery mac address = " MACSTR "\n",
                                MAC2STR(mNmi));
                    }
                }
                GET_NAN_HANDLE(info)->mHandlers.EventDiscEngEvent(&de_event);
                /* XXX: WAR for sending intf addr to generate Identity
                 * change callback in framework
                 * Also WAR for enable response
                 */
                if (de_event.event_type == NAN_EVENT_ID_STARTED_CLUSTER) {
                    NanResponseMsg rsp_data;
                    memcpy(&de_event.data.mac_addr.addr, mNmi, NAN_MAC_ADDR_LEN);
                    de_event.event_type = NAN_EVENT_ID_DISC_MAC_ADDR;
                    GET_NAN_HANDLE(info)->mHandlers.EventDiscEngEvent(&de_event);
                    rsp_data.response_type = NAN_RESPONSE_ENABLED;
                    rsp_data.status = NAN_STATUS_SUCCESS;
                    memcpy(rsp_data.nan_error, NanStatusToString(rsp_data.status),
                            strlen(NanStatusToString(rsp_data.status)));
                    GET_NAN_HANDLE(info)->mHandlers.NotifyResponse(mId, &rsp_data);
                    /* clean up mId to distinguish duplciated disable command */
                    mId = NAN_MAC_INVALID_TRANSID;
                }
                break;

            case NAN_EVENT_DISABLED:
                ALOGI("Received NAN_EVENT_DISABLED\n");
                NanDisabledInd disabled_ind;
                memset(&disabled_ind, 0, sizeof(NanDisabledInd));
                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();
                    if (attr_type == NAN_ATTRIBUTE_STATUS) {
                        disabled_ind.reason = (NanStatusType)it.get_u8();
                        ALOGI("Nan Disable:status %u", disabled_ind.reason);
                    } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                        u8 len = min(it.get_len(), sizeof(disabled_ind.nan_reason));
                        memcpy(disabled_ind.nan_reason, it.get_data(), len);
                        ALOGI("Disabled nan reason: %s, len = %d\n",
                            disabled_ind.nan_reason, len);
                    }
                }

                GET_NAN_HANDLE(info)->mHandlers.EventDisabled(&disabled_ind);
                /* unregister Nan vendor events */
                unRegisterNanVendorEvents();
                break;

            case NAN_EVENT_SDF:
                ALOGI("Received NAN_EVENT_SDF:\n");
                NanBeaconSdfPayloadInd sdfInd;
                memset(&sdfInd, 0, sizeof(sdfInd));

                for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                    attr_type = it.get_type();

                    if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                        sdfInd.data.frame_len = it.get_u16();
                        if (sdfInd.data.frame_len > NAN_MAX_FRAME_DATA_LEN) {
                            sdfInd.data.frame_len = NAN_MAX_FRAME_DATA_LEN;
                        }
                        ALOGI("Received NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN: 0x%x(%d)\n",
                                sdfInd.data.frame_len, sdfInd.data.frame_len);

                    } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                        ALOGI("Received NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO\n");
                        memcpy(&sdfInd.data.frame_data, it.get_data(), sdfInd.data.frame_len);
                        prhex("sdfInd.data.frame_data: ", (u8*)sdfInd.data.frame_data,
                                sdfInd.data.frame_len);
                    }
                }
                GET_NAN_HANDLE(info)->mHandlers.EventBeaconSdfPayload(&sdfInd);
                break;

            case NAN_EVENT_TCA:
                ALOGI("Received NAN_EVENT_TCA\n");
                break;

            case NAN_EVENT_UNKNOWN:
                ALOGI("Received NAN_EVENT_UNKNOWN\n");
                break;
        } // end-of-switch

        return NL_SKIP;
    }
    void unRegisterNanVendorEvents()
    {
        int i = 0;
        for (i = NAN_EVENT_ENABLED; i <= NAN_EVENT_DATA_END; i++) {
            unregisterVendorHandler(GOOGLE_OUI, i);
        }
        unregisterVendorHandler(GOOGLE_OUI, NAN_ASYNC_RESPONSE_DISABLED);
        unregisterVendorHandler(GOOGLE_OUI, NAN_EVENT_MATCH_EXPIRY);
    }
    void registerNanVendorEvents()
    {
        int i = 0;
        for (i = NAN_EVENT_ENABLED; i <= NAN_EVENT_DATA_END; i++) {
            registerVendorHandler(GOOGLE_OUI, i);
        }
        registerVendorHandler(GOOGLE_OUI, NAN_ASYNC_RESPONSE_DISABLED);
        registerVendorHandler(GOOGLE_OUI, NAN_EVENT_MATCH_EXPIRY);
    }
};

/* pretty hex print a contiguous buffer */
static void prhex(const char *msg, u8 *buf, u32 nbytes)
{
    char line[128];
    char *p;
    int len = sizeof(line);
    int nchar;
    u32 i;

    if (msg && (msg[0] != '\0')) {
        printf("%s:\n", msg);
    }

    p = line;
    for (i = 0; i < nbytes; i++) {
        if (i % 16 == 0) {
            nchar = snprintf(p, len, "  %04d: ", i);    /* line prefix */
            p += nchar;
            len -= nchar;
        }

        if (len > 0) {
            nchar = snprintf(p, len, "%02x ", buf[i]);
            p += nchar;
            len -= nchar;
        }

        if (i % 16 == 15) {
            ALOGE("%s\n", line);       /* flush line */
            p = line;
            len = sizeof(line);
        }
    }

    /* flush last partial line */
    if (p != line) {
        ALOGE("%s\n", line);
    }
}


static const char *NanRspToString(int cmd_resp)
{
    switch (cmd_resp) {
        C2S(NAN_RESPONSE_ENABLED)
            C2S(NAN_RESPONSE_DISABLED)
            C2S(NAN_RESPONSE_PUBLISH)
            C2S(NAN_RESPONSE_SUBSCRIBE)
            C2S(NAN_RESPONSE_PUBLISH_CANCEL)
            C2S(NAN_RESPONSE_SUBSCRIBE_CANCEL)
            C2S(NAN_RESPONSE_TRANSMIT_FOLLOWUP)
            C2S(NAN_RESPONSE_CONFIG)
            C2S(NAN_RESPONSE_TCA)
            C2S(NAN_RESPONSE_STATS)
            C2S(NAN_DP_INTERFACE_CREATE)
            C2S(NAN_DP_INTERFACE_DELETE)
            C2S(NAN_DP_INITIATOR_RESPONSE)
            C2S(NAN_DP_RESPONDER_RESPONSE)
            C2S(NAN_DP_END)
            C2S(NAN_GET_CAPABILITIES)

        default:
            return "UNKNOWN_NAN_CMD_RESPONSE";
    }
}

static const char *NanCmdToString(int cmd)
{
    switch (cmd) {
        C2S(NAN_REQUEST_ENABLE)
            C2S(NAN_REQUEST_DISABLE)
            C2S(NAN_REQUEST_PUBLISH)
            C2S(NAN_REQUEST_PUBLISH_CANCEL)
            C2S(NAN_REQUEST_TRANSMIT_FOLLOWUP)
            C2S(NAN_REQUEST_SUBSCRIBE)
            C2S(NAN_REQUEST_SUBSCRIBE_CANCEL)
            C2S(NAN_REQUEST_STATS)
            C2S(NAN_REQUEST_CONFIG)
            C2S(NAN_REQUEST_TCA)
            C2S(NAN_REQUEST_EVENT_CHECK)
            C2S(NAN_REQUEST_GET_CAPABILITIES)
            C2S(NAN_DATA_PATH_IFACE_CREATE)
            C2S(NAN_DATA_PATH_IFACE_DELETE)
            C2S(NAN_DATA_PATH_INIT_REQUEST)
            C2S(NAN_DATA_PATH_IND_RESPONSE)
            C2S(NAN_DATA_PATH_END)
            C2S(NAN_DATA_PATH_IFACE_UP)
            C2S(NAN_DATA_PATH_SEC_INFO)
            C2S(NAN_VERSION_INFO)
        default:
            return "UNKNOWN_NAN_CMD";
    }
}

static const char *NanAttrToString(u16 cmd)
{
    switch (cmd) {
        C2S(NAN_ATTRIBUTE_HEADER)
            C2S(NAN_ATTRIBUTE_HANDLE)
            C2S(NAN_ATTRIBUTE_TRANSAC_ID)
            C2S(NAN_ATTRIBUTE_5G_SUPPORT)
            C2S(NAN_ATTRIBUTE_CLUSTER_LOW)
            C2S(NAN_ATTRIBUTE_CLUSTER_HIGH)
            C2S(NAN_ATTRIBUTE_SID_BEACON)
            C2S(NAN_ATTRIBUTE_SYNC_DISC_5G_BEACON)
            C2S(NAN_ATTRIBUTE_RSSI_CLOSE)
            C2S(NAN_ATTRIBUTE_RSSI_MIDDLE)
            C2S(NAN_ATTRIBUTE_RSSI_PROXIMITY)
            C2S(NAN_ATTRIBUTE_HOP_COUNT_LIMIT)
            C2S(NAN_ATTRIBUTE_RANDOM_FACTOR)
            C2S(NAN_ATTRIBUTE_MASTER_PREF)
            C2S(NAN_ATTRIBUTE_PERIODIC_SCAN_INTERVAL)
            C2S(NAN_ATTRIBUTE_PUBLISH_ID)
            C2S(NAN_ATTRIBUTE_TTL)
            C2S(NAN_ATTRIBUTE_PERIOD)
            C2S(NAN_ATTRIBUTE_REPLIED_EVENT_FLAG)
            C2S(NAN_ATTRIBUTE_PUBLISH_TYPE)
            C2S(NAN_ATTRIBUTE_TX_TYPE)
            C2S(NAN_ATTRIBUTE_PUBLISH_COUNT)
            C2S(NAN_ATTRIBUTE_SERVICE_NAME_LEN)
            C2S(NAN_ATTRIBUTE_SERVICE_NAME)
            C2S(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN)
            C2S(NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO)
            C2S(NAN_ATTRIBUTE_RX_MATCH_FILTER_LEN)
            C2S(NAN_ATTRIBUTE_RX_MATCH_FILTER)
            C2S(NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN)
            C2S(NAN_ATTRIBUTE_TX_MATCH_FILTER)
            C2S(NAN_ATTRIBUTE_SUBSCRIBE_ID)
            C2S(NAN_ATTRIBUTE_SUBSCRIBE_TYPE)
            C2S(NAN_ATTRIBUTE_SERVICERESPONSEFILTER)
            C2S(NAN_ATTRIBUTE_SERVICERESPONSEINCLUDE)
            C2S(NAN_ATTRIBUTE_USESERVICERESPONSEFILTER)
            C2S(NAN_ATTRIBUTE_SSIREQUIREDFORMATCHINDICATION)
            C2S(NAN_ATTRIBUTE_SUBSCRIBE_MATCH)
            C2S(NAN_ATTRIBUTE_SUBSCRIBE_COUNT)
            C2S(NAN_ATTRIBUTE_MAC_ADDR)
            C2S(NAN_ATTRIBUTE_MAC_ADDR_LIST)
            C2S(NAN_ATTRIBUTE_MAC_ADDR_LIST_NUM_ENTRIES)
            C2S(NAN_ATTRIBUTE_PUBLISH_MATCH)
            C2S(NAN_ATTRIBUTE_ENABLE_STATUS)
            C2S(NAN_ATTRIBUTE_JOIN_STATUS)
            C2S(NAN_ATTRIBUTE_ROLE)
            C2S(NAN_ATTRIBUTE_MASTER_RANK)
            C2S(NAN_ATTRIBUTE_ANCHOR_MASTER_RANK)
            C2S(NAN_ATTRIBUTE_CNT_PEND_TXFRM)
            C2S(NAN_ATTRIBUTE_CNT_BCN_TX)
            C2S(NAN_ATTRIBUTE_CNT_BCN_RX)
            C2S(NAN_ATTRIBUTE_CNT_SVC_DISC_TX)
            C2S(NAN_ATTRIBUTE_CNT_SVC_DISC_RX)
            C2S(NAN_ATTRIBUTE_AMBTT)
            C2S(NAN_ATTRIBUTE_CLUSTER_ID)
            C2S(NAN_ATTRIBUTE_INST_ID)
            C2S(NAN_ATTRIBUTE_OUI)
            C2S(NAN_ATTRIBUTE_STATUS)
            C2S(NAN_ATTRIBUTE_DE_EVENT_TYPE)
            C2S(NAN_ATTRIBUTE_MERGE)
            C2S(NAN_ATTRIBUTE_IFACE)
            C2S(NAN_ATTRIBUTE_CHANNEL)
            C2S(NAN_ATTRIBUTE_PEER_ID)
            C2S(NAN_ATTRIBUTE_NDP_ID)
            C2S(NAN_ATTRIBUTE_SECURITY)
            C2S(NAN_ATTRIBUTE_QOS)
            C2S(NAN_ATTRIBUTE_RSP_CODE)
            C2S(NAN_ATTRIBUTE_INST_COUNT)
            C2S(NAN_ATTRIBUTE_PEER_DISC_MAC_ADDR)
            C2S(NAN_ATTRIBUTE_PEER_NDI_MAC_ADDR)
            C2S(NAN_ATTRIBUTE_IF_ADDR)
            C2S(NAN_ATTRIBUTE_WARMUP_TIME)
            C2S(NAN_ATTRIBUTE_RANGING_RESULT)
            C2S(NAN_ATTRIBUTE_RANGING_INDICATION)
            C2S(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN)
            C2S(NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO)
            C2S(NAN_ATTRIBUTE_RANDOMIZATION_INTERVAL)
            C2S(NAN_ATTRIBUTE_ENABLE_MERGE)

        default:
            return "NAN_ATTRIBUTE_UNKNOWN";
    }
}

NanResponseType get_response_type(WIFI_SUB_COMMAND nan_subcmd)
{
    NanResponseType response_type;

    switch(nan_subcmd) {
        case NAN_SUBCMD_ENABLE:
            response_type = NAN_RESPONSE_ENABLED;
            break;
        case NAN_SUBCMD_DISABLE:
            response_type = NAN_RESPONSE_DISABLED;
            break;
        case NAN_SUBCMD_PUBLISH:
            response_type = NAN_RESPONSE_PUBLISH;
            break;
        case NAN_SUBCMD_SUBSCRIBE:
            response_type = NAN_RESPONSE_SUBSCRIBE;
            break;
        case NAN_SUBCMD_PUBLISH_CANCEL:
            response_type = NAN_RESPONSE_PUBLISH_CANCEL;
            break;
        case NAN_SUBCMD_SUBSCRIBE_CANCEL:
            response_type = NAN_RESPONSE_SUBSCRIBE_CANCEL;
            break;
        case NAN_SUBCMD_TRANSMIT_FOLLOWUP:
            response_type = NAN_RESPONSE_TRANSMIT_FOLLOWUP;
            break;
        case NAN_SUBCMD_CONFIG:
            response_type = NAN_RESPONSE_CONFIG;
            break;
        case NAN_SUBCMD_TCA:
            response_type = NAN_RESPONSE_TCA;
            break;
        case NAN_SUBCMD_STATS:
            response_type = NAN_RESPONSE_STATS;
            break;
        case NAN_SUBCMD_DATA_PATH_IFACE_CREATE:
            response_type = NAN_DP_INTERFACE_CREATE;
            break;
        case NAN_SUBCMD_DATA_PATH_IFACE_DELETE:
            response_type = NAN_DP_INTERFACE_DELETE;
            break;
        case NAN_SUBCMD_DATA_PATH_REQUEST:
            response_type = NAN_DP_INITIATOR_RESPONSE;
            break;
        case NAN_SUBCMD_DATA_PATH_RESPONSE:
            response_type = NAN_DP_RESPONDER_RESPONSE;
            break;
        case NAN_SUBCMD_DATA_PATH_END:
            response_type = NAN_DP_END;
            break;
        case NAN_SUBCMD_GET_CAPABILITIES:
            response_type = NAN_GET_CAPABILITIES;
            break;
        default:
            /* unknown response for a command */
            response_type = NAN_RESPONSE_ERROR;
            break;
    }

    return response_type;
}

static int get_svc_hash(unsigned char *svc_name,
        u16 svc_name_len, u8 *svc_hash, u16 svc_hash_len)
{
    SHA256_CTX sha_ctx;
    u8 sha_hash[SHA256_DIGEST_LENGTH];
    unsigned char *p;
    int len = svc_name_len;

    if (!svc_name || !svc_hash) {
        ALOGE("Bad arguments!!\n");
        return WIFI_ERROR_UNKNOWN;
    }

    if (svc_hash_len < NAN_SVC_HASH_SIZE) {
        ALOGE("Bad len!!\n");
        return WIFI_ERROR_UNKNOWN;
    }
    for (p = svc_name; *p; p++)
    {
        *p = tolower((int)*p);
    }
    SHA256_Init(&sha_ctx);
    SHA256_Update(&sha_ctx, svc_name, len);
    SHA256_Final(sha_hash, &sha_ctx);

    memcpy(svc_hash, sha_hash, NAN_SVC_HASH_SIZE);
    ALOGI("svc_name: %s\n", svc_name);
    prhex("svc_hash:", svc_hash, NAN_SVC_HASH_SIZE);

    return WIFI_SUCCESS;
}

static int dump_NanEnableRequest(NanEnableRequest* msg)
{
    ALOGI("%s: Dump NanEnableRequest msg:\n", __func__);

    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGI("master_pref=%u\n", msg->master_pref);
    ALOGI("cluster_low=%u\n", msg->cluster_low);
    ALOGI("cluster_high=%u\n", msg->cluster_high);
    ALOGI("config_support_5g=%u\n", msg->config_support_5g);
    ALOGI("support_5g_val=%u\n", msg->support_5g_val);
    ALOGI("config_sid_beacon=%u\n", msg->config_sid_beacon);
    ALOGI("sid beacon=%u\n", msg->sid_beacon_val);
    ALOGI("config_sub_sid_beacon=%u\n", msg->config_subscribe_sid_beacon);
    ALOGI("sub sid beacon=%u\n", msg->subscribe_sid_beacon_val);
    ALOGI("config_2dot4g_rssi_close=%u\n", msg->config_2dot4g_rssi_close);
    ALOGI("rssi_close_2dot4g_val=%u\n", msg->rssi_close_2dot4g_val);
    ALOGI("config_2dot4g_rssi_middle=%u\n", msg->config_2dot4g_rssi_middle);
    ALOGI("rssi_middle_2dot4g_val=%u\n", msg->rssi_middle_2dot4g_val);
    ALOGI("config_2dot4g_rssi_proximity=%u\n", msg->config_2dot4g_rssi_proximity);
    ALOGI("rssi_proximity_2dot4g_val=%u\n", msg->rssi_proximity_2dot4g_val);
    ALOGI("config_hop_count_limit=%u\n", msg->config_hop_count_limit);
    ALOGI("hop_count_limit_val=%u\n", msg->hop_count_limit_val);
    ALOGI("config_2dot4g_support=%u\n", msg->config_2dot4g_support);
    ALOGI("support_2dot4g_val=%u\n", msg->support_2dot4g_val);
    ALOGI("config_2dot4g_beacons=%u\n", msg->config_2dot4g_beacons);
    ALOGI("beacon_2dot4g_val=%u\n", msg->beacon_2dot4g_val);
    ALOGI("config_2dot4g_sdf=%u\n", msg->config_2dot4g_sdf);
    ALOGI("sdf_2dot4g_val=%u\n", msg->sdf_2dot4g_val);
    ALOGI("config_5g_beacons=%u\n", msg->config_5g_beacons);
    ALOGI("beacon_5g_val=%u\n", msg->beacon_5g_val);
    ALOGI("config_5g_sdf=%u\n", msg->config_5g_sdf);
    ALOGI("config_5g_rssi_close=%u\n", msg->config_5g_rssi_close);
    ALOGI("rssi_close_5g_val=%u\n", msg->rssi_close_5g_val);
    ALOGI("config_5g_rssi_middle=%u\n", msg->config_5g_rssi_middle);
    ALOGI("rssi_middle_5g_val=%u\n", msg->rssi_middle_5g_val);
    ALOGI("config_5g_rssi_close_proximity=%u\n", msg->config_5g_rssi_close_proximity);
    ALOGI("rssi_close_proximity_5g_val=%u\n", msg->rssi_close_proximity_5g_val);
    ALOGI("config_rssi_window_size=%u\n", msg->config_rssi_window_size);
    ALOGI("rssi_window_size_val=%u\n", msg->rssi_window_size_val);
    ALOGI("config_oui=%u\n", msg->config_oui);
    ALOGI("oui_val=%u\n", msg->oui_val);
    ALOGI("config_intf_addr=%u\n", msg->config_intf_addr);
    ALOGI("intf_addr_val=" MACSTR "\n", MAC2STR(msg->intf_addr_val));
    ALOGI("config_cluster_attribute_val=%u\n", msg->config_cluster_attribute_val);
    ALOGI("config_scan_params=%u\n", msg->config_scan_params);
    if (msg->config_scan_params) {
        ALOGI("dwell_time=%u\n", msg->scan_params_val.dwell_time[0]);
        ALOGI("scan_period=%u\n", msg->scan_params_val.scan_period[0]);
    }
    ALOGI("config_random_factor_force=%u\n", msg->config_random_factor_force);
    ALOGI("random_factor_force_val=%u\n", msg->random_factor_force_val);
    ALOGI("config_hop_count_force=%u\n", msg->config_hop_count_force);
    ALOGI("config_24g_channel=%u\n", msg->config_24g_channel);
    ALOGI("channel_24g_val=%u\n", msg->channel_24g_val);
    ALOGI("config_5g_channel=%u\n", msg->config_5g_channel);
    ALOGI("channel_5g_val=%u\n", msg->channel_5g_val);
    ALOGI("config_dw.config_2dot4g_dw_band=%u\n", msg->config_dw.config_2dot4g_dw_band);
    if (msg->config_dw.config_2dot4g_dw_band) {
        ALOGI("dw_2dot4g_interval_val=%u\n", msg->config_dw.dw_2dot4g_interval_val);
    }
    ALOGI("config_dw.config_5g_dw_band=%u\n", msg->config_dw.config_5g_dw_band);
    if (msg->config_dw.config_5g_dw_band) {
        ALOGI("dw_5g_interval_val=%u\n", msg->config_dw.dw_5g_interval_val);
    }
    ALOGI("discovery_indication_cfg=%u\n", msg->discovery_indication_cfg);
    ALOGI("config_ndpe_attr=%u\n", msg->config_ndpe_attr);
    if (msg->config_ndpe_attr) {
        ALOGI("use_ndpe_attr=%u\n", msg->use_ndpe_attr);
    }
    ALOGI("config_discovery_beacon_int=%u\n", msg->config_discovery_beacon_int);
    if (msg->config_discovery_beacon_int) {
        ALOGI("discovery beacon interval =%u\n", msg->discovery_beacon_interval);
    }
    ALOGI("config_nss=%u\n", msg->config_nss);
    if (msg->config_nss) {
        ALOGI("nss =%u\n", msg->nss);
    }
    ALOGI("config_enable_ranging =%u\n", msg->config_enable_ranging);
    if (msg->config_enable_ranging) {
        ALOGI("enable_ranging =%u\n", msg->enable_ranging);
    }
    ALOGI("config_dw_early_termination =%u\n", msg->config_dw_early_termination);
    if (msg->config_dw_early_termination) {
        ALOGI("enable_dw_termination =%u\n", msg->enable_dw_termination);
    }
    ALOGI("config_disc_mac_addr_randomization=%u\n", msg->config_disc_mac_addr_randomization);
    if (msg->config_disc_mac_addr_randomization) {
        ALOGI("disc_mac_addr_rand_interval_sec =%u\n", msg->disc_mac_addr_rand_interval_sec);
    }
    ALOGI("config_enable_instant_mode =%u\n", msg->config_enable_instant_mode);
    if (msg->config_enable_instant_mode) {
        ALOGI("enable_instant_mode =%u\n", msg->enable_instant_mode);
    }
    ALOGI("config_instant_mode_channel=%u\n", msg->config_instant_mode_channel);
    if (msg->config_instant_mode_channel) {
        ALOGI("instant_mode_channel=%u\n", msg->instant_mode_channel);
    }

    return WIFI_SUCCESS;
}

#ifdef CONFIG_BRCM
static int dump_NanConfigRequestRequest(NanConfigRequest* msg)
{
    ALOGI("%s: Dump NanConfigRequest msg:\n", __func__);

    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGI("master_pref=%u\n", msg->master_pref);
    ALOGI("sid beacon=%u\n", msg->sid_beacon);
    ALOGI("config_sub_sid_beacon=%u\n", msg->config_subscribe_sid_beacon);
    ALOGI("sub sid beacon=%u\n", msg->subscribe_sid_beacon_val);
    ALOGI("rssi_proximity=%u\n", msg->rssi_proximity);
    ALOGI("rssi_close_proximity_5g_val=%u\n", msg->rssi_close_proximity_5g_val);
    ALOGI("rssi_window_size_val=%u\n", msg->rssi_window_size_val);
    ALOGI("scan_params_val.dwell_time[0]=%u\n", msg->scan_params_val.dwell_time[0]);
    ALOGI("scan_params_val.scan_period[0]=%u\n", msg->scan_params_val.scan_period[0]);
    ALOGI("config_scan_params=%u\n", msg->config_scan_params);
    ALOGI("random_factor_force_val=%u\n", msg->random_factor_force_val);
    ALOGI("hop_count_force_val=%u\n", msg->hop_count_force_val);
    ALOGI("fam_val.numchans=%u\n", msg->fam_val.numchans);
    ALOGI("fam_val.famchan[0].entry_control=%u\n", msg->fam_val.famchan[0].entry_control);
    ALOGI("fam_val.famchan[0].class_val=%u\n", msg->fam_val.famchan[0].class_val);
    ALOGI("fam_val.famchan[0].channel=%u\n", msg->fam_val.famchan[0].channel);
    ALOGI("fam_val.famchan[0].mapid=%u\n", msg->fam_val.famchan[0].mapid);
    ALOGI("fam_val.famchan[0].avail_interval_bitmap=%u\n", msg->fam_val.famchan[0].avail_interval_bitmap);
    ALOGI("config_dw.config_2dot4g_dw_band=%u\n", msg->config_dw.config_2dot4g_dw_band);
    if (msg->config_dw.config_2dot4g_dw_band) {
        ALOGI("dw_2dot4g_interval_val=%u\n", msg->config_dw.dw_2dot4g_interval_val);
    }
    ALOGI("config_dw.config_5g_dw_band=%u\n", msg->config_dw.config_5g_dw_band);
    if (msg->config_dw.config_5g_dw_band) {
        ALOGI("dw_5g_interval_val=%u\n", msg->config_dw.dw_5g_interval_val);
    }
    ALOGI("discovery_indication_cfg=%u\n", msg->discovery_indication_cfg);
    ALOGI("config_ndpe_attr=%u\n", msg->config_ndpe_attr);
    if (msg->config_ndpe_attr) {
        ALOGI("use_ndpe_attr=%u\n", msg->use_ndpe_attr);
    }
    ALOGI("config_discovery_beacon_int=%u\n", msg->config_discovery_beacon_int);
    if (msg->config_discovery_beacon_int) {
        ALOGI("discovery beacon interval =%u\n", msg->discovery_beacon_interval);
    }
    ALOGI("config_nss=%u\n", msg->config_nss);
    if (msg->config_nss) {
        ALOGI("nss =%u\n", msg->nss);
    }
    ALOGI("config_enable_ranging =%u\n", msg->config_enable_ranging);
    if (msg->config_enable_ranging) {
        ALOGI("enable_ranging =%u\n", msg->enable_ranging);
    }
    ALOGI("config_dw_early_termination =%u\n", msg->config_dw_early_termination);
    if (msg->config_dw_early_termination) {
        ALOGI("enable_dw_termination =%u\n", msg->enable_dw_termination);
    }

    ALOGI("config_disc_mac_addr_randomization=%u\n", msg->config_disc_mac_addr_randomization);
    if (msg->config_disc_mac_addr_randomization) {
        ALOGI("disc_mac_addr_rand_interval_sec =%u\n", msg->disc_mac_addr_rand_interval_sec);
    }
    ALOGI("config_enable_instant_mode =%u\n", msg->config_enable_instant_mode);
    if (msg->config_enable_instant_mode) {
        ALOGI("enable_instant_mode =%u\n", msg->enable_instant_mode);
    }
    ALOGI("config_instant_mode_channel=%u\n", msg->config_instant_mode_channel);
    if (msg->config_instant_mode_channel) {
        ALOGI("instant_mode_channel=%u\n", msg->instant_mode_channel);
    }

    return WIFI_SUCCESS;
}

static int dump_NanPublishRequest(NanPublishRequest* msg)
{
    ALOGI("%s: Dump NanPublishRequest msg:\n", __func__);
    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }
    ALOGI("publish_id=%u\n", msg->publish_id);
    ALOGI("ttl=%u\n", msg->ttl);
    ALOGI("period=%u\n", msg->period);
    ALOGI("publish_type=%u\n", msg->publish_type);
    ALOGI("tx_type=%u\n", msg->tx_type);
    ALOGI("publish_count=%u\n", msg->publish_count);
    ALOGI("publish_match_indicator=%u\n", msg->publish_match_indicator);
    ALOGI("service_responder_policy=%u\n", msg->service_responder_policy);
    ALOGI("service_name_len=%u\n", msg->service_name_len);
    if (msg->service_name_len) {
        ALOGI("service_name=%s\n", msg->service_name);
    }
    ALOGI("service_specific_info_len=%u\n", msg->service_specific_info_len);
    if (msg->service_specific_info_len) {
        ALOGI("service_specific_info=%s\n", msg->service_specific_info);
    }
    ALOGI("rx_match_filter_len=%u\n", msg->rx_match_filter_len);
    if (msg->rx_match_filter_len) {
        prhex("rx_match_filter", msg->rx_match_filter, msg->rx_match_filter_len);
    }
    ALOGI("tx_match_filter_len=%u\n", msg->tx_match_filter_len);
    if (msg->tx_match_filter_len) {
        prhex("tx_match_filter", msg->tx_match_filter, msg->tx_match_filter_len);
    }
    ALOGI("rssi_threshold_flag=%u\n", msg->rssi_threshold_flag);
    ALOGI("connmap=%u\n", msg->connmap);
    ALOGI("recv_indication_cfg=%u\n", msg->recv_indication_cfg);
    ALOGI("cipher_type=%u\n", msg->cipher_type);
    ALOGI("key_info: key_type =%u\n", msg->key_info.key_type);
    ALOGI("key_info: pmk info=%s\n", msg->key_info.body.pmk_info.pmk);
    ALOGI("key_info: passphrase_info=%s\n", msg->key_info.body.passphrase_info.passphrase);
    ALOGI("scid_len=%u\n", msg->scid_len);
    if (msg->scid_len) {
        ALOGI("scid=%s\n", msg->scid);
    }
    ALOGI("NanSdeaCtrlParams NdpType=%u\n", msg->sdea_params.ndp_type);
    ALOGI("NanSdeaCtrlParams security_cfg=%u\n", msg->sdea_params.security_cfg);
    ALOGI("NanSdeaCtrlParams ranging_state=%u\n", msg->sdea_params.ranging_state);
    ALOGI("NanSdeaCtrlParams range_report=%u\n", msg->sdea_params.range_report);
    ALOGI("NanRangingCfg ranging_interval_msec=%u\n", msg->ranging_cfg.ranging_interval_msec);
    ALOGI("NanRangingCfg config_ranging_indications=%u\n", msg->ranging_cfg.config_ranging_indications);
    ALOGI("NanRangingCfg distance_ingress_mm=%u\n", msg->ranging_cfg.distance_ingress_mm);
    ALOGI("NanRangingCfg distance_egress_mm=%u\n", msg->ranging_cfg.distance_egress_mm);
    ALOGI("NanRangingAutoResponse = %u\n", msg->ranging_auto_response);
    ALOGI("range_response_cfg=%u\n", msg->range_response_cfg.ranging_response);

    ALOGI("sdea_service_specific_info_len=%u\n", msg->sdea_service_specific_info_len);
    if (msg->sdea_service_specific_info_len) {
        ALOGI("sdea_service_specific_info=%s\n", msg->sdea_service_specific_info);
    }

    return WIFI_SUCCESS;
}

static int dump_NanSubscribeRequest(NanSubscribeRequest* msg)
{
    ALOGI("%s: Dump NanSubscribeRequest msg:\n", __func__);
    u8 i = 0;
    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }
    ALOGI("subscribe_id=%u\n", msg->subscribe_id);
    ALOGI("ttl=%u\n", msg->ttl);
    ALOGI("period=%u\n", msg->period);
    ALOGI("subscribe_type=%u\n", msg->subscribe_type);
    ALOGI("serviceResponseFilter=%u\n", msg->serviceResponseFilter);
    ALOGI("serviceResponseInclude=%u\n", msg->serviceResponseInclude);
    ALOGI("useServiceResponseFilter=%u\n", msg->useServiceResponseFilter);
    ALOGI("ssiRequiredForMatchIndication=%u\n", msg->ssiRequiredForMatchIndication);
    ALOGI("subscribe_count=%u\n", msg->subscribe_count);
    ALOGI("subscribe_match_indicator=%u\n", msg->subscribe_match_indicator);
    ALOGI("service_name_len=%u\n", msg->service_name_len);
    if (msg->service_name_len)
        ALOGI("service_name=%s\n", msg->service_name);
    ALOGI("service_specific_info_len=%u\n", msg->service_specific_info_len);
    if (msg->service_specific_info_len)
        ALOGI("service_specific_info=%s\n", msg->service_specific_info);
    ALOGI("rx_match_filter_len=%u\n", msg->rx_match_filter_len);
    if (msg->rx_match_filter_len)
        prhex("rx_match_filter", msg->rx_match_filter, msg->rx_match_filter_len);
    ALOGI("tx_match_filter_len=%u\n", msg->tx_match_filter_len);
    if (msg->tx_match_filter_len)
        prhex("tx_match_filter", msg->tx_match_filter, msg->tx_match_filter_len);
    ALOGI("rssi_threshold_flag=%u\n", msg->rssi_threshold_flag);
    ALOGI("connmap=%u\n", msg->connmap);
    ALOGI("num_intf_addr_present=%u\n", msg->num_intf_addr_present);
    if (msg->num_intf_addr_present) {
        for (i = 0; i < NAN_MAX_SUBSCRIBE_MAX_ADDRESS; i++) {
            ALOGI("peer_disc_mac_addr=" MACSTR "\n", MAC2STR(msg->intf_addr[i]));
        }
    }
    ALOGI("recv_indication_cfg=%u\n", msg->recv_indication_cfg);
    ALOGI("cipher_type=%u\n", msg->cipher_type);
    ALOGI("key_info: key_type =%u\n", msg->key_info.key_type);
    ALOGI("key_info: pmk info=%s\n", msg->key_info.body.pmk_info.pmk);
    ALOGI("key_info: passphrase_info=%s\n", msg->key_info.body.passphrase_info.passphrase);
    ALOGI("scid_len=%u\n", msg->scid_len);
    if (msg->scid_len) {
        ALOGI("scid=%s\n", msg->scid);
    }
    ALOGI("NanSdeaCtrlParams NdpType=%u\n", msg->sdea_params.ndp_type);
    ALOGI("NanSdeaCtrlParams security_cfg=%u\n", msg->sdea_params.security_cfg);
    ALOGI("NanSdeaCtrlParams ranging_state=%u\n", msg->sdea_params.ranging_state);
    ALOGI("NanSdeaCtrlParams range_report=%u\n", msg->sdea_params.range_report);
    ALOGI("NanRangingCfg ranging_interval_msec=%u\n", msg->ranging_cfg.ranging_interval_msec);
    ALOGI("NanRangingCfg config_ranging_indications=%u\n", msg->ranging_cfg.config_ranging_indications);
    ALOGI("NanRangingCfg distance_ingress_mm=%u\n", msg->ranging_cfg.distance_ingress_mm);
    ALOGI("NanRangingCfg distance_egress_mm=%u\n", msg->ranging_cfg.distance_egress_mm);
    ALOGI("NanRangingAutoResponse = %u\n", msg->ranging_auto_response);
    ALOGI("range_response = %u\n", msg->range_response_cfg.ranging_response);

    ALOGI("sdea_service_specific_info_len=%u\n", msg->sdea_service_specific_info_len);
    if (msg->sdea_service_specific_info_len)
        ALOGI("sdea_service_specific_info=%s\n", msg->sdea_service_specific_info);

    return WIFI_SUCCESS;
}

static int dump_NanTransmitFollowupRequest(NanTransmitFollowupRequest* msg)
{
    ALOGI("%s: Dump NanTransmitFollowupRequest msg:\n", __func__);
    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }
    ALOGI("publish_subscribe_id=%u\n", msg->publish_subscribe_id);
    ALOGI("requestor_instance_id=%u\n", msg->requestor_instance_id);
    ALOGI("addr=" MACSTR "\n", MAC2STR(msg->addr));
    ALOGI("priority=%u\n", msg->priority);
    ALOGI("dw_or_faw=%u\n", msg->dw_or_faw);
    ALOGI("service_specific_info_len=%u\n", msg->service_specific_info_len);
    if (msg->service_specific_info_len)
        ALOGI("service_specific_info=%s\n", msg->service_specific_info);
    ALOGI("recv_indication_cfg=%u\n", msg->recv_indication_cfg);
    ALOGI("sdea_service_specific_info_len=%u\n", msg->sdea_service_specific_info_len);
    if (msg->sdea_service_specific_info_len)
        ALOGI("sdea_service_specific_info=%s\n", msg->sdea_service_specific_info);

    return WIFI_SUCCESS;
}

static int dump_NanDataPathInitiatorRequest(NanDataPathInitiatorRequest* msg)
{
    ALOGI("%s: Dump NanDataPathInitiatorRequest msg:\n", __func__);

    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGI("requestor_instance_id=%d\n", msg->requestor_instance_id);
    ALOGI("channel_request_type=%d\n", msg->channel_request_type);
    ALOGI("channel=%u\n", msg->channel);
    ALOGI("peer_disc_mac_addr=" MACSTR "\n", MAC2STR(msg->peer_disc_mac_addr));
    ALOGI("ndp_iface=%s\n", msg->ndp_iface);
    ALOGI("ndp_cfg: security_cfg =%u\n", msg->ndp_cfg.security_cfg);
    ALOGI("ndp_cfg: qos_cfg=%u\n", msg->ndp_cfg.qos_cfg);
    ALOGI("dp app info len=%u\n", msg->app_info.ndp_app_info_len);
    if (msg->app_info.ndp_app_info_len) {
        prhex("dp app info=: ", (u8*)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len);
    }
    ALOGI("cipher_type=%u\n", msg->cipher_type);
    ALOGI("key_info: key_type =%u\n", msg->key_info.key_type);
    ALOGI("key_info: pmk info=%s\n", msg->key_info.body.pmk_info.pmk);
    ALOGI("key_info: passphrase_info=%s\n", msg->key_info.body.passphrase_info.passphrase);
    ALOGI("scid_len=%u\n", msg->scid_len);
    if (msg->scid_len) {
        ALOGI("scid=%s\n", msg->scid);
    }
    if (msg->service_name_len) {
        ALOGI("service_name=%s\n", msg->service_name);
    }
    return WIFI_SUCCESS;
}

static int dump_NanDataPathIndicationResponse(NanDataPathIndicationResponse* msg)
{
    ALOGI("%s: Dump NanDataPathIndicationResponse msg:\n", __func__);

    if (msg == NULL) {
        ALOGE("Invalid msg\n");
        return WIFI_ERROR_UNKNOWN;
    }

    ALOGI("ndp_instance_id=%d\n", msg->ndp_instance_id);
    ALOGI("ndp_iface=%s\n", msg->ndp_iface);
    ALOGI("ndp_cfg: security_cfg =%u\n", msg->ndp_cfg.security_cfg);
    ALOGI("response code =%u\n", msg->rsp_code);
    ALOGI("ndp_cfg: qos_cfg=%u\n", msg->ndp_cfg.qos_cfg);
    ALOGI("dp app info len=%u\n", msg->app_info.ndp_app_info_len);
    if (msg->app_info.ndp_app_info_len) {
        prhex("dp app info=: ", (u8*)msg->app_info.ndp_app_info,
                msg->app_info.ndp_app_info_len);
    }
    ALOGI("cipher_type=%u\n", msg->cipher_type);
    ALOGI("key_info: key_type =%u\n", msg->key_info.key_type);
    ALOGI("key_info: pmk info=%s\n", msg->key_info.body.pmk_info.pmk);
    ALOGI("key_info: passphrase_info=%s\n", msg->key_info.body.passphrase_info.passphrase);
    ALOGI("service_name_len=%u\n", msg->service_name_len);
    ALOGI("scid_len=%u\n", msg->scid_len);
    if (msg->scid_len) {
        ALOGI("scid=%s\n", msg->scid);
    }
    if (msg->service_name_len) {
        ALOGI("service_name=%s\n", msg->service_name);
    }
    return WIFI_SUCCESS;
}
#endif /* CONFIG_BRCM */

void nan_reset_dbg_counters()
{
    memset(&counters, 0, sizeof(counters));
}

///////////////////////////////////////////////////////////////////////////////
wifi_error nan_enable_request(transaction_id id,
        wifi_interface_handle iface, NanEnableRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    wifi_handle handle = getWifiHandle(iface);
    NanRequestType cmdType = NAN_REQUEST_ENABLE;

    ALOGI("Enabling Nan, Handle = %p\n", handle);

#ifdef CONFIG_BRCM
    // check up nan enable params from Nan manager level
    dump_NanEnableRequest(msg);
#endif /* CONFIG_BRCM */
    nan_reset_dbg_counters();
    /* XXX: WAR posting async enable response */
    //NanMacControl *cmd = new NanMacControl(iface, id, (void *)msg, cmdType);
    NanMacControl *cmd = (NanMacControl*)(info.nan_mac_control);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    cmd->setType(cmdType);
    cmd->setId(id);
    cmd->setMsg((void *)msg);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    //cmd->releaseRef();
    return ret;
}

void nan_dump_dbg_counters()
{
    ALOGI("Num Data Path Requests %d\n", counters.dp_req);
    ALOGI("Num Data Path Responses %d\n", counters.dp_resp);
    ALOGI("Num Data Path Confirms %d\n", counters.dp_confirm_evt);
    ALOGI("Num Data Path Request Events %d\n", counters.dp_req_evt);
    ALOGI("Num Transmit Requests %d\n", counters.transmit_req);
    ALOGI("Num Followup Transmits Recvd %d\n", counters.transmit_recv);
    ALOGI("Num Transmit Success %d\n", counters.transmit_txs);
}

wifi_error nan_disable_request(transaction_id id,
        wifi_interface_handle iface)
{
    wifi_handle handle = getWifiHandle(iface);
    NanRequestType cmdType = NAN_REQUEST_DISABLE;
    wifi_error ret = WIFI_SUCCESS;

    ALOGI("Disabling Nan, Handle = %p\n", handle);
    NanMacControl *cmd = new NanMacControl(iface, id, NULL, cmdType);
    NanMacControl *mac_prim = (NanMacControl*)(info.nan_mac_control);

    if (id != NAN_MAC_INVALID_TRANSID) {
        ALOGE("Disable NAN MAC transId= %d\n", id);
        mac_prim->setId(id);
    } else {
        ALOGE("Invalid transId= %d cur= %d\n", id, mac_prim->getId());
    }

    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    nan_dump_dbg_counters();

    ret = (wifi_error)cmd->cancel();
    if (ret != WIFI_SUCCESS) {
        ALOGE("cancel failed, error = %d\n", ret);
    } else {
        ALOGE("Deinitializing Nan Mac Control = %p\n", cmd);
    }
    cmd->releaseRef();
    return ret;
}

wifi_error nan_publish_request(transaction_id id,
        wifi_interface_handle iface, NanPublishRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    wifi_handle handle = getWifiHandle(iface);

    ALOGI("Publish Nan, halHandle = %p\n", handle);
#ifdef CONFIG_BRCM
    dump_NanPublishRequest(msg);
#endif /* CONFIG_BRCM */

    NanRequestType cmdType = NAN_REQUEST_PUBLISH;
    NanDiscEnginePrimitive *cmd = new NanDiscEnginePrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}

/* Function to send NAN request to the wifi driver */
wifi_error nan_publish_cancel_request(transaction_id id,
        wifi_interface_handle iface, NanPublishCancelRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    NanDiscEnginePrimitive *cmd;
    NanRequestType cmdType = NAN_REQUEST_PUBLISH_CANCEL;

    ALOGE("Cancellling publish request %d\n", msg->publish_id);
    cmd = new NanDiscEnginePrimitive(iface, id, (void *)msg, cmdType);
    cmd->setInstId(msg->publish_id);
    cmd->setType(cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}

/* Function to send NAN request to the wifi driver */
wifi_error nan_subscribe_request(transaction_id id,
        wifi_interface_handle iface, NanSubscribeRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    wifi_handle handle = getWifiHandle(iface);
    ALOGI("Subscribe Nan, halHandle = %p handle[%d]\n", handle, msg->subscribe_id);
    NanDiscEnginePrimitive *cmd;
#ifdef CONFIG_BRCM
    dump_NanSubscribeRequest(msg);
#endif /* CONFIG_BRCM */

    NanRequestType cmdType = NAN_REQUEST_SUBSCRIBE;
    cmd = new NanDiscEnginePrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;

}

/*  Function to send NAN request to the wifi driver.*/
wifi_error nan_subscribe_cancel_request(transaction_id id,
        wifi_interface_handle iface, NanSubscribeCancelRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    NanDiscEnginePrimitive *cmd;
    NanRequestType cmdType = NAN_REQUEST_SUBSCRIBE_CANCEL;

    ALOGE("creating new instance + %d\n", msg->subscribe_id);
    cmd = new NanDiscEnginePrimitive(iface, id, (void *)msg, cmdType);
    cmd->setInstId(msg->subscribe_id);
    cmd->setType(cmdType);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();

    return ret;
}

/*  Function to send nan transmit followup Request to the wifi driver.*/
wifi_error nan_transmit_followup_request(transaction_id id,
        wifi_interface_handle iface, NanTransmitFollowupRequest* msg)
{
    NanDiscEnginePrimitive *cmd = NULL;
    NanRequestType cmdType = NAN_REQUEST_TRANSMIT_FOLLOWUP;
    wifi_error ret = WIFI_SUCCESS;

#ifdef CONFIG_BRCM
    dump_NanTransmitFollowupRequest(msg);
#endif /* CONFIG_BRCM */
    counters.transmit_req++;
    cmd = new NanDiscEnginePrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    cmd->setTransactionId(id);

    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}

/* Function to send NAN statistics request to the wifi driver */
wifi_error nan_stats_request(transaction_id id,
        wifi_interface_handle iface, NanStatsRequest* msg)
{
    wifi_handle handle = getWifiHandle(iface);

    ALOGI("Nan Stats, halHandle = %p", handle);

#ifdef NOT_SUPPORTED
    NanRequestType cmdType = NAN_REQUEST_STATS;
    wifi_error ret = WIFI_SUCCESS;
    NanCommand *cmd = new NanCommand(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
#else
    return WIFI_ERROR_NOT_SUPPORTED;
#endif
}

/* Function to send NAN configuration request to the wifi driver */
wifi_error nan_config_request(transaction_id id,
        wifi_interface_handle iface, NanConfigRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    wifi_handle handle = getWifiHandle(iface);
    NanRequestType cmdType = NAN_REQUEST_CONFIG;

    ALOGI("Configuring Nan, halHandle = %p\n", handle);

#ifdef CONFIG_BRCM
    /* check up nan config params from Nan manager level */
    dump_NanConfigRequestRequest(msg);
#endif /* CONFIG_BRCM */

    NanMacControl *cmd = new NanMacControl(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    cmd->setType(cmdType);
    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("start failed, error = %d\n", ret);
    } else {
        ALOGE("Initializing Nan Mac Control = %p\n", cmd);
    }
    cmd->releaseRef();
    return ret;
}

/* Function to send NAN request to the wifi driver */
wifi_error nan_tca_request(transaction_id id,
        wifi_interface_handle iface, NanTCARequest* msg)
{
    wifi_handle handle = getWifiHandle(iface);

    ALOGI("Nan TCA, halHandle = %p", handle);

#ifdef NOT_SUPPORTED
    NanRequestType cmdType = NAN_REQUEST_TCA;
    wifi_error ret = WIFI_SUCCESS;
    NanCommand *cmd = new NanCommand(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
#else
    return WIFI_ERROR_NOT_SUPPORTED;
#endif
}

wifi_error nan_beacon_sdf_payload_request(transaction_id id,
        wifi_interface_handle iface, NanBeaconSdfPayloadRequest* msg)
{
    ALOGI("Nan Beacon Sdf Payload Request");
    return WIFI_ERROR_NOT_SUPPORTED;
}

wifi_error nan_get_capabilities(transaction_id id, wifi_interface_handle iface)
{
    wifi_error ret = WIFI_SUCCESS;
    wifi_handle handle = getWifiHandle(iface);
    ALOGI("Get Nan Capabilities, id=%d, halHandle=%p\n", id, handle);

    NanRequestType cmdType = NAN_REQUEST_GET_CAPABILITIES;
    NanDiscEnginePrimitive *cmd = new NanDiscEnginePrimitive(iface, id, NULL, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in start, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    return ret;
}
wifi_error nan_check_dhd_hal_version(wifi_interface_handle iface,
        wifi_handle handle)
{
    NanRequestType cmdType = NAN_VERSION_INFO;
    NanMacControl *cmd = new NanMacControl(iface, 0, NULL, cmdType);
    wifi_error ret = WIFI_SUCCESS;
    u32 version;

    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    cmd->setType(cmdType);

    ret = (wifi_error)cmd->start();
    if (ret != WIFI_SUCCESS) {
        ALOGI("\nVersion subcmd failed ret = %x\n", ret);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto done;
    }
    version = cmd->getVersion();
    /* check if version handled..can support multiple versions */
    if (version == NAN_HAL_VERSION_1) {
        ALOGI("\nGot the supported version %d\n", version);
        current_dhd_hal_ver = version;
        ret = WIFI_SUCCESS;
        goto done;
    } else {
        ALOGI("\nGot the unsupported version %d\n", version);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto done;
    }
done:
    cmd->releaseRef();
    return ret;
}
wifi_error nan_deinit_handler()
{
    if (info.nan_mac_control) {
        /* register for Nan vendor events with info mac class*/
        NanMacControl *cmd_event = (NanMacControl*)(info.nan_mac_control);
        cmd_event->unRegisterNanVendorEvents();
        delete (NanMacControl*)info.nan_mac_control;
        info.nan_mac_control = NULL;
    }
    if (info.nan_disc_control) {
        delete (NanDiscEnginePrimitive*)info.nan_disc_control;
        info.nan_disc_control = NULL;
    }
    if (info.nan_dp_control) {
        delete (NanDataPathPrimitive*)info.nan_dp_control;
        info.nan_dp_control = NULL;
    }
    if (NAN_HANDLE(info)) {
        delete GET_NAN_HANDLE(info);
        NAN_HANDLE(info) = NULL;
    }
    ALOGI("wifi nan internal clean up done");
    return WIFI_SUCCESS;
}
wifi_error nan_register_handler(wifi_interface_handle iface,
        NanCallbackHandler handlers)
{
    wifi_handle handle = getWifiHandle(iface);
    if (NAN_HANDLE(info)) {
        /* cleanup and re-register */
        nan_deinit_handler();
    }
    ALOGI("\nChecking version compat\n");
    /* checking version compat b/w DHD and HAL */
    if (nan_check_dhd_hal_version(iface, handle) != WIFI_SUCCESS) {
        ALOGE("\n Get version failed..check DHD\n");
        return WIFI_ERROR_NOT_SUPPORTED;
    }
    memset(&info, 0, sizeof(info));
    NAN_HANDLE(info) = new NanHandle(handle, handlers);
    info.nan_mac_control =
        (void*)new NanMacControl(iface, 0, NULL, NAN_REQUEST_LAST);
    NULL_CHECK_RETURN(info.nan_mac_control, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    info.nan_disc_control =
        (void*)new NanDiscEnginePrimitive(iface, 0, NULL, NAN_REQUEST_LAST);
    NULL_CHECK_RETURN(info.nan_disc_control, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    info.nan_dp_control =
        (void*)new NanDataPathPrimitive(iface, 0, NULL, NAN_REQUEST_LAST);
    NULL_CHECK_RETURN(info.nan_dp_control, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    /* register for Nan vendor events with info mac class*/
    NanMacControl *cmd_event = (NanMacControl*)(info.nan_mac_control);
    cmd_event->registerNanVendorEvents();
    return WIFI_SUCCESS;
}

wifi_error nan_get_version(wifi_handle handle, NanVersion* version)
{
    wifi_error ret = WIFI_SUCCESS;
    if (version) {
        *version = (NAN_MAJOR_REL_VERSION << 16 | NAN_MINOR_REL_VERSION << 8 |
                NAN_PATCH_REL_VERSION);
    } else {
        ret = WIFI_ERROR_INVALID_ARGS;
    }

    return ret;
}


///////////////////////////////////////////////////////////////////////////////
class NanEventCap : public WifiCommand
{
    public:
        NanEventCap(wifi_interface_handle iface, int id)
            : WifiCommand("NanCommand", iface, id)
        {}

        int start()
        {
            registerNanVendorEvents();
            return WIFI_SUCCESS;
        }

        int handleResponse(WifiEvent& reply) {
            return NL_SKIP;
        }
        void unRegisterNanVendorEvents()
        {
            int i = 0;
            for (i = NAN_EVENT_ENABLED; i <= NAN_EVENT_DATA_END; i++) {
                unregisterVendorHandler(GOOGLE_OUI, i);
            }
            unregisterVendorHandler(GOOGLE_OUI, NAN_ASYNC_RESPONSE_DISABLED);
            unregisterVendorHandler(GOOGLE_OUI, NAN_EVENT_MATCH_EXPIRY);
        }
        void registerNanVendorEvents()
        {
            int i = 0;
            for (i = NAN_EVENT_ENABLED; i <= NAN_EVENT_DATA_END; i++) {
                registerVendorHandler(GOOGLE_OUI, i);
            }
            registerVendorHandler(GOOGLE_OUI, NAN_ASYNC_RESPONSE_DISABLED);
            registerVendorHandler(GOOGLE_OUI, NAN_EVENT_MATCH_EXPIRY);
        }

        int handleEvent(WifiEvent& event) {
            int cmd = event.get_vendor_subcmd();
            u16 attr_type;
            nlattr *vendor_data = event.get_attribute(NL80211_ATTR_VENDOR_DATA);

            switch(cmd) {
                case NAN_EVENT_DE_EVENT: {
                    u16 attr_type;
                    NanDiscEngEventInd de_event;
                    memset(&de_event, 0, sizeof(NanDiscEngEventInd));

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();
                        if (attr_type == NAN_ATTRIBUTE_CLUSTER_ID) {
                            memcpy(&de_event.data.cluster.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("cluster id = " MACSTR "\n", MAC2STR(de_event.data.cluster.addr));
                        } else if (attr_type == NAN_ATTRIBUTE_ENABLE_STATUS) {
                            ALOGI("nan enable status = %u\n", it.get_u16());
                        } else if (attr_type == NAN_ATTRIBUTE_JOIN_STATUS) {
                            ALOGI("nan joined status = %u\n", it.get_u16());
                        } else if (attr_type == NAN_ATTRIBUTE_DE_EVENT_TYPE) {
                            u8 de_type = it.get_u8();
                            ALOGI("nan de event type = %u\n", de_type);
                            if (de_type == NAN_EVENT_IFACE) {
                                de_event.event_type = NAN_EVENT_ID_DISC_MAC_ADDR;
                                ALOGI("received NAN_EVENT_ID_DISC_MAC_ADDR event\n");
                            } else if (de_type == NAN_EVENT_START) {
                                de_event.event_type = NAN_EVENT_ID_STARTED_CLUSTER;
                                ALOGI("received NAN cluster started event\n");
                            } else if (de_type == NAN_EVENT_JOIN) {
                                /* To be deprecated */
                                de_event.event_type = NAN_EVENT_ID_JOINED_CLUSTER;
                                ALOGI("received join event\n");
                            } else if (de_type == NAN_EVENT_ROLE_CHANGE) {
                                ALOGI("received device role change event\n");
                            } else if (de_type == NAN_EVENT_MERGE) {
                                ALOGI("received Merge Event\n");
                            } else {
                                ALOGI("received unknown DE event, [%d]\n", de_type);
                            }
                        } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                            memcpy(&de_event.data.cluster.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                            memcpy(mNmi, it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("Primary discovery mac address = " MACSTR "\n",
                                    MAC2STR(mNmi));
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventDiscEngEvent(&de_event);
                    break;
                }
                case NAN_EVENT_DISABLED: {
                    ALOGI("Received NAN_EVENT_DISABLED\n");
                    NanDisabledInd disabled_ind;
                    memset(&disabled_ind, 0, sizeof(NanDisabledInd));
                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();
                        if (attr_type == NAN_ATTRIBUTE_STATUS) {
                            disabled_ind.reason = (NanStatusType)it.get_u8();
                            ALOGI("Nan Disable:status %u", disabled_ind.reason);
                        } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                            u8 len = min(it.get_len(), sizeof(disabled_ind.nan_reason));
                            memcpy(disabled_ind.nan_reason, it.get_data(), len);
                            ALOGI("nan disabled reason: %s, len = %d\n",
                                disabled_ind.nan_reason, len);
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventDisabled(&disabled_ind);
                    unRegisterNanVendorEvents();
                    break;
                }
                case NAN_EVENT_PUBLISH_TERMINATED: {
                    ALOGI("Received NAN_EVENT_PUBLISH_TERMINATED\n");
                    NanPublishTerminatedInd pub_term_event;
                    memset(&pub_term_event, 0, sizeof(NanPublishTerminatedInd));

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                            pub_term_event.publish_id = it.get_u32();
                            ALOGI("pub id %u", pub_term_event.publish_id);
                        } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                            pub_term_event.reason = (NanStatusType)it.get_u8();
                            ALOGI("pub termination status %u", pub_term_event.reason);
                        } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                            u8 len = min(it.get_len(), sizeof(pub_term_event.nan_reason));
                            memcpy(pub_term_event.nan_reason, it.get_data(), len);
                            ALOGI("Pub termination nan reason: %s, len = %d\n",
                                pub_term_event.nan_reason, len);
                        } else {
                            ALOGE("Unknown attr\n");
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventPublishTerminated(&pub_term_event);
                    break;
                }
                case NAN_EVENT_SUBSCRIBE_MATCH: {
                    NanMatchInd subscribe_event;
                    memset(&subscribe_event, 0, sizeof(NanMatchInd));
                    ALOGI("Received NAN_EVENT_SUBSCRIBE_MATCH\n");

                    /* By default FW is unable to cache this match */
                    subscribe_event.out_of_resource_flag = true;

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                            ALOGI("sub id: %u", it.get_u16());
                            subscribe_event.publish_subscribe_id = it.get_u16();

                        } else if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                            ALOGI("pub id %u", it.get_u32());
                            subscribe_event.requestor_instance_id = it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                            memcpy(subscribe_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("publisher mac: " MACSTR, MAC2STR(subscribe_event.addr));

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                            ALOGI("svc length: %d", it.get_u16());
                            subscribe_event.service_specific_info_len = it.get_u16();

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                            memcpy(subscribe_event.service_specific_info, it.get_data(),
                                    subscribe_event.service_specific_info_len);
                            subscribe_event.service_specific_info
                                [subscribe_event.service_specific_info_len] = '\0';
                            ALOGI("service info: %s", subscribe_event.service_specific_info);

                        } else if (attr_type == NAN_ATTRIBUTE_TX_MATCH_FILTER_LEN) {
                            ALOGI("sdf match filter length: %d", subscribe_event.sdf_match_filter_len);
                            subscribe_event.sdf_match_filter_len = it.get_u16();

                        } else if (attr_type == NAN_ATTRIBUTE_TX_MATCH_FILTER) {
                            memcpy(subscribe_event.sdf_match_filter, it.get_data(),
                                    subscribe_event.sdf_match_filter_len);
                            subscribe_event.sdf_match_filter
                                [subscribe_event.sdf_match_filter_len] = '\0';
                            ALOGI("sdf match filter: %s", subscribe_event.sdf_match_filter);
                        } else if (attr_type == NAN_ATTRIBUTE_CIPHER_SUITE_TYPE) {
                            ALOGI("Peer Cipher suite type: %u", it.get_u8());
                            subscribe_event.peer_cipher_type = it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_SCID_LEN) {
                            ALOGI("scid length %d", it.get_u32());
                            subscribe_event.scid_len= it.get_u32();
                        } else if (attr_type == NAN_ATTRIBUTE_SCID) {
                            memcpy(subscribe_event.scid, it.get_data(),
                                    subscribe_event.scid_len);
                            subscribe_event.scid
                                [subscribe_event.scid_len] = '\0';
                            ALOGI("scid: %s", subscribe_event.scid);
                        } else if (attr_type == NAN_ATTRIBUTE_RANGING_INDICATION) {
                            subscribe_event.range_info.ranging_event_type = it.get_u32();
                            ALOGI("ranging indication %d", it.get_u32());
                        } else if (attr_type == NAN_ATTRIBUTE_RANGING_RESULT) {
                            subscribe_event.range_info.range_measurement_mm = it.get_u32();
                            ALOGI("ranging result %d", it.get_u32());
                        } else if (attr_type == NAN_ATTRIBUTE_RSSI_PROXIMITY) {
                            subscribe_event.rssi_value = it.get_u8();
                            ALOGI("rssi value : %u", it.get_u8());
                        } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                            ALOGI("sdea svc length %d", it.get_u16());
                            subscribe_event.sdea_service_specific_info_len = it.get_u16();
                        } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO) {
                            memcpy(subscribe_event.sdea_service_specific_info, it.get_data(),
                                    subscribe_event.sdea_service_specific_info_len);
                            subscribe_event.sdea_service_specific_info
                                [subscribe_event.sdea_service_specific_info_len] = '\0';
                            ALOGI("sdea service info: %s", subscribe_event.sdea_service_specific_info);
                        } else if (attr_type == NAN_ATTRIBUTE_MATCH_OCCURRED_FLAG) {
                            ALOGI("match occurred flag: %u", it.get_u8());
                            subscribe_event.match_occured_flag = it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_OUT_OF_RESOURCE_FLAG) {
                            ALOGI("Out of resource flag: %u", it.get_u8());
                            subscribe_event.out_of_resource_flag = it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_CONFIG_DP) {
                            ALOGI("Peer config for data path needed: %u", it.get_u8());
                            subscribe_event.peer_sdea_params.config_nan_data_path = it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_DP_TYPE) {
                            ALOGI("Data Path type: %u", it.get_u8());
                            subscribe_event.peer_sdea_params.ndp_type = (NdpType)it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_SECURITY) {
                            ALOGI("Security configuration: %u", it.get_u8());
                            subscribe_event.peer_sdea_params.security_cfg =
                                (NanDataPathSecurityCfgStatus)it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_SDE_CONTROL_RANGE_SUPPORT) {
                            ALOGI("Ranging report state: %u", it.get_u8());
                            subscribe_event.peer_sdea_params.range_report = (NanRangeReport)it.get_u8();
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventMatch(&subscribe_event);
                    break;
                }
                case NAN_EVENT_SUBSCRIBE_UNMATCH: {
                    ALOGI("Received NAN_EVENT_SUBSCRIBE_UNMATCH\n");
                    ALOGE("%s: Not applicable yet\n", __func__);
                    break;
                }
                case NAN_EVENT_SUBSCRIBE_TERMINATED: {
                    NanSubscribeTerminatedInd sub_term_event;
                    memset(&sub_term_event, 0, sizeof(NanSubscribeTerminatedInd));
                    ALOGI("Received NAN_EVENT_SUBSCRIBE_TERMINATED\n");

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                            sub_term_event.subscribe_id = it.get_u16();
                            ALOGI("sub id: %u", sub_term_event.subscribe_id);
                        } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                            sub_term_event.reason = (NanStatusType)it.get_u8();
                            ALOGI("sub termination status %u", sub_term_event.reason);
                        } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                            u8 len = min(it.get_len(), sizeof(sub_term_event.nan_reason));
                            memcpy(sub_term_event.nan_reason, it.get_data(), len);
                            ALOGI("sub termination nan reason: %s, len = %d\n",
                                sub_term_event.nan_reason, len);
                        } else {
                            ALOGE("Unknown attr: %u\n", attr_type);
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventSubscribeTerminated(&sub_term_event);
                    break;
                }
                case NAN_EVENT_MATCH_EXPIRY:
                    HandleExpiryEvent(info, vendor_data);
                    break;
                case NAN_EVENT_FOLLOWUP: {
                    NanFollowupInd followup_event;
                    memset(&followup_event, 0, sizeof(NanFollowupInd));
                    ALOGI("Received NAN_EVENT_FOLLOWUP\n");

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                            memcpy(followup_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);

                        } else if (attr_type == NAN_ATTRIBUTE_PEER_ID) {
                            followup_event.publish_subscribe_id = it.get_u16();

                        } else if (attr_type == NAN_ATTRIBUTE_INST_ID) {
                            followup_event.requestor_instance_id = it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                            followup_event.service_specific_info_len = it.get_u16();

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                            memcpy(followup_event.service_specific_info, it.get_data(),
                                    followup_event.service_specific_info_len);
                            followup_event.service_specific_info[followup_event.service_specific_info_len] =
                                '\0';
                        } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                            ALOGI("sdea svc length %d", it.get_u16());
                            followup_event.sdea_service_specific_info_len = it.get_u16();
                        } else if (attr_type == NAN_ATTRIBUTE_SDEA_SERVICE_SPECIFIC_INFO) {
                            memcpy(followup_event.sdea_service_specific_info, it.get_data(),
                                    followup_event.sdea_service_specific_info_len);
                            followup_event.sdea_service_specific_info[followup_event.sdea_service_specific_info_len] = '\0';
                            ALOGI("sdea service info: %s", followup_event.sdea_service_specific_info);
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventFollowup(&followup_event);
                    break;
                }
                case NAN_EVENT_SDF: {
                    ALOGI("Received NAN_EVENT_SDF:\n");
                    NanBeaconSdfPayloadInd sdfInd;
                    memset(&sdfInd, 0, sizeof(sdfInd));

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                            sdfInd.data.frame_len = it.get_u16();
                            if (sdfInd.data.frame_len > NAN_MAX_FRAME_DATA_LEN) {
                                sdfInd.data.frame_len = NAN_MAX_FRAME_DATA_LEN;
                            }
                            ALOGI("Received NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN: 0x%x(%d)\n",
                                    sdfInd.data.frame_len, sdfInd.data.frame_len);

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                            ALOGI("Received NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO\n");
                            memcpy(&sdfInd.data.frame_data, it.get_data(), sdfInd.data.frame_len);
                            prhex("sdfInd.data.frame_data: ", (u8*)sdfInd.data.frame_data,
                                    sdfInd.data.frame_len);
                        }
                    }
                    GET_NAN_HANDLE(info)->mHandlers.EventBeaconSdfPayload(&sdfInd);
                    break;
                }
#ifdef NOT_YET
                case NAN_EVENT_PUBLISH_REPLIED_IND: {
                    ALOGI("Received NAN_EVENT_PUBLISH_REPLIED_IND\n");
                    NanPublishRepliedInd pub_reply_event;
                    memset(&pub_reply_event, 0, sizeof(pub_reply_event));

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_SUBSCRIBE_ID) {
                            ALOGI("sub id: %u", it.get_u32());
                            pub_reply_event.requestor_instance_id = it.get_u32();
                        } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                            memcpy(pub_reply_event.addr, it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("Subscriber mac: " MACSTR, MAC2STR(pub_reply_event.addr));
                        } else if (attr_type == NAN_ATTRIBUTE_RSSI_PROXIMITY) {
                            pub_reply_event.rssi_value = it.get_u8();
                            ALOGI("Received rssi value : %u", it.get_u8());
                        }
                    }
                    GET_NAN_HANDLE(info)->mHandlers.EventPublishReplied(&pub_reply_event);
                    break;
                }
#endif /* NOT_YET */
                case NAN_EVENT_TCA: {
                    ALOGI("Received NAN_EVENT_TCA\n");
                    //GET_NAN_HANDLE(info)->mHandlers.EventTca(&sdfPayload);
                    break;
                }
                case NAN_EVENT_DATA_REQUEST: {
                    ALOGI("Received NAN_EVENT_DATA_REQUEST_INDICATION\n");
                    NanDataPathRequestInd ndp_request_event;
                    memset(&ndp_request_event, 0, sizeof(NanDataPathRequestInd));
                    u16 ndp_ind_app_info_len = 0;

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_PUBLISH_ID) {
                            ALOGI("publish_id: %u\n", it.get_u32());
                            ndp_request_event.service_instance_id = it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_MAC_ADDR) {
                            memcpy(ndp_request_event.peer_disc_mac_addr,
                                    it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("Discovery MAC addr of the peer/initiator: " MACSTR "\n",
                                    MAC2STR(ndp_request_event.peer_disc_mac_addr));

                        } else if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                            ALOGI("ndp id: %u\n", it.get_u32());
                            ndp_request_event.ndp_instance_id = it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_SECURITY) {
                            ALOGI("security: %u\n", (NanDataPathSecurityCfgStatus) it.get_u8());
                            ndp_request_event.ndp_cfg.security_cfg =
                                (NanDataPathSecurityCfgStatus)it.get_u8();

                        } else if (attr_type == NAN_ATTRIBUTE_QOS) {
                            ALOGI("QoS: %u", (NanDataPathQosCfg)it.get_u8());
                            ndp_request_event.ndp_cfg.qos_cfg = (NanDataPathQosCfg)it.get_u8();

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                            ALOGI("service info len: %d\n", it.get_u16());
                            ndp_ind_app_info_len = it.get_u16();
                            ndp_request_event.app_info.ndp_app_info_len = ndp_ind_app_info_len;

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                            memcpy(ndp_request_event.app_info.ndp_app_info,
                                    it.get_data(), ndp_ind_app_info_len);
                            ndp_request_event.app_info.ndp_app_info[ndp_ind_app_info_len] = '\0';
                            ALOGI("service info: %s\n", ndp_request_event.app_info.ndp_app_info);

                        } else if (attr_type == NAN_ATTRIBUTE_SCID_LEN) {
                            ALOGI("scid length %d\n", it.get_u32());
                            ndp_request_event.scid_len= it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_SCID) {
                            memcpy(ndp_request_event.scid, it.get_data(),
                                ndp_request_event.scid_len);
                            ndp_request_event.scid[ndp_request_event.scid_len] = '\0';
                            ALOGI("scid: %s\n", ndp_request_event.scid);
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventDataRequest(&ndp_request_event);
                    break;
                }
                case NAN_EVENT_DATA_CONFIRMATION: {
                    ALOGI("Received NAN_EVENT_DATA_CONFIRMATION\n");
                    NanDataPathConfirmInd ndp_create_confirmation_event;
                    memset(&ndp_create_confirmation_event, 0, sizeof(NanDataPathConfirmInd));
                    u16 ndp_conf_app_info_len = 0;

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();

                        if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                            ALOGI("ndp id: %u", it.get_u32());
                            ndp_create_confirmation_event.ndp_instance_id = it.get_u32();

                        } else if (attr_type == NAN_ATTRIBUTE_PEER_NDI_MAC_ADDR) {
                            memcpy(ndp_create_confirmation_event.peer_ndi_mac_addr,
                                    it.get_data(), NAN_MAC_ADDR_LEN);
                            ALOGI("NDI mac address of the peer: " MACSTR "\n",
                                    MAC2STR(ndp_create_confirmation_event.peer_ndi_mac_addr));

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO_LEN) {
                            ALOGI("service info string len: %d\n", it.get_u16());
                            ndp_conf_app_info_len = it.get_u16();
                            ndp_create_confirmation_event.app_info.ndp_app_info_len =
                                ndp_conf_app_info_len;

                        } else if (attr_type == NAN_ATTRIBUTE_SERVICE_SPECIFIC_INFO) {
                            memcpy(ndp_create_confirmation_event.app_info.ndp_app_info, it.get_data(),
                                    ndp_conf_app_info_len);
                            ndp_create_confirmation_event.app_info.ndp_app_info[ndp_conf_app_info_len] =
                                '\0';
                            ALOGI("service info string: %s\n",
                                    ndp_create_confirmation_event.app_info.ndp_app_info);

                        } else if (attr_type == NAN_ATTRIBUTE_RSP_CODE) {
                            ALOGI("response code %u\n", (NanDataPathResponseCode) it.get_u8());
                            ndp_create_confirmation_event.rsp_code =
                                (NanDataPathResponseCode)it.get_u8();
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventDataConfirm(&ndp_create_confirmation_event);
                    break;
                }
                case NAN_EVENT_DATA_END: {
                    ALOGI("Received NAN_EVENT_DATA_END\n");
                    NanDataPathEndInd ndp_end_event;
                    memset(&ndp_end_event, 0, sizeof(NanDataPathEndInd));
                    u8 count = 0;

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();
                        if (attr_type == NAN_ATTRIBUTE_INST_COUNT) {
                            ALOGI("ndp count: %u\n", it.get_u8());
                            ndp_end_event.num_ndp_instances = it.get_u8();
                            count = it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_NDP_ID) {
                            ALOGI("count: %u\n", count);
                            while (count) {
                                ndp_end_event.ndp_instance_id[count-1] = it.get_u32();
                                ALOGI("ndp id: %u\n", ndp_end_event.ndp_instance_id[count-1]);
                                count -= 1;
                            }
                        } else {
                            ALOGI("Unknown attr_type: %s\n", NanAttrToString(attr_type));
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventDataEnd(&ndp_end_event);
                    break;
                }
                case NAN_EVENT_TRANSMIT_FOLLOWUP_IND: {
                    ALOGI("Received NAN_EVENT_TRANSMIT_FOLLOWUP_IND\n");
                    NanTransmitFollowupInd followup_ind;
                    memset(&followup_ind, 0, sizeof(NanTransmitFollowupInd));

                    for (nl_iterator it(vendor_data); it.has_next(); it.next()) {
                        attr_type = it.get_type();
                        if (attr_type == NAN_ATTRIBUTE_TRANSAC_ID) {
                            followup_ind.id = it.get_u16();
                        } else if (attr_type == NAN_ATTRIBUTE_STATUS) {
                            followup_ind.reason = (NanStatusType)it.get_u8();
                        } else if (attr_type == NAN_ATTRIBUTE_REASON) {
                            u8 len = min(it.get_len(), sizeof(followup_ind.nan_reason));
                            memcpy(followup_ind.nan_reason, it.get_data(), len);
                            ALOGI("nan transmit followup ind: reason: %s, len = %d\n",
                               followup_ind.nan_reason, len);
                        }
                    }

                    GET_NAN_HANDLE(info)->mHandlers.EventTransmitFollowup(&followup_ind);
                    break;
                }
                case NAN_EVENT_UNKNOWN:
                    ALOGI("Received NAN_EVENT_UNKNOWN\n");
                    break;
            } // end-of-switch
            return NL_SKIP;
        }
};

/* To see event prints in console */
wifi_error nan_event_check_request(transaction_id id, wifi_interface_handle iface)
{
    NanEventCap *cmd = new NanEventCap(iface, id);
    if (cmd == NULL) {
        return WIFI_ERROR_NOT_SUPPORTED;
    }
    return (wifi_error)cmd->start();
}

/* Create NAN Data Interface */
wifi_error nan_data_interface_create(transaction_id id,
        wifi_interface_handle iface, char* iface_name)
{
    wifi_error ret = WIFI_SUCCESS;
    NAN_DBG_ENTER();

    NanRequestType cmdType = NAN_DATA_PATH_IFACE_CREATE;
    NanDataPathPrimitive *cmd =
        new NanDataPathPrimitive(iface, id, (void *)iface_name, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();

    NAN_DBG_EXIT();
    return ret;
}

/* Delete NAN Data Interface */
wifi_error nan_data_interface_delete(transaction_id id,
        wifi_interface_handle iface, char* iface_name)
{
    wifi_error ret = WIFI_SUCCESS;
    NAN_DBG_ENTER();

    NanRequestType cmdType = NAN_DATA_PATH_IFACE_DELETE;
    NanDataPathPrimitive *cmd =
        new NanDataPathPrimitive(iface, id, (void *)iface_name, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();

    NAN_DBG_EXIT();
    return ret;
}

/* Initiate a NDP session: Initiator */
wifi_error nan_data_request_initiator(transaction_id id,
        wifi_interface_handle iface, NanDataPathInitiatorRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;

    NAN_DBG_ENTER();
    NanRequestType cmdType;
    NanDataPathPrimitive *cmd = NULL;

#ifdef CONFIG_BRCM
    dump_NanDataPathInitiatorRequest(msg);
#endif /* CONFIG_BRCM */
    counters.dp_req++;
    if (msg->service_name_len) {
        if (strncmp(NAN_OOB_INTEROP_SVC_NAME,
                    (char*)msg->service_name, msg->service_name_len) == 0) {
            ALOGI("Use Hardcoded svc_hash\n");
            msg->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(msg->service_name, NAN_OOB_INTEROP_SVC_HASH, NAN_SVC_HASH_SIZE);
        } else {
            u8 svc_hash[NAN_SVC_HASH_SIZE];

            ret = (wifi_error)get_svc_hash(msg->service_name, msg->service_name_len,
                    svc_hash, NAN_SVC_HASH_SIZE);
            if (ret < 0) {
                ALOGE("%s: Failed to get hashed svc name\n", __func__);
                goto done;
            }

            ALOGI("Created svc_hash\n");
            msg->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(msg->service_name, svc_hash, msg->service_name_len);
        }
    } else if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
        NanDataPathSecInfoRequest msg_sec_info;
        if (msg->requestor_instance_id == 0) {
            ALOGE("Invalid Pub ID = %d, Mandatory param is missing\n", msg->requestor_instance_id);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto done;
        } else {
            ALOGI("Pub ID = %d, Mandatory param is present\n", msg->requestor_instance_id);
        }
        if (ETHER_ISNULLADDR(msg->peer_disc_mac_addr)) {
            ALOGE("Invalid Pub NMI, Mandatory param is missing\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto done;
        }

        msg_sec_info.requestor_instance_id = msg->requestor_instance_id;
        memcpy(msg_sec_info.peer_disc_mac_addr, msg->peer_disc_mac_addr, NAN_MAC_ADDR_LEN);
        msg_sec_info.ndp_instance_id = 0;
        cmdType = NAN_DATA_PATH_SEC_INFO;
        cmd = new NanDataPathPrimitive(iface, id, (void *)&msg_sec_info, cmdType);
        NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
        ret = (wifi_error)cmd->open();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s : failed in start, error = %d\n", __func__, ret);
            goto done;
        }
        memcpy(msg->service_name, cmd->mSvcHash, NAN_SVC_HASH_SIZE);
    }
    /* free old command */
    if (cmd) {
        cmd->releaseRef();
    }
    cmdType = NAN_DATA_PATH_INIT_REQUEST;
    cmd = new NanDataPathPrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
        goto done;
    }
done:
    if (cmd) {
        cmd->releaseRef();
    }

    NAN_DBG_EXIT();
    return ret;
}

/* Response to a data indication received corresponding to a NDP session.
 * An indication is received with a data request and the responder will send a data response
 */
wifi_error nan_data_indication_response(transaction_id id,
        wifi_interface_handle iface, NanDataPathIndicationResponse* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    NAN_DBG_ENTER();
    NanRequestType cmdType;
    u8 pub_nmi[NAN_MAC_ADDR_LEN] = {0};
    NanDataPathPrimitive *cmd = NULL;

#ifdef CONFIG_BRCM
    dump_NanDataPathIndicationResponse(msg);
#endif /* CONFIG_BRCM */
    counters.dp_resp++;
    if (msg->service_name_len) {
        if (strncmp(NAN_OOB_INTEROP_SVC_NAME,
                    (char*)msg->service_name, msg->service_name_len) == 0) {
            ALOGI("Use Hardcoded svc_hash\n");
            msg->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(msg->service_name, NAN_OOB_INTEROP_SVC_HASH, NAN_SVC_HASH_SIZE);
        } else {
            u8 svc_hash[NAN_SVC_HASH_SIZE];

            ret = (wifi_error)get_svc_hash(msg->service_name, msg->service_name_len,
                    svc_hash, NAN_SVC_HASH_SIZE);
            if (ret < 0) {
                ALOGE("%s: Failed to get hashed svc name\n", __func__);
                goto done;
            }
            ALOGI("Created svc_hash\n");
            msg->service_name_len = NAN_SVC_HASH_SIZE;
            memcpy(msg->service_name, svc_hash, msg->service_name_len);
        }
    }
    if (msg->key_info.key_type == NAN_SECURITY_KEY_INPUT_PASSPHRASE) {
        NanDataPathSecInfoRequest msg_sec_info;

        if (msg->ndp_instance_id == 0) {
            ALOGE("Invalid NDP ID, Mandatory info is not present\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto done;
        } else {
            ALOGI("NDP ID = %d, Mandatory info is present\n", msg->ndp_instance_id);
        }
        msg_sec_info.ndp_instance_id = msg->ndp_instance_id;
        msg_sec_info.requestor_instance_id = 0;
        cmdType = NAN_DATA_PATH_SEC_INFO;
        cmd = new NanDataPathPrimitive(iface, id, (void *)&msg_sec_info, cmdType);
        NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

        ret = (wifi_error)cmd->open();
        if (ret != WIFI_SUCCESS) {
            ALOGE("%s : failed in start, error = %d\n", __func__, ret);
            goto done;
        }

        if (ETHER_ISNULLADDR(cmd->mPubNmi)) {
            ALOGE("Invalid Pub NMI\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto done;
        }
        memcpy(pub_nmi, cmd->mPubNmi, NAN_MAC_ADDR_LEN);

        if (!msg->service_name_len) {
            if (SVCHASH_ISNULL(cmd->mSvcHash)) {
                ALOGE("Invalid svc_hash\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto done;
            }
            memcpy(msg->service_name, cmd->mSvcHash, NAN_SVC_HASH_SIZE);
        }
    }
    /* free old command */
    if (cmd) {
        cmd->releaseRef();
    }
    cmdType = NAN_DATA_PATH_IND_RESPONSE;
    cmd = new NanDataPathPrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);
    memcpy(cmd->mPubNmi, pub_nmi, NAN_MAC_ADDR_LEN);
    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
        goto done;
    }

done:
    if (cmd) {
        cmd->releaseRef();
    }
    NAN_DBG_EXIT();
    return ret;
}

/* NDL termination request: from either Initiator/Responder */
wifi_error nan_data_end(transaction_id id,
        wifi_interface_handle iface, NanDataPathEndRequest* msg)
{
    wifi_error ret = WIFI_SUCCESS;
    NanDataPathPrimitive *cmd;
    NanRequestType cmdType = NAN_DATA_PATH_END;
    NAN_DBG_ENTER();

    cmd = new NanDataPathPrimitive(iface, id, (void *)msg, cmdType);
    NULL_CHECK_RETURN(cmd, "memory allocation failure", WIFI_ERROR_OUT_OF_MEMORY);

    ret = (wifi_error)cmd->open();
    if (ret != WIFI_SUCCESS) {
        ALOGE("%s : failed in open, error = %d\n", __func__, ret);
    }
    cmd->releaseRef();
    NAN_DBG_EXIT();
    return ret;
}
