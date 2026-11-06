package git.artdeell.mojo.prefs;

import android.content.Context;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.utils.GpuUtils;

public final class PerformanceProfile {

    public static final String AUTOMATIC = "automatic";
    public static final String LOW = "low";
    public static final String BALANCED = "balanced";
    public static final String PERFORMANCE = "performance";

    private PerformanceProfile() {
    }

    public static String getActiveProfile(Context context) {
        String selected = LauncherPreferences.PREF_PERFORMANCE_PROFILE;

        if (LOW.equals(selected)
                || BALANCED.equals(selected)
                || PERFORMANCE.equals(selected)) {
            return selected;
        }

        return getAutomaticProfile(context);
    }

    private static String getAutomaticProfile(Context context) {

        long totalRam = Tools.getTotalDeviceMemory(context);

        // Support RAM values reported in bytes or MB.
        if (totalRam > 1024L * 1024L * 1024L) {
            totalRam /= 1024L * 1024L;
        }

        int cpuCores = Runtime.getRuntime().availableProcessors();

        GpuUtils.GLInfo gpu = GpuUtils.getGlInfo();

        boolean knownGpu = gpu.isAdreno() || gpu.isArm();

        if (totalRam <= 3072 || cpuCores <= 4
                || gpu.glesMajorVersion < 3) {
            return LOW;
        }

        if (totalRam >= 6144 && cpuCores >= 8 && knownGpu) {
            return PERFORMANCE;
        }

        return BALANCED;
    }
}
