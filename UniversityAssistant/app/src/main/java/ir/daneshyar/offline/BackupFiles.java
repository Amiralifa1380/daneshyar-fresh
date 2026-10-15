package ir.daneshyar.offline;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Streaming complete backups; bounded ZIP parsing, safe paths and transactional metadata. */
public final class BackupFiles {
    private static final long MAX_JSON=10L*1024*1024,MAX_TOTAL=300L*1024*1024;
    private BackupFiles() {}
    public static void export(Context c,Store db,OutputStream destination) throws Exception {
        JSONObject root=db.backup();root.put("preferences",AppPrefs.backup(c));
        byte[] json=root.toString(2).getBytes(StandardCharsets.UTF_8);
        if(json.length>MAX_JSON)throw new IOException("اطلاعات متنی بیش از ۱۰ مگابایت است");
        Set<String> names=new LinkedHashSet<>();
        JSONArray resources=root.getJSONArray("resources");
        for(int i=0;i<resources.length();i++){JSONObject r=resources.getJSONObject(i);if(r.optInt("kind")==0)names.add(r.optString("value"));}
        String audio=root.getJSONObject("preferences").optString("audio_file","");if(!audio.isEmpty())names.add(audio);
        long total=json.length;
        try(ZipOutputStream zip=new ZipOutputStream(destination)) {
            zip.putNextEntry(new ZipEntry("data.json"));zip.write(json);zip.closeEntry();
            for(String name:names) {
                File file=LocalMaterials.file(c,name);if(!file.isFile())throw new IOException("فایل پشتیبان ناقص است: جزوه یا صدای محلی پیدا نشد");
                total+=file.length();if(total>MAX_TOTAL)throw new IOException("پشتیبان بیش از ۳۰۰ مگابایت است");
                zip.putNextEntry(new ZipEntry("files/"+name));
                try(InputStream in=new FileInputStream(file)){LocalMaterials.copy(in,zip,LocalMaterials.MAX_FILE);}
                zip.closeEntry();
            }
        }
    }
    public static void restore(Context c,Store db,InputStream source) throws Exception {
        BufferedInputStream buffered=new BufferedInputStream(source);buffered.mark(4);
        int a=buffered.read(),b=buffered.read();buffered.reset();
        if(a!='P'||b!='K') {
            ByteArrayOutputStream text=new ByteArrayOutputStream();LocalMaterials.copy(buffered,text,MAX_JSON);
            JSONObject legacy=new JSONObject(text.toString("UTF-8"));
            if(legacy.optInt("version")!=1)throw new IOException("پشتیبان کامل نسخهٔ جدید باید ZIP باشد");
            db.restore(legacy);try{AlarmService.stop(c);LocalMaterials.cleanup(c,db);}catch(RuntimeException cleanup){android.util.Log.w("BackupFiles","Post-restore cleanup failed",cleanup);}return;
        }
        File staging=new File(c.getCacheDir(),"backup-"+UUID.randomUUID());
        if(!staging.mkdirs())throw new IOException("پوشهٔ موقت ایجاد نشد");
        List<File> promoted=new ArrayList<>();boolean committed=false;
        try {
            Set<String> entries=new HashSet<>();Map<String,File> staged=new HashMap<>();JSONObject root=null;long total=0;
            try(ZipInputStream zip=new ZipInputStream(buffered)) {
                ZipEntry entry;int count=0;
                while((entry=zip.getNextEntry())!=null) {
                    if(++count>10005)throw new IOException("تعداد فایل پشتیبان بیش از حد مجاز است");
                    String name=entry.getName();if(entry.isDirectory()||!entries.add(name))throw new IOException("ساختار ZIP نامعتبر است");
                    if(name.equals("data.json")) {
                        ByteArrayOutputStream text=new ByteArrayOutputStream();total+=LocalMaterials.copy(zip,text,MAX_JSON);
                        root=new JSONObject(text.toString("UTF-8"));
                    } else if(name.startsWith("files/")&&LocalMaterials.safeName(name.substring(6))) {
                        String shortName=name.substring(6);File file=new File(staging,shortName);
                        try(OutputStream out=new FileOutputStream(file)){total+=LocalMaterials.copy(zip,out,LocalMaterials.MAX_FILE);}
                        staged.put(shortName,file);
                    } else throw new IOException("مسیر ناشناخته یا ناامن در ZIP");
                    if(total>MAX_TOTAL)throw new IOException("پشتیبان بیش از ۳۰۰ مگابایت است");zip.closeEntry();
                }
            }
            if(root==null||root.optInt("version")!=2)throw new IOException("فایل data.json یا نسخهٔ پشتیبان نامعتبر است");
            JSONObject preferences=root.optJSONObject("preferences");if(preferences!=null)AppPrefs.validate(preferences);
            Map<String,String> renamed=new HashMap<>();
            JSONArray rows=root.getJSONArray("resources");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                if(row.optInt("kind")==0){String name=promote(c,row.getString("value"),staged,renamed,promoted);row.put("value",name);row.put("size",LocalMaterials.file(c,name).length());}
            }
            if(preferences!=null&&!preferences.optString("audio_file","").isEmpty())preferences.put("audio_file",promote(c,preferences.getString("audio_file"),staged,renamed,promoted));
            // All file copies succeed before committing database replacement.
            db.restore(root);
            committed=true;
            if(preferences!=null)AppPrefs.restore(c,preferences);
            try{AlarmService.stop(c);LocalMaterials.cleanup(c,db);}catch(RuntimeException cleanup){android.util.Log.w("BackupFiles","Post-restore cleanup failed",cleanup);}
        } finally {
            if(!committed)for(File file:promoted)file.delete();
            for(File file:promoted)LocalMaterials.finishImport(file.getName());
            if(committed)LocalMaterials.cleanup(c,db);
            File[] files=staging.listFiles();if(files!=null)for(File file:files)file.delete();staging.delete();
        }
    }
    private static String promote(Context c,String old,Map<String,File> staged,Map<String,String> renamed,List<File> promoted) throws IOException {
        if(!LocalMaterials.safeName(old)||!staged.containsKey(old))throw new IOException("فایل جزوه یا صدای پشتیبان ناقص است");
        if(renamed.containsKey(old))return renamed.get(old);
        String name=UUID.randomUUID()+".bin";LocalMaterials.beginImport(name);File target=LocalMaterials.file(c,name);promoted.add(target);
        try(InputStream in=new FileInputStream(staged.get(old));OutputStream out=new FileOutputStream(target)){LocalMaterials.copy(in,out,LocalMaterials.MAX_FILE);}
        renamed.put(old,name);return name;
    }
}
