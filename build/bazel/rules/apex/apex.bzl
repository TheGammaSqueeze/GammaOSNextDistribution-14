# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@soong_injection//apex_toolchain:constants.bzl", "default_manifest_version")
load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load("//build/bazel/rules:build_fingerprint.bzl", "BuildFingerprintInfo")
load("//build/bazel/rules:common.bzl", "get_dep_targets")
load("//build/bazel/rules:metadata.bzl", "MetadataFileInfo")
load("//build/bazel/rules:prebuilt_file.bzl", "PrebuiltFileInfo")
load("//build/bazel/rules:sh_binary.bzl", "ShBinaryInfo")
load("//build/bazel/rules:toolchain_utils.bzl", "verify_toolchain_exists")
load("//build/bazel/rules/android:android_app_certificate.bzl", "AndroidAppCertificateInfo", "NoAndroidAppCertificateInfo", "android_app_certificate_with_default_cert")
load("//build/bazel/rules/android:manifest_fixer.bzl", "manifest_fixer")
load("//build/bazel/rules/apex:cc.bzl", "ApexCcInfo", "ApexCcMkInfo", "apex_cc_aspect")
load("//build/bazel/rules/apex:sdk_versions.bzl", "maybe_override_min_sdk_version")
load("//build/bazel/rules/apex:transition.bzl", "apex_transition", "shared_lib_transition_32", "shared_lib_transition_64")
load("//build/bazel/rules/cc:clang_tidy.bzl", "collect_deps_clang_tidy_info")
load("//build/bazel/rules/cc:stripped_cc_common.bzl", "CcUnstrippedInfo", "StrippedCcBinaryInfo")
load("//build/bazel/rules/common:api.bzl", "api")
load(
    "//build/bazel/rules/license:license_aspect.bzl",
    "RuleLicensedDependenciesInfo",
    "license_aspect",
    "license_map",
    "license_map_notice_files",
    "license_map_to_json",
)
load(":apex_available.bzl", "ApexAvailableInfo", "apex_available_aspect")
load(":apex_deps_validation.bzl", "ApexDepsInfo", "apex_deps_validation_aspect", "validate_apex_deps")
load(":apex_info.bzl", "ApexInfo", "ApexMkInfo")
load(":apex_key.bzl", "ApexKeyInfo")
load(":bundle.bzl", "apex_zip_files")

def _create_file_mapping(ctx):
    """Create a file mapping for the APEX filesystem image.

    This returns a Dict[File, str] where the dictionary keys are paths in the
    apex staging dir / filesystem image, and the values are the files and other
    metadata that should be installed there.

    It also returns other data structures, such as:
    - requires: libs that this apex depend on from other apex or the platform
    - provides: libs that this apex provide to other apex or the platform
    - make_modules_to_install: make module names of libs that needs to be installed onto the platform in a bundled build (LOCAL_REQUIRED_MODULES)
    - make_files_info: metadata about this apex's payload to be used for other packaging steps.
    """

    # Dictionary mapping from paths in the apex to the files and associated metadata to be put there
    file_mapping = {}
    requires = {}
    provides = {}
    make_modules_to_install = {}
    metadata_file_mapping = {}

    # Generate a str -> str dictionary to define Make modules and variables for the
    # packaging step in a mixed build. This is necessary as long as there are
    # Make-derived actions that operate on bazel's outputs. If we move all Make
    # packaging actions to Bazel, there's no need for this data flow.
    make_files_info = {}

    arch = platforms.get_target_arch(ctx.attr._platform_utils)
    is_target_64_bit = platforms.get_target_bitness(ctx.attr._platform_utils) == 64

    def add_file_mapping(install_dir, basename, bazel_file, klass, owner, arch = None, unstripped = None, metadata_file = None):
        installed_path = paths.join(install_dir, basename)
        if installed_path in file_mapping and file_mapping[installed_path] != bazel_file:
            # TODO: we should figure this out and make it a failure
            print("Warning: %s in this apex is already installed to %s, overwriting it with %s" %
                  (file_mapping[installed_path].path, installed_path, bazel_file.path))
        file_mapping[installed_path] = bazel_file
        metadata_file_mapping[installed_path] = metadata_file

        files_info = {
            "built_file": bazel_file.path,
            "class": klass,
            "install_dir": install_dir,
            "basename": basename,
            "package": owner.package,
            "make_module_name": owner.name,
            "arch": arch,
        }
        if unstripped:
            files_info["unstripped_built_file"] = unstripped.path
        make_files_info[installed_path] = files_info

    def _add_lib_files(directory, libs, arch):
        for dep in libs:
            apex_cc_info = dep[ApexCcInfo]
            for lib in apex_cc_info.requires_native_libs.to_list():
                requires[lib] = True
            for lib in apex_cc_info.provides_native_libs.to_list():
                provides[lib] = True
            for lib_file in apex_cc_info.transitive_shared_libs.to_list():
                stripped = lib_file.stripped
                unstripped = lib_file.unstripped
                add_file_mapping(
                    directory,
                    stripped.basename,
                    stripped,
                    "nativeSharedLib",
                    lib_file.generating_rule_owner,
                    arch = arch,
                    unstripped = unstripped,
                    metadata_file = lib_file.metadata_file,
                )

            # For bundled builds.
            apex_cc_mk_info = dep[ApexCcMkInfo]
            for mk_module in apex_cc_mk_info.make_modules_to_install.to_list():
                make_modules_to_install[mk_module] = True

    if is_target_64_bit:
        _add_lib_files("lib64", ctx.attr.native_shared_libs_64, arch)

        secondary_arch = platforms.get_target_secondary_arch(ctx.attr._platform_utils)
        if secondary_arch:
            _add_lib_files("lib", ctx.attr.native_shared_libs_32, secondary_arch)
    else:
        _add_lib_files("lib", ctx.attr.native_shared_libs_32, arch)

    backing_libs = []
    for lib in file_mapping.values():
        if lib.basename not in backing_libs:
            backing_libs.append(lib.basename)
    backing_libs = sorted(backing_libs)

    # Handle prebuilts
    for dep in ctx.attr.prebuilts:
        prebuilt_file_info = dep[PrebuiltFileInfo]
        if prebuilt_file_info.filename:
            filename = prebuilt_file_info.filename
        else:
            filename = dep.label.name
        add_file_mapping(
            prebuilt_file_info.dir,
            filename,
            prebuilt_file_info.src,
            "etc",
            dep.label,
            arch = arch,
            metadata_file = dep[MetadataFileInfo].metadata_file,
        )

    # Handle binaries
    for dep in ctx.attr.binaries:
        if ShBinaryInfo in dep:
            # sh_binary requires special handling on directory/filename construction.
            sh_binary_info = dep[ShBinaryInfo]
            if sh_binary_info:
                directory = "bin"
                if sh_binary_info.sub_dir:
                    directory = paths.join("bin", sh_binary_info.sub_dir)

                filename = dep.label.name
                if sh_binary_info.filename:
                    filename = sh_binary_info.filename

                add_file_mapping(
                    directory,
                    filename,
                    dep[DefaultInfo].files_to_run.executable,
                    "shBinary",
                    dep.label,
                    arch = arch,
                    metadata_file = dep[MetadataFileInfo].metadata_file,
                )
        elif ApexCcInfo in dep:
            # cc_binary just takes the final executable from the runfiles.
            add_file_mapping(
                "bin",
                dep.label.name,
                dep[DefaultInfo].files_to_run.executable,
                "nativeExecutable",
                dep.label,
                arch,
                unstripped = dep[CcUnstrippedInfo].unstripped[0].files.to_list()[0],
                metadata_file = dep[MetadataFileInfo].metadata_file,
            )

            # Add transitive shared lib deps of apex binaries to the apex.
            if is_target_64_bit:
                _add_lib_files("lib64", [dep], arch)
            else:
                _add_lib_files("lib", [dep], arch)

    return (
        file_mapping,
        sorted(requires.keys(), key = lambda x: x.name),  # sort on just the name of the target, not package
        sorted(provides.keys(), key = lambda x: x.name),
        backing_libs,
        sorted(make_modules_to_install),
        sorted(make_files_info.values(), key = lambda x: ":".join([x["package"], x["make_module_name"], x["arch"]])),
        metadata_file_mapping,
    )

