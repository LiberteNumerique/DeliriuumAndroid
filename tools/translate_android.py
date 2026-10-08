#!/usr/bin/env python3
"""
Deliriuum Android — traduction automatique des ressources V2
==========================================================

Traduit app/src/main/res/values/strings.xml (français source)
vers les 29 autres locales déjà utilisées par Deliriuum iOS.

- strings + plurals
- cache/reprise après interruption
- validation des placeholders Android
- validation des retours à la ligne explicites
- masquage/restauration garanti des marques, placeholders, tags et retours ligne
- catégories de pluriels propres à chaque langue
- aucun secret stocké dans le projet

Prérequis :
    python3 -m pip install --upgrade openai
    export OPENAI_API_KEY="sk-..."

Usage :
    python3 tools/translate_android.py --dry-run
    python3 tools/translate_android.py
    python3 tools/translate_android.py --languages en de es
    python3 tools/translate_android.py --validate-only
    python3 tools/translate_android.py --force --languages en

Le modèle par défaut est gpt-5.6-luna.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

# ---------------------------------------------------------------------------
# CONFIGURATION DELIRIIUM
# ---------------------------------------------------------------------------

SOURCE_LOCALE = "fr"

# Exactement les 30 locales du catalogue iOS Deliriuum :
# français source + 29 cibles.
LANGUAGES: dict[str, str] = {
    "fr": "Français",
    "en": "English",
    "de": "Deutsch",
    "es": "Español",
    "it": "Italiano",
    "pt": "Português",
    "nl": "Nederlands",
    "pl": "Polski",
    "cs": "Čeština",
    "sk": "Slovenčina",
    "hu": "Magyar",
    "ro": "Română",
    "bg": "Български",
    "hr": "Hrvatski",
    "sl": "Slovenščina",
    "el": "Ελληνικά",
    "sv": "Svenska",
    "da": "Dansk",
    "no": "Norsk",
    "fi": "Suomi",
    "et": "Eesti",
    "lv": "Latviešu",
    "lt": "Lietuvių",
    "tr": "Türkçe",
    "uk": "Українська",
    "ru": "Русский",
    "ar": "العربية",
    "he": "עברית",
    "ja": "日本語",
    "ko": "한국어",
}

# Dossier Android pour chaque locale.
# Les qualifiers simples suffisent ici, y compris pour he avec AAPT2 moderne.
ANDROID_DIR: dict[str, str] = {
    code: ("values" if code == "fr" else f"values-{code}")
    for code in LANGUAGES
}

# Quantités CLDR/Android utiles pour les langues ciblées.
# "other" est obligatoire dans Android.
PLURAL_QUANTITIES: dict[str, tuple[str, ...]] = {
    "fr": ("one", "other"),
    "en": ("one", "other"),
    "de": ("one", "other"),
    "es": ("one", "other"),
    "it": ("one", "other"),
    "pt": ("one", "many", "other"),
    "nl": ("one", "other"),
    "pl": ("one", "few", "many", "other"),
    "cs": ("one", "few", "many", "other"),
    "sk": ("one", "few", "many", "other"),
    "hu": ("one", "other"),
    "ro": ("one", "few", "other"),
    "bg": ("one", "other"),
    "hr": ("one", "few", "other"),
    "sl": ("one", "two", "few", "other"),
    "el": ("one", "other"),
    "sv": ("one", "other"),
    "da": ("one", "other"),
    "no": ("one", "other"),
    "fi": ("one", "other"),
    "et": ("one", "other"),
    "lv": ("zero", "one", "other"),
    "lt": ("one", "few", "many", "other"),
    "tr": ("one", "other"),
    "uk": ("one", "few", "many", "other"),
    "ru": ("one", "few", "many", "other"),
    "ar": ("zero", "one", "two", "few", "many", "other"),
    "he": ("one", "two", "other"),
    "ja": ("other",),
    "ko": ("other",),
}

BRANDS = (
    "Deliriuum",
    "Deep Shield",
    "WireGuard",
    "WebRTC",
    "WebGL",
    "GeckoView",
    "DuckDuckGo",
    "Android",
    "Canvas",
    "AudioContext",
    "MediaDevices",
)

# Tokens printf Android : %1$s, %2$d, %s, %d, %1$.2f...
PRINTF_RE = re.compile(
    r"%(?!%)(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]"
)

TAG_RE = re.compile(r"</?[^>]+?>")

# ---------------------------------------------------------------------------
# DATA
# ---------------------------------------------------------------------------

@dataclass
class Resource:
    name: str
    kind: str  # "string" | "plurals"
    value: str | None
    forms: dict[str, str]
    attrs: dict[str, str]

    def fingerprint(self) -> str:
        payload = {
            "name": self.name,
            "kind": self.kind,
            "value": self.value,
            "forms": self.forms,
            "attrs": self.attrs,
        }
        return hashlib.sha256(
            json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ).hexdigest()

# ---------------------------------------------------------------------------
# XML
# ---------------------------------------------------------------------------

def inner_xml(element: ET.Element) -> str:
    chunks: list[str] = []
    if element.text:
        chunks.append(element.text)
    for child in list(element):
        chunks.append(ET.tostring(child, encoding="unicode", short_empty_elements=True))
    return "".join(chunks)


def normalize_source_text(text: str) -> str:
    # ElementTree convertit déjà &amp; etc.
    # On conserve les escapes Android déjà présents.
    return text


def load_resources(path: Path) -> list[Resource]:
    if not path.exists():
        raise FileNotFoundError(f"Fichier source introuvable : {path}")

    tree = ET.parse(path)
    root = tree.getroot()

    if root.tag != "resources":
        raise ValueError(f"Racine XML inattendue dans {path}: <{root.tag}>")

    result: list[Resource] = []
    seen: set[str] = set()

    for node in list(root):
        if not isinstance(node.tag, str):
            continue

        if node.tag == "string":
            name = node.attrib.get("name")
            if not name:
                continue
            if node.attrib.get("translatable", "").lower() == "false":
                continue
            if name in seen:
                raise ValueError(f"Clé dupliquée dans strings.xml : {name}")
            seen.add(name)

            result.append(
                Resource(
                    name=name,
                    kind="string",
                    value=normalize_source_text(inner_xml(node)),
                    forms={},
                    attrs={
                        k: v for k, v in node.attrib.items()
                        if k not in {"name", "translatable"}
                    },
                )
            )

        elif node.tag == "plurals":
            name = node.attrib.get("name")
            if not name:
                continue
            if node.attrib.get("translatable", "").lower() == "false":
                continue
            if name in seen:
                raise ValueError(f"Clé dupliquée dans strings.xml : {name}")
            seen.add(name)

            forms: dict[str, str] = {}
            for item in node.findall("item"):
                quantity = item.attrib.get("quantity")
                if quantity:
                    forms[quantity] = normalize_source_text(inner_xml(item))

            if "other" not in forms:
                raise ValueError(
                    f"<plurals name={name!r}> doit contenir quantity=\"other\"."
                )

            result.append(
                Resource(
                    name=name,
                    kind="plurals",
                    value=None,
                    forms=forms,
                    attrs={
                        k: v for k, v in node.attrib.items()
                        if k not in {"name", "translatable"}
                    },
                )
            )

    return result


def android_escape_text(text: str) -> str:
    """
    Échappe uniquement ce qui est nécessaire au XML/Android sans casser
    les séquences \n, les placeholders ni les tags inline.
    """
    # Le modèle doit rendre \n comme deux caractères. Si une vraie nouvelle
    # ligne apparaît accidentellement dans une valeur, on la normalise.
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\n", r"\n")

    # Protège les tags inline afin de ne pas les XML-escaper.
    protected: dict[str, str] = {}
    def protect_tag(match: re.Match[str]) -> str:
        key = f"___I18N_TAG_{len(protected)}___"
        protected[key] = match.group(0)
        return key

    tmp = TAG_RE.sub(protect_tag, text)

    # Les backslashes Android existants restent inchangés.
    tmp = html.escape(tmp, quote=False)

    # Android string resources : échapper apostrophes/guillemets s'ils ne
    # le sont pas déjà. On évite de doubler les escapes existants.
    tmp = re.sub(r"(?<!\\)'", r"\\'", tmp)
    tmp = re.sub(r'(?<!\\)"', r'\\"', tmp)

    for key, tag in protected.items():
        tmp = tmp.replace(key, tag)

    return tmp


def write_resources(
    path: Path,
    resources: list[Resource],
    translations: dict[str, Any],
    locale: str,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>\n',
        "<resources>\n",
        f"    <!-- Generated by translate_android.py — locale {locale} -->\n",
    ]

    for res in resources:
        translated = translations[res.name]

        attrs = "".join(
            f' {k}="{html.escape(v, quote=True)}"'
            for k, v in res.attrs.items()
        )

        if res.kind == "string":
            value = android_escape_text(str(translated["value"]))
            lines.append(
                f'    <string name="{res.name}"{attrs}>{value}</string>\n'
            )
        else:
            lines.append(f'    <plurals name="{res.name}"{attrs}>\n')
            forms = translated["forms"]
            for quantity in PLURAL_QUANTITIES[locale]:
                value = android_escape_text(str(forms[quantity]))
                lines.append(
                    f'        <item quantity="{quantity}">{value}</item>\n'
                )
            lines.append("    </plurals>\n")

    lines.append("</resources>\n")
    path.write_text("".join(lines), encoding="utf-8")


# ---------------------------------------------------------------------------
# VALIDATION
# ---------------------------------------------------------------------------

def placeholders(text: str) -> list[str]:
    return sorted(m.group(0) for m in PRINTF_RE.finditer(text))


def explicit_newlines(text: str) -> int:
    return text.count(r"\n")


def tag_signature(text: str) -> list[str]:
    return TAG_RE.findall(text)


def brand_signature(text: str) -> list[str]:
    return [brand for brand in BRANDS if brand in text]


def validate_one(source: str, target: str, label: str) -> list[str]:
    errors: list[str] = []

    if placeholders(source) != placeholders(target):
        errors.append(
            f"{label}: placeholders différents "
            f"{placeholders(source)} != {placeholders(target)}"
        )

    if explicit_newlines(source) != explicit_newlines(target):
        errors.append(
            f"{label}: nombre de \\\\n différent "
            f"{explicit_newlines(source)} != {explicit_newlines(target)}"
        )

    if tag_signature(source) != tag_signature(target):
        errors.append(
            f"{label}: balises XML/HTML modifiées "
            f"{tag_signature(source)} != {tag_signature(target)}"
        )

    for brand in brand_signature(source):
        if brand not in target:
            errors.append(f"{label}: marque modifiée ou perdue : {brand}")

    if not target.strip():
        errors.append(f"{label}: traduction vide")

    return errors


def source_for_plural_quantity(res: Resource, quantity: str) -> str:
    if quantity in res.forms:
        return res.forms[quantity]
    # Les langues sources n'ont pas forcément toutes les catégories.
    # "other" porte la forme générique à préserver pour placeholders/tags.
    return res.forms["other"]


def validate_translation(
    res: Resource,
    translated: dict[str, Any],
    locale: str,
) -> list[str]:
    errors: list[str] = []

    if res.kind == "string":
        value = translated.get("value")
        if not isinstance(value, str):
            return [f"{res.name}: champ value manquant/non textuel"]
        errors.extend(
            validate_one(
                res.value or "",
                value,
                res.name,
            )
        )
        return errors

    forms = translated.get("forms")
    if not isinstance(forms, dict):
        return [f"{res.name}: champ forms manquant"]

    expected = PLURAL_QUANTITIES[locale]
    missing = [q for q in expected if q not in forms]
    extra = [q for q in forms if q not in expected]

    if missing:
        errors.append(f"{res.name}: pluriels manquants {missing}")
    if extra:
        errors.append(f"{res.name}: pluriels inattendus {extra}")

    if errors:
        return errors

    # Pour chaque forme cible on compare les invariants à la forme source
    # équivalente si disponible, sinon à "other".
    for q in expected:
        target = forms[q]
        if not isinstance(target, str):
            errors.append(f"{res.name}[{q}]: valeur non textuelle")
            continue
        source = source_for_plural_quantity(res, q)
        errors.extend(
            validate_one(source, target, f"{res.name}[{q}]")
        )

    return errors


def load_target_as_translation(
    path: Path,
    source_resources: list[Resource],
) -> dict[str, Any]:
    target_resources = load_resources(path)
    by_name = {r.name: r for r in target_resources}
    out: dict[str, Any] = {}

    for src in source_resources:
        t = by_name.get(src.name)
        if not t:
            continue
        if src.kind != t.kind:
            continue
        if src.kind == "string":
            out[src.name] = {"value": t.value or ""}
        else:
            out[src.name] = {"forms": t.forms}

    return out


def validate_file(
    source_resources: list[Resource],
    target_path: Path,
    locale: str,
) -> list[str]:
    if not target_path.exists():
        return [f"{locale}: fichier absent : {target_path}"]

    translated = load_target_as_translation(target_path, source_resources)
    errors: list[str] = []

    for res in source_resources:
        if res.name not in translated:
            errors.append(f"{locale}: clé manquante : {res.name}")
            continue
        errors.extend(
            f"{locale}: {e}"
            for e in validate_translation(res, translated[res.name], locale)
        )

    return errors



# ---------------------------------------------------------------------------
# PROTECTION DES TOKENS AVANT TRADUCTION
# ---------------------------------------------------------------------------

TOKEN_PREFIX = "__DLM_"

def mask_text_for_translation(text: str) -> tuple[str, dict[str, str]]:
    """
    Remplace les éléments qui ne doivent JAMAIS être modifiés par le modèle
    par des sentinelles ASCII stables, puis les restaure après traduction.

    Protège :
      - retours à la ligne explicites \\n
      - placeholders Android printf
      - marques / termes techniques Deliriuum
      - balises XML/HTML inline
    """
    mapping: dict[str, str] = {}
    counter = 0

    def token(kind: str, original: str) -> str:
        nonlocal counter
        key = f"__DLM_{kind}_{counter}__"
        counter += 1
        mapping[key] = original
        return key

    # 1. Balises inline
    def repl_tag(m: re.Match[str]) -> str:
        return token("TAG", m.group(0))

    masked = TAG_RE.sub(repl_tag, text)

    # 2. Placeholders printf Android
    def repl_fmt(m: re.Match[str]) -> str:
        return token("FMT", m.group(0))

    masked = PRINTF_RE.sub(repl_fmt, masked)

    # 3. Marques / termes techniques.
    # On protège les plus longs d'abord.
    for brand in sorted(BRANDS, key=len, reverse=True):
        if brand in masked:
            masked = masked.replace(
                brand,
                token("TERM", brand)
            )

    # 4. Retours à la ligne Android explicites.
    # Chaque occurrence a son token pour conserver exactement le compte.
    while r"\n" in masked:
        masked = masked.replace(
            r"\n",
            token("NL", r"\n"),
            1
        )

    return masked, mapping


def unmask_text_after_translation(
    translated: str,
    mapping: dict[str, str],
    label: str
) -> str:
    """
    Restaure les sentinelles. Si le modèle en a supprimé ou déformé une,
    on échoue explicitement afin que le lot soit retenté.
    """
    result = translated

    missing = [
        key for key in mapping
        if key not in result
    ]

    if missing:
        raise ValueError(
            f"{label}: token(s) protégé(s) perdu(s) : "
            + ", ".join(missing[:8])
        )

    for key, original in mapping.items():
        result = result.replace(key, original)

    # Aucun token interne ne doit survivre.
    if TOKEN_PREFIX in result:
        raise ValueError(
            f"{label}: token interne Deliriuum non restauré"
        )

    return result


def masked_resource_payload(
    res: Resource,
    locale: str
) -> tuple[dict[str, Any], dict[str, dict[str, str]]]:
    """
    Retourne le payload à envoyer au modèle + les maps nécessaires pour
    restaurer chaque valeur.
    """
    maps: dict[str, dict[str, str]] = {}

    if res.kind == "string":
        masked, mapping = mask_text_for_translation(
            res.value or ""
        )
        maps["value"] = mapping
        return {
            "id": res.name,
            "type": "string",
            "source": masked,
        }, maps

    masked_forms: dict[str, str] = {}
    for quantity, value in res.forms.items():
        masked, mapping = mask_text_for_translation(value)
        masked_forms[quantity] = masked
        maps[quantity] = mapping

    return {
        "id": res.name,
        "type": "plurals",
        "source_forms": masked_forms,
        "required_target_quantities": list(
            PLURAL_QUANTITIES[locale]
        ),
    }, maps


def restore_translated_item(
    res: Resource,
    item: dict[str, Any],
    locale: str,
    maps: dict[str, dict[str, str]]
) -> dict[str, Any]:
    """
    Restaure les tokens masqués dans la réponse du modèle.
    Pour une forme plurielle cible absente de la source, on utilise comme
    référence la map de 'other', exactement comme la validation.
    """
    if res.kind == "string":
        value = item.get("value")
        if not isinstance(value, str):
            raise ValueError(
                f"{res.name}: champ value absent/non textuel"
            )
        return {
            "value": unmask_text_after_translation(
                value,
                maps.get("value", {}),
                res.name
            )
        }

    forms = item.get("forms")
    if not isinstance(forms, dict):
        raise ValueError(
            f"{res.name}: champ forms absent/non objet"
        )

    restored: dict[str, str] = {}
    for quantity in PLURAL_QUANTITIES[locale]:
        value = forms.get(quantity)
        if not isinstance(value, str):
            raise ValueError(
                f"{res.name}[{quantity}]: valeur absente/non textuelle"
            )

        source_quantity = (
            quantity
            if quantity in maps
            else "other"
        )

        restored[quantity] = unmask_text_after_translation(
            value,
            maps.get(source_quantity, {}),
            f"{res.name}[{quantity}]"
        )

    return {
        "forms": restored
    }


# ---------------------------------------------------------------------------
# OPENAI
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = r"""
You are translating the Android user interface of Deliriuum, a privacy
application with VPN and browser protections.

