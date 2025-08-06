package dev.lrxh.blockChanger.wrapper.impl.block.impl.v1_21;

import dev.lrxh.blockChanger.wrapper.impl.block.IBlockData;
import org.bukkit.block.data.BlockData;

import java.lang.invoke.MethodHandle;

public class IBlockData_1_21 extends IBlockData {
    public IBlockData_1_21(BlockData input) {
        super(input);
    }

    @Override
    protected Object apply(BlockData input) {
        try {
            Object craftBlockData = cb("block.data.CraftBlockData").cast(input);
            Class<?> iBlockData = nms("world.level.block.state.IBlockData");
            MethodHandle getState = getMethod(craftBlockData.getClass(), "getState", iBlockData);

            return getState.invoke(craftBlockData);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get block state", e);
        }
    }
}
