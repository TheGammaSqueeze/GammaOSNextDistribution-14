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

package android.server.wm.backgroundactivity.appa;

import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;

public class VirtualDisplayActivity extends RelaunchingActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Components appA = Components.get(getApplicationContext());
        boolean usePublicPresentation = getIntent().getBooleanExtra(
                appA.VIRTUAL_DISPLAY_ACTIVITY_EXTRA.USE_PUBLIC_PRESENTATION, false);
        if (usePublicPresentation) {
            createPublicVirtualDisplayAndShowPresentation();
        } else {
            createVirtualDisplayAndShowPresentation();
        }
    }

    private void createPublicVirtualDisplayAndShowPresentation() {
        VirtualDisplay virtualDisplay = getSystemService(DisplayManager.class).createVirtualDisplay(
                "VirtualDisplay1", 10, 10, 10, null,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        + DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                        + DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        new Presentation(getBaseContext(), virtualDisplay.getDisplay()).show();
    }

    private void createVirtualDisplayAndShowPresentation() {
        VirtualDisplay virtualDisplay = getSystemService(DisplayManager.class).createVirtualDisplay(
                "VirtualDisplay1", 10, 10, 10, null, 0);
        new Presentation(getBaseContext(), virtualDisplay.getDisplay()).show();
    }
}
