/*
 * Copyright 2024 The Android Open Source Project
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

#pragma once

#include <memory>
#include <string>
#include <vector>

#include "hardware/bluetooth.h"
#include "include/hardware/bluetooth.h"
#include "stack/include/bt_name.h"

std::string bt_property_text(const bt_property_t& property);
std::string bt_property_type_text(const bt_property_type_t& type);

namespace bluetooth {
namespace property {

class BtProperty {
 public:
  // Return size in bytes of property data value
  virtual size_t Size() const = 0;
  // Returns raw pointer to the data value
  virtual const void* Val() const = 0;

  bt_property_type_t Type() const {
    return type_;
  }

  std::string ToString() const {
    return bt_property_type_text(type_);
  }

 protected:
  BtProperty(bt_property_type_t type) : type_(type) {}
  virtual ~BtProperty() = default;

 private:
  const bt_property_type_t type_;
};

// Provide pointer/size access to properties for legacy jni API
class BtPropertyLegacy {
 public:
  BtPropertyLegacy(const std::vector<std::shared_ptr<BtProperty>>& bt_properties);

  void Export(bt_property_t* bt_properties, size_t size);

  size_t NumProperties() const;

  const std::vector<bt_property_t>& Properties() const;

  bt_property_t* Ptr() const {
    return const_cast<bt_property_t*>(&properties_[0]);
  }
  int Len() const {
    return static_cast<int>(properties_.size());
  }

 private:
  const std::vector<std::shared_ptr<BtProperty>> bt_properties_;
  std::vector<bt_property_t> properties_;
};

template <typename T>
class BtPropertySimple : public BtProperty {
 public:
  virtual size_t Size() const override {
    return sizeof(T);
  }

  const void* Val() const override {
    return (const void*)val_.get();
  }

 protected:
  BtPropertySimple<T>(bt_property_type_t type, T val)
      : BtProperty(type), val_(std::make_shared<T>(val)) {}

 private:
  std::shared_ptr<T> val_;
};

template <typename T>
class BtPropertyVector : public BtProperty {
 public:
  virtual size_t Size() const override {
    return sizeof(T) * val_->size();
  }
  const void* Val() const override {
    return (const void*)&(*val_)[0];
  }

 protected:
  // Create a vector property from another vector
  BtPropertyVector<T>(bt_property_type_t type, const std::vector<T>& val)
      : BtProperty(type), val_(std::make_shared<std::vector<T>>(val)) {}

  // Create a vector property from a raw pointer and size
  BtPropertyVector<T>(bt_property_type_t type, const T* val, size_t size)
      : BtProperty(type), val_(std::make_shared<std::vector<T>>(val, val + size)) {}

 protected:
  std::shared_ptr<std::vector<T>> val_;
};

template <typename T>
class BtPropertyVectorWithPad : public BtPropertyVector<T> {
 protected:
  // Create a vector property from a raw pointer and size with pad element
  BtPropertyVectorWithPad<T>(bt_property_type_t type, const T* val, size_t size, T pad)
      : BtPropertyVector<T>(type, val, size) {
    BtPropertyVector<T>::val_->push_back(pad);
  }
};

class BdName : public BtPropertyVectorWithPad<uint8_t> {
 public:
  BdName(const BD_NAME bd_name)
      : BtPropertyVectorWithPad<uint8_t>(BT_PROPERTY_BDNAME, bd_name, kBdNameLength, kBdNameDelim) {
  }

  static std::shared_ptr<BdName> Create(const BD_NAME bd_name);
};

class BdAddr : public BtPropertySimple<RawAddress> {
 public:
  BdAddr(const RawAddress& bd_addr) : BtPropertySimple<RawAddress>(BT_PROPERTY_BDADDR, bd_addr) {}

  static std::shared_ptr<BdAddr> Create(const RawAddress& bd_addr);
};

class Uuids : public BtPropertyVector<bluetooth::Uuid> {
 public:
  Uuids(const std::vector<bluetooth::Uuid>& uuids)
      : BtPropertyVector<bluetooth::Uuid>(BT_PROPERTY_UUIDS, uuids) {}

  static std::shared_ptr<Uuids> Create(const std::vector<bluetooth::Uuid>& uuids);
};

class ClassOfDevice : public BtPropertySimple<uint32_t> {
 public:
  ClassOfDevice(const uint32_t& cod)
      : BtPropertySimple<uint32_t>(BT_PROPERTY_CLASS_OF_DEVICE, cod) {}
  static std::shared_ptr<ClassOfDevice> Create(const uint32_t& bd_addr);
};

class TypeOfDevice : public BtPropertySimple<bt_device_type_t> {
 public:
  TypeOfDevice(const bt_device_type_t& device_type)
      : BtPropertySimple<bt_device_type_t>(BT_PROPERTY_TYPE_OF_DEVICE, device_type) {}
  static std::shared_ptr<TypeOfDevice> Create(const bt_device_type_t& device_type);
};

class ServiceRecord : public BtPropertySimple<bt_service_record_t> {
 public:
  ServiceRecord(const bt_service_record_t& record)
      : BtPropertySimple<bt_service_record_t>(BT_PROPERTY_SERVICE_RECORD, record) {}
  static std::shared_ptr<ServiceRecord> Create(const bt_service_record_t& record);
};

class AdapterScanMode : public BtPropertySimple<bt_scan_mode_t> {
 public:
  AdapterScanMode(const bt_scan_mode_t& mode)
      : BtPropertySimple<bt_scan_mode_t>(BT_PROPERTY_ADAPTER_SCAN_MODE, mode) {}
  static std::shared_ptr<AdapterScanMode> Create(const bt_scan_mode_t& mode);
};

class AdapterBondedDevices : public BtPropertyVector<RawAddress> {
 public:
  AdapterBondedDevices(const RawAddress* bd_addr, size_t len)
      : BtPropertyVector<RawAddress>(BT_PROPERTY_ADAPTER_BONDED_DEVICES, bd_addr, len) {}

  static std::shared_ptr<AdapterBondedDevices> Create(const RawAddress* bd_addr, size_t len);
};

class AdapterDiscoverableTimeout : public BtPropertySimple<uint32_t> {
 public:
  AdapterDiscoverableTimeout(const uint32_t& timeout)
      : BtPropertySimple<uint32_t>(BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT, timeout) {}

  static std::shared_ptr<AdapterDiscoverableTimeout> Create(const uint32_t& timeout);
};

class RemoteFriendlyName : public BtPropertyVectorWithPad<uint8_t> {
 public:
  RemoteFriendlyName(const uint8_t bd_name[], size_t len)
      : BtPropertyVectorWithPad<uint8_t>(
            BT_PROPERTY_REMOTE_FRIENDLY_NAME, bd_name, len, kBdNameDelim) {}

  static std::shared_ptr<RemoteFriendlyName> Create(const uint8_t bd_name[], size_t len);
};

class RemoteRSSI : public BtPropertySimple<int8_t> {
 public:
  RemoteRSSI(const int8_t& rssi) : BtPropertySimple<int8_t>(BT_PROPERTY_REMOTE_RSSI, rssi) {}

  static std::shared_ptr<RemoteRSSI> Create(const int8_t& rssi);
};

class RemoteVersionInfo : public BtPropertySimple<bt_remote_version_t> {
 public:
  RemoteVersionInfo(const bt_remote_version_t& info)
      : BtPropertySimple<bt_remote_version_t>(BT_PROPERTY_REMOTE_VERSION_INFO, info) {}

  static std::shared_ptr<RemoteVersionInfo> Create(const bt_remote_version_t& info);
};

class LocalLeFeatures : public BtPropertySimple<bt_local_le_features_t> {
 public:
  LocalLeFeatures(const bt_local_le_features_t& features)
      : BtPropertySimple<bt_local_le_features_t>(BT_PROPERTY_LOCAL_LE_FEATURES, features) {}

  static std::shared_ptr<LocalLeFeatures> Create(const bt_local_le_features_t& features);
};

class LocalIOCaps : public BtPropertySimple<bt_io_cap_t> {
 public:
  LocalIOCaps(const bt_io_cap_t& cap)
      : BtPropertySimple<bt_io_cap_t>(BT_PROPERTY_LOCAL_IO_CAPS, cap) {}

  static std::shared_ptr<LocalIOCaps> Create(const bt_io_cap_t& cap);
};

class RemoteIsCoordinatedSetMember : public BtPropertySimple<bool> {
 public:
  RemoteIsCoordinatedSetMember(const bool& is_set_member)
      : BtPropertySimple<bool>(BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER, is_set_member) {}

  static std::shared_ptr<RemoteIsCoordinatedSetMember> Create(const bool& is_set_member);
};

class Appearance : public BtPropertySimple<uint16_t> {
 public:
  Appearance(const uint16_t& appearance)
      : BtPropertySimple<uint16_t>(BT_PROPERTY_APPEARANCE, appearance) {}

  static std::shared_ptr<Appearance> Create(const uint16_t& appearance);
};

class VendorProductInfo : public BtPropertySimple<bt_vendor_product_info_t> {
 public:
  VendorProductInfo(const bt_vendor_product_info_t& info)
      : BtPropertySimple<bt_vendor_product_info_t>(BT_PROPERTY_VENDOR_PRODUCT_INFO, info) {}

  static std::shared_ptr<VendorProductInfo> Create(const bt_vendor_product_info_t& info);
};

class RemoteASHACapability : public BtPropertySimple<int16_t> {
 public:
  RemoteASHACapability(const int16_t capability)
      : BtPropertySimple<int16_t>(BT_PROPERTY_REMOTE_ASHA_CAPABILITY, capability) {}

  static std::shared_ptr<RemoteASHACapability> Create(const int16_t& capability);
};

class RemoteASHATruncatedHiSyncId : public BtPropertySimple<uint32_t> {
 public:
  RemoteASHATruncatedHiSyncId(const uint32_t id)
      : BtPropertySimple<uint32_t>(BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID, id) {}

  static std::shared_ptr<RemoteASHATruncatedHiSyncId> Create(const uint32_t& id);
};

class RemoteModelNum : public BtPropertyVectorWithPad<uint8_t> {
 public:
  RemoteModelNum(const bt_bdname_t& name)
      : BtPropertyVectorWithPad<uint8_t>(
            BT_PROPERTY_REMOTE_MODEL_NUM,
            name.name,
            sizeof(bt_bdname_t) - sizeof(kBdNameDelim),
            kBdNameDelim) {}

  static std::shared_ptr<RemoteModelNum> Create(const bt_bdname_t& name);
};

class RemoteAddrType : public BtPropertySimple<uint8_t> {
 public:
  RemoteAddrType(const uint8_t& type)
      : BtPropertySimple<uint8_t>(BT_PROPERTY_REMOTE_ADDR_TYPE, type) {}

  static std::shared_ptr<RemoteAddrType> Create(const uint8_t& type);
};

class RemoteDeviceTimestamp : public BtPropertySimple<int> {
 public:
  RemoteDeviceTimestamp(const int& timestamp)
      : BtPropertySimple<int>(BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP, timestamp) {}

  static std::shared_ptr<RemoteDeviceTimestamp> Create(const int& timestamp);
};

}  // namespace property
}  // namespace bluetooth
