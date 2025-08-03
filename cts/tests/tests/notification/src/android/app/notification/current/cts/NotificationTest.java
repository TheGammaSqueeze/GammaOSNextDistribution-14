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

package android.app.notification.current.cts;

import static android.app.Notification.FLAG_BUBBLE;
import static android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP;
import static android.graphics.drawable.Icon.TYPE_RESOURCE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Notification;
import android.app.Notification.Action.Builder;
import android.app.Notification.CallStyle;
import android.app.Notification.MessagingStyle;
import android.app.Notification.MessagingStyle.Message;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.StrictMode;
import android.test.AndroidTestCase;
import android.util.Pair;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@RunWith(AndroidJUnit4.class)
public class NotificationTest {
    private static final String TEXT_RESULT_KEY = "text";
    private static final String DATA_RESULT_KEY = "data";
    private static final String DATA_AND_TEXT_RESULT_KEY = "data and text";

    private Notification.Action mAction;
    private Notification mNotification;
    private Context mContext;

    private static final String TICKER_TEXT = "tickerText";
    private static final String CONTENT_TITLE = "contentTitle";
    private static final String CONTENT_TEXT = "contentText";
    private static final String URI_STRING = "uriString";
    private static final String ACTION_TITLE = "actionTitle";
    private static final int BUBBLE_HEIGHT = 300;
    private static final int BUBBLE_HEIGHT_RESID = 31415;
    private static final String BUBBLE_SHORTCUT_ID = "bubbleShortcutId";
    private static final int TOLERANCE = 200;
    private static final long TIMEOUT = 4000;
    private static final NotificationChannel CHANNEL = new NotificationChannel("id", "name",
            NotificationManager.IMPORTANCE_HIGH);
    private static final String SHORTCUT_ID = "shortcutId";
    private static final String SETTING_TEXT = "work chats";
    private static final boolean ALLOW_SYS_GEN_CONTEXTUAL_ACTIONS = false;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mNotification = new Notification();
    }

    @Test
    public void testConstructor() {
        mNotification = null;
        mNotification = new Notification();
        assertNotNull(mNotification);
        assertTrue(System.currentTimeMillis() - mNotification.when < TOLERANCE);

        mNotification = null;
        final int notificationTime = 200;
        mNotification = new Notification(0, TICKER_TEXT, notificationTime);
        assertEquals(notificationTime, mNotification.when);
        assertEquals(0, mNotification.icon);
        assertEquals(TICKER_TEXT, mNotification.tickerText);
        assertEquals(0, mNotification.number);
    }

    @Test
    public void testBuilderConstructor() {
        mNotification = new Notification.Builder(mContext, CHANNEL.getId()).build();
        assertEquals(CHANNEL.getId(), mNotification.getChannelId());
        assertEquals(Notification.BADGE_ICON_NONE, mNotification.getBadgeIconType());
        assertNull(mNotification.getShortcutId());
        assertEquals(Notification.GROUP_ALERT_ALL, mNotification.getGroupAlertBehavior());
        assertEquals((long) 0, mNotification.getTimeoutAfter());
    }

    @Test
    public void testDescribeContents() {
        final int expected = 0;
        mNotification = new Notification();
        assertEquals(expected, mNotification.describeContents());
    }

    @Test
    public void testCategories() {
        assertNotNull(Notification.CATEGORY_ALARM);
        assertNotNull(Notification.CATEGORY_CALL);
        assertNotNull(Notification.CATEGORY_EMAIL);
        assertNotNull(Notification.CATEGORY_ERROR);
        assertNotNull(Notification.CATEGORY_EVENT);
        assertNotNull(Notification.CATEGORY_MESSAGE);
        assertNotNull(Notification.CATEGORY_NAVIGATION);
        assertNotNull(Notification.CATEGORY_PROGRESS);
        assertNotNull(Notification.CATEGORY_PROMO);
        assertNotNull(Notification.CATEGORY_RECOMMENDATION);
        assertNotNull(Notification.CATEGORY_REMINDER);
        assertNotNull(Notification.CATEGORY_SERVICE);
        assertNotNull(Notification.CATEGORY_SOCIAL);
        assertNotNull(Notification.CATEGORY_STATUS);
        assertNotNull(Notification.CATEGORY_SYSTEM);
        assertNotNull(Notification.CATEGORY_TRANSPORT);
        assertNotNull(Notification.CATEGORY_WORKOUT);
        assertNotNull(Notification.CATEGORY_LOCATION_SHARING);
        assertNotNull(Notification.CATEGORY_STOPWATCH);
        assertNotNull(Notification.CATEGORY_MISSED_CALL);
    }

    @Test
    public void testWriteToParcel() {
        Notification.BubbleMetadata bubble = makeBubbleMetadata();
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setBadgeIconType(Notification.BADGE_ICON_SMALL)
                .setShortcutId(SHORTCUT_ID)
                .setTimeoutAfter(TIMEOUT)
                .setSettingsText(SETTING_TEXT)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setBubbleMetadata(bubble)
                .setAllowSystemGeneratedContextualActions(ALLOW_SYS_GEN_CONTEXTUAL_ACTIONS)
                .build();
        mNotification.icon = 0;
        mNotification.number = 1;
        final Intent intent = new Intent().setPackage(mContext.getPackageName());
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE);
        mNotification.contentIntent = pendingIntent;
        final Intent deleteIntent = new Intent().setPackage(mContext.getPackageName());
        final PendingIntent delPendingIntent = PendingIntent.getBroadcast(
                mContext, 0, deleteIntent, PendingIntent.FLAG_MUTABLE);
        mNotification.deleteIntent = delPendingIntent;
        mNotification.tickerText = TICKER_TEXT;

        final RemoteViews contentView = new RemoteViews(mContext.getPackageName(),
                android.R.layout.simple_list_item_1);
        mNotification.contentView = contentView;
        mNotification.defaults = 0;
        mNotification.flags = 0;
        final Uri uri = Uri.parse(URI_STRING);
        mNotification.sound = uri;
        mNotification.audioStreamType = 0;
        final long[] longArray = { 1l, 2l, 3l };
        mNotification.vibrate = longArray;
        mNotification.ledARGB = 0;
        mNotification.ledOnMS = 0;
        mNotification.ledOffMS = 0;
        mNotification.iconLevel = 0;

        Parcel parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        // Test Notification(Parcel)
        Notification result = new Notification(parcel);
        assertEquals(mNotification.icon, result.icon);
        assertEquals(mNotification.when, result.when);
        assertEquals(mNotification.number, result.number);
        assertNotNull(result.contentIntent);
        assertNotNull(result.deleteIntent);
        assertEquals(mNotification.tickerText, result.tickerText);
        assertNotNull(result.contentView);
        assertEquals(mNotification.defaults, result.defaults);
        assertEquals(mNotification.flags, result.flags);
        assertNotNull(result.sound);
        assertEquals(mNotification.audioStreamType, result.audioStreamType);
        assertEquals(mNotification.vibrate[0], result.vibrate[0]);
        assertEquals(mNotification.vibrate[1], result.vibrate[1]);
        assertEquals(mNotification.vibrate[2], result.vibrate[2]);
        assertEquals(mNotification.ledARGB, result.ledARGB);
        assertEquals(mNotification.ledOnMS, result.ledOnMS);
        assertEquals(mNotification.ledOffMS, result.ledOffMS);
        assertEquals(mNotification.iconLevel, result.iconLevel);
        assertEquals(mNotification.getShortcutId(), result.getShortcutId());
        assertEquals(mNotification.getBadgeIconType(), result.getBadgeIconType());
        assertEquals(mNotification.getTimeoutAfter(), result.getTimeoutAfter());
        assertEquals(mNotification.getChannelId(), result.getChannelId());
        assertEquals(mNotification.getSettingsText(), result.getSettingsText());
        assertEquals(mNotification.getGroupAlertBehavior(), result.getGroupAlertBehavior());
        assertNotNull(result.getBubbleMetadata());
        assertEquals(mNotification.getAllowSystemGeneratedContextualActions(),
                result.getAllowSystemGeneratedContextualActions());

        mNotification.contentIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentIntent);

        mNotification.deleteIntent = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.deleteIntent);

        mNotification.tickerText = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.tickerText);

        mNotification.contentView = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.contentView);

        mNotification.sound = null;
        parcel = Parcel.obtain();
        mNotification.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        result = new Notification(parcel);
        assertNull(result.sound);
    }

    @Test
    public void testColorizeNotification() {
        mNotification = new Notification.Builder(mContext, "channel_id")
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setColorized(true).setColor(Color.WHITE)
                .build();

        assertTrue(mNotification.extras.getBoolean(Notification.EXTRA_COLORIZED));
    }

    @Test
    public void testBuilder() {
        final Intent intent = new Intent();
        final PendingIntent contentIntent =
                PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        Notification.BubbleMetadata bubble = makeBubbleMetadata();
        final PendingIntent actionIntent =
                PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setContentText(CONTENT_TEXT)
                .setContentIntent(contentIntent)
                .setBadgeIconType(Notification.BADGE_ICON_SMALL)
                .setShortcutId(SHORTCUT_ID)
                .setTimeoutAfter(TIMEOUT)
                .setSettingsText(SETTING_TEXT)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setBubbleMetadata(bubble)
                .setAllowSystemGeneratedContextualActions(ALLOW_SYS_GEN_CONTEXTUAL_ACTIONS)
                .addAction(new Notification.Action.Builder(0, ACTION_TITLE, actionIntent)
                        .setContextual(true)
                        .build())
                .addAction(new Notification.Action.Builder(0, "not contextual", actionIntent)
                        .setContextual(false)
                        .build())
                .build();
        assertEquals(CONTENT_TEXT, mNotification.extras.getString(Notification.EXTRA_TEXT));
        assertEquals(CONTENT_TITLE, mNotification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(1, mNotification.icon);
        assertEquals(contentIntent, mNotification.contentIntent);
        assertEquals(CHANNEL.getId(), mNotification.getChannelId());
        assertEquals(Notification.BADGE_ICON_SMALL, mNotification.getBadgeIconType());
        assertEquals(SHORTCUT_ID, mNotification.getShortcutId());
        assertEquals(TIMEOUT, mNotification.getTimeoutAfter());
        assertEquals(SETTING_TEXT, mNotification.getSettingsText());
        assertEquals(Notification.GROUP_ALERT_SUMMARY, mNotification.getGroupAlertBehavior());
        assertEquals(bubble, mNotification.getBubbleMetadata());
        assertEquals(ALLOW_SYS_GEN_CONTEXTUAL_ACTIONS,
                mNotification.getAllowSystemGeneratedContextualActions());
        assertEquals(1, mNotification.getContextualActions().size());
        assertEquals(ACTION_TITLE, mNotification.getContextualActions().get(0).title);
    }

    @Test
    public void testBuilder_getStyle() {
        MessagingStyle ms = new MessagingStyle(new Person.Builder().setName("Test name").build());
        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL.getId());

        builder.setStyle(ms);

        assertEquals(ms, builder.getStyle());
    }

    @Test
    public void testActionBuilder() {
        final Intent intent = new Intent().setPackage(mContext.getPackageName());
        final PendingIntent actionIntent = PendingIntent.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE);
        mAction = null;
        mAction = new Notification.Action.Builder(0, ACTION_TITLE, actionIntent)
                .setAuthenticationRequired(true)
                .build();
        assertEquals(ACTION_TITLE, mAction.title);
        assertEquals(actionIntent, mAction.actionIntent);
        assertEquals(true, mAction.getAllowGeneratedReplies());
        assertTrue(mAction.isAuthenticationRequired());
    }

    @Test
    public void testNotification_addPerson() {
        String name = "name";
        String key = "key";
        String uri = "name:name";
        Person person = new Person.Builder()
                .setName(name)
                .setIcon(Icon.createWithResource(mContext, 1))
                .setKey(key)
                .setUri(uri)
                .build();
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .addPerson(person)
                .build();

        ArrayList<Person> restoredPeople = mNotification.extras.getParcelableArrayList(
                Notification.EXTRA_PEOPLE_LIST);
        assertNotNull(restoredPeople);
        Person restoredPerson = restoredPeople.get(0);
        assertNotNull(restoredPerson);
        assertNotNull(restoredPerson.getIcon());
        assertEquals(name, restoredPerson.getName());
        assertEquals(key, restoredPerson.getKey());
        assertEquals(uri, restoredPerson.getUri());
    }

    @Test
    public void testNotification_MessagingStyle_people() {
        String name = "name";
        String key = "key";
        String uri = "name:name";
        Person user = new Person.Builder()
                .setName(name)
                .setIcon(Icon.createWithResource(mContext, 1))
                .setKey(key)
                .setUri(uri)
                .build();
        Person participant = new Person.Builder().setName("sender").build();
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle(user)
                .addMessage("text", 0, participant)
                .addMessage(new Message("text 2", 0, participant));
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setStyle(messagingStyle)
                .build();

        Person restoredPerson = mNotification.extras.getParcelable(
                Notification.EXTRA_MESSAGING_PERSON);
        assertNotNull(restoredPerson);
        assertNotNull(restoredPerson.getIcon());
        assertEquals(name, restoredPerson.getName());
        assertEquals(key, restoredPerson.getKey());
        assertEquals(uri, restoredPerson.getUri());
        assertNotNull(mNotification.extras.getParcelableArray(Notification.EXTRA_MESSAGES));
    }


    @Test
    public void testMessagingStyle_historicMessages() {
        Message referenceMessage = new Message("historic text", 0, "historic sender");
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .addMessage("text", 0, "sender")
                .addMessage(new Message("image", 0, "sender")
                        .setData("image/png", Uri.parse("http://example.com/image.png")))
                .addHistoricMessage(referenceMessage)
                .setConversationTitle("title");
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setStyle(messagingStyle)
                .build();

        List<Message> historicMessages = messagingStyle.getHistoricMessages();
        assertNotNull(historicMessages);
        assertEquals(1, historicMessages.size());
        Message message = historicMessages.get(0);
        assertEquals(referenceMessage, message);

        assertNotNull(
                mNotification.extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES));
    }

    @Test
    public void testMessagingStyle_isGroupConversation() {
        mContext.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.P;
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .setGroupConversation(true)
                .setConversationTitle("test conversation title");
        Notification notification = new Notification.Builder(mContext, "test id")
                .setSmallIcon(1)
                .setContentTitle("test title")
                .setStyle(messagingStyle)
                .build();

        assertTrue(messagingStyle.isGroupConversation());
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION));
    }

    @Test
    public void testMessagingStyle_isGroupConversation_noConversationTitle() {
        mContext.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.P;
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .setGroupConversation(true)
                .setConversationTitle(null);
        Notification notification = new Notification.Builder(mContext, "test id")
                .setSmallIcon(1)
                .setContentTitle("test title")
                .setStyle(messagingStyle)
                .build();

        assertTrue(messagingStyle.isGroupConversation());
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION));
    }

    @Test
    public void testMessagingStyle_isGroupConversation_withConversationTitle_legacy() {
        // In legacy (version < P), isGroupConversation is controlled by conversationTitle.
        mContext.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.O;
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .setGroupConversation(false)
                .setConversationTitle("test conversation title");
        Notification notification = new Notification.Builder(mContext, "test id")
                .setSmallIcon(1)
                .setContentTitle("test title")
                .setStyle(messagingStyle)
                .build();

        assertTrue(messagingStyle.isGroupConversation());
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION));
    }

    @Test
    public void testMessagingStyle_isGroupConversation_withoutConversationTitle_legacy() {
        // In legacy (version < P), isGroupConversation is controlled by conversationTitle.
        mContext.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.O;
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .setGroupConversation(true)
                .setConversationTitle(null);
        Notification notification = new Notification.Builder(mContext, "test id")
                .setSmallIcon(1)
                .setContentTitle("test title")
                .setStyle(messagingStyle)
                .build();

        assertFalse(messagingStyle.isGroupConversation());
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION));
    }

    @Test
    public void testMessagingStyle_getUser() {
        Person user = new Person.Builder().setName("Test name").build();

        MessagingStyle messagingStyle = new MessagingStyle(user);

        assertEquals(user, messagingStyle.getUser());
    }

    @Test
    public void testMessagingStyle_getConversationTitle() {
        final String title = "test conversation title";
        Person user = new Person.Builder().setName("Test name").build();
        MessagingStyle messagingStyle = new MessagingStyle(user).setConversationTitle(title);

        Notification notification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setStyle(messagingStyle)
                .build();

        assertEquals(title, messagingStyle.getConversationTitle());
        assertEquals(title, notification.extras.getString(Notification.EXTRA_CONVERSATION_TITLE));
    }

    @Test
    public void testMessage() {
        String senderName = "Test name";
        Person sender = new Person.Builder().setName(senderName).build();
        String text = "Test message";
        long timestamp = 400;

        Message message = new Message(text, timestamp, sender);

        assertEquals(text, message.getText());
        assertEquals(timestamp, message.getTimestamp());
        assertEquals(sender, message.getSenderPerson());
        assertEquals(senderName, message.getSender());
    }

    @Test
    public void testMessageData() {
        Person sender = new Person.Builder().setName("Test name").build();
        String text = "Test message";
        long timestamp = 400;
        Message message = new Message(text, timestamp, sender);

        String mimeType = "image/png";
        Uri uri = Uri.parse("http://example.com/image.png");
        message.setData(mimeType, uri);

        assertEquals(mimeType, message.getDataMimeType());
        assertEquals(uri, message.getDataUri());
    }

    @Test
    public void testToString() {
        mNotification = new Notification();
        assertNotNull(mNotification.toString());
        mNotification = null;
    }

    @Test
    public void testNotificationActionBuilder_setDataOnlyRemoteInput() throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput()).build();
        RemoteInput[] textInputs = a.getRemoteInputs();
        assertTrue(textInputs == null || textInputs.length == 0);
        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
    }

    @Test
    public void testNotificationActionBuilder_setTextAndDataOnlyRemoteInput() throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput())
                .addRemoteInput(newTextRemoteInput())
                .build();

        verifyRemoteInputArrayHasSingleResult(a.getRemoteInputs(), TEXT_RESULT_KEY);
        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
    }

    @Test
    public void testNotificationActionBuilder_setTextAndDataOnlyAndBothRemoteInput()
            throws Throwable {
        Notification.Action a = newActionBuilder()
                .addRemoteInput(newDataOnlyRemoteInput())
                .addRemoteInput(newTextRemoteInput())
                .addRemoteInput(newTextAndDataRemoteInput())
                .build();

        assertTrue(a.getRemoteInputs() != null && a.getRemoteInputs().length == 2);
        assertEquals(TEXT_RESULT_KEY, a.getRemoteInputs()[0].getResultKey());
        assertFalse(a.getRemoteInputs()[0].isDataOnly());
        assertEquals(DATA_AND_TEXT_RESULT_KEY, a.getRemoteInputs()[1].getResultKey());
        assertFalse(a.getRemoteInputs()[1].isDataOnly());

        verifyRemoteInputArrayHasSingleResult(a.getDataOnlyRemoteInputs(), DATA_RESULT_KEY);
        assertTrue(a.getDataOnlyRemoteInputs()[0].isDataOnly());
    }

    @Test
    public void testAction_builder_hasDefault() {
        Notification.Action action = makeNotificationAction(null);
        assertEquals(Notification.Action.SEMANTIC_ACTION_NONE, action.getSemanticAction());
    }

    @Test
    public void testAction_builder_setSemanticAction() {
        Notification.Action action = makeNotificationAction(
                builder -> builder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY));
        assertEquals(Notification.Action.SEMANTIC_ACTION_REPLY, action.getSemanticAction());
    }

    @Test
    public void testAction_builder_contextualAction_nullIcon() {
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        Notification.Action.Builder builder =
                new Notification.Action.Builder(null /* icon */, "title", pendingIntent)
                .setContextual(true);
        try {
            builder.build();
            fail("Creating a semantic Action with a null icon should cause a NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testAction_builder_contextualAction_nullIntent() {
        Notification.Action.Builder builder =
                new Notification.Action.Builder(0 /* icon */, "title", null /* intent */)
                .setContextual(true);
        try {
            builder.build();
            fail("Creating a semantic Action with a null PendingIntent should cause a "
                    + "NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testAction_parcel() {
        Notification.Action action = writeAndReadParcelable(
                makeNotificationAction(builder -> {
                    builder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_ARCHIVE);
                    builder.setAllowGeneratedReplies(true);
                }));

        assertEquals(Notification.Action.SEMANTIC_ACTION_ARCHIVE, action.getSemanticAction());
        assertTrue(action.getAllowGeneratedReplies());
    }

    @Test
    public void testAction_clone() {
        Notification.Action action = makeNotificationAction(
                builder -> builder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_DELETE));
        assertEquals(
                Notification.Action.SEMANTIC_ACTION_DELETE,
                action.clone().getSemanticAction());
    }

    @Test
    public void testBuildStrictMode() {
        try {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyDeath().build());
            Notification.Action a = newActionBuilder()
                    .addRemoteInput(newDataOnlyRemoteInput())
                    .addRemoteInput(newTextRemoteInput())
                    .addRemoteInput(newTextAndDataRemoteInput())
                    .build();
            Notification.Builder b = new Notification.Builder(mContext, "id")
                    .setStyle(new Notification.BigTextStyle().setBigContentTitle("Big content"))
                    .setContentTitle("title")
                    .setActions(a);

            b.build();
        } finally {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        }
    }

    @Test
    public void testGetAllowSystemGeneratedContextualActions_trueByDefault() {
        Notification notification = new Notification.Builder(mContext, CHANNEL.getId()).build();
        assertTrue(notification.getAllowSystemGeneratedContextualActions());
    }

    @Test
    public void testGetAllowSystemGeneratedContextualActions() {
        Notification notification = new Notification.Builder(mContext, CHANNEL.getId())
                .setAllowSystemGeneratedContextualActions(false)
                .build();
        assertFalse(notification.getAllowSystemGeneratedContextualActions());
    }

    @Test
    public void testBubbleMetadataBuilder() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(mContext, 1);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                .setDesiredHeight(BUBBLE_HEIGHT)
                .setSuppressableBubble(false)
                .setDeleteIntent(deleteIntent);

        Notification.BubbleMetadata data = metadataBuilder.build();
        assertEquals(BUBBLE_HEIGHT, data.getDesiredHeight());
        assertEquals(icon, data.getIcon());
        assertEquals(bubbleIntent, data.getIntent());
        assertEquals(deleteIntent, data.getDeleteIntent());
        assertFalse(data.isNotificationSuppressed());
        assertFalse(data.isBubbleSuppressable());
        assertFalse(data.getAutoExpandBubble());
    }

    @Test
    public void testBubbleMetadata_parcel() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(mContext, 1);
        Notification.BubbleMetadata metadata =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                        .setDesiredHeight(BUBBLE_HEIGHT)
                        .setAutoExpandBubble(true)
                        .setSuppressNotification(true)
                        .setSuppressableBubble(true)
                        .setDeleteIntent(deleteIntent)
                        .build();

        writeAndReadParcelable(metadata);
        assertEquals(BUBBLE_HEIGHT, metadata.getDesiredHeight());
        assertEquals(icon, metadata.getIcon());
        assertEquals(bubbleIntent, metadata.getIntent());
        assertEquals(deleteIntent, metadata.getDeleteIntent());
        assertTrue(metadata.getAutoExpandBubble());
        assertTrue(metadata.isNotificationSuppressed());
        assertTrue(metadata.isBubbleSuppressable());
    }

    @Test
    public void testBubbleMetadataBuilder_shortcutId() {
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID)
                        .setDesiredHeight(BUBBLE_HEIGHT)
                        .setSuppressableBubble(true)
                        .setDeleteIntent(deleteIntent);

        Notification.BubbleMetadata data = metadataBuilder.build();
        assertEquals(BUBBLE_HEIGHT, data.getDesiredHeight());
        assertEquals(BUBBLE_SHORTCUT_ID, data.getShortcutId());
        assertEquals(deleteIntent, data.getDeleteIntent());
        assertTrue(data.isBubbleSuppressable());
        assertFalse(data.isNotificationSuppressed());
        assertFalse(data.getAutoExpandBubble());
    }

    @Test
    public void testBubbleMetadataBuilder_parcelShortcutId() {
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.BubbleMetadata metadata =
                new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID)
                        .setDesiredHeight(BUBBLE_HEIGHT)
                        .setAutoExpandBubble(true)
                        .setSuppressableBubble(true)
                        .setSuppressNotification(true)
                        .setDeleteIntent(deleteIntent)
                        .build();

        writeAndReadParcelable(metadata);
        assertEquals(BUBBLE_HEIGHT, metadata.getDesiredHeight());
        assertEquals(deleteIntent, metadata.getDeleteIntent());
        assertEquals(BUBBLE_SHORTCUT_ID, metadata.getShortcutId());
        assertTrue(metadata.isBubbleSuppressable());
        assertTrue(metadata.getAutoExpandBubble());
        assertTrue(metadata.isNotificationSuppressed());
    }

    @Test
    public void testBubbleMetadata_parcelResId() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(mContext, 1);
        Notification.BubbleMetadata metadata =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                        .setDesiredHeightResId(BUBBLE_HEIGHT_RESID)
                .build();
        writeAndReadParcelable(metadata);
        assertEquals(BUBBLE_HEIGHT_RESID, metadata.getDesiredHeightResId());
        assertEquals(icon, metadata.getIcon());
        assertEquals(bubbleIntent, metadata.getIntent());
        assertFalse(metadata.getAutoExpandBubble());
        assertFalse(metadata.isBubbleSuppressable());
        assertFalse(metadata.isNotificationSuppressed());
    }

    @Test
    public void testBubbleMetadataBuilder_throwForNoIntentNoShortcut() {
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder();
        try {
            metadataBuilder.build();
            fail("Should have thrown exception, no pending intent or shortcutId");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testBubbleMetadataBuilder_noThrowWithShortcut() {
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID)
                        .setDesiredHeight(BUBBLE_HEIGHT);
        Notification.BubbleMetadata metadata = metadataBuilder.build();
        assertNotNull(metadata.getShortcutId());
        assertNull(metadata.getIcon());
        assertNull(metadata.getIntent());
    }

    @Test
    public void testBubbleMetadataBuilder_shortcutBuilder_throwsForSetIntent() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        try {
            Notification.BubbleMetadata.Builder metadataBuilder =
                    new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID)
                            .setDesiredHeightResId(BUBBLE_HEIGHT_RESID)
                            .setIntent(bubbleIntent);
            fail("Should have thrown exception, can't set intent on shortcut builder");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testBubbleMetadataBuilder_shortcutBuilder_throwsForSetIcon() {
        try {
            Icon icon = Icon.createWithResource(mContext, 1);
            Notification.BubbleMetadata.Builder metadataBuilder =
                    new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID)
                            .setDesiredHeightResId(BUBBLE_HEIGHT_RESID)
                            .setIcon(icon);
            fail("Should have thrown exception, can't set icon on shortcut builder");
        } catch (Exception e) {
            // expected
        }
    }

    @Test
    public void testBubbleMetadataBuilder_notifBubbleShortcutIds_match_noThrow() {
        Notification.BubbleMetadata metadata =
                new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID).build();

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setShortcutId(BUBBLE_SHORTCUT_ID)
                .setBubbleMetadata(metadata)
                .build();
        assertEquals(mNotification.getShortcutId(), BUBBLE_SHORTCUT_ID);
        assertEquals(mNotification.getBubbleMetadata().getShortcutId(),
                mNotification.getShortcutId());
    }

    @Test
    public void testBubbleMetadataBuilder_notifBubbleShortcutIds_different_throw() {
        Notification.BubbleMetadata metadata =
                new Notification.BubbleMetadata.Builder(BUBBLE_SHORTCUT_ID).build();

        Notification.Builder nb = new Notification.Builder(mContext, CHANNEL.getId())
                .setSmallIcon(1)
                .setContentTitle(CONTENT_TITLE)
                .setShortcutId("a different shortcut id")
                .setBubbleMetadata(metadata);

        try {
            nb.build();
            fail("Should have thrown IllegalArgumentException, "
                    + "notif & bubble shortcutIds mismatch");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testBubbleMetadataBuilder_noThrowForAdaptiveBitmapIcon() {
        Bitmap b = Bitmap.createBitmap(50, 25, Bitmap.Config.ARGB_8888);
        new Canvas(b).drawColor(0xffff0000);
        Icon icon = Icon.createWithAdaptiveBitmap(b);

        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon);
        Notification.BubbleMetadata metadata = metadataBuilder.build();
        assertNotNull(metadata.getIcon());
        assertEquals(TYPE_ADAPTIVE_BITMAP, metadata.getIcon().getType());
    }

    @Test
    public void testBubbleMetadataBuilder_noThrowForNonBitmapIcon() {
        Icon icon = Icon.createWithResource(mContext, R.drawable.ic_android);

        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon);
        Notification.BubbleMetadata metadata = metadataBuilder.build();
        assertNotNull(metadata.getIcon());
        assertEquals(TYPE_RESOURCE, metadata.getIcon().getType());
    }

    @Test
    public void testBubbleMetadataBuilder_replaceHeightRes() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(mContext, 1);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                        .setDesiredHeight(BUBBLE_HEIGHT)
                        .setDesiredHeightResId(BUBBLE_HEIGHT_RESID)
                        .setDeleteIntent(deleteIntent);

        Notification.BubbleMetadata data = metadataBuilder.build();
        // Desired height should be cleared
        assertEquals(0, data.getDesiredHeight());
        // Res id should be used
        assertEquals(BUBBLE_HEIGHT_RESID, data.getDesiredHeightResId());
    }

    @Test
    public void testBubbleMetadataBuilder_replaceHeightDp() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()),
                PendingIntent.FLAG_MUTABLE);
        PendingIntent deleteIntent = PendingIntent.getActivity(mContext, 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(mContext, 1);
        Notification.BubbleMetadata.Builder metadataBuilder =
                new Notification.BubbleMetadata.Builder(bubbleIntent, icon)
                        .setDesiredHeightResId(BUBBLE_HEIGHT_RESID)
                        .setDesiredHeight(BUBBLE_HEIGHT)
                        .setDeleteIntent(deleteIntent);

        Notification.BubbleMetadata data = metadataBuilder.build();
        // Desired height should be used
        assertEquals(BUBBLE_HEIGHT, data.getDesiredHeight());
        // Res id should be cleared
        assertEquals(0, data.getDesiredHeightResId());
    }

    @Test
    public void testFlagBubble() {
        Notification n = new Notification();
        assertFalse((n.flags & FLAG_BUBBLE) != 0);
        n.flags |= FLAG_BUBBLE;
        assertTrue((n.flags & FLAG_BUBBLE) != 0);
    }

    @Test
    public void testGetMessagesFromBundleArray() {
        Person sender = new Person.Builder().setName("Sender").build();
        Notification.MessagingStyle.Message firstExpectedMessage =
                new Notification.MessagingStyle.Message("hello", /* timestamp= */ 123, sender);
        Notification.MessagingStyle.Message secondExpectedMessage =
                new Notification.MessagingStyle.Message("hello2", /* timestamp= */ 456, sender);

        Notification.MessagingStyle messagingStyle =
                new Notification.MessagingStyle("self name")
                        .addMessage(firstExpectedMessage)
                        .addMessage(secondExpectedMessage);
        Notification notification = new Notification.Builder(mContext, "test id")
                .setSmallIcon(1)
                .setContentTitle("test title")
                .setStyle(messagingStyle)
                .build();

        List<Notification.MessagingStyle.Message> actualMessages =
                Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                        notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES));

        assertEquals(2, actualMessages.size());
        assertMessageEquals(firstExpectedMessage, actualMessages.get(0));
        assertMessageEquals(secondExpectedMessage, actualMessages.get(1));
    }

    @Test
    public void testNotification_isBigPictureStyle_pictureContentDescriptionSet() {
        final String contentDescription = "content description";

        final Notification.BigPictureStyle bigPictureStyle = new Notification.BigPictureStyle()
                .setContentDescription(contentDescription);

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(bigPictureStyle)
                .build();

        final CharSequence notificationContentDescription =
                mNotification.extras.getCharSequence(
                        Notification.EXTRA_PICTURE_CONTENT_DESCRIPTION);
        assertEquals(contentDescription, notificationContentDescription);
    }

    @Test
    public void testHasImage_messagingStyle() {
        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle("self name")
                .addMessage(new Message("image", 0, "sender")
                        .setData("image/png", Uri.parse("http://example.com/image.png")));

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(messagingStyle)
                .build();

        assertTrue(mNotification.hasImage());
    }

    @Test
    public void testHasImage_largeIcon() {
        Bitmap b = Bitmap.createBitmap(50, 25, Bitmap.Config.ARGB_8888);

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .setLargeIcon(b)
                .build();

        assertTrue(mNotification.hasImage());
    }

    @Test
    public void testHasImage_backgroundImage() {
        final Uri backgroundImage = Uri.parse("content://com.example/background");

        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_BACKGROUND_IMAGE_URI, backgroundImage.toString());

        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                .addExtras(extras)
                .build();

        assertTrue(mNotification.hasImage());
    }

    @Test
    public void testHasImage_smallIcon() {
        mNotification = new Notification.Builder(mContext, CHANNEL.getId())
                    .setSmallIcon(1)
                    .build();

        assertFalse(mNotification.hasImage());
    }

    @Test
    public void testCallStyle_setsChronometerExtra() {
        Person person = new Person.Builder().setName("Test name").build();
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()), PendingIntent.FLAG_MUTABLE);
        CallStyle cs = CallStyle.forIncomingCall(person, pendingIntent, pendingIntent);
        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(cs)
                .setUsesChronometer(true);

        Notification notification = builder.build();
        Bundle extras = notification.extras;
        assertTrue(extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER));
    }

    @Test
    public void testCallStyle_setsCallTypeExtra() {
        Person person = new Person.Builder().setName("Test name").build();
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()), PendingIntent.FLAG_MUTABLE);
        CallStyle cs = CallStyle.forIncomingCall(person, pendingIntent, pendingIntent);
        Notification.Builder builder = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(cs);

        Notification notification = builder.build();
        Bundle extras = notification.extras;
        assertEquals(CallStyle.CALL_TYPE_INCOMING, extras.getInt(Notification.EXTRA_CALL_TYPE));

        cs = CallStyle.forOngoingCall(person, pendingIntent);
        builder = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(cs);

        notification = builder.build();
        extras = notification.extras;
        assertEquals(CallStyle.CALL_TYPE_ONGOING, extras.getInt(Notification.EXTRA_CALL_TYPE));

        cs = CallStyle.forScreeningCall(person, pendingIntent, pendingIntent);
        builder = new Notification.Builder(mContext, CHANNEL.getId())
                .setStyle(cs);

        notification = builder.build();
        extras = notification.extras;
        assertEquals(CallStyle.CALL_TYPE_SCREENING,
                extras.getInt(Notification.EXTRA_CALL_TYPE));
    }

    @Test
    public void testFreeformRemoteInputActionPair_noRemoteInput() {
        PendingIntent intent = PendingIntent.getActivity(
                mContext, 0, new Intent("test1"), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888));
        Notification notification = new Notification.Builder(mContext, "test")
                .addAction(new Notification.Action.Builder(icon, "TEXT 1", intent)
                        .build())
                .build();
        assertNull(notification.findRemoteInputActionPair(false));
    }

    @Test
    public void testFreeformRemoteInputActionPair_hasRemoteInput() {
        PendingIntent intent = PendingIntent.getActivity(
                mContext, 0, new Intent("test1"), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888));

        RemoteInput remoteInput = new RemoteInput.Builder("a").build();

        Notification.Action actionWithRemoteInput =
                new Notification.Action.Builder(icon, "TEXT 1", intent)
                        .addRemoteInput(remoteInput)
                        .addRemoteInput(remoteInput)
                        .build();

        Notification.Action actionWithoutRemoteInput =
                new Notification.Action.Builder(icon, "TEXT 2", intent)
                        .build();

        Notification notification = new Notification.Builder(mContext, "test")
                .addAction(actionWithoutRemoteInput)
                .addAction(actionWithRemoteInput)
                .build();

        Pair<RemoteInput, Notification.Action> remoteInputActionPair =
                notification.findRemoteInputActionPair(false);

        assertNotNull(remoteInputActionPair);
        assertEquals(remoteInput, remoteInputActionPair.first);
        assertEquals(actionWithRemoteInput, remoteInputActionPair.second);
    }

    @Test
    public void testFreeformRemoteInputActionPair_requestFreeform_noFreeformRemoteInput() {
        PendingIntent intent = PendingIntent.getActivity(
                mContext, 0, new Intent("test1"), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888));
        Notification notification = new Notification.Builder(mContext, "test")
                .addAction(new Notification.Action.Builder(icon, "TEXT 1", intent)
                        .addRemoteInput(
                                new RemoteInput.Builder("a")
                                        .setAllowFreeFormInput(false).build())
                        .build())
                .build();
        assertNull(notification.findRemoteInputActionPair(true));
    }

    @Test
    public void testFreeformRemoteInputActionPair_requestFreeform_hasFreeformRemoteInput() {
        PendingIntent intent = PendingIntent.getActivity(
                mContext, 0, new Intent("test1"), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithBitmap(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888));

        RemoteInput remoteInput =
                new RemoteInput.Builder("a").setAllowFreeFormInput(false).build();
        RemoteInput freeformRemoteInput =
                new RemoteInput.Builder("b").setAllowFreeFormInput(true).build();

        Notification.Action actionWithFreeformRemoteInput =
                new Notification.Action.Builder(icon, "TEXT 1", intent)
                        .addRemoteInput(remoteInput)
                        .addRemoteInput(freeformRemoteInput)
                        .build();

        Notification.Action actionWithoutFreeformRemoteInput =
                new Notification.Action.Builder(icon, "TEXT 2", intent)
                        .addRemoteInput(remoteInput)
                        .build();

        Notification notification = new Notification.Builder(mContext, "test")
                .addAction(actionWithoutFreeformRemoteInput)
                .addAction(actionWithFreeformRemoteInput)
                .build();

        Pair<RemoteInput, Notification.Action> remoteInputActionPair =
                notification.findRemoteInputActionPair(true);

        assertNotNull(remoteInputActionPair);
        assertEquals(freeformRemoteInput, remoteInputActionPair.first);
        assertEquals(actionWithFreeformRemoteInput, remoteInputActionPair.second);
    }

    private static void assertMessageEquals(
            Notification.MessagingStyle.Message expected,
            Notification.MessagingStyle.Message actual) {
        assertEquals(expected.getText(), actual.getText());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
        assertEquals(expected.getSenderPerson(), actual.getSenderPerson());
    }

    private static RemoteInput newDataOnlyRemoteInput() {
        return new RemoteInput.Builder(DATA_RESULT_KEY)
            .setAllowFreeFormInput(false)
            .setAllowDataType("mimeType", true)
            .build();
    }

    private static RemoteInput newTextAndDataRemoteInput() {
        return new RemoteInput.Builder(DATA_AND_TEXT_RESULT_KEY)
            .setAllowDataType("mimeType", true)
            .build();  // allowFreeForm defaults to true
    }

    private static RemoteInput newTextRemoteInput() {
        return new RemoteInput.Builder(TEXT_RESULT_KEY).build();  // allowFreeForm defaults to true
    }

    private static void verifyRemoteInputArrayHasSingleResult(
            RemoteInput[] remoteInputs, String expectedResultKey) {
        assertTrue(remoteInputs != null && remoteInputs.length == 1);
        assertEquals(expectedResultKey, remoteInputs[0].getResultKey());
    }

    private static Notification.Action.Builder newActionBuilder() {
        return new Notification.Action.Builder(0, "title", null);
    }

    /**
     * Writes an arbitrary {@link Parcelable} into a {@link Parcel} using its writeToParcel
     * method before reading it out again to check that it was sent properly.
     */
    private static <T extends Parcelable> T writeAndReadParcelable(T original) {
        Parcel p = Parcel.obtain();
        p.writeParcelable(original, /* flags */ 0);
        p.setDataPosition(0);
        return p.readParcelable(/* classLoader */ null);
    }

    /**
     * Creates a Notification.Action by mocking initial dependencies and then applying
     * transformations if they're defined.
     */
    private Notification.Action makeNotificationAction(
            @Nullable Consumer<Builder> transformation) {
        Notification.Action.Builder actionBuilder =
            new Notification.Action.Builder(null, "Test Title", null);
        if (transformation != null) {
            transformation.accept(actionBuilder);
        }
        return actionBuilder.build();
    }

    private Notification.BubbleMetadata makeBubbleMetadata() {
        PendingIntent bubbleIntent = PendingIntent.getActivity(mContext, 0,
                new Intent().setPackage(mContext.getPackageName()), PendingIntent.FLAG_MUTABLE);

        return new Notification.BubbleMetadata.Builder(bubbleIntent,
                Icon.createWithResource(mContext, 1))
                .setDesiredHeight(BUBBLE_HEIGHT)
                .build();
    }
}

