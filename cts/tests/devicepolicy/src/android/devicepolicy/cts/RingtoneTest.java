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

package android.devicepolicy.cts;

import static android.Manifest.permission.WRITE_SETTINGS;
import static android.provider.Settings.Secure.SYNC_PARENT_SOUNDS;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@Postsubmit(reason = "New tests")
public final class RingtoneTest {

    private enum RingtoneConfig {
        RINGTONE(Settings.System.RINGTONE, RingtoneManager.TYPE_RINGTONE),
        NOTIFICATION(Settings.System.NOTIFICATION_SOUND, RingtoneManager.TYPE_NOTIFICATION),
        ALARM(Settings.System.ALARM_ALERT, RingtoneManager.TYPE_ALARM);

        final String mRingtoneName;
        final int mType;

        RingtoneConfig(String ringtoneName, int type) {
            this.mRingtoneName = ringtoneName;
            this.mType = type;
        }
    }

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final Uri RINGTONE_URI = Uri.parse("http://uri.does.not.matter");

    // TODO(b/194509745): Parameterize on different user types

    @Test
    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
    public void getActualDefaultRingtoneUri_matchesSettingsProviderRingtone(
            @EnumTestParameter(RingtoneConfig.class) RingtoneConfig config) {
        String defaultRingtone = Settings.System.getString(
                sContext.getContentResolver(), config.mRingtoneName);
        Uri expectedUri = getUriWithoutUserId(defaultRingtone);
        Uri actualRingtoneUri = getUriWithoutUserId(
                RingtoneManager.getActualDefaultRingtoneUri(
                        sContext, config.mType));

        assertThat(expectedUri).isEqualTo(actualRingtoneUri);
    }

    @Test
    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
    @EnsureHasPermission(WRITE_SETTINGS)
    public void getActualDefaultRingtoneUri_syncParentSoundsIsTrue_returnsDefaultRingtone(
            @EnumTestParameter(RingtoneConfig.class) RingtoneConfig config) {
        int originalSyncParentSounds = TestApis.settings().secure().getInt(SYNC_PARENT_SOUNDS);
        Uri originalUri = RingtoneManager.getActualDefaultRingtoneUri(
                sContext, config.mType);
        try {
            RingtoneManager.setActualDefaultRingtoneUri(
                    sContext, config.mType, RINGTONE_URI);
            TestApis.settings().secure().putInt(SYNC_PARENT_SOUNDS, 1);

            assertThat(RingtoneManager.getActualDefaultRingtoneUri(
                    sContext, config.mType)).isEqualTo(originalUri);
        } finally {
            RingtoneManager.setActualDefaultRingtoneUri(
                    sContext, config.mType, originalUri);
            TestApis.settings().secure().putInt(SYNC_PARENT_SOUNDS, originalSyncParentSounds);
        }
    }

    private static Uri getUriWithoutUserId(String uriString) {
        if (uriString == null) {
            return null;
        }
        return getUriWithoutUserId(Uri.parse(uriString));
    }

    /** Copied from {@link android.content.ContentProvider} */
    private static Uri getUriWithoutUserId(Uri uri) {
        if (uri == null) {
            return null;
        }
        Uri.Builder builder = uri.buildUpon();
        builder.authority(getAuthorityWithoutUserId(uri.getAuthority()));
        return builder.build();
    }

    /** Copied from {@link android.content.ContentProvider} */
    private static String getAuthorityWithoutUserId(String auth) {
        if (auth == null) {
            return null;
        }
        int end = auth.lastIndexOf('@');
        return auth.substring(end + 1);
    }
}
