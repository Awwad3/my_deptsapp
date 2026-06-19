package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import com.example.ui.viewmodel.DebtViewModel
import com.example.ui.AuthMiddleware
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    RtlLayout {
                        DeyoniMainScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Simple Composable wrapper to enforce RTL directionality for Arabic representation
@Composable
fun RtlLayout(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeyoniMainScreen(
    modifier: Modifier = Modifier,
    viewModel: DebtViewModel = viewModel()
) {
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val loggedInRole by viewModel.loggedInRole.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Merchant, 1 = Customer, 2 = Logs/Notifications

    val stores by viewModel.storesState.collectAsState()
    val debts by viewModel.debtsState.collectAsState()
    val requests by viewModel.requestsState.collectAsState()
    val notifications by viewModel.notificationsState.collectAsState()
    val activityLogs by viewModel.activityLogsState.collectAsState()
    val isOnline by viewModel.isOnlineState.collectAsState()
    val syncing by viewModel.syncingState.collectAsState()
    val unsyncedCount by viewModel.unsyncedCountState.collectAsState()

    var showDetailDebt by remember { mutableStateOf<DebtWithDetails?>(null) }
    var showAddMemberDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authMiddleware = remember { AuthMiddleware(context) }

    if (loggedInUser == null) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { _ ->
                coroutineScope.launch {
                    val userId = viewModel.loggedInUser.value?.id ?: ""
                    val userEmail = viewModel.loggedInUser.value?.email ?: ""
                    
                    // Route user dynamically using GetX-inspired AuthMiddleware checking SQLite user_roles
                    val routedDestination = authMiddleware.redirect(userId, userEmail)
                    
                    selectedTab = if (routedDestination == AuthMiddleware.ROUTE_MERCHANT_DASHBOARD) {
                        0
                    } else {
                        1
                    }
                }
            }
        )
        return
    }

    Column(
        modifier = modifier.background(Color(0xFFF9FBFC))
    ) {
        // App Premium Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F1E24), Color(0xFF1B3139))
                    )
                )
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Try to load our generated premium logo, fail graceful to fallback Icon
                    val painter = painterResource(id = R.drawable.deyoni_logo)
                    Image(
                        painter = painter,
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCBE911), RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ديوني",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "نظام قيد الحسابات الموثوق عائلياً وتجارياً",
                            color = Color(0xFFA5B8C0),
                            fontSize = 11.sp
                        )
                    }
                }
                
                // Active profile and alerts row
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = loggedInUser?.name ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (loggedInRole == "merchant") "حساب التاجر 🏪" else "حساب العميل 📱",
                            color = if (loggedInRole == "merchant") Color(0xFFCBE911) else Color(0xFF81D4FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Logout Icon Button
                    IconButton(
                        onClick = {
                            viewModel.logout()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val unreadCount = notifications.size
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1F3A44), RoundedCornerShape(20.dp))
                            .clickable { selectedTab = 2 }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notif",
                                tint = if (unreadCount > 0) Color(0xFFCBE911) else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            if (unreadCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$unreadCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cloud Sync & Connectivity Control Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("sync_manager_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E6E9))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Beacon
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "متصل بالسيرفر السحابي ☁️" else "العمل بدون اتصال (Offline) 🔴",
                            color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Simulated Connection Toggle Switcher
                    Button(
                        onClick = {
                            viewModel.setOnline(!isOnline)
                            android.widget.Toast.makeText(
                                context,
                                if (!isOnline) "تم تفعيل الاتصال بالسحابة!" else "تم الانتقال إلى وضع العمل المحلي دون اتصال!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOnline) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                            contentColor = if (isOnline) Color(0xFFC62828) else Color(0xFF2E7D32)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("toggle_online_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isOnline) "قطع الاتصال 🔌" else "اتصال ⚡",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Divider(color = Color(0xFFECEFF1))

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (unsyncedCount > 0) {
                            Text(
                                text = "لديك ($unsyncedCount) عملية مسجلة محلياً وبانتظار المزامنة:",
                                fontSize = 12.sp,
                                color = Color(0xFF5A727C),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "سيتم رفعها مباشرة فور الاتصال بالسيرفر السحابي.",
                                fontSize = 10.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "مزامنة كاملة 💎",
                                fontSize = 12.sp,
                                color = Color(0xFF1B3139),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "قاعدة البيانات المحلية متطابقة تماماً مع السيرفر السحابي.",
                                fontSize = 11.sp,
                                color = Color(0xFF7A939E)
                            )
                        }
                    }

                    if (unsyncedCount > 0 || syncing) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (isOnline) {
                                    viewModel.syncData { count ->
                                        android.widget.Toast.makeText(
                                            context,
                                            "تمت مزامنة عدد $count عمليات بنجاح مع السيرفر! 🎉",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "يرجى تفعيل الاتصال بالإنترنت أولاً للبدء في المزامنة!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            enabled = !syncing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOnline) Color(0xFF1B3139) else Color(0xFFB0BEC5),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("sync_now_button")
                        ) {
                            if (syncing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري الرفع...", fontSize = 11.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "sync",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("مزامنة الآن 🔄", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Dynamic tabs based on user role
        val availableTabs = remember(loggedInRole) {
            if (loggedInRole == "merchant") {
                listOf(
                    Triple(0, "🏪 التاجر", Icons.Default.ShoppingCart),
                    Triple(2, "📜 سجلات النظام", Icons.Default.List)
                )
            } else {
                listOf(
                    Triple(1, "📱 العميل", Icons.Default.Person),
                    Triple(2, "📜 حركاتي وسجلاتي", Icons.Default.List)
                )
            }
        }

        // Animated Segmented Selector Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEEF2F4)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            availableTabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_indicator_$index")
                        .clickable { selectedTab = index }
                        .background(
                            if (isSelected) Color(0xFF1B3139) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFCBE911) else Color(0xFF5A727C),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else Color(0xFF1B3139),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Active screens display
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { targetTab ->
                when (targetTab) {
                    0 -> MerchantScreen(
                        viewModel = viewModel,
                        requests = requests,
                        debts = debts,
                        onViewDebtDetails = { showDetailDebt = it }
                    )
                    1 -> CustomerScreen(
                        viewModel = viewModel,
                        debts = debts,
                        requests = requests,
                        stores = stores,
                        activityLogs = activityLogs,
                        onAddMemberClick = { showAddMemberDialog = true },
                        onViewDebtDetails = { showDetailDebt = it }
                    )
                    2 -> AuditScreen(
                        viewModel = viewModel,
                        notifications = notifications,
                        activityLogs = activityLogs
                    )
                }
            }
        }
    }

    // Debt detailed Transactions Sheet dialog
    showDetailDebt?.let { debtWithDetails ->
        DebtHistoryDialog(
            viewModel = viewModel,
            debtWithDetails = debtWithDetails,
            onDismiss = { showDetailDebt = null }
        )
    }

    // Add Family Member input Form dialog
    if (showAddMemberDialog) {
        AddMemberDialog(
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false }
        )
    }
}

// ---------------- MERCHANT DASHBOARD ----------------
@Composable
fun MerchantScreen(
    viewModel: DebtViewModel,
    requests: List<AccountRequestWithDetails>,
    debts: List<DebtWithDetails>,
    onViewDebtDetails: (DebtWithDetails) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            // Welcome Card Ahmad
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFE2F4C5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("أحمد", color = Color(0xFF1B3139), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مرحباً أحمد 👋",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F1E24)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE3F2FD), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("التاجر المالك", color = Color(0xFF1976D2), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "🏪 مدير متجر: النهضة سوبرماركت",
                            fontSize = 13.sp,
                            color = Color(0xFF5A727C),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Requests Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🔔 طلبات فتح حساب جديدة (${requests.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )
            }
        }

        if (requests.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد طلبات فتح حساب معلقة حالياً.",
                        color = Color(0xFF5A727C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(requests) { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.family.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1B3139)
                                )
                            }
                            Text(
                                text = "📅 ${item.request.createdAt}",
                                color = Color(0xFF7A939E),
                                fontSize = 11.sp
                            )
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF1F5F7))
                        
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { viewModel.rejectRequest(item.request.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD32F2F))
                            ) {
                                Text("رفض", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { viewModel.approveRequest(item.request.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("موافقة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Customer Debts Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📊 حسابات ديون العملاء المعتمدة:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )
            }
        }

        if (debts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لا توجد دفاتر ديون معتمدة نشطة.",
                        color = Color(0xFF5A727C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(debts) { debt ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF0F1E24), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = debt.family.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF0F1E24)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الحساب: ${debt.debt.title}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5A727C)
                                )
                            }

                            // Balance Badge
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("الرصيد القائم", color = Color(0xFFE65100), fontSize = 9.sp)
                                    Text(
                                        text = "${debt.debt.totalBalance} ريال",
                                        color = Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row {
                            Button(
                                onClick = { onViewDebtDetails(debt) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3139)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("عرض الحركات", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // New transaction register form
        item {
            MerchantAddTransactionCard(viewModel = viewModel, debts = debts)
        }
    }
}

// Interactive Box matching: "📝 تسجيل حركة جديدة:"
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantAddTransactionCard(
    viewModel: DebtViewModel,
    debts: List<DebtWithDetails>
) {
    if (debts.isEmpty()) return

    var selectedDebtIndex by remember { mutableStateOf(0) }
    var itemText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedActorIndex by remember { mutableStateOf(0) }

    val actors = listOf("أحمد (المالك)", "صالح (موظف)", "سالم (موظف)")

    var isDebtDropdownExpanded by remember { mutableStateOf(false) }
    var isActorDropdownExpanded by remember { mutableStateOf(false) }
    var txType by remember { mutableStateOf("charge") } // charge, payment, refund

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBE911)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF1B3139))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📝 تسجيل حركة وسداد دين جديدة:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Select Debt ledger dropdown
            Text("اختر حساب العميل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFFF1F5F7), RoundedCornerShape(8.dp))
                    .clickable { isDebtDropdownExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = debts[selectedDebtIndex].family.name,
                        color = Color(0xFF0F1E24),
                        fontSize = 14.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF1B3139))
                }

                DropdownMenu(
                    expanded = isDebtDropdownExpanded,
                    onDismissRequest = { isDebtDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f).background(Color.White)
                ) {
                    debts.forEachIndexed { idx, debt ->
                        DropdownMenuItem(
                            text = { Text(debt.family.name, fontSize = 13.sp) },
                            onClick = {
                                selectedDebtIndex = idx
                                isDebtDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Transaction Type Selector tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEEF2F4)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val types = listOf("charge" to "شراء بالدين", "payment" to "تسديد نقدي", "refund" to "إرجاع سلع")
                types.forEach { (typeKey, typeLabel) ->
                    val isTypeSelected = txType == typeKey
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { txType = typeKey }
                            .background(
                                if (isTypeSelected) Color(0xFF5A727C) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = typeLabel,
                            color = if (isTypeSelected) Color.White else Color(0xFF1B3139),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Item / Description Field
            Text("الصنف أو البيان:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
            OutlinedTextField(
                value = itemText,
                onValueChange = { itemText = it },
                placeholder = { Text("مثال: حليب ممتاز، معلبات، خبز...", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1B3139),
                    unfocusedBorderColor = Color(0xFFCFD8DC)
                )
            )

            // Amount Field
            Text("المبلغ المطلوب (ريال):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                placeholder = { Text("0.00", fontSize = 13.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF1B3139),
                    unfocusedBorderColor = Color(0xFFCFD8DC)
                )
            )

            // Selector Actor / Employee dropdown
            Text("بواسطة الموظف:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFFF1F5F7), RoundedCornerShape(8.dp))
                    .clickable { isActorDropdownExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actors[selectedActorIndex],
                        color = Color(0xFF0F1E24),
                        fontSize = 14.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF1B3139))
                }

                DropdownMenu(
                    expanded = isActorDropdownExpanded,
                    onDismissRequest = { isActorDropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.85f).background(Color.White)
                ) {
                    actors.forEachIndexed { idx, actor ->
                        DropdownMenuItem(
                            text = { Text(actor, fontSize = 13.sp) },
                            onClick = {
                                selectedActorIndex = idx
                                isActorDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Save action Button
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 50.0
                    val items = if (itemText.isBlank()) "مبيعات عامة بالتراضي" else itemText
                    val debt = debts[selectedDebtIndex]
                    viewModel.saveTransaction(
                        debtId = debt.debt.id,
                        transactionType = txType,
                        amount = amount,
                        description = items
                    )
                    // Reset fields elegantly
                    itemText = ""
                    amountText = ""
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3139)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Done, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ الحركة وتحديث الدفتر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}


// ---------------- CLIENT DASHBOARD (📱 واجهة العميل) ----------------
@Composable
fun CustomerScreen(
    viewModel: DebtViewModel,
    debts: List<DebtWithDetails>,
    requests: List<AccountRequestWithDetails>,
    stores: List<Store>,
    activityLogs: List<ActivityLog>,
    onAddMemberClick: () -> Unit,
    onViewDebtDetails: (DebtWithDetails) -> Unit
) {
    val familyMembers by viewModel.familyMembersState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredStores by viewModel.filteredStores.collectAsState()
    var activeSubTab by remember { mutableStateOf(0) } // 0 = الحسابات والمتاجر, 1 = سجل النشاطات والتعديلات

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            // Welcome Header Card Khaled
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFE2F4C5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("خالد", color = Color(0xFF1B3139), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "مرحباً خالد 👋",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F1E24)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("رئيس العائلة", color = Color(0xFF2E7D32), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "📌 عائلة الحسامي • الرمز: FAM-HUSAMI-777",
                            fontSize = 13.sp,
                            color = Color(0xFF5A727C),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = 0 }
                        .background(
                            if (activeSubTab == 0) Color(0xFF1B3139) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💳 حساباتي والمتاجر",
                        color = if (activeSubTab == 0) Color.White else Color(0xFF455A64),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeSubTab = 1 }
                        .background(
                            if (activeSubTab == 1) Color(0xFF1B3139) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📜 سجل العمليات والتعديلات",
                        color = if (activeSubTab == 1) Color.White else Color(0xFF455A64),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (activeSubTab == 0) {
            // Search Stores Section
            item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFEAEFF2), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF1B3139))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔍 بحث عن متجر تجاري مالي:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3139)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("ادخل اسم السوبرماركت أو الحي...", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1B3139),
                            unfocusedBorderColor = Color(0xFFCFD8DC)
                        )
                    )
                }
            }
        }

        // Nearby Stores List Card
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🏪 المتاجر الشريكة القريبة:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )
            }
        }

        if (filteredStores.isEmpty()) {
            item {
                Text(
                    text = "لا توجد متاجر مطابقة للبحث حالياً.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }
        } else {
            items(filteredStores) { store ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "📍 ${store.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F1E24)
                            )
                            Text(
                                text = "المسافة: 200م • ${store.address ?: ""}",
                                color = Color(0xFF7A939E),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Button(
                            onClick = { viewModel.requestAccount(store.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF88C057)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("طلب فتح حساب", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Pending account requests sent by this family
        val pendingRequests = requests.filter { it.request.familyId == viewModel.husamiFamilyId }
        if (pendingRequests.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⏳ طلباتي المعلقة لفتح حساب (${pendingRequests.size}):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3139)
                    )
                }
            }

            items(pendingRequests) { req ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFECEFF1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = req.store.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F1E24)
                            )
                            Text(
                                text = "تاريخ الطلب: ${req.request.createdAt}",
                                color = Color(0xFF7A939E),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFF8E1), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⏳ قيد الانتظار لموافقة التاجر",
                                color = Color(0xFFE65100),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Customer's Personal Debts
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF1B3139), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "📊 حسابات ديوني النشطة:",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )
            }
        }

        // Filter the debts relative to Khaled's family
        val khaledDebts = debts.filter { it.debt.familyId == viewModel.husamiFamilyId }
        if (khaledDebts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ليس لديك حسابات نشطة حالياً. اطلب فتح حساب بالأعلى.",
                        color = Color(0xFF5A727C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(khaledDebts) { debt ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "🏪 ${debt.store.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B3139)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "آخر حركة: أحمد (التاجر) • 50 ريال",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7A939E)
                                )
                            }

                            // Balance display
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الرصيد القائم", color = Color(0xFFD32F2F), fontSize = 10.sp)
                                Text(
                                    text = "${debt.debt.totalBalance} ريال",
                                    color = Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onViewDebtDetails(debt) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3D44)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("عرض تفاصيل الحركات", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Family Management Module
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF1B3139))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "👨‍👩‍👦 إدارة أفراد العائلة المعتمدين:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B3139)
                            )
                        }

                        Button(
                            onClick = onAddMemberClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3139)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة جديد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (familyMembers.isEmpty()) {
                        Text("جاري تحميل أفراد عائلتك...", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            familyMembers.forEach { member ->
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFB), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFFCFD8DC), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF5A727C), modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = member.user.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F1E24)
                                            )
                                            Text(
                                                text = member.user.phoneNumber ?: "-",
                                                fontSize = 10.sp,
                                                color = Color(0xFF7A939E)
                                            )
                                        }
                                    }

                                    val badgeColor = when (member.member.memberRole) {
                                        "admin" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
                                        else -> Color(0xFFECEFF1) to Color(0xFF455A64)
                                    }
                                    val roleText = when (member.member.memberRole) {
                                        "admin" -> "مدير"
                                        else -> "عضو عائلة"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(badgeColor.first, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = roleText,
                                            color = badgeColor.second,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        } else {
            // Display customer timeline
            val familyMemberIds = familyMembers.map { it.user.id }.toSet()
            val familyDebts = debts.filter { it.debt.familyId == viewModel.husamiFamilyId }
            val familyDebtIds = familyDebts.map { it.debt.id }.toSet()
            val familyRequestIds = requests.filter { it.request.familyId == viewModel.husamiFamilyId }.map { it.request.id }.toSet()

            val familyLogs = activityLogs.filter { log ->
                log.userId in familyMemberIds ||
                log.details?.contains(viewModel.husamiFamilyId) == true ||
                log.details?.contains("الحسامي") == true ||
                (log.entityType == "account_requests" && log.entityId in familyRequestIds) ||
                (log.entityType == "debt_transactions" && log.userId in familyMemberIds)
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF1B3139),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔎 التعديلات والمصادقات الأخيرة على حساباتك (${familyLogs.size}):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B3139)
                    )
                }
            }

            if (familyLogs.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "لا توجد تعديلات أو حركات مسجلة حالياً لعائلتك.",
                                color = Color(0xFF5A727C),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(familyLogs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFECEFF1)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val (badgeBg, badgeFg) = when {
                                    log.action.contains("الموافقة") || log.action.contains("تسديد") || log.action.contains("دفع") -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
                                    log.action.contains("رفض") || log.action.contains("حذف") || log.action.contains("إلغاء") -> Color(0xFFFFEBEE) to Color(0xFFD32F2F)
                                    log.action.contains("شراء") || log.action.contains("قيد") || log.action.contains("حركة") -> Color(0xFFFFF3E0) to Color(0xFFE65100)
                                    else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
                                }

                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = log.action,
                                        color = badgeFg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = log.createdAt,
                                    fontSize = 11.sp,
                                    color = Color(0xFF7A939E)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = log.details ?: "تفاصيل العملية غير متوفرة",
                                fontSize = 13.sp,
                                color = Color(0xFF0F1E24),
                                fontWeight = FontWeight.Medium
                            )

                            if (log.ipAddress != null || log.userAgent != null) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Color(0xFFECEFF1)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Security verified",
                                            tint = Color(0xFF88C057),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "عملية موثقة وآمنة",
                                            fontSize = 9.sp,
                                            color = Color(0xFF7A939E),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "الجهاز: ${log.userAgent ?: "آمن"} • IP: ${log.ipAddress ?: "محلي"}",
                                        fontSize = 9.sp,
                                        color = Color(0xFF7A939E)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ---------------- AUDIT SCREEN (📜 الإشعارات وسجل العمليات والمزامنة السحابية) ----------------
