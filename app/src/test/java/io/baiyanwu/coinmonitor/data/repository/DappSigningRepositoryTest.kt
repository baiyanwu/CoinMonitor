package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.domain.model.WalletNetwork
import io.baiyanwu.coinmonitor.ui.browser.DappSignatureMethod
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DappSigningRepositoryTest {
    private val repository = DappSigningRepository()
    private val address = "0x1111111111111111111111111111111111111111"

    @Test
    fun `prepares UTF-8 personal sign request`() {
        val request = repository.prepare(
            method = DappSignatureMethod.PERSONAL_MESSAGE,
            network = WalletNetwork.BNB_CHAIN,
            expectedAddress = address,
            params = buildJsonObject {
                put("address", "0x${address.drop(2).uppercase()}")
                put("data", "0x48656c6c6f")
                put("originalMethod", "personal_sign")
            }
        ) as DappSignatureRequest.PersonalMessage

        assertEquals("Hello", request.message)
        assertEquals("personal_sign", request.originalMethod)
    }

    @Test
    fun `prepares Permit2 typed data and exposes nested approval fields`() {
        val raw = """
            {
              "types": {
                "EIP712Domain": [
                  {"name":"name","type":"string"},
                  {"name":"chainId","type":"uint256"},
                  {"name":"verifyingContract","type":"address"}
                ],
                "PermitSingle": [{"name":"details","type":"PermitDetails"}],
                "PermitDetails": [
                  {"name":"token","type":"address"},
                  {"name":"amount","type":"uint160"}
                ]
              },
              "primaryType": "PermitSingle",
              "domain": {
                "name": "Permit2",
                "chainId": 56,
                "verifyingContract": "0x2222222222222222222222222222222222222222"
              },
              "message": {
                "details": {
                  "token": "0x3333333333333333333333333333333333333333",
                  "amount": "1000000"
                }
              }
            }
        """.trimIndent()
        val request = repository.prepare(
            method = DappSignatureMethod.TYPED_DATA,
            network = WalletNetwork.BNB_CHAIN,
            expectedAddress = address,
            params = buildJsonObject {
                put("address", address)
                put("raw", raw)
                put("version", "V4")
                put("originalMethod", "eth_signTypedData_v4")
            }
        ) as DappSignatureRequest.TypedData

        assertEquals("PermitSingle", request.primaryType)
        assertEquals("Permit2", request.domainName)
        assertTrue(request.fields.any { it.name == "details.token" })
        assertTrue(request.fields.any { it.name == "details.amount" && it.value == "1000000" })
    }

    @Test
    fun `rejects typed data for another chain`() {
        val raw = """
            {
              "types": {
                "EIP712Domain": [{"name":"chainId","type":"uint256"}],
                "Order": [{"name":"amount","type":"uint256"}]
              },
              "primaryType": "Order",
              "domain": {"chainId": "0x1"},
              "message": {"amount": "1"}
            }
        """.trimIndent()

        val error = runCatching {
            repository.prepare(
                method = DappSignatureMethod.TYPED_DATA,
                network = WalletNetwork.BNB_CHAIN,
                expectedAddress = address,
                params = buildJsonObject {
                    put("address", address)
                    put("raw", raw)
                    put("version", "V4")
                }
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("Chain ID") == true)
    }

    @Test
    fun `rejects signing with another wallet address`() {
        val error = runCatching {
            repository.prepare(
                method = DappSignatureMethod.PERSONAL_MESSAGE,
                network = WalletNetwork.BNB_CHAIN,
                expectedAddress = address,
                params = buildJsonObject {
                    put("address", "0x9999999999999999999999999999999999999999")
                    put("data", "0x48656c6c6f")
                }
            )
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("当前活动钱包") == true)
    }
}
