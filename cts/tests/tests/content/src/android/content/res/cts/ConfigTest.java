/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.content.res.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.cts.R;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.util.DisplayMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class ConfigTest {
    private static final String TEST_PACKAGE = "android.content.cts";

    private Context mContext;
    private int mTargetSdkVersion;

    enum Properties {
        LANGUAGE,
        COUNTRY,
        SCRIPT,
        VARIANT,
        GRAMMATICAL_GENDER,
        MCC,
        MNC,
        TOUCHSCREEN,
        KEYBOARD,
        KEYBOARDHIDDEN,
        NAVIGATION,
        ORIENTATION,
        COLOR_MODE,
        WIDTH,
        HEIGHT,
        DENSITY,
        SCREENLAYOUT,
        SWIDTH_DP,
        WIDTH_DP,
        HEIGHT_DP
    }

    private static void checkValue(final Resources res, final int resId,
            final String expectedValue) {
        try {
            final String actual = res.getString(resId);
            assertNotNull("Returned wrong configuration-based simple value: expected <nothing>, "
                    + "got '" + actual + "' from resource 0x" + Integer.toHexString(resId),
                    expectedValue);
            assertEquals("Returned wrong configuration-based simple value: expected '"
                    + expectedValue + "', got '" + actual + "' from resource 0x"
                    + Integer.toHexString(resId), expectedValue, actual);
        } catch (NotFoundException e) {
            assertNull("Resource not found for configuration-based simple value: expecting \""
                    + expectedValue + "\"", expectedValue);
        }
    }

    private static void checkValue(final Resources res, final int resId,
            final int[] styleable, final String[] expectedValues) {
        final Resources.Theme theme = res.newTheme();
        final TypedArray sa = theme.obtainStyledAttributes(resId, styleable);
        for (int i = 0; i < styleable.length; i++) {
            final String actual = sa.getString(i);
            assertEquals("Returned wrong configuration-based style value: expected '"
                    + expectedValues[i] + "', got '" + actual + "' from attr "
                    + i + " of resource 0x" + Integer.toHexString(resId),
                    expectedValues[i], actual);
        }
        sa.recycle();
    }

    private class TotalConfig {
        final Configuration mConfig;
        final DisplayMetrics mMetrics;

        public TotalConfig() {
            mConfig = new Configuration();
            mMetrics = new DisplayMetrics();
            mConfig.locale = Locale.ROOT;
        }

        public void setProperty(final Properties p, final int value) {
            switch(p) {
                case GRAMMATICAL_GENDER:
                    mConfig.setGrammaticalGender(value);
                    break;
                case MCC:
                    mConfig.mcc = value;
                    break;
                case MNC:
                    mConfig.mnc = value;
                    break;
                case TOUCHSCREEN:
                    mConfig.touchscreen = value;
                    break;
                case KEYBOARD:
                    mConfig.keyboard = value;
                    break;
                case KEYBOARDHIDDEN:
                    mConfig.keyboardHidden = value;
                    break;
                case NAVIGATION:
                    mConfig.navigation = value;
                    break;
                case ORIENTATION:
                    mConfig.orientation = value;
                    break;
                case COLOR_MODE:
                    mConfig.colorMode = value;
                    break;
                case WIDTH:
                    mMetrics.widthPixels = value;
                    mMetrics.noncompatWidthPixels = value;
                    break;
                case HEIGHT:
                    mMetrics.heightPixels = value;
                    mMetrics.noncompatHeightPixels = value;
                    break;
                case DENSITY:
                    // this is the ratio from the standard
                    mMetrics.density = (((float)value)/((float)DisplayMetrics.DENSITY_DEFAULT));
                    mMetrics.noncompatDensity = mMetrics.density;
                    mConfig.densityDpi = value;
                    break;
                case SCREENLAYOUT:
                    mConfig.screenLayout = value;
                    break;
                case SWIDTH_DP:
                    mConfig.smallestScreenWidthDp = value;
                    break;
                case WIDTH_DP:
                    mConfig.screenWidthDp = value;
                    break;
                case HEIGHT_DP:
                    mConfig.screenHeightDp = value;
                    break;
                default:
                    assert(false);
                    break;
            }
        }

        public void setProperty(final Properties p, final String value) {
            switch(p) {
                case LANGUAGE:
                    mConfig.locale = new Locale.Builder()
                            .setLocale(mConfig.locale)
                            .setLanguage(value)
                            .build();
                    break;
                case COUNTRY:
                    mConfig.locale = new Locale.Builder()
                            .setLocale(mConfig.locale)
                            .setRegion(value)
                            .build();
                    break;
                case SCRIPT:
                    mConfig.locale = new Locale.Builder()
                            .setLocale(mConfig.locale)
                            .setScript(value)
                            .build();
                    break;
                case VARIANT:
                    mConfig.locale = new Locale.Builder()
                            .setLocale(mConfig.locale)
                            .setVariant(value)
                            .build();
                    break;
                default:
                    assert(false);
                    break;
            }
        }

        public Resources getResources() {
            final AssetManager assmgr = new AssetManager();
            assmgr.addAssetPath(mContext.getPackageResourcePath());
            return new Resources(assmgr, mMetrics, mConfig);
        }
    }

    public TotalConfig makeEmptyConfig() {
        return new TotalConfig();
    }

    public TotalConfig makeClassicConfig() {
        TotalConfig config = new TotalConfig();
        config.setProperty(Properties.LANGUAGE, "en");
        config.setProperty(Properties.COUNTRY, "US");
        config.setProperty(Properties.MCC, 310);
        config.setProperty(Properties.MNC, 001); // unused
        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_FINGER);
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_QWERTY);
        config.setProperty(Properties.KEYBOARDHIDDEN, Configuration.KEYBOARDHIDDEN_YES);
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_TRACKBALL);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_PORTRAIT);
        config.setProperty(Properties.SWIDTH_DP, 320);
        config.setProperty(Properties.WIDTH_DP, 320);
        config.setProperty(Properties.HEIGHT_DP, 480);
        config.setProperty(Properties.DENSITY, 160);
        config.setProperty(Properties.WIDTH, 200);
        config.setProperty(Properties.HEIGHT, 320);
        return config;
    }

    private static void checkPair(Resources res, int[] notResIds,
            int simpleRes, String simpleString,
            int bagRes, String bagString) {
        boolean willHave = true;
        if (notResIds != null) {
            for (int i : notResIds) {
                if (i == simpleRes) {
                    willHave = false;
                    break;
                }
            }
        }
        checkValue(res, simpleRes, willHave ? simpleString : null);
        checkValue(res, bagRes, R.styleable.TestConfig,
                new String[]{willHave ? bagString : null});
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        final PackageManager pm = mContext.getPackageManager();
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(TEST_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0));
            mTargetSdkVersion = appInfo.targetSdkVersion;
        } catch (NameNotFoundException e) {
            fail("Should be able to find application info for this package");
        }
    }

    @Test
    public void testAllEmptyConfigs() {
        /**
         * Test a resource that contains a value for each possible single
         * configuration value.
         */
        TotalConfig config = makeEmptyConfig();
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple default");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag default"});

        config = makeEmptyConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx"});

        config = makeEmptyConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.COUNTRY, "YY");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx-rYY");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx-rYY"});

        config = makeEmptyConfig();
        config.setProperty(Properties.MCC, 111);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc111"});

        config = makeEmptyConfig();
        config.setProperty(Properties.MNC, 222);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mnc222");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mnc222"});

        config = makeEmptyConfig();
        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_NOTOUCH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple notouch");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag notouch"});

        config = makeEmptyConfig();
        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_STYLUS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple stylus");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag stylus"});

        config = makeEmptyConfig();
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_NOKEYS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nokeys");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nokeys"});

        config = makeEmptyConfig();
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_12KEY);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 12key");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 12key"});

        config = makeEmptyConfig();
        config.setProperty(Properties.KEYBOARDHIDDEN, Configuration.KEYBOARDHIDDEN_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple keysexposed");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag keysexposed"});

        config = makeEmptyConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_NONAV);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nonav");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nonav"});

        config = makeEmptyConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_DPAD);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple dpad");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag dpad"});

        config = makeEmptyConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_WHEEL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple wheel");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag wheel"});

        config = makeEmptyConfig();
        config.setProperty(Properties.HEIGHT, 480);
        config.setProperty(Properties.WIDTH, 320);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 480x320");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 480x320"});

        config = makeEmptyConfig();
        config.setProperty(Properties.DENSITY, 240);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});

        config = makeEmptyConfig();
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple landscape");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag landscape"});

        config = makeEmptyConfig();
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_SQUARE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple square");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag square"});

        config = makeEmptyConfig();
        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_HDR_YES);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple hdr");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag hdr"});

        config = makeEmptyConfig();
        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_HDR_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple ldr");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag ldr"});

        config = makeEmptyConfig();
        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_YES);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple widecg");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag widecg"});

        config = makeEmptyConfig();
        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nowidecg");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nowidecg"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_SMALL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple small");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag small"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_NORMAL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple normal");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag normal"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple large");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag large"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xlarge");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xlarge"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw600"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw600"});

        config = makeEmptyConfig();
        config.setProperty(Properties.SWIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw720");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw720"});

        config = makeEmptyConfig();
        config.setProperty(Properties.WIDTH_DP, 600);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple w600");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag w600"});

        config = makeEmptyConfig();
        config.setProperty(Properties.WIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple w720");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag w720"});

        config = makeEmptyConfig();
        config.setProperty(Properties.HEIGHT_DP, 550);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple h550");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag h550"});

        config = makeEmptyConfig();
        config.setProperty(Properties.HEIGHT_DP, 670);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple h670");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag h670"});

        config = makeEmptyConfig();
        config.setProperty(Properties.GRAMMATICAL_GENDER,
                Configuration.GRAMMATICAL_GENDER_FEMININE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple feminine");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag feminine"});

        config = makeEmptyConfig();
        config.setProperty(Properties.GRAMMATICAL_GENDER,
                Configuration.GRAMMATICAL_GENDER_MASCULINE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple masculine");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag masculine"});

        config = makeEmptyConfig();
        config.setProperty(Properties.GRAMMATICAL_GENDER,
                Configuration.GRAMMATICAL_GENDER_NEUTRAL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple neuter");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag neuter"});
    }

    @Test
    public void testAllClassicConfigs() {
        /**
         * Test a resource that contains a value for each possible single
         * configuration value.
         */
        TotalConfig config = makeClassicConfig();
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple default");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag default"});

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx"});

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.COUNTRY, "YY");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx-rYY");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx-rYY"});

        config = makeClassicConfig();
        config.setProperty(Properties.MCC, 111);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc111"});

        config = makeClassicConfig();
        config.setProperty(Properties.MNC, 222);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mnc222");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mnc222"});

        config = makeClassicConfig();
        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_NOTOUCH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple notouch");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag notouch"});

        config = makeClassicConfig();
        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_STYLUS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple stylus");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag stylus"});

        config = makeClassicConfig();
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_NOKEYS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nokeys");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nokeys"});

        config = makeClassicConfig();
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_12KEY);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 12key 63x57");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 12key 63x57"});

        config = makeClassicConfig();
        config.setProperty(Properties.KEYBOARDHIDDEN, Configuration.KEYBOARDHIDDEN_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple keysexposed");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag keysexposed"});

        config = makeClassicConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_NONAV);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nonav");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nonav"});

        config = makeClassicConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_DPAD);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple dpad 63x57");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag dpad 63x57"});

        config = makeClassicConfig();
        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_WHEEL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple wheel");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag wheel"});

        config = makeClassicConfig();
        config.setProperty(Properties.HEIGHT, 480);
        config.setProperty(Properties.WIDTH, 320);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 480x320");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 480x320"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 240);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple landscape");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag landscape"});

        config = makeClassicConfig();
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_SQUARE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple square");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag square"});

        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_SMALL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple small");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag small"});

        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_NORMAL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple normal");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag normal"});

        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple large");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag large"});

        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xlarge");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xlarge"});

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw600"});

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600 land");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw600 land"});

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw720");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw720"});

        config = makeClassicConfig();
        config.setProperty(Properties.WIDTH_DP, 600);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple w600");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag w600"});

        config = makeClassicConfig();
        config.setProperty(Properties.WIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple w720");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag w720"});

        config = makeClassicConfig();
        config.setProperty(Properties.HEIGHT_DP, 550);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple h550");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag h550"});

        config = makeClassicConfig();
        config.setProperty(Properties.HEIGHT_DP, 670);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple h670");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag h670"});
    }
    
    @Test
    public void testDensity() throws Exception {
        // Have 32, 240 and the default 160 content.
        // Rule is that next highest wins.
        TotalConfig config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 2);
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple 32dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 32dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 32);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 32dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 32dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 48);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple default");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag default"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 150);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple default");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag default"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 181);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 239);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 490);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});
    }

    @Test
    public void testScreenSize() throws Exception {
        // ensure that we fall back to the best available screen size
        // for a given configuration.
        TotalConfig config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_SMALL);
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple small");
        checkValue(res, R.string.small, "small");
        checkValue(res, R.string.normal, "default");
        checkValue(res, R.string.large, "default");
        checkValue(res, R.string.xlarge, "default");
        
        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_NORMAL);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple normal");
        checkValue(res, R.string.small, "default");
        checkValue(res, R.string.normal, "normal");
        checkValue(res, R.string.large, "default");
        checkValue(res, R.string.xlarge, "default");
        
        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple large");
        checkValue(res, R.string.small, "default");
        checkValue(res, R.string.normal, "normal");
        checkValue(res, R.string.large, "large");
        checkValue(res, R.string.xlarge, "default");
        
        config = makeClassicConfig();
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xlarge");
        checkValue(res, R.string.small, "default");
        checkValue(res, R.string.normal, "normal");
        checkValue(res, R.string.large, "large");
        checkValue(res, R.string.xlarge, "xlarge");
    }

    @Test
    public void testNewScreenSize() throws Exception {
        // ensure that swNNNdp, wNNNdp, and hNNNdp are working correctly
        // for various common screen configurations.
        TotalConfig config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 589);
        config.setProperty(Properties.WIDTH_DP, 589);
        config.setProperty(Properties.HEIGHT_DP, 500);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple large");
        checkValue(res, R.string.sw, "default");
        checkValue(res, R.string.w, "default");
        checkValue(res, R.string.h, "default");
        checkValue(res, R.string.wh, "default");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 590);
        config.setProperty(Properties.WIDTH_DP, 590);
        config.setProperty(Properties.HEIGHT_DP, 500);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_MEDIUM);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw590 mdpi");
        checkValue(res, R.string.sw, "590 mdpi");

        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_HIGH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw590 hdpi");
        checkValue(res, R.string.sw, "590 hdpi");

        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_XHIGH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw590 xhdpi");
        checkValue(res, R.string.sw, "590 xhdpi");

        config.setProperty(Properties.SWIDTH_DP, 591);
        config.setProperty(Properties.WIDTH_DP, 591);
        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_MEDIUM);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw591");
        checkValue(res, R.string.sw, "591");

        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_HIGH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw591 hdpi");
        checkValue(res, R.string.sw, "591 hdpi");

        config.setProperty(Properties.DENSITY, DisplayMetrics.DENSITY_XHIGH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw591 hdpi");
        checkValue(res, R.string.sw, "591 hdpi");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 480);
        config.setProperty(Properties.WIDTH_DP, 800);
        config.setProperty(Properties.HEIGHT_DP, 480);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple w720");
        checkValue(res, R.string.sw, "default");
        checkValue(res, R.string.w, "720");
        checkValue(res, R.string.h, "default");
        checkValue(res, R.string.wh, "600");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        config.setProperty(Properties.WIDTH_DP, 1024);
        config.setProperty(Properties.HEIGHT_DP, 552);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600 land");
        checkValue(res, R.string.sw, "600 land");
        checkValue(res, R.string.w, "720");
        checkValue(res, R.string.h, "550");
        checkValue(res, R.string.wh, "600-550");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 600);
        config.setProperty(Properties.WIDTH_DP, 600);
        config.setProperty(Properties.HEIGHT_DP, 974);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_PORTRAIT);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600");
        checkValue(res, R.string.sw, "600");
        checkValue(res, R.string.w, "600");
        checkValue(res, R.string.h, "670");
        checkValue(res, R.string.wh, "600-550");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 719);
        config.setProperty(Properties.WIDTH_DP, 1279);
        config.setProperty(Properties.HEIGHT_DP, 669);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_LARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw600 land");
        checkValue(res, R.string.sw, "600 land");
        checkValue(res, R.string.w, "720");
        checkValue(res, R.string.h, "550");
        checkValue(res, R.string.wh, "600-550");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 800);
        config.setProperty(Properties.WIDTH_DP, 1280);
        config.setProperty(Properties.HEIGHT_DP, 672);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw720");
        checkValue(res, R.string.sw, "720");
        checkValue(res, R.string.w, "720");
        checkValue(res, R.string.h, "670");
        checkValue(res, R.string.wh, "720-670");

        config = makeClassicConfig();
        config.setProperty(Properties.SWIDTH_DP, 800);
        config.setProperty(Properties.WIDTH_DP, 720);
        config.setProperty(Properties.HEIGHT_DP, 1230);
        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_PORTRAIT);
        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw720");
        checkValue(res, R.string.sw, "720");
        checkValue(res, R.string.w, "720");
        checkValue(res, R.string.h, "670");
        checkValue(res, R.string.wh, "720-670");
    }

