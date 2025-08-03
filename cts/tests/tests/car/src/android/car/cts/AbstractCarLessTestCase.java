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
package android.car.cts;

import android.car.test.AbstractExpectableTestCase;

/**
 * Base class for tests that don't need to connect to a {@link android.car.Car} object.
 *
 * <p>Typically used to test POJO-like (Plain-Old Java Objects) classes; for tests that need a
 * {@link android.car.Car} object, use {@link AbstractCarTestCase} instead.
 */
abstract class AbstractCarLessTestCase extends AbstractExpectableTestCase {

    private static final String TAG = AbstractCarLessTestCase.class.getSimpleName();
}
