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

package android.webkit.cts;

/**
 * Extending this class indicates that a test can be shared between the SDK Runtime and Activity
 * based tests.
 *
 * <p>If a test is shared, it will be expected to provide its own {@link
 * SharedWebViewTestEnvironment} implementation.
 */
public abstract class SharedWebViewTest {
    public static final String WEB_VIEW_TEST_CLASS_NAME = "WEB_VIEW_TEST_CLASS_NAME";

    private SharedWebViewTestEnvironment mEnvironment;

    protected abstract SharedWebViewTestEnvironment createTestEnvironment();

    public void setTestEnvironment(SharedWebViewTestEnvironment sharedWebViewTestEnvironment) {
        mEnvironment = sharedWebViewTestEnvironment;
    }

    public SharedWebViewTestEnvironment getTestEnvironment() {
        if (mEnvironment == null) {
            mEnvironment = createTestEnvironment();
        }
        return mEnvironment;
    }
}
