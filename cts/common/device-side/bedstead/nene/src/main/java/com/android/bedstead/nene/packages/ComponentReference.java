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

package com.android.bedstead.nene.packages;

import android.content.ComponentName;
import android.text.TextUtils;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.Objects;

/**
 * A representation of a component on device which may or may not exist.
 */
@Experimental
public class ComponentReference {

    final Package mPackage;
    final String mClassName;

    private static final String CONFIG_KEY_RESOLVER_ACTIVITY = "config_customResolverActivity";
    private static final String DEFAULT_STRING_TYPE = "string";
    private static final String DEFAULT_PACKAGE_ANDROID = "android";
    private static final ComponentReference DEFAULT_RESOLVER_ACTIVITY =
            new ComponentReference(TestApis.packages().find(DEFAULT_PACKAGE_ANDROID),
                    "com.android.internal.app.ResolverActivity");

    /** See {@link ComponentName#unflattenFromString(String)}. */
    public static ComponentReference unflattenFromString(String string) {
        return new ComponentReference(
                Objects.requireNonNull(ComponentName.unflattenFromString(string)));
    }

    public ComponentReference(Package packageName, String className) {
        mPackage = packageName;
        mClassName = className;
    }

    public ComponentReference(ComponentName component) {
        this(new Package(component.getPackageName()), component.getClassName());
    }

    /**
     * Get the {@link Package} for this component.
     */
    public Package pkg() {
        return mPackage;
    }

    /**
     * Get the class for this component.
     */
    public String className() {
        return mClassName;
    }

    /**
     * Get this component as a {@link ComponentName}.
     */
    public ComponentName componentName() {
        return new ComponentName(mPackage.packageName(), mClassName);
    }

    /**
     * Enable this component for the given {@link UserReference}.
     */
    public ComponentReference enable(UserReference user) {
        try {
            ShellCommand.builderForUser(user, "pm enable")
                    .addOperand(mPackage.packageName() + "/" + mClassName)
                    .validate(o -> o.contains("new state"))
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error enabling component " + this + " for user " + user, e);
        }
        return this;
    }

    /**
     * See {@link ComponentName#flattenToString()}.
     */
    public String flattenToString() {
        return componentName().flattenToString();
    }

    /**
     * Enable this component for the instrumented user.
     */
    public ComponentReference enable() {
        return enable(TestApis.users().instrumented());
    }

    /**
     * Disable this component for the given {@link UserReference}.
     */
    public ComponentReference disable(UserReference user) {
        try {
            ShellCommand.builderForUser(user, "pm disable")
                    .addOperand(mPackage.packageName() + "/" + mClassName)
                    .validate(o -> o.contains("new state"))
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error disabling component " + this + " for user " + user, e);
        }
        return this;
    }

    /**
     * Disable this component for the instrumented user.
     */
    public ComponentReference disable() {
        return disable(TestApis.users().instrumented());
    }

    /**
     * Checks if the activity is a {@link com.android.internal.app.ResolverActivity}
     */
    public boolean isResolver() {
        return equals(getResolverActivity());
    }

    private ComponentReference getResolverActivity() {
        String resolverActivity = TestApis.resources().system().getString(
                /* name= */ CONFIG_KEY_RESOLVER_ACTIVITY,
                /* defType= */ DEFAULT_STRING_TYPE,
                /* defPackage= */ DEFAULT_PACKAGE_ANDROID);
        if (TextUtils.isEmpty(resolverActivity)) {
            return DEFAULT_RESOLVER_ACTIVITY;
        }

        return ComponentReference.unflattenFromString(resolverActivity);
    }

    @Override
    public int hashCode() {
        return componentName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ComponentReference)) {
            return false;
        }

        ComponentReference other = (ComponentReference) obj;
        return other.componentName().equals(componentName());
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("ComponentReference{");
        stringBuilder.append("package=" + mPackage);
        stringBuilder.append(", component=" + mClassName);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
