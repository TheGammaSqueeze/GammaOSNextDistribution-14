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

package com.android.interactive;

/**
 * An automatic implementation of a Step.
 */
public interface Automation<E> {

    /**
     * Run the automation.
     *
     * <p>This should match exactly the behavior of the Step being automated (as determined by the
     * textual instruction of the step, with additional details provided in the Javadoc).
     *
     * <p>If the step cannot be completed, an exception should be thrown. This will fail the test
     * and the exception will be shown as the failure reason.
     *
     * <p>If the step can be completed successfully and no exception is thrown, then the test will
     * continue as if the step had been completed manually.
     */
    E automate() throws Exception;
}
