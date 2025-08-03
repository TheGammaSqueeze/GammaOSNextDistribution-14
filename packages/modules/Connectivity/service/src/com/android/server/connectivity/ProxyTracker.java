/**
 * Copyright (c) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.connectivity;

import static android.net.ConnectivitySettingsManager.GLOBAL_HTTP_PROXY_EXCLUSION_LIST;
import static android.net.ConnectivitySettingsManager.GLOBAL_HTTP_PROXY_HOST;
import static android.net.ConnectivitySettingsManager.GLOBAL_HTTP_PROXY_PAC;
import static android.net.ConnectivitySettingsManager.GLOBAL_HTTP_PROXY_PORT;
import static android.provider.Settings.Global.HTTP_PROXY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.LinkProperties;
import android.net.Network;
import android.net.PacProxyManager;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.net.module.util.ProxyUtils;

import java.util.Collections;
import java.util.Objects;

/**
 * A class to handle proxy for ConnectivityService.
 *
 * @hide
 */
public class ProxyTracker {
    private static final String TAG = ProxyTracker.class.getSimpleName();
    private static final boolean DBG = true;

    // EXTRA_PROXY_INFO is now @removed. In order to continue sending it, hardcode its value here.
    // The Proxy.EXTRA_PROXY_INFO constant is not visible to this code because android.net.Proxy
    // a hidden platform constant not visible to mainline modules.
    private static final String EXTRA_PROXY_INFO = "android.intent.extra.PROXY_INFO";

    @NonNull
    private final Context mContext;

    @NonNull
    private final Object mProxyLock = new Object();
    // The global proxy is the proxy that is set device-wide, overriding any network-specific
    // proxy. Note however that proxies are hints ; the system does not enforce their use. Hence
    // this value is only for querying.
    @Nullable
    @GuardedBy("mProxyLock")
    private ProxyInfo mGlobalProxy = null;
    // The default proxy is the proxy that applies to no particular network if the global proxy
    // is not set. Individual networks have their own settings that override this. This member
    // is set through setDefaultProxy, which is called when the default network changes proxies
    // in its LinkProperties, or when ConnectivityService switches to a new default network, or
    // when PacProxyService resolves the proxy.
    @Nullable
    @GuardedBy("mProxyLock")
    private volatile ProxyInfo mDefaultProxy = null;
    // Whether the default proxy is enabled.
    @GuardedBy("mProxyLock")
    private boolean mDefaultProxyEnabled = true;

    private final Handler mConnectivityServiceHandler;

    @Nullable
    private final PacProxyManager mPacProxyManager;

    private class PacProxyInstalledListener implements PacProxyManager.PacProxyInstalledListener {
        private final int mEvent;

        PacProxyInstalledListener(int event) {
            mEvent = event;
        }

        public void onPacProxyInstalled(@Nullable Network network, @NonNull ProxyInfo proxy) {
            Log.i(TAG, "PAC proxy installed on network " + network + " : " + proxy);
            mConnectivityServiceHandler
                    .sendMessage(mConnectivityServiceHandler
                    .obtainMessage(mEvent, new Pair<>(network, proxy)));
        }
    }

    public ProxyTracker(@NonNull final Context context,
            @NonNull final Handler connectivityServiceInternalHandler, final int pacChangedEvent) {
        mContext = context;
        mConnectivityServiceHandler = connectivityServiceInternalHandler;
        mPacProxyManager = context.getSystemService(PacProxyManager.class);

        if (mPacProxyManager != null) {
            PacProxyInstalledListener listener = new PacProxyInstalledListener(pacChangedEvent);
            mPacProxyManager.addPacProxyInstalledListener(
                mConnectivityServiceHandler::post, listener);
        }
    }

    // Convert empty ProxyInfo's to null as null-checks are used to determine if proxies are present
    // (e.g. if mGlobalProxy==null fall back to network-specific proxy, if network-specific
    // proxy is null then there is no proxy in place).
    @Nullable
    private static ProxyInfo canonicalizeProxyInfo(@Nullable final ProxyInfo proxy) {
        if (proxy != null && TextUtils.isEmpty(proxy.getHost())
                && Uri.EMPTY.equals(proxy.getPacFileUrl())) {
            return null;
        }
        return proxy;
    }

