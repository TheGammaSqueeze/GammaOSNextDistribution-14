#! /bin/sh
#
# Creates keystores from privkey.prm and certificate.pem with algorithm variations

make_store() {
    local KEYALG="$1"
    local CERTALG="$2"
    local MACALG="$3"
    local OUTFILE="$4"

    openssl3 pkcs12 -export -out "$OUTFILE" -in certificate.pem \
         -inkey privkey.pem -passout pass:password \
         -macalg "$MACALG" -keypbe "$KEYALG" -certpbe "$CERTALG"
}

make_aes_store() {
    local KEYALG="$1"
    local CERTALG="$2"
    local MACALG="$3"
    local OUTFILE="pbes2-${KEYALG}-${CERTALG}-${MACALG}.p12"

    make_store "${KEYALG}-cbc" "${CERTALG}-cbc" "$MACALG" "$OUTFILE"
}

make_3des_store() {
  local MACALG="$1"

  make_store "pbeWithSHA1And3-KeyTripleDES-CBC" "pbeWithSHA1And3-KeyTripleDES-CBC" \
    "$MACALG" "pbe-3des-${MACALG}.p12"
}

MACALGS="sha1 sha224 sha256 sha384 sha512"
AES_KEYALGS="aes-128 aes-192 aes-256"

for keyalg in $AES_KEYALGS; do
    for certalg in $AES_KEYALGS; do
        for macalg in $MACALGS; do
            make_aes_store "$keyalg" "$certalg" "$macalg"
        done
    done
done

for macalg in $MACALGS; do
    make_3des_store "$macalg"
done
