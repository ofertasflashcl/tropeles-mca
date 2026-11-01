package net.mca.entity.ai.obra;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.body.Explorar;
import net.mca.entity.ai.body.Hands;
import net.mca.entity.ai.body.Mining;
import net.mca.entity.ai.body.Motor;
import net.mca.entity.ai.body.Progreso;
import net.mca.entity.ai.body.Toques;
import net.mca.entity.ai.brain.tasks.chore.ProspectingTask;
import net.mca.entity.ai.mente.Recuerdos;
import net.mca.entity.ai.mente.Voluntad;
import net.mca.entity.ai.sense.Obras;
import net.mca.entity.ai.sense.Retina;
import net.mca.entity.ai.sense.VisualMemory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A villager putting a building up with its own hands, the way a player does, never a block it does not have and
 * never one it could not reach: it reads what is left from the world, picks the next block bottom up (the frame
 * layer by layer, the door, windows and lights whenever their wall is there), finds a spot to stand where its eye
 * sees a face to set it against (on the ground, on the floor it built, or on a pillar of dirt it climbs and takes
 * down again), walks there over what is really there, and sets it. Whatever it lacks it gets: logs from trees,
 * planks, sticks, stairs, a door and a chest at a crafting table it sets down, stone from a quarry staircase it
 * cuts with a pickaxe it made, sand it finds, glass and charcoal in a furnace of its own. A player may help: blocks
 * set in the plan count, and what is thrown near it is picked up. Server thread only.
 */
public final class Constructor {
    public enum Fin {SIGUE, LISTA}

    private enum Accion {PENSAR, IR, SUBIR, TRABAJAR, BAJAR, LENA, CANTERA, ARENA, MANO, ESPERAR}

    private enum Tipo {PIEZA, ESTORBO, RECOGER, ANDAMIO, MESA, HORNO, COFRE, LUGAR, RELLENO}

    private static final class Objetivo {
        final Tipo tipo;
        final BlockPos pos;
        @Nullable
        final Obra.Celda celda;

        Objetivo(Tipo tipo, BlockPos pos, @Nullable Obra.Celda celda) {
            this.tipo = tipo;
            this.pos = pos.toImmutable();
            this.celda = celda;
        }

        boolean romper() {
            return tipo == Tipo.ESTORBO || tipo == Tipo.RECOGER || tipo == Tipo.ANDAMIO;
        }
    }

    /** Where it stands to work: on {@code base}, or {@code alto} blocks up on a pillar it builds there. */
    private record Puesto(BlockPos base, int alto, @Nullable List<BlockPos> camino) {
        BlockPos pies() {
            return base.up(alto);
        }
    }

    /** What the world says is left of the building right now. */
    private static final class Lectura {
        final List<Obra.Celda> pendientes = new ArrayList<>();
        final List<Obra.Celda> estorbos = new ArrayList<>();
        final Map<Plano.Rol, Integer> faltan = new EnumMap<>(Plano.Rol.class);
        int techo = Integer.MAX_VALUE;
        int hechas;
        int total;
        int bloqueadas;

        int f(Plano.Rol rol) {
            return faltan.getOrDefault(rol, 0);
        }
    }

    /** What its furnace holds, and whether it is burning. */
    private record Fuego(boolean existe, ItemStack entrada, ItemStack combustible, ItemStack salida, boolean encendido) {
        /** Smelting now, or about to: something in, and a fire or fuel for it. */
        boolean ocupado() {
            return existe && !entrada.isEmpty() && (encendido || !combustible.isEmpty());
        }

        /** Something in it and nothing to burn it with: it went out. */
        boolean apagado() {
            return existe && !entrada.isEmpty() && !encendido && combustible.isEmpty();
        }
    }

    private static final double ALCANCE = Retina.BLOCK_REACH;
    private static final int MAX_PILAR = 5;
    private static final int CRAFT_TICKS = 10;
    private static final int MANO_TICKS = 6;
    private static final int PROFUNDIDAD = 12;
    private static final int CANTERA_MAX = 40;
    private static final int SIN_AVANCE = 3600;

    private static final Map<UUID, Constructor> TODOS = new HashMap<>();

    @Nullable
    private Obra obra;
    private Accion accion = Accion.PENSAR;
    private long proximoPensar;
    @Nullable
    private Objetivo objetivo;
    @Nullable
    private Puesto puesto;
    @Nullable
    private List<BlockPos> camino;
    private int irTicks;
    private int espera;
    private final Hands.Breaker breaker = new Hands.Breaker();
    /** Cells it tried and could not do just now, and until when it leaves them be. */
    private final Map<Long, Long> fallos = new HashMap<>();
    private long ultimoAvance;
    private int atascos;
    private long avisado;
    private long avisoLena;
    private long avisoPiedra;
    private long avisoArena;
    private long sinArbolesHasta;
    private int hito;
    private boolean faltanAndamios;
    private int lenaObjetivo;
    private int lenaQuieto;
    private boolean subiendoCantera;
    private int canteraQuieto;
    @Nullable
    private BlockPos arenaVista;
    private String haciendo = "pensando por dónde seguir";
    private String haciendoEn = "working out what to do next";
    private Map<Plano.Rol, Integer> faltanVistos = Map.of();
    private final List<Taller.Receta> mano = new ArrayList<>();
    private int ultimaCuenta = -1;
    /** What it worked out it needs, for this reading of the building this tick. */
    @Nullable
    private Necesidad nCache;
    @Nullable
    private Lectura nLectura;
    private long nTick = -1;
    /** Ways it may still plan this tick (each one can look at thousands of cells). */
    private int caminos;
    private long caminosTick = -1;
    /** The last search for a place to stand ran out of ways to plan this tick (no verdict on that block). */
    private boolean sinPresupuesto;
    /** How much sand it set out to carry. */
    private int arenaMeta;

    private Constructor() {
    }

    /** The builder in this villager (one per villager, kept while the server runs). */
    public static Constructor de(VillagerEntityMCA villager) {
        return TODOS.computeIfAbsent(villager.getUuid(), k -> new Constructor());
    }

    @Nullable
    public static Constructor si(VillagerEntityMCA villager) {
        return TODOS.get(villager.getUuid());
    }

    public static void olvidar(UUID villager) {
        TODOS.remove(villager);
    }

    /**
     * What a building under way keeps for itself ({@link Hands#buildingBlock}): its planks, its stone while the
     * footing still needs it, its glass. Never a pillar, a bridge or a plug of those; dirt and the rest go.
     */
    public static boolean reservado(VillagerEntityMCA villager, ItemStack stack) {
        Constructor c = TODOS.get(villager.getUuid());
        if (c == null || c.obra == null) {
            return false;
        }
        if (stack.isIn(ItemTags.PLANKS) || stack.isOf(Items.GLASS)) {
            return true;
        }
        if (Taller.PIEDRA.test(stack) || Plano.acepta(Plano.Rol.BASE, stack)) {
            return c.faltanVistos.getOrDefault(Plano.Rol.BASE, 0) > 0 || c.obra.horno == null;
        }
        return false;
    }

    /** In Spanish or English: what it is doing for the house right now. */
    public String haciendo(boolean es) {
        return es ? haciendo : haciendoEn;
    }

    private void hace(String es, String en) {
        haciendo = es;
        haciendoEn = en;
    }

    /** Standing on a scaffold of its own (the pause waits until it is down). */
    public boolean enAndamio(ServerWorld world, VillagerEntityMCA villager) {
        return obra != null && obra.andamios.contains(Mining.pies(villager).down());
    }

    /** Gets off its scaffold before anything else. */
    public void bajarYa() {
        accion = Accion.BAJAR;
    }

    /** It leaves the building for now (fed up, hungry, night, told something else): hands and legs let go. */
    public void soltar(ServerWorld world, VillagerEntityMCA villager, @Nullable ProspectingTask task) {
        breaker.reset(world, villager);
        if (task != null) {
            task.motor().reset(world, villager);
            if (task.progreso().pidiendo()) {
                task.progreso().olvidarPedidos(world, villager);
            }
        }
        villager.getNavigation().stop();
        accion = Accion.PENSAR;
        objetivo = null;
        puesto = null;
        camino = null;
        mano.clear();
    }

    // ------------------------------------------------------------------ the loop

    /** One tick of building {@code o}. LISTA once the last block is set and its things are put away. */
    public Fin tick(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task, Obra o) {
        long now = world.getTime();
        if (obra != o) {
            obra = o;
            accion = Accion.PENSAR;
            objetivo = null;
            fallos.clear();
            ultimoAvance = now;
            atascos = 0;
            hito = -1;
        }
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        if ((villager.age % 10) == 0 && accion != Accion.LENA) {
            // what lies about that the house needs: thrown by a player, dropped by what it broke
            Hands.collect(world, villager, Mining.pies(villager), 3.5, Constructor::sirve);
        }
        int cuenta = cuentaUtil(villager.getInventory());
        if (cuenta != ultimaCuenta) {
            if (cuenta > ultimaCuenta && ultimaCuenta >= 0) {
                avance(now);
            }
            ultimaCuenta = cuenta;
        }
        Motor motor = task.motor();
        Fin fin = Fin.SIGUE;
        switch (accion) {
            case PENSAR -> {
                if (motor.midStep(villager)) {
                    motor.step(world, villager, Mining.pies(villager), false, false);
                } else if (now >= proximoPensar) {
                    fin = pensar(world, villager, task);
                } else {
                    Motor.standStill(villager);
                }
            }
            case IR -> ir(world, villager, motor);
            case SUBIR -> subir(world, villager, motor);
            case TRABAJAR -> trabajar(world, villager, task);
            case BAJAR -> bajar(world, villager);
            case LENA -> lena(world, villager, task);
            case CANTERA -> cantera(world, villager, task);
            case ARENA -> arena(world, villager, task);
            case MANO -> mano(world, villager);
            case ESPERAR -> {
                Motor.standStill(villager);
                if (--espera <= 0) {
                    accion = Accion.PENSAR;
                }
            }
        }
        if (now - ultimoAvance > SIN_AVANCE) {
            atascado(world, villager, task, now);
        }
        return fin;
    }

    private void avance(long now) {
        ultimoAvance = now;
        atascos = 0;
    }

    /** Nothing has come of anything for minutes: it starts over, every cell gets a new try, and in the end it asks for help. */
    private void atascado(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task, long now) {
        ultimoAvance = now;
        atascos++;
        MCA.LOGGER.info("[Tropeles] {} lleva rato sin avanzar la casa ({}, {}): empieza de nuevo", villager.getName().getString(), accion, haciendo);
        BlockPos below = Mining.pies(villager).down();
        soltar(world, villager, task);
        fallos.clear();
        if (obra != null && obra.andamios.contains(below)) {
            accion = Accion.BAJAR;
        }
        if (atascos >= 2 && now - avisado > 6000 && obra != null) {
            avisado = now;
            BlockPos at = Mining.pies(villager);
            Voluntad.contar(villager, "Me estoy trabando con la casa (estoy en " + at.getX() + " " + at.getY() + " " + at.getZ() + "). "
                            + faltas(world, villager, obra, true) + " Si me echas una mano, sigo.",
                    "I'm getting stuck with the house (I'm at " + at.getX() + " " + at.getY() + " " + at.getZ() + "). "
                            + faltas(world, villager, obra, false) + " If you give me a hand, I'll keep going.");
        }
    }

    // ------------------------------------------------------------------ deciding

