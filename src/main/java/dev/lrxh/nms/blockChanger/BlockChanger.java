package dev.lrxh.neptune.utils;

import lombok.SneakyThrows;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

public final class BlockChanger {
    private final int MINOR_VERSION;
    private final JavaPlugin plugin;
    private final boolean debug;

    private final HashSet<Chunk> chunks;

    // NMS Classes
    private Class<?> CHUNK;
    private Class<?> CRAFT_CHUNK;
    private Class<?> CRAFT_BLOCK_DATA;
    private Class<?> LEVEL_HEIGHT_ACCESSOR;

    // NMS METHODS
    private Method GET_STATE;
    private Method GET_HANDLE;
    private Method GET_SECTIONS;
    private Method SET_BLOCK_STATE;
    private Method GET_SECTION_INDEX;
    private Method HAS_ONLY_AIR;

    // NMS FIELDS
    private Field CHUNK_STATUS_FULL;
    private Field NON_EMPTY_BLOCK_COUNT;

    public BlockChanger(JavaPlugin instance, boolean debug) {
        plugin = instance;
        MINOR_VERSION = extractMinorVersion();
        chunks = new HashSet<>();
        this.debug = debug;

        init();
    }

    @SneakyThrows
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

        Class<?> i_BLOCK_DATA;

        if (MINOR_VERSION != 16) {
            i_BLOCK_DATA = loadClass(NET_MINECRAFT + "world.level.block.state.IBlockData");
        } else {
            i_BLOCK_DATA = loadClass(NET_MINECRAFT + "IBlockData");
        }
        debug("I_BLOCK_DATA Loaded");

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

        CRAFT_CHUNK = loadClass(CRAFT_BUKKIT + "CraftChunk");
        debug("CRAFT_CHUNK Loaded");

        Class<?> i_CHUNK_ACCESS;

        if (MINOR_VERSION != 16) {
            i_CHUNK_ACCESS = loadClass(NET_MINECRAFT + "world.level.chunk.IChunkAccess");
        } else {
            i_CHUNK_ACCESS = loadClass(NET_MINECRAFT + "IChunkAccess");
        }
        debug("I_CHUNK_ACCESS Loaded");

        CRAFT_BLOCK_DATA = loadClass(CRAFT_BUKKIT + "block.data.CraftBlockData");
        debug("CRAFT_BLOCK_DATA Loaded");

        Class<?> CHUNK_STATUS;
        if (MINOR_VERSION != 16) {
            if (supports(21)) {
                CHUNK_STATUS = loadClass(NET_MINECRAFT + "world.level.chunk.status.ChunkStatus");
            } else {
                CHUNK_STATUS = loadClass(NET_MINECRAFT + "world.level.chunk.ChunkStatus");
            }
        } else {
            CHUNK_STATUS = loadClass(NET_MINECRAFT + "ChunkStatus");
        }
        debug("CHUNK_STATUS Loaded");

        GET_STATE = getDeclaredMethod(CRAFT_BLOCK_DATA, "getState");
        debug("GET_STATE Loaded");

        if (MINOR_VERSION != 16) {
            GET_HANDLE = getDeclaredMethod(CRAFT_CHUNK, "getHandle", CHUNK_STATUS);
        } else {
            GET_HANDLE = getDeclaredMethod(CRAFT_CHUNK, "getHandle");
        }
        debug("GET_HANDLE Loaded");

        if (supports(21) || MINOR_VERSION == 16) {
            GET_SECTIONS = getDeclaredMethod(i_CHUNK_ACCESS, "getSections");
        } else {
            GET_SECTIONS = getDeclaredMethod(i_CHUNK_ACCESS, "d");
        }
        debug("GET_SECTIONS Loaded");

        if (MINOR_VERSION != 16) {
            if (supports(21)) {
                SET_BLOCK_STATE = getDeclaredMethod(CHUNK_SECTION, "setBlockState", int.class, int.class, int.class, i_BLOCK_DATA);
            } else {
                SET_BLOCK_STATE = getDeclaredMethod(CHUNK_SECTION, "a", int.class, int.class, int.class, i_BLOCK_DATA);
            }
        } else {
            SET_BLOCK_STATE = getDeclaredMethod(CHUNK_SECTION, "setType", int.class, int.class, int.class, i_BLOCK_DATA);
        }
        debug("SET_BLOCK_STATE Loaded");

        if (supports(21)) {
            CHUNK_STATUS_FULL = getDeclaredField(CHUNK_STATUS, "FULL");
        } else {
            CHUNK_STATUS_FULL = getDeclaredField(CHUNK_STATUS, "n");
        }
        debug("CHUNK_STATUS_FULL Loaded");

