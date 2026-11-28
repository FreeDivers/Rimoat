"""Read-only audit of packaged translations, resource references, and catalog label coverage.

Run: python -B tools/audit_i18n.py
Prints JSON to stdout and returns nonzero for structural resource errors and disallowed
i18n patterns (AppStrings literal/Chinese fallbacks, localizedLabel Chinese literals, and
U+00B7 separators). Catalog coverage and Japanese Han matches are reported for review.
No databases, assets, resources, or user data are modified.
"""

from collections import Counter
import json
from pathlib import Path
import re
import sqlite3
import sys
import xml.etree.ElementTree as ET
import zlib

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "app/src/main/java/org/qinyan/rimoat"
LOCALES = ("values", "values-en", "values-zh", "values-b+zh+Hant", "values-ja")
EXPECTED_TAGS = {"zh-CN", "zh-Hant", "en", "ja"}
HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
KOTLIN_COMMENT_OR_STRING = re.compile(
    r'//[^\n]*|/\*[\s\S]*?\*/|"""([\s\S]*?)"""|"([^"\n\\]*(?:\\.[^"\n\\]*)*)"'
)
APP_STRINGS_LITERAL_ARGUMENT = re.compile(
    r'AppStrings\.get\(\s*(?:org\.qinyan\.rimoat\.)?R\.string\.(\w+)\s*,\s*'
    r'("""[\s\S]*?"""|"(?:\\.|[^"\\])*")'
)
LOCALIZED_LABEL_CHINESE_LITERAL = re.compile(
    r'(?:org\.qinyan\.rimoat\.i18n\.)?localizedLabel\(\s*'
    r'("""[\s\S]*?"""|"(?:\\.|[^"\\])*")'
)
FORMAT = re.compile(r"%(?:(\d+)\$)?([-#+ 0,(<]*)(?:\d+)?(?:\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])")
REFERENCE = re.compile(r"(?<![\w.])(?:org\.qinyan\.rimoat\.)?R\.(string|plurals)\.(\w+)")
LANGUAGE_NAMES = {"settings_language_traditional_chinese", "language_chinese", "language_english", "language_japanese"}


def relative(path):
    return path.relative_to(ROOT).as_posix()


def resource_text(element):
    return "".join(element.itertext())


def load_resources(directory, errors):
    entries = {}
    for path in sorted((RES / directory).glob("*.xml")):
        try:
            tree = ET.parse(path)
        except ET.ParseError as error:
            errors.append({"kind": "xml_parse", "file": relative(path), "detail": str(error)})
            continue
        for element in tree.getroot():
            if element.tag not in {"string", "plurals"}:
                continue
            key = element.tag + "/" + element.attrib["name"]
            if key in entries:
                errors.append({"kind": "duplicate_resource", "locale": directory, "key": key})
            values = {"value": resource_text(element)} if element.tag == "string" else {
                item.attrib["quantity"]: resource_text(item) for item in element.findall("item")
            }
            if element.tag == "plurals" and len(values) != len(element.findall("item")):
                errors.append({"kind": "duplicate_plural_quantity", "locale": directory, "key": key})
            entries[key] = {
                "file": relative(path), "values": values,
                "formatted": element.attrib.get("formatted", "true"),
                "translatable": element.attrib.get("translatable", "true"),
            }
    return entries


def placeholders(value):
    """Compare argument identities/types while allowing translated argument order/repetition."""
    signature = set()
    implicit = 0
    previous = None
    for match in FORMAT.finditer(value):
        index, flags, conversion = match.groups()
        if conversion in {"%", "n"}:
            continue
        if "<" in flags:
            argument = previous
        elif index:
            argument = int(index)
        else:
            implicit += 1
            argument = implicit
        signature.add((argument, conversion.lower()))
        previous = argument
    return sorted(signature, key=lambda pair: (pair[0] or 0, pair[1]))


