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
package com.android.internal.gammaos;

import android.os.SystemProperties;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Network share configuration, shared by Settings and TvSettings.
 *
 * A share is served by the gammaos-sharefs FUSE daemon, which speaks SMB2/3, NFS, WebDAV or FTP in
 * userspace and presents the result at /mnt/shares/&lt;name&gt;. The device kernels this runs on have
 * no cifs or nfs and cannot be replaced, which is why the protocol is spoken here rather than
 * mounted by the kernel.
 *
 * <p>This class is a deliberate mirror of frameworks/base/cmds/gammaos-sharefs/share_config.cpp,
 * which the daemon and the GammaOS Nano menu both link. The two have to agree exactly: a share
 * added in Settings must be readable by the daemon and editable in Nano, and vice versa. If the
 * property names or the password encoding change in one, change them in the other.
 *
 * <p>Nothing here starts or stops a mount directly. init has a trigger per slot watching
 * {@code persist.gammaos.share.<n>.enabled}, so setting that property is the whole mechanism and no
 * caller needs permission to control services.
 *
 * @hide
 */
public final class GammaShareConfig {

    /**
     * How many shares the system offers. Kept small deliberately: each one is a resident process
     * holding a connection, and these are memory-constrained devices.
     */
    public static final int MAX_SHARES = 4;

    /** Where a mounted share appears. */
    public static final String MOUNT_ROOT = "/mnt/shares";

    public static final String TYPE_SMB = "smb";
    public static final String TYPE_NFS = "nfs";
    public static final String TYPE_WEBDAV = "webdav";
    public static final String TYPE_FTP = "ftp";

    private static final String PREFIX = "persist.gammaos.share.";

    private GammaShareConfig() {}

    /** One configured share. Fields map one-to-one onto the properties for its slot. */
    public static final class Share {
        /** 1..MAX_SHARES. Identifies which property set this came from. */
        public int slot;
        /** Display name, also the directory name under {@link #MOUNT_ROOT}. */
        public String name = "";
        /** One of the TYPE_* constants. */
        public String type = TYPE_SMB;
        /** Hostname or address. */
        public String host = "";
        /** 0 means the protocol default. */
        public int port;
        /** SMB share name, or the remote directory for NFS/WebDAV/FTP. */
        public String path = "";
        /** Empty means connect as guest/anonymous. Unused for NFS. */
        public String user = "";
        /** Plain text here; encrypted on the way to the property. */
        public String password = "";
        /** SMB workgroup, optional. */
        public String domain = "";
        public boolean readOnly;
        /** The user's intent. Whether it is mounted right now is {@link #isMounted}. */
        public boolean enabled;
        /**
         * Encrypt the connection: https for WebDAV, ftps for FTP. Its own flag rather than being
         * inferred from the port, because a NAS commonly serves WebDAV over TLS on a port of its
         * own (Synology's default is 5006) and inferring from 443 would make that unreachable.
         * Ignored by SMB, which negotiates its own encryption, and by NFS.
         */
        public boolean useTls;

        /** True when this protocol can be encrypted by the {@link #useTls} flag. */
        public boolean supportsTls() {
            return TYPE_WEBDAV.equals(type) || TYPE_FTP.equals(type);
        }

        /** True when this protocol authenticates with an account rather than by address. */
        public boolean usesCredentials() {
            return !TYPE_NFS.equals(type);
        }

        /** The port this share will actually use. */
        public int effectivePort() {
            return port > 0 ? port : defaultPort(type, useTls);
        }

        /** Where this share appears once mounted. */
        public String mountPoint() {
            return MOUNT_ROOT + "/" + name;
        }
    }

    /**
     * The default port for a protocol, for showing what a blank port field means. WebDAV moves to
     * 443 once TLS is on, so the hint tracks the encryption toggle rather than being fixed.
     */
    public static int defaultPort(String type, boolean useTls) {
        if (TYPE_SMB.equals(type)) return 445;
        if (TYPE_NFS.equals(type)) return 2049;
        if (TYPE_WEBDAV.equals(type)) return useTls ? 443 : 80;
        if (TYPE_FTP.equals(type)) return 21;
        return 0;
    }

