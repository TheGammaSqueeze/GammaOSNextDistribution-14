/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "TcUtils"

#include "tcutils/tcutils.h"

#include "logging.h"
#include "bpf/KernelUtils.h"
#include "scopeguard.h"

#include <arpa/inet.h>
#include <cerrno>
#include <cstring>
#include <libgen.h>
#include <linux/if_arp.h>
#include <linux/if_ether.h>
#include <linux/netlink.h>
#include <linux/pkt_cls.h>
#include <linux/pkt_sched.h>
#include <linux/rtnetlink.h>
#include <linux/tc_act/tc_bpf.h>
#include <net/if.h>
#include <stdio.h>
#include <sys/socket.h>
#include <unistd.h>
#include <utility>

#define BPF_FD_JUST_USE_INT
#include <BpfSyscallWrappers.h>
#undef BPF_FD_JUST_USE_INT

// The maximum length of TCA_BPF_NAME. Sync from net/sched/cls_bpf.c.
#define CLS_BPF_NAME_LEN 256

// Classifier name. See cls_bpf_ops in net/sched/cls_bpf.c.
#define CLS_BPF_KIND_NAME "bpf"

namespace android {
namespace {

/**
 * IngressPoliceFilterBuilder builds a nlmsg request equivalent to the following
 * tc command:
 *
 * tc filter add dev .. ingress prio .. protocol .. matchall \
 *     action police rate .. burst .. conform-exceed pipe/continue \
 *     action bpf object-pinned .. \
 *     drop
 */
class IngressPoliceFilterBuilder final {
  // default mtu is 2047, so the cell logarithm factor (cell_log) is 3.
  // 0x7FF >> 0x3FF x 2^1 >> 0x1FF x 2^2 >> 0xFF x 2^3
  static constexpr int RTAB_CELL_LOGARITHM = 3;
  static constexpr size_t RTAB_SIZE = 256;
  static constexpr unsigned TIME_UNITS_PER_SEC = 1000000;

  struct Request {
    nlmsghdr n;
    tcmsg t;
    struct {
      nlattr attr;
      char str[NLMSG_ALIGN(sizeof("matchall"))];
    } kind;
    struct {
      nlattr attr;
      struct {
        nlattr attr;
        struct {
          nlattr attr;
          struct {
            nlattr attr;
            char str[NLMSG_ALIGN(sizeof("police"))];
          } kind;
          struct {
            nlattr attr;
            struct {
              nlattr attr;
              struct tc_police obj;
            } police;
            struct {
              nlattr attr;
              uint32_t u32[RTAB_SIZE];
            } rtab;
            struct {
              nlattr attr;
              int32_t s32;
            } notexceedact;
          } opt;
        } act1;
        struct {
          nlattr attr;
          struct {
            nlattr attr;
            char str[NLMSG_ALIGN(sizeof("bpf"))];
          } kind;
          struct {
            nlattr attr;
            struct {
              nlattr attr;
              uint32_t u32;
            } fd;
            struct {
              nlattr attr;
              char str[NLMSG_ALIGN(CLS_BPF_NAME_LEN)];
            } name;
            struct {
              nlattr attr;
              struct tc_act_bpf obj;
            } parms;
          } opt;
        } act2;
      } acts;
    } opt;
  };

  // class members
  const unsigned mBurstInBytes;
  const char *mBpfProgPath;
  int mBpfFd;
  Request mRequest;

  static double getTickInUsec() {
    FILE *fp = fopen("/proc/net/psched", "re");
    if (!fp) {
      ALOGE("fopen(\"/proc/net/psched\"): %s", strerror(errno));
      return 0.0;
    }
    auto scopeGuard = base::make_scope_guard([fp] { fclose(fp); });

    uint32_t t2us;
    uint32_t us2t;
    uint32_t clockRes;
    const bool isError =
        fscanf(fp, "%08x%08x%08x", &t2us, &us2t, &clockRes) != 3;

    if (isError) {
      ALOGE("fscanf(/proc/net/psched, \"%%08x%%08x%%08x\"): %s",
               strerror(errno));
      return 0.0;
    }

    const double clockFactor =
        static_cast<double>(clockRes) / TIME_UNITS_PER_SEC;
    return static_cast<double>(t2us) / static_cast<double>(us2t) * clockFactor;
  }

