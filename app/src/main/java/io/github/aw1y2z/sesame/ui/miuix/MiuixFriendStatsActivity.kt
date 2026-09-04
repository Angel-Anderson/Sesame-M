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
import io.github.aw1y2z.sesame.util.FileUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 好友统计二级页:能量统计 + 好友收取明细(周收/总收,PK数据)。
 * 数据由支付宝进程每4小时自动同步一次(周日23点后强制同步),本页支持手动刷新。
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
                StatsSummaryCard(rankingData)
            }

            // 好友收取明细标题 + 手动刷新按钮(固定顶部,不随列表滚动)
            CollectDetailHeader(
                rankingData = rankingData,
                isRefreshing = isRefreshing,
                onRefresh = {
                    refreshBaseTime = rankingData?.updateTime ?: 0L
                    activity.sendRefreshBroadcast()
                    isRefreshing = true
                }
            )

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
                    // 好友收取明细列表(PK数据,已按周收→总收→开始统计时间排序)
                    val friendList = rankingData?.friends ?: emptyList()
                    if (friendList.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRefreshing) "(刷新中,请稍候...)" else "(暂无数据,等待同步)",
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
            val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val firstSeen = f.optLong("firstSeen", 0)
                friends.add(
                    FriendRankInfo(
                        userId = f.optString("userId", i.toString()),
                        name = f.optString("name", f.optString("userId")),
                        weekEnergy = f.optLong("weekEnergy", 0),
                        totalEnergy = f.optLong("totalEnergy", 0),
                        firstSeenText = if (firstSeen > 0) {
                            "（开始统计时间:" + dateFormat.format(Date(firstSeen)) + "）"
                        } else ""
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
            // 旧版模块代码写入的文件没有月/年汇总字段,用于提示支付宝内模块需要更新
            hasMonthYearSums = jo.has("monthSum") && jo.has("yearSum"),
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
    val hasMonthYearSums: Boolean,
    val friends: List<FriendRankInfo>
)

data class FriendRankInfo(
    val userId: String,
    val name: String,
    val weekEnergy: Long,
    val totalEnergy: Long,
    val firstSeenText: String
)

/** 好友收取明细标题:手动刷新按钮 + 本周/累计汇总 + 最近同步时间。 */
@Composable
private fun CollectDetailHeader(
    rankingData: RankingData?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
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
            TextButton(
                text = if (isRefreshing) "刷新中..." else "刷新数据",
                onClick = { if (!isRefreshing) onRefresh() }
            )
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
 * 垂直快速翻页滑块:采用绝对映射——手指在轨道上的绝对位置直接换算目标项索引,
 * 不依赖滚动状态增量,彻底消除连续手势事件读到过期位置导致的进度滞后。
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

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    // 列表内全部为等高明细行:实际可达的最大 firstVisibleItemIndex = 总项数 - 可见项数
    val denom = (totalItems - layoutInfo.visibleItemsInfo.size).coerceAtLeast(1)
    // 显示进度带小数部分(首项内偏移/首项高度),手动滚动时滑块连续移动
    val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
    val rawIndex = if (firstItem != null && firstItem.size > 0) {
        listState.firstVisibleItemIndex +
                listState.firstVisibleItemScrollOffset.toFloat() / firstItem.size
    } else {
        listState.firstVisibleItemIndex.toFloat()
    }
    val progress = (rawIndex.coerceIn(0f, denom.toFloat()) / denom).coerceIn(0f, 1f)
    val maxThumbOffset = (trackHeight - thumbHeightPx).coerceAtLeast(0f)
    val thumbOffset = progress * maxThumbOffset

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            // 底部留出圆角/导航栏区域,顶部留少量边距,拖到底无需触屏幕边缘
            .padding(top = 8.dp, bottom = 88.dp, end = 4.dp)
            .onGloballyPositioned { trackHeight = it.size.height.toFloat() },
        contentAlignment = Alignment.TopEnd
    ) {
        // 拖拽响应区域:手指绝对位置 → 目标项索引
        Box(
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val curLayout = listState.layoutInfo
                        val curTotal = curLayout.totalItemsCount
                        if (curTotal <= 0) return@detectDragGestures
                        val curMaxThumb = (trackHeight - thumbHeightPx).coerceAtLeast(1f)
                        val curDenom = (curTotal - curLayout.visibleItemsInfo.size).coerceAtLeast(1)
                        // 手指中心点在轨道上的绝对位置 → 目标进度(0~1)
                        val frac = ((change.position.y - thumbHeightPx / 2f)
                            .coerceIn(0f, curMaxThumb)) / curMaxThumb
                        val targetIndex = if (frac >= 0.999f) {
                            // 滑块到底:直接定位列表末尾,scrollToItem 自动钳制到真实底部
                            curTotal - 1
                        } else {
                            (frac * curDenom).roundToInt().coerceIn(0, curDenom)
                        }
                        scope.launch { listState.scrollToItem(targetIndex) }
                    }
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
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.6f))
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

        if (!rankingData.hasMonthYearSums) {
            Text(
                text = "月/年汇总数据需要支付宝内的模块同步生成。当前支付宝内运行的仍是旧版模块:" +
                        "LSPosed 用户请强停支付宝后重新打开;LSPatch/NPatch 用户请用新模块重新修补支付宝,然后再点\"刷新数据\"。",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(8.dp))
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
