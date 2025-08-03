/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "mac80211_create_radios"

#include <memory>
#include <log/log.h>
#include <netlink/genl/ctrl.h>
#include <netlink/genl/genl.h>
#include <netlink/netlink.h>
#include <net/ethernet.h>
#include <linux/nl80211.h>

#define RETURN(R) return (R);
#define RETURN_ERROR(C, R) \
    do { \
        ALOGE("%s:%d '%s' failed", __func__, __LINE__, C); \
        return (R); \
    } while (false);
#define RETURN_NL_ERROR(C, NLR, R) \
    do { \
        ALOGE("%s:%d '%s' failed with '%s'", __func__, __LINE__, C, nlErrStr((NLR))); \
        return (R); \
    } while (false);
#define BIT(x) (1 << (x))

enum {
    HWSIM_CMD_UNSPEC,
    HWSIM_CMD_REGISTER,
    HWSIM_CMD_FRAME,
    HWSIM_CMD_TX_INFO_FRAME,
    HWSIM_CMD_NEW_RADIO,
    HWSIM_CMD_DEL_RADIO,
    HWSIM_CMD_GET_RADIO,
};

enum {
    HWSIM_ATTR_UNSPEC,
    HWSIM_ATTR_ADDR_RECEIVER,
    HWSIM_ATTR_ADDR_TRANSMITTER,
    HWSIM_ATTR_FRAME,
    HWSIM_ATTR_FLAGS,
    HWSIM_ATTR_RX_RATE,
    HWSIM_ATTR_SIGNAL,
    HWSIM_ATTR_TX_INFO,
    HWSIM_ATTR_COOKIE,
    HWSIM_ATTR_CHANNELS,
    HWSIM_ATTR_RADIO_ID,
    HWSIM_ATTR_REG_HINT_ALPHA2,
    HWSIM_ATTR_REG_CUSTOM_REG,
    HWSIM_ATTR_REG_STRICT_REG,
    HWSIM_ATTR_SUPPORT_P2P_DEVICE,
    HWSIM_ATTR_USE_CHANCTX,
    HWSIM_ATTR_DESTROY_RADIO_ON_CLOSE,
    HWSIM_ATTR_RADIO_NAME,
    HWSIM_ATTR_NO_VIF,
    HWSIM_ATTR_FREQ,
    HWSIM_ATTR_PAD,
    HWSIM_ATTR_TX_INFO_FLAGS,
    HWSIM_ATTR_PERM_ADDR,
    HWSIM_ATTR_IFTYPE_SUPPORT,
    HWSIM_ATTR_CIPHER_SUPPORT,
    HWSIM_ATTR_MLO_SUPPORT,
    HWSIM_ATTR_PMSR_SUPPORT,
};

struct nl_sock_deleter {
    void operator()(struct nl_sock* x) const { nl_socket_free(x); }
};

struct nl_msg_deleter {
    void operator()(struct nl_msg* x) const { nlmsg_free(x); }
};

constexpr char kHwSimFamilyName[] = "MAC80211_HWSIM";
constexpr int kHwSimVersion = 1;
constexpr uint32_t kChannels = 2;
constexpr uint32_t kPmsrMaxPeers = 10;
constexpr uint32_t kFtmAllPreables = BIT(NL80211_PREAMBLE_LEGACY)
        | BIT(NL80211_PREAMBLE_HT)
        | BIT(NL80211_PREAMBLE_VHT)
        | BIT(NL80211_PREAMBLE_DMG)
        | BIT(NL80211_PREAMBLE_HE);
constexpr uint32_t kFtmAllBandwidths = BIT(NL80211_CHAN_WIDTH_20_NOHT)
        | BIT(NL80211_CHAN_WIDTH_20)
        | BIT(NL80211_CHAN_WIDTH_40)
        | BIT(NL80211_CHAN_WIDTH_80)
        | BIT(NL80211_CHAN_WIDTH_80P80)
        | BIT(NL80211_CHAN_WIDTH_160)
        | BIT(NL80211_CHAN_WIDTH_5)
        | BIT(NL80211_CHAN_WIDTH_10)
        | BIT(NL80211_CHAN_WIDTH_1)
        | BIT(NL80211_CHAN_WIDTH_2)
        | BIT(NL80211_CHAN_WIDTH_4)
        | BIT(NL80211_CHAN_WIDTH_8)
        | BIT(NL80211_CHAN_WIDTH_16)
        | BIT(NL80211_CHAN_WIDTH_320);
