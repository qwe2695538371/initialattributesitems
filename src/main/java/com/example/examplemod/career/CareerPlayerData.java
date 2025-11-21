package com.example.examplemod.career;

import com.example.examplemod.ExampleMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 负责把玩家的职业数据存储在持久化 NBT 中。
 */
public final class CareerPlayerData {
    private static final String ROOT = ExampleMod.MODID;
    private static final String KEY_ID = "careerId";
    private static final String KEY_ITEMS_GRANTED = "careerItemsGranted";

    private CareerPlayerData() {}

    public static Optional<String> getCareerId(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(ROOT);
        String id = tag.getString(KEY_ID);
        return id.isEmpty() ? Optional.empty() : Optional.of(id);
    }

    public static void setCareerId(ServerPlayer player, String id) {
        CompoundTag tag = getOrCreateRoot(player);
        tag.putString(KEY_ID, id);
        player.getPersistentData().put(ROOT, tag);
    }

    public static boolean hasGrantedItems(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(ROOT);
        return tag.getBoolean(KEY_ITEMS_GRANTED);
    }

    public static void markGrantedItems(ServerPlayer player) {
        CompoundTag tag = getOrCreateRoot(player);
        tag.putBoolean(KEY_ITEMS_GRANTED, true);
        player.getPersistentData().put(ROOT, tag);
    }

    public static void copyPersistentData(CompoundTag from, CompoundTag to) {
        if (from.contains(ROOT)) {
            to.put(ROOT, from.getCompound(ROOT).copy());
        }
    }

    static CompoundTag getOrCreateRoot(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(ROOT)) {
            CompoundTag tag = new CompoundTag();
            data.put(ROOT, tag);
            return tag;
        }
        return data.getCompound(ROOT);
    }
}
