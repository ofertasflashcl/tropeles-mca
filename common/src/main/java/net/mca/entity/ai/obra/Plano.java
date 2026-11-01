package net.mca.entity.ai.obra;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.StairShape;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The blueprint of a building, in its own coordinates (front to the north, -z; x and z from 0): every block with the
 * exact state it should end up in and what kind of piece it is, plus the cells that must be left empty (the rooms,
 * the attic, the way in). Pure data, shared by the server (what to build) and the client (the translucent preview).
 * The first one is a two-storey wooden house, 7 by 7: a cobblestone footing with a wooden floor, log corners, plank
 * walls, a door with a doorstep, fifteen glass windows, a staircase inside up to the upper floor, a gable roof of
 * stairs, four torches, a crafting table and a chest.
 */
public final class Plano {
    /** What a piece is, which says what it is made of and when it counts as done. */
    public enum Rol {
        BASE("piedra", "stone", true),
        TRONCO("tronco", "log", true),
        TABLA("tabla", "planks", true),
        ESCALON("escalón", "stairs", true),
        PUERTA("puerta", "door", false),
        /** The upper half of the door: never placed on its own, it comes with the lower one. */
        PUERTA_ARRIBA("puerta", "door", false),
        VIDRIO("vidrio", "glass pane", false),
        ANTORCHA("antorcha", "torch", false),
        MESA("mesa de crafteo", "crafting table", false),
        COFRE("cofre", "chest", false),
        /** A cell that must end up empty: a room, the attic, the way to the door. */
        AIRE("aire", "air", false);

        public final String es;
        public final String en;
        /** Part of the frame (floor, walls, roof): built layer by layer from the bottom up. */
        public final boolean estructura;

        Rol(String es, String en, boolean estructura) {
            this.es = es;
            this.en = en;
            this.estructura = estructura;
        }

        public boolean bloque() {
            return this != AIRE && this != PUERTA_ARRIBA;
        }
    }

    /** One block of the plan: where (in the plan's own coordinates), the exact state, and its kind. */
    public record Pieza(BlockPos rel, BlockState state, Rol rol) {
    }

    /** The wood a building is made of: its log, planks, stairs and door. */
    public enum Madera {
        ROBLE("roble", "oak", Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS, Blocks.OAK_DOOR),
        ABETO("abeto", "spruce", Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_DOOR),
        ABEDUL("abedul", "birch", Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_STAIRS, Blocks.BIRCH_DOOR),
        JUNGLA("jungla", "jungle", Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_DOOR),
        ACACIA("acacia", "acacia", Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS, Blocks.ACACIA_DOOR),
        ROBLE_OSCURO("roble oscuro", "dark oak", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_DOOR),
        MANGLE("mangle", "mangrove", Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_DOOR),
        CEREZO("cerezo", "cherry", Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_STAIRS, Blocks.CHERRY_DOOR);

        public final String es;
        public final String en;
        public final Block log;
        public final Block planks;
        public final Block stairs;
        public final Block door;

        Madera(String es, String en, Block log, Block planks, Block stairs, Block door) {
            this.es = es;
            this.en = en;
            this.log = log;
            this.planks = planks;
            this.stairs = stairs;
            this.door = door;
        }

        /** The wood of a log, planks, stairs or door block, or null when it is none of these. */
        @Nullable
        public static Madera de(Block block) {
            for (Madera m : values()) {
                if (m.log == block || m.planks == block || m.stairs == block || m.door == block) {
                    return m;
                }
            }
            return null;
        }

