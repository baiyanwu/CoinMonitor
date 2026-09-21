package io.baiyanwu.coinmonitor.data.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DappMessageSignerInstrumentedTest {
    private val privateKey = "03a9ca895dca1623c7dfd69693f7b4111f5d819d2e145536e0b03c136025a25d"
        .hexToBytes()
    private val signer = DappMessageSigner()

    @Test
    fun signsPersonalMessageUsingWalletCoreKnownVector() {
        val signature = signer.signPersonalMessage("Foo", privateKey)

        assertEquals(
            "0x21a779d499957e7fd39392d49a079679009e60e492d9654a148829be43d2490736" +
                "ec72bc4a5644047d979c3cf4ebe2c1c514044cf436b063cb89fc6676be71101b",
            signature
        )
    }

    @Test
    fun signsEip712UsingWalletCoreKnownVector() {
        val typedData = """
            {
              "types": {
                "EIP712Domain": [
                  {"name":"name","type":"string"},
                  {"name":"version","type":"string"},
                  {"name":"chainId","type":"uint256"},
                  {"name":"verifyingContract","type":"address"}
                ],
                "Person": [
                  {"name":"name","type":"string"},
                  {"name":"wallet","type":"address"}
                ]
              },
              "primaryType":"Person",
              "domain": {
                "name":"Ether Person",
                "version":"1",
                "chainId":0,
                "verifyingContract":"0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
              },
              "message": {
                "name":"Cow",
                "wallet":"CD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
              }
            }
        """.trimIndent()

        val signature = signer.signTypedData(typedData, privateKey)

        assertEquals(
            "0x446434e4c34d6b7456e5f07a1b994b88bf85c057234c68d1e10c936b1c85706" +
                "c4e19147c0ac3a983bc2d56ebfd7146f8b62bcea6114900fe8e7d7351f44bf3761c",
            signature
        )
    }
}
