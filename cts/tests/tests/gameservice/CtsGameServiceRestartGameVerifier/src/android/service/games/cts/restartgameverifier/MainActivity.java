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

package android.service.games.cts.restartgameverifier;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public final class MainActivity extends AppCompatActivity {

    private static final String TIMES_STARTED_KEY = "times_started_key";
    private static final String HAS_SAVED_INSTANCE_STATE_KEY = "has_saved_instance_state_key";

    private int incrementTimesStarted() {
        SharedPreferences sharedPrefs = getPreferences(Context.MODE_PRIVATE);

        int timesStarted = sharedPrefs.getInt(TIMES_STARTED_KEY, 0) + 1;

        sharedPrefs.edit().putInt(TIMES_STARTED_KEY, timesStarted).commit();

        return timesStarted;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_layout);

        final TextView timesStartedView = findViewById(R.id.times_started);
        timesStartedView.setText(Integer.toString(incrementTimesStarted()));


        boolean hasSavedInstanceState = false;
        if (savedInstanceState != null) {
            hasSavedInstanceState = savedInstanceState.getBoolean(HAS_SAVED_INSTANCE_STATE_KEY);
        }
        final TextView hasSavedInstanceStateView = findViewById(R.id.has_saved_instance_state);
        hasSavedInstanceStateView.setText(Boolean.toString(hasSavedInstanceState));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(HAS_SAVED_INSTANCE_STATE_KEY, true);
    }
}
