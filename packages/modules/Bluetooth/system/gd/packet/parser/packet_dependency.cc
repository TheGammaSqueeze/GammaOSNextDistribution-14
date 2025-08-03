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

#include "packet_dependency.h"

#include <algorithm>
#include <list>
#include <set>

PacketDependency::PacketDependency(const ParentDef* root) {
  std::map<std::string, std::set<std::string>> initial_parse_and_match_fields;
  std::vector<std::string> available_fields;
  CollectInitialParseAndMatchFields(root, initial_parse_and_match_fields);
  FinalizeParseAndMatchFields(root, initial_parse_and_match_fields, available_fields);
}

std::vector<std::string>& PacketDependency::GetDependencies(const std::string& packet_name) {
  return dependencies[packet_name];
}

std::vector<std::string>& PacketDependency::GetChildrenDependencies(
    const std::string& packet_name) {
  return children_dependencies[packet_name];
}

std::set<std::string> PacketDependency::CollectInitialParseAndMatchFields(
    const ParentDef* parent,
    std::map<std::string, std::set<std::string>>& initial_parse_and_match_fields) const {
  // Case Leaf Packet: Return all of its constraints
  if (parent->children_.empty()) {
    auto constraints = parent->GetAllConstraints();
    auto constraints_set = std::set<std::string>{};
    for (auto& c : constraints) {
      constraints_set.insert(c.first);
    }
    return constraints_set;
  }

  auto children_constraints = std::set<std::string>{};
  auto parent_constraints = parent->GetAllConstraints();
  auto parent_fields = parent->fields_;

  for (const auto child : parent->children_) {
    auto constraints = CollectInitialParseAndMatchFields(child, initial_parse_and_match_fields);
    auto child_only_constraints = std::set<std::string>{};
    for (auto c : constraints) {
      //             __PARENT__
      //          c1/   c2|     \c3
      //           /      |      \.
      //         CH1     CH2     CH3
      //        c4|
      //          |
      //        CH11
      // GetAllConstraints on leaf packet CH11 will return (C4, c1)
      // GetAllConstraints on packet CH1 will return C1
      // Thus CH11 only constraint is: (C4, C1) - (C1) => (C4)
      if (parent_constraints.find(c) == parent_constraints.end()) {
        child_only_constraints.insert(c);
      }
      // If the constraint can be satisfied by the immediate parent, no need to accumulate it
      if (parent_fields.GetField(c) != nullptr) {
        continue;
      }
      // Accumulate constraints from all the children so parent packet can accurately
      // figure out which constraints it should be getting from its parents.
      children_constraints.insert(c);
    }
    // child_only_constraints contains the variables required to be passed in when calling parse
    initial_parse_and_match_fields[child->name_] = child_only_constraints;
  }
  return children_constraints;
}

void PacketDependency::FinalizeParseAndMatchFields(
    const ParentDef* parent,
    std::map<std::string, std::set<std::string>>& initial_parse_and_match_fields,
    std::vector<std::string>& available_fields) {
  // Root does not have any constraints on anything
  if (parent->parent_ == nullptr) {
    dependencies[parent->name_] = std::vector<std::string>{};
    children_dependencies[parent->name_] = std::vector<std::string>{};
  }

  // Collect the available fields, required to fix the order of pass and match vectors
  for (auto& pf : parent->fields_) {
    available_fields.push_back(pf->GetName());
  }

  auto children_constraints_to_me = std::set<std::string>{};
  children_dependencies[parent->name_] = std::vector<std::string>{};

  // Accumulate direct constraints from all the children to parent
  //             __PARENT__
  //          c1/   c2|     \c3
  //           /      |      \.
  //         CH1     CH2     CH3
  //        c4|
  //          |
  //        CH11
  // For this case: children_constraints_to_me = (c1, c2, c3)
  for (auto& child : parent->children_) {
    for (auto pcons : child->parent_constraints_) {
      children_constraints_to_me.insert(pcons.first);
    }
  }

  // If children constraints to the parent are (c1, c2, c3) and so far parent has
  // fields (c1, c2) available, then parent will match its children on (c1, c2)
  for (auto avf : available_fields) {
    if (children_constraints_to_me.find(avf) != children_constraints_to_me.end()) {
      auto& match_variables = children_dependencies[parent->name_];
      if (std::find(match_variables.begin(), match_variables.end(), avf) == match_variables.end()) {
        match_variables.push_back(avf);
      }
    }
  }

  for (auto& child : parent->children_) {
    auto child_initial_parse_params = initial_parse_and_match_fields[child->name_];
    auto child_actual_parse_params = std::vector<std::string>{};

    // Remove all the params from parse method of this child
    // if these variables are the ones parent will match its children.
    for (auto pcons : child->parent_constraints_) {
      child_initial_parse_params.erase(pcons.first);
    }

    // Store unique vars from child_initial_parse_params to child_actual_parse_params
    // in the same order as fields are defined in the packets
    for (auto avf : available_fields) {
      if (child_initial_parse_params.find(avf) != child_initial_parse_params.end()) {
        child_actual_parse_params.push_back(avf);
      }
    }
    dependencies[child->name_] = child_actual_parse_params;
    FinalizeParseAndMatchFields(child, initial_parse_and_match_fields, available_fields);
  }
}
