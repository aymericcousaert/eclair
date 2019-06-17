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

package fr.acinq.eclair.crypto

import fr.acinq.bitcoin.{Block, ByteVector32, DeterministicWallet}
import fr.acinq.bitcoin.Crypto.PublicKey
import fr.acinq.bitcoin.DeterministicWallet.KeyPath
import fr.acinq.bitcoin.{Block, Script}
import fr.acinq.eclair.TestConstants
import fr.acinq.eclair.channel.Channel.{fundingKeyPath, keyPath}
import fr.acinq.eclair.channel.LocalParams
import fr.acinq.eclair.transactions.Scripts
import org.scalatest.FunSuite
import scodec.bits._

class LocalKeyManagerSpec extends FunSuite {
  test("generate the same node id from the same seed") {
    // if this test breaks it means that we will generate a different node id  from
    // the same seed, which could be a problem during an upgrade
    val seed = hex"17b086b228025fa8f4416324b6ba2ec36e68570ae2fc3d392520969f2a9d0c1501"
    val keyManager = new LocalKeyManager(seed, Block.TestnetGenesisBlock.hash)
    assert(keyManager.nodeId == PublicKey(hex"02a051267759c3a149e3e72372f4e0c4054ba597ebfd0eda78a2273023667205ee"))
  }

  test("generate the same secrets from the same seed") {
    // data was generated with eclair 0.3
    val seed = hex"17b086b228025fa8f4416324b6ba2ec36e68570ae2fc3d392520969f2a9d0c1501"
    val keyManager = new LocalKeyManager(seed, Block.TestnetGenesisBlock.hash)
    assert(keyManager.nodeId == PublicKey(hex"02a051267759c3a149e3e72372f4e0c4054ba597ebfd0eda78a2273023667205ee"))
    val keyPath = KeyPath("m/1'/2'/3'/4'")
    assert(keyManager.commitmentSecret(keyPath, 0L).value == ByteVector32.fromValidHex("fa7a8c2fc62642f7a9a19ea0bfad14d39a430f3c9899c185dcecc61c8077891e"))
    assert(keyManager.commitmentSecret(keyPath, 1L).value == ByteVector32.fromValidHex("3e82338d3e487c760ee10448127613d196b040e86ce90d2d437db6425bb7301c"))
    assert(keyManager.commitmentSecret(keyPath, 2L).value == ByteVector32.fromValidHex("102357f7a9b2d0b9147f645c98aa156d3278ddb4745caf0631773dd663e76e6f"))
    assert(keyManager.commitmentPoint(keyPath, 0L).value == hex"0x0237dd5a0ea26ed84ed1249d46cc715679b542939d6943b42232e043825cde3944")
    assert(DeterministicWallet.encode(keyManager.delayedPaymentPoint(keyPath), DeterministicWallet.tpub) == "tpubDMBn7xW1g1Gsok5eThkJAKJnB3ZFqZQnvsdWv8VvM3RjZkqVPZZpjPDAAmbyDHnZPdAZY8EnFBh1ibTBtiuDqb8t9wRcAZiFihma3yYRG1f")
    assert(DeterministicWallet.encode(keyManager.htlcPoint(keyPath), DeterministicWallet.tpub) == "tpubDMBn7xW1g1GsqpsqaVNB1ehpjktQUX44Dycy7fJ6thp774XGzNeWFmQf5L6dVChHREgkoc8BYc2caHqwc2mZzTYCwoxsvrpchBSujsPCvGH")
    assert(DeterministicWallet.encode(keyManager.paymentPoint(keyPath), DeterministicWallet.tpub) == "tpubDMBn7xW1g1Gsme9jTAEJwTvizDJtJEgE3jc9vkDqQ9azuh9Es2aM6GsioFiouwdvWPJoNw2zavCkVTMta6UJN6BWR5cMZQsSHvsFyQNfGzv")
    assert(DeterministicWallet.encode(keyManager.revocationPoint(keyPath), DeterministicWallet.tpub) == "tpubDMBn7xW1g1GsizhaZ7M4co6sBtUDhRUKgUUPWRv3WfLTpTGYrSjATJy6ZVSoYFCKRnaBop5dFig3Ham1P145NQAKuUgPUbujLAooL7F2vy6")
  }

