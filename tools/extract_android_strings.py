#!/usr/bin/env python3
"""
Deliriuum Android i18n extractor — V3
=====================================

Cette version est conçue pour le projet Deliriuum après analyse du rapport
réel V2 (31 fichiers Kotlin, 384 SAFE / 94 REVIEW / 716 SKIP).

Améliorations V2
----------------
1. Comprend les interpolations Kotlin contenant elles-mêmes des chaînes :
      "Envoyé à ${email ?: ""}. Clique ici."
2. Reconnaît les @Composable avec receiver :
      private fun RowScope.SafetyBadge(...)
3. Regroupe les phrases écrites comme :
      "première partie " +
      "deuxième partie " +
      "troisième partie"
   en UNE SEULE ressource traduisible.
4. Dans une fonction @Composable, utilise un Context de ressources capturé :
      _i18nContext.getString(R.string.xxx, ...)
   Ce choix reste valable dans les callbacks/lambdas non composables contenus
   dans la fonction.
5. Trois helpers Deliriuum purement UI sont automatiquement traités comme
   @Composable, car ils ne sont appelés que depuis l'UI :
      - HomeView.kt: auditPedagogy
      - HomeView.kt: pedagogicalAuditResult
      - GeckoBrowserActivity.kt: messageFor
6. Les chaînes réellement affichées depuis PrivacyAuditManager, AuthManager,
   TunnelManager et APIClient sont maintenant localisées via AppStrings.
7. Les chaînes techniques qui servent de protocole, de matching serveur ou
   d'initialisation restent volontairement non traduites.
8. Les espaces système « Réseaux sociaux » et « Vidéo » restent stockés avec
   leur kind technique, mais leur nom affiché est localisé dynamiquement.
9. Le résumé d'audit avec comptages utilise de vrais <plurals> Android.
10. Le fallback « ton adresse » à l'intérieur d'une interpolation est lui aussi
    externalisé afin qu'aucun fragment français ne survive dans l'expression.

Utilisation
-----------
    python3 tools/extract_android_strings.py --dry-run
    python3 tools/extract_android_strings.py --apply
    python3 tools/extract_android_strings.py --check

IMPORTANT
---------
Toujours faire --dry-run avant --apply.
"""

from __future__ import annotations

import argparse
import bisect
import datetime as dt
import hashlib
import html
import json
import re
import shutil
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

TRIPLE_QUOTE = '"""'
I18N_CONTEXT_NAME = "_i18nContext"

# Helpers Deliriuum qui ne fabriquent que du texte affiché et sont appelés
# depuis des composables. V2 peut donc les promouvoir sans changer la logique.
PROMOTE_TO_COMPOSABLE: dict[str, set[str]] = {
    "HomeView.kt": {
        "auditPedagogy",
        "pedagogicalAuditResult",
    },
    "GeckoBrowserActivity.kt": {
        "messageFor",
    },
}

TECHNICAL_ONLY_FILES = {
    "BaselineAuditActivity.kt",
    "DeliriumGeckoRuntime.kt",
    "KeychainStore.kt",
}

# Couches data dont les messages sont effectivement remontés à l'UI.
DATA_LOCALIZE_FILES = {
    "APIClient.kt",
    "AuthManager.kt",
    "PrivacyAuditManager.kt",
    "TunnelManager.kt",
}

# Chaînes humainement lisibles mais techniques : les traduire casserait soit
# une détection de message serveur, soit un invariant de démarrage, soit le
# nom technique du périphérique.
DATA_SKIP_EXACT: dict[str, set[str]] = {
    "AuthManager.kt": {
        "vérifiée",
    },
    "TunnelManager.kt": {
        "TunnelManager doit être initialisé dans MainActivity",
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "Device introuvable",
        "device not found",
    },
}

DATA_PLURAL_SPECIALS: dict[str, set[str]] = {
    "PrivacyAuditManager.kt": {
        "$detected police(s) détectée(s) sur $testedCount testées.",
        "$candidateCount candidat(s) ICE ont été observés.",
        "$uniqueHashCount rendus distincts ont été observés. Le verdict reste volontairement partiel.",
    },
}

# Ressources canoniques utilisées également par les patchs spéciaux.
EXPLICIT_STRING_RESOURCES = {
    "shortcut_space_social": "Réseaux sociaux",
    "shortcut_space_video": "Vidéo",
    "auth_fallback_email_address": "ton adresse",
}

EXPLICIT_PLURALS = {
    "home_audit_exposed_elements": {
        "one": "%1$d élément reste observable.",
        "other": "%1$d éléments restent observables.",
    },
    "home_audit_partial_protections": {
        "one": "%1$d protection est partielle.",
        "other": "%1$d protections sont partielles.",
    },
    "privacy_audit_fonts_detected": {
        "one": "%1$d police détectée sur %2$d testées.",
        "other": "%1$d polices détectées sur %2$d testées.",
    },
    "privacy_audit_ice_candidates_observed": {
        "one": "%1$d candidat ICE a été observé.",
        "other": "%1$d candidats ICE ont été observés.",
    },
    "privacy_audit_canvas_distinct_renders": {
        "one": "%1$d rendu distinct a été observé. Le verdict reste volontairement partiel.",
        "other": "%1$d rendus distincts ont été observés. Le verdict reste volontairement partiel.",
    },
}

DENY_CONTEXT_PATTERNS = [
    r"\bLog\.[diewv]\s*\(",
    r"\bandroid\.util\.Log\.[diewv]\s*\(",
    r"\bprintln\s*\(",
    r"\bprint\s*\(",
    r"\bUri\.parse\s*\(",
    r"\bURL\s*\(",
    r"\bRegex\s*\(",
    r"\bJSONObject\s*\(",
    r"\.put\s*\(",
    r"\.optString\s*\(",
    r"\.getStringExtra\s*\(",
    r"\bgetSharedPreferences\s*\(",
    r"\bputExtra\s*\(",
    r"\baddFlags\s*\(",
]

TECHNICAL_EXACT = {
    "privacy_audit",
    "payload",
    "type",
    "id",
    "url",
    "title",
    "kind",
    "shortcuts",
    "name",
    "iconKey",
    "GET",
    "POST",
    "UTF-8",
    "application/json",
    "Mozilla",
    "Atlantic/Reykjavik",
}

KNOWN_UI_SHORT_WORDS = {
    "oui", "non", "ok", "annuler", "fermer", "menu", "ajouter", "ajouté",
    "suivant", "précédent", "actualiser", "réessayer", "continuer",
    "refuser", "supprimer", "modifier", "terminé", "active", "inactive",
    "protégé", "visible", "partiel", "chargement", "connexion", "déconnexion",
    "créer", "vidéo",
}