constexpr uint8_t kFtmMaxBurstsExponent = 15;
constexpr uint8_t kFtmMaxFtmsPerBurst = 31;

const char* nlErrStr(const int e) { return (e < 0) ? nl_geterror(e) : ""; }

int parseInt(const char* str, int* result) { return sscanf(str, "%d", result); }

std::unique_ptr<struct nl_msg, nl_msg_deleter> createNlMessage(
        const int family,
        const int cmd) {
    std::unique_ptr<struct nl_msg, nl_msg_deleter> msg(nlmsg_alloc());
    if (!msg) { RETURN_ERROR("nlmsg_alloc", nullptr); }

    void* user = genlmsg_put(msg.get(), NL_AUTO_PORT, NL_AUTO_SEQ, family, 0,
                       NLM_F_REQUEST, cmd, kHwSimVersion);
    if (!user) { RETURN_ERROR("genlmsg_put", nullptr); }

    RETURN(msg);
}

#define PUT_FLAG(MSG, TYPE, R) \
    do { \
        (R) = nla_put_flag((MSG).get(), TYPE); \
        if (R) RETURN_NL_ERROR("nla_put_flag(" #TYPE ")", R, nullptr); \
    } while (false);

#define PUT_DATA(MSG, TYPE, V, SIZE, R) \
    do { \
        (R) = nla_put((MSG).get(), TYPE, SIZE, V); \
        if (R) RETURN_NL_ERROR("nla_put(" #TYPE ")", R, nullptr); \
    } while (false);

#define PUT_VALUE(MSG, TYPE, V, R) PUT_DATA(MSG, TYPE, &(V), sizeof(V), (R))

#define NEST_START(MSG, TYPE, ATTR) \
    do { \
        (ATTR) = nla_nest_start((MSG).get(), TYPE); \
        if (!(ATTR)) RETURN_ERROR("nla_nest_start(" #TYPE ")", nullptr); \
    } while (false);

#define NEST_END(MSG, START, R) \
    do { \
        (R) = nla_nest_end((MSG).get(), START); \
        if (R) RETURN_NL_ERROR("nla_nest_end(" #START ")", R, nullptr); \
    } while (false);

std::unique_ptr<struct nl_msg, nl_msg_deleter>
buildCreateRadioMessage(const int family, const uint8_t mac[ETH_ALEN],
                        const bool enablePmsr) {
    std::unique_ptr<struct nl_msg, nl_msg_deleter> msg =
        createNlMessage(family, HWSIM_CMD_NEW_RADIO);
    if (!msg) { RETURN(nullptr); }

    int ret;
    PUT_DATA(msg, HWSIM_ATTR_PERM_ADDR, mac, ETH_ALEN, ret);
    PUT_FLAG(msg, HWSIM_ATTR_SUPPORT_P2P_DEVICE, ret);
    PUT_VALUE(msg, HWSIM_ATTR_CHANNELS, kChannels, ret);

    if (enablePmsr) {
        struct nlattr* pmsr;
        NEST_START(msg, HWSIM_ATTR_PMSR_SUPPORT, pmsr);

        PUT_VALUE(msg, NL80211_PMSR_ATTR_MAX_PEERS, kPmsrMaxPeers, ret);

        struct nlattr* pmsrType;
        NEST_START(msg, NL80211_PMSR_ATTR_TYPE_CAPA, pmsrType);

        struct nlattr* ftm;
        NEST_START(msg, NL80211_PMSR_TYPE_FTM, ftm);

        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_ASAP, ret);
        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_NON_ASAP, ret);
        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_REQ_LCI, ret);
        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_REQ_CIVICLOC, ret);
        PUT_VALUE(msg, NL80211_PMSR_FTM_CAPA_ATTR_PREAMBLES,
                     kFtmAllPreables, ret);
        PUT_VALUE(msg, NL80211_PMSR_FTM_CAPA_ATTR_BANDWIDTHS,
                     kFtmAllBandwidths, ret);
        PUT_VALUE(msg, NL80211_PMSR_FTM_CAPA_ATTR_MAX_BURSTS_EXPONENT,
                     kFtmMaxBurstsExponent, ret);
        PUT_VALUE(msg, NL80211_PMSR_FTM_CAPA_ATTR_MAX_FTMS_PER_BURST,
                     kFtmMaxFtmsPerBurst, ret);
        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_TRIGGER_BASED, ret);
        PUT_FLAG(msg, NL80211_PMSR_FTM_CAPA_ATTR_NON_TRIGGER_BASED, ret);

        NEST_END(msg, ftm, ret);
        NEST_END(msg, pmsrType, ret);
        NEST_END(msg, pmsr, ret);
    }

    RETURN(msg);
}

