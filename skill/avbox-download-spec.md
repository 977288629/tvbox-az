# AVBox 影片下载功能 Spec（参照 iOS 端 DownloadManager / DownloadListView）

> 项目：AVBox（TVBox OSC fork；仓库根目录 = 本文件所在目录的上一级）
> 配套：先读 `SKILL.md`（通用规范 + 文档地图）、`avbox-mobile-ui-spec.md`（UI 基线）、`avbox-kv-mmkv-spec.md`（KV 约束）、`avbox-playback-service-spec.md`（前台服务与播放所有权）、`avbox-i18n-spec.md`（四语资源硬闸门）。
> 状态：**方案待评审，未实施（2026-11-16）**。本文件只描述方案，不含任何代码改动。
> 触发背景：AVBox 当前**没有影片下载能力**；iOS 端具备完整下载体系（HTTP / HLS / M3U8 / 磁力 四种类型、8 种状态、下载列表页、详情页每集下载与批量下载、设置项）。用户要求按 iOS 的 `DownloadManager` / `DownloadListView` 结构补齐。

## 0. 摘要

按 iOS 的职责划分在 AVBox 复刻一套下载能力，分三阶段落地：

| 阶段 | 交付 | 出口条件 |
|---|---|---|
| P1 数据层 + 引擎 + 前台服务 | `DownloadTask` 实体/DAO/Repository、`FileDownloader`、`DownloadService`、`DownloadManager`、Manifest 与权限、设置 KV | `:app:assembleDebug` 通过；adb 触发一次真实下载，文件完整落盘、进程重启后任务状态仍在 |
| P2 下载列表页 + 设置入口 | `DownloadActivity` / `DownloadScreen` / `DownloadViewModel`、设置页「下载」分组、四语资源 | 列表实时进度、暂停/继续/取消/重试/删除/清空全部可用；空间统计正确 |
| P3 详情页入口 + 本地播放 | 每集下载角标三态、批量下载 + 确认弹窗、已完成走 `file://` 播放 | 真机端到端 8 项全过（见 §10） |

**复用为主，不引新框架**：Room（任务持久化）、`HttpClients.defaultClient`（取流）、`SpiderLoader.downloadJarAsync` 的「`.tmp` + rename + 可取消」模板（落盘）、`PlaybackService` 的 FGS + 通知范式、`AppTopBarScaffold` / `LoadStateBox` / `CapsuleSegmentedButton`（列表页）、`MediaSources` 的 `DefaultDataSource`（本地播放）。**不引入** WorkManager / ExoPlayer `DownloadManager` / 新 HTTP 栈。

三个必须提前认清的约束：

1. **任务状态与进度必须落 Room**：MMKV 写入是异步的（`avbox-kv-mmkv-spec.md` §4.1），不能承担下载状态持久化；KV 只放「仅 WiFi / 并发数」这类设置。
2. **`data/` 不得依赖 `net` / `api`**（AGENTS.md 第 4 条）：取流代码放 `net/download/`，`DownloadRepository` 只做 DB。
3. **播放解析链只认 http**：`PlayLoader.shouldDirectPlay`、`DefaultConfig.isVideoFormat`、`PlayUrlResolver.jsonParse` 三处会把 `file://` 判成「需要解析」，本地播放必须显式短路（§8）。

## 1. 现状盘点（2026-11-16 实测）

### 1.1 iOS 端下载体系

参考包：`TVBox-iOS.zip`（仓库外本地参考，不入库）。

| 文件 | 行数 | 职责 |
|---|---|---|
| `Core/Services/DownloadManager.swift` | 825 | 任务模型 + 队列 + 下载实现 + 进度/速度 + 持久化 + `URLSessionDownloadDelegate` / `AVAssetDownloadDelegate` |
| `Core/Services/DownloadService.swift` | — | `DownloadServicing` 协议实现，转发 Manager（UI 只依赖协议） |
| `Core/Cache/DownloadCache.swift` | — | 文件路径规则、已用/可用空间、删除/清空、剩余空间校验 |
| `Features/Download/DownloadListView.swift` | 184 | 下载列表页：`Section("下载中")` + `Section("已完成")`、行内操作、编辑模式删除 |
| `Features/Download/DownloadListViewModel.swift` | 76 | 分组（`downloadingTasks` / `completedTasks`）、暂停/继续/取消/删除/清空、空间统计 |
| `Features/Download/Components/DownloadTaskCell.swift` | — | 任务行单元 |
| `Features/Detail/DetailEpisodeGrid.swift` | 261 | 每集下载按钮（三态）、「批量下载」入口与确认弹窗 |
| `Features/Settings/SettingsView.swift` | 889 | 下载设置分组（仅 WiFi / 并发数 / 完成数 / 已用 / 可用 / 清空） |
| `Features/Settings/SettingsViewModel.swift` | — | `wifiOnlyDownload`、`maxConcurrentDownloads`、空间统计转发 |
| `Features/Profile/ProfileView.swift` | — | 入口：`NavigationLink(destination: DownloadListView())`（「我的」页） |

