package ir.daneshyar.offline;
import android.app.Instrumentation;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.TextView;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONObject;
import java.util.Calendar;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.*;
import android.database.sqlite.SQLiteDatabase;
import android.content.Context;
import android.content.ContextWrapper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
/** Dependency-free on-device integration tests. Never run against the user's database. */
public final class OfflineTests extends Instrumentation {
    private static final String TEST_DB = "daneshyar-instrumentation-test.db";
    private int checks = 0;
    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private Bundle status() {
        Bundle bundle = new Bundle();
        bundle.putString("id", "DaneshyarOfflineRunner");
        bundle.putString("class", getClass().getName());
        bundle.putString("test", "testOfflineIntegration");
        bundle.putInt("numtests", 1);
        bundle.putInt("current", 1);
        return bundle;
    }
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        JSONObject originalAlarmPrefs=null;
        // Announce the suite before setup: startup failures must be visible as test failures.
        sendStatus(1, status());
        android.content.Context testContext = getTargetContext();
        try {
            testContext.deleteDatabase(TEST_DB);
            try(Store db=new Store(testContext, TEST_DB)) {
                ContentValues teacher=new ContentValues();
                teacher.put("name","استاد آزمون");
                long t=db.save("teachers",0,teacher);
                ContentValues course=new ContentValues();
                course.put("name","ریاضی");
                course.put("teacher_id",t);
                course.put("units",3);
                course.put("grade",18.5);
                long c=db.save("courses",0,course);
                check(db.get("courses",c).optDouble("grade")==18.5,"grade persistence");
                ContentValues session=new ContentValues();
                session.put("course_id",c);
                session.put("day",0);
                session.put("start",540);
                session.put("end",600);
                session.put("remind",15);
                long s=db.save("sessions",0,session);
                ContentValues event=new ContentValues();
                event.put("title","امتحان");
                event.put("kind",0);
                event.put("course_id",c);
                event.put("due",System.currentTimeMillis()+3600000L);
                event.put("lead",15);
                long e=db.save("events",0,event);
                JSONObject backup=db.backup();
                check(backup.getJSONArray("events").length()==1,"backup contains event");
                event.put("title","ویرایش");
                db.save("events",e,event);
                check(db.get("events",e).optString("title").equals("ویرایش"),"update");
                db.restore(backup);
                check(db.get("events",e).optString("title").equals("امتحان"),"restore original");
                JSONObject bad=new JSONObject(backup.toString());
                bad.getJSONArray("courses").getJSONObject(0).put("units",99);
                boolean rejected=false;
                try {
                    db.restore(bad);
                }
                catch(Exception expected) {
                    rejected=true;
                }
                check(rejected&&db.get("courses",c).optInt("units")==3,"invalid restore rollback");
                db.delete("teachers",t);
                check(db.get("courses",c).isNull("teacher_id"),"teacher nullifies link");
                db.delete("courses",c);
                check(db.all("sessions").isEmpty(),"course cascades sessions");
                check(db.get("events",e).isNull("course_id"),"course preserves event");
                db.restore(backup);
                check(db.all("teachers").size()==1,"restore teacher");
                Context isolated=isolatedFiles(testContext);
                String filename=UUID.randomUUID()+".bin";
                byte[] lecture="جزوهٔ آفلاین".getBytes(StandardCharsets.UTF_8);
                try(OutputStream out=new FileOutputStream(LocalMaterials.file(isolated,filename))){out.write(lecture);}
                ContentValues file=new ContentValues();file.put("course_id",c);file.put("title","جزوه");file.put("kind",0);file.put("value",filename);file.put("mime","text/plain");file.put("size",lecture.length);file.put("added",System.currentTimeMillis());
                long fileId=db.save("resources",0,file);
                ContentValues note=new ContentValues();note.put("course_id",c);note.put("title","یادداشت");note.put("kind",2);note.put("value","درس اول");note.put("added",System.currentTimeMillis());
                db.save("resources",0,note);
                check(db.all("resources").size()==2,"per-course file and note");
                check(LocalMaterials.validLink("https://t.me/course_group"),"course group URL accepted");
                check(!LocalMaterials.validLink("javascript:alert(1)")&&!LocalMaterials.validLink("file:///etc/passwd"),"unsafe link schemes rejected");
                ByteArrayOutputStream packed=new ByteArrayOutputStream();BackupFiles.export(isolated,db,packed);
                db.delete("resources",fileId);
                BackupFiles.restore(isolated,db,new ByteArrayInputStream(packed.toByteArray()));
                check(db.all("resources").size()==2,"ZIP backup restores resource metadata");
                String restoredName=db.get("resources",fileId).optString("value");
                check(!filename.equals(restoredName),"restored file uses new safe identifier");
                byte[] restored;
                try(InputStream in=new FileInputStream(LocalMaterials.file(isolated,restoredName))){ByteArrayOutputStream out=new ByteArrayOutputStream();LocalMaterials.copy(in,out,LocalMaterials.MAX_FILE);restored=out.toByteArray();}
                check(java.util.Arrays.equals(lecture,restored),"ZIP backup restores file bytes");
                ByteArrayOutputStream incomplete=new ByteArrayOutputStream();
                try(ZipOutputStream zip=new ZipOutputStream(incomplete)){zip.putNextEntry(new ZipEntry("data.json"));zip.write(db.backup().toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
                boolean incompleteRejected=false;
                try{BackupFiles.restore(isolated,db,new ByteArrayInputStream(incomplete.toByteArray()));}catch(Exception expected){incompleteRejected=true;}
                check(incompleteRejected&&db.all("resources").size()==2&&LocalMaterials.file(isolated,restoredName).isFile(),"incomplete ZIP preserves prior data and file");
                JSONObject legacy=new JSONObject(backup.toString());legacy.put("version",1);legacy.remove("resources");
                BackupFiles.restore(isolated,db,new ByteArrayInputStream(legacy.toString().getBytes(StandardCharsets.UTF_8)));
                check(db.get("courses",c)!=null&&db.all("resources").isEmpty(),"version 1 JSON backup remains readable");
                JSONObject row=db.get("sessions",s);
                Calendar now=Calendar.getInstance();
                now.set(2026,Calendar.OCTOBER,17,8,0,0);
                now.set(Calendar.MILLISECOND,0);
                long next=Reminders.nextSession(row,now.getTimeInMillis());
                check(next-now.getTimeInMillis()==45*60000L,"Saturday reminder");
                check(Reminders.nextSession(row,next)==next+7*86400000L,"next week after delivered");
            }
            testMigration(testContext);
            check(PersianDates.minutes("۰۹:۳۰")==570,"Persian digit time");
            check(PersianDates.date(PersianDates.parse("۱۴۰۵/۰۷/۲۲","۱۳:۲۱")).equals("1405/07/22"),"Persian date round trip");
            Calendar date=Calendar.getInstance();
            date.setTimeInMillis(PersianDates.parse("1405/07/22","13:21"));
            check(date.get(Calendar.YEAR)==2026&&date.get(Calendar.MONTH)==9&&date.get(Calendar.DAY_OF_MONTH)==14,"known Gregorian mapping");
            boolean rejected=false;
            try {
                PersianDates.parse("1405/07/31","09:00");
            }
            catch(Exception expected) {
                rejected=true;
            }
            check(rejected,"invalid Persian date rejected");
            rejected=false;
            try {
                PersianDates.parse("1405/01/01","24:00");
            }
            catch(Exception expected) {
                rejected=true;
            }
            check(rejected,"invalid time rejected");
            Intent launch=new Intent(getTargetContext(),MainActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);
            waitForIdleSync();
            runOnMainSync(()-> {
                check(containsText(activity.getWindow().getDecorView(),"دانش‌یار"),"launch Persian UI");
                MainActivity screen=(MainActivity)activity;
                android.widget.LinearLayout form=screen.form();
                android.widget.EditText dateField=screen.dateField(form,System.currentTimeMillis());
                android.widget.EditText timeField=screen.timeField(form,"انتخاب ساعت",570);
                android.widget.EditText reminder=screen.durationField(form,15,1440);
                check(!dateField.isFocusable(),"date field selected without typing");
                check(!timeField.isFocusable()&&PersianDates.minutes(timeField.getText().toString())==570,"time field selected without typing");
                check(!reminder.isFocusable()&&(Integer)reminder.getTag()==15,"reminder duration selected without typing");
                check(findNavigation(activity.getWindow().getDecorView()) != null, "Material navigation exists");
                findNavigation(activity.getWindow().getDecorView()).setSelectedItemId(101);
                check(containsText(activity.getWindow().getDecorView(), "هفتهٔ تو"), "weekly page renders");
                findNavigation(activity.getWindow().getDecorView()).setSelectedItemId(102);
                check(containsText(activity.getWindow().getDecorView(), "کارها و مهلت‌ها"), "event page renders");
                findNavigation(activity.getWindow().getDecorView()).setSelectedItemId(103);
                check(containsText(activity.getWindow().getDecorView(), "دروس و کارنامه"), "course page renders");
                findNavigation(activity.getWindow().getDecorView()).setSelectedItemId(104);
                check(containsText(activity.getWindow().getDecorView(), "تقویم دانشگاهی"), "calendar page renders");
                findSettings(activity.getWindow().getDecorView()).performClick();
                check(containsText(activity.getWindow().getDecorView(), "نویسنده: امیرحسین فلاحتی"), "author attribution");
            }
            );
            originalAlarmPrefs=AppPrefs.backup(testContext);
            AppPrefs.get(testContext).edit().putString("audio_file","").putString("sound_uri","android.resource://ir.daneshyar.offline/0").putBoolean("loop",true).commit();
            check(AlarmService.start(testContext,"آزمون کنترل هشدار"),"foreground alarm service starts while app visible");
            waitForIdleSync();
            long deadline=android.os.SystemClock.elapsedRealtime()+10000;
            while(!AlarmService.playbackStarted&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            check(AlarmService.playbackStarted,"alarm audio actually starts despite unavailable selected sound");
            Thread.sleep(1800); // Beyond the 1.5-second bundled clip: looping must still be active.
            check(AlarmService.playbackStarted,"alarm playback loops beyond one clip");
            check(AppPrefs.active(testContext),"alarm remains active pending explicit stop");
            AlarmService.stop(testContext);waitForIdleSync();
            long stopDeadline=android.os.SystemClock.elapsedRealtime()+5000;
            while(AlarmService.playbackStarted&&android.os.SystemClock.elapsedRealtime()<stopDeadline)Thread.sleep(50);
            check(!AlarmService.playbackStarted,"explicit stop releases playback");
            check(!AppPrefs.active(testContext),"explicit stop clears active alarm");
            runOnMainSync(activity::finish);
            result.putString("stream", "PASS: " + checks + " on-device checks\n");
            sendStatus(0, status());
        }
        catch (Throwable error) {
            String stack = android.util.Log.getStackTraceString(error);
            android.util.Log.e("DaneshyarTests", "Integration test failed", error);
            Bundle failure = status();
            failure.putString("stack", stack);
            sendStatus(-2, failure);
            result.putString("stream", "FAIL after " + checks + " checks:\n" + stack);
        }
        finally {
            try {
                AlarmService.stop(testContext);
                if(originalAlarmPrefs!=null)AppPrefs.restore(testContext,originalAlarmPrefs);
                testContext.deleteDatabase(TEST_DB);
                testContext.deleteDatabase("daneshyar-migration-test.db");
                clearTree(new File(testContext.getCacheDir(),"offline-test-files"));
            } catch (Exception cleanupError) {
                android.util.Log.e("DaneshyarTests", "Test database cleanup failed", cleanupError);
            }
        }
        // Runner completed; individual test status determines success/failure in Gradle.
        finish(Activity.RESULT_OK, result);
    }
    private Context isolatedFiles(Context base) {
        File root=new File(base.getCacheDir(),"offline-test-files");root.mkdirs();
        return new ContextWrapper(base){
            @Override public File getFilesDir(){File files=new File(root,"files");files.mkdirs();return files;}
            @Override public File getCacheDir(){File cache=new File(root,"cache");cache.mkdirs();return cache;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return base.getSharedPreferences("offline-tests_"+name,mode);}
        };
    }
    private void clearTree(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)clearTree(child);}file.delete();}
    private void testMigration(Context context) {
        String name="daneshyar-migration-test.db";context.deleteDatabase(name);
        try(SQLiteDatabase old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null);Store schema=new Store(context,name)){
            schema.onCreate(old);old.execSQL("DROP TABLE resources");old.setVersion(1);
            old.execSQL("INSERT INTO courses(name,units) VALUES('درس قبلی',2)");
        }
        try(Store upgraded=new Store(context,name)){
            check(upgraded.all("courses").size()==1,"migration retains version 1 course");
            check(upgraded.all("resources").isEmpty(),"migration creates resource table");
        }
        context.deleteDatabase(name);
    }
    private BottomNavigationView findNavigation(View view) {
        if (view instanceof BottomNavigationView) return (BottomNavigationView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                BottomNavigationView found = findNavigation(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private View findSettings(View view) {
        if ("تنظیمات و دربارهٔ برنامه".contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findSettings(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private boolean containsText(View view,String text) {
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)if(containsText(group.getChildAt(i),text))return true;
        }
        return false;
    }
}
