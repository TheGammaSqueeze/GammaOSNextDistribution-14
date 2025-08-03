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

#include "annotator/datetime/testing/datetime-component-builder.h"

namespace libtextclassifier3 {

DatetimeComponentsBuilder DatetimeComponentsBuilder::Add(
    DatetimeComponent::ComponentType type, int value) {
  DatetimeComponent component;
  component.component_type = type;
  component.value = value;
  return AddComponent(component);
}

DatetimeComponentsBuilder DatetimeComponentsBuilder::Add(
    DatetimeComponent::ComponentType type, int value,
    DatetimeComponent::RelativeQualifier relative_qualifier,
    int relative_count) {
  DatetimeComponent component;
  component.component_type = type;
  component.value = value;
  component.relative_qualifier = relative_qualifier;
  component.relative_count = relative_count;
  return AddComponent(component);
}

std::vector<DatetimeComponent> DatetimeComponentsBuilder::Build() {
  return std::move(datetime_components_);
}

DatetimeComponentsBuilder DatetimeComponentsBuilder::AddComponent(
    const DatetimeComponent& datetime_component) {
  datetime_components_.push_back(datetime_component);
  return *this;
}
}  // namespace libtextclassifier3
