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

package libcore.java.util.concurrent;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LinkedBlockingQueueTest {

    @Test
    public void testForEach() {
        int capacity = 10;
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
        for (int i = 0; i < capacity; ++i) {
            queue.add(Integer.valueOf(i+1));
        }
        LongAdder adder = new LongAdder();
        queue.forEach((Integer x) -> adder.add(x.longValue()));
        // The size is small enough for the sum not to overflow
        assertEquals(queue.size() * (queue.size() + 1) / 2, adder.sum());
    }
}
