package com.smallhairball.deepx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned semantic ABI plus a complete inventory of source Hook registration sites. */
final class JavaPluginHookCatalog {
    static final class Point {
        final String id, category, title, description, parameters, usage;
        final String trigger, canDo, preferredApi, constraints, source;
        final boolean wired;
        Point(String id, String category, String title, String description,
              String parameters, boolean wired, String usage) {
            this(id,category,title,description,parameters,wired,usage,"","","","","");
        }
        Point(String id, String category, String title, String description,
              String parameters, boolean wired, String usage, String trigger,
              String canDo, String preferredApi, String constraints, String source) {
            this.id=id; this.category=category; this.title=title;
            this.description=description; this.parameters=parameters;
            this.wired=wired; this.usage=usage; this.trigger=trigger;
            this.canDo=canDo; this.preferredApi=preferredApi;
            this.constraints=constraints; this.source=source;
        }
    }

    private static final List<Point> POINTS = build();
    private static final Map<String, Point> BY_ID = index(POINTS);
    private JavaPluginHookCatalog() {}

    static List<Point> all() {
        ArrayList<Point> out=new ArrayList<>();
        for(Point point:POINTS) if(point.wired) out.add(point);
        for(JavaPluginRuntimeBridge.Descriptor descriptor
                :JavaPluginRuntimeBridge.descriptors()){
            if(JavaHookGuide.restricted(descriptor.sourceHook))continue;

            for(String phase:JavaPluginRuntimeBridge.PHASES)
                out.add(runtimePoint(descriptor,phase));
        }
        return Collections.unmodifiableList(out);
    }
    static Point find(String id) {
        Point semantic=BY_ID.get(id);if(semantic!=null)return semantic;
        JavaPluginRuntimeBridge.Descriptor descriptor=
                JavaPluginRuntimeBridge.findPhase(id);
        if(descriptor==null)return null;
        int split=id.lastIndexOf('.');
        if(JavaHookGuide.restricted(descriptor.sourceHook))return null;
        return runtimePoint(descriptor,id.substring(split+1));
    }
    static List<String> runtimeIds(){
        ArrayList<String> out=new ArrayList<>();
        for(JavaPluginRuntimeBridge.Descriptor descriptor
                :JavaPluginRuntimeBridge.descriptors())
            if(!JavaHookGuide.restricted(descriptor.sourceHook))
            for(String phase:JavaPluginRuntimeBridge.PHASES)
                out.add(descriptor.baseId+"."+phase);
        return Collections.unmodifiableList(out);
    }