    /** Human-readable protocol name. */
    public static String typeLabel(String type) {
        if (TYPE_SMB.equals(type)) return "SMB";
        if (TYPE_NFS.equals(type)) return "NFS";
        if (TYPE_WEBDAV.equals(type)) return "WebDAV";
        if (TYPE_FTP.equals(type)) return "FTP";
        return type;
    }

    // ---- storage ----------------------------------------------------------

    private static String prop(int slot, String field, String def) {
        return SystemProperties.get(PREFIX + slot + "." + field, def);
    }

    private static void setProp(int slot, String field, String value) {
        SystemProperties.set(PREFIX + slot + "." + field, value == null ? "" : value);
    }

    /** Reads one slot, or returns null when that slot holds no share. */
    public static Share load(int slot) {
        if (slot < 1 || slot > MAX_SHARES) return null;
        String name = prop(slot, "name", "");
        if (name.isEmpty()) return null;
        Share s = new Share();
        s.slot = slot;
        s.name = name;
        s.type = prop(slot, "type", TYPE_SMB);
        s.host = prop(slot, "host", "");
        s.path = prop(slot, "path", "");
        s.user = prop(slot, "user", "");
        s.domain = prop(slot, "domain", "");
        s.password = decryptSecret(prop(slot, "pass", ""));
        try {
            s.port = Integer.parseInt(prop(slot, "port", "0"));
        } catch (NumberFormatException e) {
            s.port = 0;
        }
        s.readOnly = "1".equals(prop(slot, "ro", "0"));
        s.useTls = "1".equals(prop(slot, "tls", "0"));
        s.enabled = "1".equals(prop(slot, "enabled", "0"));
        return s;
    }

    /** Every configured share, in slot order. */
    public static List<Share> loadAll() {
        List<Share> out = new ArrayList<>();
        for (int i = 1; i <= MAX_SHARES; i++) {
            Share s = load(i);
            if (s != null) out.add(s);
        }
        return out;
    }

    /**
     * Writes a share into its slot, encrypting the password. Does not change the enabled flag, so
     * editing a running share does not silently restart it; use {@link #setEnabled}.
     */
    public static void save(Share s) {
        if (s == null || s.slot < 1 || s.slot > MAX_SHARES) return;
        setProp(s.slot, "name", s.name);
        setProp(s.slot, "type", s.type);
        setProp(s.slot, "host", s.host);
        setProp(s.slot, "path", s.path);
        setProp(s.slot, "user", s.user);
        setProp(s.slot, "domain", s.domain);
        setProp(s.slot, "pass", encryptSecret(s.password));
        setProp(s.slot, "port", s.port > 0 ? Integer.toString(s.port) : "0");
        setProp(s.slot, "ro", s.readOnly ? "1" : "0");
        setProp(s.slot, "tls", s.useTls ? "1" : "0");
    }

    /** Starts or stops the mount by setting the flag init watches. */
    public static void setEnabled(int slot, boolean enabled) {
        if (slot < 1 || slot > MAX_SHARES) return;
        setProp(slot, "enabled", enabled ? "1" : "0");
    }

    /** Clears a slot completely, stopping its mount first. */
    public static void delete(int slot) {
        if (slot < 1 || slot > MAX_SHARES) return;
        setEnabled(slot, false);
        for (String f : new String[] {
                "name", "type", "host", "path", "user", "domain", "pass", "port", "ro",
                "tls" }) {
            setProp(slot, f, "");
        }
    }

    /** The first unused slot, or 0 when all of them are taken. */
    public static int firstFreeSlot() {
        for (int i = 1; i <= MAX_SHARES; i++) {
            if (prop(i, "name", "").isEmpty()) return i;
        }
        return 0;
    }

    // ---- state ------------------------------------------------------------

