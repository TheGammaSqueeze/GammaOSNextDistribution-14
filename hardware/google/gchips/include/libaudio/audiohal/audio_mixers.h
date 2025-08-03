/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef AUDIO_MIXERS_H
#define AUDIO_MIXERS_H

/* Mixer Values Definition */
enum {
    MIXER_VALUE_OFF   = 0,
    MIXER_VALUE_ON,
};

#define MIXER_CTL_VAL_INVALID       -1

/* Specific Mixer Name */
// To use Virtual PCM Device for APCall
#define ABOX_APCALLBUFFTYPE_CONTROL_NAME    "ABOX PCM ext APCALL BUFFTYPE"


#endif /* AUDIO_MIXERS */
