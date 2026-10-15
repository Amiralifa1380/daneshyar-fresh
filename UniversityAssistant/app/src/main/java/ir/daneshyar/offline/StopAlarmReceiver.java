package ir.daneshyar.offline;
import android.content.*;
public final class StopAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){AlarmService.stop(context);}
}
