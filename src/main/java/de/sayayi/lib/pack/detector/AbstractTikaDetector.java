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
 * Abstract base class for detecting pack file content types using the {@link Detector Apache Tika detection API}.
 * <p>
 * Subclasses provide a specific {@link PackConfig} and base MIME type. This class handles detection by attempting to
 * read the input as a pack stream and returning an annotated {@link MediaType} that may include version and
 * compression parameters.
 *
 * <h2>Usage Example</h2>
 * Create a concrete subclass that supplies your pack configuration and MIME type:
 * <pre>{@code
 * public class MyPackTikaDetector extends AbstractTikaDetector
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
 *
 * Register the detector as a service provider by creating a file
 * {@code META-INF/services/org.apache.tika.detect.Detector} containing the fully qualified class name:
 * <pre>
 * com.example.MyPackTikaDetector
 * </pre>
 *
 * Once registered, Tika's detection framework will automatically use your detector and return an annotated
 * {@link MediaType} such as {@code application/x-mypack;version=3;compress=true}.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see AbstractFileTypeDetector
 */
public abstract class AbstractTikaDetector implements Detector
{
  /** Pack configuration used to read and validate pack stream headers. */
  protected final PackConfig packConfig;

  /** Base MIME type returned when a pack file is successfully detected. */
  protected final MediaType mimeType;


  /**
   * Creates a new Tika detector with the given pack configuration and base MIME type.
   *
   * @param packConfig  pack configuration used to read and validate the pack stream, not {@code null}
   * @param mimeType    base MIME type to return when a pack file is detected (e.g.
   *                    {@code "application/x-mypack"}), not {@code null}
   */
  protected AbstractTikaDetector(@NotNull PackConfig packConfig, @NotNull String mimeType)
  {
    this.packConfig = packConfig;
    this.mimeType = MediaType.parse(mimeType);
  }


  /**
   * Detects whether the given input stream contains a valid pack file.
   * <p>
   * If the input can be successfully read as a pack stream, an annotated {@link MediaType} is returned. Otherwise,
   * {@link MediaType#OCTET_STREAM OCTET_STREAM} is returned as a fallback. The input stream is marked and reset so
   * that it can be re-read by subsequent detectors.
   *
   * @param input     the input stream to probe, or {@code null}
   * @param metadata  document metadata (unused)
   *
   * @return  the detected media type, never {@code null}
   *
   * @throws IOException  if an I/O error occurs while resetting the stream
   */
  @Override
  public MediaType detect(InputStream input, Metadata metadata) throws IOException
  {
    if (input != null)
    {
      // worst case: magic.length + (1bit (compression) + versionBits + 7) / 8 + 10 bytes (zip header)
      input.mark(packConfig.getMagic().length + 11 + packConfig.getVersionBits() / 8);

      try(var packStream = new PackInputStream(packConfig, input)) {
        return buildAnnotatedMimeType(packStream.getVersion(), packStream.isCompressed());
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
}
