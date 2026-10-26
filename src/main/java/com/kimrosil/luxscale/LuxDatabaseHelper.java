package com.kimrosil.luxscale;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local SQLite Storage for Room & Plant Canopy Photometric Audit Logs.
 */
public class LuxDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "lux_scale_audits.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE_AUDITS = "room_audits";

    public static final class AuditRecord {
        public final long id;
        public final String roomName;
        public final double calLux;
        public final double ppfd;
        public final double dli;
        public final double calFactor;
        public final int spectrumMode;

        public AuditRecord(
                long id,
                String roomName,
                double calLux,
                double ppfd,
                double dli,
                double calFactor,
                int spectrumMode) {
            this.id = id;
            this.roomName = roomName;
            this.calLux = calLux;
            this.ppfd = ppfd;
            this.dli = dli;
            this.calFactor = calFactor;
            this.spectrumMode = spectrumMode;
        }
    }

    public LuxDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_AUDITS + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "room_name TEXT NOT NULL, "
                + "cal_lux REAL NOT NULL, "
                + "ppfd REAL NOT NULL, "
                + "dli REAL NOT NULL, "
                + "cal_factor REAL NOT NULL, "
                + "spectrum_mode INTEGER NOT NULL"
                + ")");

        seedRecord(db, "Studio Desk Bay", 520.0, 9.6, 0.48, 1.00, LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        seedRecord(db, "Monstera East Window", 2450.0, 45.3, 2.28, 1.00, LuxEngine.SPECTRUM_DAYLIGHT_6500K);
        seedRecord(db, "Basil Grow Rack A", 9200.0, 200.6, 11.55, 1.05, LuxEngine.SPECTRUM_GROW_PAR_LED);
    }

    private void seedRecord(
            SQLiteDatabase db,
            String name,
            double lux,
            double ppfd,
            double dli,
            double calFactor,
            int spectrum) {
        ContentValues cv = new ContentValues();
        cv.put("room_name", name);
        cv.put("cal_lux", lux);
        cv.put("ppfd", ppfd);
        cv.put("dli", dli);
        cv.put("cal_factor", calFactor);
        cv.put("spectrum_mode", spectrum);
        db.insert(TABLE_AUDITS, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_AUDITS);
        onCreate(db);
    }

    public long insertAudit(
            String roomName,
            double calLux,
            double ppfd,
            double dli,
            double calFactor,
            int spectrumMode) {
        String safeName = (roomName == null || roomName.trim().isEmpty()) ? "Room Audit" : roomName.trim();
        if (safeName.length() > 24) {
            safeName = safeName.substring(0, 24);
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("room_name", safeName);
        cv.put("cal_lux", LuxEngine.clamp(calLux, 0.0, 500000.0));
        cv.put("ppfd", LuxEngine.clamp(ppfd, 0.0, 10000.0));
        cv.put("dli", LuxEngine.clamp(dli, 0.0, 500.0));
        cv.put("cal_factor", LuxEngine.clamp(calFactor, 0.05, 100.0));
        cv.put("spectrum_mode", spectrumMode);
        return db.insert(TABLE_AUDITS, null, cv);
    }

    public void deleteAudit(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_AUDITS, "id = ?", new String[]{String.valueOf(id)});
    }

    public List<AuditRecord> getAllAudits() {
        List<AuditRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_AUDITS, null, null, null, null, null, "id DESC", "15");
        try {
            while (c.moveToNext()) {
                list.add(new AuditRecord(
                        c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("room_name")),
                        c.getDouble(c.getColumnIndexOrThrow("cal_lux")),
                        c.getDouble(c.getColumnIndexOrThrow("ppfd")),
                        c.getDouble(c.getColumnIndexOrThrow("dli")),
                        c.getDouble(c.getColumnIndexOrThrow("cal_factor")),
                        c.getInt(c.getColumnIndexOrThrow("spectrum_mode"))
                ));
            }
        } finally {
            c.close();
        }
        return list;
    }
}
