package dev.lrxh.blockChanger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public abstract class CraftWrapper<T> {
    private final Object nms;

    public CraftWrapper(T input) {
        this.nms = apply(input);
    }

    public Class cb(String className) {
        try {
            return Class.forName("org.bukkit.craftbukkit." + className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("CraftBukkit class not found: " + className, e);
        }
    }

    public Class nms(String className) {
        try {
            return Class.forName("net.minecraft." + className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("NMS class not found: " + className, e);
        }
    }

    public Object nms() {
        return nms;
    }

    protected MethodHandle getMethod(Class<?> clazz, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType methodType = MethodType.methodType(returnType, parameterTypes);

            return lookup.findVirtual(clazz, methodName, methodType);

        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to get method handle for " + methodName + " in " + clazz.getName(), e);
        }
    }

    protected Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Field not found: " + fieldName + " in class " + clazz.getName(), e);
        }
    }

    public <T> T getFieldValue(Field field) {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) field.get(null);
            return result;
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field value: " + field.getName(), e);
        }
    }

    protected abstract Object apply(T input);
}
