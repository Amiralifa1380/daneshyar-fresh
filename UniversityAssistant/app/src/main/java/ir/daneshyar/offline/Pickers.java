package ir.daneshyar.offline;

import android.widget.*;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import android.icu.util.Calendar;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** All dates, times and reminder durations are selected, never manually formatted. */
public final class Pickers {
    private Pickers() {}
    private static final String[] MONTHS={"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"};
    static NumberPicker number(AppCompatActivity c,int min,int max,int value) {
        NumberPicker p=new NumberPicker(c);p.setMinValue(min);p.setMaxValue(max);p.setValue(Math.max(min,Math.min(max,value)));
        p.setWrapSelectorWheel(false);p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        p.setFormatter(n->PersianDates.digits(Integer.toString(n)));return p;
    }
    static void wheel(LinearLayout row,NumberPicker picker,String label) {
        LinearLayout box=new LinearLayout(row.getContext());box.setOrientation(LinearLayout.VERTICAL);
        TextView t=new TextView(row.getContext());t.setText(label);t.setGravity(android.view.Gravity.CENTER);box.addView(t);box.addView(picker);
        row.addView(box,new LinearLayout.LayoutParams(0,-2,1));
    }
    public static void date(AppCompatActivity c,long initial,LongConsumer callback) {
        Calendar cal=PersianDates.calendar(initial);
        LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        NumberPicker year=number(c,1200,1600,cal.get(Calendar.YEAR));
        NumberPicker month=number(c,1,12,cal.get(Calendar.MONTH)+1);month.setDisplayedValues(MONTHS);
        NumberPicker day=number(c,1,cal.getActualMaximum(Calendar.DAY_OF_MONTH),cal.get(Calendar.DAY_OF_MONTH));
        Runnable clamp=()->{
            long first=PersianDates.parse(String.format(Locale.US,"%04d/%02d/01",year.getValue(),month.getValue()),"12:00");
            int maximum=PersianDates.calendar(first).getActualMaximum(Calendar.DAY_OF_MONTH);
            if(day.getValue()>maximum)day.setValue(maximum);
            day.setMaxValue(maximum);
        };
        year.setOnValueChangedListener((p,old,n)->clamp.run());month.setOnValueChangedListener((p,old,n)->clamp.run());
        wheel(row,year,"سال");wheel(row,month,"ماه");wheel(row,day,"روز");
        new MaterialAlertDialogBuilder(c).setTitle("انتخاب تاریخ شمسی").setView(row).setNegativeButton("انصراف",null)
            .setPositiveButton("انتخاب",(d,w)->callback.accept(PersianDates.parse(String.format(Locale.US,"%04d/%02d/%02d",year.getValue(),month.getValue(),day.getValue()),"12:00"))).show();
    }
    public static void time(AppCompatActivity c,int initial,IntConsumer callback) {
        MaterialTimePicker picker=new MaterialTimePicker.Builder().setTitleText("انتخاب ساعت")
            .setTimeFormat(TimeFormat.CLOCK_24H).setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setHour(initial/60).setMinute(initial%60).setPositiveButtonText("انتخاب").setNegativeButtonText("انصراف").build();
        picker.addOnPositiveButtonClickListener(v->callback.accept(picker.getHour()*60+picker.getMinute()));
        picker.show(c.getSupportFragmentManager(),"time_picker");
    }
    public static String durationLabel(int value){
        if(value<0)return "خاموش";
        if(value==0)return "در زمان شروع";
        int days=value/1440,hours=(value%1440)/60,minutes=value%60;
        java.util.List<String> parts=new java.util.ArrayList<>();
        if(days>0)parts.add(days+" روز");if(hours>0)parts.add(hours+" ساعت");if(minutes>0)parts.add(minutes+" دقیقه");
        return PersianDates.digits(android.text.TextUtils.join(" و ",parts)+" قبل");
    }
    public static void duration(AppCompatActivity c,int initial,int maximum,IntConsumer callback) {
        java.util.List<Integer> values=new java.util.ArrayList<>();java.util.List<String> labels=new java.util.ArrayList<>();
        int[] presets={-1,0,5,10,15,30,60,120,1440,10080};
        for(int n:presets)if(n<=maximum){values.add(n);labels.add(durationLabel(n));}
        labels.add("انتخاب مدت دلخواه…");
        new MaterialAlertDialogBuilder(c).setTitle("زمان یادآوری").setItems(labels.toArray(new String[0]),(d,index)->{
            if(index<values.size()){callback.accept(values.get(index));return;}
            LinearLayout row=new LinearLayout(c);row.setOrientation(LinearLayout.HORIZONTAL);row.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
            int start=Math.max(0,initial);
            boolean atMax=start/1440==maximum/1440;
            NumberPicker days=number(c,0,maximum/1440,start/1440),hours=number(c,0,atMax?0:23,(start%1440)/60),minutes=number(c,0,atMax?0:59,start%60);
            days.setOnValueChangedListener((picker,old,n)->{boolean limit=n==maximum/1440;if(limit){hours.setValue(0);minutes.setValue(0);}hours.setMaxValue(limit?0:23);minutes.setMaxValue(limit?0:59);});
            wheel(row,days,"روز قبل");wheel(row,hours,"ساعت قبل");wheel(row,minutes,"دقیقه قبل");
            new MaterialAlertDialogBuilder(c).setTitle("مدت یادآوری").setView(row).setNegativeButton("انصراف",null)
                .setPositiveButton("انتخاب",(dialog,which)->callback.accept(days.getValue()*1440+hours.getValue()*60+minutes.getValue())).show();
        }).show();
    }
}
