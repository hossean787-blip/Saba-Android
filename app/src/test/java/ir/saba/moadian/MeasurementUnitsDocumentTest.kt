package ir.saba.moadian

import org.junit.Assert.*
import org.junit.Test

class MeasurementUnitsDocumentTest {
    @Test fun allOfficialCodesMatchSuppliedV118Document() {
        val expected = "1611 1612 1613 1618 1619 1620 1624 1627 1628 1629 1631 1640 1641 1642 1643 1645 1649 1661 1668 1673 1694 1637 1650 1683 1656 1630 163 1660 1647 1689 1690 1635 164 1638 161 1625 1654 1646 1644 1617 162 165 1610 1615 1680 1639 1614 1687 1693 166 1666 1626 1648 1684 169 1651 1633 1679 168 1665 1659 1636 1622 1616 1652 1678 16100 16101 16102 16103 16104 16105 1669 1676 16110 16111 16112 16113 16114 16115 16116 1632 1653 16108 16117 16118 16119 1681 1667 16120 16121 16122 16125 16126 16127 16128 16129 16130 16131 16132 16133 16134".split(" ")
        assertEquals(102, MoadianMeasurementUnits.size)
        assertEquals(expected, MoadianMeasurementUnits.search().map { it.code })
        assertEquals("نفر-ماه", MoadianMeasurementUnits.nameOf("16134"))
        assertEquals("کیلوگرم", MoadianMeasurementUnits.nameOf("164"))
        assertNull(MoadianMeasurementUnits.nameOf("99999"))
    }
    @Test fun lookupAcceptsPersianArabicDigitsAndNormalizedNames() {
        assertEquals("16134", MoadianMeasurementUnits.search("۱۶۱۳۴").single().code)
        assertEquals("16134", MoadianMeasurementUnits.search("١٦١٣٤").single().code)
        assertEquals("16134", MoadianMeasurementUnits.search("نفر ماه").single().code)
        assertEquals("164", MoadianMeasurementUnits.search("كيلوگرم").single().code)
        assertTrue(MoadianMeasurementUnits.search("99999").isEmpty())
    }
}
