/*
 * Copyright (C) 2026 GammaOS
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

package com.android.settings.handheld;

import android.content.Context;

import com.android.internal.gammaos.GammaShareConfig;
import com.android.settings.R;

/**
 * Turns the reason the share daemon reported for a failed connection into something to show, so a
 * share that cannot connect says why instead of sitting on "Connecting..." while it is retried.
 */
final class NetworkShareErrors {
    private NetworkShareErrors() {}

    /** A short label ("Sign-in refused"), or null when the last attempt reported no error. */
    static String label(Context ctx, GammaShareConfig.Share s) {
        final String e = GammaShareConfig.connectError(s.slot);
        if (e.isEmpty()) return null;
        switch (e) {
            case GammaShareConfig.ERROR_UNREACHABLE:
                return ctx.getString(R.string.network_shares_error_unreachable);
            case GammaShareConfig.ERROR_SIGNIN:
                return ctx.getString(R.string.network_shares_error_signin);
            case GammaShareConfig.ERROR_NOT_FOUND:
                return ctx.getString(R.string.network_shares_error_notfound);
            case GammaShareConfig.ERROR_DENIED:
                return ctx.getString(R.string.network_shares_error_denied);
            case GammaShareConfig.ERROR_CERTIFICATE:
                return ctx.getString(R.string.network_shares_error_certificate);
            case GammaShareConfig.ERROR_TLS:
                return ctx.getString(R.string.network_shares_error_tls);
            case GammaShareConfig.ERROR_PROTOCOL:
                return ctx.getString(R.string.network_shares_error_protocol);
            default:
                return ctx.getString(R.string.network_shares_error_failed);
        }
    }

    /** The label followed by what to do about it, or null when there is no error. */
    static String summary(Context ctx, GammaShareConfig.Share s) {
        final String label = label(ctx, s);
        if (label == null) return null;
        final boolean webdav = GammaShareConfig.TYPE_WEBDAV.equals(s.type);
        final boolean ftp = GammaShareConfig.TYPE_FTP.equals(s.type);
        final String hint;
        switch (GammaShareConfig.connectError(s.slot)) {
            case GammaShareConfig.ERROR_UNREACHABLE:
                hint = ctx.getString(R.string.network_shares_error_unreachable_hint);
                break;
            case GammaShareConfig.ERROR_SIGNIN:
                hint = ctx.getString(R.string.network_shares_error_signin_hint);
                break;
            case GammaShareConfig.ERROR_NOT_FOUND:
                hint = ctx.getString(GammaShareConfig.TYPE_SMB.equals(s.type)
                        ? R.string.network_shares_error_notfound_hint_share
                        : R.string.network_shares_error_notfound_hint_folder);
                break;
            case GammaShareConfig.ERROR_DENIED:
                hint = ctx.getString(GammaShareConfig.TYPE_NFS.equals(s.type)
                        ? R.string.network_shares_error_denied_hint_nfs
                        : R.string.network_shares_error_denied_hint);
                break;
            case GammaShareConfig.ERROR_CERTIFICATE:
                hint = ctx.getString(R.string.network_shares_error_certificate_hint);
                break;
            case GammaShareConfig.ERROR_TLS:
                hint = ctx.getString(webdav
                        ? R.string.network_shares_error_tls_hint_https
                        : R.string.network_shares_error_tls_hint_ftps);
                break;
            case GammaShareConfig.ERROR_PROTOCOL:
                hint = ctx.getString(webdav || ftp
                        ? R.string.network_shares_error_protocol_hint_tls
                        : R.string.network_shares_error_protocol_hint,
                        GammaShareConfig.typeLabel(s.type));
                break;
            default:
                hint = ctx.getString(R.string.network_shares_error_failed_hint);
                break;
        }
        return label + ". " + hint;
    }
}
