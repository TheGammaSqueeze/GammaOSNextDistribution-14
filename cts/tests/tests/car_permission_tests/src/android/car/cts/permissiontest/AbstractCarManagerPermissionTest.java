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

package android.car.cts.permissiontest;

import static com.android.compatibility.common.util.ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.Car;
import android.car.test.PermissionsCheckerRule;
import android.content.Context;
import android.os.Handler;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Rule;

import java.util.Objects;

/**
 * This abstract class contains setup logic and utility methods for car security and permission
 * tests.
 */
public class AbstractCarManagerPermissionTest {

    protected Car mCar = null;
    protected final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    // CheckFlagsRule rule needs to before PermissionsCheckerRule.
    @Rule(order = 0)
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule(order = 1)
    public final PermissionsCheckerRule permissionsCheckerRule = new PermissionsCheckerRule();

    public final void connectCar() {
        mCar = Objects.requireNonNull(Car.createCar(mContext, (Handler) null));
        assertWithMessage("mCar").that(mCar).isNotNull();
    }

    @After
    public final void disconnectCar() {
        invokeMethodWithShellPermissionsNoReturn(mCar, car -> car.disconnect());
    }
}
