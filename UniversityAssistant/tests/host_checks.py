#!/usr/bin/env python3
"""Checks the actual SQL schema and executes extracted pure Java methods.
Not an Android build, emulator test, or substitute for on-device tests.
"""
from pathlib import Path
import sqlite3, re, subprocess, os, tempfile, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/ir/daneshyar/offline'
passed = 0

def check(condition, name):
    global passed
    assert condition, name
    passed += 1
    print('PASS:', name)

def rejects(db, sql, args, name):
    try:
        db.execute(sql, args)
    except sqlite3.IntegrityError:
        check(True, name)
    else:
        raise AssertionError(name)

connection = sqlite3.connect(':memory:')
connection.execute('PRAGMA foreign_keys=ON')
for sql in re.findall(r'db\.execSQL\("([^"]+)"\)', (SRC/'Store.java').read_text()):
    connection.execute(sql)
check(len(connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name!='sqlite_sequence'").fetchall()) == 5, 'five tables from actual source schema')
connection.execute('INSERT INTO teachers(name) VALUES(?)', ('استاد',))
connection.execute('INSERT INTO courses(name,teacher_id,units,grade) VALUES(?,?,?,?)', ('ریاضی', 1, 3, 18.5))
connection.execute('INSERT INTO sessions(course_id,day,start,end,remind) VALUES(?,?,?,?,?)', (1, 0, 540, 600, 15))
connection.execute('INSERT INTO events(title,kind,course_id,due,lead) VALUES(?,?,?,?,?)', ('آزمون', 0, 1, 1800000000000, 60))
check(connection.execute('SELECT grade FROM courses').fetchone()[0] == 18.5, 'grade and units persisted')
rejects(connection, 'INSERT INTO courses(name,units,grade) VALUES(?,?,?)', ('نام', 3, 21), 'reject grade outside 0..20')
rejects(connection, 'INSERT INTO courses(name,units) VALUES(?,?)', ('نام', 0), 'reject invalid credits')
rejects(connection, 'INSERT INTO sessions(course_id,day,start,end,remind) VALUES(?,?,?,?,?)', (1, 7, 10, 20, 0), 'reject invalid weekday')
rejects(connection, 'INSERT INTO sessions(course_id,day,start,end,remind) VALUES(?,?,?,?,?)', (1, 1, 600, 540, 0), 'reject reversed class times')
rejects(connection, 'INSERT INTO events(title,kind,course_id,due,lead) VALUES(?,?,?,?,?)', ('نام', 0, 999, 1, 0), 'reject orphan course link')
rejects(connection, 'INSERT INTO events(title,kind,due,lead) VALUES(?,?,?,?)', ('نام', 4, 1, 0), 'reject invalid event kind')
connection.commit()
try:
    with connection:
        connection.execute('DELETE FROM events')
        connection.execute('INSERT INTO events(title,kind,due,lead) VALUES(?,?,?,?)', ('خراب', 0, -1, 60))
except sqlite3.IntegrityError:
    pass
check(connection.execute('SELECT title FROM events').fetchone()[0] == 'آزمون', 'SQLite restores previous data after transaction error')
connection.execute('DELETE FROM teachers WHERE id=?', (1,))
check(connection.execute('SELECT teacher_id FROM courses').fetchone()[0] is None, 'teacher removal preserves course')
connection.execute('DELETE FROM courses WHERE id=?', (1,))
check(connection.execute('SELECT count(*) FROM sessions').fetchone()[0] == 0, 'course removal cascades weekly sessions')
check(connection.execute('SELECT course_id FROM events').fetchone()[0] is None, 'course removal preserves events')
payload = "x'); DROP TABLE events; --"
connection.execute('INSERT INTO teachers(name) VALUES(?)', (payload,))
check(connection.execute('SELECT name FROM teachers').fetchone()[0] == payload, 'parameter binding stores SQL-shaped input safely')
# Exercise the actual additional schema and its upgrade path without dropping old records.
schema = re.findall(r'db\.execSQL\("([^"]+)"\)', (SRC/'Store.java').read_text())
old_schema = [sql for sql in schema if 'CREATE TABLE resources' not in sql and 'CREATE INDEX resources_course' not in sql]
new_schema = [sql for sql in schema if sql not in old_schema]
migration = sqlite3.connect(':memory:')
migration.execute('PRAGMA foreign_keys=ON')
for sql in old_schema: migration.execute(sql)
migration.execute('INSERT INTO courses(name,units) VALUES(?,?)', ('درس قبلی', 2))
for sql in new_schema: migration.execute(sql)
check(migration.execute('SELECT name FROM courses').fetchone()[0]=='درس قبلی', 'resources migration preserves existing courses')
migration.execute('INSERT INTO resources(course_id,title,kind,value,added) VALUES(?,?,?,?,?)', (1,'یادداشت',2,'جزوهٔ درس',1800000000000))
check(migration.execute('SELECT value FROM resources').fetchone()[0]=='جزوهٔ درس', 'per-course note persists')
rejects(migration, 'INSERT INTO resources(course_id,title,kind,value,added) VALUES(?,?,?,?,?)', (999,'عنوان',1,'https://example.test',1), 'reject orphan resource')
rejects(migration, 'INSERT INTO resources(course_id,title,kind,value,size,added) VALUES(?,?,?,?,?,?)', (1,'عنوان',0,'file',26214401,1), 'reject oversized file metadata')
migration.execute('DELETE FROM courses WHERE id=1')
check(migration.execute('SELECT count(*) FROM resources').fetchone()[0]==0, 'course deletion cascades resources')
for file in (ROOT/'app/src/main').rglob('*.xml'):
    ET.parse(file)
check(True, 'all Android resource XML parses')
base_theme = ET.parse(ROOT/'app/src/main/res/values/styles.xml').getroot()
v27_theme = ET.parse(ROOT/'app/src/main/res/values-v27/styles.xml').getroot()
check(not any(i.get('name') == 'android:windowLightNavigationBar' for i in base_theme.iter('item')), 'API 27 navigation property excluded from API 26 defaults')
check(any(i.get('name') == 'android:windowLightNavigationBar' for i in v27_theme.iter('item')), 'API 27 navigation property version-qualified')
manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
permissions = [x.get('{http://schemas.android.com/apk/res/android}name') for x in manifest.findall('uses-permission')]
check('android.permission.INTERNET' not in permissions, 'no INTERNET permission')
check(not re.search(r'HttpURLConnection|Socket\(|URL\([^)]*\)\.openConnection', '\n'.join(p.read_text() for p in SRC.glob('*.java'))), 'no network calls or remote resources in Java sources')

gradle = (ROOT/'app/build.gradle').read_text()
check("applicationId 'ir.daneshyar.offline'" in gradle and 'versionCode 6' in gradle, 'update keeps package ID and increments version')
check('minSdk 26' in gradle, 'update keeps Android 8 support')
check('debuggable false' in gradle and 'ANDROID_SIGNING_FILE' in gradle, 'release disables debugging and uses external signing key')
check('super(c, databaseName, null, 2)' in (SRC/'Store.java').read_text() and 'if(old==1 && next==2)' in (SRC/'Store.java').read_text(), 'database version 2 provides version 1 migration')
check('com.google.android.material:material:1.12.0' in gradle, 'Material components are bundled dependencies')
check('امیرحسین فلاحتی' in (SRC/'MainActivity.java').read_text(), 'author attribution included')

check('android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' in permissions and 'android.permission.SCHEDULE_EXACT_ALARM' in permissions, 'alarm service and exact scheduling permissions declared')
check('android.permission.READ_EXTERNAL_STORAGE' not in permissions and 'android.permission.MANAGE_EXTERNAL_STORAGE' not in permissions, 'no broad storage permission')
service=(SRC/'AlarmService.java').read_text()
check('setLooping(AppPrefs.looping(this))' in service and 'StopAlarmReceiver.class' in service, 'shared looping alarm exposes explicit stop action')
check('setInputType(InputType.TYPE_NULL)' in (SRC/'MainActivity.java').read_text() and 'FOCUS_BLOCK_DESCENDANTS' in (SRC/'Pickers.java').read_text(), 'date/time fields use non-typing selection')
check((ROOT/'app/src/main/res/values-night/colors.xml').exists() and 'Theme.Material3.DayNight' in (ROOT/'app/src/main/res/values/styles.xml').read_text(), 'dark theme resources provided')
check('MAX_TOTAL=300L*1024*1024' in (SRC/'BackupFiles.java').read_text() and 'LocalMaterials.safeName(name.substring(6))' in (SRC/'BackupFiles.java').read_text(), 'ZIP backup bounded and file paths checked')

main = (SRC/'MainActivity.java').read_text()
check('getWindow().getInsetsController()' not in main and 'decor.getWindowInsetsController()' in main, 'startup avoids Window controller before DecorView exists')
check(main.index('setContentView(root);') < main.index('configureSystemBars(decor);'), 'system bar configuration follows content creation')
def method(text, name):
    start = text.index('    public static ' + name) if name.startswith(('String ', 'int ', 'long ', 'boolean ')) else -1
    brace = text.index('{', start)
    depth = 1
    i = brace + 1
    while depth:
        if text[i] == '{': depth += 1
        elif text[i] == '}': depth -= 1
        i += 1
    return text[start:i]

persian = (SRC/'PersianDates.java').read_text()
reminders = (SRC/'Reminders.java').read_text()
assets = (SRC/'LocalMaterials.java').read_text()
methods = '\n'.join(method(persian, n) for n in ['String ascii(', 'int minutes(', 'String clock(', 'String digits('])
java = '''import java.util.*;
class HostLogic {
 static int checks=0;
 static void check(boolean c,String n){if(!c)throw new AssertionError(n);checks++;}
 static class JSONObject {Map<String,Integer> values=new HashMap<>();JSONObject put(String k,int v){values.put(k,v);return this;}int optInt(String k){return values.getOrDefault(k,0);}}
 static class TimeRules { METHODS }
 static class ReminderRules { REMINDER }
 static class AssetRules { SAFENAME }
 public static void main(String[] args){
  String valid="12345678-1234-1234-1234-123456789abc.bin";
  check(AssetRules.safeName(valid),"safe UUID file");
  for(String invalid:List.of("../"+valid,"/"+valid,"files/"+valid,".","..","test.bin",valid+"/more","ABCDEFAB-1234-1234-1234-123456789abc.bin"))check(!AssetRules.safeName(invalid),"reject unsafe backup file name");
  TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tehran"));
  check(TimeRules.minutes("۰۹:۳۰")==570,"Persian numerals");
  check(TimeRules.minutes("٠٩:٣٠")==570,"Arabic numerals");
  check(TimeRules.clock(1439).equals("23:59"),"last minute");
  check(TimeRules.digits("123").equals("۱۲۳"),"localized digits");
  for(String value:List.of("24:00","09:60","-1:00","bad")){boolean bad=false;try{TimeRules.minutes(value);}catch(Exception e){bad=true;}check(bad,"invalid time "+value);}
  Calendar now=Calendar.getInstance();now.set(2026,9,17,8,0,0);now.set(Calendar.MILLISECOND,0);
  JSONObject row=new JSONObject().put("day",0).put("start",540).put("remind",15);
  long next=ReminderRules.nextSession(row,now.getTimeInMillis());
  check(next-now.getTimeInMillis()==45*60000L,"Saturday next reminder");
  check(ReminderRules.nextSession(row,next)==next+7*86400000L,"delivered reminder advances a week");
  row.put("day",1).put("start",10).put("remind",30);
  Calendar expected=(Calendar)now.clone();expected.set(Calendar.HOUR_OF_DAY,23);expected.set(Calendar.MINUTE,40);
  check(ReminderRules.nextSession(row,now.getTimeInMillis())==expected.getTimeInMillis(),"Sunday reminder crosses to Saturday");
  for(int today=0;today<7;today++)for(int target=0;target<7;target++){
   Calendar anchor=Calendar.getInstance();anchor.set(2026,9,17+today,8,0,0);anchor.set(Calendar.MILLISECOND,0);
   row.put("day",target).put("start",540).put("remind",0);
   long actual=ReminderRules.nextSession(row,anchor.getTimeInMillis());
   check(actual-anchor.getTimeInMillis()==((target-today+7)%7)*86400000L+3600000L,"weekday mapping");
  }
  TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
  now=Calendar.getInstance();now.set(2026,2,7,10,0,0);now.set(Calendar.MILLISECOND,0);
  row.put("day",0).put("start",540).put("remind",0);
  next=ReminderRules.nextSession(row,now.getTimeInMillis());Calendar actual=Calendar.getInstance();actual.setTimeInMillis(next);
  check(actual.get(Calendar.HOUR_OF_DAY)==9&&actual.get(Calendar.DAY_OF_MONTH)==14,"DST keeps local class time");
  System.out.println("PASS: "+checks+" Java time/weekly-reminder/file-name assertions from actual source methods");
 }
}
'''.replace('METHODS', methods).replace('REMINDER', method(reminders, 'long nextSession(')).replace('SAFENAME', method(assets, 'boolean safeName('))
with tempfile.TemporaryDirectory() as tmp:
    path=Path(tmp)/'HostLogic.java';path.write_text(java)
    subprocess.run(['java',str(path)],check=True)
subprocess.run(['java',str(ROOT/'tests/ParseJava.java'),*[str(p) for p in (ROOT/'app/src').rglob('*.java')]],check=True)
print(f'PASS: {passed} host schema/XML/security checks. Android build and device tests NOT executed.')
