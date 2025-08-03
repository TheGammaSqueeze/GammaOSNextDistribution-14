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

// Copyright 2014 Google Inc. All Rights Reserved.
// Author: csyoung@google.com (C. Sean Young)

#ifndef ICING_LEGACY_CORE_ICING_COMPAT_H_
#define ICING_LEGACY_CORE_ICING_COMPAT_H_

#ifdef __APPLE__
// iOS does not allow dlopen/dlclose/dlsym.
#define ICING_ALLOW_DYNAMIC_EXTENSIONS 0
// Xcode 6.3/LLVM 3.6 removed std::hash specialization for std::basic_string,
// so we need the versions from util/hash/hash.h. The implementations in
// util/hash/* have added an AVOID_TRADE_SECRET_CODE macro that's defined on
// Apple (and some other client targets) to prevent leaking proprietary hash
// code.
#else
#define ICING_ALLOW_DYNAMIC_EXTENSIONS 1
#endif  // __APPLE__

#endif  // ICING_LEGACY_CORE_ICING_COMPAT_H_
