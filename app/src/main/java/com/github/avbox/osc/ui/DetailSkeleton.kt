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

private val DetailSkeletonHeroMinHeight = 280.dp

private val DetailSkeletonHeroMaxHeight = 460.dp

private const val DetailSkeletonHeroRatio = 0.50f

@Composable
internal fun DetailSkeleton(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val imageHeight = (configuration.screenHeightDp * DetailSkeletonHeroRatio)
        .dp
        .coerceIn(DetailSkeletonHeroMinHeight, DetailSkeletonHeroMaxHeight)
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(28.dp),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(12.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .height(16.dp),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.height(14.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(8.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(14.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(8.dp))
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp),
                shape = RoundedCornerShape(7.dp),
            )
            Spacer(Modifier.height(20.dp))
            SkeletonBox(
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                repeat(3) {
                    SkeletonBox(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                    )
                }
            }
        }
        DetailSkeletonSection()
        DetailSkeletonEpisodes()
    }
}

@Composable
private fun DetailSkeletonSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            SkeletonBox(modifier = Modifier.size(20.dp), shape = CircleShape)
            Spacer(Modifier.width(8.dp))
            SkeletonBox(
                modifier = Modifier.width(72.dp).height(22.dp),
                shape = RoundedCornerShape(8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSkeletonEpisodes(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        ) {
            SkeletonBox(modifier = Modifier.size(20.dp), shape = CircleShape)
            Spacer(Modifier.width(8.dp))
            SkeletonBox(
                modifier = Modifier.width(72.dp).height(22.dp),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.weight(1f))
            SkeletonBox(
                modifier = Modifier.width(96.dp).height(18.dp),
                shape = RoundedCornerShape(8.dp),
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
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(7.dp),
                    )
                }
            }
        }
    }
}
