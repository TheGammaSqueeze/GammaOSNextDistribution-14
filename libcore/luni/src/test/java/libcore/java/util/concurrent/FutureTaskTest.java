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

package libcore.java.util.concurrent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FutureTaskTest {

    /*
     * See b/241297967.
     */
    @Test
    public void testRecursiveToString() {
        TestCallable callable = new TestCallable();
        FutureTask task = new FutureTask(callable);
        callable.set(task);
        String placeholder = task.toString();
        assertFalse(callable.hasDetectedRecursiveToString());
    }

    static class TestCallable extends AtomicReference<FutureTask> implements Callable<Integer> {

        private int toStringCalls = 0;

        @Override
        public Integer call() throws Exception {
            return Integer.valueOf(0);
        }

        public boolean hasDetectedRecursiveToString() {
            return toStringCalls >= 3;
        }

        @Override
        public String toString() {
            if (toStringCalls < 3) {
                ++toStringCalls;
                return super.toString();
            } else {
                return "";
            }
        }
    }

}
