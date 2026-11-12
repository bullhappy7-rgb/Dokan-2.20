package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.Customer
import com.example.data.model.Payment
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.SaleItem
import com.example.data.model.Store
import com.example.data.model.StoreMember
import com.example.data.model.Supplier
import com.example.data.model.Purchase
import com.example.data.model.PurchaseItem
import com.example.data.model.PurchaseReturn
import com.example.data.model.HealthFixLog
import com.example.data.model.UserProfile
import com.example.domain.CalculationEngine
import com.example.domain.HealthProblem
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StoreRepository(private val db: FirebaseFirestore?) {

    constructor(context: Context) : this(
        try {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w("StoreRepository", "Firestore init fallback: ${e.message}")
            try {
                FirebaseFirestore.getInstance()
            } catch (e2: Exception) {
                null
            }
        }
    )

    private val auth get() = try { Firebase.auth } catch (e: Exception) { null }

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: "demo_user"
    }

    // =============================================================
    // IN-MEMORY DEMO STORE STATE
    // =============================================================

    private val demoInvoiceCounter = AtomicLong(104L)
    private val demoPaymentCounter = AtomicLong(1L)

    private val _demoStore = MutableStateFlow<Store?>(
        Store(
            id = "demo_store",
            name = "Bismillah General Store (দোকান)",
            phone = "+880 1712-345678",
            address = "Shop 14, New Market, Dhaka",
            ownerId = "demo_user",
            memberUids = listOf("demo_user"),
            members = listOf(StoreMember(userId = "demo_user", role = "owner", status = "active")),
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
    )

    private val _demoProducts = MutableStateFlow<List<Product>>(
        listOf(
            Product(
                id = "prod_1",
                storeId = "demo_store",
                name = "Miniket Rice 5kg",
                nameBn = "মিনিকেট চাল ৫ কেজি",
                barcode = "890123456789",
                category = "Grocery",
                unit = "bag",
                buyPrice = 320.0,
                sellPrice = 375.0,
                stock = 45L,
                lowStockLimit = 10L
            ),
            Product(
                id = "prod_2",
                storeId = "demo_store",
                name = "Rupchanda Soybean Oil 2L",
                nameBn = "রূপচাঁদা সয়াবিন তেল ২ লিটার",
                barcode = "890987654321",
                category = "Oil & Ghee",
                unit = "bottle",
                buyPrice = 360.0,
                sellPrice = 395.0,
                stock = 28L,
                lowStockLimit = 8L
            ),
            Product(
                id = "prod_3",
                storeId = "demo_store",
                name = "Radhuni Turmeric Powder 200g",
                nameBn = "রাঁধুনী হলুদ গুঁড়া ২০০ গ্রাম",
                barcode = "890456123789",
                category = "Spices",
                unit = "pack",
                buyPrice = 70.0,
                sellPrice = 85.0,
                stock = 60L,
                lowStockLimit = 15L
            ),
            Product(
                id = "prod_4",
                storeId = "demo_store",
                name = "Aarong Butter 200g",
                nameBn = "আড়ং মাখন ২০০ গ্রাম",
                barcode = "890321654987",
                category = "Dairy",
                unit = "pack",
                buyPrice = 180.0,
                sellPrice = 210.0,
                stock = 14L,
                lowStockLimit = 5L
            ),
            Product(
                id = "prod_5",
                storeId = "demo_store",
                name = "Pran Frooto Mango 1L",
                nameBn = "প্রাণ ফ্রুটো ম্যাঙ্গো জুস",
                barcode = "890789456123",
                category = "Beverages",
                unit = "bottle",
                buyPrice = 90.0,
                sellPrice = 110.0,
                stock = 35L,
                lowStockLimit = 10L
            ),
            Product(
                id = "prod_6",
                storeId = "demo_store",
                name = "Lifebuoy Soap 100g",
                nameBn = "লাইফবয় সাবান ১০০ গ্রাম",
                barcode = "890654321987",
                category = "Personal Care",
                unit = "pcs",
                buyPrice = 48.0,
                sellPrice = 60.0,
                stock = 4L,
                lowStockLimit = 10L
            ),
            Product(
                id = "prod_7",
                storeId = "demo_store",
                name = "ACI Pure Salt 1kg",
                nameBn = "এসিআই পিওর লবণ ১ কেজি",
                barcode = "890111222333",
                category = "Grocery",
                unit = "pack",
                buyPrice = 35.0,
                sellPrice = 42.0,
                stock = 0L,
                lowStockLimit = 10L
            )
        )
    )

    private val _demoCustomers = MutableStateFlow<List<Customer>>(
        listOf(
            Customer(
                id = "cust_1",
                storeId = "demo_store",
                name = "Mohammad Rahim",
                mobile = "01711223344",
                address = "House 12, Road 4, Mirpur",
                notes = "Regular buyer",
                currentBalance = 850.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 5))
            ),
            Customer(
                id = "cust_2",
                storeId = "demo_store",
                name = "Kabir Hossain",
                mobile = "01819887766",
                address = "Shop 9, Market Lane",
                notes = "Clears every Thursday",
                currentBalance = 1420.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 4))
            ),
            Customer(
                id = "cust_3",
                storeId = "demo_store",
                name = "Dr. Selim Ahmed",
                mobile = "01912334455",
                address = "Sector 3, Uttara",
                notes = "Local pharmacy owner",
                currentBalance = 320.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 3))
            ),
            Customer(
                id = "cust_4",
                storeId = "demo_store",
                name = "Fatema Begum",
                mobile = "01677889900",
                address = "Apartment 5B",
                notes = "Clears prompt on 1st",
                currentBalance = 0.0,
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            )
        )
    )

    private val _demoSales = MutableStateFlow<List<Sale>>(
        listOf(
            Sale(
                id = "sale_1",
                storeId = "demo_store",
                invoiceNo = "INV-0101",
                items = listOf(
                    SaleItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 1L,
                        unit = "bag",
                        buyPrice = 320.0,
                        sellPrice = 375.0,
                        discount = 15.0,
                        lineTotal = 360.0,
                        profit = 40.0
                    ),
                    SaleItem(
                        productId = "prod_4",
                        name = "Aarong Butter 200g",
                        qty = 1L,
                        unit = "pack",
                        buyPrice = 180.0,
                        sellPrice = 210.0,
                        discount = 0.0,
                        lineTotal = 210.0,
                        profit = 30.0
                    )
                ),
                totalItems = 2L,
                subtotal = 585.0,
                discount = 15.0,
                grandTotal = 570.0,
                profit = 70.0,
                paymentType = "baki",
                customerId = "cust_1",
                customerName = "Mohammad Rahim",
                customerPhone = "01711223344",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            ),
            Sale(
                id = "sale_2",
                storeId = "demo_store",
                invoiceNo = "INV-0102",
                items = listOf(
                    SaleItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 2L,
                        unit = "bottle",
                        buyPrice = 360.0,
                        sellPrice = 395.0,
                        discount = 10.0,
                        lineTotal = 780.0,
                        profit = 60.0
                    )
                ),
                totalItems = 2L,
                subtotal = 790.0,
                discount = 10.0,
                grandTotal = 780.0,
                profit = 60.0,
                paymentType = "cash",
                customerName = "Cash Sale",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            ),
            Sale(
                id = "sale_3",
                storeId = "demo_store",
                invoiceNo = "INV-0103",
                items = listOf(
                    SaleItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 2L,
                        unit = "bag",
                        buyPrice = 320.0,
                        sellPrice = 375.0,
                        discount = 0.0,
                        lineTotal = 750.0,
                        profit = 110.0
                    ),
                    SaleItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 1L,
                        unit = "bottle",
                        buyPrice = 360.0,
                        sellPrice = 395.0,
                        discount = 0.0,
                        lineTotal = 395.0,
                        profit = 35.0
                    )
                ),
                totalItems = 3L,
                subtotal = 1145.0,
                discount = 0.0,
                grandTotal = 1145.0,
                profit = 145.0,
                paymentType = "baki",
                customerId = "cust_2",
                customerName = "Kabir Hossain",
                customerPhone = "01819887766",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 3600000L * 4))
            ),
            Sale(
                id = "sale_4",
                storeId = "demo_store",
                invoiceNo = "INV-0104",
                items = listOf(
                    SaleItem(
                        productId = "prod_5",
                        name = "Pran Frooto Mango 1L",
                        qty = 1L,
                        unit = "bottle",
                        buyPrice = 90.0,
                        sellPrice = 110.0,
                        discount = 0.0,
                        lineTotal = 110.0,
                        profit = 20.0
                    )
                ),
                totalItems = 1L,
                subtotal = 110.0,
                discount = 0.0,
                grandTotal = 110.0,
                profit = 20.0,
                paymentType = "cash",
                customerName = "Cash Sale",
                soldBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 1800000L))
            )
        )
    )

    private val _demoPayments = MutableStateFlow<List<Payment>>(
        listOf(
            Payment(
                id = "pay_1",
                storeId = "demo_store",
                paymentNo = "PAY-0001",
                customerId = "cust_1",
                customerName = "Mohammad Rahim",
                customerMobile = "01711223344",
                amount = 500.0,
                paymentMethod = "cash",
                paymentDate = System.currentTimeMillis() - 86400000L,
                note = "Weekly settlement",
                receivedBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val demoPurchaseCounter = AtomicLong(103L)
    private val demoReturnCounter = AtomicLong(1L)

    private val _demoSuppliers = MutableStateFlow<List<Supplier>>(
        listOf(
            Supplier(
                id = "sup_1",
                storeId = "demo_store",
                name = "Abul Bashar Traders",
                mobile = "01722334455",
                company = "Bashar Oil & Spices Co.",
                address = "Khatunganj, Chattogram",
                balance = 12500.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 10))
            ),
            Supplier(
                id = "sup_2",
                storeId = "demo_store",
                name = "Meghna Flour & Rice Mills",
                mobile = "01833445566",
                company = "Meghna Group of Industries",
                address = "Tejgaon I/A, Dhaka",
                balance = 8400.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 8))
            ),
            Supplier(
                id = "sup_3",
                storeId = "demo_store",
                name = "Unilever Bangladesh Agency",
                mobile = "01944556677",
                company = "Unilever Distribution Hub",
                address = "Kawran Bazar, Dhaka",
                balance = 0.0,
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 5))
            )
        )
    )

    private val _demoPurchases = MutableStateFlow<List<Purchase>>(
        listOf(
            Purchase(
                id = "pur_1",
                storeId = "demo_store",
                purchaseNo = "PUR-0101",
                supplierId = "sup_1",
                supplierName = "Abul Bashar Traders",
                supplierMobile = "01722334455",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_2",
                        name = "Rupchanda Soybean Oil 2L",
                        qty = 30L,
                        unit = "bottle",
                        buyPrice = 360.0,
                        lineTotal = 10800.0
                    ),
                    PurchaseItem(
                        productId = "prod_3",
                        name = "Radhuni Turmeric Powder 200g",
                        qty = 60L,
                        unit = "pack",
                        buyPrice = 70.0,
                        lineTotal = 4200.0
                    )
                ),
                totalItems = 90L,
                totalAmount = 15000.0,
                paidAmount = 2500.0,
                dueAmount = 12500.0,
                paymentType = "credit",
                note = "Received bulk delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 3))
            ),
            Purchase(
                id = "pur_2",
                storeId = "demo_store",
                purchaseNo = "PUR-0102",
                supplierId = "sup_2",
                supplierName = "Meghna Flour & Rice Mills",
                supplierMobile = "01833445566",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_1",
                        name = "Miniket Rice 5kg",
                        qty = 45L,
                        unit = "bag",
                        buyPrice = 320.0,
                        lineTotal = 14400.0
                    )
                ),
                totalItems = 45L,
                totalAmount = 14400.0,
                paidAmount = 6000.0,
                dueAmount = 8400.0,
                paymentType = "credit",
                note = "Bags batch #B14",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            ),
            Purchase(
                id = "pur_3",
                storeId = "demo_store",
                purchaseNo = "PUR-0103",
                supplierId = "sup_3",
                supplierName = "Unilever Bangladesh Agency",
                supplierMobile = "01944556677",
                items = listOf(
                    PurchaseItem(
                        productId = "prod_6",
                        name = "Lifebuoy Soap 100g",
                        qty = 100L,
                        unit = "pcs",
                        buyPrice = 48.0,
                        lineTotal = 4800.0
                    )
                ),
                totalItems = 100L,
                totalAmount = 4800.0,
                paidAmount = 4800.0,
                dueAmount = 0.0,
                paymentType = "paid",
                note = "Cash on delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L))
            )
        )
    )

    private val _demoPurchaseReturns = MutableStateFlow<List<PurchaseReturn>>(
        listOf(
            PurchaseReturn(
                id = "ret_1",
                storeId = "demo_store",
                returnNo = "RET-0001",
                purchaseId = "pur_1",
                supplierId = "sup_1",
                supplierName = "Abul Bashar Traders",
                amount = 720.0,
                reason = "2 bottles leaking cap returned on delivery",
                createdBy = "demo_user",
                createdAt = Timestamp(Date(System.currentTimeMillis() - 86400000L * 2))
            )
        )
    )

    private val _demoHealthFixLogs = MutableStateFlow<List<HealthFixLog>>(emptyList())

    // -------------------------------------------------------------
    // USER PROFILE & STORE LINKAGE
    // -------------------------------------------------------------

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val database = db ?: return flow { emit(UserProfile(id = userId, email = "demo@storeos.com", storeId = "demo_store")) }
        return database.collection("users").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(UserProfile::class.java) else null
            }
    }

    suspend fun getUserProfileOnce(userId: String): UserProfile? {
        val database = db ?: return UserProfile(id = userId, email = "demo@storeos.com", storeId = "demo_store")
        val snap = database.collection("users").document(userId).get().await()
        return if (snap.exists()) snap.toObject(UserProfile::class.java) else null
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val database = db ?: return
        val uid = requireUserId()
        val docRef = database.collection("users").document(uid)
        val payload = mapOf(
            "email" to profile.email,
            "storeId" to profile.storeId,
            "role" to profile.role,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload, SetOptions.merge()).await()
    }

    // -------------------------------------------------------------
    // STORE SETUP & MANAGEMENT
    // -------------------------------------------------------------

    fun observeStore(storeId: String): Flow<Store?> {
        if (storeId == "demo_store" || db == null) {
            return _demoStore.asStateFlow()
        }
        return db.collection("stores").document(storeId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(Store::class.java) else null
            }
    }

    suspend fun createStore(name: String, phone: String, address: String): String {
        val database = db ?: run {
            val newStore = Store(
                id = "demo_store",
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                ownerId = "demo_user",
                memberUids = listOf("demo_user"),
                members = listOf(StoreMember(userId = "demo_user", role = "owner", status = "active")),
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            _demoStore.value = newStore
            return "demo_store"
        }

        val uid = requireUserId()
        val userEmail = auth?.currentUser?.email ?: ""
        val storeRef = database.collection("stores").document()
        val storeId = storeRef.id

        val storePayload = mapOf(
            "name" to name.trim(),
            "phone" to phone.trim(),
            "address" to address.trim(),
            "ownerId" to uid,
            "memberUids" to listOf(uid),
            "members" to listOf(
                mapOf(
                    "userId" to uid,
                    "role" to "owner",
                    "status" to "active"
                )
            ),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val counterRef = storeRef.collection("meta").document("counters")
        val counterPayload = mapOf("invoiceSeq" to 100L)

        val userRef = database.collection("users").document(uid)
        val userPayload = mapOf(
            "email" to userEmail,
            "storeId" to storeId,
            "role" to "owner",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        storeRef.set(storePayload).await()
        userRef.set(userPayload, SetOptions.merge()).await()
        try {
            counterRef.set(counterPayload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("StoreRepository", "Counter init note: ${e.message}")
        }

        return storeId
    }

    // -------------------------------------------------------------
    // PRODUCTS & INVENTORY
    // -------------------------------------------------------------

    fun observeProducts(storeId: String): Flow<List<Product>> {
        if (storeId == "demo_store" || db == null) {
            return _demoProducts.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("products")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Product::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    suspend fun addProduct(storeId: String, product: Product) {
        require(product.stock >= 0L) { "Stock quantity cannot be negative" }
        require(product.buyPrice >= 0.0) { "Cost price cannot be negative" }
        require(product.sellPrice >= 0.0) { "Sale price cannot be negative" }

        if (storeId == "demo_store" || db == null) {
            val newId = "prod_${System.currentTimeMillis()}"
            val newProd = product.copy(id = newId, storeId = storeId, createdAt = Timestamp.now())
            _demoProducts.value = (_demoProducts.value + newProd).sortedBy { it.name.lowercase() }
            return
        }

        val uid = requireUserId()
        val prodRef = db.collection("stores").document(storeId).collection("products").document()
        val payload = mapOf(
            "storeId" to storeId,
            "name" to product.name.trim(),
            "nameBn" to product.nameBn.trim(),
            "barcode" to product.barcode.trim(),
            "category" to product.category.trim().ifEmpty { "General" },
            "unit" to product.unit.trim().ifEmpty { "pcs" },
            "buyPrice" to product.buyPrice,
            "sellPrice" to product.sellPrice,
            "stock" to product.stock,
            "lowStockLimit" to product.lowStockLimit,
            "photoUri" to product.photoUri,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        prodRef.set(payload).await()
    }

    suspend fun updateProduct(storeId: String, product: Product) {
        require(product.id.isNotBlank()) { "Product ID cannot be blank" }
        require(product.stock >= 0L) { "Stock quantity cannot be negative" }
        require(product.buyPrice >= 0.0) { "Cost price cannot be negative" }
        require(product.sellPrice >= 0.0) { "Sale price cannot be negative" }

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == product.id) product.copy(updatedAt = Timestamp.now()) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(product.id)
        val payload = mapOf(
            "name" to product.name.trim(),
            "nameBn" to product.nameBn.trim(),
            "category" to product.category.trim().ifEmpty { "General" },
            "unit" to product.unit.trim().ifEmpty { "pcs" },
            "buyPrice" to product.buyPrice,
            "sellPrice" to product.sellPrice,
            "stock" to product.stock,
            "lowStockLimit" to product.lowStockLimit,
            "photoUri" to product.photoUri,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    suspend fun deleteProduct(storeId: String, productId: String) {
        require(productId.isNotBlank()) { "Product ID cannot be blank" }

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.filter { it.id != productId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("products").document(productId)
            .delete().await()
    }

    suspend fun addStock(storeId: String, productId: String, additionalStock: Long) {
        require(additionalStock > 0L) { "Additional stock must be positive" }

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == productId) it.copy(stock = it.stock + additionalStock) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        docRef.update(
            "stock", FieldValue.increment(additionalStock),
            "updatedAt", FieldValue.serverTimestamp()
        ).await()
    }

    suspend fun updateProductStock(storeId: String, productId: String, newStock: Long) {
        require(newStock >= 0L) { "Stock cannot fall below zero" }

        if (storeId == "demo_store" || db == null) {
            _demoProducts.value = _demoProducts.value.map {
                if (it.id == productId) it.copy(stock = newStock) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("products").document(productId)
        docRef.update(
            "stock", newStock,
            "updatedAt", FieldValue.serverTimestamp()
        ).await()
    }

    // -------------------------------------------------------------
    // SALES & CHECKOUT
    // -------------------------------------------------------------

    fun observeSales(storeId: String): Flow<List<Sale>> {
        if (storeId == "demo_store" || db == null) {
            return _demoSales.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("sales")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Sale::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun executeSaleCheckout(
        storeId: String,
        items: List<SaleItem>,
        invoiceDiscount: Double = 0.0,
        paymentType: String = "cash",
        customerId: String = "",
        customerName: String = "Cash Sale",
        customerPhone: String = "",
        notes: String = ""
    ): String {
        require(items.isNotEmpty()) { "Cannot complete checkout with empty items" }

        if (storeId == "demo_store" || db == null) {
            val seq = demoInvoiceCounter.incrementAndGet()
            val formattedInvoiceNo = CalculationEngine.formatInvoiceNumber(seq)

            // Deduct stock
            val currentProds = _demoProducts.value.toMutableList()
            for (item in items) {
                val idx = currentProds.indexOfFirst { it.id == item.productId }
                if (idx != -1) {
                    val p = currentProds[idx]
                    currentProds[idx] = p.copy(stock = (p.stock - item.qty).coerceAtLeast(0L))
                }
            }
            _demoProducts.value = currentProds

            val totalItems = items.sumOf { it.qty }
            val subtotal = items.sumOf { it.sellPrice * it.qty }
            val itemDiscounts = items.sumOf { it.discount }
            val totalDiscount = (itemDiscounts + invoiceDiscount).coerceIn(0.0, subtotal)
            val grandTotal = (subtotal - totalDiscount).coerceAtLeast(0.0)
            val totalProfit = items.sumOf { (it.sellPrice * it.qty) - (it.buyPrice * it.qty) - it.discount } - invoiceDiscount

            // If Baki, adjust customer balance
            if (paymentType == "baki" && customerId.isNotBlank()) {
                _demoCustomers.value = _demoCustomers.value.map { cust ->
                    if (cust.id == customerId) cust.copy(currentBalance = cust.currentBalance + grandTotal) else cust
                }
            }

            val newSale = Sale(
                id = "sale_${System.currentTimeMillis()}",
                storeId = storeId,
                invoiceNo = formattedInvoiceNo,
                items = items,
                totalItems = totalItems,
                subtotal = subtotal,
                discount = totalDiscount,
                grandTotal = grandTotal,
                profit = totalProfit,
                paymentType = paymentType,
                customerId = customerId.trim(),
                customerName = customerName.trim().ifEmpty { "Cash Sale" },
                customerPhone = customerPhone.trim(),
                notes = notes.trim(),
                soldBy = "demo_user",
                createdAt = Timestamp.now()
            )

            _demoSales.value = listOf(newSale) + _demoSales.value
            return formattedInvoiceNo
        }

        val database = db ?: throw IllegalStateException("Firestore not available")
        val uid = requireUserId()
        val storeRef = database.collection("stores").document(storeId)
        val counterRef = storeRef.collection("meta").document("counters")
        val saleRef = storeRef.collection("sales").document()
        val customerRef = if (customerId.isNotBlank()) storeRef.collection("customers").document(customerId) else null

        return database.runTransaction<String> { transaction ->
            val counterSnap = transaction.get(counterRef)
            val currentSeq = (counterSnap.getLong("invoiceSeq") ?: 100L) + 1L

            val customerSnap = customerRef?.let { transaction.get(it) }
            val currentCustomerBalance = customerSnap?.getDouble("currentBalance") ?: 0.0

            val productSnapshots = items.map { item ->
                val prodRef = storeRef.collection("products").document(item.productId)
                val snap = transaction.get(prodRef)
                Triple(item, prodRef, snap)
            }

            for ((item, _, snap) in productSnapshots) {
                val liveStock = snap.getLong("stock") ?: 0L
                if (liveStock < item.qty) {
                    throw IllegalStateException("Another device sold this item first! Only $liveStock ${item.unit} available for '${item.name}'.")
                }
            }

            val formattedInvoiceNo = CalculationEngine.formatInvoiceNumber(currentSeq)
            var totalItems = 0L
            var subtotal = 0.0
            var itemDiscounts = 0.0
            var totalProfit = 0.0
            val itemMaps = mutableListOf<Map<String, Any>>()
            val deductions = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Long>>()

            for ((item, prodRef, snap) in productSnapshots) {
                val liveStock = snap.getLong("stock") ?: 0L
                val remainingStock = liveStock - item.qty
                deductions.add(prodRef to remainingStock)

                totalItems += item.qty
                val gross = item.sellPrice * item.qty
                val boundDiscount = item.discount.coerceIn(0.0, gross)
                val netTotal = gross - boundDiscount
                val lineProfit = (item.sellPrice * item.qty) - (item.buyPrice * item.qty) - boundDiscount

                subtotal += gross
                itemDiscounts += boundDiscount
                totalProfit += lineProfit

                itemMaps.add(
                    mapOf(
                        "productId" to item.productId,
                        "name" to item.name,
                        "qty" to item.qty,
                        "unit" to item.unit,
                        "buyPrice" to item.buyPrice,
                        "sellPrice" to item.sellPrice,
                        "discount" to boundDiscount,
                        "lineTotal" to netTotal,
                        "profit" to lineProfit
                    )
                )
            }

            val totalDiscount = (itemDiscounts + invoiceDiscount).coerceIn(0.0, subtotal)
            val grandTotal = (subtotal - totalDiscount).coerceAtLeast(0.0)
            val netProfit = (totalProfit - invoiceDiscount)

            transaction.set(counterRef, mapOf("invoiceSeq" to currentSeq), SetOptions.merge())

            for ((prodRef, remainingStock) in deductions) {
                transaction.update(prodRef, "stock", remainingStock, "updatedAt", FieldValue.serverTimestamp())
            }

            if (paymentType == "baki" && customerRef != null && customerSnap != null && customerSnap.exists()) {
                val newCustomerBalance = currentCustomerBalance + grandTotal
                transaction.update(
                    customerRef,
                    "currentBalance", newCustomerBalance,
                    "updatedAt", FieldValue.serverTimestamp()
                )
            }

            val salePayload = mapOf(
                "storeId" to storeId,
                "invoiceNo" to formattedInvoiceNo,
                "items" to itemMaps,
                "totalItems" to totalItems,
                "subtotal" to subtotal,
                "discount" to totalDiscount,
                "grandTotal" to grandTotal,
                "profit" to netProfit,
                "paymentType" to paymentType,
                "customerId" to customerId.trim(),
                "customerName" to customerName.trim().ifEmpty { "Cash Sale" },
                "customerPhone" to customerPhone.trim(),
                "notes" to notes.trim(),
                "soldBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            transaction.set(saleRef, salePayload)

            formattedInvoiceNo
        }.await()
    }

    // -------------------------------------------------------------
    // CUSTOMERS MANAGEMENT
    // -------------------------------------------------------------

    fun observeCustomers(storeId: String): Flow<List<Customer>> {
        if (storeId == "demo_store" || db == null) {
            return _demoCustomers.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("customers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Customer::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    suspend fun addCustomer(storeId: String, customer: Customer): String {
        require(customer.name.isNotBlank()) { "Customer name is required" }

        if (storeId == "demo_store" || db == null) {
            val newId = "cust_${System.currentTimeMillis()}"
            val newCust = customer.copy(id = newId, storeId = storeId, createdAt = Timestamp.now())
            _demoCustomers.value = (_demoCustomers.value + newCust).sortedBy { it.name.lowercase() }
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("customers").document()
        val payload = mapOf(
            "storeId" to storeId,
            "name" to customer.name.trim(),
            "mobile" to customer.mobile.trim(),
            "address" to customer.address.trim(),
            "notes" to customer.notes.trim(),
            "currentBalance" to customer.currentBalance.coerceAtLeast(0.0),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun updateCustomer(storeId: String, customer: Customer) {
        require(customer.id.isNotBlank()) { "Customer ID cannot be blank" }
        require(customer.name.isNotBlank()) { "Customer name is required" }

        if (storeId == "demo_store" || db == null) {
            _demoCustomers.value = _demoCustomers.value.map {
                if (it.id == customer.id) customer.copy(updatedAt = Timestamp.now()) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("customers").document(customer.id)
        val payload = mapOf(
            "name" to customer.name.trim(),
            "mobile" to customer.mobile.trim(),
            "address" to customer.address.trim(),
            "notes" to customer.notes.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    suspend fun deleteCustomer(storeId: String, customerId: String) {
        require(customerId.isNotBlank()) { "Customer ID cannot be blank" }

        if (storeId == "demo_store" || db == null) {
            _demoCustomers.value = _demoCustomers.value.filter { it.id != customerId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("customers").document(customerId)
            .delete().await()
    }

    // -------------------------------------------------------------
    // BAKI PAYMENTS
    // -------------------------------------------------------------

    fun observePayments(storeId: String): Flow<List<Payment>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPayments.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("payments")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Payment::class.java) }
                    .sortedByDescending { if (it.paymentDate > 0L) it.paymentDate else (it.createdAt?.toDate()?.time ?: 0L) }
            }
    }

    suspend fun recordCustomerPaymentAtomic(
        storeId: String,
        customerId: String,
        customerName: String,
        customerMobile: String,
        amount: Double,
        note: String,
        paymentDate: Long
    ): String {
        require(amount > 0.0) { "Payment amount must be greater than zero" }

        if (storeId == "demo_store" || db == null) {
            val seq = demoPaymentCounter.incrementAndGet()
            val paymentNo = CalculationEngine.formatPaymentNumber(seq)

            _demoCustomers.value = _demoCustomers.value.map { cust ->
                if (cust.id == customerId) {
                    cust.copy(currentBalance = (cust.currentBalance - amount).coerceAtLeast(0.0))
                } else cust
            }

            val newPay = Payment(
                id = "pay_${System.currentTimeMillis()}",
                storeId = storeId,
                paymentNo = paymentNo,
                customerId = customerId.trim(),
                customerName = customerName.trim(),
                customerMobile = customerMobile.trim(),
                amount = amount,
                paymentMethod = "cash",
                paymentDate = if (paymentDate > 0L) paymentDate else System.currentTimeMillis(),
                note = note.trim(),
                receivedBy = "demo_user",
                createdAt = Timestamp.now()
            )

            _demoPayments.value = listOf(newPay) + _demoPayments.value
            return paymentNo
        }

        val database = db ?: throw IllegalStateException("Firestore not available")
        val uid = requireUserId()
        val storeRef = database.collection("stores").document(storeId)
        val counterRef = storeRef.collection("meta").document("counters")
        val paymentRef = storeRef.collection("payments").document()
        val customerRef = if (customerId.isNotBlank() && !customerId.startsWith("legacy_")) {
            storeRef.collection("customers").document(customerId)
        } else null

        return database.runTransaction<String> { transaction ->
            val counterSnap = transaction.get(counterRef)
            val currentSeq = (counterSnap.getLong("paymentSeq") ?: 0L) + 1L

            val customerSnap = customerRef?.let { transaction.get(it) }
            val liveBalance = customerSnap?.getDouble("currentBalance") ?: Double.MAX_VALUE

            if (customerSnap != null && customerSnap.exists() && amount > (liveBalance + 0.001)) {
                throw IllegalStateException("Payment amount ৳${amount.toInt()} exceeds current balance ৳${liveBalance.toInt()}.")
            }

            val newBalance = if (customerSnap != null && customerSnap.exists()) {
                (liveBalance - amount).coerceAtLeast(0.0)
            } else 0.0

            val paymentNo = CalculationEngine.formatPaymentNumber(currentSeq)

            transaction.set(counterRef, mapOf("paymentSeq" to currentSeq), SetOptions.merge())

            if (customerRef != null && customerSnap != null && customerSnap.exists()) {
                transaction.update(
                    customerRef,
                    "currentBalance", newBalance,
                    "updatedAt", FieldValue.serverTimestamp()
                )
            }

            val paymentPayload = mapOf(
                "storeId" to storeId,
                "paymentNo" to paymentNo,
                "customerId" to customerId.trim(),
                "customerName" to customerName.trim(),
                "customerMobile" to customerMobile.trim(),
                "amount" to amount,
                "paymentMethod" to "cash",
                "paymentDate" to if (paymentDate > 0L) paymentDate else System.currentTimeMillis(),
                "note" to note.trim(),
                "receivedBy" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
            transaction.set(paymentRef, paymentPayload)

            paymentNo
        }.await()
    }

    // -------------------------------------------------------------
    // SUPPLIERS MANAGEMENT
    // -------------------------------------------------------------

    fun observeSuppliers(storeId: String): Flow<List<Supplier>> {
        if (storeId == "demo_store" || db == null) {
            return _demoSuppliers.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("suppliers")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Supplier::class.java) }
                    .sortedBy { it.name.lowercase() }
            }
    }

    suspend fun addSupplier(storeId: String, supplier: Supplier): String {
        require(supplier.name.isNotBlank()) { "Supplier name is required" }

        if (storeId == "demo_store" || db == null) {
            val newId = "sup_${System.currentTimeMillis()}"
            val newSup = supplier.copy(id = newId, storeId = storeId, createdAt = Timestamp.now())
            _demoSuppliers.value = (_demoSuppliers.value + newSup).sortedBy { it.name.lowercase() }
            return newId
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("suppliers").document()
        val payload = mapOf(
            "storeId" to storeId,
            "name" to supplier.name.trim(),
            "mobile" to supplier.mobile.trim(),
            "company" to supplier.company.trim(),
            "address" to supplier.address.trim(),
            "balance" to supplier.balance.coerceAtLeast(0.0),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        return docRef.id
    }

    suspend fun updateSupplier(storeId: String, supplier: Supplier) {
        require(supplier.id.isNotBlank()) { "Supplier ID cannot be blank" }
        require(supplier.name.isNotBlank()) { "Supplier name is required" }

        if (storeId == "demo_store" || db == null) {
            _demoSuppliers.value = _demoSuppliers.value.map {
                if (it.id == supplier.id) supplier.copy(updatedAt = Timestamp.now()) else it
            }
            return
        }

        requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("suppliers").document(supplier.id)
        val payload = mapOf(
            "name" to supplier.name.trim(),
            "mobile" to supplier.mobile.trim(),
            "company" to supplier.company.trim(),
            "address" to supplier.address.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.update(payload).await()
    }

    suspend fun deleteSupplier(storeId: String, supplierId: String) {
        require(supplierId.isNotBlank()) { "Supplier ID cannot be blank" }

        if (storeId == "demo_store" || db == null) {
            _demoSuppliers.value = _demoSuppliers.value.filter { it.id != supplierId }
            return
        }

        requireUserId()
        db.collection("stores").document(storeId).collection("suppliers").document(supplierId)
            .delete().await()
    }

    // -------------------------------------------------------------
    // PURCHASES & RETURNS
    // -------------------------------------------------------------

    fun observePurchases(storeId: String): Flow<List<Purchase>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPurchases.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("purchases")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(Purchase::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun recordPurchase(storeId: String, purchase: Purchase): String {
        require(purchase.items.isNotEmpty()) { "Purchase must have at least one item" }

        if (storeId == "demo_store" || db == null) {
            val seq = demoPurchaseCounter.incrementAndGet()
            val purNo = String.format(java.util.Locale.US, "PUR-%04d", seq)
            val newPur = purchase.copy(
                id = "pur_${System.currentTimeMillis()}",
                purchaseNo = purNo,
                storeId = storeId,
                createdAt = Timestamp.now()
            )

            // Adjust supplier balance if credit
            if (purchase.dueAmount > 0.0 && purchase.supplierId.isNotBlank()) {
                _demoSuppliers.value = _demoSuppliers.value.map { sup ->
                    if (sup.id == purchase.supplierId) sup.copy(balance = sup.balance + purchase.dueAmount) else sup
                }
            }

            // Adjust stock
            val curProds = _demoProducts.value.toMutableList()
            for (item in purchase.items) {
                val idx = curProds.indexOfFirst { it.id == item.productId }
                if (idx != -1) {
                    val p = curProds[idx]
                    curProds[idx] = p.copy(stock = p.stock + item.qty)
                }
            }
            _demoProducts.value = curProds
            _demoPurchases.value = listOf(newPur) + _demoPurchases.value
            return purNo
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("purchases").document()
        val payload = mapOf(
            "storeId" to storeId,
            "purchaseNo" to purchase.purchaseNo.ifBlank { "PUR-${System.currentTimeMillis() % 10000}" },
            "supplierId" to purchase.supplierId,
            "supplierName" to purchase.supplierName,
            "supplierMobile" to purchase.supplierMobile,
            "items" to purchase.items.map {
                mapOf(
                    "productId" to it.productId,
                    "name" to it.name,
                    "qty" to it.qty,
                    "unit" to it.unit,
                    "buyPrice" to it.buyPrice,
                    "lineTotal" to it.lineTotal
                )
            },
            "totalItems" to purchase.totalItems,
            "totalAmount" to purchase.totalAmount,
            "paidAmount" to purchase.paidAmount,
            "dueAmount" to purchase.dueAmount,
            "paymentType" to purchase.paymentType,
            "note" to purchase.note,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        if (purchase.dueAmount > 0.0 && purchase.supplierId.isNotBlank()) {
            db.collection("stores").document(storeId).collection("suppliers").document(purchase.supplierId)
                .update("balance", FieldValue.increment(purchase.dueAmount))
        }

        for (item in purchase.items) {
            db.collection("stores").document(storeId).collection("products").document(item.productId)
                .update("stock", FieldValue.increment(item.qty))
        }

        return docRef.id
    }

    fun observePurchaseReturns(storeId: String): Flow<List<PurchaseReturn>> {
        if (storeId == "demo_store" || db == null) {
            return _demoPurchaseReturns.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("purchaseReturns")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(PurchaseReturn::class.java) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun recordPurchaseReturn(storeId: String, returnItem: PurchaseReturn): String {
        require(returnItem.amount > 0.0) { "Return amount must be positive" }

        if (storeId == "demo_store" || db == null) {
            val seq = demoReturnCounter.incrementAndGet()
            val retNo = String.format(java.util.Locale.US, "RET-%04d", seq)
            val newRet = returnItem.copy(
                id = "ret_${System.currentTimeMillis()}",
                returnNo = retNo,
                storeId = storeId,
                createdAt = Timestamp.now()
            )
            if (returnItem.supplierId.isNotBlank()) {
                _demoSuppliers.value = _demoSuppliers.value.map { sup ->
                    if (sup.id == returnItem.supplierId) sup.copy(balance = (sup.balance - returnItem.amount).coerceAtLeast(0.0)) else sup
                }
            }
            _demoPurchaseReturns.value = listOf(newRet) + _demoPurchaseReturns.value
            return retNo
        }

        val uid = requireUserId()
        val docRef = db.collection("stores").document(storeId).collection("purchaseReturns").document()
        val payload = mapOf(
            "storeId" to storeId,
            "returnNo" to returnItem.returnNo.ifBlank { "RET-${System.currentTimeMillis() % 10000}" },
            "purchaseId" to returnItem.purchaseId,
            "supplierId" to returnItem.supplierId,
            "supplierName" to returnItem.supplierName,
            "amount" to returnItem.amount,
            "reason" to returnItem.reason,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        if (returnItem.supplierId.isNotBlank()) {
            db.collection("stores").document(storeId).collection("suppliers").document(returnItem.supplierId)
                .update("balance", FieldValue.increment(-returnItem.amount))
        }

        return docRef.id
    }

    // -------------------------------------------------------------
    // HEALTH FIX LOGS & AUDITED FIXES
    // -------------------------------------------------------------

    fun observeHealthFixLogs(storeId: String): Flow<List<HealthFixLog>> {
        if (storeId == "demo_store" || db == null) {
            return _demoHealthFixLogs.asStateFlow()
        }
        return db.collection("stores").document(storeId).collection("healthFixLogs")
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject(HealthFixLog::class.java) }
                    .sortedByDescending { it.fixedAt?.toDate()?.time ?: 0L }
            }
    }

    suspend fun applyHealthFix(storeId: String, problem: HealthProblem, ownerUid: String): Boolean {
        val fixLog = HealthFixLog(
            id = "fix_${System.currentTimeMillis()}",
            storeId = storeId,
            problemType = problem.type.name,
            description = problem.description,
            entityId = problem.entityId,
            entityCollection = problem.entityCollection,
            fixDetails = problem.fixDescription,
            fixedBy = ownerUid,
            fixedAt = Timestamp.now()
        )

        if (storeId == "demo_store" || db == null) {
            when (problem.entityCollection) {
                "products" -> {
                    _demoProducts.value = _demoProducts.value.map { p ->
                        if (p.id == problem.entityId) {
                            var updated = p
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "stock" -> (v as? Number)?.toLong()?.let { updated = updated.copy(stock = it) }
                                    "sellPrice" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(sellPrice = it) }
                                    "barcode" -> (v as? String)?.let { updated = updated.copy(barcode = it) }
                                    "name" -> (v as? String)?.let { updated = updated.copy(name = it) }
                                    "category" -> (v as? String)?.let { updated = updated.copy(category = it) }
                                }
                            }
                            updated
                        } else p
                    }
                }
                "customers" -> {
                    _demoCustomers.value = _demoCustomers.value.map { c ->
                        if (c.id == problem.entityId) {
                            var updated = c
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "currentBalance" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(currentBalance = it) }
                                    "mobile" -> (v as? String)?.let { updated = updated.copy(mobile = it) }
                                }
                            }
                            updated
                        } else c
                    }
                }
                "suppliers" -> {
                    _demoSuppliers.value = _demoSuppliers.value.map { s ->
                        if (s.id == problem.entityId) {
                            var updated = s
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "balance" -> (v as? Number)?.toDouble()?.let { updated = updated.copy(balance = it) }
                                    "mobile" -> (v as? String)?.let { updated = updated.copy(mobile = it) }
                                }
                            }
                            updated
                        } else s
                    }
                }
                "sales" -> {
                    _demoSales.value = _demoSales.value.map { s ->
                        if (s.id == problem.entityId) {
                            var updated = s
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "customerId" -> (v as? String)?.let { updated = updated.copy(customerId = it) }
                                    "customerName" -> (v as? String)?.let { updated = updated.copy(customerName = it) }
                                }
                            }
                            updated
                        } else s
                    }
                }
                "purchases" -> {
                    _demoPurchases.value = _demoPurchases.value.map { pu ->
                        if (pu.id == problem.entityId) {
                            var updated = pu
                            problem.fixActionPayload.forEach { (k, v) ->
                                when (k) {
                                    "supplierId" -> (v as? String)?.let { updated = updated.copy(supplierId = it) }
                                }
                            }
                            updated
                        } else pu
                    }
                }
            }
            _demoHealthFixLogs.value = listOf(fixLog) + _demoHealthFixLogs.value
            return true
        }

        if (problem.entityCollection.isNotBlank() && problem.entityId.isNotBlank() && problem.fixActionPayload.isNotEmpty()) {
            val docRef = db.collection("stores").document(storeId)
                .collection(problem.entityCollection).document(problem.entityId)
            docRef.update(problem.fixActionPayload).await()
        }

        val logRef = db.collection("stores").document(storeId).collection("healthFixLogs").document()
        val logPayload = mapOf(
            "storeId" to storeId,
            "problemType" to fixLog.problemType,
            "description" to fixLog.description,
            "entityId" to fixLog.entityId,
            "entityCollection" to fixLog.entityCollection,
            "fixDetails" to fixLog.fixDetails,
            "fixedBy" to ownerUid,
            "fixedAt" to FieldValue.serverTimestamp()
        )
        logRef.set(logPayload).await()
        return true
    }
}
