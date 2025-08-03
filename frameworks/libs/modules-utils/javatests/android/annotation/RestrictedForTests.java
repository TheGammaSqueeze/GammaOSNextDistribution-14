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

package android.annotation;

import static com.google.common.truth.Truth.assertThat;

import android.annotation.RestrictedFor.Environment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RestrictedForTests {

    @Test
    public void testAnnotationAvailableInRuntime() throws Exception {
        ClassWithAnnotation clz = new ClassWithAnnotation();
        RestrictedFor annotation = clz.getClass().getAnnotation(RestrictedFor.class);

        assertThat(annotation).isNotNull();
    }

    @Test
    public void testAnnotationIsRepeatable() throws Exception {
        ClassWithRepeatedAnnotation clz = new ClassWithRepeatedAnnotation();
        RestrictedFor[] annotations = clz.getClass().getAnnotationsByType(RestrictedFor.class);

        assertThat(annotations).hasLength(2);
    }

    @Test
    public void testAnnotationParameters() throws Exception {
        ClassWithAnnotation clz = new ClassWithAnnotation();
        RestrictedFor annotation = clz.getClass().getAnnotation(RestrictedFor.class);

        Environment[] e = annotation.environments();
        assertThat(e).asList().containsExactly(Environment.SDK_SANDBOX);
        int from = annotation.from();
        assertThat(from).isEqualTo(33);
    }

    @Test
    public void testAnnotationParameters_environmentToString() throws Exception {
        ClassWithAnnotation clz = new ClassWithAnnotation();
        RestrictedFor annotation = clz.getClass().getAnnotation(RestrictedFor.class);

        Environment e = annotation.environments()[0];
        assertThat(e).isEqualTo(Environment.SDK_SANDBOX);
        assertThat(e.toString()).isEqualTo("SDK Runtime");
    }

    @Test
    public void testAnnotationParameters_environment_multipleEnvironments() throws Exception {
        ClassWithMultipleEnvironment clz = new ClassWithMultipleEnvironment();
        RestrictedFor annotation = clz.getClass().getAnnotation(RestrictedFor.class);

        Environment[] e = annotation.environments();
        assertThat(e).asList().containsExactly(Environment.SDK_SANDBOX, Environment.SDK_SANDBOX);
    }

    @RestrictedFor(environments=Environment.SDK_SANDBOX, from=33)
    private static class ClassWithAnnotation {
    }

    @RestrictedFor(environments=Environment.SDK_SANDBOX, from=0)
    @RestrictedFor(environments=Environment.SDK_SANDBOX, from=0)
    private static class ClassWithRepeatedAnnotation {
    }

    @RestrictedFor(environments={Environment.SDK_SANDBOX, Environment.SDK_SANDBOX}, from=0)
    private static class ClassWithMultipleEnvironment {
    }
}
