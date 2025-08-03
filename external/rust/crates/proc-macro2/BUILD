load("@rules_license//rules:license.bzl", "license")
load("@rules_license//rules:license_kind.bzl", "license_kind")
load("@rules_rust//cargo:defs.bzl", "cargo_build_script")
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

CRATE_FEATURES = [
    "default",
    "proc-macro",
    "span-locations",
]

rust_library(
    name = "proc-macro2",
    srcs = glob(["**/*.rs"]),
    crate_features = CRATE_FEATURES,
    edition = "2021",
    deps = [
        ":proc-macro2_build_script",
        # This should map to repo checked out from Android third party project
        # "platform/external/rust/crates/unicode-ident".
        "@unicode-ident",
    ],
)

cargo_build_script(
    name = "proc-macro2_build_script",
    srcs = glob(["**/*.rs"]),
    crate_features = CRATE_FEATURES,
    crate_root = "build.rs",
    edition = "2021",
    visibility = ["//visibility:private"],
)
