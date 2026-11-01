package net.mca.mixin.client;

import net.mca.client.render.Fantasmas;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The see-through plans of the buildings under way, drawn once the world is ({@link Fantasmas}). */
@Mixin(WorldRenderer.class)
public abstract class MixinWorldRendererFantasma {
    @Unique
    private static boolean mca$fantasmaFallo;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN"))
    private void mca$fantasmas(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                               LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        try {
            Fantasmas.render(matrices, camera);
        } catch (RuntimeException e) {
            if (!mca$fantasmaFallo) {
                mca$fantasmaFallo = true; // said once; a frame is never lost to it
                net.mca.MCA.LOGGER.warn("[Tropeles] no se pudo dibujar el plano fantasma: {}", e.toString());
            }
        }
    }
}
