// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.php.lang.intentions.PhpReplaceQuotesIntention
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.*
import com.jetbrains.php.lang.psi.elements.impl.PhpEchoStatementImpl
import com.jetbrains.php.lang.psi.stubs.indexes.PhpDepthLimitedRecursiveElementVisitor
import io.ktor.http.*
import java.rmi.UnexpectedException

/**
 * Implements an intention action to replace string concatenations with HEREDOC strings
 */
class StringConcatenationToHeredocConverter : PsiElementBaseIntentionAction(), IntentionAction {
    /**
     * If this action is applicable, returns the text to be shown in the list of intention actions available.
     */
    override fun getText(): String {
        return INTENTION_HINT
    }

    /**
     * Returns text for name of this family of intentions.
     * It is used to externalize "auto-show" state of intentions.
     * It is also the directory name for the descriptions.
     *
     * @return the intention family name.
     */
    override fun getFamilyName(): String {
        return "StringManipulationIntention"
    }

    /**
     * Checks whether this intention is available at the caret offset in file - the caret must sit:
     * - at any point inside a concatenation expression
     * - at any point inside an "echo" expression with commas
     *
     *
     * Note: this method must do its checks quickly and return.
     *
     * @param project a reference to the Project object being edited.
     * @param editor  a reference to the object editing the project source
     * @param element a reference to the PSI element currently under the caret
     * @return `true` if the caret is in an expression which can be converted by this plugin
     * `false` for all other types of caret positions
     */
    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        // TODO: add plugin settings to display intention with minimum amount of concatenation expressions and/or echo commas
        // TODO consider displaying intention only if expressions are present

