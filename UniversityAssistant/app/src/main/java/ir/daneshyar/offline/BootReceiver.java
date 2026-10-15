package ir.daneshyar.offline;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        Reminders.schedule(c);
    }
}
