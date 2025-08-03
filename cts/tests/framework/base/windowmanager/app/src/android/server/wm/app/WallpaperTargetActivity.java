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

package android.server.wm.app;

import static android.server.wm.app.Components.WallpaperTargetActivity.EXTRA_ENABLE_WALLPAPER_TOUCH;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

public class WallpaperTargetActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean enableTouch =
                getIntent().getBooleanExtra(EXTRA_ENABLE_WALLPAPER_TOUCH, true);
        WindowManager.LayoutParams p = getWindow().getAttributes();
        p.flags |= FLAG_SHOW_WALLPAPER;
        p.setWallpaperTouchEventsEnabled(enableTouch /* enable */);
    }
}
