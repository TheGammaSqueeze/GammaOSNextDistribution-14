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

package android.platform.test.rule;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

/** Tests for {@link CancelNotificationRule}. */
@RunWith(JUnit4.class)
public class CancelNotificationRuleTest {

    private static final Statement TEST_STATEMENT =
            new Statement() {
                @Override
                public void evaluate() {}
            };
    private static final Description TEST_DESCRIPTION =
            Description.createTestDescription("class", "method");

    @Test
    public void testCancelNotificationRule() throws Throwable {
        CancelNotificationRuleTest.TestableCancelNotificationRule rule =
                new CancelNotificationRuleTest.TestableCancelNotificationRule();
        NotificationManager mockNotificationManager = Mockito.mock(NotificationManager.class);
        when(mockNotificationManager.getActiveNotifications())
                .thenReturn(new StatusBarNotification[0]);

        rule.setNotificationManager(mockNotificationManager);
        rule.apply(TEST_STATEMENT, TEST_DESCRIPTION).evaluate();

        // Validate if the cancelAll is called once
        verify(mockNotificationManager, times(1)).cancelAll();
    }

    private static class TestableCancelNotificationRule extends CancelNotificationRule {
        private UiDevice mDevice;
        private Bundle mArgs;

        TestableCancelNotificationRule() {
            mDevice = Mockito.mock(UiDevice.class);
            mArgs = new Bundle();
        }

        @Override
        protected Bundle getArguments() {
            return mArgs;
        }

        @Override
        protected UiDevice getUiDevice() {
            return mDevice;
        }
    }
}
