package com.ello.litertchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private val PhoneBlue = ElloPalette.Canvas
private val PhoneInk = ElloPalette.Text
private val PhoneMuted = ElloPalette.Muted
private val PhonePanel = ElloPalette.Surface
private val CountryLookupClient = OkHttpClient.Builder()
    .callTimeout(5, TimeUnit.SECONDS)
    .build()

private data class PhoneCountry(
    val region: String,
    val name: String,
    val dialCode: String,
)

private val PhoneCountries: List<PhoneCountry> = PhoneNumberUtil.getInstance().supportedRegions
    .filter { it != "001" }
    .mapNotNull { region ->
        val callingCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(region)
        if (callingCode <= 0) null else PhoneCountry(
            region = region,
            name = Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.getDefault()),
            dialCode = "+$callingCode",
        )
    }
    .sortedBy { it.name }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun OnboardingPhoneScreen(
    modifier: Modifier = Modifier,
    initialPhone: String = "",
    busy: Boolean = false,
    onContinue: (String) -> Unit,
) {
    val hapticView = LocalView.current
    val fallbackRegion = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT).takeIf { code -> PhoneCountries.any { it.region == code } } ?: "US"
    }
    val parsedInitialPhone = remember(initialPhone, fallbackRegion) {
        runCatching { PhoneNumberUtil.getInstance().parse(initialPhone, fallbackRegion) }.getOrNull()
    }
    val initialRegion = parsedInitialPhone?.let(PhoneNumberUtil.getInstance()::getRegionCodeForNumber)
        ?: fallbackRegion
    var selectedRegion by rememberSaveable { mutableStateOf(initialRegion) }
    val selectedCountry = PhoneCountries.firstOrNull { it.region == selectedRegion } ?: PhoneCountries.first()
    var phone by rememberSaveable {
        mutableStateOf((parsedInitialPhone?.nationalNumber?.toString() ?: initialPhone.filter(Char::isDigit)).take(10))
    }
    var countryPickedByUser by rememberSaveable { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var countrySearch by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val detectedRegion = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://ipapi.co/json/")
                    .header("User-Agent", "Ello-Android")
                    .build()
                CountryLookupClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    JSONObject(response.body?.string().orEmpty()).optString("country_code").uppercase(Locale.ROOT)
                }
            }.getOrNull()
        }
        if (!countryPickedByUser && phone.isBlank() && !detectedRegion.isNullOrBlank()) {
            PhoneCountries.firstOrNull { it.region == detectedRegion }?.let { country ->
                selectedRegion = country.region
            }
        }
    }

    val fullPhone = selectedCountry.dialCode + phone.filter(Char::isDigit)
    val fullDigitCount = fullPhone.count(Char::isDigit)
    val valid = phone.count(Char::isDigit) == 10 && fullDigitCount <= 15

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PhoneBlue)
            .imePadding(),
    ) {
        val compact = maxHeight < 620.dp
        Column(Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = if (compact) 92.dp else 156.dp),
            ) {
                val mascotSize = minOf(maxWidth, maxHeight) * 0.97f
                ElloAnimatedMascot(
                    rawResId = R.raw.ello_chilling_blue,
                    modifier = Modifier.size(mascotSize).align(Alignment.Center),
                    contentDescription = "Ello mascot relaxing",
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = PhonePanel,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = if (compact) 22.dp else 28.dp, bottom = if (compact) 20.dp else 28.dp),
                ) {
                    Text(
                        text = "Your number",
                        color = PhoneInk,
                        fontSize = 27.sp,
                        lineHeight = 33.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 18.dp else 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = {
                                hapticView.performElloHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                countrySearch = ""
                                showCountryPicker = true
                            },
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = ElloPalette.SurfaceRaised,
                            border = BorderStroke(1.dp, ElloPalette.Border),
                            enabled = !busy,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(selectedCountry.region, color = PhoneInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(5.dp))
                                Text(selectedCountry.dialCode, color = PhoneInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose country code", tint = PhoneMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(9.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { value ->
                                val nextPhone = value.filter(Char::isDigit).take(10)
                                hapticView.performElloTypingHaptic(phone, nextPhone)
                                phone = nextPhone
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !busy,
                            placeholder = { Text("10-digit number", color = ElloPalette.Muted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = PhoneBlue, modifier = Modifier.size(19.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ElloPalette.SurfaceRaised,
                                unfocusedContainerColor = ElloPalette.SurfaceRaised,
                                disabledContainerColor = ElloPalette.SurfaceRaised,
                                focusedBorderColor = ElloPalette.Accent,
                                unfocusedBorderColor = ElloPalette.Border,
                                focusedTextColor = PhoneInk,
                                unfocusedTextColor = PhoneInk,
                                cursorColor = ElloPalette.Accent,
                            ),
                        )
                    }

                    Button(
                        onClick = {
                            hapticView.performElloHaptic(HapticFeedbackConstants.CONFIRM)
                            onContinue(fullPhone)
                        },
                        enabled = valid && !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compact) 18.dp else 24.dp)
                            .height(54.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElloPalette.Accent,
                            contentColor = ElloPalette.AccentText,
                            disabledContainerColor = PhoneBlue.copy(alpha = 0.38f),
                            disabledContentColor = ElloPalette.AccentText.copy(alpha = 0.6f),
                        ),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(21.dp), color = ElloPalette.AccentText, strokeWidth = 2.dp)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(9.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCountryPicker) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filteredCountries = remember(countrySearch) {
            PhoneCountries.filter { country ->
                country.name.contains(countrySearch, ignoreCase = true) ||
                    country.region.contains(countrySearch, ignoreCase = true) ||
                    country.dialCode.contains(countrySearch)
            }
        }
        ModalBottomSheet(
            onDismissRequest = { showCountryPicker = false },
            sheetState = sheetState,
            containerColor = PhonePanel,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("Choose a country", color = PhoneInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = countrySearch,
                    onValueChange = { value -> hapticView.performElloTypingHaptic(countrySearch, value); countrySearch = value },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                    placeholder = { Text("Search countries or codes") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PhoneMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ElloPalette.SurfaceRaised,
                        unfocusedContainerColor = ElloPalette.SurfaceRaised,
                        focusedBorderColor = ElloPalette.Accent,
                        unfocusedBorderColor = ElloPalette.Border,
                        focusedTextColor = ElloPalette.Text,
                        unfocusedTextColor = ElloPalette.Text,
                        cursorColor = ElloPalette.Accent,
                    ),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).navigationBarsPadding()) {
                    items(filteredCountries, key = { it.region }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .clickable {
                                    hapticView.performElloHaptic(HapticFeedbackConstants.CLOCK_TICK)
                                    selectedRegion = country.region
                                    countryPickedByUser = true
                                    showCountryPicker = false
                                }
                                .padding(horizontal = 12.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(country.name, color = PhoneInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text(country.dialCode, color = PhoneMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (filteredCountries.isEmpty()) item {
                        Text("No country found", color = PhoneMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                    }
                }
            }
        }
    }
}
