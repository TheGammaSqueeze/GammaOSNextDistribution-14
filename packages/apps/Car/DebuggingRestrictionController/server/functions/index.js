'use strict';

const functions = require('firebase-functions');
const jws = require('jsonwebtoken');

function cert_to_x5c(cert) {
  return cert.replace(/-----[^\n]+\n?/gm, '').replace(/\n/g, '');
}

exports.requestAccessToken = functions.https.onCall((data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError('failed-precondition', 'Unauthorized user');
  }

  const payload = {
    nonce: data.nonce,
    deviceId: data.deviceId,
    restrictions: { 'no_debugging_features': false }
  };
  functions.logger.log("Payload: ", payload);
  const config = functions.config().api_config;
  const options = {
    algorithm: 'RS256',
    expiresIn: config.expiration,
    issuer: config.issuer,
    audience: config.audience,
    header: { x5c: config.certificates.map(cert_to_x5c) }
  };
  const token = jws.sign(payload, config.key, options);
  functions.logger.log("Signed Token: ", token);
  return { token: token };
});
