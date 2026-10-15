package ir.daneshyar.offline;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
/** The only persistence boundary. SQL identifiers are allowlisted; values are bound. */
public final class Store extends SQLiteOpenHelper {
    public static final String[] TABLES= {
        "teachers","courses","sessions","events","resources"
    };
    private static final String[][] COLUMNS= {
 {
            "id","name","contact","office"
        },
 {
            "id","name","teacher_id","units","grade"
        },
 {
            "id","course_id","day","start","end","room","remind"
        },
 {
            "id","title","kind","course_id","due","lead","done","notes"
        },
        {"id","course_id","title","kind","value","mime","size","added"}
    };
    public Store(Context c) {
        this(c, "daneshyar.db");
    }
    /** Isolated database for instrumentation; production always uses daneshyar.db. */
    Store(Context c, String databaseName) {
        super(c, databaseName, null, 2);
    }
    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE teachers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL CHECK(length(name)>0),contact TEXT NOT NULL DEFAULT '',office TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE courses(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL CHECK(length(name)>0),teacher_id INTEGER REFERENCES teachers(id) ON DELETE SET NULL,units INTEGER NOT NULL CHECK(units BETWEEN 1 AND 12),grade REAL CHECK(grade BETWEEN 0 AND 20))");
        db.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,day INTEGER NOT NULL CHECK(day BETWEEN 0 AND 6),start INTEGER NOT NULL CHECK(start BETWEEN 0 AND 1439),end INTEGER NOT NULL CHECK(end>start AND end<=1439),room TEXT NOT NULL DEFAULT '',remind INTEGER NOT NULL CHECK(remind BETWEEN -1 AND 1440))");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL CHECK(length(title)>0),kind INTEGER NOT NULL CHECK(kind BETWEEN 0 AND 3),course_id INTEGER REFERENCES courses(id) ON DELETE SET NULL,due INTEGER NOT NULL CHECK(due>0),lead INTEGER NOT NULL CHECK(lead BETWEEN -1 AND 10080),done INTEGER NOT NULL DEFAULT 0 CHECK(done IN(0,1)),notes TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX events_due ON events(due)");
        db.execSQL("CREATE INDEX sessions_day ON sessions(day,start)");
        createResources(db);
    }
    private void createResources(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE resources(id INTEGER PRIMARY KEY AUTOINCREMENT,course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,title TEXT NOT NULL CHECK(length(title)>0),kind INTEGER NOT NULL CHECK(kind BETWEEN 0 AND 2),value TEXT NOT NULL CHECK(length(value)>0),mime TEXT NOT NULL DEFAULT '',size INTEGER NOT NULL DEFAULT 0 CHECK(size>=0 AND size<=26214400),added INTEGER NOT NULL CHECK(added>0))");
        db.execSQL("CREATE INDEX resources_course ON resources(course_id,added)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) {
        if(old==1 && next==2) {createResources(db);return;}
        throw new IllegalStateException("Unsupported database migration");
    }
    private static int tableIndex(String table) {
        for(int i=0;i<TABLES.length;i++) if(TABLES[i].equals(table))return i;
        throw new IllegalArgumentException("Unknown table");
    }
    public List<JSONObject> all(String table) {
        int idx=tableIndex(table);
        List<JSONObject> result=new ArrayList<>();
        String order=idx==2?"day,start":idx==3?"due":idx==4?"added DESC":"name";
        try(Cursor c=getReadableDatabase().query(table,COLUMNS[idx],null,null,null,null,order)) {
            while(c.moveToNext()) {
                JSONObject row=new JSONObject();
                for(int i=0;i<c.getColumnCount();i++) try {
                    row.put(c.getColumnName(i), c.isNull(i)?JSONObject.NULL:c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getType(i)==Cursor.FIELD_TYPE_FLOAT?c.getDouble(i):c.getString(i));
                }
                catch(Exception e) {
                    throw new IllegalStateException(e);
                }
                result.add(row);
            }
        }
        return result;
    }
    public JSONObject get(String table,long id) {
        for(JSONObject r:all(table))if(r.optLong("id")==id)return r;
        return null;
    }
    public long save(String table,long id,ContentValues values) {
        tableIndex(table);
        for(String key:values.keySet()) {
            boolean found=false;
            for(String col:COLUMNS[tableIndex(table)])if(col.equals(key)&&!key.equals("id"))found=true;
            if(!found)throw new IllegalArgumentException("Unknown field");
        }
        if(id==0)return getWritableDatabase().insertOrThrow(table,null,values);
        if(getWritableDatabase().update(table,values,"id=?",new String[] {
            Long.toString(id)
        }
        )!=1)throw new IllegalArgumentException("اطلاعات پیدا نشد");
        return id;
    }
    public void delete(String table,long id) {
        tableIndex(table);
        getWritableDatabase().delete(table,"id=?",new String[] {
            Long.toString(id)
        }
        );
    }
    public JSONObject backup() throws Exception {
        JSONObject root=new JSONObject();
        root.put("format","daneshyar-offline");
        root.put("version",2);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            for(String t:TABLES)root.put(t,new JSONArray(all(t)));
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
        return root;
    }
    /** Validate structure first, then constraints/FKs inside one rollback-safe transaction. */
    public void restore(JSONObject root) throws Exception {
        int version=root.optInt("version");
        if(!root.optString("format").equals("daneshyar-offline")||(version!=1&&version!=2))throw new IllegalArgumentException("فرمت یا نسخه پشتیبان پشتیبانی نمی‌شود");
        if(version==1)root.put("resources",new JSONArray());
        for(int i=0;i<TABLES.length;i++) {
            JSONArray rows=root.getJSONArray(TABLES[i]);
            if(rows.length()>10000)throw new IllegalArgumentException("تعداد رکورد بیش از حد مجاز است");
            for(int j=0;j<rows.length();j++) {
                JSONObject row=rows.getJSONObject(j);
                if(row.length()!=COLUMNS[i].length)throw new IllegalArgumentException("ساختار رکورد نامعتبر است");
                for(String col:COLUMNS[i])if(!row.has(col))throw new IllegalArgumentException("فیلد ناقص است");
                if(row.getLong("id")<=0||row.getLong("id")>100000000)throw new IllegalArgumentException("شناسه نامعتبر است");
                for(String col:COLUMNS[i]) {
                    Object v=row.get(col);
                    boolean string=col.equals("name")||col.equals("contact")||col.equals("office")||col.equals("room")||col.equals("title")||col.equals("notes")||col.equals("value")||col.equals("mime");
                    boolean nullable=col.equals("teacher_id")||col.equals("grade")||(col.equals("course_id")&&i==3);
                    if(v==JSONObject.NULL) {
                        if(!nullable)throw new IllegalArgumentException("فیلد اجباری خالی است");
                    }
                    else if(string) {
                        if(!(v instanceof String)||((String)v).length()>4000)throw new IllegalArgumentException("متن نامعتبر است");
                    }
                    else if(!(v instanceof Number)||(!col.equals("grade")&&((Number)v).doubleValue()!=((Number)v).longValue()))throw new IllegalArgumentException("عدد نامعتبر است");
                }
                if(i==4) {
                    int kind=row.getInt("kind");String value=row.getString("value");
                    if(kind==0&&!LocalMaterials.safeName(value))throw new IllegalArgumentException("نام فایل جزوه نامعتبر است");
                    if(kind==1&&!LocalMaterials.validLink(value))throw new IllegalArgumentException("لینک درسی نامعتبر است");
                }
            }
        }
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            for(int i=TABLES.length-1;i>=0;i--)db.delete(TABLES[i],null,null);
            for(int i=0;i<TABLES.length;i++) {
                JSONArray rows=root.getJSONArray(TABLES[i]);
                for(int j=0;j<rows.length();j++) {
                    JSONObject row=rows.getJSONObject(j);
                    ContentValues values=new ContentValues();
                    for(String col:COLUMNS[i]) {
                        Object v=row.get(col);
                        if(v==JSONObject.NULL)values.putNull(col);
                        else if(v instanceof String)values.put(col,(String)v);
                        else if(col.equals("grade"))values.put(col,((Number)v).doubleValue());
                        else values.put(col,((Number)v).longValue());
                    }
                    db.insertOrThrow(TABLES[i],null,values);
                }
            }
            try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)) {
                if(c.moveToFirst())throw new IllegalArgumentException("ارتباط رکوردها نامعتبر است");
            }
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
    }
}
