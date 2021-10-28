package com.lothrazar.cyclic.block.anvilvoid;

import com.lothrazar.cyclic.base.ScreenBase;
import com.lothrazar.cyclic.gui.ButtonMachineField;
import com.lothrazar.cyclic.gui.FluidBar;
import com.lothrazar.cyclic.registry.TextureRegistry;
import com.lothrazar.cyclic.util.UtilChat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ScreenAnvilVoid extends ScreenBase<ContainerAnvilVoid> {

  private ButtonMachineField btnRedstone;
  private FluidBar fluid;

  public ScreenAnvilVoid(ContainerAnvilVoid screenContainer, Inventory inv, Component titleIn) {
    super(screenContainer, inv, titleIn);
    fluid = new FluidBar(this, 152, 8, TileAnvilVoid.CAPACITY);
    fluid.emtpyTooltip = "0 " + UtilChat.lang("fluid.cyclic.xpjuice");
  }

  @Override
  public void init() {
    super.init();
    fluid.guiLeft = leftPos;
    fluid.guiTop = topPos;
    int x, y;
    x = leftPos + 6;
    y = topPos + 6;
    btnRedstone = addRenderableWidget(new ButtonMachineField(x, y, TileAnvilVoid.Fields.REDSTONE.ordinal(), menu.tile.getBlockPos()));
  }

  @Override
  public void render(PoseStack ms, int mouseX, int mouseY, float partialTicks) {
    this.renderBackground(ms);
    super.render(ms, mouseX, mouseY, partialTicks);
    this.renderTooltip(ms, mouseX, mouseY);
    btnRedstone.onValueUpdate(menu.tile);
    fluid.renderHoveredToolTip(ms, mouseX, mouseY, menu.tile.getFluid());
  }

  @Override
  protected void renderLabels(PoseStack ms, int mouseX, int mouseY) {
    this.drawButtonTooltips(ms, mouseX, mouseY);
    this.drawName(ms, this.title.getString());
  }

  @Override
  protected void renderBg(PoseStack ms, float partialTicks, int mouseX, int mouseY) {
    this.drawBackground(ms, TextureRegistry.INVENTORY);
    this.drawSlot(ms, 54, 34);
    this.drawSlotLarge(ms, 104, 30);
    fluid.draw(ms, menu.tile.getFluid());
  }
}
