package com.worldtv.app.ui.account

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worldtv.app.R
import com.worldtv.app.data.AccountFavoritesRepository
import com.worldtv.app.domain.Accounts
import com.worldtv.app.domain.AuthError
import com.worldtv.app.ui.catalog.CatalogViewModel
import com.worldtv.app.ui.components.wtShadow
import com.worldtv.app.ui.theme.LocalWt
import kotlinx.coroutines.launch

val LocalAccountModel = staticCompositionLocalOf<AccountViewModel> {
    error("AccountViewModel is missing")
}

@Composable
fun AccountHost(
    catalogModel: CatalogViewModel,
    accountModel: AccountViewModel,
    favorites: AccountFavoritesRepository,
) {
    val user by accountModel.user.collectAsStateWithLifecycle()
    val offer by favorites.migrationOffer.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(catalogModel) {
        catalogModel.favoriteAuthPrompt.collect { accountModel.prompt(it) }
    }
    LaunchedEffect(user?.uid, accountModel.pendingFavorite?.key) {
        val entry = accountModel.pendingFavorite ?: return@LaunchedEffect
        if (accountModel.user.value == null) return@LaunchedEffect
        accountModel.consumePending()
        accountModel.closeScreen()
        accountModel.savePending(entry)
    }
    BackHandler(enabled = accountModel.screen != null) {
        accountModel.closeScreen()
    }
    if (accountModel.screen != null) {
        AccountPane(accountModel)
    }
    if (accountModel.screen == null && accountModel.pendingFavorite != null) {
        if (accountModel.accountsEnabled) {
            SignInPromptDialog(
                onSignIn = accountModel::openSignIn,
                onCreate = accountModel::openRegister,
                onLater = accountModel::dismissPrompt,
            )
        } else {
            NoticeDialog(
                message = stringResource(R.string.accounts_disabled),
                onClose = accountModel::dismissPrompt,
            )
        }
    }
    if (accountModel.screen == null && accountModel.pendingFavorite == null && offer.isNotEmpty()) {
        MigrationDialog(
            onAdd = { scope.launch { favorites.acceptLegacy() } },
            onLater = favorites::dismissLegacy,
        )
    }
    if (accountModel.deleteOpen) {
        DeleteAccountDialog(accountModel)
    }
}

@Composable
fun AccountSettingsCard(model: AccountViewModel) {
    val palette = LocalWt.current
    val user by model.user.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(22.dp)
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .wtShadow(shape, dark = palette.dark)
            .background(palette.surface, shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AccountText(stringResource(R.string.account_title), palette.heading, 16.sp, FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        when {
            !model.accountsEnabled -> AccountText(stringResource(R.string.accounts_disabled), palette.muted, 14.sp)
            user == null -> {
                AccountText(stringResource(R.string.account_signed_out), palette.muted, 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusTextButton(stringResource(R.string.account_sign_in), onClick = model::openSignIn)
                    FocusTextButton(stringResource(R.string.account_sign_up), onClick = model::openRegister)
                }
            }
            else -> {
                AccountText(user?.email ?: user?.displayName ?: stringResource(R.string.account_signed_in), palette.heading, 16.sp)
                FocusTextButton(stringResource(R.string.account_sign_out), onClick = model::signOut)
                FocusTextButton(stringResource(R.string.account_delete), onClick = model::startDelete)
            }
        }
        FocusTextButton(stringResource(R.string.account_deletion_page)) {
            openDeletionPage(context)
        }
    }
}

@Composable
private fun AccountPane(model: AccountViewModel) {
    val palette = LocalWt.current
    val keyboard = LocalSoftwareKeyboardController.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(model.screen) {
        runCatching { emailFocus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        FocusTextButton(stringResource(R.string.back), onClick = model::closeScreen)
        Spacer(Modifier.height(12.dp))
        AccountText(
            stringResource(if (model.registerMode) R.string.account_sign_up else R.string.account_sign_in),
            palette.heading,
            30.sp,
            FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        if (!model.accountsEnabled) {
            AccountText(stringResource(R.string.accounts_disabled), palette.muted, 16.sp)
            return@Column
        }
        OutlinedTextField(
            value = model.email,
            onValueChange = { model.email = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocus)
                .showKeyboardWhenFocused(),
            enabled = model.busy == null,
            singleLine = true,
            textStyle = accountFieldStyle(),
            label = { AccountText(stringResource(R.string.auth_email), palette.muted, 14.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { runCatching { passwordFocus.requestFocus() } }),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = model.password,
            onValueChange = { model.password = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocus)
                .showKeyboardWhenFocused(),
            enabled = model.busy == null,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = accountFieldStyle(),
            label = { AccountText(stringResource(R.string.auth_password), palette.muted, 14.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                keyboard?.hide()
                model.submit()
            }),
        )
        model.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            AccountText(authErrorText(error), palette.live, 14.sp)
        }
        if (model.resetSent) {
            Spacer(Modifier.height(12.dp))
            AccountText(stringResource(R.string.auth_reset_sent), palette.accent, 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = model::submit,
            enabled = model.busy == null && model.email.isNotBlank() && model.password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
        ) {
            if (model.busy == AuthBusy.SUBMIT) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                AccountText(
                    stringResource(if (model.registerMode) R.string.account_sign_up else R.string.account_sign_in),
                    Color.White,
                    16.sp,
                    FontWeight.SemiBold,
                )
            }
        }
        if (model.googleSignInAvailable) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = model::signInWithGoogle,
                enabled = model.busy == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (model.busy == AuthBusy.GOOGLE) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    AccountText(stringResource(R.string.auth_google), palette.heading, 16.sp)
                }
            }
        }
        FocusTextButton(
            label = stringResource(R.string.auth_forgot),
            enabled = model.busy == null && model.email.isNotBlank(),
            onClick = model::sendReset,
        )
        if (model.busy == AuthBusy.RESET) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        FocusTextButton(
            label = stringResource(if (model.registerMode) R.string.auth_switch_sign_in else R.string.auth_switch_register),
            enabled = model.busy == null,
            onClick = model::switchMode,
        )
    }
}

