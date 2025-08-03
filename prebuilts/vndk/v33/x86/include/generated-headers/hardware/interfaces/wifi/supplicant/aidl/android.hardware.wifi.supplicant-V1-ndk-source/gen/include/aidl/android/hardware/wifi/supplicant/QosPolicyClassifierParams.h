#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/wifi/supplicant/IpVersion.h>
#include <aidl/android/hardware/wifi/supplicant/PortRange.h>
#include <aidl/android/hardware/wifi/supplicant/ProtocolNextHeader.h>
#include <aidl/android/hardware/wifi/supplicant/QosPolicyClassifierParamsMask.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class QosPolicyClassifierParams {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::wifi::supplicant::IpVersion ipVersion = ::aidl::android::hardware::wifi::supplicant::IpVersion(0);
  ::aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask classifierParamMask = ::aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask(0);
  std::vector<uint8_t> srcIp;
  std::vector<uint8_t> dstIp;
  int32_t srcPort = 0;
  ::aidl::android::hardware::wifi::supplicant::PortRange dstPortRange;
  ::aidl::android::hardware::wifi::supplicant::ProtocolNextHeader protocolNextHdr = ::aidl::android::hardware::wifi::supplicant::ProtocolNextHeader(0);
  std::vector<uint8_t> flowLabelIpv6;
  std::string domainName;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) != std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }
  inline bool operator<(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) < std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }
  inline bool operator<=(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) <= std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }
  inline bool operator==(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) == std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }
  inline bool operator>(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) > std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }
  inline bool operator>=(const QosPolicyClassifierParams& rhs) const {
    return std::tie(ipVersion, classifierParamMask, srcIp, dstIp, srcPort, dstPortRange, protocolNextHdr, flowLabelIpv6, domainName) >= std::tie(rhs.ipVersion, rhs.classifierParamMask, rhs.srcIp, rhs.dstIp, rhs.srcPort, rhs.dstPortRange, rhs.protocolNextHdr, rhs.flowLabelIpv6, rhs.domainName);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "QosPolicyClassifierParams{";
    os << "ipVersion: " << ::android::internal::ToString(ipVersion);
    os << ", classifierParamMask: " << ::android::internal::ToString(classifierParamMask);
    os << ", srcIp: " << ::android::internal::ToString(srcIp);
    os << ", dstIp: " << ::android::internal::ToString(dstIp);
    os << ", srcPort: " << ::android::internal::ToString(srcPort);
    os << ", dstPortRange: " << ::android::internal::ToString(dstPortRange);
    os << ", protocolNextHdr: " << ::android::internal::ToString(protocolNextHdr);
    os << ", flowLabelIpv6: " << ::android::internal::ToString(flowLabelIpv6);
    os << ", domainName: " << ::android::internal::ToString(domainName);
    os << "}";
    return os.str();
  }
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
