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
import de.sayayi.lib.pack.PackInputStream;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.OptionalInt;

import static org.apache.tika.mime.MediaType.OCTET_STREAM;


/**
 * Tika detector for packs.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public abstract class AbstractTikaDetector implements Detector
{
  protected final PackConfig packConfig;
  protected final MediaType mimeType;


  protected AbstractTikaDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    this.packConfig = packConfig;
    this.mimeType = MediaType.parse(mimeType);
  }


  @Override
  public MediaType detect(InputStream input, Metadata metadata) throws IOException
  {
    if (input != null)
    {
      // worst case: magic.length + (1bit (compression) + versionBits + 7) / 8 + 10 bytes (zip header)
      input.mark(packConfig.getMagic().length + 11 + packConfig.getVersionBits() / 8);

      try(var packStream = new PackInputStream(packConfig, input)) {
        return buildAnnotatedMimeType(packStream.getVersion(), packStream.isCompressed());
      } finally {
        input.reset();
      }
    }

    return OCTET_STREAM;
  }


  @Contract(pure = true)
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  protected @NotNull MediaType buildAnnotatedMimeType(@NotNull OptionalInt version, Boolean compressed)
  {
    final var parameters = new HashMap<String,String>();

    if (version.isPresent())
      parameters.put("version", Integer.toString(version.getAsInt()));

    if (compressed != null)
      parameters.put("compress", Boolean.toString(compressed));

    return new MediaType(mimeType, parameters);
  }
}
