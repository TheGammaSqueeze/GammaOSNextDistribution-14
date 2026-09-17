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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

/**
 * Client for the Syncthing daemon shipped with GammaOS (external/gammaos-syncthing), shared by the
 * Settings and TV Settings apps so both offer exactly the same screens as the nano menu
 * (frameworks/base/cmds/gammaos-nano/NanoSyncthing.cpp is the C++ twin of this class).
 *
 * <p>The daemon is started and stopped by init off {@link #ENABLED_PROP}; everything else goes
 * through its REST API on 127.0.0.1:8384 with the API key read from the daemon's config.xml (the
 * daemon runs as system, as do both Settings apps). Every REST call blocks and must run off the
 * main thread; failures are {@link IOException}s carrying the daemon's own message where it gave one.
 */
public final class SyncthingClient {

    /** 0/1: whether init runs the daemon. The only thing a UI toggles directly. */
    public static final String ENABLED_PROP = "persist.gammaos.syncthing.enabled";
    /** Set to 1 to bounce the daemon (init clears it). */
    public static final String RESTART_PROP = "sys.gammaos.syncthing.restart";

    private static final String BINARY = "/system/bin/syncthing";
    private static final String CONFIG_XML = "/data/misc/syncthing/config.xml";
    private static final String BASE = "http://127.0.0.1:8384";
    private static final int TIMEOUT_MS = 4000;

    /** Where new folders go unless the user picks another path. */
    public static final String DEFAULT_ROOT = "/data/media/0/Syncthing";

    public static final String[] FOLDER_TYPES = { "sendreceive", "sendonly", "receiveonly" };
    public static final String[] VERSIONING_TYPES = { "", "trashcan", "simple", "staggered" };
    public static final int[] RESCAN_SECONDS = { 60, 600, 3600, 86400, 0 };
    public static final String[] COMPRESSION = { "metadata", "always", "never" };

    private String mApiKey;

    // ---- daemon control (properties) -------------------------------------------------------

    /** Existence, not executability: the callers are not allowed to execute it under SELinux. */
    public static boolean isInstalled() { return new File(BINARY).exists(); }
    public static boolean isEnabled() { return SystemProperties.getBoolean(ENABLED_PROP, false); }
    public static void setEnabled(boolean on) { SystemProperties.set(ENABLED_PROP, on ? "1" : "0"); }
    public static void requestRestart() { SystemProperties.set(RESTART_PROP, "1"); }

    // ---- folder paths ----------------------------------------------------------------------

