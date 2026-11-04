package net.mca.entity.ai.nucleo;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.Acciones;
import net.mca.server.world.data.FamilyTree;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grounded speech (E_UNGROUNDED in the audit): a language model may only speak of what it was told. A snapshot is
 * taken on the server thread of everything the model was given (the prompt, the talk so far, the player's words, the
 * villager's own answer), of the people of this world and of the bag; then, off the thread, every sentence the model
 * wrote is checked against it, and one that names coordinates it was never given, a person of this world it was told
 * nothing of, or something it says it carries and does not, is dropped. When nothing is left, the villager's own mind
 * answers instead.
 */
public final class Anclaje {
    private static final Pattern TRIPLE = Pattern.compile("(?<![\\d.\\-])(-?\\d{1,7})[ ,;/]+(-?\\d{1,3})[ ,;/]+(-?\\d{1,7})(?![\\d.])");
    private static final Pattern FRASES = Pattern.compile("(?<=[.!?…])\\s+");
    private static final Pattern TENGO = Pattern.compile("(?<![a-z])(?<!no )(?<!ya no )(tengo|llevo|cargo|i have|i ve got|i got|i m carrying|i carry)\\s+"
            + "(?:(?:\\d+|un|una|unos|unas|algo de|algunos|algunas|varios|varias|mucho|mucha|muchos|muchas|harto|harta|hartos|hartas|"
            + "dos|tres|cuatro|cinco|seis|diez|a|an|some|two|three|a few|lots of|plenty of)\\s+)?(?:de\\s+)?([a-z]+(?: de [a-z]+| [a-z]+)?)");

    /** Material words that name many items: a claim about them is not checked. */
    private static final Set<String> VAGAS = Set.of("madera", "piedra", "piedras", "roca", "rocas", "tierra", "mineral", "minerales",
            "lingote", "lingotes", "crudo", "cruda", "wood", "stone", "ore", "ingot", "ingots", "raw", "log", "logs", "cobblestone");

    private final List<int[]> coords;
    private final String contexto;
    private final Set<String> personas;
    private final SimpleInventory bolsa;
    private final String villager;

    private Anclaje(List<int[]> coords, String contexto, Set<String> personas, SimpleInventory bolsa, String villager) {
        this.coords = coords;
        this.contexto = contexto;
        this.personas = personas;
        this.bolsa = bolsa;
        this.villager = villager;
    }

    /** The snapshot, on the server thread; null if it could not be taken (then nothing is filtered). */
    @Nullable
    public static Anclaje de(VillagerEntityMCA v, ServerPlayerEntity player, String contexto) {
        try {
            if (!(v.getWorld() instanceof ServerWorld world)) {
                return null;
            }
            List<int[]> coords = new ArrayList<>();
            Matcher m = TRIPLE.matcher(contexto);
            while (m.find() && coords.size() < 512) {
                int[] c = triple(m);
                if (c != null) {
                    coords.add(c);
                }
            }
            Set<String> personas = new HashSet<>();
            for (VillagerEntityMCA o : world.getEntitiesByClass(VillagerEntityMCA.class, v.getBoundingBox().expand(160), x -> x != v)) {
                personas.add(o.getName().getString());
            }
            for (ServerPlayerEntity o : world.getServer().getPlayerManager().getPlayerList()) {
                personas.add(o.getName().getString());
            }
            FamilyTree tree = FamilyTree.get(world);
            for (UUID id : Nucleo.parientes(v.getRelationships().getFamilyEntry())) {
                tree.getOrEmpty(id).ifPresent(n -> personas.add(n.getName()));
            }
            Set<String> nombres = new HashSet<>();
            for (String p : personas) {
                for (String part : p.split("\\s+")) {
                    if (part.length() >= 3 && Character.isUpperCase(part.charAt(0))) {
                        nombres.add(part);
                    }
                }
            }
            // what it carries: the bag, and what it holds and wears ("tengo una espada" with the sword in its hand is true)
            SimpleInventory inv = v.getInventory();
            EquipmentSlot[] slots = EquipmentSlot.values();
            SimpleInventory bolsa = new SimpleInventory(inv.size() + slots.length);
            for (int i = 0; i < inv.size(); i++) {
                bolsa.setStack(i, inv.getStack(i).copy());
            }
            for (int i = 0; i < slots.length; i++) {
                bolsa.setStack(inv.size() + i, v.getEquippedStack(slots[i]).copy());
            }
            return new Anclaje(coords, " " + Acciones.normalize(contexto) + " ", nombres, bolsa, v.getName().getString());
        } catch (RuntimeException e) {
            MCA.LOGGER.warn("[Tropeles] no se pudo anclar lo que sabe {}: {}", v.getName().getString(), e.toString());
            return null;
        }
    }

    @Nullable
    private static int[] triple(Matcher m) {
        try {
            int y = Integer.parseInt(m.group(2));
            if (y < -64 || y > 320) {
                return null;
            }
            return new int[]{Integer.parseInt(m.group(1)), y, Integer.parseInt(m.group(3))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** What the model said, without the sentences it had no ground for; null when none is left. Any thread. */
    @Nullable
    public String filtrar(String said) {
        String[] frases = FRASES.split(said.trim());
        StringBuilder out = new StringBuilder();
        int dropped = 0;
        for (String f : frases) {
            String why = sinBase(f);
            if (why != null) {
                dropped++;
                MCA.LOGGER.info("[Tropeles] E_UNGROUNDED: {} iba a decir «{}» ({}); esa frase no se dice", villager, f, why);
                continue;
            }
            out.append(out.length() == 0 ? "" : " ").append(f);
        }
        if (dropped == 0) {
            return said;
        }
        return out.length() == 0 ? null : out.toString();
    }

    @Nullable
    private String sinBase(String frase) {
        Matcher m = TRIPLE.matcher(frase);
        while (m.find()) {
            int[] c = triple(m);
            if (c != null && !conocida(c)) {
                return "coordenadas " + c[0] + " " + c[1] + " " + c[2] + " que nadie le dio";
            }
        }
        for (String name : personas) {
            int at = palabra(frase, name);
            if (at > 0 && !Pattern.compile("(?<![a-z])" + Pattern.quote(Acciones.normalize(name)) + "(?![a-z])").matcher(contexto).find()) {
                return "habla de " + name + " sin que le contaran nada de esa persona";
            }
        }
        String n = Acciones.normalize(frase).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ");
        if (!n.contains("cofre") && !n.contains("baul") && !n.contains("chest")) {
            Matcher t = TENGO.matcher(n);
            while (t.find()) {
                String cosa = t.group(2);
                String[] partes = cosa.split(" ");
                String head = partes[0];
                if (!Acciones.esCosa(head) || VAGAS.contains(head)) {
                    continue; // not a thing, or a material too broad to check ("madera" may be logs or planks)
                }
                // only words that name a thing or its material are checked: "pan fresco" is bread whatever "fresco"
                // says of it, while "espada de piedra" still has to be a stone sword
                StringBuilder base = new StringBuilder(head);
                for (int i = 1; i < partes.length; i++) {
                    if (Acciones.esCosa(partes[i])) {
                        base.append(' ').append(partes[i]);
                    }
                }
                Acciones.Request req = Acciones.read(base.toString());
                if (!req.words().isEmpty() && Acciones.find(bolsa, req) < 0) {
                    return "dice que lleva " + cosa + " y no lo lleva";
                }
            }
        }
        return null;
    }

    private boolean conocida(int[] c) {
        for (int[] k : coords) {
            if (Math.abs(k[0] - c[0]) <= 3 && Math.abs(k[1] - c[1]) <= 4 && Math.abs(k[2] - c[2]) <= 3) {
                return true;
            }
        }
        return false;
    }

    /** Where {@code name} stands in {@code text} as a whole word, as written (a capital), or -1. */
    private static int palabra(String text, String name) {
        int from = 0;
        while (true) {
            int i = text.indexOf(name, from);
            if (i < 0) {
                return -1;
            }
            int end = i + name.length();
            boolean before = i == 0 || !Character.isLetter(text.charAt(i - 1));
            boolean after = end >= text.length() || !Character.isLetter(text.charAt(end));
            if (before && after) {
                // the first word of a sentence is capitalised anyway: a name there may be a common word ("Luz no hay")
                String head = text.substring(0, i).replaceAll("[¡¿\"«\\s]", "");
                return head.isEmpty() ? 0 : i;
            }
            from = end;
        }
    }
}
