package com.maciej916.indreb.common.block.impl.machines.extruder;

import com.maciej916.indreb.common.config.ServerConfig;
import com.maciej916.indreb.common.energy.interfaces.IEnergyBlock;
import com.maciej916.indreb.common.entity.block.BlockEntityProgress;
import com.maciej916.indreb.common.entity.block.FluidStorage;
import com.maciej916.indreb.common.entity.block.IndRebBlockEntity;
import com.maciej916.indreb.common.entity.slot.SlotBattery;
import com.maciej916.indreb.common.entity.slot.IndRebSlot;
import com.maciej916.indreb.common.enums.EnergyTier;
import com.maciej916.indreb.common.enums.GuiSlotType;
import com.maciej916.indreb.common.enums.InventorySlotType;
import com.maciej916.indreb.common.interfaces.entity.IExpCollector;
import com.maciej916.indreb.common.interfaces.entity.ITileSound;
import com.maciej916.indreb.common.interfaces.entity.IElectricSlot;
import com.maciej916.indreb.common.network.ModNetworking;
import com.maciej916.indreb.common.network.packet.PacketExtruderRecipe;
import com.maciej916.indreb.common.receipe.impl.ExtrudingRecipe;
import com.maciej916.indreb.common.registries.ModBlockEntities;
import com.maciej916.indreb.common.registries.ModRecipeType;
import com.maciej916.indreb.common.registries.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;
import org.apache.logging.log4j.core.jmx.Server;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.maciej916.indreb.common.enums.EnumEnergyType.RECEIVE;

public class BlockEntityExtruder extends IndRebBlockEntity implements IEnergyBlock, ITileSound, IExpCollector {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    public FluidStorage waterStorage = new FluidStorage(8000);
    public FluidStorage lavaStorage = new FluidStorage(8000);
    public BlockEntityProgress progress = new BlockEntityProgress();

    protected int recipeIndex = 0;
    protected static List<ExtrudingRecipe> recipes;
    protected ExtrudingRecipe recipe;

    private boolean active = false;

    public void setRecipe(int index) {
        if (level != null) {
            this.recipe = Objects.requireNonNullElseGet(recipes, () -> level.getRecipeManager().getAllRecipesFor(ModRecipeType.EXTRUDING)).get(index);
            getStackHandler().setStackInSlot(INPUT_SLOT, this.recipe.getResultItem());
            progress.setBoth(-1);
        }
    }

    public BlockEntityExtruder(BlockPos pWorldPosition, BlockState pBlockState) {
        super(ModBlockEntities.EXTRUDER, pWorldPosition, pBlockState);
        createEnergyStorage(0, ServerConfig.extruder_energy_capacity.get(), EnergyTier.BASIC.getBasicTransfer(), 0, RECEIVE);
        waterStorage.setFluid(new FluidStack(Fluids.WATER, 0));
        lavaStorage.setFluid(new FluidStack(Fluids.LAVA, 0));
    }

    @Override
    public ArrayList<IndRebSlot> addInventorySlot(ArrayList<IndRebSlot> slots) {
        slots.add(new IndRebSlot(INPUT_SLOT, 80, 59, InventorySlotType.DISABLED, GuiSlotType.NORMAL, 79, 58));
        slots.add(new IndRebSlot(OUTPUT_SLOT, 121, 35, InventorySlotType.OUTPUT, GuiSlotType.LARGE, 116, 30));
        return super.addInventorySlot(slots);
    }

    @Override
    public ArrayList<IElectricSlot> addBatterySlot(ArrayList<IElectricSlot> slots) {
        slots.add(new SlotBattery(0, 152, 62, false, List.of(EnergyTier.BASIC)));
        return super.addBatterySlot(slots);
    }

    @Override
    public SoundEvent getSoundEvent() {
        return ModSounds.EXTRACTOR;
    }

    @Override
    public boolean isItemValidForSlot(int slot, @Nonnull ItemStack stack) {
        if (slot == INPUT_SLOT || slot == OUTPUT_SLOT) return false;
        return super.isItemValidForSlot(slot, stack);
    }

    public Runnable prevRecipe() {
        return () -> ModNetworking.INSTANCE.sendToServer(new PacketExtruderRecipe(getBlockPos(), false));
    }

