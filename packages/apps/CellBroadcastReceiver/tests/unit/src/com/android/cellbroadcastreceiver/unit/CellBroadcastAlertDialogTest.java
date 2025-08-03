/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cellbroadcastreceiver.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ContentProviderHolder;
import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.telephony.SmsCbMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Singleton;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.cellbroadcastreceiver.CellBroadcastAlertDialog;
import com.android.cellbroadcastreceiver.CellBroadcastAlertService;
import com.android.cellbroadcastreceiver.CellBroadcastChannelManager;
import com.android.cellbroadcastreceiver.CellBroadcastReceiverApp;
import com.android.cellbroadcastreceiver.CellBroadcastSettings;
import com.android.cellbroadcastreceiver.R;
import com.android.internal.telephony.gsm.SmsCbConstants;

import org.junit.After;
import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class CellBroadcastAlertDialogTest extends
        CellBroadcastActivityTestCase<CellBroadcastAlertDialog> {

    @Mock
    private NotificationManager mMockedNotificationManager;

    @Mock
    private IPowerManager.Stub mMockedPowerManagerService;

    @Mock
    private IThermalService.Stub mMockedThermalService;

    @Mock
    private IActivityManager.Stub mMockedActivityManager;

    @Mock
    IWindowManager.Stub mWindowManagerService;

    @Mock
    LinearLayout mMockLinearLayout;

    @Captor
    private ArgumentCaptor<Integer> mFlags;

    @Captor
    private ArgumentCaptor<Integer> mInt;

    @Captor
    private ArgumentCaptor<Notification> mNotification;

    private PowerManager mPowerManager;
    private int mSubId = 0;

    public CellBroadcastAlertDialogTest() {
        super(CellBroadcastAlertDialog.class);
    }

    private int mServiceCategory = SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL;
    private int mCmasMessageClass = 0;

    private ArrayList<SmsCbMessage> mMessageList;

    MockedServiceManager mMockedActivityManagerHelper;

    @Override
    protected Intent createActivityIntent() {
        mMessageList = new ArrayList<>(1);
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12412,
                mServiceCategory,
                mCmasMessageClass));

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                mMessageList);
        return intent;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        injectSystemService(NotificationManager.class, mMockedNotificationManager);
        // PowerManager is a final class so we can't use Mockito to mock it, but we can mock
        // its underlying service.
        doReturn(true).when(mMockedPowerManagerService).isInteractive();
        mPowerManager = new PowerManager(mContext, mMockedPowerManagerService,
                mMockedThermalService, null);
        injectSystemService(PowerManager.class, mPowerManager);

        SubscriptionManager mockSubManager = mock(SubscriptionManager.class);
        injectSystemService(SubscriptionManager.class, mockSubManager);
        SubscriptionInfo mockSubInfo = mock(SubscriptionInfo.class);
        doReturn(mockSubInfo).when(mockSubManager).getActiveSubscriptionInfo(anyInt());

        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = "test";
        providerInfo.applicationInfo = new ApplicationInfo();
        providerInfo.applicationInfo.uid = 999;
        ContentProviderHolder holder = new ContentProviderHolder(providerInfo);
        doReturn(holder).when(mMockedActivityManager)
                .getContentProvider(any(), any(), any(), anyInt(), anyBoolean());
        holder.provider = mock(IContentProvider.class);

        Singleton<IActivityManager> activityManagerSingleton = new Singleton<IActivityManager>() {
            @Override
            protected IActivityManager create() {
                return mMockedActivityManager;
            }
        };

        mMockedActivityManagerHelper = new MockedServiceManager();
        mMockedActivityManagerHelper.replaceService("window", mWindowManagerService);
        mMockedActivityManagerHelper.replaceInstance(ActivityManager.class,
                "IActivityManagerSingleton", null, activityManagerSingleton);

        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
    }

    @After
    public void tearDown() throws Exception {
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        mMockedActivityManagerHelper.restoreAllServices();
        super.tearDown();
    }

    public void testTitleAndMessageText() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);

        startActivity();
        waitForMs(100);

        CharSequence alertString =
                getActivity().getResources().getText(com.android.cellbroadcastreceiver.R.string
                        .cmas_presidential_level_alert);
        assertTrue(getActivity().getTitle().toString().startsWith(alertString.toString()));
        assertTrue(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText().toString()
                .startsWith(alertString.toString()));

        waitUntilAssertPasses(()-> {
            String body = CellBroadcastAlertServiceTest.createMessage(34596).getMessageBody();
            assertEquals(body, ((TextView) getActivity().findViewById(
                            com.android.cellbroadcastreceiver.R.id.message)).getText().toString());
        }, 1000);

        stopActivity();
    }
    public void testNoTitle() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        startActivity();
        waitForMs(100);
        assertTrue(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));
        stopActivity();
    }

    public void waitUntilAssertPasses(Runnable r, long maxWaitMs) {
        long waitTime = 0;
        while (waitTime < maxWaitMs) {
            try {
                r.run();
                // if the assert succeeds, return
                return;
            } catch (Exception e) {
                waitTime += 100;
                waitForMs(100);
            }
        }
        // if timed out, run one last time without catching exception
        r.run();
    }

    public void testAddToNotification() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);

        startActivity();
        waitForMs(100);
        leaveActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(1)).notify(mInt.capture(),
                mNotification.capture());
        Bundle b = mNotification.getValue().extras;

        assertEquals(1, (int) mInt.getValue());

        assertTrue(getActivity().getTitle().toString().startsWith(
                b.getCharSequence(Notification.EXTRA_TITLE).toString()));
        assertEquals(CellBroadcastAlertServiceTest.createMessage(98235).getMessageBody(),
                b.getCharSequence(Notification.EXTRA_TEXT));

        verify(mMockedActivityManager, times(2))
                .getIntentSenderWithFeature(anyInt(), any(), any(), any(), any(), anyInt(),
                        any(), any(), mFlags.capture(), any(), anyInt());

        assertTrue((PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                ==  mFlags.getAllValues().get(0));
        assertTrue((PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                ==  mFlags.getAllValues().get(1));

        Field field = ((Class) WindowManagerGlobal.class).getDeclaredField("sWindowManagerService");
        field.setAccessible(true);
        field.set(null, null);
    }

    public void testAddToNotificationWithDifferentConfiguration() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(R.bool.show_alert_title);
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);

        startActivity();
        waitForMs(100);
        leaveActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(1)).notify(mInt.capture(),
                mNotification.capture());
        Bundle b = mNotification.getValue().extras;

        assertEquals(1, (int) mInt.getValue());
        assertTrue(TextUtils.isEmpty(b.getCharSequence(Notification.EXTRA_TITLE)));
        verify(mContext.getResources(), times(1)).getString(mInt.capture(), anyInt());
        assertEquals(R.string.notification_multiple, (int) mInt.getValue());
    }

    public void testDoNotAddToNotificationOnStop() throws Throwable {
        startActivity();
        waitForMs(100);
        stopActivity();
        waitForMs(100);
        verify(mMockedNotificationManager, times(0)).notify(mInt.capture(),
                mNotification.capture());
    }

    public void testDismissByDeleteIntent() throws Throwable {
        final Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        Looper.prepare();
        CellBroadcastAlertDialog activity =
                startActivity(intent, null, null);
        getInstrumentation().callActivityOnUserLeaving(activity);
        verify(mMockedNotificationManager, atLeastOnce()).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
    }

    public void testGetNewMessageListIfNeeded() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        Resources spyRes = mContext.getResources();
        doReturn(false).when(spyRes).getBoolean(
                R.bool.show_cmas_messages_in_priority_order);

        SmsCbMessage testMessage1 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(12412,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        mCmasMessageClass);
        waitForMs(10);
        SmsCbMessage testMessage2 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(12412,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);
        ArrayList<SmsCbMessage> inputList1 = new ArrayList<>();
        ArrayList<SmsCbMessage> inputList2 = new ArrayList<>();

        inputList1.add(testMessage1);
        ArrayList<SmsCbMessage> messageList = activity.getNewMessageListIfNeeded(
                inputList1, inputList2);
        assertTrue(messageList.size() == 1);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(0).getReceivedTime());

        inputList2.add(testMessage1);
        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 1);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(0).getReceivedTime());

        inputList2.add(testMessage2);
        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 2);
        assertEquals(testMessage2.getReceivedTime(), messageList.get(1).getReceivedTime());

        doReturn(true).when(spyRes).getBoolean(
                R.bool.show_cmas_messages_in_priority_order);

        messageList = activity.getNewMessageListIfNeeded(inputList1, inputList2);
        assertTrue(messageList.size() == 2);
        assertEquals(testMessage1.getReceivedTime(), messageList.get(1).getReceivedTime());
    }

    @InstrumentationTest
    // This test has a module dependency (it uses the CellBroadcastContentProvider), so it is
    // disabled for OEM testing because it is not a true unit test
    public void testDismiss() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.dismiss();

        verify(mMockedNotificationManager, times(1)).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
    }

    public void testOnNewIntent() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);
        waitForMs(100);

        ImageView image = activity.findViewById(R.id.pictogramImage);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());

        // add more messages to list
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING,
                SmsCbConstants.MESSAGE_ID_ETWS_EARTHQUAKE_WARNING));
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        activity.onNewIntent(intent);

        verify(mMockedNotificationManager, atLeastOnce()).cancel(
                eq(CellBroadcastAlertService.NOTIFICATION_ID));
        assertNotNull(image.getLayoutParams());
    }

    public void testAnimationHandler() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();

        activity.mAnimationHandler.startIconAnimation(mSubId);

        assertTrue(activity.mAnimationHandler.mWarningIconVisible);

        Message m = Message.obtain();
        m.what = activity.mAnimationHandler.mCount.get();
        activity.mAnimationHandler.handleMessage(m);

        // assert that message count has gone up
        assertEquals(m.what + 1, activity.mAnimationHandler.mCount.get());
    }

    public void testOnResume() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        CellBroadcastAlertDialog.AnimationHandler mockAnimationHandler = mock(
                CellBroadcastAlertDialog.AnimationHandler.class);
        activity.mAnimationHandler = mockAnimationHandler;

        activity.onResume();
        verify(mockAnimationHandler).startIconAnimation(anyInt());
    }

    public void testOnPause() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        CellBroadcastAlertDialog.AnimationHandler mockAnimationHandler = mock(
                CellBroadcastAlertDialog.AnimationHandler.class);
        activity.mAnimationHandler = mockAnimationHandler;

        activity.onPause();
        verify(mockAnimationHandler).stopIconAnimation();
    }

    public void testOnKeyDown() throws Throwable {
        Intent intent = createActivityIntent();
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);

        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        assertTrue(activity.onKeyDown(0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS)));
    }

    public void testOnConfigurationChanged() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        Configuration newConfig = new Configuration();

        ImageView image = activity.findViewById(R.id.pictogramImage);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());

        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);
        assertNotNull(image.getLayoutParams());

        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
        activity.onConfigurationChanged(newConfig);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, image.getLayoutParams().height);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, image.getLayoutParams().width);
    }

    public void testOnWindowFocusChanged() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();

        ImageView image = activity.findViewById(R.id.pictogramImage);
        image.setVisibility(View.VISIBLE);
        assertEquals(View.VISIBLE, image.getVisibility());

        activity.onWindowFocusChanged(true);
        assertNotNull(image.getLayoutParams());
    }

    public void testOnKeyDownWithEmptyMessageList() throws Throwable {
        mMessageList = new ArrayList<>(1);

        Intent intent = new Intent(getInstrumentation().getTargetContext(),
                CellBroadcastAlertDialog.class);
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                mMessageList);
        intent.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);

        assertTrue(activity.onKeyDown(0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FOCUS)));
    }

    public void testPulsationHandlerStart() throws Throwable {
        int[] pattern = new int[] {0xFFFF0000, 100000, 500, 1000};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));

        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        assertEquals(0xFFFF0000, activity.mPulsationHandler.mHighlightColor);
        assertEquals(100000, activity.mPulsationHandler.mDuration);
        assertEquals(500, activity.mPulsationHandler.mOnInterval);
        assertEquals(1000, activity.mPulsationHandler.mOffInterval);

        waitForMs(2000);

        verify(mMockLinearLayout, atLeastOnce()).setBackgroundColor(eq(0xFFFF0000));
    }

    public void testPulsationRestartOnNewIntent() throws Throwable {
        int[] pattern = new int[] {0xFFFF0000, 100000, 500, 1000};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));

        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);
        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        assertEquals(0xFFFF0000, activity.mPulsationHandler.mHighlightColor);
        assertEquals(100000, activity.mPulsationHandler.mDuration);
        assertEquals(500, activity.mPulsationHandler.mOnInterval);
        assertEquals(1000, activity.mPulsationHandler.mOffInterval);

        pattern = new int[] {0xFFFFFFFF, 200000, 1000, 500};
        doReturn(pattern).when(mContext.getResources()).getIntArray(
                eq(com.android.cellbroadcastreceiver.R.array.default_pulsation_pattern));
        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY));
        Intent intent = createActivityIntent();
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        CellBroadcastSettings.resetResourcesCache();
        CellBroadcastChannelManager.clearAllCellBroadcastChannelRanges();
        activity.onNewIntent(intent);
        waitForMs(100);

        // Verify existing pulsation has been stopped
        verify(mMockLinearLayout, times(1)).setBackgroundColor(
                eq(activity.mPulsationHandler.mBackgroundColor));

        activity.mPulsationHandler.mLayout = mMockLinearLayout;

        // Verify new parameters have been applied
        assertEquals(0xFFFFFFFF, activity.mPulsationHandler.mHighlightColor);
        assertEquals(200000, activity.mPulsationHandler.mDuration);
        assertEquals(1000, activity.mPulsationHandler.mOnInterval);
        assertEquals(500, activity.mPulsationHandler.mOffInterval);

        waitForMs(2000);

        // Verify new pulsation takes effect
        verify(mMockLinearLayout, atLeastOnce()).setBackgroundColor(eq(0xFFFFFFFF));
    }

    public void testPulsationHandlerHandleMessageAndStop() throws Throwable {
        CellBroadcastAlertDialog activity = startActivity();
        waitForMs(100);

        int backgroundColor = activity.mPulsationHandler.mBackgroundColor;
        activity.mPulsationHandler.mHighlightColor = 0xFFFF0000;
        activity.mPulsationHandler.mLayout = mMockLinearLayout;
        activity.mPulsationHandler.mOnInterval = 60000;
        activity.mPulsationHandler.mOffInterval = 60000;
        activity.mPulsationHandler.mDuration = 300000;

        Message m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that message count has gone up, and the background color is highlighted
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertTrue(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(1)).setBackgroundColor(eq(0xFFFF0000));

        m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that message count has gone up, and the background color is restored
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertFalse(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(1)).setBackgroundColor(eq(backgroundColor));

        m = Message.obtain();
        m.what = activity.mPulsationHandler.mCount.get();
        activity.mPulsationHandler.handleMessage(m);

        // assert that the background color is highlighted again
        assertEquals(m.what + 1, activity.mPulsationHandler.mCount.get());
        assertTrue(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(2)).setBackgroundColor(eq(0xFFFF0000));

        activity.mPulsationHandler.stop();
        waitForMs(100);

        // assert that the background color is restored
        assertEquals(m.what + 2, activity.mPulsationHandler.mCount.get());
        assertFalse(activity.mPulsationHandler.mIsPulsationOn);
        verify(mMockLinearLayout, times(2)).setBackgroundColor(eq(backgroundColor));
    }

    private ArrayList<SmsCbMessage> getNewMessageList() throws Exception {
        Method method = CellBroadcastReceiverApp.class.getDeclaredMethod("getNewMessageList");
        method.setAccessible(true);
        return (ArrayList<SmsCbMessage>) method.invoke(null);
    }

    private ArrayList<SmsCbMessage> addNewMessageToList(SmsCbMessage message) {
        Class[] args = new Class[1];
        args[0] = SmsCbMessage.class;
        try {
            Method method = CellBroadcastReceiverApp.class.getDeclaredMethod(
                    "addNewMessageToList", args);
            method.setAccessible(true);
            return (ArrayList<SmsCbMessage>) method.invoke(null, message);
        } catch (Exception e) {
            return null;
        }
    }

    public void testNewMessageListCount() throws Throwable {
        SmsCbMessage testMessage1 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(75103,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_PRESIDENTIAL_LEVEL,
                        mCmasMessageClass);
        SmsCbMessage testMessage2 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(51030,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);
        SmsCbMessage testMessage3 = CellBroadcastAlertServiceTest
                .createMessageForCmasMessageClass(10307,
                        SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                        mCmasMessageClass);

        // touch a notification for on-going message
        Intent intent1 = createActivityIntent();
        intent1.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, false);
        intent1.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, false);
        addNewMessageToList(testMessage1);
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent1, null, null);
        waitForMs(100);

        assertEquals(1, getNewMessageList().size());

        // touch a notification for pending message
        Intent intent2 = createActivityIntent();
        intent2.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, false);
        intent2.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        addNewMessageToList(testMessage2);
        activity.onNewIntent(intent2);

        assertEquals(2, getNewMessageList().size());

        // swipe a notification for pending message
        Intent intent3 = createActivityIntent();
        intent3.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent3.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, true);
        addNewMessageToList(testMessage3);
        activity.onNewIntent(intent3);

        assertEquals(getNewMessageList().size(), 0);

        // swipe a notification for on-going message
        Intent intent4 = createActivityIntent();
        intent4.putExtra(CellBroadcastAlertService.DISMISS_DIALOG, true);
        intent4.putExtra(CellBroadcastAlertDialog.DISMISS_NOTIFICATION_EXTRA, false);
        addNewMessageToList(testMessage1);
        activity.onNewIntent(intent4);

        assertEquals(getNewMessageList().size(), 1);
    }

    private void setWatchUiMode() {
        Configuration configuration = new Configuration(
                mContext.getResources().getConfiguration());
        configuration.uiMode =
                (configuration.uiMode & ~Configuration.UI_MODE_TYPE_MASK)
                | Configuration.UI_MODE_TYPE_WATCH;
        mContext.enableOverrideConfiguration(true);
        mContext = (TestContext) mContext.createConfigurationContext(configuration);
        setActivityContext(mContext);
    }

    public void testOnConfigurationChangedForWatch() throws Throwable {
        setWatchUiMode();
        CellBroadcastAlertDialog activity = startActivity();

        Configuration newConfig = new Configuration();
        newConfig.orientation = Configuration.ORIENTATION_LANDSCAPE;
        activity.onConfigurationChanged(newConfig);

        newConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
        activity.onConfigurationChanged(newConfig);

        assertNull(activity.findViewById(R.id.pictogramImage));
    }

    public void testOnCreate() throws Throwable {
        doReturn(false).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);
        CellBroadcastAlertDialog activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE), 0);
        stopActivity();
    }

    public void testOnCreateWithCaptureRestriction() throws Throwable {
        doReturn(true).when(mContext.getResources()).getBoolean(
                R.bool.disable_capture_alert_dialog);
        CellBroadcastAlertDialog activity = startActivity();
        int flags = activity.getWindow().getAttributes().flags;
        assertEquals((flags & WindowManager.LayoutParams.FLAG_SECURE),
                WindowManager.LayoutParams.FLAG_SECURE);
        stopActivity();
    }

    public void testTitleOnNonDefaultSubId() throws Throwable {
        Intent intent = createActivityIntent();
        Looper.prepare();
        CellBroadcastAlertDialog activity = startActivity(intent, null, null);
        waitForMs(100);

        assertFalse(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));

        SharedPreferences mockSharedPreferences = mock(SharedPreferences.class);
        doReturn("334090").when(mockSharedPreferences).getString(any(), any());
        mContext.injectSharedPreferences(mockSharedPreferences);
        Resources mockResources2 = mock(Resources.class);
        doReturn(false).when(mockResources2).getBoolean(R.bool.show_alert_title);
        doReturn("none").when(mockResources2).getString(R.string.link_method);

        CellBroadcastSettings.sResourcesCacheByOperator.put("334090", mockResources2);

        mMessageList.add(CellBroadcastAlertServiceTest.createMessageForCmasMessageClass(12413,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY,
                SmsCbConstants.MESSAGE_ID_CMAS_ALERT_CHILD_ABDUCTION_EMERGENCY));
        intent.putParcelableArrayListExtra(CellBroadcastAlertService.SMS_CB_MESSAGE_EXTRA,
                new ArrayList<>(mMessageList));
        activity.onNewIntent(intent);

        assertTrue(TextUtils.isEmpty(((TextView) getActivity().findViewById(
                com.android.cellbroadcastreceiver.R.id.alertTitle)).getText()));
    }
}
