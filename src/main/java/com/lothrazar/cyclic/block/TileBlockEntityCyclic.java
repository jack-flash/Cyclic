package com.lothrazar.cyclic.block;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.lothrazar.cyclic.ModCyclic;
import com.lothrazar.cyclic.block.breaker.BlockBreaker;
import com.lothrazar.cyclic.block.cable.energy.TileCableEnergy;
import com.lothrazar.cyclic.capabilities.CustomEnergyStorage;
import com.lothrazar.cyclic.item.datacard.filter.FilterCardItem;
import com.lothrazar.cyclic.net.PacketEnergySync;
import com.lothrazar.cyclic.registry.PacketRegistry;
import com.lothrazar.cyclic.util.UtilEntity;
import com.lothrazar.cyclic.util.UtilFakePlayer;
import com.lothrazar.cyclic.util.UtilFluid;
import com.lothrazar.cyclic.util.UtilItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public abstract class TileBlockEntityCyclic extends BlockEntity implements Container {

  public static final String NBTINV = "inv";
  public static final String NBTFLUID = "fluid";
  public static final String NBTENERGY = "energy";
  public static final int MENERGY = 64 * 1000;
  protected int flowing = 1;
  protected int needsRedstone = 1;
  protected int render = 0; // default to do not render
  protected int timer = 0;

  public TileBlockEntityCyclic(BlockEntityType<?> tileEntityTypeIn, BlockPos pos, BlockState state) {
    super(tileEntityTypeIn, pos, state);
  }

  public int getTimer() {
    return timer;
  }

  protected Player getLookingPlayer(int maxRange, boolean mustCrouch) {
    List<Player> players = level.getEntitiesOfClass(Player.class, new AABB(
        this.worldPosition.getX() - maxRange, this.worldPosition.getY() - maxRange, this.worldPosition.getZ() - maxRange, this.worldPosition.getX() + maxRange, this.worldPosition.getY() + maxRange, this.worldPosition.getZ() + maxRange));
    for (Player player : players) {
      if (mustCrouch && !player.isCrouching()) {
        continue; //check the next one
      }
      //am i looking
      Vec3 positionEyes = player.getEyePosition(1F);
      Vec3 look = player.getViewVector(1F);
      //take the player eye position. draw a vector from the eyes, in the direction they are looking
      //of LENGTH equal to the range
      Vec3 visionWithLength = positionEyes.add(look.x * maxRange, look.y * maxRange, look.z * maxRange);
      //ray trayce from eyes, along the vision vec
      BlockHitResult rayTrace = this.level.clip(new ClipContext(positionEyes, visionWithLength, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
      if (this.worldPosition.equals(rayTrace.getBlockPos())) {
        //at least one is enough, stop looping
        return player;
      }
    }
    return null;
  }

  public void tryDumpFakePlayerInvo(WeakReference<FakePlayer> fp, boolean includeMainHand) {
    int start = (includeMainHand) ? 0 : 1;
    ArrayList<ItemStack> toDrop = new ArrayList<ItemStack>();
    for (int i = start; i < fp.get().getInventory().items.size(); i++) {
      ItemStack s = fp.get().getInventory().items.get(i);
      if (s.isEmpty() == false) {
        toDrop.add(s.copy());
        fp.get().getInventory().items.set(i, ItemStack.EMPTY);
      }
    }
    UtilItemStack.drop(this.level, this.worldPosition.above(), toDrop);
  }

  public static void tryEquipItem(ItemStack item, WeakReference<FakePlayer> fp, InteractionHand hand) {
    if (fp == null) {
      return;
    }
    fp.get().setItemInHand(hand, item);
  }

  public static void syncEquippedItem(LazyOptional<IItemHandler> i, WeakReference<FakePlayer> fp, int slot, InteractionHand hand) {
    if (fp == null) {
      return;
    }
    i.ifPresent(inv -> {
      inv.extractItem(slot, 64, false); //delete and overwrite
      inv.insertItem(slot, fp.get().getItemInHand(hand), false);
    });
  }

  public static void tryEquipItem(LazyOptional<IItemHandler> i, WeakReference<FakePlayer> fp, int slot, InteractionHand hand) {
    if (fp == null) {
      return;
    }
    i.ifPresent(inv -> {
      ItemStack maybeTool = inv.getStackInSlot(0);
      if (!maybeTool.isEmpty()) {
        if (maybeTool.getCount() <= 0) {
          maybeTool = ItemStack.EMPTY;
        }
      }
      if (!maybeTool.equals(fp.get().getItemInHand(hand))) {
        fp.get().setItemInHand(hand, maybeTool);
      }
    });
  }

  public static InteractionResult rightClickBlock(WeakReference<FakePlayer> fakePlayer,
      Level world, BlockPos targetPos, InteractionHand hand, Direction facing) throws Exception {
    if (fakePlayer == null) {
      return InteractionResult.FAIL;
    }
    Direction placementOn = (facing == null) ? fakePlayer.get().getMotionDirection() : facing;
    BlockHitResult blockraytraceresult = new BlockHitResult(
        fakePlayer.get().getLookAngle(), placementOn,
        targetPos, true);
    //processRightClick
    InteractionResult result = fakePlayer.get().gameMode.useItemOn(fakePlayer.get(), world,
        fakePlayer.get().getItemInHand(hand), hand, blockraytraceresult);
    //it becomes CONSUME result 1 bucket. then later i guess it doesnt save, and then its water_bucket again
    return result;
  }

  public static boolean tryHarvestBlock(WeakReference<FakePlayer> fakePlayer, Level world, BlockPos targetPos) {
    if (fakePlayer == null) {
      return false;
    }
    return fakePlayer.get().gameMode.destroyBlock(targetPos);
  }

  public WeakReference<FakePlayer> setupBeforeTrigger(ServerLevel sw, String name, UUID uuid) {
    WeakReference<FakePlayer> fakePlayer = UtilFakePlayer.initFakePlayer(sw, uuid, name);
    if (fakePlayer == null) {
      ModCyclic.LOGGER.error("Fake player failed to init " + name + " " + uuid);
      return null;
    }
    //fake player facing the same direction as tile. for throwables
    fakePlayer.get().setPos(this.getBlockPos().getX(), this.getBlockPos().getY(), this.getBlockPos().getZ()); //seems to help interact() mob drops like milk
    fakePlayer.get().setYRot(UtilEntity.getYawFromFacing(this.getCurrentFacing()));
    return fakePlayer;
  }

  public WeakReference<FakePlayer> setupBeforeTrigger(ServerLevel sw, String name) {
    return setupBeforeTrigger(sw, name, UUID.randomUUID());
  }

  public void setLitProperty(boolean lit) {
    BlockState st = this.getBlockState();
    if (!st.hasProperty(BlockCyclic.LIT)) {
      return;
    }
    boolean previous = st.getValue(BlockBreaker.LIT);
    if (previous != lit) {
      this.level.setBlockAndUpdate(worldPosition, st.setValue(BlockBreaker.LIT, lit));
    }
  }

  public Direction getCurrentFacing() {
    if (this.getBlockState().hasProperty(BlockStateProperties.FACING)) {
      return this.getBlockState().getValue(BlockStateProperties.FACING);
    }
    if (this.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
      return this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
    return null;
  }

  protected BlockPos getCurrentFacingPos(int distance) {
    Direction f = this.getCurrentFacing();
    if (f != null) {
      return this.worldPosition.relative(f, distance);
    }
    return this.worldPosition;
  }

  protected BlockPos getCurrentFacingPos() {
    return getCurrentFacingPos(1);
  }

  @Override
  public CompoundTag getUpdateTag() {
    CompoundTag syncData = super.getUpdateTag();
    this.saveAdditional(syncData);
    return syncData;
  }

  @Override
  public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
    this.load(pkt.getTag());
    super.onDataPacket(net, pkt);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }

  public boolean isPowered() {
    return this.getLevel().hasNeighborSignal(this.getBlockPos());
  }

  public int getRedstonePower() {
    return this.getLevel().getBestNeighborSignal(this.getBlockPos());
  }

  public boolean requiresRedstone() {
    return this.needsRedstone == 1;
  }

  public void moveFluids(Direction myFacingDir, BlockPos posTarget, int toFlow, IFluidHandler tank) {
    // posTarget = pos.offset(myFacingDir);
    if (tank == null || tank.getFluidInTank(0).getAmount() <= 0) {
      return;
    }
    Direction themFacingMe = myFacingDir.getOpposite();
    UtilFluid.tryFillPositionFromTank(level, posTarget, themFacingMe, tank, toFlow);
  }

  public void tryExtract(IItemHandler myself, Direction extractSide, int qty, ItemStackHandler nullableFilter) {
    if (extractSide == null) {
      return;
    }
    if (extractSide == null || !myself.getStackInSlot(0).isEmpty()) {
      return;
    }
    BlockPos posTarget = worldPosition.relative(extractSide);
    BlockEntity tile = level.getBlockEntity(posTarget);
    if (tile != null) {
      IItemHandler itemHandlerFrom = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, extractSide.getOpposite()).orElse(null);
      //
      ItemStack itemTarget;
      if (itemHandlerFrom != null) {
        //ok go
        for (int i = 0; i < itemHandlerFrom.getSlots(); i++) {
          itemTarget = itemHandlerFrom.extractItem(i, qty, true);
          if (itemTarget.isEmpty()) {
            continue;
          }
          // and then pull 
          if (nullableFilter != null &&
              !FilterCardItem.filterAllowsExtract(nullableFilter.getStackInSlot(0), itemTarget)) {
            continue;
          }
          itemTarget = itemHandlerFrom.extractItem(i, qty, false);
          ItemStack result = myself.insertItem(0, itemTarget.copy(), false);
          itemTarget.setCount(result.getCount());
          return;
        }
      }
    }
  }

  public boolean moveItems(Direction myFacingDir, int max, IItemHandler handlerHere) {
    return moveItems(myFacingDir, worldPosition.relative(myFacingDir), max, handlerHere, 0);
  }

  public boolean moveItems(Direction myFacingDir, BlockPos posTarget, int max, IItemHandler handlerHere, int theslot) {
    if (this.level.isClientSide()) {
      return false;
    }
    if (handlerHere == null) {
      return false;
    }
    Direction themFacingMe = myFacingDir.getOpposite();
    BlockEntity tileTarget = level.getBlockEntity(posTarget);
    if (tileTarget == null) {
      return false;
    }
    IItemHandler handlerOutput = tileTarget.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, themFacingMe).orElse(null);
    if (handlerOutput == null) {
      return false;
    }
    if (handlerHere != null && handlerOutput != null) {
      //first simulate 
      ItemStack drain = handlerHere.extractItem(theslot, max, true); // handlerHere.getStackInSlot(theslot).copy();
      int sizeStarted = drain.getCount();
      if (!drain.isEmpty()) {
        //now push it into output, but find out what was ACTUALLY taken
        for (int slot = 0; slot < handlerOutput.getSlots(); slot++) {
          drain = handlerOutput.insertItem(slot, drain, false);
          if (drain.isEmpty()) {
            break; //done draining
          }
        }
      }
      int sizeAfter = sizeStarted - drain.getCount();
      if (sizeAfter > 0) {
        handlerHere.extractItem(theslot, sizeAfter, false);
      }
      return sizeAfter > 0;
    }
    return false;
  }

  protected boolean moveEnergy(Direction myFacingDir, int quantity) {
    return moveEnergy(myFacingDir, worldPosition.relative(myFacingDir), quantity);
  }

  protected boolean moveEnergy(Direction myFacingDir, BlockPos posTarget, int quantity) {
    if (this.level.isClientSide) {
      return false; //important to not desync cables
    }
    IEnergyStorage handlerHere = this.getCapability(CapabilityEnergy.ENERGY, myFacingDir).orElse(null);
    if (handlerHere == null || handlerHere.getEnergyStored() == 0) {
      return false;
    }
    Direction themFacingMe = myFacingDir.getOpposite();
    BlockEntity tileTarget = level.getBlockEntity(posTarget);
    if (tileTarget == null) {
      return false;
    }
    IEnergyStorage handlerOutput = tileTarget.getCapability(CapabilityEnergy.ENERGY, themFacingMe).orElse(null);
    if (handlerOutput == null) {
      return false;
    }
    if (handlerHere != null && handlerOutput != null
        && handlerHere.canExtract() && handlerOutput.canReceive()) {
      //first simulate
      int drain = handlerHere.extractEnergy(quantity, true);
      if (drain > 0) {
        //now push it into output, but find out what was ACTUALLY taken
        int filled = handlerOutput.receiveEnergy(drain, false);
        //now actually drain that much from here
        handlerHere.extractEnergy(filled, false);
        if (filled > 0 && tileTarget instanceof TileCableEnergy) {
          // not so compatible with other fluid systems. itl do i guess
          TileCableEnergy cable = (TileCableEnergy) tileTarget;
          cable.updateIncomingEnergyFace(themFacingMe);
        }
        return filled > 0;
      }
    }
    return false;
  }

  @Override
  public void load(CompoundTag tag) {
    flowing = tag.getInt("flowing");
    needsRedstone = tag.getInt("needsRedstone");
    render = tag.getInt("renderParticles");
    timer = tag.getInt("timer");
    super.load(tag);
  }

  @Override
  public void saveAdditional(CompoundTag tag) {
    tag.putInt("flowing", flowing);
    tag.putInt("needsRedstone", needsRedstone);
    tag.putInt("renderParticles", render);
    tag.putInt("timer", timer);
    super.saveAdditional(tag);
  }

  public abstract void setField(int field, int value);

  public abstract int getField(int field);

  public void setNeedsRedstone(int value) {
    this.needsRedstone = value % 2;
  }

  public FluidStack getFluid() {
    return FluidStack.EMPTY;
  }

  public void setFluid(FluidStack fluid) {}

  /************************** IInventory needed for IRecipe **********************************/
  @Deprecated
  @Override
  public int getContainerSize() {
    return 0;
  }

  @Deprecated
  @Override
  public boolean isEmpty() {
    return true;
  }

  @Deprecated
  @Override
  public ItemStack getItem(int index) {
    return ItemStack.EMPTY;
  }

  @Deprecated
  @Override
  public ItemStack removeItem(int index, int count) {
    return ItemStack.EMPTY;
  }

  @Deprecated
  @Override
  public ItemStack removeItemNoUpdate(int index) {
    return ItemStack.EMPTY;
  }

  @Deprecated
  @Override
  public void setItem(int index, ItemStack stack) {}

  @Deprecated
  @Override
  public boolean stillValid(Player player) {
    return false;
  }

  @Deprecated
  @Override
  public void clearContent() {}

  public void setFieldString(int field, String value) {
    //for string field  
  }

  public String getFieldString(int field) {
    //for string field  
    return null;
  }

  public int getEnergy() {
    return this.getCapability(CapabilityEnergy.ENERGY).map(IEnergyStorage::getEnergyStored).orElse(0);
  }

  public void setEnergy(int value) {
    IEnergyStorage energ = this.getCapability(CapabilityEnergy.ENERGY).orElse(null);
    if (energ != null && energ instanceof CustomEnergyStorage) {
      ((CustomEnergyStorage) energ).setEnergy(value);
    }
  }

  //fluid tanks have 'onchanged', energy caps do not
  protected void syncEnergy() {
    if (level.isClientSide == false && level.getGameTime() % 20 == 0) { //if serverside then 
      IEnergyStorage energ = this.getCapability(CapabilityEnergy.ENERGY).orElse(null);
      if (energ != null) {
        PacketRegistry.sendToAllClients(this.getLevel(), new PacketEnergySync(this.getBlockPos(), energ.getEnergyStored()));
      }
    }
  }

  public void exportEnergyAllSides() {
    List<Integer> rawList = IntStream.rangeClosed(0, 5).boxed().collect(Collectors.toList());
    Collections.shuffle(rawList);
    for (Integer i : rawList) {
      Direction exportToSide = Direction.values()[i];
      moveEnergy(exportToSide, MENERGY / 2);
    }
  }
}
