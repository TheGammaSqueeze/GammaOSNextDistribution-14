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

package com.android.cellbroadcastreceiver.compliancetests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.KeyguardManager;
import android.app.LocaleManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.LocaleList;
import android.provider.Settings;
import android.provider.Telephony;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiScrollable;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.text.TextUtils;
import android.widget.LinearLayout;

import com.android.internal.util.HexDump;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Iterator;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class CellBroadcastUiTest extends CellBroadcastBaseTest {
    private static final String TAG = "CellBroadcastUiTest";
    private static final int UI_TIMEOUT = 10000;
    private static final Uri CONTENT_URI = Uri.parse("content://cellbroadcasts");
    private static final String SELECT_BY_SERIAL_NUMBER =
            Telephony.CellBroadcasts.SERIAL_NUMBER + "=?";
    private static int sSerialId = 0;
    /** Bitmask for messages of ETWS type (including future extensions). */
    private static final int MESSAGE_ID_ETWS_TYPE_MASK = 0xFFF8;
    /** Value for messages of ETWS type after applying {@link #MESSAGE_ID_ETWS_TYPE_MASK}. */
    private static final int MESSAGE_ID_ETWS_TYPE = 0x1100; // 4352
    private static final String CELL_BROADCAST_LIST_ACTIVITY =
            "com.android.cellbroadcastreceiver.CellBroadcastSettings";
    private static final BySelector FULL_SCREEN_DIALOG =
            By.res("android:id/immersive_cling_title");
    private static final BySelector CLOSE_BUTTON =
            By.res("android:id/ok");

    private static final BySelector YES_BUTTON =
            By.res("android:id/button1");

    private boolean mIsOptOutDialogHandled = false;

    @Before
    public void beforeTest() throws Exception {
        super.beforeTest();

        if ("testEmergencyAlertSettingsUi".equals(mTestNameRule.getMethodName())
                || "testAlertUiOnReceivedAlert".equals(mTestNameRule.getMethodName())) {
            KeyguardManager keyguardManager = getContext().getSystemService(KeyguardManager.class);
            assumeTrue("cannot test under secure keyguard",
                    keyguardManager != null && !keyguardManager.isKeyguardSecure());
            // dismiss keyguard and wait from idle
            if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
                dismissKeyGuard();
            }
        }
        if ("testAlertUiOnReceivedAlert".equals(mTestNameRule.getMethodName())) {
            PackageManager pm = getContext().getPackageManager();
            assumeTrue("FULL_ACCESS_CELL_BROADCAST_HISTORY permission "
                    + "is necessary for this test", pm.checkPermission(
                    "com.android.cellbroadcastservice.FULL_ACCESS_CELL_BROADCAST_HISTORY",
                    "com.android.shell") != PackageManager.PERMISSION_DENIED);
        }
        if ("testEmergencyAlertSettingsUi".equals(mTestNameRule.getMethodName())
                || "testAlertUiOnReceivedAlert".equals(mTestNameRule.getMethodName())) {
            sDevice.pressHome();
        }
    }

    @After
    public void afterTest() {
        if (sPreconditionError != 0) {
            return;
        }

        if ("testAlertUiOnReceivedAlert".equals(mTestNameRule.getMethodName())
                && (sSerialId > 0)) {
            deleteMessageWithShellPermissionIdentity();

            if (!mIsOptOutDialogHandled) {
                UiObject2 yesButton = sDevice.wait(Until.findObject(YES_BUTTON), 1000);
                if (yesButton != null) {
                    logd("yesButton click");
                    yesButton.click();
                    mIsOptOutDialogHandled = true;
                }
            }
        }

        if ("testEmergencyAlertSettingsUi".equals(mTestNameRule.getMethodName())
                || "testAlertUiOnReceivedAlert".equals(mTestNameRule.getMethodName())) {
            LocaleManager localeManager = getContext().getSystemService(LocaleManager.class);
            localeManager.setApplicationLocales(sPackageName, LocaleList.getEmptyLocaleList());
        }
    }

    @Test
    @Parameters(method = "paramsCarrierAndChannelForTest")
    public void testAlertUiOnReceivedAlert(String carrierName, String channel) throws Throwable {
        logd("CellBroadcastUiTest#testAlertUiOnReceivedAlert");
        CellBroadcastCarrierTestConfig carrierInfo =
                new CellBroadcastCarrierTestConfig(sCarriersObject, carrierName);
        CellBroadcastChannelTestConfig channelInfo =
                new CellBroadcastChannelTestConfig(sChannelsObject, carrierName, channel);
        // setup mccmnc
        if (sInputMccMnc == null || (sInputMccMnc != null
                && !sInputMccMnc.equals(carrierInfo.mMccMnc))) {
            setSimInfo(carrierName, carrierInfo.mMccMnc);
        }

        // change language of CBR
        changeLocale(carrierInfo, sPackageName, true);

        if (!channelInfo.mChannelDefaultValue || TextUtils.isEmpty(channelInfo.mExpectedTitle)
                || channelInfo.mFilteredLanguageBySecondLanguagePref
                || channelInfo.mIsEnabledOnTestMode
                || !channelInfo.mNeedDisplay) {
            // let's skip for alerttitle
            return;
        }
        boolean isMessageEnglish = !channelInfo.mIgnoreMessageByLanguageFilter
                || (channelInfo.mIgnoreMessageByLanguageFilter && carrierInfo.mLanguageTag != null
                && !carrierInfo.mLanguageTag.equals("en"));

        // receive broadcast message
        receiveBroadcastMessage(channel, channelInfo.mWarningType, isMessageEnglish);

        logd("carrier " + carrierName + ", expectedTitle = "
                + channelInfo.mExpectedTitle + " for channel " + channel
                + ", alertTypeIsNotification = " + channelInfo.mAlertTypeIsNotification);
        if (channelInfo.mAlertTypeIsNotification) {
            verifyNotificationPosted(carrierName, channelInfo.mExpectedTitle, channel,
                    sPackageName, channelInfo.mIgnoreMessageByLanguageFilter);
        } else {
            verifyAlertDialogTitle(carrierName, channelInfo.mExpectedTitle, channel,
                    carrierInfo.mDisableNavigation, carrierInfo.mNeedFullScreen, sPackageName,
                    channelInfo.mIgnoreMessageByLanguageFilter);
        }
    }

    public void receiveBroadcastMessage(String channelName, String warningType,
            boolean isMessageEnglish) {
        int channel = Integer.parseInt(channelName);
        String hexChannel = String.format("%04X", channel);

        sSerialId++;
        String serialHexString = String.format("%04X", sSerialId);
        logd("receiveBroadcastMessage, channel = " + hexChannel
                + ", serialIdHexString = " + serialHexString);

        boolean isEtws = (channel & MESSAGE_ID_ETWS_TYPE_MASK) == MESSAGE_ID_ETWS_TYPE;
        String langCode = isMessageEnglish ? "01" : "00"; // 01 is english, 00 is german
        String etwsWarningType = TextUtils.isEmpty(warningType) ? "00" : warningType;
        String pduCode = isEtws ? etwsWarningType : langCode;
        String hexString = serialHexString + hexChannel + pduCode + "11D4F29C0E0AB2CB727A08";
        byte[] data = HexDump.hexStringToByteArray(hexString);

        sMockModemManager.newBroadcastSms(sSlotId, data);
    }

    private void verifyAlertDialogTitle(String carrier, String title, String channel,
            boolean disableNavigation, boolean needsFullScreen, String packageName,
            boolean ignoreMessageByLanguageFilter) {
        boolean expectedResult = ignoreMessageByLanguageFilter ? false : true;
        if (needsFullScreen && !isConfirmedForFullScreenGuide(getContext())) {
            checkForFullScreenGuide();
        }
        boolean result = false;
        String outputTitle = null;
        UiObject2 item = sDevice.wait(Until.findObject(By.res(packageName, "alertTitle")),
                UI_TIMEOUT);
        if (item != null) {
            outputTitle = item.getText();
            if (outputTitle != null && outputTitle.startsWith(title)) {
                result = true;
            }
            if (disableNavigation) {
                // for certain country like chile, check if system navigation bar is disabled
                sDevice.openNotification();
                UiSelector notificationStackScroller = new UiSelector()
                        .packageName("com.android.systemui")
                        .resourceId("com.android.systemui:id/notification_stack_scroller");
                UiObject widget = new UiObject(notificationStackScroller);
                boolean canOpenNotification = widget.waitForExists(3000);
                assertEquals("carrier=" + carrier + ", channel=" + channel
                        + ", system ui should be disabled. ", canOpenNotification, false);
            }
            // dismiss dialog
            UiObject2 okItem = sDevice.wait(Until.findObject(
                    By.res(packageName, "dismissButton")), UI_TIMEOUT);
            if (okItem != null) {
                okItem.click();
            }
        }
        assertEquals("carrier=" + carrier + ", channel=" + channel
                + ", output title=" + outputTitle
                + ", expected title=" + title, expectedResult, result);
    }

    /** Pulls down notification shade and verifies that message text is found. */
    private void verifyNotificationPosted(String carrier, String title, String channel,
            String packageName, boolean ignoreMessageByLanguageFilter)
            throws UiObjectNotFoundException {
        boolean expectedResult = ignoreMessageByLanguageFilter ? false : true;

        // open notification shade
        sDevice.openNotification();
        boolean hasObject = sDevice.wait(Until.hasObject(By.text(title)), UI_TIMEOUT);
        if (hasObject) {
            dismissNotificationAsNeeded(title, packageName);
        }
        assertEquals("carrier=" + carrier + ", channel=" + channel
                + ", expected title=" + title, expectedResult, hasObject);
    }

    private void dismissNotificationAsNeeded(String title, String packageName)
            throws UiObjectNotFoundException {
        UiSelector notificationStackScroller = new UiSelector()
                .packageName("com.android.systemui")
                .resourceId("com.android.systemui:id/notification_stack_scroller");
        UiObject object = sDevice.findObject(notificationStackScroller);
        UiObject object2 = object.getChild(new UiSelector().textContains(title));
        if (object2 != null) {
            object2.click();
            UiObject2 okItem = sDevice.wait(Until.findObject(
                    By.res(packageName, "dismissButton")), UI_TIMEOUT);
            if (okItem != null) {
                okItem.click();
            }
        }
    }

    @Test
    @Parameters(method = "paramsForTest")
    public void testEmergencyAlertSettingsUi(String carrierName) throws Throwable {
        logd("CellBroadcastUiTest#testEmergencyAlertSettingsUi");
        CellBroadcastCarrierTestConfig carrierInfo =
                new CellBroadcastCarrierTestConfig(sCarriersObject, carrierName);
        // setup mccmnc
        if (sInputMccMnc == null || (sInputMccMnc != null
                && !sInputMccMnc.equals(carrierInfo.mMccMnc))) {
            setSimInfo(carrierName, carrierInfo.mMccMnc);
        }

        // change language of CBR
        changeLocale(carrierInfo, sPackageName, false);

        // launch setting activity of CBR
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(sPackageName, CELL_BROADCAST_LIST_ACTIVITY));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);

        UiScrollable listView = new UiScrollable(new UiSelector().resourceId("android:id"
                + "/list_container"));
        listView.waitForExists(2000);

        // check if there is each setting menu
        JSONObject settingsForCarrier = sSettingsObject.getJSONObject(carrierName);
        for (Iterator<String> iterator = settingsForCarrier.keys(); iterator.hasNext(); ) {
            String settingName = iterator.next();
            CellBroadcastSettingTestConfig settingInfo =
                    new CellBroadcastSettingTestConfig(settingsForCarrier, settingName);
            if (!settingInfo.mIsToggleAvailability) {
                continue;
            }
            UiObject item = null;
            try {
                item = listView.getChildByText(new UiSelector()
                        .className(LinearLayout.class.getName()), settingName, true);
            } catch (UiObjectNotFoundException e) {
                logd("let's scrollforward if it cannot find switch");
                listView.scrollForward();
                try {
                    item = listView.getChildByText(new UiSelector()
                                    .className(LinearLayout.class.getName()),
                            settingName, true);
                } catch (UiObjectNotFoundException e2) {
                    assertTrue("carrier=" + carrierName + ", settingName="
                            + settingName, false);
                }
                return;
            }
            UiObject itemSwitch = null;
            try {
                itemSwitch = item.getChild(new UiSelector()
                        .className(android.widget.Switch.class.getName()));
                logd("itemSwitch = " + itemSwitch.isChecked());
            } catch (UiObjectNotFoundException e) {
                logd("switch not found, let's find again");
                String searchText = settingName;
                if (settingInfo.mSummary != null) {
                    searchText = settingInfo.mSummary;
                } else {
                    // let's scrollforward if it cannot find switch
                    listView.scrollForward();
                }
                item = listView.getChildByText(new UiSelector()
                        .className(LinearLayout.class.getName()), searchText, true);
                itemSwitch = item.getChild(new UiSelector()
                        .className(android.widget.Switch.class.getName()));
            }
            assertEquals("carrierName=" + carrierName + ", settingName=" + settingName
                    + ", expectedSwitchValue=" + settingInfo.mIsToggleAvailability,
                    settingInfo.mExpectedSwitchValue, itemSwitch.isChecked());
        }
        sDevice.pressBack();
    }

    private void dismissKeyGuard() throws Exception {
        logd("dismissKeyGuard");
        sDevice.wakeUp();
        sDevice.executeShellCommand("wm dismiss-keyguard");
    }

    private boolean checkForFullScreenGuide() {
        logd("checkForFullScreenGuide");
        UiObject2 viewObject = sDevice.wait(Until.findObject(FULL_SCREEN_DIALOG),
                UI_TIMEOUT);
        if (viewObject != null) {
            logd("Found full screen dialog, dismissing.");
            UiObject2 okButton = sDevice.wait(Until.findObject(CLOSE_BUTTON), UI_TIMEOUT);
            if (okButton != null) {
                okButton.click();
                return true;
            } else {
                logd("Unable to dismiss full screen dialog");
            }
        }
        return false;
    }

    private boolean isConfirmedForFullScreenGuide(Context context) {
        logd("isConfirmedForFullScreenGuide");
        String value = null;
        boolean isConfirmed = false;
        try {
            value = Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.IMMERSIVE_MODE_CONFIRMATIONS);
            isConfirmed = "confirmed".equals(value);
            logd("Loaded isConfirmed = " + isConfirmed);
        } catch (Throwable t) {
            logd("Error loading confirmations, value=" + value);
        }
        return isConfirmed;
    }

    private void changeLocale(CellBroadcastCarrierTestConfig info,
            String packageName, boolean checkAlertUi) {
        LocaleManager localeManager = getContext().getSystemService(LocaleManager.class);
        if (info.mLanguageTag != null && (checkAlertUi || info.mCheckSettingWithMainLanguage)) {
            logd("setApplicationLocales " + info.mLanguageTag);
            localeManager.setApplicationLocales(packageName,
                    LocaleList.forLanguageTags(info.mLanguageTag));
        } else {
            logd("setApplicationLocales to default");
            localeManager.setApplicationLocales(packageName,
                    LocaleList.forLanguageTags("en-US"));
        }
    }

    private void deleteMessageWithShellPermissionIdentity() {
        UiAutomation uiAutomation = sInstrumentation.getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        try {
            getContext().getContentResolver().delete(CONTENT_URI,
                    SELECT_BY_SERIAL_NUMBER, new String[]{String.valueOf(sSerialId)});
        } catch (SecurityException e) {
            logd("runWithShellPermissionIdentity exception = " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
