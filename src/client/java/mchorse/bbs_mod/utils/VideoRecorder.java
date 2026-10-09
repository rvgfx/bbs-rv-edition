package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.UIUtils;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class VideoRecorder
{
    private static final Link RENDER_COMPLETE_SOUND = Link.assets("sounds/render_complete.ogg");

    /**
     * How many frames may wait for ffmpeg before the render thread blocks. Keeps rendering
     * and encoding overlapped; memory cost is FRAME_QUEUE * width * height * 3 bytes.
     */
    private static final int FRAME_QUEUE = 4;
    private static final ByteBuffer END_OF_STREAM = ByteBuffer.allocate(0);

    private Process process;
    private WritableByteChannel channel;
    private boolean recording;

    private BlockingQueue<ByteBuffer> freeFrames;
    private BlockingQueue<ByteBuffer> pendingFrames;
    private Thread writer;
    private VideoYUVConverter yuv;
    private int textureId = -1;
    private int textureWidth;
    private int textureHeight;
    private int counter;

    public int serverTicks;
    public int lastServerTicks;

    public boolean isRecording()
    {
        return this.recording;
    }

    public int getTextureId()
    {
        return this.textureId;
    }

    public int getCounter()
    {
        return this.counter;
    }

    private int[] pbos;
    private int pboIndex;

    /**
     * Start recording the video using ffmpeg
     */
    public void startRecording(String movieName, File audioFile, int textureId, int width, int height)
    {
        if (this.recording)
        {
            return;
        }

        this.counter = 0;
        this.textureId = textureId;
        this.textureWidth = width;
        this.textureHeight = height;

        int size = width * height * 3;

        try
        {
            File movies = BBSRendering.getVideoFolder();

            movies.mkdirs();

            Path path = Paths.get(movies.toString());

            if (movieName == null || movieName.isEmpty())
            {
                movieName = StringUtils.createTimestampFilename();
            }

            String params = audioFile == null
                ? BBSSettings.videoArguments.get()
                : BBSSettings.videoArgumentsAudio.get();

            params = VideoEncoders.apply(params);

            /* GPU colour conversion needs the stock bgr24 input and filter tokens to swap out */
            if (BBSSettings.videoGpuColorConversion.get()
                && VideoYUVConverter.supports(width, height)
                && params.contains("-pix_fmt bgr24")
                && params.contains("-vf %FILTERS%"))
            {
                this.yuv = new VideoYUVConverter(width, height);

                if (!this.yuv.isValid())
                {
                    this.yuv.delete();
                    this.yuv = null;
                }
            }

            if (this.yuv != null)
            {
                params = params
                    .replace("-pix_fmt bgr24", "-pix_fmt yuv420p")
                    .replace("-vf %FILTERS%", "-vf %FILTERS% -colorspace bt709 -color_primaries bt709 -color_trc bt709 -color_range tv");
                size = VideoYUVConverter.getFrameSize(width, height);
            }

            /* The YUV shader already flips, "null" keeps -vf valid without motion blur */
            StringBuilder filters = new StringBuilder(this.yuv == null ? "vflip" : "null");
            float frameRate = (float) BBSRendering.getVideoFrameRate();

            int motionBlur = BBSRendering.getMotionBlur();

            for (int i = 0; i < motionBlur; i++)
            {
                filters.append(",tblend=all_mode=average,framestep=2");
            }

            List<String> args = new ArrayList<>();
            String encoder = FFMpegUtils.getFFMPEG();

            args.add(encoder);

            /* Tokens are substituted after splitting, so a movie name or an audio path
             * with spaces stays a single argument. ProcessBuilder passes quote characters
             * literally, so they must not be added around paths either. */
            for (String arg : params.split(" "))
            {
                if (arg.isEmpty())
                {
                    continue;
                }

                arg = arg.replace("%WIDTH%", String.valueOf(width));
                arg = arg.replace("%HEIGHT%", String.valueOf(height));
                arg = arg.replace("%FPS%", String.valueOf(frameRate));
                arg = arg.replace("%NAME%", movieName);
                arg = arg.replace("%FILTERS%", filters.toString());

                if (audioFile != null)
                {
                    arg = arg.replace("%AUDIO_TRACK%", audioFile.getAbsolutePath());
                }

                args.add(arg);
            }

            System.out.println("Recording video with following arguments: " + args);

            /**
             * macOS reads the frame synchronously into a pooled frame buffer (see
             * {@link #recordFrameDirect()}); the asynchronous PBO pipeline below misbehaves
             * there and produces pitch-black footage, so we only set it up off macOS.
             */
            if (OS.CURRENT == OS.MACOS)
            {
                this.pbos = null;
            }
            else
            {
                this.pbos = new int[2];
                this.pboIndex = 0;

                for (int i = 0; i < 2; i++)
                {
                    this.pbos[i] = GL30.glGenBuffers();

                    GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[i]);
                    GL30.glBufferData(GL30.GL_PIXEL_PACK_BUFFER, size, GL30.GL_STREAM_READ);
                }

                GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);
            }

            ProcessBuilder builder = new ProcessBuilder(args);
            File log = path.resolve(movieName.concat(".log")).toFile();

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            builder.directory(path.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            this.process = builder.start();

            /**
             * Java wraps the process output stream into a BufferedOutputStream,
             *
             * but its little buffer is just slowing everything down with the
             * huge amount of data we're dealing here, so unwrap it with this little
             * hack.
             */
            OutputStream os = this.process.getOutputStream();
            Unsafe unsafe = UnsafeUtils.getUnsafe();

            if (os instanceof FilterOutputStream)
            {
                try
                {
                    Field outField = FilterOutputStream.class.getDeclaredField("out");

                    os = (OutputStream) unsafe.getObject(os, unsafe.objectFieldOffset(outField));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            this.channel = Channels.newChannel(os);
            this.freeFrames = new ArrayBlockingQueue<>(FRAME_QUEUE);
            this.pendingFrames = new ArrayBlockingQueue<>(FRAME_QUEUE + 1);

            for (int i = 0; i < FRAME_QUEUE; i++)
            {
                this.freeFrames.add(MemoryUtil.memAlloc(size));
            }

            this.recording = true;
            this.writer = new Thread(this::writeFrames, "BBS video writer");
            this.writer.setDaemon(true);
            this.writer.start();

            UIUtils.playClick(2F);
        }
        catch (Exception e)
        {
            e.printStackTrace();

            if (this.yuv != null)
            {
                this.yuv.delete();
                this.yuv = null;
            }
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * Stop recording
     */
    public void stopRecording()
    {
        this.stopRecording(true);
    }

    /**
     * Stop recording. With {@code finishEffects} false the completion sound and the
     * folder opening are skipped - the caller runs {@link #playFinishEffects()} itself
     * once the file is actually final (audio post pass).
     */
    public void stopRecording(boolean finishEffects)
    {
        if (!this.recording)
        {
            return;
        }

        if (this.pbos != null)
        {
            for (int pbo : this.pbos)
            {
                GL30.glDeleteBuffers(pbo);
            }
        }

        this.pbos = null;
        this.textureId = -1;

        if (this.yuv != null)
        {
            this.yuv.delete();
            this.yuv = null;
        }

        if (this.writer != null)
        {
            try
            {
                this.pendingFrames.put(END_OF_STREAM);
                this.writer.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            this.writer = null;
        }

        if (this.freeFrames != null)
        {
            for (ByteBuffer frame : this.freeFrames)
            {
                MemoryUtil.memFree(frame);
            }

            this.freeFrames = null;
            this.pendingFrames = null;
        }

        try
        {
            if (this.channel != null && this.channel.isOpen())
            {
                this.channel.close();
            }

            this.channel = null;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        try
        {
            if (this.process != null)
            {
                this.process.waitFor(1, TimeUnit.MINUTES);
                this.process.destroy();
            }

            this.process = null;
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }

        this.recording = false;

        if (finishEffects)
        {
            this.playFinishEffects();
        }

        this.serverTicks = this.lastServerTicks = 0;
    }

    /**
     * The end-of-export feedback (completion sound, opening the movies folder).
     */
    public void playFinishEffects()
    {
        if (BBSSettings.videoPlaySoundAfterExport.get())
        {
            if (BBSModClient.getSounds().play(RENDER_COMPLETE_SOUND) == null)
            {
                UIUtils.playClick(0.5F);
            }
        }

        if (BBSSettings.videoOpenFolderAfterExport.get())
        {
            File folder = BBSRendering.getVideoFolder();
            MinecraftClient.getInstance().execute(() -> UIUtils.openFolder(folder));
        }
    }

    /**
     * Record a frame
     */
    public void recordFrame()
    {
        if (!this.recording)
        {
            return;
        }

        if (OS.CURRENT == OS.MACOS)
        {
            this.recordFrameDirect();
        }
        else
        {
            this.recordFramePBO();
        }

        this.counter += 1;
    }

    /**
     * Asynchronous read-back path (Windows/Linux): {@code glGetTexImage} into a ping-pong
     * pair of pixel pack buffers, mapping the previously filled buffer to overlap GPU
     * read-back with the CPU-side write to ffmpeg.
     */
    private void recordFramePBO()
    {
        try
        {
            int pbo = this.pboIndex;
            int nextPbo = (this.pboIndex + 1) % this.pbos.length;

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[pbo]);
            this.readTexture(0L);

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[nextPbo]);

            ByteBuffer mappedBuffer = GL30.glMapBuffer(GL30.GL_PIXEL_PACK_BUFFER, GL30.GL_READ_ONLY);

            if (mappedBuffer != null && this.counter != 0)
            {
                ByteBuffer frame = this.freeFrames.take();

                frame.clear();
                frame.put(mappedBuffer);
                frame.flip();
                this.pendingFrames.put(frame);
            }

            GL30.glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);
            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

            this.pboIndex = nextPbo;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Synchronous read-back path (macOS): {@code glGetTexImage} into a pooled frame
     * and write it to ffmpeg. Simpler and stalls the render thread, but avoids the
     * pixel-pack-buffer path that renders black on macOS.
     */
    private void recordFrameDirect()
    {
        try
        {
            ByteBuffer frame = this.freeFrames.take();

            frame.clear();
            this.readTexture(MemoryUtil.memAddress(frame));
            this.pendingFrames.put(frame);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Read the frame either into the bound pixel pack buffer (address is an offset)
     * or straight into memory. With the YUV converter it's 1.5 bytes per pixel of
     * ready yuv420p, otherwise bgr24 that ffmpeg flips and converts itself.
     */
    private void readTexture(long address)
    {
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);

        if (this.yuv != null)
        {
            this.yuv.convert(this.textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.yuv.getTexture());
            GL11.nglGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, address);
        }
        else
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
            GL11.nglGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGR, GL11.GL_UNSIGNED_BYTE, address);
        }
    }

    /**
     * Writer thread: pushes queued frames into ffmpeg's stdin so the render thread
     * doesn't wait on the pipe. If ffmpeg dies, frames are still drained back into
     * the pool so the render thread can never deadlock on {@link #freeFrames}.
     */
    private void writeFrames()
    {
        boolean failed = false;

        while (true)
        {
            ByteBuffer frame;

            try
            {
                frame = this.pendingFrames.take();
            }
            catch (InterruptedException e)
            {
                return;
            }

            if (frame == END_OF_STREAM)
            {
                return;
            }

            if (!failed)
            {
                try
                {
                    this.channel.write(frame);
                }
                catch (IOException e)
                {
                    failed = true;
                    e.printStackTrace();
                }
            }

            this.freeFrames.add(frame);
        }
    }

    /**
     * Toggle recording of the video
     */
    public void toggleRecording(int textureId, int textureWidth, int textureHeight)
    {
        if (this.recording)
        {
            this.stopRecording();
        }
        else
        {
            this.startRecording(StringUtils.createTimestampFilename(), null, textureId, textureWidth, textureHeight);
        }

        UIUtils.playClick();
    }
}