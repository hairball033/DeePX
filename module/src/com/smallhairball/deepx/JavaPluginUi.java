package com.smallhairball.deepx;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Border-led engineering UI for the first Java plugin ABI. */
final class JavaPluginUi {
    private JavaPluginUi() {}

    static void show(final Activity activity) {
        if(activity==null||activity.isFinishing())return;
        final boolean dark=DeePXUi.isDark(activity);
        final Palette p=new Palette(dark);
        final Dialog dialog=new Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.canvas);
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(activity,8),statusBar(activity),dp(activity,8),0);
        TextView back=label(activity,"‹",29,p.ink,false);back.setGravity(Gravity.CENTER);bar.addView(back,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,56)));
        LinearLayout titles=new LinearLayout(activity);titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label(activity,"Java 插件",18,p.ink,true));titles.addView(label(activity,"正式版 · ABI v3",11,p.muted,false));
        bar.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView hooks=toolbarAction(activity,"Hook 点",p);TextView importZip=toolbarAction(activity,"导入",p);
        bar.addView(hooks,new LinearLayout.LayoutParams(dp(activity,74),dp(activity,44)));
        bar.addView(importZip,new LinearLayout.LayoutParams(dp(activity,64),dp(activity,44)));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(activity,56)+statusBar(activity)));
        ScrollView scroll=new ScrollView(activity);final LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity,16),dp(activity,16),dp(activity,16),dp(activity,32));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        final Runnable refresh=new Runnable(){public void run(){render(activity,content,p,this);}};refresh.run();
        back.setOnClickListener(v->close(dialog,root));hooks.setOnClickListener(v->showHooks(activity,p));
        importZip.setOnClickListener(v->confirmImport(activity));
        dialog.setContentView(root);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(p.canvas));window.setLayout(-1,-1);}dialog.show();
        root.setTranslationX(activity.getResources().getDisplayMetrics().widthPixels*.12f);root.setAlpha(0f);root.animate().translationX(0).alpha(1).setDuration(180).start();
    }

    private static void render(Activity activity,LinearLayout content,Palette p,Runnable refresh){
        content.removeAllViews();
        LinearLayout summary=surface(activity,p,15);summary.setPadding(dp(activity,16),dp(activity,15),dp(activity,16),dp(activity,15));
        List<JavaPluginManager.Info> plugins=JavaPluginManager.list(activity);int enabled=0;for(JavaPluginManager.Info i:plugins)if(i.enabled)enabled++;
        TextView hero=label(activity,"插件运行环境",17,p.ink,true);summary.addView(hero);
        TextView stats=label(activity,plugins.size()+" 个插件  ·  "+enabled+" 个启用  ·  "+JavaPluginHookCatalog.all().size()+" 个 Hook 点",12,p.success,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(activity,4);summary.addView(stats,sp);
        TextView warning=label(activity,"插件与 DeepSeek 同进程运行。只导入可信 ZIP；Local API 永不向插件开放。",12,p.muted,false);
        warning.setLineSpacing(dp(activity,2),1f);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(activity,10);summary.addView(warning,wp);
        content.addView(summary);
        section(activity,content,"已安装",p);
        for(JavaPluginManager.Info info:plugins)addPlugin(activity,content,info,p,refresh);
        section(activity,content,"学习与开发",p);
        LinearLayout learning=surface(activity,p,13);
        learning.addView(actionRow(activity,"使用教程","从目录结构、D8 编译到导入和排障。",p,v->showTutorial(activity,p)));
        learning.addView(divider(activity,p));
        learning.addView(actionRow(activity,"导出给 AI","导出当前版本真实可用的接口、参数、约束和逐点调用示例。",p,v->exportAiReference(activity)));
        learning.addView(divider(activity,p));
        learning.addView(actionRow(activity,"导出教程 ZIP","包含清单、Java 源码、文件说明、构建教程和完整 Hook 表。",p,v->exportTutorial(activity)));
        learning.addView(divider(activity,p));
        learning.addView(actionRow(activity,"Hook 点目录",JavaPluginHookCatalog.all().size()+" 个可查询点；包含本机实际成员四阶段事件。",p,v->showHooks(activity,p)));
        content.addView(learning);
        DeePXUi.addBuildFooter(activity,content,p.muted);
    }

    private static void addPlugin(Activity a,LinearLayout parent,JavaPluginManager.Info info,Palette p,Runnable refresh){
        LinearLayout card=surface(a,p,13);card.setPadding(dp(a,16),dp(a,14),dp(a,12),dp(a,12));
        LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout text=new LinearLayout(a);text.setOrientation(LinearLayout.VERTICAL);
        text.addView(label(a,info.name,16,p.ink,true));text.addView(label(a,"v"+info.version+" · "+info.author,11,p.muted,false));head.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        if(!info.builtIn){Switch toggle=switchView(a,p.dark);toggle.setChecked(info.enabled);head.addView(toggle);toggle.setOnCheckedChangeListener((button,checked)->{
            if(JavaPluginManager.setEnabled(a,info.id,checked)){Toast.makeText(a,checked?"插件已启用":"插件已停用",Toast.LENGTH_SHORT).show();refresh.run();}
            else{button.setChecked(!checked);Toast.makeText(a,"插件状态保存失败",Toast.LENGTH_SHORT).show();}});}card.addView(head);
        TextView desc=label(a,info.description.length()==0?"没有说明":info.description,12,p.secondary,false);desc.setLineSpacing(dp(a,1),1f);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2);dp.topMargin=dp(a,9);card.addView(desc,dp);
        String status=info.error.length()>0?"异常 · "+info.error:info.builtIn?"内置 · 可随时运行":info.enabled?(info.loaded?"运行中":"已启用 · 等待加载"):"已停用";
        TextView state=label(a,status,11,info.error.length()>0?p.danger:info.loaded||info.builtIn?p.success:p.muted,false);LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,-2);st.topMargin=dp(a,10);card.addView(state,st);
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(a,12),0,0);
        if(info.builtIn){TextView test=smallAction(a,"一键测试",p,true);actions.addView(test,new LinearLayout.LayoutParams(0,dp(a,42),1));test.setOnClickListener(v->{test.setEnabled(false);test.setText("测试中…");JavaPluginManager.runBuiltInTest(a,(ok,message)->{test.setEnabled(true);test.setText("一键测试");Toast.makeText(a,message,Toast.LENGTH_LONG).show();});});}
        else{TextView detail=smallAction(a,"查看清单",p,false);TextView remove=smallAction(a,"删除",p,false);remove.setTextColor(p.danger);actions.addView(detail,new LinearLayout.LayoutParams(0,dp(a,42),1));actions.addView(remove,new LinearLayout.LayoutParams(0,dp(a,42),1));detail.setOnClickListener(v->showManifest(a,info,p));remove.setOnClickListener(v->new AlertDialog.Builder(a).setTitle("删除 "+info.name+"？").setMessage("将删除插件代码、优化缓存和清单。此操作无法撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{if(JavaPluginManager.remove(a,info.id)){Toast.makeText(a,"插件已删除",Toast.LENGTH_SHORT).show();refresh.run();}}).show());}
        card.addView(actions);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(a,10);parent.addView(card,cp);
    }

    static void handleImportResult(Activity activity,int result,Intent data){if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        try{activity.getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Throwable ignored){}
        JavaPluginManager.importZip(activity,uri,(ok,message)->{Toast.makeText(activity,message,Toast.LENGTH_LONG).show();if(ok)show(activity);});}

    static void handleTutorialExportResult(Activity activity,int result,Intent data){if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        try{File source=JavaPluginManager.tutorialArchive(activity);try(FileInputStream in=new FileInputStream(source);OutputStream out=activity.getContentResolver().openOutputStream(data.getData(),"w")){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);out.flush();}Toast.makeText(activity,"教程 ZIP 已导出",Toast.LENGTH_SHORT).show();}catch(Throwable error){Toast.makeText(activity,"导出失败："+error.getMessage(),Toast.LENGTH_LONG).show();}}

    private static void confirmImport(Activity a){new AlertDialog.Builder(a).setTitle("导入 Java 插件").setMessage("插件代码将在 DeepSeek 进程内运行，并拥有与应用相同的进程权限；清单权限用于约束正式 API，不是 Android 进程沙箱。只安装你信任且能审查源码的插件。\n\nABI v3 格式要求 ZIP 根目录包含 classes.dex，并在 META-INF/deekseep/plugin.json 声明 permissions。").setNegativeButton("取消",null).setPositiveButton("选择 ZIP",(d,w)->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);a.startActivityForResult(i,JavaPluginManager.IMPORT_REQUEST);}).show();}
    static void handleAiReferenceExportResult(Activity activity,int result,Intent data){if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=activity.getContentResolver().openOutputStream(data.getData(),"w")){if(out==null)throw new IllegalStateException("无法打开目标文件");out.write(JavaPluginManager.aiReferenceMarkdown().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();Toast.makeText(activity,"AI 插件参考文档已导出",Toast.LENGTH_SHORT).show();}catch(Throwable error){Toast.makeText(activity,"导出失败："+error.getMessage(),Toast.LENGTH_LONG).show();}}

    private static void exportTutorial(Activity a){try{JavaPluginManager.tutorialArchive(a);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"deepx-java-plugin-tutorial-v3.zip");a.startActivityForResult(i,JavaPluginManager.IMPORT_REQUEST+1);}catch(Throwable error){Toast.makeText(a,"生成教程失败："+error.getMessage(),Toast.LENGTH_LONG).show();}}
    private static void exportAiReference(Activity a){try{JavaPluginManager.aiReferenceDocument(a);Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/markdown").putExtra(Intent.EXTRA_TITLE,"DeePX-Java-Plugin-AI-Reference-v3.md");a.startActivityForResult(i,JavaPluginManager.IMPORT_REQUEST+2);}catch(Throwable error){Toast.makeText(a,"生成参考文档失败："+error.getMessage(),Toast.LENGTH_LONG).show();}}
    private static void showTutorial(Activity a,Palette p){showTextPage(a,"Java 插件使用教程",JavaPluginManager.tutorialText(),p);}
    private static void showHooks(Activity a,Palette p){
        final Dialog d=new Dialog(a,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.canvas);
        LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(a,8),statusBar(a),dp(a,16),0);
        TextView back=label(a,"‹",29,p.ink,false);back.setGravity(Gravity.CENTER);bar.addView(back,new LinearLayout.LayoutParams(dp(a,48),dp(a,56)));
        LinearLayout titles=new LinearLayout(a);titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label(a,"Hook 点目录",18,p.ink,true));
        int wired=0;for(JavaPluginHookCatalog.Point point:JavaPluginHookCatalog.all())if(point.wired)wired++;
        titles.addView(label(a,JavaPluginHookCatalog.all().size()+" 个节点 · "+wired+" 个已接线",11,p.muted,false));
        bar.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bar,new LinearLayout.LayoutParams(-1,dp(a,56)+statusBar(a)));

        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(a);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,32));
        LinearLayout note=surface(a,p,13);note.setPadding(dp(a,15),dp(a,13),dp(a,15),dp(a,13));
        note.addView(label(a,"ABI 路由图",16,p.ink,true));
        TextView copy=label(a,"目录只显示当前版本真正可订阅的稳定接口与本机实际成员事件。源码共扫描 "+JavaHookInventory.SOURCE_HOOK_COUNT+" 个注册位置，重复成员已合并；受保护的 Local API 点不会进入插件目录。点按任一分支查看完整用法。",12,p.secondary,false);
        copy.setLineSpacing(dp(a,2),1f);LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(-1,-2);copyParams.topMargin=dp(a,5);note.addView(copy,copyParams);
        content.addView(note);
        HookRailView graph=new HookRailView(a,p,JavaPluginHookCatalog.all());
        LinearLayout.LayoutParams graphParams=new LinearLayout.LayoutParams(-1,graph.requiredHeight());graphParams.topMargin=dp(a,12);content.addView(graph,graphParams);
        scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        back.setOnClickListener(v->close(d,root));d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(p.canvas));w.setLayout(-1,-1);}d.show();
    }
    private static void showManifest(Activity a,JavaPluginManager.Info i,Palette p){StringBuilder text=new StringBuilder().append("ID\n").append(i.id).append("\n\n入口类\n").append(i.entryClass).append("\n\nABI 范围\n").append(i.minAbi).append(" — ").append(i.maxAbi<=0?"不限":i.maxAbi).append("\n\n授权能力\n");if(i.permissions.isEmpty())text.append("• 无\n");for(String permission:i.permissions)text.append("• ").append(permission).append('\n');text.append("\n插件依赖\n");if(i.dependencies.isEmpty())text.append("• 无\n");for(JavaPluginManager.Dependency dependency:i.dependencies)text.append("• ").append(dependency.id).append(dependency.minVersion.length()==0?"":" >= "+dependency.minVersion).append(dependency.optional?"（可选）":"").append('\n');text.append("\n声明事件\n");for(String hook:i.hooks)text.append("• ").append(hook).append('\n');showTextPage(a,i.name,text.toString(),p);}
    private static void showHookDetail(Activity a,JavaPluginHookCatalog.Point point,Palette p){
        String text="Hook ID\n"+point.id+"\n\n分类\n"+point.category
                +"\n\n用途\n"+point.description+"\n\n参数\n"+point.parameters
                +"\n\n状态\n"+(point.wired?"已接线，可实际收到事件":"已登记，待运行时接线")
                +"\n\n使用说明\n"+point.usage;
        showTextPage(a,point.title,text,p);
    }
    private static void showTextPage(Activity a,String title,String value,Palette p){Dialog d=new Dialog(a,android.R.style.Theme_Black_NoTitleBar_Fullscreen);LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.canvas);LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(a,8),statusBar(a),dp(a,16),0);TextView back=label(a,"‹",29,p.ink,false);back.setGravity(Gravity.CENTER);bar.addView(back,new LinearLayout.LayoutParams(dp(a,48),dp(a,56)));bar.addView(label(a,title,18,p.ink,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(bar,new LinearLayout.LayoutParams(-1,dp(a,56)+statusBar(a)));ScrollView scroll=new ScrollView(a);TextView body=label(a,value,13,p.secondary,false);body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);body.setLineSpacing(dp(a,3),1f);body.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,32));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));back.setOnClickListener(v->close(d,root));d.setContentView(root);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(p.canvas));w.setLayout(-1,-1);}d.show();}
    private static View actionRow(Activity a,String title,String detail,Palette p,View.OnClickListener listener){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,16),dp(a,13),dp(a,12),dp(a,13));LinearLayout labels=new LinearLayout(a);labels.setOrientation(LinearLayout.VERTICAL);labels.addView(label(a,title,15,p.ink,true));TextView desc=label(a,detail,12,p.muted,false);desc.setLineSpacing(dp(a,1),1f);labels.addView(desc);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=label(a,"›",24,p.muted,false);arrow.setGravity(Gravity.CENTER);row.addView(arrow,new LinearLayout.LayoutParams(dp(a,28),dp(a,40)));row.setOnClickListener(listener);row.setClickable(true);return row;}
    private static TextView toolbarAction(Activity a,String text,Palette p){TextView v=label(a,text,13,p.ink,true);v.setGravity(Gravity.CENTER);v.setClickable(true);return v;}
    private static TextView smallAction(Activity a,String text,Palette p,boolean primary){TextView v=label(a,text,13,primary?Color.WHITE:p.ink,true);v.setGravity(Gravity.CENTER);v.setBackground(round(primary?DeePXUi.BRAND:p.inset,p.line,9,dp(a,1)));return v;}
    private static void section(Activity a,LinearLayout parent,String value,Palette p){TextView t=label(a,value,12,p.muted,true);t.setLetterSpacing(.04f);t.setPadding(dp(a,4),dp(a,22),dp(a,4),dp(a,9));parent.addView(t);}
    private static LinearLayout surface(Activity a,Palette p,float radius){LinearLayout v=new LinearLayout(a);v.setOrientation(LinearLayout.VERTICAL);v.setBackground(round(p.surface,p.line,radius,dp(a,1)));return v;}
    private static View divider(Activity a,Palette p){View v=new View(a);v.setBackgroundColor(p.line);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,1));lp.leftMargin=dp(a,16);v.setLayoutParams(lp);return v;}
    private static Switch switchView(Activity a,boolean dark){Switch v=new HubInsetSwitch(a);int[][] s={new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};v.setThumbTintList(new android.content.res.ColorStateList(s,new int[]{DeePXUi.BRAND,dark?0xFFCCCCCC:Color.WHITE}));v.setTrackTintList(new android.content.res.ColorStateList(s,new int[]{0xFFADBFFF,dark?0xFF555555:0xFFBFBFBF}));v.setBackground(null);return v;}
    private static TextView label(Activity a,String value,float size,int color,boolean bold){TextView v=new TextView(a);v.setText(value);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private static GradientDrawable round(int fill,int line,float radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radius);d.setStroke(stroke,line);return d;}
    private static int statusBar(Activity a){int id=a.getResources().getIdentifier("status_bar_height","dimen","android");return id>0?a.getResources().getDimensionPixelSize(id):0;}
    private static int dp(Activity a,float v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static void close(Dialog d,View root){root.animate().translationX(root.getWidth()).setDuration(150).withEndAction(d::dismiss).start();}
    private static final class Palette{final boolean dark;final int canvas,surface,inset,ink,secondary,muted,line,success,danger;Palette(boolean d){dark=d;canvas=d?0xFF191A1C:0xFFF6F7F8;surface=d?0xFF252629:Color.WHITE;inset=d?0xFF202124:0xFFF2F3F5;ink=d?0xFFF2F3F4:0xFF1E2024;secondary=d?0xFFD0D2D5:0xFF454950;muted=d?0xFF9A9EA5:0xFF747982;line=d?0xFF393B3F:0xFFE3E5E8;success=d?0xFF83D3A3:0xFF267849;danger=d?0xFFFF8A8A:0xFFC63C3C;}}

    /** Canvas catalogue: category trunks and hook branches remain readable at 200+ points. */
    private static final class HookRailView extends View {
        private final Activity activity;
        private final Palette palette;
        private final List<JavaPluginHookCatalog.Point> points;
        private final float density;
        private final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint title=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint body=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint mono=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint category=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        private final int height;
        private float downY;

        HookRailView(Activity activity,Palette palette,List<JavaPluginHookCatalog.Point> points){
            super(activity);this.activity=activity;this.palette=palette;this.points=new ArrayList<>(points);density=activity.getResources().getDisplayMetrics().density;
            line.setStrokeWidth(px(1));line.setStrokeCap(Paint.Cap.ROUND);
            title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setTextSize(sp(14));title.setColor(palette.ink);
            body.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));body.setTextSize(sp(12));body.setColor(palette.secondary);
            mono.setTypeface(Typeface.MONOSPACE);mono.setTextSize(sp(10.5f));mono.setColor(palette.muted);
            category.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));category.setTextSize(sp(12));category.setColor(palette.ink);
            int groups=0;String previous="";for(JavaPluginHookCatalog.Point point:this.points){if(!point.category.equals(previous)){groups++;previous=point.category;}}
            height=dpv(20)+groups*dpv(42)+this.points.size()*dpv(102)+dpv(16);
            setBackground(round(palette.surface,palette.line,px(13),dpv(1)));
            setContentDescription("Java Hook ABI 路由图，共 "+this.points.size()+" 个 Hook 点");
            setClickable(true);
        }

        int requiredHeight(){return height;}

        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float railX=px(24),textX=px(52),right=getWidth()-px(14);float y=px(18);String current="";
            for(int index=0;index<points.size();index++){
                JavaPluginHookCatalog.Point point=points.get(index);
                if(!point.category.equals(current)){
                    current=point.category;y+=px(10);line.setColor(palette.line);line.setStrokeWidth(px(1));float categoryWidth=category.measureText(current);canvas.drawLine(railX,y+px(8),textX-px(10),y+px(8),line);canvas.drawLine(textX+categoryWidth+px(12),y+px(8),right,y+px(8),line);
                    line.setStyle(Paint.Style.FILL);line.setColor(palette.ink);canvas.drawCircle(railX,y+px(8),px(4),line);line.setStyle(Paint.Style.STROKE);
                    canvas.drawText(current,textX,y+px(12),category);y+=px(32);
                }
                float top=y,center=top+px(45),bottom=top+px(96);
                line.setColor(palette.line);line.setStrokeWidth(px(1));canvas.drawLine(railX,top-px(8),railX,bottom,line);canvas.drawLine(railX,center,textX-px(10),center,line);
                line.setStyle(Paint.Style.FILL);line.setColor(point.wired?palette.success:palette.surface);canvas.drawCircle(railX,center,px(4.5f),line);
                if(!point.wired){line.setStyle(Paint.Style.STROKE);line.setStrokeWidth(px(1.25f));line.setColor(palette.muted);canvas.drawCircle(railX,center,px(4.5f),line);}line.setStyle(Paint.Style.STROKE);
                canvas.drawText(ellipsize(point.title,title,right-textX-px(66)),textX,top+px(18),title);
                Paint state=mono;state.setColor(point.wired?palette.success:palette.muted);state.setTextAlign(Paint.Align.RIGHT);canvas.drawText(point.wired?"已接线":"ABI",right,top+px(17),state);state.setTextAlign(Paint.Align.LEFT);state.setColor(palette.muted);
                canvas.drawText(ellipsize(point.id,mono,right-textX),textX,top+px(35),mono);
                canvas.drawText(ellipsize(point.description,body,right-textX),textX,top+px(54),body);
                canvas.drawText(ellipsize("参数 · "+point.parameters,mono,right-textX),textX,top+px(72),mono);
                canvas.drawText(ellipsize("用法 · "+point.usage,mono,right-textX),textX,top+px(90),mono);
                y+=px(102);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN){downY=event.getY();return true;}
            if(event.getAction()!=MotionEvent.ACTION_UP)return true;
            if(Math.abs(event.getY()-downY)>px(8))return true;
            float y=px(18);String current="";
            for(JavaPluginHookCatalog.Point point:points){
                if(!point.category.equals(current)){current=point.category;y+=px(42);}
                if(event.getY()>=y&&event.getY()<y+px(102)){
                    performClick();showHookDetail(activity,point,palette);return true;
                }
                y+=px(102);
            }
            return true;
        }

        @Override public boolean performClick(){super.performClick();return true;}

        private String ellipsize(String value,Paint paint,float max){String safe=value==null?"":value;if(paint.measureText(safe)<=max)return safe;String suffix="…";int count=paint.breakText(safe,true,Math.max(0,max-paint.measureText(suffix)),null);return safe.substring(0,Math.max(0,count))+suffix;}
        private float px(float value){return value*density;}
        private float sp(float value){return value*activity.getResources().getDisplayMetrics().scaledDensity;}
        private int dpv(float value){return Math.round(px(value));}
    }
}
