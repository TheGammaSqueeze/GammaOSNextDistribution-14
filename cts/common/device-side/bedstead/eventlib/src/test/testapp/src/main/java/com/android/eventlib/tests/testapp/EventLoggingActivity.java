package com.android.eventlib.tests.testapp;

import android.app.Activity;

import com.android.eventlib.events.CustomEvent;

/**
 * An {@link Activity} which, when resumed, logs a {@link CustomEvent} with the
 * passed in tag and data.
 */
public class EventLoggingActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();

        CustomEvent.logger(this)
                .setTag(getIntent().getStringExtra("TAG"))
                .setData(getIntent().getStringExtra("DATA"))
                .log();
    }
}