package ir.saba.invoice.data

import androidx.room.withTransaction

/** Imports new records only; never overwrites an existing taxpayer or customer. */
class ExcelTransferService(private val db: SabaDatabase) {
    companion object {
        val taxpayerHeaders = listOf("نام", "شناسه ملی", "شماره اقتصادی", "آدرس", "تلفن",
            "نوع مؤدی", "شماره ثبت", "شروع سریال صورتحساب", "شروع سریال ارجاعی",
            "فکس", "کد پستی", "استان", "شهرستان", "موبایل")
        val customerHeaders = listOf("نام", "شناسه ملی", "شماره اقتصادی", "کد پستی",
            "آدرس", "تلفن", "نوع شخص", "موبایل", "فکس", "ایمیل",
            "استان", "شهرستان", "شماره ثبت", "شماره شناسنامه", "مشتری", "تأمین کننده")
    }

    suspend fun export(scope: String, currentCompanyId: Long?): ByteArray {
        return if (scope == "taxpayers") {
            val details = db.moadianDetailsDao().all().associateBy { it.companyId }
            ExcelWorkbook.write(taxpayerHeaders, db.companyDao().all().map { c ->
                val d = details[c.id] ?: MoadianDetails(c.id)
                listOf(c.name, c.nationalId, c.economicId, c.address, c.phone,
                    d.taxpayerType, d.registrationNumber, d.invoiceSerialStart,
                    d.referenceSerialStart, d.fax, d.postalCode, d.province,
                    d.county, d.mobile)
            })
        } else {
            val id = requireNotNull(currentCompanyId) { "ابتدا در خانه یک مؤدی انتخاب کنید." }
            val profiles = db.customerProfileDao().forCompany(id).associateBy { it.customerId }
            ExcelWorkbook.write(customerHeaders, db.customerDao().forCompany(id).map { c ->
                val d = profiles[c.id] ?: CustomerProfile(c.id, id)
                listOf(c.name, c.nationalId, c.economicId, c.postalCode, c.address,
                    c.phone, d.accountType, d.mobile, d.fax, d.email, d.province,
                    d.county, d.registrationNumber, d.birthCertificateNumber,
                    if (d.isCustomer) "بله" else "خیر",
                    if (d.isSupplier) "بله" else "خیر")
            })
        }
    }

    fun template(scope: String): ByteArray =
        ExcelWorkbook.write(if (scope == "taxpayers") taxpayerHeaders else customerHeaders, emptyList())

    suspend fun import(scope: String, currentCompanyId: Long?, bytes: ByteArray): Int {
        val rows = ExcelWorkbook.read(bytes)
        val headers = if (scope == "taxpayers") taxpayerHeaders else customerHeaders
        require(rows.firstOrNull()?.take(headers.size) == headers) {
            "ستون‌های فایل با الگوی سورا مطابقت ندارند؛ الگوی جدید را دانلود کنید."
        }
        val records = rows.drop(1).filter { row -> row.any(String::isNotBlank) }
        require(records.size <= 5000) { "تعداد ردیف‌ها بیش از حد مجاز است." }
        fun cell(row: List<String>, i: Int) = row.getOrElse(i) { "" }.trim()
        records.forEachIndexed { i, row ->
            require(cell(row, 0).isNotEmpty()) { "نام در ردیف ${i + 2} خالی است." }
            if (scope == "taxpayers") {
                require(cell(row, 1).isNotEmpty()) { "شناسه ملی ردیف ${i + 2} خالی است." }
            }
        }
        return db.withTransaction {
            var inserted = 0
            if (scope == "taxpayers") {
                val current = db.companyDao().all().map { it.nationalId }.toMutableSet()
                for (row in records) {
                    val national = cell(row, 1)
                    if (!current.add(national)) continue
                    val id = db.companyDao().insert(Company(
                        purchaserId = "owner",
                        name = cell(row, 0), nationalId = national,
                        economicId = cell(row, 2), address = cell(row, 3),
                        phone = cell(row, 4)
                    ))
                    db.moadianDetailsDao().upsert(MoadianDetails(
                        companyId = id, taxpayerType = cell(row, 5).ifBlank { "UNSPECIFIED" },
                        registrationNumber = cell(row, 6), invoiceSerialStart = cell(row, 7),
                        referenceSerialStart = cell(row, 8), fax = cell(row, 9),
                        postalCode = cell(row, 10), province = cell(row, 11),
                        county = cell(row, 12), mobile = cell(row, 13)
                    ))
                    inserted++
                }
            } else {
                val id = requireNotNull(currentCompanyId) { "ابتدا در خانه یک مؤدی انتخاب کنید." }
                require(db.companyDao().byId(id) != null) { "مؤدی انتخاب‌شده وجود ندارد." }
                val existing = db.customerDao().forCompany(id).map { it.nationalId to it.name }.toMutableSet()
                for (row in records) {
                    val identity = cell(row, 1) to cell(row, 0)
                    if (!existing.add(identity)) continue
                    val customerId = db.customerDao().insert(Customer(
                        companyId = id, name = cell(row, 0), nationalId = cell(row, 1),
                        economicId = cell(row, 2), postalCode = cell(row, 3),
                        address = cell(row, 4), phone = cell(row, 5)
                    ))
                    db.customerProfileDao().upsert(CustomerProfile(
                        customerId = customerId, companyId = id,
                        accountType = cell(row, 6).ifBlank { "NATURAL" },
                        mobile = cell(row, 7), fax = cell(row, 8), email = cell(row, 9),
                        province = cell(row, 10), county = cell(row, 11),
                        registrationNumber = cell(row, 12),
                        birthCertificateNumber = cell(row, 13),
                        isCustomer = cell(row, 14) != "خیر",
                        isSupplier = cell(row, 15) == "بله"
                    ))
                    inserted++
                }
            }
            inserted
        }
    }
}
