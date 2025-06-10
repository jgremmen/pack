/*
 * Copyright 2025 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.pack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

import static java.lang.System.currentTimeMillis;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Pack/unpack various types to/from stream")
class PackStreamTest
{
  private static final PackConfig PACK_CONFIG = new PackConfig.Builder()
      .noVersion()
      .withCompressionSupport(true)
      .withMagic("MyTeSt\u0007**")
      .build();

  private static final Random RANDOM = new Random();


  @Test
  @DisplayName("Pack/unpack a mixture of types")
  void packMixed() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      packStream.writeSmall(5, 3);
      packStream.writeBoolean(true);
      packStream.writeEnum(ElementType.LOCAL_VARIABLE);
      packStream.writeUnsignedShort(11234);
      packStream.writeString(null);
      packStream.writeSmallVar(13);
      packStream.writeString("Schön ist es hier ÄÖß§");
      packStream.writeEnum(RetentionPolicy.CLASS);
      packStream.writeLong(Long.MIN_VALUE);
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      assertEquals(5, packStream.readSmall(3));
      assertTrue(packStream.readBoolean());
      assertEquals(ElementType.LOCAL_VARIABLE, packStream.readEnum(ElementType.class));
      assertEquals(11234, packStream.readUnsignedShort());
      assertNull(packStream.readString());
      assertEquals(13, packStream.readSmallVar());
      assertEquals("Schön ist es hier ÄÖß§", packStream.readString());
      assertEquals(RetentionPolicy.CLASS, packStream.readEnum(RetentionPolicy.class));
      assertEquals(Long.MIN_VALUE, packStream.readLong());
    }
  }


  @Test
  @DisplayName("Pack/unpack small value 0..255 with variable bit width")
  void packSmallVar() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();
    var numbers = new int[10000];

    for(int n = 0; n < numbers.length; n++)
      numbers[n] = RANDOM.nextInt(256);

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      for(int number: numbers)
        packStream.writeSmallVar(number);
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(int number: numbers)
        assertEquals(number, packStream.readSmallVar());
    }
  }


  @Test
  @DisplayName("Pack/unpack small value 0..255 with fixed bit widths")
  void packSmall() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      for(int bitWidth = 1; bitWidth <= 8; bitWidth++)
        for(int value = 0; value < (1 << bitWidth); value++)
          packStream.writeSmall(value, bitWidth);
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(int bitWidth = 1; bitWidth <= 8; bitWidth++)
        for(int value = 0; value < (1 << bitWidth); value++)
          assertEquals(value, packStream.readSmall(bitWidth));
    }
  }


  @Test
  @DisplayName("Pack/unpack large values with fixed bit widths")
  void packLarge() throws IOException
  {
    var random = new Random(currentTimeMillis());
    final Map<Integer,long[]> valueMap = Arrays
        .stream(new int[] { 9, 16, 17, 29, 32, 43, 48, 57, 64 })
        .boxed()
        .collect(toMap(identity(), bitWidth -> {
          var values = new long[1000];
          long mask = bitWidth == 64 ? -1L : ((1L << bitWidth) - 1);

          for(int n = 0; n < values.length; n++)
            values[n] = random.nextLong() & mask;

          return values;
        }, (l1,l2) -> l1, TreeMap::new));

    var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      valueMap.forEach((Integer bitWidth, long[] values) -> {
        try {
          for(long value: values)
            packStream.writeLarge(value, bitWidth);
        } catch(IOException ex) {
          fail(ex);
        }
      });
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      valueMap.forEach((Integer bitWidth, long[] values) -> {
        try {
          for(long value: values)
            assertEquals(value, packStream.readLarge(bitWidth));
        } catch(IOException ex) {
          fail(ex);
        }
      });
    }
  }


  @Test
  @DisplayName("Pack/unpack booleans")
  void packBoolean() throws IOException
  {
    var random = new Random(currentTimeMillis());
    var longs = new long[100];
    for(int n = 0; n < longs.length; n++)
      longs[n] = random.nextLong();
    var bits = BitSet.valueOf(longs);

    var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, false, byteStream)) {
      for(int n = 0; n < bits.length(); n++)
        packStream.writeBoolean(bits.get(n));
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(int n = 0; n < bits.length(); n++)
        assertEquals(bits.get(n), packStream.readBoolean());
    }
  }


  @Test
  @DisplayName("Pack/unpack int values")
  void packInt() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();
    var paddingBits = new byte[10000];
    var numbers = new int[10000];

    for(int n = 0; n < numbers.length; n++)
    {
      paddingBits[n] = (byte)(RANDOM.nextInt(7) + 1);
      numbers[n] = RANDOM.nextInt();
    }

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      for(int n = 0; n < numbers.length; n++)
      {
        packStream.writeSmall(0, paddingBits[n]);
        packStream.writeInt(numbers[n]);
      }
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(int n = 0; n < numbers.length; n++)
      {
        assertEquals(0, packStream.readSmall(paddingBits[n]));
        assertEquals(numbers[n], packStream.readInt());
      }
    }
  }


  @Test
  @DisplayName("Pack/unpack long values")
  void packLong() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();
    var paddingBits = new byte[10000];
    var numbers = new long[10000];

    for(int n = 0; n < numbers.length; n++)
    {
      paddingBits[n] = (byte)(RANDOM.nextInt(7) + 1);
      numbers[n] = RANDOM.nextLong();
    }

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      for(int n = 0; n < numbers.length; n++)
      {
        packStream.writeSmall(0, paddingBits[n]);
        packStream.writeLong(numbers[n]);
      }
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(int n = 0; n < numbers.length; n++)
      {
        assertEquals(0, packStream.readSmall(paddingBits[n]));
        assertEquals(numbers[n], packStream.readLong());
      }
    }
  }
}
