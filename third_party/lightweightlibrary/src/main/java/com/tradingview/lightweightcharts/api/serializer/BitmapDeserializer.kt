package com.tradingview.lightweightcharts.api.serializer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.JsonElement
import java.lang.IllegalStateException

class BitmapDeserializer : Deserializer<Bitmap>() {
    companion object {
        // data:[<MIME-type>][;charset=<encoding>][;base64],<data>
        const val dataUrlToBase64Pattern = "^data:[^;]+;base64[,](?<data>.+)"
    }

    override fun deserialize(json: JsonElement): Bitmap {
        val dataUrl = json.asString
        val regex = Regex(dataUrlToBase64Pattern)
        val match = regex.find(dataUrl) ?: throw IllegalStateException("Invalid data url")
        val base64Data = match.groupValues[1]
        val decodedString: ByteArray = Base64.decode(base64Data, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    }
}
