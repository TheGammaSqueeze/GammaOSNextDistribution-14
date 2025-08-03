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

package platform.test.runner.parameterized;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for fields of the test class which will be initialized by the method annotated by
 * {@link Parameters}.<br>
 * By using this annotation, the test class constructor isn't needed.<br>
 * Index range must start at 0. Default value is 0.
 *
 * <p>{@see org.junit.runners.Parameterized.Parameter}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Parameter {
    /**
     * Method that returns the index of the parameter in the array returned by the method annotated
     * by {@link Parameters}.<br>
     * Index range must start at 0. Default value is 0.
     *
     * @return the index of the parameter.
     */
    int value() default 0;
}
