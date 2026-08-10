package example.jawmsaddon;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/**
 * Complete fixture-local textured renderer. It snapshots actual entry state and
 * restores through GlStateManager so Minecraft's caches remain coherent.
 */
final class CacheSafeReplacementHud {

    private static final int BAR_WIDTH = 97;
    private static final int BAR_HEIGHT = 11;
    private static final int TEXTURE_SIZE = 16;

    private static ResourceLocation texture;

    private CacheSafeReplacementHud() {
    }

    static void render(ManaHudRenderEvent event, boolean failAfterDraw) {
        Minecraft minecraft = Minecraft.getMinecraft();
        RenderState state = RenderState.capture();
        try {
            state.enterFixtureState();

            ClientManaSnapshot snapshot = event.getSnapshot();
            int left = 4;
            int top = event.getScaledHeight() - 61;
            int innerWidth = BAR_WIDTH - 2;
            int fill = (int) Math.round(
                    innerWidth * snapshot.getCurrentMana()
                            / (double) snapshot.getMaximumMana()
            );

            Gui.drawRect(
                    left,
                    top,
                    left + BAR_WIDTH,
                    top + BAR_HEIGHT,
                    0xFFE7CFFF
            );
            Gui.drawRect(
                    left + 1,
                    top + 1,
                    left + BAR_WIDTH - 1,
                    top + BAR_HEIGHT - 1,
                    0xB0180828
            );
            if (fill > 0) {
                minecraft.getTextureManager().bindTexture(texture(minecraft));
                Gui.drawScaledCustomSizeModalRect(
                        left + 1,
                        top + 1,
                        0.0F,
                        0.0F,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE,
                        fill,
                        BAR_HEIGHT - 2,
                        TEXTURE_SIZE,
                        TEXTURE_SIZE
                );
            }

            if (failAfterDraw) {
                throw new FixtureRenderException("intentional post-draw failure");
            }

            minecraft.fontRenderer.drawStringWithShadow(
                    "API 1.3 MANA " + snapshot.getCurrentMana()
                            + "/" + snapshot.getMaximumMana(),
                    left,
                    top - 10,
                    0xFFFFCCFF
            );
        } finally {
            state.restore();
        }
    }

    private static ResourceLocation texture(Minecraft minecraft) {
        if (texture == null) {
            DynamicTexture dynamicTexture = new DynamicTexture(TEXTURE_SIZE, TEXTURE_SIZE);
            int[] pixels = dynamicTexture.getTextureData();
            for (int y = 0; y < TEXTURE_SIZE; y++) {
                for (int x = 0; x < TEXTURE_SIZE; x++) {
                    int alpha = 80 + (x * 144 / (TEXTURE_SIZE - 1));
                    int blue = 190 + (y * 65 / (TEXTURE_SIZE - 1));
                    int red = ((x + y) & 1) == 0 ? 45 : 95;
                    int green = ((x + y) & 1) == 0 ? 90 : 45;
                    pixels[y * TEXTURE_SIZE + x] =
                            (alpha << 24) | (red << 16) | (green << 8) | blue;
                }
            }
            dynamicTexture.updateDynamicTexture();
            texture = minecraft.getTextureManager().getDynamicTextureLocation(
                    "jawms_api_fixture_mana",
                    dynamicTexture
            );
        }
        return texture;
    }

    private static final class RenderState {

        private final boolean alphaEnabled;
        private final int alphaFunction;
        private final float alphaReference;
        private final boolean blendEnabled;
        private final int blendSourceRgb;
        private final int blendDestinationRgb;
        private final int blendSourceAlpha;
        private final int blendDestinationAlpha;
        private final boolean depthEnabled;
        private final boolean lightingEnabled;
        private final int activeTexture;
        private final boolean textureEnabled;
        private final int boundTexture;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;

        private boolean matrixPushed;

        private RenderState() {
            alphaEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
            blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            try {
                textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
                boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            } finally {
                GlStateManager.setActiveTexture(activeTexture);
            }

            // LWJGL 2 validates glGetFloat buffers against the largest value
            // the entry point can return, even when this pname returns only RGBA.
            FloatBuffer color = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
            red = color.get(0);
            green = color.get(1);
            blue = color.get(2);
            alpha = color.get(3);
        }

        static RenderState capture() {
            return new RenderState();
        }

        void enterFixtureState() {
            GlStateManager.pushMatrix();
            matrixPushed = true;
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        void restore() {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            try {
                if (matrixPushed) {
                    GlStateManager.popMatrix();
                    matrixPushed = false;
                }

                GlStateManager.alphaFunc(alphaFunction, alphaReference);
                setAlpha(alphaEnabled);
                GlStateManager.tryBlendFuncSeparate(
                        blendSourceRgb,
                        blendDestinationRgb,
                        blendSourceAlpha,
                        blendDestinationAlpha
                );
                setBlend(blendEnabled);
                setDepth(depthEnabled);
                setLighting(lightingEnabled);
                GlStateManager.bindTexture(boundTexture);
                setTexture(textureEnabled);
                GlStateManager.color(red, green, blue, alpha);
            } finally {
                GlStateManager.setActiveTexture(activeTexture);
            }
        }

        private static void setAlpha(boolean enabled) {
            if (enabled) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
        }

        private static void setBlend(boolean enabled) {
            if (enabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
        }

        private static void setDepth(boolean enabled) {
            if (enabled) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
        }

        private static void setLighting(boolean enabled) {
            if (enabled) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
        }

        private static void setTexture(boolean enabled) {
            if (enabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        }
    }
}

final class FixtureRenderException extends RuntimeException {

    FixtureRenderException(String message) {
        super(message);
    }
}
