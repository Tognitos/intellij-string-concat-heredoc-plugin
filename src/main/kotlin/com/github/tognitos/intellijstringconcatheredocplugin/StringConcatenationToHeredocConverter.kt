// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.github.tognitos.intellijstringconcatheredocplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import com.jetbrains.php.codeInsight.PhpScopeHolder
import com.jetbrains.php.lang.inspections.PhpScopeHolderVisitor
import com.jetbrains.php.lang.intentions.strings.PhpConvertConcatenationToSprintfIntention
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.*
import com.jetbrains.php.lang.psi.elements.impl.PhpEchoStatementImpl
import com.jetbrains.php.lang.psi.stubs.indexes.PhpDepthLimitedRecursiveElementVisitor
import java.rmi.UnexpectedException
import java.util.regex.Pattern

internal class TupleHeredocSprintf(var heredoc: String, var sprintf: FunctionReference)

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
        // Quick sanity check
        if (element == null) {
            return false
        }

        // TODO: add plugin settings to display intention with minimum amount of concatenation expressions and/or echo commas

        // TODO implement for echo calls with a lot of `,` , only if any of the echo statements contain HTML or any expression
        val isConcat = isElementOrAncestorAConcatenation(element)
        val isEchoWithCommas = isElementOrAncestorAEchoWithCommas(element)
        val available = isConcat || isEchoWithCommas
        println(element.toString() + " parent: " + element.parent.toString())
        println("Is available $available (isConcat=$isConcat;isEchoWithCommas=$isEchoWithCommas)")
        return available
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

        // will be used to know where to declare variables for fn calls
        val concatToSprintf = PhpConvertConcatenationToSprintfIntention()
        if (!concatToSprintf.isAvailable(project, editor, element)) {
            // TODO : remove this message and maybe the whole condition at all
            nl()
            print("------------------------------")
            println("Sprintf not available, therefore not doing anything: what's the point if concatenation does not contain any expression?")
            print("------------------------------")
            nl()
            return

        }

        val (pre, heredocContent) = runCatching {
            useVisitor(project, topConcatenation)
        }.onFailure { e ->
            println("Could not generate hereddoc content: $topConcatenation")
            e.printStackTrace()
            return
        }.getOrThrow()


        /*
         * Structure of heredoc as a StringLiteralExpression:
         *  - heredoc start (e.g. <<<DELIMITER)
         *  - heredoc content (e.g. <div>Hi $username</div>)
         *  - heredoc end (e.g. DELIMITER;)
         */

        // TODO : let user pick delimiter, or choose based on interpreted content (e.g. HTML or JS or SQL)
        val heredocDelimiter = "HEREDOC_DELIMITER"
        val result = PhpPsiElementFactory.createPhpPsiFromText(
            project,
            StringLiteralExpression::class.java,
            """
            $pre
            <<<$heredocDelimiter
            $heredocContent
            $heredocDelimiter;
            """.trimIndent()
        )
        topConcatenation.replace(result)
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

            // true = not within Echo

