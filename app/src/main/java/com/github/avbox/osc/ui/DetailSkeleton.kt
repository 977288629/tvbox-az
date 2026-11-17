package com.github.avbox.osc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
 * 详情页骨架屏: 尺寸/间距/行高与 DetailHero + DetailContent 真实布局逐项对齐,
 * 数据就绪后无缝替换, 零 CLS(布局跳变)。
 *
 * 对齐依据(来源代码常量):
 * - hero 高度: screenH * 0.50, clamp 280~460 (DetailHero.kt HERO_IMAGE_HEIGHT_RATIO)
 * - 标题: headlineSmall 24sp/32sp; 元信息行: titleMedium 16sp/24sp
 * - 简介: bodyMedium 14sp/20sp x 3行 (HeroDescriptionCollapsedLines), 行间距 14dp
 * - 播放胶囊: 180dp min x 52dp, 圆角14 (HeroPlayCapsuleHeight/MinWidth)
 * - 圆形按钮: 50dp x 3, 间距24dp (HeroCircleButtonSize/HeroCircleButtonSpacing)
 * - 线路卡: 160dp 宽 x 70dp 高, 圆角16 (DetailItemWidth/Height)
 * - 选集: 3列, 16:9 图 + 底部标题 6dp, 列/行距 8dp, 区标题 titleLarge 18sp/26sp
 * - 区块间 padding: top 16dp, 区标题 bottom 10dp, 横向 16dp
 */
@Composable
internal fun DetailSkeleton(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.50f)
        .dp
        .coerceIn(280.dp, 460.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        // hero 海报: 与 DetailHero 图片同高同圆角
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        )
        // hero 信息区: 横向 16dp, 顶部 10dp (HeroContentTopPadding)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 剧名: headlineSmall 24sp/行高32sp
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(32.dp),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(14.dp))
            // 元信息行: titleMedium 16sp/行高24sp (评分+年份+地区)
            SkeletonBox(
                modifier = Modifier
                    .width(196.dp)
                    .height(24.dp),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(14.dp))
            // 简介: bodyMedium 14sp/行高20sp x 3行 (真实折叠行数), 满宽模拟两端对齐
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(4.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(4.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .height(20.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(16.dp))
            // 播放胶囊: min 180dp x 52dp 高, 圆角14
            SkeletonBox(
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(16.dp))
            // 三个圆形按钮: 50dp, 间距 24dp
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                    )
                }
            }
        }
        // 线路区: 区块顶 16dp, 标题行底 10dp (与 DetailContent flags item 一致)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            ) {
                // 区标题图标 20dp + titleLarge 18sp/26sp 文本
                SkeletonBox(modifier = Modifier.size(20.dp), shape = CircleShape)
                Spacer(Modifier.width(8.dp))
                SkeletonBox(
                    modifier = Modifier.width(64.dp).height(26.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.weight(1f))
                // 右侧"来源: xxx" labelLarge 14sp/20sp
                SkeletonBox(
                    modifier = Modifier.width(120.dp).height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                )
            }
            // 线路卡: 160dp 宽 x 70dp 高, 间距 10dp
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) {
                    SkeletonBox(
                        modifier = Modifier
                            .width(160.dp)
                            .height(70.dp),
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        }
        // 选集区: 与 EpisodeRow 结构一致 (标题行 + 3列 16:9 网格)
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
                    modifier = Modifier.width(64.dp).height(26.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.weight(1f))
                // 右侧操作组: 倒序/全部 两个 labelLarge
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
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(20.dp),
                            shape = RoundedCornerShape(7.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f)) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        SkeletonBox(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxWidth()
                                .height(20.dp),
                            shape = RoundedCornerShape(7.dp),
                        )
                    }
                }
            }
        }
    }
}
