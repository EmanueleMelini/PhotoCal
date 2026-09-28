package it.emanuelemelini.photocal.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Text created outside the UI (ViewModels, errors) and resolved in the current language
 * only when shown, so a language change doesn't leave stale strings behind.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Res -> stringResource(id, *args.map { it.resolved() }.toTypedArray())
        is Plural -> pluralStringResource(id, count, *args.map { it.resolved() }.toTypedArray())
        is Raw -> text
    }
}

/** Nested UiText arguments are resolved too. */
@Composable
private fun Any.resolved(): Any = if (this is UiText) asString() else this

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())
