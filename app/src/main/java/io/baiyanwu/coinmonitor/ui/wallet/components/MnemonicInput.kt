package io.baiyanwu.coinmonitor.ui.wallet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.baiyanwu.coinmonitor.R
import kotlinx.coroutines.delay

private val mnemonicSeparator = Regex("[\\s\\u3000]+")
private val supportedMnemonicWordCounts = setOf(12, 15, 18, 21, 24)

@Composable
private fun MnemonicWordTag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    warningContentDescription: String? = null,
    onDelete: (() -> Unit)? = null
) {
    val isWarning = warningContentDescription != null
    val contentColor = when {
        isWarning -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = modifier.padding(top = 4.dp, end = 4.dp)) {
        Surface(
            modifier = Modifier
                .height(32.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(50),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (warningContentDescription != null) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = warningContentDescription,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
        if (onDelete != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .size(18.dp)
                    .clickable(onClick = onDelete),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.wallet_mnemonic_delete_word),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

internal enum class MnemonicWordEditMode {
    APPEND,
    REPLACE,
    INSERT_AFTER
}

internal fun splitMnemonicWords(value: String): List<String> =
    value.trim().split(mnemonicSeparator).filter(String::isNotBlank)

internal fun hasSupportedMnemonicWordCount(value: String): Boolean =
    splitMnemonicWords(value).size in supportedMnemonicWordCounts

internal fun updateMnemonicWords(
    currentValue: String,
    input: String,
    editingIndex: Int? = null,
    mode: MnemonicWordEditMode = MnemonicWordEditMode.APPEND
): String {
    val incoming = splitMnemonicWords(input)
    if (incoming.isEmpty()) return currentValue

    val words = splitMnemonicWords(currentValue).toMutableList()
    when (mode) {
        MnemonicWordEditMode.APPEND -> words.addAll(incoming)
        MnemonicWordEditMode.REPLACE -> {
            if (editingIndex == null || editingIndex !in words.indices) return currentValue
            words.removeAt(requireNotNull(editingIndex))
            words.addAll(editingIndex, incoming)
        }
        MnemonicWordEditMode.INSERT_AFTER -> {
            if (editingIndex == null || editingIndex !in words.indices) return currentValue
            words.addAll(requireNotNull(editingIndex) + 1, incoming)
        }
    }
    return words.joinToString(" ")
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
internal fun MnemonicInput(
    value: String,
    onValueChange: (String) -> Unit,
    suggestionsFor: (String) -> List<String>,
    isValidWord: (String) -> Boolean,
    modifier: Modifier = Modifier
) {
    val words = remember(value) { splitMnemonicWords(value) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editMode by rememberSaveable { mutableStateOf(MnemonicWordEditMode.APPEND) }
    var inputFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val wordValidity = remember(words, isValidWord) { words.map(isValidWord) }
    val invalidWordCount = wordValidity.count { valid -> !valid }
    val normalizedDraft = draft.text.trim()
    val suggestions = remember(normalizedDraft, suggestionsFor) {
        if (normalizedDraft.isEmpty() || normalizedDraft.any(Char::isWhitespace)) {
            emptyList()
        } else {
            suggestionsFor(normalizedDraft)
        }
    }
    val draftIsInvalid = normalizedDraft.length >= 2 &&
        suggestions.isEmpty() &&
        !isValidWord(normalizedDraft)

    LaunchedEffect(inputFocused) {
        if (inputFocused) {
            delay(80)
            bringIntoViewRequester.bringIntoView()
        }
    }

    fun resetSelection(keepFocus: Boolean) {
        draft = TextFieldValue()
        editingIndex = null
        editMode = MnemonicWordEditMode.APPEND
        if (keepFocus) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    fun commitInput(input: String, clearFocus: Boolean) {
        if (input.isBlank()) return
        onValueChange(updateMnemonicWords(value, input, editingIndex, editMode))
        resetSelection(keepFocus = !clearFocus)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.wallet_mnemonic_input_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (words.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                words.forEachIndexed { index, word ->
                    val valid = wordValidity[index]
                    val selected = editingIndex == index
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MnemonicWordTag(
                            text = "${index + 1}. $word",
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    resetSelection(keepFocus = true)
                                } else {
                                    editingIndex = index
                                    editMode = MnemonicWordEditMode.REPLACE
                                    draft = TextFieldValue(
                                        text = word,
                                        selection = TextRange(word.length)
                                    )
                                    focusRequester.requestFocus()
                                }
                            },
                            warningContentDescription = if (valid) null else stringResource(R.string.wallet_mnemonic_invalid_word),
                            onDelete = if (selected) {
                                {
                                    onValueChange(words.filterIndexed { wordIndex, _ -> wordIndex != index }.joinToString(" "))
                                    resetSelection(keepFocus = true)
                                }
                            } else {
                                null
                            }
                        )
                        if (selected) {
                            Surface(
                                modifier = Modifier
                                    .offset(y = 2.dp)
                                    .size(24.dp)
                                    .clickable {
                                        editMode = MnemonicWordEditMode.INSERT_AFTER
                                        draft = TextFieldValue()
                                        focusRequester.requestFocus()
                                    },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        contentDescription = stringResource(R.string.wallet_mnemonic_insert_after),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.wallet_mnemonic_word_count, words.size) +
                    " · " + stringResource(R.string.wallet_mnemonic_word_count_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (suggestions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.wallet_mnemonic_suggestions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            MnemonicWordTag(
                                text = suggestion,
                                selected = false,
                                onClick = {
                                    commitInput(suggestion, clearFocus = false)
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { newValue ->
                    val input = newValue.text
                    val containsSeparator = input.any(Char::isWhitespace) || input.contains('\u3000')
                    val pastedMultipleWords = splitMnemonicWords(input).size > 1
                    if ((containsSeparator || pastedMultipleWords) && splitMnemonicWords(input).isNotEmpty()) {
                        commitInput(input, clearFocus = false)
                    } else {
                        draft = newValue
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { inputFocused = it.isFocused },
                label = {
                    Text(
                        when (editMode) {
                            MnemonicWordEditMode.APPEND -> stringResource(R.string.wallet_mnemonic_input_label)
                            MnemonicWordEditMode.REPLACE -> stringResource(
                                R.string.wallet_mnemonic_edit_word,
                                requireNotNull(editingIndex) + 1
                            )
                            MnemonicWordEditMode.INSERT_AFTER -> stringResource(
                                R.string.wallet_mnemonic_insert_after_word,
                                requireNotNull(editingIndex) + 1
                            )
                        }
                    )
                },
                supportingText = when {
                    draftIsInvalid -> {
                        {
                            Text(
                                stringResource(R.string.wallet_mnemonic_no_suggestion),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    invalidWordCount > 0 -> {
                        {
                            Text(
                                stringResource(R.string.wallet_mnemonic_invalid_word_count, invalidWordCount),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    else -> null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { commitInput(draft.text, clearFocus = true) }),
                trailingIcon = {
                    if (draft.text.isNotBlank()) {
                        IconButton(onClick = { commitInput(draft.text, clearFocus = false) }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(
                                    if (editMode == MnemonicWordEditMode.REPLACE) R.string.wallet_mnemonic_save_word
                                    else R.string.wallet_mnemonic_add_word
                                )
                            )
                        }
                    }
                },
                isError = draftIsInvalid,
                singleLine = true
            )
        }
    }
}
