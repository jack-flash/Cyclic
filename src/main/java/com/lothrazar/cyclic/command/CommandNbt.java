package com.lothrazar.cyclic.command;

import com.lothrazar.cyclic.util.UtilChat;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class CommandNbt {

  public static int executePrintNbt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer player = ctx.getSource().getPlayerOrException();
    ItemStack held = player.getMainHandItem();
    if (held.hasTag()) {
      UtilChat.sendFeedback(ctx, held.getTag().toString());
    }
    else {
      UtilChat.sendFeedback(ctx, "command.cyclic.nbtprint.null");
    }
    return 0;
  }

  public static int executePrintTags(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    ServerPlayer player = ctx.getSource().getPlayerOrException();
    ItemStack held = player.getMainHandItem();
    for (ResourceLocation tag : held.getItem().getTags()) {
      UtilChat.sendFeedback(ctx, tag.toString());
    }
    return 0;
  }
}