  static inline const double kTickInUsec = getTickInUsec();

public:
  // clang-format off
  IngressPoliceFilterBuilder(int ifIndex, uint16_t prio, uint16_t proto, unsigned rateInBytesPerSec,
                      unsigned burstInBytes, const char* bpfProgPath)
      : mBurstInBytes(burstInBytes),
        mBpfProgPath(bpfProgPath),
        mBpfFd(-1),
        mRequest{
            .n = {
                .nlmsg_len = sizeof(mRequest),
                .nlmsg_type = RTM_NEWTFILTER,
                .nlmsg_flags = NLM_F_REQUEST | NLM_F_ACK | NLM_F_EXCL | NLM_F_CREATE,
            },
            .t = {
                .tcm_family = AF_UNSPEC,
                .tcm_ifindex = ifIndex,
                .tcm_handle = TC_H_UNSPEC,
                .tcm_parent = TC_H_MAKE(TC_H_CLSACT, TC_H_MIN_INGRESS),
                .tcm_info = (static_cast<uint32_t>(prio) << 16)
                            | static_cast<uint32_t>(htons(proto)),
            },
            .kind = {
                .attr = {
                    .nla_len = sizeof(mRequest.kind),
                    .nla_type = TCA_KIND,
                },
                .str = "matchall",
            },
            .opt = {
                .attr = {
                    .nla_len = sizeof(mRequest.opt),
                    .nla_type = TCA_OPTIONS,
                },
                .acts = {
                    .attr = {
                        .nla_len = sizeof(mRequest.opt.acts),
                        .nla_type = TCA_MATCHALL_ACT,
                    },
                    .act1 = {
                        .attr = {
                            .nla_len = sizeof(mRequest.opt.acts.act1),
                            .nla_type = 1, // action priority
                        },
                        .kind = {
                            .attr = {
                                .nla_len = sizeof(mRequest.opt.acts.act1.kind),
                                .nla_type = TCA_ACT_KIND,
                            },
                            .str = "police",
                        },
                        .opt = {
                            .attr = {
                                .nla_len = sizeof(mRequest.opt.acts.act1.opt),
                                .nla_type = TCA_ACT_OPTIONS | NLA_F_NESTED,
                            },
                            .police = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act1.opt.police),
                                    .nla_type = TCA_POLICE_TBF,
                                },
                                .obj = {
                                    .action = TC_ACT_PIPE,
                                    .burst = 0,
                                    .rate = {
                                        .cell_log = RTAB_CELL_LOGARITHM,
                                        .linklayer = TC_LINKLAYER_ETHERNET,
                                        .cell_align = -1,
                                        .rate = rateInBytesPerSec,
                                    },
                                },
                            },
                            .rtab = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act1.opt.rtab),
                                    .nla_type = TCA_POLICE_RATE,
                                },
                                .u32 = {},
                            },
                            .notexceedact = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act1.opt.notexceedact),
                                    .nla_type = TCA_POLICE_RESULT,
                                },
                                .s32 = TC_ACT_UNSPEC,
                            },
                        },
                    },
                    .act2 = {
                        .attr = {
                            .nla_len = sizeof(mRequest.opt.acts.act2),
                            .nla_type = 2, // action priority
                        },
                        .kind = {
                            .attr = {
                                .nla_len = sizeof(mRequest.opt.acts.act2.kind),
                                .nla_type = TCA_ACT_KIND,
                            },
                            .str = "bpf",
                        },
                        .opt = {
                            .attr = {
                                .nla_len = sizeof(mRequest.opt.acts.act2.opt),
                                .nla_type = TCA_ACT_OPTIONS | NLA_F_NESTED,
                            },
                            .fd = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act2.opt.fd),
                                    .nla_type = TCA_ACT_BPF_FD,
                                },
                                .u32 = 0, // set during build()
                            },
                            .name = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act2.opt.name),
                                    .nla_type = TCA_ACT_BPF_NAME,
                                },
                                .str = "placeholder",
                            },
                            .parms = {
                                .attr = {
                                    .nla_len = sizeof(mRequest.opt.acts.act2.opt.parms),
                                    .nla_type = TCA_ACT_BPF_PARMS,
                                },
                                .obj = {
                                    // default action to be executed when bpf prog
                                    // returns TC_ACT_UNSPEC.
                                    .action = TC_ACT_SHOT,
                                },
                            },
                        },
                    },
                },
            },
        } {
      // constructor body
  }
  // clang-format on

  ~IngressPoliceFilterBuilder() {
    // TODO: use unique_fd
    if (mBpfFd != -1) {
      close(mBpfFd);
    }
  }

  constexpr unsigned getRequestSize() const { return sizeof(Request); }

