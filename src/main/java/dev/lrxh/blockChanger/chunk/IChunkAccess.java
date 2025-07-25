package dev.lrxh.blockChanger.chunk;

import dev.lrxh.blockChanger.BlockChanger;
import dev.lrxh.blockChanger.CraftWrapper;
import dev.lrxh.blockChanger.chunk.impl.v1_21.IChunkAccess_1_21;

public abstract class IChunkAccess extends CraftWrapper<Object> {
    public IChunkAccess(Object input) {
        super(input);
    }

    public static IChunkAccess from(Object o) {
        return switch (BlockChanger.getMinorVersion()) {
            case 21 -> new IChunkAccess_1_21(o);
            default -> throw new IllegalStateException("Unexpected minor version: " + BlockChanger.getMinorVersion());
        };
    }

    @Override
    protected Object apply(Object input) {
        return input;
    }

    public abstract Object[] getSections();

    public abstract void setSections(Object[] newSections);

    public abstract Object[] getSectionsCopy();
}
