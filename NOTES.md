# Developer Notes & Maintenance Guide

## Overview
This repository contains CloudStream extensions for BDIX-based services (Dflix, DhakaFlix, FtpBd). 

## Major Fixes (Dec 2025)
- **Dflix:**
    - **Robust Login:** Implemented automatic re-login if the session expires or redirects to the login page.
    - **URL Scheme Fix:** Added `fixUrl` to ensure NiceHttp never receives a scheme-less URL (fixed "Expected URL scheme 'http' or 'https'" error).
    - **Parsing Overhaul:** Updated CSS selectors for movies and series to match current website structure.
- **DhakaFlix:**
    - **Network Stability:** Added 10-15s timeouts and try-catch blocks to prevent app hangs when trying to connect to unreachable private BDIX IPs.
    - **Recursive Loading:** Implemented `parseDirectoryRecursive` to handle nested folders.
- **FtpBd:**
    - **SSL/Connectivity:** Switched to `http://server3.ftpbd.net` by default to avoid SSL handshake failures common on BDIX servers.
    - **Anti-Bot:** Added a standard `User-Agent` header to prevent requests from being blocked.

## Maintenance Workflow

### 1. Version Bumping
The project uses a custom `version = X` property at the top level of each extension's `build.gradle.kts`. 
- To update, increment this number. 
- You can use the `bump_version.py` script: `python3 bump_version.py`.

### 2. Login Logic (Dflix)
If Dflix stops loading links, check if `https://dflix.discoveryftp.net/login/demo` still works in a browser. The `login()` and `checkLogin()` methods in `DflixProvider.kt` are responsible for session management.

### 3. Build & Deployment
Pushing to the `main` branch triggers a GitHub Action that:
1. Builds the `.cs3` files.
2. Updates `plugins.json`.
3. Pushes artifacts to the `builds` branch.

**App Update Tip:** If CloudStream shows version `-1` or won't update, **uninstall the extension** and re-install it from the repository. This is usually necessary when the internal package name (namespace) changes.

## Repository URLs
- **Main Repo:** `https://github.com/salmanbappi/cloudstream-extensions`
- **CloudStream Repo URL:** `https://raw.githubusercontent.com/salmanbappi/cloudstream-extensions/main/repo.json`
