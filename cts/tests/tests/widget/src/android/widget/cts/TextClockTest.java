/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.util.MutableBoolean;
import android.widget.TextClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UserSettings;
import com.android.compatibility.common.util.UserSettings.Namespace;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link TextClock}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextClockTest {

    private static final String TAG = TextClockTest.class.getSimpleName();

    private Activity mActivity;
    private TextClock mTextClock;
    private String mDefaultTime1224;

    private final UserSettings mSystemSettings = new UserSettings(Namespace.SYSTEM);

    @Rule
    public ActivityTestRule<TextClockCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextClockCtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        mTextClock = mActivity.findViewById(R.id.textclock);
        mDefaultTime1224 = mSystemSettings.get(Settings.System.TIME_12_24);
        Log.d(TAG, "Initial value of TIME_12_24 setting: " + mDefaultTime1224);
    }

    @After
    public void teardown() throws Throwable {
        setTime1224(mDefaultTime1224);
    }

    @Test
    public void testUpdate12_24() throws Throwable {
        grantWriteSettingsPermission();

        mActivityRule.runOnUiThread(() -> {
            mTextClock.setFormat12Hour("h");
            mTextClock.setFormat24Hour("H");
            mTextClock.disableClockTick();
        });

        final ContentResolver resolver = mActivity.getContentResolver();
        Calendar mNow = Calendar.getInstance();
        mNow.setTimeInMillis(System.currentTimeMillis()); // just like TextClock uses

        // make sure the clock is showing some time > 12pm and not near midnight
        for (String id : TimeZone.getAvailableIDs()) {
            final TimeZone timeZone = TimeZone.getTimeZone(id);
            mNow.setTimeZone(timeZone);
            int hour = mNow.get(Calendar.HOUR_OF_DAY);
            if (hour < 22 && hour > 12) {
                mActivityRule.runOnUiThread(() -> {
                    mTextClock.setTimeZone(id);
                });
                break;
            }
        }

        // If the time was already set to 12, we want it to start at locale-specified
        if (mDefaultTime1224 != null) {
            final CountDownLatch changeDefault = registerForChanges(Settings.System.TIME_12_24);
            mActivityRule.runOnUiThread(() -> setTime1224(null));
            assertTrue(changeDefault.await(1, TimeUnit.SECONDS));
        }

        // Change to 12-hour mode
        final CountDownLatch change12 = registerForChanges(Settings.System.TIME_12_24);
        mActivityRule.runOnUiThread(() -> setTime1224(12));
        assertTrue(change12.await(1, TimeUnit.SECONDS));

        // Must poll here because there are no timing guarantees for ContentObserver notification
        PollingCheck.waitFor(() -> {
            final MutableBoolean ok = new MutableBoolean(false);
            try {
                mActivityRule.runOnUiThread(() -> {
                    int hour = Integer.parseInt(mTextClock.getText().toString());
                    ok.value = hour >= 1 && hour < 12;
                });
            } catch (Throwable t) {
                throw new RuntimeException(t.getMessage());
            }
            return ok.value;
        });

        // Change to 24-hour mode
        final CountDownLatch change24 = registerForChanges(Settings.System.TIME_12_24);
        mActivityRule.runOnUiThread(() -> setTime1224(24));
        assertTrue(change24.await(1, TimeUnit.SECONDS));

        // Must poll here because there are no timing guarantees for ContentObserver notification
        PollingCheck.waitFor(() -> {
            final MutableBoolean ok = new MutableBoolean(false);
            try {
                mActivityRule.runOnUiThread(() -> {
                    int hour = Integer.parseInt(mTextClock.getText().toString());
                    ok.value = hour > 12 && hour < 24;
                });
                return ok.value;
            } catch (Throwable t) {
                throw new RuntimeException(t.getMessage());
            }
        });
    }

    @Test
    public void testNoChange() throws Throwable {
        grantWriteSettingsPermission();
        mActivityRule.runOnUiThread(() -> mTextClock.disableClockTick());
        final ContentResolver resolver = mActivity.getContentResolver();

        // Now test that it isn't updated when a non-12/24 hour setting is set
        mActivityRule.runOnUiThread(() -> mTextClock.setText("Nothing"));

        mActivityRule.runOnUiThread(() -> assertEquals("Nothing", mTextClock.getText().toString()));

        final CountDownLatch otherChange = registerForChanges(Settings.System.TEXT_AUTO_CAPS);
        mActivityRule.runOnUiThread(() -> {
            int oldAutoCaps = Settings.System.getInt(resolver, Settings.System.TEXT_AUTO_CAPS,
                    1);
            try {
                int newAutoCaps = oldAutoCaps == 0 ? 1 : 0;
                Settings.System.putInt(resolver, Settings.System.TEXT_AUTO_CAPS, newAutoCaps);
            } finally {
                Settings.System.putInt(resolver, Settings.System.TEXT_AUTO_CAPS, oldAutoCaps);
            }
        });

        assertTrue(otherChange.await(1, TimeUnit.SECONDS));

        mActivityRule.runOnUiThread(() -> assertEquals("Nothing", mTextClock.getText().toString()));
    }

    private CountDownLatch registerForChanges(String setting) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);

        mActivityRule.runOnUiThread(() -> {
            final ContentResolver resolver = mActivity.getContentResolver();
            Uri uri = Settings.System.getUriFor(setting);
            resolver.registerContentObserver(uri, true,
                    new ContentObserver(new Handler()) {
                        @Override
                        public void onChange(boolean selfChange) {
                            countDownAndRemove();
                        }

                        private void countDownAndRemove() {
                            latch.countDown();
                            resolver.unregisterContentObserver(this);
                        }
                    });
        });
        return latch;
    }

    private void setTime1224(String time) {
        Log.d(TAG, "Setting time 12/24 to '" + time + "'");
        mSystemSettings.set(Settings.System.TIME_12_24, time);
    }

    private void setTime1224(int time) {
        setTime1224(String.valueOf(time));
    }

    private void grantWriteSettingsPermission() throws IOException {
        int userId = Process.myUserHandle().getIdentifier();
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "appops set --user " + userId  + " " + mActivity.getPackageName() + " "
                        + AppOpsManager.OPSTR_WRITE_SETTINGS + " allow");
    }
}
