package mchorse.bbs_mod.utils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Converts the export texture to raw yuv420p (BT.709, limited range) on the GPU,
 * flipped to ffmpeg's top-down row order. The result lives in a single R8 texture
 * of width x (height * 3 / 2) whose bytes, read back row by row, are exactly the
 * Y, U and V planes ffmpeg expects, so ffmpeg needs neither swscale nor vflip and
 * the read-back is half the size of bgr24.
 */
public class VideoYUVConverter
{
    private static final String VERT = """
        #version 150

        void main()
        {
            vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);

            gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    private static final String FRAG = """
        #version 150

        uniform sampler2D u_source;
        uniform int u_width;
        uniform int u_height;

        out vec4 fragColor;

        /* Source row 0 is the bottom of the picture, ffmpeg wants the top first */
        vec3 fetch(int x, int row)
        {
            return texelFetch(u_source, ivec2(x, u_height - 1 - row), 0).rgb;
        }

        void main()
        {
            ivec2 p = ivec2(gl_FragCoord.xy);
            int i = p.y * u_width + p.x;
            int lumaSize = u_width * u_height;
            vec3 k = vec3(0.2126, 0.7152, 0.0722);
            float value;

            if (i < lumaSize)
            {
                value = 16.0 + 219.0 * dot(fetch(i % u_width, i / u_width), k);
            }
            else
            {
                int cw = u_width / 2;
                int chromaSize = cw * (u_height / 2);
                int j = i - lumaSize;
                int c = j % chromaSize;
                int x = (c % cw) * 2;
                int row = (c / cw) * 2;
                vec3 rgb = (fetch(x, row) + fetch(x + 1, row) + fetch(x, row + 1) + fetch(x + 1, row + 1)) * 0.25;
                float y = dot(rgb, k);

                value = j < chromaSize
                    ? 128.0 + 224.0 * (rgb.b - y) / 1.8556
                    : 128.0 + 224.0 * (rgb.r - y) / 1.5748;
            }

            fragColor = vec4(clamp(value, 0.0, 255.0) / 255.0);
        }
        """;

    private final int width;
    private final int height;
    private int program;
    private int vao;
    private int texture;
    private int framebuffer;

    /**
     * yuv420p needs even dimensions.
     */
    public static boolean supports(int width, int height)
    {
        return width % 2 == 0 && height % 2 == 0;
    }

    public static int getFrameSize(int width, int height)
    {
        return width * height * 3 / 2;
    }

    public VideoYUVConverter(int width, int height)
    {
        this.width = width;
        this.height = height;
        this.program = this.createProgram();
        this.vao = GL30.glGenVertexArrays();
        this.texture = GL11.glGenTextures();
        this.framebuffer = GL30.glGenFramebuffers();

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height * 3 / 2, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.texture, 0);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFramebuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
    }

    public boolean isValid()
    {
        return this.program != 0;
    }

    /**
     * Converts the source texture, leaving the result in {@link #getTexture()}.
     * Restores every piece of GL state it touches, so Minecraft's state cache stays valid.
     */
    public void convert(int sourceTexture)
    {
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer);
        GL11.glViewport(0, 0, this.width, this.height * 3 / 2);
        GL20.glUseProgram(this.program);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.program, "u_source"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.program, "u_width"), this.width);
        GL20.glUniform1i(GL20.glGetUniformLocation(this.program, "u_height"), this.height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
        GL30.glBindVertexArray(this.vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glBindVertexArray(prevVao);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture);
        GL13.glActiveTexture(prevActive);
        GL20.glUseProgram(prevProgram);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        this.restore(GL11.GL_BLEND, blend);
        this.restore(GL11.GL_DEPTH_TEST, depth);
        this.restore(GL11.GL_SCISSOR_TEST, scissor);
        this.restore(GL11.GL_CULL_FACE, cull);
    }

    private void restore(int capability, boolean enabled)
    {
        if (enabled)
        {
            GL11.glEnable(capability);
        }
    }

    public int getTexture()
    {
        return this.texture;
    }

    public void delete()
    {
        GL20.glDeleteProgram(this.program);
        GL30.glDeleteVertexArrays(this.vao);
        GL11.glDeleteTextures(this.texture);
        GL30.glDeleteFramebuffers(this.framebuffer);
    }

    private int createProgram()
    {
        int vert = this.compile(GL20.GL_VERTEX_SHADER, VERT);
        int frag = this.compile(GL20.GL_FRAGMENT_SHADER, FRAG);
        int program = 0;

        if (vert != 0 && frag != 0)
        {
            program = GL20.glCreateProgram();

            GL20.glAttachShader(program, vert);
            GL20.glAttachShader(program, frag);
            GL20.glLinkProgram(program);

            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            {
                System.err.println("[VideoYUVConverter] Link failed:\n" + GL20.glGetProgramInfoLog(program));
                GL20.glDeleteProgram(program);

                program = 0;
            }
        }

        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        return program;
    }

    private int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);

        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[VideoYUVConverter] Shader failed:\n" + GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);

            return 0;
        }

        return shader;
    }
}
