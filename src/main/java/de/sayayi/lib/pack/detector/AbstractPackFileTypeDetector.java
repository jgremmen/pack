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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.OptionalInt;

import static java.nio.file.Files.newInputStream;


/**
 * File type detector for packs.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public abstract class AbstractPackFileTypeDetector extends FileTypeDetector
{
  private final PackConfig packConfig;
  private final String mimeType;


  protected AbstractPackFileTypeDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    this.packConfig = packConfig;
    this.mimeType = mimeType;
  }


  @Override
  public String probeContentType(Path path)
  {
    try(var packStream = new PackInputStream(packConfig, newInputStream(path))) {
      return buildAnnotatedMimeType(packStream.getVersion(), packStream.isCompressed());
    } catch(Exception ignored) {
    }

    return null;
  }


  @Contract(pure = true)
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  protected @NotNull String buildAnnotatedMimeType(@NotNull OptionalInt version, Boolean compressed)
  {
    var m = new StringBuilder(mimeType);

    if (version.isPresent())
      m.append(";version=").append(version.getAsInt());

    if (compressed != null)
      m.append(";compress=").append(compressed);

    return m.toString();
  }
}
