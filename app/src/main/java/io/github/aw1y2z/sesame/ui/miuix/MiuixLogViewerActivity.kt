package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * 二级日志页支持的日志类型(与一级页面日志类目一一对应)。
 * 一级页点击哪个类目,二级页就只展示该类。
 */
enum class LogType(val displayName: String) {
    FOREST("森林记录"),
    GOLDENBEANS("金豆记录"),
    FARM("庄园记录"),
    OTHER("其他记录"),
    DEBUG("抓包记录"),
    ERROR("查看异常日志"),
    RUNTIME("查看运行日志");

    /** 每次访问都重新取当日文件,避免跨天后路径过期 */
    val file: File
        get() = when (this) {
            FOREST -> FileUtil.getForestLogFile()
            GOLDENBEANS -> FileUtil.getGoldenBeansLogFile()
            FARM -> FileUtil.getFarmLogFile()
            OTHER -> FileUtil.getOtherLogFile()
            DEBUG -> FileUtil.getDebugLogFile()
            ERROR -> FileUtil.getErrorLogFile()
            RUNTIME -> FileUtil.getRuntimeLogFile()
        }

    companion object {
        /** 一级页跳转时携带的 extra key,值为 LogType.name */
        const val EXTRA_LOG_TYPE = "sesame_log_type"

        fun fromIntent(intent: Intent?): LogType {
            val name = intent?.getStringExtra(EXTRA_LOG_TYPE)
            return entries.firstOrNull { it.name == name } ?: RUNTIME
        }
    }
}

/** 单条日志条目(可能含多行正文) */
data class LogEntry(
    val lineNumber: Int,
    val time: String?,
    val tag: String?,
    val body: String
)

class MiuixLogViewerActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            LogScreen(this, LogType.fromIntent(intent))
        }
    }
}

