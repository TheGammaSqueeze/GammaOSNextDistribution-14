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

package android.smartspace.cts;

import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.Icon;
import android.app.smartspace.uitemplatedata.TapAction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

public class SmartspaceTestUtils {
    public static SmartspaceTarget getBasicSmartspaceTarget(String id, ComponentName componentName,
            UserHandle userHandle) {
        return new SmartspaceTarget.Builder(id, componentName, userHandle).build();
    }

    public static ComponentName getTestComponentName() {
        return new ComponentName("package name", "class name");
    }

    public static Icon createSmartspaceIcon(String contentDescription) {
        android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
        return new Icon.Builder(icon).setContentDescription(contentDescription).build();
    }

    public static TapAction createSmartspaceTapAction(Context context, CharSequence id) {
        Bundle extras = new Bundle();
        extras.putString("key", "value");

        Intent intent = new Intent();
        PendingIntent pendingIntent = TaskStackBuilder.create(context)
                .addNextIntent(intent)
                .getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE);

        return new TapAction.Builder(id)
                .setIntent(intent)
                .setPendingIntent(pendingIntent)
                .setUserHandle(Process.myUserHandle())
                .setExtras(extras).build();
    }
}
