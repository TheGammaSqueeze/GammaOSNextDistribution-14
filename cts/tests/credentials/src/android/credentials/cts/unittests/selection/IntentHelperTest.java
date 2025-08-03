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

package android.credentials.cts.unittests.selection;

import static android.credentials.flags.Flags.FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.slice.Slice;
import android.content.Context;
import android.content.Intent;
import android.credentials.CreateCredentialRequest;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialRequest;
import android.credentials.selection.AuthenticationEntry;
import android.credentials.selection.CancelSelectionRequest;
import android.credentials.selection.CreateCredentialProviderData;
import android.credentials.selection.CreateCredentialProviderInfo;
import android.credentials.selection.DisabledProviderData;
import android.credentials.selection.DisabledProviderInfo;
import android.credentials.selection.Entry;
import android.credentials.selection.GetCredentialProviderData;
import android.credentials.selection.GetCredentialProviderInfo;
import android.credentials.selection.IntentFactory;
import android.credentials.selection.IntentHelper;
import android.credentials.selection.ProviderData;
import android.credentials.selection.RequestInfo;
import android.credentials.selection.RequestToken;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class IntentHelperTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final IBinder TOKEN = new Binder();
    private static final String PACKAGE_NAME = "mock_package";
    private static final String KEY = "key";
    private static final String SUBKEY = "subKey";
    private static final Slice SLICE = new Slice.Builder(Uri.parse("foo://bar"), null)
            .addText("some text", null, List.of(Slice.HINT_TITLE)).build();
    private static final int STATUS = 0;
    private static final Intent MOCK_INTENT = new Intent("mock_action");
    private static final Entry CREDENTIAL_ENTRY = new Entry(KEY, SUBKEY, SLICE, MOCK_INTENT);
    private static final AuthenticationEntry AUTHENTICATION_ENTRY =
            new AuthenticationEntry(KEY, SUBKEY, SLICE, STATUS, MOCK_INTENT);
    private static final String PROVIDER_NAME = "com.android.test/.MockProviderService";
    private static final List<Entry> ENTRY_LIST = List.of(CREDENTIAL_ENTRY);
    private static final List<Entry> SAVE_ENTRY_LIST = List.of(CREDENTIAL_ENTRY);
    private static final List<Entry> ACTION_LIST = List.of(CREDENTIAL_ENTRY);
    private static final List<AuthenticationEntry> AUTHENTICATION_ENTRY_LIST =
            List.of(AUTHENTICATION_ENTRY);
    private static final Entry REMOTE_ENTRY = new Entry(KEY, SUBKEY, SLICE, MOCK_INTENT);

    private final Context mContext = getInstrumentation().getContext();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractCancelUiRequest() {
        boolean shouldShowCancellationUi = true;
        Intent intent = IntentFactory.createCancelUiIntent(TOKEN, shouldShowCancellationUi,
                PACKAGE_NAME);

        CancelSelectionRequest cancelUiRequestExtracted = IntentHelper.extractCancelUiRequest(
                intent);

        assertThat(cancelUiRequestExtracted.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(cancelUiRequestExtracted.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(cancelUiRequestExtracted.shouldShowCancellationExplanation())
                .isEqualTo(shouldShowCancellationUi);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractRequestInfo_createRequest() {
        final String type = Credential.TYPE_PASSWORD_CREDENTIAL;
        final Bundle data = new Bundle();
        data.putString("foo", "bar");
        final List<String> defaultProviderIds = new ArrayList<>();
        defaultProviderIds.add("p1");
        defaultProviderIds.add("p2");
        defaultProviderIds.add("p3");
        final CreateCredentialRequest createRequest =
                new CreateCredentialRequest.Builder(type, data, data).build();
        RequestInfo requestInfo = RequestInfo.newCreateRequestInfo(
                TOKEN, createRequest, PACKAGE_NAME,
                /*hasPermissionToOverrideDefault=*/ false, defaultProviderIds,
                /*isShowAllOptionsRequested=*/ false
        );
        Intent intent = IntentFactory.createCredentialSelectorIntent(
                mContext, requestInfo, new ArrayList<>(),
                new ArrayList<>(), new ResultReceiver(null));

        RequestInfo requestInfoExtracted = IntentHelper.extractRequestInfo(intent);

        assertThat(requestInfoExtracted.getRequestToken()).isEqualTo(new RequestToken(TOKEN));
        assertThat(requestInfo.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(requestInfo.getType()).isEqualTo(RequestInfo.TYPE_CREATE);
        assertThat(requestInfo.getDefaultProviderIds()).containsExactlyElementsIn(
                defaultProviderIds).inOrder();
        assertThat(requestInfo.hasPermissionToOverrideDefault()).isFalse();
        assertThat(requestInfo.isShowAllOptionsRequested()).isFalse();
        assertThat(requestInfo.getCreateCredentialRequest()).isEqualTo(createRequest);
        assertThat(requestInfo.getGetCredentialRequest()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractRequestInfo_getRequest() {
        final Bundle data = new Bundle();
        final IBinder token = new Binder();
        data.putString("foo", "bar");
        final GetCredentialRequest getRequest =
                new GetCredentialRequest.Builder(data).addCredentialOption(
                        new CredentialOption.Builder("type", new Bundle(),
                                new Bundle()).build()).build();
        RequestInfo requestInfo = RequestInfo.newGetRequestInfo(
                token, getRequest, "package", /*hasPermissionToOverrideDefault=*/ false,
                /*isShowAllOptionsRequested=*/ false);
        Intent intent = IntentFactory.createCredentialSelectorIntent(
                mContext, requestInfo, new ArrayList<>(),
                new ArrayList<>(), new ResultReceiver(null));

        RequestInfo requestInfoExtracted = IntentHelper.extractRequestInfo(intent);

        assertThat(requestInfoExtracted.getRequestToken()).isEqualTo(new RequestToken(token));
        assertThat(requestInfo.getPackageName()).isEqualTo("package");
        assertThat(requestInfo.getType()).isEqualTo(RequestInfo.TYPE_GET);
        assertThat(requestInfo.getDefaultProviderIds()).isEmpty();
        assertThat(requestInfo.hasPermissionToOverrideDefault()).isFalse();
        assertThat(requestInfo.isShowAllOptionsRequested()).isFalse();
        assertThat(requestInfo.getCreateCredentialRequest()).isNull();
        assertThat(requestInfo.getGetCredentialRequest()).isEqualTo(getRequest);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractGetCredentialProviderDataList_nullProviderList() {
        // no provider list is provided in intent
        Intent intent = new Intent("mock_action");

        assertThat(IntentHelper.extractGetCredentialProviderInfoList(intent)).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractGetCredentialProviderDataList() {
        Intent intent = new Intent("mock_action");
        GetCredentialProviderData getCredentialProviderData =
                new GetCredentialProviderData(
                        PROVIDER_NAME,
                        ENTRY_LIST,
                        ACTION_LIST,
                        AUTHENTICATION_ENTRY_LIST,
                        REMOTE_ENTRY
                );
        intent.putParcelableArrayListExtra(ProviderData.EXTRA_ENABLED_PROVIDER_DATA_LIST,
                new ArrayList<>(Arrays.asList(getCredentialProviderData)));

        List<GetCredentialProviderInfo> getCredentialProviderInfoList =
                IntentHelper.extractGetCredentialProviderInfoList(intent);

        GetCredentialProviderInfo getCredentialProviderInfoExtracted =
                getCredentialProviderInfoList.get(0);
        assertThat(getCredentialProviderInfoExtracted.getProviderName()).isEqualTo(
                PROVIDER_NAME);
        assertThat(getCredentialProviderInfoExtracted.getCredentialEntries())
                .containsExactlyElementsIn(ENTRY_LIST);
        assertThat(getCredentialProviderInfoExtracted.getActionChips())
                .containsExactlyElementsIn(ACTION_LIST);
        assertThat(getCredentialProviderInfoExtracted.getRemoteEntry()).isEqualTo(REMOTE_ENTRY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractCreateCredentialProviderDataList() {
        Intent intent = new Intent("mock_action");
        CreateCredentialProviderData createCredentialProviderData =
                new CreateCredentialProviderData(
                        PROVIDER_NAME,
                        SAVE_ENTRY_LIST,
                        REMOTE_ENTRY
                );
        intent.putParcelableArrayListExtra(ProviderData.EXTRA_ENABLED_PROVIDER_DATA_LIST,
                new ArrayList<>(Arrays.asList(createCredentialProviderData)));

        List<CreateCredentialProviderInfo> createCredentialProviderInfoList =
                IntentHelper.extractCreateCredentialProviderInfoList(intent);
        CreateCredentialProviderInfo createCredentialProviderInfoExtracted =
                createCredentialProviderInfoList.get(0);

        assertThat(createCredentialProviderInfoExtracted.getProviderName())
                .isEqualTo(PROVIDER_NAME);
        assertThat(createCredentialProviderInfoExtracted.getSaveEntries())
                .containsExactlyElementsIn(SAVE_ENTRY_LIST);
        assertThat(createCredentialProviderInfoExtracted.getRemoteEntry()).isEqualTo(REMOTE_ENTRY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractDisabledProviderDataList() {
        Intent intent = new Intent("mock_action");
        DisabledProviderData disabledProviderData1 = new DisabledProviderData("com.android.pName1");
        DisabledProviderData disabledProviderData2 = new DisabledProviderData("com.pName2");
        intent.putParcelableArrayListExtra(ProviderData.EXTRA_DISABLED_PROVIDER_DATA_LIST,
                new ArrayList<>(Arrays.asList(disabledProviderData1, disabledProviderData2)));

        List<DisabledProviderInfo> disabledProviderInfoList =
                IntentHelper.extractDisabledProviderInfoList(intent);

        assertThat(disabledProviderInfoList).hasSize(2);
        assertThat(disabledProviderInfoList.get(0).getProviderName()).isEqualTo(
                "com.android.pName1");
        assertThat(disabledProviderInfoList.get(1).getProviderName()).isEqualTo("com.pName2");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void testExtractResultReceiver() {
        ResultReceiver resultReceiver = new ResultReceiver(null);
        Intent intent = new Intent("mock_action");
        intent.putExtra("android.credentials.selection.extra.RESULT_RECEIVER", resultReceiver);

        ResultReceiver actual = IntentHelper.extractResultReceiver(intent);

        assertThat(actual).isEqualTo(resultReceiver);
    }
}
