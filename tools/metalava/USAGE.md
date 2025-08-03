# Metalava usage by clients

This doc lists out known usages of Metalava with specific features used by each user. The main users are Android
platform and AndroidX libraries.

## Features used by both androidx and Android platform
- Signature generation of `*.txt` e.g. `current.txt` (`--api`)
- API change compatibility checking of current API vs previous version txt (`--check-compatibility:api:released`)
- API lint for compliance with [Android API guidelines](https://s.android.com/api-guidelines) (`--api-lint`)

## Features used exclusively by androidx
- Kotlin sources as inputs

## Features used exclusively by Android platform
- Support for partial signature files (opposite of `--show-unannotated`) where `@SystemApi` txt file omits public APIs
- Java stubs for jar generation (`--stubs`) for generating android.jar, etc for SDK
- Java stubs for docs generation (`--doc-stubs`) for passing to documentation tools
- Documentation stub enhancement (`--enhance-documentation`)
- Injecting `added in API level` into documentation stubs (`--apply-api-levels`)
- Generating API levels information for documentation and SDK (`--generate-api-levels`)
- Documentation rewriting for documentation stubs (`--replace-documentation`) used by libcore
- Rewriting of nullness annotations to @RecentlyNull/NonNull (`--migrate-nullness`,
`--force-convert-to-warning-nullability-annotations`) for SDK
- Tracking @removed APIs (`--removed-api`)
- DEX API signature generation (`--dex-api`) for for hidden API enforcement
- XML API signature generation (`--api-xml`) for CTS tests and test coverage infrastructure
- Annotation include, exclude, rewrite, passthrough in stubs (`--include-annotations`, `--exclude-all-annotations`,
`--pass-through-annotation`, `--exclude-annotation`)
- Annotation extraction (`--extract-annotations`, `--copy-annotations`) for generating the SDK
- Generating SDK metadata (`--sdk-`values`)
