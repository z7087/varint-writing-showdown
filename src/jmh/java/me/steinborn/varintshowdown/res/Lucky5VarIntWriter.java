package me.steinborn.varintshowdown.res;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

public class Lucky5VarIntWriter implements VarIntWriter {
    private static final int[] VARINT_EXACT_BYTE_LENGTHS = new int[33];

    static {
        for (int i = 0; i <= 32; ++i) {
            VARINT_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
        }
        VARINT_EXACT_BYTE_LENGTHS[32] = 1; // Special case for 0.
    }

    private static int int2VarInt28Bits(int value) {
        IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
        vector = vector.lanewise(VectorOperators.LSHL, IntVector.fromArray(IntVector.SPECIES_128, new int[]{0, 1, 2, 3}, 0));
        vector = vector.lanewise(VectorOperators.AND, IntVector.fromArray(IntVector.SPECIES_128, new int[]{0x7F, 0x7F00, 0x7F0000, 0x7F000000}, 0));
        return vector.reduceLanes(VectorOperators.OR);
    }

    // copy code from writeXXX methods to here to check assembly
    // or just call those methods directly to check speed only
    @Override
    public void write(ByteBuf buf, int value) {
//        writeLittleEndianSecondOne(buf, value);
//        writeLittleEndianSecondOne2(buf, value);
        writeLittleEndianSIMD5(buf, value);
//        writeLittleEndianSIMD2(buf, value);
//        writeLittleEndianSIMD3(buf, value);
//        writeLittleEndianSIMD4(buf, value);
    }

