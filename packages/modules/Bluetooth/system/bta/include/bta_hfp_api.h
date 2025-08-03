/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef BTA_HFP_API_H
#define BTA_HFP_API_H

/* HFP Versions */
#define HFP_HSP_VERSION_UNKNOWN 0x0000
#define HFP_HSP_VERSION_DONT_CARE 0xFFFF
#define HFP_VERSION_1_1 0x0101
#define HFP_VERSION_1_5 0x0105
#define HFP_VERSION_1_6 0x0106
#define HFP_VERSION_1_7 0x0107
#define HFP_VERSION_1_8 0x0108
#define HFP_VERSION_1_9 0x0109

#define HSP_VERSION_1_0 0x0100
#define HSP_VERSION_1_2 0x0102

int get_default_hfp_version();

#endif /* BTA_HFP_API_H */
