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

#ifndef CHPP_PLATFORM_LINK_H_
#define CHPP_PLATFORM_LINK_H_

#ifdef __cplusplus
extern "C" {
#endif

#define CHPP_TEST_LINK_TX_MTU_BYTES ((size_t)1280)
#define CHPP_TEST_LINK_RX_MTU_BYTES ((size_t)1280)

struct ChppTestLinkState {
  void *fake;
  uint8_t txBuffer[CHPP_TEST_LINK_TX_MTU_BYTES];
  const struct ChppTransportState *transportContext;
};

#ifdef __cplusplus
}
#endif

#endif  // CHPP_PLATFORM_LINK_H_
