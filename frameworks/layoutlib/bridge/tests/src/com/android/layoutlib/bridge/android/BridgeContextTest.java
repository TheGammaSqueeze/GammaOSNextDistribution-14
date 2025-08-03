/*
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

package com.android.layoutlib.bridge.android;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.impl.RenderAction;
import com.android.layoutlib.bridge.impl.RenderActionTestUtil;
import com.android.layoutlib.bridge.intensive.LayoutLibTestCallback;
import com.android.layoutlib.bridge.intensive.setup.ConfigGenerator;
import com.android.layoutlib.bridge.intensive.setup.LayoutPullParser;

import org.junit.BeforeClass;
import org.junit.Test;

import android.R.attr;
import android.R.style;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BridgeContextTest extends RenderTestBase {
    @BeforeClass
    public static void setUp() {
        Bridge.prepareThread();
    }

    @Test
    public void basic() throws ClassNotFoundException {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());

        context.initResources(params.getAssets());
        BridgeContext oldContext = RenderActionTestUtil.setBridgeContext(context);
        try {
            Context themeContext = new ContextThemeWrapper(context, style.Theme_Material);
            // First we try to get the style from the ?attr/editTextStyle fallback value.
            // We pass an invalid value to defStyleRes
            TypedArray array = themeContext.obtainStyledAttributes(null,
                            new int[]{attr.clickable}, attr.editTextStyle, Integer.MAX_VALUE);
            assertTrue(array.getBoolean(0, false));
            // Now, we try to get it directly from the Widget.EditText. We pass an invalid value
            // to defStyleAttr so it fails and falls back to the defStyleRes
            array = themeContext.obtainStyledAttributes(null,
                    new int[]{attr.clickable}, Integer.MAX_VALUE,
                    style.Widget_EditText);
            assertTrue(array.getBoolean(0, false));

        } finally {
            RenderActionTestUtil.setBridgeContext(oldContext);
            context.disposeResources();
        }

        // This message is expected when asking for an invalid defStyleAttr
        sRenderMessages.removeIf(msg ->
                msg.startsWith("Failed to find the style corresponding to the id"));
    }

    @Test
    public void checkNoErrorWhenUsingDefaults() throws ClassNotFoundException {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());

        context.initResources(params.getAssets());
        BridgeContext oldContext = RenderActionTestUtil.setBridgeContext(context);
        try {
            Context themeContext = new ContextThemeWrapper(context, style.Theme_Material);
            // First we try to get the style from the ?attr/editTextStyle fallback value.
            // We pass an invalid value to defStyleRes
            themeContext.obtainStyledAttributes(null,
                    new int[]{attr.clickable}, 0, style.Widget_EditText);
            themeContext.obtainStyledAttributes(null,
                    new int[]{attr.clickable}, attr.editTextStyle, 0);
        } finally {
            RenderActionTestUtil.setBridgeContext(oldContext);
            context.disposeResources();
        }
    }

    @Test
    public void noExceptionForCustomService() throws ClassNotFoundException {
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());

        assertNull(context.getSystemService("my_custom_service"));
        sRenderMessages.removeIf(message -> message.equals("Service my_custom_service was not found or is unsupported"));
    }

    @Test
    public void dynamicTheming() throws ClassNotFoundException {
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .setTheme("Theme.Material", false)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context = new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                params.getAssets(), params.getLayoutlibCallback(), configuration,
                params.getTargetSdkVersion(), params.isRtlSupported());
        context.initResources(params.getAssets());
        try {
            assertEquals(-13749965, context.getResources().getColor(android.R.color.system_neutral1_800, null));

            ((DynamicRenderResources) context.getRenderResources()).setWallpaper(
                    "/com/android/layoutlib/testdata/wallpaper1.webp",
                    configuration.isNightModeActive());
            assertEquals(-13029845, context.getResources().getColor(android.R.color.system_neutral1_800, null));

            ((DynamicRenderResources) context.getRenderResources()).setWallpaper(
                    "/com/android/layoutlib/testdata/wallpaper2.webp",
                    configuration.isNightModeActive());
            assertEquals(-13946321, context.getResources().getColor(android.R.color.system_neutral1_800, null));
        } finally {
            context.disposeResources();
        }
    }

    @Test
    public void noCrashInPowerManager() throws ClassNotFoundException {
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder().setParser(parser).setCallback(layoutLibCallback).setTheme(
                "Theme.Material", false).build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);
        BridgeContext context =
                new BridgeContext(params.getProjectKey(), metrics, params.getResources(), params.getAssets(), params.getLayoutlibCallback(), configuration,
                        params.getTargetSdkVersion(), params.isRtlSupported());

        PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        // Check that the following calls do not trigger a native crash
        assertFalse(powerManager.isPowerSaveMode());
        assertTrue(powerManager.isInteractive());
    }

    @Test
    public void testTypedValue() throws Exception {
        // Setup
        // Create the layout pull parser for our resources (empty.xml can not be part of the test
        // app as it won't compile).
        LayoutPullParser parser = LayoutPullParser.createFromPath("/empty.xml");
        // Create LayoutLibCallback.
        LayoutLibTestCallback layoutLibCallback =
                new LayoutLibTestCallback(RenderTestBase.getLogger(), mDefaultClassLoader);
        layoutLibCallback.initResources();
        SessionParams params = getSessionParamsBuilder()
                .setConfigGenerator(ConfigGenerator.NEXUS_4)
                .setParser(parser)
                .setCallback(layoutLibCallback)
                .build();
        DisplayMetrics metrics = new DisplayMetrics();
        Configuration configuration = RenderAction.getConfiguration(params);

        BridgeContext mContext =
                new BridgeContext(params.getProjectKey(), metrics, params.getResources(),
                        params.getAssets(), params.getLayoutlibCallback(), configuration,
                        params.getTargetSdkVersion(), params.isRtlSupported());

        TypedValue outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.colorPrimary, outValue, true);
        assertEquals(TypedValue.TYPE_INT_COLOR_ARGB8, outValue.type);
        assertNotEquals(0, outValue.data);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.colorError, outValue, true);
        assertEquals(TypedValue.TYPE_INT_COLOR_RGB4, outValue.type);
        assertEquals(-65536, outValue.data);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(attr.colorActivatedHighlight, outValue, true);
        assertEquals(TypedValue.TYPE_INT_COLOR_ARGB4, outValue.type);
        assertEquals(-872349952, outValue.data);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.isLightTheme, outValue, true);
        assertEquals(TypedValue.TYPE_INT_BOOLEAN, outValue.type);
        assertEquals(1, outValue.data);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.scrollbarFadeDuration, outValue, true);
        assertEquals(TypedValue.TYPE_INT_DEC, outValue.type);
        assertEquals(250, outValue.data);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.scrollbarThumbHorizontal, outValue, true);
        assertEquals(TypedValue.TYPE_STRING, outValue.type);
        assertNotNull(outValue.string);

        outValue = new TypedValue();
        mContext.resolveThemeAttribute(android.R.attr.actionBarSize, outValue, true);
        assertEquals(TypedValue.TYPE_DIMENSION, outValue.type);
        assertEquals(14337, outValue.data);
    }
}
