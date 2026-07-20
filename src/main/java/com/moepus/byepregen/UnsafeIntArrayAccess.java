package com.moepus.byepregen;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

final public class UnsafeIntArrayAccess {
    private static final Unsafe UNSAFE = findUnsafe();
    private static final long INT_ARRAY_BASE = UNSAFE.arrayBaseOffset(int[].class);
    private static final int INT_ARRAY_SHIFT = Integer.numberOfTrailingZeros(UNSAFE.arrayIndexScale(int[].class));
    private static final long BYTE_ARRAY_BASE = UNSAFE.arrayBaseOffset(byte[].class);

    private UnsafeIntArrayAccess() {
    }

    public static int get(int[] array, int index) {
        return UNSAFE.getInt(array, offset(index));
    }

    public static void set(int[] array, int index, int value) {
        UNSAFE.putInt(array, offset(index), value);
    }

    public static void clear(int[] array, int fromIndex, int toIndex) {
        UNSAFE.setMemory(array, offset(fromIndex), (long) (toIndex - fromIndex) << INT_ARRAY_SHIFT, (byte) 0);
    }

    public static byte get(byte[] array, int index) {
        return UNSAFE.getByte(array, BYTE_ARRAY_BASE + index);
    }

    public static void set(byte[] array, int index, byte value) {
        UNSAFE.putByte(array, BYTE_ARRAY_BASE + index, value);
    }

    private static long offset(int index) {
        return INT_ARRAY_BASE + ((long) index << INT_ARRAY_SHIFT);
    }

    private static Unsafe findUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
