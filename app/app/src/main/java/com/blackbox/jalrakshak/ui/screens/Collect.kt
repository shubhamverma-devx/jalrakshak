package com.blackbox.jalrakshak.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * collectAsStateSafe() — `collectAsStateWithLifecycle()` ka chhota naam.
 *
 * KYUN lifecycle-aware collect (plain collectAsState nahi):
 *  App background mein jaaye to plain collectAsState flow sunta rehta hai — matlab
 *  Room queries aur recomposition chalte rehte hain jabki screen dikh hi nahi rahi.
 *  Battery jalti hai. Lifecycle wala version app background jaate hi collect rok deta
 *  hai aur wapas aane pe shuru. Flood app phone mein hafton padi rehti hai — ye farq
 *  matter karta hai.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateSafe(): State<T> = collectAsStateWithLifecycle()