    /**
     * The daemon runs outside the app sandbox, so a synced folder must sit on a raw storage
     * mount: internal storage is /data/media/&lt;user&gt;/... and a removable card is
     * /mnt/media_rw/&lt;volume&gt;/... (what vold mounts underneath the FUSE views apps see at
     * /storage). This maps the paths a user types (/storage/emulated/0, /sdcard,
     * /storage/XXXX-XXXX) onto those raw mounts and leaves anything else alone.
     */
    public static String canonicalFolderPath(String in) {
        StringBuilder sb = new StringBuilder();
        for (char c : in.toCharArray()) {
            if (c != '/' || sb.length() == 0 || sb.charAt(sb.length() - 1) != '/') sb.append(c);
        }
        while (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') sb.setLength(sb.length() - 1);
        String p = sb.toString();
        if (p.equals("/sdcard") || p.startsWith("/sdcard/")) return "/data/media/0" + p.substring(7);
        if (p.equals("/storage/self/primary") || p.startsWith("/storage/self/primary/")) {
            return "/data/media/0" + p.substring(21);
        }
        for (String root : new String[] { "/storage/emulated/", "/mnt/user/" }) {
            if (!p.startsWith(root)) continue;
            String rest = p.substring(root.length());
            if (root.startsWith("/mnt")) {
                int e = rest.indexOf("emulated/");
                if (e < 0) return p;
                rest = rest.substring(e + 9);
            }
            int sl = rest.indexOf('/');
            String user = sl < 0 ? rest : rest.substring(0, sl);
            if (!user.matches("[0-9]+")) return p;
            return "/data/media/" + user + (sl < 0 ? "" : rest.substring(sl));
        }
        if (p.startsWith("/storage/")) {
            String rest = p.substring(9);
            int sl = rest.indexOf('/');
            String vol = sl < 0 ? rest : rest.substring(0, sl);
            if (!vol.isEmpty() && !vol.equals("emulated") && !vol.equals("self")) return "/mnt/media_rw/" + rest;
        }
        return p;
    }

    /** Whether the (canonical) path is somewhere the daemon may use at all. */
    public static boolean isSupportedFolderPath(String path) {
        String p = canonicalFolderPath(path);
        return p.matches("/data/media/[0-9]+(/.*)?") || isRemovableFolderPath(p);
    }

    /** On a removable card, whose FAT file system stores no permission bits. */
    public static boolean isRemovableFolderPath(String path) {
        return canonicalFolderPath(path).matches("/mnt/media_rw/[^/]+(/.*)?");
    }

    // ---- model -----------------------------------------------------------------------------

    public static final class Folder {
        public String id = "", label = "", path = "", type = "sendreceive";
        public boolean paused;
        public List<String> devices = new ArrayList<>();
        public int rescanIntervalS = 3600;
        public boolean fsWatcherEnabled = true;
        public boolean ignorePerms = true;
        public String versioningType = "", versioningParam = "";
        public int minDiskFreePct = 1;
        public JSONObject raw;   // the daemon's full object, so an edit round-trips the rest
    }

    public static final class FolderStatus {
        public String state = "", error = "", stateChanged = "";
        public long globalBytes, localBytes, needBytes, globalFiles, localFiles, needFiles;
        public long receiveOnlyChangedFiles;
        public int pullErrors;
    }

    public static final class Device {
        public String id = "", name = "";
        public List<String> addresses = new ArrayList<>(Arrays.asList("dynamic"));
        public boolean paused, introducer, autoAcceptFolders;
        public String compression = "metadata";
        public JSONObject raw;
    }

    public static final class Connection {
        public boolean connected, paused;
        public String address = "", type = "", clientVersion = "", lastSeen = "";
        public long inBytesTotal, outBytesTotal;
        public double completion = 100.0;
    }

    public static final class PendingDevice { public String id = "", name = "", address = "", time = ""; }
    public static final class PendingFolder { public String id = "", label = "", offeredBy = "", offeredByName = "", time = ""; }

    public static final class Options {
        public String deviceName = "";
        public List<String> listenAddresses = new ArrayList<>();
        public boolean globalAnnounceEnabled = true, localAnnounceEnabled = true, relaysEnabled = true, natEnabled = true;
        public int maxSendKbps, maxRecvKbps;
        public boolean limitBandwidthInLan;
        public int maxFolderConcurrency;
        public int minHomeDiskFreePct = 1;
        public boolean urAccepted, crashReportingEnabled;
    }

    public static final class Gui {
        public String address = "", user = "";
        public boolean passwordSet, useTLS;
    }

    /** Everything one refresh of a screen needs, fetched together. */
    public static final class Snapshot {
        public boolean apiOk;
        public String error = "";
        public String version = "", myID = "";
        public long uptimeS;
        public List<Folder> folders = new ArrayList<>();
        public Map<String, FolderStatus> folderStatus = new HashMap<>();
        public List<Device> devices = new ArrayList<>();
        public Map<String, Connection> connections = new HashMap<>();
        public List<PendingDevice> pendingDevices = new ArrayList<>();
        public List<PendingFolder> pendingFolders = new ArrayList<>();
        public Options options = new Options();
        public Gui gui = new Gui();
        public List<String> listeners = new ArrayList<>();
        public List<String> discoveryErrors = new ArrayList<>();

        public Folder folder(String id) { for (Folder f : folders) if (f.id.equals(id)) return f; return null; }
        public Device device(String id) { for (Device d : devices) if (d.id.equals(id)) return d; return null; }
        public String deviceName(String id) { Device d = device(id); return d != null && !d.name.isEmpty() ? d.name : shortId(id); }
        /** True when the GUI listens on more than loopback. */
        public boolean lanGui() { return !(gui.address.startsWith("127.0.0.1") || gui.address.startsWith("localhost")); }
    }

    // ---- transport -------------------------------------------------------------------------

    private String apiKey() throws IOException {
        if (mApiKey != null) return mApiKey;
        try (InputStream in = new FileInputStream(CONFIG_XML)) {
            byte[] buf = new byte[65536];
            int n = in.read(buf);
            String text = n > 0 ? new String(buf, 0, n, StandardCharsets.UTF_8) : "";
            int a = text.indexOf("<apikey>"), b = a < 0 ? -1 : text.indexOf("</apikey>", a);
            if (a < 0 || b < 0) throw new IOException("no API key in config");
            mApiKey = text.substring(a + 8, b);
            return mApiKey;
        }
    }

    private String call(String method, String path, String body) throws IOException {
        String key = apiKey();
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(TIMEOUT_MS); c.setReadTimeout(TIMEOUT_MS);
        c.setRequestMethod(method);
        c.setRequestProperty("X-API-Key", key);
        c.setUseCaches(false);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            text = bo.toString("UTF-8");
        }
        c.disconnect();
        if (code == 403) { mApiKey = null; throw new IOException("API key rejected"); }
        if (code < 200 || code >= 300) throw new IOException(errorText(text, code));
        return text;
    }

