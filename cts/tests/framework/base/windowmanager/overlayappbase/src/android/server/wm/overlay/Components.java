/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.overlay;

import android.content.ComponentName;
import android.os.Bundle;
import android.server.wm.component.ComponentsBase;


public class Components extends ComponentsBase {
    public interface UntrustedTouchTestService {
        ComponentName COMPONENT = component("UntrustedTouchTestService");
    }

    public interface OverlayActivity {
        ComponentName COMPONENT = component("OverlayActivity");
        String EXTRA_OPACITY = "opacity";
        String EXTRA_TOUCHABLE = "touchable";
        String EXTRA_TOKEN_RECEIVER = "token_receiver";
        String EXTRA_TOKEN = "token";
    }

    public interface ExitAnimationActivity {
        ComponentName COMPONENT = component("ExitAnimationActivity");
    }

    public interface ExitAnimationActivityReceiver {
        String ACTION_FINISH =
                "android.server.wm.overlay.ExitAnimationActivityReceiver.ACTION_FINISH";
        String EXTRA_ANIMATION = "animation";
        int EXTRA_VALUE_ANIMATION_EMPTY = 0;
        int EXTRA_VALUE_ANIMATION_0_7 = 1;
        int EXTRA_VALUE_ANIMATION_0_9 = 2;
        int EXTRA_VALUE_LONG_ANIMATION_0_7 = 3;
    }

    public interface ToastActivity {
        ComponentName COMPONENT = component("ToastActivity");
    }

    public interface TranslucentFloatingActivity {
        String ACTION_FINISH =
                "android.server.wm.overlay.TranslucentFloatingActivity.ACTION_FINISH";
        String EXTRA_FADE_EXIT =
                "android.server.wm.overlay.TranslucentFloatingActivity.ACTION_FINISH_FADE_EXIT";
        ComponentName BASE_COMPONENT = component("TranslucentFloatingActivity");
        static ComponentName getComponent(String packageName) {
            return new ComponentName(packageName, BASE_COMPONENT.getClassName());
        }
    }

    public interface TrampolineActivity {
        ComponentName BASE_COMPONENT = component("TrampolineActivity");
        String COMPONENTS_EXTRA = "components_extra";

        static ComponentName getComponent(String packageName) {
            return new ComponentName(packageName, BASE_COMPONENT.getClassName());
        }

        static Bundle buildTrampolineExtra(ComponentName... componentNames) {
            Bundle trampolineTarget = new Bundle();
            trampolineTarget.putParcelableArray(COMPONENTS_EXTRA, componentNames);
            return trampolineTarget;
        }

    }

    private static ComponentName component(String className) {
        return component(Components.class, className);
    }
}
