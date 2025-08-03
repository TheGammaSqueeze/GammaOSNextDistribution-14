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

package com.android.car.notification.headsup;

import android.app.Activity;
import android.os.Bundle;
import android.testing.TestableContext;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.notification.R;

import org.junit.Rule;

public class HeadsUpContainerViewTestActivity extends Activity {
    private HeadsUpContainerView mHeadsUpContainerView;
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext.getOrCreateTestableResources().addOverride(R.bool.config_focusHUNWhenShown,
                /* value= */ false);
        mHeadsUpContainerView = new HeadsUpContainerView(mContext);
        setContentView(mHeadsUpContainerView);
    }

    public HeadsUpContainerView getHeadsUpContainerView() {
        return mHeadsUpContainerView;
    }
}
