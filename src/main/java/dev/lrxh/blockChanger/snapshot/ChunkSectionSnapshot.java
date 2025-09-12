package dev.lrxh.blockChanger.snapshot;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;

public record ChunkSectionSnapshot(LevelChunkSection[] sections, ChunkPos position) {
}
