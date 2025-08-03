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

package android.autofillservice.cts.saveui;

import static android.service.autofill.SaveInfo.SAVE_DATA_TYPE_GENERIC;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.autofillservice.cts.R;
import android.autofillservice.cts.activities.SimpleSaveActivity;
import android.autofillservice.cts.activities.WelcomeActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.DeviceUtils;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.SaveRequest;
import android.autofillservice.cts.testcore.UiBot;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Basic initial tests for the SaveDialog UI. These tests should probably be moved to a full-fledged
 * test suite like DialogLoginActivityTest later.
 */
@RunWith(TestParameterInjector.class)
public class SaveUiTest extends AutoFillServiceTestCase.BaseTestCase {

    private static final String TAG = "SaveUiTest";

    @Rule
    public ActivityScenarioRule<SimpleSaveActivity> rule =
            new ActivityScenarioRule<>(SimpleSaveActivity.class);

    @Before
    public void setUp() throws Exception {
        enableService();

        DeviceUtils.wakeUp();
        DeviceUtils.unlockScreen();
        DeviceUtils.closeSystemDialogs();
    }

    enum EXIT_SAVE_SCREEN {
        ROTATE_THEN_TAP_BACK_BUTTON,
        TAP_BACK_BUTTON,
        FINISH_ACTIVITY
    }

    @Test
    public void testTapLink_changeOrientationThenTapBack(@TestParameter EXIT_SAVE_SCREEN type)
            throws Exception {
        Assume.assumeTrue("Rotation is supported", Helper.isRotationSupported(mContext));
        Assume.assumeTrue(
                "Device state is not REAR_DISPLAY",
                !Helper.isDeviceInState(mContext, Helper.DeviceStateEnum.REAR_DISPLAY));
        mUiBot.assumeMinimumResolution(500);

        // Set expectations.
        sReplier.onRequest(
                fillRequest -> {
                    return new CannedFillResponse.Builder()
                            .setRequiredSavableIds(
                                    SAVE_DATA_TYPE_GENERIC, SimpleSaveActivity.ID_INPUT)
                            .setSaveInfoVisitor(
                                    (contexts, builder) ->
                                            builder.setCustomDescription(
                                                    Helper.CustomDescriptionUtils
                                                            .newCustomDescription(
                                                                    mContext,
                                                                    WelcomeActivity.class,
                                                                    mPackageName)))
                            .build();
                });

        rule.getScenario()
                .onActivity(
                        activity -> {
                            final EditText input = activity.findViewById(R.id.input);
                            input.setTextIsSelectable(true);
                        });

        onView(withId(R.id.input)).perform(ViewActions.click());
        onView(withId(R.id.input)).perform(ViewActions.typeText("108"));
        onView(withId(R.id.commit)).perform(ViewActions.click());

        DeviceUtils.SaveDialog.assertShows();
        mUiBot.selectByText("DON'T TAP ME!");

        // Make sure new activity is shown...
        DeviceUtils.Logcat.includes("WelcomeActivity:D", "Message");
        DeviceUtils.SaveDialog.assertHidden();

        // .. then do something to return to previous activity...
        switch (type) {
            case ROTATE_THEN_TAP_BACK_BUTTON:
                // After the device rotates, the input field get focus and generate a new session.
                sReplier.addResponse(CannedFillResponse.NO_RESPONSE);

                mUiBot.setScreenOrientation(UiBot.LANDSCAPE);
                WelcomeActivity.assertShowingDefaultMessage(mUiBot);
                // not breaking on purpose
            case TAP_BACK_BUTTON:
                // ..then go back and save it.
                mUiBot.pressBack();
                break;
            case FINISH_ACTIVITY:
                // ..then finishes it.
                WelcomeActivity.finishIt();
                break;
            default:
                throw new IllegalArgumentException("invalid type: " + type);
        }
        // Make sure previous activity is back...
        DeviceUtils.Logcat.includes("SimpleSaveActivity:D", "onResume()");

        // ... and tap save.
        DeviceUtils.SaveDialog.assertShows();
        mUiBot.selectByRelativeId("android", "autofill_save_yes");

        final SaveRequest saveRequest = sReplier.getNextSaveRequest();
        Helper.assertTextAndValue(
                Helper.findNodeByResourceId(saveRequest.structure, SimpleSaveActivity.ID_INPUT),
                "108");
        rule.getScenario().moveToState(androidx.lifecycle.Lifecycle.State.DESTROYED);
    }

    @NonNull
    @Override
    protected TestRule getMainTestRule() {
        // No-op test rule because we're managing activities ourselves.
        return (base, description) -> base;
    }
}
