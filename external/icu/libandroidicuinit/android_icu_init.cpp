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

#include <stdbool.h>

#include "androidicuinit/android_icu_init.h"
#include "IcuRegistration.h"

void android_icu_init() {
    bool runAndroidInit = false;

    // We know that the environment variables are exported early in init.environ.rc on Android.
    #ifdef __ANDROID__
      #ifdef ANDROID_ICU_NO_DAT
        // If we're intentionally building ICU on Android without the .dat file, no
        // need to run init.
        runAndroidInit = false;
      #else  // !ANDROID_ICU_NO_DAT
        runAndroidInit = true;
      #endif
    #else // ART host testing environment has these env variables set.
    runAndroidInit = getenv("ANDROID_DATA") != NULL &&
                     getenv("ANDROID_TZDATA_ROOT") != NULL &&
                     getenv("ANDROID_I18N_ROOT") != NULL;
    #endif

    if (runAndroidInit) {
        if (!android_icu_is_registered()) {
            android_icu_register();
        } else {
            AICU_LOGE("libicuuc has already been initialized but android_icu_init() is called.");
        }
    }
}

void android_icu_cleanup() {
    if (android_icu_is_registered()) {
        android_icu_deregister();
    } else {
        AICU_LOGW("libicuuc is not initialized and possibly never used, "
              "but android_icu_cleanup() is called.");
    }
}
