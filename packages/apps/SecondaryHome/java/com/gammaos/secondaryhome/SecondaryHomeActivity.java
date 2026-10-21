package com.gammaos.secondaryhome;

import android.content.Intent;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Home of the secondary (bottom) display in normal Android mode: it hosts the control centre
 * ({@link CcPanel}) when nothing else runs on that panel. Over a running bottom app the same
 * centre is shown by {@link OverlayService} instead, as a window, so the app is not paused.
 */
public class SecondaryHomeActivity extends AppCompatActivity {
    private CcPanel mPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Display own = getDisplay();
        final int ownId = own != null ? own.getDisplayId() : Display.DEFAULT_DISPLAY;
        mPanel = new CcPanel(this, ownId, false, new CcPanel.Shell() {
            @Override
            public void startOnOwnDisplay(Intent intent) {
                // startActivity from this activity keeps the launch on this (the secondary) display
                startActivity(intent);
            }

            @Override
            public void onAppStarted() {
            }
        });
        setContentView(mPanel);
        // Touch only, like the nano control centre: a focusable window would make this display
        // the focused one on the first tap and every pad key after that would leave the game
        // on the top screen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    @Override
    protected void onDestroy() {
        mPanel.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPanel.resume();
    }

    @Override
    protected void onPause() {
        mPanel.pause();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed on this display: show the dashboard, like the nano CC.
        mPanel.showDashboard();
    }
}
