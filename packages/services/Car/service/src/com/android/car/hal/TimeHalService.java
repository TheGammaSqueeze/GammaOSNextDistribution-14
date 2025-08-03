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

package com.android.car.hal;

import static android.hardware.automotive.vehicle.VehicleProperty.ANDROID_EPOCH_TIME;
import static android.hardware.automotive.vehicle.VehicleProperty.EXTERNAL_CAR_TIME;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.app.time.ExternalTimeSuggestion;
import android.app.time.TimeManager;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.automotive.vehicle.VehiclePropertyStatus;

import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Writes the Android System time to ANDROID_EPOCH_TIME in the VHAL, if supported. */
public final class TimeHalService extends HalServiceBase {

    private static final int[] SUPPORTED_PROPERTIES =
            new int[]{ANDROID_EPOCH_TIME, EXTERNAL_CAR_TIME};

    private final Context mContext;
    private final VehicleHal mHal;
    private final TimeManager mTimeManager;

    private final boolean mEnableExternalCarTimeSuggestions;

    private final HalPropValueBuilder mPropValueBuilder;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Intent.ACTION_TIME_CHANGED)) {
                return;
            }

            synchronized (mLock) {
                if (mAndroidTimeSupported) {
                    updateAndroidEpochTimePropertyLocked(System.currentTimeMillis());
                }
            }
        }
    };

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mReceiverRegistered;

    @GuardedBy("mLock")
    @Nullable
    private Instant mLastAndroidTimeReported;

    @GuardedBy("mLock")
    @Nullable
    private ExternalTimeSuggestion mLastExternalTimeSuggestion;

    @GuardedBy("mLock")
    private boolean mAndroidTimeSupported;

    @GuardedBy("mLock")
    private boolean mExternalCarTimeSupported;

    TimeHalService(Context context, VehicleHal hal) {
        mContext = requireNonNull(context);
        mHal = requireNonNull(hal);
        mTimeManager = requireNonNull(context.getSystemService(TimeManager.class));
        mEnableExternalCarTimeSuggestions = mContext.getResources().getBoolean(
                R.bool.config_enableExternalCarTimeToExternalTimeSuggestion);
        mPropValueBuilder = hal.getHalPropValueBuilder();
    }

    @Override
    public void init() {
        synchronized (mLock) {
            if (mAndroidTimeSupported) {
                updateAndroidEpochTimePropertyLocked(System.currentTimeMillis());

                IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
                mContext.registerReceiver(mReceiver, filter);
                mReceiverRegistered = true;
                Slogf.d(CarLog.TAG_TIME,
                        "Registered BroadcastReceiver for Intent.ACTION_TIME_CHANGED");
            }

            if (mExternalCarTimeSupported) {
                HalPropValue propValue = mHal.get(EXTERNAL_CAR_TIME);
                suggestExternalTimeLocked(propValue);

                mHal.subscribePropertySafe(this, EXTERNAL_CAR_TIME);
                Slogf.d(CarLog.TAG_TIME, "Subscribed to VHAL property EXTERNAL_CAR_TIME.");
            }
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            if (mReceiverRegistered) {
                mContext.unregisterReceiver(mReceiver);
                mReceiverRegistered = false;
            }

            mAndroidTimeSupported = false;
            mLastAndroidTimeReported = null;

            mExternalCarTimeSupported = false;
            mLastExternalTimeSuggestion = null;
        }
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        for (HalPropConfig property : properties) {
            switch (property.getPropId()) {
                case ANDROID_EPOCH_TIME:
                    synchronized (mLock) {
                        mAndroidTimeSupported = true;
                    }
                    break;
                case EXTERNAL_CAR_TIME:
                    if (mEnableExternalCarTimeSuggestions) {
                        synchronized (mLock) {
                            mExternalCarTimeSupported = true;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        synchronized (mLock) {
            if (!mExternalCarTimeSupported) {
                return;
            }

            for (HalPropValue value : values) {
                if (value.getPropId() == EXTERNAL_CAR_TIME) {
                    suggestExternalTimeLocked(value);
                    break;
                }
            }
        }
    }

    /** Returns whether the service has detected support for ANDROID_EPOCH_TIME VHAL property. */
    public boolean isAndroidTimeSupported() {
        synchronized (mLock) {
            return mAndroidTimeSupported;
        }
    }

    /** Returns whether the service has detected support for EXTERNAL_CAR_TIME VHAL property. */
    public boolean isExternalCarTimeSupported() {
        synchronized (mLock) {
            return mExternalCarTimeSupported;
        }
    }

    @GuardedBy("mLock")
    private void updateAndroidEpochTimePropertyLocked(long timeMillis) {
        HalPropValue propValue = mPropValueBuilder.build(ANDROID_EPOCH_TIME, /* areaId= */ 0,
                /*timestamp=*/timeMillis, VehiclePropertyStatus.AVAILABLE,
                /*value=*/timeMillis);

        Slogf.d(CarLog.TAG_TIME, "Writing value %d to property ANDROID_EPOCH_TIME", timeMillis);
        mHal.set(propValue);
        mLastAndroidTimeReported = Instant.ofEpochMilli(timeMillis);
    }

    @GuardedBy("mLock")
    private void suggestExternalTimeLocked(HalPropValue value) {
        if (value.getPropId() != EXTERNAL_CAR_TIME
                || value.getStatus() != VehiclePropertyStatus.AVAILABLE) {
            return;
        }

        if (value.getInt64ValuesSize() != 1) {
            Slogf.e(CarLog.TAG_TIME, "Invalid value received for EXTERNAL_CAR_TIME.\n"
                    + "  Expected a single element in int64values.\n"
                    + "  Received: %s", value.dumpInt64Values());
            return;
        }
        long epochTime = value.getInt64Value(0);
        // timestamp is stored in nanoseconds but the suggest API uses milliseconds.
        long elapsedRealtime = value.getTimestamp() / 1_000_000;

        mLastExternalTimeSuggestion = new ExternalTimeSuggestion(elapsedRealtime, epochTime);

        Slogf.d(CarLog.TAG_TIME, "Sending Time Suggestion: " + mLastExternalTimeSuggestion);
        mTimeManager.suggestExternalTime(mLastExternalTimeSuggestion);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter printWriter) {
        IndentingPrintWriter writer = new IndentingPrintWriter(printWriter);
        writer.println("*ExternalTime HAL*");
        writer.increaseIndent();
        synchronized (mLock) {
            if (mAndroidTimeSupported) {
                writer.printf("mLastAndroidTimeReported: %d millis\n",
                        mLastAndroidTimeReported.toEpochMilli());
            }
            if (mExternalCarTimeSupported) {
                writer.printf("mLastExternalTimeSuggestion: %s\n", mLastExternalTimeSuggestion);
            }
        }
        writer.decreaseIndent();
        writer.flush();
    }
}
