#ifndef AIDL_GENERATED_ANDROID_CONTENT_PM_PACKAGE_CHANGE_EVENT_H_
#define AIDL_GENERATED_ANDROID_CONTENT_PM_PACKAGE_CHANGE_EVENT_H_

#include <binder/Parcel.h>
#include <binder/Status.h>
#include <cstdint>
#include <string>
#include <tuple>

namespace android {

namespace content {

namespace pm {

class PackageChangeEvent : public ::android::Parcelable {
public:
  inline bool operator!=([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)!=std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator<([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)<std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator<=([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)<=std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator==([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)==std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator>([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)>std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  inline bool operator>=([[maybe_unused]] const PackageChangeEvent& rhs) const {
    return std::tie(packageName, version, lastUpdateTimeMillis, newInstalled, dataRemoved, isDeleted)>=std::tie(rhs.packageName, rhs.version, rhs.lastUpdateTimeMillis, rhs.newInstalled, rhs.dataRemoved, rhs.isDeleted);
  }
  ::std::string packageName;
  int64_t version = {};
  int64_t lastUpdateTimeMillis = {};
  bool newInstalled = {};
  bool dataRemoved = {};
  bool isDeleted = {};
  ::android::status_t readFromParcel(const ::android::Parcel* _aidl_parcel) override final;
  ::android::status_t writeToParcel(::android::Parcel* _aidl_parcel) const override final;
};  // class PackageChangeEvent

}  // namespace pm

}  // namespace content

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_CONTENT_PM_PACKAGE_CHANGE_EVENT_H_
