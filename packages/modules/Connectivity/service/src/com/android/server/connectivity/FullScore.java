/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkScore.KEEP_CONNECTED_NONE;
import static android.net.NetworkScore.POLICY_YIELD_TO_BAD_WIFI;

import static com.android.net.module.util.BitUtils.describeDifferences;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkScore;
import android.net.NetworkScore.KeepConnectedReason;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.MessageUtils;

import java.util.StringJoiner;

/**
 * This class represents how desirable a network is.
 *
 * FullScore is very similar to NetworkScore, but it contains the bits that are managed
 * by ConnectivityService. This provides static guarantee that all users must know whether
 * they are handling a score that had the CS-managed bits set.
 */
public class FullScore {
    private static final String TAG = FullScore.class.getSimpleName();

    // Agent-managed policies are in NetworkScore. They start from 1.
    // CS-managed policies, counting from 63 downward
    // This network is validated. CS-managed because the source of truth is in NetworkCapabilities.
    /** @hide */
    public static final int POLICY_IS_VALIDATED = 63;

    // This network has been validated at least once since it was connected.
    /** @hide */
    public static final int POLICY_EVER_VALIDATED = 62;

    // This is a VPN and behaves as one for scoring purposes.
    /** @hide */
    public static final int POLICY_IS_VPN = 61;

    // This network has been selected by the user manually from settings or a 3rd party app
    // at least once. @see NetworkAgentConfig#explicitlySelected.
    /** @hide */
    public static final int POLICY_EVER_USER_SELECTED = 60;

    // The user has indicated in UI that this network should be used even if it doesn't
    // validate. @see NetworkAgentConfig#acceptUnvalidated.
    /** @hide */
    public static final int POLICY_ACCEPT_UNVALIDATED = 59;

    // The user explicitly said in UI to avoid this network when unvalidated.
    // TODO : remove setAvoidUnvalidated and instead disconnect the network when the user
    // chooses to move away from this network, and remove this flag.
    /** @hide */
    public static final int POLICY_AVOIDED_WHEN_UNVALIDATED = 58;

    // This network is unmetered. @see NetworkCapabilities.NET_CAPABILITY_NOT_METERED.
    /** @hide */
    public static final int POLICY_IS_UNMETERED = 57;

    // This network is invincible. This is useful for offers until there is an API to listen
    // to requests.
    /** @hide */
    public static final int POLICY_IS_INVINCIBLE = 56;

    // This network has undergone initial validation.
    //
    // The stack considers that any result finding some working connectivity (valid, partial,
    // captive portal) is an initial validation. Negative result (not valid), however, is not
    // considered initial validation until {@link ConnectivityService#PROMPT_UNVALIDATED_DELAY_MS}
    // have elapsed. This is because some networks may spuriously fail for a short time immediately
    // after associating. If no positive result is found after the timeout has elapsed, then
    // the network has been evaluated once.
    public static final int POLICY_EVER_EVALUATED = 55;

    // The network agent has communicated that this network no longer functions, and the underlying
    // native network has been destroyed. The network will still be reported to clients as connected
    // until a timeout expires, the agent disconnects, or the network no longer satisfies requests.
    // This network should lose to an identical network that has not been destroyed, but should
    // otherwise be scored exactly the same.
    /** @hide */
    public static final int POLICY_IS_DESTROYED = 54;

    // To help iterate when printing
    @VisibleForTesting
    static final int MIN_CS_MANAGED_POLICY = POLICY_IS_DESTROYED;
    @VisibleForTesting
    static final int MAX_CS_MANAGED_POLICY = POLICY_IS_VALIDATED;

    // Mask for policies in NetworkScore. This should have all bits managed by NetworkScore set
    // and all bits managed by FullScore unset. As bits are handled from 0 up in NetworkScore and
    // from 63 down in FullScore, cut at the 32nd bit for simplicity, but change this if some day
    // there are more than 32 bits handled on either side.
    // YIELD_TO_BAD_WIFI is temporarily handled by ConnectivityService.
    private static final long EXTERNAL_POLICIES_MASK =
            0x00000000FFFFFFFFL & ~(1L << POLICY_YIELD_TO_BAD_WIFI);

    private static SparseArray<String> sMessageNames = MessageUtils.findMessageNames(
            new Class[]{FullScore.class, NetworkScore.class}, new String[]{"POLICY_"});

    @VisibleForTesting
    public static @NonNull String policyNameOf(final int policy) {
        final String name = sMessageNames.get(policy);
        if (name == null) {
            // Don't throw here because name might be null due to proguard stripping out the
            // POLICY_* constants, potentially causing a crash only on user builds because proguard
            // does not run on userdebug builds.
            // TODO: make MessageUtils safer by not returning the array and instead storing it
            // internally and providing a getter (that does not throw) for individual values.
            Log.wtf(TAG, "Unknown policy: " + policy);
            return Integer.toString(policy);
        }
        return name.substring("POLICY_".length());
    }

    // Bitmask of all the policies applied to this score.
    private final long mPolicies;

    private final int mKeepConnectedReason;

