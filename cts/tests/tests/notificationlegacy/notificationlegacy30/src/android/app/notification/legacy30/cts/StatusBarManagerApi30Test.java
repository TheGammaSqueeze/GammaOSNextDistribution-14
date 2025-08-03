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

package android.app.notification.legacy30.cts;

import static org.junit.Assume.assumeFalse;

import android.app.StatusBarManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class StatusBarManagerApi30Test {
    private StatusBarManager mStatusBarManager;
    private Context mContext;

    private boolean isWatch() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        assumeFalse("Status bar service not supported", isWatch());
        mStatusBarManager = mContext.getSystemService(StatusBarManager.class);
    }

    @Test
    public void testCollapsePanels_withoutStatusBarPermission_doesNotThrow() throws Exception {
        mStatusBarManager.collapsePanels();

        // Nothing thrown, passed
    }

    @Test
    public void testTogglePanel_withoutStatusBarPermission_doesNotThrow() throws Exception {
        mStatusBarManager.togglePanel();

        // Nothing thrown, passed
    }

    @Test
    public void testHandleSystemKey_withoutStatusBarPermission_doesNotThrow() throws Exception {
        mStatusBarManager.handleSystemKey(
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP));

        // Nothing thrown, passed
    }
}
