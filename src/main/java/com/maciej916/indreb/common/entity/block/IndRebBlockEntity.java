package com.maciej916.indreb.common.entity.block;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.maciej916.indreb.common.energy.impl.BasicEnergyStorage;
import com.maciej916.indreb.common.energy.interfaces.IEnergy;
import com.maciej916.indreb.common.entity.slot.IndRebSlot;
import com.maciej916.indreb.common.entity.slot.SlotItemHandlerDisabled;
import com.maciej916.indreb.common.entity.slot.SlotItemHandlerOutput;
import com.maciej916.indreb.common.enums.EnumEnergyType;
import com.maciej916.indreb.common.enums.InventorySlotType;
import com.maciej916.indreb.common.interfaces.entity.ICooldown;
import com.maciej916.indreb.common.interfaces.entity.IExpCollector;
import com.maciej916.indreb.common.interfaces.entity.IHasSlot;
import com.maciej916.indreb.common.interfaces.entity.ITileSound;
import com.maciej916.indreb.common.interfaces.block.IStateActive;
import com.maciej916.indreb.common.interfaces.entity.IElectricSlot;
import com.maciej916.indreb.common.network.ModNetworking;
import com.maciej916.indreb.common.network.packet.PacketExperience;
import com.maciej916.indreb.common.registries.ModCapabilities;
import com.maciej916.indreb.common.util.SoundHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.maciej916.indreb.common.registries.ModTags.*;

public class IndRebBlockEntity extends BlockEntity implements IHasSlot {

    private ItemStackHandler stackHandler;
    private ArrayList<IndRebSlot> slots = new ArrayList<>();
    private final ArrayList<SlotItemHandler> itemHandlers = new ArrayList<>();

    private ArrayList<IElectricSlot> electricSlot = new ArrayList<>();
    private BasicEnergyStorage energyStorage;
    private final LazyOptional<IEnergy> energy = LazyOptional.of(() -> energyStorage);

    private ItemStackHandler batteryHandler;
    private final ArrayList<ElectricSlotHandler> batteryHandlers = new ArrayList<>();

    Block block;
    protected int tickCounter = 0;
    private int cooldown = 0;

    protected boolean isActivate;
    protected boolean hasCooldown;
    protected boolean hasInventory = false;
    protected boolean hasEnergy = false;
    protected boolean hasBattery = false;
    protected boolean hasSound = false;
    protected boolean hasExp = false;

    private SoundEvent soundEvent = null;
    private SoundInstance activeSound;

    private final Map<ResourceLocation, Integer> recipesUsed = Maps.newHashMap();

    public IndRebBlockEntity(BlockEntityType<?> pType, BlockPos pWorldPosition, BlockState pBlockState) {
        super(pType, pWorldPosition, pBlockState);
        this.init();
    }

    public Block getBlock() {
        return block;
    }

    public boolean isHasBattery() {
        return hasBattery;
    }

    public boolean isHasEnergy() {
        return hasEnergy;
    }

    public void init() {
        this.block = getBlockState().getBlock();

        this.setSupportedTypes();
        this.initSlots();
        this.initBatterySlots();

        if (hasSound) {
            soundEvent = ((ITileSound) this).getSoundEvent();
        }
    }

    private void setSupportedTypes() {
        isActivate = block instanceof IStateActive;
        hasCooldown = this instanceof ICooldown;
        hasSound = this instanceof ITileSound;
        hasExp = this instanceof IExpCollector;
    }