def audit_resources(errors):
    bundles = {locale: load_resources(locale, errors) for locale in LOCALES}
    baseline = bundles["values"]
    for locale, entries in bundles.items():
        missing = sorted(key for key in baseline.keys() - entries.keys()
                         if baseline[key]["translatable"] != "false")
        extra = sorted(entries.keys() - baseline.keys())
        if missing or extra:
            errors.append({"kind": "resource_coverage", "locale": locale, "missing": missing, "extra": extra})
        for key, entry in entries.items():
            if key.startswith("plurals/"):
                required = {"one", "other"} if locale in {"values", "values-en"} else {"other"}
                missing_quantities = sorted(required - entry["values"].keys())
                if missing_quantities:
                    errors.append({"kind": "plural_coverage", "locale": locale, "key": key, "missing": missing_quantities})
            if key not in baseline:
                continue
            base = baseline[key]
            if locale == "values-en" and any(entry[field] != base[field] for field in ("values", "formatted", "translatable")):
                errors.append({"kind": "default_english_mismatch", "key": key})
            if base["formatted"] == "false":
                continue
            source_value = base["values"].get("value", base["values"].get("other", ""))
            expected = placeholders(source_value)
            for quantity, value in entry["values"].items():
                actual = placeholders(value)
                if actual != expected:
                    errors.append({"kind": "placeholder_mismatch", "locale": locale, "key": key,
                                   "quantity": quantity, "expected": expected, "actual": actual})
    reference_count = 0
    source_files = 0
    for path in sorted((ROOT / "app/src").rglob("*.kt")):
        source_files += 1
        for line, text in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
            for kind, name in REFERENCE.findall(text):
                reference_count += 1
                key = kind + "/" + name
                if key not in baseline:
                    errors.append({"kind": "missing_source_resource", "file": relative(path), "line": line, "key": key})
    config = RES / "xml/locales_config.xml"
    configured = [element.attrib["{http://schemas.android.com/apk/res/android}name"] for element in ET.parse(config).getroot()]
    if set(configured) != EXPECTED_TAGS or len(configured) != len(EXPECTED_TAGS):
        errors.append({"kind": "locale_config", "expected": sorted(EXPECTED_TAGS), "actual": configured})
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot().find("application")
    if manifest is None or manifest.attrib.get("{http://schemas.android.com/apk/res/android}localeConfig") != "@xml/locales_config":
        errors.append({"kind": "manifest_locale_config"})
    suspicious = {"english_han_values": [], "japanese_identical_to_chinese_candidates": []}
    for locale in ("values-en", "values-ja"):
        for key, entry in bundles[locale].items():
            if key.split("/", 1)[1] in LANGUAGE_NAMES:
                continue
            for quantity, value in entry["values"].items():
                if not HAN.search(value):
                    continue
                item = {"key": key, "quantity": quantity, "value": value}
                if locale == "values-en":
                    suspicious["english_han_values"].append(item)
                else:
                    chinese = bundles["values-zh"].get(key, {}).get("values", {})
                    if value == chinese.get(quantity, chinese.get("other")):
                        suspicious["japanese_identical_to_chinese_candidates"].append(item)
    if suspicious["english_han_values"]:
        errors.append({"kind": "english_contains_han", "count": len(suspicious["english_han_values"])})
    return bundles, {
        "locales": {locale: {"strings": sum(key.startswith("string/") for key in entries),
                              "plurals": sum(key.startswith("plurals/") for key in entries),
                              "shared_nontranslatable_fallbacks": sum(
                                  key not in entries and entry["translatable"] == "false" for key, entry in baseline.items()
                              )} for locale, entries in bundles.items()},
        "configured_tags": configured, "source_files": source_files, "source_resource_references": reference_count,
        "literal_review": suspicious,
        "literal_review_note": "Japanese shares Han spellings with Chinese. Identical values are candidates for human review, not automatic failures; language names are excluded.",
    }


