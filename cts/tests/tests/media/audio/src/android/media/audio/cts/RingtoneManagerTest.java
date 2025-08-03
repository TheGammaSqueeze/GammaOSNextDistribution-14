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
package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.cts.Utils;
import android.net.Uri;
import android.os.ConditionVariable;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class RingtoneManagerTest {

    private static final String PKG = "android.media.audio.cts";
    private static final String TAG = "RingtoneManagerTest";

    private RingtonePickerActivity mActivity;
    private ActivityScenario<RingtonePickerActivity> mActivityScenario;
    private Instrumentation mInstrumentation;
    private Context mContext;
    private RingtoneManager mRingtoneManager;
    private AudioManager mAudioManager;
    private int mOriginalRingerMode;
    private Uri mDefaultUri;

    @Before
    public void setUp() throws Exception {
        mActivityScenario = ActivityScenario.launch(RingtonePickerActivity.class);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(10000);
        assertNotNull("Failed to acquire activity reference.", mActivity);

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        mInstrumentation.waitForIdleSync();

        Utils.enableAppOps(mContext.getPackageName(), "android:write_settings", mInstrumentation);
        mRingtoneManager = new RingtoneManager(mActivity);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        // backup ringer settings
        mDefaultUri = ContentProvider.maybeAddUserId(
                RingtoneManager.getActualDefaultRingtoneUri(
                        mContext, RingtoneManager.TYPE_RINGTONE), mContext.getUserId());

        mOriginalRingerMode = mAudioManager.getRingerMode();
        if (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            try {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), mInstrumentation, true);
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            } finally {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), mInstrumentation, false);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, true);
            // restore original ringer settings
            if (mAudioManager != null) {
                mAudioManager.setRingerMode(mOriginalRingerMode);
            }
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), mInstrumentation, false);
        }
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE,
                mDefaultUri);
        Utils.disableAppOps(mContext.getPackageName(), "android:write_settings", mInstrumentation);
    }

    private boolean isSupportedDevice() {
        final PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
                && !pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY);
    }

    @Test
    public void testConstructors() {
        if (!isSupportedDevice()) return;

        new RingtoneManager(mActivity);
        new RingtoneManager(mContext);
    }

    @Test
    public void testAccessMethods() {
        if (!isSupportedDevice()) return;

        Cursor c = mRingtoneManager.getCursor();
        assertTrue("Must have at least one ring tone available", c.getCount() > 0);

        assertNotNull(mRingtoneManager.getRingtone(0));
        assertNotNull(RingtoneManager.getRingtone(mContext, Settings.System.DEFAULT_RINGTONE_URI));
        int expectedPosition = 0;
        Uri uri = mRingtoneManager.getRingtoneUri(expectedPosition);
        assertEquals(expectedPosition, mRingtoneManager.getRingtonePosition(uri));
        assertNotNull(RingtoneManager.getValidRingtoneUri(mContext));
        uri = ContentProvider.maybeAddUserId(uri, mContext.getUserId());

        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE, uri);

        Uri actualDefaultRingtoneUri = ContentProvider.maybeAddUserId(
                RingtoneManager.getActualDefaultRingtoneUri(
                        mContext, RingtoneManager.TYPE_RINGTONE), mContext.getUserId());

        assertEquals(uri, actualDefaultRingtoneUri);

        try (AssetFileDescriptor afd = RingtoneManager.openDefaultRingtoneUri(
                mActivity, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))) {
            assertNotNull(afd);
        } catch (IOException e) {
            fail(e.getMessage());
        }

        Uri bogus = Uri.parse("content://a_bogus_uri");
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE, bogus);
        // not testing the matching getter after setting a bogus URI as ringtone
        //assertNotEquals(bogus, RingtoneManager.getActualDefaultRingtoneUri(mContext,
        //        RingtoneManager.TYPE_RINGTONE));

        assertEquals(Settings.System.DEFAULT_RINGTONE_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        assertEquals(RingtoneManager.TYPE_RINGTONE,
                RingtoneManager.getDefaultType(Settings.System.DEFAULT_RINGTONE_URI));
        assertEquals(RingtoneManager.TYPE_NOTIFICATION,
                RingtoneManager.getDefaultType(Settings.System.DEFAULT_NOTIFICATION_URI));
        assertTrue(RingtoneManager.isDefault(Settings.System.DEFAULT_RINGTONE_URI));
    }

    @Test
    public void testSetType() {
        if (!isSupportedDevice()) return;

        mRingtoneManager.setType(RingtoneManager.TYPE_ALARM);
        assertEquals(AudioManager.STREAM_ALARM, mRingtoneManager.inferStreamType());
        Cursor c = mRingtoneManager.getCursor();
        assertTrue("Must have at least one alarm tone available", c.getCount() > 0);
        Ringtone r = mRingtoneManager.getRingtone(0);
        assertEquals(RingtoneManager.TYPE_ALARM, r.getStreamType());
    }

    @Test
    public void testStopPreviousRingtone() {
        if (!isSupportedDevice()) return;

        Cursor c = mRingtoneManager.getCursor();
        assertTrue("Must have at least one ring tone available", c.getCount() > 0);

        mRingtoneManager.setStopPreviousRingtone(true);
        assertTrue(mRingtoneManager.getStopPreviousRingtone());
        Uri uri = Uri.parse("android.resource://" + PKG + "/" + R.raw.john_cage);
        Ringtone ringtone = RingtoneManager.getRingtone(mContext, uri);
        ringtone.play();
        assertTrue(ringtone.isPlaying());
        ringtone.stop();
        assertFalse(ringtone.isPlaying());
        Ringtone newRingtone = mRingtoneManager.getRingtone(0);
        assertFalse(ringtone.isPlaying());
        newRingtone.play();
        assertTrue(newRingtone.isPlaying());
        mRingtoneManager.stopPreviousRingtone();
        assertFalse(newRingtone.isPlaying());
    }

    @Test
    public void testQuery() {
        if (!isSupportedDevice()) return;

        final Cursor c = mRingtoneManager.getCursor();
        assertTrue(c.moveToFirst());
        assertTrue(c.getInt(RingtoneManager.ID_COLUMN_INDEX) >= 0);
        assertTrue(c.getString(RingtoneManager.TITLE_COLUMN_INDEX) != null);
        assertTrue(c.getString(RingtoneManager.URI_COLUMN_INDEX),
                c.getString(RingtoneManager.URI_COLUMN_INDEX).startsWith("content://"));
    }

    @Test
    public void testHasHapticChannels() {
        if (!isSupportedDevice()) return;

        Cursor c = mRingtoneManager.getCursor();
        assertTrue("Must have at lease one ringtone available", c.getCount() > 0);
        mRingtoneManager.hasHapticChannels(0);

        final String uriPrefix = ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                mContext.getPackageName() + "/raw/";
        assertTrue(RingtoneManager.hasHapticChannels(Uri.parse(uriPrefix + "a_4_haptic")));
        assertFalse(RingtoneManager.hasHapticChannels(Uri.parse(uriPrefix + "a_4")));

        assertTrue(RingtoneManager.hasHapticChannels(
                mContext, Uri.parse(uriPrefix + "a_4_haptic")));
        assertFalse(RingtoneManager.hasHapticChannels(mContext, Uri.parse(uriPrefix + "a_4")));
    }
}
