package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SurfaceControlFlowPlan {
    private final SurfaceRulePlan.EntryId rootEntry;
    private final SurfaceRulePlan.EntryId failEntry;
    private final List<Entry> entries;

    private SurfaceControlFlowPlan(
            SurfaceRulePlan.EntryId rootEntry,
            SurfaceRulePlan.EntryId failEntry,
            List<Entry> entries
    ) {
        this.rootEntry = rootEntry;
        this.failEntry = failEntry;
        this.entries = List.copyOf(entries);
    }

    static SurfaceControlFlowPlan build(
            SurfaceRulePlan.Rule root,
            int ruleEntryCount
    ) {
        Builder builder = new Builder(ruleEntryCount);
        builder.connect(root, builder.failEntry);
        builder.put(new Fail(builder.failEntry));
        return builder.finish(root.metadata().entryId());
    }

    public SurfaceRulePlan.EntryId rootEntry() {
        return this.rootEntry;
    }

    public SurfaceRulePlan.EntryId failEntry() {
        return this.failEntry;
    }

    public int entryCount() {
        return this.entries.size();
    }

    public Entry entry(SurfaceRulePlan.EntryId id) {
        Objects.requireNonNull(id, "id");
        if (id.value() >= this.entries.size()) {
            throw new IllegalArgumentException("Unknown surface entry " + id.value());
        }
        return this.entries.get(id.value());
    }

    public List<Entry> entries() {
        return this.entries;
    }

    public sealed interface Entry permits Jump, Branch, Terminal, Delegate, Fail {
        SurfaceRulePlan.EntryId id();
    }

    public record Jump(
            SurfaceRulePlan.EntryId id,
            SurfaceRulePlan.EntryId target,
            SurfaceRulePlan.Sequence source
    ) implements Entry {
    }

    public record Branch(
            SurfaceRulePlan.EntryId id,
            SurfaceRulePlan.Condition condition,
            SurfaceRulePlan.EntryId onTrue,
            SurfaceRulePlan.EntryId onFalse,
            SurfaceRulePlan.Test source
    ) implements Entry {
    }

    public record Terminal(
            SurfaceRulePlan.EntryId id,
            SurfaceRulePlan.Rule source
    ) implements Entry {
    }

    public record Delegate(
            SurfaceRulePlan.EntryId id,
            SurfaceRulePlan.EntryId onNull,
            SurfaceRulePlan.OpaqueRule source
    ) implements Entry {
    }

    public record Fail(SurfaceRulePlan.EntryId id) implements Entry {
    }

    private static final class Builder {
        private final Entry[] entries;
        private final SurfaceRulePlan.EntryId failEntry;

        private Builder(int ruleEntryCount) {
            if (ruleEntryCount < 1) {
                throw new IllegalArgumentException("Surface CFG has no rule entries");
            }
            this.entries = new Entry[ruleEntryCount + 1];
            this.failEntry = new SurfaceRulePlan.EntryId(ruleEntryCount);
        }

        private void connect(
                SurfaceRulePlan.Rule rule,
                SurfaceRulePlan.EntryId continuation
        ) {
            if (rule instanceof SurfaceRulePlan.Sequence sequence) {
                this.connectSequence(sequence, continuation);
            } else if (rule instanceof SurfaceRulePlan.Test test) {
                this.connectTest(test, continuation);
            } else if (rule instanceof SurfaceRulePlan.OpaqueRule opaque) {
                this.put(new Delegate(opaque.metadata().entryId(), continuation, opaque));
            } else {
                this.put(new Terminal(rule.metadata().entryId(), rule));
            }
        }

        private void connectSequence(
                SurfaceRulePlan.Sequence sequence,
                SurfaceRulePlan.EntryId continuation
        ) {
            List<SurfaceRulePlan.Rule> rules = sequence.rules();
            SurfaceRulePlan.EntryId first = rules.isEmpty()
                    ? continuation
                    : rules.get(0).metadata().entryId();
            this.put(new Jump(sequence.metadata().entryId(), first, sequence));
            SurfaceRulePlan.EntryId next = continuation;
            for (int index = rules.size() - 1; index >= 0; index--) {
                SurfaceRulePlan.Rule child = rules.get(index);
                this.connect(child, next);
                next = child.metadata().entryId();
            }
        }

        private void connectTest(
                SurfaceRulePlan.Test test,
                SurfaceRulePlan.EntryId continuation
        ) {
            SurfaceRulePlan.EntryId followup = test.followup().metadata().entryId();
            this.put(new Branch(
                    test.metadata().entryId(),
                    test.condition(),
                    followup,
                    continuation,
                    test
            ));
            this.connect(test.followup(), continuation);
        }

        private void put(Entry entry) {
            int index = entry.id().value();
            if (index >= this.entries.length || this.entries[index] != null) {
                throw new IllegalArgumentException("Duplicate surface CFG entry " + index);
            }
            this.entries[index] = entry;
        }

        private SurfaceControlFlowPlan finish(SurfaceRulePlan.EntryId rootEntry) {
            if (Arrays.stream(this.entries).anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Surface CFG has missing entries");
            }
            return new SurfaceControlFlowPlan(
                    rootEntry,
                    this.failEntry,
                    new ArrayList<>(Arrays.asList(this.entries))
            );
        }
    }
}
