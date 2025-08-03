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

package com.android.interactive.steps.enterprise.settings;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

import com.google.common.truth.Truth;

@AutomationFor("com.android.interactive.steps.enterprise.settings.IsItPossibleToDeactivateRemoteDPCStep")
public final class IsItPossibleToDeactivateRemoteDPCStepAutomation implements Automation<Boolean> {

    private static final long WAIT_TIMEOUT = 10000;

    @Override
    public Boolean automate() throws Exception {
        UiScrollable settingsItem = new UiScrollable(new UiSelector()
                .className("androidx.recyclerview.widget.RecyclerView2"));
        UiObject remoteDpcText = settingsItem.getChildByText(new UiSelector()
                .className(TextView.class), "RemoteDPC");
        assertThat(remoteDpcText.exists()).isTrue();
        // TODO: This is very dependent on the view hierarchy and quite brittle
        UiObject2 remoteDpcSwitch =
                TestApis.ui().device().findObject(By.text("RemoteDPC")).getParent().getParent()
                        .findObject(By.checkable(true));

        remoteDpcSwitch.click();

        // Now on the confirmation page
        UiScrollable confirmPageList = new UiScrollable(new UiSelector()
                .className(ScrollView.class));
        UiObject cancelButton = confirmPageList.getChildByText(new UiSelector()
                .className(Button.class), "Cancel");
        Truth.assertWithMessage("Expected Cancel button on confirmation screen")
                .that(cancelButton.waitForExists(WAIT_TIMEOUT)).isTrue();

        // TODO: add a better failure message
        assertThrows(UiObjectNotFoundException.class, () -> {
            confirmPageList.getChildByText(new UiSelector()
                    .className(Button.class), "Deactivate this device admin app");
        });

        // Back to the list
        TestApis.ui().device().pressBack();

        // We expect to not be able to - as long as the button didn't appear then we can't do it
        return false;
    }
}
