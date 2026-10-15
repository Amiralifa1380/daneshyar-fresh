package ir.daneshyar.offline;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Copies selected files into private storage; no upload or external file-path access. */
public final class LocalMaterials {
    public static final long MAX_FILE=25L*1024*1024;
    private LocalMaterials() {}
    private static final Set<String> PENDING=new HashSet<>();
    public static synchronized void beginImport(String name){PENDING.add(name);}
    public static synchronized void finishImport(String name){PENDING.remove(name);}
    public static File directory(Context c) {
        File folder=new File(c.getFilesDir(),"materials");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IllegalStateException("پوشهٔ فایل‌ها ایجاد نشد");return folder;
    }
    public static boolean safeName(String name) {return name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.bin");}
    public static File file(Context c,String name) {
        if(!safeName(name))throw new IllegalArgumentException("نام فایل نامعتبر است");return new File(directory(c),name);
    }
    public static boolean validLink(String value) {
        Uri uri=Uri.parse(value);String scheme=uri.getScheme();
        return ("https".equals(scheme)||"http".equals(scheme))&&uri.getHost()!=null&&!uri.getHost().isEmpty()&&!value.contains("\n")&&!value.contains("\r");
    }
    public static String displayName(Context c,Uri uri) {
        try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){String n=cursor.getString(0);if(n!=null&&!n.trim().isEmpty())return n.substring(0,Math.min(200,n.length()));}
        }catch(RuntimeException ignored){}
        return "فایل درسی";
    }
    public static String copySelected(Context c,Uri uri) throws IOException {
        String name=UUID.randomUUID()+".bin";beginImport(name);File file=file(c,name);
        try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)) {
            if(in==null)throw new IOException("فایل باز نشد");copy(in,out,MAX_FILE);
        }catch(Exception error){file.delete();finishImport(name);throw error;}return name;
    }
    public static long copy(InputStream in,OutputStream out,long maximum) throws IOException {
        long count=0;byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){count+=n;if(count>maximum)throw new IOException("فایل یا پشتیبان بیش از حد مجاز است");out.write(buffer,0,n);}return count;
    }
    public static void openFile(Context c,JSONObject resource) {
        File file=file(c,resource.optString("value"));if(!file.isFile())throw new IllegalArgumentException("فایل محلی پیدا نشد؛ نسخهٔ پشتیبان کامل را بازیابی کنید");
        Uri uri=FileProvider.getUriForFile(c,c.getPackageName()+".materials",file,resource.optString("title","فایل درسی"));
        String mime=resource.optString("mime","application/octet-stream");if(mime.isEmpty())mime="application/octet-stream";
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("جزوه",uri));c.startActivity(Intent.createChooser(intent,"بازکردن فایل درسی"));
    }
    public static synchronized void cleanup(Context c,Store db) {
        try {
        Set<String> live=new HashSet<>(PENDING);for(JSONObject row:db.all("resources"))if(row.optInt("kind")==0)live.add(row.optString("value"));
        String audio=AppPrefs.get(c).getString("audio_file","");if(!audio.isEmpty())live.add(audio);
        File[] files=directory(c).listFiles();if(files!=null)for(File f:files)if(safeName(f.getName())&&!live.contains(f.getName()))f.delete();
        }catch(RuntimeException failed){android.util.Log.w("LocalMaterials","Cleanup deferred",failed);}
    }
}
