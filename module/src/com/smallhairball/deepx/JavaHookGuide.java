package com.smallhairball.deepx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Human documentation for every Main.java source Hook group exposed through host.member.*. */
final class JavaHookGuide {
    static final class Guide {
        final String sourceMethod, title, category, purpose, trigger;
        final String canDo, preferredApi, constraints;
        Guide(String method,String title,String category,String purpose,String trigger,
              String canDo,String preferredApi,String constraints){
            this.sourceMethod=method;this.title=title;this.category=category;
            this.purpose=purpose;this.trigger=trigger;this.canDo=canDo;
            this.preferredApi=preferredApi;this.constraints=constraints;
        }
    }

    private static final Map<String,Guide> GUIDES=build();
    private JavaHookGuide(){}

    static Guide forMethod(String method){
        Guide guide=GUIDES.get(method);if(guide!=null)return guide;
        return create(method,"宿主成员调用","源码 Hook · 模块运行",
                "模块安装的宿主成员拦截点。","对应宿主成员被调用时。",
                "用于调用频率、耗时和异常诊断；确认行为后再请求稳定 ABI。",
                "优先使用 runtime.info、稳定 capability 或 extension point。",
                "原始事件只公开类型元数据，不公开对象值，也不能替换参数或返回值。");
    }

    static boolean restricted(String method){
        return method!=null&&method.toLowerCase(java.util.Locale.US).contains("localapi");
    }