private:
  unsigned calculateXmitTime(unsigned size) {
    const uint32_t rate = mRequest.opt.acts.act1.opt.police.obj.rate.rate;
    return (static_cast<double>(size) / static_cast<double>(rate)) *
           TIME_UNITS_PER_SEC * kTickInUsec;
  }

  void initBurstRate() {
    mRequest.opt.acts.act1.opt.police.obj.burst =
        calculateXmitTime(mBurstInBytes);
  }

  // Calculates a table with 256 transmission times for different packet sizes
  // (all the way up to MTU). RTAB_CELL_LOGARITHM is used as a scaling factor.
  // In this case, MTU size is always 2048, so RTAB_CELL_LOGARITHM is always
  // 3. Therefore, this function generates the transmission times for packets
  // of size 1..256 x 2^3.
  void initRateTable() {
    for (unsigned i = 0; i < RTAB_SIZE; ++i) {
      unsigned adjustedSize = (i + 1) << RTAB_CELL_LOGARITHM;
      mRequest.opt.acts.act1.opt.rtab.u32[i] = calculateXmitTime(adjustedSize);
    }
  }

  int initBpfFd() {
    mBpfFd = bpf::retrieveProgram(mBpfProgPath);
    if (mBpfFd == -1) {
      int error = errno;
      ALOGE("retrieveProgram failed: %d", error);
      return -error;
    }

    mRequest.opt.acts.act2.opt.fd.u32 = static_cast<uint32_t>(mBpfFd);
    snprintf(mRequest.opt.acts.act2.opt.name.str,
             sizeof(mRequest.opt.acts.act2.opt.name.str), "%s:[*fsobj]",
             basename(mBpfProgPath));

    return 0;
  }

public:
  int build() {
    if (kTickInUsec == 0.0) {
      return -EINVAL;
    }

    initBurstRate();
    initRateTable();
    return initBpfFd();
  }

  const Request *getRequest() const {
    // Make sure to call build() before calling this function. Otherwise, the
    // request will be invalid.
    return &mRequest;
  }
};

const sockaddr_nl KERNEL_NLADDR = {AF_NETLINK, 0, 0, 0};
const uint16_t NETLINK_REQUEST_FLAGS = NLM_F_REQUEST | NLM_F_ACK;

