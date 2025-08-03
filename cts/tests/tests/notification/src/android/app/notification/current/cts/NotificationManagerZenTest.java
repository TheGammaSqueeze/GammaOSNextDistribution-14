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

package android.app.notification.current.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED;
import static android.app.NotificationManager.ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED;
import static android.app.NotificationManager.ACTION_NOTIFICATION_POLICY_CHANGED;
import static android.app.NotificationManager.AUTOMATIC_RULE_STATUS_ACTIVATED;
import static android.app.NotificationManager.AUTOMATIC_RULE_STATUS_DEACTIVATED;
import static android.app.NotificationManager.AUTOMATIC_RULE_STATUS_DISABLED;
import static android.app.NotificationManager.AUTOMATIC_RULE_STATUS_ENABLED;
import static android.app.NotificationManager.AUTOMATIC_RULE_STATUS_UNKNOWN;
import static android.app.NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_ID;
import static android.app.NotificationManager.EXTRA_AUTOMATIC_ZEN_RULE_STATUS;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS;
import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
import static android.app.NotificationManager.INTERRUPTION_FILTER_NONE;
import static android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY;
import static android.app.NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE;
import static android.app.NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT;
import static android.app.NotificationManager.Policy.CONVERSATION_SENDERS_NONE;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CALLS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS;
import static android.app.NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM;
import static android.app.NotificationManager.Policy.PRIORITY_SENDERS_STARRED;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.AutomaticZenRule;
import android.app.Flags;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.app.WallpaperManager;
import android.app.compat.CompatChanges;
import android.app.cts.CtsAppTestUtils;
import android.app.stubs.AutomaticZenRuleActivity;
import android.app.stubs.GetResultActivity;
import android.app.stubs.R;
import android.app.stubs.shared.NotificationHelper.SEARCH_TYPE;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.display.ColorDisplayManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.RemoteException;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.service.notification.Condition;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.notification.ZenDeviceEffects;
import android.service.notification.ZenPolicy;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ScreenUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Tests zen/dnd related logic in NotificationManager.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationManagerZenTest extends BaseNotificationManagerTest {

    private static final String TAG = NotificationManagerZenTest.class.getSimpleName();

    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    private static final String NOTIFICATION_CHANNEL_ID_NOISY = TAG + "/noisy";
    private static final String NOTIFICATION_CHANNEL_ID_MEDIA = TAG + "/media";
    private static final String NOTIFICATION_CHANNEL_ID_GAME = TAG + "/game";
    private static final String NOTIFICATION_CHANNEL_ID_PRIORITY = TAG + "/priority";
    private static final String ALICE = "Alice";
    private static final String ALICE_PHONE = "+16175551212";
    private static final String ALICE_EMAIL = "alice@_foo._bar";
    private static final String BOB = "Bob";
    private static final String BOB_PHONE = "+16505551212";
    private static final String BOB_EMAIL = "bob@_foo._bar";
    private static final String CHARLIE = "Charlie";
    private static final String CHARLIE_PHONE = "+13305551212";
    private static final String CHARLIE_EMAIL = "charlie@_foo._bar";
    private static final int MODE_URI = 1;
    private static final int MODE_PHONE = 2;
    private static final int MODE_EMAIL = 3;
    private static final int SEND_A = 0x1;
    private static final int SEND_B = 0x2;
    private static final int SEND_C = 0x4;
    private static final int SEND_ALL = SEND_A | SEND_B | SEND_C;
    private static final int MATCHES_CALL_FILTER_NOT_PERMITTED = 0;
    private static final int MATCHES_CALL_FILTER_PERMITTED = 1;
    private static final String MATCHES_CALL_FILTER_CLASS =
            TEST_APP + ".MatchesCallFilterTestActivity";
    private static final String MINIMAL_LISTENER_CLASS = TEST_APP + ".TestNotificationListener";

    private final String NAME = "name";
    private ComponentName CONFIG_ACTIVITY;
    private final ZenPolicy POLICY = new ZenPolicy.Builder().allowAlarms(true).build();
    private final Uri CONDITION_ID = new Uri.Builder().scheme("scheme")
            .authority("authority")
            .appendPath("path")
            .appendPath("test")
            .build();
    private static final String TRIGGER_DESC = "Triggered mysteriously";
    private static final int UNRESTRICTED_TYPE = AutomaticZenRule.TYPE_IMMERSIVE; // Freely usable.
    private static final boolean ALLOW_MANUAL = true;
    private static final int ICON_RES_ID =
            android.app.notification.current.cts.R.drawable.ic_android;
    private NotificationManager.Policy mOriginalPolicy;
    private ZenPolicy mDefaultPolicy;

    @Before
    public void setUp() throws Exception {
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);

        CONFIG_ACTIVITY = new ComponentName(mContext, AutomaticZenRuleActivity.class);

        mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
        assertNotNull(mListener);

        createChannels();

        // Set up a known DND state for all tests:
        // * DND off.
        // * Alarms, Media, Calls (starred), Messages (starred), Repeat Calls, Conversations
        //   (starred) allowed.
        // * Some suppressed visual effects.
        // (using the SystemUI permission so we're certain to update global state).
        runAsSystemUi(() -> {
            mOriginalPolicy = mNotificationManager.getNotificationPolicy();

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_MEDIA | PRIORITY_CATEGORY_CALLS
                            | PRIORITY_CATEGORY_MESSAGES | PRIORITY_CATEGORY_REPEAT_CALLERS
                            | PRIORITY_CATEGORY_CONVERSATIONS, PRIORITY_SENDERS_STARRED,
                    PRIORITY_SENDERS_STARRED,
                    SUPPRESSED_EFFECT_AMBIENT | SUPPRESSED_EFFECT_PEEK | SUPPRESSED_EFFECT_LIGHTS
                            | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT,
                    CONVERSATION_SENDERS_IMPORTANT));
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);

            // Also get and cache the default policy for comparison later.
            if (Flags.modesApi()) {
                mDefaultPolicy = mNotificationManager.getDefaultZenPolicy();
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                android.os.Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);

        // Restore to the previous DND state.
        runAsSystemUi(() -> {
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
            if (mOriginalPolicy != null) {
                mNotificationManager.setNotificationPolicy(mOriginalPolicy);
            }
        });

        final ArrayList<ContentProviderOperation> operationList = new ArrayList<>();
        Uri aliceUri = lookupContact(ALICE_PHONE);
        if (aliceUri != null) {
            operationList.add(ContentProviderOperation.newDelete(aliceUri).build());
        }
        Uri bobUri = lookupContact(BOB_PHONE);
        if (bobUri != null) {
            operationList.add(ContentProviderOperation.newDelete(bobUri).build());
        }
        Uri charlieUri = lookupContact(CHARLIE_PHONE);
        if (charlieUri != null) {
            operationList.add(ContentProviderOperation.newDelete(charlieUri).build());
        }
        if (aliceUri != null || bobUri != null || charlieUri != null) {
            try {
                mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
            } catch (RemoteException e) {
                Log.e(TAG, String.format("%s: %s", e, e.getMessage()));
            } catch (OperationApplicationException e) {
                Log.e(TAG, String.format("%s: %s", e, e.getMessage()));
            }
        }

        deleteAllAutomaticZenRules();

        if (mListener != null) {
            // setUp asserts mListener isn't null, but tearDown will still run after that assertion
            // failure.
            mListener.resetData();
            mNotificationHelper.disableListener(STUB_PACKAGE_NAME);
        }

        deleteChannels();
    }

    private void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }
    }
    // usePriorities true: B, C, A
    // usePriorities false:
    //   MODE_NONE: C, B, A
    //   otherwise: A, B ,C
    private void sendNotifications(int uriMode, boolean usePriorities, boolean noisy) {
        sendNotifications(SEND_ALL, uriMode, usePriorities, noisy);
    }

    private void sendNotifications(int which, int uriMode, boolean usePriorities, boolean noisy) {
        // C, B, A when sorted by time.  Times must be in the past
        long whenA = System.currentTimeMillis() - 4000000L;
        long whenB = System.currentTimeMillis() - 2000000L;
        long whenC = System.currentTimeMillis() - 1000000L;

        // B, C, A when sorted by priorities
        int priorityA = usePriorities ? Notification.PRIORITY_MIN : Notification.PRIORITY_DEFAULT;
        int priorityB = usePriorities ? Notification.PRIORITY_MAX : Notification.PRIORITY_DEFAULT;
        int priorityC = usePriorities ? Notification.PRIORITY_LOW : Notification.PRIORITY_DEFAULT;

        final String channelId = noisy ? NOTIFICATION_CHANNEL_ID_NOISY : NOTIFICATION_CHANNEL_ID;

        Uri aliceUri = lookupContact(ALICE_PHONE);
        Uri bobUri = lookupContact(BOB_PHONE);
        Uri charlieUri = lookupContact(CHARLIE_PHONE);
        if ((which & SEND_B) != 0) {
            Notification.Builder bob = new Notification.Builder(mContext, channelId)
                    .setContentTitle(BOB)
                    .setContentText(BOB)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setPriority(priorityB)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(whenB);
            addPerson(uriMode, bob, bobUri, BOB_PHONE, BOB_EMAIL);
            mNotificationManager.notify(BOB, 2, bob.build());
        }
        if ((which & SEND_C) != 0) {
            Notification.Builder charlie =
                    new Notification.Builder(mContext, channelId)
                            .setContentTitle(CHARLIE)
                            .setContentText(CHARLIE)
                            .setSmallIcon(android.R.drawable.sym_def_app_icon)
                            .setPriority(priorityC)
                            .setCategory(Notification.CATEGORY_MESSAGE)
                            .setWhen(whenC);
            addPerson(uriMode, charlie, charlieUri, CHARLIE_PHONE, CHARLIE_EMAIL);
            mNotificationManager.notify(CHARLIE, 3, charlie.build());
        }
        if ((which & SEND_A) != 0) {
            Notification.Builder alice = new Notification.Builder(mContext, channelId)
                    .setContentTitle(ALICE)
                    .setContentText(ALICE)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setPriority(priorityA)
                    .setCategory(Notification.CATEGORY_MESSAGE)
                    .setWhen(whenA);
            addPerson(uriMode, alice, aliceUri, ALICE_PHONE, ALICE_EMAIL);
            mNotificationManager.notify(ALICE, 1, alice.build());
        }

        mNotificationHelper.findPostedNotification(ALICE, 1, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(BOB, 2, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(CHARLIE, 3, SEARCH_TYPE.POSTED);
    }

    private void sendEventAlarmReminderNotifications(int which) {
        long when = System.currentTimeMillis() - 4000000L;
        final String channelId = NOTIFICATION_CHANNEL_ID;

        // Event notification to Alice
        if ((which & SEND_A) != 0) {
            Notification.Builder alice = new Notification.Builder(mContext, channelId)
                    .setContentTitle(ALICE)
                    .setContentText(ALICE)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setCategory(Notification.CATEGORY_EVENT)
                    .setWhen(when);
            mNotificationManager.notify(ALICE, 4, alice.build());
        }

        // Alarm notification to Bob
        if ((which & SEND_B) != 0) {
            Notification.Builder bob = new Notification.Builder(mContext, channelId)
                    .setContentTitle(BOB)
                    .setContentText(BOB)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setWhen(when);
            mNotificationManager.notify(BOB, 5, bob.build());
        }

        // Reminder notification to Charlie
        if ((which & SEND_C) != 0) {
            Notification.Builder charlie =
                    new Notification.Builder(mContext, channelId)
                            .setContentTitle(CHARLIE)
                            .setContentText(CHARLIE)
                            .setSmallIcon(android.R.drawable.sym_def_app_icon)
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .setWhen(when);
            mNotificationManager.notify(CHARLIE, 6, charlie.build());
        }

        mNotificationHelper.findPostedNotification(ALICE, 4, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(BOB, 5, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(CHARLIE, 6, SEARCH_TYPE.POSTED);
    }

    private void sendAlarmOtherMediaNotifications(int which) {
        long when = System.currentTimeMillis() - 4000000L;
        final String channelId = NOTIFICATION_CHANNEL_ID;

        // Alarm notification to Alice
        if ((which & SEND_A) != 0) {
            Notification.Builder alice = new Notification.Builder(mContext, channelId)
                    .setContentTitle(ALICE)
                    .setContentText(ALICE)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setWhen(when);
            mNotificationManager.notify(ALICE, 7, alice.build());
        }

        // "Other" notification to Bob
        if ((which & SEND_B) != 0) {
            Notification.Builder bob = new Notification.Builder(mContext,
                    NOTIFICATION_CHANNEL_ID_GAME)
                    .setContentTitle(BOB)
                    .setContentText(BOB)
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setWhen(when);
            mNotificationManager.notify(BOB, 8, bob.build());
        }

        // Media notification to Charlie
        if ((which & SEND_C) != 0) {
            Notification.Builder charlie =
                    new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID_MEDIA)
                            .setContentTitle(CHARLIE)
                            .setContentText(CHARLIE)
                            .setSmallIcon(android.R.drawable.sym_def_app_icon)
                            .setWhen(when);
            mNotificationManager.notify(CHARLIE, 9, charlie.build());
        }

        mNotificationHelper.findPostedNotification(ALICE, 7, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(BOB, 8, SEARCH_TYPE.POSTED);
        mNotificationHelper.findPostedNotification(CHARLIE, 9, SEARCH_TYPE.POSTED);
    }

    private boolean hasReadContactsPermission(String pkgName) {
        return mPackageManager.checkPermission(Manifest.permission.READ_CONTACTS, pkgName)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void toggleReadContactsPermission(String pkgName, boolean on) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (on) {
                mInstrumentation.getUiAutomation().grantRuntimePermission(pkgName,
                        Manifest.permission.READ_CONTACTS);
            } else {
                mInstrumentation.getUiAutomation().revokeRuntimePermission(pkgName,
                        Manifest.permission.READ_CONTACTS);
            }
        });
    }

    // Creates a GetResultActivity into which one can call startActivityForResult with
    // in order to test the outcome of an activity that returns a result code.
    private GetResultActivity setUpGetResultActivity() {
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        GetResultActivity activity = (GetResultActivity) mInstrumentation.startActivitySync(intent);
        mInstrumentation.waitForIdleSync();
        activity.clearResult();
        return activity;
    }

    private void addPerson(int mode, Notification.Builder note,
            Uri uri, String phone, String email) {
        if (mode == MODE_URI && uri != null) {
            note.addPerson(uri.toString());
        } else if (mode == MODE_PHONE) {
            note.addPerson(Uri.fromParts("tel", phone, null).toString());
        } else if (mode == MODE_EMAIL) {
            note.addPerson(Uri.fromParts("mailto", email, null).toString());
        }
    }

    private void insertSingleContact(String name, String phone, String email, boolean starred) {
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.STARRED, starred ? 1 : 0);
        operationList.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(
                ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
        operationList.add(builder.build());

        if (phone != null) {
            builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
            builder.withValueBackReference(
                    ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, 0);
            builder.withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
            builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
            builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone);
            builder.withValue(ContactsContract.Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }
        if (email != null) {
            builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
            builder.withValueBackReference(
                    ContactsContract.CommonDataKinds.Email.RAW_CONTACT_ID, 0);
            builder.withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
            builder.withValue(ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME);
            builder.withValue(ContactsContract.CommonDataKinds.Email.DATA, email);
            operationList.add(builder.build());
        }

        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private Uri lookupContact(String phone) {
        Cursor c = null;
        try {
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(phone));
            String[] projection = new String[] { ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY };
            c = mContext.getContentResolver().query(phoneUri, projection, null, null, null);
            if (c != null && c.getCount() > 0) {
                c.moveToFirst();
                int lookupIdx = c.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY);
                int idIdx = c.getColumnIndex(ContactsContract.Contacts._ID);
                String lookupKey = c.getString(lookupIdx);
                long contactId = c.getLong(idIdx);
                return ContactsContract.Contacts.getLookupUri(contactId, lookupKey);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Problem getting content resolver or performing contacts query.", t);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return null;
    }

    private boolean isStarred(Uri uri) {
        Cursor c = null;
        boolean starred = false;
        try {
            String[] projection = new String[] { ContactsContract.Contacts.STARRED };
            c = mContext.getContentResolver().query(uri, projection, null, null, null);
            if (c != null && c.getCount() > 0) {
                int starredIdx = c.getColumnIndex(ContactsContract.Contacts.STARRED);
                while (c.moveToNext()) {
                    starred |= c.getInt(starredIdx) == 1;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Problem getting content resolver or performing contacts query.", t);
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return starred;
    }

    private void createChannels() {
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_MIN);
        mNotificationManager.createNotificationChannel(channel);
        NotificationChannel noisyChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_NOISY,
                NOTIFICATION_CHANNEL_ID_NOISY, NotificationManager.IMPORTANCE_HIGH);
        noisyChannel.enableVibration(true);
        mNotificationManager.createNotificationChannel(noisyChannel);
        NotificationChannel mediaChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_MEDIA,
                NOTIFICATION_CHANNEL_ID_MEDIA, NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes.Builder aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA);
        mediaChannel.setSound(null, aa.build());
        mNotificationManager.createNotificationChannel(mediaChannel);
        NotificationChannel gameChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_GAME,
                NOTIFICATION_CHANNEL_ID_GAME, NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes.Builder aa2 = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME);
        gameChannel.setSound(null, aa2.build());
        mNotificationManager.createNotificationChannel(gameChannel);
        if (Flags.modesApi()) {
            // "Priority" channel has canBypassDnd set to true.
            NotificationChannel priorityChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID_PRIORITY, NOTIFICATION_CHANNEL_ID_PRIORITY,
                    NotificationManager.IMPORTANCE_HIGH);
            priorityChannel.setBypassDnd(true);
            mNotificationManager.createNotificationChannel(priorityChannel);
        }
    }

    private void deleteChannels() {
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID_NOISY);
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID_MEDIA);
        mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID_GAME);
        if (Flags.modesApi()) {
            mNotificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID_PRIORITY);
        }
    }

    private void deleteAllAutomaticZenRules() {
        Map<String, AutomaticZenRule> rules = mNotificationManager.getAutomaticZenRules();
        for (String ruleId : rules.keySet()) {
            // Delete rules "as system" so they are not preserved.
            // Otherwise, if updated with fromUser=true and then deleted "as app", they might be
            // resurrected by other tests, making the outcome order-dependent.
            runAsSystemUi(() -> mNotificationManager.removeAutomaticZenRule(ruleId));
        }
    }

    private void deleteSingleContact(Uri uri) {
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        operationList.add(ContentProviderOperation.newDelete(uri).build());
        try {
            mContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
    }

    private int findTagInKeys(String tag, List<String> orderedKeys) {
        for (int i = 0; i < orderedKeys.size(); i++) {
            if (orderedKeys.get(i).contains(tag)) {
                return i;
            }
        }
        return -1;
    }

    // Simple helper function to take a phone number's string representation and make a tel: uri
    private Uri makePhoneUri(String phone) {
        return new Uri.Builder()
                .scheme("tel")
                .encodedOpaquePart(phone)  // don't re-encode anything passed in
                .build();
    }

    // Returns whether ZenPolicies are equivalent after any unset fields are set to the defaults.
    private boolean doPoliciesMatchWithDefaults(ZenPolicy a, ZenPolicy b) {
        return Objects.equals(mDefaultPolicy.overwrittenWith(a), mDefaultPolicy.overwrittenWith(b));
    }

    private AutomaticZenRule createRule(String name, int filter) {
        return new AutomaticZenRule(name, null,
                new ComponentName(mContext, AutomaticZenRuleActivity.class),
                new Uri.Builder().scheme("scheme")
                        .appendPath("path")
                        .appendQueryParameter("fake_rule", "fake_value")
                        .build(), null, filter, true);
    }

    private AutomaticZenRule createRule(String name) {
        return createRule(name, INTERRUPTION_FILTER_PRIORITY);
    }

    // TESTS START

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    @RequiresFlagsDisabled(Flags.FLAG_MODES_UI)
    public void testAreAutomaticZenRulesUserManaged_flagsOff() {
        assertFalse(mNotificationManager.areAutomaticZenRulesUserManaged());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_MODES_API, Flags.FLAG_MODES_UI})
    public void testAreAutomaticZenRulesUserManaged_flagsOn() {
        assertTrue(mNotificationManager.areAutomaticZenRulesUserManaged());
    }

    @Test
    public void testNotificationPolicyVisualEffectsEqual() {
        NotificationManager.Policy policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON);
        NotificationManager.Policy policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_PEEK);
        assertTrue(policy.equals(policy2));
        assertTrue(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                0);
        assertFalse(policy.equals(policy2));
        assertFalse(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_OFF);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_FULL_SCREEN_INTENT | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS);
        assertTrue(policy.equals(policy2));
        assertTrue(policy2.equals(policy));

        policy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_OFF);
        policy2 = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_LIGHTS);
        assertFalse(policy.equals(policy2));
        assertFalse(policy2.equals(policy));
    }

    @Test
    public void testGetSuppressedVisualEffectsOff_ranking() throws Exception {
        mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
        assertNotNull(mListener);

        final int notificationId = 1;
        sendNotification(notificationId, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification

        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking =
                new NotificationListenerService.Ranking();

        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);

                // check notification key match
                assertEquals(0, outRanking.getSuppressedVisualEffects());
            }
        }
    }

    @Test
    public void testGetSuppressedVisualEffects_ranking() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
            assertNotNull(mListener);

            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_PEEK));
            } else {
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON));
            }
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

            final int notificationId = 1;
            // update notification
            sendNotification(notificationId, R.drawable.black);
            Thread.sleep(500); // wait for notification listener to receive notification

            NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
            NotificationListenerService.Ranking outRanking =
                    new NotificationListenerService.Ranking();

            for (String key : rankingMap.getOrderedKeys()) {
                if (key.contains(mListener.getPackageName())) {
                    rankingMap.getRanking(key, outRanking);

                    if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                        assertEquals(SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_PEEK,
                                outRanking.getSuppressedVisualEffects());
                    } else {
                        assertEquals(SUPPRESSED_EFFECT_SCREEN_ON,
                                outRanking.getSuppressedVisualEffects());
                    }
                }
            }
        } finally {
            // reset notification policy
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }

    }

    @Test
    public void testConsolidatedNotificationPolicy() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            // no custom ZenPolicy, so consolidatedPolicy should equal the default notif policy
            assertEquals(mNotificationManager.getConsolidatedNotificationPolicy(),
                    mNotificationManager.getNotificationPolicy());

            // setup custom ZenPolicy for an automatic rule
            AutomaticZenRule rule = createRule("test_consolidated_policy",
                    INTERRUPTION_FILTER_PRIORITY);
            rule.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(true)
                    .allowMedia(false)
                    .build());
            String id = mNotificationManager.addAutomaticZenRule(rule);
            // set condition of the automatic rule to TRUE
            Condition condition = new Condition(rule.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id, condition);

            Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            NotificationManager.Policy consolidatedPolicy =
                    mNotificationManager.getConsolidatedNotificationPolicy();

            if (Flags.modesApi()) {
                // Expect the final consolidated policy to be effectively equivalent to the
                // specified custom policy with remaining fields filled in by defaults.
                ZenPolicy fullySpecified = mDefaultPolicy.overwrittenWith(rule.getZenPolicy());
                assertPolicyCategoriesMatchZenPolicy(consolidatedPolicy, fullySpecified);
            } else {
                // reminders is allowed from the automatic rule's custom ZenPolicy
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_REMINDERS) != 0);

                // media is disallowed from the automatic rule's custom ZenPolicy
                assertFalse((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);

                // other stuff is from the default notification policy (see #setUp)
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS) != 0);
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS)
                        != 0);
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CALLS) != 0);
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_REPEAT_CALLERS)
                            != 0);
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MESSAGES) != 0);
                assertFalse(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0);
                assertFalse(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_EVENTS) != 0);
            }
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testConsolidatedNotificationPolicyMultiRules() throws Exception {
        final int originalFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            toggleNotificationPolicyAccess(mContext.getPackageName(),
                    InstrumentationRegistry.getInstrumentation(), true);

            // setup custom ZenPolicy for two automatic rules
            AutomaticZenRule rule1 = createRule("test_consolidated_policyq",
                    INTERRUPTION_FILTER_PRIORITY);
            rule1.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(false)
                    .allowSystem(true)
                    .allowAlarms(false)
                    .build());
            AutomaticZenRule rule2 = createRule("test_consolidated_policy2",
                    INTERRUPTION_FILTER_PRIORITY);
            rule2.setZenPolicy(new ZenPolicy.Builder()
                    .allowReminders(true)
                    .allowSystem(true)
                    .allowMedia(true)
                    .build());
            String id1 = mNotificationManager.addAutomaticZenRule(rule1);
            String id2 = mNotificationManager.addAutomaticZenRule(rule2);
            Condition onCondition1 = new Condition(rule1.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            Condition onCondition2 = new Condition(rule2.getConditionId(), "summary",
                    Condition.STATE_TRUE);
            mNotificationManager.setAutomaticZenRuleState(id1, onCondition1);
            mNotificationManager.setAutomaticZenRuleState(id2, onCondition2);

            Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            NotificationManager.Policy consolidatedPolicy =
                    mNotificationManager.getConsolidatedNotificationPolicy();

            if (Flags.modesApi()) {
                // if modesApi is enabled, confirm that these settings match depending on the device
                // defaults. Each rule inherits default values for any unset fields, so for fields
                // where only one rule has expressed an explicit opinion about the setting, the
                // default setting may be more restrictive and win.
                ZenPolicy expectedCombined = new ZenPolicy.Builder()
                        .allowReminders(false)  // rule1 wins over rule2
                        .allowSystem(true)  // both active rules set this
                        .allowAlarms(false)  // opinion only from rule1
                        // media opinion only from rule2 (to be allowed); therefore it depends on
                        // default settings
                        .allowMedia(
                                mDefaultPolicy.getPriorityCategoryAlarms() == ZenPolicy.STATE_ALLOW)
                        .build();

                // The rest are entirely from the default policy.
                ZenPolicy fullySpecified = mDefaultPolicy.overwrittenWith(expectedCombined);
                assertPolicyCategoriesMatchZenPolicy(consolidatedPolicy, fullySpecified);
            } else {
                // reminders aren't allowed from rule1 overriding rule2
                // (not allowed takes precedence over allowed)
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_REMINDERS) == 0);

                // system allowed from both
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0);

                // alarms aren't allowed from rule1, so that alarm setting will always win
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS) == 0);

                // media is allowed from rule2
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);

                // other stuff is from the default notification policy (see #setUp)
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS)
                            != 0);
                assertTrue((consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_CALLS) != 0);
                assertTrue(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_MESSAGES) != 0);
                assertFalse(
                        (consolidatedPolicy.priorityCategories & PRIORITY_CATEGORY_EVENTS) != 0);
            }
        } finally {
            mNotificationManager.setInterruptionFilter(originalFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_MODES_API})
    public void testConsolidatedNotificationPolicy_broadcasts() throws Exception {
        // Setup also changes Policy and creates a DND-bypassing channel, so we might get 1-2
        // extra broadcasts. Make sure they are out of the way.
        Thread.sleep(500);
        assertThat(mNotificationManager.getConsolidatedNotificationPolicy().priorityCategories
                & PRIORITY_CATEGORY_ALARMS).isNotEqualTo(0);

        // Set up a rule with a custom ZenPolicy.
        AutomaticZenRule rule = createRule("testRule");
        rule.setZenPolicy(new ZenPolicy.Builder()
                .allowReminders(false)
                .allowSystem(true)
                .allowAlarms(false)
                .build());
        String id = mNotificationManager.addAutomaticZenRule(rule);

        // Enable rule, and check for broadcast.
        NotificationManagerBroadcastReceiver brOn = new NotificationManagerBroadcastReceiver();
        brOn.register(mContext, ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED, 1);
        Condition conditionOn =
                new Condition(rule.getConditionId(), "on", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, conditionOn);

        brOn.assertBroadcastsReceivedWithin(Duration.ofMillis(500));
        NotificationManager.Policy ruleOnPolicy = (NotificationManager.Policy) brOn.getExtra(
                NotificationManager.EXTRA_NOTIFICATION_POLICY, 0, 0);
        assertThat(ruleOnPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS).isEqualTo(0);

        // TODO: b/324376849 - Registered BR in a DND-access pkg gets broadcast twice.
        // Thread.sleep(500);
        // assertThat(brOn.results).hasSize(1); // Also no *extra* broadcasts received.
        brOn.unregister();

        // Disable rule, and check for broadcast.
        NotificationManagerBroadcastReceiver brOff = new NotificationManagerBroadcastReceiver();
        brOff.register(mContext, ACTION_CONSOLIDATED_NOTIFICATION_POLICY_CHANGED, 1);
        Condition conditionOff =
                new Condition(rule.getConditionId(), "on", Condition.STATE_FALSE);
        mNotificationManager.setAutomaticZenRuleState(id, conditionOff);

        brOff.assertBroadcastsReceivedWithin(Duration.ofMillis(500));
        NotificationManager.Policy ruleOffPolicy = (NotificationManager.Policy) brOff.getExtra(
                NotificationManager.EXTRA_NOTIFICATION_POLICY, 0, 0);
        assertThat(ruleOffPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS).isNotEqualTo(0);

        // TODO: b/324376849 - Registered BR in a DND-access pkg gets broadcast twice.
        // Thread.sleep(500);
        // assertThat(brOff.results).hasSize(1); // Also no *extra* broadcasts received.
        brOff.unregister();
    }

    @Test
    public void testNotificationPolicy_broadcasts() throws Exception {
        // Setup also changes Policy and creates a DND-bypassing channel, so we might get 1-2
        // extra broadcasts. Make sure they are out of the way.
        Thread.sleep(500);
        assertThat(mNotificationManager.getNotificationPolicy().priorityCategories
                & PRIORITY_CATEGORY_ALARMS).isNotEqualTo(0);
        NotificationManagerBroadcastReceiver br = new NotificationManagerBroadcastReceiver();
        br.register(mContext, ACTION_NOTIFICATION_POLICY_CHANGED, 1);

        NotificationManager.Policy updatePolicy = new NotificationManager.Policy(0, 0, 0);
        runAsSystemUi(() -> mNotificationManager.setNotificationPolicy(updatePolicy));

        br.assertBroadcastsReceivedWithin(Duration.ofMillis(500));
        if (Flags.modesApi()) {
            NotificationManager.Policy broadcastPolicy = (NotificationManager.Policy) br.getExtra(
                    NotificationManager.EXTRA_NOTIFICATION_POLICY, 0, 0);
            assertThat(broadcastPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS).isEqualTo(0);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void testConsolidatedNotificationPolicy_mergesAllowPriorityChannels() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // setup custom ZenPolicy for an automatic rule
        AutomaticZenRule rule = createRule("test_consolidated_policy_priority_channels",
                INTERRUPTION_FILTER_PRIORITY);
        rule.setZenPolicy(new ZenPolicy.Builder()
                .allowPriorityChannels(true)
                .build());
        String id = mNotificationManager.addAutomaticZenRule(rule);
        // set condition of the automatic rule to TRUE
        Condition condition = new Condition(rule.getConditionId(), "summary",
                Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

        assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

        NotificationManager.Policy consolidatedPolicy =
                mNotificationManager.getConsolidatedNotificationPolicy();

        // channels are permitted as set by the rule
        assertTrue(consolidatedPolicy.allowPriorityChannels());

        // new rule that disallows channels
        AutomaticZenRule rule2 = createRule("test_consolidated_policy_no_channels",
                INTERRUPTION_FILTER_PRIORITY);
        rule2.setZenPolicy(new ZenPolicy.Builder()
                .allowPriorityChannels(false)
                .build());
        String id2 = mNotificationManager.addAutomaticZenRule(rule2);
        Condition onCondition2 = new Condition(rule2.getConditionId(), "summary",
                Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id2, onCondition2);

        // now priority channels are disallowed because "no channels" overrides "priority"
        consolidatedPolicy =
                mNotificationManager.getConsolidatedNotificationPolicy();
        assertFalse(consolidatedPolicy.allowPriorityChannels());
    }

    @Test
    public void testPostPCanToggleAlarmsMediaSystemTest() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                // Post-P can toggle alarms, media, system
                // toggle on alarms, media, system:
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                        PRIORITY_CATEGORY_ALARMS
                                | PRIORITY_CATEGORY_MEDIA
                                | PRIORITY_CATEGORY_SYSTEM, 0, 0));
                NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_ALARMS) != 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0);

                // toggle off alarms, media, system
                mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0));
                policy = mNotificationManager.getNotificationPolicy();
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_ALARMS) == 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_MEDIA) == 0);
                assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) == 0);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testPostRCanToggleConversationsTest() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();

        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    0, 0, 0, 0));
            NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
            assertEquals(0, (policy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS));
            assertEquals(CONVERSATION_SENDERS_NONE, policy.priorityConversationSenders);

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CONVERSATIONS, 0, 0, 0, CONVERSATION_SENDERS_ANYONE));
            policy = mNotificationManager.getNotificationPolicy();
            assertTrue((policy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS) != 0);
            assertEquals(CONVERSATION_SENDERS_ANYONE, policy.priorityConversationSenders);

        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testTotalSilenceOnlyMuteStreams() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // ensure volume is not muted/0 to start test
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
        // exception for presidential alert
        //mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 1, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0);

        AutomaticZenRule rule = createRule("test_total_silence", INTERRUPTION_FILTER_NONE);
        String id = mNotificationManager.addAutomaticZenRule(rule);
        Condition condition =
                new Condition(rule.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        if (!SdkLevel.isAtLeastV()) {
            runAsSystemUi(
                    () -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY));
        }

        // delay for streams to get into correct mute states
        Thread.sleep(1000);
        assertTrue("Music (media) stream should be muted",
                mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC));
        assertTrue("System stream should be muted",
                mAudioManager.isStreamMute(AudioManager.STREAM_SYSTEM));
        // exception for presidential alert
        //assertTrue("Alarm stream should be muted",
        //        mAudioManager.isStreamMute(AudioManager.STREAM_ALARM));

        if (SdkLevel.isAtLeastV()) {
            // For the audio stream to be muted correctly, we need the priority channels setting;
            // otherwise, pre-V, we cannot guarantee that no channels are bypassing DND.
            assertTrue("Ringer stream should be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_RING));
        }
    }

    @Test
    public void testAlarmsOnlyMuteStreams() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // ensure volume is not muted/0 to start test
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_ALARM, 1, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 1, 0);
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, 1, 0);

        if (!SdkLevel.isAtLeastV()) {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_MEDIA, 0, 0));
        }
        AutomaticZenRule rule = createRule("test_alarms", INTERRUPTION_FILTER_ALARMS);
        String id = mNotificationManager.addAutomaticZenRule(rule);
        Condition condition =
                new Condition(rule.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        if (!SdkLevel.isAtLeastV()) {
            runAsSystemUi(
                    () -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY));
        }

        // delay for streams to get into correct mute states
        Thread.sleep(1000);
        assertFalse("Music (media) stream should not be muted",
                mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC));
        assertTrue("System stream should be muted",
                mAudioManager.isStreamMute(AudioManager.STREAM_SYSTEM));
        assertFalse("Alarm stream should not be muted",
                mAudioManager.isStreamMute(AudioManager.STREAM_ALARM));

        if (SdkLevel.isAtLeastV()) {
            // For the audio stream to be muted correctly, we need the priority channels setting;
            // otherwise, pre-V, we cannot guarantee that no channels are bypassing DND.
            assertTrue("Ringer stream should be muted",
                    mAudioManager.isStreamMute(AudioManager.STREAM_RING));
        }
    }

    @Test
    public void testAddAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        assertNotNull(id);
        assertRulesEqual(ruleToCreate, mNotificationManager.getAutomaticZenRule(id));
    }

    @Test
    public void testUpdateAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        ruleToCreate.setEnabled(false);
        mNotificationManager.updateAutomaticZenRule(id, ruleToCreate);

        assertNotNull(id);
        assertRulesEqual(ruleToCreate, mNotificationManager.getAutomaticZenRule(id));
    }

    @Test
    public void testRemoveAutomaticZenRule_configActivity() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        assertNotNull(id);
        mNotificationManager.removeAutomaticZenRule(id);

        assertNull(mNotificationManager.getAutomaticZenRule(id));
        assertEquals(0, mNotificationManager.getAutomaticZenRules().size());
    }

    @Test
    public void testSetAutomaticZenRuleState() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());
    }

    @Test
    public void testSetAutomaticZenRuleState_turnOff() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        // make sure DND is off
        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "on", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());

        // disable DND
        condition = new Condition(ruleToCreate.getConditionId(), "off", Condition.STATE_FALSE);

        mNotificationManager.setAutomaticZenRuleState(id, condition);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);
    }

    @Test
    public void testSetAutomaticZenRuleState_deletedRule() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        assertExpectedDndState(ruleToCreate.getInterruptionFilter());

        mNotificationManager.removeAutomaticZenRule(id);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);
    }

    @Test
    public void testSetAutomaticZenRuleState_multipleRules() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("Rule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        AutomaticZenRule secondRuleToCreate = createRule("Rule 2");
        secondRuleToCreate.setInterruptionFilter(INTERRUPTION_FILTER_NONE);
        String secondId = mNotificationManager.addAutomaticZenRule(secondRuleToCreate);

        // make sure DND is off
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        Condition secondCondition =
                new Condition(secondRuleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(secondId, secondCondition);

        // the second rule has a 'more silent' DND filter, so the system wide DND should be
        // using its filter
        assertExpectedDndState(secondRuleToCreate.getInterruptionFilter());

        // remove intense rule, system should fallback to other rule
        mNotificationManager.removeAutomaticZenRule(secondId);
        assertExpectedDndState(ruleToCreate.getInterruptionFilter());
    }

    @Test
    public void testSetNotificationPolicy_P_setOldFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF
                        | SUPPRESSED_EFFECT_PEEK | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;

                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testSetNotificationPolicy_P_setNewFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {
                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_NOTIFICATION_LIST | SUPPRESSED_EFFECT_AMBIENT
                                | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_NOTIFICATION_LIST | SUPPRESSED_EFFECT_SCREEN_OFF
                        | SUPPRESSED_EFFECT_AMBIENT | SUPPRESSED_EFFECT_LIGHTS
                        | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testSetNotificationPolicy_P_setOldNewFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            if (mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.P) {

                NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_STATUS_BAR);
                mNotificationManager.setNotificationPolicy(appPolicy);

                int expected = SUPPRESSED_EFFECT_STATUS_BAR;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);

                appPolicy = new NotificationManager.Policy(0, 0, 0,
                        SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_AMBIENT
                                | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT);
                mNotificationManager.setNotificationPolicy(appPolicy);

                expected = SUPPRESSED_EFFECT_SCREEN_OFF | SUPPRESSED_EFFECT_AMBIENT
                        | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
                assertEquals(expected,
                        mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
            }
        } finally {
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }


    @Test
    public void testMatchesCallFilter_noPermissions() {
        // make sure we definitely don't have contacts access
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        try {
            toggleReadContactsPermission(TEST_APP, false);

            // start an activity that has no permissions, which will run matchesCallFilter on
            // a meaningless uri. The result code indicates whether or not the method call was
            // permitted.
            final Intent mcfIntent = new Intent(Intent.ACTION_MAIN);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with no permissions, this call should not have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_NOT_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    @Test
    public void testMatchesCallFilter_listenerPermissionOnly() throws Exception {
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        // minimal listener service so that it can be given listener permissions
        final ComponentName listenerComponent =
                new ComponentName(TEST_APP, MINIMAL_LISTENER_CLASS);
        try {
            // make surethat we don't for some reason have contacts access
            toggleReadContactsPermission(TEST_APP, false);

            // grant the notification app package notification listener access;
            // give it time to succeed
            toggleExternalListenerAccess(listenerComponent, true);
            Thread.sleep(500);

            // set up & run intent
            final Intent mcfIntent = new Intent(Intent.ACTION_MAIN);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with just listener permissions, this call should have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            // clean up listener access, reset read contacts access
            toggleExternalListenerAccess(listenerComponent, false);
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    @Test
    public void testMatchesCallFilter_contactsPermissionOnly() throws Exception {
        // grant the notification app package contacts read access
        boolean hadReadPerm = hasReadContactsPermission(TEST_APP);
        try {
            toggleReadContactsPermission(TEST_APP, true);

            // set up & run intent
            final Intent mcfIntent = new Intent(Intent.ACTION_MAIN);
            mcfIntent.setClassName(TEST_APP, MATCHES_CALL_FILTER_CLASS);
            GetResultActivity grActivity = setUpGetResultActivity();
            grActivity.startActivityForResult(mcfIntent, REQUEST_CODE);
            UiDevice.getInstance(mInstrumentation).waitForIdle();

            // with just contacts read permissions, this call should have been permitted
            GetResultActivity.Result result = grActivity.getResult();
            assertEquals(REQUEST_CODE, result.requestCode);
            assertEquals(MATCHES_CALL_FILTER_PERMITTED, result.resultCode);
            grActivity.finishActivity(REQUEST_CODE);
        } finally {
            // clean up contacts access
            toggleReadContactsPermission(TEST_APP, hadReadPerm);
        }
    }

    @Test
    public void testMatchesCallFilter_zenOff() throws Exception {
        // zen mode is not on so nothing is filtered; matchesCallFilter should always pass
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        try {
            // allowed from anyone: nothing is filtered, and make sure change went through
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
            assertExpectedDndState(INTERRUPTION_FILTER_ALL);

            // create a phone URI from which to receive a call
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode("+16175551212"));
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
        }
    }

    @Test
    public void testMatchesCallFilter_noCallInterruptions() throws Exception {
        // when no call interruptions are allowed at all, or only alarms, matchesCallFilter
        // should always fail
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        try {
            // create a phone URI from which to receive a call
            Uri phoneUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode("+16175551212"));

            // no interruptions allowed at all
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_NONE);
            assertExpectedDndState(INTERRUPTION_FILTER_NONE);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            // only alarms
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALARMS);
            assertExpectedDndState(INTERRUPTION_FILTER_ALARMS);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_MESSAGES, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
        }
    }

    @Test
    public void testMatchesCallFilter_someCallers() throws Exception {
        // zen mode is active; check various configurations where some calls, but not all calls,
        // are allowed
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();

        // for storing lookup URIs for deleting the contacts afterwards
        Uri aliceUri = null;
        Uri bobUri = null;
        try {
            // set up phone numbers: one starred, one regular, one unknown number
            // starred contact from whom to receive a call
            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // non-starred contact from whom to also receive a call
            insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
            bobUri = lookupContact(BOB_PHONE);
            Uri bobPhoneUri = makePhoneUri(BOB_PHONE);

            // non-contact phone URI
            Uri phoneUri = makePhoneUri("+16175555656");

            // set up: any contacts are allowed to call.
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CALLS,
                    NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS, 0));

            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // in this case Alice and Bob should get through but not the unknown number.
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
            assertTrue(mNotificationManager.matchesCallFilter(bobPhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));

            // set up: only starred contacts are allowed to call.
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_CALLS,
                    PRIORITY_SENDERS_STARRED, 0));
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // now only Alice should be allowed to get through
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(bobPhoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }
            if (bobUri != null) {
                deleteSingleContact(bobUri);
            }
        }
    }

    @Test
    public void testMatchesCallFilter_repeatCallers() throws Exception {
        // if repeat callers are allowed, an unknown number calling twice should go through
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        long startTime = System.currentTimeMillis();
        try {
            // create phone URIs from which to receive a call; one US, one non-US,
            // both fully specified
            Uri phoneUri = makePhoneUri("+16175551212");
            Uri phoneUri2 = makePhoneUri("+81 75 350 6006");

            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            // not repeat callers yet, so it shouldn't be allowed
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri2));

            // register a call from number 1, then cancel the notification, which is when
            // a call is actually recorded.
            sendNotification(1, null, R.drawable.blue, true, phoneUri);
            cancelAndPoll(1);

            // now this number should count as a repeat caller
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri));
            assertFalse(mNotificationManager.matchesCallFilter(phoneUri2));

            // also, any other variants of this phone number should also count as a repeat caller
            Uri[] variants = { makePhoneUri(Uri.encode("+1-617-555-1212")),
                    makePhoneUri("+1 (617) 555-1212") };
            for (int i = 0; i < variants.length; i++) {
                assertTrue("phone variant " + variants[i] + " should still match",
                        mNotificationManager.matchesCallFilter(variants[i]));
            }

            // register call 2
            sendNotification(2, null, R.drawable.blue, true, phoneUri2);
            cancelAndPoll(2);

            // now this should be a repeat caller
            assertTrue(mNotificationManager.matchesCallFilter(phoneUri2));

            Uri[] variants2 = { makePhoneUri(Uri.encode("+81 75 350 6006")),
                    makePhoneUri("+81753506006")};
            for (int j = 0; j < variants2.length; j++) {
                assertTrue("phone variant " + variants2[j] + " should still match",
                        mNotificationManager.matchesCallFilter(variants2[j]));
            }
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);

            // make sure we clean up the recent call, otherwise future runs of this will fail
            // and we'll have a fake call still kicking around somewhere.
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    @Test
    public void testMatchesCallFilter_repeatCallers_fromContact() throws Exception {
        // set up such that only repeat callers (and not any individuals) are allowed; make sure
        // that a call registered with a contact's lookup URI will return the correct info
        // when matchesCallFilter is called with their phone number
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;
        long startTime = System.currentTimeMillis();
        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // no one has called; matchesCallFilter should return false for both URIs
            assertFalse(mNotificationManager.matchesCallFilter(aliceUri));
            assertFalse(mNotificationManager.matchesCallFilter(alicePhoneUri));

            assertTrue(aliceUri.toString()
                    .startsWith(ContactsContract.Contacts.CONTENT_LOOKUP_URI.toString()));

            // register a call from Alice via the contact lookup URI, then cancel so the call is
            // recorded accordingly.
            sendNotification(1, null, R.drawable.blue, true, aliceUri);
            // wait for contact lookup of number to finish; this can take a while because it runs
            // in the background, so give it a fair bit of time
            Thread.sleep(3000);
            cancelAndPoll(1);

            // now a phone call from Alice's phone number should match the repeat callers list
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }

            // clean up the recorded calls
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    @Test
    public void testRepeatCallers_repeatCallNotIntercepted_contactAfterPhone() throws Exception {
        mListener = mNotificationHelper.enableListener(STUB_PACKAGE_NAME);
        assertNotNull(mListener);

        // if a call is recorded with just phone number info (not a contact's uri), which may
        // happen when the same contact calls across multiple apps (or if the contact uri provided
        // is otherwise inconsistent), check for the contact's phone number
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;
        long startTime = System.currentTimeMillis();
        try {
            mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                    PRIORITY_CATEGORY_REPEAT_CALLERS, 0, 0));
            // turn on manual DND
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);
            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);

            // no one has called; matchesCallFilter should return false for both URIs
            assertFalse(mNotificationManager.matchesCallFilter(aliceUri));
            assertFalse(mNotificationManager.matchesCallFilter(alicePhoneUri));

            // register a call from Alice via just the phone number
            sendNotification(1, null, R.drawable.blue, true, alicePhoneUri);
            Thread.sleep(1000); // give the listener some time to receive info

            // check that the first notification is intercepted
            StatusBarNotification sbn = mNotificationHelper.findPostedNotification(null, 1,
                    SEARCH_TYPE.POSTED);
            assertNotNull(sbn);
            assertTrue(mListener.mIntercepted.containsKey(sbn.getKey()));
            assertTrue(mListener.mIntercepted.get(sbn.getKey()));  // should be intercepted

            // cancel first notification
            cancelAndPoll(1);

            // now send a call with only Alice's contact Uri as the info
            // Note that this is a test of the repeat caller check, not matchesCallFilter itself
            sendNotification(2, null, R.drawable.blue, true, aliceUri);
            // wait for contact lookup, which may take a while
            Thread.sleep(3000);

            // now check that the second notification is not intercepted
            StatusBarNotification sbn2 = mNotificationHelper.findPostedNotification(null, 2,
                    SEARCH_TYPE.POSTED);
            assertTrue(mListener.mIntercepted.containsKey(sbn2.getKey()));
            assertFalse(mListener.mIntercepted.get(sbn2.getKey()));  // should not be intercepted

            // cancel second notification
            cancelAndPoll(2);
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }

            // clean up the recorded calls
            SystemUtil.runWithShellPermissionIdentity(() ->
                    mNotificationManager.cleanUpCallersAfter(startTime));
        }
    }

    @Test
    public void testMatchesCallFilter_allCallers() throws Exception {
        // allow all callers
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);
        int origFilter = mNotificationManager.getCurrentInterruptionFilter();
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        Uri aliceUri = null;  // for deletion after the test is done
        try {
            NotificationManager.Policy currPolicy = mNotificationManager.getNotificationPolicy();
            NotificationManager.Policy newPolicy = new NotificationManager.Policy(
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
                            | PRIORITY_CATEGORY_REPEAT_CALLERS,
                    NotificationManager.Policy.PRIORITY_SENDERS_ANY,
                    currPolicy.priorityMessageSenders,
                    currPolicy.suppressedVisualEffects);
            mNotificationManager.setNotificationPolicy(newPolicy);
            mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
            assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

            insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, false);
            aliceUri = lookupContact(ALICE_PHONE);

            Uri alicePhoneUri = makePhoneUri(ALICE_PHONE);
            assertTrue(mNotificationManager.matchesCallFilter(alicePhoneUri));
        } finally {
            mNotificationManager.setInterruptionFilter(origFilter);
            mNotificationManager.setNotificationPolicy(origPolicy);
            if (aliceUri != null) {
                // delete the contact
                deleteSingleContact(aliceUri);
            }
        }
    }

    @Test
    public void testInterruptionFilterNoneInterceptsMessages() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        sendNotifications(MODE_URI, false, false);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 1,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 2,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 3,
                SEARCH_TYPE.POSTED);

        assertTrue(mListener.mIntercepted.get(alice.getKey()));
        assertTrue(mListener.mIntercepted.get(bob.getKey()));
        assertTrue(mListener.mIntercepted.get(charlie.getKey()));
    }

    @Test
    public void testInterruptionFilterNoneInterceptsEventAlarmReminder() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
        sendEventAlarmReminderNotifications(SEND_ALL);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 4,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 5,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 6,
                SEARCH_TYPE.POSTED);

        assertTrue(mListener.mIntercepted.get(alice.getKey()));
        assertTrue(mListener.mIntercepted.get(bob.getKey()));
        assertTrue(mListener.mIntercepted.get(charlie.getKey()));
    }

    @Test
    public void testInterruptionFilterAllInterceptsNothing() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        sendNotifications(MODE_URI, false, false);
        sendEventAlarmReminderNotifications(SEND_ALL);
        sendAlarmOtherMediaNotifications(SEND_ALL);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 1,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 2,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 3,
                SEARCH_TYPE.POSTED);

        StatusBarNotification aliceEvent = mNotificationHelper.findPostedNotification(ALICE, 4,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bobAlarm = mNotificationHelper.findPostedNotification(BOB, 5,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlieReminder = mNotificationHelper.findPostedNotification(CHARLIE, 6,
                SEARCH_TYPE.POSTED);

        StatusBarNotification aliceAlarm = mNotificationHelper.findPostedNotification(ALICE, 7,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bobOther = mNotificationHelper.findPostedNotification(BOB, 8,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlieMedia = mNotificationHelper.findPostedNotification(CHARLIE, 9,
                SEARCH_TYPE.POSTED);

        assertFalse(mListener.mIntercepted.get(alice.getKey()));
        assertFalse(mListener.mIntercepted.get(bob.getKey()));
        assertFalse(mListener.mIntercepted.get(charlie.getKey()));
        assertFalse(mListener.mIntercepted.get(aliceEvent.getKey()));
        assertFalse(mListener.mIntercepted.get(bobAlarm.getKey()));
        assertFalse(mListener.mIntercepted.get(charlieReminder.getKey()));
        assertFalse(mListener.mIntercepted.get(aliceAlarm.getKey()));
        assertFalse(mListener.mIntercepted.get(bobOther.getKey()));
        assertFalse(mListener.mIntercepted.get(charlieMedia.getKey()));
    }

    @Test
    public void testPriorityStarredMessages() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        assertTrue(isStarred(lookupContact(ALICE_PHONE)));
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
        NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
        policy = new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
                policy.priorityCallSenders,
                PRIORITY_SENDERS_STARRED);
        mNotificationManager.setNotificationPolicy(policy);
        sendNotifications(MODE_URI, false, false);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 1,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 2,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 3,
                SEARCH_TYPE.POSTED);

        // wait for contacts lookup in NMS to finish so we have the correct interception info
        boolean isAliceIntercepted = true;
        for (int i = 0; i < 6; i++) {
            isAliceIntercepted = mListener.mIntercepted.get(alice.getKey());
            if (!isAliceIntercepted) {
                break;
            }
            sleep();
        }
        assertFalse(isAliceIntercepted);
        assertTrue(mListener.mIntercepted.get(bob.getKey()));
        assertTrue(mListener.mIntercepted.get(charlie.getKey()));
    }

    @Test
    public void testPriorityInterceptsAlarms() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
        NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
        policy = new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                policy.priorityCallSenders,
                policy.priorityMessageSenders);
        mNotificationManager.setNotificationPolicy(policy);
        sendEventAlarmReminderNotifications(SEND_ALL);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 4,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 5,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 6,
                SEARCH_TYPE.POSTED);

        boolean isBobIntercepted = true;
        for (int i = 0; i < 6; i++) {
            isBobIntercepted = mListener.mIntercepted.get(bob.getKey());
            if (!isBobIntercepted) {
                break;
            }
            sleep();
        }
        assertFalse(isBobIntercepted);

        assertTrue(mListener.mIntercepted.get(alice.getKey()));
        assertTrue(mListener.mIntercepted.get(charlie.getKey()));
    }

    @Test
    public void testPriorityInterceptsMediaSystemOther() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);
        NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
        policy = new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA,
                policy.priorityCallSenders,
                policy.priorityMessageSenders);
        mNotificationManager.setNotificationPolicy(policy);
        sendAlarmOtherMediaNotifications(SEND_ALL);

        StatusBarNotification alice = mNotificationHelper.findPostedNotification(ALICE, 7,
                SEARCH_TYPE.POSTED);
        StatusBarNotification bob = mNotificationHelper.findPostedNotification(BOB, 8,
                SEARCH_TYPE.POSTED);
        StatusBarNotification charlie = mNotificationHelper.findPostedNotification(CHARLIE, 9,
                SEARCH_TYPE.POSTED);

        boolean isBobIntercepted = true;
        for (int i = 0; i < 6; i++) {
            isBobIntercepted = mListener.mIntercepted.get(bob.getKey());
            if (!isBobIntercepted) {
                break;
            }
            sleep();
        }
        assertFalse(isBobIntercepted);

        boolean isCharlieIntercepted = true;
        for (int i = 0; i < 6; i++) {
            isCharlieIntercepted = mListener.mIntercepted.get(charlie.getKey());
            if (!isCharlieIntercepted) {
                break;
            }
            sleep();
        }
        assertFalse(isCharlieIntercepted);

        assertTrue(mListener.mIntercepted.get(alice.getKey()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void testPriorityChannelNotInterceptedByDefault() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // Setup: no contacts, so nobody counts as "priority" in terms of senders.
        // Construct a policy that doesn't allow anything except priority channels through;
        // apply it via zen rule
        AutomaticZenRule rule = createRule("test_channel_bypass",
                INTERRUPTION_FILTER_PRIORITY);
        rule.setZenPolicy(new ZenPolicy.Builder().disallowAllSounds().build());
        String id = mNotificationManager.addAutomaticZenRule(rule);

        // enable rule
        Condition condition = new Condition(rule.getConditionId(), "summary",
                Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

        // Create a notification under the PRIORITY (set to bypass DND) channel and send.
        Notification.Builder notif = new Notification.Builder(mContext,
                NOTIFICATION_CHANNEL_ID_PRIORITY)
                .setContentTitle(NAME)
                .setContentText("content")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setWhen(System.currentTimeMillis() - 1000000L);
        mNotificationManager.notify(NAME, 1, notif.build());

        StatusBarNotification sbn = mNotificationHelper.findPostedNotification(NAME, 1,
                SEARCH_TYPE.POSTED);

        // should not be intercepted because priority channels are allowed.
        assertFalse(mListener.mIntercepted.get(sbn.getKey()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void testPriorityChannelInterceptedWhenChannelsDisallowed() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // Setup: no contacts, so nobody counts as "priority" in terms of senders, not that the
        // policy allows senders anyway.
        // Construct a policy that doesn't allow anything through and also disallows channels.
        AutomaticZenRule rule = createRule("test_channels_disallowed",
                INTERRUPTION_FILTER_PRIORITY);
        rule.setZenPolicy(new ZenPolicy.Builder()
                .disallowAllSounds()
                .allowPriorityChannels(false)
                .build());
        String id = mNotificationManager.addAutomaticZenRule(rule);

        // enable rule
        Condition condition = new Condition(rule.getConditionId(), "summary",
                Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        Thread.sleep(300); // wait for rules to be applied - it's done asynchronously

        // Send notification under the priority channel.
        Notification.Builder notif = new Notification.Builder(mContext,
                NOTIFICATION_CHANNEL_ID_PRIORITY)
                .setContentTitle(NAME)
                .setContentText("content")
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setWhen(System.currentTimeMillis() - 1000000L);
        mNotificationManager.notify(NAME, 1, notif.build());

        StatusBarNotification sbn = mNotificationHelper.findPostedNotification(NAME, 1,
                SEARCH_TYPE.POSTED);

        // Notification should be intercepted now
        assertTrue(mListener.mIntercepted.get(sbn.getKey()));
    }

    @CddTest(requirements = {"2.2.3/3.8.4/H-1-1"})
    @Test
    public void testContactAffinityByPhoneOrder() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        sendNotifications(MODE_PHONE, false, false);

        int rankA= 0, rankB = 0, rankC = 0;
        for (int i = 0; i < 6; i++) {
            List<String> orderedKeys = new ArrayList<>(
                    Arrays.asList(mListener.mRankingMap.getOrderedKeys()));
            rankA = findTagInKeys(ALICE, orderedKeys);
            rankB = findTagInKeys(BOB, orderedKeys);
            rankC = findTagInKeys(CHARLIE, orderedKeys);
            // ordered by contact affinity: A, B, C
            if (rankA < rankB && rankB < rankC) {
                // yay
                break;
            }
            sleep();
        }
        // ordered by contact affinity: A, B, C
        if (rankA < rankB && rankB < rankC) {
            // yay
        } else {
            fail("Notifications out of order. Actual order: Alice: " + rankA + " Bob: " + rankB
                    + " Charlie: " + rankC);
        }
    }

    @CddTest(requirements = {"2.2.3/3.8.4/H-1-1"})
    @Test
    public void testContactUriByUriOrder() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        sendNotifications(MODE_URI, false, false);

        int rankA= 0, rankB = 0, rankC = 0;
        for (int i = 0; i < 6; i++) {
            List<String> orderedKeys = new ArrayList<>(
                    Arrays.asList(mListener.mRankingMap.getOrderedKeys()));
            rankA = findTagInKeys(ALICE, orderedKeys);
            rankB = findTagInKeys(BOB, orderedKeys);
            rankC = findTagInKeys(CHARLIE, orderedKeys);
            // ordered by contact affinity: A, B, C
            if (rankA < rankB && rankB < rankC) {
                // yay
                break;
            }
            sleep();
        }
        // ordered by contact affinity: A, B, C
        if (rankA < rankB && rankB < rankC) {
            // yay
        } else {
            fail("Notifications out of order. Actual order: Alice: " + rankA + " Bob: " + rankB
                    + " Charlie: " + rankC);
        }
    }

    @CddTest(requirements = {"2.2.3/3.8.4/H-1-1"})
    @Test
    public void testContactUriByEmailOrder() throws Exception {
        insertSingleContact(ALICE, ALICE_PHONE, ALICE_EMAIL, true);
        insertSingleContact(BOB, BOB_PHONE, BOB_EMAIL, false);
        // Not Charlie

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL);
        sendNotifications(MODE_EMAIL, false, false);

        int rankA= 0, rankB = 0, rankC = 0;
        for (int i = 0; i < 6; i++) {
            List<String> orderedKeys = new ArrayList<>(
                    Arrays.asList(mListener.mRankingMap.getOrderedKeys()));
            rankA = findTagInKeys(ALICE, orderedKeys);
            rankB = findTagInKeys(BOB, orderedKeys);
            rankC = findTagInKeys(CHARLIE, orderedKeys);
            // ordered by contact affinity: A, B, C
            if (rankA < rankB && rankB < rankC) {
                // yay
                break;
            }
            sleep();
        }
        // ordered by contact affinity: A, B, C
        if (rankA < rankB && rankB < rankC) {
            // yay
        } else {
            fail("Notifications out of order. Actual order: Alice: " + rankA + " Bob: " + rankB
                    + " Charlie: " + rankC);
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void testAddAutomaticZenRule_includesModesApiFields() throws Exception {
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = new AutomaticZenRule.Builder(NAME, CONDITION_ID)
                .setZenPolicy(POLICY)
                .setManualInvocationAllowed(ALLOW_MANUAL)
                .setOwner(null)
                .setType(UNRESTRICTED_TYPE)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setTriggerDescription(TRIGGER_DESC)
                .setIconResId(ICON_RES_ID)
                .build();
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        assertNotNull(id);
        assertRulesEqual(ruleToCreate, mNotificationManager.getAutomaticZenRule(id));
    }

    @Test
    public void testSnoozeRule() throws Exception {
        if (!Flags.modesApi() || !CompatChanges.isChangeEnabled(308673617)) {
            Log.d(TAG, "Skipping testSnoozeRule() "
                    + Flags.modesApi() + " " + Build.VERSION.SDK_INT);
            return;
        }
        NotificationManagerBroadcastReceiver br = new NotificationManagerBroadcastReceiver();
        br.register(mContext, ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED, 2);

        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        AutomaticZenRule ruleToCreate = createRule("testSnoozeRule");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        // snooze the rule
        runAsSystemUi(() -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL));

        assertEquals(AUTOMATIC_RULE_STATUS_ACTIVATED,
                br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 0, 500));
        assertEquals(AUTOMATIC_RULE_STATUS_DEACTIVATED,
                br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 1, 500));
        br.unregister();
    }

    @Test
    public void testUnsnoozeRule_disableEnable() throws Exception {
        if (!Flags.modesApi()) {
            Log.d(TAG, "Skipping testUnsnoozeRule_disableEnable() " + Flags.modesApi()
                    + " " + Build.VERSION.SDK_INT);
            return;
        }
        NotificationManagerBroadcastReceiver br = new NotificationManagerBroadcastReceiver();
        br.register(mContext, ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED, 3);

        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // No broadcast expected on creation
        AutomaticZenRule ruleToCreate = createRule("testUnsnoozeRule_disableEnable");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);

        // enable DND
        Condition condition =
                new Condition(ruleToCreate.getConditionId(), "summary", Condition.STATE_TRUE);
        // triggers ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED: Enabled
        mNotificationManager.setAutomaticZenRuleState(id, condition);

        // snooze the rule by pretending the user turned off the mode from SystemUI
        // triggers ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED: Deactivated
        runAsSystemUi(() -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL));
        assertExpectedDndState(INTERRUPTION_FILTER_ALL);

        // disable the rule. should unsnooze.
        ruleToCreate.setEnabled(false);
        mNotificationManager.updateAutomaticZenRule(id, ruleToCreate);

        assertEquals(AUTOMATIC_RULE_STATUS_DISABLED,
                br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 2, 500));
        br.unregister();

        br = new NotificationManagerBroadcastReceiver();
        br.register(mContext, ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED, 2);

        // re-enable and activate
        ruleToCreate.setEnabled(true);
        mNotificationManager.updateAutomaticZenRule(id, ruleToCreate);
        mNotificationManager.setAutomaticZenRuleState(id, condition);
        assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);

        assertEquals(AUTOMATIC_RULE_STATUS_ENABLED,
                br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 0, 500));
        if (CompatChanges.isChangeEnabled(308673617)) {
            assertEquals(AUTOMATIC_RULE_STATUS_ACTIVATED,
                    br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 1, 500));
        } else {
            assertEquals(AUTOMATIC_RULE_STATUS_UNKNOWN,
                    br.getExtra(EXTRA_AUTOMATIC_ZEN_RULE_STATUS, 1, 500));
        }
        br.unregister();
    }

    @Test
    public void testGetAutomaticZenRules() {
        assertThat(mNotificationManager.getAutomaticZenRules()).isEmpty();

        AutomaticZenRule rule1 = createRule("One");
        AutomaticZenRule rule2 = createRule("Two");
        if (Flags.modesApi()) {
            rule1 = new AutomaticZenRule.Builder(rule1)
                    .setType(AutomaticZenRule.TYPE_DRIVING)
                    .setManualInvocationAllowed(true)
                    .build();
            rule2 = new AutomaticZenRule.Builder(rule2)
                    .setTriggerDescription("On the twelfth day of Christmas")
                    .setIconResId(R.drawable.icon_green)
                    .build();
        }
        String ruleId1 = mNotificationManager.addAutomaticZenRule(rule1);
        String ruleId2 = mNotificationManager.addAutomaticZenRule(rule2);

        Map<String, AutomaticZenRule> rules = mNotificationManager.getAutomaticZenRules();

        assertThat(rules).hasSize(2);
        assertRulesEqual(rules.get(ruleId1), rule1);
        assertRulesEqual(rules.get(ruleId2), rule2);
    }

    private void assertRulesEqual(AutomaticZenRule r1, AutomaticZenRule r2) {
        // Cannot test for exact equality because some extra fields (e.g. packageName,
        // creationTime) come back. So we verify everything that the client app can set.
        assertThat(r1.getConditionId()).isEqualTo(r2.getConditionId());
        assertThat(r1.getConfigurationActivity()).isEqualTo(r2.getConfigurationActivity());
        assertThat(r1.getInterruptionFilter()).isEqualTo(r2.getInterruptionFilter());
        assertThat(r1.getName()).isEqualTo(r2.getName());
        assertThat(r1.getOwner()).isEqualTo(r2.getOwner());
        assertThat(r1.isEnabled()).isEqualTo(r2.isEnabled());

        if (Flags.modesApi()) {
            assertThat(r1.getDeviceEffects()).isEqualTo(r2.getDeviceEffects());
            assertThat(doPoliciesMatchWithDefaults(r1.getZenPolicy(), r2.getZenPolicy())).isTrue();

            assertThat(r1.getIconResId()).isEqualTo(r2.getIconResId());
            assertThat(r1.getTriggerDescription()).isEqualTo(r2.getTriggerDescription());
            assertThat(r1.getType()).isEqualTo(r2.getType());
            assertThat(r1.isManualInvocationAllowed()).isEqualTo(r2.isManualInvocationAllowed());
        } else {
            assertThat(r1.getZenPolicy()).isEqualTo(r2.getZenPolicy());
        }
    }

    // Checks that the priority categories in the provided NotificationManager.Policy match
    // those of the provided ZenPolicy. Does not check call/message/conversation senders or
    // visual effects.
    private void assertPolicyCategoriesMatchZenPolicy(
            NotificationManager.Policy nmPolicy, ZenPolicy zenPolicy) {
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryAlarms() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_CALLS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryCalls() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_CONVERSATIONS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryConversations() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_EVENTS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryEvents() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_MEDIA) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryMedia() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_MESSAGES) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryMessages() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_REMINDERS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryReminders() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_REPEAT_CALLERS) != 0)
                .isEqualTo(zenPolicy.getPriorityCategoryRepeatCallers() == ZenPolicy.STATE_ALLOW);
        assertThat((nmPolicy.priorityCategories & PRIORITY_CATEGORY_SYSTEM) != 0)
                .isEqualTo(zenPolicy.getPriorityCategorySystem() == ZenPolicy.STATE_ALLOW);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_withInterruptionFilterAll_canBeUsed()
            throws InterruptedException {
        AutomaticZenRule rule = createRule("Without filter", INTERRUPTION_FILTER_ALL);
        rule.setDeviceEffects(
                new ZenDeviceEffects.Builder().setShouldDisplayGrayscale(true).build());

        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        AutomaticZenRule savedRule = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedRule).isNotNull();
        assertThat(savedRule.getInterruptionFilter()).isEqualTo(INTERRUPTION_FILTER_ALL);

        // Simple update, just to verify no validation errors.
        savedRule.setName("Still without filter");
        mNotificationManager.updateAutomaticZenRule(ruleId, savedRule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "summary", Condition.STATE_TRUE));
        assertThat(mNotificationManager.getCurrentInterruptionFilter()).isEqualTo(
                INTERRUPTION_FILTER_ALL);
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isColorDisplayManagerSaturationActivated()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "summary", Condition.STATE_FALSE));
        assertThat(mNotificationManager.getCurrentInterruptionFilter()).isEqualTo(
                INTERRUPTION_FILTER_ALL);
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isColorDisplayManagerSaturationActivated()).isFalse();

        mNotificationManager.removeAutomaticZenRule(ruleId);
        assertThat(mNotificationManager.getAutomaticZenRules()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_fromUser_onlyAcceptedFromSystem() {
        AutomaticZenRule newRule = createRule("Test");

        String rule1 = mNotificationManager.addAutomaticZenRule(newRule, /* fromUser= */ false);

        assertThrows(SecurityException.class,
                () -> mNotificationManager.addAutomaticZenRule(newRule, /* fromUser= */ true));

        String rule2 = callAsSystemUi(
                () -> mNotificationManager.addAutomaticZenRule(newRule, /* fromUser= */ true));

        assertThat(mNotificationManager.getAutomaticZenRule(rule1)).isNotNull();
        assertThat(mNotificationManager.getAutomaticZenRule(rule2)).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void updateAutomaticZenRule_fromUser_updatesRuleFully() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setIconResId(android.app.notification.current.cts.R.drawable.ic_android)
                .setTriggerDescription("Immerse yourself")
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowRepeatCallers(false)
                        .allowAlarms(false)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder()
                        .setShouldDisplayGrayscale(true)
                        .build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);
        ZenPolicy combinedPolicy = original.getZenPolicy();

        // Update the rule "from user" once.
        // Set settings for events & calls that do not match the default so we're certain these
        // changes will reflect that the "user" actually changed the fields.
        AutomaticZenRule firstUserUpdate = new AutomaticZenRule.Builder(original)
                .setName("First update")
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowEvents(
                                mDefaultPolicy.getPriorityCategoryEvents() != ZenPolicy.STATE_ALLOW)
                        .allowCalls(mDefaultPolicy.getPriorityCallSenders()
                                == ZenPolicy.PEOPLE_TYPE_ANYONE
                                        ? ZenPolicy.PEOPLE_TYPE_CONTACTS
                                        : ZenPolicy.PEOPLE_TYPE_ANYONE)
                        .build())
                .build();
        runAsSystemUi(
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, firstUserUpdate,
                        /* fromUser= */ true));

        // Verify the update succeeded.
        combinedPolicy = combinedPolicy.overwrittenWith(firstUserUpdate.getZenPolicy());
        AutomaticZenRule firstUserUpdateResult = mNotificationManager.getAutomaticZenRule(ruleId);
        AutomaticZenRule expectedRuleAfterFirstUpdate =
                new AutomaticZenRule.Builder(firstUserUpdate)
                        .setZenPolicy(combinedPolicy)
                        .build();
        assertRulesEqual(expectedRuleAfterFirstUpdate, firstUserUpdateResult);

        // Update the rule "from user" a second time.
        AutomaticZenRule secondUserUpdate = new AutomaticZenRule.Builder(original)
                // User changes
                .setName("Updated again")
                .setEnabled(false)
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowMedia(true)
                        .allowCalls(ZenPolicy.PEOPLE_TYPE_CONTACTS)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder(original.getDeviceEffects())
                        .setShouldDimWallpaper(true)
                        .build())
                // Technically nothing stops this API call from also updating fields that should be
                // the purview of the app.
                .setType(AutomaticZenRule.TYPE_DRIVING)
                .setTriggerDescription("While driving")
                .setIconResId(android.R.drawable.sym_def_app_icon)
                .build();
        runAsSystemUi(
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, secondUserUpdate,
                        /* fromUser= */ true));

        // The second update succeeded as well.
        combinedPolicy = combinedPolicy.overwrittenWith(secondUserUpdate.getZenPolicy());
        AutomaticZenRule secondUserUpdateResult = mNotificationManager.getAutomaticZenRule(ruleId);
        AutomaticZenRule expectedRuleAfterSecondUpdate =
                new AutomaticZenRule.Builder(secondUserUpdate)
                        .setZenPolicy(combinedPolicy)
                        .build();
        assertRulesEqual(expectedRuleAfterSecondUpdate, secondUserUpdateResult);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void updateAutomaticZenRule_fromApp_forNonUserModifiedRule_allFieldsUpdated() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setIconResId(android.app.notification.current.cts.R.drawable.ic_android)
                .setTriggerDescription("Immerse yourself")
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowRepeatCallers(false)
                        .allowAlarms(false)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder()
                        .setShouldDisplayGrayscale(true)
                        .build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        // Update the rule "from app".
        AutomaticZenRule appUpdate = new AutomaticZenRule.Builder(original)
                .setName("Updated")
                .setType(AutomaticZenRule.TYPE_DRIVING)
                .setTriggerDescription("While driving")
                .setIconResId(android.R.drawable.sym_def_app_icon)
                .setEnabled(false)
                .setZenPolicy(new ZenPolicy.Builder(original.getZenPolicy())
                        .allowMedia(true)
                        .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder(original.getDeviceEffects())
                        .setShouldDimWallpaper(true)
                        .build())
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, appUpdate);

        // Verify the update succeeded.
        AutomaticZenRule result = mNotificationManager.getAutomaticZenRule(ruleId);
        assertRulesEqual(appUpdate, result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void updateAutomaticZenRule_fromApp_forUserModifiedRule_onlySomeFieldsUpdated() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setIconResId(android.app.notification.current.cts.R.drawable.ic_android)
                .setTriggerDescription("Immerse yourself")
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowRepeatCallers(false)
                        .allowAlarms(false)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder()
                        .setShouldDisplayGrayscale(true)
                        .build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        // Minimally update the rule "from user".
        AutomaticZenRule userUpdate = new AutomaticZenRule.Builder(original)
                // User changes
                .setName("Updated by user")
                .build();
        runAsSystemUi(
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, userUpdate,
                        /* fromUser= */ true));

        // Now try to update again "from app".
        AutomaticZenRule appUpdate = new AutomaticZenRule.Builder(original)
                .setName("Updated")
                .setType(AutomaticZenRule.TYPE_DRIVING)
                .setTriggerDescription("While driving")
                .setIconResId(android.R.drawable.sym_def_app_icon)
                .setEnabled(false)
                .setZenPolicy(new ZenPolicy.Builder(original.getZenPolicy())
                        .allowAlarms(true)
                        .allowCalls(ZenPolicy.PEOPLE_TYPE_ANYONE)
                        .build())
                .setDeviceEffects(new ZenDeviceEffects.Builder(original.getDeviceEffects())
                        .setShouldDimWallpaper(true)
                        .build())
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, appUpdate);

        // The app-controlled fields should be updated.
        AutomaticZenRule result = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(result.getType()).isEqualTo(appUpdate.getType());
        assertThat(result.getTriggerDescription()).isEqualTo(appUpdate.getTriggerDescription());
        assertThat(result.getIconResId()).isEqualTo(appUpdate.getIconResId());
        assertThat(result.isEnabled()).isEqualTo(appUpdate.isEnabled());

        // ... but nothing else should (even though those fields were not _specifically_ modified by
        // the user).
        assertThat(result.getName()).isEqualTo(userUpdate.getName());
        assertThat(doPoliciesMatchWithDefaults(result.getZenPolicy(), original.getZenPolicy()))
                .isTrue();
        assertThat(result.getDeviceEffects()).isEqualTo(original.getDeviceEffects());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_forDeletedAndPreviouslyUserModifiedRule_restoresRule() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        // Rename it "from user".
        AutomaticZenRule userUpdate = new AutomaticZenRule.Builder(original)
                // User changes
                .setName("Updated by user")
                .build();
        runAsSystemUi(
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, userUpdate,
                        /* fromUser= */ true));

        // Now delete it "from app".
        mNotificationManager.removeAutomaticZenRule(ruleId);
        assertThat(mNotificationManager.getAutomaticZenRule(ruleId)).isNull();

        // Now create it "from app" again, with a new name.
        AutomaticZenRule reAddRule = new AutomaticZenRule.Builder(original)
                .setName("Here we go again")
                .build();
        String newRuleId = mNotificationManager.addAutomaticZenRule(reAddRule);

        // The rule was added, but the user's customization was restored.
        AutomaticZenRule finalRule = mNotificationManager.getAutomaticZenRule(newRuleId);
        assertThat(finalRule).isNotNull();
        assertThat(finalRule.getName()).isEqualTo("Updated by user");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_withDeviceEffects_stored() {
        ZenDeviceEffects effects = new ZenDeviceEffects.Builder()
                .setShouldDisplayGrayscale(true)
                .setShouldUseNightMode(true)
                .build();

        AutomaticZenRule newRule = createRule("With effects");
        newRule.setDeviceEffects(effects);

        String ruleId = mNotificationManager.addAutomaticZenRule(newRule);
        AutomaticZenRule readRule = mNotificationManager.getAutomaticZenRule(ruleId);

        assertThat(readRule.getDeviceEffects()).isEqualTo(effects);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_withDeviceExtraEffects_storedFromSystem() {
        ZenDeviceEffects effects = new ZenDeviceEffects.Builder()
                .setShouldDisplayGrayscale(true)
                .setShouldUseNightMode(true)
                .setExtraEffects(ImmutableSet.of("TIME_TRAVEL", "DINOSAUR_CLONING"))
                .build();
        AutomaticZenRule newRule = createRule("With effects");
        newRule.setDeviceEffects(effects);

        String ruleId = callAsSystemUi(() -> mNotificationManager.addAutomaticZenRule(newRule));

        AutomaticZenRule readRule = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(readRule.getDeviceEffects()).isEqualTo(effects);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void addAutomaticZenRule_withUnderspecifiedPolicies_filledIn() {
        AutomaticZenRule noPolicy = new AutomaticZenRule.Builder("no policy", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setIconResId(android.app.notification.current.cts.R.drawable.ic_android)
                .setTriggerDescription("whatever")
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(null)  // not really necessary, but doing so explicitly anyway.
                .build();
        String noPolicyRuleId = mNotificationManager.addAutomaticZenRule(noPolicy);

        // The resulting actual policy on the rule should be equivalent to the default, with all
        // fields fully filled in (rather than being left as null).
        AutomaticZenRule readRule = mNotificationManager.getAutomaticZenRule(noPolicyRuleId);
        assertThat(readRule.getZenPolicy()).isEqualTo(mDefaultPolicy);

        AutomaticZenRule underspecified = new AutomaticZenRule.Builder("some policy", CONDITION_ID)
                .setConfigurationActivity(CONFIG_ACTIVITY)
                .setType(AutomaticZenRule.TYPE_IMMERSIVE)
                .setIconResId(android.app.notification.current.cts.R.drawable.ic_android)
                .setTriggerDescription("whatever")
                .setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(new ZenPolicy.Builder()
                        .allowAlarms(true)
                        .allowMedia(false)
                        .build())
                .build();
        String underspecRuleId = mNotificationManager.addAutomaticZenRule(underspecified);

        AutomaticZenRule readRule2 = mNotificationManager.getAutomaticZenRule(underspecRuleId);
        assertThat(readRule2.getZenPolicy()).isEqualTo(
                mDefaultPolicy.overwrittenWith(underspecified.getZenPolicy()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void getAutomaticZenRuleState_returnsRuleState() {
        AutomaticZenRule rule = createRule("Test");

        String ruleId = mNotificationManager.addAutomaticZenRule(rule);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(
                Condition.STATE_FALSE);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "", Condition.STATE_TRUE));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(
                Condition.STATE_TRUE);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "", Condition.STATE_FALSE));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(
                Condition.STATE_FALSE);

        mNotificationManager.removeAutomaticZenRule(ruleId);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(
                Condition.STATE_UNKNOWN);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_ruleWithGrayscale_applied() throws Exception {
        assertThat(isColorDisplayManagerSaturationActivated()).isFalse();

        AutomaticZenRule rule = createRule("Grayscale");
        rule.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldDisplayGrayscale(true)
                .build());
        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "yeah", Condition.STATE_TRUE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isColorDisplayManagerSaturationActivated()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "nope", Condition.STATE_FALSE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isColorDisplayManagerSaturationActivated()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_ruleWithDimWallpaper_applied() throws Exception {
        assumeTrue(mContext.getSystemService(WallpaperManager.class).isWallpaperSupported());
        assertThat(getWallpaperManagerDimAmount()).isZero();

        AutomaticZenRule rule = createRule("Dim wallpaper");
        rule.setDeviceEffects(new ZenDeviceEffects.Builder().setShouldDimWallpaper(true).build());
        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "yeah", Condition.STATE_TRUE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(getWallpaperManagerDimAmount()).isNonZero();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "nope", Condition.STATE_FALSE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(getWallpaperManagerDimAmount()).isZero();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_ruleWithDisableAmbientDisplay_applied() throws Exception {
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isFalse();

        AutomaticZenRule rule = createRule("Disable ambient display");
        rule.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldSuppressAmbientDisplay(true)
                .build());
        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "yeah", Condition.STATE_TRUE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "nope", Condition.STATE_FALSE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_ruleWithNightMode_appliedImmediately() throws Exception {
        assertThat(isUiModeManagerThemeOverlayActive()).isFalse();

        AutomaticZenRule rule = createRule("Grayscale");
        rule.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldUseNightMode(true)
                .build());
        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "yeah", Condition.STATE_TRUE,
                        Condition.SOURCE_USER_ACTION));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isUiModeManagerThemeOverlayActive()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "nope", Condition.STATE_FALSE,
                        Condition.SOURCE_USER_ACTION));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isUiModeManagerThemeOverlayActive()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_ruleWithNightMode_appliedOnScreenOff() throws Exception {
        assertThat(isUiModeManagerThemeOverlayActive()).isFalse();

        AutomaticZenRule rule = createRule("Grayscale");
        rule.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldUseNightMode(true)
                .build());
        String ruleId = mNotificationManager.addAutomaticZenRule(rule);

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "yeah", Condition.STATE_TRUE,
                        Condition.SOURCE_SCHEDULE));
        Thread.sleep(300); // Effects are applied asynchronously.

        assertThat(isUiModeManagerThemeOverlayActive()).isFalse(); // Not yet applied.

        // Have you tried turning it off and on again?
        turnScreenOffAndOn();
        assertThat(isUiModeManagerThemeOverlayActive()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(ruleId,
                new Condition(rule.getConditionId(), "nope", Condition.STATE_FALSE,
                        Condition.SOURCE_SCHEDULE));
        Thread.sleep(300); // Effects are applied asynchronously.

        assertThat(isUiModeManagerThemeOverlayActive()).isTrue(); // Not yet applied.

        turnScreenOffAndOn();
        assertThat(isUiModeManagerThemeOverlayActive()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_MODES_API)
    public void setAutomaticZenRuleState_multipleRulesWithDeviceEffects_effectsMerged()
            throws Exception {
        AutomaticZenRule withDisableAmbientDisplay = createRule("Disable ambient display");
        withDisableAmbientDisplay.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldSuppressAmbientDisplay(true)
                .build());
        String withDisableAmbientDisplayId = mNotificationManager.addAutomaticZenRule(
                withDisableAmbientDisplay);

        AutomaticZenRule withGrayscale = createRule("With grayscale");
        withGrayscale.setDeviceEffects(new ZenDeviceEffects.Builder()
                .setShouldDisplayGrayscale(true)
                .build());
        String withGrayscaleId = mNotificationManager.addAutomaticZenRule(withGrayscale);

        mNotificationManager.setAutomaticZenRuleState(withDisableAmbientDisplayId,
                new Condition(withDisableAmbientDisplay.getConditionId(), "ad",
                        Condition.STATE_TRUE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isTrue();
        assertThat(isColorDisplayManagerSaturationActivated()).isFalse();

        mNotificationManager.setAutomaticZenRuleState(withGrayscaleId,
                new Condition(withGrayscale.getConditionId(), "gs", Condition.STATE_TRUE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isTrue();
        assertThat(isColorDisplayManagerSaturationActivated()).isTrue();

        mNotificationManager.setAutomaticZenRuleState(withDisableAmbientDisplayId,
                new Condition(withDisableAmbientDisplay.getConditionId(), "ad",
                        Condition.STATE_FALSE));
        Thread.sleep(300); // Effects are applied asynchronously.
        assertThat(isPowerManagerAmbientDisplaySuppressed()).isFalse();
        assertThat(isColorDisplayManagerSaturationActivated()).isTrue();
    }

    private float getWallpaperManagerDimAmount() {
        WallpaperManager wallpaperManager = checkNotNull(
                mContext.getSystemService(WallpaperManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> wallpaperManager.getWallpaperDimAmount(),
                Manifest.permission.SET_WALLPAPER_DIM_AMOUNT);
    }

    private boolean isPowerManagerAmbientDisplaySuppressed() {
        PowerManager powerManager = checkNotNull(mContext.getSystemService(PowerManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> powerManager.isAmbientDisplaySuppressed(),
                Manifest.permission.READ_DREAM_STATE);
    }

    private boolean isColorDisplayManagerSaturationActivated() {
        ColorDisplayManager colorDisplayManager = checkNotNull(
                mContext.getSystemService(ColorDisplayManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> colorDisplayManager.isSaturationActivated(),
                Manifest.permission.CONTROL_DISPLAY_COLOR_TRANSFORMS);
    }

    private boolean isUiModeManagerThemeOverlayActive() {
        UiModeManager uiModeManager = checkNotNull(mContext.getSystemService(UiModeManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> uiModeManager.getAttentionModeThemeOverlay()
                        == UiModeManager.MODE_ATTENTION_THEME_OVERLAY_NIGHT,
                Manifest.permission.MODIFY_DAY_NIGHT_MODE);
    }

    private void turnScreenOffAndOn() throws Exception {
        ScreenUtils.setScreenOn(false);
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);
    }

    @Test
    public void testSetInterruptionFilter_usesAutomaticZenRule() throws Exception {
        // NMS: MANAGE_GLOBAL_ZEN_VIA_IMPLICIT_RULES
        if (!android.app.Flags.modesApi() || !CompatChanges.isChangeEnabled(308670109L)) {
            return;
        }
        assertThat(mNotificationManager.getAutomaticZenRules()).isEmpty();

        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

        // The filter was applied, but through a rule.
        assertExpectedDndState(INTERRUPTION_FILTER_PRIORITY);
        Map<String, AutomaticZenRule> zenRules = mNotificationManager.getAutomaticZenRules();
        assertThat(zenRules).hasSize(1);
        AutomaticZenRule rule = Iterables.getOnlyElement(zenRules.values());
        assertThat(rule.getInterruptionFilter()).isEqualTo(INTERRUPTION_FILTER_PRIORITY);
    }

    @Test
    public void testSetNotificationPolicy_usesAutomaticZenRule() {
        // NMS: MANAGE_GLOBAL_ZEN_VIA_IMPLICIT_RULES
        if (!android.app.Flags.modesApi() || !CompatChanges.isChangeEnabled(308670109L)) {
            return;
        }
        assertThat(mNotificationManager.getAutomaticZenRules()).isEmpty();

        NotificationManager.Policy policy = new NotificationManager.Policy(
                PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_REPEAT_CALLERS,
                0, 0);
        mNotificationManager.setNotificationPolicy(policy);

        // The policy was mapped to a rule.
        Map<String, AutomaticZenRule> zenRules = mNotificationManager.getAutomaticZenRules();
        assertThat(zenRules).hasSize(1);
        ZenPolicy ruleZen = Iterables.getOnlyElement(zenRules.values()).getZenPolicy();
        assertThat(ruleZen).isNotNull();

        assertThat(ruleZen.getPriorityCategoryAlarms()).isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(ruleZen.getPriorityCategoryRepeatCallers()).isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(ruleZen.getPriorityCategoryCalls()).isEqualTo(ZenPolicy.STATE_DISALLOW);
        assertThat(ruleZen.getPriorityCategoryMedia()).isEqualTo(ZenPolicy.STATE_DISALLOW);
        assertThat(ruleZen.getPriorityCategorySystem()).isEqualTo(ZenPolicy.STATE_DISALLOW);
    }

    @Test
    public void testSetInterruptionFilter_withSetNotificationPolicy_sharesAutomaticZenRule() {
        // NMS: MANAGE_GLOBAL_ZEN_VIA_IMPLICIT_RULES
        if (!android.app.Flags.modesApi() || !CompatChanges.isChangeEnabled(308670109L)) {
            return;
        }
        assertThat(mNotificationManager.getAutomaticZenRules()).isEmpty();

        NotificationManager.Policy policy = new NotificationManager.Policy(
                PRIORITY_CATEGORY_ALARMS | PRIORITY_CATEGORY_REPEAT_CALLERS,
                0, 0);
        mNotificationManager.setNotificationPolicy(policy);
        mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY);

        // The policy and interruption filter were mapped to the same rule.
        Map<String, AutomaticZenRule> zenRules = mNotificationManager.getAutomaticZenRules();
        assertThat(zenRules).hasSize(1);
        AutomaticZenRule rule = Iterables.getOnlyElement(zenRules.values());
        assertThat(rule.getInterruptionFilter()).isEqualTo(INTERRUPTION_FILTER_PRIORITY);
        ZenPolicy ruleZen = rule.getZenPolicy();
        assertThat(ruleZen).isNotNull();
        assertThat(ruleZen.getPriorityCategoryAlarms()).isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(ruleZen.getPriorityCategoryCalls()).isEqualTo(ZenPolicy.STATE_DISALLOW);

        // The rule was turned on, and is working.
        assertThat(mNotificationManager.getCurrentInterruptionFilter()).isEqualTo(
                INTERRUPTION_FILTER_PRIORITY);
        NotificationManager.Policy activePolicy =
                mNotificationManager.getConsolidatedNotificationPolicy();
        assertThat(activePolicy.priorityCategories & PRIORITY_CATEGORY_ALARMS).isNotEqualTo(0);
        assertThat(activePolicy.priorityCategories & PRIORITY_CATEGORY_CALLS).isEqualTo(0);
    }

    @RequiresFlagsEnabled({Flags.FLAG_MODES_API, Flags.FLAG_MODES_UI})
    @Test
    public void testIndividualRuleIntent_resolvesToActivity() {
        AutomaticZenRule ruleToCreate = createRule("testIndividualRuleIntent_resolvesToActivity");
        String id = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        final PackageManager pm = mContext.getPackageManager();
        final Intent intent = new Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS);
        intent.putExtra(EXTRA_AUTOMATIC_ZEN_RULE_ID, id);
        final ResolveInfo resolveInfo = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY);
        assertNotNull(resolveInfo);
    }
}
