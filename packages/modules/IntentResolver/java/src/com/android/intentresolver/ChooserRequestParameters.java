/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static java.util.Objects.requireNonNullElse;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PatternMatcher;
import android.service.chooser.ChooserAction;
import android.service.chooser.ChooserTarget;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.intentresolver.util.UriFilters;

import com.google.common.collect.ImmutableList;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility to parse and validate parameters from the client-supplied {@link Intent} that launched
 * the Sharesheet {@link ChooserActivity}. The validated parameters are stored as immutable ivars.
 *
 * TODO: field nullability in this class reflects legacy use, and typically would indicate that the
 * client's intent didn't provide the respective data. In some cases we may be able to provide
 * defaults instead of nulls -- especially for methods that return nullable lists or arrays, if the
 * client code could instead handle empty collections equally well.
 *
 * TODO: some of these fields (especially getTargetIntent() and any other getters that delegate to
 * it internally) differ from the legacy model because they're computed directly from the initial
 * Chooser intent, where in the past they've been relayed up to ResolverActivity and then retrieved
 * through methods on the base class. The base always seems to return them exactly as they were
 * provided, so this should be safe -- and clients can reasonably switch to retrieving through these
 * parameters instead. For now, the other convention is still used in some places. Ideally we'd like
 * to normalize on a single source of truth, but we'll have to clean up the delegation up to the
 * resolver (or perhaps this needs to be a subclass of some `ResolverRequestParameters` class?).
 */
public class ChooserRequestParameters {
    private static final String TAG = "ChooserActivity";

    private static final int LAUNCH_FLAGS_FOR_SEND_ACTION =
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
    private static final int MAX_CHOOSER_ACTIONS = 5;

    private final Intent mTarget;
    private final String mReferrerPackageName;
    private final Pair<CharSequence, Integer> mTitleSpec;
    private final Intent mReferrerFillInIntent;
    private final ImmutableList<ComponentName> mFilteredComponentNames;
    private final ImmutableList<ChooserTarget> mCallerChooserTargets;
    private final @NonNull ImmutableList<ChooserAction> mChooserActions;
    private final ChooserAction mModifyShareAction;
    private final boolean mRetainInOnStop;

    @Nullable
    private final ImmutableList<Intent> mAdditionalTargets;

    @Nullable
    private final Bundle mReplacementExtras;

    @Nullable
    private final ImmutableList<Intent> mInitialIntents;

    @Nullable
    private final IntentSender mChosenComponentSender;

    @Nullable
    private final IntentSender mRefinementIntentSender;

    @Nullable
    private final String mSharedText;

    @Nullable
    private final IntentFilter mTargetIntentFilter;

    @Nullable
    private final CharSequence mMetadataText;

    public ChooserRequestParameters(
            final Intent clientIntent,
            String referrerPackageName,
            final Uri referrer) {
        final Intent requestedTarget = parseTargetIntentExtra(
                clientIntent.getParcelableExtra(Intent.EXTRA_INTENT));
        mTarget = intentWithModifiedLaunchFlags(requestedTarget);

        mReferrerPackageName = referrerPackageName;

        mAdditionalTargets = intentsWithModifiedLaunchFlagsFromExtraIfPresent(
                clientIntent, Intent.EXTRA_ALTERNATE_INTENTS);

        mReplacementExtras = clientIntent.getBundleExtra(Intent.EXTRA_REPLACEMENT_EXTRAS);

        mTitleSpec = makeTitleSpec(
                clientIntent.getCharSequenceExtra(Intent.EXTRA_TITLE),
                isSendAction(mTarget.getAction()));

        mInitialIntents = intentsWithModifiedLaunchFlagsFromExtraIfPresent(
                clientIntent, Intent.EXTRA_INITIAL_INTENTS);

        mReferrerFillInIntent = new Intent().putExtra(Intent.EXTRA_REFERRER, referrer);

        mChosenComponentSender =
                Optional.ofNullable(
                        clientIntent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER,
                                IntentSender.class))
                        .orElse(clientIntent.getParcelableExtra(
                                Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER,
                                IntentSender.class));

