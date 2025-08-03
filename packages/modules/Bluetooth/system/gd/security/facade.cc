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
#include "security/facade.h"

#include "blueberry/facade/security/facade.grpc.pb.h"
#include "grpc/grpc_event_queue.h"
#include "hci/address_with_type.h"
#include "hci/le_address_manager.h"
#include "hci/le_advertising_manager.h"
#include "hci/octets.h"
#include "l2cap/classic/security_policy.h"
#include "l2cap/le/l2cap_le_module.h"
#include "os/handler.h"
#include "security/pairing/oob_data.h"
#include "security/security_manager_listener.h"
#include "security/security_module.h"
#include "security/ui.h"

using bluetooth::l2cap::le::L2capLeModule;

namespace bluetooth {
namespace security {

using namespace blueberry::facade::security;

namespace {
constexpr uint8_t AUTH_REQ_NO_BOND = 0x01;
constexpr uint8_t AUTH_REQ_BOND = 0x01;
constexpr uint8_t AUTH_REQ_MITM_MASK = 0x04;
constexpr uint8_t AUTH_REQ_SECURE_CONNECTIONS_MASK = 0x08;
constexpr uint8_t AUTH_REQ_KEYPRESS_MASK = 0x10;
constexpr uint8_t AUTH_REQ_CT2_MASK = 0x20;
constexpr uint8_t AUTH_REQ_RFU_MASK = 0xC0;

blueberry::facade::BluetoothAddressWithType ToFacadeAddressWithType(hci::AddressWithType address) {
  blueberry::facade::BluetoothAddressWithType ret;

  ret.mutable_address()->set_address(address.GetAddress().ToString());
  ret.set_type(static_cast<blueberry::facade::BluetoothAddressTypeEnum>(address.GetAddressType()));

  return ret;
}

}  // namespace

class SecurityModuleFacadeService : public SecurityModuleFacade::Service,
                                    public ISecurityManagerListener,
                                    public UI,
                                    public hci::AdvertisingCallback {
 public:
  SecurityModuleFacadeService(
      SecurityModule* security_module,
      L2capLeModule* l2cap_le_module,
      ::bluetooth::os::Handler* security_handler,
      hci::LeAdvertisingManager* le_advertising_manager)
      : security_module_(security_module),
        l2cap_le_module_(l2cap_le_module),
        security_handler_(security_handler),
        le_advertising_manager_(le_advertising_manager) {
    security_module_->GetSecurityManager()->RegisterCallbackListener(this, security_handler_);
    security_module_->GetSecurityManager()->SetUserInterfaceHandler(this, security_handler_);

    /* In order to receive connect/disconenct event, we must register service */
    l2cap_le_module_->GetFixedChannelManager()->RegisterService(
        bluetooth::l2cap::kLastFixedChannel - 2,
        common::BindOnce(&SecurityModuleFacadeService::OnL2capRegistrationCompleteLe, common::Unretained(this)),
        common::Bind(&SecurityModuleFacadeService::OnConnectionOpenLe, common::Unretained(this)),
        security_handler_);
  }

  void OnL2capRegistrationCompleteLe(
      l2cap::le::FixedChannelManager::RegistrationResult result,
      std::unique_ptr<l2cap::le::FixedChannelService> /* le_smp_service */) {
    ASSERT_LOG(
        result == bluetooth::l2cap::le::FixedChannelManager::RegistrationResult::SUCCESS,
        "Failed to register to LE SMP Fixed Channel Service");
  }

  void OnConnectionOpenLe(std::unique_ptr<l2cap::le::FixedChannel> channel) {
    channel->RegisterOnCloseCallback(
        security_handler_,
        common::BindOnce(
            &SecurityModuleFacadeService::OnConnectionClosedLe, common::Unretained(this), channel->GetDevice()));
  }

  void OnConnectionClosedLe(hci::AddressWithType address, hci::ErrorCode /* error_code */) {
    SecurityHelperMsg disconnected;
    *disconnected.mutable_peer() = ToFacadeAddressWithType(address);
    disconnected.set_message_type(HelperMsgType::DEVICE_DISCONNECTED);
    helper_events_.OnIncomingEvent(disconnected);
  }

  ::grpc::Status CreateBond(
      ::grpc::ServerContext* /* context */,
      const blueberry::facade::BluetoothAddressWithType* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address(), peer));
    hci::AddressType peer_type = static_cast<hci::AddressType>(request->type());
    security_module_->GetSecurityManager()->CreateBond(hci::AddressWithType(peer, peer_type));
    return ::grpc::Status::OK;
  }

