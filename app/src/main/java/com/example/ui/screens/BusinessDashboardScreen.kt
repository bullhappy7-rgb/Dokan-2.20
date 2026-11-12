package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.GlassCard
import com.example.ui.components.PlainEmptyState
import com.example.ui.components.SalesBarChart
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VividGreen
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.BusinessNavDestination
import com.example.ui.viewmodel.HistoryPeriod
import com.example.ui.viewmodel.StoreViewModel
import com.example.util.AppLanguage
import com.example.util.ShopLocale

/**
 * Main Business tab screen router.
 * Replaces the old "Coming soon" screen and directs to all dashboards, reports, and tools.
 */
@Composable
fun BusinessScreen(
    authViewModel: AuthViewModel,
    storeViewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val destination by storeViewModel.businessNavDestination.collectAsState()

    BackHandler(enabled = destination != BusinessNavDestination.DASHBOARD) {
        storeViewModel.navigateBackInBusiness()
    }

    when (destination) {
        BusinessNavDestination.DASHBOARD -> {
            BusinessDashboardScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.BAKI_SCREEN -> {
            BakiMainScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.SUPPLIERS_SCREEN -> {
            SuppliersScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.LOW_STOCK_SCREEN -> {
            LowStockScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.SLOW_SELLERS_SCREEN -> {
            SlowSellersScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_SALES -> {
            SalesReportScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_PURCHASES -> {
            PurchaseReportScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_DAY_BOOK -> {
            DayBookScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_PROFIT_LOSS -> {
            ProfitAndLossScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_CUSTOMER_STATEMENT -> {
            CustomerStatementScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_SUPPLIER_STATEMENT -> {
            SupplierStatementScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.REPORT_INVENTORY -> {
            InventoryReportScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.MONTHLY_REPORT -> {
            MonthlyReportScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.DATA_HEALTH_CHECK -> {
            DataHealthCheckScreen(
                authViewModel = authViewModel,
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
        BusinessNavDestination.TEST_CHECKLIST -> {
            TestChecklistScreen(
                storeViewModel = storeViewModel,
                modifier = modifier
            )
        }
    }
}

/**
 * Step 2: Business Tab Dashboard
 */
@Composable
fun BusinessDashboardScreen(
    storeViewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val currentLanguage by storeViewModel.currentLanguage.collectAsState()
    val period by storeViewModel.businessPeriod.collectAsState()
    val customRange by storeViewModel.businessCustomDateRange.collectAsState()

    val financials by storeViewModel.businessFinancials.collectAsState()
    val moneyNow by storeViewModel.moneyNow.collectAsState()
    val chartPoints by storeViewModel.salesChartPoints.collectAsState()
    val topProducts by storeViewModel.topSellingProducts.collectAsState()
    val categoryStats by storeViewModel.salesByCategory.collectAsState()
    val slowSellers by storeViewModel.slowSellingProducts.collectAsState()
    val lowStockList by storeViewModel.lowStockProducts.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        CustomDateRangePickerDialog(
            initialRange = customRange,
            currentLanguage = currentLanguage,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                storeViewModel.setBusinessCustomDateRange(start, end)
                showDatePicker = false
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Step 1: Shared period filter
        item {
            Spacer(modifier = Modifier.height(12.dp))
            HistoryPeriodTabs(
                currentPeriod = period,
                customRange = customRange,
                currentLanguage = currentLanguage,
                onSelectPeriod = { selected ->
                    if (selected == HistoryPeriod.CUSTOM) {
                        showDatePicker = true
                    } else {
                        storeViewModel.setBusinessPeriod(selected)
                    }
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Top 3 KPI Cards: Total Sales, Total Profit, Orders
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiStatCard(
                        title = if (currentLanguage == AppLanguage.BN) "মোট বিক্রয়" else "Total Sales",
                        value = ShopLocale.formatMoney(financials.totalSold, currentLanguage),
                        icon = Icons.Default.TrendingUp,
                        iconTint = VividGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = if (currentLanguage == AppLanguage.BN) "মোট লাভ" else "Total Profit",
                        value = ShopLocale.formatMoney(financials.totalProfit, currentLanguage),
                        icon = Icons.Default.AccountBalance,
                        iconTint = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = if (currentLanguage == AppLanguage.BN) "অর্ডার" else "Orders",
                        value = "${financials.totalOrders}",
                        icon = Icons.Default.ReceiptLong,
                        iconTint = Color(0xFFFFB300),
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Money Now Card: Stock value at cost, Baki, You owe suppliers
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("money_now_card")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = VividGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentLanguage == AppLanguage.BN) "বর্তমান স্থিতি (Money Now)" else "Money Now",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MoneyNowItem(
                                title = if (currentLanguage == AppLanguage.BN) "মজুদ পণ্যের মূল্য" else "Stock Value",
                                value = ShopLocale.formatMoney(moneyNow.stockValueAtCost, currentLanguage),
                                subtitle = "At buying cost",
                                valueColor = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            MoneyNowItem(
                                title = if (currentLanguage == AppLanguage.BN) "পাওনা বাকি" else "Baki (Owed to me)",
                                value = ShopLocale.formatMoney(moneyNow.bakiCustomersOwe, currentLanguage),
                                subtitle = "${moneyNow.debtorsCount} debtors",
                                valueColor = Color(0xFFFFB300),
                                modifier = Modifier.weight(1f)
                            )
                            MoneyNowItem(
                                title = if (currentLanguage == AppLanguage.BN) "মহাজন দেনা" else "You Owe Suppliers",
                                value = ShopLocale.formatMoney(moneyNow.debtToSuppliers, currentLanguage),
                                subtitle = "${moneyNow.suppliersCount} suppliers",
                                valueColor = Color(0xFFFF5252),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Sales Trend Chart (Day or Month)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentLanguage == AppLanguage.BN) "বিক্রয় চিত্র (Sales Chart)" else "Sales Trend",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (period == HistoryPeriod.YEAR || period == HistoryPeriod.ALL) "Monthly" else "Daily",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        SalesBarChart(points = chartPoints)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Best Selling Products (Top 5)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = if (currentLanguage == AppLanguage.BN) "সর্বোচ্চ বিক্রিত পণ্য (Top 5 Products)" else "Best-Selling Products",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (topProducts.isEmpty()) {
                            PlainEmptyState(message = "No products sold in this period")
                        } else {
                            topProducts.forEachIndexed { idx, stat ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0x265DD62C),
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    color = VividGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = stat.productName,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${stat.quantitySold} ${stat.unit} sold",
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = ShopLocale.formatMoney(stat.totalSalesAmount, currentLanguage),
                                        color = VividGreen,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Sales by Category Donut Chart
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = if (currentLanguage == AppLanguage.BN) "ক্যাটেগরি অনুযায়ী বিক্রয়" else "Sales by Category",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CategoryDonutChart(stats = categoryStats)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Tappable Action Cards: Baki, Suppliers, Low Stock, Slow Sellers
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Baki card (how many customers owe, total, tap opens Baki screen)
                ActionSummaryCard(
                    title = if (currentLanguage == AppLanguage.BN) "বাকি হিসাব (Baki Ledger)" else "Customer Baki",
                    subtitle = "${moneyNow.debtorsCount} customers owe ${ShopLocale.formatMoney(moneyNow.bakiCustomersOwe, currentLanguage)}",
                    icon = Icons.Default.People,
                    iconTint = Color(0xFFFFB300),
                    testTag = "card_open_baki",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.BAKI_SCREEN) }
                )

                // Suppliers card (total I owe, tap opens Suppliers screen)
                ActionSummaryCard(
                    title = if (currentLanguage == AppLanguage.BN) "মহাজনদের তালিকা (Suppliers)" else "Suppliers & Credit",
                    subtitle = "You owe ${ShopLocale.formatMoney(moneyNow.debtToSuppliers, currentLanguage)} to ${moneyNow.suppliersCount} suppliers",
                    icon = Icons.Default.ShoppingCart,
                    iconTint = Color(0xFFFF5252),
                    testTag = "card_open_suppliers",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.SUPPLIERS_SCREEN) }
                )

                // Low Stock card (count, tap opens low stock list)
                ActionSummaryCard(
                    title = if (currentLanguage == AppLanguage.BN) "স্বল্প মজুদের পণ্য (Low Stock)" else "Low Stock Alert",
                    subtitle = "${lowStockList.size} items at or below alert limit",
                    icon = Icons.Default.Warning,
                    iconTint = Color(0xFFFFB300),
                    testTag = "card_open_low_stock",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.LOW_STOCK_SCREEN) }
                )

                // Slow Sellers card (products with stock but no sales in the period, with stock count)
                ActionSummaryCard(
                    title = if (currentLanguage == AppLanguage.BN) "অচল পণ্য (Slow Sellers)" else "Slow-Selling Products",
                    subtitle = "${slowSellers.size} products in stock with zero sales in this period",
                    icon = Icons.Default.HourglassEmpty,
                    iconTint = Color(0xFFB388FF),
                    testTag = "card_open_slow_sellers",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.SLOW_SELLERS_SCREEN) }
                )

                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Step 3: Reports Section listing 7 reports + Monthly Report
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = if (currentLanguage == AppLanguage.BN) "ব্যবসায়িক রিপোর্টসমূহ (Reports)" else "Financial Reports",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Live reports with PDF, Excel/CSV, Print & Share export",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                ReportNavRow(
                    title = "Sales Report",
                    description = "Invoices, customers, profit, Paid & Baki status",
                    icon = Icons.Default.ReceiptLong,
                    testTag = "report_link_sales",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_SALES) }
                )

                ReportNavRow(
                    title = "Purchase Report",
                    description = "Purchase orders, suppliers, paid & credit, returns",
                    icon = Icons.Default.ShoppingBag,
                    testTag = "report_link_purchases",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_PURCHASES) }
                )

                ReportNavRow(
                    title = "Day Book",
                    description = "All cash, baki, purchases, returns in time order",
                    icon = Icons.Default.Book,
                    testTag = "report_link_daybook",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_DAY_BOOK) }
                )

                ReportNavRow(
                    title = "Profit and Loss",
                    description = "Gross & Net profit with stock-based toggle",
                    icon = Icons.Default.Assessment,
                    testTag = "report_link_pnl",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_PROFIT_LOSS) }
                )

                ReportNavRow(
                    title = "Customer Statement",
                    description = "Opening balance, Baki purchases, payments, closing",
                    icon = Icons.Default.People,
                    testTag = "report_link_cust_statement",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_CUSTOMER_STATEMENT) }
                )

                ReportNavRow(
                    title = "Supplier Statement",
                    description = "Credit purchases, payments out, returns, balance",
                    icon = Icons.Default.ShoppingCart,
                    testTag = "report_link_sup_statement",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_SUPPLIER_STATEMENT) }
                )

                ReportNavRow(
                    title = "Inventory Report",
                    description = "Stock, buying price, valuation, low stock filter",
                    icon = Icons.Default.Inventory,
                    testTag = "report_link_inventory",
                    onClick = { storeViewModel.navigateInBusiness(BusinessNavDestination.REPORT_INVENTORY) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Step 5: Monthly Report Banner
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { storeViewModel.navigateInBusiness(BusinessNavDestination.MONTHLY_REPORT) }
                        .testTag("link_monthly_report")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3300E5FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Monthly Performance Report",
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Month picker with Bangla/English PDF export",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 6: Data Health Check Banner
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { storeViewModel.navigateInBusiness(BusinessNavDestination.DATA_HEALTH_CHECK) }
                        .testTag("link_data_health")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x335DD62C)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HealthAndSafety,
                                    contentDescription = null,
                                    tint = VividGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Data Health Check",
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Scan & fix negative stock, price bugs, orphan sales",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step 7: Test Checklist link
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { storeViewModel.navigateInBusiness(BusinessNavDestination.TEST_CHECKLIST) }
                        .testTag("link_test_checklist")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x26FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = VividGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Feature Verification Checklist",
                                    color = TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Verify exports, calculations, and rules against live data",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MoneyNowItem(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = TextMuted,
            fontSize = 9.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionSummaryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ReportNavRow(
    title: String,
    description: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = GlassSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0x1A5DD62C)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = VividGreen,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = description,
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