@dataclass
class StringToken:
    start: int
    end: int
    raw_content: str
    line: int


@dataclass
class FunctionRange:
    start: int
    fun_start: int
    body_start: int
    body_end: int
    name: str
    composable: bool
    promoted: bool = False
    expression_body: bool = False
    eq_pos: int | None = None


@dataclass
class Candidate:
    path: Path
    start: int
    end: int
    line: int
    raw_content: str
    category: str       # SAFE / REVIEW / SKIP
    reason: str
    resource_text: str | None = None
    args: list[str] | None = None
    key: str | None = None
    function: FunctionRange | None = None


# ---------------------------------------------------------------------------
# BASICS
# ---------------------------------------------------------------------------

def line_starts(text: str) -> list[int]:
    starts = [0]
    for m in re.finditer("\n", text):
        starts.append(m.end())
    return starts


def line_number(starts: list[int], offset: int) -> int:
    return bisect.bisect_right(starts, offset)


def camel_to_snake(name: str) -> str:
    name = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    name = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", name)
    return name.lower()


def file_prefix(path: Path) -> str:
    stem = camel_to_snake(path.stem)
    for suffix in (
        "_activity", "_screen", "_view", "_component", "_layout",
        "_dialog", "_fragment", "_page"
    ):
        if stem.endswith(suffix):
            stem = stem[:-len(suffix)]
            break

    special = {
        "gecko_browser": "browser",
        "delirium_gecko": "browser",
        "deliriuum_gecko": "browser",
        "side_menu": "side_menu",
        "home": "home",
    }
    return special.get(stem, stem or "app")


def slugify(text: str, max_len: int = 56) -> str:
    s = re.sub(r"%\d+\$s", " ", text)
    s = re.sub(r"\\[ntr]", " ", s)
    s = html.unescape(s)
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.combining(ch))
    s = s.lower()
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    if not s:
        return "text"
    if s[0].isdigit():
        s = "text_" + s
    return s[:max_len].rstrip("_")


def is_probably_technical(raw: str) -> bool:
    stripped = raw.strip()
    if not stripped:
        return True

    low = stripped.lower()

    if stripped in TECHNICAL_EXACT:
        return True

    if low.startswith((
        "http://", "https://", "about:", "file:", "data:",
        "resource://", "moz-extension://"
    )):
        return True

    if low.startswith("bearer $"):
        return True

    if stripped.startswith("error="):
        return True

    if re.match(r"^[a-zA-Z]+/[a-zA-Z0-9.+_-]+$", stripped):
        return True

    # Clés / IDs techniques : un token unique sans espace.
    if re.match(r"^[a-zA-Z_][a-zA-Z0-9_.:/-]*$", stripped):
        if low not in KNOWN_UI_SHORT_WORDS:
            return True

    if "\\d" in stripped or "\\w" in stripped or "(?:" in stripped:
        return True

    if re.search(r"\b(?:com|org|androidx|mozilla)\.[A-Za-z0-9_.]+", stripped):
        return True

    if len(stripped) > 900:
        return True

    return False


def looks_human(raw: str) -> bool:
    s = raw.strip()
    if is_probably_technical(s):
        return False

    if not re.search(r"[A-Za-zÀ-ÖØ-öø-ÿ]", s):
        return False

    low = s.lower()
    if low in KNOWN_UI_SHORT_WORDS:
        return True

    return (
        bool(re.search(r"\s", s))
        or bool(re.search(r"[À-ÖØ-öø-ÿ]", s))
        or bool(re.search(r"[!?…,:;«»']", s))
        or (len(s) >= 4 and s[:1].isupper())
    )


