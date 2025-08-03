/*
 * Copyright (C) 2016 The Android Open Source Project
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


package com.android.intentresolver;

import static android.content.Context.ACTIVITY_SERVICE;

import static java.util.stream.Collectors.toList;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.intentresolver.chooser.DisplayResolveInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Shows a dialog with actions to take on a chooser target.
 */
public class ChooserTargetActionsDialogFragment extends DialogFragment
        implements DialogInterface.OnClickListener {

    protected final static String TARGET_DETAILS_FRAGMENT_TAG = "targetDetailsFragment";

    private final List<DisplayResolveInfo> mTargetInfos;
    private final UserHandle mUserHandle;
    private final boolean mIsShortcutPinned;

    @Nullable
    private final String mShortcutId;

    @Nullable
    private final String mShortcutTitle;

    @Nullable
    private final IntentFilter mIntentFilter;

    public static void show(
            FragmentManager fragmentManager,
            List<DisplayResolveInfo> targetInfos,
            UserHandle userHandle,
            @Nullable String shortcutId,
            @Nullable String shortcutTitle,
            boolean isShortcutPinned,
            @Nullable IntentFilter intentFilter) {
        ChooserTargetActionsDialogFragment fragment = new ChooserTargetActionsDialogFragment(
                targetInfos,
                userHandle,
                shortcutId,
                shortcutTitle,
                isShortcutPinned,
                intentFilter);
        fragment.show(fragmentManager, TARGET_DETAILS_FRAGMENT_TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // Bail. It's probably not possible to trigger reloading our fragments from a saved
            // instance since Sharesheet isn't kept in history and the entire session will probably
            // be lost under any conditions that would've triggered our retention. Nevertheless, if
            // we ever *did* try to load from a saved state, we wouldn't be able to populate valid
            // data (since we wouldn't be able to get back our original TargetInfos if we had to
            // restore them from a Bundle).
            dismissAllowingStateLoss();
        }
    }

    /**
     * Build the menu UI according to our design spec.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
            @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        // Make the background transparent to show dialog rounding
        Optional.of(getDialog()).map(Dialog::getWindow)
                .ifPresent(window -> {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                });

        // Fetch UI details from target info
        List<Pair<Drawable, CharSequence>> items = mTargetInfos.stream().map(dri -> {
            return new Pair<>(getItemIcon(dri), getItemLabel(dri));
        }).collect(toList());

        View v = inflater.inflate(R.layout.chooser_dialog, container, false);

        TextView title = v.findViewById(com.android.internal.R.id.title);
        ImageView icon = v.findViewById(com.android.internal.R.id.icon);
        RecyclerView rv = v.findViewById(com.android.internal.R.id.listContainer);

        final TargetPresentationGetter pg = getProvidingAppPresentationGetter();
        title.setText(isShortcutTarget() ? mShortcutTitle : pg.getLabel());
        icon.setImageDrawable(pg.getIcon(mUserHandle));
        rv.setAdapter(new VHAdapter(items));

        return v;
    }

    @Override
    public void onStop() {
        super.onStop();
        dismissAllowingStateLoss();
    }

    class VHAdapter extends RecyclerView.Adapter<VH> {

        List<Pair<Drawable, CharSequence>> mItems;

        VHAdapter(List<Pair<Drawable, CharSequence>> items) {
            mItems = items;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.chooser_dialog_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(mItems.get(position), position);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    class VH extends RecyclerView.ViewHolder {
        TextView mLabel;
        ImageView mIcon;

        VH(@NonNull View itemView) {
            super(itemView);
            mLabel = itemView.findViewById(com.android.internal.R.id.text);
            mIcon = itemView.findViewById(com.android.internal.R.id.icon);
        }

        public void bind(Pair<Drawable, CharSequence> item, int position) {
            mLabel.setText(item.second);

            if (item.first == null) {
                mIcon.setVisibility(View.GONE);
            } else {
                mIcon.setVisibility(View.VISIBLE);
                mIcon.setImageDrawable(item.first);
            }

            itemView.setOnClickListener(v -> onClick(getDialog(), position));
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (isShortcutTarget()) {
            toggleShortcutPinned(mTargetInfos.get(which).getResolvedComponentName());
        } else {
            pinComponent(mTargetInfos.get(which).getResolvedComponentName());
        }
        ((PackagesChangedListener) getActivity()).handlePackagesChanged();
        dismiss();
    }

    private void toggleShortcutPinned(ComponentName name) {
        if (mIntentFilter == null) {
            return;
        }
        // Fetch existing pinned shortcuts of the given package.
        List<String> pinnedShortcuts = getPinnedShortcutsFromPackageAsUser(getContext(),
                mUserHandle, mIntentFilter, name.getPackageName());
        // If the shortcut has already been pinned, unpin it; otherwise, pin it.
        if (mIsShortcutPinned) {
            pinnedShortcuts.remove(mShortcutId);
        } else {
            pinnedShortcuts.add(mShortcutId);
        }
        // Update pinned shortcut list in ShortcutService via LauncherApps
        getContext().getSystemService(LauncherApps.class).pinShortcuts(
                name.getPackageName(), pinnedShortcuts, mUserHandle);
    }

    private static List<String> getPinnedShortcutsFromPackageAsUser(Context context,
            UserHandle user, IntentFilter filter, String packageName) {
        Context contextAsUser = context.createContextAsUser(user, 0 /* flags */);
        List<ShortcutManager.ShareShortcutInfo> targets = contextAsUser.getSystemService(
                ShortcutManager.class).getShareTargets(filter);
        return targets.stream()
                .map(ShortcutManager.ShareShortcutInfo::getShortcutInfo)
                .filter(s -> s.isPinned() && s.getPackage().equals(packageName))
                .map(ShortcutInfo::getId)
                .collect(Collectors.toList());
    }

    private void pinComponent(ComponentName name) {
        SharedPreferences sp = ChooserActivity.getPinnedSharedPrefs(getContext());
        final String key = name.flattenToString();
        boolean currentVal = sp.getBoolean(name.flattenToString(), false);
        if (currentVal) {
            sp.edit().remove(key).apply();
        } else {
            sp.edit().putBoolean(key, true).apply();
        }
    }

    private Drawable getPinIcon(boolean isPinned) {
        return isPinned
                ? getContext().getDrawable(com.android.internal.R.drawable.ic_close)
                : getContext().getDrawable(R.drawable.ic_chooser_pin_dialog);
    }

    private CharSequence getPinLabel(boolean isPinned, CharSequence targetLabel) {
        return isPinned
                ? getResources().getString(R.string.unpin_specific_target, targetLabel)
                : getResources().getString(R.string.pin_specific_target, targetLabel);
    }

    @NonNull
    protected CharSequence getItemLabel(DisplayResolveInfo dri) {
        final PackageManager pm = getContext().getPackageManager();
        return getPinLabel(isPinned(dri),
                isShortcutTarget() ? mShortcutTitle : dri.getResolveInfo().loadLabel(pm));
    }

    @Nullable
    protected Drawable getItemIcon(DisplayResolveInfo dri) {
        return getPinIcon(isPinned(dri));
    }

    private TargetPresentationGetter getProvidingAppPresentationGetter() {
        final ActivityManager am = (ActivityManager) getContext()
                .getSystemService(ACTIVITY_SERVICE);
        final int iconDpi = am.getLauncherLargeIconDensity();

        // Use the matching application icon and label for the title, any TargetInfo will do
        return new TargetPresentationGetter.Factory(getContext(), iconDpi)
                .makePresentationGetter(mTargetInfos.get(0).getResolveInfo());
    }

    private boolean isPinned(DisplayResolveInfo dri) {
        return isShortcutTarget() ? mIsShortcutPinned : dri.isPinned();
    }

    private boolean isShortcutTarget() {
        return mShortcutId != null;
    }

    protected ChooserTargetActionsDialogFragment(
            List<DisplayResolveInfo> targetInfos, UserHandle userHandle) {
        this(targetInfos, userHandle, null, null, false, null);
    }

    private ChooserTargetActionsDialogFragment(
            List<DisplayResolveInfo> targetInfos,
            UserHandle userHandle,
            @Nullable String shortcutId,
            @Nullable String shortcutTitle,
            boolean isShortcutPinned,
            @Nullable IntentFilter intentFilter) {
        mTargetInfos = targetInfos;
        mUserHandle = userHandle;
        mShortcutId = shortcutId;
        mShortcutTitle = shortcutTitle;
        mIsShortcutPinned = isShortcutPinned;
        mIntentFilter = intentFilter;
    }
}