def _add_so(label):
    return label.name + ".so"

def _add_apex_manifest_information(
        ctx,
        apex_toolchain,
        requires_native_libs,
        provides_native_libs):
    apex_manifest_json = ctx.file.manifest
    apex_manifest_full_json = ctx.actions.declare_file(ctx.attr.name + "_apex_manifest_full.json")

    args = ctx.actions.args()
    args.add(apex_manifest_json)
    args.add_all(["-a", "requireNativeLibs"])
    args.add_all(requires_native_libs, map_each = _add_so)  # e.g. turn "//foo/bar:baz" to "baz.so"
    args.add_all(["-a", "provideNativeLibs"])
    args.add_all(provides_native_libs, map_each = _add_so)

    manifest_version = default_manifest_version
    if ctx.attr.variant_version != "":
        manifest_version = manifest_version + int(ctx.attr.variant_version)
    override_manifest_version = ctx.attr._override_apex_manifest_default_version[BuildSettingInfo].value
    if override_manifest_version:
        manifest_version = override_manifest_version
    args.add_all(["-se", "version", "0", manifest_version])

    # TODO: support other optional flags like -v name and -a jniLibs
    args.add_all(["-o", apex_manifest_full_json])

    ctx.actions.run(
        inputs = [apex_manifest_json],
        outputs = [apex_manifest_full_json],
        executable = apex_toolchain.jsonmodify[DefaultInfo].files_to_run,
        arguments = [args],
        mnemonic = "ApexManifestModify",
    )

    return apex_manifest_full_json

# conv_apex_manifest - Convert the JSON APEX manifest to protobuf, which is needed by apexer.
def _convert_apex_manifest_json_to_pb(ctx, apex_toolchain, apex_manifest_json):
    apex_manifest_pb = ctx.actions.declare_file(ctx.attr.name + "_apex_manifest.pb")

    ctx.actions.run(
        outputs = [apex_manifest_pb],
        inputs = [apex_manifest_json],
        executable = apex_toolchain.conv_apex_manifest[DefaultInfo].files_to_run,
        arguments = [
            "proto",
            apex_manifest_json.path,
            "-o",
            apex_manifest_pb.path,
        ],
        mnemonic = "ConvApexManifest",
    )

    return apex_manifest_pb

def _generate_canned_fs_config(ctx, filepaths):
    """Generate filesystem config.

    This encodes the filemode, uid, and gid of each file in the APEX,
    including apex_manifest.json and apex_manifest.pb.
    NOTE: every file must have an entry.
    """

    # Ensure all paths don't start with / and are normalized
    filepaths = [paths.normalize(f).lstrip("/") for f in filepaths]

    # Soong also sorts the config lines to be consistent with bazel
    filepaths = sorted([f for f in filepaths if f])

    # First, collect a set of all the directories in the apex
    apex_subdirs_set = {}
    for f in filepaths:
        d = paths.dirname(f)
        if d != "":  # The root dir is handled manually below
            # Make sure all the parent dirs of the current subdir are in the set, too
            dirs = d.split("/")
            for i in range(1, len(dirs) + 1):
                apex_subdirs_set["/".join(dirs[:i])] = True

    config_lines = []
    config_lines.append("/ 1000 1000 0755")
    config_lines.append("/apex_manifest.json 1000 1000 0644")
    config_lines.append("/apex_manifest.pb 1000 1000 0644")

    # Readonly if not executable. filepaths is already sorted.
    config_lines += ["/" + f + " 1000 1000 0644" for f in filepaths if not f.startswith("bin/")]

    # Mark all binaries as executable. filepaths is already sorted.
    config_lines += ["/" + f + " 0 2000 0755" for f in filepaths if f.startswith("bin/")]

    # All directories have the same permission.
    config_lines += ["/" + d + " 0 2000 0755" for d in sorted(apex_subdirs_set.keys())]

    output = ctx.actions.declare_file(ctx.attr.name + "_canned_fs_config.txt")

    config_lines = "\n".join(config_lines) + "\n"
    ctx.actions.write(output, config_lines)

    if ctx.attr.canned_fs_config:
        # Append the custom fs config content to the existing file
        combined_output = ctx.actions.declare_file(ctx.attr.name + "_combined_canned_fs_config.txt")
        ctx.actions.run_shell(
            inputs = [ctx.file.canned_fs_config, output],
            outputs = [combined_output],
            mnemonic = "AppendCustomFsConfig",
            command = "cat {i} {canned_fs_config} > {o}".format(
                i = output.path,
                o = combined_output.path,
                canned_fs_config = ctx.file.canned_fs_config.path,
            ),
        )
        output = combined_output

    return output

