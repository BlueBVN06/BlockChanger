package dev.lrxh.blockChanger.wrapper.impl.chunk.impl.v1_21;

import dev.lrxh.blockChanger.wrapper.impl.block.IBlockData;
import dev.lrxh.blockChanger.wrapper.impl.chunk.IChunkAccess;
import org.bukkit.block.data.BlockData;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IChunkAccess_1_21 extends IChunkAccess {
    private final Map<Integer, Object> sections;

    public IChunkAccess_1_21(Object input) {
        super(input);
        this.sections = new HashMap<>();
    }

    @Override
    public Object[] getSections() {
        try {
            Class<?> chunkClass = this.get().getClass();

            Class<?> chunkSectionClass = nms("world.level.chunk.ChunkSection");
            MethodHandle getSectionsHandle = getMethod(chunkClass, "d", Array.newInstance(chunkSectionClass, 0).getClass());
            return (Object[]) getSectionsHandle.invoke(this.get());
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get chunk handle", e);
        }
    }

    @Override
    public void setSections(Object[] newSections) {
        Object[] currentSections = getSections();

        if (currentSections.length != newSections.length) {
            throw new IllegalArgumentException("Section count mismatch: expected "
                    + currentSections.length + ", but got " + newSections.length);
        }

        for (int i = 0; i < currentSections.length; i++) {
            currentSections[i] = copySection(newSections[i]);
        }
    }

    @Override
    public Object[] getSectionsCopy() {
        try {
            Object[] sections = getSections();

            List<Object> copiedSections = new ArrayList<>(sections.length);

            for (Object section : sections) {
                copiedSections.add(copySection(section));
            }

            return copiedSections.toArray(new Object[0]);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get chunk handle", e);
        }
    }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        try {
            Object section = getSection(y);
            IBlockData iBlockData = IBlockData.from(blockData);
            Class<?> iBlockDataClass = nms("world.level.block.state.IBlockData");
            MethodHandle setType = getMethod(
                    section.getClass(),
                    "a",
                    iBlockDataClass,
                    int.class,
                    int.class,
                    int.class,
                    iBlockDataClass
            );

            int localX = x & 15;
            int localY = y & 15;
            int localZ = z & 15;

            setType.invoke(section, localX, localY, localZ, iBlockData.get());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getSection(int y) {
        try {
            if (sections.containsKey(y)) {
                return sections.get(y);
            }
            Object levelHeightAccessor = nms("world.level.LevelHeightAccessor").cast(get());
            int sectionIndex = (int) getMethod(levelHeightAccessor.getClass(), "f", int.class, int.class).invoke(levelHeightAccessor, y);
            Object section = getSections()[sectionIndex];
            sections.put(sectionIndex, section);
            return section;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to get section", e);
        }
    }

    private Object copySection(Object section) {
        try {
            MethodHandle copyHandle = getMethod(section.getClass(), "k", section.getClass());
            return copyHandle.invoke(section);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to copy chunk section", e);
        }
    }
}
