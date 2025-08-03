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

package android.view.inputmethod.cts.installtests;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import android.Manifest;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.inputmethodservice.cts.common.CommandProviderConstants;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.Ime2Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.CommonPackages;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@LargeTest
@RequireFeature(CommonPackages.FEATURE_INPUT_METHODS)
@RequireMultiUserSupport
@RunWith(BedsteadJUnit4.class)
public final class AdditionalSubtypeLifecycleTest {
    private static final InputMethodSubtype TEST_SUBTYPE1 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(1)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(2)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE3 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(3)
                    .build();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();  // Required by Bedstead.

    private boolean mNeedsTearDown = false;

    @Before
    public void setUp() {
        mNeedsTearDown = true;
    }

    @After
    public void tearDown() {
        if (!mNeedsTearDown) {
            return;
        }

        TestApis.packages().find(Ime1Constants.PACKAGE).uninstallFromAllUsers();
        TestApis.packages().find(Ime2Constants.PACKAGE).uninstallFromAllUsers();
        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());
    }

    @Test
    @EnsureHasSecondaryUser
    public void testPerUserAdditionalInputMethodSubtype() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference secondaryUser = sDeviceState.secondaryUser();
        final int currentUserId = currentUser.id();
        final int secondaryUserId = secondaryUser.id();

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(secondaryUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, secondaryUserId);

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID, secondaryUserId);

            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE3.hashCode());
        }
    }

    @Test
    @EnsureHasSecondaryUser
    public void testClearAdditionalInputMethodSubtypeForForegroundUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference secondaryUser = sDeviceState.secondaryUser();
        final int currentUserId = currentUser.id();
        final int secondaryUserId = secondaryUser.id();

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(secondaryUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, secondaryUserId);

        // Updating an already-installed APK clears additional subtypes (for the foreground user).
        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID, secondaryUserId);

            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE3.hashCode());
        }
    }

    /**
     * Regression test for Bug 27859687.
     */
    @Test
    @EnsureHasSecondaryUser
    public void testClearAdditionalInputMethodSubtypeForBackgroundUser() {
        final UserReference currentUser = sDeviceState.initialUser();
        final UserReference secondaryUser = sDeviceState.secondaryUser();
        final int currentUserId = currentUser.id();
        final int secondaryUserId = secondaryUser.id();

        TestApis.packages().install(currentUser, new File(Ime1Constants.APK_PATH));
        TestApis.packages().install(secondaryUser, new File(Ime2Constants.APK_PATH));

        callSetAdditionalInputMethodSubtype(Ime1Constants.AUTHORITY, Ime1Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE1, TEST_SUBTYPE2}, currentUserId);
        callSetAdditionalInputMethodSubtype(Ime2Constants.AUTHORITY, Ime2Constants.IME_ID,
                new InputMethodSubtype[] {TEST_SUBTYPE3}, secondaryUserId);

        // Updating an already-installed APK clears additional subtypes (for a background user).
        TestApis.packages().install(secondaryUser, new File(Ime2Constants.APK_PATH));

        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime1Constants.IME_ID, currentUserId);

            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().contains(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE3.hashCode());
        }
        {
            final var subtypeHashCodes = getSubtypeHashCodes(Ime2Constants.IME_ID, secondaryUserId);

            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE1.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE2.hashCode());
            Truth.assertThat(subtypeHashCodes).asList().doesNotContain(TEST_SUBTYPE3.hashCode());
        }
    }

    private static void callSetAdditionalInputMethodSubtype(
            @NonNull String authority, @NonNull String imeId,
            @NonNull InputMethodSubtype[] additionalSubtypes, int userId) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final Bundle args = new Bundle();
        args.putString(CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_IMEID_KEY, imeId);
        args.putParcelableArray(CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_SUBTYPES_KEY,
                additionalSubtypes);
        SystemUtil.runWithShellPermissionIdentity(
                () -> getContentResolverForUser(context, userId).call(authority,
                        CommandProviderConstants.SET_ADDITIONAL_SUBTYPES_COMMAND, null, args));
    }

    @NonNull
    private static ContentResolver getContentResolverForUser(@NonNull Context context, int userId) {
        try {
            return context.createPackageContextAsUser("android", 0, UserHandle.of(userId))
                    .getContentResolver();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private static int[] getSubtypeHashCodes(@NonNull String imeId, int userId) {
        return getInputMethodList(userId)
                .stream()
                .filter(imi -> TextUtils.equals(imi.getId(), imeId))
                .flatMap(imi -> StreamSupport.stream(
                        asSubtypeIterable(imi).spliterator(), false))
                .mapToInt(InputMethodSubtype::hashCode)
                .toArray();
    }

    @NonNull
    private static List<InputMethodInfo> getInputMethodList(int userId) {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodManager imm =
                Objects.requireNonNull(context.getSystemService(InputMethodManager.class));
        return SystemUtil.runWithShellPermissionIdentity(
                () -> imm.getInputMethodListAsUser(userId),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.QUERY_ALL_PACKAGES);
    }

    @NonNull
    private static Iterable<InputMethodSubtype> asSubtypeIterable(@NonNull InputMethodInfo imi) {
        final int subtypeCount = imi.getSubtypeCount();
        return new Iterable<>() {
            @Override
            public Iterator<InputMethodSubtype> iterator() {
                return new Iterator<>() {
                    private int mIndex = 0;

                    @Override
                    public boolean hasNext() {
                        return mIndex < subtypeCount;
                    }

                    @Override
                    public InputMethodSubtype next() {
                        final var value = imi.getSubtypeAt(mIndex);
                        mIndex++;
                        return value;
                    }
                };
            }

            @Override
            public void forEach(Consumer<? super InputMethodSubtype> action) {
                for (int i = 0; i < subtypeCount; ++i) {
                    action.accept(imi.getSubtypeAt(i));
                }
            }
        };
    }
}
