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

import java.util.Spliterator.OfPrimitive;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SpliteratorTest {


    /**
     * Class used to test {@link OfPrimitive#forEachRemaining(Object)}
     */
    private static class PrimitiveIntegerSpliterator
            implements OfPrimitive<Integer, IntConsumer, PrimitiveIntegerSpliterator> {
        private int current = 1;
        private final int size;

        PrimitiveIntegerSpliterator(int size) {
            this.size = size;
        }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (current <= size) {
                action.accept(current++);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public long estimateSize() {
            return size;
        }

        @Override
        public int characteristics() {
            return 0;
        }

        @Override
        public boolean tryAdvance(Consumer<? super Integer> action) {
            if (action instanceof IntConsumer) {
                return tryAdvance(action);
            } else {
                return tryAdvance((IntConsumer) action::accept);
            }
        }

        @Override
        public PrimitiveIntegerSpliterator trySplit() {
            // null implying that this spliterator cannot be split
            return null;
        }
    }

    @Test
    public void testOfPrimitiveForEachRemaining() {
        OfPrimitive<Integer, IntConsumer, PrimitiveIntegerSpliterator> spliterator =
                new PrimitiveIntegerSpliterator(10);
        AtomicInteger sum = new AtomicInteger();
        IntConsumer action = (i) -> sum.addAndGet(i);
        spliterator.forEachRemaining(action);

        assertEquals(55, sum.get());
    }
}
