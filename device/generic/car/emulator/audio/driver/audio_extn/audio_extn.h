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

#ifndef AUDIO_EXTN_H
#define AUDIO_EXTN_H

#ifdef __cplusplus
extern "C" {
#endif

#include "audio_hw.h"
#include <cutils/str_parms.h>

#ifndef HFP_ENABLED
#define audio_extn_hfp_set_parameters(adev, parms) (0)
#else
int audio_extn_hfp_set_parameters(struct generic_audio_device *adev,
                                           struct str_parms *parms);
#endif

#ifdef __cplusplus
}
#endif

#endif /* AUDIO_EXTN_H */
