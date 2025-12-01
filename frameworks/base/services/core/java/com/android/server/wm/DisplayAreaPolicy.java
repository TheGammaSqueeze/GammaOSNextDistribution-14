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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG;
import static android.view.WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;
import static android.view.WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE;
import static android.view.WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;
import static android.window.DisplayAreaOrganizer.FEATURE_FULLSCREEN_MAGNIFICATION;
import static android.window.DisplayAreaOrganizer.FEATURE_HIDE_DISPLAY_CUTOUT;
import static android.window.DisplayAreaOrganizer.FEATURE_IME_PLACEHOLDER;
import static android.window.DisplayAreaOrganizer.FEATURE_ONE_HANDED;
import static android.window.DisplayAreaOrganizer.FEATURE_WINDOWED_MAGNIFICATION;

import static com.android.server.wm.DisplayAreaPolicyBuilder.Feature;
import static com.android.server.wm.DisplayAreaPolicyBuilder.HierarchyBuilder;

import android.annotation.Nullable;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import android.util.Slog;
import android.os.SystemProperties;

/**
 * Policy that manages {@link DisplayArea}.
 */
public abstract class DisplayAreaPolicy {
    protected final WindowManagerService mWmService;

    /**
     * The {@link RootDisplayArea} of the whole logical display. All {@link DisplayArea}s must be
     * (direct or indirect) descendants of this area.
     */
    protected final RootDisplayArea mRoot;

    /**
     * Constructs a new {@link DisplayAreaPolicy}
     *
     * @param wmService the window manager service instance
     * @param root the root display area under which the policy operates
     */
    protected DisplayAreaPolicy(WindowManagerService wmService, RootDisplayArea root) {
        mWmService = wmService;
        mRoot = root;
    }

    /**
     * Called to ask the policy to attach the given {@link WindowToken} to the {@link DisplayArea}
     * hierarchy.
     *
     * <p>This must attach the token to {@link #mRoot} (or one of its descendants).
     */
    public abstract void addWindow(WindowToken token);

    /** Gets the {@link DisplayArea} with given window type and launched options */
    public abstract DisplayArea.Tokens findAreaForWindowType(int type, Bundle options,
            boolean ownerCanManageAppTokens, boolean roundedCornerOverlay);

    /**
     * Gets the set of {@link DisplayArea} that are created for the given feature to apply to.
     */
    public abstract List<DisplayArea<? extends WindowContainer>> getDisplayAreas(int featureId);

    /**
     * @return the default/fallback {@link TaskDisplayArea} on the display.
     */
    public abstract TaskDisplayArea getDefaultTaskDisplayArea();

    /** Returns the {@link TaskDisplayArea} specified by launch options. */
    public abstract TaskDisplayArea getTaskDisplayArea(@Nullable Bundle options);

    /** Provider for platform-default display area policy. */
    static final class DefaultProvider implements DisplayAreaPolicy.Provider {
        @Override
        public DisplayAreaPolicy instantiate(WindowManagerService wmService,
                DisplayContent content, RootDisplayArea root,
                DisplayArea.Tokens imeContainer) {
            // GammaOS: note secondary-home override + display flags at policy construction time.
            final TaskDisplayArea defaultTaskDisplayArea = new TaskDisplayArea(content, wmService,
                    "DefaultTaskDisplayArea", FEATURE_DEFAULT_TASK_CONTAINER);
            final List<TaskDisplayArea> tdaList = new ArrayList<>();
            tdaList.add(defaultTaskDisplayArea);

            // Define the features that will be supported under the root of the whole logical
            // display. The policy will build the DisplayArea hierarchy based on this.
            final HierarchyBuilder rootHierarchy = new HierarchyBuilder(root);
            // Set the essential containers (even if the display doesn't support IME).
            rootHierarchy.setImeContainer(imeContainer).setTaskDisplayAreas(tdaList);

            // GammaOS: debug the override + flags so we can correlate with mirroring later.
            final String gammaOverride =
                    android.os.SystemProperties.get("persist.gammaos.secondary_home", "").trim();
            final int diFlags = content.getDisplayInfo().flags;
            final boolean isTrusted =
                    (diFlags & android.view.Display.FLAG_TRUSTED) != 0;
            final boolean ownsGroup =
                    (diFlags & android.view.Display.FLAG_OWN_DISPLAY_GROUP) != 0;
            if (!TextUtils.isEmpty(gammaOverride)) {
                Slog.d("DisplayAreaPolicy", "GammaOS: policy instantiate on display "
                        + content.getDisplayId() + " override='" + gammaOverride
                        + "' trusted=" + isTrusted + " ownGroup=" + ownsGroup);
            }

            // GammaOS: Promote secondary displays to "trusted + decorated" when Desktop mode is forced.
            // This allows SystemUI nav/status bars and a HOME task on the external screen.
            // We keep default display behavior unchanged.
            final boolean gammaForceMirror = android.os.SystemProperties.getBoolean("persist.gammaos.ext.force_mirror", false);
            final boolean forceDesktop = wmService.mForceDesktopModeOnExternalDisplays && !gammaForceMirror;
            final boolean promoteDecor = content.isTrusted()
                    || (forceDesktop && !content.isDefaultDisplay);
            if (promoteDecor) {
                // This adds the system-decor feature layers expected by SystemUI.
                configureTrustedHierarchyBuilder(rootHierarchy, wmService, content);
            }

            // Instantiate the policy with the hierarchy defined above. This will create and attach
            // all the necessary DisplayAreas to the root.
            return new DisplayAreaPolicyBuilder().setRootHierarchy(rootHierarchy).build(wmService);
        }

