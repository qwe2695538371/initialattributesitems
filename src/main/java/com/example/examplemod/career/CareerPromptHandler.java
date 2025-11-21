package com.example.examplemod.career;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.command.CareerCommand;
import com.example.examplemod.network.CareerNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 负责注册命令与未选职业玩家的定时提示。
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CareerPromptHandler {
    private static final long TICKS_INTERVAL = 1200L; // 60s
    private static final Map<UUID, Long> lastPromptTick = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(CareerCommand.register());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (CareerPlayerData.getCareerId(player).isPresent()) {
            lastPromptTick.remove(player.getUUID());
            return;
        }
        long gameTime = player.level().getGameTime();
        long last = lastPromptTick.getOrDefault(player.getUUID(), 0L);
        if (gameTime - last >= TICKS_INTERVAL) {
            sendPrompt(player);
            lastPromptTick.put(player.getUUID(), gameTime);
        }
    }

    private static void sendPrompt(ServerPlayer player) {
        Style link = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/career"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击打开职业选择")))
                .withUnderlined(true);
        Component msg = Component.literal("你尚未选择职业，点击 ")
                .append(Component.literal("[职业选择]").setStyle(link))
                .append(Component.literal(" 打开面板。"));
        player.sendSystemMessage(msg);
    }
}