    private static String errorText(String body, int code) {
        try {
            JSONObject o = new JSONObject(body);
            String e = o.optString("error", "");
            if (!e.isEmpty()) return e;
        } catch (JSONException ignored) { }
        String b = body.trim();
        if (b.length() > 160) b = b.substring(0, 160) + "...";
        return b.isEmpty() ? "HTTP " + code : b;
    }

    private JSONObject getJson(String path) throws IOException {
        try { return new JSONObject(call("GET", path, null)); }
        catch (JSONException e) { throw new IOException("bad JSON from " + path); }
    }

    private JSONArray getJsonArray(String path) throws IOException {
        try { return new JSONArray(call("GET", path, null)); }
        catch (JSONException e) { throw new IOException("bad JSON from " + path); }
    }

    // ---- parsing ---------------------------------------------------------------------------

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static Folder parseFolder(JSONObject f) {
        Folder c = new Folder();
        c.id = f.optString("id"); c.label = f.optString("label"); c.path = f.optString("path");
        c.type = f.optString("type", "sendreceive"); c.paused = f.optBoolean("paused");
        c.rescanIntervalS = f.optInt("rescanIntervalS", 3600);
        c.fsWatcherEnabled = f.optBoolean("fsWatcherEnabled", true);
        c.ignorePerms = f.optBoolean("ignorePerms", false);
        c.devices.clear();
        JSONArray ds = f.optJSONArray("devices");
        if (ds != null) for (int i = 0; i < ds.length(); i++) c.devices.add(ds.optJSONObject(i).optString("deviceID"));
        JSONObject v = f.optJSONObject("versioning");
        if (v != null) {
            c.versioningType = v.optString("type");
            JSONObject p = v.optJSONObject("params");
            if (p != null) {
                if ("trashcan".equals(c.versioningType)) c.versioningParam = p.optString("cleanoutDays");
                else if ("simple".equals(c.versioningType)) c.versioningParam = p.optString("keep");
                else if ("staggered".equals(c.versioningType)) c.versioningParam = p.optString("maxAge");
            }
        }
        JSONObject m = f.optJSONObject("minDiskFree");
        if (m != null) c.minDiskFreePct = m.optInt("value", 1);
        c.raw = f;
        return c;
    }

    private static Device parseDevice(JSONObject d) {
        Device c = new Device();
        c.id = d.optString("deviceID"); c.name = d.optString("name");
        c.addresses = strings(d.optJSONArray("addresses"));
        c.paused = d.optBoolean("paused"); c.introducer = d.optBoolean("introducer");
        c.autoAcceptFolders = d.optBoolean("autoAcceptFolders");
        c.compression = d.optString("compression", "metadata");
        c.raw = d;
        return c;
    }

    // ---- reads -----------------------------------------------------------------------------

