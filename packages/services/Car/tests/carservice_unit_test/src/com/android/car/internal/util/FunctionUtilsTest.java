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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.RemoteException;

import com.android.car.internal.util.FunctionalUtils.RemoteExceptionIgnoringConsumer;
import com.android.car.internal.util.FunctionalUtils.ThrowingBiConsumer;
import com.android.car.internal.util.FunctionalUtils.ThrowingBiFunction;
import com.android.car.internal.util.FunctionalUtils.ThrowingConsumer;
import com.android.car.internal.util.FunctionalUtils.ThrowingFunction;
import com.android.car.internal.util.FunctionalUtils.ThrowingRunnable;
import com.android.car.internal.util.FunctionalUtils.ThrowingSupplier;

import org.junit.Test;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FunctionUtilsTest {

    @Test
    public void testUncheckedExceptionsThrowningConsumer() {
        ThrowingConsumer<Boolean> tc = (Boolean error) -> {
            if (error) {
                throw new Exception("1234");
            }
        };
        Consumer<Boolean> c = FunctionalUtils.uncheckExceptions(tc);

        assertThrows(RuntimeException.class, () -> {
            c.accept(true);
        });
        c.accept(false);
    }

    @Test
    public void testUncheckedExceptionsThrowningFunction() {
        ThrowingFunction<Boolean, Integer> tf = (Boolean error) -> {
            if (error) {
                throw new Exception("1234");
            }
            return 1;
        };
        Function<Boolean, Integer> f = FunctionalUtils.uncheckExceptions(tf);

        assertThrows(RuntimeException.class, () -> {
            f.apply(true);
        });
        assertThat(f.apply(false)).isEqualTo(1);
    }

    @Test
    public void testUncheckedExceptionsThrowningRunnable() {
        ThrowingRunnable tr = () -> {
            throw new Exception("1234");
        };
        Runnable r = FunctionalUtils.uncheckExceptions(tr);

        assertThrows(RuntimeException.class, () -> {
            r.run();
        });
    }

    @Test
    public void testUncheckedExceptionsThrowningBiConsumer() {
        ThrowingBiConsumer<Boolean, Boolean> tbc = (Boolean error1, Boolean error2) -> {
            if (error1 && error2) {
                throw new Exception("1234");
            }
        };
        BiConsumer<Boolean, Boolean> bc = FunctionalUtils.uncheckExceptions(tbc);

        assertThrows(RuntimeException.class, () -> {
            bc.accept(true, true);
        });
        bc.accept(true, false);
    }

    @Test
    public void testUncheckedExceptionsThrowingSupplier() {
        ThrowingSupplier<Integer> ts = () -> {
            throw new Exception("1234");
        };
        Supplier<Integer> s = FunctionalUtils.uncheckExceptions(ts);

        assertThrows(RuntimeException.class, () -> {
            s.get();
        });

        ts = () -> {
            return 1;
        };
        Supplier<Integer> s2 = FunctionalUtils.uncheckExceptions(ts);

        assertThat(s2.get()).isEqualTo(1);
    }

    @Test
    public void testIgnoreRemoteException() {
        RemoteExceptionIgnoringConsumer<Boolean> ic = (Boolean remote) -> {
            if (remote) {
                throw new RemoteException("1234");
            } else {
                throw new RuntimeException("1234");
            }
        };

        Consumer<Boolean> c = FunctionalUtils.ignoreRemoteException(ic);

        assertThrows(RuntimeException.class, () -> {
            c.accept(false);
        });
        c.accept(true);
    }

    @Test
    public void testHandleExceptions() {
        Exception e = new Exception("1234");
        ThrowingRunnable tr = () -> {
            throw e;
        };
        Consumer<Throwable> handler = (Throwable t) -> {
            assertThat(t.getCause()).isEqualTo(e);
        };

        FunctionalUtils.handleExceptions(tr, handler).run();
    }

    @Test
    public void testThrowingBiFunction() {
        ThrowingBiFunction<Boolean, Boolean, Integer> tbf = (Boolean error1, Boolean error2) -> {
            if (error1 && error2) {
                throw new RemoteException("1234");
            }
            return 1;
        };

        BiFunction<Boolean, Boolean, Integer> bf = tbf;
        assertThrows(RuntimeException.class, () -> {
            bf.apply(true, true);
        });
        assertThat(bf.apply(true, false)).isEqualTo(1);
    }

    @Test
    public void testGetLambdaName() {
        Runnable r = ()->{};
        assertThat(FunctionalUtils.getLambdaName(r)).contains(FunctionUtilsTest.class
                .getCanonicalName());
    }
}
