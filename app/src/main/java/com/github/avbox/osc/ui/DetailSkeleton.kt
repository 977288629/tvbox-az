package com.github.avbox.osc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.github.avbox.osc.ui.components.SkeletonBox

/**
 * 详情页骨架屏: 与真实渲染帧逐像素对齐(720x1612@2x 实测)。
 *
 * 对齐基准(真实布局常量 + 真机截图):
 * - hero: screenH*0.50 clamp 280~460dp, 标题条在 hero 内部底部居中(HeroLogoBottomPadding=16dp)
 * - hero 顶部悬浮按钮(返回/收藏/更多)属于 DetailHeroTopActions, Loading 态不渲染, 骨架不放
 * - 信息区: hero 外, horizontal 16dp, top 10dp(HeroContentTopPadding), 无评分行时简介直接起
 * - 简介: bodyMedium 行高 20sp, 3 行, 左对齐, 行距 4dp, 末行 66% 宽(省略号)
 * - 播放胶囊: 200x52dp 圆角 14, 居中, 距简介 16dp(HeroActionSpacing)
 * - 圆形按钮行已移除(功能收进更多菜单), 骨架不再渲染
 * - 线路区: top 16dp, 标题行(图标20+titleLarge 18sp/26sp+来源条), 卡 160x70dp 圆角 16, 间距 10dp
 * - 选集区: 纯文字卡形态(EpisodeNameCard, 常见短剧无分集图), 3 列, 卡高约 48dp 圆角 16, 间距 8dp
 */
@Composable
internal fun DetailSkeleton(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.50f)
        .dp
        .coerceIn(280.dp, 460.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(heroHeight),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            )
            // 标题: hero 内底部居中, bottom padding 16dp(与 HeroLogoBottomPadding 一致)
            SkeletonBox(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.62f)
                    .height(32.dp),
                shape = RoundedCornerShape(8.dp),
            )
        }
        // 信息区: 简介(左对齐 3 行) + 播放胶囊(居中)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(4.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth().height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(4.dp))
            SkeletonBox(
                modifier = Modifier.fillMaxWidth(0.66f).height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(16.dp))
            SkeletonBox(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(200.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            )
        }
        // 线路区
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            ) {
                SkeletonBox(modifier = Modifier.size(20.dp), shape = CircleShape)
                Spacer(Modifier.width(8.dp))
                SkeletonBox(
                    modifier = Modifier.width(48.dp).height(26.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.weight(1f))
                SkeletonBox(
                    modifier = Modifier.width(140.dp).height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(
                    modifier = Modifier
                        .width(160.dp)
                        .height(70.dp),
                    shape = RoundedCornerShape(16.dp),
                )
                SkeletonBox(
                    modifier = Modifier
                        .width(160.dp)
                        .height(70.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        // 选集区: 纯文字卡 3 列两行
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            ) {
                SkeletonBox(modifier = Modifier.size(20.dp), shape = CircleShape)
                Spacer(Modifier.width(8.dp))
                SkeletonBox(
                    modifier = Modifier.width(48.dp).height(26.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.weight(1f))
                SkeletonBox(
                    modifier = Modifier.width(52.dp).height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
                Spacer(Modifier.width(10.dp))
                SkeletonBox(
                    modifier = Modifier.width(52.dp).height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }
    }
}
