package com.nextmove.app;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class BackgroundSync {
    private static final int JOB_ID = 18018;
    private static final long FIFTEEN_MINUTES = 15L * 60L * 1000L;

    private BackgroundSync() { }

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(FIFTEEN_MINUTES)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    public static void runSoon(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID + 1, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1000L)
                .setOverrideDeadline(30000L)
                .build();
        scheduler.schedule(job);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID);
            scheduler.cancel(JOB_ID + 1);
        }
    }

    public static void scheduleSafely(Context context) {
        try { schedule(context); } catch (Throwable ignored) { }
    }

    public static void runSoonSafely(Context context) {
        try { runSoon(context); } catch (Throwable ignored) { }
    }

    public static void cancelSafely(Context context) {
        try { cancel(context); } catch (Throwable ignored) { }
    }
}
