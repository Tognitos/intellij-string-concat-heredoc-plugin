/**
 * Some examples
 * @see https://github.com/JetBrains/intellij-community/blob/idea/233.14015.106/spellchecker/testSrc/com/intellij/spellchecker/inspector/AcceptWordAsCorrectTest.java
 * @see https://github.com/JetBrains/intellij-community/blob/idea/233.14015.106/java/java-tests/testSrc/com/intellij/copyright/JavaCopyrightTest.kt
 * @see https://github.com/JetBrains/intellij-community/blob/idea/233.14015.106/platform/testFramework/src/com/intellij/testFramework/fixtures/CodeInsightTestFixture.java
 *
 * An actual real life example `ComparingStringReferencesInspectionTest`
 * @see https://github.com/JetBrains/intellij-sdk-code-samples/blob/main/comparing_string_references_inspection/src/test/java/org/intellij/sdk/codeInspection/ComparingStringReferencesInspectionTest.java
 */

package com.github.tognitos.intellijstringconcatheredocplugin


import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class TestCompletionKt : BasePlatformTestCase() {

    override fun getTestDataPath() = "src/test"

    fun testCompletion() {
        myFixture.configureByFiles("java/com/github/tognitos/intellijstringconcatheredocplugin/CompleteTestData.php", "java/com/github/tognitos/intellijstringconcatheredocplugin/DefaultTestData.php")

        val availableIntentions = myFixture.availableIntentions
        assertNotNull(availableIntentions)

        println(myFixture.availableIntentions)
    }
}
