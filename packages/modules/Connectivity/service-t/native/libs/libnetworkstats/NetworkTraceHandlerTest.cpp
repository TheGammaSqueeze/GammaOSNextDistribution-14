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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <vector>

#include "netdbpf/NetworkTraceHandler.h"
#include "protos/perfetto/config/android/network_trace_config.gen.h"
#include "protos/perfetto/trace/android/network_trace.pb.h"
#include "protos/perfetto/trace/trace.pb.h"
#include "protos/perfetto/trace/trace_packet.pb.h"

namespace android {
namespace bpf {
using ::perfetto::protos::NetworkPacketEvent;
using ::perfetto::protos::NetworkPacketTraceConfig;
using ::perfetto::protos::Trace;
using ::perfetto::protos::TracePacket;
using ::perfetto::protos::TrafficDirection;

class NetworkTraceHandlerTest : public testing::Test {
 protected:
  // Starts a tracing session with the handler under test.
  std::unique_ptr<perfetto::TracingSession> StartTracing(
      NetworkPacketTraceConfig settings) {
    perfetto::TracingInitArgs args;
    args.backends = perfetto::kInProcessBackend;
    perfetto::Tracing::Initialize(args);

    perfetto::DataSourceDescriptor dsd;
    dsd.set_name("test.network_packets");
    NetworkTraceHandler::Register(dsd, /*isTest=*/true);

    perfetto::TraceConfig cfg;
    cfg.add_buffers()->set_size_kb(1024);
    auto* config = cfg.add_data_sources()->mutable_config();
    config->set_name("test.network_packets");
    config->set_network_packet_trace_config_raw(settings.SerializeAsString());

    auto session = perfetto::Tracing::NewTrace(perfetto::kInProcessBackend);
    session->Setup(cfg);
    session->StartBlocking();
    return session;
  }

  // Stops the trace session and reports all relevant trace packets.
  bool StopTracing(perfetto::TracingSession* session,
                   std::vector<TracePacket>* output) {
    session->StopBlocking();

    Trace trace;
    std::vector<char> raw_trace = session->ReadTraceBlocking();
    if (!trace.ParseFromArray(raw_trace.data(), raw_trace.size())) {
      ADD_FAILURE() << "trace.ParseFromArray failed";
      return false;
    }

    // This is a real trace and includes irrelevant trace packets such as trace
    // metadata. The following strips the results to just the packets we want.
    for (const auto& pkt : trace.packet()) {
      if (pkt.has_network_packet() || pkt.has_network_packet_bundle()) {
        output->emplace_back(pkt);
      }
    }

    return true;
  }

  // This runs a trace with a single call to Write.
  bool TraceAndSortPackets(const std::vector<PacketTrace>& input,
                           std::vector<TracePacket>* output,
                           NetworkPacketTraceConfig config = {}) {
    auto session = StartTracing(config);
    NetworkTraceHandler::Trace([&](NetworkTraceHandler::TraceContext ctx) {
      ctx.GetDataSourceLocked()->Write(input, ctx);
      ctx.Flush();
    });

    if (!StopTracing(session.get(), output)) {
      return false;
    }

    // Sort to provide deterministic ordering regardless of Perfetto internals
    // or implementation-defined (e.g. hash map) reshuffling.
    std::sort(output->begin(), output->end(),
              [](const TracePacket& a, const TracePacket& b) {
                return a.timestamp() < b.timestamp();
              });

    return true;
  }
};

TEST_F(NetworkTraceHandlerTest, WriteBasicFields) {
  std::vector<PacketTrace> input = {
      PacketTrace{
          .timestampNs = 1000,
          .length = 100,
          .uid = 10,
          .tag = 123,
          .ipProto = IPPROTO_TCP,
          .tcpFlags = 1,
      },
  };

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events));

  ASSERT_EQ(events.size(), 1);
  EXPECT_THAT(events[0].timestamp(), 1000);
  EXPECT_THAT(events[0].network_packet().uid(), 10);
  EXPECT_THAT(events[0].network_packet().tag(), 123);
  EXPECT_THAT(events[0].network_packet().ip_proto(), 6);
  EXPECT_THAT(events[0].network_packet().tcp_flags(), 1);
  EXPECT_THAT(events[0].network_packet().length(), 100);
  EXPECT_THAT(events[0].has_sequence_flags(), false);
}

