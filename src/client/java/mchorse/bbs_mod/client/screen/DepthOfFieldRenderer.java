package mchorse.bbs_mod.client.screen;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.camera.clips.screen.DepthOfFieldEffect;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Depth of field pass for {@link mchorse.bbs_mod.camera.clips.screen.DepthOfFieldClip}.
 *
 * <p>Minecraft clears the depth buffer before drawing the hand, long before the screen effects run,
 * so {@link #captureDepth()} copies it (and the projection needed to turn it back into distances)
 * right after the world is drawn. With an Iris shader pack on, the main framebuffer's depth is not
 * the world's, so this pass is only meant for vanilla rendering — shader packs bring their own DoF.
 */
public class DepthOfFieldRenderer
{
    private static final String VERT = """
            #version 150

            in vec2 a_pos;
            in vec2 a_uv;
            out vec2 v_uv;

            void main()
            {
                v_uv = a_uv;
                gl_Position = vec4(a_pos, 0.0, 1.0);
            }
            """;

    /* Scatter as gather: a sample at radius r blurs into this pixel only when its own circle of
     * confusion reaches that far, and samples behind a sharper pixel are clamped so the background
     * doesn't bleed over an in-focus foreground. */
    private static final String FRAG = """
            #version 150

            #define SAMPLES 64
            #define GOLDEN_ANGLE 2.39996323

            in vec2 v_uv;
            out vec4 fragColor;

            uniform sampler2D u_color;
            uniform sampler2D u_depth;
            uniform vec3 u_proj;
            uniform float u_focus;
            uniform float u_range;
            uniform float u_strength;
            uniform vec2 u_texel;

            float distanceAt(vec2 uv)
            {
                float ndc = texture(u_depth, uv).r * 2.0 - 1.0;

                return u_proj.z != 0.0 ? u_proj.y / (ndc + u_proj.x) : (u_proj.y - ndc) / u_proj.x;
            }

            float cocAt(float d)
            {
                float off = max(abs(d - u_focus) - u_range, 0.0);

                return clamp(off / max(d, 0.001), 0.0, 1.0) * u_strength;
            }

            void main()
            {
                float centerDistance = distanceAt(v_uv);
                float centerCoc = cocAt(centerDistance);
                vec3 sum = texture(u_color, v_uv).rgb;
                float weight = 1.0;

                for (int i = 1; i < SAMPLES; i++)
                {
                    float r = sqrt(float(i) / float(SAMPLES)) * u_strength;
                    float a = float(i) * GOLDEN_ANGLE;
                    vec2 uv = v_uv + vec2(cos(a), sin(a)) * r * u_texel;
                    float d = distanceAt(uv);
                    float coc = cocAt(d);

                    if (d > centerDistance)
                    {
                        coc = min(coc, centerCoc * 2.0);
                    }

                    float w = smoothstep(r - 1.0, r + 1.0, coc);

                    sum += texture(u_color, uv).rgb * w;
                    weight += w;
                }

                fragColor = vec4(sum / weight, 1.0);
            }
            """;

    private static boolean initialized;
    private static boolean failed;
    private static int program;
    private static int vao;
    private static int vbo;
    private static Texture colorTex;
    private static Texture depthTex;
    private static boolean captured;
    private static float projM22;
    private static float projM32;
    private static float projM23;

    private static int uColor;
    private static int uDepth;
    private static int uProj;
    private static int uFocus;
    private static int uRange;
    private static int uStrength;
    private static int uTexel;

    /**
     * Called right after the world is drawn, before the hand clears the depth buffer.
     */
    public static void captureDepth()
    {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        if (depthTex == null)
        {
            depthTex = new Texture();
            depthTex.setFormat(TextureFormat.DEPTH_F24);
            depthTex.setFilter(GL11.GL_NEAREST);
            depthTex.setWrap(GL12.GL_CLAMP_TO_EDGE);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fb.fbo);
        depthTex.bind();

        if (depthTex.width != fb.textureWidth || depthTex.height != fb.textureHeight)
        {
            depthTex.setSize(fb.textureWidth, fb.textureHeight);
        }

        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fb.textureWidth, fb.textureHeight);
        depthTex.unbind();
        fb.beginWrite(false);

        projM22 = projection.m22();
        projM32 = projection.m32();
        projM23 = projection.m23();
        captured = true;
    }

    public static void apply(List<DepthOfFieldEffect> effects)
    {
        if (effects.isEmpty() || !captured)
        {
            return;
        }

        /* Overlapping clips: the strongest one wins, like the other screen effects' max() */
        DepthOfFieldEffect effect = effects.get(0);

        for (DepthOfFieldEffect e : effects)
        {
            if (e.strength > effect.strength)
            {
                effect = e;
            }
        }

        if (effect.strength <= 0F)
        {
            return;
        }

        if (!initialized)
        {
            init();
        }

        if (failed)
        {
            return;
        }

        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int fbW = fb.textureWidth;
        int fbH = fb.textureHeight;

        if (depthTex.width != fbW || depthTex.height != fbH)
        {
            return;
        }

        if (colorTex == null)
        {
            colorTex = new Texture();
            colorTex.setFormat(TextureFormat.RGB_U8);
            colorTex.setFilter(GL11.GL_LINEAR);
            colorTex.setWrap(GL12.GL_CLAMP_TO_EDGE);
        }

        fb.beginRead();
        colorTex.bind();

        if (colorTex.width != fbW || colorTex.height != fbH)
        {
            colorTex.setSize(fbW, fbH);
        }

        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, fbW, fbH);
        colorTex.unbind();
        fb.beginWrite(false);

        int[] prevViewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        GL11.glViewport(0, 0, fbW, fbH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

        GL20.glUseProgram(program);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        depthTex.bind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        colorTex.bind();

        GL20.glUniform1i(uColor, 0);
        GL20.glUniform1i(uDepth, 1);
        GL20.glUniform3f(uProj, projM22, projM32, projM23);
        GL20.glUniform1f(uFocus, effect.focus);
        GL20.glUniform1f(uRange, effect.range);
        /* Radius is authored at 1080p so a shot looks the same in the preview and the export */
        GL20.glUniform1f(uStrength, effect.strength * fbH / 1080F);
        GL20.glUniform2f(uTexel, 1F / fbW, 1F / fbH);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        depthTex.unbind();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        colorTex.unbind();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        fb.beginWrite(false);
    }

    private static int compile(int type, String source)
    {
        int shader = GL20.glCreateShader(type);

        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[DepthOfFieldRenderer] Shader failed:\n" + GL20.glGetShaderInfoLog(shader));
            GL20.glDeleteShader(shader);

            return 0;
        }

        return shader;
    }

    private static void init()
    {
        initialized = true;

        int vert = compile(GL20.GL_VERTEX_SHADER, VERT);
        int frag = compile(GL20.GL_FRAGMENT_SHADER, FRAG);

        if (vert == 0 || frag == 0)
        {
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            failed = true;

            return;
        }

        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glBindAttribLocation(program, 0, "a_pos");
        GL20.glBindAttribLocation(program, 1, "a_uv");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
        {
            System.err.println("[DepthOfFieldRenderer] Link failed:\n" + GL20.glGetProgramInfoLog(program));
            GL20.glDeleteProgram(program);
            failed = true;

            return;
        }

        uColor = GL20.glGetUniformLocation(program, "u_color");
        uDepth = GL20.glGetUniformLocation(program, "u_depth");
        uProj = GL20.glGetUniformLocation(program, "u_proj");
        uFocus = GL20.glGetUniformLocation(program, "u_focus");
        uRange = GL20.glGetUniformLocation(program, "u_range");
        uStrength = GL20.glGetUniformLocation(program, "u_strength");
        uTexel = GL20.glGetUniformLocation(program, "u_texel");

        /* Fullscreen quad VAO/VBO (NDC coords + UV), same as ColorGradeRenderer's */
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer buf = MemoryUtil.memAllocFloat(24);

        buf.put(new float[] {
            -1F, -1F,  0F, 0F,
             1F, -1F,  1F, 0F,
             1F,  1F,  1F, 1F,
            -1F, -1F,  0F, 0F,
             1F,  1F,  1F, 1F,
            -1F,  1F,  0F, 1F,
        }).flip();

        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        int stride = 4 * Float.BYTES;

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, (long) (2 * Float.BYTES));

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}