    /**
     * One full refresh. Throws when the daemon does not answer at all; secondary calls (stats,
     * pending) fail silently and leave their part empty.
     */
    public Snapshot fetchSnapshot() throws IOException {
        Snapshot s = new Snapshot();
        JSONObject st = getJson("/rest/system/status");
        s.apiOk = true;
        s.myID = st.optString("myID");
        s.uptimeS = st.optLong("uptime");
        JSONObject css = st.optJSONObject("connectionServiceStatus");
        if (css != null) for (Iterator<String> it = css.keys(); it.hasNext();) {
            String k = it.next(); String e = css.optJSONObject(k) != null ? css.optJSONObject(k).optString("error", "") : "";
            s.listeners.add(k + ": " + (e.isEmpty() || "null".equals(e) ? "ok" : e));
        }
        JSONObject de = st.optJSONObject("discoveryErrors");
        if (de != null) for (Iterator<String> it = de.keys(); it.hasNext();) {
            String k = it.next(); String e = de.optString(k, "");
            if (!e.isEmpty() && !"null".equals(e)) s.discoveryErrors.add(k + ": " + e);
        }
        try { s.version = getJson("/rest/system/version").optString("version"); } catch (IOException ignored) { }

        JSONObject cfg = getJson("/rest/config");
        JSONArray fs = cfg.optJSONArray("folders");
        if (fs != null) for (int i = 0; i < fs.length(); i++) s.folders.add(parseFolder(fs.optJSONObject(i)));
        JSONArray ds = cfg.optJSONArray("devices");
        if (ds != null) for (int i = 0; i < ds.length(); i++) {
            Device d = parseDevice(ds.optJSONObject(i));
            if (d.id.equals(s.myID)) s.options.deviceName = d.name; else s.devices.add(d);
        }
        JSONObject o = cfg.optJSONObject("options");
        if (o != null) {
            Options op = s.options;
            op.listenAddresses = strings(o.optJSONArray("listenAddresses"));
            op.globalAnnounceEnabled = o.optBoolean("globalAnnounceEnabled", true);
            op.localAnnounceEnabled = o.optBoolean("localAnnounceEnabled", true);
            op.relaysEnabled = o.optBoolean("relaysEnabled", true);
            op.natEnabled = o.optBoolean("natEnabled", true);
            op.maxSendKbps = o.optInt("maxSendKbps"); op.maxRecvKbps = o.optInt("maxRecvKbps");
            op.limitBandwidthInLan = o.optBoolean("limitBandwidthInLan");
            op.maxFolderConcurrency = o.optInt("maxFolderConcurrency");
            op.crashReportingEnabled = o.optBoolean("crashReportingEnabled");
            op.urAccepted = o.optInt("urAccepted") > 0;
            JSONObject m = o.optJSONObject("minHomeDiskFree");
            if (m != null) op.minHomeDiskFreePct = m.optInt("value", 1);
        }
        JSONObject g = cfg.optJSONObject("gui");
        if (g != null) {
            s.gui.address = g.optString("address"); s.gui.user = g.optString("user");
            s.gui.passwordSet = !g.optString("password").isEmpty(); s.gui.useTLS = g.optBoolean("useTLS");
        }
        for (Folder f : s.folders) {
            try {
                JSONObject fst = getJson("/rest/db/status?folder=" + f.id);
                FolderStatus x = new FolderStatus();
                x.state = fst.optString("state"); x.error = fst.optString("error"); x.stateChanged = fst.optString("stateChanged");
                x.globalBytes = fst.optLong("globalBytes"); x.localBytes = fst.optLong("localBytes"); x.needBytes = fst.optLong("needBytes");
                x.globalFiles = fst.optLong("globalFiles"); x.localFiles = fst.optLong("localFiles"); x.needFiles = fst.optLong("needFiles");
                x.receiveOnlyChangedFiles = fst.optLong("receiveOnlyChangedFiles"); x.pullErrors = fst.optInt("pullErrors");
                s.folderStatus.put(f.id, x);
            } catch (IOException ignored) { }
        }
        try {
            JSONObject conns = getJson("/rest/system/connections").optJSONObject("connections");
            if (conns != null) for (Iterator<String> it = conns.keys(); it.hasNext();) {
                String k = it.next(); JSONObject v = conns.optJSONObject(k);
                if (v == null) continue;
                Connection c = new Connection();
                c.connected = v.optBoolean("connected"); c.paused = v.optBoolean("paused");
                c.address = v.optString("address"); c.type = v.optString("type"); c.clientVersion = v.optString("clientVersion");
                c.inBytesTotal = v.optLong("inBytesTotal"); c.outBytesTotal = v.optLong("outBytesTotal");
                s.connections.put(k, c);
            }
        } catch (IOException ignored) { }
        try {
            JSONObject stats = getJson("/rest/stats/device");
            for (Iterator<String> it = stats.keys(); it.hasNext();) {
                String k = it.next();
                Connection c = s.connections.get(k);
                if (c == null) { c = new Connection(); s.connections.put(k, c); }
                c.lastSeen = stats.optJSONObject(k) != null ? stats.optJSONObject(k).optString("lastSeen") : "";
            }
        } catch (IOException ignored) { }
        for (Device d : s.devices) {
            try {
                double comp = getJson("/rest/db/completion?device=" + d.id).optDouble("completion", 100.0);
                Connection c = s.connections.get(d.id);
                if (c == null) { c = new Connection(); s.connections.put(d.id, c); }
                c.completion = comp;
            } catch (IOException ignored) { }
        }
        try {
            JSONObject pd = getJson("/rest/cluster/pending/devices");
            for (Iterator<String> it = pd.keys(); it.hasNext();) {
                String k = it.next(); JSONObject v = pd.optJSONObject(k);
                PendingDevice p = new PendingDevice(); p.id = k;
                if (v != null) { p.name = v.optString("name"); p.address = v.optString("address"); p.time = v.optString("time"); }
                s.pendingDevices.add(p);
            }
        } catch (IOException ignored) { }
        try {
            JSONObject pf = getJson("/rest/cluster/pending/folders");
            for (Iterator<String> it = pf.keys(); it.hasNext();) {
                String fid = it.next(); JSONObject ob = pf.optJSONObject(fid) != null ? pf.optJSONObject(fid).optJSONObject("offeredBy") : null;
                if (ob == null) continue;
                for (Iterator<String> di = ob.keys(); di.hasNext();) {
                    String dev = di.next(); JSONObject v = ob.optJSONObject(dev);
                    PendingFolder p = new PendingFolder(); p.id = fid; p.offeredBy = dev;
                    if (v != null) { p.label = v.optString("label"); p.time = v.optString("time"); }
                    p.offeredByName = s.deviceName(dev);
                    s.pendingFolders.add(p);
                }
            }
        } catch (IOException ignored) { }
        return s;
    }

