package com.hkbuscal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hkbuscal.app.data.local.CommuteRuleEntity
import com.hkbuscal.app.domain.BusOperator
import com.hkbuscal.app.domain.EventCommuteSuggestion
import com.hkbuscal.app.domain.UnifiedBusEta
import com.hkbuscal.app.ui.theme.CitybusNavy
import com.hkbuscal.app.ui.theme.CitybusYellow
import com.hkbuscal.app.ui.theme.KmbRed
import com.hkbuscal.app.ui.theme.LwbOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val rules by viewModel.rules.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val useTc = state.settings.useTraditionalChinese

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (useTc) "巴士日程實時到站" else "HK Bus Calendar ETA",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.sendTestNotification() }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Notify")
                    }
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CalendarMonth, null) },
                    label = { Text(if (useTc) "日程班次" else "Commute") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.DirectionsBus, null) },
                    label = { Text(if (useTc) "路線規則" else "Routes") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(if (useTc) "設定" else "Settings") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> CommuteDashboardTab(
                    state = state,
                    useTc = useTc,
                    onRequestPermissions = onRequestPermissions,
                    onRefresh = { viewModel.refreshAll() }
                )
                1 -> RouteRulesTab(
                    rules = rules,
                    useTc = useTc,
                    onAddRule = { kw, op, route, dir -> viewModel.addRule(kw, op, route, dir) },
                    onDeleteRule = { viewModel.deleteRule(it) }
                )
                2 -> SettingsTab(
                    state = state,
                    onUpdateSettings = { viewModel.updateSettings(it) },
                    onTestNotification = { viewModel.sendTestNotification() }
                )
            }

            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun CommuteDashboardTab(
    state: MainUiState,
    useTc: Boolean,
    onRequestPermissions: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            state.currentLocation?.let { loc ->
                Text(
                    text = if (useTc) {
                        "目前位置：%.4f, %.4f (搜尋半徑 %dm)".format(
                            loc.latitude, loc.longitude, state.settings.searchRadiusMeters
                        )
                    } else {
                        "Current GPS: %.4f, %.4f (Radius %dm)".format(
                            loc.latitude, loc.longitude, state.settings.searchRadiusMeters
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Button(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
                Text(if (useTc) "授權日曆、定位及通知權限" else "Grant Calendar, GPS & Notification Permissions")
            }
        }

        state.statusMessage?.let { msg ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(msg, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (state.suggestions.isNotEmpty()) {
            item {
                Text(
                    text = if (useTc) "即將開始的日曆活動" else "Upcoming Calendar Events",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(state.suggestions, key = { "${it.event.id}-${it.event.startTimeMillis}" }) { suggestion ->
                EventCommuteCard(suggestion = suggestion, useTc = useTc)
            }
        }

        if (state.nearbyEtas.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (useTc) "附近實時巴士到站 (九巴 / 龍運)" else "Nearby Live Bus Arrivals (KMB / LWB)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(state.nearbyEtas.take(12)) { eta ->
                EtaRowItem(eta = eta, useTc = useTc)
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text(if (useTc) "立即重新整理實時數據" else "Refresh Live Data Now")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EventCommuteCard(suggestion: EventCommuteSuggestion, useTc: Boolean) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val timeText = Instant.ofEpochMilli(suggestion.event.startTimeMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$timeText  ${suggestion.event.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (useTc) "${suggestion.event.minutesUntilStart} 分鐘後" else "in ${suggestion.event.minutesUntilStart}m",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (suggestion.event.location.isNotBlank()) {
                Text(
                    text = "📍 ${suggestion.event.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = suggestion.statusSummary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            suggestion.recommendedEtas.take(3).forEach { eta ->
                EtaRowItem(eta = eta, useTc = useTc)
            }
        }
    }
}

@Composable
private fun EtaRowItem(eta: UnifiedBusEta, useTc: Boolean) {
    val badgeBg = when (eta.operator) {
        BusOperator.KMB -> KmbRed
        BusOperator.LWB -> LwbOrange
        BusOperator.CTB -> CitybusYellow
    }
    val badgeFg = if (eta.operator == BusOperator.CTB) CitybusNavy else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${if (useTc) eta.operator.labelTc else eta.operator.labelEn} ${eta.route}",
                color = badgeFg,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "→ ${if (useTc) eta.destinationTc else eta.destinationEn}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${if (useTc) eta.stopNameTc else eta.stopNameEn} • ${eta.distanceMeters.toInt()}m (${eta.walkingMinutes}m walk)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val etaLabel = when (val m = eta.etaMinutes) {
                null -> "--"
                in Long.MIN_VALUE..0L -> if (useTc) "即將到站" else "Due"
                else -> if (useTc) "$m 分鐘" else "$m min"
            }
            Text(
                text = etaLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            val rmk = if (useTc) eta.remarkTc else eta.remarkEn
            if (rmk.isNotBlank()) {
                Text(rmk, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun RouteRulesTab(
    rules: List<CommuteRuleEntity>,
    useTc: Boolean,
    onAddRule: (String, String, String, String) -> Unit,
    onDeleteRule: (CommuteRuleEntity) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("KMB") }
    var direction by remember { mutableStateOf("both") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = if (useTc) "新增日曆路線配對規則" else "Add Calendar-to-Bus Route Rule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text(if (useTc) "日曆地點/標題關鍵字 (留空 = 所有活動)" else "Calendar Keyword (blank = all events)") },
                placeholder = { Text("e.g., PolyU, 尖沙咀, Airport") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = route,
                onValueChange = { route = it },
                label = { Text(if (useTc) "巴士路線編號" else "Bus Route Number") },
                placeholder = { Text("e.g., 1A, A21, E23, 970X") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("KMB" to "九巴 KMB", "LWB" to "龍運 LWB", "CTB" to "城巴 CTB").forEach { (code, label) ->
                    FilterChip(
                        selected = operator == code,
                        onClick = { operator = code },
                        label = { Text(label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("both" to "雙向 Both", "outbound" to "去程 Out", "inbound" to "回程 In").forEach { (d, label) ->
                    FilterChip(
                        selected = direction == d,
                        onClick = { direction = d },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (route.isNotBlank()) {
                        onAddRule(keyword, operator, route, direction)
                        keyword = ""
                        route = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(if (useTc) "加入監察路線" else "Save Commute Rule")
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (useTc) "已儲存規則 (${rules.size})" else "Saved Rules (${rules.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(rules, key = { it.id }) { rule ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${rule.operatorCode} ${rule.route} (${rule.direction})",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (rule.keyword == "*") {
                                if (useTc) "套用於所有即將開始的活動" else "Applies to all upcoming events"
                            } else {
                                if (useTc) "關鍵字：${rule.keyword}" else "Keyword: ${rule.keyword}"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { onDeleteRule(rule) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: MainUiState,
    onUpdateSettings: ((com.hkbuscal.app.domain.AppSettings) -> com.hkbuscal.app.domain.AppSettings) -> Unit,
    onTestNotification: () -> Unit
) {
    val s = state.settings
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (s.useTraditionalChinese) "通知與定位設定" else "Notification & GPS Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("繁體中文介面 (Traditional Chinese)")
            Switch(
                checked = s.useTraditionalChinese,
                onCheckedChange = { checked -> onUpdateSettings { it.copy(useTraditionalChinese = checked) } }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (s.useTraditionalChinese) "背景自動監察日曆通知" else "Background Calendar Monitor"
            )
            Switch(
                checked = s.backgroundMonitorEnabled,
                onCheckedChange = { checked -> onUpdateSettings { it.copy(backgroundMonitorEnabled = checked) } }
            )
        }

        Column {
            Text(
                if (s.useTraditionalChinese) "活動前提前通知時間：${s.leadTimeMinutes} 分鐘"
                else "Notify before event: ${s.leadTimeMinutes} min"
            )
            Slider(
                value = s.leadTimeMinutes.toFloat(),
                onValueChange = { v -> onUpdateSettings { it.copy(leadTimeMinutes = v.toInt()) } },
                valueRange = 15f..120f,
                steps = 6
            )
        }

        Column {
            Text(
                if (s.useTraditionalChinese) "附近巴士站搜尋半徑：${s.searchRadiusMeters} 米"
                else "Nearby stop search radius: ${s.searchRadiusMeters} m"
            )
            Slider(
                value = s.searchRadiusMeters.toFloat(),
                onValueChange = { v -> onUpdateSettings { it.copy(searchRadiusMeters = v.toInt()) } },
                valueRange = 150f..1000f,
                steps = 16
            )
        }

        Button(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.NotificationsActive, null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (s.useTraditionalChinese) "立即發送測試通知" else "Send Test Commute Notification Now"
            )
        }
    }
}
