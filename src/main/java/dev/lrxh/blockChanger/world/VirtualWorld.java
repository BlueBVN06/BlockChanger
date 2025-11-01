package dev.lrxh.blockChanger.world;

import dev.lrxh.blockChanger.BlockChanger;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import dev.lrxh.blockChanger.snapshot.SnapshotService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Map;

@SuppressWarnings({"unused"})
public class VirtualWorld {
  private final ServerLevel level;
  private final Listener listener;

  public VirtualWorld(ServerLevel level, Listener listener) {
    this.level = level;
    this.listener = listener;
  }

  public World getWorld() {
    return level.getWorld();
  }

  public void unload() {
    try {
      level.getChunkSource().getDataStorage().close();
      level.moonrise$getChunkTaskScheduler().chunkHolderManager.close(false, false);
      level.levelStorageAccess.close();
    } catch (Exception ignored) {
    }
    MinecraftServer.getServer().removeLevel(level);
    HandlerList.unregisterAll(listener);
    BlockChanger.removeVirtualWorld(this);
  }

  public void paste(CuboidSnapshot snapshot) {
    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      SnapshotService.addSnapshot(entry.getValue(), this.getWorld());
    }
  }
}