        /** The wood named in an order ("de abeto", "spruce house"), or null when it names none. */
        @Nullable
        public static Madera palabra(String words) {
            String t = words.toLowerCase(Locale.ROOT);
            if (t.matches(".*(roble oscuro|dark oak|madera oscura).*")) {
                return ROBLE_OSCURO;
            }
            if (t.matches(".*(abeto|pino|spruce|pine).*")) {
                return ABETO;
            }
            if (t.matches(".*(abedul|birch).*")) {
                return ABEDUL;
            }
            if (t.matches(".*(jungla|selva|jungle).*")) {
                return JUNGLA;
            }
            if (t.matches(".*(acacia).*")) {
                return ACACIA;
            }
            if (t.matches(".*(mangle|manglar|mangrove).*")) {
                return MANGLE;
            }
            if (t.matches(".*(cerezo|sakura|cherry).*")) {
                return CEREZO;
            }
            if (t.matches(".*(roble|oak).*")) {
                return ROBLE;
            }
            return null;
        }
    }

    public final String nombre;
    public final String nombreEn;
    /** Every block, ground floor first; the empty cells last. */
    public final List<Pieza> piezas;
    /** How many of each kind of piece (the door once). */
    public final Map<Rol, Integer> materiales;
    public final int minX;
    public final int minZ;
    public final int maxX;
    public final int maxZ;
    public final int alto;

    private Plano(String nombre, String nombreEn, List<Pieza> piezas) {
        this.nombre = nombre;
        this.nombreEn = nombreEn;
        this.piezas = Collections.unmodifiableList(piezas);
        Map<Rol, Integer> m = new EnumMap<>(Rol.class);
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        int y1 = 0;
        for (Pieza p : piezas) {
            if (p.rol().bloque()) {
                m.merge(p.rol(), 1, Integer::sum);
            }
            x0 = Math.min(x0, p.rel().getX());
            z0 = Math.min(z0, p.rel().getZ());
            x1 = Math.max(x1, p.rel().getX());
            z1 = Math.max(z1, p.rel().getZ());
            y1 = Math.max(y1, p.rel().getY());
        }
        this.materiales = Collections.unmodifiableMap(m);
        this.minX = x0;
        this.minZ = z0;
        this.maxX = x1;
        this.maxZ = z1;
        this.alto = y1 + 1;
    }

    private static final Map<Madera, Plano> CASAS = new EnumMap<>(Madera.class);

    /** The two-storey house in this wood. */
    public static synchronized Plano casa(Madera madera) {
        return CASAS.computeIfAbsent(madera, Plano::hacerCasa);
    }

    /** The house's own centre (in the plan's coordinates): where the site search puts it. */
    public static final BlockPos CENTRO_CASA = new BlockPos(3, 0, 3);