TEST_F(NetworkTraceHandlerTest, WriteDirectionAndPorts) {
  std::vector<PacketTrace> input = {
      PacketTrace{
          .timestampNs = 1,
          .sport = htons(8080),
          .dport = htons(443),
          .egress = true,
          .ipProto = IPPROTO_TCP,
      },
      PacketTrace{
          .timestampNs = 2,
          .sport = htons(443),
          .dport = htons(8080),
          .egress = false,
          .ipProto = IPPROTO_TCP,
      },
  };

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events));

  ASSERT_EQ(events.size(), 2);
  EXPECT_THAT(events[0].network_packet().local_port(), 8080);
  EXPECT_THAT(events[0].network_packet().remote_port(), 443);
  EXPECT_THAT(events[0].network_packet().direction(),
              TrafficDirection::DIR_EGRESS);
  EXPECT_THAT(events[1].network_packet().local_port(), 8080);
  EXPECT_THAT(events[1].network_packet().remote_port(), 443);
  EXPECT_THAT(events[1].network_packet().direction(),
              TrafficDirection::DIR_INGRESS);
}

TEST_F(NetworkTraceHandlerTest, WriteIcmpTypeAndCode) {
  std::vector<PacketTrace> input = {
      PacketTrace{
          .timestampNs = 1,
          .sport = htons(11),  // type
          .dport = htons(22),  // code
          .egress = true,
          .ipProto = IPPROTO_ICMP,
      },
      PacketTrace{
          .timestampNs = 2,
          .sport = htons(33),  // type
          .dport = htons(44),  // code
          .egress = false,
          .ipProto = IPPROTO_ICMPV6,
      },
  };

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events));

  ASSERT_EQ(events.size(), 2);
  EXPECT_FALSE(events[0].network_packet().has_local_port());
  EXPECT_FALSE(events[0].network_packet().has_remote_port());
  EXPECT_THAT(events[0].network_packet().icmp_type(), 11);
  EXPECT_THAT(events[0].network_packet().icmp_code(), 22);
  EXPECT_THAT(events[0].network_packet().direction(),
              TrafficDirection::DIR_EGRESS);
  EXPECT_FALSE(events[1].network_packet().local_port());
  EXPECT_FALSE(events[1].network_packet().remote_port());
  EXPECT_THAT(events[1].network_packet().icmp_type(), 33);
  EXPECT_THAT(events[1].network_packet().icmp_code(), 44);
  EXPECT_THAT(events[1].network_packet().direction(),
              TrafficDirection::DIR_INGRESS);
}

TEST_F(NetworkTraceHandlerTest, BasicBundling) {
  // TODO: remove this once bundling becomes default. Until then, set arbitrary
  // aggregation threshold to enable bundling.
  NetworkPacketTraceConfig config;
  config.set_aggregation_threshold(10);

  std::vector<PacketTrace> input = {
      PacketTrace{.timestampNs = 2, .length = 200, .uid = 123},
      PacketTrace{.timestampNs = 1, .length = 100, .uid = 123},
      PacketTrace{.timestampNs = 4, .length = 300, .uid = 123},

      PacketTrace{.timestampNs = 2, .length = 400, .uid = 456},
      PacketTrace{.timestampNs = 4, .length = 100, .uid = 456},
  };

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events, config));

  ASSERT_EQ(events.size(), 2);

  EXPECT_THAT(events[0].timestamp(), 1);
  EXPECT_THAT(events[0].network_packet_bundle().ctx().uid(), 123);
  EXPECT_THAT(events[0].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(200, 100, 300));
  EXPECT_THAT(events[0].network_packet_bundle().packet_timestamps(),
              testing::ElementsAre(1, 0, 3));

  EXPECT_THAT(events[1].timestamp(), 2);
  EXPECT_THAT(events[1].network_packet_bundle().ctx().uid(), 456);
  EXPECT_THAT(events[1].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(400, 100));
  EXPECT_THAT(events[1].network_packet_bundle().packet_timestamps(),
              testing::ElementsAre(0, 2));
}

