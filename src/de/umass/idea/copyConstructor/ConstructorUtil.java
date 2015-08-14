package de.umass.idea.copyConstructor;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ConstructorUtil {

	private ConstructorUtil() {
	}

	public static boolean isCopyConstructor(@NotNull PsiMethod method) {
		PsiClass containingClass = method.getContainingClass();
		if(!method.isConstructor() || containingClass == null)
			return false;

		String className = containingClass.getQualifiedName();
		PsiParameterList paramList = method.getParameterList();
		return paramList.getParametersCount() == 1 && paramList.getParameters()[0].getType().getCanonicalText().equals(className);
	}

	/**
	 * Finds a copy constructor in this class, or null if none was found.
	 * The copy constructor is defined as the constructor with exactly one parameter of the same type as the class.
	 */
	@Nullable
	public static PsiMethod findCopyConstructor(@Nullable PsiClass psiClass) {
		if (psiClass == null)
			return null;

		PsiMethod[] constructors = psiClass.getConstructors();
		for (PsiMethod constructor : constructors) {
			if (isCopyConstructor(constructor)) {
				return constructor;
			}
		}
		return null;
	}

	public static boolean hasCopyConstructor(@Nullable PsiClass psiClass) {
		return findCopyConstructor(psiClass) != null;
	}

	/**
	 * Finds the explicitly called constructor method, i.e. the one references by any call to super() or this(),
	 * in the passed constructor method, or null if no explicit constructor call is present.
	 */
	@Nullable
	public static PsiMethod findConstructorCall(PsiMethod constructor) {
		PsiCodeBlock body = constructor.getBody();
		if (body != null) {
			PsiStatement[] statements = body.getStatements();
			if (statements.length != 0) {
				PsiElement firstChild = statements[0].getFirstChild();
				if (firstChild instanceof PsiMethodCallExpression) {
					PsiMethodCallExpression callExpression = (PsiMethodCallExpression) firstChild;
					PsiMethod methodCallTarget = callExpression.resolveMethod();
					if (methodCallTarget != null && methodCallTarget.isConstructor()) {
						return methodCallTarget;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Finds all fields in the passed class that should be copied by a copy constructor.
	 * This excludes, for example, static fields.
	 */
	public static List<PsiField> getAllCopyableFields(PsiClass psiClass) {
		List<PsiField> copyableFields = new ArrayList<PsiField>();
		PsiField[] fields = psiClass.getFields();
		for (PsiField field : fields) {
			if (isCopyableField(field)) {
				copyableFields.add(field);
			}
		}
		return copyableFields;
	}

	/**
	 * Returns true if a specific field is copyable by a copy constructor. Fields that can't or should not be copied include:
	 * * static fields,
	 * * final and already initialized fields.
	 */
	private static boolean isCopyableField(PsiField field) {
		return !field.hasModifierProperty(PsiModifier.STATIC)
				&& (!field.hasModifierProperty(PsiModifier.FINAL) || field.getInitializer() == null);
	}

}
