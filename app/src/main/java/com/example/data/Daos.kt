package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Define wrapper data classes for combined entity queries to support UI displays elegantly.
data class AccountRequestWithDetails(
    @Embedded val request: AccountRequest,
    @Relation(parentColumn = "family_id", entityColumn = "id") val family: Family,
    @Relation(parentColumn = "store_id", entityColumn = "id") val store: Store
)

data class DebtWithDetails(
    @Embedded val debt: Debt,
    @Relation(parentColumn = "family_id", entityColumn = "id") val family: Family,
    @Relation(parentColumn = "store_id", entityColumn = "id") val store: Store
)

data class TransactionWithDetails(
    @Embedded val transaction: DebtTransaction,
    @Relation(parentColumn = "actor_id", entityColumn = "id") val actor: User,
    @Relation(parentColumn = "member_id", entityColumn = "id") val member: User?
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("""
        SELECT r.code 
        FROM roles r 
        INNER JOIN user_roles ur ON r.id = ur.role_id 
        WHERE ur.user_id = :userId
    """)
    suspend fun getUserRoles(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRole(role: Role)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserRole(userRole: UserRole)
}

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families")
    fun getAllFamilies(): Flow<List<Family>>

    @Query("SELECT * FROM families WHERE id = :id LIMIT 1")
    suspend fun getFamilyById(id: String): Family?

    @Query("SELECT fm.*, u.name as userName FROM family_members fm INNER JOIN users u ON fm.user_id = u.id WHERE fm.family_id = :familyId")
    fun getFamilyMembers(familyId: String): Flow<List<FamilyMemberWithUser>>

    @Query("SELECT user_id FROM family_members WHERE family_id = :familyId")
    suspend fun getFamilyMemberUserIds(familyId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamily(family: Family)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyMember(member: FamilyMember)
}

data class FamilyMemberWithUser(
    @Embedded val member: FamilyMember,
    @Relation(parentColumn = "user_id", entityColumn = "id") val user: User
)

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores")
    fun getAllStores(): Flow<List<Store>>

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun getStoreById(id: String): Store?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: Store)
}

@Dao
interface AccountRequestDao {
    @Transaction
    @Query("SELECT * FROM account_requests ORDER BY created_at DESC")
    fun getAccountRequestsWithDetails(): Flow<List<AccountRequestWithDetails>>

    @Query("SELECT * FROM account_requests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountRequest?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: AccountRequest)

    @Update
    suspend fun update(request: AccountRequest)
}

@Dao
interface DebtDao {
    @Transaction
    @Query("SELECT * FROM debts ORDER BY updated_at DESC")
    fun getDebtsWithDetails(): Flow<List<DebtWithDetails>>

    @Query("SELECT * FROM debts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Debt?

    @Query("SELECT * FROM debts WHERE family_id = :familyId AND store_id = :storeId LIMIT 1")
    suspend fun getByFamilyAndStore(familyId: String, storeId: String): Debt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(debt: Debt)

    @Update
    suspend fun update(debt: Debt)
}

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM debt_transactions WHERE debt_id = :debtId ORDER BY client_created_at DESC")
    fun getTransactionsForDebt(debtId: String): Flow<List<TransactionWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: DebtTransaction)

    @Query("SELECT COUNT(*) FROM debt_transactions WHERE is_synced = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM debt_transactions WHERE is_synced = 0")
    suspend fun getUnsyncedTransactions(): List<DebtTransaction>

    @Query("UPDATE debt_transactions SET is_synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY created_at DESC")
    fun getAllNotifications(): Flow<List<Notification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: Notification)
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY created_at DESC")
    fun getAllActivityLogs(): Flow<List<ActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityLog)
}

@Dao
interface DebtPartyDao {
    @Query("SELECT * FROM debt_parties WHERE debt_id = :debtId")
    suspend fun getPartiesForDebt(debtId: String): List<DebtParty>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(party: DebtParty)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parties: List<DebtParty>)
}