def catalog_translation_maps():
    """Read the narrow Kotlin mapping declarations instead of duplicating the catalog taxonomy."""
    legacy = (SOURCE / "data/LegacyLabelMap.kt").read_text("utf-8")
    labels = (SOURCE / "i18n/ButtonKeyLabels.kt").read_text("utf-8")
    resources = dict(re.findall(r'ButtonKey\.(\w+)\s*->\s*R\.string\.(\w+)', labels))
    translated = {label: resources[key] for label, key in
                  re.findall(r'"([^"\n]+)"\s+to\s+ButtonKey\.(\w+)', legacy) if key in resources}
    catalog_labels = (SOURCE / "i18n/CatalogKeyLabels.kt").read_text("utf-8")
    fallback_resources = dict(re.findall(
        r'CatalogFallbackLabel\.(\w+)\s*->\s*R\.string\.(\w+)', catalog_labels))
    translated.update({label: fallback_resources[key] for label, key in re.findall(
        r'"([^"\n]+)"\s*->\s*CatalogFallbackLabel\.(\w+)', legacy) if key in fallback_resources})
    translated["OK"] = "control_ok"
    catalog = legacy
    typed_block = catalog.split("private val catalogAliases", 1)[1].split("private val catalogLabelAliases", 1)[0]
    typed = {}
    for number, mappings in re.findall(r'(-?\d+)\s+to\s+mapOf\((.*?)\)', typed_block, re.S):
        typed[int(number)] = dict(re.findall(r'"([^"\n]+)"\s+to\s+"([^"\n]+)"', mappings))
    alias_block = catalog.split("private val catalogLabelAliases", 1)[1].split("private val catalogCanonicalNames", 1)[0]
    aliases = dict(re.findall(r'"([^"\n]+)"\s+to\s+"([^"\n]+)"', alias_block))
    return translated, typed, aliases


def audit_catalog(bundles):
    database = ROOT / "app/src/main/assets/catalog_v1.db"
    translated, typed, aliases = catalog_translation_maps()
    summaries = {}
    # mode=ro explicitly prevents SQLite journal/schema/data writes.
    with sqlite3.connect(database.as_uri() + "?mode=ro", uri=True) as connection:
        for legacy in (0, 1):
            occurrences = chinese_occurrences = translated_occurrences = key_sets = 0
            chinese_names = set()
            unresolved = Counter()
            unresolved_types = Counter()
            examples = {}
            for (blob,) in connection.execute("SELECT data FROM key_sets WHERE legacy=?", (legacy,)):
                key_sets += 1
                for row in json.loads(zlib.decompress(blob)):
                    occurrences += 1
                    key_type, label = row[0], (row[1] or "").strip()
                    if not HAN.search(label):
                        continue
                    chinese_occurrences += 1
                    chinese_names.add(label)
                    normalized = typed.get(key_type, {}).get(label, aliases.get(label, label))
                    is_digit = key_type in range(10) and label in {str(key_type), f"数字键 {key_type}", f"数字键{key_type}"}
                    is_preset = re.fullmatch(r"开机_(制冷|制热)_(\d{2})度", label) is not None
                    key = translated.get(normalized)
                    english = bundles["values-en"].get("string/" + (key or ""), {}).get("values", {}).get("value", normalized)
                    if is_digit or is_preset or not HAN.search(english):
                        translated_occurrences += 1
                    else:
                        unresolved[label] += 1
                        unresolved_types[key_type] += 1
                        examples.setdefault(label, key_type)
            summaries["current" if legacy == 0 else "legacy"] = {
                "key_sets": key_sets, "key_occurrences": occurrences,
                "han_label_occurrences": chinese_occurrences, "distinct_han_labels": len(chinese_names),
                "translated_han_occurrences_estimate": translated_occurrences,
                "untranslated_han_occurrences_estimate": sum(unresolved.values()),
                "untranslated_distinct_han_labels_estimate": len(unresolved),
                "top_untranslated_labels": [{"label": label, "occurrences": count, "example_key_type": examples[label]}
                                             for label, count in unresolved.most_common(20)],
                "untranslated_occurrences_by_key_type": dict(sorted(unresolved_types.items())),
            }
    return {
        "asset": relative(database), "read_only": True, "sets": summaries,
        "method": "Static estimate of source label display translation using legacy metadata, stable ButtonKey resources and packaged English resources. Runtime category construction and normalized profile labels can differ.",
        "boundary": "Manufacturer/custom catalog labels without a reviewed semantic mapping remain source text. Negative shape/color IDs and shifted legacy IDs are not replaced solely from numeric type. User-entered names/labels are preserved; database assets are not rewritten.",
    }


