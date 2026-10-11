package com.concept.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.concept.music.data.kugou.*

@Composable
fun VipCard(state: VipUiState, loggedIn: Boolean, autoClaim: Boolean, onClaim: () -> Unit,
    onAutoChange: (Boolean) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("概念版每日权益", style = MaterialTheme.typography.titleLarge)
            if (!loggedIn) Text("登录酷狗账号后，领取并查看你的会员权益")
            else {
                if (!state.snapshot.checked) Text("会员状态尚未获取")
                state.snapshot.benefits.forEach { Text(it.display(), style = MaterialTheme.typography.bodyMedium) }
                Text(if (state.fresh) "已从酷狗刷新" else "尚未完整刷新，请以最新查询为准", style = MaterialTheme.typography.labelMedium)
                val record = state.records.find { it.day == today() }
                Text(when {
                    record?.received != true -> "今日领取：${if (state.fresh) "未发现领取记录" else "待查询"}"
                    record.type == "svip" -> "今日领取记录：概念版权益"
                    else -> "今日领取记录：畅听权益，可继续升级"
                })
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.result?.let { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onClaim, enabled = !state.busy) { Text(if (loggedIn) "一键领取今日权益" else "登录领取") }
                if (loggedIn) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("刷新") }
            }
            if (loggedIn) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("打开应用时自动领取")
                    Text("仅当前账号 · 每日检查 · 不在后台运行", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = autoClaim, onCheckedChange = onAutoChange)
            }
        }
    }
}
