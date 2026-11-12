package com.example.domain

import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Date

class CalculationEngineTest {

    @Test
    fun testProfitCalculation() {
        // Sale: 250 x 2 = 500, Buy: 180 x 2 = 360, Discount: 20 -> Profit: 500 - 360 - 20 = 120
        val profit = CalculationEngine.calculateLineProfit(
            salePrice = 250.0,
            buyPrice = 180.0,
            qty = 2L,
            discount = 20.0
        )
        assertEquals(120.0, profit, 0.001)
    }

    @Test
    fun testDiscountCannotExceedLineTotal() {
        // Gross is 100 x 2 = 200. Discount 300 should be bounded to 200.
        val bounded = CalculationEngine.calculateBoundedDiscount(
            salePrice = 100.0,
            qty = 2L,
            discount = 300.0
        )
        assertEquals(200.0, bounded, 0.001)

        val netTotal = CalculationEngine.calculateLineNetTotal(
            salePrice = 100.0,
            qty = 2L,
            discount = 300.0
        )
        assertEquals(0.0, netTotal, 0.001)
    }

    @Test
    fun testStockCannotFallBelowZero() {
        // Current stock 5, deducting 3 -> 2 remaining
        val remaining = CalculationEngine.validateStockDeduction(5L, 3L)
        assertEquals(2L, remaining)

        // Current stock 2, deducting 3 -> throws exception
        assertThrows(IllegalArgumentException::class.java) {
            CalculationEngine.validateStockDeduction(2L, 3L)
        }
    }

    @Test
    fun testSequentialInvoiceFormatting() {
        assertEquals("INV-0001", CalculationEngine.formatInvoiceNumber(1L))
        assertEquals("INV-0042", CalculationEngine.formatInvoiceNumber(42L))
        assertEquals("INV-0105", CalculationEngine.formatInvoiceNumber(105L))
    }

    @Test
    fun testStoreFinancialsTotals() {
        val products = listOf(
            Product(id = "p1", name = "Rice", buyPrice = 50.0, sellPrice = 70.0, stock = 10L),
            Product(id = "p2", name = "Oil", buyPrice = 120.0, sellPrice = 150.0, stock = 5L)
        )
        // Stock value at cost: (50 * 10) + (120 * 5) = 500 + 600 = 1100

        val sales = listOf(
            Sale(
                id = "s1",
                grandTotal = 200.0,
                profit = 50.0,
                totalItems = 3L,
                createdAt = Timestamp(Date())
            )
        )

        val financials = CalculationEngine.calculateStoreFinancials(
            products = products,
            sales = sales,
            startingCashFloat = 500.0
        )

        assertEquals(200.0, financials.todaySales, 0.001)
        assertEquals(50.0, financials.todayProfit, 0.001)
        assertEquals(1L, financials.todayOrdersCount)
        assertEquals(3L, financials.todayItemsSold)
        assertEquals(1100.0, financials.stockValueAtCost, 0.001)
        assertEquals(700.0, financials.cashBalance, 0.001) // 500 + 200
    }

    @Test
    fun testProfitPerPieceAndMargin() {
        val profitPerPiece = CalculationEngine.calculateProfitPerPiece(sellPrice = 100.0, buyPrice = 80.0)
        assertEquals(20.0, profitPerPiece, 0.001)

        val margin = CalculationEngine.calculateMarginPercent(sellPrice = 100.0, buyPrice = 80.0)
        assertEquals(20.0, margin, 0.001) // (20 / 100) * 100 = 20%
    }

    @Test
    fun testStockSummary() {
        val products = listOf(
            Product(id = "p1", name = "A", buyPrice = 10.0, stock = 15L, lowStockLimit = 5L),
            Product(id = "p2", name = "B", buyPrice = 20.0, stock = 3L, lowStockLimit = 5L), // low
            Product(id = "p3", name = "C", buyPrice = 30.0, stock = 0L, lowStockLimit = 5L)  // out of stock
        )
        val summary = CalculationEngine.calculateStockSummary(products)
        assertEquals(3L, summary.totalItems)
        assertEquals(1L, summary.lowStockCount)
        assertEquals(1L, summary.outOfStockCount)
        assertEquals((10.0 * 15) + (20.0 * 3), summary.stockValueAtCost, 0.001)
    }

