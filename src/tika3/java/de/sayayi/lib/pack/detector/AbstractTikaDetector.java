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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static org.apache.tika.mime.MediaType.OCTET_STREAM;


/**
 * Abstract base class for detecting pack file content types using the {@link Detector Apache Tika detection API}.
 * <p>
 * This class is compiled against and intended to be used with Apache Tika 3. New code should extend
 * {@link AbstractTika3Detector} instead, which makes the targeted Tika version explicit. For Apache Tika 4, the
 * separate class {@code AbstractTika4Detector} in the same package is provided, which is compiled against Tika 4 and is
 * binary incompatible with this class.
 * <p>
 * Subclasses provide a specific {@link PackConfig} and base MIME type. This class handles detection by attempting to
 * read the input as a pack stream and returning an annotated {@link MediaType} that may include version and compression
 * parameters.
 * <p>
 * Detectors hold no mutable state and can be used by multiple threads concurrently.
 *
 * <h2>Usage Example</h2>
 * <p>
 * Create a concrete subclass of {@link AbstractTika3Detector} that supplies your pack configuration and MIME type:
 * <pre>{@code
 * public class MyPackTikaDetector extends AbstractTika3Detector
 * {
 *   private static final PackConfig MY_PACK_CONFIG = new PackConfig.Builder()
 *       .withMagic("MYPK")
 *       .withVersionRange(1, 5)
 *       .withCompressionSupport()
 *       .build();
 *
 *   public MyPackTikaDetector() {
 *     super(MY_PACK_CONFIG, "application/x-mypack");
 *   }
 * }
 * }</pre>
 * <p>
 * Register the detector as a service provider by creating a file
 * {@code META-INF/services/org.apache.tika.detect.Detector} containing the fully qualified class name:
 * <pre>
 * com.example.MyPackTikaDetector
 * </pre>
 * <p>
 * Once registered, Tika's detection framework will automatically use your detector and return an annotated
 * {@link MediaType} such as {@code application/x-mypack;version=3;compress=true}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see AbstractFileTypeDetector
 *
 * @deprecated Extend {@link AbstractTika3Detector} instead, which provides the same functionality for Apache Tika 3.
 *             This class will be removed in a future version.
 */
@Deprecated(since = "0.3.1", forRemoval = true)
public abstract class AbstractTikaDetector implements Detector
{
  /** Pack configuration used to read and validate pack stream headers. */
  protected final PackConfig packConfig;

  /** Base MIME type returned when a pack file is successfully detected. */
  protected final MediaType mimeType;


  /**
   * Creates a new Tika detector with the given pack configuration and base MIME type.
   *
   * @param packConfig  pack configuration used to read and validate the pack stream, not {@code null}. The
   *                    configuration must define magic bytes, as a detector without magic bytes would claim almost any
   *                    file
   * @param mimeType    base MIME type to return when a pack file is detected (e.g. {@code "application/x-mypack"}), not
   *                    {@code null}
   *
   * @throws IllegalArgumentException  if {@code packConfig} does not define magic bytes
   */
  protected AbstractTikaDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    if (requireNonNull(packConfig, "packConfig must not be null").getMagic().length == 0)
      throw new IllegalArgumentException("packConfig must define magic bytes for file type detection");

    this.packConfig = packConfig;
    this.mimeType = MediaType.parse(mimeType);
  }


  /**
   * Detects whether the given input stream contains a valid pack file.
   * <p>
   * If the input can be successfully read as a pack stream, an annotated {@link MediaType} is returned (see
   * {@link #buildAnnotatedMimeType(OptionalInt, Boolean)}). Otherwise, {@link MediaType#OCTET_STREAM OCTET_STREAM} is
   * returned as a fallback. The input stream must support {@linkplain InputStream#markSupported() marking}; it is
   * marked and reset so that it can be read again by subsequent detectors, and it is not closed.
   *
   * @param input     the input stream to probe, or {@code null}
   * @param metadata  document metadata (unused)
   *
   * @return  the detected media type, never {@code null}
   *
   * @throws IOException  if an I/O error occurs while resetting the stream
   */
  @Override
  public @NotNull MediaType detect(InputStream input, Metadata metadata) throws IOException
  {
    if (input != null)
    {
      // worst case: magic.length + (1bit (compression) + versionBits + 7) / 8 + 10 bytes (zip header)
      input.mark(packConfig.getMagic().length + 11 + packConfig.getVersionBits() / 8);

      try(var packStream = new PackInputStream(packConfig, new CloseShieldInputStream(input))) {
        return buildAnnotatedMimeType(
            packStream.getVersion(),
            packConfig.isCompressionSupport() ? packStream.isCompressed() : null);
      } catch(Exception ignored) {
      } finally {
        input.reset();
      }
    }

    return OCTET_STREAM;
  }


  /**
   * Builds an annotated {@link MediaType} from the base MIME type, optionally including {@code version} and
   * {@code compress} parameters.
   * <p>
   * Subclasses may override this method to customize the returned media type.
   *
   * @param version     the pack version, or empty if no version information is available
   * @param compressed  {@code true} or {@code false} if compression status is known, or {@code null} if not applicable
   *
   * @return  the annotated media type, never {@code null}
   */
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




  /**
   * Input stream wrapper which ignores {@link #close()}, leaving the wrapped stream open.
   * <p>
   * This prevents the pack stream used for probing from closing the stream passed to
   * {@link #detect(InputStream, Metadata)}, which is owned by the caller.
   *
   * @since 0.3.2
   */
  private static final class CloseShieldInputStream extends FilterInputStream
  {
    /**
     * Creates a new close shield for the given input stream.
     *
     * @param in  the input stream to wrap, not {@code null}
     */
    private CloseShieldInputStream(@NotNull InputStream in) {
      super(in);
    }


    /**
     * Does nothing, so the wrapped input stream stays open.
     */
    @Override
    public void close() {
    }
  }
}
