package com.example.conantcg.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.conantcg.data.model.PackStats
import com.example.conantcg.utils.ConanTheme
import java.io.File

// --- 1. Rarity Stats Header ---
@Composable
fun RarityStatsHeader(stats: PackStats) {
    val rarityOrder = listOf("D", "C", "CP", "R", "RP", "SR", "SRP", "SRCP", "MR", "MRP", "MRCP", "SEC", "PR")
    val overallProgress = if (stats.total > 0) stats.owned.toFloat() / stats.total.toFloat() else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D3A)),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp) // ลดช่องว่างใต้แถบลงนิดหน่อย
    ) {
        // [แก้ไข] ลด padding แนวตั้ง (vertical) เหลือ 8.dp เพื่อให้แถบแคบลง
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [แก้ไข] ลดขนาดวงกลมเปอร์เซ็นต์ลงจาก 40.dp -> 36.dp
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(36.dp), color = Color.DarkGray, strokeWidth = 4.dp)
                CircularProgressIndicator(progress = { overallProgress }, modifier = Modifier.size(36.dp), color = Color(0xFFFFD700), strokeWidth = 4.dp)
                Text("${(overallProgress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Spacer(Modifier.width(16.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 10.dp)
            ) {
                val sortedStats = stats.rarityMap.toList().sortedBy { (key, _) ->
                    val index = rarityOrder.indexOf(key)
                    if (index == -1) 999 else index
                }
                items(sortedStats) { (rarity, pair) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // [แก้ไข] ปรับขนาดฟอนต์ให้สมส่วนกับแถบที่เล็กลง
                        Text("${pair.first}/${pair.second}", color = if (pair.first == pair.second) Color(0xFFFFD700) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(rarity, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// --- 2. Pack Selection Item ---
@Composable
fun PackSelectionItem(packName: String, owned: Int, total: Int, showCollectionStats: Boolean, imageModel: Any?, isLargeImage: Boolean = false, isDownloaded: Boolean, downloadProgress: Float?, onDownloadClick: () -> Unit, onClick: () -> Unit) {
    val displayName = remember(packName) { packName.split(" ").firstOrNull() ?: packName }
    val percentage = if (total > 0) owned.toFloat() / total.toFloat() else 0f
    val itemHeight = if (showCollectionStats) 250.dp else 200.dp

    Card(
        onClick = onClick,
        modifier = Modifier.height(itemHeight).shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tabPath = Path().apply {
                    moveTo(0f, 20f)
                    lineTo(size.width * 0.4f, 20f)
                    lineTo(size.width * 0.5f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(tabPath, color = ConanTheme.BlueSuit.copy(alpha = 0.05f))
            }
            Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (showCollectionStats) Arrangement.Top else Arrangement.Center) {
                if (!showCollectionStats) Spacer(Modifier.height(8.dp))
                val targetImageHeight = 120.dp
                if (imageModel != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(imageModel).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.height(targetImageHeight).fillMaxWidth(),
                        contentScale = ContentScale.Fit,
                        loading = { Box(Modifier.fillMaxSize().background(Color.LightGray)) }
                    )
                } else {
                    Icon(Icons.Filled.Email, null, tint = ConanTheme.BlueSuit, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(text = displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showCollectionStats) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Collected", fontSize = 10.sp, color = Color.Gray)
                            Text("${(percentage * 100).toInt()}%", fontSize = 10.sp, color = ConanTheme.BlueSuit, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { percentage }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (percentage == 1f) Color(0xFF4CAF50) else ConanTheme.BowTieRed, trackColor = Color.LightGray.copy(0.3f))
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            Text("$owned / $total", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                if (downloadProgress != null) {
                    CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = ConanTheme.BlueSuit)
                } else if (isDownloaded) {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "Downloaded", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onDownloadClick, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = "Download Pack", tint = ConanTheme.BlueSuit, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterTable(
    onColorChange: (Set<String>) -> Unit,
    onTypeChange: (Set<String>) -> Unit,
    onRarityChange: (Set<String>) -> Unit,
    onLevelChange: (Set<String>) -> Unit, // [แก้] เปลี่ยนจากรับค่า (String, String) เป็น Set
    onApChange: (Set<String>) -> Unit,
    onLpChange: (Set<String>) -> Unit,    // [แก้] เปลี่ยนจากรับค่า (String, String) เป็น Set
    onPackChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    currentColors: Set<String>,
    currentTypes: Set<String>,
    currentRarities: Set<String>,
    currentAps: Set<String>,
    currentLevels: Set<String>,           // [แก้] เปลี่ยนเป็น Set
    currentLps: Set<String>,              // [แก้] เปลี่ยนเป็น Set
    currentPack: String,
    currentCategory: String,
    availablePacks: List<String>,
    availableCategories: List<String>
) {
    val colorMap = listOf("Blue" to "青", "Green" to "緑", "White" to "白", "Red" to "赤", "Yellow" to "黄", "Black" to "黒")
    val types = listOf("Partner", "Character", "Event", "Case")
    val rarities = listOf("D", "C", "CP", "R", "RP", "SR", "SRP", "SRCP", "MR", "MRP", "MRCP", "SEC", "PR")
    val aps = listOf("0", "1000", "2000", "3000", "4000", "5000", "6000", "7000", "8000", "9000")

    // [แก้] ตัดคำว่า "All" ออก เพราะถ้าไม่เลือกอะไรเลย (Set ว่าง) จะถือว่าเอาทั้งหมดอยู่แล้ว
    val levels = (1..9).map { it.toString() }
    val lps = listOf("0","1", "2", "3")

    Column(Modifier.background(Color(0xFFF9F9F9)).padding(8.dp)) {
        FilterTableRow("Package") { SimpleDropdown(listOf("All") + availablePacks, currentPack) { onPackChange(it) } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))

        FilterTableRow("Category") { SimpleDropdown(listOf("All") + availableCategories, currentCategory) { onCategoryChange(it) } }
        HorizontalDivider(color = Color.LightGray.copy(0.5f))

        FilterTableRow("Color") {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                colorMap.forEach { (engKey, kanjiLabel) ->
                    val isSel = currentColors.contains(engKey)
                    Box(
                        modifier = Modifier.size(30.dp).background(if (isSel) getColor(engKey) else Color.White, RoundedCornerShape(4.dp)).border(if (isSel) 2.dp else 1.dp, if (isSel) Color.Black else Color.LightGray, RoundedCornerShape(4.dp)).clickable { onColorChange(if (isSel) currentColors - engKey else currentColors + engKey) },
                        contentAlignment = Alignment.Center
                    ) { Text(text = kanjiLabel, color = if (isSel && (engKey == "White" || engKey == "Yellow")) Color.Black else if (isSel) Color.White else getColor(engKey), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Type") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(types) { type -> FilterChipItem(type, currentTypes.contains(type)) { onTypeChange(if (it) currentTypes + type else currentTypes - type) } } } }

        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("Rarity") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(rarities) { rarity -> FilterChipItem(rarity, currentRarities.contains(rarity)) { onRarityChange(if (it) currentRarities + rarity else currentRarities - rarity) } } } }

        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        // [แก้ไข] เปลี่ยนจาก RangeSelector มาใช้ LazyRow คล้าย Rarity
        FilterTableRow("Level") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(levels) { level ->
                    FilterChipItem(level, currentLevels.contains(level)) {
                        onLevelChange(if (it) currentLevels + level else currentLevels - level)
                    }
                }
            }
        }

        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        FilterTableRow("AP") { LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { items(aps) { ap -> FilterChipItem(ap, currentAps.contains(ap)) { onApChange(if (it) currentAps + ap else currentAps - ap) } } } }

        HorizontalDivider(color = Color.LightGray.copy(0.5f))
        // [แก้ไข] เปลี่ยนจาก RangeSelector มาใช้ LazyRow คล้าย Rarity
        FilterTableRow("LP") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(lps) { lp ->
                    FilterChipItem(lp, currentLps.contains(lp)) {
                        onLpChange(if (it) currentLps + lp else currentLps - lp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                onColorChange(emptySet()); onTypeChange(emptySet()); onRarityChange(emptySet()); onApChange(emptySet())
                onLevelChange(emptySet()); onLpChange(emptySet()); // [แก้] ส่งค่าเป็น Set ว่างเวลา Reset
                onPackChange("All"); onCategoryChange("All")
            },
            modifier = Modifier.fillMaxWidth().height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCDCDC)), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(0.dp)
        ) { Text("Reset Filters", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
    }
}

// --- UI Helpers ---

// [ALIGNMENT FIX] ปรับความกว้าง Label เป็น 70.dp เพื่อให้แถว Package/Category เริ่มต้นตรงกับ Color
@Composable
fun FilterTableRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            modifier = Modifier.width(70.dp), // ปรับให้กว้างพอสำหรับคำว่า "Package"
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = ConanTheme.TextSecondary
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun FilterChipItem(label: String, isSelected: Boolean, onClick: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .background(if (isSelected) ConanTheme.BlueSuit else Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, if (isSelected) ConanTheme.BlueSuit else Color.LightGray, RoundedCornerShape(4.dp))
            .clickable { onClick(!isSelected) }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = if (isSelected) Color.White else Color.Black, fontSize = 12.sp)
    }
}

@Composable
fun RangeSelector(options: List<String>, min: String, max: String, onChange: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(80.dp)) { SimpleDropdown(options, min) { onChange(it, max) } }
        Text(" ~ ", modifier = Modifier.padding(horizontal = 8.dp))
        Box(Modifier.width(80.dp)) { SimpleDropdown(options, max) { onChange(min, it) } }
    }
}

@Composable
fun SimpleDropdown(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    // [LAYOUT FIX] ใช้ Box ธรรมดาแทน ExposedDropdownMenuBox เพื่อแก้ปัญหา Padding แฝงที่ทำให้เยื้อง
    Box(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .clickable { expanded = true }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(selected, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(16.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White).heightIn(max = 200.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 12.sp) },
                    onClick = { onSelected(option); expanded = false },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

fun getColor(colorName: String): Color {
    return when {
        colorName.contains("Blue", true) || colorName.contains("青") -> Color(0xFF2196F3)
        colorName.contains("Green", true) || colorName.contains("緑") -> Color(0xFF4CAF50)
        colorName.contains("White", true) || colorName.contains("白") -> Color(0xFFB5B5B5)
        colorName.contains("Red", true) || colorName.contains("赤") -> Color(0xFFF44336)
        colorName.contains("Yellow", true) || colorName.contains("黄") -> Color(0xFFFFC107)
        colorName.contains("Black", true) || colorName.contains("黒") -> Color(0xFF212121)
        else -> Color.LightGray
    }
}