// TODO - add tests for special cases - ie, other key params seem ignored if 
// nokeys is set

    @Test
    public void testPrecedence() {
        /**
         * Check for precedence of resources selected when there are multiple
         * options matching the current config.
         */
        TotalConfig config = makeEmptyConfig();
        config.setProperty(Properties.HEIGHT, 640);
        config.setProperty(Properties.WIDTH, 400);
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple 640x400");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 640x400"});

        config.setProperty(Properties.NAVIGATION, Configuration.NAVIGATION_NONAV);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nonav");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nonav"});

        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_NOKEYS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple nokeys");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag nokeys"});

        config.setProperty(Properties.KEYBOARDHIDDEN, Configuration.KEYBOARDHIDDEN_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple keysexposed");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag keysexposed"});

        config.setProperty(Properties.TOUCHSCREEN, Configuration.TOUCHSCREEN_NOTOUCH);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple notouch");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag notouch"});

        config.setProperty(Properties.DENSITY, 240);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 240dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 240dpi"});

        config.setProperty(Properties.ORIENTATION, Configuration.ORIENTATION_LANDSCAPE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple landscape");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag landscape"});

        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_HDR_YES);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple hdr");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag hdr"});

        config.setProperty(Properties.COLOR_MODE, Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_YES);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple widecg");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag widecg"});

        config.setProperty(Properties.SCREENLAYOUT, Configuration.SCREENLAYOUT_SIZE_XLARGE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xlarge");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xlarge"});

        config.setProperty(Properties.HEIGHT_DP, 670);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple h670");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag h670"});

        config.setProperty(Properties.WIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 720-670");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 720-670"});

        config.setProperty(Properties.SWIDTH_DP, 720);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple sw720");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag sw720"});

        config.setProperty(Properties.GRAMMATICAL_GENDER,
                Configuration.GRAMMATICAL_GENDER_FEMININE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple feminine");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag feminine"});

        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.COUNTRY, "YY");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx-rYY");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx-rYY"});

        config.setProperty(Properties.MCC, 111);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111 xx-rYY");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc111 xx-rYY"});

        config.setProperty(Properties.MNC, 222);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111 mnc222");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc111 mnc222"});
    }

    @Test
    public void testCombinations() {
        /**
         * Verify that in cases of ties, the specific ordering is followed
         */

        /**
         * Precidence order: mcc, mnc, locale, swdp, wdp, hdp, screenlayout-size,
         * screenlayout-long, orientation, density,
         * touchscreen, hidden, keyboard, navigation, width-height
         */

        /**
         * verify mcc trumps mnc.  Have 110-xx, 220-xx but no 110-220
         * so which is selected?  Should be mcc110-xx.
         */
        TotalConfig config = makeClassicConfig();
        config.setProperty(Properties.MCC, 110);
        config.setProperty(Properties.MNC, 220);
        config.setProperty(Properties.LANGUAGE, "xx");
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc110 xx");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc110 xx"});

        /* full A + B + C doesn't exist.  Do we get A + C or B + C? 
         */
        config = makeClassicConfig();
        config.setProperty(Properties.MCC, 111);
        config.setProperty(Properties.MNC, 222);
        config.setProperty(Properties.LANGUAGE, "xx");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111 mnc222");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mcc111 mnc222"});

        config = makeClassicConfig();
        config.setProperty(Properties.MNC, 222);
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.ORIENTATION, 
                Configuration.ORIENTATION_SQUARE);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mnc222 xx");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mnc222 xx"});

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.ORIENTATION, 
                Configuration.ORIENTATION_SQUARE);
        config.setProperty(Properties.DENSITY, 32);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx square");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag xx square"});

        /**
         * Verify that proper strings are found for multiple-selectivity case
         * (ie, a string set for locale and mcc is found only when both are
         * true).
         */
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.COUNTRY, "YY");
        config.setProperty(Properties.MCC, 111);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mcc111 xx-rYY");
        checkValue(res, R.style.bag, R.styleable.TestConfig,
                new String[] { "bag mcc111 xx-rYY" });

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "xx");
        config.setProperty(Properties.COUNTRY, "YY");
        config.setProperty(Properties.MCC, 333);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple xx-rYY");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag xx-rYY" });

        config = makeClassicConfig();
        config.setProperty(Properties.MNC, 333);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple default");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag default"});

        config = makeClassicConfig();
        config.setProperty(Properties.ORIENTATION, 
                Configuration.ORIENTATION_SQUARE);
        config.setProperty(Properties.DENSITY, 32);
        config.setProperty(Properties.TOUCHSCREEN, 
                Configuration.TOUCHSCREEN_STYLUS);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple square 32dpi");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag square 32dpi"});

        config = makeClassicConfig();
        config.setProperty(Properties.DENSITY, 32);
        config.setProperty(Properties.TOUCHSCREEN, 
                Configuration.TOUCHSCREEN_STYLUS);
        config.setProperty(Properties.KEYBOARDHIDDEN, 
                Configuration.KEYBOARDHIDDEN_NO);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 32dpi stylus");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 32dpi stylus"});

        config = makeClassicConfig();
        config.setProperty(Properties.TOUCHSCREEN, 
                Configuration.TOUCHSCREEN_STYLUS);
        config.setProperty(Properties.KEYBOARDHIDDEN, 
                Configuration.KEYBOARDHIDDEN_NO);
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_12KEY);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple stylus keysexposed");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag stylus keysexposed"});

        config = makeClassicConfig();
        config.setProperty(Properties.KEYBOARDHIDDEN, 
                Configuration.KEYBOARDHIDDEN_NO);
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_12KEY);
        config.setProperty(Properties.NAVIGATION, 
                Configuration.NAVIGATION_DPAD);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple keysexposed 12key");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag keysexposed 12key"});

        config = makeClassicConfig();
        config.setProperty(Properties.KEYBOARD, Configuration.KEYBOARD_12KEY);
        config.setProperty(Properties.NAVIGATION, 
                Configuration.NAVIGATION_DPAD);
        config.setProperty(Properties.HEIGHT, 63);
        config.setProperty(Properties.WIDTH, 57);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple 12key dpad");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag 12key dpad"});

        config = makeClassicConfig();
        config.setProperty(Properties.NAVIGATION, 
                Configuration.NAVIGATION_DPAD);
        config.setProperty(Properties.HEIGHT, 640);
        config.setProperty(Properties.WIDTH, 400);
        res = config.getResources();
        checkValue(res, R.string.simple, "simple dpad 63x57");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag dpad 63x57"});
    }

    @Test
    public void testVersions() {
        final boolean isReleaseBuild = "REL".equals(android.os.Build.VERSION.CODENAME);

        // Release builds must not have a dev SDK version
        if (isReleaseBuild) {
            assertTrue("Release builds must build with a valid SDK version",
                    mTargetSdkVersion < 10000);
        }

        // ...and skip this test if this is a dev-SDK-version build
        assumeTrue("This product was built with non-release SDK level 10000",
                mTargetSdkVersion < 10000);

        // Check that we get the most recent resources that are <= our
        // current version.  Note the special version adjustment, so that
        // during development the resource version is incremented to the
        // next one.
        int vers = android.os.Build.VERSION.SDK_INT;
        if (!isReleaseBuild) {
            vers++;
        }
        String expected = "v" + vers + "cur";
        assertEquals(expected, mContext.getResources().getString(R.string.version_cur));
        assertEquals("base",  mContext.getResources().getString(R.string.version_old));
        assertEquals("v3",  mContext.getResources().getString(R.string.version_v3));
    }

    @Test
    public void testNormalLocales() {
        Resources res;
        TotalConfig config = makeClassicConfig();
        // Hebrew
        config.setProperty(Properties.LANGUAGE, "iw");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple iw");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag iw"});

        // Hebrew for Israel
        config.setProperty(Properties.LANGUAGE, "iw");
        config.setProperty(Properties.COUNTRY, "IL");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple iw IL");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag iw IL"});

        config = makeClassicConfig();
        // Macedonian
        config.setProperty(Properties.LANGUAGE, "mk");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mk");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mk"});

        // Macedonian for Macedonia
        config.setProperty(Properties.LANGUAGE, "mk");
        config.setProperty(Properties.COUNTRY, "MK");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple mk MK");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag mk MK"});
    }

    @Test
    public void testExtendedLocales() {
        TotalConfig config = makeClassicConfig();
        // BCP 47 Locale kok
        config.setProperty(Properties.LANGUAGE, "kok");
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple kok");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok"});

        // BCP 47 Locale kok-IN
        config.setProperty(Properties.COUNTRY, "IN");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok IN");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok IN"});

        // BCP 47 Locale kok-419
        config.setProperty(Properties.COUNTRY, "419");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok 419");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok 419"});


        // BCP 47 Locale kok-419-VARIANT
        config.setProperty(Properties.VARIANT, "VARIANT");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok 419 VARIANT");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok 419 VARIANT"});

        // BCP 47 Locale kok-Knda
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "kok");
        config.setProperty(Properties.SCRIPT, "Knda");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok Knda");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok Knda"});

        // BCP 47 Locale kok-Knda-419
        config.setProperty(Properties.COUNTRY, "419");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok Knda 419");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok Knda 419"});

        // BCP 47 Locale kok-Knda-419-VARIANT
        config.setProperty(Properties.VARIANT, "VARIANT");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok Knda 419 VARIANT");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok Knda 419 VARIANT"});

        // BCP 47 Locale kok-VARIANT
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "kok");
        config.setProperty(Properties.VARIANT, "VARIANT");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple kok VARIANT");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[]{"bag kok VARIANT"});
    }

    @Test
    public void testTlAndFilConversion() {
        TotalConfig config = makeClassicConfig();

        // Ensure that "fil" is mapped to "tl" correctly.
        config.setProperty(Properties.LANGUAGE, "fil");
        config.setProperty(Properties.COUNTRY, "US");
        Resources res = config.getResources();
        checkValue(res, R.string.simple, "simple fil");  // We have this resource in 'fil'
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag tl" });  // But this comes from 'tl'

        // Ensure that "fil-PH" is mapped to "tl-PH" correctly.
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "fil");
        config.setProperty(Properties.COUNTRY, "PH");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple tl PH");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag tl PH" });

        // Ensure that "fil-SA" works with no "tl" version.
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "fil");
        config.setProperty(Properties.COUNTRY, "SA");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple fil");  // This comes from 'fil'
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag fil SA" });  // And this from 'fil-SA'

        // Ensure that "tlh" is not mistakenly treated as a "tl" variant.
        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "tlh");
        config.setProperty(Properties.COUNTRY, "US");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple tlh");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag tlh" });

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "tgl");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple tgl");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag tgl" });

        config = makeClassicConfig();
        config.setProperty(Properties.LANGUAGE, "tgl");
        config.setProperty(Properties.COUNTRY, "PH");
        res = config.getResources();
        checkValue(res, R.string.simple, "simple tgl PH");
        checkValue(res, R.style.bag,
                R.styleable.TestConfig, new String[] { "bag tgl PH" });
    }

    @Test
    public void testGetLocalesConvertsTlToFil() {
        TotalConfig config = makeClassicConfig();

        // Check that the list of locales doesn't contain any of the
        // "tl" variants. They should've been converted to "fil"
        // locales.
        AssetManager am = config.getResources().getAssets();
        String[] locales = am.getLocales();
        final List<String> tlLocales = new ArrayList<String>(4);
        final List<String> filLocales = new ArrayList<String>(4);
        for (String locale : locales) {
            if (locale.startsWith("tl-") || locale.equals("tl")) {
                tlLocales.add(locale);
            }

            if (locale.startsWith("fil-") || locale.equals("fil")) {
                filLocales.add(locale);
            }
        }

        assertEquals(0, tlLocales.size());
        assertEquals(3, filLocales.size());
        assertTrue(filLocales.contains("fil"));
        assertTrue(filLocales.contains("fil-PH"));
        assertTrue(filLocales.contains("fil-SA"));
    }
}
