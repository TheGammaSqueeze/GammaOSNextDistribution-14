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

package com.android.systemui.car.systembar;

import static android.service.voice.VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE;

import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionListener;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;

import java.util.Set;

/**
 * AssistantButton is an UI component that will trigger the Voice Interaction Service.
 */
public class AssistantButton extends CarSystemBarButton {
    private static final String TAG = "AssistantButton";
    private final AssistUtils mAssistUtils;
    private final IVoiceInteractionSessionShowCallback mShowCallback =
            new IVoiceInteractionSessionShowCallback.Stub() {
                @Override
                public void onFailed() {
                    Log.w(TAG, "Failed to show VoiceInteractionSession");
                }

                @Override
                public void onShown() {
                    Log.d(TAG, "IVoiceInteractionSessionShowCallback onShown()");
                }
            };

    public AssistantButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAssistUtils = new AssistUtils(context);
        setOnClickListener(v -> showAssistant());
        mAssistUtils.registerVoiceInteractionSessionListener(
                new IVoiceInteractionSessionListener.Stub() {
                    @Override
                    public void onVoiceSessionShown() throws RemoteException {
                        assistantSetSelected(/* selected= */ true);
                    }

                    @Override
                    public void onVoiceSessionHidden() throws RemoteException {
                        assistantSetSelected(/* selected= */ false);
                    }

                    @Override
                    public void onVoiceSessionWindowVisibilityChanged(boolean visible)
                            throws RemoteException { }

                    @Override
                    public void onSetUiHints(Bundle hints) {
                    }
                }
        );
    }

    void showAssistant() {
        if (canShowTosAcceptanceFlow()) {
            SystemBarUtil.INSTANCE.showTosAcceptanceFlow(getContext(), getUserTracker());
            return;
        }
        final Bundle args = new Bundle();
        mAssistUtils.showSessionForActiveService(args,
                SHOW_SOURCE_ASSIST_GESTURE, mShowCallback, /*activityToken=*/ null);
    }

    /**
     * Helper method to check if tos acceptance flow can be launched. The tos flow can be launched
     * when there is no active assistant selected by the system and the default assistant has been
     * disabled because tos is unaccepted
     *
     * @return true if tos flow can be launched, false otherwise
     */
    private boolean canShowTosAcceptanceFlow() {
        ComponentName activeAssistantComponent = mAssistUtils.getActiveServiceComponentName();
        String defaultAssistantInConfig =
                getContext().getString(com.android.internal.R.string.config_defaultAssistant);
        Integer userId = getUserTracker() != null ? getUserTracker().getUserId() : null;
        Set<String> tosDisabledApps = SystemBarUtil.INSTANCE
                .getTosDisabledPackages(getContext(), userId);
        boolean defaultAssistantDisabled = tosDisabledApps.contains(defaultAssistantInConfig);

        return activeAssistantComponent == null && defaultAssistantDisabled;
    }

    @Override
    protected void setUpIntents(TypedArray typedArray) {
        // left blank because for the assistant button Intent will not be passed from the layout.
    }

    @Override
    protected String getRoleName() {
        return RoleManager.ROLE_ASSISTANT;
    }

    @Override
    public void setSelected(boolean selected) {
        // override to no-op as AssistantButton will maintain its own selected state by listening to
        // the actual voice interaction session.
    }

    private void assistantSetSelected(boolean selected) {
        if (hasSelectionState()) {
            getContext().getMainExecutor().execute(
                    () -> AssistantButton.super.setSelected(selected));
        }
    }
}
