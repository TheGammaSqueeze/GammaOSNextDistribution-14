/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.server.biometrics;

import static com.android.systemui.Flags.constraintBp;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.mock;

import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricTestSession;
import android.hardware.biometrics.Flags;
import android.hardware.biometrics.PromptContentItem;
import android.hardware.biometrics.PromptContentItemBulletedText;
import android.hardware.biometrics.PromptContentItemPlainText;
import android.hardware.biometrics.PromptVerticalListContentView;
import android.hardware.biometrics.SensorProperties;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Basic test cases for content view on biometric prompt.
 */
@Presubmit
public class BiometricPromptContentViewTest extends BiometricTestBase {
    private static final String TAG = "BiometricTests/PromptVerticalListContentView";

    /**
     * Test the max item number shown on {@link PromptVerticalListContentView}.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_maxItemNumber() {
        final String contentViewDescription = "Content view description";
        final PromptVerticalListContentView.Builder contentViewBuilder =
                new PromptVerticalListContentView.Builder().setDescription(contentViewDescription);
        for (int i = 0; i < PromptVerticalListContentView.getMaxItemCount() + 1; i++) {
            contentViewBuilder.addListItem(new PromptContentItemBulletedText(("list item")));
        }

        IllegalStateException e = assertThrows(IllegalStateException.class,
                contentViewBuilder::build);
        assertThat(e).hasMessageThat().contains("The number of list items exceeds");
    }

    /**
     * Test the max character number of each list item shown on
     * {@link PromptVerticalListContentView}.
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void test_maxEachItemCharacterNumber() {
        final String contentViewDescription = "Content view description";
        final Random random = new Random(System.currentTimeMillis());
        final int maxCharNum = PromptVerticalListContentView.getMaxEachItemCharacterNumber() + 1;
        final StringBuilder randomString = new StringBuilder(maxCharNum);
        for (int i = 0; i < maxCharNum; i++) {
            randomString.append((random.nextInt() & 0xf) % 10);
        }
        final String longListItemText = randomString.toString();
        final PromptVerticalListContentView.Builder contentViewBuilder =
                new PromptVerticalListContentView.Builder().setDescription(contentViewDescription);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> contentViewBuilder
                        .addListItem(new PromptContentItemBulletedText("item list"))
                        .addListItem(new PromptContentItemBulletedText(longListItemText)));
        assertThat(e).hasMessageThat().contains("The character number of list item exceeds");
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested. When {@link BiometricPrompt.Builder#setContentView} is
     * called, {@link BiometricPrompt.Builder#setDescription} is overridden.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setContentView",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setNegativeButton",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationCallback#onAuthenticationSucceeded",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#setDescription"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void testSimpleBiometricAuth_nonConvenience_setContentView() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED);

                enrollForSensor(session, props.getSensorId());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_SUCCESS);

                final Random random = new Random();
                final String randomTitle = String.valueOf(random.nextInt(10000));
                final String randomSubtitle = String.valueOf(random.nextInt(10000));
                final String randomDescription = String.valueOf(random.nextInt(10000));
                final String randomNegativeButtonText = String.valueOf(random.nextInt(10000));

                final String randomContentViewDescription =
                        String.valueOf(random.nextInt(10000));
                final String randomContentViewItem1 = String.valueOf(random.nextInt(10000));
                final String randomContentViewItem2 = String.valueOf(random.nextInt(10000));
                final PromptVerticalListContentView.Builder contentViewBuilder =
                        new PromptVerticalListContentView.Builder().setDescription(
                                randomContentViewDescription);
                final List<PromptContentItem> itemList = new ArrayList<>();
                itemList.add(new PromptContentItemBulletedText(randomContentViewItem1));
                itemList.add(new PromptContentItemPlainText(randomContentViewItem2));
                itemList.forEach(contentViewBuilder::addListItem);
                final PromptVerticalListContentView randomContentView = contentViewBuilder.build();

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPromptWithContents(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, randomTitle, randomSubtitle,
                        randomDescription, randomContentView, randomNegativeButtonText);

                final UiObject2 actualLogo = findView(LOGO_VIEW);
                final UiObject2 actualLogoDescription = findView(LOGO_DESCRIPTION_VIEW);
                final UiObject2 actualTitle = findView(TITLE_VIEW);
                final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
                final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
                final UiObject2 actualNegativeButton = findView(BUTTON_ID_NEGATIVE);
                final UiObject2 actualContentContainerView = findView(CONTENT_CONTAINER_VIEW);
                final UiObject2 actualContentView = actualContentContainerView.getChildren().get(0);
                final UiObject2 actualContentViewDescription =
                        actualContentView.getChildren().get(0);
                final UiObject2 actualContentViewItemListFirstRow =
                        actualContentView.getChildren().get(1);
                final UiObject2 actualContentViewItemTextView1 =
                        actualContentViewItemListFirstRow.getChildren().get(0);
                final UiObject2 actualContentViewItemTextView2 =
                        actualContentViewItemListFirstRow.getChildren().get(1);

                assertThat(actualLogo.getVisibleBounds()).isNotNull();
                assertThat(actualLogoDescription.getText())
                        .isEqualTo("CtsBiometricsTestCases");
                assertThat(actualTitle.getText()).isEqualTo(randomTitle);
                assertThat(actualSubtitle.getText()).isEqualTo(randomSubtitle);
                // With content view set, description should be null.
                assertThat(actualDescription).isNull();
                assertThat(actualNegativeButton.getText()).isEqualTo(randomNegativeButtonText);
                assertThat(actualContentViewDescription.getText()).isEqualTo(
                        randomContentViewDescription);
                // With short enough content, the container should not be scrollable.
                assertThat(actualContentContainerView.isScrollable()).isFalse();
                // Since items are all short enough, the column number should be 2.
                assertThat(actualContentViewItemListFirstRow.getChildCount()).isEqualTo(2);
                assertThat(actualContentViewItemTextView1.getText()).isEqualTo(
                        randomContentViewItem1);
                assertThat(actualContentViewItemTextView2.getText()).isEqualTo(
                        randomContentViewItem2);

                // Finish auth
                successfullyAuthenticate(session, 0 /* userId */, callback);
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested. Tests that if there is at least one item too long, content
     * view will be 1 column instead of 2 columns as default.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void testSimpleBiometricAuth_nonConvenience_setContentView_itemTooLongFor2Column()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        // TODO (b/302735104): add another test to check tablet columns.
        assumeFalse(isTablet());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED);

                enrollForSensor(session, props.getSensorId());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_SUCCESS);

                final Random random = new Random();
                final String randomContentViewDescription =
                        String.valueOf(random.nextInt(10000));

                final String randomContentViewItem1 = String.valueOf(random.nextInt(10000));
                // Set a long-enough string to show single column.
                final int charNum = 100;
                final StringBuilder longString = new StringBuilder(charNum);
                for (int i = 0; i < charNum; i++) {
                    longString.append(random.nextInt(10));
                }
                final String randomContentViewItem2 = longString.toString();

                final PromptVerticalListContentView.Builder contentViewBuilder =
                        new PromptVerticalListContentView.Builder().setDescription(
                                randomContentViewDescription);
                final List<PromptContentItem> itemList = new ArrayList<>();
                itemList.add(new PromptContentItemBulletedText(randomContentViewItem1));
                itemList.add(new PromptContentItemPlainText(randomContentViewItem2));
                itemList.forEach(contentViewBuilder::addListItem);
                final PromptVerticalListContentView randomContentView = contentViewBuilder.build();

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPromptWithContents(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, "title", "subtitle",
                        "description", randomContentView, "negative button");

                final UiObject2 actualContentContainerView = findView(CONTENT_CONTAINER_VIEW);
                final UiObject2 actualContentView = actualContentContainerView.getChildren().get(0);
                final UiObject2 actualContentViewDescription =
                        actualContentView.getChildren().get(0);
                final UiObject2 actualContentViewItemListRow1 =
                        actualContentView.getChildren().get(1);
                final UiObject2 actualContentViewItemTextView1 =
                        actualContentViewItemListRow1.getChildren().get(0);
                final UiObject2 actualContentViewItemListRow2 =
                        actualContentView.getChildren().get(2);
                final UiObject2 actualContentViewItemTextView2 =
                        actualContentViewItemListRow2.getChildren().get(0);

                assertThat(actualContentViewDescription.getText()).isEqualTo(
                        randomContentViewDescription);
                // Since there is at least one item that is long enough, the column number should
                // be 1.
                assertThat(actualContentViewItemListRow1.getChildCount()).isEqualTo(1);
                assertThat(actualContentViewItemTextView1.getText()).isEqualTo(
                        randomContentViewItem1);
                // Since there is at least one item that is long enough, the column number should
                // be 1.
                assertThat(actualContentViewItemListRow2.getChildCount()).isEqualTo(1);
                assertThat(actualContentViewItemTextView2.getText()).isEqualTo(
                        randomContentViewItem2);

                // Finish auth
                successfullyAuthenticate(session, 0 /* userId */, callback);
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when biometric auth is requested. Tests that with enough content, content view will start
     * scrolling.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     *
     * TODO(b/302735104): cover all sensors besides udfps and unignore.
     */
    @Ignore
    @CddTest(requirements = {"7.3.10/C-4-2"})
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricManager#canAuthenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#addListItem"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void testSimpleBiometricAuth_nonConvenience_setContentView_scrollability()
            throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        for (SensorProperties props : mSensorProperties) {
            if (props.getSensorStrength() == SensorProperties.STRENGTH_CONVENIENCE) {
                continue;
            }

            Log.d(TAG, "testSimpleBiometricAuth, sensor: " + props.getSensorId());

            try (BiometricTestSession session =
                         mBiometricManager.createTestSession(props.getSensorId())) {

                final int authenticatorStrength =
                        Utils.testApiStrengthToAuthenticatorStrength(props.getSensorStrength());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED);

                enrollForSensor(session, props.getSensorId());

                assertWithMessage("Sensor: " + props.getSensorId()
                        + ", strength: " + props.getSensorStrength()).that(
                        mBiometricManager.canAuthenticate(authenticatorStrength)).isEqualTo(
                        BiometricManager.BIOMETRIC_SUCCESS);

                final Random random = new Random();
                final String randomContentViewDescription =
                        String.valueOf(random.nextInt(10000));

                final PromptVerticalListContentView.Builder contentViewBuilder =
                        new PromptVerticalListContentView.Builder().setDescription(
                                randomContentViewDescription);
                final int maxItemCount = PromptVerticalListContentView.getMaxItemCount();
                final List<PromptContentItem> itemList = new ArrayList<>();
                // Set long-enough string to make content view scrollable.
                final int maxCharNum =
                        PromptVerticalListContentView.getMaxEachItemCharacterNumber();
                final StringBuilder longString = new StringBuilder(maxCharNum);
                for (int i = 0; i < maxCharNum; i++) {
                    longString.append(random.nextInt(10));
                }
                for (int i = 0; i < maxItemCount - 1; i++) {
                    itemList.add(new PromptContentItemBulletedText(longString.toString()));
                }
                final String contentViewLastItemText = "last item";
                itemList.add(new PromptContentItemBulletedText(contentViewLastItemText));
                itemList.forEach(contentViewBuilder::addListItem);
                final PromptVerticalListContentView randomContentView = contentViewBuilder.build();

                BiometricPrompt.AuthenticationCallback callback =
                        mock(BiometricPrompt.AuthenticationCallback.class);

                showDefaultBiometricPromptWithContents(props.getSensorId(), 0 /* userId */,
                        true /* requireConfirmation */, callback, "title", "subtitle",
                        "description", randomContentView, "negative button");

                final UiObject2 actualContentContainerView = findView(CONTENT_CONTAINER_VIEW);
                final UiObject2 actualContentView = actualContentContainerView.getChildren().get(0);
                final UiObject2 actualContentViewDescription =
                        actualContentView.getChildren().get(0);

                assertThat(actualContentViewDescription.getText()).isEqualTo(
                        randomContentViewDescription);
                // Content view is scrollable
                assertThat(actualContentContainerView.isScrollable()).isTrue();
                actualContentContainerView.scrollUntil(Direction.DOWN,
                        Until.findObject(By.text(contentViewLastItemText)));

                // Finish auth
                successfullyAuthenticate(session, 0 /* userId */, callback);
            }
        }
    }

    /**
     * Tests that the values specified through the public APIs are shown on the BiometricPrompt UI
     * when credential auth is requested.
     *
     * Upon successful authentication, checks that the result is
     * {@link BiometricPrompt#AUTHENTICATION_RESULT_TYPE_BIOMETRIC}
     */
    @ApiTest(apis = {
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setTitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setSubtitle",
            "android.hardware.biometrics."
                    + "BiometricPrompt.Builder#setDescription",
            "android.hardware.biometrics."
                    + "PromptVerticalListContentView.Builder#build",
            "android.hardware.biometrics."
                    + "BiometricPrompt#authenticate",
            "android.hardware.biometrics."
                    + "BiometricPrompt.AuthenticationResult#getAuthenticationType"})
    @RequiresFlagsEnabled(Flags.FLAG_CUSTOM_BIOMETRIC_PROMPT)
    @Test
    public void testSimpleCredentialAuth_withContentView_showsTwoStep() throws Exception {
        assumeTrue(Utils.isFirstApiLevel29orGreater());
        assumeTrue(!isWatch() || constraintBp());
        try (CredentialSession session = new CredentialSession()) {
            session.setCredential();

            final Random random = new Random();
            final String randomTitle = String.valueOf(random.nextInt(10000));
            final String randomSubtitle = String.valueOf(random.nextInt(10000));
            final String randomDescription = String.valueOf(random.nextInt(10000));

            CountDownLatch latch = new CountDownLatch(1);
            BiometricPrompt.AuthenticationCallback callback =
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                BiometricPrompt.AuthenticationResult result) {
                            assertWithMessage("Must be TYPE_CREDENTIAL").that(
                                    result.getAuthenticationType()).isEqualTo(
                                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL);
                            latch.countDown();
                        }
                    };
            showCredentialOnlyBiometricPromptWithContents(callback, new CancellationSignal(),
                    true /* shouldShow */, randomTitle, randomSubtitle, randomDescription,
                    new PromptVerticalListContentView.Builder().build());

            final UiObject2 actualTitle = findView(TITLE_VIEW);
            final UiObject2 actualSubtitle = findView(SUBTITLE_VIEW);
            final UiObject2 actualDescription = findView(DESCRIPTION_VIEW);
            assertThat(actualTitle.getText()).isEqualTo(randomTitle);
            // With content view set, subtitle should be null.
            assertThat(actualSubtitle).isNull();
            // With content view set, description should be null.
            assertThat(actualDescription).isNull();

            // Finish auth
            successfullyEnterCredential();
            latch.await(3, TimeUnit.SECONDS);
        }
    }
}
