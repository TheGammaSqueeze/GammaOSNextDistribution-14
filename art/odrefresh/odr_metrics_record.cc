/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "odr_metrics_record.h"

#include <iosfwd>
#include <string>

#include "android-base/logging.h"
#include "tinyxml2.h"

namespace art {
namespace odrefresh {

namespace {
android::base::Result<int64_t> ReadInt64(tinyxml2::XMLElement* parent, const char* name) {
  tinyxml2::XMLElement* element = parent->FirstChildElement(name);
  if (element == nullptr) {
    return Errorf("Expected Odrefresh metric {} not found", name);
  }

  int64_t metric;
  tinyxml2::XMLError result = element->QueryInt64Text(&metric);
  if (result == tinyxml2::XML_SUCCESS) {
    return metric;
  } else {
    return Errorf("Odrefresh metric {} is not an int64", name);
  }
}

android::base::Result<int32_t> ReadInt32(tinyxml2::XMLElement* parent, const char* name) {
  tinyxml2::XMLElement* element = parent->FirstChildElement(name);
  if (element == nullptr) {
    return Errorf("Expected Odrefresh metric {} not found", name);
  }

  int32_t metric;
  tinyxml2::XMLError result = element->QueryIntText(&metric);
  if (result == tinyxml2::XML_SUCCESS) {
    return metric;
  } else {
    return Errorf("Odrefresh metric {} is not an int32", name);
  }
}

android::base::Result<int32_t> ReadInt32Attribute(tinyxml2::XMLElement* element,
                                                  const char* element_name,
                                                  const char* attribute_name,
                                                  int min_value,
                                                  int max_value) {
  int32_t value;
  tinyxml2::XMLError result = element->QueryAttribute(attribute_name, &value);
  if (result != tinyxml2::XML_SUCCESS) {
    return Errorf("Expected Odrefresh metric {}.{} is not an int32", element_name, attribute_name);
  }

  if (value < min_value || value > max_value) {
    return Errorf(
        "Odrefresh metric {}.{} has a value ({}) outside of the expected range ([{}, {}])",
        element_name,
        attribute_name,
        value,
        min_value,
        max_value);
  }

  return value;
}

android::base::Result<OdrMetricsRecord::Dex2OatExecResult> ReadExecResult(
    tinyxml2::XMLElement* parent, const char* nodeName) {
  tinyxml2::XMLElement* element = parent->FirstChildElement(nodeName);
  if (element == nullptr) {
    return Errorf("Expected Odrefresh metric {} not found", nodeName);
  }

  return OdrMetricsRecord::Dex2OatExecResult(
      OR_RETURN(ReadInt32Attribute(element, nodeName, "status", 0, kExecResultNotRun)),
      OR_RETURN(ReadInt32Attribute(element, nodeName, "exit-code", -1, 255)),
      OR_RETURN(ReadInt32Attribute(element, nodeName, "signal", 0, SIGRTMAX)));
}

template <typename T>
void AddMetric(tinyxml2::XMLElement* parent, const char* name, const T& value) {
  parent->InsertNewChildElement(name)->SetText(value);
}

void AddResult(tinyxml2::XMLElement* parent,
               const char* name,
               const OdrMetricsRecord::Dex2OatExecResult& execResult) {
  tinyxml2::XMLElement* result = parent->InsertNewChildElement(name);
  result->SetAttribute("status", execResult.status);
  result->SetAttribute("exit-code", execResult.exit_code);
  result->SetAttribute("signal", execResult.signal);
}
}  // namespace

android::base::Result<void> OdrMetricsRecord::ReadFromFile(const std::string& filename) {
  tinyxml2::XMLDocument xml_document;
  tinyxml2::XMLError result = xml_document.LoadFile(filename.data());
  if (result != tinyxml2::XML_SUCCESS) {
    return android::base::Error() << xml_document.ErrorStr();
  }

  tinyxml2::XMLElement* metrics = xml_document.FirstChildElement("odrefresh_metrics");
  if (metrics == nullptr) {
    return Errorf("odrefresh_metrics element not found in {}", filename);
  }

  odrefresh_metrics_version = OR_RETURN(ReadInt32(metrics, "odrefresh_metrics_version"));
  if (odrefresh_metrics_version != kOdrefreshMetricsVersion) {
    return Errorf("odrefresh_metrics_version {} is different than expected ({})",
                  odrefresh_metrics_version,
                  kOdrefreshMetricsVersion);
  }

  art_apex_version = OR_RETURN(ReadInt64(metrics, "art_apex_version"));
  trigger = OR_RETURN(ReadInt32(metrics, "trigger"));
  stage_reached = OR_RETURN(ReadInt32(metrics, "stage_reached"));
  status = OR_RETURN(ReadInt32(metrics, "status"));
  cache_space_free_start_mib = OR_RETURN(ReadInt32(metrics, "cache_space_free_start_mib"));
  cache_space_free_end_mib = OR_RETURN(ReadInt32(metrics, "cache_space_free_end_mib"));
  primary_bcp_compilation_millis = OR_RETURN(ReadInt32(metrics, "primary_bcp_compilation_millis"));
  secondary_bcp_compilation_millis =
      OR_RETURN(ReadInt32(metrics, "secondary_bcp_compilation_millis"));
  system_server_compilation_millis =
      OR_RETURN(ReadInt32(metrics, "system_server_compilation_millis"));
  primary_bcp_dex2oat_result = OR_RETURN(ReadExecResult(metrics, "primary_bcp_dex2oat_result"));
  secondary_bcp_dex2oat_result = OR_RETURN(ReadExecResult(metrics, "secondary_bcp_dex2oat_result"));
  system_server_dex2oat_result = OR_RETURN(ReadExecResult(metrics, "system_server_dex2oat_result"));
  primary_bcp_compilation_type = OR_RETURN(ReadInt32(metrics, "primary_bcp_compilation_type"));
  secondary_bcp_compilation_type = OR_RETURN(ReadInt32(metrics, "secondary_bcp_compilation_type"));

  return {};
}

android::base::Result<void> OdrMetricsRecord::WriteToFile(const std::string& filename) const {
  tinyxml2::XMLDocument xml_document;
  tinyxml2::XMLElement* metrics = xml_document.NewElement("odrefresh_metrics");
  xml_document.InsertEndChild(metrics);

  // The order here matches the field order of MetricsRecord.
  AddMetric(metrics, "odrefresh_metrics_version", odrefresh_metrics_version);
  AddMetric(metrics, "art_apex_version", art_apex_version);
  AddMetric(metrics, "trigger", trigger);
  AddMetric(metrics, "stage_reached", stage_reached);
  AddMetric(metrics, "status", status);
  AddMetric(metrics, "cache_space_free_start_mib", cache_space_free_start_mib);
  AddMetric(metrics, "cache_space_free_end_mib", cache_space_free_end_mib);
  AddMetric(metrics, "primary_bcp_compilation_millis", primary_bcp_compilation_millis);
  AddMetric(metrics, "secondary_bcp_compilation_millis", secondary_bcp_compilation_millis);
  AddMetric(metrics, "system_server_compilation_millis", system_server_compilation_millis);
  AddResult(metrics, "primary_bcp_dex2oat_result", primary_bcp_dex2oat_result);
  AddResult(metrics, "secondary_bcp_dex2oat_result", secondary_bcp_dex2oat_result);
  AddResult(metrics, "system_server_dex2oat_result", system_server_dex2oat_result);
  AddMetric(metrics, "primary_bcp_compilation_type", primary_bcp_compilation_type);
  AddMetric(metrics, "secondary_bcp_compilation_type", secondary_bcp_compilation_type);

  tinyxml2::XMLError result = xml_document.SaveFile(filename.data(), /*compact=*/true);
  if (result == tinyxml2::XML_SUCCESS) {
    return {};
  } else {
    return android::base::Error() << xml_document.ErrorStr();
  }
}

}  // namespace odrefresh
}  // namespace art