    FullScore(final long policies, @KeepConnectedReason final int keepConnectedReason) {
        mPolicies = policies;
        mKeepConnectedReason = keepConnectedReason;
    }

    /**
     * Given a score supplied by the NetworkAgent and CS-managed objects, produce a full score.
     *
     * @param score the score supplied by the agent
     * @param caps the NetworkCapabilities of the network
     * @param config the NetworkAgentConfig of the network
     * @param everValidated whether this network has ever validated
     * @param avoidUnvalidated whether the user said in UI to avoid this network when unvalidated
     * @param yieldToBadWiFi whether this network yields to a previously validated wifi gone bad
     * @param everEvaluated whether this network ever evaluated at least once
     * @param destroyed whether this network has been destroyed pending a replacement connecting
     * @return a FullScore that is appropriate to use for ranking.
     */
    // TODO : this shouldn't manage bad wifi avoidance – instead this should be done by the
    // telephony factory, so that it depends on the carrier. For now this is handled by
    // connectivity for backward compatibility.
    public static FullScore fromNetworkScore(@NonNull final NetworkScore score,
            @NonNull final NetworkCapabilities caps, @NonNull final NetworkAgentConfig config,
            final boolean everValidated, final boolean avoidUnvalidated,
            final boolean yieldToBadWiFi, final boolean everEvaluated, final boolean destroyed) {
        return withPolicies(score.getPolicies(),
                score.getKeepConnectedReason(),
                caps.hasCapability(NET_CAPABILITY_VALIDATED),
                everValidated, caps.hasTransport(TRANSPORT_VPN),
                config.explicitlySelected,
                config.acceptUnvalidated,
                avoidUnvalidated,
                caps.hasCapability(NET_CAPABILITY_NOT_METERED),
                yieldToBadWiFi,
                false /* invincible */, // only prospective scores can be invincible
                everEvaluated,
                destroyed);
    }

    /**
     * Given a score supplied by a NetworkProvider, produce a prospective score for an offer.
     *
     * NetworkOffers have score filters that are compared to the scores of actual networks
     * to see if they could possibly beat the current satisfier. Some things the agent can't
     * know in advance; a good example is the validation bit – some networks will validate,
     * others won't. For comparison purposes, assume the best, so all possibly beneficial
     * networks will be brought up.
     *
     * @param score the score supplied by the agent for this offer
     * @param caps the capabilities supplied by the agent for this offer
     * @return a FullScore appropriate for comparing to actual network's scores.
     */
    public static FullScore makeProspectiveScore(@NonNull final NetworkScore score,
            @NonNull final NetworkCapabilities caps, final boolean yieldToBadWiFi) {
        // If the network offers Internet access, it may validate.
        final boolean mayValidate = caps.hasCapability(NET_CAPABILITY_INTERNET);
        // If the offer may validate, then it should be considered to have validated at some point
        final boolean everValidated = mayValidate;
        // VPN transports are known in advance.
        final boolean vpn = caps.hasTransport(TRANSPORT_VPN);
        // The network hasn't been chosen by the user (yet, at least).
        final boolean everUserSelected = false;
        // Don't assume the user will accept unvalidated connectivity.
        final boolean acceptUnvalidated = false;
        // A prospective network is never avoided when unvalidated, because the user has never
        // had the opportunity to say so in UI.
        final boolean avoidUnvalidated = false;
        // Prospective scores are always unmetered, because unmetered networks are stronger
        // than metered networks, and it's not known in advance whether the network is metered.
        final boolean unmetered = true;
        // A prospective score is invincible if the legacy int in the filter is over the maximum
        // score.
        final boolean invincible = score.getLegacyInt() > NetworkRanker.LEGACY_INT_MAX;
        // A prospective network will eventually be evaluated.
        final boolean everEvaluated = true;
        // A network can only be destroyed once it has connected.
        final boolean destroyed = false;
        return withPolicies(score.getPolicies(), KEEP_CONNECTED_NONE,
                mayValidate, everValidated, vpn, everUserSelected,
                acceptUnvalidated, avoidUnvalidated, unmetered, yieldToBadWiFi,
                invincible, everEvaluated, destroyed);
    }

    /**
     * Return a new score given updated caps and config.
     *
     * @param caps the NetworkCapabilities of the network
     * @param config the NetworkAgentConfig of the network
     * @return a score with the policies from the arguments reset
     */
    // TODO : this shouldn't manage bad wifi avoidance – instead this should be done by the
    // telephony factory, so that it depends on the carrier. For now this is handled by
    // connectivity for backward compatibility.
    public FullScore mixInScore(@NonNull final NetworkCapabilities caps,
            @NonNull final NetworkAgentConfig config,
            final boolean everValidated,
            final boolean avoidUnvalidated,
            final boolean yieldToBadWifi,
            final boolean everEvaluated,
            final boolean destroyed) {
        return withPolicies(mPolicies, mKeepConnectedReason,
                caps.hasCapability(NET_CAPABILITY_VALIDATED),
                everValidated, caps.hasTransport(TRANSPORT_VPN),
                config.explicitlySelected,
                config.acceptUnvalidated,
                avoidUnvalidated,
                caps.hasCapability(NET_CAPABILITY_NOT_METERED),
                yieldToBadWifi,
                false /* invincible */, // only prospective scores can be invincible
                everEvaluated,
                destroyed);
    }

