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

package main

import (
	"android.com/libcore/certutil"
	"crypto/x509"
	"path/filepath"
)

// Build the required certs for X509CertChainBuildingTest.
// See that class for the requirements.
func main() {
	a := certutil.NewCA("Root A")
	b := certutil.NewCA("Root B")
	intermediate := certutil.NewCA("intermediate")
	leaf1 := certutil.NewEntity("Leaf")
	leaf2 := certutil.NewEntity("Leaf 2")

	outdir := "assets/path_building/"
	a.SignToPEM(a, filepath.Join(outdir, "a"))
	a.SignWithAlgorithmToPEM(a, x509.ECDSAWithSHA1, filepath.Join(outdir, "a_sha1"))
	b.SignToPEM(b, filepath.Join(outdir, "b"))

	a.SignToPEM(b, filepath.Join(outdir, "b_to_a"))
	b.SignToPEM(a, filepath.Join(outdir, "a_to_b"))

	a.SignToPEM(leaf1, filepath.Join(outdir, "leaf1"))

	a.SignToPEM(intermediate, filepath.Join(outdir, "intermediate_a"))
	b.SignToPEM(intermediate, filepath.Join(outdir, "intermediate_b"))

	intermediate.SignToPEM(leaf2, outdir+"leaf2")
}
