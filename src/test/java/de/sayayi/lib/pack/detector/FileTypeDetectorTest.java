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
package de.sayayi.lib.pack.detector;

import com.sun.management.UnixOperatingSystemMXBean;
import de.sayayi.lib.pack.PackConfig;
import de.sayayi.lib.pack.PackOutputStream;
import org.apache.tika.mime.MediaType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.OptionalInt;

import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;
import static java.nio.file.Files.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Tests {@link AbstractFileTypeDetector}.
 * <p>
 * Covers detection of pack files with and without compression support, the annotated MIME type returned, rejection of
 * pack configurations without magic bytes, and probing of non pack files without leaking file handles.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@DisplayName("File type detector")
class FileTypeDetectorTest
{
  private static final PackConfig PACK_CONFIG = new PackConfig.Builder()
      .withVersionRange(1, 100)
      .withCompressionSupport(true)
      .withMagic("MyTeSt\u0007**")
      .build();

  @TempDir
  private Path tempDir;


  @Test
  @DisplayName("Detect")
  void detect() throws IOException
  {
    final var packPath = tempDir.resolve("test.pack");

    try(var packStream = new PackOutputStream(PACK_CONFIG, 12, false, newOutputStream(packPath))) {
      packStream.writeSmall(5, 3);
      packStream.writeBoolean(true);
      packStream.writeEnum(ElementType.LOCAL_VARIABLE);
    }

    final var mediaType = MediaType.parse(probeContentType(packPath));

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("12", mediaType.getParameters().get("version"));
    assertEquals("false", mediaType.getParameters().get("compress"));
  }


  @Test
  @DisplayName("Detect without compression support")
  void detectWithoutCompressionSupport() throws IOException
  {
    final var packConfig = new PackConfig.Builder().withMagic("NoCoMpReSs").withVersionRange(1, 100).build();
    final var packPath = tempDir.resolve("test.pack");

    new PackOutputStream(packConfig, 12, newOutputStream(packPath)).close();

    final var mediaType = MediaType.parse(
        new AbstractFileTypeDetector(packConfig, "application/my-bitpack") {}.probeContentType(packPath));

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("12", mediaType.getParameters().get("version"));
    assertFalse(mediaType.getParameters().containsKey("compress"));
  }


  @Test
  @DisplayName("Reject configuration without magic bytes")
  void rejectConfigWithoutMagic()
  {
    final var packConfig = new PackConfig
        .Builder()
        .withVersionRange(1, 100)
        .withCompressionSupport()
        .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> new AbstractFileTypeDetector(packConfig, "application/my-bitpack") {});
  }


  @Test
  @DisplayName("Detect file with corrupt compressed payload")
  void detectCorruptCompression() throws IOException
  {
    final var packPath = tempDir.resolve("corrupt.pack");

    new PackOutputStream(PACK_CONFIG, 12, true, newOutputStream(packPath)).close();

    // header: magic, compression bit + 5-bit SmallVar version offset 11 (1 byte), followed by the GZIP header
    final var bytes = readAllBytes(packPath);
    final var gzipOffset = PACK_CONFIG.getMagic().length + 1;

    assertEquals((byte)0x1f, bytes[gzipOffset]);
    bytes[gzipOffset] = 0;
    write(packPath, bytes);

    assertNull(new MyFileTypeDetector().probeContentType(packPath));
  }


  @Test
  @DisplayName("Propagate runtime exceptions")
  void propagateRuntimeException() throws IOException
  {
    final var packPath = tempDir.resolve("test.pack");

    new PackOutputStream(PACK_CONFIG, 12, false, newOutputStream(packPath)).close();

    final var detector = new AbstractFileTypeDetector(PACK_CONFIG, "application/my-bitpack") {
      @Override
      protected @NotNull String buildAnnotatedMimeType(@NotNull OptionalInt version, Boolean compressed) {
        throw new IllegalStateException("bug");
      }
    };

    assertEquals("bug", assertThrows(IllegalStateException.class, () -> detector.probeContentType(packPath))
        .getMessage());
  }


  @Test
  @DisplayName("Detect non-pack file without leaking file handles")
  void detectNonPack() throws IOException
  {
    assumeTrue(getOperatingSystemMXBean() instanceof UnixOperatingSystemMXBean,
        "open file descriptor count not available");

    final var os = (UnixOperatingSystemMXBean)getOperatingSystemMXBean();
    final var textPath = tempDir.resolve("test.txt");
    final var detector = new MyFileTypeDetector();

    writeString(textPath, "this is not a pack file");

    final var openFiles = os.getOpenFileDescriptorCount();

    for(var n = 0; n < 100; n++)
      assertNull(detector.probeContentType(textPath));

    assertTrue(os.getOpenFileDescriptorCount() - openFiles < 10);
  }




  public static final class MyFileTypeDetector extends AbstractFileTypeDetector {
    public MyFileTypeDetector() {
      super(PACK_CONFIG, "application/my-bitpack");
    }
  }
}
