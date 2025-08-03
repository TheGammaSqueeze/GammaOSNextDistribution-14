// Copyright 2014 The Chromium OS Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef LIBBRILLO_BRILLO_HTTP_MOCK_TRANSPORT_H_
#define LIBBRILLO_BRILLO_HTTP_MOCK_TRANSPORT_H_

#include <memory>
#include <string>

#include <base/location.h>
#include <base/macros.h>
#include <brillo/http/http_transport.h>
#include <gmock/gmock.h>

namespace brillo {
namespace http {

class MockTransport : public Transport {
 public:
  MockTransport() = default;

  MOCK_METHOD(std::shared_ptr<Connection>,
              CreateConnection,
              (const std::string&,
               const std::string&,
               const HeaderList&,
               const std::string&,
               const std::string&,
               brillo::ErrorPtr*),
              (override));
  MOCK_METHOD(void,
              RunCallbackAsync,
              (const base::Location&, const base::Closure&),
              (override));
  MOCK_METHOD(RequestID,
              StartAsyncTransfer,
              (Connection*, const SuccessCallback&, const ErrorCallback&),
              (override));
  MOCK_METHOD(bool, CancelRequest, (RequestID), (override));
  MOCK_METHOD(void, SetDefaultTimeout, (base::TimeDelta), (override));
  MOCK_METHOD(void, SetLocalIpAddress, (const std::string&), (override));
  MOCK_METHOD(void, UseDefaultCertificate, (), (override));
  MOCK_METHOD(void, UseCustomCertificate, (Certificate), (override));
  MOCK_METHOD(void,
              ResolveHostToIp,
              (const std::string&, uint16_t, const std::string&),
              (override));

 protected:
  MOCK_METHOD(void, ClearHost, (), (override));

 private:
  DISALLOW_COPY_AND_ASSIGN(MockTransport);
};

}  // namespace http
}  // namespace brillo

#endif  // LIBBRILLO_BRILLO_HTTP_MOCK_TRANSPORT_H_
