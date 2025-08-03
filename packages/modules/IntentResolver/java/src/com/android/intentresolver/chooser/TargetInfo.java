/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.intentresolver.chooser;


import android.app.Activity;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.chooser.ChooserTarget;
import android.text.TextUtils;
import android.util.HashedStringCache;

import androidx.annotation.Nullable;

import com.android.intentresolver.ChooserListAdapter;
import com.android.intentresolver.ChooserRefinementManager;
import com.android.intentresolver.ResolverActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single target as represented in the chooser.
 */
public interface TargetInfo {

    /**
     * Container for a {@link TargetInfo}'s (potentially) mutable icon state. This is provided to
     * encapsulate the state so that the {@link TargetInfo} itself can be "immutable" (in some
     * sense) as long as it always returns the same {@link IconHolder} instance.
     *
     * TODO: move "stateful" responsibilities out to clients; for more info see the Javadoc comment
     * on {@link #getDisplayIconHolder()}.
     */
    interface IconHolder {
        /** @return the icon (if it's already loaded, or statically available), or null. */
        @Nullable
        Drawable getDisplayIcon();

        /**
         * @param icon the icon to return on subsequent calls to {@link #getDisplayIcon()}.
         * Implementations may discard this request as a no-op if they don't support setting.
         */
        void setDisplayIcon(Drawable icon);
    }

    /** A simple mutable-container implementation of {@link IconHolder}. */
    final class SettableIconHolder implements IconHolder {
        @Nullable
        private Drawable mDisplayIcon;

        @Nullable
        public Drawable getDisplayIcon() {
            return mDisplayIcon;
        }

        public void setDisplayIcon(Drawable icon) {
            mDisplayIcon = icon;
        }
    }

    /**
     * Get the resolved intent that represents this target. Note that this may not be the
     * intent that will be launched by calling one of the <code>start</code> methods provided;
     * this is the intent that will be credited with the launch.
     *
     * @return the resolved intent for this target
     */
    Intent getResolvedIntent();

    /**
     * Get the target intent, the one that will be used with one of the <code>start</code> methods.
     * @return the intent with target will be launced with.
     */
    @Nullable Intent getTargetIntent();

    /**
     * Get the resolved component name that represents this target. Note that this may not
     * be the component that will be directly launched by calling one of the <code>start</code>
     * methods provided; this is the component that will be credited with the launch. This may be
     * null if the target was specified by a caller-provided {@link ChooserTarget} that we failed to
     * resolve to a component on the system.
     *
     * @return the resolved ComponentName for this target
     */
    @Nullable
    ComponentName getResolvedComponentName();

    /**
     * If this target was historically built from a (now-deprecated) {@link ChooserTarget} record,
     * get the {@link ComponentName} that would've been provided by that record.
     *
     * TODO: for (historical) {@link ChooserTargetInfo} targets, this differs from the result of
     * {@link #getResolvedComponentName()} only for caller-provided targets that we fail to resolve;
     * then this returns the name of the component that was requested, and the other returns null.
     * At the time of writing, this method is only called in contexts where the client knows that
     * the target was a historical {@link ChooserTargetInfo}. Thus this method could be removed and
     * all clients consolidated on the other, if we have some alternate mechanism of tracking this
     * discrepancy; or if we know that the distinction won't apply in the conditions when we call
     * this method; or if we determine that tracking the distinction isn't a requirement for us.
     */
    @Nullable
    default ComponentName getChooserTargetComponentName() {
        return null;
    }

    /**
     * Start the activity referenced by this target as if the Activity's caller was performing the
     * start operation.
     *
     * @param activity calling Activity (actually) performing the launch
     * @param options ActivityOptions bundle
     * @param userId userId to start as or {@link UserHandle#USER_NULL} for activity's caller
     * @return true if the start completed successfully
     */
    boolean startAsCaller(Activity activity, Bundle options, int userId);

