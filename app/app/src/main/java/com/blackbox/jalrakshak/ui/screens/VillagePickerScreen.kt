package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.components.AppText
import com.blackbox.jalrakshak.ui.components.EmptyState
import com.blackbox.jalrakshak.ui.theme.CardBg
import com.blackbox.jalrakshak.ui.theme.Green
import com.blackbox.jalrakshak.ui.theme.Ink
import com.blackbox.jalrakshak.ui.theme.Ink2
import com.blackbox.jalrakshak.ui.theme.Ink3
import com.blackbox.jalrakshak.ui.theme.Line
import com.blackbox.jalrakshak.ui.theme.Neutral

/**
 * VillagePickerScreen — app_mockup.html ka "Village select" screen.
 *
 * Mockup: bada `.h1` heading, `.sub` ek line, `.search` field, `.list` mein `.vr` rows
 * (naam + district + right side pe check circle), aur neeche full-width dark `.btn`.
 *
 * FUNCTIONALITY WAISE KI WAISE:
 *  - list `GET /api/villages` se aati hai
 *  - search naam aur district dono pe filter karta hai
 *  - gaon chunte hi FCM token us gaon ke liye register hota hai (MainViewModel)
 *
 * Ek behaviour farq mockup ke hisaab se: pehle row tap karte hi screen band ho jaati thi.
 * Ab mockup ki tarah row select hoti hai (green check dikhta hai) aur "Continue" dabane pe
 * aage badhte hain. Ye design ka hissa hai — user apni choice confirm kar paata hai.
 */
@Composable
fun VillagePickerScreen(
    vm: MainViewModel,
    onSelected: () -> Unit,
    /**
     * null = pehli launch (peeche jaane ki koi jagah nahi, gaon chunna zaroori hai).
     * non-null = user ne "gaon badlein" dabaya — tab back button dikhta hai taaki
     * wo bina badle wapas ja sake. Pehle yahan se nikalne ka koi raasta hi nahi tha.
     */
    onBack: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    val villages by vm.villageList.collectAsStateSafe()
    val error by vm.villageListError.collectAsStateSafe()
    val currentId by vm.villageId.collectAsStateSafe()

    var query by remember { mutableStateOf("") }
    // Pehle se chuna hua gaon (agar "gaon badlein" se aaye hain) highlight rehta hai.
    var picked by remember(currentId) { mutableStateOf(currentId) }

    LaunchedEffect(Unit) { vm.loadVillageList() }

    val filtered = remember(villages, query) {
        if (query.isBlank()) villages
        else villages.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.district.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))

        if (onBack != null) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Neutral)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ArrowBack, null, tint = Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(14.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }

        AppText(s.chooseVillage, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        AppText(s.chooseVillageSub, style = MaterialTheme.typography.bodyLarge, color = Ink2)
        Spacer(Modifier.height(22.dp))

        // ---------- .search ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg, RoundedCornerShape(12.dp))
                .border(1.dp, Line, RoundedCornerShape(12.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = Ink3, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    AppText(s.searchVillage, style = MaterialTheme.typography.titleMedium, color = Ink3)
                }
                // BasicTextField isliye ki mockup ka field bilkul flat hai — Material
                // TextField apna background, underline aur label le aata hai.
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                    cursorBrush = SolidColor(Ink),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            error != null && villages.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(Icons.Outlined.WifiOff, s.offlineNoData, error.orEmpty())
                Spacer(Modifier.height(12.dp))
                DarkButton(text = s.retry, showArrow = false) { vm.loadVillageList() }
            }

            villages.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }

            filtered.isEmpty() -> Box(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center,
            ) { AppText(s.noVillageFound, style = MaterialTheme.typography.bodyLarge, color = Ink2) }

            else -> {
                // Rows ek "card" jaise dikhein isliye pehli/aakhri row ke corners round
                // hote hain aur beech mein 1px divider — mockup ka `.list` + `.vr`.
                // LazyColumn isliye ki 30+ gaon scroll karne padte hain.
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(filtered, key = { _, v -> v.id }) { i, v ->
                        VillageRow(
                            name = v.name,
                            district = v.district,
                            selected = picked == v.id,
                            first = i == 0,
                            last = i == filtered.lastIndex,
                            onClick = { picked = v.id },
                        )
                    }
                }

                // List aur button ke beech saans lene ki jagah — warna aakhri row
                // button se chipki hui dikhti hai.
                Spacer(Modifier.height(4.dp))
                DarkButton(text = s.confirm, showArrow = true, enabled = picked != null) {
                    val v = villages.firstOrNull { it.id == picked }
                    if (v != null) {
                        vm.selectVillage(v.id, v.name)
                        onSelected()
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Mockup ka `.vr` row — naam + district, right pe check circle. */
@Composable
private fun VillageRow(
    name: String,
    district: String,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
    )
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
        Row(
            Modifier
                .fillMaxWidth()
                .background(CardBg, shape)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                AppText(district, style = MaterialTheme.typography.bodySmall, color = Ink2)
            }
            if (selected) {
                Icon(
                    Icons.Filled.CheckCircle, null,
                    tint = Green, modifier = Modifier.size(22.dp),
                )
            } else {
                // Khaali circle — mockup ka un-selected `.ck`
                Box(
                    Modifier
                        .size(22.dp)
                        .border(2.dp, Line, CircleShape),
                )
            }
        }
    }
}

/** Mockup ka `.btn.dark` — full width, 12dp radius, flat, no shadow. */
@Composable
private fun DarkButton(
    text: String,
    showArrow: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(vertical = 15.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AppText(text, style = MaterialTheme.typography.labelLarge)
        if (showArrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}
