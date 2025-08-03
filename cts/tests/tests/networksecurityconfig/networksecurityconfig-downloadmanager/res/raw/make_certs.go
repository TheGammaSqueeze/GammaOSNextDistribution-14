package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"time"
)

func mustDecodePEMKey(in string) ([]byte, *rsa.PrivateKey) {
	block, _ := pem.Decode([]byte(in))
	if block == nil || block.Type != "PRIVATE KEY" {
		panic("could not find PEM block of type PRIVATE KEY")
	}
	key, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		panic(fmt.Sprintf("error decoding private key: %s", err))
	}
	return block.Bytes, key.(*rsa.PrivateKey)
}

func mustWriteFile(name string, data []byte) {
	if err := os.WriteFile(name, data, 0666); err != nil {
		panic(err)
	}
}

func mustWritePEMBlocks(name string, blocks ...*pem.Block) {
	var data []byte
	for _, b := range blocks {
		data = append(data, pem.EncodeToMemory(b)...)
	}
	mustWriteFile(name, data)
}

func mustMakeCertChain(caName string, eeKey, caKey *rsa.PrivateKey) (eeCert, caCert *x509.Certificate) {
	notBefore, err := time.Parse(time.DateOnly, "2015-01-01")
	if err != nil {
		panic(err)
	}
	notAfter, err := time.Parse(time.DateOnly, "3000-01-01")
	if err != nil {
		panic(err)
	}

	caTemplate := x509.Certificate{
		SerialNumber:          new(big.Int).SetUint64(0),
		Subject:               pkix.Name{CommonName: caName},
		NotBefore:             notBefore,
		NotAfter:              notAfter,
		BasicConstraintsValid: true,
		IsCA:                  true,
		SignatureAlgorithm:    x509.SHA256WithRSA,
	}
	caBytes, err := x509.CreateCertificate(rand.Reader, &caTemplate, &caTemplate, caKey.Public(), caKey)
	if err != nil {
		panic(err)
	}
	caCert, err = x509.ParseCertificate(caBytes)
	if err != nil {
		panic(err)
	}

	eeTemplate := x509.Certificate{
		SerialNumber:          new(big.Int).SetUint64(1),
		Subject:               pkix.Name{CommonName: "localhost"},
		NotBefore:             notBefore,
		NotAfter:              notAfter,
		DNSNames:              []string{"localhost"},
		BasicConstraintsValid: true,
		SignatureAlgorithm:    x509.SHA256WithRSA,
	}
	eeBytes, err := x509.CreateCertificate(rand.Reader, &eeTemplate, caCert, eeKey.Public(), caKey)
	if err != nil {
		panic(err)
	}
	eeCert, err = x509.ParseCertificate(eeBytes)
	if err != nil {
		panic(err)
	}

	return
}

func main() {
	trustedCAKeyDER, trustedCAKey := mustDecodePEMKey(trustedCAKeyPEM)
	_, untrustedCAKey := mustDecodePEMKey(untrustedCAKeyPEM)
	endEntityKeyDER, endEntityKey := mustDecodePEMKey(endEntityKeyPEM)

	eeTrusted, caTrusted := mustMakeCertChain("Android CTS trusted CA", endEntityKey, trustedCAKey)
	eeUntrusted, caUntrusted := mustMakeCertChain("Android CTS untrusted CA", endEntityKey, untrustedCAKey)

	mustWriteFile("test_key.pkcs8", endEntityKeyDER)
	mustWritePEMBlocks("valid_chain.pem", &pem.Block{Type: "CERTIFICATE", Bytes: eeTrusted.Raw}, &pem.Block{Type: "CERTIFICATE", Bytes: caTrusted.Raw})
	mustWritePEMBlocks("invalid_chain.pem", &pem.Block{Type: "CERTIFICATE", Bytes: eeUntrusted.Raw}, &pem.Block{Type: "CERTIFICATE", Bytes: caUntrusted.Raw})
	mustWritePEMBlocks("valid_ca.pem", &pem.Block{Type: "CERTIFICATE", Bytes: caTrusted.Raw}, &pem.Block{Type: "PRIVATE KEY", Bytes: trustedCAKeyDER})
}