def xml_escape_android(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", r"\'")
    return text


def xml_unescape_android(text: str) -> str:
    return html.unescape(text).replace(r"\'", "'")


# ---------------------------------------------------------------------------
# KOTLIN STRING SCANNER
# ---------------------------------------------------------------------------

def _skip_quoted_in_interpolation(text: str, i: int, quote: str) -> int:
    """Skip a regular Kotlin quoted string/char inside ${ ... }."""
    n = len(text)
    i += 1
    while i < n:
        if text[i] == "\\":
            i += 2
            continue
        if text[i] == quote:
            return i + 1
        i += 1
    return n


def _skip_triple_in_interpolation(text: str, i: int) -> int:
    j = text.find(TRIPLE_QUOTE, i + 3)
    return len(text) if j < 0 else j + 3


def _skip_braced_interpolation(text: str, i: int) -> int:
    """
    i points to '{' in ${...}. Returns the position just after the matching }.
    Handles nested braces and quoted strings inside the expression.
    """
    n = len(text)
    depth = 1
    i += 1

    while i < n and depth > 0:
        if text.startswith(TRIPLE_QUOTE, i):
            i = _skip_triple_in_interpolation(text, i)
            continue
        if text.startswith("//", i):
            j = text.find("\n", i + 2)
            i = n if j < 0 else j + 1
            continue
        if text.startswith("/*", i):
            level = 1
            i += 2
            while i < n and level:
                if text.startswith("/*", i):
                    level += 1
                    i += 2
                elif text.startswith("*/", i):
                    level -= 1
                    i += 2
                else:
                    i += 1
            continue
        if text[i] in ('"', "'"):
            i = _skip_quoted_in_interpolation(text, i, text[i])
            continue
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
        i += 1

    return i


def scan_regular_strings(text: str) -> list[StringToken]:
    """
    Scanner lexical Kotlin minimal, mais conscient des interpolations ${...}.

    Ignore :
    - commentaires
    - chars
    - strings triples
    """
    tokens: list[StringToken] = []
    starts = line_starts(text)

    i = 0
    n = len(text)
    state = "normal"
    block_depth = 0

    while i < n:
        if state == "normal":
            if text.startswith("//", i):
                state = "line_comment"
                i += 2
                continue
            if text.startswith("/*", i):
                state = "block_comment"
                block_depth = 1
                i += 2
                continue
            if text.startswith(TRIPLE_QUOTE, i):
                state = "triple"
                i += 3
                continue
            if text[i] == "'":
                state = "char"
                i += 1
                continue
            if text[i] == '"':
                start = i
                i += 1
                content_start = i

                while i < n:
                    if text[i] == "\\":
                        i += 2
                        continue

                    # IMPORTANT : les guillemets à l'intérieur de ${ ... }
                    # ne terminent pas la chaîne externe.
                    if text.startswith("${", i):
                        i = _skip_braced_interpolation(text, i + 1)
                        continue

                    if text[i] == '"':
                        tokens.append(
                            StringToken(
                                start=start,
                                end=i + 1,
                                raw_content=text[content_start:i],
                                line=line_number(starts, start),
                            )
                        )
                        i += 1
                        break

                    i += 1
                continue

            i += 1

        elif state == "line_comment":
            if text[i] == "\n":
                state = "normal"
            i += 1

        elif state == "block_comment":
            if text.startswith("/*", i):
                block_depth += 1
                i += 2
            elif text.startswith("*/", i):
                block_depth -= 1
                i += 2
                if block_depth == 0:
                    state = "normal"
            else:
                i += 1

        elif state == "triple":
            j = text.find(TRIPLE_QUOTE, i)
            if j < 0:
                return tokens
            i = j + 3
            state = "normal"

        elif state == "char":
            if text[i] == "\\":
                i += 2
            elif text[i] == "'":
                i += 1
                state = "normal"
            else:
                i += 1

    return tokens


# ---------------------------------------------------------------------------
# MASK + FUNCTIONS
# ---------------------------------------------------------------------------

def code_mask(text: str) -> str:
    """
    Même longueur que text. Les commentaires/strings deviennent des espaces.
    Les retours ligne sont conservés.

    IMPORTANT : on masque d'abord les chaînes Kotlin standards, puis on
    analyse CETTE COPIE déjà masquée. Ainsi une apostrophe contenue dans
    "l'application" ne peut jamais être prise pour le début d'un Char Kotlin.
    """
    chars = list(text)
    for tok in scan_regular_strings(text):
        for k in range(tok.start, tok.end):
            if chars[k] != "\n":
                chars[k] = " "

    # Les strings régulières sont maintenant invisibles au scanner suivant.
    base = "".join(chars)

    i = 0
    n = len(base)
    state = "normal"
    depth = 0

    def blank(a: int, b: int) -> None:
        for k in range(a, min(b, n)):
            if chars[k] != "\n":
                chars[k] = " "

    while i < n:
        if state == "normal":
            if base.startswith("//", i):
                blank(i, i + 2)
                state = "line_comment"
                i += 2
            elif base.startswith("/*", i):
                blank(i, i + 2)
                state = "block_comment"
                depth = 1
                i += 2
            elif base.startswith(TRIPLE_QUOTE, i):
                blank(i, i + 3)
                state = "triple"
                i += 3
            elif base[i] == "'":
                blank(i, i + 1)
                state = "char"
                i += 1
            else:
                i += 1

        elif state == "line_comment":
            if base[i] == "\n":
                state = "normal"
            else:
                blank(i, i + 1)
            i += 1

        elif state == "block_comment":
            if base.startswith("/*", i):
                blank(i, i + 2)
                depth += 1
                i += 2
            elif base.startswith("*/", i):
                blank(i, i + 2)
                depth -= 1
                i += 2
                if depth == 0:
                    state = "normal"
            else:
                blank(i, i + 1)
                i += 1

        elif state == "triple":
            if base.startswith(TRIPLE_QUOTE, i):
                blank(i, i + 3)
                i += 3
                state = "normal"
            else:
                blank(i, i + 1)
                i += 1

        elif state == "char":
            if base[i] == "\\":
                blank(i, min(i + 2, n))
                i += 2
            elif base[i] == "'":
                blank(i, i + 1)
                i += 1
                state = "normal"
            else:
                blank(i, i + 1)
                i += 1

    return "".join(chars)


FUN_PATTERN = re.compile(
    r"""
    (?P<prefix>
        (?:(?:public|private|internal|protected|inline|suspend|operator|infix|
             tailrec|external|open|final|override)\s+)*
    )
    fun\s+
    (?:
        [A-Za-z_][A-Za-z0-9_<>,?.\s]*\.
    )?
    (?P<name>[A-Za-z_][A-Za-z0-9_]*)\s*
    \(
    """,
    re.VERBOSE | re.MULTILINE,
)


def all_braced_functions(text: str) -> list[FunctionRange]:
    mask = code_mask(text)
    out: list[FunctionRange] = []

    for m in FUN_PATTERN.finditer(mask):
        name = m.group("name")
        open_paren = m.end() - 1
        paren_depth = 0
        close_paren = -1

        for i in range(open_paren, len(mask)):
            if mask[i] == "(":
                paren_depth += 1
            elif mask[i] == ")":
                paren_depth -= 1
                if paren_depth == 0:
                    close_paren = i
                    break

        if close_paren < 0:
            continue

        search_end = min(len(mask), close_paren + 1200)
        brace = mask.find("{", close_paren + 1, search_end)
        eq = mask.find("=", close_paren + 1, search_end)

        expression_body = eq >= 0 and (brace < 0 or eq < brace)

        # Pour une expression-body de forme "= when (...) { ... }",
        # l'accolade trouvée est celle du when et délimite l'expression.
        # C'est suffisant pour nos helpers UI promus.
        if brace < 0:
            continue

        depth = 0
        body_end = -1
        for i in range(brace, len(mask)):
            if mask[i] == "{":
                depth += 1
            elif mask[i] == "}":
                depth -= 1
                if depth == 0:
                    body_end = i + 1
                    break

        if body_end < 0:
            continue

        pre = text[max(0, m.start() - 500):m.start()]
        last_composable = pre.rfind("@Composable")
        last_close = max(pre.rfind("}"), pre.rfind(";"))
        composable = last_composable >= 0 and last_composable > last_close

        start = m.start()
        if composable:
            start = max(0, m.start() - (len(pre) - last_composable))

        out.append(
            FunctionRange(
                start=start,
                fun_start=m.start(),
                body_start=brace,
                body_end=body_end,
                name=name,
                composable=composable,
                promoted=False,
                expression_body=expression_body,
                eq_pos=eq if expression_body else None,
            )
        )

    return out


def localizable_functions(path: Path, text: str) -> list[FunctionRange]:
    funcs = all_braced_functions(text)
    promote = PROMOTE_TO_COMPOSABLE.get(path.name, set())

    for f in funcs:
        if not f.composable and f.name in promote:
            f.promoted = True

    return [f for f in funcs if f.composable or f.promoted]


def containing_function(
    offset: int,
    funcs: list[FunctionRange]
) -> FunctionRange | None:
    matches = [f for f in funcs if f.body_start < offset < f.body_end]
    if not matches:
        return None
    # Fonction la plus interne.
    return min(matches, key=lambda f: f.body_end - f.body_start)


# ---------------------------------------------------------------------------
# KOTLIN INTERPOLATION -> ANDROID PLACEHOLDERS
# ---------------------------------------------------------------------------

IDENT = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*")


def raw_to_android_format(raw: str) -> tuple[str, list[str]]:
    args: list[str] = []
    out: list[str] = []
    i = 0
    literal_start = 0

    def flush_literal(end: int) -> None:
        literal = raw[literal_start:end]
        # Dès qu'il y a des format args, Resources.getString fait un format.
        # Les % littéraux devront donc être doublés à la fin.
        out.append(literal)

    while i < len(raw):
        if raw[i] == "\\":
            i += 2
            continue

        if raw.startswith("${", i):
            flush_literal(i)
            start_expr = i + 2
            depth = 1
            j = start_expr
            quote = None

            while j < len(raw) and depth:
                ch = raw[j]
                if quote:
                    if ch == "\\":
                        j += 2
                        continue
                    if ch == quote:
                        quote = None
                    j += 1
                    continue

                if ch in ('"', "'"):
                    quote = ch
                    j += 1
                    continue
                if ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1

            if depth != 0:
                # Interpolation mal formée : ne touche pas.
                return raw, []

            expr = raw[start_expr:j].strip()
            args.append(expr)
            out.append(f"%{len(args)}$s")
            i = j + 1
            literal_start = i
            continue

        if raw[i] == "$":
            m = IDENT.match(raw, i + 1)
            if m:
                flush_literal(i)
                expr = m.group(0)
                args.append(expr)
                out.append(f"%{len(args)}$s")
                i = m.end()
                literal_start = i
                continue

        i += 1

    flush_literal(len(raw))
    result = "".join(out)

    if args:
        # Un % littéral devient %% sans toucher aux placeholders créés.
        sentinel = "\u0000PH"
        for idx in range(1, len(args) + 1):
            result = result.replace(f"%{idx}$s", f"{sentinel}{idx}\u0000")
        result = result.replace("%", "%%")
        for idx in range(1, len(args) + 1):
            result = result.replace(f"{sentinel}{idx}\u0000", f"%{idx}$s")

    return result, args


# ---------------------------------------------------------------------------
# CONCAT CHAINS
# ---------------------------------------------------------------------------

def pure_literal_chains(
    text: str,
    tokens: list[StringToken]
) -> list[list[StringToken]]:
    chains: list[list[StringToken]] = []
    current: list[StringToken] = []

    for a, b in zip(tokens, tokens[1:]):
        between = text[a.end:b.start]
        joined = bool(re.fullmatch(r"\s*\+\s*", between))

        if joined:
            if not current:
                current = [a, b]
            elif current[-1] is a:
                current.append(b)
            else:
                chains.append(current)
                current = [a, b]
        else:
            if current:
                chains.append(current)
                current = []

    if current:
        chains.append(current)

    # Déduplique les tokens qui pourraient avoir été ajoutés deux fois.
    normalized = []
    for chain in chains:
        seen = set()
        c = []
        for tok in chain:
            if tok.start not in seen:
                seen.add(tok.start)
                c.append(tok)
        if len(c) >= 2:
            normalized.append(c)

    return normalized


# ---------------------------------------------------------------------------
# CLASSIFICATION
# ---------------------------------------------------------------------------

def context_before(text: str, start: int, width: int = 260) -> str:
    return text[max(0, start - width):start]


def deny_context(before: str) -> str | None:
    tail = before[-220:]
    for pattern in DENY_CONTEXT_PATTERNS:
        if re.search(pattern, tail, re.MULTILINE):
            return pattern
    return None



def home_transparency_span(text: str) -> tuple[int, int] | None:
    """Zone de résumé audit qui doit utiliser les pluriels Android."""
    m = re.search(
        r"\bval\s+transparencyText\s*=\s*buildString\s*\{[\s\S]*?\}\s*\.trim\(\)",
        text,
        re.MULTILINE,
    )
    if not m:
        return None
    return (m.start(), m.end())


def in_span(offset: int, span: tuple[int, int] | None) -> bool:
    return span is not None and span[0] <= offset < span[1]


def normalize_special_args(path: Path, raw: str, args: list[str]) -> list[str]:
    """Externalise les rares textes UI contenus dans des expressions ${...}."""
    if path.name == "AuthScreen.kt":
        target = 'authManager.pendingVerificationEmail ?: "ton adresse"'
        return [
            arg.replace(
                target,
                'authManager.pendingVerificationEmail ?: '
                '_i18nContext.getString(R.string.auth_fallback_email_address)'
            )
            for arg in args
        ]

    if path.name == "HomeView.kt":
        return [
            (
                "localizedShortcutSpaceName(_i18nContext, space)"
                if arg == "space.name"
                else arg
            )
            for arg in args
        ]

    return args


def classify_file(path: Path, text: str) -> list[Candidate]:
    funcs = localizable_functions(path, text)
    tokens = scan_regular_strings(text)
    transparency_span = (
        home_transparency_span(text)
        if path.name == "HomeView.kt"
        else None
    )

    chain_by_start: dict[int, list[StringToken]] = {}
    member_starts: set[int] = set()

    for chain in pure_literal_chains(text, tokens):
        chain_by_start[chain[0].start] = chain
        member_starts.update(tok.start for tok in chain[1:])

    out: list[Candidate] = []

    for tok in tokens:
        if tok.start in member_starts:
            continue

        chain = chain_by_start.get(tok.start)
        if chain:
            start = chain[0].start
            end = chain[-1].end
            raw = "".join(x.raw_content for x in chain)
            line = chain[0].line
        else:
            start = tok.start
            end = tok.end
            raw = tok.raw_content
            line = tok.line

        # Pluriels métiers de PrivacyAuditManager.
        if raw in DATA_PLURAL_SPECIALS.get(path.name, set()):
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", "remplacé par plurals Android métier"
                )
            )
            continue

        # Résumé d'audit : traité globalement par <plurals>.
        if in_span(start, transparency_span):
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", "remplacé par plurals Android"
                )
            )
            continue

        # Les noms bruts des deux espaces système restent des valeurs internes
        # persistées. Leur rendu utilisateur est localisé par ShortcutSpaceKind.
        if (
            path.name == "HomeView.kt"
            and raw in {"Réseaux sociaux", "Vidéo"}
            and containing_function(start, funcs) is None
        ):
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", "nom système persisté ; affichage localisé dynamiquement"
                )
            )
            continue

        if not looks_human(raw):
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", "technique/non-UI"
                )
            )
            continue

        before = context_before(text, start)
        denied = deny_context(before)
        if denied:
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", f"contexte technique: {denied}"
                )
            )
            continue

        fn = containing_function(start, funcs)
        resource_text, args = raw_to_android_format(raw)
        args = normalize_special_args(path, raw, args)

        if fn is not None:
            reason = (
                "UI Compose — phrase concaténée regroupée"
                if chain
                else (
                    "UI helper promu @Composable"
                    if fn.promoted
                    else "UI dans @Composable"
                )
            )
            out.append(
                Candidate(
                    path=path,
                    start=start,
                    end=end,
                    line=line,
                    raw_content=raw,
                    category="SAFE",
                    reason=reason,
                    resource_text=resource_text,
                    args=args,
                    function=fn,
                )
            )
            continue

        # Les fichiers runtime/audit bootstrap internes listés ici sont
        # techniques : leurs messages ne sont pas destinés à l'interface.
        if path.name in TECHNICAL_ONLY_FILES:
            out.append(
                Candidate(
                    path, start, end, line, raw,
                    "SKIP", "fichier technique interne"
                )
            )
            continue

        if path.name in DATA_LOCALIZE_FILES:
            if raw in DATA_SKIP_EXACT.get(path.name, set()):
                out.append(
                    Candidate(
                        path, start, end, line, raw,
                        "SKIP", "chaîne data technique / matching serveur"
                    )
                )
            else:
                out.append(
                    Candidate(
                        path=path,
                        start=start,
                        end=end,
                        line=line,
                        raw_content=raw,
                        category="SAFE",
                        reason="message data affiché à l'utilisateur",
                        resource_text=resource_text,
                        args=args,
                        function=None,
                    )
                )
            continue

        out.append(
            Candidate(
                path=path,
                start=start,
                end=end,
                line=line,
                raw_content=raw,
                category="REVIEW",
                reason="texte humain hors UI Compose",
                resource_text=resource_text,
                args=args,
            )
        )

    return out