    /**
     * Is this share's FUSE mount live right now?
     *
     * <p>Reads the kernel mount table rather than stat-ing the mount point: the daemon creates the
     * directory before it connects, so the directory existing proves nothing, and stat-ing a mount
     * whose server has gone away blocks until FUSE times out.
     */
    public static boolean isMounted(String name) {
        if (name == null || name.isEmpty()) return false;
        final String want = MOUNT_ROOT + "/" + name;
        try (InputStream in = new FileInputStream("/proc/self/mountinfo")) {
            byte[] buf = new byte[64 * 1024];
            int n, len = 0;
            while ((n = in.read(buf, len, buf.length - len)) > 0) {
                len += n;
                if (len == buf.length) break;
            }
            for (String line : new String(buf, 0, len, StandardCharsets.UTF_8).split("\n")) {
                // Field 5 (1-based) is the mount point.
                String[] f = line.split(" ");
                if (f.length > 4 && want.equals(f[4])) return true;
            }
        } catch (IOException e) {
            // Treat an unreadable mount table as "not mounted"; the UI just shows it disconnected.
        }
        return false;
    }

    /**
     * Everything wrong with this share that would stop it mounting, as a short sentence, or null
     * when it looks complete. Used to explain a disabled toggle rather than letting the mount fail
     * silently in the background.
     */
    public static String problem(Share s) {
        if (s == null) return "No share";
        if (s.name == null || s.name.isEmpty()) return "Give the share a name";
        // The name becomes a directory under /mnt/shares, so it has to be a usable one.
        if (s.name.contains("/") || s.name.equals(".") || s.name.equals("..")) {
            return "The name cannot contain a slash";
        }
        if (s.type == null || s.type.isEmpty()) return "Choose a share type";
        if (s.host == null || s.host.isEmpty()) return "Enter the server address";
        if (TYPE_SMB.equals(s.type) && (s.path == null || s.path.isEmpty())) {
            return "Enter the share name on the server";
        }
        if (TYPE_NFS.equals(s.type) && (s.path == null || s.path.isEmpty())) {
            return "Enter the exported path on the server";
        }
        return null;
    }

    // ---- credentials ------------------------------------------------------

    /*
     * Passwords are kept obfuscated rather than in clear text, keyed off a value that differs per
     * device. To be explicit about what this is worth: anyone with root on the device can recover
     * them, because the daemon itself has to be able to. It stops a share password being readable
     * in a property dump that gets copied off the device, pasted into a bug report, or read over
     * adb, which is the realistic exposure here. A stronger scheme would need a keystore-backed
     * key, which the native daemon cannot reach before the framework is up, and shares must mount
     * at boot.
     *
     * share_config.cpp implements exactly this; keep the two in step.
     */

    private static byte[] deviceKey() {
        // The key has to be derivable before the framework is up (shares mount at boot) and stable
        // across reboots, which rules out anything random or keystore-backed. The head of
        // build.prop satisfies both and differs between builds/devices.
        byte[] seed = "gammaos-sharefs".getBytes(StandardCharsets.UTF_8);
        byte[] head = new byte[127];
        int got = 0;
        try (InputStream in = new FileInputStream("/system/build.prop")) {
            int n;
            while (got < head.length && (n = in.read(head, got, head.length - got)) > 0) {
                got += n;
            }
        } catch (IOException e) {
            // Fall back to the bare seed. It still decrypts what it encrypted, which keeps the UI
            // self-consistent even if the daemon would disagree.
        }
        byte[] key = new byte[seed.length + got];
        System.arraycopy(seed, 0, key, 0, seed.length);
        System.arraycopy(head, 0, key, seed.length, got);
        return key;
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Encodes a password for storage. */
    public static String encryptSecret(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        byte[] key = deviceKey();
        // The native side XORs the raw bytes of the password, so encode to UTF-8 first rather than
        // XOR-ing chars, or anything non-ASCII would round-trip differently in the two languages.
        byte[] in = plain.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(in.length * 2);
        for (int i = 0; i < in.length; i++) {
            int c = (in[i] ^ key[i % key.length]) & 0xFF;
            out.append(HEX[c >> 4]).append(HEX[c & 0xF]);
        }
        return out.toString();
    }

    /** Decodes a stored password. */
    public static String decryptSecret(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        byte[] key = deviceKey();
        int pairs = stored.length() / 2;
        byte[] out = new byte[pairs];
        for (int i = 0; i < pairs; i++) {
            int c;
            try {
                c = Integer.parseInt(stored.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                return "";   // not something we wrote; treat it as no password
            }
            out[i] = (byte) (c ^ key[i % key.length]);
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