    @Test
    fun testPeriodSummaryCalculation() {
        val sales = listOf(
            Sale(id = "s1", grandTotal = 500.0, profit = 120.0, totalItems = 4L),
            Sale(id = "s2", grandTotal = 300.0, profit = 80.0, totalItems = 2L)
        )
        val summary = CalculationEngine.calculatePeriodSummary(sales, purchasesTotal = 0.0)
        assertEquals(800.0, summary.totalSold, 0.001)
        assertEquals(0.0, summary.totalBought, 0.001)
        assertEquals(200.0, summary.totalProfit, 0.001)
        assertEquals(2L, summary.totalOrders)
        assertEquals(6L, summary.totalItemsSold)
    }

    @Test
    fun testProductsSoldStatsAggregation() {
        val item1 = SaleItem(productId = "p1", name = "Soap", qty = 3L, lineTotal = 150.0, profit = 30.0)
        val item2 = SaleItem(productId = "p2", name = "Shampoo", qty = 1L, lineTotal = 200.0, profit = 50.0)
        val item3 = SaleItem(productId = "p1", name = "Soap", qty = 2L, lineTotal = 100.0, profit = 20.0)

        val sales = listOf(
            Sale(id = "s1", items = listOf(item1, item2)),
            Sale(id = "s2", items = listOf(item3))
        )

        val stats = CalculationEngine.calculateProductsSoldStats(sales)
        assertEquals(2, stats.size)
        // Soap should be first with total qty = 3 + 2 = 5
        assertEquals("Soap", stats[0].productName)
        assertEquals(5L, stats[0].quantitySold)
        assertEquals(250.0, stats[0].totalSalesAmount, 0.001)
        assertEquals(50.0, stats[0].totalProfit, 0.001)

        // Shampoo should be second with qty = 1
        assertEquals("Shampoo", stats[1].productName)
        assertEquals(1L, stats[1].quantitySold)
        assertEquals(200.0, stats[1].totalSalesAmount, 0.001)
        assertEquals(50.0, stats[1].totalProfit, 0.001)
    }

    @Test
    fun testCustomerBalanceAndPaymentScenarios() {
        val customer = com.example.data.model.Customer(
            id = "c1",
            name = "Karim",
            mobile = "01711111111"
        )

        // 1. Make a Baki sale of ৳500
        val sale1 = Sale(
            id = "s1",
            invoiceNo = "INV-0001",
            customerId = "c1",
            customerName = "Karim",
            customerPhone = "01711111111",
            paymentType = "baki",
            grandTotal = 500.0,
            createdAt = Timestamp(Date(1000L))
        )

        val summaryAfterSale = CalculationEngine.calculateCustomerSummary(
            customer = customer,
            allSales = listOf(sale1),
            allPayments = emptyList()
        )
        // Balance is ৳500
        assertEquals(500.0, summaryAfterSale.currentBalance, 0.001)
        assertEquals(500.0, summaryAfterSale.totalBakiSales, 0.001)
        assertEquals(0.0, summaryAfterSale.totalPayments, 0.001)

        // 2. Receive ৳200 payment
        val payment1 = com.example.data.model.Payment(
            id = "p1",
            paymentNo = "PAY-0001",
            customerId = "c1",
            customerName = "Karim",
            amount = 200.0,
            paymentDate = 2000L
        )

        val summaryAfterPay1 = CalculationEngine.calculateCustomerSummary(
            customer = customer,
            allSales = listOf(sale1),
            allPayments = listOf(payment1)
        )
        // Balance is ৳300
        assertEquals(300.0, summaryAfterPay1.currentBalance, 0.001)
        assertEquals(200.0, summaryAfterPay1.totalPayments, 0.001)

        // 3. Receive ৳300 payment (full remaining balance)
        val payment2 = com.example.data.model.Payment(
            id = "p2",
            paymentNo = "PAY-0002",
            customerId = "c1",
            customerName = "Karim",
            amount = 300.0,
            paymentDate = 3000L
        )

        val summaryAfterPay2 = CalculationEngine.calculateCustomerSummary(
            customer = customer,
            allSales = listOf(sale1),
            allPayments = listOf(payment1, payment2)
        )
        // Balance is 0
        assertEquals(0.0, summaryAfterPay2.currentBalance, 0.001)
        assertEquals(500.0, summaryAfterPay2.totalPayments, 0.001)

        // Check ledger running balances (returned newest first)
        val ledger = CalculationEngine.calculateCustomerLedger(
            customer = customer,
            allSales = listOf(sale1),
            allPayments = listOf(payment1, payment2)
        )
        assertEquals(3, ledger.size)
        // Newest entry is payment2: running balance is 0.0
        assertEquals("PAY-0002", ledger[0].referenceNo)
        assertEquals(0.0, ledger[0].runningBalance, 0.001)
        // Middle entry is payment1: running balance was 300.0
        assertEquals("PAY-0001", ledger[1].referenceNo)
        assertEquals(300.0, ledger[1].runningBalance, 0.001)
        // Oldest entry is sale1: running balance was 500.0
        assertEquals("INV-0001", ledger[2].referenceNo)
        assertEquals(500.0, ledger[2].runningBalance, 0.001)
    }

