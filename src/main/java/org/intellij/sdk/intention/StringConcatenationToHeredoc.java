// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.sdk.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.profiler.ultimate.hprof.visitors.ReferenceVisitor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.php.codeInsight.PhpScopeHolder;
import com.jetbrains.php.lang.inspections.PhpScopeHolderVisitor;
import com.jetbrains.php.lang.intentions.strings.PhpConvertConcatenationToSprintfIntention;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import com.thoughtworks.qdox.model.expression.FieldRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.rmi.UnexpectedException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Implements an intention action to replace a ternary statement with if-then-else.
 */
@NonNls
public class StringConcatenationToHeredoc extends PsiElementBaseIntentionAction implements IntentionAction {

    public static class MyTreeChangeListener implements PsiTreeChangeListener {

        public static FunctionReference sprintfCall = null;

        @Override
        public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void childAdded(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void childRemoved(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void childReplaced(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
            PsiElement oldChild = event.getOldChild();
            PsiElement newChild = event.getNewChild();
            System.out.println("old: " + oldChild);
            System.out.println("new: " + newChild);

            if (newChild instanceof FunctionReference && ((FunctionReference) newChild).getName().equals("sprintf")) {
                MyTreeChangeListener.sprintfCall = (FunctionReference) newChild;
                System.out.println("Found sprintfFunction reference!");
            } else if (newChild instanceof PsiElement) {
                System.out.println("new child is instance of psiElement, usually a reference to the whole new document");
//                System.out.println(newChild.getText());
            }
        }

        @Override
        public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void childMoved(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }

        @Override
        public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
            System.out.println(event.toString());
        }
    }

    private static MyTreeChangeListener myTreeChangeListener = new MyTreeChangeListener();

    /**
     * If this action is applicable, returns the text to be shown in the list of intention actions available.
     */
    @NotNull
    public String getText() {
        return "<<cc Convert concatenation to heredoc>>";
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
     * Checks whether this intention is available at the caret offset in file - the caret must sit just before a "?"
     * character in a ternary statement. If this condition is met, this intention's entry is shown in the available
     * intentions list.
     *
     * <p>Note: this method must do its checks quickly and return.</p>
     *
     * @param project a reference to the Project object being edited.
     * @param editor  a reference to the object editing the project source
     * @param element a reference to the PSI element currently under the caret
     * @return {@code true} if the caret is in a literal string element, so this functionality should be added to the
     * intention menu or {@code false} for all other types of caret positions
     */
    public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
        // Quick sanity check
        if (element == null) {
            return false;
        }

        // TODO implement for echo calls with a lot of `,` , only any of the echo statements contain HTML or any expression

        boolean available = this.isElementOrAncestorAConcatenation(element);

        System.out.println("Is available ? " + available);

        return available;
    }

