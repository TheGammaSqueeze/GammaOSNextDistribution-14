#/bin/bash

echo "This script generates the key and certificate chain for deploying"
echo "the AAOS Debugging Restriction Controller client and service"
echo
echo "WARNING: Only use this script if you are using a self-signed CA."
echo
echo "Continue (y/N)?"
read c
if [[ "$c" != "y" ]]
then
    exit -1
fi

echo "Enter the path of the CA certificate:"
read ca_cert

echo "Enter path of the CA private key:"
read ca_key

echo
echo "Enter the number of days the token signing key should be valid for:"
echo "  (press return for 365 days)"
read validity

if [[ -z "$validity" ]] ; then
  validity=365
fi
echo "Using '$validity' days"

echo
echo "Enter the hostname that identifies the token signer:"
read hostname

echo
echo "Generating the token signing key and certificate signing request ..."
echo "Please fill in the fields when requested."
date=$(date +%Y-%m-%d)
folder=$(mktemp -d)
req="$folder/token_signing-${date}.req"
key="$folder/token_signing-${date}.key"
signed="$folder/token_signing-${date}.pem"

config="
[ server ]
basicConstraints = critical,CA:false
keyUsage = nonRepudiation, digitalSignature
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer:always
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = $hostname
"

openssl req -nodes -newkey rsa:2048 -sha256 -keyout "${key}" -out "${req}"
echo "Signing the certificate ..."

openssl x509 -req \
    -in "$req" -out "$signed" -CA "$ca_cert" -CAkey "$ca_key" \
    -sha256 -days "$validity" -set_serial 666 \
    -extensions server -extfile <(echo "$config")

key_out="token_signing_key-$date.pem"
cert_chain_out="token_signing_certs-$date.pem"
cat "$key" > "$key_out"
cat "$signed" "$ca_cert" > "$cert_chain_out"


echo "The token signing key and certificate chain have been created."
echo "See $key_out and $cert_chain_out."
echo
echo "Verifying the certificate chain ..."
openssl verify -CAfile "$ca_cert" "$cert_chain_out"
rm -rf "$folder"
