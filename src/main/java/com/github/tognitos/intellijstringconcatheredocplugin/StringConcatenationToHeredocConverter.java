// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.tognitos.intellijstringconcatheredocplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.php.codeInsight.PhpScopeHolder;
import com.jetbrains.php.lang.inspections.PhpScopeHolderVisitor;
import com.jetbrains.php.lang.intentions.strings.PhpConvertConcatenationToSprintfIntention;
import com.jetbrains.php.lang.lexer.PhpTokenTypes;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.impl.PhpEchoStatementImpl;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.php.lang.psi.elements.*;

import java.rmi.UnexpectedException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class TupleHeredocSprintf {
    public String heredoc;
    public FunctionReference sprintf;

    public TupleHeredocSprintf(String heredoc, FunctionReference sprintf) {
        this.heredoc = heredoc;
        this.sprintf = sprintf;
    }
}
/**
 * Implements an intention action to replace string concatenations with HEREDOC strings
 */
@NonNls
public class StringConcatenationToHeredocConverter extends PsiElementBaseIntentionAction implements IntentionAction {

    public static final String INTENTION_HINT = "<<cc Convert concatenation to heredoc>>";

    /**
     * If this action is applicable, returns the text to be shown in the list of intention actions available.
     */
    @NotNull
    public String getText() {
        return INTENTION_HINT;
    }

    /**
     * Returns text for name of this family of intentions.
     * It is used to externalize "auto-show" state of intentions.
     * It is also the directory name for the descriptions.
     *
     * @return the intention family name.
     */
    @NotNull
    public String getFamilyName() {
        return "StringManipulationIntention";
    }

    /**
     * Checks whether this intention is available at the caret offset in file - the caret must sit:
     * - at any point inside a concatenation expression
     * - at any point inside an "echo" expression with commas
     *
     * <p>Note: this method must do its checks quickly and return.</p>
     *
     * @param project a reference to the Project object being edited.
     * @param editor  a reference to the object editing the project source
     * @param element a reference to the PSI element currently under the caret
     * @return {@code true} if the caret is in an expression which can be converted by this plugin
     *  {@code false} for all other types of caret positions
     */
    public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
        // Quick sanity check
        if (element == null) {
            return false;
        }

        // TODO implement for echo calls with a lot of `,` , only if any of the echo statements contain HTML or any expression
        boolean isConcat = isElementOrAncestorAConcatenation(element);
        boolean isEchoWithCommas = isElementOrAncestorAEchoWithCommas(element);
        boolean available = isConcat || isEchoWithCommas;
        System.out.println(element.toString() + " parent: " + element.getParent().toString());
        System.out.println("Is available " + available + " (" + "isConcat=" + isConcat + ";isEchoWithCommas=" + isEchoWithCommas + ")");