# ---------------------------------------------------------------------------
# XML / REGISTRY
# ---------------------------------------------------------------------------

STRING_RE = re.compile(
    r'<string\s+name="([^"]+)"(?:\s+[^>]*)?>(.*?)</string>',
    re.DOTALL,
)


def read_existing_strings(path: Path) -> tuple[dict[str, str], dict[str, str]]:
    by_key: dict[str, str] = {}
    by_value: dict[str, str] = {}

    if not path.exists():
        return by_key, by_value

    text = path.read_text(encoding="utf-8")
    for name, value in STRING_RE.findall(text):
        clean = xml_unescape_android(value.strip())
        by_key[name] = clean
        by_value.setdefault(clean, name)

    return by_key, by_value


def load_registry(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return {str(k): str(v) for k, v in data.items()}
    except Exception:
        return {}


def unique_key(
    path: Path,
    resource_text: str,
    existing_by_key: dict[str, str],
    existing_by_value: dict[str, str],
    registry: dict[str, str],
) -> str:
    if resource_text in existing_by_value:
        return existing_by_value[resource_text]

    if resource_text in registry:
        key = registry[resource_text]
        if key not in existing_by_key or existing_by_key[key] == resource_text:
            return key

    prefix = file_prefix(path)
    slug = slugify(resource_text)
    base = re.sub(r"_+", "_", f"{prefix}_{slug}").strip("_")[:80].rstrip("_")
    key = base

    if key in existing_by_key and existing_by_key[key] != resource_text:
        digest = hashlib.sha1(resource_text.encode("utf-8")).hexdigest()[:7]
        key = f"{base[:70]}_{digest}"

    i = 2
    original = key
    while key in existing_by_key and existing_by_key[key] != resource_text:
        key = f"{original}_{i}"
        i += 1

    return key


def append_strings_xml(
    path: Path,
    entries: list[tuple[str, str]],
    plurals: dict[str, dict[str, str]] | None = None,
) -> None:
    plurals = plurals or {}
    if not entries and not plurals:
        return

    path.parent.mkdir(parents=True, exist_ok=True)

    if not path.exists():
        path.write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<resources>\n'
            '</resources>\n',
            encoding="utf-8",
        )

    original = path.read_text(encoding="utf-8")
    closing = original.rfind("</resources>")
    if closing < 0:
        raise RuntimeError(f"{path} ne contient pas </resources>")

    block = [
        "\n    <!-- Ajouté automatiquement par extract_android_strings.py V3 -->\n"
    ]
    for key, value in entries:
        block.append(
            f'    <string name="{key}">{xml_escape_android(value)}</string>\n'
        )

    for plural_name, quantities in plurals.items():
        if re.search(
            rf'<plurals\s+name="{re.escape(plural_name)}"',
            original,
        ):
            continue

        block.append(f'    <plurals name="{plural_name}">\n')
        for quantity in ("zero", "one", "two", "few", "many", "other"):
            if quantity in quantities:
                value = quantities[quantity]
                block.append(
                    f'        <item quantity="{quantity}">'
                    f'{xml_escape_android(value)}</item>\n'
                )
        block.append("    </plurals>\n")

    updated = original[:closing] + "".join(block) + original[closing:]
    path.write_text(updated, encoding="utf-8")


