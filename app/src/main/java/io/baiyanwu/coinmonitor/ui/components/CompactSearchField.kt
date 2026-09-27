package io.baiyanwu.coinmonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.baiyanwu.coinmonitor.R
import io.baiyanwu.coinmonitor.ui.theme.CoinMonitorThemeTokens

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val colors = CoinMonitorThemeTokens.colors
    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colors.primaryText,
        fontSize = 15.sp,
        lineHeight = 18.sp
    )
    val placeholderStyle = MaterialTheme.typography.bodyMedium.copy(
        color = colors.tertiaryText,
        fontSize = 15.sp,
        lineHeight = 18.sp
    )

    Surface(
        modifier = modifier.height(40.dp),
        color = colors.cardBackground,
        shape = RoundedCornerShape(18.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colors.accent
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = placeholder,
                                color = colors.tertiaryText,
                                style = placeholderStyle,
                                maxLines = 1
                            )
                        }
                        innerTextField()
                    }

                    if (value.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onClear),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.common_clear),
                                modifier = Modifier.size(16.dp),
                                tint = colors.secondaryText
                            )
                        }
                    }
                }
            }
        )
    }
}
