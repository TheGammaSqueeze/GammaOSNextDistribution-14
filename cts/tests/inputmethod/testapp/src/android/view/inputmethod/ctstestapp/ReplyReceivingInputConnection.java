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

package android.view.inputmethod.ctstestapp;


import android.os.Bundle;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.cts.util.MockTestActivityUtil;

/**
 * A special {@link InputConnection} to receive {@link UserHandle} from
 * {@link CtsBaseInputMethod} via {@link InputConnection#performPrivateCommand(String, Bundle)}.
 */
class ReplyReceivingInputConnection extends InputConnectionWrapper {

    public static final String EDITOR_INFO_KEY_REPLY_USER_HANDLE_SESSION_ID =
            "android.inputmethodservice.cts.ime.ReplyUserHandleSessionId";

    public static final String ACTION_KEY_REPLY_USER_HANDLE =
            MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE;

    public static final String BUNDLE_KEY_REPLY_USER_HANDLE =
            MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE;

    public static final String BUNDLE_KEY_REPLY_USER_HANDLE_SESSION_ID =
            "android.inputmethodservice.cts.ime.ReplyUserHandleSessionId";

    private final String mSessionKey;
    private final RemoteCallback mRemoteCallback;

    ReplyReceivingInputConnection(InputConnection target, String sessionKey,
            RemoteCallback remoteCallback) {
        super(target, false);
        mSessionKey = sessionKey;
        mRemoteCallback = remoteCallback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        if (TextUtils.equals(ACTION_KEY_REPLY_USER_HANDLE, action) && TextUtils.equals(mSessionKey,
                data.getString(BUNDLE_KEY_REPLY_USER_HANDLE_SESSION_ID))) {
            final UserHandle userHandle = data.getParcelable(BUNDLE_KEY_REPLY_USER_HANDLE,
                    UserHandle.class);
            if (userHandle != null) {
                Bundle b = new Bundle();
                b.putInt(ACTION_KEY_REPLY_USER_HANDLE, Process.myUserHandle().getIdentifier());
                mRemoteCallback.sendResult(b);
            }
            return true;
        }
        return super.performPrivateCommand(action, data);
    }
}
