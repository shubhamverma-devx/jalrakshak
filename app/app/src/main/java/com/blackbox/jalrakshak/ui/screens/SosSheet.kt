package com.blackbox.jalrakshak.ui.screens

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.blackbox.jalrakshak.ui.components.AppText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.theme.riskColor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * =====================================================================================
 *  SosSheet — "मदद माँगें" (two-way communication ka citizen wala sira)
 * =====================================================================================
 *  DATA: POST /api/relief -> officer dashboard ke "Relief requests" panel mein turant dikhta hai.
 *
 *  KYUN YE FEATURE PRODUCT KA ADHA HISSA HAI:
 *   Govt ka flood SMS EK-TARAFFA hai — citizen wapas kuch nahi bol sakta. Yahan chhat pe
 *   phasa aadmi apni EXACT location bhej sakta hai aur wo 30 second ke andar officer ke
 *   dashboard pe dikh jaati hai. Demo mein yahi sabse strong moment hai.
 *
 *  KYUN BOTTOM SHEET, alag screen nahi: SOS jaldi mein bheja jaata hai. Navigation,
 *  back stack, screen transition — ye sab beech mein aane wali rukavatein hain.
 *  Sheet upar aata hai, do line likho, bhejo, khatam.
 * =====================================================================================
 */
@OptIn(ExperimentalPermissionsApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SosSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sosState by vm.sosState.collectAsStateSafe()

    var message by remember { mutableStateOf("") }
    var showEmptyError by remember { mutableStateOf(false) }

    /**
     * Location permission.
     * KYUN COARSE, FINE nahi: rescue team ko "kaunsa ghar" nahi, "kaunsa mohalla" chahiye —
     * coarse (~1-2 km) uske liye kaafi hai, aur user ise dene mein zyada comfortable hota
     * hai. Kam permission maangna hamesha behtar hai.
     */
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Bhejne ke baad sheet apne aap band — user ko OK dabane ki zaroorat nahi.
    LaunchedEffect(sosState) {
        if (sosState is MainViewModel.SosState.Sent) {
            kotlinx.coroutines.delay(1600)
            vm.resetSos()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { vm.resetSos(); onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
        ) {
            when (val st = sosState) {
                // ---------- bhej diya ----------
                is MainViewModel.SosState.Sent -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Outlined.CheckCircle, null,
                            tint = riskColor("green"),
                            modifier = Modifier.size(28.dp),
                        )
                        AppText(s.sosSent, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // ---------- form ----------
                else -> {
                    // Explicit close — drag-to-dismiss discoverable nahi hai, khaas kar
                    // us user ke liye jo pehli baar smartphone use kar raha ho.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            s.sosTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(com.blackbox.jalrakshak.ui.theme.Neutral)
                                .clickable { vm.resetSos(); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Close, null,
                                tint = com.blackbox.jalrakshak.ui.theme.Ink,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AppText(
                        s.sosSub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it; showEmptyError = false },
                        placeholder = { AppText(s.sosMessageHint) },
                        minLines = 3,
                        maxLines = 5,
                        isError = showEmptyError,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = st !is MainViewModel.SosState.Sending,
                    )

                    if (showEmptyError) {
                        AppText(
                            s.sosEmptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Location ka status — SAAF likha hai. Agar permission nahi hai to
                    // hum chup-chaap gaon ka lat/lng bhejte hain, aur citizen ko ye pata
                    // hona chahiye (warna wo samjhega rescue seedha uske paas aayega).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val granted = locationPermission.status.isGranted
                        Icon(
                            if (granted) Icons.Outlined.LocationOn else Icons.Outlined.LocationOff,
                            null,
                            tint = if (granted) riskColor("green") else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        AppText(
                            if (granted) s.locationAttached else s.locationMissing,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!granted) {
                            TextButton(onClick = { locationPermission.launchPermissionRequest() }) {
                                AppText("GPS")
                            }
                        }
                    }

                    if (st is MainViewModel.SosState.Failed) {
                        Spacer(Modifier.height(8.dp))
                        AppText(
                            "${s.sosFailed}${if (st.message.isNotBlank()) " — ${st.message}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (message.isBlank()) {
                                showEmptyError = true
                            } else {
                                vm.sendSos(message.trim(), locationPermission.status.isGranted)
                            }
                        },
                        enabled = st !is MainViewModel.SosState.Sending,
                        colors = ButtonDefaults.buttonColors(containerColor = riskColor("red")),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (st is MainViewModel.SosState.Sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.height(0.dp))
                            AppText("  ${s.sosSending}", fontWeight = FontWeight.SemiBold)
                        } else {
                            AppText(s.sosSend, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
