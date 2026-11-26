package ir.saba.invoice.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.OfficialSubmission
import ir.saba.invoice.domain.InvoiceSubject

enum class InvoiceQuickAction(val label:String) {
    COPY("کپی"), CORRECT("اصلاحیه"), CANCEL("ابطال"), EDIT("ویرایش")
}

internal fun invoiceSubjectColor(subject:String):Color = when(subject) {
    InvoiceSubject.CORRECTION -> Color(0xFF805A1B)
    InvoiceSubject.CANCELLATION -> Color(0xFFAA343D)
    else -> SuraPalette.Teal
}

internal fun availableInvoiceQuickActions(invoice:Invoice,receipt:OfficialSubmission?,invoices:List<Invoice>):List<InvoiceQuickAction> = buildList {
    if(canCopyAsNewInvoice(invoice)) add(InvoiceQuickAction.COPY)
    if(canStartCorrection(invoice,receipt) && invoices.none {
        it.companyId==invoice.companyId && it.status!="CANCELLED" && it.id!=invoice.id &&
        it.referenceTaxId==receipt?.taxId
    }) add(InvoiceQuickAction.CORRECT)
    if(canDraftOriginalCancellation(invoice,receipt,invoices)) add(InvoiceQuickAction.CANCEL)
    if(invoice.status=="DRAFT" && canEditInvoicePreview(invoice,receipt)) add(InvoiceQuickAction.EDIT)
}

@Composable
internal fun InvoiceQuickActions(invoice:Invoice,receipt:OfficialSubmission?,invoices:List<Invoice>,
                                 enabled:Boolean=true,onAction:(InvoiceQuickAction)->Unit) {
    val actions=availableInvoiceQuickActions(invoice,receipt,invoices)
    if(actions.isEmpty()) return
    HorizontalDivider(Modifier.padding(horizontal=12.dp),color=SuraPalette.Edge)
    Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=2.dp),
        horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
        actions.forEach { action ->
            val tint=when(action) {
                InvoiceQuickAction.CANCEL->Color(0xFFAA343D)
                InvoiceQuickAction.CORRECT->Color(0xFF805A1B)
                else->SuraPalette.Teal
            }
            TextButton(onClick={onAction(action)},enabled=enabled,
                modifier=Modifier.heightIn(min=48.dp).testTag("invoiceQuick_${action.name}_${invoice.id}"),
                contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp),
                colors=ButtonDefaults.textButtonColors(contentColor=tint)) {
                if(action==InvoiceQuickAction.COPY) {
                    Canvas(Modifier.size(19.dp).semantics{contentDescription="کپی فاکتور"}) {
                        val w=size.width;val h=size.height;val line=Stroke(1.6.dp.toPx())
                        drawRoundRect(tint,Offset(w*.10f,h*.06f),Size(w*.60f,h*.66f),CornerRadius(2.dp.toPx()),style=line)
                        drawRoundRect(tint,Offset(w*.31f,h*.29f),Size(w*.59f,h*.65f),CornerRadius(2.dp.toPx()),style=line)
                    }
                } else {
                    Icon(when(action) {
                        InvoiceQuickAction.CORRECT->Icons.Default.Refresh
                        InvoiceQuickAction.CANCEL->Icons.Default.Close
                        else->Icons.Default.Edit
                    },contentDescription=null,modifier=Modifier.size(19.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(action.label,style=MaterialTheme.typography.labelMedium,maxLines=1)
            }
        }
    }
}
