/*
 * Copyright (C) 2024 The Android Open Source Project
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

package libcore.android34.java.util.concurrent;

import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@RunWith(JUnit4.class)
public class BlockingDequeTest {

    /**
     * Regression test for http://b/322063300.
     * ART hidden API check shouldn't block the public API BlockingDeque.getLast()
     * even though its super-interface SequencedCollection is hidden.
     */
    @Test
    public void testGetLast() throws InterruptedException {
        BlockingDeque<String> q = new LinkedBlockingDeque<>();
        q.put("a");
        assertEquals("a", q.getLast());
    }
}
