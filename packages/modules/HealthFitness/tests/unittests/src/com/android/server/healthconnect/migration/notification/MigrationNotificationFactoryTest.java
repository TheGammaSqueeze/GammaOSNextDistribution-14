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

package com.android.server.healthconnect.migration.notification;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.graphics.drawable.Icon;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class MigrationNotificationFactoryTest {

    @Mock private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testAllNotificationStringsExist() {
        MigrationNotificationFactory factory = new MigrationNotificationFactory(mContext);
        String[] expected = MigrationNotificationFactory.getNotificationStringResources();
        for (String s : expected) {
            String fetched = factory.getStringResource(s);
            String failMessage = "String resource with name " + s + " cannot be found.";
            assertWithMessage(failMessage).that(fetched).isNotNull();
        }
    }

    @Test
    public void testAppIconDrawableExists() {
        MigrationNotificationFactory factory = new MigrationNotificationFactory(mContext);
        Icon fetched = factory.getAppIcon();
        String failMessage =
                "Drawable resource with name "
                        + MigrationNotificationFactory.APP_ICON_DRAWABLE_NAME
                        + " cannot be found.";
        assertWithMessage(failMessage).that(fetched).isNotNull();
    }
}
