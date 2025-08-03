# Compatibility

As stated in the README, one of Metalava's core functions is that it
can diff two versions of an API to determine compatibility. But what
does that word "compatibility" mean? This document details the definitions
Metalava maintainers have adopted then uses those definitions to outline
the specific compatibility that Metalava strives to uphold.

The inspiration for this comes from the [Evolving Java Based APIs series](https://wiki.eclipse.org/Evolving_Java-based_APIs)
in the Eclipse Wiki.

## Binary Compatibility

Binary compatibility is when **existing binaries correctly link with an updated library at load time**.

An example of a binary incompatibility is deleting a public method.

Metalava strives to prevent 100% of binary incompatible changes when performing
compatibility checks. In the context of semantic versioning, incompatibilities are only allowed at
major version bumps or by special exemption during certain later stages of release.

## Source Compatibility

Source compatibility is when **existing source compiles against an updated library without compile time errors**.

Examples of source incompatibilities are adding an enum value in Kotlin (due to exhaustive when checks) or
changing the name of a parameter.

Metalava warns or blocks on many forms of source incompatibility; however, 100% enforcement is not a goal.
about source compatibility than binary compatibility. Some forms of source incompatibility are simple to fix
and very difficult to avoid; therefore source compatibility is not considered a hard requirement for API Compatibility.

## Runtime Compatibility

Runtime compatibility is when **existing valid interactions with an updated library do not trigger unexpected exceptions at runtime**.

An example of runtime incompatibility is changing a method from nullable to non-null.

Runtime incompatibility is impossible to enforce with tooling, but is nice to have. Therefore Metalava strives
to prevent runtime incompatibility with it's checks, but cannot provide any assurances about it.

## API Contract Compatibility


API Contract Compatibility is when **existing client code is not invalidated by the new API**.

API compatibility is most strongly enforceable with Binary compatibility. Unfortunately,
it is extremely difficult to fully automate the detection of all Api Contract incompatibilities. For example,
if a method documents that it returns a non-empty list, then the comment changes to state that it allows
the return of an empty list, that breaks the API contract.

Metalava strives to maintain API Contract Compatibility as fully as possible.

