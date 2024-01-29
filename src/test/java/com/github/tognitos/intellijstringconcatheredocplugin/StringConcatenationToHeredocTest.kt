package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.PsiElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpEchoStatement
import com.jetbrains.php.lang.psi.elements.PhpPsiElement
import com.jetbrains.php.lang.psi.elements.impl.PhpEchoStatementImpl
import com.jetbrains.php.lang.psi.stubs.indexes.PhpDepthLimitedRecursiveElementVisitor
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
import org.jetbrains.uast.util.isInstanceOf

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class StringConcatenationToHeredocTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/php/echo"


    fun testIsElementWithinEchoWithCommas() {
        // GIVEN
        val psiFile = myFixture.configureByFile("echo_with_commas.php")
        val phpFile = assertInstanceOf(psiFile, PhpFile::class.java)



        // sanity checks
        assertFalse(PsiErrorElementUtil.hasErrors(project, phpFile.virtualFile))

        // WHEN/THEN
        val echoStatements = PsiTreeUtil.collectElements(phpFile) { it is PhpEchoStatement }
        assertSize(1, echoStatements)
        val echoStatement = echoStatements[0]

        // when cursor is positioned within the echoStatement, regardless of where, it should return true
        echoStatement.acceptChildren(object: PhpDepthLimitedRecursiveElementVisitor(){
            override fun visitPhpElement(element: PhpPsiElement?) {
                super.visitPhpElement(element)

//                assertTrue(sut.isAvailable(project, editor, element))
            }
        })
    }


    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }
}
