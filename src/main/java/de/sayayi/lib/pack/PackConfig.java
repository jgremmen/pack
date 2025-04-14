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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.US_ASCII;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public class PackConfig
{
  protected final byte[] magic;
  protected final int lowestVersionNumber;
  protected final int versionBits;
  protected final boolean compressionSupport;


  protected PackConfig(byte @NotNull [] magic, int lowestVersionNumber, int versionBits, boolean compressionSupport)
  {
    this.magic = magic;
    this.lowestVersionNumber = lowestVersionNumber;
    this.versionBits = versionBits;
    this.compressionSupport = compressionSupport;
  }


  @Contract(pure = true)
  public byte @NotNull [] getMagic() {
    return Arrays.copyOf(magic, magic.length);
  }


  @Contract(pure = true)
  public int getLowestVersionNumber() {
    return lowestVersionNumber;
  }


  @Contract(pure = true)
  public int getHighestVersionNumber() {
    return lowestVersionNumber + (int)((1L << versionBits) - 1);
  }


  @Contract(pure = true)
  public @Range(from = 0, to = 31) int getVersionBits() {
    return versionBits;
  }


  @Contract(pure = true)
  public boolean isCompressionSupport() {
    return compressionSupport;
  }




  public static class Builder
  {
    private byte[] magic = new byte[0];
    private int lowestVersionNumber = 0;
    private int versionBits = 0;
    private boolean compressionSupport = false;


    @Contract("-> this")
    public @NotNull Builder noVersion()
    {
      lowestVersionNumber = 0;
      versionBits = 0;

      return this;
    }


    @Contract("_ -> this")
    public @NotNull Builder withMagic(byte @NotNull [] magic)
    {
      this.magic = Objects.requireNonNull(magic, "magic must not be null");
      return this;
    }


    @Contract("_ -> this")
    public @NotNull Builder withMagic(@NotNull String magic) {
      return withMagic(magic.getBytes(US_ASCII));
    }


    @Contract("_, _ -> this")
    public @NotNull Builder withMagic(@NotNull String magic, @NotNull Charset charset) {
      return withMagic(magic.getBytes(charset));
    }


    @Contract("-> this")
    public @NotNull Builder withCompressionSupport() {
      return withCompressionSupport(true);
    }


    @Contract("_ -> this")
    public @NotNull Builder withCompressionSupport(boolean compressionSupport)
    {
      this.compressionSupport = compressionSupport;
      return this;
    }


    @Contract("_, _ -> this")
    public @NotNull Builder withVersionRange(int lowestVersion, int highestVersion)
    {
      if (lowestVersion < 0)
        throw new IllegalArgumentException("lowestVersion must not be negative");
      if (lowestVersion > highestVersion)
        throw new IllegalArgumentException("lowestVersion must not be larger than highestVersion");

      lowestVersionNumber = lowestVersion;

      var v = highestVersion - lowestVersion;
      v |= v >> 1;
      v |= v >> 2;
      v |= v >> 4;
      v |= v >> 8;
      v |= v >> 16;

      this.versionBits = Integer.bitCount(v);

      return this;
    }


    @Contract(value = "-> new", pure = true)
    public @NotNull PackConfig build() {
      return new PackConfig(magic, lowestVersionNumber, versionBits, compressionSupport);
    }
  }
}
