package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Role::class,
        Permission::class,
        RolePermission::class,
        UserRole::class,
        Family::class,
        FamilyMember::class,
        Store::class,
        StoreMember::class,
        AccountRequest::class,
        Debt::class,
        DebtParty::class,
        DebtTransaction::class,
        TransactionAttachment::class,
        Notification::class,
        ActivityLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun familyDao(): FamilyDao
    abstract fun storeDao(): StoreDao
    abstract fun accountRequestDao(): AccountRequestDao
    abstract fun debtDao(): DebtDao
    abstract fun transactionDao(): TransactionDao
    abstract fun notificationDao(): NotificationDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun debtPartyDao(): DebtPartyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deyoni_database"
                )
                .addCallback(AppDatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        suspend fun populateDatabase(db: AppDatabase) {
            // 1. Insert Users
            val merchantUser = User(
                id = "user_ahmed",
                name = "أحمد",
                email = "ahmed@alnahda.com",
                phoneNumber = "+966500000001",
                isActive = 1
            )
            val customerUser = User(
                id = "user_khaled",
                name = "خالد",
                email = "khaled@elhusami.com",
                phoneNumber = "+966500000002",
                isActive = 1
            )
            val familyMember1 = User(
                id = "user_fatima",
                name = "فاطمة",
                email = "fatima@elhusami.com",
                phoneNumber = "+966500000003",
                isActive = 1
            )
            val familyMember2 = User(
                id = "user_ahmed_son",
                name = "أحمد الابن",
                email = "ahmed_son@elhusami.com",
                phoneNumber = "+966500000004",
                isActive = 1
            )
            val otherFamilyCreator1 = User(
                id = "user_saleem",
                name = "سليم",
                email = "saleem@example.com",
                phoneNumber = "+966500000055",
                isActive = 1
            )
            val otherFamilyCreator2 = User(
                id = "user_harbi",
                name = "سليمان الحربي",
                email = "harbi@example.com",
                phoneNumber = "+966500000077",
                isActive = 1
            )

            db.userDao().insertUser(merchantUser)
            db.userDao().insertUser(customerUser)
            db.userDao().insertUser(familyMember1)
            db.userDao().insertUser(familyMember2)
            db.userDao().insertUser(otherFamilyCreator1)
            db.userDao().insertUser(otherFamilyCreator2)

            // Insert Roles in SQLite
            val merchantRole = Role(id = "role_merchant", name = "التاجر", code = "merchant", description = "صاحب المحل التجاري")
            val customerRole = Role(id = "role_customer", name = "العميل", code = "customer", description = "مدير أو فرد من العائلة المدونة بالدين")
            db.userDao().insertRole(merchantRole)
            db.userDao().insertRole(customerRole)

            // Insert User Roles in SQLite
            db.userDao().insertUserRole(UserRole(userId = "user_ahmed", roleId = "role_merchant"))
            db.userDao().insertUserRole(UserRole(userId = "user_khaled", roleId = "role_customer"))
            db.userDao().insertUserRole(UserRole(userId = "user_fatima", roleId = "role_customer"))
            db.userDao().insertUserRole(UserRole(userId = "user_ahmed_son", roleId = "role_customer"))
            db.userDao().insertUserRole(UserRole(userId = "user_saleem", roleId = "role_customer"))
            db.userDao().insertUserRole(UserRole(userId = "user_harbi", roleId = "role_customer"))

            // 2. Insert Families
            val husamiFamily = Family(
                id = "family_husami",
                name = "عائلة الحسامي",
                creatorId = "user_khaled",
                inviteCode = "FAM-HUSAMI-777",
                createdAt = "2026-06-15 12:00:00"
            )
            val saleemFamily = Family(
                id = "family_saleem",
                name = "عائلة سليم",
                creatorId = "user_saleem",
                inviteCode = "FAM-SALEEM-333"
            )
            val harbiFamily = Family(
                id = "family_harbi",
                name = "عائلة الحربي",
                creatorId = "user_harbi",
                inviteCode = "FAM-HARBI-999"
            )

            db.familyDao().insertFamily(husamiFamily)
            db.familyDao().insertFamily(saleemFamily)
            db.familyDao().insertFamily(harbiFamily)

            // 3. Insert Family Members
            db.familyDao().insertFamilyMember(
                FamilyMember(familyId = "family_husami", userId = "user_khaled", memberRole = "admin")
            )
            db.familyDao().insertFamilyMember(
                FamilyMember(familyId = "family_husami", userId = "user_fatima", memberRole = "member")
            )
            db.familyDao().insertFamilyMember(
                FamilyMember(familyId = "family_husami", userId = "user_ahmed_son", memberRole = "member")
            )

            // 4. Insert Stores
            val nahdaStore = Store(
                id = "store_nahda",
                ownerId = "user_ahmed",
                name = "النهضة سوبرماركت",
                phone = "+96611222333",
                address = "حي النهضة، الرياض",
                latitude = 24.7136,
                longitude = 46.6753,
                inviteCode = "STORE-NAHDA-888"
            )
            val secondStore = Store(
                id = "store_yasmin",
                ownerId = "user_ahmed",
                name = "الياسمين بقالة",
                phone = "+96611444555",
                address = "حي الياسمين، الرياض",
                latitude = 24.8124,
                longitude = 46.6341,
                inviteCode = "STORE-YASMIN-111"
            )

            db.storeDao().insertStore(nahdaStore)
            db.storeDao().insertStore(secondStore)

            // 5. Insert Account Requests matching: "🔔 طلبات فتح حساب (3)"
            // Request 1: عائلة الحسامي to النهضة (This might be pending or approved, wait, let's pre-populate pending ones)
            // Actually, let's pre-populate three pending ones:
            // 1) Family Saleem -> النهضة سوبرماركت (Pending)
            // 2) Family Harbi -> النهضة سوبرماركت (Pending)
            // 3) Family Husami -> الياسمين بقالة (Pending)
            // And also an already Approved one for Husami -> Nahda so they have an active Debt account!
            val req1 = AccountRequest(
                id = "req_1",
                familyId = "family_saleem",
                storeId = "store_nahda",
                status = "pending",
                requestedBy = "user_saleem",
                createdAt = "2026-06-18 14:30:00"
            )
            val req2 = AccountRequest(
                id = "req_2",
                familyId = "family_harbi",
                storeId = "store_nahda",
                status = "pending",
                requestedBy = "user_harbi",
                createdAt = "2026-06-18 15:45:00"
            )
            val req3 = AccountRequest(
                id = "req_3",
                familyId = "family_husami",
                storeId = "store_yasmin",
                status = "pending",
                requestedBy = "user_khaled",
                createdAt = "2026-06-19 08:30:00"
            )
            // We can also have an already accepted one for Husami -> Nahda, or keep Husami -> Nahda pending?
            // Wait, the diagram shows BOTH:
            // 1) "🔔 طلبات فتح حساب (3)" - with list item: "👤 عائلة الحسامي, 2026-06-18 14:30, [موافقة][رفض]"
            // 2) "📊 حسابات العملاء" - with list item: "🏠 عائلة الحسامي, الرصيد: 50 ريال"
            // To make BOTH appear beautifully, we can have "عائلة الحسامي" have a pending request FOR "الياسمين بقالة" (or another store request),
            // and an approved debt account for "النهضة سوبرماركت"!
            // Wait, yes! In the Merchant UI, he manages "النهضة سوبرماركت". He sees "طلبات فتح حساب (3)", and one of them is "عائلة الحسامي" requesting opening *another* or *this* account!
            // Let's make "عائلة الحسامي" request to "النهضة سوبرماركت" be pending as req_husami_pending, and we ALSO have an active Debt account for them. This lets the merchant approve/reject of another family member, or we can just have req_husami_pending as shown in the mockup:
            val reqHusami = AccountRequest(
                id = "req_husami",
                familyId = "family_husami",
                storeId = "store_nahda",
                status = "pending",
                requestedBy = "user_khaled",
                createdAt = "2026-06-18 14:30:00"
            )

            db.accountRequestDao().insert(req1)
            db.accountRequestDao().insert(req2)
            db.accountRequestDao().insert(reqHusami)

            // 6. Insert Debts (الحسابات)
            // We have an active account for family_husami at store_nahda with balance 50.0
            val debtHusami = Debt(
                id = "debt_husami_nahda",
                title = "عائلة الحسامي",
                debtType = "store",
                familyId = "family_husami",
                storeId = "store_nahda",
                creatorId = "user_ahmed",
                totalBalance = 50.0,
                status = "active",
                createdAt = "2026-06-17 10:00:00",
                updatedAt = "2026-06-18 14:35:00"
            )
            db.debtDao().insert(debtHusami)

            // 7. Insert Debt Transactions
            val tx1 = DebtTransaction(
                id = "tx_initial",
                debtId = "debt_husami_nahda",
                storeId = "store_nahda",
                actorId = "user_ahmed", // Ahmad registered it
                memberId = "user_khaled", // Delivered to Khaled
                transactionType = "charge",
                amount = 50.0,
                description = "حليب مع زبادي ومعلبات",
                clientCreatedAt = System.currentTimeMillis() - 60000 * 60 * 18, // 18 hrs ago
                isSynced = 1
            )
            db.transactionDao().insert(tx1)

            // Insert initial Debt Parties
            db.debtPartyDao().insert(DebtParty(debtId = "debt_husami_nahda", userId = "user_ahmed", partyRole = "creditor"))
            db.debtPartyDao().insert(DebtParty(debtId = "debt_husami_nahda", userId = "user_khaled", partyRole = "debtor"))
            db.debtPartyDao().insert(DebtParty(debtId = "debt_husami_nahda", userId = "user_fatima", partyRole = "debtor"))
            db.debtPartyDao().insert(DebtParty(debtId = "debt_husami_nahda", userId = "user_ahmed_son", partyRole = "debtor"))

            // 8. Insert Notification
            val notification = Notification(
                id = "notif_1",
                userId = "user_khaled",
                type = "transaction_added",
                title = "حركة دين جديدة",
                message = "تم تسجيل مبلغ 50 ريال في حسابكم لدى النهضة سوبرماركت بواسطة أحمد."
            )
            db.notificationDao().insert(notification)

            // 9. Insert Activity Logs matching instructions: record user actions like login, creation, modification
            db.activityLogDao().insert(
                ActivityLog(
                    id = "log_user_creation_1",
                    userId = "user_khaled",
                    action = "تسجيل حساب عميل جديد",
                    entityType = "users",
                    entityId = "user_khaled",
                    details = "تم إنشاء حساب المستخدم خالد بنجاح كمدير عائلة."
                )
            )
            db.activityLogDao().insert(
                ActivityLog(
                    id = "log_family_creation",
                    userId = "user_khaled",
                    action = "إنشاء عائلة جديدة",
                    entityType = "families",
                    entityId = "family_husami",
                    details = "تم إنشاء عائلة الحسامي مع كود دعوة FAM-HUSAMI-777"
                )
            )
            db.activityLogDao().insert(
                ActivityLog(
                    id = "log_debt_charge",
                    userId = "user_ahmed",
                    action = "تسجيل حركة مبيعات بالدين",
                    entityType = "debt_transactions",
                    entityId = "tx_initial",
                    details = "تم قيد مبلغ 50.0 ريال شراء بالدين على عائلة الحسامي للسلعة: حليب مع زبادي ومعلبات"
                )
            )
        }
    }
}