    /**
     * @param element
     * @return  true if passed element or any of its ancestors are ConcatenationExpression
     *          false otherwise
     */
    public static boolean isElementOrAncestorAConcatenation(PsiElement element) {
        if (element == null) return false;

        return element instanceof ConcatenationExpression
                || (PsiTreeUtil.getParentOfType(element, ConcatenationExpression.class) != null);
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
     * @see StringConcatenationToHeredoc#startInWriteAction()
     */
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        // Get the factory for making new PsiElements, and the code style manager to format new statements
//        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final CodeStyleManager codeStylist = CodeStyleManager.getInstance(project);

        // the "top" of the concatenation expression (if we have multiple concats, then there is a tree of Concatenations
        ConcatenationExpression topConcatenation = PsiTreeUtil.getTopmostParentOfType(element, ConcatenationExpression.class);

        // basically the line where this Concatenation is present (e.g. `return` statement, assignment, fn call...)
        Statement parentStatement = PsiTreeUtil.getParentOfType(element, Statement.class);
        // will be used to know where to declare variables for fn calls



        System.out.println("instance of class " + parentStatement.getClass().getName());

        PhpConvertConcatenationToSprintfIntention intention = new PhpConvertConcatenationToSprintfIntention();
        String heredocContent = null;

        if (intention.isAvailable(project, editor, element)) {
            System.out.println("Sprintf intention is available!");
            try {
                heredocContent = StringConcatenationToHeredoc.useSprintf(
                        project, editor, element,
                        parentStatement, topConcatenation, intention);
            } catch (UnexpectedException e) {
                e.printStackTrace();
            }
        } else {
            nl();
            nl();
            System.out.print("------------------------------");
            System.out.println("Sprintf not available, therefore doing manually with Visitor");
            System.out.println("Not implemented yet, maybe not even needed (what's the point if concatenation does not" +
                    "contain any expression?");
            System.out.print("------------------------------");
            nl();
            nl();
//            heredocContent = this.useVisitor(parentStatement, topConcatenation);
            return;
        }


        /**
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
                "<<<" + heredocDelimiter + "\n" + heredocContent + "\n" + heredocDelimiter + ";"
        );
//        heredoc = (StringLiteralExpression) codeStylist.reformat(heredoc);
//        Statement statement =
//                (Statement) PhpPsiElementFactory.createStatement(project, variable.getText() + " = 0;");
//        statement = (Statement) codeStylist.reformat(statement);
//        heredocExpression = PhpASTFactory.composite()

        // finally
//        topConcatenation.replace(heredoc);
//        Statement newTopStatement = getNewTopStatement();


//        newTopStatement.getParent().addAfter(heredoc, newTopStatement);
        StringConcatenationToHeredoc.myTreeChangeListener.sprintfCall.replace(heredoc);
    }

    public static Statement getNewTopStatement() {
        Statement newTopStatement = PsiTreeUtil.getParentOfType(
                StringConcatenationToHeredoc.myTreeChangeListener.sprintfCall,
                Statement.class
        );
        System.out.println("new top statement " + newTopStatement);
        return newTopStatement;
    }

    public static String useSprintf(
            @NotNull Project project, Editor editor, @NotNull PsiElement element,
            Statement parentStatement, ConcatenationExpression topConcatenation, PhpConvertConcatenationToSprintfIntention intention) throws UnexpectedException {
        if (!PhpConvertConcatenationToSprintfIntention.canModify(parentStatement)) {
            System.out.println("cannot modify parentStatement " + parentStatement);
            System.out.println("cannot modify topConcatenation? " + PhpConvertConcatenationToSprintfIntention.canModify(topConcatenation));
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        psiManager.addPsiTreeChangeListener(StringConcatenationToHeredoc.myTreeChangeListener);

        intention.startInWriteAction();
        intention.invoke(project, editor, element);

        psiManager.removePsiTreeChangeListener(StringConcatenationToHeredoc.myTreeChangeListener);

        FunctionReference sprintfCall = StringConcatenationToHeredoc.myTreeChangeListener.sprintfCall;

        if (!sprintfCall.getName().equals("sprintf")) {
            System.out.println("Function is not sprintf?");
            throw new UnexpectedException("Containing function is not sprintf call");
        }

        PsiElement[] params = sprintfCall.getParameters();
        String stringToInterpolate = params[0].getText();

        // remove quotes around string
        String quote = String.valueOf(stringToInterpolate.charAt(0));
        if (!stringToInterpolate.startsWith(quote) && !stringToInterpolate.endsWith(quote)) {
            // sanity check
            throw new UnexpectedException("String is malformed: " + stringToInterpolate);
        }
        stringToInterpolate = stringToInterpolate.substring(1, stringToInterpolate.length() - 1);

        // remove escaped characters if they equal the opening and closing quote (either ' or ")
        stringToInterpolate = stringToInterpolate.replaceAll("\\\\" + quote, quote);
        System.out.println("cleaned stringToInterpolate: " + stringToInterpolate);
        nl();

        // change all SIMPLE specifiers (%s, %d, %f) to %s

        // match any %d,%s or %f, which is NOT preceded by another % (because that is "escaped")
        String regexpMatchSpecifiers = "(?<!%)%[sdf]";
        Matcher m = Pattern.compile(regexpMatchSpecifiers).matcher(stringToInterpolate);

        // ironically this format string will be used for us to insert our own placeholders in a String.format call
        String heredocContentWithPlaceholders = m.replaceAll("%s");
        System.out.println("heredocContentWithPlaceholders\n" + heredocContentWithPlaceholders);
        nl();

        String[] placeholdersValues = new String[params.length-1];
        System.out.println("sprinft context " + sprintfCall.getContext());

        // TODO : how to check that variables do not exist in scope: important to avoid confusion in same file
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
                String newVarName = StringConcatenationToHeredoc.extractNameOfPhpVar(variable);
                toAppend = StringConcatenationToHeredoc.formatVariableForHeredoc(newVarName);
            } else if (param instanceof FieldReference || param instanceof ArrayAccessExpression) {
                toAppend = StringConcatenationToHeredoc.formatVariableForHeredoc(param.getText());
            } else if(param instanceof AssignmentExpression) {
                AssignmentExpression assignmentExpression = (AssignmentExpression) param;
                // add semi-colon to the assignment expression, since it did not have it in the string concatenation
                Statement newAssignmentStatement = PhpPsiElementFactory.createStatement(project, assignmentExpression.getText() + ";");

                // add the whole assignment line before the statement (parent of sprintfCall) [missing]
                Statement newTopStatement = getNewTopStatement();
                newTopStatement.getParent().addBefore(newAssignmentStatement, newTopStatement);

                // append variable name
                Variable variable = (Variable) assignmentExpression.getVariable();
                String newVarName = StringConcatenationToHeredoc.extractNameOfPhpVar(variable);
                toAppend = StringConcatenationToHeredoc.formatVariableForHeredoc(newVarName);
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
                Statement newTopStatement = getNewTopStatement();
                newTopStatement.getParent().addBefore(newFnCallAssignmentStatement, newTopStatement);

                toAppend = StringConcatenationToHeredoc.formatVariableForHeredoc(newVarName);
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
                Statement newTopStatement = getNewTopStatement();
                newTopStatement.getParent().addBefore(newFnCallAssignmentStatement, newTopStatement);

                toAppend = StringConcatenationToHeredoc.formatVariableForHeredoc(newVarName);
            } else {
                toAppend = "I_SHOULD_NOT_APPEAR";
            }
            placeholdersValues[i-1] = toAppend;
        }

        printScopesAndVariables();
        // TODO : reformat CONTENT of heredoc based on content type (e.g. format HTML decently if possible)

        String heredocContent = String.format(heredocContentWithPlaceholders, placeholdersValues);
        System.out.println("Final version of `heredocContentWithPlaceholders` " + heredocContentWithPlaceholders);
        return heredocContent;
    }

    public static void printScopesAndVariables(){
        Statement target = getNewTopStatement();
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