TEST_F(NetworkTraceHandlerTest, AggregationThreshold) {
  // With an aggregation threshold of 3, the set of packets with uid=123 will
  // be aggregated (3>=3) whereas packets with uid=456 get per-packet info.
  NetworkPacketTraceConfig config;
  config.set_aggregation_threshold(3);

  std::vector<PacketTrace> input = {
      PacketTrace{.timestampNs = 2, .length = 200, .uid = 123},
      PacketTrace{.timestampNs = 1, .length = 100, .uid = 123},
      PacketTrace{.timestampNs = 4, .length = 300, .uid = 123},

      PacketTrace{.timestampNs = 2, .length = 400, .uid = 456},
      PacketTrace{.timestampNs = 4, .length = 100, .uid = 456},
  };

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events, config));

  ASSERT_EQ(events.size(), 2);

  EXPECT_EQ(events[0].timestamp(), 1);
  EXPECT_EQ(events[0].network_packet_bundle().ctx().uid(), 123);
  EXPECT_EQ(events[0].network_packet_bundle().total_duration(), 3);
  EXPECT_EQ(events[0].network_packet_bundle().total_packets(), 3);
  EXPECT_EQ(events[0].network_packet_bundle().total_length(), 600);

  EXPECT_EQ(events[1].timestamp(), 2);
  EXPECT_EQ(events[1].network_packet_bundle().ctx().uid(), 456);
  EXPECT_THAT(events[1].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(400, 100));
  EXPECT_THAT(events[1].network_packet_bundle().packet_timestamps(),
              testing::ElementsAre(0, 2));
}

TEST_F(NetworkTraceHandlerTest, DropLocalPort) {
  NetworkPacketTraceConfig config;
  config.set_drop_local_port(true);
  config.set_aggregation_threshold(10);

  __be16 a = htons(10000);
  __be16 b = htons(10001);
  std::vector<PacketTrace> input = {
      // Recall that local is `src` for egress and `dst` for ingress.
      PacketTrace{.timestampNs = 1, .length = 2, .sport = a, .egress = true},
      PacketTrace{.timestampNs = 2, .length = 4, .dport = a, .egress = false},
      PacketTrace{.timestampNs = 3, .length = 6, .sport = b, .egress = true},
      PacketTrace{.timestampNs = 4, .length = 8, .dport = b, .egress = false},
  };

  // Set common fields.
  for (PacketTrace& pkt : input) {
    pkt.ipProto = IPPROTO_TCP;
  }

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events, config));
  ASSERT_EQ(events.size(), 2);

  // Despite having different local ports, drop and bundle by remaining fields.
  EXPECT_EQ(events[0].network_packet_bundle().ctx().direction(),
            TrafficDirection::DIR_EGRESS);
  EXPECT_THAT(events[0].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(2, 6));

  EXPECT_EQ(events[1].network_packet_bundle().ctx().direction(),
            TrafficDirection::DIR_INGRESS);
  EXPECT_THAT(events[1].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(4, 8));

  // Local port shouldn't be in output.
  EXPECT_FALSE(events[0].network_packet_bundle().ctx().has_local_port());
  EXPECT_FALSE(events[1].network_packet_bundle().ctx().has_local_port());
}

TEST_F(NetworkTraceHandlerTest, DropRemotePort) {
  NetworkPacketTraceConfig config;
  config.set_drop_remote_port(true);
  config.set_aggregation_threshold(10);

  __be16 a = htons(443);
  __be16 b = htons(80);
  std::vector<PacketTrace> input = {
      // Recall that remote is `dst` for egress and `src` for ingress.
      PacketTrace{.timestampNs = 1, .length = 2, .dport = a, .egress = true},
      PacketTrace{.timestampNs = 2, .length = 4, .sport = a, .egress = false},
      PacketTrace{.timestampNs = 3, .length = 6, .dport = b, .egress = true},
      PacketTrace{.timestampNs = 4, .length = 8, .sport = b, .egress = false},
  };

  // Set common fields.
  for (PacketTrace& pkt : input) {
    pkt.ipProto = IPPROTO_TCP;
  }

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events, config));
  ASSERT_EQ(events.size(), 2);

  // Despite having different remote ports, drop and bundle by remaining fields.
  EXPECT_EQ(events[0].network_packet_bundle().ctx().direction(),
            TrafficDirection::DIR_EGRESS);
  EXPECT_THAT(events[0].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(2, 6));

  EXPECT_EQ(events[1].network_packet_bundle().ctx().direction(),
            TrafficDirection::DIR_INGRESS);
  EXPECT_THAT(events[1].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(4, 8));

  // Remote port shouldn't be in output.
  EXPECT_FALSE(events[0].network_packet_bundle().ctx().has_remote_port());
  EXPECT_FALSE(events[1].network_packet_bundle().ctx().has_remote_port());
}

