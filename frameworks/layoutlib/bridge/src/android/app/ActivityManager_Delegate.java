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

package android.app;

import com.android.internal.os.IResultReceiver;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.app.ActivityManager.MemoryInfo;
import android.app.ActivityManager.PendingIntentInfo;
import android.app.ActivityManager.ProcessErrorStateInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.LocusId;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.os.IProgressListener;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.StrictMode.ViolationInfo;
import android.os.WorkSource;

import java.util.List;

public class ActivityManager_Delegate {
    private static final IActivityManager sStubManager = new IActivityManager.Default();

    @LayoutlibDelegate
    public static IActivityManager getService() {
        return sStubManager;
    }
}
