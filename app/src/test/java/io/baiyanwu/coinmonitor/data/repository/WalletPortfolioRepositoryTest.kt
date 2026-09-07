package io.baiyanwu.coinmonitor.data.repository

import io.baiyanwu.coinmonitor.data.network.OkxWalletClient
import io.baiyanwu.coinmonitor.domain.model.OkxWalletCredentials
import java.math.BigDecimal
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletPortfolioRepositoryTest {
    @Test fun `uses BigDecimal values and sorts risk assets last`() = kotlinx.coroutines.runBlocking {
        val repository = repository(totalSucceeds = true)
        val snapshot = repository.load("11111111111111111111111111111111", includeRiskInTotal = false)
        assertEquals(BigDecimal("0.020"), snapshot.assets.first().holdingValueUsd)
        assertFalse(snapshot.assets.first().isRiskToken)
        assertTrue(snapshot.assets.last().isRiskToken)
        assertEquals(BigDecimal("9.99"), snapshot.totalValueUsd)
        assertFalse(snapshot.totalIsEstimated)
    }

    @Test fun `falls back to priced non-risk details when total fails`() = kotlinx.coroutines.runBlocking {
        val snapshot = repository(totalSucceeds = false).load("11111111111111111111111111111111", includeRiskInTotal = false)
        assertEquals(BigDecimal("0.020"), snapshot.totalValueUsd)
        assertTrue(snapshot.totalIsEstimated)
    }

    private fun repository(totalSucceeds: Boolean): DefaultWalletPortfolioRepository {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val isTotal = chain.request().url.encodedPath.contains("total-value")
            val isSupportedChains = chain.request().url.encodedPath.contains("supported/chain")
            val body = if (isSupportedChains) {
                """{"code":"0","data":[{"name":"Solana","chainIndex":"501"}]}"""
            } else if (isTotal && totalSucceeds) {
                """{"code":"0","data":[{"totalValue":"9.99"}]}"""
            } else if (isTotal) {
                """{"code":"50000","msg":"total unavailable","data":[]}"""
            } else {
                """{"code":"0","data":[{"tokenAssets":[
                  {"chainIndex":"501","tokenContractAddress":"normal","symbol":"AAA","balance":"0.1","tokenPrice":"0.20","isRiskToken":false},
                  {"chainIndex":"501","tokenContractAddress":"no-price","symbol":"BBB","balance":"5","tokenPrice":"","isRiskToken":false},
                  {"chainIndex":"501","tokenContractAddress":"risk","symbol":"CCC","balance":"2","tokenPrice":"3","isRiskToken":true},
                  {"chainIndex":"501","tokenContractAddress":"zero","symbol":"ZERO","balance":"0","tokenPrice":"4","isRiskToken":false}
                ]}]}"""
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        return DefaultWalletPortfolioRepository(OkxWalletClient(http, { OkxWalletCredentials(true, "key", "secret", "pass") }), nowMillis = { 123L })
    }
}
