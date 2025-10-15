package me.steinborn.varintshowdown;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.steinborn.varintshowdown.res.BlendedVarIntWriter;
import me.steinborn.varintshowdown.res.Lucky5VarIntWriter;
import me.steinborn.varintshowdown.states.*;
import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Fork(value = Constants.FORK)
@Warmup(iterations = Constants.WARM_UP_ITERATIONS, time = Constants.WARM_UP_ITERATIONS_TIME)
@Measurement(iterations = Constants.ITERATIONS, time = Constants.ITERATIONS_TIME)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VarIntWriterBenchmark {

    private int[] numbers;

    @Setup
    public void setupNumbers() {
        Random random = new Random(77083993792645L);
        this.numbers = new int[2048];
        for (int i = 0; i < 2048; i++) {
            this.numbers[i] = generateRandomBitNumber(random, random.nextInt(30) + 1);
        }
        // delete this before speed test bc this loads classes and makes virtual methods lookup slow
        check();
    }

    private static int generateRandomBitNumber(Random random, int i) {
        int lowerBound = (1 << (i - 1));
        int upperBound = (1 << i) - 1;
        if (lowerBound == upperBound) {
            return lowerBound;
        }
        return lowerBound + random.nextInt(upperBound - lowerBound);
    }

    private void check() {
        ByteBuf buf = Unpooled.directBuffer(5);
        ByteBuf buf2 = Unpooled.directBuffer(5);
        for (int n : numbers) {
            new Lucky5VarIntWriter().write(buf, n);
            new BlendedVarIntWriter().write(buf2, n);
            if (!buf.equals(buf2)) {
                System.out.println("Mismatch for " + n);
                System.out.println("Lucky5:   " + buf);
                for (int i = 0, l = buf.capacity(); i < l; ++i) {
                    System.out.println("Lucky5[" + i + "]:   " + buf.getByte(i));
                }
                System.out.println("Blended:  " + buf2);
                for (int i = 0, l = buf2.capacity(); i < l; ++i) {
                    System.out.println("Blended[" + i + "]:   " + buf2.getByte(i));
                }
                throw new IllegalArgumentException();
            }
            buf.clear();
            buf2.clear();
        }
        buf.release();
        buf2.release();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void lucky5VarintWrite(Lucky5VarintState state) {
        for (int number : numbers) {
            state.write(number);
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void zblendedVarintWrite(BlendedVarintState state) {
        for (int number : numbers) {
            state.write(number);
        }
    }
}
