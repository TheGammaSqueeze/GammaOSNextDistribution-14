/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef __EXYNOS_AUDIOHAL_LOG_H__
#define __EXYNOS_AUDIOHAL_LOG_H__


char * audiomode_table[AUDIO_MODE_CNT] = {
    [AUDIO_MODE_NORMAL]             = "normal mode",
    [AUDIO_MODE_RINGTONE]           = "ringtone mode",
    [AUDIO_MODE_IN_CALL]            = "in_call mode",
    [AUDIO_MODE_IN_COMMUNICATION]   = "in_comm mode",
};

char * callmode_table[CALL_MODE_CNT] = {
    [CALL_OFF]      = "Call Off",
    [VOICE_CALL]    = "Voice Call Mode",
    [VOLTE_CALL]    = "VoLTE Call Mode",
    [VOWIFI_CALL]   = "VoWiFi Call Mode",
};

#endif  // __EXYNOS_AUDIOHAL_LOG_H__