**类型枚举（4 种）**

```swift
enum DownloadType: String, Codable {
    case http      // HTTP 直链
    case hls       // HLS 离线（AVAssetDownloadTask）
    case m3u8      // M3U8 分段下载合并
    case magnet    // 磁力（服务器转换）
}
```

**状态枚举（8 种）**

```swift
enum DownloadState: String, Codable {
    case waiting, preparing, downloading, paused,
         merging, completed, failed, cancelled
}
```

**任务模型字段**

| 字段 | 说明 |
|---|---|
| `id` / `videoId` / `title` / `coverURL` | 任务标识与影片元数据 |
| `downloadType` / `createdAt` | 类型与入队时间 |
| `state` / `progress` / `speed` / `downloadedSize` / `totalSize` / `errorMessage` | 运行时状态（`@Published`） |
| `episodeName` | 集名（UI 优先显示它，否则 `title`） |
| `url` / `headers` | 下载地址与防盗链头 |
| `outputPath` / `fileName` | 落盘位置 |
| `resumeData` / `taskId` | 断点续传数据与系统任务句柄 |
| `m3u8Segments` / `completedSegments` / `totalSegments` | M3U8 分片进度 |
| `retryCount` / `maxRetries`（默认 3） | 重试 |

**实现要点**

- **持久化**：`UserDefaults.standard` 键 `download_tasks`，整表 JSON（`saveTasks` / `loadTasks`）；`loadTasks` 时把残留的 `downloading` 改成 `paused` 再 `resume`。
- **HTTP**：`URLSessionConfiguration.background(withIdentifier:)` 两个后台会话（普通 + HLS）；续传用 `downloadTask(withResumeData:)`。
- **M3U8**：拉 manifest → `parseM3U8`（按 `#EXTINF` 切段、解析相对地址）→ 分片下载（`DispatchSemaphore(value: 6)`）→ `mergeM3U8Segments` 顺序拼接为 `.mp4`。
- **HLS**：`AVAssetDownloadTask`（iOS 专有）。
- **速度**：`updateSpeeds` 按相邻两次采样的字节差 / 时间差计算。
- **落盘**：`DownloadCache.fileURL` = `文档目录/<videoId>_<episodeName>.mp4`（空格换下划线），另有 `sanitizeFileName` 过滤 `\/:*?"<>|`。
- **空间**：`calculateUsedSpace` / `getAvailableSpace` / `hasEnoughSpace`（期望值未知时按 100MB 兜底）。

**列表页结构**

- `List` 两个 Section：`下载中`（含 waiting/preparing/downloading/paused/merging/failed）、`已完成`。
- 行内容：集名（或片名）、状态文案、大小、下载中显示速度与百分比、按状态显示操作（暂停/继续/取消/重试/删除）。
- 编辑模式：多选删除；顶部操作：清空已完成、清空全部。

**详情页入口**

- 每集卡片右侧独立下载按钮（`28×44`）：未下载 `arrow.down.circle`、下载中 `ProgressView`、已下载 `checkmark.circle.fill`。
- 选集标题行「批量下载」按钮（`arrow.down.circle.fill`）+ 确认弹窗「确定要下载全部 N 集吗？」（取消 / 下载全部）。

**设置项（默认值实测）**

| 键 | 类型 | 默认 |
|---|---|---|
| `tvbox_wifi_only_download` | Bool | `false` |
| `tvbox_max_concurrent_downloads` | Int | `3`（`value > 0 ? value : 3`） |
| 完成数 / 已用空间 / 可用空间 | 只读统计 | — |
| 清空全部下载 | 动作 | — |

### 1.2 AVBox 现状：确认没有影片下载

全仓 `download` 字样共 6 个文件，**均与影片下载无关**：

| 文件 | 实际含义 |
|---|---|
| `app/src/main/java/com/github/avbox/osc/api/SpiderLoader.kt` | 下载**爬虫 jar**（`downloadJarAsync`） |
| `.../io/LocalSourceTree.kt` | 系统 `Download` 目录的路径判定（读本地配置） |
| `.../io/LocalConfigHost.kt` | `content://downloads` 等 Uri → 路径转换 |
| `.../sourcedata/SubtitleViewModel.kt` | 字幕站（assrt.net）的字幕下载 URL |
| `.../sourcedata/Thunder.kt` | 迅雷 `XLTaskHelper` **解析磁力/迅雷链接做边下边播**，不是存片管理 |
| `.../util/DefaultConfig.kt` | URL 嗅探正则里的 `download.aspx` 片段 |

`ui/` 下**零**下载入口；`AndroidManifest.xml` 无下载服务；`KVKeys` 无下载键；无下载任务表。

### 1.3 AVBox 可复用基建

