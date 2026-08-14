package com.nothingai.capture.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureCategoryTest {
    @Test
    fun classifiesCommonCaptureTypesFromTextAndLabels() {
        assertEquals(CaptureCategory.RECEIPT, categoryFor("Total ₹450 paid", "Document, Receipt"))
        assertEquals(CaptureCategory.SHOPPING, categoryFor("Add running shoes to cart", "Product"))
        assertEquals(CaptureCategory.TRAVEL, categoryFor("Flight to Delhi on Friday", "Airplane"))
        assertEquals(CaptureCategory.WORK, categoryFor("Project meeting agenda", "Document"))
        assertEquals(CaptureCategory.EVENT, categoryFor("Birthday party Saturday 7 PM", "Event"))
        assertEquals(CaptureCategory.IDEA, categoryFor("Build a better notebook app", "Idea"))
    }

    @Test
    fun fallsBackToOther() {
        assertEquals(CaptureCategory.OTHER, categoryFor("A quiet sunset", "Nature"))
    }
}