int sendAndProcessNetlinkResponse(const void *req, int len) {
  // TODO: use unique_fd instead of ScopeGuard
  int fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
  if (fd == -1) {
    int error = errno;
    ALOGE("socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE): %d",
             error);
    return -error;
  }
  auto scopeGuard = base::make_scope_guard([fd] { close(fd); });

  static constexpr int on = 1;
  if (setsockopt(fd, SOL_NETLINK, NETLINK_CAP_ACK, &on, sizeof(on))) {
    int error = errno;
    ALOGE("setsockopt(fd, SOL_NETLINK, NETLINK_CAP_ACK, 1): %d", error);
    return -error;
  }

  if (setsockopt(fd, SOL_NETLINK, NETLINK_EXT_ACK, &on, sizeof(on))) {
    int error = errno;
    ALOGW("setsockopt(fd, SOL_NETLINK, NETLINK_EXT_ACK, 1): %d", error);
    // will fail on 4.9 kernels so don't: return -error;
  }

  // this is needed to get valid strace netlink parsing, it allocates the pid
  if (bind(fd, (const struct sockaddr *)&KERNEL_NLADDR,
           sizeof(KERNEL_NLADDR))) {
    int error = errno;
    ALOGE("bind(fd, {AF_NETLINK, 0, 0}: %d)", error);
    return -error;
  }

  // we do not want to receive messages from anyone besides the kernel
  if (connect(fd, (const struct sockaddr *)&KERNEL_NLADDR,
              sizeof(KERNEL_NLADDR))) {
    int error = errno;
    ALOGE("connect(fd, {AF_NETLINK, 0, 0}): %d", error);
    return -error;
  }

  int rv = send(fd, req, len, 0);

  if (rv == -1) {
    int error = errno;
    ALOGE("send(fd, req, len, 0) failed: %d", error);
    return -error;
  }

  if (rv != len) {
    ALOGE("send(fd, req, len = %d, 0) returned invalid message size %d", len,
             rv);
    return -EMSGSIZE;
  }

  struct {
    nlmsghdr h;
    nlmsgerr e;
    char buf[256];
  } resp = {};

  rv = recv(fd, &resp, sizeof(resp), MSG_TRUNC);

  if (rv == -1) {
    int error = errno;
    ALOGE("recv() failed: %d", error);
    return -error;
  }

  if (rv < (int)NLMSG_SPACE(sizeof(struct nlmsgerr))) {
    ALOGE("recv() returned short packet: %d", rv);
    return -EBADMSG;
  }

  if (resp.h.nlmsg_len != (unsigned)rv) {
    ALOGE("recv() returned invalid header length: %d != %d",
             resp.h.nlmsg_len, rv);
    return -EBADMSG;
  }

  if (resp.h.nlmsg_type != NLMSG_ERROR) {
    ALOGE("recv() did not return NLMSG_ERROR message: %d",
             resp.h.nlmsg_type);
    return -ENOMSG;
  }

  if (resp.e.error) {
    ALOGE("NLMSG_ERROR message return error: %d", resp.e.error);
  }
  return resp.e.error; // returns 0 on success
}

int hardwareAddressType(const char *interface) {
  int fd = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC, 0);
  if (fd < 0)
    return -errno;
  auto scopeGuard = base::make_scope_guard([fd] { close(fd); });

  struct ifreq ifr = {};
  // We use strncpy() instead of strlcpy() since kernel has to be able
  // to handle non-zero terminated junk passed in by userspace anyway,
  // and this way too long interface names (more than IFNAMSIZ-1 = 15
  // characters plus terminating NULL) will not get truncated to 15
  // characters and zero-terminated and thus potentially erroneously
  // match a truncated interface if one were to exist.
  strncpy(ifr.ifr_name, interface, sizeof(ifr.ifr_name));

  if (ioctl(fd, SIOCGIFHWADDR, &ifr, sizeof(ifr))) {
    return -errno;
  }
  return ifr.ifr_hwaddr.sa_family;
}

} // namespace

int isEthernet(const char *iface, bool &isEthernet) {
  int rv = hardwareAddressType(iface);
  if (rv < 0) {
    ALOGE("Get hardware address type of interface %s failed: %s", iface,
             strerror(-rv));
    return rv;
  }

  // Backwards compatibility with pre-GKI kernels that use various custom
  // ARPHRD_* for their cellular interface
  switch (rv) {
  // ARPHRD_PUREIP on at least some Mediatek Android kernels
  // example: wembley with 4.19 kernel
  case 520:
  // in Linux 4.14+ rmnet support was upstreamed and ARHRD_RAWIP became 519,
  // but it is 530 on at least some Qualcomm Android 4.9 kernels with rmnet
  // example: Pixel 3 family
  case 530:
    // >5.4 kernels are GKI2.0 and thus upstream compatible, however 5.10
    // shipped with Android S, so (for safety) let's limit ourselves to
    // >5.10, ie. 5.11+ as a guarantee we're on Android T+ and thus no
    // longer need this non-upstream compatibility logic
    static bool is_pre_5_11_kernel = !bpf::isAtLeastKernelVersion(5, 11, 0);
    if (is_pre_5_11_kernel)
      return false;
  }

  switch (rv) {
  case ARPHRD_ETHER:
    isEthernet = true;
    return 0;
  case ARPHRD_NONE:
  case ARPHRD_PPP:
  case ARPHRD_RAWIP:
    isEthernet = false;
    return 0;
  default:
    ALOGE("Unknown hardware address type %d on interface %s", rv, iface);
    return -EAFNOSUPPORT;
  }
}

