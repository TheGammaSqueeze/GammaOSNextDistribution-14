/*
 * GammaOS Nano Bluetooth media-control bridge.
 *
 * GammaOS Nano is a native (C++) PS3-XMB launcher that plays its own audio/video
 * via AAudio and registers no MediaSession, so Bluetooth AVRCP transport commands
 * (play / pause / skip) from a headphone or car head unit have nothing to route to.
 *
 * This bridge runs inside system_server (like GammapadVibrationBridge) and owns an
 * AVRCP-eligible MediaSession. AVRCP passthrough is dispatched by the framework to
 * the active "media button session"; when this session is active and carries a
 * PlaybackState with the transport action bits, its callbacks fire. We forward each
 * callback to nano via a one-shot system property (sys.gammaos.nano.media) that nano
 * reads each frame. In the other direction we watch nano's published now-playing
 * state (sys.gammaos.nano.media.*) and keep the session active + its PlaybackState /
 * metadata current, so AVRCP routes to us and the headphone/car shows the track.
 */

package com.android.server.media;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Slog;
import android.view.KeyEvent;

import com.android.server.SystemService;

import java.io.BufferedReader;
import java.io.FileReader;

public class NanoMediaBridge extends SystemService {
    private static final String TAG = "NanoMediaBridge";

    private static final String PROP_CMD   = "sys.gammaos.nano.media";        // bridge -> nano (one-shot)
    private static final String PROP_STATE = "sys.gammaos.nano.media.state";  // nano -> bridge
    private static final String PROP_POS   = "sys.gammaos.nano.media.pos";    // whole seconds
    private static final String PROP_DUR   = "sys.gammaos.nano.media.dur";    // whole seconds
    private static final String PROP_META  = "sys.gammaos.nano.media.meta";   // generation counter
    private static final String META_FILE  = "/data/system/nano_media_meta.json";

    private final Context mContext;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private MediaSession mSession;

    public NanoMediaBridge(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public void onStart() {
        Slog.i(TAG, "Starting NanoMediaBridge");
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != SystemService.PHASE_BOOT_COMPLETED) return;
        if (!SystemProperties.getBoolean("persist.gammaos.nano.btmedia", true)) {
            Slog.i(TAG, "disabled by persist.gammaos.nano.btmedia");
            return;
        }
        try {
            mSession = new MediaSession(mContext, "GammaOS Nano");
            mSession.setCallback(mCb, mMainHandler);
        } catch (Exception e) {
            Slog.e(TAG, "MediaSession create failed", e);
            return;
        }

        // The session alone cannot reliably receive AVRCP keys: the framework picks the
        // "media button session" by matching the currently-playing audio UID, and nano's
        // AAudio output runs as uid 0 (root) while our session is owned by system_server
        // (uid 1000), so it is never selected. Register an OnMediaKeyListener instead -
        // the dispatch path consults it BEFORE session selection, so it catches AVRCP
        // transport keys regardless of UID. The session is still kept for now-playing
        // metadata / playback state (headphone display, best effort).
        try {
            MediaSessionManager msm = mContext.getSystemService(MediaSessionManager.class);
            if (msm != null) {
                msm.setOnMediaKeyListener(this::onMediaKey, mMainHandler);
                Slog.i(TAG, "OnMediaKeyListener registered");
            } else {
                Slog.w(TAG, "MediaSessionManager unavailable; key interception disabled");
            }
        } catch (Exception e) {
            Slog.w(TAG, "setOnMediaKeyListener failed", e);
        }

        Thread t = new Thread(this::watchLoop, "NanoMediaWatcher");
        t.setDaemon(true);
        t.start();
        Slog.i(TAG, "NanoMediaBridge active");
    }

