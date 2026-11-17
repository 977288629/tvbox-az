package com.github.avbox.osc.ui

import com.github.avbox.osc.R
import com.github.avbox.osc.bean.LiveChannelItem
import com.github.avbox.osc.bean.LiveSettingGroup
import com.github.avbox.osc.util.AppContextHolder
import com.github.avbox.osc.util.LanguageManager

internal interface LiveSettingsSource {
    fun settingGroups(): List<LiveSettingGroup>

    fun playerScale(): Int

    fun playerType(): Int

    fun connectTimeoutIndex(): Int

    fun switchChecked(position: Int): Boolean

    fun liveGroupIndex(): Int

    fun configIndex(): Int

    fun isLiveApiLineMode(): Boolean
}

internal data class LiveSettingsItemUi(
    val itemIndex: Int,
    val title: String,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val tail: String = "",
    val tailGood: Boolean = false,
)

internal data class LiveSettingsGroupUi(
    val groupIndex: Int,
    val title: String,
    val switchRow: Boolean = false,
    val longPressDelete: Boolean = false,
    val items: List<LiveSettingsItemUi> = emptyList(),
)

internal data class LiveSettingsUi(
    val sheetVisible: Boolean = false,
    val groups: List<LiveSettingsGroupUi> = emptyList(),
)

internal object LiveSettingsSnapshot {

    internal const val SWITCH_GROUP_INDEX = 4
    internal const val CONFIG_GROUP_INDEX = 6

    fun of(source: LiveSettingsSource, channel: LiveChannelItem?, sourceIndex: Int): List<LiveSettingsGroupUi> {
        val groups = LiveSettingsRules.visibleGroups(source.settingGroups(), LiveSettingsRules.hasChannelSource(channel, sourceIndex))
        return groups.mapNotNull { group ->
            val items = group.liveSettingItems ?: return@mapNotNull null
            val switchRow = group.groupIndex == SWITCH_GROUP_INDEX
            val selectedIndex = if (switchRow) -1 else selectedIndex(source, group.groupIndex, sourceIndex)
            LiveSettingsGroupUi(
                groupIndex = group.groupIndex,
                title = group.groupName.orEmpty(),
                switchRow = switchRow,
                longPressDelete = group.groupIndex == CONFIG_GROUP_INDEX && !source.isLiveApiLineMode(),
                items = items.map { item ->
                    val badge = speedBadge(group.groupIndex, item.itemSpeedMs)
                    LiveSettingsItemUi(
                        itemIndex = item.itemIndex,
                        title = item.itemName.orEmpty(),
                        checked = switchRow && source.switchChecked(item.itemIndex),
                        selected = !switchRow && item.itemIndex == selectedIndex,
                        tail = badge.first,
                        tailGood = badge.second,
                    )
                },
            )
        }
    }

    private fun speedBadge(groupIndex: Int, speedMs: Int): Pair<String, Boolean> {
        if (groupIndex != 0) return "" to false
        return when {
            speedMs > 0 -> str(R.string.live_speed_ms, speedMs) to true
            speedMs == LiveSpeedTester.RESULT_FAILED -> str(R.string.live_speed_timeout) to false
            speedMs == LiveSpeedTester.RESULT_UNSUPPORTED -> str(R.string.live_speed_unsupported) to false
            else -> "" to false
        }
    }

    private fun str(resId: Int, vararg args: Any): String {
        val context = AppContextHolder.context() ?: return ""
        return LanguageManager.localized(context).getString(resId, *args)
    }

    private fun selectedIndex(source: LiveSettingsSource, groupIndex: Int, sourceIndex: Int): Int =
        when (groupIndex) {
            0 -> sourceIndex
            1 -> source.playerScale()
            2 -> source.playerType()
            3 -> source.connectTimeoutIndex()
            5 -> source.liveGroupIndex()
            CONFIG_GROUP_INDEX -> source.configIndex()
            else -> -1
        }
}
