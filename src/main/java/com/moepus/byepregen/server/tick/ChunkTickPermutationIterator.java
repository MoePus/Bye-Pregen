package com.moepus.byepregen.server.tick;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class ChunkTickPermutationIterator implements Iterator<Object> {
    private List<?> values = List.of();
    private int multiplier;
    private int increment;
    private int mask;
    private int state;
    private int remaining;

    public void reset(List<?> values, RandomSource random) {
        this.values = values;
        int domain = Mth.smallestEncompassingPowerOfTwo(Math.max(1, values.size()));
        this.multiplier = random.nextInt() & ~3 | 1;
        this.increment = random.nextInt() | 1;
        this.mask = domain - 1;
        this.state = random.nextInt() & this.mask;
        this.remaining = values.size();
    }

    public Iterator<?> iteratorFor(List<?> expectedValues) {
        if (this.values != expectedValues) {
            throw new IllegalStateException("Chunk tick iterator used without matching shuffle preparation");
        }
        return this;
    }

    @Override
    public boolean hasNext() {
        return this.remaining > 0;
    }

    @Override
    public Object next() {
        if (this.remaining == 0) {
            throw new NoSuchElementException();
        }

        int index;
        do {
            this.state = this.state * this.multiplier + this.increment & this.mask;
            index = this.state;
        } while (index >= this.values.size());

        Object value = this.values.get(index);
        if (--this.remaining == 0) {
            this.values = List.of();
        }
        return value;
    }
}
