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

package com.android.cts.verifier.controls;

import android.app.ActivityTaskManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.widget.TextView;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.ManifestTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class ControlsPanelVerifierTestList extends PassFailButtons.TestListActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();

        if (ActivityTaskManager.supportsMultiWindow(this)) {

            getPassButton().setEnabled(false);

            ManifestTestListAdapter adapter = new ManifestTestListAdapter(this,
                    getClass().getName());
            setTestListAdapter(adapter);

            adapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    updatePassButton();
                }

                @Override
                public void onInvalidated() {
                    updatePassButton();
                }
            });
        } else {
            TextView text = findViewById(android.R.id.empty);
            text.setText(R.string.controls_panel_not_supported);
            setTestListAdapter(new ArrayTestListAdapter(this));
            updatePassButton();
        }
    }

}
