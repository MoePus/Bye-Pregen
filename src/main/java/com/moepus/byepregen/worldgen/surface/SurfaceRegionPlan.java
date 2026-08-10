package com.moepus.byepregen.worldgen.surface;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

final class SurfaceRegionPlan {
    private static final int MAX_REGIONS = 8;
    private static final int MIN_WHOLE_REGION_BYTES = 300;
    private static final int MIN_WHOLE_REGION_OCCURRENCES = 8;
    private static final int MAX_WHOLE_REGION_OCCURRENCES = 48;
    private static final int MIN_PARTITION_CHILDREN = 4;
    private static final int PARTITION_THRESHOLD_BYTES = 1_350;
    private static final int GROUP_TARGET_WEIGHT = 1_100;
    private static final int BRANCH_WEIGHT = 6;

    private final SurfaceScalarTarget target;
    private final IdentityHashMap<SurfaceRulePlan.Rule, Estimate> estimates =
            new IdentityHashMap<>();
    private final IdentityHashMap<SurfaceRulePlan.Rule, String> outlinedRules =
            new IdentityHashMap<>();
    private final IdentityHashMap<SurfaceRulePlan.Rule, String> rulePaths =
            new IdentityHashMap<>();
    private final IdentityHashMap<SurfaceRulePlan.Sequence, String> sequencePaths =
            new IdentityHashMap<>();
    private final IdentityHashMap<SurfaceRulePlan.Sequence, List<SequenceRange>> sequenceRanges =
            new IdentityHashMap<>();
    private final List<Region> regions = new ArrayList<>();

    private SurfaceRegionPlan(SurfaceRulePlan.Rule root, SurfaceScalarTarget target) {
        this.target = target;
        this.indexPaths(root, "$root");
        this.estimate(root);
        this.planRoot(root);
    }

    static SurfaceRegionPlan create(
            SurfaceRulePlan.Rule root,
            SurfaceScalarTarget target
    ) {
        return new SurfaceRegionPlan(root, target);
    }

    String outlinedMethod(SurfaceRulePlan.Rule rule) {
        return this.outlinedRules.get(rule);
    }

    SequenceRange rangeStartingAt(SurfaceRulePlan.Sequence sequence, int index) {
        List<SequenceRange> ranges = this.sequenceRanges.get(sequence);
        if (ranges == null) {
            return null;
        }
        for (SequenceRange range : ranges) {
            if (range.fromIndex() == index) {
                return range;
            }
        }
        return null;
    }

    List<Region> regions() {
        return List.copyOf(this.regions);
    }

    String describe() {
        List<String> descriptions = new ArrayList<>(this.regions.size());
        for (Region region : this.regions) {
            String body = switch (region.body()) {
                case RuleBody rule -> this.rulePaths.getOrDefault(rule.rule(), "<unknown>");
                case SequenceRange range -> this.sequencePaths.getOrDefault(
                        range.sequence(), "<unknown>"
                ) + "[" + range.fromIndex() + "," + range.toIndex() + ")";
            };
            descriptions.add(region.methodName() + "=" + body
                    + ":" + region.estimate().bytes()
                    + "/" + region.estimate().branches());
        }
        return String.join(", ", descriptions);
    }

    private void indexPaths(SurfaceRulePlan.Rule rule, String path) {
        this.rulePaths.put(rule, path);
        if (rule instanceof SurfaceRulePlan.Sequence sequence) {
            this.sequencePaths.put(sequence, path + ".sequence");
            for (int index = 0; index < sequence.rules().size(); index++) {
                this.indexPaths(sequence.rules().get(index), path + ".sequence[" + index + "]");
            }
        } else if (rule instanceof SurfaceRulePlan.Test test) {
            this.indexPaths(test.followup(), path + ".thenRun");
        }
    }

    private void planRoot(SurfaceRulePlan.Rule root) {
        if (!(root instanceof SurfaceRulePlan.Sequence sequence)) {
            return;
        }
        SurfaceRulePlan.Rule primary = this.largestComplexChild(sequence.rules());
        if (primary == null || this.estimate(primary).bytes() < PARTITION_THRESHOLD_BYTES) {
            return;
        }
        this.outlineRule(primary);
        SurfaceRulePlan.Sequence control = firstBranchSequence(primary);
        if (control == null) {
            return;
        }
        List<SurfaceRulePlan.Sequence> partitions = new ArrayList<>();
        for (SurfaceRulePlan.Rule child : control.rules()) {
            Estimate estimate = this.estimate(child);
            if (estimate.bytes() < MIN_WHOLE_REGION_BYTES) {
                continue;
            }
            int occurrences = countOccurrences(child);
            if (occurrences >= MIN_WHOLE_REGION_OCCURRENCES
                    && occurrences <= MAX_WHOLE_REGION_OCCURRENCES) {
                this.outlineRule(child);
            } else if (occurrences > MAX_WHOLE_REGION_OCCURRENCES) {
                SurfaceRulePlan.Sequence partition = this.widestPartitionSequence(child);
                if (partition != null) {
                    partitions.add(partition);
                } else {
                    this.outlineRule(child);
                }
            }
        }
        this.partitionAll(partitions);
    }

