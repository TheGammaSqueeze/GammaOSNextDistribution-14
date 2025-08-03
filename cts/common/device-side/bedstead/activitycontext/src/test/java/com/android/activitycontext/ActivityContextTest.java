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

package com.android.activitycontext;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.activitycontext.annotations.RunWhenInstrumentingOtherApp;
import com.android.compatibility.common.util.BlockingCallback;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@RunWith(JUnit4.class)
public class ActivityContextTest {
    private static final String STRING_VALUE = "String";
    private static final int INT_VALUE = 1;
    private static final boolean BOOLEAN_VALUE = true;

    @Test
    public void getWithContext_nullRunnable_throwsException() {
        assertThrows(NullPointerException.class, () -> ActivityContext.getWithContext(null));
    }

    @Test
    public void runWithContext_nullRunnable_throwsException() {
        assertThrows(NullPointerException.class, () -> ActivityContext.runWithContext(null));
    }

    @Test
    public void getWithContext_passesActivityContext() throws Exception {
        boolean contextIsActivityContext = ActivityContext.getWithContext(Objects::nonNull);

        assertThat(contextIsActivityContext).isTrue();
    }

    @Test
    public void getWithContext_oneArgument_passesActivityContext() throws Exception {
        boolean contextIsActivityContext = ActivityContext.getWithContext(
                STRING_VALUE, (context, str) -> context != null);

        assertThat(contextIsActivityContext).isTrue();
    }

    @Test
    public void getWithContext_oneArgument_passesFirstArgument() throws Exception {
        String passedString = ActivityContext.getWithContext(STRING_VALUE, (context, str) -> str);

        assertThat(passedString).isEqualTo(STRING_VALUE);
    }

    @Test
    public void getWithContext_twoArguments_passesActivityContext() throws Exception {
        boolean contextIsActivityContext = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, (context, str, i) -> context != null);

        assertThat(contextIsActivityContext).isTrue();
    }

    @Test
    public void getWithContext_twoArguments_passesFirstArgument() throws Exception {
        String passedString = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, (context, str, i) -> str);

        assertThat(passedString).isEqualTo(STRING_VALUE);
    }

    @Test
    public void getWithContext_twoArguments_passesSecondArgument() throws Exception {
        int passedInt = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, (context, str, i) -> i);

        assertThat(passedInt).isEqualTo(INT_VALUE);
    }

    @Test
    public void getWithContext_threeArguments_passesActivityContext() throws Exception {
        boolean contextIsActivityContext = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, BOOLEAN_VALUE,
                (context, str, i, b) -> context != null);

        assertThat(contextIsActivityContext).isTrue();
    }

    @Test
    public void getWithContext_threeArguments_passesFirstArgument() throws Exception {
        String passedString = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, BOOLEAN_VALUE, (context, str, i, b) -> str);

        assertThat(passedString).isEqualTo(STRING_VALUE);
    }

    @Test
    public void getWithContext_threeArguments_passesSecondArgument() throws Exception {
        int passedInt = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, BOOLEAN_VALUE, (context, str, i, b) -> i);

        assertThat(passedInt).isEqualTo(INT_VALUE);
    }

    @Test
    public void getWithContext_threeArguments_passesThirdArgument() throws Exception {
        boolean passedBoolean = ActivityContext.getWithContext(
                STRING_VALUE, INT_VALUE, BOOLEAN_VALUE, (context, str, i, b) -> b);

        assertThat(passedBoolean).isEqualTo(BOOLEAN_VALUE);
    }

    @Test
    public void runWithContext_passesActivityContext() throws Exception {
        BlockingActivityConsumer callback = new BlockingActivityConsumer();

        ActivityContext.runWithContext(callback);

        assertThat(callback.await()).isTrue();
    }

    private static final class BlockingActivityConsumer extends BlockingCallback<Boolean> implements
            Consumer<ActivityContext> {
        @Override
        public void accept(ActivityContext activity) {
            callbackTriggered(activity != null);
        }
    }

    @Test
    public void runWithContext_oneArgument_passesActivityContext() throws Exception {
        BlockingActivityBiConsumerChecksActivity callback =
                new BlockingActivityBiConsumerChecksActivity();

        ActivityContext.runWithContext(STRING_VALUE, callback);

        assertThat(callback.await()).isTrue();
    }

    private static final class BlockingActivityBiConsumerChecksActivity
            extends BlockingCallback<Boolean> implements BiConsumer<ActivityContext, String> {
        @Override
        public void accept(ActivityContext activity, String s) {
            callbackTriggered(activity != null);
        }
    }

    @Test
    public void runWithContext_oneArgument_passesFirstArgument() throws Exception {
        BlockingActivityBiConsumerReturnsFirstArgument callback =
                new BlockingActivityBiConsumerReturnsFirstArgument();

        ActivityContext.runWithContext(STRING_VALUE, callback);

        assertThat(callback.await()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void runWithContext_throwsRuntimeException_isThrownHere() {
        assertThrows(RuntimeException.class, () -> {
            ActivityContext.runWithContext((context) -> {
                throw new RuntimeException();
            });
        });
    }

    @Test
    public void runWithContext_throwsError_isThrownHere() {
        assertThrows(AssertionError.class, () -> {
            ActivityContext.runWithContext((context) -> {
                throw new AssertionError();
            });
        });
    }

    @Test
    @RunWhenInstrumentingOtherApp
    public void getWithContext_notInstrumentingSelf_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> ActivityContext.getWithContext(Objects::nonNull));
    }

    private static final class BlockingActivityBiConsumerReturnsFirstArgument
            extends BlockingCallback<String> implements BiConsumer<ActivityContext, String> {
        @Override
        public void accept(ActivityContext activity, String s) {
            callbackTriggered(s);
        }
    }

}
