/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.bips.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.print.PrintJobInfo;
import android.print.PrinterId;
import android.printservice.PrintService;
import android.util.Log;
import android.view.MenuItem;

import com.android.bips.BuiltInPrintService;
import com.android.bips.discovery.DiscoveredPrinter;
import com.android.bips.discovery.Discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Launched by system in response to a "More Options" request while tracking a printer.
 */
public class MoreOptionsActivity extends Activity implements ServiceConnection, Discovery.Listener {
    private static final String TAG = MoreOptionsActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private BuiltInPrintService mPrintService;
    PrinterId mPrinterId;
    DiscoveredPrinter mPrinter;
    InetAddress mPrinterAddress;

    public static final String EXTRA_PRINTER_ID = "EXTRA_PRINTER_ID";
    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().hasExtra(PrintService.EXTRA_PRINT_JOB_INFO)) {
            PrintJobInfo jobInfo =
                    getIntent().getParcelableExtra(PrintService.EXTRA_PRINT_JOB_INFO);
            mPrinterId = jobInfo.getPrinterId();
        } else if (getIntent().hasExtra(EXTRA_PRINTER_ID)) {
            mPrinterId = getIntent().getParcelableExtra(EXTRA_PRINTER_ID);
        } else {
            if (DEBUG) Log.i(TAG, "No job info or printer info to show. Exiting.");
            finish();
            return;
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        getFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, BuiltInPrintService.class), this,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPrintService != null) {
            mPrintService.getDiscovery().stop(this);
        }
        unbindService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mPrintService = BuiltInPrintService.getInstance();
        mPrintService.getDiscovery().start(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mPrintService = null;
    }

    @Override
    public void onPrinterFound(DiscoveredPrinter printer) {
        if (printer.getUri().toString().equals(mPrinterId.getLocalId())) {
            // We discovered a printer matching the job's PrinterId, so show recommendations
            mPrinter = printer;
            setTitle(mPrinter.name);
            mExecutorService.execute(() -> {
                try {
                    mPrinterAddress = InetAddress.getByName(mPrinter.path.getHost());
                    // No need for continued discovery after we find the printer.
                    mPrintService.getDiscovery().stop(this);
                    if (!mExecutorService.isShutdown() && mPrintService != null) {
                        mPrintService.getMainHandler().post(() -> {
                            if (getFragmentManager().getFragments().isEmpty()) {
                                MoreOptionsFragment fragment = new MoreOptionsFragment();
                                getFragmentManager().beginTransaction()
                                        .replace(android.R.id.content, fragment)
                                        .commit();
                            }
                        });
                    }
                } catch (UnknownHostException ignored) { }
            });
        }
    }

    @Override
    public void onPrinterLost(DiscoveredPrinter printer) {
        // Ignore
    }
}
