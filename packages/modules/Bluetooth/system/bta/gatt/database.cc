/******************************************************************************
 *
 *  Copyright 2018 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "database.h"

#include <base/logging.h>
#include <bluetooth/log.h>

#include <algorithm>
#include <list>
#include <sstream>

#include "crypto_toolbox/crypto_toolbox.h"
#include "internal_include/bt_trace.h"
#include "stack/include/bt_types.h"
#include "stack/include/gattdefs.h"
#include "types/bluetooth/uuid.h"

using bluetooth::Uuid;
using namespace bluetooth;

namespace gatt {

namespace {
const Uuid PRIMARY_SERVICE = Uuid::From16Bit(GATT_UUID_PRI_SERVICE);
const Uuid SECONDARY_SERVICE = Uuid::From16Bit(GATT_UUID_SEC_SERVICE);
const Uuid INCLUDE = Uuid::From16Bit(GATT_UUID_INCLUDE_SERVICE);
const Uuid CHARACTERISTIC = Uuid::From16Bit(GATT_UUID_CHAR_DECLARE);
const Uuid CHARACTERISTIC_EXTENDED_PROPERTIES =
    Uuid::From16Bit(GATT_UUID_CHAR_EXT_PROP);

bool HandleInRange(const Service& svc, uint16_t handle) {
  return handle >= svc.handle && handle <= svc.end_handle;
}
}  // namespace

static size_t UuidSize(const Uuid& uuid) {
  size_t len = uuid.GetShortestRepresentationSize();
  return (len == Uuid::kNumBytes32) ? Uuid::kNumBytes128 : len;
}

Service* FindService(std::list<Service>& services, uint16_t handle) {
  for (Service& service : services) {
    if (handle >= service.handle && handle <= service.end_handle)
      return &service;
  }

  return nullptr;
}

std::string Database::ToString() const {
  std::stringstream tmp;

  for (const Service& service : services) {
    tmp << "Service: handle=" << loghex(service.handle)
        << ", end_handle=" << loghex(service.end_handle)
        << ", uuid=" << service.uuid << "\n";

    for (const auto& is : service.included_services) {
      tmp << "\t Included service: handle=" << loghex(is.handle)
          << ", start_handle=" << loghex(is.start_handle)
          << ", end_handle=" << loghex(is.end_handle) << ", uuid=" << is.uuid
          << "\n";
    }

    for (const Characteristic& c : service.characteristics) {
      tmp << "\t Characteristic: declaration_handle="
          << loghex(c.declaration_handle)
          << ", value_handle=" << loghex(c.value_handle) << ", uuid=" << c.uuid
          << ", prop=" << loghex(c.properties) << "\n";

      for (const Descriptor& d : c.descriptors) {
        tmp << "\t\t Descriptor: handle=" << loghex(d.handle)
            << ", uuid=" << d.uuid << "\n";
      }
    }
  }
  return tmp.str();
}

std::vector<StoredAttribute> Database::Serialize() const {
  std::vector<StoredAttribute> nv_attr;

  if (services.empty()) return std::vector<StoredAttribute>();

  for (const Service& service : services) {
    // TODO: add constructor to NV_ATTR, use emplace_back
    nv_attr.push_back({service.handle,
                       service.is_primary ? PRIMARY_SERVICE : SECONDARY_SERVICE,
                       {.service = {.uuid = service.uuid,
                                    .end_handle = service.end_handle}}});
  }

  for (const Service& service : services) {
    for (const IncludedService& p_isvc : service.included_services) {
      nv_attr.push_back({p_isvc.handle,
                         INCLUDE,
                         {.included_service = {.handle = p_isvc.start_handle,
                                               .end_handle = p_isvc.end_handle,
                                               .uuid = p_isvc.uuid}}});
    }

    for (const Characteristic& charac : service.characteristics) {
      nv_attr.push_back(
          {charac.declaration_handle,
           CHARACTERISTIC,
           {.characteristic = {.properties = charac.properties,
                               .value_handle = charac.value_handle,
                               .uuid = charac.uuid}}});

      for (const Descriptor& desc : charac.descriptors) {
        if (desc.uuid == CHARACTERISTIC_EXTENDED_PROPERTIES) {
          nv_attr.push_back({desc.handle,
                             desc.uuid,
                             {.characteristic_extended_properties =
                                  desc.characteristic_extended_properties}});
        } else {
          nv_attr.push_back({desc.handle, desc.uuid, {}});
        }
      }
    }
  }

  return nv_attr;
}

Database Database::Deserialize(const std::vector<StoredAttribute>& nv_attr,
                               bool* success) {
  // clear reallocating
  Database result;
  auto it = nv_attr.cbegin();

  for (; it != nv_attr.cend(); ++it) {
    const auto& attr = *it;
    if (attr.type != PRIMARY_SERVICE && attr.type != SECONDARY_SERVICE) break;
    result.services.emplace_back(Service{
        .handle = attr.handle,
        .uuid = attr.value.service.uuid,
        .is_primary = (attr.type == PRIMARY_SERVICE),
        .end_handle = attr.value.service.end_handle,
        .included_services = {},
        .characteristics = {},
    });
  }

  auto current_service_it = result.services.begin();
  for (; it != nv_attr.cend(); it++) {
    const auto& attr = *it;

    // go to the service this attribute belongs to; attributes are stored in
    // order, so iterating just forward is enough
    while (current_service_it != result.services.end() &&
           current_service_it->end_handle < attr.handle) {
      current_service_it++;
    }

    if (current_service_it == result.services.end() ||
        !HandleInRange(*current_service_it, attr.handle)) {
      log::error("Can't find service for attribute with handle: {}",
                 loghex(attr.handle));
      *success = false;
      return result;
    }

    if (attr.type == INCLUDE) {
      Service* included_service =
          FindService(result.services, attr.value.included_service.handle);
      if (!included_service) {
        log::error("Non-existing included service!");
        *success = false;
        return result;
      }
      current_service_it->included_services.push_back(IncludedService{
          .handle = attr.handle,
          .uuid = attr.value.included_service.uuid,
          .start_handle = attr.value.included_service.handle,
          .end_handle = attr.value.included_service.end_handle,
      });
    } else if (attr.type == CHARACTERISTIC) {
      current_service_it->characteristics.emplace_back(Characteristic{
          .declaration_handle = attr.handle,
          .uuid = attr.value.characteristic.uuid,
          .value_handle = attr.value.characteristic.value_handle,
          .properties = attr.value.characteristic.properties,
          .descriptors = {},
      });

    } else {
      if (attr.type == CHARACTERISTIC_EXTENDED_PROPERTIES) {
        current_service_it->characteristics.back().descriptors.emplace_back(
            Descriptor{.handle = attr.handle,
                       .uuid = attr.type,
                       .characteristic_extended_properties =
                           attr.value.characteristic_extended_properties});

      } else {
        current_service_it->characteristics.back().descriptors.emplace_back(
            Descriptor{
                .handle = attr.handle,
                .uuid = attr.type,
                .characteristic_extended_properties = {},
            });
      }
    }
  }
  *success = true;
  return result;
}

Octet16 Database::Hash() const {
  int len = 0;
  // Compute how much space we need to actually hold the data.
  for (const Service& service : services) {
    len += 4 + UuidSize(service.uuid);

    for (const auto& is : service.included_services) {
      len += 8 + UuidSize(is.uuid);
    }

    for (const Characteristic& c : service.characteristics) {
      len += 7 + UuidSize(c.uuid);

      for (const Descriptor& d : c.descriptors) {
        if (UuidSize(d.uuid) != Uuid::kNumBytes16) {
          continue;
        }
        uint16_t value = d.uuid.As16Bit();
        if (value == GATT_UUID_CHAR_DESCRIPTION ||
            value == GATT_UUID_CHAR_CLIENT_CONFIG ||
            value == GATT_UUID_CHAR_SRVR_CONFIG ||
            value == GATT_UUID_CHAR_PRESENT_FORMAT ||
            value == GATT_UUID_CHAR_AGG_FORMAT) {
          len += 2 + UuidSize(d.uuid);
        } else if (value == GATT_UUID_CHAR_EXT_PROP) {
          len += 4 + UuidSize(d.uuid);
        }
      }
    }
  }

  std::vector<uint8_t> serialized(len);
  uint8_t* p = serialized.data();
  for (const Service& service : services) {
    UINT16_TO_STREAM(p, service.handle);
    if (service.is_primary) {
      UINT16_TO_STREAM(p, GATT_UUID_PRI_SERVICE);
    } else {
      UINT16_TO_STREAM(p, GATT_UUID_SEC_SERVICE);
    }

    if (UuidSize(service.uuid) == Uuid::kNumBytes16) {
      UINT16_TO_STREAM(p, service.uuid.As16Bit());
    } else {
      ARRAY_TO_STREAM(p, service.uuid.To128BitLE(), (int)Uuid::kNumBytes128);
    }

    for (const auto& is : service.included_services) {
      UINT16_TO_STREAM(p, is.handle);
      UINT16_TO_STREAM(p, GATT_UUID_INCLUDE_SERVICE);
      UINT16_TO_STREAM(p, is.start_handle);
      UINT16_TO_STREAM(p, is.end_handle);

      if (UuidSize(is.uuid) == Uuid::kNumBytes16) {
        UINT16_TO_STREAM(p, is.uuid.As16Bit());
      } else {
        ARRAY_TO_STREAM(p, is.uuid.To128BitLE(), (int)Uuid::kNumBytes128);
      }
    }

    for (const Characteristic& c : service.characteristics) {
      UINT16_TO_STREAM(p, c.declaration_handle);
      UINT16_TO_STREAM(p, GATT_UUID_CHAR_DECLARE);
      UINT8_TO_STREAM(p, c.properties);
      UINT16_TO_STREAM(p, c.value_handle);

      if (UuidSize(c.uuid) == Uuid::kNumBytes16) {
        UINT16_TO_STREAM(p, c.uuid.As16Bit());
      } else {
        ARRAY_TO_STREAM(p, c.uuid.To128BitLE(), (int)Uuid::kNumBytes128);
      }

      for (const Descriptor& d : c.descriptors) {
        if (UuidSize(d.uuid) != Uuid::kNumBytes16) continue;
        uint16_t value = d.uuid.As16Bit();
        if (value == GATT_UUID_CHAR_DESCRIPTION ||
            value == GATT_UUID_CHAR_CLIENT_CONFIG ||
            value == GATT_UUID_CHAR_SRVR_CONFIG ||
            value == GATT_UUID_CHAR_PRESENT_FORMAT ||
            value == GATT_UUID_CHAR_AGG_FORMAT) {
          UINT16_TO_STREAM(p, d.handle);
          UINT16_TO_STREAM(p, d.uuid.As16Bit());
        } else if (value == GATT_UUID_CHAR_EXT_PROP) {
          UINT16_TO_STREAM(p, d.handle);
          UINT16_TO_STREAM(p, d.uuid.As16Bit());
          UINT16_TO_STREAM(p, d.characteristic_extended_properties);
        }
      }
    }
  }

  std::reverse(serialized.begin(), serialized.end());
  return crypto_toolbox::aes_cmac(Octet16{0}, serialized.data(),
                                  serialized.size());
}
}  // namespace gatt
