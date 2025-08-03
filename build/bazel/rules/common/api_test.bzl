load("@bazel_skylib//lib:unittest.bzl", "asserts", "unittest")
load("//build/bazel/rules/common:api.bzl", "api_from_product")

def _is_preview_test_impl(ctx):
    env = unittest.begin(ctx)
    platform_sdk_variables = struct(
        platform_version_active_codenames = ["UpsideDownCake"],
    )
    api = api_from_product(platform_sdk_variables)

    # schema: version string to parse: is preview api
    _LEVELS_UNDER_TEST = {
        # numbers
        "9": False,  # earliest released number
        # codenames
        "Tiramisu": False,
        "UpsideDownCake": True,  # preview
        "current": True,  # future (considered as preview)
        "(no version)": True,
        # preview numbers
        "9000": True,  # preview
        "10000": True,  # future (considered as preview)
    }

    for level, expected in _LEVELS_UNDER_TEST.items():
        asserts.equals(env, expected, api.is_preview(level), "unexpected is_preview value for %s" % level)

    return unittest.end(env)

is_preview_test = unittest.make(_is_preview_test_impl)

def _default_app_target_sdk_string_test_impl(ctx):
    env = unittest.begin(ctx)

    asserts.equals(
        env,
        "33",
        api_from_product(struct(
            platform_sdk_final = True,
            platform_sdk_version = 33,
            platform_sdk_codename = "REL",
            platform_version_active_codenames = [],
        )).default_app_target_sdk_string(),
        "unexpected default_app_target_sdk_string value with platform_sdk_final True and platform_sdk_version 33.",
    )
    asserts.equals(
        env,
        "VanillaIceCream",
        api_from_product(struct(
            platform_sdk_final = False,
            platform_sdk_version = 33,
            platform_sdk_codename = "VanillaIceCream",
            platform_version_active_codenames = ["VanillaIceCream"],
        )).default_app_target_sdk_string(),
        "unexpected default_app_target_sdk_string value with platform_sdk_final False and platform_sdk_codename VanillaIceCream.",
    )

    return unittest.end(env)

default_app_target_sdk_string_test = unittest.make(_default_app_target_sdk_string_test_impl)

def _effective_version_string_test_impl(ctx):
    env = unittest.begin(ctx)

    asserts.equals(
        env,
        "32",
        api_from_product(struct(
            platform_sdk_final = True,
            platform_sdk_version = 33,
            platform_sdk_codename = "REL",
            platform_version_active_codenames = [],
        )).effective_version_string("32"),
        "unexpected effective version string when input version (32) is not preview",
    )
    asserts.equals(
        env,
        "33",
        api_from_product(struct(
            platform_sdk_final = True,
            platform_sdk_version = 33,
            platform_sdk_codename = "REL",
            platform_version_active_codenames = [],
        )).effective_version_string("current"),
        "unexpected effective version string when input version (current) is preview and default_app_target_sdk (33) is not.",
    )
    asserts.equals(
        env,
        "VanillaIceCream",
        api_from_product(struct(
            platform_sdk_final = False,
            platform_sdk_version = 33,
            platform_sdk_codename = "UpsideDownCake",
            platform_version_active_codenames = ["UpsideDownCake", "VanillaIceCream"],
        )).effective_version_string("VanillaIceCream"),
        "unexpected effective version string when both input version (VanillaIceCream) and default_app_target_sdk (UpsideDownCake) are preview.",
    )
    asserts.equals(
        env,
        "UpsideDownCake",
        api_from_product(struct(
            platform_sdk_final = False,
            platform_sdk_version = 33,
            platform_sdk_codename = "UpsideDownCake",
            platform_version_active_codenames = ["UpsideDownCake", "VanillaIceCream"],
        )).effective_version_string("current"),
        "unexpected effective version string when both input version (current) and default_app_target_sdk (UpsideDownCake) are preview.",
    )

    return unittest.end(env)

effective_version_string_test = unittest.make(_effective_version_string_test_impl)

def _api_levels_test_impl(ctx):
    env = unittest.begin(ctx)
    api = api_from_product(struct(
        platform_sdk_final = False,
        platform_sdk_version = 33,
        platform_sdk_codename = "UpsideDownCake",
        platform_version_active_codenames = ["UpsideDownCake"],
    ))

    # schema: version string to parse: expected api int
    _LEVELS_UNDER_TEST = {
        # numbers
        "9": 9,  # earliest released number
        "21": 21,
        "30": 30,
        "33": 33,
        # unchecked non final api level (not finalized, not preview, not current)
        "1234": 1234,
        "8999": 8999,
        "9999": 9999,
        "10001": 10001,
        # letters
        "G": 9,  # earliest released letter
        "J-MR1": 17,
        "R": 30,
        "S": 31,
        "S-V2": 32,
        # codenames
        "Tiramisu": 33,
        "UpsideDownCake": 9000,
        "current": 10000,
        "9000": 9000,
        "10000": 10000,
    }

    for level, expected in _LEVELS_UNDER_TEST.items():
        asserts.equals(env, expected, api.parse_api_level_from_version(level), "unexpected api level parsed for %s" % level)

    return unittest.end(env)

api_levels_test = unittest.make(_api_levels_test_impl)

def _final_or_future_test_impl(ctx):
    env = unittest.begin(ctx)
    api = api_from_product(struct(
        platform_sdk_final = False,
        platform_sdk_version = 33,
        platform_sdk_codename = "UpsideDownCake",
        platform_version_active_codenames = ["UpsideDownCake"],
    ))

    # schema: version string to parse: expected api int
    _LEVELS_UNDER_TEST = {
        # finalized
        "30": 30,
        "33": 33,
        "S": 31,
        "S-V2": 32,
        "Tiramisu": 33,
        # not finalized
        "UpsideDownCake": 10000,
        "current": 10000,
        "9000": 10000,
        "10000": 10000,
    }

    for level, expected in _LEVELS_UNDER_TEST.items():
        asserts.equals(
            env,
            expected,
            api.final_or_future(api.parse_api_level_from_version(level)),
            "unexpected final or future api for %s" % level,
        )

    return unittest.end(env)

final_or_future_test = unittest.make(_final_or_future_test_impl)

def api_levels_test_suite(name):
    tests = {
        "api_levels": api_levels_test,
        "final_or_future": final_or_future_test,
        "is_preview": is_preview_test,
        "default_app_target_sdk_string": default_app_target_sdk_string_test,
        "effective_version_string": effective_version_string_test,
    }

    for test_name, test_function in tests.items():
        test_function(name = name + "_" + test_name)

    native.test_suite(
        name = name,
        tests = [name + "_" + test_name for test_name in tests.keys()],
    )
