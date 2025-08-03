/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.media.drmframework.cts;

import static junit.framework.Assert.assertTrue;

import static org.testng.Assert.assertEquals;

import android.media.MediaCrypto;
import android.media.MediaCryptoException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.media.metrics.LogSessionId;
import android.media.metrics.MediaMetricsManager;
import android.media.metrics.PlaybackSession;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaDrmTest {

    private final String TAG = this.getClass().getName();

    private void testSingleScheme(UUID scheme) throws Exception {
        MediaDrm md = new MediaDrm(scheme);
        // TODO(b/267460223): test properties description, vendor, version
        md.close();
    }

    @Test
    public void testSupportedCryptoSchemes() throws Exception {
        List<UUID> supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes();
        if (supportedCryptoSchemes.isEmpty()) {
            Log.w(TAG, "No supported crypto schemes reported");
        }
        for (UUID scheme : supportedCryptoSchemes) {
            Log.d(TAG, "supported scheme: " + scheme.toString());
            assertTrue(MediaDrm.isCryptoSchemeSupported(scheme));
            testSingleScheme(scheme);
        }
    }

    @Test
    public void testGetLogMessages() throws Exception {
        List<UUID> supportedCryptoSchemes = MediaDrm.getSupportedCryptoSchemes();
        for (UUID scheme : supportedCryptoSchemes) {
            MediaDrm drm = new MediaDrm(scheme);
            try {
                byte[] sid = drm.openSession();
                drm.closeSession(sid);
            } catch (NotProvisionedException e) {
                Log.w(TAG, scheme.toString() + ": not provisioned", e);
            }

            List<MediaDrm.LogMessage> logMessages;
            try {
                logMessages = drm.getLogMessages();
                Assert.assertFalse("Empty logs", logMessages.isEmpty());
                for (MediaDrm.LogMessage log : logMessages) {
                    Assert.assertFalse("Empty log: " + log.toString(), log.getMessage().isEmpty());
                }
            } catch (UnsupportedOperationException e) {
                Log.w(TAG, scheme.toString() + ": no LogMessage support", e);
                continue;
            }

            long end = System.currentTimeMillis();
            for (MediaDrm.LogMessage log: logMessages) {
                Assert.assertTrue("Log occurred in future",
                        log.getTimestampMillis() <= end);
                Assert.assertTrue("Invalid log priority",
                        log.getPriority() >= Log.VERBOSE &&
                                log.getPriority() <= Log.ASSERT);
                Log.i(TAG, log.toString());
            }
        }
    }

    private static boolean searchMetricsForValue(PersistableBundle haystack, Object needle) {
        for (String key : haystack.keySet()) {
            Object obj = haystack.get(key);
            if (obj.equals(needle)) {
                return true;
            }
            if (obj instanceof PersistableBundle) {
                PersistableBundle haystack2 = (PersistableBundle) obj;
                if (searchMetricsForValue(haystack2, needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void testPlaybackComponent() throws UnsupportedSchemeException {
        final UUID CLEARKEY_UUID = new UUID(0xe2719d58a985b3c9L, 0x781ab030af78d30eL);
        // TODO(b/267463362): move testPlaybackComponent to MediaDrmClearkeyTest
        for (UUID scheme : new UUID[] {CLEARKEY_UUID}) {
            MediaDrm drm = new MediaDrm(scheme);
            byte[] sid = null;
            try {
                drm = new MediaDrm(scheme);
                sid = drm.openSession();
                Assert.assertNotNull("null session id", sid);
                MediaDrm.PlaybackComponent component = drm.getPlaybackComponent(sid);
                Assert.assertNotNull("null PlaybackComponent", component);

                final MediaMetricsManager mediaMetricsManager =
                        InstrumentationRegistry.getTargetContext()
                                .getSystemService(MediaMetricsManager.class);
                final PlaybackSession playbackSession =
                        mediaMetricsManager.createPlaybackSession();
                final LogSessionId logSessionId = playbackSession.getSessionId();
                component.setLogSessionId(logSessionId);
                assertEquals(logSessionId, component.getLogSessionId(),
                        "LogSessionId not set");
                PersistableBundle metrics = drm.getMetrics();
                assertTrue("LogSessionId not found in metrics",
                        searchMetricsForValue(metrics, logSessionId.getStringId()));
            } catch (UnsupportedOperationException | NotProvisionedException e) {
                Log.w(TAG, "testPlaybackComponent: skipping scheme " + scheme, e);
            } catch (ResourceBusyException e) {
                // todo: retry
            } finally {
                drm.close();
            }
        }
    }

    private void testRequiresSecureDecoder(UUID scheme, MediaDrm drm)
            throws ResourceBusyException, NotProvisionedException,
            MediaCryptoException {
        int[] levels = {
                MediaDrm.SECURITY_LEVEL_SW_SECURE_CRYPTO,
                MediaDrm.SECURITY_LEVEL_HW_SECURE_ALL,
                MediaDrm.getMaxSecurityLevel()};
        for (int level : levels) {
            for (String mime : new String[]{"audio/mp4", "video/mp4"}) {
                if (!MediaDrm.isCryptoSchemeSupported(scheme, mime, level)) {
                    continue;
                }
                byte[] sid = drm.openSession(level);
                MediaCrypto crypto = new MediaCrypto(scheme, sid);
                boolean supported1 = crypto.requiresSecureDecoderComponent(mime);
                boolean supported2;
                if (level == MediaDrm.getMaxSecurityLevel()) {
                    supported2 = drm.requiresSecureDecoder(mime);
                } else {
                    supported2 = drm.requiresSecureDecoder(mime, level);
                }
                assertEquals(supported1, supported2, "secure decoder requirements inconsistent");
            }
        }
    }

    @Test
    public void testRequiresSecureDecoder()
            throws MediaCryptoException, UnsupportedSchemeException, ResourceBusyException {
        for (UUID scheme: MediaDrm.getSupportedCryptoSchemes()) {
            MediaDrm drm = new MediaDrm(scheme);
            try {
                testRequiresSecureDecoder(scheme, drm);
            } catch (UnsupportedOperationException | NotProvisionedException e) {
                Log.w(TAG, "testRequiresSecureDecoder: skipping scheme " + scheme, e);
            } finally {
                drm.close();
            }
        }
    }
}
