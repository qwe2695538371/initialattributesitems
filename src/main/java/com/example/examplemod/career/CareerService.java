package com.example.examplemod.career;

import com.example.examplemod.ExampleMod;
import com.example.playerattributemanagement.api.PlayerAttributeApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理职业分配、属性应用及初始物品发放。
 */
public final class CareerService {
    private static final String KEY_ATTR_LIST = "careerAppliedAttributes";
    private static boolean warnedMissingApi = false;

    private CareerService() {}

    public static void applyExistingCareer(ServerPlayer player) {
        Optional<String> careerId = CareerPlayerData.getCareerId(player);
        if (careerId.isEmpty()) {
            return;
        }
        CareerConfigManager.findCareer(careerId.get()).ifPresent(def -> applyAttributes(player, def));
    }

    public static boolean chooseCareer(ServerPlayer player, String id) {
        var current = CareerPlayerData.getCareerId(player);
        if (current.isPresent()) {
            if (CareerConfigManager.isLockAfterChoice()) {
                return false; // 不允许切换
            }
            if (current.get().equals(id)) {
                return false; // 已经是该职业
            }
        }
        Optional<CareerDefinition> def = CareerConfigManager.findCareer(id);
        if (def.isEmpty()) {
            return false;
        }

        CareerPlayerData.setCareerId(player, id);
        applyAttributes(player, def.get());
        giveStartingItems(player, def.get());
        return true;
    }

    private static void applyAttributes(ServerPlayer player, CareerDefinition def) {
        if (!ModList.get().isLoaded("playerattributemanagement")) {
            if (!warnedMissingApi) {
                ExampleMod.LOGGER.error("未找到 playerattributemanagement 模组，职业属性加成无法生效");
                warnedMissingApi = true;
            }
            return;
        }

        Set<ResourceLocation> newIds = def.attributes().stream()
                .map(CareerDefinition.AttributeBonus::attributeId)
                .collect(Collectors.toSet());

        Set<ResourceLocation> previous = readAppliedAttributes(player);

        for (CareerDefinition.AttributeBonus bonus : def.attributes()) {
            try {
                PlayerAttributeApi.setExtra(player, bonus.attributeId(), bonus.value());
            } catch (IllegalArgumentException ex) {
                ExampleMod.LOGGER.warn("应用属性 {} 失败: {}", bonus.attributeId(), ex.getMessage());
            }
        }

        // 清理不再存在的旧属性
        for (ResourceLocation oldId : previous) {
            if (!newIds.contains(oldId)) {
                try {
                    PlayerAttributeApi.resetExtra(player, oldId);
                } catch (IllegalArgumentException ignored) {
                    // 忽略不受管的属性
                }
            }
        }

        storeAppliedAttributes(player, newIds);
    }

    private static void giveStartingItems(ServerPlayer player, CareerDefinition def) {
        if (CareerPlayerData.hasGrantedItems(player)) {
            return;
        }
        for (CareerDefinition.StartingItem item : def.startingItems()) {
            Item mcItem = ForgeRegistries.ITEMS.getValue(item.itemId());
            if (mcItem == null) {
                ExampleMod.LOGGER.warn("初始物品不存在: {}", item.itemId());
                continue;
            }
            ItemStack stack = new ItemStack(mcItem, item.count());
            boolean added = player.getInventory().add(stack);
            if (!added) {
                player.drop(stack, false);
            }
        }
        CareerPlayerData.markGrantedItems(player);
    }

    private static Set<ResourceLocation> readAppliedAttributes(ServerPlayer player) {
        var tag = CareerPlayerData.getOrCreateRoot(player);
        Set<ResourceLocation> result = new HashSet<>();
        if (tag.contains(KEY_ATTR_LIST)) {
            var listTag = tag.getList(KEY_ATTR_LIST, 8); // 8 = string
            for (int i = 0; i < listTag.size(); i++) {
                String raw = listTag.getString(i);
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private static void storeAppliedAttributes(ServerPlayer player, Set<ResourceLocation> ids) {
        var listTag = new net.minecraft.nbt.ListTag();
        for (ResourceLocation id : ids) {
            listTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        }
        var tag = CareerPlayerData.getOrCreateRoot(player);
        tag.put(KEY_ATTR_LIST, listTag);
        player.getPersistentData().put(ExampleMod.MODID, tag);
    }
}
