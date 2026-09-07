/*
 * Copyright 2026 Jeroen Gremmen
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
import org.jetbrains.annotations.NotNull;


/**
 * Abstract base class for detecting pack file content types using the
 * {@link org.apache.tika.detect.Detector Apache Tika detection API}.
 * <p>
 * This class is compiled against and intended to be used with Apache Tika 3. A separate, source-compatible but
 * binary-incompatible class named {@code AbstractTika4Detector} is provided for use with Apache Tika 4.
 * <p>
 * Subclasses provide a specific {@link PackConfig} and base MIME type. Detection is handled by the deprecated
 * {@link AbstractTikaDetector} superclass, which attempts to read the input as a pack stream and returns an
 * annotated media type that may include version and compression parameters.
 *
 * @author Jeroen Gremmen
 * @since 0.3.1
 *
 * @see AbstractFileTypeDetector
 */
@SuppressWarnings("deprecation")
public abstract class AbstractTika3Detector extends AbstractTikaDetector
{
  /**
   * Creates a new Tika detector with the given pack configuration and base MIME type.
   *
   * @param packConfig  pack configuration used to read and validate the pack stream, not {@code null}
   * @param mimeType    base MIME type to return when a pack file is detected (e.g.
   *                    {@code "application/x-mypack"}), not {@code null}
   */
  protected AbstractTika3Detector(@NotNull PackConfig packConfig, @NotNull String mimeType) {
    super(packConfig, mimeType);
  }
}
