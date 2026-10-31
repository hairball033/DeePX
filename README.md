# DeePX (DeePX LSPosed)

DeePX is an independent LSPosed/Xposed module for the official DeepSeek Android app. It runs inside
the DeepSeek process and provides a module settings entry, a Miuix-styled host UI and feature
enhancements. It is based on [Deekseep LSPosed](https://github.com/lllucccian/Deekseep) and now
targets **DeepSeek 2.5.3**. It is not made by or affiliated with DeepSeek.

[简体中文](README_CN.md) | [Changelog](CHANGELOG.md)

---

### License & Prohibited Use (read this first)

This project is licensed under **[GPL-3.0-only](LICENSE)**. By using, modifying or distributing it
you agree to the following:

- **No paid distribution**: you may not sell this project (source, built APK or derivatives) in any
  form, including paid builds, paid groups / membership channels, reselling, bundling, or
  "donation" schemes used as a de-facto price.
- **No closed source**: every modified or derivative build must **publish its complete source**,
  keep the original author credits, copyright notices and source links, and stay under
  **GPL-3.0** (copyleft).
- **No impersonation**: do not impersonate the author or maintainer, and do not remove or alter the
  copyright and attribution information.

Violating these terms is a violation of the GPL license, and the author reserves the right to pursue
it.

---

## Current Version

- Module version: **1.0.0 Beta (20260926)**
- Host: Mainland DeepSeek **2.5.3** (versionName 2.5.3 / versionCode 275)
- Build config: `minSdk 24`, `targetSdk 37`, `compileSdk 37`

## Requirements

- Android 7.0 or newer (API 24+).
- Official DeepSeek package `com.deepseek.chat`.
- LSPosed/Xposed able to load the traditional Xposed entry.
- Root, or the permissions required by your LSPosed/Xposed setup; the Agent (device command)
  features need Shizuku or root.

## Features

- **Content & privacy**: safety-review removal (keeps the pre-rewrite answer), opt-out of data used
  for experience improvement, anti-ban, local mute (experimental).
- **Accounts & login**: unlock Google and password/email login (usable on mainland networks),
  unlock WeChat and mobile login (usable on overseas networks), multi-account manager, registration
  and password login bridges.
- **Device & risk control**: lightweight sanitisation by default (risk detector results zeroed);
  "bypass risk SDK" switches to the new-device identity mode (spoofed identifiers + one-time local
  identity reset); "reset device ID" performs a one-tap rebirth (wipe identity, stop DeepSeek).
- **Agent & device control**: Shizuku backend (permission requested before use), terminal commands,
  background keep-alive.
- **Remote & updates**: announcements and version updates (with version comparison, forced update
  cannot be dismissed), Local API (HTTP/HTTPS with CA verification).
- **UI**: Miuix-styled settings page, task console and dialogs, bilingual (Chinese/English).

## Before Installing

1. Check the DeepSeek package name and versionCode in Android app info.
2. Back up important conversations and local files.
3. Install and configure LSPosed/Xposed first, then enable this module's scope for DeepSeek.
4. Remove DeepSeek's battery restrictions if you need background requests or notifications.

## Repository Layout

| Directory | Purpose |
|---|---|
| `compose-launcher/` | Companion app (Kotlin + Compose + Miuix UI): settings and management UI |
| `module/` | Core hooks (Java): login, risk control, content, Agent, Local API, in-host settings page |
| `module-legacy/` | Compatibility adapters for older host versions |
| `module-universal/` | Universal packaging (Magisk/KernelSU template, resources, assets) |
| `scripts/` | Build, test and debug scripts |
| `third_party/` | Third-party licenses and runtime assets (Shizuku rish etc.) |

## Disclaimer

This module is for personal study, research and private use only. Do not use it for anything
unlawful. Using it may violate the host app's terms of service; account risk and data loss are the
user's own responsibility.
**Do not sell or redistribute closed-source builds of this project or its derivatives.**

## Credits

- Upstream project: Deekseep LSPosed by [@lllucccian](https://github.com/lllucccian). This fork
  continues development on top of it.
- Upstream community: QQ group 1106465300 / Telegram [@Deekseepapp](https://t.me/Deekseepapp)
  (belong to the upstream project).
- Feedback: please open an issue in this repository.