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

package android.hardware.display;


import com.android.internal.annotations.VisibleForTesting;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.hardware.OverlayProperties;
import android.hardware.display.DisplayManager.DisplayListener;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.util.Pair;
import android.view.Display;
import android.view.DisplayAdjustments;
import android.view.DisplayInfo;
import android.view.Surface;

import java.util.List;

public final class DisplayManagerGlobal {
    public static final int EVENT_DISPLAY_ADDED = 1;
    public static final int EVENT_DISPLAY_CHANGED = 2;
    public static final int EVENT_DISPLAY_REMOVED = 3;
    public static final int EVENT_DISPLAY_BRIGHTNESS_CHANGED = 4;

    private static DisplayManagerGlobal sInstance;

    @VisibleForTesting
    public DisplayManagerGlobal(IDisplayManager dm) {}

    public static DisplayManagerGlobal getInstance() {
        synchronized (DisplayManagerGlobal.class) {
            if (sInstance == null) {
                sInstance = new DisplayManagerGlobal(null);
            }
            return sInstance;
        }
    }

    public DisplayInfo getDisplayInfo(int displayId) {
        return null;
    }

    public int[] getDisplayIds() {
        return null;
    }

    public boolean isUidPresentOnDisplay(int uid, int displayId) {
        return false;
    }

    public Display getCompatibleDisplay(int displayId, DisplayAdjustments daj) {
        return null;
    }

    public Display getCompatibleDisplay(int displayId, Resources resources) {
        return null;
    }

    public Display getRealDisplay(int displayId) {
        return null;
    }

    public void registerDisplayListener(@NonNull DisplayListener listener,
            @Nullable Handler handler, long eventsMask) {}

    public void unregisterDisplayListener(DisplayListener listener) {}

    public void startWifiDisplayScan() {}

    public void stopWifiDisplayScan() {}

    public void connectWifiDisplay(String deviceAddress) {}

    public void pauseWifiDisplay() {}

    public void resumeWifiDisplay() {}

    public void disconnectWifiDisplay() {}

    public void renameWifiDisplay(String deviceAddress, String alias) {}

    public void forgetWifiDisplay(String deviceAddress) {}

    public WifiDisplayStatus getWifiDisplayStatus() {
        return null;
    }

    public void setUserDisabledHdrTypes(int[] userDisabledHdrTypes) {}

    public void setAreUserDisabledHdrTypesAllowed(boolean areUserDisabledHdrTypesAllowed) {}

    public boolean areUserDisabledHdrTypesAllowed() {
        return false;
    }

    public int[] getUserDisabledHdrTypes() {
        return null;
    }

    public void requestColorMode(int displayId, int colorMode) {}

    public VirtualDisplay createVirtualDisplay(@NonNull Context context, MediaProjection projection,
            @NonNull VirtualDisplayConfig virtualDisplayConfig, VirtualDisplay.Callback callback,
            Handler handler) {
        return null;
    }

    public void setVirtualDisplaySurface(IVirtualDisplayCallback token, Surface surface) {}

    public void resizeVirtualDisplay(IVirtualDisplayCallback token,
            int width, int height, int densityDpi) {}

    public void releaseVirtualDisplay(IVirtualDisplayCallback token) {}

    void setVirtualDisplayState(IVirtualDisplayCallback token, boolean isOn) {}

    public Point getStableDisplaySize() {
        return null;
    }

    public List<BrightnessChangeEvent> getBrightnessEvents(String callingPackage) {
        return null;
    }

    public BrightnessInfo getBrightnessInfo(int displayId) {
        return null;
    }

    public ColorSpace getPreferredWideGamutColorSpace() {
        return null;
    }

    public OverlayProperties getOverlaySupport() { return null; }

    public void setBrightnessConfigurationForUser(BrightnessConfiguration c, int userId,
            String packageName) {}

    public BrightnessConfiguration getBrightnessConfigurationForUser(int userId) {
        return null;
    }

    public BrightnessConfiguration getDefaultBrightnessConfiguration() {
        return null;
    }

    public boolean isMinimalPostProcessingRequested(int displayId) {
        return false;
    }

    public void setTemporaryBrightness(int displayId, float brightness) {}

    public void setBrightness(int displayId, float brightness) {}

    public float getBrightness(int displayId) {
        return 0.0f;
    }

    public void setTemporaryAutoBrightnessAdjustment(float adjustment) {}

    public Pair<float[], float[]> getMinimumBrightnessCurve() {
        return null;
    }

    public List<AmbientBrightnessDayStats> getAmbientBrightnessStats() {
        return null;
    }

    public void setShouldAlwaysRespectAppRequestedMode(boolean enabled) {}

    public boolean shouldAlwaysRespectAppRequestedMode() {
        return false;
    }

    public void setRefreshRateSwitchingType(int newValue) {}

    public int getRefreshRateSwitchingType() {
        return 0;
    }

    public static final String CACHE_KEY_DISPLAY_INFO_PROPERTY =
            "cache_key.display_info";

    public static void invalidateLocalDisplayInfoCaches() {}

    public void disableLocalDisplayInfoCaches() {}
}
