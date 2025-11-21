package com.example.examplemod.career;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 职业定义，源自配置文件。
 */
public record CareerDefinition(
        String id,
        String name,
        String description,
        ResourceLocation iconItem,
        List<AttributeBonus> attributes,
        List<StartingItem> startingItems
) {

    public record AttributeBonus(ResourceLocation attributeId, double value) {}

    public record StartingItem(ResourceLocation itemId, int count) {}
}