  test("generate different node ids from the same seed on different chains") {
    val seed = hex"17b086b228025fa8f4416324b6ba2ec36e68570ae2fc3d392520969f2a9d0c1501"
    val keyManager1 = new LocalKeyManager(seed, Block.TestnetGenesisBlock.hash)
    val keyManager2 = new LocalKeyManager(seed, Block.LivenetGenesisBlock.hash)
    assert(keyManager1.nodeId != keyManager2.nodeId)
    val keyPath = KeyPath(1L :: Nil)
    assert(keyManager1.fundingPublicKey(keyPath) != keyManager2.fundingPublicKey(keyPath))
    assert(keyManager1.commitmentPoint(keyPath, 1) != keyManager2.commitmentPoint(keyPath, 1))
  }

  /**
    * TESTNET funder funding public key from extended public key: 03c69365c8ae813a45b9fead1056331c41f38ab3ab7d5d383d62c4c70cfd91f9ea
    * TESTNET funder payment point from extended public key: 02efa31ae79a7c29faf23a21f00d5ca62ce14539c2f431db5dade3e919d2ff9050
    * TESTNET funder revocation point from extended public key: 03f5cfec0e593e3d8fe965eb4ca066fe810cf8e87f7f2c0c8ababbd1a004662777
    * TESTNET funder payment point from extended public key: 02efa31ae79a7c29faf23a21f00d5ca62ce14539c2f431db5dade3e919d2ff9050
    * TESTNET funder delayed payment point from extended public key: 029b6dd179c16ea0952b7ff59ca59377ccd257b9ae8f0a6a88bd5409a07013b443
    * TESTNET funder htlc point from extended public key: 02e5afb68852863190c893d61f6244339ad368a14e0b761ab3cff5531c204e1efc
    * TESTNET funder shachain point from extended public key: 02d46180dfd3a4bc388ac3dabe1240fc671d240e48c0f5652bce4363c22a878426
    *
    * MAINNET funder funding public key from extended public key: 02dd88103f4690fa5484f0c6a13f917fb00f03b5f2be1375cd08c64b11b19d730e
    * MAINNET funder payment point from extended public key: 030a7898d98245666be451b55a63a5f1acd71ec1efec85fb8364f3fbe46327441b
    *
    * TESTNET fundee funding public key from extended public key #34273 #0: 029278489277ce1abf6a05463ec913f5fe32ee194588a13c5b7899215d4ee477da
    * TESTNET fundee htlc public point from extended public key #34273 #0: 02563a8f3480b06f1e653c7a1a9a006236dfe503ec616ccef8656ab8e2cbe938d5
    *
    * MAINNET fundee funding public key from extended public key #34273 #0: 03fe59b3ac6c2f08172d634f0346194fbf3e4e90fe01422a030546a913f0a4b21e
    * MAINNET fundee htlc public point from extended public key #34273 #0: 03056cef064df0a65812c7c05fedfbdee978de5a9b4d986dc8b389e4c0ac42d9f1
    *
    * MAINNET fundee revocation point from extended public key #34273 #500: 022a714b39b36557b43146c37502d3a94f64fa5f651651b7749894cae64e6f42f7
    * MAINNET fundee payment point from extended public key #34273 #500: 022427779e5ea91e885a63413cae8e28c4d9bc451f4cdf7494fd9acc6a27828c1b
    * MAINNET fundee delayed payment point from extended public key #34273 #500: 0256f501048527f6d67e155b4aa615490f675d57e99e879f0758114dfe8774f5fd
    * MAINNET fundee htlc point from extended public key #34273 #500: 02d13757fd99c3e99985a263996d905d9a617fc2c17998630b3f5dd8cb79d88f79
    * MAINNET fundee shachain public point from extended public key #34273 #500: 037bc0457999d9ba6609250146fc3176f5cf382cfe5216e007267fd7fe047b2bf4
    *
    */

  test("test vectors derivation paths (funder TESTNET)") {

    val inputOutpoint = hex"1d12dcab62f3d509db16b8dcb69782ea6358a7060b579675561c4fc2e3294f41"
    val seed = hex"0101010102020202AABBCCDD030303030404040405050505060606060707070701"
    val keyManager = new LocalKeyManager(seed, Block.TestnetGenesisBlock.hash)

    val funderChannelKeyPath = LocalKeyManager.makeChannelKeyPathFunder(inputOutpoint)

    // TESTNET funder funding public key
    assert(keyManager.fundingPublicKey(funderChannelKeyPath).publicKey.value === hex"03c69365c8ae813a45b9fead1056331c41f38ab3ab7d5d383d62c4c70cfd91f9ea")
    // TESTNET funder payment point
    assert(keyManager.paymentPoint(funderChannelKeyPath).publicKey.value === hex"02efa31ae79a7c29faf23a21f00d5ca62ce14539c2f431db5dade3e919d2ff9050")
    // TESTNET funder revocation point
    assert(keyManager.revocationPoint(funderChannelKeyPath).publicKey.value === hex"03f5cfec0e593e3d8fe965eb4ca066fe810cf8e87f7f2c0c8ababbd1a004662777")
    // TESTNET funder delayed payment point
    assert(keyManager.delayedPaymentPoint(funderChannelKeyPath).publicKey.value === hex"029b6dd179c16ea0952b7ff59ca59377ccd257b9ae8f0a6a88bd5409a07013b443")
    // TESTNET funder htlc point
    assert(keyManager.htlcPoint(funderChannelKeyPath).publicKey.value === hex"02e5afb68852863190c893d61f6244339ad368a14e0b761ab3cff5531c204e1efc")
  }

