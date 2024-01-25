package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.jetbrains.php.lang.PhpFileType
import com.jetbrains.php.lang.psi.PhpFile

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class StringConcatenationToHeredocTest : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test/testData/rename"

    fun testIsElementWithinEchoWithCommas() {
        val psiFile = myFixture.configureByText(PhpFileType.INSTANCE, """
            ${'$'}a = 5;
            echo ${'$'}a, 'hello', 55;
        """.trimIndent())
        val phpFile = assertInstanceOf(psiFile, PhpFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, phpFile.virtualFile))
    }


    fun testRename() {
        myFixture.testRename("foo.xml", "foo_after.xml", "a2")
    }
}
