/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.ContextWrapper;
import android.media.session.MediaSession;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class MediaSessionTest extends StsExtraBusinessLogicTestCase {
    private static final String TAG = "MediaSessionTest";

    private static final String TEST_SESSION_TAG_FOREIGN_PACKAGE =
            "test-session-tag-foreign-package";
    private static final String TEST_FOREIGN_PACKAGE_NAME = "fakepackage";
    private static final String TEST_FOREIGN_PACKAGE_CLASS = "com.fakepackage.media.FakeReceiver";

    @Test
    @AsbSecurityTest(cveBugId = 238177121)
    public void setMediaButtonBroadcastReceiver_withForeignPackageName_fails() throws Exception {
        // Create Media Session
        MediaSession mediaSession = new MediaSession(new ContextWrapper(getContext()) {
                    @Override
                    public String getPackageName() {
                        return TEST_FOREIGN_PACKAGE_NAME;
                    }
                }, TEST_SESSION_TAG_FOREIGN_PACKAGE);

        assertThrows("Component name with different package name was registered.",
                IllegalArgumentException.class,
                () -> mediaSession.setMediaButtonBroadcastReceiver(
                        new ComponentName(TEST_FOREIGN_PACKAGE_NAME, TEST_FOREIGN_PACKAGE_CLASS)));

        mediaSession.release();
    }
}
