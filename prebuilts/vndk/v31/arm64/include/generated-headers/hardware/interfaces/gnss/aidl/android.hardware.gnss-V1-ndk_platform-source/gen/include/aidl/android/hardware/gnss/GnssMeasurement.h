#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/gnss/CorrelationVector.h>
#include <aidl/android/hardware/gnss/GnssMultipathIndicator.h>
#include <aidl/android/hardware/gnss/GnssSignalType.h>
#include <aidl/android/hardware/gnss/SatellitePvt.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class GnssMeasurement {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t flags = 0;
  int32_t svid = 0;
  ::aidl::android::hardware::gnss::GnssSignalType signalType;
  double timeOffsetNs = 0.000000;
  int32_t state = 0;
  int64_t receivedSvTimeInNs = 0L;
  int64_t receivedSvTimeUncertaintyInNs = 0L;
  double antennaCN0DbHz = 0.000000;
  double basebandCN0DbHz = 0.000000;
  double pseudorangeRateMps = 0.000000;
  double pseudorangeRateUncertaintyMps = 0.000000;
  int32_t accumulatedDeltaRangeState = 0;
  double accumulatedDeltaRangeM = 0.000000;
  double accumulatedDeltaRangeUncertaintyM = 0.000000;
  int64_t carrierCycles = 0L;
  double carrierPhase = 0.000000;
  double carrierPhaseUncertainty = 0.000000;
  ::aidl::android::hardware::gnss::GnssMultipathIndicator multipathIndicator = ::aidl::android::hardware::gnss::GnssMultipathIndicator::UNKNOWN;
  double snrDb = 0.000000;
  double agcLevelDb = 0.000000;
  double fullInterSignalBiasNs = 0.000000;
  double fullInterSignalBiasUncertaintyNs = 0.000000;
  double satelliteInterSignalBiasNs = 0.000000;
  double satelliteInterSignalBiasUncertaintyNs = 0.000000;
  ::aidl::android::hardware::gnss::SatellitePvt satellitePvt;
  std::vector<::aidl::android::hardware::gnss::CorrelationVector> correlationVectors;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) != std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }
  inline bool operator<(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) < std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }
  inline bool operator<=(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) <= std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }
  inline bool operator==(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) == std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }
  inline bool operator>(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) > std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }
  inline bool operator>=(const GnssMeasurement& rhs) const {
    return std::tie(flags, svid, signalType, timeOffsetNs, state, receivedSvTimeInNs, receivedSvTimeUncertaintyInNs, antennaCN0DbHz, basebandCN0DbHz, pseudorangeRateMps, pseudorangeRateUncertaintyMps, accumulatedDeltaRangeState, accumulatedDeltaRangeM, accumulatedDeltaRangeUncertaintyM, carrierCycles, carrierPhase, carrierPhaseUncertainty, multipathIndicator, snrDb, agcLevelDb, fullInterSignalBiasNs, fullInterSignalBiasUncertaintyNs, satelliteInterSignalBiasNs, satelliteInterSignalBiasUncertaintyNs, satellitePvt, correlationVectors) >= std::tie(rhs.flags, rhs.svid, rhs.signalType, rhs.timeOffsetNs, rhs.state, rhs.receivedSvTimeInNs, rhs.receivedSvTimeUncertaintyInNs, rhs.antennaCN0DbHz, rhs.basebandCN0DbHz, rhs.pseudorangeRateMps, rhs.pseudorangeRateUncertaintyMps, rhs.accumulatedDeltaRangeState, rhs.accumulatedDeltaRangeM, rhs.accumulatedDeltaRangeUncertaintyM, rhs.carrierCycles, rhs.carrierPhase, rhs.carrierPhaseUncertainty, rhs.multipathIndicator, rhs.snrDb, rhs.agcLevelDb, rhs.fullInterSignalBiasNs, rhs.fullInterSignalBiasUncertaintyNs, rhs.satelliteInterSignalBiasNs, rhs.satelliteInterSignalBiasUncertaintyNs, rhs.satellitePvt, rhs.correlationVectors);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { HAS_SNR = 1 };
  enum : int32_t { HAS_CARRIER_FREQUENCY = 512 };
  enum : int32_t { HAS_CARRIER_CYCLES = 1024 };
  enum : int32_t { HAS_CARRIER_PHASE = 2048 };
  enum : int32_t { HAS_CARRIER_PHASE_UNCERTAINTY = 4096 };
  enum : int32_t { HAS_AUTOMATIC_GAIN_CONTROL = 8192 };
  enum : int32_t { HAS_FULL_ISB = 65536 };
  enum : int32_t { HAS_FULL_ISB_UNCERTAINTY = 131072 };
  enum : int32_t { HAS_SATELLITE_ISB = 262144 };
  enum : int32_t { HAS_SATELLITE_ISB_UNCERTAINTY = 524288 };
  enum : int32_t { HAS_SATELLITE_PVT = 1048576 };
  enum : int32_t { HAS_CORRELATION_VECTOR = 2097152 };
  enum : int32_t { STATE_UNKNOWN = 0 };
  enum : int32_t { STATE_CODE_LOCK = 1 };
  enum : int32_t { STATE_BIT_SYNC = 2 };
  enum : int32_t { STATE_SUBFRAME_SYNC = 4 };
  enum : int32_t { STATE_TOW_DECODED = 8 };
  enum : int32_t { STATE_MSEC_AMBIGUOUS = 16 };
  enum : int32_t { STATE_SYMBOL_SYNC = 32 };
  enum : int32_t { STATE_GLO_STRING_SYNC = 64 };
  enum : int32_t { STATE_GLO_TOD_DECODED = 128 };
  enum : int32_t { STATE_BDS_D2_BIT_SYNC = 256 };
  enum : int32_t { STATE_BDS_D2_SUBFRAME_SYNC = 512 };
  enum : int32_t { STATE_GAL_E1BC_CODE_LOCK = 1024 };
  enum : int32_t { STATE_GAL_E1C_2ND_CODE_LOCK = 2048 };
  enum : int32_t { STATE_GAL_E1B_PAGE_SYNC = 4096 };
  enum : int32_t { STATE_SBAS_SYNC = 8192 };
  enum : int32_t { STATE_TOW_KNOWN = 16384 };
  enum : int32_t { STATE_GLO_TOD_KNOWN = 32768 };
  enum : int32_t { STATE_2ND_CODE_LOCK = 65536 };
  enum : int32_t { ADR_STATE_UNKNOWN = 0 };
  enum : int32_t { ADR_STATE_VALID = 1 };
  enum : int32_t { ADR_STATE_RESET = 2 };
  enum : int32_t { ADR_STATE_CYCLE_SLIP = 4 };
  enum : int32_t { ADR_STATE_HALF_CYCLE_RESOLVED = 8 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "GnssMeasurement{";
    os << "flags: " << ::android::internal::ToString(flags);
    os << ", svid: " << ::android::internal::ToString(svid);
    os << ", signalType: " << ::android::internal::ToString(signalType);
    os << ", timeOffsetNs: " << ::android::internal::ToString(timeOffsetNs);
    os << ", state: " << ::android::internal::ToString(state);
    os << ", receivedSvTimeInNs: " << ::android::internal::ToString(receivedSvTimeInNs);
    os << ", receivedSvTimeUncertaintyInNs: " << ::android::internal::ToString(receivedSvTimeUncertaintyInNs);
    os << ", antennaCN0DbHz: " << ::android::internal::ToString(antennaCN0DbHz);
    os << ", basebandCN0DbHz: " << ::android::internal::ToString(basebandCN0DbHz);
    os << ", pseudorangeRateMps: " << ::android::internal::ToString(pseudorangeRateMps);
    os << ", pseudorangeRateUncertaintyMps: " << ::android::internal::ToString(pseudorangeRateUncertaintyMps);
    os << ", accumulatedDeltaRangeState: " << ::android::internal::ToString(accumulatedDeltaRangeState);
    os << ", accumulatedDeltaRangeM: " << ::android::internal::ToString(accumulatedDeltaRangeM);
    os << ", accumulatedDeltaRangeUncertaintyM: " << ::android::internal::ToString(accumulatedDeltaRangeUncertaintyM);
    os << ", carrierCycles: " << ::android::internal::ToString(carrierCycles);
    os << ", carrierPhase: " << ::android::internal::ToString(carrierPhase);
    os << ", carrierPhaseUncertainty: " << ::android::internal::ToString(carrierPhaseUncertainty);
    os << ", multipathIndicator: " << ::android::internal::ToString(multipathIndicator);
    os << ", snrDb: " << ::android::internal::ToString(snrDb);
    os << ", agcLevelDb: " << ::android::internal::ToString(agcLevelDb);
    os << ", fullInterSignalBiasNs: " << ::android::internal::ToString(fullInterSignalBiasNs);
    os << ", fullInterSignalBiasUncertaintyNs: " << ::android::internal::ToString(fullInterSignalBiasUncertaintyNs);
    os << ", satelliteInterSignalBiasNs: " << ::android::internal::ToString(satelliteInterSignalBiasNs);
    os << ", satelliteInterSignalBiasUncertaintyNs: " << ::android::internal::ToString(satelliteInterSignalBiasUncertaintyNs);
    os << ", satellitePvt: " << ::android::internal::ToString(satellitePvt);
    os << ", correlationVectors: " << ::android::internal::ToString(correlationVectors);
    os << "}";
    return os.str();
  }
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
