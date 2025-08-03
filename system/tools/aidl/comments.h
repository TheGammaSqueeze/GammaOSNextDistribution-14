/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <iostream>
#include <optional>
#include <string>
#include <vector>

namespace android {
namespace aidl {

// Represents a single comment
struct Comment {
  enum class Type { LINE, BLOCK };
  Type type;
  std::string body;

  Comment(const std::string& body);

  // for GTest assertions
  friend inline bool operator==(const Comment& lhs, const Comment& rhs) {
    return lhs.body == rhs.body;
  }
  friend std::ostream& operator<<(std::ostream& out, const Comment& c) { return out << c.body; }
};

// TODO: smoreland says don't do this - instead use 'std::vector<Comment>' everywhere
using Comments = std::vector<Comment>;

bool HasHideInComments(const Comments& comments);

struct Deprecated {
  std::string note;  // can be empty("")
};

std::optional<Deprecated> FindDeprecated(const Comments& comments);

std::string FormatCommentsForJava(const Comments& comments);

}  // namespace aidl
}  // namespace android
