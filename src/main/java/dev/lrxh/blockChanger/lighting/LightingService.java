package dev.lrxh.blockChanger.lighting;

import dev.lrxh.blockChanger.BlockChanger;
import dev.lrxh.blockChanger.wrapper.impl.lighting.ThreadedLevelLightEngine;
import org.bukkit.Chunk;

import java.util.Set;

public class LightingService {
    public static void updateLighting(Set<Chunk> chunks, boolean refresh) {
        if (!BlockChanger.isPaper) return;
        ThreadedLevelLightEngine lightEngine = ThreadedLevelLightEngine.from(chunks.iterator().next().getWorld());
        lightEngine.relightChunks(chunks);

        if (refresh) {
            for (Chunk chunk : chunks) {
                chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
            }
        }
    }
}
