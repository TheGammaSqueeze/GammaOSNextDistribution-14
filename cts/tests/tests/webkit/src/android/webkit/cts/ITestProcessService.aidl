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

package android.webkit.cts;

import android.os.Bundle;

interface ITestProcessService {
    /**
     * Runs the given test class.
     *
     * <p>This is a sync call.
     *
     * @param testClassName the name of a test class that extends {@code
     *     TestProcessClient#TestRunnable}.
     * @return test result as a bundle. If the test passes, the bundle will be empty. If it fails,
     *     it will contain the failure exception as a Serializable.
     */
    Bundle run(String testClassName);

    /**
     * Terminates the TestProcessService process.
     *
     * <p>This is a sync call.
     */
    void exit();
}