# Append an entry for apex_manifest.pb to the file_contexts file for this APEX,
# which is either from /system/sepolicy/apex/<apexname>-file_contexts (set in
# the apex macro) or custom file_contexts attribute value of this APEX. This
# ensures that the manifest file is correctly labeled as system_file.
def _generate_file_contexts(ctx):
    file_contexts = ctx.actions.declare_file(ctx.attr.name + "-file_contexts")

    ctx.actions.run_shell(
        inputs = [ctx.file.file_contexts],
        outputs = [file_contexts],
        mnemonic = "GenerateApexFileContexts",
        command = "cat {i} > {o} && echo >> {o} && echo /apex_manifest\\\\.pb u:object_r:system_file:s0 >> {o} && echo / u:object_r:system_file:s0 >> {o}"
            .format(i = ctx.file.file_contexts.path, o = file_contexts.path),
    )

    return file_contexts

def _mark_manifest_as_test_only(ctx, apex_toolchain):
    android_manifest = ctx.file.android_manifest
    dir_name = android_manifest.dirname
    base_name = android_manifest.basename
    android_manifest_fixed = ctx.actions.declare_file(paths.join(dir_name, "manifest_fixer", base_name))
    manifest_fixer.fix(
        ctx,
        manifest_fixer = apex_toolchain.manifest_fixer[DefaultInfo].files_to_run,
        in_manifest = android_manifest,
        out_manifest = android_manifest_fixed,
        mnemonic = "MarkAndroidManifestTestOnly",
        test_only = True,
    )
    return android_manifest_fixed

# Generate <APEX>_backing.txt file which lists all libraries used by the APEX.
def _generate_apex_backing_file(ctx, backing_libs):
    backing_file = ctx.actions.declare_file(ctx.attr.name + "_backing.txt")
    ctx.actions.write(
        output = backing_file,
        content = " ".join(backing_libs) + "\n",
    )
    return backing_file

# Generate installed-files.txt which lists all installed files by the APEX.
def _generate_installed_files_list(ctx, file_mapping):
    installed_files = ctx.actions.declare_file(ctx.attr.name + "-installed-files.txt")
    command = []
    for device_path, bazel_file in file_mapping.items():
        command.append("echo $(stat -L -c %%s %s) ./%s" % (bazel_file.path, device_path))
    ctx.actions.run_shell(
        inputs = file_mapping.values(),
        outputs = [installed_files],
        mnemonic = "GenerateApexInstalledFileList",
        command = "(" + "; ".join(command) + ") | sort -nr > " + installed_files.path,
    )
    return installed_files

def _generate_notices(ctx, apex_toolchain):
    licensees = license_map(ctx.attr.binaries + ctx.attr.prebuilts + ctx.attr.native_shared_libs_32 + ctx.attr.native_shared_libs_64)
    licenses_file = ctx.actions.declare_file(ctx.attr.name + "_licenses.json")
    ctx.actions.write(licenses_file, "[\n%s\n]\n" % ",\n".join(license_map_to_json(licensees)))

    # Run HTML notice file generator.
    notice_file = ctx.actions.declare_file(ctx.attr.name + "_notice_dir/NOTICE.html.gz")
    notice_generator = apex_toolchain.notice_generator[DefaultInfo].files_to_run

    args = ctx.actions.args()
    args.add_all(["-o", notice_file, licenses_file])

    # TODO(asmundak): should we extend it with license info for self
    # (the case when APEX itself has applicable_licenses attribute)?
    inputs = license_map_notice_files(licensees) + [licenses_file]
    ctx.actions.run(
        mnemonic = "GenerateNoticeFile",
        inputs = inputs,
        outputs = [notice_file],
        executable = notice_generator,
        tools = [notice_generator],
        arguments = [args],
    )
    return notice_file

def _use_api_fingerprint(ctx):
    if not ctx.attr._unbundled_build[BuildSettingInfo].value:
        return False
    if ctx.attr._always_use_prebuilt_sdks[BuildSettingInfo].value:
        return False
    if not ctx.attr._unbundled_build_target_sdk_with_api_fingerprint[BuildSettingInfo].value:
        return False
    return True

