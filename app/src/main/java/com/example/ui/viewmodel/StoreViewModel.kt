package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CompletedSaleReceipt
import com.example.data.model.Customer
import com.example.data.model.Payment
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Store
import com.example.data.repository.StoreRepository
import com.example.domain.BakiOverview
import com.example.domain.CalculationEngine
import com.example.domain.CustomerBalanceSummary
import com.example.domain.CustomerLedgerEntry
import com.example.domain.LedgerEntryType
import com.example.domain.StockSummary
import com.example.domain.StoreFinancials
import com.example.domain.PeriodFinancialSummary
import com.example.domain.ProductSoldStat
import com.example.data.model.Supplier
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.HealthFixLog
import com.example.domain.DailySalesPoint
import com.example.domain.CategorySalesStat
import com.example.domain.MoneyNowSummary
import com.example.domain.ProfitAndLossStatement
import com.example.domain.DayBookEntry
import com.example.domain.CustomerStatementReport
import com.example.domain.SupplierStatementReport
import com.example.domain.MonthlyReportData
import com.example.domain.HealthProblem
import java.util.Calendar
import com.example.util.AppLanguage
import com.example.util.NetworkMonitor
import com.example.util.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryPeriod(val labelEn: String, val labelBn: String) {
    TODAY("Today", "আজকে"),
    WEEK("Week", "সপ্তাহ"),
    MONTH("Month", "মাস"),
    YEAR("Year", "বছর"),
    ALL("All", "সব"),
    CUSTOM("Custom Date", "নির্দিষ্ট তারিখ")
}

enum class HistoryFilter(val labelEn: String, val labelBn: String) {
    ALL("All", "সব"),
    SALES_ONLY("Sales Only", "শুধু বিক্রয়"),
    PURCHASES_ONLY("Purchases Only", "শুধু ক্রয়")
}

enum class HistoryViewMode {
    TRANSACTIONS, PRODUCTS_SOLD
}

enum class BusinessSubScreen {
    BAKI_HUB, CUSTOMERS_LIST, CUSTOMER_DETAILS
}

enum class BusinessNavDestination {
    DASHBOARD,
    BAKI_SCREEN,
    SUPPLIERS_SCREEN,
    LOW_STOCK_SCREEN,
    SLOW_SELLERS_SCREEN,
    REPORT_SALES,
    REPORT_PURCHASES,
    REPORT_DAY_BOOK,
    REPORT_PROFIT_LOSS,
    REPORT_CUSTOMER_STATEMENT,
    REPORT_SUPPLIER_STATEMENT,
    REPORT_INVENTORY,
    MONTHLY_REPORT,
    DATA_HEALTH_CHECK,
    TEST_CHECKLIST
}

sealed class HistoryTimelineItem {
    abstract val id: String
    abstract val timestamp: Long

    data class SaleItemRow(val sale: Sale) : HistoryTimelineItem() {
        override val id: String get() = sale.id
        override val timestamp: Long get() = sale.createdAt?.toDate()?.time ?: 0L
    }

    data class PaymentItemRow(val payment: Payment) : HistoryTimelineItem() {
        override val id: String get() = payment.id
        override val timestamp: Long get() = if (payment.paymentDate > 0L) payment.paymentDate else (payment.createdAt?.toDate()?.time ?: 0L)
    }
}

data class PosCartState(
    val items: Map<String, SaleItem> = emptyMap(), // Keyed by productId
    val totalItems: Long = 0L,
    val subtotal: Double = 0.0,
    val totalDiscount: Double = 0.0,
    val invoiceDiscount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val projectedProfit: Double = 0.0,
    val paymentType: String = "cash", // "cash" or "baki"
    val customerId: String = "",
    val customerName: String = "Cash Sale",
    val customerPhone: String = "",
    val notes: String = ""
)

