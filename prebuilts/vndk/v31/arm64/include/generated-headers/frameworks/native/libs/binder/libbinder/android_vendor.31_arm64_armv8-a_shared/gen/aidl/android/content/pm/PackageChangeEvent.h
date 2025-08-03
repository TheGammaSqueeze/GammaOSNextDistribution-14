#pragma once

#include <android/binder_to_string.h>
#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>
#include <utils/String16.h>

namespace android {

namespace content {

namespace pm {

class PackageChangeEvent : public ::android::Parcelable {
public:
  inline bool operator!=(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) != std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator<(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) < std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator<=(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) <= std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator==(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) == std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator>(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) > std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator>=(const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted) >= std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }

  ::std::string packageName;
  int64_t version = int64_t(0L);
  int64_t lastUpdateTimeMillis = int64_t(0L);
  bool newInstalled = bool(false);
  bool dataRemoved = bool(false);
  bool isDeleted = bool(false);
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const final;
  static const ::android::String16& getParcelableDescriptor() {
    static const ::android::StaticString16 DESCIPTOR (u"android.content.pm.PackageChangeEvent");
    return DESCIPTOR;
  }
  inline std::string toString() const {
    std::ostringstream os;
    os << "PackageChangeEvent{";
    os << "packageName: " << ::android::internal::ToString(packageName);
    os << ", version: " << ::android::internal::ToString(version);
    os << ", lastUpdateTimeMillis: " << ::android::internal::ToString(lastUpdateTimeMillis);
    os << ", newInstalled: " << ::android::internal::ToString(newInstalled);
    os << ", dataRemoved: " << ::android::internal::ToString(dataRemoved);
    os << ", isDeleted: " << ::android::internal::ToString(isDeleted);
    os << "}";
    return os.str();
  }
};  // class PackageChangeEvent

}  // namespace pm

}  // namespace content

}  // namespace android
