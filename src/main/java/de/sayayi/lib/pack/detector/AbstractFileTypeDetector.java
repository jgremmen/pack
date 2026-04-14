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
 * Abstract base class for detecting pack file content types using the
 * {@link FileTypeDetector Java NIO file type detection SPI}.
 * <p>
 * Subclasses provide a specific {@link PackConfig} and base MIME type. This class handles probing files by attempting
 * to read them as pack streams and returning an annotated MIME type that may include version and compression
 * parameters.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see AbstractTikaDetector
 */
public abstract class AbstractFileTypeDetector extends FileTypeDetector
{
  private final PackConfig packConfig;
  private final String mimeType;


  /**
   * Creates a new file type detector with the given pack configuration and base MIME type.
   *
   * @param packConfig  pack configuration used to read and validate the pack stream, not {@code null}
   * @param mimeType    base MIME type to return when a pack file is detected (e.g.
   *                    {@code "application/x-mypack"}), not {@code null}
   */
  protected AbstractFileTypeDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    this.packConfig = packConfig;
    this.mimeType = mimeType;
  }


  /**
   * Probes the given file path to determine whether it is a valid pack file.
   * <p>
   * If the file can be successfully read as a pack stream, an annotated MIME type string is returned. Otherwise,
   * {@code null} is returned to indicate that this detector does not recognize the file.
   *
   * @param path  the path to the file to probe
   *
   * @return  the annotated MIME type string if the file is a recognized pack file, or {@code null} otherwise
   */
  @Override
  public String probeContentType(Path path)
  {
    try(var packStream = new PackInputStream(packConfig, newInputStream(path))) {
      return buildAnnotatedMimeType(packStream.getVersion(), packStream.isCompressed());
    } catch(Exception ignored) {
    }

    return null;
  }


  /**
   * Builds an annotated MIME type string from the base MIME type, optionally appending {@code version} and
   * {@code compress} parameters.
   *
   * @param version     the pack version, or empty if no version information is available
   * @param compressed  {@code true} or {@code false} if compression status is known, or {@code null} if not applicable
   *
   * @return  the annotated MIME type string, never {@code null}
   */
  @Contract(pure = true)
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  protected @NotNull String buildAnnotatedMimeType(@NotNull OptionalInt version, Boolean compressed)
  {
    final var m = new StringBuilder(mimeType);

    if (version.isPresent())
      m.append(";version=").append(version.getAsInt());

    if (compressed != null)
      m.append(";compress=").append(compressed);

    return m.toString();
  }
}
