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

package libcore.java.util.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompletionStageTest {

    static abstract class BaseStage<T> implements CompletionStage<T> {
        private T resultObj = null;
        private Throwable exception = null;
        private Executor executor = null;

        BaseStage() {
        }

        BaseStage(T result) {
            resultObj = result;
        }

        BaseStage(Executor executor) {
            this.executor = executor;
        }

        protected boolean hasExecutor() {
            return executor != null;
        }

        protected void execute() throws Throwable {
            executor.execute(() -> {
                try {
                    resultObj = doGet();
                } catch (Throwable ex) {
                    exception = ex;
                }
            });
            if (exception != null) {
                throw exception;
            }
        }

        boolean isAsync() {
            return false;
        }

        public void completeExceptionally(Throwable ex) {
            this.exception = ex;
        }

        protected abstract T doGet() throws Throwable;

        public T get() throws Throwable {
            if (this.exception != null) {
                throw new TestCompletionException(exception);
            }
            if (resultObj != null) {
                return resultObj;
            }

            if (!hasExecutor()) {
                resultObj = doGet();
            } else {
                execute();
            }
            return resultObj;
        }

        @Override
        public <U> CompletionStage<U> thenApply(Function<? super T,? extends U> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> thenApplyAsync(Function<? super T,? extends U> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> thenApplyAsync(Function<? super T,? extends U> fn,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenAccept(Consumer<? super T> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenAcceptAsync(Consumer<? super T> action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenRun(Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> thenRunAsync(Runnable action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U,V> CompletionStage<V> thenCombine(CompletionStage<? extends U> other,
                BiFunction<? super T,? super U,? extends V> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U,V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                BiFunction<? super T,? super U,? extends V> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U,V> CompletionStage<V> thenCombineAsync(CompletionStage<? extends U> other,
                BiFunction<? super T,? super U,? extends V> fn,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<Void> thenAcceptBoth(CompletionStage<? extends U> other,
                BiConsumer<? super T, ? super U> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                BiConsumer<? super T, ? super U> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<Void> thenAcceptBothAsync(CompletionStage<? extends U> other,
                BiConsumer<? super T, ? super U> action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterBoth(CompletionStage<?> other,
                Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other,
                Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterBothAsync(CompletionStage<?> other,
                Runnable action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> applyToEither(CompletionStage<? extends T> other,
                Function<? super T, U> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                Function<? super T, U> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> applyToEitherAsync(CompletionStage<? extends T> other,
                Function<? super T, U> fn,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> acceptEither(CompletionStage<? extends T> other,
                Consumer<? super T> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                Consumer<? super T> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> acceptEitherAsync(CompletionStage<? extends T> other,
                Consumer<? super T> action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterEither(CompletionStage<?> other,
                Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other,
                Runnable action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<Void> runAfterEitherAsync(CompletionStage<?> other,
                Runnable action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> CompletionStage<U> thenCompose(
                Function<? super T, ? extends CompletionStage<U>> fn) {
            return new ComposeStage<T, U>(this, fn);
        }

        @Override
        public <U> CompletionStage<U> thenComposeAsync(
                Function<? super T, ? extends CompletionStage<U>> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public <U> CompletionStage<U> thenComposeAsync(
                Function<? super T, ? extends CompletionStage<U>> fn,
                Executor executor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U> CompletionStage<U> handle(BiFunction<? super T, Throwable, ? extends U> fn) {
            return new HandleStage<T, U>(this, fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(
                BiFunction<? super T, Throwable, ? extends U> fn) {
            return new AsyncHandleStage<T, U>(this, fn);
        }

        @Override
        public <U> CompletionStage<U> handleAsync(BiFunction<? super T, Throwable, ? extends U> fn,
                Executor executor) {
            return new AsyncHandleStage<T, U>(this, fn, executor);
        }

        @Override
        public CompletionStage<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<T> whenCompleteAsync(
                BiConsumer<? super T, ? super Throwable> action) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<T> whenCompleteAsync(
                BiConsumer<? super T, ? super Throwable> action,
                Executor executor) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletionStage<T> exceptionally(Function<Throwable, ? extends T> fn) {
            throw new UnsupportedOperationException();
        }
        @Override
        public CompletableFuture<T> toCompletableFuture() {
            throw new UnsupportedOperationException();
        }
    }

    static abstract class ChildStage<T, U> extends BaseStage<U> {
        private final BaseStage<T> parent;

        ChildStage(BaseStage<T> parent) {
            this.parent = parent;
        }

        ChildStage(BaseStage<T> parent, Executor executor) {
            super(executor);
            this.parent = parent;
        }

        protected T parentGet() throws Throwable {
            return parent.get();
        }
    }

    static class HandleStage<T, U> extends ChildStage<T, U> {
        private final BiFunction<? super T, Throwable, ? extends U> fn;

        HandleStage(BaseStage<T> parent,
                BiFunction<? super T, Throwable, ? extends U> fn) {
            super(parent);
            this.fn = fn;
        }

        HandleStage(BaseStage<T> parent,
                BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            super(parent, executor);
            this.fn = fn;
        }

        @Override
        protected U doGet() throws Throwable {
            T parentResult = null;
            TestCompletionException thrownException = null;
            try {
                parentResult = parentGet();
            } catch (TestCompletionException ex) {
                thrownException = ex;
                if (isAsync()) {
                    thrownException.markAsync();
                }
            }

            return fn.apply(parentResult, thrownException);
        }
    }

    static class AsyncHandleStage<T, U> extends HandleStage<T, U> {

        AsyncHandleStage(BaseStage<T> parent,
                BiFunction<? super T, Throwable, ? extends U> fn) {
            super(parent, fn);
        }

        AsyncHandleStage(BaseStage<T> parent,
                BiFunction<? super T, Throwable, ? extends U> fn, Executor executor) {
            super(parent, fn, executor);
        }

        @Override
        boolean isAsync() {
            return true;
        }
    }

    static final class ComposeStage<T, U> extends ChildStage<T, U> {
        private final Function<? super T, ? extends CompletionStage<U>> fn;

        ComposeStage(BaseStage<T> parent,
                Function<? super T, ? extends CompletionStage<U>> fn) {
            super(parent);
            this.fn = fn;
        }

        @Override
        protected U doGet() throws Throwable {
            T parentResult = null;
            parentResult = parentGet();
            BaseStage<U> next = (BaseStage<U>) fn.apply(parentResult);
            return next.get();
        }
    }

    static final class SupplierStage<T> extends BaseStage<T> {
        private Supplier<T> fn = null;

        SupplierStage() {
        }

        SupplierStage(Supplier<T> fn) {
            this.fn = fn;
        }

        @Override
        public T doGet() throws Throwable {

            if (fn != null) {
                return fn.get();
            }

            return null;
        }
    }

    static final class ResolvedStage<T> extends BaseStage<T> {
        ResolvedStage(T result) {
            super(result);
        }

        @Override
        public T doGet() throws Throwable {
            // This shouldn't be reached as resultObj is already set.
            return null;
        }
    }

    static final class TestCompletionException extends Throwable {
        private boolean asyncHandled = false;

        TestCompletionException(Throwable cause) {
            super(cause);
        }

        void markAsync() {
            asyncHandled = true;
        }

        boolean isAsyncHandled() {
            return asyncHandled;
        }
    }

    static final class TestExecutor implements Executor {
        private int executions = 0;

        @Override
        public void execute(Runnable command) {
            ++executions;
            command.run();
        }

        boolean wasRun() {
            return executions > 0;
        }
    }

    static abstract class ExceptionalFunction<T, U>
                    implements Function<TestCompletionException, U> {
        private int invocationCount = 0;
        private Throwable exception = null;
        private boolean asyncCall = false;
        final private T resultObj;

        ExceptionalFunction(T result) {
            resultObj = result;
        }

        protected void markInvoked(TestCompletionException ex) {
            invocationCount++;
            exception = ex.getCause();
            if (ex.isAsyncHandled()) {
                markAsync();
            }
        }

        protected T getResult() {
            return resultObj;
        }

        boolean wasInvoked() {
            return invocationCount > 0;
        }

        boolean wasAsync() {
            return asyncCall;
        }

        void markAsync() {
            asyncCall = true;
        }

        Throwable getException() {
            return exception;
        }
    }

    static final class ExceptionalCompletionFunction<T> extends ExceptionalFunction<T, T> {

        ExceptionalCompletionFunction(T result) {
            super(result);
        }

        @Override
        public T apply(TestCompletionException ex) {
            markInvoked(ex);
            return getResult();
        }
    }

    static final class ExceptionalCompletionStageFunction<T>
                    extends ExceptionalFunction<T, CompletionStage<T>> {

        ExceptionalCompletionStageFunction(T result) {
            super(result);
        }

        @Override
        public CompletionStage<T> apply(TestCompletionException ex) {
            markInvoked(ex);
            return new ResolvedStage<T>(getResult());
        }
    }

    /**
     * default exceptionallyAsync will provide for a stage with a new CompletionStage which will
     * run asynchronously if the stage completes exceptionally.
     *
     * Validate that the supplied function was run on exceptional completion and it received the
     * original exception.
     */
    @Test
    public void testExceptionallyAsync() throws Throwable {
        BaseStage stage = new SupplierStage();
        Object expectedResult = new Object();
        Throwable expectedException = new RuntimeException();
        ExceptionalCompletionFunction fn = new ExceptionalCompletionFunction(expectedResult);

        stage.completeExceptionally(expectedException);
        stage = (BaseStage) stage.exceptionallyAsync(fn);
        Object result = stage.get();

        assertTrue(fn.wasInvoked());
        assertTrue(fn.wasAsync());
        assertSame(expectedException, fn.getException());
        assertSame(expectedResult, result);
    }

    /**
     * default exceptionallyAsync will provide for a stage with a new CompletionStage which will
     * run asynchronously if the stage completes exceptionally. The new CompletionStage will be run
     * in a provided executor.
     *
     * Validate that the supplied function was run on exceptional completion and it received the
     * original exception. The run of the function occurred within the provided executor.
     */
    @Test
    public void testExceptionallyAsyncWithExecutor() throws Throwable {
        BaseStage stage = new SupplierStage();
        Object expectedResult = new Object();
        Throwable expectedException = new RuntimeException();
        ExceptionalCompletionFunction fn = new ExceptionalCompletionFunction(expectedResult);
        TestExecutor executor = new TestExecutor();

        stage.completeExceptionally(expectedException);
        stage = (BaseStage) stage.exceptionallyAsync(fn, executor);
        Object result = stage.get();

        assertTrue(fn.wasInvoked());
        assertTrue(fn.wasAsync());
        assertSame(expectedException, fn.getException());
        assertSame(expectedResult, result);
        assertTrue(executor.wasRun());
    }

    /**
     * default exceptionallyComposeAsync will compose a stage with a new CompletionStage which will
     * run if the stage completes exceptionally.
     *
     * Validate that the supplied function was run on exceptional completion and it received the
     * original exception.
     */
    @Test
    public void testExceptionallyCompose() throws Throwable {
        BaseStage stage = new SupplierStage();
        Object expectedResult = new Object();
        Throwable expectedException = new RuntimeException();
        ExceptionalCompletionStageFunction fn = new ExceptionalCompletionStageFunction(expectedResult);

        stage.completeExceptionally(expectedException);
        stage = (BaseStage) stage.exceptionallyCompose(fn);
        Object result = stage.get();

        assertTrue(fn.wasInvoked());
        assertFalse(fn.wasAsync());
        assertSame(expectedException, fn.getException());
        assertSame(expectedResult, result);
    }

    /**
     * default exceptionallyComposeAsync will compose a stage with a new CompletionStage which will
     * run asynchronously if the stage completes exceptionally.
     *
     * Validate that the supplied function was run on exceptional completion and it received the
     * original exception.
     */
    @Test
    public void testExceptionallyComposeAsync() throws Throwable {
        BaseStage stage = new SupplierStage();
        Object expectedResult = new Object();
        Throwable expectedException = new RuntimeException();
        ExceptionalCompletionStageFunction fn = new ExceptionalCompletionStageFunction(expectedResult);

        stage.completeExceptionally(expectedException);
        stage = (BaseStage) stage.exceptionallyComposeAsync(fn);
        Object result = stage.get();

        assertTrue(fn.wasInvoked());
        assertTrue(fn.wasAsync());
        assertSame(expectedException, fn.getException());
        assertSame(expectedResult, result);
    }

    /**
     * default exceptionallyComposeAsync will compose a stage with a new CompletionStage which will
     * run asynchronously if the stage completes exceptionally. The new CompletionStage will be run
     * in a provided executor.
     *
     * Validate that the supplied function was run on exceptional completion and it received the
     * original exception. The run of the function occurred within the provided executor.
     */
    @Test
    public void testExceptionallyComposeAsyncWithExecutor() throws Throwable {
        BaseStage stage = new SupplierStage();
        Object expectedResult = new Object();
        Throwable expectedException = new RuntimeException();
        ExceptionalCompletionStageFunction fn = new ExceptionalCompletionStageFunction(expectedResult);
        TestExecutor executor = new TestExecutor();

        stage.completeExceptionally(expectedException);
        stage = (BaseStage) stage.exceptionallyComposeAsync(fn, executor);
        Object result = stage.get();

        assertTrue(fn.wasInvoked());
        assertTrue(fn.wasAsync());
        assertSame(expectedException, fn.getException());
        assertSame(expectedResult, result);
        assertTrue(executor.wasRun());
    }

}