class StoreViewModel(
    val storeId: String,
    private val repository: StoreRepository,
    networkMonitor: NetworkMonitor? = null
) : ViewModel() {

    // Language state
    private val _currentLanguage = MutableStateFlow(AppLanguage.EN)
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    // Status notifications
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    // Network and sync status (Online / Syncing / Offline)
    val syncStatus: StateFlow<SyncStatus> = if (networkMonitor != null) {
        combine(networkMonitor.isOnline, _isBusy) { online, busy ->
            when {
                !online -> SyncStatus.OFFLINE
                busy -> SyncStatus.SYNCING
                else -> SyncStatus.ONLINE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncStatus.ONLINE)
    } else {
        _isBusy.map { busy ->
            if (busy) SyncStatus.SYNCING else SyncStatus.ONLINE
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), SyncStatus.ONLINE)
    }

    // Store profile
    val store: StateFlow<Store?> = repository.observeStore(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Products catalog
    val products: StateFlow<List<Product>> = repository.observeProducts(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Stock Summary: items count, low stock count, stock value at cost
    val stockSummary: StateFlow<StockSummary> = products.map { prodList ->
        CalculationEngine.calculateStockSummary(prodList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StockSummary(0L, 0L, 0L, 0.0)
    )

    // Low stock products (stock <= lowStockLimit)
    val lowStockProducts: StateFlow<List<Product>> = products.map { list ->
        list.filter { it.stock <= it.lowStockLimit }
            .sortedBy { it.stock }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Sales history
    val sales: StateFlow<List<Sale>> = repository.observeSales(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Recent sales (up to 10)
    val recentSales: StateFlow<List<Sale>> = sales.map { list ->
        list.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Customers management (real-time stream)
    val customers: StateFlow<List<Customer>> = repository.observeCustomers(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Payments recorded (Payment In)
    val payments: StateFlow<List<Payment>> = repository.observePayments(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Suppliers (stream)
    val suppliers: StateFlow<List<Supplier>> = repository.observeSuppliers(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Purchases (stream)
    val purchases: StateFlow<List<Purchase>> = repository.observePurchases(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Purchase Returns (stream)
    val purchaseReturns: StateFlow<List<PurchaseReturn>> = repository.observePurchaseReturns(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Health Fix Logs (stream)
    val healthFixLogs: StateFlow<List<HealthFixLog>> = repository.observeHealthFixLogs(storeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Baki overview: total owed, count, debtors list (All from CalculationEngine)
    val bakiOverview: StateFlow<BakiOverview> = combine(
        customers,
        sales,
        payments
    ) { custList, saleList, payList ->
        CalculationEngine.calculateBakiOverview(custList, saleList, payList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BakiOverview(0.0, 0L, emptyList())
    )

    // All customers with calculated balances
    val allCustomerSummaries: StateFlow<List<CustomerBalanceSummary>> = combine(
        customers,
        sales,
        payments
    ) { custList, saleList, payList ->
        val list = custList.toMutableList()
        for (sale in saleList) {
            if (sale.customerName != "Cash Sale" && sale.customerName.isNotBlank()) {
                val exists = list.any { CalculationEngine.doesSaleMatchCustomer(sale, it) }
                if (!exists) {
                    list.add(
                        Customer(
                            id = sale.customerId.ifBlank { "legacy_${sale.customerName.trim().lowercase().replace(' ', '_')}" },
                            name = sale.customerName.trim(),
                            mobile = sale.customerPhone.trim(),
                            notes = "Previous customer"
                        )
                    )
                }
            }
        }
        list.map { CalculationEngine.calculateCustomerSummary(it, saleList, payList) }
            .sortedBy { it.customer.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business Sub-Screen and Selected Customer details
    private val _businessSubScreen = MutableStateFlow(BusinessSubScreen.BAKI_HUB)
    val businessSubScreen: StateFlow<BusinessSubScreen> = _businessSubScreen.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    val selectedCustomerSummary: StateFlow<CustomerBalanceSummary?> = combine(
        _selectedCustomer,
        sales,
        payments
    ) { cust, saleList, payList ->
        if (cust != null) CalculationEngine.calculateCustomerSummary(cust, saleList, payList) else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val selectedCustomerLedger: StateFlow<List<CustomerLedgerEntry>> = combine(
        _selectedCustomer,
        sales,
        payments
    ) { cust, saleList, payList ->
        if (cust != null) CalculationEngine.calculateCustomerLedger(cust, saleList, payList) else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Financial Metrics - Strictly calculated via ONE module CalculationEngine
    val financials: StateFlow<StoreFinancials> = combine(products, sales) { prodList, saleList ->
        CalculationEngine.calculateStoreFinancials(prodList, saleList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StoreFinancials(0.0, 0.0, 0L, 0L, 0.0, 0.0)
    )

    // POS Cart State
    private val _cart = MutableStateFlow(PosCartState())
    val cart: StateFlow<PosCartState> = _cart.asStateFlow()

    private val _lastCompletedReceipt = MutableStateFlow<CompletedSaleReceipt?>(null)
    val lastCompletedReceipt: StateFlow<CompletedSaleReceipt?> = _lastCompletedReceipt.asStateFlow()

    fun clearLastReceipt() {
        _lastCompletedReceipt.value = null
    }

    // -------------------------------------------------------------
    // HISTORY & FINANCIAL STATEMENTS STATE
    // -------------------------------------------------------------
    private val _historyPeriod = MutableStateFlow(HistoryPeriod.TODAY)
    val historyPeriod: StateFlow<HistoryPeriod> = _historyPeriod.asStateFlow()

    private val _historyCustomDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val historyCustomDateRange: StateFlow<Pair<Long, Long>?> = _historyCustomDateRange.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _historyViewMode = MutableStateFlow(HistoryViewMode.TRANSACTIONS)
    val historyViewMode: StateFlow<HistoryViewMode> = _historyViewMode.asStateFlow()

    private val _productsSoldIsGrid = MutableStateFlow(false)
    val productsSoldIsGrid: StateFlow<Boolean> = _productsSoldIsGrid.asStateFlow()

    private val _selectedSaleDetail = MutableStateFlow<Sale?>(null)
    val selectedSaleDetail: StateFlow<Sale?> = _selectedSaleDetail.asStateFlow()

    // Filtered sales for History
    val historyFilteredSales: StateFlow<List<Sale>> = combine(
        sales,
        _historyPeriod,
        _historyCustomDateRange,
        _historyFilter
    ) { allSales, period, customRange, filter ->
        if (filter == HistoryFilter.PURCHASES_ONLY) {
            emptyList()
        } else {
            val (startMillis, endMillis) = computeDateRange(period, customRange)
            CalculationEngine.filterSalesByDateRange(allSales, startMillis, endMillis)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Summary for History: All totals come strictly from CalculationEngine (Rule 4)
    val historySummary: StateFlow<PeriodFinancialSummary> = historyFilteredSales.map { filteredList ->
        CalculationEngine.calculatePeriodSummary(filteredList, purchasesTotal = 0.0)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        PeriodFinancialSummary(0.0, 0.0, 0.0, 0L, 0L)
    )

    // Products sold metrics for History: Strictly from CalculationEngine (Rule 4)
    val historyProductsSold: StateFlow<List<ProductSoldStat>> = historyFilteredSales.map { filteredList ->
        CalculationEngine.calculateProductsSoldStats(filteredList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Payments in date range for History
    val historyFilteredPayments: StateFlow<List<Payment>> = combine(
        payments,
        _historyPeriod,
        _historyCustomDateRange,
        _historyFilter
    ) { allPayments, period, customRange, filter ->
        if (filter == HistoryFilter.SALES_ONLY || filter == HistoryFilter.PURCHASES_ONLY) {
            emptyList()
        } else {
            val (startMillis, endMillis) = computeDateRange(period, customRange)
            allPayments.filter { p ->
                val pDate = if (p.paymentDate > 0L) p.paymentDate else (p.createdAt?.toDate()?.time ?: 0L)
                pDate in startMillis..endMillis
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Timeline items for History (Sales + Payment In rows, newest first)
    val historyTimelineItems: StateFlow<List<HistoryTimelineItem>> = combine(
        historyFilteredSales,
        historyFilteredPayments,
        _historyFilter
    ) { filteredSales, filteredPayments, filter ->
        if (filter == HistoryFilter.PURCHASES_ONLY) {
            emptyList()
        } else {
            val items = mutableListOf<HistoryTimelineItem>()
            filteredSales.forEach { items.add(HistoryTimelineItem.SaleItemRow(it)) }
            if (filter == HistoryFilter.ALL) {
                filteredPayments.forEach { items.add(HistoryTimelineItem.PaymentItemRow(it)) }
            }
            items.sortedByDescending { it.timestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private fun computeDateRange(period: HistoryPeriod, customRange: Pair<Long, Long>?): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        return when (period) {
            HistoryPeriod.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.WEEK -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                Pair(start, Long.MAX_VALUE)
            }
            HistoryPeriod.ALL -> {
                Pair(0L, Long.MAX_VALUE)
            }
            HistoryPeriod.CUSTOM -> {
                customRange ?: Pair(0L, Long.MAX_VALUE)
            }
        }
    }

    fun setHistoryPeriod(period: HistoryPeriod) {
        _historyPeriod.value = period
    }

    fun setHistoryCustomDateRange(startMillis: Long, endMillis: Long) {
        _historyCustomDateRange.value = Pair(startMillis, endMillis)
        _historyPeriod.value = HistoryPeriod.CUSTOM
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setHistoryViewMode(mode: HistoryViewMode) {
        _historyViewMode.value = mode
    }

    fun toggleProductsSoldGrid() {
        _productsSoldIsGrid.value = !_productsSoldIsGrid.value
    }

    fun selectSaleDetail(sale: Sale?) {
        _selectedSaleDetail.value = sale
    }

    fun toggleLanguage() {
        _currentLanguage.value = if (_currentLanguage.value == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
    }

    // -------------------------------------------------------------
    // BUSINESS TAB DASHBOARD, REPORTS & SHARED PERIOD
    // -------------------------------------------------------------

    private val _businessNavDestination = MutableStateFlow(BusinessNavDestination.DASHBOARD)
    val businessNavDestination: StateFlow<BusinessNavDestination> = _businessNavDestination.asStateFlow()

    private val _businessNavBackStack = mutableListOf<BusinessNavDestination>()

    fun navigateInBusiness(destination: BusinessNavDestination) {
        if (_businessNavDestination.value != destination) {
            _businessNavBackStack.add(_businessNavDestination.value)
            _businessNavDestination.value = destination
        }
    }

    fun navigateBackInBusiness(): Boolean {
        return if (_businessNavBackStack.isNotEmpty()) {
            _businessNavDestination.value = _businessNavBackStack.removeAt(_businessNavBackStack.size - 1)
            true
        } else if (_businessNavDestination.value != BusinessNavDestination.DASHBOARD) {
            _businessNavDestination.value = BusinessNavDestination.DASHBOARD
            true
        } else {
            false
        }
    }

    val businessPeriod: StateFlow<HistoryPeriod> = _historyPeriod.asStateFlow()
    val businessCustomDateRange: StateFlow<Pair<Long, Long>?> = _historyCustomDateRange.asStateFlow()

    fun setBusinessPeriod(period: HistoryPeriod) {
        _historyPeriod.value = period
    }

    fun setBusinessCustomDateRange(startMillis: Long, endMillis: Long) {
        _historyCustomDateRange.value = Pair(startMillis, endMillis)
        _historyPeriod.value = HistoryPeriod.CUSTOM
    }

    // Business filtered sales: strictly calculated by CalculationEngine
    val businessFilteredSales: StateFlow<List<Sale>> = combine(
        sales,
        _historyPeriod,
        _historyCustomDateRange
    ) { allSales, period, customRange ->
        CalculationEngine.filterSalesByPeriod(allSales, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered purchases: strictly calculated by CalculationEngine
    val businessFilteredPurchases: StateFlow<List<Purchase>> = combine(
        purchases,
        _historyPeriod,
        _historyCustomDateRange
    ) { allPurchases, period, customRange ->
        CalculationEngine.filterPurchasesByPeriod(allPurchases, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered returns
    val businessFilteredReturns: StateFlow<List<PurchaseReturn>> = combine(
        purchaseReturns,
        _historyPeriod,
        _historyCustomDateRange
    ) { allReturns, period, customRange ->
        CalculationEngine.filterReturnsByPeriod(allReturns, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Business filtered payments
    val businessFilteredPayments: StateFlow<List<Payment>> = combine(
        payments,
        _historyPeriod,
        _historyCustomDateRange
    ) { allPayments, period, customRange ->
        CalculationEngine.filterPaymentsByPeriod(allPayments, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Financial KPI summary for the period
    val businessFinancials: StateFlow<PeriodFinancialSummary> = combine(
        businessFilteredSales,
        businessFilteredPurchases
    ) { filteredSales, filteredPurchases ->
        val purchasesTotal = filteredPurchases.sumOf { it.totalAmount }
        CalculationEngine.calculatePeriodSummary(filteredSales, purchasesTotal)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        PeriodFinancialSummary(0.0, 0.0, 0.0, 0L, 0L)
    )

    // Money Now: Stock value at cost, Baki, debt to suppliers (CalculationEngine rule 4)
    val moneyNow: StateFlow<MoneyNowSummary> = combine(
        products,
        customers,
        suppliers,
        sales,
        payments
    ) { prodList, custList, supList, saleList, payList ->
        CalculationEngine.calculateMoneyNow(prodList, custList, supList, saleList, payList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        MoneyNowSummary(0.0, 0.0, 0.0, 0L, 0L)
    )

    // Sales chart points (bars/lines grouped by day or month)
    val salesChartPoints: StateFlow<List<DailySalesPoint>> = combine(
        businessFilteredSales,
        _historyPeriod,
        _historyCustomDateRange
    ) { filteredSales, period, customRange ->
        CalculationEngine.calculateDailySalesChart(filteredSales, period, customRange)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Best-selling products (top 5 by qty with amount)
    val topSellingProducts: StateFlow<List<ProductSoldStat>> = businessFilteredSales.map { filteredSales ->
        CalculationEngine.calculateTopSellingProducts(filteredSales, 5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Sales by Category
    val salesByCategory: StateFlow<List<CategorySalesStat>> = combine(
        businessFilteredSales,
        products
    ) { filteredSales, allProducts ->
        CalculationEngine.calculateSalesByCategory(filteredSales, allProducts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Slow Sellers: products with stock > 0 but 0 sales in the period
    val slowSellingProducts: StateFlow<List<Product>> = combine(
        products,
        businessFilteredSales
    ) { allProducts, filteredSales ->
        CalculationEngine.calculateSlowSellingProducts(allProducts, filteredSales)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Profit & Loss Stock-Based toggle
    private val _isPnlStockBased = MutableStateFlow(true)
    val isPnlStockBased: StateFlow<Boolean> = _isPnlStockBased.asStateFlow()

    fun togglePnlStockBased() {
        _isPnlStockBased.value = !_isPnlStockBased.value
    }

    // Profit & Loss statement
    val pnlStatement: StateFlow<ProfitAndLossStatement> = combine(
        businessFilteredSales,
        businessFilteredPurchases,
        _isPnlStockBased
    ) { filteredSales, filteredPurchases, isStockBased ->
        CalculationEngine.calculateProfitAndLoss(filteredSales, filteredPurchases, isStockBased)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        ProfitAndLossStatement(0.0, 0.0, 0.0, 0.0, 0.0, true)
    )

    // Day Book entries
    val dayBookEntries: StateFlow<List<DayBookEntry>> = combine(
        businessFilteredSales,
        businessFilteredPurchases,
        businessFilteredPayments,
        businessFilteredReturns
    ) { sList, pList, payList, rList ->
        CalculationEngine.calculateDayBook(sList, pList, payList, rList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    // Customer Statement selection & calculation
    private val _selectedStatementCustomer = MutableStateFlow<Customer?>(null)
    val selectedStatementCustomer: StateFlow<Customer?> = _selectedStatementCustomer.asStateFlow()

    fun selectStatementCustomer(customer: Customer?) {
        _selectedStatementCustomer.value = customer
    }

    val customerStatementReport: StateFlow<CustomerStatementReport?> = combine(
        _selectedStatementCustomer,
        sales,
        payments,
        _historyPeriod,
        _historyCustomDateRange
    ) { cust, allSales, allPayments, period, customRange ->
        if (cust != null) {
            CalculationEngine.calculateCustomerStatement(cust, allSales, allPayments, period, customRange)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Supplier Statement selection & calculation
    private val _selectedStatementSupplier = MutableStateFlow<Supplier?>(null)
    val selectedStatementSupplier: StateFlow<Supplier?> = _selectedStatementSupplier.asStateFlow()

    fun selectStatementSupplier(supplier: Supplier?) {
        _selectedStatementSupplier.value = supplier
    }

    val supplierStatementReport: StateFlow<SupplierStatementReport?> = combine(
        _selectedStatementSupplier,
        purchases,
        purchaseReturns,
        _historyPeriod,
        _historyCustomDateRange
    ) { sup, allPurchases, allReturns, period, customRange ->
        if (sup != null) {
            CalculationEngine.calculateSupplierStatement(sup, allPurchases, allReturns, period, customRange)
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    // Monthly Report Month Picker & Data
    private val _monthlyReportYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val monthlyReportYear: StateFlow<Int> = _monthlyReportYear.asStateFlow()

    private val _monthlyReportMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val monthlyReportMonth: StateFlow<Int> = _monthlyReportMonth.asStateFlow()

    fun setMonthlyReportMonth(year: Int, month: Int) {
        _monthlyReportYear.value = year
        _monthlyReportMonth.value = month
    }

    val monthlyReportData: StateFlow<MonthlyReportData> = combine(
        _monthlyReportYear,
        _monthlyReportMonth,
        products,
        sales,
        purchases,
        customers,
        suppliers,
        payments
    ) { year, month, prodList, saleList, purList, custList, supList, payList ->
        CalculationEngine.calculateMonthlyReportData(
            year = year,
            month = month,
            products = prodList,
            sales = saleList,
            purchases = purList,
            customers = custList,
            suppliers = supList,
            payments = payList
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        CalculationEngine.calculateMonthlyReportData(
            Calendar.getInstance().get(Calendar.YEAR),
            Calendar.getInstance().get(Calendar.MONTH),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()
        )
    )

    // Health Check state
    private val _healthProblems = MutableStateFlow<List<HealthProblem>?>(null)
    val healthProblems: StateFlow<List<HealthProblem>?> = _healthProblems.asStateFlow()

    private val _isScanningHealth = MutableStateFlow(false)
    val isScanningHealth: StateFlow<Boolean> = _isScanningHealth.asStateFlow()

    fun scanDataHealth() {
        _isScanningHealth.value = true
        viewModelScope.launch {
            try {
                val scanned = CalculationEngine.scanDataHealth(
                    store = store.value,
                    products = products.value,
                    sales = sales.value,
                    purchases = purchases.value,
                    customers = customers.value,
                    suppliers = suppliers.value,
                    payments = payments.value
                )
                _healthProblems.value = scanned
                _statusMessage.value = if (scanned.isEmpty()) "Scan complete: No problems detected!" else "Found ${scanned.size} data anomalies"
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Health scan error", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to scan database"
            } finally {
                _isScanningHealth.value = false
            }
        }
    }

    fun applyHealthFix(problem: HealthProblem, ownerUid: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.applyHealthFix(storeId, problem, ownerUid)
                _healthProblems.value = _healthProblems.value?.filter { it.id != problem.id }
                _statusMessage.value = "Fix applied successfully!"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Health fix error", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to apply fix"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // PRODUCT ACTIONS (ADD, EDIT, DELETE, SAFE STOCK INCREMENT)
    // -------------------------------------------------------------

    fun addNewProductWizard(
        name: String,
        nameBn: String,
        category: String,
        unit: String,
        buyPrice: Double,
        sellPrice: Double,
        stock: Long,
        lowStockLimit: Long,
        photoUri: String,
        onSuccess: () -> Unit
    ) {
        if (name.isBlank()) {
            _errorMessage.value = "Product name in English is required"
            return
        }
        if (buyPrice < 0.0 || sellPrice < 0.0 || stock < 0L || lowStockLimit < 0L) {
            _errorMessage.value = "Prices and stock numbers cannot be negative"
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val newProduct = Product(
                    storeId = storeId,
                    name = name.trim(),
                    nameBn = nameBn.trim(),
                    barcode = "",
                    category = category.trim().ifEmpty { "General" },
                    unit = unit.trim().ifEmpty { "pcs" },
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    stock = stock,
                    lowStockLimit = lowStockLimit,
                    photoUri = photoUri.trim()
                )
                repository.addProduct(storeId, newProduct)
                _statusMessage.value = "Product '${name.trim()}' added successfully"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to add product", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to save product"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateProductWizard(
        productId: String,
        name: String,
        nameBn: String,
        category: String,
        unit: String,
        buyPrice: Double,
        sellPrice: Double,
        stock: Long,
        lowStockLimit: Long,
        photoUri: String,
        onSuccess: () -> Unit
    ) {
        if (productId.isBlank()) return
        if (name.isBlank()) {
            _errorMessage.value = "Product name in English is required"
            return
        }
        if (buyPrice < 0.0 || sellPrice < 0.0 || stock < 0L || lowStockLimit < 0L) {
            _errorMessage.value = "Prices and stock numbers cannot be negative"
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val updated = Product(
                    id = productId,
                    storeId = storeId,
                    name = name.trim(),
                    nameBn = nameBn.trim(),
                    category = category.trim().ifEmpty { "General" },
                    unit = unit.trim().ifEmpty { "pcs" },
                    buyPrice = buyPrice,
                    sellPrice = sellPrice,
                    stock = stock,
                    lowStockLimit = lowStockLimit,
                    photoUri = photoUri.trim()
                )
                repository.updateProduct(storeId, updated)
                _statusMessage.value = "Product '${name.trim()}' updated successfully"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to update product", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to update product"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteProduct(productId: String, onSuccess: () -> Unit) {
        if (productId.isBlank()) return
        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                repository.deleteProduct(storeId, productId)
                _statusMessage.value = "Product deleted successfully"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to delete product", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to delete product"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun addQuickStock(productId: String, additionalStock: Long) {
        if (additionalStock <= 0L) return
        _isBusy.value = true
        viewModelScope.launch {
            try {
                repository.addStock(storeId, productId, additionalStock)
                _statusMessage.value = "Added +$additionalStock stock successfully"
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Failed to increment stock", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to update stock"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // POS CART & CHECKOUT (CALCULATION ENGINE ENFORCED)
    // -------------------------------------------------------------

    fun addProductToCart(product: Product) {
        if (product.stock <= 0L) {
            _errorMessage.value = "Item is out of stock"
            return
        }

        val currentMap = _cart.value.items.toMutableMap()
        val existing = currentMap[product.id]

        val newQty = (existing?.qty ?: 0L) + 1L
        if (newQty > product.stock) {
            _errorMessage.value = "Cannot add more. Available stock: ${product.stock} ${product.unit}"
            return
        }

        val updatedItem = CalculationEngine.buildSaleItem(
            product = product,
            qty = newQty,
            requestedDiscount = existing?.discount ?: 0.0
        )
        currentMap[product.id] = updatedItem
        recalculateCart(currentMap, _cart.value.invoiceDiscount)
    }

    fun updateCartItemQty(productId: String, product: Product, newQty: Long) {
        val currentMap = _cart.value.items.toMutableMap()
        if (newQty <= 0L) {
            currentMap.remove(productId)
        } else {
            if (newQty > product.stock) {
                _errorMessage.value = "Cannot exceed available stock of ${product.stock} ${product.unit}"
                return
            }
            val existing = currentMap[productId]
            val updated = CalculationEngine.buildSaleItem(
                product = product,
                qty = newQty,
                requestedDiscount = existing?.discount ?: 0.0
            )
            currentMap[productId] = updated
        }
        recalculateCart(currentMap, _cart.value.invoiceDiscount)
    }

    fun setInvoiceDiscount(discount: Double): Boolean {
        val currentSubtotal = _cart.value.subtotal
        if (discount < 0.0) {
            _errorMessage.value = "Discount cannot be negative"
            return false
        }
        if (discount > currentSubtotal) {
            _errorMessage.value = "Discount cannot exceed cart subtotal"
            return false
        }
        _errorMessage.value = null
        recalculateCart(_cart.value.items, discount)
        return true
    }

    fun setPaymentType(paymentType: String) {
        _cart.value = _cart.value.copy(paymentType = paymentType)
    }

    fun setCustomer(name: String, phone: String, id: String = "") {
        _cart.value = _cart.value.copy(customerId = id, customerName = name, customerPhone = phone)
    }

    fun selectCustomerForCheckout(customer: Customer) {
        _cart.value = _cart.value.copy(
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.mobile
        )
    }

    fun setCashSaleForCheckout() {
        _cart.value = _cart.value.copy(
            customerId = "",
            customerName = "Cash Sale",
            customerPhone = ""
        )
    }

    fun setNotes(notes: String) {
        _cart.value = _cart.value.copy(notes = notes)
    }

    fun clearCart() {
        _cart.value = PosCartState()
    }

    private fun recalculateCart(items: Map<String, SaleItem>, invoiceDiscount: Double) {
        var totalItems = 0L
        var subtotal = 0.0
        var lineDiscounts = 0.0
        var totalProfit = 0.0

        for (item in items.values) {
            totalItems += item.qty
            subtotal += (item.sellPrice * item.qty)
            lineDiscounts += item.discount
            totalProfit += item.profit
        }

        val boundedInvDiscount = invoiceDiscount.coerceIn(0.0, subtotal - lineDiscounts)
        val totalDiscount = lineDiscounts + boundedInvDiscount
        val grandTotal = (subtotal - totalDiscount).coerceAtLeast(0.0)
        val projectedProfit = (totalProfit - boundedInvDiscount)

        _cart.value = _cart.value.copy(
            items = items,
            totalItems = totalItems,
            subtotal = subtotal,
            totalDiscount = totalDiscount,
            invoiceDiscount = boundedInvDiscount,
            grandTotal = grandTotal,
            projectedProfit = projectedProfit
        )
    }

    fun checkoutCurrentCart(onSuccess: (CompletedSaleReceipt) -> Unit) {
        val cartState = _cart.value
        val itemsList = cartState.items.values.toList()
        if (itemsList.isEmpty()) {
            _errorMessage.value = "Cart is empty"
            return
        }

        // Baki is strictly blocked for Cash Sale with the exact clear message
        if (cartState.paymentType == "baki") {
            val isCashSale = cartState.customerName.trim().equals("Cash Sale", ignoreCase = true) ||
                    cartState.customerName.trim().isBlank()
            if (isCashSale) {
                _errorMessage.value = "Choose a customer for Baki"
                return
            }
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val invoiceNumber = repository.executeSaleCheckout(
                    storeId = storeId,
                    items = itemsList,
                    invoiceDiscount = cartState.invoiceDiscount,
                    paymentType = cartState.paymentType,
                    customerId = cartState.customerId,
                    customerName = cartState.customerName,
                    customerPhone = cartState.customerPhone,
                    notes = cartState.notes
                )

                val curStore = store.value
                val receipt = CompletedSaleReceipt(
                    invoiceNo = invoiceNumber,
                    storeName = curStore?.name?.ifBlank { "StoreOS" } ?: "StoreOS",
                    storePhone = curStore?.phone ?: "",
                    storeAddress = curStore?.address ?: "",
                    items = itemsList,
                    totalItems = cartState.totalItems,
                    subtotal = cartState.subtotal,
                    discount = cartState.totalDiscount,
                    grandTotal = cartState.grandTotal,
                    paymentType = cartState.paymentType,
                    customerName = cartState.customerName.trim().ifEmpty { "Cash Sale" },
                    customerPhone = cartState.customerPhone,
                    notes = cartState.notes,
                    timestamp = System.currentTimeMillis()
                )

                _lastCompletedReceipt.value = receipt
                _cart.value = PosCartState()
                _statusMessage.value = "Invoice $invoiceNumber completed!"
                onSuccess(receipt)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Checkout transaction failed", e)
                _errorMessage.value = e.localizedMessage ?: "Checkout failed. Please check stock levels."
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // CUSTOMER MANAGEMENT & PAYMENT METHODS
    // -------------------------------------------------------------

    fun setBusinessSubScreen(screen: BusinessSubScreen) {
        _businessSubScreen.value = screen
    }

    fun openCustomerDetails(customer: Customer) {
        _selectedCustomer.value = customer
        _businessSubScreen.value = BusinessSubScreen.CUSTOMER_DETAILS
    }

    fun closeCustomerDetails() {
        _selectedCustomer.value = null
        _businessSubScreen.value = BusinessSubScreen.BAKI_HUB
    }

    fun addCustomer(
        name: String,
        mobile: String,
        address: String,
        notes: String,
        onSuccess: (Customer) -> Unit
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _errorMessage.value = "Customer name is required"
            return
        }
        val trimmedMobile = mobile.trim()
        if (trimmedMobile.isNotBlank()) {
            val duplicate = customers.value.find { it.mobile.isNotBlank() && it.mobile.trim() == trimmedMobile }
            if (duplicate != null) {
                _statusMessage.value = "Warning: Mobile number $trimmedMobile already belongs to '${duplicate.name}'."
            }
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newCust = Customer(
                    storeId = storeId,
                    name = trimmedName,
                    mobile = trimmedMobile,
                    address = address.trim(),
                    notes = notes.trim(),
                    currentBalance = 0.0
                )
                val newId = repository.addCustomer(storeId, newCust)
                val createdCust = newCust.copy(id = newId)
                _statusMessage.value = "Customer '$trimmedName' added!"
                onSuccess(createdCust)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Add customer failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to add customer"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateCustomer(customer: Customer, onSuccess: () -> Unit) {
        if (customer.name.isBlank()) {
            _errorMessage.value = "Customer name is required"
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.updateCustomer(storeId, customer)
                if (_selectedCustomer.value?.id == customer.id) {
                    _selectedCustomer.value = customer
                }
                _statusMessage.value = "Customer updated successfully!"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Update customer failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to update customer"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteCustomer(customer: Customer, onSuccess: () -> Unit) {
        val summary = CalculationEngine.calculateCustomerSummary(customer, sales.value, payments.value)
        if (!CalculationEngine.canDeleteCustomer(summary)) {
            _errorMessage.value = "Cannot delete customer with existing sales or outstanding balance."
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteCustomer(storeId, customer.id)
                if (_selectedCustomer.value?.id == customer.id) {
                    _selectedCustomer.value = null
                    _businessSubScreen.value = BusinessSubScreen.BAKI_HUB
                }
                _statusMessage.value = "Customer deleted."
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete customer failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to delete customer"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordCustomerPayment(
        customer: Customer,
        amount: Double,
        note: String,
        paymentDate: Long,
        onSuccess: (String) -> Unit
    ) {
        if (amount <= 0.0) {
            _errorMessage.value = "Payment amount must be greater than zero."
            return
        }
        val summary = CalculationEngine.calculateCustomerSummary(customer, sales.value, payments.value)
        if (amount > summary.currentBalance + 0.001) {
            _errorMessage.value = "Payment amount ৳${amount.toInt()} cannot exceed customer balance of ৳${summary.currentBalance.toInt()}."
            return
        }

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val paymentNo = repository.recordCustomerPaymentAtomic(
                    storeId = storeId,
                    customerId = customer.id,
                    customerName = customer.name,
                    customerMobile = customer.mobile,
                    amount = amount,
                    note = note,
                    paymentDate = paymentDate
                )
                _statusMessage.value = "Payment $paymentNo recorded successfully!"
                onSuccess(paymentNo)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record payment failed", e)
                _errorMessage.value = e.localizedMessage ?: "Payment failed"
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // SUPPLIERS & PURCHASES ACTIONS
    // -------------------------------------------------------------

    fun addSupplier(
        name: String,
        mobile: String,
        company: String,
        address: String,
        onSuccess: (Supplier) -> Unit
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Supplier name is required"
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newSup = Supplier(
                    storeId = storeId,
                    name = trimmed,
                    mobile = mobile.trim(),
                    company = company.trim(),
                    address = address.trim(),
                    balance = 0.0
                )
                val id = repository.addSupplier(storeId, newSup)
                _statusMessage.value = "Supplier '$trimmed' added!"
                onSuccess(newSup.copy(id = id))
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Add supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to add supplier"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateSupplier(supplier: Supplier, onSuccess: () -> Unit) {
        if (supplier.name.isBlank()) {
            _errorMessage.value = "Supplier name is required"
            return
        }
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.updateSupplier(storeId, supplier)
                _statusMessage.value = "Supplier updated successfully!"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Update supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to update supplier"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteSupplier(supplierId: String, onSuccess: () -> Unit) {
        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                repository.deleteSupplier(storeId, supplierId)
                _statusMessage.value = "Supplier deleted"
                onSuccess()
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Delete supplier failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to delete supplier"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordPurchase(
        supplier: Supplier,
        items: List<PurchaseItem>,
        paidAmount: Double,
        note: String,
        onSuccess: (String) -> Unit
    ) {
        if (items.isEmpty()) {
            _errorMessage.value = "Purchase must have at least one item"
            return
        }
        val totalAmount = items.sumOf { it.buyPrice * it.qty }
        val boundedPaid = paidAmount.coerceIn(0.0, totalAmount)
        val dueAmount = (totalAmount - boundedPaid).coerceAtLeast(0.0)
        val paymentType = if (dueAmount > 0.001) "credit" else "paid"

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val newPur = Purchase(
                    storeId = storeId,
                    supplierId = supplier.id,
                    supplierName = supplier.name,
                    supplierMobile = supplier.mobile,
                    items = items,
                    totalItems = items.sumOf { it.qty },
                    totalAmount = totalAmount,
                    paidAmount = boundedPaid,
                    dueAmount = dueAmount,
                    paymentType = paymentType,
                    note = note.trim()
                )
                val purNo = repository.recordPurchase(storeId, newPur)
                _statusMessage.value = "Purchase $purNo recorded!"
                onSuccess(purNo)
            } catch (e: Exception) {
                Log.e("StoreViewModel", "Record purchase failed", e)
                _errorMessage.value = e.localizedMessage ?: "Failed to record purchase"
            } finally {
                _isBusy.value = false
            }
        }
    }
}
