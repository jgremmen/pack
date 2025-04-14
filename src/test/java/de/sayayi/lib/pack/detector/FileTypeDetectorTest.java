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

import de.sayayi.lib.pack.PackConfig;
import de.sayayi.lib.pack.PackOutputStream;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.nio.file.Path;

import static java.nio.file.Files.newOutputStream;
import static java.nio.file.Files.probeContentType;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
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




  public static final class MyFileTypeDetector extends AbstractFileTypeDetector {
    public MyFileTypeDetector() {
      super(PACK_CONFIG, "application/my-bitpack");
    }
  }
}
