/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.PreferentialNetworkServiceConfig;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.os.HandlerExecutor;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Range;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.PreferentialNetworkService;
import com.android.bedstead.nene.TestApis;
import com.android.testutils.TestableNetworkCallback;
import com.android.testutils.TestableNetworkOfferCallback;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// TODO(b/190797743): Move this test to to net test folder.
@RunWith(BedsteadJUnit4.class)
public final class PreferentialNetworkServiceTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private final long DEFAULT_TIMEOUT_MS = 30_000L;
    private final long NO_CALLBACK_TIMEOUT_MS = 100L;
    private final String TAG = PreferentialNetworkServiceTest.class.getSimpleName();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ConnectivityManager sCm =
            sContext.getSystemService(ConnectivityManager.class);
    private final HandlerThread mHandlerThread = new HandlerThread(TAG + " handler thread");
    private final NetworkCapabilities mEnterpriseNcFilter = new NetworkCapabilities.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
            .addCapability(NET_CAPABILITY_ENTERPRISE)
            .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
            // Only accept requests from this uid, otherwise the provider who uses this
            // filter might see all requests of the same user if the feature is enabled.
            .setUids(Set.of(new Range(Process.myUid(), Process.myUid())))
            .build();

    private static final PreferentialNetworkServiceConfig ENABLED_CONFIG =
            (new PreferentialNetworkServiceConfig.Builder())
                    .setEnabled(true)
                    .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                    .build();

    @Before
    public void setUp() throws Exception {
        mHandlerThread.start();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
    }

    /**
     * Make sure both including and excluding UIDs from a policy is disallowed.
     */
    @Test
    public void buildPreferentialNetworkServiceConfig_includedAndExcluded() {
        // May not set both included and excluded UIDs
        assertThrows(IllegalStateException.class, () ->
                new PreferentialNetworkServiceConfig.Builder()
                        .setIncludedUids(new int[] { 1001, 1002 })
                        .setExcludedUids(new int[] { 1003, 1004 })
                        .build());
    }

    /**
     * Make sure setting fallback allowed and blocking together is disallowed.
     */
    @Test
    public void buildPreferentialNetworkServiceConfig_fallbackAndBlock() {
        // May not set block non-matching networks and fallback to default connection at the same
        // time, because this makes no sense when there is no preferential network service available
        assertThrows(IllegalStateException.class, () ->
                new PreferentialNetworkServiceConfig.Builder()
                        .setFallbackToDefaultConnectionAllowed(true)
                        .setShouldBlockNonMatchingNetworks(true)
                        .build());
    }

    /**
     * Enable PreferentialNetworkService, verify the provider that provides enterprise slice can
     * see the enterprise slice requests.
     */
    @EnsureHasPermission({ACCESS_NETWORK_STATE, NETWORK_SETTINGS})
    @PolicyAppliesTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceEnabled_enableService_issueRequest() {
        // Expect a regular default network.
        final Network defaultNetwork = Objects.requireNonNull(sCm.getActiveNetwork(),
                "Default network is required to perform the test.");
        final TestableNetworkCallback defaultCallback = new TestableNetworkCallback();
        sCm.registerDefaultNetworkCallback(defaultCallback);
        defaultCallback.expectAvailableCallbacks(defaultNetwork, /* suspended= */ false,
                /* validated= */ true, /* blocked= */ false, DEFAULT_TIMEOUT_MS);

        // Register a pair of provider and offer that can provides enterprise slice, verify it
        // received nothing since the feature is not enabled.
        final NetworkProvider provider =
                new NetworkProvider(sContext, mHandlerThread.getLooper(), TAG);
        sCm.registerNetworkProvider(provider);
        final TestableNetworkOfferCallback offerCallback = registerEnterpriseNetworkOffer(provider);

        try {
            // Enable PreferentialNetworkService, verify the provider sees the enterprise
            // slice request.
            // But the network callback received nothing since it should automatically fallback to
            // default request if there is no enterprise slice.
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceEnabled(true);
            offerCallback.expectOnNetworkNeeded(mEnterpriseNcFilter);
            defaultCallback.assertNoCallback(NO_CALLBACK_TIMEOUT_MS);
        } finally {
            sCm.unregisterNetworkCallback(defaultCallback);
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceEnabled(false);
            sCm.unregisterNetworkProvider(provider);
        }
    }

    /**
     * Disable PreferentialNetworkService, verify the provider that provides enterprise slice cannot
     * see the enterprise slice requests.
     */
    @EnsureHasPermission({ACCESS_NETWORK_STATE, NETWORK_SETTINGS})
    @PolicyAppliesTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceEnabled_disableService_noIssueRequest() {
        // Expect a regular default network.
        final Network defaultNetwork = Objects.requireNonNull(sCm.getActiveNetwork(),
                "Default network is required to perform the test.");
        final TestableNetworkCallback defaultCallback = new TestableNetworkCallback();
        sCm.registerDefaultNetworkCallback(defaultCallback);
        defaultCallback.expectAvailableCallbacks(defaultNetwork, /* suspended= */ false,
                /* validated= */ true, /* blocked= */ false, DEFAULT_TIMEOUT_MS);

        // Register a pair of provider and offer that can provides enterprise slice, verify it
        // received nothing since the feature is not enabled.
        final NetworkProvider provider =
                new NetworkProvider(sContext, mHandlerThread.getLooper(), TAG);
        sCm.registerNetworkProvider(provider);
        final TestableNetworkOfferCallback offerCallback = registerEnterpriseNetworkOffer(provider);

        try {
            // Disable PreferentialNetworkService, verify the provider cannot see the enterprise
            // slice request. And the network callback received nothing since there is no any
            // change.
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceEnabled(false);
            offerCallback.assertNoCallback();  // Still unneeded.
            defaultCallback.assertNoCallback(NO_CALLBACK_TIMEOUT_MS);
        } finally {
            sCm.unregisterNetworkCallback(defaultCallback);
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceEnabled(false);
            sCm.unregisterNetworkProvider(provider);
        }
    }

    /**
     * Enable PreferentialNetworkService with blocking non-enterprise network feature enabled,
     * verify the application cannot access non-enterprise network.
     */
    @EnsureHasPermission({ACCESS_NETWORK_STATE, NETWORK_SETTINGS})
    @PolicyAppliesTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceEnabled_blockingNonEnterprise_noAccess()
            throws Exception {
        // Expect a regular default network.
        final Network defaultNetwork = Objects.requireNonNull(sCm.getActiveNetwork(),
                "Default network is required to perform the test.");
        final TestableNetworkCallback defaultCallback = new TestableNetworkCallback();
        sCm.registerDefaultNetworkCallback(defaultCallback);
        defaultCallback.expectAvailableCallbacks(defaultNetwork, /* suspended= */ false,
                /* validated= */ true, /* blocked= */ false, DEFAULT_TIMEOUT_MS);

        // Verify the default network could be accessed.
        assertBindSocketToNetwork(true /* expectSuccess */, defaultNetwork);

        PreferentialNetworkServiceConfig blockConfig =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                        .setFallbackToDefaultConnectionAllowed(false)
                        .setShouldBlockNonMatchingNetworks(true)
                        .build();
        try {
            // Enable PreferentialNetworkService, with blocking non-enterprise network feature
            // enabled. The default network should be lost since the preference does not allow
            // fallback.
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(blockConfig));
            defaultCallback.expect(TestableNetworkCallback.CallbackEntry.LOST, DEFAULT_TIMEOUT_MS);

            // Verify the application cannot access default network  since it is
            // a non-enterprise network.
            assertBindSocketToNetwork(false /* expectSuccess */, defaultNetwork);
        } finally {
            sCm.unregisterNetworkCallback(defaultCallback);
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(PreferentialNetworkServiceConfig.DEFAULT));
        }
    }

    // TODO: Copied and modified from android.net.cts.ConnectivityManager. Need to refactor them
    //  to a common place.
    private void assertBindSocketToNetwork(boolean expectSuccess, final Network network)
            throws Exception {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                for (int i = 0; i < 300; i++) {
                    SystemClock.sleep(10);

                    try (Socket socket = new Socket()) {
                        network.bindSocket(socket);
                        if (expectSuccess) {
                            future.complete(true);
                            return;
                        }
                    } catch (IOException e) {
                        if (!expectSuccess) {
                            future.complete(false);
                            return;
                        }
                    }
                }
            });
            if (expectSuccess) {
                assertThat(future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
            } else {
                assertThat(future.get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isFalse();
            }
        } finally {
            executor.shutdown();
        }
    }

    @CannotSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceEnabled_enableService_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setPreferentialNetworkServiceEnabled(true));
    }

    @CannotSetPolicyTest(policy = PreferentialNetworkService.class)
    public void isPreferentialNetworkServiceEnabled_default_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .isPreferentialNetworkServiceEnabled());
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void isPreferentialNetworkServiceEnabled_default_isTrue() {
        assertThat(sDeviceState.dpc().devicePolicyManager().isPreferentialNetworkServiceEnabled())
                .isFalse();
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_enabled_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(ENABLED_CONFIG));

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(PreferentialNetworkServiceConfig.DEFAULT));
        }
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_fallback_isSet() {
        PreferentialNetworkServiceConfig fallbackConfig =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                        .setFallbackToDefaultConnectionAllowed(true)
                        .build();
        try {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(fallbackConfig));

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0)
                    .isFallbackToDefaultConnectionAllowed()).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(PreferentialNetworkServiceConfig.DEFAULT));
        }
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_block_isSet() {
        PreferentialNetworkServiceConfig blockConfig =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                        .setFallbackToDefaultConnectionAllowed(false)
                        .setShouldBlockNonMatchingNetworks(true)
                        .build();
        try {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(blockConfig));

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0)
                    .shouldBlockNonMatchingNetworks()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0)
                    .isFallbackToDefaultConnectionAllowed()).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(PreferentialNetworkServiceConfig.DEFAULT));
        }
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_enabled_isSet_excludedUids_set() {
        UserHandle user = UserHandle.of(sContext.getUserId());
        final int currentUid = user.getUid(0 /* appId */);
        PreferentialNetworkServiceConfig slice1Config =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                        .setExcludedUids(new int[]{currentUid})
                        .build();
        PreferentialNetworkServiceConfig slice2Config =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_2)
                        .setIncludedUids(new int[]{currentUid})
                        .build();
        try {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(slice1Config, slice2Config));

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(0)
                    .getExcludedUids()).isEqualTo(new int[]{currentUid});
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(1).isEnabled()).isTrue();
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getPreferentialNetworkServiceConfigs().get(1)
                    .getIncludedUids()).isEqualTo(new int[]{currentUid});
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                    List.of(PreferentialNetworkServiceConfig.DEFAULT));
        }
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPrefentialNetworkServiceConfigs_overlappingUids_throwsException() {
        UserHandle user = UserHandle.of(sContext.getUserId());
        final int currentUid = user.getUid(/* appId */ 0);
        PreferentialNetworkServiceConfig slice1Config =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_1)
                        .setIncludedUids(new int[]{currentUid})
                        .build();
        PreferentialNetworkServiceConfig slice2Config =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true)
                        .setNetworkId(PreferentialNetworkServiceConfig.PREFERENTIAL_NETWORK_ID_2)
                        .setIncludedUids(new int[]{currentUid})
                        .build();
        assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setPreferentialNetworkServiceConfigs(
                                List.of(slice1Config, slice2Config)));
        assertThat(sDeviceState.dpc().devicePolicyManager()
            .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isFalse();
    }

    @CanSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_default_isNotSet() {
        sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                List.of(PreferentialNetworkServiceConfig.DEFAULT));

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPreferentialNetworkServiceConfigs().get(0).isEnabled()).isFalse();
    }

    @CannotSetPolicyTest(policy = PreferentialNetworkService.class)
    public void setPreferentialNetworkServiceConfigs_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                        List.of(ENABLED_CONFIG)));
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setPreferentialNetworkServiceConfigs(
                        List.of(PreferentialNetworkServiceConfig.DEFAULT)));
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getPreferentialNetworkServiceConfigs());
    }

    private TestableNetworkOfferCallback registerEnterpriseNetworkOffer(
            NetworkProvider provider) {
        final TestableNetworkOfferCallback offerCallback =
                new TestableNetworkOfferCallback(DEFAULT_TIMEOUT_MS, NO_CALLBACK_TIMEOUT_MS);
        provider.registerNetworkOffer(new NetworkScore.Builder().build(), mEnterpriseNcFilter,
                new HandlerExecutor(mHandlerThread.getThreadHandler()), offerCallback);
        offerCallback.assertNoCallback();
        return offerCallback;
    }
}