    public Runnable nextRecipe() {
        return () -> ModNetworking.INSTANCE.sendToServer(new PacketExtruderRecipe(getBlockPos(), true));
    }

    public void initRecipes() {
        recipes = Objects.requireNonNull(getLevel()).getRecipeManager().getAllRecipesFor(ModRecipeType.EXTRUDING);
    }

    public void changeRecipe(boolean next) {
        if (recipes != null) {
            int newIndex = recipeIndex + (next ? 1 : -1);
            if (newIndex > recipes.size() - 1) newIndex = 0;
            if (newIndex < 0) newIndex = recipes.size() - 1;

            setRecipe(newIndex);
            this.recipeIndex = newIndex;

            this.progress.setBoth(-1);
            this.setActive(false);
            this.updateBlockState();
        }
    }

    @Override
    public void tickServer(BlockState state) {
        active = false;

        if (recipes == null) {
            initRecipes();
        }

        if (this.recipe == null && recipes != null) {
            setRecipe(0);
            this.updateBlockState();
        }

        final ItemStack outputStack = getStackHandler().getStackInSlot(OUTPUT_SLOT);

        if (
                recipe != null &&
                getEnergyStorage().consumeEnergy(recipe.getPowerCost(), true) >= recipe.getPowerCost() &&
                recipe.getResultItem().getCount() + outputStack.getCount() <= outputStack.getMaxStackSize() &&
                (outputStack.isEmpty() || outputStack.getItem() == recipe.getResultItem().getItem()) &&
                !waterStorage.getFluid().isEmpty() &&
                !lavaStorage.getFluid().isEmpty() &&
                lavaStorage.takeFluid(recipe.getWaterCost(), true) == recipe.getWaterCost() &&
                lavaStorage.takeFluid(recipe.getLavaCost(), true) == recipe.getLavaCost()
        ) {

                if (progress.getProgress() == -1) {
                    progress.setData(0, recipe.getDuration());
                }

                getEnergyStorage().consumeEnergy(recipe.getPowerCost(), false);
                progress.incProgress(1);
                active = true;
        }

        if (progress.getProgress() > 0 && progress.getProgress() == progress.getProgressMax()) {
            progress.setBoth(-1);
            this.setRecipeUsed(recipe);

            if (recipe.getWaterCost() > 0) waterStorage.takeFluid(recipe.getWaterCost(), false);
            if (recipe.getLavaCost() > 0) lavaStorage.takeFluid(recipe.getLavaCost(), false);

            if (outputStack.isEmpty()) {
                getStackHandler().setStackInSlot(OUTPUT_SLOT, recipe.getResultItem().copy());
            } else {
                getStackHandler().getStackInSlot(OUTPUT_SLOT).grow(recipe.getResultItem().getCount());
            }
        }

        this.setActive(active);
        if (progress.changed()) {
            super.updateBlockState();
        }

        super.tickServer(state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.lavaStorage.readFromNBT(tag.getCompound("lava_storage"));
        this.waterStorage.readFromNBT(tag.getCompound("water_storage"));
        this.active = tag.getBoolean("active");
        this.progress.deserializeNBT(tag.getCompound("progress"));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("lava_storage", this.lavaStorage.writeToNBT(tag.getCompound("lava_storage")));
        tag.put("water_storage", this.waterStorage.writeToNBT(tag.getCompound("water_storage")));
        tag.putBoolean("active", active);
        tag.put("progress", this.progress.serializeNBT());
        return super.save(tag);
    }

    @Override
    public float getExperience(Recipe<?> recipe) {
        return ((ExtrudingRecipe) recipe).getExperience();
    }

    @Override
    public boolean canReceiveEnergyDir(Direction side) {
        return true;
    }

    ArrayList<LazyOptional<?>> capabilities = new ArrayList<>(Arrays.asList(
            LazyOptional.of(this::getStackHandler),
            LazyOptional.of(() -> new RangedWrapper(getStackHandler(), OUTPUT_SLOT, OUTPUT_SLOT + 1))
    ));

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull final Capability<T> cap, @Nullable final Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return capabilities.get(1).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void onBreak() {
        for (LazyOptional<?> capability : capabilities) capability.invalidate();
        super.onBreak();
    }
}