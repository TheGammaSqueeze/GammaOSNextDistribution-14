/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_DATETIME_COMPONENT_BUILDER_H_
#define LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_DATETIME_COMPONENT_BUILDER_H_

#include "annotator/types.h"

namespace libtextclassifier3 {

// Builder class to construct the DatetimeComponents and make the test readable.
class DatetimeComponentsBuilder {
 public:
  DatetimeComponentsBuilder Add(DatetimeComponent::ComponentType type,
                                int value);

  DatetimeComponentsBuilder Add(
      DatetimeComponent::ComponentType type, int value,
      DatetimeComponent::RelativeQualifier relative_qualifier,
      int relative_count);

  std::vector<DatetimeComponent> Build();

 private:
  DatetimeComponentsBuilder AddComponent(
      const DatetimeComponent& datetime_component);
  std::vector<DatetimeComponent> datetime_components_;
};
}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_ANNOTATOR_DATETIME_TESTING_DATETIME_COMPONENT_BUILDER_H_
