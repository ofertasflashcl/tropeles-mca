package net.mca.entity.ai.sense;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Hands;
import net.mca.entity.ai.body.Mining;
import net.mca.entity.ai.mente.Recuerdos;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A single server-tick view. The image and block references come from the same exact rays. */
public final class VistaSituada {
    private static final int MAX_BLOQUES = 16;
    private static final int MAX_EDAD = 200;
    private static final int ESCALA = 6;
    private static final double ALCANCE_INSPECCION = 20;

    private record Bloque(BlockPos pos, BlockState estado, Direction cara, int x, int y, double distancia) {
        String id() {
            return Registries.BLOCK.getId(estado.getBlock()).toString();
        }
    }

    private final RegistryKey<World> mundo;
    private final long tick;
    private final BlockPos pies;
    private final Direction frente;
    private final float inclinacion;
    private final List<Bloque> bloques;
    private final List<String> cuerpos;
    private final boolean rayosConAgua;
    private final boolean sumergido;
    private final int aire;
    private final int aireMaximo;
    @Nullable
    private final int[] rgb;

    private VistaSituada(ServerWorld world, VillagerEntityMCA v, List<Bloque> bloques, List<String> cuerpos, boolean rayosConAgua, @Nullable int[] rgb) {
        mundo = world.getRegistryKey();
        tick = world.getTime();
        pies = Mining.pies(v).toImmutable();
        frente = Direction.fromRotation(v.getHeadYaw());
        inclinacion = v.getPitch();
        this.bloques = List.copyOf(bloques);
        this.cuerpos = List.copyOf(cuerpos);
        this.rayosConAgua = rayosConAgua;
        sumergido = v.isSubmergedInWater();
        aire = v.getAir();
        aireMaximo = v.getMaxAir();
        this.rgb = rgb;
    }

    /** World access is confined to the server thread. Encoding can then run with the model call. */
    public static VistaSituada captar(ServerWorld world, VillagerEntityMCA v, boolean imagen) {
        Retina.lookAll(world, v);
        VisualMemory memoria = Retina.memory(v);
        Retina.Pixel[] pixels = memoria.frame();
        int[] rgb = imagen ? new int[Retina.WIDTH * Retina.HEIGHT] : null;
        Map<BlockPos, Bloque> distintos = new HashMap<>();
        Map<Integer, String> cuerpos = new LinkedHashMap<>();
        boolean agua = false;
        for (int i = 0; i < pixels.length; i++) {
            Retina.Pixel p = pixels[i];
            if (p == null) {
                continue;
            }
            if (rgb != null) {
                rgb[i] = color(world, p);
            }
            agua |= p.water();
            if (p.pos() != null && p.state() != null && p.face() != null && p.distance() <= ALCANCE_INSPECCION && world.isChunkLoaded(p.pos())) {
                Bloque b = new Bloque(p.pos().toImmutable(), p.state(), p.face(), i % Retina.WIDTH, i / Retina.WIDTH, p.distance());
                distintos.merge(b.pos(), b, (a, next) -> a.distancia() <= next.distancia() ? a : next);
            }
            if (p.entity() != null && cuerpos.size() < 6) {
                cuerpos.putIfAbsent(p.entity().getId(), Registries.ENTITY_TYPE.getId(p.entity().getType()) + " at "
                        + p.entity().getBlockPos().toShortString());
            }
        }
        Retina.Focus foco = memoria.focus();
        BlockPos centrado = foco != null && foco.distance() <= ALCANCE_INSPECCION ? foco.pos() : null;
        if (centrado != null && foco.state() != null && foco.face() != null && world.isChunkLoaded(centrado)) {
            distintos.put(centrado, new Bloque(centrado.toImmutable(), foco.state(), foco.face(),
                    Retina.WIDTH / 2, Retina.HEIGHT / 2, foco.distance()));
        }
        List<Bloque> seleccion = distintos.values().stream().sorted(Comparator
                .comparing((Bloque b) -> !b.pos().equals(centrado))
                .thenComparingInt(b -> relevancia(b.estado()))
                .thenComparingDouble(Bloque::distancia)
                .thenComparingLong(b -> b.pos().asLong())).limit(MAX_BLOQUES).toList();
        return new VistaSituada(world, v, seleccion, new ArrayList<>(cuerpos.values()), agua, rgb);
    }

    private static int relevancia(BlockState s) {
        if (!s.getFluidState().isEmpty() || Mining.isOre(s) || s.hasBlockEntity()) return 0;
        var bloque = s.getBlock();
        return bloque instanceof StairsBlock || bloque instanceof SlabBlock || bloque instanceof LadderBlock
                || bloque instanceof DoorBlock || bloque instanceof TrapdoorBlock ? 1 : 2;
    }

