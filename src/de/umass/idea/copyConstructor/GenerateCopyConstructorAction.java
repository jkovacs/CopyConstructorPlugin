package de.umass.idea.copyConstructor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

public class GenerateCopyConstructorAction extends AnAction {

	public void actionPerformed(AnActionEvent e) {
		final PsiClass psiClass = getPsiClassFromContext(e);
		new WriteCommandAction.Simple(psiClass.getProject(), psiClass.getContainingFile()) {

			@Override
			protected void run() throws Throwable {
				generateCopyConstructor(psiClass);
			}
		}.execute();
	}

	@Override
	public void update(AnActionEvent e) {
		PsiClass psiClass = getPsiClassFromContext(e);
		e.getPresentation().setEnabled(psiClass != null);
	}

	private void generateCopyConstructor(PsiClass psiClass) {
		PsiField[] fields = psiClass.getFields();
		String parameterName = "other";

		PsiMethod superclassCopyConstructor = ConstructorUtil.findCopyConstructor(psiClass.getSuperClass());

		StringBuilder code = new StringBuilder();
		code.append(String.format("public %s(%s %s) {", psiClass.getName(), psiClass.getName(), parameterName));

		if (superclassCopyConstructor != null) {
			code.append(String.format("super(%s);", parameterName));
		}

		for (PsiField field : fields) {
			// Skip static fields as they're non-instance
			if (field.hasModifierProperty(PsiModifier.STATIC)) {
				continue;
			}

			String name = field.getName();
			code.append(String.format("this.%s = %s.%s;", name, parameterName, name));
		}
		code.append("}");

		PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
		PsiMethod constructor = elementFactory.createMethodFromText(code.toString(), psiClass);
		PsiElement method = psiClass.add(constructor);
		JavaCodeStyleManager.getInstance(psiClass.getProject()).shortenClassReferences(method);
	}

	private PsiClass getPsiClassFromContext(AnActionEvent e) {
		PsiFile file = e.getData(LangDataKeys.PSI_FILE);
		Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if (file == null || editor == null) {
			return null;
		}
		int offset = editor.getCaretModel().getOffset();
		PsiElement elementAt = file.findElementAt(offset);
		return PsiTreeUtil.getParentOfType(elementAt, PsiClass.class);
	}
}
