package com.maciej916.indreb.common.energy.provider.comparator;

import com.maciej916.indreb.common.energy.interfaces.IEnergy;
import net.minecraft.world.item.ItemStack;

public class EnergyExtractComparator extends EnergyComparator<EnergyExtractComparator> {

    ItemStack stack;

    public EnergyExtractComparator(IEnergy energy, ItemStack stack) {
        super(energy);
        this.stack = stack;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public int compareTo(EnergyExtractComparator o1) {
        return getEnergy().maxExtract() > o1.getEnergy().maxExtract() ? 1 : 0;
    }
}