    public void updateBlockState() {
        if (level != null) {
            this.setChanged();

            // TODO
            // I don't think its good idea/ practice to do it like that

            level.setBlockAndUpdate(getBlockPos(), getBlockState());
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    public void tickServer(BlockState state) {
        if (tickCounter == 20) {
            tickCounter = 0;
        } else {
            tickCounter++;
        }

        if (hasCooldown) {
            if (tickCounter == 20 && cooldown > 0) {
                --cooldown;
                this.updateBlockState();
            }
        }
    }

    public void tickClient(BlockState state) {
        if (hasSound) {
            handleSound();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        if (hasSound) {
            handleSound();
        }

        if (hasEnergy) {
            energy.invalidate();
        }
    }

    public void initSlots() {
        ArrayList<IndRebSlot> slots = addInventorySlot(new ArrayList<>());
        if (slots.size() > 0) {
            this.initStackHandler(slots);
            this.hasInventory = true;
        }
    }

    public ArrayList<IndRebSlot> addInventorySlot(ArrayList<IndRebSlot> slots) {
        return slots;
    }

    public boolean isItemValidForSlot(final int slot, @Nonnull final ItemStack stack) {
        return true;
    }

    @Nullable
    public ItemStack insertItemForSlot(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return null;
    }

    public void initStackHandler(ArrayList<IndRebSlot> slots) {
        this.slots = slots;
        this.stackHandler = new ItemStackHandler(slots.size()) {
            @Override
            public boolean isItemValid(final int slot, @Nonnull final ItemStack stack) {
                return isItemValidForSlot(slot, stack);
            }

            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                ItemStack returnedStack = insertItemForSlot(slot, stack, simulate);
                return Objects.requireNonNullElseGet(returnedStack, () -> super.insertItem(slot, stack, simulate));
            }

            @Override
            protected void onContentsChanged(final int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }
        };

        slots.forEach(sl -> {
            if (sl.getInventorySlotType() == InventorySlotType.OUTPUT) {
                itemHandlers.add(new SlotItemHandlerOutput(this, stackHandler, sl.getSlotId(), sl.getXPosition(), sl.getYPosition()));
            } else if (sl.getInventorySlotType() == InventorySlotType.DISABLED) {
                itemHandlers.add(new SlotItemHandlerDisabled(stackHandler, sl.getSlotId(), sl.getXPosition(), sl.getYPosition()));
            }else {
                itemHandlers.add(new SlotItemHandler(stackHandler, sl.getSlotId(), sl.getXPosition(), sl.getYPosition()));
            }
        });
    }

    public ArrayList<IndRebSlot> getSlots() {
        return slots;
    }

    public ItemStackHandler getStackHandler() {
        return stackHandler;
    }

    @Override
    public ArrayList<SlotItemHandler> getItemHandlers() {
        return itemHandlers;
    }

    public void initBatterySlots() {
        ArrayList<IElectricSlot> slots = addBatterySlot(new ArrayList<>());
        if (slots.size() > 0) {
            this.initBatteryStackHandler(slots);
            this.hasBattery = true;
        }
    }

    public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
        return slots;
    }

    public void initBatteryStackHandler(ArrayList<IElectricSlot> slots) {
        this.electricSlot = slots;
        this.batteryHandler = new ItemStackHandler(slots.size()) {
            @Override
            public boolean isItemValid(final int slot, @Nonnull final ItemStack stack) {
                return stack.getItem().getTags().contains(ELECTRICS);
            }

            @Override
            protected void onContentsChanged(final int slot) {
                super.onContentsChanged(slot);
                setChanged();
            }
        };

        slots.forEach(sl -> batteryHandlers.add(
                new ElectricSlotHandler(batteryHandler, sl.getSlotId(), sl.getXPosition(), sl.getYPosition(), sl.isCharging(), sl.getInventorySlotType(), sl.getAllowedTiers()))
        );
    }

    public ItemStackHandler getBatteryHandler() {
        return batteryHandler;
    }

    public ArrayList<ElectricSlotHandler> getBatteryHandlers() {
        return batteryHandlers;
    }

    public ArrayList<IElectricSlot> getElectricSlot() {
        return electricSlot;
    }

    public boolean canExtractEnergyDir(Direction side) {
        return false;
    }

    public boolean canReceiveEnergyDir(Direction side) {
        return false;
    }

    public void createEnergyStorage(int energyStored, int maxEnergy, int maxReceiveTick, int maxExtractTick, EnumEnergyType energyType) {
        this.energyStorage = new BasicEnergyStorage(energyStored, maxEnergy, maxReceiveTick, maxExtractTick, energyType) {
            @Override
            public boolean canExtractEnergy(Direction side) {
                return canExtractEnergyDir(side);
            }

            @Override
            public boolean canReceiveEnergy(Direction side) {
                return canReceiveEnergyDir(side);
            }

            @Override
            public void updated() {
                updateBlockState();
            }
        };
        this.hasEnergy = true;
    }

    public BasicEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int time) {
        this.cooldown = time;
    }

    protected boolean getActive() {
        if (isActivate) {
            return ((IStateActive) block).isActive(getBlockState());
        }
        return false;
    }

    protected boolean setActive(boolean active) {
        if (isActivate) {
            assert level != null;
            if (getActive() != active) {
                BlockState state = ((IStateActive) block).setActive(getBlockState(), active);
                level.setBlockAndUpdate(getBlockPos(), state);
                return true;
            }
        }
        return false;
    }

    protected boolean canPlaySound() {
        return getActive();
    }

    private void handleSound() {
        if (hasSound) {
            if (canPlaySound() && !isRemoved()) {
                if (tickCounter > 0) {
                    return;
                }
                if (activeSound == null || !Minecraft.getInstance().getSoundManager().isActive(activeSound)) {
                    activeSound = SoundHandler.startTileSound(soundEvent, SoundSource.BLOCKS, 1F, getBlockPos());
                }
            } else if (activeSound != null) {
                SoundHandler.stopTileSound(getBlockPos());
                activeSound = null;
            }
        }
    }

