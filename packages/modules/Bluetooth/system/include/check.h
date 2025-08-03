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

// On all old libchrome, CHECK is available via logging.h
// After 822064, this was moved into base/check.h and base/check_op.h
#if defined(BASE_VER) && BASE_VER >= 822064
#include <base/check.h> // CHECK/DCHECK
#include <base/check_op.h> // CHECK_LE/DCHECK_LE/CHECK/_GT, etc
#else
#include <base/logging.h>
#endif