# apexer - generate the APEX file.
def _run_apexer(ctx, apex_toolchain):
    # Inputs
    apex_key_info = ctx.attr.key[ApexKeyInfo]
    privkey = apex_key_info.private_key
    pubkey = apex_key_info.public_key
    android_jar = apex_toolchain.android_jar

    file_mapping, requires_native_libs, provides_native_libs, backing_libs, make_modules_to_install, make_files_info, metadata_file_mapping = _create_file_mapping(ctx)
    canned_fs_config = _generate_canned_fs_config(ctx, file_mapping.keys())
    file_contexts = _generate_file_contexts(ctx)
    full_apex_manifest_json = _add_apex_manifest_information(ctx, apex_toolchain, requires_native_libs, provides_native_libs)
    apex_manifest_pb = _convert_apex_manifest_json_to_pb(ctx, apex_toolchain, full_apex_manifest_json)
    notices_file = _generate_notices(ctx, apex_toolchain)
    api_fingerprint_file = None

    staging_dir_builder_options_file = ctx.actions.declare_file(ctx.attr.name + "_staging_dir_builder_options.json")
    ctx.actions.write(staging_dir_builder_options_file, json.encode({
        "file_mapping": {k: v.path for k, v in file_mapping.items()},
    }))

    # Outputs
    apex_output_file = ctx.actions.declare_file(ctx.attr.name + ".apex.unsigned")

    apexer_files = apex_toolchain.apexer[DefaultInfo].files_to_run

    # Arguments
    command = [ctx.executable._staging_dir_builder.path, staging_dir_builder_options_file.path]

    # start of apexer cmd
    command.append(apexer_files.executable.path)

    command.append("--force")
    command.append("--include_build_info")
    command.extend(["--canned_fs_config", canned_fs_config.path])
    command.extend(["--manifest", apex_manifest_pb.path])
    command.extend(["--file_contexts", file_contexts.path])
    command.extend(["--key", privkey.path])
    command.extend(["--pubkey", pubkey.path])
    command.extend(["--payload_type", "image"])
    command.extend(["--payload_fs_type", "ext4"])
    command.extend(["--assets_dir", notices_file.dirname])

    # Override the package name, if it's expicitly specified
    if ctx.attr.package_name:
        command.extend(["--override_apk_package_name", ctx.attr.package_name])
    else:
        override_package_name = _override_manifest_package_name(ctx)
        if override_package_name:
            command.extend(["--override_apk_package_name", override_package_name])

    if ctx.attr.logging_parent:
        command.extend(["--logging_parent", ctx.attr.logging_parent])

    use_api_fingerprint = _use_api_fingerprint(ctx)

    target_sdk_version = str(api.final_or_future(api.default_app_target_sdk()))
    if use_api_fingerprint:
        api_fingerprint_file = ctx.file._api_fingerprint_txt
        sdk_version_suffix = ".$(cat {})".format(api_fingerprint_file.path)
        target_sdk_version = ctx.attr._platform_sdk_codename[BuildSettingInfo].value + sdk_version_suffix

    command.extend(["--target_sdk_version", target_sdk_version])

    # TODO(b/215339575): This is a super rudimentary way to convert "current" to a numerical number.
    # Generalize this to API level handling logic in a separate Starlark utility, preferably using
    # API level maps dumped from api_levels.go
    min_sdk_version = ctx.attr.min_sdk_version
    if min_sdk_version == "current":
        min_sdk_version = "10000"

    override_min_sdk_version = ctx.attr._apex_global_min_sdk_version_override[BuildSettingInfo].value
    min_sdk_version = str(maybe_override_min_sdk_version(min_sdk_version, override_min_sdk_version))

    if min_sdk_version == "10000" and use_api_fingerprint:
        min_sdk_version = ctx.attr._platform_sdk_codename[BuildSettingInfo].value + sdk_version_suffix
        command.append(api_fingerprint_file.path)
    command.extend(["--min_sdk_version", min_sdk_version])

    # apexer needs the list of directories containing all auxilliary tools invoked during
    # the creation of an apex
    avbtool_files = apex_toolchain.avbtool[DefaultInfo].files_to_run
    e2fsdroid_files = apex_toolchain.e2fsdroid[DefaultInfo].files_to_run
    mke2fs_files = apex_toolchain.mke2fs[DefaultInfo].files_to_run
    resize2fs_files = apex_toolchain.resize2fs[DefaultInfo].files_to_run
    sefcontext_compile_files = apex_toolchain.sefcontext_compile[DefaultInfo].files_to_run
    staging_dir_builder_files = ctx.attr._staging_dir_builder[DefaultInfo].files_to_run
    apexer_tool_paths = [
        apex_toolchain.aapt2.dirname,
        apexer_files.executable.dirname,
        avbtool_files.executable.dirname,
        e2fsdroid_files.executable.dirname,
        mke2fs_files.executable.dirname,
        resize2fs_files.executable.dirname,
        sefcontext_compile_files.executable.dirname,
    ]

    command.extend(["--apexer_tool_path", ":".join(apexer_tool_paths)])

    android_manifest = ctx.file.android_manifest
    if android_manifest != None:
        if ctx.attr.testonly:
            android_manifest = _mark_manifest_as_test_only(ctx, apex_toolchain)
        command.extend(["--android_manifest", android_manifest.path])
    elif ctx.attr.testonly:
        command.append("--test_only")

    command.append("STAGING_DIR_PLACEHOLDER")
    command.append(apex_output_file.path)

    inputs = [
        ctx.executable._staging_dir_builder,
        staging_dir_builder_options_file,
        canned_fs_config,
        apex_manifest_pb,
        file_contexts,
        notices_file,
        privkey,
        pubkey,
        android_jar,
    ] + file_mapping.values()
    if use_api_fingerprint:
        inputs.append(api_fingerprint_file)

    if android_manifest != None:
        inputs.append(android_manifest)

    # This is run_shell instead of run because --target_sdk_version may
    # use the API fingerprinting file contents using bash expansion,
    # and only run_shell can support that by executing the whole command with
    # /bin/bash -c. Regular run would quote the --target_sdk_version value with
    # single quotes ('--target_sdk_version=ABC.$(cat version.txt)'), preventing
    # bash expansion.
    ctx.actions.run_shell(
        inputs = inputs,
        tools = [
            apexer_files,
            avbtool_files,
            e2fsdroid_files,
            mke2fs_files,
            resize2fs_files,
            sefcontext_compile_files,
            apex_toolchain.aapt2,
            staging_dir_builder_files,
        ],
        outputs = [apex_output_file],
        command = " ".join(command),
        mnemonic = "Apexer",
    )
    return struct(
        unsigned_apex = apex_output_file,
        requires_native_libs = requires_native_libs,
        provides_native_libs = provides_native_libs,
        backing_libs = _generate_apex_backing_file(ctx, backing_libs),
        symbols_used_by_apex = _generate_symbols_used_by_apex(ctx, apex_toolchain, file_mapping),
        java_symbols_used_by_apex = _generate_java_symbols_used_by_apex(ctx, apex_toolchain),
        installed_files = _generate_installed_files_list(ctx, file_mapping),
        make_modules_to_install = make_modules_to_install,
        make_files_info = make_files_info,
        file_mapping = file_mapping,
        metadata_file_mapping = metadata_file_mapping,
    )

def _run_signapk(ctx, unsigned_file, signed_file, private_key, public_key, mnemonic):
    """Sign a file with signapk."""

    # Arguments
    args = ctx.actions.args()
    args.add_all(["-a", 4096])
    args.add_all(["--align-file-size"])
    args.add_all([public_key, private_key])
    args.add_all([unsigned_file, signed_file])

    ctx.actions.run(
        inputs = [
            unsigned_file,
            private_key,
            public_key,
            ctx.executable._signapk,
        ],
        outputs = [signed_file],
        executable = ctx.executable._signapk,
        arguments = [args],
        mnemonic = mnemonic,
    )

    return signed_file