const (
	endEntityKeyPEM = `
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCh/iHZ86A3hmhN
Ulp+C/HYX3efQCVWDlsc/PJ7SueYjwsTxnTXai+TF9Dp/StIgX/nvN/ZkTCHY2NQ
bRKOS5/TAOkUy0MLejj2lhfx0EB5JqyzjkoTWXHq3mvqyRz3zezXnUoOlH29m3FZ
+w6ygcFIoACQ1lZ9NiAesBzvbJRfjbAHKcnnMbb2P6e7dJYLgHtI9SIlX1/sRnTR
VO1o5hkUM5OSmrxYtwcA3Nua1z+ySf+jOuk58yOeIkSPdfD9AG4RPtyv+QW3vKjx
bXZRDgoT+p6VFtTYZs/fSqJZHFv7gl1ARwLJtu0D3Vt8GF+ocGuXkvFRk1roRDnX
T3Qbxhj3AgMBAAECggEAUWzaOyG4kPBgkS0qC12ZrPcfu86UddM1fTav88VM8GRG
XyRMKtNXIorAGj2FeiaN0kAgDM5sJEGS9CQ/RYNBzOASSL967dP7ugbr9kKFheAo
wwBsv5kgJ7yxZgy9INpDiIWpafa91YbCzUXit1t9ejHi1urGAp4oOwSvJrHfSr+y
0tBlT1FeNcMd/11me/lmsanzdli0v/xzkzinRmzesI0d4Z+2XTxBFtlos/vrMMAV
O5Rlg1VVKnpF6wYf+/he5/q8y2TKr61J2FSFX66VoGvHYzegFCO8Shk2ojYXbcJb
mvcAHqLnllkGQ5TeqMIOldlewlbbwoIsKtrM981LcQKBgQDOnJvfQJdz4SwjQfyR
rXqrYnjr70OL5CFMQFcWcLg0NUpCsKlWj+HxeOJ5TMm4WdDCpoqJNGb2dpIH0F8c
Hvmat3lztMuiTSpHQ4mHdo8v5hBr3m79TNmPVOVbSpXJ6ZzTpgzoIO69NZypSjvr
CyDNIpm7kSfsAfIIrbkeWjc32wKBgQDItxtqDfxeqVNWM2h10Q2r4VHxaweNZsYe
oyS1JAIGRqir5L5nIRvYsHpVRPyb/xA7wwd/Z21t89ulcitPA8rHOY3q3llnbDA8
DczS3CSyHlyHrSq3DDK+tzVavOwM102dZK2vX1n1M2n04f7BgYfzMfhhqU+chZHZ
RwBp6PTMFQKBgQC/eRVe9V4mLtQXrKxjWRDoyW5kwCyp9PYC01Gh4z5ia8xxN4UH
SnpKrQu8/DOpG9tCuNKESsLsv3+frDSoO/B3uHbeivt4Yui+eFF475/M9Pnc7ZuQ
NOseUL/pNZrRLyV+Y1FswjBdutR/cuNKdNqmIN21nNURe9AQyOZYwaSQpwKBgAEw
/QurG5VgQetgEL2qSP99LeRV80yGkKR76Wrd1Nk+sB+dEvg/vQ+TNJau+yTd5bXF
GZVLAt5FNUeNHu6wIhL+p3X+bd9sR/kDR/T0c49PGNOBVqCRhPeW1M0+E9Sbro1B
nLpOwZTwAi/+62kwoKLz5/qEa8GNFWmJv1nhlRplAoGAPpMGm9GP8gB+XMOGQFLE
kBOoYD3xhFKH9HH83vY878dfDJKdHEBJurUiEsv0/1bs1czrWtFuWGoyOjuvPk7s
E6CB0HYGPjBw+naDFXgQw69OcucysDS6r3YrkTGFfnu1s20nrRcmP6Dk4c6jCp+i
9gweslZFc4sWLBohJr1/DxQ=
-----END PRIVATE KEY-----
`

	trustedCAKeyPEM = `
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDK7USDnAqJ7Kuj
uotcjH6pqpsTyY+pEvzWLSa1AGOereCxdXd/uiRSyyD/xBHNiJgY/+yGXLvQw6op
RE/+CXs61SPxJcvBAXNnoH2GdZNcZGcFjgkVyBLLoskzCj5k+DBW1uN/UMBY04tG
Gpgn1TGvQ9fkpnkBp8VWHRj4lWZoV8Y/En9QGLzcZDtOgoG8dMU3lJCODlC2VbkN
oQ1xs7uXMH864qciUs/paURXAJL5xOhkDNpNp85VlpRqtYeK3SSr7zHLGX6SjGAt
wIqWQNilkJ7NjdQDEaF5GeZK5qHosr0FHHZxIw4qR43ewChUHoBGLsXfQxiPtEfZ
dHMu6ZctAgMBAAECggEAezX1E7P68iOxU4hAdcEYZwwffLQ1dgMBYUmo5t2FnyMT
+qvIEtWCmIKdVq5F4PW+4+8APdSwdOFYwBWqPCSlneMsH49DV7z5xUG89ZcOElsj
8kt7WK5SOzJr14GwwL2xHAj9uJ/fKg/H0Jj1KbpYoIIg48PwVQD44IBqWQTdWRxd
QVbxczDIHAjXSD14P4uUAXQrFyYEQXgksu4FNNGFr6JnuNe6eSreKxrw8/7J9OXZ
7VUfN0Iuw/M4HF1dKQKVK2R0W34wuS2KyI3fKUS7RoSrfXfBuZ1hQ1gWoATiXkbR
AAMUSWuaj5RQ4lj0wxdRAO+e4QB2yUXHgzCr8pH6QQKBgQDuiXtcdZ2FVN9ezxJt
XDd6225Rvh8XtWEUwTaJmOtZz2AKlKTQr06u/BqqpKWc5SWQSf88K7WPxF6EMizB
4D3wVGzCFkeRMMriZmrRe+8IVCq+mAZnRahV4SSH35ZQoNd8/3Mv6o59/UR0x7Nl
5yTqruROK0Ycz8S0GlvfKiDyywKBgQDZyGaIYqZ63piagmRx3EB1Z+8yfXnn8g2d
iVYU3UTDWxAFtzq6cfPRUdDxGHgAjmVmLvSGEaxqYNOftxwC3zk1E03w4/KvXg+y
Vt+1qPZ7Hj1OcGMYA+1/Qy6+GMneYnUkmO9zHoNzSDG5hfNkQ+3SyMx53FfTO8oA
Lrpl4gFG5wKBgQCtCGXIKDlf4rU13RgM5HwKTuqzuSps1FHb8FxTa+4tc9TDWBhG
mSSGorHlXxITwdWB2WughkRqSZQWaR82dCf6EgPitq6rj61cldaepzw52nQ3Vagv
ecQmp+8L8RDk5Afs0JEKDSfYFMR3wfVM0mNhKgTK/3EYrU6PJx/FvpWwCQKBgDrk
ICXdV1t+ehG+FN9dSej1tA8ZMy/vmpLxIl/9/aw+IbUJ+U2VpvMBhtjLXxf3aaAa
LnFash8KE+/qmh6EsnmRwM/VNDkL3H7DUzdSe2SLptRhO8qwtTZmumsZVO1X/olo
+cdNhwpTiW67tDd2zwbi2bhSR0WNs3AdMrZ+SQ4dAoGBANkjgWwzVN8KGOe9GdEo
opcwVzC1l9xkUcB6ykIG+DKw5p1ChGLA+2uufdpNWfPqXixCt5X3qIOy1q/VIdlj
EHNurGEld93H86V0ieLMRPg5llXWfKND2W8vezZSCGqFcSo+bAVi0YzA6XbLu+TV
GyyCD8Jk/efmdN0DKjERIKDH
-----END PRIVATE KEY-----
`

	untrustedCAKeyPEM = `
-----BEGIN PRIVATE KEY-----
MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCca1lasr3QaiEt
hhcUPtqBkfDjTUAmQCPIE7Hr97gO1s9yuwB2jTxy1fAEGulynXkvKSnQIiES4OqC
++AHAMAGTEeoaSHuGr/xsQeWV0zIvGb9l4l8DDw2zlulT63tHj93WQCY63cO/z5a
12k2S9jTALsOSevI4ISlk/rPsUjYXqlFWWViI/Cz412RGvRO40TiEW/IrXnwmxFg
UUweOB/CR8+qL0TrkSVt5MAG1WFPgmenQZ3HDQn5OOAspy1pjsUDpeCIgcd3dd5h
xNVJsEXgCoH1lLNLaU4KfySZGOu7rQnoJUaG1GQxwW2hq19xhPJafXsK7hSoegXn
O5Cji1p9AgMBAAECggEAGrReGTS2YNBZsTi5FHkFm4TjvB66cr1VDonEQL22U/w7
BwKjmdnTheR9+WlxzSJS5+kObS0CegNhrjKbxP5NClY4DqnCQ/EXr4byfWmSn5vA
to9KRt4c4pt4/IBduIOHQNQ2XKUomqwbhG1N1ln9Hsr7ZH1czpr8MnuQ4KgLAHtm
Zd3xibhULlE/5S9uYpvBaqwZx3RJk3CMcseio4aFtpx/yi+UPyDWekS0ch2Fz5MF
G8wnYqXHt2NZhRWdBrQ7n7ZCQT2zNGvKkekAd2jIl5H+mxIRFSlArgdx+TeDJrxZ
J+mmYooOY7gE8GL+7pSpCb2yNHcA/CkDQspM14QqOQKBgQDQtnanCgAbxW5pDgT4
fdi4EVWPt9nF18mMXwDZuSqrwPj1kKx9CqhVXbRT4kMaYgMuhn1coC4m+j95kaG+
dTLtrfnl8mTr/4HBImaacisn1i9gYKfYwoKscBrK9B5n/zaH/HnCKehbTaeinnID
vvbd7cI2f/YFPVgiq2W6yxDGyQKBgQC/29Frc5rZyNCellT/583AU240PY5b3yhd
FsYdkCBdI5suq/shvEmwxDqKv8eJt+MRXdPiFub6Wk3wcsiR20saQSsM09sUA5dQ
CuKZlD2w93DzsbRXinRtwwBYyGFS4C89Bz1AO6rGyi3+GDlJuTAo7XYP9HVwUVos
rvYB6rysFQKBgQDDj2Qn6a/mVZy5pOA1cb2B4wQXsL3FqgZ4l3/1gZGg8ySS+2cT
lsvZiP5xZt1XOCUhD/UguBnmfa7CGxnBmpEIsW4o7nFvy63pqHEZIAadwgwMMySy
brcAGd6Q8iIXccPHsWLo8ll8S4vaTLoqFmG72o6SgF2l1S/i9FRSrPjgOQKBgG+O
xo9/MewuezerXBNM2vNYz7yqiktbT+II6vunoVnm6UXTFHxCOmsBPrUM3F50wSCI
+Tn+bSHnPmhwpbVB2MKUYA7eZQWXLPWKzsXUT6bFyjS5AI7iX96uw1XcddK1rmID
ApeF2kAqsWGM/kqi1qEFql+OmnbLpu5ScZMdxcUdAoGBAIkyMaBNiWyImkyl/bxv
pnhP/eZf435ZQoXRRKR9ONejT9moJoWfNKqwZKgnnHuYA7XmG45N3vSLZgocggDm
1XWL2XxS0Qs90JOgM3lY3VUfxRwCCWAiYy4a1O8OxaP8dURgy4uqrtH08xRunsIL
9G6RWORMdSgKMYvi9bJYoHQZ
-----END PRIVATE KEY-----
`
)