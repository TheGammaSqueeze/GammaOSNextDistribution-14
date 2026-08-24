package com.gammaos.shadercontrol;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Transparent, no-content activity that GammaOS Nano brings to the front while the PSP slide clock is
 * open over a running game. Fronting it makes the game receive the normal onPause/onStop lifecycle so
 * it pauses ITSELF cleanly - this is deliberately NOT a SIGSTOP / cgroup freeze, which left apps like
 * RetroArch deadlocked on resume (the OS keeps sending binder to a frozen process and it never
 * recovers). nano launches this on clock-open and re-launches it with the boolean "finish" extra on
 * clock-close, so the game gets onResume and comes back on its own.
 *
 * nano's home/clock overlay is a SurfaceFlinger layer far above the activity window, so this activity
 * is never actually visible - it only exists to drive the game's lifecycle. It is hidden from recents
 * and the launcher, lives in its own task, and a 60s watchdog finishes it if nano dies mid-summon so a
 * game can never be stranded paused.
 */
public class NanoPauseActivity extends Activity {

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mWatchdog = new Runnable() {
        @Override public void run() { finish(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("finish", false)) {
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        // (Re)arm the crash-safety watchdog: never hold the game paused for more than a minute.
        mHandler.removeCallbacks(mWatchdog);
        mHandler.postDelayed(mWatchdog, 60000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mWatchdog);
    }
}
