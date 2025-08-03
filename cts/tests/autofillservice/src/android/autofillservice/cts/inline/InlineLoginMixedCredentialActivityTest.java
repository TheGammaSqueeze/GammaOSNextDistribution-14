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

package android.autofillservice.cts.inline;

import static android.autofillservice.cts.testcore.Helper.ID_CARD_NUMBER;
import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.disablePccDetectionFeature;
import static android.autofillservice.cts.testcore.InstrumentedAutoFillServiceInlineEnabled.SERVICE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.autofillservice.cts.commontests.AbstractMixedCredentialLoginActivityTestCase;
import android.autofillservice.cts.credman.CtsCredentialProviderService;
import android.autofillservice.cts.credman.DeviceConfigStateRequiredRule;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.Helper;
import android.autofillservice.cts.testcore.InlineUiBot;
import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.CredentialManager;
import android.credentials.CredentialProviderInfo;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.Timeout;
import com.android.compatibility.common.util.UserSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@AppModeFull(reason = "Permission and providers are not available on instant mode.")
public class InlineLoginMixedCredentialActivityTest
        extends AbstractMixedCredentialLoginActivityTestCase {

    private static final String TAG = "LoginMixedCredentialActivityInlineTest";
    private static final String CTS_SERVICE_NAME =
            "android.autofillservice.cts/android.autofillservice.cts.credman."
                    + "CtsCredentialProviderService";
    private static final String PASSWORD_CREDENTIAL_TYPE =
            "android.credentials.TYPE_PASSWORD_CREDENTIAL";
    private static final String PASSKEY_CREDENTIAL_TYPE =
            "android.credentials.TYPE_PUBLIC_KEY_CREDENTIAL";
    private static final String CREDENTIAL_SERVICE = "credential_service";
    private static final String CREDENTIAL_SERVICE_PRIMARY = "credential_service_primary";
    private static final Timeout CONNECTION_TIMEOUT =
            new Timeout("CONNECTION_TIMEOUT", 1500, 2F, 1500);
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";

    private CredentialManager mCredentialManager;
    private final UserSettings mUserSettings = new UserSettings(mContext);

    // Sets up the feature flag rule and the device flag rule
    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(
                    DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL,
                    mContext,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Log.i(TAG, "Enabling service from scratch for " + CTS_SERVICE_NAME);
        Log.i(TAG, "Enabling CredentialManager flags as well...");
        enableCredentialManagerDeviceFeature(mContext);
        mCredentialManager = mContext.getSystemService(CredentialManager.class);
        assertThat(mCredentialManager).isNotNull();
        assumeTrue("VERSION.SDK_INT=" + Build.VERSION.SDK_INT, BuildCompat.isAtLeastU());
        clearAllTestCredentialProviderServices();
        bindToTestService();
    }

    @After
    public void tearDown() {
        Log.i(TAG, "Disabling credman services and device feature flag");
        clearAllTestCredentialProviderServices();
    }

    @Override
    protected void enableService() {
        Helper.enableAutofillService(SERVICE_NAME);
    }

    public InlineLoginMixedCredentialActivityTest() {
        super(getInlineUiBot());
    }

    @Override
    protected boolean isInlineMode() {
        return true;
    }

    @Override
    public TestRule getMainTestRule() {
        return InlineUiBot.annotateRule(super.getMainTestRule());
    }

    @After
    public void disablePcc() {
        Log.d(TAG, "@After: disablePcc()");
        disablePccDetectionFeature(sContext);
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testCredmanProxyServiceIsNotPublic() throws Exception {
        // Set service.
        enableService();

        // Verify during initial setup.
        assertThat(readServiceNameList())
                .asList()
                .containsExactly("android.autofillservice.cts/.testcore."
                        + "InstrumentedAutoFillServiceInlineEnabled");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();

        // Verify after credman service is invoked.
        assertThat(readServiceNameList())
                .asList()
                .containsExactly("android.autofillservice.cts/.testcore."
                        + "InstrumentedAutoFillServiceInlineEnabled");
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_selectNonCredentialView() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_CARD_NUMBER, "1111-1111-1111-1111")
                        .setPresentation(createPresentation("The Credit"))
                        .setInlinePresentation(createInlinePresentation("The Credit"))
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectCreditCardAutoFill("1111-1111-1111-1111");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.assertDatasets("The Credit");

        mUiBot.selectDataset("The Credit");
        mUiBot.waitForIdleSync();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_selectCredentialView() throws Exception {
        // Set service.
        enableService();

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();

        mUiBot.assertDatasets("defaultUsername");

        mUiBot.selectDataset("defaultUsername");
        mUiBot.waitForIdleSync();

        // TODO(b/314195644): Verify that credential dataset is autofilled through autofill value.
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_selectNonCredentialThenCredential() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_CARD_NUMBER, "1111-1111-1111-1111")
                        .setPresentation(createPresentation("The Credit"))
                        .setInlinePresentation(createInlinePresentation("The Credit"))
                        .build());
        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();

        mUiBot.assertDatasets("defaultUsername");
        mUiBot.assertNotShowingForSure("The Username");

        mUiBot.selectDataset("defaultUsername");
        mUiBot.waitForIdleSync();

        // TODO(b/314195644): Verify that credential dataset is autofilled through autofill value.
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_selectCredentialThenNonCredential() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_CARD_NUMBER, "1111-1111-1111-1111")
                        .setPresentation(createPresentation("The Credit"))
                        .setInlinePresentation(createInlinePresentation("The Credit"))
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectCreditCardAutoFill("1111-1111-1111-1111");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();

        mUiBot.assertDatasets("The Credit");

        mUiBot.selectDataset("The Credit");
        mUiBot.waitForIdleSync();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_startAndEndWithCredential() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_CARD_NUMBER, "1111-1111-1111-1111")
                        .setPresentation(createPresentation("The Credit"))
                        .setInlinePresentation(createInlinePresentation("The Credit"))
                        .build());
        sReplier.addResponse(builder.build());

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("defaultUsername");
        mUiBot.assertNotShowingForSure("The Username");

        mUiBot.selectDataset("defaultUsername");
        mUiBot.waitForIdleSync();

        // TODO(b/314195644): Verify that credential dataset is autofilled through autofill value.
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void testAutofill_startAndEndWithNonCredential() throws Exception {
        // Set service.
        enableService();

        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setPresentation(createPresentation("The Username"))
                        .setInlinePresentation(createInlinePresentation("The Username"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("The Password"))
                        .setInlinePresentation(createInlinePresentation("The Password"))
                        .build())
                .addDataset(new CannedFillResponse.CannedDataset.Builder()
                        .setField(ID_CARD_NUMBER, "1111-1111-1111-1111")
                        .setPresentation(createPresentation("The Credit"))
                        .setInlinePresentation(createInlinePresentation("The Credit"))
                        .build());
        sReplier.addResponse(builder.build());
        mActivity.expectCreditCardAutoFill("1111-1111-1111-1111");

        // Trigger auto-fill.
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.selectByRelativeId(ID_USERNAME);
        mUiBot.waitForIdleSync();
        CtsCredentialProviderService.onReceivedResponse();
        mUiBot.selectByRelativeId(ID_CARD_NUMBER);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("The Credit");

        mUiBot.selectDataset("The Credit");
        mUiBot.waitForIdleSync();

        // Check the results.
        mActivity.assertAutoFilled();
    }

    @Test
    @RequiresFlagsEnabled("android.service.autofill.autofill_credman_integration")
    public void getCredentialProviderServices_returnsAllProviders() {
        Map<String, CredentialProviderInfo> results =
                getCredentialProviderServices(
                        CredentialManager.PROVIDER_FILTER_ALL_PROVIDERS);

        // Verify data of the returned provider.
        assertThat(results).containsKey(CTS_SERVICE_NAME);
        CredentialProviderInfo cpi = results.get(CTS_SERVICE_NAME);
        assertThat(cpi).isNotNull();
        assertThat(cpi.isSystemProvider()).isFalse();
        assertThat(cpi.getLabel(mContext).toString())
                .isEqualTo("Test Provider Service");
        assertThat(cpi.getSettingsSubtitle().toString())
                .isEqualTo("This is a subtitle");
        assertThat(cpi.getCapabilities())
                .containsExactly(PASSWORD_CREDENTIAL_TYPE, PASSKEY_CREDENTIAL_TYPE);
    }

    /**
     * Enable the main credential manager feature. If this is off, any underlying changes for
     * autofill-credentialManager integrations are off.
     */
    private static void enableCredentialManagerDeviceFeature(@NonNull Context context) {
        setCredentialManagerFeature(context, true);
    }

    /** Enable Credential Manager related autofill changes */
    private static void setCredentialManagerFeature(@NonNull Context context, boolean enabled) {
        setDeviceConfig(context, DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER, enabled);
    }

    /** Set device config to set flag values. */
    private static void setDeviceConfig(
            @NonNull Context context, @NonNull String feature, boolean value) {
        DeviceConfigStateManager deviceConfigStateManager =
                new DeviceConfigStateManager(context, DeviceConfig.NAMESPACE_CREDENTIAL, feature);
        setDeviceConfig(deviceConfigStateManager, String.valueOf(value));
    }

    /** Set device config. */
    private static void setDeviceConfig(
            @NonNull DeviceConfigStateManager deviceConfigStateManager, @Nullable String value) {
        final String previousValue = deviceConfigStateManager.get();
        if ((TextUtils.isEmpty(value) && TextUtils.isEmpty(previousValue))
                || TextUtils.equals(previousValue, value)) {
            Log.v(TAG, "No changed in config: " + deviceConfigStateManager);
            return;
        }
        deviceConfigStateManager.set(value);
    }

    private void bindToTestService() {
        // On Manager, bind to test service
        setTestableCredentialProviderService(CTS_SERVICE_NAME);
        assertTrue(isCredentialProviderServiceEnabled(CTS_SERVICE_NAME));
    }

    private void clearAllTestCredentialProviderServices() {
        mUserSettings.syncSet(CREDENTIAL_SERVICE, null);
        mUserSettings.syncSet(CREDENTIAL_SERVICE_PRIMARY, null);
    }

    private boolean isCredentialProviderServiceEnabled(String serviceName) {
        return isCredentialProviderServiceEnabledInternal(serviceName, CREDENTIAL_SERVICE);
    }

    private void setTestableCredentialProviderService(@NonNull String serviceName) {
        setTestableCredentialProviderServiceInternal(serviceName, CREDENTIAL_SERVICE);
    }

    private void setTestableCredentialProviderServiceInternal(@NonNull String serviceName,
                                                              @NonNull String key) {
        if (isCredentialProviderServiceEnabledInternal(serviceName, key)) return;

        String settingOutput = readCredentialManagerProviderSetting(key);
        settingOutput = settingOutput == null ? "" : settingOutput;
        if (settingOutput.length() > 0) {
            settingOutput += ";" + serviceName;
        } else {
            settingOutput += serviceName;
        }
        // Guaranteed to not be null now since the NoOp service exists at a minimum
        Log.i(TAG, "Attempting to set services: " + settingOutput);
        mUserSettings.syncSet(key, settingOutput);

        // Waits until the service is actually enabled.
        try {
            CONNECTION_TIMEOUT.run(
                    "Checking if service enabled",
                    () -> isCredentialProviderServiceEnabledInternal(serviceName, key));
        } catch (Exception e) {
            Log.i(TAG, "Failure... " + e.getLocalizedMessage());
            throw new AssertionError("Enabling Credman service failed.");
        }
    }

    private boolean isCredentialProviderServiceEnabledInternal(String serviceName, String key) {
        final String actualNames = readCredentialManagerProviderSetting(key);
        Log.i(
                TAG,
                "actual names in setting: "
                        + actualNames
                        + " ,serviceName being "
                        + "checked : "
                        + serviceName);
        if (actualNames == null) {
            return false;
        }
        return containsName(actualNames, serviceName);
    }

    private boolean containsName(@NonNull String serviceNames, @NonNull String name) {
        Set<String> services = new LinkedHashSet<>(List.of(serviceNames.split(";")));
        return services.contains(name);
    }

    /** Gets then name of the credential service for the default user. */
    private String readCredentialManagerProviderSetting(String key) {
        return mUserSettings.get(key);
    }

    private Map<String, CredentialProviderInfo> getCredentialProviderServices(int providerFilter) {
        return mCredentialManager.getCredentialProviderServicesForTesting(providerFilter).stream()
                .collect(Collectors.toMap(c -> c.getComponentName().flattenToString(), c -> c));
    }

    private String[] readServiceNameList() {
        return parseColonDelimitedServiceNames(
                Settings.Secure.getString(
                        mContext.getContentResolver(), Settings.Secure.AUTOFILL_SERVICE));
    }

    private String[] parseColonDelimitedServiceNames(String serviceNames) {
        final Set<String> delimitedServices = new ArraySet<>();
        if (!TextUtils.isEmpty(serviceNames)) {
            final TextUtils.SimpleStringSplitter splitter =
                    new TextUtils.SimpleStringSplitter(':');
            splitter.setString(serviceNames);
            while (splitter.hasNext()) {
                final String str = splitter.next();
                if (TextUtils.isEmpty(str)) {
                    continue;
                }
                delimitedServices.add(str);
            }
        }
        String[] delimitedServicesArray = new String[delimitedServices.size()];
        return delimitedServices.toArray(delimitedServicesArray);
    }

}