# ---------------------------------------------------------------------------
# SOURCE REWRITE
# ---------------------------------------------------------------------------

def app_strings_get(key: str, args: list[str]) -> str:
    if args:
        return (
            f"AppStrings.get(R.string.{key}, "
            + ", ".join(args)
            + ")"
        )
    return f"AppStrings.get(R.string.{key})"


def context_get_string(key: str, args: list[str]) -> str:
    if args:
        return (
            f"{I18N_CONTEXT_NAME}.getString(R.string.{key}, "
            + ", ".join(args)
            + ")"
        )
    return f"{I18N_CONTEXT_NAME}.getString(R.string.{key})"


def function_indent(text: str, fun_start: int) -> str:
    line_start = text.rfind("\n", 0, fun_start) + 1
    m = re.match(r"[ \t]*", text[line_start:fun_start])
    return m.group(0) if m else ""


def ensure_imports(
    text: str,
    need_local_context: bool,
    need_composable_import: bool,
    need_app_strings: bool = False,
) -> str:
    imports = []

    if need_local_context and "import androidx.compose.ui.platform.LocalContext" not in text:
        imports.append("import androidx.compose.ui.platform.LocalContext")

    if "import com.deliriuum.app.R" not in text:
        imports.append("import com.deliriuum.app.R")

    if (
        need_app_strings
        and "import com.deliriuum.app.i18n.AppStrings" not in text
    ):
        imports.append("import com.deliriuum.app.i18n.AppStrings")

    has_composable = (
        "import androidx.compose.runtime.Composable" in text
        or "import androidx.compose.runtime.*" in text
    )
    if need_composable_import and not has_composable:
        imports.append("import androidx.compose.runtime.Composable")

    if not imports:
        return text

    lines = text.splitlines(True)
    import_indexes = [
        i for i, line in enumerate(lines)
        if line.startswith("import ")
    ]

    if import_indexes:
        idx = import_indexes[-1] + 1
    else:
        package_indexes = [
            i for i, line in enumerate(lines)
            if line.startswith("package ")
        ]
        idx = (package_indexes[0] + 1) if package_indexes else 0

    lines.insert(idx, "".join(x + "\n" for x in imports))
    return "".join(lines)


