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

package android.cts.statsdatom.media;

import static android.media.drm.Enums.DrmScheme.CLEAR_KEY_DASH_IF;
import static android.media.drm.Enums.IDrmFrontend.IDRM_JNI;
import static android.media.drm.Enums.SecurityLevel.SECURITY_LEVEL_MAX;
import static android.media.drm.Enums.SecurityLevel.SECURITY_LEVEL_SW_SECURE_CRYPTO;
import static android.media.drm.Enums.Status.ERROR_INVALID_STATE;
import static android.media.drm.Enums.DrmApi.DRM_API_INIT_CHECK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.os.media.MediaDrmAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import com.google.common.truth.StandardSubjectBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MediaDrmAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final UUID CLEAR_KEY_UUID = new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        // Put a delay to give statsd enough time to remove previous configs and
        // reports, as well as install the test app.
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_APK,
                DeviceUtils.STATSD_ATOM_TEST_PKG, mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testMediaDrmAtom() throws Throwable {
        // Upload the config.
        ConfigUtils.uploadConfigForPushedAtoms(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG,
                new int[]{
                        AtomsProto.Atom.MEDIA_DRM_CREATED_FIELD_NUMBER,
                        AtomsProto.Atom.MEDIA_DRM_ERRORED_FIELD_NUMBER,
                        AtomsProto.Atom.MEDIA_DRM_SESSION_OPENED_FIELD_NUMBER
                });
        // Trigger AtomTests.
        DeviceUtils.runDeviceTests(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG, ".AtomTests",
                "testMediaDrmAtoms");
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);

        List<MediaDrmAtoms.MediaDrmCreated> createdList = new ArrayList<>();
        List<MediaDrmAtoms.MediaDrmSessionOpened> openedList = new ArrayList<>();
        List<MediaDrmAtoms.MediaDrmErrored> erroredList = new ArrayList<>();
        int testAppUid = DeviceUtils.getStatsdTestAppUid(getDevice());

        for (StatsLog.EventMetricData event : data) {
            MediaDrmAtoms.MediaDrmCreated created = event.getAtom().getMediaDrmCreated();
            MediaDrmAtoms.MediaDrmSessionOpened opened = event.getAtom().getMediaDrmSessionOpened();
            MediaDrmAtoms.MediaDrmErrored errored = event.getAtom().getMediaDrmErrored();

            if (created != null && created.getUid() == testAppUid) {
                createdList.add(created);
            }

            if (opened != null && opened.getUid() == testAppUid) {
                openedList.add(opened);
            }

            if (errored != null && errored.getUid() == testAppUid
                    && errored.getApi() != DRM_API_INIT_CHECK) {
                erroredList.add(errored);
            }
        }
        // verify the events
        assertThat(createdList.size()).isEqualTo(1);
        validateMediaDrmCreated(createdList.get(0));

        assertThat(openedList.size()).isEqualTo(2);
        for (int i = 0; i < openedList.size(); i++) {
            MediaDrmAtoms.MediaDrmSessionOpened opened = openedList.get(i);
            validateMediaDrmOpenedListItem(openedList, i);
        }
        assertThat(openedList.get(0).getObjectNonce()).isEqualTo(
                openedList.get(1).getObjectNonce());

        assertThat(erroredList.size()).isEqualTo(2);
        for (int i = 0; i < erroredList.size(); i++) {
            validateMediaDrmErroredListItem(erroredList, i);
        }
        assertThat(erroredList.get(0).getObjectNonce()).isEqualTo(
                openedList.get(0).getObjectNonce());
        assertThat(erroredList.get(0).getObjectNonce()).isEqualTo(
                erroredList.get(1).getObjectNonce());
        assertThat(erroredList.get(0).getSessionNonce()).isNotEqualTo(
                erroredList.get(1).getSessionNonce());
    }
    // validating the created media drm events
    private void validateMediaDrmCreated(MediaDrmAtoms.MediaDrmCreated created) {
        assertThat(created.getScheme()).isEqualTo(CLEAR_KEY_DASH_IF);
        assertThat(created.getUuidMsb())
                .isEqualTo(CLEAR_KEY_UUID.getMostSignificantBits());
        assertThat(created.getUuidLsb())
                .isEqualTo(CLEAR_KEY_UUID.getLeastSignificantBits());
        assertThat(created.getFrontend()).isEqualTo(IDRM_JNI);
        assertThat(created.getVersion()).isNotEmpty();
    }
    // validating the opened media drm events
    private void validateMediaDrmOpenedListItem(
            List<MediaDrmAtoms.MediaDrmSessionOpened> openedList, int index) {
        MediaDrmAtoms.MediaDrmSessionOpened opened = openedList.get(index);
        StandardSubjectBuilder _assert = assertWithMessage(
                "index %s atom %s", index, opened);
        _assert.that(opened.getScheme()).isEqualTo(CLEAR_KEY_DASH_IF);
        _assert.that(opened.getUuidMsb())
                .isEqualTo(CLEAR_KEY_UUID.getMostSignificantBits());
        _assert.that(opened.getUuidLsb())
                .isEqualTo(CLEAR_KEY_UUID.getLeastSignificantBits());
        _assert.that(opened.getFrontend()).isEqualTo(IDRM_JNI);
        _assert.that(opened.getVersion()).isNotEmpty();
        _assert.that(opened.getObjectNonce()).isNotEmpty();
        _assert.that(opened.getRequestedSecurityLevel()).isEqualTo(SECURITY_LEVEL_MAX);
        _assert.that(opened.getOpenedSecurityLevel()).isEqualTo(SECURITY_LEVEL_SW_SECURE_CRYPTO);
    }
    // validating the errored media drm events
    private void validateMediaDrmErroredListItem(List<MediaDrmAtoms.MediaDrmErrored> erroredList,
            int index) {
        MediaDrmAtoms.MediaDrmErrored errored = erroredList.get(index);
        StandardSubjectBuilder _assert = assertWithMessage(
                "index %s atom %s", index, errored);
        _assert.that(errored.getScheme()).isEqualTo(CLEAR_KEY_DASH_IF);
        _assert.that(errored.getUuidMsb())
                .isEqualTo(CLEAR_KEY_UUID.getMostSignificantBits());
        _assert.that(errored.getUuidLsb())
                .isEqualTo(CLEAR_KEY_UUID.getLeastSignificantBits());
        _assert.that(errored.getFrontend()).isEqualTo(IDRM_JNI);
        _assert.that(errored.getVersion()).isNotEmpty();
        _assert.that(errored.getObjectNonce()).isNotEmpty();
        _assert.that(errored.getSessionNonce()).isNotEmpty();
        _assert.that(errored.getSecurityLevel()).isEqualTo(SECURITY_LEVEL_SW_SECURE_CRYPTO);
        _assert.that(errored.getErrorCode()).isEqualTo(ERROR_INVALID_STATE);

        int version;
        try {
            version = Integer.parseInt(errored.getVersion());
        } catch (NumberFormatException e) {
            version = Integer.MIN_VALUE;
        }
        if (version >= 14) { // Android U Clearkey
            _assert.that(errored.getCdmErr()).isEqualTo(5);
            _assert.that(errored.getOemErr()).isEqualTo(123);
            _assert.that(errored.getErrorContext()).isEqualTo(456);
        }
    }
}
