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

package android.platform.test.flag.junit;

import android.annotation.SuppressLint;
import android.app.UiAutomation;
import android.platform.test.flag.util.Flag;
import android.platform.test.flag.util.FlagReadException;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.base.CaseFormat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/** A {@code IFlagsValueProvider} which provides flag values from device side. */
public class DeviceFlagsValueProvider implements IFlagsValueProvider {
    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final Set<String> VALID_BOOLEAN_VALUE = Set.of("true", "false");

    private final UiAutomation mUiAutomation;

    public static CheckFlagsRule createCheckFlagsRule() {
        return new CheckFlagsRule(new DeviceFlagsValueProvider());
    }

    /**
     * Creates a {@link CheckFlagsRule} with an existing {@link UiAutomation} instance.
     *
     * <p>This is necessary if the test modifies {@link UiAutomation} flags.
     *
     * @param uiAutomation The {@link UiAutomation} used by the test.
     */
    public static CheckFlagsRule createCheckFlagsRule(UiAutomation uiAutomation) {
        return new CheckFlagsRule(new DeviceFlagsValueProvider(uiAutomation));
    }

    public DeviceFlagsValueProvider() {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    private DeviceFlagsValueProvider(UiAutomation uiAutomation) {
        mUiAutomation = uiAutomation;
    }

    @Override
    public void setUp() {
        mUiAutomation.adoptShellPermissionIdentity(READ_DEVICE_CONFIG_PERMISSION);
    }

    @Override
    public boolean getBoolean(String flag) throws FlagReadException {
        Flag parsedFlag = Flag.createFlag(flag);
        if (parsedFlag.namespace() != null) {
            if (parsedFlag.packageName() != null) {
                throw new FlagReadException(
                        flag, "You can not specify the namespace for aconfig flags.");
            }
            return getLegacyFlagBoolean(parsedFlag);
        }
        if (parsedFlag.packageName() == null) {
            throw new FlagReadException(
                    flag, "Flag name is not the expected format {packageName}.{flagName}.");
        }
        String className = parsedFlag.flagsClassName();
        String simpleFlagName = parsedFlag.simpleFlagName();

        // Must be consistent with method name in aconfig auto generated code.
        String methodName = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, simpleFlagName);

        try {
            Class<?> flagsClass = Class.forName(className);
            Method method = flagsClass.getMethod(methodName);
            Object result = method.invoke(null);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            throw new FlagReadException(
                    flag,
                    String.format(
                            "Flag type is %s, not boolean", result.getClass().getSimpleName()));
        } catch (ClassNotFoundException e) {
            throw new FlagReadException(
                    flag,
                    String.format(
                            "Can not load the Flags class %s to get its values. Please check the "
                                    + "flag name and ensure that the aconfig auto generated "
                                    + "library is in the dependency.",
                            className),
                    e);
        } catch (NoSuchMethodException e) {
            throw new FlagReadException(
                    flag,
                    String.format(
                            "No method %s in the Flags class to read the flag value. Please check"
                                    + " the flag name.",
                            methodName),
                    e);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new FlagReadException(flag, e);
        }
    }

    private boolean getLegacyFlagBoolean(Flag flag) throws FlagReadException {
        @SuppressLint("MissingPermission")
        String property = DeviceConfig.getProperty(flag.namespace(), flag.fullFlagName());
        if (property == null) {
            throw new FlagReadException(flag.fullFlagName(), "Flag does not exist on the device.");
        }
        if (VALID_BOOLEAN_VALUE.contains(property)) {
            return Boolean.valueOf(property);
        }
        throw new FlagReadException(
                flag.fullFlagName(), String.format("Value %s is not a valid boolean.", property));
    }

    @Override
    public void tearDownBeforeTest() {
        mUiAutomation.dropShellPermissionIdentity();
    }
}
