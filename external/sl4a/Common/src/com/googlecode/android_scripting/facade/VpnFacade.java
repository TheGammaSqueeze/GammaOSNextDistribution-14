/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.googlecode.android_scripting.facade;

import android.app.Service;
import android.net.VpnManager;
import android.os.RemoteException;
import android.security.Credentials;
import android.security.KeyStore;
import android.security.LegacyVpnProfileStore;

import com.android.internal.net.LegacyVpnInfo;
import com.android.internal.net.VpnConfig;
import com.android.internal.net.VpnProfile;
import com.android.internal.util.ArrayUtils;

import com.google.android.collect.Lists;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Access VPN functions.
 */
public class VpnFacade extends RpcReceiver {

    private final Service mService;
    private final VpnManager mVpnManager;
    private CertInstallerHelper mCertHelper;

    public VpnFacade(FacadeManager manager) {
        super(manager);
        mService = manager.getService();
        mCertHelper = new CertInstallerHelper();
        mVpnManager = mService.getSystemService(VpnManager.class);
    }

    static List<VpnProfile> loadVpnProfiles(KeyStore keyStore, int... excludeTypes) {
        final ArrayList<VpnProfile> result = Lists.newArrayList();

        for (String key : LegacyVpnProfileStore.list(Credentials.VPN)) {
            final VpnProfile profile = VpnProfile.decode(key,
                    LegacyVpnProfileStore.get(Credentials.VPN + key));
            if (profile != null && !ArrayUtils.contains(excludeTypes, profile.type)) {
                result.add(profile);
            }
        }
        return result;
    }

    private VpnProfile genLegacyVpnProfile(JSONObject vpnProfileJson) {
        VpnProfile vp = new VpnProfile(vpnProfileJson.optString("key", ""));
        vp.name = vpnProfileJson.optString("name", "");
        vp.type = vpnProfileJson.optInt("type", VpnProfile.TYPE_PPTP);
        vp.server = vpnProfileJson.optString("server", "");
        vp.username = vpnProfileJson.optString("username", "");
        vp.password = vpnProfileJson.optString("password", "");
        vp.dnsServers = vpnProfileJson.optString("dnsServers", "");
        vp.searchDomains = vpnProfileJson.optString("searchDomains", "");
        vp.routes = vpnProfileJson.optString("routes", "");
        vp.mppe = vpnProfileJson.optBoolean("mppe", true);
        vp.l2tpSecret = vpnProfileJson.optString("l2tpSecret", "");
        vp.ipsecIdentifier = vpnProfileJson.optString("ipsecIdentifier", "");
        vp.ipsecSecret = vpnProfileJson.optString("ipsecSecret", "");
        vp.ipsecUserCert = vpnProfileJson.optString("ipsecUserCert", "");
        vp.ipsecCaCert = vpnProfileJson.optString("ipsecCaCert", "");
        vp.ipsecServerCert = vpnProfileJson.optString("ipsecServerCert", "");
        vp.saveLogin = vpnProfileJson.optBoolean("saveLogin", false);
        return vp;
    }

    @Rpc(description = "Start legacy VPN with a profile.")
    public void vpnStartLegacyVpn(@RpcParameter(name = "vpnProfile") JSONObject vpnProfile)
            throws RemoteException {
        VpnProfile profile = genLegacyVpnProfile(vpnProfile);
        mVpnManager.startLegacyVpn(profile);
    }

    @Rpc(description = "Stop the current legacy VPN connection.")
    public void vpnStopLegacyVpn() throws RemoteException {
        mVpnManager.prepareVpn(VpnConfig.LEGACY_VPN, VpnConfig.LEGACY_VPN, mService.getUserId());
    }

    @Rpc(description = "Get the info object of the currently active legacy VPN connection.")
    public LegacyVpnInfo vpnGetLegacyVpnInfo() throws RemoteException {
        return mVpnManager.getLegacyVpnInfo(mService.getUserId());
    }

    @Override
    public void shutdown() {
    }

    @Rpc(description = "Install certificate for RSA VPNs.")
    public void installCertificate(@RpcParameter(name = "vpnProfile") JSONObject vpnProfile,
                                   @RpcParameter(name = "certFile") String certFile,
                                   @RpcParameter(name = "password") String password)
            throws RemoteException {
        VpnProfile profile = genLegacyVpnProfile(vpnProfile);
        mCertHelper.installCertificate(profile, certFile, password);
    }
}
