package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 好友统计二级页:能量统计 + 好友收取明细(周收/总收,PK数据)。
 * 数据由支付宝进程自动同步:周一至周六每天一次,周日每6小时一次且23点按手机时间准点强制同步;本页支持手动刷新。
 */
class MiuixFriendStatsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            FriendStatsScreen(this)
        }
    }

    /** 发送广播触发支付宝进程逐个好友执行 queryPKRecord 抓取。 */
    fun sendRefreshBroadcast() {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
        intent.putExtra("type", "antForest")
        intent.putExtra("method", "queryFriendRanking")
        sendBroadcast(intent)
    }
}

@Composable
fun FriendStatsScreen(activity: MiuixFriendStatsActivity) {
    // 好友收取明细数据(读取同步生成的文件)
    var rankingData by remember { mutableStateOf(loadRankingData()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshBaseTime by remember { mutableStateOf(0L) }
    var showExcludeDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 手动刷新轮询:逐个好友抓取耗时较长,轮询文件 updateTime 变化直至完成
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            val deadline = System.currentTimeMillis() + 300_000L
            while (System.currentTimeMillis() < deadline) {
                delay(3000)
                val loaded = loadRankingData()
                if (loaded != null && loaded.updateTime > refreshBaseTime) {
                    rankingData = loaded
                    break
                }
            }
            isRefreshing = false
        }
    }

    // 页面打开期间每分钟重读一次文件,自动同步完成后也能及时展示
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            rankingData = loadRankingData()
        }
    }

    // 排除关键词(空格分隔);保存后立即过滤已加载数据,无需重新抓取
    var excludeKeywords by remember { mutableStateOf(parseExcludeKeywords(loadExcludeKeywordsText())) }
    // 按关键词过滤后的展示数据(明细列表与全部汇总即时生效)
    val displayData = remember(rankingData, excludeKeywords) {
        applyExclusion(rankingData, excludeKeywords)
    }

    // 进页面时若数据已过期(工作日超24h/周日超6h/从未同步),自动静默补刷一次;
    // 周日23点强制同步由模块内定时调度器按手机时间准点执行,不依赖页面
    LaunchedEffect(Unit) {
        val lastSync = rankingData?.updateTime ?: 0L
        if (isFriendStatsDue(lastSync)) {
            refreshBaseTime = lastSync
            activity.sendRefreshBroadcast()
            isRefreshing = true
        }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "好友统计",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 能量统计汇总卡片固定在列表外部,保证列表内全部为等高明细行,
            // 使滑块的索引进度与滚动进度严格线性
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                StatsSummaryCard(displayData)
            }

            // 好友收取明细标题 + 排除/手动刷新按钮(固定顶部,不随列表滚动)
            CollectDetailHeader(
                rankingData = displayData,
                isRefreshing = isRefreshing,
                onRefresh = {
                    refreshBaseTime = rankingData?.updateTime ?: 0L
                    activity.sendRefreshBroadcast()
                    isRefreshing = true
                },
                onExclude = { showExcludeDialog = true }
            )

            if (showExcludeDialog) {
                ExcludeKeywordsDialog(
                    initial = loadExcludeKeywordsText(),
                    onSave = {
                        saveExcludeKeywordsText(it)
                        // 关键词立即作用于已加载的列表与汇总,无需重新抓取
                        excludeKeywords = parseExcludeKeywords(it)
                        ToastUtil.show(activity, "已保存并生效")
                        showExcludeDialog = false
                    },
                    onDismiss = { showExcludeDialog = false }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    // 好友收取明细列表(PK数据,已按周收→总收→开始统计时间排序,应用排除过滤)
                    val friendList = displayData?.friends ?: emptyList()
                    if (friendList.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRefreshing) "(刷新中,请稍候...)" else "(暂无数据,等待同步或已被全部排除)",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = friendList,
                            key = { _, item -> item.userId }
                        ) { index, friend ->
                            FriendDetailItem(index + 1, friend)
                        }
                    }
                }

                // 快速翻页滑块(列表可滚动时显示)
                val layoutInfo = listState.layoutInfo
                val scrollable = layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size + 5
                if (scrollable) {
                    VerticalScrollSlider(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

/** 判断好友收取明细数据是否已过期:周一至周六超24h,周日超6h;从未同步也算过期。周日23点强制同步由模块定时调度器负责。 */
private fun isFriendStatsDue(lastSync: Long): Boolean {
    val now = System.currentTimeMillis()
    if (lastSync <= 0L) return true
    val cal = java.util.Calendar.getInstance()
    val sunday = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY
    val interval = if (sunday) 6 * 60 * 60 * 1000L else 24 * 60 * 60 * 1000L
    return now - lastSync >= interval
}

/** 读取 friendRanking.json,解析好友收取明细数据(周收/总收/开始统计时间)。 */
private fun loadRankingData(): RankingData? {
    return try {
        val file = File(FileUtil.MAIN_DIRECTORY_FILE, "friendRanking.json")
        if (!file.exists()) return null
        val json = FileUtil.readFromFile(file)
        if (json.isEmpty()) return null
        val jo = JSONObject(json)
        val friends = mutableListOf<FriendRankInfo>()
        val arr = jo.optJSONArray("friends")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                friends.add(
                    FriendRankInfo(
                        userId = f.optString("userId", i.toString()),
                        name = f.optString("name", f.optString("userId")),
                        weekEnergy = f.optLong("weekEnergy", 0),
                        totalEnergy = f.optLong("totalEnergy", 0),
                        monthEnergy = f.optLong("monthEnergy", 0),
                        yearEnergy = f.optLong("yearEnergy", 0),
                        // startTime 已由模块端格式化为 yyyy年MM月dd日HH:mm:ss.SSS 字符串,UI 仅展示年月日
                        firstSeenText = f.optString("startTime", "").takeIf { it.isNotEmpty() }
                            ?.let { "（开始统计时间:${it.take(11)}）" } ?: ""
                    )
                )
            }
        }
        RankingData(
            total = jo.optInt("total", 0),
            weekSum = jo.optLong("weekSum", 0),
            monthSum = jo.optLong("monthSum", 0),
            yearSum = jo.optLong("yearSum", 0),
            totalSum = jo.optLong("totalSum", 0),
            updateTime = jo.optLong("updateTime", 0),
            friends = friends
        )
    } catch (e: Exception) {
        null
    }
}