    private static final IntVector vvv = IntVector.fromArray(IntVector.SPECIES_128, new int[]{0x7F, 0x3F80, 0x1FC000, 0xFE00000}, 0);
    // needs avx to support stuff that i dont know
    @Deprecated
    private static void writeLittleEndianSIMD2(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
        vector = vector.lanewise(VectorOperators.AND, vvv);
        int a = vector.lane(0) | (vector.lane(1) << 1);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(a | 0x80);
        } else {
            a |= (vector.lane(2) << 2);
            if ((value & (0xFFFFFFFF << 21)) == 0) {
                buf.writeMediumLE(a | 0x8080);
            } else {
                a |= (vector.lane(3) << 3);
                if ((value & (0xFFFFFFFF << 28)) == 0) {
                    buf.writeIntLE(a | 0x808080);
                } else {
                    buf.writeIntLE(a | 0x80808080);
                    buf.writeByte(value >>> 28);
                }
            }
        }
    }

    private static final IntVector vvv2 = IntVector.fromArray(IntVector.SPECIES_128, new int[]{0, 1, 2, 3}, 0);
    // needs avx2 to support LSHL
    private static void writeLittleEndianSIMD3(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
        vector = vector.lanewise(VectorOperators.AND, vvv);
        vector = vector.lanewise(VectorOperators.LSHL, vvv2);
        int a = vector.reduceLanes(VectorOperators.OR);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(a | 0x80);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            buf.writeMediumLE(a | 0x8080);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            buf.writeIntLE(a | 0x808080);
        } else {
            buf.writeIntLE(a | 0x80808080);
            buf.writeByte(value >>> 28);
        }
    }


    private static final IntVector vvv3 = IntVector.fromArray(IntVector.SPECIES_128, new int[]{1, 2, 4, 8}, 0);
    // LSHL simulation, needs avx only
    private static void writeLittleEndianSIMD4(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
        vector = vector.lanewise(VectorOperators.AND, vvv);
        vector = vector.lanewise(VectorOperators.MUL, vvv3);
        int a = vector.reduceLanes(VectorOperators.OR);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(a | 0x80);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            buf.writeMediumLE(a | 0x8080);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            buf.writeIntLE(a | 0x808080);
        } else {
            buf.writeIntLE(a | 0x80808080);
            buf.writeByte(value >>> 28);
        }
    }

    // fallback to sisd implementation at length==2, currently fastest
    private static void writeLittleEndianSIMD5(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE((value & 0x7F) | ((value & 0x3F80) << 1) | 0x80);
        } else {
            IntVector vector = IntVector.broadcast(IntVector.SPECIES_128, value);
            vector = vector.lanewise(VectorOperators.AND, vvv);
            vector = vector.lanewise(VectorOperators.MUL, vvv3);
            int a = vector.reduceLanes(VectorOperators.OR);
            if ((value & (0xFFFFFFFF << 21)) == 0) {
                buf.writeMediumLE(a | 0x8080);
            } else if ((value & (0xFFFFFFFF << 28)) == 0) {
                buf.writeIntLE(a | 0x808080);
            } else {
                buf.writeIntLE(a | 0x80808080);
                buf.writeByte(value >>> 28);
            }
        }
    }

    // why is table switch so slow?
    @Deprecated
    private static void writeTableSwitch(ByteBuf buf, int value) {
        switch (Integer.numberOfLeadingZeros(value)) {
            case 32, 31, 30, 29, 28, 27, 26, 25: {
                buf.writeByte(value);
                return;
            }
            case 24, 23, 22, 21, 20, 19, 18: {
                int v0;
                v0 = value & 0x7F;
                v0 |= (value << 1) & 0x7F00;
                v0 |= 0x80;
                buf.writeShortLE(v0);
                return;
            }
            case 17, 16, 15, 14, 13, 12, 11: {
                int v0;
                v0 = value & 0x7F;
                v0 |= (value << 1) & 0x7F00;
                v0 |= (value << 2) & 0x7F0000;
                v0 |= 0x8080;
                buf.writeMediumLE(v0);
                return;
            }
            case 10, 9, 8, 7, 6, 5, 4: {
                int v0;
                v0 = value & 0x7F;
                v0 |= (value << 1) & 0x7F00;
                v0 |= (value << 2) & 0x7F0000;
                v0 |= (value << 3) & 0x7F000000;
                v0 |= 0x808080;
                buf.writeIntLE(v0);
                return;
            }
            case 3, 2, 1, 0:{
                int v0;
                v0 = value & 0x7F;
                v0 |= (value << 1) & 0x7F00;
                v0 |= (value << 2) & 0x7F0000;
                v0 |= (value << 3) & 0x7F000000;
                v0 |= 0x80808080;
                buf.writeIntLE(v0);
                buf.writeByte(value >>> 28);
                return;
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    @Deprecated
    // cmov stuff test and failed
    private static void writeRemoveJmp(ByteBuf buf, int value) {
        int index = buf.writerIndex();
        buf.ensureWritable(5);
        int v0;
        {
            v0 = value & 0x7F;
            {
                int v = v0 | 0x80;
                int b = ((value & (0xFFFFFFFF << 7)) != 0) ? 0xFFFFFFFF : 0;
                v0 = (b != 0) ? v : v0;
            }
            v0 |= (value << 1) & 0x7F00;
            {
                int v = v0 | 0x8000;
                int b = ((value & (0xFFFFFFFF << 14)) != 0) ? 0xFFFFFFFF : 0;
                v0 = (b != 0) ? v : v0;
            }
            v0 |= (value << 2) & 0x7F0000;
            {
                int v = v0 | 0x800000;
                int b = ((value & (0xFFFFFFFF << 21)) != 0) ? 0xFFFFFFFF : 0;
                v0 = (b != 0) ? v : v0;
            }
            v0 |= (value << 3) & 0x7F000000;
            {
                int v = v0 | 0x80000000;
                int b = ((value & (0xFFFFFFFF << 28)) != 0) ? 0xFFFFFFFF : 0;
                v0 = (b != 0) ? v : v0;
            }
        }
        buf.setIntLE(index, v0);
        buf.setByte(index + 4, value >>> 28);
        int l = 1;
        {
            int b = ((value & (0xFFFFFFFF << 7)) != 0) ? 0xFFFFFFFF : 0;
            l = (b != 0) ? 2 : l;
        }
        {
            int b = ((value & (0xFFFFFFFF << 14)) != 0) ? 0xFFFFFFFF : 0;
            l = (b != 0) ? 3 : l;
        }
        {
            int b = ((value & (0xFFFFFFFF << 21)) != 0) ? 0xFFFFFFFF : 0;
            l = (b != 0) ? 4 : l;
        }
        {
            int b = ((value & (0xFFFFFFFF << 28)) != 0) ? 0xFFFFFFFF : 0;
            l = (b != 0) ? 5 : l;
        }
        //l = ((value & (0xFFFFFFFF << 7)) != 0) ? 2 : l;
        //l = ((value & (0xFFFFFFFF << 14)) != 0) ? 3 : l;
        //l = ((value & (0xFFFFFFFF << 21)) != 0) ? 4 : l;
        //l = ((value & (0xFFFFFFFF << 28)) != 0) ? 5 : l;
        buf.writerIndex(index + l);
    }

    @Deprecated
    private static void writeLittleEndianSIMD(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        int v4 = int2VarInt28Bits(value);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(v4 | 0x80);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            buf.writeMediumLE(v4 | 0x8080);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            buf.writeIntLE(v4 | 0x808080);
        } else {
            buf.writeIntLE(v4 | 0x80808080);
            buf.writeByte(value >>> 28);
        }
    }


    private static void writeLittleEndianSecondOne2(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        int a = (value & 0x7F) | ((value & 0x3F80) << 1);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(a | 0x80);
        } else {
            a |= ((value & 0x1FC000) << 2);
            if ((value & (0xFFFFFFFF << 21)) == 0) {
                buf.writeMediumLE(a | 0x8080);
            } else {
                a |= ((value & 0xFE00000) << 3);
                if ((value & (0xFFFFFFFF << 28)) == 0) {
                    buf.writeIntLE(a | 0x808080);
                } else {
                    buf.writeIntLE(a | 0x80808080);
                    buf.writeByte(value >>> 28);
                }
            }
        }
    }

    // le-be swap seems to have less cost
    private static void writeLittleEndianSecondOne(ByteBuf buf, int value) {
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

    private static void writeLittleEndianFirstOne(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        int a = (value & 0xFF) | 0x80 | ((value << 1) & 0xFF00);
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            buf.writeShortLE(a);
        } else {
            a |= (((value << 2) & 0xFF0000) | 0x8000);
            if ((value & (0xFFFFFFFF << 21)) == 0) {
                buf.writeMediumLE(a);
            } else {
                a |= (((value << 3) & 0xFF000000) | 0x800000);
                if ((value & (0xFFFFFFFF << 28)) == 0) {
                    buf.writeIntLE(a);
                } else {
                    buf.writeIntLE(a | 0x80000000);
                    buf.writeByte(value >>> 28);
                }
            }
        }
    }

    private static void writeReduceMicroCommandTest(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
            return;
        }
        int a = value << 8;
        if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = a | (value >>> 7) | 0x8000;
            buf.writeShort(w);
        } else {
            a = (a | ((value >>> 7) & 0xFF)) << 8;
            if ((value & (0xFFFFFFFF << 21)) == 0) {
                int w = a | (value >>> 14) | 0x808000;
                buf.writeMedium(w);
            } else {
                a = (a | ((value >>> 14) & 0xFF)) << 8;
                if ((value & (0xFFFFFFFF << 28)) == 0) {
                    int w = a | (value >>> 21) | 0x80808000;
                    buf.writeInt(w);
                } else {
                    int w = a | ((value >>> 21) & 0xFF) | 0x80808080;
                    buf.writeInt(w);
                    buf.writeByte(value >>> 28);
                }
            }
        }
    }

    @Deprecated
    private static void writeTree(ByteBuf buf, int value) {
        if ((value & (0xFFFFFFFF << 21)) == 0) {
            if ((value & ((~(0xFFFFFFFF << 21)) & (0xFFFFFFFF << 14))) == 0) {
                if ((value & ((~(0xFFFFFFFF << 14)) & (0xFFFFFFFF << 7))) == 0) {
                    buf.writeByte(value);
                    return;
                } else {
                    int w = (value & 0x7F) << 8 | (value >>> 7) | 0x8000;
                    buf.writeShort(w);
                    return;
                }
            } else {
                int w = (value & 0x7F) << 16 | ((value >>> 7) & 0x7F) << 8 | (value >>> 14) | 0x808000;
                buf.writeMedium(w);
                return;
            }
        } else {
            if ((value & (0xFFFFFFFF << 28)) == 0) {
                int w = (value & 0x7F) << 24 | (((value >>> 7) & 0x7F) << 16)
                        | ((value >>> 14) & 0x7F) << 8 | (value >>> 21) | 0x80808000;
                buf.writeInt(w);
                return;
            } else {
                int w = (value & 0x7F) << 24 | ((value >>> 7) & 0x7F) << 16 | ((value >>> 14) & 0x7F) << 8
                        | ((value >>> 21) & 0x7F) | 0x80808080;
                buf.writeInt(w);
                buf.writeByte(value >>> 28);
                return;
            }
        }
    }
}