    private Fin pensar(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        long now = world.getTime();
        proximoPensar = now + 10;
        Obra o = obra;
        if (o == null) {
            return Fin.SIGUE;
        }
        BlockPos feet = Mining.pies(villager);
        if (o.andamios.contains(feet.down())) {
            accion = Accion.BAJAR;
            return Fin.SIGUE;
        }
        Lectura l = leer(world, villager, now);
        faltanVistos = l.faltan;
        hitos(villager, l);
        if (l.pendientes.isEmpty() && l.estorbos.stream().noneMatch(c -> Obras.mayBreak(world, villager, c.pos()))) {
            return terminar(world, villager, task);
        }
        Fuego fuego = fuego(world);
        // the furnace first when it stands idle with work for it: it smelts while the hands do the rest
        Objetivo horno = trabajoHorno(world, villager, l, fuego, true);
        if (horno != null && ir(world, villager, horno, false)) {
            return Fin.SIGUE;
        }
        // 1. the building: something out of the way, or the next block with a place to stand
        if (elegirPieza(world, villager, l, feet, now)) {
            return Fin.SIGUE;
        }
        // 2. what it lacks: made from what it carries, by hand or at a table
        if (manoNecesaria(world, villager, l, fuego)) {
            accion = Accion.MANO;
            espera = MANO_TICKS;
            return Fin.SIGUE;
        }
        if (!recetasMesa(villager, l, fuego).isEmpty()) {
            Objetivo mesa = mesaParaTrabajar(world, villager);
            if (mesa != null) {
                hace("voy a la mesa de crafteo", "going to the crafting table");
                if (ir(world, villager, mesa, false)) {
                    return Fin.SIGUE;
                }
            } else if (Taller.contar(villager.getInventory(), s -> s.isOf(Items.CRAFTING_TABLE)) > 0 && ponerJunto(world, villager, true)) {
                return Fin.SIGUE;
            }
        }
        horno = trabajoHorno(world, villager, l, fuego, false);
        if (horno != null && ir(world, villager, horno, false)) {
            return Fin.SIGUE;
        }
        if (!fuego.existe() && Taller.contar(villager.getInventory(), s -> s.isOf(Items.FURNACE)) > 0 && necesitaHorno(villager, l, fuego)
                && ponerJunto(world, villager, false)) {
            return Fin.SIGUE;
        }
        // 3. what it has to go and get
        return conseguir(world, villager, task, l, fuego, now);
    }

    /** Reads the building cell by cell. */
    private Lectura leer(ServerWorld world, VillagerEntityMCA villager, long now) {
        Lectura l = new Lectura();
        int yMin = Integer.MAX_VALUE;
        for (Obra.Celda c : obra.celdas()) {
            BlockState s = world.getBlockState(c.pos());
            Plano.Rol rol = c.rol();
            if (rol == Plano.Rol.AIRE) {
                if (!obra.andamios.contains(c.pos()) && estorbo(world, c.pos(), s)) {
                    l.estorbos.add(c);
                }
                continue;
            }
            if (rol == Plano.Rol.PUERTA_ARRIBA) {
                if (!Plano.hecho(rol, c.state(), s) && !s.isReplaceable() && !obra.andamios.contains(c.pos())) {
                    l.estorbos.add(c);
                }
                continue;
            }
            l.total++;
            if (Plano.hecho(rol, c.state(), s)) {
                l.hechas++;
                continue;
            }
            if (obra.sinVidrio && rol == Plano.Rol.VIDRIO) {
                continue;
            }
            boolean libre = s.isReplaceable();
            if (!libre && !obra.andamios.contains(c.pos()) && !Obras.mayBreak(world, villager, c.pos())) {
                l.bloqueadas++;
                continue; // somebody's block where the plan has another: it stays, and the house is done without that one
            }
            l.pendientes.add(c);
            l.faltan.merge(rol, 1, Integer::sum);
            if (!libre && !obra.andamios.contains(c.pos())) {
                l.estorbos.add(c);
            }
            if (rol.estructura && !fallando(c.pos(), now)) {
                yMin = Math.min(yMin, c.pos().getY());
            }
        }
        l.techo = yMin == Integer.MAX_VALUE ? Integer.MAX_VALUE : yMin + 1;
        return l;
    }

