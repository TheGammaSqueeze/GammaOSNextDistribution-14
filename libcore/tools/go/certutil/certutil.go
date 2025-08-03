// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Utility methods for generating X.509 certificate chains.
package certutil

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"log"
	"math/big"
	"os"
	"time"
)

type Entity struct {
	PrivateKey *ecdsa.PrivateKey
	Template   *x509.Certificate
	// CA entities only
	LastSerial *big.Int
}

var two32 = big.NewInt(1 << 32)
var one = big.NewInt(1)

func newKey() *ecdsa.PrivateKey {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		log.Fatal(err)
	}
	return key
}

func newTemplate(cn string) *x509.Certificate {
	notBefore, err := time.Parse(time.RFC3339, "2020-01-01T00:00:00Z")
	if err != nil {
		log.Fatal(err)
	}
	notAfter, err := time.Parse(time.RFC3339, "2030-01-01T00:00:00Z")
	if err != nil {
		log.Fatal(err)
	}
	return &x509.Certificate{
		NotBefore: notBefore,
		NotAfter:  notAfter,
		Subject:   pkix.Name{CommonName: cn},
	}
}

func NewEntity(name string) *Entity {
	return &Entity{
		PrivateKey: newKey(),
		Template:   newTemplate(name),
	}
}

func NewCA(name string) *Entity {
	ca := NewEntity(name)
	ca.Template.BasicConstraintsValid = true
	ca.Template.IsCA = true
	ca.LastSerial = mustRandInt(two32)
	return ca
}

func (e *Entity) publicKey() *ecdsa.PublicKey {
	return &e.PrivateKey.PublicKey
}

func (e *Entity) name() string {
	return e.Template.Subject.String()
}

func (e *Entity) nextSerial() *big.Int {
	if e.LastSerial == nil {
		log.Fatal("Not a CA: " + e.name())
	}
	e.LastSerial = e.LastSerial.Add(e.LastSerial, one)
	return e.LastSerial
}

func (ca *Entity) doSign(childTemplate *x509.Certificate, pubKey *ecdsa.PublicKey) []byte {
	copyTemplate := *childTemplate
	copyTemplate.SerialNumber = ca.nextSerial()
	cert, err := x509.CreateCertificate(
		rand.Reader,
		&copyTemplate,
		ca.Template,
		pubKey,
		ca.PrivateKey)
	if err != nil {
		log.Fatal(err)
	}
	return cert
}

func (ca *Entity) Sign(child *Entity) []byte {
	return ca.doSign(child.Template, child.publicKey())
}

func (ca *Entity) SignWithAlgorithm(child *Entity, algorithm x509.SignatureAlgorithm) []byte {
	copyTemplate := *child.Template
	copyTemplate.SignatureAlgorithm = algorithm
	return ca.doSign(&copyTemplate, child.publicKey())
}

func (ca *Entity) SignToPEM(child *Entity, filename string) {
	cert := ca.Sign(child)
	mustWriteFile(filename+".pem", encodePEM(cert))
}

func (ca *Entity) SignWithAlgorithmToPEM(child *Entity, algorithm x509.SignatureAlgorithm, filename string) {
	cert := ca.SignWithAlgorithm(child, algorithm)
	mustWriteFile(filename+".pem", encodePEM(cert))
}

func encodePEM(b []byte) []byte {
	return pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: b})
}

func mustWriteFile(path string, bs []byte) {
	if err := os.WriteFile(path, bs, 0666); err != nil {
		log.Fatal(err)
	}
}

func mustRandInt(max *big.Int) *big.Int {
	r, err := rand.Int(rand.Reader, max)
	if err != nil {
		log.Fatal(err)
	}
	return r
}
