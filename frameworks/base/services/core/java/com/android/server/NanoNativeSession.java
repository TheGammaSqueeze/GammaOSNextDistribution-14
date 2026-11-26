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

package com.android.server;

import android.os.SystemProperties;

/**
 * GammaOS Nano: is a native emulator session (drastic-nano, azahar-nano or retroarch-nano) on
 * the panel.
 *
 * <p>The emulators are /system/bin services started by init, not activities, so the
 * framework's app-alive model cannot see them. They may also be started before
 * system_server exists (nano launches them with ctl.start as soon as storage is unlocked),
 * so the activity, window and power services can come up in the middle of a game. Every
 * place that would start the home, raise the nano overlay or sleep the panel asks this
 * first. These properties say a session is live:
 * <ul>
 *     <li>{@code sys.gammaos.drastic_nano.session}: set by nano and drastic-nano, cleared
 *     by drastic-nano.rc on session_done.</li>
 *     <li>{@code sys.gammaos.azahar_nano.session}: set by azahar-nano as it starts, cleared
 *     by azahar-nano.rc on session_done or when the service stops.</li>
 *     <li>{@code sys.gammaos.retroarch_nano.session}: the same for retroarch-nano (native
 *     RetroArch), cleared by retroarch-nano.rc.</li>
 *     <li>{@code sys.gammaos.nano.native_session}: set by nano (and the rc start actions)
 *     for any DRM-direct native launch, cleared by the rc files. It only counts while one
 *     of the services is actually alive, so a launch whose ctl.start never ran cannot
 *     keep the home away for the rest of the boot. It also covers the moment between the
 *     start and the emulator setting its own session.</li>
 * </ul>
 * All are 0 or unset on a normal Android boot, so this is always false there.
 */
public final class NanoNativeSession {
    private NanoNativeSession() {}

    /** True while a drastic-nano, azahar-nano or retroarch-nano session owns the panel. */
    public static boolean isActive() {
        if ("1".equals(SystemProperties.get("sys.gammaos.drastic_nano.session", "0"))) {
            return true;
        }
        if ("1".equals(SystemProperties.get("sys.gammaos.azahar_nano.session", "0"))) {
            return true;
        }
        if ("1".equals(SystemProperties.get("sys.gammaos.retroarch_nano.session", "0"))) {
            return true;
        }
        return "1".equals(SystemProperties.get("sys.gammaos.nano.native_session", "0"))
                && (serviceAlive("drastic-nano") || serviceAlive("azahar-nano")
                        || serviceAlive("retroarch-nano"));
    }

    /** True while a drastic-nano session is live (it has its own graceful-exit channel). */
    public static boolean isDrasticActive() {
        return "1".equals(SystemProperties.get("sys.gammaos.drastic_nano.session", "0"));
    }

    private static boolean serviceAlive(String name) {
        final String state = SystemProperties.get("init.svc." + name, "");
        return "running".equals(state) || "stopping".equals(state)
                || "restarting".equals(state);
    }
}