    /**
     * Start the activity referenced by this target as a given user.
     *
     * @param activity calling activity performing the launch
     * @param options ActivityOptions bundle
     * @param user handle for the user to start the activity as
     * @return true if the start completed successfully
     */
    boolean startAsUser(Activity activity, Bundle options, UserHandle user);

    /**
     * Return the ResolveInfo about how and why this target matched the original query
     * for available targets.
     *
     * @return ResolveInfo representing this target's match
     */
    ResolveInfo getResolveInfo();

    /**
     * Return the human-readable text label for this target.
     *
     * @return user-visible target label
     */
    CharSequence getDisplayLabel();

    /**
     * Return any extended info for this target. This may be used to disambiguate
     * otherwise identical targets.
     *
     * @return human-readable disambig string or null if none present
     */
    CharSequence getExtendedInfo();

    /**
     * @return the {@link IconHolder} for the icon used to represent this target, including badge.
     *
     * TODO: while the {@link TargetInfo} may be immutable in always returning the same instance of
     * {@link IconHolder} here, the holder itself is mutable state, and could become a problem if we
     * ever rely on {@link TargetInfo} immutability elsewhere. Ideally, the {@link TargetInfo}
     * should provide an immutable "spec" that tells clients <em>how</em> to load the appropriate
     * icon, while leaving the load itself to some external component.
     */
    IconHolder getDisplayIconHolder();

    /**
     * @return true if display icon is available.
     */
    default boolean hasDisplayIcon() {
        return getDisplayIconHolder().getDisplayIcon() != null;
    }

    /**
     * Attempt to apply a {@code proposedRefinement} that the {@link ChooserRefinementManager}
     * received from the caller's refinement flow. This may succeed only if the target has a source
     * intent that matches the filtering parameters of the proposed refinement (according to
     * {@link Intent#filterEquals}). Then the first such match is the "base intent," and the
     * proposed refinement is merged into that base (via {@link Intent#fillIn}; this can never
     * result in a change to the {@link Intent#filterEquals} status of the base, but may e.g. add
     * new "extras" that weren't previously given in the base intent).
     *
     * @return a copy of this {@link TargetInfo} where the "base intent to send" is the result of
     * merging the refinement into the best-matching source intent, if possible. If there is no
     * suitable match for the proposed refinement, or if merging fails for any other reason, this
     * returns null.
     *
     * @see android.content.Intent#fillIn(Intent, int)
     */
    @Nullable
    TargetInfo tryToCloneWithAppliedRefinement(Intent proposedRefinement);

    /**
     * @return the list of supported source intents deduped against this single target
     */
    List<Intent> getAllSourceIntents();

    /**
     * @return the one or more {@link DisplayResolveInfo}s that this target represents in the UI.
     *
     * TODO: clarify the semantics of the {@link DisplayResolveInfo} branch of {@link TargetInfo}'s
     * class hierarchy. Why is it that {@link MultiDisplayResolveInfo} can stand in for some
     * "virtual" {@link DisplayResolveInfo} targets that aren't individually represented in the UI,
     * but OTOH a {@link ChooserTargetInfo} (which doesn't inherit from {@link DisplayResolveInfo})
     * can't provide its own UI treatment, and instead needs us to reach into its composed-in
     * info via {@link #getDisplayResolveInfo()}? It seems like {@link DisplayResolveInfo} may be
     * required to populate views in our UI, while {@link ChooserTargetInfo} may carry some other
     * metadata. For non-{@link ChooserTargetInfo} targets (e.g. in {@link ResolverActivity}) the
     * "naked" {@link DisplayResolveInfo} might also be taken to provide some of this metadata, but
     * this presents a denormalization hazard since the "UI info" ({@link DisplayResolveInfo}) that
     * represents a {@link ChooserTargetInfo} might provide different values than its enclosing
     * {@link ChooserTargetInfo} (as they both implement {@link TargetInfo}). We could try to
     * address this by splitting {@link DisplayResolveInfo} into two types; one (which implements
     * the same {@link TargetInfo} interface as {@link ChooserTargetInfo}) provides the previously-
     * implicit "metadata", and the other provides only the UI treatment for a target of any type
     * (taking over the respective methods that previously belonged to {@link TargetInfo}).
     */
    ArrayList<DisplayResolveInfo> getAllDisplayTargets();