    // Intercept Bluetooth/headset transport keys before session selection. Only steals
    // the key while nano is the active player (state != stopped); otherwise it falls
    // through so a real foreground media app keeps its controls.
    private boolean onMediaKey(KeyEvent event) {
        if (event == null) return false;
        String state = SystemProperties.get(PROP_STATE, "stopped");
        if (state.equals("stopped")) return false;   // nano idle -> let others handle
        String cmd;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:          cmd = "play";      break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:         cmd = "pause";     break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:         cmd = "playpause"; break;
            case KeyEvent.KEYCODE_MEDIA_STOP:          cmd = "stop";      break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:  cmd = "next";      break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:        cmd = "prev";      break;
            default: return false;                     // not a transport key -> fall through
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) send(cmd);
        return true;   // consume DOWN + UP so the key does not also reach another session
    }

    // AVRCP passthrough -> nano. The framework splits KEYCODE_MEDIA_PLAY_PAUSE into
    // onPlay/onPause based on our PlaybackState, so we do not override onMediaButtonEvent.
    private final MediaSession.Callback mCb = new MediaSession.Callback() {
        @Override public void onPlay()           { send("play"); }
        @Override public void onPause()          { send("pause"); }
        @Override public void onStop()           { send("stop"); }
        @Override public void onSkipToNext()     { send("next"); }
        @Override public void onSkipToPrevious() { send("prev"); }
    };

    private void send(String cmd) {
        try { SystemProperties.set(PROP_CMD, cmd); }
        catch (Exception e) { Slog.w(TAG, "set " + PROP_CMD + " failed", e); }
    }

    // Watch nano's published state and mirror it into the session. Cheap: the props
    // only change while playing; when nano is idle nothing changes and we stay quiet.
    // Thread.sleep() does not hold a wakelock, so this never blocks suspend.
    private void watchLoop() {
        String lastState = "";
        String lastMetaGen = "";
        int lastPosSec = -1;
        boolean sessionActive = false;
        while (true) {
            try {
                String state = SystemProperties.get(PROP_STATE, "stopped");
                int posSec = SystemProperties.getInt(PROP_POS, 0);
                int durSec = SystemProperties.getInt(PROP_DUR, 0);
                String metaGen = SystemProperties.get(PROP_META, "0");

                if (!metaGen.equals(lastMetaGen)) {
                    lastMetaGen = metaGen;
                    pushMetadata(durSec);
                }
                if (!state.equals(lastState) || posSec != lastPosSec) {
                    lastState = state;
                    lastPosSec = posSec;
                    pushPlaybackState(state, posSec);
                    boolean active = !state.equals("stopped");
                    if (active != sessionActive) {
                        sessionActive = active;
                        final boolean a = active;
                        mMainHandler.post(() -> { if (mSession != null) mSession.setActive(a); });
                    }
                }
            } catch (Exception e) {
                Slog.w(TAG, "watch", e);
            }
            try { Thread.sleep(1000); } catch (InterruptedException ie) { return; }
        }
    }

    private void pushPlaybackState(String state, int posSec) {
        int s = state.equals("playing") ? PlaybackState.STATE_PLAYING
              : state.equals("paused")  ? PlaybackState.STATE_PAUSED
              :                           PlaybackState.STATE_STOPPED;
        // The callback validates these action bits before dispatching onPlay/onPause/etc.
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                     | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                     | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        float rate = state.equals("playing") ? 1f : 0f;
        final PlaybackState ps = new PlaybackState.Builder()
                .setActions(actions)
                .setState(s, (long) posSec * 1000L, rate, SystemClock.elapsedRealtime())
                .build();
        mMainHandler.post(() -> { if (mSession != null) mSession.setPlaybackState(ps); });
    }

    private void pushMetadata(int durSec) {
        String title = "", artist = "", album = "";
        try (BufferedReader r = new BufferedReader(new FileReader(META_FILE))) {
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = r.readLine()) != null) sb.append(ln);
            String json = sb.toString();
            title  = extract(json, "title");
            artist = extract(json, "artist");
            album  = extract(json, "album");
        } catch (Exception e) {
            // metadata file may not exist yet (nothing has played) - leave fields blank
        }
        final MediaMetadata md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, (long) durSec * 1000L)
                .build();
        mMainHandler.post(() -> { if (mSession != null) mSession.setMetadata(md); });
    }

    // Minimal extractor for a flat JSON string field (the writer escapes " and \).
    private static String extract(String json, String key) {
        String pat = "\"" + key + "\":\"";
        int i = json.indexOf(pat);
        if (i < 0) return "";
        i += pat.length();
        StringBuilder sb = new StringBuilder();
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) { sb.append(json.charAt(++i)); continue; }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }
}
