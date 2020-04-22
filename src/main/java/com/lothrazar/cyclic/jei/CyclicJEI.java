package com.lothrazar.cyclic.jei;

import com.lothrazar.cyclic.ModCyclic;
import com.lothrazar.cyclic.block.melter.ContainerMelter;
import com.lothrazar.cyclic.block.melter.ScreenMelter;
import com.lothrazar.cyclic.recipe.RecipeMelter;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.util.ResourceLocation;

@JeiPlugin
public class CyclicJEI implements IModPlugin {

  private ResourceLocation id = new ResourceLocation(ModCyclic.MODID, "jei");

  @Override
  public ResourceLocation getPluginUid() {
    return id;
  }

  @Override
  public void registerCategories(IRecipeCategoryRegistration registry) {
    IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();
    registry.addRecipeCategories(new MelterRecipeCategory(guiHelper));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registry) {
    registry.addRecipes(RecipeMelter.RECIPES, MelterRecipeCategory.id);
  }

  @Override
  public void registerGuiHandlers(IGuiHandlerRegistration registry) {
    registry.addRecipeClickArea(ScreenMelter.class, 75, 0, 40, 26, MelterRecipeCategory.id);
  }

  @Override
  public void registerRecipeTransferHandlers(IRecipeTransferRegistration registry) {
    registry.addRecipeTransferHandler(ContainerMelter.class, MelterRecipeCategory.id,
        0, 1, 1, 4 * 9);
  }
}