// ADD:     nlMsgType=RTM_NEWQDISC nlMsgFlags=NLM_F_EXCL|NLM_F_CREATE
// REPLACE: nlMsgType=RTM_NEWQDISC nlMsgFlags=NLM_F_CREATE|NLM_F_REPLACE
// DEL:     nlMsgType=RTM_DELQDISC nlMsgFlags=0
int doTcQdiscClsact(int ifIndex, uint16_t nlMsgType, uint16_t nlMsgFlags) {
  // This is the name of the qdisc we are attaching.
  // Some hoop jumping to make this compile time constant with known size,
  // so that the structure declaration is well defined at compile time.
#define CLSACT "clsact"
  // sizeof() includes the terminating NULL
  static constexpr size_t ASCIIZ_LEN_CLSACT = sizeof(CLSACT);

  const struct {
    nlmsghdr n;
    tcmsg t;
    struct {
      nlattr attr;
      char str[NLMSG_ALIGN(ASCIIZ_LEN_CLSACT)];
    } kind;
  } req = {
      .n =
          {
              .nlmsg_len = sizeof(req),
              .nlmsg_type = nlMsgType,
              .nlmsg_flags =
                  static_cast<__u16>(NETLINK_REQUEST_FLAGS | nlMsgFlags),
          },
      .t =
          {
              .tcm_family = AF_UNSPEC,
              .tcm_ifindex = ifIndex,
              .tcm_handle = TC_H_MAKE(TC_H_CLSACT, 0),
              .tcm_parent = TC_H_CLSACT,
          },
      .kind =
          {
              .attr =
                  {
                      .nla_len = NLA_HDRLEN + ASCIIZ_LEN_CLSACT,
                      .nla_type = TCA_KIND,
                  },
              .str = CLSACT,
          },
  };
#undef CLSACT

  return sendAndProcessNetlinkResponse(&req, sizeof(req));
}

// tc filter add dev .. in/egress prio 1 protocol ipv6/ip bpf object-pinned
// /sys/fs/bpf/... direct-action
int tcAddBpfFilter(int ifIndex, bool ingress, uint16_t prio, uint16_t proto,
                   const char *bpfProgPath) {
  const int bpfFd = bpf::retrieveProgram(bpfProgPath);
  if (bpfFd == -1) {
    ALOGE("retrieveProgram failed: %d", errno);
    return -errno;
  }
  auto scopeGuard = base::make_scope_guard([bpfFd] { close(bpfFd); });

  struct {
    nlmsghdr n;
    tcmsg t;
    struct {
      nlattr attr;
      // The maximum classifier name length is defined in
      // tcf_proto_ops in include/net/sch_generic.h.
      char str[NLMSG_ALIGN(sizeof(CLS_BPF_KIND_NAME))];
    } kind;
    struct {
      nlattr attr;
      struct {
        nlattr attr;
        __u32 u32;
      } fd;
      struct {
        nlattr attr;
        char str[NLMSG_ALIGN(CLS_BPF_NAME_LEN)];
      } name;
      struct {
        nlattr attr;
        __u32 u32;
      } flags;
    } options;
  } req = {
      .n =
          {
              .nlmsg_len = sizeof(req),
              .nlmsg_type = RTM_NEWTFILTER,
              .nlmsg_flags = NETLINK_REQUEST_FLAGS | NLM_F_EXCL | NLM_F_CREATE,
          },
      .t =
          {
              .tcm_family = AF_UNSPEC,
              .tcm_ifindex = ifIndex,
              .tcm_handle = TC_H_UNSPEC,
              .tcm_parent = TC_H_MAKE(TC_H_CLSACT, ingress ? TC_H_MIN_INGRESS
                                                           : TC_H_MIN_EGRESS),
              .tcm_info =
                  static_cast<__u32>((static_cast<uint16_t>(prio) << 16) |
                                     htons(static_cast<uint16_t>(proto))),
          },
      .kind =
          {
              .attr =
                  {
                      .nla_len = sizeof(req.kind),
                      .nla_type = TCA_KIND,
                  },
              .str = CLS_BPF_KIND_NAME,
          },
      .options =
          {
              .attr =
                  {
                      .nla_len = sizeof(req.options),
                      .nla_type = NLA_F_NESTED | TCA_OPTIONS,
                  },
              .fd =
                  {
                      .attr =
                          {
                              .nla_len = sizeof(req.options.fd),
                              .nla_type = TCA_BPF_FD,
                          },
                      .u32 = static_cast<__u32>(bpfFd),
                  },
              .name =
                  {
                      .attr =
                          {
                              .nla_len = sizeof(req.options.name),
                              .nla_type = TCA_BPF_NAME,
                          },
                      // Visible via 'tc filter show', but
                      // is overwritten by strncpy below
                      .str = "placeholder",
                  },
              .flags =
                  {
                      .attr =
                          {
                              .nla_len = sizeof(req.options.flags),
                              .nla_type = TCA_BPF_FLAGS,
                          },
                      .u32 = TCA_BPF_FLAG_ACT_DIRECT,
                  },
          },
  };

  snprintf(req.options.name.str, sizeof(req.options.name.str), "%s:[*fsobj]",
           basename(bpfProgPath));

  int error = sendAndProcessNetlinkResponse(&req, sizeof(req));
  return error;
}

