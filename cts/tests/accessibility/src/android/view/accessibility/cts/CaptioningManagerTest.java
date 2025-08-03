/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.view.accessibility.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.Manifest;
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.app.UiAutomation;
import android.content.res.Resources;
import android.os.ParcelFileDescriptor;
import android.view.accessibility.CaptioningManager;
import android.view.accessibility.CaptioningManager.CaptionStyle;
import android.view.accessibility.CaptioningManager.CaptioningChangeListener;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Tests whether the CaptioningManager APIs are functional.
 */
@RunWith(AndroidJUnit4.class)
public class CaptioningManagerTest {

    @Rule
    public final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private static final int LISTENER_TIMEOUT = 3000;
    private CaptioningManager mManager;
    private UiAutomation mUiAutomation;

    @Before
    public void setUp() throws Exception {
        mManager = getInstrumentation().getTargetContext().getSystemService(
                CaptioningManager.class);

        assertNotNull("Obtained captioning manager", mManager);

        mUiAutomation = getInstrumentation().getUiAutomation();
    }

    /**
     * Tests whether a client can observe changes in caption properties.
     */
    @Test
    public void testChangeListener() {
        putSecureSetting("accessibility_captioning_enabled","0");
        putSecureSetting("accessibility_captioning_preset", "1");
        putSecureSetting("accessibility_captioning_locale", "en_US");
        putSecureSetting("accessibility_captioning_font_scale", "1.0");
        // TODO (b209352162): We need to backup and restore the original values for
        //  these two setting keys.
        putSecureSetting("odi_captions_enabled", "0");
        putSecureSetting("odi_captions_volume_ui_enabled", "0");

        CaptioningChangeListener mockListener = mock(CaptioningChangeListener.class);
        mManager.addCaptioningChangeListener(mockListener);

        putSecureSetting("accessibility_captioning_enabled", "1");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onEnabledChanged(true);

        // Style change gets posted in a Runnable, so we need to wait for idle.
        putSecureSetting("accessibility_captioning_preset", "-1");
        getInstrumentation().waitForIdleSync();
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onUserStyleChanged(anyObject());

        putSecureSetting("accessibility_captioning_locale", "ja_JP");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onLocaleChanged(anyObject());

        putSecureSetting("accessibility_captioning_font_scale", "2.0");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onFontScaleChanged(anyFloat());

        putSecureSetting("odi_captions_enabled", "1");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onSystemAudioCaptioningChanged(true);

        putSecureSetting("odi_captions_volume_ui_enabled", "1");
        verify(mockListener, timeout(LISTENER_TIMEOUT)).onSystemAudioCaptioningUiChanged(true);

        mManager.removeCaptioningChangeListener(mockListener);

        Mockito.reset(mockListener);

        putSecureSetting("accessibility_captioning_enabled","0");
        putSecureSetting("odi_captions_enabled", "0");
        putSecureSetting("odi_captions_volume_ui_enabled", "0");
        verifyZeroInteractions(mockListener);

        try {
            mManager.removeCaptioningChangeListener(mockListener);
        } catch (Exception e) {
            throw new AssertionError("Fails silently when removing listener twice", e);
        }
    }

    @Test
    public void testProperties() {
        putSecureSetting("accessibility_captioning_font_scale", "2.0");
        putSecureSetting("accessibility_captioning_locale", "ja_JP");
        putSecureSetting("accessibility_captioning_enabled", "1");

        assertEquals("Test runner set font scale to 2.0", 2.0f, mManager.getFontScale(), 0f);
        assertEquals("Test runner set locale to Japanese", Locale.JAPAN, mManager.getLocale());
        assertEquals("Test runner set enabled to true", true, mManager.isEnabled());
    }

    @Test
    public void testUserStyle() {
        putSecureSetting("accessibility_captioning_preset", "-1");
        putSecureSetting("accessibility_captioning_foreground_color", "511");
        putSecureSetting("accessibility_captioning_background_color", "511");
        putSecureSetting("accessibility_captioning_window_color", "511");
        putSecureSetting("accessibility_captioning_edge_color", "511");
        putSecureSetting("accessibility_captioning_edge_type", "-1");
        deleteSecureSetting("accessibility_captioning_typeface");

        CaptionStyle userStyle = mManager.getUserStyle();
        assertNotNull("Default user style is not null", userStyle);
        assertFalse("Default user style has no edge type", userStyle.hasEdgeType());
        assertFalse("Default user style has no edge color", userStyle.hasEdgeColor());
        assertFalse("Default user style has no foreground color", userStyle.hasForegroundColor());
        assertFalse("Default user style has no background color", userStyle.hasBackgroundColor());
        assertFalse("Default user style has no window color", userStyle.hasWindowColor());
        assertNull("Default user style has no typeface", userStyle.getTypeface());
    }

    @Test
    public void testIsCallCaptioningEnabled() {
        Resources resources =
                getInstrumentation().getTargetContext().getResources();
        // com.android.internal.R is not visible to this test so we need
        // to query for the resource id.
        int resourceId = resources.getIdentifier(
                "config_systemCaptionsServiceCallsEnabled", "bool", "android");

        boolean expected = false;

        try {
            expected = resources.getBoolean(resourceId);
        } catch (Resources.NotFoundException e) {
            // If the resource isn't defined then the return value should be false
        }

        boolean actual = mManager.isCallCaptioningEnabled();

        assertEquals(expected, actual);
    }

    @Test(expected = SecurityException.class)
    public void testSetSystemAudioCaptionWithoutPermission_throwSecurityException() {
        mManager.setSystemAudioCaptioningEnabled(true);
    }

    @Test(expected = SecurityException.class)
    public void testSetSystemAudioCaptionUiWithoutPermission_throwSecurityException() {
        mManager.setSystemAudioCaptioningUiEnabled(true);
    }

    @Test
    public void testSystemAudioCaption() {
        putSecureSetting("odi_captions_enabled", "0");
        putSecureSetting("odi_captions_volume_ui_enabled", "0");
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.SET_SYSTEM_AUDIO_CAPTION);
        try {
            mManager.setSystemAudioCaptioningEnabled(true);
            assertTrue("Test runner set system audio caption enabled to true",
                    mManager.isSystemAudioCaptioningEnabled());

            mManager.setSystemAudioCaptioningUiEnabled(true);
            assertTrue("Test runner set system audio caption ui enabled to true",
                    mManager.isSystemAudioCaptioningUiEnabled());
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
            putSecureSetting("odi_captions_enabled", "0");
            putSecureSetting("odi_captions_volume_ui_enabled", "0");
        }
    }

    private void deleteSecureSetting(String name) {
        execShellCommand("settings delete secure " + name);
    }

    private void putSecureSetting(String name, String value) {
        execShellCommand("settings put secure " + name + " " + value);
    }

    private void execShellCommand(String cmd) {
        ParcelFileDescriptor pfd = mUiAutomation.executeShellCommand(cmd);
        InputStream is = new FileInputStream(pfd.getFileDescriptor());
        try {
            final byte[] buffer = new byte[8192];
            while ((is.read(buffer)) != -1);
        } catch (IOException e) {
            throw new RuntimeException("Failed to exec: " + cmd);
        }
    }
}
