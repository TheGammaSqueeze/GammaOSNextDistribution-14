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

package android.quicksettings.cts;

import android.content.ComponentName;
import android.service.quicksettings.TileService;

public class TestTileService extends TileService {

    public static final String TAG = "TestTileService";
    public static final String PKG = "android.app.stubs";
    public static final int ICON_ID = R.drawable.robot;

    public static String getId() {
        return String.format("%s/%s", TestTileService.class.getPackage().getName(),
                TestTileService.class.getName());
    }

    public static ComponentName getComponentName() {
        return new ComponentName(TestTileService.class.getPackage().getName(),
                TestTileService.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        CurrentTestState.setCurrentInstance(this);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        CurrentTestState.setCurrentInstance(null);
    }

    @Override
    public void onClick() {
        super.onClick();
        CurrentTestState.setTileHasBeenClicked(true);
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        CurrentTestState.setTileServiceClass(getClass().getName());
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        CurrentTestState.reset();
    }
}
