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

package android.translation.cts;

import android.app.Activity;
import android.os.Bundle;
import android.translation.cts.views.VirtualContainerView;
import android.view.autofill.AutofillId;

import java.util.ArrayList;
import java.util.List;

/**
 * A activity that contains a VirtualContainerView used for translation testing.
 */
public class VirtualContainerViewActivity extends Activity {

    VirtualContainerView mVirtualContainerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.virtual_container_view_activity);

        mVirtualContainerView = findViewById(R.id.virtual_container_view);
    }

    List<AutofillId> getViewsForTranslation() {
        AutofillId autofillId = mVirtualContainerView.getAutofillId();
        final List<AutofillId> views = new ArrayList<>();
        // TODO: get from Content Capture
        AutofillId virtualChild1 = new AutofillId(autofillId, 12345L, 0);
        AutofillId virtualChild2 = new AutofillId(autofillId, 12346L, 0);
        views.add(virtualChild1);
        views.add(virtualChild2);
        return views;
    }

    VirtualContainerView getVirtualContainerView() {
        return mVirtualContainerView;
    }
}