# Soong-Bazel equivalents

This doc aims to describe *internal*-facing implementation concepts. For
external-facing, see
https://android.googlesource.com/platform/build/bazel/+/refs/heads/master/docs/concepts.md.

[TOC]

## Overview

Soong/Ninja                                                     | Bazel                                                                                    | Remarks
--------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | -------
make phony goal, e.g. "dist", "sdk", "apps_only", "droidcore"   | Top level `filegroup` rule target                                                        | [Details](#phony-goal)
Ninja build target (phony)                                      | (readable) alias to a file target                                                        |
Ninja build target (non-phony)                                  | File target                                                                              |
`ModuleFactory`                                                 | `RuleConfiguredTargetFactory`                                                            |
`Module type` (e.g. `cc_library`)                               | Rule class (e.g. `cc_library`)                                                           |
Module object instance                                          | Target (instance of a rule)                                                              | [Details](#instance)
Module properties                                               | [Rule attributes](https://docs.bazel.build/versions/main/skylark/rules.html#attributes)  | [Details](#props)
Module name                                                     | Target label                                                                             |
Module variant                                                  | (Split) configured target                                                                |
[LoadHooks](#loadhooks)                                         | [macros (ish)](https://docs.bazel.build/versions/main/skylark/macros.html)               |
Top-down mutators on modules                                    | Split configuration on targets                                                           | Allows building multiple "variants" of the same build artifact in the same build.
Bottom-up mutators on modules                                   | [Aspects](https://docs.bazel.build/versions/main/skylark/aspects.html) on targets        |
[Build statement (Ninja)](#ninja-build-statement)               | Action (result of ctx.actions.run)                                                       |
[Rule statement (Ninja)](#ninja-rules)                          | [ctx.actions.run() API](https://docs.bazel.build/versions/main/skylark/lib/actions.html) |
`out/soong/build.ninja` and `out/build-<target>.ninja`          | Action graph (serialized)                                                                |
Pool (ninja)                                                    | Thread pools / `ExecutorService`                                                         |
Blueprint's Registration and Parse, `ResolveDependencies` phase | Loading phase                                                                            |
Blueprint's [Generate and Write phases](#blueprint-analysis)    | Analysis Phase                                                                           |
Ninja execution                                                 | Execution phase                                                                          |
Blueprints/`Android.bp` files                                   | `BUILD`/`BUILD.bazel` files                                                              |
[Namespaces](#namespaces)                                       | [Packages](#pkgs)                                                                        | Most Soong modules are within the global namespace
[Mutators](#mutators)                                           | Configuration keys (ish)                                                                 |
[Variation](#variation)                                         | Configuration value                                                                      |
[Singleton](#singleton)                                         | Aspect-ish                                                                               |
Target (system + vendor + product)                              | [Platform](https://docs.bazel.build/versions/main/platforms.html)                        |
Bash scripts e.g. envsetup functions, `soong_ui.bash`)          | Repository rule                                                                          |
Product and board configuration makefile and env variables      | Configuration in Bazel (ish)                                                             | [Details](#config)
[Dependency Tags](#deptags)                                     | Provider names                                                                           |

## Remarks

### Phony goals {#phony-goal}

Soong maintains the make terminology of
[goals](https://www.gnu.org/software/make/manual/html_node/Goals.html) to denote
what should be built. All modules can be specified by name as a goal, in
addition, phony goals are supported.

A Phony goal creates a Make-style phony rule, a rule with no commands that can
depend on other phony rules or real files. Phony can be called on the same name
multiple times to add additional dependencies. These are often used to build
many targets at once. The default goal for Android's build system is `droid`.
Some other common phony goals include: `nothing` (perform loading/analysis),
`docs`, `checkbuild`, `apps_only`.

Some common phony goals are defined in
[`build/make/core/main.mk`](http://cs.android.com/android/platform/superproject/+/master:build/make/core/main.mk)
The purpose is to help `soong_ui` to determine what top level files to build.

### Module/Target {#instance}

When a Module is instantiated by Blueprint (which calls the appropriate
`ModuleFactory`), the [property structs](#props) are populated by Blueprint.

Blueprint performs no additional operations on these properties, such that
dependencies on other modules and references to source files are unresolved
initially. [`Mutators`](#mutators) then introspect the values of properties to
[specify dependencies](https://cs.android.com/android/platform/superproject/+/master:build/blueprint/module_ctx.go;l=871-886,918-960;drc=030150d8f9d164783ea661f07793c45198739cca)
between modules, which
[Blueprint resolves](https://cs.android.com/android/platform/superproject/+/master:build/blueprint/context.go;l=1630,1667;drc=5c4abb15e3b84ab0bcedfa119e2feb397d1fb106).
Source files (including globs) and output paths for references to other modules
are resolved during [blueprint analysis](#blueprint-analysis) via the various
`Path[s]ForModuleSrc[Excludes]` functions within
[build/soong/android/paths.go](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/paths.go).

For a Bazel target instance, the dependencies and source file references within
[`attrs`](#attributes) have been resolved by Bazel.

Bazel
[implementation](https://github.com/bazelbuild/bazel/blob/a20b32690a71caf712d1d241f01fef16649562ba/src/main/java/com/google/devtools/build/lib/skyframe/TransitiveBaseTraversalFunction.java#L113-L140)
to collect deps.

### Properties/Attributes {#props}

#### Properties

Within Soong/Blueprint, properties are represented as Go structs, which can be
nested, with no depth limit. Properties can be primitive or pointer types, but
they must be one of these types: `int64`, `string`, `bool`, `list`.

These properties can be defined from various structs within the module type
factory itself (via
[AddProperties](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=1276;drc=8631cc7327919845c9d9037188cbd483d22ba077))
or from common helper functions such as:

*   `InitAndroidModule`:
    [specifies](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=1042-1045;drc=8631cc7327919845c9d9037188cbd483d22ba077)
    name-related, common, and dist properties.
*   `InitAndroidArchModule`: adds
    [host/device properies](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=1077;drc=8631cc7327919845c9d9037188cbd483d22ba077)

Go comments for a property will be treated as documentation to describe the
property. In some cases, these comments describe a default value for the
property. However, the default value is not based on the comment or field
definition but resolved somewhere within the module's mutators or build. These
defaults are often determined using Blueprint
[`proptools`](https://cs.android.com/android/platform/superproject/+/master:build/blueprint/proptools/proptools.go)
`*Default` functions. For example, `cc` modules have a property
[`include_build_directory`](https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/compiler.go;l=265;drc=135bf55281d79576f33469ce4f9abc517a614af5),
which is described in the comments. The default value is
[resolved](https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/compiler.go;l=265;drc=135bf55281d79576f33469ce4f9abc517a614af5)
when compiler flags are being determined.

In general, these can be set in an Android.bp file. However, if the property is
tagged with `` `blueprint:"mutated"` ``, it can only be set programmatically
within Blueprint/Soong. Additionally, `mutated` tagged properties also support
`map` and `int` types in addition to those mentioned above. These `mutated`
properties are used to propagate data that gets set during mutations, which
ensures that the information is copied successfully to module variants during
mutation.

Soong supports additional property tags to provide additional
functionality/information about a property:

*   `` `android:arch_variant` ``: This specifies that a property can be
    configured for different architectures, operating systems, targets, etc. The
    [arch mutator](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/arch.go;l=597;drc=135bf55281d79576f33469ce4f9abc517a614af5),
    will merge target-specific properties into the correct variant for
    properties with this tag.

    Note: if a nested property is arch-variant, all recursively nesting structs
    that can be specified in an Android.bp file must also be tagged as
    arch-variant.

*   `` `android:variant_prepend` ``: When merging properties for the arch
    variant, the arch-specific values should be *prepended* rather than appended
    to existing property values.

*   `` `android:path` ``: This specifies that this property will contain some
    combination of:

    *   module-relative paths
    *   references to other modules in the form:
        *   `":<name>{.<tag>}"`, where `{.<tag>}` is optional to specify a
            non-default output file, specific to the module type
        *   `"<namespace>:<name>{.<tag>}""`

    Note: Dependencies to other modules for these properties will be
    automatically added by the
    [pathdeps mutator](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/path_properties.go;l=40;drc=40131a3f9e5ac974a44d3bd1293d31d585dc3a07).

#### Attributes

Similar to properties,
[attributes](https://docs.bazel.build/versions/main/skylark/lib/attr.html) only
support a few types. The difference is that Bazel attributes cannot be nested .

Some attributes are
[common](https://docs.bazel.build/versions/2.1.0/be/common-definitions.html#common-attributes)
across many/all rule classes, including (but not limited to) `name`, `tag`,
`visibility`.

The definition of an attribute can contain settings, such as: its default value,
whether it is mandatory ot have a value, and its documentation.

To specify a source file or reference to another module, use `label` or
`label_list` attribute types (rather than regular `string` or `string_list`
types). These support additional restrictions (as compared to `string*` types),
such as:

*   whether files are supported
*   the providers that must be given by a dependency
*   whether the dependency should be executable
*   the configuration (host, target)
*   aspects

Unlike Soong, when accessing this attribute within the rule's implementation (at
anlysis time), the label(s) will be resolved to the file or target they refer
to.

Attributes do not need to specify whether they accept
[configurable attribute](https://docs.bazel.build/versions/main/configurable-attributes.html).
However, the rule definition can specify the configuration or specify a
[configuration transition](https://docs.bazel.build/versions/main/skylark/lib/transition.html).

However, not all target definitions within a `BUILD` file are invoking a rule.
Instead, they may invoke a Starlark macro, which is a load-time wrapper around
rules. Arguments for a macro are not typed. If macros are used, their arguments
would have to be wrangled into an attribute-compatible type.

### LoadHooks

[LoadHooks](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/hooks.go;l=24-36;drc=07656410df1836a70bea3054e50bb410ecbf8e07)
provide access to :

*   append/prepend additional properties to the module
    (`AppendProperties`/`PrependProperties`)
*   create a new module `CreateModule`

`LoadHooks` make it easier to extend existing module factories to always specify
certain properties or to split a single `Android.bp` definition into multiple
Module instances .

### Build Statement (ninja) {#ninja-build-statement}

[Ninja build statements](https://ninja-build.org/manual.html#_build_statements) can be
expanded from [Ninja rules](https://ninja-build.org/manual.html#_rules), which are like
templates.

```
# rule
rule cattool
  depfile = out/test/depfile.d
  command = ${in} ${out}

# build statement
build out/test/output.txt: cattool test/cattool.sh test/one test/two

# build statement
build out/test/other_output.txt: cattool test/cattool.sh test/three test/four
```

Rules for `Android.mk` modules (`out/build-<target>.ninja`) and build statements
are 1:1. That is every rule is only used once by a single build statement.

Soong (`out/soong/build.ninja`) rules are reused extensively in build statements
(1:many). For example the `Cp` rule is a commonly used rule for creating build
statements which copy files.

### Ninja Rules in Soong {#ninja-rules}

In Soong, Ninja rules can be defined in two ways:

*   [rule_builder](http://cs.android.com/android/platform/superproject/+/master:build/soong/android/rule_builder.go)
*   [package_ctx](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/package_ctx.go;l=102-293;drc=77cdcfdeafd383ef1f1214226c47eb20c902a28f)

### Blueprint Generate & Write phase {#blueprint-analysis}

1.  [`ResolveDependencies`](https://cs.android.com/android/platform/superproject/+/master:build/blueprint/context.go;l=1547;drc=5c4abb15e3b84ab0bcedfa119e2feb397d1fb106)
    Running a series of Mutators, to add dependencies, split modules with
    variations, etc
1.  [`PrepareBuildActions`](https://cs.android.com/android/platform/superproject/+/master:build/blueprint/context.go;l=2367;drc=5c4abb15e3b84ab0bcedfa119e2feb397d1fb106):

    1.  Running Modules’ `GenerateBuildActions` to generate Ninja statements,
        which in turn calls each module's
        [`GenerateAndroidBuildActions`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=445-448;drc=8631cc7327919845c9d9037188cbd483d22ba077).
    1.  Running Singletons to generate Ninja statements that generate docs,
        android.mk statements, etc

### Soong namespaces {#namespace}

Module
[Namespaces](https://android.googlesource.com/platform/build/soong/+/master/README.md#namespaces)
can import other namespaces, and there’s a module name lookup algorithm which
terminates in the global namespace.

Note: this is not widely used and most Soong modules are in the global
namespace.

### Bazel packages {#pkgs}

[Packages](https://docs.bazel.build/versions/main/build-ref.html#packages) can
nest subpackages recursively, but they are independent containers of Bazel
targets. This means that Bazel target names only need to be unique within a
package.

### Mutators

blueprint invokes mutators are invoking in the order they are registered (e.g.
top-down and bottom-up can be interleaved). Each mutator applys a single
visitation to every module in the graph.

Mutators visiting module can parallelized, while maintaining their ordering, by
calling `.Parallel()`.

While top-down and bottom-up mutators differ in their purposes, the interface
available to each contains many similarities. Both have access to:
[`BaseModuleContext`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=139;drc=8631cc7327919845c9d9037188cbd483d22ba077)
and
[`BaseMutatorContext`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/mutator.go;l=246;drc=2ada09a5463a0108d713773679c5ba2c35450fa4).

In addition to the registration order, Soong supports phase-based ordering of
mutators:

1.  Pre-Arch: mutators that need to run before arch-variation. For example,
    defaults are handled at this stage such properties from defaults are
    correctly propagated to arch-variants later.

1.  (Hard-coded)
    [`archMutator`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/arch.go;l=597;drc=135bf55281d79576f33469ce4f9abc517a614af5)
    splits a module into the appropriate target(s). Next, the arch- and
    OS-specific properties are merged into the appropriate variant.

1.  Pre-Deps: mutators that can/need to run before deps have been resolved, for
    instance, creating variations that have an impact on dependency resolution.

1.  (Hard-coded)
    [`depsMutator`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/mutator.go;l=502;drc=2ada09a5463a0108d713773679c5ba2c35450fa4),
    which calls the `DepsMutator` function that *must* be part of a Soong
    `Module`'s interface.

1.  Post-Deps: mutators that need to run after deps have been resolved

1.  Final-Deps like post-deps but variations cannot be created

#### Top-down Mutator

A top-down mutator is invoked on a module before its dependencies.

The general purpose is to propagate dependency info from a module to its
dependencies.

#### Bottom-up Mutator

A bottom-up mutator is invoked on a module only after the mutator has been
invoked on all its dependencies.

The general purpose of a bottom-up mutator is to split modules into variants.

### Soong/Blueprint Variation {#variation}

A tuple (name of mutator, variation / config value) passed to
`CreateVariations`.

### Configuration {#config}

Soong's config process encompasses both *what* should build and *how* it should
build. This section focuses on the *how* aspect.

We do not cover how Soong's configuration will be implemented in Bazel, but the
general capabilities of Bazel to configure builds.

#### Soong

Android users can configure their builds based on:

*   Specifying a target (via lunch, banchan, tapas, or Soong’s command line
    options)
*   Environment variables

Some environment variables or command line options are used directly to alter
the build. However, specification of target product encompasses many aspects of
both *what* and *how* things are built. This configuration is currently handled
within Make but is in the process of being migrated to Starlark.

Soong
[invokes Kati](https://cs.android.com/android/platform/superproject/+/master:build/soong/ui/build/dumpvars.go;drc=7ae80a704494bbb934dced97ed97eb55a21a9a00)
to run in a "config" mode, also commonly known as "product config". This mode
limits the scope of what `.mk` files are parsed. The product-specific handlers
are largely in:

*   [`product_config.mk`](https://cs.android.com/android/platform/superproject/+/master:build/make/core/product_config.mk;drc=d189ab71f3505ea28324ebfaced2466af5eb0af7):
    this subset of functionality is also commonly referred to as "product
    config"
*   [`board_config.mk`](https://cs.android.com/android/platform/superproject/+/master:build/make/core/board_config.mk)

However, these cover only a subset of
[`config.mk`](https://cs.android.com/android/platform/superproject/+/master:build/make/core/config.mk).
This ensures that all values have appropriate defaults and specify details
necessary to the build. Some examples:

*   [handling of version defaults](https://cs.android.com/android/platform/superproject/+/master:build/make/core/version_defaults.mk)
*   [rbe setup](https://cs.android.com/android/platform/superproject/+/master:build/make/core/rbe.mk)
*   [user-defined config](https://cs.android.com/android/platform/superproject/+/master:build/make/core/config.mk;l=300-308;drc=ee20ae1a8dcdfe7b843d65099000708800d9b93a):
    [buildspec.mk](http://cs.android.com/android/platform/superproject/+/master:build/make/buildspec.mk.default)
    is similar to
    [`.bazelrc`](https://docs.bazel.build/versions/main/guide.html#bazelrc-the-bazel-configuration-file)
    file.
*   ensuring
    [`PRODUCT_SHIPPING_API_LEVEL`](https://cs.android.com/android/platform/superproject/+/master:build/make/core/config.mk;l=729-745;drc=ee20ae1a8dcdfe7b843d65099000708800d9b93a)
    is defaulted if not specified by the target.

Finally, Kati dumps variables to be consumed by Soong:

*   environment variables specifically requested by Soong
*   writes
    [`soong.variables`](http://cs.android.com/android/platform/superproject/+/master:build/make/core/soong_config.mk),
    a JSON file

Throughout Soong, environment variables can be accessed to alter the build via
the `Config`:

*   [`GetEnv`](http://cs.android.com/search?q=f:soong%20%5C.GetEnv%5C%28%20-f:%2Fui%2F%20-f:%2Fcmd%2F&sq=)
*   [`GetEnvWithDefault`](http://cs.android.com/search?q=f:soong%20%5C.GetEnvWithDefault%5C%28%20-f:%2Fui%2F%20-f:%2Fcmd%2F&sq=)
*   [`IsEnvTrue`](http://cs.android.com/search?q=f:soong%20%5C.IsEnvTrue%5C%28%20-f:%2Fui%2F%20-f:%2Fcmd%2F&sq=)
*   [`IsEnvFalse`](http://cs.android.com/search?q=f:soong%20%5C.IsEnvFalse%5C%28%20-f:%2Fui%2F%20-f:%2Fcmd%2F&sq=)

Soong
[loads the `soong.variables`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/config.go;l=174;drc=b078ade28d94c85cec78e9776eb31948a5647070)
config file, stored as
[`productVariables`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/variable.go;l=163;drc=16e77a9b303a71018eb6630f12f1414cd6ad615c).
These variables are used in three ways:

*   Direct access from `Config`, for example: paths can be
    [opted out](https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/sanitize.go;l=364,371,393;drc=582fc2d1dde6c70687e6a0bea192f2a2ef67bbd5)
    of specific sanitizers
*   In limited cases, users can use these within their `Android.bp` file to
    control what is built or perform variable replacement.
    [`variableProperties`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/variable.go;l=38;drc=16e77a9b303a71018eb6630f12f1414cd6ad615c)
    limits which configuration variables can be specified within an `Android.bp`
    file and which properties they can apply to. The values specified within an
    `Android.bp` file, are merged/replaced by the
    [`VariableMutator`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/variable.go;l=539;drc=16e77a9b303a71018eb6630f12f1414cd6ad615c),
    which appends performs string replacement if requested and merges the
    properties into the modules.
*   Through
    [Soong Config Variables](https://android.googlesource.com/platform/build/soong/+/refs/heads/master/README.md#soong-config-variables):
    which allow users to specify additional configuration variables that can be
    used within an `Android.bp` file for the module type and properties they
    request. Soong config variable structs are
    [dynamically generated](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/soongconfig/modules.go;l=257;drc=997f27aa0353dabf76d063d78ee5d4495da85651)
    via reflection. In the
    [factory](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/soong_config_modules.go;l=423;drc=18fd09998223d004a926b02938e4cb588e4cc934),
    the properties to merge into the module instance are
    [identified](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/soongconfig/modules.go;l=416;drc=997f27aa0353dabf76d063d78ee5d4495da85651)
    based on the config variable's type.

The product configuration also provides information about architecture and
operating system, both for target(s) and host. This is used within the
[`archMutator`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/arch.go;l=569-597;drc=135bf55281d79576f33469ce4f9abc517a614af5)
to split a module into the required variants and merge target-specific
properties into the appropriate variant. Only properties which have been tagged
with `android:"arch_variant"` can be specified within an `Android.bp` as
arch/os/target-specific. For example:

```go
type properties struct {
  // this property will be arch-variant
  Arch_variant_not_nested *string `android:"arch_variant"`

  Nested_with_arch_variant struct {
    // this property is arch-variant
    Arch_variant_nested *string `android:"arch_variant"`

    // this property is **not** arch-variant
    Not_arch_variant_nested *string
  } `android:"arch_variant"`

  Nested_no_arch_variant struct {
    // this property is **NOT** arch-variant
    No_arch_variant_nested_not_arch_variant *string `android:"arch_variant"`

    // this property is **not** arch-variant
    No_arch_variant_nested *string
  }
}
```

The arch/os/target-specific structs are
[dynamically generated](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/arch.go;l=780-787;drc=135bf55281d79576f33469ce4f9abc517a614af5)
based on the tags using reflection.

#### Bazel

Bazel documentation covers configurable builds fairly extensively, so this is a
short overview that primarily links to existing Bazel documentation rather than
repeating it here.

[Configurable attributes](https://docs.bazel.build/versions/main/configurable-attributes.html),
(aka `select()`) allows users to toggle values of build rule attributes on the
command line.

Within a `rule`, the value of a `select` will have been resolved based on the
configuration at analysis phase. However, within a macro (at loading phase,
before analysis phase), a `select()` is an opaque type that cannot be inspected.
This restricts what operations are possible on the arguments passed to a macro.

The conditions within a `select` statement are one of:

*   [`config_setting`](https://docs.bazel.build/versions/main/be/general.html#config_setting)
*   [`constraint_value`](https://docs.bazel.build/versions/main/be/platform.html#constraint_value)

A `config_setting` is a collection of build settings, whether defined by Bazel,
or user-defined.

User-defined
[build settings](https://docs.bazel.build/versions/main/skylark/config.html#defining-build-settings)
allow users to specify additional configuration, which *optionally* can be
specified as a flag. In addition to specifying build settings within a
`config_setting`, rules can depend directly on them.

In addition, Bazel supports
[`platform`s](https://docs.bazel.build/versions/main/be/platform.html#platform),
which is a named collection of constraints. Both a target and host platform can
be specified on the command line.
[More about platforms](https://docs.bazel.build/versions/main/platforms.html).

## Communicating between modules/targets

### Soong communication

There are many mechanisms to communicate between Soong modules. Because of this,
it can be difficult to trace the information communicated between modules.

#### Dependency Tags {#deptags}

Dependency tags are the primary way to filter module dependencies by what
purpose the dependency serves. For example, to filter for annotation processor
plugins in the deps of a Java library module, use `ctx.VisitDirectDeps` and
check the tags:

```
ctx.VisitDirectDeps(func(module android.Module) {
  tag := ctx.OtherModuleDependencyTag(module)
  if tag == pluginTag { patchPaths += ":" + strings.Split(ctx.OtherModuleDir(module), "/")[0] }
  }
)
```

At this point the module managing the dependency, may have enough information to
cast it to a specific type or interface and perform more specific operations.

For instance, shared libraries and executables have
[special handling](http://cs.android.com/android/platform/superproject/+/master:build/soong/cc/cc.go;l=2771-2776;drc=5df7bd33f7b64e2b880856e3193419697a8fb693)
for static library dependencies: where the coverage files and source based ABI
dump files are needed explicitly. Based on the dependency tag, the module is
cast to a concrete type, like `cc.Module`, where internal fields are accessed
and used to obtain the desired data.

Usage of dependency tags can be more evident when used between module types
representing different langauges, as the functions must be exported in Go due to
Soong's language-based package layout. For example, rust uses `cc` module's
[`HasStubVariants`](http://cs.android.com/android/platform/superproject/+/master:build/soong/rust/rust.go;l=1457-1458;drc=9f59e8db270f58a3f2e4fe5bc041f84363a5877e).

#### Interfaces

A common mechanism for a module to communicate information about itself is to
define or implement a Go interface.

Some interfaces are common throughout Soong:

*   [`SourceFileProducer`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=2967;drc=8707cd74bf083fe4a31e5f5aa5e74bd2a47e9e58),
    by implementing `Srcs() Paths`
*   [`OutputFileProducer`](http://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=2974;drc=8707cd74bf083fe4a31e5f5aa5e74bd2a47e9e58)
    by implementing `OutputFiles(string) (Paths, error)`
*   [`HostToolProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=3032;drc=8707cd74bf083fe4a31e5f5aa5e74bd2a47e9e58)
    by implementing `HostToolPath() OptionalPath`

`SourceFileProducer` and `OutputFileProducer` are used to resolve references to
other modules via `android:"path"` references.

Modules may define additional interfaces. For example, `genrule` defines a
[`SourceFileGenerator` interface](http://cs.android.com/android/platform/superproject/+/master:build/soong/genrule/genrule.go;l=98-102;drc=2ada09a5463a0108d713773679c5ba2c35450fa4).

#### Providers

Soong has Bazel-inspired providers, but providers are not used in all cases yet.

Usages of providers are the easiest, simplest, and cleanest communication
approach in Soong.

In the module providing information, these are specified via
[`SetProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=212;drc=5a34ffb350fb295780e5c373fd1c78430fa4e3ed)
and
[`SetVariationProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/mutator.go;l=719;drc=5a34ffb350fb295780e5c373fd1c78430fa4e3ed).

In the module retrieving information,
[`HasProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=205-206;drc=8631cc7327919845c9d9037188cbd483d22ba077)
and
[`Provider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=198-203;drc=8631cc7327919845c9d9037188cbd483d22ba077)
or
[`OtherModuleHasProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=195-196;drc=8631cc7327919845c9d9037188cbd483d22ba077)
and
[`OtherModuleProvider`](https://cs.android.com/android/platform/superproject/+/master:build/soong/android/module.go;l=189-193;drc=8631cc7327919845c9d9037188cbd483d22ba077)
are used to test existence and retrieve a provider.

### Bazel communication

Targets primarily communicate with each other via providers in Bazel rule
implementations. All rules have access to any of the providers but rules will
pick and choose which ones to access based on their needs. For example, all
rules can access `JavaInfo` provider, which provides information about compile
and rolled-up runtime jars for javac and java invocations downstream. However,
the `JavaInfo` provider is only useful to `java_*` rules or rules that need jvm
information.

#### Starlark rules

[Providers](https://docs.bazel.build/versions/main/skylark/rules.html#providers)
are pieces of information exposed to other modules.

One such provider is `DefaultInfo`, which contains the default output files and
[`runfiles`](https://docs.bazel.build/versions/main/skylark/rules.html#runfiles).

Rule authors can also create
[custom providers](https://docs.bazel.build/versions/main/skylark/lib/Provider.html#modules.Provider)
or implement existing providers to communicate information specific to their
rule logic. For instance, in Android Starlark
[`cc_object`](http://cs/android/build/bazel/rules/cc_object.bzl?l=86-87&rcl=42607e831f8ff73c82825b663609cafb777c18e1)
rule implementation, we return a
[`CcInfo`](https://docs.bazel.build/versions/main/skylark/lib/CcInfo.html)
provider and a custom
[`CcObjectInfo`](http://cs/android/build/bazel/rules/cc_object.bzl?l=17-21&rcl=42607e831f8ff73c82825b663609cafb777c18e1)
provider.

#### Native rules

For implementation of native rules in Java,
[`ruleContext.getPrerequisite`](https://github.com/bazelbuild/bazel/blob/a20b32690a71caf712d1d241f01fef16649562ba/src/main/java/com/google/devtools/build/lib/analysis/RuleContext.java#L911-L983)
is used to extract providers from dependencies.

#### `depset` construction

[`depset`](https://docs.bazel.build/versions/main/glossary.html#depset) are used
in conjunction with providers to accumulate data efficiently from transitive
dependencies. used to accumulate data from transitive dependencies.

#### `exports`

Some target have an `exports` attribute by convention, like
[`java_library.exports`](https://docs.bazel.build/versions/main/be/java.html#java_import.exports).
This attribute is commonly used to propagate transitive dependencies to the
dependent as though the dependent has a direct edge to the transitive
dependencies.
