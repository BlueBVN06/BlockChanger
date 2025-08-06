package dev.lrxh.blockChanger.wrapper.impl.block;

import dev.lrxh.blockChanger.BlockChanger;
import dev.lrxh.blockChanger.wrapper.CraftWrapper;
import dev.lrxh.blockChanger.wrapper.impl.block.impl.v1_21.IBlockData_1_21;
import org.bukkit.block.data.BlockData;

public abstract class IBlockData extends CraftWrapper<BlockData> {
    public IBlockData(BlockData input) {
        super(input);
    }

    public static IBlockData from(BlockData o) {
        return switch (BlockChanger.getMinorVersion()) {
            case 21 -> new IBlockData_1_21(o);
            default -> throw new IllegalStateException("Unexpected minor version: " + BlockChanger.getMinorVersion());
        };
    }

    @Override
    protected Object apply(BlockData input) {
        return input;
    }
}