/**
 * 日志详情页:展示指定类目的全部条目卡片。
 * 仿 LSPosed 日志界面:每条目一张卡(标签 + 时间 + 正文)。
 * 进入页面默认定位到最底部(最新记录);上翻阅读旧日志时暂停自动跟随,回到底部后恢复。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(activity: MiuixLogViewerActivity, logType: LogType) {
    val context = LocalContext.current
    val file = logType.file
    var entries by remember(logType) { mutableStateOf<List<LogEntry>>(emptyList()) }
    var loading by remember(logType) { mutableStateOf(true) }
    val listState = rememberLazyListState()

    // 是否自动跟随最新日志:用户滚到底部(或列表未满一屏)时为 true,上翻阅读旧日志时暂停跟随
    var followBottom by remember { mutableStateOf(true) }
    // 首次定位标记:进入页面拿到首份数据后无条件跳到最底部,与 followBottom 判定解耦,
    // 避免列表初始停在顶部时跟随判定被误置 false 导致首次定位不执行
    var initialScrolled by remember(logType) { mutableStateOf(false) }

    // 异步加载日志(只读文件尾部,大文件不阻塞 UI),文件变化时自动重新加载
    // (文件长度+修改时间未变则跳过重新解析)
    LaunchedEffect(file, logType) {
        var lastStamp = -1L to -1L
        while (true) {
            val stamp = if (file.exists()) file.length() to file.lastModified() else 0L to 0L
            if (stamp != lastStamp) {
                lastStamp = stamp
                entries = withContext(Dispatchers.IO) { loadLogEntries(file) }
                loading = false
            }
            delay(3000)
        }
    }

    // 首次定位:拿到首份非空数据后无条件跳到最底部;日志清空后重置标记,待有数据再重新定位
    LaunchedEffect(entries) {
        if (entries.isEmpty()) {
            initialScrolled = false
        } else if (!initialScrolled) {
            listState.scrollToItem(entries.size - 1)
            initialScrolled = true
        }
    }

    // 后续跟随:已定位且当前在底部时,新日志到来自动滚到最新;上翻阅读时不打断
    LaunchedEffect(initialScrolled, entries) {
        if (initialScrolled && followBottom && entries.isNotEmpty()) {
            listState.scrollToItem(entries.size - 1)
        }
    }

    // 跟随判定:首次定位完成后才启用,避免初始位置(顶部)把 followBottom 误置为 false
    LaunchedEffect(listState, initialScrolled) {
        if (!initialScrolled) return@LaunchedEffect
        snapshotFlow { listState.canScrollForward }
            .collect { canForward -> followBottom = !canForward }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = logType.displayName,
                onBack = { activity.finish() },
                onExport = {
                    val exported = FileUtil.exportFile(file)
                    if (exported != null) {
                        ToastUtil.show(context, "已导出: " + exported.path)
                    } else {
                        ToastUtil.show(context, "导出失败")
                    }
                },
                onClear = {
                    if (FileUtil.clearFile(file)) {
                        entries = loadLogEntries(file)
                        ToastUtil.show(context, "已清空")
                    }
                }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (loading) "(加载中...)" else "(空)",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            // 所有日志行合并到一个大卡片里逐行展示,与旧版一致(而非每条独立卡片)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(entries, key = { _, e -> "${e.lineNumber}-${e.hashCode()}" }) { _, entry ->
                    LogEntryLine(entry)
                }
            }
        }
    }
}

/** 单条日志行:时间戳 + 正文,合并在同一大卡片内逐行展示(不显示TAG) */
@Composable
fun LogEntryLine(entry: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.time ?: "",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = entry.body,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 通用顶部栏:返回图标 + 可选的操作图标(导入/导出/删除) + 横向 marquee 滚动的标题。
 * 仿 LSPosed 日志页样式。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogTopBar(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1
            )
            if (onImport != null) {
                IconButton(onClick = onImport) {
                    // 导入图标:把 Upload 旋转 180°(朝下)与导出(朝上)区分
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "导入",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.rotate(180f)
                    )
                }
            }
            if (onExport != null) {
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "导出",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** 尾部读取上限:只加载文件末尾 512KB,避免大日志文件拖慢加载与内存占用 */
private const val LOG_TAIL_BYTES = 512 * 1024

/**
 * 读取日志文件并按行解析为条目(只读文件末尾 LOG_TAIL_BYTES 字节)。
 * 无时间戳的行合并到上一条(多行日志聚合为同一卡片);尾部截断边界处不完整的条目丢弃。
 */
private fun loadLogEntries(file: File?): List<LogEntry> {
    if (file == null || !file.exists()) {
        return emptyList()
    }
    return try {
        val text = readLogTail(file)
        // 时间戳格式为 HH:mm:ss.SSS,其后可选跟 TAG:(运行/异常等日志有TAG,森林/庄园/其他日志无TAG直接是消息)
        val timeRegex = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})(?:\\s+(\\w+):)?\\s*(.*)$")
        val entries = mutableListOf<LogEntry>()
        var lineNumber = 0
        text.lineSequence().forEach { line ->
            lineNumber++
            val match = timeRegex.find(line)
            if (match != null) {
                entries.add(
                    LogEntry(
                        lineNumber = lineNumber,
                        time = match.groupValues[1],
                        tag = match.groupValues[2].ifEmpty { null },
                        body = match.groupValues[3]
                    )
                )
            } else if (entries.isNotEmpty()) {
                // 无时间戳:视为上一条的续行,合并到同卡片
                val last = entries.removeAt(entries.size - 1)
                entries.add(last.copy(body = last.body + "\n" + line))
            }
            // 条目列表为空时的无时间戳行(尾部读取边界处的截断内容):直接丢弃
        }
        entries
    } catch (e: Throwable) {
        emptyList()
    }
}

/** 只读文件末尾 LOG_TAIL_BYTES 字节;超出时从行边界起读,丢弃首个不完整行 */
private fun readLogTail(file: File): String {
    val len = file.length()
    if (len <= LOG_TAIL_BYTES) {
        return file.readText()
    }
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(len - LOG_TAIL_BYTES)
        val bytes = ByteArray(LOG_TAIL_BYTES)
        raf.readFully(bytes)
        val text = String(bytes, Charsets.UTF_8)
        val firstNewline = text.indexOf('\n')
        return if (firstNewline >= 0) text.substring(firstNewline + 1) else text
    }
}
