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
import com.jetbrains.php.codeInsight.PhpScopeHolder
import com.jetbrains.php.lang.inspections.PhpScopeHolderVisitor
import com.jetbrains.php.lang.intentions.strings.PhpConvertConcatenationToSprintfIntention
import com.jetbrains.php.lang.lexer.PhpTokenTypes
import com.jetbrains.php.lang.psi.PhpPsiElementFactory
import com.jetbrains.php.lang.psi.elements.*
import com.jetbrains.php.lang.psi.elements.impl.PhpEchoStatementImpl
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor
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

        // basically the line where this Concatenation is present (e.g. `return` statement, assignment, fn call...)
        val parentStatement = PsiTreeUtil.getParentOfType(
            element,
            Statement::class.java
        )
        // will be used to know where to declare variables for fn calls
        val concatToSprintf = PhpConvertConcatenationToSprintfIntention()
        var tuple: TupleHeredocSprintf? = null
        if (concatToSprintf.isAvailable(project, editor, element)) {
            try {
                tuple = useSprintf(
                    project, editor, element,
                    parentStatement, topConcatenation, concatToSprintf
                )
            } catch (e: UnexpectedException) {
                println("Could not generate hereddoc content: $project$editor$element$parentStatement$topConcatenation$concatToSprintf")
                e.printStackTrace()
            }
        } else {
            nl()
            print("------------------------------")
            println("Sprintf not available, therefore not doing anything: what's the point if concatenation does not contain any expression?")
            print("------------------------------")
            nl()
            return
        }


        /*
         * Structure of heredoc as a StringLiteralExpression:
         *  - heredoc start (e.g. <<<DELIMITER)
         *  - heredoc content (e.g. <div>Hi $username</div>)
         *  - heredoc end (e.g. DELIMITER;)
         */

        // TODO : let user pick delimiter, or choose based on interpreted content (e.g. HTML or JS or SQL)
        val heredocDelimiter = "HEREDOC_DELIMITER"
        val heredoc = PhpPsiElementFactory.createPhpPsiFromText(
            project,
            StringLiteralExpression::class.java,
            """
                 <<<$heredocDelimiter
                 ${tuple!!.heredoc}
                 $heredocDelimiter;
                 """.trimIndent()
        )
        tuple.sprintf.replace(heredoc)
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
        private fun useSprintf(
            project: Project,
            editor: Editor?,
            element: PsiElement,
            parentStatement: Statement?,
            topConcatenation: ConcatenationExpression?,
            intention: PhpConvertConcatenationToSprintfIntention
        ): TupleHeredocSprintf {
            if (!canModify(parentStatement)) {
                println("cannot modify parentStatement $parentStatement")
                println("cannot modify topConcatenation? " + canModify(topConcatenation))
            }

            // take note of what statement the concatenation is in
            val statementBefore = findParentStatement(topConcatenation)
            println("Statement before$statementBefore")

            // intention won't change the statementBefore, allowing us to find the generated sprintf
            intention.startInWriteAction()
            intention.invoke(project, editor!!, element)

            // find sprintf after intention invocation, as a child of the statement from before
            val sprintfCall = findChildSprintf(statementBefore)
            if (sprintfCall == null) {
                println("Function is null?")
                throw UnexpectedException("Containing function is null")
            }
            val params = sprintfCall.parameters
            var stringToInterpolate = params[0].text

            // remove quotes around string
            val quote = stringToInterpolate[0].toString()
            if (quote != "\"" && quote != "'") {
                // sanity check
                throw UnexpectedException("Quote is not a quote: $quote")
            }
            if (!stringToInterpolate.startsWith(quote) && !stringToInterpolate.endsWith(quote)) {
                // sanity check
                throw UnexpectedException("String is malformed: $stringToInterpolate")
            }
            // remove quotes from string, because it will become a HEREDOC string
            stringToInterpolate = stringToInterpolate.substring(1, stringToInterpolate.length - 1)

            // remove escaped characters if they equal the opening and closing quote (either ' or ")
            stringToInterpolate = stringToInterpolate.replace(("\\\\" + quote).toRegex(), quote)
            println("cleaned stringToInterpolate: $stringToInterpolate")
            nl()

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


            // change all SIMPLE specifiers (%s, %d, %f) to %s

            // match any %d,%s or %f, which is NOT preceded by another % (because double percentage-sign means "escaped")
            val regexpMatchSpecifiers = "(?<!%)%[sdf]"
            val m = Pattern.compile(regexpMatchSpecifiers).matcher(stringToInterpolate)

            // ironically this format string will be used for us to insert our own placeholders in a String.format call
            val heredocContentWithPlaceholders = m.replaceAll("%s")
            println("heredocContentWithPlaceholders\n$heredocContentWithPlaceholders")
            nl()
            val placeholdersValues = arrayOfNulls<String>(params.size - 1)
            println("sprinft context " + sprintfCall.context)

            // TODO : how to check that variables do not exist in scope: important to avoid confusion in same file
            // TODO : how to generate variable name based on content and scope (intellij offers that?)
            // TODO : do we need the "Marks"?
            val createdVariables: Map<String, Boolean> = HashMap()
            for (i in 1 until params.size) {
                val param = params[i]
                println("param at pos $i is : \t $param")
                var toAppend: String?
                toAppend = if (param is StringLiteralExpression) {
                    param.getText()
                } else if (param is Variable) {
                    val variable = param
                    println("name identifier of variable " + variable.nameIdentifier!!.text)
                    println("name of variable second try: " + variable.nameNode!!.text)

                    // always wrap in curly braces because if some letter follows the variable name in heredoc,
                    // the wrong variable name will be matched (e.g. $juices vs {$juice}s)
                    val newVarName = extractNameOfPhpVar(variable)
                    formatVariableForHeredoc(newVarName)
                } else if (param is FieldReference || param is ArrayAccessExpression) {
                    formatVariableForHeredoc(param.text)
                } else if (param is AssignmentExpression) {
                    val assignmentExpression = param
                    // add semi-colon to the assignment expression, since it did not have it in the string concatenation
                    val newAssignmentStatement =
                        PhpPsiElementFactory.createStatement(project, assignmentExpression.text + ";")

                    // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                    statementBefore!!.parent.addBefore(newAssignmentStatement, statementBefore)

                    // append variable name
                    val variable = assignmentExpression.variable as Variable?
                    val newVarName = extractNameOfPhpVar(
                        variable!!
                    )
                    formatVariableForHeredoc(newVarName)
                } else if (param is FunctionReference) {
                    // if it's function call
                    // add marker
                    // create variable before statement (parent of sprintfCall, can be a return or assignment or even a fn call)
                    // assign new variable = the function call
                    // append name of variable to sb
                    val newVarName = "\$newVarFnCall$i"
                    val newFnCallAssignmentStatement = PhpPsiElementFactory.createStatement(
                        project,
                        newVarName + "=" + param.text + ";"
                    )

                    // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                    statementBefore!!.parent.addBefore(newFnCallAssignmentStatement, statementBefore)
                    formatVariableForHeredoc(newVarName)
                } else if (param is PhpExpression) {
                    // probably do the same as for FunctionReference, just create a var

                    // TODO use text to create var name?
                    val newVarName = "\$newVarPhpExpression$i"
                    val newFnCallAssignmentStatement = PhpPsiElementFactory.createStatement(
                        project,
                        newVarName + "=" + param.text + ";"
                    )

                    // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                    statementBefore!!.parent.addBefore(newFnCallAssignmentStatement, statementBefore)
                    formatVariableForHeredoc(newVarName)
                } else {
                    "I_SHOULD_NOT_APPEAR"
                }
                placeholdersValues[i - 1] = toAppend
            }
            printScopesAndVariables(statementBefore)
            // TODO : reformat CONTENT of heredoc based on content type (e.g. format HTML decently if possible)
            val heredocContent = String.format(heredocContentWithPlaceholders, *placeholdersValues as Array<Any?>)
            println("Final version of `heredocContentWithPlaceholders` $heredocContentWithPlaceholders")
            return TupleHeredocSprintf(heredocContent, sprintfCall)
        }

        fun printScopesAndVariables(target: Statement?) {
//        Statement target = getNewTopStatement();
            println("visiting stuff")
            target!!.acceptChildren(object : PhpScopeHolderVisitor() {
                override fun check(phpScopeHolder: PhpScopeHolder) {
                    println("phpScopeHolder\n")
                    println(phpScopeHolder)
                }
            })
            println(target.context)
            println(target.useScope)
            println("stopped visiting")
            nl()
        }

        fun extractNameOfPhpVar(variable: Variable): String {
            return variable.nameIdentifier!!.text
        }

        fun formatVariableForHeredoc(variableNameWithDollar: String): String {
            return String.format("{%s}", variableNameWithDollar)
        }

        fun useVisitor(parentStatement: Statement?, topConcatenation: ConcatenationExpression): String {
            // TODO use PsiRecursiveElementWalkingVisitor to visit all concat parts
            topConcatenation.acceptChildren(object : PhpElementVisitor() {
                override fun visitPhpElement(element: PhpPsiElement) {
                    super.visitPhpElement(element)
                    println("[PHP EL]visited this element")
                    println(element)
                }
            })
            return "visitor used content"
        }
    }
}
