package com.LastEcho.lastecho_npc;

import com.periut.retroapi.entrypoint.RetroModInitializer;
import com.periut.retroapi.entity.RetroEntities;
import com.periut.retroapi.entity.client.MobFactory;
import com.periut.retroapi.register.item.RetroItemAccess;
import com.periut.retroapi.register.recipe.RetroRecipes;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LastEchoNPCMod implements RetroModInitializer {
    public static final String MOD_ID = "lastecho_npc";
    public static Item HUMAN_SPAWNER;
    public static Item IDENTITY_TOME;
    public static Item CRYSTAL_ROSE;

    public static final List<String> SKIN_PATHS = new ArrayList<>();
    public static final List<Personality> PERSONALITIES = new ArrayList<>();

    public static NamespacedIdentifier id(String name) {
        return NamespacedIdentifiers.from(MOD_ID, name);
    }

    @Override
    public void initRetro() {
        loadSkinPaths();
        loadPersonalities();

        // ---- Human entity ----
        RetroEntities.register(HumanEntity.ID, HumanEntity.class)
                .factory((MobFactory) HumanEntity::new);

        // ---- Spawner item ----
        HUMAN_SPAWNER = RetroItemAccess.of(new HumanSpawnItem(RetroItemAccess.AUTO_ID))
                .maxStackSize(1)
                .texture(id("human_spawner"))
                .register(id("human_spawner"));

        // ---- Identity Tome ----
        IDENTITY_TOME = RetroItemAccess.create()
                .maxStackSize(1)
                .texture(id("identity_tome"))
                .handheld()
                .register(id("identity_tome"));

        // ---- Crystal Rose ----
        CRYSTAL_ROSE = RetroItemAccess.create()
                .maxStackSize(1)
                .texture(id("crystal_rose"))
                .handheld()
                .register(id("crystal_rose"));

        // ---- Register recipes directly (safe in retroapi entrypoint) ----
        registerRecipes();
    }

    // ---------- Skin loading ----------
    private void loadSkinPaths() {
        try (InputStream in = getClass().getResourceAsStream("/data/lastecho_npc/skins.txt")) {
            if (in == null) {
                SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human0.png");
                SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human1.png");
                SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human2.png");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (!line.startsWith("/")) line = "/" + line;
                    SKIN_PATHS.add(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human0.png");
            SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human1.png");
            SKIN_PATHS.add("/assets/lastecho_npc/textures/entity/human2.png");
        }
    }

    // ---------- Personality loading ----------
    private void loadPersonalities() {
        try (InputStream in = getClass().getResourceAsStream("/data/lastecho_npc/personalities.json")) {
            if (in == null) {
                PERSONALITIES.add(new Personality("Default", List.of("Hello!", "Nice to meet you.")));
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(in)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray array = root.getAsJsonArray("personalities");
                for (var elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    JsonArray msgArray = obj.getAsJsonArray("messages");
                    List<String> messages = new ArrayList<>();
                    for (var msgElem : msgArray) {
                        messages.add(msgElem.getAsString());
                    }
                    PERSONALITIES.add(new Personality(name, messages));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (PERSONALITIES.isEmpty()) {
                PERSONALITIES.add(new Personality("Default", List.of("Hello!", "Nice to meet you.")));
            }
        }
    }

    // ---------- Recipes ----------
    private void registerRecipes() {
        // Human Spawner: apple surrounded by redstone dust
        RetroRecipes.addShaped(new ItemStack(HUMAN_SPAWNER),
                "RRR",
                "RAR",
                "RRR",
                'R', Item.REDSTONE, 'A', Item.APPLE);

        // Identity Tome: book + feather + lapis lazuli (shapeless)
        RetroRecipes.addShapeless(new ItemStack(IDENTITY_TOME),
                Item.BOOK, Item.FEATHER, new ItemStack(Item.DYE, 1, 4));

        // Crystal Rose: rose (block) + glass block + glowstone dust (shapeless)
        RetroRecipes.addShapeless(new ItemStack(CRYSTAL_ROSE),
                Block.ROSE, Block.GLASS, Item.GLOWSTONE_DUST);
    }

    // ---------- Public helpers ----------
    public static List<String> getMessagesForPersonality(int index) {
        if (PERSONALITIES.isEmpty()) {
            return List.of("Hello!");
        }
        int idx = index % PERSONALITIES.size();
        return PERSONALITIES.get(idx).messages;
    }

    public static String getPersonalityName(int index) {
        if (PERSONALITIES.isEmpty()) return "Unknown";
        int idx = index % PERSONALITIES.size();
        return PERSONALITIES.get(idx).name;
    }

    public static int getPersonalityCount() {
        return PERSONALITIES.size();
    }

    public static class Personality {
        public final String name;
        public final List<String> messages;

        public Personality(String name, List<String> messages) {
            this.name = name;
            this.messages = messages;
        }
    }
}
