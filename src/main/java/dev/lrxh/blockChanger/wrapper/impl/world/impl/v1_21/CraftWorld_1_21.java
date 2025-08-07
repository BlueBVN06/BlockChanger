package dev.lrxh.blockChanger.wrapper.impl.world.impl.v1_21;

import dev.lrxh.blockChanger.wrapper.impl.world.CraftWorld;
import org.bukkit.World;

public class CraftWorld_1_21 extends CraftWorld {
    public CraftWorld_1_21(World input) {
        super(input);
    }

    @Override
    protected Object apply(World input) {
        Class<?> craftWorldClass = cb("CraftWorld");
        return craftWorldClass.cast(input);
    }
}