@Composable
fun AuditScreen(
    viewModel: DebtViewModel,
    notifications: List<Notification>,
    activityLogs: List<ActivityLog>
) {
    var activeSubTab by remember { mutableStateOf(0) } // 0 = Notifications, 1 = Activity Logs, 2 = Cloud Sync
    val syncService = viewModel.syncService
    
    val isOnline by syncService.isOnlineRx.collectAsState()
    val isSyncing by syncService.isSyncingRx.collectAsState()
    val conflicts by syncService.activeConflicts.collectAsState()
    val syncLogs by syncService.syncLogs.collectAsState()
    val activeStrategy by syncService.selectedStrategy.collectAsState()
    val unsyncedCount by viewModel.unsyncedCountState.collectAsState()
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Toggle Button inside sub-dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .background(Color(0xFFECEFF1), RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeSubTab = 0 }
                    .background(
                        if (activeSubTab == 0) Color(0xFF1B3139) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔔 التنبيهات (${notifications.size})",
                    color = if (activeSubTab == 0) Color.White else Color(0xFF1B3139),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeSubTab = 1 }
                    .background(
                        if (activeSubTab == 1) Color(0xFF1B3139) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📝 تدقيق العمليات (${activityLogs.size})",
                    color = if (activeSubTab == 1) Color.White else Color(0xFF1B3139),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clickable { activeSubTab = 2 }
                    .background(
                        if (activeSubTab == 2) Color(0xFF1B3139) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 10.dp)
                    .testTag("sync_manager_tab"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (activeSubTab == 2) Color(0xFFCBE911) else Color(0xFF1B3139),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "☁️ مـزامـنة السحاب",
                        color = if (activeSubTab == 2) Color.White else Color(0xFF1B3139),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    if (unsyncedCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE57373), CircleShape)
                                .size(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$unsyncedCount", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (activeSubTab == 0) {
                // Notifications List view
                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا توجد إشعارات أو تنبيهات واردة.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(notifications) { notif ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFEAEFF2)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFE0F2F1), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF00796B), modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = notif.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F1E24)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.message,
                                            fontSize = 12.sp,
                                            color = Color(0xFF455A64)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeSubTab == 1) {
                // System Activity Log live view (موثقة)
                if (activityLogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("سجل التدقيق فارغ حالياً.", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(activityLogs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF37474F), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = log.action,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "ID: ${log.userId.take(8)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF5A727C)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = log.details ?: "-",
                                        fontSize = 12.sp,
                                        color = Color(0xFF263238),
                                        fontWeight = FontWeight.Medium
                                    )

                                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFCFD8DC))

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "نوع الكيان: ${log.entityType}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF5A727C)
                                        )
                                        Text(
                                            text = "التاريخ: ${log.createdAt}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF5A727C)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ☁️ GetX-inspired Offline-to-Cloud Intelligent Sync & Conflict Management Panel
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        // Connection State Simulator Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isOnline) Color(0xFFA5D6A7) else Color(0xFFEF9A9A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isOnline) "حالة الاتصال: متصل وسريع 🌐" else "حالة الاتصال: غير متصل (أوفلاين) 🔌",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isOnline) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                        )
                                        Text(
                                            text = if (isOnline) "خدمة المزامنة ترفع البيانات تلقائياً بمجرد استلامها" else "يتم تدوين وحفظ الحركات محلياً في SQLite بأمان",
                                            fontSize = 10.sp,
                                            color = if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }

                                // Switch Simulator
                                Switch(
                                    checked = isOnline,
                                    onCheckedChange = {
                                        viewModel.setOnline(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2E7D32),
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = Color(0xFFC62828)
                                    ),
                                    modifier = Modifier.testTag("network_switch")
                                )
                            }
                        }
                    }

                    item {
                        // Conflict Strategy Option Segmented Controls
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFECEFF1)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "⚙️ إستراتيجية معالجة تعارض البيانات (GetX Reactive Strategy)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF1B3139)
                                )
                                Text(
                                    text = "تحدد كيفية معالجة الفروق بين القيود المحلية وسجل مزود السحاب:",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F3F4))
                                        .padding(2.dp)
                                ) {
                                    val strategies = listOf(
                                        Triple(SyncStrategy.SERVER_WINS, "السيرفر 🖥️", "server_strategy"),
                                        Triple(SyncStrategy.CLIENT_WINS, "العميل 📱", "client_strategy"),
                                        Triple(SyncStrategy.MERGE_LATEST, "تدخل/دمج 🤝", "merge_strategy")
                                    )

                                    strategies.forEach { (strat, text, id) ->
                                        val selected = activeStrategy == strat
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (selected) Color(0xFF1B3139) else Color.Transparent)
                                                .clickable {
                                                    syncService.setStrategy(strat)
                                                }
                                                .testTag(id),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = text,
                                                color = if (selected) Color.White else Color(0xFF5A727C),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Manual Sync Button card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val count = syncService.triggerSync()
                                        if (count > 0) {
                                            android.widget.Toast.makeText(context, "تم رفع ومزامنة $count حركات بنجاح مع السحاب ☁️", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("force_sync_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1B3139),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isSyncing
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("جاري المزامنة...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ابدأ المزامنة الآن ☁️", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Active Manual Conflicts displaying (If any)
                    if (conflicts.isNotEmpty()) {
                        item {
                            Text(
                                text = "⚠️ تعارضات معلقة تتطلب تدخل يدوي (${conflicts.size})",
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(conflicts) { conflict ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFBC02D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "معرّف العملية: ${conflict.transactionId}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = conflict.description,
                                        fontSize = 12.sp,
                                        color = Color(0xFF333333),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = Color(0xFFFBC02D).copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { syncService.resolveConflict(conflict, "server") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3139)),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("السيرفر 🖥️", color = Color.White, fontSize = 10.sp)
                                        }

                                        Button(
                                            onClick = { syncService.resolveConflict(conflict, "client") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("المحلي 📱", color = Color.White, fontSize = 10.sp)
                                        }

                                        Button(
                                            onClick = { syncService.resolveConflict(conflict, "merge") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("دمج ذكي 🤝", color = Color.White, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Log Terminal window
                    item {
                        Column {
                            Text(
                                text = "🖥️ لوحة تتبع وسجلات المزامنة الفورية (GetX Activity Monitor)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF5A727C),
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F1E24))
                                    .border(1.dp, Color(0xFF1F3A44), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(syncLogs) { log ->
                                        Text(
                                            text = log,
                                            color = if (log.contains("✅") || log.contains("✓")) Color(0xFF81C784)
                                                    else if (log.contains("⚠️") || log.contains("تعارض")) Color(0xFFFFF176)
                                                    else if (log.contains("❌")) Color(0xFFE57373)
                                                    else Color(0xFF81D4FA),
                                            fontSize = 9.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- Transaction ledger detail dialog list ---
@Composable
fun DebtHistoryDialog(
    viewModel: DebtViewModel,
    debtWithDetails: DebtWithDetails,
    onDismiss: () -> Unit
) {
    // Set viewmodel target
    LaunchedEffect(debtWithDetails) {
        viewModel.selectDebt(debtWithDetails.debt.id)
    }

    val transactions by viewModel.activeTransactions.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header details
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "📖 كشف حركات دفتر: ${debtWithDetails.family.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1B3139)
                        )
                        Text(
                            text = "المتجر: ${debtWithDetails.store.name} • مالي",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // Summary stats
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F7), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("الرصيد الكلي المترتب", color = Color(0xFF5A727C), fontSize = 11.sp)
                            Text("${debtWithDetails.debt.totalBalance} ريال", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("عدد الحركات المسجلة", color = Color(0xFF5A727C), fontSize = 11.sp)
                            Text("${transactions.size} حركة", color = Color(0xFF1B3139), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scroll transactions list
                Box(modifier = Modifier.weight(1f)) {
                    if (transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("لا توجد حركات مسجلة حالياً.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(transactions) { tx ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    border = BorderStroke(1.dp, Color(0xFFECEFF1)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val badge = when (tx.transaction.transactionType) {
                                                    "charge" -> Color(0xFFFFEBEE) to Color(0xFFD32F2F) to "دين +"
                                                    "payment" -> Color(0xFFE8F5E9) to Color(0xFF2E7D32) to "تسديد -"
                                                    else -> Color(0xFFE0F7FA) to Color(0xFF00838F) to "إرجاع -"
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .background(badge.first.first, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = badge.second,
                                                        color = badge.first.second,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = tx.transaction.description ?: "حركة حساب",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF0F1E24)
                                                )
                                            }

                                            Text(
                                                text = "${tx.transaction.amount} ريال",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = when (tx.transaction.transactionType) {
                                                    "charge" -> Color(0xFFD32F2F)
                                                    else -> Color(0xFF2E7D32)
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "بواسطة: ${tx.actor.name} • المستلم: ${tx.member?.name ?: "غير محدد"} " + (if (tx.transaction.isSynced == 1) "☁️" else "⏳"),
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                            val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.transaction.clientCreatedAt))
                                            Text(
                                                text = dateText,
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// --- Add Family member form dialog ---
@Composable
fun AddMemberDialog(
    viewModel: DebtViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("member") } // member, admin

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "👨‍👩‍👦 إضافة فرد عائلة جديد للحساب:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B3139)
                )

                Divider()

                Text("اسم العضو الكامل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("مثال: فاطمة الحسامي", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("رقم الهاتف:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    placeholder = { Text("05xxxxxxxx", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("الدور العائلي:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5A727C))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == "member", onClick = { role = "member" })
                        Text("عضو (سحب محدود)", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == "admin", onClick = { role = "admin" })
                        Text("مشرف (سحب كامل للبيانات)", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                viewModel.addNewFamilyMember(name, phone, role)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3139)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إضافة جديد وثاقاً", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: DebtViewModel,
    onLoginSuccess: (String) -> Unit
) {
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var loginTabState by remember { mutableStateOf(0) } // 0 = Merchant (تاجر), 1 = Customer (عميل)
    var isPasswordVisible by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Registration Form States
    var isRegisterMode by remember { mutableStateOf(false) }
    var registerNameInput by remember { mutableStateOf("") }
    var registerEmailInput by remember { mutableStateOf("") }
    var registerPasswordInput by remember { mutableStateOf("") }
    var registerRoleState by remember { mutableStateOf(0) } // 0 = Customer, 1 = Merchant
    var registerSuccessMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFCFD))
    ) {
        // Aesthetic Top Decorative Gradient wave
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F1E24), Color(0xFF1B3139))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Splash / Login Logo icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF102A35))
                        .border(1.5.dp, Color(0xFFCBE911), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Logo Lock",
                        tint = Color(0xFFCBE911),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "نظام ديوني السحابي",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = if (isRegisterMode) "سجل حساباً سحابياً جديداً في ثوانٍ" else "قم بتسجيل الدخول للوصول إلى حسابك وسجلات الديون",
                    color = Color(0xFFA5B8C0),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
                )
            }
        }

        // Rest of content in a Scrollable / Card layout
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .testTag("login_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRegisterMode) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "تسجيل حساب سحابي جديد 📝",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1B3139)
                            )
                            Text(
                                text = "قم بإنشاء حساب وتعيين دورك لتبدأ المزامنة بأمان",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    item {
                        // register name
                        OutlinedTextField(
                            value = registerNameInput,
                            onValueChange = {
                                registerNameInput = it
                                loginError = null
                                registerSuccessMessage = null
                            },
                            label = { Text("الاسم بالكامل") },
                            placeholder = { Text("مثال: عبد الرحمن الغامدي") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF5A727C)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1B3139),
                                unfocusedBorderColor = Color(0xFFCFD8DC),
                                focusedLabelColor = Color(0xFF1B3139)
                            )
                        )
                    }

                    item {
                        // register email
                        OutlinedTextField(
                            value = registerEmailInput,
                            onValueChange = {
                                registerEmailInput = it
                                loginError = null
                                registerSuccessMessage = null
                            },
                            label = { Text("البريد الإلكتروني") },
                            placeholder = { Text("example@domain.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF5A727C)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_email_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1B3139),
                                unfocusedBorderColor = Color(0xFFCFD8DC),
                                focusedLabelColor = Color(0xFF1B3139)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }

                    item {
                        // register password
                        OutlinedTextField(
                            value = registerPasswordInput,
                            onValueChange = {
                                registerPasswordInput = it
                                loginError = null
                                registerSuccessMessage = null
                            },
                            label = { Text("كلمة المرور") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF5A727C)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_password_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1B3139),
                                unfocusedBorderColor = Color(0xFFCFD8DC),
                                focusedLabelColor = Color(0xFF1B3139)
                            ),
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }

                    item {
                        // register role selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "تعيين دور المستخدم في النظام: ",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF1B3139)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF0F4F7))
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (registerRoleState == 0) Color(0xFF1B3139) else Color.Transparent)
                                        .clickable {
                                            registerRoleState = 0
                                        }
                                        .testTag("register_role_customer"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "عميل 📱",
                                        color = if (registerRoleState == 0) Color.White else Color(0xFF5A727C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (registerRoleState == 1) Color(0xFF1B3139) else Color.Transparent)
                                        .clickable {
                                            registerRoleState = 1
                                        }
                                        .testTag("register_role_merchant"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "تاجر 🏪",
                                        color = if (registerRoleState == 1) Color.White else Color(0xFF5A727C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    if (loginError != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = loginError!!,
                                    color = Color(0xFFC62828),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    if (registerSuccessMessage != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = registerSuccessMessage!!,
                                    color = Color(0xFF2E7D32),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (registerNameInput.isBlank()) {
                                    loginError = "الرجاء إدخال الاسم بالكامل"
                                    return@Button
                                }
                                if (registerEmailInput.isBlank()) {
                                    loginError = "الرجاء إدخال البريد الإلكتروني"
                                    return@Button
                                }
                                if (registerPasswordInput.isBlank()) {
                                    loginError = "الرجاء إدخال كلمة المرور"
                                    return@Button
                                }
                                val role = if (registerRoleState == 1) "merchant" else "customer"
                                viewModel.registerUser(
                                    name = registerNameInput,
                                    email = registerEmailInput,
                                    passwordHash = registerPasswordInput,
                                    selectedRole = role
                                ) { success, msg ->
                                    if (success) {
                                        registerSuccessMessage = msg
                                        loginError = null
                                        android.widget.Toast.makeText(context, "تم تسجيل الحساب بنجاح بصفة ${if (role == "merchant") "تاجر" else "عميل"}! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                        emailInput = registerEmailInput
                                        loginTabState = if (role == "merchant") 0 else 1
                                        isRegisterMode = false
                                        registerNameInput = ""
                                        registerEmailInput = ""
                                        registerPasswordInput = ""
                                    } else {
                                        loginError = msg
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("register_submit_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B3139),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "إنشاء حساب جديد ✨",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    item {
                        TextButton(
                            onClick = {
                                isRegisterMode = false
                                loginError = null
                                registerSuccessMessage = null
                            },
                            modifier = Modifier.testTag("switch_to_login_button")
                        ) {
                            Text(
                                text = "لديك حساب بالفعل؟ سجل دخولك الآن 🔑",
                                color = Color(0xFF1B3139),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    item {
                        // Segmented Controller for Merchant vs Customer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0F4F7))
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (loginTabState == 0) Color(0xFF1B3139) else Color.Transparent)
                                    .clickable {
                                        loginTabState = 0
                                        loginError = null
                                        emailInput = ""
                                        passwordInput = ""
                                    }
                                    .testTag("merchant_login_tab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (loginTabState == 0) Color(0xFFCBE911) else Color(0xFF5A727C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "بوابة التاجر 🏪",
                                        color = if (loginTabState == 0) Color.White else Color(0xFF5A727C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (loginTabState == 1) Color(0xFF1B3139) else Color.Transparent)
                                    .clickable {
                                        loginTabState = 1
                                        loginError = null
                                        emailInput = ""
                                        passwordInput = ""
                                    }
                                    .testTag("customer_login_tab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (loginTabState == 1) Color(0xFFCBE911) else Color(0xFF5A727C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "بوابة العميل 📱",
                                        color = if (loginTabState == 1) Color.White else Color(0xFF5A727C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = if (loginTabState == 0) "تسجيل الدخول - لوحة تحكم التاجر" else "تسجيل الدخول - بوابة العملاء وعائلاتهم",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1B3139)
                            )
                            Text(
                                text = if (loginTabState == 0) "الوصول لإدارة ديون الزبائن والطلبات والمصادقات" else "مراجعة ديون العائلة والتواصل السريع مع المحلات",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    item {
                        // Email Field
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                loginError = null
                            },
                            label = { Text("البريد الإلكتروني") },
                            placeholder = { Text(if (loginTabState == 0) "ahmed@alnahda.com" else "khaled@elhusami.com") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF5A727C)
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1B3139),
                                unfocusedBorderColor = Color(0xFFCFD8DC),
                                focusedLabelColor = Color(0xFF1B3139)
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }

                    item {
                        // Password Field
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                loginError = null
                            },
                            label = { Text("كلمة المرور") },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF5A727C)
                                )
                            },
                            trailingIcon = {
                                TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Text(
                                        text = if (isPasswordVisible) "إخفاء" else "إظهار",
                                        color = Color(0xFF1B3139),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF1B3139),
                                unfocusedBorderColor = Color(0xFFCFD8DC),
                                focusedLabelColor = Color(0xFF1B3139)
                            ),
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }

                    if (loginError != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = loginError!!,
                                    color = Color(0xFFC62828),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = {
                                if (emailInput.isBlank()) {
                                    loginError = "الرجاء إدخال البريد الإلكتروني"
                                    return@Button
                                }
                                val expectedRole = if (loginTabState == 0) "merchant" else "customer"
                                viewModel.loginWithEmailAndRole(emailInput, expectedRole) { success, errMsg ->
                                    if (success) {
                                        val name = viewModel.loggedInUser.value?.name ?: ""
                                        android.widget.Toast.makeText(context, "مرحباً بك يا $name! تم تسجيل الدخول بنجاح عبر SQLite 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                        onLoginSuccess(expectedRole)
                                    } else {
                                        loginError = errMsg ?: "فشل تسجيل الدخول!"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_login_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B3139),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "تسجيل الدخول الآمن",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    item {
                        TextButton(
                            onClick = {
                                isRegisterMode = true
                                loginError = null
                                registerSuccessMessage = null
                            },
                            modifier = Modifier.testTag("switch_to_register_button")
                        ) {
                            Text(
                                text = "ليس لديك حساب؟ سجل حساباً جديداً الآن 📝",
                                color = Color(0xFF1B3139),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    item {
                        // Helper Fast-testing section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            border = BorderStroke(1.dp, Color(0xFFECEFF1)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7F8)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "💡 تجربة سريعة بدون كتابة (تم التحقق من SQLite 🗄️)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B3139)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            emailInput = "ahmed@alnahda.com"
                                            viewModel.loginWithEmailAndRole(emailInput, "merchant") { success, _ ->
                                                if (success) {
                                                    android.widget.Toast.makeText(context, "تم دخول أحمد (تاجر) عبر دور SQLite بنجاح! 🏪", android.widget.Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess("merchant")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE2F0D9)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("fast_merchant_login"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("دخول كتاجر 🏪", color = Color(0xFF385723), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            emailInput = "khaled@elhusami.com"
                                            viewModel.loginWithEmailAndRole(emailInput, "customer") { success, _ ->
                                                if (success) {
                                                    android.widget.Toast.makeText(context, "تم دخول خالد (عميل) عبر دور SQLite بنجاح! 📱", android.widget.Toast.LENGTH_SHORT).show()
                                                    onLoginSuccess("customer")
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF2CC)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("fast_customer_login"),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("دخول كعميل 📱", color = Color(0xFF7F6000), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
