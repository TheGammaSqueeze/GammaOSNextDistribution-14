/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.app.cts.wallpapers;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Window;
import android.widget.FrameLayout;

import javax.annotation.Nullable;

/* This activity is added to simulate switching window focus by
* launching a second app on top of the first activity
*/
public class WallpaperOverlayTestActivity extends Activity {
    Context mContext;
    WallpaperManager mWallpaperManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this.getApplicationContext();
        mWallpaperManager = WallpaperManager.getInstance(mContext);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);

        setContentView(frameLayout, layoutParams);
    }

    public void sendWallpaperCommand(String command) {
        Window window = this.getWindow();
        IBinder windowToken = window.getDecorView().getWindowToken();
        mWallpaperManager.sendWallpaperCommand(
                windowToken, WallpaperManager.COMMAND_TAP, 50, 50, 0, null);
    }
}
