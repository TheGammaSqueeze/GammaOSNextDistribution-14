/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.runner;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.internal.runner.listener.InstrumentationRunListener;

import junit.framework.TestCase;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.lang.Class;
import java.lang.ReflectiveOperationException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ResponseCache;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * A {@link RunListener} for Libcore CTS tests. Sets the system properties
 * necessary for many core tests to run. This is needed because there are some
 * core tests that need writing access to the file system.
 * Finally, we add a means to free memory allocated by a TestCase after its
 * execution.
 */
public class CtsLibcoreTestRunListener extends InstrumentationRunListener {

    private static final String TAG = "CtsLibcoreTestRunListener";

    private TestEnvironment mEnvironment;

    @Override
    public void testRunStarted(Description description) throws Exception {
        mEnvironment = new TestEnvironment(getInstrumentation().getTargetContext());

        // We might want to move this to /sdcard, if is is mounted/writable.
        File cacheDir = getInstrumentation().getTargetContext().getCacheDir();
        System.setProperty("java.io.tmpdir", cacheDir.getAbsolutePath());

        // attempt to disable keyguard, if current test has permission to do so
        // TODO: move this to a better place, such as InstrumentationTestRunner
        // ?
        if (getInstrumentation().getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.DISABLE_KEYGUARD)
                == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Disabling keyguard");
            KeyguardManager keyguardManager =
                    (KeyguardManager) getInstrumentation().getContext().getSystemService(
                            Context.KEYGUARD_SERVICE);
            keyguardManager.newKeyguardLock("cts").disableKeyguard();
        } else {
            Log.i(TAG, "Test lacks permission to disable keyguard. " +
                    "UI based tests may fail if keyguard is up");
        }
    }

    @Override
    public void testStarted(Description description) throws Exception {
        mEnvironment.reset();
    }

    @Override
    public void testFinished(Description description) {
        // no way to implement this in JUnit4...
        // offending test cases that need this logic should probably be cleaned
        // up individually
        // if (test instanceof TestCase) {
        // cleanup((TestCase) test);
        // }
    }

    /**
     * Nulls all non-static reference fields in the given test class. This
     * method helps us with those test classes that don't have an explicit
     * tearDown() method. Normally the garbage collector should take care of
     * everything, but since JUnit keeps references to all test cases, a little
     * help might be a good idea.
     */
    private void cleanup(TestCase test) {
        Class<?> clazz = test.getClass();

        while (clazz != TestCase.class) {
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (!f.getType().isPrimitive() &&
                        !Modifier.isStatic(f.getModifiers())) {
                    try {
                        f.setAccessible(true);
                        f.set(test, null);
                    } catch (Exception ignored) {
                        // Nothing we can do about it.
                    }
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

    private interface TestEnvironmentResetter {
        Boolean getDateFormatIs24Hour();
        void setDateFormatIs24Hour(Boolean value);
        Properties createDefaultProperties();
    }

    private static class AndroidTestEnvironmentResetter implements TestEnvironmentResetter {
        private final Field mDateFormatIs24HourField;

        AndroidTestEnvironmentResetter() {
            try {
                Class<?> dateFormatClass = Class.forName("java.text.DateFormat");
                mDateFormatIs24HourField = dateFormatClass.getDeclaredField("is24Hour");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Missing DateFormat.is24Hour", e);
            }
        }

        @Override
        public Boolean getDateFormatIs24Hour() {
            try {
                return (Boolean) mDateFormatIs24HourField.get(null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to get java.text.DateFormat.is24Hour", e);
            }
        }

        @Override
        public void setDateFormatIs24Hour(Boolean value) {
            try {
                mDateFormatIs24HourField.set(null, value);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to set java.text.DateFormat.is24Hour", e);
            }
        }

        @Override
        public Properties createDefaultProperties() {
            return new Properties();
        }
    }

    private static class StubTestEnvironmentResetter implements TestEnvironmentResetter {
        @Override
        public Boolean getDateFormatIs24Hour() {
            return false;
        }

        @Override
        public void setDateFormatIs24Hour(Boolean value) {
        }

        @Override
        public Properties createDefaultProperties() {
            return System.getProperties();
        }
    }

    // http://code.google.com/p/vogar/source/browse/trunk/src/vogar/target/TestEnvironment.java
    static class TestEnvironment {
        private final static TestEnvironmentResetter sTestEnvironmentResetter;
        static {
            if (System.getProperty("java.vendor").toLowerCase().contains("android")) {
                sTestEnvironmentResetter = new AndroidTestEnvironmentResetter();
            } else {
                sTestEnvironmentResetter = new StubTestEnvironmentResetter();
            }
        }

        private final Locale mDefaultLocale;
        private final TimeZone mDefaultTimeZone;
        private final HostnameVerifier mHostnameVerifier;
        private final SSLSocketFactory mSslSocketFactory;
        private final Properties mProperties;
        private final Boolean mDefaultIs24Hour;

        TestEnvironment(Context context) {
            mDefaultLocale = Locale.getDefault();
            mDefaultTimeZone = TimeZone.getDefault();
            mHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
            mSslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();

            mProperties = sTestEnvironmentResetter.createDefaultProperties();
            mProperties.setProperty("user.home", "");
            mProperties.setProperty("java.io.tmpdir", context.getCacheDir().getAbsolutePath());
            // The CDD mandates that devices that support WiFi are the only ones that will have
            // multicast.
            PackageManager pm = context.getPackageManager();
            mProperties.setProperty("android.cts.device.multicast",
                    Boolean.toString(pm.hasSystemFeature(PackageManager.FEATURE_WIFI)));
            mDefaultIs24Hour = sTestEnvironmentResetter.getDateFormatIs24Hour();

            // There are tests in libcore that should be disabled for low ram devices. They can't
            // access ActivityManager to call isLowRamDevice, but can read system properties.
            ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            mProperties.setProperty("android.cts.device.lowram",
                    Boolean.toString(activityManager.isLowRamDevice()));
        }

        void reset() {
            System.setProperties(null);
            System.setProperties(mProperties);
            Locale.setDefault(mDefaultLocale);
            TimeZone.setDefault(mDefaultTimeZone);
            Authenticator.setDefault(null);
            CookieHandler.setDefault(null);
            ResponseCache.setDefault(null);
            HttpsURLConnection.setDefaultHostnameVerifier(mHostnameVerifier);
            HttpsURLConnection.setDefaultSSLSocketFactory(mSslSocketFactory);
            sTestEnvironmentResetter.setDateFormatIs24Hour(mDefaultIs24Hour);
        }
    }
}