TEST_F(NetworkTraceHandlerTest, DropTcpFlags) {
  NetworkPacketTraceConfig config;
  config.set_drop_tcp_flags(true);
  config.set_aggregation_threshold(10);

  std::vector<PacketTrace> input = {
      PacketTrace{.timestampNs = 1, .length = 1, .uid = 123, .tcpFlags = 1},
      PacketTrace{.timestampNs = 2, .length = 2, .uid = 123, .tcpFlags = 2},
      PacketTrace{.timestampNs = 3, .length = 3, .uid = 456, .tcpFlags = 1},
      PacketTrace{.timestampNs = 4, .length = 4, .uid = 456, .tcpFlags = 2},
  };

  // Set common fields.
  for (PacketTrace& pkt : input) {
    pkt.ipProto = IPPROTO_TCP;
  }

  std::vector<TracePacket> events;
  ASSERT_TRUE(TraceAndSortPackets(input, &events, config));

  ASSERT_EQ(events.size(), 2);

  // Despite having different tcp flags, drop and bundle by remaining fields.
  EXPECT_EQ(events[0].network_packet_bundle().ctx().uid(), 123);
  EXPECT_THAT(events[0].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(1, 2));

  EXPECT_EQ(events[1].network_packet_bundle().ctx().uid(), 456);
  EXPECT_THAT(events[1].network_packet_bundle().packet_lengths(),
              testing::ElementsAre(3, 4));

  // Tcp flags shouldn't be in output.
  EXPECT_FALSE(events[0].network_packet_bundle().ctx().has_tcp_flags());
  EXPECT_FALSE(events[1].network_packet_bundle().ctx().has_tcp_flags());
}

TEST_F(NetworkTraceHandlerTest, Interning) {
  NetworkPacketTraceConfig config;
  config.set_intern_limit(2);

  // The test writes 4 packets coming from three sources (uids). With an intern
  // limit of 2, the first two sources should be interned. This test splits this
  // into individual writes since internally an unordered map is used and would
  // otherwise non-deterministically choose what to intern (this is fine for
  // real use, but not good for test assertions).
  std::vector<std::vector<PacketTrace>> inputs = {
      {PacketTrace{.timestampNs = 1, .uid = 123}},
      {PacketTrace{.timestampNs = 2, .uid = 456}},
      {PacketTrace{.timestampNs = 3, .uid = 789}},
      {PacketTrace{.timestampNs = 4, .uid = 123}},
  };

  auto session = StartTracing(config);

  NetworkTraceHandler::Trace([&](NetworkTraceHandler::TraceContext ctx) {
    ctx.GetDataSourceLocked()->Write(inputs[0], ctx);
    ctx.GetDataSourceLocked()->Write(inputs[1], ctx);
    ctx.GetDataSourceLocked()->Write(inputs[2], ctx);
    ctx.GetDataSourceLocked()->Write(inputs[3], ctx);
    ctx.Flush();
  });

  std::vector<TracePacket> events;
  ASSERT_TRUE(StopTracing(session.get(), &events));

  ASSERT_EQ(events.size(), 4);

  // First time seen, emit new interned data, bundle uses iid instead of ctx.
  EXPECT_EQ(events[0].network_packet_bundle().iid(), 1);
  ASSERT_EQ(events[0].interned_data().packet_context().size(), 1);
  EXPECT_EQ(events[0].interned_data().packet_context(0).iid(), 1);
  EXPECT_EQ(events[0].interned_data().packet_context(0).ctx().uid(), 123);
  EXPECT_EQ(events[0].sequence_flags(),
            TracePacket::SEQ_INCREMENTAL_STATE_CLEARED);

  // First time seen, emit new interned data, bundle uses iid instead of ctx.
  EXPECT_EQ(events[1].network_packet_bundle().iid(), 2);
  ASSERT_EQ(events[1].interned_data().packet_context().size(), 1);
  EXPECT_EQ(events[1].interned_data().packet_context(0).iid(), 2);
  EXPECT_EQ(events[1].interned_data().packet_context(0).ctx().uid(), 456);
  EXPECT_EQ(events[1].sequence_flags(),
            TracePacket::SEQ_NEEDS_INCREMENTAL_STATE);

  // Not enough room in intern table (limit 2), inline the context.
  EXPECT_EQ(events[2].network_packet_bundle().ctx().uid(), 789);
  EXPECT_EQ(events[2].interned_data().packet_context().size(), 0);
  EXPECT_EQ(events[2].sequence_flags(),
            TracePacket::SEQ_NEEDS_INCREMENTAL_STATE);

  // Second time seen, no need to re-emit interned data, only record iid.
  EXPECT_EQ(events[3].network_packet_bundle().iid(), 1);
  EXPECT_EQ(events[3].interned_data().packet_context().size(), 0);
  EXPECT_EQ(events[3].sequence_flags(),
            TracePacket::SEQ_NEEDS_INCREMENTAL_STATE);
}

}  // namespace bpf
}  // namespace android