    private static List<Point> build() {
        ArrayList<Point> out = new ArrayList<>();
        // App and navigation.
        add(out,"app.loaded","生命周期","模块加载","模块完成宿主初始化。","package, hostVersion",true);
        add(out,"app.activity.resumed","生命周期","主界面恢复","DeepSeek 主 Activity 进入前台。","activityName",true);
        add(out,"app.activity.paused","生命周期","主界面暂停","DeepSeek 主 Activity 离开前台。","activityName",true);
        add(out,"app.theme.changed","生命周期","主题变化","浅色/深色或主题色发生变化。","dark, primary",false);
        add(out,"app.language.changed","生命周期","语言变化","宿主语言设置发生变化。","languageTag",false);
        add(out,"navigation.route.changed","导航","页面变化","当前 Compose 路由变化。","route",false);
        add(out,"navigation.sidebar.opened","导航","侧边栏打开","聊天侧边栏已经打开。","source",false);
        add(out,"navigation.sidebar.closed","导航","侧边栏关闭","聊天侧边栏已经关闭。","source",false);
        add(out,"conversation.opened","导航","进入对话","用户打开一个对话。","conversationId",false);
        add(out,"conversation.closed","导航","离开对话","用户离开当前对话。","conversationId",false);
        // UI contribution points.
        add(out,"ui.module.engineering.row","界面注入","工程页按钮","在模块工程页面贡献一个操作行。","id,title,description,action",true);
        add(out,"ui.module.appearance.row","界面注入","美化页按钮","在界面美化页面贡献操作行。","id,title,description,action",true);
        add(out,"ui.module.chat.row","界面注入","聊天页按钮","在聊天功能页面贡献操作行。","id,title,description,action",true);
        add(out,"ui.module.account.row","界面注入","账号页按钮","在账号与隐私页面贡献操作行。","id,title,description,action",true);
        add(out,"ui.settings.native.row","界面注入","原生设置按钮","在 DeepSeek 原生设置加入按钮。","id,title,description,action",false);
        add(out,"ui.sidebar.action","界面注入","侧边栏按钮","在侧边栏操作区加入按钮。","id,icon,title,action",false);
        add(out,"ui.chat.toolbar.action","界面注入","聊天工具栏按钮","在输入框工具栏加入按钮。","id,icon,title,action",false);
        add(out,"ui.chat.message.action","界面注入","消息操作按钮","在消息操作菜单加入按钮。","id,title,messageId,action",false);
        add(out,"ui.fullscreen.page","界面注入","全屏插件页","请求打开插件提供的全屏页面。","title,renderer",true);
        add(out,"ui.dialog.show","界面注入","通用弹窗","在当前 Activity 显示标题、输入框和按钮。","title,fields,buttons",true);
        add(out,"ui.toast.show","界面注入","短提示","显示宿主内短提示。","text,duration",true);
        add(out,"ui.bottom_sheet.show","界面注入","底部面板","打开插件底部操作面板。","title,rows",false);
        add(out,"ui.floating_panel.show","界面注入","悬浮面板","显示模块风格悬浮控制面板。","title,actions",false);
        // Chat lifecycle.
        add(out,"chat.composer.changed","聊天","输入变化","聊天输入框文本变化。","length,empty",false);
        add(out,"chat.send.before","聊天","发送之前","消息提交前的只读通知。","conversationId,textLength",false);
        add(out,"chat.send.after","聊天","发送之后","消息已提交给宿主。","conversationId",false);
        add(out,"chat.response.started","聊天","回复开始","模型开始生成回复。","conversationId,messageId",false);
        add(out,"chat.response.text.delta","聊天","正文增量","模型正文产生增量。","messageId,delta",false);
        add(out,"chat.response.thinking.delta","聊天","思考增量","思考链产生增量。","messageId,delta",false);
        add(out,"chat.response.completed","聊天","回复完成","模型回复已经完成。","messageId,finishReason",false);
        add(out,"chat.response.interrupted","聊天","回复中断","回复被宿主或网络中断。","messageId,reason",false);
        add(out,"chat.message.rendered","聊天","消息渲染","消息进入可见列表。","messageId,role",false);
        add(out,"chat.message.recalled","聊天","消息撤回","消息撤回事件被观察到。","messageId,intercepted",false);
        add(out,"chat.message.deleted","聊天","消息删除","消息删除事件被观察到。","messageId",false);
        add(out,"chat.selection.changed","聊天","多选变化","聊天记录多选数量变化。","selectedCount",false);
        add(out,"chat.regenerate.requested","聊天","重新生成","用户请求重新生成回复。","messageId",false);
        add(out,"chat.stop.requested","聊天","停止生成","用户按下停止。","messageId",false);
        // Files and images.
        add(out,"attachment.picker.opened","附件","选择器打开","附件选择器已经打开。","type",false);
        add(out,"attachment.selected","附件","附件选中","用户选中了附件。","mime,size,name",false);
        add(out,"attachment.upload.started","附件","上传开始","附件开始上传。","name,size",false);
        add(out,"attachment.upload.completed","附件","上传完成","附件上传结束。","name,success",false);
        add(out,"image.preview.opened","附件","图片预览","原生图片预览打开。","uri",false);
        add(out,"image.saved","附件","图片保存","图片保存操作完成。","success",false);
        // Accounts/login. No credential values are exposed.
        add(out,"account.list.changed","账号","账号列表变化","候选账号数量或状态变化。","count",false);
        add(out,"account.active.changed","账号","当前账号变化","当前账号槽发生变化。","slotId,provider",false);
        add(out,"account.validation.started","账号","校验开始","批量账号校验开始。","count",false);
        add(out,"account.validation.completed","账号","校验完成","账号状态校验结束。","valid,blocked,failed",false);
        add(out,"login.dialog.requested","登录","登录弹窗","请求模块样式登录弹窗。","mode,title",false);
        add(out,"login.password.submitted","登录","密码登录提交","密码登录已提交，不包含密码。","identifierType",false);
        add(out,"login.sms.requested","登录","验证码请求","短信验证码请求已提交。","phoneMasked",false);
        add(out,"login.sms.submitted","登录","验证码提交","短信验证码已提交。","phoneMasked",false);
        add(out,"login.completed","登录","登录完成","登录流程成功或失败。","success,errorCode",false);
        // Agent/tooling, excluding Local API.
        add(out,"agent.enabled.changed","Agent","Agent 开关","Agent 主开关发生变化。","enabled",true);
        add(out,"agent.tool.before","Agent","工具执行前","本地 Agent 工具即将执行。","tool,scope",false);
        add(out,"agent.tool.completed","Agent","工具执行后","本地 Agent 工具完成。","tool,success,durationMs",false);
        add(out,"agent.run.started","Agent","运行开始","Agent 运行记录创建。","runId,conversationId",false);
        add(out,"agent.run.completed","Agent","运行完成","Agent 运行结束。","runId,status",false);
        add(out,"agent.backend.changed","Agent","后端变化","应用内/Root/Shizuku 后端变化。","backend",false);
        add(out,"mcp.server.connected","Agent","MCP 连接","MCP 服务连接成功。","profileId,toolCount",false);
        add(out,"mcp.server.disconnected","Agent","MCP 断开","MCP 服务断开。","profileId,reason",false);
        add(out,"mcp.tool.before","Agent","MCP 调用前","MCP 工具即将调用。","server,tool",false);
        add(out,"mcp.tool.completed","Agent","MCP 调用后","MCP 工具调用完成。","server,tool,success",false);
        // Module services (explicitly not Local API).
        add(out,"module.config.changed","模块","配置变化","模块普通配置发生变化。","key",false);
        add(out,"module.logs.exported","模块","日志导出","日志包导出完成。","success",false);
        add(out,"module.backup.completed","模块","备份完成","模块备份结束。","success",false);
        add(out,"module.update.available","模块","发现更新","发现新的模块版本。","version",false);
        add(out,"plugin.installed","插件","插件安装","Java 插件安装完成。","pluginId,version",true);
        add(out,"plugin.enabled","插件","插件启用","Java 插件开始运行。","pluginId",true);
        add(out,"plugin.disabled","插件","插件停用","Java 插件停止运行。","pluginId",true);
        add(out,"plugin.test","插件","测试事件","一键测试插件使用的安全事件。","message,sequence",true);
        return Collections.unmodifiableList(out);
    }

