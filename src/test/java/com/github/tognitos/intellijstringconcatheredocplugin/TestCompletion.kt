package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.stubs.indexes.PhpDepthLimitedRecursiveElementVisitor

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class TestCompletion : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/java/com/github/tognitos/intellijstringconcatheredocplugin"

    fun testCompletion() {
        myFixture.configureByFiles("CompleteTestData.kt", "DefaultTestData.php")
        println(myFixture.availableIntentions)
        myFixture.complete(CompletionType.BASIC)
        val lookupElementStrings = myFixture.lookupElementStrings
        assertNotNull(lookupElementStrings)
        println(lookupElementStrings)
    }
}
