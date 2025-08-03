load("@rules_license//rules:providers.bzl", "LicenseInfo")
load("//build/bazel/rules:metadata.bzl", "MetadataFileInfo")

RuleLicensedDependenciesInfo = provider(
    doc = """Rule's licensed dependencies.""",
    fields = dict(
        license_closure = "depset(license) for the rule and its licensed dependencies",
    ),
)

def _maybe_expand(rule, transitive_licenses):
    if not RuleLicensedDependenciesInfo in rule:
        return
    dep_info = rule[RuleLicensedDependenciesInfo]
    if hasattr(dep_info, "license_closure"):
        transitive_licenses.append(dep_info.license_closure)

def create_metadata_file_info(ctx):
    if hasattr(ctx.rule.attr, "applicable_licenses"):
        for lic in ctx.rule.attr.applicable_licenses:
            files = lic.files.to_list()
            if len(files) == 1 and files[0].basename == "METADATA":
                return MetadataFileInfo(metadata_file = files[0])

    return MetadataFileInfo(metadata_file = None)

def _rule_licenses_aspect_impl(_rule, ctx):
    if ctx.rule.kind == "_license":
        return [RuleLicensedDependenciesInfo(), MetadataFileInfo()]

    licenses = []
    transitive_licenses = []
    if hasattr(ctx.rule.attr, "applicable_licenses"):
        licenses.extend(ctx.rule.attr.applicable_licenses)

    for a in dir(ctx.rule.attr):
        # Ignore private attributes
        if a.startswith("_"):
            continue
        value = getattr(ctx.rule.attr, a)
        vlist = value if type(value) == type([]) else [value]
        for item in vlist:
            if type(item) == "Target" and RuleLicensedDependenciesInfo in item:
                _maybe_expand(item, transitive_licenses)

    return [
        RuleLicensedDependenciesInfo(license_closure = depset(licenses, transitive = transitive_licenses)),
        create_metadata_file_info(ctx),
    ]

license_aspect = aspect(
    doc = """Collect transitive license closure.""",
    implementation = _rule_licenses_aspect_impl,
    attr_aspects = ["*"],
    apply_to_generating_rules = True,
    provides = [RuleLicensedDependenciesInfo, MetadataFileInfo],
)

_license_kind_template = """
      {{
        "target": "{kind_path}",
        "name": "{kind_name}",
        "conditions": {kind_conditions}
      }}"""

def _license_kind_to_json(kind):
    return _license_kind_template.format(kind_name = kind.name, kind_path = kind.label, kind_conditions = kind.conditions)

def _quotes_or_null(s):
    if not s:
        return "null"
    return s

def _license_file(license_rule):
    file = license_rule[LicenseInfo].license_text
    return file if file and file.basename != "__NO_LICENSE__" else struct(path = "")

def _divine_package_name(license):
    if license.package_name:
        return license.package_name.removeprefix("external").removesuffix("BUILD.bazel").replace("/", " ").strip()
    return license.rule.name.removeprefix("external_").removesuffix("_license").replace("_", " ")

def license_map(deps):
    """Collects license to licensees map for the given set of rule targets.

    TODO(asmundak): at the moment licensees lists are all empty because collecting
    the licensees turned out to be too slow. Restore this later.
    Args:
        deps: list of rule targets
    Returns:
        dictionary mapping a license to its licensees
    """
    transitive_licenses = []
    for d in deps:
        _maybe_expand(d, transitive_licenses)

    # Each rule provides the closure of its licenses, let us build the
    # reverse map. A minor quirk is that for some reason there may be
    # multiple license instances with with the same label. Use the
    # intermediary dict to map rule's label to its first instance
    license_by_label = dict()
    licensees = dict()
    for lic in depset(transitive = transitive_licenses).to_list():
        if not LicenseInfo in lic:
            continue
        label = lic[LicenseInfo].label.name
        if not label in license_by_label:
            license_by_label[label] = lic
            licensees[lic] = []
    return licensees

_license_template = """  {{
    "rule": "{rule}",
    "license_kinds": [{kinds}
    ],
    "copyright_notice": "{copyright_notice}",
    "package_name": "{package_name}",
    "package_url": {package_url},
    "package_version": {package_version},
    "license_text": "{license_text}",
    "licensees": [
        "{licensees}"
    ]
    \n  }}"""

def _used_license_to_json(license_rule, licensed_rules):
    license = license_rule[LicenseInfo]
    return _license_template.format(
        rule = license.label.name,
        copyright_notice = license.copyright_notice,
        package_name = _divine_package_name(license),
        package_url = _quotes_or_null(license.package_url),
        package_version = _quotes_or_null(license.package_version),
        license_text = _license_file(license_rule).path,
        kinds = ",\n".join([_license_kind_to_json(kind) for kind in license.license_kinds]),
        licensees = "\",\n        \"".join([r for r in licensed_rules]),
    )

def license_map_to_json(licensees):
    """Returns an array of JSON representations of a license and its licensees. """
    return [_used_license_to_json(lic, rules) for lic, rules in licensees.items()]

def license_map_notice_files(licensees):
    """Returns an array of license text files for the given licensee map.

    Args:
        licensees: dict returned by license_map() call
    Returns:
        the list of notice files this licensees map depends on.
    """
    notice_files = []
    for lic in licensees.keys():
        file = _license_file(lic)
        if file.path:
            notice_files.append(file)
    return notice_files