        LEVEL_HEIGHT_ACCESSOR = loadClass(NET_MINECRAFT + "world.level.LevelHeightAccessor");
        debug("LEVEL_HEIGHT_ACCESSOR Loaded");

        if (MINOR_VERSION == 21) {
            GET_SECTION_INDEX = getDeclaredMethod(LEVEL_HEIGHT_ACCESSOR, "f", int.class);
        } else if (supports(17)) {
            GET_SECTION_INDEX = getDeclaredMethod(LEVEL_HEIGHT_ACCESSOR, "e", int.class);
        }

        debug("GET_SECTION_INDEX Loaded");

        if(supports(18)) {
            HAS_ONLY_AIR = getDeclaredMethod(CHUNK_SECTION, "c");
        }

        debug("HAS_ONLY_AIR Loaded");

        if (HAS_ONLY_AIR == null) {
            if (MINOR_VERSION == 17) {
                NON_EMPTY_BLOCK_COUNT = getDeclaredField(CHUNK_SECTION, "f");
            } else if (MINOR_VERSION == 16) {
                NON_EMPTY_BLOCK_COUNT = getDeclaredField(CHUNK_SECTION, "c");
            }

            debug("NON_EMPTY_BLOCK_COUNT Loaded");
        }
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

    private void debug(String message) {
        if (debug) plugin.getLogger().info(message);
    }

    @SneakyThrows
    public void setBlock(Location location, BlockData blockData) {
        Object nmsBlockData = getBlockDataNMS(blockData);

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        Object nmsChunk = getChunkNMS(location.getChunk()); // NET.MC.CHUNK

        Object cs; // ORG.BUKKIT.CHUNKSECTION
        if (LEVEL_HEIGHT_ACCESSOR != null) {
            Object LevelHeightAccessor = LEVEL_HEIGHT_ACCESSOR.cast(nmsChunk);

            int i = (int) GET_SECTION_INDEX.invoke(LevelHeightAccessor, y);

            cs = getSections(nmsChunk)[i];
        } else {
            cs = getSections(nmsChunk)[y >> 4];
        }

        if (cs == null) return;

        if (HAS_ONLY_AIR != null) {
            if ((Boolean) HAS_ONLY_AIR.invoke(cs) && blockData.getMaterial().isAir()) return;
        } else {
            if ((Short) NON_EMPTY_BLOCK_COUNT.get(cs) == 0) return;
        }

        Object result = SET_BLOCK_STATE.invoke(cs, x & 15, y & 15, z & 15, nmsBlockData); // ORG.BUKKIT.CHUNKSECTION

        if (result == null) return;

        chunks.add(location.getChunk());
    }

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
                    Block block = min.getWorld().getBlockAt(x, y, z);
                    Location location = new Location(world, x, y, z);
                    snapshot.add(location, block.getBlockData());
                }
            }
        }

        return snapshot;
    }

    public void revert(Snapshot snapshot) {
        for (Map.Entry<Location, BlockData> entry : snapshot.snapshot.entrySet()) {
            setBlock(entry.getKey(), entry.getValue());
        }
        notifyChanges();
    }

    public void notifyChanges() {
        for (Chunk chunk : chunks) {
            chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
        }

        chunks.clear();
    }

    @SneakyThrows
    private Object[] getSections(Object nmsChunk) {
        return (Object[]) GET_SECTIONS.invoke(nmsChunk);
    }

    @SneakyThrows
    private Object getBlockDataNMS(BlockData blockData) {
        return GET_STATE.invoke(CRAFT_BLOCK_DATA.cast(blockData));
    }

    @SneakyThrows
    private Object getChunkNMS(Chunk chunk) {
        if (MINOR_VERSION == 16) {
            Object craftChunk = CRAFT_CHUNK.cast(chunk);

            return GET_HANDLE.invoke(craftChunk);
        }

        Object craftChunk = CRAFT_CHUNK.cast(chunk);
        Object IChunkAccess = GET_HANDLE.invoke(craftChunk, CHUNK_STATUS_FULL.get(null));

        return CHUNK.cast(IChunkAccess);
    }

    private Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    private Field getDeclaredField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private Method getDeclaredMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private boolean supports(int version) {
        return MINOR_VERSION >= version;
    }

    private int extractMinorVersion() {
        String[] versionParts = plugin.getServer().getBukkitVersion().split("-")[0].split("\\.");
        if (versionParts.length >= 2) {

            return Integer.parseInt(versionParts[1]);
        }

        return 0;
    }

    public static class Snapshot {
        protected HashMap<Location, BlockData> snapshot;

        public Snapshot() {
            snapshot = new HashMap<>();
        }

        protected void add(Location location, BlockData blockData) {
            snapshot.put(location, blockData);
        }
    }
}