        return available;
    }

    /**
     * @param element
     * @return  true if passed element or any of its ancestors are ConcatenationExpression
     *          false otherwise
     */
    private static boolean isElementOrAncestorAConcatenation(PsiElement element) {
        if (element == null) return false;

        return element instanceof ConcatenationExpression
                || (PsiTreeUtil.getParentOfType(element, ConcatenationExpression.class) != null);
    }

    /**
     *
     * Expressions which will return {@code true} are, for example:
     * echo $someVar, '<tag>', fnReturnString(), "</tag>";
     * @param element
     * @return  true if passed element is located within an "echo" expression with commas
     *          false otherwise
     */
    private static boolean isElementOrAncestorAEchoWithCommas(PsiElement element) {
        if (element == null) return false;

        PhpEchoStatementImpl parentEchoStatement = PsiTreeUtil.getParentOfType(element, PhpEchoStatementImpl.class);

        // true = not within Echo
        if (parentEchoStatement == null) return false;

//        PhpElementVisitor commaVisitor = new PhpElementVisitor() {
//            @Override
//            public void visitPhpElement(PhpPsiElement element) {
//                super.visitPhpElement(element);
//                // if found then throw StopVisitingException
//            }
//        };

        // TODO: replace this with Visitor for better performance?
        // if any direct child of the Echo statement is a comma, we are inside an echo with commas
        return parentEchoStatement.getNode().getChildren(TokenSet.create(PhpTokenTypes.opCOMMA)).length > 0;
    }

    public static void nl() {
        System.out.println("\n");
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
     *                                     when manipulation of the psi tree fails.
     * @see StringConcatenationToHeredocConverter#startInWriteAction()
     */
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        // the "top" of the concatenation expression (if we have multiple concats, then there is a tree of Concatenations
        ConcatenationExpression topConcatenation = PsiTreeUtil.getTopmostParentOfType(element, ConcatenationExpression.class);

        // basically the line where this Concatenation is present (e.g. `return` statement, assignment, fn call...)
        Statement parentStatement = PsiTreeUtil.getParentOfType(element, Statement.class);
        // will be used to know where to declare variables for fn calls

        PhpConvertConcatenationToSprintfIntention concatToSprintf = new PhpConvertConcatenationToSprintfIntention();
        TupleHeredocSprintf tuple = null;

        if (concatToSprintf.isAvailable(project, editor, element)) {
            try {
                tuple = useSprintf(
                        project, editor, element,
                        parentStatement, topConcatenation, concatToSprintf);
            } catch (UnexpectedException e) {
                System.out.println("Could not generate hereddoc content: " + project + editor + element + parentStatement + topConcatenation + concatToSprintf);
                e.printStackTrace();
            }
        } else {
            nl();
            System.out.print("------------------------------");
            System.out.println("Sprintf not available, therefore not doing anything: what's the point if concatenation does not contain any expression?");
            System.out.print("------------------------------");
            nl();
            return;
        }


        /*
         * Structure of heredoc as a StringLiteralExpression:
         *  - heredoc start (e.g. <<<DELIMITER)
         *  - heredoc content (e.g. <div>Hi $username</div>)
         *  - heredoc end (e.g. DELIMITER;)
         */

        // TODO : let user pick delimiter, or choose based on interpreted content (e.g. HTML or JS or SQL)
        String heredocDelimiter = "HEREDOC_DELIMITER";
        StringLiteralExpression heredoc = PhpPsiElementFactory.createPhpPsiFromText(
                project,
                StringLiteralExpression.class,
                "<<<" + heredocDelimiter + "\n" + tuple.heredoc + "\n" + heredocDelimiter + ";"
        );

        tuple.sprintf.replace(heredoc);
    }

    public static Statement findParentStatement(PsiElement psiElement) {
        if (psiElement == null) {
            System.out.println("psiElement null, will not find parent statement");
            return null;
        }

        return PsiTreeUtil.getParentOfType(
                psiElement,
                Statement.class
        );
    }
    public static FunctionReference findChildSprintf(Statement statement) {
        Collection<FunctionReference> functionChildren = PsiTreeUtil.findChildrenOfType(statement, FunctionReference.class);
        for (FunctionReference functionReference : functionChildren) {
            if (Objects.equals(functionReference.getName(), "sprintf")) {
                return functionReference;
            }
        }

        System.out.println("Could not find child of type sprintf, something must have gone wrong");
        return null;
    }

    public static TupleHeredocSprintf useSprintf(
            @NotNull Project project, Editor editor, @NotNull PsiElement element,
            Statement parentStatement, ConcatenationExpression topConcatenation, PhpConvertConcatenationToSprintfIntention intention) throws UnexpectedException {
        if (!PhpConvertConcatenationToSprintfIntention.canModify(parentStatement)) {
            System.out.println("cannot modify parentStatement " + parentStatement);
            System.out.println("cannot modify topConcatenation? " + PhpConvertConcatenationToSprintfIntention.canModify(topConcatenation));
        }

        // take note of what statement the concatenation is in
        Statement statementBefore = findParentStatement(topConcatenation);
        System.out.println("Statement before" + statementBefore);

        // intention won't change the statementBefore, allowing us to find the generated sprintf
        intention.startInWriteAction();
        intention.invoke(project, editor, element);

        // find sprintf after intention invocation, as a child of the statement from before
        FunctionReference sprintfCall = findChildSprintf(statementBefore);

        if (sprintfCall == null) {
            System.out.println("Function is null?");
            throw new UnexpectedException("Containing function is null");
        }

        PsiElement[] params = sprintfCall.getParameters();
        String stringToInterpolate = params[0].getText();

        // remove quotes around string
        String quote = String.valueOf(stringToInterpolate.charAt(0));
        if (!quote.equals("\"") && !quote.equals("'")) {
            // sanity check
            throw new UnexpectedException("Quote is not a quote: " + quote);
        }
        if (!stringToInterpolate.startsWith(quote) && !stringToInterpolate.endsWith(quote)) {
            // sanity check
            throw new UnexpectedException("String is malformed: " + stringToInterpolate);
        }
        // remove quotes from string, because it will become a HEREDOC string
        stringToInterpolate = stringToInterpolate.substring(1, stringToInterpolate.length() - 1);

        // remove escaped characters if they equal the opening and closing quote (either ' or ")
        stringToInterpolate = stringToInterpolate.replaceAll("\\\\" + quote, quote);
        System.out.println("cleaned stringToInterpolate: " + stringToInterpolate);
        nl();

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
        String regexpMatchSpecifiers = "(?<!%)%[sdf]";
        Matcher m = Pattern.compile(regexpMatchSpecifiers).matcher(stringToInterpolate);

        // ironically this format string will be used for us to insert our own placeholders in a String.format call
        String heredocContentWithPlaceholders = m.replaceAll("%s");
        System.out.println("heredocContentWithPlaceholders\n" + heredocContentWithPlaceholders);
        nl();

        String[] placeholdersValues = new String[params.length-1];
        System.out.println("sprinft context " + sprintfCall.getContext());

        // TODO : how to check that variables do not exist in scope: important to avoid confusion in same file
        // TODO : how to generate variable name based on content and scope (intellij offers that?)
        // TODO : do we need the "Marks"?
        Map<String, Boolean> createdVariables = new HashMap<>();
        for(int i = 1; i < params.length; i++) {
            PsiElement param = params[i];

            System.out.println("param at pos " + i + " is : \t " + param);
            String toAppend;
            if (param instanceof StringLiteralExpression) {
                toAppend = param.getText();
            } else if (param instanceof Variable) {
                Variable variable = (Variable) param;
                System.out.println("name identifier of variable " + (variable.getNameIdentifier().getText()));
                System.out.println("name of variable second try: " + (variable.getNameNode().getText()));

                // always wrap in curly braces because if some letter follows the variable name in heredoc,
                // the wrong variable name will be matched (e.g. $juices vs {$juice}s)
                String newVarName = extractNameOfPhpVar(variable);
                toAppend = formatVariableForHeredoc(newVarName);
            } else if (param instanceof FieldReference || param instanceof ArrayAccessExpression) {
                toAppend = formatVariableForHeredoc(param.getText());
            } else if(param instanceof AssignmentExpression) {
                AssignmentExpression assignmentExpression = (AssignmentExpression) param;
                // add semi-colon to the assignment expression, since it did not have it in the string concatenation
                Statement newAssignmentStatement = PhpPsiElementFactory.createStatement(project, assignmentExpression.getText() + ";");

                // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                Statement newTopStatement = statementBefore; // TODO: delete comment : // getNewTopStatement();
                newTopStatement.getParent().addBefore(newAssignmentStatement, newTopStatement);

                // append variable name
                Variable variable = (Variable) assignmentExpression.getVariable();
                String newVarName = extractNameOfPhpVar(variable);
                toAppend = formatVariableForHeredoc(newVarName);
            } else if (param instanceof FunctionReference) {
                FunctionReference functionReference = (FunctionReference) param;
                // if it's function call
                // add marker
                // create variable before statement (parent of sprintfCall, can be a return or assignment or even a fn call)
                // assign new variable = the function call
                // append name of variable to sb
                String newVarName = "$newVarFnCall" + i;
                Statement newFnCallAssignmentStatement = PhpPsiElementFactory.createStatement(project,
                        newVarName + "=" + functionReference.getText() + ";"
                );

                // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                Statement newTopStatement = statementBefore; // TODO: delete comment : // getNewTopStatement();
                newTopStatement.getParent().addBefore(newFnCallAssignmentStatement, newTopStatement);

                toAppend = formatVariableForHeredoc(newVarName);
            } else if(param instanceof PhpExpression) {
                /**
                 * This is a generic fallback to create variables whenever I have not considered whether a certain
                 * expression type can be contained inside a heredoc string or not.
                 *
                 * until now it was proven that it works well for:
                 * - ParenthesizedExpression
                 * - TernaryExpression
                 * - ConstantReference (because constants can't be added to HEREDOC)
                 *      - this includes booleans, because a boolean as a string is `1` and not `true`
                 *
                 * Extracting numeric values (int, float) to variables could be argued:
                 * - in favour: adds an explicit meaning to the number through the variable name
                 * - against: creates more variables for a fixed value whose interpretation will never change
                 */

                PhpExpression phpExpression = (PhpExpression) param;
                // probably do the same as for FunctionReference, just create a var

                // TODO use text to create var name?
                String newVarName = "$newVarPhpExpression" + i;

                Statement newFnCallAssignmentStatement = PhpPsiElementFactory.createStatement(project,
                        newVarName + "=" + phpExpression.getText() + ";"
                );

                // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                statementBefore.getParent().addBefore(newFnCallAssignmentStatement, statementBefore);

                toAppend = formatVariableForHeredoc(newVarName);
            } else {
                toAppend = "I_SHOULD_NOT_APPEAR";
            }
            placeholdersValues[i-1] = toAppend;
        }

        printScopesAndVariables(statementBefore);
        // TODO : reformat CONTENT of heredoc based on content type (e.g. format HTML decently if possible)

        String heredocContent = String.format(heredocContentWithPlaceholders, (Object[]) placeholdersValues);
        System.out.println("Final version of `heredocContentWithPlaceholders` " + heredocContentWithPlaceholders);
        return new TupleHeredocSprintf(heredocContent, sprintfCall);
    }

    public static void printScopesAndVariables(Statement target){
//        Statement target = getNewTopStatement();
        System.out.println("visiting stuff");
        target.acceptChildren(new PhpScopeHolderVisitor() {
            @Override
            protected void check(@NotNull PhpScopeHolder phpScopeHolder) {
                System.out.println("phpScopeHolder\n");
                System.out.println(phpScopeHolder);
            }
        });

        System.out.println(target.getContext());
        System.out.println(target.getUseScope());
        System.out.println("stopped visiting");
        nl();

    }

    public static String extractNameOfPhpVar(@NotNull Variable variable) {
        return variable.getNameIdentifier().getText();
    }

    public static String formatVariableForHeredoc(@NotNull String variableNameWithDollar) {
        return String.format("{%s}", variableNameWithDollar);
    }

    public static String useVisitor(Statement parentStatement, ConcatenationExpression topConcatenation) {
        // TODO use PsiRecursiveElementWalkingVisitor to visit all concat parts

        topConcatenation.acceptChildren(new PhpElementVisitor() {
            @Override
            public void visitPhpElement(PhpPsiElement element) {
                super.visitPhpElement(element);
                System.out.println("[PHP EL]visited this element");
                System.out.println(element);
            }
        });

        return "visitor used content";
    }

    /**
     * Indicates this intention action expects the Psi framework to provide the write action context for any changes.
     *
     * @return {@code true} if the intention requires a write action context to be provided or {@code false} if this
     * intention action will start a write action
     */
    public boolean startInWriteAction() {
        return true;
    }

}
