package com.blackbox.jalrakshak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackbox.jalrakshak.MainViewModel
import com.blackbox.jalrakshak.core.LocalStrings
import com.blackbox.jalrakshak.ui.components.CenterBox
import com.blackbox.jalrakshak.ui.components.EmptyState

/**
 * VillagePickerScreen — pehli launch pe: "apna gaon chuno".
 *
 * DATA: GET /api/villages
 *
 * KYUN YE PEHLI SCREEN HAI: poore product ka differentiator VILLAGE-LEVEL targeting hai.
 * Govt ka SMS poore zile ko jaata hai; yahan citizen apna gaon chunta hai aur usse SIRF
 * usi gaon ka risk aur alert milta hai. Gaon chune bina app ka koi matlab hi nahi —
 * isliye ye blocking pehla kadam hai.
 *
 * Gaon baad mein badla ja sakta hai (home screen ka "गाँव बदलें").
 */
@Composable
fun VillagePickerScreen(vm: MainViewModel, onSelected: () -> Unit) {
    val s = LocalStrings.current
    val villages by vm.villageList.collectAsStateSafe()
    val error by vm.villageListError.collectAsStateSafe()

    var query by remember { mutableStateOf("") }

    // Screen khulte hi list laao.
    LaunchedEffect(Unit) { vm.loadVillageList() }

    // Search filter — 30 gaon mein bhi scroll karna zyada hai, aur asli deployment mein
    // hazaron honge. Naam aur district dono pe match karte hain.
    val filtered = remember(villages, query) {
        if (query.isBlank()) villages
        else villages.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.district.contains(query, ignoreCase = true)
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(24.dp))

        Text(s.chooseVillage, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            s.chooseVillageSub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(s.searchVillage) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        when {
            // Network fail — gaon ki list ke bina aage badh hi nahi sakte, isliye
            // yahan retry dena zaroori hai (offline cache is screen pe possible nahi).
            error != null && villages.isEmpty() -> CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Outlined.WifiOff, s.offlineNoData, error.orEmpty())
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.loadVillageList() }) { Text(s.retry) }
                }
            }

            villages.isEmpty() -> CenterBox { CircularProgressIndicator() }

            filtered.isEmpty() -> CenterBox {
                Text(s.noVillageFound, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn {
                items(filtered, key = { it.id }) { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.selectVillage(v.id, v.name)
                                onSelected()
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(7.dp)
                                .size(20.dp),
                        )
                        Column {
                            Text(
                                v.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                v.district,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
