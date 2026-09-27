package io.baiyanwu.coinmonitor.data.wallet

import wallet.core.jni.Mnemonic

internal fun parseMnemonicSuggestions(value: String): List<String> =
    value.split(' ').map(String::trim).filter(String::isNotEmpty).distinct()

internal object WalletMnemonicDictionary {
    fun suggestions(prefix: String): List<String> {
        val normalized = prefix.trim()
        if (normalized.isEmpty() || normalized.any(Char::isWhitespace)) return emptyList()
        NativeWalletCore.ensureLoaded()
        return parseMnemonicSuggestions(Mnemonic.suggest(normalized))
    }

    fun isValidWord(word: String): Boolean {
        val normalized = word.trim()
        if (normalized.isEmpty() || normalized.any(Char::isWhitespace)) return false
        NativeWalletCore.ensureLoaded()
        return Mnemonic.isValidWord(normalized)
    }
}