    private static Map<String,Guide> build(){
        LinkedHashMap<String,Guide> out=new LinkedHashMap<>();
        // method | Chinese title | category | exact purpose
        String[][] rows={
            {"handleLoadPackage","宿主启动与 Activity 生命周期","生命周期","安装进程启动、Activity 生命周期及结果回调的基础拦截。"},
            {"hookModelFileLinks","模型文件链接","附件","识别模型输出中的本地文件链接并接入查看、分享边界。"},
            {"hookNativeSettingsEntry","原生设置入口","界面","在 DeepSeek 原生设置树中定位模块入口。"},
            {"prepareNativeSettingsDecoration","设置入口装饰","界面","同步原生设置入口的文案、图标和可见状态。"},
            {"maybeInstallAdaptedSettingsEntry","设置入口兼容安装","界面","针对不同宿主代际选择可用的设置入口注入方案。"},
            {"hookAssistantAvatarPainter","助手头像绘制","界面","捕获助手头像绘制边界并应用自定义头像。"},
            {"hookChatBubbleCustomization","聊天气泡绘制","界面","定位用户与助手消息布局、背景和反馈栏绘制边界。"},
            {"hookLocalEditorImageUris","编辑器图片 URI","附件","在本地会话编辑器中恢复和传递图片 URI。"},
            {"hookSidebarMultiSelectDelete","侧边栏批量删除","会话","接入侧边栏多选删除的选择与提交边界。"},
            {"hookSidebarToggleCleanup","侧边栏状态清理","会话","侧边栏开合时清理残留多选和临时 UI 状态。"},
            {"hookWelcomeWhaleMotion","欢迎鲸鱼动画","界面","定位首页鲸鱼图形的动画和重绘调用。"},
            {"hookTextWaveMotion","文字波纹动画","界面","定位首页文字波纹的布局、动画和绘制调用。"},
            {"hookHomeGreeting","首页欢迎语","界面","读取并替换首页欢迎语候选文本。"},
            {"hookTrainingOptOutControl","训练数据开关","账号","同步不用于训练设置和宿主配置。"},
            {"hookHotUpdateDialog","热更新弹窗","配置","观察并控制宿主热更新提示弹窗。"},
            {"hookRemoteConfigHotUpdates","远程灰度配置","配置","观察服务器远程配置和灰度值落地。"},
            {"hookNativeFakeMute","本地禁言状态","账号","在宿主读取禁言状态时应用本地配置。"},
            {"hookRegionalLoginUnlock","地区登录入口","登录","恢复地区构建隐藏的 Google、微信和手机号登录入口。"},
            {"hookLoginEntryPasswordUnlock","密码登录入口","登录","恢复邮箱或手机号密码登录入口。"},
            {"hookCandidateLoginErrorMessages","候选账号错误映射","登录","把宿主登录错误资源映射成候选账号流程提示。"},
            {"hookCandidatePasswordLoginCapture","候选账号凭证捕获","登录","登录成功时保存候选账号并恢复当前账号。"},
            {"hookCandidateRegistrationCaptchaCallbacks","快捷注册安全验证","登录","接入官方图形验证、验证码请求结果和注册会话。"},
            {"hookRegionOverride","登录地区策略","登录","调整登录入口格式所需的宿主地区策略。"},
            {"installRiskSdkNeutralizer","风险 SDK 调用","安全","定位风险 SDK 初始化、采集和上报调用。"},
            {"installSmsdkForge","风险标识生成","安全","定位设备风险标识生成和读取调用。"},
            {"hookAndroidIdSpoof","Android ID 读取","安全","观察宿主风险链路中的 Android ID 读取。"},
            {"hookOaidSpoof","OAID 读取","安全","观察宿主风险链路中的 OAID 读取。"},
            {"neutralizeRiskIoClass","风险 SDK I/O","安全","定位风险 SDK 文件与网络 I/O 类调用。"},
            {"installLoginRiskLogger","登录风控结果","登录","记录登录或注册的服务端风险结果，不记录凭证。"},
            {"installBizCodeLogger","服务端业务码","诊断","捕获登录链路业务码和错误类型。"},
            {"fa","模块基础兼容组 A","模块","安装一组跨版本基础宿主兼容拦截。"},
            {"f9","图片与会话兼容组 B","附件","安装专家图片和会话发送相关兼容拦截。"},
            {"hookNativeDualChatRoot","双会话根布局","界面","定位双开聊天模式的原生根布局。"},
            {"hookAgentToolLogRoundedRect","工具日志绘制","Agent","接入 Agent 工具调用日志的 Canvas 绘制边界。"},
            {"hookAgentToolLogTouchSurface","工具日志点击","Agent","把原生触摸坐标映射到单个工具日志行。"},
            {"hookAgentToolLogComposeTouchSurface","Compose 工具日志点击","Agent","接入 Compose 层工具日志点击和详情面板。"},
            {"hookNativeImagePreviewBoundary","原生图片预览","附件","定位图片预览打开、关闭和操作边界。"},
            {"hookChatRequest","聊天请求入口","聊天","观察宿主普通聊天请求创建和发送时机。"},
            {"hookAttachmentGuidePromptRollout","附件引导灰度","配置","定位附件引导提示的远程灰度分支。"},
            {"hookHomeWelcomeRollout","首页文案灰度","配置","定位首页随机候选词和欢迎语灰度分支。"},
            {"hookHeartbeatToolResponses","Agent 工具结果","Agent","接入工具调用结果、输入输出和同轮回传。"},
            {"hookHeartbeatPatchDispatcher","Agent 补丁分发","Agent","观察 Agent 消息 JSON Patch 的分发和应用。"},
            {"hookTrackedHeartbeatStateWrites","Agent 状态写入","Agent","跟踪 Agent 运行状态和工具批次状态写入。"},
            {"hookHeartbeatFragmentRenderBoundary","Agent 片段渲染","Agent","定位思考、正文和工具片段进入 UI 的边界。"},
            {"hookNativeModelFileCards","模型文件卡片","附件","把模型生成文件渲染成原生文件卡片。"},
            {"hookHeartbeatMarkdownInputBoundary","Agent Markdown 输入","Agent","在 Agent Markdown 解析前识别工具日志载荷。"},
            {"hookHeartbeatNativeMarkdownParser","Agent Markdown 解析","Agent","定位宿主 Markdown 解析器和内联图形布局。"},
            {"hookHeartbeatToolStatusStyle","工具状态样式","Agent","应用工具日志状态文字、描边和间距。"},
            {"hookHeartbeatToolStatusBasicText","工具状态基础文本","Agent","兼容简单文本工具状态的显示。"},
            {"hookExpertUnlock","专家能力状态","附件","同步专家模式、文件与图片上传能力状态。"},
            {"installExpertUploadGate","专家上传门禁","附件","定位图片或文件上传前的能力检查。"},
            {"hookSafetyRetraction","消息撤回与拦截","聊天","观察安全撤回、拦截和上下文保留边界。"},
            {"installServerCapture","服务器流事件","聊天","诊断宿主 SSE 原始事件类型和到达时序。"},
            {"hookContentFilterApply","内容过滤补丁","聊天","定位内容过滤 JSON Patch 的应用边界。"},
            {"installMsgRebuildCapture","消息重建","聊天","观察消息片段重建为完整消息的过程。"},
            {"hookStatusWrite","消息状态写入","聊天","定位消息 status 与 quasi_status 写入。"},
            {"hookTemplateProbe","消息模板选择","聊天","观察不同消息片段选择渲染模板。"},
            {"hookFinalMessageMerge","最终消息合并","聊天","定位流结束后整条消息的合并边界。"},
            {"hookFinalMessageApply","最终消息应用","聊天","定位单条最终消息替换和状态提交。"},
            {"installImageCredentialBridge","图片凭证桥","附件","在宿主图片请求中传递短时授权上下文。"},
            {"hookLocalSessionDirectoryMerge","本地会话目录合并","会话","合并本地创建会话与服务器目录。"},
            {"hookLocalNativeSessionRefresh","本地会话刷新","会话","触发本地会话列表的原生刷新。"},
            {"hookLocalSessionRemoteReload","远端会话重载","会话","观察会话目录从服务器重新加载。"},
            {"hookNativeDetailRequest","会话详情请求","会话","定位进入对话时的详情加载请求。"},
            {"hookLocalSessionDeletedResponse","删除响应处理","会话","处理本地会话删除后的服务器响应。"},
            {"hookLocalSessionDeletedFlow","删除状态流","会话","观察会话删除状态流变化。"},
            {"hookV234LocalSessionDeletedFlow","2.3.4 删除状态流","会话","兼容 DeepSeek 2.3.4 的会话删除状态流。"},
            {"hookV234LocalSessionDeletionPresenter","2.3.4 删除呈现","会话","兼容 DeepSeek 2.3.4 的删除 UI 呈现。"},
            {"hookV236FeedbackDeletedFlow","2.3.6 反馈删除流","会话","兼容 DeepSeek 2.3.6 的反馈与删除状态流。"},
            {"hookNativeSessionNavigator","会话导航","会话","定位切换、打开和返回会话的导航调用。"},
            {"hookNativeSessionClickCallback","会话点击回调","会话","定位侧边栏会话条目的点击回调。"},
            {"hookHistoryLoadDiagnostics","历史加载诊断","会话","记录历史消息加载阶段、数量和异常类型。"},
            {"hookActiveChatSessionCapture","当前会话捕获","会话","跟踪当前可见会话标识。"},
            {"hookProactiveVisibleThreadFilter","主动消息会话筛选","Agent","限制主动消息只写入目标可见会话。"},
            {"hookHostThemeColor","宿主主题颜色","界面","定位 Compose 主题颜色的创建和读取。"},
            {"hookHostThemeDrawColors","宿主绘制颜色","界面","把主题色应用到 Canvas 与绘制调用。"},
            {"hookComposeVisibleThreadState","可见会话 Compose 状态","会话","观察 Compose 当前会话状态。"},
            {"hookS11RenderFilter","消息渲染过滤 S11","聊天","兼容一类消息列表渲染过滤器。"},
            {"hookCs1RenderFilter","消息渲染过滤 CS1","聊天","兼容另一类消息列表渲染过滤器。"},
            {"hookNativeUiHeartbeatCompletion","Agent UI 完成状态","Agent","定位 Agent 回复在原生 UI 中完成的时机。"},
            {"hookSettingsNavigation","设置导航","界面","定位原生设置页路由和返回。"},
            {"hookNavStateMethod","导航状态成员","界面","观察宿主导航状态对象的方法调用。"},
            {"hookTransport","聊天传输层","聊天","定位宿主聊天网络传输调用和时序。"},
            {"installExpertHistoryImagePreserver","历史图片保留","附件","保留多轮会话中的历史图片引用。"},
            {"hookSendPointFps234","2.3.4 图片发送点","附件","兼容 DeepSeek 2.3.4 图片发送参数。"},
            {"hookSendPointFps","2.3.6 图片发送点","附件","接入当前版本图片发送参数。"},
            {"installPowManagerCapture","PoW 管理器","聊天","捕获宿主工作量证明管理器实例和调用。"},
            {"installExpertFlowCollectHook","专家状态流","附件","观察专家模式状态流收集。"},
            {"hookLocalApiAccountRouting","本地 API 账号路由","受保护","本地 API 专用账号路由，不对 Java 插件开放。"}
        };
        for(String[] row:rows)out.put(row[0],create(row[0],row[1],category(row[2]),row[3],
                trigger(row[1]),canDo(row[2]),preferred(row[2]),constraints(row[2])));
        return Collections.unmodifiableMap(out);
    }

