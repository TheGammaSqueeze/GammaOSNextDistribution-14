/*
 * Copyright (C) 2022 The Android Open Source Project
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
#ifndef GD_RUST_TOPSHIM_COMMON_TYPE_ALIAS_H
#define GD_RUST_TOPSHIM_COMMON_TYPE_ALIAS_H

/*
 * Declare type aliases in the topshim namespace in this file.
 *
 * The type declarations in cxx bridge blocks are bound to the namespace of the
 * block. Since in topshim we always put the bridge codes in namespace
 * `bluetooth::topshim::rust`, to reuse the existing types in GD it's necessary
 * to define the aliases in the topshim namespace.
 */

#include "types/raw_address.h"

namespace bluetooth {
namespace topshim {
namespace rust {

using RawAddress = ::RawAddress;

}  // namespace rust
}  // namespace topshim
}  // namespace bluetooth

#endif  // GD_RUST_TOPSHIM_COMMON_TYPE_ALIAS_H