    /**
     * @return true if this target cannot be selected by the user
     */
    boolean isSuspended();

    /**
     * @return true if this target should be pinned to the front by the request of the user
     */
    boolean isPinned();

    /**
     * Determine whether two targets represent "similar" content that could be de-duped.
     * Note an earlier version of this code cautioned maintainers,
     * "do not label as 'equals', since this doesn't quite work as intended with java 8."
     * This seems to refer to the rule that interfaces can't provide defaults that conflict with the
     * definitions of "real" methods in {@code java.lang.Object}, and (if desired) it could be
     * presumably resolved by converting {@code TargetInfo} from an interface to an abstract class.
     */
    default boolean isSimilar(TargetInfo other) {
        if (other == null) {
            return false;
        }

        // TODO: audit usage and try to reconcile a behavior that doesn't depend on the legacy
        // subclass type. Note that the `isSimilar()` method was pulled up from the legacy
        // `ChooserTargetInfo`, so no legacy behavior currently depends on calling `isSimilar()` on
        // an instance where `isChooserTargetInfo()` would return false (although technically it may
        // have been possible for the `other` target to be of a different type). Thus we have
        // flexibility in defining the similarity conditions between pairs of non "chooser" targets.
        if (isChooserTargetInfo()) {
            return other.isChooserTargetInfo()
                    && Objects.equals(
                            getChooserTargetComponentName(), other.getChooserTargetComponentName())
                    && TextUtils.equals(getDisplayLabel(), other.getDisplayLabel())
                    && TextUtils.equals(getExtendedInfo(), other.getExtendedInfo());
        } else {
            return !other.isChooserTargetInfo() && Objects.equals(this, other);
        }
    }

    /**
     * @return the target score, including any Chooser-specific modifications that may have been
     * applied (either overriding by special-case for "non-selectable" targets, or by twiddling the
     * scores of "selectable" targets in {@link ChooserListAdapter}). Higher scores are "better."
     * Targets that aren't intended for ranking/scoring should return a negative value.
     */
    default float getModifiedScore() {
        return -0.1f;
    }

    /**
     * @return the {@link ShortcutInfo} for any shortcut associated with this target.
     */
    @Nullable
    default ShortcutInfo getDirectShareShortcutInfo() {
        return null;
    }

    /**
     * @return the ID of the shortcut represented by this target, or null if the target didn't come
     * from a {@link ShortcutManager} shortcut.
     */
    @Nullable
    default String getDirectShareShortcutId() {
        ShortcutInfo shortcut = getDirectShareShortcutInfo();
        if (shortcut == null) {
            return null;
        }
        return shortcut.getId();
    }

    /**
     * @return the {@link AppTarget} metadata if this target was sourced from App Prediction
     * service, or null otherwise.
     */
    @Nullable
    default AppTarget getDirectShareAppTarget() {
        return null;
    }

    /**
     * Get more info about this target in the form of a {@link DisplayResolveInfo}, if available.
     * TODO: this seems to return non-null only for ChooserTargetInfo subclasses. Determine the
     * meaning of a TargetInfo (ChooserTargetInfo) embedding another kind of TargetInfo
     * (DisplayResolveInfo) in this way, and - at least - improve this documentation; OTOH this
     * probably indicates an opportunity to simplify or better separate these APIs. (For example,
     * targets that <em>don't</em> descend from ChooserTargetInfo instead descend directly from
     * DisplayResolveInfo; should they return `this`? Do we always use DisplayResolveInfo to
     * represent visual properties, and then either assume some implicit metadata properties *or*
     * embed that visual representation within a ChooserTargetInfo to carry additional metadata? If
     * that's the case, maybe we could decouple by saying that all TargetInfos compose-in their
     * visual representation [as a DisplayResolveInfo, now the root of its own class hierarchy] and
     * then add a new TargetInfo type that explicitly represents the "implicit metadata" that we
     * previously assumed for "naked DisplayResolveInfo targets" that weren't wrapped as
     * ChooserTargetInfos. Or does all this complexity disappear once we stop relying on the
     * deprecated ChooserTarget type?)
     */
    @Nullable
    default DisplayResolveInfo getDisplayResolveInfo() {
        return null;
    }

