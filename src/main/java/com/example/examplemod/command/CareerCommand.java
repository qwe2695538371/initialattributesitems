package com.example.examplemod.command;

import com.example.examplemod.career.CareerConfigManager;
import com.example.examplemod.career.CareerPlayerData;
import com.example.examplemod.network.CareerNetwork;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CareerCommand {
    private CareerCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("career")
                .requires(cs -> cs.hasPermission(0))
                .executes(CareerCommand::openCareerScreen);
    }

    private static int openCareerScreen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (CareerConfigManager.isLockAfterChoice() && CareerPlayerData.getCareerId(player).isPresent()) {
            player.sendSystemMessage(Component.literal("已选择职业，无法再次打开职业面板"));
            return 0;
        }
        CareerNetwork.sendCareerData(player);
        return 1;
    }
}