    /** Tail of the daemon's in-memory log, oldest first, without the structured field tails. */
    public List<String> fetchLog(int maxLines) throws IOException {
        JSONArray m = getJson("/rest/system/log").optJSONArray("messages");
        List<String> out = new ArrayList<>();
        if (m == null) return out;
        int start = Math.max(0, m.length() - maxLines);
        for (int i = start; i < m.length(); i++) {
            JSONObject e = m.optJSONObject(i);
            if (e == null) continue;
            String when = e.optString("when");
            if (when.length() >= 19) when = when.substring(11, 19);
            String msg = e.optString("message");
            int tail = msg.lastIndexOf(" (log.pkg=");
            if (tail >= 0) msg = msg.substring(0, tail);
            out.add(when + "  " + msg);
        }
        return out;
    }

    public List<String> getIgnores(String folderId) throws IOException {
        return strings(getJson("/rest/db/ignores?folder=" + folderId).optJSONArray("ignore"));
    }

    // ---- writes ----------------------------------------------------------------------------

    private static JSONArray array(List<String> in) { JSONArray a = new JSONArray(); for (String s : in) a.put(s); return a; }

    /** Create (PUT is an upsert; a new folder starts from the daemon's defaults) or update a folder. */
    public void putFolder(Folder f) throws IOException {
        try {
            JSONObject obj = f.raw != null ? new JSONObject(f.raw.toString()) : getJson("/rest/config/defaults/folder");
            obj.put("id", f.id).put("label", f.label).put("path", f.path).put("type", f.type).put("paused", f.paused)
               .put("rescanIntervalS", f.rescanIntervalS).put("fsWatcherEnabled", f.fsWatcherEnabled).put("ignorePerms", f.ignorePerms);
            JSONArray devs = new JSONArray();
            for (String id : f.devices) devs.put(new JSONObject().put("deviceID", id));
            obj.put("devices", devs);
            JSONObject params = new JSONObject();
            if (!f.versioningParam.isEmpty()) {
                String key = "trashcan".equals(f.versioningType) ? "cleanoutDays" : "simple".equals(f.versioningType) ? "keep"
                           : "staggered".equals(f.versioningType) ? "maxAge" : null;
                if (key != null) params.put(key, f.versioningParam);
            }
            obj.put("versioning", new JSONObject().put("type", f.versioningType).put("params", params));
            obj.put("minDiskFree", new JSONObject().put("value", f.minDiskFreePct).put("unit", "%"));
            call("PUT", "/rest/config/folders/" + f.id, obj.toString());
        } catch (JSONException e) { throw new IOException(e.getMessage()); }
    }