    private void partitionAll(List<SurfaceRulePlan.Sequence> sequences) {
        int remaining = MAX_REGIONS - this.regions.size();
        if (sequences.isEmpty() || remaining < sequences.size() * 2) {
            return;
        }
        int[] groups = new int[sequences.size()];
        int[] weights = new int[sequences.size()];
        int totalGroups = 0;
        for (int index = 0; index < sequences.size(); index++) {
            SurfaceRulePlan.Sequence sequence = sequences.get(index);
            weights[index] = sequence.rules().stream()
                    .mapToInt(child -> weight(this.estimate(child)))
                    .sum();
            groups[index] = Math.max(2, divideRoundUp(weights[index], GROUP_TARGET_WEIGHT));
            groups[index] = Math.min(groups[index], sequence.rules().size());
            totalGroups += groups[index];
        }
        while (totalGroups > remaining) {
            int reducible = cheapestGroupReduction(groups, weights);
            if (reducible < 0) {
                return;
            }
            groups[reducible]--;
            totalGroups--;
        }
        for (int index = 0; index < sequences.size(); index++) {
            this.partition(sequences.get(index), groups[index]);
        }
    }

    private void partition(SurfaceRulePlan.Sequence sequence, int groups) {
        List<SurfaceRulePlan.Rule> children = sequence.rules();
        int[] boundaries = this.balancedBoundaries(children, groups);
        List<SequenceRange> ranges = new ArrayList<>(groups);
        for (int group = 0; group < groups; group++) {
            int from = boundaries[group];
            int to = boundaries[group + 1];
            SequenceRange range = new SequenceRange(
                    this.nextMethodName(),
                    sequence,
                    from,
                    to,
                    this.estimate(children, from, to)
            );
            ranges.add(range);
            this.regions.add(new Region(range.methodName(), range, range.estimate()));
        }
        this.sequenceRanges.put(sequence, List.copyOf(ranges));
    }

    private int[] balancedBoundaries(List<SurfaceRulePlan.Rule> children, int groups) {
        int count = children.size();
        int[][] best = new int[groups + 1][count + 1];
        int[][] previous = new int[groups + 1][count + 1];
        for (int group = 0; group <= groups; group++) {
            java.util.Arrays.fill(best[group], Integer.MAX_VALUE);
            java.util.Arrays.fill(previous[group], -1);
        }
        best[0][0] = 0;
        int[] prefix = new int[count + 1];
        for (int index = 0; index < count; index++) {
            prefix[index + 1] = prefix[index] + weight(this.estimate(children.get(index)));
        }
        for (int group = 1; group <= groups; group++) {
            for (int end = group; end <= count; end++) {
                for (int start = group - 1; start < end; start++) {
                    if (best[group - 1][start] == Integer.MAX_VALUE) {
                        continue;
                    }
                    int candidate = Math.max(
                            best[group - 1][start],
                            prefix[end] - prefix[start]
                    );
                    if (candidate < best[group][end]) {
                        best[group][end] = candidate;
                        previous[group][end] = start;
                    }
                }
            }
        }
        int[] result = new int[groups + 1];
        result[groups] = count;
        for (int group = groups; group > 0; group--) {
            result[group - 1] = previous[group][result[group]];
        }
        return result;
    }

    private void outlineRule(SurfaceRulePlan.Rule rule) {
        if (this.regions.size() >= MAX_REGIONS || this.outlinedRules.containsKey(rule)) {
            return;
        }
        String method = this.nextMethodName();
        RuleBody body = new RuleBody(rule);
        this.outlinedRules.put(rule, method);
        this.regions.add(new Region(method, body, this.estimate(rule)));
    }

    private String nextMethodName() {
        return "region$" + this.regions.size();
    }

    private Estimate estimate(List<SurfaceRulePlan.Rule> rules, int from, int to) {
        int bytes = 4;
        int branches = 0;
        for (int index = from; index < to; index++) {
            Estimate child = this.estimate(rules.get(index));
            bytes += child.bytes() + 5;
            branches += child.branches() + 1;
        }
        return new Estimate(bytes, branches);
    }

    private Estimate estimate(SurfaceRulePlan.Rule rule) {
        Estimate cached = this.estimates.get(rule);
        if (cached != null) {
            return cached;
        }
        Estimate result;
        if (rule instanceof SurfaceRulePlan.State) {
            result = new Estimate(6, 0);
        } else if (rule instanceof SurfaceRulePlan.Bandlands
                || rule instanceof SurfaceRulePlan.OpaqueRule) {
            result = new Estimate(14, 0);
        } else if (rule instanceof SurfaceRulePlan.Test test) {
            Estimate followup = this.estimate(test.followup());
            result = new Estimate(
                    this.conditionBytes(test.condition()) + followup.bytes() + 4,
                    conditionBranches(test.condition()) + followup.branches() + 1
            );
        } else {
            SurfaceRulePlan.Sequence sequence = (SurfaceRulePlan.Sequence) rule;
            result = this.estimate(sequence.rules(), 0, sequence.rules().size());
        }
        this.estimates.put(rule, result);
        return result;
    }

