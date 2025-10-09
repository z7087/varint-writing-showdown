package me.steinborn.varintshowdown.res;

import io.netty.buffer.ByteBuf;

public class BlendedLEVarIntWriter implements VarIntWriter {

  @Override
  public void write(ByteBuf buf, int value) {
    if ((value & (0xFFFFFFFF << 7)) == 0) {
      buf.writeByte(value);
      return;
    }
    int a = (value & 0xFF) | ((value << 1) & 0xFF00);
    if ((value & (0xFFFFFFFF << 14)) == 0) {
      buf.writeShortLE(a | 0x80);
    } else {
      a |= (((value << 2) & 0xFF0000));
      if ((value & (0xFFFFFFFF << 21)) == 0) {
        buf.writeMediumLE(a | 0x8080);
      } else {
        a |= (((value << 3) & 0xFF000000));
        if ((value & (0xFFFFFFFF << 28)) == 0) {
          buf.writeIntLE(a | 0x808080);
        } else {
          buf.writeIntLE(a | 0x80808080);
          buf.writeByte(value >>> 28);
        }
      }
    }
  }
}
