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

package android.car.cts.builtin.view;

import static android.car.cts.builtin.app.DisplayUtils.VirtualDisplaySession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.car.builtin.view.DisplayHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public final class DisplayHelperTest {

    private static final String TAG = DisplayHelperTest.class.getSimpleName();

    // the constant comes from com.android.server.display.LocalDisplayAdapter.UNIQUE_ID_PREFIX
    private static final String DISPLAY_ID_PREFIX_LOCAL = "local:";
    // the constant comes from com.android.server.display.VirtualDisplayAdapter.UNIQUE_ID_PREFIX
    private static final String DISPLAY_ID_PREFIX_VIRTUAL = "virtual:";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    public void testLocalDisplayPhysicalPort() throws Exception {
        // setup
        ArrayList<Display> allLocalDisplays = getAllLocalDisplays();

        // assert that there is at least one local display
        assertThat(allLocalDisplays.size()).isGreaterThan(0);

        // execution and assertion
        for (Display d : allLocalDisplays) {
            int physicalPort = DisplayHelper.getPhysicalPort(d);
            Log.d(TAG, "Display Physical Port: " + physicalPort);
            assertThat(physicalPort).isNotEqualTo(DisplayHelper.INVALID_PORT);

            String uniqueId = DisplayHelper.getUniqueId(d);
            assertThat(physicalPort).isEqualTo(getPhysicalPortFromId(uniqueId));
        }
    }

    @Test
    public void testVirtualDisplayPhysicalPort() throws Exception {
        // check the assumption
        String requiredFeature = PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
        assumeTrue(mContext.getPackageManager().hasSystemFeature(requiredFeature));

        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            Display vDisplay =
                    session.createDisplayWithDefaultDisplayMetricsAndWait(mContext, true);
            assertThat(DisplayHelper.getPhysicalPort(vDisplay))
                    .isEqualTo(DisplayHelper.INVALID_PORT);
        }
    }

    @Test
    public void testDisplayType() throws Exception {
        // check the assumption
        String requiredFeature = PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
        // failure indicates that the device is not able to support multi-display and can
        // not create virtual display to check virtual type
        assumeTrue(mContext.getPackageManager().hasSystemFeature(requiredFeature));

        // setup
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);

        // execute and assert
        // check physical (external or internal) type
        int displayType = DisplayHelper.getType(defaultDisplay);
        assertThat(displayType).isAnyOf(DisplayHelper.TYPE_INTERNAL, DisplayHelper.TYPE_EXTERNAL);

        // check virtual type
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            Display vDisplay = session.createDisplayWithDefaultDisplayMetricsAndWait(mContext,
                    /* isPrivate= */ true);
            assertThat(DisplayHelper.getType(vDisplay)).isEqualTo(DisplayHelper.TYPE_VIRTUAL);
        }
    }

    private ArrayList<Display> getAllLocalDisplays() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display[] allDisplays = displayManager.getDisplays();

        ArrayList<Display> localDisplays = new ArrayList<>();
        for (Display d : allDisplays) {
            if (isDisplayLocal(d)) {
                localDisplays.add(d);
            }
        }

        return localDisplays;
    }

    private boolean isDisplayLocal(Display display) {
        String uniqueId = DisplayHelper.getUniqueId(display);
        return uniqueId.startsWith(DISPLAY_ID_PREFIX_LOCAL);
    }

    private int getPhysicalPortFromId(String displayUniqueId) throws Exception {
        if (displayUniqueId == null) {
            throw new IllegalArgumentException("null displayId string");
        }

        int startIndex = DISPLAY_ID_PREFIX_LOCAL.length();
        long physicalDisplayId = Long.parseLong(displayUniqueId.substring(startIndex));
        return (int) (physicalDisplayId & 0xFF);
    }
}
