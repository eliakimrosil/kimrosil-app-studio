package com.kimrosil.flowstep;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Task Atomizer Engine for ADHD & Executive Dysfunction.
 * Provides instant 100% offline rule-based physical decomposition
 * and an optional zero-dependency Gemini 3.8 Flash cloud bridge.
 */
public class TaskAtomizerEngine {

    public static final String PREFS_NAME = "flowstep_settings";
    public static final String PREF_GEMINI_API_KEY = "gemini_api_key";

    public interface Callback {
        void onAtomized(List<DatabaseHelper.StepBlueprint> steps, boolean usedCloud);
    }

    public interface SubdivideCallback {
        void onSubdivided(DatabaseHelper.StepBlueprint sub1, DatabaseHelper.StepBlueprint sub2);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public TaskAtomizerEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public void atomize(String goalInput, Callback callback) {
        String trimmed = goalInput.trim();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(PREF_GEMINI_API_KEY, "").trim();

        if (apiKey.isEmpty()) {
            // Instant offline heuristic decomposition
            List<DatabaseHelper.StepBlueprint> steps = decomposeOffline(trimmed);
            mainHandler.post(() -> callback.onAtomized(steps, false));
            return;
        }

        // Try Gemini 3.8 Flash cloud bridge with automatic offline fallback
        executor.execute(() -> {
            try {
                List<DatabaseHelper.StepBlueprint> cloudSteps = callGeminiApi(trimmed, apiKey);
                if (cloudSteps != null && !cloudSteps.isEmpty()) {
                    mainHandler.post(() -> callback.onAtomized(cloudSteps, true));
                    return;
                }
            } catch (Exception ignored) {
                // Network unavailable, invalid key, or timeout -> seamless offline fallback
            }

            List<DatabaseHelper.StepBlueprint> fallbackSteps = decomposeOffline(trimmed);
            mainHandler.post(() -> callback.onAtomized(fallbackSteps, false));
        });
    }

    public void subdivide(String instruction, SubdivideCallback callback) {
        // Instant micro-starter subdivision (lowers activation energy to < 60 seconds)
        String lower = instruction.toLowerCase(Locale.US);
        DatabaseHelper.StepBlueprint sub1;
        DatabaseHelper.StepBlueprint sub2;

        if (lower.contains("write") || lower.contains("type")) {
            sub1 = new DatabaseHelper.StepBlueprint("SIT", "Sit in chair and type just ONE single sentence or headline", 1);
            sub2 = new DatabaseHelper.StepBlueprint("TYPE", "Type 3 bullet points without correcting grammar or spelling", 2);
        } else if (lower.contains("clean") || lower.contains("pick up") || lower.contains("wash")) {
            sub1 = new DatabaseHelper.StepBlueprint("PICK UP", "Pick up exactly ONE single object and put it in place", 1);
            sub2 = new DatabaseHelper.StepBlueprint("WIPE", "Wipe down just one square foot of surface", 1);
        } else if (lower.contains("read") || lower.contains("study")) {
            sub1 = new DatabaseHelper.StepBlueprint("OPEN", "Open page or doc and read only the first two paragraphs", 1);
            sub2 = new DatabaseHelper.StepBlueprint("HIGHLIGHT", "Underline or highlight one sentence that makes sense", 1);
        } else {
            sub1 = new DatabaseHelper.StepBlueprint("TOUCH", "Place your hands on the primary tool for 10 seconds without working", 1);
            sub2 = new DatabaseHelper.StepBlueprint("START", "Execute the absolute easiest physical movement for 60 seconds", 1);
        }

        mainHandler.post(() -> callback.onSubdivided(sub1, sub2));
    }