def rewrite_file(text: str, candidates: list[Candidate]) -> str:
    safe = [c for c in candidates if c.category == "SAFE"]
    if not safe:
        return text

    edits: list[tuple[int, int, str]] = []

    # Remplacements de chaînes.
    need_app_strings = False

    for c in safe:
        assert c.key

        if c.function is None:
            replacement = app_strings_get(c.key, c.args or [])
            need_app_strings = True
        else:
            replacement = context_get_string(c.key, c.args or [])

        edits.append(
            (
                c.start,
                c.end,
                replacement,
            )
        )

    # Fonctions concernées.
    functions: dict[tuple[int, int], FunctionRange] = {}
    for c in safe:
        if c.function is not None:
            functions[(c.function.fun_start, c.function.body_start)] = c.function

    for f in functions.values():
        indent = function_indent(text, f.fun_start)

        if f.expression_body and f.promoted and f.eq_pos is not None:
            # Transforme :
            #   fun x(...): T = when (...) { ... }
            # en :
            #   @Composable
            #   fun x(...): T {
            #       val _i18nContext = LocalContext.current
            #       return when (...) { ... }
            #   }
            opening = (
                "{\n"
                + indent
                + "    val "
                + I18N_CONTEXT_NAME
                + " = LocalContext.current\n"
                + indent
                + "    return "
            )
            edits.append((f.eq_pos, f.eq_pos + 1, opening))
            edits.append((f.body_end, f.body_end, "\n" + indent + "}"))
        else:
            # Injection du Context local une seule fois.
            body_preview = text[f.body_start:min(f.body_end, f.body_start + 500)]
            if I18N_CONTEXT_NAME not in body_preview:
                insertion = (
                    "\n"
                    + indent
                    + "    val "
                    + I18N_CONTEXT_NAME
                    + " = LocalContext.current\n"
                )
                edits.append((f.body_start + 1, f.body_start + 1, insertion))

        # Promotion explicitement validée des helpers UI Deliriuum.
        if f.promoted:
            line_start = text.rfind("\n", 0, f.fun_start) + 1
            existing_before = text[max(0, line_start - 200):f.fun_start]
            if "@Composable" not in existing_before:
                edits.append(
                    (
                        line_start,
                        line_start,
                        indent + "@Composable\n",
                    )
                )

    # Applique du bas vers le haut.
    for start, end, replacement in sorted(edits, key=lambda x: x[0], reverse=True):
        text = text[:start] + replacement + text[end:]

    text = ensure_imports(
        text,
        need_local_context=bool(functions),
        need_composable_import=any(f.promoted for f in functions.values()),
        need_app_strings=need_app_strings,
    )

    return text



def patch_homeview_specials(text: str) -> str:
    # 1) Noms SOCIAL / VIDEO localisés selon ShortcutSpaceKind.
    # 2) Résumé d'audit avec de vrais pluriels Android.
    if "private data class ShortcutSpace(" not in text:
        return text

    text = text.replace(
        "name = space.name,\n                                iconKey = space.iconKey",
        "name = localizedShortcutSpaceName(context, space),\n"
        "                                iconKey = space.iconKey",
    )

    text = text.replace(
        "title = space.name,\n        subtitle = subtitle",
        "title = localizedShortcutSpaceName(_i18nContext, space),\n"
        "        subtitle = subtitle",
    )

    helper_marker = "private fun localizedShortcutSpaceName("
    if helper_marker not in text:
        anchor = "private class ShortcutSpaceStore("
        idx = text.find(anchor)
        if idx >= 0:
            helper = """private fun localizedShortcutSpaceName(
    context: android.content.Context,
    space: ShortcutSpace
): String =
    when (space.kind) {
        ShortcutSpaceKind.SOCIAL ->
            context.getString(R.string.shortcut_space_social)

        ShortcutSpaceKind.VIDEO ->
            context.getString(R.string.shortcut_space_video)

        ShortcutSpaceKind.CUSTOM ->
            space.name
    }


"""
            text = text[:idx] + helper + text[idx:]

    span = home_transparency_span(text)
    if span is not None:
        line_start = text.rfind("\n", 0, span[0]) + 1
        match = re.match(r"[ \t]*", text[line_start:span[0]])
        indent = match.group(0) if match else ""

        new = (
            "val transparencyText =\n"
            + indent + "    buildList {\n"
            + indent + "        if (auditState.exposedCount > 0) {\n"
            + indent + "            add(\n"
            + indent + "                " + I18N_CONTEXT_NAME + ".resources.getQuantityString(\n"
            + indent + "                    R.plurals.home_audit_exposed_elements,\n"
            + indent + "                    auditState.exposedCount,\n"
            + indent + "                    auditState.exposedCount\n"
            + indent + "                )\n"
            + indent + "            )\n"
            + indent + "        }\n\n"
            + indent + "        if (auditState.partialCount > 0) {\n"
            + indent + "            add(\n"
            + indent + "                " + I18N_CONTEXT_NAME + ".resources.getQuantityString(\n"
            + indent + "                    R.plurals.home_audit_partial_protections,\n"
            + indent + "                    auditState.partialCount,\n"
            + indent + "                    auditState.partialCount\n"
            + indent + "                )\n"
            + indent + "            )\n"
            + indent + "        }\n"
            + indent + "    }\n"
            + indent + "        .joinToString(\" \")"
        )
        text = text[:span[0]] + new + text[span[1]:]

    return text



def patch_privacy_audit_plurals(text: str) -> str:
    replacements = {
        '"$detected police(s) détectée(s) sur $testedCount testées."':
            "AppStrings.quantity("
            "R.plurals.privacy_audit_fonts_detected, "
            "detected, detected, testedCount"
            ")",

        '"$candidateCount candidat(s) ICE ont été observés."':
            "AppStrings.quantity("
            "R.plurals.privacy_audit_ice_candidates_observed, "
            "candidateCount, candidateCount"
            ")",

        '"$uniqueHashCount rendus distincts ont été observés. '
        'Le verdict reste volontairement partiel."':
            "AppStrings.quantity("
            "R.plurals.privacy_audit_canvas_distinct_renders, "
            "uniqueHashCount, uniqueHashCount"
            ")",
    }

    for old, new in replacements.items():
        text = text.replace(old, new)

    if "AppStrings.quantity(" in text:
        text = ensure_imports(
            text,
            need_local_context=False,
            need_composable_import=False,
            need_app_strings=True,
        )

    return text


