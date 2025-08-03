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
package com.android.server.wm;

import static android.content.pm.PackageManager.GET_CONFIGURATIONS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.car.builtin.util.Slogf;
import android.car.feature.Flags;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.res.CompatScaleWrapper;
import android.os.Environment;
import android.os.ServiceSpecificException;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.SparseArray;
import android.util.Xml;

import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Implementation of {@link CarDisplayCompatScaleProviderUpdatable}.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class CarDisplayCompatScaleProviderUpdatableImpl implements
        CarDisplayCompatScaleProviderUpdatable {
    private static final String TAG = "CarDisplayCompatScaleProvider";
    private static final String FEATURE_DISPLAYCOMPAT = "android.car.displaycompatibility";
    private static final String CONFIG_PATH = "etc/display_compat_config.xml";
    private static final String NS = null;

    private final Object mLock = new Object();
    private final PackageManager mPackageManager;
    private final CarDisplayCompatScaleProviderInterface mCarCompatScaleProviderInterface;

    @GuardedBy("mLock")
    private final ArrayMap<String, Boolean> mRequiresDisplayCompat = new ArrayMap<>();

    private Config mConfig = new Config();

    public CarDisplayCompatScaleProviderUpdatableImpl(Context context,
            CarDisplayCompatScaleProviderInterface carCompatScaleProviderInterface) {
        mPackageManager = context.getPackageManager();
        mCarCompatScaleProviderInterface = carCompatScaleProviderInterface;
        if (!Flags.displayCompatibility()) {
            Slogf.i(TAG, Flags.FLAG_DISPLAY_COMPATIBILITY + " is not enabled");
            return;
        }
        if (mPackageManager != null
                && !mPackageManager.hasSystemFeature(FEATURE_DISPLAYCOMPAT)) {
            Slogf.i(TAG, FEATURE_DISPLAYCOMPAT + " is not available");
            return;
        }

        try (FileInputStream in = getConfigFile().openRead();) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            mConfig.readConfig(parser);
        } catch (XmlPullParserException | IOException | SecurityException e) {
            Slogf.e(TAG, "read config failed", e);
        }
    }

    @Nullable
    @Override
    public CompatScaleWrapper getCompatScale(@NonNull String packageName, @UserIdInt int userId) {
        if (!Flags.displayCompatibility()) {
            Slogf.i(TAG, Flags.FLAG_DISPLAY_COMPATIBILITY + " is not enabled");
            return null;
        }
        if (mPackageManager != null
                && !mPackageManager.hasSystemFeature(FEATURE_DISPLAYCOMPAT)) {
            Slogf.i(TAG, FEATURE_DISPLAYCOMPAT + " is not available");
            return null;
        }
        try {
            if (requiresDisplayCompat(packageName, userId)) {
                int display = mCarCompatScaleProviderInterface.getMainDisplayAssignedToUser(userId);
                if (display == INVALID_DISPLAY) {
                    display = DEFAULT_DISPLAY;
                }
                float scale =  mConfig.get(display, 1.0f);
                return new CompatScaleWrapper(1.0f, scale);
            }
        } catch (ServiceSpecificException e) {
            return null;
        }
        return null;
    }

    @Override
    public boolean requiresDisplayCompat(@NonNull String packageName, @UserIdInt int userId) {
        boolean result = false;
        synchronized (mLock) {
            // TODO(b/300642384): need to listen to add/remove of packages from PackageManager so
            // the list doesn't have stale data.
            Boolean res = mRequiresDisplayCompat.get(packageName);
            if (res != null) {
                return res.booleanValue();
            }

            try {
                PackageInfoFlags flags = PackageInfoFlags.of(GET_CONFIGURATIONS);
                FeatureInfo[] features = mPackageManager.getPackageInfo(packageName, flags)
                        .reqFeatures;
                if (features != null) {
                    for (FeatureInfo feature: features) {
                        // TODO: get the string from PackageManager
                        if (FEATURE_DISPLAYCOMPAT.equals(feature.name)) {
                            Slogf.i(TAG, "detected autoenhance package: " + packageName);
                            result = true;
                            break;
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Slogf.e(TAG, "Package " + packageName + " not found", e);
                throw new ServiceSpecificException(
                        -100 /** {@code CarPackageManager#ERROR_CODE_NO_PACKAGE} */,
                        e.getMessage());
            }

            mRequiresDisplayCompat.put(packageName, result);
        }
        return result;
    }

    /**
     * Dump {code CarDisplayCompatScaleProviderUpdatableImpl#mRequiresDisplayCompat}
     */
    public void dump(IndentingPrintWriter writer) {
        writer.println(TAG);
        writer.increaseIndent();
        writer.println("DisplayCompat Config:");
        writer.increaseIndent();
        if (mConfig.size() == 0) {
            writer.println("Config is empty.");
        } else {
            for (int i = 0; i < mConfig.size(); i++) {
                float scale = mConfig.get(i, -1);
                if (scale != -1) {
                    writer.println("display: " + i + " scale: " + scale);
                }
            }
        }
        writer.decreaseIndent();
        writer.println("List of DisplayCompat packages:");
        writer.increaseIndent();
        synchronized (mLock) {
            if (mRequiresDisplayCompat.size() == 0) {
                writer.println("No package is enabled.");
            } else {
                for (int i = 0; i < mRequiresDisplayCompat.size(); i++) {
                    if (mRequiresDisplayCompat.valueAt(i)) {
                        writer.println("Package name: " + mRequiresDisplayCompat.keyAt(i));
                    }
                }
            }
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    @NonNull
    private static AtomicFile getConfigFile() {
        File configFile = new File(Environment.getProductDirectory(), CONFIG_PATH);
        return new AtomicFile(configFile);
    }

    private static class Scale {
        public final int display;
        public final float scale;

        private Scale(int display, float scale) {
            this.display = display;
            this.scale = scale;
        }
    }

    private static class Config {
        private static final String CONFIG = "config";
        private static final String SCALE = "scale";
        private static final String DISPLAY = "display";

        private SparseArray<Float> mScales = new SparseArray<>();

        public int size() {
            return mScales.size();
        }

        public float get(int index, float defaultValue) {
            return mScales.get(index, defaultValue);
        }

        public void readConfig(@NonNull XmlPullParser parser) throws XmlPullParserException,
                IOException {
            parser.require(XmlPullParser.START_TAG, NS, CONFIG);
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = parser.getName();
                if (SCALE.equals(name)) {
                    Scale scale = readScale(parser);
                    mScales.put(scale.display, scale.scale);
                } else {
                    skip(parser);
                }
            }
        }

        private Scale readScale(@NonNull XmlPullParser parser) throws XmlPullParserException,
                IOException {
            parser.require(XmlPullParser.START_TAG, NS, SCALE);
            int display = DEFAULT_DISPLAY;
            try {
                display = Integer.parseInt(parser.getAttributeValue(NS, DISPLAY));
            } catch (NullPointerException | NumberFormatException e) {
                Slogf.e(TAG, "parse failed: " + parser.getAttributeValue(NS, DISPLAY), e);
            }
            float value = 1f;
            if (parser.next() == XmlPullParser.TEXT) {
                try {
                    value = Float.parseFloat(parser.getText());
                } catch (NullPointerException | NumberFormatException e) {
                    Slogf.e(TAG, "parse failed: " + parser.getText(), e);
                }
                parser.nextTag();
            }
            parser.require(XmlPullParser.END_TAG, NS, SCALE);
            return new Scale(display, value);
        }

        private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                throw new IllegalStateException();
            }
            int depth = 1;
            while (depth != 0) {
                switch (parser.next()) {
                    case XmlPullParser.END_TAG:
                        depth--;
                        break;
                    case XmlPullParser.START_TAG:
                        depth++;
                        break;
                }
            }
        }
    }
}
