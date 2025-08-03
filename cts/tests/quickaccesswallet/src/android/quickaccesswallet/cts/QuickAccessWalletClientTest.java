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
package android.quickaccesswallet.cts;

import static android.Manifest.permission.MANAGE_DEFAULT_APPLICATIONS;
import static android.Manifest.permission.MANAGE_ROLE_HOLDERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.quickaccesswallet.NoPermissionQuickAccessWalletService;
import android.quickaccesswallet.QuickAccessWalletActivity;
import android.quickaccesswallet.QuickAccessWalletDelegateTargetActivityService;
import android.quickaccesswallet.QuickAccessWalletSettingsActivity;
import android.quickaccesswallet.TestHostApduService;
import android.quickaccesswallet.TestQuickAccessWalletService;
import android.service.quickaccesswallet.GetWalletCardsError;
import android.service.quickaccesswallet.GetWalletCardsRequest;
import android.service.quickaccesswallet.GetWalletCardsResponse;
import android.service.quickaccesswallet.QuickAccessWalletClient;
import android.service.quickaccesswallet.QuickAccessWalletClient.WalletPendingIntentCallback;
import android.service.quickaccesswallet.QuickAccessWalletClient.WalletServiceEventListener;
import android.service.quickaccesswallet.QuickAccessWalletService;
import android.service.quickaccesswallet.SelectWalletCardRequest;
import android.service.quickaccesswallet.WalletCard;
import android.service.quickaccesswallet.WalletServiceEvent;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.UserSettings;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests parceling of the {@link WalletCard}
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class QuickAccessWalletClientTest {

    private static final String SETTING_KEY = "lockscreen_show_wallet";
    private static String NFC_PAYMENT_DEFAULT_COMPONENT = "nfc_payment_default_component";

    private static final GetWalletCardsRequest GET_WALLET_CARDS_REQUEST =
            new GetWalletCardsRequest(700, 440, 64, 5);

    private static final String SETTING_DISABLED = "0";
    private static final String SETTING_ENABLED = "1";

    private final Context mContext = InstrumentationRegistry.getInstrumentation()
            .getTargetContext();
    private final UserSettings mUserSettings = new UserSettings(mContext);

    private String mDefaultPaymentApp;

    @Before
    public void setUp() throws Exception {
        // Save current default payment app
        mDefaultPaymentApp = mUserSettings.get(NFC_PAYMENT_DEFAULT_COMPONENT);
        ComponentName component =
                ComponentName.createRelative(mContext, TestHostApduService.class.getName());
        // setDefaultWalletRoleHolder only succeeds if the Flag is enabled. If it fails, indicating
        // that the flag is not enabled, we set the default payment component and test the flow
        // without the wallet role.
        if (!setDefaultWalletRoleHolder(mContext, component.getPackageName())) {
            mUserSettings.syncSet(NFC_PAYMENT_DEFAULT_COMPONENT, component.flattenToString());
        }
        TestQuickAccessWalletService.resetStaticFields();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(MANAGE_ROLE_HOLDERS);
    }

    @After
    public void tearDown() throws InterruptedException {
        // Restore saved default payment app
        mUserSettings.syncSet(NFC_PAYMENT_DEFAULT_COMPONENT, mDefaultPaymentApp);

        // Return all services to default state
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        setServiceState(NoPermissionQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        setServiceState(QuickAccessWalletDelegateTargetActivityService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        TestQuickAccessWalletService.resetStaticFields();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testIsWalletServiceAvailable_returnsFalseIfNoServiceAvailable() {
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        assertThat(client.isWalletServiceAvailable()).isFalse();
    }

    @Test
    public void testIsWalletFeatureAvailableWhenDeviceLocked_checksSecureSettings() {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        String showCardsAndPasses =
                mUserSettings.get(Settings.Secure.POWER_MENU_LOCKED_SHOW_CONTENT);

        try {
            mUserSettings.syncSet(SETTING_KEY, SETTING_ENABLED);
            assertThat(client.isWalletFeatureAvailableWhenDeviceLocked()).isTrue();

            mUserSettings.syncSet(SETTING_KEY, SETTING_DISABLED);
            assertThat(client.isWalletFeatureAvailableWhenDeviceLocked()).isFalse();
        } finally {
            // return setting to original value
            mUserSettings.syncSet(SETTING_KEY, showCardsAndPasses);
        }
    }

    @Test
    public void testGetWalletPendingIntent_serviceWithOverride_notNull_ableToSend()
            throws Exception {
        setServiceState(QuickAccessWalletDelegateTargetActivityService.class,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);

        TestPendingIntentListener testPendingIntentListener = new TestPendingIntentListener();

        client.getWalletPendingIntent(mContext.getMainExecutor(), testPendingIntentListener);
        testPendingIntentListener.await(3, TimeUnit.SECONDS);
        assertThat(testPendingIntentListener.mPendingIntent).isNotNull();

        TestPendingIntentSentListener intentSentListener = new TestPendingIntentSentListener();

        testPendingIntentListener.mPendingIntent.send(0, intentSentListener,
                new Handler(Looper.getMainLooper()));

        intentSentListener.await(3, TimeUnit.SECONDS);

        String targetActivityPackage = "android.sample.quickaccesswallet.app";
        String targetActivityComponent =
                targetActivityPackage + ".QuickAccessWalletDelegateTargetActivity";

        assertThat(intentSentListener.mIntent).isNotNull();
        assertThat(intentSentListener.mIntent.getComponent())
                .isEqualTo(new ComponentName(targetActivityPackage, targetActivityComponent));


    }

    @Test
    public void testGetWalletPendingIntent_serviceWithNoOverride_isNull() throws Exception {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);

        TestPendingIntentListener testPendingIntentListener = new TestPendingIntentListener();

        client.getWalletPendingIntent(mContext.getMainExecutor(), testPendingIntentListener);
        testPendingIntentListener.await(3, TimeUnit.SECONDS);
        assertThat(testPendingIntentListener.mPendingIntent).isNull();
    }

    @Test
    public void testGetWalletCards_success() throws Exception {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        assertThat(client.isWalletServiceAvailable()).isTrue();
        TestCallback callback = new TestCallback();

        client.getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNotNull();
        assertThat(callback.mResponse.getWalletCards()).hasSize(1);
        assertThat(callback.mError).isNull();
    }

    @Test
    public void testGetWalletCards_failsIfNoServiceAvailable() throws Exception {
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        assertThat(client.isWalletServiceAvailable()).isFalse();
        TestCallback callback = new TestCallback();

        client.getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNull();
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_failsIfServiceDoesNotRequirePermission() throws Exception {
        setServiceState(NoPermissionQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        setServiceState(TestQuickAccessWalletService.class,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        assertThat(client.isWalletServiceAvailable()).isFalse();
        TestCallback callback = new TestCallback();

        client.getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNull();
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidCard_nullId_fails() throws Exception {
        WalletCard card = new WalletCard.Builder(null, createCardImage(), "Card",
                createPendingIntent()).build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidCard_nullImage_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", null, "Card",
                createPendingIntent()).build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidCard_nullContentDesc_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", createCardImage(), null,
                createPendingIntent()).build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);
        callback.await(3, TimeUnit.SECONDS);

        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidCard_nullPendingIntent_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", createCardImage(), "card", null).build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidResponse_tooManyCards_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", createCardImage(), "card",
                createPendingIntent()).build();
        List<WalletCard> walletCards = Arrays.asList(card, card, card, card, card, card, card);
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(walletCards, 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_zeroCards_isAllowed() throws Exception {
        List<WalletCard> walletCards = Collections.emptyList();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(walletCards, 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidResponse_nullCards_fails() throws Exception {
        TestQuickAccessWalletService.setWalletCardsResponse(new GetWalletCardsResponse(null, 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidResponse_negativeIndex_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", createCardImage(), "card",
                createPendingIntent()).build();
        List<WalletCard> walletCards = Arrays.asList(card, card, card);
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(walletCards, -1));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_invalidResponse_indexOutOfBounds_fails() throws Exception {
        WalletCard card = new WalletCard.Builder("cardId", createCardImage(), "card",
                createPendingIntent()).build();
        List<WalletCard> walletCards = Arrays.asList(card, card, card);
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(walletCards, 3));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);
        callback.await(3, TimeUnit.SECONDS);

        assertThat(callback.mError).isNotNull();
    }

    @Test
    public void testGetWalletCards_locationFeatureEnabled_includesLocations() throws Exception {
        assumeTrue(
                mContext.getPackageManager()
                        .hasSystemFeature(
                                PackageManager.FEATURE_WALLET_LOCATION_BASED_SUGGESTIONS));
        WalletCard card =
                new WalletCard.Builder("cardId", createCardImage(), "Card", createPendingIntent())
                        .setCardLocations(List.of(new Location("test")))
                        .build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNotNull();
        assertThat(callback.mResponse.getWalletCards()).hasSize(1);
        assertThat(callback.mResponse.getWalletCards().get(0).getCardLocations()).hasSize(1);
    }

    @Test
    public void testGetWalletCards_locationFeatureDisabled_excludesLocations() throws Exception {
        assumeFalse(
                mContext.getPackageManager()
                        .hasSystemFeature(
                                PackageManager.FEATURE_WALLET_LOCATION_BASED_SUGGESTIONS));
        WalletCard card =
                new WalletCard.Builder("cardId", createCardImage(), "Card", createPendingIntent())
                        .setCardLocations(List.of(new Location("test")))
                        .build();
        TestQuickAccessWalletService.setWalletCardsResponse(
                new GetWalletCardsResponse(Collections.singletonList(card), 0));
        TestCallback callback = new TestCallback();

        QuickAccessWalletClient.create(mContext).getWalletCards(GET_WALLET_CARDS_REQUEST, callback);

        callback.await(3, TimeUnit.SECONDS);
        assertThat(callback.mResponse).isNotNull();
        assertThat(callback.mResponse.getWalletCards()).hasSize(1);
        assertThat(callback.mResponse.getWalletCards().get(0).getCardLocations()).isEmpty();
    }

    @Test
    public void testSelectWalletCard_success() throws Exception {
        TestQuickAccessWalletService.setExpectedRequestCount(1);
        QuickAccessWalletClient.create(mContext)
                .selectWalletCard(new SelectWalletCardRequest("card1"));
        TestQuickAccessWalletService.awaitRequests(3, TimeUnit.SECONDS);

        List<SelectWalletCardRequest> selectRequests =
                TestQuickAccessWalletService.getSelectRequests();

        assertThat(selectRequests).hasSize(1);
        assertThat(selectRequests.get(0).getCardId()).isEqualTo("card1");
    }

    @Test
    public void testNotifyWalletDismissed_success() throws Exception {
        assertThat(TestQuickAccessWalletService.isWalletDismissed()).isFalse();
        TestQuickAccessWalletService.setExpectedRequestCount(1);

        QuickAccessWalletClient.create(mContext).notifyWalletDismissed();
        TestQuickAccessWalletService.awaitRequests(3, TimeUnit.SECONDS);

        assertThat(TestQuickAccessWalletService.isWalletDismissed()).isTrue();
    }

    @Test
    public void testAddListener_sendEvent_success() throws Exception {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        TestEventListener listener = new TestEventListener();
        TestQuickAccessWalletService.setExpectedRequestCount(1);
        client.addWalletServiceEventListener(listener);
        TestQuickAccessWalletService.awaitRequests(1, TimeUnit.SECONDS);

        TestQuickAccessWalletService.sendEvent(
                new WalletServiceEvent(WalletServiceEvent.TYPE_NFC_PAYMENT_STARTED));
        listener.await(300, TimeUnit.SECONDS);

        assertThat(listener.mEvents).hasSize(1);
    }

    @Test
    public void testRemoveListener_sendEvent_shouldNotBeDelivered() {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        TestEventListener listener = new TestEventListener();
        client.addWalletServiceEventListener(listener);
        client.removeWalletServiceEventListener(listener);

        TestQuickAccessWalletService.sendEvent(
                new WalletServiceEvent(WalletServiceEvent.TYPE_NFC_PAYMENT_STARTED));
        try {
            listener.await(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            // It is expected that this time out
        }

        assertThat(listener.mEvents).hasSize(0);
    }

    @Test
    public void testDisconnect_shouldClearListenersAndDisconnect() throws Exception {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);
        TestQuickAccessWalletService.setExpectedBindCount(1);
        TestQuickAccessWalletService.setExpectedUnbindCount(1);

        client.getWalletCards(GET_WALLET_CARDS_REQUEST, new TestCallback());

        TestQuickAccessWalletService.awaitBinding(3, TimeUnit.SECONDS);
        client.disconnect();
        TestQuickAccessWalletService.awaitUnbinding(3, TimeUnit.SECONDS);
    }

    @Test
    public void testConnect_disconnect_reconnect_shouldWork() throws Exception {
        QuickAccessWalletClient client = QuickAccessWalletClient.create(mContext);

        TestCallback callback = new TestCallback();
        client.getWalletCards(GET_WALLET_CARDS_REQUEST, callback);
        callback.await(3, TimeUnit.SECONDS);

        TestQuickAccessWalletService.setExpectedUnbindCount(1);
        client.disconnect();
        TestQuickAccessWalletService.awaitUnbinding(3, TimeUnit.SECONDS);

        TestCallback callback2 = new TestCallback();
        client.getWalletCards(GET_WALLET_CARDS_REQUEST, callback2);
        callback2.await(3, TimeUnit.SECONDS);

        assertThat(callback.mResponse).isNotNull();
        assertThat(callback2.mResponse).isNotNull();
    }

    @Test
    public void testCreateWalletIntent_parsesXmlAndUsesCorrectIntentAction() {
        Intent walletIntent = QuickAccessWalletClient.create(mContext).createWalletIntent();
        assertThat(walletIntent).isNotNull();
        assertThat(walletIntent.getAction()).isEqualTo(QuickAccessWalletService.ACTION_VIEW_WALLET);
        ComponentName expectedComponent = ComponentName.createRelative(mContext,
                QuickAccessWalletActivity.class.getName());
        assertThat(walletIntent.getComponent()).isEqualTo(expectedComponent);
    }

    @Test
    public void testCreateWalletSettingsIntent_usesSettingsActionToFindAppropriateActivity() {
        Intent settingsIntent =
                QuickAccessWalletClient.create(mContext).createWalletSettingsIntent();
        assertThat(settingsIntent).isNotNull();
        assertThat(settingsIntent.getAction()).isEqualTo(
                QuickAccessWalletService.ACTION_VIEW_WALLET_SETTINGS);
        ComponentName expectedComponent = ComponentName.createRelative(mContext,
                QuickAccessWalletSettingsActivity.class.getName());
        assertThat(settingsIntent.getComponent()).isEqualTo(expectedComponent);
    }

    private void setServiceState(
            Class<? extends QuickAccessWalletService> cls, int state) {
        ComponentName componentName = ComponentName.createRelative(mContext, cls.getName());
        mContext.getPackageManager().setComponentEnabledSetting(
                componentName, state, PackageManager.DONT_KILL_APP);
    }

    private Icon createCardImage() {
        Bitmap bitmap = Bitmap.createBitmap(
                GET_WALLET_CARDS_REQUEST.getCardWidthPx(),
                GET_WALLET_CARDS_REQUEST.getCardHeightPx(),
                Bitmap.Config.ARGB_8888);
        return Icon.createWithBitmap(bitmap.copy(Bitmap.Config.HARDWARE, false));
    }

    private PendingIntent createPendingIntent() {
        Intent intent = new Intent(mContext, QuickAccessWalletActivity.class);
        return PendingIntent
                .getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private static class TestCallback implements
            QuickAccessWalletClient.OnWalletCardsRetrievedCallback {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private GetWalletCardsResponse mResponse;
        private GetWalletCardsError mError;

        @Override
        public void onWalletCardsRetrieved(GetWalletCardsResponse response) {
            mResponse = response;
            mLatch.countDown();
        }

        @Override
        public void onWalletCardRetrievalError(GetWalletCardsError error) {
            mError = error;
            mLatch.countDown();
        }

        public void await(int time, TimeUnit unit) throws InterruptedException {
            mLatch.await(time, unit);
        }
    }

    private static class TestEventListener implements WalletServiceEventListener {

        private final List<WalletServiceEvent> mEvents = new ArrayList<>();
        private final CountDownLatch mLatch = new CountDownLatch(1);


        @Override
        public void onWalletServiceEvent(WalletServiceEvent event) {
            mEvents.add(event);
            mLatch.countDown();
        }

        public void await(int time, TimeUnit unit) throws InterruptedException {
            mLatch.await(time, unit);
        }
    }

    private static class TestPendingIntentListener implements WalletPendingIntentCallback {

        private PendingIntent mPendingIntent;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onWalletPendingIntentRetrieved(@Nullable PendingIntent walletPendingIntent) {
            mPendingIntent = walletPendingIntent;
            mLatch.countDown();
        }

        public void await(int time, TimeUnit unit) throws InterruptedException {
            mLatch.await(time, unit);
        }
    }

    private static class TestPendingIntentSentListener implements PendingIntent.OnFinished {

        private Intent mIntent;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            mIntent = intent;
            mLatch.countDown();
        }

        public void await(int time, TimeUnit unit) throws InterruptedException {
            mLatch.await(time, unit);
        }
    }


    private static boolean setDefaultWalletRoleHolder(Context context, String packageName)
            throws InterruptedException {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().adoptShellPermissionIdentity(MANAGE_DEFAULT_APPLICATIONS);
        try {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_WALLET)) {
                return false;
            }
            CountDownLatch countDownLatch = new CountDownLatch(1);
            AtomicReference<Boolean> result = new AtomicReference<>(false);
            roleManager.setDefaultApplication(RoleManager.ROLE_WALLET,
                    packageName, 0,
                    MoreExecutors.directExecutor(), aBoolean -> {
                        result.set(aBoolean);
                        countDownLatch.countDown();
                    });
            countDownLatch.await(2000, TimeUnit.MILLISECONDS);
            return result.get();
        } finally {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().dropShellPermissionIdentity();
        }
    }
}
