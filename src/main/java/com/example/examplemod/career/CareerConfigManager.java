package com.example.examplemod.career;

import com.example.examplemod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 负责读取/写入职业配置，若文件缺失会自动写入默认示例。
 */
public final class CareerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(ExampleMod.MODID).resolve("careers.json");

    private static List<CareerDefinition> cached = List.of();
    private static boolean lockAfterChoice = true;

    private CareerConfigManager() {}

    public static void load() {
        ensureDefaultFile();
        cached = readConfig();
        ExampleMod.LOGGER.info("已加载 {} 个职业定义", cached.size());
    }

    public static List<CareerDefinition> getCareers() {
        return cached;
    }

    public static Optional<CareerDefinition> findCareer(String id) {
        return cached.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public static boolean isLockAfterChoice() {
        return lockAfterChoice;
    }

    private static void ensureDefaultFile() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (!Files.exists(CONFIG_PATH)) {
                RawConfig defaults = buildDefaults();
                try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                    GSON.toJson(defaults, writer);
                }
                ExampleMod.LOGGER.info("未发现职业配置，已生成默认文件 {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            ExampleMod.LOGGER.error("写入默认职业配置失败", e);
        }
    }

    private static List<CareerDefinition> readConfig() {
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            RawConfig raw = GSON.fromJson(reader, RawConfig.class);
            return validate(raw);
        } catch (IOException | JsonParseException e) {
            ExampleMod.LOGGER.error("读取职业配置失败，将使用内置默认值", e);
            return validate(buildDefaults());
        }
    }

    private static List<CareerDefinition> validate(RawConfig raw) {
        if (raw == null || raw.careers == null) {
            return Collections.emptyList();
        }
        lockAfterChoice = raw.lockAfterChoice == null ? true : raw.lockAfterChoice;
        List<CareerDefinition> list = new ArrayList<>();
        for (RawCareer c : raw.careers) {
            if (c == null) continue;
            String id = Objects.toString(c.id, "").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            String name = Objects.toString(c.name, id);
            String desc = Objects.toString(c.description, "");
            ResourceLocation icon = Optional.ofNullable(ResourceLocation.tryParse(c.icon))
                    .orElseGet(() -> Objects.requireNonNull(ResourceLocation.tryParse("minecraft:book")));

            List<CareerDefinition.AttributeBonus> attrs = new ArrayList<>();
            if (c.attributes != null) {
                for (RawAttribute attr : c.attributes) {
                    if (attr == null) continue;
                    ResourceLocation attrId = ResourceLocation.tryParse(attr.id);
                    if (attrId == null) {
                        ExampleMod.LOGGER.warn("忽略无效属性 ID: {}", attr.id);
                        continue;
                    }
                    attrs.add(new CareerDefinition.AttributeBonus(attrId, attr.value));
                }
            }

            List<CareerDefinition.StartingItem> items = new ArrayList<>();
            if (c.items != null) {
                for (RawItem item : c.items) {
                    if (item == null) continue;
                    ResourceLocation itemId = ResourceLocation.tryParse(item.id);
                    if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                        ExampleMod.LOGGER.warn("忽略无效初始物品: {}", item.id);
                        continue;
                    }
                    int count = Math.max(1, item.count);
                    items.add(new CareerDefinition.StartingItem(itemId, count));
                }
            }

            list.add(new CareerDefinition(id, name, desc, icon, attrs, items));
        }
        return list;
    }

    private static RawConfig buildDefaults() {
        RawCareer warrior = new RawCareer();
        warrior.id = "warrior";
        warrior.name = "战士";
        warrior.description = "重甲近战，靠近敌人并承伤。";
        warrior.icon = "minecraft:iron_sword";
        warrior.attributes = List.of(
                new RawAttribute("minecraft:generic.armor", 1.0),
                new RawAttribute("minecraft:generic.attack_damage", 1.0)
        );
        warrior.items = List.of(
                new RawItem("minecraft:iron_sword", 1),
                new RawItem("minecraft:bread", 8)
        );

        RawCareer mage = new RawCareer();
        mage.id = "mage";
        mage.name = "法师";
        mage.description = "提升生命与幸运，偏向技能输出。";
        mage.icon = "minecraft:blaze_powder";
        mage.attributes = List.of(
                new RawAttribute("minecraft:generic.max_health", 4.0),
                new RawAttribute("minecraft:generic.luck", 1.0)
        );
        mage.items = List.of(
                new RawItem("minecraft:book", 3),
                new RawItem("minecraft:stick", 1)
        );

        RawCareer scout = new RawCareer();
        scout.id = "scout";
        scout.name = "斥候";
        scout.description = "移动更快，拥有额外的交互距离。";
        scout.icon = "minecraft:feather";
        scout.attributes = List.of(
                new RawAttribute("minecraft:generic.movement_speed", 0.05),
                new RawAttribute("forge:entity_reach", 0.5)
        );
        scout.items = List.of(
                new RawItem("minecraft:bow", 1),
                new RawItem("minecraft:arrow", 24)
        );

        RawConfig config = new RawConfig();
        config.lockAfterChoice = true;
        config.careers = List.of(warrior, mage, scout);
        return config;
    }

    private static class RawConfig {
        @SerializedName("careers")
        List<RawCareer> careers = new ArrayList<>();
        @SerializedName("lockAfterChoice")
        Boolean lockAfterChoice = true;
    }

    private static class RawCareer {
        String id;
        String name;
        String description;
        String icon;
        List<RawAttribute> attributes;
        List<RawItem> items;
    }

    private static class RawAttribute {
        String id;
        double value;

        RawAttribute(String id, double value) {
            this.id = id;
            this.value = value;
        }
    }

    private static class RawItem {
        String id;
        int count;

        RawItem(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }
}