| 能力 | 位置 | 复用方式 |
|---|---|---|
| OkHttp 单例 | `app/src/main/java/com/github/avbox/osc/net/HttpClients.kt`（`defaultClient` / `init()`）、`.../com/github/catvod/net/OkHttp.kt`（`client(timeout)` / `cancel(tag)`） | 下载取流用 `defaultClient`；单任务取消用 `OkHttp.cancel(tag)` |
| 流式落盘模板 | `.../api/SpiderLoader.kt:124 downloadJarAsync` | 照抄：`body.byteStream()` → 16KB buffer → `.tmp` → `renameTo`（失败 `FileUtils.copyFile`）→ 失败删 tmp → `mainHandler.post` 回主线程 |
| 防盗链头解析 | `.../util/VodImages.kt`（`parseVodPicUrl`，`@Headers/@Referer/@Cookie/@User-Agent`）、`.../sourcedata/PushUrlParser.kt`（`parseMarkedHeaders`）、`.../util/HeaderGuard.kt`（`isSendable`） | 提取为 `util/` 共用函数（不复制逻辑）；所有配置来源的 header 必须过 `HeaderGuard` |
| 文件工具 | `.../io/FileUtils.kt`（`copyFile` / `getFilePath` / `formatCacheSize` / `deleteFile` / `createNoMediaMarkerIfAbsent`） | 直接复用 |
| Room 四件套 | `.../data/AppDataBase.kt`（version 2，4 实体）、`.../data/AppDataManager.kt`（`BundledSQLiteDriver` + `addMigrations`）、`.../data/AppMigrations.kt`、`.../data/VodCollect.kt` + `VodCollectDao.kt` + `CollectRepository.kt`、`.../data/AppGraph.kt` | 下载任务照抄「实体 + DAO + Repository + AppGraph」；`observeAll(): Flow` 直接驱动列表页 |
| VM 范式 | `.../ui/page/CollectViewModel.kt`（`private val _items = MutableStateFlow` + `observeAll().collect`） | 下载列表 VM 照抄 |
| 设置门面 | `.../ui/settings/UiSettingsState.kt`、`.../util/KVKeys.kt`、`.../util/kv/KVKeySpec.kt` | 新增 2 个设置键 |
| FGS 范式 | `.../player/PlaybackService.kt`（NotificationChannel / `startForegroundSafely` / `attachBaseContext` 过 `LanguageManager.wrap` / `START_NOT_STICKY`） | 下载服务照抄骨架 |
| 列表页组件 | `.../ui/components/EdgeToEdgeTopBar.kt`（`AppTopBarScaffold`）、`.../ui/components/LoadStateBox.kt`、`.../ui/components/CapsuleSegmentedButton.kt`、`.../ui/page/RecordsPage.kt`（编辑/删除范式） | 下载页直接组合 |
| 详情页插入点 | `.../ui/DetailEpisodes.kt`（`EpisodeRow` 标题行 `PillAction`、`EpisodeCard` / `EpisodeNameCard`、`EpisodeSheet` 网格）、`.../ui/DetailContent.kt`（item 顺序）、`.../ui/DetailViewModel.kt`（`onEpisodeClick` / `toggleCollect` 范式、`_toastEvent`） | 加角标与批量入口 |
| 本地播放 | `.../player/engine/MediaSources.kt`（`DefaultDataSource.Factory(appContext, httpFactory)` 原生支持 `file://` / `content://`） | 无需改造 DataSource |
| 权限 | `AndroidManifest.xml` 已有 `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` / `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` / `WAKE_LOCK` / `READ_MEDIA_VIDEO` / `MANAGE_EXTERNAL_STORAGE` | 仅需补 `FOREGROUND_SERVICE_DATA_SYNC` |
| 跳转 | `.../ui/page/Jump.kt`（`jumpToDetail(id, sourceKey, title, picture, collect)`） | 下载页点条目回详情页 |

## 2. 目标与非目标

**目标**

- G1 单集下载、批量下载（当前线路全部集）、队列与并发控制。
- G2 暂停 / 继续（断点续传）/ 取消 / 失败重试 / 删除 / 清空。
- G3 任务持久化：进程被杀、App 重启后任务与进度仍在。
- G4 下载列表页：下载中 / 已完成分组、实时进度与速度、空间统计、编辑删除、清空。
- G5 详情页：每集下载角标三态、批量下载确认弹窗。
- G6 已完成任务走本地文件播放，观看进度按原 key 继承。
- G7 前台服务 + 进度通知；仅 WiFi、并发数设置。
- G8 M3U8 分段下载与合并（对齐 iOS 的 `m3u8` 类型）。

**非目标**

- N1 **不做磁力下载**：iOS 的 `magnet` 依赖服务器转换；AVBox 的 `Thunder.kt` 只做「解析后边下边播」，不纳入本次范围。
- N2 **不做加密 HLS / DRM**：`#EXT-X-KEY` 的 `SAMPLE-AES`、Widevine/FairPlay 一律不支持；`AES-128` 视 §11 决策。
- N3 不改造播放器内核与 `com.github.catvod.**` 任何签名。
- N4 不引入 WorkManager / ExoPlayer `DownloadManager` / 新 HTTP 栈（理由：现有 FGS + OkHttp 已够，避免双套缓存与任务模型）。
- N5 不做多进程 / 多实例。
- N6 不迁移 iOS 端已有下载数据。