    public Runnable collectExp() {
        return () -> ModNetworking.INSTANCE.sendToServer(new PacketExperience(getBlockPos()));
    }

    public float getExperience(Recipe<?> recipe) {
        return 0;
    }

    public void collectExp(Player player) {
        List<Recipe<?>> list = Lists.newArrayList();

        for(Map.Entry<ResourceLocation, Integer> entry : this.recipesUsed.entrySet()) {
            player.level.getRecipeManager().byKey(entry.getKey()).ifPresent((recipe -> {
                list.add(recipe);
                spawnExpOrbs(player, entry.getValue(), getExperience(recipe));
            }));
        }

        player.awardRecipes(list);
        this.recipesUsed.clear();
    }

    public void setRecipeUsed(@Nullable Recipe<?> recipe) {
        if (recipe != null) {
            this.recipesUsed.compute(recipe.getId(), (key, val) -> 1 + (val == null ? 0 : val));
        }
    }

    private void spawnExpOrbs(Player player, int entry, float experience) {
        if (experience == 0.0F) {
            entry = 0;
        } else if (experience < 1.0F) {
            int i = (int) Math.floor((float)entry * experience);
            if (i < Math.ceil((float)entry * experience) && Math.random() < (double)((float)entry * experience - (float)i)) {
                ++i;
            }
            entry = i;
        }

        while(entry > 0) {
            int j = ExperienceOrb.getExperienceValue(entry);
            entry -= j;
            level.addFreshEntity(new ExperienceOrb(level, player.getX(), player.getY() + 0.5D, player.getZ() + 0.5D, j));
        }
    }

    public void onPlace() {

    }

    public void onBreak() {
        if (getLevel() == null) return;
        NonNullList<ItemStack> stacks = NonNullList.create();

        if (hasInventory) {
            for (int slot = 0; slot < stackHandler.getSlots(); ++slot) {
                stacks.add(stackHandler.getStackInSlot(slot));
            }
        }

        if (hasBattery) {
            for (int slot = 0; slot < batteryHandler.getSlots(); ++slot) {
                stacks.add(batteryHandler.getStackInSlot(slot));
            }
        }

        Containers.dropContents(getLevel(), getBlockPos(), stacks);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {

        if (hasEnergy) {
            if (cap == ModCapabilities.ENERGY) {
                return energy.cast();
            }
        }

        return super.getCapability(cap, side);
    }
















// sync block data its not good because it always sync

    // chunk loads
    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    // create tag for load
    @Override
    public CompoundTag getUpdateTag() {
        return save(new CompoundTag());
    }


    // on block update

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return new ClientboundBlockEntityDataPacket(this.worldPosition, -1, save(new CompoundTag()));
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        load(pkt.getTag());
    }










    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (hasInventory) {
            stackHandler.deserializeNBT(tag.getCompound("inventory"));
        }

        if (hasBattery) {
            batteryHandler.deserializeNBT(tag.getCompound("battery"));
        }

        if (hasEnergy) {
            energyStorage.setEnergy(tag.getInt("energy"));
        }

        if (hasCooldown) {
            this.cooldown = tag.getInt("cooldown");
        }

        if (hasExp) {
            tag.putShort("RecipesUsedSize", (short)this.recipesUsed.size());
            int i = 0;
            for(Map.Entry<ResourceLocation, Integer> entry : this.recipesUsed.entrySet()) {
                tag.putString("RecipeLocation" + i, entry.getKey().toString());
                tag.putInt("RecipeAmount" + i, entry.getValue());
                ++i;
            }
        }

    }

    @Override
    public CompoundTag save(CompoundTag tag) {

        if (hasInventory) {
            tag.put("inventory", stackHandler.serializeNBT());
        }

        if (hasBattery) {
            tag.put("battery", batteryHandler.serializeNBT());
        }

        if (hasEnergy) {
            tag.putInt("energy", energyStorage.energyStored());
        }

        if (hasCooldown) {
            tag.putInt("cooldown", cooldown);
        }

        if (hasExp) {
            int i = tag.getShort("RecipesUsedSize");
            for(int j = 0; j < i; ++j) {
                ResourceLocation resourcelocation = new ResourceLocation(tag.getString("RecipeLocation" + j));
                int k = tag.getInt("RecipeAmount" + j);
                this.recipesUsed.put(resourcelocation, k);
            }
        }


        return super.save(tag);
    }
}