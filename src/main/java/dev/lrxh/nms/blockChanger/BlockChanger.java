package dev.lrxh.nms.blockChanger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author lrxxh
 * @apiNote 1.16.5 - 1.21.4 easy to use util to be able to
 * set blocks blazingly fast
 */
public final class BlockChanger {
    private final int MINOR_VERSION;
    private final JavaPlugin plugin;
    private final boolean debug;
    private final HashMap<Object, Object> worldCache;

    // NMS Classes
    private Class<?> CRAFT_BLOCK_DATA;
    private Class<?> LEVEL_HEIGHT_ACCESSOR;
    private Class<?> CRAFT_WORLD;
    private Class<?> WORLD_SERVER;
    // NMS MethodHandles
    private MethodHandle GET_STATE;
    private MethodHandle GET_SECTIONS;
    private MethodHandle GET_SECTION_INDEX;
    private MethodHandle HAS_ONLY_AIR;
    private MethodHandle GET_CHUNK_AT;
    private MethodHandle GET_HANDLE_WORLD;
    private MethodHandle GET_STATES;
    private MethodHandle GET_AND_SET_UNCHECKED;
    private MethodHandle GET;
    // NMS Fields
    private Field NON_EMPTY_BLOCK_COUNT;
    // NMS Constructors
    private Constructor<?> CRAFT_BLOCK_DATA_CONSTRUCTOR;

    public BlockChanger(JavaPlugin instance, boolean debug) {
        plugin = instance;
        MINOR_VERSION = getMinorVersion();
        this.debug = debug;
        this.worldCache = new HashMap<>();

        init();
    }

    /**
     * Sets block's block-data using NMS.
     * This shouldn't be used for multi block changes
     * instead use setBlocks.
     *
     * @param location  world where the block is located
     * @param blockData Block data to be set
     */
    public void setBlock(Location location, BlockData blockData) {
        setBlock(location, blockData, location.getChunk(), false, null);
    }

    /**
     * Gets location's block-data using NMS.
     * This can be run async;
     *
     * @param  location  location to return block-data from
     * @return BlockData Block data found at given location
     */
    public BlockData getBlockDataAt(Location location) {
        Object blockDataNMS = getBlockDataNMS(location);
        return getBlockDataFromNMS(blockDataNMS);
    }

