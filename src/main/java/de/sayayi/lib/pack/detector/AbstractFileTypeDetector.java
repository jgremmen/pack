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
import static java.util.Objects.requireNonNull;


/**
 * Abstract base class for detecting pack file content types using the
 * {@link FileTypeDetector Java NIO file type detection SPI}.
 * <p>
 * Subclasses provide a specific {@link PackConfig} and base MIME type. This class handles probing files by attempting
 * to read them as pack streams and returning an annotated MIME type that may include version and compression
 * parameters.
 * <p>
 * Detectors hold no mutable state and can be used by multiple threads concurrently.
 *
 * <h2>Usage Example</h2>
 * <p>
 * Create a concrete subclass that supplies your pack configuration and MIME type:
 * <pre>{@code
 * public class MyPackDetector extends AbstractFileTypeDetector
 * {
 *   private static final PackConfig MY_PACK_CONFIG = new PackConfig.Builder()
 *       .withMagic("MYPK")
 *       .withVersionRange(1, 5)
 *       .withCompressionSupport()
 *       .build();
 *
 *   public MyPackDetector() {
 *     super(MY_PACK_CONFIG, "application/x-mypack");
 *   }
 * }
 * }</pre>
 * <p>
 * Register the detector as a service provider by creating a file
 * {@code META-INF/services/java.nio.file.spi.FileTypeDetector} containing the fully qualified class name:
 * <pre>
 * com.example.MyPackDetector
 * </pre>
 * <p>
 * If the application is a named module on the module path, the {@code META-INF/services} file is ignored. Declare the
 * provider in the application's {@code module-info.java} instead:
 * <pre>{@code
 * provides java.nio.file.spi.FileTypeDetector with com.example.MyPackDetector;
 * }</pre>
 * <p>
 * Once registered, {@link java.nio.file.Files#probeContentType(Path)} will automatically use your detector and return
 * an annotated MIME type such as {@code application/x-mypack;version=3;compress=true}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public abstract class AbstractFileTypeDetector extends FileTypeDetector
{
  /**
   * Pack configuration used to read and validate pack stream headers.
   *
   * @since 0.3.0
   */
  protected final PackConfig packConfig;

  /**
   * Base MIME type returned when a pack file is successfully detected.
   *
   * @since 0.3.0
   */
  protected final String mimeType;


  /**
   * Creates a new file type detector with the given pack configuration and base MIME type.
   *
   * @param packConfig  pack configuration used to read and validate the pack stream, not {@code null}. The
   *                    configuration must define magic bytes, as a detector without magic bytes would claim almost any
   *                    file
   * @param mimeType    base MIME type to return when a pack file is detected (e.g. {@code "application/x-mypack"}), not
   *                    {@code null}
   *
   * @throws IllegalArgumentException  if {@code packConfig} does not define magic bytes
   */
  protected AbstractFileTypeDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    if (requireNonNull(packConfig, "packConfig must not be null").getMagic().length == 0)
      throw new IllegalArgumentException("packConfig must define magic bytes for file type detection");

    this.packConfig = packConfig;
    this.mimeType = mimeType;
  }


  /**
   * Probes the given file to determine whether it is a pack file matching the configured format.
   * <p>
   * The file is recognized if its header can be successfully read as a pack stream using the configured
   * {@link PackConfig}. In that case, an annotated MIME type string is returned (see
   * {@link #buildAnnotatedMimeType(OptionalInt, Boolean)}). Otherwise, including when the file cannot be read,
   * {@code null} is returned to indicate that this detector does not recognize the file.
   *
   * @param path  the path to the file to probe
   *
   * @return  the annotated MIME type string if the file is a recognized pack file, or {@code null} otherwise
   */
  @Override
  public String probeContentType(Path path)
  {
    try(var stream = newInputStream(path);
        var packStream = new PackInputStream(packConfig, stream)) {
      return buildAnnotatedMimeType(
          packStream.getVersion(),
          packConfig.isCompressionSupport() ? packStream.isCompressed() : null);
    } catch(Exception ignored) {
    }

    return null;
  }


  /**
   * Builds an annotated MIME type string from the base MIME type, optionally appending {@code version} and
   * {@code compress} parameters.
   * <p>
   * For example, a base MIME type {@code application/x-mypack} with version {@code 3} and compression results in
   * {@code application/x-mypack;version=3;compress=true}. Subclasses may override this method to customize the returned
   * MIME type.
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
