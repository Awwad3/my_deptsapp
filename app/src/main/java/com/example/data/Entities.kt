package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    @ColumnInfo(name = "password_hash") val passwordHash: String? = null,
    @ColumnInfo(name = "google_id") val googleId: String? = null,
    @ColumnInfo(name = "phone_number") val phoneNumber: String? = null,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "roles")
data class Role(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "permissions")
data class Permission(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val description: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "role_permissions", primaryKeys = ["role_id", "permission_id"])
data class RolePermission(
    @ColumnInfo(name = "role_id") val roleId: String,
    @ColumnInfo(name = "permission_id") val permissionId: String
)

@Entity(tableName = "user_roles", primaryKeys = ["user_id", "role_id"])
data class UserRole(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "role_id") val roleId: String
)

@Entity(tableName = "families")
data class Family(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "creator_id") val creatorId: String,
    @ColumnInfo(name = "invite_code") val inviteCode: String,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "family_members")
data class FamilyMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "member_role") val memberRole: String = "member", // admin, member, viewer
    @ColumnInfo(name = "is_active") val isActive: Int = 1,
    @ColumnInfo(name = "joined_at") val joinedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "invite_code") val inviteCode: String,
    val status: String = "active", // active, inactive
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "store_members")
data class StoreMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "store_id") val storeId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val role: String, // manager, cashier
    @ColumnInfo(name = "joined_at") val joinedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "account_requests")
data class AccountRequest(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "store_id") val storeId: String,
    val status: String = "pending", // pending, approved, rejected
    @ColumnInfo(name = "requested_by") val requestedBy: String,
    @ColumnInfo(name = "approved_by") val approvedBy: String? = null,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "debt_type") val debtType: String, // store, family
    @ColumnInfo(name = "family_id") val familyId: String,
    @ColumnInfo(name = "store_id") val storeId: String,
    @ColumnInfo(name = "creator_id") val creatorId: String,
    @ColumnInfo(name = "total_balance") val totalBalance: Double = 0.0,
    val status: String = "active", // active, closed
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "debt_parties")
data class DebtParty(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "party_role") val partyRole: String // creditor, debtor
)

@Entity(tableName = "debt_transactions")
data class DebtTransaction(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "debt_id") val debtId: String,
    @ColumnInfo(name = "store_id") val storeId: String,
    @ColumnInfo(name = "actor_id") val actorId: String, // original: Merchant or staff member
    @ColumnInfo(name = "member_id") val memberId: String? = null, // customer family receiver (optional)
    @ColumnInfo(name = "transaction_type") val transactionType: String, // charge, payment, refund
    val amount: Double,
    val description: String? = null,
    @ColumnInfo(name = "attachment_path") val attachmentPath: String? = null,
    @ColumnInfo(name = "client_created_at") val clientCreatedAt: Long, // timestamp milis on device
    @ColumnInfo(name = "is_synced") val isSynced: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "transaction_attachments")
data class TransactionAttachment(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "uploaded_by") val uploadedBy: String,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val type: String, // e.g., request_approved, transaction_added
    val title: String,
    val message: String,
    @ColumnInfo(name = "is_read") val isRead: Int = 0,
    @ColumnInfo(name = "related_entity_type") val relatedEntityType: String? = null,
    @ColumnInfo(name = "related_entity_id") val relatedEntityId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP"
)

@Entity(tableName = "activity_logs")
data class ActivityLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val action: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val details: String? = null,
    @ColumnInfo(name = "ip_address") val ipAddress: String? = null,
    @ColumnInfo(name = "user_agent") val userAgent: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "CURRENT_TIMESTAMP"
)