    public void removeFolder(String id) throws IOException { call("DELETE", "/rest/config/folders/" + id, null); }
    public void setFolderPaused(String id, boolean paused) throws IOException { call("PATCH", "/rest/config/folders/" + id, "{\"paused\":" + paused + "}"); }
    public void rescanFolder(String id) throws IOException { call("POST", "/rest/db/scan?folder=" + id, null); }
    public void overrideFolder(String id) throws IOException { call("POST", "/rest/db/override?folder=" + id, null); }
    public void revertFolder(String id) throws IOException { call("POST", "/rest/db/revert?folder=" + id, null); }
    public void setIgnores(String id, List<String> lines) throws IOException {
        try { call("POST", "/rest/db/ignores?folder=" + id, new JSONObject().put("ignore", array(lines)).toString()); }
        catch (JSONException e) { throw new IOException(e.getMessage()); }
    }

    public void putDevice(Device d) throws IOException {
        try {
            JSONObject obj = d.raw != null ? new JSONObject(d.raw.toString()) : getJson("/rest/config/defaults/device");
            obj.put("deviceID", d.id).put("name", d.name)
               .put("addresses", array(d.addresses.isEmpty() ? Arrays.asList("dynamic") : d.addresses))
               .put("paused", d.paused).put("introducer", d.introducer).put("autoAcceptFolders", d.autoAcceptFolders);
            if (!d.compression.isEmpty()) obj.put("compression", d.compression);
            call("PUT", "/rest/config/devices/" + d.id, obj.toString());
        } catch (JSONException e) { throw new IOException(e.getMessage()); }
    }

    public void removeDevice(String id) throws IOException { call("DELETE", "/rest/config/devices/" + id, null); }
    public void setDevicePaused(String id, boolean paused) throws IOException { call("PATCH", "/rest/config/devices/" + id, "{\"paused\":" + paused + "}"); }
    public void dismissPendingDevice(String id) throws IOException { call("DELETE", "/rest/cluster/pending/devices?device=" + id, null); }
    public void dismissPendingFolder(String folderId, String deviceId) throws IOException {
        call("DELETE", "/rest/cluster/pending/folders?folder=" + folderId + (deviceId == null || deviceId.isEmpty() ? "" : "&device=" + deviceId), null);
    }

    public void setOptions(Options o, String myID) throws IOException {
        try {
            JSONObject v = new JSONObject()
                .put("listenAddresses", array(o.listenAddresses))
                .put("globalAnnounceEnabled", o.globalAnnounceEnabled).put("localAnnounceEnabled", o.localAnnounceEnabled)
                .put("relaysEnabled", o.relaysEnabled).put("natEnabled", o.natEnabled)
                .put("maxSendKbps", o.maxSendKbps).put("maxRecvKbps", o.maxRecvKbps)
                .put("limitBandwidthInLan", o.limitBandwidthInLan).put("maxFolderConcurrency", o.maxFolderConcurrency)
                .put("crashReportingEnabled", o.crashReportingEnabled)
                .put("urAccepted", o.urAccepted ? 3 : -1)   // -1 declines, a positive value is the accepted report version
                .put("minHomeDiskFree", new JSONObject().put("value", o.minHomeDiskFreePct).put("unit", "%"));
            call("PATCH", "/rest/config/options", v.toString());
            if (myID != null && !myID.isEmpty())
                call("PATCH", "/rest/config/devices/" + myID, new JSONObject().put("name", o.deviceName).toString());
        } catch (JSONException e) { throw new IOException(e.getMessage()); }
    }

    /** newPassword null or empty keeps the stored one; the daemon hashes it. */
    public void setGui(Gui g, String newPassword) throws IOException {
        try {
            JSONObject v = new JSONObject().put("address", g.address).put("user", g.user).put("useTLS", g.useTLS);
            if (newPassword != null && !newPassword.isEmpty()) v.put("password", newPassword);
            call("PATCH", "/rest/config/gui", v.toString());
        } catch (JSONException e) { throw new IOException(e.getMessage()); }
    }

    public void restart() throws IOException { call("POST", "/rest/system/restart", null); }

    // ---- helpers ---------------------------------------------------------------------------

    public static String shortId(String id) { return id != null && id.length() > 7 ? id.substring(0, 7) : id; }