    static void add(List<Point> out, String id, String category, String title,
                    String description, String parameters, boolean wired) {
        String usage;
        if (id.startsWith("ui.module.")) {
            String placement = id.substring("ui.".length(), id.length() - ".row".length());
            usage = "context.registerAction(\"" + placement
                    + "\", \"action.id\", \"标题\", \"说明\", activity -> {});";
        } else {
            usage = "context.subscribe(\"" + id
                    + "\", event -> context.log(event.data.toString()));";
        }
        add(out,id,category,title,description,parameters,wired,usage);
    }

    static void add(List<Point> out, String id, String category, String title,
                    String description, String parameters, boolean wired, String usage) {
        JavaHookGuide.Guide guide=JavaHookGuide.forMethod(id);
        out.add(new Point(id,category,title,description,parameters,wired,usage,
                guide.trigger,guide.canDo,guide.preferredApi,guide.constraints,"stable:"+id));
    }

    private static Map<String,Point> index(List<Point> points) {
        LinkedHashMap<String,Point> out=new LinkedHashMap<>();
        for(Point point:points) if(point.wired) out.put(point.id,point);
        return Collections.unmodifiableMap(out);
    }

    private static Point runtimePoint(JavaPluginRuntimeBridge.Descriptor descriptor,
                                      String phase){
        JavaHookGuide.Guide guide=JavaHookGuide.forMethod(descriptor.sourceHook);
        String phaseTitle;
        String phaseDescription;
        String parameters;
        if("registered".equals(phase)){
            phaseTitle="已安装";
            phaseDescription="真实成员已进入统一 Hook 适配器。";
            parameters="memberId, owner, name, signature, parameterTypes";
        }else if("before".equals(phase)){
            phaseTitle="调用前";
            phaseDescription="成员进入拦截链前发出，只提供稳定类型元数据。";
            parameters="memberId, receiverType, argumentCount, argumentTypes";
        }else if("after".equals(phase)){
            phaseTitle="正常返回";
            phaseDescription="成员和拦截链正常完成后发出。";
            parameters="memberId, resultType, durationUs";
        }else{
            phaseTitle="异常";
            phaseDescription="成员或拦截链抛出异常时发出。";
            parameters="memberId, errorType, errorMessage, durationUs";
        }
        String id=descriptor.baseId+"."+phase;
        String usage="context.subscribe(\""+id
                +"\", event -> context.log(event.data.toString()));";
        String source=descriptor.sourceHook.length()==0
                ? descriptor.signature
                : descriptor.sourceHook+(descriptor.sourceLine>0
                ? ":"+descriptor.sourceLine : "")+" → "+descriptor.signature;
        return new Point(id,guide.category,guide.title+" · "+phaseTitle,
                guide.purpose+" "+phaseDescription,parameters,
                !JavaHookGuide.restricted(descriptor.sourceHook),usage,
                guide.trigger,guide.canDo,guide.preferredApi,guide.constraints,source);
    }
}
