package me.steinborn.varintshowdown.states;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.steinborn.varintshowdown.res.BlendedLEVarIntWriter;
import me.steinborn.varintshowdown.res.VarIntWriter;
import org.openjdk.jmh.annotations.*;

@State(Scope.Benchmark)
public class BlendedLEVarintState {

  private ByteBuf buf;
  private VarIntWriter varIntWriter;

  @Setup(Level.Trial)
  public void setup() {
    buf = Unpooled.directBuffer(5);
    varIntWriter = new BlendedLEVarIntWriter();
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    buf.release();
  }

  public void write(int value) {
    varIntWriter.write(buf, value);
    buf.clear();
  }

}
