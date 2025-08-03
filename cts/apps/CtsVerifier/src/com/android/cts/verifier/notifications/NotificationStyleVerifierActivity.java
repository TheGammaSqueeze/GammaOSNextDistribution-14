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

package com.android.cts.verifier.notifications;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.app.NotificationManager.IMPORTANCE_HIGH;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.app.Notification;
import android.app.NotificationChannel;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.cts.verifier.R;

import com.google.common.collect.ImmutableList;

import java.util.List;

/** Verifier test for notification styles and custom views. */
public class NotificationStyleVerifierActivity extends InteractiveVerifierActivity {

    @Override
    protected int getTitleResource() {
        return R.string.notification_style_test;
    }

    @Override
    protected int getInstructionsResource() {
        return R.string.notification_style_test_info;
    }

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        return ImmutableList.of(
                new BigPictureAnimatedTest(),
                new BigPictureAnimatedUriTest(),
                new CustomContentViewTest(),
                new CustomBigContentViewTest(),
                new CustomHeadsUpContentViewTest());
    }

    private abstract class NotifyTestCase extends InteractiveTestCase {

        private static final int NOTIFICATION_ID = 1;

        @StringRes private final int mInstructionsText;
        @DrawableRes private final int mInstructionsImage;
        private View mView;

        protected NotifyTestCase(@StringRes int instructionsText) {
            this(instructionsText,  Resources.ID_NULL);
        }

        protected NotifyTestCase(@StringRes int instructionsText,
                @DrawableRes int instructionsImage) {
            mInstructionsText = instructionsText;
            mInstructionsImage = instructionsImage;
        }

        @Override
        protected View inflate(ViewGroup parent) {
            mView = createPassFailItem(parent, mInstructionsText, mInstructionsImage);
            setButtonsEnabled(mView, false);
            return mView;
        }

        @Override
        protected void setUp() {
            super.setUp();
            mNm.createNotificationChannel(getChannel());
            mNm.notify(NOTIFICATION_ID, getNotification());
            setButtonsEnabled(mView, true);
            status = READY;
            next();
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            // In all tests we post a notification and ask the user to confirm that its appearance
            // matches expectations.
            status = WAIT_FOR_USER;
            next();
        }

        @Override
        protected void tearDown() {
            mNm.cancelAll();
            mNm.deleteNotificationChannel(getChannel().getId());
            delay();
            super.tearDown();
        }

        protected abstract NotificationChannel getChannel();

        protected abstract Notification getNotification();

        protected RemoteViews getCustomLayoutRemoteView() {
            return new RemoteViews(getPackageName(), R.layout.notification_custom_layout);
        }
    }

    private class BigPictureAnimatedTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.BigPictureAnimatedTest";

        protected BigPictureAnimatedTest() {
            super(R.string.ns_bigpicture_animated_instructions);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(
                            Icon.createWithResource(mContext,
                                    R.drawable.notification_bigpicture_animated)))
                    .build();
        }
    }

    private class BigPictureAnimatedUriTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.BigPictureAnimatedUriTest";

        protected BigPictureAnimatedUriTest() {
            super(R.string.ns_bigpicture_animated_instructions);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            Uri imageUri = Uri.parse(
                    "content://com.android.cts.verifier.notifications.assets/"
                            + "notification_bigpicture_animated.webp");

            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(
                            Icon.createWithContentUri(imageUri)))
                    .build();
        }
    }

    private class CustomContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomContentViewTest";

        private CustomContentViewTest() {
            super(R.string.ns_custom_content_instructions,
                    R.drawable.notification_custom_layout_content);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_alice)
                    .setCustomContentView(getCustomLayoutRemoteView())
                    // Needed to ensure that the notification can be manually expanded/collapsed
                    // (otherwise, no affordance is included).
                    .setContentText(getString(R.string.ns_custom_content_alt_text))
                    .build();
        }
    }

    private class CustomBigContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomBigContentViewTest";

        private CustomBigContentViewTest() {
            super(R.string.ns_custom_big_content_instructions,
                    R.drawable.notification_custom_layout_big_content);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_DEFAULT);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_bob)
                    .setCustomBigContentView(getCustomLayoutRemoteView())
                    .setContentText(getString(R.string.ns_custom_big_content_alt_text))
                    .build();
        }
    }

    private class CustomHeadsUpContentViewTest extends NotifyTestCase {

        private static final String CHANNEL_ID = "NSVA.CustomHeadsUpContentViewTest";

        private CustomHeadsUpContentViewTest() {
            super(R.string.ns_custom_heads_up_content_instructions,
                    R.drawable.notification_custom_layout_heads_up);
        }

        @Override
        protected NotificationChannel getChannel() {
            return new NotificationChannel(CHANNEL_ID, CHANNEL_ID, IMPORTANCE_HIGH);
        }

        @Override
        protected Notification getNotification() {
            return new Notification.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_charlie)
                    .setCustomHeadsUpContentView(getCustomLayoutRemoteView())
                    // Needed to ensure that the notification can be manually expanded/collapsed
                    // (otherwise, no affordance is included).
                    .setStyle(new Notification.BigTextStyle().bigText(
                            getString(R.string.ns_custom_heads_up_content_alt_text)))
                    .build();
        }
    }
}