  test("test vectors derivation paths (funder MAINNET)") {

    val inputOutpoint = hex"1d12dcab62f3d509db16b8dcb69782ea6358a7060b579675561c4fc2e3294f41"
    val seed = hex"0101010102020202AABBCCDD030303030404040405050505060606060707070701"
    val keyManager = new LocalKeyManager(seed, Block.LivenetGenesisBlock.hash)

    val funderChannelKeyPath = LocalKeyManager.makeChannelKeyPathFunder(inputOutpoint)

    // MAINNET funder funding public key from extended public key
    assert(keyManager.fundingPublicKey(funderChannelKeyPath).publicKey.value === hex"02dd88103f4690fa5484f0c6a13f917fb00f03b5f2be1375cd08c64b11b19d730e")
    // MAINNET funder payment point from extended public key
    assert(keyManager.paymentPoint(funderChannelKeyPath).publicKey.value === hex"030a7898d98245666be451b55a63a5f1acd71ec1efec85fb8364f3fbe46327441b")
  }

  test("test vectors derivation paths (fundee TESTNET)") {

    val remoteNodePubkey = PublicKey(hex"03864ef025fde8fb587d989186ce6a4a186895ee44a926bfc370e2c366597a3f8f")
    val seed = hex"0101010102020202AABBCCDD030303030404040405050505060606060707070701"
    val keyManager = new LocalKeyManager(seed, Block.TestnetGenesisBlock.hash)

    val fundeePubkeyKeyPath = LocalKeyManager.makeChannelKeyPathFundeePubkey(34273, 0)
    // TESTNET fundee funding public key from extended public key
    assert(keyManager.fundingPublicKey(fundeePubkeyKeyPath).publicKey.value === hex"029278489277ce1abf6a05463ec913f5fe32ee194588a13c5b7899215d4ee477da")

    val fundingPubkeyScript = Script.write(Script.pay2wsh(Scripts.multiSig2of2(keyManager.fundingPublicKey(fundeePubkeyKeyPath).publicKey, remoteNodePubkey)))
    val fundeeChannelKeyPath = LocalKeyManager.makeChannelKeyPathFundee(fundingPubkeyScript)

    // TESTNET fundee htlc public point from extended public key
    assert(keyManager.htlcPoint(fundeeChannelKeyPath).publicKey.value === hex"02563a8f3480b06f1e653c7a1a9a006236dfe503ec616ccef8656ab8e2cbe938d5")
    // TESTNET fundee htlc public point from extended public key
    assert(keyManager.paymentPoint(fundeeChannelKeyPath).publicKey.value === hex"0321047df59f000ba15f674c2eb6180c00edb55e5eae6e8ea22e82554c4213cfa4")
  }

