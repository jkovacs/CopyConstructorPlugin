package de.umass.idea.copyConstructor.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiVariable;

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
				return referenceExpression.isReferenceTo(field) && qualifierReferencesVariable(qualifierExpression, instance);
			}
			return false;
		}

		private boolean qualifierReferencesVariable(@Nullable PsiExpression qualifier, @Nullable PsiVariable instance) {
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