    private static Plano hacerCasa(Madera m) {
        Map<BlockPos, Pieza> p = new LinkedHashMap<>();
        BlockState log = m.log.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
        BlockState planks = m.planks.getDefaultState();
        BlockState stone = Blocks.COBBLESTONE.getDefaultState();
        // the footing: a ring of stone round a wooden floor, flush with the ground, and a stone step at the door
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                boolean ring = x == 0 || x == 6 || z == 0 || z == 6;
                put(p, x, 0, z, ring ? stone : planks, ring ? Rol.BASE : Rol.TABLA);
            }
        }
        put(p, 3, 0, -1, stone, Rol.BASE);
        // the door, set before the walls so its cells are never taken by planks
        BlockState door = m.door.getDefaultState().with(DoorBlock.FACING, Direction.SOUTH).with(DoorBlock.HINGE, DoorHinge.LEFT)
                .with(DoorBlock.OPEN, false).with(DoorBlock.POWERED, false);
        put(p, 3, 1, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER), Rol.PUERTA);
        put(p, 3, 2, 0, door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER), Rol.PUERTA_ARRIBA);
        // windows: six below, nine above
        int[][] windows = {{1, 2, 0}, {5, 2, 0}, {2, 2, 6}, {4, 2, 6}, {0, 2, 2}, {0, 2, 4},
                {1, 6, 0}, {3, 6, 0}, {5, 6, 0}, {2, 6, 6}, {4, 6, 6}, {0, 6, 2}, {0, 6, 4}, {6, 6, 2}, {6, 6, 4}};
        for (int[] w : windows) {
            put(p, w[0], w[1], w[2], Blocks.GLASS_PANE.getDefaultState(), Rol.VIDRIO);
        }
        // the staircase up, along the east wall, climbing to the south
        BlockState step = m.stairs.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH)
                .with(Properties.BLOCK_HALF, BlockHalf.BOTTOM).with(Properties.STAIR_SHAPE, StairShape.STRAIGHT);
        put(p, 5, 1, 2, step, Rol.ESCALON);
        put(p, 5, 2, 3, step, Rol.ESCALON);
        put(p, 5, 3, 4, step, Rol.ESCALON);
        put(p, 5, 4, 5, step, Rol.ESCALON);
        // the walls of both floors, logs at the corners
        for (int y : new int[]{1, 2, 3, 5, 6, 7}) {
            for (int x = 0; x <= 6; x++) {
                for (int z = 0; z <= 6; z++) {
                    if (x != 0 && x != 6 && z != 0 && z != 6) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                    put(p, x, y, z, corner ? log : planks, corner ? Rol.TRONCO : Rol.TABLA);
                }
            }
        }
        // the upper floor, open over the staircase
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                if (x == 5 && z >= 2 && z <= 4) {
                    continue;
                }
                boolean corner = (x == 0 || x == 6) && (z == 0 || z == 6);
                put(p, x, 4, z, corner ? log : planks, corner ? Rol.TRONCO : Rol.TABLA);
            }
        }
        // the gable roof: three rows of stairs on each side and a ridge of planks, the length of the house
        for (int z = 0; z <= 6; z++) {
            BlockState east = m.stairs.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST)
                    .with(Properties.BLOCK_HALF, BlockHalf.BOTTOM).with(Properties.STAIR_SHAPE, StairShape.STRAIGHT);
            BlockState west = east.with(Properties.HORIZONTAL_FACING, Direction.WEST);
            put(p, 0, 8, z, east, Rol.ESCALON);
            put(p, 1, 9, z, east, Rol.ESCALON);
            put(p, 2, 10, z, east, Rol.ESCALON);
            put(p, 3, 10, z, planks, Rol.TABLA);
            put(p, 4, 10, z, west, Rol.ESCALON);
            put(p, 5, 9, z, west, Rol.ESCALON);
            put(p, 6, 8, z, west, Rol.ESCALON);
        }
        // the gables close the attic at the front and the back
        for (int z : new int[]{0, 6}) {
            for (int x = 1; x <= 5; x++) {
                put(p, x, 8, z, planks, Rol.TABLA);
            }
            for (int x = 2; x <= 4; x++) {
                put(p, x, 9, z, planks, Rol.TABLA);
            }
        }
        // light: one torch on the back wall of each floor, two by the door outside
        BlockState torch = Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        put(p, 3, 2, 5, torch, Rol.ANTORCHA);
        put(p, 3, 6, 5, torch, Rol.ANTORCHA);
        put(p, 2, 2, -1, torch, Rol.ANTORCHA);
        put(p, 4, 2, -1, torch, Rol.ANTORCHA);
        // a crafting table and a chest by the back wall downstairs
        put(p, 1, 1, 5, Blocks.CRAFTING_TABLE.getDefaultState(), Rol.MESA);
        put(p, 2, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH), Rol.COFRE);
        // what stays empty: both rooms, the hole over the stairs, the attic, the way to the door
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y : new int[]{1, 2, 3, 5, 6, 7, 8}) {
                    put(p, x, y, z, air, Rol.AIRE);
                }
            }
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 1; z <= 5; z++) {
                put(p, x, 9, z, air, Rol.AIRE);
            }
        }
        for (int z = 2; z <= 4; z++) {
            put(p, 5, 4, z, air, Rol.AIRE);
        }
        put(p, 3, 1, -1, air, Rol.AIRE);
        put(p, 3, 2, -1, air, Rol.AIRE);
        // the panes join what is beside them in the wall
        for (Map.Entry<BlockPos, Pieza> e : p.entrySet()) {
            Pieza w = e.getValue();
            if (w.rol() != Rol.VIDRIO) {
                continue;
            }
            BlockState s = w.state();
            for (Direction d : Direction.Type.HORIZONTAL) {
                Pieza n = p.get(e.getKey().offset(d));
                boolean join = n != null && n.rol() != Rol.AIRE && n.rol() != Rol.ANTORCHA && n.rol() != Rol.ESCALON;
                s = s.with(lado(d), join);
            }
            e.setValue(new Pieza(w.rel(), s, Rol.VIDRIO));
        }
        // blocks bottom up, the empty cells last
        List<Pieza> list = new ArrayList<>();
        for (Pieza piece : p.values()) {
            if (piece.rol() != Rol.AIRE) {
                list.add(piece);
            }
        }
        list.sort((a, b) -> a.rel().getY() != b.rel().getY() ? Integer.compare(a.rel().getY(), b.rel().getY()) : 0);
        for (Pieza piece : p.values()) {
            if (piece.rol() == Rol.AIRE) {
                list.add(piece);
            }
        }
        return new Plano("casa de dos pisos", "two-storey house", list);
    }

    /** The first piece set at a cell stays: the door and windows are put before the walls they sit in. */
    private static void put(Map<BlockPos, Pieza> p, int x, int y, int z, BlockState state, Rol rol) {
        BlockPos at = new BlockPos(x, y, z);
        p.putIfAbsent(at, new Pieza(at, state, rol));
    }

    private static net.minecraft.state.property.BooleanProperty lado(Direction d) {
        return switch (d) {
            case NORTH -> Properties.NORTH;
            case SOUTH -> Properties.SOUTH;
            case EAST -> Properties.EAST;
            default -> Properties.WEST;
        };
    }

    // ------------------------------------------------------------------ turning the plan to face a way

    /** The turn that makes a plan drawn with its front to the north face {@code frente}. */
    public static BlockRotation giro(Direction frente) {
        return switch (frente) {
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Where a cell of the plan is in the world, for a plan set at {@code origen} turned by {@code giro}. */
    public static BlockPos enMundo(BlockPos origen, BlockRotation giro, BlockPos rel) {
        return origen.add(rel.rotate(giro));
    }

    // ------------------------------------------------------------------ what counts as done, and what each piece takes

    /** Whether {@code actual} (what is in the world at that cell) is this piece done: the right kind, the right way round. */
    public static boolean hecho(Rol rol, BlockState planned, BlockState actual) {
        return switch (rol) {
            case BASE -> piedra(actual);
            case TRONCO -> actual.isIn(BlockTags.LOGS) && mismo(planned, actual, Properties.AXIS);
            case TABLA -> actual.isIn(BlockTags.PLANKS);
            case ESCALON -> actual.isIn(BlockTags.WOODEN_STAIRS) && mismo(planned, actual, Properties.HORIZONTAL_FACING)
                    && mismo(planned, actual, Properties.BLOCK_HALF);
            case PUERTA -> actual.isIn(BlockTags.WOODEN_DOORS) && actual.contains(DoorBlock.HALF) && actual.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            case PUERTA_ARRIBA -> actual.isIn(BlockTags.WOODEN_DOORS) && actual.contains(DoorBlock.HALF) && actual.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
            case VIDRIO -> actual.isOf(Blocks.GLASS_PANE) || actual.isOf(Blocks.GLASS) || actual.getBlock() instanceof StainedGlassPaneBlock
                    || actual.getBlock() instanceof StainedGlassBlock;
            case ANTORCHA -> actual.isOf(Blocks.WALL_TORCH) || actual.isOf(Blocks.SOUL_WALL_TORCH) || actual.isOf(Blocks.TORCH);
            case MESA -> actual.isOf(Blocks.CRAFTING_TABLE);
            case COFRE -> actual.isOf(Blocks.CHEST) || actual.isOf(Blocks.TRAPPED_CHEST) || actual.isOf(Blocks.BARREL);
            case AIRE -> actual.isAir();
        };
    }

    /** Stone good for a footing: cobble, stone, bricks of stone, deepslate. */
    public static boolean piedra(BlockState s) {
        return s.isOf(Blocks.COBBLESTONE) || s.isOf(Blocks.MOSSY_COBBLESTONE) || s.isOf(Blocks.STONE) || s.isOf(Blocks.STONE_BRICKS)
                || s.isOf(Blocks.COBBLED_DEEPSLATE) || s.isOf(Blocks.DEEPSLATE) || s.isOf(Blocks.BLACKSTONE) || s.isOf(Blocks.ANDESITE)
                || s.isOf(Blocks.DIORITE) || s.isOf(Blocks.GRANITE) || s.isOf(Blocks.TUFF) || s.isOf(Blocks.SMOOTH_STONE);
    }

    private static <T extends Comparable<T>> boolean mismo(BlockState a, BlockState b, Property<T> p) {
        return !a.contains(p) || b.contains(p) && a.get(p).equals(b.get(p));
    }

    /** Whether this stack can be set as this piece. */
    public static boolean acepta(Rol rol, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Block block = stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
        return switch (rol) {
            case BASE -> stack.isIn(ItemTags.STONE_TOOL_MATERIALS) || stack.isOf(Items.STONE) || stack.isOf(Items.STONE_BRICKS)
                    || stack.isOf(Items.MOSSY_COBBLESTONE);
            case TRONCO -> stack.isIn(ItemTags.LOGS) && block != null && block.getDefaultState().contains(Properties.AXIS);
            case TABLA -> stack.isIn(ItemTags.PLANKS);
            case ESCALON -> block != null && block.getDefaultState().isIn(BlockTags.WOODEN_STAIRS);
            case PUERTA -> block != null && block.getDefaultState().isIn(BlockTags.WOODEN_DOORS);
            case VIDRIO -> stack.isOf(Items.GLASS_PANE);
            case ANTORCHA -> stack.isOf(Items.TORCH);
            case MESA -> stack.isOf(Items.CRAFTING_TABLE);
            case COFRE -> stack.isOf(Items.CHEST);
            default -> false;
        };
    }

    /** The state this stack makes at that piece: its own block, turned and set like the plan's. */
    public static BlockState estadoPara(Rol rol, BlockState planned, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) {
            return planned;
        }
        Block block = bi.getBlock();
        BlockState s = block.getDefaultState();
        return switch (rol) {
            case TRONCO -> con(s, planned, Properties.AXIS);
            case ESCALON -> con(con(con(s, planned, Properties.HORIZONTAL_FACING), planned, Properties.BLOCK_HALF), planned, Properties.STAIR_SHAPE);
            case PUERTA -> con(con(s, planned, DoorBlock.FACING), planned, DoorBlock.HINGE).with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                    .with(DoorBlock.OPEN, false).with(DoorBlock.POWERED, false);
            case VIDRIO, ANTORCHA -> planned;
            case COFRE -> con(s, planned, Properties.HORIZONTAL_FACING);
            default -> s;
        };
    }

    private static <T extends Comparable<T>> BlockState con(BlockState to, BlockState from, Property<T> p) {
        return to.contains(p) && from.contains(p) ? to.with(p, from.get(p)) : to;
    }

    /** The item a piece is made from, for the bill of materials: what it would ask for. */
    public static Item item(Rol rol, Madera m) {
        return switch (rol) {
            case BASE -> Items.COBBLESTONE;
            case TRONCO -> m.log.asItem();
            case TABLA -> m.planks.asItem();
            case ESCALON -> m.stairs.asItem();
            case PUERTA, PUERTA_ARRIBA -> m.door.asItem();
            case VIDRIO -> Items.GLASS_PANE;
            case ANTORCHA -> Items.TORCH;
            case MESA -> Items.CRAFTING_TABLE;
            case COFRE -> Items.CHEST;
            case AIRE -> Items.AIR;
        };
    }
}