# See also getOverrideManifestPackageName
# https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/builder.go;l=1000;drc=241e738c7156d928e9a993b15993cb3297face45
def _override_manifest_package_name(ctx):
    apex_name = ctx.attr.name
    overrides = ctx.attr._manifest_package_name_overrides[BuildSettingInfo].value
    if not overrides:
        return None

    matches = [o for o in overrides if o.split(":")[0] == apex_name]

    if not matches:
        return None

    if len(matches) > 1:
        fail("unexpected multiple manifest package overrides for %s, %s" % (apex_name, matches))

    return matches[0].split(":")[1]

# https://cs.android.com/android/platform/superproject/+/master:build/soong/android/config.go;drc=5ca657189aac546af0aafaba11bbc9c5d889eab3;l=1501
# In Soong, we don't check whether the current apex is part of Unbundled_apps.
# Hence, we might simplify the logic by just checking product_vars["Unbundled_build"]
# TODO(b/271474456): Eventually we might default to unbundled mode in bazel-only mode
# so that we don't need to check Unbundled_apps.
def _compression_enabled(ctx):
    compressed_apex = ctx.attr._compressed_apex[BuildSettingInfo].value
    unbundled_apps = ctx.attr._unbundled_build_apps[BuildSettingInfo].value

    return compressed_apex and len(unbundled_apps) == 0

# Compress a file with apex_compression_tool.
def _run_apex_compression_tool(ctx, apex_toolchain, input_file, output_file_name):
    avbtool_files = apex_toolchain.avbtool[DefaultInfo].files_to_run
    apex_compression_tool_files = apex_toolchain.apex_compression_tool[DefaultInfo].files_to_run

    # Outputs
    compressed_file = ctx.actions.declare_file(output_file_name)

    # Arguments
    args = ctx.actions.args()
    args.add_all(["compress"])
    tool_dirs = [apex_toolchain.soong_zip.dirname, avbtool_files.executable.dirname]
    args.add_all(["--apex_compression_tool", ":".join(tool_dirs)])
    args.add_all(["--input", input_file])
    args.add_all(["--output", compressed_file])

    ctx.actions.run(
        inputs = [input_file],
        tools = [
            avbtool_files,
            apex_compression_tool_files,
            apex_toolchain.soong_zip,
        ],
        outputs = [compressed_file],
        executable = apex_compression_tool_files,
        arguments = [args],
        mnemonic = "BazelApexCompressing",
    )
    return compressed_file

# Generate <module>_using.txt, which contains a list of versioned NDK symbols
# dynamically linked to by this APEX's contents. This is used for coverage
# checks.
def _generate_symbols_used_by_apex(ctx, apex_toolchain, file_mapping):
    staging_dir_builder_options_file = ctx.actions.declare_file(ctx.attr.name + "_generate_symbols_used_by_apex_staging_dir_builder_options.json")
    ctx.actions.write(staging_dir_builder_options_file, json.encode({
        "file_mapping": {k: v.path for k, v in file_mapping.items()},
    }))
    symbols_used_by_apex = ctx.actions.declare_file(ctx.attr.name + "_using.txt")
    ctx.actions.run(
        outputs = [symbols_used_by_apex],
        inputs = [staging_dir_builder_options_file] + file_mapping.values(),
        tools = [
            apex_toolchain.readelf.files_to_run,
            apex_toolchain.gen_ndk_usedby_apex.files_to_run,
        ],
        executable = ctx.executable._staging_dir_builder,
        arguments = [
            staging_dir_builder_options_file.path,
            apex_toolchain.gen_ndk_usedby_apex.files_to_run.executable.path,
            "STAGING_DIR_PLACEHOLDER",
            apex_toolchain.readelf.files_to_run.executable.path,
            symbols_used_by_apex.path,
        ],
        progress_message = "Generating dynamic NDK symbol list used by the %s apex" % ctx.attr.name,
        mnemonic = "ApexUsingNDKSymbolsForCoverage",
    )
    return symbols_used_by_apex

# Generate <module>_using.xml, which contains a list of java API metadata used
# by this APEX's contents. This is used for coverage checks.
#
# TODO(b/257954111): Add JARs and APKs as inputs to this action when we start
# building Java mainline modules.
def _generate_java_symbols_used_by_apex(ctx, apex_toolchain):
    java_symbols_used_by_apex = ctx.actions.declare_file(ctx.attr.name + "_using.xml")
    ctx.actions.run(
        outputs = [java_symbols_used_by_apex],
        inputs = [],
        tools = [
            apex_toolchain.dexdeps.files_to_run,
            apex_toolchain.gen_java_usedby_apex.files_to_run,
        ],
        executable = apex_toolchain.gen_java_usedby_apex.files_to_run,
        arguments = [
            apex_toolchain.dexdeps.files_to_run.executable.path,
            java_symbols_used_by_apex.path,
        ],
        progress_message = "Generating Java symbol list used by the %s apex" % ctx.attr.name,
        mnemonic = "ApexUsingJavaSymbolsForCoverage",
    )
    return java_symbols_used_by_apex

def _validate_apex_deps(ctx):
    transitive_deps = depset(
        transitive = [
            d[ApexDepsInfo].transitive_deps
            for d in (
                ctx.attr.native_shared_libs_32 +
                ctx.attr.native_shared_libs_64 +
                ctx.attr.binaries +
                ctx.attr.prebuilts
            )
        ],
    )
    validation_files = []
    if not ctx.attr._unsafe_disable_apex_allowed_deps_check[BuildSettingInfo].value:
        validation_files.append(validate_apex_deps(ctx, transitive_deps, ctx.file.allowed_apex_deps_manifest))

    transitive_unvalidated_targets = []
    transitive_invalid_targets = []
    for _, attr_deps in get_dep_targets(ctx.attr, predicate = lambda target: ApexAvailableInfo in target).items():
        for dep in attr_deps:
            transitive_unvalidated_targets.append(dep[ApexAvailableInfo].transitive_unvalidated_targets)
            transitive_invalid_targets.append(dep[ApexAvailableInfo].transitive_invalid_targets)

    invalid_targets = depset(transitive = transitive_invalid_targets).to_list()
    if len(invalid_targets) > 0:
        invalid_targets_msg = "\n    ".join([
            "{label}; apex_available tags: {tags}".format(label = target.label, tags = list(apex_available_tags))
            for target, apex_available_tags in invalid_targets
        ])
        msg = ("`{apex_name}` apex has transitive dependencies that do not include the apex in " +
               "their apex_available tags:\n    {invalid_targets_msg}").format(
            apex_name = ctx.label,
            invalid_targets_msg = invalid_targets_msg,
        )
        fail(msg)

    transitive_unvalidated_targets_output_file = ctx.actions.declare_file(ctx.attr.name + "_unvalidated_deps.txt")
    ctx.actions.write(
        transitive_unvalidated_targets_output_file,
        "\n".join([
            str(label) + ": " + str(reason)
            for label, reason in depset(transitive = transitive_unvalidated_targets).to_list()
        ]),
    )
    return transitive_deps, transitive_unvalidated_targets_output_file, validation_files

