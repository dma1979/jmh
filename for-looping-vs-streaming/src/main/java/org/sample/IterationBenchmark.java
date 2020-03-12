package org.sample;

import java.util.stream.IntStream;

import org.openjdk.jmh.annotations.Benchmark;

public class IterationBenchmark {

    private static final int ITERATIONS = 10_000;

    /**
     * A for-loop starting at zero and then counting up to some pre-defined number.
     *
     * @return the sum of the elements
     */
    @Benchmark
    public int forUp() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            sum += i;
        }
        return sum;
    }

    /**
     * A for-loop that starts with a predetermined non-negative value and then it counts down.
     *
     * @return the sum of the elements
     */
    @Benchmark
    public int forDown() {
        int sum = 0;
        for (int i = ITERATIONS; i-- > 0;) {
            sum += i;
        }
        return sum;
    }

    @Benchmark
    public int streaming() {
        return IntStream.range(0, ITERATIONS)
                        .sum();
    }

    @Benchmark
    public int streamingInParallel() {
        return IntStream.range(0, ITERATIONS)
                        .parallel()
                        .sum();
    }

    /**
     * Mathematical approach, we recall that the sum of consecutive numbers starting at zero is N*(N+1)/2 where N is the highest number in the series.
     * @return the sum of the elements
     */
    @Benchmark
    public int math() {
        return ITERATIONS * (ITERATIONS + 1) / 2;
    }

}
