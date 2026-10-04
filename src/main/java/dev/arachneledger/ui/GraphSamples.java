package dev.arachneledger.ui;

import dev.arachneledger.ledger.Ledger;

import java.util.ArrayList;
import java.util.List;

/** Bounded graph geometry. Receipt values remain exact; only their screen positions are sampled. */
public final class GraphSamples {
    /** A pixel column retains both its final step value and any brief positive or negative peak. */
    public record Bucket(int column, double last, double minimum, double maximum) {}

    /**
     * Built once for an immutable journal series, rather than walking it on every rendered frame.
     */
    public static final class Index {
        private final List<Ledger.Point> points;
        private final int base;
        private final double[] minima, maxima;

        public Index(List<Ledger.Point> points) {
            this.points = points;
            int size = 1;
            while (size < points.size()) size <<= 1;
            base = size;
            minima = new double[size * 2];
            maxima = new double[size * 2];
            java.util.Arrays.fill(minima, Double.POSITIVE_INFINITY);
            java.util.Arrays.fill(maxima, Double.NEGATIVE_INFINITY);
            for (int i = 0; i < points.size(); i++) {
                minima[base + i] = maxima[base + i] = points.get(i).profit();
            }
            for (int i = base - 1; i > 0; i--) {
                minima[i] = Math.min(minima[i * 2], minima[i * 2 + 1]);
                maxima[i] = Math.max(maxima[i * 2], maxima[i * 2 + 1]);
            }
        }

        public double minimum() {
            return points.isEmpty() ? 0 : Math.min(0, minima[1]);
        }

        public double maximum() {
            return points.isEmpty() ? 0 : Math.max(0, maxima[1]);
        }

        /** At most width + 1 columns, regardless of how many receipts share the viewport. */
        public List<Bucket> buckets(long duration, int width) {
            long span = Math.max(1, duration);
            int columns = Math.max(1, width), first = 0;
            List<Bucket> buckets = new ArrayList<>(Math.min(points.size(), columns + 1));
            for (int column = 0; column <= columns && first < points.size(); column++) {
                int end =
                        column == columns
                                ? points.size()
                                : lowerBound(
                                        (long) Math.ceil((column + 1) * (double) span / columns));
                if (end <= first) continue;
                double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
                for (int left = first + base, right = end + base;
                        left < right;
                        left >>= 1, right >>= 1) {
                    if ((left & 1) != 0) {
                        minimum = Math.min(minimum, minima[left]);
                        maximum = Math.max(maximum, maxima[left++]);
                    }
                    if ((right & 1) != 0) {
                        minimum = Math.min(minimum, minima[--right]);
                        maximum = Math.max(maximum, maxima[right]);
                    }
                }
                buckets.add(new Bucket(column, points.get(end - 1).profit(), minimum, maximum));
                first = end;
            }
            return List.copyOf(buckets);
        }

        private int lowerBound(long elapsed) {
            int low = 0, high = points.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (points.get(middle).elapsed() < elapsed) low = middle + 1;
                else high = middle;
            }
            return low;
        }
    }

    private GraphSamples() {}
}
