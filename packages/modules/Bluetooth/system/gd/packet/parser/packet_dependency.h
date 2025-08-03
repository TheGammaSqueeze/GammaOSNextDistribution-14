/*
 * Copyright 2022 The Android Open Source Project
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

#include <map>
#include <vector>

#include "parent_def.h"

class PacketDependency {
 private:
  std::map<std::string, std::vector<std::string>> dependencies;
  std::map<std::string, std::vector<std::string>> children_dependencies;

  std::set<std::string> CollectInitialParseAndMatchFields(
      const ParentDef* parent,
      std::map<std::string, std::set<std::string>>& initial_parse_and_match_fields) const;
  void FinalizeParseAndMatchFields(
      const ParentDef* parent,
      std::map<std::string, std::set<std::string>>& initial_parse_and_match_fields,
      std::vector<std::string>& available_fields);

 public:
  PacketDependency(const ParentDef* root);
  std::vector<std::string>& GetDependencies(const std::string& packet_name);
  std::vector<std::string>& GetChildrenDependencies(const std::string& packet_name);
};
