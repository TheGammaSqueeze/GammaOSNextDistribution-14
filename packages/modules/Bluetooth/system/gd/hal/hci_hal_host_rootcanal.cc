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

#include "hal/hci_hal_host.h"

#include <netdb.h>
#include <netinet/in.h>
#include <poll.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <chrono>
#include <csignal>
#include <mutex>
#include <queue>

#include "hal/hci_hal.h"
#include "hal/snoop_logger.h"
#include "metrics/counter_metrics.h"
#include "os/log.h"
#include "os/reactor.h"
#include "os/thread.h"

namespace {
constexpr int INVALID_FD = -1;

constexpr uint8_t kH4Command = 0x01;
constexpr uint8_t kH4Acl = 0x02;
constexpr uint8_t kH4Sco = 0x03;
constexpr uint8_t kH4Event = 0x04;
constexpr uint8_t kH4Iso = 0x05;

constexpr uint8_t kH4HeaderSize = 1;
constexpr uint8_t kHciAclHeaderSize = 4;
constexpr uint8_t kHciScoHeaderSize = 3;
constexpr uint8_t kHciEvtHeaderSize = 2;
constexpr uint8_t kHciIsoHeaderSize = 4;
constexpr int kBufSize = 1024 + 4 + 1;  // DeviceProperties::acl_data_packet_size_ + ACL header + H4 header

int ConnectToSocket() {
  auto* config = bluetooth::hal::HciHalHostRootcanalConfig::Get();
  const std::string& server = config->GetServerAddress();
  int port = config->GetPort();

  int socket_fd = socket(AF_INET, SOCK_STREAM, 0);
  if (socket_fd < 1) {
    LOG_ERROR("can't create socket: %s", strerror(errno));
    return INVALID_FD;
  }

  struct hostent* host;
  host = gethostbyname(server.c_str());
  if (host == nullptr) {
    LOG_ERROR("can't get server name");
    return INVALID_FD;
  }

  struct sockaddr_in serv_addr;
  memset((void*)&serv_addr, 0, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  serv_addr.sin_addr.s_addr = INADDR_ANY;
  serv_addr.sin_port = htons(port);

  int result = connect(socket_fd, (struct sockaddr*)&serv_addr, sizeof(serv_addr));
  if (result < 0) {
    LOG_ERROR("can't connect: %s", strerror(errno));
    return INVALID_FD;
  }

  timeval socket_timeout{
      .tv_sec = 3,
      .tv_usec = 0,
  };
  int ret = setsockopt(socket_fd, SOL_SOCKET, SO_RCVTIMEO, &socket_timeout, sizeof(socket_timeout));
  if (ret == -1) {
    LOG_ERROR("can't control socket fd: %s", strerror(errno));
    return INVALID_FD;
  }
  return socket_fd;
}
}  // namespace

namespace bluetooth {
namespace hal {

class HciHalHost : public HciHal {
 public:
  void registerIncomingPacketCallback(HciHalCallbacks* callback) override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    LOG_INFO("%s before", __func__);
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      ASSERT(incoming_packet_callback_ == nullptr && callback != nullptr);
      incoming_packet_callback_ = callback;
    }
    LOG_INFO("%s after", __func__);
  }