  test("test vectors derivation paths (fundee MAINNET)") {

    val remoteNodePubkey = PublicKey(hex"03864ef025fde8fb587d989186ce6a4a186895ee44a926bfc370e2c366597a3f8f")
    val seed = hex"0101010102020202AABBCCDD030303030404040405050505060606060707070701"
    val keyManager = new LocalKeyManager(seed, Block.LivenetGenesisBlock.hash)

    val fundeePubkeyKeyPath = LocalKeyManager.makeChannelKeyPathFundeePubkey(34273, 0)
    // MAINNET fundee funding public key from extended public key
    assert(keyManager.fundingPublicKey(fundeePubkeyKeyPath).publicKey.value === hex"03fe59b3ac6c2f08172d634f0346194fbf3e4e90fe01422a030546a913f0a4b21e")

    val fundingPubkeyScript = Script.write(Script.pay2wsh(Scripts.multiSig2of2(keyManager.fundingPublicKey(fundeePubkeyKeyPath).publicKey, remoteNodePubkey)))
    val fundeeChannelKeyPath = LocalKeyManager.makeChannelKeyPathFundee(fundingPubkeyScript)

    // MAINNET fundee htlc public point from extended public key
    assert(keyManager.htlcPoint(fundeeChannelKeyPath).publicKey.value === hex"03056cef064df0a65812c7c05fedfbdee978de5a9b4d986dc8b389e4c0ac42d9f1")

    // with different counter
    val fundeePubkeyKeyPath1 = LocalKeyManager.makeChannelKeyPathFundeePubkey(34273, 500)
    val fundingPubkeyScript1 = Script.write(Script.pay2wsh(Scripts.multiSig2of2(keyManager.fundingPublicKey(fundeePubkeyKeyPath1).publicKey, remoteNodePubkey)))
    val fundeeChannelKeyPath1 = LocalKeyManager.makeChannelKeyPathFundee(fundingPubkeyScript1)

    // MAINNET fundee revocation point
    assert(keyManager.revocationPoint(fundeeChannelKeyPath1).publicKey.value === hex"022a714b39b36557b43146c37502d3a94f64fa5f651651b7749894cae64e6f42f7")
    // MAINNET fundee payment point
    assert(keyManager.paymentPoint(fundeeChannelKeyPath1).publicKey.value === hex"022427779e5ea91e885a63413cae8e28c4d9bc451f4cdf7494fd9acc6a27828c1b")
    // MAINNET fundee delayed payment point
    assert(keyManager.delayedPaymentPoint(fundeeChannelKeyPath1).publicKey.value === hex"0256f501048527f6d67e155b4aa615490f675d57e99e879f0758114dfe8774f5fd")
    // MAINNET fundee htlc point
    assert(keyManager.htlcPoint(fundeeChannelKeyPath1).publicKey.value === hex"02d13757fd99c3e99985a263996d905d9a617fc2c17998630b3f5dd8cb79d88f79")
    // MAINNED fundee shaSeed point
    assert(keyManager.shaSeedPub(fundeeChannelKeyPath1).publicKey.value === hex"037bc0457999d9ba6609250146fc3176f5cf382cfe5216e007267fd7fe047b2bf4")
  }

  test("use correct keypath to compute keys") {
    val seed = hex"0101010102020202AABBCCDD030303030404040405050505060606060707070701"
    val keyManager = new LocalKeyManager(seed, Block.LivenetGenesisBlock.hash)

    // FUNDER
    val funderParams = TestConstants.Alice.channelParams
    val funderKeyPath = funderParams.channelKeyPath.left.get

    assert(keyManager.fundingPublicKey(fundingKeyPath(funderParams)).publicKey == keyManager.fundingPublicKey(funderKeyPath).publicKey)
    assert(keyManager.revocationPoint(keyPath(funderParams)) == keyManager.revocationPoint(funderKeyPath))
    assert(keyManager.paymentPoint(keyPath(funderParams)).publicKey == keyManager.paymentPoint(funderKeyPath).publicKey)
    assert(keyManager.delayedPaymentPoint(keyPath(funderParams)).publicKey == keyManager.delayedPaymentPoint(funderKeyPath).publicKey)
    assert(keyManager.htlcPoint(keyPath(funderParams)).publicKey == keyManager.htlcPoint(funderKeyPath).publicKey)
    assert(keyManager.commitmentPoint(keyPath(funderParams), 0) == keyManager.commitmentPoint(funderKeyPath, 0))

    // FUNDEE
    val fundeeParams = TestConstants.Bob.channelParams
    val fundeeFundingKeyPath = fundeeParams.channelKeyPath.right.get.fundingKeyPath
    val fundeeKeyPath = fundeeParams.channelKeyPath.right.get.pointsKeyPath

    assert(keyManager.fundingPublicKey(fundingKeyPath(fundeeParams)).publicKey == keyManager.fundingPublicKey(fundeeFundingKeyPath).publicKey)
    assert(keyManager.revocationPoint(keyPath(fundeeParams)) == keyManager.revocationPoint(fundeeKeyPath))
    assert(keyManager.paymentPoint(keyPath(fundeeParams)).publicKey == keyManager.paymentPoint(fundeeKeyPath).publicKey)
    assert(keyManager.delayedPaymentPoint(keyPath(fundeeParams)).publicKey == keyManager.delayedPaymentPoint(fundeeKeyPath).publicKey)
    assert(keyManager.htlcPoint(keyPath(fundeeParams)).publicKey == keyManager.htlcPoint(fundeeKeyPath).publicKey)
    assert(keyManager.commitmentPoint(keyPath(fundeeParams), 0) == keyManager.commitmentPoint(fundeeKeyPath, 0))
  }

}