    @Test
    fun testOldBakiSalesFromBeforeUpdateAppearCorrectly() {
        // Legacy sale: customerId is blank, but customerName = "Rahim" and customerPhone = "01812345678"
        val legacySale = Sale(
            id = "legacy_s1",
            invoiceNo = "INV-0010",
            customerId = "",
            customerName = "Rahim",
            customerPhone = "01812345678",
            paymentType = "baki",
            grandTotal = 750.0,
            createdAt = Timestamp(Date(5000L))
        )

        val bakiOverview = CalculationEngine.calculateBakiOverview(
            storedCustomers = emptyList(), // no customer doc saved yet
            allSales = listOf(legacySale),
            allPayments = emptyList()
        )

        assertEquals(1L, bakiOverview.debtorCount)
        assertEquals(750.0, bakiOverview.totalBakiOwed, 0.001)
        assertEquals("Rahim", bakiOverview.debtors[0].customer.name)
        assertEquals(750.0, bakiOverview.debtors[0].currentBalance, 0.001)
    }

    @Test
    fun testCustomerDeletionRule() {
        val customerWithSales = CustomerBalanceSummary(
            customer = com.example.data.model.Customer(id = "c1", name = "Test"),
            totalBakiSales = 0.0,
            totalPayments = 0.0,
            currentBalance = 0.0,
            totalPurchases = 100.0, // has sales
            lastPurchaseDate = 1000L
        )
        org.junit.Assert.assertFalse(CalculationEngine.canDeleteCustomer(customerWithSales))

        val customerWithDebt = CustomerBalanceSummary(
            customer = com.example.data.model.Customer(id = "c2", name = "Test2"),
            totalBakiSales = 200.0,
            totalPayments = 0.0,
            currentBalance = 200.0, // owes
            totalPurchases = 200.0,
            lastPurchaseDate = 1000L
        )
        org.junit.Assert.assertFalse(CalculationEngine.canDeleteCustomer(customerWithDebt))

        val cleanCustomer = CustomerBalanceSummary(
            customer = com.example.data.model.Customer(id = "c3", name = "Clean"),
            totalBakiSales = 0.0,
            totalPayments = 0.0,
            currentBalance = 0.0,
            totalPurchases = 0.0, // no sales, zero balance
            lastPurchaseDate = null
        )
        org.junit.Assert.assertTrue(CalculationEngine.canDeleteCustomer(cleanCustomer))
    }
}
