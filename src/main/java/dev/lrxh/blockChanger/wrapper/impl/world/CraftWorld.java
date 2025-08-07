package dev.lrxh.blockChanger.wrapper.impl.world;

import dev.lrxh.blockChanger.BlockChanger;
import dev.lrxh.blockChanger.wrapper.CraftWrapper;
import dev.lrxh.blockChanger.wrapper.impl.world.impl.v1_21.CraftWorld_1_21;
import org.bukkit.World;

public abstract class CraftWorld extends CraftWrapper<World> {
    public CraftWorld(World input) {
        super(input);
    }

    public static CraftWorld from(World o) {
        return switch (BlockChanger.getMinorVersion()) {
            case 21 -> new CraftWorld_1_21(o);
            default -> throw new IllegalStateException("Unexpected minor version: " + BlockChanger.getMinorVersion());
        };
    }
}
