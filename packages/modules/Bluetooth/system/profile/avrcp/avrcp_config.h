/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include "stack/include/avrc_api.h"

// Supported Features for AVRCP Version 1.6
#ifndef AVRCP_SUPF_TG_1_6
#define AVRCP_SUPF_TG_1_6                          \
  (AVRC_SUPF_TG_CAT1 | AVRC_SUPF_TG_MULTI_PLAYER | \
   AVRC_SUPF_TG_BROWSE) /* TODO: | AVRC_SUPF_TG_APP_SETTINGS) */
#endif

// Supported Features for AVRCP Version 1.5
#ifndef AVRCP_SUPF_TG_1_5
#define AVRCP_SUPF_TG_1_5                          \
  (AVRC_SUPF_TG_CAT1 | AVRC_SUPF_TG_MULTI_PLAYER | \
   AVRC_SUPF_TG_BROWSE)
#endif

// Supported Features for AVRCP Version 1.4
#ifndef AVRCP_SUPF_TG_1_4
#define AVRCP_SUPF_TG_1_4                          \
  (AVRC_SUPF_TG_CAT1 | AVRC_SUPF_TG_MULTI_PLAYER | \
   AVRC_SUPF_TG_BROWSE)
#endif

// Supported Features for AVRCP Version 1.3 "Compatibility Mode"
#ifndef AVRCP_SUPF_TG_1_3
#define AVRCP_SUPF_TG_1_3 AVRC_SUPF_TG_CAT1
#endif

/**
 * Default Supported Feature bits for AVRCP Controller
 */
#ifndef AVRCP_SUPF_TG
#define AVRCP_SUPF_TG_DEFAULT AVRCP_SUPF_TG_1_4
#endif

/**
 * Supported Feature for AVRCP tartget control
 */
#ifndef AVRCP_SUPF_TG_CT
#define AVRCP_SUPF_TG_CT AVRC_SUPF_CT_CAT2
#endif
