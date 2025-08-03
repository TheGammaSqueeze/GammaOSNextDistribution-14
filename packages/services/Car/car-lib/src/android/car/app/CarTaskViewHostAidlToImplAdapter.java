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

package android.car.app;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;

/**
 * An adapter that adapts {@link ICarTaskViewHost} to {@link CarTaskViewHost}.
 */
final class CarTaskViewHostAidlToImplAdapter extends ICarTaskViewHost.Stub {
    private final CarTaskViewHost mCarTaskViewHost;

    CarTaskViewHostAidlToImplAdapter(CarTaskViewHost carTaskViewHost) {
        mCarTaskViewHost = carTaskViewHost;
    }

    @Override
    public void release() {
        mCarTaskViewHost.release();
    }

    @Override
    public void startActivity(
            PendingIntent pendingIntent, Intent intent, Bundle options, Rect launchBounds) {
        mCarTaskViewHost.startActivity(pendingIntent, intent, options, launchBounds);
    }

    @Override
    public void createRootTask(int displayId) {
        mCarTaskViewHost.createRootTask(displayId);
    }

    @Override
    public void createLaunchRootTask(int displayId, boolean embedHomeTask, boolean embedRecentsTask,
            boolean embedAssistantTask) {
        mCarTaskViewHost.createLaunchRootTask(displayId, embedHomeTask, embedRecentsTask,
                embedAssistantTask);
    }

    @Override
    public void notifySurfaceCreated(SurfaceControl control) {
        mCarTaskViewHost.notifySurfaceCreated(control);
    }

    @Override
    public void setWindowBounds(Rect windowBoundsOnScreen) {
        mCarTaskViewHost.setWindowBounds(windowBoundsOnScreen);
    }

    @Override
    public void notifySurfaceDestroyed() {
        mCarTaskViewHost.notifySurfaceDestroyed();
    }

    @Override
    public void showEmbeddedTask() {
        mCarTaskViewHost.showEmbeddedTask();
    }

    @Override
    public void setTaskVisibility(boolean visibility) {
        mCarTaskViewHost.setTaskVisibility(visibility);
    }

    @Override
    public void reorderTask(boolean onTop) {
        mCarTaskViewHost.reorderTask(onTop);
    }

    @Override
    public void addInsets(int index, int type, @NonNull Rect frame) {
        mCarTaskViewHost.addInsets(index, type, frame);
    }

    @Override
    public void removeInsets(int index, int type) {
        mCarTaskViewHost.removeInsets(index, type);
    }
}
