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
package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;

/** {@link TestRule} for releasing resources once a test ends. */
public final class ResourceReleaser extends ExternalResource {

    private final Queue<Runnable> mPendingRunnables = new ArrayDeque<>();

    /**
     * Adds a {@link Runnable} for execution after the end of the test run, regardless of the test
     * result.
     */
    public void add(Runnable runnable) {
        mPendingRunnables.add(runnable);
    }

    @Override
    public void after() {
        ArrayList<Throwable> throwables = new ArrayList<>();
        while (!mPendingRunnables.isEmpty()) {
            Runnable runnable = mPendingRunnables.remove();
            try {
                runnable.run();
            } catch (Throwable e) {
                throwables.add(e);
            }
        }
        assertWithMessage("Ran into exceptions while releasing resources.")
                .that(throwables)
                .isEmpty();
    }
}
