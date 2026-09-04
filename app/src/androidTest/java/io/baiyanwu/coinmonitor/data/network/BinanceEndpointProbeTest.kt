package io.baiyanwu.coinmonitor.data.network

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.baiyanwu.coinmonitor.data.repository.DefaultNetworkLogRepository
import io.baiyanwu.coinmonitor.data.repository.DefaultMarketSearchRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BinanceEndpointProbeTest {
    @Test
    fun probePublicMarketDataEndpoints() {
        val factory = NetworkFactory(DefaultNetworkLogRepository())
        runBlocking {
            val repository = DefaultMarketSearchRepository(
                alphaApi = factory.alphaApi,
                binanceApi = factory.binanceApi,
                binanceFuturesApi = factory.binanceFuturesApi,
                okxApi = factory.okxApi,
                dexScreenerClient = DexScreenerClient(factory.dexScreenerApi)
            )
            repository.searchExchange("btc")
                .catch { error ->
                    Log.i("BinanceEndpointProbe", "search error => ${error::class.simpleName}: ${error.message}")
                }
                .collect { items ->
                    Log.i("BinanceEndpointProbe", "search emission => ${items.joinToString { it.id }}")
                }
        }
    }
}
