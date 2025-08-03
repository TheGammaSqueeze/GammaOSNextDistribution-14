/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context;

import com.android.internal.app.LocalePicker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocalePickerCompat {
    public static void updateLocale(Locale locale) {
        LocalePicker.updateLocale(locale);
    }

    public static class LocaleInfoCompat {
        private final LocalePicker.LocaleInfo mLocaleInfo;

        LocaleInfoCompat(LocalePicker.LocaleInfo localeInfo) {
            mLocaleInfo = localeInfo;
        }

        public Locale getLocale() {
            return mLocaleInfo.getLocale();
        }
    }

    public static List<LocaleInfoCompat> getAllAssetLocales(
            Context context, boolean isDeveloperMode) {
        List<LocaleInfoCompat> result = new ArrayList<>();
        for (LocalePicker.LocaleInfo info : LocalePicker.getAllAssetLocales(context,
                isDeveloperMode)) {
            // object is of type LocalePicker.LocaleInfo
            result.add(new LocaleInfoCompat(info));
        }
        return result;
    }
}
