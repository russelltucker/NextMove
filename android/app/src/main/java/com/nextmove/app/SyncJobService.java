package com.nextmove.app;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class SyncJobService extends JobService {
    private volatile Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            boolean retry = false;
            try {
                sync();
            } catch (Exception e) {
                retry = true;
                SharedPreferences sp = getSharedPreferences("nextmove_sync", MODE_PRIVATE);
                sp.edit()
                        .putString("last_error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()))
                        .putLong("last_attempt_ms", System.currentTimeMillis())
                        .apply();
            } finally {
                jobFinished(params, retry);
            }
        }, "NextMoveBackgroundSync");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return true;
    }

    private void sync() throws Exception {
        SharedPreferences sp = getSharedPreferences("nextmove_sync", MODE_PRIVATE);
        String base = sp.getString("url", "").replaceAll("/+$", "");
        String key = sp.getString("key", "");
        String sessionText = sp.getString("session", "");
        if (base.isEmpty() || key.isEmpty() || sessionText.isEmpty()) return;

        JSONObject session = new JSONObject(sessionText);
        long expiresAt = session.optLong("expires_at", 0L) * 1000L;
        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt - 120000L) {
            String refresh = session.optString("refresh_token", "");
            if (refresh.isEmpty()) throw new IOException("Supabase session expired with no refresh token");
            JSONObject refreshed = new JSONObject(request(
                    base + "/auth/v1/token?grant_type=refresh_token",
                    "POST", key, "",
                    new JSONObject().put("refresh_token", refresh).toString()));
            session = refreshed;
            sp.edit().putString("session", refreshed.toString()).apply();
        }

        String token = session.optString("access_token", "");
        if (token.isEmpty()) throw new IOException("No Supabase access token");

        String response = request(
                base + "/rest/v1/nextmove_sync?select=payload,client_updated_at&limit=1",
                "GET", key, token, null);
        JSONArray rows = new JSONArray(response);
        if (rows.length() == 0) {
            cancelRemovedReminders(sp, new HashSet<>());
            sp.edit().putString("scheduled_ids", "[]").apply();
            markSuccess(sp, "Cloud has no NextMove payload yet");
            return;
        }

        JSONObject row = rows.getJSONObject(0);
        JSONObject payload = row.optJSONObject("payload");
        if (payload == null) throw new IOException("Cloud payload is missing");

        Set<String> activeIds = scheduleCloudReminders(payload);
        cancelRemovedReminders(sp, activeIds);

        JSONArray savedIds = new JSONArray();
        for (String id : activeIds) savedIds.put(id);
        sp.edit()
                .putString("scheduled_ids", savedIds.toString())
                .putString("last_payload", payload.toString())
                .putString("last_remote_updated_at", row.optString("client_updated_at", ""))
                .apply();
        markSuccess(sp, "Background synced " + activeIds.size() + " active reminder task(s)");
    }

    private Set<String> scheduleCloudReminders(JSONObject payload) throws Exception {
        Set<String> active = new HashSet<>();
        JSONArray tasks = payload.optJSONArray("tasks");
        if (tasks == null) return active;

        JSONObject projectNames = new JSONObject();
        JSONArray projects = payload.optJSONArray("projects");
        if (projects != null) {
            for (int i = 0; i < projects.length(); i++) {
                JSONObject p = projects.optJSONObject(i);
                if (p != null) projectNames.put(p.optString("id", ""), p.optString("name", ""));
            }
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            String id = task.optString("id", "");
            if (id.isEmpty()) continue;

            boolean enabled = !task.optBoolean("completed", false)
                    && !task.optString("dueAt", "").isEmpty()
                    && task.optInt("reminderBefore", -1) >= 0;
            if (!enabled) {
                MainActivity.cancelNative(this, id);
                continue;
            }

            long dueMillis;
            try {
                dueMillis = Instant.parse(task.getString("dueAt")).toEpochMilli();
            } catch (Exception badDate) {
                MainActivity.cancelNative(this, id);
                continue;
            }

            long reminderBeforeMinutes = Math.max(0L, task.optLong("reminderBefore", 0L));
            long repeatMinutes = Math.max(0L, task.optLong("repeatMins", 0L));
            long first = dueMillis - reminderBeforeMinutes * 60000L;
            long interval = repeatMinutes * 60000L;

            if (first <= now && interval > 0L) {
                long elapsed = now - first;
                long jumps = elapsed / interval + 1L;
                first += jumps * interval;
            } else if (first <= now) {
                first = now + 1500L;
            }

            String project = projectNames.optString(task.optString("projectId", ""), "");
            String assignee = task.optString("assignee", "");
            String body = project;
            if (!assignee.isEmpty()) body += (body.isEmpty() ? "" : " • ") + "Assigned: " + assignee;

            MainActivity.scheduleNative(first, interval, this, id,
                    task.optString("title", "NextMove task"), body);
            active.add(id);
        }
        return active;
    }

    private void cancelRemovedReminders(SharedPreferences sp, Set<String> activeIds) {
        try {
            JSONArray previous = new JSONArray(sp.getString("scheduled_ids", "[]"));
            for (int i = 0; i < previous.length(); i++) {
                String id = previous.optString(i, "");
                if (!id.isEmpty() && !activeIds.contains(id)) MainActivity.cancelNative(this, id);
            }
        } catch (Exception ignored) { }
    }

    private void markSuccess(SharedPreferences sp, String message) {
        long now = System.currentTimeMillis();
        sp.edit()
                .putLong("last_success_ms", now)
                .putLong("last_attempt_ms", now)
                .putString("last_status", message)
                .remove("last_error")
                .apply();
    }

    private String request(String url, String method, String key, String token, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("apikey", key);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = "";
        if (input != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) builder.append(line);
                text = builder.toString();
            }
        }
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + ": " + text);
        return text;
    }
}
