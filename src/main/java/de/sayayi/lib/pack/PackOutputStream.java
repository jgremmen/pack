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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.bitCount;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public class PackOutputStream implements Closeable
{
  private @NotNull OutputStream stream;
  private int bit = 7;
  private byte b;


  public PackOutputStream(@NotNull PackConfig packConfig, @Range(from = 0, to = MAX_VALUE) int version,
                          @NotNull OutputStream stream) throws IOException {
    this(packConfig, version, packConfig.isCompressionSupport(), stream);
  }


  public PackOutputStream(@NotNull PackConfig packConfig, @NotNull OutputStream stream) throws IOException {
    this(packConfig, packConfig.getLowestVersionNumber(), packConfig.isCompressionSupport(), stream);
  }


  public PackOutputStream(@NotNull PackConfig packConfig, boolean compress, @NotNull OutputStream stream)
      throws IOException {
    this(packConfig, packConfig.getLowestVersionNumber(), compress, stream);
  }


  public PackOutputStream(@NotNull PackConfig packConfig, @Range(from = 0, to = MAX_VALUE) int version,
                          boolean compress, @NotNull OutputStream stream) throws IOException
  {
    this.stream = stream;

    stream.write(packConfig.getMagic());

    if (packConfig.isCompressionSupport())
      writeBoolean(compress);
    else if (compress)
      throw new IllegalArgumentException("Compression is not supported");

    var versionBits = packConfig.getVersionBits();
    if (versionBits != 0)
    {
      if (version < packConfig.getLowestVersionNumber() || version > packConfig.getHighestVersionNumber())
        throw new IllegalArgumentException("Invalid version number: " + version);

      version -= packConfig.getLowestVersionNumber();

      if (versionBits <= 8)
        writeSmallVar(version);
      else
        writeLarge(version, versionBits);
    }

    if (compress)
    {
      forceByteAlignment();
      stream.flush();

      this.stream = new GZIPOutputStream(stream);
    }
  }


  /**
   * Write a boolean value to the output stream.
   *
   * @param value  boolean value to write
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeBoolean(boolean value) throws IOException
  {
    if (value)
      b |= (byte)(1 << bit);

    if (bit-- == 0)
    {
      stream.write(b);
      bit = 7;
      b = 0;
    }
  }


  /**
   * Write an enumerated value to the output stream.
   *
   * @param value     enumerated value to write, not {@code null}
   * @param bitWidth  number of bits to use for the enumerated value
   *
   * @throws IllegalArgumentException  if {@code bitWidth} is not in the range 1..16 or if the ordinal of
   *                                   the enumerated value exceeds the {@code bitWidth}
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> void writeEnum(@NotNull T value, @Range(from = 1, to = 16) int bitWidth) throws IOException
  {
    //noinspection ConstantValue
    if (bitWidth <= 0 || bitWidth > 16)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);

    var ordinal = value.ordinal();
    if (ordinal >= (1 << bitWidth))
      throw new IllegalArgumentException("Ordinal of enum '" + value.name() + "' exceeds bitWidth");

    if (bitWidth <= 8)
      writeSmall(value.ordinal(), bitWidth);
    else
      writeLarge(value.ordinal(), bitWidth);
  }


  /**
   * Write an enumerated value to the output stream.
   *
   * @param value  enumerated value to write, not {@code null}
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> void writeEnum(@NotNull T value) throws IOException
  {
    var n = value.getClass().getEnumConstants().length;

    writeEnum(value, bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8)));
  }


  /**
   * @param value  unsigned value (0..65535)
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeUnsignedShort(@Range(from = 0, to = 65535) int value) throws IOException {
    writeLarge(value, 16);
  }


  @Contract(mutates = "this,io")
  public void writeInt(int value) throws IOException {
    writeLarge(value, 32);
  }


  @Contract(mutates = "this,io")
  public void writeLong(long value) throws IOException {
    writeLarge(value, 64);
  }


  @Contract(mutates = "this,io")
  public void writeString(String str) throws IOException
  {
    if (str == null)
    {
      writeSmall(0, 2);
      return;
    }

    var utfLength = getUTFLength(str);
    if (utfLength > 0xffff)
      throw new IllegalArgumentException("String too large");

    if (utfLength < 16)
      writeSmall(0b01_0000 | utfLength, 6);
    else if (utfLength < 256)
      writeLarge(0b10_0000_0000 | utfLength, 10);
    else
    {
      writeSmall(0b11, 2);
      writeLarge(utfLength, 16);
    }

    if (utfLength > 0)
    {
      forceByteAlignment();

      final var bytes = new byte[utfLength];

      for(int charIdx = 0, utfIdx = 0, stringLength = str.length(); charIdx < stringLength; charIdx++)
      {
        final var c = str.charAt(charIdx);

        if (c >= 0x0001 && c <= 0x007F)
          bytes[utfIdx++] = (byte)c;
        else if (c > 0x07FF)
        {
          bytes[utfIdx++] = (byte)(0xE0 | ((c >> 12) & 0x0F));
          bytes[utfIdx++] = (byte)(0x80 | ((c >>  6) & 0x3F));
          bytes[utfIdx++] = (byte)(0x80 | ( c        & 0x3F));
        }
        else
        {
          bytes[utfIdx++] = (byte)(0xC0 | ((c >> 6) & 0x1F));
          bytes[utfIdx++] = (byte)(0x80 | ( c       & 0x3F));
        }
      }

      stream.write(bytes);
    }
  }


  @Contract(pure = true)
  protected int getUTFLength(@NotNull String string)
  {
    var utflen = 0;

    for(int i = 0, l = string.length(); i < l; i++)
    {
      int c = string.charAt(i);

      if (c >= 0x0001 && c <= 0x007F)
        utflen++;
      else if (c > 0x07FF)
        utflen += 3;
      else
        utflen += 2;
    }

    return utflen;
  }


  /**
   * Ranges: 0..7 (4 bit), 8..15 (5 bit), 16..255 (10 bit)
   *
   * @param value  unsigned value (0..255)
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeSmallVar(@Range(from = 0, to = 255) int value) throws IOException
  {
    if (value <= 7)
      writeSmall(value, 4);  // 0vvv
    else if (value <= 15)
      writeSmall(0b10_000 | (value - 8), 5);  // 10vvv (vvv + 8)
    else
      writeLarge(0b11_00000000 | value, 10);  // 11vvvvvvvv
  }


  /**
   * @param value     unsigned value (0..255)
   * @param bitWidth  range 1..8 bits
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeSmall(@Range(from = 0, to = 255) int value,
                         @Range(from = 1, to = 8) int bitWidth) throws IOException
  {
    if (value >= (1 << bitWidth))
      throw new IllegalArgumentException("value " + value + " occupies more than " + bitWidth + " bits");

    var bitsRemaining = bit + 1 - bitWidth;

    if (bitsRemaining > 0)
    {
      b |= (byte)((value & ((1 << bitWidth) - 1)) << bitsRemaining);
      bit -= bitWidth;
    }
    else if (bitsRemaining == 0)
    {
      stream.write(b | (value & ((1 << bitWidth) - 1)));
      b = 0;
      bit = 7;
    }
    else  // bitsRemaining < 0
    {
      stream.write(b | (byte)(value >>> -bitsRemaining) & ((1 << (bit + 1)) - 1));
      b = (byte)((value << (8 + bitsRemaining)) & 0xff);
      bit = 7 + bitsRemaining;
    }
  }


  /**
   * @param value     signed value
   * @param bitWidth  range 9..64 bits
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeLarge(long value, @Range(from = 9, to = 64) int bitWidth) throws IOException
  {
    if (bit < 7)
    {
      var bitsLeft = bit + 1 - bitWidth;

      b |= (byte)((byte)(value >>> -bitsLeft) & ((1 << (bit + 1)) - 1));
      stream.write(b);

      bitWidth -= bit + 1;
      b = 0;
      bit = 7;
    }

    while(bitWidth >= 8)
      stream.write((byte)(value >>> (bitWidth -= 8)));

    if (bitWidth > 0)
    {
      b = (byte)((value << (8 - bitWidth)) & 0xff);
      bit = 7 - bitWidth;
    }
  }


  @Contract(mutates = "this,io")
  protected void forceByteAlignment() throws IOException
  {
    if (bit != 7)
    {
      stream.write(b);
      bit = 7;
      b = 0;
    }
  }


  @Override
  public void close() throws IOException
  {
    forceByteAlignment();
    stream.flush();
    stream.close();
  }
}