    // ProxyInfo equality functions with a couple modifications over ProxyInfo.equals() to make it
    // better for determining if a new proxy broadcast is necessary:
    // 1. Canonicalize empty ProxyInfos to null so an empty proxy compares equal to null so as to
    //    avoid unnecessary broadcasts.
    // 2. Make sure all parts of the ProxyInfo's compare true, including the host when a PAC URL
    //    is in place.  This is important so legacy PAC resolver (see com.android.proxyhandler)
    //    changes aren't missed.  The legacy PAC resolver pretends to be a simple HTTP proxy but
    //    actually uses the PAC to resolve; this results in ProxyInfo's with PAC URL, host and port
    //    all set.
    public static boolean proxyInfoEqual(@Nullable final ProxyInfo a, @Nullable final ProxyInfo b) {
        final ProxyInfo pa = canonicalizeProxyInfo(a);
        final ProxyInfo pb = canonicalizeProxyInfo(b);
        // ProxyInfo.equals() doesn't check hosts when PAC URLs are present, but we need to check
        // hosts even when PAC URLs are present to account for the legacy PAC resolver.
        return Objects.equals(pa, pb) && (pa == null || Objects.equals(pa.getHost(), pb.getHost()));
    }

    /**
     * Gets the default system-wide proxy.
     *
     * This will return the global proxy if set, otherwise the default proxy if in use. Note
     * that this is not necessarily the proxy that any given process should use, as the right
     * proxy for a process is the proxy for the network this process will use, which may be
     * different from this value. This value is simply the default in case there is no proxy set
     * in the network that will be used by a specific process.
     * @return The default system-wide proxy or null if none.
     */
    @Nullable
    public ProxyInfo getDefaultProxy() {
        // This information is already available as a world read/writable jvm property.
        synchronized (mProxyLock) {
            if (mGlobalProxy != null) return mGlobalProxy;
            if (mDefaultProxyEnabled) return mDefaultProxy;
            return null;
        }
    }

    /**
     * Gets the global proxy.
     *
     * @return The global proxy or null if none.
     */
    @Nullable
    public ProxyInfo getGlobalProxy() {
        // This information is already available as a world read/writable jvm property.
        synchronized (mProxyLock) {
            return mGlobalProxy;
        }
    }

    /**
     * Read the global proxy settings and cache them in memory.
     */
    public void loadGlobalProxy() {
        if (loadDeprecatedGlobalHttpProxy()) {
            return;
        }
        ContentResolver res = mContext.getContentResolver();
        String host = Settings.Global.getString(res, GLOBAL_HTTP_PROXY_HOST);
        int port = Settings.Global.getInt(res, GLOBAL_HTTP_PROXY_PORT, 0);
        String exclList = Settings.Global.getString(res, GLOBAL_HTTP_PROXY_EXCLUSION_LIST);
        String pacFileUrl = Settings.Global.getString(res, GLOBAL_HTTP_PROXY_PAC);
        if (!TextUtils.isEmpty(host) || !TextUtils.isEmpty(pacFileUrl)) {
            ProxyInfo proxyProperties;
            if (!TextUtils.isEmpty(pacFileUrl)) {
                proxyProperties = ProxyInfo.buildPacProxy(Uri.parse(pacFileUrl));
            } else {
                proxyProperties = ProxyInfo.buildDirectProxy(host, port,
                        ProxyUtils.exclusionStringAsList(exclList));
            }
            if (!proxyProperties.isValid()) {
                if (DBG) Log.d(TAG, "Invalid proxy properties, ignoring: " + proxyProperties);
                return;
            }

            synchronized (mProxyLock) {
                mGlobalProxy = proxyProperties;
            }

            if (!TextUtils.isEmpty(pacFileUrl) && mPacProxyManager != null) {
                mConnectivityServiceHandler.post(
                        () -> mPacProxyManager.setCurrentProxyScriptUrl(proxyProperties));
            }
        }
    }

