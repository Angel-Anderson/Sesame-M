package io.github.aw1y2z.sesame.model.extensions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.model.task.antSports.AntSportsRpcCall;
import io.github.aw1y2z.sesame.model.task.protectEcology.ProtectTreeRpcCall;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.PathThemeMapListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class ExtensionsHandle {
    private static final String TAG = ExtensionsHandle.class.getSimpleName();

    public static void handleRequest(String type, String fun, Object data) {
        if (handleAlphaRequest(type, fun, data) != null) {
            return;
        }
        switch (type) {
            case "antForest":
                if (Objects.equals("getWateredItems", fun)) {
                    getWateredItems();
                }else if (Objects.equals("getWateringItems", fun)) {
                    getWateringItems();
                }else if (Objects.equals("getTreeItems", fun)) {
                    getTreeItems();
                } else if (Objects.equals("getNewTreeItems", fun)) {
                    getNewTreeItems();
                } else if (Objects.equals("queryAreaTrees", fun)) {
                    queryAreaTrees();
                } else if (Objects.equals("getUnlockTreeItems", fun)) {
                    getUnlockTreeItems();
                }else if (Objects.equals("fillWateredFriendList", fun)) {
                    fillWateredFriendList();
                } else if (Objects.equals("queryFriendRanking", fun)) {
                    queryFriendRanking();
                }
                break;
            case "setCustomWalkPathIdList":
                addCustomWalkPathIdList((String) data);
                break;
            case "setCustomWalkPathIdQueue":
                if (Objects.equals("addCustomWalkPathIdQueue", fun)) {
                    addCustomWalkPathIdQueue((String) data);
                } else if (Objects.equals("clearCustomWalkPathIdQueue", fun)) {
                    clearCustomWalkPathIdQueue();
                }
                break;
        }
    }

    public static Object handleAlphaRequest(String type, String fun, Object data) {
        try {
            return Class.forName("io.github.aw1y2z.sesame.model.extensions.ExtensionsHandleAlpha")
                    .getMethod("handleAlphaRequest", String.class, String.class, Object.class)
                    .invoke(null, type, fun, data);
        } catch (Exception e) {
            return null;
        }
    }
    private static void getWateredItems() {
        Status.getWateredFriendToday();
    }
    
    private static void getWateringItems() {
        Status.getWateringFriendToday();
    }

    private static void fillWateredFriendList() {
        Status.fillWateredFriendList();
    }

    /**
     * 逐个好友调用 queryPKRecord 抓取本周收取能量,累计后写入文件供 UI 端读取展示。
     * 本周数据每周一0点由服务端清零;总收为历史累计,只累加新增部分;
     * 按 周收↑→总收↑→开始统计时间↑ 排序。
     */
    private static void queryFriendRanking() {
        try {
            // 1. 读取累计统计,逐个好友查询并实时合并
            //    (每个好友抓到数据立刻合并,周翻转以抓取时刻判断,避免跨周一0点误差)
            JSONObject stats = loadCollectStats();
            JSONObject friendsStats = stats.optJSONObject("friends");
            if (friendsStats == null) {
                friendsStats = new JSONObject();
                stats.put("friends", friendsStats);
            }
            Map<String, Long> weekCollectMap = new LinkedHashMap<>();
            List<String> excludeKeywords = loadExcludeKeywords();
            for (Map.Entry<String, UserEntity> entry : UserIdMap.getUserMap().entrySet()) {
                String userId = entry.getKey();
                if (userId == null || userId.isEmpty()) continue;
                // 名字命中排除关键词的好友:不抓取、不计入明细与汇总
                if (isNameExcluded(getDisplayName(userId), excludeKeywords)) {
                    continue;
                }
                long weekCollected = fetchWeekCollectEnergy(userId);
                if (weekCollected >= 0) {
                    weekCollectMap.put(userId, weekCollected);
                    mergeFriendWeekEnergy(friendsStats, userId, weekCollected);
                }
                TimeUtil.sleep(100);
            }
            if (weekCollectMap.isEmpty()) {
                Toast.show("好友数据抓取失败");
                return;
            }

            // 2. 保存累计统计
            long now = System.currentTimeMillis();
            stats.put("updateTime", now);
            FileUtil.write2File(stats.toString(),
                    new File(FileUtil.MAIN_DIRECTORY_FILE, "friendCollectStats.json"));

            // 3. 构建展示列表并排序:周收↑→总收↑→开始统计时间↑
            //    遍历 friendsStats 中所有非排除好友(不仅是本次抓取成功的),
            //    本次未抓取的好友沿用上次数据(含历史周累计的月/年收)
            List<JSONObject> friendList = new ArrayList<>();
            long weekSum = 0, totalSum = 0, monthSum = 0, yearSum = 0;
            Iterator<String> statsKeys = friendsStats.keys();
            while (statsKeys.hasNext()) {
                String userId = statsKeys.next();
                // 名字命中排除关键词的好友:不计入明细与汇总
                if (isNameExcluded(getDisplayName(userId), excludeKeywords)) {
                    continue;
                }
                JSONObject f = friendsStats.optJSONObject(userId);
                if (f == null) continue;
                JSONObject info = new JSONObject();
                info.put("userId", userId);
                info.put("name", getDisplayName(userId));
                long weekEnergy = f.optLong("weekEnergy", 0);
                long totalEnergy = f.optLong("totalEnergy", 0);
                info.put("weekEnergy", weekEnergy);
                info.put("totalEnergy", totalEnergy);
                // 月/年收取随明细输出,UI 端排除过滤后可即时重算全部汇总
                info.put("monthEnergy", f.optLong("monthEnergy", 0));
                info.put("yearEnergy", f.optLong("yearEnergy", 0));
                info.put("firstSeen", f.optLong("firstSeen", now));
                weekSum += weekEnergy;
                totalSum += totalEnergy;
                monthSum += f.optLong("monthEnergy", 0);
                yearSum += f.optLong("yearEnergy", 0);
                friendList.add(info);
            }
            Collections.sort(friendList, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    long wa = a.optLong("weekEnergy", 0), wb = b.optLong("weekEnergy", 0);
                    if (wa != wb) return Long.compare(wa, wb);
                    long ta = a.optLong("totalEnergy", 0), tb = b.optLong("totalEnergy", 0);
                    if (ta != tb) return Long.compare(ta, tb);
                    long fa = a.optLong("firstSeen", 0), fb = b.optLong("firstSeen", 0);
                    return Long.compare(fa, fb);
                }
            });
            JSONArray sortedArray = new JSONArray();
            for (JSONObject f : friendList) {
                sortedArray.put(f);
            }

            // 4. 写入展示文件
            JSONObject result = new JSONObject();
            result.put("total", sortedArray.length());
            result.put("weekSum", weekSum);
            result.put("monthSum", monthSum);
            result.put("yearSum", yearSum);
            result.put("totalSum", totalSum);
            result.put("friends", sortedArray);
            result.put("updateTime", now);

            FileUtil.write2File(result.toString(),
                    new File(FileUtil.MAIN_DIRECTORY_FILE, "friendRanking.json"));
            Log.record("好友收取明细:同步完成 " + sortedArray.length() + "人"
                    + " 本周" + weekSum + "g 本月" + monthSum + "g 本年" + yearSum + "g"
                    + " 累计" + totalSum + "g");
            Toast.show("好友数据已刷新: 共" + sortedArray.length() + "人, 本周收取" + weekSum + "g");
        } catch (Throwable t) {
            Log.i(TAG, "queryFriendRanking err:");
            Log.printStackTrace(TAG, t);
            Toast.show("好友数据刷新失败");
        }
    }

    /**
     * 查询本周我从指定好友处收取的能量(queryPKRecord pkType=Week)。
     * myRecord = 我的记录(collectEnergy=我收取TA的能量);失败返回 -1。
     */
    private static long fetchWeekCollectEnergy(String friendUserId) {
        try {
            JSONObject resp = new JSONObject(AntForestRpcCall.queryPKRecord(friendUserId));
            if (!resp.optBoolean("success", false)
                    && !"SUCCESS".equals(resp.optString("resultCode"))) {
                return -1;
            }
            JSONObject myRecord = resp.optJSONObject("myRecord");
            if (myRecord != null && friendUserId.equals(myRecord.optString("userId"))) {
                // 字段错位时用 targetRecord 兜底
                myRecord = resp.optJSONObject("targetRecord");
            }
            if (myRecord == null) return -1;
            return myRecord.optLong("collectEnergy", 0);
        } catch (Throwable t) {
            Log.i(TAG, "fetchWeekCollectEnergy err:");
            Log.printStackTrace(TAG, t);
            return -1;
        }
    }

    /** 读取好友累计统计文件 friendCollectStats.json。 */
    private static JSONObject loadCollectStats() {
        try {
            File file = new File(FileUtil.MAIN_DIRECTORY_FILE, "friendCollectStats.json");
            if (file.exists()) {
                String content = FileUtil.readFromFile(file);
                if (!StringUtil.isEmpty(content)) {
                    return new JSONObject(content);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "loadCollectStats err:");
            Log.printStackTrace(TAG, t);
        }
        return new JSONObject();
    }

    /**
     * 将单个好友本周收取数据合并进累计统计。
     * 总收/月收/年收均独立增量累加:
     * - totalEnergy:历史累计,只加新增部分;跨周时把上周最终值作为「上周完整收取」补加
     * - monthEnergy:本月累计,周翻转且月份变化时清零;跨周时把上周最终值补加到旧月份(如已清零则仅累加本周新增)
     * - yearEnergy:本年累计,周翻转且年份变化时清零;跨周时把上周最终值补加
     * 跨周补加机制:如果上次同步时记录的 weekEnergy 还没被算进总收(比如周日23点强制同步没运行),
     * 周一发现跨周时先把上周值作为「上周完整收取」补加到总收/年收,确保上周数据不丢失。
     */
    private static void mergeFriendWeekEnergy(JSONObject friendsStats, String userId, long weekCollected) {
        try {
            String currentWeekKey = getCurrentWeekKey();
            String currentMonthKey = currentWeekKey.substring(0, 7);
            String currentYearKey = currentWeekKey.substring(0, 4);
            JSONObject f = friendsStats.optJSONObject(userId);
            if (f == null) {
                f = new JSONObject();
                f.put("totalEnergy", 0);
                f.put("monthEnergy", 0);
                f.put("yearEnergy", 0);
                f.put("firstSeen", System.currentTimeMillis());
            }
            String storedWeekKey = f.optString("weekKey", "");
            long prevWeek = f.optLong("weekEnergy", 0);
            boolean hasLastSyncFlag = f.optBoolean("lastSynced", false);
            // 周翻转:存储的 weekKey 与当前不同 → 新的一周
            boolean weekFlipped = !storedWeekKey.isEmpty() && !storedWeekKey.equals(currentWeekKey);
            // 月翻转:存储的周标识月份与当前不同 → 新的一月
            String storedMonth = storedWeekKey.length() >= 7 ? storedWeekKey.substring(0, 7) : "";
            boolean monthFlipped = weekFlipped && !storedMonth.equals(currentMonthKey);
            // 年翻转:存储的周标识年份与当前不同 → 新的一年
            String storedYear = storedWeekKey.length() >= 4 ? storedWeekKey.substring(0, 4) : "";
            boolean yearFlipped = weekFlipped && !storedYear.equals(currentYearKey);

            long addToTotal = 0;    // 本次要累加到 totalEnergy 的量
            long addToMonth = 0;    // 本次要累加到 monthEnergy 的量(本月)
            long addToYear = 0;    // 本次要累加到 yearEnergy 的量(本年)

            if (weekFlipped) {
                // 跨周:上周结束值(prevWeek)如果还没被算进总收(上次同步时已算过则跳过)
                // lastSynced 标记:上次同步时 prevWeek 是否已累加进 totalEnergy
                if (!hasLastSyncFlag && prevWeek > 0) {
                    // 上周结束时未同步过,把上周最终值作为完整一周补加到总收/年收
                    // 月收:若跨月,上周属旧月份,本月从0开始(不补加到本月);若同月,补加到本月
                    addToTotal += prevWeek;
                    addToYear += prevWeek;
                    if (!monthFlipped) {
                        addToMonth += prevWeek;
                    }
                    Log.record("好友收取明细:跨周补加 " + userId + " 上周"
                            + storedWeekKey + "=" + prevWeek + "g 到累计/年收"
                            + (monthFlipped ? "(跨月,月收不补加)" : "(同月,月收补加)"));
                }
                // 本周新增:周一服务端清零后本周值(可能为0或本周已收的部分)
                addToTotal += Math.max(weekCollected, 0);
                addToMonth += Math.max(weekCollected, 0);
                addToYear += Math.max(weekCollected, 0);
            } else {
                // 同周:新增部分 = 本周值 - 上次同步的本周值
                long newPortion = Math.max(0, weekCollected - prevWeek);
                addToTotal += newPortion;
                addToMonth += newPortion;
                addToYear += newPortion;
            }
            // 周翻转时,月/年按翻转情况清零(新月份/新年份从0开始)
            if (monthFlipped) {
                f.put("monthEnergy", 0);
            }
            if (yearFlipped) {
                f.put("yearEnergy", 0);
            }
            // 三项独立累加
            f.put("totalEnergy", f.optLong("totalEnergy", 0) + addToTotal);
            f.put("monthEnergy", f.optLong("monthEnergy", 0) + addToMonth);
            f.put("yearEnergy", f.optLong("yearEnergy", 0) + addToYear);
            // 更新本周标识与值,标记本周值已累加进 totalEnergy(避免下次同步重复算)
            f.put("weekKey", currentWeekKey);
            f.put("weekEnergy", Math.max(weekCollected, 0));
            f.put("lastSynced", true);
            friendsStats.put(userId, f);
        } catch (Throwable t) {
            Log.i(TAG, "mergeFriendWeekEnergy err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 同步间隔:周一至周六每天一次。 */
    private static final long FRIEND_STATS_SYNC_INTERVAL_DAY = 24 * 60 * 60 * 1000L;
    /** 同步间隔:周日每6小时一次。 */
    private static final long FRIEND_STATS_SYNC_INTERVAL_SUNDAY = 6 * 60 * 60 * 1000L;
    /** 同步进行中标记,防止重入。 */
    private static final java.util.concurrent.atomic.AtomicBoolean friendStatsSyncing =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 好友收取明细数据定时同步入口,由主任务循环调用:
     * 周一至周六每天一次;周日每6小时一次,且23点后强制同步一次并确保完成
     * (失败会在主任务循环每轮重试,直到成功保存本周最终数据)。
     * 后台线程执行,不阻塞主流程。
     */
    public static void trySyncFriendStats() {
        try {
            if (friendStatsSyncing.get()) {
                return;
            }
            long lastSync = loadCollectStats().optLong("updateTime", 0);
            long now = System.currentTimeMillis();
            boolean sunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
            long interval = sunday ? FRIEND_STATS_SYNC_INTERVAL_SUNDAY : FRIEND_STATS_SYNC_INTERVAL_DAY;
            boolean due = now - lastSync >= interval;
            boolean sundayForce = isSundayAfter23(lastSync, now);
            if (!due && !sundayForce) {
                return;
            }
            if (friendStatsSyncing.compareAndSet(false, true)) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.record("好友收取明细:开始自动同步数据");
                            queryFriendRanking();
                        } finally {
                            friendStatsSyncing.set(false);
                        }
                    }
                }, "friend-stats-sync");
                thread.start();
            }
        } catch (Throwable t) {
            Log.i(TAG, "trySyncFriendStats err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 周日23点后且上次同步早于本周日23点 → 强制同步。 */
    private static boolean isSundayAfter23(long lastSync, long now) {
        Calendar cal = Calendar.getInstance();
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            return false;
        }
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long sunday2300 = cal.getTimeInMillis();
        return now >= sunday2300 && lastSync < sunday2300;
    }

    // ===== 周日23点按手机时间准点强制同步的定时调度器 =====
    private static android.os.HandlerThread schedulerThread;
    private static android.os.Handler schedulerHandler;
    private static Runnable sundayForceTask;

    /**
     * 初始化周日23点定时强制同步调度器(按手机系统时间),由模块入口调用一次。
     * 不依赖主任务循环或打开统计页:支付宝进程存活期间,到周日23:00准点自动触发;
     * 触发完成后自动排程下一个周日23:00。
     */
    public static synchronized void initFriendStatsScheduler() {
        try {
            if (schedulerHandler == null) {
                schedulerThread = new android.os.HandlerThread("friend-stats-scheduler");
                schedulerThread.start();
                schedulerHandler = new android.os.Handler(schedulerThread.getLooper());
            }
            scheduleSundayForceSync();
        } catch (Throwable t) {
            Log.i(TAG, "initFriendStatsScheduler err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 排程下一次周日23:00(若今天是周日且未到23点则为今天,否则为下周日)的强制同步。 */
    private static void scheduleSundayForceSync() {
        try {
            if (schedulerHandler == null) return;
            if (sundayForceTask != null) {
                schedulerHandler.removeCallbacks(sundayForceTask);
            }
            long now = System.currentTimeMillis();
            long triggerAt;
            // 兜底:现在已是周日23点后(如模块/进程在23点后才启动)且本周尚未强制同步 → 立即补执行
            Calendar nowCal = Calendar.getInstance();
            nowCal.set(Calendar.HOUR_OF_DAY, 23);
            nowCal.set(Calendar.MINUTE, 0);
            nowCal.set(Calendar.SECOND, 0);
            nowCal.set(Calendar.MILLISECOND, 0);
            long lastSyncTs = loadCollectStats().optLong("updateTime", 0);
            if (nowCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    && now >= nowCal.getTimeInMillis()
                    && lastSyncTs < nowCal.getTimeInMillis()) {
                triggerAt = now;
                Log.record("好友收取明细:当前为周日23点后且本周未同步,立即补执行强制同步");
            } else {
                triggerAt = nextSunday2300();
            }
            final long delay = triggerAt - System.currentTimeMillis();
            sundayForceTask = new Runnable() {
                @Override
                public void run() {
                    boolean started = false;
                    try {
                        long lastSync = loadCollectStats().optLong("updateTime", 0);
                        long now = System.currentTimeMillis();
                        // 已有23点之后的成功同步(其他途径完成) → 本时段任务完成,排程下周
                        if (!isSundayAfter23(lastSync, now) && lastSync >= triggerAt) {
                            scheduleSundayForceSync();
                            return;
                        }
                        Log.record("好友收取明细:周日23点定时触发,开始强制同步");
                        started = friendStatsSyncing.compareAndSet(false, true);
                        if (started) {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        queryFriendRanking();
                                    } finally {
                                        friendStatsSyncing.set(false);
                                        scheduleSundayForceSync();
                                    }
                                }
                            }, "friend-stats-sync").start();
                        }
                    } catch (Throwable t) {
                        Log.i(TAG, "sundayForceTask err:");
                        Log.printStackTrace(TAG, t);
                    } finally {
                        // 若已有同步在进行(未启动新线程),1分钟后重试本时段强制同步,确保完成
                        if (!started) {
                            schedulerHandler.postDelayed(this, 60 * 1000L);
                        }
                    }
                }
            };
            schedulerHandler.postDelayed(sundayForceTask, Math.max(delay, 0));
            Log.record("好友收取明细:已排程周日23点强制同步,触发时间 "
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new java.util.Date(triggerAt)));
        } catch (Throwable t) {
            Log.i(TAG, "scheduleSundayForceSync err:");
            Log.printStackTrace(TAG, t);
        }
    }

    /** 计算下一个周日23:00的时间戳:今天周日且现在早于23点 → 今天23点;否则顺延至下周日23点。 */
    private static long nextSunday2300() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // 今天不是周日,或今天周日但已过23点 → 顺延到下一个周日
        if (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                || cal.getTimeInMillis() <= System.currentTimeMillis()) {
            do {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY);
        }
        return cal.getTimeInMillis();
    }

    /** 获取当前周标识(本周周一0点的日期,用于周一清零判断)。 */
    private static String getCurrentWeekKey() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime());
    }

    /**
     * 读取好友排除关键词文件 friendStatsExclude.txt(逗号/顿号/换行分隔),
     * 好友名字包含任一关键词则不计入明细与汇总。
     */
    private static List<String> loadExcludeKeywords() {
        List<String> keywords = new ArrayList<>();
        try {
            File file = new File(FileUtil.MAIN_DIRECTORY_FILE, "friendStatsExclude.txt");
            if (file.exists()) {
                String content = FileUtil.readFromFile(file);
                if (!StringUtil.isEmpty(content)) {
                    for (String kw : content.split("[\\s,，、\\n\\r]+")) {
                        kw = kw.trim();
                        if (!kw.isEmpty()) {
                            keywords.add(kw.toLowerCase());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "loadExcludeKeywords err:");
            Log.printStackTrace(TAG, t);
        }
        return keywords;
    }

    /** 名字是否命中排除关键词(不区分大小写)。 */
    private static boolean isNameExcluded(String name, List<String> excludeKeywords) {
        if (name == null || name.isEmpty() || excludeKeywords.isEmpty()) {
            return false;
        }
        String lowerName = name.toLowerCase();
        for (String keyword : excludeKeywords) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 获取好友展示名:备注名优先,其次 showName,最后兜底 userId。 */
    private static String getDisplayName(String userId) {
        UserEntity userEntity = UserIdMap.get(userId);
        if (userEntity != null) {
            String remark = userEntity.getRemarkName();
            if (remark != null && !remark.isEmpty()) return remark;
            String show = userEntity.getShowName();
            if (show != null && !show.isEmpty()) return show;
        }
        return userId;
    }
    
    private static void getNewTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("COMING", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            if (ja.length() == 0) {
                Log.forest("新树上苗🌱[当前没有新树上苗信息!]");
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                if (!"TREE".equals(jo.getString("projectType")))
                    continue;
                if (!"COMING".equals(jo.getString("applyAction")))
                    continue;
                String projectId = jo.getString("itemId");
                queryTreeForExchange(projectId);
            }
        } catch (Throwable t) {
            Log.i(TAG, "getTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryTreeForExchange(String projectId) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject exchangeableTree = jo.getJSONObject("exchangeableTree");
            int currentBudget = exchangeableTree.getInt("currentBudget");
            String region = exchangeableTree.getString("region");
            String treeName = exchangeableTree.getString("treeName");
            String tips = "不可合种";
            if (exchangeableTree.optBoolean("canCoexchange", false)) {
                tips = "可以合种-合种类型："
                        + exchangeableTree.getJSONObject("extendInfo").getString("cooperate_template_id_list");
            }
            Log.forest("新树上苗🌱[" + region + "-" + treeName + "]#" + currentBudget + "株-" + tips);
        } catch (Throwable t) {
            Log.i(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("AVAILABLE,ENERGY_LACK", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                String projectId = jo.getString("itemId");
                String itemName = jo.getString("itemName");
                getTreeCurrentBudget(projectId, itemName);
                TimeUtil.sleep(100);
            }
        } catch (Throwable t) {
            Log.i(TAG, "getTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getTreeCurrentBudget(String projectId, String treeName) {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject exchangeableTree = jo.getJSONObject("exchangeableTree");
                int currentBudget = exchangeableTree.getInt("currentBudget");
                String region = exchangeableTree.getString("region");
                Log.forest("树苗查询🌱[" + region + "-" + treeName + "]#剩余:" + currentBudget);
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryTreeForExchange err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void queryAreaTrees() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryAreaTrees());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject areaTrees = jo.getJSONObject("areaTrees");
            JSONObject regionConfig = jo.getJSONObject("regionConfig");
            Iterator<String> regionKeys = regionConfig.keys();
            while (regionKeys.hasNext()) {
                String regionKey = regionKeys.next();
                if (!areaTrees.has(regionKey)) {
                    JSONObject region = regionConfig.getJSONObject(regionKey);
                    String regionName = region.optString("regionName");
                    Log.forest("未解锁地区🗺️[" + regionName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "queryAreaTrees err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void getUnlockTreeItems() {
        try {
            JSONObject jo = new JSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange("", "project"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.getJSONArray("treeItems");
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.getJSONObject(i);
                if (!jo.has("projectType"))
                    continue;
                int certCountForAlias = jo.optInt("certCountForAlias", -1);
                if (certCountForAlias == 0) {
                    String itemName = jo.optString("itemName");
                    String region = jo.optString("region");
                    String organization = jo.optString("organization");
                    Log.forest("未解锁项目🐘[" + region + "-" + itemName + "]#" + organization);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "getUnlockTreeItems err:");
            Log.printStackTrace(TAG, t);
        }
    }

    private static void addCustomWalkPathIdList(String pathId) {
        if (!StringUtil.isEmpty(pathId)) {
            String pathName = AntSportsRpcCall.queryPathName(pathId);
            if (pathName == null) {
                Toast.show("添加自定义路线列表失败:找不到路线信息");
                return;
            }
            PathThemeMapListMap.load();
            PathThemeMapListMap.add(pathId, pathName);
            PathThemeMapListMap.save();
            Toast.show("添加自定义路线列表成功:" + pathName);
        }
    }

    private static void addCustomWalkPathIdQueue(String pathId) {
        if (!StringUtil.isEmpty(pathId)) {
            String pathName = AntSportsRpcCall.queryPathName(pathId);
            if (pathName == null) {
                Toast.show("添加待行走路线队列失败:找不到路线信息");
                return;
            }
            if (TokenConfig.addCustomWalkPathIdQueue(pathId)) {
                Toast.show("添加待行走路线队列成功:" + pathName);
            }
        }
    }

    private static void clearCustomWalkPathIdQueue() {
        if (TokenConfig.clearCustomWalkPathIdQueue()) {
            Toast.show("清除待行走路线队列成功");
        }
    }
}
