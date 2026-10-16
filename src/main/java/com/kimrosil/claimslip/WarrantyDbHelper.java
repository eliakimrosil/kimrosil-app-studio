package com.kimrosil.claimslip;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WarrantyDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "claimslip.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_WARRANTIES = "warranties";
    public static final String COL_ID = "id";
    public static final String COL_ITEM_NAME = "item_name";
    public static final String COL_CATEGORY = "category";
    public static final String COL_RETAILER = "retailer";
    public static final String COL_PURCHASE_DATE = "purchase_date";
    public static final String COL_WARRANTY_MONTHS = "warranty_months";
    public static final String COL_EXPIRY_DATE = "expiry_date";
    public static final String COL_PRICE = "price";
    public static final String COL_CURRENCY = "currency";
    public static final String COL_MODEL_NO = "model_no";
    public static final String COL_SERIAL_NO = "serial_no";
    public static final String COL_NOTES = "notes";
    public static final String COL_RECEIPT_PATH = "receipt_path";
    public static final String COL_IS_THERMAL = "is_thermal";
    public static final String COL_CREATED_AT = "created_at";

    public WarrantyDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createSql = "CREATE TABLE " + TABLE_WARRANTIES + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ITEM_NAME + " TEXT NOT NULL, "
                + COL_CATEGORY + " TEXT NOT NULL, "
                + COL_RETAILER + " TEXT, "
                + COL_PURCHASE_DATE + " TEXT NOT NULL, "
                + COL_WARRANTY_MONTHS + " INTEGER NOT NULL, "
                + COL_EXPIRY_DATE + " TEXT NOT NULL, "
                + COL_PRICE + " REAL, "
                + COL_CURRENCY + " TEXT, "
                + COL_MODEL_NO + " TEXT, "
                + COL_SERIAL_NO + " TEXT, "
                + COL_NOTES + " TEXT, "
                + COL_RECEIPT_PATH + " TEXT, "
                + COL_IS_THERMAL + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER"
                + ");";
        db.execSQL(createSql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WARRANTIES);
        onCreate(db);
    }

    public long insertWarranty(WarrantyItem item) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ITEM_NAME, item.getItemName());
        cv.put(COL_CATEGORY, item.getCategory());
        cv.put(COL_RETAILER, item.getRetailer());
        cv.put(COL_PURCHASE_DATE, item.getPurchaseDate());
        cv.put(COL_WARRANTY_MONTHS, item.getWarrantyMonths());
        cv.put(COL_EXPIRY_DATE, item.getExpiryDate());
        cv.put(COL_PRICE, item.getPrice());
        cv.put(COL_CURRENCY, item.getCurrency());
        cv.put(COL_MODEL_NO, item.getModelNo());
        cv.put(COL_SERIAL_NO, item.getSerialNo());
        cv.put(COL_NOTES, item.getNotes());
        cv.put(COL_RECEIPT_PATH, item.getReceiptPath());
        cv.put(COL_IS_THERMAL, item.isThermalSlip() ? 1 : 0);
        cv.put(COL_CREATED_AT, item.getCreatedAt());
        return db.insert(TABLE_WARRANTIES, null, cv);
    }

    public int deleteWarranty(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_WARRANTIES, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public List<WarrantyItem> getWarranties(String filterType, String searchQuery) {
        List<WarrantyItem> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String selection = null;
        List<String> selectionArgsList = new ArrayList<>();

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            selection = "(" + COL_ITEM_NAME + " LIKE ? OR "
                    + COL_RETAILER + " LIKE ? OR "
                    + COL_MODEL_NO + " LIKE ? OR "
                    + COL_SERIAL_NO + " LIKE ?)";
            String likeArg = "%" + searchQuery.trim() + "%";
            selectionArgsList.add(likeArg);
            selectionArgsList.add(likeArg);
            selectionArgsList.add(likeArg);
            selectionArgsList.add(likeArg);
        }

        String[] selectionArgs = selectionArgsList.isEmpty() ? null : selectionArgsList.toArray(new String[0]);
        String orderBy = COL_EXPIRY_DATE + " ASC";

        Cursor c = db.query(TABLE_WARRANTIES, null, selection, selectionArgs, null, null, orderBy);
        if (c != null) {
            while (c.moveToNext()) {
                WarrantyItem item = cursorToItem(c);
                if (matchesFilter(item, filterType)) {
                    list.add(item);
                }
            }
            c.close();
        }
        return list;
    }

    private boolean matchesFilter(WarrantyItem item, String filter) {
        if (filter == null || filter.equalsIgnoreCase("ALL")) return true;
        WarrantyItem.Status s = item.getStatus();
        if (filter.equalsIgnoreCase("ACTIVE")) {
            return s == WarrantyItem.Status.ACTIVE || s == WarrantyItem.Status.EXPIRING_SOON;
        } else if (filter.equalsIgnoreCase("EXPIRING")) {
            return s == WarrantyItem.Status.EXPIRING_SOON;
        } else if (filter.equalsIgnoreCase("EXPIRED")) {
            return s == WarrantyItem.Status.EXPIRED;
        }
        return true;
    }

    private WarrantyItem cursorToItem(Cursor c) {
        WarrantyItem item = new WarrantyItem();
        item.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
        item.setItemName(c.getString(c.getColumnIndexOrThrow(COL_ITEM_NAME)));
        item.setCategory(c.getString(c.getColumnIndexOrThrow(COL_CATEGORY)));
        item.setRetailer(c.getString(c.getColumnIndexOrThrow(COL_RETAILER)));
        item.setPurchaseDate(c.getString(c.getColumnIndexOrThrow(COL_PURCHASE_DATE)));
        item.setWarrantyMonths(c.getInt(c.getColumnIndexOrThrow(COL_WARRANTY_MONTHS)));
        item.setExpiryDate(c.getString(c.getColumnIndexOrThrow(COL_EXPIRY_DATE)));
        item.setPrice(c.getDouble(c.getColumnIndexOrThrow(COL_PRICE)));
        item.setCurrency(c.getString(c.getColumnIndexOrThrow(COL_CURRENCY)));
        item.setModelNo(c.getString(c.getColumnIndexOrThrow(COL_MODEL_NO)));
        item.setSerialNo(c.getString(c.getColumnIndexOrThrow(COL_SERIAL_NO)));
        item.setNotes(c.getString(c.getColumnIndexOrThrow(COL_NOTES)));
        item.setReceiptPath(c.getString(c.getColumnIndexOrThrow(COL_RECEIPT_PATH)));
        item.setThermalSlip(c.getInt(c.getColumnIndexOrThrow(COL_IS_THERMAL)) == 1);
        item.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)));
        return item;
    }

    public void seedInitialSampleDataIfEmpty() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_WARRANTIES, null);
        int count = 0;
        if (c != null) {
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
        }

        if (count == 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();

            // 1. LG Dual Inverter AC (Purchased 6 months ago, 24-month warranty -> ACTIVE, ~540 days left)
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -6);
            String pDate1 = sdf.format(cal.getTime());
            cal.add(Calendar.MONTH, 24);
            String expDate1 = sdf.format(cal.getTime());

            WarrantyItem item1 = new WarrantyItem();
            item1.setItemName("LG Dual-Inverter Split AC 1.5HP");
            item1.setCategory("Appliances");
            item1.setRetailer("Abenson Appliance");
            item1.setPurchaseDate(pDate1);
            item1.setWarrantyMonths(24);
            item1.setExpiryDate(expDate1);
            item1.setPrice(38995.00);
            item1.setCurrency("₱");
            item1.setModelNo("HS-12ISM");
            item1.setSerialNo("LGAC-2024-88491");
            item1.setNotes("10-Year compressor coverage / 2-Year general parts. Service: (02) 8902-5544");
            item1.setThermalSlip(true);
            insertWarranty(item1);

            // 2. Sony WH-1000XM5 (Purchased 11 months ago, 12-month warranty -> EXPIRING SOON, ~20 days left)
            cal.setTime(new Date());
            cal.add(Calendar.DAY_OF_YEAR, -345);
            String pDate2 = sdf.format(cal.getTime());
            cal.add(Calendar.MONTH, 12);
            String expDate2 = sdf.format(cal.getTime());

            WarrantyItem item2 = new WarrantyItem();
            item2.setItemName("Sony WH-1000XM5 ANC Headphones");
            item2.setCategory("Electronics");
            item2.setRetailer("Sony Authorized Hub");
            item2.setPurchaseDate(pDate2);
            item2.setWarrantyMonths(12);
            item2.setExpiryDate(expDate2);
            item2.setPrice(19999.00);
            item2.setCurrency("₱");
            item2.setModelNo("WH1000XM5/B");
            item2.setSerialNo("SN-5082194-XM5");
            item2.setNotes("Includes 1-Year Sony PH official warranty. Thermal receipt scanned.");
            item2.setThermalSlip(true);
            insertWarranty(item2);

            // 3. De'Longhi Dedica Espresso (Purchased 14 months ago, 12-month warranty -> EXPIRED)
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -14);
            String pDate3 = sdf.format(cal.getTime());
            cal.add(Calendar.MONTH, 12);
            String expDate3 = sdf.format(cal.getTime());

            WarrantyItem item3 = new WarrantyItem();
            item3.setItemName("De'Longhi Dedica Deluxe Pump Espresso");
            item3.setCategory("Appliances");
            item3.setRetailer("Rustan's Department Store");
            item3.setPurchaseDate(pDate3);
            item3.setWarrantyMonths(12);
            item3.setExpiryDate(expDate3);
            item3.setPrice(16500.00);
            item3.setCurrency("₱");
            item3.setModelNo("EC685M");
            item3.setSerialNo("DL-993184-IT");
            item3.setNotes("Standard 1-year coverage expired. Out-of-warranty service available.");
            item3.setThermalSlip(false);
            insertWarranty(item3);
        }
    }
}
