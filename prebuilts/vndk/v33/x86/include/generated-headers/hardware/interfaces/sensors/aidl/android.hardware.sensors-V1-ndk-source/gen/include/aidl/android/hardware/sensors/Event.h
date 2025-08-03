#pragma once

#include <array>
#include <cassert>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <variant>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/sensors/AdditionalInfo.h>
#include <aidl/android/hardware/sensors/DynamicSensorInfo.h>
#include <aidl/android/hardware/sensors/Event.h>
#include <aidl/android/hardware/sensors/SensorStatus.h>
#include <aidl/android/hardware/sensors/SensorType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

#ifndef __BIONIC__
#define __assert2(a,b,c,d) ((void)0)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class Event {
public:
  typedef std::true_type fixed_size;
  static const char* descriptor;

  class EventPayload {
  public:
    typedef std::true_type fixed_size;
    static const char* descriptor;

    class Vec4 {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float x __attribute__((aligned (4))) = 0.000000f;
      float y __attribute__((aligned (4))) = 0.000000f;
      float z __attribute__((aligned (4))) = 0.000000f;
      float w __attribute__((aligned (4))) = 0.000000f;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Vec4& rhs) const {
        return std::tie(x, y, z, w) != std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }
      inline bool operator<(const Vec4& rhs) const {
        return std::tie(x, y, z, w) < std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }
      inline bool operator<=(const Vec4& rhs) const {
        return std::tie(x, y, z, w) <= std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }
      inline bool operator==(const Vec4& rhs) const {
        return std::tie(x, y, z, w) == std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }
      inline bool operator>(const Vec4& rhs) const {
        return std::tie(x, y, z, w) > std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }
      inline bool operator>=(const Vec4& rhs) const {
        return std::tie(x, y, z, w) >= std::tie(rhs.x, rhs.y, rhs.z, rhs.w);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Vec4{";
        os << "x: " << ::android::internal::ToString(x);
        os << ", y: " << ::android::internal::ToString(y);
        os << ", z: " << ::android::internal::ToString(z);
        os << ", w: " << ::android::internal::ToString(w);
        os << "}";
        return os.str();
      }
    };
    class Vec3 {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float x __attribute__((aligned (4))) = 0.000000f;
      float y __attribute__((aligned (4))) = 0.000000f;
      float z __attribute__((aligned (4))) = 0.000000f;
      ::aidl::android::hardware::sensors::SensorStatus status __attribute__((aligned (1))) = ::aidl::android::hardware::sensors::SensorStatus(0);

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Vec3& rhs) const {
        return std::tie(x, y, z, status) != std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }
      inline bool operator<(const Vec3& rhs) const {
        return std::tie(x, y, z, status) < std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }
      inline bool operator<=(const Vec3& rhs) const {
        return std::tie(x, y, z, status) <= std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }
      inline bool operator==(const Vec3& rhs) const {
        return std::tie(x, y, z, status) == std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }
      inline bool operator>(const Vec3& rhs) const {
        return std::tie(x, y, z, status) > std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }
      inline bool operator>=(const Vec3& rhs) const {
        return std::tie(x, y, z, status) >= std::tie(rhs.x, rhs.y, rhs.z, rhs.status);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Vec3{";
        os << "x: " << ::android::internal::ToString(x);
        os << ", y: " << ::android::internal::ToString(y);
        os << ", z: " << ::android::internal::ToString(z);
        os << ", status: " << ::android::internal::ToString(status);
        os << "}";
        return os.str();
      }
    };
    class Uncal {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float x __attribute__((aligned (4))) = 0.000000f;
      float y __attribute__((aligned (4))) = 0.000000f;
      float z __attribute__((aligned (4))) = 0.000000f;
      float xBias __attribute__((aligned (4))) = 0.000000f;
      float yBias __attribute__((aligned (4))) = 0.000000f;
      float zBias __attribute__((aligned (4))) = 0.000000f;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) != std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }
      inline bool operator<(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) < std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }
      inline bool operator<=(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) <= std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }
      inline bool operator==(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) == std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }
      inline bool operator>(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) > std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }
      inline bool operator>=(const Uncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias) >= std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Uncal{";
        os << "x: " << ::android::internal::ToString(x);
        os << ", y: " << ::android::internal::ToString(y);
        os << ", z: " << ::android::internal::ToString(z);
        os << ", xBias: " << ::android::internal::ToString(xBias);
        os << ", yBias: " << ::android::internal::ToString(yBias);
        os << ", zBias: " << ::android::internal::ToString(zBias);
        os << "}";
        return os.str();
      }
    };
    class HeadTracker {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float rx __attribute__((aligned (4))) = 0.000000f;
      float ry __attribute__((aligned (4))) = 0.000000f;
      float rz __attribute__((aligned (4))) = 0.000000f;
      float vx __attribute__((aligned (4))) = 0.000000f;
      float vy __attribute__((aligned (4))) = 0.000000f;
      float vz __attribute__((aligned (4))) = 0.000000f;
      int32_t discontinuityCount __attribute__((aligned (4))) = 0;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) != std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }
      inline bool operator<(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) < std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }
      inline bool operator<=(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) <= std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }
      inline bool operator==(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) == std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }
      inline bool operator>(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) > std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }
      inline bool operator>=(const HeadTracker& rhs) const {
        return std::tie(rx, ry, rz, vx, vy, vz, discontinuityCount) >= std::tie(rhs.rx, rhs.ry, rhs.rz, rhs.vx, rhs.vy, rhs.vz, rhs.discontinuityCount);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "HeadTracker{";
        os << "rx: " << ::android::internal::ToString(rx);
        os << ", ry: " << ::android::internal::ToString(ry);
        os << ", rz: " << ::android::internal::ToString(rz);
        os << ", vx: " << ::android::internal::ToString(vx);
        os << ", vy: " << ::android::internal::ToString(vy);
        os << ", vz: " << ::android::internal::ToString(vz);
        os << ", discontinuityCount: " << ::android::internal::ToString(discontinuityCount);
        os << "}";
        return os.str();
      }
    };
    class LimitedAxesImu {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float x __attribute__((aligned (4))) = 0.000000f;
      float y __attribute__((aligned (4))) = 0.000000f;
      float z __attribute__((aligned (4))) = 0.000000f;
      float xSupported __attribute__((aligned (4))) = 0.000000f;
      float ySupported __attribute__((aligned (4))) = 0.000000f;
      float zSupported __attribute__((aligned (4))) = 0.000000f;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) != std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator<(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) < std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator<=(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) <= std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator==(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) == std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator>(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) > std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator>=(const LimitedAxesImu& rhs) const {
        return std::tie(x, y, z, xSupported, ySupported, zSupported) >= std::tie(rhs.x, rhs.y, rhs.z, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "LimitedAxesImu{";
        os << "x: " << ::android::internal::ToString(x);
        os << ", y: " << ::android::internal::ToString(y);
        os << ", z: " << ::android::internal::ToString(z);
        os << ", xSupported: " << ::android::internal::ToString(xSupported);
        os << ", ySupported: " << ::android::internal::ToString(ySupported);
        os << ", zSupported: " << ::android::internal::ToString(zSupported);
        os << "}";
        return os.str();
      }
    };
    class LimitedAxesImuUncal {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float x __attribute__((aligned (4))) = 0.000000f;
      float y __attribute__((aligned (4))) = 0.000000f;
      float z __attribute__((aligned (4))) = 0.000000f;
      float xBias __attribute__((aligned (4))) = 0.000000f;
      float yBias __attribute__((aligned (4))) = 0.000000f;
      float zBias __attribute__((aligned (4))) = 0.000000f;
      float xSupported __attribute__((aligned (4))) = 0.000000f;
      float ySupported __attribute__((aligned (4))) = 0.000000f;
      float zSupported __attribute__((aligned (4))) = 0.000000f;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) != std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator<(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) < std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator<=(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) <= std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator==(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) == std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator>(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) > std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }
      inline bool operator>=(const LimitedAxesImuUncal& rhs) const {
        return std::tie(x, y, z, xBias, yBias, zBias, xSupported, ySupported, zSupported) >= std::tie(rhs.x, rhs.y, rhs.z, rhs.xBias, rhs.yBias, rhs.zBias, rhs.xSupported, rhs.ySupported, rhs.zSupported);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "LimitedAxesImuUncal{";
        os << "x: " << ::android::internal::ToString(x);
        os << ", y: " << ::android::internal::ToString(y);
        os << ", z: " << ::android::internal::ToString(z);
        os << ", xBias: " << ::android::internal::ToString(xBias);
        os << ", yBias: " << ::android::internal::ToString(yBias);
        os << ", zBias: " << ::android::internal::ToString(zBias);
        os << ", xSupported: " << ::android::internal::ToString(xSupported);
        os << ", ySupported: " << ::android::internal::ToString(ySupported);
        os << ", zSupported: " << ::android::internal::ToString(zSupported);
        os << "}";
        return os.str();
      }
    };
    class HeartRate {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float bpm __attribute__((aligned (4))) = 0.000000f;
      ::aidl::android::hardware::sensors::SensorStatus status __attribute__((aligned (1))) = ::aidl::android::hardware::sensors::SensorStatus(0);

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const HeartRate& rhs) const {
        return std::tie(bpm, status) != std::tie(rhs.bpm, rhs.status);
      }
      inline bool operator<(const HeartRate& rhs) const {
        return std::tie(bpm, status) < std::tie(rhs.bpm, rhs.status);
      }
      inline bool operator<=(const HeartRate& rhs) const {
        return std::tie(bpm, status) <= std::tie(rhs.bpm, rhs.status);
      }
      inline bool operator==(const HeartRate& rhs) const {
        return std::tie(bpm, status) == std::tie(rhs.bpm, rhs.status);
      }
      inline bool operator>(const HeartRate& rhs) const {
        return std::tie(bpm, status) > std::tie(rhs.bpm, rhs.status);
      }
      inline bool operator>=(const HeartRate& rhs) const {
        return std::tie(bpm, status) >= std::tie(rhs.bpm, rhs.status);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "HeartRate{";
        os << "bpm: " << ::android::internal::ToString(bpm);
        os << ", status: " << ::android::internal::ToString(status);
        os << "}";
        return os.str();
      }
    };
    class Heading {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      float heading __attribute__((aligned (4))) = 0.000000f;
      float accuracy __attribute__((aligned (4))) = 0.000000f;

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Heading& rhs) const {
        return std::tie(heading, accuracy) != std::tie(rhs.heading, rhs.accuracy);
      }
      inline bool operator<(const Heading& rhs) const {
        return std::tie(heading, accuracy) < std::tie(rhs.heading, rhs.accuracy);
      }
      inline bool operator<=(const Heading& rhs) const {
        return std::tie(heading, accuracy) <= std::tie(rhs.heading, rhs.accuracy);
      }
      inline bool operator==(const Heading& rhs) const {
        return std::tie(heading, accuracy) == std::tie(rhs.heading, rhs.accuracy);
      }
      inline bool operator>(const Heading& rhs) const {
        return std::tie(heading, accuracy) > std::tie(rhs.heading, rhs.accuracy);
      }
      inline bool operator>=(const Heading& rhs) const {
        return std::tie(heading, accuracy) >= std::tie(rhs.heading, rhs.accuracy);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Heading{";
        os << "heading: " << ::android::internal::ToString(heading);
        os << ", accuracy: " << ::android::internal::ToString(accuracy);
        os << "}";
        return os.str();
      }
    };
    class MetaData {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      enum class MetaDataEventType : int32_t {
        META_DATA_FLUSH_COMPLETE = 1,
      };

      ::aidl::android::hardware::sensors::Event::EventPayload::MetaData::MetaDataEventType what __attribute__((aligned (4))) = ::aidl::android::hardware::sensors::Event::EventPayload::MetaData::MetaDataEventType(0);

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const MetaData& rhs) const {
        return std::tie(what) != std::tie(rhs.what);
      }
      inline bool operator<(const MetaData& rhs) const {
        return std::tie(what) < std::tie(rhs.what);
      }
      inline bool operator<=(const MetaData& rhs) const {
        return std::tie(what) <= std::tie(rhs.what);
      }
      inline bool operator==(const MetaData& rhs) const {
        return std::tie(what) == std::tie(rhs.what);
      }
      inline bool operator>(const MetaData& rhs) const {
        return std::tie(what) > std::tie(rhs.what);
      }
      inline bool operator>=(const MetaData& rhs) const {
        return std::tie(what) >= std::tie(rhs.what);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "MetaData{";
        os << "what: " << ::android::internal::ToString(what);
        os << "}";
        return os.str();
      }
    };
    class Pose6Dof {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      std::array<float, 15> values __attribute__((aligned (4))) = {{}};

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Pose6Dof& rhs) const {
        return std::tie(values) != std::tie(rhs.values);
      }
      inline bool operator<(const Pose6Dof& rhs) const {
        return std::tie(values) < std::tie(rhs.values);
      }
      inline bool operator<=(const Pose6Dof& rhs) const {
        return std::tie(values) <= std::tie(rhs.values);
      }
      inline bool operator==(const Pose6Dof& rhs) const {
        return std::tie(values) == std::tie(rhs.values);
      }
      inline bool operator>(const Pose6Dof& rhs) const {
        return std::tie(values) > std::tie(rhs.values);
      }
      inline bool operator>=(const Pose6Dof& rhs) const {
        return std::tie(values) >= std::tie(rhs.values);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Pose6Dof{";
        os << "values: " << ::android::internal::ToString(values);
        os << "}";
        return os.str();
      }
    };
    class Data {
    public:
      typedef std::true_type fixed_size;
      static const char* descriptor;

      std::array<float, 16> values __attribute__((aligned (4))) = {{}};

      binder_status_t readFromParcel(const AParcel* parcel);
      binder_status_t writeToParcel(AParcel* parcel) const;

      inline bool operator!=(const Data& rhs) const {
        return std::tie(values) != std::tie(rhs.values);
      }
      inline bool operator<(const Data& rhs) const {
        return std::tie(values) < std::tie(rhs.values);
      }
      inline bool operator<=(const Data& rhs) const {
        return std::tie(values) <= std::tie(rhs.values);
      }
      inline bool operator==(const Data& rhs) const {
        return std::tie(values) == std::tie(rhs.values);
      }
      inline bool operator>(const Data& rhs) const {
        return std::tie(values) > std::tie(rhs.values);
      }
      inline bool operator>=(const Data& rhs) const {
        return std::tie(values) >= std::tie(rhs.values);
      }

      static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
      inline std::string toString() const {
        std::ostringstream os;
        os << "Data{";
        os << "values: " << ::android::internal::ToString(values);
        os << "}";
        return os.str();
      }
    };
    enum class Tag : int8_t {
      vec3 = 0,
      vec4 = 1,
      uncal = 2,
      meta = 3,
      scalar = 4,
      stepCount = 5,
      heartRate = 6,
      pose6DOF = 7,
      dynamic = 8,
      additional = 9,
      data = 10,
      headTracker = 11,
      limitedAxesImu = 12,
      limitedAxesImuUncal = 13,
      heading = 14,
    };

    // Expose tag symbols for legacy code
    static const inline Tag vec3 = Tag::vec3;
    static const inline Tag vec4 = Tag::vec4;
    static const inline Tag uncal = Tag::uncal;
    static const inline Tag meta = Tag::meta;
    static const inline Tag scalar = Tag::scalar;
    static const inline Tag stepCount = Tag::stepCount;
    static const inline Tag heartRate = Tag::heartRate;
    static const inline Tag pose6DOF = Tag::pose6DOF;
    static const inline Tag dynamic = Tag::dynamic;
    static const inline Tag additional = Tag::additional;
    static const inline Tag data = Tag::data;
    static const inline Tag headTracker = Tag::headTracker;
    static const inline Tag limitedAxesImu = Tag::limitedAxesImu;
    static const inline Tag limitedAxesImuUncal = Tag::limitedAxesImuUncal;
    static const inline Tag heading = Tag::heading;

    template <Tag _Tag>
    using _at = typename std::tuple_element<static_cast<size_t>(_Tag), std::tuple<::aidl::android::hardware::sensors::Event::EventPayload::Vec3, ::aidl::android::hardware::sensors::Event::EventPayload::Vec4, ::aidl::android::hardware::sensors::Event::EventPayload::Uncal, ::aidl::android::hardware::sensors::Event::EventPayload::MetaData, float, int64_t, ::aidl::android::hardware::sensors::Event::EventPayload::HeartRate, ::aidl::android::hardware::sensors::Event::EventPayload::Pose6Dof, ::aidl::android::hardware::sensors::DynamicSensorInfo, ::aidl::android::hardware::sensors::AdditionalInfo, ::aidl::android::hardware::sensors::Event::EventPayload::Data, ::aidl::android::hardware::sensors::Event::EventPayload::HeadTracker, ::aidl::android::hardware::sensors::Event::EventPayload::LimitedAxesImu, ::aidl::android::hardware::sensors::Event::EventPayload::LimitedAxesImuUncal, ::aidl::android::hardware::sensors::Event::EventPayload::Heading>>::type;
    template <Tag _Tag, typename _Type>
    static EventPayload make(_Type&& _arg) {
      EventPayload _inst;
      _inst.set<_Tag>(std::forward<_Type>(_arg));
      return _inst;
    }
    constexpr Tag getTag() const {
      return _tag;
    }
    template <Tag _Tag>
    const _at<_Tag>& get() const {
      if (_Tag != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return *(_at<_Tag>*)(&_value);
    }
    template <Tag _Tag>
    _at<_Tag>& get() {
      if (_Tag != _tag) { __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "bad access: a wrong tag"); }
      return *(_at<_Tag>*)(&_value);
    }
    template <Tag _Tag, typename _Type>
    void set(_Type&& _arg) {
      _tag = _Tag;
      get<_Tag>() = std::forward<_Type>(_arg);
    }
    binder_status_t readFromParcel(const AParcel* _parcel);
    binder_status_t writeToParcel(AParcel* _parcel) const;

    static int _cmp(const EventPayload& _lhs, const EventPayload& _rhs) {
      return _cmp_value(_lhs.getTag(), _rhs.getTag()) || _cmp_value_at<heading>(_lhs, _rhs);
    }
    template <Tag _Tag>
    static int _cmp_value_at(const EventPayload& _lhs, const EventPayload& _rhs) {
      if constexpr (_Tag == vec3) {
        return _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>());
      } else {
        return (_lhs.getTag() == _Tag)
          ? _cmp_value(_lhs.get<_Tag>(), _rhs.get<_Tag>())
          : _cmp_value_at<static_cast<Tag>(static_cast<size_t>(_Tag)-1)>(_lhs, _rhs);
      }
    }
    template <typename _Type>
    static int _cmp_value(const _Type& _lhs, const _Type& _rhs) {
      return (_lhs == _rhs) ? 0 : (_lhs < _rhs) ? -1 : 1;
    }
    inline bool operator!=(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) != 0;
    }
    inline bool operator<(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) < 0;
    }
    inline bool operator<=(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) <= 0;
    }
    inline bool operator==(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) == 0;
    }
    inline bool operator>(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) > 0;
    }
    inline bool operator>=(const EventPayload&_rhs) const {
      return _cmp(*this, _rhs) >= 0;
    }
    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "EventPayload{";
      switch (getTag()) {
      case vec3: os << "vec3: " << ::android::internal::ToString(get<vec3>()); break;
      case vec4: os << "vec4: " << ::android::internal::ToString(get<vec4>()); break;
      case uncal: os << "uncal: " << ::android::internal::ToString(get<uncal>()); break;
      case meta: os << "meta: " << ::android::internal::ToString(get<meta>()); break;
      case scalar: os << "scalar: " << ::android::internal::ToString(get<scalar>()); break;
      case stepCount: os << "stepCount: " << ::android::internal::ToString(get<stepCount>()); break;
      case heartRate: os << "heartRate: " << ::android::internal::ToString(get<heartRate>()); break;
      case pose6DOF: os << "pose6DOF: " << ::android::internal::ToString(get<pose6DOF>()); break;
      case dynamic: os << "dynamic: " << ::android::internal::ToString(get<dynamic>()); break;
      case additional: os << "additional: " << ::android::internal::ToString(get<additional>()); break;
      case data: os << "data: " << ::android::internal::ToString(get<data>()); break;
      case headTracker: os << "headTracker: " << ::android::internal::ToString(get<headTracker>()); break;
      case limitedAxesImu: os << "limitedAxesImu: " << ::android::internal::ToString(get<limitedAxesImu>()); break;
      case limitedAxesImuUncal: os << "limitedAxesImuUncal: " << ::android::internal::ToString(get<limitedAxesImuUncal>()); break;
      case heading: os << "heading: " << ::android::internal::ToString(get<heading>()); break;
      }
      os << "}";
      return os.str();
    }
  private:
    Tag _tag = vec3;
    union _value_t {
      _value_t() {}
      ~_value_t() {}
      ::aidl::android::hardware::sensors::Event::EventPayload::Vec3 vec3 = ::aidl::android::hardware::sensors::Event::EventPayload::Vec3();
      ::aidl::android::hardware::sensors::Event::EventPayload::Vec4 vec4;
      ::aidl::android::hardware::sensors::Event::EventPayload::Uncal uncal;
      ::aidl::android::hardware::sensors::Event::EventPayload::MetaData meta;
      float scalar __attribute__((aligned (4)));
      int64_t stepCount __attribute__((aligned (8)));
      ::aidl::android::hardware::sensors::Event::EventPayload::HeartRate heartRate;
      ::aidl::android::hardware::sensors::Event::EventPayload::Pose6Dof pose6DOF;
      ::aidl::android::hardware::sensors::DynamicSensorInfo dynamic;
      ::aidl::android::hardware::sensors::AdditionalInfo additional;
      ::aidl::android::hardware::sensors::Event::EventPayload::Data data;
      ::aidl::android::hardware::sensors::Event::EventPayload::HeadTracker headTracker;
      ::aidl::android::hardware::sensors::Event::EventPayload::LimitedAxesImu limitedAxesImu;
      ::aidl::android::hardware::sensors::Event::EventPayload::LimitedAxesImuUncal limitedAxesImuUncal;
      ::aidl::android::hardware::sensors::Event::EventPayload::Heading heading;
    } _value;
  };
  int64_t timestamp __attribute__((aligned (8))) = 0L;
  int32_t sensorHandle __attribute__((aligned (4))) = 0;
  ::aidl::android::hardware::sensors::SensorType sensorType __attribute__((aligned (4))) = ::aidl::android::hardware::sensors::SensorType(0);
  ::aidl::android::hardware::sensors::Event::EventPayload payload;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) != std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }
  inline bool operator<(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) < std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }
  inline bool operator<=(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) <= std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }
  inline bool operator==(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) == std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }
  inline bool operator>(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) > std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }
  inline bool operator>=(const Event& rhs) const {
    return std::tie(timestamp, sensorHandle, sensorType, payload) >= std::tie(rhs.timestamp, rhs.sensorHandle, rhs.sensorType, rhs.payload);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Event{";
    os << "timestamp: " << ::android::internal::ToString(timestamp);
    os << ", sensorHandle: " << ::android::internal::ToString(sensorHandle);
    os << ", sensorType: " << ::android::internal::ToString(sensorType);
    os << ", payload: " << ::android::internal::ToString(payload);
    os << "}";
    return os.str();
  }
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(Event::EventPayload::MetaData::MetaDataEventType val) {
  switch(val) {
  case Event::EventPayload::MetaData::MetaDataEventType::META_DATA_FLUSH_COMPLETE:
    return "META_DATA_FLUSH_COMPLETE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::Event::EventPayload::MetaData::MetaDataEventType, 1> enum_values<aidl::android::hardware::sensors::Event::EventPayload::MetaData::MetaDataEventType> = {
  aidl::android::hardware::sensors::Event::EventPayload::MetaData::MetaDataEventType::META_DATA_FLUSH_COMPLETE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
[[nodiscard]] static inline std::string toString(Event::EventPayload::Tag val) {
  switch(val) {
  case Event::EventPayload::Tag::vec3:
    return "vec3";
  case Event::EventPayload::Tag::vec4:
    return "vec4";
  case Event::EventPayload::Tag::uncal:
    return "uncal";
  case Event::EventPayload::Tag::meta:
    return "meta";
  case Event::EventPayload::Tag::scalar:
    return "scalar";
  case Event::EventPayload::Tag::stepCount:
    return "stepCount";
  case Event::EventPayload::Tag::heartRate:
    return "heartRate";
  case Event::EventPayload::Tag::pose6DOF:
    return "pose6DOF";
  case Event::EventPayload::Tag::dynamic:
    return "dynamic";
  case Event::EventPayload::Tag::additional:
    return "additional";
  case Event::EventPayload::Tag::data:
    return "data";
  case Event::EventPayload::Tag::headTracker:
    return "headTracker";
  case Event::EventPayload::Tag::limitedAxesImu:
    return "limitedAxesImu";
  case Event::EventPayload::Tag::limitedAxesImuUncal:
    return "limitedAxesImuUncal";
  case Event::EventPayload::Tag::heading:
    return "heading";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::sensors::Event::EventPayload::Tag, 15> enum_values<aidl::android::hardware::sensors::Event::EventPayload::Tag> = {
  aidl::android::hardware::sensors::Event::EventPayload::Tag::vec3,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::vec4,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::uncal,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::meta,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::scalar,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::stepCount,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::heartRate,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::pose6DOF,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::dynamic,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::additional,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::data,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::headTracker,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::limitedAxesImu,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::limitedAxesImuUncal,
  aidl::android::hardware::sensors::Event::EventPayload::Tag::heading,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
