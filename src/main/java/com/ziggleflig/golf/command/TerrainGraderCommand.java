package com.ziggleflig.golf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ziggleflig.golf.item.TerrainGraderItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Chat confirmation for a held tool; ordinary terrain protection still applies. */
public final class TerrainGraderCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golf").then(Commands.literal("grade")
            .requires(source -> source.getEntity() instanceof ServerPlayer player && !player.isSpectator())
            .then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                .executes(context -> TerrainGraderItem.confirmGrade(context.getSource().getPlayerOrException(),
                    StringArgumentType.getString(context, "token")) == InteractionResult.CONSUME ? 1 : 0)))));
    }
    private TerrainGraderCommand() { }
}
