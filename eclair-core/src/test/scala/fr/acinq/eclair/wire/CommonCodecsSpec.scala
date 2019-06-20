/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wire

import java.net.{Inet4Address, Inet6Address, InetAddress}

import com.google.common.net.InetAddresses
import fr.acinq.bitcoin.Crypto.PrivateKey
import fr.acinq.eclair.{UInt64, randomBytes32}
import fr.acinq.eclair.wire.CommonCodecs._
import org.scalatest.FunSuite
import scodec.bits.{BitVector, HexStringSyntax}

/**
  * Created by t-bast on 20/06/2019.
  */

class CommonCodecsSpec extends FunSuite {

  test("encode/decode with uint64 codec") {
    val expected = Map(
      UInt64(0) -> hex"00 00 00 00 00 00 00 00",
      UInt64(42) -> hex"00 00 00 00 00 00 00 2a",
      UInt64(hex"ffffffffffffffff") -> hex"ff ff ff ff ff ff ff ff"
    ).mapValues(_.toBitVector)

    for ((uint, ref) <- expected) {
      val encoded = uint64ex.encode(uint).require
      assert(ref === encoded)
      val decoded = uint64ex.decode(encoded).require.value
      assert(uint === decoded)
    }
  }

  test("encode/decode with uint64L codec") {
    val expected = Map(
      0L -> hex"00 00 00 00 00 00 00 00",
      42L -> hex"2a 00 00 00 00 00 00 00",
      6211610197754262546L -> hex"12 34 56 78 90 12 34 56"
    ).mapValues(_.toBitVector)

    for ((long, ref) <- expected) {
      val encoded = uint64L.encode(long).require
      assert(ref === encoded)
      val decoded = uint64L.decode(encoded).require.value
      assert(long === decoded)
    }
  }

  test("encode/decode with varint codec") {
    val expected = Map(
      0L -> hex"00",
      42L -> hex"2a",
      550L -> hex"fd 26 02",
      998000L -> hex"fe 70 3a 0f 00",
      6211610197754262546L -> hex"ff 12 34 56 78 90 12 34 56"
    ).mapValues(_.toBitVector)

    for ((long, ref) <- expected) {
      val encoded = varInt.encode(long).require
      assert(ref === encoded)
      val decoded = varInt.decode(encoded).require.value
      assert(long === decoded)
    }
  }

  test("decode invalid varint") {
    val testCases = Seq(
      hex"fd",
      hex"fe 01",
      hex"fe",
      hex"fe 12 34",
      hex"ff",
      hex"ff 12 34 56 78"
    ).map(_.toBitVector)

    for (testCase <- testCases) {
      assert(varInt.decode(testCase).isFailure)
    }
  }

  test("encode/decode with rgb codec") {
    val color = Color(47.toByte, 255.toByte, 142.toByte)
    val bin = rgb.encode(color).require
    assert(bin === hex"2f ff 8e".toBitVector)
    val color2 = rgb.decode(bin).require.value
    assert(color === color2)
  }

  test("encode/decode all kind of IPv6 addresses with ipv6address codec") {
    {
      // IPv4 mapped
      val bin = hex"00000000000000000000ffffae8a0b08".toBitVector
      val ipv6 = Inet6Address.getByAddress(null, bin.toByteArray, null)
      val bin2 = ipv6address.encode(ipv6).require
      assert(bin === bin2)
    }

    {
      // regular IPv6 address
      val ipv6 = InetAddresses.forString("1080:0:0:0:8:800:200C:417A").asInstanceOf[Inet6Address]
      val bin = ipv6address.encode(ipv6).require
      val ipv62 = ipv6address.decode(bin).require.value
      assert(ipv6 === ipv62)
    }
  }

  test("encode/decode with nodeaddress codec") {
    {
      val ipv4addr = InetAddress.getByAddress(Array[Byte](192.toByte, 168.toByte, 1.toByte, 42.toByte)).asInstanceOf[Inet4Address]
      val nodeaddr = IPv4(ipv4addr, 4231)
      val bin = nodeaddress.encode(nodeaddr).require
      assert(bin === hex"01 C0 A8 01 2A 10 87".toBitVector)
      val nodeaddr2 = nodeaddress.decode(bin).require.value
      assert(nodeaddr === nodeaddr2)
    }
    {
      val ipv6addr = InetAddress.getByAddress(hex"2001 0db8 0000 85a3 0000 0000 ac1f 8001".toArray).asInstanceOf[Inet6Address]
      val nodeaddr = IPv6(ipv6addr, 4231)
      val bin = nodeaddress.encode(nodeaddr).require
      assert(bin === hex"02 2001 0db8 0000 85a3 0000 0000 ac1f 8001 1087".toBitVector)
      val nodeaddr2 = nodeaddress.decode(bin).require.value
      assert(nodeaddr === nodeaddr2)
    }
    {
      val nodeaddr = Tor2("z4zif3fy7fe7bpg3", 4231)
      val bin = nodeaddress.encode(nodeaddr).require
      assert(bin === hex"03 cf3282ecb8f949f0bcdb 1087".toBitVector)
      val nodeaddr2 = nodeaddress.decode(bin).require.value
      assert(nodeaddr === nodeaddr2)
    }
    {
      val nodeaddr = Tor3("mrl2d3ilhctt2vw4qzvmz3etzjvpnc6dczliq5chrxetthgbuczuggyd", 4231)
      val bin = nodeaddress.encode(nodeaddr).require
      assert(bin === hex"04 6457a1ed0b38a73d56dc866accec93ca6af68bc316568874478dc9399cc1a0b3431b03 1087".toBitVector)
      val nodeaddr2 = nodeaddress.decode(bin).require.value
      assert(nodeaddr === nodeaddr2)
    }
  }

  test("encode/decode with private key codec") {
    val value = PrivateKey(randomBytes32)
    val wire = privateKey.encode(value).require
    assert(wire.length == 256)
    val value1 = privateKey.decode(wire).require.value
    assert(value1 == value)
  }

  test("encode/decode with public key codec") {
    val value = PrivateKey(randomBytes32).publicKey
    val wire = CommonCodecs.publicKey.encode(value).require
    assert(wire.length == 33 * 8)
    val value1 = CommonCodecs.publicKey.decode(wire).require.value
    assert(value1 == value)
  }

  test("encode/decode with zeropaddedstring codec") {
    val c = zeropaddedstring(32)

    {
      val alias = "IRATEMONK"
      val bin = c.encode(alias).require
      assert(bin === BitVector(alias.getBytes("UTF-8") ++ Array.fill[Byte](32 - alias.length)(0)))
      val alias2 = c.decode(bin).require.value
      assert(alias === alias2)
    }

    {
      val alias = "this-alias-is-exactly-32-B-long."
      val bin = c.encode(alias).require
      assert(bin === BitVector(alias.getBytes("UTF-8") ++ Array.fill[Byte](32 - alias.length)(0)))
      val alias2 = c.decode(bin).require.value
      assert(alias === alias2)
    }

    {
      val alias = "this-alias-is-far-too-long-because-we-are-limited-to-32-bytes"
      assert(c.encode(alias).isFailure)
    }
  }

  test("encode/decode UInt64") {
    val codec = uint64ex
    Seq(
      UInt64(hex"ffffffffffffffff"),
      UInt64(hex"fffffffffffffffe"),
      UInt64(hex"efffffffffffffff"),
      UInt64(hex"effffffffffffffe")
    ).map(value => {
      assert(codec.decode(codec.encode(value).require).require.value === value)
    })
  }

}
