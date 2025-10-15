package me.steinborn.varintshowdown.res;

import io.netty.buffer.ByteBuf;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;

public class BlendedVarIntWriter implements VarIntWriter {

  @Override
  public void write(ByteBuf buf, int value) {
      writeBigEndian(buf, value);
  }

  private static final IntVector vvv = IntVector.fromArray(IntVector.SPECIES_128, new int[]{0x7F, 0x3F80, 0x1FC000, 0xFE00000}, 0);
  // slow. reimplement?
  private static void writeBigEndianSMID(ByteBuf buf, int value) {
      if ((value & (0xFFFFFFFF << 7)) == 0) {
          buf.writeByte(value);
      }
      IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
      vector = vector.lanewise(VectorOperators.AND, vvv);
      if ((value & (0xFFFFFFFF << 14)) == 0) {
          buf.writeShort((vector.lane(0) << 8) | (vector.lane(1) >>> 7) | 0x8000);
      } else {
          if ((value & (0xFFFFFFFF << 21)) == 0) {
              buf.writeMedium((vector.lane(0) << 16) | (vector.lane(1) << 1) | (vector.lane(2) >>> 14) | 0x808000);
          } else {
              if ((value & (0xFFFFFFFF << 28)) == 0) {
                  buf.writeInt((vector.lane(0) << 24) | (vector.lane(1) << 9) | (vector.lane(2) >>> 6) | (vector.lane(3) >>> 21) | 0x80808000);
              } else {
                  buf.writeInt((vector.lane(0) << 24) | (vector.lane(1) << 9) | (vector.lane(2) >>> 6) | (vector.lane(3) >>> 21) | 0x80808080);
                  buf.writeByte(value >>> 28);
              }
          }
      }
  }

    private static void writeBigEndian(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value << 8) | (value >>> 7);
            buf.writeShort(w | 0x8000);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value << 16) | ((value & 0x3F80) << 1) | (value >>> 14);
            buf.writeMedium(w | 0x808000);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value << 24) | ((value & 0x3F80) << 9)
                    | ((value & 0x1FC000) >>> 6) | (value >>> 21);
            buf.writeInt(w | 0x80808000);
        } else {
            int w = (value << 24) | ((value & 0x3F80) << 9)
                    | ((value & 0x1FC000) >>> 6) | ((value >>> 21) & 0x7F);
            buf.writeInt(w | 0x80808080);
            buf.writeByte(value >>> 28);
        }
    }

  private static void writeVarIntUncommon(ByteBuf buf, int value) {
    while (true) {
      if ((value & 0xFFFFFF80) == 0) {
        buf.writeByte(value);
        return;
      }

      buf.writeByte(value & 0x7F | 0x80);
      value >>>= 7;
    }
  }
}