    private static int color(ServerWorld world, Retina.Pixel p) {
        if (p.dark()) {
            return 0x15141c;
        }
        if (p.entity() != null) {
            return 0x988167;
        }
        if (p.state() == null || p.pos() == null) {
            return p.water() ? Foto.WATER : 0x9ab9dd;
        }
        BlockState s = p.state();
        FluidState f = s.getFluidState();
        int base = f.isIn(FluidTags.LAVA) ? Foto.LAVA : f.isIn(FluidTags.WATER) ? Foto.WATER : s.getMapColor(world, p.pos()).color;
        if (base == 0) {
            base = 0x8a8781;
        }
        if (p.water()) {
            base = ((base & 0xfefefe) >>> 1) + ((Foto.WATER & 0xfefefe) >>> 1);
        }
        double luz = (0.45 + 0.55 * Math.max(0, p.light()) / 15) * (p.face() == Direction.UP ? 1 : 0.83)
                * (1 - Math.min(0.3, p.distance() / 120));
        int red = (int) (((base >> 16) & 255) * luz);
        int green = (int) (((base >> 8) & 255) * luz);
        int blue = (int) ((base & 255) * luz);
        return red << 16 | green << 8 | blue;
    }

    public Direction frente() {
        return frente;
    }

    public boolean tieneBloques() {
        return !bloques.isEmpty();
    }

    public boolean vigente(ServerWorld world, VillagerEntityMCA v) {
        long edad = world.getTime() - tick;
        return mundo.equals(world.getRegistryKey()) && edad >= 0 && edad <= MAX_EDAD
                && pies.isWithinDistance(Mining.pies(v), 3.0);
    }

    public String paraModelo() {
        StringBuilder out = new StringBuilder("\nLive eye sample, tick ").append(tick).append(", dimension ").append(mundo.getValue())
                .append(", feet ").append(pies.toShortString()).append(", facing ").append(frente.asString())
                .append(", pitch ").append(Math.round(inclinacion)).append(". These observations are separate from older memories. ")
                .append("The optional image is a color and depth sketch of these same rays. Exact block IDs come from the list below. ")
                .append("A pixel that is dark or occluded does not identify anything. Inspect only a listed b reference. ")
                .append(rayosConAgua ? "Some rays crossed water. " : "");
        if (sumergido) {
            out.append("Your head was underwater with ").append(aire).append('/').append(aireMaximo).append(" air. ");
        }
        for (int i = 0; i < bloques.size(); i++) {
            Bloque b = bloques.get(i);
            out.append("\nb").append(i + 1).append(" = ").append(b.id()).append(" at ").append(b.pos().toShortString())
                    .append(" face ").append(b.cara().asString()).append(" distance ").append(Math.round(b.distancia() * 10) / 10.0)
                    .append(" blocks, pixel ").append(b.x()).append(',').append(b.y())
                    .append(" of ").append(Retina.WIDTH).append('x').append(Retina.HEIGHT);
            if (!b.estado().getProperties().isEmpty()) {
                String state = b.estado().toString();
                int bracket = state.indexOf('[');
                if (bracket >= 0) out.append(" state ").append(state.substring(bracket));
            }
        }
        if (!cuerpos.isEmpty()) {
            out.append("\nVisible entities: ").append(String.join(" | ", cuerpos));
        }
        return out.toString();
    }

    @Nullable
    public String imagen() {
        if (rgb == null) {
            return null;
        }
        int ancho = Retina.WIDTH * ESCALA;
        int alto = Retina.HEIGHT * ESCALA;
        int[] grande = new int[ancho * alto];
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                grande[y * ancho + x] = rgb[(y / ESCALA) * Retina.WIDTH + x / ESCALA];
            }
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(Foto.encode(grande, ancho, alto));
    }

    /** Resolves an observed reference again in the live world before turning the eyes or recording it. */
    public String inspeccionar(ServerWorld world, VillagerEntityMCA v, String referencia, boolean es) {
        String id = referencia.strip().toLowerCase(Locale.ROOT);
        int indice = id.matches("b[1-9][0-9]?") ? Integer.parseInt(id.substring(1)) - 1 : -1;
        if (indice < 0 || indice >= bloques.size() || !vigente(world, v)) {
            return es ? "Necesito mirar otra vez para identificar ese bloque." : "I need a fresh look to identify that block.";
        }
        Bloque b = bloques.get(indice);
        BlockHitResult cara = world.isChunkLoaded(b.pos()) && world.getBlockState(b.pos()).equals(b.estado())
                ? Retina.visibleFace(world, v, b.pos(), ALCANCE_INSPECCION) : null;
        String perdido = es ? "Ese bloque ya cambió o perdí la vista de él." : "That block changed or I lost sight of it.";
        if (cara == null || !Retina.perceives(Retina.lightAt(world, b.pos(), cara.getSide(), b.estado()),
                Retina.eye(v).distanceTo(cara.getPos()), Retina.nightVision(v))) {
            return perdido;
        }
        Hands.aim(v, cara.getPos());
        Retina.lookAll(world, v);
        Retina.Focus foco = Retina.memory(v).focus();
        if (foco == null || !b.pos().equals(foco.pos()) || !b.estado().equals(foco.state())) {
            return perdido;
        }
        Recuerdos.anotar(v, "vista", "vi " + b.id() + " en " + b.pos().toShortString(), b.pos());
        String pos = b.pos().getX() + " " + b.pos().getY() + " " + b.pos().getZ();
        return es ? "Estoy mirando " + b.estado().getBlock().getName().getString() + " en " + pos + "."
                : "I'm looking at " + b.estado().getBlock().getName().getString() + " at " + pos + ".";
    }
}
