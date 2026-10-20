package com.kimrosil.krhabit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ProfileImageHelper fetches, transforms, and caches the user's Google / Gravatar
 * profile picture as a circular image with zero external dependencies.
 */
public class ProfileImageHelper {

    private static final String CACHE_FILE_PREFIX = "user_avatar_";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface ImageCallback {
        void onLoaded(Bitmap bitmap);
    }

    public static void loadProfileImage(final Context context, final String email, final ImageView targetView, final ImageCallback callback) {
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            return;
        }

        final String cleanEmail = email.trim().toLowerCase();
        final String hash = md5(cleanEmail);
        final File cacheFile = new File(context.getFilesDir(), CACHE_FILE_PREFIX + hash + ".png");

        // 1. Try disk cache first for instant rendering
        if (cacheFile.exists()) {
            try {
                Bitmap cached = BitmapFactory.decodeFile(cacheFile.getAbsolutePath());
                if (cached != null) {
                    final Bitmap circular = getCircularBitmap(cached);
                    if (targetView != null) {
                        targetView.setImageBitmap(circular);
                    }
                    if (callback != null) {
                        callback.onLoaded(circular);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Fetch fresh profile image from Gravatar / Google account
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = "https://www.gravatar.com/avatar/" + hash + "?s=256&d=identicon";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KR Habit)");

                    if (conn.getResponseCode() == 200) {
                        InputStream is = conn.getInputStream();
                        Bitmap downloaded = BitmapFactory.decodeStream(is);
                        is.close();
                        conn.disconnect();

                        if (downloaded != null) {
                            // Save to disk cache
                            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                                downloaded.compress(Bitmap.CompressFormat.PNG, 95, fos);
                            } catch (Exception ignored) {}

                            final Bitmap circular = getCircularBitmap(downloaded);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (targetView != null) {
                                        targetView.setImageBitmap(circular);
                                    }
                                    if (callback != null) {
                                        callback.onLoaded(circular);
                                    }
                                }
                            });
                        }
                    } else {
                        conn.disconnect();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        paint.setShader(shader);
        float r = size / 2f;
        canvas.drawCircle(r, r, r, paint);
        return output;
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
