package ir.saba.invoice.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.saba.invoice.data.Invoice
import ir.saba.invoice.data.OfficialSubmission
import ir.saba.invoice.domain.InvoiceSubject
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale

private val ink=SuraPalette.Navy
private val accent=SuraPalette.Teal
private val muted=SuraPalette.Muted
private val pale=SuraPalette.Soft
private val border=SuraPalette.Edge

internal fun draftTotal(invoices:List<Invoice>):BigInteger =
    invoices.filter{it.status=="DRAFT"}.fold(BigInteger.ZERO){sum,invoice->sum+BigInteger.valueOf(invoice.totalAmountRial)}

private fun draftMoney(value:Number):String =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag("fa-IR")).format(value)+" ریال"

@Composable
internal fun DraftsSummary(drafts:List<Invoice>,visibleCount:Int) {
    val shape=RoundedCornerShape(22.dp)
    val total=remember(drafts){draftTotal(drafts)}
    Card(Modifier.fillMaxWidth().testTag("draftSummary"),shape=shape,
        colors=CardDefaults.cardColors(containerColor=ink)) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(ink,Color(0xFF116773))))
            .padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=RoundedCornerShape(13.dp),color=Color(0xFF2B6475)) {
                    Icon(Icons.Default.Edit,null,Modifier.padding(10.dp).size(22.dp),tint=Color(0xFFD6F3F2))
                }
                Column(Modifier.weight(1f)) {
                    Text("میز کار پیش‌نویس‌ها",color=Color.White,fontWeight=FontWeight.Bold,
                        style=MaterialTheme.typography.titleMedium)
                    Text("ثبت محفوظ؛ قبل از ارسال قابل بررسی",color=SuraPalette.OnDark,
                        style=MaterialTheme.typography.labelSmall)
                }
                Surface(color=Color(0xFFE5F5F4),shape=RoundedCornerShape(12.dp)) {
                    Text(drafts.size.toString(),Modifier.padding(horizontal=12.dp,vertical=7.dp),
                        color=ink,fontWeight=FontWeight.ExtraBold,style=MaterialTheme.typography.titleMedium)
                }
            }
            HorizontalDivider(color=Color(0xFF497885))
            Text("جمع مبلغ پیش‌نویس‌ها",color=SuraPalette.OnDark,style=MaterialTheme.typography.labelMedium)
            Text(draftMoney(total),color=Color.White,fontWeight=FontWeight.ExtraBold,
                style=MaterialTheme.typography.titleLarge)
            if(visibleCount!=drafts.size) Text("نمایش $visibleCount از ${drafts.size} پیش‌نویس",
                color=SuraPalette.OnDark,style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun DraftInvoiceCard(invoice:Invoice,customerName:String?,receipt:OfficialSubmission?,busy:Boolean,
                              onEdit:()->Unit,onPreview:()->Unit) {
    val event=invoiceEventStatus(invoice,receipt)
    val editable=event==InvoiceEventStatus.DRAFT && canEditInvoicePreview(invoice,receipt)
    val subjectLabel=InvoiceSubject.label(invoice.subject)
    val subjectColor=when(invoice.subject) {
        InvoiceSubject.CORRECTION->Color(0xFF805A1B)
        InvoiceSubject.CANCELLATION->Color(0xFF9E3944)
        else->accent
    }
    val subjectBg=when(invoice.subject) {
        InvoiceSubject.CORRECTION->Color(0xFFFFF3DE)
        InvoiceSubject.CANCELLATION->Color(0xFFFDEEF0)
        else->Color(0xFFE4F4F4)
    }
    Card(Modifier.fillMaxWidth().testTag("invoiceListCard_${invoice.id}"),
        shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Color.White),
        border=BorderStroke(1.dp,border),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=RoundedCornerShape(14.dp),color=subjectBg) {
                    Icon(Icons.AutoMirrored.Filled.List,null,Modifier.padding(11.dp).size(23.dp),tint=subjectColor)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(customerName?.takeIf{it.isNotBlank()}?:"مشتری نامشخص",color=ink,
                        fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text("فاکتور ${invoice.invoiceNumber}",color=muted,
                        style=MaterialTheme.typography.labelMedium)
                }
                Surface(shape=RoundedCornerShape(9.dp),color=subjectBg) {
                    Text(subjectLabel,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=subjectColor,
                        style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text(invoice.issueDate,color=muted,style=MaterialTheme.typography.bodySmall)
                Text(event.label,color=if(editable) accent else MaterialTheme.colorScheme.error,
                    fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.labelMedium)
            }
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),color=pale) {
                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("مبلغ کل صورتحساب",color=muted,style=MaterialTheme.typography.labelSmall)
                    Text(draftMoney(invoice.totalAmountRial),color=ink,fontWeight=FontWeight.ExtraBold,
                        style=MaterialTheme.typography.titleMedium)
                    Text("مالیات و عوارض: "+draftMoney(invoice.taxAmountRial),
                        color=muted,style=MaterialTheme.typography.bodySmall)
                }
            }
            receipt?.takeIf{it.taxId.isNotBlank()}?.let {
                Text("شماره مالیاتی: "+it.taxId,style=MaterialTheme.typography.labelSmall,color=muted)
            }
            receipt?.referenceNumber?.takeIf{it.isNotBlank()}?.let {
                Text("شماره پیگیری: "+it,style=MaterialTheme.typography.labelSmall,color=muted)
            }
            if(editable) {
                Button(enabled=!busy,onClick=onEdit,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("draftEdit_${invoice.id}"),
                    shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=accent)) {
                    Icon(Icons.Default.Edit,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ادامه ویرایش")
                }
            }
            OutlinedButton(enabled=!busy,onClick=onPreview,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("draftPreview_${invoice.id}"),
                shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,border),
                colors=ButtonDefaults.outlinedButtonColors(contentColor=ink)) {
                Text("مشاهده جزئیات",fontWeight=FontWeight.SemiBold)
            }
            if(!editable) Text("برای تعیین تکلیف، جزئیات و وضعیت ارسال را بررسی کنید.",
                color=muted,style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun DraftsEmpty(searching:Boolean,onClear:()->Unit,onNew:()->Unit,busy:Boolean) {
    Card(Modifier.fillMaxWidth().testTag("draftEmpty"),shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=Color.White),border=BorderStroke(1.dp,border)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Surface(shape=RoundedCornerShape(18.dp),color=pale) {
                Icon(Icons.AutoMirrored.Filled.List,null,Modifier.padding(15.dp).size(30.dp),tint=accent)
            }
            Text(if(searching) "پیش‌نویسی مطابق جستجو نیست" else "هنوز پیش‌نویسی ندارید",
                color=ink,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
            Text(if(searching) "شماره یا نام مشتری دیگری را جستجو کنید." else "صورتحساب جدید ایجاد کنید و قبل از ارسال آن را ذخیره کنید.",
                color=muted,style=MaterialTheme.typography.bodySmall,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(searching) OutlinedButton(onClick=onClear,enabled=!busy){Text("پاک کردن جستجو")}
            else Button(onClick=onNew,enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=accent)){
                Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("ایجاد پیش‌نویس")
            }
        }
    }
}