## 3. 方案总览

见 §0 三阶段表。每阶段结束必须：`:app:assembleDebug` 通过 + 对应真机验证项通过 + 静态自检（§10.2），不一次性糊完再报完成。

## 4. 数据层设计

### 4.1 枚举

```kotlin
enum class DownloadType { HTTP, M3U8 }          // 磁力、HLS 专有类型不在 v1
enum class DownloadState {
    WAITING, PREPARING, DOWNLOADING, PAUSED,
    MERGING, COMPLETED, FAILED, CANCELLED
}
```

> 状态名与 iOS 一一对应，便于对照与后续扩展；`HLS`（AVAssetDownloadTask）无 Android 等价物，M3U8 已覆盖绝大多数 HLS 源。

### 4.2 `DownloadTask` 实体

新建 `app/src/main/java/com/github/avbox/osc/data/DownloadTask.kt`，骨架照 `data/VodCollect.kt`（`@PrimaryKey(autoGenerate = true)` + `@ColumnInfo` + `Serializable`）。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Int (PK autoGen) | 主键 |
| `cid` | String? | 订阅隔离（复用 `CurrentSubscription.cid()`，与收藏/历史一致） |
| `sourceKey` | String? | 源 key |
| `vodId` / `vodName` / `vodPic` | String? | 影片元数据 |
| `episodeName` | String? | 集名 |
| `episodeIndex` | Int | 集下标（按下载时 `episodes` 顺序） |
| `episodeUrl` | String? | 原始播放地址（含 `@Headers=` 后缀） |
| `downloadType` | Int | `DownloadType.ordinal` |
| `state` | Int | `DownloadState.ordinal` |
| `downloadedBytes` / `totalBytes` | Long | 进度（`totalBytes <= 0` 表示未知） |
| `filePath` | String? | 完成后的绝对路径（相对 App 私有目录，不存盘符语义） |
| `errorMessage` | String? | 失败原因（已本地化文案或错误码） |
| `retryCount` | Int | 已重试次数 |
| `createTime` / `updateTime` | Long | 排序与清理依据 |

> 速度（`speed`）**不落库**：由 `DownloadManager` 在内存按采样计算，避免高频写库。

### 4.3 DAO

新建 `data/DownloadTaskDao.kt`，照 `VodCollectDao.kt`：

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(task: DownloadTask): Long
@Update suspend fun update(task: DownloadTask)
@Query("select * from downloadTask order by createTime desc") fun observeAll(): Flow<List<DownloadTask>>
@Query("select * from downloadTask where cid=:cid and sourceKey=:sourceKey and vodId=:vodId order by episodeIndex")
fun observeByVod(cid: String?, sourceKey: String?, vodId: String?): Flow<List<DownloadTask>>
@Query("select * from downloadTask where id=:id") suspend fun getById(id: Int): DownloadTask?
@Query("select * from downloadTask where state in (:states) order by createTime") suspend fun getByStates(states: List<Int>): List<DownloadTask>
@Delete suspend fun delete(task: DownloadTask): Int
@Query("delete from downloadTask where state=:completed") suspend fun deleteCompleted(completed: Int)
@Query("delete from downloadTask") suspend fun deleteAll()
```

### 4.4 Repository

新建 `data/DownloadRepository.kt`，照 `CollectRepository.kt`（`interface` + `internal class RoomDownloadRepository(private val dao: () -> DownloadTaskDao)`）：

- `observeAll(): Flow<List<DownloadTask>>`
- `observeByVod(...): Flow<List<DownloadTask>>`
- `enqueue(tasks: List<DownloadTask>): List<Long>`
- `update(task)` / `updateState(id, state, errorMessage)` / `updateProgress(id, downloaded, total)`
- `delete(id)` / `clearCompleted()` / `clearAll()` / `pendingForResume()`

### 4.5 数据库升级

- `data/AppDataBase.kt`：`entities += DownloadTask::class`，`version = 3`，新增 `abstract fun getDownloadTaskDao(): DownloadTaskDao`
- `data/AppMigrations.kt`：新增 `MIGRATION_2_3`（`CREATE TABLE IF NOT EXISTS downloadTask (...)`，字段与 §4.2 一致，`id` 自增主键）
- `data/AppDataManager.kt`：`addMigrations(MIGRATION_1_2, MIGRATION_2_3)`
- `data/AppGraph.kt`：`val downloadRepository: DownloadRepository by lazy { RoomDownloadRepository { AppDataManager.get().getDownloadTaskDao() } }`

> schema 导出目录已配置（`app/build.gradle.kts` 的 `ksp { arg("room.schemaLocation", ...) }`），升级后确认 `app/schemas/.../3.json` 生成并入库。

## 5. 下载引擎与前台服务

### 5.1 `net/download/FileDownloader.kt`（HTTP 直链）

照 `api/SpiderLoader.kt:124 downloadJarAsync` 的成熟结构：

```kotlin
class FileDownloader(
    private val client: OkHttpClient = HttpClients.defaultClient ?: OkHttp.client(DOWNLOAD_TIMEOUT_SECONDS),
) {
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Result<File>
    fun cancel(tag: String)
}
```

- **断点续传**：目标 `.tmp` 已存在且长度 > 0 时发 `Range: bytes=<len>-`；服务端返回 `206` 则续写，返回 `200` 则截断重下。
- **原子落盘**：写 `<name>.tmp`，完成后 `renameTo(<name>.<ext>)`；失败回退 `FileUtils.copyFile`。
- **进度节流**：满足「距上次回调 ≥ 200ms」或「增量 ≥ 256KB」才回调，避免高频写库（§11 风险项）。
- **取消**：`Call.cancel()` + 协程 `invokeOnCancellation`；删除 `.tmp`。
- **重试**：失败按 `retryCount < 3` 指数退避（1s / 2s / 4s）重试；`IOException` 与 5xx 可重试，4xx 不重试。
- **header**：`util/` 提取共用 `parseUrlWithHeaders(raw): Pair<String, Map<String,String>>`（合并 `VodImages.parseVodPicUrl` 与 `PushUrlParser.parseMarkedHeaders` 的能力），每个 header 过 `HeaderGuard.isSendable`。

### 5.2 `net/download/M3u8Downloader.kt`

- 拉取 manifest（复用同一 client + headers）→ 解析 `#EXTINF` 与分片 URL（支持相对路径）→ 分片并发下载（默认 4，上限 6）→ 顺序拼接为 `.ts`（**不强行改名 `.mp4`**，见 §11）。
- 记录 `completedSegments / totalSegments` 到任务（用于进度与续传：已存在的 `segment_<n>.ts` 跳过）。
- `#EXT-X-KEY`：`METHOD=NONE` 支持；`AES-128` 见 §11 决策；`SAMPLE-AES` 直接判失败并提示「该集为加密流，暂不支持下载」。
- `#EXT-X-DISCONTINUITY`：按顺序拼接即可（TS 流本身允许）。