APP_STRINGS_SOURCE = r"""package com.deliriuum.app.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Accès aux ressources traduites depuis les couches non-Compose.
 *
 * null / "auto" = langue du système.
 * Une balise BCP-47 ("en", "de", "pt-BR", "zh-CN"...) force la langue
 * pour les lectures effectuées via AppStrings.
 *
 * Le sélecteur de langue de la sidebar utilisera cette même préférence.
 */
object AppStrings {

    private const val PREFS_NAME =
        "deliriuum_i18n"

    private const val KEY_LANGUAGE =
        "language_tag"

    const val AUTOMATIC =
        "auto"

    @Volatile
    private var appContext: Context? =
        null

    fun initialize(context: Context) {
        appContext =
            context.applicationContext
    }

    fun currentLanguageTag(): String? {
        val context =
            checkNotNull(appContext)

        val stored =
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_LANGUAGE,
                    AUTOMATIC
                )
                ?: AUTOMATIC

        return stored
            .takeUnless {
                it == AUTOMATIC
            }
    }

    fun setLanguageTag(tag: String?) {
        val context =
            checkNotNull(appContext)

        val normalized =
            tag
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty() &&
                            it != AUTOMATIC
                }

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LANGUAGE,
                normalized ?: AUTOMATIC
            )
            .apply()
    }

    fun localizedContext(
        base: Context? = null
    ): Context {
        val context =
            base?.applicationContext
                ?: checkNotNull(appContext)

        val tag =
            currentLanguageTag()
                ?: return context

        val configuration =
            Configuration(
                context.resources.configuration
            )

        configuration.setLocale(
            Locale.forLanguageTag(tag)
        )

        return context
            .createConfigurationContext(
                configuration
            )
    }

    fun get(
        @StringRes id: Int,
        vararg args: Any
    ): String {
        val context =
            localizedContext()

        return if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args)
        }
    }

    fun quantity(
        @PluralsRes id: Int,
        quantity: Int,
        vararg args: Any
    ): String {
        val context =
            localizedContext()

        return context.resources.getQuantityString(
            id,
            quantity,
            *args
        )
    }
}
"""


def patch_main_activity_for_app_strings(text: str) -> str:
    # Initialise AppStrings avant les managers qui peuvent construire
    # des messages destinés à l'utilisateur.
    if "AppStrings.initialize(" in text:
        return text

    if "import com.deliriuum.app.i18n.AppStrings" not in text:
        lines = text.splitlines(True)
        import_indexes = [
            i for i, line in enumerate(lines)
            if line.startswith("import ")
        ]
        idx = import_indexes[-1] + 1 if import_indexes else 1
        lines.insert(
            idx,
            "import com.deliriuum.app.i18n.AppStrings\n"
        )
        text = "".join(lines)

    marker = "KeychainStore.initialize("
    idx = text.find(marker)
    if idx < 0:
        raise RuntimeError(
            "Impossible de trouver KeychainStore.initialize dans "
            "MainActivity.kt pour initialiser AppStrings."
        )

    line_start = text.rfind("\n", 0, idx) + 1
    match = re.match(r"[ \t]*", text[line_start:idx])
    indent = match.group(0) if match else ""

    init = (
        indent + "AppStrings.initialize(\n"
        + indent + "    applicationContext\n"
        + indent + ")\n\n"
    )

    return text[:line_start] + init + text[line_start:]


# ---------------------------------------------------------------------------
# REPORT / BACKUP
# ---------------------------------------------------------------------------

def printable_raw(raw: str, limit: int = 180) -> str:
    raw = raw.replace("\n", r"\n")
    if len(raw) > limit:
        return raw[:limit - 1] + "…"
    return raw


