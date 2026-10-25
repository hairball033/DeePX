# DeePX（DeePX LSPosed 模块）

DeePX 是面向官方 DeepSeek Android App 的独立 LSPosed/Xposed 模块。它运行在 DeepSeek 进程中，
提供模块设置入口、界面同化与功能增强。本项目基于 [Deekseep LSPosed](https://github.com/lllucccian/Deekseep)
继续开发，当前主线适配 **DeepSeek 2.7.1**（向下兼容 2.5.3 / 2.5.2 / 2.4.1 等旧版）。本项目不是 DeepSeek 官方项目，与 DeepSeek 官方无任何关联。

[English](README.md) | [更新日志 (Changelog)](CHANGELOG.md)

---

### 协议与使用禁令（请务必阅读）

本项目以 **[GPL-3.0-only](LICENSE)** 协议开源。使用、修改或分发本项目，即表示你同意以下条款：

- **禁止收费**：不得将本项目（含源码、编译产物 APK、衍生版本）用于任何牟利行为，包括但不限于
  付费售卖、付费入群 / 会员制分发、代购、捆绑销售，或借「赞助」名义变相收费。
- **禁止闭源**：任何修改版或衍生作品在分发时**必须公开完整源码**，保留原作者署名、版权声明与来源链接，
  并继续以 **GPL-3.0** 协议发布（copyleft）。
- **禁止冒充**：不得冒充原作者或维护者，不得移除、篡改版权与来源信息。

违反上述条款即构成对 GPL 协议的侵权，作者保留追究责任的权利。

---

## 当前版本

- 模块版本：**1.1**
- 适配宿主（国内版 DeepSeek）：
  - **2.7.1**（versionName 2.7.1 / versionCode 287）← 当前主线
  - 2.5.3（versionName 2.5.3 / versionCode 275）
  - 2.5.2（versionCode 273）
  - 2.4.1（versionCode 257）
  - 2.3.6 / 2.3.4 / 2.3.0
- 编译参数：`minSdk 24`、`targetSdk 37`、`compileSdk 37`

## 环境要求

- Android 7.0 或更高版本（API 24+）。
- 官方 DeepSeek 包名 `com.deepseek.chat`。
- 能加载传统 Xposed 入口的 LSPosed/Xposed。
- Root，或你的 LSPosed/Xposed 环境所要求的权限；Agent（设备命令）功能需要 Shizuku 或 Root。

## 功能一览

- **内容与隐私**：去除安全审查（保留被替换前的完整回答）、禁用数据用于优化体验、防止封号、本地禁言（实验性）。
- **账号与登录**：解锁 Google 与邮箱密码登录（国内网络可用）、解锁微信与手机号登录（海外网络可用）、
  多账号管理、注册与密码登录桥。
- **设备与风控**：默认轻量净化（风控检测结果归零）；开启「绕过风控 SDK」进入新设备身份模式
  （伪造设备标识 + 一次性重置本地身份）；「重置设备 ID」一键转世（清身份并结束 DeepSeek 进程）。
- **Agent 与设备控制**：Shizuku 后端（先申请授权再执行）、终端命令、后台保活。
- **远程与更新**：公告与版本更新（含版本号比对，强制更新不可取消）、本地 API（HTTP/HTTPS 与 CA 校验）。
- **界面**：Miuix 风格同化（设置页、执行页、确认与更新弹窗），中英文双语。

## 安装前准备

1. 在 Android 应用信息中确认 DeepSeek 包名与 versionCode。
2. 备份重要聊天记录及本地文件。
3. 先安装并配置 LSPosed/Xposed，再启用本模块作用域（勾选 DeepSeek）。
4. 如需后台请求或通知，取消 DeepSeek 的电池优化限制（可加入 Doze 白名单）。

## 目录结构

| 目录 | 说明 |
|---|---|
| `compose-launcher/` | 伴侣 App（Kotlin + Compose + Miuix UI），模块设置与管理界面 |
| `module/` | 模块核心钩子（Java）：登录、风控、内容、Agent、本地 API 与宿主内设置页 |
| `module-legacy/` | 旧版本宿主兼容适配层 |
| `module-universal/` | 通用模块打包（Magisk/KernelSU 模板、资源与资产） |
| `scripts/` | 构建、测试与调试脚本 |
| `third_party/` | 第三方许可与运行资产（Shizuku rish 等） |

## 免责声明

本模块仅供个人学习、研究与自用，请勿用于任何违法用途。使用本模块可能违反宿主应用的服务条款，
由此带来的账号风险、数据丢失等后果由使用者自行承担。
**请勿将本项目或其衍生版本收费售卖或闭源分发。**

## 致谢

- 上游项目：Deekseep LSPosed，作者 [@lllucccian](https://github.com/lllucccian)。本项目在其基础上继续开发。
- 上游交流群：QQ 群 1106465300 / Telegram [@Deekseepapp](https://t.me/Deekseepapp)（属上游项目）。
- 问题反馈：请在本仓库 Issue 区提出。