def _verify_updatability(ctx):
    # TODO(b/274732759): Add these checks as more APEXes are converted to Bazel.
    #
    # Keep this in sync with build/soong/apex/apex.go#checkUpdatable.
    #
    # - Cannot use platform APIs.
    # - Cannot use external VNDK libs.
    # - Does not set future_updatable.

    if not ctx.attr.min_sdk_version:
        fail("updatable APEXes must set min_sdk_version.")

def _generate_sbom(ctx, file_mapping, metadata_file_mapping, apex_file):
    apex_filename = paths.basename(apex_file.path)
    sbom_metadata_csv = ctx.actions.declare_file(apex_filename + "-sbom-metadata.csv")
    command = []
    metadata_files = []
    sbom_metadata_csv_columns = [
        "installed_file",
        "module_path",
        "soong_module_type",
        "is_prebuilt_make_module",
        "product_copy_files",
        "kernel_module_copy_files",
        "is_platform_generated",
        "build_output_path",
        "static_libraries",
        "whole_static_libraries",
        "is_static_lib",
    ]
    command.append("echo " + ",".join(sbom_metadata_csv_columns))
    command.append("echo %s,%s,,,,,,%s,,," % (apex_filename, ctx.label.package, apex_file.path))
    for installed_file, bazel_output_file in file_mapping.items():
        if metadata_file_mapping[installed_file]:
            metadata_files.append(metadata_file_mapping[installed_file])
        command.append("echo %s,%s,,,,,,%s,,," % (installed_file, paths.dirname(bazel_output_file.short_path), bazel_output_file.path))
    ctx.actions.run_shell(
        inputs = file_mapping.values(),
        outputs = [sbom_metadata_csv],
        mnemonic = "GenerateSBOMMetadata",
        command = "(" + "; ".join(command) + ") > " + sbom_metadata_csv.path,
    )

    sbom_file = ctx.actions.declare_file(apex_filename + ".spdx.json")
    sbom_fragment_file = ctx.actions.declare_file(apex_filename + "-fragment.spdx")
    inputs = [
        apex_file,
        sbom_metadata_csv,
        ctx.executable._generate_sbom,
    ]
    inputs += file_mapping.values()
    inputs += metadata_files

    build_fingerprint = ctx.attr._build_fingerprint[BuildFingerprintInfo].fingerprint_blank_build_number
    ctx.actions.run(
        inputs = inputs,
        outputs = [sbom_file, sbom_fragment_file],
        arguments = [
            "--output_file",
            sbom_file.path,
            "--metadata",
            sbom_metadata_csv.path,
            "--build_version",
            build_fingerprint,
            "--product_mfr",
            ctx.attr._product_manufacturer[BuildSettingInfo].value,
            "--json",
            "--unbundled_apex",
        ],
        mnemonic = "GenerateSBOM",
        executable = ctx.executable._generate_sbom,
    )
    return [sbom_file, sbom_fragment_file]

# See the APEX section in the README on how to use this rule.
def _apex_rule_impl(ctx):
    verify_toolchain_exists(ctx, "//build/bazel/rules/apex:apex_toolchain_type")
    if ctx.attr.updatable:
        _verify_updatability(ctx)

    apex_toolchain = ctx.toolchains["//build/bazel/rules/apex:apex_toolchain_type"].toolchain_info

    apexer_outputs = _run_apexer(ctx, apex_toolchain)
    unsigned_apex = apexer_outputs.unsigned_apex

    if AndroidAppCertificateInfo in ctx.attr.certificate_override[0]:
        apex_cert_info = ctx.attr.certificate_override[0][AndroidAppCertificateInfo]
    else:
        apex_cert_info = ctx.attr.certificate[0][AndroidAppCertificateInfo]

    private_key = apex_cert_info.pk8
    public_key = apex_cert_info.pem

    signed_apex = ctx.actions.declare_file(ctx.attr.name + ".apex")
    signed_capex = None

    _run_signapk(ctx, unsigned_apex, signed_apex, private_key, public_key, "BazelApexSigning")

    if ctx.attr.compressible and _compression_enabled(ctx):
        compressed_apex_output_file = _run_apex_compression_tool(ctx, apex_toolchain, signed_apex, ctx.attr.name + ".capex.unsigned")
        signed_capex = ctx.actions.declare_file(ctx.attr.name + ".capex")
        _run_signapk(ctx, compressed_apex_output_file, signed_capex, private_key, public_key, "BazelCompressedApexSigning")

    apex_key_info = ctx.attr.key[ApexKeyInfo]

    arch = platforms.get_target_arch(ctx.attr._platform_utils)
    zip_files = apex_zip_files(
        actions = ctx.actions,
        name = ctx.label.name,
        tools = struct(
            aapt2 = apex_toolchain.aapt2,
            zip2zip = ctx.executable._zip2zip,
            merge_zips = ctx.executable._merge_zips,
            soong_zip = apex_toolchain.soong_zip,
        ),
        apex_file = signed_apex,
        arch = arch,
        secondary_arch = platforms.get_target_secondary_arch(ctx.attr._platform_utils),
    )

    transitive_apex_deps, transitive_unvalidated_targets_output_file, apex_deps_validation_files = _validate_apex_deps(ctx)

    optional_output_groups = {}
    if signed_capex:
        optional_output_groups["signed_compressed_output"] = [signed_capex]

    return [
        DefaultInfo(files = depset([signed_apex])),
        ApexInfo(
            signed_output = signed_apex,
            signed_compressed_output = signed_capex,
            unsigned_output = unsigned_apex,
            requires_native_libs = apexer_outputs.requires_native_libs,
            provides_native_libs = apexer_outputs.provides_native_libs,
            bundle_key_info = apex_key_info,
            container_key_info = apex_cert_info,
            package_name = ctx.attr.package_name,
            backing_libs = apexer_outputs.backing_libs,
            symbols_used_by_apex = apexer_outputs.symbols_used_by_apex,
            installed_files = apexer_outputs.installed_files,
            java_symbols_used_by_apex = apexer_outputs.java_symbols_used_by_apex,
            base_file = zip_files.apex_only,
            base_with_config_zip = zip_files.apex_with_config,
        ),
        OutputGroupInfo(
            coverage_files = [apexer_outputs.symbols_used_by_apex],
            java_coverage_files = [apexer_outputs.java_symbols_used_by_apex],
            backing_libs = depset([apexer_outputs.backing_libs]),
            installed_files = depset([apexer_outputs.installed_files]),
            transitive_unvalidated_targets = depset([transitive_unvalidated_targets_output_file]),
            apex_sbom = depset(_generate_sbom(ctx, apexer_outputs.file_mapping, apexer_outputs.metadata_file_mapping, signed_apex)),
            capex_sbom = depset(_generate_sbom(ctx, apexer_outputs.file_mapping, apexer_outputs.metadata_file_mapping, signed_capex) if signed_capex else []),
            _validation = apex_deps_validation_files,
            **optional_output_groups
        ),
        ApexDepsInfo(transitive_deps = transitive_apex_deps),
        ApexMkInfo(
            make_modules_to_install = apexer_outputs.make_modules_to_install,
            files_info = apexer_outputs.make_files_info,
        ),
        collect_deps_clang_tidy_info(ctx),
    ]