    /**
     * Deterministic, zero-network rule-based task atomizer.
     */
    public List<DatabaseHelper.StepBlueprint> decomposeOffline(String goal) {
        String lower = goal.toLowerCase(Locale.US);
        List<DatabaseHelper.StepBlueprint> steps = new ArrayList<>();

        if (lower.contains("clean") || lower.contains("tidy") || lower.contains("declutter") || lower.contains("room") || lower.contains("kitchen") || lower.contains("desk")) {
            steps.add(new DatabaseHelper.StepBlueprint("STAND UP", "Stand up and walk to the target room or area", 1));
            steps.add(new DatabaseHelper.StepBlueprint("GATHER", "Pick up all trash or empty dishes and throw them into the bin/sink", 2));
            steps.add(new DatabaseHelper.StepBlueprint("CLEAR", "Clear one flat surface completely (desk, counter, or bed)", 3));
            steps.add(new DatabaseHelper.StepBlueprint("WIPE", "Wipe the surface with a damp cloth or spray", 2));
        } else if (lower.contains("tax") || lower.contains("bill") || lower.contains("invoice") || lower.contains("finance")) {
            steps.add(new DatabaseHelper.StepBlueprint("SIT", "Sit at desk and open a glass of water", 1));
            steps.add(new DatabaseHelper.StepBlueprint("GATHER", "Place all physical receipts and documents in a single pile", 2));
            steps.add(new DatabaseHelper.StepBlueprint("LOG IN", "Open browser and log in to the tax or banking portal", 2));
            steps.add(new DatabaseHelper.StepBlueprint("DOWNLOAD", "Download or review only the first primary statement", 3));
        } else if (lower.contains("email") || lower.contains("inbox") || lower.contains("message")) {
            steps.add(new DatabaseHelper.StepBlueprint("OPEN", "Open email inbox and mute all other notifications", 1));
            steps.add(new DatabaseHelper.StepBlueprint("ARCHIVE", "Archive or delete the top 3 obvious newsletters/spam", 2));
            steps.add(new DatabaseHelper.StepBlueprint("CLICK", "Click into the single highest priority email", 1));
            steps.add(new DatabaseHelper.StepBlueprint("TYPE", "Type a short 2-sentence reply without overthinking", 2));
        } else if (lower.contains("write") || lower.contains("essay") || lower.contains("report") || lower.contains("doc")) {
            steps.add(new DatabaseHelper.StepBlueprint("OPEN", "Open a blank document and write the main title", 1));
            steps.add(new DatabaseHelper.StepBlueprint("OUTLINE", "Type 3 rough bullet points of what happened or what is needed", 2));
            steps.add(new DatabaseHelper.StepBlueprint("WRITE", "Draft 3 quick sentences under the first bullet point", 3));
            steps.add(new DatabaseHelper.StepBlueprint("STEP BACK", "Take one deep breath and stretch your shoulders", 1));
        } else if (lower.contains("workout") || lower.contains("exercise") || lower.contains("run") || lower.contains("gym")) {
            steps.add(new DatabaseHelper.StepBlueprint("PUT ON", "Put on your workout shoes and socks right now", 2));
            steps.add(new DatabaseHelper.StepBlueprint("FILL", "Fill your water bottle and set it by the door", 1));
            steps.add(new DatabaseHelper.StepBlueprint("STEP OUT", "Step onto the workout mat or walk outside", 2));
            steps.add(new DatabaseHelper.StepBlueprint("MOVE", "Do 60 seconds of gentle stretching or jogging in place", 2));
        } else if (lower.contains("laundry") || lower.contains("clothes")) {
            steps.add(new DatabaseHelper.StepBlueprint("WALK TO", "Walk to the laundry pile with an empty basket", 1));
            steps.add(new DatabaseHelper.StepBlueprint("LOAD", "Put one load of darks/lights into the washer", 2));
            steps.add(new DatabaseHelper.StepBlueprint("POUR", "Add detergent and press Start on normal cycle", 1));
        } else {
            // Universal physical friction-lowering heuristic for arbitrary tasks
            steps.add(new DatabaseHelper.StepBlueprint("WALK TO", "Walk directly to the physical workspace for this task", 1));
            steps.add(new DatabaseHelper.StepBlueprint("REMOVE", "Remove the phone or 1 obvious distraction out of sight", 1));
            steps.add(new DatabaseHelper.StepBlueprint("OPEN", "Open or gather the exact primary tool needed for: " + goal, 2));
            steps.add(new DatabaseHelper.StepBlueprint("ENGAGE", "Perform the first 2 minutes of physical action at a gentle pace", 3));
        }
        return steps;
    }

    /**
     * Pure Java/Android HttpsURLConnection client for Gemini 3.8 Flash.
     * Zero Ktor, Retrofit, or external serialization dependencies.
     */
    private List<DatabaseHelper.StepBlueprint> callGeminiApi(String goal, String apiKey) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=" + apiKey;
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);

        String systemPrompt = "You are an Executive Function Task Atomizer for adults with severe ADHD. " +
                "Decompose the user's task into 3 to 5 hyper-concrete physical micro-actions. " +
                "CRITICAL RULES: " +
                "1. PHYSICAL VERBS ONLY: Every instruction MUST start with an unambiguous physical action verb (e.g. 'Pick up', 'Open', 'Sit', 'Walk to', 'Type', 'Wipe', 'Gather'). " +
                "2. NO VAGUE VERBS: Never use cognitive verbs like 'Plan', 'Analyze', 'Think', 'Organize', 'Review'. " +
                "3. Maximum 3 minutes per step. " +
                "4. Output valid JSON in format: {\"steps\": [{\"verb\": \"OPEN\", \"instruction\": \"Open document\", \"minutes\": 2}]}";

        JSONObject requestJson = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject userPart = new JSONObject();
        userPart.put("role", "user");
        JSONArray parts = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("text", systemPrompt + "\n\nUser Task: " + goal);
        parts.put(textPart);
        userPart.put("parts", parts);
        contents.put(userPart);
        requestJson.put("contents", contents);

        JSONObject genConfig = new JSONObject();
        genConfig.put("temperature", 0.1);
        genConfig.put("response_mime_type", "application/json");
        requestJson.put("generationConfig", genConfig);

        byte[] postBytes = requestJson.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postBytes);
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            return null;
        }

        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }
        }
        conn.disconnect();

        JSONObject responseObj = new JSONObject(responseBuilder.toString());
        JSONArray candidates = responseObj.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return null;

        JSONObject candidate = candidates.getJSONObject(0);
        JSONObject content = candidate.optJSONObject("content");
        if (content == null) return null;

        JSONArray partsArr = content.optJSONArray("parts");
        if (partsArr == null || partsArr.length() == 0) return null;

        String rawJson = partsArr.getJSONObject(0).optString("text", "");
        if (rawJson.isEmpty()) return null;

        JSONObject parsed = new JSONObject(rawJson);
        JSONArray stepsArr = parsed.optJSONArray("steps");
        if (stepsArr == null) return null;

        List<DatabaseHelper.StepBlueprint> result = new ArrayList<>();
        for (int i = 0; i < stepsArr.length(); i++) {
            JSONObject stepObj = stepsArr.getJSONObject(i);
            String verb = stepObj.optString("verb", "START").toUpperCase(Locale.US);
            String instruction = stepObj.optString("instruction", "");
            int minutes = stepObj.optInt("minutes", 2);
            if (!instruction.isEmpty()) {
                result.add(new DatabaseHelper.StepBlueprint(verb, instruction, minutes));
            }
        }

        return result.isEmpty() ? null : result;
    }
}
