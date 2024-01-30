package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.stubs.indexes.PhpDepthLimitedRecursiveElementVisitor

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class StringConcatenationToHeredocConverterTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/php"

    fun doTestIntentionAction(testName: String, hint: String?) {
        val beforeSuffix = ".template.before.php"
        val afterSuffix = ".template.after.php"

        myFixture.configureByFile("$testName$beforeSuffix")
        val action = myFixture.findSingleIntention(hint!!)
        assertNotNull(action)
        myFixture.launchAction(action)
        myFixture.checkResultByFile("$testName$afterSuffix")
    }

    fun doTestIntentionAvailability(testName: String, hint: String?) {
        myFixture.configureByFile(testName)
        assertEmpty(myFixture.filterAvailableIntentions(hint!!))
    }

    fun testIntention() {
        arrayOf(
            "templates/concatenation/with_escapings",
        ).forEach { fileName -> doTestIntentionAction(fileName, StringConcatenationToHeredocConverter.INTENTION_HINT) }
    }

    fun testIsNotAvailable() {
        arrayOf(
            "not_available/caret_not_on_concat.php",
            "not_available/caret_not_on_echo_with_commas.php",
            "not_available/caret_on_echo_but_no_commas.php",
            "not_available/empty.php",
        ).forEach { fileName -> doTestIntentionAvailability(fileName, StringConcatenationToHeredocConverter.INTENTION_HINT) }
    }

    fun testIsAvailableEverywhereWithinEchoWithCommas() {
        // GIVEN
        val psiFile = myFixture.configureByFile("templates/echo/with_commas.php")


        // WHEN/THEN
        `assert intention available from any descendant of element`(
            assertInstanceOf(psiFile, PhpFile::class.java)
        ) { it is PhpEchoStatement }
    }

    fun testIsAvailableEverywhereWithinConcatenationExpression() {
        // GIVEN
        val psiFile = myFixture.configureByFile("templates/concatenation/complex.template.before.php")


        // WHEN/THEN
        `assert intention available from any descendant of element`(
            assertInstanceOf(psiFile, PhpFile::class.java)
        ) { it is ConcatenationExpression }
    }

    fun `assert intention available from any descendant of element`(phpFile: PhpFile, filter: PsiElementFilter) {
        // sanity checks
        assertFalse(PsiErrorElementUtil.hasErrors(project, phpFile.virtualFile))

        val sut = StringConcatenationToHeredocConverter()
        val editor = myFixture.editor


        // WHEN/THEN
        val foundContainerElements = PsiTreeUtil.collectElements(phpFile, filter)
        assertTrue(foundContainerElements.size >= 1)

        // for concatenations, this will be the top-most element
        val firstContainingElement = foundContainerElements[0]

        // when cursor is positioned within the echoStatement, regardless of where, it should return true
        firstContainingElement.acceptChildren(object: PhpDepthLimitedRecursiveElementVisitor(){
            override fun visitPhpElement(element: PhpPsiElement) {
                super.visitPhpElement(element)
                assertTrue(sut.isAvailable(project, editor, element))
            }
        })
    }
}
