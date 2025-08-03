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

package com.example.helloworld.lib;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

import com.android.compatibility.common.util.MatcherUtils;
import com.android.internal.util.XmlUtils;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringStartsWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TestUtils {
    public static final String TEST_APP_PACKAGE = "com.example.helloworld";
    public static final String TEST_ACTIVITY_NAME = "com.example.helloworld.TestActivity";

    public static final String TEST_NAME_EXTRA_KEY = "TEST_NAME";
    public static final String TEST_ASSERT_SUCCESS_EXTRA_KEY = "ASSERT_SUCCESS";

    public static final String TEST_STATUS_ACTION = "android.content.pm.cts.TEST_STATUS_UPDATE";

    public static final String PID_STATUS_PID_KEY = "TEST_PID";
    public static final String TEST_STATUS_RESULT_KEY = "TEST_RESULT";
    public static final String TEST_STATUS_RESULT_SUCCESS = "Success";

    public static final String RES_XML_PATH = "res/xml/test_xml.xml";
    public static final String RES_XML_ATTRS_PATH = "res/xml/test_xml_attrs.xml";
    public static final String RES_DRAWABLE_HDPI_PATH = "res/drawable-hdpi-v4/background.jpg";
    public static final String RES_DRAWABLE_MDPI_PATH = "res/drawable-mdpi-v4/background.jpg";

    public static final String RES_IDENTIFIER = TEST_APP_PACKAGE + ":string/inc_string";

    public static final int RES_STRING = 0x7f021000;
    public static final int RES_ARRAY = 0x7f051000;
    public static final int RES_STYLE = 0x7f061000;
    public static final int[] RES_STYLEABLE = {0x7f011000, 0x7f011001, 0x7f011002};

    public enum AssertionType {
        ASSERT_SUCCESS,
        ASSERT_READ_FAILURE,
    }

    public static final String TEST_GET_IDENTIFIER = "checkGetIdentifier";

    public static void checkGetIdentifier(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailure(type,
                () -> res.getIdentifier(RES_IDENTIFIER, "", ""),
                Matchers.not(equalTo(0)), equalTo(0));
    }

    public static final String TEST_GET_RESOURCE_NAME = "checkGetResourceName";

    public static void checkGetResourceName(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> res.getResourceName(RES_STRING),
                equalTo(RES_IDENTIFIER), instanceOf(Resources.NotFoundException.class));
    }

    public static final String TEST_GET_STRING = "checkGetString";

    public static void checkGetString(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> res.getString(RES_STRING),
                equalTo("true"), instanceOf(Resources.NotFoundException.class));
    }

    public static final String TEST_GET_STRING_ARRAY = "checkGetStringArray";

    public static void checkGetStringArray(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> res.getStringArray(RES_ARRAY),
                equalTo(new String[]{"true"}), instanceOf(Resources.NotFoundException.class));
    }

    public static final String TEST_OPEN_XML = "checkOpenXmlResourceParser";

    public static void checkOpenXmlResourceParser(Resources res, AssertionType type)
            throws Exception {
        testReadSuccessAndFailureException(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (XmlResourceParser p = assets.openXmlResourceParser(RES_XML_PATH)) {
                        XmlUtils.beginDocument(p, "Test");
                        assertEquals(XmlPullParser.START_TAG, p.next());
                        final String text = p.nextText();
                        return text == null ? "" : text.trim();
                    }
                },
                StringStartsWith.startsWith("Lorem ipsum dolor"),
                instanceOf(FileNotFoundException.class));
    }

    public static final String TEST_APPLY_STYLE = "checkApplyStyle";

    public static void checkApplyStyle(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailure(type,
                () -> {
                    final Resources.Theme theme = res.newTheme();
                    theme.applyStyle(RES_STYLE, true);
                    final TypedArray values = theme.obtainStyledAttributes(RES_STYLEABLE);
                    return new String[]{
                            values.getString(0),
                            values.getString(1),
                            values.getString(2),
                    };
                },
                equalTo(new String[]{"true", "true", "1"}),
                equalTo(new String[]{null, null, null}));
    }

    public static final String TEST_XML_ATTRIBUTES = "checkXmlAttributes";

    public static void checkXmlAttributes(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailure(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (XmlResourceParser p = assets.openXmlResourceParser(RES_XML_ATTRS_PATH)) {
                        XmlUtils.beginDocument(p, "Test");
                        final TypedArray values = res.obtainAttributes(p, RES_STYLEABLE);
                        final String[] results = {
                                values.getString(0),
                                values.getString(1),
                                values.getString(2),
                        };
                        values.recycle();
                        return results;
                    }
                },
                equalTo(new String[]{"true", "true", "1"}),
                equalTo(new String[]{null, null, null}));
    }

    public static final String TEST_OPEN_FILE_MISSING = "checkOpenMissingFile";

    public static void checkOpenMissingFile(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (InputStream is = assets.openNonAsset(RES_DRAWABLE_HDPI_PATH)) {
                        return true;
                    }
                },
                equalTo(true), instanceOf(FileNotFoundException.class));
    }

    public static final String TEST_OPEN_FILE_FD_MISSING = "checkOpenMissingFdFile";

    public static void checkOpenMissingFdFile(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (AssetFileDescriptor is = assets.openNonAssetFd(RES_DRAWABLE_HDPI_PATH)) {
                        return true;
                    }
                },
                equalTo(true), instanceOf(FileNotFoundException.class));
    }

    private static class FailedWhileReadingException extends Exception {
    }

    public static final String TEST_OPEN_FILE = "checkOpen";

    public static void checkOpen(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (InputStream is = assets.openNonAsset(RES_DRAWABLE_MDPI_PATH)) {
                        try {
                            readFullStream(is);
                        } catch (IOException e) {
                            throw new FailedWhileReadingException();
                        }
                        return true;
                    }
                }, equalTo(true), instanceOf(FailedWhileReadingException.class));
    }

    public static final String TEST_OPEN_FILE_FD = "checkOpenFd";

    public static void checkOpenFd(Resources res, AssertionType type) throws Exception {
        testReadSuccessAndFailureException(type,
                () -> {
                    final AssetManager assets = res.getAssets();
                    try (AssetFileDescriptor fd = assets.openNonAssetFd(RES_DRAWABLE_MDPI_PATH)) {
                        try {
                            readFullStream(fd.createInputStream());
                        } catch (IOException e) {
                            throw new FailedWhileReadingException();
                        }
                        return true;
                    }
                }, equalTo(true), instanceOf(FailedWhileReadingException.class));
    }

    /**
     * Used to wait for the process to wait for a particular broadcast to be received.
     */
    public static class BroadcastDetector implements AutoCloseable {
        private final Context mContext;
        private final HandlerThread mThread;
        private final Handler mHandler;
        private final BroadcastReceiver mReceiver;
        private final Semaphore mLatch = new Semaphore(0);
        private volatile Exception mException;

        public BroadcastDetector(Context context, IntentFilter intentFilter,
                IBroadcastCallback callback) {
            mContext = context;
            mThread = new HandlerThread(callback.toString(), Process.THREAD_PRIORITY_FOREGROUND);
            mThread.start();
            mHandler = new Handler(mThread.getLooper());
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (callback.onReceive(context, intent)) {
                            mLatch.release();
                        }
                    } catch (Exception e) {
                        mException = e;
                        mLatch.release();
                    }
                }
            };
            context.registerReceiver(mReceiver, intentFilter, null, mHandler, Context.RECEIVER_EXPORTED);
        }

        /**
         * Returns true if the broadcast was received and
         * {@link IBroadcastCallback#onReceive(Context, Intent)} returned true.
         */
        public boolean waitForBroadcast(long timeout, TimeUnit unit)
                throws Exception {
            if (!mLatch.tryAcquire(timeout, unit)) {
                return false;
            }
            if (mException != null) {
                throw mException;
            }
            return true;
        }

        @Override
        public void close() {
            mContext.unregisterReceiver(mReceiver);
            mThread.quit();
        }
    }

    public interface IBroadcastCallback {
        boolean onReceive(Context context, Intent intent) throws Exception;
    }

    private interface ThrowingFunction<R> {
        R apply() throws Exception;
    }

    private static <T> void testReadSuccessAndFailure(AssertionType assertType,
            ThrowingFunction<T> getValue,
            Matcher<? super T> checkSuccess,
            Matcher<? super T> checkFailure) throws Exception {
        final T value = getValue.apply();
        if (assertType == AssertionType.ASSERT_SUCCESS) {
            assertThat(value, checkSuccess);
        } else {
            assertThat(value, checkFailure);
        }
    }

    private static <T> void testReadSuccessAndFailureException(AssertionType assertType,
            ThrowingFunction<T> getValue,
            Matcher<? super T> checkSuccess,
            Matcher<Throwable> checkFailure) throws Exception {
        if (assertType == AssertionType.ASSERT_SUCCESS) {
            assertThat(getValue.apply(), checkSuccess);
        } else {
            MatcherUtils.assertThrows(checkFailure, getValue::apply);
        }
    }

    private static void readFullStream(InputStream is) throws IOException {
        final byte[] buffer = new byte[1024];
        while (is.read(buffer) != -1) {
        }
    }
}
