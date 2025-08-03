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

package android.test.example.parameterized.helloworld;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class HelloWorldTest {

    private static final String TAG = HelloWorldTest.class.getSimpleName();

    private final String mFirstWord;
    private final String mSecondWord;

    @Parameterized.Parameters
    public static Collection configs() {
        return Arrays.asList(
                new Object[][] {
                    {"hello", "world"},
                    {"Hello", "World"},
                    {"Hello", "World!"},
                });
    }

    public HelloWorldTest(String firstWord, String secondWord) {
        this.mFirstWord = firstWord;
        this.mSecondWord = secondWord;
    }

    @Test
    public void testHelloWorld() {
        Assert.assertNotEquals(this.mFirstWord, this.mSecondWord);
    }

    @Test
    public void testHalloWorld() {
        Assert.assertNotEquals(this.mFirstWord, this.mSecondWord);
    }
}
