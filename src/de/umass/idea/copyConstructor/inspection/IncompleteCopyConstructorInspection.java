package de.umass.idea.copyConstructor.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.umass.idea.copyConstructor.ConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
				final List<PsiField> fields = ConstructorUtil.getAllCopyableFields(containingClass);

				if (!constructorAssignsAllFields(method, fields)) {
					PsiIdentifier identifier = method.getNameIdentifier();
					holder.registerProblem(identifier != null ? identifier : method, "Copy constructor does not copy all fields",
							ProblemHighlightType.WEAK_WARNING);
				}
			}
		}

		private boolean constructorAssignsAllFields(final PsiMethod constructor, List<PsiField> allFields) {
			final Set<PsiField> unassignedFields = new HashSet<>(allFields);
			final PsiParameter copyParameter = constructor.getParameterList().getParameters()[0];
			constructor.accept(new JavaRecursiveElementVisitor() {
				@Override
				public void visitAssignmentExpression(PsiAssignmentExpression expression) {
					PsiExpression left = expression.getLExpression();
					PsiExpression right = expression.getRExpression();
					PsiReference assignee = left.getReference();
					if (assignee != null) {
						PsiElement leftReference = assignee.resolve();
						if (leftReference instanceof PsiField) {
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
			boolean isReference = false;
			if (expression instanceof PsiReferenceExpression) {
				PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
				PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
				isReference = referenceExpression.isReferenceTo(field) && qualifierReferencesVariable(qualifierExpression, instance);
				if (!isReference && qualifierExpression instanceof PsiReferenceExpression) {
					isReference = isReferenceToFieldInInstance(qualifierExpression, field, instance);
				}
			} else if (expression instanceof PsiCallExpression) {
				PsiCallExpression callExpression = (PsiCallExpression) expression;
				isReference = isCallExpressionReferencingToFieldInstance(callExpression, field, instance);
			} else if (expression instanceof PsiPolyadicExpression) {
				PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
				isReference = isPolyadicExpressionReferencingToFieldInstance(polyadicExpression, field, instance);
			} else if (expression instanceof PsiConditionalExpression) {
				PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) expression;
				isReference = isConditionalExpressionReferencingToFieldInstance(conditionalExpression, field, instance);
			} else if (expression instanceof PsiParenthesizedExpression) {
				PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression) expression;
				isReference = isReferenceToFieldInInstance(parenthesizedExpression.getExpression(), field, instance);
			}
			return isReference;
		}

		private boolean isConditionalExpressionReferencingToFieldInstance(
				final PsiConditionalExpression conditionalExpression, @NotNull final PsiField field,
				@Nullable final PsiVariable instance) {
			return isReferenceToFieldInInstance(conditionalExpression.getCondition(), field, instance)
					&& (isReferenceToFieldInInstance(conditionalExpression.getThenExpression(), field, instance)
						 || isReferenceToFieldInInstance(conditionalExpression.getElseExpression(), field, instance));
		}

		private boolean isPolyadicExpressionReferencingToFieldInstance(
				final PsiPolyadicExpression polyadicExpression, @NotNull final PsiField field,
				@Nullable final PsiVariable instance) {
			for(PsiExpression operandExpression : polyadicExpression.getOperands()) {
				if (isReferenceToFieldInInstance(operandExpression, field, instance)) {
					return true;
				}
			}
			return false;
		}

		private boolean isCallExpressionReferencingToFieldInstance(final PsiCallExpression callExpression,
				@NotNull final PsiField field, @Nullable final PsiVariable instance) {
			if (callExpression instanceof PsiMethodCallExpression) {
				PsiMethodCallExpression methodExpression = (PsiMethodCallExpression) callExpression;
				return isReferenceToFieldInInstance(methodExpression.getMethodExpression(), field, instance);
			} else if (callExpression.getArgumentList() != null) {
				for(PsiExpression argumentExpression : callExpression.getArgumentList().getExpressions()) {
					if (isReferenceToFieldInInstance(argumentExpression, field, instance)) {
						return true;
					}
				}
			}
			return false;
		}

		private boolean qualifierReferencesVariable(@Nullable PsiExpression qualifier, @Nullable PsiVariable instance) {
			if (instance == null && qualifierReferencesThis(qualifier))
				return true;

			if (qualifier != null) {
				PsiReference reference = qualifier.getReference();
				return reference != null && instance != null && reference.isReferenceTo(instance);
			}

			return false;
		}

		private boolean qualifierReferencesThis(@Nullable PsiExpression qualifier) {
			return qualifier == null || qualifier instanceof PsiThisExpression;
		}
	}
}