    private int conditionBytes(SurfaceRulePlan.Condition condition) {
        if (condition instanceof SurfaceRulePlan.NotCondition not) {
            return this.conditionBytes(not.target());
        }
        SurfaceConditionSpec spec = condition.value().spec();
        return switch (spec) {
            case SurfaceConditionSpec.Biome ignored -> 14;
            case SurfaceConditionSpec.Noise ignored ->
                    this.target == SurfaceScalarTarget.BUILD_POINT ? 72 : 36;
            case SurfaceConditionSpec.StoneDepth stone -> stone.secondaryDepthRange() == 0 ? 20 : 44;
            case SurfaceConditionSpec.VerticalGradient ignored -> 58;
            case SurfaceConditionSpec.Water ignored -> 26;
            case SurfaceConditionSpec.YAbove ignored -> 26;
            case SurfaceConditionSpec.Singleton ignored -> 12;
            case SurfaceConditionSpec.Opaque ignored -> 12;
            case SurfaceConditionSpec.Negated ignored -> 4;
        };
    }

    private static int conditionBranches(SurfaceRulePlan.Condition condition) {
        if (condition instanceof SurfaceRulePlan.NotCondition not) {
            return conditionBranches(not.target());
        }
        return condition.value().spec() instanceof SurfaceConditionSpec.VerticalGradient ? 3 : 1;
    }

    private SurfaceRulePlan.Rule largestComplexChild(List<SurfaceRulePlan.Rule> children) {
        SurfaceRulePlan.Rule result = null;
        int largest = -1;
        for (SurfaceRulePlan.Rule child : children) {
            if (!(child instanceof SurfaceRulePlan.Sequence || child instanceof SurfaceRulePlan.Test)) {
                continue;
            }
            int bytes = this.estimate(child).bytes();
            if (bytes > largest) {
                result = child;
                largest = bytes;
            }
        }
        return result;
    }

    private static SurfaceRulePlan.Sequence firstBranchSequence(SurfaceRulePlan.Rule rule) {
        if (rule instanceof SurfaceRulePlan.Sequence sequence) {
            if (sequence.rules().size() > 1) {
                return sequence;
            }
            return sequence.rules().isEmpty()
                    ? null
                    : firstBranchSequence(sequence.rules().get(0));
        }
        return rule instanceof SurfaceRulePlan.Test test
                ? firstBranchSequence(test.followup())
                : null;
    }

    private SurfaceRulePlan.Sequence widestPartitionSequence(SurfaceRulePlan.Rule rule) {
        SurfaceRulePlan.Sequence widest = null;
        if (rule instanceof SurfaceRulePlan.Sequence sequence) {
            if (sequence.rules().size() >= MIN_PARTITION_CHILDREN) {
                widest = sequence;
            }
            for (SurfaceRulePlan.Rule child : sequence.rules()) {
                SurfaceRulePlan.Sequence candidate = this.widestPartitionSequence(child);
                if (isWider(candidate, widest)) {
                    widest = candidate;
                }
            }
        } else if (rule instanceof SurfaceRulePlan.Test test) {
            widest = this.widestPartitionSequence(test.followup());
        }
        return widest;
    }

    private static boolean isWider(
            SurfaceRulePlan.Sequence candidate,
            SurfaceRulePlan.Sequence current
    ) {
        return candidate != null && (current == null
                || candidate.rules().size() > current.rules().size());
    }

    private static int countOccurrences(SurfaceRulePlan.Rule rule) {
        if (rule instanceof SurfaceRulePlan.Sequence sequence) {
            return 1 + sequence.rules().stream()
                    .mapToInt(SurfaceRegionPlan::countOccurrences)
                    .sum();
        }
        return rule instanceof SurfaceRulePlan.Test test
                ? 2 + countOccurrences(test.followup())
                : 1;
    }

    private static int cheapestGroupReduction(int[] groups, int[] weights) {
        int result = -1;
        for (int index = 0; index < groups.length; index++) {
            if (groups[index] <= 2) {
                continue;
            }
            if (result < 0 || reductionPenalty(weights[index], groups[index])
                    < reductionPenalty(weights[result], groups[result])) {
                result = index;
            }
        }
        return result;
    }

    private static int reductionPenalty(int weight, int groups) {
        return divideRoundUp(weight, groups - 1) - divideRoundUp(weight, groups);
    }

    private static int weight(Estimate estimate) {
        return estimate.bytes() + estimate.branches() * BRANCH_WEIGHT;
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    sealed interface Body permits RuleBody, SequenceRange {
    }

    record RuleBody(SurfaceRulePlan.Rule rule) implements Body {
    }

    record SequenceRange(
            String methodName,
            SurfaceRulePlan.Sequence sequence,
            int fromIndex,
            int toIndex,
            Estimate estimate
    ) implements Body {
        SequenceRange {
            if (fromIndex < 0 || toIndex <= fromIndex || toIndex > sequence.rules().size()) {
                throw new IllegalArgumentException("Invalid surface sequence range");
            }
        }
    }

    record Estimate(int bytes, int branches) {
    }

    record Region(String methodName, Body body, Estimate estimate) {
    }
}
