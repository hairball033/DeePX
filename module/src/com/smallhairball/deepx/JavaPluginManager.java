package com.smallhairball.deepx;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dalvik.system.DexClassLoader;

/** Stable Java plugin runtime v2: private ZIP install, metadata and wildcard Hook event bus. */
final class JavaPluginManager {
    static final int IMPORT_REQUEST = 0xDE51;
    static final String MANIFEST_PATH = "META-INF/deekseep/plugin.json";
    static final String DEX_PATH = "classes.dex";
    private static final int MAX_ARCHIVE = 24 * 1024 * 1024;
    private static final int MAX_EXTRACTED = 48 * 1024 * 1024;
    private static final int MAX_ENTRIES = 256;
    private static final String ROOT_NAME = "deekseep_java_plugins";
    private static final String STATE_FILE = "state.json";
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Map<String, Loaded> LOADED = new LinkedHashMap<>();
    private static final Map<String, String> LOAD_ERRORS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> PLUGIN_PERMISSIONS =
            new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> PLUGIN_FAILURES =
            new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArrayList<Subscription>> SUBSCRIPTIONS =
            new HashMap<>();
    private static final CopyOnWriteArrayList<PatternSubscription> PATTERN_SUBSCRIPTIONS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Contribution> CONTRIBUTIONS =
            new CopyOnWriteArrayList<>();
    private static final ExecutorService DISPATCH = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DeePX-JavaPlugin"); t.setDaemon(true); return t;
    });
    private static volatile java.lang.ref.WeakReference<Activity> currentActivity =
            new java.lang.ref.WeakReference<>(null);

    static final class Info {
        final String id,name,version,author,description,entryClass;
        final int versionCode,minAbi,maxAbi;
        final List<String> hooks,permissions;
        final List<Dependency> dependencies;
        final boolean builtIn, enabled, loaded;
        final String error;
        Info(String id,String name,String version,int versionCode,String author,
             String description,String entryClass,int minAbi,List<String> hooks,
             int maxAbi,List<String> permissions,List<Dependency> dependencies,
             boolean builtIn,boolean enabled,
             boolean loaded,String error){
            this.id=id;this.name=name;this.version=version;this.versionCode=versionCode;
            this.author=author;this.description=description;this.entryClass=entryClass;
            this.minAbi=minAbi;this.maxAbi=maxAbi;this.hooks=hooks;
            this.permissions=permissions;this.dependencies=dependencies;
            this.builtIn=builtIn;this.enabled=enabled;
            this.loaded=loaded;this.error=error;
        }
    }

    static final class Dependency {
        final String id,minVersion;final boolean optional;
        Dependency(String id,String minVersion,boolean optional){
            this.id=id;this.minVersion=minVersion;this.optional=optional;}
    }

    static final class Contribution {
        final String pluginId, placement, actionId, title, description;
        final JavaPluginApi.ActionCallback callback;
        Contribution(String pluginId,String placement,String actionId,String title,
                     String description,JavaPluginApi.ActionCallback callback){
            this.pluginId=pluginId;this.placement=placement;this.actionId=actionId;
            this.title=title;this.description=description;this.callback=callback;
        }
    }

    interface Callback { void done(boolean success, String message); }

    private static final class Loaded {
        final Info info; final JavaPluginApi.Plugin plugin; final DexClassLoader loader;
        final ThreadPoolExecutor executor;
        Loaded(Info info,JavaPluginApi.Plugin plugin,DexClassLoader loader){
            this.info=info;this.plugin=plugin;this.loader=loader;
            this.executor=new ThreadPoolExecutor(1,1,20L,TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(128),r->{Thread thread=new Thread(r,
                    "DeePX-Plugin-"+info.id);thread.setDaemon(true);return thread;},
                    new ThreadPoolExecutor.AbortPolicy());
        }
    }
    private static final class Subscription {
        final String pluginId; final JavaPluginApi.HookCallback callback;
        Subscription(String pluginId,JavaPluginApi.HookCallback callback){this.pluginId=pluginId;this.callback=callback;}
    }
    private static final class PatternSubscription {
        final String pluginId, pattern; final JavaPluginApi.HookCallback callback;
        PatternSubscription(String pluginId,String pattern,JavaPluginApi.HookCallback callback){
            this.pluginId=pluginId;this.pattern=pattern;this.callback=callback;}
    }
    private interface HookInvocation { void run() throws Exception; }

    private JavaPluginManager() {}

    static void initialize(Activity activity) {
        if (activity == null) return;
        currentActivity = new java.lang.ref.WeakReference<>(activity);
        if (!INITIALIZED.compareAndSet(false,true)) return;
        DISPATCH.execute(() -> {
            try{tutorialArchive(activity);aiReferenceDocument(activity);}catch(Throwable error){
                Main.log("java plugin tutorial prepare failed "+safe(error));}
            for (Info info : list(activity)) if (!info.builtIn && info.enabled)
                loadWithDependencies(activity,info.id,new HashSet<String>());
            JavaPluginRuntimeBridge.publishRegisteredSnapshot();
            // Snapshot events use the normal dispatcher. Queue app.loaded behind them so
            // audit plugins see the complete concrete-member registry in their first report.
            emit("app.loaded", json("package",activity.getPackageName(),
                    "hostVersion",HostCompat.generationName()));
        });
    }

    static void onActivityResumed(Activity activity) {
        if(activity==null)return; currentActivity=new java.lang.ref.WeakReference<>(activity);
        emit("app.activity.resumed",json("activityName",activity.getClass().getName()));
    }
    static void onActivityPaused(Activity activity) {
        emit("app.activity.paused",json("activityName",activity==null?"":activity.getClass().getName()));
    }

    static List<Info> list(Context context) {
        ArrayList<Info> out=new ArrayList<>();
        out.add(new Info("builtin.one_click_test","一键测试插件","3.0",3,"DeePX",
                "验证 ABI v3 事件总线、能力注册、动态 Hook 阶段与故障隔离。","",2,
                java.util.Arrays.asList("plugin.test","ui.toast.show","app.activity.resumed"),
                JavaPluginApi.ABI_VERSION,JavaPluginPlatform.knownPermissions(),
                Collections.emptyList(),true,true,true,""));
        File[] dirs=root(context).listFiles();
        if(dirs!=null) for(File dir:dirs){
            if(!dir.isDirectory()||dir.getName().startsWith("."))continue;
            try{out.add(readInfo(context,dir));}
            catch(Throwable error){out.add(new Info(dir.getName(),dir.getName(),"?",0,"未知",
                    "插件清单损坏","",1,Collections.emptyList(),
                    JavaPluginApi.ABI_VERSION,Collections.emptyList(),
                    Collections.emptyList(),false,false,false,
                    error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage())));}
        }
        return out;
    }

    static void importZip(final Activity activity, final Uri uri, final Callback callback) {
        DISPATCH.execute(() -> {
            try {
                Info info=installArchive(activity,uri);
                emit("plugin.installed",json("pluginId",info.id,"version",info.version));
                post(activity,callback,true,"已安装 "+info.name+" "+info.version);
            } catch(Throwable error){post(activity,callback,false,"导入失败："+safe(error));}
        });
    }

    static boolean setEnabled(Context context,String id,boolean enabled) {
        if(id==null||id.startsWith("builtin."))return false;
        try{
            File dir=pluginDir(context,id); JSONObject state=readJson(new File(dir,STATE_FILE),new JSONObject());
            state.put("enabled",enabled); writeJson(new File(dir,STATE_FILE),state);
            if(enabled && !loadWithDependencies(context,id,new HashSet<String>())) {
                state.put("enabled",false); writeJson(new File(dir,STATE_FILE),state);
                return false;
            }
            if(!enabled) unload(id);
            emit(enabled?"plugin.enabled":"plugin.disabled",json("pluginId",id));
            return true;
        }catch(Throwable error){Main.log("java plugin toggle failed id="+id+" "+safe(error));return false;}
    }

    static boolean remove(Context context,String id) {
        if(id==null||id.startsWith("builtin."))return false;
        unload(id); try{return deleteTree(pluginDir(context,id),root(context));}
        catch(Throwable error){Main.log("java plugin remove failed "+safe(error));return false;}
    }

    static void runBuiltInTest(Activity activity,Callback callback) {
        DISPATCH.execute(() -> {
            try{
                final int[] hits={0},runtimeHits={0};
                final String builtin="builtin.one_click_test";
                JavaPluginApi.HookCallback listener=event->{hits[0]++;Main.log("java plugin test event="+event.string("message",""));};
                subscribe(builtin,"plugin.test",listener);
                subscribePattern(builtin,"host.member.*",event->runtimeHits[0]++);
                for(int i=1;i<=3;i++)emitNow("plugin.test",json("message","测试事件 "+i,"sequence",i));
                JavaPluginApi.Result info=JavaPluginPlatform.call(activity,builtin,
                        "runtime.info",new JSONObject());
                JavaPluginApi.Result saved=JavaPluginPlatform.call(activity,builtin,
                        "storage.put",json("key","selftest","value","ok"));
                JavaPluginApi.Result loaded=JavaPluginPlatform.call(activity,builtin,
                        "storage.get",json("key","selftest"));
                JavaPluginPlatform.call(activity,builtin,"storage.delete",json("key","selftest"));
                JavaPluginApi.Registration form=JavaPluginPlatform.register(builtin,
                        JavaPluginPlatform.EXT_FORM_ACTION,"selftest",json("name","自检"),
                        request->JavaPluginApi.Result.ok(json("echo",
                                request.optString("value",""))));
                JavaPluginApi.Result formResult=JavaPluginPlatform.call(activity,builtin,
                        "runtime.invoke_own_extension",json("point",
                                JavaPluginPlatform.EXT_FORM_ACTION,"id","selftest","input",
                                json("value","form-ok")));
                form.unregister();
                boolean capabilityOk=info.success&&saved.success&&loaded.success
                        &&"ok".equals(loaded.data.optString("value"))
                        &&formResult.success&&"form-ok".equals(
                                formResult.data.optString("echo"));
                boolean aiOk=false;
                String aiDetail="";
                try{
                    z2.CompletionResult ai=Main.completePluginAi(builtin,"deepseek-chat",
                            "You are a plugin runtime health check.",
                            "Reply with one short word confirming the engine is alive.",128);
                    aiOk=ai!=null&&ai.text!=null&&ai.text.trim().length()>0;
                    aiDetail=aiOk?"返回 "+ai.text.trim().length()+" 字":"空响应";
                }catch(Throwable error){aiDetail=safe(error);}
                final boolean stableCapabilities=capabilityOk;
                final boolean nativeAi=aiOk;
                final String nativeAiDetail=aiDetail;
                JavaPluginRuntimeBridge.emitSelfTestPhases();
                // Runtime phase delivery uses the normal dispatcher. Queue the verdict after it.
                DISPATCH.execute(()->{
                    unsubscribePlugin(builtin);
                    boolean ok=hits[0]==3&&runtimeHits[0]>=4
                            &&stableCapabilities&&nativeAi;
                    post(activity,callback,ok,ok
                            ?"ABI v3 端到端测试通过：事件 3/3，运行时阶段 "
                                    +runtimeHits[0]+"/4，存储/表单通过，宿主 AI "
                                    +nativeAiDetail
                            :"测试失败：事件 "+hits[0]+"/3，运行时阶段 "
                                    +runtimeHits[0]+"/4，存储/表单="
                                    +(stableCapabilities?"通过":"失败")+"，宿主 AI="
                                    +nativeAiDetail);
                });
            }catch(Throwable error){post(activity,callback,false,"测试失败："+safe(error));}
        });
    }

    static List<Contribution> contributions() {
        return new ArrayList<>(CONTRIBUTIONS);
    }

    static void invokeContribution(Activity activity,Contribution contribution) {
        if(activity==null||contribution==null||contribution.callback==null)return;
        try{contribution.callback.onClick(activity);}
        catch(Throwable error){Main.log("java plugin action failed id="
                +contribution.pluginId+" action="+contribution.actionId+" "+safe(error));
            android.widget.Toast.makeText(activity,"插件操作失败："+safe(error),
                    android.widget.Toast.LENGTH_LONG).show();}
    }

    static synchronized File tutorialArchive(Context context) throws Exception {
        File file=new File(root(context),"deepx-java-plugin-tutorial-v3.zip");
        File temp=new File(root(context),".tutorial-v3.tmp");
        ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(temp,false));
        put(zip,MANIFEST_PATH,tutorialManifest().toString(2));
        put(zip,"src/com/example/deekseep/TutorialPlugin.java",tutorialSource());
        put(zip,"sdk-src/com/smallhairball/deepx/JavaPluginApi.java",tutorialApiSource());
        put(zip,"README.md",withCurrentHookCount(tutorialReadme()));
        put(zip,"DIRECTORY.md",withCurrentHookCount(tutorialDirectory()));
        put(zip,"BUILD.md",tutorialBuild());
        put(zip,"HOOK_POINTS.md",hookMarkdown());
        put(zip,"AI_PLUGIN_REFERENCE.md",aiReferenceMarkdown());
        put(zip,"CAPABILITIES.md",tutorialCapabilities());
        put(zip,"examples/LoginDialogExample.java.txt",tutorialLoginExample());
        put(zip,"examples/PlacementExamples.java.txt",tutorialPlacementExample());
        put(zip,"SECURITY.md",tutorialSecurity());
        put(zip,"files/classes.dex.txt","编译后把 Android DEX 放到压缩包根目录并命名为 classes.dex。\n");
        put(zip,"files/META-INF-deekseep-plugin.json.txt","正式清单必须位于 META-INF/deekseep/plugin.json。\n");
        zip.close();
        if(file.exists()&&!file.delete())throw new IllegalStateException("无法更新教程 ZIP");
        if(!temp.renameTo(file))throw new IllegalStateException("无法提交教程 ZIP");
        return file;
    }

    static String tutorialText(){return withCurrentHookCount(tutorialReadme())
            +"\n\n"+tutorialBuild();}
    static String hookMarkdown(){return aiReferenceMarkdown();}

    /** Standalone, AI-oriented reference generated from the live wired catalog. */
    static String aiReferenceMarkdown(){
        StringBuilder out=new StringBuilder();
        out.append("# DeePX Java 插件点位参考（ABI v3）\n\n")
                .append("这份文档由当前运行时目录生成。只把本文列出的点位当作可用接口；源码里的混淆类名只是高级定位信息，不是插件 API 名称。\n\n")
                .append("## 给插件生成器的硬规则\n\n")
                .append("1. 优先使用稳定 capability/extension；只有确实需要宿主时序观察才使用下列 host.member 点。\n")
                .append("2. 订阅 host.member 需要清单声明 `host.raw` 权限。事件只给类型、阶段、耗时和错误摘要，不给宿主对象、凭证或 token，也不能修改参数和返回值。\n")
                .append("3. Local API 相关源码点属于受保护边界，不在可订阅目录中。\n")
                .append("4. 每个真实成员有四个阶段：registered（安装完成）、before（调用前）、after（正常返回）、error（异常）。\n\n")
                .append("## 最小插件骨架\n\n```java\n")
                .append("context.subscribe(\"<点位 ID>\", event -> {\n")
                .append("  context.log(event.hookPoint + \" phase=\" + event.string(\"phase\", \"\"));\n")
                .append("});\n```\n\n")
                .append("## 可用点位\n\n");
        int count=0;
        for(JavaPluginHookCatalog.Point p:JavaPluginHookCatalog.all()){
            if(!p.wired)continue;
            count++;
            out.append("### ").append(count).append(". ").append(p.title).append("\n\n")
                    .append("- ID：`").append(p.id).append("`\n")
                    .append("- 分类：").append(p.category).append("\n")
                    .append("- 状态：已接线\n")
                    .append("- 触发：").append(p.trigger).append("\n")
                    .append("- 用途：").append(p.description).append("\n")
                    .append("- 可以做：").append(p.canDo).append("\n")
                    .append("- 参数：").append(p.parameters).append("\n")
                    .append("- 首选接口：").append(p.preferredApi).append("\n")
                    .append("- 约束：").append(p.constraints).append("\n")
                    .append("- 源码定位：`").append(p.source).append("`\n")
                    .append("- 使用：\n\n```java\n").append(p.usage)
                    .append("\n```\n\n");
        }
        out.append("## 目录统计\n\n").append("当前可订阅点位：").append(count)
                .append("。目录只包含真实接线点位，不再把源码盘点行伪装成“待接线”事件。\n");
        return out.toString();
    }

    static synchronized File aiReferenceDocument(Context context)throws Exception{
        File file=new File(root(context),"deepx-java-plugin-ai-reference-v3.md");
        File temp=new File(root(context),".ai-reference-v3.tmp");

        try(FileOutputStream output=new FileOutputStream(temp,false)){
            output.write(aiReferenceMarkdown().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
        if(file.exists()&&!file.delete())throw new IllegalStateException("无法更新 AI 点位文档");
        if(!temp.renameTo(file))throw new IllegalStateException("无法提交 AI 点位文档");
        return file;
    }

    static void emit(String hook,JSONObject data){DISPATCH.execute(()->emitNow(hook,data));}
    static JSONObject eventData(Object...pairs){return json(pairs);}
    static void emitRuntime(String hook,JSONObject data){
        if(!INITIALIZED.get())return;
        emit(hook,data);
    }
    static boolean hasRuntimeSubscribers(){
        synchronized(SUBSCRIPTIONS){
            for(String hook:SUBSCRIPTIONS.keySet())if(hook.startsWith(JavaPluginRuntimeBridge.PREFIX))return true;
        }
        for(PatternSubscription sub:PATTERN_SUBSCRIPTIONS)
            if(sub.pattern.startsWith("host.")||sub.pattern.startsWith("*"))return true;
        return false;
    }
    private static void emitNow(String hook,JSONObject data){
        if(JavaPluginHookCatalog.find(hook)==null)return;
        List<Subscription> list; synchronized(SUBSCRIPTIONS){list=SUBSCRIPTIONS.get(hook)==null?
                Collections.emptyList():new ArrayList<>(SUBSCRIPTIONS.get(hook));}
        JavaPluginApi.Event event=new JavaPluginApi.Event(hook,data);
        for(Subscription sub:list)dispatchCallback(sub.pluginId,hook,
                ()->sub.callback.onHook(event));
        for(PatternSubscription sub:PATTERN_SUBSCRIPTIONS){
            if(!matches(sub.pattern,hook))continue;
            dispatchCallback(sub.pluginId,hook,()->sub.callback.onHook(event));
        }
    }

    private static void dispatchCallback(String pluginId,String hook,HookInvocation callback){
        if(pluginId!=null&&pluginId.startsWith("builtin.")){
            try{callback.run();}catch(Throwable error){recordPluginFailure(pluginId,error);}return;
        }
        Loaded loaded;synchronized(LOADED){loaded=LOADED.get(pluginId);}
        if(loaded==null)return;
        try{loaded.executor.execute(()->{
            try{callback.run();}
            catch(Throwable error){recordPluginFailure(pluginId,error);
                Main.log("java plugin callback failed id="+pluginId
                        +" hook="+hook+" "+safe(error));}
        });}catch(Throwable error){recordPluginFailure(pluginId,error);
            Main.log("java plugin callback queue full id="+pluginId+" hook="+hook);}
    }

    private static <T> T invokeLifecycle(Loaded loaded,Callable<T> task,long timeoutMs)
            throws Exception{
        Future<T> future=loaded.executor.submit(task);
        try{return future.get(timeoutMs,TimeUnit.MILLISECONDS);}
        catch(java.util.concurrent.TimeoutException error){future.cancel(true);
            throw new IllegalStateException("插件生命周期超时 "+timeoutMs+"ms",error);}
    }

    private static void subscribe(String pluginId,String hook,JavaPluginApi.HookCallback callback){
        if(JavaPluginHookCatalog.find(hook)==null)throw new IllegalArgumentException("unknown hook "+hook);
        requireEventPermission(pluginId,hook);
        synchronized(SUBSCRIPTIONS){SUBSCRIPTIONS.computeIfAbsent(hook,k->new CopyOnWriteArrayList<>())
                .add(new Subscription(pluginId,callback));}
    }
    private static void subscribePattern(String pluginId,String pattern,
                                         JavaPluginApi.HookCallback callback){
        String value=pattern==null?"":pattern.trim();
        if(callback==null)throw new IllegalArgumentException("missing callback");
        if(value.length()<3||value.length()>180||!value.matches("[A-Za-z0-9_.$*:-]+"))
            throw new IllegalArgumentException("invalid hook pattern "+value);
        requireEventPermission(pluginId,value);
        PATTERN_SUBSCRIPTIONS.add(new PatternSubscription(pluginId,value,callback));
    }
    private static boolean matches(String pattern,String value){
        if(pattern==null||value==null)return false;
        int star=pattern.indexOf('*');
        if(star<0)return pattern.equals(value);
        String prefix=pattern.substring(0,star),suffix=pattern.substring(star+1);
        return value.startsWith(prefix)&&value.endsWith(suffix)
                &&value.length()>=prefix.length()+suffix.length();
    }
    private static void unsubscribePlugin(String id){synchronized(SUBSCRIPTIONS){
        for(CopyOnWriteArrayList<Subscription> list:SUBSCRIPTIONS.values())
            list.removeIf(value->value.pluginId.equals(id));}
        PATTERN_SUBSCRIPTIONS.removeIf(value->value.pluginId.equals(id));
        CONTRIBUTIONS.removeIf(value->value.pluginId.equals(id));
        JavaPluginPlatform.removePlugin(id);}

    private static void registerAction(String pluginId,String placement,String actionId,
                                       String title,String description,
                                       JavaPluginApi.ActionCallback callback){
        requirePermission(pluginId,JavaPluginPlatform.PERMISSION_UI);
        String place=placement==null?"":placement.trim();
        if(!place.matches("module\\.(engineering|chat|account|appearance)"))
            throw new IllegalArgumentException("unsupported placement "+place);
        String id=cleanId(actionId);if(id.length()==0)
            throw new IllegalArgumentException("invalid action id");
        String safeTitle=bounded(title,48);if(safeTitle.length()==0)
            throw new IllegalArgumentException("empty action title");
        if(callback==null)throw new IllegalArgumentException("missing callback");
        int count=0;for(Contribution value:CONTRIBUTIONS)
            if(value.pluginId.equals(pluginId)&&++count>=16)
                throw new IllegalStateException("too many actions");
        CONTRIBUTIONS.removeIf(value->value.pluginId.equals(pluginId)
                &&value.actionId.equals(id));
        CONTRIBUTIONS.add(new Contribution(pluginId,place,id,safeTitle,
                bounded(description,120),callback));
    }

    private static boolean loadWithDependencies(Context context,String id,Set<String> visiting){
        synchronized(LOADED){if(LOADED.containsKey(id))return true;}
        if(!visiting.add(id)){LOAD_ERRORS.put(id,"检测到循环插件依赖");return false;}
        try{
            Info info=readInfo(context,pluginDir(context,id));
            for(Dependency dependency:info.dependencies){
                File dependencyDir=pluginDir(context,dependency.id);
                if(!dependencyDir.isDirectory()){
                    if(dependency.optional)continue;
                    throw new IllegalStateException("缺少依赖 "+dependency.id);
                }
                Info dependencyInfo=readInfo(context,dependencyDir);
                if(dependency.minVersion.length()>0
                        &&compareVersions(dependencyInfo.version,dependency.minVersion)<0)
                    throw new IllegalStateException("依赖 "+dependency.id+" 需要版本 >= "
                            +dependency.minVersion);
                if(!dependencyInfo.enabled){
                    if(dependency.optional)continue;
                    throw new IllegalStateException("依赖未启用 "+dependency.id);
                }
                if(!loadWithDependencies(context,dependency.id,visiting)&&!dependency.optional)
                    throw new IllegalStateException("依赖启动失败 "+dependency.id);
            }
            return load(context,id);
        }catch(Throwable error){String message=safe(error);LOAD_ERRORS.put(id,message);
            Main.log("java plugin dependency resolution failed id="+id+" "+message);return false;
        }finally{visiting.remove(id);}
    }

    private static boolean load(Context context,String id){
        synchronized(LOADED){if(LOADED.containsKey(id))return true;}
        try{
            File dir=pluginDir(context,id); Info info=readInfo(context,dir);
            if(info.minAbi>JavaPluginApi.ABI_VERSION)throw new IllegalStateException("需要 ABI "+info.minAbi);
            if(info.maxAbi>0&&JavaPluginApi.ABI_VERSION>info.maxAbi)
                throw new IllegalStateException("插件最高支持 ABI "+info.maxAbi);
            verifyRuntimeDigest(dir);
            File dex=new File(dir,DEX_PATH);if(!dex.isFile())throw new IllegalStateException("缺少 classes.dex");
            // Android 14+ rejects writable dynamically loaded code. Integrity is verified first;
            // then freeze the DEX before constructing its class loader. Directory ownership still
            // permits an intentional plugin update or removal.
            if(dex.canWrite()&&!dex.setReadOnly())
                throw new SecurityException("无法把插件 DEX 设置为只读");
            File opt=new File(dir,".opt");if(!opt.isDirectory()&&!opt.mkdirs())throw new IllegalStateException("无法创建优化目录");
            DexClassLoader loader=new DexClassLoader(dex.getAbsolutePath(),opt.getAbsolutePath(),null,
                    JavaPluginApi.class.getClassLoader());
            Object instance=Class.forName(info.entryClass,true,loader).newInstance();
            if(!(instance instanceof JavaPluginApi.Plugin))throw new IllegalStateException("入口未实现 JavaPluginApi.Plugin");
            JavaPluginApi.Plugin plugin=(JavaPluginApi.Plugin)instance;
            PLUGIN_PERMISSIONS.put(id,info.permissions);
            Loaded loaded=new Loaded(info,plugin,loader);
            synchronized(LOADED){LOADED.put(id,loaded);}
            invokeLifecycle(loaded,()->{plugin.onLoad(new PluginContextImpl(context,id));return null;},5000L);
            invokeLifecycle(loaded,()->{plugin.onStart();return null;},5000L);
            LOAD_ERRORS.remove(id);
            PLUGIN_FAILURES.remove(id);
            Main.log("java plugin loaded id="+id+" version="+info.version);
            return true;
        }catch(Throwable error){Loaded failed;synchronized(LOADED){failed=LOADED.remove(id);}
            if(failed!=null)failed.executor.shutdownNow();
            String message=safe(error);LOAD_ERRORS.put(id,message);
            PLUGIN_PERMISSIONS.remove(id);JavaPluginPlatform.removePlugin(id);
            Main.log("java plugin load failed id="+id+" "+message);return false;}
    }
    private static void unload(String id){Loaded loaded; synchronized(LOADED){loaded=LOADED.remove(id);}
        unsubscribePlugin(id);if(loaded!=null)try{
            invokeLifecycle(loaded,()->{loaded.plugin.onStop();return null;},3000L);
            invokeLifecycle(loaded,()->{loaded.plugin.onUnload();return null;},3000L);}
        catch(Throwable error){Main.log("java plugin unload failed "+safe(error));}
        finally{if(loaded!=null)loaded.executor.shutdownNow();PLUGIN_PERMISSIONS.remove(id);}}

    private static final class PluginContextImpl implements JavaPluginApi.Context{
        final Context context;final String id;PluginContextImpl(Context c,String i){context=c.getApplicationContext();id=i;}
        public int abiVersion(){return JavaPluginApi.ABI_VERSION;}public String pluginId(){return id;}
        public String hostVersion(){return HostCompat.generationName();}
        public boolean hasPermission(String permission){return JavaPluginManager.hasPermission(id,permission);}
        public List<String> grantedPermissions(){return JavaPluginManager.permissionsFor(id);}
        public void subscribe(String hook,JavaPluginApi.HookCallback callback){JavaPluginManager.subscribe(id,hook,callback);}
        public void subscribePattern(String pattern,JavaPluginApi.HookCallback callback){JavaPluginManager.subscribePattern(id,pattern,callback);}
        public List<String> runtimeHookPoints(){return hasPermission(JavaPluginPlatform.PERMISSION_HOST_RAW)
                ?JavaPluginHookCatalog.runtimeIds():Collections.emptyList();}
        public void registerAction(String placement,String actionId,String title,
                                   String description,JavaPluginApi.ActionCallback callback){
            JavaPluginManager.registerAction(id,placement,actionId,title,description,callback);}
        public JavaPluginApi.Result call(String capability,JSONObject request){
            return JavaPluginPlatform.call(context,id,capability,request);}
        public void callAsync(String capability,JSONObject request,JavaPluginApi.ResultCallback callback){
            JavaPluginPlatform.callAsync(context,id,capability,request,callback);}
        public JavaPluginApi.Registration registerExtension(String point,String extensionId,
                JSONObject descriptor,JavaPluginApi.ExtensionCallback callback){
            return JavaPluginPlatform.register(id,point,extensionId,descriptor,callback);}
        public void log(String message){Main.log("plugin["+id+"] "+String.valueOf(message));}
        public String readSetting(String key,String fallback){requirePermission(id,JavaPluginPlatform.PERMISSION_STORAGE);return context.getSharedPreferences("deekseep_plugin_"+id,0).getString(key,fallback);}
        public boolean writeSetting(String key,String value){requirePermission(id,JavaPluginPlatform.PERMISSION_STORAGE);return context.getSharedPreferences("deekseep_plugin_"+id,0).edit().putString(key,value).commit();}
        public Activity currentActivity(){requirePermission(id,JavaPluginPlatform.PERMISSION_UI_DIALOG);return JavaPluginManager.currentActivity.get();}
    }

    private static Info installArchive(Context context,Uri uri)throws Exception{
        try(AssetFileDescriptor descriptor=context.getContentResolver()
                .openAssetFileDescriptor(uri,"r")){
            if(descriptor!=null&&descriptor.getLength()>MAX_ARCHIVE)
                throw new IllegalArgumentException("压缩包超过 24MB");
        }
        File staging=new File(root(context),".import-"+Long.toHexString(System.nanoTime()));
        if(!staging.mkdirs())throw new IllegalStateException("无法创建导入目录");
        int entries=0,total=0;String manifest=null;
        try(InputStream raw=context.getContentResolver().openInputStream(uri);
            ZipInputStream zip=new ZipInputStream(raw)){
            if(raw==null)throw new IllegalArgumentException("无法读取文件");
            ZipEntry entry;byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null){
                if(++entries>MAX_ENTRIES)throw new IllegalArgumentException("文件数量过多");
                String name=entry.getName().replace('\\','/');
                if(name.startsWith("/")||name.contains("../"))throw new SecurityException("非法路径");
                File target=new File(staging,name).getCanonicalFile();
                if(!target.getPath().startsWith(staging.getCanonicalPath()+File.separator))throw new SecurityException("越界路径");
                if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IllegalStateException("无法创建目录");continue;}
                File parent=target.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IllegalStateException("无法创建目录");
                ByteArrayOutputStream manifestBuffer=MANIFEST_PATH.equals(name)?new ByteArrayOutputStream():null;
                try(FileOutputStream output=new FileOutputStream(target,false)){int count;while((count=zip.read(buffer))>0){
                    total+=count;if(total>MAX_EXTRACTED)throw new IllegalArgumentException("解压内容过大");
                    output.write(buffer,0,count);if(manifestBuffer!=null)manifestBuffer.write(buffer,0,count);}}
                if(manifestBuffer!=null)manifest=new String(manifestBuffer.toByteArray(),StandardCharsets.UTF_8);
            }
        }catch(Throwable error){deleteTree(staging,root(context));throw error;}
        try{
            if(manifest==null)throw new IllegalArgumentException("缺少 "+MANIFEST_PATH);
            JSONObject json=new JSONObject(manifest);String id=cleanId(json.optString("id"));
            if(id.length()==0)throw new IllegalArgumentException("插件 ID 无效");
            if(!new File(staging,DEX_PATH).isFile())throw new IllegalArgumentException("缺少 classes.dex");
            unload(id);
            File destination=pluginDir(context,id);File backup=new File(root(context),".backup-"+id);
            deleteTree(backup,root(context));if(destination.exists()&&!destination.renameTo(backup))throw new IllegalStateException("无法备份旧版本");
            if(!staging.renameTo(destination)){if(backup.exists())backup.renameTo(destination);throw new IllegalStateException("无法提交插件");}
            deleteTree(backup,root(context));writeJson(new File(destination,STATE_FILE),
                    new JSONObject().put("enabled",false)
                            .put("runtimeSha256",runtimeDigest(destination)));
            LOAD_ERRORS.remove(id);
            return readInfo(context,destination);
        }catch(Throwable error){deleteTree(staging,root(context));throw error;}
    }

    private static Info readInfo(Context context,File dir)throws Exception{
        JSONObject root=readJson(new File(dir,MANIFEST_PATH),null);if(root==null)throw new IllegalArgumentException("missing manifest");
        String id=cleanId(root.optString("id"));if(!dir.getName().equals(id))throw new IllegalArgumentException("ID 与目录不一致");
        JSONArray array=root.optJSONArray("hooks");ArrayList<String> hooks=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++){
            String hook=array.optString(i);if(isKnownHookDeclaration(hook))hooks.add(hook);}
        int minAbi=root.optInt("minAbi",1);int maxAbi=root.optInt("maxAbi",0);
        JSONArray permissionArray=root.optJSONArray("permissions");
        ArrayList<String> permissions=new ArrayList<>();
        if(permissionArray!=null)for(int i=0;i<permissionArray.length();i++){
            String permission=permissionArray.optString(i,"").trim();
            if(!JavaPluginPlatform.isKnownPermission(permission))
                throw new IllegalArgumentException("未知权限 "+permission);
            if(!permissions.contains(permission))permissions.add(permission);
        }
        if(permissionArray==null&&minAbi<3){
            permissions.add(JavaPluginPlatform.PERMISSION_LIFECYCLE);
            permissions.add(JavaPluginPlatform.PERMISSION_UI);
            permissions.add(JavaPluginPlatform.PERMISSION_UI_DIALOG);
            permissions.add(JavaPluginPlatform.PERMISSION_STORAGE);
        }
        JSONArray dependencyArray=root.optJSONArray("dependencies");
        ArrayList<Dependency> dependencies=new ArrayList<>();
        if(dependencyArray!=null)for(int i=0;i<dependencyArray.length();i++){
            Object raw=dependencyArray.opt(i);String dependencyId="";
            String minimum="";boolean optional=false;
            if(raw instanceof JSONObject){JSONObject object=(JSONObject)raw;
                dependencyId=cleanId(object.optString("id",""));
                minimum=bounded(object.optString("minVersion",""),32);
                optional=object.optBoolean("optional",false);
            }else dependencyId=cleanId(String.valueOf(raw));
            if(dependencyId.length()==0||dependencyId.equals(id))
                throw new IllegalArgumentException("无效插件依赖");
            dependencies.add(new Dependency(dependencyId,minimum,optional));
        }
        boolean enabled=readJson(new File(dir,STATE_FILE),new JSONObject()).optBoolean("enabled",false);
        boolean loaded; synchronized(LOADED){loaded=LOADED.containsKey(id);}
        return new Info(id,root.optString("name",id),root.optString("version","1.0"),root.optInt("versionCode",1),
                root.optString("author","未知"),root.optString("description",""),root.optString("entryClass",""),
                minAbi,Collections.unmodifiableList(hooks),maxAbi,
                Collections.unmodifiableList(permissions),
                Collections.unmodifiableList(dependencies),false,enabled,loaded,
                LOAD_ERRORS.getOrDefault(id,""));
    }

    static Activity currentActivitySnapshot(){return currentActivity.get();}
    static List<String> permissionsFor(String pluginId){
        List<String> value=PLUGIN_PERMISSIONS.get(pluginId);
        return value==null?Collections.emptyList():value;
    }
    static boolean hasPermission(String pluginId,String permission){
        if(pluginId!=null&&pluginId.startsWith("builtin."))return true;
        List<String> values=PLUGIN_PERMISSIONS.get(pluginId);
        return values!=null&&values.contains(permission);
    }
    static void requirePermission(String pluginId,String permission){
        if(!hasPermission(pluginId,permission))throw new SecurityException(
                "插件 "+pluginId+" 未获权限 "+permission);
    }
    static void recordPluginFailure(String pluginId,Throwable error){
        if(pluginId==null)return;
        int failures=PLUGIN_FAILURES.computeIfAbsent(pluginId,k->new AtomicInteger())
                .incrementAndGet();
        Main.log("java plugin runtime failure id="+pluginId+" count="+failures+" "+safe(error));
        if(failures==5&&!pluginId.startsWith("builtin.")){
            LOAD_ERRORS.put(pluginId,"连续运行异常，已自动停用");
            Activity activity=currentActivity.get();
            if(activity!=null)DISPATCH.execute(()->setEnabled(activity,pluginId,false));
        }
    }
    private static void requireEventPermission(String pluginId,String hook){
        if(pluginId!=null&&pluginId.startsWith("builtin."))return;
        String value=hook==null?"":hook;
        if(value.startsWith(JavaPluginRuntimeBridge.PREFIX)||value.startsWith("host.")
                ||value.startsWith("*")){
            requirePermission(pluginId,JavaPluginPlatform.PERMISSION_HOST_RAW);return;
        }
        if(value.startsWith("chat.")||value.startsWith("conversation.")
                ||value.startsWith("attachment.")||value.startsWith("image.")){
            if(hasPermission(pluginId,JavaPluginPlatform.PERMISSION_CHAT_OBSERVE)
                    ||hasPermission(pluginId,JavaPluginPlatform.PERMISSION_CHAT_MODIFY))return;
            throw new SecurityException("插件 "+pluginId+" 未获聊天观察权限");
        }
        if(value.startsWith("account.")||value.startsWith("login.")){
            if(hasPermission(pluginId,JavaPluginPlatform.PERMISSION_ACCOUNT_READ)
                    ||hasPermission(pluginId,JavaPluginPlatform.PERMISSION_ACCOUNT_MANAGE))return;
            throw new SecurityException("插件 "+pluginId+" 未获账号权限");
        }
        if(value.startsWith("agent.")||value.startsWith("mcp.")){
            if(hasPermission(pluginId,JavaPluginPlatform.PERMISSION_AGENT_OBSERVE)
                    ||hasPermission(pluginId,JavaPluginPlatform.PERMISSION_AGENT_TOOL))return;
            throw new SecurityException("插件 "+pluginId+" 未获 Agent 权限");
        }
        if(value.startsWith("app.")||value.startsWith("navigation.")){
            requirePermission(pluginId,JavaPluginPlatform.PERMISSION_LIFECYCLE);
        }
    }
    private static void verifyRuntimeDigest(File dir)throws Exception{
        File stateFile=new File(dir,STATE_FILE);
        JSONObject state=readJson(stateFile,new JSONObject());
        String actual=runtimeDigest(dir);
        String expected=state.optString("runtimeSha256","");
        if(expected.length()==0){state.put("runtimeSha256",actual);writeJson(stateFile,state);return;}
        if(!constantTimeEquals(expected,actual))throw new SecurityException("插件完整性校验失败");
    }
    private static String runtimeDigest(File dir)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        updateDigest(digest,new File(dir,MANIFEST_PATH));
        updateDigest(digest,new File(dir,DEX_PATH));
        byte[] value=digest.digest();StringBuilder out=new StringBuilder(value.length*2);
        for(byte item:value)out.append(String.format(java.util.Locale.US,"%02x",item&0xff));
        return out.toString();
    }
    private static void updateDigest(MessageDigest digest,File file)throws Exception{
        if(!file.isFile())throw new IllegalArgumentException("缺少运行文件 "+file.getName());
        try(FileInputStream input=new FileInputStream(file)){
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))>=0)if(count>0)digest.update(buffer,0,count);
        }
    }
    private static boolean constantTimeEquals(String first,String second){
        if(first==null||second==null||first.length()!=second.length())return false;
        int diff=0;for(int i=0;i<first.length();i++)diff|=first.charAt(i)^second.charAt(i);
        return diff==0;
    }
    private static int compareVersions(String first,String second){
        int[] left=versionParts(first),right=versionParts(second);
        int length=Math.max(left.length,right.length);
        for(int i=0;i<length;i++){int a=i<left.length?left[i]:0,b=i<right.length?right[i]:0;
            if(a!=b)return a<b?-1:1;}
        return 0;
    }
    private static int[] versionParts(String value){
        String[] raw=(value==null?"":value).split("[^0-9]+");
        ArrayList<Integer> out=new ArrayList<>();
        for(String item:raw)if(item.length()>0)try{out.add(Integer.parseInt(item));}
        catch(Throwable ignored){out.add(0);}
        int[] result=new int[out.size()];for(int i=0;i<result.length;i++)result[i]=out.get(i);
        return result;
    }

    private static File root(Context context){File dir=new File(context.getFilesDir(),ROOT_NAME);if(!dir.isDirectory())dir.mkdirs();return dir;}
    private static File pluginDir(Context context,String id)throws Exception{File root=root(context).getCanonicalFile();File dir=new File(root,cleanId(id)).getCanonicalFile();
        if(!dir.getPath().startsWith(root.getPath()+File.separator))throw new SecurityException("invalid plugin id");return dir;}
    private static String cleanId(String value){String v=value==null?"":value.trim();return v.matches("[A-Za-z0-9_.-]{3,80}")?v:"";}
    private static boolean isKnownHookDeclaration(String value){
        String hook=value==null?"":value.trim();
        if(JavaPluginHookCatalog.find(hook)!=null)return true;
        return hook.indexOf('*')>=0&&hook.length()<=180
                &&hook.matches("[A-Za-z0-9_.$*:-]+")
                &&(hook.startsWith("host.member.")||hook.startsWith("app.")
                ||hook.startsWith("chat.")||hook.startsWith("plugin."));
    }
    private static String bounded(String value,int max){String text=value==null?"":value.trim();return text.length()<=max?text:text.substring(0,max);}
    private static String withCurrentHookCount(String value){return value==null?"":value
            .replace("68 个",JavaPluginHookCatalog.all().size()+" 个")
            .replace("ABI v1","ABI v3").replace("· 实验性","")
            .replace("v1 支持","v3 支持").replace("minAbi | 是 | 当前填写 1",
                    "minAbi | 是 | 正式版填写 3")
            .replace("当前 v1","当前 v3").replace("ABI v1 首版","ABI v3 正式版")
            .replace("只有标为“已接线”的事件会由当前版本实际发出；“已登记”表示已进入完整目录但仍待运行时接线。",
                    "目录只导出当前版本真实接线、可以实际收到的事件。")
            .replace("受保护的 Local API 源码点只用于定位，不向插件传递数据。",
                    "受保护的 Local API 源码点不会进入插件目录，也不向插件传递数据。")
            .replace("“已登记，待接线”的 Hook 当前不会发出。","目录中每个 Hook 都已接线并可实际发出。");}
    private static JSONObject readJson(File file,JSONObject fallback)throws Exception{if(!file.isFile())return fallback;try(FileInputStream in=new FileInputStream(file)){byte[] data=new byte[(int)Math.min(file.length(),256*1024)];int n=in.read(data);return new JSONObject(new String(data,0,Math.max(0,n),StandardCharsets.UTF_8));}}
    private static void writeJson(File file,JSONObject json)throws Exception{File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory())parent.mkdirs();File temp=new File(file.getPath()+".tmp");try(FileOutputStream out=new FileOutputStream(temp,false)){out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));out.flush();}if(file.exists()&&!file.delete())throw new IllegalStateException("replace failed");if(!temp.renameTo(file))throw new IllegalStateException("commit failed");}
    private static boolean deleteTree(File target,File root)throws Exception{if(target==null||!target.exists())return true;File canonical=target.getCanonicalFile(),base=root.getCanonicalFile();if(canonical.equals(base)||!canonical.getPath().startsWith(base.getPath()+File.separator))throw new SecurityException("unsafe delete");if(target.isDirectory()){File[] children=target.listFiles();if(children!=null)for(File child:children){if(!child.getCanonicalPath().startsWith(canonical.getPath()+File.separator))throw new SecurityException("linked child");deleteTree(child,base);}}return target.delete();}
    private static JSONObject json(Object...pairs){JSONObject out=new JSONObject();try{for(int i=0;i+1<pairs.length;i+=2)out.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(Throwable ignored){}return out;}
    private static String safe(Throwable error){Throwable cause=error.getCause()==null?error:error.getCause();return cause.getClass().getSimpleName()+": "+String.valueOf(cause.getMessage());}
    private static void post(Activity a,Callback c,boolean ok,String message){if(c==null)return;a.runOnUiThread(()->c.done(ok,message));}
    private static void put(ZipOutputStream zip,String name,String text)throws Exception{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static JSONObject tutorialManifest(){JSONObject root=json("schemaVersion",3,"id","com.example.deekseep.tutorial","name","DeePX 教程插件","version","3.0","versionCode",3,"author","你的名字","description","ABI v3 能力型 Java 插件示例","entryClass","com.example.deekseep.TutorialPlugin","minAbi",3,"maxAbi",3);try{root.put("permissions",new JSONArray().put("lifecycle.observe").put("ui.contribute").put("ui.dialog").put("storage.private").put("agent.tool"));root.put("hooks",new JSONArray().put("app.activity.resumed"));root.put("dependencies",new JSONArray());}catch(Throwable ignored){}return root;}
    private static String tutorialSource(){return "package com.example.deekseep;\n\nimport com.smallhairball.deepx.JavaPluginApi;\nimport org.json.JSONObject;\n\n/** 最小可运行入口；清单 entryClass 必须与此完整类名一致。 */\npublic final class TutorialPlugin implements JavaPluginApi.Plugin {\n  private JavaPluginApi.Context context;\n\n  public void onLoad(JavaPluginApi.Context value) throws Exception {\n    context = value;\n    context.subscribe(\"app.activity.resumed\", event ->\n        context.log(\"DeepSeek 回到前台：\" + event.string(\"activityName\", \"unknown\")));\n    context.registerAction(\"module.engineering\", \"tutorial.open\",\n        \"教程插件测试\", \"验证稳定能力调用。\", activity ->\n            context.call(\"ui.toast\", new JSONObject().put(\"text\",\n                \"ABI v\" + context.abiVersion() + \" 已运行\")));\n    JSONObject tool = new JSONObject().put(\"name\", \"文本长度\")\n        .put(\"description\", \"计算输入文本的字符数\")\n        .put(\"inputSchema\", new JSONObject().put(\"type\", \"object\"));\n    context.registerExtension(\"agent.tool\", \"text_length\", tool, request ->\n        JavaPluginApi.Result.ok(new JSONObject().put(\"output\",\n            String.valueOf(request.optString(\"text\", \"\").length()))));\n  }\n\n  public void onStart() { context.log(\"教程插件已启动\"); }\n  public void onStop() { context.log(\"教程插件已停止\"); }\n  public void onUnload() { context = null; }\n}\n";}
    private static String tutorialApiSource(){return "package com.smallhairball.deepx;\n\nimport android.app.Activity;\nimport org.json.JSONObject;\nimport java.util.List;\n\n/** 仅用于编译的 ABI v3 声明。不要把它打进最终 classes.dex。 */\npublic final class JavaPluginApi {\n  public static final int ABI_VERSION = 3;\n  public interface Plugin { void onLoad(Context c) throws Exception; void onStart() throws Exception; void onStop() throws Exception; void onUnload() throws Exception; }\n  public interface HookCallback { void onHook(Event e) throws Exception; }\n  public interface ActionCallback { void onClick(Activity a) throws Exception; }\n  public interface ResultCallback { void onResult(Result r); }\n  public interface ExtensionCallback { Result onInvoke(JSONObject request) throws Exception; }\n  public interface Registration { String id(); void unregister(); }\n  public interface Context { int abiVersion(); String pluginId(); String hostVersion(); boolean hasPermission(String p); List<String> grantedPermissions(); void subscribe(String hook,HookCallback cb); void subscribePattern(String pattern,HookCallback cb); List<String> runtimeHookPoints(); void registerAction(String placement,String id,String title,String description,ActionCallback cb); Result call(String capability,JSONObject request); void callAsync(String capability,JSONObject request,ResultCallback cb); Registration registerExtension(String point,String id,JSONObject descriptor,ExtensionCallback cb); void log(String text); String readSetting(String key,String fallback); boolean writeSetting(String key,String value); Activity currentActivity(); }\n  public static final class Result { public final boolean success; public final String code,message; public final JSONObject data; public Result(boolean ok,String c,String m,JSONObject d){success=ok;code=c;message=m;data=d;} public static Result ok(JSONObject d){return new Result(true,\"ok\",\"\",d);} public static Result error(String c,String m){return new Result(false,c,m,new JSONObject());} }\n  public static final class Event { public final String hookPoint=\"\"; public final long timestamp=0; public final JSONObject data=null; public String string(String key,String fallback){return fallback;} public boolean bool(String key,boolean fallback){return fallback;} public int integer(String key,int fallback){return fallback;} public long longValue(String key,long fallback){return fallback;} }\n}\n";}
    private static String tutorialReadme(){return "# DeePX Java 插件 ABI v1\n\n本压缩包既是目录模板，也是离线教程。运行时只读取 ZIP 根目录 `classes.dex` 和 `META-INF/deekseep/plugin.json`；其余源码、示例和 Markdown 都供作者阅读。导入后代码解压到 DeepSeek 的私有目录，默认停用，必须由用户手动开启。\n\n## 十分钟上手\n\n1. 复制 `src/com/example/deekseep/TutorialPlugin.java` 并修改包名与类名。\n2. 修改清单的 id、name、version、author、entryClass 和 hooks。\n3. 按 `BUILD.md` 编译；最终 DEX 只能包含你的插件类，不能包含 ABI stub。\n4. 把 `classes.dex`、`META-INF/`、源码与说明一起压缩，清单必须位于固定路径。\n5. 打开“工程 → Java 插件 · 实验性 → 导入”，检查作者/版本/Hook 后启用。\n6. 先运行内置“一键测试插件”；再验证你注册的按钮和日志。\n\n## 生命周期\n\n- `onLoad`：保存 Context、订阅 Hook、注册按钮；只执行一次初始化。\n- `onStart`：插件已启用，可以开始后台工作。\n- `onStop`：插件被停用，立即停止任务和定时器。\n- `onUnload`：释放引用；不要继续持有 Activity。\n\n## 订阅与按钮\n\n`context.subscribe(hookId, callback)` 接收不可变事件副本。`context.registerAction(placement, id, title, description, callback)` 创建模块操作行；v1 支持 module.engineering、module.chat、module.account、module.appearance。按钮回调运行在主线程，可用 Activity 创建弹窗或全屏 Dialog。每个插件最多注册 16 个操作。\n\n`HOOK_POINTS.md` 列出全部 68 个 Hook 点，并包含每一点的用途、参数和最小使用代码。只有标为“已接线”的事件会由当前版本实际发出；“已登记”表示已进入完整目录但仍待运行时接线。受保护的 Local API 源码点只用于定位，不向插件传递数据。\n";}
    private static String tutorialDirectory(){return "# 目录与每个文件的用途\n\n```text\nplugin.zip\n├── classes.dex                         # 必需：D8 生成的插件字节码\n├── META-INF/deekseep/plugin.json       # 必需：固定路径清单\n├── src/.../TutorialPlugin.java         # 推荐：可审查源码\n├── sdk-src/.../JavaPluginApi.java      # 编译 stub，不可打入最终 DEX\n├── examples/                           # 弹窗、登录流程和位置示例\n├── HOOK_POINTS.md                      # 68 个 Hook 的参数/状态表\n├── BUILD.md                            # 编译、D8、打包、导入、排障\n├── SECURITY.md                         # 权限和禁止边界\n└── README.md                           # 从这里开始\n```\n\n## 清单字段\n\n| 字段 | 必需 | 规则 |\n|---|---|---|\n| id | 是 | 3–80 位，仅字母、数字、点、横线、下划线；安装目录名由它决定 |\n| name | 是 | 插件列表显示名称 |\n| version | 是 | 面向用户的版本文字 |\n| versionCode | 是 | 递增整数 |\n| author | 是 | 作者或团队 |\n| description | 建议 | 一句话说明能力 |\n| entryClass | 是 | 实现 JavaPluginApi.Plugin 的完整类名 |\n| minAbi | 是 | 当前填写 1 |\n| hooks | 是 | 只声明实际使用的 Hook ID |\n\n未知 Hook 会被忽略，ABI 高于宿主会拒绝启用，缺失清单或 classes.dex 会在导入阶段拒绝。\n";}
    private static String tutorialBuild(){return "# 构建与排障\n\n## A. Gradle（推荐）\n\n建立 Android Library，compileSdk 34 或更高，Java 8。把 `sdk-src` 编译为 compileOnly 依赖；插件源码正常编译。生成 classes.jar 后运行 Android SDK Build Tools 的 D8：\n\n```sh\nd8 --lib $ANDROID_SDK_ROOT/platforms/android-34/android.jar --output out plugin-classes.jar\ncp out/classes.dex package/classes.dex\n(cd package && zip -r ../my-plugin.zip classes.dex META-INF src README.md)\n```\n\n检查 `plugin-classes.jar`/DEX 不含 `com/smallhairball/deepx/JavaPluginApi`，否则会造成 ABI 类型冲突。不要启用 desugaring 之外的动态语言运行时。\n\n## B. 命令行 Java\n\n先用 android.jar 与 sdk-src 编译插件；把 ABI stub 单独放进编译 classpath，只把插件包下的 class 收集为 plugin-classes.jar，再交给 D8。Java 源码不会在手机上自动编译。\n\n## 导入检查顺序\n\n1. ZIP 小于 24MB、最多 256 项、解压后小于 48MB。\n2. 清单固定路径存在且 JSON 合法。\n3. id 合法、entryClass 与源码一致、根目录 classes.dex 存在。\n4. 导入成功后先看“查看清单”，再启用。启用失败会显示类加载/ABI/入口错误。\n5. 先点内置一键测试：应收到 3/3 事件。\n6. 插件日志统一写入 DeePX 日志，前缀为 `plugin[插件ID]`。\n\n## 常见错误\n\n- ClassNotFoundException：entryClass 写错或 DEX 未包含入口类。\n- 入口未实现 Plugin：编译时使用了错误 ABI，或把另一份 ABI 类打进 DEX。\n- 需要 ABI 2：插件 minAbi 高于当前 v1。\n- 看不到按钮：确认插件已启用、placement 是四个受支持值之一，并重新打开对应模块分类。\n- 订阅没有事件：查看 HOOK_POINTS.md；“已登记，待接线”的 Hook 当前不会发出。\n";}
    private static String tutorialLoginExample(){return "// 教学片段，不会被运行时自动加载。\n// 目标：在不退出当前账号的情况下，弹出手机号/验证码界面。\n// v1 可以可靠创建模块按钮与 Android 弹窗；真正发送验证码、提交登录和写入候选账号\n// 必须等待 login.sms.requested/login.sms.submitted/login.completed 接线，插件不得反射账户 token。\ncontext.registerAction(\"module.account\", \"login.sms\", \"验证码添加账号\",\n    \"演示登录弹窗入口\", activity -> {\n  android.widget.EditText phone = new android.widget.EditText(activity);\n  phone.setHint(\"手机号\");\n  new android.app.AlertDialog.Builder(activity)\n      .setTitle(\"添加账号\").setView(phone)\n      .setNegativeButton(\"取消\", null)\n      .setPositiveButton(\"发送验证码\", (d, w) -> {\n        // 后续 ABI 在这里调用受限 LoginService；当前版本不要自行读取或写入凭证。\n        context.log(\"请求发送验证码：\" + phone.getText().length() + \" 位号码\");\n      }).show();\n});\n";}
    private static String tutorialPlacementExample(){return "// placement 决定按钮显示在哪个模块分类；actionId 在插件内必须唯一。\ncontext.registerAction(\"module.engineering\", \"tools.main\", \"开发工具\", \"工程页入口\", a -> {});\ncontext.registerAction(\"module.chat\", \"chat.main\", \"聊天扩展\", \"聊天功能入口\", a -> {});\ncontext.registerAction(\"module.account\", \"account.main\", \"账号扩展\", \"账号与隐私入口\", a -> {});\ncontext.registerAction(\"module.appearance\", \"appearance.main\", \"外观扩展\", \"界面美化入口\", a -> {});\n\n// 原生设置、侧边栏、聊天工具栏、底部面板等已在 HOOK_POINTS.md 登记，\n// 但 ABI v1 首版尚未接线，不能把“登记”当成“已可用”。\n";}
    private static String tutorialCapabilities(){return "# ABI v3 稳定能力与扩展点\n\n普通插件通过 `Context.call` 使用稳定宿主服务，通过 `registerExtension` 提供回调；不需要定位混淆类。\n\n## 可用能力\n\n| capability | 权限 | 返回/作用 |\n|---|---|---|\n| runtime.info / runtime.extensions | 无 | ABI、宿主代际与公开扩展 |\n| storage.get / put / delete | storage.private | 插件隔离键值存储，单值 64KB |\n| ui.toast / ui.open_uri | ui.dialog | 提示与安全 HTTP(S) 跳转 |\n| ui.clipboard.set | ui.dialog | 写入用户可见剪贴板 |\n| ui.form.open | ui.contribute | 打开结构化页面；支持 text、password、number、multiline、output、choice、toggle、image_challenge |\n| ai.generate | ai.generate | 不暴露 API Key，直接调用宿主 AI；返回 text/reasoning/finishReason |\n| account.list / account.validate | account.read | 脱敏账号清单与服务端封禁/有效状态校验 |\n| account.password_candidate | account.manage | 走宿主原生密码登录并加入候选账号，不切换当前账号；必须后台调用 |\n| account.registration.start / resend / complete / cancel | account.manage | 宿主承载官方图形验证与原生注册；成功加入候选账号并恢复当前账号 |\n| agent.mcp.list | agent.observe | MCP 名称、状态、传输和工具数，不返回请求头 |\n| network.http | network.http | HTTP(S)，30 秒内、1MB 内、禁止覆盖 Host/Content-Length |\n\n## 扩展点\n\n| extensionPoint | 权限 | 契约 |\n|---|---|---|\n| ui.form.action | ui.contribute | request 含 actionId/values；结果 data.values 更新字段，data.message 更新状态 |\n| agent.tool | agent.tool | descriptor 声明 name/description/inputSchema；回调返回 data.output |\n| chat.outgoing.transform | chat.modify | request 含 conversationId/text；data.text 替换本轮文本 |\n| chat.prompt.provider | chat.modify | data.prompt 注入隐藏系统上下文 |\n\n图形验证使用 image_challenge 字段：服务端图片以 Base64 或 data URI 写回字段；用户点击后 values 返回归一化 x/y，可连同验证码再次提交。涉及 DeepSeek 注册时应改用 account.registration，由宿主独占官方验证码令牌与凭证。`registerAction` 支持 module.engineering、module.chat、module.account、module.appearance。\n\n## 权限与运行约束\n\n可声明 lifecycle.observe、ui.contribute、ui.dialog、storage.private、chat.observe、chat.modify、account.read、account.manage、agent.observe、agent.tool、network.http、ai.generate、host.raw。每个插件有独立有界队列；生命周期启动 5 秒、停止 3 秒；同步聊天转换 80ms；连续五次异常自动停用。DEX 与清单加载前校验 SHA-256。\n";}
    private static String tutorialSecurity(){return "# 安全边界\n\nJava 插件与 DeepSeek 同进程，理论上能使用 Android/Java 反射，因此只导入你能审查源码和作者身份的 ZIP。管理器使用私有目录、安全规范化路径、文件数/大小上限并拒绝 `../` 越界；插件默认停用。\n\nABI 不提供 Local API、API Key、HTTPS 证书、模型映射、随机路由或请求/响应对象。账号事件也不会传密码、验证码、token、cookie。不要动态下载二级 DEX，不要加载 native so，不要长期持有 Activity，不要在主线程执行网络或磁盘重任务。\n";}
}