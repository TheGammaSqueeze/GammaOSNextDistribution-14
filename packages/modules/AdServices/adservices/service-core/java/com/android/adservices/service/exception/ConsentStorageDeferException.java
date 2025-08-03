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

package com.android.adservices.service.exception;

import java.io.IOException;

/**
 * Exception class to indicate operation is not supported by current storage manager. This exception
 * will always be caught in {@link ConsentCompositeStorage}, and ConsentCompositeStorage will call
 * the next IConsentStorage instance to get/set the right value.
 */
public final class ConsentStorageDeferException extends IOException {}
