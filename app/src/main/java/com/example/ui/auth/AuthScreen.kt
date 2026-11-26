package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.auth.onCreateAccountClicked
import com.example.data.model.TargetLanguage
import com.example.ui.components.StudioIcons
import com.example.ui.components.StudioBadge
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileCard
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.accentForLanguage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val palette = LocalStudioPalette.current

    var isAutoChecking by remember { mutableStateOf(true) }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateAccount by rememberSaveable { mutableStateOf(false) }
    var createAccountError by remember { mutableStateOf<String?>(null) }
    var legalDialog by rememberSaveable { mutableStateOf<LegalDoc?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isAutoChecking = false
                onAuthSuccess()
            },
            onUnauthenticated = {
                isAutoChecking = false
            },
            scope = scope
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StudioBadge(
                text = "ENGLISH → JP • ES • FR",
                containerColor = palette.languageTint,
                contentColor = palette.languageAccent
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            TactileCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_studio),
                        contentDescription = "Polyglot Studio desk illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LanguageMiniPill(TargetLanguage.JAPANESE, palette.isDark, Modifier.weight(1f))
                            LanguageMiniPill(TargetLanguage.SPANISH, palette.isDark, Modifier.weight(1f))
                            LanguageMiniPill(TargetLanguage.FRENCH, palette.isDark, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "5 Skill-Focused Games + AI Speech & Live Tutor",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FeatureChip(StudioIcons.Style, "Card Match")
                            FeatureChip(StudioIcons.Brush, "Calligraphy Vision")
                            FeatureChip(StudioIcons.Quiz, "Syntax Quiz")
                            FeatureChip(StudioIcons.GraphicEq, "Voice Arena")
                            FeatureChip(StudioIcons.Forum, "Story Café")
                            FeatureChip(StudioIcons.AutoAwesome, "Gemini Live Tutor")
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = palette.dangerSoft,
                    border = BorderStroke(1.5.dp, palette.danger),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            TactileButton(
                onClick = {
                    errorMessage = null
                    isSigningIn = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isSigningIn = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isSigningIn = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isSigningIn = false
                        }
                    )
                },
                enabled = !isSigningIn && !isAutoChecking,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_sign_in_button")
            ) {
                if (isSigningIn || isAutoChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isAutoChecking) "Checking session…" else "Signing in…",
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_with_google),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            TactileButton(
                onClick = {
                    errorMessage = null
                    createAccountError = null
                    showCreateAccount = true
                },
                enabled = !isSigningIn && !isAutoChecking,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_account_button")
            ) {
                Text(
                    text = stringResource(R.string.create_account),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Cloud syncs your XP, daily streaks, skill mastery, and saved phrases.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
            // Black in light mode as designed; falls back to the regular text colour in dark
            // mode, where pure black would be invisible on the dark background.
            val legalLinkColor = if (palette.isDark) MaterialTheme.colorScheme.onBackground else Color.Black
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                LegalLink(
                    text = "Terms and Conditions",
                    color = legalLinkColor,
                    testTag = "terms_link",
                    onClick = { legalDialog = LegalDoc.TERMS }
                )
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = legalLinkColor
                )
                LegalLink(
                    text = "Privacy Policy",
                    color = legalLinkColor,
                    testTag = "privacy_link",
                    onClick = { legalDialog = LegalDoc.PRIVACY }
                )
            }
        }
    }

    if (showCreateAccount) {
        CreateAccountDialog(
            isSubmitting = isSigningIn,
            errorMessage = createAccountError,
            onDismiss = { showCreateAccount = false },
            onSubmit = { username ->
                createAccountError = null
                isSigningIn = true
                onCreateAccountClicked(
                    username = username,
                    onAuthSuccess = {
                        isSigningIn = false
                        showCreateAccount = false
                        onAuthSuccess()
                    },
                    onAuthError = { msg ->
                        isSigningIn = false
                        createAccountError = msg
                    },
                    scope = scope
                )
            }
        )
    }

    when (legalDialog) {
        LegalDoc.TERMS -> LegalDialog(
            title = "Terms and Conditions",
            sections = TermsAndConditions,
            onDismiss = { legalDialog = null }
        )
        LegalDoc.PRIVACY -> LegalDialog(
            title = "Privacy Policy",
            sections = PrivacyPolicy,
            onDismiss = { legalDialog = null }
        )
        null -> Unit
    }
}

private enum class LegalDoc { TERMS, PRIVACY }

@Composable
private fun LegalLink(
    text: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .testTag(testTag)
    )
}

@Composable
private fun LanguageMiniPill(
    language: TargetLanguage,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val (accent, tint) = accentForLanguage(language, isDark)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tint,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = language.shortBadge,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Text(
                text = language.nativeLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    val palette = LocalStudioPalette.current
    Surface(
        shape = RoundedCornerShape(50),
        color = palette.subtleSurface,
        border = BorderStroke(1.dp, palette.inkBorder.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
