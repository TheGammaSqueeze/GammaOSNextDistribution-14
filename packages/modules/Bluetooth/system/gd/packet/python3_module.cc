/*
 * Copyright 2019 The Android Open Source Project
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
#include <pybind11/pybind11.h>
#include <pybind11/stl.h>

#include <cstring>
#include <memory>

#include "packet/base_packet_builder.h"
#include "packet/bit_inserter.h"
#include "packet/checksum_type_checker.h"
#include "packet/custom_type_checker.h"
#include "packet/iterator.h"
#include "packet/packet_builder.h"
#include "packet/packet_struct.h"
#include "packet/packet_view.h"
#include "packet/raw_builder.h"

namespace py = pybind11;

namespace bluetooth {

namespace l2cap {
void define_l2cap_packets_submodule(py::module&);
}

namespace packet {

using ::bluetooth::packet::BasePacketBuilder;
using ::bluetooth::packet::BaseStruct;
using ::bluetooth::packet::BitInserter;
using ::bluetooth::packet::CustomTypeChecker;
using ::bluetooth::packet::Iterator;
using ::bluetooth::packet::kLittleEndian;
using ::bluetooth::packet::PacketBuilder;
using ::bluetooth::packet::PacketStruct;
using ::bluetooth::packet::PacketView;
using ::bluetooth::packet::RawBuilder;
using ::bluetooth::packet::parser::ChecksumTypeChecker;

PYBIND11_MODULE(bluetooth_packets_python3, m) {
  py::class_<BasePacketBuilder, std::shared_ptr<BasePacketBuilder>>(m, "BasePacketBuilder");
  py::class_<RawBuilder, BasePacketBuilder, std::shared_ptr<RawBuilder>>(m, "RawBuilder")
      .def(py::init([](std::vector<uint8_t> bytes) { return std::make_unique<RawBuilder>(bytes); }))
      .def(py::init([](std::string bytes) {
        return std::make_unique<RawBuilder>(std::vector<uint8_t>(bytes.begin(), bytes.end()));
      }))
      .def("Serialize", [](RawBuilder& builder) {
        std::vector<uint8_t> packet;
        BitInserter it(packet);
        builder.Serialize(it);
        std::string result = std::string(packet.begin(), packet.end());
        return py::bytes(result);
      });
  py::class_<PacketBuilder<kLittleEndian>, BasePacketBuilder, std::shared_ptr<PacketBuilder<kLittleEndian>>>(
      m, "PacketBuilderLittleEndian");
  py::class_<PacketBuilder<!kLittleEndian>, BasePacketBuilder, std::shared_ptr<PacketBuilder<!kLittleEndian>>>(
      m, "PacketBuilderBigEndian");
  py::class_<BaseStruct, std::shared_ptr<BaseStruct>>(m, "BaseStruct");
  py::class_<PacketStruct<kLittleEndian>, BaseStruct, std::shared_ptr<PacketStruct<kLittleEndian>>>(
      m, "PacketStructLittleEndian");
  py::class_<PacketStruct<!kLittleEndian>, BaseStruct, std::shared_ptr<PacketStruct<!kLittleEndian>>>(
      m, "PacketStructBigEndian");
  py::class_<Iterator<kLittleEndian>>(m, "IteratorLittleEndian");
  py::class_<Iterator<!kLittleEndian>>(m, "IteratorBigEndian");
  py::class_<PacketView<kLittleEndian>>(m, "PacketViewLittleEndian")
      .def(py::init([](std::vector<uint8_t> bytes) {
        // Make a copy
        auto bytes_shared = std::make_shared<std::vector<uint8_t>>(bytes);
        return std::make_unique<PacketView<kLittleEndian>>(bytes_shared);
      }))
      .def("GetBytes", [](const PacketView<kLittleEndian> view) {
        std::string result;
        for (auto byte : view) {
          result += byte;
        }
        return py::bytes(result);
      });
  py::class_<PacketView<!kLittleEndian>>(m, "PacketViewBigEndian").def(py::init([](std::vector<uint8_t> bytes) {
    // Make a copy
    auto bytes_shared = std::make_shared<std::vector<uint8_t>>(bytes);
    return std::make_unique<PacketView<!kLittleEndian>>(bytes_shared);
  }));

  py::module l2cap_m = m.def_submodule("l2cap_packets", "A submodule of l2cap_packets");
  bluetooth::l2cap::define_l2cap_packets_submodule(l2cap_m);
}

}  // namespace packet
}  // namespace bluetooth
