package net.mca.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.mca.entity.ai.obra.Plano;
import net.mca.entity.ai.obra.Propuestas;
import net.mca.network.s2c.FantasmaMessage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The plans of the buildings under way near the player, drawn see-through over the world the way "The Forest"
 * shows a structure before it is built: every block still to be set in its real look, pale and breathing; a block
 * of the plan where something else stands now, reddish; whatever is in the way where the house must be empty
 * (dirt, leaves, a trunk), tinted red over itself. What is already built is not drawn. A house only proposed, waiting
 * for the player's yes, is drawn golden instead of pale. Client render thread.
 */
public final class Fantasmas {
    private record Fantasma(String dim, BlockPos[] pos, BlockState[] states, Plano.Rol[] roles, Vec3d centro, boolean propuesta) {
    }

    private static final Map<String, Fantasma> TODOS = new ConcurrentHashMap<>();
    private static final double VER = 128.0;
    private static final int MAX_BLOQUES = 6000;
    /** The connection the ghosts came over: another server (or a new session) starts with none. */
    private static Object conexion;

    private Fantasmas() {
    }

    public static void recibir(FantasmaMessage m) {
        nuevaConexion();
        if (m.borrar) {
            TODOS.remove(m.id);
            return;
        }
        int n = m.xs.length;
        if (n == 0 || m.ys.length != n || m.zs.length != n || m.estados.length != n || m.roles.length != n) {
            return;
        }
        BlockPos[] pos = new BlockPos[n];
        BlockState[] states = new BlockState[n];
        Plano.Rol[] roles = new Plano.Rol[n];
        Plano.Rol[] all = Plano.Rol.values();
        double sx = 0;
        double sy = 0;
        double sz = 0;
        for (int i = 0; i < n; i++) {
            pos[i] = new BlockPos(m.xs[i], m.ys[i], m.zs[i]);
            states[i] = Block.getStateFromRawId(m.estados[i]);
            int r = m.roles[i];
            roles[i] = r >= 0 && r < all.length ? all[r] : Plano.Rol.AIRE;
            sx += m.xs[i] + 0.5;
            sy += m.ys[i] + 0.5;
            sz += m.zs[i] + 0.5;
        }
        TODOS.put(m.id, new Fantasma(m.dim, pos, states, roles, new Vec3d(sx / n, sy / n, sz / n), m.id.startsWith(Propuestas.PREFIJO)));
    }

    private static void nuevaConexion() {
        Object h = MinecraftClient.getInstance().getNetworkHandler();
        if (h != conexion) {
            conexion = h;
            TODOS.clear();
        }
    }

    /** Called at the end of the world's render, with the camera-rotated matrices. */
    public static void render(MatrixStack matrices, Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return;
        }
        nuevaConexion();
        if (TODOS.isEmpty()) {
            return;
        }
        String dim = world.getRegistryKey().getValue().toString();
        Vec3d cam = camera.getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        RenderLayer layer = TexturedRenderLayers.getEntityTranslucentCull();
        VertexConsumer vc = immediate.getBuffer(layer);
        BlockRenderManager blocks = client.getBlockRenderManager();
        double t = (System.currentTimeMillis() % 2400L) / 2400.0;
        float alpha = 0.30F + 0.08F * MathHelper.sin((float) (t * Math.PI * 2.0));
        int drawn = 0;
        for (Fantasma f : TODOS.values()) {
            if (!f.dim().equals(dim) || f.centro().distanceTo(cam) > VER) {
                continue;
            }
            for (int i = 0; i < f.pos().length && drawn < MAX_BLOQUES; i++) {
                Plano.Rol rol = f.roles()[i];
                BlockPos p = f.pos()[i];
                BlockState actual = world.getBlockState(p);
                BlockState draw;
                float r;
                float g;
                float b;
                float scale;
                if (rol == Plano.Rol.AIRE) {
                    // must be empty: what is there now and will go, in red over itself
                    if (actual.isAir() || actual.isReplaceable() || actual.getCollisionShape(world, p).isEmpty()) {
                        continue;
                    }
                    draw = actual;
                    r = 1.0F;
                    g = 0.25F;
                    b = 0.25F;
                    scale = 1.004F;
                } else {
                    if (Plano.hecho(rol, f.states()[i], actual)) {
                        continue;
                    }
                    draw = f.states()[i];
                    boolean blocked = !actual.isAir() && !actual.isReplaceable();
                    r = blocked ? 1.0F : f.propuesta() ? 1.0F : 0.70F;
                    g = blocked ? 0.45F : f.propuesta() ? 0.84F : 0.92F;
                    b = blocked ? 0.40F : f.propuesta() ? 0.35F : 1.0F;
                    scale = 0.98F;
                }
                if (draw.getRenderType() != BlockRenderType.MODEL) {
                    continue; // a chest and the like have no block model to draw
                }
                BakedModel model = blocks.getModel(draw);
                matrices.push();
                float off = (1.0F - scale) / 2.0F;
                matrices.translate(p.getX() - cam.x + off, p.getY() - cam.y + off, p.getZ() - cam.z + off);
                matrices.scale(scale, scale, scale);
                blocks.getModelRenderer().render(matrices.peek(), vc, draw, model, r, g, b, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                matrices.pop();
                drawn++;
            }
        }
        if (drawn == 0) {
            immediate.draw(layer);
            return;
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        try {
            immediate.draw(layer);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
