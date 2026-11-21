package space.teleopen.clauden.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {

    @Test
    fun headingBecomesSeparateBlock() {
        val blocks = parseTextBlocks(
            """
            Дарou! Сейчас гляну файл.

            Карты (Visa)

            - В списке способов оплаты есть 11, эквайринг
            """.trimIndent()
        )
        assertEquals(3, blocks.size)
        assertEquals("Карты (Visa)", (blocks[1] as MdLine.Paragraph).text)
        assertTrue(blocks[2] is MdLine.Bullet)
    }

    @Test
    fun markdownHeadingIsParsedWithLevel() {
        val blocks = parseTextBlocks(
            """
            ### Карты (Visa)
            В списке способов оплаты есть `11`.
            """.trimIndent()
        )
        assertEquals(2, blocks.size)
        val heading = blocks[0] as MdLine.Heading
        assertEquals(3, heading.level)
        assertEquals("Карты (Visa)", heading.text)
        assertEquals("В списке способов оплаты есть `11`.", (blocks[1] as MdLine.Paragraph).text)
    }

    @Test
    fun bulletsKeepContinuationAndSpacing() {
        val blocks = parseTextBlocks(
            """
            - Первый пункт
            продолжение пункта
            - Второй пункт
            """.trimIndent()
        )
        assertEquals(2, blocks.size)
        assertEquals("Первый пункт продолжение пункта", (blocks[0] as MdLine.Bullet).text)
        assertEquals("Второй пункт", (blocks[1] as MdLine.Bullet).text)
    }

    @Test
    fun orderedListKeepsNumbers() {
        val blocks = parseTextBlocks(
            """
            Что уточнить у менеджера

            1. Какие карты и из каких стран проходят
            2. Какие монеты и сети доступны
            3. В какой валюте выставлять сумму
            """.trimIndent()
        )
        assertEquals(4, blocks.size)
        assertEquals("1", (blocks[1] as MdLine.Ordered).number)
        assertEquals("2", (blocks[2] as MdLine.Ordered).number)
        assertEquals("3", (blocks[3] as MdLine.Ordered).number)
        assertEquals("Какие монеты и сети доступны", (blocks[2] as MdLine.Ordered).text)
    }

    @Test
    fun boldStandaloneLineBecomesHeading() {
        val blocks = parseTextBlocks(
            """
            Документация это подтверждает лишь отчасти.
            **Карты (Visa)**
            - В списке способов оплаты есть 11
            """.trimIndent()
        )
        assertEquals(3, blocks.size)
        assertEquals("Карты (Visa)", (blocks[1] as MdLine.Heading).text)
        assertEquals(3, (blocks[1] as MdLine.Heading).level)
    }

    @Test
    fun paragraphLineBreaksArePreserved() {
        val blocks = parseTextBlocks("Первая строка\nВторая строка")
        assertEquals(1, blocks.size)
        assertEquals("Первая строка\nВторая строка", (blocks[0] as MdLine.Paragraph).text)
    }

    @Test
    fun tableStaysTable() {
        val blocks = parseTextBlocks(
            """
            | Key | Value |
            | --- | --- |
            | X-MerchantId | 1 |
            """.trimIndent()
        )
        assertEquals(1, blocks.size)
        val rows = (blocks[0] as MdLine.Table).rows
        assertEquals(2, rows.size)
        assertEquals(listOf("Key", "Value"), rows[0])
        assertEquals(listOf("X-MerchantId", "1"), rows[1])
    }
}
