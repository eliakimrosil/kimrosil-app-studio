#!/usr/bin/env python3
"""
KimRosil App Studio - Catalog & Asset Integrity Validator
Single source of truth validation engine used by:
1. Git Pre-Push Hook (.git/hooks/pre-push)
2. Git Pre-Commit Hook (.git/hooks/pre-commit)
3. KimRosil App Studio Automation (manage_studio.sh / devops.py / release_officer.py)
4. GitHub Actions CI (.github/workflows/validate-catalog.yml)
"""

import json
import os
import sys

# Terminal Colors
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def find_repo_root():
    """Locate the root directory of kim-android-apps repository."""
    current_dir = os.path.dirname(os.path.abspath(__file__))
    parent_dir = os.path.abspath(os.path.join(current_dir, ".."))
    if os.path.exists(os.path.join(parent_dir, "apps.json")):
        return parent_dir
    if os.path.exists("apps.json"):
        return os.path.abspath(".")
    return None


def validate_catalog(repo_root=None):
    if repo_root is None:
        repo_root = find_repo_root()

    if not repo_root:
        print(f"{RED}[✗] Error: Unable to locate repository root containing apps.json{RESET}")
        return False

    catalog_path = os.path.join(repo_root, "apps.json")
    if not os.path.exists(catalog_path):
        print(f"{RED}[✗] Error: apps.json not found at {catalog_path}{RESET}")
        return False

    print(f"\n{BOLD}{CYAN}🔍 Validating KimRosil App Studio Catalog & Quality...{RESET}")
    print(f"Repository Root: {repo_root}")
    print(f"Catalog File:    {catalog_path}\n")

    # 1. Parse JSON
    try:
        with open(catalog_path, "r", encoding="utf-8") as f:
            apps = json.load(f)
    except Exception as e:
        print(f"{RED}[✗] JSON Syntax Error in apps.json: {e}{RESET}")
        return False

    if not isinstance(apps, list):
        print(f"{RED}[✗] Schema Error: apps.json must contain a root JSON array of apps.{RESET}")
        return False

    total_apps = len(apps)
    print(f"{BOLD}[*] Total Registered Applications: {total_apps}{RESET}")

    errors = []
    warnings = []

    # 2. Check for Duplicate Slugs
    seen_slugs = set()
    for idx, app in enumerate(apps):
        slug = app.get("slug")
        if not slug:
            errors.append(f"App at index {idx} has missing or empty 'slug'.")
            continue
        if slug in seen_slugs:
            errors.append(f"Duplicate slug detected: '{slug}'")
        seen_slugs.add(slug)

    # 3. Required Fields Check
    required_fields = [
        "slug",
        "title",
        "category",
        "tagline",
        "status",
        "tag",
        "apk_url",
        "landing_page",
        "privacy_policy",
        "icon",
    ]

    # 4. Detailed App-by-App Validation
    for app in apps:
        slug = app.get("slug", "unknown")
        
        # Check required fields
        for field in required_fields:
            if field not in app or not str(app[field]).strip():
                errors.append(f"[{slug}] Missing required field: '{field}'")

        # Validate Icon File Existence & Size
        icon_rel_path = app.get("icon", f"assets/icons/{slug}.svg")
        icon_abs_path = os.path.join(repo_root, icon_rel_path)

        if not os.path.exists(icon_abs_path):
            errors.append(f"[{slug}] Icon file missing on disk: {icon_rel_path}")
        else:
            try:
                size = os.path.getsize(icon_abs_path)
                if size < 50:
                    errors.append(f"[{slug}] Icon file is suspiciously small ({size} bytes): {icon_rel_path}")
            except Exception as e:
                errors.append(f"[{slug}] Cannot read icon file {icon_rel_path}: {e}")

        # Validate URL formats
        apk_url = app.get("apk_url", "")
        if apk_url and not apk_url.startswith("https://"):
            errors.append(f"[{slug}] Invalid apk_url (must start with https://): {apk_url}")

        landing_page = app.get("landing_page", "")
        if landing_page and not landing_page.startswith("https://"):
            errors.append(f"[{slug}] Invalid landing_page (must start with https://): {landing_page}")

        privacy_policy = app.get("privacy_policy", "")
        if privacy_policy and not privacy_policy.startswith("https://"):
            errors.append(f"[{slug}] Invalid privacy_policy (must start with https://): {privacy_policy}")

    # Output Summary
    if errors:
        print(f"\n{BOLD}{RED}❌ Catalog Validation FAILED with {len(errors)} error(s):{RESET}")
        for err in errors:
            print(f"  {RED}• {err}{RESET}")
        print(f"\n{YELLOW}Action required: Fix the listed errors before committing or pushing.{RESET}\n")
        return False

    if warnings:
        print(f"\n{BOLD}{YELLOW}⚠️  Validation passed with {len(warnings)} warning(s):{RESET}")
        for warn in warnings:
            print(f"  {YELLOW}• {warn}{RESET}")

    print(f"\n{BOLD}{GREEN}✅ All {total_apps} applications, manifests, and icons validated successfully!{RESET}\n")
    return True


if __name__ == "__main__":
    success = validate_catalog()
    sys.exit(0 if success else 1)