### 5.3 `download/DownloadService.kt`（前台服务）

照 `player/PlaybackService.kt`：

- `onCreate`：`NotificationChannel`（低优先级、静默）、初始化 `DownloadManager`。
- `onStartCommand`：处理 action（`ACTION_ENQUEUE` / `PAUSE` / `RESUME` / `CANCEL` / `CLEAR_COMPLETED`），`startForegroundSafely()`。
- **队列调度**：`Semaphore(maxConcurrent)`（默认 3，读设置）；按 `createTime` 顺序取 `WAITING` 任务；WiFi-only 开启且当前非 WiFi 时，任务保持 `WAITING` 并更新通知文案「等待 WiFi」。
- **通知**：单条汇总通知（总进度 + 当前任务数 + 当前集名），完成/失败时发一次性提示；全部结束 `stopForeground(STOP_FOREGROUND_DETACH)`。
- `START_NOT_STICKY`；`attachBaseContext` 过 `LanguageManager.wrap`；文案用 `LanguageManager.localized(this).getString(...)`。
- 进程重启后：读 `pendingForResume()`（`DOWNLOADING` / `WAITING`），状态改 `PAUSED`，由用户在列表页继续或由设置「自动恢复」触发（v1 采用手动，见 §11）。

### 5.4 `download/DownloadManager.kt`（编排）

- `object DownloadManager`，持有 `private val _state = MutableStateFlow(DownloadUiState(...))` + 对外 `val state: StateFlow<...>`（合规单例形态，同 `UiSettingsState`，不是 `App.setXxx` 式全局可变量槽）。
- 职责：入队去重（同 `cid+sourceKey+vodId+episodeName` 已存在则跳过）、调度 Service、把进度/状态写 Repository、内存速度采样、失败重试计数、`pause/resume/cancel/retry/delete/clearCompleted/clearAll`。
- 与 UI 的边界：UI 只读 `state` 与 `downloadRepository.observeAll()`；动作全部经 VM → `DownloadManager`。

### 5.5 存储路径

```
context.getExternalFilesDir(null)/downloads/<sourceKey>/<vodName>/<episodeName>.<ext>
```

- 文件名：`sanitizeFileName`（过滤 `\/:*?"<>|`、去首尾空格、超长截断 + hash 后缀防冲突）。
- 目录：`FileUtils.createNoMediaMarkerIfAbsent(dir)` 建 `.nomedia`。
- **不硬编码盘符**（AGENTS 红线），不申请 `MANAGE_EXTERNAL_STORAGE`；App 私有外部目录可直接 `file://` 播放。
- 下载前用 `StatFs` 检查剩余空间（期望大小未知时按 100MB 兜底，同 iOS `hasEnoughSpace`）。

### 5.6 iOS → Android 映射

