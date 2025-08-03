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

package com.example.classes;

public class InnerClasses {
    public class PublicNestedClass {}
    protected class ProtectedNestedClass {}
    private class PrivateNestedClass {}
    class NestedClass {}

    public interface PublicNestedInterface {}
    protected interface ProtectedNestedInterface {}
    private interface PrivateNestedInterface {}
    interface NestedInterface {}

    public enum PublicNestedEnum {}
    protected enum ProtectedNestedEnum {}
    private enum PrivateNestedEnum {}
    enum NestedEnum {}

    public @interface PublicNestedAnnotation {}
    protected @interface ProtectedNestedAnnotation {}
    private @interface PrivateNestedAnnotation {}
    @interface NestedAnnotation {}
}
