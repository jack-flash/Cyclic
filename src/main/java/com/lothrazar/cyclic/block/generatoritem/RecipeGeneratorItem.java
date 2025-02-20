package com.lothrazar.cyclic.block.generatoritem;

import com.google.gson.JsonObject;
import com.lothrazar.cyclic.ModCyclic;
import com.lothrazar.cyclic.recipe.CyclicRecipe;
import com.lothrazar.cyclic.recipe.CyclicRecipeType;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistryEntry;

@SuppressWarnings("rawtypes")
public class RecipeGeneratorItem<TileEntityBase> extends CyclicRecipe {

  private NonNullList<Ingredient> ingredients = NonNullList.create();
  private int ticks;
  private int rfpertick;

  public RecipeGeneratorItem(ResourceLocation id, Ingredient in, int ticks, int rfpertick) {
    super(id);
    ingredients.add(in);
    this.setTicks(Math.max(1, ticks));
    this.rfpertick = Math.max(1, rfpertick);
  }

  @Override
  public boolean matches(com.lothrazar.cyclic.block.TileBlockEntityCyclic inv, Level worldIn) {
    try {
      TileGeneratorDrops tile = (TileGeneratorDrops) inv;
      return matches(tile.inputSlots.getStackInSlot(0), ingredients.get(0));
    }
    catch (ClassCastException e) {
      return false;
    }
  }

  public boolean matches(ItemStack current, Ingredient ing) {
    if (ing == Ingredient.EMPTY) {
      //it must be empty
      return current.isEmpty();
    }
    if (current.isEmpty()) {
      return ing == Ingredient.EMPTY;
    }
    return ing.test(current);
  }

  public ItemStack[] ingredientAt(int slot) {
    Ingredient ing = ingredients.get(slot);
    return ing.getItems();
  }

  @Override
  public NonNullList<Ingredient> getIngredients() {
    return ingredients;
  }

  @Override
  public ItemStack getResultItem() {
    return ItemStack.EMPTY;
  }

  @Override
  public RecipeType<?> getType() {
    return CyclicRecipeType.GENERATOR_ITEM;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return SERIALGENERATOR;
  }

  public int getTicks() {
    return ticks;
  }

  public void setTicks(int ticks) {
    this.ticks = ticks;
  }

  public int getRfpertick() {
    return rfpertick;
  }

  public void setRfpertick(int rfpertick) {
    this.rfpertick = rfpertick;
  }

  public int getRfTotal() {
    return this.getRfpertick() * this.getTicks();
  }

  public static final SerializeGenerateItem SERIALGENERATOR = new SerializeGenerateItem();

  public static class SerializeGenerateItem extends ForgeRegistryEntry<RecipeSerializer<?>> implements RecipeSerializer<RecipeGeneratorItem<? extends com.lothrazar.cyclic.block.TileBlockEntityCyclic>> {

    SerializeGenerateItem() {
      // This registry name is what people will specify in their json files.
      this.setRegistryName(new ResourceLocation(ModCyclic.MODID, "generator_item"));
    }

    /**
     * The fluid stuff i was helped out a ton by looking at this https://github.com/mekanism/Mekanism/blob/921d10be54f97518c1f0cb5a6fc64bf47d5e6773/src/api/java/mekanism/api/SerializerHelper.java#L129
     */
    @SuppressWarnings("unchecked")
    @Override
    public RecipeGeneratorItem<? extends com.lothrazar.cyclic.block.TileBlockEntityCyclic> fromJson(ResourceLocation recipeId, JsonObject json) {
      RecipeGeneratorItem r = null;
      try {
        Ingredient inputFirst = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "fuel"));
        JsonObject result = json.get("energy").getAsJsonObject();
        int ticks = result.get("ticks").getAsInt();
        int rfpertick = result.get("rfpertick").getAsInt();
        //        String fluidId = JSONUtils.getString(result, "fluid");
        //        ResourceLocation resourceLocation = new ResourceLocation(fluidId);
        //        Fluid fluid = ForgeRegistries.FLUIDS.getValue(resourceLocation);
        r = new RecipeGeneratorItem(recipeId, inputFirst, ticks, rfpertick);
      }
      catch (Exception e) {
        ModCyclic.LOGGER.error("Error loading recipe " + recipeId, e);
      }
      return r;
    }

    @Override
    public RecipeGeneratorItem fromNetwork(ResourceLocation recipeId, FriendlyByteBuf buffer) {
      RecipeGeneratorItem r = new RecipeGeneratorItem(recipeId, Ingredient.fromNetwork(buffer), buffer.readInt(), buffer.readInt());
      //server reading recipe from client or vice/versa 
      return r;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, RecipeGeneratorItem recipe) {
      Ingredient zero = (Ingredient) recipe.ingredients.get(0);
      zero.toNetwork(buffer);
      buffer.writeInt(recipe.getTicks());
      buffer.writeInt(recipe.rfpertick);
    }
  }
}
