// @java.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */
package org.gridgain.inspection.abbrev;

import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.refactoring.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static org.gridgain.util.GridUtils.*;

/**
 * Inspection that checks variable names for usage of restricted words that
 * need to be abbreviated.
 *
 * @author @java.author
 * @version @java.version
 */
public class GridAbbreviationInspection extends BaseJavaLocalInspectionTool {
    /** Abbreviation rules. */
    private GridAbbreviationRules abbreviationRules = GridAbbreviationRules.getInstance();

    /** {@inheritDoc} */
    @NotNull @Override public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    /** {@inheritDoc} */
    @NotNull @Override public String getDisplayName() {
        return "Incorrect Java abbreviation usage";
    }

    /** {@inheritDoc} */
    @NotNull @Override public String getShortName() {
        return "JavaAbbreviationUsage";
    }

    /** {@inheritDoc} */
    @NotNull @Override public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
        final boolean isOnTheFly) {
        return new JavaElementVisitor() {
            /** {@inheritDoc} */
            @Override public void visitField(PsiField field) {
                boolean isFinal = false;

                boolean isStatic = false;

                if (field instanceof PsiEnumConstant)
                    return;

                for (PsiElement el : field.getChildren()) {
                    if (el instanceof PsiModifierList) {
                        PsiModifierList modifiers = (PsiModifierList)el;

                        isFinal = modifiers.hasExplicitModifier("final");

                        isStatic = modifiers.hasExplicitModifier("static");
                    }

                    if (el instanceof PsiIdentifier && !(isFinal && isStatic))
                        checkShouldAbbreviate(field, el);
                }
            }

            /** {@inheritDoc} */
            @Override public void visitLocalVariable(PsiLocalVariable variable) {
                for (PsiElement el : variable.getChildren()) {
                    if (el instanceof PsiIdentifier)
                        checkShouldAbbreviate(variable, el);
                }
            }

            /** {@inheritDoc} */
            @Override public void visitMethod(PsiMethod mtd) {
                for (PsiParameter par : mtd.getParameterList().getParameters()) {
                    for (PsiElement el : par.getChildren()) {
                        if (el instanceof PsiIdentifier)
                            checkShouldAbbreviate(par, el);
                    }
                }
            }

            /**
             * Checks if given name contains a part that should be abbreviated and registers fix if needed.
             *
             * @param toCheck Element to check and rename.
             * @param el Element to highlight the problem.
             */
            private void checkShouldAbbreviate(PsiNamedElement toCheck, PsiElement el) {
                List<String> nameParts = camelCaseParts(toCheck.getName());

                for (String part : nameParts) {
                    if (getAbbreviation(part) != null) {
                        holder.registerProblem(el, "Abbreviation should be used",
                            new RenameToFix(abbreviationRules.replaceWithAbbreviations(nameParts)));

                        break;
                    }
                }
            }
        };
    }

    /**
     * Creates panel with user message.
     *
     * @return Panel instance.
     */
    @Override public javax.swing.JComponent createOptionsPanel() {
        javax.swing.JPanel panel = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        File abbrevFile = abbreviationRules.getFile();

        javax.swing.JLabel msg = new javax.swing.JLabel(
            abbrevFile != null ? "Using abbreviation file: " + abbrevFile.getAbsolutePath() :
            "Using default abbreviation file from plugin resources.");

        panel.add(msg);

        return panel;
    }

    /**
     * Renames variable to a given name.
     */
    private class RenameToFix implements LocalQuickFix {
        /** New proposed variable name. */
        private String name;

        /**
         * Creates rename to fix.
         *
         * @param name New variable name.
         */
        private RenameToFix(@NotNull String name) {
            this.name = name;
        }

        /** {@inheritDoc} */
        @NotNull public String getName() {
            return "Rename to " + name;
        }

        /** {@inheritDoc} */
        @NotNull public String getFamilyName() {
            return "";
        }

        /** {@inheritDoc} */
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descr) {
            PsiElement element = descr.getPsiElement().getParent();

            rename(project, element, name);
        }

        /**
         * Renames given element to a given name.
         *
         * @param project Project in which variable is located.
         * @param element Element to rename.
         * @param name New element name.
         */
        private void rename(@NotNull Project project, @NotNull PsiElement element, @NotNull String name) {
            JavaRefactoringFactory factory = JavaRefactoringFactory.getInstance(project);

            RenameRefactoring renRefactoring = factory.createRename(element, name, false, false);

            renRefactoring.run();
        }
    }

    /**
     * Performs lookup of name part in abbreviation table.
     *
     * @param namePart Name part to lookup.
     * @return Abbreviation for given name or {@code null} if there is no such abbreviation.
     */
    @Nullable private String getAbbreviation(String namePart) {
        return abbreviationRules.getAbbreviation(namePart);
    }
}
