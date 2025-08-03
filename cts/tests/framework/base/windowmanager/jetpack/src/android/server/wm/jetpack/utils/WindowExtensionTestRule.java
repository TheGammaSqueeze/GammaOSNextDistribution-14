/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.server.wm.jetpack.utils;

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.assumeExtensionSupportedDevice;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getExtensionWindowAreaComponent;
import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getExtensionWindowLayoutComponent;
import static org.junit.Assume.assumeNotNull;

import androidx.window.extensions.area.WindowAreaComponent;
import androidx.window.extensions.layout.WindowLayoutComponent;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class WindowExtensionTestRule implements TestRule {

    private final Class<?> mComponentToFetch;
    private Object mComponent;

    public Object getExtensionComponent() {
        return mComponent;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                assumeExtensionSupportedDevice();
                mComponent = getComponent(mComponentToFetch);
                assumeNotNull(mComponent);
                base.evaluate();
            }
        };
    }

    public WindowExtensionTestRule(Class<?> componentType) {
        mComponentToFetch = componentType;
    }

    public Object getComponent(Class<?> componentToFetch) {
        if (componentToFetch == WindowAreaComponent.class) {
            return getExtensionWindowAreaComponent();
        } else if (componentToFetch == WindowLayoutComponent.class) {
            return getExtensionWindowLayoutComponent();
        }
        return null;
    }
}
