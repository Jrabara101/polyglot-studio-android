package com.example.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.auth.MINIMUM_AGE
import com.example.auth.ageFromBirthdayMillis
import com.example.auth.validateUsername
import com.example.ui.theme.LocalStudioPalette
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Pop-up for creating an account: username + birthday. The birthday only gates access
 * (must be [MINIMUM_AGE]+) and is never passed on. The prompt is age-neutral — it does not
 * reveal the cutoff before the user answers — and an under-age answer locks the form for this
 * session rather than letting the user immediately pick an older date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAccountDialog(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (username: String) -> Unit
) {
    val palette = LocalStudioPalette.current

    var username by rememberSaveable { mutableStateOf("") }
    var usernameTouched by rememberSaveable { mutableStateOf(false) }
    var birthdayMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var ageBlocked by rememberSaveable { mutableStateOf(false) }

    val usernameError = if (usernameTouched) validateUsername(username) else null
    val canSubmit = !isSubmitting && !ageBlocked &&
        validateUsername(username) == null && birthdayMillis != null

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Create your account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ageBlocked) {
                    Text(
                        text = "Sorry, you're not able to create an account right now. " +
                            "Polyglot Studio is for learners aged $MINIMUM_AGE and up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.danger
                    )
                    return@Column
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.take(20)
                        usernameTouched = true
                    },
                    label = { Text("Username") },
                    supportingText = {
                        Text(usernameError ?: "3–20 letters, numbers, dots or underscores. Don't use your real full name.")
                    },
                    isError = usernameError != null,
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_account_username")
                )

                OutlinedTextField(
                    value = birthdayMillis?.let { formatUtcDate(it) } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Birthday") },
                    placeholder = { Text("Select your birthday") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }, enabled = !isSubmitting) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = "Pick birthday")
                        }
                    },
                    supportingText = { Text("Used only to confirm your age. Not stored.") },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_account_birthday")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.danger
                    )
                }

                Text(
                    text = "By creating an account you agree to the Terms and Conditions and " +
                        "Privacy Policy. If you're under 18, a parent or guardian must agree too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (ageBlocked) {
                TextButton(onClick = onDismiss) { Text("Close") }
            } else {
                TextButton(
                    onClick = {
                        usernameTouched = true
                        val birthday = birthdayMillis ?: return@TextButton
                        if (validateUsername(username) != null) return@TextButton
                        if (ageFromBirthdayMillis(birthday) < MINIMUM_AGE) {
                            ageBlocked = true
                        } else {
                            onSubmit(username.trim())
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.testTag("create_account_submit")
                ) {
                    if (isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Creating…")
                        }
                    } else {
                        Text("Create Account")
                    }
                }
            }
        },
        dismissButton = {
            if (!ageBlocked) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
            }
        }
    )

    if (showDatePicker) {
        val todayUtcMillis = remember { todayAsUtcMidnightMillis() }
        val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = birthdayMillis,
            yearRange = (currentYear - 100)..currentYear,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
                override fun isSelectableYear(year: Int) = year <= currentYear
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        birthdayMillis = pickerState.selectedDateMillis
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Pop-up showing Terms and Conditions or the Privacy Policy. */
@Composable
fun LegalDialog(
    title: String,
    sections: List<LegalSection>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Last updated: $LEGAL_LAST_UPDATED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                sections.forEach { section ->
                    Spacer(Modifier.height(12.dp))
                    Text(section.heading, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(section.body, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun formatUtcDate(utcMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(utcMillis))

private fun todayAsUtcMidnightMillis(): Long {
    val local = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}