    /** Two lines of four groups: fits every dialog and list width. */
    public static String wrapId(String id) {
        return id != null && id.length() >= 63 ? id.substring(0, 31) + "\n" + id.substring(32) : id;
    }

    /** A new folder ID in the web GUI's style (xxxxx-xxxxx). */
    public static String newFolderId() {
        final String alphabet = "abcdefghijkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) sb.append(i == 5 ? '-' : alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    /** Canonical 8x7 dashed form of a typed device ID, or null when it is not one. */
    public static String normalizeDeviceId(String in) {
        if (in == null) return null;
        StringBuilder s = new StringBuilder();
        for (char c : in.toCharArray()) {
            if (c == '-' || Character.isWhitespace(c)) continue;
            s.append(Character.toUpperCase(c));
        }
        if (s.length() != 56) return null;
        for (int i = 0; i < 56; i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '2' && c <= '7'))) return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 56; i += 7) { if (i > 0) out.append('-'); out.append(s, i, i + 7); }
        return out.toString();
    }

    public static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0));
    }

    /** "3 min ago" for an RFC3339 stamp; "never" for Syncthing's zero time or garbage. */
    public static String formatAgo(String ts) {
        if (ts == null || ts.length() < 19 || ts.startsWith("0001")) return "never";
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long t = f.parse(ts.substring(0, 19)).getTime() / 1000;
            int z = Math.max(ts.lastIndexOf('+'), ts.lastIndexOf('-'));
            if (z >= 19 && ts.length() >= z + 6) {
                int off = Integer.parseInt(ts.substring(z + 1, z + 3)) * 3600 + Integer.parseInt(ts.substring(z + 4, z + 6)) * 60;
                t -= ts.charAt(z) == '+' ? off : -off;
            }
            long d = Math.max(0, System.currentTimeMillis() / 1000 - t);
            if (d < 60) return d + " s ago";
            if (d < 3600) return (d / 60) + " min ago";
            if (d < 86400) return (d / 3600) + " h ago";
            return (d / 86400) + " d ago";
        } catch (ParseException | NumberFormatException e) {
            return "never";
        }
    }

    public static String folderTypeLabel(String t) {
        if ("sendonly".equals(t)) return "Send Only";
        if ("receiveonly".equals(t)) return "Receive Only";
        if ("receiveencrypted".equals(t)) return "Receive Encrypted";
        return "Send & Receive";
    }

    public static String folderStateLabel(String s) {
        switch (s == null ? "" : s) {
            case "idle": return "Up to Date";
            case "scanning": return "Scanning";
            case "scan-waiting": return "Waiting to Scan";
            case "syncing": return "Syncing";
            case "sync-preparing": return "Preparing to Sync";
            case "sync-waiting": return "Waiting to Sync";
            case "cleaning": return "Cleaning";
            case "clean-waiting": return "Waiting to Clean";
            case "error": return "Error";
            default: return "Unknown";
        }
    }

    /** One-line state for a folder row, matching the nano screens. */
    public static String folderStateText(Snapshot s, Folder f) {
        if (f.paused) return "Paused";
        FolderStatus st = s.folderStatus.get(f.id);
        if (st == null) return "Unknown";
        if (!st.error.isEmpty()) return "Error";
        String txt = folderStateLabel(st.state);
        if ("syncing".equals(st.state) && st.globalBytes > 0) {
            int pct = (int) (100.0 * (st.globalBytes - st.needBytes) / st.globalBytes);
            txt += " (" + Math.max(0, Math.min(100, pct)) + "%)";
        } else if ("idle".equals(st.state) && st.needBytes > 0) {
            txt = "Out of Sync (" + formatBytes(st.needBytes) + ")";
        } else if ("idle".equals(st.state) && st.receiveOnlyChangedFiles > 0) {
            txt = "Local Additions";
        }
        return txt;
    }

    /** One-line state for a device row. */
    public static String deviceStateText(Snapshot s, Device d) {
        if (d.paused) return "Paused";
        Connection c = s.connections.get(d.id);
        if (c == null || !c.connected) return "Disconnected, last seen " + formatAgo(c != null ? c.lastSeen : "");
        if (c.completion >= 99.95) return "Up to Date";
        return String.format(Locale.US, "Syncing (%.0f%%)", c.completion);
    }
}