/** 好友收取明细数据。 */
data class RankingData(
    val total: Int,
    val weekSum: Long,
    val monthSum: Long,
    val yearSum: Long,
    val totalSum: Long,
    val updateTime: Long,
    val friends: List<FriendRankInfo>
)

data class FriendRankInfo(
    val userId: String,
    val name: String,
    val weekEnergy: Long,
    val totalEnergy: Long,
    val monthEnergy: Long,
    val yearEnergy: Long,
    val firstSeenText: String
)

/** 好友收取明细标题:排除/手动刷新按钮 + 本周/累计汇总 + 最近同步时间。 */
@Composable
private fun CollectDetailHeader(
    rankingData: RankingData?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onExclude: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 4.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "好友收取明细",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "排除",
                    onClick = onExclude
                )
                TextButton(
                    text = if (isRefreshing) "刷新中..." else "刷新数据",
                    onClick = { if (!isRefreshing) onRefresh() }
                )
            }
        }
        if (rankingData == null) {
            Text(
                text = "数据每天自动同步一次,周日每6小时一次,周日23点后强制同步",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        } else {
            val timeStr = if (rankingData.updateTime > 0) {
                " · 更新于 " + SimpleDateFormat(
                    "MM-dd HH:mm", Locale.getDefault()
                ).format(Date(rankingData.updateTime))
            } else ""
            Text(
                text = "本周共收 ${rankingData.weekSum}g · 累计共收 ${rankingData.totalSum}g$timeStr",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/** 排除关键词文件:名字包含任一关键词的好友不计入明细与汇总(UI即时过滤+模块同步时过滤)。 */
private val excludeKeywordsFile by lazy { File(FileUtil.MAIN_DIRECTORY_FILE, "friendStatsExclude.txt") }

/** 读取排除关键词原文(空格分隔)。 */
private fun loadExcludeKeywordsText(): String {
    return if (excludeKeywordsFile.exists()) FileUtil.readFromFile(excludeKeywordsFile) else ""
}

/** 保存排除关键词原文。 */
private fun saveExcludeKeywordsText(text: String) {
    FileUtil.write2File(text, excludeKeywordsFile)
}

/** 解析排除关键词原文:空格分隔(兼容逗号/顿号),不区分大小写。 */
private fun parseExcludeKeywords(text: String): List<String> {
    return text.split(Regex("[\\s,，、]+"))
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
}

/**
 * 按关键词过滤明细数据并重算全部汇总,保存关键词后列表立即生效,无需重新抓取。
 */
private fun applyExclusion(data: RankingData?, keywords: List<String>): RankingData? {
    if (data == null || keywords.isEmpty()) return data
    val filtered = data.friends.filter { friend ->
        val lower = friend.name.lowercase()
        keywords.none { lower.contains(it) }
    }
    return data.copy(
        total = filtered.size,
        weekSum = filtered.sumOf { it.weekEnergy },
        monthSum = filtered.sumOf { it.monthEnergy },
        yearSum = filtered.sumOf { it.yearEnergy },
        totalSum = filtered.sumOf { it.totalEnergy },
        friends = filtered
    )
}

/** 排除关键词编辑对话框:多个关键词用空格分隔,保存后列表与汇总立即生效。 */
@Composable
private fun ExcludeKeywordsDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Text(
                text = "排除好友",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "好友名字包含任一关键词即不计入明细与汇总,多个关键词用空格分隔,保存后立即生效",
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                label = "关键词,如: 代拍 机器人",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(text = "取消", onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                TextButton(text = "保存", onClick = { onSave(text) })
            }
        }
    }
}

/** 好友收取明细行:左侧序号,第一行 名字（开始统计时间:yyyy年MM月dd日）,第二行 周收/总收,全部左对齐。 */
@Composable
private fun FriendDetailItem(no: Int, friend: FriendRankInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧序号
        Text(
            text = no.toString(),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // 第一行:名字 + 开始统计时间
            Text(
                text = friend.name + friend.firstSeenText,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            // 第二行:周收/总收
            Text(
                text = "周收: ${friend.weekEnergy}g · 总收: ${friend.totalEnergy}g",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}

/**
 * 垂直快速翻页滑块。
 * 拖拽中:滑块位置直接按手指位置渲染(瞬时跟手,不等待列表布局);
 * 列表滚动协程逐次取消旧任务只保留最新目标,避免事件堆积导致卡顿;
 * 松手后:滑块恢复按列表真实滚动位置渲染。
 * 手指拖到轨道底部 = scrollToItem(最后一项) = 列表真正到底。
 * 轨道底部留出导航栏/圆角区域,无需触到屏幕底边即可拖到滑块底部。
 */
@Composable
private fun VerticalScrollSlider(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { 48.dp.toPx() }

    // 拖拽状态:dragFraction 为手指在轨道上的进度,拖拽中用它直接渲染滑块(跟手)
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    // 列表滚动任务:每次只保留最新一个,旧任务取消,防止协程堆积
    var scrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    // 列表内全部为等高明细行:实际可达的最大 firstVisibleItemIndex = 总项数 - 可见项数
    val denom = (totalItems - layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
    // 非拖拽时:显示进度带小数部分(首项内偏移/首项高度),手动滚动列表时滑块连续移动
    val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
    val itemHeight = firstItem?.size?.toFloat() ?: 0f
    val listFraction = if (firstItem != null && itemHeight > 0f) {
        ((listState.firstVisibleItemIndex +
                listState.firstVisibleItemScrollOffset.toFloat() / itemHeight) / denom)
            .coerceIn(0f, 1f)
    } else {
        0f
    }

    val maxThumbOffset = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
    // 拖拽中跟手渲染,非拖拽跟随列表位置
    val thumbOffset = (if (dragging) dragFraction else listFraction) * maxThumbOffset

    // 按进度滚动列表:小数部分换算为行内偏移,定位更平滑;旧滚动任务取消
    fun scrollToFraction(frac: Float) {
        val curLayout = listState.layoutInfo
        val curTotal = curLayout.totalItemsCount
        if (curTotal <= 0) return
        val curDenom = (curTotal - curLayout.visibleItemsInfo.size).coerceAtLeast(1)
        scrollJob?.cancel()
        scrollJob = scope.launch {
            if (frac >= 0.999f) {
                // 滑块到底:直接定位列表末尾,scrollToItem 自动钳制到真实底部
                listState.scrollToItem(curTotal - 1)
            } else {
                val exact = frac.coerceIn(0f, 1f) * curDenom
                val index = exact.toInt().coerceIn(0, curDenom)
                val h = curLayout.visibleItemsInfo.firstOrNull()?.size ?: 0
                val innerOffset = if (h > 0) {
                    ((exact - index) * h).toInt().coerceIn(0, h - 1)
                } else 0
                listState.scrollToItem(index, innerOffset)
            }
        }
    }

    Box(
        modifier = modifier
            .width(40.dp)
            .fillMaxHeight()
            // 底部留出圆角/导航栏区域,顶部留少量边距,拖到底无需触屏幕边缘
            .padding(top = 8.dp, bottom = 88.dp, end = 4.dp)
            .onGloballyPositioned { trackHeight = it.size.height.toFloat() },
        contentAlignment = Alignment.TopEnd
    ) {
        // 拖拽响应区域(加宽到40dp更易触摸):手指绝对位置 → 进度
        Box(
            Modifier
                .width(40.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            val max = (trackHeight - thumbHeightPx).coerceAtLeast(1f)
                            val frac = ((offset.y - thumbHeightPx / 2f)
                                .coerceIn(0f, max)) / max
                            dragFraction = frac
                            scrollToFraction(frac)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val max = (trackHeight - thumbHeightPx).coerceAtLeast(1f)
                            val frac = ((change.position.y - thumbHeightPx / 2f)
                                .coerceIn(0f, max)) / max
                            dragFraction = frac
                            scrollToFraction(frac)
                        }
                    )
                }
        )
        // 轨道(右移8dp,与20dp宽的滑块水平居中对齐)
        Box(
            Modifier
                .padding(end = 8.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.2f))
        )
        // 滑块
        Box(
            Modifier
                .offset { IntOffset(0, thumbOffset.toInt()) }
                .width(20.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.7f))
        )
    }
}

/** 能量统计汇总卡片:按好友收取明细求和,展示本年/本月/本周收取。 */
@Composable
private fun StatsSummaryCard(rankingData: RankingData?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        Text(
            text = "能量统计",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))

        if (rankingData == null) {
            Text(
                text = "(暂无数据,等待同步)",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            return@Column
        }

        EnergyRow("本年", rankingData.yearSum)
        EnergyRow("本月", rankingData.monthSum)
        EnergyRow("本周", rankingData.weekSum)
    }
}

/** 一行能量统计:标签 + 收取克数(按好友收取明细求和)。 */
@Composable
private fun EnergyRow(label: String, collected: Long) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = "收取:${collected}g",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onBackground
        )
    }
}
