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
            for (Map.Entry<String, UserEntity> entry : UserIdMap.getUserMap().entrySet()) {
                String userId = entry.getKey();
                if (userId == null || userId.isEmpty()) continue;
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
            List<JSONObject> friendList = new ArrayList<>();
            long weekSum = 0, totalSum = 0, monthSum = 0, yearSum = 0;
            for (Map.Entry<String, Long> entry : weekCollectMap.entrySet()) {
                JSONObject f = friendsStats.optJSONObject(entry.getKey());
                if (f == null) continue;
                JSONObject info = new JSONObject();
                info.put("userId", entry.getKey());
                info.put("name", getDisplayName(entry.getKey()));
                long weekEnergy = f.optLong("weekEnergy", 0);
                long totalEnergy = f.optLong("totalEnergy", 0);
                info.put("weekEnergy", weekEnergy);
                info.put("totalEnergy", totalEnergy);
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
     * 每人维护本年各周收取值(weeks:weekKey→收取克数),本周/本月/本年均由周历史推导:
     * 首次使用时只有本周数据,三者相等;过了本周本月累加新周,过了本月本年累加新月份。
     * 总收仍为增量累加(可保留安装前的历史)。firstSeen 记录首次统计时间。
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
                f.put("firstSeen", System.currentTimeMillis());
            }
            JSONObject weeks = f.optJSONObject("weeks");
            if (weeks == null) {
                // 兼容旧数据:用原 weekEnergy 建立本周历史。
                // 旧 weekKey 与当前不一致(跨周升级)时旧周收作废归零,避免算错总收增量
                weeks = new JSONObject();
                if (!currentWeekKey.equals(f.optString("weekKey"))) {
                    f.put("weekEnergy", 0);
                }
                weeks.put(currentWeekKey, f.optLong("weekEnergy", 0));
            }
            // 只保留本年的周历史(本年统计只需当年各周)
            Iterator<String> it = weeks.keys();
            while (it.hasNext()) {
                if (!it.next().startsWith(currentYearKey)) {
                    it.remove();
                }
            }
            // 总收增量累加:本周已同步值 → 新增部分
            long prevWeek = weeks.optLong(currentWeekKey, 0);
            long newPortion = Math.max(0, weekCollected - prevWeek);
            f.put("totalEnergy", f.optLong("totalEnergy", 0) + newPortion);
            // 更新本周历史并推导 本周/本月/本年
            weeks.put(currentWeekKey, Math.max(weekCollected, 0));
            f.put("weeks", weeks);
            f.put("weekKey", currentWeekKey);
            f.put("weekEnergy", weeks.optLong(currentWeekKey, 0));
            long monthEnergy = 0, yearEnergy = 0;
            Iterator<String> it2 = weeks.keys();
            while (it2.hasNext()) {
                String k = it2.next();
                long v = weeks.optLong(k, 0);
                if (k.startsWith(currentMonthKey)) {
                    monthEnergy += v;
                }
                if (k.startsWith(currentYearKey)) {
                    yearEnergy += v;
                }
            }
            f.put("monthEnergy", monthEnergy);
            f.put("yearEnergy", yearEnergy);
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