    /**
     * @return true if this target represents a legacy {@code ChooserTargetInfo}. These objects were
     * historically documented as representing "[a] TargetInfo for Direct Share." However, not all
     * of these targets are actually *valid* for direct share; e.g. some represent "empty" items
     * (although perhaps only for display in the Direct Share UI?). In even earlier versions, these
     * targets may also have been results from peers in the (now-deprecated/unsupported)
     * {@code ChooserTargetService} ecosystem; even though we no longer use these services, we're
     * still shoehorning other target data into the deprecated {@link ChooserTarget} structure for
     * compatibility with some internal APIs.
     * TODO: refactor to clarify the semantics of any target for which this method returns true
     * (e.g., are they characterized by their application in the Direct Share UI?), and to remove
     * the scaffolding that adapts to and from the {@link ChooserTarget} structure. Eventually, we
     * expect to remove this method (and others that strictly indicate legacy subclass roles) in
     * favor of a more semantic design that expresses the purpose and distinctions in those roles.
     */
    default boolean isChooserTargetInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code DisplayResolveInfo}. These objects
     * were historically documented as an augmented "TargetInfo plus additional information needed
     * to render it (such as icon and label) and resolve it to an activity." That description in no
     * way distinguishes from the base {@code TargetInfo} API. At the time of writing, these objects
     * are most-clearly defined by their opposite; this returns true for exactly those instances of
     * {@code TargetInfo} where {@link #isChooserTargetInfo()} returns false (these conditions are
     * complementary because they correspond to the immediate {@code TargetInfo} child types that
     * historically partitioned all concrete {@code TargetInfo} implementations). These may(?)
     * represent any target displayed somewhere other than the Direct Share UI.
     */
    default boolean isDisplayResolveInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code MultiDisplayResolveInfo}. These
     * objects were historically documented as representing "a 'stack' of chooser targets for
     * various activities within the same component." For historical reasons this currently can
     * return true only if {@link #isDisplayResolveInfo()} returns true (because the legacy classes
     * shared an inheritance relationship), but new code should avoid relying on that relationship
     * since these APIs are "in transition."
     */
    default boolean isMultiDisplayResolveInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code SelectableTargetInfo}. Note that this
     * is defined for legacy compatibility and may not conform to other notions of a "selectable"
     * target. For historical reasons, this method and {@link #isNotSelectableTargetInfo()} only
     * partition the {@code TargetInfo} instances for which {@link #isChooserTargetInfo()} returns
     * true; otherwise <em>both</em> methods return false.
     * TODO: define selectability for targets not historically from {@code ChooserTargetInfo},
     * then attempt to replace this with a new method like {@code TargetInfo#isSelectable()} that
     * actually partitions <em>all</em> target types (after updating client usage as needed).
     */
    default boolean isSelectableTargetInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code NotSelectableTargetInfo} (i.e., a
     * target where {@link #isChooserTargetInfo()} is true but {@link #isSelectableTargetInfo()} is
     * false). For more information on how this divides the space of targets, see the Javadoc for
     * {@link #isSelectableTargetInfo()}.
     */
    default boolean isNotSelectableTargetInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code ChooserActivity#EmptyTargetInfo}. Note
     * that this is defined for legacy compatibility and may not conform to other notions of an
     * "empty" target.
     */
    default boolean isEmptyTargetInfo() {
        return false;
    }

    /**
     * @return true if this target represents a legacy {@code ChooserActivity#PlaceHolderTargetInfo}
     * (defined only for compatibility with historic use in {@link ChooserListAdapter}). For
     * historic reasons (owing to a legacy subclass relationship) this can return true only if
     * {@link #isNotSelectableTargetInfo()} also returns true.
     */
    default boolean isPlaceHolderTargetInfo() {
        return false;
    }

    /**
     * @return true if this target should be logged with the "direct_share" metrics category in
     * {@link ResolverActivity#maybeLogCrossProfileTargetLaunch}. This is defined for legacy
     * compatibility and is <em>not</em> likely to be a good indicator of whether this is actually a
     * "direct share" target (e.g. because it historically also applies to "empty" and "placeholder"
     * targets).
     */
    default boolean isInDirectShareMetricsCategory() {
        return isChooserTargetInfo();
    }

    /**
     * @param context caller's context, to provide the {@link SharedPreferences} for use by the
     * {@link HashedStringCache}.
     * @return a hashed ID that should be logged along with our target-selection metrics, or null.
     * The contents of the plaintext are defined for historical reasons, "the package name + target
     * name to answer the question if most users share to mostly the same person
     * or to a bunch of different people." Clients should consider this as opaque data for logging
     * only; they should not rely on any particular semantics about the value.
     */
    default HashedStringCache.HashResult getHashedTargetIdForMetrics(Context context) {
        return null;
    }

    /**
     * Fix the URIs in {@code intent} if cross-profile sharing is required. This should be called
     * before launching the intent as another user.
     */
    static void prepareIntentForCrossProfileLaunch(Intent intent, int targetUserId) {
        final int currentUserId = UserHandle.myUserId();
        if (targetUserId != currentUserId) {
            intent.fixUris(currentUserId);
        }
    }

    /**
     * Derive a "complete" intent from a proposed `refinement` intent by merging it into a matching
     * `base` intent, without modifying the filter-equality properties of the `base` intent, while
     * still allowing the `refinement` to replace Share "payload" fields.
     * Note! Callers are responsible for ensuring that the `base` is a suitable match for the given
     * `refinement`, such that the two can be merged without modifying filter-equality properties.
     */
    static Intent mergeRefinementIntoMatchingBaseIntent(Intent base, Intent refinement) {
        Intent mergedIntent = new Intent(base);

        /* Copy over any fields from the `refinement` that weren't already specified by the `base`,
         * along with the refined ClipData (if present, even if that overwrites data given in the
         * `base` intent).
         *
         * Refinement may have modified the payload content stored in the ClipData; such changes
         * are permitted in refinement since ClipData isn't a factor in the determination of
         * `Intent.filterEquals()` (which must be preserved as an invariant of refinement). */
        mergedIntent.fillIn(refinement, Intent.FILL_IN_CLIP_DATA);

        /* Refinement may also modify payload content held in the 'extras' representation, as again
         * those attributes aren't a factor in determining filter-equality. There is no `FILL_IN_*`
         * flag that would allow the refinement to overwrite existing keys in the `base` extras, so
         * here we have to implement the logic ourselves.
         *
         * Note this still doesn't imply that the refined intent is the final authority on extras;
         * in particular, `SelectableTargetInfo.mActivityStarter` uses `Intent.putExtras(Bundle)` to
         * merge in the `mChooserTargetIntentExtras` (i.e., the `EXTRA_SHORTCUT_ID`), which will
         * overwrite any existing value.
         *
         * TODO: formalize the precedence and make sure extras are set in the appropriate stages,
         * instead of relying on maintainers to know that (e.g.) authoritative changes belong in the
         * `TargetActivityStarter`. Otherwise, any extras-based data that Sharesheet adds internally
         * might be susceptible to "spoofing" from the refinement activity. */
        mergedIntent.putExtras(refinement);  // Re-merge extras to favor refinement.

        // TODO(b/279067078): consider how to populate the "merged" ClipData. The `base`
        // already has non-null ClipData due to the implicit migration in Intent, so if the 
        // refinement modified any of the payload extras, they *must* also provide a modified
        // ClipData, or else the updated "extras" payload will be inconsistent with the
        // pre-refinement ClipData when they're merged together. We may be able to do better,
        // but there are complicated tradeoffs.

        return mergedIntent;
    }
}