        mRefinementIntentSender = clientIntent.getParcelableExtra(
                Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER);

        ComponentName[] filteredComponents = clientIntent.getParcelableArrayExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS, ComponentName.class);
        mFilteredComponentNames = filteredComponents != null
                ? ImmutableList.copyOf(filteredComponents)
                : ImmutableList.of();

        mCallerChooserTargets = parseCallerTargetsFromClientIntent(clientIntent);

        mRetainInOnStop = clientIntent.getBooleanExtra(
                ChooserActivity.EXTRA_PRIVATE_RETAIN_IN_ON_STOP, false);

        mSharedText = mTarget.getStringExtra(Intent.EXTRA_TEXT);

        mTargetIntentFilter = getTargetIntentFilter(mTarget);

        mChooserActions = getChooserActions(clientIntent);
        mModifyShareAction = getModifyShareAction(clientIntent);

        if (android.service.chooser.Flags.enableSharesheetMetadataExtra()) {
            mMetadataText = clientIntent.getCharSequenceExtra(Intent.EXTRA_METADATA_TEXT);
        } else {
            mMetadataText = null;
        }
    }

    public Intent getTargetIntent() {
        return mTarget;
    }

    @Nullable
    public String getTargetAction() {
        return getTargetIntent().getAction();
    }

    public boolean isSendActionTarget() {
        return isSendAction(getTargetAction());
    }

    @Nullable
    public String getTargetType() {
        return getTargetIntent().getType();
    }

    public String getReferrerPackageName() {
        return mReferrerPackageName;
    }

    @Nullable
    public CharSequence getTitle() {
        return mTitleSpec.first;
    }

    public int getDefaultTitleResource() {
        return mTitleSpec.second;
    }

    public Intent getReferrerFillInIntent() {
        return mReferrerFillInIntent;
    }

    public ImmutableList<ComponentName> getFilteredComponentNames() {
        return mFilteredComponentNames;
    }

    public ImmutableList<ChooserTarget> getCallerChooserTargets() {
        return mCallerChooserTargets;
    }

    @NonNull
    public ImmutableList<ChooserAction> getChooserActions() {
        return mChooserActions;
    }

    @Nullable
    public ChooserAction getModifyShareAction() {
        return mModifyShareAction;
    }

    /**
     * Whether the {@link ChooserActivity#EXTRA_PRIVATE_RETAIN_IN_ON_STOP} behavior was requested.
     */
    public boolean shouldRetainInOnStop() {
        return mRetainInOnStop;
    }

    /**
     * TODO: this returns a nullable array for convenience, but if the legacy APIs can be
     * refactored, returning {@link #mAdditionalTargets} directly is simpler and safer.
     */
    @Nullable
    public Intent[] getAdditionalTargets() {
        return (mAdditionalTargets == null) ? null : mAdditionalTargets.toArray(new Intent[0]);
    }

    @Nullable
    public Bundle getReplacementExtras() {
        return mReplacementExtras;
    }

    /**
     * TODO: this returns a nullable array for convenience, but if the legacy APIs can be
     * refactored, returning {@link #mInitialIntents} directly is simpler and safer.
     */
    @Nullable
    public Intent[] getInitialIntents() {
        return (mInitialIntents == null) ? null : mInitialIntents.toArray(new Intent[0]);
    }

    @Nullable
    public IntentSender getChosenComponentSender() {
        return mChosenComponentSender;
    }

    @Nullable
    public IntentSender getRefinementIntentSender() {
        return mRefinementIntentSender;
    }

    @Nullable
    public String getSharedText() {
        return mSharedText;
    }

    @Nullable
    public IntentFilter getTargetIntentFilter() {
        return mTargetIntentFilter;
    }

    @Nullable
    public CharSequence getMetadataText() {
        return mMetadataText;
    }

    private static boolean isSendAction(@Nullable String action) {
        return (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action));
    }

    private static Intent parseTargetIntentExtra(@Nullable Parcelable targetParcelable) {
        if (targetParcelable instanceof Uri) {
            try {
                targetParcelable = Intent.parseUri(targetParcelable.toString(),
                        Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("Failed to parse EXTRA_INTENT from URI", ex);
            }
        }

        if (!(targetParcelable instanceof Intent)) {
            throw new IllegalArgumentException(
                    "EXTRA_INTENT is neither an Intent nor a Uri: " + targetParcelable);
        }

        return ((Intent) targetParcelable);
    }

    private static Intent intentWithModifiedLaunchFlags(Intent intent) {
        if (isSendAction(intent.getAction())) {
            intent.addFlags(LAUNCH_FLAGS_FOR_SEND_ACTION);
        }
        return intent;
    }

    /**
     * Build a pair of values specifying the title to use from the client request. The first
     * ({@link CharSequence}) value is the client-specified title, if there was one and their
     * requested target <em>wasn't</em> a send action; otherwise it is null. The second value is
     * the resource ID of a default title string; this is nonzero only if the first value is null.
     *
     * TODO: change the API for how these are passed up to {@link ResolverActivity#onCreate}, or
     * create a real type (not {@link Pair}) to express the semantics described in this comment.
     */
    private static Pair<CharSequence, Integer> makeTitleSpec(
            @Nullable CharSequence requestedTitle, boolean hasSendActionTarget) {
        if (hasSendActionTarget && (requestedTitle != null)) {
            // Do not allow the title to be changed when sharing content
            Log.w(TAG, "Ignoring intent's EXTRA_TITLE, deprecated in P. You may wish to set a"
                    + " preview title by using EXTRA_TITLE property of the wrapped"
                    + " EXTRA_INTENT.");
            requestedTitle = null;
        }

        int defaultTitleRes = (requestedTitle == null) ? R.string.chooseActivity : 0;

        return Pair.create(requestedTitle, defaultTitleRes);
    }

    private static ImmutableList<ChooserTarget> parseCallerTargetsFromClientIntent(
            Intent clientIntent) {
        return
                streamParcelableArrayExtra(
                        clientIntent, Intent.EXTRA_CHOOSER_TARGETS, ChooserTarget.class, true, true)
                .collect(toImmutableList());
    }

    @NonNull
    private static ImmutableList<ChooserAction> getChooserActions(Intent intent) {
        return streamParcelableArrayExtra(
                intent,
                Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS,
                ChooserAction.class,
                true,
                true)
                .filter(UriFilters::hasValidIcon)
                .limit(MAX_CHOOSER_ACTIONS)
                .collect(toImmutableList());
    }

    @Nullable
    private static ChooserAction getModifyShareAction(Intent intent) {
        try {
            return intent.getParcelableExtra(
                    Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION,
                    ChooserAction.class);
        } catch (Throwable t) {
            Log.w(
                    TAG,
                    "Unable to retrieve Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION argument",
                    t);
            return null;
        }
    }

    private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
        return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
    }

    @Nullable
    private static ImmutableList<Intent> intentsWithModifiedLaunchFlagsFromExtraIfPresent(
            Intent clientIntent, String extra) {
        Stream<Intent> intents =
                streamParcelableArrayExtra(clientIntent, extra, Intent.class, true, false);
        if (intents == null) {
            return null;
        }
        return intents
                .map(ChooserRequestParameters::intentWithModifiedLaunchFlags)
                .collect(toImmutableList());
    }

    /**
     * Make a {@link Stream} of the {@link Parcelable} objects given in the provided {@link Intent}
     * as the optional parcelable array extra with key {@code extra}. The stream elements, if any,
     * are all of the type specified by {@code clazz}.
     *
     * @param intent The intent that may contain the optional extras.
     * @param extra The extras key to identify the parcelable array.
     * @param clazz A class that is assignable from any elements in the result stream.
     * @param warnOnTypeError Whether to log a warning (and ignore) if the client extra doesn't have
     * the required type. If false, throw an {@link IllegalArgumentException} if the extra is
     * non-null but can't be assigned to variables of type {@code T}.
     * @param streamEmptyIfNull Whether to return an empty stream if the optional extra isn't
     * present in the intent (or if it had the wrong type, but <em>warnOnTypeError</em> is true).
     * If false, return null in these cases, and only return an empty stream if the intent
     * explicitly provided an empty array for the specified extra.
     */
    @Nullable
    private static <T extends Parcelable> Stream<T> streamParcelableArrayExtra(
            final Intent intent,
            String extra,
            @NonNull Class<T> clazz,
            boolean warnOnTypeError,
            boolean streamEmptyIfNull) {
        T[] result = null;

        try {
            result = getParcelableArrayExtraIfPresent(intent, extra, clazz);
        } catch (IllegalArgumentException e) {
            if (warnOnTypeError) {
                Log.w(TAG, "Ignoring client-requested " + extra, e);
            } else {
                throw e;
            }
        }

        if (result != null) {
            return Arrays.stream(result);
        } else if (streamEmptyIfNull) {
            return Stream.empty();
        } else {
            return null;
        }
    }

    /**
     * If the specified {@code extra} is provided in the {@code intent}, cast it to type {@code T[]}
     * or throw an {@code IllegalArgumentException} if the cast fails. If the {@code extra} isn't
     * present in the {@code intent}, return null.
     */
    @Nullable
    private static <T extends Parcelable> T[] getParcelableArrayExtraIfPresent(
            final Intent intent, String extra, @NonNull Class<T> clazz) throws
                    IllegalArgumentException {
        if (!intent.hasExtra(extra)) {
            return null;
        }

        T[] castResult = intent.getParcelableArrayExtra(extra, clazz);
        if (castResult == null) {
            Parcelable[] actualExtrasArray = intent.getParcelableArrayExtra(extra);
            if (actualExtrasArray != null) {
                throw new IllegalArgumentException(
                        String.format(
                                "%s is not of type %s[]: %s",
                                extra,
                                clazz.getSimpleName(),
                                Arrays.toString(actualExtrasArray)));
            } else if (intent.getParcelableExtra(extra) != null) {
                throw new IllegalArgumentException(
                        String.format(
                                "%s is not of type %s[] (or any array type): %s",
                                extra,
                                clazz.getSimpleName(),
                                intent.getParcelableExtra(extra)));
            } else {
                throw new IllegalArgumentException(
                        String.format(
                                "%s is not of type %s (or any Parcelable type): %s",
                                extra,
                                clazz.getSimpleName(),
                                intent.getExtras().get(extra)));
            }
        }

        return castResult;
    }

    private static IntentFilter getTargetIntentFilter(final Intent intent) {
        try {
            String dataString = intent.getDataString();
            if (intent.getType() == null) {
                if (!TextUtils.isEmpty(dataString)) {
                    return new IntentFilter(intent.getAction(), dataString);
                }
                Log.e(TAG, "Failed to get target intent filter: intent data and type are null");
                return null;
            }
            IntentFilter intentFilter = new IntentFilter(intent.getAction(), intent.getType());
            List<Uri> contentUris = new ArrayList<>();
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    contentUris.add(uri);
                }
            } else {
                List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris != null) {
                    contentUris.addAll(uris);
                }
            }
            for (Uri uri : contentUris) {
                intentFilter.addDataScheme(uri.getScheme());
                intentFilter.addDataAuthority(uri.getAuthority(), null);
                intentFilter.addDataPath(uri.getPath(), PatternMatcher.PATTERN_LITERAL);
            }
            return intentFilter;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get target intent filter", e);
            return null;
        }
    }
}
