#!/usr/bin/env lucicfg
#
# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""LUCI project configuration for the production instance of LUCI.

After modifying this file execute it ('./main.star') to regenerate the configs.
"""

lucicfg.check_version("1.30.9", "Please update depot_tools")

luci.builder.defaults.experiments.set({
    "luci.recipes.use_python3": 100,
})

# Use LUCI Scheduler BBv2 names and add Scheduler realms configs.
lucicfg.enable_experiment("crbug.com/1182002")

# Tell lucicfg what files it is allowed to touch.
lucicfg.config(
    config_dir = "generated",
    fail_on_warnings = True,
    lint_checks = ["default"],
)

# TODO: Switch to project-scoped service account.

luci.project(
    name = "art",
    buildbucket = "cr-buildbucket.appspot.com",
    logdog = "luci-logdog.appspot.com",
    milo = "luci-milo.appspot.com",
    notify = "luci-notify.appspot.com",
    scheduler = "luci-scheduler.appspot.com",
    swarming = "chromium-swarm.appspot.com",
    acls = [
        # Publicly readable.
        acl.entry(
            roles = [
                acl.BUILDBUCKET_READER,
                acl.LOGDOG_READER,
                acl.PROJECT_CONFIGS_READER,
                acl.SCHEDULER_READER,
            ],
            groups = "all",
        ),
        acl.entry(
            roles = [
                acl.BUILDBUCKET_OWNER,
                acl.SCHEDULER_OWNER,
            ],
            groups = "project-art-admins",
        ),
        acl.entry(
            roles = acl.LOGDOG_WRITER,
            groups = "luci-logdog-chromium-writers",
        ),
    ],
    bindings = [
        luci.binding(
            roles = "role/swarming.poolOwner",
            groups = "project-art-admins",
        ),
        luci.binding(
            roles = "role/swarming.poolViewer",
            groups = "all",
        ),
    ],
)

# Per-service tweaks.
luci.logdog(gs_bucket = "chromium-luci-logdog")
luci.milo(logo = "https://storage.googleapis.com/chrome-infra-public/logo/art-logo.png")

# Allow admins to use LED and "Debug" button on every builder and bot.
luci.binding(
    realm = "@root",
    roles = "role/swarming.poolUser",
    groups = "project-art-admins",
)
luci.binding(
    realm = "@root",
    roles = "role/swarming.taskTriggerer",
    groups = "project-art-admins",
)

# Resources shared by all subprojects.

luci.realm(name = "pools/ci")
luci.bucket(name = "ci")

luci.notifier_template(
    name = "default",
    body = io.read_file("luci-notify.template"),
)

luci.console_view(
    name = "luci",
    repo = "https://android.googlesource.com/platform/art",
    title = "ART LUCI Console",
    refs = ["refs/heads/master"],
    include_experimental_builds = True,
)

luci.notifier(
    name = "art-team+chromium-buildbot",
    on_new_status = [
        "FAILURE",
        "INFRA_FAILURE",
    ],
    notify_emails = [
        "art-team+chromium-buildbot@google.com",
    ],
)

luci.gitiles_poller(
    name = "art",
    bucket = "ci",
    repo = "https://android.googlesource.com/platform/art",
    refs = ["refs/heads/master"],
)

luci.gitiles_poller(
    name = "libcore",
    bucket = "ci",
    repo = "https://android.googlesource.com/platform/libcore",
    refs = ["refs/heads/master"],
)

luci.gitiles_poller(
    name = "vogar",
    bucket = "ci",
    repo = "https://android.googlesource.com/platform/external/vogar",
    refs = ["refs/heads/master"],
)

luci.gitiles_poller(
    name = "manifest",
    bucket = "ci",
    repo = "https://android.googlesource.com/platform/manifest",
    refs = ["refs/heads/master-art"],
)

def ci_builder(name, category, short_name, dimensions, properties={}, is_fyi=False):
    default_properties = {
        "builder_group": "client.art",
        "concurrent_collector": True,
        "generational_cc": True,
    }

    default_properties = default_properties | properties

    luci.builder(
        name = name,
        bucket = "ci",
        executable = luci.recipe(
            cipd_package = "infra/recipe_bundles/chromium.googlesource.com/chromium/tools/build",
            cipd_version = "refs/heads/main",
            name = "art",
        ),
        dimensions = dimensions | {
            "pool": "luci.art.ci",
        },
        service_account = "art-ci-builder@chops-service-accounts.iam.gserviceaccount.com",

        # Maximum delay between scheduling a build and the build actually starting.
        # In a healthy state (enough free/idle devices), the delay is fairly small,
        # but if enough devices are offline, this timeout will cause INFRA_FAILURE.
        # Set the value reasonably high to prefer delayed builds over failing ones.
        # NB: LUCI also enforces (expiration_timeout + execution_timeout <= 47).
        expiration_timeout = 17 * time.hour,
        execution_timeout = 30 * time.hour,
        build_numbers = True,
        properties = default_properties,
        caches = [
            # Directory called "art" that persists from build to build (one per bot).
            # We can checkout and build in this directory to get fast incremental builds.
            swarming.cache("art", name = "art"),
        ],
        notifies = ["art-team+chromium-buildbot"],
        triggered_by = [
            "art",
            "libcore",
            "manifest",
            "vogar",
        ],
    )
    if not is_fyi:
        luci.console_view_entry(
            console_view = "luci",
            builder = name,
            category = category,
            short_name = short_name,
        )

def target_builders():
    target_dims = {"os": "Android"}
    # userfault-GC configurations must be run on Pixel 6.
    userfault_gc_target_dims = target_dims | {"device_type": "oriole"}

    ci_builder(
        name="angler-armv7-debug",
        category="angler|armv7",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 32,
            "device": "angler-armv7",
            "debug": True,
            "product": "arm_krait",
        }
    )
    ci_builder(
        name="angler-armv7-non-gen-cc",
        category="angler|armv7",
        short_name="ngen",
        dimensions=userfault_gc_target_dims,
        properties={
            "bitness": 32,
            "device": "angler-armv7",
            "debug": True,
            "concurrent_collector": False,
            "generational_cc": False,
            "product": "arm_krait",
        }
    )
    ci_builder(
        name="angler-armv7-ndebug",
        category="angler|armv7",
        short_name="ndbg",
        dimensions=target_dims,
        properties={
            "bitness": 32,
            "device": "angler-armv7",
            "debug": False,
            "product": "arm_krait",
        }
    )
    ci_builder(
        name="angler-armv8-debug",
        category="angler|armv8",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "angler-armv8",
            "debug": True,
            "product": "armv8",
        }
    )
    ci_builder(
        name="angler-armv8-non-gen-cc",
        category="angler|armv8",
        short_name="ngen",
        dimensions=userfault_gc_target_dims,
        properties={
            "bitness": 64,
            "device": "angler-armv8",
            "debug": True,
            "concurrent_collector": False,
            "generational_cc": False,
            "product": "armv8",
        }
    )
    ci_builder(
        name="angler-armv8-ndebug",
        category="angler|armv8",
        short_name="ndbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "angler-armv8",
            "debug": False,
            "product": "armv8",
        }
    )
    ci_builder(
        name="bullhead-armv7-gcstress-ndebug",
        category="bullhead|armv7|gcstress",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 32,
            "device": "bullhead-armv7",
            "debug": False,
            "gcstress": True,
            "product": "arm_krait",
        }
    )
    ci_builder(
        name="bullhead-armv8-gcstress-debug",
        category="bullhead|armv8|gcstress",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "bullhead-armv8",
            "debug": True,
            "gcstress": True,
            "product": "armv8",
        }
    )
    ci_builder(
        name="bullhead-armv8-gcstress-ndebug",
        category="bullhead|armv8|gcstress",
        short_name="ndbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "bullhead-armv8",
            "debug": False,
            "gcstress": True,
            "product": "armv8",
        }
    )
    ci_builder(
        name="walleye-armv7-poison-debug",
        category="walleye|armv7|poison",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 32,
            "device": "walleye-armv7",
            "debug": True,
            "heap_poisoning": True,
            "product": "arm_krait",
        }
    )
    ci_builder(
        name="walleye-armv8-poison-debug",
        category="walleye|armv8|poison",
        short_name="dbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "walleye-armv8",
            "debug": True,
            "heap_poisoning": True,
            "product": "armv8",
        }
    )
    ci_builder(
        name="walleye-armv8-poison-ndebug",
        category="walleye|armv8|poison",
        short_name="ndbg",
        dimensions=target_dims,
        properties={
            "bitness": 64,
            "device": "walleye-armv8",
            "debug": False,
            "heap_poisoning": True,
            "product": "armv8",
        }
    )

def host_builders():
    host_dims = {"os": "Linux"}
    ci_builder(
        name="host-x86-cms",
        category="host|x86",
        short_name="cms",
        dimensions=host_dims,
        properties={
            "debug": True,
            "bitness": 32,
            "concurrent_collector": False,
            "generational_cc": False,
        }
    )
    ci_builder(
        name="host-x86-debug",
        category="host|x86",
        short_name="dbg",
        dimensions=host_dims,
        properties={
            "debug": True,
            "bitness": 32,
        }
    )
    ci_builder(
        name="host-x86-ndebug",
        category="host|x86",
        short_name="ndbg",
        dimensions=host_dims,
        properties={
            "debug": False,
            "bitness": 32,
        }
    )
    ci_builder(
        name="host-x86-gcstress-debug",
        category="host|x86",
        short_name="gcs",
        dimensions=host_dims,
        properties={
            "debug": True,
            "gcstress": True,
            "bitness": 32,
        }
    )
    ci_builder(
        name="host-x86-poison-debug",
        category="host|x86",
        short_name="psn",
        dimensions=host_dims,
        properties={
            "bitness": 32,
            "debug": True,
            "heap_poisoning": True,
        }
    )
    ci_builder(
        name="host-x86_64-cdex-fast",
        category="host|x64",
        short_name="cdx",
        dimensions=host_dims,
        properties={
            "use_props": True,
            "bitness": 64,
            "cdex_level": "fast",
            "debug": True,
        }
    )
    ci_builder(
        name="host-x86_64-cms",
        category="host|x64",
        short_name="cms",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "concurrent_collector": False,
            "debug": True,
            "generational_cc": False,
        }
    )
    ci_builder(
        name="host-x86_64-debug",
        category="host|x64",
        short_name="dbg",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "debug": True,
        }
    )
    ci_builder(
        name="host-x86_64-non-gen-cc",
        category="host|x64",
        short_name="ngen",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "debug": True,
            "generational_cc": False,
        }
    )
    ci_builder(
        name="host-x86_64-ndebug",
        category="host|x64",
        short_name="ndbg",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "debug": False,
        }
    )
    ci_builder(
        name="host-x86_64-poison-debug",
        category="host|x64",
        short_name="psn",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "debug": True,
            "heap_poisoning": True,
        }
    )
    ci_builder(
        name="qemu-armv8-ndebug",
        category="qemu|armv8",
        short_name="ndbg",
        dimensions=host_dims,
        is_fyi=True,
        properties={
            "bitness": 64,
            "debug": False,
            "device": "qemu-armv8",
            "on_virtual_machine": True,
            "product": "armv8",
        }
    )
    ci_builder(
        name="qemu-riscv64-ndebug",
        category="qemu|riscv64",
        short_name="ndbg",
        dimensions=host_dims,
        is_fyi=True,
        properties={
            "bitness": 64,
            "debug": False,
            "device": "qemu-riscv64",
            "on_virtual_machine": True,
            "product": "riscv64",
        }
    )
    ci_builder(
        name="qemu-riscv64-ndebug-build_only",
        category="qemu|riscv64",
        short_name="bo",
        dimensions=host_dims,
        properties={
            "bitness": 64,
            "build_only": True,
            "debug": False,
            "device": "qemu-riscv64",
            "on_virtual_machine": True,
            "product": "riscv64",
        }
    )

target_builders()
host_builders()