| iOS | Android |
|---|---|
| `DownloadManager`（单例 + `@Published tasks`） | `DownloadManager`（object + 只读 `StateFlow`）+ `DownloadService`（FGS） |
| `DownloadServicing` 协议 | `DownloadRepository` + `DownloadManager` 的公开方法 |
| `UserDefaults` JSON 持久化 | Room `downloadTask` 表（理由：进度高频、需 `Flow`、MMKV 异步） |
| `URLSessionConfiguration.background` | 前台服务 `dataSync` + OkHttp 流式写盘 |
| `downloadTask(withResumeData:)` | `Range: bytes=` 续传 |
| `AVAssetDownloadTask`（HLS） | 不支持；M3U8 分片合并覆盖 |
| `DownloadCache`（文档目录 + 空间） | `getExternalFilesDir/downloads` + `StatFs` |
| `DownloadListView`（两个 Section） | `DownloadScreen`（两个分组 + 顶栏操作） |
| `ProfileView → DownloadListView` | 「我的」页（`SettingsPage`）→ `DownloadActivity` |

## 6. UI 设计

### 6.1 下载列表页

- 新建 `ui/DownloadActivity.kt`：照 `ui/PlaySettingsActivity.kt`（`BaseActivity` + `ComposeView(R.id.compose_view)` + `AVBoxTheme`，`companion object fun start(context)`）；`AndroidManifest.xml` 声明。
- 新建 `ui/page/DownloadScreen.kt`：
  - `AppTopBarScaffold`：标题「下载」；`actions`：全部暂停 / 全部开始 / 清空已完成 / 清空全部（后两者走 `AVBoxAlertDialog` 二次确认）。
  - 内容：`LazyColumn`，两个 sticky 分组标题「下载中」「已完成」（对齐 iOS 的两个 `Section`）。
  - 任务行：海报缩略（`VodPoster`）+ 集名（无则片名）+ 状态文案 + 大小；下载中显示速度与百分比 + `LinearProgressIndicator`；按状态显示操作按钮（暂停 / 继续 / 取消 / 重试 / 删除）。
  - 已完成行：点击 → 打开详情页定位该集（`jumpToDetail`），由详情页走本地播放（§8）。
  - 空态：`LoadStateBox(LoadState.Empty)`；底部/顶栏显示「已用空间 / 可用空间」。
- 新建 `ui/page/DownloadViewModel.kt`：`private val _items = MutableStateFlow<List<DownloadTask>>(emptyList())` + `val items: StateFlow<...>`；`init` 里 `downloadRepository.observeAll().collect`；动作方法转发 `DownloadManager`；速度/分组由 `combine(items, DownloadManager.state)` 派生。

### 6.2 详情页

- `ui/DetailEpisodes.kt`
  - `EpisodeRow` 标题行加第三个 `PillAction`（图标 + 「批量下载」），复用同文件 private `PillAction`。
  - `EpisodeCard` / `EpisodeNameCard` 右上角加下载角标（三态：↓ / 环形进度或百分比 / ✓），点击 `vm.onDownloadClick(index)`；`EpisodeSheet` 网格项同样加角标。
  - 角标状态来自 `vm.downloadStateByEpisode: StateFlow<Map<String, DownloadState>>`（按集名索引）。
- `ui/DetailViewModel.kt`
  - `fun onDownloadClick(index: Int)`：取 `episodes[index]`，判类型（`m3u8` 走 M3U8 下载器；其余走直链），`DownloadManager.enqueue(...)`，`_toastEvent` 提示已加入。
  - `fun onBatchDownload()`：当前 `playFlag` 全部集，跳过已下载与不支持类型，弹确认「共 N 集，跳过 M 集」。
  - `fun onOpenDownloads()`：交给 Activity 跳 `DownloadActivity`（照 `openMusicPlayer()` 范式）。
  - `init`/`refresh` 里订阅 `downloadRepository.observeByVod(...)`，映射为 `downloadStateByEpisode`。
- `ui/DetailContent.kt`：把 `onOpenDownloads` 串到 `DetailHero` 的 `DetailActionRow`（新增第四个 `HeroCircleButton`，图标 `ic_download`）。

### 6.3 设置页

`ui/page/SettingsPage.kt` 新增「下载」分组（位置对齐 iOS 的 `downloadSection`）：

| 行 | 组件 | 行为 |
|---|---|---|
| 下载管理 | `SettingsRow` | `DownloadActivity.start(context)`，valueText 显示「N 个任务」 |
| 仅 WiFi 下载 | `SettingsSwitchRow` | `UiSettingsState.downloadWifiOnly` |
| 同时下载数 | `SettingsOptionMenuRow`（1 / 2 / 3） | `UiSettingsState.downloadConcurrency` |
| 占用空间 / 可用空间 | `SettingsRow`（只读） | 实时计算 |
| 清空全部下载 | `SettingsRow` + `AVBoxAlertDialog` | 删任务 + 删文件 |

## 7. 设置项与资源

### 7.1 KV