        private void configureTrustedHierarchyBuilder(HierarchyBuilder rootHierarchy,
                WindowManagerService wmService, DisplayContent content) {
            // WindowedMagnification should be on the top so that there is only one surface
            // to be magnified.
            rootHierarchy.addFeature(new Feature.Builder(wmService.mPolicy, "WindowedMagnification",
                    FEATURE_WINDOWED_MAGNIFICATION)
                    .upTo(TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY)
                    .except(TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY)
                    // Make the DA dimmable so that the magnify window also mirrors the dim layer.
                    .setNewDisplayAreaSupplier(DisplayArea.Dimmable::new)
                    .build());
            if (content.isDefaultDisplay) {
                // Only default display can have cutout.
                // See LocalDisplayAdapter.LocalDisplayDevice#getDisplayDeviceInfoLocked.
                rootHierarchy.addFeature(new Feature.Builder(wmService.mPolicy, "HideDisplayCutout",
                        FEATURE_HIDE_DISPLAY_CUTOUT)
                        .all()
                        .except(TYPE_NAVIGATION_BAR, TYPE_NAVIGATION_BAR_PANEL, TYPE_STATUS_BAR,
                                TYPE_NOTIFICATION_SHADE)
                        .build())
                        .addFeature(new Feature.Builder(wmService.mPolicy, "OneHanded",
                                FEATURE_ONE_HANDED)
                                .all()
                                .except(TYPE_NAVIGATION_BAR, TYPE_NAVIGATION_BAR_PANEL,
                                        TYPE_SECURE_SYSTEM_OVERLAY)
                                .build());
            }
            rootHierarchy
                    .addFeature(new Feature.Builder(wmService.mPolicy, "FullscreenMagnification",
                            FEATURE_FULLSCREEN_MAGNIFICATION)
                            .all()
                            .except(TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY, TYPE_INPUT_METHOD,
                                    TYPE_INPUT_METHOD_DIALOG, TYPE_MAGNIFICATION_OVERLAY,
                                    TYPE_NAVIGATION_BAR, TYPE_NAVIGATION_BAR_PANEL)
                            .build())
                    .addFeature(new Feature.Builder(wmService.mPolicy, "ImePlaceholder",
                            FEATURE_IME_PLACEHOLDER)
                            .and(TYPE_INPUT_METHOD, TYPE_INPUT_METHOD_DIALOG)
                            .build());
        }
    }

    /**
     * Provider for {@link DisplayAreaPolicy} instances.
     *
     * <p>By implementing this interface and overriding the
     * {@code config_deviceSpecificDisplayAreaPolicyProvider}, a device-specific implementations
     * of {@link DisplayAreaPolicy} can be supplied.
     */
    public interface Provider {
        /**
         * Instantiates a new {@link DisplayAreaPolicy}. It should set up the {@link DisplayArea}
         * hierarchy.
         *
         * @see DisplayAreaPolicy#DisplayAreaPolicy
         */
        DisplayAreaPolicy instantiate(WindowManagerService wmService, DisplayContent content,
                RootDisplayArea root, DisplayArea.Tokens imeContainer);

        /**
         * Instantiates the device-specific {@link Provider}.
         */
        static Provider fromResources(Resources res) {
            String name = res.getString(
                    com.android.internal.R.string.config_deviceSpecificDisplayAreaPolicyProvider);
            if (TextUtils.isEmpty(name)) {
                return new DisplayAreaPolicy.DefaultProvider();
            }
            try {
                return (Provider) Class.forName(name).newInstance();
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IllegalStateException("Couldn't instantiate class " + name
                        + " for config_deviceSpecificDisplayAreaPolicyProvider:"
                        + " make sure it has a public zero-argument constructor"
                        + " and implements DisplayAreaPolicy.Provider", e);
            }
        }
    }
}
