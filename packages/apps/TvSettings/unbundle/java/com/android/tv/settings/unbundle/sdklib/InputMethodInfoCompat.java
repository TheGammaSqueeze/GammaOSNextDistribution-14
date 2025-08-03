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

import android.view.inputmethod.InputMethodInfo;

import java.util.ArrayList;
import java.util.List;

public class InputMethodInfoCompat {
    public InputMethodInfoCompat(InputMethodInfo inputMethodInfo) {
        mInputMethodInfo = inputMethodInfo;
    }

    private final InputMethodInfo mInputMethodInfo;

    public boolean isAuxiliaryIme() {
        return mInputMethodInfo.isAuxiliaryIme();
    }

    public boolean isSystem() {
        return mInputMethodInfo.isSystem();
    }

    public static List<InputMethodInfoCompat> convert(List<InputMethodInfo> infos) {
        List<InputMethodInfoCompat> list = new ArrayList<>();
        for (InputMethodInfo info : infos) {
            list.add(new InputMethodInfoCompat(info));
        }
        return list;
    }

    public static List<InputMethodInfo> reverse(List<InputMethodInfoCompat> infos) {
        List<InputMethodInfo> list = new ArrayList<>();
        for (InputMethodInfoCompat info : infos) {
            list.add(info.mInputMethodInfo);
        }
        return list;
    }
}
