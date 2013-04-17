package de.umass.idea.copyConstructor.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import de.umass.idea.copyConstructor.ConstructorUtil;
import org.jetbrains.annotations.NotNull;

import static de.umass.idea.copyConstructor.ConstructorUtil.findCopyConstructor;
import static de.umass.idea.copyConstructor.ConstructorUtil.isCopyConstructor;

public class SuperclassConstructorInspection extends LocalInspectionTool {

	@NotNull
	@Override
	public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
		return new SuperclassConstructorVisitor(holder);
	}

	private static class SuperclassConstructorVisitor extends JavaElementVisitor {

		private final ProblemsHolder holder;

		public SuperclassConstructorVisitor(ProblemsHolder holder) {
			this.holder = holder;
		}

		@Override
		public void visitMethod(PsiMethod method) {
			PsiClass containingClass = method.getContainingClass();
			if (isCopyConstructor(method) && containingClass != null) {
				PsiMethod superclassCopyConstructor = findCopyConstructor(containingClass.getSuperClass());
				if (superclassCopyConstructor != null && !doesCallConstructor(method, superclassCopyConstructor)) {
					PsiElement identifier = method.getNameIdentifier();
					holder.registerProblem(identifier != null ? identifier : method, "Copy constructor does not call copy constructor of superclass",
							ProblemHighlightType.WEAK_WARNING);
				}
			}
		}

		private boolean doesCallConstructor(PsiMethod method, PsiMethod constructor) {
			PsiMethod calledConstructor = ConstructorUtil.findConstructorCall(method);
			return calledConstructor != null && calledConstructor.isEquivalentTo(constructor);
		}
	}
}
