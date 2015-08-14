package de.umass.idea.copyConstructor;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;

public class GenerateCopyConstructorAction extends BaseGenerateAction {

	public GenerateCopyConstructorAction() {
		super(new GenerateCopyConstructorHandler());
	}

	@Override
	protected boolean isValidForClass(PsiClass targetClass) {
		return super.isValidForClass(targetClass)
				&& !(targetClass instanceof PsiAnonymousClass)
				&& !ConstructorUtil.hasCopyConstructor(targetClass);
	}
}
