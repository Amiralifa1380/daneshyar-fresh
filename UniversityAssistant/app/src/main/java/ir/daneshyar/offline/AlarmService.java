package ir.daneshyar.offline;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.File;

/** User-visible playback service. One shared looping sound, stopped explicitly by user. */
public final class AlarmService extends Service {
    private static final int NOTIFICATION=900000001;
    private static final String CHANNEL="continuous-alarm-v1";
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean hasFocus,prepared;
    private int soundStage;
    static volatile boolean playbackStarted;
    private String title="یادآوری دانشگاه";
    public static boolean start(Context c,String title) {
        Intent intent=new Intent(c,AlarmService.class).putExtra("title",title);
        try{c.startForegroundService(intent);return true;}catch(RuntimeException unavailable){return false;}
    }
    public static void stop(Context c) {
        AppPrefs.active(c,false);
        c.stopService(new Intent(c,AlarmService.class));
    }
    @Override public void onCreate() {
        super.onCreate();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,"هشدار صوتی فعال",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null,null);channel.enableVibration(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null&&!AppPrefs.active(this)){stopSelf();return START_NOT_STICKY;}
        if(intent!=null)title=intent.getStringExtra("title")==null?title:intent.getStringExtra("title");
        Notification notice=notification();
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION,notice);
        AppPrefs.active(this,true);
        if(player==null)begin(0);
        return START_STICKY;
    }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class).putExtra("show_alarm_controls",true);
        PendingIntent view=PendingIntent.getActivity(this,71,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getBroadcast(this,72,new Intent(this,StopAlarmReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_school).setContentTitle("هشدار دانش‌یار فعال است")
            .setContentText(title).setStyle(new Notification.BigTextStyle().bigText(title+"\nبرای قطع صدا، توقف را بزنید."))
            .setContentIntent(view).addAction(new Notification.Action.Builder(null,"توقف صدا",stop).build())
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void begin(int stage) {
        soundStage=stage;
        playbackStarted=false;
        AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        if(focus==null){
            focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change->{
                    hasFocus=change==AudioManager.AUDIOFOCUS_GAIN;
                    if(player!=null&&prepared){if(hasFocus){try{player.start();playbackStarted=true;}catch(IllegalStateException ignored){}}else if(player.isPlaying()){player.pause();playbackStarted=false;}}
                },new Handler(Looper.getMainLooper())).build();
            hasFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        player=new MediaPlayer();player.setAudioAttributes(attributes);player.setLooping(AppPrefs.looping(this));
        player.setWakeMode(this,PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(media->{if(player!=media)return;prepared=true;if(hasFocus)try{media.start();playbackStarted=true;}catch(IllegalStateException failed){failOrFallback();}});
        player.setOnCompletionListener(media->stopSelf());
        player.setOnErrorListener((media,what,extra)->{if(player==media)failOrFallback();return true;});
        try {
            String local=AppPrefs.get(this).getString("audio_file","");
            String selected=AppPrefs.get(this).getString("sound_uri","");
            if(stage==2) {
                try(android.content.res.AssetFileDescriptor asset=getResources().openRawResourceFd(R.raw.alarm_fallback)) {
                    player.setDataSource(asset.getFileDescriptor(),asset.getStartOffset(),asset.getLength());
                }
            }
            else if(stage==0&&!local.isEmpty()) {
                try(java.io.FileInputStream input=new java.io.FileInputStream(LocalMaterials.file(this,local))) {
                    player.setDataSource(input.getFD());
                }
            }
            else {
                Uri uri=stage==1||selected.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM):Uri.parse(selected);
                if(uri==null)uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if(uri==null)throw new java.io.IOException("System alarm sound unavailable");
                player.setDataSource(this,uri);
            }
            player.prepareAsync();
        }catch(Exception error){failOrFallback();}
    }
    private void failOrFallback() {
        if(player!=null){player.release();player=null;}prepared=false;
        playbackStarted=false;
        if(soundStage<2)begin(soundStage+1);else {
            android.util.Log.e("DaneshyarAlarm","All alarm sound sources failed");
            stopSelf();
        }
    }
    @Override public void onDestroy() {
        playbackStarted=false;
        if(player!=null){player.release();player=null;}
        if(audio!=null&&focus!=null)audio.abandonAudioFocusRequest(focus);
        AppPrefs.active(this,false);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
