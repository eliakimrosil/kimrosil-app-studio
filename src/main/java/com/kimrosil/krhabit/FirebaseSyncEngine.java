package com.kimrosil.krhabit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FirebaseSyncEngine manages Google Account authentication and Cloud Firestore
 * synchronization using lightweight, zero-dependency REST APIs.
 */
public class FirebaseSyncEngine {

    private static final String PREFS_NAME = "kr_habit_cloud";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_PROJECT_ID = "project_id";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_CLIENT_ID = "client_id";
    private static final String KEY_LAST_SYNC = "last_sync_time";

    private final Context context;
    private final SharedPreferences prefs;
    private final DatabaseHelper dbHelper;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface SyncCallback {
        void onSyncStarted();
        void onSyncSuccess(int habitsSynced, int logsSynced, String message);
        void onSyncError(String error);
    }

    public FirebaseSyncEngine(Context context, DatabaseHelper dbHelper) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.dbHelper = dbHelper;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public boolean isSignedIn() {
        return prefs.getString(KEY_USER_ID, null) != null;
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "Google User");
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "Not signed in");
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getIdToken() {
        return prefs.getString(KEY_ID_TOKEN, null);
    }

    public String getLastSyncTimeFormatted() {
        long time = prefs.getLong(KEY_LAST_SYNC, 0);
        if (time == 0) return "Never";
        return new SimpleDateFormat("MMM dd, hh:mm a", Locale.US).format(new Date(time));
    }

    public String getProjectId() {
        return prefs.getString(KEY_PROJECT_ID, "gen-lang-client-0189236416");
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "AIzaSyCVp76Ac2QCds-fe7kiPbC1J6N_BvuOcuQ");
    }

    public String getClientId() {
        return prefs.getString(KEY_CLIENT_ID, "");
    }

    public void saveFirebaseConfig(String projectId, String apiKey, String clientId) {
        prefs.edit()
                .putString(KEY_PROJECT_ID, projectId != null ? projectId.trim() : "")
                .putString(KEY_API_KEY, apiKey != null ? apiKey.trim() : "")
                .putString(KEY_CLIENT_ID, clientId != null ? clientId.trim() : "")
                .apply();
    }

    /**
     * Signs in with Google credentials or developer test account.
     */
    public void signIn(String uid, String email, String name, String token) {
        prefs.edit()
                .putString(KEY_USER_ID, uid)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_ID_TOKEN, token)
                .apply();
    }

    public void signOut() {
        prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_NAME)
                .remove(KEY_ID_TOKEN)
                .remove(KEY_LAST_SYNC)
                .apply();
    }

    /**
     * Synchronizes local SQLite habits & logs with Cloud Firestore.
     */
    public void syncWithFirestore(final SyncCallback callback) {
        if (!isSignedIn()) {
            if (callback != null) {
                callback.onSyncError("Sign in with Google required before syncing.");
            }
            return;
        }

        if (callback != null) {
            callback.onSyncStarted();
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String projectId = getProjectId();
                    String uid = getUserId();
                    String apiKey = getApiKey();

                    if (projectId.isEmpty()) {
                        postError(callback, "Firebase Project ID not configured in Settings.");
                        return;
                    }

                    List<DatabaseHelper.HabitItem> unsyncedHabits = dbHelper.getUnsyncedHabits();
                    List<DatabaseHelper.HabitLogEntry> unsyncedLogs = dbHelper.getUnsyncedLogs();

                    int habitCount = 0;
                    int logCount = 0;

                    // Push unsynced habits to Firestore
                    for (DatabaseHelper.HabitItem h : unsyncedHabits) {
                        boolean ok = pushHabitToFirestore(projectId, uid, apiKey, h);
                        if (ok) {
                            dbHelper.markHabitSynced(h.uuid);
                            habitCount++;
                        }
                    }

                    // Push unsynced logs to Firestore
                    for (DatabaseHelper.HabitLogEntry l : unsyncedLogs) {
                        boolean ok = pushLogToFirestore(projectId, uid, apiKey, l);
                        if (ok) {
                            dbHelper.markLogSynced(l.habitUuid, l.dateStr);
                            logCount++;
                        }
                    }

                    // Fetch remote changes from Firestore
                    fetchRemoteHabits(projectId, uid, apiKey);

                    // Update last sync timestamp
                    long now = System.currentTimeMillis();
                    prefs.edit().putLong(KEY_LAST_SYNC, now).apply();

                    final int finalHabitCount = habitCount;
                    final int finalLogCount = logCount;
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) {
                                String msg = "Synced " + finalHabitCount + " habits and " + finalLogCount + " check-ins with Firebase.";
                                callback.onSyncSuccess(finalHabitCount, finalLogCount, msg);
                            }
                        }
                    });

                } catch (final Exception e) {
                    postError(callback, e.getMessage() != null ? e.getMessage() : "Network error during sync.");
                }
            }
        });
    }

    private boolean pushHabitToFirestore(String projectId, String uid, String apiKey, DatabaseHelper.HabitItem habit) {
        HttpURLConnection conn = null;
        try {
            // Firestore REST API document path
            String urlStr = "https://firestore.googleapis.com/v1/projects/" + projectId
                    + "/databases/(default)/documents/users/" + uid + "/habits/" + habit.uuid;
            if (!apiKey.isEmpty()) {
                urlStr += "?key=" + apiKey;
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            String token = getIdToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            // Construct Firestore Document JSON
            JSONObject fields = new JSONObject();
            fields.put("uuid", new JSONObject().put("stringValue", habit.uuid));
            fields.put("title", new JSONObject().put("stringValue", habit.title));
            fields.put("description", new JSONObject().put("stringValue", habit.description));
            fields.put("category", new JSONObject().put("stringValue", habit.category));
            fields.put("frequency", new JSONObject().put("integerValue", habit.targetDaysPerWeek));
            fields.put("created_at", new JSONObject().put("integerValue", habit.createdAt));
            fields.put("updated_at", new JSONObject().put("integerValue", habit.updatedAt));

            JSONObject doc = new JSONObject();
            doc.put("fields", fields);

            OutputStream os = conn.getOutputStream();
            os.write(doc.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            return code == 200 || code == 201;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private boolean pushLogToFirestore(String projectId, String uid, String apiKey, DatabaseHelper.HabitLogEntry log) {
        HttpURLConnection conn = null;
        try {
            String docId = log.habitUuid + "_" + log.dateStr;
            String urlStr = "https://firestore.googleapis.com/v1/projects/" + projectId
                    + "/databases/(default)/documents/users/" + uid + "/logs/" + docId;
            if (!apiKey.isEmpty()) {
                urlStr += "?key=" + apiKey;
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Content-Type", "application/json");
            String token = getIdToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);

            JSONObject fields = new JSONObject();
            fields.put("habit_uuid", new JSONObject().put("stringValue", log.habitUuid));
            fields.put("log_date", new JSONObject().put("stringValue", log.dateStr));
            fields.put("completed", new JSONObject().put("booleanValue", log.completed));
            fields.put("updated_at", new JSONObject().put("integerValue", log.updatedAt));

            JSONObject doc = new JSONObject();
            doc.put("fields", fields);

            OutputStream os = conn.getOutputStream();
            os.write(doc.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            return code == 200 || code == 201;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void fetchRemoteHabits(String projectId, String uid, String apiKey) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://firestore.googleapis.com/v1/projects/" + projectId
                    + "/databases/(default)/documents/users/" + uid + "/habits";
            if (!apiKey.isEmpty()) {
                urlStr += "?key=" + apiKey;
            }

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String token = getIdToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code == 200) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject response = new JSONObject(sb.toString());
                if (response.has("documents")) {
                    JSONArray docs = response.getJSONArray("documents");
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.getJSONObject(i);
                        if (doc.has("fields")) {
                            JSONObject f = doc.getJSONObject("fields");
                            String uuid = f.has("uuid") ? f.getJSONObject("uuid").optString("stringValue", "") : "";
                            String title = f.has("title") ? f.getJSONObject("title").optString("stringValue", "") : "";
                            String desc = f.has("description") ? f.getJSONObject("description").optString("stringValue", "") : "";
                            String cat = f.has("category") ? f.getJSONObject("category").optString("stringValue", "Health") : "Health";
                            int freq = f.has("frequency") ? f.getJSONObject("frequency").optInt("integerValue", 7) : 7;
                            long createdAt = f.has("created_at") ? f.getJSONObject("created_at").optLong("integerValue", System.currentTimeMillis()) : System.currentTimeMillis();
                            long updatedAt = f.has("updated_at") ? f.getJSONObject("updated_at").optLong("integerValue", System.currentTimeMillis()) : System.currentTimeMillis();

                            if (!uuid.isEmpty() && !title.isEmpty()) {
                                dbHelper.mergeRemoteHabit(uuid, title, desc, cat, freq, createdAt, updatedAt);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void postError(final SyncCallback callback, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onSyncError(message);
                }
            }
        });
    }
}