@Composable
private fun SignInPromptDialog(
    onSignIn: () -> Unit,
    onCreate: () -> Unit,
    onLater: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    AlertDialog(
        onDismissRequest = onLater,
        title = { AccountText(stringResource(R.string.account_sign_in), LocalWt.current.heading, 20.sp, FontWeight.SemiBold) },
        text = { AccountText(stringResource(R.string.auth_required_body), LocalWt.current.text, 16.sp) },
        confirmButton = {
            TextButton(onClick = onSignIn, modifier = Modifier.focusRequester(first)) {
                AccountText(stringResource(R.string.account_sign_in), LocalWt.current.accent, 14.sp)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCreate) {
                    AccountText(stringResource(R.string.account_sign_up), LocalWt.current.accent, 14.sp)
                }
                TextButton(onClick = onLater) {
                    AccountText(stringResource(R.string.account_not_now), LocalWt.current.muted, 14.sp)
                }
            }
        },
    )
}

@Composable
private fun MigrationDialog(onAdd: () -> Unit, onLater: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    AlertDialog(
        onDismissRequest = onLater,
        title = { AccountText(stringResource(R.string.migration_title), LocalWt.current.heading, 20.sp, FontWeight.SemiBold) },
        text = { AccountText(stringResource(R.string.migration_body), LocalWt.current.text, 16.sp) },
        confirmButton = {
            TextButton(onClick = onAdd, modifier = Modifier.focusRequester(first)) {
                AccountText(stringResource(R.string.migration_add), LocalWt.current.accent, 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                AccountText(stringResource(R.string.account_not_now), LocalWt.current.muted, 14.sp)
            }
        },
    )
}

@Composable
private fun DeleteAccountDialog(model: AccountViewModel) {
    val palette = LocalWt.current
    val passwordFocus = remember { FocusRequester() }
    LaunchedEffect(model.needsPassword) {
        if (model.needsPassword) runCatching { passwordFocus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = model::cancelDelete,
        title = { AccountText(stringResource(R.string.account_delete_title), palette.heading, 20.sp, FontWeight.SemiBold) },
        text = {
            Column {
                AccountText(
                    stringResource(if (model.needsPassword) R.string.auth_error_recent else R.string.account_delete_body),
                    palette.text,
                    16.sp,
                )
                if (model.needsPassword) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = model.deletePassword,
                        onValueChange = { model.deletePassword = it },
                        modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).showKeyboardWhenFocused(),
                        enabled = !model.deleting,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = accountFieldStyle(),
                        label = { AccountText(stringResource(R.string.auth_password), palette.muted, 14.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { model.confirmDelete() }),
                    )
                }
                model.deleteError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    AccountText(authErrorText(error), palette.live, 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = model::confirmDelete, enabled = !model.deleting) {
                if (model.deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    AccountText(stringResource(R.string.account_delete_confirm), palette.live, 14.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = model::cancelDelete, enabled = !model.deleting) {
                AccountText(stringResource(R.string.cancel), palette.muted, 14.sp)
            }
        },
    )
}

@Composable
private fun NoticeDialog(message: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = { AccountText(message, LocalWt.current.text, 16.sp) },
        confirmButton = {
            TextButton(onClick = onClose) {
                AccountText(stringResource(R.string.account_not_now), LocalWt.current.accent, 14.sp)
            }
        },
    )
}

@Composable
private fun FocusTextButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalWt.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) palette.accent else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
        ),
    ) {
        AccountText(label, if (enabled) palette.accent else palette.muted, 14.sp)
    }
}

@Composable
private fun AccountText(
    text: String,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) {
    Text(text, color = color, fontSize = size, fontWeight = weight, fontFamily = FontFamily.Default)
}

@Composable
private fun authErrorText(error: AuthError): String = when (error) {
    AuthError.INVALID_EMAIL -> stringResource(R.string.auth_error_email)
    AuthError.WRONG_PASSWORD -> stringResource(R.string.auth_error_password)
    AuthError.EMAIL_IN_USE -> stringResource(R.string.auth_error_in_use)
    AuthError.WEAK_PASSWORD -> stringResource(R.string.auth_error_weak)
    AuthError.USER_NOT_FOUND -> stringResource(R.string.auth_error_missing)
    AuthError.NETWORK -> stringResource(R.string.auth_error_network)
    AuthError.RECENT_LOGIN_REQUIRED -> stringResource(R.string.auth_error_recent)
    AuthError.CANCELLED -> ""
    AuthError.DISABLED -> stringResource(R.string.accounts_disabled)
    AuthError.UNKNOWN -> stringResource(R.string.auth_error_unknown)
}

@Composable
private fun accountFieldStyle() = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, color = LocalWt.current.heading)

@Composable
private fun Modifier.showKeyboardWhenFocused(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    return this.then(
        Modifier.onFocusChanged { state ->
            if (state.isFocused) keyboard?.show()
        },
    )
}

private fun openDeletionPage(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Accounts.DELETION_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