def write_report(
    path: Path,
    root: Path,
    all_candidates: list[Candidate],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    groups = {
        "SAFE": [c for c in all_candidates if c.category == "SAFE"],
        "REVIEW": [c for c in all_candidates if c.category == "REVIEW"],
        "SKIP": [c for c in all_candidates if c.category == "SKIP"],
    }

    lines = [
        "DELIRIIUM — RAPPORT I18N ANDROID V3\n",
        "=" * 72 + "\n",
        f"SAFE   : {len(groups['SAFE'])}\n",
        f"REVIEW : {len(groups['REVIEW'])}\n",
        f"SKIP   : {len(groups['SKIP'])}\n\n",
        "SAFE = conversion automatique prévue\n",
        "REVIEW = cas non classé restant ; V3 vise normalement 0\n",
        "SKIP = chaîne technique / non traduisible\n\n",
    ]

    for category in ("SAFE", "REVIEW"):
        lines.append("\n" + category + "\n")
        lines.append("-" * 72 + "\n")

        for c in groups[category]:
            try:
                rel = c.path.relative_to(root)
            except ValueError:
                rel = c.path

            key_text = f" -> {c.key}" if c.key else ""
            lines.append(
                f"{rel}:{c.line}  [{c.reason}]{key_text}\n"
                f'    "{printable_raw(c.raw_content)}"\n'
            )

    path.write_text("".join(lines), encoding="utf-8")


def backup_files(root: Path, files: Iterable[Path]) -> Path:
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_root = root / ".i18n_backup" / stamp

    for src in files:
        if not src.exists():
            continue
        try:
            rel = src.relative_to(root)
        except ValueError:
            rel = Path(src.name)
        dest = backup_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dest)

    return backup_root


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser()

    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--dry-run", action="store_true")
    mode.add_argument("--apply", action="store_true")
    mode.add_argument("--check", action="store_true")

    parser.add_argument("--root", default=".")
    parser.add_argument("--source", default="app/src/main/java")
    parser.add_argument(
        "--strings",
        default="app/src/main/res/values/strings.xml",
    )
    parser.add_argument(
        "--report",
        default="build/i18n-report-v3.txt",
    )
    parser.add_argument(
        "--registry",
        default="tools/i18n_registry.json",
    )
    parser.add_argument("--include", action="append", default=[])
    parser.add_argument("--exclude", action="append", default=[])

    args = parser.parse_args()

    root = Path(args.root).expanduser().resolve()
    source_root = (root / args.source).resolve()
    strings_path = (root / args.strings).resolve()
    report_path = (root / args.report).resolve()
    registry_path = (root / args.registry).resolve()

    if not source_root.exists():
        print(f"ERREUR: source introuvable: {source_root}", file=sys.stderr)
        return 2

    kotlin_files = sorted(source_root.rglob("*.kt"))

    if args.include:
        kotlin_files = [
            p for p in kotlin_files
            if any(x in str(p.relative_to(root)) for x in args.include)
        ]

    if args.exclude:
        kotlin_files = [
            p for p in kotlin_files
            if not any(x in str(p.relative_to(root)) for x in args.exclude)
        ]

    existing_by_key, existing_by_value = read_existing_strings(strings_path)
    registry = load_registry(registry_path)

    for explicit_key, explicit_value in EXPLICIT_STRING_RESOURCES.items():
        registry[explicit_value] = explicit_key

    texts: dict[Path, str] = {}
    all_candidates: list[Candidate] = []

    for path in kotlin_files:
        source = path.read_text(encoding="utf-8")
        texts[path] = source
        all_candidates.extend(classify_file(path, source))

    # IMPORTANT V3 : les SAFE obtiennent leurs clés en premier.
    # Une chaîne visible dans l'UI ne récupère donc pas un nom basé sur un
    # fichier data simplement parce que la même phrase y existe aussi.
    ordered = (
        [c for c in all_candidates if c.category == "SAFE"]
        + [c for c in all_candidates if c.category == "REVIEW"]
    )

    planned_by_value = dict(existing_by_value)
    planned_by_key = dict(existing_by_key)

    for explicit_key, explicit_value in EXPLICIT_STRING_RESOURCES.items():
        planned_by_value[explicit_value] = explicit_key
        planned_by_key.setdefault(explicit_key, explicit_value)

    for c in ordered:
        if c.resource_text is None:
            continue

        if c.resource_text in planned_by_value:
            c.key = planned_by_value[c.resource_text]
            continue

        key = unique_key(
            c.path,
            c.resource_text,
            planned_by_key,
            planned_by_value,
            registry,
        )
        c.key = key
        planned_by_value[c.resource_text] = key
        planned_by_key[key] = c.resource_text
        registry[c.resource_text] = key

    write_report(report_path, root, all_candidates)

    safe = [c for c in all_candidates if c.category == "SAFE"]
    review = [c for c in all_candidates if c.category == "REVIEW"]
    skipped = [c for c in all_candidates if c.category == "SKIP"]

    print(f"Kotlin analysés : {len(kotlin_files)}")
    print(f"SAFE            : {len(safe)}")
    print(f"REVIEW          : {len(review)}")
    print(f"SKIP            : {len(skipped)}")
    print(f"Rapport         : {report_path}")

    if args.dry_run:
        print("\nAucun fichier source modifié (--dry-run).")
        return 0

    if args.check:
        if safe or review:
            print(
                "\nÉCHEC I18N : des chaînes utilisateur potentielles restent dans le code.",
                file=sys.stderr,
            )
            return 1

        print("\nOK : aucune chaîne UI détectée.")
        return 0

    # APPLY -----------------------------------------------------------------
    by_file: dict[Path, list[Candidate]] = {}
    for c in safe:
        by_file.setdefault(c.path, []).append(c)

    changed_paths = []
    rewritten: dict[Path, str] = {}

    for path, candidates in by_file.items():
        new_source = rewrite_file(texts[path], candidates)

        if path.name == "HomeView.kt":
            new_source = patch_homeview_specials(new_source)

        if path.name == "PrivacyAuditManager.kt":
            new_source = patch_privacy_audit_plurals(new_source)

        rewritten[path] = new_source
        if new_source != texts[path]:
            changed_paths.append(path)

    for path, source in texts.items():
        if path.name == "HomeView.kt" and path not in rewritten:
            new_source = patch_homeview_specials(source)
            rewritten[path] = new_source
            if new_source != source:
                changed_paths.append(path)

        if path.name == "PrivacyAuditManager.kt" and path not in rewritten:
            new_source = patch_privacy_audit_plurals(source)
            rewritten[path] = new_source
            if new_source != source:
                changed_paths.append(path)

    # XML : seulement les valeurs effectivement appliquées.
    new_entries: list[tuple[str, str]] = []
    seen_keys = set(existing_by_key)

    for explicit_key, explicit_value in EXPLICIT_STRING_RESOURCES.items():
        if explicit_key not in seen_keys:
            new_entries.append((explicit_key, explicit_value))
            seen_keys.add(explicit_key)

    for c in safe:
        assert c.key and c.resource_text is not None
        if c.key not in seen_keys:
            new_entries.append((c.key, c.resource_text))
            seen_keys.add(c.key)

    app_strings_path = (
        source_root
        / "com"
        / "deliriuum"
        / "app"
        / "i18n"
        / "AppStrings.kt"
    )

    main_activity_candidates = list(
        source_root.rglob("MainActivity.kt")
    )
    if len(main_activity_candidates) != 1:
        raise RuntimeError(
            "V3 attend exactement un MainActivity.kt ; trouvé : "
            + str(len(main_activity_candidates))
        )

    main_activity_path = main_activity_candidates[0]
    main_activity_source = (
        rewritten.get(main_activity_path)
        or texts.get(main_activity_path)
        or main_activity_path.read_text(encoding="utf-8")
    )

    patched_main_activity = patch_main_activity_for_app_strings(
        main_activity_source
    )
    rewritten[main_activity_path] = patched_main_activity

    if (
        patched_main_activity
        != main_activity_path.read_text(encoding="utf-8")
        and main_activity_path not in changed_paths
    ):
        changed_paths.append(main_activity_path)

    backup_targets = list(dict.fromkeys(changed_paths))
    if strings_path.exists():
        backup_targets.append(strings_path)
    if registry_path.exists():
        backup_targets.append(registry_path)
    if app_strings_path.exists():
        backup_targets.append(app_strings_path)

    backup_root = backup_files(root, backup_targets)

    for path in changed_paths:
        path.write_text(rewritten[path], encoding="utf-8")

    app_strings_path.parent.mkdir(parents=True, exist_ok=True)
    app_strings_path.write_text(
        APP_STRINGS_SOURCE,
        encoding="utf-8",
    )

    append_strings_xml(
        strings_path,
        new_entries,
        plurals=EXPLICIT_PLURALS,
    )

    registry_path.parent.mkdir(parents=True, exist_ok=True)
    registry_path.write_text(
        json.dumps(
            registry,
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        ) + "\n",
        encoding="utf-8",
    )

    print(f"\nFichiers Kotlin modifiés : {len(changed_paths)}")
    print(f"Clés XML ajoutées        : {len(new_entries)}")
    print(f"Plurals gérés            : {len(EXPLICIT_PLURALS)}")
    print(f"Helper AppStrings        : {app_strings_path}")
    print(f"Sauvegarde               : {backup_root}")

    if review:
        print(
            f"\nIl reste {len(review)} chaîne(s) REVIEW.\n"
            f"Elles n'ont PAS été modifiées.\n"
            f"Rapport : {report_path}"
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
