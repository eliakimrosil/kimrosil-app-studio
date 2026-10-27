package com.kimrosil.ductflow;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DuctDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ductflow.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_DUCTS = "saved_ducts";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_LABEL = "label";
    public static final String COLUMN_MODE = "mode";
    public static final String COLUMN_MATERIAL = "material";
    public static final String COLUMN_CFM = "cfm";
    public static final String COLUMN_FRICTION = "friction_rate";
    public static final String COLUMN_HEIGHT = "known_height";
    public static final String COLUMN_ROUND_DIA = "round_dia";
    public static final String COLUMN_RECT_WIDTH = "rect_width";
    public static final String COLUMN_VELOCITY = "velocity_fpm";
    public static final String COLUMN_ASPECT = "aspect_ratio";
    public static final String COLUMN_IS_METRIC = "is_metric";
    public static final String COLUMN_TIMESTAMP = "created_at";

    public static class DuctRecord {
        public long id;
        public String label;
        public int mode;
        public int material;
        public double cfm;
        public double frictionRate;
        public double height;
        public double roundDia;
        public double rectWidth;
        public double velocity;
        public double aspectRatio;
        public boolean isMetric;
        public long createdAt;
    }

    public DuctDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_DUCTS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_LABEL + " TEXT, " +
                COLUMN_MODE + " INTEGER DEFAULT 0, " +
                COLUMN_MATERIAL + " INTEGER DEFAULT 0, " +
                COLUMN_CFM + " REAL, " +
                COLUMN_FRICTION + " REAL, " +
                COLUMN_HEIGHT + " REAL, " +
                COLUMN_ROUND_DIA + " REAL, " +
                COLUMN_RECT_WIDTH + " REAL, " +
                COLUMN_VELOCITY + " REAL, " +
                COLUMN_ASPECT + " REAL, " +
                COLUMN_IS_METRIC + " INTEGER DEFAULT 0, " +
                COLUMN_TIMESTAMP + " INTEGER" +
                ");";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_DUCTS + " ADD COLUMN " + COLUMN_LABEL + " TEXT DEFAULT '';");
                db.execSQL("ALTER TABLE " + TABLE_DUCTS + " ADD COLUMN " + COLUMN_MODE + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_DUCTS + " ADD COLUMN " + COLUMN_MATERIAL + " INTEGER DEFAULT 0;");
                db.execSQL("ALTER TABLE " + TABLE_DUCTS + " ADD COLUMN " + COLUMN_IS_METRIC + " INTEGER DEFAULT 0;");
            } catch (Exception e) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_DUCTS);
                onCreate(db);
            }
        }
    }

    public long insertDuct(String label, int mode, int material, boolean isMetric,
                           double cfm, double friction, double height,
                           double roundDia, double rectWidth, double velocity, double aspect) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_LABEL, (label == null || label.trim().isEmpty()) ? "Duct Run" : label.trim());
        cv.put(COLUMN_MODE, mode);
        cv.put(COLUMN_MATERIAL, material);
        cv.put(COLUMN_IS_METRIC, isMetric ? 1 : 0);
        cv.put(COLUMN_CFM, cfm);
        cv.put(COLUMN_FRICTION, friction);
        cv.put(COLUMN_HEIGHT, height);
        cv.put(COLUMN_ROUND_DIA, roundDia);
        cv.put(COLUMN_RECT_WIDTH, rectWidth);
        cv.put(COLUMN_VELOCITY, velocity);
        cv.put(COLUMN_ASPECT, aspect);
        cv.put(COLUMN_TIMESTAMP, System.currentTimeMillis());
        return db.insert(TABLE_DUCTS, null, cv);
    }

    public List<DuctRecord> getAllDucts() {
        List<DuctRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_DUCTS, null, null, null, null, null, COLUMN_TIMESTAMP + " DESC");
        if (cursor != null) {
            try {
                int idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID);
                int labelIdx = cursor.getColumnIndex(COLUMN_LABEL);
                int modeIdx = cursor.getColumnIndex(COLUMN_MODE);
                int matIdx = cursor.getColumnIndex(COLUMN_MATERIAL);
                int metricIdx = cursor.getColumnIndex(COLUMN_IS_METRIC);
                int cfmIdx = cursor.getColumnIndexOrThrow(COLUMN_CFM);
                int fricIdx = cursor.getColumnIndexOrThrow(COLUMN_FRICTION);
                int hIdx = cursor.getColumnIndexOrThrow(COLUMN_HEIGHT);
                int rIdx = cursor.getColumnIndexOrThrow(COLUMN_ROUND_DIA);
                int wIdx = cursor.getColumnIndexOrThrow(COLUMN_RECT_WIDTH);
                int vIdx = cursor.getColumnIndexOrThrow(COLUMN_VELOCITY);
                int aIdx = cursor.getColumnIndexOrThrow(COLUMN_ASPECT);
                int tIdx = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP);

                while (cursor.moveToNext()) {
                    DuctRecord r = new DuctRecord();
                    r.id = cursor.getLong(idIdx);
                    r.label = (labelIdx != -1 && !cursor.isNull(labelIdx)) ? cursor.getString(labelIdx) : "Duct Run";
                    r.mode = (modeIdx != -1) ? cursor.getInt(modeIdx) : 0;
                    r.material = (matIdx != -1) ? cursor.getInt(matIdx) : 0;
                    r.isMetric = (metricIdx != -1) && (cursor.getInt(metricIdx) == 1);
                    r.cfm = cursor.getDouble(cfmIdx);
                    r.frictionRate = cursor.getDouble(fricIdx);
                    r.height = cursor.getDouble(hIdx);
                    r.roundDia = cursor.getDouble(rIdx);
                    r.rectWidth = cursor.getDouble(wIdx);
                    r.velocity = cursor.getDouble(vIdx);
                    r.aspectRatio = cursor.getDouble(aIdx);
                    r.createdAt = cursor.getLong(tIdx);
                    list.add(r);
                }
            } finally {
                cursor.close();
            }
        }
        return list;
    }

    public boolean deleteDuct(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_DUCTS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DUCTS, null, null);
    }

    public String exportToCsv() {
        List<DuctRecord> list = getAllDucts();
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Label,Airflow_CFM,Friction_InWg100ft,Height_In,RoundDia_In,RectWidth_In,Velocity_FPM,AspectRatio,Material,Timestamp\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        for (DuctRecord r : list) {
            String matName = (r.material == DuctFlowEngine.MATERIAL_FLEX_DUCT) ? "Flexible Duct" : "Sheet Metal";
            sb.append(String.format(Locale.US, "%d,\"%s\",%.0f,%.3f,%.1f,%.1f,%.1f,%.0f,%.1f,\"%s\",\"%s\"\n",
                    r.id,
                    r.label.replace("\"", "\"\""),
                    r.cfm,
                    r.frictionRate,
                    r.height,
                    r.roundDia,
                    r.rectWidth,
                    r.velocity,
                    r.aspectRatio,
                    matName,
                    sdf.format(new Date(r.createdAt))
            ));
        }
        return sb.toString();
    }
}