//        PhpElementVisitor commaVisitor = new PhpElementVisitor() {
//            @Override
//            public void visitPhpElement(PhpPsiElement element) {
//                super.visitPhpElement(element);
//                // if found then throw StopVisitingException
//            }
//        };

            // TODO: replace this with Visitor for better performance?
            // if any direct child of the Echo statement is a comma, we are inside an echo with commas
            return parentEchoStatement.node.getChildren(TokenSet.create(PhpTokenTypes.opCOMMA)).size > 0
        }

        fun nl() {
            println("\n")
        }

        fun findParentStatement(psiElement: PsiElement?): Statement? {
            if (psiElement == null) {
                println("psiElement null, will not find parent statement")
                return null
            }
            return PsiTreeUtil.getParentOfType(
                psiElement,
                Statement::class.java
            )
        }

        fun findChildSprintf(statement: Statement?): FunctionReference? {
            val functionChildren = PsiTreeUtil.findChildrenOfType(
                statement,
                FunctionReference::class.java
            )
            for (functionReference in functionChildren) {
                if (functionReference.name == "sprintf") {
                    return functionReference
                }
            }
            println("Could not find child of type sprintf, something must have gone wrong")
            return null
        }


        @Throws(UnexpectedException::class)
        private fun useVisitor(
            project: Project,
            topConcatenation: ConcatenationExpression
        ): Pair<String, String> {
            println("useWalker")
            println("topConcatenation $topConcatenation")
            println("can modify topConcatenation? ${canModify(topConcatenation)}")

            val pre = StringBuilder()
            val heredocContent = StringBuilder()

            var i = 0;
            // TODO use PsiRecursiveElementWalkingVisitor to visit all concat parts
            topConcatenation.acceptChildren(object : PhpDepthLimitedRecursiveElementVisitor() {
                override fun visitPhpElement(element: PhpPsiElement) {
                    super.visitPhpElement(element)
                    i++
                    println("[PHP EL]visited this element")
                    println(element)

                    when (element) {
                        is ConcatenationExpression -> Unit // do nothing, we will visit the children afterward

                        is StringLiteralExpression -> {
                            println("matched stringliteralexpression")
                            heredocContent.append(element.contents)
                        }

                        is Variable -> {
                            println("name identifier of variable " + element.nameIdentifier!!.text)
                            println("name of variable second try: " + element.nameNode!!.text)

                            // always wrap in curly braces because if some letter follows the variable name in heredoc,
                            // the wrong variable name will be matched (e.g. $juices vs {$juice}s)
                            extractNameOfPhpVar(element).let{
                                // pre.append(it)
                                heredocContent.append(formatVariableForHeredoc(it))
                            }
                        }

                        is FieldReference, is ArrayAccessExpression -> {
                            (element.text).let {
                                heredocContent.append(formatVariableForHeredoc(it))
                            }
                        }

                        is AssignmentExpression -> {
                            // add semicolon to the assignment expression, since it did not have it in the string concatenation
                            pre.append("${element.text};")

                            // append variable name
                            heredocContent.append(formatVariableForHeredoc(extractNameOfPhpVar(
                                element.variable as Variable
                            )))
                        }

                        is FunctionReference -> {
                            // if it's function call
                            // create variable before statement
                            // assign new variable = the function call
                            // append name of variable to sb
                            val newVarName = "\$newVarFnCall$i"

                            // add the whole assignment line before the heredoc
                            newVarName.let {
                                pre.append("$newVarName=${element.text};")
                                heredocContent.append(formatVariableForHeredoc(newVarName))
                            }
                        }

                        is PhpExpression -> {
                            // same as for FunctionReference, just create a var

                            // TODO use text to create var name?
                            val newVarName = "\$newVarPhpExpression$i"
                            newVarName.let {
                                pre.append("$newVarName=${element.text};")
                                heredocContent.append(formatVariableForHeredoc(newVarName))
                            }
                        }
                        else -> {
                            heredocContent.append("I_SHOULD_NOT_APPEAR")
                        }
                    }
                }
            })

            // TODO: replace literal "written-out" \n with actual line breaks in the HEREDOC
            /*
            for example
            <<<HEREDOC
            hello\nworld
            HEREDOC;

            would become
            <<<HEREDOC
            hello
            world
            HEREDOC;
         */
            // TODO read above

            // TODO : how to check that variables do not exist in scope: important to avoid confusion in same file
            // TODO : how to generate variable name based on content and scope (intellij offers that?)
            // TODO : do we need the "Marks"?
            // TODO : reformat CONTENT of heredoc based on content type (e.g. format HTML decently if possible)

            return Pair(pre.toString(), heredocContent.toString())
        }

        fun extractNameOfPhpVar(variable: Variable): String {
            return variable.nameIdentifier!!.text
        }

        fun formatVariableForHeredoc(variableNameWithDollar: String): String {
            return String.format("{%s}", variableNameWithDollar)
        }
    }
}
