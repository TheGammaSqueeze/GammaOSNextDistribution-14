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

package android.server.wm;

import android.app.DreamManager;
import android.content.ComponentName;
import android.content.Context;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.SystemUtil;

public class DreamCoordinator {
    private final DreamManager mDreamManager;

    private boolean mSetup;
    private boolean mDefaultDreamServiceEnabled;

    public DreamCoordinator(Context context) {
        mDreamManager = context.getSystemService(DreamManager.class);
    }

    public final ComponentName getDreamActivityName(ComponentName dream) {
        return new ComponentName(dream.getPackageName(),
                "android.service.dreams.DreamActivity");
    }

    /**
     * Sets up the system to show dreams. If system doesn't support dreams, returns {@code false}.
     */
    public void setup() {
        assumeTrue(mDreamManager.areDreamsSupported());

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mDefaultDreamServiceEnabled = mDreamManager.isScreensaverEnabled();
            if (!mDefaultDreamServiceEnabled) {
                mDreamManager.setScreensaverEnabled(true);
            }
        });

        mSetup = true;
    }

    /**
     * Restores any settings changed by {@link #setup()}.
     */
    public void restoreDefaults() {
        // If we have not set up the coordinator, do not do anything.
        if (!mSetup) {
            return;
        }

        mSetup = false;

        // Nothing to restore if dreams are enabled by default.
        if (mDefaultDreamServiceEnabled) {
            return;
        }

        SystemUtil.runWithShellPermissionIdentity(
                () -> mDreamManager.setScreensaverEnabled(false));
    }

    public void startDream() {
        SystemUtil.runWithShellPermissionIdentity(() -> mDreamManager.startDream());
    }

    public void stopDream() {
        SystemUtil.runWithShellPermissionIdentity(mDreamManager::stopDream);
    }

    public ComponentName setActiveDream(ComponentName dream) {
        SystemUtil.runWithShellPermissionIdentity(() -> mDreamManager.setActiveDream(dream));
        return getDreamActivityName(dream);
    }

    public ComponentName setSystemDream(ComponentName dream) {
        SystemUtil.runWithShellPermissionIdentity(() ->
                mDreamManager.setSystemDreamComponent(dream));
        return dream == null ? null : getDreamActivityName(dream);
    }

    public void setDreamOverlay(ComponentName overlay) {
        SystemUtil.runWithShellPermissionIdentity(() -> mDreamManager.setDreamOverlay(overlay));
    }

    public boolean isDreaming() {
        return SystemUtil.runWithShellPermissionIdentity(mDreamManager::isDreaming);
    }
}
