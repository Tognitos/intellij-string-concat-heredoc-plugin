// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.tognitos.intellijstringconcatheredocplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.php.lang.psi.elements.TernaryExpression;
import com.jetbrains.php.lang.psi.elements.*;


/**
 * Implements an intention action to replace a ternary statement with if-then-else.
 */
@NonNls
public class ConditionalOperatorConverter extends PsiElementBaseIntentionAction implements IntentionAction {

    /**
     * If this action is applicable, returns the text to be shown in the list of intention actions available.
     */
    @NotNull
    public String getText() {
        return "<<Intention short text comes here>>";
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
        return "ConditionalOperatorIntention";
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

        // Is this a token of type representing a "?" character?
//        System.out.println("Token is");
//        System.out.println("To string: " + element.toString() + "\n");
//        System.out.println("Text " + element.getText() + "\n");
//        if (!element.textMatches("?")) {
//            System.out.println("Rejected (isAvailable) because token is not `?`");
//            return false;
//        }
//        // Is this token part of a fully formed conditional, i.e. a ternary?
//        if (element.getParent() instanceof TernaryExpression) {
//            final TernaryExpression conditionalExpression = (TernaryExpression) element.getParent();
//
//            System.out.println("True variant : " + conditionalExpression.getTrueVariant() + "\n");
//            System.out.println("False variant : " + conditionalExpression.getFalseVariant() + "\n");
//
//            // Satisfies all criteria; call back invoke method
//            return conditionalExpression.getTrueVariant() != null && conditionalExpression.getFalseVariant() != null;
//        }
        return false;
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
     * @see ConditionalOperatorConverter#startInWriteAction()
     */
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
            throws IncorrectOperationException {
        // Get the factory for making new PsiElements, and the code style manager to format new statements
//        final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        final CodeStyleManager codeStylist = CodeStyleManager.getInstance(project);

        // Get the parent of the "?" element in the ternary statement to find the conditional expression that contains it
        TernaryExpression conditionalExpression =
                PsiTreeUtil.getParentOfType(element, TernaryExpression.class, false);
        // Verify the conditional expression exists and has two outcomes in the ternary statement.
        if (conditionalExpression == null) {
            return;
        }
        // TODO : shouldn't this be removed because it is checked in `isAvailable`?
        if (conditionalExpression.getTrueVariant() == null || conditionalExpression.getFalseVariant() == null) {
            return;
        }

        // Keep searching up the Psi Tree in case the ternary is part of a FOR statement.
        PsiElement originalStatement = PsiTreeUtil.getParentOfType(conditionalExpression, PhpExpression.class, false);
        while (originalStatement instanceof PhpLoop) {
            originalStatement = PsiTreeUtil.getParentOfType(originalStatement, PhpExpression.class, true);
        }

        System.out.println("1) original statement is: " + originalStatement);

        if (originalStatement == null) {
            return;
        }

        int i = 2;

        PsiElement tmp = originalStatement;
        while ((tmp = (PsiElement) tmp.getParent()) != null) {
            System.out.println("parent " + i + " is " + tmp + "\n\n");
            i++;
        }

        // If the original statement is a declaration based on a ternary operator,
        // split the declaration and assignment
        if (originalStatement.getParent() instanceof AssignmentExpression) {
            System.out.println("entered if");
            final AssignmentExpression declaration = (AssignmentExpression) originalStatement.getParent();

            // Find the local variable within the declaration statement
            Variable variable = (Variable) declaration.getVariable();
            if (variable == null) {
                return;
            }

            // Ensure that the variable declaration is not combined with other declarations, and add a mark
            final Object marker = new Object();
            PsiTreeUtil.mark(conditionalExpression, marker);

            System.out.println("next sibling");
            System.out.println(variable.getNextPsiSibling());
            System.out.println(variable.getNextPsiSibling().getNextPsiSibling());
            System.out.println(variable.getText());
            System.out.println(variable.getFirstChild()); // does nothing basically
            System.out.println(variable.getFirstPsiChild());
            System.out.println("next sibling DONE");
            // Create a new expression to declare the local variable
            Statement statement =
                    (Statement) PhpPsiElementFactory.createStatement(project, variable.getText() + " = 0;");
            statement = (Statement) codeStylist.reformat(statement);

            System.out.println("expression should be whole line " + statement.getText());

            System.out.println("How is the assignment");
            System.out.println(declaration.getNextSibling());
            System.out.println(declaration.getNextPsiSibling());
            System.out.println(declaration.getValue()); // getValue == getVariable.getNextPsiSibling --> see above

            // TODO: verify if the next few steps inside this block are correct
            // Replace initializer with the ternary expression, making an assignment statement using the ternary
            // first child of a statement is actually the assignment, and the second child is the `;`
            ((AssignmentExpression) statement.getFirstChild()).getValue().replace(variable.getNextPsiSibling());

            // Remove the initializer portion of the local variable statement,
            // making it a declaration statement with no initializer
            variable.getNextPsiSibling().delete();

            // Get the grandparent of the local var declaration, and add the new declaration just beneath it
            final PsiElement variableParent = variable.getParent();
            originalStatement = variableParent.getParent().addAfter(statement, variableParent);
            conditionalExpression = (TernaryExpression) PsiTreeUtil.releaseMark(originalStatement, marker);

            System.out.println("conditional expression is then: " + conditionalExpression);
        }

        // Create an IF statement from a string with placeholder elements.
        // This will replace the ternary statement
        If newIfStmt = (If) PhpPsiElementFactory.createStatement(project, "if (true) {a=b;} else {c=d;}");
        newIfStmt = (If) codeStylist.reformat(newIfStmt);

        System.out.println("new statement after declaration " + newIfStmt.getText());
        System.out.println("new statement after declaration " + newIfStmt.getStatement());

        // Replace the conditional expression with the one from the original ternary expression
//        final PsiReferenceExpression condition = (PsiReferenceExpression) conditionalExpression.getCondition().copy();
//        newIfStmt.getCondition().replace(condition);
//
//        // Begin building the assignment string for the THEN and ELSE clauses using the
//        // parent of the ternary conditional expression
//        PsiAssignmentExpression assignmentExpression =
//                PsiTreeUtil.getParentOfType(conditionalExpression, PsiAssignmentExpression.class, false);
//        // Get the contents of the assignment expression up to the start of the ternary expression
//        String exprFrag = assignmentExpression.getLExpression().getText()
//                + assignmentExpression.getOperationSign().getText();
//
//        // Build the THEN statement string for the new IF statement,
//        // make a PhpExpression from the string, and switch the placeholder
//        String thenStr = exprFrag + conditionalExpression.getTrueVariant().getText() + ";";
//        PhpExpression thenStmt = (PhpExpression) PhpPsiElementFactory.createStatement(project, thenStr);
//
//        // TODO: is enough to do getStatement() for `If` ?
//        System.out.println("current if 'statement' : " + newIfStmt.getStatement() + "\n\n");
////        ((PsiBlockStatement) newIfStmt.getThenBranch()).getCodeBlock().getStatements()[0].replace(thenStmt);
//
//        // Build the ELSE statement string for the new IF statement,
//        // make a PhpExpression from the string, and switch the placeholder
//        String elseStr = exprFrag + conditionalExpression.getFalseVariant().getText() + ";";
//        PhpExpression elseStmt = (PhpExpression) PhpPsiElementFactory.createStatement(project, elseStr);
//        ((PsiBlockStatement) newIfStmt.getElseBranch()).getCodeBlock().getStatements()[0].replace(elseStmt);
//
//        // Replace the entire original statement with the new IF
//        newIfStmt = (If) originalStatement.replace(newIfStmt);
//
//        System.out.println("original statement was");
//        System.out.println(originalStatement);
//        System.out.println("new statement is");
//        System.out.println(newIfStmt);

        System.out.println("\n\n");
        System.out.println("\n\n");
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
