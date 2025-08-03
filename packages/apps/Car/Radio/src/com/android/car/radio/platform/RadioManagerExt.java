/**
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio.platform;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioManager.BandDescriptor;
import android.hardware.radio.RadioTuner;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.ArrayMap;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.android.car.broadcastradio.support.platform.RadioMetadataExt;
import com.android.car.radio.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Proposed extensions to android.hardware.radio.RadioManager.
 *
 * They might eventually get pushed to the framework.
 */
public final class RadioManagerExt {
    private static final String TAG = "BcRadioApp.mgrext";

    // For now, we open first radio module only.
    private static final int HARDCODED_MODULE_INDEX = 0;

    private static final long SHIFT_FOR_MODULE_ID = 32;
    private static final long MASK_FOR_LOCAL_ID = 0xFFFFFFFF;

    // This won't be necessary when we push this code to the framework,
    // as we really need only module references.
    private static Map<Integer, RadioTuner> sSessions = new ArrayMap<>();

    private final Object mLock = new Object();
    private final Context mContext;

    private final HandlerThread mCallbackHandlerThread = new HandlerThread("BcRadioApp.cbhandler");

    private final RadioManager mRadioManager;

    @GuardedBy("mLock")
    private List<RadioManager.ModuleProperties> mModules;

    @GuardedBy("mLock")
    @Nullable private List<BandDescriptor> mAmFmRegionConfig;

    public RadioManagerExt(Context ctx) {
        mContext = Objects.requireNonNull(ctx, "Context cannot be null");
        mRadioManager = ctx.getSystemService(RadioManager.class);
        Objects.requireNonNull(mRadioManager, "RadioManager could not be loaded");
        mCallbackHandlerThread.start();
    }

    // Select only one region. HAL 2.x moves region selection responsibility from the app to the
    // Broadcast Radio service, so we won't implement region selection based on bands in the app.
    @Nullable
    private List<BandDescriptor> reduceAmFmBands(@Nullable BandDescriptor[] bands) {
        if (bands == null || bands.length == 0) {
            return null;
        }
        int region = bands[0].getRegion();
        Log.d(TAG, "Auto-selecting region " + region);

        return Arrays.stream(bands).filter(band -> band.getRegion() == region).
                collect(Collectors.toList());
    }

    @GuardedBy("mLock")
    private void initModulesLocked() {
        if (mModules != null) {
            return;
        }

        mModules = new ArrayList<>();
        int status = mRadioManager.listModules(mModules);
        if (status != RadioManager.STATUS_OK) {
            Log.w(TAG, "Couldn't get radio module list: " + status);
            return;
        }

        if (mModules.size() == 0) {
            Log.i(TAG, "No radio modules on this device");
            return;
        }

        RadioManager.ModuleProperties moduleProperties = mModules.get(HARDCODED_MODULE_INDEX);
        mAmFmRegionConfig = reduceAmFmBands(moduleProperties.getBands());
    }

    /**
     * Opens a session to interact with hardware tuner.
     *
     * @param callback Session callback.
     * @param handler The Handler on which the callbacks will be received,
     *        {@code null} for default handler.
     */
    @Nullable
    public RadioTunerExt openSession(RadioTuner.Callback callback, Handler handler) {
        Log.i(TAG, "Opening broadcast radio session...");

        RadioManager.ModuleProperties moduleProperties;
        synchronized (mLock) {
            initModulesLocked();
            if (mModules.size() == 0) {
                return null;
            }
            moduleProperties = mModules.get(HARDCODED_MODULE_INDEX);
        }

        // We won't need custom default wrapper when we push these proposed extensions to the
        // framework; this is solely to avoid deadlock on onConfigurationChanged callback versus
        // waitForInitialization.
        Handler hwHandler = new Handler(mCallbackHandlerThread.getLooper());

        TunerCallbackAdapterExt cbExt = new TunerCallbackAdapterExt(callback, handler);

        RadioTuner tuner = mRadioManager.openTuner(
                moduleProperties.getId(),
                /* config= */ null,
                /* withAudio= */ true,
                cbExt, hwHandler);
        sSessions.put(moduleProperties.getId(), tuner);
        if (tuner == null) {
            return null;
        }
        RadioMetadataExt.setModuleId(moduleProperties.getId());

        if (moduleProperties.isInitializationRequired()) {
            if (!cbExt.waitForInitialization()) {
                Log.w(TAG, "Timed out waiting for tuner initialization");
                tuner.close();
                return null;
            }
        }

        return new RadioTunerExt(mContext, tuner, cbExt);
    }

    /**
     * Gets AM/FM region configuration
     *
     * @return AM/FM region configuration
     */
    @Nullable
    public List<BandDescriptor> getAmFmRegionConfig() {
        List<BandDescriptor> amFmRegionConfig;
        synchronized (mLock) {
            initModulesLocked();
            amFmRegionConfig = mAmFmRegionConfig;
        }
        return amFmRegionConfig;
    }

    /**
     * Gets metadata image
     *
     * @param globalId Global id of the metadata image
     * @return Metadata image
     */
    @Nullable
    public Bitmap getMetadataImage(long globalId) {
        if (globalId == 0) {
            return null;
        }

        int moduleId = (int) (globalId >>> SHIFT_FOR_MODULE_ID);
        int localId = (int) (globalId & MASK_FOR_LOCAL_ID);

        RadioTuner tuner = sSessions.get(moduleId);
        if (tuner == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return tuner.getMetadataImage(localId);
        } else {
            return null;
        }
    }
}
