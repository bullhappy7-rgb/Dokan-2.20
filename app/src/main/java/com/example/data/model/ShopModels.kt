package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val email: String = "",
    val storeId: String = "",
    val role: String = "owner",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StoreMember(
    val userId: String = "",
    val role: String = "owner", // "owner", "manager", "staff"
    val status: String = "active"
)

@IgnoreExtraProperties
data class Store(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val members: List<StoreMember> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val name: String = "",
    val nameBn: String = "",
    val barcode: String = "",
    val category: String = "General",
    val unit: String = "pcs",
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val stock: Long = 0L,
    val lowStockLimit: Long = 5L,
    val photoUri: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SaleItem(
    val productId: String = "",
    val name: String = "",
    val qty: Long = 0L,
    val unit: String = "pcs",
    val buyPrice: Double = 0.0,
    val sellPrice: Double = 0.0,
    val discount: Double = 0.0,
    val lineTotal: Double = 0.0,
    val profit: Double = 0.0
)

@IgnoreExtraProperties
data class Sale(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val invoiceNo: String = "",
    val items: List<SaleItem> = emptyList(),
    val totalItems: Long = 0L,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val profit: Double = 0.0,
    val paymentType: String = "cash", // "cash" or "baki"
    val customerId: String = "",
    val customerName: String = "Cash Sale",
    val customerPhone: String = "",
    val notes: String = "",
    val soldBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val name: String = "",
    val mobile: String = "",
    val address: String = "",
    val notes: String = "",
    val currentBalance: Double = 0.0,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val paymentNo: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val customerMobile: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "cash",
    val paymentDate: Long = 0L,
    val note: String = "",
    val receivedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Supplier(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val name: String = "",
    val mobile: String = "",
    val company: String = "",
    val address: String = "",
    val balance: Double = 0.0,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PurchaseItem(
    val productId: String = "",
    val name: String = "",
    val qty: Long = 0L,
    val unit: String = "pcs",
    val buyPrice: Double = 0.0,
    val lineTotal: Double = 0.0
)

@IgnoreExtraProperties
data class Purchase(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val purchaseNo: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val supplierMobile: String = "",
    val items: List<PurchaseItem> = emptyList(),
    val totalItems: Long = 0L,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val paymentType: String = "paid",
    val note: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PurchaseReturn(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val returnNo: String = "",
    val purchaseId: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class HealthFixLog(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val problemType: String = "",
    val description: String = "",
    val entityId: String = "",
    val entityCollection: String = "",
    val fixDetails: String = "",
    val fixedBy: String = "",
    @ServerTimestamp val fixedAt: Timestamp? = null
)

data class CompletedSaleReceipt(
    val invoiceNo: String = "",
    val storeName: String = "",
    val storePhone: String = "",
    val storeAddress: String = "",
    val items: List<SaleItem> = emptyList(),
    val totalItems: Long = 0L,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paymentType: String = "cash",
    val customerName: String = "Cash Sale",
    val customerPhone: String = "",
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formatTextReceipt(): String {
        val sName = storeName.ifBlank { "StoreOS Dokan" }
        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.US).format(java.util.Date(timestamp))
        val sb = StringBuilder()
        sb.appendLine("================================")
        sb.appendLine(sName.uppercase())
        if (storePhone.isNotBlank()) sb.appendLine("Phone: $storePhone")
        if (storeAddress.isNotBlank()) sb.appendLine("Address: $storeAddress")
        sb.appendLine("================================")
        sb.appendLine("Invoice : #$invoiceNo")
        sb.appendLine("Date    : $dateStr")
        sb.appendLine("Customer: $customerName")
        if (customerPhone.isNotBlank()) sb.appendLine("Mobile  : $customerPhone")
        sb.appendLine("--------------------------------")
        sb.appendLine("ITEMS:")
        items.forEach { item ->
            sb.appendLine("• ${item.name}")
            val qtyRate = "  ${item.qty} ${item.unit} x ৳${"%.2f".format(item.sellPrice)}"
            val lineTot = "৳${"%.2f".format(item.lineTotal)}"
            val spaces = " ".repeat((32 - qtyRate.length - lineTot.length).coerceAtLeast(1))
            sb.appendLine("$qtyRate$spaces$lineTot")
        }
        sb.appendLine("--------------------------------")
        val subStr = "Subtotal:".padEnd(20) + "৳${"%.2f".format(subtotal)}"
        sb.appendLine(subStr)
        if (discount > 0.0) {
            val discStr = "Discount:".padEnd(20) + "-৳${"%.2f".format(discount)}"
            sb.appendLine(discStr)
        }
        val grandStr = "TOTAL:".padEnd(20) + "৳${"%.2f".format(grandTotal)}"
        sb.appendLine(grandStr)
        sb.appendLine("Paid Via: ${paymentType.uppercase()}")
        if (notes.isNotBlank()) {
            sb.appendLine("Note: $notes")
        }
        sb.appendLine("================================")
        sb.appendLine("   Thank you for your visit!   ")
        sb.appendLine("================================")
        return sb.toString()
    }
}
