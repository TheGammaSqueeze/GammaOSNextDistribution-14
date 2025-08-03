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

package libcore.java.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

@RunWith(JUnit4.class)
public class PriorityQueueTest {

    @Test
    public void forEach_throwsNPE_whenConsumerIsNullAndQueueIsEmpty() {
        var pq = new PriorityQueue<Integer>();

        assertThrows(NullPointerException.class, () -> pq.forEach(null));
    }

    @Test
    public void forEach_throwsNPE_whenConsumerIsNullAndQueueIsNotEmpty() {
        var pq = new PriorityQueue<Integer>();

        pq.add(10);

        assertThrows(NullPointerException.class, () -> pq.forEach(null));
    }

    @Test
    public void forEach_followPriorityQueueInArbitraryOrder() {
        var pq = new PriorityQueue<Integer>();

        pq.add(10);
        pq.add(0);
        pq.add(42);
        pq.add(-10);
        pq.add(0);
        pq.add(42);

        // PriorityQueue.iterator() does not specify traversal order and the
        // collection allows duplicates, hence Map instead of List or Set and
        // duplicate values being added.
        var expected = Map.of(-10, 1, 0, 2, 10, 1, 42, 2);

        var actual = new HashMap<Integer, Integer>();

        pq.forEach(element -> actual.compute(element,
                (k, oldValue) -> oldValue == null ? 1 : oldValue + 1));

        assertEquals(expected, actual);
    }

    @Test
    public void forEach_throwsCME_ifConsumerModifiesQueue() {
        var pq = new PriorityQueue<>();

        pq.add(0);

        assertThrows(ConcurrentModificationException.class, () -> pq.forEach(pq::add));
    }

}
