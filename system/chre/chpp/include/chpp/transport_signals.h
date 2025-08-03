/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHPP_TRANSPORT_SIGNALS_H_
#define CHPP_TRANSPORT_SIGNALS_H_

#include <stdint.h>

#include "macros.h"

#ifdef __cplusplus
extern "C" {
#endif

//! Signals to use in ChppNotifier in the CHPP transport work thread
//! (ran in chppWorkThreadStart()).
#define CHPP_TRANSPORT_SIGNAL_EXIT UINT32_C(1 << 0)
#define CHPP_TRANSPORT_SIGNAL_EVENT UINT32_C(1 << 1)

//! No data to process, just to trigger an iteration of the work thread so that
//! the next timeout can be recalculated
#define CHPP_TRANSPORT_SIGNAL_RECALC_TIMEOUT UINT32_C(1 << 2)

//! These bits are reserved for platform use (e.g. in the platform-specific
//! link layer implementation).
#define CHPP_TRANSPORT_SIGNAL_PLATFORM_MASK UINT32_C(0xFFFF0000)

#ifdef __cplusplus
}
#endif

#endif  // CHPP_TRANSPORT_SIGNALS_H_