  ::grpc::Status CreateBondOutOfBand(
      ::grpc::ServerContext* /* context */,
      const OobDataBondMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address().address(), peer));
    hci::AddressType peer_type = static_cast<hci::AddressType>(request->address().type());
    pairing::SimplePairingHash c;
    pairing::SimplePairingRandomizer r;
    std::copy(
        std::begin(request->p192_data().confirmation_value()),
        std::end(request->p192_data().confirmation_value()),
        c.data());
    std::copy(std::begin(request->p192_data().random_value()), std::end(request->p192_data().random_value()), r.data());
    pairing::OobData p192_data(c, r);
    std::copy(
        std::begin(request->p256_data().confirmation_value()),
        std::end(request->p256_data().confirmation_value()),
        c.data());
    std::copy(std::begin(request->p256_data().random_value()), std::end(request->p256_data().random_value()), r.data());
    pairing::OobData p256_data(c, r);
    security_module_->GetSecurityManager()->CreateBondOutOfBand(
        hci::AddressWithType(peer, peer_type), p192_data, p256_data);
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetOutOfBandData(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetSecurityManager()->GetOutOfBandData(
        security_handler_->BindOnceOn(this, &SecurityModuleFacadeService::OobDataEventOccurred));
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchGetOutOfBandDataEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<OobDataBondMessage>* writer) override {
    return oob_events_.RunLoop(context, writer);
  }

  ::grpc::Status CreateBondLe(
      ::grpc::ServerContext* /* context */,
      const blueberry::facade::BluetoothAddressWithType* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address(), peer));
    hci::AddressType peer_type = static_cast<hci::AddressType>(request->type());
    security_module_->GetSecurityManager()->CreateBondLe(hci::AddressWithType(peer, peer_type));
    return ::grpc::Status::OK;
  }

  ::grpc::Status CancelBond(
      ::grpc::ServerContext* /* context */,
      const blueberry::facade::BluetoothAddressWithType* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address(), peer));
    hci::AddressType peer_type = hci::AddressType::PUBLIC_DEVICE_ADDRESS;
    security_module_->GetSecurityManager()->CancelBond(hci::AddressWithType(peer, peer_type));
    return ::grpc::Status::OK;
  }

  ::grpc::Status RemoveBond(
      ::grpc::ServerContext* /* context */,
      const blueberry::facade::BluetoothAddressWithType* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address(), peer));
    hci::AddressType peer_type = hci::AddressType::PUBLIC_DEVICE_ADDRESS;
    security_module_->GetSecurityManager()->RemoveBond(hci::AddressWithType(peer, peer_type));
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchUiEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<UiMsg>* writer) override {
    return ui_events_.RunLoop(context, writer);
  }

  ::grpc::Status SendUiCallback(
      ::grpc::ServerContext* /* context */,
      const UiCallbackMsg* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address().address(), peer));
    hci::AddressType remote_type = static_cast<hci::AddressType>(request->address().type());

    switch (request->message_type()) {
      case UiCallbackType::PASSKEY:
        security_module_->GetSecurityManager()->OnPasskeyEntry(
            hci::AddressWithType(peer, remote_type), request->numeric_value());
        break;
      case UiCallbackType::YES_NO:
        security_module_->GetSecurityManager()->OnConfirmYesNo(hci::AddressWithType(peer, remote_type),
                                                               request->boolean());
        break;
      case UiCallbackType::PAIRING_PROMPT:
        security_module_->GetSecurityManager()->OnPairingPromptAccepted(
            hci::AddressWithType(peer, remote_type), request->boolean());
        break;
      case UiCallbackType::PIN:
        LOG_INFO("PIN Callback");
        security_module_->GetSecurityManager()->OnPinEntry(
            hci::AddressWithType(peer, remote_type),
            std::vector<uint8_t>(request->pin().cbegin(), request->pin().cend()));
        break;
      default:
        LOG_ERROR("Unknown UiCallbackType %d", static_cast<int>(request->message_type()));
        return ::grpc::Status(::grpc::StatusCode::INVALID_ARGUMENT, "Unknown UiCallbackType");
    }
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchBondEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<BondMsg>* writer) override {
    return bond_events_.RunLoop(context, writer);
  }

  ::grpc::Status FetchHelperEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<SecurityHelperMsg>* writer) override {
    return helper_events_.RunLoop(context, writer);
  }

  ::grpc::Status FetchAdvertisingCallbackEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<AdvertisingCallbackMsg>* writer) override {
    le_advertising_manager_->RegisterAdvertisingCallback(this);
    return advertising_callback_events_.RunLoop(context, writer);
  }

  void OnAdvertisingSetStarted(
      int /* reg_id */,
      uint8_t advertiser_id,
      int8_t /* tx_power */,
      AdvertisingStatus /* status */) {
    AdvertisingCallbackMsg advertising_set_started;
    advertising_set_started.set_message_type(AdvertisingCallbackMsgType::ADVERTISING_SET_STARTED);
    advertising_set_started.set_advertising_started(AdvertisingSetStarted::STARTED);
    advertising_set_started.set_advertiser_id(advertiser_id);
    advertising_callback_events_.OnIncomingEvent(advertising_set_started);
  }

  void OnAdvertisingEnabled(uint8_t /* advertiser_id */, bool /* enable */, uint8_t /* status */) {
    // Not used yet
  }

  void OnAdvertisingDataSet(uint8_t /* advertiser_id */, uint8_t /* status */) {
    // Not used yet
  }

  void OnScanResponseDataSet(uint8_t /* advertiser_id */, uint8_t /* status */) {
    // Not used yet
  }

  void OnAdvertisingParametersUpdated(
      uint8_t /* advertiser_id */, int8_t /* tx_power */, uint8_t /* status */) {
    // Not used yet
  }

  void OnPeriodicAdvertisingParametersUpdated(uint8_t /* advertiser_id */, uint8_t /* status */) {
    // Not used yet
  }

  void OnPeriodicAdvertisingDataSet(uint8_t /* advertiser_id */, uint8_t /* status */) {
    // Not used yet
  }

  void OnPeriodicAdvertisingEnabled(
      uint8_t /* advertiser_id */, bool /* enable */, uint8_t /* status */) {
    // Not used yet
  }

  void OnOwnAddressRead(uint8_t /* advertiser_id */, uint8_t /* address_type */, Address address) {
    AdvertisingCallbackMsg get_own_address;
    get_own_address.set_message_type(AdvertisingCallbackMsgType::OWN_ADDRESS_READ);
    get_own_address.mutable_address()->set_address(address.ToString());
    advertising_callback_events_.OnIncomingEvent(get_own_address);
  }

  ::grpc::Status SetIoCapability(
      ::grpc::ServerContext* /* context */,
      const IoCapabilityMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetFacadeConfigurationApi()->SetIoCapability(
        static_cast<hci::IoCapability>(request->capability()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetLeIoCapability(
      ::grpc::ServerContext* /* context */,
      const LeIoCapabilityMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetFacadeConfigurationApi()->SetLeIoCapability(
        static_cast<security::IoCapability>(request->capabilities()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetAuthenticationRequirements(
      ::grpc::ServerContext* /* context */,
      const AuthenticationRequirementsMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetFacadeConfigurationApi()->SetAuthenticationRequirements(
        static_cast<hci::AuthenticationRequirements>(request->requirement()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetLeAuthRequirements(
      ::grpc::ServerContext* /* context */,
      const LeAuthRequirementsMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    uint8_t auth_req = request->bond() ? AUTH_REQ_BOND : AUTH_REQ_NO_BOND;

    if (request->mitm()) auth_req |= AUTH_REQ_MITM_MASK;
    if (request->secure_connections()) auth_req |= AUTH_REQ_SECURE_CONNECTIONS_MASK;
    if (request->keypress()) auth_req |= AUTH_REQ_KEYPRESS_MASK;
    if (request->ct2()) auth_req |= AUTH_REQ_CT2_MASK;
    if (request->reserved_bits()) auth_req |= (((request->reserved_bits()) << 6) & AUTH_REQ_RFU_MASK);

    security_module_->GetFacadeConfigurationApi()->SetLeAuthRequirements(auth_req);
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetLeMaximumEncryptionKeySize(
      ::grpc::ServerContext* /* context */,
      const LeMaximumEncryptionKeySizeMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetFacadeConfigurationApi()->SetLeMaximumEncryptionKeySize(
        request->maximum_encryption_key_size());
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetLeOobDataPresent(
      ::grpc::ServerContext* /* context */,
      const LeOobDataPresentMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    security_module_->GetFacadeConfigurationApi()->SetLeOobDataPresent(
        static_cast<OobDataFlag>(request->data_present()));
    return ::grpc::Status::OK;
  }

  ::grpc::Status SetLeInitiatorAddressPolicy(
      ::grpc::ServerContext* /* context */,
      const blueberry::facade::hci::PrivacyPolicy* request,
      ::google::protobuf::Empty* /* response */) override {
    Address address = Address::kEmpty;
    hci::LeAddressManager::AddressPolicy address_policy =
        static_cast<hci::LeAddressManager::AddressPolicy>(request->address_policy());
    if (address_policy == hci::LeAddressManager::AddressPolicy::USE_STATIC_ADDRESS ||
        address_policy == hci::LeAddressManager::AddressPolicy::USE_PUBLIC_ADDRESS) {
      ASSERT(Address::FromString(request->address_with_type().address().address(), address));
    }
    hci::AddressWithType address_with_type(address, static_cast<hci::AddressType>(request->address_with_type().type()));
    hci::Octet16 irk = {};
    auto request_irk_length = request->rotation_irk().end() - request->rotation_irk().begin();
    if (request_irk_length == hci::kOctet16Length) {
      std::vector<uint8_t> irk_data(request->rotation_irk().begin(), request->rotation_irk().end());
      std::copy_n(irk_data.begin(), hci::kOctet16Length, irk.begin());
    } else {
      ASSERT(request_irk_length == 0);
    }
    auto minimum_rotation_time = std::chrono::milliseconds(request->minimum_rotation_time());
    auto maximum_rotation_time = std::chrono::milliseconds(request->maximum_rotation_time());
    security_module_->GetSecurityManager()->SetLeInitiatorAddressPolicyForTest(
        address_policy, address_with_type, irk, minimum_rotation_time, maximum_rotation_time);
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchEnforceSecurityPolicyEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<EnforceSecurityPolicyMsg>* writer) override {
    return enforce_security_policy_events_.RunLoop(context, writer);
  }

  ::grpc::Status EnforceSecurityPolicy(
      ::grpc::ServerContext* /* context */,
      const SecurityPolicyMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address().address(), peer));
    hci::AddressType peer_type = static_cast<hci::AddressType>(request->address().type());
    hci::AddressWithType peer_with_type(peer, peer_type);
    l2cap::classic::SecurityEnforcementInterface::ResultCallback callback =
        security_handler_->BindOnceOn(this, &SecurityModuleFacadeService::EnforceSecurityPolicyEvent);
    security_module_->GetFacadeConfigurationApi()->EnforceSecurityPolicy(
        peer_with_type, static_cast<l2cap::classic::SecurityPolicy>(request->policy()), std::move(callback));
    return ::grpc::Status::OK;
  }

  ::grpc::Status GetLeOutOfBandData(
      ::grpc::ServerContext* /* context */,
      const ::google::protobuf::Empty* /* request */,
      OobDataMessage* response) override {
    std::array<uint8_t, 16> le_sc_c;
    std::array<uint8_t, 16> le_sc_r;
    security_module_->GetFacadeConfigurationApi()->GetLeOutOfBandData(&le_sc_c, &le_sc_r);

    std::string le_sc_c_str(17, '\0');
    std::copy(le_sc_c.begin(), le_sc_c.end(), le_sc_c_str.data());
    response->set_confirmation_value(le_sc_c_str);

    std::string le_sc_r_str(17, '\0');
    std::copy(le_sc_r.begin(), le_sc_r.end(), le_sc_r_str.data());
    response->set_random_value(le_sc_r_str);

    return ::grpc::Status::OK;
  }

  ::grpc::Status SetOutOfBandData(
      ::grpc::ServerContext* /* context */,
      const OobDataMessage* request,
      ::google::protobuf::Empty* /* response */) override {
    hci::Address peer;
    ASSERT(hci::Address::FromString(request->address().address().address(), peer));
    hci::AddressType peer_type = static_cast<hci::AddressType>(request->address().type());
    hci::AddressWithType peer_with_type(peer, peer_type);

    // We can't simply iterate till end of string, because we have an empty byte added at the end. We know confirm and
    // random are fixed size, 16 bytes
    std::array<uint8_t, 16> le_sc_c;
    auto req_le_sc_c = request->confirmation_value();
    std::copy(req_le_sc_c.begin(), req_le_sc_c.begin() + 16, le_sc_c.data());

    std::array<uint8_t, 16> le_sc_r;
    auto req_le_sc_r = request->random_value();
    std::copy(req_le_sc_r.begin(), req_le_sc_r.begin() + 16, le_sc_r.data());

    security_module_->GetFacadeConfigurationApi()->SetOutOfBandData(peer_with_type, le_sc_c, le_sc_r);
    return ::grpc::Status::OK;
  }

  ::grpc::Status FetchDisconnectEvents(
      ::grpc::ServerContext* context,
      const ::google::protobuf::Empty* /* request */,
      ::grpc::ServerWriter<DisconnectMsg>* writer) override {
    security_module_->GetFacadeConfigurationApi()->SetDisconnectCallback(
        common::Bind(&SecurityModuleFacadeService::DisconnectEventOccurred, common::Unretained(this)));
    return disconnect_events_.RunLoop(context, writer);
  }

  void OobDataEventOccurred(bluetooth::hci::CommandCompleteView packet) {
    LOG_INFO("Got OOB Data event");
    ASSERT(packet.IsValid());
    auto cc = bluetooth::hci::ReadLocalOobDataCompleteView::Create(packet);
    ASSERT(cc.IsValid());
    OobDataBondMessage msg;
    OobDataMessage p192;
    // Just need this to satisfy the proto message
    bluetooth::hci::AddressWithType peer;
    *p192.mutable_address() = ToFacadeAddressWithType(peer);

    auto c = cc.GetC();
    p192.set_confirmation_value(c.data(), c.size());

    auto r = cc.GetR();
    p192.set_random_value(r.data(), r.size());

    // Only the Extended version returns 256 also.
    // The API has a parameter for both, so we set it
    // empty and the module and test suite will ignore it.
    OobDataMessage p256;
    *p256.mutable_address() = ToFacadeAddressWithType(peer);

    std::array<uint8_t, 16> empty_val;
    p256.set_confirmation_value(empty_val.data(), empty_val.size());
    p256.set_random_value(empty_val.data(), empty_val.size());

    *msg.mutable_address() = ToFacadeAddressWithType(peer);
    *msg.mutable_p192_data() = p192;
    *msg.mutable_p256_data() = p256;
    oob_events_.OnIncomingEvent(msg);
  }

  void DisconnectEventOccurred(bluetooth::hci::AddressWithType peer) {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    DisconnectMsg msg;
    *msg.mutable_address() = ToFacadeAddressWithType(peer);
    disconnect_events_.OnIncomingEvent(msg);
  }

  void DisplayPairingPrompt(const bluetooth::hci::AddressWithType& peer, std::string /* name */) {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    UiMsg display_yes_no;
    *display_yes_no.mutable_peer() = ToFacadeAddressWithType(peer);
    display_yes_no.set_message_type(UiMsgType::DISPLAY_PAIRING_PROMPT);
    display_yes_no.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_yes_no);
  }

  virtual void DisplayConfirmValue(ConfirmationData data) {
    const bluetooth::hci::AddressWithType& peer = data.GetAddressWithType();
    std::string name = data.GetName();
    uint32_t numeric_value = data.GetNumericValue();
    LOG_INFO("%s value = 0x%x", ADDRESS_TO_LOGGABLE_CSTR(peer), numeric_value);
    UiMsg display_with_value;
    *display_with_value.mutable_peer() = ToFacadeAddressWithType(peer);
    display_with_value.set_message_type(UiMsgType::DISPLAY_YES_NO_WITH_VALUE);
    display_with_value.set_numeric_value(numeric_value);
    display_with_value.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_with_value);
  }

  void DisplayYesNoDialog(ConfirmationData data) override {
    const bluetooth::hci::AddressWithType& peer = data.GetAddressWithType();
    std::string name = data.GetName();
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    UiMsg display_yes_no;
    *display_yes_no.mutable_peer() = ToFacadeAddressWithType(peer);
    display_yes_no.set_message_type(UiMsgType::DISPLAY_YES_NO);
    display_yes_no.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_yes_no);
  }

  void DisplayPasskey(ConfirmationData data) override {
    const bluetooth::hci::AddressWithType& peer = data.GetAddressWithType();
    std::string name = data.GetName();
    uint32_t passkey = data.GetNumericValue();
    LOG_INFO("%s value = 0x%x", ADDRESS_TO_LOGGABLE_CSTR(peer), passkey);
    UiMsg display_passkey;
    *display_passkey.mutable_peer() = ToFacadeAddressWithType(peer);
    display_passkey.set_message_type(UiMsgType::DISPLAY_PASSKEY);
    display_passkey.set_numeric_value(passkey);
    display_passkey.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_passkey);
  }

  void DisplayEnterPasskeyDialog(ConfirmationData data) override {
    const bluetooth::hci::AddressWithType& peer = data.GetAddressWithType();
    std::string name = data.GetName();
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    UiMsg display_passkey_input;
    *display_passkey_input.mutable_peer() = ToFacadeAddressWithType(peer);
    display_passkey_input.set_message_type(UiMsgType::DISPLAY_PASSKEY_ENTRY);
    display_passkey_input.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_passkey_input);
  }

  void DisplayEnterPinDialog(ConfirmationData data) override {
    const bluetooth::hci::AddressWithType& peer = data.GetAddressWithType();
    std::string name = data.GetName();
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    UiMsg display_pin_input;
    *display_pin_input.mutable_peer() = ToFacadeAddressWithType(peer);
    display_pin_input.set_message_type(UiMsgType::DISPLAY_PIN_ENTRY);
    display_pin_input.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_pin_input);
  }

  void Cancel(const bluetooth::hci::AddressWithType& peer) override {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    UiMsg display_cancel;
    *display_cancel.mutable_peer() = ToFacadeAddressWithType(peer);
    display_cancel.set_message_type(UiMsgType::DISPLAY_CANCEL);
    display_cancel.set_unique_id(unique_id++);
    ui_events_.OnIncomingEvent(display_cancel);
  }

  void OnDeviceBonded(hci::AddressWithType peer) override {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    BondMsg bonded;
    *bonded.mutable_peer() = ToFacadeAddressWithType(peer);
    bonded.set_message_type(BondMsgType::DEVICE_BONDED);
    bond_events_.OnIncomingEvent(bonded);
  }

  void OnEncryptionStateChanged(hci::EncryptionChangeView /* encryption_change_view */) override {}

  void OnDeviceUnbonded(hci::AddressWithType peer) override {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    BondMsg unbonded;
    *unbonded.mutable_peer() = ToFacadeAddressWithType(peer);
    unbonded.set_message_type(BondMsgType::DEVICE_UNBONDED);
    bond_events_.OnIncomingEvent(unbonded);
  }

  void OnDeviceBondFailed(hci::AddressWithType peer, PairingFailure status) override {
    LOG_INFO("%s", ADDRESS_TO_LOGGABLE_CSTR(peer));
    BondMsg bond_failed;
    *bond_failed.mutable_peer() = ToFacadeAddressWithType(peer);
    bond_failed.set_message_type(BondMsgType::DEVICE_BOND_FAILED);
    bond_failed.set_reason(static_cast<uint8_t>(status.reason));
    bond_events_.OnIncomingEvent(bond_failed);
  }

  void EnforceSecurityPolicyEvent(bool result) {
    EnforceSecurityPolicyMsg msg;
    msg.set_result(result);
    enforce_security_policy_events_.OnIncomingEvent(msg);
  }

 private:
  SecurityModule* security_module_;
  L2capLeModule* l2cap_le_module_;
  ::bluetooth::os::Handler* security_handler_;
  hci::LeAdvertisingManager* le_advertising_manager_;
  ::bluetooth::grpc::GrpcEventQueue<UiMsg> ui_events_{"UI events"};
  ::bluetooth::grpc::GrpcEventQueue<BondMsg> bond_events_{"Bond events"};
  ::bluetooth::grpc::GrpcEventQueue<SecurityHelperMsg> helper_events_{"Events that don't fit any other category"};
  ::bluetooth::grpc::GrpcEventQueue<EnforceSecurityPolicyMsg> enforce_security_policy_events_{
      "Enforce Security Policy Events"};
  ::bluetooth::grpc::GrpcEventQueue<DisconnectMsg> disconnect_events_{"Disconnect events"};
  ::bluetooth::grpc::GrpcEventQueue<OobDataBondMessage> oob_events_{"OOB Data events"};
  ::bluetooth::grpc::GrpcEventQueue<AdvertisingCallbackMsg> advertising_callback_events_{"Advertising callback events"};
  uint32_t unique_id{1};
  std::map<uint32_t, common::OnceCallback<void(bool)>> user_yes_no_callbacks_;
  std::map<uint32_t, common::OnceCallback<void(uint32_t)>> user_passkey_callbacks_;
};

void SecurityModuleFacadeModule::ListDependencies(ModuleList* list) const {
  ::bluetooth::grpc::GrpcFacadeModule::ListDependencies(list);
  list->add<SecurityModule>();
  list->add<L2capLeModule>();
  list->add<hci::LeAdvertisingManager>();
}

void SecurityModuleFacadeModule::Start() {
  ::bluetooth::grpc::GrpcFacadeModule::Start();
  service_ = new SecurityModuleFacadeService(
      GetDependency<SecurityModule>(),
      GetDependency<L2capLeModule>(),
      GetHandler(),
      GetDependency<hci::LeAdvertisingManager>());
}

void SecurityModuleFacadeModule::Stop() {
  delete service_;
  ::bluetooth::grpc::GrpcFacadeModule::Stop();
}

::grpc::Service* SecurityModuleFacadeModule::GetService() const {
  return service_;
}

const ModuleFactory SecurityModuleFacadeModule::Factory =
    ::bluetooth::ModuleFactory([]() { return new SecurityModuleFacadeModule(); });

}  // namespace security
}  // namespace bluetooth
