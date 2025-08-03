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

package libcore.java.util.stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.stream.Stream;

@RunWith(JUnit4.class)
public class MapMultiTest {

    private BiConsumer<Integer, IntConsumer> intToId = (e, sink) -> sink.accept(e);
    private BiConsumer<Long, LongConsumer> longToId = (e, sink) -> sink.accept(e);
    private BiConsumer<Double, DoubleConsumer> doubleToId = (e, sink) -> sink.accept(e);

    private BiConsumer<Integer, IntConsumer> intDevNull = (e, sink) -> {};
    private BiConsumer<Long, LongConsumer> longDevNull = (e, sink) -> {};
    private BiConsumer<Double, DoubleConsumer> doubleDevNull = (e, sink) -> {};

    private BiConsumer<List<Integer>, IntConsumer> intListFlattener = (list, sink) -> {
        list.forEach(sink::accept);
    };
    private BiConsumer<List<Long>, LongConsumer> longListFlattener = (list, sink) -> {
        list.forEach(sink::accept);
    };
    private BiConsumer<List<Double>, DoubleConsumer> doubleListFlattener = (list, sink) -> {
        list.forEach(sink::accept);
    };


    @Test
    public void shouldThrowNPE_whenConsumerIsNull() {
        assertThrows(NullPointerException.class, () -> Stream.of().mapMultiToInt(null));
        assertThrows(NullPointerException.class, () -> Stream.of().mapMultiToLong(null));
        assertThrows(NullPointerException.class, () -> Stream.of().mapMultiToDouble(null));
    }

    @Test
    public void testMapMultiToInt_toId() {
        int[] result = Stream.of(10, 20, 30).mapMultiToInt(intToId).toArray();

        assertArrayEquals(new int[] {10, 20, 30}, result);
    }

    @Test
    public void testMapMultiToLong_toId() {
        long[] result = Stream.of(10L, 20L, 30L).mapMultiToLong(longToId).toArray();

        assertArrayEquals(new long[] {10L, 20L, 30L}, result);
    }

    @Test
    public void testMapMultiToDouble_toId() {
        double[] result = Stream.of(10.0, 20.0, 30.0).mapMultiToDouble(doubleToId).toArray();

        assertArrayEquals(new double[] {10.0, 20.0, 30.0}, result, 0.01);
    }

    @Test
    public void testMapMultiToInt_noopSink() {
        int[] result = Stream.of(10, 20, 30).mapMultiToInt(intDevNull).toArray();

        assertArrayEquals(new int[] {}, result);
    }

    @Test
    public void testMapMultiToLong_noopSink() {
        long[] result = Stream.of(10L, 20L, 30L).mapMultiToLong(longDevNull).toArray();

        assertArrayEquals(new long[] {}, result);
    }

    @Test
    public void testMapMultiToDouble_noopSink() {
        double[] result = Stream.of(10.0, 20.0, 30.0).mapMultiToDouble(doubleDevNull).toArray();

        assertArrayEquals(new double[] {}, result, 0);
    }

    @Test
    public void testMapMultiToInt_flattenToList() {
        int[] flattened = Stream.of(List.of(10, 20, 30)).mapMultiToInt(intListFlattener).toArray();

        assertArrayEquals(new int[] {10, 20, 30}, flattened);
    }

    @Test
    public void testMapMultiToLong_flattenToList() {
        long[] flattened =
                Stream.of(List.of(10L, 20L, 30L)).mapMultiToLong(longListFlattener).toArray();

        assertArrayEquals(new long[] {10, 20, 30}, flattened);
    }

    @Test
    public void testMapMultiToDouble_flattenToList() {
        double[] flattened =
                Stream.of(List.of(10.0, 20.0, 30.0))
                        .mapMultiToDouble(doubleListFlattener)
                        .toArray();

        assertArrayEquals(new double[]{10, 20, 30}, flattened, 0.0);
    }


}
