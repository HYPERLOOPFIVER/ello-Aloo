package com.ello.litertchat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val EmailAuthBackground = ElloPalette.Canvas
private val EmailAuthBlue = ElloPalette.Accent

@Composable
internal fun EmailOtpLoginScreen(
    context: Context,
    modifier: Modifier = Modifier,
    onVerified: () -> Unit,
) {
    val hapticView = LocalView.current
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().background(EmailAuthBackground).padding(horizontal = 26.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ElloAnimatedMascot(
            rawResId = R.raw.ello_waving,
            modifier = Modifier.height(220.dp).fillMaxWidth().padding(bottom = 24.dp),
        )
        Text(
            if (codeSent) "Check your email" else "Sign in with email",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (codeSent) "Enter the 6-digit code we sent you." else "We’ll email you a one-time code.",
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 22.dp),
        )
        if (!codeSent) {
            OutlinedTextField(
                value = email,
                onValueChange = { value -> val next = value.take(254); hapticView.performElloTypingHaptic(email, next); email = next; error = null },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                label = { Text("Email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        } else {
            Text(email, color = Color.White, fontSize = 14.sp)
            OutlinedTextField(
                value = code,
                onValueChange = { value -> val next = value.filter(Char::isDigit).take(6); hapticView.performElloTypingHaptic(code, next); code = next; error = null },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                singleLine = true,
                enabled = !busy,
                label = { Text("6-digit code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        }
        if (error != null) Text(error.orEmpty(), color = ElloPalette.Error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                error = null
                scope.launch {
                    runCatching {
                        if (codeSent) EmailOtpAuth.verifyCode(context, email, code)
                        else EmailOtpAuth.requestCode(email).let { codeSent = true }
                    }.onSuccess {
                        if (codeSent && it is String) onVerified()
                    }.onFailure { error = it.message ?: "Could not continue. Try again." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = CircleShape,
            enabled = !busy && if (codeSent) code.length == 6 else email.contains('@'),
            colors = ButtonDefaults.buttonColors(containerColor = EmailAuthBlue, contentColor = ElloPalette.AccentText),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
            else Text(if (codeSent) "Verify code" else "Send code", fontWeight = FontWeight.SemiBold)
        }
        if (codeSent) TextButton(enabled = !busy, onClick = { codeSent = false; code = ""; error = null }) {
            Text("Use another email", color = Color.White)
        }
        if (codeSent) TextButton(enabled = !busy, onClick = {
            busy = true
            error = null
            scope.launch {
                runCatching { EmailOtpAuth.requestCode(email) }
                    .onFailure { error = it.message ?: "Could not resend the code." }
                busy = false
            }
        }) {
            Text("Resend code", color = Color.White)
        }
    }
}
