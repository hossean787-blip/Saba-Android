package ir.saba.invoice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Local simulation records. Never represents a government submission. */
@Entity(tableName = "moadian_simulations", indices = [Index("companyId"), Index(value = ["invoiceId"], unique = true)])
data class MoadianSimulation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: Long,
    val invoiceId: Long,
    val requestId: String,
    val payloadHash: String,
    val localStatus: String = "SIMULATED",
    val createdAtMillis: Long
)

@Dao interface MoadianSimulationDao {
    @Insert suspend fun insert(row: MoadianSimulation): Long
    @Query("SELECT * FROM moadian_simulations WHERE companyId = :companyId ORDER BY id DESC")
    suspend fun forCompany(companyId: Long): List<MoadianSimulation>
    @Query("SELECT * FROM moadian_simulations WHERE invoiceId = :invoiceId ORDER BY id DESC LIMIT 1")
    suspend fun latestForInvoice(invoiceId: Long): MoadianSimulation?
}
