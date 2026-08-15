# 贡献指南

感谢关注 Comic Plus。项目源码仅供个人非商业学习。提交改动前，请先确认改动范围清晰、不会携带本机数据，并完成与风险相称的验证。

## 开发流程

1. 使用 JDK 17 或更高版本、Android SDK 36 和 PowerShell。
2. 在 `android/` 目录运行 JVM 测试、Kotlin 编译和 Android Lint。
3. 保持改动聚焦，不提交无关格式化、下载内容或生成文件。
4. 对阅读器、下载、缓存和网络协议改动补充或更新测试。

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Set-Location .\android
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug
```

## 安全要求

- 不提交 `android/keystore.properties`、密钥、密码、Cookie、Token、个人绝对路径或 CI secrets。
- 不提交 `build/`、`output/`、APK/AAB、R8 mapping、日志、下载章节或用户数据。
- 源码中的上游协议常量是兼容数据，不是项目凭据；不要把新的私密配置硬编码到源码。
- 涉及真实漏洞时优先使用仓库的私密漏洞报告渠道，不在公开 Issue 中附带秘密或用户数据。

## 许可边界

本项目使用 [Comic Plus Personal Non-Commercial Learning License 1.0](LICENSE)，不是 OSI 开源许可证。仅允许自然人用于个人、非商业、自主学习；禁止商业、工作、组织内部使用、再分发、公开部署和应用商店发布。

提交 Pull Request 即表示你有权提交相关内容，并同意：该贡献按项目当前许可证向公众提供；同时授予项目维护者永久、全球、不可撤销、免版税的权利，以使用、修改、分发、再许可和维护该贡献。不同意该授权时，请不要提交代码。

不要提交未经授权的漫画页面、封面、字体、品牌素材或第三方源码。上游服务、内容、名称和商标不因本项目许可而获得授权。
