load("@rules_license//rules:license.bzl", "license")
load("@rules_license//rules:license_kind.bzl", "license_kind")
load("@rules_rust//rust:defs.bzl", "rust_library")

package(
    default_applicable_licenses = [":license"],
    default_visibility = ["//visibility:public"],
)

license(
    name = "license",
    license_kinds = [
        ":SPDX-license-identifier-Apache-2.0",
        ":SPDX-license-identifier-MIT",
    ],
    license_text = "LICENSE-APACHE",
    visibility = [":__subpackages__"],
)

license_kind(
    name = "SPDX-license-identifier-Apache-2.0",
    conditions = ["notice"],
    url = "https://spdx.org/licenses/Apache-2.0.html",
)

license_kind(
    name = "SPDX-license-identifier-MIT",
    conditions = ["notice"],
    url = "",
)

rust_library(
    name = "zerocopy",
    srcs = glob(["**/*.rs"]),
    crate_features = [
        "byteorder",
        "derive",
	"simd",
        "zerocopy-derive",
    ],
    edition = "2021",
    proc_macro_deps = [
        # This should map to repo checked out from Android third party project
        # "platform/external/rust/crates/zerocopy-derive".
        "@zerocopy-derive",
    ],
    deps = [
        # Map to project repo "platform/external/rust/crates/byteorder".
        "@byteorder",
    ],
)