    /** Something solid, or a plant, in a cell of the building that must stay empty. */
    private static boolean estorbo(World world, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getCollisionShape(world, pos).isEmpty()) {
            return false;
        }
        if (s.isOf(Blocks.TORCH) || s.isOf(Blocks.WALL_TORCH) || s.isOf(Blocks.LANTERN)) {
            return false; // a light somebody set: it may stay
        }
        return !s.getCollisionShape(world, pos).isEmpty() || s.isReplaceable();
    }

    private boolean fallando(BlockPos pos, long now) {
        Long until = fallos.get(pos.asLong());
        if (until == null) {
            return false;
        }
        if (until <= now) {
            fallos.remove(pos.asLong());
            return false;
        }
        return true;
    }

    private void fallo(BlockPos pos, long until) {
        fallos.put(pos.asLong(), until);
        if (fallos.size() > 2048) {
            fallos.clear();
        }
    }

    /** The next thing to do on the building itself, with a place to stand for it: false when there is none right now. */
    private boolean elegirPieza(ServerWorld world, VillagerEntityMCA villager, Lectura l, BlockPos feet, long now) {
        faltanAndamios = false;
        List<Objetivo> cands = candidatos(world, villager, l, now);
        if (cands.isEmpty()) {
            return false;
        }
        cands.sort(Comparator.comparingDouble(o -> orden(o, Vec3d.ofCenter(feet))));
        int tries = 0;
        for (Objetivo o : cands) {
            if (++tries > 6) {
                break;
            }
            if (ir(world, villager, o, true)) {
                return true;
            }
            if (sinPresupuesto) {
                break; // no more planning this tick: the rest get their turn next time, no verdict on them
            }
            fallo(o.pos, now + 600 + villager.getRandom().nextInt(200));
        }
        return false;
    }

    /**
     * Which goes first: what is in the way, from the top down (the way a player digs a site out), then what fills the
     * ground under the floor, then the blocks from the bottom up, the nearest of a layer first.
     */
    private static double orden(Objetivo o, Vec3d from) {
        double d = Vec3d.ofCenter(o.pos).distanceTo(from);
        return switch (o.tipo) {
            case ESTORBO -> -100000.0 - o.pos.getY() * 6.0 + d;
            case RELLENO -> -50000.0 + o.pos.getY() * 6.0 + d;
            default -> o.pos.getY() * 6.0 + d;
        };
    }

    private List<Objetivo> candidatos(ServerWorld world, VillagerEntityMCA villager, Lectura l, long now) {
        List<Objetivo> cands = new ArrayList<>();
        SimpleInventory inv = villager.getInventory();
        for (Obra.Celda c : l.estorbos) {
            if (fallando(c.pos(), now) || c.pos().getY() > l.techo + 3) {
                continue;
            }
            Plano.Rol rol = c.rol();
            if (rol != Plano.Rol.AIRE && rol != Plano.Rol.PUERTA_ARRIBA && rol != Plano.Rol.PUERTA
                    && Hands.findSlot(inv, s -> Plano.acepta(rol, s)) < 0) {
                continue; // the ground stays where a block goes until it has that block: no holes in the floor for nothing
            }
            if (!Obras.mayBreak(world, villager, c.pos())) {
                continue;
            }
            cands.add(new Objetivo(Tipo.ESTORBO, c.pos(), c));
        }
        boolean relleno = !Hands.buildingBlock(villager).isEmpty();
        int y0 = obra.origen.getY();
        for (Obra.Celda c : l.pendientes) {
            if (fallando(c.pos(), now)) {
                continue;
            }
            if (c.rol().estructura && c.pos().getY() > l.techo) {
                continue;
            }
            BlockState here = world.getBlockState(c.pos());
            if (!here.isReplaceable()) {
                continue; // the block in the way goes first
            }
            Plano.Rol rol = c.rol();
            if (c.pos().getY() == y0 && rol.estructura) {
                // the floor over a dip: the ground under it is filled first, as a player would, with dirt or the like
                BlockPos below = c.pos().down();
                BlockState under = world.getBlockState(below);
                if (under.isReplaceable() && under.getFluidState().isEmpty() && !fallando(below, now)) {
                    if (relleno && !world.getBlockState(below.down()).isReplaceable()) {
                        cands.add(new Objetivo(Tipo.RELLENO, below, null));
                    } else if (!relleno) {
                        faltanAndamios = true;
                    }
                }
            }
            if (rol == Plano.Rol.PUERTA && !world.getBlockState(c.pos().up()).isReplaceable()) {
                continue;
            }
            if (Hands.findSlot(inv, s -> Plano.acepta(rol, s)) < 0) {
                continue;
            }
            cands.add(new Objetivo(Tipo.PIEZA, c.pos(), c));
        }
        return cands;
    }

    /** Sets off for {@code o}: a place to stand found and a way there. False when there is none. */
    private boolean ir(ServerWorld world, VillagerEntityMCA villager, Objetivo o, boolean conPilar) {
        Puesto p = buscarPuesto(world, villager, o, conPilar);
        if (p == null) {
            return false;
        }
        objetivo = o;
        puesto = p;
        camino = p.camino();
        irTicks = 0;
        accion = Accion.IR;
        if (o.tipo == Tipo.PIEZA && o.celda != null) {
            hace("pongo " + o.celda.rol().es + " en la casa", "setting " + o.celda.rol().en + " on the house");
        } else if (o.tipo == Tipo.ESTORBO) {
            hace("despejo el sitio de la casa", "clearing the house's site");
        }
        return true;
    }

    // ------------------------------------------------------------------ where to stand

    /**
     * The cheapest spot from which its eye would see what it needs of {@code o} (a face to set a block against, the
     * block itself to break or use) within a player's reach: a cell it can stand in, or the top of a pillar of up to
     * {@value #MAX_PILAR} of its own blocks, with a way to walk there over what is there now.
     */
    @Nullable
    private Puesto buscarPuesto(ServerWorld world, VillagerEntityMCA villager, Objetivo o, boolean conPilar) {
        BlockPos target = o.pos;
        BlockPos feet = Mining.pies(villager);
        long now = world.getTime();
        if (caminosTick != now) {
            caminosTick = now;
            caminos = 8;
        }
        sinPresupuesto = false;
        if (o.tipo == Tipo.LUGAR) {
            // a spot to stand on itself
            if (feet.equals(target)) {
                return new Puesto(target, 0, null);
            }
            if (caminos-- <= 0) {
                sinPresupuesto = true;
                return null;
            }
            List<BlockPos> path = Motor.findWalk(world, feet, target, 0.5, 3000, null);
            return path == null && feet.getManhattanDistance(target) <= 24 ? null : new Puesto(target, 0, path);
        }
        double eyeH = villager.getStandingEyeHeight();
        int bloques = conPilar ? Math.min(MAX_PILAR, Hands.buildingBlockCount(villager)) : 0;
        Vec3d centro = Vec3d.ofCenter(target);
        List<long[]> ops = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x = target.getX() + dx;
                int z = target.getZ() + dz;
                for (int y = target.getY() + 3; y >= target.getY() - 6; y--) {
                    m.set(x, y, z);
                    if (!Motor.standCell(world, m)) {
                        continue;
                    }
                    BlockPos f = m.toImmutable();
                    if (cuerpoEstorba(o, f)) {
                        continue;
                    }
                    add(ops, f, 0, feet, centro, o, eyeH);
                    for (int h = 1; h <= MAX_PILAR; h++) {
                        BlockPos top = f.up(h);
                        if (top.getY() > target.getY() + 3 || !pilarLibre(world, villager, f.up(h - 1), now) || !Mining.passable(world, top.up())) {
                            break;
                        }
                        if (cuerpoEstorba(o, top)) {
                            break;
                        }
                        if (h > bloques) {
                            // it could see from up there, with blocks to climb on
                            if (vistaDesde(world, villager, o, ojo(top, eyeH))) {
                                faltanAndamios = true;
                            }
                            break;
                        }
                        add(ops, f, h, feet, centro, o, eyeH);
                    }
                }
            }
        }
        ops.sort(Comparator.comparingLong(a -> a[2]));
        int looked = 0;
        int walks = 0;
        for (long[] op : ops) {
            if (++looked > 40) {
                break;
            }
            BlockPos base = BlockPos.fromLong(op[0]);
            int h = (int) op[1];
            if (!vistaDesde(world, villager, o, ojo(base.up(h), eyeH))) {
                continue;
            }
            if (base.equals(feet)) {
                return new Puesto(base, h, null);
            }
            if (++walks > 4) {
                break; // four spots it could not walk to: this block is out of its way for now
            }
            if (caminos-- <= 0) {
                sinPresupuesto = true;
                break;
            }
            List<BlockPos> path = Motor.findWalk(world, feet, base, 0.5, 3000, null);
            if (path != null) {
                return new Puesto(base, h, path);
            }
            if (feet.getManhattanDistance(base) > 24) {
                // too far to plan the whole way: the legs take it near, the rest is planned there
                return new Puesto(base, h, null);
            }
        }
        return null;
    }

    private static void add(List<long[]> ops, BlockPos f, int h, BlockPos feet, Vec3d centro, Objetivo o, double eyeH) {
        Vec3d eye = ojo(f.up(h), eyeH);
        if (eye.distanceTo(centro) > ALCANCE + 0.9) {
            return;
        }
        double walk = Math.sqrt(f.getSquaredDistance(feet));
        double cost = walk + h * 3.5 + Math.abs(f.getY() - feet.getY()) * 1.5 + (f.equals(feet) && h == 0 ? -4.0 : 0.0) + eye.distanceTo(centro) * 0.3;
        ops.add(new long[]{f.asLong(), h, Math.round(cost * 100)});
    }

    /** The feet or the head would be in the cell it works on. */
    private static boolean cuerpoEstorba(Objetivo o, BlockPos feet) {
        BlockPos t = o.pos;
        if (feet.equals(t) || feet.up().equals(t)) {
            return true;
        }
        return o.celda != null && o.celda.rol() == Plano.Rol.PUERTA && (feet.equals(t.up()) || feet.up().equals(t.up()));
    }

    /** A cell a pillar block may go in: empty, no part of any building, not a cell it keeps breaking and filling. */
    private boolean pilarLibre(ServerWorld world, VillagerEntityMCA villager, BlockPos cell, long now) {
        BlockState s = world.getBlockState(cell);
        if (!s.isReplaceable() || !s.getFluidState().isEmpty() || !Mining.passable(world, cell)) {
            return false;
        }
        Obra.Celda c = obra == null ? null : obra.en(cell);
        if (c != null && c.rol() != Plano.Rol.AIRE) {
            return false;
        }
        if (obra != null && (cell.equals(obra.entrada()) || cell.equals(obra.entrada().up()))) {
            return false;
        }
        return !Toques.forbidden(villager, cell, now);
    }

    private static Vec3d ojo(BlockPos feet, double eyeH) {
        return new Vec3d(feet.getX() + 0.5, feet.getY() + eyeH, feet.getZ() + 0.5);
    }

    /** From that eye, it would see what {@code o} needs. */
    private boolean vistaDesde(ServerWorld world, VillagerEntityMCA villager, Objetivo o, Vec3d eye) {
        return switch (o.tipo) {
            case LUGAR -> true;
            case PIEZA -> caraDesde(world, eye, o.pos, villager, apoyo(o.celda)) != null;
            case RELLENO -> caraDesde(world, eye, o.pos, villager, null) != null;
            default -> caraVisible(world, eye, o.pos, villager) != null;
        };
    }

    /** The one neighbour a block must be set against, when it has one: a wall torch goes on its wall. */
    @Nullable
    private static Direction apoyo(@Nullable Obra.Celda c) {
        if (c != null && c.rol() == Plano.Rol.ANTORCHA && c.state().contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) {
            return c.state().get(net.minecraft.state.property.Properties.HORIZONTAL_FACING).getOpposite();
        }
        return null;
    }

    /**
     * The face of a solid neighbour of {@code target} an eye at {@code eye} can put its crosshair on within reach, the
     * way a player looks at the side of a block to set another against it ({@link Hands#supportFace} from any eye).
     */
    @Nullable
    static BlockHitResult caraDesde(World world, Vec3d eye, BlockPos target, Entity entity, @Nullable Direction solo) {
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            if (solo != null && dir != solo) {
                continue;
            }
            BlockPos neighbor = target.offset(dir);
            BlockState state = world.getBlockState(neighbor);
            if (state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty() && state.getCollisionShape(world, neighbor).isEmpty()) {
                continue;
            }
            Direction side = dir.getOpposite();
            Vec3d face = Vec3d.ofCenter(target).add(Vec3d.of(dir.getVector()).multiply(0.5));
            for (Vec3d point : puntos(face, side)) {
                double distance = eye.distanceTo(point);
                if (distance > ALCANCE || distance >= bestDistance) {
                    continue;
                }
                Vec3d past = point.add(point.subtract(eye).normalize().multiply(0.05));
                BlockHitResult hit = world.raycast(new RaycastContext(eye, past, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(neighbor) && hit.getSide() == side) {
                    best = hit;
                    bestDistance = distance;
                    break;
                }
            }
        }
        return best;
    }

    /** A point of {@code target}'s own outline an eye at {@code eye} sees within reach (to break or use it). */
    @Nullable
    static BlockHitResult caraVisible(World world, Vec3d eye, BlockPos target, Entity entity) {
        BlockState state = world.getBlockState(target);
        VoxelShape shape = state.getOutlineShape(world, target, ShapeContext.of(entity));
        if (state.isAir() || shape.isEmpty()) {
            return null;
        }
        Vec3d c = shape.getBoundingBox().getCenter().add(target.getX(), target.getY(), target.getZ());
        for (Direction dir : Direction.values()) {
            Vec3d normal = Vec3d.of(dir.getVector());
            Vec3d face = c.add(normal.multiply(0.45));
            if (face.subtract(eye).dotProduct(normal) >= 0) {
                continue; // a face turned away from the eye
            }
            for (Vec3d point : puntos(face, dir)) {
                if (eye.distanceTo(point) > ALCANCE) {
                    continue;
                }
                Vec3d past = point.add(point.subtract(eye).normalize().multiply(0.1));
                BlockHitResult hit = world.raycast(new RaycastContext(eye, past, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, entity));
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static Vec3d[] puntos(Vec3d face, Direction side) {
        return new Vec3d[]{face, face.add(enCara(side, 0.3, 0.3)), face.add(enCara(side, -0.3, 0.3)), face.add(enCara(side, 0.3, -0.3)),
                face.add(enCara(side, -0.3, -0.3))};
    }

    private static Vec3d enCara(Direction side, double a, double b) {
        return switch (side.getAxis()) {
            case X -> new Vec3d(0, a, b);
            case Y -> new Vec3d(a, 0, b);
            case Z -> new Vec3d(a, b, 0);
        };
    }

    // ------------------------------------------------------------------ the legs

    private void ir(ServerWorld world, VillagerEntityMCA villager, Motor motor) {
        if (puesto == null || objetivo == null) {
            accion = Accion.PENSAR;
            return;
        }
        Motor.Result r = andar(world, villager, motor, puesto.base(), 400 + (int) Math.sqrt(Mining.pies(villager).getSquaredDistance(puesto.base())) * 30);
        if (r == Motor.Result.ARRIVED) {
            accion = puesto.alto() > 0 ? Accion.SUBIR : Accion.TRABAJAR;
            espera = 2;
        } else if (r == Motor.Result.BLOCKED) {
            MCA.LOGGER.info("[Tropeles] {} no llega a {} para la casa", villager.getName().getString(), puesto.base().toShortString());
            fallo(objetivo.pos, world.getTime() + 800);
            accion = Accion.PENSAR;
        }
    }

    /**
     * One tick of walking to {@code goal}: over what is there (a way planned cell by cell, doors opened on the way),
     * or by its legs' own sense of the way when that is too far to plan, then planned again once near.
     */
    private Motor.Result andar(ServerWorld world, VillagerEntityMCA villager, Motor motor, BlockPos goal, int limite) {
        BlockPos feet = Mining.pies(villager);
        if (feet.equals(goal) && Motor.onCell(world, villager, feet) && !motor.midStep(villager)) {
            villager.getNavigation().stop();
            camino = null;
            return Motor.Result.ARRIVED;
        }
        irTicks++;
        if (camino != null) {
            Motor.Result r = motor.walkPath(world, villager, camino);
            if (r == Motor.Result.RUNNING) {
                return r;
            }
            camino = null;
            if (r == Motor.Result.ARRIVED && Mining.pies(villager).equals(goal)) {
                return Motor.Result.ARRIVED;
            }
            motor.reset(world, villager);
        }
        double d = Math.sqrt(feet.getSquaredDistance(goal));
        if (irTicks % 40 == 1 && d < 28) {
            List<BlockPos> path = Motor.findWalk(world, feet, goal, 0.5, 3000, null);
            if (path != null) {
                camino = path;
                return Motor.Result.RUNNING;
            }
        }
        if (irTicks % 20 == 1 || villager.getNavigation().isIdle()) {
            Vec3d to = Vec3d.ofBottomCenter(goal);
            if (d > 40) {
                Vec3d step = to.subtract(villager.getPos()).multiply(1, 0, 1).normalize().multiply(32);
                BlockPos mid = BlockPos.ofFloored(villager.getPos().add(step));
                int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, mid.getX(), mid.getZ());
                to = new Vec3d(mid.getX() + 0.5, y, mid.getZ() + 0.5);
            }
            villager.getNavigation().startMovingTo(to.x, to.y, to.z, 0.7);
        }
        if (d < 1.5 && Math.abs(feet.getY() - goal.getY()) <= 1) {
            villager.getMoveControl().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.6);
        }
        return irTicks > limite ? Motor.Result.BLOCKED : Motor.Result.RUNNING;
    }

    /** Up its own pillar to where it can reach the block. */
    private void subir(ServerWorld world, VillagerEntityMCA villager, Motor motor) {
        if (puesto == null || obra == null) {
            accion = Accion.PENSAR;
            return;
        }
        hace("subo a un andamio para alcanzar", "climbing a scaffold to reach");
        Motor.Result r = motor.pillarStep(world, villager, puesto.pies());
        // whatever it set under itself on the way up is scaffold, to be taken down again (also if it is called away mid-climb)
        BlockPos base = puesto.base();
        boolean nuevo = false;
        for (int y = 0; y < puesto.alto(); y++) {
            BlockPos cell = base.up(y);
            if (!obra.andamios.contains(cell) && !world.getBlockState(cell).isReplaceable() && Obras.kind(world, villager, cell) == Obras.Kind.OWN) {
                obra.andamios.add(cell.toImmutable());
                nuevo = true;
            }
        }
        if (nuevo) {
            ObrasMundo.get(world).cambio();
        }
        if (r == Motor.Result.RUNNING) {
            return;
        }
        if (r == Motor.Result.ARRIVED) {
            accion = Accion.TRABAJAR;
            espera = 2;
        } else {
            if (objetivo != null) {
                fallo(objetivo.pos, world.getTime() + 800);
            }
            accion = obra.andamios.contains(Mining.pies(villager).down()) ? Accion.BAJAR : Accion.PENSAR;
        }
    }

    /** Down its scaffold, a block at a time from under its feet, the way a player does. */
    private void bajar(ServerWorld world, VillagerEntityMCA villager) {
        Motor.standStill(villager);
        if (obra == null) {
            accion = Accion.PENSAR;
            return;
        }
        BlockPos below = Mining.pies(villager).down();
        if (!obra.andamios.contains(below)) {
            accion = Accion.PENSAR;
            proximoPensar = 0;
            return;
        }
        if (!villager.isOnGround()) {
            return;
        }
        hace("bajo del andamio", "climbing down the scaffold");
        if (world.getBlockState(below).isReplaceable()) {
            obra.andamios.remove(below);
            return;
        }
        int r = breaker.tick(world, villager, below, true);
        if (r == Hands.Breaker.DONE) {
            obra.andamios.remove(below);
            ObrasMundo.get(world).cambio();
            Hands.collect(world, villager, below, 2.5);
            avance(world.getTime());
        } else if (r == Hands.Breaker.FAILED) {
            obra.andamios.remove(below);
            accion = Accion.PENSAR;
        }
    }

    // ------------------------------------------------------------------ the hands at the spot

    private void trabajar(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        Motor.standStill(villager);
        if (objetivo == null || obra == null) {
            accion = Accion.PENSAR;
            return;
        }
        if (!Motor.settled(villager)) {
            return;
        }
        if (espera > 0) {
            espera--;
            return;
        }
        long now = world.getTime();
        Objetivo o = objetivo;
        switch (o.tipo) {
            case PIEZA -> {
                if (o.celda == null || Obra.hecha(world, o.celda)) {
                    siguiente(world, villager);
                    return;
                }
                if (colocar(world, villager, o.celda)) {
                    avance(now);
                    espera = 3;
                } else {
                    fallo(o.pos, now + 400);
                }
                siguiente(world, villager);
            }
            case RELLENO -> {
                if (!world.getBlockState(o.pos).isReplaceable()) {
                    siguiente(world, villager);
                    return;
                }
                if (rellenar(world, villager, o.pos)) {
                    avance(now);
                    espera = 3;
                } else {
                    fallo(o.pos, now + 400);
                }
                siguiente(world, villager);
            }
            case ESTORBO, RECOGER, ANDAMIO -> {
                BlockState s = world.getBlockState(o.pos);
                if (s.isAir() || o.tipo == Tipo.ESTORBO && !estorbo(world, o.pos, s) && (o.celda == null || o.celda.rol() == Plano.Rol.AIRE
                        || s.isReplaceable())) {
                    siguiente(world, villager);
                    return;
                }
                if (o.tipo == Tipo.ANDAMIO && s.getBlock() instanceof net.minecraft.block.AbstractFurnaceBlock) {
                    vaciarHorno(world, villager, o.pos);
                }
                int r = breaker.tick(world, villager, o.pos);
                if (r == Hands.Breaker.RUNNING) {
                    return;
                }
                if (r == Hands.Breaker.FAILED) {
                    fallo(o.pos, now + 1200);
                } else {
                    avance(now);
                    obra.andamios.remove(o.pos);
                    if (o.pos.equals(obra.mesa)) {
                        obra.mesa = null;
                    }
                    if (o.pos.equals(obra.horno)) {
                        obra.horno = null;
                    }
                    ObrasMundo.get(world).cambio();
                    Hands.collect(world, villager, o.pos, 3.0);
                }
                siguiente(world, villager);
            }
            case MESA -> {
                if (!world.getBlockState(o.pos).isOf(Blocks.CRAFTING_TABLE)) {
                    accion = Accion.PENSAR;
                    return;
                }
                Hands.aim(villager, Vec3d.ofCenter(o.pos));
                List<Taller.Receta> todo = recetasMesa(villager, leer(world, villager, now), fuego(world));
                if (todo.isEmpty() || !Taller.hacer(villager, todo.get(0), obra.madera, reservaTroncos(world, villager))) {
                    accion = Accion.PENSAR;
                    proximoPensar = now;
                    return;
                }
                villager.swingHand(Hand.MAIN_HAND);
                world.playSound(null, o.pos, SoundEvents.BLOCK_WOOD_HIT, SoundCategory.BLOCKS, 0.5F, 1.0F);
                Retina.memory(villager).doing("fabrica " + todo.get(0).name().toLowerCase(java.util.Locale.ROOT) + " para la casa", now);
                hace("fabrico cosas para la casa en la mesa de crafteo", "crafting things for the house at the crafting table");
                avance(now);
                espera = CRAFT_TICKS;
            }
            case HORNO -> {
                usarHorno(world, villager, o.pos);
                accion = Accion.PENSAR;
                proximoPensar = now + 5;
            }
            case COFRE -> {
                guardar(world, villager, o.pos);
                accion = Accion.PENSAR;
                proximoPensar = now;
            }
            case LUGAR -> {
                accion = Accion.PENSAR;
                proximoPensar = now;
            }
        }
    }

    /**
     * After a block: the next one it can do from right where it stands (no step taken), or down from its
     * scaffold, or a new look at the building.
     */
    private void siguiente(ServerWorld world, VillagerEntityMCA villager) {
        long now = world.getTime();
        Vec3d eye = villager.getEyePos();
        BlockPos feet = Mining.pies(villager);
        if (objetivo != null && (objetivo.tipo == Tipo.PIEZA || objetivo.tipo == Tipo.ESTORBO || objetivo.tipo == Tipo.RELLENO)) {
            Lectura l = leer(world, villager, now);
            faltanVistos = l.faltan;
            List<Objetivo> cands = candidatos(world, villager, l, now);
            cands.removeIf(o -> Vec3d.ofCenter(o.pos).distanceTo(eye) > ALCANCE + 1.0 || cuerpoEstorba(o, feet));
            cands.sort(Comparator.comparingDouble(o -> orden(o, eye)));
            for (Objetivo o : cands) {
                if (vistaDesde(world, villager, o, eye)) {
                    objetivo = o;
                    return;
                }
            }
        }
        if (objetivo != null && objetivo.tipo == Tipo.RECOGER && !bastante(villager)) {
            BlockPos next = materialCerca(world, villager, eye);
            if (next != null) {
                objetivo = new Objetivo(Tipo.RECOGER, next, null);
                return;
            }
        }
        objetivo = null;
        accion = obra != null && obra.andamios.contains(feet.down()) ? Accion.BAJAR : Accion.PENSAR;
        proximoPensar = now + 2;
    }

    /** It carries the sand it set out for, or dirt enough for its scaffolds. */
    private boolean bastante(VillagerEntityMCA villager) {
        if (arenaFalta > 0) {
            return Taller.contar(villager.getInventory(), Taller.ARENA) >= arenaMeta;
        }
        return Hands.buildingBlockCount(villager) >= 16;
    }

    /** The next block of the same stuff it is digging (sand, or the top layer of dirt) right within reach. */
    @Nullable
    private BlockPos materialCerca(ServerWorld world, VillagerEntityMCA villager, Vec3d eye) {
        BlockPos feet = Mining.pies(villager);
        for (BlockPos p : BlockPos.iterateOutwards(feet, 4, 3, 4)) {
            BlockState s = world.getBlockState(p);
            boolean wanted = arenaFalta > 0 ? esArena(s) : esTierra(s) && p.getY() >= feet.getY() - 1 && Sitio.suelo(world, p.getX(), p.getZ()) == p.getY();
            if (!wanted || obra != null && obra.cerca(p, 2) || p.equals(feet.down()) || !Obras.mayBreak(world, villager, p)) {
                continue;
            }
            if (!esArena(s) && Obras.cropAt(world, p.up())) {
                continue;
            }
            if (caraVisible(world, eye, p, villager) == null) {
                continue;
            }
            return p.toImmutable();
        }
        return null;
    }

    private int arenaFalta;

    /**
     * Sets one block of the plan from the bag, against a face its eye sees, exactly as planned (turned, the door's two
     * halves); it counts as the player's build from then on (nobody digs a house away for a tunnel).
     */
    private boolean colocar(ServerWorld world, VillagerEntityMCA villager, Obra.Celda c) {
        SimpleInventory inv = villager.getInventory();
        Plano.Rol rol = c.rol();
        int slot = Hands.findSlot(inv, s -> Plano.acepta(rol, s));
        if (slot < 0) {
            return false;
        }
        ItemStack stack = inv.getStack(slot);
        BlockPos pos = c.pos();
        if (!world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        BlockState state = Plano.estadoPara(rol, c.state(), stack);
        if (!state.canPlaceAt(world, pos) || !world.canPlace(state, pos, ShapeContext.absent())) {
            return false;
        }
        BlockState upper = null;
        if (rol == Plano.Rol.PUERTA) {
            upper = state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
            if (!world.getBlockState(pos.up()).isReplaceable() || !world.canPlace(upper, pos.up(), ShapeContext.absent())) {
                return false;
            }
        }
        BlockHitResult face = caraDesde(world, villager.getEyePos(), pos, villager, apoyo(c));
        if (face == null) {
            return false;
        }
        Hands.aim(villager, face.getPos());
        BlockState placed = Block.postProcessState(state, world, pos);
        if (!world.setBlockState(pos, placed, Block.NOTIFY_ALL)) {
            return false;
        }
        if (upper != null) {
            world.setBlockState(pos.up(), upper, Block.NOTIFY_ALL);
            Obras.placedByPlayer(world, pos.up());
        }
        long now = world.getTime();
        BlockSoundGroup sound = placed.getSoundGroup();
        world.playSound(null, pos, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        world.emitGameEvent(villager, GameEvent.BLOCK_PLACE, pos);
        stack.decrement(1);
        inv.markDirty();
        Obras.placedByPlayer(world, pos);
        Toques.placed(villager, pos, now);
        villager.swingHand(Hand.MAIN_HAND);
        Retina.memory(villager).see(pos, world.getBlockState(pos), face.getSide(), face.getPos(), villager.getEyePos().distanceTo(face.getPos()),
                world.getLightLevel(pos), now);
        return true;
    }

    /** A block of dirt (or whatever plain block it carries) into a dip under the floor, against a face its eye sees. */
    private boolean rellenar(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack stack = Hands.buildingBlock(villager);
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.BlockItem item) || !world.getBlockState(pos).isReplaceable()) {
            return false;
        }
        BlockState state = item.getBlock().getDefaultState();
        if (!state.canPlaceAt(world, pos) || !world.canPlace(state, pos, ShapeContext.absent())) {
            return false;
        }
        BlockHitResult face = caraDesde(world, villager.getEyePos(), pos, villager, null);
        if (face == null) {
            return false;
        }
        Hands.aim(villager, face.getPos());
        BlockState placed = Block.postProcessState(state, world, pos);
        if (!world.setBlockState(pos, placed, Block.NOTIFY_ALL)) {
            return false;
        }
        BlockSoundGroup sound = placed.getSoundGroup();
        world.playSound(null, pos, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        world.emitGameEvent(villager, GameEvent.BLOCK_PLACE, pos);
        stack.decrement(1);
        villager.getInventory().markDirty();
        // the ground under a house is the house's: nobody digs it away
        Obras.placedByPlayer(world, pos);
        Toques.placed(villager, pos, world.getTime());
        villager.swingHand(Hand.MAIN_HAND);
        hace("relleno el suelo bajo el piso", "filling the ground under the floor");
        return true;
    }

    // ------------------------------------------------------------------ crafting

    /** The logs it keeps as logs: the corners still to set, and one for each charcoal it still has to burn. */
    private int reservaTroncos(ServerWorld world, VillagerEntityMCA villager) {
        Map<Plano.Rol, Integer> f = faltanVistos;
        SimpleInventory inv = villager.getInventory();
        int torches = Math.max(0, f.getOrDefault(Plano.Rol.ANTORCHA, 0) - Taller.contar(inv, s -> s.isOf(Items.TORCH)));
        int carbon = Taller.contar(inv, Taller.CARBON);
        Fuego fuego = fuego(world);
        int charcoal = Math.max(0, ceil(torches, 4) - carbon - (fuego.salida().isOf(Items.CHARCOAL) ? fuego.salida().getCount() : 0)
                - (fuego.entrada().isIn(ItemTags.LOGS) ? fuego.entrada().getCount() : 0));
        return f.getOrDefault(Plano.Rol.TRONCO, 0) + charcoal;
    }

    private static int ceil(int a, int b) {
        return a <= 0 ? 0 : (a + b - 1) / b;
    }

    /** How many items (of any kind it may build with) the bag holds: a rise is progress. */
    private static int cuentaUtil(SimpleInventory inv) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && sirve(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** What a house needs, raw or made: worth picking up. */
    static boolean sirve(ItemStack s) {
        return s.isIn(ItemTags.LOGS) || s.isIn(ItemTags.PLANKS) || Taller.PIEDRA.test(s) || Taller.ARENA.test(s) || Taller.VIDRIO.test(s)
                || Taller.CARBON.test(s) || Taller.PALOS.test(s) || s.isOf(Items.GLASS_PANE) || s.isOf(Items.TORCH) || s.isOf(Items.CRAFTING_TABLE)
                || s.isOf(Items.CHEST) || s.isOf(Items.FURNACE) || s.isOf(Items.DIRT) || Plano.acepta(Plano.Rol.ESCALON, s)
                || Plano.acepta(Plano.Rol.PUERTA, s) || Plano.acepta(Plano.Rol.BASE, s);
    }

    /** What it makes by hand now, queued for {@link #mano}; true when there is something. */
    private boolean manoNecesaria(ServerWorld world, VillagerEntityMCA villager, Lectura l, Fuego fuego) {
        mano.clear();
        SimpleInventory inv = villager.getInventory();
        Necesidad n = necesidad(world, villager, l, fuego);
        int reserva = n.reservaTroncos;
        int tablas = Taller.contar(inv, Taller.TABLAS);
        int troncos = Taller.contar(inv, Taller.TRONCOS);
        int palos = Taller.contar(inv, Taller.PALOS);
        int tablasMas = Math.max(0, n.tablas - tablas);
        for (int i = 0; i < Math.min(ceil(tablasMas, 4), troncos - reserva); i++) {
            mano.add(Taller.Receta.TABLAS);
        }
        int tablasLuego = tablas + 4 * mano.size();
        if (palos < n.palos && tablasLuego >= 2) {
            for (int i = 0; i < ceil(n.palos - palos, 4) && tablasLuego >= 2; i++) {
                mano.add(Taller.Receta.PALOS);
                tablasLuego -= 2;
            }
        }
        int carbon = Taller.contar(inv, Taller.CARBON);
        int palosLuego = palos + 4 * (int) mano.stream().filter(r -> r == Taller.Receta.PALOS).count();
        for (int i = 0; i < Math.min(ceil(n.antorchas, 4), Math.min(carbon, palosLuego)); i++) {
            mano.add(Taller.Receta.ANTORCHAS);
        }
        int mesas = Taller.contar(inv, s -> s.isOf(Items.CRAFTING_TABLE));
        boolean mesaParaCasa = l.f(Plano.Rol.MESA) > 0 && mesas == 0;
        boolean mesaParaTrabajar = !recetasMesa(villager, l, fuego).isEmpty() && mesas == 0 && mesaParaTrabajarCerca(world, villager) == null;
        if ((mesaParaCasa || mesaParaTrabajar) && tablasLuego >= 4) {
            mano.add(Taller.Receta.MESA);
        }
        return !mano.isEmpty();
    }

    /** One hand craft every few ticks, standing still, then back to thinking. */
    private void mano(ServerWorld world, VillagerEntityMCA villager) {
        Motor.standStill(villager);
        if (--espera > 0) {
            return;
        }
        espera = MANO_TICKS;
        if (mano.isEmpty()) {
            accion = Accion.PENSAR;
            proximoPensar = 0;
            return;
        }
        Taller.Receta r = mano.remove(0);
        int reserva = reservaTroncos(world, villager);
        if (Taller.hacer(villager, r, obra.madera, reserva)) {
            villager.swingHand(Hand.MAIN_HAND);
            hace("hago " + switch (r) {
                case TABLAS -> "tablas";
                case PALOS -> "palos";
                case MESA -> "una mesa de crafteo";
                default -> "antorchas";
            } + " para la casa", "making " + r.name().toLowerCase(java.util.Locale.ROOT) + " for the house");
            Retina.memory(villager).doing(haciendo, world.getTime());
            avance(world.getTime());
        }
    }

    /** What it still needs, all told. */
    private static final class Necesidad {
        int tablas;
        int troncos;
        int palos;
        int antorchas;
        int escalones;
        int paneles;
        int piedra;
        int arena;
        int vidrioPorHacer;
        int carbonPorHacer;
        int reservaTroncos;
        boolean pico;
        boolean puerta;
        boolean cofre;
        boolean horno;
    }

    private Necesidad necesidad(ServerWorld world, VillagerEntityMCA villager, Lectura l, Fuego fuego) {
        long now = world.getTime();
        if (nCache != null && nLectura == l && nTick == now) {
            return nCache;
        }
        Necesidad n = new Necesidad();
        nCache = n;
        nLectura = l;
        nTick = now;
        SimpleInventory inv = villager.getInventory();
        int escalones = Taller.contar(inv, s -> Plano.acepta(Plano.Rol.ESCALON, s));
        int paneles = Taller.contar(inv, s -> s.isOf(Items.GLASS_PANE));
        int antorchas = Taller.contar(inv, s -> s.isOf(Items.TORCH));
        int piedra = Taller.contar(inv, s -> Plano.acepta(Plano.Rol.BASE, s));
        int vidrio = Taller.contar(inv, Taller.VIDRIO);
        int arena = Taller.contar(inv, Taller.ARENA);
        int carbon = Taller.contar(inv, Taller.CARBON);
        int palos = Taller.contar(inv, Taller.PALOS);
        n.escalones = Math.max(0, l.f(Plano.Rol.ESCALON) - escalones);
        n.puerta = l.f(Plano.Rol.PUERTA) > 0 && Taller.contar(inv, s -> Plano.acepta(Plano.Rol.PUERTA, s)) == 0;
        n.cofre = l.f(Plano.Rol.COFRE) > 0 && Taller.contar(inv, s -> s.isOf(Items.CHEST)) == 0;
        boolean mesaItem = l.f(Plano.Rol.MESA) > 0 && Taller.contar(inv, s -> s.isOf(Items.CRAFTING_TABLE)) == 0;
        n.paneles = obra.sinVidrio ? 0 : Math.max(0, l.f(Plano.Rol.VIDRIO) - paneles);
        n.antorchas = Math.max(0, l.f(Plano.Rol.ANTORCHA) - antorchas);
        int vidrioEnHorno = fuego.salida().isOf(Items.GLASS) ? fuego.salida().getCount() : 0;
        int arenaEnHorno = Taller.ARENA.test(fuego.entrada()) ? fuego.entrada().getCount() : 0;
        n.vidrioPorHacer = n.paneles > 0 ? Math.max(0, ceil(n.paneles, 16) * 6 - vidrio - vidrioEnHorno - arenaEnHorno) : 0;
        int carbonEnHorno = (fuego.salida().isOf(Items.CHARCOAL) ? fuego.salida().getCount() : 0)
                + (fuego.entrada().isIn(ItemTags.LOGS) ? fuego.entrada().getCount() : 0);
        n.carbonPorHacer = n.antorchas > 0 ? Math.max(0, ceil(n.antorchas, 4) - carbon - carbonEnHorno) : 0;
        n.arena = Math.max(0, n.vidrioPorHacer - arena);
        boolean hornoTrabaja = n.vidrioPorHacer > 0 || n.carbonPorHacer > 0 || fuego.ocupado();
        n.horno = hornoTrabaja && !fuego.existe() && Taller.contar(inv, s -> s.isOf(Items.FURNACE)) == 0;
        n.piedra = l.f(Plano.Rol.BASE) + (n.horno ? 8 : 0);
        n.pico = n.piedra > piedra && Progreso.bestPickTier(villager) == 0;
        n.palos = (n.pico ? 2 : 0) + ceil(n.antorchas, 4);
        // fuel for all that is still to smelt, and for what sits in the furnace with nothing under it (a plank burns 1.5 items)
        int combustible = ceil((n.vidrioPorHacer + n.carbonPorHacer + (fuego.apagado() ? fuego.entrada().getCount() : 0)) * 2, 3);
        boolean recetas = n.escalones > 0 || n.puerta || n.cofre || n.pico || n.horno || n.paneles > 0 && vidrio >= 6;
        boolean mesaFalta = recetas && !mesaItem && Taller.contar(inv, s -> s.isOf(Items.CRAFTING_TABLE)) == 0 && mesaParaTrabajarCerca(world, villager) == null;
        n.tablas = l.f(Plano.Rol.TABLA) + 6 * ceil(n.escalones, 4) + (n.puerta ? 6 : 0) + (n.cofre ? 8 : 0) + (mesaItem ? 4 : 0) + (mesaFalta ? 4 : 0)
                + (n.pico ? 3 : 0) + 2 * ceil(Math.max(0, n.palos - palos), 4) + combustible;
        n.reservaTroncos = l.f(Plano.Rol.TRONCO) + n.carbonPorHacer;
        n.troncos = n.reservaTroncos + ceil(Math.max(0, n.tablas - Taller.contar(inv, Taller.TABLAS)), 4);
        return n;
    }

    /** The table recipes it needs and can make now from what it carries, in order. */
    private List<Taller.Receta> recetasMesa(VillagerEntityMCA villager, Lectura l, Fuego fuego) {
        List<Taller.Receta> list = new ArrayList<>();
        if (obra == null || !(villager.getWorld() instanceof ServerWorld world)) {
            return list;
        }
        Necesidad n = necesidad(world, villager, l, fuego);
        SimpleInventory inv = villager.getInventory();
        if (n.pico && Taller.puede(villager, Taller.Receta.PICO, 0)) {
            list.add(Taller.Receta.PICO);
        }
        if (n.escalones > 0 && Taller.puede(villager, Taller.Receta.ESCALERAS, 0)) {
            list.add(Taller.Receta.ESCALERAS);
        }
        if (n.puerta && Taller.puede(villager, Taller.Receta.PUERTA, 0)) {
            list.add(Taller.Receta.PUERTA);
        }
        if (n.cofre && Taller.puede(villager, Taller.Receta.COFRE, 0)) {
            list.add(Taller.Receta.COFRE);
        }
        int piedra = Taller.contar(inv, s -> Plano.acepta(Plano.Rol.BASE, s));
        if (n.horno && Taller.puede(villager, Taller.Receta.HORNO, 0) && piedra - 8 >= 0 && (piedra >= n.piedra || l.f(Plano.Rol.BASE) == 0
                || piedra - 8 >= l.f(Plano.Rol.BASE))) {
            list.add(Taller.Receta.HORNO);
        }
        if (n.paneles > 0 && Taller.puede(villager, Taller.Receta.PANELES, 0)) {
            list.add(Taller.Receta.PANELES);
        }
        return list;
    }

    /** A crafting table to work at: the house's own when it stands, the one it set down, or one it saw near the site. */
    @Nullable
    private Objetivo mesaParaTrabajar(ServerWorld world, VillagerEntityMCA villager) {
        BlockPos t = mesaParaTrabajarCerca(world, villager);
        return t == null ? null : new Objetivo(Tipo.MESA, t, null);
    }

    @Nullable
    private BlockPos mesaParaTrabajarCerca(ServerWorld world, VillagerEntityMCA villager) {
        if (obra == null) {
            return null;
        }
        for (Obra.Celda c : obra.celdas()) {
            if (c.rol() == Plano.Rol.MESA && world.getBlockState(c.pos()).isOf(Blocks.CRAFTING_TABLE)) {
                return c.pos();
            }
        }
        if (obra.mesa != null && world.getBlockState(obra.mesa).isOf(Blocks.CRAFTING_TABLE)) {
            return obra.mesa;
        }
        BlockPos center = obra.centro();
        for (VisualMemory.SeenBlock seen : Retina.memory(villager).blocks(s -> s.state.isOf(Blocks.CRAFTING_TABLE))) {
            if (seen.pos.isWithinDistance(center, 24) && world.getBlockState(seen.pos).isOf(Blocks.CRAFTING_TABLE)) {
                return seen.pos.toImmutable();
            }
        }
        return null;
    }

    /**
     * Sets down the table (or the furnace) it carries next to it, never on the building or right against it: its
     * workshop by the site. Out of the site first when it is standing in it.
     */
    private boolean ponerJunto(ServerWorld world, VillagerEntityMCA villager, boolean mesa) {
        Predicate<ItemStack> what = mesa ? s -> s.isOf(Items.CRAFTING_TABLE) : s -> s.isOf(Items.FURNACE);
        BlockPos feet = Mining.pies(villager);
        if (!obra.cerca(feet, 2)) {
            BlockPos placed = Progreso.placeBeside(world, villager, what, cell -> obra.cerca(cell, 1));
            if (placed != null) {
                if (mesa) {
                    obra.mesa = placed;
                } else {
                    obra.horno = placed;
                }
                ObrasMundo.get(world).cambio();
                avance(world.getTime());
                hace(mesa ? "pongo una mesa de crafteo junto a la obra" : "pongo un horno junto a la obra",
                        mesa ? "setting a crafting table by the site" : "setting a furnace by the site");
                MCA.LOGGER.info("[Tropeles] {} pone {} en {} para la casa", villager.getName().getString(), mesa ? "una mesa" : "un horno", placed.toShortString());
                accion = Accion.PENSAR;
                proximoPensar = world.getTime() + 5;
                return true;
            }
        }
        // a free spot of open ground round the site to set its workshop on
        BlockPos center = obra.centro();
        int i0 = villager.getRandom().nextInt(16);
        for (int r = 7; r <= 10; r++) {
            for (int k = 0; k < 16; k++) {
                double a = 2 * Math.PI * ((i0 + k) % 16) / 16;
                int x = center.getX() + (int) Math.round(Math.cos(a) * r);
                int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = Sitio.suelo(world, x, z);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos spot = new BlockPos(x, y + 1, z);
                if (spot.equals(feet) || obra.cerca(spot, 2) || !Motor.standCell(world, spot) || Obras.cropAt(world, spot)) {
                    continue;
                }
                if (ir(world, villager, new Objetivo(Tipo.LUGAR, spot, null), false)) {
                    hace("busco un sitio junto a la obra para mi taller", "finding a spot by the site for my workshop");
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the furnace

    private Fuego fuego(ServerWorld world) {
        if (obra == null || obra.horno == null) {
            return new Fuego(false, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, false);
        }
        BlockEntity be = world.isChunkLoaded(obra.horno) ? world.getBlockEntity(obra.horno) : null;
        if (!(be instanceof AbstractFurnaceBlockEntity f)) {
            obra.horno = null;
            return new Fuego(false, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, false);
        }
        BlockState s = world.getBlockState(obra.horno);
        boolean lit = s.contains(net.minecraft.block.AbstractFurnaceBlock.LIT) && s.get(net.minecraft.block.AbstractFurnaceBlock.LIT);
        return new Fuego(true, f.getStack(0), f.getStack(1), f.getStack(2), lit);
    }

    private boolean necesitaHorno(VillagerEntityMCA villager, Lectura l, Fuego fuego) {
        if (!(villager.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        Necesidad n = necesidad(world, villager, l, fuego);
        return n.vidrioPorHacer > 0 || n.carbonPorHacer > 0;
    }

    /**
     * Something to do at its furnace: take out what is done, refuel it when it went out with work in it, or (with
     * {@code soloCargar} only these two) put in sand or a log with fuel when it stands idle.
     */
    @Nullable
    private Objetivo trabajoHorno(ServerWorld world, VillagerEntityMCA villager, Lectura l, Fuego fuego, boolean soloCargar) {
        if (!fuego.existe() || obra.horno == null) {
            return null;
        }
        Necesidad n = necesidad(world, villager, l, fuego);
        if (!soloCargar && !fuego.salida().isEmpty() && (n.paneles > 0 || n.antorchas > 0 || l.pendientes.isEmpty())) {
            return new Objetivo(Tipo.HORNO, obra.horno, null);
        }
        if (fuego.apagado() && tieneCombustible(villager)) {
            return new Objetivo(Tipo.HORNO, obra.horno, null);
        }
        if (fuego.entrada().isEmpty() && cargaHorno(villager, n) != null && (tieneCombustible(villager) || !fuego.combustible().isEmpty())) {
            return new Objetivo(Tipo.HORNO, obra.horno, null);
        }
        return null;
    }

    private static boolean tieneCombustible(VillagerEntityMCA villager) {
        SimpleInventory inv = villager.getInventory();
        return Taller.contar(inv, Taller.TABLAS) > 0 || Taller.contar(inv, Taller.CARBON) > 0;
    }

    /** What it would put in the furnace now (sand for glass first, then a log for charcoal), or null. */
    @Nullable
    private static Predicate<ItemStack> cargaHorno(VillagerEntityMCA villager, Necesidad n) {
        SimpleInventory inv = villager.getInventory();
        if (n.vidrioPorHacer > 0 && Taller.contar(inv, Taller.ARENA) > 0) {
            return Taller.ARENA;
        }
        if (n.carbonPorHacer > 0 && Taller.contar(inv, Taller.TRONCOS) > n.reservaTroncos - n.carbonPorHacer) {
            return Taller.TRONCOS;
        }
        return null;
    }

    /** At the furnace: what is done comes out, sand or a log goes in, and fuel enough for all of it. */
    private void usarHorno(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity f)) {
            return;
        }
        Hands.aim(villager, Vec3d.ofCenter(pos));
        villager.swingHand(Hand.MAIN_HAND);
        long now = world.getTime();
        ItemStack out = f.getStack(2);
        if (!out.isEmpty()) {
            String name = out.getName().getString();
            Taller.dar(villager, out.copy());
            f.setStack(2, ItemStack.EMPTY);
            hace("saco del horno " + name, "taking " + name + " out of the furnace");
            avance(now);
        }
        SimpleInventory inv = villager.getInventory();
        Necesidad n = necesidad(world, villager, leer(world, villager, now), fuego(world));
        Predicate<ItemStack> what = f.getStack(0).isEmpty() ? cargaHorno(villager, n) : null;
        if (what != null) {
            int amount = what == Taller.ARENA ? Math.min(n.vidrioPorHacer, Taller.contar(inv, Taller.ARENA))
                    : Math.min(n.carbonPorHacer, Taller.contar(inv, Taller.TRONCOS) - (n.reservaTroncos - n.carbonPorHacer));
            amount = Math.min(64, Math.max(1, amount));
            int slot = Hands.findSlot(inv, what);
            if (slot >= 0) {
                ItemStack in = inv.getStack(slot).split(Math.min(amount, inv.getStack(slot).getCount()));
                f.setStack(0, in);
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.5F, 1.0F);
                hace("pongo " + in.getName().getString() + " al horno", "putting " + in.getName().getString() + " in the furnace");
                Retina.memory(villager).doing(haciendo, now);
                MCA.LOGGER.info("[Tropeles] {} pone {} x{} en su horno para la casa", villager.getName().getString(), in.getName().getString(), in.getCount());
                avance(now);
            }
        }
        cebar(world, villager, f, pos);
        inv.markDirty();
        f.markDirty();
    }

    /** Fuel under what is in the furnace, enough for all of it: planks first, then coal; never logs it keeps. */
    private static void cebar(ServerWorld world, VillagerEntityMCA villager, AbstractFurnaceBlockEntity f, BlockPos pos) {
        ItemStack in = f.getStack(0);
        if (in.isEmpty()) {
            return;
        }
        BlockState s = world.getBlockState(pos);
        boolean lit = s.contains(net.minecraft.block.AbstractFurnaceBlock.LIT) && s.get(net.minecraft.block.AbstractFurnaceBlock.LIT);
        ItemStack fuel = f.getStack(1);
        int have = (fuel.isEmpty() ? 0 : fuel.getCount() * (Taller.CARBON.test(fuel) ? 1600 : 300)) + (lit ? 100 : 0);
        int more = in.getCount() * 200 - have;
        if (more <= 0) {
            return;
        }
        SimpleInventory inv = villager.getInventory();
        for (Predicate<ItemStack> kind : List.<Predicate<ItemStack>>of(Taller.TABLAS, Taller.CARBON)) {
            int per = kind == Taller.CARBON ? 1600 : 300;
            for (int i = 0; i < inv.size() && more > 0; i++) {
                ItemStack p = inv.getStack(i);
                if (p.isEmpty() || !kind.test(p)) {
                    continue;
                }
                ItemStack slot = f.getStack(1);
                int k = Math.min(p.getCount(), ceil(more, per));
                if (slot.isEmpty()) {
                    f.setStack(1, p.split(k));
                } else if (ItemStack.canCombine(slot, p)) {
                    k = Math.min(k, slot.getMaxCount() - slot.getCount());
                    if (k <= 0) {
                        return;
                    }
                    p.decrement(k);
                    slot.increment(k);
                } else {
                    continue; // another fuel is in: it burns that first
                }
                more -= k * per;
            }
        }
    }

    private static void vaciarHorno(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity f) {
            for (int i = 0; i < 3; i++) {
                ItemStack s = f.removeStack(i);
                if (!s.isEmpty()) {
                    Taller.dar(villager, s);
                }
            }
            f.markDirty();
        }
    }

    // ------------------------------------------------------------------ getting what it lacks

    private Fin conseguir(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task, Lectura l, Fuego fuego, long now) {
        Necesidad n = necesidad(world, villager, l, fuego);
        SimpleInventory inv = villager.getInventory();
        int troncos = Taller.contar(inv, Taller.TRONCOS);
        if (faltanAndamios && Hands.buildingBlockCount(villager) < MAX_PILAR) {
            // blocks to climb on (or to fill a dip under the floor): dirt from round the site, quick and right there
            BlockPos dirt = tierraCerca(world, villager);
            if (dirt != null) {
                hace("saco tierra para hacer andamios", "digging dirt to make scaffolds");
                arenaFalta = 0;
                if (ir(world, villager, new Objetivo(Tipo.RECOGER, dirt, null), false)) {
                    return Fin.SIGUE;
                }
            }
        }
        arenaFalta = n.arena;
        if (troncos < n.troncos && now >= sinArbolesHasta) {
            // wood, in loads it can carry: the house grows as it comes
            lenaObjetivo = troncos + Math.min(n.troncos - troncos, 32);
            task.progreso().pedirTroncos(lenaObjetivo);
            lenaQuieto = 0;
            accion = Accion.LENA;
            hace("talo árboles para la casa", "chopping trees for the house");
            if (now - avisoLena > 2400) {
                avisoLena = now;
                Voluntad.contar(villager, "Voy a talar: me faltan unos " + (n.troncos - troncos) + " troncos para la casa.",
                        "I'm off to chop: I need about " + (n.troncos - troncos) + " more logs for the house.");
            }
            return Fin.SIGUE;
        }
        int piedra = Taller.contar(inv, s -> Plano.acepta(Plano.Rol.BASE, s));
        if (piedra < n.piedra && Progreso.bestPickTier(villager) > 0) {
            accion = Accion.CANTERA;
            subiendoCantera = false;
            canteraQuieto = 0;
            irTicks = 0;
            camino = null;
            hace("saco piedra de mi cantera para la base", "digging stone for the footing at my quarry");
            if (now - avisoPiedra > 2400) {
                avisoPiedra = now;
                Voluntad.contar(villager, "Necesito piedra para la base: voy a cavar una cantera aquí al lado.",
                        "I need stone for the footing: I'm digging a quarry right next to it.");
            }
            return Fin.SIGUE;
        }
        if (n.arena > 0 && !obra.sinVidrio) {
            accion = Accion.ARENA;
            arenaVista = null;
            arenaMeta = Taller.contar(inv, Taller.ARENA) + n.arena;
            task.explorar().reset();
            hace("busco arena para los vidrios", "looking for sand for the windows");
            if (now - avisoArena > 2400) {
                avisoArena = now;
                Voluntad.contar(villager, "Para los vidrios necesito arena: voy a buscarla.", "I need sand for the windows: I'm going to look for some.");
            }
            return Fin.SIGUE;
        }
        if (fuego.ocupado()) {
            hace("espero a que el horno termine", "waiting for the furnace to finish");
            accion = Accion.ESPERAR;
            espera = 60;
            Retina.look(world, villager);
            return Fin.SIGUE;
        }
        // nothing it can do now: a look round, and another try in a moment
        hace("pienso cómo seguir con la casa", "working out how to go on with the house");
        accion = Accion.ESPERAR;
        espera = 40;
        Retina.look(world, villager);
        return Fin.SIGUE;
    }

    /** Fells trees ({@link Progreso}) until it carries the load it set out for, or no tree is left to find. */
    private void lena(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        Progreso progreso = task.progreso();
        long now = world.getTime();
        int logs = Progreso.troncos(villager);
        if (logs >= lenaObjetivo) {
            progreso.olvidarPedidos(world, villager);
            accion = Accion.PENSAR;
            proximoPensar = now + 5;
            return;
        }
        if (!progreso.pidiendo()) {
            progreso.pedirTroncos(lenaObjetivo);
        }
        boolean busy = progreso.tick(world, villager, (k, p) -> {
        });
        if (progreso.sinArboles()) {
            progreso.olvidarPedidos(world, villager);
            sinArbolesHasta = now + 6000;
            accion = Accion.PENSAR;
            Voluntad.contar(villager, "No encuentro más árboles por aquí. Si me traes troncos o tablas, sigo con la casa; si no, vuelvo a buscar en un rato.",
                    "I can't find any more trees around here. If you bring me logs or planks I'll go on with the house; otherwise I'll look again in a while.");
            return;
        }
        if (busy) {
            lenaQuieto = 0;
            return;
        }
        if (++lenaQuieto > 1200) {
            progreso.olvidarPedidos(world, villager);
            sinArbolesHasta = now + 2400;
            accion = Accion.PENSAR;
        }
    }

    // ------------------------------------------------------------------ the quarry

    /** The feet cell of step {@code i} of its quarry: down one each step to its depth, then straight on. */
    private BlockPos peldano(int i) {
        BlockPos s = obra.cantera;
        return s.offset(obra.canteraDir, i).down(Math.min(i, PROFUNDIDAD));
    }

    /** Where its quarry starts: a spot of open ground a few steps behind (or beside) the house, going down away from it. */
    @Nullable
    private BlockPos elegirCantera(ServerWorld world, VillagerEntityMCA villager) {
        Direction back = obra.frente.getOpposite();
        Direction[] order = {back, obra.frente.rotateYClockwise(), obra.frente.rotateYCounterclockwise(), obra.frente};
        BlockPos center = obra.centro();
        ObrasMundo obras = ObrasMundo.get(world);
        for (Direction d : order) {
            for (int dist = 7; dist <= 12; dist++) {
                BlockPos col = center.offset(d, dist);
                int y = Sitio.suelo(world, col.getX(), col.getZ());
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos start = new BlockPos(col.getX(), y + 1, col.getZ());
                if (!Motor.standCell(world, start) || obra.cerca(start, 2)) {
                    continue;
                }
                boolean ok = true;
                for (int i = 1; i <= 4 && ok; i++) {
                    BlockPos step = start.offset(d, i).down(i);
                    Obra other = obras.en(step);
                    ok = other == null && !obra.cerca(step, 2) && Obras.mayBreak(world, villager, step) && world.getBlockState(step).getFluidState().isEmpty();
                }
                if (ok) {
                    obra.canteraDir = d;
                    return start;
                }
            }
        }
        return null;
    }

    /** Down its quarry staircase, a step cut at a time, till it carries the stone it needs; then back up the same steps. */
    private void cantera(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        Motor motor = task.motor();
        long now = world.getTime();
        if ((villager.age % 5) == 0) {
            Hands.collect(world, villager, Mining.pies(villager), 3.0);
        }
        Necesidad n = necesidad(world, villager, leer(world, villager, now), fuego(world));
        int piedra = Taller.contar(villager.getInventory(), s -> Plano.acepta(Plano.Rol.BASE, s));
        if (obra.cantera == null) {
            obra.cantera = elegirCantera(world, villager);
            obra.canteraPasos = 0;
            ObrasMundo.get(world).cambio();
            if (obra.cantera == null) {
                Voluntad.contar(villager, "No encuentro dónde sacar piedra cerca de la casa. ¿Me traes un poco de piedra?",
                        "I can't find anywhere to dig stone near the house. Could you bring me some stone?");
                accion = Accion.ESPERAR;
                espera = 1200;
                return;
            }
        }
        List<BlockPos> steps = new ArrayList<>();
        for (int i = 0; i <= obra.canteraPasos; i++) {
            steps.add(peldano(i));
        }
        BlockPos feet = Mining.pies(villager);
        int here = steps.indexOf(feet);
        if (subiendoCantera || piedra >= n.piedra) {
            subiendoCantera = true;
            if (here == 0 || here < 0 && !Mining.onSurface(world, feet) && ++canteraQuieto > 600) {
                accion = Accion.PENSAR;
                proximoPensar = now + 5;
                return;
            }
            if (here < 0) {
                // somewhere on the land: done with the quarry
                accion = Accion.PENSAR;
                return;
            }
            List<BlockPos> up = new ArrayList<>(steps.subList(0, here + 1));
            java.util.Collections.reverse(up);
            if (camino == null || !camino.equals(up)) {
                camino = up;
            }
            hace("subo de la cantera", "climbing out of the quarry");
            Motor.Result r = motor.walkPath(world, villager, camino);
            if (r == Motor.Result.ARRIVED) {
                camino = null;
                accion = Accion.PENSAR;
            } else if (r == Motor.Result.BLOCKED) {
                camino = null;
                motor.reset(world, villager);
                // the steps are its own: it cuts its way up them again
                Motor.Result s = motor.step(world, villager, steps.get(Math.max(0, here - 1)), true, true);
                if (s == Motor.Result.BLOCKED && ++canteraQuieto > 200) {
                    accion = Accion.PENSAR;
                }
            }
            return;
        }
        if (here < 0) {
            // to the top of the staircase first
            irTicks = Math.max(irTicks, 0);
            Motor.Result r = andar(world, villager, motor, steps.get(0), 1200);
            if (r == Motor.Result.BLOCKED) {
                obra.cantera = null;
                irTicks = 0;
                accion = Accion.PENSAR;
            } else if (r == Motor.Result.ARRIVED) {
                irTicks = 0;
            }
            return;
        }
        if (here < obra.canteraPasos) {
            // down the steps already cut
            List<BlockPos> down = new ArrayList<>(steps.subList(here, steps.size()));
            if (camino == null || !camino.equals(down)) {
                camino = down;
            }
            Motor.Result r = motor.walkPath(world, villager, camino);
            if (r == Motor.Result.BLOCKED) {
                camino = null;
                motor.reset(world, villager);
                Motor.Result s = motor.step(world, villager, steps.get(here + 1), true, false);
                if (s == Motor.Result.BLOCKED) {
                    subiendoCantera = true;
                }
            } else if (r == Motor.Result.ARRIVED) {
                camino = null;
            }
            return;
        }
        // at the face: coal it sees right there comes out too (the torches), then the next step
        if (n.carbonPorHacer > 0 || n.antorchas > 0 && Taller.contar(villager.getInventory(), Taller.CARBON) == 0) {
            BlockPos coal = carbonCerca(world, villager);
            if (coal != null) {
                int r = breaker.tick(world, villager, coal);
                if (r != Hands.Breaker.RUNNING) {
                    Hands.collect(world, villager, coal, 3.0);
                }
                return;
            }
        }
        if (obra.canteraPasos >= CANTERA_MAX) {
            // this staircase is long enough: a new one next time
            obra.cantera = null;
            subiendoCantera = true;
            return;
        }
        hace("cavo la cantera para sacar piedra", "digging the quarry for stone");
        BlockPos next = peldano(obra.canteraPasos + 1);
        Motor.Result r = motor.step(world, villager, next, true, false);
        if (r == Motor.Result.ARRIVED) {
            obra.canteraPasos++;
            ObrasMundo.get(world).cambio();
            avance(now);
        } else if (r == Motor.Result.BLOCKED) {
            // water, lava, bedrock or something not its own: this way is done
            MCA.LOGGER.info("[Tropeles] {} deja la cantera en el paso {}: no puede seguir", villager.getName().getString(), obra.canteraPasos);
            obra.cantera = null;
            subiendoCantera = true;
            ObrasMundo.get(world).cambio();
        }
    }

    /** Coal ore its eye sees within reach right now. */
    @Nullable
    private static BlockPos carbonCerca(ServerWorld world, VillagerEntityMCA villager) {
        Vec3d eye = villager.getEyePos();
        for (BlockPos p : BlockPos.iterateOutwards(Mining.pies(villager), 3, 3, 3)) {
            BlockState s = world.getBlockState(p);
            if ((s.isOf(Blocks.COAL_ORE) || s.isOf(Blocks.DEEPSLATE_COAL_ORE)) && caraVisible(world, eye, p, villager) != null) {
                return p.toImmutable();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ sand and dirt

    /** Sand it saw (or goes looking for): dug block by block, a pile at a time. */
    private void arena(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        long now = world.getTime();
        arenaFalta = 1;
        if (Taller.contar(villager.getInventory(), Taller.ARENA) >= arenaMeta) {
            task.explorar().reset();
            accion = Accion.PENSAR;
            return;
        }
        if ((arenaVista == null || !esArena(world.getBlockState(arenaVista)) || fallando(arenaVista, now)) && (villager.age % 10) == 0) {
            arenaVista = arenaVista(world, villager, now);
        }
        if (arenaVista != null) {
            task.explorar().reset();
            if (!ir(world, villager, new Objetivo(Tipo.RECOGER, arenaVista, null), false)) {
                fallo(arenaVista, now + 2400);
                arenaVista = null;
            }
            return;
        }
        Explorar explorar = task.explorar();
        if (!explorar.tick(world, villager, Explorar.Busca.ARENA) || explorar.exhausted()) {
            explorar.reset();
            obra.sinVidrio = true;
            ObrasMundo.get(world).cambio();
            accion = Accion.PENSAR;
            MCA.LOGGER.info("[Tropeles] {} no encontró arena: la casa queda con las ventanas abiertas", villager.getName().getString());
            Voluntad.contar(villager, "No encontré arena por ningún lado: dejo las ventanas abiertas por ahora. Si me traes arena o vidrio, las pongo.",
                    "I couldn't find sand anywhere: I'm leaving the windows open for now. If you bring me sand or glass, I'll put them in.");
        }
    }

    /** The nearest sand it has seen that is still there and nobody's. */
    @Nullable
    private BlockPos arenaVista(ServerWorld world, VillagerEntityMCA villager, long now) {
        BlockPos feet = Mining.pies(villager);
        BlockPos best = null;
        double bestD = 96 * 96;
        for (VisualMemory.SeenBlock seen : Retina.memory(villager).blocks(s -> esArena(s.state))) {
            double d = seen.pos.getSquaredDistance(feet);
            if (d >= bestD || fallando(seen.pos, now) || !esArena(world.getBlockState(seen.pos)) || obra.cerca(seen.pos, 2)) {
                continue;
            }
            if (!Obras.mayBreak(world, villager, seen.pos)) {
                continue;
            }
            best = seen.pos.toImmutable();
            bestD = d;
        }
        return best;
    }

    /** Open ground round the site to dig a block of dirt from (never the building's, never a path or a field). */
    @Nullable
    private BlockPos tierraCerca(ServerWorld world, VillagerEntityMCA villager) {
        BlockPos center = obra.centro();
        for (int r = 6; r <= 14; r++) {
            for (int i = 0; i < 16; i++) {
                double a = 2 * Math.PI * i / 16;
                int x = center.getX() + (int) Math.round(Math.cos(a) * r);
                int z = center.getZ() + (int) Math.round(Math.sin(a) * r);
                int y = Sitio.suelo(world, x, z);
                if (y == Integer.MIN_VALUE) {
                    continue;
                }
                BlockPos p = new BlockPos(x, y, z);
                if (esTierra(world.getBlockState(p)) && !obra.cerca(p, 2) && Obras.mayBreak(world, villager, p) && !Obras.cropAt(world, p.up())
                        && !fallando(p, world.getTime())) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean esArena(BlockState s) {
        return s.isOf(Blocks.SAND) || s.isOf(Blocks.RED_SAND);
    }

    private static boolean esTierra(BlockState s) {
        return s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT) || s.isOf(Blocks.COARSE_DIRT) || s.isOf(Blocks.PODZOL) || s.isOf(Blocks.ROOTED_DIRT)
                || s.isOf(Blocks.MYCELIUM);
    }

    // ------------------------------------------------------------------ finishing

    /** The last block is set: scaffolds and its workshop come down, what is left over goes in the house's chest. */
    private Fin terminar(ServerWorld world, VillagerEntityMCA villager, ProspectingTask task) {
        long now = world.getTime();
        for (Iterator<BlockPos> it = obra.andamios.iterator(); it.hasNext(); ) {
            BlockPos a = it.next();
            if (world.getBlockState(a).isReplaceable() || Obras.kind(world, villager, a) != Obras.Kind.OWN) {
                it.remove();
            }
        }
        for (BlockPos a : obra.andamios) {
            if (!fallando(a, now) && ir(world, villager, new Objetivo(Tipo.ANDAMIO, a, null), false)) {
                hace("quito los andamios", "taking the scaffolds down");
                return Fin.SIGUE;
            }
        }
        for (BlockPos own : new BlockPos[]{obra.mesa, obra.horno}) {
            if (own != null && !fallando(own, now) && !world.getBlockState(own).isAir() && Obras.kind(world, villager, own) == Obras.Kind.OWN
                    && ir(world, villager, new Objetivo(Tipo.ANDAMIO, own, null), false)) {
                hace("recojo mi mesa y mi horno", "picking up my table and furnace");
                return Fin.SIGUE;
            }
        }
        BlockPos chest = null;
        for (Obra.Celda c : obra.celdas()) {
            if (c.rol() == Plano.Rol.COFRE && world.getBlockEntity(c.pos()) instanceof ChestBlockEntity) {
                chest = c.pos();
            }
        }
        if (chest != null && !fallando(chest, now) && cuentaSobrante(villager.getInventory()) > 0
                && ir(world, villager, new Objetivo(Tipo.COFRE, chest, null), false)) {
            hace("guardo lo que sobró en el cofre de la casa", "putting the leftovers in the house's chest");
            return Fin.SIGUE;
        }
        soltar(world, villager, task);
        return Fin.LISTA;
    }

    /** What is left over from the house, worth leaving for its owner. */
    private static boolean sobrante(ItemStack s) {
        // its coal and torches stay with it: a miner lights its tunnels with them
        return sirve(s) && !s.isOf(Items.DIRT) && !Taller.CARBON.test(s) && !s.isOf(Items.TORCH);
    }

    private static int cuentaSobrante(SimpleInventory inv) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (sobrante(inv.getStack(i))) {
                n += inv.getStack(i).getCount();
            }
        }
        return n;
    }

    /** Leftovers into the chest, the lid opened and shut like a player's. */
    private void guardar(ServerWorld world, VillagerEntityMCA villager, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            fallo(pos, world.getTime() + 6000);
            return;
        }
        Hands.aim(villager, Vec3d.ofCenter(pos));
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, 1.0F);
        SimpleInventory inv = villager.getInventory();
        int moved = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !sobrante(s)) {
                continue;
            }
            moved += meter(chest, s);
        }
        inv.markDirty();
        chest.markDirty();
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, 1.0F);
        villager.swingHand(Hand.MAIN_HAND);
        if (moved == 0) {
            fallo(pos, world.getTime() + 6000); // full
        }
        MCA.LOGGER.info("[Tropeles] {} guarda {} cosas que sobraron en el cofre de la casa", villager.getName().getString(), moved);
    }

    /** As much of {@code s} as fits into {@code into}: onto what is already there, then into empty slots. */
    private static int meter(Inventory into, ItemStack s) {
        int moved = 0;
        for (int j = 0; j < into.size() && !s.isEmpty(); j++) {
            ItemStack t = into.getStack(j);
            if (!t.isEmpty() && ItemStack.canCombine(t, s) && t.getCount() < t.getMaxCount()) {
                int k = Math.min(s.getCount(), t.getMaxCount() - t.getCount());
                t.increment(k);
                s.decrement(k);
                moved += k;
            }
        }
        for (int j = 0; j < into.size() && !s.isEmpty(); j++) {
            if (into.getStack(j).isEmpty()) {
                moved += s.getCount();
                into.setStack(j, s.copy());
                s.setCount(0);
            }
        }
        return moved;
    }

    // ------------------------------------------------------------------ what it can tell

    /** Says so when the house is a quarter, half and three quarters done. */
    private void hitos(VillagerEntityMCA villager, Lectura l) {
        if (l.total == 0) {
            return;
        }
        int pct = l.hechas * 100 / l.total;
        int h = pct >= 75 ? 3 : pct >= 50 ? 2 : pct >= 25 ? 1 : 0;
        if (hito < 0) {
            hito = h;
            return;
        }
        if (h > hito) {
            hito = h;
            String[] es = {"", "¡Ya va un cuarto de la casa!", "¡Ya va la mitad de la casa!", "¡Ya casi! Tres cuartos de la casa listos."};
            String[] en = {"", "A quarter of the house is up!", "Half the house is up!", "Almost there! Three quarters of the house done."};
            Voluntad.contar(villager, es[h], en[h]);
            Recuerdos.anotar(villager, "obra", "la casa de " + (obra.paraNombre.isEmpty() ? "alguien" : obra.paraNombre) + " va en " + pct + "%",
                    obra.centro());
        }
    }

    /** What it still has to get, in a few words ("Me falta unos 40 troncos, 33 de piedra para la base."). */
    public static String faltas(ServerWorld world, VillagerEntityMCA villager, Obra o, boolean es) {
        Constructor c = TODOS.get(villager.getUuid());
        if (c == null || c.obra != o) {
            c = new Constructor();
            c.obra = o;
        }
        Lectura l = c.leer(world, villager, world.getTime());
        Necesidad n = c.necesidad(world, villager, l, c.fuego(world));
        SimpleInventory inv = villager.getInventory();
        List<String> needs = new ArrayList<>();
        int logs = Taller.contar(inv, Taller.TRONCOS);
        if (n.troncos > logs) {
            needs.add(es ? "unos " + (n.troncos - logs) + " troncos" : "about " + (n.troncos - logs) + " logs");
        }
        int stone = Taller.contar(inv, s -> Plano.acepta(Plano.Rol.BASE, s));
        if (n.piedra > stone) {
            int k = n.piedra - stone;
            needs.add(es ? k + " de piedra" + (n.horno ? " (la base y un horno)" : " para la base") : k + " stone" + (n.horno ? " (the footing and a furnace)" : " for the footing"));
        }
        if (n.arena > 0) {
            needs.add(es ? n.arena + " de arena para los vidrios" : n.arena + " sand for the windows");
        }
        if (n.antorchas > 0 && Taller.contar(inv, Taller.CARBON) == 0 && n.carbonPorHacer > 0) {
            needs.add(es ? "carbón para " + n.antorchas + " antorchas (o lo hago en el horno con troncos)" : "coal for " + n.antorchas + " torches (or I make charcoal from logs)");
        }
        if (needs.isEmpty()) {
            return es ? "Tengo todo lo que necesito." : "I have everything I need.";
        }
        return (es ? "Me falta " : "I still need ") + String.join(", ", needs) + ".";
    }

    /** How the house is going, for the chat: blocks set, where, what it is doing, what it still needs. */
    public static String estado(ServerWorld world, VillagerEntityMCA villager, Obra o, boolean es) {
        int total = o.total();
        int done = o.hechas(world);
        int pct = total == 0 ? 100 : done * 100 / total;
        Constructor c = TODOS.get(villager.getUuid());
        String doing = c == null || c.obra != o ? (es ? "estoy por retomarla" : "I'm about to get back to it") : c.haciendo(es);
        String pausa = o.pausaJugador ? (es ? " (en pausa: dime que siga con la casa y sigo)" : " (paused: tell me to go on with the house)") : "";
        String vidrio = o.sinVidrio ? (es ? " Las ventanas quedan abiertas: no encontré arena." : " The windows stay open: I couldn't find sand.") : "";
        return es ? "Llevo " + done + " de " + total + " bloques de la casa (" + pct + "%), en " + o.donde() + pausa + ". Ahora " + doing + ". "
                + faltas(world, villager, o, true) + vidrio
                : "I've set " + done + " of the house's " + total + " blocks (" + pct + "%), at " + o.donde() + pausa + ". Right now: " + doing + ". "
                + faltas(world, villager, o, false) + vidrio;
    }

    /** The bill of materials of a whole house, roughly, for the first words about it. */
    public static String materiales(Plano plano, boolean es) {
        int planks = plano.materiales.getOrDefault(Plano.Rol.TABLA, 0) + 6 * ceil(plano.materiales.getOrDefault(Plano.Rol.ESCALON, 0), 4) + 6 + 8 + 4 + 16;
        int logs = plano.materiales.getOrDefault(Plano.Rol.TRONCO, 0) + ceil(planks, 4) + 1;
        int stone = plano.materiales.getOrDefault(Plano.Rol.BASE, 0) + 8;
        int panes = plano.materiales.getOrDefault(Plano.Rol.VIDRIO, 0);
        return es ? "unos " + logs + " troncos, " + stone + " de piedra, arena para " + panes + " vidrios y carbón para las antorchas"
                : "about " + logs + " logs, " + stone + " stone, sand for " + panes + " windows and coal for the torches";
    }
}
