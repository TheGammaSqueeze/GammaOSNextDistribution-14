/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "dex_file_layout.h"

#include "base/bit_utils.h"
#include "base/mman.h"
#include "dex_file.h"

namespace art {

std::ostream& operator<<(std::ostream& os, const DexLayoutSection& section) {
  for (size_t i = 0; i < static_cast<size_t>(LayoutType::kLayoutTypeCount); ++i) {
    const DexLayoutSection::Subsection& part = section.parts_[i];
    os << static_cast<LayoutType>(i) << "("
       << part.start_offset_ << "-" << part.end_offset_ << ") ";
  }
  return os;
}

std::ostream& operator<<(std::ostream& os, const DexLayoutSections& sections) {
  for (size_t i = 0; i < static_cast<size_t>(DexLayoutSections::SectionType::kSectionCount); ++i) {
    os << static_cast<DexLayoutSections::SectionType>(i) << ":" << sections.sections_[i] << "\n";
  }
  return os;
}

}  // namespace art
