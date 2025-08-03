/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.imsserviceentitlement.ts43;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.AddrStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.EntitlementStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.ProvStatus;
import com.android.imsserviceentitlement.ts43.Ts43VowifiStatus.TcStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Ts43VowifiStatusTest {
    @Test
    public void ts43VowifiStatus_vowifiAvailable_vowifiEntitled() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isTrue();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_addressNotRequired_vowifiEntitled() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.NOT_REQUIRED)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isTrue();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_tcStatusNotRequired_vowifiEntitled() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.NOT_REQUIRED)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isTrue();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_provisionNotRequired_vowifiEntitled() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.NOT_REQUIRED)
                        .build();

        assertThat(status.vowifiEntitled()).isTrue();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_addressNotAvailable_serverDataMissing() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isTrue();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_tcStatusAvailable_serverDataMissing() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.NOT_AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isTrue();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_entitlementStatusProvisioning_inProgress() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.PROVISIONING)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isTrue();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_addressInProgress_inProgress() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.IN_PROGRESS)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isTrue();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_tcStatusInProgress_inProgress() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.IN_PROGRESS)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isTrue();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_provStatusNotProvisioned_inProgress() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.NOT_PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isTrue();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_provStatusInProgress_inProgress() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.IN_PROGRESS)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isTrue();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_provStatusInProgress_incompatible() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.INCOMPATIBLE)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isTrue();
    }

    @Test
    public void ts43VowifiStatus_entitlementStatusEnabledAndServerDataMissing_noAnyMatches() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.NOT_AVAILABLE)
                        .setTcStatus(TcStatus.NOT_AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void ts43VowifiStatus_entitlementStatusDisabledAndServerDataNotRequired_noAnyMatches() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.DISABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.vowifiEntitled()).isFalse();
        assertThat(status.serverDataMissing()).isFalse();
        assertThat(status.inProgress()).isFalse();
        assertThat(status.incompatible()).isFalse();
    }

    @Test
    public void toString_vowifiAvailable_statusLogged() {
        Ts43VowifiStatus status =
                Ts43VowifiStatus.builder()
                        .setEntitlementStatus(EntitlementStatus.ENABLED)
                        .setAddrStatus(AddrStatus.AVAILABLE)
                        .setTcStatus(TcStatus.AVAILABLE)
                        .setProvStatus(ProvStatus.PROVISIONED)
                        .build();

        assertThat(status.toString())
                .isEqualTo("Ts43VowifiStatus {"
                        + "entitlementStatus=1,tcStatus=1,addrStatus=1,provStatus=1}");
    }
}