| 键（`util/KVKeys.kt`） | 类型 | 默认 | `KVKeySpec` 登记 |
|---|---|---|---|
| `DOWNLOAD_WIFI_ONLY` = `"download_wifi_only"` | Boolean | `false`（对齐 iOS） | `register(key, false)` |
| `DOWNLOAD_CONCURRENCY` = `"download_concurrency"` | Int | `3`（对齐 iOS） | `register(key, 0)` |

`ui/settings/UiSettingsState.kt`：`UiSettingsConfig` 加两个字段 + `var` setter（`KV.put` + `current.copy`）+ `load()` 读取。

### 7.2 四语文案（新增约 22 条）

`values` / `values-en` / `values-b+zh+Hant` 三层必须齐全，`values-zh-rHK` 只放港式差异词：

```
download_title / download_section_active / download_section_completed
download_action_pause / download_action_resume / download_action_cancel
download_action_retry / download_action_delete / download_action_pause_all
download_action_resume_all / download_action_clear_completed / download_action_clear_all
download_state_waiting / download_state_preparing / download_state_downloading
download_state_paused / download_state_merging / download_state_completed
download_state_failed / download_state_cancelled
download_wifi_only / download_concurrency / download_used_space
download_available_space / download_manage / download_batch / download_confirm_batch
download_unsupported_stream / download_episode_added / download_no_space
download_waiting_wifi
```

文案由 VM/Service 读取时用 `LanguageManager.localized(context).getString(...)`，不在 UI 里拼中文。

### 7.3 图标（`res/drawable/`，矢量）

`ic_download.xml`（↓）、`ic_download_done.xml`（✓）、`ic_download_pause.xml`、`ic_download_resume.xml`、`ic_download_cancel.xml`、`ic_download_retry.xml`；命名沿用 `ic_*` 习惯。

## 8. 本地播放联动

1. **详情页点击已下载集**：`DetailViewModel.onEpisodeClick(index)` 若该集有 `COMPLETED` 任务 → 构造 `VodInfo.copy(seriesMap = 把该集 url 换成 "file://" + filePath)` → `preparePlaySession()`。进度键由 `sourceKey + id + playFlag + playIndex + 集名` 组成（与 URL 无关），观看进度自动继承。
2. **解析链短路**（必须）：
   - `sourcedata/PlayLoader.kt` 的 `playPrepared` 最早处判 `file://` / `content://` → 直接 `postPlayResult(createDirectPlayResult(...))`（`parse=0`），不进爬虫/解析。
   - 放宽 `PlayLoader.shouldDirectPlay` 的 http 判断（唯一必须改的播放侧逻辑）。
   - `DefaultConfig.isVideoFormat` 对本地路径按扩展名判 true（避免 `parse=1`）。
   - `PlayUrlResolver.jsonParse` 的 `startsWith("http")` 分支不得吞掉 `file://`（现有逻辑会 `return null`，需在调用前短路）。
3. **下载页点击已完成**：`jumpToDetail(...)` 打开详情页并定位该集（复用现有跳转），由详情页走本地播放；**不在下载页另起播放链路**，保持 `PlaybackService` 单一所有权。
4. `player/engine/MediaSources.kt` 的 `DefaultDataSource.Factory` 已原生支持 `file://`，无需改造。

## 9. 架构约束对照（AGENTS.md）

| 约束 | 本方案落点 |
|---|---|
| `base` 只放 `App`，不依赖 `ui` | 不涉及 |
| `player.core` 不依赖兄弟包/api/server/ui/data | 不改 `player.core` |
| `server` 不 import api/base/player/sourcedata/catvod | 不涉及 |
| `data` 只依赖 `bean`/`io`/`util`/`event` | `DownloadTask`/DAO/Repository 只做 DB；取流在 `net/download/`，不进 `data/` |
| Composable 只渲染，禁 import `api/base/data/sourcedata/net` | 下载页只经 `DownloadViewModel` 的 `StateFlow`；VM 不在判定域 |
| 不得新增包级循环 | 新增 `net/download/` 与 `download/`，`download` → `net`/`data`/`util`，无反向 |
| `MutableStateFlow`/`mutableStateOf`/`MutableSharedFlow` 必须 private | `DownloadManager` / `DownloadViewModel` 全部 `private val _x` + `asStateFlow()` |
| `ui/` 不得直写别人状态、不得直接调度 | UI 只调 VM 方法；调度在 `DownloadManager` |
| 禁 EventBus | 进度用 Room `Flow` + `DownloadManager.state` |
| 禁全局单槽 | 用 `AppGraph` lazy + `DownloadManager` 只读 `StateFlow` |
| 零注释（仅许可证 / `i18n: keep`） | 新代码不写解释性注释 |
| 不用裸 `!!` | `?: return` / `requireNotNull(x) { ... }` |
| 不硬编码盘符/路径 | `getExternalFilesDir` + 相对路径 |
| 改 `com.github.catvod.**` 需 `javap` 符号守恒 | 不修改该包 |
| 中文走 `stringResource` | UI 全部资源化；VM/Service 用 `LanguageManager.localized` |

## 10. 验证方案

### 10.1 构建与单测