    /**
     * Read the global proxy from the deprecated Settings.Global.HTTP_PROXY setting and apply it.
     * Returns {@code true} when global proxy was set successfully from deprecated setting.
     */
    public boolean loadDeprecatedGlobalHttpProxy() {
        final String proxy = Settings.Global.getString(mContext.getContentResolver(), HTTP_PROXY);
        if (!TextUtils.isEmpty(proxy)) {
            String data[] = proxy.split(":");
            if (data.length == 0) {
                return false;
            }

            final String proxyHost = data[0];
            int proxyPort = 8080;
            if (data.length > 1) {
                try {
                    proxyPort = Integer.parseInt(data[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            final ProxyInfo p = ProxyInfo.buildDirectProxy(proxyHost, proxyPort,
                    Collections.emptyList());
            setGlobalProxy(p);
            return true;
        }
        return false;
    }

    /**
     * Sends the system broadcast informing apps about a new proxy configuration.
     *
     * Confusingly this method also sets the PAC file URL. TODO : separate this, it has nothing
     * to do in a "sendProxyBroadcast" method.
     */
    public void sendProxyBroadcast() {
        final ProxyInfo defaultProxy = getDefaultProxy();
        final ProxyInfo proxyInfo = null != defaultProxy ?
                defaultProxy : ProxyInfo.buildDirectProxy("", 0, Collections.emptyList());

        if (mPacProxyManager != null) {
            mPacProxyManager.setCurrentProxyScriptUrl(proxyInfo);
        }

        if (!shouldSendBroadcast(proxyInfo)) {
            return;
        }
        if (DBG) Log.d(TAG, "sending Proxy Broadcast for " + proxyInfo);
        Intent intent = new Intent(Proxy.PROXY_CHANGE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING |
                Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(EXTRA_PROXY_INFO, proxyInfo);
        final long ident = Binder.clearCallingIdentity();
        try {
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private boolean shouldSendBroadcast(ProxyInfo proxy) {
        return Uri.EMPTY.equals(proxy.getPacFileUrl()) || proxy.getPort() > 0;
    }

    /**
     * Sets the global proxy in memory. Also writes the values to the global settings of the device.
     *
     * @param proxyInfo the proxy spec, or null for no proxy.
     */
    public void setGlobalProxy(@Nullable ProxyInfo proxyInfo) {
        synchronized (mProxyLock) {
            // ProxyInfo#equals is not commutative :( and is public API, so it can't be fixed.
            if (proxyInfo == mGlobalProxy) return;
            if (proxyInfo != null && proxyInfo.equals(mGlobalProxy)) return;
            if (mGlobalProxy != null && mGlobalProxy.equals(proxyInfo)) return;

            final String host;
            final int port;
            final String exclList;
            final String pacFileUrl;
            if (proxyInfo != null && (!TextUtils.isEmpty(proxyInfo.getHost()) ||
                    !Uri.EMPTY.equals(proxyInfo.getPacFileUrl()))) {
                if (!proxyInfo.isValid()) {
                    if (DBG) Log.d(TAG, "Invalid proxy properties, ignoring: " + proxyInfo);
                    return;
                }
                mGlobalProxy = new ProxyInfo(proxyInfo);
                host = mGlobalProxy.getHost();
                port = mGlobalProxy.getPort();
                exclList = ProxyUtils.exclusionListAsString(mGlobalProxy.getExclusionList());
                pacFileUrl = Uri.EMPTY.equals(proxyInfo.getPacFileUrl())
                        ? "" : proxyInfo.getPacFileUrl().toString();
            } else {
                host = "";
                port = 0;
                exclList = "";
                pacFileUrl = "";
                mGlobalProxy = null;
            }
            final ContentResolver res = mContext.getContentResolver();
            final long token = Binder.clearCallingIdentity();
            try {
                Settings.Global.putString(res, GLOBAL_HTTP_PROXY_HOST, host);
                Settings.Global.putInt(res, GLOBAL_HTTP_PROXY_PORT, port);
                Settings.Global.putString(res, GLOBAL_HTTP_PROXY_EXCLUSION_LIST, exclList);
                Settings.Global.putString(res, GLOBAL_HTTP_PROXY_PAC, pacFileUrl);
            } finally {
                Binder.restoreCallingIdentity(token);
            }

            sendProxyBroadcast();
        }
    }

    /**
     * Sets the default proxy for the device.
     *
     * The default proxy is the proxy used for networks that do not have a specific proxy.
     * @param proxyInfo the proxy spec, or null for no proxy.
     */
    public void setDefaultProxy(@Nullable ProxyInfo proxyInfo) {
        // The code has been accepting empty proxy objects forever, so for backward
        // compatibility it should continue doing so.
        if (proxyInfo != null && TextUtils.isEmpty(proxyInfo.getHost())
                && Uri.EMPTY.equals(proxyInfo.getPacFileUrl())) {
            proxyInfo = null;
        }
        synchronized (mProxyLock) {
            if (Objects.equals(mDefaultProxy, proxyInfo)) return;
            if (proxyInfo != null && !proxyInfo.isValid()) {
                if (DBG) Log.d(TAG, "Invalid proxy properties, ignoring: " + proxyInfo);
                return;
            }

            // This call could be coming from the PacProxyService, containing the port of the
            // local proxy. If this new proxy matches the global proxy then copy this proxy to the
            // global (to get the correct local port), and send a broadcast.
            // TODO: Switch PacProxyService to have its own message to send back rather than
            // reusing EVENT_HAS_CHANGED_PROXY and this call to handleApplyDefaultProxy.
            if ((mGlobalProxy != null) && (proxyInfo != null)
                    && (!Uri.EMPTY.equals(proxyInfo.getPacFileUrl()))
                    && proxyInfo.getPacFileUrl().equals(mGlobalProxy.getPacFileUrl())) {
                mGlobalProxy = proxyInfo;
                sendProxyBroadcast();
                return;
            }
            mDefaultProxy = proxyInfo;

            if (mGlobalProxy != null) return;
            if (mDefaultProxyEnabled) {
                sendProxyBroadcast();
            }
        }
    }

    private boolean isPacProxy(@Nullable final ProxyInfo info) {
        return null != info && info.isPacProxy();
    }

    /**
     * Adjust the proxy in the link properties if necessary.
     *
     * It is necessary when the proxy in the passed property is for PAC, and the default proxy
     * is also for PAC. This is because the original LinkProperties from the network agent don't
     * include the port for the local proxy as it's not known at creation time, but this class
     * knows it after the proxy service is started.
     *
     * This is safe because there can only ever be one proxy service running on the device, so
     * if the ProxyInfo in the LinkProperties is for PAC, then the port is necessarily the one
     * ProxyTracker knows about.
     *
     * @param lp the LinkProperties to fix up.
     * @param network the network of the local proxy server.
     */
    // TODO: Leave network unused to support local proxy server per network in the future.
    public void updateDefaultNetworkProxyPortForPAC(@NonNull final LinkProperties lp,
            @Nullable Network network) {
        final ProxyInfo defaultProxy = getDefaultProxy();
        if (isPacProxy(lp.getHttpProxy()) && isPacProxy(defaultProxy)) {
            synchronized (mProxyLock) {
                // At this time, this method can only be called for the default network's LP.
                // Therefore the PAC file URL in the LP must match the one in the default proxy,
                // and we just update the port.
                // Note that the global proxy, if any, is set out of band by the DPM and becomes
                // the default proxy (it overrides it, see {@link getDefaultProxy}). The PAC URL
                // in the global proxy might not be the one in the LP of the default
                // network, so discount this case.
                if (null == mGlobalProxy && !lp.getHttpProxy().getPacFileUrl()
                        .equals(defaultProxy.getPacFileUrl())) {
                    Log.wtf(TAG, "Unexpected discrepancy between proxy in LP of "
                            + "default network and default proxy. The former has a PAC URL of "
                            + lp.getHttpProxy().getPacFileUrl() + " while the latter has "
                            + defaultProxy.getPacFileUrl());
                }
            }
            // If this network has a PAC proxy and proxy tracker already knows about
            // it, now is the right time to patch it in. If proxy tracker does not know
            // about it yet, then it will be patched in when it learns about it.
            lp.setHttpProxy(defaultProxy);
        }
    }
}
