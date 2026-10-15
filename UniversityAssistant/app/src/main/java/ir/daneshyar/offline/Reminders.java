package ir.daneshyar.offline;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
/** One scheduled PendingIntent avoids stale alarms after edits/deletes/imports. */
public final class Reminders {
    public static final String CHANNEL="academic-v2";
    private Reminders() {
    }
    public static void channel(Context c) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"یادآوری دانشگاه",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null,null);
        nm.createNotificationChannel(channel);
    }
    private static PendingIntent pending(Context c,String title,long at) {
        Intent i=new Intent(c,ReminderReceiver.class);
        i.putExtra("title",title);
        i.putExtra("at",at);
        return PendingIntent.getBroadcast(c,1,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static long nextSession(JSONObject row,long now) {
        Calendar cal=Calendar.getInstance();
        cal.setTimeInMillis(now);
        int today=cal.get(Calendar.DAY_OF_WEEK)%7;
        cal.add(Calendar.DAY_OF_MONTH,(row.optInt("day")-today+7)%7);
        cal.set(Calendar.HOUR_OF_DAY,row.optInt("start")/60);
        cal.set(Calendar.MINUTE,row.optInt("start")%60);
        cal.set(Calendar.SECOND,0);
        cal.set(Calendar.MILLISECOND,0);
        cal.add(Calendar.MINUTE,-row.optInt("remind"));
        if(cal.getTimeInMillis()<=now)cal.add(Calendar.DAY_OF_MONTH,7);
        return cal.getTimeInMillis();
    }
    public static void schedule(Context c) {
        long now=System.currentTimeMillis(), next=Long.MAX_VALUE;
        String title="";
        try(Store db=new Store(c)) {
            for(JSONObject e:db.all("events")) {
                if(e.optInt("done")==1||e.optInt("lead")<0)continue;
                long at=e.optLong("due")-e.optInt("lead")*60000L;
                if(at>now&&at<next) {
                    next=at;
                    title=e.optString("title");
                }
            }
            Map<Long,String> names=new HashMap<>();
            for(JSONObject course:db.all("courses"))names.put(course.optLong("id"),course.optString("name"));
            for(JSONObject s:db.all("sessions")) {
                if(s.optInt("remind")<0)continue;
                long at=nextSession(s,now);
                if(at<next) {
                    next=at;
                    title="کلاس "+names.getOrDefault(s.optLong("course_id"),"")+" · "+s.optString("room");
                }
            }
        }
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p=pending(c,title,next);
        am.cancel(p);
        if(next!=Long.MAX_VALUE) {
            try{if(Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,p);}catch(SecurityException revoked){am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,p);}
        }
    }
    /** Dispatch all items sharing the scheduled instant, including delivery delayed by Doze. */
    public static void deliver(Context c,long from,long now) {
        if(from<=0 || from>now)return;
        try(Store db=new Store(c)) {
            for(JSONObject e:db.all("events")) {
                if(e.optInt("done")==1 || e.optInt("lead")<0)continue;
                long at=e.optLong("due")-e.optInt("lead")*60000L;
                if(at>=from && at<=now)notify(c,e.optString("title"),(int)e.optLong("id"));
            }
            Map<Long,String> names=new HashMap<>();
            for(JSONObject course:db.all("courses"))names.put(course.optLong("id"),course.optString("name"));
            for(JSONObject s:db.all("sessions")) {
                if(s.optInt("remind")<0)continue;
                long at=nextSession(s,from-1);
                if(at<=now) {
                    notify(c,"کلاس "+names.getOrDefault(s.optLong("course_id"),"")+" · "+s.optString("room"),100000000+(int)s.optLong("id"));
                }
            }
        }
    }
    public static void notify(Context c,String title,int id) {
        channel(c);
        Intent intent=new Intent(c,MainActivity.class);
        PendingIntent open=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=manager.getNotificationChannel(CHANNEL);
        if(!manager.areNotificationsEnabled()||channel==null||channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return;
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(ir.daneshyar.offline.R.drawable.ic_school).setContentTitle("دانش‌یار • یادآوری").setContentText(title).setStyle(new Notification.BigTextStyle().bigText(title)).setContentIntent(open).setAutoCancel(true).build();
        try {
            manager.notify(id,n);
            if(!AlarmService.start(c,title)) {
                Intent retry=new Intent(c,MainActivity.class).putExtra("pending_alarm_title",title);
                PendingIntent tap=PendingIntent.getActivity(c,73,retry,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                Notification fallback=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_school).setContentTitle("یادآوری دانش‌یار")
                    .setContentText(title+" · برای پخش صدا لمس کنید").setContentIntent(tap).setAutoCancel(true).build();
                manager.notify(id,fallback);
            }
        }
        catch(SecurityException ignored) {
        }
    }
}
