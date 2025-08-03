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

package libcore.libcore.internal;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import libcore.internal.Java11LanguageFeatures;

import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class Java11LanguageFeaturesTest {

    @Test
    public void testVarForLambda() {
        assertEquals("ONE TWO THREE",
                Java11LanguageFeatures.collectUpperCaseStrings(
                        Arrays.asList("one", "two", "three")));
    }

    @Test
    public void testLocalVariableTypeInference() {
        assertEquals("", Java11LanguageFeatures.guessTheString("42"));
        assertEquals("The answer to the universe, life and everything",
                Java11LanguageFeatures.guessTheString(
                        "The answer to the universe, life and everything"));
    }

    @Test
    @Ignore("b/210843415")
    public void testReflectionOnPrivateNestmate() throws Throwable {
        Java11LanguageFeatures.Person person = new Java11LanguageFeatures.Person("R2D2");
        assertEquals("Hello R2D2", person.greet());
    }
}
