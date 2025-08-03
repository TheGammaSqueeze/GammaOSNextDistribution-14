/*
 * Copyright 2020 The Android Open Source Project
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

#include "sysprops/sysprops_module.h"

#include <filesystem>

#include "os/handler.h"
#include "os/log.h"
#include "os/parameter_provider.h"
#include "os/system_properties.h"
#include "storage/legacy_config_file.h"

namespace bluetooth {
namespace sysprops {

static const size_t kDefaultCapacity = 10000;

const ModuleFactory SyspropsModule::Factory = ModuleFactory([]() { return new SyspropsModule(); });

struct SyspropsModule::impl {
  impl(os::Handler* sysprops_handler) : sysprops_handler_(sysprops_handler) {}

  os::Handler* sysprops_handler_;
};

void SyspropsModule::ListDependencies(ModuleList* /* list */) const {}

void SyspropsModule::Start() {
  std::string file_path = os::ParameterProvider::SyspropsFilePath();
  if (!file_path.empty()) {
    parse_config(file_path);
    // Merge config fragments
    std::string override_dir = file_path + ".d";
    if (std::filesystem::exists(override_dir)) {
      for (const auto& entry : std::filesystem::directory_iterator(override_dir)) {
        parse_config(entry.path());
      }
    }
  }

  pimpl_ = std::make_unique<impl>(GetHandler());
}

void SyspropsModule::Stop() {
  pimpl_.reset();
}

std::string SyspropsModule::ToString() const {
  return "Sysprops Module";
}

void SyspropsModule::parse_config(std::string file_path) {
  const std::list<std::string> supported_sysprops = {
      // General
      "bluetooth.btm.sec.delay_auth_ms.value",
      "bluetooth.device.default_name",
      "bluetooth.core.gap.le.privacy.enabled",
      "bluetooth.core.gap.le.conn.only_init_1m_phy.enabled",
      "bluetooth.device.class_of_device",
      "bluetooth.device_id.product_id",
      "bluetooth.device_id.product_version",
      "bluetooth.device_id.vendor_id",
      "bluetooth.device_id.vendor_id_source",
      "persist.bluetooth.inq_by_rssi",
      // BR/EDR
      "bluetooth.core.classic.page_scan_type",
      "bluetooth.core.classic.page_scan_interval",
      "bluetooth.core.classic.page_scan_window",
      "bluetooth.core.classic.inq_scan_type",
      "bluetooth.core.classic.inq_scan_interval",
      "bluetooth.core.classic.inq_scan_window",
      "bluetooth.core.classic.inq_length",
      "bluetooth.core.acl.link_supervision_timeout",
      "bluetooth.core.classic.page_timeout",
      "bluetooth.core.classic.sniff_max_intervals",
      "bluetooth.core.classic.sniff_min_intervals",
      "bluetooth.core.classic.sniff_attempts",
      "bluetooth.core.classic.sniff_timeouts",
      // LE
      "bluetooth.core.le.min_connection_interval",
      "bluetooth.core.le.max_connection_interval",
      "bluetooth.core.le.connection_latency",
      "bluetooth.core.le.connection_supervision_timeout",
      "bluetooth.core.le.direct_connection_timeout",
      "bluetooth.core.le.connection_scan_interval_fast",
      "bluetooth.core.le.connection_scan_window_fast",
      "bluetooth.core.le.connection_scan_window_2m_fast",
      "bluetooth.core.le.connection_scan_window_coded_fast",
      "bluetooth.core.le.connection_scan_interval_slow",
      "bluetooth.core.le.connection_scan_window_slow",
      "bluetooth.core.le.inquiry_scan_interval",
      "bluetooth.core.le.inquiry_scan_window",
      "bluetooth.core.le.vendor_capabilities.enabled",
      // SCO
      "bluetooth.sco.disable_enhanced_connection",
      "bluetooth.sco.swb_supported",
      // Profile
      "persist.bluetooth.avrcpcontrolversion",
  };

  auto config = storage::LegacyConfigFile::FromPath(file_path).Read(kDefaultCapacity);
  if (!config) {
    return;
  }

  for (auto s = supported_sysprops.begin(); s != supported_sysprops.end(); s++) {
    auto str = config->GetProperty("Sysprops", *s);
    if (str) {
      bluetooth::os::SetSystemProperty(*s, *str);
    }
  }
}

}  // namespace sysprops
}  // namespace bluetooth