Translate from French into the requested target language.

Hard rules:
1. Return ONLY valid JSON, with exactly the requested resource IDs.
2. Do not translate or alter resource IDs.
3. Preserve these product/technical names exactly when present:
   Deliriuum, Deep Shield, WireGuard, WebRTC, WebGL, GeckoView,
   DuckDuckGo, Android, Canvas, AudioContext, MediaDevices.
4. Any token beginning with __DLM_ and ending with __ MUST be copied
   EXACTLY, character-for-character, in the translated text. These tokens
   represent protected brands, printf placeholders, line breaks or tags.
5. Never translate, delete, reorder, split or alter __DLM_*__ tokens.
6. Preserve the natural meaning and punctuation around those protected tokens.
7. Translate the WHOLE sentence naturally. Never translate phrase fragments
   independently when they belong to one resource.
8. Preserve the source register. If the French uses informal "tu", use the
   natural informal equivalent in the target language. If it uses formal
   "vous", preserve that distinction when the target language permits it.
9. "Protection" refers to privacy/network/browser protection, not antivirus.
10. "Espace" in the shortcuts UI means a user-created group/space of
    shortcuts.
11. For plural resources, write grammatically natural forms for every
    requested Android/CLDR quantity. Do not merely copy the French singular
    and plural structure.
