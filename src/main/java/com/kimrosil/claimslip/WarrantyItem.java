package com.kimrosil.claimslip;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class WarrantyItem {

    public enum Status {
        ACTIVE,
        EXPIRING_SOON,
        EXPIRED
    }

    private long id;
    private String itemName;
    private String category;
    private String retailer;
    private String purchaseDate; // YYYY-MM-DD
    private int warrantyMonths;
    private String expiryDate; // YYYY-MM-DD
    private double price;
    private String currency;
    private String modelNo;
    private String serialNo;
    private String notes;
    private String receiptPath;
    private boolean isThermalSlip;
    private long createdAt;

    public WarrantyItem() {
        this.currency = "₱";
        this.warrantyMonths = 12;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getItemName() { return itemName != null ? itemName : ""; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getCategory() { return category != null ? category : "Appliances"; }
    public void setCategory(String category) { this.category = category; }

    public String getRetailer() { return retailer != null ? retailer : ""; }
    public void setRetailer(String retailer) { this.retailer = retailer; }

    public String getPurchaseDate() { return purchaseDate != null ? purchaseDate : ""; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate = purchaseDate; }

    public int getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(int warrantyMonths) { this.warrantyMonths = warrantyMonths; }

    public String getExpiryDate() { return expiryDate != null ? expiryDate : ""; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCurrency() { return currency != null ? currency : "₱"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getModelNo() { return modelNo != null ? modelNo : ""; }
    public void setModelNo(String modelNo) { this.modelNo = modelNo; }

    public String getSerialNo() { return serialNo != null ? serialNo : ""; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getReceiptPath() { return receiptPath != null ? receiptPath : ""; }
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }

    public boolean isThermalSlip() { return isThermalSlip; }
    public void setThermalSlip(boolean thermalSlip) { isThermalSlip = thermalSlip; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    // =========================================================================
    // Calculations & Status Helpers
    // =========================================================================

    public int getDaysRemaining() {
        if (expiryDate == null || expiryDate.isEmpty()) return 0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date exp = sdf.parse(expiryDate);
            if (exp == null) return 0;

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);

            long diffMillis = exp.getTime() - today.getTimeInMillis();
            return (int) (diffMillis / (1000L * 3600L * 24L));
        } catch (Exception e) {
            return 0;
        }
    }

    public Status getStatus() {
        int days = getDaysRemaining();
        if (days < 0) {
            return Status.EXPIRED;
        } else if (days <= 30) {
            return Status.EXPIRING_SOON;
        } else {
            return Status.ACTIVE;
        }
    }

    public String getStatusBadgeText() {
        int days = getDaysRemaining();
        if (days < 0) {
            return "Expired (" + Math.abs(days) + "d ago)";
        } else if (days == 0) {
            return "Expires Today!";
        } else if (days <= 30) {
            return "Expiring: " + days + "d left";
        } else {
            return "Active: " + days + "d left";
        }
    }

    public static String calculateExpiry(String purchaseDateStr, int months) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date pDate = sdf.parse(purchaseDateStr);
            if (pDate == null) return purchaseDateStr;
            Calendar cal = Calendar.getInstance();
            cal.setTime(pDate);
            cal.add(Calendar.MONTH, months);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            return purchaseDateStr;
        }
    }
}