    // TODO : this shouldn't manage bad wifi avoidance – instead this should be done by the
    // telephony factory, so that it depends on the carrier. For now this is handled by
    // connectivity for backward compatibility.
    private static FullScore withPolicies(final long externalPolicies,
            @KeepConnectedReason final int keepConnectedReason,
            final boolean isValidated,
            final boolean everValidated,
            final boolean isVpn,
            final boolean everUserSelected,
            final boolean acceptUnvalidated,
            final boolean avoidUnvalidated,
            final boolean isUnmetered,
            final boolean yieldToBadWiFi,
            final boolean invincible,
            final boolean everEvaluated,
            final boolean destroyed) {
        return new FullScore((externalPolicies & EXTERNAL_POLICIES_MASK)
                | (isValidated       ? 1L << POLICY_IS_VALIDATED : 0)
                | (everValidated     ? 1L << POLICY_EVER_VALIDATED : 0)
                | (isVpn             ? 1L << POLICY_IS_VPN : 0)
                | (everUserSelected  ? 1L << POLICY_EVER_USER_SELECTED : 0)
                | (acceptUnvalidated ? 1L << POLICY_ACCEPT_UNVALIDATED : 0)
                | (avoidUnvalidated  ? 1L << POLICY_AVOIDED_WHEN_UNVALIDATED : 0)
                | (isUnmetered       ? 1L << POLICY_IS_UNMETERED : 0)
                | (yieldToBadWiFi    ? 1L << POLICY_YIELD_TO_BAD_WIFI : 0)
                | (invincible        ? 1L << POLICY_IS_INVINCIBLE : 0)
                | (everEvaluated     ? 1L << POLICY_EVER_EVALUATED : 0)
                | (destroyed         ? 1L << POLICY_IS_DESTROYED : 0),
                keepConnectedReason);
    }

    /**
     * Returns this score but with the specified yield to bad wifi policy.
     */
    public FullScore withYieldToBadWiFi(final boolean newYield) {
        return new FullScore(newYield ? mPolicies | (1L << POLICY_YIELD_TO_BAD_WIFI)
                        : mPolicies & ~(1L << POLICY_YIELD_TO_BAD_WIFI),
                mKeepConnectedReason);
    }

    /**
     * Returns this score but validated.
     */
    public FullScore asValidated() {
        return new FullScore(mPolicies | (1L << POLICY_IS_VALIDATED), mKeepConnectedReason);
    }

    /**
     * Gets the policies as an long. Internal callers only.
     *
     * DO NOT USE if not immediately collapsing back into a scalar. Instead, use
     * {@link #hasPolicy}.
     * @return the internal, version-dependent int representing the policies.
     * @hide
     */
    public long getPoliciesInternal() {
        return mPolicies;
    }

    /**
     * @return whether this score has a particular policy.
     */
    @VisibleForTesting
    public boolean hasPolicy(final int policy) {
        return 0 != (mPolicies & (1L << policy));
    }

    /**
     * Returns the keep-connected reason, or KEEP_CONNECTED_NONE.
     */
    public int getKeepConnectedReason() {
        return mKeepConnectedReason;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final FullScore fullScore = (FullScore) o;

        if (mPolicies != fullScore.mPolicies) return false;
        return mKeepConnectedReason == fullScore.mKeepConnectedReason;
    }

    @Override
    public int hashCode() {
        return 2 * ((int) mPolicies)
                + 3 * (int) (mPolicies >>> 32)
                + 5 * mKeepConnectedReason;
    }

    /**
     * Returns a short but human-readable string of updates from an older score.
     * @param old the old score to diff from
     * @return a string fit for logging differences, or null if no differences.
     *         this method cannot return the empty string. See BitUtils#describeDifferences.
     */
    @Nullable
    public String describeDifferencesFrom(@Nullable final FullScore old) {
        final long oldPolicies = null == old ? 0 : old.mPolicies;
        return describeDifferences(oldPolicies, mPolicies, FullScore::policyNameOf);
    }

    // Example output :
    // Score(Policies : EVER_USER_SELECTED&IS_VALIDATED ; KeepConnected : )
    @Override
    public String toString() {
        final StringJoiner sj = new StringJoiner(
                "&", // delimiter
                "Score(Policies : ", // prefix
                " ; KeepConnected : " + mKeepConnectedReason + ")"); // suffix
        for (int i = NetworkScore.MIN_AGENT_MANAGED_POLICY;
                i <= NetworkScore.MAX_AGENT_MANAGED_POLICY; ++i) {
            if (hasPolicy(i)) sj.add(policyNameOf(i));
        }
        for (int i = MIN_CS_MANAGED_POLICY; i <= MAX_CS_MANAGED_POLICY; ++i) {
            if (hasPolicy(i)) sj.add(policyNameOf(i));
        }
        return sj.toString();
    }
}