// tc filter add dev .. ingress prio .. protocol .. matchall \
//     action police rate .. burst .. conform-exceed pipe/continue \
//     action bpf object-pinned .. \
//     drop
//
// TODO: tc-police does not do ECN marking, so in the future, we should consider
// adding a second tc-police filter at a lower priority that rate limits traffic
// at something like 0.8 times the global rate limit and ecn marks exceeding
// packets inside a bpf program (but does not drop them).
int tcAddIngressPoliceFilter(int ifIndex, uint16_t prio, uint16_t proto,
                             unsigned rateInBytesPerSec,
                             const char *bpfProgPath) {
  // TODO: this value needs to be validated.
  // TCP IW10 (initial congestion window) means servers will send 10 mtus worth
  // of data on initial connect.
  // If nic is LRO capable it could aggregate up to 64KiB, so again probably a
  // bad idea to set burst below that, because ingress packets could get
  // aggregated to 64KiB at the nic.
  // I don't know, but I wonder whether we shouldn't just do 128KiB and not do
  // any math.
  static constexpr unsigned BURST_SIZE_IN_BYTES = 128 * 1024; // 128KiB
  IngressPoliceFilterBuilder filter(ifIndex, prio, proto, rateInBytesPerSec,
                                    BURST_SIZE_IN_BYTES, bpfProgPath);
  const int error = filter.build();
  if (error) {
    return error;
  }
  return sendAndProcessNetlinkResponse(filter.getRequest(),
                                       filter.getRequestSize());
}

// tc filter del dev .. in/egress prio .. protocol ..
int tcDeleteFilter(int ifIndex, bool ingress, uint16_t prio, uint16_t proto) {
  const struct {
    nlmsghdr n;
    tcmsg t;
  } req = {
      .n =
          {
              .nlmsg_len = sizeof(req),
              .nlmsg_type = RTM_DELTFILTER,
              .nlmsg_flags = NETLINK_REQUEST_FLAGS,
          },
      .t =
          {
              .tcm_family = AF_UNSPEC,
              .tcm_ifindex = ifIndex,
              .tcm_handle = TC_H_UNSPEC,
              .tcm_parent = TC_H_MAKE(TC_H_CLSACT, ingress ? TC_H_MIN_INGRESS
                                                           : TC_H_MIN_EGRESS),
              .tcm_info =
                  static_cast<__u32>((static_cast<uint16_t>(prio) << 16) |
                                     htons(static_cast<uint16_t>(proto))),
          },
  };

  return sendAndProcessNetlinkResponse(&req, sizeof(req));
}

} // namespace android
