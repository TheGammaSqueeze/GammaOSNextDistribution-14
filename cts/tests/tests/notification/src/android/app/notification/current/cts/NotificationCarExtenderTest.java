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
package android.app.notification.current.cts;

 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertNotNull;

 import android.app.Notification;
 import android.app.Notification.CarExtender;
 import android.app.Notification.CarExtender.UnreadConversation;
 import android.app.PendingIntent;
 import android.app.RemoteInput;
 import android.content.Context;
 import android.content.Intent;
 import android.graphics.Bitmap;
 import android.graphics.Bitmap.Config;

 import androidx.test.platform.app.InstrumentationRegistry;
 import androidx.test.runner.AndroidJUnit4;

 import org.junit.Before;
 import org.junit.Test;
 import org.junit.runner.RunWith;

 @RunWith(AndroidJUnit4.class)
 public class NotificationCarExtenderTest {

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testCarExtender_EmptyConstructor() {
        CarExtender extender = new Notification.CarExtender();
        assertNotNull(extender);
        assertEquals(Notification.COLOR_DEFAULT, extender.getColor());
        assertEquals(null, extender.getLargeIcon());
        assertEquals(null, extender.getUnreadConversation());
    }

    @Test
    public void testCarExtender_Constructor() {
        Notification notification = new Notification();
        CarExtender extender = new Notification.CarExtender(notification);
        assertNotNull(extender);
        assertEquals(Notification.COLOR_DEFAULT, extender.getColor());
        assertEquals(null, extender.getLargeIcon());
        assertEquals(null, extender.getUnreadConversation());
    }

    @Test
    public void testCarExtender() {
        final int testColor = 11;
        final Bitmap testIcon = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        final UnreadConversation testConversation =
            new CarExtender.Builder("testParticipant")
                .addMessage("testMessage")
                .setLatestTimestamp(System.currentTimeMillis())
                .build();

        CarExtender extender = new Notification.CarExtender()
            .setColor(testColor)
            .setLargeIcon(testIcon)
            .setUnreadConversation(testConversation);

        assertEquals(testColor, extender.getColor());
        assertEquals(testIcon, extender.getLargeIcon());
        assertEquals(testConversation, extender.getUnreadConversation());
    }

    @Test
    public void testCarExtender_extend() {
        final int testColor = 11;
        final Bitmap testIcon = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        final UnreadConversation testConversation =
            new CarExtender.Builder("testParticipant")
                .addMessage("testMessage")
                .setLatestTimestamp(System.currentTimeMillis())
                .build();
        Notification.Builder notifBuilder = new Notification.Builder(mContext, "test id")
            .setSmallIcon(1);
        CarExtender extender = new Notification.CarExtender()
            .setColor(testColor)
            .setLargeIcon(testIcon)
            .setUnreadConversation(testConversation);

        extender.extend(notifBuilder);

        Notification notification = notifBuilder.build();

        CarExtender receiveCarExtender = new CarExtender(notification);

        assertNotNull(receiveCarExtender);
        assertEquals(testColor, receiveCarExtender.getColor());
        assertEquals(testIcon, receiveCarExtender.getLargeIcon());
        assertEquals(1, receiveCarExtender.getUnreadConversation().getMessages().length);
        assertEquals(testConversation.getMessages().length,
            receiveCarExtender.getUnreadConversation().getMessages().length);
        assertEquals(testConversation.getMessages()[0],
            receiveCarExtender.getUnreadConversation().getMessages()[0]);
    }

    @Test
    public void testCarExtender_UnreadConversationAndBuilder() {
        final long testTime = System.currentTimeMillis();
        final String testMessage = "testMessage";
        final String testParticipant = "testParticipant";
        final Intent testIntent = new Intent("testIntent")
                .setPackage(mContext.getPackageName());
        final PendingIntent testPendingIntent =
            PendingIntent.getBroadcast(mContext, 0, testIntent,
            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        final PendingIntent testReplyPendingIntent =
            PendingIntent.getBroadcast(mContext, 0, testIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        final RemoteInput testRemoteInput = new RemoteInput.Builder("key").build();

        final UnreadConversation testConversation =
            new CarExtender.Builder(testParticipant)
                .setLatestTimestamp(testTime)
                .addMessage(testMessage)
                .setReadPendingIntent(testPendingIntent)
                .setReplyAction(testReplyPendingIntent, testRemoteInput)
                .build();

        assertEquals(testTime, testConversation.getLatestTimestamp());
        assertEquals(1, testConversation.getMessages().length);
        assertEquals(testMessage, testConversation.getMessages()[0]);
        assertEquals(testParticipant, testConversation.getParticipant());
        assertEquals(1, testConversation.getParticipants().length);
        assertEquals(testParticipant, testConversation.getParticipants()[0]);
        assertEquals(testPendingIntent, testConversation.getReadPendingIntent());
        assertEquals(testRemoteInput, testConversation.getRemoteInput());
        assertEquals(testPendingIntent, testConversation.getReplyPendingIntent());
    }
}
