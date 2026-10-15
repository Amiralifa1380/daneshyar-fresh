package ir.daneshyar.offline;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import androidx.appcompat.app.AppCompatDelegate;

/** Device-only appearance and shared alarm preferences. */
public final class AppPrefs {
    private AppPrefs() {}
    public static SharedPreferences get(Context c) {return c.getSharedPreferences("daneshyar_preferences", Context.MODE_PRIVATE);}
    public static int theme(Context c) {return get(c).getInt("theme",0);}
    public static int accent(Context c) {return get(c).getInt("accent",0);}
    public static void applyTheme(Context c) {
        int choice=theme(c);
        AppCompatDelegate.setDefaultNightMode(choice==1?AppCompatDelegate.MODE_NIGHT_NO:choice==2?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    public static boolean looping(Context c) {return get(c).getBoolean("loop",true);}
    public static void active(Context c,boolean active) {get(c).edit().putBoolean("alarm_active",active).apply();}
    public static boolean active(Context c) {return get(c).getBoolean("alarm_active",false);}
    public static JSONObject backup(Context c) throws Exception {
        JSONObject o=new JSONObject();
        o.put("theme",theme(c));o.put("accent",accent(c));o.put("loop",looping(c));
        o.put("audio_file",get(c).getString("audio_file",""));
        o.put("sound_uri",get(c).getString("sound_uri",""));
        o.put("sound_label",get(c).getString("sound_label","صدای پیش‌فرض گوشی"));
        return o;
    }
    public static void validate(JSONObject o) {
        if(o.optInt("theme",-1)<0||o.optInt("theme")>2||o.optInt("accent",-1)<0||o.optInt("accent")>2)throw new IllegalArgumentException("تنظیم تم نامعتبر است");
        if(!(o.opt("loop") instanceof Boolean))throw new IllegalArgumentException("تنظیم صدا نامعتبر است");
        String file=o.optString("audio_file","");
        if(!file.isEmpty()&&!LocalMaterials.safeName(file))throw new IllegalArgumentException("فایل صدا نامعتبر است");
        String uri=o.optString("sound_uri","");
        if(!uri.isEmpty()&&!uri.startsWith("content://")&&!uri.startsWith("android.resource://"))throw new IllegalArgumentException("نشانی صدا نامعتبر است");
        if(uri.length()>4000||o.optString("sound_label","").length()>4000)throw new IllegalArgumentException("تنظیمات بیش از حد طولانی است");
    }
    public static void restore(Context c,JSONObject o) {
        validate(o);
        get(c).edit().putInt("theme",o.optInt("theme")).putInt("accent",o.optInt("accent")).putBoolean("loop",o.optBoolean("loop",true))
            .putString("audio_file",o.optString("audio_file","")).putString("sound_uri",o.optString("sound_uri",""))
            .putString("sound_label",o.optString("sound_label","صدای پیش‌فرض گوشی")).apply();
    }
}
