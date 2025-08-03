/*
 * Copyright 2020 The Android Open Source Project
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

package com.google.android.iwlan.epdg;

import android.content.Context;
import android.net.DnsResolver;
import android.net.DnsResolver.DnsException;
import android.net.InetAddresses;
import android.net.Network;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.CarrierConfigManager;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.DataFailCause;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import com.google.android.iwlan.ErrorPolicyManager;
import com.google.android.iwlan.IwlanError;
import com.google.android.iwlan.IwlanHelper;
import com.google.android.iwlan.epdg.NaptrDnsResolver.NaptrTarget;
import com.google.android.iwlan.flags.FeatureFlags;
import com.google.android.iwlan.flags.FeatureFlagsImpl;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EpdgSelector {
    private final FeatureFlags mFeatureFlags;
    private static final String TAG = "EpdgSelector";
    private final Context mContext;
    private final int mSlotId;
    private static final ConcurrentHashMap<Integer, EpdgSelector> mSelectorInstances =
            new ConcurrentHashMap<>();
    private int mV4PcoId = -1;
    private int mV6PcoId = -1;
    private List<byte[]> mV4PcoData;
    private List<byte[]> mV6PcoData;
    @NonNull private final ErrorPolicyManager mErrorPolicyManager;

    // Temporary excluded IP addresses due to recent failures. Cleared after tunnel opened
    // successfully or all resolved IP addresses are tried and excluded.
    private final Set<InetAddress> mTemporaryExcludedAddresses;

    // The default DNS timeout in the DNS module is set to 5 seconds. To account for IPC overhead,
    // IWLAN applies an internal timeout of 6 seconds, slightly longer than the default timeout
    private static final long DNS_RESOLVER_TIMEOUT_DURATION_SEC = 6L;

    private static final long PARALLEL_STATIC_RESOLUTION_TIMEOUT_DURATION_SEC = 6L;
    private static final long PARALLEL_PLMN_RESOLUTION_TIMEOUT_DURATION_SEC = 20L;
    private static final int NUM_EPDG_SELECTION_EXECUTORS = 2; // 1 each for normal selection, SOS.
    private static final int MAX_DNS_RESOLVER_THREADS = 25; // Do not expect > 25 FQDNs per carrier.

    private static final int PCO_MCC_MNC_LEN = 3; // 3 bytes for MCC and MNC in PCO data.
    private static final int PCO_IPV4_LEN = 4; // 4 bytes for IPv4 address in PCO data.
    private static final int PCO_IPV6_LEN = 16; // 16 bytes for IPv6 address in PCO data.

    private static final String NO_DOMAIN = "NO_DOMAIN";
    private static final Pattern PLMN_PATTERN = Pattern.compile("\\d{5,6}");

    BlockingQueue<Runnable> dnsResolutionQueue;

    Executor mDnsResolutionExecutor;

    ExecutorService mEpdgSelectionExecutor;
    Future<?> mDnsPrefetchFuture;

    ExecutorService mSosEpdgSelectionExecutor;
    Future<?> mSosDnsPrefetchFuture;

    final Comparator<InetAddress> inetAddressComparator =
            (ip1, ip2) -> {
                if ((ip1 instanceof Inet4Address) && (ip2 instanceof Inet6Address)) {
                    return -1;
                } else if ((ip1 instanceof Inet6Address) && (ip2 instanceof Inet4Address)) {
                    return 1;
                } else {
                    return 0;
                }
            };

    public static final int PROTO_FILTER_IPV4 = 0;
    public static final int PROTO_FILTER_IPV6 = 1;
    public static final int PROTO_FILTER_IPV4V6 = 2;

    @IntDef({PROTO_FILTER_IPV4, PROTO_FILTER_IPV6, PROTO_FILTER_IPV4V6})
    @interface ProtoFilter {}

    public static final int IPV4_PREFERRED = 0;
    public static final int IPV6_PREFERRED = 1;
    public static final int SYSTEM_PREFERRED = 2;

    @IntDef({IPV4_PREFERRED, IPV6_PREFERRED, SYSTEM_PREFERRED})
    @interface EpdgAddressOrder {}

    public interface EpdgSelectorCallback {
        /*gives priority ordered list of addresses*/
        void onServerListChanged(int transactionId, List<InetAddress> validIPList);

        void onError(int transactionId, IwlanError error);
    }

    @VisibleForTesting
    EpdgSelector(Context context, int slotId, FeatureFlags featureFlags) {
        mContext = context;
        mSlotId = slotId;
        mFeatureFlags = featureFlags;

        mV4PcoData = new ArrayList<>();
        mV6PcoData = new ArrayList<>();

        mV4PcoData = new ArrayList<>();
        mV6PcoData = new ArrayList<>();

        mErrorPolicyManager = ErrorPolicyManager.getInstance(mContext, mSlotId);

        mTemporaryExcludedAddresses = new HashSet<>();
        initializeExecutors();
    }

    private void initializeExecutors() {
        int maxEpdgSelectionThreads = mFeatureFlags.preventEpdgSelectionThreadsExhausted() ? 3 : 2;

        dnsResolutionQueue =
                new ArrayBlockingQueue<>(
                        MAX_DNS_RESOLVER_THREADS
                                * maxEpdgSelectionThreads
                                * NUM_EPDG_SELECTION_EXECUTORS);

        mDnsResolutionExecutor =
                new ThreadPoolExecutor(
                        0, MAX_DNS_RESOLVER_THREADS, 60L, TimeUnit.SECONDS, dnsResolutionQueue);

        mEpdgSelectionExecutor =
                new ThreadPoolExecutor(
                        0,
                        maxEpdgSelectionThreads,
                        60L,
                        TimeUnit.SECONDS,
                        new SynchronousQueue<>());

        mSosEpdgSelectionExecutor =
                new ThreadPoolExecutor(
                        0,
                        maxEpdgSelectionThreads,
                        60L,
                        TimeUnit.SECONDS,
                        new SynchronousQueue<>());
    }

    public static EpdgSelector getSelectorInstance(Context context, int slotId) {
        mSelectorInstances.computeIfAbsent(
                slotId, k -> new EpdgSelector(context, slotId, new FeatureFlagsImpl()));
        return mSelectorInstances.get(slotId);
    }

    public boolean setPcoData(int pcoId, @NonNull byte[] pcoData) {
        Log.d(
                TAG,
                "onReceive PcoId:"
                        + String.format("0x%04x", pcoId)
                        + " PcoData:"
                        + Arrays.toString(pcoData));

        int PCO_ID_IPV6 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, mContext, mSlotId);
        int PCO_ID_IPV4 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, mContext, mSlotId);

        Log.d(
                TAG,
                "PCO_ID_IPV6:"
                        + String.format("0x%04x", PCO_ID_IPV6)
                        + " PCO_ID_IPV4:"
                        + String.format("0x%04x", PCO_ID_IPV4));

        if (pcoId == PCO_ID_IPV4) {
            mV4PcoId = pcoId;
            mV4PcoData.add(pcoData);
            return true;
        } else if (pcoId == PCO_ID_IPV6) {
            mV6PcoId = pcoId;
            mV6PcoData.add(pcoData);
            return true;
        }

        return false;
    }

    public void clearPcoData() {
        Log.d(TAG, "Clear PCO data");
        mV4PcoId = -1;
        mV6PcoId = -1;
        mV4PcoData.clear();
        mV6PcoData.clear();
    }

    /**
     * Notify {@link EpdgSelector} that ePDG is connected successfully. The excluded ip addresses
     * will be cleared so that next ePDG selection will retry all ip addresses.
     */
    void onEpdgConnectedSuccessfully() {
        clearExcludedIpAddresses();
    }

    /**
     * Notify {@link EpdgSelector} that failed to connect to an ePDG. EpdgSelector will add the
     * {@code ipAddress} into excluded list and will not retry until any ePDG connected successfully
     * or all ip addresses candidates are tried.
     *
     * @param ipAddress the ePDG ip address that failed to connect
     */
    void onEpdgConnectionFailed(InetAddress ipAddress) {
        excludeIpAddress(ipAddress);
    }

    private void excludeIpAddress(InetAddress ipAddress) {
        if (!mFeatureFlags.epdgSelectionExcludeFailedIpAddress()) {
            return;
        }
        Log.d(TAG, "Added " + ipAddress + " into temporary excluded addresses");
        mTemporaryExcludedAddresses.add(ipAddress);
    }

    private void clearExcludedIpAddresses() {
        if (!mFeatureFlags.epdgSelectionExcludeFailedIpAddress()) {
            return;
        }
        Log.d(TAG, "Cleared temporary excluded addresses");
        mTemporaryExcludedAddresses.clear();
    }

    private boolean isInExcludedIpAddresses(InetAddress ipAddress) {
        return mTemporaryExcludedAddresses.contains(ipAddress);
    }

    private CompletableFuture<Map.Entry<String, List<InetAddress>>> submitDnsResolverQuery(
            String domainName, Network network, int queryType, Executor executor) {
        CompletableFuture<Map.Entry<String, List<InetAddress>>> result = new CompletableFuture();

        final DnsResolver.Callback<List<InetAddress>> cb =
                new DnsResolver.Callback<List<InetAddress>>() {
                    @Override
                    public void onAnswer(@NonNull final List<InetAddress> answer, final int rcode) {
                        if (rcode != 0) {
                            Log.e(
                                    TAG,
                                    "DnsResolver Response Code = "
                                            + rcode
                                            + " for domain "
                                            + domainName);
                        }
                        Map.Entry<String, List<InetAddress>> entry = Map.entry(domainName, answer);
                        result.complete(entry);
                    }

                    @Override
                    public void onError(@Nullable final DnsResolver.DnsException error) {
                        Log.e(
                                TAG,
                                "Resolve DNS with error: " + error + " for domain: " + domainName);
                        result.complete(null);
                    }
                };
        DnsResolver.getInstance()
                .query(network, domainName, queryType, DnsResolver.FLAG_EMPTY, executor, null, cb);
        return result;
    }

    private List<InetAddress> v4v6ProtocolFilter(List<InetAddress> ipList, int filter) {
        List<InetAddress> validIpList = new ArrayList<>();
        for (InetAddress ipAddress : ipList) {
            if (IwlanHelper.isIpv4EmbeddedIpv6Address(ipAddress)) {
                continue;
            }
            switch (filter) {
                case PROTO_FILTER_IPV4:
                    if (ipAddress instanceof Inet4Address) {
                        validIpList.add(ipAddress);
                    }
                    break;
                case PROTO_FILTER_IPV6:
                    if (ipAddress instanceof Inet6Address) {
                        validIpList.add(ipAddress);
                    }
                    break;
                case PROTO_FILTER_IPV4V6:
                    validIpList.add(ipAddress);
                    break;
                default:
                    Log.d(TAG, "Invalid ProtoFilter : " + filter);
            }
        }
        return validIpList;
    }

    // Converts a list of CompletableFutures of type T into a single CompletableFuture containing a
    // list of T. The resulting CompletableFuture waits for all futures to complete,
    // even if any future throw an exception.
    private <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futuresList) {
        CompletableFuture<Void> allFuturesResult =
                CompletableFuture.allOf(
                        futuresList.toArray(new CompletableFuture[futuresList.size()]));
        return allFuturesResult.thenApply(
                v ->
                        futuresList.stream()
                                .map(CompletableFuture::join)
                                .filter(Objects::nonNull)
                                .collect(Collectors.<T>toList()));
    }

    @VisibleForTesting
    protected boolean hasIpv4Address(Network network) {
        return IwlanHelper.hasIpv4Address(IwlanHelper.getAllAddressesForNetwork(mContext, network));
    }

    @VisibleForTesting
    protected boolean hasIpv6Address(Network network) {
        return IwlanHelper.hasIpv6Address(IwlanHelper.getAllAddressesForNetwork(mContext, network));
    }

    private void printParallelDnsResult(Map<String, List<InetAddress>> domainNameToIpAddresses) {
        Log.d(TAG, "Parallel DNS resolution result:");
        for (String domain : domainNameToIpAddresses.keySet()) {
            Log.d(TAG, domain + ": " + domainNameToIpAddresses.get(domain));
        }
    }

    private List<InetAddress> filterExcludedAddresses(List<InetAddress> ipList) {
        if (!mFeatureFlags.epdgSelectionExcludeFailedIpAddress()) {
            return ipList;
        }
        if (mTemporaryExcludedAddresses.containsAll(ipList)) {
            Log.d(
                    TAG,
                    "All valid ip are tried and excluded, clear all"
                            + " excluded address and retry entire list again");
            clearExcludedIpAddresses();
        }

        var filteredIpList =
                ipList.stream().filter(ipAddress -> !isInExcludedIpAddresses(ipAddress)).toList();

        int excludedIpNum = filteredIpList.size() - ipList.size();
        if (excludedIpNum > 0) {
            Log.d(
                    TAG,
                    "Excluded "
                            + excludedIpNum
                            + " out of "
                            + ipList.size()
                            + " addresses from the list due to recent failures");
        }

        return filteredIpList;
    }

    /**
     * Returns a list of unique IP addresses corresponding to the given domain names, in the same
     * order of the input. Runs DNS resolution across parallel threads.
     *
     * @param domainNames Domain names for which DNS resolution needs to be performed.
     * @param filter Selects for IPv4, IPv6 (or both) addresses from the resulting DNS records
     * @param network {@link Network} Network on which to run the DNS query.
     * @param timeout timeout in seconds.
     * @return List of unique IP addresses corresponding to the domainNames.
     */
    private LinkedHashMap<String, List<InetAddress>> getIP(
            List<String> domainNames, int filter, Network network, long timeout) {
        // LinkedHashMap preserves insertion order (and hence priority) of domain names passed in.
        LinkedHashMap<String, List<InetAddress>> domainNameToIpAddr = new LinkedHashMap<>();

        List<CompletableFuture<Map.Entry<String, List<InetAddress>>>> futuresList =
                new ArrayList<>();
        for (String domainName : domainNames) {
            if (InetAddresses.isNumericAddress(domainName)) {
                Log.d(TAG, domainName + " is a numeric IP address!");
                InetAddress inetAddr = InetAddresses.parseNumericAddress(domainName);
                domainNameToIpAddr.put(NO_DOMAIN, new ArrayList<>(List.of(inetAddr)));
                continue;
            }

            domainNameToIpAddr.put(domainName, new ArrayList<>());
            // Dispatches separate IPv4 and IPv6 queries to avoid being blocked on either result.
            if (hasIpv4Address(network)) {
                futuresList.add(
                        submitDnsResolverQuery(
                                domainName, network, DnsResolver.TYPE_A, mDnsResolutionExecutor));
            }
            if (hasIpv6Address(network)) {
                futuresList.add(
                        submitDnsResolverQuery(
                                domainName,
                                network,
                                DnsResolver.TYPE_AAAA,
                                mDnsResolutionExecutor));
            }
        }
        CompletableFuture<List<Map.Entry<String, List<InetAddress>>>> allFuturesResult =
                allOf(futuresList);

        List<Map.Entry<String, List<InetAddress>>> resultList = null;
        try {
            resultList = allFuturesResult.get(timeout, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Log.e(TAG, "Cause of ExecutionException: ", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.e(TAG, "InterruptedException: ", e);
        } catch (TimeoutException e) {
            Log.e(TAG, "TimeoutException: ", e);
        } finally {
            if (resultList == null) {
                Log.w(TAG, "No IP addresses in parallel DNS query!");
            } else {
                for (Map.Entry<String, List<InetAddress>> entry : resultList) {
                    String resultDomainName = entry.getKey();
                    List<InetAddress> resultIpAddr = v4v6ProtocolFilter(entry.getValue(), filter);

                    if (!domainNameToIpAddr.containsKey(resultDomainName)) {
                        Log.w(
                                TAG,
                                "Unexpected domain name in DnsResolver result: "
                                        + resultDomainName);
                        continue;
                    }
                    domainNameToIpAddr.get(resultDomainName).addAll(resultIpAddr);
                }
            }
        }
        return domainNameToIpAddr;
    }

    /**
     * Updates the validIpList with the IP addresses corresponding to this domainName. Runs blocking
     * DNS resolution on the same thread.
     *
     * @param domainName Domain name for which DNS resolution needs to be performed.
     * @param filter Selects for IPv4, IPv6 (or both) addresses from the resulting DNS records
     * @param validIpList A running list of IP addresses that needs to be updated.
     * @param network {@link Network} Network on which to run the DNS query.
     */
    private void getIP(
            String domainName, int filter, List<InetAddress> validIpList, Network network) {
        List<InetAddress> ipList = new ArrayList<InetAddress>();

        // Get All IP for each domain name
        Log.d(TAG, "Input domainName : " + domainName);

        if (InetAddresses.isNumericAddress(domainName)) {
            Log.d(TAG, domainName + " is a numeric IP address!");
            ipList.add(InetAddresses.parseNumericAddress(domainName));
        } else {
            try {
                CompletableFuture<List<InetAddress>> result = new CompletableFuture();
                final DnsResolver.Callback<List<InetAddress>> cb =
                        new DnsResolver.Callback<List<InetAddress>>() {
                            @Override
                            public void onAnswer(
                                    @NonNull final List<InetAddress> answer, final int rcode) {
                                if (rcode != 0) {
                                    Log.e(TAG, "DnsResolver Response Code = " + rcode);
                                }
                                result.complete(answer);
                            }

                            @Override
                            public void onError(@Nullable final DnsResolver.DnsException error) {
                                Log.e(TAG, "Resolve DNS with error : " + error);
                                result.completeExceptionally(error);
                            }
                        };
                DnsResolver.getInstance()
                        .query(
                                network,
                                domainName,
                                DnsResolver.FLAG_EMPTY,
                                Runnable::run,
                                null,
                                cb);
                ipList =
                        new ArrayList<>(
                                result.get(DNS_RESOLVER_TIMEOUT_DURATION_SEC, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                Log.e(TAG, "Cause of ExecutionException: ", e.getCause());
            } catch (InterruptedException e) {
                Thread thread = Thread.currentThread();
                if (thread.interrupted()) {
                    thread.interrupt();
                }
                Log.e(TAG, "InterruptedException: ", e);
            } catch (TimeoutException e) {
                Log.e(TAG, "TimeoutException: ", e);
            }
        }

        List<InetAddress> filteredIpList = v4v6ProtocolFilter(ipList, filter);
        validIpList.addAll(filteredIpList);
    }

    private String[] getPlmnList() {
        List<String> plmnsFromCarrierConfig = getPlmnsFromCarrierConfig();
        Log.d(TAG, "plmnsFromCarrierConfig:" + plmnsFromCarrierConfig);

        // Get Ehplmns & mccmnc from SubscriptionManager
        SubscriptionManager subscriptionManager =
                mContext.getSystemService(SubscriptionManager.class);
        if (subscriptionManager == null) {
            Log.e(TAG, "SubscriptionManager is NULL");
            return plmnsFromCarrierConfig.toArray(new String[plmnsFromCarrierConfig.size()]);
        }

        SubscriptionInfo subInfo =
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(mSlotId);
        if (subInfo == null) {
            Log.e(TAG, "SubscriptionInfo is NULL");
            return plmnsFromCarrierConfig.toArray(new String[plmnsFromCarrierConfig.size()]);
        }

        // Get MCCMNC from IMSI
        String plmnFromImsi = subInfo.getMccString() + subInfo.getMncString();

        int[] prioritizedPlmnTypes =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PLMN_PRIORITY_INT_ARRAY,
                        mContext,
                        mSlotId);

        List<String> ehplmns = getEhplmns();
        String registeredPlmn = getRegisteredPlmn();

        List<String> combinedList = new ArrayList<>();
        for (int plmnType : prioritizedPlmnTypes) {
            switch (plmnType) {
                case CarrierConfigManager.Iwlan.EPDG_PLMN_RPLMN:
                    if (isInEpdgSelectionInfo(registeredPlmn)) {
                        combinedList.add(registeredPlmn);
                    }
                    break;
                case CarrierConfigManager.Iwlan.EPDG_PLMN_HPLMN:
                    combinedList.add(plmnFromImsi);
                    break;
                case CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_ALL:
                    combinedList.addAll(getEhplmns());
                    break;
                case CarrierConfigManager.Iwlan.EPDG_PLMN_EHPLMN_FIRST:
                    if (!ehplmns.isEmpty()) {
                        combinedList.add(ehplmns.get(0));
                    }
                    break;
                default:
                    Log.e(TAG, "Unknown PLMN type: " + plmnType);
                    break;
            }
        }

        combinedList =
                combinedList.stream()
                        .distinct()
                        .filter(EpdgSelector::isValidPlmn)
                        .map(plmn -> new StringBuilder(plmn).insert(3, "-").toString())
                        .toList();

        Log.d(TAG, "Final plmn list:" + combinedList);
        return combinedList.toArray(new String[combinedList.size()]);
    }

    private List<String> getPlmnsFromCarrierConfig() {
        return Arrays.asList(
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY, mContext, mSlotId));
    }

    private boolean isInEpdgSelectionInfo(String plmn) {
        if (!isValidPlmn(plmn)) {
            return false;
        }
        List<String> plmnsFromCarrierConfig = getPlmnsFromCarrierConfig();
        return plmnsFromCarrierConfig.contains(new StringBuilder(plmn).insert(3, "-").toString());
    }

    private ArrayList<InetAddress> removeDuplicateIp(List<InetAddress> validIpList) {
        ArrayList<InetAddress> resultIpList = new ArrayList<InetAddress>();

        for (InetAddress validIp : validIpList) {
            if (!resultIpList.contains(validIp)) {
                resultIpList.add(validIp);
            }
        }

        return resultIpList;
    }

    private List<InetAddress> prioritizeIp(
            @NonNull List<InetAddress> validIpList, @EpdgAddressOrder int order) {
        return switch (order) {
            case IPV4_PREFERRED -> validIpList.stream().sorted(inetAddressComparator).toList();
            case IPV6_PREFERRED -> validIpList.stream()
                    .sorted(inetAddressComparator.reversed())
                    .toList();
            case SYSTEM_PREFERRED -> validIpList;
            default -> {
                Log.w(TAG, "Invalid EpdgAddressOrder : " + order);
                yield validIpList;
            }
        };
    }

    private String[] splitMccMnc(String plmn) {
        String[] mccmnc = plmn.split("-");
        mccmnc[1] = String.format("%03d", Integer.parseInt(mccmnc[1]));
        return mccmnc;
    }

    /**
     * @return the registered PLMN, null if not registered with 3gpp or failed to get telephony
     *     manager
     */
    @Nullable
    private String getRegisteredPlmn() {
        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return null;
        }

        telephonyManager =
                telephonyManager.createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        String registeredPlmn = telephonyManager.getNetworkOperator();
        return registeredPlmn.isEmpty() ? null : registeredPlmn;
    }

    private List<String> getEhplmns() {
        TelephonyManager mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager =
                Objects.requireNonNull(mTelephonyManager)
                        .createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (mTelephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return new ArrayList<String>();
        } else {
            return mTelephonyManager.getEquivalentHomePlmns();
        }
    }

    private void resolutionMethodStatic(
            int filter, List<InetAddress> validIpList, Network network) {
        String[] domainNames = null;

        Log.d(TAG, "STATIC Method");

        // Get the static domain names from carrier config
        // Config obtained in form of a list of domain names separated by
        // a delimiter is only used for testing purpose.
        if (!inSameCountry()) {
            domainNames =
                    getDomainNames(
                            CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_ROAMING_STRING);
        }
        if (domainNames == null
                && (domainNames =
                                getDomainNames(
                                        CarrierConfigManager.Iwlan.KEY_EPDG_STATIC_ADDRESS_STRING))
                        == null) {
            Log.d(TAG, "Static address string is null");
            return;
        }

        Log.d(TAG, "Static Domain Names: " + Arrays.toString(domainNames));
        LinkedHashMap<String, List<InetAddress>> domainNameToIpAddr =
                getIP(
                        Arrays.asList(domainNames),
                        filter,
                        network,
                        PARALLEL_STATIC_RESOLUTION_TIMEOUT_DURATION_SEC);
        printParallelDnsResult(domainNameToIpAddr);
        domainNameToIpAddr.values().forEach(validIpList::addAll);
    }

    private String[] getDomainNames(String key) {
        String configValue = (String) IwlanHelper.getConfig(key, mContext, mSlotId);
        if (configValue == null || configValue.isEmpty()) {
            Log.d(TAG, key + " string is null");
            return null;
        }
        return configValue.split(",");
    }

    private boolean inSameCountry() {
        boolean inSameCountry = true;

        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        tm =
                Objects.requireNonNull(tm)
                        .createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (tm != null) {
            String simCountry = tm.getSimCountryIso();
            String currentCountry = IwlanHelper.getLastKnownCountryCode(mContext);
            if (!TextUtils.isEmpty(simCountry) && !TextUtils.isEmpty(currentCountry)) {
                Log.d(TAG, "simCountry = " + simCountry + ", currentCountry = " + currentCountry);
                inSameCountry = simCountry.equalsIgnoreCase(currentCountry);
            }
        }

        return inSameCountry;
    }

    private Map<String, List<InetAddress>> resolutionMethodPlmn(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();

        Log.d(TAG, "PLMN Method");

        plmnList = getPlmnList();
        List<String> domainNames = new ArrayList<>();
        for (String plmn : plmnList) {
            String[] mccmnc = splitMccMnc(plmn);
            /*
             * Operator Identifier based ePDG FQDN format:
             * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             *
             * Operator Identifier based Emergency ePDG FQDN format:
             * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             */
            if (isEmergency) {
                domainName = new StringBuilder();
                domainName
                        .append("sos.")
                        .append("epdg.epc.mnc")
                        .append(mccmnc[1])
                        .append(".mcc")
                        .append(mccmnc[0])
                        .append(".pub.3gppnetwork.org");
                domainNames.add(domainName.toString());
                domainName.setLength(0);
            }
            // For emergency PDN setup, still adding FQDN without "sos" header as second priority
            // because some operator doesn't support hostname with "sos" prefix.
            domainName
                    .append("epdg.epc.mnc")
                    .append(mccmnc[1])
                    .append(".mcc")
                    .append(mccmnc[0])
                    .append(".pub.3gppnetwork.org");
            domainNames.add(domainName.toString());
            domainName.setLength(0);
        }

        LinkedHashMap<String, List<InetAddress>> domainNameToIpAddr =
                getIP(domainNames, filter, network, PARALLEL_PLMN_RESOLUTION_TIMEOUT_DURATION_SEC);
        printParallelDnsResult(domainNameToIpAddr);
        domainNameToIpAddr.values().forEach(validIpList::addAll);
        return domainNameToIpAddr;
    }

    private void resolutionMethodCellularLoc(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();

        Log.d(TAG, "CELLULAR_LOC Method");

        TelephonyManager mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mTelephonyManager =
                Objects.requireNonNull(mTelephonyManager)
                        .createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (mTelephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return;
        }

        List<CellInfo> cellInfoList = mTelephonyManager.getAllCellInfo();
        if (cellInfoList == null) {
            Log.e(TAG, "cellInfoList is NULL");
            return;
        }

        for (CellInfo cellInfo : cellInfoList) {
            if (!cellInfo.isRegistered()) {
                continue;
            }

            if (cellInfo instanceof CellInfoGsm) {
                CellIdentityGsm gsmCellId = ((CellInfoGsm) cellInfo).getCellIdentity();
                String lacString = String.format("%04x", gsmCellId.getLac());

                lacDomainNameResolution(filter, validIpList, lacString, isEmergency, network);
            } else if (cellInfo instanceof CellInfoWcdma) {
                CellIdentityWcdma wcdmaCellId = ((CellInfoWcdma) cellInfo).getCellIdentity();
                String lacString = String.format("%04x", wcdmaCellId.getLac());

                lacDomainNameResolution(filter, validIpList, lacString, isEmergency, network);
            } else if (cellInfo instanceof CellInfoLte) {
                CellIdentityLte lteCellId = ((CellInfoLte) cellInfo).getCellIdentity();
                String tacString = String.format("%04x", lteCellId.getTac());
                String[] tacSubString = new String[2];
                tacSubString[0] = tacString.substring(0, 2);
                tacSubString[1] = tacString.substring(2);

                plmnList = getPlmnList();
                for (String plmn : plmnList) {
                    String[] mccmnc = splitMccMnc(plmn);
                    /**
                     * Tracking Area Identity based ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-hb<TAC-high-byte>.tac.
                     * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     *
                     * <p>Tracking Area Identity based Emergency ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-hb<TAC-highbyte>.tac.
                     * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org"
                     */
                    domainName
                            .append("tac-lb")
                            .append(tacSubString[1])
                            .append(".tac-hb")
                            .append(tacSubString[0]);
                    if (isEmergency) {
                        domainName.append(".tac.sos.epdg.epc.mnc");
                    } else {
                        domainName.append(".tac.epdg.epc.mnc");
                    }
                    domainName
                            .append(mccmnc[1])
                            .append(".mcc")
                            .append(mccmnc[0])
                            .append(".pub.3gppnetwork.org");
                    getIP(domainName.toString(), filter, validIpList, network);
                    domainName.setLength(0);
                }
            } else if (cellInfo instanceof CellInfoNr) {
                CellIdentityNr nrCellId = (CellIdentityNr) cellInfo.getCellIdentity();
                String tacString = String.format("%06x", nrCellId.getTac());
                String[] tacSubString = new String[3];
                tacSubString[0] = tacString.substring(0, 2);
                tacSubString[1] = tacString.substring(2, 4);
                tacSubString[2] = tacString.substring(4);

                plmnList = getPlmnList();
                for (String plmn : plmnList) {
                    String[] mccmnc = splitMccMnc(plmn);
                    /**
                     * 5GS Tracking Area Identity based ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-mb<TAC-middle-byte>.tac-hb<TAC-high-byte>.
                     * 5gstac.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     *
                     * <p>5GS Tracking Area Identity based Emergency ePDG FQDN format:
                     * tac-lb<TAC-low-byte>.tac-mb<TAC-middle-byte>.tac-hb<TAC-high-byte>.
                     * 5gstac.sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
                     */
                    domainName
                            .append("tac-lb")
                            .append(tacSubString[2])
                            .append(".tac-mb")
                            .append(tacSubString[1])
                            .append(".tac-hb")
                            .append(tacSubString[0]);
                    if (isEmergency) {
                        domainName.append(".5gstac.sos.epdg.epc.mnc");
                    } else {
                        domainName.append(".5gstac.epdg.epc.mnc");
                    }
                    domainName
                            .append(mccmnc[1])
                            .append(".mcc")
                            .append(mccmnc[0])
                            .append(".pub.3gppnetwork.org");
                    getIP(domainName.toString(), filter, validIpList, network);
                    domainName.setLength(0);
                }
            } else {
                Log.d(TAG, "This cell doesn't contain LAC/TAC info");
            }
        }
    }

    private void lacDomainNameResolution(
            int filter,
            List<InetAddress> validIpList,
            String lacString,
            boolean isEmergency,
            Network network) {
        String[] plmnList;
        StringBuilder domainName = new StringBuilder();

        plmnList = getPlmnList();
        for (String plmn : plmnList) {
            String[] mccmnc = splitMccMnc(plmn);
            /**
             * Location Area Identity based ePDG FQDN format:
             * lac<LAC>.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             *
             * <p>Location Area Identity based Emergency ePDG FQDN format:
             * lac<LAC>.sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
             */
            domainName.append("lac").append(lacString);
            if (isEmergency) {
                domainName.append(".sos.epdg.epc.mnc");
            } else {
                domainName.append(".epdg.epc.mnc");
            }
            domainName
                    .append(mccmnc[1])
                    .append(".mcc")
                    .append(mccmnc[0])
                    .append(".pub.3gppnetwork.org");

            getIP(domainName.toString(), filter, validIpList, network);
            domainName.setLength(0);
        }
    }

    private void resolutionMethodPco(int filter, @NonNull List<InetAddress> validIpList) {
        Log.d(TAG, "PCO Method");

        int PCO_ID_IPV6 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV6_INT, mContext, mSlotId);
        int PCO_ID_IPV4 =
                IwlanHelper.getConfig(
                        CarrierConfigManager.Iwlan.KEY_EPDG_PCO_ID_IPV4_INT, mContext, mSlotId);

        switch (filter) {
            case PROTO_FILTER_IPV4:
                if (mV4PcoId != PCO_ID_IPV4) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV4PcoData, validIpList);
                }
                break;
            case PROTO_FILTER_IPV6:
                if (mV6PcoId != PCO_ID_IPV6) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV6PcoData, validIpList);
                }
                break;
            case PROTO_FILTER_IPV4V6:
                if ((mV4PcoId != PCO_ID_IPV4) || (mV6PcoId != PCO_ID_IPV6)) {
                    clearPcoData();
                } else {
                    getInetAddressWithPcoData(mV4PcoData, validIpList);
                    getInetAddressWithPcoData(mV6PcoData, validIpList);
                }
                break;
            default:
                Log.d(TAG, "Invalid ProtoFilter : " + filter);
        }
    }

    private void getInetAddressWithPcoData(
            List<byte[]> pcoData, @NonNull List<InetAddress> validIpList) {
        for (byte[] data : pcoData) {
            int ipAddressLen = 0;
            /*
             * The PCO container contents starts with the operator MCC and MNC of size 3 bytes
             * combined followed by one IPv6 or IPv4 address.
             * IPv6 address is encoded as a 128-bit address and
             * IPv4 address is encoded as 32-bit address.
             */
            if (data.length > PCO_MCC_MNC_LEN) {
                ipAddressLen = data.length - PCO_MCC_MNC_LEN;
            }
            if ((ipAddressLen == PCO_IPV4_LEN) || (ipAddressLen == PCO_IPV6_LEN)) {
                byte[] ipAddressData = Arrays.copyOfRange(data, PCO_MCC_MNC_LEN, data.length);
                try {
                    validIpList.add(InetAddress.getByAddress(ipAddressData));
                } catch (UnknownHostException e) {
                    Log.e(
                            TAG,
                            "Exception when querying IP address("
                                    + Arrays.toString(ipAddressData)
                                    + "): "
                                    + e);
                }
            } else {
                Log.e(TAG, "Invalid PCO data:" + Arrays.toString(data));
            }
        }
    }

    private String composeFqdnWithMccMnc(String mcc, String mnc, boolean isEmergency) {
        StringBuilder domainName = new StringBuilder();

        /*
         * Operator Identifier based ePDG FQDN format:
         * epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
         *
         * Operator Identifier based Emergency ePDG FQDN format:
         * sos.epdg.epc.mnc<MNC>.mcc<MCC>.pub.3gppnetwork.org
         */
        domainName.setLength(0);
        if (isEmergency) {
            domainName.append("sos.");
        }
        domainName
                .append("epdg.epc.mnc")
                .append(mnc)
                .append(".mcc")
                .append(mcc)
                .append(".pub.3gppnetwork.org");

        return domainName.toString();
    }

    private boolean isRegisteredWith3GPP(TelephonyManager telephonyManager) {
        List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
        if (cellInfoList == null) {
            Log.e(TAG, "cellInfoList is NULL");
        } else {
            for (CellInfo cellInfo : cellInfoList) {
                if (!cellInfo.isRegistered()) {
                    continue;
                }
                if (cellInfo instanceof CellInfoGsm
                        || cellInfo instanceof CellInfoTdscdma
                        || cellInfo instanceof CellInfoWcdma
                        || cellInfo instanceof CellInfoLte
                        || cellInfo instanceof CellInfoNr) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processNaptrResponse(
            int filter,
            List<InetAddress> validIpList,
            boolean isEmergency,
            Network network,
            boolean isRegisteredWith3GPP,
            List<NaptrTarget> naptrResponse,
            Set<String> plmnsFromCarrierConfig,
            String registeredhostName) {
        Set<String> resultSet = new LinkedHashSet<>();

        for (NaptrTarget target : naptrResponse) {
            Log.d(TAG, "NaptrTarget - name: " + target.mName);
            Log.d(TAG, "NaptrTarget - type: " + target.mType);
            if (target.mType == NaptrDnsResolver.TYPE_A) {
                resultSet.add(target.mName);
            }
        }

        /*
         * As 3GPP TS 23.402 4.5.4.5 bullet 2a,
         * if the device registers via 3GPP and its PLMN info is in the NAPTR response,
         * try to connect ePDG with this PLMN info.
         */
        if (isRegisteredWith3GPP) {
            if (resultSet.contains(registeredhostName)) {
                getIP(registeredhostName, filter, validIpList, network);
                resultSet.remove(registeredhostName);
            }
        }

        /*
         * As 3GPP TS 23.402 4.5.4.5 bullet 2b
         * Check if there is any PLMN in both ePDG selection information and the DNS response
         */
        for (String plmn : plmnsFromCarrierConfig) {
            String[] mccmnc = splitMccMnc(plmn);
            String carrierConfighostName = composeFqdnWithMccMnc(mccmnc[0], mccmnc[1], isEmergency);

            if (resultSet.contains(carrierConfighostName)) {
                getIP(carrierConfighostName, filter, validIpList, network);
                resultSet.remove(carrierConfighostName);
            }
        }

        /*
         * Do FQDN with the remaining PLMNs in the ResultSet
         */
        for (String result : resultSet) {
            getIP(result, filter, validIpList, network);
        }
    }

    private void resolutionMethodVisitedCountry(
            int filter, List<InetAddress> validIpList, boolean isEmergency, Network network) {
        StringBuilder domainName = new StringBuilder();

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        telephonyManager =
                Objects.requireNonNull(telephonyManager)
                        .createForSubscriptionId(IwlanHelper.getSubId(mContext, mSlotId));

        if (telephonyManager == null) {
            Log.e(TAG, "TelephonyManager is NULL");
            return;
        }

        final boolean isRegisteredWith3GPP = isRegisteredWith3GPP(telephonyManager);

        // Get ePDG selection information from CarrierConfig
        final Set<String> plmnsFromCarrierConfig =
                new LinkedHashSet<>(
                        Arrays.asList(
                                IwlanHelper.getConfig(
                                        CarrierConfigManager.Iwlan.KEY_MCC_MNCS_STRING_ARRAY,
                                        mContext,
                                        mSlotId)));

        final String cellMcc = telephonyManager.getNetworkOperator().substring(0, 3);
        final String cellMnc = telephonyManager.getNetworkOperator().substring(3);
        final String plmnFromNetwork = cellMcc + "-" + cellMnc;
        final String registeredhostName = composeFqdnWithMccMnc(cellMcc, cellMnc, isEmergency);

        /*
        * As TS 23 402 4.5.4.4 bullet 3a
        * If the UE determines to be located in a country other than its home country
        * If the UE is registered via 3GPP access to a PLMN and this PLMN matches an entry
          in the ePDG selection information, then the UE shall select an ePDG in this PLMN.
        */
        if (isRegisteredWith3GPP) {
            if (plmnsFromCarrierConfig.contains(plmnFromNetwork)) {
                getIP(registeredhostName, filter, validIpList, network);
            }
        }

        /*
         * Visited Country FQDN format:
         * epdg.epc.mcc<MCC>.visited-country.pub.3gppnetwork.org
         *
         * Visited Country Emergency ePDG FQDN format:
         * sos.epdg.epc.mcc<MCC>.visited-country.pub.3gppnetwork.org
         */
        if (isEmergency) {
            domainName.append("sos.");
        }
        domainName
                .append("epdg.epc.mcc")
                .append(cellMcc)
                .append(".visited-country.pub.3gppnetwork.org");

        Log.d(TAG, "Visited Country FQDN with " + domainName);

        CompletableFuture<List<NaptrTarget>> naptrDnsResult = new CompletableFuture<>();
        DnsResolver.Callback<List<NaptrTarget>> naptrDnsCb =
                new DnsResolver.Callback<List<NaptrTarget>>() {
                    @Override
                    public void onAnswer(@NonNull final List<NaptrTarget> answer, final int rcode) {
                        if (rcode == 0 && answer.size() != 0) {
                            naptrDnsResult.complete(answer);
                        } else {
                            naptrDnsResult.completeExceptionally(new UnknownHostException());
                        }
                    }

                    @Override
                    public void onError(@Nullable final DnsException error) {
                        naptrDnsResult.completeExceptionally(error);
                    }
                };
        NaptrDnsResolver.query(network, domainName.toString(), Runnable::run, null, naptrDnsCb);

        try {
            final List<NaptrTarget> naptrResponse =
                    naptrDnsResult.get(DNS_RESOLVER_TIMEOUT_DURATION_SEC, TimeUnit.SECONDS);
            // Check if there is any record in the NAPTR response
            if (naptrResponse != null && naptrResponse.size() > 0) {
                processNaptrResponse(
                        filter,
                        validIpList,
                        isEmergency,
                        network,
                        isRegisteredWith3GPP,
                        naptrResponse,
                        plmnsFromCarrierConfig,
                        registeredhostName);
            }
        } catch (ExecutionException e) {
            Log.e(TAG, "Cause of ExecutionException: ", e.getCause());
        } catch (InterruptedException e) {
            Thread thread = Thread.currentThread();
            if (thread.interrupted()) {
                thread.interrupt();
            }
            Log.e(TAG, "InterruptedException: ", e);
        } catch (TimeoutException e) {
            Log.e(TAG, "TimeoutException: ", e);
        }
    }

    // Cancels duplicate prefetches if a prefetch is already running. Always schedules tunnel
    // bringup.
    protected void trySubmitEpdgSelectionExecutor(
            Runnable runnable, boolean isPrefetch, boolean isEmergency) {
        if (isEmergency) {
            if (isPrefetch) {
                if (mSosDnsPrefetchFuture == null || mSosDnsPrefetchFuture.isDone()) {
                    mSosDnsPrefetchFuture = mSosEpdgSelectionExecutor.submit(runnable);
                }
            } else {
                mSosEpdgSelectionExecutor.execute(runnable);
            }
        } else {
            if (isPrefetch) {
                if (mDnsPrefetchFuture == null || mDnsPrefetchFuture.isDone()) {
                    mDnsPrefetchFuture = mEpdgSelectionExecutor.submit(runnable);
                }
            } else {
                mEpdgSelectionExecutor.execute(runnable);
            }
        }
    }

    /**
     * Asynchronously runs DNS resolution on a carrier-specific list of ePDG servers into IP
     * addresses, and passes them to the caller via the {@link EpdgSelectorCallback}.
     *
     * @param transactionId A unique ID passed in to match the response with the request. If this
     *     value is 0, the caller is not interested in the result.
     * @param filter Allows the caller to filter for IPv4 or IPv6 servers, or both.
     * @param isRoaming Specifies whether the subscription is currently in roaming state.
     * @param isEmergency Specifies whether the ePDG server lookup is to make an emergency call.
     * @param network {@link Network} The server lookups will be performed over this Network.
     * @param selectorCallback {@link EpdgSelectorCallback} The result will be returned through this
     *     callback. If null, the caller is not interested in the result. Typically, this means the
     *     caller is performing DNS prefetch of the ePDG server addresses to warm the native
     *     dnsresolver module's caches.
     * @return {link IwlanError} denoting the status of this operation.
     */
    public IwlanError getValidatedServerList(
            int transactionId,
            @ProtoFilter int filter,
            @EpdgAddressOrder int order,
            boolean isRoaming,
            boolean isEmergency,
            @NonNull Network network,
            EpdgSelectorCallback selectorCallback) {

        final Runnable epdgSelectionRunnable =
                () -> {
                    List<InetAddress> validIpList = new ArrayList<>();
                    Log.d(
                            TAG,
                            "Processing request with transactionId: "
                                    + transactionId
                                    + ", for slotID: "
                                    + mSlotId
                                    + ", isEmergency: "
                                    + isEmergency);

                    int[] addrResolutionMethods =
                            IwlanHelper.getConfig(
                                    CarrierConfigManager.Iwlan.KEY_EPDG_ADDRESS_PRIORITY_INT_ARRAY,
                                    mContext,
                                    mSlotId);

                    final boolean isVisitedCountryMethodRequired =
                            Arrays.stream(addrResolutionMethods)
                                    .anyMatch(
                                            i ->
                                                    i
                                                            == CarrierConfigManager.Iwlan
                                                                    .EPDG_ADDRESS_VISITED_COUNTRY);

                    // In the visited country
                    if (isRoaming && !inSameCountry() && isVisitedCountryMethodRequired) {
                        resolutionMethodVisitedCountry(filter, validIpList, isEmergency, network);
                    }

                    Map<String, List<InetAddress>> plmnDomainNamesToIpAddress = null;
                    for (int addrResolutionMethod : addrResolutionMethods) {
                        switch (addrResolutionMethod) {
                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_STATIC:
                                resolutionMethodStatic(filter, validIpList, network);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_PLMN:
                                plmnDomainNamesToIpAddress =
                                        resolutionMethodPlmn(
                                                filter, validIpList, isEmergency, network);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_PCO:
                                resolutionMethodPco(filter, validIpList);
                                break;

                            case CarrierConfigManager.Iwlan.EPDG_ADDRESS_CELLULAR_LOC:
                                resolutionMethodCellularLoc(
                                        filter, validIpList, isEmergency, network);
                                break;

                            default:
                                Log.d(
                                        TAG,
                                        "Incorrect address resolution method "
                                                + addrResolutionMethod);
                        }
                    }

                    if (selectorCallback != null) {
                        if (mErrorPolicyManager.getMostRecentDataFailCause()
                                == DataFailCause.IWLAN_CONGESTION) {
                            Objects.requireNonNull(plmnDomainNamesToIpAddress)
                                    .values()
                                    .removeIf(List::isEmpty);

                            int numFqdns = plmnDomainNamesToIpAddress.size();
                            int index = mErrorPolicyManager.getCurrentFqdnIndex(numFqdns);
                            if (index >= 0 && index < numFqdns) {
                                Object[] keys = plmnDomainNamesToIpAddress.keySet().toArray();
                                validIpList = plmnDomainNamesToIpAddress.get((String) keys[index]);
                            } else {
                                Log.w(
                                        TAG,
                                        "CONGESTION error handling- invalid index: "
                                                + index
                                                + " number of PLMN FQDNs: "
                                                + numFqdns);
                            }
                        }

                        if (!validIpList.isEmpty()) {
                            validIpList = removeDuplicateIp(validIpList);
                            validIpList = filterExcludedAddresses(validIpList);
                            validIpList = prioritizeIp(validIpList, order);
                            selectorCallback.onServerListChanged(transactionId, validIpList);
                        } else {
                            selectorCallback.onError(
                                    transactionId,
                                    new IwlanError(
                                            IwlanError.EPDG_SELECTOR_SERVER_SELECTION_FAILED));
                        }
                    }
                };

        boolean isPrefetch = (selectorCallback == null);
        trySubmitEpdgSelectionExecutor(epdgSelectionRunnable, isPrefetch, isEmergency);

        return new IwlanError(IwlanError.NO_ERROR);
    }

    /**
     * Validates a PLMN (Public Land Mobile Network) identifier string.
     *
     * @param plmn The PLMN identifier string to validate.
     * @return True if the PLMN identifier is valid, false otherwise.
     */
    private static boolean isValidPlmn(String plmn) {
        return plmn != null && PLMN_PATTERN.matcher(plmn).matches();
    }
}
