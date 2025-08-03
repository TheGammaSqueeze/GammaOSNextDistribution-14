/*
 * Copyright 2023 The Android Open Source Project
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

#include <base/strings/stringprintf.h>

#include <deque>
#include <string>

#include "gd/os/log.h"
#include "include/hardware/bluetooth.h"
#include "macros.h"
#include "test/headless/log.h"
#include "test/headless/property.h"
#include "test/headless/text.h"
#include "types/raw_address.h"

enum class Callback {
  AclStateChanged,
  AdapterProperties,
  DeviceFound,
  DiscoveryStateChanged,
  RemoteDeviceProperties,
};

inline std::string callback_text(const Callback& callback) {
  switch (callback) {
    CASE_RETURN_TEXT(Callback::AclStateChanged);
    CASE_RETURN_TEXT(Callback::AdapterProperties);
    CASE_RETURN_TEXT(Callback::DeviceFound);
    CASE_RETURN_TEXT(Callback::DiscoveryStateChanged);
    CASE_RETURN_TEXT(Callback::RemoteDeviceProperties);
  }
}

struct callback_data_t {
  std::string Name() const { return std::string(name_); }
  Callback CallbackType() const { return callback_type_; }

  uint64_t TimestampInMs() const {
    return static_cast<uint64_t>(timestamp_ms_);
  }
  virtual ~callback_data_t() = default;

  virtual std::string ToString() const = 0;

 protected:
  callback_data_t(const char* name, Callback callback_type_)
      : name_(name),
        callback_type_(callback_type_),
        timestamp_ms_(GetTimestampMs()) {}

 private:
  const char* name_;
  const Callback callback_type_;
  const long long timestamp_ms_;
};

struct callback_params_t : public callback_data_t {
  virtual std::string ToString() const override {
    return std::string("VIRTUAL");
  }

 protected:
  callback_params_t(const char* name, Callback callback_type)
      : callback_data_t(name, callback_type) {}
  virtual ~callback_params_t() = default;
};

// Specializes the callback parameter
template <typename T>
// std::shared_ptr<T> Cast(std::shared_ptr<callback_params_t> params) { return
// std::shared_ptr<T>(static_cast<T*>(params.get()));}
std::shared_ptr<T> Cast(std::shared_ptr<callback_params_t> params) {
  return std::make_shared<T>(*(static_cast<T*>(params.get())));
}

struct callback_params_with_properties_t : public callback_params_t {
 public:
  std::deque<bluetooth::test::headless::bt_property_t*> properties() const {
    return property_queue_;
  }
  size_t num_properties() const { return property_queue_.size(); }

 protected:
  callback_params_with_properties_t(const char* name, Callback callback_type,
                                    int num_properties,
                                    ::bt_property_t* properties)
      : callback_params_t(name, callback_type) {
    for (int i = 0; i < num_properties; i++) {
      LOG_DEBUG("Processing property %d/%d %p type:%d val:%p", i,
                num_properties, &properties[i], properties[i].type,
                properties[i].val);
      property_queue_.push_back(
          bluetooth::test::headless::property_factory(properties[i]));
    }
  }
  virtual ~callback_params_with_properties_t() = default;

 private:
  std::deque<bluetooth::test::headless::bt_property_t*> property_queue_;
};

struct acl_state_changed_params_t : public callback_params_t {
  acl_state_changed_params_t(bt_status_t status, RawAddress remote_bd_addr,
                             bt_acl_state_t state, int transport_link_type,
                             bt_hci_error_code_t hci_reason,
                             bt_conn_direction_t direction, uint16_t acl_handle)
      : callback_params_t("acl_state_changed", Callback::AclStateChanged),
        status(status),
        remote_bd_addr(remote_bd_addr),
        state(state),
        transport_link_type(transport_link_type),
        hci_reason(hci_reason),
        direction(direction),
        acl_handle(acl_handle) {}
  acl_state_changed_params_t(const acl_state_changed_params_t& params) =
      default;
  virtual ~acl_state_changed_params_t() {}

  bt_status_t status;
  RawAddress remote_bd_addr;
  bt_acl_state_t state;
  int transport_link_type;
  bt_hci_error_code_t hci_reason;
  bt_conn_direction_t direction;
  uint16_t acl_handle;

  std::string ToString() const override {
    return base::StringPrintf(
        "status:%s remote_bd_addr:%s state:%s transport:%s reason:%s"
        " direction:%s handle:%hu",
        bt_status_text(status).c_str(), remote_bd_addr.ToString().c_str(),
        (state == BT_ACL_STATE_CONNECTED) ? "CONNECTED" : "DISCONNECTED",
        bt_transport_text(static_cast<const tBT_TRANSPORT>(transport_link_type))
            .c_str(),
        bt_status_text(static_cast<const bt_status_t>(hci_reason)).c_str(),
        bt_conn_direction_text(direction).c_str(), acl_handle);
  }
};

struct discovery_state_changed_params_t : public callback_params_t {
  discovery_state_changed_params_t(bt_discovery_state_t state)
      : callback_params_t("discovery_state_changed",
                          Callback::DiscoveryStateChanged),
        state(state) {}
  discovery_state_changed_params_t(
      const discovery_state_changed_params_t& params) = default;

  virtual ~discovery_state_changed_params_t() {}

  bt_discovery_state_t state;
  std::string ToString() const override {
    return base::StringPrintf("state:%s",
                              bt_discovery_state_text(state).c_str());
  }
};

struct adapter_properties_params_t : public callback_params_with_properties_t {
  adapter_properties_params_t(bt_status_t status, int num_properties,
                              ::bt_property_t* properties)
      : callback_params_with_properties_t("adapter_properties",
                                          Callback::AdapterProperties,
                                          num_properties, properties),
        status(status) {}
  adapter_properties_params_t(const adapter_properties_params_t& params) =
      default;

  virtual ~adapter_properties_params_t() {}
  bt_status_t status;

  std::string ToString() const override {
    return base::StringPrintf("status:%s num_properties:%zu",
                              bt_status_text(status).c_str(), num_properties());
  }
};

struct remote_device_properties_params_t
    : public callback_params_with_properties_t {
  remote_device_properties_params_t(bt_status_t status, RawAddress bd_addr,
                                    int num_properties,
                                    ::bt_property_t* properties)
      : callback_params_with_properties_t("remote_device_properties",
                                          Callback::RemoteDeviceProperties,
                                          num_properties, properties),
        status(status),
        bd_addr(bd_addr) {}
  remote_device_properties_params_t(
      const remote_device_properties_params_t& params) = default;

  virtual ~remote_device_properties_params_t() {}
  bt_status_t status;
  RawAddress bd_addr;

  std::string ToString() const override {
    return base::StringPrintf("status:%s bd_addr:%s num_properties:%zu",
                              bt_status_text(status).c_str(),
                              bd_addr.ToString().c_str(), num_properties());
  }
};

struct device_found_params_t : public callback_params_with_properties_t {
  device_found_params_t(int num_properties, ::bt_property_t* properties)
      : callback_params_with_properties_t("device_found", Callback::DeviceFound,
                                          num_properties, properties) {}

  device_found_params_t(const device_found_params_t& params) = default;
  virtual ~device_found_params_t() {}

  std::string ToString() const override {
    return base::StringPrintf("num_properties:%zu", num_properties());
  }
};

using callback_function_t = void (*)(callback_data_t*);

void headless_add_callback(const std::string interface_name,
                           callback_function_t function);
void headless_remove_callback(const std::string interface_name);
