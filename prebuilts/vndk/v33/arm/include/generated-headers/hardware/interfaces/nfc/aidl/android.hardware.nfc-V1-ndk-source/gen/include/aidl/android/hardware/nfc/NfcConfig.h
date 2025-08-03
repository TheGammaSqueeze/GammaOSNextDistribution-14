#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/nfc/PresenceCheckAlgorithm.h>
#include <aidl/android/hardware/nfc/ProtocolDiscoveryConfig.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class NfcConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  bool nfaPollBailOutMode = false;
  ::aidl::android::hardware::nfc::PresenceCheckAlgorithm presenceCheckAlgorithm = ::aidl::android::hardware::nfc::PresenceCheckAlgorithm(0);
  ::aidl::android::hardware::nfc::ProtocolDiscoveryConfig nfaProprietaryCfg;
  int8_t defaultOffHostRoute = 0;
  int8_t defaultOffHostRouteFelica = 0;
  int8_t defaultSystemCodeRoute = 0;
  int8_t defaultSystemCodePowerState = 0;
  int8_t defaultRoute = 0;
  int8_t offHostESEPipeId = 0;
  int8_t offHostSIMPipeId = 0;
  int32_t maxIsoDepTransceiveLength = 0;
  std::vector<uint8_t> hostAllowlist;
  std::vector<uint8_t> offHostRouteUicc;
  std::vector<uint8_t> offHostRouteEse;
  int8_t defaultIsoDepRoute = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) != std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }
  inline bool operator<(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) < std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }
  inline bool operator<=(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) <= std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }
  inline bool operator==(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) == std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }
  inline bool operator>(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) > std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }
  inline bool operator>=(const NfcConfig& rhs) const {
    return std::tie(nfaPollBailOutMode, presenceCheckAlgorithm, nfaProprietaryCfg, defaultOffHostRoute, defaultOffHostRouteFelica, defaultSystemCodeRoute, defaultSystemCodePowerState, defaultRoute, offHostESEPipeId, offHostSIMPipeId, maxIsoDepTransceiveLength, hostAllowlist, offHostRouteUicc, offHostRouteEse, defaultIsoDepRoute) >= std::tie(rhs.nfaPollBailOutMode, rhs.presenceCheckAlgorithm, rhs.nfaProprietaryCfg, rhs.defaultOffHostRoute, rhs.defaultOffHostRouteFelica, rhs.defaultSystemCodeRoute, rhs.defaultSystemCodePowerState, rhs.defaultRoute, rhs.offHostESEPipeId, rhs.offHostSIMPipeId, rhs.maxIsoDepTransceiveLength, rhs.hostAllowlist, rhs.offHostRouteUicc, rhs.offHostRouteEse, rhs.defaultIsoDepRoute);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NfcConfig{";
    os << "nfaPollBailOutMode: " << ::android::internal::ToString(nfaPollBailOutMode);
    os << ", presenceCheckAlgorithm: " << ::android::internal::ToString(presenceCheckAlgorithm);
    os << ", nfaProprietaryCfg: " << ::android::internal::ToString(nfaProprietaryCfg);
    os << ", defaultOffHostRoute: " << ::android::internal::ToString(defaultOffHostRoute);
    os << ", defaultOffHostRouteFelica: " << ::android::internal::ToString(defaultOffHostRouteFelica);
    os << ", defaultSystemCodeRoute: " << ::android::internal::ToString(defaultSystemCodeRoute);
    os << ", defaultSystemCodePowerState: " << ::android::internal::ToString(defaultSystemCodePowerState);
    os << ", defaultRoute: " << ::android::internal::ToString(defaultRoute);
    os << ", offHostESEPipeId: " << ::android::internal::ToString(offHostESEPipeId);
    os << ", offHostSIMPipeId: " << ::android::internal::ToString(offHostSIMPipeId);
    os << ", maxIsoDepTransceiveLength: " << ::android::internal::ToString(maxIsoDepTransceiveLength);
    os << ", hostAllowlist: " << ::android::internal::ToString(hostAllowlist);
    os << ", offHostRouteUicc: " << ::android::internal::ToString(offHostRouteUicc);
    os << ", offHostRouteEse: " << ::android::internal::ToString(offHostRouteEse);
    os << ", defaultIsoDepRoute: " << ::android::internal::ToString(defaultIsoDepRoute);
    os << "}";
    return os.str();
  }
};
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
