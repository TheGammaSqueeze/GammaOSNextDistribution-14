/*
 * Copyright 2018 The Android Open Source Project
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

#pragma once

#include <list>
#include <memory>

#include "avrcp_common.h"
#include "packet.h"
namespace bluetooth {

// A helper templated class to access the protected members of Packet to make
// testing easier
template <class PacketType>
class TestPacketType : public PacketType {
 public:
  using PacketType::PacketType;

  static std::shared_ptr<TestPacketType<PacketType>> Make() {
    return std::shared_ptr<TestPacketType<PacketType>>(
        new TestPacketType<PacketType>());
  }

  static std::shared_ptr<TestPacketType<PacketType>> Make(
      std::shared_ptr<Packet> packet) {
    return std::shared_ptr<TestPacketType<PacketType>>(
        new TestPacketType<PacketType>(packet));
  }

  static std::shared_ptr<TestPacketType<PacketType>> Make(
      std::vector<uint8_t> payload) {
    size_t end = payload.size();
    return Make(std::move(payload), 0, end);
  }

  static std::shared_ptr<TestPacketType<PacketType>> Make(
      std::vector<uint8_t> payload, size_t start, size_t end) {
    auto pkt = std::shared_ptr<TestPacketType<PacketType>>(
        new TestPacketType<PacketType>());
    pkt->packet_start_index_ = start;
    pkt->packet_end_index_ = end;
    pkt->data_ = std::make_shared<std::vector<uint8_t>>(std::move(payload));
    return pkt;
  }

  const std::vector<uint8_t>& GetData() { return *PacketType::data_; }

  std::shared_ptr<std::vector<uint8_t>> GetDataPointer() {
    return PacketType::data_;
  }
};

namespace avrcp {

inline std::string to_string(const Attribute& a) {
  switch (a) {
    case Attribute::TITLE:
      return "TITLE";
    case Attribute::ARTIST_NAME:
      return "ARTIST_NAME";
    case Attribute::ALBUM_NAME:
      return "ALBUM_NAME";
    case Attribute::TRACK_NUMBER:
      return "TRACK_NUMBER";
    case Attribute::TOTAL_NUMBER_OF_TRACKS:
      return "TOTAL_NUMBER_OF_TRACKS";
    case Attribute::GENRE:
      return "GENRE";
    case Attribute::PLAYING_TIME:
      return "PLAYING_TIME";
    case Attribute::DEFAULT_COVER_ART:
      return "DEFAULT_COVER_ART";
    default:
      return "UNKNOWN ATTRIBUTE";
  };
}

inline std::string to_string(const AttributeEntry& entry) {
  std::stringstream ss;
  ss << to_string(entry.attribute()) << ": " << entry.value();
  return ss.str();
}

template <class Container>
std::string to_string(const Container& entries) {
  std::stringstream ss;
  for (const auto& el : entries) {
    ss << to_string(el) << std::endl;
  }
  return ss.str();
}

inline bool operator==(const AttributeEntry& a, const AttributeEntry& b) {
  return (a.attribute() == b.attribute()) && (a.value() == b.value());
}

inline bool operator!=(const AttributeEntry& a, const AttributeEntry& b) {
  return !(a == b);
}

template <class AttributesResponseBuilder>
class AttributesResponseBuilderTestUser {
 public:
  using Builder = AttributesResponseBuilder;
  using Maker = std::function<typename Builder::Builder(size_t)>;

 private:
  Maker maker;
  typename Builder::Builder _builder;
  size_t _mtu;
  size_t _current_size = 0;
  size_t _entry_counter = 0;
  std::set<AttributeEntry> _control_set;
  std::list<AttributeEntry> _order_control;
  std::list<AttributeEntry> _sended_order;
  std::stringstream _report;
  bool _test_result = true;
  bool _order_test_result = true;

  void reset() {
    for (const auto& en : _builder->entries_) {
      _sended_order.push_back(en);
    }
    _current_size = 0, _entry_counter = 0;
    _control_set.clear();
    _builder->clear();
  }

  size_t expected_size() { return Builder::kHeaderSize() + _current_size; }

 public:
  std::string getReport() const { return _report.str(); }

  AttributesResponseBuilderTestUser(size_t m_size, Maker maker)
      : maker(maker), _builder(maker(m_size)), _mtu(m_size) {
    _report << __func__ << ": mtu \"" << _mtu << "\"\n";
  }

  void startTest(size_t m_size) {
    _builder = maker(m_size);
    _mtu = m_size;
    reset();
    _report.str("");
    _report.clear();
    _order_control.clear();
    _sended_order.clear();
    _report << __func__ << ": mtu \"" << _mtu << "\"\n";
    _order_test_result = true;
    _test_result = true;
  }

  bool testResult() const { return _test_result; }

  bool testOrder() { return _order_test_result; }

  void finishTest() {
    reset();
    if (_order_control.size() != _sended_order.size()) {
      _report << __func__ << ": testOrder FAIL: "
              << "the count of entries which should send ("
              << _order_control.size() << ") is not equal to sended entries("
              << _sended_order.size() << ")) \n input:\n "
              << to_string(_order_control) << "\n sended:\n"
              << to_string(_sended_order) << "\n";
      _order_test_result = false;
      return;
    }
    auto e = _order_control.begin();
    auto s = _sended_order.begin();
    for (; e != _order_control.end(); ++e, ++s) {
      if (*e != *s) {
        _report << __func__ << "testOrder FAIL: order of entries was changed\n";
        _order_test_result = false;
        break;
      }
    }
    _report << __func__ << ": mtu \"" << _mtu << "\"\n";
  }

  void AddAttributeEntry(AttributeEntry entry) {
    auto f = _builder->AddAttributeEntry(entry);
    if (f != 0) {
      _current_size += f;
      ++_entry_counter;
    }
    if (f == entry.size()) {
      wholeEntry(f, std::move(entry));
    } else {
      fractionEntry(f, std::move(entry));
    }
  }

 private:
  void wholeEntry(size_t f, AttributeEntry&& entry) {
    _control_set.insert(entry);
    _order_control.push_back(entry);
    if (_builder->size() != expected_size()) {
      _report << __func__ << "FAIL for \"" << to_string(entry)
              << "\": not allowed to add.\n";
      _test_result = false;
    }
  }

  void fractionEntry(size_t f, AttributeEntry&& entry) {
    auto l_value = entry.value().size() - (entry.size() - f);
    if (f != 0) {
      auto pushed_entry = AttributeEntry(
          entry.attribute(), std::string(entry.value(), 0, l_value));
      _control_set.insert(pushed_entry);
      _order_control.push_back(pushed_entry);
    }

    if (expected_size() != _builder->size()) {
      _test_result = false;
      _report << __func__ << "FAIL for \"" << to_string(entry)
              << "\": not allowed to add.\n";
    }

    if (_builder->size() != expected_size() ||
        _builder->entries_.size() != _entry_counter) {
      _report << __func__ << "FAIL for \"" << to_string(entry)
              << "\": unexpected size of packet\n";
      _test_result = false;
    }
    for (auto dat = _builder->entries_.begin(), ex = _control_set.begin();
         ex != _control_set.end(); ++dat, ++ex) {
      if (*dat != *ex) {
        _report << __func__ << "FAIL for \"" << to_string(entry)
                << "\": unexpected entry order\n";
        _test_result = false;
      }
    }
    auto tail = (f == 0) ? entry
                         : AttributeEntry(entry.attribute(),
                                          std::string(entry.value(), l_value));
    if (_builder->entries_.size() != 0) {
      reset();
      AddAttributeEntry(tail);
    }
    if (_builder->entries_.size() == 0) {
      _report << __func__ << "FAIL: MTU " << _mtu << " too small\n";
      _test_result = false;
      _order_control.push_back(entry);
      reset();
    }
  }
};

template <class AttributesBuilder>
class FragmentationBuilderHelper {
 public:
  using Builder = AttributesBuilder;
  using Helper = AttributesResponseBuilderTestUser<Builder>;
  using Maker = typename Helper::Maker;

  FragmentationBuilderHelper(size_t mtu, Maker m) : _helper(mtu, m) {}

  template <class TestCollection>
  void runTest(const TestCollection& test_data, size_t mtu,
               bool expect_fragmentation = true, bool expect_ordering = true) {
    _helper.startTest(mtu);

    for (auto& i : test_data) {
      _helper.AddAttributeEntry(i);
    }
    _helper.finishTest();

    EXPECT_EQ(expect_fragmentation, _helper.testResult())
        << "Report: " << _helper.getReport();
    EXPECT_EQ(expect_ordering, _helper.testOrder())
        << "Report: " << _helper.getReport();
  }

 private:
  Helper _helper;
};
}  // namespace avrcp
}  // namespace bluetooth