# These are the standard aspects that should be applied on all edges that
# contribute to an APEX's payload.
STANDARD_PAYLOAD_ASPECTS = [
    license_aspect,
    apex_available_aspect,
    apex_deps_validation_aspect,
]

_apex = rule(
    implementation = _apex_rule_impl,
    attrs = {
        # Attributes that configure the APEX container.
        "manifest": attr.label(allow_single_file = [".json"]),
        "android_manifest": attr.label(allow_single_file = [".xml"]),
        "package_name": attr.string(),
        "logging_parent": attr.string(),
        "file_contexts": attr.label(allow_single_file = True, mandatory = True),
        "canned_fs_config": attr.label(
            allow_single_file = True,
            doc = """Path to the canned fs config file for customizing file's
uid/gid/mod/capabilities. The content of this file is appended to the
default config, so that the custom entries are preferred.

The format is /<path_or_glob> <uid> <gid> <mode> [capabilities=0x<cap>], where
path_or_glob is a path or glob pattern for a file or set of files, uid/gid
are numerial values of user ID and group ID, mode is octal value for the
file mode, and cap is hexadecimal value for the capability.""",
        ),
        "key": attr.label(providers = [ApexKeyInfo], mandatory = True),
        "certificate": attr.label(
            providers = [AndroidAppCertificateInfo],
            mandatory = True,
            cfg = apex_transition,
        ),
        "certificate_override": attr.label(
            providers = [[AndroidAppCertificateInfo], [NoAndroidAppCertificateInfo]],
            mandatory = True,
            cfg = apex_transition,
        ),
        "min_sdk_version": attr.string(
            default = "current",
            doc = """The minimum SDK version that this APEX must support at minimum. This is usually set to
the SDK version that the APEX was first introduced.

When not set, defaults to "10000" (or "current").""",
        ),
        "updatable": attr.bool(default = True, doc = """Whether this APEX is considered updatable or not.

When set to true, this will enforce additional rules for making sure that the
APEX is truly updatable. To be updatable, min_sdk_version should be set as well."""),
        "installable": attr.bool(default = True),
        "compressible": attr.bool(default = False),
        "base_apex_name": attr.string(
            default = "",
            doc = "The name of the base apex of this apex. For example, the AOSP variant of this apex.",
        ),
        "apex_available_name": attr.string(
            default = "",
            doc = "The name that dependencies can specify in their apex_available " +
                  "properties to refer to this module. If not specified, this " +
                  "defaults to Soong module name.",
        ),
        "variant_version": attr.string(
            default = "",
            doc = "Variant version of the mainline module. Must be an integer between 0-9",
            values = [""] + [str(i) for i in range(10)],
        ),

        # Attributes that contribute to the payload.
        "native_shared_libs_32": attr.label_list(
            providers = [ApexCcInfo, ApexCcMkInfo, RuleLicensedDependenciesInfo],
            aspects = STANDARD_PAYLOAD_ASPECTS + [apex_cc_aspect],
            cfg = shared_lib_transition_32,
            doc = "The libs compiled for 32-bit",
        ),
        "native_shared_libs_64": attr.label_list(
            providers = [ApexCcInfo, ApexCcMkInfo, RuleLicensedDependenciesInfo],
            aspects = STANDARD_PAYLOAD_ASPECTS + [apex_cc_aspect],
            cfg = shared_lib_transition_64,
            doc = "The libs compiled for 64-bit",
        ),
        "binaries": attr.label_list(
            providers = [
                # The dependency must produce _all_ of the providers in _one_ of these lists.
                [ShBinaryInfo, RuleLicensedDependenciesInfo],  # sh_binary
                [StrippedCcBinaryInfo, CcInfo, ApexCcInfo, ApexCcMkInfo, RuleLicensedDependenciesInfo],  # cc_binary (stripped)
            ],
            cfg = apex_transition,
            aspects = STANDARD_PAYLOAD_ASPECTS + [apex_cc_aspect],
        ),
        "prebuilts": attr.label_list(
            providers = [PrebuiltFileInfo, RuleLicensedDependenciesInfo],
            cfg = apex_transition,
            aspects = STANDARD_PAYLOAD_ASPECTS,
        ),

        # Required to use apex_transition. This is an acknowledgement to the risks of memory bloat when using transitions.
        "_allowlist_function_transition": attr.label(default = "@bazel_tools//tools/allowlists/function_transition_allowlist"),

        # Tools that are not part of the apex_toolchain.
        "_staging_dir_builder": attr.label(
            cfg = "exec",
            doc = "The staging dir builder to avoid the problem where symlinks are created inside apex image.",
            executable = True,
            default = "//build/bazel/rules:staging_dir_builder",
        ),
        "_signapk": attr.label(
            cfg = "exec",
            doc = "The signapk tool.",
            executable = True,
            default = "//build/make/tools/signapk",
        ),
        "_zip2zip": attr.label(
            cfg = "exec",
            allow_single_file = True,
            doc = "The tool zip2zip. Used to convert apex file to the expected directory structure.",
            default = "//build/soong/cmd/zip2zip:zip2zip",
            executable = True,
        ),
        "_merge_zips": attr.label(
            cfg = "exec",
            allow_single_file = True,
            doc = "The tool merge_zips. Used to combine base zip and config file into a single zip for mixed build aab creation.",
            default = "//build/soong/cmd/merge_zips",
            executable = True,
        ),
        "_platform_utils": attr.label(
            default = "//build/bazel/platforms:platform_utils",
        ),
        "_build_fingerprint": attr.label(
            default = "//build/bazel/rules:build_fingerprint",
        ),
        "_generate_sbom": attr.label(
            cfg = "exec",
            doc = "SBOM generation tool",
            executable = True,
            default = "//build/make/tools/sbom:generate-sbom",
        ),

        # allowed deps check
        "_unsafe_disable_apex_allowed_deps_check": attr.label(
            default = "//build/bazel/rules/apex:unsafe_disable_apex_allowed_deps_check",
        ),
        "allowed_apex_deps_manifest": attr.label(
            allow_single_file = True,
            default = "//packages/modules/common/build:allowed_deps.txt",
        ),

        # Build settings.
        "_always_use_prebuilt_sdks": attr.label(
            default = "//build/bazel/product_config:always_use_prebuilt_sdks",
        ),
        "_apex_global_min_sdk_version_override": attr.label(
            default = "//build/bazel/product_config:apex_global_min_sdk_version_override",
            doc = "If specified, override the min_sdk_version of this apex and in the transition and checks for dependencies.",
        ),
        "_compressed_apex": attr.label(
            default = "//build/bazel/product_config:compressed_apex",
        ),
        "_manifest_package_name_overrides": attr.label(
            default = "//build/bazel/product_config:manifest_package_name_overrides",
        ),
        "_override_apex_manifest_default_version": attr.label(
            default = "//build/bazel/rules/apex:override_apex_manifest_default_version",
            doc = "If specified, override 'version: 0' in apex_manifest.json with this value instead of the branch default. Non-zero versions will not be changed.",
        ),
        "_product_manufacturer": attr.label(
            default = "//build/bazel/product_config:product_manufacturer",
        ),
        "_unbundled_build_apps": attr.label(
            default = "//build/bazel/product_config:unbundled_build_apps",
        ),
        "_unbundled_build": attr.label(
            default = "//build/bazel/product_config:unbundled_build",
        ),

        # Api_fingerprint
        "_unbundled_build_target_sdk_with_api_fingerprint": attr.label(
            default = "//build/bazel/rules/apex:unbundled_build_target_sdk_with_api_fingerprint",
        ),
        "_platform_sdk_codename": attr.label(
            default = "//build/bazel/rules/apex:platform_sdk_codename",
        ),
        "_api_fingerprint_txt": attr.label(
            default = "//frameworks/base/api:api_fingerprint",
            allow_single_file = True,
        ),
    },
    # The apex toolchain is not mandatory so that we don't get toolchain resolution errors even
    # when the apex is not compatible with the current target (via target_compatible_with).
    toolchains = [config_common.toolchain_type("//build/bazel/rules/apex:apex_toolchain_type", mandatory = False)],
    fragments = ["platform"],
)

