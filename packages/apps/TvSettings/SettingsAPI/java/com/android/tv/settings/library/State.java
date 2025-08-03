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

package com.android.tv.settings.library;

import android.content.Intent;
import android.os.Bundle;

/*
 * Implement this to provide data for each settings screen.
 */
public interface State {
    void onAttach();

    void onCreate(Bundle extras);

    void onStart();

    void onResume();

    void onPause();

    void onStop();

    void onDestroy();

    void onDetach();

    boolean onPreferenceTreeClick(String[] key, boolean status);

    void onActivityResult(int requestCode, int resultCode, Intent data);

    boolean onPreferenceChange(String[] key, Object newValue);

    int getStateIdentifier();

    void onDisplayDialogPreference(String[] key);
}
