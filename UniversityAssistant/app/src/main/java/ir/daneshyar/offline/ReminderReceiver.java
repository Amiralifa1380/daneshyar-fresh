package ir.daneshyar.offline;
import android.content.*;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        Reminders.deliver(c,i.getLongExtra("at",0),System.currentTimeMillis());
        Reminders.schedule(c);
    }
}
