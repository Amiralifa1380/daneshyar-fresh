package ir.daneshyar.offline;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import android.media.RingtoneManager;
import android.provider.Settings;
import com.google.android.material.color.MaterialColors;
import androidx.core.graphics.ColorUtils;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class MainActivity extends AppCompatActivity {
    static final int NAVY=Color.rgb(18,40,63);
    private int TEAL,INK,MUTED,BG,SURFACE;
    private long materialsCourse=0, pendingCourse=0;
    static final String[] DAYS= {
        "شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه"
    };
    static final String[] KINDS= {
        "امتحان","تکلیف","پروژه","رویداد دانشگاهی"
    };
    static final String[] MONTHS= {
        "فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"
    };
    private ProgressDialog busyDialog;
    private Store db;
    private LinearLayout root,body,nav;
    private int page=0,weekDay,eventFilter=0,kindFilter=0;
    private long selectedDate=System.currentTimeMillis();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        AppPrefs.applyTheme(this);
        super.onCreate(state);
        updatePalette();
        db=new Store(this);
        weekDay=day(System.currentTimeMillis());
        if(state!=null) {
            page=state.getInt("page");
            selectedDate=state.getLong("date");
            weekDay=state.getInt("day");
            materialsCourse=state.getLong("materialsCourse");
            pendingCourse=state.getLong("pendingCourse");
            eventFilter=state.getInt("eventFilter");
            kindFilter=state.getInt("kindFilter");
        }
        Reminders.channel(this);
        Reminders.schedule(this);
        if(getIntent().getBooleanExtra("show_alarm_controls",false))page=5;
        String pending=getIntent().getStringExtra("pending_alarm_title");
        if(pending!=null){AlarmService.start(this,pending);page=5;getIntent().removeExtra("pending_alarm_title");}
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page",page);
        out.putLong("date",selectedDate);
        out.putInt("day",weekDay);
        out.putLong("materialsCourse",materialsCourse);
        out.putLong("pendingCourse",pendingCourse);
        out.putInt("eventFilter",eventFilter);
        out.putInt("kindFilter",kindFilter);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if(r==7) {
            toast(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED?"اعلان‌ها فعال شد":"اجازه اعلان داده نشد؛ از تنظیمات گوشی فعال کنید");
            render();
        }
    }
    @Override protected void onDestroy() {
        if(busyDialog!=null)busyDialog.dismiss();
        db.close();
        io.shutdown();
        super.onDestroy();
    }
    int dp(float n) {
        return Math.round(n*getResources().getDisplayMetrics().density);
    }
    LinearLayout column() {
        LinearLayout l=new LinearLayout(this);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    LinearLayout row() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    TextView text(String s,int size,int color) {
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0,dp(4),0,dp(4));
        return v;
    }
    TextView title(String s) {
        TextView t=text(s,21,INK);
        t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    GradientDrawable surface(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    Button button(String label, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(TEAL);
        button.setAllCaps(false);
        button.setCornerRadius(dp(14));
        button.setBackgroundTintList(ColorStateList.valueOf(ColorUtils.blendARGB(TEAL,BG,0.88f)));
        button.setStrokeWidth(0);
        button.setElevation(0);
        button.setMinHeight(dp(48));
        button.setPadding(dp(16), dp(8), dp(16), dp(8));
        button.setOnClickListener(view -> action.run());
        return button;
    }
    void addButton(LinearLayout target,String label,Runnable action) {
        target.addView(button(label,action));
    }
    int onAccent(){return ColorUtils.calculateLuminance(TEAL)>0.45?NAVY:Color.WHITE;}
    void updatePalette() {
        boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int[][] colors={{Color.rgb(0,108,117),Color.rgb(130,212,208)},{Color.rgb(36,93,157),Color.rgb(154,198,255)},{Color.rgb(107,67,155),Color.rgb(210,183,255)}};
        TEAL=colors[AppPrefs.accent(this)][dark?1:0];
        INK=MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurface,Color.BLACK);
        MUTED=MaterialColors.getColor(this,com.google.android.material.R.attr.colorOnSurfaceVariant,Color.GRAY);
        SURFACE=MaterialColors.getColor(this,com.google.android.material.R.attr.colorSurface,Color.WHITE);
        BG=getColor(R.color.app_background);
    }
    EditText selectable(LinearLayout form,String label,String value) {
        EditText field=input(form,label,value,false);
        field.setInputType(InputType.TYPE_NULL);field.setKeyListener(null);field.setFocusable(false);field.setFocusableInTouchMode(false);field.setCursorVisible(false);field.setLongClickable(false);
        return field;
    }
    EditText dateField(LinearLayout form,long initial) {
        EditText field=selectable(form,"انتخاب تاریخ شمسی",fa(PersianDates.date(initial)));
        field.setOnClickListener(v->Pickers.date(this,PersianDates.parse(field.getText().toString(),"12:00"),chosen->field.setText(fa(PersianDates.date(chosen)))));
        return field;
    }
    EditText timeField(LinearLayout form,String label,int initial) {
        EditText field=selectable(form,label,fa(PersianDates.clock(initial)));
        field.setOnClickListener(v->Pickers.time(this,PersianDates.minutes(field.getText().toString()),chosen->field.setText(fa(PersianDates.clock(chosen)))));
        return field;
    }
    EditText durationField(LinearLayout form,int initial,int max) {
        EditText field=selectable(form,"انتخاب زمان یادآوری",Pickers.durationLabel(initial));
        field.setTag(initial);
        field.setOnClickListener(v->Pickers.duration(this,(Integer)field.getTag(),max,chosen->{field.setTag(chosen);field.setText(Pickers.durationLabel(chosen));}));
        return field;
    }
    void heading(String s,String sub) {
        body.addView(title(s));
        body.addView(text(sub,13,MUTED));
    }
    LinearLayout card() { return card(body); }
    LinearLayout card(LinearLayout target) {
        MaterialCardView shell = new MaterialCardView(this);
        shell.setRadius(dp(24));
        shell.setCardElevation(dp(1));
        shell.setStrokeWidth(dp(1));
        shell.setStrokeColor(ColorUtils.blendARGB(INK,SURFACE,0.93f));
        shell.setCardBackgroundColor(SURFACE);
        LinearLayout content = column();
        content.setPadding(dp(18), dp(16), dp(18), dp(16));
        shell.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, dp(8));
        target.addView(shell, params);
        return content;
    }
    void section(String label, String action, Runnable callback) {
        LinearLayout line = row();
        line.setPadding(0, dp(18), 0, dp(4));
        line.addView(title(label), new LinearLayout.LayoutParams(0, -2, 1));
        if(callback != null) line.addView(button(action, callback));
        body.addView(line);
    }
    void metric(LinearLayout target, String value, String label, int color) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        target.addView(UiParts.metric(this, value, label, color), params);
    }
    void badge(LinearLayout target, String label, int color) {
        target.addView(UiParts.badge(this, label, color), new LinearLayout.LayoutParams(-2, -2));
    }
    void empty(String message) {
        LinearLayout content = card();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(24), dp(28), dp(24), dp(28));
        ImageView illustration = new ImageView(this);
        illustration.setImageResource(R.drawable.ic_calendar);
        illustration.setImageTintList(ColorStateList.valueOf(TEAL));
        illustration.setPadding(dp(18), dp(18), dp(18), dp(18));
        illustration.setBackground(surface(ColorUtils.blendARGB(TEAL,BG,0.88f), 24));
        content.addView(illustration, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView label = title(message);
        label.setGravity(Gravity.CENTER);
        content.addView(label);
        TextView help = text("برنامه‌ات از همین‌جا شروع می‌شود.\nدکمهٔ افزودن را لمس کن.", 14, MUTED);
        help.setGravity(Gravity.CENTER);
        content.addView(help);
    }
    void toast(String s) {
        Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    }
    String fa(String s) {
        return PersianDates.digits(s);
    }
    String course(long id) {
        JSONObject c=db.get("courses",id);
        return c==null?"بدون درس":c.optString("name");
    }
    int day(long millis) {
        java.util.Calendar c=java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c.get(java.util.Calendar.DAY_OF_WEEK)%7;
    }
    String dateLabel(long t) {
        return DAYS[day(t)]+"، "+fa(PersianDates.date(t));
    }
    boolean sameDay(long a,long b) {
        return PersianDates.date(a).equals(PersianDates.date(b));
    }
    void changed() {
        Reminders.schedule(this);
        LocalMaterials.cleanup(this,db);
        render();
    }
    /** Run only after setContentView; View's controller can be null until attachment. */
    private void configureSystemBars(View decor) {
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?SURFACE:NAVY);
        boolean dark=(getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if(Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController controller=decor.getWindowInsetsController();
            if(controller!=null){
                int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark?0:mask,mask);
                return;
            }
        }
        int flags=dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=27?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
        decor.setSystemUiVisibility(flags);
    }
    void render() {
        updatePalette();
        root = column();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if(Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout header = row();
        header.setPadding(dp(20), dp(12), dp(20), dp(10));
        LinearLayout branding = column();
        TextView name = title("دانش‌یار");
        name.setTextSize(25);
        branding.addView(name);
        branding.addView(text("برنامهٔ تو، مسیر موفقیت تو", 12, MUTED));
        header.addView(branding, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton settings = (MaterialButton) button("", () -> { page=5; render(); });
        settings.setIconResource(R.drawable.ic_settings);
        settings.setIconTint(ColorStateList.valueOf(INK));
        settings.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        settings.setIconPadding(0);
        settings.setContentDescription("تنظیمات و دربارهٔ برنامه");
        settings.setMinWidth(0);
        settings.setPadding(dp(12), 0, dp(12), 0);
        settings.setCornerRadius(dp(24));
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        FrameLayout stage = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        body = column();
        body.setPadding(dp(18), dp(8), dp(18), dp(110));
        scroll.addView(body);
        stage.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if(page < 5) {
            ExtendedFloatingActionButton add = new ExtendedFloatingActionButton(this);
            add.setText("افزودن");
            add.setIconResource(R.drawable.ic_add);
            add.setIconTint(ColorStateList.valueOf(onAccent()));
            add.setTextColor(onAccent());
            add.setBackgroundTintList(ColorStateList.valueOf(TEAL));
            add.setOnClickListener(view -> {
                if(page==1) editSession(null);
                else if(page==3) editCourse(null);
                else editEvent(null);
            });
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            params.setMargins(dp(20), 0, dp(20), dp(16));
            stage.addView(add, params);
        }
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        BottomNavigationView navigation = new BottomNavigationView(this);
        navigation.setBackgroundColor(SURFACE);
        navigation.setElevation(dp(6));
        navigation.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_LABELED);
        String[] labels = {"امروز", "هفته", "رویدادها", "دروس", "تقویم"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_week, R.drawable.ic_events, R.drawable.ic_courses, R.drawable.ic_calendar};
        for(int i=0; i<labels.length; i++) navigation.getMenu().add(0, 100+i, i, labels[i]).setIcon(icons[i]);
        ColorStateList colors = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{TEAL, MUTED});
        navigation.setItemIconTintList(colors);
        navigation.setItemTextColor(colors);
        if(page < 5) navigation.setSelectedItemId(100+page);
        navigation.setOnItemSelectedListener(item -> { page=item.getItemId()-100; render(); return true; });
        root.addView(navigation);
        setContentView(root);
        View decor=getWindow().getDecorView();
        configureSystemBars(decor);
        decor.post(()->configureSystemBars(decor));
        root.requestApplyInsets();
        if(page==0) today();
        else if(page==1) week();
        else if(page==2) events();
        else if(page==3) courses();
        else if(page==4) calendar();
        else if(page==6) materials();
        else settings();
    }
    void today() {
        long now = System.currentTimeMillis();
        heading("سلام، روزت را بساز", dateLabel(now));
        int units=0, pending=0, completed=0, allEvents=0;
        double weighted=0, graded=0;
        for(JSONObject course : db.all("courses")) {
            units += course.optInt("units");
            if(!course.isNull("grade")) {
                graded += course.optInt("units");
                weighted += course.optDouble("grade") * course.optInt("units");
            }
        }
        for(JSONObject event : db.all("events")) {
            allEvents++;
            if(event.optInt("done")==1) completed++;
            else if(event.optLong("due")>=now) pending++;
        }
        LinearLayout hero = card();
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TR_BL, new int[]{Color.rgb(0, 117, 121), NAVY});
        gradient.setCornerRadius(dp(24));
        hero.setBackground(gradient);
        hero.setPadding(dp(24), dp(24), dp(24), dp(22));
        hero.addView(text("نمای کلی نیم‌سال", 12, Color.rgb(172, 224, 223)));
        TextView heroTitle = text("هر روز، یک قدم جلوتر", 23, Color.WHITE);
        heroTitle.setTypeface(null, Typeface.BOLD);
        hero.addView(heroTitle);
        hero.addView(text("درس‌ها، مهلت‌ها و کلاس‌هایت را یک‌جا مدیریت کن.", 13, Color.rgb(216, 238, 241)));
        hero.addView(text(fa(completed+" از "+allEvents+" کار انجام‌شده"), 13, Color.WHITE));
        hero.addView(UiParts.progress(this, allEvents==0?0:completed*100/allEvents, Color.rgb(94, 224, 205)), new LinearLayout.LayoutParams(-1, dp(6)));
        LinearLayout stats = row();
        stats.setPadding(0, dp(10), 0, dp(4));
        metric(stats, fa(""+units), "واحد این ترم", TEAL);
        metric(stats, graded==0?"—":fa(String.format(Locale.US, "%.2f", weighted/graded)), "معدل ثبت‌شده", Color.rgb(86, 96, 165));
        metric(stats, fa(""+pending), "مهلت پیش رو", Color.rgb(181, 99, 40));
        body.addView(stats);
        section("کلاس‌های امروز", "برنامهٔ هفته", () -> {page=1; render();});
        daily(now);
        section("روی میز تو", "همهٔ رویدادها", () -> {page=2; render();});
        int shown=0;
        for(JSONObject event : db.all("events")) if(event.optInt("done")==0) {
            eventCard(event);
            if(++shown==3) break;
        }
        if(shown==0) empty("همه‌چیز مرتب است");
    }
    void daily(long date) {
        int n=0;
        for(JSONObject s:db.all("sessions"))if(s.optInt("day")==day(date)) {
            sessionCard(s);
            n++;
        }
        if(n==0) {
            LinearLayout c=card();
            c.addView(text("امروز کلاسی ندارید",16,MUTED));
        }
    }
    void week() {
        heading("هفتهٔ تو", "زمان کلاس‌ها را مرتب کن و تداخل‌ها را زود ببین");
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = row();
        for(int i=0; i<7; i++) {
            final int selected=i;
            Chip chip = new Chip(this);
            chip.setText(DAYS[i]);
            chip.setCheckable(true);
            chip.setChecked(i==weekDay);
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(view -> {weekDay=selected; render();});
            choices.addView(chip);
        }
        strip.addView(choices);
        body.addView(strip);
        section("کلاس‌های "+DAYS[weekDay], "", null);
        int count=0;
        for(JSONObject session : db.all("sessions")) if(session.optInt("day")==weekDay) {
            sessionCard(session);
            count++;
        }
        if(count==0) empty("یک روز آزاد در برنامه");
    }
    void sessionCard(JSONObject session) {
        LinearLayout content = card();
        LinearLayout top = row();
        top.addView(title(course(session.optLong("course_id"))), new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(UiParts.badge(this, "کلاس", TEAL));
        content.addView(top);
        TextView clock = text(fa(PersianDates.clock(session.optInt("start"))+" — "+PersianDates.clock(session.optInt("end"))), 23, TEAL);
        clock.setTypeface(null, Typeface.BOLD);
        content.addView(clock);
        content.addView(text(session.optString("room").isEmpty()?"محل کلاس مشخص نشده":session.optString("room"), 14, MUTED));
        content.addView(text(session.optInt("remind")<0?"یادآوری خاموش":fa("یادآوری "+session.optInt("remind")+" دقیقه قبل"), 12, MUTED));
        content.addView(button("جزئیات و ویرایش", () -> editSession(session)));
    }
    void events() {
        heading("کارها و مهلت‌ها", "اولویت‌هایت را پیدا کن؛ هیچ موعدی را از دست نده");
        String[] statuses={"همه", "در انتظار", "انجام‌شده", "عقب‌افتاده"};
        HorizontalScrollView statusStrip=new HorizontalScrollView(this);
        statusStrip.setHorizontalScrollBarEnabled(false);
        LinearLayout statusesRow=row();
        for(int i=0;i<statuses.length;i++) {
            final int selected=i;
            Chip chip=new Chip(this);
            chip.setText(statuses[i]);
            chip.setCheckable(true);
            chip.setChecked(eventFilter==i);
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(view->{eventFilter=selected;render();});
            statusesRow.addView(chip);
        }
        statusStrip.addView(statusesRow);
        body.addView(statusStrip);
        String[] kinds={"همهٔ انواع", "امتحان", "تکلیف", "پروژه", "دانشگاهی"};
        HorizontalScrollView kindStrip=new HorizontalScrollView(this);
        kindStrip.setHorizontalScrollBarEnabled(false);
        LinearLayout kindsRow=row();
        for(int i=0;i<kinds.length;i++) {
            final int selected=i;
            Chip chip=new Chip(this);
            chip.setText(kinds[i]);
            chip.setCheckable(true);
            chip.setChecked(kindFilter==i);
            chip.setCheckedIconVisible(false);
            chip.setOnClickListener(view->{kindFilter=selected;render();});
            kindsRow.addView(chip);
        }
        kindStrip.addView(kindsRow);
        body.addView(kindStrip);
        int shown=0;
        long now=System.currentTimeMillis();
        for(JSONObject event:db.all("events")) {
            boolean done=event.optInt("done")==1;
            if(kindFilter>0 && event.optInt("kind")!=kindFilter-1) continue;
            if(eventFilter==1 && done || eventFilter==2 && !done || eventFilter==3 && (done || event.optLong("due")>=now)) continue;
            eventCard(event);
            shown++;
        }
        if(shown==0) empty("در این بخش کاری ثبت نشده");
    }
    void eventCard(JSONObject event) {
        LinearLayout content=card();
        boolean done=event.optInt("done")==1;
        long due=event.optLong("due");
        boolean late=!done && due<System.currentTimeMillis();
        int color=done?MUTED:late?Color.rgb(176, 54, 61):TEAL;
        LinearLayout labels=row();
        labels.addView(UiParts.badge(this, KINDS[event.optInt("kind")], Color.rgb(86, 96, 165)), new LinearLayout.LayoutParams(0, -2, 1));
        labels.addView(UiParts.badge(this, done?"انجام شد":late?"مهلت گذشته":"در انتظار", color));
        content.addView(labels);
        TextView name=title(event.optString("title"));
        if(done)name.setPaintFlags(name.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        content.addView(name);
        content.addView(text(dateLabel(due)+"  ·  "+fa(PersianDates.time(due)), 14, color));
        if(!event.isNull("course_id")) content.addView(text(course(event.optLong("course_id")), 13, MUTED));
        if(!event.optString("notes").isEmpty()) content.addView(text(event.optString("notes"), 14, INK));
        LinearLayout actions=row();
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
        params.setMargins(dp(4),dp(8),dp(4),0);
        actions.addView(button("ویرایش",()->editEvent(event)),params);
        actions.addView(button(done?"بازگشایی":"انجام دادم",()->{
            ContentValues values=new ContentValues();
            values.put("done",done?0:1);
            db.save("events",event.optLong("id"),values);
            changed();
        }),params);
        content.addView(actions);
    }
    void courses() {
        heading("دروس و کارنامه", "نمره، واحد و استاد هر درس در یک نگاه");
        addButton(body,"مدیریت اساتید",this::teachers);
        List<JSONObject> courses=db.all("courses");
        LinearLayout results=column();
        EditText search=input(body,"جست‌وجوی نام درس یا استاد","",false);
        body.addView(results);
        drawCourses(results,courses,"");
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence value,int start,int before,int count){drawCourses(results,courses,value.toString());}
            @Override public void afterTextChanged(Editable value){}
        });
    }
    void drawCourses(LinearLayout target,List<JSONObject> courses,String query) {
        target.removeAllViews();
        int shown=0;
        for(JSONObject course:courses) {
            JSONObject teacher=db.get("teachers",course.optLong("teacher_id"));
            String teacherName=teacher==null?"استاد مشخص نشده":teacher.optString("name");
            if(!course.optString("name").contains(query.trim())&&!teacherName.contains(query.trim()))continue;
            LinearLayout content=card(target);
            LinearLayout top=row();
            top.addView(title(course.optString("name")),new LinearLayout.LayoutParams(0,-2,1));
            top.addView(UiParts.badge(this,fa(course.optInt("units")+" واحد"),TEAL));
            content.addView(top);
            content.addView(text(teacherName,14,MUTED));
            LinearLayout gradeLine=row();
            gradeLine.addView(text("نمرهٔ ثبت‌شده",13,MUTED),new LinearLayout.LayoutParams(0,-2,1));
            gradeLine.addView(title(course.isNull("grade")?"—":fa(course.optString("grade"))+" / "+fa("20")));
            content.addView(gradeLine);
            if(!course.isNull("grade")) content.addView(UiParts.progress(this,(int)Math.round(course.optDouble("grade")*5),TEAL),new LinearLayout.LayoutParams(-1,dp(6)));
            int resourceCount=0;for(JSONObject r:db.all("resources"))if(r.optLong("course_id")==course.optLong("id"))resourceCount++;
            content.addView(button(fa("پروندهٔ درس · "+resourceCount+" مورد"),()->{materialsCourse=course.optLong("id");page=6;render();}));
            content.addView(button("جزئیات و ویرایش",()->editCourse(course)));
            shown++;
        }
        if(shown==0){LinearLayout content=card(target);content.addView(text(query.isEmpty()?"اولین درس این ترم را با دکمهٔ افزودن ثبت کن.":"درسی با این نام پیدا نشد.",15,MUTED));}
    }
    void materials() {
        JSONObject courseRow=db.get("courses",materialsCourse);
        if(courseRow==null){heading("پروندهٔ درس","درس پیدا نشد");addButton(body,"بازگشت به دروس",()->{page=3;render();});return;}
        heading("پروندهٔ "+courseRow.optString("name"),"جزوه، یادداشت و لینک • هر فایل تا ۲۵ مگابایت");
        addButton(body,"بازگشت به دروس",()->{page=3;render();});
        LinearLayout actions=row();
        actions.addView(button("افزودن فایل",()->{pendingCourse=materialsCourse;Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(choose,31);}));
        actions.addView(button("لینک",()->editResource(null,1)));
        actions.addView(button("یادداشت",()->editResource(null,2)));body.addView(actions);
        int count=0;
        for(JSONObject resource:db.all("resources"))if(resource.optLong("course_id")==materialsCourse){
            count++;LinearLayout content=card();int kind=resource.optInt("kind");
            badge(content,kind==0?"فایل محلی":kind==1?"لینک درسی":"یادداشت",TEAL);
            content.addView(title(resource.optString("title")));
            if(kind==0)content.addView(text(fa(String.format(Locale.US,"%.1f مگابایت",resource.optLong("size")/1048576.0)),12,MUTED));
            else if(kind==1)content.addView(text(resource.optString("value"),13,MUTED));
            else content.addView(text(resource.optString("value"),14,INK));
            LinearLayout controls=row();
            if(kind!=2)controls.addView(button(kind==0?"بازکردن فایل":"بازکردن لینک",()->{
                try{if(kind==0)LocalMaterials.openFile(this,resource);else{String value=resource.optString("value");if(!LocalMaterials.validLink(value))throw new IllegalArgumentException("لینک معتبر نیست");new MaterialAlertDialogBuilder(this).setTitle("بازکردن لینک بیرونی؟").setMessage("این لینک در برنامهٔ دیگر باز می‌شود و ممکن است اینترنت بخواهد.").setNegativeButton("انصراف",null).setPositiveButton("بازکردن",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value)));}catch(ActivityNotFoundException unavailable){toast("برنامه‌ای برای بازکردن لینک پیدا نشد");}}).show();}}
                catch(Exception error){toast("باز نشد: "+error.getMessage());}
            }));
            controls.addView(button("ویرایش",()->editResource(resource,kind)));
            content.addView(controls);
        }
        if(count==0)empty("پروندهٔ این درس هنوز خالی است");
    }
    void editResource(JSONObject old,int kind) {
        LinearLayout form=form();EditText title=input(form,"عنوان",value(old,"title",""),false);
        EditText value=kind==0?null:input(form,kind==1?"نشانی لینک https://…":"متن یادداشت",value(old,"value",""),false);
        if(kind==2){value.setSingleLine(false);value.setMinLines(4);}
        if(kind==1)form.addView(text("لینک گروه یا کانال را به‌صورت نشانی وب وارد کنید؛ مثل لینک t.me یا WhatsApp.",12,MUTED));
        dialog(kind==0?"نام فایل":kind==1?"لینک درسی":"یادداشت درس",form,"resources",old,()->{
            ContentValues values=new ContentValues();values.put("title",required(title));
            if(kind!=0){String body=required(value);if(kind==1&&!LocalMaterials.validLink(body))throw new IllegalArgumentException("لینک با http یا https و نام میزبان معتبر وارد کنید");values.put("value",body);}
            if(old==null){values.put("course_id",materialsCourse);values.put("kind",kind);values.put("added",System.currentTimeMillis());}
            db.save("resources",id(old),values);
        });
    }
    void importMaterial(Uri uri,long courseId,boolean audio) {
        String label=LocalMaterials.displayName(this,uri);
        ProgressDialog progress=ProgressDialog.show(this,audio?"صدای هشدار":"افزودن فایل درسی","کپی در حافظهٔ خصوصی گوشی…",true,false);busyDialog=progress;
        io.execute(()->{
            String message;String copied=null;
            try(Store store=new Store(getApplicationContext())){
                copied=LocalMaterials.copySelected(getApplicationContext(),uri);
                if(audio){AlarmService.stop(getApplicationContext());AppPrefs.get(getApplicationContext()).edit().putString("audio_file",copied).putString("sound_uri","").putString("sound_label",label).apply();}
                else{ContentValues values=new ContentValues();values.put("course_id",courseId);values.put("title",label);values.put("kind",0);values.put("value",copied);String mime=getContentResolver().getType(uri);values.put("mime",mime==null?"application/octet-stream":mime);values.put("size",LocalMaterials.file(getApplicationContext(),copied).length());values.put("added",System.currentTimeMillis());store.save("resources",0,values);}
                LocalMaterials.cleanup(getApplicationContext(),store);message=audio?"صدای مشترک یادآوری‌ها انتخاب شد":"فایل در پروندهٔ درس ذخیره شد";
            }catch(Exception error){if(copied!=null)LocalMaterials.file(getApplicationContext(),copied).delete();message="ذخیره نشد: "+error.getMessage();}
            finally{if(copied!=null)LocalMaterials.finishImport(copied);}
            final String result=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){progress.dismiss();toast(result);render();}});
        });
    }
    void teachers() {
        LinearLayout list=column();
        list.setPadding(dp(18),dp(10),dp(18),dp(10));
        addButton(list,"＋ افزودن استاد",()->editTeacher(null));
        for(JSONObject t:db.all("teachers")) {
            list.addView(button(t.optString("name")+" · "+t.optString("contact"),()->editTeacher(t)));
        }
        ScrollView scroll=new ScrollView(this);
        scroll.addView(list);
        new MaterialAlertDialogBuilder(this).setTitle("اساتید").setView(scroll).setNegativeButton("بستن",null).show();
    }
    void calendar() {
        android.icu.util.Calendar current=PersianDates.calendar(selectedDate);
        int year=current.get(android.icu.util.Calendar.YEAR),month=current.get(android.icu.util.Calendar.MONTH);
        heading("تقویم دانشگاهی",MONTHS[month]+" "+fa(""+year));
        LinearLayout controls=row();
        controls.addView(button("ماه قبل",()->shiftMonth(-1)));
        controls.addView(button("امروز",()-> {
            selectedDate=System.currentTimeMillis();render();
        }
        ));
        controls.addView(button("ماه بعد",()->shiftMonth(1)));
        body.addView(controls);
        android.icu.util.Calendar first=PersianDates.calendar(selectedDate);
        first.set(android.icu.util.Calendar.DAY_OF_MONTH,1);
        List<JSONObject> calendarEvents=db.all("events");
        int offset=day(first.getTimeInMillis()),days=first.getActualMaximum(android.icu.util.Calendar.DAY_OF_MONTH);
        LinearLayout grid=card();
        LinearLayout line=row();
        String[] shortDays= {
            "ش","ی","د","س","چ","پ","ج"
        };
        for(String label:shortDays) {
            TextView t=text(label,14,MUTED);
            t.setGravity(Gravity.CENTER);
            line.addView(t,new LinearLayout.LayoutParams(0,dp(36),1));
        }
        grid.addView(line);
        for(int cell=0;cell<((offset+days+6)/7)*7;cell++) {
            if(cell%7==0) {
                line=row();
                grid.addView(line);
            }
            int date=cell-offset+1;
            if(date<1||date>days) {
                line.addView(new TextView(this),new LinearLayout.LayoutParams(0,dp(48),1));
                continue;
            }
            android.icu.util.Calendar cal=PersianDates.calendar(first.getTimeInMillis());
            cal.set(android.icu.util.Calendar.DAY_OF_MONTH,date);
            final long value=cal.getTimeInMillis();
            boolean has=false;
            for(JSONObject e:calendarEvents)if(sameDay(value,e.optLong("due")))has=true;
            TextView cellView=text(fa(""+date)+(has?" •":""),13,sameDay(value,selectedDate)?onAccent():INK);
            cellView.setGravity(Gravity.CENTER);
            if(sameDay(value,selectedDate))cellView.setBackground(surface(TEAL,12));
            cellView.setContentDescription(dateLabel(value)+(has?" دارای رویداد":""));
            cellView.setOnClickListener(v-> {
                selectedDate=value;render();
            }
            );
            line.addView(cellView,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        body.addView(title(dateLabel(selectedDate)));
        daily(selectedDate);
        for(JSONObject e:db.all("events"))if(sameDay(selectedDate,e.optLong("due")))eventCard(e);
        
    }
    void shiftMonth(int delta) {
        android.icu.util.Calendar c=PersianDates.calendar(selectedDate);
        c.set(android.icu.util.Calendar.DAY_OF_MONTH,1);
        c.add(android.icu.util.Calendar.MONTH,delta);
        selectedDate=c.getTimeInMillis();
        render();
    }
    LinearLayout form() {
        LinearLayout f=column();
        f.setPadding(dp(20),dp(12),dp(20),dp(12));
        return f;
    }
    EditText input(LinearLayout target,String label,String value,boolean number) {
        TextInputLayout field=new TextInputLayout(this,null,com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(label);
        field.setBoxCornerRadii(dp(14),dp(14),dp(14),dp(14));
        TextInputEditText edit=new TextInputEditText(field.getContext());
        edit.setText(value);
        edit.setTextSize(15);
        edit.setSingleLine(true);
        edit.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);
        edit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        field.addView(edit,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,dp(10),0,dp(8));
        target.addView(field,params);
        return edit;
    }
    Spinner select(LinearLayout f,String label,String[] labels,int pos) {
        f.addView(text(label,13,MUTED));
        Spinner s=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setSelection(Math.max(0,pos));
        f.addView(s);
        return s;
    }
    interface SaveAction {
        void run() throws Exception;
    }
    void dialog(String title,LinearLayout f,String table,JSONObject old,SaveAction save) {
        ScrollView scroll=new ScrollView(this);
        scroll.addView(f);
        AlertDialog.Builder b=new MaterialAlertDialogBuilder(this).setTitle(title).setView(scroll).setPositiveButton("ذخیره",null).setNegativeButton("انصراف",null);
        if(old!=null)b.setNeutralButton("حذف",null);
        AlertDialog d=b.create();
        d.setOnShowListener(v-> {
            d.getButton(-1).setOnClickListener(view-> {
                try {
                    save.run();changed();d.dismiss();
                }
                catch(Exception ex) {
                    toast("ذخیره نشد: "+(ex.getMessage()==null?"اطلاعات نامعتبر":ex.getMessage()));
                }
            }
            );if(old!=null)d.getButton(-3).setOnClickListener(view->new MaterialAlertDialogBuilder(this).setTitle("حذف اطلاعات؟").setMessage(table.equals("courses")?"جلسه‌های این درس هم حذف می‌شوند؛ رویدادها باقی می‌مانند.":"این عمل قابل بازگشت نیست؛ نسخه پشتیبان تهیه کنید.").setNegativeButton("انصراف",null).setPositiveButton("حذف",(a,w)-> {
                db.delete(table,old.optLong("id"));changed();d.dismiss();
            }
            ).show());
        }
        );
        d.show();
    }
    String value(JSONObject old,String key,String fallback) {
        return old==null||old.isNull(key)?fallback:old.optString(key,fallback);
    }
    long id(JSONObject old) {
        return old==null?0:old.optLong("id");
    }
    String required(EditText e) {
        String s=e.getText().toString().trim();
        if(s.isEmpty())throw new IllegalArgumentException("عنوان یا نام را وارد کنید");
        return s;
    }
    int integer(EditText e) {
        return Integer.parseInt(PersianDates.ascii(e.getText().toString()));
    }
    void editTeacher(JSONObject old) {
        LinearLayout f=form();
        EditText name=input(f,"نام استاد",value(old,"name",""),false),contact=input(f,"راه ارتباطی (اختیاری)",value(old,"contact",""),false),office=input(f,"اتاق / ساعت مراجعه",value(old,"office",""),false);
        dialog("اطلاعات استاد",f,"teachers",old,()-> {
            ContentValues v=new ContentValues();v.put("name",required(name));v.put("contact",contact.getText().toString());v.put("office",office.getText().toString());db.save("teachers",id(old),v);
        }
        );
    }
    String[] names(List<JSONObject> rows,boolean optional) {
        String[] a=new String[rows.size()+(optional?1:0)];
        int i=0;
        if(optional)a[i++]="انتخاب نشده";
        for(JSONObject r:rows)a[i++]=r.optString("name");
        return a;
    }
    int position(List<JSONObject> rows,long id,boolean optional) {
        for(int i=0;i<rows.size();i++)if(rows.get(i).optLong("id")==id)return i+(optional?1:0);
        return 0;
    }
    void link(ContentValues v,String key,Spinner spinner,List<JSONObject> list,boolean optional) {
        int index=spinner.getSelectedItemPosition()-(optional?1:0);
        if(index<0)v.putNull(key);
        else v.put(key,list.get(index).optLong("id"));
    }
    void editCourse(JSONObject old) {
        LinearLayout f=form();
        EditText name=input(f,"نام درس",value(old,"name",""),false);
        List<JSONObject> teachers=db.all("teachers");
        Spinner teacher=select(f,"استاد (در صفحه دروس ← اساتید اضافه کنید)",names(teachers,true),position(teachers,old==null?0:old.optLong("teacher_id"),true));
        EditText units=input(f,"تعداد واحد (۱ تا ۱۲)",value(old,"units","3"),true),grade=input(f,"نمره (۰ تا ۲۰؛ خالی یعنی ثبت نشده)",value(old,"grade",""),true);
        dialog("اطلاعات درس",f,"courses",old,()-> {
            ContentValues v=new ContentValues();v.put("name",required(name));int u=integer(units);if(u<1||u>12)throw new IllegalArgumentException("واحد بین ۱ و ۱۲ باشد");v.put("units",u);String g=PersianDates.ascii(grade.getText().toString());if(g.isEmpty())v.putNull("grade");else {
                double n=Double.parseDouble(g);if(!Double.isFinite(n)||n<0||n>20)throw new IllegalArgumentException("نمره بین ۰ و ۲۰ باشد");v.put("grade",n);
            }
            link(v,"teacher_id",teacher,teachers,true);db.save("courses",id(old),v);
        }
        );
    }
    void editSession(JSONObject old) {
        List<JSONObject> courses=db.all("courses");
        if(courses.isEmpty()){toast("ابتدا یک درس اضافه کنید");page=3;render();return;}
        LinearLayout form=form();
        Spinner course=select(form,"درس",names(courses,false),position(courses,old==null?0:old.optLong("course_id"),false));
        Spinner day=select(form,"روز هفته",DAYS,old==null?weekDay:old.optInt("day"));
        EditText start=timeField(form,"انتخاب ساعت شروع",old==null?540:old.optInt("start"));
        EditText end=timeField(form,"انتخاب ساعت پایان",old==null?660:old.optInt("end"));
        EditText room=input(form,"کلاس / ساختمان",value(old,"room",""),false);
        EditText reminder=durationField(form,old==null?15:old.optInt("remind"),1440);
        dialog("جلسهٔ کلاس",form,"sessions",old,()->{
            int begin=PersianDates.minutes(start.getText().toString()),finish=PersianDates.minutes(end.getText().toString());
            if(finish<=begin)throw new IllegalArgumentException("پایان باید بعد از شروع باشد");
            for(JSONObject other:db.all("sessions"))if(other.optLong("id")!=id(old)&&other.optInt("day")==day.getSelectedItemPosition()&&begin<other.optInt("end")&&finish>other.optInt("start"))throw new IllegalArgumentException("این ساعت با یک کلاس دیگر تداخل دارد");
            ContentValues values=new ContentValues();
            values.put("course_id",courses.get(course.getSelectedItemPosition()).optLong("id"));values.put("day",day.getSelectedItemPosition());
            values.put("start",begin);values.put("end",finish);values.put("room",room.getText().toString());values.put("remind",(Integer)reminder.getTag());
            db.save("sessions",id(old),values);
        });
    }
    void editEvent(JSONObject old) {
        LinearLayout form=form();EditText name=input(form,"عنوان",value(old,"title",""),false);
        Spinner kind=select(form,"نوع",KINDS,old==null?0:old.optInt("kind"));
        List<JSONObject> courses=db.all("courses");
        Spinner course=select(form,"درس مرتبط (اختیاری)",names(courses,true),position(courses,old==null?0:old.optLong("course_id"),true));
        long initial=old==null?(page==4?selectedDate:System.currentTimeMillis()):old.optLong("due");
        EditText date=dateField(form,initial);
        EditText time=timeField(form,"انتخاب ساعت",old==null?540:PersianDates.minutes(PersianDates.time(initial)));
        EditText reminder=durationField(form,old==null?60:old.optInt("lead"),10080);
        EditText notes=input(form,"توضیحات",value(old,"notes",""),false);notes.setSingleLine(false);notes.setMinLines(2);
        dialog("رویداد و مهلت",form,"events",old,()->{
            long due=PersianDates.parse(date.getText().toString(),time.getText().toString());int lead=(Integer)reminder.getTag();
            ContentValues values=new ContentValues();values.put("title",required(name));values.put("kind",kind.getSelectedItemPosition());
            link(values,"course_id",course,courses,true);values.put("due",due);values.put("lead",lead);values.put("done",old==null?0:old.optInt("done"));values.put("notes",notes.getText().toString());
            db.save("events",id(old),values);
            if(lead>=0&&due-lead*60000L<=System.currentTimeMillis())toast("زمان یادآوری گذشته است؛ رویداد ذخیره شد ولی هشدار گذشته پخش نمی‌شود");
        });
    }
    void settings() {
        heading("تنظیمات و نسخهٔ پشتیبان","اطلاعات فقط روی دستگاه شماست");
        LinearLayout appearance=card();appearance.addView(title("ظاهر برنامه"));
        String[] themes={"هماهنگ با گوشی","روشن","تیره"};
        addButton(appearance,"تم: "+themes[AppPrefs.theme(this)],()->new MaterialAlertDialogBuilder(this).setTitle("انتخاب تم").setSingleChoiceItems(themes,AppPrefs.theme(this),(d,index)->{AppPrefs.get(this).edit().putInt("theme",index).apply();d.dismiss();AppPrefs.applyTheme(this);recreate();}).setNegativeButton("بستن",null).show());
        String[] accents={"آبی‌سبز","آبی","بنفش"};
        addButton(appearance,"رنگ اصلی: "+accents[AppPrefs.accent(this)],()->new MaterialAlertDialogBuilder(this).setTitle("رنگ اصلی").setSingleChoiceItems(accents,AppPrefs.accent(this),(d,index)->{AppPrefs.get(this).edit().putInt("accent",index).apply();d.dismiss();render();}).setNegativeButton("بستن",null).show());
        LinearLayout alarm=card();alarm.addView(title("صدای مشترک همهٔ یادآوری‌ها"));
        alarm.addView(text(AppPrefs.get(this).getString("sound_label","صدای پیش‌فرض گوشی"),14,MUTED));
        addButton(alarm,"انتخاب زنگ گوشی",()->{Intent choose=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);choose.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_ALARM);choose.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,false);choose.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,true);startActivityForResult(choose,33);});
        addButton(alarm,"انتخاب فایل صوتی از گوشی",()->{Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY,true);startActivityForResult(choose,32);});
        Switch loop=new Switch(this);loop.setText("تکرار صدا تا زدن دکمهٔ توقف");loop.setChecked(AppPrefs.looping(this));loop.setOnCheckedChangeListener((b,on)->{AlarmService.stop(this);AppPrefs.get(this).edit().putBoolean("loop",on).apply();});alarm.addView(loop);
        addButton(alarm,"پخش آزمایشی",()->{if(!AlarmService.start(this,"آزمون صدای انتخاب‌شده"))toast("اجرای هشدار ممکن نشد؛ مجوزها را بررسی کنید");});
        addButton(alarm,"توقف صدای هشدار",()->{AlarmService.stop(this);toast("صدای هشدار متوقف شد");});
        if(Build.VERSION.SDK_INT>=31){AlarmManager manager=(AlarmManager)getSystemService(ALARM_SERVICE);alarm.addView(text(manager.canScheduleExactAlarms()?"مجوز هشدار دقیق فعال است":"برای هشدار خودکار در پس‌زمینه، مجوز هشدار دقیق را فعال کنید.",13,MUTED));addButton(alarm,"اجازهٔ هشدار دقیق",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));}
        alarm.addView(text("حجم صدا از کانال آلارم گوشی گرفته می‌شود. حالت بی‌صدا، تماس، توقف اجباری یا محدودیت باتری ممکن است پخش را متوقف یا محدود کند.",13,MUTED));
        LinearLayout info=card();
        info.addView(title("آفلاین و خصوصی"));
        info.addView(text("بدون حساب کاربری، سرور، تبلیغات یا مجوز اینترنت. فایل پشتیبان شامل اطلاعات شخصی است؛ آن را در محل امن ذخیره کنید.",14,MUTED));
        LinearLayout notifications=card();
        notifications.addView(title("اعلان‌های محلی"));
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch=nm.getNotificationChannel(Reminders.CHANNEL);
        boolean enabled=nm.areNotificationsEnabled()&&ch!=null&&ch.getImportance()!=NotificationManager.IMPORTANCE_NONE;
        notifications.addView(text(enabled?"اعلان‌ها فعال هستند":"اعلان‌ها خاموش هستند",14,TEAL));
        notifications.addView(text("بدون مجوز هشدار دقیق، اعلان ممکن است دیر برسد و برای پخش صدا نیاز به لمس آن باشد. پس از توقف اجباری برنامه، آن را دوباره باز کنید. کلاس‌ها هر هفته تکرار می‌شوند.",13,MUTED));
        addButton(notifications,"اجازه / تنظیم اعلان‌ها",()-> {
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[] {
                Manifest.permission.POST_NOTIFICATIONS
            },7);else {
                Intent i=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);
            }
        }
        );
        addButton(notifications,"آزمون اعلان",()-> {
            Reminders.notify(this,"این یک یادآوری آزمایشی است",-1);toast("اگر اجازه اعلان فعال باشد، اعلان آزمایشی نمایش داده می‌شود");
        }
        );
        LinearLayout backup=card();
        backup.addView(title("پشتیبان‌گیری محلی"));
        backup.addView(text("برای حفظ آفلاین بودن، در انتخابگر فایل پوشهٔ حافظهٔ گوشی را انتخاب کنید. بازیابی، اطلاعات فعلی را جایگزین می‌کند.",14,MUTED));
        addButton(backup,"ذخیره نسخهٔ پشتیبان",()-> {
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,"daneshyar-"+PersianDates.date(System.currentTimeMillis()).replace('/','-')+".zip");startActivityForResult(i,10);
        }
        );
        addButton(backup,"بازیابی از فایل",()-> {
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_LOCAL_ONLY,true);i.setType("*/*");startActivityForResult(i,11);
        }
        );
        LinearLayout about=card();
        about.addView(title("دربارهٔ دانش‌یار"));
        about.addView(text("نویسنده: امیرحسین فلاحتی",16,INK));
        about.addView(text("نسخهٔ "+installedVersion()+" • طراحی Material 3\nتمام اطلاعات روی دستگاه شما ذخیره می‌شود.",14,MUTED));
    }
    @Override protected void onResume() {
        super.onResume();
        if(db!=null&&root!=null) {
            Reminders.schedule(this);
            render();
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==33){
            Uri tone=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if(tone==null)tone=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            AlarmService.stop(this);android.media.Ringtone ringtone=RingtoneManager.getRingtone(this,tone);
            AppPrefs.get(this).edit().putString("audio_file","").putString("sound_uri",tone==null?"":tone.toString()).putString("sound_label",ringtone==null?"صدای پیش‌فرض گوشی":ringtone.getTitle(this)).apply();
            LocalMaterials.cleanup(this,db);render();return;
        }
        Uri uri=data.getData();if(uri==null)return;
        if(request==31){importMaterial(uri,pendingCourse,false);return;}
        if(request==32){importMaterial(uri,0,true);return;}
        if(request==10)exportBackup(uri);
        else if(request==11)new MaterialAlertDialogBuilder(this).setTitle("جایگزینی اطلاعات؟").setMessage("اطلاعات و فایل‌های درسی با پشتیبان جایگزین می‌شوند؛ قبل از ادامه پشتیبان تهیه کنید.").setNegativeButton("انصراف",null).setPositiveButton("بازیابی",(d,w)->importBackup(uri)).show();
    }
    void exportBackup(Uri uri) {
        ProgressDialog progress=ProgressDialog.show(this,"نسخهٔ پشتیبان کامل","ذخیرهٔ داده‌ها و فایل‌های درسی…",true,false);busyDialog=progress;
        io.execute(()->{
            String message;
            try(Store store=new Store(getApplicationContext());OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("فایل باز نشد");BackupFiles.export(getApplicationContext(),store,out);message="پشتیبان کامل داده‌ها و فایل‌ها ذخیره شد";
            }catch(Exception error){message="ذخیره ناموفق: "+error.getMessage();}
            final String result=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){progress.dismiss();toast(result);}});
        });
    }
    void importBackup(Uri uri) {
        ProgressDialog progress=ProgressDialog.show(this,"بازیابی","بررسی و بازیابی فایل‌ها…",true,false);busyDialog=progress;
        io.execute(()->{
            String message;
            try(Store store=new Store(getApplicationContext());InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("فایل باز نشد");BackupFiles.restore(getApplicationContext(),store,in);message="اطلاعات و فایل‌ها بازیابی شد";
                try{Reminders.schedule(getApplicationContext());}catch(RuntimeException alarmError){message+="؛ تنظیم مجدد هشدار را بررسی کنید";}
            }catch(Exception error){message="بازیابی ناموفق؛ اطلاعات قبلی حفظ شد: "+error.getMessage();}
            final String result=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){progress.dismiss();toast(result);AppPrefs.applyTheme(this);render();}});
        });
    }
    private String installedVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(),0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { return "نامشخص"; }
    }

}
