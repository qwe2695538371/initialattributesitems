package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 客户端界面配置：背景图片路径、面板尺寸。
 */
public final class ClientGuiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(ExampleMod.MODID).resolve("client_gui.json");

    private static GuiConfigData cached = defaultData();
    private static ResourceLocation cachedTexture;
    private static String loadedTexturePath;
    private static boolean loaded = false;

    private ClientGuiConfig() {}

    public static GuiConfigData get() {
        if (!loaded) {
            load();
        }
        return cached;
    }

    public static ResourceLocation getBackgroundTexture() {
        if (!loaded) {
            load();
        }
        String path = cached.backgroundPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (Objects.equals(path, loadedTexturePath) && cachedTexture != null) {
            return cachedTexture;
        }
        try {
            NativeImage image = NativeImage.read(Files.newInputStream(Path.of(path)));
            DynamicTexture texture = new DynamicTexture(image);
            cachedTexture = Minecraft.getInstance().getTextureManager().register("gui_background_" + texture.hashCode(), texture);
            loadedTexturePath = path;
            return cachedTexture;
        } catch (IOException e) {
            ExampleMod.LOGGER.warn("自定义背景图片加载失败: {}", path, e);
            cachedTexture = null;
            loadedTexturePath = null;
            return null;
        }
    }

    private static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    GuiConfigData data = GSON.fromJson(reader, GuiConfigData.class);
                    if (data != null) {
                        cached = data;
                    }
                }
            } else {
                writeDefault();
            }
        } catch (IOException | JsonParseException e) {
            ExampleMod.LOGGER.warn("读取界面配置失败，使用默认值", e);
            cached = defaultData();
        }
        loaded = true;
    }

    private static void writeDefault() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(defaultData(), writer);
        } catch (IOException e) {
            ExampleMod.LOGGER.warn("写入默认界面配置失败", e);
        }
    }

    private static GuiConfigData defaultData() {
        return new GuiConfigData("", 260, 200);
    }

    public record GuiConfigData(String backgroundPath, int panelWidth, int panelHeight) {}
}
