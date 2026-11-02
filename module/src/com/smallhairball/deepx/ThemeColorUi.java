package com.smallhairball.deepx;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

final class ThemeColorUi {
    private static int dp(Activity a, float n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }

    static void show(final Activity a) {
        final boolean dark = (a.getResources().getConfiguration().uiMode & 48) == 32;
        final int ink = dark ? 0xFFF2F2F4 : 0xFF18181C;
        final int muted = dark ? 0xFFA8A8B0 : 0xFF6C6C74;
        final int canvas = dark ? 0xFF17171B : 0xFFF7F7F9;
        final int surface = dark ? 0xFF242429 : Color.WHITE;
        final ThemeColorConfig.Value draft = ThemeColorConfig.get();
        final Dialog dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(canvas, dp(a, 24)));
        LinearLayout header = new LinearLayout(a);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(a, 8), dp(a, 8), dp(a, 12), dp(a, 8));
        TextView back = text(a, "‹", ink, 34, Gravity.CENTER);
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(dp(a, 44), dp(a, 44)));
        TextView title = text(a, "主题色", ink, 20, Gravity.CENTER_VERTICAL);
        title.setTypeface(null, 1);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(a, 44), 1));
        root.addView(header);

        ScrollView scroll = new ScrollView(a);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 28));
        scroll.addView(body);

        LinearLayout enable = settingRow(a, surface);
        enable.addView(copy(a, "启用主题色", "应用到 DeepSeek 原生界面", ink, muted),
                new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(a);
        enabled.setChecked(draft.enabled);
        enable.addView(enabled);
        body.addView(enable);

        TextView colorLabel = text(a, "主题颜色", muted, 13, Gravity.START);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.setMargins(dp(a, 4), dp(a, 22), 0, dp(a, 10));
        body.addView(colorLabel, labelLp);

        LinearLayout palettes = new LinearLayout(a);
        palettes.setWeightSum(2);
        final RoundColorEditor first = new RoundColorEditor(a, dark, "颜色一", draft.primary,
                color -> draft.primary = color);
        final RoundColorEditor second = new RoundColorEditor(a, dark, "颜色二", draft.secondary,
                color -> draft.secondary = color);
        LinearLayout.LayoutParams firstLp = new LinearLayout.LayoutParams(0, -2, 1);
        LinearLayout.LayoutParams secondLp = new LinearLayout.LayoutParams(0, -2, 1);
        secondLp.leftMargin = dp(a, 12);
        palettes.addView(first, firstLp);
        palettes.addView(second, secondLp);
        body.addView(palettes);

        LinearLayout gradient = settingRow(a, surface);
        gradient.addView(copy(a, "双色渐变", "使用两种主题颜色", ink, muted),
                new LinearLayout.LayoutParams(0, -2, 1));
        Switch gradientSwitch = new Switch(a);
        gradientSwitch.setChecked(draft.gradient);
        gradient.addView(gradientSwitch);
        LinearLayout.LayoutParams gradientLp = new LinearLayout.LayoutParams(-1, -2);
        gradientLp.topMargin = dp(a, 16);
        body.addView(gradient, gradientLp);

        final TextView directionValue = text(a, directionName(draft.direction), ink, 15, Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout direction = settingRow(a, surface);
        direction.addView(copy(a, "渐变方向", "", ink, muted),
                new LinearLayout.LayoutParams(0, -2, 1));
        directionValue.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        directionValue.setCompoundDrawablePadding(dp(a, 4));
        direction.addView(directionValue, new LinearLayout.LayoutParams(dp(a, 104), dp(a, 44)));
        direction.setOnClickListener(v -> showDirectionMenu(a, dark, draft, directionValue));
        LinearLayout.LayoutParams directionLp = new LinearLayout.LayoutParams(-1, -2);
        directionLp.topMargin = dp(a, 10);
        body.addView(direction, directionLp);

        TextView save = text(a, "保存并应用", Color.WHITE, 16, Gravity.CENTER);
        save.setTypeface(null, 1);
        save.setBackground(round(draft.primary, dp(a, 13)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(a, 52));
        saveLp.topMargin = dp(a, 24);
        body.addView(save, saveLp);

        enabled.setOnCheckedChangeListener((b, checked) -> draft.enabled = checked);
        gradientSwitch.setOnCheckedChangeListener((b, checked) -> draft.gradient = checked);
        save.setOnClickListener(v -> {
            first.commit(); second.commit();
            if (ThemeColorConfig.save(draft)) {
                Toast.makeText(a, "主题色已保存，返回后重新进入页面生效", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else Toast.makeText(a, "主题色保存失败", Toast.LENGTH_SHORT).show();
        });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(root);
        dialog.show();
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            int screenWidth = a.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = a.getResources().getDisplayMetrics().heightPixels;
            int square = Math.min(screenWidth - dp(a, 32), screenHeight - dp(a, 96));
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogWindow.setDimAmount(0.28f);
            dialogWindow.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialogWindow.setLayout(square, square);
        }
    }

    private static void showDirectionMenu(Activity a, boolean dark,
            ThemeColorConfig.Value draft, TextView value) {
        final Dialog menu = new Dialog(a);
        menu.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        list.setBackground(round(dark ? 0xFF2A2A30 : Color.WHITE, dp(a, 16)));
        String[] labels = {"无", "左右", "上下", "左上到右下", "右上到左下"};
        String[] values = {"none", ThemeColorConfig.HORIZONTAL, ThemeColorConfig.VERTICAL,
                ThemeColorConfig.DIAGONAL_DOWN, ThemeColorConfig.DIAGONAL_UP};
        for (int i = 0; i < labels.length; i++) {
            final String selected = values[i];
            TextView item = text(a, labels[i], dark ? 0xFFF2F2F4 : 0xFF18181C, 15, Gravity.CENTER_VERTICAL);
            item.setPadding(dp(a, 16), 0, dp(a, 12), 0);
            if (("none".equals(selected) && !draft.gradient) || selected.equals(draft.direction)) {
                item.setTypeface(null, 1);
                item.setTextColor(0xFF4D6BFE);
            }
            item.setOnClickListener(v -> {
                if ("none".equals(selected)) draft.gradient = false;
                else { draft.gradient = true; draft.direction = selected; }
                value.setText(directionName(draft.gradient ? draft.direction : "none"));
                menu.dismiss();
            });
            list.addView(item, new LinearLayout.LayoutParams(dp(a, 224), dp(a, 48)));
        }
        menu.setContentView(list);
        Window w = menu.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.18f);
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        menu.show();
        if (menu.getWindow() != null) menu.getWindow().setLayout(dp(a, 240), -2);
    }

    private static final class RoundColorEditor extends LinearLayout {
        final EditText hex;
        final ColorWheel wheel;
        final Listener listener;
        int color;
        boolean internal;
        RoundColorEditor(Activity a, boolean dark, String name, int initial, Listener listener) {
            super(a); this.listener = listener; color = initial;
            setOrientation(VERTICAL); setGravity(Gravity.CENTER_HORIZONTAL);
            setPadding(dp(a, 8), dp(a, 12), dp(a, 8), dp(a, 12));
            setBackground(round(dark ? 0xFF242429 : Color.WHITE, dp(a, 16)));
            TextView label = text(a, name, dark ? 0xFFF2F2F4 : 0xFF18181C, 14, Gravity.CENTER);
            label.setTypeface(null, 1); addView(label, new LinearLayout.LayoutParams(-1, dp(a, 28)));
            hex = new EditText(a); hex.setSingleLine(); hex.setGravity(Gravity.CENTER);
            hex.setText(String.format("#%06X", initial & 0xFFFFFF));
            hex.setTextColor(dark ? 0xFFE4E4E8 : 0xFF303036); hex.setTextSize(13);
            hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(9)});
            wheel = new ColorWheel(a, initial, c -> {
                color = c; listener.changed(c);
                internal = true; hex.setText(String.format("#%06X", c & 0xFFFFFF)); hex.setSelection(hex.length()); internal = false;
            });
            LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(dp(a, 132), dp(a, 132));
            wheelLp.topMargin = dp(a, 6); addView(wheel, wheelLp);
            addView(hex, new LinearLayout.LayoutParams(-1, dp(a, 42)));
            hex.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s,int st,int c,int after){}
                public void onTextChanged(CharSequence s,int st,int before,int count){}
                public void afterTextChanged(Editable e){ if(internal)return; int p=ThemeColorConfig.parseColor(e.toString(),Integer.MIN_VALUE);if(p!=Integer.MIN_VALUE){color=p;wheel.setColor(p);listener.changed(p);} }
            });
        }
        void commit(){ color=ThemeColorConfig.parseColor(hex.getText().toString(),color);listener.changed(color); }
    }

    private static final class ColorWheel extends View {
        final Paint paint = new Paint(3); final Paint marker = new Paint(3); final Listener listener;
        Bitmap bitmap; float hue, saturation; float mx, my;
        ColorWheel(Activity a,int color,Listener listener){super(a);this.listener=listener;marker.setStyle(Paint.Style.STROKE);marker.setStrokeWidth(dp(a,3));setColor(color);}
        void setColor(int color){float[] hsv=new float[3];Color.colorToHSV(color,hsv);hue=hsv[0];saturation=hsv[1];invalidate();}
        protected void onSizeChanged(int w,int h,int ow,int oh){bitmap=null;}
        protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())/2f-2;if(bitmap==null){int size=Math.max(1,(int)(r*2));bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);for(int y=0;y<size;y++)for(int x=0;x<size;x++){float dx=x-r,dy=y-r,d=(float)Math.sqrt(dx*dx+dy*dy);if(d<=r){float angle=(float)Math.toDegrees(Math.atan2(dy,dx));if(angle<0)angle+=360;bitmap.setPixel(x,y,Color.HSVToColor(new float[]{angle,Math.min(1,d/r),1}));}}}c.drawBitmap(bitmap,getWidth()/2f-r,getHeight()/2f-r,paint);double rad=Math.toRadians(hue);mx=getWidth()/2f+(float)Math.cos(rad)*saturation*r;my=getHeight()/2f+(float)Math.sin(rad)*saturation*r;marker.setColor(Color.WHITE);c.drawCircle(mx,my,dp((Activity)getContext(),7),marker);marker.setColor(0x66000000);marker.setStrokeWidth(dp((Activity)getContext(),1));c.drawCircle(mx,my,dp((Activity)getContext(),9),marker);marker.setStrokeWidth(dp((Activity)getContext(),3));}
        public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE){
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                float cx=getWidth()/2f,cy=getHeight()/2f,dx=e.getX()-cx,dy=e.getY()-cy,r=Math.min(getWidth(),getHeight())/2f-2;
                hue=(float)Math.toDegrees(Math.atan2(dy,dx));if(hue<0)hue+=360;
                saturation=Math.min(1,(float)Math.sqrt(dx*dx+dy*dy)/r);
                listener.changed(Color.HSVToColor(new float[]{hue,saturation,1}));invalidate();return true;
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                performClick();return true;
            }
            return super.onTouchEvent(e);
        }
        public boolean performClick(){super.performClick();return true;}
    }

    private interface Listener { void changed(int color); }
    private static String directionName(String d){if(ThemeColorConfig.VERTICAL.equals(d))return "上下";if(ThemeColorConfig.DIAGONAL_DOWN.equals(d))return "左上 ↘";if(ThemeColorConfig.DIAGONAL_UP.equals(d))return "右上 ↙";if(ThemeColorConfig.HORIZONTAL.equals(d))return "左右";return "无";}
    private static LinearLayout settingRow(Activity a,int color){LinearLayout r=new LinearLayout(a);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(a,14),dp(a,12),dp(a,10),dp(a,12));r.setBackground(round(color,dp(a,16)));return r;}
    private static LinearLayout copy(Activity a,String title,String desc,int ink,int muted){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);TextView t=text(a,title,ink,16,Gravity.START);t.setTypeface(null,1);l.addView(t);if(desc.length()>0){TextView d=text(a,desc,muted,12,Gravity.START);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(a,3);l.addView(d,lp);}return l;}
    private static TextView text(Activity a,String s,int color,float size,int gravity){TextView v=new TextView(a);v.setText(s);v.setTextColor(color);v.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);v.setGravity(gravity);return v;}
    private static GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
}
