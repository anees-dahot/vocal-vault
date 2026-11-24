package com.vocalvault.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vocalvault.app.ui.theme.Accent
import com.vocalvault.app.ui.theme.Bg
import com.vocalvault.app.ui.theme.OnAccent
import com.vocalvault.app.ui.theme.Surface2
import com.vocalvault.app.ui.theme.TextHi
import com.vocalvault.app.ui.theme.TextMid
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardPage(val icon: ImageVector, val colors: List<Color>, val title: String, val body: String)

private val pages = listOf(
    OnboardPage(Icons.Rounded.Search, listOf(Color(0xFFFA2D48), Color(0xFFFF8A65)), "Any song, just the voice", "Type a song or artist. Vocal Vault finds the acapella or isolated vocal for you."),
    OnboardPage(Icons.Rounded.GraphicEq, listOf(Color(0xFF5B3CFF), Color(0xFF29B6F6)), "Practice your way", "Slow it down without changing key, shift to your range, and loop the hard line until it sticks."),
    OnboardPage(Icons.Rounded.Mic, listOf(Color(0xFF0E9F6E), Color(0xFFB2F5A8)), "Hear yourself", "Record a take over the vocal and play both back together. Synced lyrics keep you on time."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    LaunchedEffect(pager.currentPage) { haptics.tick() }
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text(if (last) "" else "Skip", color = TextMid) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            val offset = (pager.currentPage - index + pager.currentPageOffsetFraction).absoluteValue
            Column(
                Modifier.fillMaxSize().padding(horizontal = 32.dp).graphicsLayer {
                    alpha = 1f - offset.coerceIn(0f, 1f) * 0.6f
                    val scale = 1f - offset.coerceIn(0f, 1f) * 0.12f
                    scaleX = scale; scaleY = scale
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(220.dp).clip(RoundedCornerShape(56.dp)).background(Brush.linearGradient(page.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(132.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(page.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
                    }
                }
                Spacer(Modifier.height(44.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, color = TextHi, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(page.body, style = MaterialTheme.typography.bodyLarge, color = TextMid, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val active = i == pager.currentPage
                val w by animateDpAsState(if (active) 24.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (active) Accent else Surface2, label = "dotc")
                Box(Modifier.padding(horizontal = 4.dp).width(w).height(8.dp).clip(CircleShape).background(c))
            }
        }
        Box(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().height(56.dp)
                .bouncyClick {
                    haptics.confirm()
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
                .clip(RoundedCornerShape(28.dp)).background(Accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (last) "Get started" else "Continue", style = MaterialTheme.typography.titleMedium, color = OnAccent)
        }
    }
}
