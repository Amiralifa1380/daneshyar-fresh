package ir.daneshyar.offline;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import java.util.Locale;
/** Device-local Persian calendar; Android ICU, no network or calendar dependency. */
public final class PersianDates {
    private PersianDates() {
    }
    public static Calendar calendar(long millis) {
        Calendar c = Calendar.getInstance(new ULocale("fa_IR@calendar=persian"));
        c.setTimeInMillis(millis);
        return c;
    }
    public static String date(long millis) {
        Calendar c = calendar(millis);
        return String.format(Locale.US, "%04d/%02d/%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH)+1, c.get(Calendar.DAY_OF_MONTH));
    }
    public static String time(long millis) {
        return new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date(millis));
    }
    public static String ascii(String s) {
        StringBuilder out = new StringBuilder();
        for (char ch : s.trim().toCharArray()) {
            if (ch >= '۰' && ch <= '۹') ch = (char)('0'+ch-'۰');
            else if (ch >= '٠' && ch <= '٩') ch = (char)('0'+ch-'٠');
            if(ch=='٫')ch='.';
            out.append(ch);
        }
        return out.toString();
    }
    public static long parse(String date, String time) {
        String[] d = ascii(date).split("/");
        String[] t = ascii(time).split(":");
        if (d.length != 3 || t.length != 2) throw new IllegalArgumentException("تاریخ مانند ۱۴۰۵/۰۷/۲۲ و ساعت مانند ۰۹:۳۰ باشد");
        int year=Integer.parseInt(d[0]);
        if(year<1200 || year>1600) throw new IllegalArgumentException("سال باید بین ۱۲۰۰ و ۱۶۰۰ باشد");
        Calendar c = calendar(0);
        c.clear();
        c.setLenient(false);
        c.set(year, Integer.parseInt(d[1])-1, Integer.parseInt(d[2]), Integer.parseInt(t[0]), Integer.parseInt(t[1]), 0);
        return c.getTimeInMillis();
    }
    public static int minutes(String s) {
        String[] p=ascii(s).split(":");
        if(p.length!=2) throw new IllegalArgumentException("ساعت را به شکل ۰۹:۳۰ وارد کنید");
        int h=Integer.parseInt(p[0]), m=Integer.parseInt(p[1]);
        if(h<0||h>23||m<0||m>59) throw new IllegalArgumentException("ساعت نامعتبر است");
        return h*60+m;
    }
    public static String clock(int minutes) {
        return String.format(Locale.US,"%02d:%02d",minutes/60,minutes%60);
    }
    public static String digits(String s) {
        StringBuilder b=new StringBuilder();
        for(char c:s.toCharArray()) b.append(c>='0'&&c<='9'?(char)('۰'+c-'0'):c);
        return b.toString();
    }
}
