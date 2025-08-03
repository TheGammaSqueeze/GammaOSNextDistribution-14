/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.platform.test.scenario.facebook;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.platform.helpers.AbstractStandardAppHelper;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import java.util.regex.Pattern;

import junit.framework.Assert;

/** Create a facebook app helper that can be used to open and navigate FB App */
public class FacebookAppHelperImpl extends AbstractStandardAppHelper implements IFacebookAppHelper {

    public final String TEST_TAG = "FacebookAppTest";

    private static final String PACKAGE_NAME = "com.facebook.katana";
    private static final String APP_NAME = "Facebook";
    private static final String LOGIN_TEXT_DESC = "Username";
    private static final String PASSWORD_TEXT_DESC = "Password";
    private static final String LOGIN_BUTTON_DESC = "Login";
    private static final String OK_BUTTON_DESC = "OK";
    private static final String DENY_BUTTON_DESC = "Deny";
    private static final String NEWSFEED_LIST_ID = "android:id/content";
    private static final String GENERIC_ALLOW_TEXT = "ALLOW";
    private static final String WHILE_USING_APP_TEXT = "While using the app";
    private static final String REMIND_ME_LATER_TEXT = "REMIND ME LATER";
    private static final String FEED_TAB_PATTERN = "News Feed, Tab .*";

    private static final String LOGIN_ACTIVITY_NAME =
            "com.facebook.account.login.activity.SimpleLoginActivity";

    private static final int SHORT_TIMEOUT = 1000;
    private static final int LONG_TIMEOUT = 10000;

    private UiDevice mDevice;
    private UiAutomation mUiAutomation;
    private Context mContext = null;

    public FacebookAppHelperImpl(Instrumentation instrumentation) {
        super(instrumentation);
        mDevice = UiDevice.getInstance(instrumentation);
        mUiAutomation = instrumentation.getUiAutomation();
        mContext = instrumentation.getContext();
    }

    @Override
    public void dismissInitialDialogs() {
        UiObject2 loginOptions =
                mDevice.wait(Until.findObject(By.text(OK_BUTTON_DESC)), LONG_TIMEOUT);

        Assert.assertNotNull("Login options not found", loginOptions);
        loginOptions.click();

        UiObject2 accessLocation =
                mDevice.wait(Until.findObject(By.text(GENERIC_ALLOW_TEXT)), LONG_TIMEOUT);
        if (accessLocation != null) {
            accessLocation.click();
            mDevice.waitForIdle();

            UiObject2 locationPermission =
                    mDevice.wait(Until.findObject(By.text(WHILE_USING_APP_TEXT)), LONG_TIMEOUT);
            if (locationPermission != null) {
                locationPermission.click();
                mDevice.waitForIdle();
            }
        }

        UiObject2 outOfDate =
                mDevice.wait(Until.findObject(By.text(REMIND_ME_LATER_TEXT)), LONG_TIMEOUT);
        if (outOfDate != null) {
            outOfDate.click();
            mDevice.waitForIdle();
        }

        UiObject2 denyCamera =
                mDevice.wait(Until.findObject(By.desc(DENY_BUTTON_DESC)), LONG_TIMEOUT);
        if (denyCamera != null) {
            denyCamera.click();
            mDevice.waitForIdle();
        }

        Assert.assertTrue(
                "The phone doesn't appear to be on the news feed tab.",
                mDevice.wait(
                        Until.hasObject(By.desc(Pattern.compile(FEED_TAB_PATTERN))), LONG_TIMEOUT));
    }

    @Override
    public String getPackage() {
        return PACKAGE_NAME;
    }

    @Override
    public String getLauncherName() {
        return APP_NAME;
    }

    public void loginWithUi(String username, String password) {
        UiObject2 usernameText =
                mDevice.wait(Until.findObject(By.desc(LOGIN_TEXT_DESC)), LONG_TIMEOUT);
        Assert.assertNotNull("Login text box not found", usernameText);

        UiObject2 passwordText =
                mDevice.wait(Until.findObject(By.desc(PASSWORD_TEXT_DESC)), LONG_TIMEOUT);
        Assert.assertNotNull("Password text box not found", passwordText);

        usernameText.setText(username);
        passwordText.setText(password);

        UiObject2 loginButton =
                mDevice.wait(Until.findObject(By.desc(LOGIN_BUTTON_DESC)), LONG_TIMEOUT);

        Assert.assertNotNull("Login button not found", loginButton);
        loginButton.click();
    }

    public void scrollNewsfeed(Direction direction, int count) {
        for (int i = 0; i < count; i++) {
            UiObject2 newsfeedList =
                    mDevice.wait(Until.findObject(By.res(NEWSFEED_LIST_ID)), LONG_TIMEOUT);
            Assert.assertNotNull("Newsfeed List not found", newsfeedList);
            newsfeedList.setGestureMargin(mDevice.getDisplayHeight() / 4);
            newsfeedList.fling(direction);
            SystemClock.sleep(2500);
        }
    }

    private void startLoginActivity() {
        Intent intent = new Intent();
        intent.setAction(LOGIN_ACTIVITY_NAME);
        intent.setComponent(new ComponentName(PACKAGE_NAME, LOGIN_ACTIVITY_NAME));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }
}
