package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSSettings;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rewrites the video encoder part of the ffmpeg arguments according to the
 * codec / hardware encoder settings. With "CPU" + "H.264" the user's arguments
 * are left untouched, so custom argument strings keep working as before.
 *
 * <p>Based on BBS Lezy's LezyVideoSettingsHelper / LezyEncoderProbe by NotLeji (MIT License).</p>
 */
public class VideoEncoders
{
    public static final int CODEC_H264 = 0;
    public static final int CODEC_HEVC = 1;
    public static final int CODEC_AV1 = 2;

    public static final int ENCODER_AUTO = 0;
    public static final int ENCODER_CPU = 1;
    public static final int ENCODER_NVIDIA = 2;
    public static final int ENCODER_AMD = 3;
    public static final int ENCODER_INTEL = 4;

    private static final String[] CODEC_NAMES = {"h264", "hevc", "av1"};
    private static final String[] CPU_ENCODERS = {"libx264", "libx265", "libsvtav1"};
    private static final String[] HW_SUFFIXES = {"_nvenc", "_amf", "_qsv"};

    /* Key is "ffmpeg path|encoder name", value is whether a test encode succeeded */
    private static final Map<String, Boolean> probed = new HashMap<>();

    /**
     * Must be called on the render thread (Auto mode reads the GL vendor).
     */
    public static String apply(String params)
    {
        int codec = BBSSettings.videoCodec.get();
        int encoder = BBSSettings.videoHardwareEncoder.get();

        if (codec == CODEC_H264 && encoder == ENCODER_CPU)
        {
            return params;
        }

        int quality = BBSSettings.videoQuality.get();
        String name = resolveEncoder(codec, encoder);
        String encoderArgs = getEncoderArgs(name, quality);

        params = params
            .replaceAll("-preset \\S+", "")
            .replaceAll("-tune \\S+", "")
            .replaceAll("-qp \\d+", "")
            .replaceAll("-crf \\d+", "")
            .replaceAll("-c:v \\S+", encoderArgs);

        if (name.endsWith("_qsv"))
        {
            /* QSV only takes NV12, the conversion is done by ffmpeg either way */
            params = params.replace("-pix_fmt yuv420p", "-pix_fmt nv12");
        }

        System.out.println("Video encoder: " + name);

        return params.replaceAll("\\s+", " ").trim();
    }

    private static String resolveEncoder(int codec, int encoder)
    {
        String codecName = CODEC_NAMES[codec];
        String cpu = CPU_ENCODERS[codec];

        if (encoder == ENCODER_CPU)
        {
            return cpu;
        }

        List<String> candidates = new ArrayList<>();

        if (encoder != ENCODER_AUTO)
        {
            candidates.add(codecName + HW_SUFFIXES[encoder - ENCODER_NVIDIA]);
        }
        else
        {
            String vendor = getGLVendor();
            int preferred = vendor.contains("nvidia") ? 0
                : vendor.contains("amd") || vendor.contains("radeon") ? 1
                : vendor.contains("intel") ? 2 : 0;

            /* Try the GPU that renders the game first, then the rest (e.g. Intel iGPU next to a dGPU) */
            candidates.add(codecName + HW_SUFFIXES[preferred]);

            for (int i = 0; i < HW_SUFFIXES.length; i++)
            {
                if (i != preferred)
                {
                    candidates.add(codecName + HW_SUFFIXES[i]);
                }
            }
        }

        for (String candidate : candidates)
        {
            if (isAvailable(candidate))
            {
                return candidate;
            }
        }

        System.err.println("No working hardware encoder among " + candidates + ", falling back to " + cpu);

        return cpu;
    }

    private static String getEncoderArgs(String name, int quality)
    {
        String args;

        if (name.endsWith("_nvenc"))
        {
            args = "-c:v " + name + " -preset p4 -rc vbr -cq " + quality + " -b:v 0";
        }
        else if (name.endsWith("_amf"))
        {
            args = "-c:v " + name + " -quality balanced -rc cqp -qp_i " + quality + " -qp_p " + quality;
        }
        else if (name.endsWith("_qsv"))
        {
            args = "-c:v " + name + " -preset medium -global_quality " + quality;
        }
        else if (name.equals("libsvtav1"))
        {
            args = "-c:v libsvtav1 -preset 10 -crf " + quality;
        }
        else
        {
            args = "-c:v " + name + " -preset ultrafast -crf " + quality;
        }

        return name.startsWith("hevc") || name.equals("libx265") ? args + " -tag:v hvc1" : args;
    }

    /**
     * Listing in "ffmpeg -encoders" isn't enough (builds ship NVENC/AMF/QSV even
     * without the GPU or driver), so do a tiny test encode once and cache it.
     */
    private static synchronized boolean isAvailable(String encoder)
    {
        String ffmpeg = FFMpegUtils.getFFMPEG();

        return probed.computeIfAbsent(ffmpeg + "|" + encoder, (k) -> testEncode(ffmpeg, encoder));
    }

    private static boolean testEncode(String ffmpeg, String encoder)
    {
        try
        {
            Process process = new ProcessBuilder(
                ffmpeg, "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "color=black:s=256x256:r=30",
                "-frames:v", "5", "-pix_fmt", "nv12", "-c:v", encoder, "-f", "null", "-"
            ).redirectErrorStream(true).start();

            process.getInputStream().readAllBytes();

            if (!process.waitFor(10, TimeUnit.SECONDS))
            {
                process.destroyForcibly();

                return false;
            }

            return process.exitValue() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static String getGLVendor()
    {
        try
        {
            return (GL11.glGetString(GL11.GL_VENDOR) + " " + GL11.glGetString(GL11.GL_RENDERER)).toLowerCase();
        }
        catch (Throwable e)
        {
            return "";
        }
    }
}