def apex(
        name,
        manifest = "apex_manifest.json",
        android_manifest = None,
        file_contexts = None,
        key = None,
        certificate = None,
        certificate_name = None,
        min_sdk_version = None,
        updatable = True,
        installable = True,
        compressible = False,
        native_shared_libs_32 = [],
        native_shared_libs_64 = [],
        binaries = [],
        prebuilts = [],
        package_name = None,
        logging_parent = None,
        canned_fs_config = None,
        testonly = False,
        # TODO(b/255400736): tests are not fully supported yet.
        tests = [],
        target_compatible_with = [],
        **kwargs):
    "Bazel macro to correspond with the APEX bundle Soong module."

    # If file_contexts is not specified, then use the default from //system/sepolicy/apex.
    # https://cs.android.com/android/platform/superproject/+/master:build/soong/apex/builder.go;l=259-263;drc=b02043b84d86fe1007afef1ff012a2155172215c
    if file_contexts == None:
        file_contexts = "//system/sepolicy/apex:" + name + "-file_contexts"

    if testonly:
        compressible = False
    elif tests:
        fail("Apex with tests attribute needs to be testonly.")

    if certificate and certificate_name:
        fail("Cannot use both certificate_name and certificate attributes together. Use only one of them.")
    app_cert_name = name + "_app_certificate"
    if certificate_name:
        # use the name key in the default cert dir
        android_app_certificate_with_default_cert(
            name = app_cert_name,
            cert_name = certificate_name,
        )
        certificate_label = ":" + app_cert_name
    elif certificate:
        certificate_label = certificate
    else:
        # use the default testkey
        android_app_certificate_with_default_cert(name = app_cert_name)
        certificate_label = ":" + app_cert_name

    target_compatible_with = select({
        "//build/bazel_common_rules/platforms/os:android": [],
        "//conditions:default": ["@platforms//:incompatible"],
    }) + target_compatible_with

    # This flag will be set based on the value of PRODUCT_CERTIFICATE_OVERRIDES
    native.label_flag(
        name = name + "_certificate_override",
        build_setting_default = "//build/bazel/rules/android:no_android_app_certificate",
    )

    _apex(
        name = name,
        manifest = manifest,
        android_manifest = android_manifest,
        file_contexts = file_contexts,
        key = key,
        certificate = certificate_label,
        certificate_override = ":" + name + "_certificate_override",
        min_sdk_version = min_sdk_version,
        updatable = updatable,
        installable = installable,
        compressible = compressible,
        native_shared_libs_32 = native_shared_libs_32,
        native_shared_libs_64 = native_shared_libs_64,
        binaries = binaries,
        prebuilts = prebuilts,
        package_name = package_name,
        logging_parent = logging_parent,
        canned_fs_config = canned_fs_config,
        testonly = testonly,
        target_compatible_with = target_compatible_with,
        **kwargs
    )