12. Do not add explanations, notes, markdown, or code fences.
13. Keep UI labels concise where the French source is concise.
14. Never invent claims or change the meaning of privacy/security statements.
""".strip()


def resource_payload(res: Resource, locale: str) -> dict[str, Any]:
    if res.kind == "string":
        return {
            "id": res.name,
            "type": "string",
            "source": res.value,
        }
    return {
        "id": res.name,
        "type": "plurals",
        "source_forms": res.forms,
        "required_target_quantities": list(PLURAL_QUANTITIES[locale]),
    }


def expected_output_shape(batch: list[Resource], locale: str) -> dict[str, Any]:
    items: list[dict[str, Any]] = []
    for res in batch:
        if res.kind == "string":
            items.append({"id": res.name, "value": "<translation>"})
        else:
            items.append({
                "id": res.name,
                "forms": {
                    q: "<translation>"
                    for q in PLURAL_QUANTITIES[locale]
                },
            })
    return {"items": items}


def clean_json_text(text: str) -> str:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)

    # Défense contre une phrase accidentelle avant/après le JSON.
    first = text.find("{")
    last = text.rfind("}")
    if first >= 0 and last >= first:
        text = text[first:last + 1]

    return text


def call_openai(
    client: Any,
    model: str,
    locale: str,
    batch: list[Resource],
    attempt: int,
    previous_errors: list[str] | None = None,
) -> dict[str, Any]:
    masked_payloads: list[dict[str, Any]] = []
    token_maps: dict[str, dict[str, dict[str, str]]] = {}

    for resource in batch:
        masked_payload, maps = masked_resource_payload(
            resource,
            locale
        )
        masked_payloads.append(masked_payload)
        token_maps[resource.name] = maps

    payload = {
        "source_language": "French",
        "target_locale": locale,
        "target_language": LANGUAGES[locale],
        "resources": masked_payloads,
        "required_output_shape_example": expected_output_shape(batch, locale),
    }

    user_prompt = (
        "Translate this resource batch.\n\n"
        + json.dumps(payload, ensure_ascii=False, indent=2)
    )

    if previous_errors:
        user_prompt += (
            "\n\nThe previous attempt failed validation. Correct all of these "
            "issues while keeping the translations natural:\n- "
            + "\n- ".join(previous_errors[:40])
        )

    response = client.responses.create(
        model=model,
        instructions=SYSTEM_PROMPT,
        input=user_prompt,
    )

    raw = response.output_text
    if not raw:
        raise RuntimeError("Réponse OpenAI vide.")

    data = json.loads(clean_json_text(raw))
    items = data.get("items")

    if not isinstance(items, list):
        raise ValueError("JSON OpenAI : champ 'items' absent.")

    by_id: dict[str, Any] = {}
    for item in items:
        if not isinstance(item, dict):
            continue
        rid = item.get("id")
        if isinstance(rid, str):
            by_id[rid] = item

    expected_ids = {r.name for r in batch}
    received_ids = set(by_id)

    missing = expected_ids - received_ids
    extra = received_ids - expected_ids

    if missing:
        raise ValueError(f"IDs manquants dans la réponse : {sorted(missing)}")
    if extra:
        raise ValueError(f"IDs inattendus dans la réponse : {sorted(extra)}")

    restored: dict[str, Any] = {}

    for resource in batch:
        restored[resource.name] = restore_translated_item(
            resource,
            by_id[resource.name],
            locale,
            token_maps[resource.name]
        )

    return restored


# ---------------------------------------------------------------------------
# CACHE
# ---------------------------------------------------------------------------

def cache_path(cache_dir: Path, locale: str) -> Path:
    return cache_dir / f"{locale}.json"


def load_cache(cache_dir: Path, locale: str) -> dict[str, Any]:
    path = cache_path(cache_dir, locale)
    if not path.exists():
        return {"locale": locale, "resources": {}}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict):
            raise ValueError
        data.setdefault("locale", locale)
        data.setdefault("resources", {})
        return data
    except Exception:
        return {"locale": locale, "resources": {}}


def save_cache(cache_dir: Path, locale: str, cache: dict[str, Any]) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    path = cache_path(cache_dir, locale)
    tmp = path.with_suffix(".json.tmp")
    tmp.write_text(
        json.dumps(cache, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    tmp.replace(path)


def cached_translation(
    cache: dict[str, Any],
    res: Resource,
    locale: str,
) -> dict[str, Any] | None:
    item = cache.get("resources", {}).get(res.name)
    if not isinstance(item, dict):
        return None
    if item.get("source_hash") != res.fingerprint():
        return None
    translated = item.get("translation")
    if not isinstance(translated, dict):
        return None
    if validate_translation(res, translated, locale):
        return None
    return translated


# ---------------------------------------------------------------------------
# TRANSLATION LOOP
# ---------------------------------------------------------------------------

def chunks(values: list[Resource], size: int) -> Iterable[list[Resource]]:
    for i in range(0, len(values), size):
        yield values[i:i + size]


def translate_locale(
    client: Any,
    model: str,
    locale: str,
    resources: list[Resource],
    cache_dir: Path,
    batch_size: int,
    force: bool,
    max_retries: int,
) -> dict[str, Any]:
    cache = load_cache(cache_dir, locale)
    result: dict[str, Any] = {}
    pending: list[Resource] = []

    for res in resources:
        cached = None if force else cached_translation(cache, res, locale)
        if cached is not None:
            result[res.name] = cached
        else:
            pending.append(res)

    print(
        f"[{locale}] {LANGUAGES[locale]} — "
        f"{len(result)} en cache, {len(pending)} à traduire"
    )

    batches = list(chunks(pending, batch_size))

    for batch_index, batch in enumerate(batches, start=1):
        print(
            f"[{locale}] lot {batch_index}/{len(batches)} "
            f"({len(batch)} ressources)"
        )

        last_errors: list[str] | None = None
        translated_batch: dict[str, Any] | None = None

        for attempt in range(1, max_retries + 1):
            try:
                translated_batch = call_openai(
                    client=client,
                    model=model,
                    locale=locale,
                    batch=batch,
                    attempt=attempt,
                    previous_errors=last_errors,
                )

                validation_errors: list[str] = []
                for res in batch:
                    validation_errors.extend(
                        validate_translation(
                            res,
                            translated_batch[res.name],
                            locale,
                        )
                    )

                if validation_errors:
                    last_errors = validation_errors
                    raise ValueError(
                        "Validation échouée : "
                        + " | ".join(validation_errors[:12])
                    )

                break

            except Exception as exc:
                if attempt >= max_retries:
                    raise RuntimeError(
                        f"[{locale}] échec après {max_retries} tentatives "
                        f"sur le lot {batch_index}: {exc}"
                    ) from exc

                wait = min(2 ** attempt, 12)
                print(
                    f"[{locale}] tentative {attempt} échouée : {exc}\n"
                    f"[{locale}] nouvelle tentative dans {wait}s..."
                )
                time.sleep(wait)

        assert translated_batch is not None

        for res in batch:
            translated = translated_batch[res.name]
            result[res.name] = translated
            cache.setdefault("resources", {})[res.name] = {
                "source_hash": res.fingerprint(),
                "translation": translated,
                "model": model,
            }

        # Sauvegarde après CHAQUE lot : reprise possible après interruption.
        save_cache(cache_dir, locale, cache)

    return result


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser()

    parser.add_argument("--root", default=".")
    parser.add_argument(
        "--source",
        default="app/src/main/res/values/strings.xml",
    )
    parser.add_argument(
        "--model",
        default=os.environ.get("OPENAI_TRANSLATION_MODEL", "gpt-5.6-luna"),
    )
    parser.add_argument(
        "--languages",
        nargs="+",
        default=[x for x in LANGUAGES if x != SOURCE_LOCALE],
        help="Locales à générer. Par défaut : les 29 locales cibles.",
    )
    parser.add_argument("--batch-size", type=int, default=35)
    parser.add_argument("--max-retries", type=int, default=4)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument(
        "--cache-dir",
        default=".i18n_translation_cache",
    )

    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    source_path = (root / args.source).resolve()
    cache_dir = (root / args.cache_dir).resolve()
    res_root = root / "app/src/main/res"

    unknown = [x for x in args.languages if x not in LANGUAGES]
    if unknown:
        print(
            f"Locales inconnues : {', '.join(unknown)}",
            file=sys.stderr,
        )
        print(
            f"Locales valides : {', '.join(LANGUAGES)}",
            file=sys.stderr,
        )
        return 2

    if SOURCE_LOCALE in args.languages:
        print(
            "Le français est la source et ne doit pas être traduit.",
            file=sys.stderr,
        )
        return 2

    resources = load_resources(source_path)
    strings_count = sum(r.kind == "string" for r in resources)
    plurals_count = sum(r.kind == "plurals" for r in resources)

    print(f"Source : {source_path}")
    print(f"Strings : {strings_count}")
    print(f"Plurals : {plurals_count}")
    print(f"Total   : {len(resources)}")
    print(f"Modèle  : {args.model}")
    print(f"Locales : {len(args.languages)}")

    if args.dry_run:
        print("\nDry-run : aucun appel API, aucun fichier traduit créé.")
        for locale in args.languages:
            target = res_root / ANDROID_DIR[locale] / "strings.xml"
            print(
                f"  {locale:>2}  {LANGUAGES[locale]:<16} -> "
                f"{target.relative_to(root)} "
                f"plurals={','.join(PLURAL_QUANTITIES[locale])}"
            )
        return 0

    if args.validate_only:
        all_errors: list[str] = []
        for locale in args.languages:
            target = res_root / ANDROID_DIR[locale] / "strings.xml"
            errors = validate_file(resources, target, locale)
            if errors:
                print(f"[{locale}] ERREURS: {len(errors)}")
                for e in errors[:50]:
                    print(f"  - {e}")
                if len(errors) > 50:
                    print(f"  ... +{len(errors)-50} autres")
                all_errors.extend(errors)
            else:
                print(f"[{locale}] OK")

        if all_errors:
            print(
                f"\nVALIDATION ÉCHOUÉE : {len(all_errors)} problème(s).",
                file=sys.stderr,
            )
            return 1

        print("\nVALIDATION OK pour toutes les locales demandées.")
        return 0

    if not os.environ.get("OPENAI_API_KEY"):
        print(
            "OPENAI_API_KEY n'est pas défini.\n"
            'Exemple : export OPENAI_API_KEY="sk-..."',
            file=sys.stderr,
        )
        return 2

    try:
        from openai import OpenAI
    except ImportError:
        print(
            "Le package OpenAI n'est pas installé.\n"
            "Commande : python3 -m pip install --upgrade openai",
            file=sys.stderr,
        )
        return 2

    client = OpenAI()

    for idx, locale in enumerate(args.languages, start=1):
        print("\n" + "=" * 72)
        print(
            f"LANGUE {idx}/{len(args.languages)} : "
            f"{locale} — {LANGUAGES[locale]}"
        )
        print("=" * 72)

        translated = translate_locale(
            client=client,
            model=args.model,
            locale=locale,
            resources=resources,
            cache_dir=cache_dir,
            batch_size=max(1, args.batch_size),
            force=args.force,
            max_retries=max(1, args.max_retries),
        )

        target = res_root / ANDROID_DIR[locale] / "strings.xml"
        write_resources(
            path=target,
            resources=resources,
            translations=translated,
            locale=locale,
        )

        errors = validate_file(resources, target, locale)
        if errors:
            print(f"[{locale}] VALIDATION FINALE ÉCHOUÉE")
            for e in errors[:50]:
                print(f"  - {e}")
            return 1

        print(f"[{locale}] OK -> {target}")

    print("\n" + "=" * 72)
    print("TRADUCTION TERMINÉE")
    print("=" * 72)
    print(
        "Étape suivante :\n"
        "  python3 tools/translate_android.py --validate-only\n"
        "  ./gradlew :app:compileDebugKotlin"
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
