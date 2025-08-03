/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Portions copyright (C) 2022 Broadcom Limited
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
#include <stdlib.h>
#ifndef ANDROID
#include <stddef.h>
#endif

#define LOG_TAG  "WifiHAL"

#define NAN_MAX_SIDS_IN_BEACONS 127
#include <utils/Log.h>
#ifndef ANDROID
#include <cutils/memory.h>
#endif
#include <inttypes.h>
#include <sys/socket.h>
#ifdef ANDROID
#include <linux/if.h>
#endif
#include <ctype.h>
#include <stdarg.h>
#include <semaphore.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include "netinet/in.h"
#include "arpa/inet.h"
#ifndef ANDROID
#include <sys/ioctl.h>
#include <net/if.h>
#endif
#include <sys/ioctl.h>
#include <linux/netlink.h>
#include "wifi_hal.h"
#include "wifi_nan.h"
#include "wifi_twt.h"
#include "hal_tool.h"
#include "interface_tool.h"

#include "common.h"

#define EVENT_COUNT 256
#define MAC2STR(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5]
#define MACSTR "%02x:%02x:%02x:%02x:%02x:%02x"

#define NMR2STR(a) (a)[0], (a)[1], (a)[2], (a)[3], (a)[4], (a)[5], (a)[6], (a)[7]
#define NMRSTR "%02x:%02x:%02x:%02x:%02x:%02x:%02x:%02x"
#define NAN_DISC_MAC_RAND_INTVL 30
pthread_mutex_t printMutex;

static wifi_hal_fn hal_fn;
static char* frequency_to_channel(int center_freq);

/* API to spawn a hal instance from halutil CLI to capture events */
wifi_error  nan_event_check_request(transaction_id id,
        wifi_interface_handle handle);

/* API to spawn a hal instance from halutil CLI to capture events */
wifi_error twt_event_check_request(transaction_id id,
        wifi_interface_handle handle);
static int set_interface_params(char *p_info, char *val_p, int len);
static void printApfUsage();
static void printTxPowerUsage();

void printMsg(const char *fmt, ...)
{
    pthread_mutex_lock(&printMutex);
    va_list l;
    va_start(l, fmt);

    vprintf(fmt, l);
    va_end(l);
    pthread_mutex_unlock(&printMutex);
}

template<typename T, unsigned N>
unsigned countof(T (&rgt)[N]) {
    return N;
}

#define NBBY    8  /* number of bits/byte */

/* Bit map related macros. */
#define setbit(a,i) ((a)[(i)/NBBY] |= 1<<((i)%NBBY))
#define clrbit(a,i) ((a)[(i)/NBBY] &= ~(1<<((i)%NBBY)))
#define isset(a,i)  ((a)[(i)/NBBY] & (1<<((i)%NBBY)))
#define isclr(a,i)  (((a)[(i)/NBBY] & (1<<((i)%NBBY))) == 0)
#define CEIL(x, y)  (((x) + ((y) - 1)) / (y))

/* TLV defines */
#define TLV_TAG_OFF     0   /* tag offset */
#define TLV_LEN_OFF     1   /* length offset */
#define TLV_HDR_LEN     2   /* header length */
#define TLV_BODY_OFF    2   /* body offset */
#define TLV_BODY_LEN_MAX 255  /* max body length */


/* Information Element IDs */
#define WIFI_EID_SSID 0
#define WIFI_EID_SUPP_RATES 1
#define WIFI_EID_FH_PARAMS 2
#define WIFI_EID_DS_PARAMS 3
#define WIFI_EID_CF_PARAMS 4
#define WIFI_EID_TIM 5
#define WIFI_EID_IBSS_PARAMS 6
#define WIFI_EID_COUNTRY 7
#define WIFI_EID_BSS_LOAD 11
#define WIFI_EID_CHALLENGE 16
/* EIDs defined by IEEE 802.11h - START */
#define WIFI_EID_PWR_CONSTRAINT 32
#define WIFI_EID_PWR_CAPABILITY 33
#define WIFI_EID_TPC_REQUEST 34
#define WIFI_EID_TPC_REPORT 35
#define WIFI_EID_SUPPORTED_CHANNELS 36
#define WIFI_EID_CHANNEL_SWITCH 37
#define WIFI_EID_MEASURE_REQUEST 38
#define WIFI_EID_MEASURE_REPORT 39
#define WIFI_EID_QUITE 40
#define WIFI_EID_IBSS_DFS 41
/* EIDs defined by IEEE 802.11h - END */
#define WIFI_EID_ERP_INFO 42
#define WIFI_EID_HT_CAP 45
#define WIFI_EID_QOS 46
#define WIFI_EID_RSN 48
#define WIFI_EID_EXT_SUPP_RATES 50
#define WIFI_EID_NEIGHBOR_REPORT 52
#define WIFI_EID_MOBILITY_DOMAIN 54
#define WIFI_EID_FAST_BSS_TRANSITION 55
#define WIFI_EID_TIMEOUT_INTERVAL 56
#define WIFI_EID_RIC_DATA 57
#define WIFI_EID_SUPPORTED_OPERATING_CLASSES 59
#define WIFI_EID_HT_OPERATION 61
#define WIFI_EID_SECONDARY_CHANNEL_OFFSET 62
#define WIFI_EID_WAPI 68
#define WIFI_EID_TIME_ADVERTISEMENT 69
#define WIFI_EID_20_40_BSS_COEXISTENCE 72
#define WIFI_EID_20_40_BSS_INTOLERANT 73
#define WIFI_EID_OVERLAPPING_BSS_SCAN_PARAMS 74
#define WIFI_EID_MMIE 76
#define WIFI_EID_SSID_LIST 84
#define WIFI_EID_BSS_MAX_IDLE_PERIOD 90
#define WIFI_EID_TFS_REQ 91
#define WIFI_EID_TFS_RESP 92
#define WIFI_EID_WNMSLEEP 93
#define WIFI_EID_TIME_ZONE 98
#define WIFI_EID_LINK_ID 101
#define WIFI_EID_INTERWORKING 107
#define WIFI_EID_ADV_PROTO 108
#define WIFI_EID_QOS_MAP_SET 110
#define WIFI_EID_ROAMING_CONSORTIUM 111
#define WIFI_EID_EXT_CAPAB 127
#define WIFI_EID_CCKM 156
#define WIFI_EID_VHT_CAP 191
#define WIFI_EID_VHT_OPERATION 192
#define WIFI_EID_VHT_EXTENDED_BSS_LOAD 193
#define WIFI_EID_VHT_WIDE_BW_CHSWITCH  194
#define WIFI_EID_VHT_TRANSMIT_POWER_ENVELOPE 195
#define WIFI_EID_VHT_CHANNEL_SWITCH_WRAPPER 196
#define WIFI_EID_VHT_AID 197
#define WIFI_EID_VHT_QUIET_CHANNEL 198
#define WIFI_EID_VHT_OPERATING_MODE_NOTIFICATION 199
#define WIFI_EID_VENDOR_SPECIFIC 221


/* Extended capabilities IE bitfields */
/* 20/40 BSS Coexistence Management support bit position */
#define DOT11_EXT_CAP_OBSS_COEX_MGMT        0
/* Extended Channel Switching support bit position */
#define DOT11_EXT_CAP_EXT_CHAN_SWITCHING    2
/* scheduled PSMP support bit position */
#define DOT11_EXT_CAP_SPSMP         6
/*  Flexible Multicast Service */
#define DOT11_EXT_CAP_FMS           11
/* proxy ARP service support bit position */
#define DOT11_EXT_CAP_PROXY_ARP     12
/* Civic Location */
#define DOT11_EXT_CAP_CIVIC_LOC     14
/* Geospatial Location */
#define DOT11_EXT_CAP_LCI           15
/* Traffic Filter Service */
#define DOT11_EXT_CAP_TFS           16
/* WNM-Sleep Mode */
#define DOT11_EXT_CAP_WNM_SLEEP     17
/* TIM Broadcast service */
#define DOT11_EXT_CAP_TIMBC         18
/* BSS Transition Management support bit position */
#define DOT11_EXT_CAP_BSSTRANS_MGMT 19
/* Direct Multicast Service */
#define DOT11_EXT_CAP_DMS           26
/* Interworking support bit position */
#define DOT11_EXT_CAP_IW            31
/* QoS map support bit position */
#define DOT11_EXT_CAP_QOS_MAP       32
/* service Interval granularity bit position and mask */
#define DOT11_EXT_CAP_SI            41
#define DOT11_EXT_CAP_SI_MASK       0x0E
/* WNM notification */
#define DOT11_EXT_CAP_WNM_NOTIF     46
/* Operating mode notification - VHT (11ac D3.0 - 8.4.2.29) */
#define DOT11_EXT_CAP_OPER_MODE_NOTIF  62
/* Fine timing measurement - D3.0 */
#define DOT11_EXT_CAP_FTM_RESPONDER    70
#define DOT11_EXT_CAP_FTM_INITIATOR    71 /* tentative 11mcd3.0 */

#define DOT11_EXT_CH_MASK   0x03 /* extension channel mask */
#define DOT11_EXT_CH_UPPER  0x01 /* ext. ch. on upper sb */
#define DOT11_EXT_CH_LOWER  0x03 /* ext. ch. on lower sb */
#define DOT11_EXT_CH_NONE   0x00 /* no extension ch.  */

enum vht_op_chan_width {
    VHT_OP_CHAN_WIDTH_20_40 = 0,
    VHT_OP_CHAN_WIDTH_80    = 1,
    VHT_OP_CHAN_WIDTH_160   = 2,
    VHT_OP_CHAN_WIDTH_80_80 = 3
};
/**
 * Channel Factor for the starting frequence of 2.4 GHz channels.
 * The value corresponds to 2407 MHz.
 */
#define CHAN_FACTOR_2_4_G 4814 /* 2.4 GHz band, 2407 MHz */

/**
 * Channel Factor for the starting frequence of 5 GHz channels.
 * The value corresponds to 5000 MHz.
 */
#define CHAN_FACTOR_5_G 10000 /* 5 GHz band, 5000 MHz */


/* ************* HT definitions. ************* */
#define MCSSET_LEN 16       /* 16-bits per 8-bit set to give 128-bits bitmap of MCS Index */
#define MAX_MCS_NUM (128)   /* max mcs number = 128 */

struct ht_op_ie {
    u8  ctl_ch;         /* control channel number */
    u8  chan_info;      /* ext ch,rec. ch. width, RIFS support */
    u16 opmode;         /* operation mode */
    u16 misc_bits;      /* misc bits */
    u8  basic_mcs[MCSSET_LEN];  /* required MCS set */
} __attribute__ ((packed));
struct vht_op_ie {
    u8  chan_width;
    u8  chan1;
    u8  chan2;
    u16 supp_mcs; /* same def as above in vht cap */
} __attribute__ ((packed));

#define EVENT_BUF_SIZE 2048
#define MAX_EVENT_MSG_LEN 256
#define MAX_CH_BUF_SIZE  256
#define MAX_FEATURE_SET  8
#define MAX_RADIO_COMBO	5
#define MAX_CORE 2
#define HOTLIST_LOST_WINDOW  5

static wifi_handle halHandle;
static wifi_interface_handle *ifaceHandles;
static wifi_interface_handle wlan0Handle;
static wifi_interface_handle p2p0Handle;
static int numIfaceHandles;
static int cmdId = 0;
static int max_event_wait = 5;
static int stest_max_ap = 10;
static int stest_base_period = 5000;
static int stest_threshold_percent = 80;
static int stest_threshold_num_scans = 10;
static int swctest_rssi_sample_size =  3;
static int swctest_rssi_lost_ap =  3;
static int swctest_rssi_min_breaching =  2;
static int swctest_rssi_ch_threshold =  1;
static int htest_low_threshold =  90;
static int htest_high_threshold =  10;
static int rssi_monitor = 0;
static signed char min_rssi = 0;
static signed char max_rssi = 0;
static size_t n_requested_pkt_fate = 0;

#define FILE_NAME_LEN 128
#define FILE_MAX_SIZE (1 * 1024 * 1024)
#define MAX_RING_NAME_SIZE 32

#define NUM_ALERT_DUMPS 10
#define ETHER_ADDR_LEN 6
#define MAX_NAN_MSG_BUF_SIZE 256

#define NAN_MAX_CLUST_VALUE_RANGE 0xFFFF
#define MAX_CH_AVOID 128

/*
 * Host can send Post Connectivity Capability attributes
 * to be included in Service Discovery frames transmitted.
 */
enum post_connectivity_capability {
    FEATURE_NOT_SUPPORTED   = 0,
    FEATURE_SUPPORTED       = 1
};


/////////////////////////////////////////////////////////////////
// Logger related.

#define DEFAULT_MEMDUMP_FILE "/data/memdump.bin"
#define ALERT_MEMDUMP_PREFIX "/data/alertdump"
#define RINGDATA_PREFIX "/data/ring-"
#define DEFAULT_TX_PKT_FATE_FILE "/data/txpktfate.txt"
#define DEFAULT_RX_PKT_FATE_FILE "/data/rxpktfate.txt"

static char mem_dump_file[FILE_NAME_LEN] = DEFAULT_MEMDUMP_FILE;
static char tx_pkt_fate_file[FILE_NAME_LEN] = DEFAULT_TX_PKT_FATE_FILE;
static char rx_pkt_fate_file[FILE_NAME_LEN] = DEFAULT_RX_PKT_FATE_FILE;

struct LoggerParams {
    u32 verbose_level;
    u32 flags;
    u32 max_interval_sec;
    u32 min_data_size;
    wifi_ring_buffer_id ring_id;
    char ring_name[MAX_RING_NAME_SIZE];
};
struct LoggerParams default_logger_param = {0, 0 , 0 , 0, 0, {0}};

char default_ring_name[MAX_RING_NAME_SIZE] = "fw_event";

typedef enum {
    LOG_INVALID = -1,
    LOG_START,
    LOG_GET_MEMDUMP,
    LOG_GET_FW_VER,
    LOG_GET_DRV_VER,
    LOG_GET_RING_STATUS,
    LOG_GET_RINGDATA,
    LOG_GET_FEATURE,
    LOG_GET_RING_DATA,
    LOG_MONITOR_PKTFATE,
    LOG_GET_TXPKTFATE,
    LOG_GET_RXPKTFATE,
    LOG_SET_LOG_HANDLER,
    LOG_SET_ALERT_HANDLER,
} LoggerCmd;

LoggerCmd log_cmd = LOG_INVALID;
wifi_ring_buffer_id ringId = -1;

#define C2S(x)  case x: return #x;

static const char *RBentryTypeToString(int cmd) {
    switch (cmd) {
        C2S(ENTRY_TYPE_CONNECT_EVENT)
        C2S(ENTRY_TYPE_PKT)
        C2S(ENTRY_TYPE_WAKE_LOCK)
        C2S(ENTRY_TYPE_POWER_EVENT)
        C2S(ENTRY_TYPE_DATA)
        default:
            return "ENTRY_TYPE_UNKNOWN";
    }
}

static const char *RBconnectEventToString(int cmd)
{
    switch (cmd) {
        C2S(WIFI_EVENT_ASSOCIATION_REQUESTED)
        C2S(WIFI_EVENT_AUTH_COMPLETE)
        C2S(WIFI_EVENT_ASSOC_COMPLETE)
        C2S(WIFI_EVENT_FW_AUTH_STARTED)
        C2S(WIFI_EVENT_FW_ASSOC_STARTED)
        C2S(WIFI_EVENT_FW_RE_ASSOC_STARTED)
        C2S(WIFI_EVENT_DRIVER_SCAN_REQUESTED)
        C2S(WIFI_EVENT_DRIVER_SCAN_RESULT_FOUND)
        C2S(WIFI_EVENT_DRIVER_SCAN_COMPLETE)
        C2S(WIFI_EVENT_G_SCAN_STARTED)
        C2S(WIFI_EVENT_G_SCAN_COMPLETE)
        C2S(WIFI_EVENT_DISASSOCIATION_REQUESTED)
        C2S(WIFI_EVENT_RE_ASSOCIATION_REQUESTED)
        C2S(WIFI_EVENT_ROAM_REQUESTED)
        C2S(WIFI_EVENT_BEACON_RECEIVED)
        C2S(WIFI_EVENT_ROAM_SCAN_STARTED)
        C2S(WIFI_EVENT_ROAM_SCAN_COMPLETE)
        C2S(WIFI_EVENT_ROAM_SEARCH_STARTED)
        C2S(WIFI_EVENT_ROAM_SEARCH_STOPPED)
        C2S(WIFI_EVENT_CHANNEL_SWITCH_ANOUNCEMENT)
        C2S(WIFI_EVENT_FW_EAPOL_FRAME_TRANSMIT_START)
        C2S(WIFI_EVENT_FW_EAPOL_FRAME_TRANSMIT_STOP)
        C2S(WIFI_EVENT_DRIVER_EAPOL_FRAME_TRANSMIT_REQUESTED)
        C2S(WIFI_EVENT_FW_EAPOL_FRAME_RECEIVED)
        C2S(WIFI_EVENT_DRIVER_EAPOL_FRAME_RECEIVED)
        C2S(WIFI_EVENT_BLOCK_ACK_NEGOTIATION_COMPLETE)
        C2S(WIFI_EVENT_BT_COEX_BT_SCO_START)
        C2S(WIFI_EVENT_BT_COEX_BT_SCO_STOP)
        C2S(WIFI_EVENT_BT_COEX_BT_SCAN_START)
        C2S(WIFI_EVENT_BT_COEX_BT_SCAN_STOP)
        C2S(WIFI_EVENT_BT_COEX_BT_HID_START)
        C2S(WIFI_EVENT_BT_COEX_BT_HID_STOP)
        C2S(WIFI_EVENT_ROAM_AUTH_STARTED)
        C2S(WIFI_EVENT_ROAM_AUTH_COMPLETE)
        C2S(WIFI_EVENT_ROAM_ASSOC_STARTED)
        C2S(WIFI_EVENT_ROAM_ASSOC_COMPLETE)
        C2S(WIFI_EVENT_DRIVER_PNO_ADD)
        C2S(WIFI_EVENT_DRIVER_PNO_REMOVE)
        C2S(WIFI_EVENT_DRIVER_PNO_NETWORK_FOUND)
        C2S(WIFI_EVENT_DRIVER_PNO_SCAN_REQUESTED)
        C2S(WIFI_EVENT_DRIVER_PNO_SCAN_RESULT_FOUND)
        C2S(WIFI_EVENT_DRIVER_PNO_SCAN_COMPLETE)
        default:
            return "WIFI_EVENT_UNKNOWN";
    }
}

static const char *RBTlvTagToString(int cmd) {
    switch (cmd) {
        C2S(WIFI_TAG_VENDOR_SPECIFIC)
        C2S(WIFI_TAG_BSSID)
        C2S(WIFI_TAG_ADDR)
        C2S(WIFI_TAG_SSID)
        C2S(WIFI_TAG_STATUS)
        C2S(WIFI_TAG_CHANNEL_SPEC)
        C2S(WIFI_TAG_WAKE_LOCK_EVENT)
        C2S(WIFI_TAG_ADDR1)
        C2S(WIFI_TAG_ADDR2)
        C2S(WIFI_TAG_ADDR3)
        C2S(WIFI_TAG_ADDR4)
        C2S(WIFI_TAG_IE)
        C2S(WIFI_TAG_INTERFACE)
        C2S(WIFI_TAG_REASON_CODE)
        C2S(WIFI_TAG_RATE_MBPS)
        C2S(WIFI_TAG_CHANNEL)
        C2S(WIFI_TAG_RSSI)
        default:
            return "WIFI_TAG_UNKNOWN";
    }
}

static const char *RBchanWidthToString(int cmd) {
    switch (cmd) {
        C2S(WIFI_CHAN_WIDTH_20)
        C2S(WIFI_CHAN_WIDTH_40)
        C2S(WIFI_CHAN_WIDTH_80)
        C2S(WIFI_CHAN_WIDTH_160)
        C2S(WIFI_CHAN_WIDTH_80P80)
        C2S(WIFI_CHAN_WIDTH_5)
        C2S(WIFI_CHAN_WIDTH_10)
        C2S(WIFI_CHAN_WIDTH_INVALID)
        default:
            return "WIFI_CHAN_WIDTH_INVALID";
    }
}

static const char *BandToString(wlan_mac_band cmd) {
    switch (cmd) {
        C2S(WLAN_MAC_2_4_BAND)
        C2S(WLAN_MAC_5_0_BAND)
        C2S(WLAN_MAC_6_0_BAND)
        C2S(WLAN_MAC_60_0_BAND)
        default:
        return "INVALID";
    }
}

static const char *AntennCfgToString(wifi_antenna_configuration cmd) {
    switch (cmd) {
        C2S(WIFI_ANTENNA_1X1)
        C2S(WIFI_ANTENNA_2X2)
        C2S(WIFI_ANTENNA_3X3)
        C2S(WIFI_ANTENNA_4X4)
        default:
        return "WIFI_ANTENNA_INVALID";
    }
}

/////////////////////////////////////////////////////////////////
// RTT related to configuration
#define MAX_SSID_LEN (32 + 1)
/* 18-bytes of Ethernet address buffer length */
#define ETHER_ADDR_STR_LEN      18
#define ETHER_ADDR_LEN          6

#define DEFAULT_RTT_FILE "/data/rtt-ap.list"
static int rtt_from_file = 0;
static int rtt_to_file = 0;
static wifi_band band = WIFI_BAND_UNSPECIFIED;
static int max_ap = 256; // the maximum count of  ap for RTT test
static char rtt_aplist[FILE_NAME_LEN] = DEFAULT_RTT_FILE;
static mac_addr responder_addr;
static wifi_channel responder_channel;
static int channel_width = 0;
static bool rtt_sta = false;
static bool rtt_nan = false;
static bool is_6g = false;
struct rtt_params {
    u32 burst_period;
    u32 num_burst;
    u32 num_frames_per_burst;
    u32 num_retries_per_ftm;
    u32 num_retries_per_ftmr;
    u32 burst_duration;
    u8 LCI_request;
    u8 LCR_request;
    u8 preamble;
    u8 bw;
    wifi_rtt_type type;
};
struct rtt_params default_rtt_param = {0, 0, 0, 0, 0, 15, 0, 0, 0, 0, RTT_TYPE_2_SIDED};

mac_addr hotlist_bssids[16];
mac_addr blacklist_bssids[16];
char whitelist_ssids[MAX_WHITELIST_SSID][MAX_SSID_LEN] = {{0}};
unsigned char mac_oui[3];
wifi_epno_params epno_cfg;
int channel_list[16];
int num_hotlist_bssids = 0;
int num_channels = 0;
mac_addr pref_bssids[16];
int rssi_modifier[16];
int num_pref_bssids = -1;
int num_blacklist_bssids = -1;
int num_whitelist_ssids = -1;
bool set_roaming_configuration = false;

#define EPNO_HIDDEN               (1 << 0)
#define EPNO_A_BAND_TRIG          (1 << 1)
#define EPNO_BG_BAND_TRIG         (1 << 2)
#define EPNO_ABG_BAND_TRIG        (EPNO_A_BAND_TRIG | EPNO_BG_BAND_TRIG)
#define EPNO_FLAG_STRICT_MATCH    (1 << 3)
#define EPNO_FLAG_SAME_NETWORK    (1 << 4)

void parseMacAddress(const char *str, mac_addr addr);

int linux_set_iface_flags(int sock, const char *ifname, int dev_up)
{
    struct ifreq ifr;
    int ret;

    ALOGD("setting interface %s flags (%s)\n", ifname, dev_up ? "UP" : "DOWN");

    if (sock < 0) {
        printMsg("Bad socket: %d\n", sock);
        return -1;
    }

    memset(&ifr, 0, sizeof(ifr));
#ifdef ANDROID
    strlcpy(ifr.ifr_name, ifname, IFNAMSIZ);
#else
    strncpy(ifr.ifr_name, ifname, IFNAMSIZ);
#endif
    ALOGD("reading old value\n");

    if (ioctl(sock, SIOCGIFFLAGS, &ifr) != 0) {
        ret = errno ? -errno : -999;
        printMsg("Could not read interface %s flags: %d\n", ifname, errno);
        return ret;
    } else {
        ALOGD("writing new value\n");
    }

    if (dev_up) {
        if (ifr.ifr_flags & IFF_UP) {
            ALOGD("interface %s is already up\n", ifname);
            return 0;
        }
        ifr.ifr_flags |= IFF_UP;
    } else {
        if (!(ifr.ifr_flags & IFF_UP)) {
            printMsg("interface %s is already down\n", ifname);
            return 0;
        }
        ifr.ifr_flags &= ~IFF_UP;
    }

    if (ioctl(sock, SIOCSIFFLAGS, &ifr) != 0) {
        ret = errno ? -errno : -999;
        printMsg("Could not set interface %s flags \n", ifname);
        return ret;
    } else {
        ALOGD("set interface %s flags (%s)\n", ifname, dev_up ? "UP" : "DOWN");
    }
    ALOGD("Done\n");
    return 0;
}


static int init() {
    android::wifi_system::HalTool hal_tool;
    if (!hal_tool.InitFunctionTable(&hal_fn)) {
        printMsg("Could not initialize the function table!\n");
        return -1;
    }

    android::wifi_system::InterfaceTool if_tool;
    if (!if_tool.SetWifiUpState(true)) {
        printMsg("Failed to set the interface state to up.\n");
        return -1;
    }

    wifi_error res = hal_fn.wifi_initialize(&halHandle);
    if (res < 0) {
        return res;
    }

    res = hal_fn.wifi_get_ifaces(halHandle, &numIfaceHandles, &ifaceHandles);
    if (res < 0) {
        return res;
    }

    char buf[EVENT_BUF_SIZE];
    for (int i = 0; i < numIfaceHandles; i++) {
        if (hal_fn.wifi_get_iface_name(ifaceHandles[i], buf, sizeof(buf)) == WIFI_SUCCESS) {
            if (strcmp(buf, "wlan0") == 0) {
                printMsg("found interface %s\n", buf);
                wlan0Handle = ifaceHandles[i];
            } else if (strcmp(buf, "p2p0") == 0) {
                printMsg("found interface %s\n", buf);
                p2p0Handle = ifaceHandles[i];
            }
        }
    }

    return res;
}

static void cleaned_up_handler(wifi_handle handle) {
    printMsg("HAL cleaned up handler\n");
    halHandle = NULL;
    ifaceHandles = NULL;
}

static void cleanup() {
    printMsg("cleaning up HAL\n");
    hal_fn.wifi_cleanup(halHandle, cleaned_up_handler);
}

sem_t event_thread_mutex;

static void *eventThreadFunc(void *context) {

    printMsg("starting wifi event loop\n");
    sem_post( &event_thread_mutex );
    hal_fn.wifi_event_loop(halHandle);
    printMsg("out of wifi event loop\n");

    return NULL;
}


static int getNewCmdId() {
    return cmdId++;
}

/* pretty hex print a contiguous buffer to file */
void
fprhex(FILE *f_wp, char *buf, uint nbytes, bool prefix)
{
    char line[128], *p;
    int rem_len = sizeof(line);
    int nchar = 0;
    uint i;

    p = line;
    for (i = 0; i < nbytes; i++) {
        if ((i % 16 == 0) && prefix) {
            nchar = snprintf(p, rem_len, "  %04x: ", i); /* line prefix */
            p += nchar;
            rem_len -= nchar;
        }

        if (rem_len > 0) {
            nchar = snprintf(p, rem_len, "%02x ", (unsigned char)buf[i]);
            p += nchar;
            rem_len -= nchar;
        }

        if (i % 16 == 15) {
            fprintf(f_wp, "%s\n", line); /* flush line */
            p = line;
            rem_len = sizeof(line);
        }
    }

    /* flush last partial line */
    if (p != line) {
        fprintf(f_wp, "%s\n", line);
    }
}


/* -------------------------------------------  */
/* helpers                                      */
/* -------------------------------------------  */

void printScanHeader() {
    printMsg("SSID\t\t\t\t\tBSSID\t\t  RSSI\tchannel\ttimestamp\tRTT\tRTT SD\n");
    printMsg("----\t\t\t\t\t-----\t\t  ----\t-------\t---------\t---\t------\n");
}

void printScanResult(wifi_scan_result result) {

    printMsg("%-32s\t", result.ssid);

    printMsg("%02x:%02x:%02x:%02x:%02x:%02x ", result.bssid[0], result.bssid[1],
            result.bssid[2], result.bssid[3], result.bssid[4], result.bssid[5]);

    printMsg("%d\t", result.rssi);
    printMsg("%d\t", result.channel);
    printMsg("%lld\t", result.ts);
    printMsg("%lld\t", result.rtt);
    printMsg("%lld\n", result.rtt_sd);
}

void printSignificantChangeResult(wifi_significant_change_result *res) {

    wifi_significant_change_result &result = *res;
    printMsg("%02x:%02x:%02x:%02x:%02x:%02x ", result.bssid[0], result.bssid[1],
            result.bssid[2], result.bssid[3], result.bssid[4], result.bssid[5]);

    printMsg("%d\t", result.channel);

    for (int i = 0; i < result.num_rssi; i++) {
        printMsg("%d,", result.rssi[i]);
    }
    printMsg("\n");
}

void printScanCapabilities(wifi_gscan_capabilities capabilities)
{
    printMsg("Scan Capabililites\n");
    printMsg("  max_scan_cache_size = %d\n", capabilities.max_scan_cache_size);
    printMsg("  max_scan_buckets = %d\n", capabilities.max_scan_buckets);
    printMsg("  max_ap_cache_per_scan = %d\n", capabilities.max_ap_cache_per_scan);
    printMsg("  max_rssi_sample_size = %d\n", capabilities.max_rssi_sample_size);
    printMsg("  max_scan_reporting_threshold = %d\n", capabilities.max_scan_reporting_threshold);
    printMsg("  max_hotlist_bssids = %d\n", capabilities.max_hotlist_bssids);
    printMsg("  max_significant_wifi_change_aps = %d\n",
            capabilities.max_significant_wifi_change_aps);
    printMsg("  max_number_epno_networks = %d\n", capabilities.max_number_epno_networks);
}


/* -------------------------------------------  */
/* commands and events                          */
/* -------------------------------------------  */

typedef enum {
    EVENT_TYPE_SCAN_FAILED                        = 1000,
    EVENT_TYPE_HOTLIST_AP_FOUND                   = 1001,
    EVENT_TYPE_SIGNIFICANT_WIFI_CHANGE            = 1002,
    EVENT_TYPE_RTT_RESULTS                        = 1003,
    EVENT_TYPE_SCAN_COMPLETE                      = 1004,
    EVENT_TYPE_HOTLIST_AP_LOST                    = 1005,
    EVENT_TYPE_EPNO_SSID                          = 1006,
    EVENT_TYPE_LOGGER_RINGBUFFER_DATA             = 1007,
    EVENT_TYPE_LOGGER_MEMDUMP_DATA                = 1008,
    EVENT_TYPE_LOGGER_ALERT_DATA                  = 1009,
    EVENT_TYPE_RSSI_MONITOR                       = 1010,
    EVENT_TYPE_SCAN_RESULTS_THRESHOLD             = 1011,
    EVENT_TYPE_NAN_PUBLISH_REPLIED                = 1012,
    EVENT_TYPE_SUBSCRIBE_MATCHED                  = 1013,
    EVENT_TYPE_NAN_FOLLOWUP_RECIEVE               = 1014,
    EVENT_TYPE_NAN_PUBLISH_TERMINATED             = 1015,
    EVENT_TYPE_NAN_DISABLED                       = 1016,
    EVENT_TYPE_NAN_SUBSCRIBE_TERMINATED           = 1017,
    EVENT_TYPE_NAN_ENABLED                        = 1018,
    EVENT_TYPE_NAN_DATA_REQUEST_INDICATION        = 1019,
    EVENT_TYPE_NAN_DATA_CONFIRMATION              = 1020,
    EVENT_TYPE_NAN_DATA_END_INDICAION             = 1021,
    EVENT_TYPE_NAN_TRANSMIT_FOLLOWUP_INDICATION   = 1022,
    EVENT_TYPE_RTT_RESULTS_DETAIL                 = 1023,
    EVENT_TYPE_CHRE_NAN_RTT_STATE_UPDATED         = 1024,

} EventType;

typedef struct {
    int type;
    char buf[MAX_EVENT_MSG_LEN];
} EventInfo;

const int MAX_EVENTS_IN_CACHE = 256;
EventInfo eventCache[256];
int eventsInCache = 0;
pthread_cond_t eventCacheCondition;
pthread_mutex_t eventCacheMutex;

void putEventInCache(int type, const char *msg) {
    pthread_mutex_lock(&eventCacheMutex);
    if (eventsInCache + 1 < MAX_EVENTS_IN_CACHE) {
        eventCache[eventsInCache].type = type;
        strncpy(eventCache[eventsInCache].buf, msg, (MAX_EVENT_MSG_LEN - 1));
        eventCache[eventsInCache].buf[MAX_EVENT_MSG_LEN - 1] = '\0';
        eventsInCache++;
        pthread_cond_signal(&eventCacheCondition);
    } else {
        printMsg("Too many events in the cache\n");
    }
    pthread_mutex_unlock(&eventCacheMutex);
}

void getEventFromCache(EventInfo& info) {
    pthread_mutex_lock(&eventCacheMutex);
    while (true) {
        if (eventsInCache > 0) {
            info.type = eventCache[0].type;
            strncpy(info.buf, eventCache[0].buf, (MAX_EVENT_MSG_LEN - 1));
            eventCache[0].buf[MAX_EVENT_MSG_LEN - 1] = '\0';
            eventsInCache--;
            memmove(&eventCache[0], &eventCache[1], sizeof(EventInfo) * eventsInCache);
            pthread_mutex_unlock(&eventCacheMutex);
            return;
        } else {
            pthread_cond_wait(&eventCacheCondition, &eventCacheMutex);
        }
    }
}

static void on_scan_event(wifi_request_id id, wifi_scan_event event) {
    EventType internal_event;
    printMsg("Received scan event\n");
    if (event == WIFI_SCAN_THRESHOLD_PERCENT || event == WIFI_SCAN_THRESHOLD_NUM_SCANS) {
        printMsg("Received buffer events - %d \n", event);
        internal_event = EVENT_TYPE_SCAN_RESULTS_THRESHOLD;
    } else if(event == WIFI_SCAN_RESULTS_AVAILABLE) {
        printMsg("Received scan complete event  - WIFI_SCAN_RESULTS_AVAILABLE!!\n");
        internal_event = EVENT_TYPE_SCAN_COMPLETE;
    } else if (event == WIFI_SCAN_FAILED) {
        printMsg("Received scan event - WIFI_SCAN_FAILED \n");
        internal_event = EVENT_TYPE_SCAN_FAILED;
    } else {
        /* set to default value */
        internal_event = EVENT_TYPE_SCAN_FAILED;
    }
    putEventInCache(internal_event, "New scan event");
}

static int scanCmdId;
static int rssiMonId;
static int hotlistCmdId;
static int rttCmdId;
static int epnoCmdId;
static int loggerCmdId;
static u16 nanCmdId;
static u16 twtCmdId;
static wifi_error twt_init_handlers(void);

static bool startScan(int max_ap_per_scan, int base_period, int threshold_percent,
        int threshold_num_scans) {

    /* Get capabilties */
    wifi_gscan_capabilities capabilities;
    int result = hal_fn.wifi_get_gscan_capabilities(wlan0Handle, &capabilities);
    if (result < 0) {
        printMsg("failed to get scan capabilities - %d\n", result);
        printMsg("trying scan anyway ..\n");
    } else {
        printScanCapabilities(capabilities);
    }

    wifi_scan_cmd_params params;
    memset(&params, 0, sizeof(params));

    if(num_channels > 0){
        params.max_ap_per_scan = max_ap_per_scan;
        params.base_period = base_period;                      // 5 second by default
        params.report_threshold_percent = threshold_percent;
        params.report_threshold_num_scans = threshold_num_scans;
        params.num_buckets = 1;

        params.buckets[0].bucket = 0;
        params.buckets[0].band = WIFI_BAND_UNSPECIFIED;
        params.buckets[0].period = base_period;
        params.buckets[0].num_channels = num_channels;

        for(int i = 0; i < num_channels; i++){
            params.buckets[0].channels[i].channel = channel_list[i];
        }

    } else {

        /* create a schedule to scan channels 1, 6, 11 every 5 second and
         * scan 36, 40, 44, 149, 153, 157, 161 165 every 10 second */
        params.max_ap_per_scan = max_ap_per_scan;
        params.base_period = base_period;                      // 5 second
        params.report_threshold_percent = threshold_percent;
        params.report_threshold_num_scans = threshold_num_scans;
        params.num_buckets = 4;

        params.buckets[0].bucket = 0;
        params.buckets[0].band = WIFI_BAND_BG;
        params.buckets[0].period = 5000;                // 5 second
        params.buckets[0].report_events = 0;
        params.buckets[0].num_channels = 3;     // driver should ignore list since band is specified

        params.buckets[0].channels[0].channel = 2412;
        params.buckets[0].channels[1].channel = 2437;
        params.buckets[0].channels[2].channel = 2462;

        params.buckets[1].bucket = 1;
        params.buckets[1].band = WIFI_BAND_UNSPECIFIED;
        params.buckets[1].period = 10000;               // 10 second
        params.buckets[1].report_events = 0;
        params.buckets[1].num_channels = 6;


        params.buckets[1].channels[0].channel = 5180;
        params.buckets[1].channels[1].channel = 5200;
        params.buckets[1].channels[2].channel = 5220;
        params.buckets[1].channels[3].channel = 5745;
        params.buckets[1].channels[4].channel = 5765;
        params.buckets[1].channels[5].channel = 5785;

        params.buckets[2].bucket = 2;
        params.buckets[2].band = WIFI_BAND_UNSPECIFIED;
        params.buckets[2].period = 15000;                // 15 second
        params.buckets[2].report_events = 0;
        params.buckets[2].num_channels = 3;

        params.buckets[2].channels[0].channel = 2462;
        params.buckets[2].channels[1].channel = 5805;
        params.buckets[2].channels[2].channel = 5825;

        params.buckets[3].bucket = 3;
        params.buckets[3].band = WIFI_BAND_A;
        params.buckets[3].period = 35000;       // 35 second
        params.buckets[3].report_events = 1;
        params.buckets[3].num_channels = 3;     // driver should ignore list since band is specified

        params.buckets[3].channels[0].channel = 2462;
        params.buckets[3].channels[1].channel = 5805;
        params.buckets[3].channels[2].channel = 5825;
    }

    wifi_scan_result_handler handler;
    memset(&handler, 0, sizeof(handler));

    handler.on_scan_event = on_scan_event;

    scanCmdId = getNewCmdId();
    printMsg("Starting scan --->\n");
    printMsg("Number of buckets = %d\n", params.num_buckets);
    return hal_fn.wifi_start_gscan(scanCmdId, wlan0Handle, params, handler) == WIFI_SUCCESS;
}

static void stopScan() {
    wifi_request_id id = scanCmdId;
    if (id == 0)
        id = -1;

    hal_fn.wifi_stop_gscan(id, wlan0Handle);
    scanCmdId = 0;
}

wifi_scan_result **saved_scan_results;
unsigned max_saved_scan_results;
unsigned num_saved_scan_results;

static void on_single_shot_scan_event(wifi_request_id id, wifi_scan_event event) {
    if(event == WIFI_SCAN_RESULTS_AVAILABLE || event == WIFI_SCAN_THRESHOLD_NUM_SCANS ||
            event == WIFI_SCAN_THRESHOLD_PERCENT) {
        printMsg("Received scan complete event: %d\n", event);
        putEventInCache(EVENT_TYPE_SCAN_COMPLETE, "One scan completed");
    }
    else if (event == WIFI_SCAN_FAILED) {
        printMsg("Received scan event - WIFI_SCAN_FAILED \n");
        putEventInCache(EVENT_TYPE_SCAN_FAILED, "Scan failed");
    }
    else {
        printMsg("Received unknown scan event: %d \n", event);
    }
}

static void on_full_scan_result(wifi_request_id id, wifi_scan_result *r, unsigned buckets_scanned) {
    if (num_saved_scan_results < max_saved_scan_results) {
        int alloc_len = offsetof(wifi_scan_result, ie_data) + r->ie_length;
        wifi_scan_result **result = &(saved_scan_results[num_saved_scan_results]);
        *result = (wifi_scan_result *)malloc(alloc_len);
        memcpy(*result, r, alloc_len);
        printMsg("buckets_scanned - %x\n", buckets_scanned);
        num_saved_scan_results++;
    }
}

static int scanOnce(wifi_band band, wifi_scan_result **results, int num_results) {

    saved_scan_results = results;
    max_saved_scan_results = num_results;
    num_saved_scan_results = 0;

    wifi_scan_cmd_params params;
    memset(&params, 0, sizeof(params));

    params.max_ap_per_scan = 10;
    params.base_period = 5000;                        // 5 second by default
    params.report_threshold_percent = 90;
    params.report_threshold_num_scans = 1;
    params.num_buckets = 1;

    params.buckets[0].bucket = 0;
    params.buckets[0].band = band;
    params.buckets[0].period = 5000;                  // 5 second
    params.buckets[0].report_events = 2;              // REPORT_EVENTS_AFTER_EACH_SCAN
    params.buckets[0].num_channels = 0;

    wifi_scan_result_handler handler;
    memset(&handler, 0, sizeof(handler));
    handler.on_scan_event = on_single_shot_scan_event;
    handler.on_full_scan_result = on_full_scan_result;

    int scanCmdId = getNewCmdId();
    printMsg("Starting scan --->\n");
    if (hal_fn.wifi_start_gscan(scanCmdId, wlan0Handle, params, handler) == WIFI_SUCCESS) {
        while (true) {
            EventInfo info;
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_SCAN_RESULTS_THRESHOLD
                    || info.type == EVENT_TYPE_SCAN_COMPLETE) {
                int retrieved_num_results = num_saved_scan_results;
                if (retrieved_num_results == 0) {
                    printMsg("fetched 0 scan results, waiting for more..\n");
                    continue;
                } else {
                    printMsg("fetched %d scan results\n", retrieved_num_results);

                    printMsg("Scan once completed, stopping scan\n");
                    hal_fn.wifi_stop_gscan(scanCmdId, wlan0Handle);
                    saved_scan_results = NULL;
                    max_saved_scan_results = 0;
                    num_saved_scan_results = 0;
                    return retrieved_num_results;
                }
            }
        }
    } else {
        return 0;
    }
}

static int retrieveScanResults() {

    int num_results = 64;
    wifi_cached_scan_results *results;
    results = (wifi_cached_scan_results *)malloc(num_results * sizeof(wifi_cached_scan_results));
    if (!results) {
        printMsg("%s:Malloc failed\n",__FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(results, 0, sizeof(wifi_cached_scan_results) * num_results);
    printMsg("Retrieve Scan results available -->\n");
    int result = hal_fn.wifi_get_cached_gscan_results(wlan0Handle, 1, num_results, results, &num_results);
    if (result < 0) {
        printMsg("failed to fetch scan results : %d\n", result);
        goto exit;
    } else {
        printMsg("fetched %d scan results\n", num_results);
    }

    printScanHeader();
    for (int i = 0; i < num_results; i++) {
        printMsg("ScanId = %d, Scanned buckets 0x%x, Flags = %x, num results = %d\n",
            results[i].scan_id, results[i].buckets_scanned, results[i].flags,
            results[i].num_results);
        for (int j = 0; j < results[i].num_results; j++) {
            printScanResult(results[i].results[j]);
        }
        printMsg("\n");
    }

exit:
    if (results) {
        free(results);
    }
    return WIFI_SUCCESS;
}


static int compareScanResultsByRssi(const void *p1, const void *p2) {
    const wifi_scan_result *result1 = *(const wifi_scan_result **)(p1);
    const wifi_scan_result *result2 = *(const wifi_scan_result **)(p2);

    /* RSSI is -ve, so lower one wins */
    if (result1->rssi < result2->rssi) {
        return 1;
    } else if (result1->rssi == result2->rssi) {
        return 0;
    } else {
        return -1;
    }
}

static void sortScanResultsByRssi(wifi_scan_result **results, int num_results) {
    qsort(results, num_results, sizeof(wifi_scan_result*), &compareScanResultsByRssi);
}

static int removeDuplicateScanResults(wifi_scan_result **results, int num) {
    /* remove duplicates by BSSID */
    int num_results = num;
    wifi_scan_result *tmp;
    for (int i = 0; i < num_results; i++) {
        for (int j = i + 1; j < num_results; ) {
            if (memcmp((*results[i]).bssid, (*results[j]).bssid, sizeof(mac_addr)) == 0) {
                int num_to_move = num_results - j - 1;
                tmp = results[j];
                memmove(&results[j], &results[j+1], num_to_move * sizeof(wifi_scan_result *));
                free(tmp);
                num_results--;
            } else {
                j++;
            }
        }
    }
    return num_results;
}

static void onRTTResults (wifi_request_id id, unsigned num_results, wifi_rtt_result *result[]) {

    printMsg("RTT results\n");
    wifi_rtt_result *rtt_result;
    mac_addr addr = {0};
    for (unsigned i = 0; i < num_results; i++) {
        rtt_result = result[i];
        if (memcmp(addr, rtt_result->addr, sizeof(mac_addr))) {
            printMsg("Target mac : %02x:%02x:%02x:%02x:%02x:%02x\n",
                    rtt_result->addr[0],
                    rtt_result->addr[1],
                    rtt_result->addr[2],
                    rtt_result->addr[3],
                    rtt_result->addr[4],
                    rtt_result->addr[5]);
            memcpy(addr, rtt_result->addr, sizeof(mac_addr));
        }
        printMsg("\tburst_num : %d, measurement_number : %d, success_number : %d\n"
                "\tnumber_per_burst_peer : %d, status : %d, retry_after_duration : %d s\n"
                "\trssi : %d dbm, rx_rate : %d Kbps, rtt : %llu ns, rtt_sd : %llu\n"
                "\tdistance : %d cm, burst_duration : %d ms, negotiated_burst_num : %d\n",
                rtt_result->burst_num, rtt_result->measurement_number,
                rtt_result->success_number, rtt_result->number_per_burst_peer,
                rtt_result->status, rtt_result->retry_after_duration,
                rtt_result->rssi, rtt_result->rx_rate.bitrate * 100,
                rtt_result->rtt/1000, rtt_result->rtt_sd, rtt_result->distance_mm / 10,
                rtt_result->burst_duration, rtt_result->negotiated_burst_num);
    }

    putEventInCache(EVENT_TYPE_RTT_RESULTS, "RTT results");
}

static void onHotlistAPFound(wifi_request_id id, unsigned num_results, wifi_scan_result *results) {

    printMsg("Found hotlist APs\n");
    for (unsigned i = 0; i < num_results; i++) {
        printScanResult(results[i]);
    }
    putEventInCache(EVENT_TYPE_HOTLIST_AP_FOUND, "Found a hotlist AP");
}

static void onHotlistAPLost(wifi_request_id id, unsigned num_results, wifi_scan_result *results) {

    printMsg("Lost hotlist APs\n");
    for (unsigned i = 0; i < num_results; i++) {
        printScanResult(results[i]);
    }
    putEventInCache(EVENT_TYPE_HOTLIST_AP_LOST, "Lost event Hotlist APs");
}

static void onePnoSsidFound(wifi_request_id id, unsigned num_results, wifi_scan_result *results) {

    printMsg("Found ePNO SSID\n");
    for (unsigned i = 0; i < num_results; i++) {
        printMsg("SSID %s, %02x:%02x:%02x:%02x:%02x:%02x, channel %d, rssi %d\n",
            results[i].ssid, results[i].bssid[0], results[i].bssid[1],
            results[i].bssid[2], results[i].bssid[3], results[i].bssid[4],
            results[i].bssid[5], results[i].channel, (signed char)results[i].rssi);
    }
    putEventInCache(EVENT_TYPE_EPNO_SSID, "Found ePNO SSID");
}

static void onRssiThresholdbreached(wifi_request_id id, u8 *cur_bssid, s8 cur_rssi) {

    printMsg("RSSI threshold breached, cur RSSI - %d!!\n", cur_rssi);
    printMsg("BSSID %02x:%02x:%02x:%02x:%02x:%02x\n",
                cur_bssid[0], cur_bssid[1], cur_bssid[2],
                cur_bssid[3], cur_bssid[4], cur_bssid[5]);
    putEventInCache(EVENT_TYPE_RSSI_MONITOR, "RSSI monitor Event");
}


static const u8 *bss_get_ie(u8 id, const char* ie, const s32 ie_len)
{
    const u8 *end, *pos;

    pos = (const u8 *)ie;
    end = pos + ie_len;

    while (pos + 1 < end) {
        if (pos + 2 + pos[1] > end)
            break;
        if (pos[0] == id)
            return pos;
        pos += 2 + pos[1];
    }

    return NULL;
}
static bool is11mcAP(const char* ie, const s32 ie_len)
{
    const u8 *ext_cap_ie, *ptr_ie;
    u8 ext_cap_length = 0;
    ptr_ie = bss_get_ie(WIFI_EID_EXT_CAPAB, ie, ie_len);
    if (ptr_ie) {
        ext_cap_length = *(ptr_ie + TLV_LEN_OFF);
        ext_cap_ie = ptr_ie + TLV_BODY_OFF;
        if ((ext_cap_length >= CEIL(DOT11_EXT_CAP_FTM_RESPONDER, NBBY)) &&
                (isset(ext_cap_ie, DOT11_EXT_CAP_FTM_RESPONDER) ||
                 isset(ext_cap_ie, DOT11_EXT_CAP_FTM_INITIATOR))) {
            return true;
        }
    }
    return false;
}

#define CHAN_FACTOR_6_G 11900u   /* 6   GHz band, 5950 MHz */
int channel2mhz_6g(uint ch)
{
     int freq;
     freq = (ch * 5) + (CHAN_FACTOR_6_G / 2);
     return freq;
}

int channel2mhz(uint ch)
{
    int freq;
    int start_factor = (ch > 14)? CHAN_FACTOR_5_G : CHAN_FACTOR_2_4_G;
    if ((start_factor == CHAN_FACTOR_2_4_G && (ch < 1 || ch > 14)) ||
            (ch > 200))
        freq = -1;
    else if ((start_factor == CHAN_FACTOR_2_4_G) && (ch == 14))
        freq = 2484;
    else
        freq = ch * 5 + start_factor / 2;

    return freq;
}

struct ht_op_ie *read_ht_oper_ie(const char* ie, const s32 ie_len)
{
    const u8 *ptr_ie;
    ptr_ie = bss_get_ie(WIFI_EID_HT_OPERATION, ie, ie_len);
    if (ptr_ie) {
        return (struct ht_op_ie *)(ptr_ie + TLV_BODY_OFF);
    }
    return NULL;
}

struct vht_op_ie *read_vht_oper_ie(const char* ie, const s32 ie_len)
{
    const u8 *ptr_ie;
    ptr_ie = bss_get_ie(WIFI_EID_VHT_OPERATION, ie, ie_len);
    if (ptr_ie) {
        return (struct vht_op_ie *)(ptr_ie + TLV_BODY_OFF);
    }
    return NULL;
}

wifi_channel_info convert_channel(int ch, int chan_width, bool is_6g)
{
    wifi_channel_info chan_info;
    memset(&chan_info, 0, sizeof(wifi_channel_info));

    chan_info.width = (wifi_channel_width)chan_width;
    if (is_6g) {
        chan_info.center_freq = channel2mhz_6g(ch);
        return chan_info;
    } else {
        chan_info.center_freq = channel2mhz(ch);
    }
    if (chan_width == WIFI_CHAN_WIDTH_160) {
        if ((ch >= 36) && (ch <= 64))
            chan_info.center_freq0 = 5250;
        if ((ch >= 100) && (ch <= 128))
            chan_info.center_freq0 = 5570;
        if ((ch >= 149) && (ch <= 177))
            chan_info.center_freq0 = 5815;
    } else if (chan_width == WIFI_CHAN_WIDTH_80) {
        /*primary is the lowest channel*/
        if ((ch >= 36) && (ch <= 48))
            chan_info.center_freq0 = 5210;
        else if ((ch >= 52) && (ch <= 64))
            chan_info.center_freq0 = 5290;
        else if ((ch >= 100) && (ch <= 112))
            chan_info.center_freq0 = 5530;
        else if ((ch >= 116) && (ch <= 128))
            chan_info.center_freq0 = 5610;
        else if ((ch >= 132) && (ch <= 144))
            chan_info.center_freq0 = 5690;
        else if ((ch >= 149) && (ch <= 161))
            chan_info.center_freq0 = 5775;
    } else {
        if (chan_width == WIFI_CHAN_WIDTH_40) {
            if ((ch >= 36) && (ch <= 40))
                chan_info.center_freq0 = 5190;
            else if ((ch >= 44) && (ch <= 48))
                chan_info.center_freq0 = 5230;
            else if ((ch >= 52) && (ch <= 56))
                chan_info.center_freq0 = 5270;
            else if ((ch >= 60) && (ch <= 64))
                chan_info.center_freq0 = 5310;
            else if ((ch >= 100) && (ch <= 104))
                chan_info.center_freq0 = 5510;
            else if ((ch >= 108) && (ch <= 112))
                chan_info.center_freq0 = 5550;
            else if ((ch >= 116) && (ch <= 120))
                chan_info.center_freq0 = 5590;
            else if ((ch >= 124) && (ch <= 128))
                chan_info.center_freq0 = 5630;
            else if ((ch >= 132) && (ch <= 136))
                chan_info.center_freq0 = 5670;
            else if ((ch >= 140) && (ch <= 144))
                chan_info.center_freq0 = 5710;
            else if ((ch >= 149) && (ch <= 153))
                chan_info.center_freq0 = 5755;
            else if ((ch >= 157) && (ch <= 161))
                chan_info.center_freq0 = 5795;
        }
    }
    return chan_info;
}

wifi_channel_info get_channel_of_ie(const char* ie, const s32 ie_len)
{
    struct vht_op_ie *vht_op;
    struct ht_op_ie *ht_op;
    const u8 *ptr_ie;
    wifi_channel_info chan_info;
    memset(&chan_info, 0, sizeof(wifi_channel_info));
    vht_op = read_vht_oper_ie(ie, ie_len);
    if ((vht_op = read_vht_oper_ie(ie, ie_len)) &&
            (ht_op = read_ht_oper_ie(ie, ie_len))) {
        /* VHT mode */
        if (vht_op->chan_width == VHT_OP_CHAN_WIDTH_80) {
            chan_info.width = WIFI_CHAN_WIDTH_80;
            /* primary channel */
            chan_info.center_freq = channel2mhz(ht_op->ctl_ch);
            /* center frequency */
            chan_info.center_freq0 = channel2mhz(vht_op->chan1);
            return chan_info;
        }
    }
    if (ht_op = read_ht_oper_ie(ie, ie_len)){
        /* HT mode */
        /* control channel */
        chan_info.center_freq = channel2mhz(ht_op->ctl_ch);
        chan_info.width = WIFI_CHAN_WIDTH_20;
        switch (ht_op->chan_info & DOT11_EXT_CH_MASK) {
            chan_info.center_freq = channel2mhz(ht_op->ctl_ch);
            case DOT11_EXT_CH_UPPER:
                chan_info.width = WIFI_CHAN_WIDTH_40;
                break;
            case DOT11_EXT_CH_LOWER:
                chan_info.width = WIFI_CHAN_WIDTH_40;
                break;
            case DOT11_EXT_CH_NONE:
                break;
        }
    } else {
        chan_info.width = WIFI_CHAN_WIDTH_20;
        ptr_ie = bss_get_ie(WIFI_EID_DS_PARAMS, ie, ie_len);
        if (ptr_ie) {
            chan_info.center_freq = channel2mhz(ptr_ie[TLV_BODY_OFF]);
        }
    }
    return chan_info;
}

static void testRTT()
{
    wifi_scan_result *results[max_ap];
    wifi_scan_result *scan_param;
    u32 num_ap = 0;
    /*For STA-STA RTT */
    u32 num_sta = 0;
    int result = 0;
    /* Run by a provided rtt-ap-list file */
    FILE* w_fp = NULL;
    wifi_rtt_config params[max_ap];
    if (!rtt_from_file && !rtt_sta && !rtt_nan) {
        /* band filter for a specific band */
        if (band == WIFI_BAND_UNSPECIFIED)
            band = WIFI_BAND_ABG;
        int num_results = scanOnce(band, results, max_ap);
        if (num_results == 0) {
            printMsg("RTT aborted because of no scan results\n");
            return;
        } else {
            printMsg("Retrieved %d scan results\n", num_results);
        }

        num_results = removeDuplicateScanResults(results, num_results);

        sortScanResultsByRssi(results, num_results);
        printMsg("Sorted scan results -\n");
        for (int i = 0; i < num_results; i++) {
            printScanResult(*results[i]);
        }
        if (rtt_to_file) {
            /* Write a RTT AP list to a file */
            w_fp = fopen(rtt_aplist, "w");
            if (w_fp == NULL) {
                printMsg("failed to open the file : %s\n", rtt_aplist);
                return;
            }
            fprintf(w_fp, "|SSID|BSSID|Primary Freq|Center Freq|Channel BW(0=20MHZ,1=40MZ,2=80MHZ)"
                    "|rtt_type(1=1WAY,2=2WAY,3=auto)|Peer Type(STA=0, AP=1)|burst period|"
                    "Num of Burst|FTM retry count|FTMR retry count|LCI|LCR|Burst Duration|Preamble|BW\n");
        }
        for (int i = 0; i < min(num_results, max_ap); i++) {
            scan_param = results[i];
            if(is11mcAP(&scan_param->ie_data[0], scan_param->ie_length)) {
                memcpy(params[num_ap].addr, scan_param->bssid, sizeof(mac_addr));
                mac_addr &addr = params[num_ap].addr;
                printMsg("Adding %s(%02x:%02x:%02x:%02x:%02x:%02x) on Freq (%d) for 11mc RTT\n",
                        scan_param->ssid, addr[0], addr[1],
                        addr[2], addr[3], addr[4], addr[5],
                        scan_param->channel);
                params[num_ap].type = default_rtt_param.type;
                params[num_ap].channel = get_channel_of_ie(&scan_param->ie_data[0],
                        scan_param->ie_length);
                params[num_ap].peer = RTT_PEER_AP;
                params[num_ap].num_burst = default_rtt_param.num_burst;
                params[num_ap].num_frames_per_burst = default_rtt_param.num_frames_per_burst;
                params[num_ap].num_retries_per_rtt_frame =
                    default_rtt_param.num_retries_per_ftm;
                params[num_ap].num_retries_per_ftmr = default_rtt_param.num_retries_per_ftmr;
                params[num_ap].burst_period = default_rtt_param.burst_period;
                params[num_ap].burst_duration = default_rtt_param.burst_duration;
                params[num_ap].LCI_request = default_rtt_param.LCI_request;
                params[num_ap].LCR_request = default_rtt_param.LCR_request;
                params[num_ap].preamble = (wifi_rtt_preamble)default_rtt_param.preamble;
                params[num_ap].bw = (wifi_rtt_bw)default_rtt_param.bw;
                if (rtt_to_file) {
                    fprintf(w_fp, "%s %02x:%02x:%02x:%02x:%02x:%02x %d %d %d %d %d %d %d %d %d %d %d %d %d %d %d\n", scan_param->ssid,
                            params[num_ap].addr[0], params[num_ap].addr[1], params[num_ap].addr[2], params[num_ap].addr[3],
                            params[num_ap].addr[4], params[num_ap].addr[5],params[num_ap].channel.center_freq,
                            params[num_ap].channel.center_freq0, params[num_ap].channel.width, params[num_ap].type,params[num_ap].peer,
                            params[num_ap].burst_period, params[num_ap].num_burst, params[num_ap].num_frames_per_burst,
                            params[num_ap].num_retries_per_rtt_frame, params[num_ap].num_retries_per_ftmr,
                            params[num_ap].LCI_request, params[num_ap].LCR_request, params[num_ap].burst_duration,
                            params[num_ap].preamble, params[num_ap].bw);
                }
                num_ap++;
            } else {
                /* legacy AP */
            }
        }
        /* free the results data */
        for (int i = 0; i < num_results; i++) {
            free(results[i]);
            results[i] = NULL;
        }
        if (w_fp)
            fclose(w_fp);
    } else if (rtt_sta || rtt_nan) {
        printf(" Run initiator rtt sta/nan, rtt_sta = %d, rtt_nan = %d \n",
            rtt_sta, rtt_nan);
        /* As we have only one target */
        memcpy(params[num_sta].addr, responder_addr, sizeof(mac_addr));
        params[num_sta].channel = convert_channel(responder_channel, channel_width, is_6g);
        printMsg("Adding(" MACSTR ") on Freq (%d),(%d) for 11mc RTT\n",
                 MAC2STR(responder_addr),
                 params[num_sta].channel.center_freq,
                 params[num_sta].channel.center_freq0);
        /*As we are doing STA-STA RTT */
        params[num_sta].type = default_rtt_param.type;
        if (rtt_nan) {
            params[num_sta].peer = RTT_PEER_NAN;
        } else if (rtt_sta) {
            params[num_sta].peer = RTT_PEER_STA;
        }
        params[num_sta].num_burst = default_rtt_param.num_burst;
        params[num_sta].num_frames_per_burst = default_rtt_param.num_frames_per_burst;
        params[num_sta].num_retries_per_rtt_frame =
            default_rtt_param.num_retries_per_ftm;
        params[num_sta].num_retries_per_ftmr = default_rtt_param.num_retries_per_ftmr;
        params[num_sta].burst_period = default_rtt_param.burst_period;
        params[num_sta].burst_duration = default_rtt_param.burst_duration;
        params[num_sta].LCI_request = default_rtt_param.LCI_request;
        params[num_sta].LCR_request = default_rtt_param.LCR_request;
        params[num_sta].preamble = (wifi_rtt_preamble)default_rtt_param.preamble;
        params[num_sta].bw = (wifi_rtt_bw)default_rtt_param.bw;
        num_sta++;

    } else {

        /* Run by a provided rtt-ap-list file */
        FILE* fp;
        char bssid[ETHER_ADDR_STR_LEN];
        char ssid[MAX_SSID_LEN];
        char first_char;
        memset(bssid, 0, sizeof(bssid));
        memset(ssid, 0, sizeof(ssid));
        /* Read a RTT AP list from a file */
        fp = fopen(rtt_aplist, "r");
        if (fp == NULL) {
            printMsg("\nRTT AP list file does not exist on %s.\n"
                    "Please specify correct full path or use default one, %s, \n"
                    "  by following order in file, such as:\n"
                    "|SSID|BSSID|chan_num|Channel BW(0=20MHZ,1=40MZ,2=80MHZ)|"
                    "RTT_Type(1=1WAY,2=2WAY,3=auto)|Peer Type(STA=0, AP=1)|Burst Period|"
                    "No of Burst|No of FTM Burst|FTM Retry Count|FTMR Retry Count|LCI|LCR|"
                    "Burst Duration|Preamble|Bandwith\n",
                    rtt_aplist, DEFAULT_RTT_FILE);
            return;
        }
        printMsg("    %-16s%-20s%-8s%-12s%-10s%-10s%-16s%-10s%-14s%-11s%-12s%-5s%-5s%-15s%-10s\n",
                "SSID", "BSSID", "chan", "Bandwidth", "RTT_Type", "RTT_Peer",
                "Burst_Period", "No_Burst", "No_FTM_Burst", "FTM_Retry",
                "FTMR_Retry", "LCI", "LCR", "Burst_duration", "Preamble", "Bandwidth");
        int i = 0;
        while (!feof(fp)) {
            if ((fscanf(fp, "%c", &first_char) == 1) && (first_char != '|')) {
                fseek(fp, -1, SEEK_CUR);
                result = fscanf(fp, "%s %s %u %u %u %u %u %u %u %u %u %hhu %hhu %u %hhu %hhu\n",
                        ssid, bssid, (unsigned int*)&responder_channel,
                        (unsigned int*)&channel_width,
                        (unsigned int*)&params[i].type, (unsigned int*)&params[i].peer,
                        &params[i].burst_period, &params[i].num_burst,
                        &params[i].num_frames_per_burst,
                        &params[i].num_retries_per_rtt_frame,
                        &params[i].num_retries_per_ftmr,
                        (unsigned char*)&params[i].LCI_request,
                        (unsigned char*)&params[i].LCR_request,
                        (unsigned int*)&params[i].burst_duration,
                        (unsigned char*)&params[i].preamble,
                        (unsigned char*)&params[i].bw);

                if (result != 16) {
                    printMsg("fscanf failed %d\n", result);
                    break;
                }
                params[i].channel = convert_channel(responder_channel, channel_width, is_6g);
                parseMacAddress(bssid, params[i].addr);

                printMsg("[%d] %-16s%-20s%-8u%-14u%-12d%-10d%-10u%-16u%-10u%-14u%-11u%-12u%-5hhu%-5hhu%-15u%-10hhu-10hhu\n",
                        i+1, ssid, bssid, params[i].channel.center_freq,
                        params[i].channel.center_freq0, params[i].channel.width,
                        params[i].type, params[i].peer, params[i].burst_period,
                        params[i].num_burst, params[i].num_frames_per_burst,
                        params[i].num_retries_per_rtt_frame,
                        params[i].num_retries_per_ftmr, params[i].LCI_request,
                        params[i].LCR_request, params[i].burst_duration, params[i].preamble, params[i].bw);

                i++;
            } else {
                /* Ignore the rest of the line. */
                result = fscanf(fp, "%*[^\n]");
                if (result != 1) {
                    printMsg("fscanf failed %d\n", result);
                    break;
                }

                result = fscanf(fp, "\n");
                if (result != 1) {
                    printMsg("fscanf failed %d\n", result);
                    break;
                }
            }
        }
        num_ap = i;
        fclose(fp);
        fp = NULL;
    }

    wifi_rtt_event_handler handler;
    handler.on_rtt_results = &onRTTResults;
    if (!rtt_to_file || rtt_sta || rtt_nan)  {
        if (num_ap || num_sta) {
            if (num_ap) {
                printMsg("Configuring RTT for %d APs\n", num_ap);
                result = hal_fn.wifi_rtt_range_request(rttCmdId, wlan0Handle, num_ap, params, handler);
            } else if (num_sta) {
                printMsg("Configuring RTT for %d sta \n", num_sta);
                result = hal_fn.wifi_rtt_range_request(rttCmdId, wlan0Handle, num_sta, params, handler);
            }

            if (result == WIFI_SUCCESS) {
                printMsg("\nWaiting for RTT results\n");
                while (true) {
                    EventInfo info;
                    memset(&info, 0, sizeof(info));
                    getEventFromCache(info);
                    if (info.type == EVENT_TYPE_RTT_RESULTS ||
                        info.type == EVENT_TYPE_RTT_RESULTS_DETAIL) {
                        break;
                    }
                }
            } else {
                printMsg("Could not set setRTTAPs : %d\n", result);
            }
        } else {
            printMsg("no candidate for RTT\n");
        }
    } else {
        printMsg("written AP info into file %s successfully\n", rtt_aplist);
    }
}

static int cancelRTT()
{
    int ret;
    ret = hal_fn.wifi_rtt_range_cancel(rttCmdId, wlan0Handle, 0, NULL);
    if (ret == WIFI_SUCCESS) {
        printMsg("Successfully cancelled the RTT\n");
    }
    return ret;
}

static void getRTTCapability()
{
    int ret;
    wifi_rtt_capabilities rtt_capability;
    ret = hal_fn.wifi_get_rtt_capabilities(wlan0Handle, &rtt_capability);
    if (ret == WIFI_SUCCESS) {
        printMsg("Supported Capabilites of RTT :\n");
        if (rtt_capability.rtt_one_sided_supported)
            printMsg("One side RTT is supported\n");
        if (rtt_capability.rtt_ftm_supported)
            printMsg("FTM(11mc) RTT is supported\n");
        if (rtt_capability.lci_support)
            printMsg("LCI is supported\n");
        if (rtt_capability.lcr_support)
            printMsg("LCR is supported\n");
        if (rtt_capability.bw_support) {
            printMsg("BW(%s %s %s %s) are supported\n",
                    (rtt_capability.bw_support & BW_20_SUPPORT) ? "20MHZ" : "",
                    (rtt_capability.bw_support & BW_40_SUPPORT) ? "40MHZ" : "",
                    (rtt_capability.bw_support & BW_80_SUPPORT) ? "80MHZ" : "",
                    (rtt_capability.bw_support & BW_160_SUPPORT) ? "160MHZ" : "");
        }
        if (rtt_capability.preamble_support) {
            printMsg("Preamble(%s %s %s) are supported\n",
                    (rtt_capability.preamble_support & PREAMBLE_LEGACY) ? "Legacy" : "",
                    (rtt_capability.preamble_support & PREAMBLE_HT) ? "HT" : "",
                    (rtt_capability.preamble_support & PREAMBLE_VHT) ? "VHT" : "");

        }
    } else {
        printMsg("Could not get the rtt capabilities : %d\n", ret);
    }

}

/* TWT related apis */
static void setupTwtRequest(char *argv[]) {
    TwtSetupRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *endptr, *param, *val_p;

    /* Set Default twt setup request params */
    memset(&msg, 0, sizeof(msg));

    /* Parse args for twt params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-config_id") == 0) {
            msg.config_id = atoi(val_p);
        } else if (strcmp(param, "-neg_type") == 0) {
            msg.negotiation_type = atoi(val_p);
        } else if (strcmp(param, "-trigger_type") == 0) {
            msg.trigger_type = atoi(val_p);
        } else if (strcmp(param, "-wake_dur_us") == 0) {
            msg.wake_dur_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_int_us") == 0) {
            msg.wake_int_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_int_min_us") == 0) {
            msg.wake_int_min_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_int_max_us") == 0) {
            msg.wake_int_max_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_dur_min_us") == 0) {
            msg.wake_dur_min_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_dur_max_us") == 0) {
            msg.wake_dur_max_us = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-avg_pkt_size") == 0) {
            msg.avg_pkt_size = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-avg_pkt_num") == 0) {
            msg.avg_pkt_num = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-wake_time_off_us") == 0) {
            msg.wake_time_off_us = strtoul(val_p, &endptr, 0);
        } else {
            printMsg("%s:Unsupported Parameter for twt setup request\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ret = twt_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize twt handlers %d\n", ret);
        goto exit;
    }
    ret = twt_setup_request(wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void TeardownTwt(char *argv[]) {
    TwtTeardownRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param, *val_p;

    /* Set Default twt teardown params */
    memset(&msg, 0, sizeof(msg));

    /* Parse args for twt params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-config_id") == 0) {
            msg.config_id = atoi(val_p);
        } else if (strcmp(param, "-all_twt") == 0) {
            msg.all_twt = atoi(val_p);
        } else if (strcmp(param, "-neg_type") == 0) {
            msg.negotiation_type = atoi(val_p);
        } else {
            printMsg("%s:Unsupported Parameter for twt teardown request\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ret = twt_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize twt handlers %d\n", ret);
        goto exit;
    }
    ret = twt_teardown_request(wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void InfoFrameTwt(char *argv[]) {
    TwtInfoFrameRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param, *val_p;

    /* Set Default twt info frame params */
    memset(&msg, 0, sizeof(msg));

    /* Parse args for twt params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-config_id") == 0) {
            msg.config_id = atoi(val_p);
        } else if (strcmp(param, "-all_twt") == 0) {
            msg.all_twt = atoi(val_p);
        } else if (strcmp(param, "-resume_time_us") == 0) {
            msg.resume_time_us = atoi(val_p);
        } else {
            printMsg("%s:Unsupported Parameter for twt info request\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ret = twt_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize twt handlers %d\n", ret);
        goto exit;
    }
    ret = twt_info_frame_request(wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void GetTwtStats(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param, *val_p;
    u8 config_id = 1;
    TwtStats twt_stats;

    /* Parse args for twt params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-config_id") == 0) {
            config_id = atoi(val_p);
        } else {
            printMsg("%s:Unsupported Parameter for get stats request\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    memset(&twt_stats, 0, sizeof(twt_stats));
    ret = twt_get_stats(wlan0Handle, config_id, &twt_stats);
    if (ret == WIFI_SUCCESS) {
        printMsg("TWT stats :\n");
        if (twt_stats.config_id)
            printMsg("config id = %d\n", twt_stats.config_id);
        if (twt_stats.avg_pkt_num_tx)
            printMsg("avg_pkt_num_tx = %d\n", twt_stats.avg_pkt_num_tx);
        if (twt_stats.avg_pkt_num_rx)
            printMsg("avg_pkt_num_rx = %d\n", twt_stats.avg_pkt_num_rx);
        if (twt_stats.avg_tx_pkt_size)
            printMsg("avg_tx_pkt_size = %d\n", twt_stats.avg_tx_pkt_size);
        if (twt_stats.avg_rx_pkt_size)
            printMsg("avg_rx_pkt_size = %d\n", twt_stats.avg_rx_pkt_size);
        if (twt_stats.avg_eosp_dur_us)
            printMsg("avg_eosp_dur_us = %d\n", twt_stats.avg_eosp_dur_us);
        if (twt_stats.eosp_count)
            printMsg("eosp_count = %d\n", twt_stats.eosp_count);

        return;
    }
exit:
    printMsg("Could not get the twt stats : err %d\n", ret);
    return;
}

void ClearTwtStats(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param, *val_p;
    u8 config_id = 1;

    /* Parse args for twt params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-config_id") == 0) {
            config_id = atoi(val_p);
        } else {
            printMsg("%s:Unsupported Parameter for twt info request\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ret = twt_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize twt handlers %d\n", ret);
        goto exit;
    }
    ret = twt_clear_stats(wlan0Handle, config_id);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void getTWTCapability() {
    int ret;
    TwtCapabilitySet twt_capability;

    ret = twt_get_capability(wlan0Handle, &twt_capability);
    if (ret == WIFI_SUCCESS) {
        printMsg("Supported Capabilites of TWT :\n");
        if (twt_capability.device_capability.requester_supported)
            printMsg("Device Requester supported\n");
        if (twt_capability.device_capability.responder_supported)
            printMsg("Device Responder supported\n");
        if (twt_capability.device_capability.broadcast_twt_supported)
            printMsg("Device Broadcast twt supported\n");
        if (twt_capability.device_capability.flexibile_twt_supported)
            printMsg("Device Flexibile twt supported\n");
        if (twt_capability.peer_capability.requester_supported)
            printMsg("Peer Requester supported\n");
        if (twt_capability.peer_capability.responder_supported)
            printMsg("Peer Responder supported\n");
        if (twt_capability.peer_capability.broadcast_twt_supported)
            printMsg("Peer Broadcast twt supported\n");
        if (twt_capability.peer_capability.flexibile_twt_supported)
            printMsg("Peer Flexibile twt supported\n");
    } else {
        printMsg("Could not get the twt capabilities : %d\n", ret);
    }
    return;
}

static void showResponderCapability(wifi_rtt_responder responder_info)
{
    wifi_channel_info channel_info;
    channel_info = responder_info.channel;
    printMsg("Centre freq = %d \n",channel_info.center_freq);
    if (channel_info.width == WIFI_CHAN_WIDTH_20) {
        printMsg("channel width = 20 \n");
    } else if (channel_info.width == WIFI_CHAN_WIDTH_40) {
        printMsg("channel width = 40 \n");
    } else if (channel_info.width == WIFI_CHAN_WIDTH_80) {
        printMsg("channel width = 80 \n");
    }
    if (channel_info.width == WIFI_CHAN_WIDTH_40 || channel_info.width == WIFI_CHAN_WIDTH_80) {
        printMsg("CentreFreq0 = %d \n",channel_info.center_freq0);
    }
    if (responder_info.preamble & WIFI_RTT_PREAMBLE_HT) {
        printMsg("Responder preamble = %d \n",responder_info.preamble);
    }
    if (responder_info.preamble & WIFI_RTT_PREAMBLE_VHT) {
        printMsg("Responder preamble = %d \n",responder_info.preamble);
    }
    if (responder_info.preamble & WIFI_RTT_PREAMBLE_LEGACY) {
        printMsg("Responder preamble = %d \n",responder_info.preamble);
    }
}

static int getRttResponderInfo()
{
    int ret;
    wifi_rtt_responder responder_info;
    ret = hal_fn.wifi_rtt_get_responder_info(wlan0Handle, &responder_info);
    if (ret == WIFI_SUCCESS) {
        showResponderCapability(responder_info);
    }
    return ret;
}

static int RttEnableResponder()
{
    int ret = 0;
    wifi_request_id id = 0;
    wifi_channel_info channel_hint;
    memset(&channel_hint, 0, sizeof(wifi_channel_info));
    wifi_rtt_responder responder_info;
    unsigned int max_duration_sec = 0;

    ret = hal_fn.wifi_enable_responder(id, wlan0Handle, channel_hint,
        max_duration_sec, &responder_info);
    if (ret == WIFI_SUCCESS) {
        showResponderCapability(responder_info);
    }
    return ret;
}

static int cancelRttResponder()
{
    int ret = 0;
    wifi_request_id id = 0;

    ret = hal_fn.wifi_disable_responder(id, wlan0Handle);
    return ret;
}

/* CHRA NAN RTT related */
static void OnChreNanRttStateChanged(chre_nan_rtt_state state) {
    printMsg("CHRE NAN RTT state update : %d\n", state);
    putEventInCache(EVENT_TYPE_CHRE_NAN_RTT_STATE_UPDATED, "CHRE NAN RTT state updated");
}

static void enableChreNanRtt() {
    wifi_error ret = WIFI_SUCCESS;
    ret = hal_fn.wifi_nan_rtt_chre_enable_request(0, wlan0Handle, NULL);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to enable CHRE NAN RTT: %d\n", ret);
    }

    return;
}

static void disableChreNanRtt() {
    wifi_error ret = WIFI_SUCCESS;
    ret = hal_fn.wifi_nan_rtt_chre_disable_request(0, wlan0Handle);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to disable CHRE NAN RTT: %d\n", ret);
    }

    return;
}

static void registerChreCallback() {
    wifi_error ret = WIFI_SUCCESS;
    EventInfo info;
    wifi_chre_handler handler;
    handler.on_chre_nan_rtt_change = OnChreNanRttStateChanged;
    ret = hal_fn.wifi_chre_register_handler(wlan0Handle, handler);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to register CHRE callback: %d\n", ret);
    } else {
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_CHRE_NAN_RTT_STATE_UPDATED) {
                printMsg("Received CHRE NAN RTT state, end the CHRE NAN RTT monitor!!\n");
                break;
            }
        }
    }
    return;
}

static void printCachedScanResults(wifi_cached_scan_report *cache_report) {
    int scanned_channel[MAX_CH_BUF_SIZE] = {0};
    wifi_cached_scan_result cached_results[MAX_CACHED_SCAN_RESULT];
    memset(&cached_results, 0, sizeof(cached_results));

    if (cache_report->ts) {
        printMsg("Printing scan results were queried at (%lu) (in microseconds):\n",
            cache_report->ts);
    }
    printMsg("--------------------------------------\n");
    if (cache_report->scanned_freq_num > MAX_CH_BUF_SIZE ) {
        cache_report->scanned_freq_num = MAX_CH_BUF_SIZE;
    }

    if (cache_report->scanned_freq_num && cache_report->scanned_freq_list) {
        memcpy(scanned_channel, cache_report->scanned_freq_list,
            cache_report->scanned_freq_num * sizeof(u32));
    }

    if (cache_report->result_cnt > MAX_CACHED_SCAN_RESULT) {
         cache_report->result_cnt = MAX_CACHED_SCAN_RESULT;
    }

    if (cache_report->result_cnt && cache_report->results) {
        memcpy(cached_results, cache_report->results,
           cache_report->result_cnt * sizeof(wifi_cached_scan_result));
    }

    printMsg("(%d) channels were scanned:\n", cache_report->scanned_freq_num);
    for (int i = 0; i < cache_report->scanned_freq_num; i++) {
         printMsg("%d, ", scanned_channel[i]);
    }
    printMsg("\n");
    printMsg("(%d) results reported:\n", cache_report->result_cnt);
    for (int i = 0; i < cache_report->result_cnt; i++) {
        printMsg("ssid:%s,bssid: %02x:%02x:%02x:%02x:%02x:%02x,"
                "rssi: %d, primary_freq: %d, bw: %d, capability: 0x%x,"
                "flags: 0x%x, age_ms: %d\n",
                cached_results[i].ssid,
                cached_results[i].bssid[0], cached_results[i].bssid[1],
                cached_results[i].bssid[2], cached_results[i].bssid[3],
                cached_results[i].bssid[4], cached_results[i].bssid[5],
                cached_results[i].rssi,
                cached_results[i].chanspec.primary_frequency,
                cached_results[i].chanspec.width,
                cached_results[i].capability, cached_results[i].flags,
                cached_results[i].age_ms);
    }
}

static void on_cached_scan_results(wifi_cached_scan_report *cache_report) {
    if (!cache_report) {
        printf("Scan results not found! Issue scan first\n");
        return;
    }

    printf("onCachedScanResult scanned_freq_num = %d result_cnt %d \n",
            cache_report->scanned_freq_num, cache_report->result_cnt);
    printCachedScanResults(cache_report);
}

static void getWifiCachedScanResults(void) {
    wifi_cached_scan_result_handler handler;
    handler.on_cached_scan_results = on_cached_scan_results;

    wifi_error ret = hal_fn.wifi_get_cached_scan_results(wlan0Handle, handler);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to get cached scan results: %d\n", ret);
    }
    return;
}

static int GetCachedGScanResults(int max, wifi_scan_result *results, int *num)
{
    int num_results = 64;
    wifi_cached_scan_results *results2;
    results2 = (wifi_cached_scan_results *)malloc(num_results * sizeof(wifi_cached_scan_results));
    memset(results2, 0, sizeof(wifi_cached_scan_results) * num_results);
    int ret = hal_fn.wifi_get_cached_gscan_results(wlan0Handle, 1, num_results, results2,
        &num_results);
    if (ret < 0) {
        printMsg("failed to fetch scan results : %d\n", ret);
        goto exit;
    } else {
        printMsg("fetched %d scan data\n", num_results);
    }

    *num = 0;
    for (int i = 0; i < num_results; i++) {
        for (int j = 0; j < results2[i].num_results; j++, (*num)++) {
            memcpy(&(results[*num]), &(results2[i].results[j]), sizeof(wifi_scan_result));
        }
    }

exit:
    if (results2) {
        free(results2);
    }
    return ret;
}


static wifi_error setHotlistAPsUsingScanResult(wifi_bssid_hotlist_params *params)
{
    printMsg("testHotlistAPs Scan started, waiting for event ...\n");
    EventInfo info;
    memset(&info, 0, sizeof(info));
    getEventFromCache(info);

    wifi_scan_result *results;
    results = (wifi_scan_result *)malloc(256 * sizeof(wifi_scan_result));
    memset(results, 0, sizeof(wifi_scan_result) * 256);

    printMsg("Retrieving scan results for Hotlist AP setting\n");
    int num_results = 256;
    int result = GetCachedGScanResults(num_results, results, &num_results);
    if (result < 0) {
        printMsg("failed to fetch scan results : %d\n", result);
        if (results) {
            free(results);
        }
        return WIFI_ERROR_UNKNOWN;
    } else {
        printMsg("fetched %d scan results\n", num_results);
    }

    for (int i = 0; i < num_results; i++) {
        printScanResult(results[i]);
    }

    for (int i = 0; i < stest_max_ap; i++) {
        memcpy(params->ap[i].bssid, results[i].bssid, sizeof(mac_addr));
        params->ap[i].low  = -htest_low_threshold;
        params->ap[i].high = -htest_high_threshold;
    }
    params->num_bssid = stest_max_ap;

    if (results) {
        free(results);
    }

    return WIFI_SUCCESS;
}

static wifi_error setHotlistAPs() {
    wifi_bssid_hotlist_params params;
    memset(&params, 0, sizeof(params));

    params.lost_ap_sample_size = HOTLIST_LOST_WINDOW;
    if (num_hotlist_bssids > 0) {
        for (int i = 0; i < num_hotlist_bssids; i++) {
            memcpy(params.ap[i].bssid, hotlist_bssids[i], sizeof(mac_addr));
            params.ap[i].low  = -htest_low_threshold;
            params.ap[i].high = -htest_high_threshold;
        }
        params.num_bssid = num_hotlist_bssids;
    } else {
        setHotlistAPsUsingScanResult(&params);
    }

    printMsg("BSSID\t\t\tHIGH\tLOW\n");
    for (int i = 0; i < params.num_bssid; i++) {
        mac_addr &addr = params.ap[i].bssid;
        printMsg("%02x:%02x:%02x:%02x:%02x:%02x\t%d\t%d\n", addr[0],
                addr[1], addr[2], addr[3], addr[4], addr[5],
                params.ap[i].high, params.ap[i].low);
    }

    wifi_hotlist_ap_found_handler handler;
    handler.on_hotlist_ap_found = &onHotlistAPFound;
    handler.on_hotlist_ap_lost = &onHotlistAPLost;
    hotlistCmdId = getNewCmdId();
    printMsg("Setting hotlist APs threshold\n");
    return hal_fn.wifi_set_bssid_hotlist(hotlistCmdId, wlan0Handle, params, handler);
}

static void resetHotlistAPs() {
    printMsg(", stoping Hotlist AP scanning\n");
    hal_fn.wifi_reset_bssid_hotlist(hotlistCmdId, wlan0Handle);
}

static void setPnoMacOui() {
    hal_fn.wifi_set_scanning_mac_oui(wlan0Handle, mac_oui);
}

static void testHotlistAPs(){

    EventInfo info;
    memset(&info, 0, sizeof(info));

    printMsg("starting Hotlist AP scanning\n");
    bool startScanResult = startScan(stest_max_ap,
            stest_base_period, stest_threshold_percent, stest_threshold_num_scans);
    if (!startScanResult) {
        printMsg("testHotlistAPs failed to start scan!!\n");
        return;
    }

    int result = setHotlistAPs();
    if (result == WIFI_SUCCESS) {
        printMsg("Waiting for Hotlist AP event\n");
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);

            if (info.type == EVENT_TYPE_SCAN_COMPLETE) {
                retrieveScanResults();
            } else if (info.type == EVENT_TYPE_HOTLIST_AP_FOUND ||
                    info.type == EVENT_TYPE_HOTLIST_AP_LOST) {
                printMsg("Hotlist APs");
                if (--max_event_wait > 0)
                    printMsg(", waiting for more event ::%d\n", max_event_wait);
                else
                    break;
            }
        }
        resetHotlistAPs();
    } else {
        printMsg("Could not set AP hotlist : %d\n", result);
    }
}

static void testPNO(bool clearOnly, bool scan){

    EventInfo info;
    int result;
    wifi_epno_handler handler;
    handler.on_network_found = &onePnoSsidFound;
    memset(&info, 0, sizeof(info));
    if (clearOnly) {
        result = wifi_reset_epno_list(-1, wlan0Handle);
        if (result != WIFI_SUCCESS) {
            printMsg("Failed to reset ePNO!!\n");
        }
        return;
    }
    epnoCmdId = getNewCmdId();
    printMsg("configuring ePNO SSIDs num %u\n", epno_cfg.num_networks);
    result = hal_fn.wifi_set_epno_list(epnoCmdId, wlan0Handle, &epno_cfg, handler);
    if (result == WIFI_SUCCESS && scan) {
        bool startScanResult = startScan(stest_max_ap,
            stest_base_period, stest_threshold_percent, stest_threshold_num_scans);
        if (!startScanResult) {
            printMsg("testPNO failed to start scan!!\n");
            return;
        }
        printMsg("Waiting for ePNO events\n");
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);

            if (info.type == EVENT_TYPE_SCAN_COMPLETE) {
                retrieveScanResults();
            } else if (info.type == EVENT_TYPE_EPNO_SSID) {
                printMsg("FOUND ePNO event");
                if (--max_event_wait > 0)
                  printMsg(", waiting for more event ::%d\n", max_event_wait);
                else
                  break;
            }
        }
        //wifi_reset_epno_list(epnoCmdId, wlan0Handle);
    } else if (result != WIFI_SUCCESS) {
        printMsg("Could not set ePNO : %d\n", result);
    }
}

static void onSignificantWifiChange(wifi_request_id id,
        unsigned num_results, wifi_significant_change_result **results)
{
    printMsg("Significant wifi change for %d\n", num_results);
    for (unsigned i = 0; i < num_results; i++) {
        printSignificantChangeResult(results[i]);
    }
    putEventInCache(EVENT_TYPE_SIGNIFICANT_WIFI_CHANGE, "significant wifi change noticed");
}

static int SelectSignificantAPsFromScanResults() {
    wifi_scan_result *results;
    results = (wifi_scan_result *)malloc(256 * sizeof(wifi_scan_result));
    memset(results, 0, sizeof(wifi_scan_result) * 256);
    printMsg("Retrieving scan results for significant wifi change setting\n");
    int num_results = 256;
    int result = GetCachedGScanResults(num_results, results, &num_results);
    if (result < 0) {
        printMsg("failed to fetch scan results : %d\n", result);
        if (results) {
            free(results);
        }
        return WIFI_ERROR_UNKNOWN;
    } else {
        printMsg("fetched %d scan results\n", num_results);
    }

    for (int i = 0; i < num_results; i++) {
        printScanResult(results[i]);
    }

    wifi_significant_change_params params;
    memset(&params, 0, sizeof(params));

    params.rssi_sample_size = swctest_rssi_sample_size;
    params.lost_ap_sample_size = swctest_rssi_lost_ap;
    params.min_breaching = swctest_rssi_min_breaching;

    for (int i = 0; i < stest_max_ap; i++) {
        memcpy(params.ap[i].bssid, results[i].bssid, sizeof(mac_addr));
        params.ap[i].low  = results[i].rssi - swctest_rssi_ch_threshold;
        params.ap[i].high = results[i].rssi + swctest_rssi_ch_threshold;
    }
    params.num_bssid = stest_max_ap;

    printMsg("Settting Significant change params rssi_sample_size#%d lost_ap_sample_size#%d"
            " and min_breaching#%d\n", params.rssi_sample_size,
            params.lost_ap_sample_size , params.min_breaching);
    printMsg("BSSID\t\t\tHIGH\tLOW\n");
    for (int i = 0; i < params.num_bssid; i++) {
        mac_addr &addr = params.ap[i].bssid;
        printMsg("%02x:%02x:%02x:%02x:%02x:%02x\t%d\t%d\n", addr[0],
                addr[1], addr[2], addr[3], addr[4], addr[5],
                params.ap[i].high, params.ap[i].low);
    }
    wifi_significant_change_handler handler;
    memset(&handler, 0, sizeof(handler));
    handler.on_significant_change = &onSignificantWifiChange;

    int id = getNewCmdId();
    if (results) {
        free(results);
    }
    return hal_fn.wifi_set_significant_change_handler(id, wlan0Handle, params, handler);

}

static void untrackSignificantChange() {
    printMsg(", Stop tracking SignificantChange\n");
    hal_fn.wifi_reset_bssid_hotlist(hotlistCmdId, wlan0Handle);
}

static void trackSignificantChange() {
    printMsg("starting trackSignificantChange\n");

    if (!startScan(stest_max_ap,
                stest_base_period, stest_threshold_percent, stest_threshold_num_scans)) {
        printMsg("trackSignificantChange failed to start scan!!\n");
        return;
    } else {
        printMsg("trackSignificantChange Scan started, waiting for event ...\n");
    }

    EventInfo info;
    memset(&info, 0, sizeof(info));
    getEventFromCache(info);

    int result = SelectSignificantAPsFromScanResults();
    if (result == WIFI_SUCCESS) {
        printMsg("Waiting for significant wifi change event\n");
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);

            if (info.type == EVENT_TYPE_SCAN_COMPLETE) {
                retrieveScanResults();
            } else if(info.type == EVENT_TYPE_SIGNIFICANT_WIFI_CHANGE) {
                printMsg("Received significant wifi change");
                if (--max_event_wait > 0)
                    printMsg(", waiting for more event ::%d\n", max_event_wait);
                else
                    break;
            }
        }
        untrackSignificantChange();
    } else {
        printMsg("Failed to set significant change  ::%d\n", result);
    }
}


void testScan() {
    printf("starting scan with max_ap_per_scan#%d  base_period#%d  threshold#%d \n",
            stest_max_ap,stest_base_period, stest_threshold_percent);
    if (!startScan(stest_max_ap,
                stest_base_period, stest_threshold_percent, stest_threshold_num_scans)) {
        printMsg("failed to start scan!!\n");
        return;
    } else {
        EventInfo info;
        memset(&info, 0, sizeof(info));

        while (true) {
            getEventFromCache(info);
            printMsg("retrieved event %d : %s\n", info.type, info.buf);
            if (info.type == EVENT_TYPE_SCAN_COMPLETE)
                continue;
            retrieveScanResults();
            if(--max_event_wait > 0)
                printMsg("Waiting for more :: %d event \n", max_event_wait);
            else
                break;
        }

        stopScan();
        printMsg("stopped scan\n");
    }
}

void testStopScan() {
    stopScan();
    printMsg("stopped scan\n");
}


///////////////////////////////////////////////////////////////////////////////
// Logger feature set

static void onRingBufferData(char *ring_name, char *buffer, int buffer_size,
                                wifi_ring_buffer_status *status)
{
    // helper for LogHandler

    static int cnt = 1;
    FILE* w_fp;
    static int f_count = 0;
    char ring_file[FILE_NAME_LEN];
    char *pBuff;

    if (!buffer || buffer_size <= 0) {
        printMsg("No data in dump buffer\n");
        return;
    }

    printMsg("\n%d) RingId=%d, Name=%s, Flags=%u, DebugLevel=%u, "
            "wBytes=%u, rBytes=%u, RingSize=%u, wRecords=%u\n",
            cnt++, status->ring_id, status->name, status->flags,
            status->verbose_level, status->written_bytes,
            status->read_bytes, status->ring_buffer_byte_size,
            status->written_records);

    wifi_ring_buffer_entry *buffer_entry = (wifi_ring_buffer_entry *) buffer;

    printMsg("Format: (%d) ", buffer_entry->flags);
    if (buffer_entry->flags & RING_BUFFER_ENTRY_FLAGS_HAS_BINARY)
        printMsg("\"BINARY\" ");
    if (buffer_entry->flags & RING_BUFFER_ENTRY_FLAGS_HAS_TIMESTAMP)
        printMsg("\"TIMESTAMP\"");

    printMsg(", Type: %s (%d)", RBentryTypeToString(buffer_entry->type), buffer_entry->type);
    printMsg(", TS: %llu ms", buffer_entry->timestamp);
    printMsg(", Size: %d bytes\n", buffer_entry->entry_size);

    pBuff = (char *) (buffer_entry + 1);
    snprintf(ring_file, FILE_NAME_LEN, "%s%s-%d.bin", RINGDATA_PREFIX, ring_name, f_count);
    w_fp = fopen(ring_file, "a");
    if (w_fp == NULL) {
        printMsg("Failed to open a file: %s\n", ring_file);
        return;
    }

    fwrite(pBuff, 1, buffer_entry->entry_size, w_fp);
    if (ftell(w_fp) >= FILE_MAX_SIZE) {
        f_count++;
        if (f_count >= NUM_ALERT_DUMPS)
            f_count = 0;
    }
    fclose(w_fp);
    w_fp = NULL;

    printMsg("Data: ");
    if (buffer_entry->flags & RING_BUFFER_ENTRY_FLAGS_HAS_BINARY) {
        for (int i = 0; i < buffer_size; i++)
            printMsg("%02x ", buffer[i]);
        printMsg("\n");
    } else {
        printMsg("%s\n", pBuff);
    }

    /*
     * Parsing Wake Lock event
     */
    if (buffer_entry->type == ENTRY_TYPE_WAKE_LOCK) {
        const char *strStatus[] = {"Taken", "Released", "Timeout"};
        wake_lock_event *wlock_event = (wake_lock_event *) pBuff;

        printMsg("Wakelock Event: Status=%s (%02x), Name=%s, Reason=%s (%02x)\n",
            strStatus[wlock_event->status], wlock_event->status,
            wlock_event->name, "\"TO BE\"", wlock_event->reason);
        return;
    }

    /*
     * Parsing TLV data
     */
    if (buffer_entry->type == ENTRY_TYPE_CONNECT_EVENT) {
        wifi_ring_buffer_driver_connectivity_event *connect_event =
            (wifi_ring_buffer_driver_connectivity_event *) (pBuff);

        tlv_log *tlv_data = (tlv_log *) (connect_event + 1);
        printMsg("Event type: %s (%u)\n", RBconnectEventToString(connect_event->event),
                connect_event->event);

        char *pos = (char *)tlv_data;
        char *end = (char *)connect_event + buffer_entry->entry_size;
        while (pos < end) {
            printMsg("TLV.type: %s (%d), TLV.len=%d (%02x)\n",
                    RBTlvTagToString(tlv_data->tag),
                    tlv_data->tag, tlv_data->length, tlv_data->length);

            switch (tlv_data->tag) {
                case WIFI_TAG_VENDOR_SPECIFIC:
                    break;

                case WIFI_TAG_BSSID:
                case WIFI_TAG_ADDR:
                case WIFI_TAG_ADDR1:
                case WIFI_TAG_ADDR2:
                case WIFI_TAG_ADDR3:
                case WIFI_TAG_ADDR4:
                {
                    if (tlv_data->length == sizeof(mac_addr)) {
                        mac_addr addr;
                        memcpy(&addr, tlv_data->value, sizeof(mac_addr));
                        printMsg("Address: %02x:%02x:%02x:%02x:%02x:%02x\n",
                            addr[0], addr[1], addr[2], addr[3], addr[4], addr[5]);
                    } else
                        printMsg("wrong lenght of address\n");
                    break;
                }

                case WIFI_TAG_SSID:
                {
                    char ssid[MAX_SSID_LEN];
                    memset(ssid, 0, sizeof(ssid));
                    if (tlv_data->length > MAX_SSID_LEN)
                        tlv_data->length = MAX_SSID_LEN;
                    memcpy(ssid, tlv_data->value, tlv_data->length);
                    printMsg("SSID = %s\n", ssid);
                    break;
                }

                case WIFI_TAG_STATUS:
                {
                    unsigned int tag_status = 0;
                    memcpy(&tag_status, tlv_data->value, tlv_data->length);
                    printMsg("tag_Status = %u\n", tag_status);
                    break;
                }

                case WIFI_TAG_CHANNEL_SPEC:
                {
                    wifi_channel_info *ch_spec = (wifi_channel_info *) tlv_data->value;
                    printMsg("Channel Info: center_freq=%d, freq0=%d, freq1=%d, width=%s (%d)\n",
                        RBchanWidthToString(ch_spec->width), ch_spec->center_freq,
                        ch_spec->center_freq0, ch_spec->center_freq1);
                    break;
                }

                case WIFI_TAG_WAKE_LOCK_EVENT:
                {
                    printMsg("Wake lock event = \"TO BE DONE LATER\"\n", tlv_data->value);
                    break;
                }

                case WIFI_TAG_TSF:
                {
                    u64 tsf = 0;
                    memcpy(&tsf, tlv_data->value, tlv_data->length);
                    printMsg("TSF value = %d\n", tsf);
                    break;
                }

                case WIFI_TAG_IE:
                {
                    printMsg("Information Element = \"TO BE\"\n");
                    break;
                }

                case WIFI_TAG_INTERFACE:
                {
                    const int len = 32;
                    char inf_name[len];

                    if (tlv_data->length > len)
                        tlv_data->length = len;
                    memset(inf_name, 0, 32);
                    memcpy(inf_name, tlv_data->value, tlv_data->length);
                    printMsg("Interface = %s\n", inf_name);
                    break;
                }

                case WIFI_TAG_REASON_CODE:
                {
                    u16 reason = 0;
                    memcpy(&reason, tlv_data->value, 2);
                    printMsg("Reason code = %d\n", reason);
                    break;
                }

                case WIFI_TAG_RATE_MBPS:
                {
                    u32 rate = 0;
                    memcpy(&rate, tlv_data->value, tlv_data->length);
                    printMsg("Rate = %.1f Mbps\n", rate * 0.5);    // rate unit is 500 Kbps.
                    break;
                }

                case WIFI_TAG_CHANNEL:
                {
                    u16 channel = 0;
                    memcpy(&channel, tlv_data->value, tlv_data->length);
                    printMsg("Channel = %d\n", channel);
                    break;
                }

                case WIFI_TAG_RSSI:
                {
                    short rssi = 0;
                    memcpy(&rssi, tlv_data->value, tlv_data->length);
                    printMsg("RSSI = %d\n", rssi);
                    break;
                }
            }
            pos = (char *)(tlv_data + 1);
            pos += tlv_data->length;
            tlv_data = (tlv_log *) pos;
        }
    }
}

static void onAlert(wifi_request_id id, char *buffer, int buffer_size, int err_code)
{

    // helper for AlertHandler

    printMsg("Getting FW Memory dump: (%d bytes), err code: %d\n", buffer_size, err_code);

    FILE* w_fp = NULL;
    static int f_count = 0;
    char dump_file[FILE_NAME_LEN];

    if (!buffer || buffer_size <= 0) {
        printMsg("No data in alert buffer\n");
        return;
    }

    snprintf(dump_file, FILE_NAME_LEN, "%s-%d.bin", ALERT_MEMDUMP_PREFIX, f_count++);
    if (f_count >= NUM_ALERT_DUMPS)
        f_count = 0;

    w_fp = fopen(dump_file, "w");
    if (w_fp == NULL) {
        printMsg("Failed to create a file: %s\n", dump_file);
        return;
    }

    printMsg("Write to \"%s\"\n", dump_file);
    fwrite(buffer, 1, buffer_size, w_fp);
    fclose(w_fp);
    w_fp = NULL;

}

static void onFirmwareMemoryDump(char *buffer, int buffer_size)
{
    // helper for LoggerGetMemdump()

    printMsg("Getting FW Memory dump: (%d bytes)\n", buffer_size);

    // Write a raw dump data into default local file or specified name
    FILE* w_fp = NULL;

    if (!buffer || buffer_size <= 0) {
        printMsg("No data in dump buffer\n");
        return;
    }

    w_fp = fopen(mem_dump_file, "w");
    if (w_fp == NULL) {
        printMsg("Failed to create a file: %s\n", mem_dump_file);
        return;
    }

    printMsg("Write to \"%s\"\n", mem_dump_file);
    fwrite(buffer, 1, buffer_size, w_fp);
    fclose(w_fp);
    w_fp = NULL;

    putEventInCache(EVENT_TYPE_LOGGER_MEMDUMP_DATA, "Memdump data");
}

static wifi_error LoggerStart()
{
    int ret;

    ret = hal_fn.wifi_start_logging(wlan0Handle,
        default_logger_param.verbose_level, default_logger_param.flags,
        default_logger_param.max_interval_sec, default_logger_param.min_data_size,
        default_logger_param.ring_name);

    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to start Logger: %d\n", ret);
        return WIFI_ERROR_UNKNOWN;
    }

    /*
     * debug mode (0) which means no more debug events will be triggered.
     *
     * Hopefully, need to extend this functionality by additional interfaces such as
     * set verbose level to each ring buffer.
     */
    return WIFI_SUCCESS;
}

static wifi_error LoggerGetMemdump()
{
    wifi_firmware_memory_dump_handler handler;
    handler.on_firmware_memory_dump = &onFirmwareMemoryDump;

    printMsg("Create Memdump event\n");
    int result = hal_fn.wifi_get_firmware_memory_dump(wlan0Handle, handler);

    if (result == WIFI_SUCCESS) {
        EventInfo info;
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_LOGGER_MEMDUMP_DATA)
                break;
            else
                printMsg("Could not get memdump data: %d\n", result);
        }
    }
    return WIFI_SUCCESS;
}

static wifi_error LoggerGetRingData()
{
    int result = hal_fn.wifi_get_ring_data(wlan0Handle, default_ring_name);

    if (result == WIFI_SUCCESS)
        printMsg("Get Ring data command success\n");
    else
        printMsg("Failed to execute get ring data command\n");

    return WIFI_SUCCESS;
}

static wifi_error LoggerGetFW()
{
    int ret;
    const int BSIZE = 256;
    int buffer_size = BSIZE;

    char buffer[BSIZE];
    memset(buffer, 0, BSIZE);

    ret = hal_fn.wifi_get_firmware_version(wlan0Handle, buffer, buffer_size);

    if (ret == WIFI_SUCCESS)
        printMsg("FW version (len=%d):\n%s\n", strlen(buffer), buffer);
    else
        printMsg("Failed to get FW version\n");

    return WIFI_SUCCESS;
}

static wifi_error LoggerGetDriver()
{
    int ret;
    const int BSIZE = 256;
    int buffer_size = BSIZE;

    char buffer[BSIZE];
    memset(buffer, 0, BSIZE);

    ret = hal_fn.wifi_get_driver_version(wlan0Handle, buffer, buffer_size);

    if (ret == WIFI_SUCCESS)
        printMsg("Driver version (len=%d):\n%s\n", strlen(buffer), buffer);
    else
        printMsg("Failed to get driver version\n");

    return WIFI_SUCCESS;
}

static wifi_error LoggerGetRingbufferStatus()
{
    int ret;
    const int NRING = 10;
    u32 num_rings = NRING;

    wifi_ring_buffer_status *status =
        (wifi_ring_buffer_status *)malloc(sizeof(wifi_ring_buffer_status) * num_rings);

    if (status == NULL)
        return WIFI_ERROR_OUT_OF_MEMORY;
    memset(status, 0, sizeof(wifi_ring_buffer_status) * num_rings);

    ret = hal_fn.wifi_get_ring_buffers_status(wlan0Handle, &num_rings, status);

    if (ret == WIFI_SUCCESS) {
        printMsg("RingBuffer status: [%d ring(s)]\n", num_rings);

        for (unsigned int i = 0; i < num_rings; i++) {
            printMsg("[%d] RingId=%d, Name=%s, Flags=%u, DebugLevel=%u, "
                    "wBytes=%u, rBytes=%u, RingSize=%u, wRecords=%u, status_addr=%p\n",
                    i+1,
                    status->ring_id,
                    status->name,
                    status->flags,
                    status->verbose_level,
                    status->written_bytes,
                    status->read_bytes,
                    status->ring_buffer_byte_size,
                    status->written_records, status);
            status++;
        }
    } else {
        printMsg("Failed to get Ringbuffer status\n");
    }

    free(status);
    status = NULL;

    return WIFI_SUCCESS;
}

static wifi_error LoggerGetFeature()
{
    int ret;
    unsigned int support = 0;

    const char *mapFeatures[] = {
        "MEMORY_DUMP",
        "PER_PACKET_TX_RX_STATUS",
        "CONNECT_EVENT",
        "POWER_EVENT",
        "WAKE_LOCK",
        "VERBOSE",
        "WATCHDOG_TIMER",
        "DRIVER_DUMP",
        "PACKET_FATE"
    };

    ret = hal_fn.wifi_get_logger_supported_feature_set(wlan0Handle, &support);

    if (ret == WIFI_SUCCESS) {
        printMsg("Logger supported features: %02x  [", support);

        if (support & WIFI_LOGGER_MEMORY_DUMP_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[0]);
        if (support & WIFI_LOGGER_PER_PACKET_TX_RX_STATUS_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[1]);
        if (support & WIFI_LOGGER_CONNECT_EVENT_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[2]);
        if (support & WIFI_LOGGER_POWER_EVENT_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[3]);
        if (support & WIFI_LOGGER_WAKE_LOCK_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[4]);
        if (support & WIFI_LOGGER_VERBOSE_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[5]);
        if (support & WIFI_LOGGER_WATCHDOG_TIMER_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[6]);
        if (support & WIFI_LOGGER_DRIVER_DUMP_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[7]);
        if (support & WIFI_LOGGER_PACKET_FATE_SUPPORTED)
            printMsg(" \"%s\" ", mapFeatures[8]);
        printMsg("]\n");
    } else {
        printMsg("Failed to get Logger supported features\n");
    }

    return WIFI_SUCCESS;
}

static wifi_error LoggerSetLogHandler()
{
    wifi_ring_buffer_data_handler handler;
    handler.on_ring_buffer_data = &onRingBufferData;

    printMsg("Setting log handler\n");
    int result = hal_fn.wifi_set_log_handler(loggerCmdId, wlan0Handle, handler);

    if (result == WIFI_SUCCESS) {
        EventInfo info;
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_LOGGER_RINGBUFFER_DATA)
                break;
        }
    } else {
        printMsg("Failed set Log handler: %d\n", result);
    }
    return WIFI_SUCCESS;
}

static wifi_error LoggerSetAlertHandler()
{
    loggerCmdId = getNewCmdId();
    wifi_alert_handler handler;
    handler.on_alert = &onAlert;

    printMsg("Create alert handler\n");
    int result = hal_fn.wifi_set_alert_handler(loggerCmdId, wlan0Handle, handler);

    if (result == WIFI_SUCCESS) {
        EventInfo info;
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_LOGGER_ALERT_DATA)
                break;
        }
    } else {
        printMsg("Failed set Alert handler: %d\n", result);
    }
    return WIFI_SUCCESS;
}

static wifi_error LoggerMonitorPktFate()
{
    printMsg("Start packet fate monitor \n");
    wifi_error result = hal_fn.wifi_start_pkt_fate_monitoring(wlan0Handle);

    if (result == WIFI_SUCCESS) {
        printMsg("Start packet fate monitor command successful\n");
    } else {
        printMsg("Start packet fate monitor command failed, err = %d\n", result);
    }
    return result;
}

static wifi_error LoggerGetTxPktFate()
{
    wifi_tx_report *tx_report, *report_ptr;
    size_t frame_len, n_provided_fates = 0;
    wifi_error result = WIFI_SUCCESS;
    FILE *w_fp = NULL;

    printMsg("Logger get tx pkt fate command\n");
    if (!n_requested_pkt_fate || n_requested_pkt_fate > MAX_FATE_LOG_LEN) {
        n_requested_pkt_fate = MAX_FATE_LOG_LEN;
    }

    tx_report = (wifi_tx_report *)malloc(n_requested_pkt_fate * sizeof(*tx_report));
    if (!tx_report) {
        printMsg("%s: Memory allocation failed\n",__FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(tx_report, 0, n_requested_pkt_fate * sizeof(*tx_report));

    result = hal_fn.wifi_get_tx_pkt_fates(wlan0Handle, tx_report,
            n_requested_pkt_fate, &n_provided_fates);
    if (result != WIFI_SUCCESS) {
        printMsg("Logger get tx pkt fate command failed, err = %d\n", result);
        goto exit;
    }

    if (!n_provided_fates) {
        printMsg("Got empty pkt fates\n");
        result = WIFI_ERROR_NOT_AVAILABLE;
        goto exit;
    }

    printMsg("No: of tx pkt fates provided = %d\n", n_provided_fates);

    w_fp = fopen(tx_pkt_fate_file, "w");
    if (!w_fp) {
        printMsg("Failed to create file: %s\n", tx_pkt_fate_file);
        result = WIFI_ERROR_NOT_AVAILABLE;
        goto exit;
    }

    fprintf(w_fp, "--- BEGIN ---\n\n");
    fprintf(w_fp, "No: of pkt fates provided = %zd\n\n", n_provided_fates);
    report_ptr = tx_report;
    for (size_t i = 0; i < n_provided_fates; i++) {
        fprintf(w_fp, "--- REPORT : %zu ---\n\n", (i + 1));
        if (report_ptr->frame_inf.frame_len == 0 ||
                report_ptr->frame_inf.payload_type == FRAME_TYPE_UNKNOWN) {
            fprintf(w_fp, "Invalid frame...!!!\n\n");
        }
        fprintf(w_fp, "MD5 Prefix                 :  ");
        fprhex(w_fp, report_ptr->md5_prefix, MD5_PREFIX_LEN, false);
        fprintf(w_fp, "Packet Fate                :  %d\n", report_ptr->fate);
        fprintf(w_fp, "Frame Type                 :  %d\n", report_ptr->frame_inf.payload_type);
        fprintf(w_fp, "Frame Len                  :  %zu\n", report_ptr->frame_inf.frame_len);
        fprintf(w_fp, "Driver Timestamp           :  %u\n",
            report_ptr->frame_inf.driver_timestamp_usec);
        fprintf(w_fp, "Firmware Timestamp         :  %u\n",
            report_ptr->frame_inf.firmware_timestamp_usec);
        if (report_ptr->frame_inf.payload_type == FRAME_TYPE_ETHERNET_II) {
            frame_len = min(report_ptr->frame_inf.frame_len, (size_t)MAX_FRAME_LEN_ETHERNET);
            fprintf(w_fp, "Frame Content (%04zu bytes) :  \n", frame_len);
            fprhex(w_fp, report_ptr->frame_inf.frame_content.ethernet_ii_bytes, frame_len, true);
        } else {
            frame_len = min(report_ptr->frame_inf.frame_len, (size_t)MAX_FRAME_LEN_80211_MGMT);
            fprintf(w_fp, "Frame Content (%04zu bytes) :  \n", frame_len);
            fprhex(w_fp, report_ptr->frame_inf.frame_content.ieee_80211_mgmt_bytes,
                frame_len, true);
        }
        fprintf(w_fp, "\n--- END OF REPORT ---\n\n");

        report_ptr++;
    }
    fprintf(w_fp, "--- EOF ---\n");

exit:
    if (w_fp) {
        fclose(w_fp);
    }
    if (tx_report) {
        free(tx_report);
    }

    return result;
}

static wifi_error LoggerGetRxPktFate()
{
    wifi_rx_report *rx_report, *report_ptr;
    size_t frame_len, n_provided_fates = 0;
    wifi_error result = WIFI_SUCCESS;
    FILE *w_fp = NULL;

    printMsg("Logger get rx pkt fate command\n");
    if (!n_requested_pkt_fate || n_requested_pkt_fate > MAX_FATE_LOG_LEN) {
        n_requested_pkt_fate = MAX_FATE_LOG_LEN;
    }

    rx_report = (wifi_rx_report *)malloc(n_requested_pkt_fate * sizeof(*rx_report));
    if (!rx_report) {
        printMsg("%s: Memory allocation failed\n",__FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(rx_report, 0, n_requested_pkt_fate * sizeof(*rx_report));

    result = hal_fn.wifi_get_rx_pkt_fates(wlan0Handle, rx_report,
            n_requested_pkt_fate, &n_provided_fates);
    if (result != WIFI_SUCCESS) {
        printMsg("Logger get rx pkt fate command failed, err = %d\n", result);
        goto exit;
    }

    if (!n_provided_fates) {
        printMsg("Got empty pkt fates\n");
        result = WIFI_ERROR_NOT_AVAILABLE;
        goto exit;
    }

    printMsg("No: of rx pkt fates provided = %d\n", n_provided_fates);

    w_fp = fopen(rx_pkt_fate_file, "w");
    if (!w_fp) {
        printMsg("Failed to create file: %s\n", rx_pkt_fate_file);
        result = WIFI_ERROR_NOT_AVAILABLE;
        goto exit;
    }

    fprintf(w_fp, "--- BEGIN ---\n\n");
    fprintf(w_fp, "No: of pkt fates provided = %zd\n\n", n_provided_fates);
    report_ptr = rx_report;
    for (size_t i = 0; i < n_provided_fates; i++) {
        fprintf(w_fp, "--- REPORT : %zu ---\n\n", (i + 1));
        if (report_ptr->frame_inf.frame_len == 0 ||
                report_ptr->frame_inf.payload_type == FRAME_TYPE_UNKNOWN) {
            fprintf(w_fp, "Invalid frame...!!!\n\n");
        }
        fprintf(w_fp, "MD5 Prefix                 :  ");
        fprhex(w_fp, report_ptr->md5_prefix, MD5_PREFIX_LEN, false);
        fprintf(w_fp, "Packet Fate                :  %d\n", report_ptr->fate);
        fprintf(w_fp, "Frame Type                 :  %d\n", report_ptr->frame_inf.payload_type);
        fprintf(w_fp, "Frame Len                  :  %zu\n", report_ptr->frame_inf.frame_len);
        fprintf(w_fp, "Driver Timestamp           :  %u\n", report_ptr->frame_inf.driver_timestamp_usec);
        fprintf(w_fp, "Firmware Timestamp         :  %u\n", report_ptr->frame_inf.firmware_timestamp_usec);
        if (report_ptr->frame_inf.payload_type == FRAME_TYPE_ETHERNET_II) {
            frame_len = min(report_ptr->frame_inf.frame_len, (size_t)MAX_FRAME_LEN_ETHERNET);
			fprintf(w_fp, "Frame Content (%04zu bytes) :  \n", frame_len);
			fprhex(w_fp, report_ptr->frame_inf.frame_content.ethernet_ii_bytes, frame_len,
				true);
        } else {
            frame_len = min(report_ptr->frame_inf.frame_len, (size_t)MAX_FRAME_LEN_80211_MGMT);
			fprintf(w_fp, "Frame Content (%04zu bytes) :  \n", frame_len);
			fprhex(w_fp, report_ptr->frame_inf.frame_content.ieee_80211_mgmt_bytes, frame_len,
				true);
        }
        fprintf(w_fp, "\n--- END OF REPORT ---\n\n");

        report_ptr++;
    }
    fprintf(w_fp, "--- EOF ---\n");

exit:
    if (w_fp) {
        fclose(w_fp);
    }
    if (rx_report) {
        free(rx_report);
    }

    return result;
}

static void runLogger()
{
    switch (log_cmd) {
        case LOG_GET_FW_VER:
            LoggerGetFW();
            break;
        case LOG_GET_DRV_VER:
            LoggerGetDriver();
            break;
        case LOG_GET_RING_STATUS:
            LoggerGetRingbufferStatus();
            break;
        case LOG_GET_FEATURE:
            LoggerGetFeature();
            break;
        case LOG_GET_MEMDUMP:
            LoggerGetMemdump();
            break;
        case LOG_GET_RING_DATA:
            LoggerGetRingData();
            break;
        case LOG_START:
            LoggerStart();
            break;
        case LOG_SET_LOG_HANDLER:
            LoggerSetLogHandler();
            break;
        case LOG_SET_ALERT_HANDLER:
            LoggerSetAlertHandler();
            break;
        case LOG_MONITOR_PKTFATE:
            LoggerMonitorPktFate();
            break;
        case LOG_GET_TXPKTFATE:
            LoggerGetTxPktFate();
            break;
        case LOG_GET_RXPKTFATE:
            LoggerGetRxPktFate();
            break;
        default:
            break;
    }
}

static wifi_error start_mkeep_alive(int index, u32 period_msec, u16 ether_type,
                                        u8* src_mac, u8* dst_mac, u8* ip_pkt,
                                        u16 ip_pkt_len)
{
    int ret;

    ret = hal_fn.wifi_start_sending_offloaded_packet(index, wlan0Handle, ether_type, ip_pkt,
          ip_pkt_len, src_mac, dst_mac, period_msec);

    if (ret == WIFI_SUCCESS) {
        printMsg("Start mkeep_alive with ID %d, %u period(msec), src(" MACSTR "), "
            "dst(" MACSTR ")\n", index, period_msec, MAC2STR(src_mac), MAC2STR(dst_mac));
    } else {
        printMsg("Failed to start mkeep_alive by ID %d: %d\n", index, ret);
        return WIFI_ERROR_NOT_AVAILABLE;
    }
    return WIFI_SUCCESS;
}

static wifi_error stop_mkeep_alive(int index)
{
    int ret;

    ret = hal_fn.wifi_stop_sending_offloaded_packet(index, wlan0Handle);

    if (ret == WIFI_SUCCESS) {
        printMsg("Stop mkeep_alive with ID %d\n", index);
    } else {
        printMsg("Failed to stop mkeep_alive by ID %d: %d\n", index, ret);
        return WIFI_ERROR_NOT_AVAILABLE;
    }
    return WIFI_SUCCESS;
}

byte parseHexChar(char ch) {
    if (isdigit(ch))
        return ch - '0';
    else if ('A' <= ch && ch <= 'F')
        return ch - 'A' + 10;
    else if ('a' <= ch && ch <= 'f')
        return ch - 'a' + 10;
    else {
        printMsg("invalid character in bssid %c\n", ch);
        return 0;
    }
}

byte parseHexByte(char ch1, char ch2) {
    return (parseHexChar(ch1) << 4) | parseHexChar(ch2);
}

void parseMacAddress(const char *str, mac_addr addr) {
    addr[0] = parseHexByte(str[0], str[1]);
    addr[1] = parseHexByte(str[3], str[4]);
    addr[2] = parseHexByte(str[6], str[7]);
    addr[3] = parseHexByte(str[9], str[10]);
    addr[4] = parseHexByte(str[12], str[13]);
    addr[5] = parseHexByte(str[15], str[16]);
}

void parseMacOUI(char *str, unsigned char *addr) {
    addr[0] = parseHexByte(str[0], str[1]);
    addr[1] = parseHexByte(str[3], str[4]);
    addr[2] = parseHexByte(str[6], str[7]);
    printMsg("read mac OUI: %02x:%02x:%02x\n", addr[0],
            addr[1], addr[2]);
}

int
ether_atoe(const char *a, u8 *addr)
{
    char *c = NULL;
    int i = 0;
    memset(addr, 0, ETHER_ADDR_LEN);
    for (; i < ETHER_ADDR_LEN; i++) {
        addr[i] = (u8)strtoul(a, &c, 16);
        if (*c != ':' && *c != '\0') {
            return 0;
        }
        a = ++c;
    }
    return (i == ETHER_ADDR_LEN);
}

void readTestOptions(int argc, char *argv[]) {

    printf("Total number of argc #%d\n", argc);
    wifi_epno_network *epno_ssid = epno_cfg.networks;
    for (int j = 1; j < argc-1; j++) {
        if (strcmp(argv[j], "-max_ap") == 0 && isdigit(argv[j+1][0])) {
            stest_max_ap = atoi(argv[++j]);
            printf(" max_ap #%d\n", stest_max_ap);
        } else if (strcmp(argv[j], "-base_period") == 0 && isdigit(argv[j+1][0])) {
            stest_base_period = atoi(argv[++j]);
            printf(" base_period #%d\n", stest_base_period);
        } else if (strcmp(argv[j], "-threshold") == 0 && isdigit(argv[j+1][0])) {
            stest_threshold_percent = atoi(argv[++j]);
            printf(" threshold #%d\n", stest_threshold_percent);
        } else if (strcmp(argv[j], "-avg_RSSI") == 0 && isdigit(argv[j+1][0])) {
            swctest_rssi_sample_size = atoi(argv[++j]);
            printf(" avg_RSSI #%d\n", swctest_rssi_sample_size);
        } else if (strcmp(argv[j], "-ap_loss") == 0 && isdigit(argv[j+1][0])) {
            swctest_rssi_lost_ap = atoi(argv[++j]);
            printf(" ap_loss #%d\n", swctest_rssi_lost_ap);
        } else if (strcmp(argv[j], "-ap_breach") == 0 && isdigit(argv[j+1][0])) {
            swctest_rssi_min_breaching = atoi(argv[++j]);
            printf(" ap_breach #%d\n", swctest_rssi_min_breaching);
        } else if (strcmp(argv[j], "-ch_threshold") == 0 && isdigit(argv[j+1][0])) {
            swctest_rssi_ch_threshold = atoi(argv[++j]);
            printf(" ch_threshold #%d\n", swctest_rssi_ch_threshold);
        } else if (strcmp(argv[j], "-wt_event") == 0 && isdigit(argv[j+1][0])) {
            max_event_wait = atoi(argv[++j]);
            printf(" wt_event #%d\n", max_event_wait);
        } else if (strcmp(argv[j], "-low_th") == 0 && isdigit(argv[j+1][0])) {
            htest_low_threshold = atoi(argv[++j]);
            printf(" low_threshold #-%d\n", htest_low_threshold);
        } else if (strcmp(argv[j], "-high_th") == 0 && isdigit(argv[j+1][0])) {
            htest_high_threshold = atoi(argv[++j]);
            printf(" high_threshold #-%d\n", htest_high_threshold);
        } else if (strcmp(argv[j], "-hotlist_bssids") == 0 && isxdigit(argv[j+1][0])) {
            j++;
            for (num_hotlist_bssids = 0; j < argc && isxdigit(argv[j][0]);
                j++, num_hotlist_bssids++) {
                    parseMacAddress(argv[j], hotlist_bssids[num_hotlist_bssids]);
            }
            j -= 1;
        } else if (strcmp(argv[j], "-channel_list") == 0 && isxdigit(argv[j+1][0])) {
            j++;
            for (num_channels = 0; j < argc && isxdigit(argv[j][0]); j++, num_channels++) {
                channel_list[num_channels] = atoi(argv[j]);
            }
            j -= 1;
        } else if ((strcmp(argv[j], "-get_ch_list") == 0)) {
            if(strcmp(argv[j + 1], "a") == 0) {
                band = WIFI_BAND_A_WITH_DFS;
            } else if(strcmp(argv[j + 1], "bg") == 0) {
                band = WIFI_BAND_BG;
            } else if(strcmp(argv[j + 1], "abg") == 0) {
                band = WIFI_BAND_ABG_WITH_DFS;
            } else if(strcmp(argv[j + 1], "a_nodfs") == 0) {
                band = WIFI_BAND_A;
            } else if(strcmp(argv[j + 1], "dfs") == 0) {
                band = WIFI_BAND_A_DFS;
            } else if(strcmp(argv[j + 1], "abg_nodfs") == 0) {
                band = WIFI_BAND_ABG;
            }
            j++;
        } else if (strcmp(argv[j], "-scan_mac_oui") == 0 && isxdigit(argv[j+1][0])) {
            parseMacOUI(argv[++j], mac_oui);
        } else if ((strcmp(argv[j], "-ssid") == 0)) {
            epno_cfg.num_networks++;
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
                memcpy(epno_ssid[epno_cfg.num_networks].ssid, argv[j + 1], (size_t)(MAX_SSID_LEN));
                printf(" SSID %s\n", epno_ssid[epno_cfg.num_networks].ssid);
                j++;
            }
        } else if ((strcmp(argv[j], "-auth") == 0)) {
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
               epno_ssid[epno_cfg.num_networks].auth_bit_field = atoi(argv[++j]);
               printf(" auth %d\n", epno_ssid[epno_cfg.num_networks].auth_bit_field);
            }
        } else if ((strcmp(argv[j], "-hidden") == 0)) {
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
               epno_ssid[epno_cfg.num_networks].flags |= atoi(argv[++j]) ? EPNO_HIDDEN: 0;
               printf(" flags %d\n", epno_ssid[epno_cfg.num_networks].flags);
            }
        } else if ((strcmp(argv[j], "-strict") == 0)) {
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
               epno_ssid[epno_cfg.num_networks].flags |= atoi(argv[++j]) ? EPNO_FLAG_STRICT_MATCH: 0;
               printf(" flags %d\n", epno_ssid[epno_cfg.num_networks].flags);
            }
        } else if ((strcmp(argv[j], "-same_network") == 0)) {
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
               epno_ssid[epno_cfg.num_networks].flags |= atoi(argv[++j]) ? EPNO_FLAG_SAME_NETWORK: 0;
               printf(" flags %d\n", epno_ssid[epno_cfg.num_networks].flags);
            }
        } else if (strcmp(argv[j], "-min5g_rssi") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.min5GHz_rssi = -atoi(argv[++j]);
            printf(" min5g_rssi %d\n", epno_cfg.min5GHz_rssi);
        } else if (strcmp(argv[j], "-min2g_rssi") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.min24GHz_rssi = -atoi(argv[++j]);
            printf(" min2g_rssi %d\n", epno_cfg.min24GHz_rssi);
        } else if (strcmp(argv[j], "-init_score_max") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.initial_score_max = atoi(argv[++j]);
            printf(" initial_score_max %d\n", epno_cfg.initial_score_max);
        } else if (strcmp(argv[j], "-cur_conn_bonus") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.current_connection_bonus = atoi(argv[++j]);
            printf(" cur_conn_bonus %d\n", epno_cfg.current_connection_bonus);
        } else if (strcmp(argv[j], "-same_network_bonus") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.same_network_bonus = atoi(argv[++j]);
            printf(" same_network_bonus %d\n", epno_cfg.same_network_bonus);
        } else if (strcmp(argv[j], "-secure_bonus") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.secure_bonus = atoi(argv[++j]);
            printf(" secure_bonus %d\n", epno_cfg.secure_bonus);
        } else if (strcmp(argv[j], "-band5g_bonus") == 0 && isdigit(argv[j+1][0])) {
            epno_cfg.band5GHz_bonus = atoi(argv[++j]);
            printf(" band5GHz_bonus %d\n", epno_cfg.band5GHz_bonus);
        } else if ((strcmp(argv[j], "-trig") == 0)) {
            if (epno_cfg.num_networks < (int)MAX_EPNO_NETWORKS) {
                if ((strcmp(argv[j + 1], "a") == 0)) {
                   epno_ssid[epno_cfg.num_networks].flags |= EPNO_A_BAND_TRIG;
                } else if ((strcmp(argv[j + 1], "bg") == 0)) {
                   epno_ssid[epno_cfg.num_networks].flags |= EPNO_BG_BAND_TRIG;
                } else if ((strcmp(argv[j + 1], "abg") == 0)) {
                   epno_ssid[epno_cfg.num_networks].flags |= EPNO_ABG_BAND_TRIG;
                }
               printf(" flags %d\n", epno_ssid[epno_cfg.num_networks].flags);
            }
            j++;
        } else if ((strcmp(argv[j], "-blacklist_bssids") == 0 && isxdigit(argv[j+1][0])) ||
            (strcmp(argv[j], "-whitelist_ssids") == 0)) {
            if (strcmp(argv[j], "-blacklist_bssids") == 0 && isxdigit(argv[j+1][0])) {
                j++;
                for (num_blacklist_bssids = 0;
                    j < argc && isxdigit(argv[j][0]) &&
                    num_blacklist_bssids < MAX_BLACKLIST_BSSID;
                    j++, num_blacklist_bssids++) {
                    parseMacAddress(argv[j], blacklist_bssids[num_blacklist_bssids]);
                }
            }
            if (strcmp(argv[j], "-whitelist_ssids") == 0) {
                j++;
                for (num_whitelist_ssids = 0;
                    j < argc && (num_whitelist_ssids < MAX_WHITELIST_SSID);
                    j++, num_whitelist_ssids++) {
                        if ((strcmp(argv[j], "-blacklist_bssids") == 0) ||
                            isxdigit(argv[j][0])) {
                            num_whitelist_ssids--;
                            continue;
                        }
                    strncpy(whitelist_ssids[num_whitelist_ssids], argv[j],
                    min(strlen(argv[j]), (size_t)(MAX_SSID_LEN-1)));
                }
                /* Setting this flag to true here as -blacklist_bssids has already existing explicit handler */
                set_roaming_configuration = true;
            }
            j -= 1;
        } else if (strcmp(argv[j], "-rssi_monitor") == 0 && isdigit(argv[j+1][0])) {
            rssi_monitor = atoi(argv[++j]);
            printf(" rssi_monitor #%d\n", rssi_monitor);
        } else if (strcmp(argv[j], "-max_rssi") == 0 && isdigit(argv[j+1][0])) {
            max_rssi = -atoi(argv[++j]);
            printf(" max_rssi #%d\n", max_rssi);
        } else if (strcmp(argv[j], "-min_rssi") == 0 && isdigit(argv[j+1][0])) {
            min_rssi = -atoi(argv[++j]);
            printf(" min_rssi #%d\n", min_rssi);
        }
    }
}

void readRTTOptions(int argc, char *argv[]) {
    for (int j = 1; j < argc-1; j++) {
        if ((strcmp(argv[j], "-get_ch_list") == 0)) {
            if(strcmp(argv[j + 1], "a") == 0) {
                band = WIFI_BAND_A_WITH_DFS;
            } else if(strcmp(argv[j + 1], "bg") == 0) {
                band = WIFI_BAND_BG;
            } else if(strcmp(argv[j + 1], "abg") == 0) {
                band = WIFI_BAND_ABG_WITH_DFS;
            } else if(strcmp(argv[j + 1], "a_nodfs") == 0) {
                band = WIFI_BAND_A;
            } else if(strcmp(argv[j + 1], "dfs") == 0) {
                band = WIFI_BAND_A_DFS;
            } else if(strcmp(argv[j + 1], "abg_nodfs") == 0) {
                band = WIFI_BAND_ABG;
            }
            ALOGE("band chosen = %s[band = %d]\n", argv[j + 1], band);
            j++;
        } else if ((strcmp(argv[j], "-l") == 0)) {
            /*
             * If this option is specified but there is no file name,
             * use a default file from rtt_aplist.
             */
            if (++j != argc-1) {
                strncpy(rtt_aplist, argv[j], (FILE_NAME_LEN -1));
                rtt_aplist[FILE_NAME_LEN -1] = '\0';
            }
            rtt_from_file = 1;
        } else if ((strcmp(argv[j], "-n") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.num_burst = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-f") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.num_frames_per_burst = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-r") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.num_retries_per_ftm = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-m") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.num_retries_per_ftmr = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-b") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.burst_duration = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-max_ap") == 0) && isdigit(argv[j+1][0])) {
            max_ap = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-lci") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.LCI_request = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-lcr") == 0) && isdigit(argv[j+1][0])) {
            default_rtt_param.LCR_request = atoi(argv[++j]);
        } else if ((strcmp(argv[j], "-type") == 0) && isdigit(argv[j+1][0])) {
            u8 rtt_type = atoi(argv[++j]);
            if (rtt_type == 1) {
                printf("RTT Type is ONE-SIDED\n");
                default_rtt_param.type = RTT_TYPE_1_SIDED;
            }
        } else if ((strcmp(argv[j], "-o") == 0)) {
            /*
             * If this option is specified but there is no file name,
             * use a default file from rtt_aplist.
             */
            if (++j != argc-1) {
                strncpy(rtt_aplist, argv[j], (FILE_NAME_LEN -1));
                rtt_aplist[FILE_NAME_LEN -1] = '\0';
            }
            rtt_to_file = 1;
        } else if ((strcmp(argv[j], "-sta") == 0) ||
            (strcmp(argv[j], "-nan") == 0)) {
            if (strcmp(argv[j], "-sta") == 0) {
                rtt_sta = true;
            } else {
                rtt_nan = true;
            }
            if (isxdigit(argv[j+1][0])) {
                j++;
                parseMacAddress(argv[j], responder_addr);
                printMsg("Target mac(" MACSTR ")", MAC2STR(responder_addr));
            }
            /* Read channel if present */
            if (argv[j+1]) {
                if (isdigit(argv[j+1][0])) {
                    j++;
                    responder_channel = atoi(argv[j]);
                    printf("Channel set as %d \n", responder_channel);
                }
                /* Read band width if present */
                if (argv[j+1]) {
                    if (isdigit(argv[j+1][0])) {
                        j++;
                        channel_width = atoi(argv[j]);
                        printf("channel_width as %d \n", channel_width);
                    }
                }
                /* check its 6g channel */
                if (argv[j+1]) {
                    if (isdigit(argv[j+1][0])) {
                        j++;
                        if(atoi(argv[j]) == 1) {
                            printf(" IS 6G CHANNEL \n");
                            is_6g = true;
                        }
                    }
                }
            }
        }
    }
}

void readLoggerOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()
    int j = 1;

    if (argc < 3) {
        printUsage();
        return;
    }

    if ((strcmp(argv[j], "-start") == 0)) {
        if ((strcmp(argv[j+1], "pktmonitor") == 0)){
            log_cmd = LOG_MONITOR_PKTFATE;
            return;
        } else if (argc != 13) {
            printf("\nUse correct logger option:\n");
            printUsage();
            return;
        }
        log_cmd = LOG_START;
        memset(&default_logger_param, 0, sizeof(default_logger_param));

        j++;
        if ((strcmp(argv[j], "-d") == 0) && isdigit(argv[j+1][0]))
            default_logger_param.verbose_level = (unsigned int)atoi(argv[++j]);
        if ((strcmp(argv[++j], "-f") == 0) && isdigit(argv[j+1][0]))
            default_logger_param.flags = atoi(argv[++j]);
        if ((strcmp(argv[++j], "-i") == 0) && isdigit(argv[j+1][0]))
            default_logger_param.max_interval_sec = atoi(argv[++j]);
        if ((strcmp(argv[++j], "-s") == 0) && isdigit(argv[j+1][0]))
            default_logger_param.min_data_size = atoi(argv[++j]);
        if ((strcmp(argv[++j], "-n") == 0))
            memcpy(default_logger_param.ring_name, argv[j+1], (MAX_RING_NAME_SIZE));
        return;
    } else if ((strcmp(argv[j], "-get") == 0) && (argc > 3)) {
        if ((strcmp(argv[j+1], "fw") == 0)) {
            log_cmd = LOG_GET_FW_VER;
        } else if ((strcmp(argv[j+1], "driver") == 0)) {
            log_cmd = LOG_GET_DRV_VER;
        } else if ((strcmp(argv[j+1], "memdump") == 0)) {
            log_cmd = LOG_GET_MEMDUMP;
            j++;
            if ((j+1 < argc-1) && (strcmp(argv[j+1], "-o") == 0)) {
                // If this option is specified but there is no file name,
                // use a default file from DEFAULT_MEMDUMP_FILE.
                j++;
                if (j+1 < argc-1) {
                    strncpy(mem_dump_file, argv[j+1] , (FILE_NAME_LEN -1));
                    mem_dump_file[FILE_NAME_LEN -1] = '\0';
                }
            }
        } else if ((strcmp(argv[j+1], "ringstatus") == 0)) {
            log_cmd = LOG_GET_RING_STATUS;
        } else if ((strcmp(argv[j+1], "feature") == 0)) {
            log_cmd = LOG_GET_FEATURE;
        } else if ((strcmp(argv[j+1], "ringdata") == 0)) {
            log_cmd = LOG_GET_RING_DATA;
            j+=2;
            if ((strcmp(argv[j], "-n") == 0))
                memcpy(default_ring_name, argv[j+1], MAX_RING_NAME_SIZE);
        } else if ((strcmp(argv[j+1], "txfate") == 0)) {
            log_cmd = LOG_GET_TXPKTFATE;
            j++;
            while (j+1 < argc-1) {
                if (strcmp(argv[j+1], "-n") == 0) {
                    j++;
                    if (j+1 < argc-1) {
                        n_requested_pkt_fate = atoi(argv[j+1]);
                    }
                } else if (strcmp(argv[j+1], "-f") == 0) {
                    j++;
                    if (j+1 < argc-1) {
                        size_t len = min(strlen(argv[j+1]), (size_t)(FILE_NAME_LEN - 1));
                        strncpy(tx_pkt_fate_file, argv[j+1], len);
                        tx_pkt_fate_file[len] = '\0';
                    }
                }
                j++;
            }
        } else if ((strcmp(argv[j+1], "rxfate") == 0)) {
            log_cmd = LOG_GET_RXPKTFATE;
            j++;
            while (j+1 < argc-1) {
                if (strcmp(argv[j+1], "-n") == 0) {
                    j++;
                    if (j+1 < argc-1) {
                        n_requested_pkt_fate = atoi(argv[j+1]);
                    }
                } else if (strcmp(argv[j+1], "-f") == 0) {
                    j++;
                    if (j+1 < argc-1) {
                        size_t len = min(strlen(argv[j+1]), (size_t)(FILE_NAME_LEN - 1));
                        strncpy(rx_pkt_fate_file, argv[j+1], len);
                        rx_pkt_fate_file[len] = '\0';
                    }
                }
                j++;
            }
        } else {
            printf("\nUse correct logger option:\n");
            printUsage();
        }
        return;
    } else if ((strcmp(argv[j], "-set") == 0) && (argc > 3)) {
        if ((strcmp(argv[j+1], "loghandler") == 0)) {
            log_cmd = LOG_SET_LOG_HANDLER;
        } else if ((strcmp(argv[j+1], "alerthandler") == 0)) {
            log_cmd = LOG_SET_ALERT_HANDLER;
        }
    } else {
        printf("\nUse correct logger option:\n");
        printUsage();

        return;
    }
}

static int str2hex(char *src, char *dst)
{
    int i;
    if (strlen(src) % 2 != 0) {
        printMsg(("Mask invalid format. Needs to be of even length\n"));
        return -1;
    }

    if (strncmp(src, "0x", 2) == 0 || strncmp(src, "0X", 2) == 0) {
        src = src + 2; /* Skip past 0x */
    }

    for (i = 0; *src != '\0'; i++) {
        char num[3];
        strncpy(num, src, 2);
        num[2] = '\0';
        dst[i] = (u8)strtoul(num, NULL, 16);
        src += 2;
    }
    return i;
}

void readKeepAliveOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()

    int index = 0;
    u32 period_msec = 0;
    mac_addr src_mac;                           // byte array of src mac address
    mac_addr dst_mac;                           // byte array of dest mac address
    u8 ip_pkt[MKEEP_ALIVE_IP_PKT_MAX] = {0};    // IP pkt including UDP and headers
    u16 ip_pkt_len = 0, ether_type = 0;
    int j = 1;
    int ret = 0;

    /**
     * For example,
     *
     * u8 ip_pkt[] =
     * "0014a54b164f000f66f45b7e08004500001e000040004011c52a0a8830700a88302513c413c4000a00000a0d"
     *
     *  length: 44 bytes
     *
     *  Ethernet header
     *       0014a54b164f   - dest addr
     *       000f66f45b7e   - src addr
     *       0800           - ether-type    ETHERTYPE_IP (IP protocol) or
     *       86dd           - ether-type    ETHERTYPE_IPV6 (IPv6 protocol)
     *  IP header
     *       4500001e       - Version, IHL, TOS, Total length
     *       00004000       - Identification, fragment
     *       4011c52a       - TTL, Protocol, Checksum
     *       0a883070       - src addr
     *       0a883025       - dest addr
     *  UDP header
     *       13c4           - src port
     *       13c4           - dest port
     *       000a           - UDP length
     *       0000           - checksum
     *  UDP payload
     *       0a0d
     */

    if ((argc == 9) && (strcmp(argv[j], "-start") == 0)) {
        // Mapping index
        index = atoi(argv[++j]);
        if (index < 1 || index > N_AVAIL_ID) {
            printMsg("Select proper index number (1 to 3) for mkeep_alive.\n");
            return;
        }

        // Mapping period
        period_msec = atoi(argv[++j]);
        if (period_msec <= 0) {
            printMsg("Select proper retransmission period for mkeep_alive, great than zero\n");
            return;
        }

        // Mapping mac addresses
        if ((str2hex(argv[++j], (char *)src_mac) != ETHER_ADDR_LEN)
                || (str2hex(argv[++j], (char *)dst_mac) != ETHER_ADDR_LEN)) {
            printMsg("Source or destination mac address is not correct. Please make sure.\n");
            return;
        }

        // Mapping ether_type
        ether_type = atoi(argv[++j]);
        if (!((ether_type == ETHERTYPE_IP) ||
            (ether_type == ETHERTYPE_IPV6))) {
            printMsg("Select proper ether_type, valid values 0x0800(2048) for IP or 0x86dd(34525) IP6\n");
            return;
        }

        // Mapping string pkt length by hexa byte
        ip_pkt_len = strlen(argv[++j])/2;
        if (ip_pkt_len > MKEEP_ALIVE_IP_PKT_MAX) {
            printMsg("IP pkt size is bigger than max_len (%d) for mkeep_alive. "
                     "Please check up the size of IP packet contents.\n",
                MKEEP_ALIVE_IP_PKT_MAX);
                return;
        }

        // Mapping pkt contents by hexa format
        memset(ip_pkt, 0, MKEEP_ALIVE_IP_PKT_MAX);
        if (str2hex(argv[j], (char *)ip_pkt) != ip_pkt_len) {
            printMsg("Conversion of hexa byte on IP pkt has been failed.\n");
            return;
        }

        ret = start_mkeep_alive(index, period_msec, ether_type, src_mac,
                dst_mac, ip_pkt, ip_pkt_len);
        if (ret == WIFI_SUCCESS)
            printMsg("Success to register mkeep_alive by ID %d\n", index);
    } else if ((argc == 4) && (strcmp(argv[j], "-stop") == 0)) {
        // mapping index
        index = atoi(argv[++j]);
        if (index < 1 || index > N_AVAIL_ID) {
            printMsg("Select proper index number (1 to 3) for mkeep_alive.\n");
            return;
        }

        ret = stop_mkeep_alive(index);
        if (ret == WIFI_SUCCESS)
            printMsg("Success to stop mkeep_alive by ID %d\n", index);
    } else {
        printf("Use correct mkeep_alive option:\n");
    }
}

const char *eht_rates[] = {
    "OFDM/LEGACY 1Mbps               ",
    "OFDM/LEGACY 2Mbps               ",
    "OFDM/LEGACY 5.5Mbps             ",
    "OFDM/LEGACY 6Mbps               ",
    "OFDM/LEGACY 9Mbps               ",
    "OFDM/LEGACY 11Mbps              ",
    "OFDM/LEGACY 12Mbps              ",
    "OFDM/LEGACY 18Mbps              ",
    "OFDM/LEGACY 24Mbps              ",
    "OFDM/LEGACY 36Mbps              ",
    "OFDM/LEGACY 48Mbps              ",
    "OFDM/LEGACY 54Mbps              ",
    "HT MCS0  | VHT/HE/EHT MCS0  NSS1",
    "HT MCS1  | VHT/HE/EHT MCS1  NSS1",
    "HT MCS2  | VHT/HE/EHT MCS2  NSS1",
    "HT MCS3  | VHT/HE/EHT MCS3  NSS1",
    "HT MCS4  | VHT/HE/EHT MCS4  NSS1",
    "HT MCS5  | VHT/HE/EHT MCS5  NSS1",
    "HT MCS6  | VHT/HE/EHT MCS6  NSS1",
    "HT MCS7  | VHT/HE/EHT MCS7  NSS1",
    "HT MCS8  | VHT/HE/EHT MCS8  NSS1",
    "HT MCS9  | VHT/HE/EHT MCS9  NSS1",
    "HT MCS10 | VHT/HE/EHT MCS10 NSS1",
    "HT MCS11 | VHT/HE/EHT MCS11 NSS1",
    "HT MCS12 |        EHT MCS12 NSS1",
    "HT MCS13 |        EHT MCS13 NSS1",
    "HT MCS14 |        EHT MCS14 NSS1",
    "HT MCS15 |        EHT MCS15 NSS1",
    "HT N/A   | VHT/HE/EHT MCS0  NSS2",
    "HT N/A   | VHT/HE/EHT MCS1  NSS2",
    "HT N/A   | VHT/HE/EHT MCS2  NSS2",
    "HT N/A   | VHT/HE/EHT MCS3  NSS2",
    "HT N/A   | VHT/HE/EHT MCS4  NSS2",
    "HT N/A   | VHT/HE/EHT MCS5  NSS2",
    "HT N/A   | VHT/HE/EHT MCS6  NSS2",
    "HT N/A   | VHT/HE/EHT MCS7  NSS2",
    "HT N/A   | VHT/HE/EHT MCS8  NSS2",
    "HT N/A   | VHT/HE/EHT MCS9  NSS2",
    "HT N/A   | VHT/HE/EHT MCS10 NSS2",
    "HT N/A   | VHT/HE/EHT MCS11 NSS2",
    "HT N/A   |        EHT MCS12 NSS2",
    "HT N/A   |        EHT MCS13 NSS2",
    "HT N/A   |        EHT MCS14 NSS2",
    "HT N/A   |        EHT MCS15 NSS2",
};

const char *rates[] = {
    "OFDM/LEGACY 1Mbps",
    "OFDM/LEGACY 2Mbps",
    "OFDM/LEGACY 5.5Mbps",
    "OFDM/LEGACY 6Mbps",
    "OFDM/LEGACY 9Mbps",
    "OFDM/LEGACY 11Mbps",
    "OFDM/LEGACY 12Mbps",
    "OFDM/LEGACY 18Mbps",
    "OFDM/LEGACY 24Mbps",
    "OFDM/LEGACY 36Mbps",
    "OFDM/LEGACY 48Mbps",
    "OFDM/LEGACY 54Mbps",
    "HT MCS0  | VHT/HE MCS0  NSS1",
    "HT MCS1  | VHT/HE MCS1  NSS1",
    "HT MCS2  | VHT/HE MCS2  NSS1",
    "HT MCS3  | VHT/HE MCS3  NSS1",
    "HT MCS4  | VHT/HE MCS4  NSS1",
    "HT MCS5  | VHT/HE MCS5  NSS1",
    "HT MCS6  | VHT/HE MCS6  NSS1",
    "HT MCS7  | VHT/HE MCS7  NSS1",
    "HT MCS8  | VHT/HE MCS8  NSS1",
    "HT MCS9  | VHT/HE MCS9  NSS1",
    "HT MCS10 | VHT/HE MCS10 NSS1",
    "HT MCS11 | VHT/HE MCS11 NSS1",
    "HT MCS12 | VHT/HE MCS0  NSS2",
    "HT MCS13 | VHT/HE MCS1  NSS2",
    "HT MCS14 | VHT/HE MCS2  NSS2",
    "HT MCS15 | VHT/HE MCS3  NSS2",
    "HT N/A   | VHT/HE MCS4  NSS2",
    "HT N/A   | VHT/HE MCS5  NSS2",
    "HT N/A   | VHT/HE MCS6  NSS2",
    "HT N/A   | VHT/HE MCS7  NSS2",
    "HT N/A   | VHT/HE MCS8  NSS2",
    "HT N/A   | VHT/HE MCS9  NSS2",
    "HT N/A   | VHT/HE MCS10 NSS2",
    "HT N/A   | VHT/HE MCS11 NSS2",
};

#define NUM_EHT_RATES (sizeof(eht_rates)/sizeof(eht_rates[0]))
#define NUM_RATES (sizeof(rates)/sizeof(rates[0]))

#define RATE_SPEC_STR_LEN 10
#define RATE_SPEC_CHECK_INDEX 27
const char rate_stat_preamble[][RATE_SPEC_STR_LEN] = {
    "OFDM",
    "CCK",
    "HT",
    "VHT",
    "HE",
    "EHT"
};

const short int rate_stat_bandwidth[] = {
    20,
    40,
    80,
    160,
    320
};

int radios = 0;
int ml_links = 0;

wifi_radio_stat rx_stat[MAX_NUM_RADIOS];
wifi_channel_stat cca_stat[MAX_CH_BUF_SIZE];

void updateRateStats(u8 **buf, int num_rates) {
    printMsg("\nPrinting rate statistics: ");
    printMsg("------------------------------------------------------\n");
    printMsg("%40s %12s %14s %15s\n", "TX",  "RX", "LOST", "RETRIES");
    for (int k = 0; k < num_rates; k++) {
        if (!*buf) {
            ALOGE("No valid buf of rate_stats for index %d\n", k);
            continue;
        }
        wifi_rate_stat *local_ratestat_ptr = (wifi_rate_stat*)(*buf);
        if (!local_ratestat_ptr) {
            printMsg("rate stat data of index %d not found\n", k);
            continue;
        }
        if (num_rates == NUM_EHT_RATES) {
            printMsg("%-28s  %10d   %10d     %10d      %10d\n",
                eht_rates[k], local_ratestat_ptr->tx_mpdu, local_ratestat_ptr->rx_mpdu,
                    local_ratestat_ptr->mpdu_lost, local_ratestat_ptr->retries);
        } else if (num_rates == NUM_RATES) {
            printMsg("%-28s  %10d   %10d     %10d      %10d\n",
                rates[k], local_ratestat_ptr->tx_mpdu, local_ratestat_ptr->rx_mpdu,
                    local_ratestat_ptr->mpdu_lost, local_ratestat_ptr->retries);
        } else {
            printMsg("num_rates %d value is not supported\n", num_rates);
            continue;
        }
        *buf += sizeof(wifi_rate_stat);
    }
}

void printPeerinfoStats(wifi_peer_info *local_peer_ptr) {
    printMsg("Peer type = %d\n", local_peer_ptr->type);
    printMsg("Peer mac address: ( " MACSTR " )\n",
        MAC2STR(local_peer_ptr->peer_mac_address));
    printMsg("Peer Capabilities = %d\n", local_peer_ptr->capabilities);
    printMsg("Load_info(Station Count) = %d\n", local_peer_ptr->bssload.sta_count);
    printMsg("CCA_level(Channel Utilization) = %d\n", local_peer_ptr->bssload.chan_util);
    printMsg("Num rate %d \n", local_peer_ptr->num_rate);
    return;
}

void update_peer_info_per_link(u8 **buf) {
    wifi_peer_info *local_peer_ptr = (wifi_peer_info*)(*buf);
    if (!local_peer_ptr) {
        printMsg("peer data not found, skip\n");
        return;
    }

    printPeerinfoStats(local_peer_ptr);

    if (local_peer_ptr->num_rate) {
        *buf += offsetof(wifi_peer_info, rate_stats);
        if (!*buf) {
            ALOGE("No valid rate_stats\n");
            return;
        }
        updateRateStats(buf, local_peer_ptr->num_rate);
    }
}

void printPerLinkStats(wifi_link_stat *local_link_ptr, int link_id) {
    printMsg("Printing link statistics of the link:%d\n", link_id);
    printMsg("Identifier for the link = %d\n", local_link_ptr->link_id);
    printMsg("Radio on which link stats are sampled. = %d\n", local_link_ptr->radio);
    printMsg("Frequency on which link is operating. = %d MHz\n", local_link_ptr->frequency);
    printMsg("beacon_rx = %d\n", local_link_ptr->beacon_rx);
    printMsg("average_tsf_offset= %d\n", local_link_ptr->average_tsf_offset);
    printMsg("leaky_ap_detected= %d\n", local_link_ptr->leaky_ap_detected);
    printMsg("leaky_ap_avg_num_frames_leaked= %d\n",
        local_link_ptr->leaky_ap_avg_num_frames_leaked);
    printMsg("leaky_ap_guard_time= %d\n", local_link_ptr->leaky_ap_guard_time);
    printMsg("mgmt_rx= %d\n", local_link_ptr->mgmt_rx);
    printMsg("mgmt_action_rx= %d\n", local_link_ptr->mgmt_action_rx);
    printMsg("mgmt_action_tx= %d\n", local_link_ptr->mgmt_action_tx);
    printMsg("RSSI mgmt = %d\n", local_link_ptr->rssi_mgmt);
    printMsg("RSSI data = %d\n", local_link_ptr->rssi_data);
    printMsg("RSSI ack = %d\n", local_link_ptr->rssi_ack);
    printMsg("AC_BE:\n");
    printMsg("txmpdu = %d\n", local_link_ptr->ac[WIFI_AC_BE].tx_mpdu);
    printMsg("rxmpdu = %d\n", local_link_ptr->ac[WIFI_AC_BE].rx_mpdu);
    printMsg("mpdu_lost = %d\n", local_link_ptr->ac[WIFI_AC_BE].mpdu_lost);
    printMsg("retries = %d\n", local_link_ptr->ac[WIFI_AC_BE].retries);
    printMsg("AC_BK:\n");
    printMsg("txmpdu = %d\n", local_link_ptr->ac[WIFI_AC_BK].tx_mpdu);
    printMsg("rxmpdu = %d\n", local_link_ptr->ac[WIFI_AC_BK].rx_mpdu);
    printMsg("mpdu_lost = %d\n", local_link_ptr->ac[WIFI_AC_BK].mpdu_lost);
    printMsg("AC_VI:\n");
    printMsg("txmpdu = %d\n", local_link_ptr->ac[WIFI_AC_VI].tx_mpdu);
    printMsg("rxmpdu = %d\n", local_link_ptr->ac[WIFI_AC_VI].rx_mpdu);
    printMsg("mpdu_lost = %d\n", local_link_ptr->ac[WIFI_AC_VI].mpdu_lost);
    printMsg("AC_VO:\n");
    printMsg("txmpdu = %d\n", local_link_ptr->ac[WIFI_AC_VO].tx_mpdu);
    printMsg("rxmpdu = %d\n", local_link_ptr->ac[WIFI_AC_VO].rx_mpdu);
    printMsg("mpdu_lost = %d\n", local_link_ptr->ac[WIFI_AC_VO].mpdu_lost);
    printMsg("time slicing duty_cycle = %d\n", local_link_ptr->time_slicing_duty_cycle_percent);
    printMsg("Num peers = %d\n", local_link_ptr->num_peers);
}

void update_per_link_data(u8 **buf, int link_id) {
    wifi_link_stat *local_link_ptr = (wifi_link_stat*)(*buf);
    if (!local_link_ptr) {
       printMsg("link data not found, skip\n");
       return;
    }

    printPerLinkStats(local_link_ptr, link_id);

    if (local_link_ptr->num_peers) {
        for (int j = 0; j < local_link_ptr->num_peers; j++) {
            *buf += offsetof(wifi_link_stat, peer_info);
            if (!*buf) {
                ALOGE("No valid peer info\n");
                continue;
            }
            update_peer_info_per_link(buf);
        }
    }
}

void onMultiLinkStatsResults(wifi_request_id id, wifi_iface_ml_stat *iface_ml_stat,
        int num_radios, wifi_radio_stat *radio_stat)
{
    u8 *local_rx_ptr = NULL, *local_cca_ptr = NULL, *buf_ptr = NULL;
    int channel_size = 0, num_channels = 0;
    int cca_avail_size = MAX_CH_BUF_SIZE;

    if (!num_radios || !iface_ml_stat || !radio_stat) {
        ALOGE("No valid radio stat data\n");
        return;
    }

    radios = num_radios;
    local_rx_ptr = (u8*)radio_stat;
    local_cca_ptr = (u8*)cca_stat;

    for (int i = 0; i < num_radios; i++) {
        memset(&rx_stat[i], 0, sizeof(&rx_stat[i]));
        memcpy(&rx_stat[i], (u8*)local_rx_ptr, offsetof(wifi_radio_stat, channels));
        local_rx_ptr += offsetof(wifi_radio_stat, channels);
        num_channels = rx_stat[i].num_channels;
        if (num_channels) {
            channel_size = sizeof(wifi_channel_stat)*num_channels;
	    if (cca_avail_size > num_channels) {
                memcpy(local_cca_ptr, (u8*)local_rx_ptr, channel_size);
                cca_avail_size -= num_channels;
            } else {
                ALOGE("No space left for chan_stat!!: cca_avail: %d, req: %d\n",
                cca_avail_size, num_channels);
                break;
	    }
	}
        if (i == (num_radios - 1)) {
            break;
        }
        local_rx_ptr += channel_size;
        local_cca_ptr += channel_size;
    }
    /* radio stat data and channel stats data is printed in printMultiLinkStats */

    buf_ptr = (u8*)iface_ml_stat;
    ml_links = iface_ml_stat->num_links;

    if (ml_links) {
        buf_ptr += offsetof(wifi_iface_ml_stat, links);
        for (int i = 0; i < ml_links; i++) {
            if (!buf_ptr) {
                ALOGE("No valid multilink data\n");
                continue;
            }
            printMsg("-----------------------------------------------------\n\n");
            update_per_link_data(&buf_ptr, i);
        }
    }
}

wifi_iface_stat link_stat;
int num_rate;
bssload_info_t bssload;
wifi_peer_info peer_info[32];
wifi_rate_stat rate_stat[NUM_RATES];
wifi_rate_stat eht_rate_stat[NUM_EHT_RATES];

void onLinkStatsResults(wifi_request_id id, wifi_iface_stat *iface_stat,
        int num_radios, wifi_radio_stat *radio_stat)
{
    int num_peer = 0;
    u8 *local_rx_stat_ptr = NULL, *local_cca_ptr = NULL;
    int channel_size = 0, num_channels = 0;
    int cca_avail_size = MAX_CH_BUF_SIZE;

    if (!num_radios || !iface_stat || !radio_stat) {
        ALOGE("No valid radio stat data\n");
        return;
    }

    radios = num_radios;
    local_rx_stat_ptr = (u8*)radio_stat;
    local_cca_ptr = (u8*)cca_stat;
    for (int i = 0; i < num_radios; i++) {
        memset(&rx_stat[i], 0, sizeof(&rx_stat[i]));
        memcpy(&rx_stat[i], (u8*)local_rx_stat_ptr, offsetof(wifi_radio_stat, channels));
        local_rx_stat_ptr += offsetof(wifi_radio_stat, channels);
        num_channels = rx_stat[i].num_channels;
        if (num_channels) {
            channel_size = sizeof(wifi_channel_stat)*num_channels;
            if (cca_avail_size > num_channels) {
                memcpy(local_cca_ptr, (u8*)local_rx_stat_ptr, channel_size);
                cca_avail_size -= num_channels;
            } else {
                ALOGE("No space left for chan_stat!!: cca_avail: %d, req: %d\n",
                cca_avail_size, num_channels);
                break;
            }
        }
        if (i == (num_radios - 1)) {
            break;
        }
        local_rx_stat_ptr += channel_size;
        local_cca_ptr += channel_size;
    }

    num_peer = iface_stat->num_peers;
    printMsg("onLinkStatsResults num_peer = %d \n", num_peer);
    memset(&link_stat, 0, sizeof(wifi_iface_stat));
    memcpy(&link_stat, iface_stat, sizeof(wifi_iface_stat));
    memcpy(peer_info, iface_stat->peer_info, num_peer*sizeof(wifi_peer_info));
    num_rate = peer_info[0].num_rate;
    printMsg("onLinkStatsResults num_rate = %d \n", num_rate);

    memset(&bssload, 0, sizeof(bssload_info_t));
    memcpy(&bssload, &iface_stat->peer_info->bssload, sizeof(bssload_info_t));

    if (num_rate == NUM_EHT_RATES) {
        memset(eht_rate_stat, 0, num_rate*sizeof(wifi_rate_stat));
        memcpy(&eht_rate_stat, iface_stat->peer_info->rate_stats, num_rate*sizeof(wifi_rate_stat));
    } else if (num_rate == NUM_RATES) {
        memset(rate_stat, 0, num_rate*sizeof(wifi_rate_stat));
        memcpy(&rate_stat, iface_stat->peer_info->rate_stats, num_rate*sizeof(wifi_rate_stat));
    }
}

void printFeatureListBitMask(void)
{
    printMsg("WIFI_FEATURE_INFRA              0x000000001 - Basic infrastructure mode\n");
    printMsg("WIFI_FEATURE_INFRA_5G           0x000000002 - Support for 5 GHz Band\n");
    printMsg("WIFI_FEATURE_HOTSPOT            0x000000004 - Support for GAS/ANQP\n");
    printMsg("WIFI_FEATURE_P2P                0x000000008 - Wifi-Direct\n");
    printMsg("WIFI_FEATURE_SOFT_AP            0x000000010 - Soft AP\n");
    printMsg("WIFI_FEATURE_GSCAN              0x000000020 - Google-Scan APIs\n");
    printMsg("WIFI_FEATURE_NAN                0x000000040 - Neighbor Awareness Networking\n");
    printMsg("WIFI_FEATURE_D2D_RTT            0x000000080 - Device-to-device RTT\n");
    printMsg("WIFI_FEATURE_D2AP_RTT           0x000000100 - Device-to-AP RTT\n");
    printMsg("WIFI_FEATURE_BATCH_SCAN         0x000000200 - Batched Scan (legacy)\n");
    printMsg("WIFI_FEATURE_PNO                0x000000400 - Preferred network offload\n");
    printMsg("WIFI_FEATURE_ADDITIONAL_STA     0x000000800 - Support for two STAs\n");
    printMsg("WIFI_FEATURE_TDLS               0x000001000 - Tunnel directed link setup\n");
    printMsg("WIFI_FEATURE_TDLS_OFFCHANNEL    0x000002000 - Support for TDLS off channel\n");
    printMsg("WIFI_FEATURE_EPR                0x000004000 - Enhanced power reporting\n");
    printMsg("WIFI_FEATURE_AP_STA             0x000008000 - Support for AP STA Concurrency\n");
    printMsg("WIFI_FEATURE_LINK_LAYER_STATS   0x000010000 - Link layer stats collection\n");
    printMsg("WIFI_FEATURE_LOGGER             0x000020000 - WiFi Logger\n");
    printMsg("WIFI_FEATURE_HAL_EPNO           0x000040000 - iFi PNO enhanced\n");
    printMsg("WIFI_FEATURE_RSSI_MONITOR       0x000080000 - RSSI Monitor\n");
    printMsg("WIFI_FEATURE_MKEEP_ALIVE        0x000100000 - WiFi mkeep_alive\n");
    printMsg("WIFI_FEATURE_CONFIG_NDO         0x000200000 - ND offload configure\n");
    printMsg("WIFI_FEATURE_TX_TRANSMIT_POWER  0x000400000 - apture Tx transmit power levels\n");
    printMsg("WIFI_FEATURE_CONTROL_ROAMING    0x000800000 - Enable/Disable firmware roaming\n");
    printMsg("WIFI_FEATURE_IE_WHITELIST       0x001000000 - Support Probe IE white listing\n");
    printMsg("WIFI_FEATURE_SCAN_RAND          0x002000000 - Support MAC & Probe Sequence Number randomization\n");
    printMsg("WIFI_FEATURE_SET_TX_POWER_LIMIT 0x004000000 - Support Tx Power Limit setting\n");
    printMsg("WIFI_FEATURE_USE_BODY_HEAD_SAR  0x008000000 - Support Using Body/Head Proximity for SAR\n");
    printMsg("WIFI_FEATURE_DYNAMIC_SET_MAC    0x010000000 - Support changing MAC address without iface reset(down and up)\n");
    printMsg("WIFI_FEATURE_SET_LATENCY_MODE   0x040000000 - Support Latency mode setting\n");
    printMsg("WIFI_FEATURE_P2P_RAND_MAC       0x080000000 - Support P2P MAC randomization\n");
    printMsg("WIFI_FEATURE_INFRA_60G          0x100000000 - Support for 60GHz Band\n");
}

void printRadioComboMatrix(wifi_radio_combination_matrix *rc)
{
    u32 num_radio_combinations = rc->num_radio_combinations;
    wifi_radio_combination *radio_combinations = rc->radio_combinations;
    u32 num_radio_configurations;
    int i,j;

    printMsg("printing band info for combinations:%d\n", num_radio_combinations);

    for (i=0; i < num_radio_combinations; i++) {
        num_radio_configurations = radio_combinations->num_radio_configurations;
        printMsg("combination:%d num_radio_configurations:%d\n", i, num_radio_configurations);
        for (j=0; j < num_radio_configurations; j++) {
            printMsg("band:%s (%d) antenna cfg:%s (%d)\n",
                BandToString(radio_combinations->radio_configurations[j].band),
                radio_combinations->radio_configurations[j].band,
                AntennCfgToString(radio_combinations->radio_configurations[j].antenna_cfg),
                radio_combinations->radio_configurations[j].antenna_cfg);
        }

        if (j == (num_radio_combinations - 1)) {
            break;
        }
        radio_combinations = (wifi_radio_combination *)((u8*)radio_combinations + sizeof(u32) +
            (num_radio_configurations * sizeof(wifi_radio_configuration)));
        if (!radio_combinations) {
            break;
        }
    }
    return;
}

#define CHAN_STR_LEN 10
static char chan_str[CHAN_STR_LEN];

static char* frequency_to_channel(int center_freq)
{
    if (center_freq >= 2412 && center_freq <= 2484) {
        if (center_freq == 2484) {
            snprintf(chan_str, CHAN_STR_LEN, "2g/ch14");
        } else {
            snprintf(chan_str, CHAN_STR_LEN, "2g/ch%d", (center_freq - 2407) / 5);
        }
    } else if (center_freq >= 5180 && center_freq <= 5825) {
        snprintf(chan_str, CHAN_STR_LEN, "5g/ch%d", (center_freq - 5000) / 5);
    } else if (center_freq >= 5845 && center_freq <= 5885) {
        /* UNII-4 channels */
        snprintf(chan_str, CHAN_STR_LEN, "5g/ch%d", (center_freq - 5000) / 5);
    } else if (center_freq >= 5935 && center_freq <= 7115) {
        if (center_freq == 5935) {
            snprintf(chan_str, CHAN_STR_LEN, "6g/ch2");
        } else {
            snprintf(chan_str, CHAN_STR_LEN, "6g/ch%d", (center_freq - 5950) / 5);
        }
    } else {
        snprintf(chan_str, CHAN_STR_LEN, "Err");
    }

   return &chan_str[0];
}

void printMultiLinkStats(wifi_channel_stat cca_stat[],
    wifi_radio_stat rx_stat[], int radios)
{
    int new_chan_base = 0;
    printMsg("\nPrinting radio statistics of multi link\n");
    printMsg("--------------------------------------\n");
    for (int i = 0; i < radios; i++) {
        printMsg("radio = %d\n", rx_stat[i].radio);
        printMsg("on time = %d\n", rx_stat[i].on_time);
        printMsg("tx time = %d\n", rx_stat[i].tx_time);
        printMsg("num_tx_levels = %d\n", rx_stat[i].num_tx_levels);
        printMsg("rx time = %d\n", rx_stat[i].rx_time);
        printMsg("SCAN\n");
        printMsg("on_time_scan(duration)= %d\n", rx_stat[i].on_time_scan);
        printMsg("on_time_nbd(duration)= %d\n", rx_stat[i].on_time_nbd);
        printMsg("on_time_gscan(duration)= %d\n", rx_stat[i].on_time_gscan);
        printMsg("on_time_roam_scan(duration)= %d\n", rx_stat[i].on_time_roam_scan);
        printMsg("on_time_pno_scan(duration)= %d\n", rx_stat[i].on_time_pno_scan);
        printMsg("on_time_hs20 = %d\n", rx_stat[i].on_time_hs20);
        printMsg("cca channel statistics: (num_channels: %d)\n", rx_stat[i].num_channels);
        for (int j = new_chan_base; j < (new_chan_base + rx_stat[i].num_channels); j++) {
            printMsg("center_freq=%d (%8s), radio_on_time %10d, cca_busytime %10d\n",
                cca_stat[j].channel.center_freq,
                frequency_to_channel(cca_stat[j].channel.center_freq),
                cca_stat[j].on_time, cca_stat[j].cca_busy_time);
        }
        new_chan_base += rx_stat[i].num_channels;
    }
    printMsg("\n");
}
/////////////////////////////////////////////////////////////////////
void printLinkStats(wifi_iface_stat *link_stat, wifi_channel_stat cca_stat[],
    wifi_radio_stat rx_stat[], bssload_info_t *bssload, int radios)
{
    int new_chan_base = 0;
    printMsg("Printing link layer statistics:\n");
    printMsg("--------------------------------------\n");
    printMsg("Num peer = %d\n", link_stat->num_peers);
    printMsg("beacon_rx = %d\n", link_stat->beacon_rx);
    printMsg("RSSI = %d\n", link_stat->rssi_mgmt);
    printMsg("Load_info(Station Count) = %d\n", bssload->sta_count);
    printMsg("CCA_level(Channel Utilization) = %d\n", bssload->chan_util);
    printMsg("AC_BE:\n");
    printMsg("txmpdu = %d\n", link_stat->ac[WIFI_AC_BE].tx_mpdu);
    printMsg("rxmpdu = %d\n", link_stat->ac[WIFI_AC_BE].rx_mpdu);
    printMsg("mpdu_lost = %d\n", link_stat->ac[WIFI_AC_BE].mpdu_lost);
    printMsg("retries = %d\n", link_stat->ac[WIFI_AC_BE].retries);
    printMsg("AC_BK:\n");
    printMsg("txmpdu = %d\n", link_stat->ac[WIFI_AC_BK].tx_mpdu);
    printMsg("rxmpdu = %d\n", link_stat->ac[WIFI_AC_BK].rx_mpdu);
    printMsg("mpdu_lost = %d\n", link_stat->ac[WIFI_AC_BK].mpdu_lost);
    printMsg("AC_VI:\n");
    printMsg("txmpdu = %d\n", link_stat->ac[WIFI_AC_VI].tx_mpdu);
    printMsg("rxmpdu = %d\n", link_stat->ac[WIFI_AC_VI].rx_mpdu);
    printMsg("mpdu_lost = %d\n", link_stat->ac[WIFI_AC_VI].mpdu_lost);
    printMsg("AC_VO:\n");
    printMsg("txmpdu = %d\n", link_stat->ac[WIFI_AC_VO].tx_mpdu);
    printMsg("rxmpdu = %d\n", link_stat->ac[WIFI_AC_VO].rx_mpdu);
    printMsg("mpdu_lost = %d\n", link_stat->ac[WIFI_AC_VO].mpdu_lost);
    printMsg("time slicing duty_cycle = %d\n", link_stat->info.time_slicing_duty_cycle_percent);
    printMsg("\n");
    printMsg("Printing radio statistics:\n");
    printMsg("--------------------------------------\n");
    for (int i = 0; i < radios; i++) {
        printMsg("--------------------------------------\n");
        printMsg("radio = %d\n", rx_stat[i].radio);
        printMsg("on time = %d\n", rx_stat[i].on_time);
        printMsg("tx time = %d\n", rx_stat[i].tx_time);
        printMsg("num_tx_levels = %d\n", rx_stat[i].num_tx_levels);
        printMsg("rx time = %d\n", rx_stat[i].rx_time);
        printMsg("SCAN\n");
        printMsg("on_time_scan(duration)= %d\n", rx_stat[i].on_time_scan);
        printMsg("on_time_nbd(duration)= %d\n", rx_stat[i].on_time_nbd);
        printMsg("on_time_gscan(duration)= %d\n", rx_stat[i].on_time_gscan);
        printMsg("on_time_roam_scan(duration)= %d\n", rx_stat[i].on_time_roam_scan);
        printMsg("on_time_pno_scan(duration)= %d\n", rx_stat[i].on_time_pno_scan);
        printMsg("on_time_hs20 = %d\n", rx_stat[i].on_time_hs20);
        printMsg("cca channel statistics: (num_channels: %d)\n", rx_stat[i].num_channels);
        for (int j = new_chan_base; j < (new_chan_base + rx_stat[i].num_channels); j++) {
            printMsg("center_freq=%d (%8s), radio_on_time %10d, cca_busytime %10d\n",
                cca_stat[j].channel.center_freq,
                frequency_to_channel(cca_stat[j].channel.center_freq),
                cca_stat[j].on_time, cca_stat[j].cca_busy_time);
        }
        new_chan_base += rx_stat[i].num_channels;
    }
    printMsg("\n");
    if (num_rate == NUM_EHT_RATES) {
        printMsg("(current BSS info: %s, %dMhz)\n",
            rate_stat_preamble[eht_rate_stat[RATE_SPEC_CHECK_INDEX].rate.preamble],
            rate_stat_bandwidth[eht_rate_stat[RATE_SPEC_CHECK_INDEX].rate.bw]);
    } else if (num_rate == NUM_RATES) {
        printMsg("(current BSS info: %s, %dMhz)\n",
            rate_stat_preamble[rate_stat[RATE_SPEC_CHECK_INDEX].rate.preamble],
            rate_stat_bandwidth[rate_stat[RATE_SPEC_CHECK_INDEX].rate.bw]);
    } else {
        printMsg("No peer found!");
        return;
    }
    printMsg("Printing rate statistics: num_rate %d", num_rate);
    printMsg("--------------------------------------\n");
    printMsg("%40s %12s %14s %15s\n", "TX",  "RX", "LOST", "RETRIES");
    for (int i=0; i < num_rate; i++) {
        if (num_rate == NUM_EHT_RATES) {
            printMsg("%-28s  %10d   %10d     %10d      %10d\n",
                eht_rates[i], eht_rate_stat[i].tx_mpdu, eht_rate_stat[i].rx_mpdu,
                eht_rate_stat[i].mpdu_lost, eht_rate_stat[i].retries);
	} else if (num_rate == NUM_RATES) {
            printMsg("%-28s  %10d   %10d     %10d      %10d\n",
                rates[i], rate_stat[i].tx_mpdu, rate_stat[i].rx_mpdu,
                rate_stat[i].mpdu_lost, rate_stat[i].retries);
        }
    }
}

void getLinkStats(void)
{
    wifi_stats_result_handler handler;
    memset(&handler, 0, sizeof(handler));

    handler.on_link_stats_results = &onLinkStatsResults;
    handler.on_multi_link_stats_results = &onMultiLinkStatsResults;

    int result = hal_fn.wifi_get_link_stats(0, wlan0Handle, handler);
    if (result < 0) {
        printMsg("failed to get link stat - %d\n", result);
    } else if (!radios) {
        printMsg("Invalid link stat data\n");
    } else if (ml_links) {
        printMultiLinkStats(cca_stat, rx_stat, radios);
    } else {
        printLinkStats(&link_stat, cca_stat, rx_stat, &bssload, radios);
    }
}

void getChannelList(void)
{
    wifi_channel channel[MAX_CH_BUF_SIZE] =  {0, };
    int num_channels = 0, i = 0;

    int result = hal_fn.wifi_get_valid_channels(wlan0Handle, band, MAX_CH_BUF_SIZE,
            channel, &num_channels);
    if (result < 0) {
        printMsg("failed to get valid channels %d\n", result);
        return;
    }
    printMsg("Number of channels - %d\nChannel List:\n",num_channels);
    for (i = 0; i < num_channels; i++) {
        printMsg("%d MHz\n", channel[i]);
    }
}

void getFeatureSet(void)
{
    feature_set set;
    int result = hal_fn.wifi_get_supported_feature_set(wlan0Handle, &set);

    if (result < 0) {
        printMsg("Error %d\n",result);
        return;
    }
    printFeatureListBitMask();
    printMsg("Supported feature set bit mask - %x\n", set);
    return;
}

void getFeatureSetMatrix(void)
{
    feature_set set[MAX_FEATURE_SET];
    int size;

    int result = hal_fn.wifi_get_concurrency_matrix(wlan0Handle, MAX_FEATURE_SET, set, &size);

    if (result < 0) {
        printMsg("Error %d\n",result);
        return;
    }
    printFeatureListBitMask();
    for (int i = 0; i < size; i++)
        printMsg("Concurrent feature set - %x\n", set[i]);
    return;
}

void getSupportedRadioMatrix(void)
{
    wifi_radio_combination_matrix *radio_matrix;
    u32 size, max_size = 0;
    int result;

    max_size = sizeof(wifi_radio_combination_matrix) +
        MAX_RADIO_COMBO*(sizeof(wifi_radio_combination) +
        MAX_CORE * sizeof(wifi_radio_configuration));

    radio_matrix = (wifi_radio_combination_matrix*)malloc(max_size);
    if (!radio_matrix) {
        printMsg("%s:Malloc failed\n",__FUNCTION__);
        return;
    }
    memset(radio_matrix, 0 , max_size);

    result = hal_fn.wifi_get_supported_radio_combinations_matrix(halHandle,
        max_size, &size, radio_matrix);
    if (!radio_matrix || !size || result < 0) {
        printMsg("Error %d\n", result);
        goto free_mem;
    }

    printRadioComboMatrix(radio_matrix);

free_mem:
    if (radio_matrix) {
        free(radio_matrix);
    }

    return;
}

int getWakeStats()
{
    WLAN_DRIVER_WAKE_REASON_CNT *wake_reason_cnt;

    wake_reason_cnt = (WLAN_DRIVER_WAKE_REASON_CNT*) malloc(sizeof(WLAN_DRIVER_WAKE_REASON_CNT));
    if (!wake_reason_cnt) {
        printMsg("%s:Malloc failed\n",__FUNCTION__);
        return WIFI_ERROR_OUT_OF_MEMORY;
    }
    memset(wake_reason_cnt, 0 , sizeof(WLAN_DRIVER_WAKE_REASON_CNT));
    wake_reason_cnt->cmd_event_wake_cnt_sz = EVENT_COUNT;
    wake_reason_cnt->cmd_event_wake_cnt = (int*)malloc(EVENT_COUNT * sizeof(int));

    int result = hal_fn.wifi_get_wake_reason_stats(wlan0Handle, wake_reason_cnt);
    if (result < 0) {
        printMsg("Error %d\n",result);
        free(wake_reason_cnt->cmd_event_wake_cnt);
        free(wake_reason_cnt);
        return WIFI_ERROR_NOT_SUPPORTED;
    }

    printMsg(" ------- DRIVER WAKE REASON STATS -------\n");
    printMsg("TotalCmdWake = %d\n", wake_reason_cnt->total_cmd_event_wake);
    printMsg("MaxCmdEvent  = %d\n", wake_reason_cnt->cmd_event_wake_cnt_sz);
    printMsg("MaxCmdEventUsed = %d\n", wake_reason_cnt->cmd_event_wake_cnt_used);
    printMsg("-----------------------------------------------------------\n");
    printMsg("TotalRxDataWake = %d\n", wake_reason_cnt->total_rx_data_wake);
    printMsg("RxUniCnt = %d\n", wake_reason_cnt->rx_wake_details.rx_unicast_cnt);
    printMsg("RxMultiCnt = %d\n", wake_reason_cnt->rx_wake_details.rx_multicast_cnt);
    printMsg("RxBcastCnt = %d\n", wake_reason_cnt->rx_wake_details.rx_broadcast_cnt);
    printMsg("-----------------------------------------------------------\n");
    printMsg("ICMP count = %d\n", wake_reason_cnt->rx_wake_pkt_classification_info.icmp_pkt);
    printMsg("ICMP6 count = %d\n", wake_reason_cnt->rx_wake_pkt_classification_info.icmp6_pkt);
    printMsg("ICMP6 ra count = %d\n", wake_reason_cnt->rx_wake_pkt_classification_info.icmp6_ra);
    printMsg("ICMP6 na count = %d\n", wake_reason_cnt->rx_wake_pkt_classification_info.icmp6_na);
    printMsg("ICMP6 ns count = %d\n", wake_reason_cnt->rx_wake_pkt_classification_info.icmp6_ns);
    printMsg("-----------------------------------------------------------\n");
    printMsg("Rx IPV4 Mcast  = %d\n", wake_reason_cnt->rx_multicast_wake_pkt_info.ipv4_rx_multicast_addr_cnt);
    printMsg("Rx IPV6 Mcast = %d\n", wake_reason_cnt->rx_multicast_wake_pkt_info.ipv6_rx_multicast_addr_cnt);
    printMsg("Rx Other Mcast = %d\n", wake_reason_cnt->rx_multicast_wake_pkt_info.other_rx_multicast_addr_cnt);
    printMsg("-----------------------------------------------------------\n");
    printMsg("Events Received and received count\n");
    for (int i = 0; i <= wake_reason_cnt->cmd_event_wake_cnt_used; i++) {
        if (wake_reason_cnt->cmd_event_wake_cnt[i] != 0)
            printMsg("Event ID %d = %u\n", i, wake_reason_cnt->cmd_event_wake_cnt[i]);
    }

    free(wake_reason_cnt->cmd_event_wake_cnt);
    free(wake_reason_cnt);
    return WIFI_SUCCESS;
}

static wifi_error setBlacklist(bool clear)
{
    if (num_blacklist_bssids == -1 && !clear)
        return WIFI_SUCCESS;
    wifi_roaming_config roam_config;
    cmdId = getNewCmdId();
    if (clear) {
        roam_config.num_blacklist_bssid = 0;
        printMsg("Clear Blacklist BSSIDs\n");
    } else {
        roam_config.num_blacklist_bssid = num_blacklist_bssids;
        printMsg("Setting %d Blacklist BSSIDs\n", num_blacklist_bssids);
    }
    for (int i = 0; i < num_blacklist_bssids; i++) {
        memcpy(&roam_config.blacklist_bssid[i], &blacklist_bssids[i],
        sizeof(mac_addr) );
    }
    return  hal_fn.wifi_configure_roaming(wlan0Handle, &roam_config);
}

static wifi_error setRoamingConfiguration()
{
    wifi_error ret;
    wifi_roaming_config roam_config;
    cmdId = getNewCmdId();

    roam_config.num_blacklist_bssid = num_blacklist_bssids;
    roam_config.num_whitelist_ssid = num_whitelist_ssids;
    if(num_blacklist_bssids != -1) {
        for (int i = 0; i < num_blacklist_bssids; i++) {
            memcpy(&roam_config.blacklist_bssid[i], &blacklist_bssids[i], sizeof(mac_addr) );
        }
    }

    if(num_whitelist_ssids != -1) {
        for (int j = 0; j < num_whitelist_ssids; j++) {
            printf("%s\n", whitelist_ssids[j]);
            strncpy(roam_config.whitelist_ssid[j].ssid_str, whitelist_ssids[j], (MAX_SSID_LENGTH - 1));
            roam_config.whitelist_ssid[j].ssid_str[MAX_SSID_LENGTH - 1] = '\0';
            roam_config.whitelist_ssid[j].length =
                strlen(roam_config.whitelist_ssid[j].ssid_str);
        }
    }

    ret = hal_fn.wifi_configure_roaming(wlan0Handle, &roam_config);

    return ret;
}

static wifi_error getRoamingCapabilities()
{
    wifi_error ret;
    wifi_roaming_capabilities roam_capability;

    ret = hal_fn.wifi_get_roaming_capabilities(wlan0Handle, &roam_capability);
    if (ret == WIFI_SUCCESS) {
        printMsg("Roaming Capabilities\n"
            "max_blacklist_size = %d\n"
            "max_whitelist_size = %d\n", roam_capability.max_blacklist_size,
            roam_capability.max_whitelist_size);
    } else {
        printMsg("Failed to get Roaming capabilities\n");
    }
    return ret;
}

static wifi_error setFWRoamingState(fw_roaming_state_t state)
{
    wifi_error ret = WIFI_SUCCESS;

    return ret;
}

static void testRssiMonitor()
{
    int id = -1;
    wifi_rssi_event_handler handler;
    EventInfo info;
    handler.on_rssi_threshold_breached = onRssiThresholdbreached;
    if (rssi_monitor) {
        rssiMonId = getNewCmdId();
        wifi_error ret = hal_fn.wifi_start_rssi_monitoring(rssiMonId, wlan0Handle,
                    max_rssi, min_rssi, handler);
        if (ret != WIFI_SUCCESS) {
            printMsg("Failed to set RSSI monitor %d\n", ret);
            return;
        }
        printMsg("rssi_monitor: %d %d %d %d\n", rssi_monitor, max_rssi, min_rssi,rssiMonId);
        while (true) {
            memset(&info, 0, sizeof(info));
            getEventFromCache(info);
            if (info.type == EVENT_TYPE_RSSI_MONITOR) {
                printMsg("done!!\n");
                break;
            }
        }
    } else {
        if (rssiMonId == 0)
            id = -1;
        hal_fn.wifi_stop_rssi_monitoring(id, wlan0Handle);
    }
    return;
}

static wifi_error setApfProgram(char *str, wifi_interface_handle ifHandle)
{
    u32 program_len;
    u8* program;
    wifi_error ret;

    if (str == NULL) {
        printMsg("APF program missing\n");
        printApfUsage();
        return WIFI_ERROR_UNINITIALIZED;
    }

    if (strncmp(str, "0x", 2) == 0 || strncmp(str, "0X", 2) == 0) {
        str = str + 2; /* Skip past 0x */
    }
    program_len = (strlen((const char *)str) / 2);
    program = (u8 *)malloc(program_len);
    if (!program) {
        printMsg("Memory allocation failed\n");
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    if ((u32)str2hex(str, (char *)program) != program_len) {
        printMsg("Invalid APF program\n");
        if (program) {
            free(program);
        }
        return WIFI_ERROR_INVALID_ARGS;
    }

    ret = hal_fn.wifi_set_packet_filter(ifHandle, program, program_len);
    if (ret != WIFI_SUCCESS) {
        if (program) {
            free(program);
        }
        printMsg("Failed to set APF program, ret = %d\n", ret);
        return WIFI_ERROR_NOT_SUPPORTED;
    }

    if (program) {
        free(program);
    }

    return ret;
}

static wifi_error getApfCapabilities(wifi_interface_handle ifHandle)
{
    u32 version, max_len;

    wifi_error ret = hal_fn.wifi_get_packet_filter_capabilities(ifHandle,
        &version, &max_len);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to get APF capability, ret = %d\n", ret);
        return WIFI_ERROR_NOT_SUPPORTED;
    }
    printMsg("APF capabilities, version = %u, max_len = %u bytes\n",
        version, max_len);
    return WIFI_SUCCESS;
}

static wifi_error getApfFilterData(wifi_interface_handle ifHandle)
{
    u8 *buf, *pc;
    u32 version, max_len, i;
    wifi_error ret;

    ret = hal_fn.wifi_get_packet_filter_capabilities(ifHandle,
            &version, &max_len);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to get APF buffer size, ret = %d\n", ret);
        return WIFI_ERROR_NOT_SUPPORTED;
    }

    buf = (u8 *)malloc(max_len);
    if (!buf) {
        printMsg("Memory allocation failed\n");
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    ret = hal_fn.wifi_read_packet_filter(ifHandle, 0, buf, max_len);
    if (ret != WIFI_SUCCESS) {
        if (buf) {
            free(buf);
        }
        printMsg("Failed to get APF buffer dump, ret = %d\n", ret);
        return WIFI_ERROR_NOT_SUPPORTED;
    }
    printMsg("\nAPF buffer size = %u (0x%x) bytes\n\n", max_len, max_len);
    printMsg("APF buffer data =\n\n");
    for (i = 1, pc = buf; pc && (i <= max_len); pc++, i++) {
       printf("%02X", *pc);
       if (i && ((i % 64) == 0)) {
           printf("\n");
       }
    }
    printf("\n");

    if (buf) {
        free(buf);
    }
    return WIFI_SUCCESS;
}

int testApfOptions(int argc, char *argv[])
{
    void printApfUsage();          // declaration for below printUsage()
    /* Interface name */
    char iface_name[IFNAMSIZ+1];
    char *val_p = NULL;
    wifi_error ret;
    wifi_interface_handle ifHandle = NULL;

    argv++; /* skip utility */
    argv++; /* skip -apf command */

    val_p = *argv++;
    if (val_p != NULL) {
        if (!set_interface_params(iface_name, val_p, (IFNAMSIZ - 1))) {
            printMsg("set interface name successfull\n");
        } else {
            printMsg("Invalid  iface name\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto usage;
        }
    }

    ifHandle = wifi_get_iface_handle(halHandle, iface_name);
    if (ifHandle == NULL) {
        printMsg("Invalid iface handle for the requested interface\n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto usage;
    } else {
        while ((val_p = *argv++) != NULL) {
            if (!val_p) {
                printMsg("%s: Need value following %s\n", __FUNCTION__, val_p);
                ret = WIFI_ERROR_NOT_SUPPORTED;
                goto usage;
            }
            if (strcmp(val_p, "-set") == 0) {
                val_p = *argv++;
                if (strcmp(val_p, "program") == 0) {
                    val_p = *argv++;
                    printMsg("program = %s\n\n", val_p);
                    printMsg("set program capa: Iface handle = %p for the requested interface: %s\n",
                        ifHandle, iface_name);
                    if (ifHandle) {
                       ret = setApfProgram(val_p, ifHandle);
                    }
                } else {
                    printMsg("Invalid value after program arg\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto usage;
                }
            } else if (strcmp(val_p, "-get") == 0) {
                val_p = *argv++;
                if (strcmp(val_p, "capa") == 0) {
                    printMsg("get capa: Iface handle = %p for the requested interface: %s\n",
                        ifHandle, iface_name);
                    if (ifHandle) {
                        ret = getApfCapabilities(ifHandle);
                    }
                } else if (strcmp(val_p, "data") == 0) {
                    printMsg("get data: Iface handle = %p for the requested interface: %s\n",
                        ifHandle, iface_name);
                    if (ifHandle) {
                        ret = getApfFilterData(ifHandle);
                    }
                } else {
                    printMsg("Invalid value for get cmd\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto usage;
                }
            } else {
                printMsg("Invalid option for apf cmd\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto usage;
            }
        }
    }

usage:
    printApfUsage();
    return WIFI_ERROR_INVALID_ARGS;
}

static int setDscpMap(s32 start, s32 end, s32 ac)
{
    void printUsage();          // declaration for below printUsage()
    int ret;

    if (start == -1 || end == -1 || ac == -1) {
        printMsg("DSCP param missing\n");
        printUsage();
        return WIFI_ERROR_UNINITIALIZED;
    }

    ret = hal_fn.wifi_map_dscp_access_category(halHandle, start, end, ac);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to set DSCP: %d\n", ret);
        return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_SUCCESS;
}

static int resetDscpMap()
{
    int ret;

    ret = hal_fn.wifi_reset_dscp_mapping(halHandle);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to reset DSCP: %d\n", ret);
        return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_SUCCESS;
}

static int setChannelAvoidance(u32 num, wifi_coex_unsafe_channel configs[], u32 mandatory)
{
    int ret;

    ret = hal_fn.wifi_set_coex_unsafe_channels(halHandle, num, configs, mandatory);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to set Channel Avoidance: %d\n", ret);
        return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_SUCCESS;
}

void testSarOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()
    wifi_power_scenario mWifi_power_scenario;
    wifi_error res;
    int scenario;

    if (argc < 2) {
        goto usage;
    }

    if ((argc > 2) && (strcmp(argv[1], "enable") == 0)) {
        scenario = atoi(argv[2]);
        if ((scenario < WIFI_POWER_SCENARIO_DEFAULT) || (scenario > SAR_CONFIG_SCENARIO_COUNT)) {
            printMsg("Unsupported tx power value:%d: Allowed range -1 to 99\n", scenario);
            return;
        }
        mWifi_power_scenario = (wifi_power_scenario)scenario;
        res = hal_fn.wifi_select_tx_power_scenario(wlan0Handle, mWifi_power_scenario);
    } else if ((strcmp(argv[1], "disable") == 0)) {
        res = hal_fn.wifi_reset_tx_power_scenario(wlan0Handle);
    } else {
        goto usage;
    }

    if (res == WIFI_SUCCESS) {
        printMsg("Success to execute sar test command\n");
    } else {
        printMsg("Failed to execute sar test command, res = %d\n", res);
    }
    return;

usage:
    printUsage();
    return;
}

void testThermalMitigationOptions(int argc, char *argv[])
{
  void printUsage();  //declaration for below printUsage()
  wifi_thermal_mode mode;
  wifi_error result;

  if (argc < 2) {
      goto usage;
  }

  if (strcmp(argv[1], "none") == 0) {
      mode = WIFI_MITIGATION_NONE;
  } else if (strcmp(argv[1], "light") == 0) {
      mode = WIFI_MITIGATION_LIGHT;
  } else if (strcmp(argv[1], "moderate") == 0) {
      mode = WIFI_MITIGATION_MODERATE;
  } else if (strcmp(argv[1], "severe") == 0) {
      mode = WIFI_MITIGATION_SEVERE;
  } else if (strcmp(argv[1], "critical") == 0) {
      mode = WIFI_MITIGATION_CRITICAL;
  } else if (strcmp(argv[1], "emergency") == 0) {
      mode = WIFI_MITIGATION_EMERGENCY;
  } else {
      printMsg("unknown thermal mode %s\n", argv[1]);
      goto usage;
  }

  result = hal_fn.wifi_set_thermal_mitigation_mode(halHandle, mode, 0);
  if (result == WIFI_ERROR_NONE) {
      printMsg("Success set thermal mode\n");
  } else {
      printMsg("Failed set thermal mode, result = %d\n", result);
  }
  return;

usage:
  printUsage();
}

void testLatencyModeOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()
    wifi_latency_mode mWifi_latency_mode;
    wifi_error res;

    if (argc < 2) {
        goto usage;
    }

    if (strcmp(argv[1], "normal") == 0) {
        mWifi_latency_mode = WIFI_LATENCY_MODE_NORMAL;
    } else if (strcmp(argv[1], "low") == 0) {
        mWifi_latency_mode = WIFI_LATENCY_MODE_LOW;
    } else if (strcmp(argv[1], "ultra-low") == 0) {
        mWifi_latency_mode = (wifi_latency_mode)2 ; //TODO: To be removed
    } else {
        goto usage;
    }

    res = hal_fn.wifi_set_latency_mode(wlan0Handle, mWifi_latency_mode);
    if (res == WIFI_SUCCESS) {
        printMsg("Success to execute set wifi latency mode test command\n");
    } else {
        printMsg("Failed to execute set wifi latency mode test command, res = %d\n", res);
    }
    return;

usage:
    printUsage();
    return;
}

void testDscpOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()
    int j = 1;
    s32 start = -1;
    s32 end = -1;
    s32 ac = -1;

    if (argc < 3) {
        goto usage;
    }

    if ((strcmp(argv[j], "-reset") == 0) && (argc == 3)) {
        resetDscpMap();
    } else if ((strcmp(argv[j], "-set") == 0) && (argc == 9)) {
        if ((strcmp(argv[++j], "-s") == 0) && isdigit(argv[j+1][0]))
            start = atoi(argv[++j]);
        if ((strcmp(argv[++j], "-e") == 0) && isdigit(argv[j+1][0]))
            end = atoi(argv[++j]);
        if ((strcmp(argv[++j], "-ac") == 0) && isdigit(argv[j+1][0]))
            ac = atoi(argv[++j]);
        setDscpMap(start, end, ac);
    } else {
        goto usage;
    }
    return;

usage:
    printUsage();
    return;
}

static void printTxPowerUsage() {
    printf("Usage: halutil [OPTION]\n");
    printf(" -tx_pwr_cap -enable\n");
    printf(" -tx_pwr_cap -disable\n");
    return;
}

static void printApfUsage() {
    printf("Usage: halutil [OPTION]\n");
    printf(" -apf [-ifname] <interface name> [-get] [capa]\n");
    printf(" -apf [-ifname] <interface name> [-get] [data]\n");
    printf(" -apf [-ifname] <interface name> [-set] [program] <bytecodes>\n");
    return;
}

static void printTwtUsage() {
    printf("Usage: halutil [OPTION]\n");
    printf("halutil -twt -setup -config_id <> -neg_type <0 for individual TWT, 1 for broadcast TWT> "
            "-trigger_type <0 for non-triggered TWT, 1 for triggered TWT> "
            "-wake_dur_us <> -wake_int_us <> -wake_int_min_us <> "
            "-wake_int_max_us <> -wake_dur_min_us <> -wake_dur_max_us <> "
            "-avg_pkt_size <> -avg_pkt_num <> -wake_time_off_us <>\n");
    printf("halutil -twt -info_frame -config_id <>"
            " -all_twt <0 for individual setp request, 1 for all TWT> -resume_time_us <>\n");
    printf("halutil -twt -teardown -config_id <> -all_twt <> "
            " -neg_type <0 for individual TWT, 1 for broadcast TWT>\n");
    printf("halutil -twt -get_stats -config_id <>\n");
    printf("halutil -twt -clear_stats -config_id <>\n");
    printf("halutil -get_capa_twt\n");
    printf("halutil -twt -event_chk\n");
    return;
}

static void printChreNanRttUsage() {
    printf("Usage: halutil [OPTION]\n");
    printf("halutil -chre_nan_rtt -enable\n");
    printf("halutil -chre_nan_rtt -disable\n");
    printf("halutil -chre -register\n");
    return;
}

void
bandstr_to_macband(char *band_str, wlan_mac_band *band)
{
    if (!strcasecmp(band_str, "a")) {
        *band = WLAN_MAC_5_0_BAND;
    } else if (!strcasecmp(band_str, "b")) {
        *band = WLAN_MAC_2_4_BAND;
    } else if (!strcasecmp(band_str, "6g")) {
        *band = WLAN_MAC_6_0_BAND;
    } else if (!strcasecmp(band_str, "60")) {
        *band = WLAN_MAC_60_0_BAND;
    }
}
void
bandstr_to_band(char *band_str, u32 *band)
{
   if (!strcasecmp(band_str, "a")) {
       *band = WIFI_BAND_A;
   } else if (!strcasecmp(band_str, "b")) {
       *band = WIFI_BAND_BG;
   } else if (!strcasecmp(band_str, "all")) {
       *band = WIFI_BAND_ABG;
   } else {
       *band = WIFI_BAND_UNSPECIFIED;
   }
}

void parse_unsafe(char *val_p, wifi_coex_unsafe_channel ca_configs[], int *idx)
{
   char *space_end;
   char *comma_end;
   char *space = strtok_r(val_p, ":", &space_end);
   wlan_mac_band band;
   while (space != NULL) {
       char *token = strtok_r(space, ",", &comma_end);
       if (!token) {
           printf("Need 3 values\n");
           return;
       }
       bandstr_to_macband(token, &band);
       if (band != WLAN_MAC_2_4_BAND && band != WLAN_MAC_5_0_BAND) {
           printMsg("Unsupported band\n");
           return;
       }
       ca_configs[*idx].band = band;
       token = strtok_r(NULL, ",", &comma_end);
       if (!token) {
           printf("Need 3 values\n");
           return;
       }
       ca_configs[*idx].channel = atoi(token);
       token = strtok_r(NULL, ",", &comma_end);
       if (!token) {
           printf("Need 3 values\n");
           return;
       }
       ca_configs[*idx].power_cap_dbm = atoi(token);

       token = strtok_r(NULL, ",", &comma_end);
       if (token) {
           printf("Need only 3 values\n");
           return;
       }
       (*idx)++;

       space = strtok_r(NULL, ":", &space_end);
   }
}


void testChannelAvoidanceOptions(int argc, char *argv[])
{
    char *param;
    char *val_p;
    wifi_coex_unsafe_channel ca_configs[MAX_CH_AVOID];
    u32 mandatory;
    int idx = 0;
    memset(ca_configs, 0, MAX_CH_AVOID * sizeof(wifi_coex_unsafe_channel));

    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            return;
        }

        if (!strncmp(param, "-unsafe", 7)) {
            parse_unsafe(val_p, ca_configs, &idx);
        } else if (!strncmp(param, "-m", 2)) {
            mandatory = atoi(val_p);
        }
    }

    for (int i=0; i<idx; i++) {
        printMsg("CONFIG  %d %d %d\n",
            ca_configs[i].band, ca_configs[i].channel, ca_configs[i].power_cap_dbm);
    }

    setChannelAvoidance(idx, ca_configs, mandatory);
    return;
}

void
bandstr_to_wlan_mac_band(char *band_str, u32 *band)
{
   if (!strcasecmp(band_str, "2g")) {
       *band = WLAN_MAC_2_4_BAND;
   } else if (!strcasecmp(band_str, "5g")) {
       *band = WLAN_MAC_5_0_BAND;
   } else if (!strcasecmp(band_str, "6g")) {
       *band = WLAN_MAC_6_0_BAND;
   }
}

void
ifacestr_to_wifi_interface_mode(char *iface_str, u8 *mode)
{
   if (!strcasecmp(iface_str, "sta")) {
       *mode = WIFI_INTERFACE_STA;
   } else if (!strcasecmp(iface_str, "softap")) {
       *mode = WIFI_INTERFACE_SOFTAP;
   } else if (!strcasecmp(iface_str, "ibss")) {
       *mode = WIFI_INTERFACE_IBSS;
   } else if (!strcasecmp(iface_str, "p2p_cli")) {
       *mode = WIFI_INTERFACE_P2P_CLIENT;
   } else if (!strcasecmp(iface_str, "p2p_go")) {
       *mode = WIFI_INTERFACE_P2P_GO;
   } else if (!strcasecmp(iface_str, "nan")) {
       *mode = WIFI_INTERFACE_NAN;
   } else if (!strcasecmp(iface_str, "mesh")) {
       *mode = WIFI_INTERFACE_MESH;
   } else if (!strcasecmp(iface_str, "tdls")) {
       *mode = WIFI_INTERFACE_TDLS;
   } else {
       printMsg("Incorrect iface type."
           " ex: sta/softap/ibss/p2p_cli/p2p_go/nan/mesh/tdls\n");
       *mode = WIFI_INTERFACE_UNKNOWN;
   }
}

u32 usable_channel_parse_band(char *val_p)
{
    char *delim_end;
    char *delim = strtok_r(val_p, ",", &delim_end);
    u32 band;
    u32 band_mask = 0;
    while (delim != NULL) {
        band = 0;
        bandstr_to_wlan_mac_band(delim, &band);
        if (band) {
            band_mask |= band;
        }
        delim = strtok_r(NULL, ",", &delim_end);
    }
    return band_mask;
}

u32 usable_channel_parse_iface(char *val_p)
{
    char *delim_end;
    char *delim = strtok_r(val_p, ",", &delim_end);
    u8 iface_mode;
    u32 iface_mode_mask = 0;
    while (delim != NULL) {
        iface_mode = 0;
        ifacestr_to_wifi_interface_mode(delim, &iface_mode);
        if (iface_mode != WIFI_INTERFACE_UNKNOWN) {
            iface_mode_mask |= (1 << iface_mode);
            delim = strtok_r(NULL, ",", &delim_end);
        }
    }
    return iface_mode_mask;
}

static wifi_error testUsableChannelOptions(int argc, char *argv[])
{
    char *param;
    char *val_p;
    u32 band_mask;
    u32 iface_mode_mask;
    u32 filter_mask;
    u32 max_size = 0;
    u32 size;
    wifi_error ret;
    wifi_usable_channel* channels = NULL;

    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            return WIFI_ERROR_INVALID_ARGS;
        }

        if (!strncmp(param, "-b", 2)) {
            band_mask = usable_channel_parse_band(val_p);
        } else if (!strncmp(param, "-i", 2)) {
            iface_mode_mask = usable_channel_parse_iface(val_p);
        } else if (!strncmp(param, "-f", 2)) {
            filter_mask = atoi(val_p);
        } else if (!strncmp(param, "-m", 2)) {
            max_size = atoi(val_p);
        }
    }

    printMsg("Usable channel param BAND:%d IFACE:%d FILTER:%d MAX_SIZE:%d\n", band_mask, iface_mode_mask, filter_mask, max_size);
    if (max_size == 0) {
        printMsg("Max size should be bigger than 0\n");
        return WIFI_ERROR_INVALID_ARGS;
    }
    if (band_mask == 0) {
        printMsg("Band mask should be bigger than 0\n");
        return WIFI_ERROR_INVALID_ARGS;
    }

    channels = (wifi_usable_channel *)malloc(sizeof(wifi_usable_channel) * max_size);
    if (!channels) {
        printMsg("Failed to alloc channels\n");
        return WIFI_ERROR_OUT_OF_MEMORY;
    }

    memset(channels, 0, sizeof(wifi_usable_channel) * max_size);

    printMsg("Call wifi_get_usable_channels\n");
    ret = hal_fn.wifi_get_usable_channels(halHandle, band_mask, iface_mode_mask, filter_mask, max_size, &size, channels);
    if (ret == WIFI_SUCCESS) {
        printMsg("Usable channel success: size:%d max_size:%d\n", size, max_size);

        for (unsigned int i=0; i < size; i++) {
            printMsg("[%d] freq=%d, width=%d(%s), iface=%d ",
                    i+1, channels[i].freq, channels[i].width,
                    RBchanWidthToString(channels[i].width), channels[i].iface_mode_mask);
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_STA)) {
                printMsg("STA ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_SOFTAP)) {
                printMsg("SOFTAP ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_IBSS)) {
                printMsg("IBSS ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_P2P_CLIENT)) {
                printMsg("P2P_CLI ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_P2P_GO)) {
                printMsg("P2P_GO ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_NAN)) {
                printMsg("NAN ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_MESH)) {
                printMsg("MESH ");
            }
            if (channels[i].iface_mode_mask & (1 << WIFI_INTERFACE_TDLS)) {
                printMsg("TDLS ");
            }
            printMsg("\n");
        }
    } else {
        printMsg("Failed to get usable channels\n");
    }

    free(channels);

    return ret;
}

void testTxPowerLimitOptions(int argc, char *argv[])
{
    void printUsage();          // declaration for below printUsage()
    wifi_error res;

    if (argc < 2) {
        goto usage;
    }

    if ((strcmp(argv[1], "-enable") == 0)) {
        res = hal_fn.wifi_enable_tx_power_limits(wlan0Handle, true);
    } else if ((strcmp(argv[1], "-disable") == 0)) {
        res = hal_fn.wifi_enable_tx_power_limits(wlan0Handle, false);
    } else {
        goto usage;
    }

    if (res == WIFI_SUCCESS) {
        printMsg("Success to execute tx power limit command\n");
    } else {
        printMsg("Failed to execute tx power limit command, res = %d\n", res);
    }
    return;

usage:
    printUsage();
    return;
}

void printUsage() {
    printf("Usage:  halutil [OPTION]\n");
    printf(" -v               print version\n");
    printf(" -s               start AP scan test\n");
    printf(" -swc             start Significant Wifi change test\n");
    printf(" -h               start Hotlist APs scan test\n");
    printf(" -ss              stop scan test\n");
    printf(" -max_ap          Max AP for scan \n");
    printf(" -base_period     Base period for scan \n");
    printf(" -threshold       Threshold scan test\n");
    printf(" -avg_RSSI        samples for averaging RSSI\n");
    printf(" -ap_loss         samples to confirm AP loss\n");
    printf(" -ap_breach       APs breaching threshold\n");
    printf(" -ch_threshold    Change in threshold\n");
    printf(" -wt_event        Waiting event for test\n");
    printf(" -low_th          Low threshold for hotlist APs\n");
    printf(" -hight_th        High threshold for hotlist APs\n");
    printf(" -hotlist_bssids  BSSIDs for hotlist test\n");
    printf(" -stats       print link layer statistics\n");
    printf(" -get_ch_list <a/bg/abg/a_nodfs/abg_nodfs/dfs/"
            "6g/a6g_nodfs/all_nodfs/all>  Get channel list\n");
    printf(" -get_feature_set  Get Feature set\n");
    printf(" -get_feature_matrix  Get concurrent feature matrix\n");
    printf(" -get_wake_stats  print wake reason statistics\n");
    printf(" -rtt [-get_ch_list <a/bg/abg/a_nodfs/abg_nodfs/dfs/"
            "6g/a6g_nodfs/all_nodfs/all>] [-i <burst_period of 100ms unit> [0 - 31] ]"
            "    [-n <exponents of 2 = (num_bursts)> [0 - 15]]\n"
            "    [-f <num_frames_per_burst>] [-r <num_retries_per_ftm>]\n"
            "    [-m <num_retries_per_ftmr>] [-b <burst_duration [2-11 or 15]>]"
            "    [-max_ap <count of allowed max AP>] [-l <file to read>] [-o <file to be stored>]\n");
    printf(" -cancel_rtt      cancel current RTT process\n");
    printf(" -enable_resp     enables the responder\n");
    printf(" -cancel_resp     cancel the responder\n");
    printf(" -get_responder_info    return the responder info\n");
    printf(" -rtt -sta/-nan <peer mac addr> <channel> [ <bandwidth> [0 - 2]] <is_6g>"
        "  bandwidth - 0 for 20, 1 for 40 , 2 for 80 . is_6g = 1 if channel is 6G\n");
    printf(" -get_capa_rtt Get the capability of RTT such as 11mc");
    printf(" -scan_mac_oui XY:AB:CD\n");
    printf(" -nodfs <0|1>     Turn OFF/ON non-DFS locales\n");
    printf(" -country <alpha2 country code> Set country\n");
    printf(" -ePNO Configure ePNO SSIDs\n");
    printf(" -blacklist_bssids blacklist bssids\n");
    printf(" -whitelist_ssids set whitelist ssids\n"
           " -whitelist_ssids ssid1 ssid2 ...\n");
    printf(" -get_roaming_capabilities get roaming capabilities\n");
    printf(" -set_fw_roaming_state set FW roaming state\n");
    printf(" -logger [-start] [-d <debug_level> -f <flags> -i <max_interval_sec>\n"
           "                   -s <min_data_size> -n <ring_name>]\n"
           "                  [pktmonitor]\n"
           "         [-get]   [fw] [driver] [feature] [memdump -o <filename>]\n"
           "                  [ringstatus] [ringdata -n <ring_name>]\n"
           "                  [txfate -n <no of pkts> -f <filename>]\n"
           "                  [rxfate -n <no of pkts> -f <filename>]\n"
           "         [-set]   [loghandler] [alerthandler]\n");
    printf(" -rssi_monitor enable/disable\n");
    printf(" -max_rssi max rssi threshold for RSSI monitor\n");
    printf(" -min_rssi min rssi threshold for RSSI monitor\n");
    printf(" -mkeep_alive [-start] <index (1 to 3)> <period_msec> <src_mac> <dst_mac> <ether_type>"
                                   "[IP packet contents by Hexa format string]\n");
    printf("              [-stop]  <index (1 to 3)>\n");
    printf("    e.g., -mkeep_alive -start 1 20000 000f66f45b7e 0014a54b164f 0800 4500001e0000400040"
           "11c52a0a8830700a88302513c413c4000a00000a0d\n");
    printf(" -nd_offload <0|1>   enable/disable ND offload feature\n"
           "                     enable also triggers IPv6 address update\n");
    printf(" -latency_mode set latency mode WIFI_LATENCY_MODE_NORMAL/WIFI_LATENCY_MODE_LOW on particular interface\n"
           " -latency_mode <interface_name> <low/normal/ultra-low mode>\n");
    printf(" -sar enable <wifi_power_scenario -1 to 99>\n");
    printf(" -sar disable\n");
    printf(" -thermal <mode (none/light/moderate/severe/critical/emergency)>\n");
    printf(" -nan [-enable] [-master_pref <master pref (2 to 254)>]\n"
            "                [-clus_id <cluster ID (50:6F:9A:01:XX:XX)]>\n"
            "                [-cluster_low <0 to 0xffff> -cluster_high <0 to 0xffff>\n"
            "                [-nan_addr <nan interface mac address(XX:XX:XX:XX:XX:XX)]>\n"
            "                [-dual_band <0/1>]\n"
            "                [-24g_chan <2.4GHz channel in MHz >]\n"
            "                [-5g_chan <5GHz channel in MHz >]\n"
            "                [-hc_limit <hop count limit>]\n"
            "                [-warmup_time <warm up time>]  [-disc_ind_cfg <0 to 7>]\n"
            "                [-random_factor <random factor value>]\n"
            "                [-rssi_close_2dot4g_val  <value>] [-rssi_middle_2dot4g_val <value>]\n"
            "                [-rssi_proximity_2dot4g_val <value>] [-support_2dot4g_val <0/1>]\n"
            "                [-beacon_2dot4g_val <0/1>] [-sdf_2dot4g_val <0/1>]\n"
            "                [-beacon_5g_val <0/1>] [-sdf_5g_val <0/1>] [-rssi_close_5g_val <RSSI value>]\n"
            "                [-rssi_middle_5g_val <RSSI value>] [-rssi_close_proximity_5g_val  <RSSI value>]\n"
            "                [-rssi_window_size_val <value>] [-config_cluster_attribute_val <0/1>]\n"
            "                [-dwell_time <value in ms>] [-scan_period <value>] [-sid_flag <0/1>] [-sid_count <1 to 127>]\n"
            "                [-sub_sid_flag <0/1>] [-sub_sid_count <1 to 127>]\n"
            "                [-dwell_time_5g <value in ms>] [-scan_period_5g <value>]\n"
            "                [-awake_dw_2g <value> -awake_dw_5g <value>]\n"
            "                [-instant_mode <0-disable, 1-enable>]\n"
            "                [-instant_chan <2.4/5GHz channel in MHz >]\n");
    printf(" -nan [-disable]\n");
    printf(" -nan [-config] [-sid_flag <0/1>]\n"
            "                [-sid_count <1 to 127>]\n"
            "                [-sub_sid_flag <0/1>] [-sub_sid_count <1 to 127>]\n"
            "                [-rssi_proximity <rssi_proximity value>]\n"
            "                [-numchans <No of channels> -entry_control <Availability interval duration>]\n"
            "                [-channel <Channel value in MHz> -avail_interval_bitmap <u32 value>]\n"
            "                [-master_pref <master_pref>]\n"
            "                [-rssi_close_prox_5g <rssi_close_prox_5g value>]\n"
            "                [-rssi_window_size_val <rssi_window_size_val>]\n"
            "                [-dwell_time <dwell_time value in ms>\n"
            "                [-scan_period <scan_period  value>]\n"
            "                [-dwell_time_5g <value in ms>] [-scan_period_5g <value>]\n"
            "                [-random_factor <random_factor value>]\n"
            "                [-hc_limit <hc_limit>]  [-disc_ind_cfg <0 to 7>]\n"
            "                [-awake_dw_2g <value> -awake_dw_5g <value>]\n"
            "                [-instant_mode <0-disable, 1-enable>]\n"
            "                [-instant_chan <2.4/5GHz channel in MHz >]\n");
    printf(" -nan [-publish] [-svc <svc_name>] [-info <svc info>\n"
            "                [-pub_type <0/1/2>] [-pub_count <val>] [-rssi_thresh_flag <0/1>]\n"
            "                [-tx_type <0/1>] [-ttl <val>] [-svc_awake_dw <val>] [-match_ind <0/1/2>]\n"
            "                [-match_rx <0/ascii str>] [-match_tx <0/ascii str>]  [-passphrase <Passphrase value, len must not be less than 8 or greater than 63>]\n"
            "                [-recv_flag <0 to 15>] [-csid <cipher suite type 0/1/2/4/8>] [-key_type <1 or 2>] [-pmk <PMK value>]\n"
            "                [-scid <scid value>] [-dp_type <0-Unicast, 1-multicast>]\n"
            "                [-secure_dp <0-No security, 1-Security>] [-ranging <0-disable, 1-enable>)]\n"
            "                [-ranging_intvl [intrvl in ms betw two ranging measurements] -ranging_ind \n"
            "                [BIT0 - Continuous Ranging event notification, BIT1 - Ingress distance is <=, BIT2 - Egress distance is >=.]\n"
            "                [-ingress [Ingress distance in centimeters] \n"
            "                [ -egress [Egress distance in centimeters] \n"
            "                [-auto_dp_accept [0 - User response required to accept dp, 1 - User response not required to accept dp] \n");
    printf(" -nan [-subscribe] [-svc <svc_name>] [-info <svc info>\n"
            "                [-sub_type <0/1>] [-sub_count <val>] [-pub_ssi <0/1>]\n"
            "                [-ttl <val>] [-svc_awake_dw <val>] [-match_ind <0/1/2>]\n"
            "                [-match_rx <0/ascii str>] [-match_tx <0/ascii str>]\n"
            "                [-mac_list <addr>] [-srf_use <0/1>] [-rssi_thresh_flag <0/1>]]\n"
            "                [-srf_include <0/1>] [-srf_type <0/1>] [-recv_flag <0 to 7>]\n"
            "                [-csid <cipher suite type 0/1/2/4/8>]  [-key_type <1 or 2>]\n"
            "                [-scid <scid value>] [-dp_type <0-Unicast,1-multicast>]\n"
            "                [-secure_dp <0-No security, 1-Security>] [-ranging <0-disable, 1-enable>)]\n"
            "                [-ranging_intvl [intrvl in ms betw two ranging measurements] -ranging_ind \n"
            "                [BIT0 - Continuous Ranging event notification, BIT1 - Ingress distance is <=, BIT2 - Egress distance is >=.]\n"
            "                [-ingress [Ingress distance in centimeters] \n"
            "                [ -egress [Egress distance in centimeters] \n");
    printf(" -nan [-cancel_pub <publish id>]\n");
    printf(" -nan [-cancel_sub <subscribe id>\n");
    printf(" -nan [-transmit] [-src_id <instance id>] [-dest_id <instance id>]\n"
            "                [-peer_addr <mac addr>] [-info <svc info>] \n"
            "                [-recv_flag <1-Disable followUp response, 0-Enable followup response from FW>]\n");
    printf(" -nan [-get_capabilities]\n");
    printf("\n ****** Nan Data Path Commands ***** \n");
    printf(" -nan [-create] [-iface <iface name>]\n");
    printf(" -nan [-delete] [-iface <iface name>]\n");
    printf(" -nan [-init] [-pub_id <pub id>] [-disc_mac <discovery mac addr>]\n"
            "                [-chan_req_type <NAN DP channel config options>]\n"
            "                [-chan <channel in mhz>] [-iface <iface>] [-sec <security>]  [-key_type <1 or 2>\n"
            "                [-qos <qos>] [-info <seq of values in the frame body>]  [-passphrase <Passphrase value, len must not be less than 8 or greater than 63>]\n"
            "                [-csid <cipher suite type 0/1/2/4/8>] [-key_type <1 or 2>]  [-pmk <PMK value>] [-svc <svc_name>]\n");
    printf(" -nan [-resp] [-ndp_id <NDP id>] [-iface <NDP iface name>]\n"
            "                [-resp_code <accept = 0, reject = 1>] [-qos <qos>]  [-key_type <1 or 2>] \n"
            "                [-info <seq of values in the frame body>]  [-passphrase <Passphrase value, len must not be less than 8 or greater than 63>]\n"
            "                [-csid <cipher suite type 0/1/2/4/8>] [-key_type <1 or 2>] [-pmk <PMK value>] [-svc <svc_name>]\n");
    printf(" -nan [-end] [-inst_count <count>] [-inst_id <NDP id>\n");
    printf(" -nan [-up] [-iface <iface name>] [-ip <ip>]\n");
    printf(" -nan [-addr]\n");
    printf(" -nan [-event_chk]\n");
    printf(" -nan [-ver]\n");
    printf(" -nan [-exit]\n");
    printf(" -dscp [-set] [-s <start>] [-e <end>] [-ac <access_category>]\n");
    printf(" -dscp [-reset] \n");
    printf(" -ch_avoid [-unsafe <band type, a,b>,<channel number>,<power capacity, maximum output for the channel in dBm>]\n"
            "               -unsafe can be used multiple times\n"
            "               b for BAND_24GHZ\n"
            "               a for BAND_5GHZ\n"
            "          [-m <mandatory flag as decimal>]\n"
            "               1 << 0 for FLAG_UNSPECIFIED \n"
            "               1 << 1 for FLAG_WIFI_DIRECT\n"
            "               1 << 2 for FLAG_SOFTAP\n"
            "               1 << 4 for FLAG_WIFI_AWARE\n");
    printf(" -usable_ch [-b <band_mask>], [-i <iface_mask>], [-f <filter_mask>], [-m <max_size of channel>]\n"
            "           [-b 2g,5g,6g]\n"
            "           [-i sta,nan,softap,p2p_go,p2p_cli]\n");
    printf("-ifadd -name <virtual iface name to be created>"
           " -type <0 for STA, 1 for AP, 2 for P2P, 3 for NAN>\n");
    printf("-ifdel -name <virtual iface name to be deleted>\n");
    printf(" -voip_mode <interface_name> <0|1>    voip mode off/on on particular interface\n");
    printf(" -dtim_multiplier <dtim count>  Set suspend bcn_li_dtim.\n");
    printf(" -on_ssr  cmd to trigger sub system restart.\n");
    printf(" -getSupportedRadioMatrix  cmd to get the supported radio combo matrix.\n");
    printf(" -get_cached_scan_results cmd to get the cached scan results.\n");
    printf(" -set_channel_mask <value> [1 for indoor channel, 2 for DFS channel]\n");
    printTwtUsage();
    printTxPowerUsage();
    printChreNanRttUsage();
}

/* Generic function to copy Iface_name/Ip/App specific Info to the buffer */
static int set_interface_params(char *p_info, char *val_p, int len) {
    void *p;
    p = strncpy(p_info, val_p, len);
    if (p_info[len - 1] == '\n') {
        p_info[len - 1] = '\0';
    }
    if (!p) {
        printMsg("Error\n");
        return WIFI_ERROR_UNKNOWN;
    }
    printMsg("Info/Iface/Ip = \"%s\"\n", (char*)p);
    return WIFI_SUCCESS;
}

void OnNanNotifyResponse(transaction_id id, NanResponseMsg* rsp_data) {
    if(rsp_data) {
    switch(rsp_data->response_type) {
    case NAN_RESPONSE_ENABLED:
        printMsg("Nan Enable Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_RESPONSE_DISABLED:
        printMsg("Nan Disable Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_RESPONSE_CONFIG:
        printMsg("Nan Config Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_RESPONSE_PUBLISH:
        printMsg("Nan Publish Response Received, Publish ID "
            "= %d, status = %d\n",
            rsp_data->body.publish_response.publish_id, rsp_data->status);
        break;
    case NAN_RESPONSE_SUBSCRIBE:
        printMsg("Nan Subscribe Response Received, Subscribe ID "
            "= %d, status = %d\n",
            rsp_data->body.subscribe_response.subscribe_id, rsp_data->status);
        break;
    case NAN_RESPONSE_PUBLISH_CANCEL:
        printMsg("Nan Cancel Publish Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_RESPONSE_SUBSCRIBE_CANCEL:
        printMsg("Nan Cancel Subscribe Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_RESPONSE_TRANSMIT_FOLLOWUP:
        printMsg("Transmit followup response received, status = %d\n", rsp_data->status);
        break;
    case NAN_DP_INTERFACE_CREATE:
        printMsg("ndp iface create response received, status = %d\n", rsp_data->status);
        break;
    case NAN_DP_INTERFACE_DELETE:
        printMsg("ndp iface delete response received, status = %d\n", rsp_data->status);
        break;
    case NAN_DP_INITIATOR_RESPONSE:
        printMsg("ndp response received, ndp_instance_id = %d, status = %d\n",
            rsp_data->body.data_request_response.ndp_instance_id, rsp_data->status);
        break;
    case NAN_DP_RESPONDER_RESPONSE:
        printMsg("Nan Data Path Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_DP_END:
        printMsg("Nan Data Path End Response Received, status = %d\n", rsp_data->status);
        break;
    case NAN_GET_CAPABILITIES:
        printMsg("Nan Get Capabilities Response Received, status = %d\n", rsp_data->status);
        printMsg("max_concurrent_nan_clusters = %d\n",
            rsp_data->body.nan_capabilities.max_concurrent_nan_clusters);
        printMsg("max_publishes = %d\n",
            rsp_data->body.nan_capabilities.max_publishes);
        printMsg("max_subscribes = %d\n",
            rsp_data->body.nan_capabilities.max_subscribes);
        printMsg("max_service_name_len = %d\n",
            rsp_data->body.nan_capabilities.max_service_name_len);
        printMsg("max_match_filter_len = %d\n",
            rsp_data->body.nan_capabilities.max_match_filter_len);
        printMsg("max_total_match_filter_len = %d\n",
            rsp_data->body.nan_capabilities.max_total_match_filter_len);
        printMsg("max_service_specific_info_len = %d\n",
            rsp_data->body.nan_capabilities.max_service_specific_info_len);
        printMsg("max_ndi_interfaces = %d\n",
            rsp_data->body.nan_capabilities.max_ndi_interfaces);
        printMsg("max_ndp_sessions = %d\n",
            rsp_data->body.nan_capabilities.max_ndp_sessions);
        printMsg("max_app_info_len = %d\n",
            rsp_data->body.nan_capabilities.max_app_info_len);
        printMsg("max_queued_transmit_followup_msgs = %d\n",
            rsp_data->body.nan_capabilities.max_queued_transmit_followup_msgs);
        printMsg("max_Subscribe_Interface_Addresses = %d\n",
            rsp_data->body.nan_capabilities.max_subscribe_address);
        printMsg("supported_CipherSuites = %d\n",
            rsp_data->body.nan_capabilities.cipher_suites_supported);
        printMsg("max_Extended_ServiceSpecific_Info_Len = %d\n",
            rsp_data->body.nan_capabilities.max_sdea_service_specific_info_len);
        printMsg("ndpe_attr_supported = %d\n",
            rsp_data->body.nan_capabilities.ndpe_attr_supported);
        break;
    default:
        printMsg("Unknown Response Received, %d\n",
            rsp_data->response_type);
        }
    }
    return;
}
void OnNanEventPublishTerminated(NanPublishTerminatedInd* event) {
    char msg_buf[MAX_NAN_MSG_BUF_SIZE] = {'\0'};
    printMsg("\n NanPublishTerminated\n");
    snprintf(msg_buf, MAX_NAN_MSG_BUF_SIZE,
        "NanPublishTerminated: id %u, reason %u\n",
        event->publish_id, event->reason);
    printMsg("Publish Terminated: nan_reason = %s\n", event->nan_reason);
    putEventInCache(EVENT_TYPE_NAN_PUBLISH_TERMINATED, msg_buf);
}
void OnNanEventMatch (NanMatchInd* event) {
    printMsg("\n Subscriber id = %d\n", event->publish_subscribe_id);
    printMsg("Publisher Id = %d\n", event->requestor_instance_id);
    printMsg("Subscribe Match found: Publisher Device Addr( " MACSTR " )\n",
        MAC2STR(event->addr));
    if (event->service_specific_info_len) {
        printMsg("svc info len = %d and svc info = %s\n",
            event->service_specific_info_len,
            event->service_specific_info);
    }
    if(event->sdf_match_filter_len) {
        printMsg("sdf match filter len = %d and sdf_match_filter = %s\n",
            event->sdf_match_filter_len,
            event->sdf_match_filter);
    }
    printMsg("Match occurred flag = %d\n", event->match_occured_flag);
    printMsg("Out of resource flag = %d\n", event->out_of_resource_flag);
    printMsg("rssi value = %d\n", event->rssi_value);
    printMsg("Peer cipher suite type = %d\n", event->peer_cipher_type);
    if (event->scid_len) {
        printMsg("scid len = %d and scid = %s\n",
            event->scid_len, event->scid);
    }
    /* Peer sdea params */
    printMsg("Peer config for data path needed %d\n", event->peer_sdea_params.config_nan_data_path);
    printMsg("Data Path type %d\n", event->peer_sdea_params.ndp_type);
    printMsg("Security configuration %d\n", event->peer_sdea_params.security_cfg);
    printMsg("Ranging report state %d\n", event->peer_sdea_params.range_report);

    printMsg("Distance to the device: %d mm\n", event->range_info.range_measurement_mm);
    printMsg("Ranging event type: %d\n", event->range_info.ranging_event_type);

    if (event->sdea_service_specific_info_len) {
        printMsg("sdea svc info len = %d and sdea svc info = %s\n",
            event->sdea_service_specific_info_len,
            event->sdea_service_specific_info);
    }
    /* Event enabled is not available in android-m */
    putEventInCache(EVENT_TYPE_SUBSCRIBE_MATCHED,
        "SubscribeMatched");
}
void OnNanEventMatchExpired (NanMatchExpiredInd* event) {
    printMsg("NanMatchExpired between publish_subscribe_id = %u "
        "and peer_instance_id = %u\n",
        event->publish_subscribe_id, event->requestor_instance_id);
}
void OnNanEventSubscribeTerminated (NanSubscribeTerminatedInd* event) {
    char msg_buf[MAX_NAN_MSG_BUF_SIZE] = {'\0'};
    printMsg("\n NanSubscribeTerminated\n");
    snprintf(msg_buf, MAX_NAN_MSG_BUF_SIZE,
        "NanSubscribeTerminated: id %u, reason %u\n",
        event->subscribe_id, event->reason);
    printMsg("Subscribe Terminated: nan_reason = %s\n", event->nan_reason);
    putEventInCache(EVENT_TYPE_NAN_SUBSCRIBE_TERMINATED, msg_buf);
}
void OnNanEventFollowup (NanFollowupInd* event) {
    printMsg("\n Instance id= %u\n",
        event->publish_subscribe_id);
    printMsg("Peer instance id = %u\n",
        event->requestor_instance_id);
    printMsg("Transmit Followup Received in %s\n",
        (event->dw_or_faw)? "FAW":"DW");
    printMsg("peer addr (" MACSTR ")\n", MAC2STR(event->addr));
    if (event->service_specific_info_len) {
        printMsg("followup svc_info len = %d and info = %s\n",
            event->service_specific_info_len,event->service_specific_info);
    }
    if (event->sdea_service_specific_info_len) {
        printMsg("sdea svc info len = %d and sdea svc info = %s\n",
            event->sdea_service_specific_info_len,
            event->sdea_service_specific_info);
    }

    putEventInCache(EVENT_TYPE_NAN_FOLLOWUP_RECIEVE,
        "NanFollowupReceived");
}
void OnNanTransmitFollowupInd (NanTransmitFollowupInd* event) {
    printMsg("\n Transaction id = %u\n",
        event->id);
    printMsg("Transmit Followup Status = %u\n",
        event->reason);
    printMsg("Nan Transmit Followup Ind: nan_reason = %s\n", event->nan_reason);
    putEventInCache(EVENT_TYPE_NAN_TRANSMIT_FOLLOWUP_INDICATION,
        "NanTransmitFollowupInd");
}

void OnNanPublishRepliedInd (NanPublishRepliedInd* event) {
    printMsg("\n Requestor Instance Id/Subscriber Id = %d\n", event->requestor_instance_id);
    printMsg("Subscriber found: Device( " MACSTR " )\n",
        MAC2STR(event->addr));
    printMsg("rssi value = %d\n", event->rssi_value);
}

void OnNanEventDiscEngEvent (NanDiscEngEventInd* event) {
    if (event->event_type == NAN_EVENT_ID_DISC_MAC_ADDR) {
        printMsg("\n DE Event Received, Nan Disc Mac Addr Creation Event\n");
        printMsg("NMI Mac address (" MACSTR ")\n",
            MAC2STR(event->data.mac_addr.addr));
    } else if (event->event_type == NAN_EVENT_ID_STARTED_CLUSTER) {
        printMsg("DE Event Received, Nan Cluster Started \n");
    } else if (event->event_type == NAN_EVENT_ID_JOINED_CLUSTER) {
        printMsg("DE Event Received, Nan Cluster Joined \n");
        printMsg("Joined cluster ID (" MACSTR ")\n",
            MAC2STR(event->data.cluster.addr));
    } else {
        printMsg("Unknown DE Event Received, %d\n", event->event_type);
        return;
    }
}
void OnNanEventDisabled (NanDisabledInd* event) {
    printMsg("\n Nan disabledInd received, reason = %u\n", event->reason);
    printMsg("Nan Disabled Event: nan_reason = %s\n", event->nan_reason);
}
void OnNanEventTca (NanTCAInd* event) {}
void OnNanEventBeaconSdfPayload (NanBeaconSdfPayloadInd* event) {}
void OnNanEventDataIndication (NanDataPathRequestInd* event) {
    printMsg("\n service id = %d\n", event->service_instance_id);
    printMsg("Discovery MAC addr of the peer/initiator(" MACSTR ")\n",
        MAC2STR(event->peer_disc_mac_addr));
    printMsg("ndp id = %d\n", event->ndp_instance_id);
    printMsg("qos = %d\n", event->ndp_cfg.qos_cfg);
    printMsg("security = %d\n", event->ndp_cfg.security_cfg);
    if (event->app_info.ndp_app_info_len) {
        printMsg("service info = %s and its length = %d\n",
            event->app_info.ndp_app_info,
            event->app_info.ndp_app_info_len);
    }
    putEventInCache(EVENT_TYPE_NAN_DATA_REQUEST_INDICATION,
        "NanDataEventIndication");
}
void OnNanEventDataConfirmation (NanDataPathConfirmInd* event) {
    printMsg("\n ndp id = %d\n", event->ndp_instance_id);
    printMsg("NDI mac address of the peer = (" MACSTR ")\n",
        MAC2STR(event->peer_ndi_mac_addr));
    if (event->app_info.ndp_app_info_len) {
        printMsg("service info = %s and length = %d\n",
            event->app_info.ndp_app_info,
            event->app_info.ndp_app_info_len);
    }
    printMsg("Response code = %d\n", event->rsp_code);
    switch (event->rsp_code) {
    case NAN_DP_REQUEST_ACCEPT:
        printMsg("Response code [%d]:NAN_DP_REQUEST_ACCEPT\n", event->rsp_code);
        break;
    case NAN_DP_REQUEST_REJECT:
        printMsg("Response code [%d]:NAN_DP_REQUEST_REJECT\n", event->rsp_code);
        printMsg("Reason code for rejection= %d\n", event->reason_code);
        break;
    default:
        printMsg("Unknown response code = %d\n", event->rsp_code);
        break;
    }
    putEventInCache(EVENT_TYPE_NAN_DATA_CONFIRMATION,
        "NanDataConfirmation");
}
void OnNanEventDataPathEnd (NanDataPathEndInd* event) {
    printMsg("\n ndp id count = %d\n", event->num_ndp_instances);
    printMsg("ndp id = %d\n",
        event->ndp_instance_id[event->num_ndp_instances -1]);
    putEventInCache(EVENT_TYPE_NAN_DATA_END_INDICAION,
        "NanDataEndIndication");
}

void OnNanRangeRequestInd (NanRangeRequestInd *event) {
    printMsg("\n Received NanRangeRequestInd\n");
}
void OnNanRangeReportInd (NanRangeReportInd *event) {
    printMsg("\n Received NanRangeReportInd\n");
}
void OnNanDataPathScheduleUpdateInd (NanDataPathScheduleUpdateInd *event) {
    printMsg("\n Received NanDataPathScheduleUpdateInd\n");
}

wifi_error nan_init_handlers(void) {
    wifi_error ret = WIFI_SUCCESS;
    NanCallbackHandler handlers;
    memset(&handlers, 0, sizeof(handlers));
    handlers.NotifyResponse = OnNanNotifyResponse;
    handlers.EventPublishTerminated = OnNanEventPublishTerminated;
    handlers.EventMatch = OnNanEventMatch;
    handlers.EventMatchExpired = OnNanEventMatchExpired;
    handlers.EventSubscribeTerminated = OnNanEventSubscribeTerminated;
    handlers.EventFollowup = OnNanEventFollowup;
    handlers.EventDiscEngEvent = OnNanEventDiscEngEvent;
    handlers.EventDisabled = OnNanEventDisabled;
    handlers.EventTca = OnNanEventTca;
    handlers.EventBeaconSdfPayload = OnNanEventBeaconSdfPayload;
    handlers.EventDataRequest = OnNanEventDataIndication;
    handlers.EventDataConfirm = OnNanEventDataConfirmation;
    handlers.EventDataEnd = OnNanEventDataPathEnd;
    handlers.EventTransmitFollowup = OnNanTransmitFollowupInd;
    handlers.EventPublishReplied = OnNanPublishRepliedInd;
    handlers.EventRangeRequest = OnNanRangeRequestInd;
    handlers.EventRangeReport = OnNanRangeReportInd;
    handlers.EventScheduleUpdate = OnNanDataPathScheduleUpdateInd;
    ret = nan_register_handler(wlan0Handle , handlers);
    printMsg("%s: ret = %d\n", __FUNCTION__, ret);
    return ret;
}

static void OnTwtNotify(TwtDeviceNotify* event) {
    if (event) {
        printMsg("OnTwtNotify, notification = %d\n", event->notification);
    }
    return;
}

static void OnTwtSetupResponse(TwtSetupResponse* event) {
    printMsg("\n OnTwtSetupResponse\n");
    if (event) {
        printMsg("config id = %d\n", event->config_id);
        printMsg("status = %d\n", event->status);
        printMsg("reason_code = %d\n", event->reason_code);
        printMsg("negotiation_type = %d\n", event->negotiation_type);
        printMsg("trigger_type = %d\n", event->trigger_type);
        printMsg("wake_dur_us = %d\n", event->wake_dur_us);
        printMsg("wake_int_us = %d\n", event->wake_int_us);
        printMsg("wake_time_off_us = %d\n", event->wake_time_off_us);
    }
    return;
}

static void OnTwtTearDownCompletion(TwtTeardownCompletion* event) {
    printMsg("\n OnTwtTearDownCompletion\n");
    if (event) {
        printMsg("config id = %d\n", event->config_id);
        printMsg("status = %d\n", event->status);
        printMsg("all twt = %d\n", event->all_twt);
        printMsg("reason = %d\n", event->reason);
    }
    return;
}

static void OnTwtInfoFrameReceived(TwtInfoFrameReceived* event) {
    printMsg("\n OnTwtInfoFrameReceived\n");
    if (event) {
        printMsg("config id = %d\n", event->config_id);
        printMsg("status = %d\n", event->status);
        printMsg("all twt = %d\n", event->all_twt);
        printMsg("reason = %d\n", event->reason);
        printMsg("twt_resumed = %d\n", event->twt_resumed);
    }
    return;
}

wifi_error twt_init_handlers(void) {
    wifi_error ret = WIFI_SUCCESS;
    TwtCallbackHandler handlers;
    memset(&handlers, 0, sizeof(handlers));
    handlers.EventTwtDeviceNotify = OnTwtNotify;
    handlers.EventTwtSetupResponse = OnTwtSetupResponse;
    handlers.EventTwtTeardownCompletion = OnTwtTearDownCompletion;
    handlers.EventTwtInfoFrameReceived = OnTwtInfoFrameReceived;
    ret = twt_register_handler(wlan0Handle , handlers);
    printMsg("%s: ret = %d\n", __FUNCTION__, ret);
    return ret;
}

/*
 * Debug function to see the events reaching to HAL
 * CTRL-C to exit the blocking command.
 */
void twtEventCheck(void) {
    wifi_error ret = WIFI_SUCCESS;

    ret = twt_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize twt handlers %d\n", ret);
        return;
    }

    twtCmdId = getNewCmdId();
    ret = twt_event_check_request(twtCmdId, wlan0Handle);
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to check the twt events: %d\n", ret);
        return;
    }

    printMsg("CTRL-C to exit the thread.\n");
    while (1)
    {
        EventInfo info;
        memset(&info, 0, sizeof(info));
        getEventFromCache(info);
        printMsg("\n Retrieved event %d : %s\n\n", info.type, info.buf);
    }
}
/*
 * Debug function to see the events reaching to HAL
 * CTRL-C to exit the blocking command.
 */
void nanEventCheck(void) {
    wifi_error ret = WIFI_SUCCESS;
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        return;
    }
    nanCmdId = getNewCmdId();
    nan_event_check_request(nanCmdId, wlan0Handle);
    printMsg("CTRL-C to exit the thread.\n");
    while (1)
    {
        EventInfo info;
        memset(&info, 0, sizeof(info));
        getEventFromCache(info);
        printMsg("\n Retrieved event %d : %s\n\n", info.type, info.buf);
    }
}

void nanVersion(void) {
    NanVersion version = 0;
    wifi_error err = WIFI_SUCCESS;
    err = nan_get_version(0, &version);
    if (err == WIFI_SUCCESS) {
        printMsg("NAN VERSION is %d\n", version);
    } else {
        printMsg("Nan Version Command Failed, err = %d\n", err);
    }
}

void set_cluster_id(char *clus_id, NanEnableRequest *msg) {
    u8 addr[NAN_MAC_ADDR_LEN]; /* cluster id */
    if (!clus_id || (!ether_atoe(clus_id, addr))) {
        printMsg("bad cluster id !!\n");
    } else {
        msg->cluster_high = ((addr[4] << 8) | addr[5]);
        msg->cluster_low = msg->cluster_high;
        printMsg("cluster low: %x, cluster high: %x\n", msg->cluster_low, msg->cluster_high);
    }
    return;
}

int
get_oui_bytes(u8 *oui_str, u8 *oui)
{
    int idx;
    u8 val;
    u8 *src, *dest;
    char hexstr[3];
    char* endptr = NULL;

    src = oui_str;
    dest = oui;

    for (idx = 0; idx < 3; idx++) {
        hexstr[0] = src[0];
        hexstr[1] = src[1];
        hexstr[2] = '\0';
        val = (u8) strtoul(hexstr, &endptr, 16);
        *dest++ = val;
        src += 2;
        if (((idx < (3 - 1)) && (*src++ != ':')) || (*endptr != '\0'))
            return WIFI_ERROR_UNKNOWN;
    }
    return WIFI_SUCCESS;
}

void nanSetOui(char *nan_oui, char* nan_type, NanEnableRequest *msg) {
    u8 type;
    u32 value;
    char* endptr;

    if(!nan_type) {
        printMsg("nan oui type is missing. Setting NAN OUI to default \n");
        return;
    }

    if (get_oui_bytes((u8 *)nan_oui, (u8 *)&value)) {
        printMsg("%s: Wrong OUI value. Setting Default OUI and type\n",__FUNCTION__);
        msg->oui_val = 0;
        return;
    }

    type = strtoul(nan_type, &endptr, 0);
    if (*endptr != '\0') {
        printMsg("%s:Wrong nan OUI type. Setting default OUI and type\n", __FUNCTION__);
        msg->oui_val = 0;
        return;
    }
    value = (value & 0xffffff) + (type << 24);
    msg->config_oui = 1;
    msg->oui_val = value;
}

void enableNan(char *argv[]) {
    NanEnableRequest msg ;
    wifi_error ret = WIFI_SUCCESS;
    char *endptr, *param, *val_p;
    int sid_flag = 0xff, sid_count = 0xff;
    int sub_sid_flag = 0xff, sub_sid_count = 0xff;
    u8 val;
    u16 clust_range;

    /* Set Default enable params */
    memset(&msg, 0, sizeof(msg));
    msg.hop_count_limit_val = 5;
    msg.config_2dot4g_support = FEATURE_SUPPORTED;
    msg.support_2dot4g_val = FEATURE_SUPPORTED;
    msg.config_2dot4g_beacons = FEATURE_SUPPORTED;
    msg.beacon_2dot4g_val = FEATURE_SUPPORTED;
    msg.config_2dot4g_sdf = FEATURE_SUPPORTED;
    msg.sdf_2dot4g_val = FEATURE_SUPPORTED;
    msg.config_disc_mac_addr_randomization = true;
    msg.disc_mac_addr_rand_interval_sec = 0;
    msg.config_ndpe_attr = false;
    msg.cluster_low = 0;
    msg.cluster_high = NAN_MAX_CLUST_VALUE_RANGE;

    /* Parse args for nan params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-clus_id") == 0) {
            set_cluster_id(val_p, &msg);
        } else if (strcmp(param, "-cluster_low") == 0) {
            clust_range = atoi(val_p);
            if (clust_range < 0 || clust_range > NAN_MAX_CLUST_VALUE_RANGE) {
                msg.cluster_low = 0;
            }
            msg.cluster_low = clust_range;
        } else if (strcmp(param, "-cluster_high") == 0) {
             clust_range = atoi(val_p);
             if (clust_range < 0 || clust_range > NAN_MAX_CLUST_VALUE_RANGE) {
                msg.cluster_high = NAN_MAX_CLUST_VALUE_RANGE;
             }
             msg.cluster_high = clust_range;
        } else if (strcmp(param, "-master_pref") == 0) {
            msg.master_pref = strtoul(val_p, &endptr, 0);
            if (*endptr != '\0' || (msg.master_pref < 2 || msg.master_pref > 254)) {
                printMsg("%s:Invalid Master Preference.Setting it to Random\n", __FUNCTION__);
                msg.master_pref = 0;
            }
        } else if (strcmp(param, "-dual_band") == 0) {
            msg.support_5g_val = strtoul(val_p, &endptr, 0);
            if (*endptr != '\0' ||  msg.support_5g_val > 1) {
                printMsg("%s:Invalid Dual Band Value.\n", __FUNCTION__);
                msg.config_support_5g = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.config_support_5g = true;
            }
        } else if (strcmp(param, "-hc_limit") == 0) {
            msg.hop_count_limit_val = strtoul(val_p, &endptr, 0);
            if (*endptr != '\0') {
                printMsg("%s:Invalid Hop Count Limit. Setting to Default\n", __FUNCTION__);
                msg.config_hop_count_limit = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.config_hop_count_limit = true;
            }
        } else if (strcmp(param, "-oui") == 0) {
            nanSetOui(val_p, *argv++, &msg);
        } else if (strcmp(param, "-sid_flag") == 0) {
            sid_flag = atoi(val_p);
            if (sid_flag) {
                msg.config_sid_beacon = true;
            } else {
                printMsg("%s:Invalid Service Id Flag. Setting to Default\n", __FUNCTION__);
                msg.config_sid_beacon = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sid_count") == 0) {
            sid_count = atoi(val_p);
            if (sid_count < 0 || sid_count > NAN_MAX_SIDS_IN_BEACONS) {
                printMsg("%s:Invalid  Service ID Count Limit. Setting to Default\n", __FUNCTION__);
                sid_count = 0;
            } else  {
                msg.sid_beacon_val = ((sid_count << 1) | sid_flag);
            }
        } else if (strcmp(param, "-sub_sid_flag") == 0) {
            sub_sid_flag = atoi(val_p);
            if (sub_sid_flag) {
                msg.config_subscribe_sid_beacon = true;
            } else {
                printMsg("%s:Invalid Subscribe Service Id Flag. Setting to Default\n", __FUNCTION__);
                msg.config_subscribe_sid_beacon = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sub_sid_count") == 0) {
            sub_sid_count = atoi(val_p);
            if (sub_sid_count < 0 || sub_sid_count > NAN_MAX_SIDS_IN_BEACONS) {
                printMsg("%s:Invalid Subscribe Service ID Count Limit. Setting to Default\n", __FUNCTION__);
                sub_sid_count = 0;
            } else  {
                msg.subscribe_sid_beacon_val = ((sub_sid_count << 1) | sub_sid_flag);
            }
        } else if (strcmp(param, "-rssi_close_2dot4g_val") == 0) {
            msg.rssi_close_2dot4g_val = atoi(val_p);
            if (msg.rssi_close_2dot4g_val) {
                msg.config_2dot4g_rssi_close = true;
            }
        } else if (strcmp(param, "-rssi_middle_2dot4g_val") == 0) {
            msg.rssi_middle_2dot4g_val = atoi(val_p);
            if (msg.rssi_middle_2dot4g_val) {
                msg.config_2dot4g_rssi_middle = true;
            }
        } else if (strcmp(param, "-rssi_proximity_2dot4g_val") == 0) {
            msg.rssi_proximity_2dot4g_val = atoi(val_p);
            if (msg.rssi_proximity_2dot4g_val) {
                msg.config_2dot4g_rssi_proximity = true;
            }
        } else if (strcmp(param, "-support_2dot4g_val") == 0) {
            val = atoi(val_p);
            /*
             * Defines 2.4G channel access support
             * 0 - No Support
             * 1 - Supported
             */
            switch(val) {
                case FEATURE_NOT_SUPPORTED:
                    msg.support_2dot4g_val = FEATURE_NOT_SUPPORTED;
                    break;
                default:
                    msg.support_2dot4g_val = FEATURE_SUPPORTED;
                    break;
            }
         } else if (strcmp(param, "-beacon_2dot4g_val") == 0) {
            val = atoi(val_p);
           /*
            * Defines 2.4G channels will be used for sync/discovery beacons
            * 0 - 2.4G channels not used for beacons
            * 1 - 2.4G channels used for beacons
            */
            switch(val) {
                case FEATURE_NOT_SUPPORTED:
                    msg.beacon_2dot4g_val = FEATURE_NOT_SUPPORTED;
                    break;
                default:
                    msg.beacon_2dot4g_val = FEATURE_SUPPORTED;
                    break;
            }
        } else if (strcmp(param, "-sdf_2dot4g_val") == 0) {
            val = atoi(val_p);
            /*
             * Defines 2.4G channels will be used for Service Discovery frames
             * 0 - 2.4G channels not used for Service Discovery frames
             * 1 - 2.4G channels used for Service Discovery frames
             */
            switch(val) {
                case FEATURE_NOT_SUPPORTED:
                    msg.sdf_2dot4g_val = FEATURE_NOT_SUPPORTED;
                    break;
                default:
                    msg.sdf_2dot4g_val = FEATURE_SUPPORTED;
                    break;
            }
        } else if (strcmp(param, "-beacon_5g_val") == 0) {
            val = atoi(val_p);
            /*
             * Defines 5G channels will be used for sync/discovery beacons
             * 0 - 5G channels not used for beacons
             * 1 - 5G channels used for beacons
             */
            switch(val) {
                case FEATURE_SUPPORTED:
                    msg.beacon_5g_val = FEATURE_SUPPORTED;
                    break;
                default:
                    msg.beacon_5g_val = FEATURE_NOT_SUPPORTED;
                    break;
            }
            msg.config_5g_beacons = true;
        } else if (strcmp(param, "-sdf_5g_val") == 0) {
            val = atoi(val_p);
            /*
             * Defines 5G channels will be used for Service Discovery frames
             * 0 - 5G channels not used for Service Discovery frames
             * 1 - 5G channels used for Service Discovery frames
             */
            switch(val) {
                case FEATURE_SUPPORTED:
                    msg.sdf_5g_val = FEATURE_SUPPORTED;
                    break;
                default:
                    msg.sdf_5g_val = FEATURE_NOT_SUPPORTED;
                    break;
            }
            msg.config_5g_sdf = true;
        } else if (strcmp(param, "-rssi_close_5g_val") == 0) {
            msg.rssi_close_5g_val = atoi(val_p);
            if (msg.rssi_close_5g_val) {
                msg.config_5g_rssi_close = true;
            }
        } else if (strcmp(param, "-rssi_middle_5g_val") == 0) {
            msg.rssi_middle_5g_val = atoi(val_p);
            if (msg.rssi_middle_5g_val) {
                msg.config_5g_rssi_middle = true;
            }
        } else if (strcmp(param, "-rssi_close_proximity_5g_val") == 0) {
            msg.rssi_close_proximity_5g_val = atoi(val_p);
            if (msg.rssi_close_proximity_5g_val) {
                msg.config_5g_rssi_close_proximity = true;
            }
        } else if (strcmp(param, "-rssi_window_size_val") == 0) {
            msg.rssi_window_size_val = atoi(val_p);
            if (msg.rssi_window_size_val) {
                msg.config_rssi_window_size = true;
            } else {
                printMsg("%s:Invalid rssi_window_size_val\n", __FUNCTION__);
                msg.config_rssi_window_size = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-config_cluster_attribute_val") == 0) {
            val = atoi(val_p);
            /*
             * If set to 1, the Discovery Engine will enclose the Cluster
             * Attribute only sent in Beacons in a Vendor Specific Attribute
             * and transmit in a Service Descriptor Frame.
             */

            switch(val) {
                case FEATURE_SUPPORTED:
                    msg.config_cluster_attribute_val = FEATURE_SUPPORTED;
                    break;
                default:
                    msg.config_cluster_attribute_val = FEATURE_NOT_SUPPORTED;
                    break;
            }
        } else if (strcmp(param, "-dwell_time") == 0) {
            msg.scan_params_val.dwell_time[0] = atoi(val_p);
            if (msg.scan_params_val.dwell_time[0]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        }  else if (strcmp(param, "-scan_period") == 0) {
                msg.scan_params_val.scan_period[0] = atoi(val_p);
            if (msg.scan_params_val.scan_period[0]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-dwell_time_5g") == 0) {
            msg.scan_params_val.dwell_time[1] = atoi(val_p);
            if (msg.scan_params_val.dwell_time[1]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params for 5g\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        }  else if (strcmp(param, "-scan_period_5g") == 0) {
                msg.scan_params_val.scan_period[1] = atoi(val_p);
            if (msg.scan_params_val.scan_period[1]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params for 5g\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-random_factor") == 0) {
            msg.random_factor_force_val = atoi(val_p);
            if (msg.random_factor_force_val) {
                msg.config_random_factor_force = true;
            } else {
                printMsg("%s:Invalid random factor\n", __FUNCTION__);
                msg.config_random_factor_force = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-24g_chan") == 0) {
            msg.channel_24g_val = atoi(val_p);
            if (msg.channel_24g_val) {
                msg.config_24g_channel = true;
            } else {
                printMsg("%s:Invalid 2.4GHz channel value\n", __FUNCTION__);
                msg.config_24g_channel = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-5g_chan") == 0) {
            msg.channel_5g_val = atoi(val_p);
            if (msg.channel_5g_val) {
                msg.config_5g_channel = true;
            } else {
                printMsg("%s:Invalid 5GHz channel value\n", __FUNCTION__);
                msg.config_5g_channel = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-nan_addr") == 0) {
            if (!ether_atoe(val_p, msg.intf_addr_val)) {
                printMsg("bad nan interface mac addr, setting to random mac by fw!\n");
                msg.config_intf_addr = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
            msg.config_intf_addr = true;
        } else if (strcmp(param, "-awake_dw_2g") == 0) {
            msg.config_dw.dw_2dot4g_interval_val = atoi(val_p);
            if (msg.config_dw.dw_2dot4g_interval_val) {
                msg.config_dw.config_2dot4g_dw_band = true;
            }
        } else if (strcmp(param, "-awake_dw_5g") == 0) {
            msg.config_dw.dw_5g_interval_val = atoi(val_p);
            if (msg.config_dw.dw_5g_interval_val) {
                msg.config_dw.config_5g_dw_band = true;
            }
        } else if (strncmp(param, "-disc_ind_cfg", strlen("-disc_ind_cfg")) == 0) {
            msg.discovery_indication_cfg = strtoul(val_p, &endptr, 0);
            printMsg("%s:disc_ind_cfg value = %d.\n",
                __FUNCTION__, msg.discovery_indication_cfg);
        } else if (strncmp(param, "-rand_mac", strlen("-rand_mac")) == 0) {
            msg.config_disc_mac_addr_randomization = true;
            msg.disc_mac_addr_rand_interval_sec = atoi(val_p);
        } else if (strcmp(param, "-use_ndpe") == 0) {
            msg.use_ndpe_attr = atoi(val_p);
            msg.config_ndpe_attr = true;
            if ((msg.use_ndpe_attr != 1) && (msg.use_ndpe_attr != 0)) {
                msg.config_ndpe_attr = false;
                printMsg("%s:Invalid use_ndpe_attr value\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-disc_bcn_interval") == 0) {
                msg.discovery_beacon_interval = atoi(val_p);
                msg.config_discovery_beacon_int = true;
        } else if (strcmp(param, "-enable_ranging") == 0) {
            msg.enable_ranging = atoi(val_p);
            if (msg.enable_ranging) {
                msg.config_enable_ranging = true;
            }
        } else if (strcmp(param, "-nss") == 0) {
            msg.nss = atoi(val_p);
            if (msg.nss) {
                msg.config_nss = true;
            }
        } else if (strcmp(param, "-enable_dw_term") == 0) {
            msg.enable_dw_termination = atoi(val_p);
            if (msg.enable_dw_termination) {
                msg.config_dw_early_termination = true;
            }
#ifdef NAN_3_1_SUPPORT
        } else if (strcmp(param, "-instant_mode") == 0) {
            msg.enable_instant_mode = atoi(val_p);
            msg.config_enable_instant_mode = true;
        } else if (strcmp(param, "-instant_chan") == 0) {
            msg.instant_mode_channel = atoi(val_p);
            if (msg.instant_mode_channel) {
                msg.config_instant_mode_channel = true;
            } else {
                printMsg("%s:Invalid Instant channel \n", __FUNCTION__);
                msg.config_instant_mode_channel = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
#endif /* NAN_3_1_SUPPORT */
        } else {
            printMsg("%s:Unsupported Parameter for Nan Enable\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_enable_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void disableNan(void) {
    wifi_error ret = WIFI_SUCCESS;
    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        return;
    }
    ret = nan_disable_request(nanCmdId, wlan0Handle);
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
}

void configNan(char *argv[]) {
    NanConfigRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *endptr, *param, *val_p;
    int sid_flag = 0xff, sid_count = 0xff;
    int sub_sid_flag = 0xff, sub_sid_count = 0xff;
    u8 val, numchans = 0;

    memset(&msg, 0, sizeof(msg));
    msg.fam_val.famchan[numchans].entry_control = NAN_DURATION_16MS;
    msg.config_ndpe_attr = false;

    /* Parse args for nan params */
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p) {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }

        if (strcmp(param, "-sid_flag") == 0) {
            sid_flag = atoi(val_p);
            if (sid_flag) {
                msg.config_sid_beacon = true;
            } else {
                printMsg("%s:Invalid Service Id Flag. Setting to Default\n", __FUNCTION__);
                msg.config_sid_beacon = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sid_count") == 0) {
            sid_count = atoi(val_p);
            if (sid_count < 0 || sid_count > NAN_MAX_SIDS_IN_BEACONS) {
                printMsg("%s:Invalid  Service ID Count Limit. Setting to Default\n", __FUNCTION__);
                sid_count = 0;
            } else  {
                msg.sid_beacon = ((sid_count << 1) | sid_flag);
            }
        } else if (strcmp(param, "-sub_sid_flag") == 0) {
            sub_sid_flag = atoi(val_p);
            if (sub_sid_flag) {
                msg.config_subscribe_sid_beacon = true;
            } else {
                printMsg("%s:Invalid Subscribe Service Id Flag. Setting to Default\n", __FUNCTION__);
                msg.config_subscribe_sid_beacon = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sub_sid_count") == 0) {
            sub_sid_count = atoi(val_p);
            if (sub_sid_count < 0 || sub_sid_count > NAN_MAX_SIDS_IN_BEACONS) {
                printMsg("%s:Invalid Subscribe Service ID Count Limit. Setting to Default\n", __FUNCTION__);
                sub_sid_count = 0;
            } else  {
                msg.subscribe_sid_beacon_val = ((sub_sid_count << 1) | sub_sid_flag);
            }
        } else if (strcmp(param, "-rssi_proximity") == 0) {
            msg.rssi_proximity = atoi(val_p);
            if (msg.rssi_proximity) {
                msg.config_rssi_proximity = true;
            } else {
                printMsg("%s:Invalid rssi proximity\n", __FUNCTION__);
                msg.config_rssi_proximity = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-master_pref") == 0) {
            msg.master_pref = atoi(val_p);
            if (msg.master_pref) {
                msg.config_master_pref = true;
            } else {
                printMsg("%s:Invalid master_pref\n", __FUNCTION__);
                msg.config_master_pref = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-rssi_close_prox_5g") == 0) {
            msg.rssi_close_proximity_5g_val = atoi(val_p);
            if (msg.rssi_close_proximity_5g_val) {
                msg.config_5g_rssi_close_proximity = true;
            } else {
                printMsg("%s:Invalid 5g rssi close proximity \n", __FUNCTION__);
                msg.config_5g_rssi_close_proximity = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-rssi_window_size_val") == 0) {
            msg.rssi_window_size_val = atoi(val_p);
            if (msg.rssi_window_size_val) {
                msg.config_rssi_window_size = true;
            } else {
                printMsg("%s:Invalid rssi window size val \n", __FUNCTION__);
                msg.config_rssi_window_size = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-config_cluster_attribute_val") == 0) {
            val = atoi(val_p);
            /*
             * If set to 1, the Discovery Engine will enclose the Cluster
             * Attribute only sent in Beacons in a Vendor Specific Attribute
             * and transmit in a Service Descriptor Frame.
             */

            switch(val) {
                case FEATURE_SUPPORTED:
                    msg.config_cluster_attribute_val = FEATURE_SUPPORTED;
                    break;
                default:
                    msg.config_cluster_attribute_val = FEATURE_NOT_SUPPORTED;
                    break;
            }
        } else if (strcmp(param, "-dwell_time") == 0) {
            msg.scan_params_val.dwell_time[0] = atoi(val_p);
            if (msg.scan_params_val.dwell_time[0]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        }  else if (strcmp(param, "-scan_period") == 0) {
            msg.scan_params_val.scan_period[0] = atoi(val_p);
            if (msg.scan_params_val.scan_period[0]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-dwell_time_5g") == 0) {
            msg.scan_params_val.dwell_time[1] = atoi(val_p);
            if (msg.scan_params_val.dwell_time[1]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params for 5g\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        }  else if (strcmp(param, "-scan_period_5g") == 0) {
                msg.scan_params_val.scan_period[1] = atoi(val_p);
            if (msg.scan_params_val.scan_period[1]) {
                msg.config_scan_params = true;
            } else {
                msg.config_scan_params = false;
                printMsg("%s:Invalid config_scan_params for 5g\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-random_factor") == 0) {
            msg.random_factor_force_val = atoi(val_p);
            if (msg.random_factor_force_val) {
                msg.config_random_factor_force = true;
            } else {
                printMsg("%s:Invalid random factor\n", __FUNCTION__);
                msg.config_random_factor_force = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-hc_limit") == 0) {
            msg.hop_count_force_val = atoi(val_p);
            if (msg.hop_count_force_val) {
                msg.config_hop_count_force = true;
            } else {
                printMsg("%s:Invalid hop_count_force_val. Setting to Default\n", __FUNCTION__);
                msg.config_hop_count_force = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-numchans") == 0) {
            numchans = atoi(val_p);
            if (numchans) {
                msg.config_fam = true;
                msg.fam_val.numchans = numchans;
            } else {
                printMsg("%s:Invalid num chan\n", __FUNCTION__);
                msg.config_fam = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-entry_control") == 0) {
            val = atoi(val_p);
            msg.config_fam = true;
            switch(val) {
            case NAN_DURATION_16MS:
                msg.fam_val.famchan[numchans].entry_control = NAN_DURATION_16MS;
                break;
            case NAN_DURATION_32MS:
                msg.fam_val.famchan[numchans].entry_control = NAN_DURATION_32MS;
                break;
            case NAN_DURATION_64MS:
                msg.fam_val.famchan[numchans].entry_control = NAN_DURATION_64MS;
                break;
            default:
                printMsg("%s: Invalid entry_control\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                msg.config_fam = false;
                break;
            }
        } else if (strcmp(param, "-class_val") == 0) {
            msg.fam_val.famchan[numchans].class_val = atoi(val_p);
            if (msg.fam_val.famchan[numchans].class_val) {
                msg.config_fam = true;
            } else {
                printMsg("%s:Invalid fam val\n", __FUNCTION__);
                msg.config_fam = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-channel") == 0) {
            msg.fam_val.famchan[numchans].channel = atoi(val_p);
            if (msg.fam_val.famchan[numchans].channel) {
                msg.config_fam = true;
            } else {
                printMsg("%s:Invalid fam val\n", __FUNCTION__);
                msg.config_fam = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-mapid") == 0) {
            msg.fam_val.famchan[numchans].mapid = atoi(val_p);
            if (msg.fam_val.famchan[numchans].mapid) {
                msg.config_fam = true;
            } else {
                printMsg("%s:Invalid fam val\n", __FUNCTION__);
                msg.config_fam = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-avail_interval_bitmap") == 0) {
            msg.fam_val.famchan[numchans].avail_interval_bitmap = atoi(val_p);
            printMsg("avail_interval_bitmap = %d\n", msg.fam_val.famchan[numchans].avail_interval_bitmap);
            if (msg.fam_val.famchan[numchans].avail_interval_bitmap) {
                msg.config_fam = true;
            } else {
                printMsg("%s:Invalid fam val\n", __FUNCTION__);
                msg.config_fam = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-awake_dw_2g") == 0) {
            msg.config_dw.dw_2dot4g_interval_val = atoi(val_p);
            if (msg.config_dw.dw_2dot4g_interval_val) {
                msg.config_dw.config_2dot4g_dw_band = true;
            }
        } else if (strcmp(param, "-awake_dw_5g") == 0) {
            msg.config_dw.dw_5g_interval_val = atoi(val_p);
            if (msg.config_dw.dw_5g_interval_val) {
                msg.config_dw.config_5g_dw_band = true;
            }
        } else if (strncmp(param, "-disc_ind_cfg", strlen("-disc_ind_cfg")) == 0) {
                msg.discovery_indication_cfg = strtoul(val_p, &endptr, 0);
                printMsg("%s:disc_ind_cfg value = %d.\n",
                    __FUNCTION__, msg.discovery_indication_cfg);
        } else if (strcmp(param, "-use_ndpe") == 0) {
            msg.use_ndpe_attr = atoi(val_p);
            msg.config_ndpe_attr = true;
            if ((msg.use_ndpe_attr != 1) && (msg.use_ndpe_attr != 0)) {
                msg.config_ndpe_attr = false;
                printMsg("%s:Invalid use_ndpe_attr value\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strncmp(param, "-rand_mac", strlen("-rand_mac")) == 0) {
            msg.config_disc_mac_addr_randomization = true;
            msg.disc_mac_addr_rand_interval_sec = atoi(val_p);
        } else if (strcmp(param, "-disc_bcn_interval") == 0) {
                msg.discovery_beacon_interval = atoi(val_p);
                msg.config_discovery_beacon_int = true;
        } else if (strcmp(param, "-enable_ranging") == 0) {
            msg.enable_ranging = atoi(val_p);
            if (msg.enable_ranging) {
                msg.config_enable_ranging = true;
            }
        } else if (strcmp(param, "-nss") == 0) {
            msg.nss = atoi(val_p);
            if (msg.nss) {
                msg.config_nss = true;
            }
        } else if (strcmp(param, "-enable_dw_term") == 0) {
            msg.enable_dw_termination = atoi(val_p);
            if (msg.enable_dw_termination) {
                msg.config_dw_early_termination = true;
            }
#ifdef NAN_3_1_SUPPORT
        } else if (strcmp(param, "-instant_mode") == 0) {
            msg.enable_instant_mode = atoi(val_p);
            msg.config_enable_instant_mode = true;
        } else if (strcmp(param, "-instant_chan") == 0) {
            msg.instant_mode_channel = atoi(val_p);
            if (msg.instant_mode_channel) {
                msg.config_instant_mode_channel = true;
            } else {
                printMsg("%s:Invalid Instant channel \n", __FUNCTION__);
                msg.config_instant_mode_channel = false;
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
#endif /* NAN_3_1_SUPPORT */
        } else {
            printMsg("%s:Unsupported Parameter for Nan Config\n", __FUNCTION__);
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_config_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void publishNan(int argc, char *argv[]) {
    NanPublishRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL, *endptr = NULL;
    u32 val = 0;
    u8 *match_rxtmp = NULL, *match_txtmp = NULL;

    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    memset(&msg, 0, sizeof(msg));
    msg.publish_id = 0;
    msg.publish_type = NAN_PUBLISH_TYPE_UNSOLICITED_SOLICITED;
    msg.publish_match_indicator = NAN_MATCH_ALG_MATCH_CONTINUOUS;
    msg.tx_type = NAN_TX_TYPE_UNICAST;
    msg.sdea_params.ndp_type = NAN_DATA_PATH_UNICAST_MSG;
    msg.service_responder_policy = NAN_SERVICE_ACCEPT_POLICY_NONE;
    msg.period = 1;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-svc") == 0) {
            if (strlen((const char *)val_p) > NAN_MAX_SERVICE_NAME_LEN) {
                printMsg("Invalid  service name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_name_len =
                strlen((const char *)val_p);
                if (!set_interface_params((char*)msg.service_name,
                    val_p, msg.service_name_len)) {
                    printMsg("Set service name successfull\n");
                }
            }
        } else if (strcmp(param, "-info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid  service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_specific_info_len =
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.service_specific_info,
                    val_p, msg.service_specific_info_len)) {
                    printMsg("Set service specific info successfull\n");
                }
            }
        } else if (strcmp(param, "-pub_count") == 0) {
            msg.publish_count = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-pub_id") == 0) {
            msg.publish_id = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-pub_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_PUBLISH_TYPE_UNSOLICITED:
                    msg.publish_type = NAN_PUBLISH_TYPE_UNSOLICITED;
                    break;
                case NAN_PUBLISH_TYPE_SOLICITED:
                    msg.publish_type = NAN_PUBLISH_TYPE_SOLICITED;
                    break;
                default:
                    msg.publish_type = NAN_PUBLISH_TYPE_UNSOLICITED_SOLICITED;
                    break;
            }
        } else if (strcmp(param, "-tx_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_TX_TYPE_BROADCAST:
                    msg.tx_type = NAN_TX_TYPE_BROADCAST;
                    break;
                default:
                    msg.tx_type = NAN_TX_TYPE_UNICAST;
                    break;
            }
        } else if (strcmp(param, "-ttl") == 0) {
            msg.ttl = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-svc_awake_dw") == 0) {
            msg.period = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-match_tx") == 0) {
            u8 m_len = strlen(val_p);
            if (!match_txtmp) {
                match_txtmp = msg.tx_match_filter;
            }
            if (strcmp(val_p, "0") == 0) {
                printMsg("wild card\n");
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.tx_match_filter_len)) {
                    *match_txtmp++ = 0;
                    msg.tx_match_filter_len++;
                }
            } else {
            if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.tx_match_filter_len)) {
                *match_txtmp++ = strlen(val_p);
                msg.tx_match_filter_len++;
                strncpy((char *)match_txtmp, val_p, strlen(val_p));
                match_txtmp += m_len;
                msg.tx_match_filter_len += m_len;
            } else {
                printMsg("Invalid match filter len\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
                }
            }
        } else if (strcmp(param, "-match_rx") == 0) {
            u8 m_len = strlen(val_p);
            if (!match_rxtmp) {
                match_rxtmp = msg.rx_match_filter;
            }
            if (strcmp(val_p, "0") == 0) {
                printMsg("wild card\n");
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.rx_match_filter_len)) {
                    *match_rxtmp++ = 0;
                    msg.rx_match_filter_len++;
                }
            } else {
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.rx_match_filter_len)) {
                    *match_rxtmp++ = strlen(val_p);
                    msg.rx_match_filter_len++;
                    strncpy((char *)match_rxtmp, val_p, strlen(val_p));
                    match_rxtmp += m_len;
                    msg.rx_match_filter_len += m_len;
                } else {
                    printMsg("Invalid match filter len\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
        } else if (strncmp(param, "-recv_flag", strlen("-recv_flag")) == 0) {
            msg.recv_indication_cfg = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-match_ind") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_MATCH_ALG_MATCH_ONCE:
                    msg.publish_match_indicator = NAN_MATCH_ALG_MATCH_ONCE;
                    break;
                case NAN_MATCH_ALG_MATCH_NEVER:
                    msg.publish_match_indicator = NAN_MATCH_ALG_MATCH_NEVER;
                    break;
                default:
                    msg.publish_match_indicator = NAN_MATCH_ALG_MATCH_CONTINUOUS;
                    break;
            }
        } else if (strcmp(param, "-csid") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_CIPHER_SUITE_SHARED_KEY_NONE:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_256_MASK;
                    break;
#ifdef NAN_3_1_SUPPORT
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK;
                    break;
#endif /* NAN_3_1_SUPPORT */
                default:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
            }
        } else if (strcmp(param, "-key_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SECURITY_KEY_INPUT_PMK:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PMK;
                    break;
                case NAN_SECURITY_KEY_INPUT_PASSPHRASE:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PASSPHRASE;
                    break;
                default:
                    printMsg("Invalid security key type\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            }
        } else if (strcmp(param, "-pmk") == 0) {
            if (strlen((const char*)val_p) > NAN_PMK_INFO_LEN) {
                printMsg("Invalid PMK\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.pmk_info.pmk_len=
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.key_info.body.pmk_info.pmk,
                    val_p, msg.key_info.body.pmk_info.pmk_len)) {
                    printMsg("Set PMK successfull\n");
                }
            }
        } else if (strcmp(param, "-passphrase") == 0) {
            if (strlen((const char*)val_p) < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                strlen((const char*)val_p) > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                printMsg("passphrase must be between %d and %d characters long\n",
                NAN_SECURITY_MIN_PASSPHRASE_LEN,
                NAN_SECURITY_MAX_PASSPHRASE_LEN);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.passphrase_info.passphrase_len =
                (strlen((const char*)val_p));
                if (!set_interface_params((char*)msg.key_info.body.passphrase_info.passphrase,
                    val_p, msg.key_info.body.passphrase_info.passphrase_len)) {
                    printMsg("Set passphrase successfull, len = %d\n", msg.key_info.body.passphrase_info.passphrase_len);
                } else {
                    printMsg("Invalid passphrase\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
        } else if (strcmp(param, "-scid") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SCID_BUF_LEN) {
               printMsg("Invalid SCID\n");
               ret = WIFI_ERROR_INVALID_ARGS;
               goto exit;
            } else {
               msg.scid_len=
               strlen((const char*)val_p);
               if (!set_interface_params((char*)msg.scid,
                  val_p, msg.scid_len)) {
                  printMsg("Set SCID successfull\n");
               }
            }
        } else if (strcmp(param, "-dp_type") == 0) {
            val = atoi(val_p);
            msg.sdea_params.config_nan_data_path = true;
            switch(val) {
                case NAN_DATA_PATH_MULTICAST_MSG:
                    msg.sdea_params.ndp_type = NAN_DATA_PATH_MULTICAST_MSG;
                    break;
                case NAN_DATA_PATH_UNICAST_MSG:
                    msg.sdea_params.ndp_type = NAN_DATA_PATH_UNICAST_MSG;
                    break;
                default:
                    printMsg("Invalid datapath type\n");
                    msg.sdea_params.config_nan_data_path = false;
                    ret = WIFI_ERROR_INVALID_ARGS;
                    break;
            }
        } else if (strcmp(param, "-secure_dp") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_SECURITY:
                    msg.sdea_params.security_cfg = NAN_DP_CONFIG_SECURITY;
                    break;
                default:
                    msg.sdea_params.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
                    break;
            }
        } else if (strcmp(param, "-ranging") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_RANGING_ENABLE:
                    msg.sdea_params.ranging_state = NAN_RANGING_ENABLE;
                    break;
                default:
                    msg.sdea_params.ranging_state = NAN_RANGING_DISABLE;
                    break;
                }
        } else if (strcmp(param, "-ranging_intvl") == 0) {
            msg.ranging_cfg.ranging_interval_msec = atoi(val_p);
        } else if (strcmp(param, "-ranging_ind") == 0) {
            msg.ranging_cfg.config_ranging_indications = atoi(val_p);
        } else if (strcmp(param, "-ingress") == 0) {
            msg.ranging_cfg.distance_ingress_mm = atoi(val_p);
        } else if (strcmp(param, "-egress") == 0) {
            msg.ranging_cfg.distance_egress_mm= atoi(val_p);
        } else if (strcmp(param, "-rssi_thresh_flag") == 0) {
            msg.rssi_threshold_flag = atoi(val_p);
        } else if (strcmp(param, "-sdea_info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid SDEA service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.sdea_service_specific_info_len =
                   strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.sdea_service_specific_info,
                    val_p, msg.sdea_service_specific_info_len)) {
                        printMsg("Set SDEA service specific info successfull\n");
                }
            }
        } else if (strcmp(param, "-auto_dp_accept") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SERVICE_ACCEPT_POLICY_ALL:
                msg.service_responder_policy = NAN_SERVICE_ACCEPT_POLICY_ALL;
                break;
                default:
                msg.service_responder_policy = NAN_SERVICE_ACCEPT_POLICY_NONE;
                break;
            }
        } else {
           printMsg("%s:Unsupported Parameter for Nan Publish\n", __FUNCTION__);
           goto exit;
        }
    }
    if (!msg.service_name_len) {
        printMsg("service name is mandatory !!\n");
        goto exit;
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_publish_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void subscribeNan(int argc, char *argv[]) {
    NanSubscribeRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL, *endptr = NULL;
    u8 num_mac_addr = 0;
    u32 val = 0;
    u8 *match_rxtmp = NULL, *match_txtmp = NULL;

    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    memset(&msg, 0, sizeof(msg));

    /* set mandatory default values */
    msg.subscribe_id = 0;
    msg.subscribe_type = NAN_SUBSCRIBE_TYPE_PASSIVE;
    msg.useServiceResponseFilter = NAN_DO_NOT_USE_SRF;
    /*
     * Set NAN_MATCH_ALG_MATCH_ONCE as default param to avoid
     * flooding of discovery result events
     */
    msg.subscribe_match_indicator = NAN_MATCH_ALG_MATCH_ONCE;
    msg.sdea_params.ndp_type = NAN_DATA_PATH_UNICAST_MSG;
    msg.rx_match_filter_len = 0;
    msg.tx_match_filter_len = 0;
    msg.period = 1;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-svc") == 0) {
            if (strlen((const char *)val_p) > NAN_MAX_SERVICE_NAME_LEN) {
                printMsg("Invalid service name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_name_len =
                strlen((const char *)val_p);
                if (!set_interface_params((char*)msg.service_name,
                    val_p, msg.service_name_len)) {
                        printMsg("Set service name successfull\n");
                }
            }
        } else if (strcmp(param, "-info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid  service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_specific_info_len =
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.service_specific_info,
                    val_p, msg.service_specific_info_len)) {
                    printMsg("Set service specific info successfull\n");
                }
            }
        } else if (strcmp(param, "-sub_count") == 0) {
            msg.subscribe_count = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-pub_ssi") == 0) {
            val = atoi(val_p);
            /*
             * Flag which specifies if the Service Specific Info is needed in
             * the Publish message before creating the MatchIndication
             */
            /* 0= Not needed, 1= Required */
            switch(val) {
                case NAN_SSI_REQUIRED_IN_MATCH_IND:
                    msg.ssiRequiredForMatchIndication = NAN_SSI_REQUIRED_IN_MATCH_IND;
                    break;
                default:
                    msg.ssiRequiredForMatchIndication = NAN_SSI_NOT_REQUIRED_IN_MATCH_IND;
                    break;
            }
        } else if (strcmp(param, "-sub_id") == 0) {
            msg.subscribe_id = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-sub_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SUBSCRIBE_TYPE_ACTIVE:
                    msg.subscribe_type = NAN_SUBSCRIBE_TYPE_ACTIVE;
                    break;
                default:
                    msg.subscribe_type = NAN_SUBSCRIBE_TYPE_PASSIVE;
                    break;
            }
        } else if (strcmp(param, "-ttl") == 0) {
            msg.ttl = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-svc_awake_dw") == 0) {
            msg.period = strtoul(val_p, &endptr, 0);
        } else if (strncmp(param, "-srf_use", strlen("-srf_use")) == 0) {
            val = atoi(val_p);
            /* 0=Do not send the Service Response Filter,1= send */
            switch(val) {
                case NAN_USE_SRF:
                    msg.useServiceResponseFilter = NAN_USE_SRF;
                    break;
                default:
                    msg.useServiceResponseFilter = NAN_DO_NOT_USE_SRF;
                    break;
            }
        } else if (strncmp(param, "-srf_include", strlen("-srf_include")) == 0) {
            val = strtoul(val_p, &endptr, 0);
            /* 0=Do not respond if in the Address Set, 1= Respond */
            switch(val) {
                case NAN_SRF_INCLUDE_RESPOND:
                    msg.serviceResponseInclude = NAN_SRF_INCLUDE_RESPOND;
                    break;
                default:
                    msg.serviceResponseInclude = NAN_SRF_INCLUDE_DO_NOT_RESPOND;
                    break;
            }
        } else if (strncmp(param, "-srf_type", strlen("-srf_type")) == 0) {
            val = atoi(val_p);
            /* 0 - Bloom Filter, 1 - MAC Addr */
            switch(val) {
                case NAN_SRF_ATTR_PARTIAL_MAC_ADDR:
                    msg.serviceResponseFilter = NAN_SRF_ATTR_PARTIAL_MAC_ADDR;
                    break;
                default:
                    msg.serviceResponseFilter = NAN_SRF_ATTR_BLOOM_FILTER;
                    break;
            }
        } else if (strcmp(param, "-mac_list") == 0) {
            if (num_mac_addr < NAN_MAX_SUBSCRIBE_MAX_ADDRESS) {
                if (!ether_atoe(val_p, msg.intf_addr[num_mac_addr])) {
                    printMsg("bad mac addr !!\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
                msg.num_intf_addr_present = ++num_mac_addr;
            } else {
                printMsg("max limit reached, %d!!!\n", num_mac_addr);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
            if (msg.num_intf_addr_present) {
                msg.useServiceResponseFilter = NAN_USE_SRF;
                msg.serviceResponseFilter = NAN_SRF_ATTR_PARTIAL_MAC_ADDR;
            }
        } else if (strcmp(param, "-match_ind") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_MATCH_ALG_MATCH_ONCE:
                    msg.subscribe_match_indicator = NAN_MATCH_ALG_MATCH_ONCE;
                    break;
                case NAN_MATCH_ALG_MATCH_NEVER:
                    msg.subscribe_match_indicator = NAN_MATCH_ALG_MATCH_NEVER;
                    break;
                default:
                    msg.subscribe_match_indicator = NAN_MATCH_ALG_MATCH_CONTINUOUS;
                    break;
            }
        } else if (strcmp(param, "-match_tx") == 0) {
            u8 m_len = strlen(val_p);
            if (!match_txtmp) {
                match_txtmp = msg.tx_match_filter;
            }
            if (strcmp(val_p, "0") == 0) {
                printMsg("wild card\n");
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.tx_match_filter_len)) {
                    *match_txtmp++ = 0;
                    msg.tx_match_filter_len++;
                }
            } else {
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.tx_match_filter_len)) {
                    *match_txtmp++ = strlen(val_p);
                    msg.tx_match_filter_len++;
                    strncpy((char *)match_txtmp, val_p, strlen(val_p));
                    match_txtmp += m_len;
                    msg.tx_match_filter_len += m_len;
                } else {
                    printMsg("Invalid match filter len\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
        } else if (strcmp(param, "-match_rx") == 0) {
            u8 m_len = strlen(val_p);
            if (!match_rxtmp) {
                match_rxtmp = msg.rx_match_filter;
            }
            if (strcmp(val_p, "0") == 0) {
                printMsg("wild card\n");
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.rx_match_filter_len)) {
                    *match_rxtmp++ = 0;
                    msg.rx_match_filter_len++;
                }
            } else {
                if (m_len < (NAN_MAX_MATCH_FILTER_LEN - msg.rx_match_filter_len)) {
                    *match_rxtmp++ = strlen(val_p);
                    msg.rx_match_filter_len++;
                    strncpy((char *)match_rxtmp, val_p, strlen(val_p));
                    match_rxtmp += m_len;
                    msg.rx_match_filter_len += m_len;
                } else {
                    printMsg("Invalid match filter len\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
        } else if (strncmp(param, "-recv_flag", strlen("-recv_flag")) == 0) {
            msg.recv_indication_cfg = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-csid") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_CIPHER_SUITE_SHARED_KEY_NONE:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_256_MASK;
                    break;
#ifdef NAN_3_1_SUPPORT
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK;
                    break;
#endif /* NAN_3_1_SUPPORT */
                default:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
            }
        } else if (strcmp(param, "-key_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SECURITY_KEY_INPUT_PMK:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PMK;
                    break;
                case NAN_SECURITY_KEY_INPUT_PASSPHRASE:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PASSPHRASE;
                    break;
                default:
                    printMsg("Invalid security key type\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            }
        } else if (strcmp(param, "-pmk") == 0) {
            if (strlen((const char*)val_p) > NAN_PMK_INFO_LEN) {
                printMsg("Invalid PMK\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.pmk_info.pmk_len=
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.key_info.body.pmk_info.pmk,
                    val_p, msg.key_info.body.pmk_info.pmk_len)) {
                    printMsg("Set PMK successfull\n");
                }
            }
        } else if (strcmp(param, "-passphrase") == 0) {
            if (strlen((const char*)val_p) < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                strlen((const char*)val_p) > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                    printMsg("passphrase must be between %d and %d characters long\n",
                    NAN_SECURITY_MIN_PASSPHRASE_LEN,
                    NAN_SECURITY_MAX_PASSPHRASE_LEN);
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            } else {
                msg.key_info.body.passphrase_info.passphrase_len =
                (strlen((const char*)val_p));
                if (!set_interface_params((char*)msg.key_info.body.passphrase_info.passphrase,
                    val_p, msg.key_info.body.passphrase_info.passphrase_len)) {
                    printMsg("Set passphrase successfull, len = %d\n", msg.key_info.body.passphrase_info.passphrase_len);
                } else {
                    printMsg("Invalid passphrase\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
               }
            }
        } else if (strcmp(param, "-scid") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SCID_BUF_LEN) {
                printMsg("Invalid SCID\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.scid_len=
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.scid,
                    val_p, msg.scid_len)) {
                    printMsg("Set SCID successfull\n");
                }
            }
        } else if (strcmp(param, "-dp_type") == 0) {
            val = atoi(val_p);
            msg.sdea_params.config_nan_data_path = true;
            switch(val) {
                case NAN_DATA_PATH_MULTICAST_MSG:
                    msg.sdea_params.ndp_type = NAN_DATA_PATH_MULTICAST_MSG;
                    break;
                case NAN_DATA_PATH_UNICAST_MSG:
                    msg.sdea_params.ndp_type = NAN_DATA_PATH_UNICAST_MSG;
                    break;
                default:
                    printMsg("Invalid datapath type\n");
                    msg.sdea_params.config_nan_data_path = false;
                    ret = WIFI_ERROR_INVALID_ARGS;
                    break;
            }
        } else if (strcmp(param, "-secure_dp") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_SECURITY:
                    msg.sdea_params.security_cfg = NAN_DP_CONFIG_SECURITY;
                    break;
                default:
                    msg.sdea_params.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
                    break;
            }
        } else if (strcmp(param, "-ranging") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_RANGING_ENABLE:
                    msg.sdea_params.ranging_state = NAN_RANGING_ENABLE;
                    break;
                default:
                    msg.sdea_params.ranging_state = NAN_RANGING_DISABLE;
                    break;
                }
        } else if (strcmp(param, "-ranging_intvl") == 0) {
            msg.ranging_cfg.ranging_interval_msec = atoi(val_p);
        } else if (strcmp(param, "-ranging_ind") == 0) {
            msg.ranging_cfg.config_ranging_indications = atoi(val_p);
        } else if (strcmp(param, "-ingress") == 0) {
            msg.ranging_cfg.distance_ingress_mm = atoi(val_p);
        } else if (strcmp(param, "-egress") == 0) {
            msg.ranging_cfg.distance_egress_mm= atoi(val_p);
        } else if (strcmp(param, "-rssi_thresh_flag") == 0) {
            msg.rssi_threshold_flag = atoi(val_p);
        } else if (strcmp(param, "-sdea_info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid SDEA service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.sdea_service_specific_info_len = strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.sdea_service_specific_info,
                    val_p, msg.sdea_service_specific_info_len)) {
                    printMsg("Set SDEA service specific info successfull\n");
                }
            }
        } else {
            printMsg("%s:Unsupported Parameter for Nan Subscribe\n", __FUNCTION__);
            goto exit;
        }
    }
    if (!msg.service_name_len) {
        printMsg("service name is mandatory !!\n");
        goto exit;
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_subscribe_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void cancelPublishNan(char *argv[]) {
    NanPublishCancelRequest msg ;
    wifi_error ret = WIFI_SUCCESS;
    u16 pub_id;
    pub_id = atoi(argv[3]);
    if (pub_id) {
        msg.publish_id = pub_id;
    } else {
        printMsg("\nInvalid argument \n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    }
    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_publish_cancel_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}


void cancelSubscribeNan(char *argv[]) {
    NanSubscribeCancelRequest msg ;
    wifi_error ret = WIFI_SUCCESS;
    u16 sub_id;
    sub_id = atoi(argv[3]);
    if (sub_id) {
        msg.subscribe_id = sub_id;
    } else {
        printMsg("\nInvalid argument \n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    }
    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_subscribe_cancel_request(nanCmdId, wlan0Handle, &msg);
#ifdef NAN_BLOCK_FOR_EVENT
    memset(&info, 0, sizeof(info));
    getEventFromCache(info);
    printMsg("retrieved event %d : %s\n", info.type, info.buf);
#endif
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void transmitNan(int argc, char *argv[]) {
    NanTransmitFollowupRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL, *endptr = NULL;
    u16 src_id = 0;
    u32 dest_id = 0;
    u8 *mac_addr = NULL;

    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    memset(&msg, 0, sizeof(msg));
    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-src_id") == 0) {
            msg.publish_subscribe_id = atoi(val_p);
            src_id = msg.publish_subscribe_id;
        } else if (strcmp(param, "-dest_id") == 0) {
            msg.requestor_instance_id = atoi(val_p);
            dest_id = msg.requestor_instance_id;
        } else if (strcmp(param, "-peer_addr") == 0) {
            if (!ether_atoe(val_p, msg.addr)) {
                printMsg("bad peer mac addr !!\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
            mac_addr = msg.addr;
        } else if (strcmp(param, "-info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid  service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_specific_info_len =
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.service_specific_info,
                    val_p, msg.service_specific_info_len)) {
                    printMsg("Set service specific info successfull\n");
                }
            }
        } else if (strncmp(param, "-recv_flag", strlen("-recv_flag")) == 0) {
            msg.recv_indication_cfg = strtoul(val_p, &endptr, 0);
        } else if (strcmp(param, "-sdea_info") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SDEA_SERVICE_SPECIFIC_INFO_LEN) {
                printMsg("Invalid SDEA service specific info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.sdea_service_specific_info_len =
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.sdea_service_specific_info,
                    val_p, msg.sdea_service_specific_info_len)) {
                    printMsg("Set SDEA service specific info successfull\n");
                }
            }
        } else {
            printMsg("%s:Unsupported Parameter for nan transmit followup\n", __FUNCTION__);
            goto exit;
        }
    }

    if (!src_id) {
        printMsg("Source Instance Id is mandatory !!\n");
        goto exit;
    }
    if (!dest_id) {
        printMsg("Destination Instance Id is mandatory !!\n");
        goto exit;
    }
    if (!mac_addr) {
        printMsg("Peer MAC Address is mandatory !!\n");
        goto exit;
    }
    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_transmit_followup_request(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void getNanCapabilities(void) {
    nanCmdId = getNewCmdId();
    wifi_error ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        return;
    }
    nan_get_capabilities(nanCmdId, wlan0Handle);
}

void nanDataPathIfaceCreate(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char ndp_iface[IFNAMSIZ+1];

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-iface") == 0) {
            if (!set_interface_params(ndp_iface, val_p, (IFNAMSIZ - 1))) {
                printMsg("set interface name successfull\n");
            } else {
                printMsg("Invalid  Iface name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else {
            printMsg("Unsupported Parameter for ndp iface create\n");
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_data_interface_create(nanCmdId, wlan0Handle, ndp_iface);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void nanDataPathIfaceDelete(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char ndp_iface[IFNAMSIZ+1];

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-iface") == 0) {
            if (!set_interface_params(ndp_iface, val_p, (IFNAMSIZ - 1))) {
                printMsg("clear interface name successfull\n");
            } else {
                printMsg("Invalid  Iface name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else {
            printMsg("Unsupported Parameter for ndp iface delete\n");
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_data_interface_delete(nanCmdId, wlan0Handle, ndp_iface);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}


void nanDataInitRequest(int argc, char *argv[]) {
    NanDataPathInitiatorRequest msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    u32 val = 0;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    memset(&msg, 0, sizeof(msg));
    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_NO_QOS;

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-pub_id") == 0) {
            msg.requestor_instance_id = atoi(val_p);
        } else if (strcmp(param, "-chan_req_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CHANNEL_NOT_REQUESTED:
                    msg.channel_request_type = NAN_DP_CHANNEL_NOT_REQUESTED ;
                    break;
                case NAN_DP_REQUEST_CHANNEL_SETUP:
                    msg.channel_request_type = NAN_DP_REQUEST_CHANNEL_SETUP;
                    break;
                case NAN_DP_FORCE_CHANNEL_SETUP:
                    msg.channel_request_type = NAN_DP_FORCE_CHANNEL_SETUP;
                    break;
                default:
                    msg.channel_request_type = NAN_DP_CHANNEL_NOT_REQUESTED;
                    break;
            }
        } else if (strcmp(param, "-chan") == 0) {
            msg.channel = atoi(val_p);
        } else if (strcmp(param, "-disc_mac") == 0) {
            if (!ether_atoe(val_p, msg.peer_disc_mac_addr)) {
                printMsg("bad Discovery Mac address !!\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-iface") == 0) {
            if (!set_interface_params(msg.ndp_iface, val_p, (IFNAMSIZ - 1))) {
                printMsg("Set Iface name successfull\n");
            } else {
                printMsg("Invalid  Iface name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sec") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_SECURITY:
                    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_SECURITY;
                    break;
                default:
                    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
                    break;
            }
        } else if (strcmp(param, "-qos") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_QOS:
                    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_QOS;
                    break;
                default:
                    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_NO_QOS;
                    break;
            }
        } else if (strcmp(param, "-info") == 0) {
            if (strlen((const char*)val_p) > NAN_DP_MAX_APP_INFO_LEN) {
                printMsg("Invalid app info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.app_info.ndp_app_info_len =
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.app_info.ndp_app_info,
                    val_p, msg.app_info.ndp_app_info_len)) {
                    printMsg("Set app info successfull\n");
                }
            }
        } else if (strcmp(param, "-csid") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_CIPHER_SUITE_SHARED_KEY_NONE:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_256_MASK;
                    break;
#ifdef NAN_3_1_SUPPORT
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK;
                    break;
#endif /* NAN_3_1_SUPPORT */
                default:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
            }
        } else if (strcmp(param, "-key_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SECURITY_KEY_INPUT_PMK:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PMK;
                    break;
                case NAN_SECURITY_KEY_INPUT_PASSPHRASE:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PASSPHRASE;
                    break;
                default:
                    printMsg("Invalid security key type\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            }
        } else if (strcmp(param, "-pmk") == 0) {
            if (strlen((const char*)val_p) > NAN_PMK_INFO_LEN) {
                printMsg("Invalid PMK\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.pmk_info.pmk_len=
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.key_info.body.pmk_info.pmk,
                val_p, msg.key_info.body.pmk_info.pmk_len)) {
                printMsg("Set PMK successfull\n");
                }
            }
        } else if (strcmp(param, "-passphrase") == 0) {
            if (strlen((const char*)val_p) < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
            strlen((const char*)val_p) > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                printMsg("passphrase must be between %d and %d characters long\n",
                NAN_SECURITY_MIN_PASSPHRASE_LEN,
                NAN_SECURITY_MAX_PASSPHRASE_LEN);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.passphrase_info.passphrase_len =
                (strlen((const char*)val_p));
                if (!set_interface_params((char*)msg.key_info.body.passphrase_info.passphrase,
                    val_p, msg.key_info.body.passphrase_info.passphrase_len)) {
                    printMsg("Set passphrase successfull, len = %d\n", msg.key_info.body.passphrase_info.passphrase_len);
                } else {
                    printMsg("Invalid passphrase\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
#ifdef NAN_3_1_SUPPORT
        } else if (strcmp(param, "-scid") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SCID_BUF_LEN) {
                printMsg("Invalid SCID\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.scid_len=
                strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.scid,
                    val_p, msg.scid_len)) {
                    printMsg("Set SCID successfull\n");
                }
            }
#endif /* NAN_3_1_SUPPORT */
        } else if (strcmp(param, "-svc") == 0) {
            if (strlen((const char *)val_p) > NAN_MAX_SERVICE_NAME_LEN) {
                printMsg("Invalid service name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_name_len =
                strlen((const char *)val_p);
                if (!set_interface_params((char*)msg.service_name,
                    val_p, msg.service_name_len)) {
                    printMsg("Set service name successfull\n");
                }
            }
        } else {
            printMsg("%s:Unsupported Parameter for Nan Data Path Request\n", __FUNCTION__);
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_data_request_initiator(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void nanDataIndResponse(int argc, char *argv[]) {
    NanDataPathIndicationResponse msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    u32 val = 0;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    memset(&msg, 0, sizeof(msg));
    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_NO_QOS;
    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-ndp_id") == 0) {
            msg.ndp_instance_id = atoi(val_p);
        } else if (strcmp(param, "-iface") == 0) {
            if (!set_interface_params(msg.ndp_iface, val_p, (IFNAMSIZ - 1))) {
                printMsg("Set Iface name successfull\n");
            } else {
                printMsg("Invalid  Iface name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-sec") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_SECURITY:
                    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_SECURITY;
                    break;
                default:
                    msg.ndp_cfg.security_cfg = NAN_DP_CONFIG_NO_SECURITY;
                    break;
            }
        } else if (strcmp(param, "-qos") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_CONFIG_QOS:
                    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_QOS;
                    break;
                default:
                    msg.ndp_cfg.qos_cfg = NAN_DP_CONFIG_NO_QOS;
                    break;
            }
        } else if (strcmp(param, "-info") == 0) {
            if ((u16)strlen((const char*)val_p) > NAN_DP_MAX_APP_INFO_LEN) {
                printMsg("Invalid app info\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.app_info.ndp_app_info_len =
                    (u16)strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.app_info.ndp_app_info,
                    val_p, msg.app_info.ndp_app_info_len)) {
                    printMsg("Set app info successfull\n");
                }
            }
        } else if (strcmp(param, "-resp_code") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_DP_REQUEST_REJECT:
                    msg.rsp_code = NAN_DP_REQUEST_REJECT;
                    break;
                case NAN_DP_REQUEST_ACCEPT:
                    msg.rsp_code = NAN_DP_REQUEST_ACCEPT;
                    break;
                default:
                    printMsg("Invalid response code\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            }
        } else if (strcmp(param, "-csid") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_CIPHER_SUITE_SHARED_KEY_NONE:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_SHARED_KEY_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_256_MASK;
                    break;
#ifdef NAN_3_1_SUPPORT
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_128_MASK;
                    break;
                case NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK:
                    msg.cipher_type = NAN_CIPHER_SUITE_PUBLIC_KEY_2WDH_256_MASK;
                    break;
#endif /* NAN_3_1_SUPPORT */
                default:
                    msg.cipher_type = NAN_CIPHER_SUITE_SHARED_KEY_NONE;
                    break;
            }
        } else if (strcmp(param, "-key_type") == 0) {
            val = atoi(val_p);
            switch(val) {
                case NAN_SECURITY_KEY_INPUT_PMK:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PMK;
                    break;
                case NAN_SECURITY_KEY_INPUT_PASSPHRASE:
                    msg.key_info.key_type = NAN_SECURITY_KEY_INPUT_PASSPHRASE;
                    break;
                default:
                    printMsg("Invalid security key type\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
            }
        } else if (strcmp(param, "-pmk") == 0) {
            if (strlen((const char*)val_p) > NAN_PMK_INFO_LEN) {
                printMsg("Invalid PMK\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.pmk_info.pmk_len =
                    strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.key_info.body.pmk_info.pmk,
                    val_p, msg.key_info.body.pmk_info.pmk_len)) {
                    printMsg("Set PMK successfull\n");
                }
            }
        } else if (strcmp(param, "-passphrase") == 0) {
            if (strlen((const char*)val_p) < NAN_SECURITY_MIN_PASSPHRASE_LEN ||
                strlen((const char*)val_p) > NAN_SECURITY_MAX_PASSPHRASE_LEN) {
                printMsg("passphrase must be between %d and %d characters long\n",
                    NAN_SECURITY_MIN_PASSPHRASE_LEN,
                    NAN_SECURITY_MAX_PASSPHRASE_LEN);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.key_info.body.passphrase_info.passphrase_len =
                    (strlen((const char*)val_p));
                if (!set_interface_params((char*)msg.key_info.body.passphrase_info.passphrase,
                    val_p, msg.key_info.body.passphrase_info.passphrase_len)) {
                    printMsg("Set passphrase successfull, len = %d\n", msg.key_info.body.passphrase_info.passphrase_len);
                } else {
                    printMsg("Invalid passphrase\n");
                    ret = WIFI_ERROR_INVALID_ARGS;
                    goto exit;
                }
            }
#ifdef NAN_3_1_SUPPORT
        } else if (strcmp(param, "-scid") == 0) {
            if (strlen((const char*)val_p) > NAN_MAX_SCID_BUF_LEN) {
                printMsg("Invalid SCID\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.scid_len= strlen((const char*)val_p);
                if (!set_interface_params((char*)msg.scid, val_p, msg.scid_len)) {
                    printMsg("Set SCID successfull\n");
                }
            }
#endif /* NAN_3_1_SUPPORT */
        } else if (strcmp(param, "-svc") == 0) {
            if (strlen((const char *)val_p) > NAN_MAX_SERVICE_NAME_LEN) {
                printMsg("Invalid service name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            } else {
                msg.service_name_len =
                strlen((const char *)val_p);
                if (!set_interface_params((char*)msg.service_name,
                    val_p, msg.service_name_len)) {
                    printMsg("Set service name successfull\n");
                }
            }
        } else {
            printMsg("%s:Unsupported Parameter for Nan Data Path Request\n", __FUNCTION__);
            goto exit;
        }
    }
    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_data_indication_response(nanCmdId, wlan0Handle, &msg);
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void nanDataPathEnd(int argc, char *argv[]) {
    NanDataPathEndRequest *msg;
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL, *endptr = NULL;
    u8 count = 0, i = 0;
    NanDataPathId ndp_id = 0;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* skip command */
    argv++;

    msg = (NanDataPathEndRequest *)malloc(NAN_MAX_NDP_COUNT_SIZE + sizeof(u8));
    if (!msg) {
        printMsg("Failed to alloc for end request\n");
        ret = WIFI_ERROR_OUT_OF_MEMORY;
        goto exit;
    }
    memset(msg, 0, NAN_MAX_NDP_COUNT_SIZE + sizeof(u8));

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-inst_count") == 0) {
            count = atoi(val_p);
            if (!count || count > 1) {
                printMsg("%s:Invalid inst_count value.\n", __FUNCTION__);
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
            msg->num_ndp_instances = count;
        } else if (strcmp(param, "-inst_id") == 0) {
            if (!msg->num_ndp_instances || (i > msg->num_ndp_instances)) {
                printMsg("num of ndp instances need to be minimum 1\n");
                goto exit;
            }
            ndp_id = strtoul(val_p, &endptr, 0);
            msg->ndp_instance_id[i++] = ndp_id;
        } else {
            printMsg("%s:Unsupported Parameter for Nan Data Path End Request\n", __FUNCTION__);
            goto exit;
        }
    }

    nanCmdId = getNewCmdId();
    ret = nan_init_handlers();
    if (ret != WIFI_SUCCESS) {
        printMsg("Failed to initialize handlers %d\n", ret);
        goto exit;
    }
    ret = nan_data_end(nanCmdId, wlan0Handle, msg);
exit:
    if (msg) {
        free(msg);
    }
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void VirtualIfaceAdd(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char iface_name[IFNAMSIZ+1];
    wifi_interface_type iface_type;

    while ((param = *argv++) != NULL) {
    val_p = *argv++;
    if (!val_p || *val_p == '-') {
        printMsg("%s: Need value following %s\n", __FUNCTION__, param);
        ret = WIFI_ERROR_NOT_SUPPORTED;
        goto exit;
    }
    if (strcmp(param, "-name") == 0) {
        if (!set_interface_params(iface_name, val_p, (IFNAMSIZ - 1))) {
                printMsg("set interface name successfull\n");
            } else {
                printMsg("Invalid Iface name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else if (strcmp(param, "-type") == 0) {
            iface_type = (wifi_interface_type)atoi(val_p);
        } else {
            printMsg("Unsupported Parameter for virtual iface delete\n");
            goto exit;
        }
    }

    ret = hal_fn.wifi_virtual_interface_create(halHandle, iface_name, iface_type);
    if (ret == WIFI_ERROR_NONE) {
        printMsg("Successful to add virtual iface\n");
    } else {
        printMsg("Failed to add virtual iface, result = %d\n", ret);
    }

exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

void VirtualIfaceDelete(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL, *val_p = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char iface_name[IFNAMSIZ+1];

    while ((param = *argv++) != NULL) {
        val_p = *argv++;
        if (!val_p || *val_p == '-') {
            printMsg("%s: Need value following %s\n", __FUNCTION__, param);
            ret = WIFI_ERROR_NOT_SUPPORTED;
            goto exit;
        }
        if (strcmp(param, "-name") == 0) {
            if (!set_interface_params(iface_name, val_p, (IFNAMSIZ - 1))) {
                printMsg("set interface name successfull\n");
            } else {
                printMsg("Invalid  face name\n");
                ret = WIFI_ERROR_INVALID_ARGS;
                goto exit;
            }
        } else {
            printMsg("Unsupported Parameter for virtual iface delete\n");
            goto exit;
        }
    }

    ret = hal_fn.wifi_virtual_interface_delete(halHandle, iface_name);
    if (ret == WIFI_ERROR_NONE) {
        printMsg("Successful to delete virtual iface\n");
    } else {
        printMsg("Failed to delete virtual iface, result = %d\n", ret);
    }
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void
MultiStaSetPrimaryConnection(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char iface_name[IFNAMSIZ+1];
    wifi_interface_handle ifHandle = NULL;

    while ((param = *argv++) != NULL) {
        if (!set_interface_params(iface_name, param, (IFNAMSIZ - 1))) {
            printMsg("set interface name successfull\n");
        } else {
            printMsg("Invalid  iface name\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ifHandle = wifi_get_iface_handle(halHandle, iface_name);
    if (ifHandle == NULL) {
        printMsg("Invalid  iface handle for the requested interface\n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    } else {
        ret = hal_fn.wifi_multi_sta_set_primary_connection(halHandle, ifHandle);
        if (ret == WIFI_ERROR_NONE) {
            printMsg("Successfully set as primary connection\n");
        }
    }
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void
MultiStaSetUsecase(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    uint use_case;
    wifi_multi_sta_use_case mMultiStaUsecase;

    /* skip utility */
    argv++;
    /* skip command */
    argv++;

    use_case = (uint)atoi(*argv);
    if (use_case >= WIFI_DUAL_STA_TRANSIENT_PREFER_PRIMARY &&
        use_case <= WIFI_DUAL_STA_NON_TRANSIENT_UNBIASED) {
        mMultiStaUsecase = (wifi_multi_sta_use_case)use_case;
    } else {
        printMsg("Invalid  multi_sta usecase\n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    }

    ret = hal_fn.wifi_multi_sta_set_use_case(halHandle, mMultiStaUsecase);
    if (ret == WIFI_ERROR_NONE) {
        printMsg("Successful to set multista usecase\n");
    } else {
        printMsg("Failed to set multista usecase, result = %d\n", ret);
    }
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

static void
SetLatencyMode(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char iface_name[IFNAMSIZ+1];
    wifi_interface_handle ifHandle = NULL;

    param = *argv++;
    if (param != NULL) {
        if (!set_interface_params(iface_name, param, (IFNAMSIZ - 1))) {
           printMsg("set interface name successfull %s\n", iface_name);
        } else {
            printMsg("Invalid  iface name\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ifHandle = wifi_get_iface_handle(halHandle, iface_name);
    if (ifHandle == NULL) {
        printMsg("Invalid  iface handle for the requested interface\n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    } else {
        /* Read the requested latency mode */
        wifi_latency_mode latency_mode = (wifi_latency_mode)(atoi)(*argv);
        ret = hal_fn.wifi_set_latency_mode(ifHandle, latency_mode);
        if (ret == WIFI_ERROR_NONE) {
            printMsg("Successfully set latency mode\n");
        }
    }
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;

}

static void
SetVoipMode(char *argv[]) {
    wifi_error ret = WIFI_SUCCESS;
    char *param = NULL;
    /* skip utility */
    argv++;
    /* skip command */
    argv++;
    /* Interface name */
    char iface_name[IFNAMSIZ+1];
    wifi_interface_handle ifHandle = NULL;

    param = *argv++;
    if (param != NULL) {
        if (!set_interface_params(iface_name, param, (IFNAMSIZ - 1))) {
            printMsg("set interface name successfull\n");
        } else {
            printMsg("Invalid  iface name\n");
            ret = WIFI_ERROR_INVALID_ARGS;
            goto exit;
        }
    }

    ifHandle = wifi_get_iface_handle(halHandle, iface_name);
    if (ifHandle == NULL) {
        printMsg("Invalid  iface handle for the requested interface\n");
        ret = WIFI_ERROR_INVALID_ARGS;
        goto exit;
    } else {
        /* Read the requested voip mode */
        wifi_voip_mode mode = (wifi_voip_mode)(atoi)(*argv);
        ret = hal_fn.wifi_set_voip_mode(ifHandle, mode);
        if (ret == WIFI_ERROR_NONE) {
            printMsg("Successfully set voip mode\n");
        }
    }
exit:
    printMsg("%s:ret = %d\n", __FUNCTION__, ret);
    return;
}

int main(int argc, char *argv[]) {
    pthread_mutex_init(&printMutex, NULL);

    set_hautil_mode(true);
    if (init() != 0) {
        printMsg("could not initiate HAL");
        return WIFI_ERROR_UNKNOWN;
    } else {
        ALOGD("successfully initialized HAL; wlan0 = %p\n", wlan0Handle);
    }

    sem_init(&event_thread_mutex,0,0);

    pthread_cond_init(&eventCacheCondition, NULL);
    pthread_mutex_init(&eventCacheMutex, NULL);

    pthread_t tidEvent;
    pthread_create(&tidEvent, NULL, &eventThreadFunc, NULL);
    sem_wait(&event_thread_mutex);

    if (argc < 2) {
        printUsage();
        goto cleanup;
    } else if (argv[1][0] != '-') {
        printUsage();
        goto cleanup;
    } else if ((strcmp(argv[1], "-nan") == 0) && (argc < 3)) {
        printUsage();
        goto cleanup;
    }
    memset(mac_oui, 0, 3);
    if (strcmp(argv[1], "-nan") == 0) {
        if ((strcmp(argv[2], "-enable") == 0)) {
            enableNan(argv);
        } else if ((strcmp(argv[2], "-disable") == 0)) {
            disableNan();
        } else if ((strcmp(argv[2], "-config") == 0)) {
            configNan(argv);
        } else if ((strcmp(argv[2], "-publish") == 0)) {
            if (argc < 4) {
                printMsg(" -nan [-publish] [-svc <svc_name>] [-info <svc info>\n"
                    "    [-pub_type <0/1/2>] [-pub_count <val>] [-rssi_thresh_flag <0/1>]\n"
                    "    [-tx_type <0/1>] [-ttl <val>] [-svc_awake_dw <val>]\n"
                    "    [-match_rx <0/ascii str>] [-match_tx <0/ascii str>]] [-match_ind <1/2>]\n"
                    "    [-csid <cipher suite type 0/1/2/4/8>] [-key_type <1 or 2>] [-pmk <PMK value>]\n"
                    "    [-passphrase <Passphrase value, len must not be less than 8 or greater than 63>]\n"
                    "    [-scid <scid value>] [-dp_type <0-Unicast, 1-multicast>]\n"
                    "    [-secure_dp <0-No security, 1-Security>] -ranging <0-disable, 1-enable>)\n"
                    "    [-ranging_intvl [intrvl in ms betw two ranging measurements] -ranging_ind \n"
                    "    [ BIT0 - Continuous Ranging event notification, BIT1 - Ingress distance is <=, BIT2 - Egress distance is >=.]\n"
                    "    [-ingress [Ingress distance in centimeters] \n"
                    "    [ -egress [Egress distance in centimeters] \n"
                    "    [-auto_dp_accept [0 - User response required to accept dp, 1 - User response not required to accept dp] \n"
                    "    [-recv_flag <0 to 15>]");
                printMsg("\n *Set/Enable corresponding bits to disable any indications that follow a publish"
                    "\n *BIT0 - Disable publish termination indication."
                    "\n *BIT1 - Disable match expired indication."
                    "\n *BIT2 - Disable followUp indication received (OTA)");
                goto cleanup;
            }
            publishNan(argc, argv);
        } else if ((strcmp(argv[2], "-subscribe") == 0)) {
            if (argc < 3) {
                printMsg(" -nan [-subscribe] [-svc <svc_name>] [-info <svc info>\n"
                    "    [-sub_type <0/1>] [-sub_count <val>] [-pub_ssi <0/1>]\n"
                    "    [-ttl <val>] [-svc_awake_dw <val>] [-match_ind <1/2>]\n"
                    "    [-match_rx <0/ascii str>] [-match_tx <0/ascii str>]\n"
                    "    [-mac_list <addr>] [-srf_include <0/1>] [-rssi_thresh_flag <0/1>]]\n"
                    "    [-csid <cipher suite type 0/1/2/4/8>] [-key_type <1 or 2>]  [-pmk <PMK value>]\n"
                    "    [-passphrase <Passphrase value, len must not be less than 8 or greater than 63>]\n"
                    "    [-scid <scid value>] [-dp_type <0-Unicast, 1-multicast>]\n"
                    "    [-secure_dp <0-No security, 1-Security>] -ranging <0-disable, 1-enable>)\n"
                    "    [-ranging_intvl [intrvl in ms betw two ranging measurements] -ranging_ind \n"
                    "    [BIT0 - Continuous Ranging event notification, BIT1 - Ingress distance is <=, BIT2 - Egress distance is >=.]\n"
                    "    [-ingress [Ingress distance in centimeters] \n"
                    "    [ -egress [Egress distance in centimeters] \n"
                    "    [-recv_flag <0 to 7>]");
                printMsg("\n *Set/Enable corresponding bits to disable any indications that follow a publish"
                    "\n *BIT0 - Disable publish termination indication."
                    "\n *BIT1 - Disable match expired indication."
                    "\n *BIT2 - Disable followUp indication received (OTA)");
                goto cleanup;
            }
            subscribeNan(argc, argv);
        } else if ((strcmp(argv[2], "-cancel_pub") == 0)) {
            if(argc < 3) {
                printMsg(" -nan [-cancel_pub] [<publish id>]\n");
                goto cleanup;
            }
            cancelPublishNan(argv);
        } else if ((strcmp(argv[2], "-cancel_sub") == 0)) {
            if(argc < 3) {
                printMsg(" -nan [-cancel_sub] [<sublish id>]\n");
                goto cleanup;
            }
            cancelSubscribeNan(argv);
        } else if ((strcmp(argv[2], "-transmit") == 0)) {
            if(argc < 5) {
                printMsg(" -nan [-transmit] [-src_id <instance id>] [-dest_id <instance id>]\n"
                    "    [-peer_addr <mac addr>] [-info <svc info>]\n");
                printMsg("\n Mandatory fields are not present\n");
                goto cleanup;
            }
            transmitNan(argc,argv);
        } else if ((strcmp(argv[2], "-get_capabilities") == 0)) {
            getNanCapabilities();
        } else if ((strcmp(argv[2], "-create") == 0)) {
            if(argc < 3) {
                printMsg("\n Mandatory fields are not present\n");
                printMsg(" -nan [-create] [-iface_name <iface name>]\n");
                goto cleanup;
            }
            nanDataPathIfaceCreate(argv);
        } else if ((strcmp(argv[2], "-delete") == 0)) {
            if(argc < 3) {
                printMsg("\n Mandatory fields are not present\n");
                printMsg(" -nan [-delete] [-iface_name <iface name>]\n");
                goto cleanup;
            }
            nanDataPathIfaceDelete(argv);
        } else if ((strcmp(argv[2], "-init") == 0)) {
            if(argc < 7) {
                printMsg("\n Mandatory fields are not present\n");
                printMsg(" -nan [-init] [-pub_id <pub id>] [-disc_mac <discovery mac addr>]\n"
                    "    [-chan <channel in mhz>] [-iface <iface>] [-sec <security>]\n"
                    "    [-qos <qos>] [-info <seq of values in the frame body>]\n"
                    "    [-csid <cipher suite type 0/1/2/4/8>]\n"
                    "    [-scid <scid value>] [-svc <svc_name>]\n");
                goto cleanup;
            }
            nanDataInitRequest(argc, argv);
        } else if ((strcmp(argv[2], "-resp") == 0)) {
            if(argc < 3) {
                printMsg("\n Mandatory fields are not present\n");
                printMsg(" -nan [-resp] [-ndp_id <NDP id>] [-iface <NDP iface name>]\n"
                    "    [-resp_code <accept = 0, accept = 1>] [-qos <qos>]\n"
                    "    [-info <seq of values in the frame body>]\n"
                    "    [-csid <cipher suite type 0/1/2/4/8>]\n"
                    "    [-scid <scid value>] [-svc <svc_name>] \n");
                goto cleanup;
            }
            nanDataIndResponse(argc, argv);
        } else if ((strcmp(argv[2], "-end") == 0)) {
            if(argc < 3) {
                printMsg("\n Mandatory fields are not present\n");
                printMsg(" -nan [-end] [-inst_count <count>] [-inst_id <NDP id>\n");
                goto cleanup;
            }
            nanDataPathEnd(argc, argv);
        } else if ((strcmp(argv[2], "-event_chk") == 0)) {
            nanEventCheck();
        } else if ((strcmp(argv[2], "-ver") == 0)) {
            nanVersion();
        } else if ((strcmp(argv[2], "-exit") == 0)) {
            return WIFI_SUCCESS;
        } else {
            printMsg("\n Unknown command\n");
            printUsage();
            return WIFI_SUCCESS;
        }
    } else if (strcmp(argv[1], "-s") == 0) {
        readTestOptions(argc, argv);
        setPnoMacOui();
        testScan();
    } else if(strcmp(argv[1], "-swc") == 0){
        readTestOptions(argc, argv);
        setPnoMacOui();
        trackSignificantChange();
    } else if (strcmp(argv[1], "-ss") == 0) {
        // Stop scan so clear the OUI too
        setPnoMacOui();
        testStopScan();
    } else if ((strcmp(argv[1], "-h") == 0)  ||
            (strcmp(argv[1], "-hotlist_bssids") == 0)) {
        readTestOptions(argc, argv);
        setPnoMacOui();
        testHotlistAPs();
    } else if (strcmp(argv[1], "-stats") == 0) {
        getLinkStats();
    } else if (strcmp(argv[1], "-rtt") == 0) {
        readRTTOptions(argc, ++argv);
        testRTT();
    } else if (strcmp(argv[1], "-cancel_rtt") == 0) {
        cancelRTT();
    } else if (strcmp(argv[1], "-get_capa_rtt") == 0) {
        getRTTCapability();
    } else if ((strcmp(argv[1], "-get_ch_list") == 0)) {
        readTestOptions(argc, argv);
        getChannelList();
    } else if ((strcmp(argv[1], "-get_responder_info") == 0)) {
        getRttResponderInfo();
    } else if ((strcmp(argv[1], "-enable_resp") == 0)) {
        RttEnableResponder();
    } else if ((strcmp(argv[1], "-cancel_resp") == 0)) {
       cancelRttResponder();
    } else if ((strcmp(argv[1], "-get_feature_set") == 0)) {
        getFeatureSet();
    } else if ((strcmp(argv[1], "-get_feature_matrix") == 0)) {
        getFeatureSetMatrix();
    } else if ((strcmp(argv[1], "-get_wake_stats") == 0)) {
        getWakeStats();
    } else if ((strcmp(argv[1], "-scan_mac_oui") == 0)) {
        readTestOptions(argc, argv);
        setPnoMacOui();
        testScan();
    } else if (strcmp(argv[1], "-nodfs") == 0) {
        u32 nodfs = 0;
        if (argc > 2)
            nodfs = (u32)atoi(argv[2]);
        hal_fn.wifi_set_nodfs_flag(wlan0Handle, nodfs);
    } else if ((strcmp(argv[1], "-ePNO") == 0) || (strcmp(argv[1], "-ePNOCfg") == 0)) {
        memset(&epno_cfg, 0, sizeof(epno_cfg));
        epno_cfg.min5GHz_rssi =  -45;
        epno_cfg.min24GHz_rssi =  -50;
        epno_cfg.initial_score_max  =  110;
        epno_cfg.num_networks = -1;
        readTestOptions(argc, argv);
        epno_cfg.num_networks++;
        setBlacklist(false);
        testPNO(false, (strcmp(argv[1], "-ePNO") == 0));
        if (strcmp(argv[1], "-ePNOCfg") == 0) {
            printMsg("Cannot close, will cleanup cfg, ctrl+c to exit\n");
            while (1);
        }
    } else if (strcmp(argv[1], "-ePNOClear") == 0) {
        testPNO(true, false);
        setBlacklist(true);
    } else if (strcmp(argv[1], "-country") == 0) {
        char *country_code = nullptr;
        if (argc > 2) {
            country_code = argv[2];
        } else {
            printMsg("Country code not provided\n");
            goto cleanup;
        }
        hal_fn.wifi_set_country_code(wlan0Handle, country_code);
    } else if ((strcmp(argv[1], "-logger") == 0)) {
        readLoggerOptions(argc, ++argv);
        runLogger();
    } else if (strcmp(argv[1], "-help") == 0) {
        printUsage();
    } else if ((strcmp(argv[1], "-blacklist_bssids") == 0) ||
        (strcmp(argv[1], "-whitelist_ssids") == 0)) {
        readTestOptions(argc, argv);
        if (set_roaming_configuration) {
            setRoamingConfiguration();
            set_roaming_configuration = false;
        } else {
            setBlacklist(((num_blacklist_bssids == -1) ? true: false));
        }
    } else if ((strcmp(argv[1], "-get_roaming_capabilities") == 0)) {
        getRoamingCapabilities();
    } else if ((strcmp(argv[1], "-set_fw_roaming_state") == 0)) {
        fw_roaming_state_t roamState = (fw_roaming_state_t)(atoi)(argv[2]);
        setFWRoamingState(roamState);
    } else if (strcmp(argv[1], "-rssi_monitor") == 0) {
        readTestOptions(argc, argv);
        testRssiMonitor();
    } else if (strcmp(argv[1], "-mkeep_alive") == 0) {
        readKeepAliveOptions(argc, ++argv);
    } else if ((strcmp(argv[1], "-nd_offload") == 0) && (argc > 2)) {
        u8 enable = (u8)(atoi)(argv[2]);
        hal_fn.wifi_configure_nd_offload(wlan0Handle, enable);
    } else if ((strcmp(argv[1], "-apf") == 0)) {
        testApfOptions(argc, ++argv);
    } else if ((strcmp(argv[1], "-sar") == 0)) {
        testSarOptions(argc-1, ++argv);
    } else if ((strcmp(argv[1], "-latency") == 0)) {
        testLatencyModeOptions(argc-1, ++argv);
    } else if ((strcmp(argv[1], "-thermal") == 0)) {
        testThermalMitigationOptions(argc-1, ++argv);
    } else if ((strcmp(argv[1], "-dscp") == 0)) {
        testDscpOptions(argc, ++argv);
    } else if ((strcmp(argv[1], "-ch_avoid") == 0)) {
        testChannelAvoidanceOptions(argc, ++argv);
    } else if ((strcmp(argv[1], "-usable_ch") == 0)) {
        testUsableChannelOptions(argc, ++argv);
    } else if ((strcmp(argv[1], "-ifadd") == 0)) {
        if (argc < 3) {
            printMsg("\n Mandatory fields are not present\n");
            printMsg(" [-ifadd] [-name <virtual iface name should be wlanX, swlanX, awareX, p2pX>"
                " -type 0/1/2/3]\n");
            printMsg(" 0 for STA, 1 for AP, 2 for P2P, 3 for NAN\n");
            goto cleanup;
        }
        VirtualIfaceAdd(argv);
    } else if ((strcmp(argv[1], "-ifdel") == 0)) {
        if(argc < 3) {
            printMsg("\n Mandatory fields are not present\n");
            printMsg("[-ifdel] [-name <virtual iface name>]\n");
            goto cleanup;
        }
        VirtualIfaceDelete(argv);
    } else if ((strcmp(argv[1], "-latency_mode") == 0) && (argc > 2)) {
        SetLatencyMode(argv);
    } else if ((strcmp(argv[1], "-multista_pri_connection") == 0)) {
        if(argc < 3) {
            printMsg("\n Mandatory fields are not present\n");
            printMsg("[-multista_pri_connection] [iface name>]\n");
            goto cleanup;
        }
        MultiStaSetPrimaryConnection(argv);
    } else if ((strcmp(argv[1], "-multista_usecase") == 0)) {
        if(argc < 3) {
            printMsg("\n Mandatory fields are not present\n");
            printMsg("[-multista_usecase] [multista usecase 0/1]\n");
            goto cleanup;
        }
        MultiStaSetUsecase(argv);
    } else if ((strcmp(argv[1], "-voip_mode") == 0) && (argc > 2)) {
        SetVoipMode(argv);
    } else if (strcmp(argv[1], "-twt") == 0) {
        if ((strcmp(argv[2], "-setup") == 0)) {
            setupTwtRequest(argv);
        } else if ((strcmp(argv[2], "-teardown") == 0)) {
            TeardownTwt(argv);
        } else if ((strcmp(argv[2], "-info_frame") == 0)) {
            InfoFrameTwt(argv);
        } else if ((strcmp(argv[2], "-get_stats") == 0)) {
            GetTwtStats(argv);
        } else if ((strcmp(argv[2], "-clear_stats") == 0)) {
            ClearTwtStats(argv);
        } else if ((strcmp(argv[2], "-event_chk") == 0)) {
            twtEventCheck();
        } else {
            printMsg("\n Unknown command\n");
            printTwtUsage();
            return WIFI_SUCCESS;
        }
    } else if (strcmp(argv[1], "-get_capa_twt") == 0) {
        getTWTCapability();
    } else if ((strcmp(argv[1], "-dtim_multiplier") == 0) && (argc > 2)) {
        int dtim_multiplier = (atoi)(argv[2]);
        hal_fn.wifi_set_dtim_config(wlan0Handle, dtim_multiplier);
    } else if (strcmp(argv[1], "-on_ssr") == 0) {
        hal_fn.wifi_trigger_subsystem_restart(halHandle);
    } else if ((strcmp(argv[1], "-getSupportedRadioMatrix") == 0)) {
        getSupportedRadioMatrix();
    } else if ((strcmp(argv[1], "-tx_pwr_cap") == 0)) {
        testTxPowerLimitOptions(argc, ++argv);
    } else if (strcmp(argv[1], "-chre_nan_rtt") == 0) {
        if ((strcmp(argv[2], "-enable") == 0)) {
            //enable CHRE NAN RTT
            enableChreNanRtt();
        } else if ((strcmp(argv[2], "-disable") == 0)) {
            //disable CHRE NAN RTT
            disableChreNanRtt();
        } else {
            printMsg("\n Unknown command\n");
            printChreNanRttUsage();
        }
    } else if (strcmp(argv[1], "-chre") == 0) {
        if ((strcmp(argv[2], "-register") == 0)) {
            //register CHRE callback
            registerChreCallback();
        } else {
            printMsg("\n Unknown command\n");
            printChreNanRttUsage();
        }
    } else if (strcmp(argv[1], "-get_cached_scan_results") == 0) {
        getWifiCachedScanResults();
    } else if (strcmp(argv[1], "-set_channel_mask") == 0) {
        u32 channel_mask = 0;
        if (argc > 2) {
            channel_mask = (u32)atoi(argv[2]);
        }
        hal_fn.wifi_enable_sta_channel_for_peer_network(halHandle, channel_mask);
    } else {
        printUsage();
    }

cleanup:
    cleanup();
    return WIFI_SUCCESS;
}