int createRadios(struct nl_sock* socket, const int netlinkFamily,
                 const int nRadios, const int macPrefix,
                 const bool enablePmsr) {
    uint8_t mac[ETH_ALEN] = {};
    mac[0] = 0x02;
    mac[1] = (macPrefix >> CHAR_BIT) & 0xFF;
    mac[2] = macPrefix & 0xFF;

    for (int idx = 0; idx < nRadios; ++idx) {
        mac[4] = idx;

        std::unique_ptr<struct nl_msg, nl_msg_deleter> msg =
            buildCreateRadioMessage(netlinkFamily, mac, enablePmsr);
        if (msg) {
            int ret = nl_send_auto(socket, msg.get());
            if (ret < 0) { RETURN_NL_ERROR("nl_send_auto", ret, 1); }
        } else {
            RETURN(1);
        }
    }

    RETURN(0);
}

int manageRadios(const int nRadios, const int macPrefix,
                 const bool enablePmsr) {
    std::unique_ptr<struct nl_sock, nl_sock_deleter> socket(nl_socket_alloc());
    if (!socket) { RETURN_ERROR("nl_socket_alloc", 1); }

    int ret;
    ret = genl_connect(socket.get());
    if (ret) { RETURN_NL_ERROR("genl_connect", ret, 1); }

    const int netlinkFamily = genl_ctrl_resolve(socket.get(), kHwSimFamilyName);
    if (netlinkFamily < 0) { RETURN_NL_ERROR("genl_ctrl_resolve", ret, 1); }

    ret = createRadios(socket.get(), netlinkFamily, nRadios, macPrefix,
                       enablePmsr);
    if (ret) { RETURN(ret); }

    RETURN(0);
}

int printUsage(FILE* dst, const int ret) {
    fprintf(dst, "%s",
    "Usage:\n"
    "   mac80211_create_radios [options] n_radios mac_prefix\n"
    "   where\n"
    "       n_radios - int, [1,100], e.g. 2;\n"
    "       mac_prefix - int, [0, 65535], e.g. 5555.\n\n"
    "   mac80211_create_radios will create n_radios with MAC addresses\n"
    "   02:pp:pp:00:nn:00, where nn is incremented (from zero)\n"
    "   and pp:pp is the mac_prefix specified.\n"
    "\n"
    "   options:\n"
    "       --enable-pmsr: enable peer measurement for RTT support.\n");

    return ret;
}

int main(int argc, char* argv[]) {
    if (argc != 4 && argc != 3) { return printUsage(stdout, 0); }

    int argIndex = 1;
    bool enablePmsr = false;
    if (!strcmp(argv[argIndex], "--enable-pmsr")) {
      enablePmsr = true;
      argIndex++;
    }
    int nRadios;
    if (!parseInt(argv[argIndex++], &nRadios)) { return printUsage(stderr, 1); }
    if (nRadios < 1) { return printUsage(stderr, 1); }
    if (nRadios > 100) { return printUsage(stderr, 1); }

    int macPrefix;
    if (!parseInt(argv[argIndex], &macPrefix)) { return printUsage(stderr, 1); }
    if (macPrefix < 0) { return printUsage(stderr, 1); }
    if (macPrefix > UINT16_MAX) { return printUsage(stderr, 1); }

    return manageRadios(nRadios, macPrefix, enablePmsr);
}
