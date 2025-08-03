/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.sample.rcsclient.carrierLock;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.sample.rcsclient.R;

public class CarrieLockModeListActivity extends AppCompatActivity {

    private final CarrierLockProvider mCarrierLockProvider = new CarrierLockProvider();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.CarrierLockListLayout);

        Button noLockModeBtn = this.findViewById(R.id.noLockMode);
        assert noLockModeBtn != null;
        noLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.UNLOCKED);
            Toast.makeText(this, "Lock mode set to UNLOCKED", Toast.LENGTH_LONG).show();
        });

        Button vzwLockModeBtn = this.findViewById(R.id.lockToVZW);
        assert vzwLockModeBtn != null;
        vzwLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.LOCK_TO_VZW);
            Toast.makeText(this, "Lock mode set to VZW", Toast.LENGTH_LONG).show();
        });

        Button attLockModeBtn = this.findViewById(R.id.lockToATT);
        assert attLockModeBtn != null;
        attLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.LOCK_TO_ATT);
            Toast.makeText(this, "Lock mode set to ATT", Toast.LENGTH_LONG).show();
        });

        Button tmoLockModeBtn = this.findViewById(R.id.lockToTMO);
        assert tmoLockModeBtn != null;
        tmoLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.LOCK_TO_TMO);
            Toast.makeText(this, "Lock mode set to TMO", Toast.LENGTH_LONG).show();
        });

        Button koodoLockModeBtn = this.findViewById(R.id.lockToKOODOS);
        assert koodoLockModeBtn != null;
        koodoLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.LOCK_TO_KOODO);
            Toast.makeText(this, "Lock mode set to KOODO", Toast.LENGTH_LONG).show();
        });

        Button telusLockModeBtn = this.findViewById(R.id.lockToTELUS);
        assert telusLockModeBtn != null;
        telusLockModeBtn.setOnClickListener(view -> {
            mCarrierLockProvider.setLockMode(CarrierRestriction.LOCK_TO_TELUS);
            Toast.makeText(this, "Lock mode set to TELUS", Toast.LENGTH_LONG).show();
        });
    }
}