        val isConcat = isElementOrAncestorAConcatenation(element)
        val isEchoWithCommas = isElementOrAncestorAEchoWithCommas(element)
        return isConcat || isEchoWithCommas
    }

    /**
     * Modifies the Psi to change a ternary expression to an if-then-else statement.
     * If the ternary is part of a declaration, the declaration is separated and moved above the if-then-else statement.
     * Called when user selects this intention action from the available intentions list.
     *
     * @param project a reference to the Project object being edited.
     * @param editor  a reference to the object editing the project source
     * @param element a reference to the PSI element currently under the caret
     * @throws IncorrectOperationException Thrown by underlying (Psi model) write action context
     * when manipulation of the psi tree fails.
     * @see StringConcatenationToHeredocConverter.startInWriteAction
     */
    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        // the "top" of the concatenation expression (if we have multiple concats, then there is a tree of Concatenations
        val topConcatenation = PsiTreeUtil.getTopmostParentOfType(
            element,
            ConcatenationExpression::class.java
        )

        topConcatenation!!

        val (extractedVariableDeclarations, heredocContent) = runCatching {
            useVisitor(topConcatenation)
        }.onFailure { e ->
            e.printStackTrace()
            return
        }.getOrThrow()

        // TODO : let user pick delimiter, or choose based on interpreted content (e.g. HTML or JS or SQL)
        val heredocDelimiter = "HEREDOC_DELIMITER"
        val heredocPsi = PhpPsiElementFactory.createPhpPsiFromText(
            project,
            StringLiteralExpression::class.java,
            " <<<$heredocDelimiter\n$heredocContent\n$heredocDelimiter;"
        )
        val topConcatenationStatement = PsiTreeUtil.getParentOfType(topConcatenation, Statement::class.java)!!
        val storeParent = topConcatenationStatement.parent

        topConcatenation.replace(heredocPsi)

        extractedVariableDeclarations.forEach { preStatementText ->
            storeParent.let {
                val preStatementPsi = PhpPsiElementFactory.createStatement(
                    project,
                    preStatementText
                )
                it.addBefore(
                    preStatementPsi,
                    topConcatenationStatement
                )}
        }
    }

    /**
     * Indicates this intention action expects the Psi framework to provide the write action context for any changes.
     *
     * @return `true` if the intention requires a write action context to be provided or `false` if this
     * intention action will start a write action
     */
    override fun startInWriteAction(): Boolean {
        return true
    }

    companion object {
        const val INTENTION_HINT = "<<cc Convert concatenation to heredoc>>"

        /**
         * @param element
         * @return  true if passed element or any of its ancestors are ConcatenationExpression
         * false otherwise
         */
        private fun isElementOrAncestorAConcatenation(element: PsiElement?): Boolean {
            return if (element == null) false else element is ConcatenationExpression || PsiTreeUtil.getParentOfType(
                element,
                ConcatenationExpression::class.java
            ) != null
        }

        /**
         *
         * Expressions which will return `true` are, for example:
         * echo $someVar, '<tag>', fnReturnString(), "</tag>";
         * @param element
         * @return  true if passed element is located within an "echo" expression with commas
         * false otherwise
         */
        private fun isElementOrAncestorAEchoWithCommas(element: PsiElement?): Boolean {
            if (element == null) return false
            val parentEchoStatement = PsiTreeUtil.getParentOfType(
                element,
                PhpEchoStatementImpl::class.java
            ) ?: return false


            // TODO: replace this with Visitor for better performance?
            // if any direct child of the Echo statement is a comma, we are inside an echo with commas
            return parentEchoStatement.node.getChildren(TokenSet.create(PhpTokenTypes.opCOMMA)).size > 0
        }


        @Throws(UnexpectedException::class)
        private fun useVisitor(
            topConcatenation: ConcatenationExpression
        ): Pair<List<String>, String> {
            val declarations = ArrayList<String>()
            val heredocContent = StringBuilder()

            var i = 0;
            topConcatenation.accept(object : PhpDepthLimitedRecursiveElementVisitor() {
                override fun visitPhpElement(element: PhpPsiElement) {
                    super.visitPhpElement(element)
                    i++

                    if (element !is ConcatenationExpression) {
                        return;
                    }

                    arrayOf(element.leftOperand, element.rightOperand).forEach { concatenationOperand ->
                        when (concatenationOperand) {
                            is ConcatenationExpression -> Unit // do nothing, we will visit the children afterward

                            is StringLiteralExpression -> {
                                val contentToAppend: String =
                                    if (concatenationOperand.isSingleQuote)
                                        // this intention takes already care of all escapings of newlines, dollars, etc...
                                        PhpReplaceQuotesIntention.createLiteralWithChangedQuotes(concatenationOperand).contents
                                    else
                                        // string ready to be used as-is, but use actual newlines
                                        concatenationOperand.contents.replace("\\n", "\n")

                                heredocContent.append(contentToAppend)
                            }


                            is Variable -> {
                                // always wrap in curly braces because if some letter follows the variable name in heredoc,
                                // the wrong variable name will be matched (e.g. $juices vs {$juice}s)
                                extractNameOfPhpVar(concatenationOperand).let{
                                    // pre.append(bla)
                                    heredocContent.append(formatVariableForHeredoc(it))
                                }
                            }

                            is FieldReference, is ArrayAccessExpression -> {
                                (concatenationOperand.text).let {
                                    heredocContent.append(formatVariableForHeredoc(it))
                                }
                            }

                            is AssignmentExpression -> {
                                // add semicolon to the assignment expression, because it is missing within a concatenation expression
                                declarations.add("${concatenationOperand.text};")

                                // append variable name
                                heredocContent.append(formatVariableForHeredoc(extractNameOfPhpVar(
                                    concatenationOperand.variable as Variable
                                )))
                            }

                            is FunctionReference -> {
                                val newVarName = "\$newVarFnCall$i"

                                // add the whole assignment line before the heredoc
                                newVarName.let {
                                    declarations.add("$newVarName=${concatenationOperand.text};")
                                    heredocContent.append(formatVariableForHeredoc(newVarName))
                                }
                            }

                            is PhpExpression -> {
                                // TODO use text to create var name?
                                val newVarName = "\$newVarPhpExpression$i"
                                newVarName.let {
                                    declarations.add("$newVarName=${concatenationOperand.text};")
                                    heredocContent.append(formatVariableForHeredoc(newVarName))
                                }
                            }
                            else -> {
                                heredocContent.append("I_SHOULD_NOT_APPEAR")
                            }
                        }
                    }

                }
            })
            // TODO : how to check that variables do not exist in scope: important to avoid confusion in same file
            // TODO : how to generate variable name based on content and scope (intellij offers that?)
            // TODO : do we need the "Marks"?
            // TODO : reformat CONTENT of heredoc based on content type (e.g. format HTML decently if possible)

            return Pair(declarations, heredocContent.toString())
        }

        fun extractNameOfPhpVar(variable: Variable): String {
            return variable.nameIdentifier!!.text
        }

        fun formatVariableForHeredoc(variableNameWithDollar: String): String {
            return String.format("{%s}", variableNameWithDollar)
        }
    }
}