    /**
     * Sets blocks block-data's using NMS.
     * This is suggested to be run async.
     *
     * @param blocks Map of locations and blockade to be set
     */
    public void setBlocks(Map<Location, BlockData> blocks) {
        HashMap<Object, Object> chunkCache = new HashMap<>();

        for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
            setBlock(entry.getKey(), entry.getValue(), entry.getKey().getChunk(), true, chunkCache);
        }
    }

    /**
     * Capture all blocks between 2 positions
     *
     * @param pos1 Position 1
     * @param pos2 Position 2
     * @return Snapshot This is needed to revert captured snapshot
     * */
    public Snapshot capture(Location pos1, Location pos2) {
        Location max = new Location(pos1.getWorld(), Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
        Location min = new Location(pos1.getWorld(), Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));

        Snapshot snapshot = new Snapshot();
        World world = max.getWorld();
        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());

        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location location = new Location(world, x, y, z);
                    Object blockDataNMS = getBlockDataNMS(location);
                    BlockData blockData = getBlockDataFromNMS(blockDataNMS);

                    snapshot.add(new BlockSnapshot(location, blockData, blockDataNMS, location.getChunk()));
                }
            }
        }

        return snapshot;
    }

    /**
     * Revert all changes from the snapshot, this runs async
     *
     * @param snapshot Snapshot you have captured
     * */
    public void revert(Snapshot snapshot) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long startTime = System.currentTimeMillis();

            HashMap<Object, Object> chunkCache = new HashMap<>();
            for (BlockSnapshot blockSnapshot : snapshot.snapshots) {
                setBlock(blockSnapshot, chunkCache);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            debug("Snapshot revert time: " + duration + " ms (" + snapshot.snapshots.size() + ")");
        });
    }

    private boolean isPlayerSeeingChunk(Player player, Chunk chunk) {
        if (!chunk.isLoaded()) return false;

        int viewDistance = player.getWorld().getViewDistance();
        int playerX = player.getLocation().getBlockX();
        int playerZ = player.getLocation().getBlockZ();

        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;

        int distanceX = Math.abs(playerX - chunkX);
        int distanceZ = Math.abs(playerZ - chunkZ);

        return distanceX <= viewDistance * 16 && distanceZ <= viewDistance * 16;
    }

    private Object getBlockDataNMS(Location location) {
        try {
            Object nmsWorld = getWorldNMS(location.getWorld());

            Object nmsChunk = getChunkNMS(nmsWorld, location.getChunk(), false, null);

            int x = (int) location.getX();
            int y = location.getBlockY();
            int z = (int) location.getZ();

            Object cs = getSection(nmsChunk, y);

            return GET.invoke(GET_STATES.invoke(cs), x & 15, y & 15, z & 15);

        } catch (Throwable e) {
            debug("Error occurred while at #getBlock(World, Location) " + e.getMessage());
        }
        return null;
    }

    private void setBlock(Location location, BlockData blockData, Chunk chunk, boolean cache, HashMap<Object, Object> chunkCache) {
        if (chunk == null) return;
        try {
            Object nmsBlockData = getBlockDataNMS(blockData);

            Object nmsWorld = getWorldNMS(location.getWorld());

            Object nmsChunk = getChunkNMS(nmsWorld, chunk, cache, chunkCache);

            int x = (int) location.getX();
            int y = location.getBlockY();
            int z = (int) location.getZ();

            Object cs = getSection(nmsChunk, y);

            if (hasOnlyAir(cs, blockData)) return;

            Object result = GET_AND_SET_UNCHECKED.invoke(GET_STATES.invoke(cs), x & 15, y & 15, z & 15, nmsBlockData);

            if (result == nmsBlockData) return;

            for (Player player : chunk.getWorld().getPlayers()) {
                if (isPlayerSeeingChunk(player, chunk)) player.sendBlockChange(location, blockData);
            }

        } catch (Throwable e) {
            debug("Error occurred while at #setBlockNew(Location, BlockData) " + e.getMessage());
        }
    }

    private void setBlock(BlockSnapshot snapshot, HashMap<Object, Object> chunkCache) {
        try {
            Object nmsBlockData = snapshot.blockDataNMS;
            BlockData blockData = snapshot.blockData;
            Location location = snapshot.location;

            Chunk chunk = snapshot.chunk;
            Object nmsWorld = getWorldNMS(snapshot.location.getWorld());

            Object nmsChunk = getChunkNMS(nmsWorld, chunk, true, chunkCache);

            int x = (int) location.getX();
            int y = location.getBlockY();
            int z = (int) location.getZ();

            Object cs = getSection(nmsChunk, y);

            if (hasOnlyAir(cs, blockData)) return;

            Object result = GET_AND_SET_UNCHECKED.invoke(GET_STATES.invoke(cs), x & 15, y & 15, z & 15, nmsBlockData);

            if (result == nmsBlockData) return;

            for (Player player : chunk.getWorld().getPlayers()) {
                if (isPlayerSeeingChunk(player, chunk)) player.sendBlockChange(location, blockData);
            }

        } catch (Throwable e) {
            debug("Error occurred while at #setBlockNew(Location, BlockData) " + e.getMessage());
        }
    }

    private Object getWorldNMS(World world) {
        Object c = worldCache.get(world.getName());
        if (c != null) return c;
        try {
            Object craftWorld = CRAFT_WORLD.cast(world);
            Object worldServer = WORLD_SERVER.cast(GET_HANDLE_WORLD.invoke(craftWorld));
            worldCache.put(world.getName(), worldServer);

            return worldServer;
        } catch (Throwable e) {
            debug("Error occurred while at #getWorldNMS(World) " + e.getMessage());
        }
        return null;
    }

    private Object getChunkNMS(Object world, Chunk chunk, boolean cache, HashMap<Object, Object> chunkCache) {
        if (cache) {
            Object c = chunkCache.get(chunk);
            if (c != null) return c;
        }

        try {
            Object nmsChunk = GET_CHUNK_AT.invoke(world, chunk.getX(), chunk.getZ());

            if (cache) chunkCache.put(chunk, nmsChunk);

            return nmsChunk;
        } catch (Throwable e) {
            debug("Error occurred while at #getChunkNMS(Object, Chunk, boolean) " + e.getMessage());
        }
        return null;
    }

    private Object getLevelHeightAccessor(Object nmsChunk) {
        try {
            return LEVEL_HEIGHT_ACCESSOR.cast(nmsChunk);
        } catch (Throwable e) {
            debug("Error occurred while at #getLevelHeightAccessor(Object) " + e.getMessage());
        }
        return null;
    }

    private BlockData getBlockDataFromNMS(Object blockDataNMS) {
        try {
            return (BlockData) CRAFT_BLOCK_DATA_CONSTRUCTOR.newInstance(blockDataNMS);
        } catch (Throwable e) {
            debug("Error occurred while at #getBlockDataFromNMS(Object) " + e.getMessage());
        }
        return null;
    }

    private Object getBlockDataNMS(BlockData blockData) {
        try {
            return GET_STATE.invoke(CRAFT_BLOCK_DATA.cast(blockData));
        } catch (Throwable e) {
            debug("Error occurred while at #getBlockDataNMS(BlockData) " + e.getMessage());
        }
        return null;
    }

    private Object getSection(Object nmsChunk, int index) {
        try {
            if (LEVEL_HEIGHT_ACCESSOR != null) {
                Object LevelHeightAccessor = getLevelHeightAccessor(nmsChunk);

                int i = (int) GET_SECTION_INDEX.invoke(LevelHeightAccessor, index);

                return getSections(nmsChunk)[i];
            } else {
                return getSections(nmsChunk)[index >> 4];
            }
        } catch (Throwable e) {
            debug("Error occurred while at #getSection(Object, int) " + e.getMessage());
        }

        return null;
    }

    private void init() {
        String CRAFT_BUKKIT;
        String NET_MINECRAFT = "net.minecraft.";

        if (!supports(16)) {
            plugin.getLogger().info("Version Unsupported by BlockChanger");
            return;
        }

        if (MINOR_VERSION == 16) {
            NET_MINECRAFT = "net.minecraft.server." + plugin.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3] + ".";
        }
        if (supports(21)) {
            CRAFT_BUKKIT = "org.bukkit.craftbukkit.";
        } else {
            CRAFT_BUKKIT = "org.bukkit.craftbukkit." + plugin.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3] + ".";
        }

        Class<?> I_BLOCK_DATA;

        if (MINOR_VERSION != 16) {
            I_BLOCK_DATA = loadClass(NET_MINECRAFT + "world.level.block.state.IBlockData");
        } else {
            I_BLOCK_DATA = loadClass(NET_MINECRAFT + "IBlockData");
        }
        debug("I_BLOCK_DATA Loaded");

        Class<?> DATA_PALETTE_BLOCK;
        if (MINOR_VERSION != 16) {
            DATA_PALETTE_BLOCK = loadClass(NET_MINECRAFT + "world.level.chunk.DataPaletteBlock");
        } else {
            DATA_PALETTE_BLOCK = loadClass(NET_MINECRAFT + "DataPaletteBlock");
        }
        debug("DATA_PALETTE_BLOCK Loaded");

        Class<?> CHUNK;
        if (MINOR_VERSION != 16) {
            CHUNK = loadClass(NET_MINECRAFT + "world.level.chunk.Chunk");
        } else {
            CHUNK = loadClass(NET_MINECRAFT + "Chunk");
        }
        debug("CHUNK Loaded");

        Class<?> CHUNK_SECTION;

        if (MINOR_VERSION != 16) {
            CHUNK_SECTION = loadClass(NET_MINECRAFT + "world.level.chunk.ChunkSection");
        } else {
            CHUNK_SECTION = loadClass(NET_MINECRAFT + "ChunkSection");
        }
        debug("CHUNK_SECTION Loaded");

        if (MINOR_VERSION != 16) {
            WORLD_SERVER = loadClass(NET_MINECRAFT + "server.level.WorldServer");
        } else {
            WORLD_SERVER = loadClass(NET_MINECRAFT + "WorldServer");
        }
        debug("WORLD_SERVER Loaded");

        Class<?> WORLD;
        if (MINOR_VERSION != 16) {
            WORLD = loadClass(NET_MINECRAFT + "world.level.World");
        } else {
            WORLD = loadClass(NET_MINECRAFT + "World");
        }
        debug("WORLD Loaded");

        if (MINOR_VERSION != 16) {
            LEVEL_HEIGHT_ACCESSOR = loadClass(NET_MINECRAFT + "world.level.LevelHeightAccessor");
        } else {
            LEVEL_HEIGHT_ACCESSOR = loadClass(NET_MINECRAFT + "LevelHeightAccessor");
        }
        debug("LEVEL_HEIGHT_ACCESSOR Loaded");

        CRAFT_WORLD = loadClass(CRAFT_BUKKIT + "CraftWorld");
        debug("CRAFT_WORLD Loaded");

        Class<?> i_CHUNK_ACCESS;

        if (MINOR_VERSION != 16) {
            i_CHUNK_ACCESS = loadClass(NET_MINECRAFT + "world.level.chunk.IChunkAccess");
        } else {
            i_CHUNK_ACCESS = loadClass(NET_MINECRAFT + "IChunkAccess");
        }
        debug("I_CHUNK_ACCESS Loaded");

        CRAFT_BLOCK_DATA = loadClass(CRAFT_BUKKIT + "block.data.CraftBlockData");
        debug("CRAFT_BLOCK_DATA Loaded");

        try {
            GET_STATE = getMethodHandle(CRAFT_BLOCK_DATA, "getState", I_BLOCK_DATA);
            debug("GET_STATE Loaded");
        } catch (Throwable e) {
            debug("GET_STATE didn't load " + e.getCause().getMessage());
        }

        try {
            GET = getMethodHandle(DATA_PALETTE_BLOCK, "a", Object.class, int.class, int.class, int.class);
            debug("SET Loaded");
        } catch (Throwable e) {
            debug("SET didn't load " + e.getCause().getMessage());
        }

        try {
            if (supports(21) || MINOR_VERSION == 16) {
                GET_SECTIONS = getMethodHandle(i_CHUNK_ACCESS, "getSections", Object[].class);
            } else {
                GET_SECTIONS = getMethodHandle(i_CHUNK_ACCESS, "d", Array.newInstance(CHUNK_SECTION, 0).getClass());
            }
            debug("GET_SECTIONS Loaded");
        } catch (Throwable e) {
            debug("GET_SECTIONS didn't load " + e.getCause().getMessage());
        }

        try {
            if (supports(20)) {
                GET_STATES = getMethodHandle(CHUNK_SECTION, "h", DATA_PALETTE_BLOCK);
            } else {
                GET_STATES = getMethodHandle(CHUNK_SECTION, "i", DATA_PALETTE_BLOCK);
            }
            debug("GET_STATES Loaded");
        } catch (Throwable e) {
            debug("GET_STATES didn't load " + e.getCause().getMessage());
        }

        try {
            GET_AND_SET_UNCHECKED = getMethodHandle(DATA_PALETTE_BLOCK, "b", Object.class, int.class, int.class, int.class, Object.class);
            debug("SET Loaded");
        } catch (Throwable e) {
            debug("SET didn't load " + e.getCause().getMessage());
        }

        try {
            if (MINOR_VERSION == 21) {
                GET_SECTION_INDEX = getMethodHandle(LEVEL_HEIGHT_ACCESSOR, "f", int.class, int.class);
            } else if (supports(17)) {
                GET_SECTION_INDEX = getMethodHandle(LEVEL_HEIGHT_ACCESSOR, "e", int.class, int.class);
            }
            debug("GET_SECTION_INDEX Loaded");
        } catch (Throwable e) {
            debug("GET_SECTIONS didn't load " + e.getCause().getMessage());
        }

        try {
            if (supports(18)) {
                HAS_ONLY_AIR = getMethodHandle(CHUNK_SECTION, "c", boolean.class);
            }
            debug("HAS_ONLY_AIR Loaded");
        } catch (Throwable e) {
            debug("GET_SECTIONS didn't load " + e.getCause().getMessage());
        }

        try {
            if (HAS_ONLY_AIR == null) {
                assert CHUNK_SECTION != null;
                if (MINOR_VERSION == 17) {
                    NON_EMPTY_BLOCK_COUNT = getDeclaredField(CHUNK_SECTION, "f");
                } else if (MINOR_VERSION == 16) {
                    NON_EMPTY_BLOCK_COUNT = getDeclaredField(CHUNK_SECTION, "c");
                }

                debug("NON_EMPTY_BLOCK_COUNT Loaded");
            }
        } catch (Throwable e) {
            debug("NON_EMPTY_BLOCK_COUNT didn't load " + e.getCause().getMessage());
        }

        try {
            GET_CHUNK_AT = getMethodHandle(WORLD, "d", CHUNK, int.class, int.class);
            debug("GET_CHUNK_AT Loaded");
        } catch (Throwable e) {
            debug("GET_CHUNK_AT didn't load " + e.getCause().getMessage());
        }

        try {
            GET_HANDLE_WORLD = getMethodHandle(CRAFT_WORLD, "getHandle", WORLD_SERVER);
            debug("GET_HANDLE_WORLD Loaded");
        } catch (Throwable e) {
            debug("GET_HANDLE_WORLD didn't load " + e.getCause().getMessage());
        }

        CRAFT_BLOCK_DATA_CONSTRUCTOR = getConstructor(CRAFT_BLOCK_DATA, I_BLOCK_DATA);
        debug("CRAFT_BLOCK_DATA_CONSTRUCTOR Loaded");
    }

    private boolean hasOnlyAir(Object cs, BlockData blockData) {
        try {
            if (HAS_ONLY_AIR != null) {
                if ((Boolean) HAS_ONLY_AIR.invoke(cs) && blockData.getMaterial().isAir()) return true;
            } else {
                if ((Short) NON_EMPTY_BLOCK_COUNT.get(cs) == 0 && blockData.getMaterial().isAir()) return true;
            }
        } catch (Throwable e) {
            debug("GET_HANDLE_WORLD didn't load " + e.getCause().getMessage());
        }

        return false;
    }

    private MethodHandle getMethodHandle(Class<?> clazz, String methodName, Class<?> rtype, Class<?>... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        return lookup.findVirtual(clazz, methodName, MethodType.methodType(rtype, parameterTypes));
    }

    private Object[] getSections(Object nmsChunk) {
        try {
            return (Object[]) GET_SECTIONS.invoke(nmsChunk);
        } catch (Throwable e) {
            debug("Error occurred while at #getSections(Object) " + e.getMessage());
        }
        return new Object[0];
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            debug("Error occurred while at #loadClass(String) " + e.getMessage());
        }
        return null;
    }

    private Field getDeclaredField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            debug("Error occurred while at #getDeclaredField(Class<?>, String) " + e.getMessage());
        }
        return null;
    }

    private Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (Exception e) {
            debug("Error occurred while at invokeConstructor: " + e.getMessage());
        }
        return null;
    }

    private void printAllMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            System.out.print("Method: " + method.getName());
            System.out.print(" | Return type: " + method.getReturnType().getSimpleName());
            System.out.print(" | Modifiers: " + Modifier.toString(method.getModifiers()));
            System.out.print(" | Parameters: ");
            Parameter[] parameters = method.getParameters();
            if (parameters.length == 0) {
                System.out.print("None");
            } else {
                for (Parameter param : parameters) {
                    System.out.print(param.getType().getSimpleName() + " " + param.getName() + ", ");
                }
                System.out.print("\b\b");
            }
            System.out.println();
        }
    }

    private void printAllFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            System.out.print("Field: " + field.getName());
            System.out.print(" | Type: " + field.getType().getSimpleName());
            System.out.print(" | Modifiers: " + Modifier.toString(field.getModifiers()));
            System.out.println();
        }
    }

    private boolean supports(int version) {
        return MINOR_VERSION >= version;
    }

    private int getMinorVersion() {
        String[] versionParts = plugin.getServer().getBukkitVersion().split("-")[0].split("\\.");
        if (versionParts.length >= 2) {
            return Integer.parseInt(versionParts[1]);
        }
        return 0;
    }

    private void debug(String message) {
        if (debug) plugin.getLogger().info(message);
    }

    public static class Snapshot {
        protected List<BlockSnapshot> snapshots;

        protected Snapshot() {
            snapshots = new ArrayList<>();
        }

        protected void add(BlockSnapshot blockData) {
            snapshots.add(blockData);
        }
    }

    protected static class BlockSnapshot {
        private final Location location;
        private final BlockData blockData;
        private final Object blockDataNMS;
        private final Chunk chunk;

        protected BlockSnapshot(Location location, BlockData blockData, Object blockDataNMS, Chunk chunk) {
            this.location = location;
            this.blockData = blockData;
            this.blockDataNMS = blockDataNMS;
            this.chunk = chunk;
        }
    }
}