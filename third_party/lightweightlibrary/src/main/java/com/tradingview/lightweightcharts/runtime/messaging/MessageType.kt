package com.tradingview.lightweightcharts.runtime.messaging

import com.google.gson.annotations.SerializedName

enum class MessageType {

    @SerializedName("Message::FunctionResult")
    FUNCTION_RESULT,

    @SerializedName("Message::FatalError")
    FATAL_ERROR,

    @SerializedName("Message::Function")
    FUNCTION,

    @SerializedName("Message::Subscription")
    SUBSCRIBE,

    @SerializedName("Message::SubscriptionCancellation")
    UNSUBSCRIBE,

    @SerializedName("Message::SubscriptionResult")
    SUBSCRIBE_RESULT,

    @SerializedName("Message::SubscriptionCancellationResult")
    UNSUBSCRIBE_RESULT,

    @SerializedName("Message::Connection")
    CONNECTION
}
