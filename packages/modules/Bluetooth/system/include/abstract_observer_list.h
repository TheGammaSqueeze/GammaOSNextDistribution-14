//
//  Copyright 2021 Google, Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at:
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#pragma once

#include <base/observer_list.h>

namespace btbase {
#if defined(BASE_VER) && BASE_VER > 780000

// Checked Observers aren't supported in the older libchrome so use unchecked
// ones instead to preserve behavior.
template <class ObserverType>
class AbstractObserverList
    : public base::ObserverList<ObserverType>::Unchecked {};

#else

template <class ObserverType>
class AbstractObserverList : public base::ObserverList<ObserverType> {};

#endif
}  // namespace btbase
