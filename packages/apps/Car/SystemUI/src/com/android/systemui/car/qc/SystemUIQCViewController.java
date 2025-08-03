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

package com.android.systemui.car.qc;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.android.car.qc.controller.BaseQCController;
import com.android.car.qc.controller.LocalQCController;
import com.android.car.qc.controller.RemoteQCController;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.car.qc.view.QCView;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.lang.reflect.Constructor;
import java.util.Map;

import javax.inject.Provider;

/**
 * Class to control instances of {@link SystemUIQCView}. This controller is responsible for
 * attaching either a remote controller for the current user or a local controller using a dagger
 * injected constructor and fall back to a default {@link Context} constructor when not present.
 */
public final class SystemUIQCViewController extends CarSystemBarElementController<SystemUIQCView> {
    private static final String TAG = SystemUIQCViewController.class.getName();
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final Map<Class<?>, Provider<BaseLocalQCProvider>> mLocalQCProviderCreators;
    private BaseQCController mController;
    private boolean mUserChangedCallbackRegistered;

    private final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onUserChanged(int newUser, Context userContext) {
            rebindController();
        }
    };

    @AssistedInject
    public SystemUIQCViewController(@Assisted SystemUIQCView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController, Context context,
            UserTracker userTracker,
            Map<Class<?>, Provider<BaseLocalQCProvider>> localQCProviderCreators) {
        super(view, disableController, stateController);
        mContext = context;
        mUserTracker = userTracker;
        mLocalQCProviderCreators = localQCProviderCreators;
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<SystemUIQCView, SystemUIQCViewController> {}

    @Override
    protected void onInit() {
        bindQCView();
    }

    @Override
    @CallSuper
    protected void onViewAttached() {
        super.onViewAttached();
        listen(true);
    }

    @Override
    @CallSuper
    protected void onViewDetached() {
        super.onViewDetached();
        listen(false);
    }

    /**
     * Sets the action listener on the QCView.
     */
    public void setActionListener(QCView.QCActionListener listener) {
        mView.setActionListener(listener);
    }

    /**
     * Destroys the current QCView and associated controller. Should be called when the anchor
     * view is no longer attached.
     */
    public void destroyQCViews() {
        resetViewAndController();
        if (mUserChangedCallbackRegistered) {
            mUserTracker.removeCallback(mUserChangedCallback);
            mUserChangedCallbackRegistered = false;
        }
    }

    private void bindQCView() {
        if (mView.getRemoteUriString() != null) {
            Uri uri = Uri.parse(mView.getRemoteUriString());
            if (uri.getUserInfo() == null) {
                // To bind to the content provider as the current user rather than user 0 (which
                // SystemUI is running on), add the current user id followed by the '@' symbol
                // before the Uri's authority.
                uri = uri.buildUpon().authority(
                        String.format("%s@%s", mUserTracker.getUserId(),
                                uri.getAuthority())).build();
            }
            bindRemoteQCView(uri);
            if (!mUserChangedCallbackRegistered) {
                mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
                mUserChangedCallbackRegistered = true;
            }
        } else if (mView.getLocalClassString() != null) {
            bindLocalQCView(mView.getLocalClassString());
        }
    }

    /**
     * Toggles whether this view should listen to live updates.
     */
    private void listen(boolean shouldListen) {
        if (mController != null) {
            mController.listen(shouldListen);
        }
    }

    private void rebindController() {
        resetViewAndController();
        bindQCView();
    }

    private void resetViewAndController() {
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
        if (mView != null) {
            mView.onChanged(/* qcItem= */ null);
        }
    }

    private void bindRemoteQCView(Uri uri) {
        mController = new RemoteQCController(mContext, uri);
        mController.addObserver(mView);
        mController.bind();
    }

    private void bindLocalQCView(String localClass) {
        BaseLocalQCProvider localQCProvider = createLocalQCProviderInstance(localClass, mContext);
        mController = new LocalQCController(mContext, localQCProvider);
        mController.addObserver(mView);
        mController.bind();
    }

    private BaseLocalQCProvider createLocalQCProviderInstance(String controllerName,
            Context context) {
        try {
            BaseLocalQCProvider injectedProvider = resolveInjectedLocalQCProviderInstance(
                    controllerName);
            if (injectedProvider != null) {
                return injectedProvider;
            }
            Class<?> clazz = Class.forName(controllerName);
            Constructor<?> providerConstructor = clazz.getConstructor(Context.class);
            return (BaseLocalQCProvider) providerConstructor.newInstance(context);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Invalid controller: " + controllerName, e);
        }
    }

    private BaseLocalQCProvider resolveInjectedLocalQCProviderInstance(@Nullable String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Provider<BaseLocalQCProvider> provider = mLocalQCProviderCreators.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Could not find class " + className);
            return null;
        }
    }
}
