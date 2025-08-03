/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.hap;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HapClientStackEventTest {

  @Test
  public void toString_containsProperSubStrings() {
    HapClientStackEvent event;
    String eventStr;
    event = new HapClientStackEvent(0 /* EVENT_TYPE_NONE */);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_NONE");

    event = new HapClientStackEvent(10000);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_UNKNOWN");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
    event.valueInt1 = -1;
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_CONNECTION_STATE_CHANGED");
    assertThat(eventStr).contains("CONNECTION_STATE_UNKNOWN");

    event.valueInt1 = HapClientStackEvent.CONNECTION_STATE_DISCONNECTED;
    eventStr = event.toString();
    assertThat(eventStr).contains("CONNECTION_STATE_DISCONNECTED");

    event.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTING;
    eventStr = event.toString();
    assertThat(eventStr).contains("CONNECTION_STATE_CONNECTING");

    event.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
    eventStr = event.toString();
    assertThat(eventStr).contains("CONNECTION_STATE_CONNECTED");

    event.valueInt1 = HapClientStackEvent.CONNECTION_STATE_DISCONNECTING;
    eventStr = event.toString();
    assertThat(eventStr).contains("CONNECTION_STATE_DISCONNECTING");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_DEVICE_AVAILABLE");

    event.valueInt1 = 1 << HapClientStackEvent.FEATURE_BIT_NUM_TYPE_MONAURAL
            | 1 << HapClientStackEvent.FEATURE_BIT_NUM_SYNCHRONIZATED_PRESETS;
    eventStr = event.toString();
    assertThat(eventStr).contains("TYPE_MONAURAL");
    assertThat(eventStr).contains("SYNCHRONIZATED_PRESETS");

    event.valueInt1 = 1 << HapClientStackEvent.FEATURE_BIT_NUM_TYPE_BANDED
            | 1 << HapClientStackEvent.FEATURE_BIT_NUM_INDEPENDENT_PRESETS;
    eventStr = event.toString();
    assertThat(eventStr).contains("TYPE_BANDED");
    assertThat(eventStr).contains("INDEPENDENT_PRESETS");

    event.valueInt1 = 1 << HapClientStackEvent.FEATURE_BIT_NUM_DYNAMIC_PRESETS
            | 1 << HapClientStackEvent.FEATURE_BIT_NUM_WRITABLE_PRESETS;
    eventStr = event.toString();
    assertThat(eventStr).contains("TYPE_BINAURAL");
    assertThat(eventStr).contains("DYNAMIC_PRESETS");
    assertThat(eventStr).contains("WRITABLE_PRESETS");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_DEVICE_FEATURES);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_DEVICE_FEATURES");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECTED);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_ON_ACTIVE_PRESET_SELECTED");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECT_ERROR);
    event.valueInt1 = -1;
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_ON_ACTIVE_PRESET_SELECT_ERROR");
    assertThat(eventStr).contains("ERROR_UNKNOWN");

    event.valueInt1 = HapClientStackEvent.STATUS_NO_ERROR;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_NO_ERROR");

    event.valueInt1 = HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_SET_NAME_NOT_ALLOWED");

    event.valueInt1 = HapClientStackEvent.STATUS_OPERATION_NOT_SUPPORTED;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_OPERATION_NOT_SUPPORTED");

    event.valueInt1 = HapClientStackEvent.STATUS_OPERATION_NOT_POSSIBLE;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_OPERATION_NOT_POSSIBLE");

    event.valueInt1 = HapClientStackEvent.STATUS_INVALID_PRESET_NAME_LENGTH;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_INVALID_PRESET_NAME_LENGTH");

    event.valueInt1 = HapClientStackEvent.STATUS_INVALID_PRESET_INDEX;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_INVALID_PRESET_INDEX");

    event.valueInt1 = HapClientStackEvent.STATUS_GROUP_OPERATION_NOT_SUPPORTED;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_GROUP_OPERATION_NOT_SUPPORTED");

    event.valueInt1 = HapClientStackEvent.STATUS_PROCEDURE_ALREADY_IN_PROGRESS;
    eventStr = event.toString();
    assertThat(eventStr).contains("STATUS_PROCEDURE_ALREADY_IN_PROGRESS");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO);
    event.valueInt2 = -1;
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_ON_PRESET_INFO");
    assertThat(eventStr).contains("UNKNOWN");

    event.valueInt2 = HapClientStackEvent.PRESET_INFO_REASON_ALL_PRESET_INFO;
    eventStr = event.toString();
    assertThat(eventStr).contains("PRESET_INFO_REASON_ALL_PRESET_INFO");

    event.valueInt2 = HapClientStackEvent.PRESET_INFO_REASON_PRESET_INFO_UPDATE;
    eventStr = event.toString();
    assertThat(eventStr).contains("PRESET_INFO_REASON_PRESET_INFO_UPDATE");

    event.valueInt2 = HapClientStackEvent.PRESET_INFO_REASON_PRESET_DELETED;
    eventStr = event.toString();
    assertThat(eventStr).contains("PRESET_INFO_REASON_PRESET_DELETED");

    event.valueInt2 = HapClientStackEvent.PRESET_INFO_REASON_PRESET_AVAILABILITY_CHANGED;
    eventStr = event.toString();
    assertThat(eventStr).contains("PRESET_INFO_REASON_PRESET_AVAILABILITY_CHANGED");

    event.valueInt2 = HapClientStackEvent.PRESET_INFO_REASON_PRESET_INFO_REQUEST_RESPONSE;
    eventStr = event.toString();
    assertThat(eventStr).contains("PRESET_INFO_REASON_PRESET_INFO_REQUEST_RESPONSE");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_PRESET_NAME_SET_ERROR);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_ON_PRESET_NAME_SET_ERROR");

    event = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_PRESET_INFO_ERROR);
    eventStr = event.toString();
    assertThat(eventStr).contains("EVENT_TYPE_ON_PRESET_INFO_ERROR");
  }
}
