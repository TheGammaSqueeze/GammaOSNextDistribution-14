// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "icing/result/projector.h"

#include <algorithm>

#include "icing/proto/document.pb.h"

namespace icing {
namespace lib {

namespace projector {

void Project(const std::vector<ProjectionTree::Node>& projection_tree,
             DocumentProto* document) {
  int num_kept = 0;
  for (int cur_pos = 0; cur_pos < document->properties_size(); ++cur_pos) {
    PropertyProto* prop = document->mutable_properties(cur_pos);
    auto itr = std::find_if(projection_tree.begin(), projection_tree.end(),
                            [&prop](const ProjectionTree::Node& node) {
                              return node.name == prop->name();
                            });
    if (itr == projection_tree.end()) {
      // Property is not present in the projection tree. Just skip it.
      continue;
    }
    // This property should be kept.
    document->mutable_properties()->SwapElements(num_kept, cur_pos);
    ++num_kept;
    if (itr->children.empty()) {
      // A field mask does refer to this property, but it has no children. So
      // we should take the entire property, with all of its
      // subproperties/values
      continue;
    }
    // The field mask refers to children of this property. Recurse through the
    // document values that this property holds and project the children
    // requested by this field mask.
    for (DocumentProto& subproperty : *(prop->mutable_document_values())) {
      Project(itr->children, &subproperty);
    }
  }
  document->mutable_properties()->DeleteSubrange(
      num_kept, document->properties_size() - num_kept);
}

}  // namespace projector

}  // namespace lib
}  // namespace icing