  void unregisterIncomingPacketCallback() override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    LOG_INFO("%s before", __func__);
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      incoming_packet_callback_ = nullptr;
    }
    LOG_INFO("%s after", __func__);
  }

  void sendHciCommand(HciPacket command) override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    ASSERT(sock_fd_ != INVALID_FD);
    std::vector<uint8_t> packet = std::move(command);
    btsnoop_logger_->Capture(packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    packet.insert(packet.cbegin(), kH4Command);
    write_to_fd(packet);
  }

  void sendAclData(HciPacket data) override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    ASSERT(sock_fd_ != INVALID_FD);
    std::vector<uint8_t> packet = std::move(data);
    btsnoop_logger_->Capture(packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    packet.insert(packet.cbegin(), kH4Acl);
    write_to_fd(packet);
  }

  void sendScoData(HciPacket data) override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    ASSERT(sock_fd_ != INVALID_FD);
    std::vector<uint8_t> packet = std::move(data);
    btsnoop_logger_->Capture(packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::SCO);
    packet.insert(packet.cbegin(), kH4Sco);
    write_to_fd(packet);
  }

  void sendIsoData(HciPacket data) override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    ASSERT(sock_fd_ != INVALID_FD);
    std::vector<uint8_t> packet = std::move(data);
    btsnoop_logger_->Capture(packet, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ISO);
    packet.insert(packet.cbegin(), kH4Iso);
    write_to_fd(packet);
  }

 protected:
  void ListDependencies(ModuleList* list) const {
    list->add<metrics::CounterMetrics>();
    list->add<SnoopLogger>();
  }

  void Start() override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    ASSERT(sock_fd_ == INVALID_FD);
    sock_fd_ = ConnectToSocket();
    ASSERT(sock_fd_ != INVALID_FD);
    reactable_ = hci_incoming_thread_.GetReactor()->Register(
        sock_fd_,
        common::Bind(&HciHalHost::incoming_packet_received, common::Unretained(this)),
        common::Bind(&HciHalHost::send_packet_ready, common::Unretained(this)));
    hci_incoming_thread_.GetReactor()->ModifyRegistration(reactable_, os::Reactor::REACT_ON_READ_ONLY);
    btsnoop_logger_ = GetDependency<SnoopLogger>();
    LOG_INFO("HAL opened successfully");
  }

  void Stop() override {
    std::lock_guard<std::mutex> lock(api_mutex_);
    LOG_INFO("HAL is closing");
    if (reactable_ != nullptr) {
      hci_incoming_thread_.GetReactor()->Unregister(reactable_);
      LOG_INFO("HAL is stopping, start waiting for last callback");
      // Wait up to 1 second for the last incoming packet callback to finish
      hci_incoming_thread_.GetReactor()->WaitForUnregisteredReactable(std::chrono::milliseconds(1000));
      LOG_INFO("HAL is stopping, finished waiting for last callback");
      ASSERT(sock_fd_ != INVALID_FD);
    }
    reactable_ = nullptr;
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      incoming_packet_callback_ = nullptr;
    }
    ::close(sock_fd_);
    sock_fd_ = INVALID_FD;
    LOG_INFO("HAL is closed");
  }

  std::string ToString() const override {
    return std::string("HciHalHost");
  }

 private:
  // Held when APIs are called, NOT to be held during callbacks
  std::mutex api_mutex_;
  HciHalCallbacks* incoming_packet_callback_ = nullptr;
  std::mutex incoming_packet_callback_mutex_;
  int sock_fd_ = INVALID_FD;
  bluetooth::os::Thread hci_incoming_thread_ =
      bluetooth::os::Thread("hci_incoming_thread", bluetooth::os::Thread::Priority::NORMAL);
  bluetooth::os::Reactor::Reactable* reactable_ = nullptr;
  std::queue<std::vector<uint8_t>> hci_outgoing_queue_;
  SnoopLogger* btsnoop_logger_ = nullptr;

  void write_to_fd(HciPacket packet) {
    // TODO: replace this with new queue when it's ready
    hci_outgoing_queue_.emplace(packet);
    if (hci_outgoing_queue_.size() == 1) {
      hci_incoming_thread_.GetReactor()->ModifyRegistration(reactable_, os::Reactor::REACT_ON_READ_WRITE);
    }
  }

  void send_packet_ready() {
    std::lock_guard<std::mutex> lock(api_mutex_);
    if (hci_outgoing_queue_.empty()) return;
    auto packet_to_send = hci_outgoing_queue_.front();
    auto bytes_written = write(sock_fd_, (void*)packet_to_send.data(), packet_to_send.size());
    hci_outgoing_queue_.pop();
    if (bytes_written == -1) {
      abort();
    }
    if (hci_outgoing_queue_.empty()) {
      hci_incoming_thread_.GetReactor()->ModifyRegistration(reactable_, os::Reactor::REACT_ON_READ_ONLY);
    }
  }

  bool socketRecvAll(void* buffer, int bufferLen) {
    auto buf = static_cast<char*>(buffer);
    while (bufferLen > 0) {
      ssize_t ret;
      RUN_NO_INTR(ret = recv(sock_fd_, buf, bufferLen, 0));
      if (ret <= 0) {
        return false;
      }
      buf += ret;
      bufferLen -= ret;
    }
    return true;
  }

  void incoming_packet_received() {
    {
      std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
      if (incoming_packet_callback_ == nullptr) {
        LOG_INFO("Dropping a packet");
        return;
      }
    }
    uint8_t buf[kBufSize] = {};

    ssize_t received_size;
    RUN_NO_INTR(received_size = recv(sock_fd_, buf, kH4HeaderSize, 0));
    ASSERT_LOG(received_size != -1, "Can't receive from socket: %s", strerror(errno));
    if (received_size == 0) {
      LOG_WARN("Can't read H4 header. EOF received");
      raise(SIGINT);
      return;
    }

    if (buf[0] == kH4Event) {
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize, kHciEvtHeaderSize), "Can't receive from socket: %s", strerror(errno));

      uint8_t hci_evt_parameter_total_length = buf[2];
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize + kHciEvtHeaderSize, hci_evt_parameter_total_length),
          "Can't receive from socket: %s",
          strerror(errno));

      HciPacket receivedHciPacket;
      receivedHciPacket.assign(
          buf + kH4HeaderSize, buf + kH4HeaderSize + kHciEvtHeaderSize + hci_evt_parameter_total_length);
      btsnoop_logger_->Capture(receivedHciPacket, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::EVT);
      {
        std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
        if (incoming_packet_callback_ == nullptr) {
          LOG_INFO("Dropping an event after processing");
          return;
        }
        incoming_packet_callback_->hciEventReceived(receivedHciPacket);
      }
    }

    if (buf[0] == kH4Acl) {
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize, kHciAclHeaderSize), "Can't receive from socket: %s", strerror(errno));

      uint16_t hci_acl_data_total_length = (buf[4] << 8) + buf[3];
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize + kHciAclHeaderSize, hci_acl_data_total_length),
          "Can't receive from socket: %s",
          strerror(errno));

      HciPacket receivedHciPacket;
      receivedHciPacket.assign(
          buf + kH4HeaderSize, buf + kH4HeaderSize + kHciAclHeaderSize + hci_acl_data_total_length);
      btsnoop_logger_->Capture(receivedHciPacket, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
      {
        std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
        if (incoming_packet_callback_ == nullptr) {
          LOG_INFO("Dropping an ACL packet after processing");
          return;
        }
        incoming_packet_callback_->aclDataReceived(receivedHciPacket);
      }
    }

    if (buf[0] == kH4Sco) {
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize, kHciScoHeaderSize), "Can't receive from socket: %s", strerror(errno));

      uint8_t hci_sco_data_total_length = buf[3];
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize + kHciScoHeaderSize, hci_sco_data_total_length),
          "Can't receive from socket: %s",
          strerror(errno));

      HciPacket receivedHciPacket;
      receivedHciPacket.assign(
          buf + kH4HeaderSize, buf + kH4HeaderSize + kHciScoHeaderSize + hci_sco_data_total_length);
      btsnoop_logger_->Capture(receivedHciPacket, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::SCO);
      {
        std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
        if (incoming_packet_callback_ == nullptr) {
          LOG_INFO("Dropping a SCO packet after processing");
          return;
        }
        incoming_packet_callback_->scoDataReceived(receivedHciPacket);
      }
    }

    if (buf[0] == kH4Iso) {
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize, kHciIsoHeaderSize), "Can't receive from socket: %s", strerror(errno));

      uint16_t hci_iso_data_total_length = ((buf[4] & 0x3f) << 8) + buf[3];
      ASSERT_LOG(
          socketRecvAll(buf + kH4HeaderSize + kHciIsoHeaderSize, hci_iso_data_total_length),
          "Can't receive from socket: %s",
          strerror(errno));

      HciPacket receivedHciPacket;
      receivedHciPacket.assign(
          buf + kH4HeaderSize, buf + kH4HeaderSize + kHciIsoHeaderSize + hci_iso_data_total_length);
      btsnoop_logger_->Capture(receivedHciPacket, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ISO);
      {
        std::lock_guard<std::mutex> incoming_packet_callback_lock(incoming_packet_callback_mutex_);
        if (incoming_packet_callback_ == nullptr) {
          LOG_INFO("Dropping a ISO packet after processing");
          return;
        }
        incoming_packet_callback_->isoDataReceived(receivedHciPacket);
      }
    }
    memset(buf, 0, kBufSize);
  }
};

const ModuleFactory HciHal::Factory = ModuleFactory([]() { return new HciHalHost(); });

}  // namespace hal
}  // namespace bluetooth
