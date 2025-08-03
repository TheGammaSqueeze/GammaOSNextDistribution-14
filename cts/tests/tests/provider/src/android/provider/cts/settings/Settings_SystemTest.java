/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.provider.cts.settings;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.testng.Assert.expectThrows;

import android.content.ContentResolver;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import com.google.common.base.Strings;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Settings_SystemTest extends StsExtraBusinessLogicTestCase {
    private static final String INT_FIELD = System.END_BUTTON_BEHAVIOR;
    private static final String LONG_FIELD = System.SCREEN_OFF_TIMEOUT;
    private static final String FLOAT_FIELD = System.FONT_SCALE;
    private static final String STRING_FIELD = System.VOLUME_RING;

    private static final int sUserId = Process.myUserHandle().getIdentifier();
    private static final String sPackageName =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    private final ContentResolver mContentResolver =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getContentResolver();

    @BeforeClass
    public static void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set --user " + sUserId + " " + sPackageName
                        + " android:write_settings allow");

        // Wait a beat to persist the change
        SystemClock.sleep(500);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set --user " + sUserId + " " + sPackageName
                        + " android:write_settings default");
    }

    @Test
    public void testSystemSettings() throws SettingNotFoundException {
        /**
         * first query the existing settings in System table, and then insert four
         * rows: an int, a long, a float, a String.
         * Get these four rows to check whether insert succeeded and then restore the original
         * values.
         */

        // first query existing rows
        Cursor c = mContentResolver.query(System.CONTENT_URI, null, null, null, null);

        // backup fontScale
        Configuration cfg = new Configuration();
        System.getConfiguration(mContentResolver, cfg);
        float store = cfg.fontScale;

        //store all original values
        final String originalIntValue = System.getString(mContentResolver, INT_FIELD);
        final String originalLongValue = System.getString(mContentResolver, LONG_FIELD);
        final String originalStringValue = System.getString(mContentResolver, STRING_FIELD);

        try {
            assertNotNull(c);
            c.close();

            String stringValue = "cts";

            // insert 4 rows, and update 1 rows
            assertTrue(System.putInt(mContentResolver, INT_FIELD, 2));
            assertTrue(System.putLong(mContentResolver, LONG_FIELD, 20L));
            assertTrue(System.putFloat(mContentResolver, FLOAT_FIELD, 1.3f));
            assertTrue(System.putString(mContentResolver, STRING_FIELD, stringValue));

            c = mContentResolver.query(System.CONTENT_URI, null, null, null, null);
            assertNotNull(c);
            c.close();

            // get these rows to assert
            assertEquals(2, System.getInt(mContentResolver, INT_FIELD));
            assertEquals(20L, System.getLong(mContentResolver, LONG_FIELD));
            assertEquals(1.3f, System.getFloat(mContentResolver, FLOAT_FIELD), 0.001);
            assertEquals(stringValue, System.getString(mContentResolver, STRING_FIELD));

            c = mContentResolver.query(System.CONTENT_URI, null, null, null, null);
            assertNotNull(c);

            // update fontScale row
            cfg = new Configuration();
            cfg.fontScale = 1.2f;
            assertTrue(System.putConfiguration(mContentResolver, cfg));

            System.getConfiguration(mContentResolver, cfg);
            assertEquals(1.2f, cfg.fontScale, 0.001);
        } finally {
            // TODO should clean up more better
            c.close();

            //Restore all original values into system
            assertTrue(System.putString(mContentResolver, INT_FIELD, originalIntValue));
            assertTrue(System.putString(mContentResolver, LONG_FIELD, originalLongValue));
            assertTrue(System.putString(mContentResolver, STRING_FIELD, originalStringValue));

            // restore the fontScale
            try {
                // Delay helps ActivityManager in completing its previous font-change processing.
                Thread.sleep(1000);
            } catch (Exception e){}

            cfg.fontScale = store;
            assertTrue(System.putConfiguration(mContentResolver, cfg));
        }
    }

    /**
     * Verifies that the invalid values for the font scale setting are rejected.
     */
    @Test
    @AsbSecurityTest(cveBugId = 156260178)
    public void testSystemSettingsRejectInvalidFontSizeScale() throws SettingNotFoundException {
        final String originalFloatValue = System.getString(mContentResolver, FLOAT_FIELD);
        try {
            // First put in a valid value
            assertTrue(System.putFloat(mContentResolver, FLOAT_FIELD, 1.15f));
            assertEquals(1.15f, System.getFloat(mContentResolver, FLOAT_FIELD), 0.001);
            try {
                assertFalse(System.putFloat(mContentResolver, FLOAT_FIELD, Float.MAX_VALUE));
                fail("Should throw");
            } catch (IllegalArgumentException e) {
            }
            try {
                assertFalse(System.putFloat(mContentResolver, FLOAT_FIELD, -1f));
                fail("Should throw");
            } catch (IllegalArgumentException e) {
            }
            try {
                assertFalse(System.putFloat(mContentResolver, FLOAT_FIELD, 0.1f));
                fail("Should throw");
            } catch (IllegalArgumentException e) {
            }
            try {
                assertFalse(System.putFloat(mContentResolver, FLOAT_FIELD, 30.0f));
                fail("Should throw");
            } catch (IllegalArgumentException e) {
            }
            assertEquals(1.15f, System.getFloat(mContentResolver, FLOAT_FIELD), 0.001);
        } finally {
            assertTrue(System.putString(mContentResolver, FLOAT_FIELD, originalFloatValue));
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 227201030)
    public void testInvalidRingtoneUriIsRejected() {
        final String originalValue = System.getString(mContentResolver, System.RINGTONE);
        final String invalidUri = "content://10@media/external/audio/media/1000000019";
        try {
            System.putString(mContentResolver, System.RINGTONE, invalidUri);
        } catch (Exception ignored) {
            // Some implementation of SettingsProvider might throw an exception here, which
            // is okay, as long as the insertion of the invalid setting fails in the end
        }
        // Assert that the insertion didn't take effect
        assertThat(System.getString(mContentResolver, System.RINGTONE)).isEqualTo(originalValue);
    }

    @Test
    @AsbSecurityTest(cveBugId = 227201030)
    public void testInvalidAlarmAlertUriIsRejected() {
        final String originalValue = System.getString(mContentResolver, System.NOTIFICATION_SOUND);
        final String invalidUri = "content://10@media/external/audio/media/1000000019";
        try {
            System.putString(mContentResolver, System.NOTIFICATION_SOUND, invalidUri);
        } catch (Exception ignored) {
        }
        // Assert that the insertion didn't take effect
        assertThat(System.getString(mContentResolver, System.NOTIFICATION_SOUND))
                .isEqualTo(originalValue);
    }

    @Test
    @AsbSecurityTest(cveBugId = 227201030)
    public void testInvalidNotificationSoundUriIsRejected() {
        final String originalValue = System.getString(mContentResolver, System.ALARM_ALERT);
        final String invalidUri = "content://10@media/external/audio/media/1000000019";
        try {
            System.putString(mContentResolver, System.ALARM_ALERT, invalidUri);
        } catch (Exception ignored) {
        }
        // Assert that the insertion didn't take effect
        assertThat(System.getString(mContentResolver, System.ALARM_ALERT)).isEqualTo(originalValue);
    }

    @Test
    public void testGetDefaultValues() {
        assertEquals(10, System.getInt(mContentResolver, "int", 10));
        assertEquals(20, System.getLong(mContentResolver, "long", 20L));
        assertEquals(30.0f, System.getFloat(mContentResolver, "float", 30.0f), 0.001);
    }

    @Test
    public void testGetUriFor() {
        String name = "table";

        Uri uri = System.getUriFor(name);
        assertNotNull(uri);
        assertEquals(Uri.withAppendedPath(System.CONTENT_URI, name), uri);
    }

    @Test
    public void testLargeSettingExceedsLimit() {
        // Test large value
        expectThrows(IllegalArgumentException.class,
                () -> System.putString(
                        mContentResolver, STRING_FIELD, Strings.repeat("A", 65535)));
        // Test large key
        expectThrows(IllegalArgumentException.class,
                () -> System.putString(
                        mContentResolver, Strings.repeat("A", 65535), "test"));
    }

    @Test
    public void testResetToDefaults() {
        final String oldStringValue = System.getString(mContentResolver, STRING_FIELD);
        final String newStringValue = "tmp";
        System.putString(mContentResolver, STRING_FIELD, oldStringValue, /* makeDefault= */true,
                false);
        System.putString(mContentResolver, STRING_FIELD, newStringValue, /* makeDefault= */false,
                false);
        assertEquals(newStringValue, System.getString(mContentResolver, STRING_FIELD));

        System.resetToDefaults(mContentResolver, null);
        assertEquals(oldStringValue, System.getString(mContentResolver, STRING_FIELD));
    }
}
