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

package android.net.http;

import static com.google.common.truth.Truth.assertThat;
import static android.net.http.ExperimentalOptions.UNSET_INT_VALUE;
import static android.net.http.ExperimentalOptions.validConnectionOptions;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.Splitter;
import android.net.http.ExperimentalOptions.OptionalBoolean;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ExperimentalOptionsTest {

  // Some random numbers to test with
  private static final int TEST_VALUE = 2;
  private static final String CONN_STRING = "CHLO,IW10";

  @Test
  public void testExperimentalOptionsConnectionoptions_shouldConsistofCommaSeparatedStringofChars()
      throws JSONException {
    // QUIC connection options are represented with a comma separated list of {3,4}-char strings eg
    // 'ABCD, EFGH'
    JSONObject quicExperiment =
        new JSONObject().put("connection_options", "|we/3," + CONN_STRING + ",bbra@google.com");
    JSONObject experimentalOptions = new JSONObject().put("QUIC", quicExperiment);
    ExperimentalOptions options = new ExperimentalOptions(experimentalOptions.toString());

    String connectionOptions = options.getConnectionOptionsOption();
    Iterable<String> optionsArray = Splitter.on(',').split(connectionOptions);

    assertEquals(CONN_STRING, connectionOptions);
    for (String option : optionsArray) {
      assertThat(validConnectionOptions).contains(option);
    }
  }

  @Test
  public void testNullString_shouldUseUnsetValues() {
    ExperimentalOptions option = new ExperimentalOptions("");

    assertThat(option.getConnectionOptionsOption()).isNull();
    assertEquals(OptionalBoolean.UNSET, option.getStoreServerConfigsInPropertiesOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxServerConfigsStoredInPropertiesOption());
    assertEquals(UNSET_INT_VALUE, option.getIdleConnectionTimeoutSecondsOption());
    assertEquals(OptionalBoolean.UNSET, option.getGoawaySessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.UNSET, option.getCloseSessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.UNSET, option.getMigrateSessionsOnNetworkChangeV2Option());
    assertEquals(OptionalBoolean.UNSET, option.getMigrateSessionsEarlyV2());
    assertEquals(OptionalBoolean.UNSET, option.getDisableBidirectionalStreamsOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxIdleTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(OptionalBoolean.UNSET, option.getEnableSocketRecvOptimizationOption());
    assertEquals(OptionalBoolean.UNSET, option.getAsyncDnsEnableOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsEnableOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsDelayMillisOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsMaxExpiredTimeMillisOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsMaxStaleUsesOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsAllowOtherNetworkOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsPersistToDiskOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsPersistDelayMillisOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsUseStaleOnNameNotResolvedOption());
    assertEquals(OptionalBoolean.UNSET, option.getDisableIpv6OnWifiOption());
  }

  @Test
  public void testInvalidString_shouldUseUnsetValues() {
    ExperimentalOptions option = new ExperimentalOptions("blah");

    assertThat(option.getConnectionOptionsOption()).isNull();
    assertEquals(OptionalBoolean.UNSET, option.getStoreServerConfigsInPropertiesOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxServerConfigsStoredInPropertiesOption());
    assertEquals(UNSET_INT_VALUE, option.getIdleConnectionTimeoutSecondsOption());
    assertEquals(OptionalBoolean.UNSET, option.getGoawaySessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.UNSET, option.getCloseSessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.UNSET, option.getMigrateSessionsOnNetworkChangeV2Option());
    assertEquals(OptionalBoolean.UNSET, option.getMigrateSessionsEarlyV2());
    assertEquals(OptionalBoolean.UNSET, option.getDisableBidirectionalStreamsOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(UNSET_INT_VALUE, option.getMaxIdleTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(OptionalBoolean.UNSET, option.getEnableSocketRecvOptimizationOption());
    assertEquals(OptionalBoolean.UNSET, option.getAsyncDnsEnableOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsEnableOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsDelayMillisOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsMaxExpiredTimeMillisOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsMaxStaleUsesOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsAllowOtherNetworkOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsPersistToDiskOption());
    assertEquals(UNSET_INT_VALUE, option.getStaleDnsPersistDelayMillisOption());
    assertEquals(OptionalBoolean.UNSET, option.getStaleDnsUseStaleOnNameNotResolvedOption());
    assertEquals(OptionalBoolean.UNSET, option.getDisableIpv6OnWifiOption());
  }

  @Test
  public void testSetToTrueOptions_shouldUseSetValues() throws JSONException {
    ExperimentalOptions option = generateExperimentalValuesWith(true);

    assertEquals(OptionalBoolean.TRUE, option.getStoreServerConfigsInPropertiesOption());
    assertEquals(TEST_VALUE, option.getMaxServerConfigsStoredInPropertiesOption());
    assertEquals(TEST_VALUE, option.getIdleConnectionTimeoutSecondsOption());
    assertEquals(OptionalBoolean.TRUE, option.getGoawaySessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.TRUE, option.getCloseSessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.TRUE, option.getMigrateSessionsOnNetworkChangeV2Option());
    assertEquals(OptionalBoolean.TRUE, option.getMigrateSessionsEarlyV2());
    assertEquals(OptionalBoolean.TRUE, option.getDisableBidirectionalStreamsOption());
    assertEquals(TEST_VALUE, option.getMaxTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(TEST_VALUE, option.getMaxIdleTimeBeforeCryptoHandshakeSecondsOption());
    assertEquals(OptionalBoolean.TRUE, option.getEnableSocketRecvOptimizationOption());
    assertEquals(OptionalBoolean.TRUE, option.getAsyncDnsEnableOption());
    assertEquals(OptionalBoolean.TRUE, option.getStaleDnsEnableOption());
    assertEquals(TEST_VALUE, option.getStaleDnsDelayMillisOption());
    assertEquals(TEST_VALUE, option.getStaleDnsMaxExpiredTimeMillisOption());
    assertEquals(TEST_VALUE, option.getStaleDnsMaxStaleUsesOption());
    assertEquals(OptionalBoolean.TRUE, option.getStaleDnsAllowOtherNetworkOption());
    assertEquals(OptionalBoolean.TRUE, option.getStaleDnsPersistToDiskOption());
    assertEquals(TEST_VALUE, option.getStaleDnsPersistDelayMillisOption());
    assertEquals(OptionalBoolean.TRUE, option.getStaleDnsUseStaleOnNameNotResolvedOption());
    assertEquals(OptionalBoolean.TRUE, option.getDisableIpv6OnWifiOption());
  }

  @Test
  public void testSetToFalseOptions_shouldUseFalseValues() throws JSONException {
    ExperimentalOptions option = generateExperimentalValuesWith(false);

    assertEquals(OptionalBoolean.FALSE, option.getStoreServerConfigsInPropertiesOption());
    assertEquals(OptionalBoolean.FALSE, option.getGoawaySessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.FALSE, option.getCloseSessionsOnIpChangeOption());
    assertEquals(OptionalBoolean.FALSE, option.getMigrateSessionsOnNetworkChangeV2Option());
    assertEquals(OptionalBoolean.FALSE, option.getMigrateSessionsEarlyV2());
    assertEquals(OptionalBoolean.FALSE, option.getDisableBidirectionalStreamsOption());
    assertEquals(OptionalBoolean.FALSE, option.getEnableSocketRecvOptimizationOption());
    assertEquals(OptionalBoolean.FALSE, option.getAsyncDnsEnableOption());
    assertEquals(OptionalBoolean.FALSE, option.getStaleDnsEnableOption());
    assertEquals(OptionalBoolean.FALSE, option.getStaleDnsAllowOtherNetworkOption());
    assertEquals(OptionalBoolean.FALSE, option.getStaleDnsPersistToDiskOption());
    assertEquals(OptionalBoolean.FALSE, option.getStaleDnsUseStaleOnNameNotResolvedOption());
    assertEquals(OptionalBoolean.FALSE, option.getDisableIpv6OnWifiOption());
  }

  private ExperimentalOptions generateExperimentalValuesWith(Boolean value) throws JSONException {
    JSONObject quicExperiment =
        new JSONObject()
            .put("connection_options", "")
            .put("store_server_configs_in_properties", value)
            .put("goaway_sessions_on_ip_change", value)
            .put("close_sessions_on_ip_change", value)
            .put("migrate_sessions_on_network_change_v2", value)
            .put("migrate_sessions_early_v2", value)
            .put("disable_bidirectional_streams", value)
            .put("enable_socket_recv_optimization", value);
    JSONObject asyncDNS = new JSONObject().put("enable", value);
    JSONObject staleDNS =
        new JSONObject()
            .put("enable", value)
            .put("allow_other_network", value)
            .put("persist_to_disk", value)
            .put("use_stale_on_name_not_resolved", value);

    // add the integer values if needed
    if (value) {
      quicExperiment
          .put("connection_options", CONN_STRING)
          .put("max_server_configs_stored_in_properties", TEST_VALUE)
          .put("idle_connection_timeout_seconds", TEST_VALUE)
          .put("max_time_before_crypto_handshake_seconds", TEST_VALUE)
          .put("max_idle_time_before_crypto_handshake_seconds", TEST_VALUE);

      staleDNS
          .put("delay_ms", TEST_VALUE)
          .put("max_expired_time_ms", TEST_VALUE)
          .put("max_stale_uses", TEST_VALUE)
          .put("persist_delay_ms", TEST_VALUE);
    }

    JSONObject options =
        new JSONObject()
            .put("QUIC", quicExperiment)
            .put("AsyncDNS", asyncDNS)
            .put("StaleDNS", staleDNS)
            .put("disable_ipv6_on_wifi", value);

    return new ExperimentalOptions(options.toString());
  }
}
