package com.moepus.byepregen.Feature;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Spliterator;

/** HashSet-compatible view backed by fastutil's open-addressed object set. */
public final class FastObjectHashSet<E> extends HashSet<E> {
    private final ObjectOpenHashSet<E> delegate = new ObjectOpenHashSet<>();

    @Override
    public boolean add(E value) {
        return this.delegate.add(value);
    }

    @Override
    public void clear() {
        this.delegate.clear();
    }

    @Override
    public boolean contains(Object value) {
        return this.delegate.contains(value);
    }

    @Override
    public Iterator<E> iterator() {
        return this.delegate.iterator();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean remove(Object value) {
        return this.delegate.remove(value);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public Spliterator<E> spliterator() {
        return this.delegate.spliterator();
    }

    @Override
    public Object[] toArray() {
        return this.delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] target) {
        return this.delegate.toArray(target);
    }
}
