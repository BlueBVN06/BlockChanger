package dev.lrxh.blockChanger.utility;

import java.util.HashMap;
import java.util.Map;

public class ReflectionUtility {
    private static final Map<String, Class<?>> cache = new HashMap<>();

    public static Class<?> getClass(String className) {
        if (cache.containsKey(className)) {
            return cache.get(className);
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to get class", e);
        }

        cache.put(className, clazz);

        return clazz;
    }

}
