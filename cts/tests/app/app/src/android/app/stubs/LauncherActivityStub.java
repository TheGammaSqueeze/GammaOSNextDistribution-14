/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.stubs;

import android.app.LauncherActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ListView;

public final class LauncherActivityStub extends LauncherActivity {

    private static final String TAG = LauncherActivityStub.class.getSimpleName();

    public boolean isOnCreateCalled;
    public boolean isOnListItemClick;
    // For testing LauncherActivity#getTargetIntent()
    private Intent mSuperIntent;

    public Intent getSuperIntent() {
        return mSuperIntent;
    }

    @Override
    protected Intent getTargetIntent() {
        mSuperIntent = super.getTargetIntent();
        Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
        targetIntent.addCategory(Intent.CATEGORY_FRAMEWORK_INSTRUMENTATION_TEST);
        targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return targetIntent;
    }

    @Override
    public Intent intentForPosition(int position) {
        return super.intentForPosition(position);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isOnCreateCalled = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "onCreate() user="  + getUser() + ", display=" + getDisplayId());
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Log.d(TAG, "onListItemClick(): position=" + position + " , id=" + id);
        isOnListItemClick = true;
    }
}
