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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Pack/unpack various types to/from stream")
class PackStreamTest
{
  private static final PackConfig PACK_CONFIG = new PackConfig.Builder()
      .noVersion()
      .withCompressionSupport(true)
      .withMagic("MyTeSt\u0007**")
      .build();

  private static final Random RANDOM = new Random();


  @Test
  @DisplayName("Pack/unpack a mixture of types")
  void packMixed() throws IOException
  {
    var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      packStream.writeSmall(5, 3);
      packStream.writeBoolean(true);
      packStream.writeEnum(ElementType.LOCAL_VARIABLE);
      packStream.writeUnsignedShort(11234);
      packStream.writeString(null);
      packStream.writeSmallVar(13);
      packStream.writeString("Schön ist es hier ÄÖß§");
      packStream.writeEnum(RetentionPolicy.CLASS);
      packStream.writeLong(Long.MIN_VALUE);
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      assertEquals(5, packStream.readSmall(3));
      assertTrue(packStream.readBoolean());
      assertEquals(ElementType.LOCAL_VARIABLE, packStream.readEnum(ElementType.class));
      assertEquals(11234, packStream.readUnsignedShort());
      assertNull(packStream.readString());
      assertEquals(13, packStream.readSmallVar());
      assertEquals("Schön ist es hier ÄÖß§", packStream.readString());
      assertEquals(RetentionPolicy.CLASS, packStream.readEnum(RetentionPolicy.class));
      assertEquals(Long.MIN_VALUE, packStream.readLong());
    }
  }


  @Test
  @DisplayName("Pack/unpack small value 0..255 with variable bit width")
  void packSmallVar() throws IOException
  {
    packUnpack(
        () -> RANDOM.nextInt(256),
        PackOutputStream::writeSmallVar,
        PackInputStream::readSmallVar);
  }


  @Test
  @DisplayName("Pack/unpack small value 0..255 with fixed bit widths")
  void packSmall() throws IOException
  {
    for(var bitWidth = 1; bitWidth <= 8; bitWidth++)
    {
      final var bits = bitWidth;

      packUnpack(
          () -> RANDOM.nextInt(1 << bits),
          (packStream, number) -> packStream.writeSmall(number, bits),
          packStream -> packStream.readSmall(bits));
    }
  }


  @Test
  @DisplayName("Pack/unpack large values with fixed bit widths")
  void packLarge() throws IOException
  {
    for(var bitWidth = 9; bitWidth <= 64; bitWidth++)
    {
      final var bits = bitWidth;
      final var mask = bits == 64 ? -1L : ((1L << bits) - 1);

      packUnpack(
          () -> RANDOM.nextLong() & mask,
          (packStream, number) -> packStream.writeLarge(number, bits),
          packStream -> packStream.readLarge(bits));
    }
  }


  @Test
  @DisplayName("Pack/unpack booleans")
  void packBoolean() throws IOException
  {
    packUnpack(
        RANDOM::nextBoolean,
        PackOutputStream::writeBoolean,
        PackInputStream::readBoolean);
  }


  @Test
  @DisplayName("Pack/unpack int values")
  void packInt() throws IOException
  {
    packUnpack(
        RANDOM::nextInt,
        (packStream,number) -> {
          packStream.writeBoolean(true);
          packStream.writeInt(number);
        },
        packStream -> {
          assertTrue(packStream.readBoolean());
          return packStream.readInt();
        });
  }


  @Test
  @DisplayName("Pack/unpack long values")
  void packLong() throws IOException
  {
    packUnpack(
        RANDOM::nextLong,
        (packStream,number) -> {
          packStream.writeBoolean(true);
          packStream.writeLong(number);
        },
        packStream -> {
          assertTrue(packStream.readBoolean());
          return packStream.readLong();
        });
  }


  private enum AA { A1, A2 }
  private enum BB { B1, B2, B3, B4, B5, B6 }
  private enum CC {
    C01, C02, C03, C04, C05, C06, C07, C08, C09, C10,
    C11, C12, C13, C14, C15, C16, C17, C18, C19, C20,
    C21 }
  private enum DD {
    D01, D02, D03, D04, D05, D06, D07, D08, D09, D10,
    D11, D12, D13, D14, D15, D16, D17, D18, D19, D20,
    D21, D22, D23, D24, D25, D26, D27, D28, D29, D30,
    D31, D32, D33, D34, D35, D36, D37, D38, D39, D40,
    D41, D42 }
  private enum EE {
    E001, E002, E003, E004, E005, E006, E007, E008, E009, E010,
    E011, E012, E013, E014, E015, E016, E017, E018, E019, E020,
    E021, E022, E023, E024, E025, E026, E027, E028, E029, E030,
    E031, E032, E033, E034, E035, E036, E037, E038, E039, E040,
    E041, E042, E043, E044, E045, E046, E047, E048, E049, E050,
    E051, E052, E053, E054, E055, E056, E057, E058, E059, E060,
    E061, E062, E063, E064, E065, E066, E067, E068, E069, E070,
    E071, E072, E073, E074, E075, E076, E077, E078, E079, E080,
    E081, E082, E083, E084, E085, E086, E087, E088, E089, E090,
    E091, E092, E093, E094, E095, E096, E097, E098, E099, E100,
    E101, E102, E103, E104, E105, E106, E107, E108, E109, E110,
    E111, E112, E113, E114, E115, E116, E117, E118, E119, E120,
    E121, E122, E123, E124, E125, E126, E127, E128, E129, E130,
    E131, E132, E133, E134, E135, E136, E137, E138, E139, E140,
    E141, E142, E143, E144, E145, E146, E147, E148, E149, E150,
    E151, E152, E153, E154, E155, E156, E157, E158, E159, E160,
    E161, E162, E163, E164, E165, E166, E167, E168, E169, E170,
    E171, E172, E173, E174, E175, E176, E177, E178, E179, E180,
    E181, E182, E183, E184, E185, E186, E187, E188, E189, E190,
    E191, E192, E193, E194, E195, E196, E197, E198, E199, E200,
    E201, E202, E203, E204, E205, E206, E207, E208, E209, E210,
    E211, E212, E213, E214, E215, E216, E217, E218, E219, E220,
    E221, E222, E223, E224, E225, E226, E227, E228, E229, E230,
    E231, E232, E233, E234, E235, E236, E237, E238, E239, E240,
    E241, E242, E243, E244, E245, E246, E247, E248, E249, E250,
    E251, E252, E253, E254, E255, E256, E257, E258, E259, E260,
    E261, E262, E263, E264, E265, E266, E267, E268, E269, E270,
    E271, E272, E273, E274, E275, E276, E277, E278, E279, E280,
    E281, E282, E283, E284, E285, E286, E287, E288, E289, E290,
    E291, E292, E293, E294, E295, E296, E297, E298, E299, E300,
  }

  @Test
  @DisplayName("Pack/unpack enums")
  @SuppressWarnings({"unchecked", "rawtypes"})
  void packEnum() throws IOException
  {
    for(var enumClass: List.of(AA.class, BB.class, CC.class, DD.class, EE.class))
    {
      final var enumClass0 = (Class<? extends Enum>)enumClass;
      final var enums = enumClass.getEnumConstants();

      packUnpack(
          () -> enums[RANDOM.nextInt(enums.length)],
          PackOutputStream::writeEnum,
          packStream -> packStream.readEnum(enumClass0));
    }
  }


  @Test
  @DisplayName("Pack/unpack strings")
  void packString() throws IOException
  {
    packUnpack(
        this::generateString,
        PackOutputStream::writeString,
        PackInputStream::readString);
  }


  private String generateString()
  {
    // 3% null values
    if (RANDOM.nextInt(100) < 3)
      return null;

    var length = RANDOM.nextInt(1000);
    var chars = new char[length];

    for(int n = 0; n < length; n++)
      chars[n] = (char)RANDOM.nextInt(65536);

    return new String(chars);
  }


  private <T> void packUnpack(@NotNull Supplier<T> generateTestValue,
                              @NotNull ValueWriter<T> writeValue,
                              @NotNull ValueReader<T> readValue) throws IOException
  {
    var byteStream = new ByteArrayOutputStream();
    var numbers = new ArrayList<T>();

    for(int n = 0; n < 100000; n++)
      numbers.add(generateTestValue.get());

    try(var packStream = new PackOutputStream(PACK_CONFIG, byteStream)) {
      for(var number: numbers)
        writeValue.write(packStream, number);
    }

    var packed = byteStream.toByteArray();

    try(var packStream = new PackInputStream(PACK_CONFIG, new ByteArrayInputStream(packed))) {
      for(var number: numbers)
        assertEquals(number, readValue.read(packStream));
    }
  }




  public interface ValueWriter<T> {
    void write(@NotNull PackOutputStream packStream, T value) throws IOException;
  }




  public interface ValueReader<T> {
    T read(@NotNull PackInputStream packStream) throws IOException;
  }
}
