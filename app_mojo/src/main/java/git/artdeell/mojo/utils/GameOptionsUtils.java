package git.artdeell.mojo.utils;

import android.content.Context;
import android.util.Log;

import git.artdeell.mojo.prefs.PerformanceProfile;

public class GameOptionsUtils {

    public static int parseIntDefault(String value, int defaultValue) {
        if (value == null) return defaultValue;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void fixDeathCloud() {
        GpuUtils.GLInfo info = GpuUtils.getGlInfo();
        if (!info.isArm()) return;

        int cloudRange = parseIntDefault(
                MCOptionUtils.get("cloudRange"), 128);

        if (cloudRange <= 64) return;

        MCOptionUtils.set("cloudRange", "64");
    }

    private static void disableNarrator() {
        if (parseIntDefault(MCOptionUtils.get("narrator"), 0) == 0) {
            return;
        }

        MCOptionUtils.set("narrator", "0");
    }

    private static void disableFullscreen() {
        String fullscreen = MCOptionUtils.get("fullscreen");

        if ("true".equals(fullscreen)) {
            MCOptionUtils.set("fullscreen", "false");
        } else if ("1".equals(fullscreen)) {
            MCOptionUtils.set("fullscreen", "0");
        }
    }

    private static void applyPerformanceProfile(Context context) {

        String profile = PerformanceProfile.getActiveProfile(
                context.getApplicationContext());

        int renderDistance;

        switch (profile) {
            case PerformanceProfile.LOW:
                renderDistance = 5;
                break;

            case PerformanceProfile.PERFORMANCE:
                renderDistance = 12;
                break;

            default:
                renderDistance = 8;
                break;
        }

        // Only change the setting if Minecraft already supports it.
        if (MCOptionUtils.get("renderDistance") != null) {
            MCOptionUtils.set(
                    "renderDistance",
                    String.valueOf(renderDistance));
        }

        Log.i("JarPerformance",
                "Active profile: " + profile
                        + ", render distance: " + renderDistance);
    }

    public static void fixOptions(boolean isLtw, Context context) {

        try {
            MCOptionUtils.load();
        } catch (Exception e) {
            Log.e("Tools", "Failed to load config", e);
        }

        if (isLtw) {
            fixDeathCloud();
        }

        disableFullscreen();
        disableNarrator();

        applyPerformanceProfile(context);

        try {
            MCOptionUtils.save();
        } catch (Exception e) {
            Log.e("Tools", "Failed to save config", e);
        }
    }
        }