def strip_kotlin_comments(text):
    return KOTLIN_COMMENT_OR_STRING.sub(
        lambda m: " " if m.group(1) is None and m.group(2) is None else m.group(0),
        text,
    )


def kotlin_string_literals(text):
    for match in KOTLIN_COMMENT_OR_STRING.finditer(text):
        if match.group(1) is not None:
            yield match.group(1)
        elif match.group(2) is not None:
            yield match.group(2)


def audit_ui_i18n_counts(bundles, errors):
    app_strings_literal_argument = 0
    app_strings_chinese_fallback = 0
    localized_label_chinese_literal = 0
    for path in sorted(SOURCE.rglob("*.kt")):
        cleaned = strip_kotlin_comments(path.read_text(encoding="utf-8-sig"))
        for match in APP_STRINGS_LITERAL_ARGUMENT.finditer(cleaned):
            app_strings_literal_argument += 1
            line = cleaned.count("\n", 0, match.start()) + 1
            if HAN.search(match.group(2)):
                app_strings_chinese_fallback += 1
                errors.append({
                    "kind": "app_strings_chinese_fallback",
                    "file": relative(path),
                    "line": line,
                    "key": match.group(1),
                })
            else:
                errors.append({
                    "kind": "app_strings_literal_argument",
                    "file": relative(path),
                    "line": line,
                    "key": match.group(1),
                })
        for match in LOCALIZED_LABEL_CHINESE_LITERAL.finditer(cleaned):
            if HAN.search(match.group(1)):
                localized_label_chinese_literal += 1
                line = cleaned.count("\n", 0, match.start()) + 1
                errors.append({
                    "kind": "localized_label_chinese_literal",
                    "file": relative(path),
                    "line": line,
                })

    ui_and_widget_literals = {}
    for scope in ("ui", "widget"):
        count = 0
        for path in sorted((SOURCE / scope).rglob("*.kt")):
            for literal in kotlin_string_literals(path.read_text(encoding="utf-8-sig")):
                if HAN.search(literal):
                    count += 1
        ui_and_widget_literals[scope] = count

    resource_u00b7 = 0
    for locale, entries in bundles.items():
        for key, entry in entries.items():
            for quantity, value in entry["values"].items():
                if "\u00b7" in value:
                    resource_u00b7 += 1
                    errors.append({
                        "kind": "resource_u00b7",
                        "locale": locale,
                        "key": key,
                        "quantity": quantity,
                    })

    return {
        "app_strings_get_literal_argument": app_strings_literal_argument,
        "app_strings_get_chinese_fallback": app_strings_chinese_fallback,
        "localized_label_chinese_literal": localized_label_chinese_literal,
        "resource_u00b7": resource_u00b7,
        "ui_and_widget_chinese_literals": {
            "total": sum(ui_and_widget_literals.values()),
            **ui_and_widget_literals,
        },
    }


def audit():
    errors = []
    bundles, resources = audit_resources(errors)
    counts = audit_ui_i18n_counts(bundles, errors)
    return {"status": "PASS" if not errors else "FAIL", "resources": resources,
            "ui_i18n_counts": counts,
            "catalog": audit_catalog(bundles), "errors": errors}


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    result = audit()
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(bool(result["errors"]))
