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

package android.server.wm.jetpack.second;

import android.content.ComponentName;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {

    public static final ComponentName SECOND_ACTIVITY = component("SecondActivity");

    public static final ComponentName PORTRAIT_ACTIVITY = component("PortraitActivity");

    public static final ComponentName SECOND_ACTIVITY_UNKNOWN_EMBEDDING_CERTS =
            component("SecondActivityUnknownEmbeddingCerts");

    public static final ComponentName SECOND_UNTRUSTED_EMBEDDING_ACTIVITY =
            component("SecondActivityAllowsUntrustedEmbedding");

    public static final ComponentName SECOND_UNTRUSTED_EMBEDDING_ACTIVITY_STATE_SHARE =
            component("SecondActivityAllowsUntrustedEmbeddingStateShare");

    public static final String EXTRA_LAUNCH_NON_EMBEDDABLE_ACTIVITY = "launch_non_embeddable";

    public static final String ACTION_ENTER_PIP = "enter_pip";

    public static final String ACTION_EXIT_PIP = "exit_pip";

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }
}
