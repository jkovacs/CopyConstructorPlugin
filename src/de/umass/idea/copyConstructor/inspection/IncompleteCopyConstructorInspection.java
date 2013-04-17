package de.umass.idea.copyConstructor.inspection;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static de.umass.idea.copyConstructor.ConstructorUtil.isCopyConstructor;

public class IncompleteCopyConstructorInspection extends LocalInspectionTool {

	@NotNull
	@Override
	public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
		return new IncompleteConstructorVisitor(holder);
	}

	private static class IncompleteConstructorVisitor extends JavaElementVisitor {

		private final ProblemsHolder holder;

		public IncompleteConstructorVisitor(ProblemsHolder holder) {
			this.holder = holder;
		}

		@Override
		public void visitMethod(PsiMethod method) {
			PsiClass containingClass = method.getContainingClass();
			if (isCopyConstructor(method) && containingClass != null) {
				if (!constructorAssignsAllFields(method, containingClass.getFields())) {
					PsiIdentifier identifier = method.getNameIdentifier();
					holder.registerProblem(identifier != null ? identifier : method, "Copy constructor does not copy all fields",
							ProblemHighlightType.WEAK_WARNING);
				}
			}
		}

		private boolean constructorAssignsAllFields(final PsiMethod constructor, PsiField[] fields) {
			final List<PsiField> allFields = Arrays.asList(fields);
			final Set<PsiField> unassignedFields = new HashSet<PsiField>(allFields);
			final PsiParameter copyParameter = constructor.getParameterList().getParameters()[0];
			constructor.accept(new JavaRecursiveElementVisitor() {
				@Override
				public void visitAssignmentExpression(PsiAssignmentExpression expression) {
					PsiExpression left = expression.getLExpression();
					PsiExpression right = expression.getRExpression();
					PsiReference assignee = left.getReference();
					if (assignee != null) {
						PsiElement leftReference = assignee.resolve();
						if (leftReference != null && leftReference instanceof PsiField) {
							PsiField referencedField = (PsiField) leftReference;
							if (isReferenceToFieldInInstance(left, referencedField, null)) {
								if (isReferenceToFieldInInstance(right, referencedField, copyParameter)) {
									unassignedFields.remove(referencedField);
								} else if (right != null) {
									// report problem: suspicious assignment copies value from wrong field: "this.x = copy.y"
									holder.registerProblem(expression,
											String.format("Suspicious assignment in copy constructor of '%s' to field %s", right.getText(),
													referencedField.getName()), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
								}
							}
						}
					}
				}
			});
			return unassignedFields.isEmpty();
		}

		private boolean isReferenceToFieldInInstance(@Nullable PsiExpression expression, @NotNull PsiField field, @Nullable PsiVariable instance) {
			if (expression != null && expression instanceof PsiReferenceExpression) {
				PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
				PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
				return referenceExpression.isReferenceTo(field) && qualifierReferecesVariable(qualifierExpression, instance);
			}
			return false;
		}

		private boolean qualifierReferecesVariable(@Nullable PsiExpression qualifier, @Nullable PsiVariable instance) {
			if (instance == null && qualifierReferencesThis(qualifier))
				return true;

			if (qualifier != null) {
				PsiReference reference = qualifier.getReference();
				return reference != null && reference.isReferenceTo(instance);
			}

			return false;
		}

		private boolean qualifierReferencesThis(@Nullable PsiExpression qualifier) {
			return qualifier == null || qualifier instanceof PsiThisExpression;
		}
	}
}