    private static Guide create(String method,String title,String category,String purpose,
                                String trigger,String canDo,String preferred,String constraints){
        return new Guide(method,title,category,purpose,trigger,canDo,preferred,constraints);
    }
    private static String category(String value){return "源码 Hook · "+value;}
    private static String trigger(String title){return "当宿主进入“"+title+"”对应成员时，依次产生 before；正常返回产生 after，异常产生 error。";}
    private static String canDo(String type){
        if("界面".equals(type))return "观察界面创建、绘制或点击时机，做版本诊断和轻量联动。";
        if("聊天".equals(type)||"会话".equals(type))return "观察会话和消息管线时序，定位串话、重复或状态错误。";
        if("Agent".equals(type))return "观察工具批次、状态写入和渲染时序，补充诊断日志。";
        if("附件".equals(type))return "观察选择、上传、预览和历史附件的生命周期。";
        if("登录".equals(type)||"账号".equals(type))return "观察登录或账号流程阶段；事件不包含密码、验证码或 token。";
        if("配置".equals(type))return "观察灰度和配置生效时机，记录版本差异。";
        if("受保护".equals(type))return "仅供模块内部审计，普通插件不可订阅。";
        return "观察调用次数、耗时、返回类型和异常类型，用于兼容性诊断。";
    }
    private static String preferred(String type){
        if("Agent".equals(type))return "优先使用 agent.tool、agent.mcp.list 与 agent.observe。";
        if("登录".equals(type)||"账号".equals(type))return "优先使用 account.list、account.validate、account.registration。";
        if("界面".equals(type))return "优先使用 registerAction、ui.form.open 与 ui.form.action。";
        if("聊天".equals(type)||"会话".equals(type))return "优先使用 chat.prompt.provider、chat.outgoing.transform。";
        if("附件".equals(type))return "优先使用稳定附件 capability；原始点只用于观察。";
        return "优先使用稳定 capability；没有稳定能力时才订阅 host.member.*。";
    }
    private static String constraints(String type){
        if("受保护".equals(type))return "Local API 边界永久禁止插件订阅。";
        return "需要 host.raw 权限；只提供类型、阶段和耗时，不提供宿主对象值，也不能改写参数或返回值。";
    }
}