```bash
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

判定：`BUILD SUCCESSFUL` + 用例 0 失败（注意：当前工作区存在 1 个既有基线失败 `NestedMapDefaultValueTest.rawHashMapDefault_degradesInnerMapType`，与本功能无关，需在报告中区分）。

建议新增纯 JVM 单测（不依赖 Android）：
- 文件名 sanitize / 扩展名推断
- M3U8 manifest 解析（相对/绝对地址、`#EXTINF`、无加密与 `#EXT-X-KEY` 判定）
- 任务去重键（`cid+sourceKey+vodId+episodeName`）
- 进度节流器（时间/字节阈值）
- 空间校验（期望值未知兜底）

### 10.2 静态自检（`.tools/` 门禁脚本不在本工作区，用等价检查）

- 裸 `!!` 0 处
- `ui/**` 的 `@Composable` 文件不新增 `api/base/data/sourcedata/net` import
- `MutableStateFlow` / `mutableStateOf` / `MutableSharedFlow` 均 `private`
- 新增中文文案 0 处硬编码（`i18n: keep` 白名单除外）
- 四语 key 集合对齐（`values` / `values-en` / `values-b+zh+Hant`，`zh-rHK` 为差异子集）
- 无硬编码盘符 / 绝对路径

### 10.3 真机端到端（vivo V2318A，网络 adb）

1. 详情页多集剧 → 选集标题行「批量下载」→ 确认弹窗显示集数 → 通知出现进度 → 列表页实时进度
2. 单集角标三态（↓ / 进度 / ✓）正确切换
3. 暂停 → 继续：走 `Range` 续传（日志确认 `206`，不从头下）
4. 取消：`.tmp` 被删、任务消失
5. 删除任务 / 清空已完成 / 清空全部
6. 已完成项 → 详情页点该集 → 本地播放（日志确认 `file://`，无 WebView 解析）
7. 仅 WiFi 开关：关 WiFi 时任务停在等待、开 WiFi 后自动继续
8. 并发数 1/2/3 切换生效；进程被杀重启后任务状态保留
9. M3U8 源下载 → 合并产物可本地播放（或按 §11 结论降级）

每步截图逐张核对（列表页、详情页角标、通知、设置项），不用 dump 文本代替视觉确认。

## 11. 风险与未决问题

| # | 风险 / 未决 | 影响 | 处理建议 |
|---|---|---|---|
| R1 | **M3U8 合并产物是 TS 流**（iOS 直接拼成 `.mp4`，实际是 TS 数据） | 部分播放器不认；扩展名与容器不符 | 输出 `.ts` 并让 ExoPlayer 本地播放；或合并后 remux（需 ffmpeg，超范围）。**待评审** |
| R2 | `AES-128` 加密 M3U8 | 需解密分片 | v1 可先判失败并提示；若要支持，用 `Cipher` + `#EXT-X-KEY` 的 URI 拉 key。**待评审** |
| R3 | `SAMPLE-AES` / DRM | 无法解密 | 明确不支持，UI 提示「加密流暂不支持下载」 |
| R4 | 服务端不支持 `Range` | 续传退化为重下 | 检测 `200` 时截断重下，进度归零并提示 |
| R5 | 通知权限（Android 13+） | 无通知，FGS 仍可运行 | 首次进下载页用 `PermissionHelper.requestNotificationIfNeeded` |
| R6 | `FOREGROUND_SERVICE_DATA_SYNC`（Android 14+ 类型限制） | 后台启动受限 | Manifest 声明类型；从 UI 前台动作启动，不在后台静默拉起 |
| R7 | 高频写库 | DB 压力 / 卡顿 | 进度节流（≥200ms 或 ≥256KB）+ 仅更新必要列 |
| R8 | 大文件 | `Int` 溢出 | 全部字节字段用 `Long` |
| R9 | 进程被杀 / 服务被杀 | 任务停在中间态 | 落库 `DOWNLOADING`，重启时改 `PAUSED`；v1 手动继续（不做自动恢复） |
| R10 | 下载目录被系统清理 / 用户删除 | 任务显示已完成但文件不存在 | 列表页进入时校验 `filePath` 存在性，缺失标失败并提供重下 |
| R11 | 与 iOS 的持久化差异 | 行为不完全一致 | Room 更可靠（跨进程、Flow），文档中注明差异，不做数据迁移 |
| R12 | 磁力 / HLS 专有类型 | 与 iOS 能力不对等 | 明确列为非目标；后续若需要，磁力走 `Thunder`、HLS 走 media3 `DownloadManager` 单独评估 |

**待评审决策点**

- D1：M3U8 合并产物用 `.ts` 还是 `.mp4`（R1）
- D2：`AES-128` 是否纳入 v1（R2）
- D3：下载完成后是否自动加入收藏 / 追剧
- D4：是否提供「仅 WiFi 下自动恢复未完成任务」开关（R9）
- D5：下载页入口放「我的」页（对齐 iOS）还是底部新增 tab（改动面大，需同步 `MainScreen` 三套导航）
