/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */
package android.systemui.cts;

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;

/**
 * An activity that exercises SYSTEM_UI_FLAG_LIGHT_STATUS_BAR and
 * SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.
 */
public class LightBarActivity extends LightBarBaseActivity {

    @Override
    protected void onCreate(Bundle bundle){
        super.onCreate(bundle);

        // Make the window extend into the waterfall insets.
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }

    public void setLightStatusBarLegacy(boolean lightStatusBar) {
        setLightBarLegacy(lightStatusBar, View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    public void setLightNavigationBarLegacy(boolean lightNavigationBar) {
        setLightBarLegacy(lightNavigationBar, View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void setLightBarLegacy(boolean light, int systemUiFlag) {
        int vis = getWindow().getDecorView().getSystemUiVisibility();
        if (light) {
            vis |= systemUiFlag;
        } else {
            vis &= ~systemUiFlag;
        }
        getWindow().getDecorView().setSystemUiVisibility(vis);
    }

    public void setLightStatusBarAppearance(boolean lightStatusBar) {
        setLightBarAppearance(lightStatusBar, APPEARANCE_LIGHT_STATUS_BARS);
    }

    public void setLightNavigationBarAppearance(boolean lightNavigationBar) {
        setLightBarAppearance(lightNavigationBar, APPEARANCE_LIGHT_NAVIGATION_BARS);
    }

    private void setLightBarAppearance(boolean light, int appearanceFlag) {
        final WindowInsetsController controller =
                getWindow().getDecorView().getWindowInsetsController();
        int appearance = controller.getSystemBarsAppearance();
        if (light) {
            appearance |= appearanceFlag;
        } else {
            appearance &= ~appearanceFlag;
        }
        controller.setSystemBarsAppearance(appearance, appearanceFlag);
    }
}
